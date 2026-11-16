#!/bin/sh
set -eu

# Prueba UAC2 de punta a punta usando la Line Input del NAS.
#
# Requiere:
#   - contenedor con /dev/snd (--device /dev/snd --group-add audio)
#   - chroot /workspace/sistema con alsa-utils + sox
#   - teléfono con UAC2 disponible (el script intenta encenderlo vía adb)
#   - cable jack-jack del teléfono a la Line Input del NAS
#
# El teléfono aparece como card2 y la Line Input del NAS como card0.
# Si los números cambian: CARD_UAC2=... CARD_LINE=... ./test-uac2-line.sh

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
CARD_UAC2=${CARD_UAC2:-2}
CARD_LINE=${CARD_LINE:-0}
DUR=${DUR:-5}
ADB_SERIAL=${ADB_SERIAL:-ZY32G798NP}
CHROOT=${CHROOT:-/workspace/sistema}

export PATH=/workspace/Android/tools/bin:$PATH

# Enciende UAC2 desde el puente si adb está disponible.
if command -v adb >/dev/null 2>&1; then
    adb -s "$ADB_SERIAL" shell \
        'am broadcast -n com.elyvaen.uac2/.Uac2Receiver \
         -a com.elyvaen.uac2.action.SET --ez enabled true' \
        >/dev/null 2>&1 || true
    sleep 2
fi

chroot "$CHROOT" /bin/bash -c "
set -eu
CARD_UAC2=$CARD_UAC2
CARD_LINE=$CARD_LINE
DUR=$DUR
echo '=== tarjetas ==='
cat /proc/asound/cards
echo '=== controles de captura (card '\$CARD_LINE') ==='
amixer -c \$CARD_LINE scontrols 2>&1 | head -30 || true
echo '=== genera tono 1 kHz ==='
sox -n -r 48000 -c 2 -b 16 /tmp/uac2_tone.wav synth \$DUR sine 1000
echo '=== graba Line Input y reproduce por UAC2 ==='
arecord -D plughw:\$CARD_LINE,0 -f S16_LE -r 48000 -c 2 -d \$DUR /tmp/uac2_line.wav &
AREC=\$!
sleep 0.5
aplay -D plughw:\$CARD_UAC2,0 /tmp/uac2_tone.wav
wait \$AREC
echo '=== estadisticas ==='
sox /tmp/uac2_line.wav -n stat
sox /tmp/uac2_line.wav -n spectrogram -o /tmp/uac2_line.png
ls -lh /tmp/uac2_line.wav /tmp/uac2_line.png
"

echo "WAV: $CHROOT/tmp/uac2_line.wav"
echo "PNG: $CHROOT/tmp/uac2_line.png"
