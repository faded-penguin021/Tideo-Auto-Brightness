#!/usr/bin/env bash
# ladder.sh — one-command local acceptance ladder + pre-flight guards.
#
# build.yml's "Acceptance ladder" step invokes THIS script directly (D-166), so the Gradle
# task set is shared by construction — there is no hand-maintained lockstep between the two.
# (CLAUDE.md "Build commands" / RUNBOOK "Acceptance ladder" still list the same five rungs
# individually; they remain the human-readable ground truth.)
#
# Usage:
#   scripts/ladder.sh                    guards, then the full 5-rung ladder
#   scripts/ladder.sh --guards-only      guards only (docs-only units; seconds, no Gradle)
#   scripts/ladder.sh <gradle args...>   extra args forwarded to Gradle (e.g. --no-daemon)
#
# Guards (fail fast, before any build):
#   1. STATE.md length rule (mirrors STATE.md's own preamble): warn over the 12 KB
#      steady-state target, FAIL over the 16 KB hard cap. 1b: required sections present
#      (over-compression tripwire). 1c: deviations-ledger rollover reminder (D-153).
#      (LADDER_STATE_FILE / LADDER_LEDGER_FILE override the paths — used only by
#      scripts/test-ladder-guards.sh, the guards' own test suite.)
#   5. D-citation integrity (D-173): every D-/DA-/DB-NNN cited in app/ domain/ platform/
#      .github/ sources must resolve to a row in its ledger file; ledger row numbers must be
#      unique. 6. F-Droid changelog cap (D-173): the CURRENT versionCode's
#      fastlane changelog must be <= 500 bytes (RUNBOOK §6; F-Droid flags longer whatsNew).
#   2. D-115 skip-ci token scan over unmerged commit messages (origin/main..HEAD) — the
#      same fixed-string token set release-preflight.yml enforces at PR time. Catching a
#      token BEFORE push matters here: force-push is forbidden (CLAUDE.md git rules), so a
#      poisoned pushed message stays on the branch until a squash-merge folds it into the
#      squash commit on main, where GitHub silently skips ALL workflows (the v1.2.0
#      incident). This file's own token list is inert: GitHub and the PR scan read commit
#      messages/titles, never file contents.
#   3/4. Local human-in-the-loop advisories (WARN-only; skipped under GITHUB_ACTIONS so they
#      never add CI noise, D-166): 3 = checkpoint tripwire (code/config changed vs main but
#      STATE.md carries no matching entry — RUNBOOK Session discipline 3); 4 = stale-branch
#      tripwire (HEAD behind origin/main invites a squash-merge conflict the green ladder
#      can't see).
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
if [ "$state_bytes" -gt 16384 ]; then
  fail "$STATE_FILE is ${state_bytes} B — over the 16 KB hard cap; compress it before committing (see its length-guard preamble)"
elif [ "$state_bytes" -gt 12288 ]; then
  echo "LADDER WARN: $STATE_FILE is ${state_bytes} B (steady-state target is <= 12 KB — compress soon)"
else
  echo "LADDER: STATE.md size OK (${state_bytes} B)"
fi

# --- guard 1b: STATE.md required structure (over-compression tripwire) ---
# The length-guard preamble says Project + Current state must survive any compression; Decided
# non-items and the Changelog are the other two load-bearing sections. Losing one = data loss.
for h in '## Project' '## Current state' '## Decided non-items' '## Changelog'; do
  grep -qF "$h" "$STATE_FILE" \
    || fail "$STATE_FILE is missing required section \"$h\" — over-compressed? Restore it (see the length-guard preamble)"
done
# Owner queue (D-167) is WARN-level: losing it is data loss for the OWNER's pending
# actions/questions/findings, but shouldn't hard-block an unrelated build — restore it from
# git history, never let the warning stand.
grep -qF '## Owner queue' "$STATE_FILE" \
  || echo "LADDER WARN: $STATE_FILE is missing '## Owner queue' (D-167) — a compression pass ate the owner's pending actions/questions/findings; restore it from git history."
echo "LADDER: STATE.md required sections present"

# --- guard 1c: deviations-ledger rollover reminder (D-153/D-171: 184 rows per file, then _A/_B…) ---
# (LADDER_LEDGER_FILE overrides the path — used only by the guard's own tests.)
LEDGER_FILE="${LADDER_LEDGER_FILE:-}"
if [ -z "$LEDGER_FILE" ]; then
  LEDGER_FILE=docs/rebuild/DEVIATIONS_LEDGER.md
  for f in docs/rebuild/DEVIATIONS_LEDGER_B.md docs/rebuild/DEVIATIONS_LEDGER_A.md; do
    if [ -f "$f" ]; then LEDGER_FILE="$f"; break; fi
  done
fi
if [ -f "$LEDGER_FILE" ]; then
  ledger_rows=$(grep -cE '^- (\*\*)?D[AB]?-[0-9]' "$LEDGER_FILE" || true)
  if [ "$ledger_rows" -gt 184 ]; then
    fail "live ledger $LEDGER_FILE has ${ledger_rows} rows (> 184) — roll over to the next file (D-153/D-171)"
  elif [ "$ledger_rows" -ge 174 ]; then
    echo "LADDER WARN: live ledger $LEDGER_FILE at ${ledger_rows}/184 rows — rollover soon (D-153/D-171)"
  else
    echo "LADDER: live ledger OK (${ledger_rows}/184 rows in $LEDGER_FILE)"
  fi
else
  fail "live ledger $LEDGER_FILE not found"
fi

# --- guard 5: D-citation integrity (D-173; numbered after the pre-existing guards 1-4,
# whose numbers are cited by immutable ledger rows and never change) ---
# Code and workflows cite deviations as bare D-NN, and the ledger preamble's contract is that
# every citation "must always resolve" to a row. Machine-check the checkable half over the
# artifacts the work produces anyway: every D-/DA-/DB-NNN in app/ domain/ platform/ .github/
# sources must match a row in its ledger file (prefix names the file, D-153), and no ledger
# file may carry a duplicate row number. Catches citation typos, a code comment merged before
# its ledger row was appended, and a mis-numbered append. scripts/ is deliberately NOT
# scanned (scripts/test-ladder-guards.sh synthesizes fixture ledgers/citations), nor is doc
# prose (it legitimately uses range/cap notation like "D-001…D-184" that names no real row).
cited=$(grep -rhoE '\bD[AB]?-[0-9]{3}\b' \
  --include='*.kt' --include='*.kts' --include='*.xml' --include='*.yml' --include='*.yaml' \
  app domain platform .github 2>/dev/null | sort -u || true)
for id in $cited; do
  case "$id" in
    DA-*) lf=docs/rebuild/DEVIATIONS_LEDGER_A.md ;;
    DB-*) lf=docs/rebuild/DEVIATIONS_LEDGER_B.md ;;
    *)    lf=docs/rebuild/DEVIATIONS_LEDGER.md ;;
  esac
  [ -f "$lf" ] || fail "citation $id cannot resolve: ledger file $lf not found (D-173)"
  grep -qE "^- (\*\*)?${id}\b" "$lf" \
    || fail "dangling deviation citation $id — no such row in $lf. Fix the typo or append the missing ledger row (D-173)."
done
for lf in docs/rebuild/DEVIATIONS_LEDGER.md docs/rebuild/DEVIATIONS_LEDGER_A.md docs/rebuild/DEVIATIONS_LEDGER_B.md; do
  [ -f "$lf" ] || continue
  dupes=$(grep -oE '^- (\*\*)?D[AB]?-[0-9]{3}' "$lf" | grep -oE 'D[AB]?-[0-9]{3}' | sort | uniq -d || true)
  [ -z "$dupes" ] || fail "duplicate deviation row number(s) in $lf: $(echo $dupes) — renumber the newest append (D-173)"
done
echo "LADDER: D-citations OK ($(echo "$cited" | grep -c . || true) distinct, all resolve; no duplicate ledger rows)"

# --- guard 6: F-Droid changelog cap (D-173) ---
# RUNBOOK §6: changelogs/<versionCode>.txt must stay under 500 characters (whole file, wc -c)
# or F-Droid's code-quality scan flags the whatsNew. Only the CURRENT versionCode's file is
# checked — it is the one the next tag ships; historical files (e.g. the pre-rule 9.txt,
# 1158 B) are shipped facts, not actionable. Existence is release-preflight.yml's job (it
# knows whether the PR ships app code); this guard only rejects an oversize file.
vc=$(grep -oE 'versionCode[[:space:]]*=[[:space:]]*[0-9]+' app/build.gradle.kts 2>/dev/null \
  | grep -oE '[0-9]+' | head -1 || true)
if [ -n "$vc" ] && [ -f "fastlane/metadata/android/en-US/changelogs/${vc}.txt" ]; then
  cl="fastlane/metadata/android/en-US/changelogs/${vc}.txt"
  cl_bytes=$(wc -c < "$cl" | tr -d '[:space:]')
  if [ "$cl_bytes" -gt 500 ]; then
    fail "$cl is ${cl_bytes} B — over the 500-char F-Droid whatsNew cap (RUNBOOK §6). Shorten it."
  fi
  echo "LADDER: F-Droid changelog OK ($cl, ${cl_bytes} B <= 500)"
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

  # --- guards 3 & 4: local human-in-the-loop advisories (WARN-only; D-166) ---
  # Skipped under GITHUB_ACTIONS: these target the interactive session workflow, not the CI
  # gate (CI keeps guards 1/1b/1c/2 above). Both only ever WARN, so they never fail a build.
  if [ "${GITHUB_ACTIONS:-}" != "true" ]; then
    # guard 3: checkpoint tripwire (RUNBOOK Session discipline 3). If any non-doc code/config
    # changed relative to main (committed + working tree) but STATE.md is not in that diff, the
    # checkpoint invariant's STATE Changelog line is probably still missing.
    # Residual (known): the diff base is origin/main, so this is per-BRANCH, not per-unit —
    # once any earlier unit on the branch touched STATE.md, a later unit that forgets its
    # Changelog line will NOT warn. Silence here is not confirmation; the invariant itself
    # (RUNBOOK Session discipline 3) still binds every unit.
    changed=$( { git diff --name-only origin/main..HEAD; git status --porcelain | sed 's/^...//'; } | sort -u )
    code_changed=$(printf '%s\n' "$changed" | grep -vE '^$|^docs/|(^|/)[^/]*\.md$' || true)
    if [ -n "$code_changed" ] && ! printf '%s\n' "$changed" | grep -qx 'docs/rebuild/STATE.md'; then
      echo "LADDER WARN: code/config changed but docs/rebuild/STATE.md is not in the diff — the checkpoint invariant wants a STATE Changelog line before commit (RUNBOOK Session discipline 3)."
    fi
    # guard 4: stale-branch tripwire. Behind origin/main invites a squash-merge conflict the
    # agent's own green ladder can't see. Advice must stay force-push-free: rebasing pushed
    # checkpoints would need a force-push, which the git rules forbid — merge instead (the
    # merge commit vanishes at squash-merge anyway).
    behind=$(git rev-list --count HEAD..origin/main 2>/dev/null || echo 0)
    if [ "$behind" -gt 0 ]; then
      echo "LADDER WARN: branch is ${behind} commit(s) behind origin/main — 'git merge origin/main' before push to avoid a squash-merge conflict (rebase ONLY if nothing is pushed yet; pushed checkpoints are immutable, never force-push)."
    fi
  fi
else
  echo "LADDER WARN: origin/main unavailable (offline?) — D-115 token scan + branch advisories skipped"
fi

if [ "$guards_only" -eq 1 ]; then
  echo "LADDER PASS (guards only)"
  exit 0
fi

# --- the five rungs, one Gradle invocation (same task set as build.yml) ---
start=$SECONDS
./gradlew :domain:test :platform:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug "$@"
echo "LADDER PASS (guards + 5 rungs) in $((SECONDS - start))s"
