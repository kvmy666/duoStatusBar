#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if command -v rive >/dev/null 2>&1; then
    RIVE="$(command -v rive)"
elif [ -x "$HOME/.rive/bin/rive" ]; then
    RIVE="$HOME/.rive/bin/rive"
else
    echo "error: the Rive CLI ('rive') is required to rebuild and verify both scenes" >&2
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
echo "using $VERSION_LINE (parsed $RIVE_VERSION)"

for scene in rive/duo/scene.rml rive/duo/scene_old.rml; do
    if [ ! -f "$scene" ]; then
        echo "error: missing Rive scene: $scene" >&2
        exit 1
    fi
done

WORK="$(mktemp -d "${TMPDIR:-/tmp}/duo-rive-both.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

cp -a rive/duo "$WORK/duo"
rm -rf "$WORK/duo/build"

hash_file() {
    sha256sum "$1" | cut -d' ' -f1
}

build_scene() {
    local label="$1"
    ( cd "$WORK/duo" && "$RIVE" . --once )
    if [ ! -f "$WORK/duo/build/duo.riv" ]; then
        echo "error: $label build produced no rive/duo/build/duo.riv" >&2
        exit 1
    fi
    printf '%s\n' "$WORK/duo/build/duo.riv"
}

NEW_RIV="$(build_scene scene.rml)"
NEW_HASH="$(hash_file "$NEW_RIV")"
cp "$NEW_RIV" "$WORK/new-duo.riv"

cd "$WORK/duo"
mv scene.rml "$WORK/scene.rml.saved"
cp scene_old.rml scene.rml
cd "$ROOT"
OLD_RIV="$(build_scene scene_old.rml)"
OLD_HASH="$(hash_file "$OLD_RIV")"

SHIPPED_NEW="$(hash_file app/src/main/res/raw/duo.riv)"
SHIPPED_OLD="$(hash_file app/src/main/res/raw/duo_old.riv)"

echo "scene.rml     fresh $NEW_HASH"
echo "duo.riv       shipped $SHIPPED_NEW"
echo "scene_old.rml fresh $OLD_HASH"
echo "duo_old.riv   shipped $SHIPPED_OLD"

status=0
if [ "$NEW_HASH" != "$SHIPPED_NEW" ]; then
    echo "error: scene.rml does not reproduce app/src/main/res/raw/duo.riv" >&2
    status=1
fi
if [ "$OLD_HASH" != "$SHIPPED_OLD" ]; then
    echo "error: scene_old.rml does not reproduce app/src/main/res/raw/duo_old.riv" >&2
    status=1
fi
if [ "$NEW_HASH" = "$OLD_HASH" ]; then
    echo "error: both scenes produced the same .riv hash; the scene swap did not take effect" >&2
    status=1
fi

if [ "$status" -ne 0 ]; then
    exit "$status"
fi

echo "both Rive scenes reproduce their shipped .riv files"
