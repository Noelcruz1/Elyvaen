# Validación del paquete de prueba

Fecha: 2026-11-08. **No se ha flasheado nada**: no hay dispositivo ADB/fastboot conectado.

## Artefactos

- Boot sin Magisk: `build-validate-rom/boot_uac2_vol_stock.img`
  - SHA-256: `1084e0f8b95f34ecedbcb1a8b5924d33938c4f20774fa7c0cca3009b86ef162f`
  - Kernel: `build-kernel-uac2-vol/Image` SHA-256 `73693847a4dc4ba1850cc9304a69eccebf3558acb32efd418592edec2764385b`.
  - Ramdisk stock sin Magisk, extraído de `boot_test.img`.
  - Header v3, OS 11.0.0, patch 2023-12, sin cmdline extra.
- Super: `build-validate-rom/super.sparse.img`
  - SHA-256: `a54445c059854084e8235b2d054aec73ed69ac61b4e4666b69d5a0f83a031021`
  - 5.4 GB sparse; mismo layout A/B que `super_ab_safe_raw.img`.
- Daemon: `payload/vendor/bin/hw/elyvaen-uac2d`
  - SHA-256: `e46a949b0b7cb7cfe77e9e91f6ed41c33fb5bf5405456cf37a41f3d9af24c672`.
- Kernel: 30.8 MB, 58 módulos, vermagic `5.4.259-perf+-elyvaen-uac2-vol-elyvaen-uac2-g21036cc3f384`.

## Validaciones realizadas

- Kernel: build completo; `CONFIG_USB_CONFIGFS_F_UAC2=y`, `CONFIG_USB_F_UAC2=y`, `CONFIG_SND_USB_AUDIO=y`.
- Módulos: 58 `.ko`, vermagic coincide con el kernel; `modules.load`/`modules.dep` generados.
- Boot: `unpack_bootimg` confirma kernel/ramdisk correctos; ramdisk sin `overlay.d`/Magisk.
- Super: `lpdump` A/B correcto; `e2fsck -fn` sin errores en product/system/system_ext/vendor.
- Vendor: 58 módulos + listas; audio configs seguros; binario UAC2 0755 con `hal_audio_default_exec`.
- `vendor_file_contexts`: regla para `/vendor/bin/hw/elyvaen-uac2d`.
- System: init UAC2/auto/on/off, `elyvaen.sh`, `elyvaen-usb-auto.sh` 0755; sin binario UAC2 viejo en `/system/bin`.
- Product: BoomingMusic presente; Launcher3QuickStep eliminado.
- Daemon: self-test, ASan/UBSan, qemu-aarch64 y compilación arm64 estática.

## Pendiente de validación real

- Arranque, táctil, audio interno, cámara, WiFi, BT y suspensión.
- UAC2: enumeración, Feature Unit, 44.1–192 kHz, `SET_CUR`, volumen/mute, ring, auto mode, ADB/MTP.
- `SCHED_FIFO`/`mlockall` y permisos SELinux en dispositivo.
- No se puede validar nada de eso sin conectar el teléfono.

## Flasheo preparado (NO ejecutado)

```sh
TOOLS=/workspace/Android/cypfq_backup/cypfqos_v2/tools
export LD_LIBRARY_PATH=$TOOLS/lib
$TOOLS/fastboot flash boot_a /workspace/Android/Elyvaen/build-validate-rom/boot_uac2_vol_stock.img
$TOOLS/fastboot flash super /workspace/Android/Elyvaen/build-validate-rom/super.sparse.img
$TOOLS/fastboot reboot
```

- Solo `boot_a`; **no tocar `boot_b`/TWRP**.
- Rollback:
  ```sh
  $TOOLS/fastboot flash boot_a /workspace/Android/cypfq_backup/current_boot_a.img
  $TOOLS/fastboot flash super /workspace/Android/cypfq_backup/super_ab_safe.img
  ```

## Primeras pruebas tras flashear

```sh
adb shell getprop sys.boot_completed
adb shell uname -r
adb shell dmesg | grep -i uac2
adb shell ls -l /config/usb_gadget/g1/functions
adb shell cat /proc/asound/cards
adb shell getprop sys.elyvaen.usbdac.auto
```

Luego conectar a PC y comprobar tarjeta de sonido, volumen/mute y selección de frecuencia.
