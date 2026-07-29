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
#   1. STATE.md length rule (mirrors STATE.md's own preamble, DA-004 hysteresis): quiet to
#      14 KB, then warn to deep-compress to <= 9 KB; FAIL over the 16 KB hard cap.
#      1a: compression-landing rule (DA-014) — FAIL when a change trims STATE out of warn
#      territory (> 14 KB before) but lands in the 9–14 KB debounce band instead of on the
#      <= 9 KB floor (catches the micro-trim the stateless thresholds miss).
#      1b: required sections present
#      (over-compression tripwire). 1c: deviations-ledger rollover reminder (D-153).
#      (LADDER_STATE_FILE / LADDER_LEDGER_FILE override the paths — used only by
#      scripts/test-ladder-guards.sh, the guards' own test suite.)
#   5. D-citation integrity (D-173): every D-/DA-/DB-NNN cited in app/ domain/ platform/
#      .github/ sources must resolve to a row in its ledger file; ledger row numbers must be
#      unique; the rows' [cited] markers must match the citation set both ways (D-174).
#      6. F-Droid changelog cap (D-173, char count DA-019): the CURRENT versionCode's
#      fastlane changelog must be <= 500 CHARACTERS (codepoints, not bytes — RUNBOOK §6;
#      F-Droid flags a longer whatsNew by string length, so a multibyte note may exceed 500 B).
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
#      tripwire (HEAD behind origin/main CAN invite a squash-merge conflict — but under the
#      DA-002 branch-train model behind-main is usually structural, so it stays advisory;
#      DA-010: a merge-tree test-merge classifies which case applies, so the warning itself
#      says "do NOT merge" when origin/main brings no content this branch lacks).
#   7. Rule-review tripwire (DA-006; WARN-only, skipped under GITHUB_ACTIONS): uncommitted
#      changes touching harness-legislation files (constitution + its AGENTS.md pointer,
#      RUNBOOK, this script + its test suite, session bootstrap, adapter permission config)
#      print a reminder that the DA-005 rule-review protocol applies before commit.
#   8. redact.sh self-test (DA-007): the secret-redaction filter is a rail — a silent regex
#      regression must fail the ladder, not pass quietly.
#   11. Falsifiable doc-facts (DA-015): a prose claim with a shipped drift incident gets a
#      machine anchor — constant in the guard, lockstep with the cited doc sentence
#      (Shizuku runtime-site count, d66de4c). Incident-only admission bar.
#   9. Secret-shape tree scan (DA-008): FAIL if redacting any tracked/untracked text file
#      would change it — the scan IS the DA-007 filter, so the two cannot drift. Catches a
#      secret BEFORE commit (server push protection only fires at push, when the secret is
#      already in history and only the owner-executed rewrite path remains, DA-006).
#      Fixture tokens must be runtime-generated so the tree stays inert.
#   10. command-guard.sh self-test (DA-009): the pre-execution command rail (force-push /
#      push-to-main / env-dump blocks with instructive deny reasons) must not regress
#      silently — same rationale as guard 8.
#      10a. On the standard agent image, the sourceable container bootstrap contract verifies
#      JDK 21 selection, SDK/build-tools readiness, transactional failure, and PATH idempotence.
set -euo pipefail

cd "$(dirname "$0")/.."

STATE_FILE="${LADDER_STATE_FILE:-docs/rebuild/STATE.md}"
guards_only=0
if [ "${1:-}" = "--guards-only" ]; then
  guards_only=1
  shift
fi

fail() { echo "LADDER FAIL: $1" >&2; exit 1; }

# --- guard 1: STATE.md length rule (DA-004 hysteresis: grow freely to 14 KB; when the warn
# fires, compress DEEP to <= 9 KB in one pass — never trim to just under a threshold, that
# only re-arms the warn a session later; fail > 16 KB) ---
# The three thresholds live here as named constants because guard 1a below also depends on the
# floor and warn line — one source of truth, lockstep with the STATE.md length-guard preamble.
STATE_HARD_BYTES=16384    # hard cap: fail over
STATE_WARN_BYTES=14336    # warn line: compression pass due over
STATE_FLOOR_BYTES=9216    # deep-compression target (<= 9 KB); warn−floor IS the debounce band
[ -f "$STATE_FILE" ] || fail "$STATE_FILE not found (run from the repo, or fix LADDER_STATE_FILE)"
state_bytes=$(wc -c < "$STATE_FILE" | tr -d '[:space:]')
if [ "$state_bytes" -gt "$STATE_HARD_BYTES" ]; then
  fail "$STATE_FILE is ${state_bytes} B — over the 16 KB hard cap; compress DEEP to <= 9 KB before committing (DA-004; see its length-guard preamble)"
elif [ "$state_bytes" -gt "$STATE_WARN_BYTES" ]; then
  echo "LADDER WARN: $STATE_FILE is ${state_bytes} B (> 14 KB) — compression pass due: compress DEEP to <= 9 KB in ONE pass, not to just under a line (DA-004)"
else
  echo "LADDER: STATE.md size OK (${state_bytes} B / 14 KB warn line)"
fi

# --- guard 1a: STATE.md compression-landing rule (DA-004 debounce enforcement; DA-014) ---
# The stateless thresholds above cannot tell a genuine deep compression from a "micro-trim"
# that shrinks STATE to just under the warn line and re-arms the warn a session or two later —
# the Goodhart hole the owner hit before stating the rule by hand. This sub-check supplies the
# missing state: if the PREVIOUS committed STATE was over the warn line (a compression was
# owed) and this change brings it down but lands in the 9–14 KB debounce band instead of on
# the <= 9 KB floor, the compression pass didn't finish — FAIL. It judges the current size
# against the last COMMITTED size: normally HEAD (the trim is in the working tree, pre-commit),
# falling back to HEAD~1 when the working tree hasn't touched STATE (a trim that is already
# committed, e.g. a re-run in CI — build.yml checks out with full depth, so HEAD~1 is present).
# It fires ONLY on a shrink out of warn territory: normal growth and sub-warn edits never trip
# it, and an author who leaves STATE bloated is caught by guard 1's warn/fail, not here (the
# "one deep pass or nothing" philosophy — a partial trim into the band is the failure mode, a
# bloated file is already flagged). Residual: a micro-trim buried under later commits on the
# same branch won't re-fire in CI, but it DID fail the ladder at its own authoring-time run
# (the enforcement point that matters, RUNBOOK Session discipline).
if git rev-parse --git-dir >/dev/null 2>&1 && git cat-file -e "HEAD:$STATE_FILE" 2>/dev/null; then
  base_ref=HEAD
  # git diff --quiet HEAD is the precise "working tree unchanged vs HEAD" test (staged OR
  # unstaged); a byte-preserving edit would fool a size-equality check.
  if git diff --quiet HEAD -- "$STATE_FILE" && git cat-file -e "HEAD~1:$STATE_FILE" 2>/dev/null; then
    base_ref=HEAD~1
  fi
  prev_bytes=$(git show "$base_ref:$STATE_FILE" 2>/dev/null | wc -c | tr -d '[:space:]')
  if [ "$prev_bytes" -gt "$STATE_WARN_BYTES" ] && [ "$state_bytes" -le "$STATE_WARN_BYTES" ] \
       && [ "$state_bytes" -gt "$STATE_FLOOR_BYTES" ]; then
    fail "$STATE_FILE was ${prev_bytes} B (> 14 KB warn) and this change trims it only to ${state_bytes} B — still inside the 9-14 KB debounce band. A compression pass must land <= 9 KB in ONE pass, never just under the warn line (a micro-trim re-arms the warn a session later; DA-004)."
  elif [ "$prev_bytes" -gt "$STATE_WARN_BYTES" ] && [ "$state_bytes" -le "$STATE_FLOOR_BYTES" ]; then
    echo "LADDER: STATE.md deep-compressed ${prev_bytes} B -> ${state_bytes} B (landed <= 9 KB floor; DA-004)"
  fi
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

# --- guard 1c: deviations-ledger rollover (D-153 mechanism; DA-001: LINE-based cap from
# ledger A on — the base ledger closed at D-176). The final row of a file may FINISH past the
# cap, but no row may START past it: a row starting beyond the cap belonged in the next file.
# (LADDER_LEDGER_FILE overrides the path — used only by the guard's own tests.)
LEDGER_CAP_LINES=1000   # owner-tunable (DA-001); warn lead below mirrors the old 10-row lead
LEDGER_WARN_LINES=$((LEDGER_CAP_LINES - 100))
LEDGER_FILE="${LADDER_LEDGER_FILE:-}"
if [ -z "$LEDGER_FILE" ]; then
  LEDGER_FILE=docs/rebuild/DEVIATIONS_LEDGER.md
  for f in docs/rebuild/DEVIATIONS_LEDGER_B.md docs/rebuild/DEVIATIONS_LEDGER_A.md; do
    if [ -f "$f" ]; then LEDGER_FILE="$f"; break; fi
  done
fi
if [ -f "$LEDGER_FILE" ]; then
  ledger_lines=$(wc -l < "$LEDGER_FILE" | tr -d '[:space:]')
  last_row_start=$(grep -nE '^- (\*\*)?D[AB]?-[0-9]' "$LEDGER_FILE" | tail -1 | cut -d: -f1 || true)
  if [ -n "$last_row_start" ] && [ "$last_row_start" -gt "$LEDGER_CAP_LINES" ]; then
    fail "live ledger $LEDGER_FILE: last row starts at line ${last_row_start} (> ${LEDGER_CAP_LINES}) — that row belongs in the next ledger file; roll over (D-153/DA-001)"
  elif [ "$ledger_lines" -gt "$LEDGER_CAP_LINES" ]; then
    echo "LADDER WARN: live ledger $LEDGER_FILE at ${ledger_lines} lines (> ${LEDGER_CAP_LINES}) — the NEXT deviation opens the next ledger file (D-153/DA-001)"
  elif [ "$ledger_lines" -ge "$LEDGER_WARN_LINES" ]; then
    echo "LADDER WARN: live ledger $LEDGER_FILE at ${ledger_lines}/${LEDGER_CAP_LINES} lines — rollover soon (D-153/DA-001)"
  else
    echo "LADDER: live ledger OK (${ledger_lines}/${LEDGER_CAP_LINES} lines in $LEDGER_FILE)"
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

# [cited] marker sync (D-174): a row cited from the scan scope above carries " [cited]"
# directly after its number; guard both directions so the marker is verified derived state
# (grep '\[cited\]' on a ledger file = every code-anchored row, no tree cross-reference).
marked=$(grep -hoE '^- (\*\*)?D[AB]?-[0-9]{3} \[cited\]' \
    docs/rebuild/DEVIATIONS_LEDGER.md docs/rebuild/DEVIATIONS_LEDGER_A.md \
    docs/rebuild/DEVIATIONS_LEDGER_B.md 2>/dev/null | grep -oE 'D[AB]?-[0-9]{3}' | sort -u || true)
unmarked=$(comm -23 <(printf '%s\n' $cited | grep . | sort -u) <(printf '%s\n' $marked | grep .) || true)
stale=$(comm -13 <(printf '%s\n' $cited | grep . | sort -u) <(printf '%s\n' $marked | grep .) || true)
[ -z "$unmarked" ] || fail "ledger row(s) cited from code but missing the [cited] marker: $(echo $unmarked) — insert \" [cited]\" directly after the row number (D-174)"
[ -z "$stale" ] || fail "ledger row(s) marked [cited] but no longer cited anywhere in scope: $(echo $stale) — remove the marker, or restore the lost citation (D-174)"
echo "LADDER: D-citations OK ($(echo "$cited" | grep -c . || true) distinct, all resolve; [cited] markers in sync; no duplicate ledger rows)"

# --- guard 6: F-Droid changelog cap (D-173; char count DA-019) ---
# RUNBOOK §6: changelogs/<versionCode>.txt must stay under 500 CHARACTERS (whole file incl. the
# trailing newline) or F-Droid's code-quality scan flags the whatsNew. F-Droid measures string
# LENGTH (codepoints), not bytes, so this counts characters — a note with multibyte glyphs
# (em dashes, accents, emoji) may run past 500 bytes while still under the real 500-char limit.
# Codepoint count, locale-independent: strip UTF-8 continuation bytes (0x80-0xBF) with LC_ALL=C
# tr, then wc -c the remainder — one byte survives per codepoint (the lead byte / any ASCII byte).
# Only the CURRENT versionCode's file is checked — it is the one the next tag ships; historical
# files (e.g. the pre-rule 9.txt) are shipped facts, not actionable. Existence is
# release-preflight.yml's job (it knows whether the PR ships app code); this guard only rejects
# an oversize file.
vc=$(grep -oE 'versionCode[[:space:]]*=[[:space:]]*[0-9]+' app/build.gradle.kts 2>/dev/null \
  | grep -oE '[0-9]+' | head -1 || true)
if [ -n "$vc" ] && [ -f "fastlane/metadata/android/en-US/changelogs/${vc}.txt" ]; then
  cl="fastlane/metadata/android/en-US/changelogs/${vc}.txt"
  cl_chars=$(LC_ALL=C tr -d '\200-\277' < "$cl" | wc -c | tr -d '[:space:]')
  if [ "$cl_chars" -gt 500 ]; then
    fail "$cl is ${cl_chars} chars — over the 500-char F-Droid whatsNew cap (RUNBOOK §6). Shorten it."
  fi
  echo "LADDER: F-Droid changelog OK ($cl, ${cl_chars} chars <= 500)"
fi

# --- guard 7: rule-review tripwire (DA-006; WARN-only, local-only — skipped under
# GITHUB_ACTIONS like guards 3/4, numbered after the pre-existing guards whose numbers are
# cited by immutable ledger rows). The DA-005 rule-review protocol binds any diff to harness
# legislation; no guard can perform that review — this tripwire only SURFACES the obligation,
# by scanning the UNCOMMITTED changes (staged/unstaged/untracked; per-unit, unlike guard 3's
# per-branch diff base, so it stops warning once the reviewed unit is committed). STATE.md
# and the ledger files are deliberately NOT in the list: they change in nearly every unit
# (changelog/queue/row appends) and warning every unit is warn fatigue — their legislative
# sections (preambles, Decided non-items) stay prose-covered (RUNBOOK Rule-review protocol).
if [ "${GITHUB_ACTIONS:-}" != "true" ] && git rev-parse --git-dir >/dev/null 2>&1; then
  legislation_touched=""
  # A new agent adapter's permission-config file joins this list (CLAUDE.md Agent harness).
  for f in CLAUDE.md AGENTS.md docs/rebuild/RUNBOOK.md scripts/ladder.sh \
           scripts/test-ladder-guards.sh scripts/session-start.sh scripts/redact.sh \
           scripts/command-guard.sh .claude/settings.json; do
    if git status --porcelain -- "$f" 2>/dev/null | grep -q .; then
      legislation_touched="$legislation_touched $f"
    fi
  done
  if [ -n "$legislation_touched" ]; then
    echo "LADDER WARN: uncommitted changes touch harness legislation:${legislation_touched} — the rule-review protocol applies before commit (fresh-context reviewer, strongest tier; RUNBOOK Rule-review, DA-005/DA-006)."
  fi
fi

# --- guard 8: redact.sh self-test (DA-007) — the redaction filter is a rail; a silently
# broken pattern must fail the ladder. Milliseconds; fake tokens generated at runtime. ---
if [ -x scripts/redact.sh ]; then
  scripts/redact.sh --self-test >/dev/null \
    || fail "scripts/redact.sh --self-test FAILED — a redaction pattern regressed; fix the filter before committing (DA-007)"
  echo "LADDER: redact.sh self-test OK"
else
  fail "scripts/redact.sh missing or not executable — the DA-007 redaction rail is gone"
fi

# --- guard 9: secret-shape tree scan (DA-008; see header) ---
if git rev-parse --git-dir >/dev/null 2>&1; then
  # NUL-separated list + xargs -0: filenames with spaces/non-ASCII must be SCANNED, not
  # silently skipped — a word-split list was this guard's original blocker-class bug.
  git ls-files -z -co --exclude-standard | xargs -0 scripts/redact.sh --scan \
    || fail "secret-shaped content in the tree (files above, value-free positions) — never commit credentials; a FIXTURE token must be runtime-generated, never a stored literal (DA-008)"
  # The worktree scan alone misses a staged-then-reverted secret: also scan staged blobs.
  scripts/redact.sh --scan-staged \
    || fail "secret-shaped content in a STAGED blob (above) — unstage and purge it before committing; the index is what a commit records (DA-008)"
  echo "LADDER: secret-shape scan OK (worktree + staged)"
fi

# --- guard 10: command-guard self-test (DA-009) — the pre-execution command rail is a rail
# like guard 8's filter; a silently broken pattern must fail the ladder. Milliseconds. ---
if [ -x scripts/command-guard.sh ]; then
  scripts/command-guard.sh --self-test >/dev/null \
    || fail "scripts/command-guard.sh --self-test FAILED — a command-guard pattern regressed; fix the rail before committing (DA-009)"
  echo "LADDER: command-guard self-test OK"
else
  fail "scripts/command-guard.sh missing or not executable — the DA-009 command rail is gone"
fi

# Standard-container-only setup contract. Hosted CI selects JDK 21 through setup-java at a
# runner-specific path; the standard agent image has the fixed path this sourceable helper targets.
if [ -x scripts/test-container-setup.sh ] && [ -x /usr/lib/jvm/java-21-openjdk-amd64/bin/java ]; then
  scripts/test-container-setup.sh >/dev/null \
    || fail "scripts/test-container-setup.sh FAILED — JDK/SDK bootstrap contract regressed (DA-032)"
  echo "LADDER: standard-container setup contract OK"
fi

# --- guard 11: falsifiable doc-facts (DA-015) — a load-bearing prose claim earns a machine
# anchor only AFTER a real drift incident (P10; this is not a doc-testing framework — the
# incident-only bar is BINDING: a fact without one was reviewed out, DA-015). The expected
# value is a constant HERE, in lockstep with the cited doc sentence — the guard checks CODE
# against the constant and never parses prose; changing either side means changing both
# (guard 7 fires on this file; rule-review applies). This is a TRIPWIRE, not proof: it
# counts files naming ShizukuShell, not semantic call sites.
# Fact (drift incident d66de4c): CLAUDE.md "Shizuku is a genuine optional runtime
# dependency in exactly two places" — consumer files referencing ShizukuShell, excluding
# its own definition.
shizuku_expected=2
shizuku_sites=$(grep -rl 'ShizukuShell' platform/src/main app/src/main 2>/dev/null \
  | grep -cv '/ShizukuShell\.kt$' || true)
[ "$shizuku_sites" = "$shizuku_expected" ] \
  || fail "doc-fact drift: ${shizuku_sites} file(s) reference ShizukuShell but the docs claim exactly ${shizuku_expected} runtime places — update the claim in CLAUDE.md (and its restatements in README.md + docs/rebuild/architecture/privilege_tiers.md, the d66de4c drift sites) AND this guard's constant in lockstep (DA-015)"
echo "LADDER: doc-facts OK (Shizuku runtime sites = ${shizuku_sites})"

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
    # merge commit vanishes at squash-merge anyway). DA-010: don't make the agent reason
    # about train topology — a content-level test-merge (git merge-tree, no worktree touch)
    # decides mechanically: if merging origin/main would leave HEAD's tree unchanged, main
    # brings nothing this branch lacks (the DA-002 structural case — main advanced by squash
    # commits of this very train) and the advice is a hard "do NOT merge".
    behind=$(git rev-list --count HEAD..origin/main 2>/dev/null || echo 0)
    if [ "$behind" -gt 0 ]; then
      # The structural claim requires a CLEAN merge (rc 0) AND an unchanged tree: on a
      # conflict (rc 1) the OID still prints, and a modify/delete conflict can leave the
      # merged tree EQUAL to HEAD's while the real merge would stop on the conflict — tree
      # equality alone is not proof (DA-005 review finding, fixture-pinned). EMPTY output
      # (rc 128: unrelated histories in a shallow/partial clone, pre-2.38 git) means it
      # could not decide at all — fall back to the neutral wording, never assert a
      # divergence the tool didn't prove (found live: this container's shallow clone).
      merge_rc=0
      merged_tree=$(git merge-tree --write-tree origin/main HEAD 2>/dev/null | head -n 1) || merge_rc=$?
      if [ "$merge_rc" -eq 0 ] && [ -n "$merged_tree" ] && [ "$merged_tree" = "$(git rev-parse 'HEAD^{tree}')" ]; then
        echo "LADDER WARN: branch is ${behind} commit(s) behind origin/main, but a clean test-merge shows origin/main brings NO content this branch lacks — the DA-002 structural case (main advances by squash commits of this very train). Do NOT merge origin/main in; keep working."
      elif [ -n "$merged_tree" ] && [ "$merge_rc" -le 1 ]; then
        echo "LADDER WARN: branch is ${behind} commit(s) behind origin/main and a test-merge shows origin/main carries content differences (or a conflicting change) this branch lacks (DA-010). Inspect what the merge would bring first — a deliberate revert on this branch can look like missing content — and reconcile only if main carries work this branch genuinely lacks: 'git merge origin/main' (rebase ONLY if nothing is pushed yet; pushed checkpoints are immutable, never force-push)."
      else
        echo "LADDER WARN: branch is ${behind} commit(s) behind origin/main (test-merge inconclusive — shallow/partial clone or old git). In the DA-002 branch-train model this is usually STRUCTURAL (main advances by squash commits of this very train) — reconcile only if main carries work this branch genuinely lacks: 'git merge origin/main' (rebase ONLY if nothing is pushed yet; pushed checkpoints are immutable, never force-push)."
      fi
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
# If the session-start warm-up (D-173) is still in flight, say so: Gradle's inter-process
# lock serializes this build behind it (total wall time ≈ one cold build — expected, not a
# hang). Informational only — the lock IS the synchronization; no sentinel/wait (DA-010).
if pgrep -f 'warm-gradle\.sh' >/dev/null 2>&1; then
  echo "LADDER: Gradle warm-up still running — this build queues behind its lock; a long first rung is expected, not a hang (progress: ~/.gradle-warmup.log)"
fi
start=$SECONDS
./gradlew :domain:test :platform:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug "$@"
echo "LADDER PASS (guards + 5 rungs) in $((SECONDS - start))s"
