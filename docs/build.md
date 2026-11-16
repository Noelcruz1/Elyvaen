# Build de Elyvaen

## Dependencias (Alpine)

```sh
apk add android-tools android-tools-lpdump android-tools-lpmake \
    android-tools-lpunpack android-tools-simg2img android-tools-avbtool \
    android-tools-e2fsdroid e2fsprogs e2fsprogs-extra e2tools \
    git python3 wget unzip
```

Comprobar:

```sh
command -v debugfs e2fsck e2rm lpdump img2simg lpmake lpunpack
```

## Base

El script usa por defecto:

- `BACKUP_DIR=/workspace/Android/cypfq_backup`
- `BASE_SUPER=$BACKUP_DIR/super_ab_safe_raw.img` (super A/B validado)
- `BASE_IMAGES=$BACKUP_DIR/safe_ab` (imágenes A stock)

Se puede sobreescribir con variables de entorno.

## Ejecución

```sh
cd /workspace/Android/Elyvaen
./scripts/build-super.sh images   # debloat + payload + build.prop + validación
./scripts/build-super.sh super    # integra las imágenes en super y genera sparse
./scripts/build-super.sh all      # todo

# Super UAC2 sobre la base estable (sin re-debloat):
./scripts/build-uac2-helper-super.sh
```

## Qué hace `images`

1. Copia `product_a`, `system_a`, `system_ext_a` y `vendor_a` a `build/work/`.
2. Elimina las apps de `config/debloat-safe.txt` con `e2rm -r`.
3. Copia el árbol `payload/` en las imágenes:
   - `payload/system` → `/system/...`
   - `payload/vendor` → `/...`
   - `payload/product` → `/...`
4. Crea directorios, asigna modo/uid/gid y etiqueta SELinux:
   - `system_file` para system/product.
   - `vendor_configs_file` para vendor.
5. Añade `config/build.prop.additions` a `/system/build.prop`.
6. Valida con `e2fsck -f -y` y `e2fsck -fn`.
7. Escribe `build/images.sha256` y `build/manifest.txt`.

## Qué hace `super`

1. Copia `super_ab_safe_raw.img` a `build/super.raw.img`.
2. Lee los offsets reales de `lpdump` y sobrescribe solo las particiones A con `dd`.
3. Conserva metadata, grupo B y `system_b` intactos.
4. Valida `lpdump` y genera `build/super.sparse.img` con `scripts/raw-to-sparse.py`
   (chunks de 256 MiB, necesarios para este bootloader).

Si `e2rm` no está disponible, usar `SKIP_DEBLOAT=1` solo cuando las imágenes
base ya estén debloqueadas (es el caso de `build-uac2-helper-super.sh`).

## Payload actual

- `product/priv-app/BoomingMusic/BoomingMusic.apk`: APK 1.4.2 personalizado (Elyvaen DAP) extraído del dispositivo; incluye Panel DAP y home tipo DAP.
- `product/app/HeliBoard/HeliBoard.apk`: teclado HeliBoard extraído del dispositivo.
- `product/etc/permissions/privapp-permissions-com.mardous.booming.xml`: permite `REBOOT`, `SHUTDOWN`, `WRITE_SECURE_SETTINGS`, `DUMP` y `BLUETOOTH_PRIVILEGED` a la app como priv-app.
- `product/media/bootanimation.zip` y `shutdownanimation.zip`: animación Aurora.
- `system/bin/elyvaen.sh`: ajustes de arranque (referencia; ya no se arranca
  por SELinux).
- `vendor/bin/hw/elyvaen-uac2d`: daemon UAC2 (`hal_audio_default_exec`).
- `vendor/bin/hw/elyvaen-usb`: helper nativo de configfs/UDC
  (`vendor_hal_usb_qti_exec`).
- `vendor/etc/init/elyvaen-uac2.rc`: servicio `elyvaen-usb`, daemon
  `elyvaen-uac2d` y acciones `vendor_init` para `mkdir` y arranque del daemon.
- `vendor/etc/elyvaen/uac2.conf`: configuración compartida por helper y daemon.
- `system/usr/keylayout/gpio-keys.kl`: botón Assistant como tecla media.
- `vendor/etc/audio_*.xml`, `mixer_paths.xml`: política 24/192, Pure Audio, LOHIFI y sin altavoz.

## Añadir o quitar archivos

- Añadir: copiar el archivo en la ruta equivalente dentro de `payload/` y reconstruir `images` + `super`.
- Quitar una app: añadir su ruta a `config/debloat-safe.txt` con el prefijo `product` o `system_ext`.
- No meter archivos grandes en `system`: quedan pocos bloques libres. Los APKs van en `product`.

## Verificación

```sh
lpdump build/super.raw.img | head -60
e2fsck -fn build/work/product_a.img
sha256sum build/super.sparse.img
```

## Aviso

El script no flashea nada. El flash y el rollback se documentan en [recovery.md](recovery.md).

## Repositorio único (2026-11-16)

La app vive en `ElyvaenMusic/` de este repo; `scripts/build-elyvaen-music.sh`
ya usa esa ruta por defecto. El puente vive en `ElyvaenUac2/` y su `build.sh`
copia el APK a `payload/product/priv-app/ElyvaenUac2/`. Los cambios de kernel
están en `patches/kernel/` (0001-0004); el árbol `kernel-sources/` se descarga
aparte. Ver `docs/repo-layout.md`.
