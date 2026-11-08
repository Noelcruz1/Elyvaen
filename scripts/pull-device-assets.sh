#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ADB_TARGET=${ADB_TARGET:-192.168.100.83:5555}
OUT=${OUT_DIR:-$ROOT/build/device-assets}

mkdir -p "$OUT"

adb connect "$ADB_TARGET" >/dev/null 2>&1 || true
adb devices -l

ADB="adb -s $ADB_TARGET"

pull_pkg() {
    pkg=$1
    name=$2
    path=$($ADB shell "pm path $pkg" | tr -d '\r' | sed 's/^package://')
    [ -n "$path" ] || {
        echo "No instalado: $pkg" >&2
        return 0
    }
    tmp="/data/local/tmp/$name.apk"
    $ADB shell "su -c \"cp '$path' '$tmp'; chmod 644 '$tmp'\""
    $ADB pull "$tmp" "$OUT/$name.apk"
}

pull_pkg com.mardous.booming BoomingMusic
pull_pkg helium314.keyboard HeliBoard
pull_pkg com.topjohnwu.magisk Magisk

$ADB shell 'su -c "tar -cf /data/local/tmp/cypfq-modules.tar -C /data/adb/modules ."' || true
$ADB pull /data/local/tmp/cypfq-modules.tar "$OUT/cypfq-modules.tar" || true

$ADB shell 'getprop' > "$OUT/getprop.txt" 2>&1 || true
$ADB shell 'pm list packages -s -f' > "$OUT/system-packages.txt" 2>&1 || true
$ADB shell 'pm list packages -3' > "$OUT/third-party-packages.txt" 2>&1 || true

echo "Assets en $OUT"
