#!/usr/bin/env bash
# Capture the app in both postures, so a fold regression is visible rather than assumed.
#
#   scripts/postures.sh <label>
#
# Writes <label>-folded.png and <label>-unfolded.png into captures/.
# Folding is a real configuration change, so this is the only honest way to
# check that the unfolded layout is a layout and not a stretched phone.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
label="${1:?usage: scripts/postures.sh <label>}"
out="$here/../captures"
mkdir -p "$out"

for posture in unfolded folded; do
  if [ "$posture" = folded ]; then "$here/fold.sh" fold >/dev/null; else "$here/fold.sh" unfold >/dev/null; fi
  # The window animates through the resize; sampling too early catches a
  # half laid out frame and reads as a bug that is not there.
  sleep 4
  "$here/shot.sh" "$out/${label}-${posture}.png" >/dev/null
  printf '%-9s %s\n' "$posture" "$(cd "$out" && identify -format '%wx%h' "${label}-${posture}.png" 2>/dev/null || echo captured)"
done
"$here/fold.sh" unfold >/dev/null
