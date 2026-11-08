#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
SRC="$ROOT/src/uac2d.c"
OUT=${UAC2D_BUILD:-$ROOT/build-uac2d}
BIN="$OUT/elyvaen-uac2d"
HOST_BIN="$OUT/elyvaen-uac2d-host"
PAYLOAD_BIN="$ROOT/payload/vendor/bin/hw/elyvaen-uac2d"

find_cross_cc() {
    if [ -n "${CROSS_CC:-}" ]; then
        printf '%s\n' "$CROSS_CC"
        return 0
    fi
    for candidate in \
        "$ROOT/../toolchains/aarch64-linux-musl-cross/bin/aarch64-linux-musl-gcc" \
        "$ROOT/../../toolchains/aarch64-linux-musl-cross/bin/aarch64-linux-musl-gcc"; do
        if [ -x "$candidate" ]; then
            printf '%s\n' "$candidate"
            return 0
        fi
    done
    command -v aarch64-linux-musl-gcc 2>/dev/null || return 1
}

mkdir -p "$OUT" "$ROOT/payload/vendor/bin/hw"

HOST_CC=${HOST_CC:-cc}
$HOST_CC -O2 -Wall -Wextra -Werror -o "$HOST_BIN" "$SRC" -lm
"$HOST_BIN" --self-test

CROSS_CC=$(find_cross_cc) || {
    echo "Falta el cross compiler aarch64-linux-musl-gcc."
    echo "Descarga: https://musl.cc/aarch64-linux-musl-cross.tgz"
    echo "Extrae en /workspace/Android/toolchains o define CROSS_CC."
    exit 1
}

"$CROSS_CC" -static -O2 -Wall -Wextra -Werror -s \
    -o "$BIN" "$SRC" -lm
cp "$BIN" "$PAYLOAD_BIN"
chmod 0755 "$PAYLOAD_BIN"

file "$BIN"
ls -l "$BIN" "$PAYLOAD_BIN"
sha256sum "$BIN" "$PAYLOAD_BIN" > "$OUT/SHA256SUMS"
