#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ADB_TARGET=${ADB_TARGET:-192.168.100.83:5555}
CONFIRM=${CONFIRM:-0}
SRC=${1:-$ROOT/magisk}

[ "$CONFIRM" = "1" ] || {
    echo "Esto copia los modulos de $SRC a /data/adb/modules del dispositivo."
    echo "Ejecuta: CONFIRM=1 $0 $SRC"
    exit 1
}

[ -d "$SRC" ] || {
    echo "No existe $SRC" >&2
    exit 1
}

adb connect "$ADB_TARGET" >/dev/null 2>&1 || true
ADB="adb -s $ADB_TARGET"
$ADB shell 'test -d /data/adb/modules' || {
    echo "Magisk no disponible en el dispositivo" >&2
    exit 1
}

TMP=/data/local/tmp/cypfq-modules
$ADB shell "rm -rf $TMP; mkdir -p $TMP"
for d in "$SRC"/*; do
    [ -d "$d" ] || continue
    $ADB push "$d" "$TMP/" >/dev/null
done
$ADB shell "su -c \"cp -a $TMP/* /data/adb/modules/; chmod -R 755 /data/adb/modules; reboot\""
