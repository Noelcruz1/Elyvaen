#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
DEST=${1:-$ROOT/kernel-sources}
BRANCH=${BRANCH:-nokernelsu}
REPO=${REPO:-https://github.com/Mxiier/kernel_motorola_cypfq.git}

command -v git >/dev/null 2>&1 || {
    echo "Falta git" >&2
    exit 1
}

mkdir -p "$DEST"
DIR="$DEST/kernel_motorola_cypfq"

if [ -d "$DIR/.git" ]; then
    echo "Actualizando $DIR"
    git -C "$DIR" fetch --depth 1 origin "$BRANCH"
    git -C "$DIR" checkout -B "$BRANCH" FETCH_HEAD
else
    echo "Clonando $REPO ($BRANCH)"
    git clone --depth 1 --branch "$BRANCH" "$REPO" "$DIR"
fi

echo "Kernel en $DIR"
echo "Rama: $(git -C "$DIR" rev-parse --abbrev-ref HEAD)"
echo "Commit: $(git -C "$DIR" rev-parse --short HEAD)"
