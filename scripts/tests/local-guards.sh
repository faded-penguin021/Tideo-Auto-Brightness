#!/usr/bin/env bash
# Fixture suite for the repo-local ladder guards under scripts/guards/.
#
# Yours, not shipped. The AMH's own scripts/test-ladder-guards.sh covers the SHIPPED ladder;
# nothing upstream knows these guards exist, so without this file they are four scripts whose
# failure paths have never run (docs/HARNESS_LOCAL.md). scripts/verify.sh invokes it.
#
# Every case builds a throwaway tree and runs the real guard against it. The point is the
# NEGATIVE cases: a guard that only passes on a clean tree is indistinguishable from `exit 0`.
set -uo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT" || exit 1

FAILS=0
CASES=0
SANDBOX=$(mktemp -d)
trap 'rm -rf "$SANDBOX"' EXIT

ok() { CASES=$((CASES + 1)); }
bad() {
	CASES=$((CASES + 1))
	FAILS=$((FAILS + 1))
	printf '  FAIL  %s\n' "$1" >&2
}

# Run <guard> inside a prepared sandbox tree; sets RC and OUT.
run_guard() { # <sandbox-subdir> <guard-name>
	local dir=$SANDBOX/$1 g=$2
	mkdir -p "$dir/scripts/guards"
	cp "$ROOT/scripts/guards/$g.sh" "$dir/scripts/guards/$g.sh"
	[ -f "$ROOT/scripts/redact.sh" ] && cp "$ROOT/scripts/redact.sh" "$dir/scripts/redact.sh"
	OUT=$( (cd "$dir" && bash "scripts/guards/$g.sh" 2>&1) )
	RC=$?
}

expect_pass() { # <label>
	[ "$RC" = 0 ] && ok || bad "$1 — expected pass, got rc=$RC: $OUT"
}
expect_fail() { # <label> [substring the diagnostic must contain]
	if [ "$RC" = 0 ]; then
		bad "$1 — expected FAIL, guard passed: $OUT"
		return
	fi
	if [ -n "${2:-}" ] && ! printf '%s' "$OUT" | grep -qF -- "$2"; then
		bad "$1 — failed as expected but the diagnostic never mentions '$2': $OUT"
		return
	fi
	ok
}

# =============================================================================
printf '\n· fdroid-changelog\n'

fdroid_tree() { # <dir> <versionCode> [changelog-body-file]
	local d=$SANDBOX/$1
	rm -rf "$d"
	mkdir -p "$d/app" "$d/fastlane/metadata/android/en-US/changelogs"
	printf 'android {\n  defaultConfig {\n    versionCode = %s\n    versionName = "9.9.9"\n  }\n}\n' \
		"$2" >"$d/app/build.gradle.kts"
}

fdroid_tree fd-ok 42
printf 'Short and sweet.\n' >"$SANDBOX/fd-ok/fastlane/metadata/android/en-US/changelogs/42.txt"
run_guard fd-ok fdroid-changelog
expect_pass "a short changelog passes"

fdroid_tree fd-over 42
head -c 501 /dev/zero | tr '\0' 'x' >"$SANDBOX/fd-over/fastlane/metadata/android/en-US/changelogs/42.txt"
run_guard fd-over fdroid-changelog
expect_fail "a 501-character changelog fails" "over the 500-char"

# THE case this guard exists for: F-Droid counts CODEPOINTS, so a note well past 500 BYTES is
# still legal if it is under 500 characters. A `wc -c` implementation passes every other case
# in this file and fails this one.
fdroid_tree fd-multibyte 42
{
	i=0
	while [ "$i" -lt 400 ]; do
		printf '—'
		i=$((i + 1))
	done
	printf '\n'
} >"$SANDBOX/fd-multibyte/fastlane/metadata/android/en-US/changelogs/42.txt"
run_guard fd-multibyte fdroid-changelog
expect_pass "400 em dashes (1201 bytes, 401 chars) passes — the guard counts codepoints, not bytes"

fdroid_tree fd-missing 42
run_guard fd-missing fdroid-changelog
expect_pass "no changelog for this versionCode yet passes (existence is release-preflight's job)"

rm -rf "$SANDBOX/fd-novc"
mkdir -p "$SANDBOX/fd-novc/app"
printf 'android { }\n' >"$SANDBOX/fd-novc/app/build.gradle.kts"
run_guard fd-novc fdroid-changelog
expect_fail "an unreadable versionCode fails rather than passing vacuously" "checked nothing"

# =============================================================================
printf '· doc-facts\n'

shizuku_tree() { # <dir> <consumer-count>
	local d=$SANDBOX/$1 i=0
	rm -rf "$d"
	mkdir -p "$d/platform/src/main" "$d/app/src/main"
	printf 'object ShizukuShell { fun exec() {} }\n' >"$d/platform/src/main/ShizukuShell.kt"
	while [ "$i" -lt "$2" ]; do
		printf 'val x = ShizukuShell.exec()\n' >"$d/app/src/main/Consumer$i.kt"
		i=$((i + 1))
	done
}

shizuku_tree df-ok 2
run_guard df-ok doc-facts
expect_pass "exactly two ShizukuShell consumers passes"

shizuku_tree df-three 3
run_guard df-three doc-facts
expect_fail "a third consumer fails — the docs claim exactly two" "doc-fact drift"

shizuku_tree df-one 1
run_guard df-one doc-facts
expect_fail "dropping to one consumer fails too — drift is bidirectional" "doc-fact drift"

# The definition file itself must never be counted as a consumer, or removing the last real
# consumer would still read as one site.
shizuku_tree df-zero 0
run_guard df-zero doc-facts
expect_fail "the definition file alone is zero consumers, not one" "0 file(s)"

# =============================================================================
printf '· ledger-prefix\n'

ledger_tree() { # <dir>; then callers write docs/LEDGER*.md
	local d=$SANDBOX/$1
	rm -rf "$d"
	mkdir -p "$d/docs"
}

ledger_tree lp-ok
printf -- '- D-001: base row.\n- D-002 [cited]: another.\n' >"$SANDBOX/lp-ok/docs/LEDGER.md"
printf -- '- DA-001: rolled over.\n' >"$SANDBOX/lp-ok/docs/LEDGER_A.md"
printf -- '- DB-001: rolled over again.\n' >"$SANDBOX/lp-ok/docs/LEDGER_B.md"
run_guard lp-ok ledger-prefix
expect_pass "every row in the volume its prefix names passes"

ledger_tree lp-wrong
printf -- '- D-001: base row.\n' >"$SANDBOX/lp-wrong/docs/LEDGER.md"
printf -- '- DA-001: fine.\n- DB-007: appended to the WRONG volume.\n' >"$SANDBOX/lp-wrong/docs/LEDGER_A.md"
run_guard lp-wrong ledger-prefix
expect_fail "a DB- row in LEDGER_A.md fails — the shipped citation guard pools volumes and cannot see this" "DB-007"

ledger_tree lp-bare
printf -- '- DA-001: a rolled-over row left in the base volume.\n' >"$SANDBOX/lp-bare/docs/LEDGER.md"
run_guard lp-bare ledger-prefix
expect_fail "a DA- row in the base volume fails" "DA-001"

ledger_tree lp-none
run_guard lp-none ledger-prefix
expect_fail "no volumes at all fails rather than passing vacuously" "checked nothing"

# =============================================================================
printf '· staged-secrets\n'

secret_tree() { # <dir>
	local d=$SANDBOX/$1
	rm -rf "$d"
	mkdir -p "$d"
	git -C "$d" init -q
	git -C "$d" config user.email harness@example.invalid
	git -C "$d" config user.name harness
}

# Runtime-generated, never a stored literal: a format-valid token committed to this repo would
# itself be secret-shaped content in the tree, which is the thing being guarded against.
fake_token() {
	local t=''
	while [ "${#t}" -lt 16 ]; do
		t=$t$(head -c 512 /dev/urandom | LC_ALL=C tr -dc 'A-Z0-9')
	done
	printf 'AKIA%s' "${t:0:16}"
}

secret_tree ss-clean
printf 'nothing to see here\n' >"$SANDBOX/ss-clean/notes.txt"
git -C "$SANDBOX/ss-clean" add notes.txt
run_guard ss-clean staged-secrets
expect_pass "a clean staged blob passes"

TOK=$(fake_token)
secret_tree ss-dirty
printf 'key = %s\n' "$TOK" >"$SANDBOX/ss-dirty/config.txt"
git -C "$SANDBOX/ss-dirty" add config.txt
run_guard ss-dirty staged-secrets
expect_fail "a credential-shaped string in a staged blob fails" "config.txt"
if printf '%s' "$OUT" | grep -qF "$TOK"; then
	bad "staged-secrets printed the matched credential — the diagnostic must be value-free"
else
	ok
fi

# The whole reason this guard exists alongside the shipped worktree scan: the secret is gone
# from disk but still in the index, which is what `git commit` records.
TOK=$(fake_token)
secret_tree ss-reverted
printf 'key = %s\n' "$TOK" >"$SANDBOX/ss-reverted/config.txt"
git -C "$SANDBOX/ss-reverted" add config.txt
printf 'key = REDACTED-BY-HAND\n' >"$SANDBOX/ss-reverted/config.txt"
run_guard ss-reverted staged-secrets
expect_fail "a staged-then-reverted secret fails — the worktree is clean, the index is not" "config.txt"

# =============================================================================
printf '\n'
if [ "$FAILS" -gt 0 ]; then
	printf 'repo-local guard fixtures: %d/%d case(s) FAILED\n' "$FAILS" "$CASES" >&2
	exit 1
fi
printf 'repo-local guard fixtures: %d cases pass\n' "$CASES"
