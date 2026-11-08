# Backend roadmap

Objetivo: cerrar el backend DAP antes del frontend. Sin Magisk y sin flashear hasta la ROM final.

## Estado actual

- UAC2 multifrecuencia 44.1–192 kHz y control de tasa: implementado.
- Daemon UAC2 en `hal_audio_default`, sin Magisk: implementado.
- Corrección de deriva con ring adaptativo: implementado.
- Release LTO/O3: pendiente.
- Validación end-to-end en dispositivo: pendiente; no se ha flasheado.

## Lista de implementación

### 1. Volumen/mute del host UAC2

- Feature Unit UAC2 en el camino USB OUT → ALSA capture.
- Controles de volumen y mute visibles para Linux/Windows.
- Controles ALSA internos `UAC2 Host Volume` y `UAC2 Host Mute`.
- El daemon aplica ganancia/mute en punto fijo, sin tocar el PCM del kernel.
- Aceptación: mover el volumen de la PC cambia el nivel interno; mute silencia sin cortar el flujo; sin denials.
- Estado: implementado offline; pendiente validación con host real.

### 2. Modo USB DAC automático

- Detectar conexión a host de datos frente a cargador.
- Pausar/liberar la app interna y el PCM de audio.
- Activar UAC2 y conservar ADB/MTP.
- Restaurar DAP al desconectar.
- Aceptación: conectar PC activa el modo sin intervención; desconectar vuelve a la app; ADB sigue disponible.
- Dependencias: servicio init, audio focus, `UsbGadget`.

### 3. Latencia/RT y estabilidad

- `SCHED_FIFO` para captura/playback, afinidad de CPU y `mlockall`.
- Ajuste de periodos/prefill con métricas de underrun y jitter.
- Aceptación: sin underruns en 30 min a 48/192 kHz; latencia estable.
- Dependencias: daemon, permisos del dominio.

### 4. Calidad del ring de deriva

- Crossfade al descartar/insertar o resampler adaptativo.
- Evitar clics en sesiones largas.
- Aceptación: sin clics audibles en 1 h con relojes distintos; drop/insert medido.
- Dependencias: ring actual.

### 5. Release del kernel

- ThinLTO/O3, sin CFI/SCS/debug, UAC2 multifrecuencia.
- Build reproducible y medición de boot/jank/batería.
- Aceptación: `Image`/módulos/DTB coherentes; benchmark antes/después.
- Dependencias: kernel multifrecuencia validado.

### 6. DAC externo USB-C y UAC1

- Modo host USB para DAC externo (`SND_USB_AUDIO` ya compilado).
- UAC1 de compatibilidad para equipos viejos.
- Aceptación: enumeración y reproducción con DAC externo; fallback UAC1.
- Dependencias: política USB/OTG, alimentación.

### 7. DoP/DSD y Bluetooth

- DoP64 a 176.4/192 kHz y validación del códec.
- LDAC/aptX/LC3 si el hardware y vendor lo permiten.
- Aceptación: reproducción estable y conmutable.
- Dependencias: multifrecuencia, codecs vendor.

### 8. Frontend Elyvaen

- Switch DAP/USB DAC, estado, now playing, biblioteca y ajustes.
- Branding Elyvaen; retirar nombres viejos.
- Aceptación: el usuario controla el backend sin shell.
- Dependencias: API de control del daemon y modo automático.

## Orden de ejecución

1. Volumen/mute UAC2.
2. Modo USB DAC automático.
3. RT/latencia.
4. Crossfade/resampler.
5. Release kernel.
6. DAC externo/UAC1.
7. DoP/BT.
8. Frontend.

## Reglas

- Un cambio por commit y por build.
- Medir antes/después; no declarar mejoras sin datos.
- No dejar SELinux en permissive.
- No flashear hasta el cierre del backend.
- Actualizar `docs/state.md` en cada checkpoint.
