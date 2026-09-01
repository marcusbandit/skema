#!/usr/bin/env bash
# Run Gradle inside a hard memory cap so a build can never take the desktop down.
#
#   scripts/build.sh :androidApp:assembleDebug
#   scripts/build.sh :shared:jvmTest              (any gradle args pass straight through)
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk}"

MIN_FREE_MB=5000
avail=$(awk '/MemAvailable/ {print int($2/1024)}' /proc/meminfo)
if (( avail < MIN_FREE_MB )); then
  echo "build.sh: only ${avail} MB available (< ${MIN_FREE_MB} MB). Close something first." >&2
  exit 1
fi

# Why 9000M and not the 3500M a single-module Android build gets: gradle.properties asks for
# -Xmx4g plus a 1 GB metaspace, so the Gradle daemon alone can commit 5 GB before its own
# native overhead, and org.gradle.parallel=true spreads work across shared, androidApp and
# desktopApp at once. Kotlin then compiles in a second JVM that inherits the same -Xmx, and
# it is spawned as a child so it lands in this cgroup too. 9000M is what those two can reach
# together; a 3500M cap would OOM-kill this build mid-compile, which reads as a mystery
# Gradle crash rather than as the cap doing its job.
#
# The Gradle daemon stays on, deliberately. A --no-daemon build re-pays JVM start, Kotlin and
# Compose plugin loading, and a cold configuration cache on every run, which on a Multiplatform
# Compose build is tens of seconds each time. The objection to the daemon is that it outlives
# the scope, and it does: it keeps running inside whichever scope first started it. That is the
# harmless direction, because the cgroup keeps holding it, so the daemon stays capped and
# swapless for its whole life rather than escaping into unbounded memory. The idle timeout
# below stops it squatting overnight, and ./gradlew --stop reclaims it now.
exec systemd-run --user --scope -q \
  -p MemoryMax=9000M -p MemorySwapMax=0 -p CPUQuota=800% \
  ./gradlew --console=plain -Dorg.gradle.daemon.idletimeout=1800000 "$@"
