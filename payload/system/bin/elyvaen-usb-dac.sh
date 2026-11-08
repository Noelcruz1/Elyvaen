#!/system/bin/sh
set -eu

GADGET=${ELV_UAC2_GADGET:-/config/usb_gadget/g1}
FUNC_NAME=uac2.0
FUNC=$GADGET/functions/$FUNC_NAME
CONF=${ELV_UAC2_CONF:-/system/etc/elyvaen/uac2.conf}
DAEMON=elyvaen-uac2d
PROP=sys.elyvaen.usbdac

conf_get() {
    [ -r "$CONF" ] || return 0
    sed -n "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*//p" "$CONF" | tail -n 1 | tr -d '\r'
}

num_or() {
    case "$1" in
        ''|*[!0-9]*) printf '%s\n' "$2" ;;
        *) printf '%s\n' "$1" ;;
    esac
}

require_root() {
    [ "$(id -u)" = "0" ] || {
        echo "elyvaen-usb-dac: root required" >&2
        exit 1
    }
}

find_udc() {
    ls /sys/class/udc 2>/dev/null | head -n 1
}

restore_udc() {
    [ -n "${udc:-}" ] || return 0
    printf '%s\n' "$udc" > "$GADGET/UDC" 2>/dev/null || true
}

remove_function() {
    cfg=$(find_config 2>/dev/null) || cfg=
    [ -n "$cfg" ] && rm -f "$cfg/$FUNC_NAME" 2>/dev/null || true
    [ -d "$FUNC" ] && rmdir "$FUNC" 2>/dev/null || true
}

find_config() {
    for c in "$GADGET"/configs/*; do
        [ -d "$c" ] && {
            printf '%s\n' "$c"
            return 0
        }
    done
    return 1
}

ensure_gadget() {
    [ -d "$GADGET" ] && return 0
    mkdir -p "$GADGET/strings/0x409" "$GADGET/configs/b.1/strings/0x409"
    printf '0x1d6b\n' > "$GADGET/idVendor"
    printf '0x0104\n' > "$GADGET/idProduct"
    printf '0x0200\n' > "$GADGET/bcdUSB"
    printf '0x0100\n' > "$GADGET/bcdDevice"
    printf 'Elyvaen\n' > "$GADGET/strings/0x409/manufacturer"
    printf 'Elyvaen USB DAC\n' > "$GADGET/strings/0x409/product"
    printf 'elyvaen-uac2\n' > "$GADGET/strings/0x409/serialnumber"
    printf 'Elyvaen USB DAC\n' > "$GADGET/configs/b.1/strings/0x409/configuration"
    printf '500\n' > "$GADGET/configs/b.1/MaxPower"
}

ensure_config_uac2() {
    cfg=$(find_config) || {
        echo "elyvaen-usb-dac: no USB config found" >&2
        return 1
    }
    if [ ! -e "$cfg/$FUNC_NAME" ]; then
        cd "$GADGET"
        ln -s "functions/$FUNC_NAME" "configs/$(basename "$cfg")/$FUNC_NAME"
    fi
}

write_function_params() {
    ssize=3
    case "$fmt" in
        s16le) ssize=2 ;;
        s24_3le) ssize=3 ;;
        s32le) ssize=4 ;;
    esac
    chmask=3
    [ "$channels" = "1" ] && chmask=1
    printf '0\n' > "$FUNC/p_chmask"
    printf '%s\n' "$rate" > "$FUNC/p_srate"
    printf '2\n' > "$FUNC/p_ssize"
    printf '%s\n' "$chmask" > "$FUNC/c_chmask"
    printf '%s\n' "$rate" > "$FUNC/c_srate"
    printf '%s\n' "$ssize" > "$FUNC/c_ssize"
    printf '4\n' > "$FUNC/req_number"
}

daemon_start() {
    setprop "$PROP" 1
    if command -v start >/dev/null 2>&1; then
        start "$DAEMON" >/dev/null 2>&1 || true
    fi
    sleep 1
    if ! pidof "$DAEMON" >/dev/null 2>&1; then
        echo "elyvaen-usb-dac: init service elyvaen-uac2d did not start" >&2
        return 1
    fi
}

daemon_stop() {
    setprop "$PROP" 0
    if command -v stop >/dev/null 2>&1; then
        stop "$DAEMON" >/dev/null 2>&1 || true
    fi
    if pidof "$DAEMON" >/dev/null 2>&1; then
        kill "$(pidof "$DAEMON")" >/dev/null 2>&1 || true
    fi
}

enable_dac() {
    udc=$(find_udc)
    [ -n "$udc" ] || {
        echo "elyvaen-usb-dac: no UDC" >&2
        return 1
    }
    rate=$(num_or "$(conf_get rate)" 48000)
    channels=$(num_or "$(conf_get channels)" 2)
    fmt=$(conf_get capture_format)
    [ -n "$fmt" ] || fmt=s24_3le

    ensure_gadget
    printf '\n' > "$GADGET/UDC" || true
    if ! mkdir -p "$FUNC" 2>/dev/null; then
        restore_udc
        return 1
    fi
    if ! write_function_params; then
        remove_function
        restore_udc
        return 1
    fi
    if ! ensure_config_uac2; then
        remove_function
        restore_udc
        return 1
    fi
    if ! printf '%s\n' "$udc" > "$GADGET/UDC"; then
        remove_function
        restore_udc
        return 1
    fi
    if ! daemon_start; then
        remove_function
        restore_udc
        return 1
    fi
    echo "elyvaen-usb-dac: enabled rate=$rate channels=$channels format=$fmt udc=$udc"
}

disable_dac() {
    udc=$(find_udc)
    daemon_stop
    if [ -d "$GADGET" ]; then
        printf '\n' > "$GADGET/UDC" || true
        remove_function
        restore_udc
    fi
    echo "elyvaen-usb-dac: disabled"
}

status_dac() {
    echo "gadget=$GADGET udc=$(find_udc)"
    [ -d "$FUNC" ] && echo "function=present" || echo "function=absent"
    cfg=$(find_config 2>/dev/null) || cfg=
    [ -n "$cfg" ] && [ -e "$cfg/$FUNC_NAME" ] && echo "linked=yes" || echo "linked=no"
    echo "daemon_pid=$(pidof "$DAEMON" 2>/dev/null || true)"
    grep -i uac2 /proc/asound/cards 2>/dev/null || echo "uac2_card=absent"
}

require_root
case "${1:-}" in
    on|enable|start) enable_dac ;;
    off|disable|stop) disable_dac ;;
    restart) disable_dac; enable_dac ;;
    status) status_dac ;;
    *)
        echo "usage: $0 {on|off|restart|status}" >&2
        exit 2
        ;;
esac
