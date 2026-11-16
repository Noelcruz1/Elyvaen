#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <math.h>
#include <poll.h>
#include <sched.h>
#include <signal.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/resource.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/uio.h>
#include <sys/un.h>
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
#define UAC2_RATE_CHANGED (-2000)
#define FADE_FRAMES 64
#define MAX_CHANNELS 8

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
    int cpu;
    const char *config_path;
    const char *log_path;
    char log_path_buf[256];
    int self_test;
    int dump_mixer;
    int fifo_output;
    const char *fifo_path;
    char fifo_path_buf[256];
    const char *rate_path;
    char rate_path_buf[256];
    const char *mode_path;
    char mode_path_buf[256];
    const char *status_path;
    char status_path_buf[256];
    int out_rate;
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

/* Minimal logd writer (Android 10+ protocol: android_log_header_t +
 * priority + tag + message). Used so the daemon is visible in logcat
 * when running as an init service with stderr redirected to /dev/null. */
struct logd_header {
    uint8_t id;
    uint16_t tid;
    uint32_t sec;
    uint32_t nsec;
} __attribute__((packed));

static int logd_fd = -1;

#define LOGD_ID_MAIN 0
#define LOGD_PRIO_INFO 4

static void logd_log(int prio, const char *msg)
{
    struct sockaddr_un un;
    struct iovec vec[4];
    struct logd_header hdr;
    struct timespec ts;
    static char tag[] = "elyvaen-uac2d";
    char line[1024];
    uint8_t p = (uint8_t)prio;
    size_t len;

    if (logd_fd < 0) {
        int fd = socket(AF_UNIX, SOCK_DGRAM | SOCK_CLOEXEC, 0);

        if (fd < 0)
            return;
        memset(&un, 0, sizeof(un));
        un.sun_family = AF_UNIX;
        strncpy(un.sun_path, "/dev/socket/logdw",
                sizeof(un.sun_path) - 1);
        if (connect(fd, (struct sockaddr *)&un, sizeof(un)) < 0) {
            close(fd);
            return;
        }
        logd_fd = fd;
    }

    len = strlen(msg);
    if (len >= sizeof(line))
        len = sizeof(line) - 1;
    memcpy(line, msg, len);
    line[len] = '\0';
    while (len > 0 && (line[len - 1] == '\n' || line[len - 1] == '\r'))
        line[--len] = '\0';

    clock_gettime(CLOCK_REALTIME, &ts);
    hdr.id = LOGD_ID_MAIN;
    hdr.tid = (uint16_t)syscall(SYS_gettid);
    hdr.sec = (uint32_t)ts.tv_sec;
    hdr.nsec = (uint32_t)ts.tv_nsec;

    vec[0].iov_base = &hdr;
    vec[0].iov_len = sizeof(hdr);
    vec[1].iov_base = &p;
    vec[1].iov_len = sizeof(p);
    vec[2].iov_base = tag;
    vec[2].iov_len = sizeof(tag);
    vec[3].iov_base = line;
    vec[3].iov_len = len + 1;
    (void)writev(logd_fd, vec, 4);
}

static void log_msg(const char *fmt, ...)
{
    char buf[1024];
    va_list ap;

    va_start(ap, fmt);
    vsnprintf(buf, sizeof(buf), fmt, ap);
    va_end(ap);

    if (!log_fp)
        log_fp = stderr;
    fputs(buf, log_fp);
    fflush(log_fp);
    logd_log(LOGD_PRIO_INFO, buf);
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

static int frame_peak(int fmt, const uint8_t *buf, int frames, int channels)
{
    int samples = frames * channels;
    int peak = 0;
    int i;

    for (i = 0; i < samples; i++) {
        int32_t v;

        if (fmt == FMT_S16) {
            v = (int32_t)(int16_t)((uint16_t)buf[i * 2] |
                                   ((uint16_t)buf[i * 2 + 1] << 8));
        } else if (fmt == FMT_S24_3) {
            const uint8_t *p = buf + i * 3;
            uint32_t u = (uint32_t)p[0] | ((uint32_t)p[1] << 8) |
                         ((uint32_t)p[2] << 16);

            v = ((int32_t)(u << 8)) >> 8;
        } else {
            const uint8_t *p = buf + i * 4;
            uint32_t u = (uint32_t)p[0] | ((uint32_t)p[1] << 8) |
                         ((uint32_t)p[2] << 16) |
                         ((uint32_t)p[3] << 24);

            v = (fmt == FMT_S24_4) ? (((int32_t)(u << 8)) >> 8) :
                (int32_t)u;
        }
        if (v < 0)
            v = -v;
        if (v > peak)
            peak = v;
    }
    return peak;
}

static unsigned long long frame_abs_sum(int fmt, const uint8_t *buf,
                                        int frames, int channels)
{
    int samples = frames * channels;
    unsigned long long sum = 0;
    int i;

    for (i = 0; i < samples; i++) {
        int32_t v;

        if (fmt == FMT_S16) {
            v = (int32_t)(int16_t)((uint16_t)buf[i * 2] |
                                   ((uint16_t)buf[i * 2 + 1] << 8));
        } else if (fmt == FMT_S24_3) {
            const uint8_t *p = buf + i * 3;
            uint32_t u = (uint32_t)p[0] | ((uint32_t)p[1] << 8) |
                         ((uint32_t)p[2] << 16);

            v = ((int32_t)(u << 8)) >> 8;
        } else {
            const uint8_t *p = buf + i * 4;
            uint32_t u = (uint32_t)p[0] | ((uint32_t)p[1] << 8) |
                         ((uint32_t)p[2] << 16) |
                         ((uint32_t)p[3] << 24);

            v = (fmt == FMT_S24_4) ? (((int32_t)(u << 8)) >> 8) :
                (int32_t)u;
        }
        if (v < 0)
            v = -v;
        sum += (unsigned int)v;
    }
    return sum;
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

static void params_any(struct snd_pcm_hw_params *p)
{
    int i;

    memset(p, 0, sizeof(*p));
    for (i = SNDRV_PCM_HW_PARAM_FIRST_MASK;
         i <= SNDRV_PCM_HW_PARAM_LAST_MASK; i++) {
        struct snd_mask *m = param_mask(p, i);
        if (m)
            memset(m, 0xff, sizeof(*m));
    }
    for (i = SNDRV_PCM_HW_PARAM_FIRST_INTERVAL;
         i <= SNDRV_PCM_HW_PARAM_LAST_INTERVAL; i++) {
        struct snd_interval *v = param_interval(p, i);
        if (v) {
            memset(v, 0, sizeof(*v));
            v->min = 0;
            v->max = UINT_MAX;
        }
    }
}

static int pcm_set_hw_params_mode(struct stream *s, int period_frames,
                                  int auto_mode, int min_period)
{
    struct snd_pcm_hw_params p;
    struct snd_mask *m;
    struct snd_interval *i;
    int ret;

    params_any(&p);
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

    i = param_interval(&p, SNDRV_PCM_HW_PARAM_CHANNELS);
    if (!i)
        return -EINVAL;
    interval_exact(i, (unsigned int)s->channels);

    i = param_interval(&p, SNDRV_PCM_HW_PARAM_RATE);
    if (!i)
        return -EINVAL;
    interval_exact(i, (unsigned int)s->rate);

    if (!auto_mode) {
        i = param_interval(&p, SNDRV_PCM_HW_PARAM_SAMPLE_BITS);
        if (!i)
            return -EINVAL;
        interval_exact(i, (unsigned int)fmt_bits(s->format));
    }

    i = param_interval(&p, SNDRV_PCM_HW_PARAM_PERIOD_SIZE);
    if (!i)
        return -EINVAL;
    if (min_period > 0) {
        i->min = (unsigned int)min_period;
        i->max = UINT_MAX;
        i->openmin = 0;
        i->openmax = 0;
        i->integer = 1;
        i->empty = 0;

        i = param_interval(&p, SNDRV_PCM_HW_PARAM_BUFFER_SIZE);
        if (!i)
            return -EINVAL;
        i->min = (unsigned int)min_period * 4;
        i->max = UINT_MAX;
        i->openmin = 0;
        i->openmax = 0;
        i->integer = 1;
        i->empty = 0;
    } else if (!auto_mode) {
        interval_exact(i, (unsigned int)period_frames);
    }

    if (!auto_mode && min_period <= 0) {
        i = param_interval(&p, SNDRV_PCM_HW_PARAM_PERIODS);
        if (!i)
            return -EINVAL;
        interval_exact(i, (unsigned int)s->periods);
    }

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

static int pcm_set_hw_params(struct stream *s, int period_frames,
                             int min_period)
{
    int ret;

    ret = pcm_set_hw_params_mode(s, period_frames, 0, min_period);
    if (ret == -EINVAL) {
        ret = pcm_set_hw_params_mode(s, period_frames, 1, min_period);
        if (ret == 0)
            log_msg("uac2d: hw_params auto: period=%d buffer=%d\n",
                    s->period_frames, s->buffer_frames);
    }
    return ret;
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
                    int min_period, int requested_format,
                    int *selected_format)
{
    char path[64];
    int formats[FMT_COUNT];
    int format_count = 0;
    int period_candidates[6];
    int period_count = 0;
    int first_open_err = 0;
    int first_hw_err = 0;
    int first_hw_fmt = -1;
    int first_hw_period = 0;
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
            s->fd = open(path, O_NONBLOCK | O_CLOEXEC |
                         (capture ? O_RDONLY : O_WRONLY));
            if (s->fd < 0)
                s->fd = open(path, O_RDWR | O_NONBLOCK | O_CLOEXEC);
            if (s->fd < 0) {
                if (!first_open_err)
                    first_open_err = -errno;
                return -errno;
            }
            {
                struct snd_pcm_info info;

                memset(&info, 0, sizeof(info));
                info.subdevice = 0;
                if (ioctl(s->fd, SNDRV_PCM_IOCTL_INFO, &info) == 0)
                    log_msg("uac2d: pcm %s id='%s' name='%s' sub='%s'\n",
                            path, info.id, info.name, info.subname);
            }
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

            ret = pcm_set_hw_params(s, pf, min_period);
            if (ret == 0) {
                *selected_format = formats[f];
                return 0;
            }
            if (!first_hw_err) {
                first_hw_err = ret;
                first_hw_fmt = formats[f];
                first_hw_period = pf;
            }
            close(s->fd);
            s->fd = -1;
        }
    }
    {
        static time_t last_probe;
        time_t now = time(NULL);

        if (now - last_probe >= 10) {
            log_msg("uac2d: probe %s: open=%s hw=%s fmt=%s period=%d\n",
                    path,
                    first_open_err ? strerror(-first_open_err) : "ok",
                    first_hw_err ? strerror(-first_hw_err) : "ok",
                    first_hw_fmt >= 0 ? fmt_name(first_hw_fmt) : "-",
                    first_hw_period);
            last_probe = now;
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

static int uac2_control_value(int card, const char *name, int *value)
{
    char path[64];
    struct snd_ctl_elem_value val;
    int fd;
    int ret;

    snprintf(path, sizeof(path), "/dev/snd/controlC%d", card);
    fd = open(path, O_RDWR | O_CLOEXEC);
    if (fd < 0)
        return -errno;
    memset(&val, 0, sizeof(val));
    val.id.iface = SNDRV_CTL_ELEM_IFACE_MIXER;
    snprintf((char *)val.id.name, sizeof(val.id.name), "%s", name);
    if (ioctl(fd, SNDRV_CTL_IOCTL_ELEM_READ, &val) < 0) {
        ret = -errno;
    } else {
        *value = (int)val.value.integer.value[0];
        ret = 0;
    }
    close(fd);
    return ret;
}

static int uac2_control_rate(int card)
{
    int value = 0;

    if (uac2_control_value(card, "UAC2 Capture Rate", &value) < 0)
        return -1;
    return value;
}

static int uac2_control_set(int card, const char *name, int value)
{
    char path[64];
    struct snd_ctl_elem_value val;
    int fd;
    int ret;

    snprintf(path, sizeof(path), "/dev/snd/controlC%d", card);
    fd = open(path, O_RDWR | O_CLOEXEC);
    if (fd < 0)
        return -errno;
    memset(&val, 0, sizeof(val));
    val.id.iface = SNDRV_CTL_ELEM_IFACE_MIXER;
    snprintf((char *)val.id.name, sizeof(val.id.name), "%s", name);
    val.value.integer.value[0] = value;
    if (ioctl(fd, SNDRV_CTL_IOCTL_ELEM_WRITE, &val) < 0)
        ret = -errno;
    else
        ret = 0;
    close(fd);
    return ret;
}

/* Set a mixer control by name. Enum controls are matched by item name;
 * integer controls parse the value. Returns 0 on success. */
static int ctl_set(int card, const char *name, const char *value)
{
    char path[64];
    struct snd_ctl_elem_info info;
    struct snd_ctl_elem_value val;
    int fd;
    int ret = -EINVAL;
    unsigned int i;

    snprintf(path, sizeof(path), "/dev/snd/controlC%d", card);
    fd = open(path, O_RDWR | O_CLOEXEC);
    if (fd < 0)
        return -errno;

    memset(&info, 0, sizeof(info));
    info.id.iface = SNDRV_CTL_ELEM_IFACE_MIXER;
    snprintf((char *)info.id.name, sizeof(info.id.name), "%s", name);
    if (ioctl(fd, SNDRV_CTL_IOCTL_ELEM_INFO, &info) < 0) {
        ret = -errno;
        goto out;
    }

    memset(&val, 0, sizeof(val));
    val.id = info.id;

    if (info.type == SNDRV_CTL_ELEM_TYPE_ENUMERATED) {
        for (i = 0; i < info.value.enumerated.items; i++) {
            info.value.enumerated.item = i;
            if (ioctl(fd, SNDRV_CTL_IOCTL_ELEM_INFO, &info) < 0)
                break;
            if (!strcasecmp((char *)info.value.enumerated.name, value)) {
                val.value.enumerated.item[0] = i;
                ret = 0;
                break;
            }
        }
    } else if (info.type == SNDRV_CTL_ELEM_TYPE_INTEGER ||
               info.type == SNDRV_CTL_ELEM_TYPE_BOOLEAN) {
        val.value.integer.value[0] = strtol(value, NULL, 0);
        ret = 0;
    }

    if (ret == 0 && ioctl(fd, SNDRV_CTL_IOCTL_ELEM_WRITE, &val) < 0)
        ret = -errno;
out:
    close(fd);
    return ret;
}

static void log_ctl_int(int card, const char *name)
{
    int value;

    if (uac2_control_value(card, name, &value) == 0)
        log_msg("uac2d: ctl %s=%d\n", name, value);
    else
        log_msg("uac2d: ctl %s unavailable\n", name);
}

/*
 * The QCOM PCM hw_params registers an ACDB app_type config for the
 * stream. A raw PCM open may clear it, losing the media calibration
 * (about 20 dB on this device). Re-writing the current values after the
 * open re-registers the HAL's media config for our stream.
 */
static void reapply_app_type(int card, int device)
{
    char name[64];
    char path[64];
    struct snd_ctl_elem_info info;
    struct snd_ctl_elem_value val;
    int fd;

    snprintf(name, sizeof(name), "Audio Stream %d App Type Cfg", device);
    snprintf(path, sizeof(path), "/dev/snd/controlC%d", card);
    fd = open(path, O_RDWR | O_CLOEXEC);
    if (fd < 0) {
        log_msg("uac2d: app_type: open %s: %s\n", path, strerror(errno));
        return;
    }
    memset(&info, 0, sizeof(info));
    info.id.iface = SNDRV_CTL_ELEM_IFACE_PCM;
    snprintf((char *)info.id.name, sizeof(info.id.name), "%.43s", name);
    if (ioctl(fd, SNDRV_CTL_IOCTL_ELEM_INFO, &info) < 0) {
        log_msg("uac2d: app_type: info %s: %s\n", name, strerror(errno));
        close(fd);
        return;
    }
    memset(&val, 0, sizeof(val));
    val.id = info.id;
    if (ioctl(fd, SNDRV_CTL_IOCTL_ELEM_READ, &val) < 0) {
        log_msg("uac2d: app_type: read %s: %s\n", name, strerror(errno));
        close(fd);
        return;
    }
    if (ioctl(fd, SNDRV_CTL_IOCTL_ELEM_WRITE, &val) < 0) {
        log_msg("uac2d: app_type: write %s: %s\n", name, strerror(errno));
        close(fd);
        return;
    }
    log_msg("uac2d: app_type re-registered %s count=%u\n", name,
            info.count);
    close(fd);
}

static const char *dma_rate_name(int rate)
{
    switch (rate) {
    case 8000:
        return "KHZ_8";
    case 11025:
        return "KHZ_11P025";
    case 16000:
        return "KHZ_16";
    case 22050:
        return "KHZ_22P05";
    case 32000:
        return "KHZ_32";
    case 44100:
        return "KHZ_44P1";
    case 48000:
        return "KHZ_48";
    case 88200:
        return "KHZ_88P2";
    case 96000:
        return "KHZ_96";
    case 176400:
        return "KHZ_176P4";
    case 192000:
        return "KHZ_192";
    default:
        return "KHZ_48";
    }
}

static void setup_playback_route(int card, int rate)
{
    (void)rate;
    static const struct {
        const char *name;
        const char *value;
    } ctrls[] = {
        /* MultiMedia1 -> RX CDC DMA -> WCD937x headphones. The HAL
         * uses this route for deep-buffer playback; SEC_MI2S_RX is the
         * speaker/AW882xx path and only leaks into the jack. */
        { "RX_CDC_DMA_RX_0 Audio Mixer MultiMedia1", "1" },
        { "RX_HPH_PWR_MODE", "LOHIFI" },
        { "RX HPH Mode", "CLS_H_LOHIFI" },
        { "RX_MACRO RX0 MUX", "AIF1_PB" },
        { "RX_MACRO RX1 MUX", "AIF1_PB" },
        { "RX_CDC_DMA_RX_0 Channels", "Two" },
        { "RX_CDC_DMA_RX_0 Format", "S24_3LE" },
        { "RX INT0_1 MIX1 INP0", "RX0" },
        { "RX INT1_1 MIX1 INP0", "RX1" },
        { "RX INT0 DEM MUX", "CLSH_DSM_OUT" },
        { "RX INT1 DEM MUX", "CLSH_DSM_OUT" },
        { "RX_COMP1 Switch", "1" },
        { "RX_COMP2 Switch", "1" },
        { "HPHL_COMP Switch", "1" },
        { "HPHR_COMP Switch", "1" },
        { "HPHL_RDAC Switch", "1" },
        { "HPHR_RDAC Switch", "1" },
        { "HPH_L Switch", "1" },
        { "HPH_R Switch", "1" },
        { "RX_CDC_DMA_RX_0_DL_HL Switch", "1" },
        /* The HAL's mixer_paths uses 84 for these; with the DMA route
         * finally correct, match the HAL instead of assuming 0 dB. */
        { "RX_RX0 Digital Volume", "84" },
        { "RX_RX1 Digital Volume", "84" },
        { "RX_RX0 Mix Digital Volume", "0" },
        { "RX_RX1 Mix Digital Volume", "0" },
        { "HPHL Volume", "20" },
        { "HPHR Volume", "20" },
    };
    int ok = 0;
    size_t i;

    for (i = 0; i < sizeof(ctrls) / sizeof(ctrls[0]); i++) {
        if (ctl_set(card, ctrls[i].name, ctrls[i].value) == 0)
            ok++;
    }
    if (ctl_set(card, "RX_CDC_DMA_RX_0 SampleRate",
                dma_rate_name(192000)) == 0)
        ok++;
    log_msg("uac2d: playback route controls applied: %d/%d rate=KHZ_192\n",
            ok, (int)(sizeof(ctrls) / sizeof(ctrls[0])) + 1);
    log_ctl_int(card, "RX_RX0 Digital Volume");
    log_ctl_int(card, "RX_RX1 Digital Volume");
    log_ctl_int(card, "HPHL Volume");
    log_ctl_int(card, "HPHR Volume");
}

static int32_t volume_gain_q15(int volume)
{
    double db;
    double gain;
    int32_t result;

    if (volume >= 0)
        return 32768;
    db = (double)volume / 256.0;
    gain = pow(10.0, db / 20.0);
    if (gain < 0.0)
        gain = 0.0;
    if (gain > 1.0)
        gain = 1.0;
    result = (int32_t)(gain * 32768.0 + 0.5);
    return result;
}

static void apply_gain(int fmt, uint8_t *buf, int frames, int channels,
                       int32_t gain)
{
    int samples = frames * channels;
    int i;

    if (gain == 32768)
        return;
    if (gain <= 0) {
        memset(buf, 0, (size_t)samples * (size_t)fmt_bytes(fmt));
        return;
    }

    for (i = 0; i < samples; i++) {
        if (fmt == FMT_S16) {
            int16_t v = (int16_t)((uint16_t)buf[i * 2] |
                                  ((uint16_t)buf[i * 2 + 1] << 8));
            int32_t out = (int32_t)(((int64_t)v * gain) >> 15);
            if (out > 32767)
                out = 32767;
            if (out < -32768)
                out = -32768;
            buf[i * 2] = (uint8_t)((uint16_t)(int16_t)out);
            buf[i * 2 + 1] = (uint8_t)(((uint16_t)(int16_t)out) >> 8);
        } else if (fmt == FMT_S24_3) {
            uint8_t *p = buf + i * 3;
            uint32_t u = (uint32_t)p[0] | ((uint32_t)p[1] << 8) |
                         ((uint32_t)p[2] << 16);
            int32_t v = ((int32_t)(u << 8)) >> 8;
            int32_t out = (int32_t)(((int64_t)v * gain) >> 15);
            uint32_t o;
            if (out > 8388607)
                out = 8388607;
            if (out < -8388608)
                out = -8388608;
            o = (uint32_t)out;
            p[0] = (uint8_t)o;
            p[1] = (uint8_t)(o >> 8);
            p[2] = (uint8_t)(o >> 16);
        } else if (fmt == FMT_S24_4) {
            uint8_t *p = buf + i * 4;
            uint32_t u = (uint32_t)p[0] | ((uint32_t)p[1] << 8) |
                         ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
            int32_t v = ((int32_t)(u << 8)) >> 8;
            int32_t out = (int32_t)(((int64_t)v * gain) >> 15);
            uint32_t o;
            if (out > 8388607)
                out = 8388607;
            if (out < -8388608)
                out = -8388608;
            o = (uint32_t)out;
            p[0] = (uint8_t)o;
            p[1] = (uint8_t)(o >> 8);
            p[2] = (uint8_t)(o >> 16);
            p[3] = (uint8_t)(o >> 24);
        } else {
            uint8_t *p = buf + i * 4;
            uint32_t u = (uint32_t)p[0] | ((uint32_t)p[1] << 8) |
                         ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
            int32_t v = (int32_t)u;
            int64_t out = ((int64_t)v * gain) >> 15;
            uint32_t o;
            if (out > 2147483647LL)
                out = 2147483647LL;
            if (out < -2147483648LL)
                out = -2147483648LL;
            o = (uint32_t)(int32_t)out;
            p[0] = (uint8_t)o;
            p[1] = (uint8_t)(o >> 8);
            p[2] = (uint8_t)(o >> 16);
            p[3] = (uint8_t)(o >> 24);
        }
    }
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

static int32_t clip_sample(int fmt, int64_t value)
{
    if (fmt == FMT_S16) {
        if (value > 32767)
            return 32767;
        if (value < -32768)
            return -32768;
        return (int32_t)value;
    }
    if (fmt == FMT_S24_3 || fmt == FMT_S24_4) {
        if (value > 8388607)
            return 8388607;
        if (value < -8388608)
            return -8388608;
        return (int32_t)value;
    }
    if (value > 2147483647LL)
        return 2147483647;
    if (value < -2147483648LL)
        return (-2147483647 - 1);
    return (int32_t)value;
}

static int32_t read_sample(int fmt, const uint8_t *p)
{
    if (fmt == FMT_S16) {
        return (int32_t)(int16_t)((uint16_t)p[0] | ((uint16_t)p[1] << 8));
    }
    if (fmt == FMT_S24_3) {
        uint32_t u = (uint32_t)p[0] | ((uint32_t)p[1] << 8) |
                     ((uint32_t)p[2] << 16);
        return ((int32_t)(u << 8)) >> 8;
    }
    if (fmt == FMT_S24_4) {
        uint32_t u = (uint32_t)p[0] | ((uint32_t)p[1] << 8) |
                     ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
        return ((int32_t)(u << 8)) >> 8;
    }
    {
        uint32_t u = (uint32_t)p[0] | ((uint32_t)p[1] << 8) |
                     ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
        return (int32_t)u;
    }
}

static void write_sample(int fmt, uint8_t *p, int32_t value)
{
    uint32_t u = (uint32_t)value;

    if (fmt == FMT_S16) {
        p[0] = (uint8_t)u;
        p[1] = (uint8_t)(u >> 8);
    } else if (fmt == FMT_S24_3) {
        p[0] = (uint8_t)u;
        p[1] = (uint8_t)(u >> 8);
        p[2] = (uint8_t)(u >> 16);
    } else {
        p[0] = (uint8_t)u;
        p[1] = (uint8_t)(u >> 8);
        p[2] = (uint8_t)(u >> 16);
        p[3] = (uint8_t)(u >> 24);
    }
}

static void crossfade_data(int fmt, int channels, int frames, uint8_t *buf,
                           int32_t *last, int *fade)
{
    int bpf = fmt_bytes(fmt);
    int i;
    int c;

    for (i = 0; i < frames; i++) {
        for (c = 0; c < channels; c++) {
            uint8_t *p = buf + ((size_t)i * channels + c) * (size_t)bpf;
            int32_t next = read_sample(fmt, p);
            int32_t out = next;
            if (*fade > 0) {
                double alpha = 1.0 - (double)*fade / (double)FADE_FRAMES;
                int64_t mixed = (int64_t)last[c] +
                    (int64_t)((double)((int64_t)next - last[c]) * alpha);
                out = clip_sample(fmt, mixed);
            }
            write_sample(fmt, p, out);
            last[c] = out;
        }
        if (*fade > 0)
            (*fade)--;
    }
}

static void fill_silence_fade(int fmt, int channels, int frames, uint8_t *buf,
                              int32_t *last, int *fade)
{
    int bpf = fmt_bytes(fmt);
    int i;
    int c;

    memset(buf, 0, (size_t)frames * channels * (size_t)bpf);
    if (*fade <= 0)
        *fade = FADE_FRAMES;
    for (i = 0; i < frames; i++) {
        for (c = 0; c < channels; c++) {
            uint8_t *p = buf + ((size_t)i * channels + c) * (size_t)bpf;
            int32_t out = 0;
            if (*fade > 0) {
                double alpha = 1.0 - (double)*fade / (double)FADE_FRAMES;
                out = clip_sample(fmt, (int64_t)((double)last[c] *
                                                 (1.0 - alpha)));
            }
            write_sample(fmt, p, out);
            last[c] = out;
        }
        if (*fade > 0)
            (*fade)--;
    }
    *fade = FADE_FRAMES;
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

struct frame_ring {
    uint8_t *data;
    int capacity;
    int frame_bytes;
    int read_frame;
    int write_frame;
    int fill;
    int target;
};

static int ring_init(struct frame_ring *r, int capacity, int frame_bytes,
                     int target)
{
    memset(r, 0, sizeof(*r));
    r->data = calloc((size_t)capacity, (size_t)frame_bytes);
    if (!r->data)
        return -ENOMEM;
    r->capacity = capacity;
    r->frame_bytes = frame_bytes;
    r->target = target;
    if (r->target < 1)
        r->target = 1;
    if (r->target > capacity)
        r->target = capacity;
    return 0;
}

static int ring_drop(struct frame_ring *r, int frames)
{
    if (frames <= 0)
        return 0;
    if (frames > r->fill)
        frames = r->fill;
    r->read_frame = (r->read_frame + frames) % r->capacity;
    r->fill -= frames;
    return frames;
}

static int ring_push(struct frame_ring *r, const uint8_t *src, int frames)
{
    int dropped = 0;
    int first;

    if (frames <= 0)
        return 0;
    if (frames > r->capacity) {
        src += (size_t)(frames - r->capacity) * (size_t)r->frame_bytes;
        frames = r->capacity;
    }
    while (r->fill + frames > r->capacity) {
        ring_drop(r, 1);
        dropped++;
    }
    first = frames;
    if (first > r->capacity - r->write_frame)
        first = r->capacity - r->write_frame;
    memcpy(r->data + (size_t)r->write_frame * (size_t)r->frame_bytes, src,
           (size_t)first * (size_t)r->frame_bytes);
    if (frames > first) {
        memcpy(r->data, src + (size_t)first * (size_t)r->frame_bytes,
               (size_t)(frames - first) * (size_t)r->frame_bytes);
    }
    r->write_frame = (r->write_frame + frames) % r->capacity;
    r->fill += frames;
    if (r->fill > r->target) {
        dropped += ring_drop(r, r->fill - r->target);
    }
    return dropped;
}

static int ring_peek(struct frame_ring *r, uint8_t *dst, int frames)
{
    int n;
    int first;

    if (frames <= 0)
        return 0;
    n = frames < r->fill ? frames : r->fill;
    first = n;
    if (first > r->capacity - r->read_frame)
        first = r->capacity - r->read_frame;
    memcpy(dst, r->data + (size_t)r->read_frame * (size_t)r->frame_bytes,
           (size_t)first * (size_t)r->frame_bytes);
    if (n > first) {
        memcpy(dst + (size_t)first * (size_t)r->frame_bytes, r->data,
               (size_t)(n - first) * (size_t)r->frame_bytes);
    }
    return n;
}

static void ring_consume(struct frame_ring *r, int frames)
{
    if (frames <= 0)
        return;
    if (frames > r->fill)
        frames = r->fill;
    r->read_frame = (r->read_frame + frames) % r->capacity;
    r->fill -= frames;
}

static void ring_free(struct frame_ring *r)
{
    free(r->data);
    r->data = NULL;
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
    int pb_period;
    int prefill;
    time_t next_rate_check;
    struct frame_ring ring;
    unsigned long long drops = 0;
    unsigned long long xruns_in = 0;
    unsigned long long xruns_out = 0;
    unsigned long long silence_frames = 0;
    int cap_peak = 0;
    int pb_peak = 0;
    unsigned long long cap_abs = 0;
    unsigned long long cap_cnt = 0;
    unsigned long long pb_abs = 0;
    unsigned long long pb_cnt = 0;
    int host_volume = 0;
    int host_mute = 0;
    int32_t gain_q15 = 32768;
    int32_t last_sample[MAX_CHANNELS] = { 0 };
    int fade_remaining = 0;
    uint8_t *capbuf = NULL;
    uint8_t *pbbuf = NULL;
    uint8_t *silence = NULL;

    memset(&cap, 0, sizeof(cap));
    memset(&pb, 0, sizeof(pb));
    memset(&ring, 0, sizeof(ring));
    cap.fd = -1;
    pb.fd = -1;

    card_count = scan_cards(cards, MAX_CARDS);
    if (card_count < 0) {
        log_msg("uac2d: no /proc/asound/cards: %s\n", strerror(-card_count));
        return card_count;
    }

    {
        static time_t last_cards;
        time_t now = time(NULL);
        int c;

        if (now - last_cards >= 30) {
            for (c = 0; c < card_count; c++)
                log_msg("uac2d: card %d: %s\n", cards[c].number,
                        cards[c].name);
            last_cards = now;
        }
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
        int current_rate = uac2_control_rate(cap_card);
        int rates[13];
        int rate_count = 13;
        static int rate_logs;
        int r;
        int opened = 0;

        if (current_rate != o->rate) {
            int set_ret = uac2_control_set(cap_card, "UAC2 Capture Rate",
                                           o->rate);

            if (set_ret == 0) {
                log_msg("uac2d: forced UAC2 capture rate %d -> %d\n",
                        current_rate, o->rate);
                current_rate = uac2_control_rate(cap_card);
            } else {
                log_msg("uac2d: cannot set UAC2 rate %d: %s\n", o->rate,
                        strerror(-set_ret));
            }
        }
        rates[0] = current_rate;
        rates[1] = o->rate;
        rates[2] = 192000;
        rates[3] = 176400;
        rates[4] = 96000;
        rates[5] = 88200;
        rates[6] = 48000;
        rates[7] = 44100;
        rates[8] = 32000;
        rates[9] = 24000;
        rates[10] = 22050;
        rates[11] = 16000;
        rates[12] = 8000;

        if (rate_logs < 4) {
            log_msg("uac2d: control rate=%d config rate=%d\n",
                    current_rate, o->rate);
            rate_logs++;
        }

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
                           o->channels, o->periods, period, 0,
                           o->capture_format, &cap_fmt);
            if (ret == 0) {
                o->rate = candidate;
                opened = 1;
                break;
            }
            if (rate_logs < 24) {
                log_msg("uac2d: rate %d rejected: %s\n", candidate,
                        strerror(-ret));
                rate_logs++;
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
    if (cap_bytes <= 0 || period <= 0) {
        close(cap.fd);
        return -EINVAL;
    }

    setup_playback_route(pb_card, o->rate);

    ret = open_pcm(&pb, pb_card, o->playback_device, 0, o->rate,
                   o->channels, o->periods, period, 256,
                   o->playback_format, &pb_fmt);
    if (ret < 0) {
        log_msg("uac2d: cannot open playback card %d device %d: %s\n",
                pb_card, o->playback_device, strerror(-ret));
        close(cap.fd);
        return ret;
    }
    pb_bytes = pb.bytes_per_frame;
    if (pb_bytes <= 0) {
        close(cap.fd);
        close(pb.fd);
        return -EINVAL;
    }
    /* hw_params may reset codec gains; re-apply them after the PCM is
     * configured (the DMA format/rate stay the same). */
    setup_playback_route(pb_card, o->rate);
    reapply_app_type(pb_card, o->playback_device);
    pb_period = period;
    if (pb_period <= 0)
        pb_period = 256;
    max_frame_bytes = cap_bytes > pb_bytes ? cap_bytes : pb_bytes;

    prefill = o->prefill_periods * pb_period;
    if (prefill > pb.buffer_frames)
        prefill = pb.buffer_frames;

    capbuf = malloc((size_t)period * (size_t)cap_bytes);
    pbbuf = malloc((size_t)pb_period * (size_t)max_frame_bytes);
    silence = calloc((size_t)pb_period, (size_t)pb_bytes);
    if (!capbuf || !pbbuf || !silence ||
        ring_init(&ring, pb.buffer_frames * 2, pb_bytes,
                  pb.buffer_frames) < 0) {
        log_msg("uac2d: out of memory\n");
        free(capbuf);
        free(pbbuf);
        free(silence);
        ring_free(&ring);
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
    fade_remaining = FADE_FRAMES;

    if (uac2_control_value(cap.card, "UAC2 Host Volume", &host_volume) < 0)
        host_volume = 0;
    if (uac2_control_value(cap.card, "UAC2 Host Mute", &host_mute) < 0)
        host_mute = 0;
    gain_q15 = host_mute ? 0 : volume_gain_q15(host_volume);
    log_msg("uac2d: initial host volume=%d mute=%d gain=%d\n",
            host_volume, host_mute, gain_q15);

    next_rate_check = time(NULL) + 1;
    {
        time_t next_stats = time(NULL) + 5;
        while (!stop_requested) {
        struct pollfd fds[2];
        int pr;

        if (time(NULL) >= next_rate_check) {
            int live_rate = uac2_control_rate(cap.card);
            if (live_rate > 0 && live_rate != cap.rate) {
                log_msg("uac2d: host rate changed %d -> %d, reopening\n",
                        cap.rate, live_rate);
                ret = UAC2_RATE_CHANGED;
                break;
            }
            if (uac2_control_value(cap.card, "UAC2 Host Volume",
                                   &host_volume) == 0 &&
                uac2_control_value(cap.card, "UAC2 Host Mute",
                                   &host_mute) == 0) {
                int32_t new_gain = host_mute ? 0 :
                                   volume_gain_q15(host_volume);
                if (new_gain != gain_q15) {
                    log_msg("uac2d: host volume=%d mute=%d gain=%d\n",
                            host_volume, host_mute, new_gain);
                    gain_q15 = new_gain;
                }
            }
            next_rate_check = time(NULL) + 1;
            if (time(NULL) >= next_stats) {
                log_msg("uac2d: stats drops=%llu xruns_in=%llu "
                        "xruns_out=%llu silence=%llu cap_peak=%d "
                        "pb_peak=%d cap_avg=%llu pb_avg=%llu\n",
                        drops, xruns_in, xruns_out, silence_frames,
                        cap_peak, pb_peak,
                        cap_cnt ? cap_abs / cap_cnt : 0,
                        pb_cnt ? pb_abs / pb_cnt : 0);
                cap_peak = 0;
                pb_peak = 0;
                cap_abs = 0;
                cap_cnt = 0;
                pb_abs = 0;
                pb_cnt = 0;
                next_stats = time(NULL) + 5;
            }
        }

        fds[0].fd = cap.fd;
        fds[0].events = POLLIN;
        fds[0].revents = 0;
        fds[1].fd = pb.fd;
        fds[1].events = POLLOUT;
        fds[1].revents = 0;
        pr = poll(fds, 2, 20);
        if (pr < 0) {
            if (errno == EINTR)
                continue;
            ret = -errno;
            break;
        }
        if (pr == 0) {
            int n = ring_peek(&ring, pbbuf, pb_period);
            int w = 0;
            if (n >= pb_period) {
                crossfade_data(pb_fmt, o->channels, pb_period, pbbuf,
                               last_sample, &fade_remaining);
                w = pcm_write(&pb, pbbuf, pb_period);
                if (w > 0)
                    ring_consume(&ring, w);
            } else if (n == 0) {
                fill_silence_fade(pb_fmt, o->channels, pb_period, silence,
                                  last_sample, &fade_remaining);
                w = pcm_write(&pb, silence, pb_period);
                if (w > 0)
                    silence_frames += (unsigned long long)w;
            } else {
                /* No event for 20 ms and only a partial block left:
                 * flush it rather than letting the PCM underrun. */
                crossfade_data(pb_fmt, o->channels, n, pbbuf, last_sample,
                               &fade_remaining);
                w = pcm_write(&pb, pbbuf, n);
                if (w > 0)
                    ring_consume(&ring, w);
            }
            if (w == -EPIPE) {
                xruns_out++;
                pcm_prepare(&pb);
                pcm_start(&pb);
            } else if (w < 0 && w != -EAGAIN) {
                log_msg("uac2d: playback write: %s\n", strerror(-w));
                ret = w;
                break;
            }
            continue;
        }

        if (fds[0].revents & (POLLIN | POLLERR | POLLHUP)) {
            int n = pcm_read(&cap, capbuf, period);
            if (n > 0) {
                convert_frames(capbuf, cap_fmt, pbbuf, pb_fmt, n,
                               o->channels);
                apply_gain(pb_fmt, pbbuf, n, o->channels, gain_q15);
                {
                    int p = frame_peak(cap_fmt, capbuf, n, o->channels);

                    if (p > cap_peak)
                        cap_peak = p;
                    p = frame_peak(pb_fmt, pbbuf, n, o->channels);
                    if (p > pb_peak)
                        pb_peak = p;
                    cap_abs += frame_abs_sum(cap_fmt, capbuf, n,
                                             o->channels);
                    cap_cnt += (unsigned long long)n * o->channels;
                    pb_abs += frame_abs_sum(pb_fmt, pbbuf, n, o->channels);
                    pb_cnt += (unsigned long long)n * o->channels;
                }
                {
                    int dropped = ring_push(&ring, pbbuf, n);
                    if (dropped > 0) {
                        drops += (unsigned long long)dropped;
                        fade_remaining = FADE_FRAMES;
                    }
                }
            } else if (n == -EPIPE) {
                xruns_in++;
                pcm_prepare(&cap);
                pcm_start(&cap);
            } else if (n < 0 && n != -EAGAIN) {
                log_msg("uac2d: capture read: %s\n", strerror(-n));
                ret = n;
                break;
            }
        }

        if (fds[1].revents & (POLLOUT | POLLERR | POLLHUP)) {
            int n = ring_peek(&ring, pbbuf, pb_period);
            int w = 0;
            if (n >= pb_period) {
                crossfade_data(pb_fmt, o->channels, pb_period, pbbuf,
                               last_sample, &fade_remaining);
                w = pcm_write(&pb, pbbuf, pb_period);
                if (w > 0)
                    ring_consume(&ring, w);
            } else if (n == 0) {
                fill_silence_fade(pb_fmt, o->channels, pb_period, silence,
                                  last_sample, &fade_remaining);
                w = pcm_write(&pb, silence, pb_period);
                if (w > 0)
                    silence_frames += (unsigned long long)w;
            }
            if (w == -EPIPE) {
                xruns_out++;
                pcm_prepare(&pb);
                pcm_start(&pb);
            } else if (w < 0 && w != -EAGAIN) {
                log_msg("uac2d: playback write: %s\n", strerror(-w));
                ret = w;
                break;
            }
        }
        }
    }
    if (drops)
        log_msg("uac2d: drift drop frames=%llu\n", drops);

    pcm_drop(&pb);
    pcm_drop(&cap);

out:
    close(cap.fd);
    close(pb.fd);
    free(capbuf);
    free(pbbuf);
    free(silence);
    ring_free(&ring);
    return ret;
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

    {
        struct frame_ring r;
        uint8_t src[4 * 4];
        uint8_t dst[4 * 4];
        int j;

        if (ring_init(&r, 4, 4, 2) < 0)
            return 1;
        for (j = 0; j < 4; j++) {
            src[j * 4] = (uint8_t)j;
            src[j * 4 + 1] = (uint8_t)(j + 10);
            src[j * 4 + 2] = (uint8_t)(j + 20);
            src[j * 4 + 3] = (uint8_t)(j + 30);
        }
        if (ring_push(&r, src, 4) != 2) {
            log_msg("uac2d: self-test ring drop failed\n");
            ring_free(&r);
            return 1;
        }
        if (ring_peek(&r, dst, 2) != 2 || dst[0] != 2 || dst[4] != 3) {
            log_msg("uac2d: self-test ring data failed\n");
            ring_free(&r);
            return 1;
        }
        ring_consume(&r, 2);
        if (r.fill != 0) {
            log_msg("uac2d: self-test ring consume failed\n");
            ring_free(&r);
            return 1;
        }
        ring_free(&r);
    }

    log_msg("uac2d: self-test ok\n");
    return 0;
}

static void setup_realtime(int cpu)
{
    struct sched_param sp;
    cpu_set_t set;

    memset(&sp, 0, sizeof(sp));
    sp.sched_priority = 80;
    if (sched_setscheduler(0, SCHED_FIFO, &sp) < 0)
        log_msg("uac2d: SCHED_FIFO unavailable: %s\n", strerror(errno));
    else
        log_msg("uac2d: SCHED_FIFO priority 80\n");

    if (setpriority(PRIO_PROCESS, 0, -20) < 0)
        log_msg("uac2d: setpriority failed: %s\n", strerror(errno));

    if (mlockall(MCL_CURRENT | MCL_FUTURE) < 0)
        log_msg("uac2d: mlockall failed: %s\n", strerror(errno));
    else
        log_msg("uac2d: memory locked\n");

    if (cpu >= 0) {
        CPU_ZERO(&set);
        CPU_SET(cpu, &set);
        if (sched_setaffinity(0, sizeof(set), &set) < 0)
            log_msg("uac2d: affinity cpu %d failed: %s\n", cpu,
                    strerror(errno));
        else
            log_msg("uac2d: affinity cpu %d\n", cpu);
    }
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
            "  --cpu N                pin daemon to CPU N\n"
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
        if (!a)
            continue;
        if (!strcmp(a, "--help") || !strcmp(a, "-h")) {
            usage(argv[0]);
            exit(0);
        }
        if (!strcmp(a, "--self-test")) {
            o->self_test = 1;
            continue;
        }
        if (!strcmp(a, "--dump-mixer")) {
            o->dump_mixer = 1;
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
        else if (!strcmp(a, "--cpu") && parse_int(v, &o->cpu) == 0)
            ;
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
        else if (!strcmp(key, "output")) {
            if (!strcmp(val, "fifo"))
                o->fifo_output = 1;
        } else if (!strcmp(key, "fifo")) {
            snprintf(o->fifo_path_buf, sizeof(o->fifo_path_buf), "%s", val);
            o->fifo_path = o->fifo_path_buf;
        } else if (!strcmp(key, "rate_file")) {
            snprintf(o->rate_path_buf, sizeof(o->rate_path_buf), "%s", val);
            o->rate_path = o->rate_path_buf;
        } else if (!strcmp(key, "mode_file")) {
            snprintf(o->mode_path_buf, sizeof(o->mode_path_buf), "%s", val);
            o->mode_path = o->mode_path_buf;
        } else if (!strcmp(key, "status_file")) {
            snprintf(o->status_path_buf, sizeof(o->status_path_buf), "%s", val);
            o->status_path = o->status_path_buf;
        } else if (!strcmp(key, "out_rate"))
            parse_int(val, &o->out_rate);
        else if (!strcmp(key, "cpu"))
            parse_int(val, &o->cpu);
        else if (!strcmp(key, "log")) {
            snprintf(o->log_path_buf, sizeof(o->log_path_buf), "%s", val);
            o->log_path = o->log_path_buf;
        }
    }
    fclose(f);
    return 0;
}

static int write_fifo_all(int fd, const void *buf, size_t len)
{
    const uint8_t *p = buf;

    while (len > 0) {
        ssize_t n = write(fd, p, len);

        if (n < 0) {
            if (errno == EINTR && !stop_requested)
                continue;
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                struct pollfd pfd;

                pfd.fd = fd;
                pfd.events = POLLOUT;
                pfd.revents = 0;
                if (poll(&pfd, 1, 200) < 0 && errno != EINTR)
                    return -errno;
                if (pfd.revents & (POLLERR | POLLHUP | POLLNVAL))
                    return -EPIPE;
                if (stop_requested)
                    return -EINTR;
                continue;
            }
            return -errno;
        }
        p += n;
        len -= (size_t)n;
    }
    return 0;
}

static int write_rate_file(const char *path, int rate)
{
    char buf[32];
    int n;
    int fd;

    if (!path || !*path)
        return 0;
    n = snprintf(buf, sizeof(buf), "%d\n", rate);
    fd = open(path, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0666);
    if (fd < 0) {
        log_msg("uac2d: rate file %s: %s\n", path, strerror(errno));
        return -1;
    }
    (void)fchmod(fd, 0666);
    (void)write(fd, buf, (size_t)n);
    close(fd);
    return 0;
}

static int write_status_file(const char *path, int rate, int mode, int native,
                             int volume, int mute, int streaming,
                             unsigned long long frames,
                             unsigned long long errors)
{
    char buf[256];
    int n;
    int fd;

    if (!path || !*path)
        return 0;
    n = snprintf(buf, sizeof(buf),
                 "rate=%d\nmode=%d\nnative=%d\nhost_volume=%d\nhost_mute=%d\n"
                 "streaming=%d\nframes=%llu\nerrors=%llu\n",
                 rate, mode ? 1 : 0, native ? 1 : 0, volume, mute, streaming,
                 frames, errors);
    fd = open(path, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0666);
    if (fd < 0) {
        log_msg("uac2d: status file %s: %s\n", path, strerror(errno));
        return -1;
    }
    (void)fchmod(fd, 0666);
    (void)write(fd, buf, (size_t)n);
    close(fd);
    return 0;
}

static int read_mode_file(const char *path)
{
    char buf[32];
    char *end;
    long value;
    ssize_t n;
    int fd;

    if (!path || !*path)
        return -1;
    fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0)
        return -1;
    n = read(fd, buf, sizeof(buf) - 1);
    close(fd);
    if (n <= 0)
        return -1;
    buf[n] = '\0';
    value = strtol(buf, &end, 10);
    if (end != buf)
        return value > 0 ? 0 : 1;
    if (strstr(buf, "native"))
        return 1;
    if (strstr(buf, "48000"))
        return 0;
    return -1;
}

struct fifo_resamp {
    double phase;
    float prev[8];
    int init;
};

static int run_fifo(struct opts *o)
{
    struct card_info cards[MAX_CARDS];
    struct stream cap;
    struct fifo_resamp rs;
    int card_count;
    int cap_card;
    int cap_fmt = -1;
    int period;
    int open_rate;
    int ret;
    int fifo_fd = -1;
    int native_mode = o->out_rate <= 0;
    int fixed_rate = o->out_rate > 0 ? o->out_rate : 0;
    int out_rate;
    int native;
    int channels = o->channels;
    uint8_t *capbuf = NULL;
    float *outbuf = NULL;
    int max_out;
    const char *fifo = o->fifo_path ? o->fifo_path :
                       "/data/local/tmp/elyvaen_uac2.pcm";
    const char *rate_file = o->rate_path ? o->rate_path :
                            "/data/local/tmp/elyvaen_uac2.rate";
    const char *mode_file = o->mode_path ? o->mode_path :
                            "/data/local/tmp/elyvaen_uac2.mode";
    const char *status_file = o->status_path ? o->status_path :
                              "/data/local/tmp/elyvaen_uac2.status";
    int mode_state = -1;
    time_t next_rate;
    time_t next_stats;
    unsigned long long out_frames_total = 0;
    unsigned long long fifo_errors = 0;
    int host_volume = 0;
    int host_mute = 0;
    int32_t host_gain = 32768;

    memset(&cap, 0, sizeof(cap));
    cap.fd = -1;
    memset(&rs, 0, sizeof(rs));

    card_count = scan_cards(cards, MAX_CARDS);
    if (card_count < 0) {
        log_msg("uac2d: fifo: no cards: %s\n", strerror(-card_count));
        return card_count;
    }
    cap_card = o->capture_card >= 0 ? o->capture_card :
               find_uac2_card(cards, card_count);
    if (cap_card < 0) {
        log_msg("uac2d: fifo: UAC2 capture card not found\n");
        return -ENODEV;
    }
    mode_state = read_mode_file(mode_file);
    if (mode_state == 0) {
        native_mode = 0;
        fixed_rate = 48000;
        log_msg("uac2d: fifo: quality mode 48000 (compatibility)\n");
    } else if (mode_state == 1) {
        native_mode = 1;
        fixed_rate = 0;
        log_msg("uac2d: fifo: quality mode native\n");
    }
    if (channels < 1 || channels > 8)
        channels = 2;

    period = o->rate / 100;
    if (period < 32)
        period = 32;
    open_rate = o->rate;
    {
        int live_rate = uac2_control_rate(cap_card);

        if (live_rate > 0)
            open_rate = live_rate;
    }
    if (open_rate != o->rate) {
        period = open_rate / 100;
        if (period < 32)
            period = 32;
        log_msg("uac2d: fifo: live host rate %d (config %d)\n",
                open_rate, o->rate);
    }
    ret = open_pcm(&cap, cap_card, o->capture_device, 1, open_rate,
                   channels, o->periods, period, 0, FMT_S24_3, &cap_fmt);
    if (ret < 0) {
        log_msg("uac2d: fifo: cannot open capture card %d: %s\n",
                cap_card, strerror(-ret));
        return ret;
    }
    period = cap.period_frames;
    out_rate = native_mode ? cap.rate : fixed_rate;
    native = native_mode || out_rate == cap.rate;
    if (native)
        out_rate = cap.rate;
    capbuf = malloc((size_t)period * (size_t)cap.bytes_per_frame);
    max_out = native ? period : period * 8 + 16;
    outbuf = malloc((size_t)max_out * (size_t)channels * sizeof(float));
    if (!capbuf || !outbuf) {
        log_msg("uac2d: fifo: out of memory\n");
        free(capbuf);
        free(outbuf);
        close(cap.fd);
        return -ENOMEM;
    }
    write_rate_file(rate_file, out_rate);
    if (uac2_control_value(cap.card, "UAC2 Host Volume", &host_volume) < 0)
        host_volume = 0;
    if (uac2_control_value(cap.card, "UAC2 Host Mute", &host_mute) < 0)
        host_mute = 0;
    host_gain = host_mute ? 0 : volume_gain_q15(host_volume);
    log_msg("uac2d: fifo capture card %d rate=%d period=%d fifo=%s "
            "out=%d Hz float32%s host_volume=%d mute=%d\n",
            cap_card, cap.rate, period, fifo, out_rate,
            native ? " native" : " resampled", host_volume, host_mute);
    write_status_file(status_file, out_rate, native_mode, native,
                      host_volume, host_mute, 0, 0, 0);

    if (pcm_prepare(&cap) < 0 || pcm_start(&cap) < 0) {
        log_msg("uac2d: fifo: cannot start capture\n");
        free(capbuf);
        free(outbuf);
        close(cap.fd);
        return -EIO;
    }

    next_rate = time(NULL) + 1;
    next_stats = time(NULL) + 60;
    while (!stop_requested) {
        struct pollfd pfd;
        int pr;

        if (fifo_fd < 0) {
            if (mkfifo(fifo, 0666) < 0 && errno != EEXIST) {
                log_msg("uac2d: fifo: mkfifo %s: %s\n", fifo,
                        strerror(errno));
            }
            (void)chmod(fifo, 0666);
            fifo_fd = open(fifo, O_WRONLY | O_NONBLOCK | O_CLOEXEC);
            if (fifo_fd < 0) {
                if ((fifo_errors++ % 20) == 0)
                    log_msg("uac2d: fifo: waiting for reader on %s\n",
                            fifo);
                msleep(250);
                continue;
            }
            log_msg("uac2d: fifo: reader connected\n");
            write_status_file(status_file, out_rate, native_mode, native,
                              host_volume, host_mute, 1, out_frames_total,
                              fifo_errors);
        }

        pfd.fd = cap.fd;
        pfd.events = POLLIN;
        pfd.revents = 0;
        pr = poll(&pfd, 1, 200);
        if (pr > 0 && (pfd.revents & (POLLIN | POLLERR | POLLHUP))) {
            int n = pcm_read(&cap, capbuf, period);

            if (n > 0) {
                int out_frames = 0;
                int i;
                int c;
                float gain = host_gain > 0 ?
                             (float)host_gain / 32768.0f : 0.0f;

                if (native) {
                    for (i = 0; i < n; i++) {
                        for (c = 0; c < channels; c++) {
                            const uint8_t *p = capbuf +
                                ((size_t)i * channels + c) * 3;
                            uint32_t u = (uint32_t)p[0] |
                                         ((uint32_t)p[1] << 8) |
                                         ((uint32_t)p[2] << 16);
                            int32_t v = ((int32_t)(u << 8)) >> 8;

                            outbuf[(size_t)i * channels + c] =
                                ((float)v / 8388608.0f) * gain;
                        }
                    }
                    out_frames = n;
                } else {
                    for (i = 0; i < n; i++) {
                        float cur[8];
                        for (c = 0; c < channels; c++) {
                            const uint8_t *p = capbuf +
                                ((size_t)i * channels + c) * 3;
                            uint32_t u = (uint32_t)p[0] |
                                         ((uint32_t)p[1] << 8) |
                                         ((uint32_t)p[2] << 16);
                            int32_t v = ((int32_t)(u << 8)) >> 8;

                            cur[c] = (float)v / 8388608.0f;
                        }
                        if (!rs.init) {
                            for (c = 0; c < channels; c++)
                                rs.prev[c] = cur[c];
                            rs.init = 1;
                        }
                        while (rs.phase < 1.0 && out_frames < max_out) {
                            for (c = 0; c < channels; c++)
                                outbuf[(size_t)out_frames * channels + c] =
                                    (rs.prev[c] + (cur[c] - rs.prev[c]) *
                                     (float)rs.phase) * gain;
                            out_frames++;
                            rs.phase += (double)cap.rate / (double)out_rate;
                        }
                        rs.phase -= 1.0;
                        if (rs.phase < 0.0)
                            rs.phase = 0.0;
                        for (c = 0; c < channels; c++)
                            rs.prev[c] = cur[c];
                    }
                }
                if (out_frames > 0) {
                    ret = write_fifo_all(fifo_fd, outbuf,
                                         (size_t)out_frames *
                                         (size_t)channels * sizeof(float));
                    if (ret < 0) {
                        log_msg("uac2d: fifo: write: %s\n", strerror(-ret));
                        close(fifo_fd);
                        fifo_fd = -1;
                        fifo_errors++;
                        write_status_file(status_file, out_rate, native_mode,
                                          native, host_volume, host_mute, 0,
                                          out_frames_total, fifo_errors);
                        continue;
                    }
                    out_frames_total += (unsigned long long)out_frames;
                }
            } else if (n == -EPIPE) {
                pcm_prepare(&cap);
                pcm_start(&cap);
            } else if (n < 0 && n != -EAGAIN) {
                log_msg("uac2d: fifo: capture read: %s\n", strerror(-n));
                break;
            }
        }

        if (time(NULL) >= next_rate) {
            int live_rate = uac2_control_rate(cap.card);
            int new_mode = read_mode_file(mode_file);

            if (new_mode >= 0) {
                int old_native = mode_state != 0;
                int new_native = new_mode != 0;

                if (new_native != old_native) {
                    log_msg("uac2d: fifo: quality mode changed, reopening\n");
                    break;
                }
                mode_state = new_mode;
            }

            if (live_rate > 0 && live_rate != cap.rate) {
                log_msg("uac2d: fifo: host rate %d -> %d\n",
                        cap.rate, live_rate);
                pcm_drop(&cap);
                close(cap.fd);
                cap.fd = -1;
                ret = open_pcm(&cap, cap_card, o->capture_device, 1,
                               live_rate, channels, o->periods, 0, 0,
                               FMT_S24_3, &cap_fmt);
                if (ret < 0) {
                    log_msg("uac2d: fifo: reopen: %s\n", strerror(-ret));
                    break;
                }
                period = cap.period_frames;
                out_rate = native_mode ? cap.rate : fixed_rate;
                native = native_mode || out_rate == cap.rate;
                if (native)
                    out_rate = cap.rate;
                free(capbuf);
                free(outbuf);
                capbuf = malloc((size_t)period *
                                (size_t)cap.bytes_per_frame);
                max_out = native ? period : period * 8 + 16;
                outbuf = malloc((size_t)max_out * (size_t)channels *
                                sizeof(float));
                if (!capbuf || !outbuf)
                    break;
                memset(&rs, 0, sizeof(rs));
                write_rate_file(rate_file, out_rate);
                if (pcm_prepare(&cap) < 0 || pcm_start(&cap) < 0)
                    break;
                log_msg("uac2d: fifo: now out=%d Hz%s\n", out_rate,
                        native ? " native" : " resampled");
                write_status_file(status_file, out_rate, native_mode, native,
                                  host_volume, host_mute, 1,
                                  out_frames_total, fifo_errors);
            }
            if (uac2_control_value(cap.card, "UAC2 Host Volume",
                                   &host_volume) == 0 &&
                uac2_control_value(cap.card, "UAC2 Host Mute",
                                   &host_mute) == 0)
                host_gain = host_mute ? 0 : volume_gain_q15(host_volume);
            write_status_file(status_file, out_rate, native_mode, native,
                              host_volume, host_mute, fifo_fd >= 0,
                              out_frames_total, fifo_errors);
            next_rate = time(NULL) + 1;
        }

        if (time(NULL) >= next_stats) {
            log_msg("uac2d: fifo stats out_frames=%llu fifo_errors=%llu\n",
                    out_frames_total, fifo_errors);
            next_stats = time(NULL) + 60;
        }
    }

    if (fifo_fd >= 0)
        close(fifo_fd);
    if (cap.fd >= 0) {
        pcm_drop(&cap);
        close(cap.fd);
    }
    free(capbuf);
    free(outbuf);
    return 0;
}

static void dump_mixer(int card)
{
    char path[64];
    struct snd_ctl_elem_list list;
    struct snd_ctl_elem_id *ids = NULL;
    int fd;
    int i;
    int count;

    snprintf(path, sizeof(path), "/dev/snd/controlC%d", card);
    fd = open(path, O_RDWR | O_CLOEXEC);
    if (fd < 0) {
        log_msg("uac2d: mixdump: cannot open %s: %s\n", path,
                strerror(errno));
        return;
    }
    memset(&list, 0, sizeof(list));
    if (ioctl(fd, SNDRV_CTL_IOCTL_ELEM_LIST, &list) < 0) {
        log_msg("uac2d: mixdump: list count failed: %s\n", strerror(errno));
        close(fd);
        return;
    }
    count = (int)list.count;
    ids = calloc((size_t)count, sizeof(*ids));
    if (!ids) {
        close(fd);
        return;
    }
    list.pids = ids;
    list.space = (unsigned int)count;
    if (ioctl(fd, SNDRV_CTL_IOCTL_ELEM_LIST, &list) < 0) {
        log_msg("uac2d: mixdump: list failed: %s\n", strerror(errno));
        free(ids);
        close(fd);
        return;
    }
    log_msg("uac2d: mixdump card=%d count=%d\n", card, (int)list.used);
    for (i = 0; i < (int)list.used; i++) {
        struct snd_ctl_elem_info info;
        struct snd_ctl_elem_value val;

        memset(&info, 0, sizeof(info));
        info.id = ids[i];
        if (ioctl(fd, SNDRV_CTL_IOCTL_ELEM_INFO, &info) < 0)
            continue;
        memset(&val, 0, sizeof(val));
        val.id = ids[i];
        if (ioctl(fd, SNDRV_CTL_IOCTL_ELEM_READ, &val) < 0)
            continue;
        if (info.type == SNDRV_CTL_ELEM_TYPE_INTEGER ||
            info.type == SNDRV_CTL_ELEM_TYPE_BOOLEAN) {
            log_msg("uac2d: mix %s=%ld\n", (char *)info.id.name,
                    val.value.integer.value[0]);
        } else if (info.type == SNDRV_CTL_ELEM_TYPE_ENUMERATED) {
            log_msg("uac2d: mix %s=%u\n", (char *)info.id.name,
                    val.value.enumerated.item[0]);
        }
    }
    free(ids);
    close(fd);
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
    o.cpu = -1;
    o.config_path = NULL;
    o.out_rate = 0;

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
    if (o.dump_mixer) {
        dump_mixer(o.playback_card >= 0 ? o.playback_card : 0);
        return 0;
    }
    if (o.channels < 1 || o.channels > 8 || o.rate < 8000 ||
        o.rate > 384000 || o.periods < 2 || o.prefill_periods < 0 ||
        o.cpu < -1 || o.cpu > 31) {
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

    setup_realtime(o.cpu);

    signal(SIGTERM, on_signal);
    signal(SIGINT, on_signal);
    signal(SIGPIPE, SIG_IGN);

    if (o.fifo_output || o.fifo_path) {
        while (!stop_requested) {
            ret = run_fifo(&o);
            if (stop_requested)
                break;
            if (ret < 0)
                log_msg("uac2d: fifo unavailable (%s), retrying\n",
                        strerror(-ret));
            msleep(1000);
        }
    } else {
        while (!stop_requested) {
            ret = run_bridge(&o);
            if (stop_requested)
                break;
            if (ret == UAC2_RATE_CHANGED)
                log_msg("uac2d: reopening for new host rate\n");
            else if (ret < 0)
                log_msg("uac2d: bridge unavailable (%s), retrying\n",
                        strerror(-ret));
            msleep(1000);
        }
    }

    log_msg("uac2d: stopped\n");
    if (log_fp && log_fp != stderr)
        fclose(log_fp);
    return 0;
}
