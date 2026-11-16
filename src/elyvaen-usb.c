/*
 * elyvaen-usb - native helper for the Elyvaen USB DAC (UAC2) mode.
 *
 * Runs as an init service without a shell. The binary is labelled
 * init_exec so that it keeps executing in the init domain, which is the
 * only domain on this ROM with full configfs access (the ROM's own
 * init.qcom.usb.rc actions rely on the same permissions).
 *
 * Responsibilities:
 *   - read /system/etc/elyvaen/uac2.conf
 *   - make sure /config/usb_gadget/g1/functions/uac2.0 has the right
 *     capture parameters (rate/channels/sample size)
 *   - link the function into the active USB configuration without
 *     touching the functions already present (adb, mtp, ...)
 *   - rebind the UDC so the host enumerates the new composition
 *   - publish state through sys.elyvaen.usbdac* properties
 *   - the init .rc uses sys.elyvaen.usbdac=1/0 to start/stop the
 *     elyvaen-uac2d daemon (which runs in hal_audio_default)
 *
 * The helper never uses /system/bin/sh, toybox, setprop or any other
 * external program.
 */

#define _GNU_SOURCE
#include <ctype.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <ftw.h>
#include <limits.h>
#include <signal.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <sys/uio.h>
#include <sys/un.h>
#include <time.h>
#include <unistd.h>

#define DEFAULT_CONFIG "/vendor/etc/elyvaen/uac2.conf"
#define DEFAULT_GADGET "/config/usb_gadget/g1"
#define DEFAULT_FUNC "uac2.0"
#define DEFAULT_UDC ""

#define PROP_REQUEST "vendor.usb.elyvaen.request"
#define PROP_DAC "vendor.usb.elyvaen.dac"
#define PROP_STATE "vendor.usb.elyvaen.state"
#define PROP_UDC "vendor.usb.elyvaen.udc"

#define PROP_SOCKET "/dev/socket/property_service"
#define PROP_NAME_MAX 32
#define PROP_VALUE_MAX 92
#define PROP_MSG_SETPROP 1

#define DEFAULT_RATE 48000
#define DEFAULT_CHANNELS 2
#define DEFAULT_SSIZE 3
#define DEFAULT_REQ 4
#define DEFAULT_POLL_MS 2500
#define DEFAULT_HEAL_MAX 3
#define HEAL_PAUSE_MS 300000

#define ARRAY_SIZE(a) (sizeof(a) / sizeof((a)[0]))

struct config {
    int rate;
    int channels;
    int sample_size;
    int req_number;
    int auto_mode;
    int poll_ms;
    int heal_max;
    char gadget[PATH_MAX];
    char function[64];
    char udc[64];
};

struct app {
    struct config cfg;
    char func_dir[PATH_MAX];
    char config_dir[PATH_MAX];
    char link_path[PATH_MAX];
    char udc_attr[PATH_MAX];
    char udc[64];
    char config_path[PATH_MAX];
    unsigned int request_seq;
    int dac_on;
    int no_props;
    volatile sig_atomic_t stop;
};

static struct app *g_app;

/* ------------------------------------------------------------------ */
/* small helpers                                                       */
/* ------------------------------------------------------------------ */

static void copy_str(char *dst, size_t size, const char *src)
{
    size_t n;

    if (size == 0)
        return;
    if (!src)
        src = "";
    n = strlen(src);
    if (n >= size)
        n = size - 1;
    memcpy(dst, src, n);
    dst[n] = '\0';
}

static void path_fmt(char *dst, size_t size, const char *fmt, ...)
    __attribute__((format(printf, 3, 4)));

static void path_fmt(char *dst, size_t size, const char *fmt, ...)
{
    va_list ap;

    va_start(ap, fmt);
    vsnprintf(dst, size, fmt, ap);
    va_end(ap);
}

static void trim(char *s)
{
    char *p = s;
    size_t n;

    while (*p && isspace((unsigned char)*p))
        p++;
    if (p != s)
        memmove(s, p, strlen(p) + 1);

    n = strlen(s);
    while (n > 0 && isspace((unsigned char)s[n - 1]))
        s[--n] = '\0';
}

static int parse_int(const char *value, int *out)
{
    char *end = NULL;
    long n;

    if (!value || !*value)
        return -1;
    errno = 0;
    n = strtol(value, &end, 10);
    if (errno || !end || *end != '\0' || n < -1 || n > 1000000)
        return -1;
    *out = (int)n;
    return 0;
}

/* Minimal logd writer (Android 10+ protocol). See the same code in
 * uac2d.c; the helper also needs to be visible in logcat because init
 * redirects service stderr to /dev/null. */
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
    static char tag[] = "elyvaen-usb";
    char line[512];
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

static void klog(const char *fmt, ...)
{
    char buf[512];
    va_list ap;

    va_start(ap, fmt);
    vsnprintf(buf, sizeof(buf), fmt, ap);
    va_end(ap);

    fprintf(stderr, "elyvaen-usb: %s\n", buf);
    fflush(stderr);
    logd_log(LOGD_PRIO_INFO, buf);
}

/* ------------------------------------------------------------------ */
/* property service (same binary protocol bionic uses)                 */
/* ------------------------------------------------------------------ */

struct prop_msg {
    uint32_t cmd;
    char name[PROP_NAME_MAX];
    char value[PROP_VALUE_MAX];
};

static int property_set_raw(const char *name, const char *value)
{
    struct sockaddr_un addr;
    struct prop_msg msg;
    struct ucred cred;
    char cbuf[CMSG_SPACE(sizeof(struct ucred))];
    struct iovec iov;
    struct msghdr mh;
    struct cmsghdr *cmsg;
    struct timeval tv;
    char response = 0;
    int fd;
    int on = 1;
    ssize_t n;

    if (strlen(name) >= PROP_NAME_MAX || strlen(value) >= PROP_VALUE_MAX)
        return -EINVAL;

    fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (fd < 0)
        return -errno;

    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    copy_str(addr.sun_path, sizeof(addr.sun_path), PROP_SOCKET);

    if (connect(fd, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
        int err = -errno;
        close(fd);
        return err;
    }

    (void)setsockopt(fd, SOL_SOCKET, SO_PASSCRED, &on, sizeof(on));
    tv.tv_sec = 1;
    tv.tv_usec = 0;
    (void)setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
    (void)setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));

    memset(&msg, 0, sizeof(msg));
    msg.cmd = PROP_MSG_SETPROP;
    copy_str(msg.name, sizeof(msg.name), name);
    copy_str(msg.value, sizeof(msg.value), value);

    memset(&cred, 0, sizeof(cred));
    cred.pid = getpid();
    cred.uid = getuid();
    cred.gid = getgid();

    iov.iov_base = &msg;
    iov.iov_len = sizeof(msg);

    memset(&mh, 0, sizeof(mh));
    mh.msg_iov = &iov;
    mh.msg_iovlen = 1;
    mh.msg_control = cbuf;
    mh.msg_controllen = sizeof(cbuf);

    cmsg = CMSG_FIRSTHDR(&mh);
    cmsg->cmsg_level = SOL_SOCKET;
    cmsg->cmsg_type = SCM_CREDENTIALS;
    cmsg->cmsg_len = CMSG_LEN(sizeof(struct ucred));
    memcpy(CMSG_DATA(cmsg), &cred, sizeof(cred));
    mh.msg_controllen = cmsg->cmsg_len;

    n = sendmsg(fd, &mh, 0);
    if (n != (ssize_t)sizeof(msg)) {
        int err = n < 0 ? -errno : -EIO;
        close(fd);
        return err;
    }

    n = read(fd, &response, 1);
    close(fd);

    /* Older property services do not reply to legacy SETPROP; a clean
     * EOF means the property was processed. */
    if (n == 0)
        return 0;
    if (n != 1)
        return n < 0 ? -errno : -EIO;

    return response == '1' ? 0 : -EPERM;
}

static int set_prop(const char *name, const char *value)
{
    int ret;

    if (!g_app || g_app->no_props)
        return 0;

    ret = property_set_raw(name, value);
    if (ret < 0)
        klog("setprop %s failed: %s", name, strerror(-ret));
    return ret;
}

static int publish_state(const char *fmt, ...)
{
    char buf[PROP_VALUE_MAX];
    va_list ap;

    va_start(ap, fmt);
    vsnprintf(buf, sizeof(buf), fmt, ap);
    va_end(ap);

    return set_prop(PROP_STATE, buf);
}

/* ------------------------------------------------------------------ */
/* file helpers                                                        */
/* ------------------------------------------------------------------ */

static int read_text(const char *path, char *buf, size_t size)
{
    int fd;
    ssize_t n;

    if (size == 0)
        return -EINVAL;

    fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0)
        return -errno;

    n = read(fd, buf, size - 1);
    close(fd);
    if (n < 0)
        return -errno;

    buf[n] = '\0';
    trim(buf);
    return 0;
}

static int read_int(const char *path, int *out)
{
    char buf[64];
    int ret = read_text(path, buf, sizeof(buf));

    if (ret < 0)
        return ret;
    return parse_int(buf, out);
}

static int write_text(const char *path, const char *text)
{
    size_t len = strlen(text);
    size_t off = 0;
    int fd;

    fd = open(path, O_WRONLY | O_CLOEXEC);
    if (fd < 0)
        return -errno;

    while (off < len) {
        ssize_t n = write(fd, text + off, len - off);
        if (n < 0) {
            int err = -errno;
            if (errno == EINTR)
                continue;
            close(fd);
            return err;
        }
        off += (size_t)n;
    }

    close(fd);
    return 0;
}

static int write_attr(const char *dir, const char *name, const char *value)
{
    char path[PATH_MAX];

    path_fmt(path, sizeof(path), "%s/%s", dir, name);
    return write_text(path, value);
}

static int write_attr_int(const char *dir, const char *name, int value)
{
    char buf[32];

    snprintf(buf, sizeof(buf), "%d\n", value);
    return write_attr(dir, name, buf);
}

/*
 * Windows only binds the USB Audio 2.0 class driver when the composite
 * device advertises the IAD device class (0xEF/0x02/0x01). The stock
 * Motorola gadget descriptor uses 0/0/0; Linux does not care, Windows does.
 * The attributes are writable only while the UDC is unbound.
 */
static int set_device_class(struct app *a)
{
    int ret = 0;

    ret |= write_attr(a->cfg.gadget, "bDeviceClass", "0xef");
    ret |= write_attr(a->cfg.gadget, "bDeviceSubClass", "0x02");
    ret |= write_attr(a->cfg.gadget, "bDeviceProtocol", "0x01");
    if (ret)
        klog("cannot set IAD device class: %d", ret);
    return ret;
}

static int is_dir(const char *path)
{
    struct stat st;

    return stat(path, &st) == 0 && S_ISDIR(st.st_mode);
}

static int remove_link(const char *path)
{
    if (unlink(path) < 0) {
        if (errno == ENOENT)
            return 0;
        return -errno;
    }
    return 0;
}

/* ------------------------------------------------------------------ */
/* config file                                                         */
/* ------------------------------------------------------------------ */

static void config_defaults(struct config *c)
{
    memset(c, 0, sizeof(*c));
    c->rate = DEFAULT_RATE;
    c->channels = DEFAULT_CHANNELS;
    c->sample_size = DEFAULT_SSIZE;
    c->req_number = DEFAULT_REQ;
    c->auto_mode = 1;
    c->poll_ms = DEFAULT_POLL_MS;
    c->heal_max = DEFAULT_HEAL_MAX;
    copy_str(c->gadget, sizeof(c->gadget), DEFAULT_GADGET);
    copy_str(c->function, sizeof(c->function), DEFAULT_FUNC);
    copy_str(c->udc, sizeof(c->udc), DEFAULT_UDC);
}

static int format_to_ssize(const char *fmt)
{
    if (!fmt || !*fmt)
        return -1;
    if (!strcmp(fmt, "s16le") || !strcmp(fmt, "s16_le"))
        return 2;
    if (!strcmp(fmt, "s24_3le") || !strcmp(fmt, "s24_3_le"))
        return 3;
    if (!strcmp(fmt, "s24le") || !strcmp(fmt, "s24_le"))
        return 4;
    if (!strcmp(fmt, "s32le") || !strcmp(fmt, "s32_le"))
        return 4;
    return -1;
}

static int load_config(struct config *c, const char *path)
{
    FILE *f;
    char line[512];

    f = fopen(path, "r");
    if (!f)
        return -errno;

    while (fgets(line, sizeof(line), f)) {
        char *eq;
        char *key;
        char *value;

        trim(line);
        if (!line[0] || line[0] == '#')
            continue;

        eq = strchr(line, '=');
        if (!eq)
            continue;
        *eq = '\0';
        key = line;
        value = eq + 1;
        trim(key);
        trim(value);

        if (!strcmp(key, "rate")) {
            parse_int(value, &c->rate);
        } else if (!strcmp(key, "channels")) {
            parse_int(value, &c->channels);
        } else if (!strcmp(key, "capture_format")) {
            int ssize = format_to_ssize(value);
            if (ssize > 0)
                c->sample_size = ssize;
        } else if (!strcmp(key, "req_number")) {
            parse_int(value, &c->req_number);
        } else if (!strcmp(key, "gadget")) {
            copy_str(c->gadget, sizeof(c->gadget), value);
        } else if (!strcmp(key, "function")) {
            copy_str(c->function, sizeof(c->function), value);
        } else if (!strcmp(key, "udc")) {
            copy_str(c->udc, sizeof(c->udc), value);
        } else if (!strcmp(key, "auto")) {
            parse_int(value, &c->auto_mode);
        } else if (!strcmp(key, "poll_ms")) {
            parse_int(value, &c->poll_ms);
        } else if (!strcmp(key, "heal_max")) {
            parse_int(value, &c->heal_max);
        }
    }

    fclose(f);
    return 0;
}

/* ------------------------------------------------------------------ */
/* gadget discovery / configuration                                    */
/* ------------------------------------------------------------------ */

static int build_paths(struct app *a)
{
    path_fmt(a->func_dir, sizeof(a->func_dir), "%s/functions/%s",
             a->cfg.gadget, a->cfg.function);
    path_fmt(a->udc_attr, sizeof(a->udc_attr), "%s/UDC", a->cfg.gadget);

    return 0;
}

static void update_link_path(struct app *a)
{
    path_fmt(a->link_path, sizeof(a->link_path), "%s/%s",
             a->config_dir, a->cfg.function);
}

static int find_config_dir(struct app *a)
{
    char configs[PATH_MAX];
    char candidate[PATH_MAX];
    DIR *dir;
    struct dirent *de;
    int found = 0;

    path_fmt(candidate, sizeof(candidate), "%s/configs/b.1", a->cfg.gadget);
    if (is_dir(candidate)) {
        copy_str(a->config_dir, sizeof(a->config_dir), candidate);
        update_link_path(a);
        return 0;
    }

    path_fmt(configs, sizeof(configs), "%s/configs", a->cfg.gadget);
    dir = opendir(configs);
    if (!dir)
        return -errno;

    while ((de = readdir(dir)) != NULL) {
        struct stat st;
        char path[PATH_MAX];

        if (de->d_name[0] == '.')
            continue;
        path_fmt(path, sizeof(path), "%s/%s", configs, de->d_name);
        if (stat(path, &st) == 0 && S_ISDIR(st.st_mode)) {
            copy_str(a->config_dir, sizeof(a->config_dir), path);
            path_fmt(a->link_path, sizeof(a->link_path), "%s/%s",
                     a->config_dir, a->cfg.function);
            found = 1;
            break;
        }
    }

    closedir(dir);
    return found ? 0 : -ENOENT;
}

static int read_bound_udc(struct app *a)
{
    char buf[128];

    if (read_text(a->udc_attr, buf, sizeof(buf)) < 0)
        return -1;
    if (!buf[0])
        return -1;
    copy_str(a->udc, sizeof(a->udc), buf);
    return 0;
}

static int resolve_udc(struct app *a)
{
    if (read_bound_udc(a) == 0)
        return 0;
    if (a->udc[0])
        return 0;
    return -ENOENT;
}

static int ensure_function_dir(struct app *a);

static int ensure_gadget(struct app *a)
{
    int ret;

    if (!is_dir(a->cfg.gadget)) {
        publish_state("error:no-gadget");
        klog("gadget %s not available", a->cfg.gadget);
        return -ENOENT;
    }

    ret = find_config_dir(a);
    if (ret < 0) {
        publish_state("error:no-config");
        klog("no USB configuration under %s/configs", a->cfg.gadget);
        return ret;
    }

    ret = ensure_function_dir(a);
    if (ret < 0) {
        publish_state("error:mkdir-function");
        klog("cannot create %s: %s", a->func_dir, strerror(-ret));
        return ret;
    }

    return 0;
}

static int read_attr_int(const char *dir, const char *name, int *out);

static int udc_is_bound(struct app *a)
{
    char buf[128];

    if (read_text(a->udc_attr, buf, sizeof(buf)) < 0)
        return 0;
    return buf[0] != '\0';
}

static int find_function_link(struct app *a);

static int uac2_is_linked(struct app *a)
{
    return find_function_link(a) == 0;
}

static int find_function_link(struct app *a)
{
    DIR *dir;
    struct dirent *de;

    dir = opendir(a->config_dir);
    if (!dir)
        return -errno;

    while ((de = readdir(dir)) != NULL) {
        char path[PATH_MAX];
        char target[PATH_MAX];
        const char *base;
        ssize_t n;

        if (de->d_name[0] == '.')
            continue;

        path_fmt(path, sizeof(path), "%s/%s", a->config_dir, de->d_name);

        /* readlink() only needs lnk_file:read; lstat() would need
         * lnk_file:getattr, which this domain does not have. Non-links
         * fail with EINVAL and are skipped. */
        n = readlink(path, target, sizeof(target) - 1);
        if (n < 0)
            continue;
        target[n] = '\0';

        base = strrchr(target, '/');
        base = base ? base + 1 : target;
        if (strcmp(base, a->cfg.function) != 0)
            continue;

        copy_str(a->link_path, sizeof(a->link_path), path);
        closedir(dir);
        return 0;
    }

    closedir(dir);
    return -ENOENT;
}

static int remove_function_links(struct app *a)
{
    int removed = 0;

    while (find_function_link(a) == 0) {
        if (unlink(a->link_path) < 0) {
            if (errno == ENOENT)
                continue;
            return -errno;
        }
        if (++removed > 32)
            break;
    }

    return removed;
}

static int params_match(struct app *a)
{
    int p_chmask, c_chmask, c_ssize, req;
    int chmask = a->cfg.channels == 1 ? 1 : 3;

    if (read_attr_int(a->func_dir, "p_chmask", &p_chmask) < 0)
        return 0;
    if (read_attr_int(a->func_dir, "c_chmask", &c_chmask) < 0)
        return 0;
    if (read_attr_int(a->func_dir, "c_ssize", &c_ssize) < 0)
        return 0;
    if (read_attr_int(a->func_dir, "req_number", &req) < 0)
        return 0;

    /* c_srate/p_srate are host-controlled (UAC2 clock). Do not fight
     * them here: the daemon follows the current rate. */
    return p_chmask == 0 && c_chmask == chmask &&
           c_ssize == a->cfg.sample_size &&
           req == a->cfg.req_number;
}

static void log_params(struct app *a)
{
    int p_chmask = -1, p_srate = -1, p_ssize = -1;
    int c_chmask = -1, c_srate = -1, c_ssize = -1, req = -1;

    (void)read_attr_int(a->func_dir, "p_chmask", &p_chmask);
    (void)read_attr_int(a->func_dir, "p_srate", &p_srate);
    (void)read_attr_int(a->func_dir, "p_ssize", &p_ssize);
    (void)read_attr_int(a->func_dir, "c_chmask", &c_chmask);
    (void)read_attr_int(a->func_dir, "c_srate", &c_srate);
    (void)read_attr_int(a->func_dir, "c_ssize", &c_ssize);
    (void)read_attr_int(a->func_dir, "req_number", &req);
    klog("params: p_chmask=%d p_srate=%d p_ssize=%d c_chmask=%d "
         "c_srate=%d c_ssize=%d req=%d (want 0/%d/2/%d/%d/%d/%d)",
         p_chmask, p_srate, p_ssize, c_chmask, c_srate, c_ssize, req,
         a->cfg.rate, a->cfg.channels == 1 ? 1 : 3, a->cfg.rate,
         a->cfg.sample_size, a->cfg.req_number);
}

static int bind_udc(struct app *a)
{
    char buf[128];
    int ret;

    if (!a->udc[0])
        return -ENOENT;

    path_fmt(buf, sizeof(buf), "%s\n", a->udc);
    ret = write_text(a->udc_attr, buf);
    if (ret < 0) {
        klog("cannot bind UDC %s: %s", a->udc, strerror(-ret));
        return ret;
    }
    return 0;
}

static int unbind_udc(struct app *a)
{
    return write_text(a->udc_attr, "\n");
}

static int write_params(struct app *a)
{
    int chmask = a->cfg.channels == 1 ? 1 : 3;
    int ret;

    ret = write_attr_int(a->func_dir, "p_chmask", 0);
    if (ret < 0)
        return ret;
    ret = write_attr_int(a->func_dir, "p_srate", a->cfg.rate);
    if (ret < 0)
        return ret;
    ret = write_attr_int(a->func_dir, "p_ssize", 2);
    if (ret < 0)
        return ret;
    ret = write_attr_int(a->func_dir, "c_chmask", chmask);
    if (ret < 0)
        return ret;
    ret = write_attr_int(a->func_dir, "c_srate", a->cfg.rate);
    if (ret < 0)
        return ret;
    ret = write_attr_int(a->func_dir, "c_ssize", a->cfg.sample_size);
    if (ret < 0)
        return ret;
    ret = write_attr_int(a->func_dir, "req_number", a->cfg.req_number);
    if (ret < 0)
        return ret;

    return 0;
}

static void restore_after_failure(struct app *a, int linked)
{
    if (linked)
        (void)remove_link(a->link_path);
    (void)bind_udc(a);
}

static void nap_ms(int ms)
{
    struct timespec ts;

    ts.tv_sec = ms / 1000;
    ts.tv_nsec = (long)(ms % 1000) * 1000 * 1000;
    (void)nanosleep(&ts, NULL);
}

static int ensure_function_dir(struct app *a)
{
    char value[32];
    int i;

    if (is_dir(a->func_dir))
        return 0;

    /* vendor_hal_usb_qti can write configfs attributes but cannot
     * create directories. A vendor_init action (see elyvaen-uac2.rc)
     * performs the mkdir when this counter changes. */
    a->request_seq++;
    snprintf(value, sizeof(value), "%u", a->request_seq);
    set_prop(PROP_REQUEST, value);

    for (i = 0; i < 30; i++) {
        if (is_dir(a->func_dir))
            return 0;
        nap_ms(100);
    }

    return -ETIMEDOUT;
}

static void set_dac(struct app *a, int on)
{
    if (a->dac_on == on)
        return;
    if (set_prop(PROP_DAC, on ? "1" : "0") == 0)
        a->dac_on = on;
}

static int apply_gadget(struct app *a)
{
    char target[PATH_MAX];
    int linked = 0;
    int was_on;
    int ret;

    ret = ensure_gadget(a);
    if (ret < 0)
        return ret;

    if (resolve_udc(a) < 0) {
        publish_state("error:no-udc");
        klog("no UDC controller available");
        return -ENOENT;
    }

    if (uac2_is_linked(a) && params_match(a)) {
        if (!udc_is_bound(a))
            (void)bind_udc(a);
        set_prop(PROP_UDC, a->udc);
        publish_state("bound");
        set_dac(a, 1);
        return 0;
    }

    /* Let the daemon close the capture PCM before the function goes
     * away: the dac property makes init stop elyvaen-uac2d. */
    was_on = a->dac_on;
    set_dac(a, 0);
    if (was_on && !a->no_props)
        nap_ms(300);

    /* The function attributes are writable only while the function is
     * not linked (f_uac2 keeps a refcount). Unbind and unlink first. */
    (void)unbind_udc(a);
    (void)set_device_class(a);
    ret = remove_function_links(a);
    if (ret < 0) {
        klog("cannot remove UAC2 links: %s", strerror(-ret));
        (void)bind_udc(a);
        publish_state("error:unlink");
        return ret;
    }
    update_link_path(a);

    ret = ensure_function_dir(a);
    if (ret < 0) {
        klog("function directory %s not available: %s", a->func_dir,
             strerror(-ret));
        (void)bind_udc(a);
        publish_state("error:mkdir");
        return ret;
    }

    ret = write_params(a);
    if (ret < 0) {
        klog("cannot configure %s: %s", a->func_dir, strerror(-ret));
        restore_after_failure(a, 0);
        publish_state("error:params-%d", ret);
        return ret;
    }

    path_fmt(target, sizeof(target), "%s/functions/%s",
             a->cfg.gadget, a->cfg.function);
    if (symlink(target, a->link_path) < 0) {
        ret = -errno;
        klog("cannot link %s -> %s: %s", a->link_path, target,
             strerror(-ret));
        restore_after_failure(a, 0);
        publish_state("error:symlink");
        return ret;
    }
    linked = 1;

    ret = bind_udc(a);
    if (ret < 0) {
        restore_after_failure(a, linked);
        publish_state("error:bind");
        return ret;
    }

    set_prop(PROP_UDC, a->udc);
    publish_state("bound");
    set_dac(a, 1);

    klog("UAC2 enabled rate=%d channels=%d ssize=%d req=%d udc=%s",
         a->cfg.rate, a->cfg.channels, a->cfg.sample_size,
         a->cfg.req_number, a->udc[0] ? a->udc : "(none)");
    return 0;
}

static int disable_gadget(struct app *a)
{
    int was_linked;
    int was_on;
    int ret;

    was_on = a->dac_on;
    set_dac(a, 0);
    if (was_on && !a->no_props)
        nap_ms(300);

    if (!is_dir(a->cfg.gadget) || find_config_dir(a) < 0) {
        publish_state("disabled");
        return 0;
    }

    was_linked = uac2_is_linked(a);
    if (!was_linked) {
        publish_state("disabled");
        return 0;
    }

    (void)resolve_udc(a);
    (void)unbind_udc(a);

    ret = remove_function_links(a);
    if (ret < 0) {
        klog("cannot remove UAC2 links: %s", strerror(-ret));
        (void)bind_udc(a);
        publish_state("error:unlink");
        return ret;
    }

    if (bind_udc(a) < 0) {
        publish_state("error:rebind");
        return -EIO;
    }

    publish_state("disabled");
    klog("UAC2 disabled");
    return 0;
}

/* read_int/read_text variants that take a directory + attribute name */
static int read_attr_int(const char *dir, const char *name, int *out)
{
    char path[PATH_MAX];

    path_fmt(path, sizeof(path), "%s/%s", dir, name);
    return read_int(path, out);
}

/* ------------------------------------------------------------------ */
/* status                                                              */
/* ------------------------------------------------------------------ */

static void print_status(struct app *a)
{
    int chmask = a->cfg.channels == 1 ? 1 : 3;

    printf("gadget=%s\n", a->cfg.gadget);
    printf("config=%s\n", a->config_dir[0] ? a->config_dir : "(none)");
    printf("function=%s\n", a->cfg.function);
    printf("function_dir=%s (%s)\n", a->func_dir,
           is_dir(a->func_dir) ? "present" : "absent");
    printf("linked=%s\n", uac2_is_linked(a) ? "yes" : "no");
    printf("udc=%s\n", a->udc[0] ? a->udc : "(unknown)");
    printf("udc_bound=%s\n", udc_is_bound(a) ? "yes" : "no");
    printf("params_ok=%s\n", params_match(a) ? "yes" : "no");
    printf("dac_prop=%s\n", a->dac_on ? "1" : "0");
    printf("want: rate=%d channels=%d(chmask=%d) ssize=%d req=%d\n",
           a->cfg.rate, a->cfg.channels, chmask, a->cfg.sample_size,
           a->cfg.req_number);
}

/* ------------------------------------------------------------------ */
/* self-test                                                           */
/* ------------------------------------------------------------------ */

static int rm_entry(const char *path, const struct stat *st, int flag,
                    struct FTW *ftw)
{
    (void)st;
    (void)ftw;

    if (flag == FTW_DP)
        return rmdir(path);
    return unlink(path);
}

static int rm_rf(const char *path)
{
    return nftw(path, rm_entry, 16, FTW_DEPTH | FTW_PHYS);
}

static int fail_test(const char *what)
{
    fprintf(stderr, "self-test FAIL: %s\n", what);
    return 1;
}

static int create_file(const char *path)
{
    int fd = open(path, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0644);

    if (fd < 0)
        return -errno;
    close(fd);
    return 0;
}

#define T_CHECK(cond, msg)                 \
    do {                                   \
        if (!(cond)) {                     \
            fprintf(stderr, "self-test: %s\n", msg); \
            goto out;                      \
        }                                  \
    } while (0)

static int self_test(void)
{
    char root[] = "/tmp/elyvaen-usb-test.XXXXXX";
    struct app a;
    char path[PATH_MAX];
    char buf[256];
    int ret = 1;

    if (!mkdtemp(root))
        return fail_test("mkdtemp");

    memset(&a, 0, sizeof(a));
    config_defaults(&a.cfg);
    a.no_props = 1;
    copy_str(a.cfg.gadget, sizeof(a.cfg.gadget), root);

    path_fmt(path, sizeof(path), "%s/functions", root);
    T_CHECK(mkdir(path, 0770) == 0, "mkdir functions");
    path_fmt(path, sizeof(path), "%s/functions/%s", root,
             a.cfg.function);
    T_CHECK(mkdir(path, 0770) == 0, "mkdir function");
    path_fmt(path, sizeof(path), "%s/configs", root);
    T_CHECK(mkdir(path, 0770) == 0, "mkdir configs");
    path_fmt(path, sizeof(path), "%s/configs/b.1", root);
    T_CHECK(mkdir(path, 0770) == 0, "mkdir configs/b.1");
    path_fmt(path, sizeof(path), "%s/UDC", root);
    T_CHECK(create_file(path) == 0, "create UDC");
    T_CHECK(write_text(path, "fake-udc\n") == 0, "write UDC");

    {
        static const char *attrs[] = {
            "p_chmask", "p_srate", "p_ssize", "c_chmask",
            "c_srate", "c_ssize", "req_number"
        };
        size_t i;

        for (i = 0; i < ARRAY_SIZE(attrs); i++) {
            path_fmt(path, sizeof(path), "%s/functions/%s/%s", root,
                     a.cfg.function, attrs[i]);
            T_CHECK(create_file(path) == 0, "create attribute");
        }
    }

    build_paths(&a);
    path_fmt(a.config_dir, sizeof(a.config_dir), "%s/configs/b.1", root);
    path_fmt(a.link_path, sizeof(a.link_path), "%s/%s", a.config_dir,
             a.cfg.function);

    g_app = &a;

    T_CHECK(apply_gadget(&a) == 0, "apply_gadget");

    {
        int p_chmask = -1, c_chmask = -1, c_srate = -1, c_ssize = -1;
        int req = -1;

        T_CHECK(read_attr_int(a.func_dir, "p_chmask", &p_chmask) == 0,
                "read p_chmask");
        T_CHECK(read_attr_int(a.func_dir, "c_chmask", &c_chmask) == 0,
                "read c_chmask");
        T_CHECK(read_attr_int(a.func_dir, "c_srate", &c_srate) == 0,
                "read c_srate");
        T_CHECK(read_attr_int(a.func_dir, "c_ssize", &c_ssize) == 0,
                "read c_ssize");
        T_CHECK(read_attr_int(a.func_dir, "req_number", &req) == 0,
                "read req_number");
        T_CHECK(p_chmask == 0, "p_chmask value");
        T_CHECK(c_chmask == 3, "c_chmask value");
        T_CHECK(c_srate == 48000, "c_srate value");
        T_CHECK(c_ssize == 3, "c_ssize value");
        T_CHECK(req == 4, "req_number value");
        T_CHECK(uac2_is_linked(&a), "function linked");
        T_CHECK(read_text(a.udc_attr, buf, sizeof(buf)) == 0, "read UDC");
        T_CHECK(strcmp(buf, "fake-udc") == 0, "UDC bound");
        T_CHECK(params_match(&a), "params_match");
    }

    T_CHECK(disable_gadget(&a) == 0, "disable_gadget");
    T_CHECK(!uac2_is_linked(&a), "function unlinked");
    T_CHECK(read_text(a.udc_attr, buf, sizeof(buf)) == 0, "read UDC 2");
    T_CHECK(strcmp(buf, "fake-udc") == 0, "UDC rebound");

    /* second enable must be idempotent */
    T_CHECK(apply_gadget(&a) == 0, "apply_gadget 2");
    T_CHECK(uac2_is_linked(&a), "function linked 2");

    fprintf(stdout, "self-test OK\n");
    ret = 0;

out:
    if (ret)
        fprintf(stderr, "self-test FAIL\n");
    g_app = NULL;
    (void)rm_rf(root);
    return ret;
}

/* ------------------------------------------------------------------ */
/* main loop                                                           */
/* ------------------------------------------------------------------ */

static void on_signal(int sig)
{
    (void)sig;
    if (g_app)
        g_app->stop = 1;
}

static void sleep_ms(int ms)
{
    struct timespec ts;

    ts.tv_sec = ms / 1000;
    ts.tv_nsec = (long)(ms % 1000) * 1000 * 1000;
    while (nanosleep(&ts, &ts) < 0 && errno == EINTR && g_app &&
           !g_app->stop) {
        /* keep sleeping the remaining time */
    }
}

static void usage(const char *argv0)
{
    fprintf(stdout,
            "usage: %s [options]\n"
            "  --config PATH        configuration file (%s)\n"
            "  --gadget PATH        USB gadget directory (%s)\n"
            "  --function NAME      function name (%s)\n"
            "  --udc NAME           UDC controller fallback\n"
            "  --rate HZ            sample rate (default %d)\n"
            "  --channels N         channels (default %d)\n"
            "  --format FMT         s16le, s24_3le, s24le or s32le\n"
            "  --req N              isochronous requests (default %d)\n"
            "  --poll-ms N          auto loop interval (default %d)\n"
            "  --heal-max N         reconfigures before pausing (default %d)\n"
            "  --auto               keep the composition healthy (default)\n"
            "  --no-auto            configure once and exit after --once\n"
            "  --once ACTION        on, off or status; then exit\n"
            "  --self-test          run a filesystem self-test\n"
            "  --help               show this help\n",
            argv0, DEFAULT_CONFIG, DEFAULT_GADGET, DEFAULT_FUNC,
            DEFAULT_RATE, DEFAULT_CHANNELS, DEFAULT_REQ,
            DEFAULT_POLL_MS, DEFAULT_HEAL_MAX);
}

static int parse_args(struct app *a, int argc, char **argv,
                      const char **config_path, const char **once)
{
    int i;

    for (i = 1; i < argc; i++) {
        const char *arg = argv[i];
        const char *value;

        if (!strcmp(arg, "--help") || !strcmp(arg, "-h")) {
            usage(argv[0]);
            exit(0);
        }
        if (!strcmp(arg, "--self-test")) {
            *once = "self-test";
            continue;
        }
        if (!strcmp(arg, "--auto")) {
            a->cfg.auto_mode = 1;
            continue;
        }
        if (!strcmp(arg, "--no-auto")) {
            a->cfg.auto_mode = 0;
            continue;
        }
        if (i + 1 >= argc) {
            fprintf(stderr, "elyvaen-usb: missing value for %s\n", arg);
            return -1;
        }
        value = argv[++i];

        if (!strcmp(arg, "--config")) {
            *config_path = value;
        } else if (!strcmp(arg, "--gadget")) {
            copy_str(a->cfg.gadget, sizeof(a->cfg.gadget), value);
        } else if (!strcmp(arg, "--function")) {
            copy_str(a->cfg.function, sizeof(a->cfg.function), value);
        } else if (!strcmp(arg, "--udc")) {
            copy_str(a->cfg.udc, sizeof(a->cfg.udc), value);
        } else if (!strcmp(arg, "--rate")) {
            if (parse_int(value, &a->cfg.rate) < 0)
                return -1;
        } else if (!strcmp(arg, "--channels")) {
            if (parse_int(value, &a->cfg.channels) < 0)
                return -1;
        } else if (!strcmp(arg, "--format")) {
            int ssize = format_to_ssize(value);
            if (ssize < 0)
                return -1;
            a->cfg.sample_size = ssize;
        } else if (!strcmp(arg, "--req")) {
            if (parse_int(value, &a->cfg.req_number) < 0)
                return -1;
        } else if (!strcmp(arg, "--poll-ms")) {
            if (parse_int(value, &a->cfg.poll_ms) < 0)
                return -1;
        } else if (!strcmp(arg, "--heal-max")) {
            if (parse_int(value, &a->cfg.heal_max) < 0)
                return -1;
        } else if (!strcmp(arg, "--once")) {
            *once = value;
        } else {
            fprintf(stderr, "elyvaen-usb: unknown option %s\n", arg);
            return -1;
        }
    }

    return 0;
}

static int validate_config(const struct config *c)
{
    if (c->rate < 8000 || c->rate > 384000)
        return -1;
    if (c->channels < 1 || c->channels > 2)
        return -1;
    if (c->sample_size < 2 || c->sample_size > 4)
        return -1;
    if (c->req_number < 1 || c->req_number > 16)
        return -1;
    if (c->poll_ms < 250 || c->poll_ms > 60000)
        return -1;
    if (c->heal_max < 0 || c->heal_max > 100)
        return -1;
    return 0;
}

static int run_auto(struct app *a)
{
    int miss = 0;
    int heals = 0;
    int paused = 0;
    int ret;

    ret = apply_gadget(a);
    if (ret < 0) {
        publish_state("error:startup");
    } else {
        heals = 0;
    }

    while (!a->stop) {
        sleep_ms(a->cfg.poll_ms);
        if (a->stop)
            break;

        if (!is_dir(a->cfg.gadget)) {
            if (++miss >= 2) {
                miss = 0;
                ret = apply_gadget(a);
                if (ret < 0)
                    publish_state("error:no-gadget");
            }
            continue;
        }

        if (uac2_is_linked(a) && params_match(a)) {
            miss = 0;
            heals = 0;
            paused = 0;

            if (!udc_is_bound(a)) {
                (void)resolve_udc(a);
                if (bind_udc(a) == 0)
                    publish_state("bound");
                else
                    publish_state("error:rebind");
            }

            set_dac(a, 1);
            continue;
        }

        if (paused)
            continue;

        if (++miss < 2)
            continue;
        miss = 0;

        klog("reapply: linked=%d params=%d udc=%d", uac2_is_linked(a),
             params_match(a), udc_is_bound(a));
        if (!params_match(a))
            log_params(a);

        if (heals >= a->cfg.heal_max) {
            klog("not reconfiguring again for a while");
            publish_state("heal-paused");
            paused = 1;
            sleep_ms(HEAL_PAUSE_MS);
            if (a->stop)
                break;
            paused = 0;
            heals = 0;
            continue;
        }

        klog("USB composition changed, re-applying UAC2");
        if (apply_gadget(a) < 0)
            publish_state("error:reapply");
        else
            heals++;
    }

    return 0;
}

int main(int argc, char **argv)
{
    struct app app;
    struct sigaction sa;
    const char *config_path = DEFAULT_CONFIG;
    const char *once = NULL;
    int ret;

    memset(&app, 0, sizeof(app));
    config_defaults(&app.cfg);
    g_app = &app;

    /* Allow --config before loading it, like the daemon does. */
    {
        int i;
        for (i = 1; i + 1 < argc; i++) {
            if (!strcmp(argv[i], "--config")) {
                config_path = argv[i + 1];
                break;
            }
        }
    }

    if (load_config(&app.cfg, config_path) < 0)
        klog("cannot read %s, using defaults", config_path);

    if (parse_args(&app, argc, argv, &config_path, &once) < 0)
        return 2;

    if (once && !strcmp(once, "self-test"))
        return self_test();

    copy_str(app.config_path, sizeof(app.config_path), config_path);
    build_paths(&app);

    if (validate_config(&app.cfg) < 0) {
        fprintf(stderr, "elyvaen-usb: invalid configuration\n");
        return 2;
    }

    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = on_signal;
    sigemptyset(&sa.sa_mask);
    sigaction(SIGTERM, &sa, NULL);
    sigaction(SIGINT, &sa, NULL);
    sigaction(SIGHUP, &sa, NULL);
    signal(SIGPIPE, SIG_IGN);

    if (once) {
        if (!strcmp(once, "on") || !strcmp(once, "enable")) {
            ret = apply_gadget(&app);
        } else if (!strcmp(once, "off") || !strcmp(once, "disable")) {
            ret = disable_gadget(&app);
        } else if (!strcmp(once, "status")) {
            (void)ensure_gadget(&app);
            (void)resolve_udc(&app);
            print_status(&app);
            ret = 0;
        } else {
            fprintf(stderr, "elyvaen-usb: unknown action %s\n", once);
            return 2;
        }
        return ret < 0 ? 1 : 0;
    }

    if (!app.cfg.auto_mode) {
        ret = apply_gadget(&app);
        return ret < 0 ? 1 : 0;
    }

    klog("starting auto mode (rate=%d channels=%d ssize=%d)",
         app.cfg.rate, app.cfg.channels, app.cfg.sample_size);
    (void)run_auto(&app);

    (void)disable_gadget(&app);
    publish_state("stopped");
    klog("stopped");
    return 0;
}
