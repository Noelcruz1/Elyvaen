# Estado del proyecto (checkpoint)

Fecha: 2026-11-15. Regla vigente: **no flashear de nuevo sin aprobación explícita**. El super estable con cámara queda como rollback.

> **Checkpoint activo:** helper nativo `elyvaen-usb` implementado, super
> flasheado y UAC2 enumerando en el host. Queda la prueba de audio real desde
> un PC. `docs/handoff-uac2.md` contiene el diagnóstico histórico; no repetirlo.
>
> **Nuevo (2026-11-15):** app Elyvaen (fork de Booming) + puente de
> propiedades UAC2. Ver "App Elyvaen + puente UAC2" al final del documento.
> Aún no se ha flasheado.

## App Elyvaen + puente UAC2 (2026-11-15)

- Fork de Booming en `/workspace/Android/ElyvaenMusic`:
  `applicationId = com.elyvaen.booming`, label `Elyvaen`, `namespace`
  `com.mardous.booming` (R/código sin cambios). La app principal sigue en el
  dominio `priv_app`, igual que el Booming original, para conservar el acceso
  completo a storage/media (`media_rw_data_file`, FUSE, MediaStore).
- `system_app` **no** sirve para la app principal: no tiene acceso a
  `media_rw_data_file`/`fuse`, así que no podría reproducir música local.
- Puente `com.elyvaen.uac2` (`/workspace/Android/ElyvaenUac2`): app
  preinstalada mínima con un `ContentProvider` (diagnóstico por shell) y un
  `BroadcastReceiver` (control desde la app). Se le asigna `system_app` +
  `system_app_data_file` por nombre en `plat_seapp_contexts` y se añade al
  whitelist de hidden API. Escribe `persist.elyvaen.uac2` y
  `sys.elyvaen.usbdac.auto` usando `android.os.SystemProperties` por reflexión.
- La app principal envía un broadcast explícito al receptor
  `com.elyvaen.uac2/.Uac2Receiver` desde `Uac2Control.kt`; los intents
  explícitos evitan la restricción de package visibility de esta ROM (el
  `ContentResolver.call` al provider devolvía `Unknown authority` incluso con
  `<queries>`). El switch está en la tarjeta "USB DAC (UAC2)" de
  `DapDashboardSheet`. El receptor está protegido por
  `android.permission.WRITE_SECURE_SETTINGS` (la app principal lo tiene por
  `privapp-permissions-com.elyvaen.booming.xml`).
- `persist.elyvaen.` se etiqueta como `system_prop` en
  `plat_property_contexts`; `sys.elyvaen.*` ya cae en la entrada genérica
  `sys.`.
- El `.rc` arranca con UAC2 apagado (`on init`), y el trigger
  `persist.elyvaen.uac2=1/0` → `sys.elyvaen.usbdac.auto=1/0` →
  `start/stop elyvaen-usb` + `stop elyvaen-uac2d`. El helper limpia el gadget
  al recibir SIGTERM.
- APKs: `payload/product/priv-app/ElyvaenBooming/ElyvaenBooming.apk`,
  `payload/product/priv-app/ElyvaenUac2/ElyvaenUac2.apk`.
- Build: `scripts/build-elyvaen-music.sh` y
  `/workspace/Android/ElyvaenUac2/build.sh` (ambos usan el chroot
  `/workspace/sistema` y el SDK `/opt/android-sdk`).
- Pendiente: construir el super, flashearlo con aprobación, poner el nuevo
  home (`com.elyvaen.booming/com.mardous.booming.MainActivity`) y probar el
  toggle en el dispositivo.
- **Flash y prueba (2026-11-15):**
  - El primer super (`1a1ab91c…`) quedó en boot loop: la regla seapp sin
    `isPrivApp=true` invalidaba todo el archivo y zygote abortaba al crear el
    system_server. Corregido y reconstruido.
  - Super corregido `3f9274ee…` flasheado; boot OK, sin errores de
    `plat_seapp_contexts`.
  - Puente probado por `content call` (shell): `set 1` → `ok=true`,
    `persist=1`, `auto=1`, helper `vendor_hal_usb_qti` y daemon
    `hal_audio_default` corriendo, estado `bound`; `set 0` → `ok=true`,
    servicios parados y USB de vuelta a `adb`.
  - La app corre en `priv_app`, home
    `com.elyvaen.booming/com.mardous.booming.MainActivity`, con
    `READ_EXTERNAL_STORAGE` y `WRITE_SECURE_SETTINGS` concedidos; el panel DAP
    y la tarjeta UAC2 se ven correctamente.
  - Primer intento desde la UI: `ContentResolver.call` falló con
    `Unknown authority com.elyvaen.uac2` por falta de `<queries>` (package
    visibility). Corregido en el manifiesto; también se cambió el fallback para
    no marcar el switch como activo si el puente falla y el rc para poner
    `sys.elyvaen.usbdac.state=disabled` al apagar.
- Super final **flasheado** (2026-11-16 00:40 UTC):
  `build-uac2-helper/super.sparse.img`, 5 810 980 056 bytes, SHA-256
  `0d5952350b0721372f7eb36c36264534f1fd1b0a97b29d10011b17d8076598ee`.
  Incluye app `ElyvaenBooming` build6 (broadcast explícito) y puente
  `ElyvaenUac2` v2 (receptor `Uac2Receiver`). Rollback:
  `build-camera-vol/super.sparse.img`
  (`8e6411554b346807f93959e9daa8143aab01c766179bed348c9b1ec49e9767b2`).
- Primer intento (SHA `1a1ab91c…`) quedó en boot loop: la regla
  `user=_app name=com.elyvaen.uac2 …` sin `isPrivApp=true` hacía que
  libselinux marcara la línea como inválida y descartara **todo** el
  `plat_seapp_contexts`; el `system_server` no obtenía contexto y zygote
  abortaba. Corregido a
  `user=_app isPrivApp=true name=com.elyvaen.uac2 domain=system_app type=system_app_data_file levelFrom=user`
  y reconstruido el super.
- Validación estática del super: `plat_seapp_contexts` con la regla
  `com.elyvaen.uac2 → system_app` (antes del bloque genérico `user=_app`),
  `plat_property_contexts` con `persist.elyvaen. → system_prop`,
  `hiddenapi-package-whitelist.xml` con `com.elyvaen.uac2`, APKs
  `ElyvaenBooming`/`ElyvaenUac2` en `/product/priv-app` etiquetados
  `system_file`, permisos priv-app de `com.elyvaen.booming`, rc con servicios
  `disabled` y UAC2 por defecto apagado, y scripts shell antiguos eliminados
  de `/system/bin`. `e2fsck -fn` OK en las cuatro particiones.
- **Prueba final en dispositivo (2026-11-16):**
  - El toggle de la app (tarjeta "USB DAC (UAC2)") enciende y apaga UAC2:
    `setEnabled(true)` → `persist=1`, `auto=1`, helper
    `vendor_hal_usb_qti` + daemon `hal_audio_default`, estado `bound`;
    `setEnabled(false)` → `persist=0`, `auto=0`, servicios parados,
    estado `disabled`, UI "UAC2 desactivado".
  - El puente corre en `u:r:system_app:s0` y el log muestra
    `ElyvaenUac2Bridge: set enabled=... ok=true`.
  - **Nota de caché del PackageManager:** el APK v2 del puente estaba en
    `/product` pero el `PackageSetting` cacheado seguía en v1 y no registraba
    el receptor. En el dispositivo actual se forzó con
    `adb install -r ElyvaenUac2.apk` (actualización en `/data/app`; conserva
    el dominio `system_app`). En un factory reset el APK v2 de `/product` se
    escanea directamente. La app build6 no necesitó actualización aparte: su
    código nuevo carga desde `/product` y el broadcast explícito funciona.
  - Home: `com.elyvaen.booming/com.mardous.booming.MainActivity`; el Booming
    original (`com.mardous.booming`) queda como respaldo.
  - El diálogo "Sistema Android ... Contacte con el fabricante" es el VINTF
    mismatch preexistente; se descarta con "Aceptar" y no lo causa este work.
  - Pendiente: audio real desde un PC (el contenedor no puede abrir
    `/dev/snd`), y la UX overhaul.

## Diagnóstico USB/Windows y línea NAS (2026-11-16)

- Windows no enumera la salida de audio; Linux sí. El descriptor crudo
  (`/sys/bus/usb/devices/3-10/descriptors`) muestra:
  - dispositivo con `bDeviceClass=0`; para un compuesto con IAD debe ser
    `EF/02/01`;
  - el `Audio Control` declara `wTotalLength=64` (incluye una Feature Unit
    de 18 bytes) pero la Feature Unit no está en las matrices dinámicas
    `fs_audio_desc`/`hs_audio_desc`/`ss_audio_desc` de `setup_descriptor`;
    el terminal de salida apunta a la unidad 2 inexistente.
  - El kernel actual es `5.4.259-perf+-elyvaen-uac2-vol-elyvaen-uac2-g21036cc3f384`.
- Fix aplicado y flasheado (2026-11-16):
  - Kernel `boot_uac2_fu.img`: Image #7 (32 332 288 B), ramdisk 12 003 754 B,
    header v3; flasheado en `boot_a`. Super `15ae8033…` flasheado (helper con
    `set_device_class`).
  - Descriptor del host: `bDeviceClass=0xEF/0x02/0x01`, IAD audio,
    `wTotalLength=64`, Feature Unit `0x06` presente y terminal de salida
    `source 2`.
  - El contenedor sigue sin `/dev/snd` (EPERM) y el host tiene
    `snd-usb-audio` desenganchado. Para la prueba analógica, en el host:
    `modprobe -r snd-usb-audio; modprobe snd-usb-audio;
    /workspace/Android/Elyvaen/scripts/test-uac2-line.sh`.
- Línea NAS: el chroot ve `card0 PCH` (Line Input) y `card2 UAC2`, pero el
  contenedor devuelve EPERM al abrir `/dev/snd` (cgroup). La prueba debe
  correr en el host del NAS (o tras recrear OpenCode con
  `--device /dev/snd --group-add audio`). El host tiene el driver
  `snd-usb-audio` desenganchado; recargarlo antes de la prueba.
- Audio UAC2 (2026-11-16): el jack/cable están bien (Booming local llega a
  −0.8 dBFS). El puente UAC2 daba un tono débil (−44 dBFS) y ruido. Causas:
  - `uac2d` enrutaba por `SEC_MI2S_RX` (altavoz) en vez de
    `RX_CDC_DMA_RX_0 Audio Mixer MultiMedia1` (auriculares). Corregido.
  - El escritor insertaba silencio entre bloques parciales: 45% de silencio
    y 113k drops. Corregido; ahora `silence` ≈ 860 frames/min y drops ≈ 2.7k.
  - `RX_RX0/1 Digital Volume` se ponía a 84, fuera del rango −84..40 del
    códec Bolero; el valor correcto es 0 dB. Corregido y verificado por
    lectura (`ctl RX_RX0 Digital Volume=0`).
  - **Formato**: el HAL usa `AUDIO_FORMAT_PCM_24_BIT_PACKED` (S24_3LE, 3
    bytes). `uac2.conf` abría el PCM interno como `s24le` (4 bytes), lo que
    desalinea las muestras. Cambiado `playback_format=s24_3le`.
  - **Puerto DMA**: `RX_CDC_DMA_RX_0 Format` arranca en `S16_LE` (default
    del driver holi), aunque el PCM entregue 24-bit packed. El HAL lo pone a
    `S24_3LE`; `uac2d` ahora también. Sin esto el tono se oye débil y con
    ruido aunque el pico digital sea full-scale (`cap_peak=7.46M`,
    `pb_peak=7.46M`). Ambos controles se aplican **antes** de abrir el PCM,
    porque el puerto DMA se configura en `hw_params`.
  - **Ritmo del puerto DMA**: un volcado completo del mixer (`--dump-mixer`,
    3397 controles) comparó el estado del HAL con reproducción local y el de
    UAC2. **El único control distinto** era
    `RX_CDC_DMA_RX_0 SampleRate`: el HAL usa `KHZ_192` (índice 10) y `uac2d`
    ponía el ritmo del PCM, `KHZ_48` (índice 6). El puerto DMA del códec debe
    ir a **192 kHz** aunque el PCM sea 48 kHz; el DSP remuestrea. Con 48 kHz
    el tono salía ~40 dB bajo.
  - `RX_RX0/1 Digital Volume`: el HAL usa 84 y el códec lo acepta
    (leído de vuelta); con 0 el nivel era ~−44 dBFS. Se usa 84 para igualar
    al HAL.
  - **Solución final (2026-11-16): puente FIFO → AudioTrack.**
    - El PCM crudo de `uac2d` en card0 seguía ~20 dB bajo aunque el mixer
      fuera idéntico al del HAL; el HAL aplica calibración/volumen de stream
      en el DSP que el PCM crudo no recibe.
    - `uac2d` ahora captura UAC2 y, con `output=fifo` en `uac2.conf`,
      escribe **float32 48 kHz estéreo** en
      `/data/local/tmp/elyvaen_uac2.pcm` (remuestrea 44.1/48/96/192 → 48).
    - La app Elyvaen tiene `Uac2PlaybackService` (primer plano) que lee el
      FIFO y lo reproduce con **AudioTrack USAGE_MEDIA**, usando la ruta del
      HAL que sí suena fuerte. El servicio lo arranca el switch del panel DAP
      (`Uac2Control`), no el puente.
    - Política SELinux recompilada con `secilc` (v30, plat+vendor+product+
      system_ext + `elyvaen.cil`): FIFO `shell_data_file` para
      `hal_audio_default` (con `dac_override`, `setattr`), `priv_app` lectura
      y `system_app` (puente antiguo). Boot verificado con la política nueva.
    - **Resultado medido:** tono 1 kHz por la Line Input del NAS: −25.4 dBFS
      estable (captura a −16 dB), armónicos < −66 dB, tanto a 48 kHz como a
      44.1 kHz (remuestreo). El PCM crudo daba −60 dBFS.
    - Pendiente: aplicar el volumen/mute UAC2 del host al FIFO (el volumen
      de Windows no controla aún; usar el volumen multimedia del teléfono) y
      arrancar el servicio al abrir la app/tras reiniciar si el ajuste está
      activo. El servicio del puente (`system_app`) quedó sin uso; el de la
      app es el activo.
  - El volumen del host UAC2 es 0 dB (`initial host volume=0`), no es la
    causa.
  - `uac2d` ahora registra `cap_peak`/`pb_peak` para localizar el punto de
    atenuación.
- Pendiente: prueba en Windows (debería ver la salida con el descriptor
  nuevo), captura de la Line Input del NAS y análisis del WAV.

## Estado actual (2026-11-15, helper nativo)

- Dispositivo flasheado con el super UAC2 `build-uac2-helper/super.sparse.img`.
  Rollback estable con cámara: `build-camera-vol/super.sparse.img`,
  SHA-256 `8e6411554b346807f93959e9daa8143aab01c766179bed348c9b1ec49e9767b2`.
- Causa raíz confirmada: los servicios init con `/system/bin/sh` no arrancan por
  SELinux (`shell_exec` sin `allow init_30_0`; `/vendor/bin/sh` termina en
  `vendor_qti_init_shell`, sin `toolbox_exec`). No repetir ese diagnóstico.
- Solución implementada: helper nativo `src/elyvaen-usb.c` →
  `/vendor/bin/hw/elyvaen-usb`, etiquetado `vendor_hal_usb_qti_exec`. init lo
  ejecuta y transiciona al dominio `vendor_hal_usb_qti`, que puede leer/escribir
  configfs y enlazar el UDC, pero no crear directorios ni poner props `sys.*`.
- El helper configura `functions/uac2.0` (48 kHz, 24-bit, 2 canales), lo enlaza
  en `configs/b.1/uac2.0`, reengancha el UDC y publica estado en
  `vendor.usb.elyvaen.*`. Dos acciones `vendor_init` del `.rc` completan lo que
  el dominio del helper no permite: `mkdir` de la función (trigger
  `vendor.usb.elyvaen.request=*`) y `start`/`stop` del daemon (trigger
  `vendor.usb.elyvaen.dac=1/0`).
- El daemon corre como servicio init con `seclabel hal_audio_default`; no hay
  shell ni `fork` desde el helper.
- La configuración vive en `/vendor/etc/elyvaen/uac2.conf` (los dominios vendor
  no pueden leer `/system/etc`).
- `init.qcom.usb.rc` está comentado en la ROM original; por eso el helper no
  depende de sus acciones `sys.usb.config=diag,uac2,...`.
- Artefactos construidos y **flasheados** el 2026-11-15:
  - Super: `build-uac2-helper/super.sparse.img`
    SHA-256 `63f180e0a84bc2070217ab169e813c47220f090a9909dde3fdce519a7c48b202`.
  - Helper: `build-elyvaen-usb/elyvaen-usb` (arm64 estático, self-test OK,
    SHA-256 `a9e7c613394baa4c7af4d159880c95a33d1af57e769026c0d7464d5f63566495`)
    y copia en `payload/vendor/bin/hw/elyvaen-usb`.
  - Build reproducible: `scripts/build-uac2-helper-super.sh`.
  - Imágenes de trabajo: `build-uac2-helper/work/{product,system,system_ext,vendor}_a.img`.
  - Base: particiones del super estable (product/system/system_ext de
    `build-validate-rom15/work`, vendor de `build-camera-vol/work`);
    `BASE_SUPER=build-camera-vol/super.raw.img`.
- Validación ejecutada: `scripts/validate-uac2.sh` (solo lectura).
- El super conserva el layout LPDump exacto del estable y el vendor del build de
  cámara; solo cambia el payload Elyvaen.
- Limpieza incluida: se elimina el bloque Elyvaen antiguo de `init.qcom.rc` y
  `/vendor/etc/init/elyvaen.rc` (servicio shell roto); queda solo
  `elyvaen-uac2.rc` con el servicio nativo.
- Validación estática hecha: xattr `vendor_hal_usb_qti_exec` en el helper,
  `hal_audio_default_exec` en el daemon, `vendor_file_contexts` actualizado,
  rc sin duplicados, e2fsck OK, layout y hash de vendor verificados.
- Flash realizado: `fastboot flash super build-uac2-helper/super.sparse.img`
  (slot A) y reinicio. El boot, `boot_b` y TWRP no se tocaron.
- Resultado en dispositivo:
  - `init.svc.elyvaen-usb=running` (PID 1385,
    `u:r:vendor_hal_usb_qti:s0`).
  - `init.svc.elyvaen-uac2d=running` (PID 2484,
    `u:r:hal_audio_default:s0`).
  - El host enumera Audio Class: control 01/01/20 y streaming 01/02/20,
    `snd-usb-audio`, 2 canales, `bSubslotSize=3`, `bBitResolution=24`,
    reloj 48000 Hz.
  - ADB sigue funcionando; cámara con 5 dispositivos; home
    `com.mardous.booming/.MainActivity`.
  - Sin denegaciones `avc` para `vendor_hal_usb_qti` ni para el helper.
  - `sys.usb.state`/`sys.usb.config` siguen diciendo `adb` (el HAL no conoce
    `uac2`), pero el kernel sí tiene ADB + UAC2.
- Prueba de reinicio completada: tras `adb reboot`, el helper volvió a
  configurar UAC2 solo. PIDs nuevos 1442 (`vendor_hal_usb_qti`) y 2502
  (`hal_audio_default`); el host volvió a enumerar Audio Class y la cámara
  siguió con 5 dispositivos.
- Pendiente: probar audio real desde un PC (el contenedor de desarrollo no
  puede abrir `/dev/snd/controlC2`: el cgroup devuelve EPERM). También queda
  validar cambio de frecuencia, volumen/mute y reconexión.
- Matiz de audio: el daemon abre el PCM interno directamente, sin pasar por el
  HAL. Si el camino de mezcla no está activo, conviene reproducir unos segundos
  en Booming antes de la prueba UAC2 para que el HAL configure speaker/auricular.
- Nota de diagnóstico: las propiedades `vendor.usb.elyvaen.*` se escriben, pero
  shell no puede leerlas (`vendor_usb_prop`). Para una futura iteración conviene
  relayarlas a `sys.elyvaen.usbdac.*` con una acción `vendor_init`.

> Las secciones siguientes son históricas (2026-11-08 y anteriores) y pueden
> estar obsoletas; no usarlas como estado actual.

## Repositorio

- Local: `/workspace/Android/Elyvaen`
- GitHub: `Noelcruz1/Elyvaen` (público), `main` en `854bff4` antes de este checkpoint.
- Deploy key: `build-kernel/elyvaen_deploy_key` (ignorada por git).
- Commits recientes con mensajes individuales:
  - `4c7bb42` chore: ignore local build and test artifacts
  - `f48a7db` feat(scripts): add bootloader-compatible sparse image converter
  - `0057e58` test(kernel): add isolated vendor and module super builder
  - `22e6346` fix(build): use 256 MiB sparse chunks for super images
  - `099e658` feat(system): make Elyvaen DAP the only home screen
  - `ce7b964` docs(audio): record validated 24-bit 192 kHz configuration
  - `f07f169` feat(kernel): add UAC2 defconfig patch
  - `854bff4` docs(kernel): record UAC2 build and next backend steps

## Dispositivo ahora

- Recuperado al DAP validado tras el incidente UAC2.
- Kernel `5.4.259-perf+-cypfq-test-g368d7ad093dd` (build sin UAC2, el validado).
- Slot A; `boot_b` = TWRP; no se ha tocado.
- Home: `com.mardous.booming/.MainActivity` desde `/product/priv-app/BoomingMusic/BoomingMusic.apk`.
- Táctil `CSOT_7807S`, audio HAL OK; config de audio completa Pure/LOHIFI/24-bit/192k.
- Sin Magisk en el boot actual; los módulos en `/data` no están activos.
- Datos conservados; la app puede estar en onboarding porque se quitó su actualización de `/data`.

## Artefactos clave

- Kernel validado: `build-kernel/` (`Image`, 58 módulos, DTB/DTBO, `boot_test.img`).
- Kernel UAC2 compilado: `build-kernel-uac2/` (`Image`, 58 módulos; UAC2 built-in; vermagic `...-g17dbfeb76641`).
- Patch UAC2: `patches/kernel/0001-config-enable-UAC2.patch`.
- Super DAP con audio completo validado: `build-audio-tuning/super.sparse.img`.
- Super DAP base stock audio: `build-dap-test/super.sparse.img`.
- Super de prueba UAC2 (NO flasheado): `build-uac2-test/super.sparse.img`.
- `boot_uac2_magisk.img` se perdió por el `rm -rf` del script y debe regenerarse antes de cualquier prueba UAC2.
- Backups/rollback: `/workspace/Android/cypfq_backup` (`super_ab_safe.img`, `current_boot_a.img`, `current_vendor_boot_a.img`, `current_dtbo_a.img`, TWRP `boot_b`).

## Lecciones

- El audio viejo no era problema del kernel: la config segura completa (effects/policy/mixer/platform) funciona con el kernel 5.4.259.
- El formato sparse de `img2simg` no lo acepta el bootloader; usar `scripts/raw-to-sparse.py` (chunks 256 MiB).
- El servicio init horneado no se ejecuta; el auto-home se resolvió eliminando `Launcher3QuickStep`, dejando la app DAP como único home.
- No flashear un `super` de prueba sin su `boot` correspondiente; causó el incidente de no enumeración USB.
- El teléfono se recupera con fastboot + `super_ab_safe`/boot validados.

## Cómo continuar

1. Trabajar solo offline/build/commits.
2. Completar backend: kernel release, UAC2 userspace, latencia/performance, pruebas estáticas.
3. Solo al final: un único flash de la ROM terminada, con rollback listo.

## UAC2 userspace (2026-11-08)

- Daemon implementado en `src/uac2d.c`; compila estático arm64 con musl.
- Kernel multifrecuencia: commit local `006ec6844`, parche `patches/kernel/0002-uac2-multirate.patch`; build `build-kernel-uac2-mr/` (Image + 58 módulos).
- Feature Unit volumen/mute: commit local `21036cc3f`, parche `patches/kernel/0003-uac2-volume-mute.patch`; build `build-kernel-uac2-vol/` (Image + 58 módulos).
- Daemon: rate probe, controles ALSA `UAC2 Capture Rate`/`UAC2 Host Volume`/`UAC2 Host Mute`, reapertura al cambiar frecuencia, ring adaptativo y ganancia Q15.
- Modo USB automático: `elyvaen-usb-auto.sh`, pausa de media, activación/desactivación UAC2, reparación del enlace y `sys.elyvaen.usbdac.auto`.
- Daemon RT: `SCHED_FIFO` 80, `nice -20`, `mlockall`, afinidad opcional (`--cpu`) y métricas de drops/xruns/silencio.
- Crossfade de 64 frames en discontinuidades del ring; pendiente validación auditiva.
- Self-test de conversión S16/S24_3/S32 pasa en x86_64, ASan/UBSan y `qemu-aarch64`.
- Binario incluido en `payload/vendor/bin/hw/elyvaen-uac2d` (0755), etiquetado `hal_audio_default_exec`.
- Config: `payload/system/etc/elyvaen/uac2.conf` (48 kHz, 24-bit, 2 canales por defecto).
- Control del gadget: `payload/system/bin/elyvaen-usb-dac.sh` (`on|off|status|restart`).
- Servicios init: `payload/system/etc/init/elyvaen-uac2.rc` (daemon con `seclabel u:r:hal_audio_default:s0` + enable/disable).
- `scripts/build-super.sh` instala binarios de `vendor/bin` con modo 0755, etiqueta el daemon como `hal_audio_default_exec` y agrega la regla a `vendor_file_contexts`.
- Imagen temporal `build-uac2d-images/work/` validó xattr `hal_audio_default_exec`, modo 0755 y regla de file_contexts. No se flasheó.
- `scripts/build-uac2d.sh` compila host para self-test y arm64 estático; toolchain en `/workspace/Android/toolchains/aarch64-linux-musl-cross`.
- **No se ha probado end-to-end**: ni el kernel UAC2 fijo ni el multifrecuencia están flasheados; el dispositivo no estaba conectado durante esta fase.
- Pendiente: probar configfs real (`uac2.0`), tarjeta `UAC2_Gadget`, transición SELinux a `hal_audio_default`, liberación del PCM al pausar la app, ADB/MTP tras el reenganche, resets de `UsbGadget`, y latencia/underruns.
- SELinux resuelto sin Magisk: se reutiliza el dominio/exec type existente `hal_audio_default` del HAL de audio, que ya tiene acceso completo a `audio_device`; el binario va en vendor y se parchea `vendor_file_contexts`.
- Pendiente de audio: validar `SET_CUR`, Feature Unit, ring/ganancia, permisos de `SCHED_FIFO`/`mlockall`, ADB/MTP y liberación del PCM.
- No se flasheó ni se modificó el dispositivo en esta fase.

## Frecuencias automáticas (2026-11-15)

Verificado en dispositivo con `SET_CUR` USB sobre el reloj UAC2: el daemon
detecta el cambio de `UAC2 Capture Rate` y reabre solo en **44100, 48000,
88200, 96000, 176400 y 192000 Hz**. En las seis:

- el capture abre en `s24_3le` a la frecuencia del host;
- el playback interno abre en `s24le`;
- se aplican los 16 controles del path `headphones` + deep buffer;
- no aparece `playback prepare: Invalid argument`.

`rate=48000` en `uac2.conf` es solo el valor inicial/fallback; el host manda.
El helper ya no fuerza `c_srate`, así que no hay peleas ni reaplicaciones.
Pendiente: si el host no reproduce, el daemon mantiene el PCM interno abierto
con silencio; eso puede chocar con Booming. Falta una liberación por inactividad
si se quiere usar la app y el DAC a la vez.

## 24/192 nativo + volumen del host (2026-11-16)

- `elyvaen-uac2d` captura el gadget a la tasa del host (44.1/48/88.2/96/
  176.4/192 kHz) y, con `out_rate=0`, escribe **float32 sin remuestreo** en
  `/data/local/tmp/elyvaen_uac2.pcm`; publica la tasa en `.rate`.
- La app abre un `AudioTrack` float32 a esa tasa (hasta 192 kHz), no a 48 kHz
  fijo. En cambios de tasa recrea el track y descarta muestras viejas.
- Corrección de deriva por `PlaybackParams` (±500 ppm, pitch 1.0) para que el
  FIFO no crezca ni se vacíe; el reloj del DAC manda.
- Volumen/mute del host se leen de `UAC2 Host Volume`/`UAC2 Host Mute` en el
  propio daemon y se aplican antes del FIFO (el host puede silenciar sin que
  la app cambie nada).
- Auto-arranque: `MainActivity.onCreate` llama a `Uac2Control.ensureService`
  si `persist.elyvaen.uac2=1`; el servicio del puente ya no arranca nada.
- Política: `sepolicy/elyvaen.cil` + `scripts/build-sepolicy.sh` compilan
  `payload/vendor/etc/selinux/precompiled_sepolicy` a partir de los CIL de las
  particiones base. El resultado reproducible es `251ae0d3…`.
- `48 kHz` queda solo como modo de compatibilidad (`out_rate=48000`).

## Pruebas nativas y correcciones (2026-11-16)

- 24/192 nativo validado de punta a punta con tono de 1 kHz: `aplay` 24-bit a
  192k, daemon `out=192000 native`, AudioTrack de la app a 192k y captura por
  Line Input del NAS (nivel ≈ −26 dBFS RMS, sin cambio de tono).
- Cambios en caliente validados: 48 → 192 → 96 → 44.1 kHz, con recreación del
  AudioTrack y `rate_file` actualizado.
- Deriva estable en 60 s a 192k (`fifo_errors=0`, sin underruns).
- Mute del host validado (el nivel cae al ruido de fondo).
- Fallo encontrado: el fichero `.rate` quedaba `0600` por el `umask`; la app
  no podía leerlo. Corregido con `fchmod(0666)` en `uac2d`.
- Fallo encontrado: al arrancar con el host ya en 192k, el daemon intentaba
  abrir a 48k y fallaba con `EINVAL`. Corregido consultando
  `UAC2 Capture Rate` antes del primer `open`.
- Fallo encontrado: el Feature Unit usaba `0x03` (solo mute). Corregido a
  `0x0f` en el parche 0005; requiere kernel #8.
- `uac2d` publica `.status` (rate, native, host_volume, host_mute, streaming,
  errors) y acepta `.mode` (`native`/`48000`) para el selector de calidad.
- La app añade estado real del enlace, calidad nativa/48k, volumen del host,
  auto-arranque y un toggle Pure Audio que desactiva el ecualizador.

## Estado final DAP nativo (2026-11-17)

- **Kernel #8** (`boot_uac2_fu8.img`, SHA `6174ba84…`): Feature Unit con
  `bmaControls=0x0f` (volumen+mute UAC2). Image `7bcc5e54…`, mismo release y
  ramdisk que #7; flasheado en `boot_a`.
- **Super** (`dde2ace8…`, 5 810 980 056 B): política `cb6a29cf…`, daemon
  `500e9822…` (crea `.mode` si falta), app `95a07a02…` (la app final
  `fe7bc624…` se instaló como actualización en `/data/app`).
- **App final `fe7bc624…`**: estado de enlace real, calidad Nativo 24/192 /
  48 kHz estable, volumen del host, auto-arranque, Pure Audio, textos es-ES y
  notificación propia (ID 225, canal `elyvaen_uac2`).
- **Puente** `0e55281b…` sin cambios; config `2ee9419f…` (`out_rate=0`,
  `mode_file`, `status_file`).
- Pruebas superadas:
  - 24/192 nativo de punta a punta; 1 kHz confirmado por Goertzel (1000 Hz
    dominante; 500/2000/4000 Hz despreciables).
  - 44.1/48/96/192 en caliente, AudioTrack recreado a la tasa correcta.
  - 60 s a 192k estable (`fifo_errors=0`).
  - Volumen del host: −6 dB → RMS 0.0453 → 0.0221 (mitad exacta); mute baja
    al ruido de fondo; `.status` refleja `host_volume`/`host_mute`.
  - Selector de calidad desde la UI en ambos sentidos; `.mode` se crea
    root:root 0666 al arrancar y la app lo sobrescribe.
  - Toggle UAC2 off/on desde la UI; off detiene daemon/servicio y restaura
    ADB USB; on vuelve a enlazar a 192k.
  - Auto-arranque tras force-stop/relanzar la app; Pure Audio desactiva el
    ecualizador.
  - Notificación UAC2 ID 225 independiente de la notificación de reproducción.
- Rollback: boot #7 `cypfq_backup/boot_uac2_fu7.img` (`553294d7…`); super
  cámara estable `build-camera-vol/super.sparse.img` (`8e641155…`).
- Nota: el super contiene la app `95a07a02…`; la `fe7bc624…` vive como
  actualización en `/data/app`. Un factory reset volvería a la app del super
  (funcional, sin las últimas traducciones/notificación). El diálogo VINTF
  "Sistema Android" es preexistente; se descarta con Aceptar.

## Windows 11 + ADB (2026-11-17)

- Causa confirmada de que Windows no viera la salida: **ADB activo**. El
  composite expone el interfaz vendor de ADB junto a UAC2 y Windows no carga
  `usbaudio2.sys`; Linux sí. Al desactivar la depuración USB, Windows muestra
  el DAC y funciona.
- `sys.usb.config=adb` y `out_frames=0` en el daemon eran los indicios.
- El estado “Conectado · 192 kHz · Nativo” de la app es interno (servicio
  leyendo el FIFO), no confirma que el host esté reproduciendo.
- Pendiente opcional: desenganchar ADB automáticamente en modo DAC o mostrar
  un aviso en el panel.

## Windows 24/96 → 24/192 (2026-11-17)

- Windows ofrecía como máximo 24/96 porque el endpoint HS del gadget
  anunciaba `bInterval=4` + `wMaxPacketSize=1024` (1,024 MB/s); 192k/24-bit
  estéreo necesita 1,152 MB/s.
- `patches/kernel/0006-uac2-hs-endpoint-bandwidth.patch` cambia a
  `bInterval=1` (150 B/microframe, 1,2 MB/s).
- Kernel #9 (`boot_uac2_fu9.img`); solo cambia `boot_a`, no el super.

- Kernel #9 flasheado: `boot_uac2_fu9.img` (`66b753fe…`), Image
  `655bc1e4…`, release `#9`. Endpoint HS OUT verificado en el host:
  `wMaxPacketSize=150` (0x96), `bInterval=1`; ALSA reporta
  `RATE: [44100 192000]`. Pendiente de confirmar la lista 24/192 en Windows.
