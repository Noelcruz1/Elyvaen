#!/bin/sh
set -eu

# Build the Elyvaen Booming fork (com.elyvaen.booming) inside the
# persistent Debian chroot and install the arm64 APK into the ROM payload.
#
# The chroot keeps Gradle caches and app/build between runs; only the
# source tree is refreshed.

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
SRC=${SRC:-/workspace/Android/ElyvaenMusic}
CHROOT=${CHROOT:-/workspace/sistema}
DST=${DST:-$CHROOT/tmp/elyvaen-music}
PAYLOAD=${PAYLOAD:-$ROOT/payload/product/priv-app/ElyvaenBooming}

[ -d "$SRC" ] || { echo "no existe $SRC" >&2; exit 1; }
[ -d "$CHROOT" ] || { echo "no existe $CHROOT" >&2; exit 1; }

mkdir -p "$DST" "$PAYLOAD"
tar -C "$SRC" \
    --exclude=./app/build \
    --exclude=./build \
    --exclude=./.gradle \
    --exclude=./.kotlin \
    --exclude=./local.properties \
    -cf - . | tar -C "$DST" -xf -
printf 'sdk.dir=/opt/android-sdk\n' > "$DST/local.properties"

chroot "$CHROOT" /bin/bash -c '
    set -eu
    cd /tmp/elyvaen-music
    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
    GRADLE_USER_HOME=/root/.gradle \
        ./gradlew --no-daemon :app:assembleFdroidRelease
'

APK=$(ls "$DST"/app/build/outputs/apk/fdroid/release/*arm64-v8a.apk | head -1)
[ -n "$APK" ] || { echo "no se genero el APK arm64" >&2; exit 1; }
cp "$APK" "$PAYLOAD/ElyvaenBooming.apk"
cp "$APK" "$ROOT/ElyvaenBooming-latest.apk"

echo "APK: $PAYLOAD/ElyvaenBooming.apk"
sha256sum "$PAYLOAD/ElyvaenBooming.apk"
