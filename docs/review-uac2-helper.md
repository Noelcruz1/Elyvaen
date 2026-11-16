# Revisión del super UAC2 (sin flashear)

Fecha: 2026-11-15. Este documento resume exactamente qué contiene el super de
prueba y por qué debería funcionar. **No se ha flasheado nada.**

## Artefacto

| Elemento | Valor |
| --- | --- |
| Imagen | `build-uac2-helper/super.sparse.img` |
| Tamaño | 5 810 980 056 bytes (5.5 GiB) |
| SHA-256 | `63f180e0a84bc2070217ab169e813c47220f090a9909dde3fdce519a7c48b202` |
| Formato | sparse, chunks de 256 MiB (`scripts/raw-to-sparse.py`) |
| Layout | idéntico al super estable (`lpdump` comparado) |
| Rollback | `build-camera-vol/super.sparse.img` |
| SHA rollback | `8e6411554b346807f93959e9daa8143aab01c766179bed348c9b1ec49e9767b2` |

Particiones base (verificadas por hash contra el super estable):

- `product_a`, `system_a`, `system_ext_a`: `build-validate-rom15/work/`.
- `vendor_a`: `build-camera-vol/work/` (incluye `camera.ko` y módulos del kernel).
- `BASE_SUPER`: `build-camera-vol/super.raw.img` (metadatos A/B intactos).

## Cambios por partición

### vendor (lo relevante)

- **Añadido** `/vendor/bin/hw/elyvaen-usb` (0755, ELF64 AArch64 estático,
  71 760 bytes, SHA-256
  `a9e7c613394baa4c7af4d159880c95a33d1af57e769026c0d7464d5f63566495`).
  xattr `security.selinux=u:object_r:vendor_hal_usb_qti_exec:s0`.
- **Añadido** `/vendor/etc/elyvaen/uac2.conf` (0644,
  `vendor_configs_file`): rate 48 kHz, 2 canales, `s24_3le`, `req_number=4`,
  `poll_ms=2500`, `heal_max=3`, `udc=4e00000.dwc3`.
- **Reemplazado** `/vendor/etc/init/elyvaen-uac2.rc`: solo el servicio
  `elyvaen-usb`, el daemon `elyvaen-uac2d` (disabled, `hal_audio_default`) y las
  acciones `vendor_init` para `mkdir` y arranque/parada del daemon.
- **Eliminado** `/vendor/etc/init/elyvaen.rc` (servicio shell roto).
- **Restaurado** `/vendor/etc/init/hw/init.qcom.rc` al fichero stock: se quitó el
  bloque Elyvaen antiguo. `diff` contra el stock: idéntico.
- **Actualizado** `/vendor/etc/selinux/vendor_file_contexts`: una sola línea
  nueva para `elyvaen-usb` → `vendor_hal_usb_qti_exec`. La línea del daemon ya
  estaba y el binario del daemon es idéntico al estable.
- Los XML de audio (`audio_effects`, `audio_platform_info`,
  `audio_policy_configuration`, `mixer_paths`) son idénticos al estable.

### system

- **Eliminado** `/system/etc/elyvaen/uac2.conf` (la config antigua). El
  directorio queda vacío.
- `build.prop` conserva la identidad Elyvaen y `ro.debuggable=0`.
- No se toca el resto de system.

### product

- Idéntico al estable: Booming, HeliBoard, permisos priv-app, animaciones.

### Boot / boot_b

- No se incluyen en `super`; no se tocan. `boot_b` (TWRP) intacto.

## Por qué el dominio elegido

El intento anterior con `/system/bin/sh` falló porque:

- `init_30_0` no tiene `allow init_30_0 shell_exec:file execute`.
- `/vendor/bin/sh` transiciona a `vendor_qti_init_shell`, que no tiene
  `toolbox_exec` (exit 127).

Además, un binario etiquetado `init_exec` **no** sirve: al ejecutarlo desde
init no hay transición y SELinux exige `execute_no_trans`, que no existe para
init. Por eso el helper usa `vendor_hal_usb_qti_exec`:

- `allow init_30_0 vendor_hal_usb_qti_exec:file execute`.
- `typetransition init_30_0 vendor_hal_usb_qti_exec process vendor_hal_usb_qti`.
- El dominio `vendor_hal_usb_qti` (el mismo del HAL USB de Qualcomm) tiene:
  - configfs `file { read write create ... }` (atributos y UDC),
  - configfs `dir { add_name remove_name search ... }` (enlaces),
  - configfs `lnk_file { read create unlink }`,
  - `vendor_configs_file:file read` (vía regla genérica `domain`),
  - `vendor_usb_prop` set (propiedades `vendor.usb.*`),
  - socket de propiedades y `connectto init`.

Lo que **no** puede hacer ese dominio se delega a acciones `vendor_init` del
mismo `.rc`:

- `vendor.usb.elyvaen.request=*` → `mkdir functions/uac2.0` (vendor_init tiene
  `configfs:dir create`).
- `vendor.usb.elyvaen.dac=1/0` → `start`/`stop elyvaen-uac2d` (init interno).

El daemon conserva `seclabel u:r:hal_audio_default:s0`, con `audio_device` y
`proc_asound` ya probados por el HAL de audio.

## Secuencia en el dispositivo

1. `vendor_hal_usb_qti` lee `/vendor/etc/elyvaen/uac2.conf`.
2. Localiza `/config/usb_gadget/g1` y `configs/b.1`.
3. Si falta `functions/uac2.0`, incrementa
   `vendor.usb.elyvaen.request`; `vendor_init` hace el `mkdir`.
4. Lee el UDC de `/config/usb_gadget/g1/UDC` (fallback `udc=`).
5. Detiene el daemon (`dac=0`), desengancha el UDC, retira enlaces previos a
   `uac2.0`, escribe los parámetros de captura y crea
   `configs/b.1/uac2.0`.
6. Reengancha el UDC (ADB/ffs.adb permanece enlazado; no se destruye).
7. Publica `vendor.usb.elyvaen.state=bound` y pone `dac=1`; init arranca el
   daemon.
8. Cada 2.5 s comprueba enlace, parámetros y UDC. Si el HAL reescribe la
   composición, reaplica (máximo 3 veces; luego pausa 5 min).

## Comprobaciones hechas offline

- `--self-test` del helper en x86_64 y `qemu-aarch64`: OK.
- `e2fsck -f -y` y `e2fsck -fn`: OK en las cuatro particiones.
- xattrs: helper `vendor_hal_usb_qti_exec`; daemon
  `hal_audio_default_exec`; config `vendor_configs_file`.
- `lpdump` del super nuevo: idéntico al estable.
- `vendor_a` extraído del super nuevo: hash idéntico a la imagen construida.
- `init.qcom.rc`: `diff` contra stock sin diferencias.
- `vendor_file_contexts`: solo la línea nueva del helper.
- Binarios de audio y XML: idénticos al estable.

## Riesgos / límites conocidos

- **El HAL USB podría reescribir la composición** al cambiar de funciones. El
  helper lo repara, pero puede haber un reenganche visible; tras 3 reparaciones
  pausa 5 minutos para no entrar en bucle.
- **El dominio del helper no puede leer `/proc/asound`**, así que el estado se
  queda en `bound`; la prueba real es `init.svc.elyvaen-uac2d=running`, la
  interfaz Audio Class en el host y el audio.
- **El daemon puede encontrar el PCM interno ocupado** si la app DAP está
  reproduciendo. En esta iteración no se pausa la app; el caso normal es que el
  host reproduzca y la app esté parada.
- **`SCHED_FIFO`/`mlockall`** pueden ser denegados por falta de capacidades en
  `hal_audio_default`; el daemon lo registra y sigue en modo normal.
- **El protocolo de propiedades** usa `PROP_MSG_SETPROP` (128 bytes). Si esta
  ROM no devolviera respuesta, el helper lo registraría como error pero la
  propiedad se aplica igualmente; las acciones de init se disparan por el
  cambio de valor.
- No se ha probado el audio real ni el reenganche en hardware.

## Validación tras un futuro flash

`scripts/validate-uac2.sh` comprueba, sin escribir nada:

- `init.svc.elyvaen-usb` y `init.svc.elyvaen-uac2d`.
- `vendor.usb.elyvaen.dac/state/udc`.
- `pidof elyvaen-uac2d`.
- `dumpsys usb`.
- Interfaces USB de clase Audio (`bInterfaceClass=01`) en el host.
- `logcat` filtrado por `elyvaen` y `avc: denied`.

Rollback: `fastboot flash super build-camera-vol/super.sparse.img`.

## Resultado del flash (2026-11-15)

Flasheado con `fastboot flash super build-uac2-helper/super.sparse.img`
(152,969 s de escritura, 8 sparse chunks), slot A, y reiniciado. El boot,
`boot_b` y TWRP no se tocaron.

Validación en dispositivo:

- `init.svc.elyvaen-usb=running`, PID 1385,
  contexto `u:r:vendor_hal_usb_qti:s0`.
- `init.svc.elyvaen-uac2d=running`, PID 2484,
  contexto `u:r:hal_audio_default:s0`.
- El daemon arrancó por `vendor.usb.elyvaen.dac=1`, lo que prueba que el helper
  completó `mkdir`, parámetros, enlace, reenganche del UDC y publicación de
  estado.
- Host: interfaces Audio Class 01/01/20 (control) y 01/02/20 (streaming),
  driver `snd-usb-audio`, 2 canales FL/FR, `bSubslotSize=3`,
  `bBitResolution=24`, reloj 48000 Hz, endpoint isócrono OUT de 1024 bytes.
- ADB sigue funcionando; cámara con 5 dispositivos; home
  `com.mardous.booming/.MainActivity`.
- Sin `avc: denied` para `vendor_hal_usb_qti` ni para el helper.
- `sys.usb.state`/`sys.usb.config` siguen en `adb` (el HAL no conoce `uac2`);
  el kernel sí expone ADB + UAC2.
- Reinicio probado: tras `adb reboot` el helper reconfiguró UAC2 solo (PIDs
  1442/2502), el host volvió a enumerar Audio Class y la cámara siguió con 5
  dispositivos.

Limitación del entorno: el contenedor de desarrollo ve la tarjeta USB como
`card2` pero su cgroup deniega la apertura de `/dev/snd/controlC2` (EPERM), así
que no se pudo reproducir audio desde aquí. La prueba de audio real debe
hacerse desde un PC normal.

Pendiente:

- Reproducir audio desde un PC y confirmar salida en el teléfono.
- Cambio de frecuencia, volumen/mute y reconexión.
- Relay de `vendor.usb.elyvaen.*` a `sys.elyvaen.usbdac.*` para diagnóstico
  legible por shell (requiere otro build).

## Super con app Elyvaen + puente UAC2 (2026-11-16, flasheado)

| Elemento | Valor |
| --- | --- |
| Imagen | `build-uac2-helper/super.sparse.img` |
| Tamaño | 5 810 980 056 bytes |
| SHA-256 | `0d5952350b0721372f7eb36c36264534f1fd1b0a97b29d10011b17d8076598ee` |
| Rollback | `build-camera-vol/super.sparse.img` |
| SHA rollback | `8e6411554b346807f93959e9daa8143aab01c766179bed348c9b1ec49e9767b2` |

Cambios respecto al super UAC2 anterior:

- **product**: `ElyvaenBooming.apk` (`com.elyvaen.booming`, label `Elyvaen`,
  firmado con la debug key del build, `system_file`; build6 usa un broadcast
  explícito al puente), `ElyvaenUac2.apk` (`com.elyvaen.uac2` v2, puente sin
  UI con `ContentProvider` para diagnóstico y `Uac2Receiver` para el control,
  `system_file`) y `privapp-permissions-com.elyvaen.booming.xml`
  (BLUETOOTH_PRIVILEGED, DUMP, REBOOT, SHUTDOWN, WRITE_SECURE_SETTINGS). El
  Booming original sigue en `/product/priv-app/BoomingMusic` como respaldo.
- **system**: `plat_seapp_contexts` con
  `user=_app isPrivApp=true name=com.elyvaen.uac2 domain=system_app type=system_app_data_file levelFrom=user`
  antes del bloque genérico `user=_app`; `plat_property_contexts` con
  `persist.elyvaen. u:object_r:system_prop:s0`; `hiddenapi-package-whitelist.xml`
  con `com.elyvaen.uac2`; scripts shell antiguos (`elyvaen*.sh`) eliminados de
  `/system/bin`.
- **vendor**: `elyvaen-uac2.rc` con los servicios `disabled`, UAC2 apagado por
  defecto (`on init`), el relé
  `persist.elyvaen.uac2=1/0 → sys.elyvaen.usbdac.auto=1/0 →
  start/stop elyvaen-usb` + `stop elyvaen-uac2d`, y
  `sys.elyvaen.usbdac.state=disabled` al apagar. El helper limpia el gadget al
  recibir SIGTERM.

Validación estática: reglas presentes, APKs con hash idéntico al payload,
`system_file` en ambos APK, permisos priv-app correctos, `vendor_file_contexts`
con los dos binarios, `e2fsck -fn` OK en product/system/system_ext/vendor,
layout LPDump idéntico al super estable.

Prueba en dispositivo (2026-11-16):

- La app arranca como home (`com.elyvaen.booming/com.mardous.booming.MainActivity`),
  en `u:r:priv_app:s0`, con `READ_EXTERNAL_STORAGE` y
  `WRITE_SECURE_SETTINGS` concedidos.
- El puente corre en `u:r:system_app:s0`; el log muestra
  `ElyvaenUac2Bridge: set enabled=true/false ok=true`.
- Toggle ON → `persist=1`, `auto=1`, helper `vendor_hal_usb_qti` y daemon
  `hal_audio_default` corriendo, estado `bound`.
- Toggle OFF → `persist=0`, `auto=0`, servicios parados, estado `disabled`,
  UI "UAC2 desactivado".
- **Caché del PackageManager:** el APK v2 estaba en `/product` pero el
  `PackageSetting` cacheado seguía en v1 y no registraba el receptor. En el
  dispositivo actual se forzó con `adb install -r ElyvaenUac2.apk` (actualiza
  en `/data/app`, conserva `system_app`). En factory reset se escanea el APK
  v2 de `/product`.

Pendiente: audio UAC2 real desde un PC (el contenedor no puede abrir
`/dev/snd`), y la UX overhaul.
