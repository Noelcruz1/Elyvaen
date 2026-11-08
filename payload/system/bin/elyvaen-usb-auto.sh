#!/system/bin/sh
PATH=/system/bin:/vendor/bin:$PATH

GADGET=/config/usb_gadget/g1
FUNC_NAME=uac2.0
AUTO_PROP=sys.elyvaen.usbdac.auto
TRANSITION_PROP=sys.elyvaen.usbdac.transition
DAC_PROP=sys.elyvaen.usbdac

find_udc() {
    ls /sys/class/udc 2>/dev/null | head -n 1
}

usb_state() {
    udc=$(find_udc)
    [ -n "$udc" ] || return 0
    cat "/sys/class/udc/$udc/state" 2>/dev/null
}

usb_online() {
    for f in /sys/class/power_supply/usb/online \
             /sys/class/power_supply/usb_phy/online; do
        [ -r "$f" ] && {
            cat "$f"
            return 0
        }
    done
    echo 1
}

uac2_linked() {
    [ -d "$GADGET/functions/$FUNC_NAME" ] || return 1
    for c in "$GADGET"/configs/*; do
        [ -e "$c/$FUNC_NAME" ] && return 0
    done
    return 1
}

host_config() {
    case "$1" in
        configured|suspended|*mtp*|*ptp*|*adb*) return 0 ;;
        *) return 1 ;;
    esac
}

not_host_count=0

while true; do
    if [ "$(getprop "$AUTO_PROP")" != "1" ]; then
        sleep 2
        continue
    fi
    if [ "$(getprop "$TRANSITION_PROP")" = "1" ]; then
        sleep 2
        continue
    fi

    state=$(usb_state)
    [ -n "$state" ] || state=$(getprop sys.usb.state)
    online=$(usb_online)
    dac=$(getprop "$DAC_PROP")

    if [ "$dac" = "1" ]; then
        if host_config "$state"; then
            not_host_count=0
            if ! uac2_linked; then
                /system/bin/elyvaen-usb-dac.sh on >/dev/null 2>&1 || true
                sleep 5
            fi
        else
            not_host_count=$((not_host_count + 1))
            if [ "$not_host_count" -ge 2 ]; then
                /system/bin/elyvaen-usb-dac.sh off >/dev/null 2>&1 || true
                not_host_count=0
                sleep 5
            fi
        fi
    else
        not_host_count=0
        if [ "$online" != "0" ] && host_config "$state"; then
            /system/bin/elyvaen-usb-dac.sh on >/dev/null 2>&1 || true
            sleep 5
        fi
    fi

    sleep 2
done
