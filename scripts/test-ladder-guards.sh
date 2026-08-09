#!/usr/bin/env bash
# AMH — fixture regression suite for the ladder's guards.
#
# Guards are code. A guard that false-passes is worse than no guard, because the
# repo now believes it is protected. Every guard therefore gets a synthesized tiny
# repo and an assertion on its pass / warn / fail behaviour.
#
# Each test builds a throwaway repo containing the shipped scripts, breaks exactly
# one thing, and asserts the ladder's verdict.
#
# Shipped by the Agentic Maintenance Harness. Repo-agnostic: do not edit locally.

set -uo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

export GIT_AUTHOR_NAME=amh-test GIT_AUTHOR_EMAIL=amh@test.invalid
export GIT_COMMITTER_NAME=amh-test GIT_COMMITTER_EMAIL=amh@test.invalid

PASSED=0
FAILED=0
SUITE_STARTED=$SECONDS
REPORT_STARTED=$SECONDS
SLOW_FIXTURE_SECONDS=${SLOW_FIXTURE_SECONDS:-10}
slow_threshold_valid() { # <candidate> — bounded to integers every supported bash can compare
	case $1 in
		''|*[!0-9]*|??????????*) return 1 ;;
	esac
	[ "$1" -le 999999999 ]
}
if ! slow_threshold_valid "$SLOW_FIXTURE_SECONDS"; then
		printf 'FIXTURE ERROR: SLOW_FIXTURE_SECONDS must be an integer from 0 to 999999999, got %q\n' \
			"$SLOW_FIXTURE_SECONDS" >&2
		exit 2
fi

# Keep the three slowest fixtures in shell variables: timing diagnostics must not add a
# dependency merely to sort a few integer values. Bash's integer SECONDS counter is coarse
# by design, monotonic enough for diagnostics within one process, and avoids non-portable
# sub-second `date` formats. Ties retain fixture execution order.
SLOWEST_SECONDS=(-1 -1 -1)
SLOWEST_NAMES=('' '' '')

record_timing() { # <fixture name> <elapsed whole seconds>
	local name=$1 elapsed=$2 rank prior
	if [ "$elapsed" -ge "$SLOW_FIXTURE_SECONDS" ]; then
		printf '  SLOW %ss - %s\n' "$elapsed" "$name"
	fi
	for rank in 0 1 2; do
		if [ "$elapsed" -gt "${SLOWEST_SECONDS[$rank]}" ]; then
			prior=$elapsed elapsed=${SLOWEST_SECONDS[$rank]}
			SLOWEST_SECONDS[rank]=$prior
			prior=$name name=${SLOWEST_NAMES[$rank]}
			SLOWEST_NAMES[rank]=$prior
		fi
	done
}

print_timing_summary() {
	local rank
	printf 'timing: %ss total; slowest fixtures:\n' "$((SECONDS - SUITE_STARTED))"
	for rank in 0 1 2; do
		[ "${SLOWEST_SECONDS[$rank]}" -ge 0 ] &&
			printf '  %ss - %s\n' "${SLOWEST_SECONDS[$rank]}" "${SLOWEST_NAMES[$rank]}"
	done
}

# --- fixture construction ---------------------------------------------------
DEFAULT_BRANCH_FIXTURE=main # must match amh.conf's DEFAULT_BRANCH below

# The integrity rung's manifest, built for whatever scripts a fixture actually has. Written
# rather than copied from the harness checkout, because a fixture repo holds four of the
# shipped scripts and the real manifest names five — and a manifest naming a file the tree
# does not have is one of the cases under test, not the baseline.
#
# The hashing tool is resolved ONCE, at the top level. Resolved inside the function, `exit`
# would only kill the command substitution it runs in: the diagnostic prints, the suite carries
# on, and every fixture gets a manifest of empty hashes — which does not produce the silent
# green anyone feared, it produces sixty unrelated red assertions blaming the wrong thing.
HASHER=''
if command -v sha256sum >/dev/null 2>&1; then
	HASHER=sha256sum
elif command -v shasum >/dev/null 2>&1; then
	HASHER=shasum
fi

fixture_sha256() { # <file>
	case $HASHER in
	sha256sum) sha256sum <"$1" ;;
	shasum) shasum -a 256 <"$1" ;;
	esac | sed 's/[^0-9a-f].*//'
}

# With no hasher the fixture repos get NO manifest, which is a state the rung handles (it
# warns), so the rest of the suite runs normally and the integrity cases below are skipped as
# a named, counted block. Same treatment as any other case that needs a tool outside the
# harness's stated baseline: gated loudly, never quietly dropped.
write_manifest() { # <fixture-dir>
	local d=$1 f
	[ -n "$HASHER" ] || return 0
	{
		printf '# AMH fixture — shipped-script integrity manifest.\n'
		for f in "$d"/scripts/*.sh; do
			printf '%s  scripts/%s\n' "$(fixture_sha256 "$f")" "$(basename -- "$f")"
		done
	} >"$d/scripts/MANIFEST.sha256"
}

mk() { # mk <name> -> prints the fixture path
	local d="$WORK/$1"
	mkdir -p "$d/scripts/guards" "$d/docs"
	# session-start.sh is copied too. It was left out for as long as this file existed,
	# which is why its two silent skips (an invalid REMOTE_FLAG, a bootstrap gated on its
	# exec bit) survived a shipped script with a fixture suite around it.
	cp "$ROOT/scripts/ladder.sh" "$ROOT/scripts/redact.sh" \
		"$ROOT/scripts/command-guard.sh" "$ROOT/scripts/session-start.sh" "$d/scripts/"
	chmod +x "$d/scripts"/*.sh
	# Ordinary fixtures exercise other guards. Patch only their disposable ladder copy so the
	# expensive, independently fixtured rungs return loudly; the shipped ladder has no bypass.
	sed -i '/^guard_rail_selftests() {/a\
\tskip "scripts/command-guard.sh and scripts/redact.sh self-tests already covered by fixture suite"\
\treturn' "$d/scripts/ladder.sh"
	sed -i '/^guard_shipped_integrity() {/a\
\tskip "shipped-script manifest check already covered by fixture suite"\
\treturn' "$d/scripts/ladder.sh"
	cat >"$d/amh.conf" <<-'CONF'
		DEFAULT_BRANCH=main
		BRANCH_PREFIX=session
		MERGE_MODE=branch-per-change
		REMOTE_FLAG=AMH_REMOTE
		STATE_FILE=docs/STATE.md
		STATE_COMPRESS_TO_KB=9
		STATE_WARN_KB=14
		STATE_HARD_KB=16
		STATE_EDIT_DELTA_BYTES=1024
		STATE_REQUIRED_SECTIONS='## Project|## Current state|## Changelog'
		STATE_OWNER_QUEUE_SECTION='## Owner queue'
		LEDGER_DIR=docs
		LEDGER_BASENAME=LEDGER
		LEDGER_LINE_CAP=800
		LEDGER_ROW_CHAR_CAP=2000
		CITATION_SCAN_PATHS='scripts'
		CITATION_EXCLUDE=''
		POISON_TOKENS='[skip ci]'
		PLAN_DIR=docs/plans
		RULE_FILES=''
	CONF
	cat >"$d/docs/STATE.md" <<-'ST'
		# STATE

		## Project
		A fixture.

		## Current state
		No active work.

		## Owner queue
		**Pending owner actions:** (none)

		## Changelog
		- 2026-01-01 — nothing yet.
	ST
	# D-001 and D-002 are the citation fixtures' own material and must stay UNMARKED.
	# Any row a shipped script cites in its comments has to exist here too, marked —
	# otherwise the citation guard fails every fixture for a reason none of them is
	# testing. Derived from the scripts just copied, never hardcoded: a hardcoded list
	# rots the first time a shipped comment cites a new row (it did).
	{
		printf '# LEDGER\n\n- D-001: a durable fact.\n- D-002: another durable fact.\n'
		grep -ohwE 'D[A-Z]*-[0-9]+' "$d/scripts"/*.sh | sort -u | grep -vxE 'D-00[12]' |
			while IFS= read -r id; do
				printf -- '- %s [cited]: a durable fact a shipped script cites.\n' "$id"
			done
	} >"$d/docs/LEDGER.md"
	(
		cd "$d" || exit 1
		git init -q .
		git add -A
		git commit -qm "fixture"
		# An `origin/<default>` ref, because three guards resolve one and go VACUOUS
		# without it: the poison-token scan has nothing to diff against and prints
		# `skip` on every run — which is how it shipped untested and inert in the
		# reference repo itself. A local ref under refs/remotes is enough; no network.
		git update-ref "refs/remotes/origin/$DEFAULT_BRANCH_FIXTURE" HEAD
	)
	printf '%s' "$d"
}

# Ordinary guard fixtures do not exercise either expensive rung. Dedicated cases below run
# each real rung against freshly copied scripts, and the ladder prints a named skip so this
# fixture-only optimization can never resemble coverage it did not execute.
run() { (cd "$1" && CI=1 scripts/ladder.sh --guards-only 2>&1); }

mk_unmodified() { # mk_unmodified <name> -> prints a fixture with the real copied ladder
	local d
	d=$(mk "$1")
	cp "$ROOT/scripts/ladder.sh" "$d/scripts/ladder.sh"
	git -C "$d" add scripts/ladder.sh
	git -C "$d" commit -qm "restore unmodified ladder" --amend
	git -C "$d" update-ref "refs/remotes/origin/$DEFAULT_BRANCH_FIXTURE" HEAD
	printf '%s' "$d"
}

run_rails() { (cd "$1" && CI=1 scripts/ladder.sh --guards-only 2>&1); }

mk_integrity() { # mk_integrity <name> -> prints a fixture path with a current manifest
	local d
	d=$(mk "$1")
	# Restore the real integrity rung, but retain the fixture-only rail skip. Integrity cases
	# are not rail cases; using mk_unmodified here reran command-guard's self-test ten times.
	cp "$ROOT/scripts/ladder.sh" "$d/scripts/ladder.sh"
	sed -i '/^guard_rail_selftests() {/a\
\tskip "scripts/command-guard.sh and scripts/redact.sh self-tests already covered by fixture suite"\
\treturn' "$d/scripts/ladder.sh"
	write_manifest "$d"
	git -C "$d" add scripts/ladder.sh scripts/MANIFEST.sha256
	git -C "$d" commit -qm "add fixture manifest" --amend
	git -C "$d" update-ref "refs/remotes/origin/$DEFAULT_BRANCH_FIXTURE" HEAD
	printf '%s' "$d"
}

# The FULL ladder, verification rung included. `run()` always passes --guards-only, so the
# two `✗ ladder red` verdicts below rung 3 are unreachable through it — untestable by
# construction, which is the defect D-020 names, not merely untested.
run_full() { (cd "$1" && CI=1 scripts/ladder.sh 2>&1); }

# The advisory rung starts with `in_ci && return`, so nothing that runs under `run()`
# can ever reach it. Local advisories are warn-only and cannot fail the ladder, so the
# assertion is on the warning TEXT.
run_local() { (cd "$1" && env -u CI scripts/ladder.sh --guards-only 2>&1); }

# --- assertions -------------------------------------------------------------
report() { # <ok|no> <name> <detail...>
	local elapsed=${FIXTURE_ELAPSED_SECONDS:-$((SECONDS - REPORT_STARTED))}
	record_timing "$2" "$elapsed"
	if [ "$1" = ok ]; then
		PASSED=$((PASSED + 1))
		printf 'ok %03d - %ss - %s\n' "$((PASSED + FAILED))" "$elapsed" "$2" >&2
	else
		FAILED=$((FAILED + 1))
		shift
		printf '  FAIL %s\n' "$1" >&2
		shift
		[ $# -gt 0 ] && printf '%s\n' "$*" | sed 's/^/       /' >&2
	fi
	unset FIXTURE_ELAPSED_SECONDS
	REPORT_STARTED=$SECONDS
}

# Exercise the timing bookkeeping without recursively running this expensive suite. Fixed
# whole-second samples prove threshold filtering and top-three ordering; the range check
# proves an oversized digit string is rejected before `test -ge` can diagnose and continue.
timing_diagnostics_self_test() {
	local out
	out=$(
		SLOW_FIXTURE_SECONDS=10
		SUITE_STARTED=$SECONDS
		SLOWEST_SECONDS=(-1 -1 -1)
		SLOWEST_NAMES=('' '' '')
		record_timing below-threshold 9
		record_timing second 12
		record_timing first 14
		record_timing third 11
		print_timing_summary
	)
	if grep -q 'below-threshold' <<<"$out"; then
		report no "timing diagnostics filter and summarize fixtures" \
			"a below-threshold fixture was printed" "$out"
	elif ! grep -qF 'SLOW 12s - second' <<<"$out" ||
		! grep -qE '^timing: [0-9]+s total; slowest fixtures:$' <<<"$out" ||
		[ "$(printf '%s\n' "$out" | tail -3)" != $'  14s - first\n  12s - second\n  11s - third' ]; then
		report no "timing diagnostics filter and summarize fixtures" \
			"threshold or slowest-three output is wrong" "$out"
	elif slow_threshold_valid 9999999999; then
		report no "timing diagnostics filter and summarize fixtures" \
			"an out-of-range threshold was accepted"
	else
		report ok "timing diagnostics filter and summarize fixtures"
	fi
}

expect_pass() { # <name> <dir>
	local out rc started=$SECONDS
	out=$(run "$2")
	rc=$?
	FIXTURE_ELAPSED_SECONDS=$((SECONDS - started))
	if [ "$rc" -eq 0 ]; then report ok "$1"; else report no "$1" "expected exit 0, got $rc" "$out"; fi
}

# A pass is not always the whole assertion. Where the verdict under test is "the ladder
# stays green AND says what it skipped", `expect_pass` alone would be satisfied by a rung
# that printed nothing — which is the exact defect the skip lines exist to close.
#
# The pattern callers pass must INCLUDE THE VERDICT WORD (`   skip  `, `   ok    `) when
# the verdict is what is on trial. This helper deliberately does not check the class
# itself: unlike `expect_warn`, its callers legitimately want different verdicts — two of
# them assert a `skip`, one asserts an `ok`. That makes the discipline the caller's, and
# it is not optional. Written without it, the first draft asserted only a bare substring,
# so demoting both new `skip` lines to `ok` left the suite green — and the rung then
# rendered an empty extension point identically to one that had done work, which is the
# entire property this unit exists to establish. D-027(a), repeated inside the fix for the
# defect D-027(a) records.
expect_pass_saying() { # <name> <dir> <grep-pattern, verdict word included>
	local out rc started=$SECONDS
	out=$(run "$2")
	rc=$?
	FIXTURE_ELAPSED_SECONDS=$((SECONDS - started))
	if [ "$rc" -ne 0 ]; then
		report no "$1" "expected exit 0, got $rc" "$out"
	elif ! grep -qF "$3" <<<"$out"; then
		report no "$1" "passed but the output never mentioned '$3'" "$out"
	else
		report ok "$1"
	fi
}

# The ladder's final verdict line, and nothing else. `✓`/`✗` in the first column is what
# marks one; `tail -1` because a fixture may legitimately print more than one over a run.
#
# This exists because "the output mentions X" and "the VERDICT mentions X" are different
# assertions, and only the second is worth making about a verdict line. Asserted with the
# bare-substring helpers, a change that moved the subject out of the verdict and into an
# `ok` line inside the guard section left the whole suite green — the reader's takeaway
# line lost the fact while every fixture agreed it was present. That is the same shape as
# the docstring on expect_pass_saying above, one level further in.
verdict_line() { # <ladder output>
	printf '%s\n' "$1" | grep -E '^(✓|✗) ' | tail -1
}

# Asserts on the verdict line specifically, with an explicit checked-NOTHING branch: a run
# that printed no verdict at all must be a failure and not a vacuous pass, which is the
# hollow-guard case the runbook requires an arm for.
expect_verdict() { # <name> <runner: run|run_full> <dir> <expected rc> <fixed substring>
	local out rc line started=$SECONDS
	out=$("$2" "$3")
	rc=$?
	FIXTURE_ELAPSED_SECONDS=$((SECONDS - started))
	line=$(verdict_line "$out")
	if [ "$rc" -ne "$4" ]; then
		report no "$1" "expected exit $4, got $rc" "$out"
	elif [ -z "$line" ]; then
		report no "$1" "the ladder printed NO verdict line at all" "$out"
	elif ! grep -qF "$5" <<<"$line"; then
		report no "$1" "the verdict line does not carry '$5'" "$line"
	else
		report ok "$1"
	fi
}

expect_runner_saying() { # <name> <runner> <dir> <expected rc> <fixed substring>
	local name=$1 runner=$2 d=$3 want_rc=$4 needle=$5 out rc started=$SECONDS
	out=$($runner "$d")
	rc=$?
	FIXTURE_ELAPSED_SECONDS=$((SECONDS - started))
	if [ "$rc" -eq "$want_rc" ] && grep -qF "$needle" <<<"$out"; then
		report ok "$name"
	else
		report no "$name" "expected exit $want_rc and '$needle'; got exit $rc" "$out"
	fi
}

expect_fail() { # <name> <dir> <grep-pattern>
	local out rc started=$SECONDS
	out=$(run "$2")
	rc=$?
	FIXTURE_ELAPSED_SECONDS=$((SECONDS - started))
	if [ "$rc" -eq 0 ]; then
		report no "$1" "expected a failure, ladder passed" "$out"
	elif ! grep -qF "$3" <<<"$out"; then
		report no "$1" "failed as expected but the message never mentioned '$3'" "$out"
	else
		report ok "$1"
	fi
}

# Asserts THREE things, and the third was missing for as long as this helper existed: exit
# 0, the expected text, and that a WARN line was actually printed. Without the last one the
# name was a lie — the text it greps for can be an `ok` line, so turning the soft-cap `warn`
# into an `ok` left every expect_warn fixture green. That matters most for the landing
# check's edit branch, which permits a shrink ONLY because the size warning stays armed: the
# single property making that branch safe was verified by nothing.
expect_warn() { # <name> <dir> <grep-pattern>
	local out rc started=$SECONDS
	out=$(run "$2")
	rc=$?
	FIXTURE_ELAPSED_SECONDS=$((SECONDS - started))
	if [ "$rc" -ne 0 ]; then
		report no "$1" "expected exit 0 with a warning, got $rc" "$out"
	elif ! grep -q '^   WARN ' <<<"$out"; then
		report no "$1" "expected a WARN line and there was none" "$out"
	elif ! grep -qF "$3" <<<"$out"; then
		report no "$1" "no output mentioning '$3'" "$out"
	else
		report ok "$1"
	fi
}

filler() { head -c "$1" /dev/zero | tr '\0' 'x'; }

# A runtime-generated AKIA-shaped token (D-004: never store a literal one). Bounded
# read then slice — `tr </dev/urandom | head -c N` leaves tr writing into a pipe head
# has closed, which printed `tr: write error: Broken pipe` three times per suite run.
akia_token() {
	local pool=''
	while [ "${#pool}" -lt 16 ]; do
		pool=$pool$(head -c 512 /dev/urandom | LC_ALL=C tr -dc 'A-Z0-9')
	done
	printf 'AKIA%s' "${pool:0:16}"
}

# =============================================================================
printf 'ladder guard fixtures\n'
timing_diagnostics_self_test

# --- baseline
d=$(mk baseline)
started=$SECONDS
out=$(run "$d")
rc=$?
FIXTURE_ELAPSED_SECONDS=$((SECONDS - started))
if [ "$rc" -eq 0 ] &&
	grep -qF "   skip  scripts/command-guard.sh and scripts/redact.sh self-tests already covered by fixture suite" <<<"$out" &&
	grep -qF "   skip  shipped-script manifest check already covered by fixture suite" <<<"$out"; then
	report ok "clean optimized fixture passes with both loud skip verdicts"
else
	report no "clean optimized fixture passes with both loud skip verdicts" \
		"expected exit 0 and both fixture-suite skip lines; got $rc" "$out"
fi

# --- STATE size band
d=$(mk state_hard)
{
	echo
	filler $((17 * 1024))
} >>"$d/docs/STATE.md"
expect_fail "STATE over the hard cap fails" "$d" "hard cap"

d=$(mk state_warn)
{
	echo
	filler $((15 * 1024))
} >>"$d/docs/STATE.md"
expect_warn "STATE over the soft cap warns only" "$d" "soft cap"

# Landing check, one fixture per branch. Sizes are set with `state_bytes` — grow past
# the target with filler, then truncate to an EXACT byte count — so every shrink these
# assert on is what it is by construction, not by however long the fixture's STATE.md
# prose happens to be. A margin that depends on the base file is a flake waiting for
# someone to reword the fixture (D-024).
#
# The filler is sized FROM the request, and the result is checked. A fixed 18 KB of filler
# was the first form and it is the same defect one level up: `head -c` on a file shorter
# than the request silently yields a shorter file and exits 0, so the first fixture that
# asked for a size past the filler — a hard-cap landing case is the obvious one — would
# have asserted against a size it never got, and passed.
state_bytes() { # <dir> <bytes> — leaves docs/STATE.md exactly <bytes> long
	local f=$1/docs/STATE.md have got
	have=$(wc -c <"$f")
	if [ "$2" -le "$have" ]; then
		printf 'FIXTURE ERROR: STATE.md is already %s bytes; cannot grow it to %s\n' "$have" "$2" >&2
		exit 1
	fi
	{
		echo
		filler $(($2 - have))
	} >>"$f"
	head -c "$2" "$f" >"$1/docs/STATE.tmp" && mv "$1/docs/STATE.tmp" "$f"
	got=$(wc -c <"$f")
	if [ "$got" != "$2" ]; then
		printf 'FIXTURE ERROR: STATE.md is %s bytes, wanted %s\n' "$got" "$2" >&2
		exit 1
	fi
}

# Branch 1 — a shrink that crosses from above the soft cap to below it must reach the
# floor, not stop in the debounce band. This is the Goodhart hole the size thresholds
# alone leave, and the branch split must not reopen it.
d=$(mk state_landing_bad)
state_bytes "$d" $((15 * 1024))
(cd "$d" && git commit -qam "grow past the soft cap")
head -c $((11 * 1024)) "$d/docs/STATE.md" >"$d/docs/STATE.tmp" && mv "$d/docs/STATE.tmp" "$d/docs/STATE.md"
# Greps branch 1's OWN wording, not the "stops short" both failing branches share: with the
# shared phrase, rewriting branch 1's message in branch 3's words left the suite green, so
# the fixture could not tell which branch had fired.
expect_fail "micro-trim that crosses below the cap but misses the floor fails" "$d" "crossed below the soft cap but stops short"

# Branch 3 — the same hole one band higher: a compression pass that never crosses below
# the cap. If the check only fired on a crossing, grow-to-15.5 / trim-to-14.2 would
# repeat forever under a mere warning. 1.5 KB lost, against a 1 KB delta.
d=$(mk state_landing_above_warn)
state_bytes "$d" $((16 * 1024))
(cd "$d" && git commit -qam "grow well past the soft cap")
head -c $((14848)) "$d/docs/STATE.md" >"$d/docs/STATE.tmp" && mv "$d/docs/STATE.tmp" "$d/docs/STATE.md"
expect_fail "a trim that stops short while still over the cap fails" "$d" "unfinished compression pass"

# Branch 2 — the defect this split exists to fix. A 100-byte deletion above the cap is a
# typo fix or a closed queue item, not a compression pass that stopped short; failing it
# leaves padding the file back as the only compliant move. Allowed, and the size warning
# above it stays armed, which is what `expect_warn` is checking alongside the branch line.
d=$(mk state_landing_edit)
state_bytes "$d" $((15 * 1024))
(cd "$d" && git commit -qam "grow past the soft cap")
head -c $((15 * 1024 - 100)) "$d/docs/STATE.md" >"$d/docs/STATE.tmp" && mv "$d/docs/STATE.tmp" "$d/docs/STATE.md"
expect_warn "a small edit above the cap is allowed and says so" "$d" "edit above the soft cap (shrank 100 bytes"

# The delta's plumbing, both directions. Neither the script's default nor the config read
# was exercised by anything above: every fixture conf sets the key, so deleting the default
# from the script left the suite green — while an adopter upgrading on an existing amh.conf,
# which cannot have the key, would hit an unbound variable under `set -u` and abort the
# ladder mid-run. And with the delta hardcoded back to a literal, the suite stayed green too.
d=$(mk state_delta_default)
grep -v '^STATE_EDIT_DELTA_BYTES=' "$d/amh.conf" >"$d/t" && mv "$d/t" "$d/amh.conf"
state_bytes "$d" $((15 * 1024))
(cd "$d" && git commit -qam "grow past the soft cap")
head -c $((15 * 1024 - 100)) "$d/docs/STATE.md" >"$d/docs/STATE.tmp" && mv "$d/docs/STATE.tmp" "$d/docs/STATE.md"
expect_warn "a conf without the delta key falls back to the shipped default" "$d" "edit above the soft cap (shrank 100 bytes"

# Same 100-byte shrink, a delta of 64: it must now read as a compression pass. This is what
# proves the value comes from the config rather than from a constant in the script.
d=$(mk state_delta_configured)
sed -i 's/^STATE_EDIT_DELTA_BYTES=.*/STATE_EDIT_DELTA_BYTES=64/' "$d/amh.conf"
state_bytes "$d" $((15 * 1024))
(cd "$d" && git commit -qam "grow past the soft cap")
head -c $((15 * 1024 - 100)) "$d/docs/STATE.md" >"$d/docs/STATE.tmp" && mv "$d/docs/STATE.tmp" "$d/docs/STATE.md"
expect_fail "the configured delta decides the branch" "$d" "unfinished compression pass"

# A malformed delta must be loud and must not silently decide the branch.
d=$(mk state_delta_malformed)
sed -i 's/^STATE_EDIT_DELTA_BYTES=.*/STATE_EDIT_DELTA_BYTES=1KB/' "$d/amh.conf"
state_bytes "$d" $((15 * 1024))
(cd "$d" && git commit -qam "grow past the soft cap")
head -c $((15 * 1024 - 100)) "$d/docs/STATE.md" >"$d/docs/STATE.tmp" && mv "$d/docs/STATE.tmp" "$d/docs/STATE.md"
expect_warn "a malformed delta warns and falls back rather than deciding quietly" "$d" "is not a positive byte count"

d=$(mk state_landing_good)
state_bytes "$d" $((15 * 1024))
(cd "$d" && git commit -qam "grow past the soft cap")
head -c $((5 * 1024)) "$d/docs/STATE.md" >"$d/docs/STATE.tmp" && mv "$d/docs/STATE.tmp" "$d/docs/STATE.md"
expect_pass "compression landing on the floor passes" "$d"

# --- STATE structure
d=$(mk state_section)
grep -v '^## Changelog' "$d/docs/STATE.md" >"$d/t" && mv "$d/t" "$d/docs/STATE.md"
expect_fail "a deleted required section fails" "$d" "is missing"

# Cheapest possible "compliance": keep the headers, delete everything under them.
d=$(mk state_empty_section)
awk '/^## Current state/{print; skip=1; next} skip && /^## /{skip=0} !skip' \
	"$d/docs/STATE.md" >"$d/t" && mv "$d/t" "$d/docs/STATE.md"
expect_fail "a required section emptied of content fails" "$d" "is empty"

# A section present TWICE. The file is a session's working memory, so two copies are two
# answers to one question — and this is not hypothetical: a scripted edit anchored on a
# string the preamble also contains spliced the whole document in after itself, shipped,
# and went green. Existence was satisfied twice over, the body check read the first copy,
# and every size check passed a file that had simply grown.
d=$(mk state_duplicated_section)
# Via a temp file, never `sed file >>file`: appending to the file being read moves EOF
# ahead of the reader and the pipeline copies until the disk fills.
sed -n '/^## Current state/,$p' "$d/docs/STATE.md" >"$d/dup.tmp"
{
	echo
	cat "$d/dup.tmp"
} >>"$d/docs/STATE.md"
rm -f "$d/dup.tmp"
expect_fail "a required section appearing twice fails" "$d" "'## Current state' appears more than once"

# ...and the check must not be scoped to the CONFIGURED sections, which is where the first
# draft left it. The heading the real incident keyed on was the owner queue — checked
# separately, and only for existence — so a guard over the required list would have closed
# the instance and left the class open. A splice duplicates whatever it duplicates.
d=$(mk state_duplicated_ownerq)
{
	echo
	echo '## Owner queue'
	echo '**Pending owner actions:** (none)'
} >>"$d/docs/STATE.md"
expect_fail "a duplicated Owner queue fails even though it is not a required section" "$d" \
	"'## Owner queue' appears more than once"

d=$(mk state_ownerq)
grep -v '^## Owner queue' "$d/docs/STATE.md" >"$d/t" && mv "$d/t" "$d/docs/STATE.md"
expect_warn "a deleted Owner queue warns" "$d" "Owner queue"

# --- ledger rollover
d=$(mk ledger_cap)
sed -i 's/^LEDGER_LINE_CAP=800/LEDGER_LINE_CAP=4/' "$d/amh.conf"
printf -- '- D-003: past the cap.\n' >>"$d/docs/LEDGER.md"
expect_fail "a row starting past the line cap fails" "$d" "past the"

# The cap gates LINES; the rung also REPORTS size, because read cost is what the cap
# stands in for and prose rows make the two drift. ALL THREE branches must carry the
# figure — one that appears only on the quiet path is missing exactly when the volume is
# growing, and the fail branch is the volume at its largest.
d=$(mk ledger_bytes)
expect_pass_saying "the passing rung reports the live volume's size, not just its lines" "$d" \
	"KB (grep it; a volume is retrieval storage, not a read)"

# A REAL size, not just the literal around it. The fixture ledger is a few hundred bytes,
# so every assertion above is equally satisfied by a script that hardcodes zero or measures
# the wrong file. Pad well past a kilobyte and demand the figure this volume's own byte
# count implies. The expectation is DERIVED, not hardcoded: the fixture ledger's length
# depends on how many rows the shipped scripts cite, so a literal here would rot silently.
# What this pins is that the number tracks the real file and is nonzero; the unit and the
# wording are pinned by the assertions above and below it.
d=$(mk ledger_bytes_nonzero)
i=0
while [ "$i" -lt 40 ]; do
	printf -- '  padding line long enough to push this volume past one kilobyte of prose.\n' >>"$d/docs/LEDGER.md"
	i=$((i + 1))
done
ledger_tenths=$(($(wc -c <"$d/docs/LEDGER.md") * 10 / 1024))
expect_pass_saying "the reported size is measured, not a hardcoded zero" "$d" \
	"lines, $((ledger_tenths / 10)).$((ledger_tenths % 10)) KB"

d=$(mk ledger_bytes_warn)
# Cap set to the volume's own length: inside the 90% warning band by construction, and
# with no row able to start past it — derived, because a hardcoded number silently moves
# into the FAIL branch the moment a shipped script cites one more row.
ledger_lines=$(wc -l <"$d/docs/LEDGER.md")
sed -i "s/^LEDGER_LINE_CAP=800/LEDGER_LINE_CAP=$ledger_lines/" "$d/amh.conf"
expect_warn "the approaching-cap warning reports the size too" "$d" "KB, approaching the ${ledger_lines}-line cap"

d=$(mk ledger_bytes_fail)
sed -i 's/^LEDGER_LINE_CAP=800/LEDGER_LINE_CAP=4/' "$d/amh.conf"
printf -- '- D-003: a row past the cap.\n' >>"$d/docs/LEDGER.md"
expect_fail "the rollover FAILURE reports the size too — that is the branch that needs it" "$d" \
	"KB), past the 4-line cap"


d=$(mk ledger_row_char_under_cap)
sed -i 's/^LEDGER_ROW_CHAR_CAP=2000/LEDGER_ROW_CHAR_CAP=120/' "$d/amh.conf"
printf -- '- D-003: short enough.\n' >>"$d/docs/LEDGER.md"
expect_pass_saying "a concise new ledger row under the byte-counted character cap passes" "$d" \
	"checked 1 new ledger row(s) against LEDGER_ROW_CHAR_CAP=120"

d=$(mk ledger_row_char_over_cap)
sed -i 's/^LEDGER_ROW_CHAR_CAP=2000/LEDGER_ROW_CHAR_CAP=80/' "$d/amh.conf"
printf -- '- D-003: long row. %s\n' "$(filler 120)" >>"$d/docs/LEDGER.md"
expect_fail "a new ledger row over the byte-counted character cap fails" "$d" \
	"over LEDGER_ROW_CHAR_CAP=80"

d=$(mk ledger_row_char_committed_over_cap)
sed -i 's/^LEDGER_ROW_CHAR_CAP=2000/LEDGER_ROW_CHAR_CAP=80/' "$d/amh.conf"
printf -- '- D-003: committed long row. %s\n' "$(filler 120)" >>"$d/docs/LEDGER.md"
(cd "$d" && git add amh.conf docs/LEDGER.md && git commit -qm long-ledger-history)
expect_pass "an already committed over-cap ledger row is historical and exempt" "$d"

d=$(mk ledger_row_char_superseded_pointer_existing)
sed -i 's/^LEDGER_ROW_CHAR_CAP=2000/LEDGER_ROW_CHAR_CAP=10/' "$d/amh.conf"
printf -- '  Superseded by D-999.\n' >>"$d/docs/LEDGER.md"
expect_pass "a sanctioned metadata-only supersession on an existing row is exempt" "$d"

# --- ledger volumes past Z, and what counts as a volume at all
#
# One continuation volume whose first row starts at line 5, for a fixture whose cap is 4.
# The suffix is BOTH the file name's and the row prefix's, and that they are the same
# string is precisely what a rollover diagnostic can get wrong, so the fixture derives
# both from one variable rather than spelling them separately. The base volume is never
# written this way: `mk` puts the rows the shipped scripts cite in it, and overwriting it
# would fail the citation guard in a case that is not about citations.
add_volume() { # <fixture dir> <suffix>
	local d=$1 s=$2
	{
		printf '# LEDGER — volume %s\n\n' "$s"
		printf 'padding\npadding\n'
		printf -- '- D%s-001: the first row of this volume.\n' "$s"
	} >"$d/docs/LEDGER_$s.md"
}

# A DENSE chain, which is what a real ledger is: volumes are only reachable through their
# predecessors, so a fixture that wants LEDGER_AA.md live has to have opened the
# twenty-six before it. The names come from brace expansion rather than from a copy of
# the odometer under test — a fixture that computes the answer the same way the code does
# agrees with it by construction.
add_chain() { # <fixture dir> <suffix>... — in order
	local d=$1 s
	shift
	for s in "$@"; do add_volume "$d" "$s"; done
}

# Past Z, with the twenty-six volumes that make AA reachable. Under the shell's collation
# LEDGER_AA.md sorts between LEDGER_A.md and LEDGER_B.md, so a rung taking the last glob
# match reports LEDGER_Z.md as live with AA sitting right there — every subsequent row
# invisible to a cap measuring the wrong file, quietly.
d=$(mk ledger_volume_past_z)
add_chain "$d" {A..Z} AA
expect_pass_saying "a two-letter volume is live once the chain reaches it" "$d" \
	"docs/LEDGER_AA.md: 5/800 lines"

# Membership is REACHABILITY, not spelling, and this is the case that settles it: an
# all-capitals stray file satisfies every name-shaped rule (`[A-Z]+`, and LONG, so it wins
# any length-first ordering) while belonging to no chain. Ranked, it pins the rung on a
# file nobody writes to and prints `ok` over a volume that is past its cap — one untracked
# one-line file switching the guard off. The cap must still fire on the real live volume.
d=$(mk ledger_volume_archive)
sed -i 's/^LEDGER_LINE_CAP=800/LEDGER_LINE_CAP=4/' "$d/amh.conf"
add_volume "$d" A
printf '# archived notes\n' >"$d/docs/LEDGER_ARCHIVE.md"
expect_fail "an all-caps stray file does not become the live volume" "$d" \
	"docs/LEDGER_A.md: a row STARTS at line 5"

# The same file, on a tree that is under its cap: the rung reports the chain's volume and
# says out loud that something volume-shaped is unreachable. A warning, not a failure —
# the rung cannot tell a deleted volume from a misnamed file, and it does not guess.
d=$(mk ledger_volume_orphan)
add_volume "$d" A
printf '# archived notes\n' >"$d/docs/LEDGER_ARCHIVE.md"
expect_warn "an unreachable volume-shaped file is named, not silently ignored" "$d" \
	"the chain does not reach: docs/LEDGER_ARCHIVE.md"

# A gap in the chain stops the walk. LEDGER_C.md here is not "the live volume with two
# missing"; it is unreachable, and its rows are read by nothing.
d=$(mk ledger_volume_gap)
add_chain "$d" A C
expect_pass_saying "the walk stops at the first gap" "$d" "docs/LEDGER_A.md: 5/800 lines"

# The base volume is where the chain starts, so its absence is not "no ledger yet" — that
# rendering is a skip, and a skip reads exactly like a pass. Citations are switched off in
# this fixture so the verdict under test is the only one on trial.
d=$(mk ledger_volume_no_base)
sed -i "s/^CITATION_SCAN_PATHS=.*/CITATION_SCAN_PATHS=''/" "$d/amh.conf"
add_volume "$d" A
rm "$d/docs/LEDGER.md"
expect_fail "a missing base volume with continuations fails instead of skipping" "$d" \
	"docs/LEDGER.md is missing while continuation volume(s) exist"

# The row pattern admits any number of volume letters. With the one-letter pattern a
# `DAA-` row matched nothing, so the cap could not fire however long the volume grew —
# the failure this whole scheme change exists to remove, and it was silent.
d=$(mk ledger_volume_multiletter_cap)
sed -i 's/^LEDGER_LINE_CAP=800/LEDGER_LINE_CAP=4/' "$d/amh.conf"
add_chain "$d" {A..Z} AA
expect_fail "a multi-letter row past the cap fails instead of passing invisibly" "$d" \
	"docs/LEDGER_AA.md: a row STARTS at line 5"

# The next volume's name is computed by carry, not looked up in a table that ends at Z.
# One case per transition the odometer has to get right; each is anchored on its own
# message, so no one of them can be deleted while a sibling covers for it. The chains are
# dense because the rung will not call an unreachable file live — which is why the ZZ case
# builds seven hundred volumes rather than one.
d=$(mk ledger_next_base)
sed -i 's/^LEDGER_LINE_CAP=800/LEDGER_LINE_CAP=4/' "$d/amh.conf"
printf -- '- D-003: past the cap.\n' >>"$d/docs/LEDGER.md"
expect_fail "the base volume rolls to _A / DA-" "$d" \
	"open docs/LEDGER_A.md, numbering from DA-001"

d=$(mk ledger_next_z)
sed -i 's/^LEDGER_LINE_CAP=800/LEDGER_LINE_CAP=4/' "$d/amh.conf"
add_chain "$d" {A..Z}
expect_fail "Z rolls to AA, which is where the old scheme simply stopped" "$d" \
	"open docs/LEDGER_AA.md, numbering from DAA-001"

d=$(mk ledger_next_az)
sed -i 's/^LEDGER_LINE_CAP=800/LEDGER_LINE_CAP=4/' "$d/amh.conf"
add_chain "$d" {A..Z} A{A..Z}
expect_fail "AZ rolls to BA — the carry advances the letter to its left, not the length" "$d" \
	"open docs/LEDGER_BA.md, numbering from DBA-001"

d=$(mk ledger_next_zz)
sed -i 's/^LEDGER_LINE_CAP=800/LEDGER_LINE_CAP=4/' "$d/amh.conf"
add_chain "$d" {A..Z} {A..Z}{A..Z}
expect_fail "ZZ rolls to AAA — a carry off the left end lengthens the suffix" "$d" \
	"open docs/LEDGER_AAA.md, numbering from DAAA-001"

# --- citations
d=$(mk cite_missing)
printf '# see D-099\n' >"$d/scripts/thing.sh"
expect_fail "a citation with no ledger row fails" "$d" "no such ledger row"

d=$(mk cite_unmarked)
printf '# see D-001\n' >"$d/scripts/thing.sh"
expect_fail "a cited row without its [cited] marker fails" "$d" "not marked"

d=$(mk cite_stale)
sed -i 's/^- D-002:/- D-002 [cited]:/' "$d/docs/LEDGER.md"
expect_fail "a [cited] marker with no citation fails" "$d" "no longer cited"

d=$(mk cite_ok)
printf '# see D-001\n' >"$d/scripts/thing.sh"
sed -i 's/^- D-001:/- D-001 [cited]:/' "$d/docs/LEDGER.md"
expect_pass "a citation with its marker passes" "$d"

# A file name with a space, in the citation guard this time. `secret_spacey` existed
# and this did not, so the word-split hole survived in one guard while being fixed in
# its neighbour — the fixture set marked the boundary of what anyone had thought about.
d=$(mk cite_spacey)
printf '# see D-099\n' >"$d/scripts/thing notes.sh"
expect_fail "a citation in a file name with a space is still seen" "$d" "no such ledger row"

d=$(mk cite_dupe)
printf -- '- D-001: a second row with the same number.\n' >>"$d/docs/LEDGER.md"
expect_fail "duplicate row numbers fail" "$d" "duplicate ledger row numbers"

# Citations resolve for a multi-letter volume, in both directions. Under the one-letter
# pattern `DAA-099` was not an unresolved citation, it was not a citation at all: the
# guard saw nothing to check and printed the same green it prints for a clean tree.
d=$(mk cite_multiletter_missing)
add_chain "$d" {A..Z} AA
printf '# see DAA-099\n' >"$d/scripts/thing.sh"
expect_fail "a multi-letter citation with no ledger row fails" "$d" "no such ledger row"

d=$(mk cite_multiletter_ok)
add_chain "$d" {A..Z} AA
sed -i 's/^- DAA-001:/- DAA-001 [cited]:/' "$d/docs/LEDGER_AA.md"
printf '# see DAA-001\n' >"$d/scripts/thing.sh"
expect_pass "a multi-letter citation with its marker passes" "$d"

# Rows are read from the CHAIN, not from every file whose name starts with the basename.
# Globbing, a scratch file could supply a row id the ledger already has — and the rung
# above would refuse to call that same file a volume. Two guards, one question, one answer.
d=$(mk cite_offchain_rows)
printf -- '- D-001: a duplicate row id in an unreachable file.\n' >"$d/docs/LEDGER_notes.md"
expect_pass "rows in an unreachable file are not read" "$d"

# The widened pattern matches whole words only. Unanchored it matches INSIDE one, so
# `README-12` reports an unresolved citation to DME-12 — an id that appears nowhere in the
# tree, which is a diagnostic nobody can act on. Same trap one letter down as the `XL-003`
# → L-003 defect this repository already shipped once.
d=$(mk cite_midword)
printf '# see README-12 and PRODUCTION-1 for details\n' >"$d/scripts/thing.sh"
expect_pass "an id-shaped substring inside a longer word is not a citation" "$d"

# The other direction, which is the real cost of the wider pattern and is stated in the
# upgrade notes: a STANDALONE token of that shape is a citation now, and it fails.
d=$(mk cite_standalone_token)
printf '# see DEBUG-2 for details\n' >"$d/scripts/thing.sh"
expect_fail "a standalone token of the id shape IS read as a citation" "$d" "no such ledger row"

# --- secret shapes
d=$(mk secret_plain)
tok=$(akia_token)
printf 'key = %s\n' "$tok" >"$d/scripts/deploy.sh"
started=$SECONDS
out=$(run "$d")
elapsed=$((SECONDS - started))
if grep -q 'credential-shaped' <<<"$out"; then
	# The diagnostic must name the file and the position and NOTHING else. A
	# regression to printing the matching line would defeat the whole guard.
	if grep -qF "$tok" <<<"$out"; then
		FIXTURE_ELAPSED_SECONDS=$elapsed
		report no "secret scan is value-free" "the diagnostic printed the token itself" "$out"
	else
		FIXTURE_ELAPSED_SECONDS=$elapsed
		report ok "secret scan is value-free"
	fi
	FIXTURE_ELAPSED_SECONDS=$elapsed
	report ok "secret-shaped string is caught"
else
	FIXTURE_ELAPSED_SECONDS=$elapsed
	report no "secret-shaped string is caught" "not flagged" "$out"
	FIXTURE_ELAPSED_SECONDS=$elapsed
	report no "secret scan is value-free" "(not reached)"
fi

# A file name with a space: the file list must be NUL-separated, or the scan
# silently skips it — a hole that looks exactly like a pass.
d=$(mk secret_spacey)
tok=$(akia_token)
printf 'key = %s\n' "$tok" >"$d/scripts/deploy notes.sh"
expect_fail "a secret in a file name with a space is still caught" "$d" "credential-shaped"

# --- repo-local guard extension point
d=$(mk guard_ok)
printf '#!/usr/bin/env bash\nexit 0\n' >"$d/scripts/guards/fine.sh"
expect_pass "a passing repo-local guard passes" "$d"

d=$(mk guard_bad)
printf '#!/usr/bin/env bash\necho "domain rule violated"\nexit 1\n' >"$d/scripts/guards/bad.sh"
expect_fail "a failing repo-local guard fails the ladder" "$d" "domain rule violated"

# The third verdict: exit 2 with a leading WARN marker is a warning, not a failure. It is
# for a rule that is usually right and occasionally, legitimately, wrong — the case where
# failing closed teaches the adopter to delete the guard instead of reading it.
d=$(mk guard_warn)
printf '#!/usr/bin/env bash\necho "WARN domain rule bent"\nexit 2\n' >"$d/scripts/guards/soft.sh"
expect_warn "a repo-local guard can warn instead of failing" "$d" "domain rule bent"

# ...and the marker is what separates a warning from a broken guard, because bash exits 2
# on a syntax error too. Without the marker requirement this fixture's guard — which cannot
# even parse — would report as a mild opinion and the ladder would stay green.
d=$(mk guard_warn_unmarked)
printf '#!/usr/bin/env bash\nif then fi\n' >"$d/scripts/guards/broken.sh"
expect_fail "a guard that exits 2 without the marker is a failure, not a warning" "$d" \
	"without the leading WARN marker"

# A warning longer than one line must not reach the transcript at column zero. The ladder's
# own verdict vocabulary lives there, so an unindented continuation lets a guard's output
# render as the ladder's — `   ok    ...` inside a warning would read as a passing rung.
d=$(mk guard_warn_multiline)
printf '#!/usr/bin/env bash\nprintf "WARN first line\\n   ok    injected verdict\\n"\nexit 2\n' \
	>"$d/scripts/guards/chatty.sh"
out=$(run "$d")
rc=$?
if [ "$rc" -ne 0 ]; then
	report no "a multi-line warning cannot forge a ladder verdict line" "expected exit 0, got $rc" "$out"
elif ! grep -q '^   WARN  chatty.sh — first line$' <<<"$out"; then
	report no "a multi-line warning cannot forge a ladder verdict line" "the warn line was not the guard's first line" "$out"
elif grep -q '^   ok    injected verdict$' <<<"$out"; then
	report no "a multi-line warning cannot forge a ladder verdict line" "the continuation printed at column zero" "$out"
elif ! grep -q '^            ok    injected verdict$' <<<"$out"; then
	report no "a multi-line warning cannot forge a ladder verdict line" "the continuation was dropped entirely" "$out"
else
	report ok "a multi-line warning cannot forge a ladder verdict line"
fi

# The marker is checked at the START of the output, not anywhere in it: a guard whose
# failure text happens to quote the word must still fail.
d=$(mk guard_warn_late_marker)
printf '#!/usr/bin/env bash\necho "rule violated, and no WARN applies"\nexit 2\n' >"$d/scripts/guards/late.sh"
expect_fail "the WARN marker counts only as the first thing printed" "$d" \
	"without the leading WARN marker"

# An extension point with nothing plugged into it. `rm -rf scripts/guards` used to leave
# this rung printing NOTHING AT ALL — no header, no line, no count — and the ladder green,
# which is indistinguishable from a set of guards that all passed. It stays a skip rather
# than a failure: an adopter who has earned no repo-local guards yet is not in error. What
# was wrong was the silence.
d=$(mk guard_dir_absent)
rm -rf "$d/scripts/guards"
expect_pass_saying "an absent scripts/guards says so instead of printing nothing" "$d" \
	"   skip  scripts/guards (directory absent) — 0 repo-local guard(s) ran"
# The section HEADER separately: it used to be printed only on finding a guard, so an
# empty extension point produced no header either and the rung vanished from the
# transcript entirely. Deleting the header alone leaves every other assertion green.
expect_pass_saying "the rung appears in the transcript even with nothing to run" "$d" \
	"▸ Repo-local guards"

# The directory present and empty is the same hole by another route: the loop found no
# file, so nothing was printed there either.
d=$(mk guard_dir_empty)
expect_pass_saying "an empty scripts/guards says so too" "$d" \
	"   skip  scripts/guards holds no *.sh — 0 repo-local guard(s) ran"

# Matched by the glob but not runnable — a broken symlink, or a directory named `x.sh`.
# `[ -f ]` alone dropped these silently and the count line then claimed the directory
# held no *.sh at all, which is not silence but an affirmative false.
d=$(mk guard_unrunnable)
ln -s ../../nowhere "$d/scripts/guards/dangling.sh"
mkdir -p "$d/scripts/guards/adirectory.sh"
expect_pass_saying "an entry that is not a runnable file is named, not dropped" "$d" \
	"   skip  dangling.sh is not a regular file — NOT run"
expect_pass_saying "...and the count says nothing ran rather than nothing matched" "$d" \
	"   skip  nothing in scripts/guards was runnable — 0 repo-local guard(s) ran"

# ...and the count must be a count, not a constant. Two guards, and the line must say two:
# a rung that reports how much work it did is only worth reading if the number moves.
d=$(mk guard_count)
printf '#!/usr/bin/env bash\nexit 0\n' >"$d/scripts/guards/one.sh"
printf '#!/usr/bin/env bash\nexit 0\n' >"$d/scripts/guards/two.sh"
expect_pass_saying "the rung reports how many guards actually ran" "$d" \
	"   ok    2 repo-local guard(s) ran"

# --- session-start.sh: the toolchain bootstrap's three silent skips
# None of these is reachable through the ladder — session-start.sh is the boot sequence,
# not a guard — so they get their own runner. All three used to produce output identical
# to a machine that is simply not remote.
#
# A bootstrap that ANNOUNCES ITSELF is what every case below turns on: the fixture writes
# one that prints a marker, so "did the bootstrap run" is a question about the transcript
# rather than about a side effect.
mk_bootstrap() { # mk_bootstrap <dir> <exit-code>
	printf '#!/usr/bin/env bash\nprintf "BOOTSTRAP RAN\\n"\nexit %s\n' "$2" >"$1/scripts/bootstrap.sh"
	chmod +x "$1/scripts/bootstrap.sh"
}

# `AMH-REMOTE` is a plausible thing to write in amh.conf — it matches the project's own
# naming — and `${!REMOTE_FLAG}` on it is a bad substitution: stderr, exit 0, no bootstrap,
# no word about it. amh.conf documents the flag as free-form, so nothing was stopping it.
d=$(mk ss_flag_invalid)
sed -i 's/^REMOTE_FLAG=.*/REMOTE_FLAG=AMH-REMOTE/' "$d/amh.conf"
mk_bootstrap "$d" 0
out=$(cd "$d" && env AMH_REMOTE=1 bash scripts/session-start.sh 2>&1)
# The whole banner, ⚠ and verdict word included — the fixture is asserting how LOUD the
# line is, so grepping a fragment of its middle would leave the loudness untested.
if grep -qF "· ⚠ REMOTE_FLAG 'AMH-REMOTE' is not a valid shell variable name — toolchain bootstrap SKIPPED" <<<"$out"; then
	report ok "an invalid REMOTE_FLAG is announced, not swallowed"
else
	report no "an invalid REMOTE_FLAG is announced, not swallowed" "no banner" "$out"
fi
# ...and it must actually have SKIPPED. The banner and the bootstrap running anyway would
# have satisfied the assertion above, which is why the fixture's bootstrap announces
# itself: the claim under test is a claim about what did not happen.
if grep -qF "BOOTSTRAP RAN" <<<"$out"; then
	report no "an invalid REMOTE_FLAG really does skip the bootstrap" "it ran anyway" "$out"
else
	report ok "an invalid REMOTE_FLAG really does skip the bootstrap"
fi
# ...and it must still be a WARNING. A boot hook that refuses to let the session start
# over a malformed config value is worse than the silent skip it replaces.
if (cd "$d" && env AMH_REMOTE=1 bash scripts/session-start.sh >/dev/null 2>&1); then
	report ok "an invalid REMOTE_FLAG is not fatal"
else
	report no "an invalid REMOTE_FLAG is not fatal" "session-start exited non-zero"
fi

# The exec bit must have no vote. This is D-019's shape in the file that boots everything:
# a bootstrap arriving 0644 from an archive extraction vanished without a line.
d=$(mk ss_bootstrap_noexec)
mk_bootstrap "$d" 0
chmod -x "$d/scripts/bootstrap.sh"
out=$(cd "$d" && env AMH_REMOTE=1 bash scripts/session-start.sh 2>&1)
if grep -qF "BOOTSTRAP RAN" <<<"$out"; then
	report ok "a non-executable bootstrap still runs"
else
	report no "a non-executable bootstrap still runs" "the bootstrap did not run" "$out"
fi

# A failing bootstrap is reported and the session continues — the property the bootstrap's
# own loud-but-non-fatal design depends on, asserted at the caller where it actually holds.
d=$(mk ss_bootstrap_fails)
mk_bootstrap "$d" 1
out=$(cd "$d" && env AMH_REMOTE=1 bash scripts/session-start.sh 2>&1)
rc=$?
if [ "$rc" -eq 0 ] && grep -qF "bootstrap reported a problem" <<<"$out"; then
	report ok "a failing bootstrap warns without killing the session"
else
	report no "a failing bootstrap warns without killing the session" "rc=$rc" "$out"
fi

# The flag set, and nothing to run. Legitimate — an adopter may have no toolchain to
# install — but a remote session skipping a configured step deserves its one line.
d=$(mk ss_bootstrap_absent)
out=$(cd "$d" && env AMH_REMOTE=1 bash scripts/session-start.sh 2>&1)
if grep -qF "does not exist"  <<<"$out"&& grep -qF "SKIPPED" <<<"$out"; then
	report ok "a missing bootstrap under the remote flag says so"
else
	report no "a missing bootstrap under the remote flag says so" "no line about it" "$out"
fi

# The negative control, and the reason the cases above are not just asserting that
# session-start.sh prints a lot: with the flag UNSET the bootstrap must not run at all.
d=$(mk ss_not_remote)
mk_bootstrap "$d" 0
out=$(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
if grep -qF "BOOTSTRAP RAN" <<<"$out"; then
	report no "a local session does not run the bootstrap" "it ran anyway" "$out"
else
	report ok "a local session does not run the bootstrap"
fi

# Starting a new session rearms the broad `.env` advisory. Without this cleanup,
# the first warning in one container lifetime spends the advisory for later sessions
# in the same repo, which contradicts the diagnostic's session-local promise.
d=$(mk ss_rearms_dotenv_advisory)
out=$(cd "$d" && scripts/command-guard.sh --command 'python3 -c "open('"'"'.env'"'"')"' 2>&1)
rc=$?
if [ "$rc" -eq 2 ] && grep -qF "This command mentions \`.env\`" <<<"$out"; then
	report ok "the first .env command gets the advisory"
else
	report no "the first .env command gets the advisory" "rc=$rc" "$out"
fi
if (cd "$d" && scripts/command-guard.sh --command 'python3 -c "open('"'"'.env'"'"')"' >/dev/null 2>&1); then
	report ok "the second interpreter .env command reaches normal rails"
else
	report no "the second interpreter .env command reaches normal rails" "it was still blocked"
fi
(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh >/dev/null 2>&1)
out=$(cd "$d" && scripts/command-guard.sh --command 'python3 -c "open('"'"'.env'"'"')"' 2>&1)
rc=$?
if [ "$rc" -eq 2 ] && grep -qF "This command mentions \`.env\`" <<<"$out"; then
	report ok "session-start rearms the one-time .env advisory"
else
	report no "session-start rearms the one-time .env advisory" "rc=$rc" "$out"
fi

# Every category of one-time advisory rearms, not just the one the reset was written for.
# The destructive-command advisory makes the same session-local promise as the `.env` one,
# and for as long as the bootstrap named a single category literally it kept that promise
# only for `.env`: a container's first `rm -rf` spent the advisory for every later session.
d=$(mk ss_rearms_destructive_advisory)
out=$(cd "$d" && scripts/command-guard.sh --command 'rm -rf tmp/build' 2>&1)
rc=$?
if [ "$rc" -eq 2 ] && grep -qF "This destructive filesystem command" <<<"$out"; then
	report ok "the first destructive command gets the advisory"
else
	report no "the first destructive command gets the advisory" "rc=$rc" "$out"
fi
if (cd "$d" && scripts/command-guard.sh --command 'rm -rf tmp/build' >/dev/null 2>&1); then
	report ok "the second destructive command reaches normal rails"
else
	report no "the second destructive command reaches normal rails" "it was still blocked"
fi
(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh >/dev/null 2>&1)
out=$(cd "$d" && scripts/command-guard.sh --command 'rm -rf tmp/build' 2>&1)
rc=$?
if [ "$rc" -eq 2 ] && grep -qF "This destructive filesystem command" <<<"$out"; then
	report ok "session-start rearms the one-time destructive advisory"
else
	report no "session-start rearms the one-time destructive advisory" "rc=$rc" "$out"
fi

# The rearm expands a pattern, and amh.conf is sourced before it runs — so an adopter's
# `set -f` (or a GLOBIGNORE covering /tmp) would leave the pattern unexpanded and `rm -f`
# would swallow the literal without a word. A rail switched off in silence by a config key
# nobody connected to it is the same class of defect as the single-category reset itself.
d=$(mk ss_rearms_advisory_under_noglob)
printf 'set -f\n' >>"$d/amh.conf"
(cd "$d" && scripts/command-guard.sh --command 'rm -rf tmp/build' >/dev/null 2>&1)
(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh >/dev/null 2>&1)
out=$(cd "$d" && scripts/command-guard.sh --command 'rm -rf tmp/build' 2>&1)
rc=$?
if [ "$rc" -eq 2 ] && grep -qF "This destructive filesystem command" <<<"$out"; then
	report ok "the rearm survives a noglob amh.conf"
else
	report no "the rearm survives a noglob amh.conf" "rc=$rc" "$out"
fi

# --- the protocol pointer names only documents that exist
# Not every install profile ships a runbook — the smallest one, which is the default,
# deliberately does not. The banner used to name docs/RUNBOOK.md unconditionally, so the
# first thing a fresh session in such a repo read was a pointer to a file that is not
# there. The adopter cannot fix it either: this script is overwritten on every upgrade.
#
# Both directions, because each is separately silent: a banner that never names the runbook
# would satisfy the absent case while breaking every repo that has one.
d=$(mk ss_no_runbook)
out=$(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
if grep -qF "docs/RUNBOOK.md" <<<"$out"; then
	report no "the protocol pointer omits a runbook the repo does not have" "it named it anyway" "$out"
else
	report ok "the protocol pointer omits a runbook the repo does not have"
fi

d=$(mk ss_with_runbook)
printf '# RUNBOOK\n' >"$d/docs/RUNBOOK.md"
out=$(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
if grep -qF "playbook in docs/RUNBOOK.md" <<<"$out"; then
	report ok "the protocol pointer names the runbook when there is one"
else
	report no "the protocol pointer names the runbook when there is one" "it did not" "$out"
fi

# --- the release-window line: tagged, untagged, off, and misconfigured
# The line exists because merged-but-untagged is invisible to every check in the harness: a
# version-consistency guard compares strings inside the tree, and a release workflow keyed on a
# tag runs after the tag exists (DA-010). A queue item about the tagging then outlives the
# tagging itself, and the next session restates it as pending (DA-011).
#
# Four cases, because each failure is silent in a different direction: claiming a tag that is
# not there, missing one that is, printing at all for the adopter who never set the keys, and
# asserting a version out of a file that does not exist.
mk_git_repo() { # <dir> [tag]
	git -C "$1" init -q 2>/dev/null || return 1
	git -C "$1" -c user.email=f@example.invalid -c user.name=fixture \
		commit -q --allow-empty -m fixture 2>/dev/null || return 1
	[ -z "${2:-}" ] || git -C "$1" tag "$2" 2>/dev/null || return 1
}

# An `origin` that is a local directory, so the remote branches are exercised with no network:
# <dir> gets an origin pointing at a bare repo that either carries the tag or does not.
mk_origin_with() { # <dir> <tag-or-empty>
	local src="$WORK/origin_src_$$_$RANDOM" bare="$WORK/origin_$$_$RANDOM.git"
	mkdir -p "$src" || return 1
	mk_git_repo "$src" "${2:-}" || return 1
	git clone -q --bare "$src" "$bare" 2>/dev/null || return 1
	git -C "$1" remote add origin "$bare" 2>/dev/null || return 1
}

set_release_keys() { # <dir> <version-file-path> <prefix>
	printf 'VERSION_FILE=%s\nRELEASE_TAG_PREFIX=%s\n' "$2" "$3" >>"$1/amh.conf"
}

d=$(mk ss_release_tagged)
printf '3.1.0\n' >"$d/VERSION"
set_release_keys "$d" VERSION v
d2=$(mk ss_release_untagged)
printf '3.1.0\n' >"$d2/VERSION"
set_release_keys "$d2" VERSION v

d3=$(mk ss_release_on_origin)
printf '3.1.0\n' >"$d3/VERSION"
set_release_keys "$d3" VERSION v
d4=$(mk ss_release_unreachable)
printf '3.1.0\n' >"$d4/VERSION"
set_release_keys "$d4" VERSION v

# These need real repos to hold real refs, and a local bare repo as `origin` so the remote
# branches run with no network. `git` is in the harness's stated baseline, so this is not a tool
# gate like the hasher one — but a sandbox that forbids `git init`/`clone` or has no committer
# identity would fail them for a reason that is not the code, so they are gated and COUNTED.
if ! mk_git_repo "$d" v3.1.0 || ! mk_git_repo "$d2" ||
	! mk_git_repo "$d3" || ! mk_origin_with "$d3" v3.1.0 ||
	! mk_git_repo "$d4" || ! mk_origin_with "$d2" ""; then
	printf '  SKIP 4 release-window case(s): could not build the git fixture repos (init, clone or commit failed)\n' >&2
else
	# (a) Local ref present: the fast path answers without touching the network.
	out=$(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
	if grep -qF "tag v3.1.0 is in this clone" <<<"$out"; then
		report ok "an existing local release tag is reported as present"
	else
		report no "an existing local release tag is reported as present" "it was not" "$out"
	fi

	# (b) The state the line EXISTS for: no such tag anywhere. Only this one may say UNRELEASED.
	out=$(cd "$d2" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
	if grep -qF "NO tag v3.1.0 exists on origin — UNRELEASED" <<<"$out"; then
		report ok "a version with no tag on origin is reported as unreleased"
	else
		report no "a version with no tag on origin is reported as unreleased" "it was not" "$out"
	fi

	# (c) The regression this suite previously LOCKED IN: tagged on origin, absent locally.
	# A clone that never fetched tags is the steady state, not an incident, and reporting it as
	# unreleased made the line cry wolf on every session in the repo that ships it. It must
	# name the tag as existing and must NOT say UNRELEASED.
	out=$(cd "$d3" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
	if grep -qF "tag v3.1.0 exists on origin"  <<<"$out"&&
		! grep -qF "UNRELEASED" <<<"$out"; then
		report ok "a tag on origin but not in the clone is reported as existing, not as unreleased"
	else
		report no "a tag on origin but not in the clone is reported as existing, not as unreleased" \
			"it was not, or it cried unreleased" "$out"
	fi

	# (d) Cannot ask is not an answer. A repo with no origin at all must say so and must make no
	# claim in either direction — the failure being refused is "unreachable" rendering as "absent".
	out=$(cd "$d4" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
	if grep -qF "not be reached to check"  <<<"$out"&&
		! grep -qF "UNRELEASED" <<<"$out"; then
		report ok "an unreachable origin is reported as unreachable, with no tag claim"
	else
		report no "an unreachable origin is reported as unreachable, with no tag claim" \
			"it claimed something anyway" "$out"
	fi
fi

# The empty-version branch, which had no fixture for as long as the comment above it claimed the
# case was refused. Without the branch the banner prints "says , and NO tag v ... UNRELEASED" —
# a confident verdict about a version nobody stated.
d=$(mk ss_release_empty_version)
: >"$d/VERSION"
set_release_keys "$d" VERSION v
out=$(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
if grep -qF "no version on its first line"  <<<"$out"&&
	! grep -qF "UNRELEASED" <<<"$out"; then
	report ok "an empty VERSION_FILE is reported and no tag claim is made"
else
	report no "an empty VERSION_FILE is reported and no tag claim is made" "it claimed one anyway" "$out"
fi

# Half-configuration is a typo, and silence would render it identically to the adopter who
# deliberately set neither key.
d=$(mk ss_release_half_configured)
printf '3.1.0\n' >"$d/VERSION"
printf 'VERSION_FILE=VERSION\n' >>"$d/amh.conf"
out=$(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
if grep -qF "needs BOTH VERSION_FILE and RELEASE_TAG_PREFIX" <<<"$out"; then
	report ok "setting one release key and not the other is reported"
else
	report no "setting one release key and not the other is reported" "it was silent" "$out"
fi

# A directory is not a missing file, and saying so costs one branch.
d=$(mk ss_release_dir_version)
set_release_keys "$d" docs v
out=$(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
if grep -qF "is a directory, not a file" <<<"$out"; then
	report ok "a VERSION_FILE that is a directory says so"
else
	report no "a VERSION_FILE that is a directory says so" "it reported something else" "$out"
fi

# --- session-start.sh: the runtime inventory (AMH ledger row DA024)
# The vocabulary is the behaviour under test here, not the presence check. Two states per
# list, and the asymmetry between the lists is what the refused capability manifest was
# refused FOR: a tool probe runs, an adapter file only ever states an intention.

# Both keys empty (and an adopter's amh.conf predating them entirely) must print no line at
# all. This is the upgrade path: `set -u` plus an unset key would kill the whole banner, and
# a repo that declares neither list gets no inventory rather than an empty one.
d=$(mk ss_inventory_off)
out=$(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
if ! grep -qE '^· (tools|adapters):' <<<"$out" &&
	grep -qF "AMH session start" <<<"$out"; then
	report ok "unset inventory keys print no inventory and do not kill the banner"
else
	report no "unset inventory keys print no inventory and do not kill the banner" "a line appeared or the banner died" "$out"
fi

# A tool that IS on PATH is `observed` — and the resolved path must never appear. `command -v`
# prints /usr/bin/sh or /home/<someone>/bin/sh, and a username in the transcript is a leak
# with no diagnostic value (P17).
d=$(mk ss_inventory_tool_present)
printf "REQUIRED_TOOLS='sh'\n" >>"$d/amh.conf"
out=$(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
if grep -qxF "· tools: sh observed"  <<<"$out"&&
	! grep -qE '· tools:.*/' <<<"$out"; then
	report ok "a present tool is observed, by name only"
else
	report no "a present tool is observed, by name only" "state wrong or a path leaked" "$out"
fi

# A tool that is NOT on PATH is `unavailable` — the probe ran and answered. Distinct from the
# adapter case below, and the fixture asserts the exact word because the whole point of the
# vocabulary is that these two are not interchangeable.
d=$(mk ss_inventory_tool_absent)
printf "REQUIRED_TOOLS='amh-no-such-tool-xyz'\n" >>"$d/amh.conf"
out=$(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
if grep -qxF "· tools: amh-no-such-tool-xyz unavailable" <<<"$out"; then
	report ok "an absent tool is unavailable"
else
	report no "an absent tool is unavailable" "state wrong" "$out"
fi

# A tool name that resolves as a shell FUNCTION or builtin rather than a PATH entry must read
# `unavailable`. `say` is this script's own output helper, defined ~180 lines above the probe,
# so under `command -v` — which resolves functions, builtins and aliases before it looks at
# PATH — the banner reported its own internals as an installed tool, and `printf` as `observed`
# on a machine with no binaries at all. `observed` is the state whose whole warrant is that the
# answer is a fact about the ENVIRONMENT; a builtin makes it a fact about this bash. The
# fixtures pin `unknown`→`unavailable` in the adapter direction; this pins the symmetric
# hazard, a non-fact becoming `observed`.
d=$(mk ss_inventory_tool_is_a_shell_function)
printf "REQUIRED_TOOLS='say'\n" >>"$d/amh.conf"
out=$(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
if grep -qxF "· tools: say unavailable" <<<"$out"; then
	report ok "a shell function is unavailable, never observed"
else
	report no "a shell function is unavailable, never observed" "the probe resolved a non-PATH name" "$out"
fi

# A whitespace-only value splits to nothing. Gating the line on the raw config value rather than
# on what the loop produced prints a bare header — and for adapters, two lines of gloss
# explaining zero states.
d=$(mk ss_inventory_blank_value)
printf "REQUIRED_TOOLS='   '\nADAPTER_FILES='   '\n" >>"$d/amh.conf"
out=$(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
if ! grep -qE '^· (tools|adapters):' <<<"$out" &&
	! grep -qF "never observed firing" <<<"$out"; then
	report ok "a whitespace-only list prints no header and no gloss"
else
	report no "a whitespace-only list prints no header and no gloss" "an empty inventory was printed" "$out"
fi

# A glob in the list must not expand against the working directory. Unguarded, `set -f`
# absent, `REQUIRED_TOOLS='*'` reports every file in the repo root as a missing tool.
d=$(mk ss_inventory_glob)
printf "REQUIRED_TOOLS='*'\n" >>"$d/amh.conf"
out=$(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
if grep -qF "· tools: * unavailable"  <<<"$out"&&
	! grep -qF "amh.conf unavailable" <<<"$out"; then
	report ok "a glob in REQUIRED_TOOLS is not expanded against the tree"
else
	report no "a glob in REQUIRED_TOOLS is not expanded against the tree" "it globbed" "$out"
fi

# A present adapter file is `configured` and must NEVER be `observed`. Presence is a request
# for an integration; nothing in this script can see a hook fire, which is why the lifecycle
# probe layer was refused rather than deferred.
d=$(mk ss_inventory_adapter_present)
mkdir -p "$d/.agent-a"
printf '{}\n' >"$d/.agent-a/settings.json"
printf "ADAPTER_FILES='.agent-a/settings.json'\n" >>"$d/amh.conf"
out=$(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
if grep -qxF "· adapters: .agent-a/settings.json configured"  <<<"$out"&&
	! grep -qE '^· adapters:.*observed' <<<"$out"; then
	report ok "a present adapter is configured, never observed"
else
	report no "a present adapter is configured, never observed" "state wrong" "$out"
fi

# An ABSENT adapter file is `unknown`, never `unavailable`. This is the assertion the whole
# section exists for: an adapter configured at user level is invisible from inside the tree,
# so `unavailable` would be a claim about the world derived from a fact about the repo, and
# `unknown` is never translated into `unavailable`, `disabled` or `safe`.
d=$(mk ss_inventory_adapter_absent)
printf "ADAPTER_FILES='.agent-a/settings.json'\n" >>"$d/amh.conf"
out=$(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
if grep -qxF "· adapters: .agent-a/settings.json unknown"  <<<"$out"&&
	! grep -qE '^· adapters:.*unavailable' <<<"$out"; then
	report ok "an absent adapter is unknown, never unavailable"
else
	report no "an absent adapter is unknown, never unavailable" "state wrong" "$out"
fi

# The gloss ships with the states. Without it `configured` reads as "the hook works", which
# is the single misreading this vocabulary exists to prevent.
d=$(mk ss_inventory_gloss)
printf "ADAPTER_FILES='.agent-b/config.toml'\n" >>"$d/amh.conf"
out=$(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
if grep -qF "never observed firing"  <<<"$out"&&
	grep -qF "Nothing reads these states." <<<"$out"; then
	report ok "the adapter states ship with their gloss"
else
	report no "the adapter states ship with their gloss" "the gloss is missing" "$out"
fi

# The two lists are independent: declaring one must not switch the other on.
d=$(mk ss_inventory_independent)
printf "REQUIRED_TOOLS='sh'\n" >>"$d/amh.conf"
out=$(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
if grep -qE '^· tools:'  <<<"$out"&&
	! grep -qE '^· adapters:' <<<"$out"; then
	report ok "REQUIRED_TOOLS and ADAPTER_FILES are independent"
else
	report no "REQUIRED_TOOLS and ADAPTER_FILES are independent" "one switched on the other" "$out"
fi

# Only the first line is the version: a trailing note would otherwise be concatenated into a tag
# name no release can match, and the banner would report that mangled string as unreleased.
d=$(mk ss_release_multiline_version)
printf '3.1.0\nnotes about the release\n' >"$d/VERSION"
set_release_keys "$d" VERSION v
out=$(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
if grep -qF "says 3.1.0"  <<<"$out"&& ! grep -qF "notes" <<<"$out"; then
	report ok "only the first line of VERSION_FILE is read"
else
	report no "only the first line of VERSION_FILE is read" "the rest leaked into the version" "$out"
fi

# The negative control, and the one that protects every existing adopter: amh.conf files
# written before these keys existed leave them empty, and an empty key means silence — not a
# line about a tag prefix nobody chose. A VERSION file is present to make the case sharp.
d=$(mk ss_release_off)
printf '3.1.0\n' >"$d/VERSION"
out=$(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
if grep -qiF "release:" <<<"$out"; then
	report no "the release line stays off when the keys are unset" "it printed anyway" "$out"
else
	report ok "the release line stays off when the keys are unset"
fi

# Misconfiguration is loud and makes no claim about the tag either way. The failure mode being
# refused is a banner that reads a missing file, gets an empty version, and announces that the
# tag for "" is absent.
d=$(mk ss_release_no_version_file)
set_release_keys "$d" harness/VERSION v
out=$(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
if grep -qF "does not exist — release line SKIPPED"  <<<"$out"&&
	! grep -qF "in this clone" <<<"$out"; then
	report ok "a VERSION_FILE that does not exist is reported and no tag claim is made"
else
	report no "a VERSION_FILE that does not exist is reported and no tag claim is made" \
		"it claimed something anyway" "$out"
fi

# --- the secret scan cannot be switched off by a file mode
# The scan IS the repo's entire secret defence (D-004), so the ways it can vanish are
# worth more fixtures than the ways it can fire. Losing the exec bit — an archive
# download, core.fileMode=false, a stray chmod — used to turn it into `skip` and left
# the ladder green with a live credential in the tree.
d=$(mk secret_noexec)
tok=$(akia_token)
printf 'key = %s\n' "$tok" >"$d/scripts/deploy.sh"
chmod -x "$d/scripts/redact.sh"
expect_fail "a non-executable redact.sh still scans" "$d" "credential-shaped"

d=$(mk secret_absent)
rm -f "$d/scripts/redact.sh"
expect_fail "a missing redact.sh fails rather than skips" "$d" "IS this repo's secret scan"

# --- rail self-tests (the rung that catches the above)
# The unmodified copied rails really run once before any mutation cases. This is the suite's
# coverage boundary: ordinary fixtures may skip the repeated work only because this assertion
# proves that the actual dispatcher and actual command-guard self-test ran successfully.
d=$(mk_unmodified rail_baseline)
expect_runner_saying "unmodified copied rails run their real self-tests once" run_rails "$d" 0 \
	"   ok    scripts/command-guard.sh"

# Mutation: a rail whose self-test fails must turn the ladder red. Without this the
# whole section could print nothing and no fixture would notice.
d=$(mk_unmodified rail_regressed)
# Mutate the fixture matrix itself, not the tail of the file: a function appended
# after the dispatcher is defined too late to ever run, which is a mutation that
# proves nothing.
sed -i 's/^\tst_allowed .cat README.md./\tst_allowed "cat .env"/' "$d/scripts/command-guard.sh"
expect_runner_saying "a regressed rail self-test fails the ladder" run_rails "$d" 1 \
	"self-test failed"

d=$(mk_unmodified rail_noexec)
sed -i 's/^\tst_allowed .cat README.md./\tst_allowed "cat .env"/' "$d/scripts/command-guard.sh"
chmod -x "$d/scripts/command-guard.sh"
expect_runner_saying "a non-executable rail is still self-tested" run_rails "$d" 1 \
	"self-test failed"

d=$(mk_unmodified rail_missing)
rm -f "$d/scripts/command-guard.sh"
expect_runner_saying "a missing rail script loudly says that nothing self-tested it" run_rails "$d" 0 \
	"   skip  scripts/command-guard.sh is not a readable file — nothing self-tested it"

# --- shipped-script integrity
# The manifest is the only thing in an adopter's tree that can tell an upgraded script from
# an edited one. Every branch below is a different way for that answer to be wrong, and the
# two that matter most are the ones that must NOT be failures: an adopter with no manifest,
# and a machine with no hashing tool. Both stay green and both say so out loud.
if [ -z "$HASHER" ]; then
	printf '  SKIP 10 shipped-integrity case(s): no sha256sum or shasum on this machine, so no fixture manifest could be built\n' >&2
else
	d=$(mk_integrity integrity_ok)
	expect_pass_saying "an untouched tree matches the manifest and says how many it checked" "$d" \
		"   ok    4 shipped script(s) match the published hashes"

	# The whole point of the rung: a local edit to a shipped script. session-start.sh is the
	# subject because nothing else in this suite executes it during a `--guards-only` run, so
	# the only rung that can react is the one under test.
	d=$(mk_integrity integrity_edited)
	printf '\n# a local edit to a shipped script\n' >>"$d/scripts/session-start.sh"
	expect_fail "an edited shipped script fails against the published hash" "$d" \
		"does not match the hash the harness published for it"

	# Absence is not a failure — an adopter who upgraded by copying *.sh before the manifest
	# existed has none, and failing them for following the documented path would be a fix
	# billed to the person it broke. It is a WARN and not a `skip`, and `expect_warn` is what
	# asserts that: deleting the manifest is also the documented way to live with a deliberate
	# local patch, so it is the one off-switch an adopter reaches on purpose, and `skip` is
	# counted by nothing and vanishes from the summary line.
	d=$(mk_integrity integrity_absent)
	rm -f "$d/scripts/MANIFEST.sha256"
	expect_warn "an absent manifest warns that the rung checked nothing" "$d" \
		"   WARN  scripts/MANIFEST.sha256 is absent"

	# A manifest that outlived the script it names. Same signature as a deleted rung, which is
	# why it is a failure and not a skip.
	d=$(mk_integrity integrity_script_gone)
	rm -f "$d/scripts/session-start.sh"
	expect_fail "a manifest entry with no file behind it fails" "$d" \
		"which is not in this tree"

	# A manifest this cannot parse verifies nothing, so it must not be read past in silence.
	d=$(mk_integrity integrity_malformed)
	printf 'not-a-hash scripts/ladder.sh\n' >>"$d/scripts/MANIFEST.sha256"
	expect_fail "a malformed manifest line fails rather than being skipped over" "$d" \
		"is not a sha256 entry naming a shipped script"

	# ...and the degenerate case the parser makes possible: a file whose every line is a
	# comment parses cleanly, checks nothing, and would otherwise print `ok 0 shipped
	# script(s)`. A green earned by an empty manifest is the one verdict this rung may never
	# give.
	d=$(mk_integrity integrity_empty)
	printf '# nothing but a comment\n' >"$d/scripts/MANIFEST.sha256"
	expect_fail "a manifest listing no scripts fails instead of passing vacuously" "$d" \
		"lists no scripts"

	# Deleting ONE line excuses ONE script, and the entry for the ladder is the one deletion
	# that excuses the file deciding whether anything else is excused. Refused, or every other
	# verdict this rung gives is worth nothing — and this fixture is what stops a later
	# simplification from dropping the self-check as redundant.
	d=$(mk_integrity integrity_self_excused)
	grep -v ' scripts/ladder\.sh$' "$d/scripts/MANIFEST.sha256" >"$d/m" &&
		mv "$d/m" "$d/scripts/MANIFEST.sha256"
	printf '\n# a rung I quietly deleted\n' >>"$d/scripts/ladder.sh"
	expect_fail "a manifest that does not cover the ladder itself fails" "$d" \
		"does not cover scripts/ladder.sh"

	# The residue that leaves, asserted rather than left to be discovered: any OTHER line can
	# be removed, and the only signal is the count. The assertion is on the count, because a
	# count nobody reads is not a signal — and if this rung ever grows a way to refuse this
	# case, this fixture is what will notice.
	d=$(mk_integrity integrity_one_excused)
	grep -v ' scripts/session-start\.sh$' "$d/scripts/MANIFEST.sha256" >"$d/m" &&
		mv "$d/m" "$d/scripts/MANIFEST.sha256"
	printf '\n# a local edit nobody will hear about\n' >>"$d/scripts/session-start.sh"
	expect_pass_saying "an excused script is unreported except in the count, which drops" "$d" \
		"   ok    3 shipped script(s) match the published hashes"

	# A manifest entry may name a shipped script and nothing else. Left unconstrained the rung
	# will hash any path it is handed and then describe /etc/hostname as a shipped script the
	# harness will restore — a true hash comparison wrapped in a false account of what was
	# checked.
	d=$(mk_integrity integrity_stray_path)
	printf '%s  ../outside.sh\n' "$(fixture_sha256 "$d/scripts/ladder.sh")" \
		>>"$d/scripts/MANIFEST.sha256"
	expect_fail "a manifest entry pointing outside scripts/ is refused, not hashed" "$d" \
		"is not a sha256 entry naming a shipped script"

	# No hashing tool on PATH. This is AMH ledger row D019's shape again — the rung is switched
	# off by a property of the MACHINE, which is not its subject — so it warns rather than
	# skipping, and it must stay non-fatal. The PATH is CONSTRUCTED from the tools the ladder
	# needs rather than filtered, because subtracting the directory holding sha256sum deletes
	# /usr/bin on most machines and every rung then dies at exit 127.
	d=$(mk_integrity integrity_no_hasher)
	SHIM="$WORK/nohash_path"
	mkdir -p "$SHIM"
	for t in bash sh env git grep sed awk sort uniq comm xargs cmp diff mktemp wc tr head cut find basename dirname cat rm mv cp chmod ls; do
		p=$(command -v "$t" 2>/dev/null) || continue
		[ -n "$p" ] && ln -sf "$p" "$SHIM/$t"
	done
	if [ -e "$SHIM/sha256sum" ] || [ -e "$SHIM/shasum" ]; then
		report no "the no-hasher fixture really has no hasher" "the shim PATH contains one"
	else
		out=$(cd "$d" && env -i PATH="$SHIM" CI=1 HOME="$WORK" bash scripts/ladder.sh --guards-only 2>&1)
		rc=$?
		if [ "$rc" -ne 0 ]; then
			report no "no hashing tool is not fatal" "expected exit 0, got $rc" "$out"
		elif grep -qF "   WARN  neither sha256sum nor shasum is on PATH" <<<"$out"; then
			report ok "no hashing tool is not fatal"
		else
			report no "no hashing tool warns that the rung checked nothing" "no such warning" "$out"
		fi
	fi
fi

# --- the branch-train history line
# Under a squash-merge train the default branch's log is a list of merges rather than a record
# of decisions, and a session that reads it gets a plausible wrong answer. Both directions,
# because each is separately silent: printed unconditionally the line is FALSE for every
# branch-per-change repo, and dropped entirely it is missing for every train.
d=$(mk ss_merge_mode_train)
sed -i 's/^MERGE_MODE=.*/MERGE_MODE=branch-train/' "$d/amh.conf"
out=$(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
if grep -qF "merge mode: branch-train — main's history is squashed" <<<"$out"; then
	report ok "a branch-train repo is told its default branch's log is not its past"
else
	report no "a branch-train repo is told its default branch's log is not its past" "no line" "$out"
fi

# The negative control, and the reason the key is read at all: under branch-per-change the
# default branch's history IS the record, so the line would be a lie.
d=$(mk ss_merge_mode_per_change)
out=$(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
if grep -qF "branch-train" <<<"$out"; then
	report no "a branch-per-change repo is not told its history is squashed" "it said so anyway" "$out"
else
	report ok "a branch-per-change repo is not told its history is squashed"
fi

# An amh.conf with no MERGE_MODE line at all. `amh.conf.example` ships the key, so this is not
# the state a fresh instantiation is in — it is the state an adopter can put their own file in
# at any time, because that file is theirs and the harness cannot upgrade it. Without the
# default in the script the banner dies under `set -u`, entirely, at the top of the session.
d=$(mk ss_merge_mode_unset)
grep -v '^MERGE_MODE=' "$d/amh.conf" >"$d/t" && mv "$d/t" "$d/amh.conf"
out=$(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
rc=$?
if [ "$rc" -eq 0 ] && grep -qF "AMH session start"  <<<"$out"&&
	! grep -qF "branch-train" <<<"$out"; then
	report ok "a conf with no MERGE_MODE key still boots"
else
	report no "a conf with no MERGE_MODE key still boots" "rc=$rc" "$out"
fi

# --- poison tokens
# This guard resolves origin/<default> and prints `skip` without one, which is how it
# ran inert in the reference repo for its whole life. mk() now creates the ref.
d=$(mk poison_token)
(
	cd "$d" || exit 1
	printf 'a change\n' >>docs/STATE.md
	git commit -qam "checkpoint [skip ci]"
)
expect_fail "a poison token in a commit message fails" "$d" "[skip ci]"

d=$(mk poison_clean)
(
	cd "$d" || exit 1
	printf 'a change\n' >>docs/STATE.md
	git commit -qam "an ordinary checkpoint"
)
expect_pass "an ordinary commit message passes" "$d"

# --- git author identity
# mk() commits as amh@test.invalid and points origin/<default> at that commit, so the
# guard's window is EMPTY in every fixture that adds no commit of its own — including the
# baseline, which is why none of the cases above had to care about this rung. Each case
# below adds exactly one commit carrying the identity on trial.
identity_commit() { # <dir> <author-email> <committer-email>
	(
		cd "$1" || exit 1
		printf 'a change\n' >>docs/STATE.md
		GIT_AUTHOR_EMAIL="$2" GIT_COMMITTER_EMAIL="$3" git commit -qam "an ordinary checkpoint"
	)
	# The environment already exports both variables for the whole suite, so a fixture
	# that meant to override one and did not would commit as amh@test.invalid and assert
	# against a guard that had nothing to find. Read the commit back: the fixture's
	# premise is checkable in one command, and a premise that is merely probable is the
	# flake this suite has already shipped once.
	local got want="$2 $3"
	got=$(cd "$1" && git log -1 --format='%ae %ce')
	if [ "$got" != "$want" ]; then
		printf 'FIXTURE ERROR: commit carries identities [%s], wanted [%s]\n' "$got" "$want" >&2
		exit 1
	fi
}

# A rebase or an amend by another tool rewrites the committer and leaves the author
# alone, so a guard reading %ae only would see nothing here.
d=$(mk identity_committer_only)
identity_commit "$d" amh@test.invalid dev@localhost
# `dev@`, not `root@`: with a root address this fixture is matched by the `root@*` arm
# first and the localhost arm never executes — it could be deleted with the suite green.
expect_fail "an invented identity in the committer field alone is caught" "$d" \
	"committer identity 'dev@localhost' names localhost"

d=$(mk identity_not_an_address)
identity_commit "$d" amh-test amh@test.invalid
expect_fail "an identity with no @ fails" "$d" "is not an email address"

# ONE FIXTURE PER INVENTED SHAPE, and the reason is worth stating: with a single fixture
# behind the whole set, four of the five patterns could be deleted and the suite stayed
# fully green — proven by mutation, not supposed. Each case below feeds a shape no other
# pattern matches, and asserts the wording belonging to that pattern alone.
d=$(mk identity_root_account)
identity_commit "$d" root@buildbox root@buildbox
expect_fail "the machine's root account fails" "$d" "is the machine's root account"

d=$(mk identity_mdns_local)
identity_commit "$d" dev@laptop.local dev@laptop.local
expect_fail "an mDNS .local machine name fails" "$d" "'dev@laptop.local' names a local-only host"

d=$(mk identity_localdomain)
identity_commit "$d" dev@box.localdomain dev@box.localdomain
expect_fail "a .localdomain machine name fails" "$d" "'dev@box.localdomain' names a local-only host"

# git's own fallback when the hostname has no resolvable domain — the identity of every
# unconfigured container, and the likeliest thing this half will ever catch.
d=$(mk identity_none_placeholder)
identity_commit "$d" 'builder@host.(none)' 'builder@host.(none)'
expect_fail "git's (none) placeholder fails" "$d" "carries git's '(none)' placeholder"

# git accepts an empty address and stores it, so the empty field is reachable and needs
# its own wording rather than sharing the placeholder's.
d=$(mk identity_empty_field)
identity_commit "$d" '' amh@test.invalid
expect_fail "an empty identity field fails" "$d" "is EMPTY"

# `case` globs are case-sensitive and git stores what it was handed, so without the
# lower-casing step the entire section above is bypassed by holding down shift. Deleting
# that one line left the suite green until this fixture existed.
d=$(mk identity_uppercase)
identity_commit "$d" ROOT@LOCALHOST ROOT@LOCALHOST
expect_fail "an invented identity in capitals is still caught" "$d" "'ROOT@LOCALHOST' is the machine's root account"

# The pair below is the whole opt-in half, and it only means something as a pair: SAME
# address, once with the key absent and once with it set. Absent must pass — an adopter
# upgrading on an amh.conf that cannot contain the key gets the zero-config half and
# nothing else, which is the entire reason the default lives in the script. Set must
# fail on that same address, or the default is permissive because the config is never
# read rather than because empty means unset.
d=$(mk identity_allow_absent)
identity_commit "$d" someone@other.example someone@other.example
expect_pass_saying "a conf without AUTHOR_EMAIL_ALLOW applies no allowlist and says so" "$d" \
	"   ok    2 distinct field/address pair(s) over 1 commit(s); all well-formed. AUTHOR_EMAIL_ALLOW is unset, so no allowlist was applied"

d=$(mk identity_allow_miss)
printf "AUTHOR_EMAIL_ALLOW='.*@test\\\\.invalid'\n" >>"$d/amh.conf"
identity_commit "$d" someone@other.example someone@other.example
expect_fail "the same address fails once AUTHOR_EMAIL_ALLOW is set" "$d" \
	"does not match AUTHOR_EMAIL_ALLOW"

d=$(mk identity_allow_match)
printf "AUTHOR_EMAIL_ALLOW='.*@test\\\\.invalid'\n" >>"$d/amh.conf"
identity_commit "$d" amh@test.invalid amh@test.invalid
expect_pass_saying "an address inside AUTHOR_EMAIL_ALLOW passes and the rung says the list ran" "$d" \
	"   ok    2 distinct field/address pair(s) over 1 commit(s); all well-formed and admitted by AUTHOR_EMAIL_ALLOW"

# The allowlist is matched anchored, so a pattern the adopter wrote for a substring must
# not quietly allow the addresses around it.
d=$(mk identity_allow_anchored)
printf "AUTHOR_EMAIL_ALLOW='amh@test\\\\.invalid'\n" >>"$d/amh.conf"
identity_commit "$d" not-amh@test.invalid.example not-amh@test.invalid.example
expect_fail "the allowlist matches the whole address, not a substring of it" "$d" \
	"does not match AUTHOR_EMAIL_ALLOW"

# An unclosed group is the adopter typo that matters: `grep -E` exits 2 on it, which an
# `if` reads as "no match", so the pattern would fail every identity in the repository
# while the config looked like an allowlist. Warn, ignore it, keep the half that works.
d=$(mk identity_allow_malformed)
printf "AUTHOR_EMAIL_ALLOW='.*@(corp\\\\.example'\n" >>"$d/amh.conf"
identity_commit "$d" someone@other.example someone@other.example
expect_warn "a malformed AUTHOR_EMAIL_ALLOW warns and is ignored rather than failing everything" "$d" \
	"is not a valid extended regex"
# ...and the verdict line must not then claim the key is UNSET. It is set; it is invalid.
# A green line contradicting the warning above it is how a reader concludes the repository
# never configured one.
# The verdict word is part of the pattern deliberately: `expect_warn` requires SOME warn
# line and then greps the whole output, so without `   ok    ` in the pattern this text
# could migrate onto the WARN line and the assertion would still pass — the helper's name
# would be checking a condition it never asserted.
expect_warn "...and the ok line says it was ignored, not that it was never set" "$d" \
	"   ok    2 distinct field/address pair(s) over 1 commit(s); all well-formed. AUTHOR_EMAIL_ALLOW was IGNORED as malformed"

# The allowlist is consulted BEFORE the invented-identity patterns, so an address the
# repository has explicitly named is admitted whatever shape it has. Without this ordering
# `alice@corp.local` — a real Active Directory domain — is rejected, adding it to the key
# does not help, and the only remedy left is editing a shipped script. The fixture is what
# stops a later simplification from reordering the two halves back.
d=$(mk identity_allow_overrides_invented)
printf "AUTHOR_EMAIL_ALLOW='alice@corp\\\\.local'\n" >>"$d/amh.conf"
identity_commit "$d" alice@corp.local alice@corp.local
expect_pass_saying "a named address overrides the invented-shape patterns" "$d" \
	"   ok    2 distinct field/address pair(s) over 1 commit(s); all well-formed and admitted by AUTHOR_EMAIL_ALLOW"

# AMH ledger row D019's shape, in the branch whose whole purpose is to be LOUDER when
# the guard is switched off by something that is not its subject. Nothing covered it —
# not for this guard and not for the poison-token scan it was modelled on — so demoting
# the warn to a skip stayed green.
d=$(mk identity_no_upstream)
git -C "$d" update-ref -d "refs/remotes/origin/$DEFAULT_BRANCH_FIXTURE"
# Verdict word AND subject, and both halves are load-bearing. Deleting the ref makes the
# poison-token rung warn in nearly the same words, so a pattern matching only the shared
# condition is satisfied whichever rung printed it; and the message text alone survives a
# demotion to `skip` unchanged, so grepping the text proved only that the words exist
# somewhere. Both mistakes were made here before this line read the way it does.
expect_warn "with no upstream ref the guard says it checked NOTHING" "$d" \
	"   WARN  author identity is unguarded locally: no main reference"

# --- local advisories
# Warn-only and skipped in CI, so `run()` can never reach them: assert on the text.
d=$(mk advisory_rules)
sed -i "s|^RULE_FILES=''|RULE_FILES='amh.conf'|" "$d/amh.conf"
printf '\n# an uncommitted legislation edit\n' >>"$d/amh.conf"
started=$SECONDS
out=$(run_local "$d")
elapsed=$((SECONDS - started))
FIXTURE_ELAPSED_SECONDS=$elapsed
if grep -qF "touches legislation" <<<"$out"; then
	report ok "an uncommitted legislation edit warns"
else
	report no "an uncommitted legislation edit warns" "no rule-review warning" "$out"
fi

# A completed plan may now retire whole into the optional archive. The advisory is coaching,
# not enforcement, but deletion-only coaching directly contradicts P2/P16 at the action point.
d=$(mk advisory_plan_lifecycle)
mkdir -p "$d/docs/plans"
printf '# completed plan\n' >"$d/docs/plans/completed.md"
started=$SECONDS
out=$(run_local "$d")
elapsed=$((SECONDS - started))
FIXTURE_ELAPSED_SECONDS=$elapsed
if grep -qF "Move a completed plan worth retaining whole to docs/history/ when that archive tier exists; otherwise delete it" <<<"$out"; then
	report ok "an orphaned plan is coached toward archive-or-delete"
else
	report no "an orphaned plan is coached toward archive-or-delete" "no archive-or-delete warning" "$out"
fi

d=$(mk advisory_ci)
sed -i "s|^RULE_FILES=''|RULE_FILES='amh.conf'|" "$d/amh.conf"
printf '\n# an uncommitted legislation edit\n' >>"$d/amh.conf"
started=$SECONDS
out=$(run "$d")
elapsed=$((SECONDS - started))
FIXTURE_ELAPSED_SECONDS=$elapsed
if grep -qF "Local advisories" <<<"$out"; then
	report no "advisories stay out of CI" "the advisory section ran under CI=1" "$out"
else
	report ok "advisories stay out of CI"
fi

# --- the verdict's subject: which commit, and whether the tree IS that commit
# The ladder printed "green" for three releases without ever saying green OF WHAT
# (AMH ledger row DA025). Every case below is asserted on the VERDICT LINE, because that
# is the line a reader takes away, and each of the four states is separately silent: the
# dirty rendering and the clean one are one word apart, and the two "cannot tell" states
# read exactly like a clean tree if the code collapses them.
d=$(mk subject_clean)
sha=$(git -C "$d" rev-parse --short HEAD)
# The real short sha, not a pattern. Grepping `HEAD ` alone would be satisfied by a line
# that printed the word and no commit — which is the defect, spelled differently.
expect_verdict "the verdict names its commit and a clean worktree" run "$d" 0 \
	" — HEAD $sha, worktree clean"

# Dirty is the case the line exists FOR: the ladder verifies the working tree, so a green
# run attributed to HEAD alone is a claim about a commit nobody verified. The count is
# asserted too — a constant here would report every dirty tree as one path.
d=$(mk subject_dirty)
sha=$(git -C "$d" rev-parse --short HEAD)
printf '\n# an uncommitted edit\n' >>"$d/amh.conf"
printf 'untracked\n' >"$d/untracked-file"
expect_verdict "a dirty worktree is named, counted, and NOT attributed to HEAD" run "$d" 0 \
	" — HEAD $sha + 2 uncommitted path(s) — the tree just verified is NOT that commit"

# The probe honours .gitignore. Without that the first adopter with a build directory gets
# "dirty" on every run, learns the line means nothing, and the clean/dirty distinction is
# dead on arrival.
d=$(mk subject_ignored_is_clean)
printf 'ignored/\n' >"$d/.gitignore"
mkdir -p "$d/ignored"
printf 'x\n' >"$d/ignored/artifact"
git -C "$d" add -A >/dev/null 2>&1
git -C "$d" commit -qm "ignore rule" >/dev/null 2>&1
sha=$(git -C "$d" rev-parse --short HEAD)
expect_verdict "an ignored path does not make the worktree dirty" run "$d" 0 \
	" — HEAD $sha, worktree clean"

# ...but `status.showUntrackedFiles=no` must NOT make it clean, and this is the case the
# probe is built the way it is for. The key is settable in .git/config or in a user-level
# ~/.gitconfig, neither of which is in the tree, so nothing else in this suite or in the
# ladder can see it. With `git status --porcelain` as the probe the ladder FAILS on the
# untracked file below while its verdict line calls the tree clean — a guard failing on
# content the verdict attributes to a commit that does not contain it.
d=$(mk subject_untracked_hidden_from_status)
sha=$(git -C "$d" rev-parse --short HEAD)
git -C "$d" config status.showUntrackedFiles no
printf 'untracked\n' >"$d/not-in-head"
expect_verdict "an untracked file hidden from git status still reads as dirty" run "$d" 0 \
	" — HEAD $sha + 1 uncommitted path(s) — the tree just verified is NOT that commit"

# No repository at all. Naming no commit is the honest answer; the failure mode being
# closed is a line that says "worktree clean" about a tree git was never asked about.
#
# NOTE the assumption this fixture rests on, since it is not local to the fixture: $WORK
# comes from `mktemp -d`, and if TMPDIR pointed inside a git checkout, `has_git` would
# resolve the ENCLOSING repository and this case would go red rather than silently green.
d=$(mk subject_no_git)
rm -rf "$d/.git"
expect_verdict "with no repository the verdict names no commit" run "$d" 0 \
	"git names no repository from here, so this verdict names no commit"

# An initialised repository with nothing committed. Distinct from the case above — there
# IS a repository — and distinct from clean, which is what it would collapse into if the
# empty `rev-parse` output were treated as a sha.
d=$(mk subject_unborn_head)
rm -rf "$d/.git"
git -C "$d" init -q
expect_verdict "an unborn HEAD is named as such, not rendered as clean" run "$d" 0 \
	"no commit yet (unborn HEAD)"

# State (b): git present, git refusing to answer. A corrupt index is the reachable trigger
# — verified, and unlike a stale index.lock, which leaves `git diff` exiting 0. Without
# this case the whole rc branch could be deleted with the suite green, and the rendering
# would silently become "worktree clean" for a tree nobody could read: D-019's rule, in the
# one place where the honest answer is that there is no answer.
#
# Exit 0 is the expectation and it is not an oversight: a fixture's guards survive a corrupt
# index (the scans that read it fall back or go vacuous), so this case isolates the RENDERING
# rather than riding on a failure. The red verdict gets its own fixture below.
d=$(mk subject_git_unreadable)
sha=$(git -C "$d" rev-parse --short HEAD)
printf 'JUNKJUNKJUNK' >"$d/.git/index"
expect_verdict "an unreadable worktree state is UNKNOWN, never clean" run "$d" 0 \
	" — HEAD $sha, worktree state UNKNOWN (git would not report it)"

# The `✗ guards:` red verdict. Committed rather than left untracked, so the assertion is
# about the red rendering and not about the dirty one it would otherwise pick up.
d=$(mk subject_red_guards)
printf '#!/usr/bin/env bash\nexit 1\n' >"$d/scripts/guards/always-fails.sh"
chmod +x "$d/scripts/guards/always-fails.sh"
git -C "$d" add -A >/dev/null 2>&1
git -C "$d" commit -qm "a failing repo-local guard" >/dev/null 2>&1
sha=$(git -C "$d" rev-parse --short HEAD)
expect_verdict "the red guards verdict names its subject" run "$d" 1 \
	"✗ guards: 1 failure(s), 0 warning(s) — HEAD $sha, worktree clean"

# The two `✗ ladder red` verdicts below rung 3, which --guards-only can never reach.
d=$(mk subject_red_no_verify)
sha=$(git -C "$d" rev-parse --short HEAD)
expect_verdict "the missing-verification-rung verdict names its subject" run_full "$d" 1 \
	"✗ ladder red — HEAD $sha, worktree clean"

d=$(mk subject_red_verify_fails)
printf '#!/usr/bin/env bash\nexit 1\n' >"$d/scripts/verify.sh"
chmod +x "$d/scripts/verify.sh"
git -C "$d" add -A >/dev/null 2>&1
git -C "$d" commit -qm "a failing verification set" >/dev/null 2>&1
sha=$(git -C "$d" rev-parse --short HEAD)
expect_verdict "the failed-verification verdict names its subject" run_full "$d" 1 \
	"✗ ladder red (verification set failed) — HEAD $sha, worktree clean"

# The green full-ladder verdict, for the same reason: it is a different printf from the
# guards-only one and nothing else in this suite reaches it.
d=$(mk subject_green_full)
printf '#!/usr/bin/env bash\nexit 0\n' >"$d/scripts/verify.sh"
chmod +x "$d/scripts/verify.sh"
git -C "$d" add -A >/dev/null 2>&1
git -C "$d" commit -qm "a passing verification set" >/dev/null 2>&1
sha=$(git -C "$d" rev-parse --short HEAD)
expect_verdict "the full green verdict names its subject" run_full "$d" 0 \
	"✓ ladder green"

# =============================================================================
printf '\n%d passed, %d failed\n' "$PASSED" "$FAILED"
print_timing_summary
[ "$FAILED" -eq 0 ]
