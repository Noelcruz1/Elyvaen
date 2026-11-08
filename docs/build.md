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
4. Valida `lpdump` y genera `build/super.sparse.img` con `img2simg`.

## Payload actual

- `product/priv-app/BoomingMusic/BoomingMusic.apk`: APK 1.4.2 personalizado (Elyvaen DAP) extraído del dispositivo; incluye Panel DAP y home tipo DAP.
- `product/app/HeliBoard/HeliBoard.apk`: teclado HeliBoard extraído del dispositivo.
- `product/etc/permissions/privapp-permissions-com.mardous.booming.xml`: permite `REBOOT`, `SHUTDOWN`, `WRITE_SECURE_SETTINGS`, `DUMP` y `BLUETOOTH_PRIVILEGED` a la app como priv-app.
- `product/media/bootanimation.zip` y `shutdownanimation.zip`: animación Aurora.
- `system/bin/elyvaen.sh` y `system/etc/init/elyvaen.rc`: ajustes de arranque.
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
