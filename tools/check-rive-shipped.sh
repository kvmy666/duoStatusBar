#!/usr/bin/env bash
set -euo pipefail

# Offline release gate: verify both Rive scenes and prove the shipped .riv files
# are the ones those scenes produce. Rive 1.9.1 has no --once (that required a
# signed-in session), so this gate only uses --verify and hashes the committed
# binaries.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SHIPPED_NEW_HASH="fa32b3f91ae1748bd8d56c6b79b7193e9ede114020d14922a3841060ae641567"
SHIPPED_OLD_HASH="436d23583822671bf1700ad61d0bdec85f936ac7410487d2d43b581097a5bf09"
SHIPPED_NEW="app/src/main/res/raw/duo.riv"
SHIPPED_OLD="app/src/main/res/raw/duo_old.riv"
SCENE="rive/duo/scene.rml"
SCENE_OLD="rive/duo/scene_old.rml"

if command -v rive >/dev/null 2>&1; then
    RIVE="$(command -v rive)"
elif [ -x "$HOME/.rive/bin/rive" ]; then
    RIVE="$HOME/.rive/bin/rive"
else
    echo "error: the Rive CLI ('rive') is required to verify both scenes" >&2
    echo "       install it or put it on PATH; this gate fails closed and never skips" >&2
    exit 1
fi

VERSION_LINE="$("$RIVE" --version 2>&1 | head -n 1)"
RIVE_VERSION="$(printf '%s\n' "$VERSION_LINE" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -n 1)"
if [ -z "$RIVE_VERSION" ]; then
    echo "error: could not parse Rive CLI version from: $VERSION_LINE" >&2
    exit 1
fi
RIVE_MAJOR="${RIVE_VERSION%%.*}"
if [ "$RIVE_MAJOR" != "1" ]; then
    echo "error: Rive CLI $RIVE_VERSION is not supported; need >=1.9.1 <2.0.0" >&2
    exit 1
fi
if ! printf '1.9.1\n%s\n' "$RIVE_VERSION" | sort -V -C; then
    echo "error: Rive CLI $RIVE_VERSION is too old; need >=1.9.1 <2.0.0" >&2
    exit 1
fi

for scene in "$SCENE" "$SCENE_OLD"; do
    if [ ! -f "$scene" ]; then
        echo "error: missing Rive scene: $scene" >&2
        exit 1
    fi
done
for riv in "$SHIPPED_NEW" "$SHIPPED_OLD"; do
    if [ ! -f "$riv" ]; then
        echo "error: missing shipped Rive file: $riv" >&2
        exit 1
    fi
done

echo "using $VERSION_LINE (parsed $RIVE_VERSION)"

WORK="$(mktemp -d "${TMPDIR:-/tmp}/duo-rive-verify.XXXXXX")"
SCENE_BACKUP="$WORK/scene.rml"

restore_scene() {
    if [ -f "$SCENE_BACKUP" ]; then
        cp -p "$SCENE_BACKUP" "$SCENE"
    fi
    rm -rf "$WORK"
}
trap restore_scene EXIT INT TERM

verify_scene() {
    local label="$1"
    echo "verifying $label ..."
    ( cd rive/duo && "$RIVE" . --verify )
    echo "verified $label"
}

verify_scene "$SCENE"

cp -p "$SCENE" "$SCENE_BACKUP"
cp -p "$SCENE_OLD" "$SCENE"
verify_scene "$SCENE_OLD (swapped in as scene.rml)"
cp -p "$SCENE_BACKUP" "$SCENE"
rm -f "$SCENE_BACKUP"

hash_file() {
    sha256sum "$1" | cut -d' ' -f1
}

NEW_HASH="$(hash_file "$SHIPPED_NEW")"
OLD_HASH="$(hash_file "$SHIPPED_OLD")"

echo "$SHIPPED_NEW  $NEW_HASH"
echo "$SHIPPED_OLD  $OLD_HASH"

status=0
if [ "$NEW_HASH" != "$SHIPPED_NEW_HASH" ]; then
    echo "error: $SHIPPED_NEW hash $NEW_HASH != expected $SHIPPED_NEW_HASH" >&2
    status=1
fi
if [ "$OLD_HASH" != "$SHIPPED_OLD_HASH" ]; then
    echo "error: $SHIPPED_OLD hash $OLD_HASH != expected $SHIPPED_OLD_HASH" >&2
    status=1
fi
if [ "$status" -ne 0 ]; then
    exit "$status"
fi

echo "both Rive scenes verify offline and match the shipped .riv hashes"
