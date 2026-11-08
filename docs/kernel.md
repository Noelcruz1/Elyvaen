# Kernel: Mxiier/kernel_motorola_cypfq

Repositorio: <https://github.com/Mxiier/kernel_motorola_cypfq>

Es una fuente de kernel independiente para el Moto G51 5G, Linux **5.4.259**, con el **techpack completo** de Motorola/CAF y los DTS de `cypfq`, incluido el panel táctil. No es crDroid.

## Por qué es importante

El intento anterior con el source oficial `MMI-S2RYA32.58-13-12-5-9` falló porque `kernel-msm` trae `techpack/stub` y no compila display/camera/audio reales. Este repositorio ya incluye:

```
techpack/audio
techpack/camera
techpack/dataipa
techpack/datarmnet
techpack/datarmnet-ext
techpack/display
techpack/video
arch/arm64/boot/dts/vendor/qcom/cypfq-display.dtsi
arch/arm64/boot/dts/vendor/qcom/cypfq-touchscreen.dtsi
arch/arm64/boot/dts/vendor/qcom/holi-cypfq-*-overlay.dts
```

También incluye `drivers/usb/gadget/function/f_uac2.c` y `u_audio.c`, necesarios para UAC2.

## Ramas

| Rama | Uso |
|---|---|
| `nokernelsu` (default) | Base recomendada. Sin KernelSU, compatible con Magisk. README_CN y envsetup incluidos. |
| `a14` | Base con KernelSU y LTO; no usar con Magisk. |

El branch `nokernelsu` además tiene configuraciones de rendimiento (LTO/ThinLTO, O3, HZ=300, uclamp, exfat, debugfs desactivado) y el fix de build de `ili9882`/display.

## Estado de UAC2

El defconfig actual tiene:

```
# CONFIG_USB_CONFIGFS_F_UAC2 is not set
```

El driver está presente. Para el objetivo DAC USB hay que:

1. Añadir `CONFIG_USB_CONFIGFS_F_UAC2=y` al defconfig.
2. Compilar kernel y módulos juntos.
3. Exponer la función `uac2` en el gadget USB (configfs) y enlazarla al UDC.
4. Rutear el stream de captura UAC2 (PC → teléfono) al DAC interno. Sin ese daemon, el teléfono enumera como tarjeta de sonido pero no suena por los audífonos.
5. Validar 24/192 y latencia; el USB 2.0 tiene ancho suficiente.

## Compilación (según README_CN)

Dependencias: Clang (ZyCromerZ), `mkdtimg`, `aarch64-linux-gnu`, `bison`, `flex`, `bc`, `libncurses-dev`, `libssl-dev`, `device-tree-compiler`.

```sh
git clone --depth 1 --branch nokernelsu https://github.com/Mxiier/kernel_motorola_cypfq.git
cd kernel_motorola_cypfq
. envsetup.sh
make vendor/cypfq_defconfig
# opcional UAC2
printf 'CONFIG_USB_CONFIGFS_F_UAC2=y\n' >> arch/arm64/configs/vendor/cypfq_defconfig
make vendor/cypfq_defconfig
make -j"$(nproc)"
```

Salidas:

```
out/arch/arm64/boot/Image
out/arch/arm64/boot/dtbo.img
out/arch/arm64/boot/dts/vendor/qcom/holi-moto-base.dtb
out/**/*.ko
```

- `Image` se empaqueta en `boot.img` con el ramdisk stock (Magisk si se quiere root).
- `dtbo.img` se flashea en `dtbo_a`.
- `holi-moto-base.dtb` se empaqueta en `vendor_boot.img`.
- Los `.ko` van a `/vendor/lib/modules` (imagen vendor o módulo Magisk para pruebas).

## Prueba segura

1. Backup actual: `current_boot_a`, `current_dtbo_a`, `current_vendor_boot_a`.
2. Probar primero solo kernel + módulos con el `vendor_boot`/`dtbo` stock si es posible; si el bootloader ignora `fastboot boot` (ya pasó), flashear `boot_a` con rollback inmediato.
3. Si display/táctil falla, restaurar los tres backups. No tocar `super` para esta prueba.
4. No mezclar `dtbo`/`vendor_boot` de otra ROM.

## Riesgos

- Es un kernel de terceros: hay que validar táctil, display, audio, cámara, Wi-Fi, Bluetooth y suspensión.
- La versión 5.4.259 no es idéntica al kernel stock 5.4.233; los módulos vendor deben ser los compilados con este kernel.
- UAC2 es una fase posterior; primero debe arrancar estable.

## Resultado de la primera prueba (2026-11-08)

Se compiló el branch `nokernelsu` con Alpine Clang 22.1.3, `LLVM_IAS=1`, 3 jobs:

- `Image`: 30.8 MB.
- `vmlinux`: 38.1 MB.
- 58 módulos `.ko` (19.5 MB), vermagic `5.4.259-perf+-cypfq-test-g368d7ad093dd`.
- `holi-moto-base.dtb` y los tres `holi-cypfq-*-overlay.dtbo` (DVT1/DVT2/PVT).

Para esta primera prueba se desactivaron LTO/CFI/SCS, `DEBUG_INFO`, `MODVERSIONS` e `IKHEADERS`; la cámara se excluyó con `BOARD_CONFIG_CAMERA_KERNEL_NAME=none` porque su techpack no compila limpio y no se usa en el DAP.

El `Image` se empaquetó con el ramdisk stock y se flasheó solo en `boot_a`. Resultado:

- El kernel arrancó (`5.4.259-perf+...`) y el sistema llegó a `sys.boot_completed=1`.
- El display funciona y se pudo tomar captura por ADB.
- Táctil/audio no funcionaron porque los módulos vendor siguen siendo del kernel stock 5.4.233 y no cargan en 5.4.259. Es el comportamiento esperado.
- Se restauró `current_boot_a.img`: el teléfono volvió a stock/Magisk, con táctil y datos intactos.
- `boot_b` (TWRP) no se tocó.

Siguiente paso: hornear los 58 módulos nuevos en `/vendor/lib/modules` y sus listas (`modules.load`/`modules.dep`) para probar táctil, audio y WiFi con el kernel nuevo. El build reproduce con `scripts/build-mxiier-kernel.sh`.
