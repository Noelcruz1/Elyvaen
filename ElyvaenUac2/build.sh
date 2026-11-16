#!/bin/sh
set -eu

# Build the tiny Elyvaen UAC2 property bridge.
#
# The bridge is a preinstalled priv-app that runs in the system_app SELinux
# domain (see plat_seapp_contexts) and exposes a ContentProvider so the
# music app (priv_app) can write persist.elyvaen.* / sys.elyvaen.* properties.
#
# The build runs inside the persistent Debian chroot at /workspace/sistema
# because the Android SDK and JDK live there.

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CHROOT=${CHROOT:-/workspace/sistema}
SDK=${SDK:-/opt/android-sdk}
BT="$SDK/build-tools/36.0.0"
AJ="$SDK/platforms/android-37.0/android.jar"
KEYSTORE=${KEYSTORE:-/root/.android/debug.keystore}
KS_ALIAS=${KS_ALIAS:-androiddebugkey}
KS_PASS=${KS_PASS:-android}
KEY_PASS=${KEY_PASS:-android}

if [ "${ELYVAEN_BRIDGE_IN_CHROOT:-0}" != "1" ]; then
    [ -d "$CHROOT" ] || { echo "no existe $CHROOT" >&2; exit 1; }
    rm -rf "$CHROOT/tmp/elyvaen-uac2"
    mkdir -p "$CHROOT/tmp/elyvaen-uac2"
    tar -C "$ROOT" --exclude=build --exclude='*.apk' -cf - . \
        | tar -C "$CHROOT/tmp/elyvaen-uac2" -xf -
    chroot "$CHROOT" /usr/bin/env \
        ELYVAEN_BRIDGE_IN_CHROOT=1 \
        SDK="$SDK" \
        KEYSTORE="$KEYSTORE" \
        KS_ALIAS="$KS_ALIAS" \
        KS_PASS="$KS_PASS" \
        KEY_PASS="$KEY_PASS" \
        /bin/bash /tmp/elyvaen-uac2/build.sh
    cp "$CHROOT/tmp/elyvaen-uac2/ElyvaenUac2.apk" "$ROOT/ElyvaenUac2.apk"
    echo "APK: $ROOT/ElyvaenUac2.apk"
    PAYLOAD=${PAYLOAD:-$ROOT/../payload/product/priv-app/ElyvaenUac2}
    mkdir -p "$PAYLOAD"
    cp "$ROOT/ElyvaenUac2.apk" "$PAYLOAD/ElyvaenUac2.apk"
    echo "Payload: $PAYLOAD/ElyvaenUac2.apk"
    exit 0
fi

cd /tmp/elyvaen-uac2
rm -rf build
mkdir -p build/classes build/dex

"$BT/aapt2" link -o build/unsigned.apk -I "$AJ" \
    --manifest AndroidManifest.xml \
    --min-sdk-version 31 --target-sdk-version 31 \
    --version-code 1 --version-name 1.0

javac -source 8 -target 8 -bootclasspath "$AJ" -d build/classes \
    $(find src -name '*.java')

"$BT/d8" --lib "$AJ" --min-api 31 --output build/dex \
    $(find build/classes -name '*.class')

cp build/unsigned.apk build/app.apk
python3 - <<'PY'
import zipfile
with zipfile.ZipFile("/tmp/elyvaen-uac2/build/app.apk", "a",
                     zipfile.ZIP_DEFLATED) as z:
    z.write("/tmp/elyvaen-uac2/build/dex/classes.dex", "classes.dex")
PY

"$BT/zipalign" -f 4 build/app.apk build/aligned.apk
"$BT/apksigner" sign \
    --ks "$KEYSTORE" \
    --ks-key-alias "$KS_ALIAS" \
    --ks-pass "pass:$KS_PASS" \
    --key-pass "pass:$KEY_PASS" \
    --out ElyvaenUac2.apk \
    build/aligned.apk

"$BT/aapt2" dump badging ElyvaenUac2.apk | head -5
ls -lh ElyvaenUac2.apk
