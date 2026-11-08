#!/system/bin/sh
sleep 30
while true; do
  level=$(cat /sys/class/power_supply/battery/capacity 2>/dev/null)
  case "$level" in
    ''|*[!0-9]*) ;;
    *)
      if [ "$level" -ge 80 ]; then
        echo 8 > /sys/class/power_supply/battery/charge_control_limit 2>/dev/null
      elif [ "$level" -le 78 ]; then
        echo 0 > /sys/class/power_supply/battery/charge_control_limit 2>/dev/null
      fi
      ;;
  esac
  sleep 60
done
