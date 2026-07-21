#!/usr/bin/env bash
# test-ladder-guards.sh — regression tests for scripts/ladder.sh's pre-flight guards (D-173).
#
# The guards are load-bearing: build.yml runs ladder.sh directly (D-166), so a silently broken
# guard weakens both local sessions AND CI. This suite exercises every guard against a
# throwaway sandbox repo built in mktemp: fixture STATE/ledger/gradle/changelog files, the
# REAL scripts/ladder.sh copied in unmodified, and a fake origin/main ref (git update-ref) so
# guard 2's token scan genuinely runs. Everything is --guards-only — no Gradle, runs in
# seconds. build.yml runs this as its own step before the acceptance ladder.
#
# Fixture D-numbers below are synthetic. This file lives in scripts/ precisely so ladder.sh's
# guard 5 (which skips scripts/) never mistakes them for real citations.
#
# Usage: bash scripts/test-ladder-guards.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SANDBOX="$(mktemp -d)"
trap 'rm -rf "$SANDBOX"' EXIT

pass=0
declare -a failures=()

# --- sandbox construction -------------------------------------------------------------------

CHANGELOG_DIR=fastlane/metadata/android/en-US/changelogs

write_state() {  # $1 = approx extra bytes of filler (0 = minimal)
  {
    printf '# STATE fixture\n\n## Project\np\n\n## Current state\nc\n\n## Owner queue\nq\n\n'
    printf '## Decided non-items\nn\n\n## Changelog\n- line\n'
    if [ "${1:-0}" -gt 0 ]; then
      head -c "$1" /dev/zero | tr '\0' 'x'
      printf '\n'
    fi
  } > "$SANDBOX/docs/rebuild/STATE.md"
}

write_ledger() {  # $1 = row count, $2 = target file (default base ledger)
  local n="$1" f="${2:-$SANDBOX/docs/rebuild/DEVIATIONS_LEDGER.md}" i
  { printf '# ledger fixture\n\n'
    for i in $(seq 1 "$n"); do printf -- '- D-%03d: fixture row\n' "$i"; done
  } > "$f"
}

write_ledger_padded() {  # $1 = filler lines BEFORE the final row, $2 = trailing continuation
  local pad="$1" tail="${2:-0}" f="${3:-$SANDBOX/docs/rebuild/DEVIATIONS_LEDGER.md}"
  { printf '# ledger fixture\n\n'
    [ "$pad" -gt 0 ] && seq 1 "$pad" | sed 's/.*/  filler prose line/'
    printf -- '- D-001: fixture row\n'
    [ "$tail" -gt 0 ] && seq 1 "$tail" | sed 's/.*/  row continuation line/'
    true
  } > "$f"
}

write_gradle() {  # $1 = versionCode
  printf 'android { defaultConfig { versionCode = %s\nversionName = "1.0.0" } }\n' "$1" \
    > "$SANDBOX/app/build.gradle.kts"
}

git_q() { git -C "$SANDBOX" -c user.email=t@t -c user.name=t "$@"; }

mkdir -p "$SANDBOX/docs/rebuild" "$SANDBOX/scripts" "$SANDBOX/app" "$SANDBOX/$CHANGELOG_DIR"
cp "$REPO_ROOT/scripts/ladder.sh" "$SANDBOX/scripts/ladder.sh"
cp "$REPO_ROOT/scripts/redact.sh" "$SANDBOX/scripts/redact.sh"   # guard 8 runs its self-test
cp "$REPO_ROOT/scripts/command-guard.sh" "$SANDBOX/scripts/command-guard.sh"   # guard 10 ditto
write_state 0
write_ledger 10
write_gradle 7
printf 'Short changelog.\n' > "$SANDBOX/$CHANGELOG_DIR/7.txt"
printf 'shared baseline\n' > "$SANDBOX/app/shared.txt"   # guard 4's modify/delete fixture
git_q init -q
git_q add -A
git_q commit -qm baseline
git_q update-ref refs/remotes/origin/main HEAD
BASELINE_SHA="$(git_q rev-parse HEAD)"

# --- assertion helper -----------------------------------------------------------------------

check() {  # $1 = case name, $2 = expected rc (0/1), $3 = required output substring
  # GITHUB_ACTIONS is cleared so the suite exercises the LOCAL guard set deterministically —
  # CI exports it for every step, which would silently skip guard 7's positive cases
  # (DA-006 rule-review finding). The CI-skip behavior is asserted explicitly below.
  local name="$1" want_rc="$2" want_out="$3" out rc=0
  out="$(cd "$SANDBOX" && GITHUB_ACTIONS= bash scripts/ladder.sh --guards-only 2>&1)" || rc=$?
  if [ "$rc" -ne "$want_rc" ] || ! grep -qF "$want_out" <<< "$out"; then
    failures+=("$name (rc=$rc, wanted rc=$want_rc + \"$want_out\")")
    printf '=== FAIL: %s ===\n%s\n' "$name" "$out" >&2
  else
    pass=$((pass + 1))
  fi
}

# --- guard 1: STATE length ------------------------------------------------------------------

check "baseline is green" 0 "LADDER PASS (guards only)"

write_state 17000
check "guard 1: STATE > 16 KB fails" 1 "over the 16 KB hard cap"
write_state 15000
check "guard 1: STATE > 14 KB warns deep-compress" 0 "compress DEEP to <= 9 KB"
write_state 13000
check "guard 1: 12-14 KB band is quiet (DA-004 hysteresis)" 0 "STATE.md size OK"
write_state 0

# LADDER_STATE_FILE override (the documented test-suite hook).
head -c 17000 /dev/zero | tr '\0' 'x' > "$SANDBOX/docs/rebuild/state_big.md"
out_rc=0
(cd "$SANDBOX" && LADDER_STATE_FILE=docs/rebuild/state_big.md bash scripts/ladder.sh --guards-only >/dev/null 2>&1) || out_rc=$?
if [ "$out_rc" -eq 1 ]; then pass=$((pass + 1)); else failures+=("LADDER_STATE_FILE override honored"); fi
rm "$SANDBOX/docs/rebuild/state_big.md"

# --- guard 1b: required sections ------------------------------------------------------------

sed -i '/## Changelog/d' "$SANDBOX/docs/rebuild/STATE.md"
check "guard 1b: missing required section fails" 1 'missing required section "## Changelog"'
write_state 0
sed -i '/## Owner queue/d' "$SANDBOX/docs/rebuild/STATE.md"
check "guard 1b: missing Owner queue only warns" 0 "missing '## Owner queue' (D-167)"
write_state 0

# --- guard 1c: ledger rollover (line-based, DA-001) -----------------------------------------

write_ledger_padded 1005
check "guard 1c: row starting past the 1000-line cap fails" 1 "belongs in the next ledger file"
write_ledger_padded 900 200
check "guard 1c: final row may overflow the cap (warn only)" 0 "the NEXT deviation opens the next ledger file"
write_ledger_padded 950
check "guard 1c: >= 900 lines warns" 0 "rollover soon"
write_ledger 10

# _A.md presence redirects the live-file pick (and DA- citation routing further down).
write_ledger 150 "$SANDBOX/docs/rebuild/DEVIATIONS_LEDGER_A.md"
sed -i 's/- D-/- DA-/' "$SANDBOX/docs/rebuild/DEVIATIONS_LEDGER_A.md"
check "guard 1c: _A.md becomes the live file" 0 "lines in docs/rebuild/DEVIATIONS_LEDGER_A.md"
rm "$SANDBOX/docs/rebuild/DEVIATIONS_LEDGER_A.md"

# LADDER_LEDGER_FILE override (the documented test-suite hook).
write_ledger_padded 1005 0 "$SANDBOX/docs/rebuild/ledger_full.md"
out_rc=0
(cd "$SANDBOX" && LADDER_LEDGER_FILE=docs/rebuild/ledger_full.md bash scripts/ladder.sh --guards-only >/dev/null 2>&1) || out_rc=$?
if [ "$out_rc" -eq 1 ]; then pass=$((pass + 1)); else failures+=("LADDER_LEDGER_FILE override honored"); fi
rm "$SANDBOX/docs/rebuild/ledger_full.md"

# --- guard 5: D-citation integrity ----------------------------------------------------------

printf '// cited: D-002\n' > "$SANDBOX/app/Cited.kt"
sed -i 's/^- D-002:/- D-002 [cited]:/' "$SANDBOX/docs/rebuild/DEVIATIONS_LEDGER.md"
check "guard 5: cited + marked row passes" 0 "1 distinct, all resolve; [cited] markers in sync"
write_ledger 10
check "guard 5: cited row missing its [cited] marker fails" 1 "missing the [cited] marker"
rm "$SANDBOX/app/Cited.kt"
sed -i 's/^- D-004:/- D-004 [cited]:/' "$SANDBOX/docs/rebuild/DEVIATIONS_LEDGER.md"
check "guard 5: stale [cited] marker fails" 1 "no longer cited anywhere in scope"
write_ledger 10
printf '// cited: D-999\n' > "$SANDBOX/app/Cited.kt"
check "guard 5: dangling citation fails" 1 "dangling deviation citation D-999"
printf '// cited: DA-001\n' > "$SANDBOX/app/Cited.kt"
check "guard 5: DA- cite without ledger A fails" 1 "DEVIATIONS_LEDGER_A.md not found"
rm "$SANDBOX/app/Cited.kt"

printf -- '- D-005: duplicate\n' >> "$SANDBOX/docs/rebuild/DEVIATIONS_LEDGER.md"
check "guard 5: duplicate ledger row fails" 1 "duplicate deviation row number"
write_ledger 10

# --- guard 6: F-Droid changelog cap ---------------------------------------------------------

head -c 501 /dev/zero | tr '\0' 'x' > "$SANDBOX/$CHANGELOG_DIR/7.txt"
check "guard 6: 501-byte changelog fails" 1 "over the 500-char F-Droid whatsNew cap"
head -c 500 /dev/zero | tr '\0' 'x' > "$SANDBOX/$CHANGELOG_DIR/7.txt"
check "guard 6: 500-byte changelog passes" 0 "F-Droid changelog OK"
printf 'Short changelog.\n' > "$SANDBOX/$CHANGELOG_DIR/7.txt"

# --- guard 8: redact.sh self-test -----------------------------------------------------------

# A weakened filter (here: a stub whose self-test fails) must fail the whole ladder.
printf '#!/bin/bash\n[ "${1:-}" = "--self-test" ] && exit 1\ncat\n' > "$SANDBOX/scripts/redact.sh"
chmod +x "$SANDBOX/scripts/redact.sh"
check "guard 8: broken redact.sh self-test fails" 1 "redaction pattern regressed"
rm "$SANDBOX/scripts/redact.sh"
check "guard 8: missing redact.sh fails" 1 "redaction rail is gone"
cp "$REPO_ROOT/scripts/redact.sh" "$SANDBOX/scripts/redact.sh"

# --- guard 10: command-guard self-test ------------------------------------------------------

printf '#!/bin/bash\n[ "${1:-}" = "--self-test" ] && exit 1\nexit 0\n' > "$SANDBOX/scripts/command-guard.sh"
chmod +x "$SANDBOX/scripts/command-guard.sh"
check "guard 10: broken command-guard self-test fails" 1 "command-guard pattern regressed"
rm "$SANDBOX/scripts/command-guard.sh"
check "guard 10: missing command-guard fails" 1 "command rail is gone"
cp "$REPO_ROOT/scripts/command-guard.sh" "$SANDBOX/scripts/command-guard.sh"

# --- guard 9: secret-shape tree scan --------------------------------------------------------

g9_tok="ghp_$(head -c 36 /dev/zero | tr '\0' a)"
printf 'debug log: %s\n' "$g9_tok" > "$SANDBOX/app/leak b.log"   # space in name: must be
check "guard 9: secret-shaped file (space in name) fails" 1 "secret-shaped content in the tree"
# Value-free property (D-175): the failing guard's output must never contain the raw token.
g9_out=$(cd "$SANDBOX" && bash scripts/ladder.sh --guards-only 2>&1) || true
if grep -qF "$g9_tok" <<<"$g9_out"; then
  failures+=("guard 9 printed the secret value (D-175 violation)")
  printf '=== FAIL: guard 9 value leak ===\n' >&2
else
  pass=$((pass + 1))
fi
rm "$SANDBOX/app/leak b.log"
# Staged-blob gap: secret staged, worktree copy cleaned -> must still fail.
printf 'staged %s\n' "$g9_tok" > "$SANDBOX/app/staged.txt"
git_q add app/staged.txt
printf 'clean now\n' > "$SANDBOX/app/staged.txt"
check "guard 9: staged-then-reverted secret fails" 1 "STAGED blob"
git_q reset -q -- app/staged.txt
rm "$SANDBOX/app/staged.txt"
check "guard 9: clean tree passes" 0 "secret-shape scan OK (worktree + staged)"

# --- guard 2: D-115 skip-ci tokens ----------------------------------------------------------

git_q commit -q --allow-empty -m 'document the [skip ci] literal'
check "guard 2: skip-ci token in unmerged commit fails" 1 "forbidden CI-skip token"
git_q reset -q --hard "$BASELINE_SHA"
git_q commit -q --allow-empty -m 'harmless commit saying skip-ci (hyphenated)'
check "guard 2: hyphenated skip-ci is allowed" 0 "no skip-ci tokens"
git_q reset -q --hard "$BASELINE_SHA"

# --- guard 4: stale-branch tripwire classification (DA-010) ---------------------------------

# Structural case: origin/main is a squash of work this branch already contains (same tree,
# different commit) -> the warning must say the merge is NOT wanted.
printf 'unit work\n' > "$SANDBOX/app/work.txt"
git_q add -A
git_q commit -qm 'unit work'
squash_sha=$(git_q commit-tree 'HEAD^{tree}' -p "$BASELINE_SHA" -m 'squash of the train')
git_q update-ref refs/remotes/origin/main "$squash_sha"
check "guard 4: behind-main, no content delta -> structural, do NOT merge" 0 "Do NOT merge origin/main"
# Divergent case: origin/main carries a file this branch lacks -> reconcile advice. The
# commit is built with plumbing (temp index) so the sandbox worktree stays untouched.
tmpidx="$SANDBOX/.git/tmp-index-g4"
hotfix_blob=$(printf 'hotfix\n' | git_q hash-object -w --stdin)
GIT_INDEX_FILE="$tmpidx" git_q read-tree "$BASELINE_SHA"
GIT_INDEX_FILE="$tmpidx" git_q update-index --add --cacheinfo "100644,$hotfix_blob,app/hotfix.txt"
divergent_tree=$(GIT_INDEX_FILE="$tmpidx" git_q write-tree)
rm -f "$tmpidx"
divergent_sha=$(git_q commit-tree "$divergent_tree" -p "$BASELINE_SHA" -m 'hotfix on main')
git_q update-ref refs/remotes/origin/main "$divergent_sha"
check "guard 4: behind-main with content differences -> reconcile advice" 0 "carries content differences"
# Modify/delete conflict (DA-005 review BLOCKER): branch modified app/shared.txt, main
# deleted it. merge-ort exits 1 but leaves HEAD's version in the written tree, so the
# merged-tree OID EQUALS HEAD's — tree equality alone must never yield "do NOT merge".
printf 'branch edit\n' > "$SANDBOX/app/shared.txt"
git_q add -A
git_q commit -qm 'edit shared on branch'
GIT_INDEX_FILE="$tmpidx" git_q read-tree "$BASELINE_SHA"
GIT_INDEX_FILE="$tmpidx" git_q update-index --force-remove app/shared.txt
del_tree=$(GIT_INDEX_FILE="$tmpidx" git_q write-tree)
rm -f "$tmpidx"
del_sha=$(git_q commit-tree "$del_tree" -p "$BASELINE_SHA" -m 'delete shared on main')
git_q update-ref refs/remotes/origin/main "$del_sha"
check "guard 4: modify/delete conflict (tree==HEAD, rc 1) -> reconcile, never structural" 0 "carries content differences"
git_q reset -q --hard "$BASELINE_SHA"
git_q update-ref refs/remotes/origin/main "$BASELINE_SHA"
# Inconclusive case: origin/main with an UNRELATED history (orphan commit — the shallow/
# partial-clone shape found live) -> merge-tree can't decide; neutral wording, no false
# divergence claim.
orphan_sha=$(git_q commit-tree "$(git_q hash-object -w -t tree /dev/null)" -m 'orphan main')
git_q update-ref refs/remotes/origin/main "$orphan_sha"
check "guard 4: unrelated histories -> inconclusive neutral wording" 0 "test-merge inconclusive"
git_q reset -q --hard "$BASELINE_SHA"
git_q update-ref refs/remotes/origin/main "$BASELINE_SHA"

# --- guard 7: rule-review tripwire (DA-006) --------------------------------------------------

printf '# fixture legislation file\n' > "$SANDBOX/CLAUDE.md"
check "guard 7: untracked legislation file warns rule-review" 0 "rule-review protocol applies"
rm "$SANDBOX/CLAUDE.md"

printf '\n# fixture appended comment\n' >> "$SANDBOX/scripts/ladder.sh"
check "guard 7: modified guard script warns rule-review" 0 "rule-review protocol applies"
git_q checkout -q -- scripts/ladder.sh

# Negative cases need absence assertions, so they bypass check()'s substring-required form.
out_rc=0
out="$(cd "$SANDBOX" && GITHUB_ACTIONS= bash scripts/ladder.sh --guards-only 2>&1)" || out_rc=$?
if [ "$out_rc" -eq 0 ] && ! grep -qF "rule-review protocol applies" <<< "$out"; then
  pass=$((pass + 1))
else
  failures+=("guard 7: clean legislation files stay quiet")
fi
printf '# fixture legislation file\n' > "$SANDBOX/CLAUDE.md"
out_rc=0
out="$(cd "$SANDBOX" && GITHUB_ACTIONS=true bash scripts/ladder.sh --guards-only 2>&1)" || out_rc=$?
if [ "$out_rc" -eq 0 ] && ! grep -qF "rule-review protocol applies" <<< "$out"; then
  pass=$((pass + 1))
else
  failures+=("guard 7: tripwire skipped under GITHUB_ACTIONS")
fi
rm "$SANDBOX/CLAUDE.md"

# --- summary --------------------------------------------------------------------------------

if [ "${#failures[@]}" -gt 0 ]; then
  printf 'GUARD TESTS FAIL (%d passed, %d failed):\n' "$pass" "${#failures[@]}" >&2
  printf '  - %s\n' "${failures[@]}" >&2
  exit 1
fi
echo "GUARD TESTS PASS ($pass cases)"
