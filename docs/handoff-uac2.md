# Handoff UAC2 Elyvaen — 2026-11-15

> **Actualización:** el helper nativo ya está implementado y el super de prueba
> construido. Estado y artefactos vigentes en `docs/state.md`; diseño en
> `docs/usb-mode.md`. Este documento se conserva como diagnóstico histórico.
> No repetir pruebas ya hechas ni reimplementar el helper.

Este documento es el punto de entrada para una conversación nueva. Sustituye al
diagnóstico disperso de la conversación anterior. **No repetir pruebas ya hechas.**

## Objetivo

Terminar Elyvaen para el Moto G51 5G `cypfq` como DAP con salida USB UAC2,
sin Magisk, sin SELinux permisivo y sin tocar `boot_b`/TWRP.

## Estado actual del dispositivo

- Teléfono estable con el super de cámara: `build-camera-vol/super.sparse.img`.
- SHA-256 del super estable:
  `8e6411554b346807f93959e9daa8143aab01c766179bed348c9b1ec49e9767b2`.
- Boot actual: `build-validate-rom/boot_uac2_vol_stock.img`.
  SHA-256: `1084e0f8b95f34ecedbcb1a8b5924d33938c4f20774fa7c0cca3009b86ef162f`.
- Kernel actual: `5.4.259-perf+-elyvaen-uac2-vol-elyvaen-uac2-g21036cc3f384`.
- `Image` del kernel: `build-kernel-uac2-vol/Image`.
  SHA-256: `73693847a4dc4ba1850cc9304a69eccebf3558acb32efd418592edec2764385b`.
- Cámara funcionando: `camera.ko` cargado, 59 módulos, HAL enumera 5 cámaras.
- Home: `com.mardous.booming/.MainActivity`.
- ADB: funciona; `sys.usb.state=adb`. No hay root (`ro.debuggable=0`).
- `sys.elyvaen.usbdac.auto=1`, pero **no hay servicios Elyvaen ni daemon UAC2**.
- `init.svc.elyvaen`, `init.svc.elyvaen-uac2d` e `init.svc.elyvaen-usb-auto` están vacíos.
- UAC2 **no está activo**; no se ha validado el gadget ni el audio.
- Datos, módem, EFS e IMEI no se tocaron. La IMEI nunca se leyó por ADB.

## Causa raíz confirmada (no es el kernel)

Los servicios de init basados en scripts nunca arrancan por SELinux:

1. Los servicios usan `/system/bin/sh`.
   - `/system/bin/sh` está etiquetado `u:object_r:shell_exec:s0`.
   - La política **no tiene** `allow init_30_0 shell_exec`.
   - `init` no puede ejecutarlo; el servicio no se registra como `init.svc.*`.
2. Se probó `/vendor/bin/sh`.
   - `init` sí puede ejecutar `vendor_shell_exec`.
   - Pero la política hace `typetransition init_30_0 vendor_shell_exec process vendor_qti_init_shell`.
   - `vendor_qti_init_shell` **no puede ejecutar `toolbox_exec`**.
   - El log muestra:
     - `avc: denied { read } ... name="toybox" ... scontext=u:r:vendor_qti_init_shell:s0 tcontext=u:object_r:toolbox_exec:s0`
     - `Service 'elyvaen.vsh' exited with status 127`
3. Los servicios de prueba con `/system/bin/sleep` sí arrancaron.
   - Eso demuestra que el parser `.rc`, el bloque al inicio de `init.qcom.rc` y
     el `start` explícito funcionan.
4. `sys.elyvaen.usbdac.auto=1` solo lo pone la acción `on boot`.
   - No demuestra que el servicio/daemon esté vivo.
5. El servicio directo `elyvaen-uac2d` con `seclabel hal_audio_default` estaba
   `disabled` y nunca se disparó. **No está aislado todavía**; no asumir que
   funciona o falla. La causa aguas arriba ya está confirmada.

Conclusión: **el diseño actual de scripts `/system/bin/sh` es inviable en esta
ROM/SELinux**. Cambiar otra vez el `.rc` no lo arregla.

## Lo que ya está hecho y verificado

- Kernel 5.4.259 con UAC2 built-in, multifrecuencia y volumen/mute.
- `src/uac2d.c`: daemon estático arm64; captura UAC2 → playback interno.
- `camera.ko` compilado con techpack completo y cargado; problema de cámara resuelto.
- Super estable actual con cámara y módulos del kernel.
- Diagnóstico de init/SELinux anterior confirmado con logs y política.
- Rollback estable disponible.

## Arquitectura siguiente (recomendada, histórica)

> El diseño final no usa el dominio init: el helper va etiquetado
> `vendor_hal_usb_qti_exec` y `vendor_init` completa `mkdir` y el arranque del
> daemon. Ver `docs/usb-mode.md`.

Implementar un **helper nativo** para el modo USB, sin shell en servicios init:

- Binario nuevo, por ejemplo `src/elyvaen-usb.c` → `/vendor/bin/hw/elyvaen-usb`.
- Servicio init directo, sin `/system/bin/sh` y sin `seclabel` innecesario:
  ```rc
  service elyvaen-usb /vendor/bin/hw/elyvaen-usb
      class late_start
      user root
      group root audio system
  on boot
      start elyvaen-usb
  ```
- El helper debe:
  1. Leer `/system/etc/elyvaen/uac2.conf`.
  2. Vigilar USB host/UDC (`/sys/class/udc/*/state`, power supply).
  3. Crear y configurar `/config/usb_gadget/g1/functions/uac2.0`.
  4. Enlazarlo a la configuración existente y reenganchar el UDC.
  5. Arrancar/parar `elyvaen-uac2d`; preferible `fork`/`exec` directo del
     daemon. La política ya tiene la transición
     `init_30_0 hal_audio_default_exec → hal_audio_default`.
  6. Publicar estado en propiedades o en un archivo local; no depender de
     `setprop` desde shell.
  7. Pausar/reanudar la app es opcional en la primera iteración.
- No usar `/system/bin/sh` en el servicio.
- Alternativa no recomendada: añadir dominio/política SELinux propia y recompilar
  `precompiled_sepolicy`; más frágil en esta ROM.

## Plan por pasos

1. Verificar que el dispositivo sigue en el super estable y que no hay servicios
   `elyvaen` en ejecución.
2. Diseñar y compilar el helper nativo; probar la lógica de configfs en host si
   es posible, y después con un servicio directo.
3. Empaquetar el helper y el `.rc` simplificado en un super de prueba basado en
   `build-camera-vol`; **un solo flash**.
4. Validar:
   - `init.svc.elyvaen-usb=running`;
   - proceso del helper y `elyvaen-uac2d` vivos;
   - `/config/usb_gadget/g1/functions/uac2.0` existe;
   - host enumera UAC2;
   - `dumpsys`/ALSA ven la tarjeta UAC2;
   - ADB/MTP siguen funcionando;
   - `off`/desconexión no dejan el gadget roto.
5. Solo después: latencia, ring, volumen/mute, crossfade y rendimiento.
6. Un flash final con rollback preparado.

## Artefactos y comandos

- Super estable: `build-camera-vol/super.sparse.img`.
- Super regular anterior: `build-validate-rom15/super.sparse.img`.
- Rollback stock: `/workspace/Android/cypfq_backup/super_ab_safe.img`.
- Boot actual: `build-validate-rom/boot_uac2_vol_stock.img`.
- Daemon: `payload/vendor/bin/hw/elyvaen-uac2d` (0755,
  `hal_audio_default_exec`).
- Scripts actuales (referencia, no usar como servicio):
  - `payload/system/bin/elyvaen-usb-dac.sh`
  - `payload/system/bin/elyvaen-usb-auto.sh`
  - `payload/system/bin/elyvaen.sh`
- Config: `payload/system/etc/elyvaen/uac2.conf`.
- Kernel source: `kernel-sources/kernel_motorola_cypfq`, rama `elyvaen-uac2`.
- Commits del kernel: `17dbfeb76`, `006ec6844`, `21036cc3f`.
- Build kernel: `scripts/build-mxiier-kernel.sh` (incluye cámara; requiere
  GNU `coreutils`).
- Build daemon: `scripts/build-uac2d.sh`.
- Toolchain daemon: `/workspace/Android/toolchains/aarch64-linux-musl-cross`.

### Herramientas del entorno

El entorno se reinició varias veces. Si faltan utilidades:

```sh
apk add --no-cache android-tools e2fsprogs e2fsprogs-extra python3 coreutils git
adb devices
fastboot devices
debugfs -V
lpdump --version
```

El `adb` viejo de `/workspace/Android/cypfq_backup/cypfqos_v2/tools` puede fallar
por librerías; usar `/usr/bin/adb` de `android-tools`.

## Restricciones

- **No Magisk.**
- **No SELinux permisivo.**
- **No tocar `boot_b`/TWRP.**
- **No factory reset**, no tocar módem/EFS/persist.
- No flashear sin aprobación explícita del usuario.
- El teléfono es el dispositivo real; los flashes se serializan.
- No paralelizar dos chats que editen el mismo repo o flasheen el mismo teléfono.
- No repetir pruebas de cámara/kernel ya resueltas.

## Prompt para la conversación nueva

```text
Trabaja en /workspace/Android/Elyvaen.
Lee primero docs/handoff-uac2.md y docs/state.md.
Continúa desde el checkpoint 2026-11-15.
La causa raíz ya está confirmada: los servicios init basados en /system/bin/sh
no arrancan por SELinux; /vendor/bin/sh termina en vendor_qti_init_shell y no
puede ejecutar toybox (exit 127). No repitas ese diagnóstico.
El objetivo inmediato es implementar el helper nativo elyvaen-usb, empaquetarlo
en un super de prueba y validar UAC2. No uses Magisk ni SELinux permisivo.
No toques boot_b. Pide aprobación antes de cada flash.
```
