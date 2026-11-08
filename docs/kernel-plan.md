# Backend del kernel: plan de trabajo

Regla: todo se construye y valida offline. **No se flashea nada** hasta cerrar el backend y aprobar la instalación final.

## Estado de partida

- Kernel 5.4.259 validado en dispositivo: boot, display, táctil `CSOT_7807S`, audio y 58 módulos.
- UAC2 habilitado y compilado built-in en la rama local `elyvaen-uac2`; no flasheado.
- Audio 24-bit/192 kHz validado con config Pure/LOHIFI.
- `CONFIG_SND_USB_AUDIO=y` ya está activo (DAC externo por USB-C).
- La build de prueba usa `LTO_NONE`, `DEBUG_FS=y`, `KALLSYMS_ALL=y` y `CPU_FREQ_DEFAULT_GOV_SCHEDUTIL` desactivado.

## K1 — UAC2 en kernel (hecho)

- `CONFIG_USB_CONFIGFS_F_UAC2=y`, `CONFIG_USB_F_UAC2=y`, `CONFIG_USB_U_AUDIO=y`.
- Driver built-in; sin `usb_f_uac2.ko`.
- Patch: `patches/kernel/0001-config-enable-UAC2.patch`.
- Pendiente: userspace/configfs y prueba real al final.

## K2 — Perfil de rendimiento (siguiente build)

Objetivo: kernel de release con el máximo rendimiento razonable, medido, sin romper audio/táctil.

Configuración propuesta:

```
CONFIG_LTO_CLANG=y
CONFIG_THINLTO=y
# CONFIG_LTO_NONE is not set
# CONFIG_CFI_CLANG is not set          (rendimiento; se puede activar en una variante segura)
# CONFIG_SHADOW_CALL_STACK is not set
# CONFIG_DEBUG_INFO is not set
# CONFIG_MODVERSIONS is not set
# CONFIG_IKHEADERS is not set
# CONFIG_DEBUG_FS is not set
# CONFIG_KALLSYMS_ALL is not set
CONFIG_CPU_FREQ_DEFAULT_GOV_SCHEDUTIL=y
CONFIG_CC_OPTIMIZE_FOR_PERFORMANCE_O3=y
CONFIG_HZ_300=y
CONFIG_PREEMPT=y
CONFIG_UCLAMP_TASK=y
CONFIG_SCHED_WALT=y
```

Script: `scripts/build-kernel-release.sh` con `-j3` y `LLVM_PARALLEL_LINK_JOBS=1`.
Salida: `build-kernel-release/`.
No se flashea; se reserva para la instalación final.

## K3 — UAC2 userspace (backend)

1. Exponer `uac2` en `/config/usb_gadget/g1` mediante un script/daemon de arranque.
2. Crear un modo conmutable DAP normal / DAC USB.
3. Capturar el PCM que llega de la PC y reproducirlo en el DAC interno.
4. Preferir un daemon C estático con tinyalsa antes que cadenas de shell.
5. Mantener ADB cuando sea posible; permitir recuperación por USB/TWRP.
6. Empaquetar en la ROM y documentar el modo de uso.

## K4 — Latencia y audio

- Ajustar periodos/buffers ALSA y prioridad RT del hilo de audio.
- Medir underruns con `dumpsys media.audio_flinger` y logs del HAL.
- Probar FLAC 24/192, WAV 24/192 y IEMs reales.
- No tocar el policy/mixer sin una prueba aislada; ya se validó la combinación actual.

## K5 — Ensamblado y flash único

- Super final: ROM Elyvaen + `vendor` con audio validado + módulos del kernel release + kernel UAC2.
- Un solo flash de `super` + `boot` + `vendor_boot`/`dtbo` stock; rollback preparado.
- Validar home automático, táctil, audio, UAC2, batería, kiosco y rendimiento.
- No hacer pruebas experimentales después del flash final.

## K6 — Release

- Commits individuales, changelog, checksums, documentación y push a GitHub.
- Mantener `boot_b`/TWRP intacto.

## Prohibiciones

- No flashear imágenes de prueba.
- No mezclar kernel y ROM sin módulos coincidentes.
- No tocar `boot_b`, datos, `bootloader`, `modem`, `persist` ni `frp`.
- No reutilizar la config de audio vieja sin pruebas aisladas.
