#!/usr/bin/env bash
# Build the debug APK, install it on the running AVD, and launch it.
#
#   scripts/run.sh              build, install, launch
#   scripts/run.sh --no-build   install whatever APK is already on disk, then launch
#
# The emulator is headless, so this opens no window anywhere. Look at the result with
# scripts/shot.sh.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

SDK="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
# Two adb builds live on this box, the SDK's and /usr/bin/adb, and they fight over the
# server. Pin the SDK one so these scripts and the IDE talk to the same daemon.
ADB="$SDK/platform-tools/adb"
[ -x "$ADB" ] || ADB=adb

# From androidApp/build.gradle.kts (applicationId) and androidApp/src/main/AndroidManifest.xml
# (the activity's android:name, relative to the namespace).
PKG="com.marcusrosado.skemafordtu"
ACTIVITY="${PKG}/.MainActivity"
APK_DIR="$ROOT/androidApp/build/outputs/apk/debug"

build=1
for arg in "$@"; do
  case "$arg" in
    --no-build) build=0 ;;
    -h|--help)  sed -n '2,8p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "run.sh: unknown argument '$arg'. Only --no-build is understood." >&2; exit 1 ;;
  esac
done

# An "offline" or "unauthorized" line is not something we can install to, so match the exact
# device state rather than counting lines.
if ! "$ADB" devices | sed -n '2,$p' | grep -q '[[:space:]]device$'; then
  echo "run.sh: no device attached. Start one with:  scripts/emulator.sh" >&2
  exit 1
fi

# A half-booted device accepts the connection but the package manager is not up yet, so the
# install fails with an INSTALL_FAILED code or simply hangs, and nothing in the error says
# the device was just not ready.
boot_flag() { "$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true; }
if [ "$(boot_flag)" != "1" ]; then
  echo "run.sh: waiting for the device to finish booting..."
  booted=0
  for _ in $(seq 1 150); do
    if [ "$(boot_flag)" = "1" ]; then booted=1; break; fi
    sleep 2
  done
  if [ "$booted" != 1 ]; then
    echo "run.sh: device never reported sys.boot_completed=1 after 5 minutes." >&2
    exit 1
  fi
fi

if [ "$build" = 1 ]; then
  "$ROOT/scripts/build.sh" :androidApp:assembleDebug
fi

shopt -s nullglob
apks=("$APK_DIR"/*-debug.apk)
shopt -u nullglob
if [ "${#apks[@]}" -eq 0 ]; then
  echo "run.sh: no debug APK in $APK_DIR. Run without --no-build." >&2
  exit 1
fi
apk="${apks[0]}"

echo "run.sh: installing $(basename "$apk")"
"$ADB" install -r "$apk"

# -W waits until the activity is actually up, so a screenshot taken straight afterwards
# shows the app rather than the launcher it was still sitting on.
"$ADB" shell am start -W -n "$ACTIVITY"

echo
echo "run.sh: ${PKG} is running. Next:"
echo "  adb logcat --pid=\$(adb shell pidof -s ${PKG})   # follow just this app"
echo "  scripts/shot.sh                                  # see it, without opening a window"
