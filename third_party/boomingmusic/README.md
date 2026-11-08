# BoomingMusic / Elyvaen DAP

La app integrada en `payload/product/priv-app/BoomingMusic/BoomingMusic.apk` es un build de **BoomingMusic 1.4.2** con las modificaciones de interfaz y ajustes del proyecto Elyvaen.

- Upstream: <https://github.com/mardous/BoomingMusic>
- Commit base: `94e723c84669453f25dfdf9f1db8bd5967d19bed`
- APK de referencia: `BoomingMusic-1.4.2-fdroid-arm64-v8a.apk`
- SHA-256 del APK integrado: `494920be42dc1d9e41c2dd0b26228ac46af6159328baa9796b325015fe8b848b`
- Licencia: GPL-3.0

## Cambios incluidos

- Branding Elyvaen DAP y tema Aurora (`AuroraTheme.kt`).
- Home tipo DAP (`DapAuroraHero`, `DapDashboardSheet`, `DapStatus`, `DapDashboardFragment`).
- Panel DAP con batería, almacenamiento, salida de audio y controles.
- Categoría `HOME` en `MainActivity`.
- Permisos `REBOOT`, `SHUTDOWN` y `WRITE_SECURE_SETTINGS`.
- Ajustes de reproducción: sin autoplay, preferencias DAP, kiosco y equalizer.

El parche completo contra el commit upstream está en `boomingmusic-cypfq.patch`.

## Reconstruir

```sh
git clone https://github.com/mardous/BoomingMusic.git
cd BoomingMusic
git checkout 94e723c84669453f25dfdf9f1db8bd5967d19bed
git apply /ruta/a/boomingmusic-cypfq.patch
./gradlew :app:assembleFdroidRelease
```

El APK resultante se copia a:

```
payload/product/priv-app/BoomingMusic/BoomingMusic.apk
```

y se reconstruye Elyvaen con `scripts/build-super.sh all`.

## Nota GPL

Al distribuir el APK se debe ofrecer el código fuente correspondiente. Este repositorio incluye el parche sobre el commit exacto del upstream; el upstream es público y descargable.

## Rebranding pendiente

El APK actual conserva el branding "Cypfq DAP" en la interfaz. La próxima compilación de BoomingMusic se rebautizará como **Elyvaen DAP** y se actualizarán las capturas.
