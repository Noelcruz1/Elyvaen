#!/bin/sh
set -eu

# Build the UAC2 test super on top of the stable camera super, without
# re-applying the debloat (the base images are already debloated).
#
# Inputs:
#   build-camera-vol/super.raw.img        stable super (base metadata)
#   build-camera-vol/work/vendor_a.img    stable vendor + camera modules
#   build-validate-rom15/work/*.img       stable product/system/system_ext
#
# Output:
#   build-uac2-helper/super.sparse.img

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT=${OUT_DIR:-$ROOT/build-uac2-helper}
BASE_SUPER=${BASE_SUPER:-$ROOT/build-camera-vol/super.raw.img}
VENDOR_BASE=${VENDOR_BASE:-$ROOT/build-camera-vol/work/vendor_a.img}
ROM_BASE=${ROM_BASE:-$ROOT/build-validate-rom15/work}

[ -f "$BASE_SUPER" ] || { echo "Falta $BASE_SUPER" >&2; exit 1; }
[ -f "$VENDOR_BASE" ] || { echo "Falta $VENDOR_BASE" >&2; exit 1; }
for name in product system system_ext; do
    [ -f "$ROM_BASE/${name}_a.img" ] || {
        echo "Falta $ROM_BASE/${name}_a.img" >&2
        exit 1
    }
done

"$ROOT/scripts/build-uac2d.sh"
"$ROOT/scripts/build-elyvaen-usb.sh"
"$ROOT/scripts/build-sepolicy.sh"

mkdir -p "$OUT/base"
ln -sf "$ROM_BASE/product_a.img" "$OUT/base/product_a.img"
ln -sf "$ROM_BASE/system_a.img" "$OUT/base/system_a.img"
ln -sf "$ROM_BASE/system_ext_a.img" "$OUT/base/system_ext_a.img"
ln -sf "$VENDOR_BASE" "$OUT/base/vendor_a.img"

BASE_SUPER="$BASE_SUPER" \
BASE_IMAGES="$OUT/base" \
OUT_DIR="$OUT" \
SKIP_DEBLOAT=1 \
    "$ROOT/scripts/build-super.sh" all

echo
echo "Super: $OUT/super.sparse.img"
sha256sum "$OUT/super.sparse.img"
echo "Rollback: $ROOT/build-camera-vol/super.sparse.img"
