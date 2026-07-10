#!/usr/bin/env bash
# ladder.sh — one-command local acceptance ladder + pre-flight guards.
#
# The Gradle task set MUST stay in lockstep with the "Test + lint + assemble" step in
# .github/workflows/build.yml — RUNBOOK "When CI fails" relies on the local ladder and CI
# running the SAME tasks. (CLAUDE.md "Build commands" / RUNBOOK "Acceptance ladder" list
# the same five rungs individually; they remain the ground truth.)
#
# Usage:
#   scripts/ladder.sh                    guards, then the full 5-rung ladder
#   scripts/ladder.sh --guards-only      guards only (docs-only units; seconds, no Gradle)
#   scripts/ladder.sh <gradle args...>   extra args forwarded to Gradle (e.g. --no-daemon)
#
# Guards (fail fast, before any build):
#   1. STATE.md length rule (mirrors STATE.md's own preamble): warn over the 12 KB
#      steady-state target, FAIL over the 32 KB hard cap.
#      (LADDER_STATE_FILE overrides the path — used only by the guard's own tests.)
#   2. D-115 skip-ci token scan over unmerged commit messages (origin/main..HEAD) — the
#      same fixed-string token set release-preflight.yml enforces at PR time. Catching a
#      token BEFORE push matters here: force-push is forbidden (CLAUDE.md git rules), so a
#      poisoned pushed message stays on the branch until a squash-merge folds it into the
#      squash commit on main, where GitHub silently skips ALL workflows (the v1.2.0
#      incident). This file's own token list is inert: GitHub and the PR scan read commit
#      messages/titles, never file contents.
set -euo pipefail

cd "$(dirname "$0")/.."

STATE_FILE="${LADDER_STATE_FILE:-docs/rebuild/STATE.md}"
guards_only=0
if [ "${1:-}" = "--guards-only" ]; then
  guards_only=1
  shift
fi

fail() { echo "LADDER FAIL: $1" >&2; exit 1; }

# --- guard 1: STATE.md length rule ---
[ -f "$STATE_FILE" ] || fail "$STATE_FILE not found (run from the repo, or fix LADDER_STATE_FILE)"
state_bytes=$(wc -c < "$STATE_FILE" | tr -d '[:space:]')
if [ "$state_bytes" -gt 32768 ]; then
  fail "$STATE_FILE is ${state_bytes} B — over the 32 KB hard cap; compress it before committing (see its length-guard preamble)"
elif [ "$state_bytes" -gt 12288 ]; then
  echo "LADDER WARN: $STATE_FILE is ${state_bytes} B (steady-state target is <= 12 KB — compress soon)"
else
  echo "LADDER: STATE.md size OK (${state_bytes} B)"
fi

# --- guard 2: D-115 skip-ci tokens in unmerged commit messages ---
if ! git rev-parse --verify -q origin/main >/dev/null; then
  git fetch origin main --quiet 2>/dev/null || true
fi
if git rev-parse --verify -q origin/main >/dev/null; then
  log_text=$(git log origin/main..HEAD --format='%s%n%b')
  TOKENS=('[skip ci]' '[ci skip]' '[no ci]' '[skip actions]' '[actions skip]' '***NO_CI***')
  for t in "${TOKENS[@]}"; do
    if printf '%s' "$log_text" | grep -Fq -- "$t"; then
      fail "forbidden CI-skip token \"$t\" in an unmerged commit message (D-115). Reword the commit (say 'skip-ci', never the literal) BEFORE pushing — force-push is not allowed."
    fi
  done
  echo "LADDER: no skip-ci tokens in origin/main..HEAD commit messages"
else
  echo "LADDER WARN: origin/main unavailable (offline?) — D-115 token scan skipped"
fi

if [ "$guards_only" -eq 1 ]; then
  echo "LADDER PASS (guards only)"
  exit 0
fi

# --- the five rungs, one Gradle invocation (same task set as build.yml) ---
start=$SECONDS
./gradlew :domain:test :platform:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug "$@"
echo "LADDER PASS (guards + 5 rungs) in $((SECONDS - start))s"
