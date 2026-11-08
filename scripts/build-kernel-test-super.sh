#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
BACKUP=${BACKUP_DIR:-/workspace/Android/cypfq_backup}
BASE_SUPER=${BASE_SUPER:-$BACKUP/super_ab_safe_raw.img}
BASE_VENDOR=${BASE_VENDOR:-$BACKUP/safe_ab/vendor_a.img}
MODULES=${MODULES:-$ROOT/build-kernel/modules}
OUT=${OUT_DIR:-$ROOT/build-kernel-test}
WORK="$OUT/work"
VENDOR="$WORK/vendor_a.img"

need() { command -v "$1" >/dev/null 2>&1 || { echo "falta $1" >&2; exit 1; }; }
for t in debugfs e2fsck e2rm dumpe2fs lpdump python3 modinfo awk dd sha256sum; do need "$t"; done

[ -d "$MODULES" ] || { echo "faltan modulos en $MODULES" >&2; exit 1; }
[ -f "$BASE_SUPER" ] || { echo "falta $BASE_SUPER" >&2; exit 1; }
[ -f "$BASE_VENDOR" ] || { echo "falta $BASE_VENDOR" >&2; exit 1; }

rm -rf "$OUT"
mkdir -p "$WORK" "$OUT"

echo "==> Preparando vendor stock + modulos 5.4.259"
cp "$BASE_VENDOR" "$VENDOR"

dbg() { debugfs -w -R "$1" "$2" >/dev/null 2>&1; }
exists() { debugfs -R "stat \"$2\"" "$1" 2>/dev/null | grep -q '^Inode:'; }
ctx="u:object_r:vendor_file:s0"

ensure_dir() {
    img=$1; path=$2; cur=
    oldifs=$IFS; IFS=/
    for part in $path; do
        [ -n "$part" ] || continue
        cur="$cur/$part"
        if ! exists "$img" "$cur"; then
            dbg "mkdir \"$cur\"" "$img"
            dbg "sif \"$cur\" mode 040755" "$img" || true
            dbg "sif \"$cur\" uid 0" "$img" || true
            dbg "sif \"$cur\" gid 2000" "$img" || true
            dbg "ea_set \"$cur\" security.selinux $ctx\\0" "$img" || true
        fi
    done
    IFS=$oldifs
}

install_file() {
    img=$1; src=$2; dst=$3
    ensure_dir "$img" "$(dirname "$dst")"
    if exists "$img" "$dst"; then dbg "rm \"$dst\"" "$img" || true; fi
    dbg "write \"$src\" \"$dst\"" "$img" || { echo "no se pudo escribir $dst" >&2; exit 1; }
    dbg "sif \"$dst\" mode 0100644" "$img" || true
    dbg "sif \"$dst\" uid 0" "$img" || true
    dbg "sif \"$dst\" gid 2000" "$img" || true
    dbg "ea_set \"$dst\" security.selinux $ctx\\0" "$img" || true
}

# Limpiar toda la capa de modulos stock; el kernel de prueba trae su propio set.
if exists "$VENDOR" /lib/modules; then
    e2rm -r "$VENDOR:/lib/modules" >/dev/null 2>&1 || true
fi
ensure_dir "$VENDOR" /lib/modules
ensure_dir "$VENDOR" /lib/modules/5.4-gki

STAGE="$WORK/modstage"
rm -rf "$STAGE"
mkdir -p "$STAGE/root" "$STAGE/gki"

# Todos los modulos en root.
cp "$MODULES"/*.ko "$STAGE/root/"
# Modulos que el init de Motorola carga explicitamente desde 5.4-gki.
for n in ili9882_mmi mmi-smbcharger-iio qpnp_adaptive_charge touchscreen_mmi focaltech_v2_mmi sensors_class mmi_relay mmi_info mmi_parallel_charger_iio mmi_sc8549 wl2866d sx937x_sar utags; do
    [ -f "$MODULES/$n.ko" ] && cp "$MODULES/$n.ko" "$STAGE/gki/"
done

# Mapa nombre interno -> archivo.
: > "$STAGE/modmap"
for f in "$STAGE/root"/*.ko; do
    n=$(modinfo -F name "$f" 2>/dev/null || true)
    [ -n "$n" ] && echo "$n $(basename "$f")" >> "$STAGE/modmap"
done

gen_dep() {
    moddir=$1; prefix=$2; outfile=$3
    : > "$outfile"
    for f in "$moddir"/*.ko; do
        [ -f "$f" ] || continue
        base=$(basename "$f")
        deps=$(modinfo -F depends "$f" 2>/dev/null || true)
        line="$prefix/$base:"
        oldifs=$IFS; IFS=','
        for d in $deps; do
            d=$(echo "$d" | tr -d ' ')
            [ -n "$d" ] || continue
            depfile=$(awk -v d="$d" '$1==d {print $2; exit}' "$STAGE/modmap")
            [ -n "$depfile" ] && line="$line $prefix/$depfile"
        done
        IFS=$oldifs
        echo "$line" >> "$outfile"
    done
}

gen_dep "$STAGE/root" /vendor/lib/modules "$STAGE/modules.dep.root"
gen_dep "$STAGE/gki" /vendor/lib/modules/5.4-gki "$STAGE/modules.dep.gki"

: > "$STAGE/modules.load.root"
for f in "$STAGE/root"/*.ko; do basename "$f"; done > "$STAGE/modules.load.root"
: > "$STAGE/modules.load.gki"
for f in "$STAGE/gki"/*.ko; do basename "$f"; done > "$STAGE/modules.load.gki"
: > "$STAGE/empty"

for f in "$STAGE/root"/*.ko; do install_file "$VENDOR" "$f" "/lib/modules/$(basename "$f")"; done
for f in "$STAGE/gki"/*.ko; do install_file "$VENDOR" "$f" "/lib/modules/5.4-gki/$(basename "$f")"; done
install_file "$VENDOR" "$STAGE/modules.dep.root" /lib/modules/modules.dep
install_file "$VENDOR" "$STAGE/modules.dep.gki" /lib/modules/5.4-gki/modules.dep
install_file "$VENDOR" "$STAGE/modules.load.root" /lib/modules/modules.load
install_file "$VENDOR" "$STAGE/modules.load.gki" /lib/modules/5.4-gki/modules.load
install_file "$VENDOR" "$STAGE/empty" /lib/modules/modules.alias
install_file "$VENDOR" "$STAGE/empty" /lib/modules/modules.softdep
install_file "$VENDOR" "$STAGE/empty" /lib/modules/5.4-gki/modules.alias
install_file "$VENDOR" "$STAGE/empty" /lib/modules/5.4-gki/modules.softdep

rm -rf "$STAGE"
rc=0
e2fsck -f -y "$VENDOR" >/dev/null 2>&1 || rc=$?
[ "$rc" -le 2 ] || { echo "e2fsck fallo: $rc" >&2; exit 1; }
e2fsck -fn "$VENDOR" >/dev/null 2>&1 || { echo "e2fsck -fn reporta errores" >&2; exit 1; }
echo "   vendor OK ($(dumpe2fs -h "$VENDOR" 2>/dev/null | awk -F: '/Free blocks/ {gsub(/ /,"",$2); print $2}') bloques libres)"

echo "==> Reconstruyendo super de prueba (stock A/B + vendor modificado)"
cp "$BASE_SUPER" "$OUT/super.raw.img"
set -- $(lpdump "$BASE_SUPER" 2>/dev/null | awk '$1=="super:" && $5=="vendor_a" {size=$4; gsub(":","",size); print $2, size-$2}')
[ $# -eq 2 ] || { echo "no se pudo leer geometria de vendor_a" >&2; exit 1; }
start=$1; sectors=$2
size=$(stat -c %s "$VENDOR")
[ "$size" -eq $((sectors * 512)) ] || { echo "vendor cambio de tamano" >&2; exit 1; }
dd if="$VENDOR" of="$OUT/super.raw.img" bs=512 seek="$start" conv=notrunc 2>/dev/null
lpdump "$OUT/super.raw.img" > "$OUT/lpdump-test.txt" 2>/dev/null || { echo "lpdump fallo" >&2; exit 1; }
python3 "$ROOT/scripts/raw-to-sparse.py" "$OUT/super.raw.img" "$OUT/super.sparse.img"
sha256sum "$OUT/super.sparse.img" "$OUT/work/vendor_a.img" > "$OUT/SHA256SUMS"
echo "Listo: $OUT/super.sparse.img"
echo "Vendor: $OUT/work/vendor_a.img"
