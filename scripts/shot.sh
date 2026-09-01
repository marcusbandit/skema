#!/usr/bin/env bash
# Screenshot the running device over adb and write it to a PNG.
#
#   scripts/shot.sh                     captures/shot-YYYYmmdd-HHMMSS.png
#   scripts/shot.sh some/where/ui.png   write to a path you choose
#   scripts/shot.sh --size [path]       also report the display geometry
#
# screencap reads the guest's own framebuffer, so the UI can be checked with no emulator
# window on any workspace and no compositor screen grab involved. The pixels are the
# device's real pixels, not a scaled capture of a window someone had to look at.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

SDK="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
# Two adb builds live on this box and they fight over the server; pin the SDK one.
ADB="$SDK/platform-tools/adb"
[ -x "$ADB" ] || ADB=adb

show_size=0
if [ "${1:-}" = "--size" ]; then show_size=1; shift; fi
# captures/ is already in .gitignore, so screenshots never end up in a commit by accident.
out="${1:-$ROOT/captures/shot-$(date +%Y%m%d-%H%M%S).png}"

if ! "$ADB" devices | sed -n '2,$p' | grep -q '[[:space:]]device$'; then
  echo "shot.sh: no device attached. Start one with:  scripts/emulator.sh" >&2
  exit 1
fi

# The foldable is two physical displays, the 2208 wide inner one and the 1080 wide outer one,
# and only the posture's own display is powered. A bare screencap does two bad things here:
# it prints a "Multiple displays were found" warning onto stdout, which lands in front of the
# PNG bytes and corrupts the file, and it falls back to display 0, which is the screen that is
# switched OFF whenever the device is folded. Ask the display service which viewport is
# actually active and name that one.
display_id="${SKEMA_DISPLAY_ID:-$(
  "$ADB" shell dumpsys display 2>/dev/null | tr -d '\r' \
    | grep -o "DisplayViewport{[^}]*}" \
    | grep "isActive=true" \
    | sed -n "s/.*uniqueId='local:\([0-9]*\)'.*/\1/p" \
    | head -1 || true
)}"

mkdir -p "$(dirname "$out")"
if [ -n "$display_id" ]; then
  "$ADB" exec-out screencap -p -d "$display_id" > "$out"
else
  "$ADB" exec-out screencap -p > "$out"
fi

# screencap exits 0 and hands back an empty file when the surface is not up yet, and any
# warning it prints ends up where the PNG header should be. Both turn into a confusing
# "corrupt image" hours later, so fail here instead.
magic=$(head -c 8 "$out" | od -An -tx1 | tr -d ' \n')
if [ "$magic" != "89504e470d0a1a0a" ]; then
  echo "shot.sh: the device did not return a PNG. It said:" >&2
  head -c 200 "$out" | tr -d '\0' >&2; echo >&2
  rm -f "$out"
  exit 1
fi

echo "$out"

if [ "$show_size" = 1 ]; then
  # Geometry is how you tell folded from unfolded without looking at anything: the outer
  # screen is narrow, the inner one is wide.
  "$ADB" shell wm size | tr -d '\r'
  "$ADB" shell wm density | tr -d '\r'
fi
