# UAC2 backend

## Kernel

- Rama local: `elyvaen-uac2` del source `Mxiier/kernel_motorola_cypfq`.
- Commit: `17dbfeb76 config: enable UAC2 USB audio gadget`.
- Defconfig: `CONFIG_USB_CONFIGFS_F_UAC2=y`, `CONFIG_USB_F_UAC2=y`, `CONFIG_USB_U_AUDIO=y`.
- Multifrecuencia: commit `006ec6844` / `patches/kernel/0002-uac2-multirate.patch`.
- El gadget anuncia 44.1, 48, 88.2, 96, 176.4 y 192 kHz; el host elige por `SET_CUR` y el kernel actualiza `params.c_srate`.
- La tarjeta UAC2 expone el control ALSA de solo lectura `UAC2 Capture Rate` para que el daemon siga el cambio.
- El driver quedó **built-in** en `Image`; no se generó `usb_f_uac2.ko`.
- Build multifrecuencia validada: `KERNEL_BUILD=build-kernel-uac2-mr BRANCH=elyvaen-uac2 JOBS=3 LOCALVERSION=elyvaen-uac2-mr ./scripts/build-mxiier-kernel.sh`.
- El build anterior de UAC2 fijo sigue en `build-kernel-uac2/`; el nuevo está en `build-kernel-uac2-mr/`.
- `boot_uac2_magisk.img` ya existe en `build-uac2-test/`; no se ha flasheado.
- El dispositivo sigue en el kernel/DAP validado anterior.

Topología del driver: `USB OUT -> ALSA capture` (audio que envía la PC) y `ALSA playback -> USB IN` (micrófono visto por la PC). El daemon usa la capture del gadget y reproduce en la tarjeta interna.

## Userspace implementado

- `src/uac2d.c`: puente ALSA sin dependencias externas.
  - Captura del PCM UAC2 (`/dev/snd/pcmCxDyc`) y reproduce en la tarjeta interna (`pcmCxDyp`).
  - Detecta tarjetas por `/proc/asound/cards` y, si SELinux lo bloquea, por `SNDRV_CTL_IOCTL_CARD_INFO` en `/dev/snd/controlC*` (necesario en `hal_audio_default`).
  - Soporta `S24_3LE`, `S24_LE`, `S32_LE` y `S16_LE`, con conversión entre formatos; el códec interno usa `S24_LE`.
  - Prueba frecuencias 192/176.4/96/88.2/48/44.1/... hasta encontrar la que anuncia el gadget; no remuestrea.
  - Lee `UAC2 Capture Rate` cada segundo y reabre el puente si el host cambia de frecuencia.
  - Buffer circular adaptativo (objetivo 3 periodos) que descarta audio viejo si el reloj del host va más rápido o inserta silencio si va más lento.
  - Prefill de 2 periodos y recuperación de underrun/overrun.
  - Reintenta si la tarjeta todavía no existe; se detiene con SIGTERM/SIGINT.
- `payload/system/etc/elyvaen/uac2.conf`: frecuencia/canales que usa el script para configurar el gadget; el daemon tiene defaults y detecta la frecuencia real.
- `payload/system/bin/elyvaen-usb-dac.sh`: crea `uac2.0` en el gadget `g1`, lo enlaza a la config activa, enlaza el UDC y arranca/para el daemon.
- `payload/vendor/bin/hw/elyvaen-uac2d`: binario final; se etiqueta `u:object_r:hal_audio_default_exec:s0`.
- `payload/system/etc/init/elyvaen-uac2.rc`: servicio `elyvaen-uac2d` con `seclabel u:r:hal_audio_default:s0`, arranque por `sys.elyvaen.usbdac=1`.
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
- `rate` es la frecuencia inicial que el script pone en el gadget antes de que el host elija; el daemon detecta después la frecuencia real y reabre si cambia.
- El host puede elegir 44.1/48/88.2/96/176.4/192 kHz sin que el gadget remuestree. La PC puede remuestrear si el archivo no coincide con la frecuencia elegida.
- `playback_format` se deja automático: prueba `S24_3LE`, `S24_LE`, `S32_LE` y `S16_LE` en ese orden.
- El códec interno (`q6asm/q6afe`) anuncia `S24_LE` y `S32_LE`; `S24_LE` conserva 24 bits reales.

## Build

```sh
./scripts/build-uac2d.sh
```

- Requiere `aarch64-linux-musl-gcc`. Toolchain usada: `/workspace/Android/toolchains/aarch64-linux-musl-cross`.
- Genera `build-uac2d/elyvaen-uac2d` (arm64, static-pie, stripped) y lo copia a `payload/vendor/bin/hw/`.
- El binario incluido pasó el self-test en x86_64 y bajo `qemu-aarch64`.
- `scripts/build-super.sh` instala binarios de `vendor/bin` con modo 0755, les pone `hal_audio_default_exec` y agrega la regla a `vendor_file_contexts`.
- El kernel multifrecuencia compila `Image`, 58 módulos y DTB/DTBO en `build-kernel-uac2-mr/`; no se flasheó.

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
- El servicio init usa `sys.elyvaen.usbdac`; el daemon corre en el dominio existente `hal_audio_default` con el exec type del HAL de audio. No usa Magisk ni SELinux permisivo.
- Si el `.rc` no se parsea, el script no arranca el binario en un dominio incorrecto: falla de forma explícita.

## Pendiente de validación

- Probar con el kernel UAC2 flasheado (no hacerlo hasta el final).
- Confirmar la etiqueta del binario (`ls -Z`) y la regla en `/vendor/etc/selinux/vendor_file_contexts`.
- Verificar que el nombre real de la función configfs sea `uac2.0` y que la tarjeta aparezca como `UAC2_Gadget`/`UAC2 PCM`.
- Medir enumeración en Linux y Windows, selección de 44.1/48/88.2/96/176.4/192, cambios de frecuencia en caliente, underruns y latencia.
- Confirmar que el gadget Android no reinicia el UAC2 al cambiar el estado USB.
- Verificar en el dispositivo que el servicio transiciona a `hal_audio_default` y que no hay denials de `audio_device`; la política ya define `init -> hal_audio_default` y `hal_audio_server` tiene acceso ALSA completo. No se usa Magisk.
- Validar la corrección de deriva y ajustar target/capacidad del ring; si aparecen clics, añadir crossfade o resampler adaptativo.
- No se ha flasheado ni modificado el dispositivo en esta fase.

## Matriz de prueba

1. Host Linux: `aplay -D hw:<UAC2> prueba.wav`; comprobar `status` y log del daemon.
2. Host Windows: seleccionar “Elyvaen USB DAC”; reproducir 16/44.1, 24/96 y 24/192.
3. Auriculares internos: validar que el audio sale por el DAC del teléfono, no por el host.
4. Confirmar en el log que el playback interno eligió `s24le` o `s32le`, no `s16le`, para 24 bits.
5. Repetir con `rate=48000` y `rate=192000`.
6. Medir latencia y underruns; ajustar `periods`/`prefill_periods` si hace falta.
7. Probar `off` y confirmar que ADB/MTP vuelven.
