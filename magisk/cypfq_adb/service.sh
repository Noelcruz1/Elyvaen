#!/system/bin/sh
until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 2; done
setprop service.adb.tcp.port 5555
stop adbd
start adbd
