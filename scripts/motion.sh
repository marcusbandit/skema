#!/usr/bin/env bash
# Record a gesture and break it into frames, so animation can be judged instead of guessed.
#
#   scripts/motion.sh <label> <seconds> <<'GESTURE'
#   input swipe 540 1800 540 900 300
#   GESTURE
#
# A still frame cannot show whether a sheet snapped, drifted, or vanished. This
# records the screen while the piped commands run, then pulls evenly spaced
# frames out of the video so the motion is visible as a strip.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ADB="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}/platform-tools/adb"
label="${1:?usage: scripts/motion.sh <label> <seconds>}"
secs="${2:-6}"
out="$here/../captures/motion-$label"
mkdir -p "$out"; rm -f "$out"/*.png "$out"/clip.mp4

"$ADB" shell screenrecord --time-limit "$secs" --bit-rate 8000000 /sdcard/motion.mp4 &
rec=$!
sleep 1.2                      # let the recorder actually start before gesturing
while IFS= read -r line; do
  [ -z "$line" ] && continue
  if [[ "$line" == sleep* ]]; then eval "$line"; else "$ADB" shell $line; fi
done
wait $rec 2>/dev/null || true
"$ADB" pull /sdcard/motion.mp4 "$out/clip.mp4" >/dev/null
"$ADB" shell rm /sdcard/motion.mp4

# Frames at 12 per second: fast enough to catch a 200ms animation, few enough to look at.
ffmpeg -loglevel error -i "$out/clip.mp4" -vf "fps=12,scale=360:-1" "$out/f%03d.png"
echo "$out: $(ls "$out"/f*.png | wc -l) frames"
