#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/ioctl.h>
#include <time.h>
#include <unistd.h>
#include <sound/asound.h>

#define DEFAULT_RATE 48000
#define DEFAULT_CHANNELS 2
#define DEFAULT_PERIODS 4
#define DEFAULT_PREFILL 2
#define MAX_CARDS 16
#define MAX_CARD_NAME 64
#define MAX_PATH 128

enum {
    FMT_S16 = 0,
    FMT_S24_3,
    FMT_S24_4,
    FMT_S32,
    FMT_COUNT
};

struct card_info {
    int number;
    char name[MAX_CARD_NAME];
};

struct opts {
    int rate;
    int channels;
    int periods;
    int prefill_periods;
    int period_frames;
    int capture_card;
    int playback_card;
    int capture_device;
    int playback_device;
    int capture_format;
    int playback_format;
    const char *config_path;
    const char *log_path;
    char log_path_buf[256];
    int self_test;
};

struct stream {
    int fd;
    int format;
    int rate;
    int channels;
    int period_frames;
    int periods;
    int buffer_frames;
    int bytes_per_frame;
    int card;
    int device;
    int capture;
};

static volatile sig_atomic_t stop_requested;
static FILE *log_fp;

static void log_msg(const char *fmt, ...)
{
    va_list ap;
    if (!log_fp)
        log_fp = stderr;
    va_start(ap, fmt);
    vfprintf(log_fp, fmt, ap);
    va_end(ap);
    fflush(log_fp);
}

static void on_signal(int sig)
{
    (void)sig;
    stop_requested = 1;
}

static void msleep(unsigned int ms)
{
    struct timespec ts;
    ts.tv_sec = ms / 1000;
    ts.tv_nsec = (long)(ms % 1000) * 1000000L;
    while (nanosleep(&ts, &ts) < 0 && errno == EINTR)
        ;
}

static int fmt_bytes(int fmt)
{
    switch (fmt) {
    case FMT_S16:
        return 2;
    case FMT_S24_3:
        return 3;
    case FMT_S24_4:
        return 4;
    case FMT_S32:
        return 4;
    default:
        return 0;
    }
}

static int fmt_bits(int fmt)
{
    switch (fmt) {
    case FMT_S16:
        return 16;
    case FMT_S24_3:
        return 24;
    case FMT_S24_4:
        return 24;
    case FMT_S32:
        return 32;
    default:
        return 0;
    }
}

static const char *fmt_name(int fmt)
{
    switch (fmt) {
    case FMT_S16:
        return "s16le";
    case FMT_S24_3:
        return "s24_3le";
    case FMT_S24_4:
        return "s24le";
    case FMT_S32:
        return "s32le";
    default:
        return "?";
    }
}

static int fmt_from_name(const char *name)
{
    if (!name || !*name)
        return -1;
    if (!strcasecmp(name, "s16") || !strcasecmp(name, "s16le"))
        return FMT_S16;
    if (!strcasecmp(name, "s24") || !strcasecmp(name, "s24_3") ||
        !strcasecmp(name, "s24_3le"))
        return FMT_S24_3;
    if (!strcasecmp(name, "s24le") || !strcasecmp(name, "s24_4le"))
        return FMT_S24_4;
    if (!strcasecmp(name, "s32") || !strcasecmp(name, "s32le"))
        return FMT_S32;
    return -1;
}

static snd_pcm_format_t alsa_format(int fmt)
{
    switch (fmt) {
    case FMT_S16:
        return SNDRV_PCM_FORMAT_S16_LE;
    case FMT_S24_3:
        return SNDRV_PCM_FORMAT_S24_3LE;
    case FMT_S24_4:
        return SNDRV_PCM_FORMAT_S24_LE;
    case FMT_S32:
        return SNDRV_PCM_FORMAT_S32_LE;
    default:
        return SNDRV_PCM_FORMAT_S16_LE;
    }
}

static struct snd_mask *param_mask(struct snd_pcm_hw_params *p, int param)
{
    if (param < SNDRV_PCM_HW_PARAM_FIRST_MASK ||
        param > SNDRV_PCM_HW_PARAM_LAST_MASK)
        return NULL;
    return &p->masks[param - SNDRV_PCM_HW_PARAM_FIRST_MASK];
}

static struct snd_interval *param_interval(struct snd_pcm_hw_params *p, int param)
{
    if (param < SNDRV_PCM_HW_PARAM_FIRST_INTERVAL ||
        param > SNDRV_PCM_HW_PARAM_LAST_INTERVAL)
        return NULL;
    return &p->intervals[param - SNDRV_PCM_HW_PARAM_FIRST_INTERVAL];
}

static void mask_none(struct snd_mask *m)
{
    memset(m, 0, sizeof(*m));
}

static void mask_set(struct snd_mask *m, unsigned int value)
{
    m->bits[value / 32] |= (uint32_t)1U << (value % 32);
}

static void interval_exact(struct snd_interval *i, unsigned int value)
{
    i->min = value;
    i->max = value;
    i->openmin = 0;
    i->openmax = 0;
    i->integer = 1;
    i->empty = 0;
}

static int pcm_set_hw_params(struct stream *s, int period_frames)
{
    struct snd_pcm_hw_params p;
    struct snd_mask *m;
    struct snd_interval *i;
    int ret;

    memset(&p, 0, sizeof(p));
    m = param_mask(&p, SNDRV_PCM_HW_PARAM_ACCESS);
    if (!m)
        return -EINVAL;
    mask_none(m);
    mask_set(m, SNDRV_PCM_ACCESS_RW_INTERLEAVED);

    m = param_mask(&p, SNDRV_PCM_HW_PARAM_FORMAT);
    if (!m)
        return -EINVAL;
    mask_none(m);
    mask_set(m, alsa_format(s->format));

    m = param_mask(&p, SNDRV_PCM_HW_PARAM_SUBFORMAT);
    if (!m)
        return -EINVAL;
    mask_none(m);
    mask_set(m, SNDRV_PCM_SUBFORMAT_STD);

    i = param_interval(&p, SNDRV_PCM_HW_PARAM_SAMPLE_BITS);
    if (!i)
        return -EINVAL;
    interval_exact(i, (unsigned int)fmt_bits(s->format));

    i = param_interval(&p, SNDRV_PCM_HW_PARAM_CHANNELS);
    if (!i)
        return -EINVAL;
    interval_exact(i, (unsigned int)s->channels);

    i = param_interval(&p, SNDRV_PCM_HW_PARAM_RATE);
    if (!i)
        return -EINVAL;
    interval_exact(i, (unsigned int)s->rate);

    i = param_interval(&p, SNDRV_PCM_HW_PARAM_PERIOD_SIZE);
    if (!i)
        return -EINVAL;
    interval_exact(i, (unsigned int)period_frames);

    i = param_interval(&p, SNDRV_PCM_HW_PARAM_PERIODS);
    if (!i)
        return -EINVAL;
    interval_exact(i, (unsigned int)s->periods);

    ret = ioctl(s->fd, SNDRV_PCM_IOCTL_HW_PARAMS, &p);
    if (ret < 0)
        return -errno;

    i = param_interval(&p, SNDRV_PCM_HW_PARAM_PERIOD_SIZE);
    if (i && !i->empty && i->min == i->max)
        s->period_frames = (int)i->min;
    else
        s->period_frames = period_frames;

    i = param_interval(&p, SNDRV_PCM_HW_PARAM_BUFFER_SIZE);
    if (i && !i->empty && i->min == i->max)
        s->buffer_frames = (int)i->min;
    else
        s->buffer_frames = s->period_frames * s->periods;

    return 0;
}

static int pcm_set_sw_params(struct stream *s, int start_threshold)
{
    struct snd_pcm_sw_params sw;
    int ret;

    memset(&sw, 0, sizeof(sw));
    sw.tstamp_mode = SNDRV_PCM_TSTAMP_NONE;
    sw.period_step = 1;
    sw.avail_min = (snd_pcm_uframes_t)s->period_frames;
    sw.start_threshold = (snd_pcm_uframes_t)start_threshold;
    sw.stop_threshold = (snd_pcm_uframes_t)s->buffer_frames;
    sw.silence_threshold = 0;
    sw.silence_size = 0;
    sw.boundary = (snd_pcm_uframes_t)s->buffer_frames;

    ret = ioctl(s->fd, SNDRV_PCM_IOCTL_SW_PARAMS, &sw);
    if (ret < 0)
        return -errno;
    return 0;
}

static int pcm_prepare(struct stream *s)
{
    if (ioctl(s->fd, SNDRV_PCM_IOCTL_PREPARE) < 0)
        return -errno;
    return 0;
}

static int pcm_start(struct stream *s)
{
    if (ioctl(s->fd, SNDRV_PCM_IOCTL_START) < 0)
        return -errno;
    return 0;
}

static int pcm_drop(struct stream *s)
{
    if (ioctl(s->fd, SNDRV_PCM_IOCTL_DROP) < 0)
        return -errno;
    return 0;
}

static int pcm_read(struct stream *s, void *buf, int frames)
{
    struct snd_xferi xfer;
    int ret;

    memset(&xfer, 0, sizeof(xfer));
    xfer.buf = buf;
    xfer.frames = (snd_pcm_uframes_t)frames;
    ret = ioctl(s->fd, SNDRV_PCM_IOCTL_READI_FRAMES, &xfer);
    if (ret < 0)
        return -errno;
    return (int)xfer.result;
}

static int pcm_write(struct stream *s, const void *buf, int frames)
{
    struct snd_xferi xfer;
    int ret;

    memset(&xfer, 0, sizeof(xfer));
    xfer.buf = (void *)buf;
    xfer.frames = (snd_pcm_uframes_t)frames;
    ret = ioctl(s->fd, SNDRV_PCM_IOCTL_WRITEI_FRAMES, &xfer);
    if (ret < 0)
        return -errno;
    return (int)xfer.result;
}

static int wait_fd(int fd, short events, int timeout_ms)
{
    struct pollfd pfd;
    int ret;
    pfd.fd = fd;
    pfd.events = events;
    pfd.revents = 0;
    do {
        ret = poll(&pfd, 1, timeout_ms);
    } while (ret < 0 && errno == EINTR && !stop_requested);
    if (ret < 0)
        return -errno;
    return ret;
}

static int open_pcm(struct stream *s, int card, int device, int capture,
                    int rate, int channels, int periods, int requested_period,
                    int requested_format, int *selected_format)
{
    char path[64];
    int formats[FMT_COUNT];
    int format_count = 0;
    int period_candidates[6];
    int period_count = 0;
    int f;
    int i;
    int ret;

    if (requested_format >= 0 && requested_format < FMT_COUNT) {
        formats[format_count++] = requested_format;
    } else {
        formats[format_count++] = FMT_S24_3;
        formats[format_count++] = FMT_S24_4;
        formats[format_count++] = FMT_S32;
        formats[format_count++] = FMT_S16;
    }

    if (requested_period > 0) {
        period_candidates[period_count++] = requested_period;
        period_candidates[period_count++] = requested_period / 2;
        period_candidates[period_count++] = requested_period * 2;
        period_candidates[period_count++] = 1024;
        period_candidates[period_count++] = 512;
        period_candidates[period_count++] = 256;
    } else {
        period_candidates[period_count++] = rate / 100;
        period_candidates[period_count++] = rate / 200;
        period_candidates[period_count++] = 1024;
        period_candidates[period_count++] = 512;
        period_candidates[period_count++] = 256;
        period_candidates[period_count++] = 128;
    }

    snprintf(path, sizeof(path), "/dev/snd/pcmC%dD%d%c", card, device,
             capture ? 'c' : 'p');

    for (f = 0; f < format_count; f++) {
        for (i = 0; i < period_count; i++) {
            int pf = period_candidates[i];
            if (pf < 32)
                pf = 32;
            s->fd = open(path, O_RDWR | O_NONBLOCK | O_CLOEXEC);
            if (s->fd < 0)
                return -errno;
            s->format = formats[f];
            s->rate = rate;
            s->channels = channels;
            s->periods = periods;
            s->bytes_per_frame = fmt_bytes(formats[f]) * channels;
            s->capture = capture;
            s->card = card;
            s->device = device;
            s->period_frames = pf;
            s->buffer_frames = 0;

            ret = pcm_set_hw_params(s, pf);
            if (ret == 0) {
                *selected_format = formats[f];
                return 0;
            }
            close(s->fd);
            s->fd = -1;
        }
    }
    return -EINVAL;
}

static int parse_card_line(const char *line, struct card_info *out)
{
    int number;
    char name[MAX_CARD_NAME];
    if (sscanf(line, " %d [%63[^]]]", &number, name) != 2)
        return -1;
    out->number = number;
    snprintf(out->name, sizeof(out->name), "%s", name);
    return 0;
}

static int scan_control_cards(struct card_info *cards, int max_cards)
{
    int card;
    int count = 0;

    for (card = 0; card < 32 && count < max_cards; card++) {
        char path[64];
        struct snd_ctl_card_info info;
        int fd;
        snprintf(path, sizeof(path), "/dev/snd/controlC%d", card);
        fd = open(path, O_RDONLY | O_CLOEXEC);
        if (fd < 0)
            continue;
        memset(&info, 0, sizeof(info));
        if (ioctl(fd, SNDRV_CTL_IOCTL_CARD_INFO, &info) == 0) {
            cards[count].number = card;
            snprintf(cards[count].name, sizeof(cards[count].name), "%s %s",
                     (const char *)info.id, (const char *)info.name);
            count++;
        }
        close(fd);
    }
    return count;
}

static int scan_cards(struct card_info *cards, int max_cards)
{
    FILE *f;
    char line[256];
    int count = 0;

    f = fopen("/proc/asound/cards", "r");
    if (f) {
        while (count < max_cards && fgets(line, sizeof(line), f)) {
            struct card_info ci;
            if (parse_card_line(line, &ci) == 0)
                cards[count++] = ci;
        }
        fclose(f);
    }
    if (count > 0)
        return count;
    return scan_control_cards(cards, max_cards);
}

static int find_uac2_card(const struct card_info *cards, int count)
{
    int i;
    for (i = 0; i < count; i++) {
        if (strcasestr(cards[i].name, "uac2"))
            return cards[i].number;
    }
    return -1;
}

static int find_internal_card(const struct card_info *cards, int count, int exclude)
{
    int i;
    for (i = 0; i < count; i++) {
        if (cards[i].number == exclude)
            continue;
        if (strcasestr(cards[i].name, "uac2"))
            continue;
        if (strcasestr(cards[i].name, "hdmi"))
            continue;
        return cards[i].number;
    }
    return -1;
}

static void convert_frames(const void *src, int src_fmt, void *dst, int dst_fmt,
                           int frames, int channels)
{
    const uint8_t *in = src;
    uint8_t *out = dst;
    int i;
    int c;

    if (src_fmt == dst_fmt) {
        memcpy(dst, src, (size_t)frames * channels * (size_t)fmt_bytes(src_fmt));
        return;
    }

    for (i = 0; i < frames; i++) {
        for (c = 0; c < channels; c++) {
            int32_t v;
            if (src_fmt == FMT_S24_3) {
                uint32_t u = (uint32_t)in[0] | ((uint32_t)in[1] << 8) |
                             ((uint32_t)in[2] << 16);
                v = ((int32_t)(u << 8)) >> 8;
                in += 3;
            } else if (src_fmt == FMT_S16) {
                v = (int32_t)(int16_t)((uint16_t)in[0] | ((uint16_t)in[1] << 8));
                in += 2;
            } else if (src_fmt == FMT_S24_4) {
                uint32_t u = (uint32_t)in[0] | ((uint32_t)in[1] << 8) |
                             ((uint32_t)in[2] << 16) | ((uint32_t)in[3] << 24);
                v = ((int32_t)(u << 8)) >> 8;
                in += 4;
            } else {
                v = (int32_t)((uint32_t)in[0] | ((uint32_t)in[1] << 8) |
                              ((uint32_t)in[2] << 16) | ((uint32_t)in[3] << 24));
                in += 4;
            }

            if (dst_fmt == FMT_S24_3) {
                int32_t s = v;
                uint32_t u;
                if (src_fmt == FMT_S16)
                    s = (int32_t)((int64_t)v * 256);
                else if (src_fmt == FMT_S32)
                    s = v >> 8;
                u = (uint32_t)s;
                out[0] = (uint8_t)u;
                out[1] = (uint8_t)(u >> 8);
                out[2] = (uint8_t)(u >> 16);
                out += 3;
            } else if (dst_fmt == FMT_S16) {
                int32_t s = v;
                if (src_fmt == FMT_S24_3 || src_fmt == FMT_S24_4)
                    s = v >> 8;
                else if (src_fmt == FMT_S32)
                    s = v >> 16;
                if (s > 32767)
                    s = 32767;
                if (s < -32768)
                    s = -32768;
                out[0] = (uint8_t)((uint16_t)(int16_t)s);
                out[1] = (uint8_t)(((uint16_t)(int16_t)s) >> 8);
                out += 2;
            } else if (dst_fmt == FMT_S24_4) {
                int32_t s = v;
                if (src_fmt == FMT_S16)
                    s = (int32_t)((int64_t)v * 256);
                else if (src_fmt == FMT_S32)
                    s = v >> 8;
                out[0] = (uint8_t)((uint32_t)s);
                out[1] = (uint8_t)((uint32_t)s >> 8);
                out[2] = (uint8_t)((uint32_t)s >> 16);
                out[3] = (uint8_t)((uint32_t)s >> 24);
                out += 4;
            } else {
                int32_t s = v;
                if (src_fmt == FMT_S16)
                    s = (int32_t)((int64_t)v * 65536);
                else if (src_fmt == FMT_S24_3 || src_fmt == FMT_S24_4)
                    s = (int32_t)((int64_t)v * 256);
                out[0] = (uint8_t)((uint32_t)s);
                out[1] = (uint8_t)((uint32_t)s >> 8);
                out[2] = (uint8_t)((uint32_t)s >> 16);
                out[3] = (uint8_t)((uint32_t)s >> 24);
                out += 4;
            }
        }
    }
}

static int write_all(struct stream *pb, const void *buf, int frames)
{
    const uint8_t *p = buf;
    int done = 0;

    while (done < frames && !stop_requested) {
        int n = pcm_write(pb, p + (size_t)done * pb->bytes_per_frame,
                          frames - done);
        if (n > 0) {
            done += n;
            continue;
        }
        if (n == -EAGAIN) {
            int w = wait_fd(pb->fd, POLLOUT, 1000);
            if (w == 0)
                continue;
            if (w < 0)
                return w;
            continue;
        }
        if (n == -EPIPE) {
            pcm_prepare(pb);
            pcm_start(pb);
            continue;
        }
        return n;
    }
    return done;
}

static int read_data(struct stream *cap, void *buf, int frames)
{
    int n;
    while (!stop_requested) {
        n = pcm_read(cap, buf, frames);
        if (n > 0)
            return n;
        if (n == 0)
            return 0;
        if (n == -EAGAIN) {
            int w = wait_fd(cap->fd, POLLIN, 1000);
            if (w == 0)
                continue;
            if (w < 0)
                return w;
            continue;
        }
        if (n == -EPIPE) {
            pcm_prepare(cap);
            pcm_start(cap);
            continue;
        }
        return n;
    }
    return 0;
}

static int prefill_silence(struct stream *pb, int frames)
{
    int bytes = frames * pb->bytes_per_frame;
    uint8_t *zero;
    int ret;

    zero = calloc(1, (size_t)bytes);
    if (!zero)
        return -ENOMEM;
    ret = write_all(pb, zero, frames);
    free(zero);
    return ret;
}

static int run_bridge(struct opts *o)
{
    struct card_info cards[MAX_CARDS] = { { 0, "" } };
    struct stream cap;
    struct stream pb;
    int card_count;
    int cap_card;
    int pb_card;
    int cap_fmt = -1;
    int pb_fmt = -1;
    int ret;
    int cap_bytes;
    int pb_bytes;
    int max_frame_bytes;
    int period;
    int prefill;
    uint8_t *capbuf = NULL;
    uint8_t *pbbuf = NULL;

    memset(&cap, 0, sizeof(cap));
    memset(&pb, 0, sizeof(pb));
    cap.fd = -1;
    pb.fd = -1;

    card_count = scan_cards(cards, MAX_CARDS);
    if (card_count < 0) {
        log_msg("uac2d: no /proc/asound/cards: %s\n", strerror(-card_count));
        return card_count;
    }

    cap_card = o->capture_card;
    if (cap_card < 0)
        cap_card = find_uac2_card(cards, card_count);
    if (cap_card < 0) {
        log_msg("uac2d: UAC2 card not found\n");
        return -ENODEV;
    }

    pb_card = o->playback_card;
    if (pb_card < 0)
        pb_card = find_internal_card(cards, card_count, cap_card);
    if (pb_card < 0) {
        log_msg("uac2d: internal playback card not found\n");
        return -ENODEV;
    }
    if (pb_card == cap_card) {
        log_msg("uac2d: capture and playback cards are the same\n");
        return -EINVAL;
    }

    if (o->period_frames > 0)
        period = o->period_frames;
    else {
        period = o->rate / 100;
        if (period < 32)
            period = 32;
    }

    {
        int rates[] = { o->rate, 192000, 176400, 96000, 88200, 48000,
                        44100, 32000, 24000, 22050, 16000, 8000 };
        int rate_count = (int)(sizeof(rates) / sizeof(rates[0]));
        int r;
        int opened = 0;
        ret = -EINVAL;
        for (r = 0; r < rate_count && !opened; r++) {
            int candidate = rates[r];
            int k;
            int duplicate = 0;
            if (candidate < 8000 || candidate > 384000)
                continue;
            for (k = 0; k < r; k++) {
                if (rates[k] == candidate)
                    duplicate = 1;
            }
            if (duplicate)
                continue;
            ret = open_pcm(&cap, cap_card, o->capture_device, 1, candidate,
                           o->channels, o->periods, period, o->capture_format,
                           &cap_fmt);
            if (ret == 0) {
                o->rate = candidate;
                opened = 1;
                break;
            }
        }
        if (!opened) {
            log_msg("uac2d: cannot open capture card %d device %d: %s\n",
                    cap_card, o->capture_device, strerror(-ret));
            return ret;
        }
    }
    cap_bytes = cap.bytes_per_frame;
    period = cap.period_frames;

    ret = open_pcm(&pb, pb_card, o->playback_device, 0, o->rate,
                   o->channels, o->periods, period, o->playback_format,
                   &pb_fmt);
    if (ret < 0) {
        log_msg("uac2d: cannot open playback card %d device %d: %s\n",
                pb_card, o->playback_device, strerror(-ret));
        close(cap.fd);
        return ret;
    }
    pb_bytes = pb.bytes_per_frame;
    max_frame_bytes = cap_bytes > pb_bytes ? cap_bytes : pb_bytes;

    prefill = o->prefill_periods * period;
    if (prefill > pb.buffer_frames)
        prefill = pb.buffer_frames;

    capbuf = malloc((size_t)period * (size_t)cap_bytes);
    pbbuf = malloc((size_t)period * (size_t)max_frame_bytes);
    if (!capbuf || !pbbuf) {
        log_msg("uac2d: out of memory\n");
        free(capbuf);
        free(pbbuf);
        close(cap.fd);
        close(pb.fd);
        return -ENOMEM;
    }

    log_msg("uac2d: card %d capture %s -> card %d playback %s, %d Hz, %d ch, "
            "period %d, buffer %d, formats %s/%s\n",
            cap_card, cap.capture ? "yes" : "no", pb_card, "yes", o->rate,
            o->channels, period, pb.buffer_frames, fmt_name(cap_fmt),
            fmt_name(pb_fmt));

    ret = pcm_set_sw_params(&cap, 1);
    if (ret < 0) {
        log_msg("uac2d: capture sw_params: %s\n", strerror(-ret));
        goto out;
    }
    ret = pcm_set_sw_params(&pb, prefill > 0 ? prefill : pb.period_frames);
    if (ret < 0) {
        log_msg("uac2d: playback sw_params: %s\n", strerror(-ret));
        goto out;
    }
    ret = pcm_prepare(&cap);
    if (ret < 0) {
        log_msg("uac2d: capture prepare: %s\n", strerror(-ret));
        goto out;
    }
    ret = pcm_prepare(&pb);
    if (ret < 0) {
        log_msg("uac2d: playback prepare: %s\n", strerror(-ret));
        goto out;
    }

    if (prefill > 0) {
        ret = prefill_silence(&pb, prefill);
        if (ret < 0) {
            log_msg("uac2d: playback prefill: %s\n", strerror(-ret));
            goto out;
        }
    }
    pcm_start(&pb);
    pcm_start(&cap);

    while (!stop_requested) {
        int got = read_data(&cap, capbuf, period);
        int written;
        if (got < 0) {
            log_msg("uac2d: capture read: %s\n", strerror(-got));
            break;
        }
        if (got == 0)
            continue;
        convert_frames(capbuf, cap_fmt, pbbuf, pb_fmt, got, o->channels);
        written = write_all(&pb, pbbuf, got);
        if (written < 0) {
            log_msg("uac2d: playback write: %s\n", strerror(-written));
            break;
        }
    }

    pcm_drop(&pb);
    pcm_drop(&cap);

out:
    close(cap.fd);
    close(pb.fd);
    free(capbuf);
    free(pbbuf);
    return 0;
}

static int self_test(void)
{
    uint8_t s24[12];
    uint8_t out[16];
    int32_t vals[4] = { 0, 8388607, -8388608, -1 };
    int32_t back[4];
    int i;

    for (i = 0; i < 4; i++) {
        uint32_t u = (uint32_t)vals[i];
        s24[i * 3] = (uint8_t)u;
        s24[i * 3 + 1] = (uint8_t)(u >> 8);
        s24[i * 3 + 2] = (uint8_t)(u >> 16);
    }
    convert_frames(s24, FMT_S24_3, out, FMT_S32, 4, 1);
    memcpy(back, out, sizeof(back));
    for (i = 0; i < 4; i++) {
        if (back[i] != (int32_t)((int64_t)vals[i] * 256)) {
            log_msg("uac2d: self-test s24->s32 failed at %d\n", i);
            return 1;
        }
    }

    convert_frames(s24, FMT_S24_3, out, FMT_S16, 4, 1);
    for (i = 0; i < 4; i++) {
        int16_t v = (int16_t)((uint16_t)out[i * 2] |
                             ((uint16_t)out[i * 2 + 1] << 8));
        int16_t expected = (int16_t)(vals[i] >> 8);
        if (v != expected) {
            log_msg("uac2d: self-test s24->s16 failed at %d\n", i);
            return 1;
        }
    }

    convert_frames(s24, FMT_S24_3, out, FMT_S24_3, 4, 1);
    if (memcmp(s24, out, 12) != 0) {
        log_msg("uac2d: self-test copy failed\n");
        return 1;
    }

    {
        int16_t s16[4] = { 0, 32767, -32768, -1 };
        uint8_t tmp[16];
        int16_t check[4];
        convert_frames(s16, FMT_S16, tmp, FMT_S24_3, 4, 1);
        convert_frames(tmp, FMT_S24_3, check, FMT_S16, 4, 1);
        for (i = 0; i < 4; i++) {
            if (check[i] != s16[i]) {
                log_msg("uac2d: self-test s16 roundtrip failed at %d\n", i);
                return 1;
            }
        }
    }

    {
        uint8_t tmp24[16];
        uint8_t packed[12];
        convert_frames(s24, FMT_S24_3, tmp24, FMT_S24_4, 4, 1);
        convert_frames(tmp24, FMT_S24_4, packed, FMT_S24_3, 4, 1);
        if (memcmp(s24, packed, 12) != 0) {
            log_msg("uac2d: self-test s24_4 roundtrip failed\n");
            return 1;
        }
    }

    {
        int32_t s32[4] = { 0, 2147483647, (-2147483647 - 1), -256 };
        uint8_t tmp[16];
        int32_t check[4];
        convert_frames(s32, FMT_S32, tmp, FMT_S24_3, 4, 1);
        convert_frames(tmp, FMT_S24_3, check, FMT_S32, 4, 1);
        for (i = 0; i < 4; i++) {
            if (check[i] != (int32_t)((uint32_t)s32[i] & 0xFFFFFF00u)) {
                log_msg("uac2d: self-test s32 roundtrip failed at %d\n", i);
                return 1;
            }
        }
    }

    log_msg("uac2d: self-test ok\n");
    return 0;
}

static void usage(const char *argv0)
{
    fprintf(stdout,
            "usage: %s [options]\n"
            "  --config PATH          configuration file\n"
            "  --rate HZ              sample rate (default %d)\n"
            "  --channels N           channels (default %d)\n"
            "  --periods N            playback buffer periods (default %d)\n"
            "  --prefill N            prefill periods (default %d)\n"
            "  --period-frames N      force period size\n"
            "  --capture-card N       UAC2 card (default auto)\n"
            "  --playback-card N      internal card (default auto)\n"
            "  --capture-device N     UAC2 PCM device (default 0)\n"
            "  --playback-device N    internal PCM device (default 0)\n"
            "  --capture-format FMT   s16le, s24_3le, s24le or s32le\n"
            "  --playback-format FMT  s16le, s24_3le, s24le or s32le\n"
            "  --log PATH             append log to PATH\n"
            "  --self-test            run conversion self-test\n"
            "  --help                 show this help\n",
            argv0, DEFAULT_RATE, DEFAULT_CHANNELS, DEFAULT_PERIODS,
            DEFAULT_PREFILL);
}

static int parse_int(const char *v, int *out)
{
    char *end = NULL;
    long n;
    if (!v || !*v)
        return -1;
    errno = 0;
    n = strtol(v, &end, 0);
    if (errno || !end || *end != '\0' || n < -1 || n > 1000000)
        return -1;
    *out = (int)n;
    return 0;
}

static int parse_args(struct opts *o, int argc, char **argv)
{
    int i;
    for (i = 1; i < argc; i++) {
        const char *a = argv[i];
        const char *v = NULL;
        if (!strcmp(a, "--help") || !strcmp(a, "-h")) {
            usage(argv[0]);
            exit(0);
        }
        if (!strcmp(a, "--self-test")) {
            o->self_test = 1;
            continue;
        }
        if (i + 1 >= argc) {
            fprintf(stderr, "uac2d: missing value for %s\n", a);
            return -1;
        }
        v = argv[++i];
        if (!strcmp(a, "--config"))
            o->config_path = v;
        else if (!strcmp(a, "--rate") && parse_int(v, &o->rate) == 0)
            ;
        else if (!strcmp(a, "--channels") && parse_int(v, &o->channels) == 0)
            ;
        else if (!strcmp(a, "--periods") && parse_int(v, &o->periods) == 0)
            ;
        else if (!strcmp(a, "--prefill") &&
                 parse_int(v, &o->prefill_periods) == 0)
            ;
        else if (!strcmp(a, "--period-frames") &&
                 parse_int(v, &o->period_frames) == 0)
            ;
        else if (!strcmp(a, "--capture-card") &&
                 parse_int(v, &o->capture_card) == 0)
            ;
        else if (!strcmp(a, "--playback-card") &&
                 parse_int(v, &o->playback_card) == 0)
            ;
        else if (!strcmp(a, "--capture-device") &&
                 parse_int(v, &o->capture_device) == 0)
            ;
        else if (!strcmp(a, "--playback-device") &&
                 parse_int(v, &o->playback_device) == 0)
            ;
        else if (!strcmp(a, "--capture-format"))
            o->capture_format = fmt_from_name(v);
        else if (!strcmp(a, "--playback-format"))
            o->playback_format = fmt_from_name(v);
        else if (!strcmp(a, "--log"))
            o->log_path = v;
        else {
            fprintf(stderr, "uac2d: unknown or invalid option %s\n", a);
            return -1;
        }
    }
    return 0;
}

static void trim(char *s)
{
    size_t n;
    char *p = s;
    while (*p == ' ' || *p == '\t')
        p++;
    if (p != s)
        memmove(s, p, strlen(p) + 1);
    n = strlen(s);
    while (n > 0 && (s[n - 1] == '\n' || s[n - 1] == '\r' ||
                     s[n - 1] == ' ' || s[n - 1] == '\t'))
        s[--n] = '\0';
}

static int load_config(struct opts *o, const char *path)
{
    FILE *f;
    char line[256];

    f = fopen(path, "r");
    if (!f)
        return -errno;
    while (fgets(line, sizeof(line), f)) {
        char *eq;
        char *key;
        char *val;
        trim(line);
        if (!line[0] || line[0] == '#')
            continue;
        eq = strchr(line, '=');
        if (!eq)
            continue;
        *eq = '\0';
        key = line;
        val = eq + 1;
        trim(key);
        trim(val);
        if (!strcmp(key, "rate"))
            parse_int(val, &o->rate);
        else if (!strcmp(key, "channels"))
            parse_int(val, &o->channels);
        else if (!strcmp(key, "periods"))
            parse_int(val, &o->periods);
        else if (!strcmp(key, "prefill_periods"))
            parse_int(val, &o->prefill_periods);
        else if (!strcmp(key, "period_frames"))
            parse_int(val, &o->period_frames);
        else if (!strcmp(key, "capture_card"))
            parse_int(val, &o->capture_card);
        else if (!strcmp(key, "playback_card"))
            parse_int(val, &o->playback_card);
        else if (!strcmp(key, "capture_device"))
            parse_int(val, &o->capture_device);
        else if (!strcmp(key, "playback_device"))
            parse_int(val, &o->playback_device);
        else if (!strcmp(key, "capture_format"))
            o->capture_format = fmt_from_name(val);
        else if (!strcmp(key, "playback_format"))
            o->playback_format = fmt_from_name(val);
        else if (!strcmp(key, "log")) {
            snprintf(o->log_path_buf, sizeof(o->log_path_buf), "%s", val);
            o->log_path = o->log_path_buf;
        }
    }
    fclose(f);
    return 0;
}

int main(int argc, char **argv)
{
    struct opts o;
    int ret;

    memset(&o, 0, sizeof(o));
    o.rate = DEFAULT_RATE;
    o.channels = DEFAULT_CHANNELS;
    o.periods = DEFAULT_PERIODS;
    o.prefill_periods = DEFAULT_PREFILL;
    o.period_frames = 0;
    o.capture_card = -1;
    o.playback_card = -1;
    o.capture_device = 0;
    o.playback_device = 0;
    o.capture_format = -1;
    o.playback_format = -1;
    o.config_path = NULL;

    {
        int i;
        for (i = 1; i + 1 < argc; i++) {
            if (!strcmp(argv[i], "--config")) {
                o.config_path = argv[i + 1];
                break;
            }
        }
    }
    if (o.config_path && access(o.config_path, R_OK) == 0)
        load_config(&o, o.config_path);
    if (parse_args(&o, argc, argv) < 0)
        return 2;
    if (o.self_test)
        return self_test();
    if (o.channels < 1 || o.channels > 8 || o.rate < 8000 ||
        o.rate > 384000 || o.periods < 2 || o.prefill_periods < 0) {
        fprintf(stderr, "uac2d: invalid audio parameters\n");
        return 2;
    }

    if (o.log_path && *o.log_path) {
        log_fp = fopen(o.log_path, "a");
        if (!log_fp) {
            fprintf(stderr, "uac2d: cannot open log %s: %s\n", o.log_path,
                    strerror(errno));
            return 1;
        }
    } else {
        log_fp = stderr;
    }

    signal(SIGTERM, on_signal);
    signal(SIGINT, on_signal);
    signal(SIGPIPE, SIG_IGN);

    while (!stop_requested) {
        ret = run_bridge(&o);
        if (stop_requested)
            break;
        if (ret < 0)
            log_msg("uac2d: bridge unavailable (%s), retrying\n",
                    strerror(-ret));
        msleep(1000);
    }

    log_msg("uac2d: stopped\n");
    if (log_fp && log_fp != stderr)
        fclose(log_fp);
    return 0;
}
