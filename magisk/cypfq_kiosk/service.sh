#!/system/bin/sh
PATH=/system/bin:/vendor/bin:$PATH
LOG=/data/local/tmp/cypfq_kiosk.log
until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 2; done
sleep 8
TASK=""
i=0
while [ "$i" -lt 10 ]; do
  TASK=$(dumpsys activity activities 2>/dev/null | sed -n 's/.*Task{[^#]*#\([0-9][0-9]*\).*A=[0-9]*:com\.mardous\.booming.*/\1/p' | head -1)
  [ -n "$TASK" ] && break
  am start -n com.mardous.booming/.MainActivity >/dev/null 2>&1
  sleep 3
  i=$((i+1))
done
if [ -n "$TASK" ]; then
  cmd activity task lock "$TASK" >> "$LOG" 2>&1
  echo "$(date) pinned task $TASK" >> "$LOG"
else
  echo "$(date) task not found" >> "$LOG"
fi
