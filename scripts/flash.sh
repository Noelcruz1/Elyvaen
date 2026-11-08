#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
IMAGE=${1:-$ROOT/build/super.sparse.img}
CONFIRM=${CONFIRM:-0}

[ "$CONFIRM" = "1" ] || {
    echo "Esto escribira 'super' en el dispositivo."
    echo "Ejecuta: CONFIRM=1 $0 $IMAGE"
    exit 1
}

[ -f "$IMAGE" ] || {
    echo "No existe la imagen: $IMAGE" >&2
    exit 1
}

command -v fastboot >/dev/null 2>&1 || {
    echo "fastboot no disponible en este entorno. Ver docs/recovery.md" >&2
    exit 1
}

fastboot devices | grep -q . || {
    echo "No hay dispositivo en fastboot" >&2
    exit 1
}

echo "Flasheando $IMAGE"
fastboot flash super "$IMAGE"
fastboot --set-active=a
fastboot reboot
echo "Listo. Esperando arranque."
