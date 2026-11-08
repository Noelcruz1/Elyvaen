#!/system/bin/sh
PATH=/system/bin:/vendor/bin:$PATH
until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 2; done
sleep 30
while true; do
  settings put global audio_safe_volume_state 2 >/dev/null 2>&1
  settings put global zen_mode 2 >/dev/null 2>&1
  sleep 600
done
