# UAC2 backend

## Kernel

- Rama local: `elyvaen-uac2` del source `Mxiier/kernel_motorola_cypfq`.
- Commit: `17dbfeb76 config: enable UAC2 USB audio gadget`.
- Defconfig: `CONFIG_USB_CONFIGFS_F_UAC2=y`, `CONFIG_USB_F_UAC2=y`, `CONFIG_USB_U_AUDIO=y`.
- El driver quedó **built-in** en `Image`; no se generó `usb_f_uac2.ko`.
- Build: `KERNEL_BUILD=build-kernel-uac2 JOBS=3 ./scripts/build-mxiier-kernel.sh`.
- Vermagic: `5.4.259-perf+-cypfq-test-g17dbfeb76641 SMP preempt mod_unload aarch64`.
- `boot_uac2_magisk.img` ya existe en `build-uac2-test/`; no se ha flasheado.
- El dispositivo sigue en el kernel/DAP validado anterior.

Topología del driver: `USB OUT -> ALSA capture` (audio que envía la PC) y `ALSA playback -> USB IN` (micrófono visto por la PC). El daemon usa la capture del gadget y reproduce en la tarjeta interna.

## Userspace implementado

- `src/uac2d.c`: puente ALSA sin dependencias externas.
  - Captura del PCM UAC2 (`/dev/snd/pcmCxDyc`) y reproduce en la tarjeta interna (`pcmCxDyp`).
  - Detecta la tarjeta UAC2 por `/proc/asound/cards` y elige la interna como la primera distinta.
  - Soporta `S24_3LE`, `S24_LE`, `S32_LE` y `S16_LE`, con conversión entre formatos; el códec interno usa `S24_LE`.
  - No remuestrea: la frecuencia debe coincidir con la configurada en el gadget.
  - Buffer de 4 periodos, prefill de 2 periodos, recuperación de underrun/overrun.
  - Reintenta si la tarjeta todavía no existe; se detiene con SIGTERM/SIGINT.
- `payload/system/etc/elyvaen/uac2.conf`: frecuencia, canales, periodos, formatos.
- `payload/system/bin/elyvaen-usb-dac.sh`: crea `uac2.0` en el gadget `g1`, lo enlaza a la config activa, enlaza el UDC y arranca/para el daemon.
- `payload/system/etc/init/elyvaen-uac2.rc`: servicio `elyvaen-uac2d`, arranque por `sys.elyvaen.usbdac=1`.
- `scripts/build-uac2d.sh`: compila host para self-test y arm64 estático con musl.

## Configuración por defecto

`/system/etc/elyvaen/uac2.conf`:

```ini
rate=48000
channels=2
periods=4
prefill_periods=2
period_frames=0
capture_device=0
playback_device=0
capture_format=s24_3le
```

- 48 kHz es el valor seguro para la primera prueba.
- Para 24/192 cambiar `rate=192000`; debe coincidir con `c_srate` del gadget. El script lee el mismo archivo.
- El gadget UAC2 de este kernel anuncia una sola frecuencia. Si la PC reproduce 44.1 kHz, el host remuestrea a la frecuencia del gadget; no es bit-perfect.
- `playback_format` se deja automático: prueba `S24_3LE`, `S24_LE`, `S32_LE` y `S16_LE` en ese orden.
- El códec interno (`q6asm/q6afe`) anuncia `S24_LE` y `S32_LE`; `S24_LE` conserva 24 bits reales.

## Build

```sh
./scripts/build-uac2d.sh
```

- Requiere `aarch64-linux-musl-gcc`. Toolchain usada: `/workspace/Android/toolchains/aarch64-linux-musl-cross`.
- Genera `build-uac2d/elyvaen-uac2d` (arm64, static-pie, stripped) y lo copia a `payload/system/bin/`.
- El binario incluido pasó el self-test en x86_64 y bajo `qemu-aarch64`.
- `scripts/build-super.sh` instala binarios de `system/bin` con modo 0755.

## Activar/desactivar en el dispositivo

Requiere root (servicio init o shell root):

```sh
/system/bin/elyvaen-usb-dac.sh on
/system/bin/elyvaen-usb-dac.sh status
/system/bin/elyvaen-usb-dac.sh off
```

También se puede disparar por init:

```sh
setprop sys.elyvaen.usbdac.request 1
setprop sys.elyvaen.usbdac.request 0
```

- `on` desengancha temporalmente el UDC, crea `functions/uac2.0`, lo enlaza a `configs/b.1`, vuelve a enlazar el UDC y arranca el daemon.
- ADB por USB puede caerse durante el cambio; ADB TCP ya está configurado por `elyvaen.sh`.
- `off` detiene el daemon, elimina el enlace/función y restaura el gadget Android.
- El servicio init usa `sys.elyvaen.usbdac`; si el `.rc` no se parsea, el script intenta arrancar el daemon directamente.

## Pendiente de validación

- Probar con el kernel UAC2 flasheado (no hacerlo hasta el final).
- Verificar que el nombre real de la función configfs sea `uac2.0` y que la tarjeta aparezca como `UAC2_Gadget`/`UAC2 PCM`.
- Medir enumeración en Linux y Windows, 24/48 y 24/192, underruns y latencia.
- Confirmar que el gadget Android no reinicia el UAC2 al cambiar el estado USB.
- Resolver SELinux/dominio del servicio init si el dominio `init` no puede abrir `/dev/snd` o escribir configfs. La política stock `user` no muestra `allow init audio_device`; el dominio que sí tiene acceso completo es `hal_audio_server`, y `audioserver` solo aparece con `read/write`. Si el servicio falla, la solución correcta es un dominio/exec type propio en sepolicy (o Magisk como puente temporal), no dejar SELinux en permissive.
- Evaluar deriva de reloj y, si hace falta, buffer adaptativo o resampler controlado.
- No se ha flasheado ni modificado el dispositivo en esta fase.

## Matriz de prueba

1. Host Linux: `aplay -D hw:<UAC2> prueba.wav`; comprobar `status` y log del daemon.
2. Host Windows: seleccionar “Elyvaen USB DAC”; reproducir 16/44.1, 24/96 y 24/192.
3. Auriculares internos: validar que el audio sale por el DAC del teléfono, no por el host.
4. Confirmar en el log que el playback interno eligió `s24le` o `s32le`, no `s16le`, para 24 bits.
5. Repetir con `rate=48000` y `rate=192000`.
6. Medir latencia y underruns; ajustar `periods`/`prefill_periods` si hace falta.
7. Probar `off` y confirmar que ADB/MTP vuelven.
