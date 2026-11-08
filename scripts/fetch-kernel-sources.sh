#!/bin/sh
set -eu

TAG=${TAG:-MMI-S2RYA32.58-13-12-5-9}
DEST=${1:-$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)/kernel-sources}
REPOS=${REPOS:-"
kernel-msm
kernel-msm-5.4-techpack-display
kernel-msm-5.4-techpack-audio
kernel-msm-5.4-techpack-camera
kernel-msm-5.4-techpack-video
kernel-msm-techpack-dataipa
kernel-devicetree
kernel-display-devicetree
kernel-camera-devicetree
motorola-kernel-modules
"}

command -v wget >/dev/null 2>&1 || {
    echo "Falta wget" >&2
    exit 1
}

mkdir -p "$DEST"
cd "$DEST"

for repo in $REPOS; do
    file="$repo-$TAG.tar.gz"
    url="https://github.com/MotorolaMobilityLLC/$repo/archive/refs/tags/$TAG.tar.gz"
    if [ -s "$file" ]; then
        echo "Ya existe: $file"
        continue
    fi
    echo "Descargando $repo ($TAG)"
    wget -c --tries=3 --timeout=60 -O "$file" "$url"
done

sha256sum ./*.tar.gz > SHA256SUMS
echo "Listo: $DEST"
