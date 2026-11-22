#!/usr/bin/env bash
set -euo pipefail

# Offline release gate: verify all Rive scenes (Duo new, Duo old, iOS) with the
# installed Rive CLI and compare the committed .riv files against pinned sha256 hashes. This gate does
# not rebuild the .riv files; it verifies the scenes and checks the binaries.
#
# The committed .riv files were produced offline by Rive 1.8.0 `--once` from
# these scenes and are verified with Rive 1.9.2.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SHIPPED_NEW_HASH="4c59e9dc0860d9d6a117889499ccb904bc6138078efe709a12d8d539aa55b817"
SHIPPED_OLD_HASH="464586cad821ce8d8a801822e45adc4f77d7c68169ffecd09c69bcaf01176cf0"
SHIPPED_IOS_HASH="db457e8d82f3e5dedeb3ae7e1cbee7e5df6271a6513b474d690246ae4cf25d49"
SHIPPED_NEW="app/src/main/res/raw/duo.riv"
SHIPPED_OLD="app/src/main/res/raw/duo_old.riv"
SHIPPED_IOS="app/src/main/res/raw/ios.riv"
SCENE="rive/duo/scene.rml"
SCENE_OLD="rive/duo/scene_old.rml"
SCENE_IOS="rive/ios/scene.rml"

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

for scene in "$SCENE" "$SCENE_OLD" "$SCENE_IOS"; do
    if [ ! -f "$scene" ]; then
        echo "error: missing Rive scene: $scene" >&2
        exit 1
    fi
done
for riv in "$SHIPPED_NEW" "$SHIPPED_OLD" "$SHIPPED_IOS"; do
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
    local dir="$1"
    local label="$2"
    echo "verifying $label ..."
    ( cd "$dir" && "$RIVE" . --verify )
    echo "verified $label"
}

verify_scene rive/duo "$SCENE"
verify_scene rive/ios "$SCENE_IOS"

cp -p "$SCENE" "$SCENE_BACKUP"
cp -p "$SCENE_OLD" "$SCENE"
verify_scene rive/duo "$SCENE_OLD (swapped in as scene.rml)"
cp -p "$SCENE_BACKUP" "$SCENE"
rm -f "$SCENE_BACKUP"

hash_file() {
    sha256sum "$1" | cut -d' ' -f1
}

NEW_HASH="$(hash_file "$SHIPPED_NEW")"
OLD_HASH="$(hash_file "$SHIPPED_OLD")"
IOS_HASH="$(hash_file "$SHIPPED_IOS")"

echo "$SHIPPED_NEW  $NEW_HASH"
echo "$SHIPPED_OLD  $OLD_HASH"
echo "$SHIPPED_IOS  $IOS_HASH"

status=0
if [ "$NEW_HASH" != "$SHIPPED_NEW_HASH" ]; then
    echo "error: $SHIPPED_NEW hash $NEW_HASH != expected $SHIPPED_NEW_HASH" >&2
    status=1
fi
if [ "$OLD_HASH" != "$SHIPPED_OLD_HASH" ]; then
    echo "error: $SHIPPED_OLD hash $OLD_HASH != expected $SHIPPED_OLD_HASH" >&2
    status=1
fi
if [ "$IOS_HASH" != "$SHIPPED_IOS_HASH" ]; then
    echo "error: $SHIPPED_IOS hash $IOS_HASH != expected $SHIPPED_IOS_HASH" >&2
    status=1
fi
if [ "$status" -ne 0 ]; then
    exit "$status"
fi

echo "all Rive scenes verify offline and match the shipped .riv hashes"
