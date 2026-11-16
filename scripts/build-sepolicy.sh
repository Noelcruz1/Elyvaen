#!/bin/sh
set -eu

# Compile the Elyvaen SELinux additions into the vendor precompiled policy.
#
# The base CIL sources live on the stock partitions, so this script can
# regenerate payload/vendor/etc/selinux/precompiled_sepolicy from the safe
# images at any time:
#
#   scripts/build-sepolicy.sh
#
# Inputs:
#   $BASE_IMAGES/{system,vendor,product,system_ext}_a.img
#   sepolicy/elyvaen.cil
#   secilc (inside $CHROOT, or on PATH)
#
# Output:
#   payload/vendor/etc/selinux/precompiled_sepolicy

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
BACKUP_DIR=${BACKUP_DIR:-/workspace/Android/cypfq_backup}
BASE_IMAGES=${BASE_IMAGES:-$BACKUP_DIR/safe_ab}
OUT=${OUT:-$ROOT/payload/vendor/etc/selinux/precompiled_sepolicy}
WORK=${WORK:-$ROOT/build-sepolicy}
CHROOT=${CHROOT:-/workspace/sistema}
POLICYVERS=${POLICYVERS:-30}
CIL=${CIL:-$ROOT/sepolicy/elyvaen.cil}

DEBUGFS=${DEBUGFS:-debugfs}
if ! command -v "$DEBUGFS" >/dev/null 2>&1; then
    DEBUGFS=/workspace/Android/tools/bin/debugfs
fi

die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

[ -f "$CIL" ] || die "no existe $CIL"
for name in system vendor product system_ext; do
    [ -f "$BASE_IMAGES/${name}_a.img" ] || \
        die "no existe $BASE_IMAGES/${name}_a.img"
done

mkdir -p "$WORK" "$(dirname "$OUT")"

dump() {
    img=$1
    src=$2
    dst=$3
    rm -f "$dst"
    "$DEBUGFS" -R "dump \"$src\" \"$dst\"" "$img" >/dev/null 2>&1 || \
        die "no se pudo extraer $src de $img"
    [ -s "$dst" ] || die "$src vacio"
}

echo "==> Extrayendo CIL base"
dump "$BASE_IMAGES/system_a.img" "/etc/selinux/plat_sepolicy.cil" \
    "$WORK/plat_sepolicy.cil"
dump "$BASE_IMAGES/system_a.img" "/etc/selinux/mapping/${POLICYVERS}.0.cil" \
    "$WORK/mapping.cil"
dump "$BASE_IMAGES/system_a.img" \
    "/etc/selinux/mapping/${POLICYVERS}.0.compat.cil" \
    "$WORK/mapping.compat.cil"
dump "$BASE_IMAGES/vendor_a.img" "/etc/selinux/plat_pub_versioned.cil" \
    "$WORK/plat_pub_versioned.cil"
dump "$BASE_IMAGES/vendor_a.img" "/etc/selinux/vendor_sepolicy.cil" \
    "$WORK/vendor_sepolicy.cil"
dump "$BASE_IMAGES/product_a.img" "/etc/selinux/product_sepolicy.cil" \
    "$WORK/product_sepolicy.cil"
dump "$BASE_IMAGES/system_ext_a.img" \
    "/etc/selinux/system_ext_sepolicy.cil" "$WORK/system_ext_sepolicy.cil"

echo "==> Compilando politica (versión $POLICYVERS)"
cp "$CIL" "$WORK/elyvaen.cil"
if [ -x "$CHROOT/usr/bin/secilc" ] || [ -x "$CHROOT/bin/secilc" ]; then
    INNER=/tmp/elyvaen-sepolicy
    rm -rf "$CHROOT$INNER"
    mkdir -p "$CHROOT$INNER"
    cp "$WORK/plat_sepolicy.cil" "$WORK/plat_pub_versioned.cil" \
        "$WORK/vendor_sepolicy.cil" "$WORK/product_sepolicy.cil" \
        "$WORK/system_ext_sepolicy.cil" "$WORK/mapping.cil" \
        "$WORK/mapping.compat.cil" "$WORK/elyvaen.cil" "$CHROOT$INNER/"
    chroot "$CHROOT" /bin/bash -c \
        'cd "$1" && secilc -m -M true -G -N -c "$2" -o precompiled_sepolicy \
        plat_sepolicy.cil plat_pub_versioned.cil vendor_sepolicy.cil \
        product_sepolicy.cil system_ext_sepolicy.cil mapping.cil \
        mapping.compat.cil elyvaen.cil' \
        sh "$INNER" "$POLICYVERS"
    cp "$CHROOT$INNER/precompiled_sepolicy" "$OUT"
    rm -rf "$CHROOT$INNER"
else
    command -v secilc >/dev/null 2>&1 || \
        die "secilc no encontrado (chroot $CHROOT ni PATH)"
    ( cd "$WORK" && secilc -m -M true -G -N -c "$POLICYVERS" -o "$OUT" \
        plat_sepolicy.cil plat_pub_versioned.cil vendor_sepolicy.cil \
        product_sepolicy.cil system_ext_sepolicy.cil mapping.cil \
        mapping.compat.cil elyvaen.cil )
fi

[ -s "$OUT" ] || die "secilc no genero $OUT"
chmod 0644 "$OUT"
echo "Poliza: $OUT"
sha256sum "$OUT"
