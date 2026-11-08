#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
SRC=${KERNEL_SRC:-$ROOT/kernel-sources/kernel_motorola_cypfq}
OUT=${KERNEL_BUILD:-$ROOT/build-kernel}
JOBS=${JOBS:-3}
BRANCH=${BRANCH:-nokernelsu}
TAG=${LOCALVERSION:-cypfq-test}

command -v clang >/dev/null 2>&1 || {
    echo "Falta clang. En Alpine: apk add build-base clang lld llvm bison flex perl openssl-dev ncurses-dev dtc elfutils-dev"
    exit 1
}

if [ ! -d "$SRC/.git" ]; then
    mkdir -p "$(dirname "$SRC")"
    git clone --depth 1 --branch "$BRANCH" https://github.com/Mxiier/kernel_motorola_cypfq.git "$SRC"
fi

cd "$SRC"
. ./envsetup.sh
export KBUILD_OUTPUT=./out-test
export LOCALVERSION="-$TAG"
export LLVM_IAS=1
export LLVM_PARALLEL_LINK_JOBS=1

make vendor/cypfq_defconfig >/tmp/cypfq-kernel-config.log 2>&1

# Build de prueba rapido y compatible con stock/Magisk:
# sin LTO/CFI/SCS, sin debug info, sin MODVERSIONS, sin IKHEADERS.
# La camara no se usa en el DAP y su techpack no compila limpio en este arbol.
scripts/config --file "$KBUILD_OUTPUT/.config" \
    -d LTO_CLANG -d CFI_CLANG -d SHADOW_CALL_STACK \
    -d DEBUG_INFO -d MODVERSIONS -d IKHEADERS \
    -e LTO_NONE
make olddefconfig >/tmp/cypfq-kernel-olddef.log 2>&1

mkdir -p "$OUT"
make -j"$JOBS" Image modules BOARD_CONFIG_CAMERA_KERNEL_NAME=none > "$OUT/build-kernel.log" 2>&1
make -j"$JOBS" dtbs BOARD_CONFIG_CAMERA_KERNEL_NAME=none > "$OUT/build-dtbs.log" 2>&1

rm -rf "$OUT/modules" "$OUT/dts"
mkdir -p "$OUT/modules" "$OUT/dts"
cp "$KBUILD_OUTPUT/arch/arm64/boot/Image" "$OUT/Image"
cp "$KBUILD_OUTPUT/vmlinux" "$KBUILD_OUTPUT/System.map" \
   "$KBUILD_OUTPUT/Module.symvers" "$KBUILD_OUTPUT/modules.order" \
   "$KBUILD_OUTPUT/.config" "$OUT/"
cp "$KBUILD_OUTPUT/arch/arm64/boot/dts/vendor/qcom/holi-moto-base.dtb" "$OUT/dts/"
cp "$KBUILD_OUTPUT"/arch/arm64/boot/dts/vendor/qcom/holi-cypfq-*.dtbo "$OUT/dts/" 2>/dev/null || true
find "$KBUILD_OUTPUT" -name '*.ko' -exec cp {} "$OUT/modules/" \;

sha256sum "$OUT/Image" > "$OUT/SHA256SUMS"
(cd "$OUT" && sha256sum modules/*.ko dts/*.dtb dts/*.dtbo >> SHA256SUMS)

echo "Listo: $OUT"
echo "Image: $OUT/Image"
echo "Modulos: $(ls "$OUT/modules" | wc -l)"
echo "Vermagic: $(modinfo "$OUT/modules/apr_dlkm.ko" 2>/dev/null | awk '/vermagic/ {print $2; exit}')"
