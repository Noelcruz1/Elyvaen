#!/system/bin/sh
PATH=/system/bin:/vendor/bin:$PATH
until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 2; done
sleep 20
settings put system peak_refresh_rate 120 >/dev/null 2>&1
settings put system min_refresh_rate 60 >/dev/null 2>&1
settings put system user_refresh_rate 120 >/dev/null 2>&1
settings put global window_animation_scale 0.5 >/dev/null 2>&1
settings put global transition_animation_scale 0.5 >/dev/null 2>&1
settings put global animator_duration_scale 0.5 >/dev/null 2>&1
settings put global zen_mode 2 >/dev/null 2>&1
settings put global audio_safe_volume_state 2 >/dev/null 2>&1
for g in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do
  echo schedutil > "$g" 2>/dev/null
done
cmd package compile -m speed -f com.mardous.booming >/dev/null 2>&1
while true; do
  settings put global audio_safe_volume_state 2 >/dev/null 2>&1
  sleep 600
done
