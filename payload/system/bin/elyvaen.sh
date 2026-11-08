#!/system/bin/sh
PATH=/system/bin:/vendor/bin:$PATH

until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 2; done
sleep 20

# Only one instance wins: init service first, Magisk module as fallback.
if [ "$(getprop sys.elyvaen.running)" = "1" ]; then
    exit 0
fi
if ! settings get global zen_mode >/dev/null 2>&1; then
    exit 1
fi
setprop sys.elyvaen.running 1

settings put system peak_refresh_rate 120 >/dev/null 2>&1
settings put system min_refresh_rate 60 >/dev/null 2>&1
settings put system user_refresh_rate 120 >/dev/null 2>&1
settings put global window_animation_scale 0.5 >/dev/null 2>&1
settings put global transition_animation_scale 0.5 >/dev/null 2>&1
settings put global animator_duration_scale 0.5 >/dev/null 2>&1
settings put global zen_mode 2 >/dev/null 2>&1
settings put global audio_safe_volume_state 2 >/dev/null 2>&1
settings put secure immersive_mode_confirmations confirmed >/dev/null 2>&1
settings put secure enabled_input_methods helium314.keyboard/.latin.LatinIME >/dev/null 2>&1
settings put secure default_input_method helium314.keyboard/.latin.LatinIME >/dev/null 2>&1

for g in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do
    echo schedutil > "$g" 2>/dev/null
done

cmd package compile -m speed -f com.mardous.booming >/dev/null 2>&1
cmd package set-home-activity --user 0 com.mardous.booming/.MainActivity >/dev/null 2>&1

setprop service.adb.tcp.port 5555 >/dev/null 2>&1
stop adbd
start adbd

audio_tune() {
    command -v tinymix >/dev/null 2>&1 || return 0
    tinymix 'RX_HPH_PWR_MODE' LOHIFI >/dev/null 2>&1
    tinymix 'RX HPH Mode' CLS_H_LOHIFI >/dev/null 2>&1
    tinymix 'RX INT0 DEM MUX' CLSH_DSM_OUT >/dev/null 2>&1
    tinymix 'RX INT1 DEM MUX' CLSH_DSM_OUT >/dev/null 2>&1
    tinymix 'RX_COMP1 Switch' 1 >/dev/null 2>&1
    tinymix 'RX_COMP2 Switch' 1 >/dev/null 2>&1
    tinymix 'HPHL_COMP Switch' 1 >/dev/null 2>&1
    tinymix 'HPHR_COMP Switch' 1 >/dev/null 2>&1
    tinymix 'HPHL_RDAC Switch' 1 >/dev/null 2>&1
    tinymix 'HPHR_RDAC Switch' 1 >/dev/null 2>&1
}

audio_tune

i=0
while [ "$i" -lt 10 ]; do
    TASK=$(dumpsys activity activities 2>/dev/null | sed -n 's/.*Task{[^#]*#\([0-9][0-9]*\).*A=[0-9]*:com\.mardous\.booming.*/\1/p' | head -1)
    [ -n "$TASK" ] && break
    am start -n com.mardous.booming/.MainActivity >/dev/null 2>&1
    sleep 2
    i=$((i+1))
done
[ -n "$TASK" ] && cmd activity task lock "$TASK" >/dev/null 2>&1

while true; do
    settings put global audio_safe_volume_state 2 >/dev/null 2>&1
    C=$(cat /sys/class/power_supply/battery/capacity 2>/dev/null)
    S=$(cat /sys/class/power_supply/battery/charge_control_limit 2>/dev/null)
    case "$C" in
        ''|*[!0-9]*) ;;
        *)
            if [ "$C" -ge 80 ] && [ "$S" != "8" ]; then
                echo 8 > /sys/class/power_supply/battery/charge_control_limit 2>/dev/null
            elif [ "$C" -le 78 ] && [ "$S" != "0" ]; then
                echo 0 > /sys/class/power_supply/battery/charge_control_limit 2>/dev/null
            fi
            ;;
    esac
    audio_tune
    sleep 30
done
