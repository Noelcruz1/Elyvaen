#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
SRC=${KERNEL_SRC:-$ROOT/kernel-sources/kernel_motorola_cypfq}
OUT=${KERNEL_BUILD:-$ROOT/build-kernel-release}
JOBS=${JOBS:-3}
BRANCH=${BRANCH:-elyvaen-uac2}

command -v clang >/dev/null 2>&1 || {
    echo "Falta clang. En Alpine: apk add build-base clang lld llvm bison flex perl openssl-dev ncurses-dev dtc elfutils-dev bash"
    exit 1
}

if [ ! -d "$SRC/.git" ]; then
    echo "Falta el source del kernel en $SRC"
    exit 1
fi

cd "$SRC"
git rev-parse --verify "$BRANCH" >/dev/null 2>&1 || {
    echo "Falta la rama $BRANCH"
    exit 1
}
git checkout "$BRANCH"

. ./envsetup.sh
export KBUILD_OUTPUT=./out-release
export LOCALVERSION=-elyvaen-release
export LLVM_IAS=1
export LLVM_PARALLEL_LINK_JOBS=1

make vendor/cypfq_defconfig >/tmp/elyvaen-release-config.log 2>&1

# Perfil de release: LTO/ThinLTO + O3, sin CFI/SCS/debug, y con UAC2.
scripts/config --file "$KBUILD_OUTPUT/.config" \
    -e LTO_CLANG -e THINLTO -d LTO_NONE \
    -d CFI_CLANG -d SHADOW_CALL_STACK \
    -d DEBUG_INFO -d MODVERSIONS -d IKHEADERS \
    -d DEBUG_FS -d KALLSYMS_ALL \
    -e CPU_FREQ_DEFAULT_GOV_SCHEDUTIL
make olddefconfig >>/tmp/elyvaen-release-config.log 2>&1

grep -E 'CONFIG_(LTO|LTO_CLANG|THINLTO|LTO_NONE|CFI_CLANG|DEBUG_FS|KALLSYMS_ALL|USB_CONFIGFS_F_UAC2|USB_F_UAC2|SND_USB_AUDIO|CC_OPTIMIZE_FOR_PERFORMANCE)' "$KBUILD_OUTPUT/.config" | head -30

make -j"$JOBS" Image modules BOARD_CONFIG_CAMERA_KERNEL_NAME=none >/tmp/elyvaen-release-build.log 2>&1

mkdir -p "$OUT" "$OUT/modules"
cp "$KBUILD_OUTPUT/arch/arm64/boot/Image" "$OUT/Image"
cp "$KBUILD_OUTPUT/vmlinux" "$KBUILD_OUTPUT/System.map" \
   "$KBUILD_OUTPUT/Module.symvers" "$KBUILD_OUTPUT/modules.order" \
   "$KBUILD_OUTPUT/.config" "$OUT/"
find "$KBUILD_OUTPUT" -name '*.ko' -exec cp {} "$OUT/modules/" \;

# DTB/DTBO ya validados en la build de prueba; se conservan para empaquetar.
if [ -d "$ROOT/build-kernel/dts" ]; then
    cp -a "$ROOT/build-kernel/dts" "$OUT/"
fi

sha256sum "$OUT/Image" > "$OUT/SHA256SUMS"
(cd "$OUT" && sha256sum modules/*.ko >> SHA256SUMS)
echo "Listo: $OUT"
echo "Image: $OUT/Image"
echo "Modulos: $(ls "$OUT/modules" | wc -l)"
