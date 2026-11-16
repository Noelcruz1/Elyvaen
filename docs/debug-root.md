# Super de depuración con root ADB

Preparado, **no flasheado**.

## Artefacto

- `build-debug-root/super.sparse.img`
- SHA-256: `f670f638ac19fef5b414b09e6223cbf5a4773a2130af87a7ef6262c284f4a854`
- `ro.debuggable=1`
- `ro.secure=0`
- `ro.build.type=user` (no se cambia)
- Kernel actual: `5.4.259-perf+-elyvaen-uac2-vol-elyvaen-uac2-g21036cc3f384`
- Sin Magisk.
- `boot_b`/TWRP no se tocan.

## Flasheo

```sh
TOOLS=/workspace/Android/cypfq_backup/cypfqos_v2/tools
export LD_LIBRARY_PATH=$TOOLS/lib
$TOOLS/fastboot flash super /workspace/Android/Elyvaen/build-debug-root/super.sparse.img
$TOOLS/fastboot reboot
```

No se reflashea `boot_a`; el kernel actual ya es el correcto.

## Root

```sh
adb root
adb shell id
```

Se espera `uid=0(root)`. Si `adb root` sigue diciendo production build, comprobar:

```sh
adb shell getprop ro.debuggable
adb shell getprop ro.secure
```

## Diagnóstico

```sh
adb shell dmesg | grep -i -E 'elyvaen|init:|service|parse|avc'
adb shell getprop init.svc.elyvaen
adb shell getprop init.svc.elyvaen-usb-auto
adb shell getprop sys.elyvaen.usbdac.auto
```

## Prueba manual UAC2

```sh
adb shell /system/bin/elyvaen-usb-dac.sh on
adb shell /system/bin/elyvaen-usb-dac.sh status
```

El script usa `runcon u:r:hal_audio_default:s0` como fallback si el servicio init no existe.

Luego, en el host:

```sh
lsusb -t
```

Debe aparecer una interfaz Audio Class además de ADB.

## Rollback

```sh
$TOOLS/fastboot flash super /workspace/Android/Elyvaen/build-validate-rom15/super.sparse.img
```

o al DAP validado:

```sh
$TOOLS/fastboot flash super /workspace/Android/Elyvaen/build-audio-tuning/super.sparse.img
```

No se borra `/data` y no se toca `boot_b`.
