# Módulos Magisk de referencia

Estos módulos son la capa systemless que el dispositivo ya tenía funcionando. Elyvaen v0.1 hornea gran parte de su contenido:

- `cypfq_audio_hires` → integrado en `payload/vendor/etc`.
- `cypfq_assistant_button` → integrado en `payload/system/usr/keylayout`.
- `cypfq_aurora` / `cypfq_aurora_product` → integrado en `payload/product/media` (bootanimation y shutdownanimation).
- `cypfq_adb`, `cypfq_battery_care`, `cypfq_dap_system`, `cypfq_kiosk`, `cypfq_os_core` → integrados en `payload/system/bin/elyvaen.sh`.

Los ZIP de animaciones no se guardan en git; están en `payload/product/media` y en `/workspace/Android/cypfq_backup/systemless`.

Se mantienen los módulos en `/data` durante las primeras pruebas como red de seguridad. En v0.2 se desactivan a medida que se valide la integración horneada.
