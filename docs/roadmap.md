# Roadmap

## v0.1 — Elyvaen integrado sobre stock (en curso)

- Hornear app Elyvaen DAP + HeliBoard en `product`.
- Hornear audio 24/192/Pure/LOHIFI en `vendor`.
- Hornear animación Aurora, keylayout del botón Assistant e init script.
- Debloat conservador (solo apps; nunca librerías ni HALs).
- Mantener Magisk y los módulos actuales como red de seguridad.
- Validar arranque, táctil, audio, home y kiosco; rollback con `super_ab_safe`.

## v0.2 — Limpieza y consolidación

- Desactivar los módulos Magisk cuyos efectos ya están horneados.
- Verificar que `elyvaen.rc` corre en dominio init y aplica ajustes; si no, usar el módulo como fallback.
- Ajustar permisos priv-app y accesibilidad/notificaciones sin intervención manual.
- Segunda pasada de debloat con lista ampliada, validando cada tanda.
- Integrar Aurora Store/F-Droid si el usuario los quiere.
- Branding completo: `build.prop`, logo, Ajustes, nombre del dispositivo.

## v0.3 — Kernel propio y UAC2

- Base principal: **Mxiier/kernel_motorola_cypfq**, rama `nokernelsu` (Linux 5.4.259, techpack completo, sin KernelSU). Ver `docs/kernel.md`.
- Respaldo/referencia: conjunto oficial Motorola `MMI-S2RYA32.58-13-12-5-9` con `scripts/fetch-kernel-sources.sh`.
- Compilar `vendor/cypfq_defconfig` con `CONFIG_USB_CONFIGFS_F_UAC2=y` y los módulos del mismo build.
- Probar primero con `fastboot boot`; si el bootloader lo ignora, flashear `boot_a` con rollback inmediato. No tocar `super` para esta prueba.
- Verificar que display/`msm-dsi`/`msm_drm` y el táctil sigan funcionando; restaurar `boot_a`, `dtbo_a` y `vendor_boot_a` si falla.
- Implementar el daemon que expone `uac2` por configfs y rutea la captura USB al DAC interno.

## v0.4 — Independencia de Magisk

- Hornear todo lo que hoy es systemless.
- Reescribir ajustes de energía y captura con APIs de sistema.
- Política SELinux propia para el servicio Elyvaen.
- Boot con kernel propio y sin parchear por Magisk.

## v0.5 — Release y mantenimiento

- Repositorio GitHub público con releases, checksums y changelog.
- Script de instalación desde fastboot y guía de recuperación.
- Pruebas de regresión: touch, audio, cámara (si aplica), Wi-Fi, Bluetooth, batería.
- Documentar desviaciones del stock y limitaciones (secure boot, DRM, módem).

## Prohibiciones permanentes

- crDroid como base, recovery, kernel o device tree.
- Borrar `lib64`, HALs, providers, SystemUI, Settings o paquetes base.
- Flashear imágenes de recovery de otra ROM sin validar el panel táctil.
