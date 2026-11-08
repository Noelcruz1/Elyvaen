# Audio 24/192 validado

Configuración validada sobre el kernel 5.4.259 y la ROM Elyvaen. Se aplicó un cambio por vez hasta confirmar que el HAL seguía funcionando.

## Archivos finales

En `payload/vendor/etc/` y en la imagen `vendor`:

| Archivo | Cambio |
|---|---|
| `audio_effects.xml` | Sin Dolby/DAP ni efectos; Pure Audio |
| `audio_policy_configuration.xml` | Salida por audífonos, sin altavoz; perfiles PCM 24-bit y 192 kHz |
| `mixer_paths.xml` | `RX_HPH_PWR_MODE=LOHIFI` y `RX HPH Mode=CLS_H_LOHIFI` en headphones |
| `audio_platform_info.xml` | `bit_width=24` para auriculares |

## Pruebas realizadas

Se probaron combinaciones acumulativas:

1. `audio_effects.xml` solo → OK.
2. `audio_effects.xml` + `audio_policy_configuration.xml` → OK.
3. Los anteriores + `mixer_paths.xml` → OK.
4. Los anteriores + `audio_platform_info.xml` → OK.

Resultado: `AudioOut_D` a 48 kHz en reposo y un hilo de salida a **192000 Hz** al reproducir un WAV de prueba 24-bit/192 kHz con la app DAP. No aparecen errores `AudioSystem::listAudioPorts` ni fallos del HAL.

## Prueba manual

```sh
adb push elyvaen_24_192.wav /sdcard/Music/
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
  -d file:///sdcard/Music/elyvaen_24_192.wav
adb shell am start -a android.intent.action.VIEW \
  -d file:///sdcard/Music/elyvaen_24_192.wav -t audio/x-wav \
  -p com.mardous.booming
adb shell dumpsys media.audio_flinger | grep -E 'Output thread|Sample rate'
```

## Pendiente

- Reproducción con los IEMs reales y prueba de FLAC 24/192.
- Confirmar el formato de salida de 24 bits, no solo la tasa de 192 kHz.
- UAC2: teléfono como DAC USB para la PC.
- Automatizar ajustes de arranque (kiosco, 120 Hz, DND, batería) sin Magisk.
