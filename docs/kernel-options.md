# Catálogo de modificaciones del kernel

Objetivo: exprimir audio y experiencia de usuario del Moto G51 como DAP, sin overclock/undervolt y sin sacrificar estabilidad. Cada cambio debe medirse; no se flashea hasta el final.

## Ya activo

- `CONFIG_PREEMPT=y`, `CONFIG_SCHED_WALT=y`, `CONFIG_UCLAMP_TASK=y`, `CONFIG_UCLAMP_BUCKETS_COUNT=20`.
- `CONFIG_CC_OPTIMIZE_FOR_PERFORMANCE_O3=y`, `CONFIG_HZ_300=y`.
- `CONFIG_ZRAM=y`, `CONFIG_EXFAT_FS=y`, `CONFIG_IO_URING=y`.
- `CONFIG_SND_USB_AUDIO=y` (DAC externo por USB-C), `CONFIG_SND_USB_AUDIO_QMI=y`.
- UAC2: `CONFIG_USB_CONFIGFS_F_UAC2=y`, `CONFIG_USB_F_UAC2=y`, `CONFIG_USB_U_AUDIO=y` (built-in).
- Ver [kernel-plan.md](kernel-plan.md) y [state.md](state.md).

## Audio

| Opción | Nivel | Valor DAP | Riesgo |
|---|---|---|---|
| UAC2 completo: configfs + daemon de captura USB → DAC interno | Kernel + userspace | Muy alto: teléfono como DAC USB | Medio |
| USB host audio para DAC externo (Fiio/KA11) | Kernel + userspace | Alto; `SND_USB_AUDIO` ya activo | Bajo |
| Perfiles PCM 24/32-bit y 44.1–384 kHz; cambio de reloj nativo | ALSA + audio policy | Alto; evitar resampling | Medio |
| Bypass total de efectos/Dolby/DAX (Pure Audio) | Kernel modules + config | Alto; señal limpia | Bajo |
| LOHIFI / ganancia de auriculares | Mixer + userspace | Alto; já validado | Bajo |
| Latencia baja: periodos/buffers ALSA, prioridad RT, afinidad de CPU | Userspace + scheduler | Alto; respuesta inmediata | Medio |
| IRQ threaded / RT para audio | Kernel | Medio; menos jitter | Alto |
| `CONFIG_HZ_1000` vs 300 | Kernel | Medio; probar latencia/potencia | Bajo |
| `CONFIG_NO_HZ_FULL` / `isolcpus` para un core de audio | Kernel cmdline | Medio; riesgo de reparto | Alto |
| DSD/DoP nativo en WCD9370/QDSP6 | ALSA/driver | Medio; depende del códec | Alto/investigar |
| Codecs Bluetooth LDAC/aptX/LC3 | Kernel/vendor/userspace | Medio si usas BT | Medio |
| Firmware/calibración de audio | Vendor | Alto; no es kernel | Bajo |
| UAC1 legado | Kernel config | Bajo; compatibilidad vieja | Bajo |
| UAC2 feedback/async y múltiples formatos | Kernel config/configfs | Medio | Medio |
| Wakelocks/runtime PM para evitar cortes al suspender | Kernel + userspace | Medio | Bajo |
| Silenciar micrófonos/entradas no usadas | Mixer/policy | Bajo | Bajo |

## Rendimiento y UX

| Opción | Nivel | Valor | Riesgo |
|---|---|---|---|
| Release con ThinLTO/O3 | Build | Alto; código más optimizado | Medio (build largo) |
| Desactivar CFI/SCS para máximo rendimiento | Build | Medio | Medio (seguridad) |
| Desactivar DEBUG_FS, KALLSYMS_ALL, FTRACE, LOCKDEP, KASAN, UBSAN, SLUB_DEBUG | Build | Medio; menos overhead y boot | Bajo/medio |
| `CONFIG_MODVERSIONS=n`, `IKHEADERS=n`, `MODULE_SIG=n` | Build | Bajo; build más simple | Bajo |
| `CONFIG_CPU_FREQ_DEFAULT_GOV_SCHEDUTIL=y` | Kernel | Medio; governor correcto desde early boot | Bajo |
| Tuning schedutil/uclamp/WALT, rate limits, boost | Kernel + userspace | Alto; fluidez | Medio |
| Afinidad de IRQ/audio y reducción de migraciones | Kernel/userspace | Medio | Medio |
| GPU: idle timeout/ULPS, governor, power collapse | Kernel | Medio; UI/animaciones | Bajo |
| 120 Hz estable y composición sin jank | Display/userspace | Alto | Bajo |
| I/O scheduler (`mq-deadline`/`bfq`/`kyber`) y read-ahead | Kernel | Medio; biblioteca/escaneo | Bajo |
| ZRAM tamaño/algoritmo y PSI | Kernel/userspace | Medio; multitarea | Bajo |
| Thermal profiles y límites de carga | Userspace/thermal HAL | Medio; batería | Bajo |
| Suspensión/wakelocks y deep sleep | Kernel/userspace | Alto; batería | Medio |
| Carga limitada al 80 % | Charger/sysfs | Alto; salud batería | Bajo |
| Touch polling/touch boost | Kernel/userspace | Medio; respuesta | Bajo |
| Reducir drivers no usados (cámara/video/FM/NFC/telefonía) | Build/config | Medio; boot/RAM | Alto |
| Comprimir kernel/Image y reducir boot | Build | Bajo/medio | Bajo |
| Rebase a 5.10/5.15 | Source | Alto a largo plazo | Muy alto |
| Overclock/undervolt | Kernel | No recomendado | Muy alto |

## Orden propuesto

1. **UAC2 userspace** (mayor valor DAP).
2. **Release LTO/O3 + limpieza de debug** y medición.
3. **Latencia baja de audio** (buffers/RT/afinidad) y benchmark de underruns.
4. **USB host audio** con DAC externo.
5. **Power/thermal/batería** y fluidez (schedutil/GPU/I/O).
6. **Avanzados**: DSD/DoP, BT codecs, aislamiento de CPU, trimming agresivo.
7. **Flash único final** y validación con IEMs/PC.

## Reglas de medición

- No declarar mejora sin antes/después: boot, jank, latencia, underruns, batería, temperatura.
- Un cambio por commit y por build.
- Audio: probar 16/44.1, 24/96 y 24/192; FLAC y WAV; auriculares reales.
- UAC2: verificar enumeración en Linux/Windows y reproducción estable.
- Cualquier cambio que rompa touch/audio se revierte antes de seguir.
