#!/usr/bin/env bash
# Drive the hinge on the foldable AVD.
#
#   scripts/fold.sh fold      close it: the narrow outer screen
#   scripts/fold.sh unfold    open it: the wide inner screen
#   scripts/fold.sh state     report the current posture and geometry
#
# The device is skema_fold, a Pixel Fold profile. Folded is the 1080 wide outer screen,
# unfolded is the 2208 wide inner one, and the app has to survive the switch at runtime
# because a real user does it mid-session. Layout checked in one posture is half checked.
set -euo pipefail

SDK="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
# Two adb builds live on this box and they fight over the server; pin the SDK one.
ADB="$SDK/platform-tools/adb"
[ -x "$ADB" ] || ADB=adb
AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.config/.android/avd}"

cmd="${1:-state}"
case "$cmd" in fold|unfold|state) ;; *)
  echo "fold.sh: expected fold, unfold or state; got '$cmd'." >&2; exit 1 ;;
esac

if ! "$ADB" devices | sed -n '2,$p' | grep -q '[[:space:]]device$'; then
  echo "fold.sh: no device attached. Start one with:  scripts/emulator.sh" >&2
  exit 1
fi

geometry() { "$ADB" shell wm size 2>/dev/null | tr -d '\r' || true; }

posture() {
  # Android reports CLOSED / HALF_OPENED / OPENED here. The emulator console has no
  # equivalent query, so ask the guest rather than guessing from the last command sent.
  "$ADB" shell cmd device_state state 2>/dev/null \
    | tr -d '\r' | sed -n "s/.*name='\([A-Z_]*\)'.*/\1/p" | head -1
}

if [ "$cmd" = state ]; then
  echo "posture:  $(posture)"
  echo "$(geometry)"
  "$ADB" shell wm density | tr -d '\r'
  exit 0
fi

# adb emu fold on a device with no hinge answers "KO" and explains nothing, so check the
# AVD's own config first and say which key is missing.
avd="$("$ADB" emu avd name 2>/dev/null | head -1 | tr -d '\r' || true)"
if [ -z "$avd" ]; then
  echo "fold.sh: the attached device is not an emulator, so it has no hinge to drive." >&2
  exit 1
fi
cfg="$AVD_HOME/$avd.avd/config.ini"
if [ ! -f "$cfg" ]; then
  echo "fold.sh: no config for AVD '$avd' at $cfg." >&2
  exit 1
fi
if ! grep -qE '^hw\.sensor\.hinge[[:space:]]*=[[:space:]]*yes' "$cfg"; then
  echo "fold.sh: AVD '$avd' has no hinge (hw.sensor.hinge is not yes in $cfg)." >&2
  echo "fold.sh: use a foldable profile, for example skema_fold." >&2
  exit 1
fi

before="$(geometry)"
"$ADB" emu "$cmd"

# The posture change lands asynchronously: the console answers OK immediately and the guest
# reconfigures its display a moment later, so poll rather than reporting a stale size.
for _ in $(seq 1 20); do
  now="$(geometry)"
  if [ -n "$now" ] && [ "$now" != "$before" ]; then break; fi
  sleep 0.5
done

echo "posture:  $(posture)"
echo "$(geometry)"
if [ "$(geometry)" = "$before" ]; then
  echo "fold.sh: geometry did not change; the device may already have been ${cmd}ed." >&2
fi
