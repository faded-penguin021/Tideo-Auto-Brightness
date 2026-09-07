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

# POSIX does not standardise `sed -i`, and BSD and GNU sed give it incompatible argument
# syntax. Fixtures edit disposable files through an ordinary temporary output-and-writeback operation so
# this shipped suite runs unchanged with either implementation.
sed_in_place() { # <sed-expression> <file>
	local expression=$1 file=$2 tmp
	tmp="${file}.amh-sed.$$"
	sed "$expression" "$file" >"$tmp" || {
		rm -f "$tmp"
		return 1
	}
	cat "$tmp" >"$file" && rm -f "$tmp"
}
# Rewrite a fixture file with CRLF endings, the way a Windows checkout receives every tracked
# text file. Written in bash rather than with `sed`, `unix2dos` or `awk`: a `\r` in a sed
# replacement is GNU-only, and the tool it would stand in for is the very one whose newline
# handling is under test here.
crlf_endings() { # <file>
	local file=$1 line tmp="$1.amh-crlf.$$"
	while IFS= read -r line || [ -n "$line" ]; do
		printf '%s\r\n' "$line"
	done <"$file" >"$tmp"
	cat "$tmp" >"$file" && rm -f "$tmp"
}

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
	sed_in_place '/^guard_rail_selftests() {/a\
\tskip "scripts/command-guard.sh and scripts/redact.sh self-tests already covered by fixture suite"\
\treturn' "$d/scripts/ladder.sh"
	sed_in_place '/^guard_shipped_integrity() {/a\
\tskip "shipped-script manifest check already covered by fixture suite"\
\treturn' "$d/scripts/ladder.sh"
	cat >"$d/amh.conf" <<-'CONF'
		DEFAULT_BRANCH=main
		BRANCH_PREFIX=session
		MERGE_MODE=branch-per-change
		REMOTE_FLAG=AMH_REMOTE
		STATE_FILE=docs/STATE.md
		STATE_COMPRESS_TO_KB=9
		STATE_COMPRESS_TO_SENTENCES=50
		STATE_WARN_KB=14
		STATE_HARD_KB=16
		STATE_EDIT_DELTA_BYTES=1024
		STATE_REQUIRED_SECTIONS='## Project|## Current state|## Changelog'
		STATE_OWNER_QUEUE_SECTION='## Owner queue'
		LEDGER_DIR=docs
		LEDGER_BASENAME=LEDGER
		LEDGER_LINE_CAP=800
		LEDGER_ROW_SENTENCE_CAP=6
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
	sed_in_place '/^guard_rail_selftests() {/a\
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
expect_pass_not_saying() { # <name> <dir> <ERE the green output must NOT match> <fixed substring it MUST contain>
	# The inverse of expect_pass_saying, and the only shape that can pin an ANTI-anchor: a
	# rule saying "do not re-state the threshold on a pass" is satisfiable by prose and
	# unfalsifiable without a fixture that fails when the number comes back. Compare 6.0.0's
	# fixtures over what the destructive advisory must not claim.
	#
	# Two deliberate choices, both learned the hard way in review. The absent needle is an
	# ERE, not a fixed string, so it can be written WITHOUT the fixture's configured value:
	# `compression trigger 14` passes the moment someone changes the fixture conf to 13 while the
	# anchor is still printed — the DB-022 trap rebuilt inside the guard against it. And the
	# fourth argument is the checked-NOTHING arm the runbook requires: an assertion about
	# absence is satisfied by a rung that never ran, printed nothing, or was renamed out of
	# existence, so the caller must also name a string the line positively has.
	local out rc started=$SECONDS
	out=$(run "$2")
	rc=$?
	FIXTURE_ELAPSED_SECONDS=$((SECONDS - started))
	if [ "$rc" -ne 0 ]; then
		report no "$1" "expected exit 0, got $rc" "$out"
	elif ! grep -qF "$4" <<<"$out"; then
		report no "$1" "checked NOTHING: the output never contained '$4', so the absence of '$3' proves nothing" "$out"
	elif grep -qE "$3" <<<"$out"; then
		report no "$1" "passed but the output re-stated '$3', which a green verdict must not do" "$out"
	else
		report ok "$1"
	fi
}

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

# --- command-guard.sh: Codex PreToolUse payload -----------------------------
d=$(mk codex_hook_payload)
out=$(cd "$d" && printf '%s' '{"hook_event_name":"PreToolUse","tool_name":"Bash","tool_input":{"command":"cat .env"}}' |
	scripts/command-guard.sh 2>&1)
rc=$?
if [ "$rc" -eq 2 ] && printf '%s' "$out" | grep -qF 'prefer presence-only checks'; then
	report ok "a forbidden Codex Bash payload is blocked with the instructive reason"
else
	report no "a forbidden Codex Bash payload is blocked with the instructive reason" "rc=$rc" "$out"
fi

out=$(cd "$d" && printf '%s' '{"hook_event_name":"PreToolUse","tool_name":"Bash","tool_input":{"command":"printf hello"}}' |
	scripts/command-guard.sh 2>&1)
rc=$?
if [ "$rc" -eq 0 ]; then report ok "an allowed Codex Bash payload passes"; else report no "an allowed Codex Bash payload passes" "rc=$rc" "$out"; fi

out=$(cd "$d" && printf '%s' '{not-json' | scripts/command-guard.sh 2>&1)
rc=$?
if [ "$rc" -eq 0 ]; then report ok "a malformed Codex payload fails open"; else report no "a malformed Codex payload fails open" "rc=$rc" "$out"; fi

out=$(cd "$d" && printf '%s' '{"hook_event_name":"PreToolUse","tool_name":"Read","tool_input":{"command":"cat .env"}}' |
	scripts/command-guard.sh 2>&1)
rc=$?
if [ "$rc" -eq 0 ]; then report ok "a non-Bash Codex payload fails open"; else report no "a non-Bash Codex payload fails open" "rc=$rc" "$out"; fi

# The distributed baseline does not require Python. Hide it from command lookup and prove
# the conservative coreutils fallback still enforces a straightforward Codex Bash payload.
fallback_path="$d/no-python-bin"
mkdir -p "$fallback_path"
for tool in bash cat dirname git grep head sed; do
	tool_path=$(command -v "$tool")
	ln -s "$tool_path" "$fallback_path/$tool"
done
out=$(cd "$d" && printf '%s' '{"hook_event_name":"PreToolUse","tool_name":"Bash","tool_input":{"command":"cat .env"}}' |
	env PATH="$fallback_path" scripts/command-guard.sh 2>&1)
rc=$?
if [ "$rc" -eq 2 ]; then report ok "a Codex Bash payload is guarded without Python"; else report no "a Codex Bash payload is guarded without Python" "rc=$rc" "$out"; fi

# The same fallback, at a payload size that makes the writer block. This is the fail-OPEN
# direction of the `grep -q` class: grep exits at its first match, the writer takes EPIPE,
# `pipefail` promotes that to the pipeline's status, and `|| return 0` stands the rail down
# on a Bash command nobody inspected. A rail that stood down is indistinguishable from a
# rail that looked and found nothing, which is the silent-skip class.
#
# TWO properties are needed and the obvious fixture has only one. Size alone does NOT
# reproduce it: grep cannot match until it holds a whole LINE, so a single-line payload is
# consumed in full however long it is, and the case passes against both versions. The
# payload must ALSO be multi-line, so that the match lands on an early line with the rest
# still pending. Pretty-printed JSON is that shape, and `extract_command`'s own `case`
# accepts it, so this is a payload the rail claims to handle rather than one invented to
# break it. The token sits on line 2 and the command on the last line, which is where the
# most bytes are left over.
d=$(mk codex_hook_payload_large)
fallback_path="$d/no-python-bin"
mkdir -p "$fallback_path"
for tool in bash cat dirname git grep head sed; do
	tool_path=$(command -v "$tool")
	ln -s "$tool_path" "$fallback_path/$tool"
done
big_pad=$(awk 'BEGIN { for (i = 0; i < 4000; i++) printf "  \"pad%d\": \"%s\",\n", i, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" }')
big_payload='{
  "tool_name": "Bash",
'"$big_pad"'  "tool_input": { "command": "cat .env" }
}'
# The payload's SIZE is the fixture, so it is asserted rather than assumed. `awk` failing or
# missing leaves `big_pad` empty under `set -uo pipefail` with no `-e`, collapsing this to a
# 68-byte payload that is a duplicate of the case above and green against both versions. Swept
# against the pre-fix script: 68 and 49957 bytes both return 2 (hollow), 100957 and 202957
# return 0 (real). The 128 KB floor is therefore CONSERVATIVE — it rejects sizes that do
# reproduce — which is the right direction for a hollowness guard: a fixture that refuses to
# run is loud, one that runs on too little is green and worthless.
if [ "${#big_payload}" -le 131072 ]; then
	report no "a large multi-line Codex Bash payload is guarded without Python" \
		"payload is only ${#big_payload} bytes — too small to reach the defect, so this case would pass against the unfixed script too"
else
out=$(cd "$d" && printf '%s' "$big_payload" | env PATH="$fallback_path" scripts/command-guard.sh 2>&1)
rc=$?
if [ "$rc" -eq 2 ]; then
	report ok "a large multi-line Codex Bash payload is guarded without Python"
else
	report no "a large multi-line Codex Bash payload is guarded without Python" "rc=$rc (0 means the rail stood down)" "$out"
fi
fi

# --- command-guard.sh: the git-native pre-push rail (P13) --------------------
# The `--self-test` matrix stubs the ancestry seam; this exercises the REAL `git merge-base`
# glue over real commits, which is the part a string-only self-test cannot see (P12). Every
# assertion fails against a command-guard with no `--pre-push` mode: the old script prints
# `usage:` and exits 2, so the ALLOW cases fail on the exit code and the BLOCK cases fail on
# the banner text they check for — the old exit-2 is not the rail's block.
d=$(mk prepush_rail)
c1=$(git -C "$d" rev-parse HEAD)
git -C "$d" commit -q --allow-empty -m prepush-c2
c2=$(git -C "$d" rev-parse HEAD)
git -C "$d" checkout -q -b prepush-div "$c1"
git -C "$d" commit -q --allow-empty -m prepush-divergent
cdiv=$(git -C "$d" rev-parse HEAD)
PP_ZERO=0000000000000000000000000000000000000000
pp_block() { # <name> <stdin line> — must exit 2 AND name the pre-push rail
	local out rc
	out=$(cd "$d" && printf '%s\n' "$2" | scripts/command-guard.sh --pre-push 2>&1)
	rc=$?
	if [ "$rc" -eq 2 ] && printf '%s' "$out" | grep -qF 'AMH pre-push rail'; then
		report ok "$1"
	else
		report no "$1" "rc=$rc" "$out"
	fi
}
pp_allow() { # <name> <stdin line> — must exit 0
	local out rc
	out=$(cd "$d" && printf '%s\n' "$2" | scripts/command-guard.sh --pre-push 2>&1)
	rc=$?
	if [ "$rc" -eq 0 ]; then report ok "$1"; else report no "$1" "rc=$rc" "$out"; fi
}
pp_block "pre-push blocks a push to the default branch" "refs/heads/session/x $c2 refs/heads/$DEFAULT_BRANCH_FIXTURE $c1"
pp_block "pre-push blocks a non-fast-forward (force by effect)" "refs/heads/session/x $c1 refs/heads/session/x $c2"
pp_block "pre-push blocks a divergent-history force" "refs/heads/session/x $cdiv refs/heads/session/x $c2"
pp_block "pre-push blocks a branch deletion" "(delete) $PP_ZERO refs/heads/session/x $c2"
pp_allow "pre-push allows a fast-forward of a session ref" "refs/heads/session/x $c2 refs/heads/session/x $c1"
pp_allow "pre-push allows a fast-forward of an assigned claude/ ref (no prefix check)" "refs/heads/claude/assigned $c2 refs/heads/claude/assigned $c1"
pp_allow "pre-push allows creating a new branch (remote sha zero)" "refs/heads/session/x $c2 refs/heads/session/x $PP_ZERO"
# Fail-open on a malformed (incomplete) line, and the input is chosen so the fixture is not
# hollow: this line's fields would make prepush_verdict BLOCK (a zero local sha reads as a
# delete) if run_prepush did not skip it first — so deleting the `|| continue` fail-open flips
# this case from allow to block, which is what makes it a real test of that branch.
pp_allow "pre-push fails open on an incomplete line" "refs/heads/session/x $PP_ZERO"
out=$(cd "$d" && printf '' | scripts/command-guard.sh --pre-push 2>&1)
rc=$?
if [ "$rc" -eq 0 ]; then report ok "pre-push fails open on empty stdin"; else report no "pre-push fails open on empty stdin" "rc=$rc" "$out"; fi

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
expect_fail "STATE over the rejection boundary fails" "$d" "rejection boundary"

d=$(mk state_warn)
{
	echo
	filler $((15 * 1024))
} >>"$d/docs/STATE.md"
expect_warn "STATE over the compression trigger warns only" "$d" "compression trigger"

# Landing check, one fixture per branch. Sizes are set with `state_bytes` — grow past
# the target with filler, then truncate to an EXACT byte count — so every shrink these
# assert on is what it is by construction, not by however long the fixture's STATE.md
# prose happens to be. A margin that depends on the base file is a flake waiting for
# someone to reword the fixture (D-024).
#
# The filler is sized FROM the request, and the result is checked. A fixed 18 KB of filler
# was the first form and it is the same defect one level up: `head -c` on a file shorter
# than the request silently yields a shorter file and exits 0, so the first fixture that
# asked for a size crossing the filler — a hard-cap landing case is the obvious one — would
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

# Countable sentences for the branches whose verdict turns on the post-action ceilings. One per line,
# each terminator followed by the next line's capital once the counter joins them.
state_sentences() { # <n> — n sentences of fixture prose on stdout
	local i=0
	while [ "$i" -lt "$1" ]; do
		printf 'Fixture sentence %s states a durable lesson and stops. \n' "$i"
		i=$((i + 1))
	done
}

# Branch 1 — a shrink that crosses from above the compression trigger to below it must reach the
# post-action ceilings, not stop in the debounce band. This is the Goodhart hole the size thresholds
# alone leave, and the branch split must not reopen it.
#
# The post-action ceilings is two conditions, so this branch takes three fixtures: one for each cheap move
# that satisfies one condition while removing nothing, and one for a real pass. They share
# a construction — a committed file of N sentences padded crossing the compression trigger — so the
# "compressed" file below is genuinely the committed one with words taken out of it, which
# is what makes the word MICRO-TRIM in the first fixture's name true of what it does.
state_grown() { # <dir> <sentences> — commit a file of <sentences> sentences crossing the cap
	cp "$1/docs/STATE.md" "$1/base.md"
	{ state_sentences "$2"; } >>"$1/docs/STATE.md"
	cp "$1/docs/STATE.md" "$1/sentences.md"
	state_bytes "$1" $((15 * 1024))
	(cd "$1" && git commit -qam "grow crossing the compression trigger")
}

# THE MICRO-TRIM CASE. The committed file carries 60 sentences and 15 KB; the landing keeps
# every one of those sentences and throws away 14 KB of the padding around them. That is
# the reflex in its purest form — a 93% cut by bytes, removing no content — and the byte
# post-action ceiling alone passed it, which is why the sentence post-action ceiling stands beside it.
d=$(mk state_landing_micro_trim)
state_grown "$d" 60
cp "$d/sentences.md" "$d/docs/STATE.md"
# Greps branch 1's OWN wording, not the "stops short" both failing branches share: with the
# shared phrase, rewriting branch 1's message in branch 3's words left the suite green, so
# the fixture could not tell which branch had fired.
expect_fail "micro-trim that crosses below the cap but misses the post-action ceilings fails" "$d" "compression result is"

# THE REPUNCTUATION CASE, which is the same defect through the other post-action ceiling. The body is
# joined onto one line and every `. F` boundary rewritten to `; f`, so the sentence count
# collapses to nearly nothing while not one byte is freed. Sized to sit BETWEEN the byte
# post-action ceilings and the compression trigger, so the sentence half of the condition is satisfied and only the
# byte half can reject it: delete that half and this fixture goes green.
d=$(mk state_landing_repunctuated)
state_grown "$d" 60
{ cat "$d/base.md"; state_sentences 200 | tr '\n' ' ' | sed 's/\.  *F/; f/g'; } >"$d/docs/STATE.md"
expect_fail "collapsing the sentence count without freeing bytes fails" "$d" "compression result is"

# The real pass, and the pair that proves the post-action ceilings come from the config rather than from
# constants: the same landing that fails above passes once both post-action ceilings admit it.
d=$(mk state_landing_floor_from_config)
state_grown "$d" 60
cp "$d/sentences.md" "$d/docs/STATE.md"
sed_in_place 's/^STATE_COMPRESS_TO_SENTENCES=.*/STATE_COMPRESS_TO_SENTENCES=70/' "$d/amh.conf"
sed_in_place 's/^STATE_COMPRESS_TO_KB=.*/STATE_COMPRESS_TO_KB=14/' "$d/amh.conf"
expect_pass_saying "the configured post-action ceilings decide the landing" "$d" "below the post-action ceilings"

# A malformed post-action ceiling must be loud and must not silently decide the branch — the same
# contract the edit delta below has, and for the same reason.
d=$(mk state_floor_malformed)
sed_in_place 's/^STATE_COMPRESS_TO_SENTENCES=.*/STATE_COMPRESS_TO_SENTENCES=9KB/' "$d/amh.conf"
expect_warn "a malformed post-action ceiling warns and falls back rather than deciding quietly" "$d" \
	"is not a positive sentence count"

# Branch 3 — the same hole one band higher: a compression pass that never crosses below
# the cap. If the check only fired on a crossing, grow-to-15.5 / trim-to-14.2 would
# repeat forever under a mere warning. 1.5 KB lost, against a 1 KB delta.
d=$(mk state_landing_above_warn)
state_bytes "$d" $((16 * 1024))
(cd "$d" && git commit -qam "grow well crossing the compression trigger")
head -c $((14848)) "$d/docs/STATE.md" >"$d/docs/STATE.tmp" && mv "$d/docs/STATE.tmp" "$d/docs/STATE.md"
expect_fail "a trim that stops short while still over the cap fails" "$d" "unfinished compression pass"

# Branch 2 — the defect this split exists to fix. A 100-byte deletion above the cap is a
# typo fix or a closed queue item, not a compression pass that stopped short; failing it
# leaves padding the file back as the only compliant move. Allowed, and the size warning
# above it stays armed, which is what `expect_warn` is checking alongside the branch line.
d=$(mk state_landing_edit)
state_bytes "$d" $((15 * 1024))
(cd "$d" && git commit -qam "grow crossing the compression trigger")
head -c $((15 * 1024 - 100)) "$d/docs/STATE.md" >"$d/docs/STATE.tmp" && mv "$d/docs/STATE.tmp" "$d/docs/STATE.md"
expect_warn "a small edit above the cap is allowed and says so" "$d" "edit above the compression trigger (shrank 100 bytes"

# The delta's plumbing, both directions. Neither the script's default nor the config read
# was exercised by anything above: every fixture conf sets the key, so deleting the default
# from the script left the suite green — while an adopter upgrading on an existing amh.conf,
# which cannot have the key, would hit an unbound variable under `set -u` and abort the
# ladder mid-run. And with the delta hardcoded back to a literal, the suite stayed green too.
d=$(mk state_delta_default)
grep -v '^STATE_EDIT_DELTA_BYTES=' "$d/amh.conf" >"$d/t" && mv "$d/t" "$d/amh.conf"
state_bytes "$d" $((15 * 1024))
(cd "$d" && git commit -qam "grow crossing the compression trigger")
head -c $((15 * 1024 - 100)) "$d/docs/STATE.md" >"$d/docs/STATE.tmp" && mv "$d/docs/STATE.tmp" "$d/docs/STATE.md"
expect_warn "a conf without the delta key falls back to the shipped default" "$d" "edit above the compression trigger (shrank 100 bytes"

# Same 100-byte shrink, a delta of 64: it must now read as a compression pass. This is what
# proves the value comes from the config rather than from a constant in the script.
d=$(mk state_delta_configured)
sed_in_place 's/^STATE_EDIT_DELTA_BYTES=.*/STATE_EDIT_DELTA_BYTES=64/' "$d/amh.conf"
state_bytes "$d" $((15 * 1024))
(cd "$d" && git commit -qam "grow crossing the compression trigger")
head -c $((15 * 1024 - 100)) "$d/docs/STATE.md" >"$d/docs/STATE.tmp" && mv "$d/docs/STATE.tmp" "$d/docs/STATE.md"
expect_fail "the configured delta decides the branch" "$d" "unfinished compression pass"

# A malformed delta must be loud and must not silently decide the branch.
d=$(mk state_delta_malformed)
sed_in_place 's/^STATE_EDIT_DELTA_BYTES=.*/STATE_EDIT_DELTA_BYTES=1KB/' "$d/amh.conf"
state_bytes "$d" $((15 * 1024))
(cd "$d" && git commit -qam "grow crossing the compression trigger")
head -c $((15 * 1024 - 100)) "$d/docs/STATE.md" >"$d/docs/STATE.tmp" && mv "$d/docs/STATE.tmp" "$d/docs/STATE.md"
expect_warn "a malformed delta warns and falls back rather than deciding quietly" "$d" "is not a positive byte count"

# A real compression pass: the sentences go WITH the bytes, which is the only move that
# satisfies both post-action ceilings at once.
d=$(mk state_landing_good)
state_grown "$d" 60
{ cat "$d/base.md"; state_sentences 2; } >"$d/docs/STATE.md"
expect_pass "compression landing on the post-action ceilings passes" "$d"
# Landing well under the post-action ceilings reports the HEADROOM in both units, and the gradient it
# teaches is the whole point: a line that answered "at or under the post-action ceilings" would read
# identically for a landing one sentence clear.
expect_pass_saying "the landing line reports how far below the post-action ceilings it landed" "$d" \
	"below the post-action ceilings"
expect_pass_not_saying "a green state landing reports measurements without a quality claim" "$d" \
	"concise|well-compressed|well compressed" "below the post-action ceilings"

# --- Anti-anchor: a green verdict names no threshold ------------------------
# Two reported Goodhart failures, one shape: the number a clean run prints becomes the
# number the next session optimizes toward. An instance shaved STATE across a dozen edits
# to land 7 bytes under the post-action ceilings, and drafted ledger rows at 828 and 805 to trim them to
# just fit — after copying "the cap is a maximum, not a target" into its own preamble by
# hand. Prose lost to salience, so the anchor is removed from the lines that reject
# nothing. These fixtures fail the moment a threshold returns to a pass.
d=$(mk state_size_green_anchor)
state_bytes "$d" $((5 * 1024))
expect_pass_not_saying "a green STATE size verdict names no threshold" "$d" \
	"compression trigger|rejection boundary|hard [0-9]" "KB, within the band"

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
sed_in_place 's/^LEDGER_LINE_CAP=800/LEDGER_LINE_CAP=4/' "$d/amh.conf"
printf -- '- D-003: crossing the cap.\n' >>"$d/docs/LEDGER.md"
expect_fail "a row starting crossing the line cap fails" "$d" "crossing the"

# The cap gates LINES; the rung also REPORTS size, because read cost is what the cap
# stands in for and prose rows make the two drift. ALL THREE branches must carry the
# figure — one that appears only on the quiet path is missing exactly when the volume is
# growing, and the fail branch is the volume at its largest.
d=$(mk ledger_bytes)
expect_pass_saying "the passing rung reports the live volume's size, not just its lines" "$d" \
	"KB (grep it; a volume is retrieval storage, not a read)"
# …and reports the count without the cap beside it. `790/800 lines` reads as context rather
# than as an anchor, which is exactly why it survived the first pass of this change: it is
# the same number in the same place doing the same thing. The warn branch at nine tenths
# still names the cap, and that is the verdict the number belongs to.
expect_pass_not_saying "a green ledger cap verdict does not re-state the line cap" "$d" \
	"[0-9]+/[0-9]+ lines|LEDGER_LINE_CAP" "lines,"

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

d=$(mk ledger_bytes_near_rollover)
# Nearness has no separate warning band: until a later row crosses the rollover boundary,
# line count and byte size are measurement only.
ledger_lines=$(wc -l <"$d/docs/LEDGER.md")
sed_in_place "s/^LEDGER_LINE_CAP=800/LEDGER_LINE_CAP=$ledger_lines/" "$d/amh.conf"
expect_pass_saying "nearing rollover remains measurement only" "$d" "lines, "

d=$(mk ledger_bytes_fail)
sed_in_place 's/^LEDGER_LINE_CAP=800/LEDGER_LINE_CAP=4/' "$d/amh.conf"
printf -- '- D-003: a row crossing the cap.\n' >>"$d/docs/LEDGER.md"
expect_fail "the rollover FAILURE reports the size too — that is the branch that needs it" "$d" \
	"KB measured), crossing the 4-line rollover boundary"


# The row caps. Every `sed` below matches `^KEY=` rather than the shipped value, so a
# default that moves cannot turn a substitution into a silent no-op.
d=$(mk ledger_row_under_cap)
sed_in_place 's/^LEDGER_ROW_SENTENCE_CAP=.*/LEDGER_ROW_SENTENCE_CAP=3/' "$d/amh.conf"
printf -- '- D-003: **Short enough.** Two sentences, and it stops.\n' >>"$d/docs/LEDGER.md"
expect_pass_saying "a new ledger row under both caps reports only its measured sentence count" "$d" \
	"checked 1 new ledger row(s) — D-003=2 sentence(s)"
expect_pass_not_saying "a green ledger-row verdict claims neither authoring quality nor either row cap" "$d" \
	"concise|well-compressed|LEDGER_ROW_SENTENCE_CAP|LEDGER_ROW_CHAR_CAP|[0-9]+ byte" "checked 1 new ledger row(s) — D-003="

# What the counter treats as a sentence boundary, pinned where an author can see it. Each
# of these fixtures dies if ONE branch of the regex is removed, which the first draft of
# this block did not manage: its `e.g.` was followed by a lowercase word, so the
# abbreviation fold it claimed to pin was never reached and deleting the fold left the
# suite green.
#
# Closers: markup and punctuation stand between the terminator and the space. Two fixtures,
# because a single one using `**` leaves every other member of the class deletable — the
# closer class shrank to `[*]*` and the suite stayed green until the quote case joined it.
d=$(mk ledger_row_counting_closer)
printf -- '- D-003: **Bold ends a sentence.** And a second one follows it.\n' >>"$d/docs/LEDGER.md"
expect_pass_saying "a bold closer ends a sentence" "$d" "D-003=2 sentence(s)"

d=$(mk ledger_row_counting_closer_quote)
printf -- '- D-003: The rail said "it stops here." Then the row states its lesson.\n' >>"$d/docs/LEDGER.md"
expect_pass_saying "a closing quote ends a sentence" "$d" "D-003=2 sentence(s)"

# Openers other than a capital. Shrink the opener class to [A-Z] and this reads 1.
d=$(mk ledger_row_counting_opener)
# shellcheck disable=SC2016 # the backticks are markdown in the row's text, not a substitution
printf -- '- D-003: A first sentence ends here. `split_words` opens the second one.\n' >>"$d/docs/LEDGER.md"
expect_pass_saying "a backticked identifier opens a sentence" "$d" "D-003=2 sentence(s)"

# The abbreviation fold, with a CAPITAL after `e.g.` so the terminator regex would split
# there if the fold were gone. Delete either gsub and this reads 3.
d=$(mk ledger_row_counting_abbrev)
printf -- '- D-003: One sentence, e.g. This clause and i.e. That one stay inside it. A second sentence closes.\n' >>"$d/docs/LEDGER.md"
expect_pass_saying "e.g. and i.e. do not end a sentence even before a capital" "$d" \
	"D-003=2 sentence(s)"

d=$(mk ledger_row_counting_titles)
printf -- '- D-003: Dr. Smith records one sentence. U.S. Policy opens the second. E.G. This clause stays within it. A third sentence closes.\n' >>"$d/docs/LEDGER.md"
expect_pass_saying "titles and initialisms do not create phantom sentences" "$d" \
	"D-003=2 sentence(s)"

# The no-config path must enforce the same shipped fallback as the configuration template.
# A 1700-byte row is above the former 1600-byte fallback but below the adopted 2000-byte
# backstop, and carries fewer than six sentences so no other row limit decides the verdict.
d=$(mk ledger_row_default_cap_lockstep)
rm "$d/amh.conf"
printf -- '- D-003: One sentence. %*s\n' 1660 '' >>"$d/docs/LEDGER.md"
expect_pass "the no-config ledger backstop matches the shipped 2000-byte default" "$d"

# A count that cannot be produced at all is a FAILURE, never a quiet pass. The row is fine;
# awk is what is missing, and the rung must say it judged nothing rather than print a
# green line with a blank number in it.
# The stub fails ONLY the sentence counter's program — matched on a string unique to it —
# and passes every other awk call through. Hiding awk entirely was the blunt version and it
# tests something else: half the ladder dies, the row rung never runs, and the arm under
# test is never reached. A fixture has to fail for its own reason.
d=$(mk ledger_row_counting_hollow)
printf -- '- D-003: **Short.** It stops.\n' >>"$d/docs/LEDGER.md"
mkdir -p "$d/stub-bin"
real_awk=$(command -v awk)
cat >"$d/stub-bin/awk" <<STUB
#!/bin/sh
case "\$*" in *'buf = buf "X"'*) exit 3 ;; esac
exec $real_awk "\$@"
STUB
chmod +x "$d/stub-bin/awk"
out=$(cd "$d" && env PATH="$d/stub-bin:$PATH" scripts/ladder.sh --guards-only 2>&1)
rc=$?
if [ "$rc" -ne 0 ] && grep -qF "judged NOTHING" <<<"$out"; then
	report ok "a sentence count that cannot be produced fails loudly"
else
	report no "a sentence count that cannot be produced fails loudly" "rc=$rc" "$out"
fi

# The other half of hollow, and the one an exit status cannot catch: awk SUCCEEDS and
# prints nothing. Without the non-numeric check the empty string reaches `[ "" -gt 6 ]`,
# which writes an error to stderr and takes the else branch — a green rung reporting a
# count nobody produced.
d=$(mk ledger_row_counting_empty)
printf -- '- D-003: **Short.** It stops.\n' >>"$d/docs/LEDGER.md"
mkdir -p "$d/stub-bin"
real_awk=$(command -v awk)
cat >"$d/stub-bin/awk" <<STUB
#!/bin/sh
case "\$*" in *'buf = buf "X"'*) exit 0 ;; esac
exec $real_awk "\$@"
STUB
chmod +x "$d/stub-bin/awk"
out=$(cd "$d" && env PATH="$d/stub-bin:$PATH" scripts/ladder.sh --guards-only 2>&1)
rc=$?
if [ "$rc" -ne 0 ] && grep -qF "judged NOTHING" <<<"$out"; then
	report ok "a sentence counter that succeeds and prints nothing fails loudly"
else
	report no "a sentence counter that succeeds and prints nothing fails loudly" "rc=$rc" "$out"
fi

# The micro-trim case, as a fixture rather than as advice. The same three sentences twice,
# once stated at length and once shaved to a third of the bytes: under a byte cap the
# second draft bought compliance, and under the sentence cap neither does. A row loses a
# whole sentence or it does not pass.
d=$(mk ledger_row_sentences_over_cap)
sed_in_place 's/^LEDGER_ROW_SENTENCE_CAP=.*/LEDGER_ROW_SENTENCE_CAP=2/' "$d/amh.conf"
printf -- '- D-003: **A finding that took three sentences to state.** The second sentence carries narrative nobody will need again. The third repeats it at greater length still.\n' >>"$d/docs/LEDGER.md"
expect_fail "a new ledger row over the sentence cap fails" "$d" \
	"crossing rejection boundary LEDGER_ROW_SENTENCE_CAP=2"

d=$(mk ledger_row_sentences_shaved)
sed_in_place 's/^LEDGER_ROW_SENTENCE_CAP=.*/LEDGER_ROW_SENTENCE_CAP=2/' "$d/amh.conf"
printf -- '- D-003: **A finding.** The narrative. It repeats.\n' >>"$d/docs/LEDGER.md"
expect_fail "shaving that row to a third of its bytes buys nothing" "$d" \
	"crossing rejection boundary LEDGER_ROW_SENTENCE_CAP=2"

# The sentence cap is read from the config like every other threshold, and a malformed one
# fails loudly rather than switching the rung off — a cap that silently stops checking is
# the shape AMH ledger row D019 is about.
d=$(mk ledger_row_sentence_cap_malformed)
sed_in_place 's/^LEDGER_ROW_SENTENCE_CAP=.*/LEDGER_ROW_SENTENCE_CAP=six/' "$d/amh.conf"
printf -- '- D-003: **Short.** It stops.\n' >>"$d/docs/LEDGER.md"
expect_fail "a malformed sentence cap fails rather than switching the rung off" "$d" \
	"LEDGER_ROW_SENTENCE_CAP must be a non-negative integer"

# The byte backstop, which is a different rule and says so in its own words. The filler row
# carries no sentence at all, so nothing but the backstop can be firing here.
d=$(mk ledger_row_char_over_cap)
sed_in_place 's/^LEDGER_ROW_CHAR_CAP=.*/LEDGER_ROW_CHAR_CAP=80/' "$d/amh.conf"
printf -- '- D-003: long row. %s\n' "$(filler 120)" >>"$d/docs/LEDGER.md"
expect_fail "a new ledger row over the byte-counted character cap fails" "$d" \
	"crossing rejection boundary LEDGER_ROW_CHAR_CAP=80; historical committed rows"

d=$(mk ledger_row_char_committed_over_cap)
sed_in_place 's/^LEDGER_ROW_CHAR_CAP=.*/LEDGER_ROW_CHAR_CAP=80/' "$d/amh.conf"
printf -- '- D-003: committed long row. %s\n' "$(filler 120)" >>"$d/docs/LEDGER.md"
(cd "$d" && git add amh.conf docs/LEDGER.md && git commit -qm long-ledger-history)
expect_pass "an already committed over-cap ledger row is historical and exempt" "$d"

d=$(mk ledger_row_char_superseded_pointer_existing)
sed_in_place 's/^LEDGER_ROW_CHAR_CAP=.*/LEDGER_ROW_CHAR_CAP=10/' "$d/amh.conf"
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
	"docs/LEDGER_AA.md: 5 lines"

# Membership is REACHABILITY, not spelling, and this is the case that settles it: an
# all-capitals stray file satisfies every name-shaped rule (`[A-Z]+`, and LONG, so it wins
# any length-first ordering) while belonging to no chain. Ranked, it pins the rung on a
# file nobody writes to and prints `ok` over a volume that is past its cap — one untracked
# one-line file switching the guard off. The cap must still fire on the real live volume.
d=$(mk ledger_volume_archive)
sed_in_place 's/^LEDGER_LINE_CAP=800/LEDGER_LINE_CAP=4/' "$d/amh.conf"
add_volume "$d" A
printf '# archived notes\n' >"$d/docs/LEDGER_ARCHIVE.md"
expect_fail "an all-caps stray file does not become the live volume" "$d" \
	"docs/LEDGER_A.md: a row starts at line 5"

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
expect_pass_saying "the walk stops at the first gap" "$d" "docs/LEDGER_A.md: 5 lines"

# The base volume is where the chain starts, so its absence is not "no ledger yet" — that
# rendering is a skip, and a skip reads exactly like a pass. Citations are switched off in
# this fixture so the verdict under test is the only one on trial.
d=$(mk ledger_volume_no_base)
sed_in_place "s/^CITATION_SCAN_PATHS=.*/CITATION_SCAN_PATHS=''/" "$d/amh.conf"
add_volume "$d" A
rm "$d/docs/LEDGER.md"
expect_fail "a missing base volume with continuations fails instead of skipping" "$d" \
	"docs/LEDGER.md is missing while continuation volume(s) exist"

# The row pattern admits any number of volume letters. With the one-letter pattern a
# `DAA-` row matched nothing, so the cap could not fire however long the volume grew —
# the failure this whole scheme change exists to remove, and it was silent.
d=$(mk ledger_volume_multiletter_cap)
sed_in_place 's/^LEDGER_LINE_CAP=800/LEDGER_LINE_CAP=4/' "$d/amh.conf"
add_chain "$d" {A..Z} AA
expect_fail "a multi-letter row crossing the cap fails instead of passing invisibly" "$d" \
	"docs/LEDGER_AA.md: a row starts at line 5"

# The next volume's name is computed by carry, not looked up in a table that ends at Z.
# One case per transition the odometer has to get right; each is anchored on its own
# message, so no one of them can be deleted while a sibling covers for it. The chains are
# dense because the rung will not call an unreachable file live — which is why the ZZ case
# builds seven hundred volumes rather than one.
d=$(mk ledger_next_base)
sed_in_place 's/^LEDGER_LINE_CAP=800/LEDGER_LINE_CAP=4/' "$d/amh.conf"
printf -- '- D-003: crossing the cap.\n' >>"$d/docs/LEDGER.md"
expect_fail "the base volume rolls to _A / DA-" "$d" \
	"open docs/LEDGER_A.md, numbering from DA-001"

d=$(mk ledger_next_z)
sed_in_place 's/^LEDGER_LINE_CAP=800/LEDGER_LINE_CAP=4/' "$d/amh.conf"
add_chain "$d" {A..Z}
expect_fail "Z rolls to AA, which is where the old scheme simply stopped" "$d" \
	"open docs/LEDGER_AA.md, numbering from DAA-001"

d=$(mk ledger_next_az)
sed_in_place 's/^LEDGER_LINE_CAP=800/LEDGER_LINE_CAP=4/' "$d/amh.conf"
add_chain "$d" {A..Z} A{A..Z}
expect_fail "AZ rolls to BA — the carry advances the letter to its left, not the length" "$d" \
	"open docs/LEDGER_BA.md, numbering from DBA-001"

d=$(mk ledger_next_zz)
sed_in_place 's/^LEDGER_LINE_CAP=800/LEDGER_LINE_CAP=4/' "$d/amh.conf"
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
sed_in_place 's/^- D-002:/- D-002 [cited]:/' "$d/docs/LEDGER.md"
expect_fail "a [cited] marker with no citation fails" "$d" "no longer cited"

d=$(mk cite_ok)
printf '# see D-001\n' >"$d/scripts/thing.sh"
sed_in_place 's/^- D-001:/- D-001 [cited]:/' "$d/docs/LEDGER.md"
expect_pass "a citation with its marker passes" "$d"

# GNU sed accepts escaped BRE `\+` and `\?` as extensions; BSD sed does not. Reject those
# extensions in a shim on every host so the citation-row extractor stays on the shared ERE
# syntax rather than waiting for the macOS job to rediscover the parse failure.
d=$(mk cite_bsd_sed)
printf '# see D-001\n' >"$d/scripts/thing.sh"
sed_in_place 's/^- D-001:/- D-001 [cited]:/' "$d/docs/LEDGER.md"
mkdir -p "$d/bsd-bin"
real_sed=$(command -v sed)
cat >"$d/bsd-bin/sed" <<-EOF
	#!/usr/bin/env bash
	for arg in "\$@"; do
		case \$arg in *'\\+'*|*'\\?'*) exit 64 ;; esac
	done
	exec "$real_sed" "\$@"
EOF
chmod +x "$d/bsd-bin/sed"
started=$SECONDS
out=$(PATH="$d/bsd-bin:$PATH" run "$d")
rc=$?
FIXTURE_ELAPSED_SECONDS=$((SECONDS - started))
if [ "$rc" -eq 0 ]; then
	report ok "citation row extraction uses BSD/GNU-common sed syntax"
else
	report no "citation row extraction uses BSD/GNU-common sed syntax" \
		"expected exit 0, got $rc" "$out"
fi

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
sed_in_place 's/^- DAA-001:/- DAA-001 [cited]:/' "$d/docs/LEDGER_AA.md"
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

# A BINARY file whose bytes happen to match. grep reports it with a notice instead of the
# match, and which STREAM that notice goes to is version-dependent: stderr on grep >= 3.5,
# where the rung's own `2>/dev/null` eats it, but stdout on <= 3.4 — and Git for Windows ships
# 3.0. There the notice is captured as a citation token, and the rung fails naming two font
# files no ledger row can resolve (reported from a Windows adopter tree, AMH ledger row DC031).
#
# The shim is what makes the case exist at all, exactly as with the `sed` shim below: on a host
# whose grep is >= 3.5 there is nothing to reproduce and this fixture would pass against the
# broken rung. It stands in for the older grep by moving that one notice back to stdout, and it
# leaves every other line — and the exit status — alone. `-I` makes the notice unreachable in
# both versions, which is why the fixture is a pass rather than a message assertion.
#
# The fixture cites a REAL row beside the font, and the assertion reads the count rather than
# the words around it. Without both, this case asserts nothing: `mk` builds its ledger from the
# ids the copied scripts mention, `shipped-citations.sh` forbids a hyphenated id in any of them,
# so the cited set here is pinned at zero — and `0 citation(s) resolve` satisfies a match on the
# phrase alone. Deleting the rung's whole body would then have left this green (D-027(a), which
# is the defect this file already records twice).
d=$(mk cite_binary_notice)
printf 'GDEF\000 D-099 \000glyf\n' >"$d/scripts/font.ttf"
printf '# see D-001\n' >"$d/scripts/cites.sh"
sed_in_place 's/^- D-001:/- D-001 [cited]:/' "$d/docs/LEDGER.md"
mkdir -p "$d/old-grep"
real_grep=$(command -v grep)
if [ -z "$real_grep" ]; then
	# Same check `require_shim` makes below, for the same reason: a shim directory that was
	# never built leaves the fixture testing whatever `grep` the PATH happens to find.
	report no "a binary file is not a citation site, whatever grep says about it" \
		"could not build a grep shim — no grep on PATH?"
	real_grep=/nonexistent
fi
cat >"$d/old-grep/grep" <<-EOF
	#!/usr/bin/env bash
	# A failed mktemp would abort the redirection, real grep would never run, and every
	# caller would read the silence as "no match" — the silent-skip class, inside the tool
	# a fixture is using to prove a guard is not silently skipping.
	err=\$(mktemp) || exit 2
	"$real_grep" "\$@" 2>"\$err"
	rc=\$?
	while IFS= read -r line; do
		case \$line in
		*': binary file matches')
			name=\${line#*: }
			printf 'Binary file %s matches\n' "\${name%: binary file matches}"
			;;
		*) printf '%s\n' "\$line" >&2 ;;
		esac
	done <"\$err"
	rm -f "\$err"
	exit \$rc
EOF
chmod +x "$d/old-grep/grep"
started=$SECONDS
out=$(cd "$d" && PATH="$d/old-grep:$PATH" CI=1 bash scripts/ladder.sh --guards-only 2>&1)
rc=$?
FIXTURE_ELAPSED_SECONDS=$((SECONDS - started))
if [ "$rc" -ne 0 ]; then
	report no "a binary file is not a citation site, whatever grep says about it" \
		"expected exit 0, got $rc" "$out"
elif ! grep -qE '^   ok    [1-9][0-9]* citation\(s\) resolve' <<<"$out"; then
	# The verdict word AND a non-zero count: a rung that scanned nothing satisfies a bare
	# "did not fail" test, and a rung whose scan set is empty satisfies the phrase alone.
	report no "a binary file is not a citation site, whatever grep says about it" \
		"the citation rung did not report a resolved set it actually found" "$out"
else
	report ok "a binary file is not a citation site, whatever grep says about it"
fi

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

# A CRLF worktree under a `sed` that is not byte-transparent — which is a stock Windows
# checkout, not an exotic one: Git for Windows sets `core.autocrlf=true` in its system config
# at install time, and the MSYS2 sed it ships rewrites CRLF to LF even for a script that
# matches nothing. The scan is a redact-then-`cmp` against the raw file, so every text file in
# the tree differed from its own filtered stream and was reported as a credential — 529 of
# them, the harness's own shipped scripts included (AMH ledger row DC030).
#
# The shim is what makes the case exist at all: on a platform whose sed IS transparent there is
# nothing to reproduce, and this fixture would pass against the broken ladder. It stands in for
# the MSYS2 build — real sed, CR removed — and it goes on PATH for the whole ladder run,
# because that is the situation being reproduced: on Windows every sed in the run is that one.
sed_shim() { # <name> <post-filter command> -> prints a PATH directory holding that sed
	local dir=$WORK/sed_shim_$1 real
	real=$(command -v sed) || return 1
	mkdir -p "$dir"
	{
		printf '#!/usr/bin/env bash\n'
		# pipefail, so a genuinely failing sed still reports as one: without it the shim
		# returns the post-filter's status and a broken sed inside a fixture reads as
		# success.
		printf 'set -o pipefail\n'
		printf '%q "$@" | %s\n' "$real" "$2"
	} >"$dir/sed"
	chmod +x "$dir/sed"
	printf '%s' "$dir"
}

# A shim that returns nothing leaves `PATH=":$PATH"`, whose empty element is the CURRENT
# DIRECTORY — a fixture that then runs whatever happens to be named `sed` in a fixture repo.
# The suite runs without `set -e`, so this would not abort: it would quietly test something
# else. Checked rather than assumed.
require_shim() { # <name> <dir>
	[ -n "$2" ] && [ -x "$2/sed" ] && return 0
	report no "$1" "could not build a sed shim — no sed on PATH?"
	return 1
}

d=$(mk_unmodified secret_crlf)
printf 'ordinary notes, no credentials here\r\nsecond line\r\n' >"$d/notes.txt"
SHIM_SED=$(sed_shim crlf "tr -d '\\r'")
require_shim "a CRLF file under a non-transparent sed is not a credential" "$SHIM_SED" || SHIM_SED=/nonexistent
started=$SECONDS
out=$(cd "$d" && PATH="$SHIM_SED:$PATH" CI=1 bash scripts/ladder.sh --guards-only 2>&1)
rc=$?
FIXTURE_ELAPSED_SECONDS=$((SECONDS - started))
if [ "$rc" -ne 0 ]; then
	report no "a CRLF file under a non-transparent sed is not a credential" "expected exit 0, got $rc" "$out"
elif ! grep -qF "   ok    no credential-shaped strings" <<<"$out"; then
	# The verdict word is part of the assertion: a scan that silently checked nothing would
	# satisfy a bare "not flagged" test, and this rung's whole failure mode is a green that
	# was never earned.
	report no "a CRLF file under a non-transparent sed is not a credential" \
		"the scan did not report a clean tree" "$out"
else
	report ok "a CRLF file under a non-transparent sed is not a credential"
fi

# The other end of the same subtraction, and the one that decides whether it is safe: a
# platform whose `sed` TRUNCATES. The filter's stream and the baseline's are both cut off in
# the same place, so they agree — and a scan that trusted the agreement would print a green
# over bytes it never read, with a live credential sitting crossing the cut. The baseline has to
# earn its place as the file's stand-in, which is why the rung compares it against the file
# itself (apart from carriage returns) before subtracting anything.
#
# The credential is planted PAST the truncation point on purpose: a fixture that plants it
# before would be satisfied by the ordinary finding and could not tell the two verdicts apart.
d=$(mk_unmodified secret_truncating_sed)
{
	printf 'line one\nline two\n'
	printf 'key = %s\n' "$(akia_token)"
} >"$d/secrets.txt"
SHIM_SED=$(sed_shim truncate 'head -2')
require_shim "a truncating sed makes the scan refuse rather than report clean" "$SHIM_SED" || SHIM_SED=/nonexistent
started=$SECONDS
out=$(cd "$d" && PATH="$SHIM_SED:$PATH" CI=1 bash scripts/ladder.sh --guards-only 2>&1)
rc=$?
FIXTURE_ELAPSED_SECONDS=$((SECONDS - started))
if [ "$rc" -eq 0 ]; then
	report no "a truncating sed makes the scan refuse rather than report clean" \
		"the ladder stayed green over a file the filter never read to the end" "$out"
elif ! grep -qF "did not reproduce" <<<"$out"; then
	report no "a truncating sed makes the scan refuse rather than report clean" \
		"it failed for some other reason than the unreadable baseline" "$out"
else
	report ok "a truncating sed makes the scan refuse rather than report clean"
fi

# ...and the control the fix itself now depends on. The scan subtracts `redact.sh --baseline`
# from `redact.sh`, so a redact.sh without that mode leaves it comparing against nothing at
# all. It must refuse to scan and say so, not report the tree clean: "I did not manage to
# look" is the one verdict this rung may never render as a pass (AMH ledger row DC002).
d=$(mk secret_no_baseline)
sed_in_place 's/^--baseline)/--baseline-removed)/' "$d/scripts/redact.sh"
expect_fail "a redact.sh with no --baseline mode makes the scan refuse rather than pass" "$d" \
	"has no working --baseline mode"

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
sed_in_place 's/^REMOTE_FLAG=.*/REMOTE_FLAG=AMH-REMOTE/' "$d/amh.conf"
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

# The `.resumed` sibling rearms too. The bootstrap's pattern stopped at the slug, so it deleted
# the advisory state and left the ledger of what a session went ahead with — and both reports
# built on that file then spanned every session sharing the container. Hook mode is required:
# the guard writes `.resumed` only on the hook path, so a `--command` fixture cannot see this.
#
# Both directions matter and are separately silent. (a) `--advisory-report` must NAME a deletion
# abandoned in the new session even when the same text was resumed in the old one — with the
# stale sibling in place it printed nothing at all, which reads exactly like compliance.
# (b) `--spawn-report` must count only this session's spawns.
d=$(mk ss_rearms_resumed_sibling)
hook_cmd() { printf '{"tool_name":"Bash","tool_input":{"command":"%s"}}' "$1"; }
(cd "$d" && hook_cmd 'rm -rf tmp/build' | scripts/command-guard.sh >/dev/null 2>&1)
(cd "$d" && hook_cmd 'rm -rf tmp/build' | scripts/command-guard.sh >/dev/null 2>&1) # resumed
(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh >/dev/null 2>&1)
(cd "$d" && hook_cmd 'rm -rf tmp/build' | scripts/command-guard.sh >/dev/null 2>&1) # advised, abandoned
out=$(cd "$d" && scripts/command-guard.sh --advisory-report 2>&1)
if grep -qF 'tmp/build' <<<"$out"; then
	report ok "an advisory abandoned after the bootstrap is reported, not hidden by the old session"
else
	report no "an advisory abandoned after the bootstrap is reported, not hidden by the old session" "report was: [$out]"
fi

d=$(mk ss_rearms_spawn_count)
(cd "$d" && printf '{"tool_name":"Task","tool_input":{}}' | scripts/command-guard.sh --pre-task >/dev/null 2>&1)
(cd "$d" && printf '{"tool_name":"Task","tool_input":{}}' | scripts/command-guard.sh --pre-task >/dev/null 2>&1) # proceeds
before=$(cd "$d" && scripts/command-guard.sh --spawn-report 2>/dev/null)
(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh >/dev/null 2>&1)
after=$(cd "$d" && scripts/command-guard.sh --spawn-report 2>/dev/null)
if [ -n "$before" ] && [ -z "$after" ]; then
	report ok "the spawn count is session-scoped: recorded before the bootstrap, gone after"
else
	report no "the spawn count is session-scoped: recorded before the bootstrap, gone after" "before=[$before] after=[$after]"
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
sed_in_place 's/^\tst_allowed .cat README.md./\tst_allowed "cat .env"/' "$d/scripts/command-guard.sh"
expect_runner_saying "a regressed rail self-test fails the ladder" run_rails "$d" 1 \
	"self-test failed"

d=$(mk_unmodified rail_noexec)
sed_in_place 's/^\tst_allowed .cat README.md./\tst_allowed "cat .env"/' "$d/scripts/command-guard.sh"
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
	printf '  SKIP 12 shipped-integrity case(s): no sha256sum or shasum on this machine, so no fixture manifest could be built\n' >&2
else
	d=$(mk_integrity integrity_ok)
	expect_pass_saying "an untouched tree matches the manifest and says how many it checked" "$d" \
		"   ok    4 shipped script(s) match the published hashes"

	# The whole point of the rung: a local edit to a shipped script. session-start.sh is the
	# subject because nothing else in this suite executes it during a `--guards-only` run, so
	# the only rung that can react is the one under test.
	d=$(mk_integrity integrity_edited)
	printf '\n# a local edit to a shipped script\n' >>"$d/scripts/session-start.sh"
	out=$(run "$d")
	rc=$?
	if [ "$rc" -eq 0 ]; then
		report no "an ordinary content mismatch retains the normal remediation without claiming CRLF" \
			"expected a failure, ladder passed" "$out"
	elif ! grep -qF "If you edited it:" <<<"$out"; then
		report no "an ordinary content mismatch retains the normal remediation without claiming CRLF" \
			"the normal edited-file remediation was absent" "$out"
	elif grep -qF "CRLF worktree" <<<"$out"; then
		report no "an ordinary content mismatch retains the normal remediation without claiming CRLF" \
			"the diagnostic claimed CRLF without Git establishing it" "$out"
	else
		report ok "an ordinary content mismatch retains the normal remediation without claiming CRLF"
	fi

	# Git's eol report is authoritative for what its checkout machinery put in the worktree.
	# Convert one tracked script after the manifest and commit are established: the published
	# hash remains LF while `git ls-files --eol` now reports w/crlf for the affected path.
	d=$(mk_integrity integrity_script_crlf)
	crlf_endings "$d/scripts/session-start.sh"
	out=$(run "$d")
	rc=$?
	if [ "$rc" -eq 0 ]; then
		report no "a CRLF shipped-script mismatch names line endings and .gitattributes" \
			"expected a failure, ladder passed" "$out"
	elif ! grep -qF "CRLF worktree" <<<"$out" || ! grep -qF ".gitattributes" <<<"$out"; then
		report no "a CRLF shipped-script mismatch names line endings and .gitattributes" \
			"the targeted line-ending remediation was incomplete" "$out"
	else
		report ok "a CRLF shipped-script mismatch names line endings and .gitattributes"
	fi

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

	# The manifest as a Windows checkout hands it over: every line ending in CRLF. The hash
	# field comes FIRST, so it still measures 64 characters and the corruption arm below never
	# fires; only the filename carries the CR, so the rung looked for `scripts/ladder.sh<CR>`,
	# reported five present scripts as deleted, and told the reader to re-run the init script
	# to restore them — the "true verdict wrapped in a false description of what was checked"
	# this rung's own comment says it must avoid (AMH ledger row DC030).
	d=$(mk_integrity integrity_crlf)
	crlf_endings "$d/scripts/MANIFEST.sha256"
	expect_pass_saying "a CRLF manifest names the same files a LF one does" "$d" \
		"   ok    4 shipped script(s) match the published hashes"

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
sed_in_place 's/^MERGE_MODE=.*/MERGE_MODE=branch-train/' "$d/amh.conf"
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

# The same rung over a message stream crossing the pipe buffer, which is where it fails OPEN.
# `git log --format=%B` prints the NEWEST commit first, so a token in the newest commit is
# matched almost immediately and everything behind it is still pending — grep exits, the
# writer takes EPIPE, `pipefail` makes a successful match a failed pipeline, and the token
# is silently not reported. Commit messages are inherently multi-line, so unlike the rail's
# payload case size alone is enough here.
#
# One commit with a long body rather than thousands of commits: the rung reads the message
# STREAM, so what matters is its total size and where the token sits in it, and a
# four-thousand-commit fixture would cost minutes to buy the same bytes.
d=$(mk poison_token_long_stream)
(
	cd "$d" || exit 1
	printf 'a change\n' >>docs/STATE.md
	{
		printf 'checkpoint [skip ci]\n\n'
		awk 'BEGIN { for (i = 0; i < 4000; i++) print "padding line to push this stream crossing the pipe buffer" }'
	} >"$d/msg"
	# `-F`, never `-m "$(cat ...)"`: a message this long as a single argv element is over
	# MAX_ARG_STRLEN and git dies with "Argument list too long", leaving no commit and a
	# rung that reports "no new commits to check" — green, for a reason the fixture is not
	# about. It failed exactly that way once before this comment existed.
	git commit -qa -F "$d/msg"
)
# Same reason as the payload case above: the stream's size IS the fixture. Verified against the
# pre-fix ladder, which reported the token (hollow) on a 563-byte stream and printed `ok clean`
# on this one.
msg_bytes=$(wc -c <"$d/msg")
rm -f "$d/msg"
if [ "$msg_bytes" -le 131072 ]; then
	report no "a poison token early in a long message stream still fails" \
		"message stream is only $msg_bytes bytes — too small to reach the defect, so this case would pass against the unfixed ladder too"
else
	expect_fail "a poison token early in a long message stream still fails" "$d" "[skip ci]"
fi

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
sed_in_place "s|^RULE_FILES=''|RULE_FILES='amh.conf'|" "$d/amh.conf"
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

# The abandoned-advisory line: the only visible consequence of clearing a destructive
# prompt by dropping the deletion. Both directions matter — an advisory that was
# re-attempted must NOT be listed, or the line degrades into "you used rm today" and
# stops meaning anything. The state file is pointed at the fixture's own directory, so
# this asserts the ladder's plumbing to the guard rather than whatever /tmp happens to hold.
d=$(mk advisory_destructive_abandoned)
started=$SECONDS
# Driven through the HOOK path, not `--command`, because that is the only path where a pass
# means the command runs next — and therefore the only one that records a re-attempt. Using
# `--command` here would leave the resumed target listed, which is exactly what the rail now
# says about an inspection: asking the guard about text twice is not doing anything twice.
hook() { printf '{"hook_event_name":"PreToolUse","tool_name":"Bash","tool_input":{"command":"%s"}}' "$1"; }
out=$(cd "$d" && DESTRUCTIVE_ADVISORY_STATE="$d/dstate" bash -c '
	h() { printf "{\"hook_event_name\":\"PreToolUse\",\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"$1\"}}"; }
	h "rm -rf /tmp/fixture-abandoned" | scripts/command-guard.sh >/dev/null 2>&1
	h "rm -rf /tmp/fixture-resumed"   | scripts/command-guard.sh >/dev/null 2>&1
	h "rm -rf /tmp/fixture-resumed"   | scripts/command-guard.sh >/dev/null 2>&1
	env -u CI scripts/ladder.sh --guards-only 2>&1')
elapsed=$((SECONDS - started))
FIXTURE_ELAPSED_SECONDS=$elapsed
if ! grep -qF "never re-attempted under this exact text" <<<"$out"; then
	report no "an abandoned destructive advisory is named in the ladder" "no note line" "$out"
elif ! grep -qF "/tmp/fixture-abandoned" <<<"$out"; then
	report no "an abandoned destructive advisory is named in the ladder" "the note omitted the abandoned target" "$out"
elif grep -qF "/tmp/fixture-resumed" <<<"$out"; then
	report no "an abandoned destructive advisory is named in the ladder" "the note listed a re-attempted target" "$out"
elif grep -qE "(WARN|FAIL) +destructive advisories" <<<"$out"; then
	report no "an abandoned destructive advisory is named in the ladder" "the line is a verdict; it must be a note" "$out"
elif ! grep -qF "guards clean (0 warning(s))" <<<"$out"; then
	# The label is not the property. `note` earns its place only by touching no counter,
	# so this asserts the COUNT in the verdict line: a `note` that increments WARNS reads
	# identically above and turns this fixture red — which the label check alone did not.
	report no "an abandoned destructive advisory is named in the ladder" "the note moved the warning count" "$out"
else
	report ok "an abandoned destructive advisory is named in the ladder"
fi

d=$(mk advisory_ci)
sed_in_place "s|^RULE_FILES=''|RULE_FILES='amh.conf'|" "$d/amh.conf"
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
