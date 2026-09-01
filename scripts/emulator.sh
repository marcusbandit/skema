#!/usr/bin/env bash
# Start the foldable AVD this app is developed against.
#
#   scripts/emulator.sh              headless (default): no window on any workspace
#   scripts/emulator.sh --window     show the device window
#   SKEMA_AVD=other scripts/emulator.sh
#
# Headless is the default on purpose. The window is an X11/Wayland surface and
# the compositor decides which workspace it lands on; over adb nothing is drawn
# anywhere, and screencap gives exact device pixels rather than a scaled capture.
set -euo pipefail
SDK="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.config/.android/avd}"
export ANDROID_SDK_HOME="${ANDROID_SDK_HOME:-$HOME/.config/.android}"
AVD="${SKEMA_AVD:-skema_fold}"

MIN_FREE_MB=5000
avail=$(awk '/MemAvailable/ {print int($2/1024)}' /proc/meminfo)
(( avail >= MIN_FREE_MB )) || { echo "emulator.sh: only ${avail} MB free (< ${MIN_FREE_MB})." >&2; exit 1; }
if pgrep -f "qemu-system-x86_64 -avd ${AVD}( |$)" >/dev/null; then
  echo "emulator.sh: ${AVD} is already running." >&2; exit 1
fi

extra=(-no-window); gpu=swiftshader_indirect
if [[ "${1:-}" == "--window" ]]; then
  extra=(); shift
  # A window needs a real display; without one EGL init fails outright.
  [ -n "${WAYLAND_DISPLAY:-}${DISPLAY:-}" ] && gpu=host
fi
[ -n "${SKEMA_EMU_GPU:-}" ] && gpu="$SKEMA_EMU_GPU"

# Capped in its own cgroup with swap forbidden, so a runaway guest dies
# before the desktop does.
exec systemd-run --user --scope -q \
  -p MemoryMax=6500M -p MemorySwapMax=0 -p CPUQuota=600% \
  "$SDK/emulator/emulator" -avd "$AVD" -memory 3072 -no-audio -no-boot-anim \
    -gpu "$gpu" "${extra[@]}" "$@"
