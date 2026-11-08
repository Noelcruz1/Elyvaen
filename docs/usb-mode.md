# Modo USB DAC automático

## Objetivo

Convertir el teléfono en DAC USB al conectar un host de datos, sin perder ADB/MTP y volviendo al DAP al desconectar.

## Implementación

- `payload/system/bin/elyvaen-usb-auto.sh`: monitor init cada 2 s.
- `payload/system/bin/elyvaen-usb-dac.sh`: activa/desactiva UAC2, pausa/reanuda la app y mantiene el gadget Android.
- `payload/system/etc/init/elyvaen-uac2.rc`: servicio `elyvaen-usb-auto` y arranque en boot.
- `payload/system/bin/elyvaen.sh`: guarda de arranque de la app cuando UAC2 está activo.

## Detección

- Lee `/sys/class/udc/<udc>/state` y cae a `sys.usb.state`.
- Considera host de datos: `configured`, `suspended`, `*mtp*`, `*ptp*`, `*adb*`.
- Un cargador no configura el UDC y no activa el modo.
- Debounce de dos ciclos antes de desactivar.
- `sys.elyvaen.usbdac.transition=1` evita que el monitor pelee durante el cambio.

## Flujo

1. Host de datos detectado.
2. Se pausa la reproducción con `cmd media_session dispatch pause`/`input keyevent 127`.
3. `elyvaen-usb-dac.sh on` desengancha el UDC, agrega `uac2.0` a la config existente y vuelve a engancharlo.
4. Se arranca `elyvaen-uac2d` en `hal_audio_default`.
5. Si el enlace UAC2 desaparece (por ejemplo, el HAL USB reconstruye el gadget), el monitor lo repara.
6. Al desconectar el host, se detiene el daemon, se quita `uac2.0`, se restaura el gadget y se reanuda la app.

## ADB/MTP

- UAC2 se agrega a `g1`; las funciones ADB/MTP existentes no se eliminan.
- ADB por USB puede caer durante el reenganche; `elyvaen.sh` configura ADB TCP en 5555.
- El monitor no borra funciones ajenas.

## Propiedades

```sh
getprop sys.elyvaen.usbdac.auto
getprop sys.elyvaen.usbdac
getprop sys.elyvaen.usbdac.transition
```

- `auto=1`: modo automático (por defecto en boot).
- `auto=0`: detiene el monitor automático.
- `usbdac=1/0`: estado real del modo DAC.
- `transition=1`: cambio en curso.

## Manual

```sh
setprop sys.elyvaen.usbdac.request 1
setprop sys.elyvaen.usbdac.request 0
/system/bin/elyvaen-usb-dac.sh status
```

## Pendiente de validación

- Confirmar que pausar la app libera el PCM interno para el daemon.
- Confirmar que ADB/MTP siguen enumerando después del reenganche.
- Confirmar que `UsbGadget` no elimina `uac2.0` de forma persistente; si lo hace, el monitor lo repara pero puede haber parpadeo.
- Probar host PC, cargador, hub, suspensión y desconexión.
- No se ha flasheado nada.
