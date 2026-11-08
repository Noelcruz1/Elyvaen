#!/bin/sh
set -eu

BACKUP_DIR=${BACKUP_DIR:-/workspace/Android/cypfq_backup}
CONFIRM=${CONFIRM:-0}

[ "$CONFIRM" = "1" ] || {
    echo "Esto restaurara el super A/B validado y las particiones de arranque."
    echo "Ejecuta: CONFIRM=1 $0"
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

for f in super_ab_safe.img current_boot_a.img current_dtbo_a.img current_vendor_boot_a.img; do
    [ -f "$BACKUP_DIR/$f" ] || {
        echo "Falta backup: $BACKUP_DIR/$f" >&2
        exit 1
    }
done

fastboot flash super "$BACKUP_DIR/super_ab_safe.img"
fastboot flash boot_a "$BACKUP_DIR/current_boot_a.img"
fastboot flash dtbo_a "$BACKUP_DIR/current_dtbo_a.img"
fastboot flash vendor_boot_a "$BACKUP_DIR/current_vendor_boot_a.img"
fastboot --set-active=a
fastboot reboot
echo "Rollback enviado."
