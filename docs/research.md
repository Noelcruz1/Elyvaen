# Investigación de recursos (2026-11-07)

## Decisión principal

La base es la **ROM stock de Motorola** respaldada desde el dispositivo. No se usa crDroid, PixelOS, Matrixx ni LineageOS como base. crDroid queda prohibido también para recovery, `dtbo`, `vendor_boot`, kernel y device tree.

## Firmware stock

- Ya descargado: `/workspace/Android/cypfq_backup/firmware/attmx_full.zip`
  - `cypfq_g-user 12 S2RYAS32.58-13-12-5-1-4`
  - Contiene `flashfile.xml`, `servicefile.xml`, `gpt.bin`, `bootloader.img`, `boot.img`, `vendor_boot.img`, `dtbo.img`, `logo.bin`, `radio.img`, `super.img_sparsechunk.0..11`, etc.
  - SHA-256: `e9da1c3f38fcc2dd4c9e151bde32b711701449e051f6c6fa9361437c13afbe26`.
- El dispositivo declara `S2RYA32.58-13-12-5-1-3`; las imágenes de backup del propio teléfono son la referencia exacta.
- Variantes nuevas publicadas: `S2RYAS32.58-13-12-5-1-6` (OPENCL/ALTMX), `S2RYAS32.58-13-12-5-3-3` (AMXMX/AMXPE) y `S2RYA32.58-13-12-5-6-1` (RETAR). No se adoptan para la primera versión para no cambiar baseband/bootloader sin necesidad.
- Mirrors de consulta: motostockrom.com, filewale.com, firmwaredrive.com, mirrors.lolinet.com (algunas rutas del mirror devuelven 404; el ZIP local es válido).

## Kernel oficial Motorola (sin crDroid)

El tag exacto es **`MMI-S2RYA32.58-13-12-5-9`**. Este es el source oficial más cercano al build `-1-3`; el intento anterior falló porque solo se compiló `kernel-msm`, cuyo `techpack/` es un stub.

Repositorios con el tag verificado mediante `git ls-remote --tags`:

| Repositorio | Tag `MMI-S2RYA32.58-13-12-5-9` |
|---|---|
| `MotorolaMobilityLLC/kernel-msm` | Sí |
| `MotorolaMobilityLLC/kernel-msm-5.4-techpack-display` | Sí |
| `MotorolaMobilityLLC/kernel-msm-5.4-techpack-audio` | Sí |
| `MotorolaMobilityLLC/kernel-msm-5.4-techpack-camera` | Sí |
| `MotorolaMobilityLLC/kernel-msm-5.4-techpack-video` | Sí |
| `MotorolaMobilityLLC/kernel-msm-techpack-dataipa` | Sí |
| `MotorolaMobilityLLC/kernel-devicetree` | Sí |
| `MotorolaMobilityLLC/kernel-display-devicetree` | Sí |
| `MotorolaMobilityLLC/kernel-camera-devicetree` | Sí |
| `MotorolaMobilityLLC/motorola-kernel-modules` | Sí |

URL de descarga:

```text
https://github.com/MotorolaMobilityLLC/<repo>/archive/refs/tags/MMI-S2RYA32.58-13-12-5-9.tar.gz
```

Esto permite unir el kernel con el techpack y los DTS correctos, que es lo que faltaba para que `msm-dsi`/`msm_drm` inicializaran y para habilitar UAC2. El script `scripts/fetch-kernel-sources.sh` descarga ese conjunto.

## Kernel alternativo: Mxiier/kernel_motorola_cypfq

Repositorio revisado el 2026-11-07: <https://github.com/Mxiier/kernel_motorola_cypfq>

- Linux **5.4.259** para `cypfq`, con `techpack` completo (`audio`, `camera`, `display`, `video`, `dataipa`, `datarmnet`, `datarmnet-ext`) y los DTS de `cypfq` (`cypfq-display.dtsi`, `cypfq-touchscreen.dtsi`, overlays DVT1/DVT2/PVT).
- Rama recomendada: **`nokernelsu`** (default), sin KernelSU y compatible con Magisk. Incluye LTO/ThinLTO, O3, HZ=300, uclamp, exfat y correcciones de build de display/`ili9882`.
- La rama `a14` incluye KernelSU; no se usará junto a Magisk.
- El driver UAC2 está presente (`f_uac2.c`, `u_audio.c`) pero desactivado en el defconfig. Se puede activar con `CONFIG_USB_CONFIGFS_F_UAC2=y`.
- Incluye instrucciones de compilación (`README_CN.md`, `envsetup.sh`) y genera `Image`, `dtbo.img`, `holi-moto-base.dtb` y módulos `.ko`.
- Es una fuente de terceros (GPL-2.0), no crDroid. Es la vía preferida para el kernel propio y UAC2; el conjunto oficial Motorola de arriba queda como respaldo/referencia.

## ROMs y device trees de referencia

- `cypfq-development/android_device_motorola_cypfq` (rama `14.0`), `proprietary_vendor_motorola_cypfq`, `android_kernel_motorola_cypfq`: referencia técnica de blobs y módulos; no se usan como base.
- `cypfq-development/cypfq-development.github.io`: builds Matrixx/LineageOS antiguos.
- No hay LineageOS oficial ni builds actuales. La última ROM publicada es crDroid 10.5 (2024-05-15), descartada.

## Recovery y táctil

- El táctil de este Moto G51 tiene una variante de panel que no funciona con las imágenes de recovery de crDroid. Ya se comprobó: al flashear `dtbo`/`vendor_boot` de crDroid se perdió el táctil y se recuperó restaurando los backups originales.
- No hace falta recovery de terceros para este proyecto: fastboot puede escribir `super`, `boot`, `dtbo` y `vendor_boot` directamente.
- Si en el futuro se necesita sideload, primero habría que validar un recovery que respete el panel; no se usará el de crDroid.

## Recovery TWRP (referencia)

- Device tree: <https://github.com/samw662/android_device_motorola_cypfq-twrp> (rama `android-12.1`, release `twrp-3.7.1_12-0`).
- Recovery-as-boot: el TWRP del dispositivo está en `boot_b`; Android en slot A. No existe partición `recovery`.
- Incluye `prebuilt/kernel`, `prebuilt/dtb`, `prebuilt/dtbo.img` basados en stock `S2RYAS32.58-13-12-5-1-6`, y `ILITEK_FW_CSOT_7807S` / `ILITEK_FW_TM_7807S` para los dos paneles táctiles.
- README: táctil funciona en recovery en ambos paneles; en custom ROMs falla; en stock funciona. Es otra evidencia de que la base correcta es stock.
- Se usa solo para recovery/backup/wipe. No se usan sus imágenes en el sistema.

## Blankflash

- Hay referencias públicas a un blankflash para **Moto G51 / XT2171** (`ufiles.aryktech.net`, 2025), pero no se ha verificado el paquete ni su SKU exacto.
- Un blankflash incorrecto puede dejar el equipo irreparable. No se descargará ni usará sin verificar modelo, CID y firma.
- El plan de recuperación actual se basa en fastboot + backups de `super`/`boot`/`dtbo`/`vendor_boot`, que ya han funcionado.

## Herramientas

- `lpmake`, `lpdump`, `lpunpack`, `simg2img`, `img2simg`, `avbtool`, `debugfs`, `e2fsck`, `e2rm`.
- Alpine Linux: `android-tools-*`, `e2fsprogs-extra`, `e2tools`.
- Las imágenes ext4 se editan con `debugfs`/`e2rm`; los xattrs `security.selinux` se fijan con `ea_set` para no dejar archivos sin etiqueta.
