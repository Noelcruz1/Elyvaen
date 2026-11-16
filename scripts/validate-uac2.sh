#!/bin/sh
# Post-flash validation for the Elyvaen UAC2 helper. Read-only.
set -u

ADB=${ADB:-adb}

say() { printf '\n== %s ==\n' "$*"; }

say "adb"
$ADB wait-for-device
$ADB shell 'getprop ro.product.device; getprop ro.build.version.release' | tr -d '\r'

say "init service"
for p in init.svc.elyvaen-usb init.svc.elyvaen-uac2d \
         sys.elyvaen.usbdac.auto; do
    printf '%s=%s\n' "$p" "$($ADB shell getprop "$p" | tr -d '\r')"
done
# vendor.usb.elyvaen.* se escribe, pero shell no puede leer vendor_usb_prop.
for p in vendor.usb.elyvaen.dac vendor.usb.elyvaen.state vendor.usb.elyvaen.udc; do
    v=$($ADB shell getprop "$p" 2>/dev/null | tr -d '\r')
    printf '%s=%s\n' "$p" "${v:-(no legible por shell)}"
done

say "daemon"
$ADB shell 'pidof elyvaen-uac2d || echo no-daemon'
$ADB shell 'ps -A -o USER,PID,PPID,NAME 2>/dev/null | grep -E "elyvaen" || true'

say "usb manager"
$ADB shell dumpsys usb 2>/dev/null | grep -E \
    'current_functions|kernel_state|connected=|configured=|current_mode' | head -10

say "host audio interfaces"
found=0
for i in /sys/bus/usb/devices/*:*; do
    [ -f "$i/bInterfaceClass" ] || continue
    cls=$(cat "$i/bInterfaceClass" 2>/dev/null)
    if [ "$cls" = "01" ]; then
        printf 'AUDIO %s subclass=%s protocol=%s driver=%s\n' "$i" \
            "$(cat "$i/bInterfaceSubClass" 2>/dev/null)" \
            "$(cat "$i/bInterfaceProtocol" 2>/dev/null)" \
            "$(basename "$(readlink "$i/driver" 2>/dev/null)" 2>/dev/null)"
        found=1
    fi
done
[ "$found" = "1" ] || echo "no Audio Class interface on the host"

say "host product"
for d in /sys/bus/usb/devices/*; do
    [ -f "$d/idVendor" ] || continue
    vid=$(cat "$d/idVendor" 2>/dev/null)
    pid=$(cat "$d/idProduct" 2>/dev/null)
    prod=$(cat "$d/product" 2>/dev/null)
    [ "$vid" = "22b8" ] && printf '%s:%s %s\n' "$vid" "$pid" "$prod"
done

say "selinux / init log"
$ADB logcat -d -b all 2>/dev/null | grep -iE \
    'elyvaen|avc: denied' | tail -40 || true

say "result"
svc=$($ADB shell getprop init.svc.elyvaen-usb | tr -d '\r')
dmn=$($ADB shell getprop init.svc.elyvaen-uac2d | tr -d '\r')
printf 'init.svc.elyvaen-usb=%s init.svc.elyvaen-uac2d=%s\n' "$svc" "$dmn"
[ "$svc" = "running" ] || echo "WARN: helper service is not running"
[ "$dmn" = "running" ] || echo "WARN: UAC2 daemon is not running"
