# Estado del dispositivo

## Identificación

| Campo | Valor |
|---|---|
| Modelo | Motorola Moto G51 5G (XT2171-1) |
| Codename | `cypfq` / `cypfq_g` |
| SoC | Qualcomm SM4350 Snapdragon 480+ 5G |
| Android | 12 (SDK 31) |
| Build | `S2RYA32.58-13-12-5-1-3` |
| Fingerprint | `motorola/cypfq_gn/cypfq:12/S2RYA32.58-13-12-5-1-3/4efc2-7fcbf0:user/release-keys` |
| Parche de seguridad | 2023-12-01 |
| Slot activo | `_a` |
| Kernel | `5.4.233-moto-g758b13f9e778` |
| Bootloader | Desbloqueado (`ro.boot.flash.locked=0`) |
| Verified boot | `orange` |
| Root | Magisk 30.7 |
| ADB Wi-Fi | IP local del dispositivo, puerto 5555 (puede cambiar) |

## Apps de terceros instaladas

- `com.mardous.booming` (Elyvaen DAP / BoomingMusic 1.4.2) — interfaz principal.
- `helium314.keyboard` (HeliBoard) — teclado.
- `com.topjohnwu.magisk` — root.

El launcher por defecto actual es el de Motorola. Elyvaen debe fijar `com.mardous.booming/.MainActivity` como home al arrancar.

## Particiones lógicas (super)

`super` mide `13,757,317,120` bytes. Metadata v10, 3 slots, grupos A/B de `6,874,464,256` bytes cada uno.

| Partición | Grupo | Tamaño | Offset (sectores) |
|---|---|---:|---:|
| `product_a` | `mot_dp_group_a` | 3,055,362,048 B | 2048 |
| `system_a` | `mot_dp_group_a` | 875,171,840 B | 5,969,920 |
| `system_b` | `mot_dp_group_b` | 38,699,008 B | 7,680,000 |
| `system_ext_a` | `mot_dp_group_a` | 925,724,672 B | 7,755,776 |
| `vendor_a` | `mot_dp_group_a` | 934,834,176 B | 9,564,160 |

`product_b`, `system_ext_b` y `vendor_b` existen en la metadata pero no tienen extents. El grupo B debe conservarse siempre; perderlo fue lo que provocó fallos de arranque en intentos anteriores.

## Backups

Todos en `/workspace/Android/cypfq_backup`:

- `super.img` — dump raw original del dispositivo.
- `super_ab_safe_raw.img` / `super_ab_safe.img` — super A/B validado con audio vendor; **rollback conocido-bueno**.
- `safe_ab/{system,system_ext,product,vendor}_a.img` — imágenes A stock usadas como base de build.
- `current_boot_a.img`, `current_dtbo_a.img`, `current_vendor_boot_a.img` — particiones que arrancan y tienen el táctil correcto.
- `current_vbmeta_a.img`, `current_vbmeta_system_a.img` — verificación desactivada.
- `firmware/attmx_full.zip` — firmware stock `S2RYAS32.58-13-12-5-1-4` (3.04 GB).
- `logo_a.img`, `logo_b.img`, `logo_aurora.bin` — logos originales y Aurora.

## Advertencias

- El botón **Volumen -** está dañado. Para recovery usar **Power + Volumen +**.
- No hacer Factory reset sin necesidad: los datos y la app ya están configurados.
- No flashear `dtbo`, `vendor_boot` ni recovery de crDroid/PixelOS: el táctil de este panel no es compatible y ya se rompió una vez.
- Mantener la batería por encima del 50 % antes de flashear.
