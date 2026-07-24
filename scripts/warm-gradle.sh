#!/usr/bin/env bash
# warm-gradle.sh — Gradle warm-up for fresh session containers (D-173).
#
# A fresh container has an empty dependency cache, so the session's FIRST real ladder run pays
# the whole download+compile+test cost serially — right when the agent wants a verdict. The
# session-start hook launches this script detached (remote containers only), so that cost
# overlaps the minutes the session spends reading STATE/RUNBOOK instead. It runs the same five
# rungs as scripts/ladder.sh, so a later ladder run finds everything cached/up-to-date.
#
# Safe by construction: Gradle's own inter-process locking serializes this against any build
# the session starts meanwhile (worst case equals today's cold cost); a failure here is
# harmless noise — the real ladder run is the reporting authority. Opt out with
# AAB_SKIP_WARMUP=1. Log: ~/.gradle-warmup.log.
set -eu

cd "$(dirname "$0")/.."

if [ "${AAB_SKIP_WARMUP:-}" = "1" ]; then
  exit 0
fi

LOG="${HOME}/.gradle-warmup.log"

# If any Gradle process is already alive, skip: a live daemon means a build already ran in
# this container (cache is warm), and a running wrapper/build would only be queued behind.
if pgrep -f 'GradleDaemon|GradleWrapperMain' >/dev/null 2>&1; then
  echo "warm-gradle $(date -u +%FT%TZ): Gradle already running/warm in this container — skipping." >> "$LOG"
  exit 0
fi

{
  echo "=== warm-gradle start $(date -u +%FT%TZ) ==="
  if nice -n 19 ./gradlew --quiet \
      :domain:test :platform:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug; then
    echo "=== warm-gradle DONE $(date -u +%FT%TZ) ==="
  else
    echo "=== warm-gradle FAILED $(date -u +%FT%TZ) (harmless: scripts/ladder.sh is the reporting authority) ==="
  fi
} >> "$LOG" 2>&1
