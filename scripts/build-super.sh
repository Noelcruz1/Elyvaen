#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
BACKUP_DIR=${BACKUP_DIR:-/workspace/Android/cypfq_backup}
BASE_SUPER=${BASE_SUPER:-$BACKUP_DIR/super_ab_safe_raw.img}
BASE_IMAGES=${BASE_IMAGES:-$BACKUP_DIR/safe_ab}
OUT_DIR=${OUT_DIR:-$ROOT/build}
WORK="$OUT_DIR/work"
PAYLOAD="$ROOT/payload"
CONFIG="$ROOT/config"
MODE=${1:-all}

log() { printf '%s\n' "$*"; }
warn() { printf 'WARN: %s\n' "$*" >&2; }
die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

need() {
    command -v "$1" >/dev/null 2>&1 || die "missing tool: $1"
}

need debugfs
need e2fsck
need e2rm
need dumpe2fs
need lpdump
need python3
need sha256sum
need stat
need find
need awk

[ -f "$BASE_SUPER" ] || die "base super not found: $BASE_SUPER"
[ -d "$BASE_IMAGES" ] || die "base images not found: $BASE_IMAGES"
[ -d "$PAYLOAD" ] || die "payload not found: $PAYLOAD"

img_for() {
    case "$1" in
        product) echo "$WORK/product_a.img" ;;
        system) echo "$WORK/system_a.img" ;;
        system_ext) echo "$WORK/system_ext_a.img" ;;
        vendor) echo "$WORK/vendor_a.img" ;;
        *) die "unknown image: $1" ;;
    esac
}

ctx_for() {
    case "$1" in
        vendor) echo "u:object_r:vendor_configs_file:s0" ;;
        *) echo "u:object_r:system_file:s0" ;;
    esac
}

file_ctx_for() {
    case "$1:$2" in
        vendor:/bin/hw/elyvaen-uac2d) echo "u:object_r:hal_audio_default_exec:s0" ;;
        *) ctx_for "$1" ;;
    esac
}

dbg() {
    debugfs -w -R "$1" "$2" >/dev/null 2>&1
}

dbgq() {
    debugfs -R "$1" "$2" 2>/dev/null
}

exists() {
    dbgq "stat \"$2\"" "$1" | grep -q '^Inode:'
}

ensure_dir() {
    img=$1
    ctx=$2
    path=$3
    cur=
    oldifs=$IFS
    IFS=/
    for part in $path; do
        [ -n "$part" ] || continue
        cur="$cur/$part"
        if ! exists "$img" "$cur"; then
            dbg "mkdir \"$cur\"" "$img" || die "cannot create $cur in $img"
            dbg "sif \"$cur\" mode 040755" "$img" || true
            dbg "sif \"$cur\" uid 0" "$img" || true
            dbg "sif \"$cur\" gid 0" "$img" || true
            dbg "ea_set \"$cur\" security.selinux $ctx\\0" "$img" || true
        fi
    done
    IFS=$oldifs
}

install_file() {
    img=$1
    src=$2
    dst=$3
    mode=$4
    file_ctx=$5
    dir_ctx=${6:-$file_ctx}
    ensure_dir "$img" "$dir_ctx" "$(dirname "$dst")"
    if exists "$img" "$dst"; then
        dbg "rm \"$dst\"" "$img" || true
    fi
    dbg "write \"$src\" \"$dst\"" "$img" || die "cannot write $dst in $img"
    dbg "sif \"$dst\" mode $mode" "$img" || true
    dbg "sif \"$dst\" uid 0" "$img" || true
    dbg "sif \"$dst\" gid 0" "$img" || true
    dbg "ea_set \"$dst\" security.selinux $file_ctx\\0" "$img" || true
}

prepare_images() {
    log "==> Copiando imagenes base"
    rm -rf "$WORK"
    mkdir -p "$WORK"
    for name in product_a system_a system_ext_a vendor_a; do
        cp "$BASE_IMAGES/$name.img" "$WORK/$name.img"
    done
}

apply_debloat() {
    log "==> Aplicando debloat seguro"
    count=0
    skipped=0
    while IFS=' ' read -r target path; do
        case "$target" in
            ''|'#'*) continue ;;
        esac
        [ -n "$path" ] || continue
        img=$(img_for "$target")
        if exists "$img" "$path"; then
            if e2rm -r "$img:$path" >/dev/null 2>&1; then
                count=$((count + 1))
            else
                warn "no se pudo eliminar $target:$path"
                skipped=$((skipped + 1))
            fi
        else
            warn "no existe $target:$path (ignorado)"
            skipped=$((skipped + 1))
        fi
    done < "$CONFIG/debloat-safe.txt"
    log "   eliminados: $count; ignorados: $skipped"
}

apply_payload() {
    log "==> Integrando payload Elyvaen"
    file_list="$OUT_DIR/payload-files.list"
    : > "$file_list"
    for section in system vendor product; do
        [ -d "$PAYLOAD/$section" ] || continue
        find "$PAYLOAD/$section" -type f >> "$file_list"
    done
    count=0
    while IFS= read -r src; do
        [ -n "$src" ] || continue
        rel=${src#"$PAYLOAD/"}
        section=${rel%%/*}
        rest=${rel#*/}
        img=$(img_for "$section")
        dir_ctx=$(ctx_for "$section")
        case "$section" in
            system) dst="/system/$rest" ;;
            *) dst="/$rest" ;;
        esac
        ctx=$(file_ctx_for "$section" "$dst")
        case "$rest" in
            *.sh|bin/*) mode=0100755 ;;
            *) mode=0100644 ;;
        esac
        install_file "$img" "$src" "$dst" "$mode" "$ctx" "$dir_ctx"
        count=$((count + 1))
    done < "$file_list"
    log "   archivos integrados: $count"
    patch_vendor_file_contexts
}

patch_vendor_file_contexts() {
    log "==> Etiquetando binario UAC2 en vendor_file_contexts"
    img=$(img_for vendor)
    tmp="$OUT_DIR/vendor_file_contexts"
    dbgq "dump \"/etc/selinux/vendor_file_contexts\" \"$tmp\"" "$img" >/dev/null || die "no se pudo extraer vendor_file_contexts"
    [ -s "$tmp" ] || die "vendor_file_contexts vacio"
    if ! grep -q 'elyvaen-uac2d' "$tmp"; then
        printf '/(vendor|system/vendor)/bin/hw/elyvaen-uac2d u:object_r:hal_audio_default_exec:s0\n' >> "$tmp"
    fi
    install_file "$img" "$tmp" "/etc/selinux/vendor_file_contexts" 0100644 "u:object_r:file_contexts_file:s0" "u:object_r:file_contexts_file:s0"
}

patch_build_prop() {
    log "==> Aplicando identidad Elyvaen a build.prop"
    img=$(img_for system)
    tmp="$OUT_DIR/build.prop"
    dbgq "dump \"/system/build.prop\" \"$tmp\"" "$img" >/dev/null || die "no se pudo extraer build.prop"
    [ -s "$tmp" ] || die "build.prop vacio"
    if ! grep -q '^ro.elyvaen.version=' "$tmp"; then
        printf '\n# Elyvaen\n' >> "$tmp"
        cat "$CONFIG/build.prop.additions" >> "$tmp"
    fi
    install_file "$img" "$tmp" "/system/build.prop" 0100600 "u:object_r:system_file:s0"
}

validate_images() {
    if [ "${SKIP_FSCK:-0}" = "1" ]; then
        log "==> Validacion e2fsck omitida (SKIP_FSCK=1)"
        return 0
    fi
    log "==> Validando imagenes ext4"
    for name in product system system_ext vendor; do
        img=$(img_for "$name")
        rc=0
        e2fsck -f -y "$img" >/dev/null 2>&1 || rc=$?
        if [ "$rc" -gt 2 ]; then
            die "e2fsck fallo en $name (rc=$rc)"
        fi
        rc=0
        e2fsck -fn "$img" >/dev/null 2>&1 || rc=$?
        [ "$rc" -eq 0 ] || die "e2fsck -fn reporta errores en $name (rc=$rc)"
        blocks=$(dumpe2fs -h "$img" 2>/dev/null | awk -F: '/Free blocks/ {gsub(/ /,"",$2); print $2}')
        log "   $name OK (free blocks: ${blocks:-?})"
    done
}

build_super() {
    log "==> Reconstruyendo super preservando metadata A/B"
    for name in product_a system_a system_ext_a vendor_a; do
        [ -f "$WORK/$name.img" ] || die "falta $WORK/$name.img; ejecuta primero: $0 images"
    done
    cp "$BASE_SUPER" "$OUT_DIR/super.raw.img"
    lpdump "$BASE_SUPER" > "$OUT_DIR/lpdump-base.txt" 2>/dev/null || die "lpdump base fallo"
    for name in product_a system_a system_ext_a vendor_a; do
        set -- $(awk -v p="$name" '$1=="super:" && $5==p {size=$4; gsub(":","",size); print $2, size-$2}' "$OUT_DIR/lpdump-base.txt")
        [ $# -eq 2 ] || die "no se pudo leer geometria de $name"
        start=$1
        sectors=$2
        img="$WORK/$name.img"
        size=$(stat -c %s "$img")
        expected=$((sectors * 512))
        [ "$size" -eq "$expected" ] || die "$name cambio de tamano ($size != $expected)"
        log "   $name @ sector $start ($sectors sectores)"
        dd if="$img" of="$OUT_DIR/super.raw.img" bs=512 seek="$start" conv=notrunc 2>/dev/null || die "dd fallo en $name"
    done
    lpdump "$OUT_DIR/super.raw.img" > "$OUT_DIR/lpdump-elyvaen.txt" 2>/dev/null || die "lpdump nuevo fallo"
    log "==> Generando imagen sparse"
    python3 "$ROOT/scripts/raw-to-sparse.py" "$OUT_DIR/super.raw.img" "$OUT_DIR/super.sparse.img"
    ln -sf super.sparse.img "$OUT_DIR/super.img"
    sha256sum "$OUT_DIR/super.sparse.img" > "$OUT_DIR/super.sparse.sha256"
    sha256sum "$WORK"/*.img > "$OUT_DIR/images.sha256"
    log "   $OUT_DIR/super.sparse.img"
}

write_manifest() {
    {
        echo "Elyvaen build"
        echo "date=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
        echo "device=cypfq (moto g51 5G)"
        echo "base_super=$BASE_SUPER"
        echo "base_images=$BASE_IMAGES"
        echo "payload_files=$(find "$PAYLOAD" -type f | wc -l)"
        echo "debloat_rules=$(grep -vc '^#' "$CONFIG/debloat-safe.txt" 2>/dev/null || true)"
        echo
        echo "sha256:"
        cat "$OUT_DIR"/images.sha256 2>/dev/null || true
        cat "$OUT_DIR"/super.sparse.sha256 2>/dev/null || true
    } > "$OUT_DIR/manifest.txt"
    log "   $OUT_DIR/manifest.txt"
}

case "$MODE" in
    images)
        mkdir -p "$OUT_DIR"
        prepare_images
        apply_debloat
        apply_payload
        patch_build_prop
        validate_images
        write_manifest
        log "Listo: imagenes en $WORK"
        ;;
    super)
        mkdir -p "$OUT_DIR"
        validate_images
        build_super
        write_manifest
        log "Listo: $OUT_DIR/super.sparse.img"
        ;;
    all)
        mkdir -p "$OUT_DIR"
        prepare_images
        apply_debloat
        apply_payload
        patch_build_prop
        validate_images
        build_super
        write_manifest
        log "Listo: $OUT_DIR/super.sparse.img"
        ;;
    *)
        echo "Uso: $0 [all|images|super]" >&2
        exit 2
        ;;
esac
