# Flasheo y recuperación

## Antes de flashear

- Batería > 50 %.
- ADB Wi-Fi funcionando o cable USB y fastboot disponible.
- Backups presentes en `/workspace/Android/cypfq_backup`.
- No tocar `bootloader`, `gpt`, `radio`, `modem`, `persist` ni `frp`.

## Flashear Elyvaen

El `super` no está ranurado: se flashea completo y luego se elige el slot A.

```sh
fastboot devices
fastboot flash super build/super.sparse.img
fastboot --set-active=a
fastboot reboot
```

Al arrancar, la app debe quedar como home y el script `elyvaen.sh` debe aplicar los ajustes. Si el sistema no arranca, ir a rollback.

## Rollback conocido-bueno

Desde fastboot:

```sh
fastboot flash super /workspace/Android/cypfq_backup/super_ab_safe.img
fastboot flash boot_a /workspace/Android/cypfq_backup/current_boot_a.img
fastboot flash dtbo_a /workspace/Android/cypfq_backup/current_dtbo_a.img
fastboot flash vendor_boot_a /workspace/Android/cypfq_backup/current_vendor_boot_a.img
fastboot --set-active=a
fastboot reboot
```

Si se quiere volver al stock puro:

```sh
fastboot flash super /workspace/Android/cypfq_backup/super_ab_full.img
```


## Recovery personalizado (TWRP) en slot B

El dispositivo **no tiene una partición `recovery` separada**. El TWRP instalado vive en **`boot_b`** (recovery-as-boot), mientras Android vive en el slot A.

- Backup del recovery actual: `/workspace/Android/cypfq_backup/boot_b.img`
  - SHA-256: `33886ada0bec7ffd826e77a183ac4674f3bbea0de745b04466dbcb1f2a7bc47d`
- También se respaldaron `dtbo_b`, `vendor_boot_b`, `vbmeta_b` y `vbmeta_system_b`.
- TWRP de referencia: release `twrp-3.7.1_12-0-cypfq-20240501-3915.img`, guardado como `twrp-3.7.1_12-0-cypfq-release.img`.
- Device tree: <https://github.com/samw662/android_device_motorola_cypfq-twrp> (rama `android-12.1`). Incluye firmware ILITEK para los dos paneles (CSOT y TM), backup/restore, wipe de datos, MTP y flasheo de ZIPs. El propio README confirma: el táctil funciona en recovery en ambos paneles, falla en custom ROMs y funciona en stock.

### Entrar a TWRP

```sh
fastboot --set-active=b
fastboot reboot
```

Para volver a Android:

```sh
fastboot --set-active=a
fastboot reboot
```

### Reglas para no perder el recovery

- **No flashear `boot_b`**: ahí está TWRP.
- No flashear `dtbo_b`, `vendor_boot_b` ni `vbmeta_b` salvo que se restaure el backup.
- El kernel propio de Elyvaen se prueba en `boot_a`, nunca en `boot_b`.
- El wipe de `/data` desde TWRP no borra `boot_b`.
- Si `boot_b` se pierde, restaurar `cypfq_backup/boot_b.img` en `boot_b`, o flashear el release de TWRP en `boot_b` (no en `boot_a`).
- No usar el kernel/`dtbo`/`dtb` precompilados del device tree de TWRP en la ROM: son solo para recovery y están basados en otro build stock (`S2RYAS32.58-13-12-5-1-6`).

## Táctil roto

Síntomas: el sistema arranca pero el touch no responde.

Causa conocida: `dtbo`/`vendor_boot` de otra ROM (crDroid). Solución:

```sh
fastboot flash dtbo_a /workspace/Android/cypfq_backup/current_dtbo_a.img
fastboot flash vendor_boot_a /workspace/Android/cypfq_backup/current_vendor_boot_a.img
fastboot flash boot_a /workspace/Android/cypfq_backup/current_boot_a.img
fastboot reboot
```

No hacer Factory reset: no arregla el táctil y borra datos.

## Entrar a fastboot/recovery sin Volumen -

El botón Volumen - está dañado.

- Recovery: mantener **Power + Volumen +** 15–20 s.
- En recovery: **Advanced → Reboot to bootloader** (navegar con Volumen + y seleccionar con Power).
- Si ADB funciona: `adb reboot bootloader`.

## Fastboot USB desde este entorno

El contenedor de opencode no ve el USB del host. El patrón usado es ejecutar un contenedor con `/dev/bus/usb`:

```sh
export DOCKER_HOST=unix:///workspace/sistema/run/docker.sock
docker run --rm --device-cgroup-rule='c 189:* rmw' \
  -v /dev/bus/usb:/dev/bus/usb \
  -v /srv/dev-disk-by-uuid-a2e4b2e5-95cf-40fb-9af4-0090fb851dbf/opencode/workspace/Android:/work \
  -w /work ghcr.io/anomalyco/opencode:latest \
  -c 'fastboot devices && fastboot flash super /work/Elyvaen/build/super.sparse.img'
```

## Qué no hacer

- No flashear recovery, `dtbo` ni `vendor_boot` de crDroid: está prohibido y rompe el táctil.
- No borrar `lib64`, HALs, providers ni paquetes base (fue la causa del bootloop del primer intento).
- No usar blankflash sin verificar SKU/CID: puede dejar el equipo irreparable.
