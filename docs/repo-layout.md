# Repositorio único

El proyecto Elyvaen se versiona en este único repositorio. Los antiguos
repositorios independientes de la app y del puente quedaron retirados; su
código vive aquí.

## Estructura

| Ruta | Contenido |
|---|---|
| `src/` | `elyvaen-usb` (helper del gadget) y `uac2d` (captura UAC2 → FIFO). |
| `payload/` | Binarios, rc, configuración y APKs que consume `build-super.sh`. |
| `scripts/` | Builds del daemon, helper, app, kernel y super. |
| `docs/` | Estado, handoff, diagnóstico y esta guía. |
| `ElyvaenMusic/` | Fork de BoomingMusic con el panel DAP y `Uac2PlaybackService`. |
| `ElyvaenUac2/` | Puente `system_app` (`Uac2Receiver`, `Uac2Provider`). |
| `patches/kernel/` | Serie UAC2 del kernel (`0001`-`0004`). |

## Builds

```sh
sh scripts/build-uac2d.sh
sh scripts/build-elyvaen-usb.sh
sh scripts/build-elyvaen-music.sh   # usa ElyvaenMusic/ del repo
sh ElyvaenUac2/build.sh             # copia el APK al payload
sh scripts/build-super.sh
```

## Kernel

El árbol completo (`kernel-sources/kernel_motorola_cypfq`, ~2.9 GB) no se
versiona. Se obtiene de `Mxiier/kernel_motorola_cypfq` (rama base) y se le
aplican los patches de `patches/kernel/`:

- `0001-config-enable-UAC2.patch`
- `0002-uac2-multirate.patch`
- `0003-uac2-volume-mute.patch`
- `0004-uac2-feature-unit.patch`

Después se compila con `scripts/build-mxiier-kernel.sh`.
