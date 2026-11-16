# Modo USB DAC automático

## Objetivo

Convertir el teléfono en DAC USB al conectar un host de datos, sin perder ADB y
sin Magisk, SELinux permisivo ni servicios basados en shell.

## Implementación (2026-11-15)

El diseño anterior con `/system/bin/sh` fue descartado: SELinux impide que init
ejecute `shell_exec` y que `vendor_qti_init_shell` ejecute toybox. El modo USB
ahora lo gestiona un binario nativo.

- `src/elyvaen-usb.c` → `payload/vendor/bin/hw/elyvaen-usb`.
- Etiqueta SELinux: `u:object_r:vendor_hal_usb_qti_exec:s0` (0755). init lo
  ejecuta y la política transiciona a `vendor_hal_usb_qti`, el mismo dominio del
  HAL USB de Qualcomm. Ese dominio puede leer/escribir configfs, crear/quitar
  enlaces de funciones y enganchar el UDC.
- Lo que ese dominio **no** puede hacer se resuelve con dos acciones
  `vendor_init` en el `.rc`:
  - `vendor.usb.elyvaen.request=*` → `mkdir functions/uac2.0`.
  - `vendor.usb.elyvaen.dac=1/0` → `start`/`stop elyvaen-uac2d`.
- El daemon sigue siendo un servicio init con `seclabel hal_audio_default`
  (`audio_device` + `proc_asound`). No hay shell ni `fork` desde el helper.
- Configuración: `/vendor/etc/elyvaen/uac2.conf` (`vendor_configs_file`), legible
  tanto por `vendor_hal_usb_qti` como por `hal_audio_default`. La copia antigua
  en `/system/etc` se elimina en el build.

## Flujo

1. `on boot` pone `sys.elyvaen.usbdac.auto=1`.
2. El helper espera a que exista `/config/usb_gadget/g1` y su configuración.
3. Lee el UDC enganchado en `/config/usb_gadget/g1/UDC`; si está vacío usa
   `udc=4e00000.dwc3` de `uac2.conf`.
4. Si falta, pide la creación de `functions/uac2.0` (propiedad `request`) y
   espera a que `vendor_init` haga el `mkdir`.
5. Detiene el daemon (`dac=0`), desengancha el UDC, retira cualquier enlace
   previo a `uac2.0` y escribe `p_chmask=0`, `p_srate`, `p_ssize=2`,
   `c_chmask`, `c_srate`, `c_ssize` y `req_number`.
6. Enlaza `functions/uac2.0` en `configs/b.1/uac2.0`, vuelve a enganchar el UDC
   y publica `bound`. Las demás funciones (ADB, MTP, …) no se tocan.
7. Pone `dac=1`: init arranca `elyvaen-uac2d`.
8. Cada `poll_ms` comprueba que el enlace y los parámetros siguen ahí y que el
   UDC está enganchado. Si el HAL USB reconstruye el gadget, reaplica UAC2
   (con pausa tras `heal_max` reparaciones para no entrar en un bucle de
   reenumeración).

## Propiedades

```sh
getprop init.svc.elyvaen-usb
getprop init.svc.elyvaen-uac2d
getprop sys.elyvaen.usbdac.auto
getprop vendor.usb.elyvaen.dac
getprop vendor.usb.elyvaen.state
getprop vendor.usb.elyvaen.udc
```

- `auto=1`: modo automático (por defecto en boot).
- `auto=0`: detiene el helper y desactiva UAC2.
- `dac=1/0`: daemon UAC2 arrancado/parado.
- `state`: `bound`, `disabled`, `error:*`, `heal-paused`, `stopped`.
- `udc`: controlador enganchado.

## Manual

```sh
stop elyvaen-usb       # desactiva UAC2 y para el daemon (init, requiere root)
start elyvaen-usb      # vuelve a activar
/vendor/bin/hw/elyvaen-usb --once status
```

Desde ADB sin root se usan las propiedades y `logcat`. `vendor.usb.elyvaen.*`
es legible por shell; `sys.elyvaen.usbdac.auto` también.

## ADB/MTP

- UAC2 se añade a `g1`; las funciones existentes no se eliminan. El enlace usa
  un nombre propio (`uac2.0`), así que las reescrituras del HAL que solo borran
  `f1..fN` no lo eliminan.
- ADB puede caer durante el reenganche del UDC, pero `ffs.adb` no se destruye:
  adbd sobrevive y el host reenumera ADB + audio.
- El ciclo de auto-reparación tiene límite para evitar parpadeos infinitos.

## Pendiente de validación en dispositivo

- `init.svc.elyvaen-usb=running` y `init.svc.elyvaen-uac2d=running`.
- Sin denegaciones `avc` para `vendor_hal_usb_qti` ni `vendor_init`.
- Creación real de `functions/uac2.0` y enlace en `configs/b.1`.
- Enumeración del host: interfaz Audio Class 01 además de ADB.
- Audio real, cambios de frecuencia, volumen/mute.
- ADB tras el reenganche, desconexión/reconexión, suspensión del host.
- Si el HAL USB reescribe la composición con frecuencia.
- Liberación del PCM interno: si la app DAP está reproduciendo, el daemon puede
  encontrar el dispositivo ocupado; en la primera iteración no se pausa la app.
