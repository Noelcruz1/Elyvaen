# Contribuir a Elyvaen

Elyvaen es un proyecto abierto para convertir el Moto G51 5G (`cypfq`) en un DAP dedicado. Antes de enviar cambios, ten en cuenta estas reglas.

## Reglas del proyecto

- **crDroid está prohibido** como base, recovery, kernel o device tree.
- No se mezclan imágenes de recovery de otra ROM con la ROM stock: eso rompe el táctil de este panel.
- El slot B está reservado para TWRP (`boot_b`). No se toca.
- No se eliminan `lib64`, HALs, providers, SystemUI, Settings ni paquetes base como parte del debloat.
- Los cambios de audio buscan 24/192, Pure Audio y LOHIFI; no se aceptan efectos que degraden la salida.
- Los scripts deben ser POSIX `sh` y funcionar en Alpine Linux.

## Cómo probar un cambio

1. Construir imágenes y super:

```sh
BACKUP_DIR=/workspace/Android/cypfq_backup ./scripts/build-super.sh all
```

2. Validar:

```sh
lpdump build/super.raw.img
e2fsck -fn build/work/product_a.img
```

3. Probar el kernel con `scripts/build-mxiier-kernel.sh` y un `boot_test.img`; no flashear `boot_b`.

## Pull requests

- Explica qué cambia, qué se validó y qué no.
- Incluye logs o capturas si el cambio es de UX/audio.
- No subas imágenes stock, firmware, backups de particiones ni claves.
- Respeta la GPL-3.0 del proyecto y la GPL de BoomingMusic.

## Ideas bienvenidas

- Integración de módulos vendor para el kernel 5.4.259.
- UAC2: exponer el teléfono como DAC USB y rutear la captura al DAC interno.
- Debloat seguro con validación de arranque.
- Mejoras de la interfaz Elyvaen DAP.
- Documentación y recetas de recuperación.
