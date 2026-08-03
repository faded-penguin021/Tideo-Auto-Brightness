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
	write_manifest "$d"
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
		grep -ohE 'D[A-Z]?-[0-9]+' "$d/scripts"/*.sh | sort -u | grep -vxE 'D-00[12]' |
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

run() { (cd "$1" && CI=1 scripts/ladder.sh --guards-only 2>&1); }

# The advisory rung starts with `in_ci && return`, so nothing that runs under `run()`
# can ever reach it. Local advisories are warn-only and cannot fail the ladder, so the
# assertion is on the warning TEXT.
run_local() { (cd "$1" && env -u CI scripts/ladder.sh --guards-only 2>&1); }

# --- assertions -------------------------------------------------------------
report() { # <ok|no> <name> <detail...>
	if [ "$1" = ok ]; then
		PASSED=$((PASSED + 1))
	else
		FAILED=$((FAILED + 1))
		shift
		printf '  FAIL %s\n' "$1" >&2
		shift
		[ $# -gt 0 ] && printf '%s\n' "$*" | sed 's/^/       /' >&2
	fi
}

expect_pass() { # <name> <dir>
	local out rc
	out=$(run "$2")
	rc=$?
	if [ "$rc" -eq 0 ]; then report ok; else report no "$1" "expected exit 0, got $rc" "$out"; fi
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
	local out rc
	out=$(run "$2")
	rc=$?
	if [ "$rc" -ne 0 ]; then
		report no "$1" "expected exit 0, got $rc" "$out"
	elif ! printf '%s' "$out" | grep -qF "$3"; then
		report no "$1" "passed but the output never mentioned '$3'" "$out"
	else
		report ok
	fi
}

expect_fail() { # <name> <dir> <grep-pattern>
	local out rc
	out=$(run "$2")
	rc=$?
	if [ "$rc" -eq 0 ]; then
		report no "$1" "expected a failure, ladder passed" "$out"
	elif ! printf '%s' "$out" | grep -qF "$3"; then
		report no "$1" "failed as expected but the message never mentioned '$3'" "$out"
	else
		report ok
	fi
}

# Asserts THREE things, and the third was missing for as long as this helper existed: exit
# 0, the expected text, and that a WARN line was actually printed. Without the last one the
# name was a lie — the text it greps for can be an `ok` line, so turning the soft-cap `warn`
# into an `ok` left every expect_warn fixture green. That matters most for the landing
# check's edit branch, which permits a shrink ONLY because the size warning stays armed: the
# single property making that branch safe was verified by nothing.
expect_warn() { # <name> <dir> <grep-pattern>
	local out rc
	out=$(run "$2")
	rc=$?
	if [ "$rc" -ne 0 ]; then
		report no "$1" "expected exit 0 with a warning, got $rc" "$out"
	elif ! printf '%s\n' "$out" | grep -q '^   WARN '; then
		report no "$1" "expected a WARN line and there was none" "$out"
	elif ! printf '%s' "$out" | grep -qF "$3"; then
		report no "$1" "no output mentioning '$3'" "$out"
	else
		report ok
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

# --- baseline
d=$(mk baseline)
expect_pass "clean fixture passes" "$d"

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

# --- secret shapes
d=$(mk secret_plain)
tok=$(akia_token)
printf 'key = %s\n' "$tok" >"$d/scripts/deploy.sh"
out=$(run "$d")
if printf '%s' "$out" | grep -q 'credential-shaped'; then
	# The diagnostic must name the file and the position and NOTHING else. A
	# regression to printing the matching line would defeat the whole guard.
	if printf '%s' "$out" | grep -qF "$tok"; then
		report no "secret scan is value-free" "the diagnostic printed the token itself" "$out"
	else
		report ok
	fi
	report ok
else
	report no "secret-shaped string is caught" "not flagged" "$out"
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
if printf '%s' "$out" | grep -qF \
	"· ⚠ REMOTE_FLAG 'AMH-REMOTE' is not a valid shell variable name — toolchain bootstrap SKIPPED"; then
	report ok
else
	report no "an invalid REMOTE_FLAG is announced, not swallowed" "no banner" "$out"
fi
# ...and it must actually have SKIPPED. The banner and the bootstrap running anyway would
# have satisfied the assertion above, which is why the fixture's bootstrap announces
# itself: the claim under test is a claim about what did not happen.
if printf '%s' "$out" | grep -qF "BOOTSTRAP RAN"; then
	report no "an invalid REMOTE_FLAG really does skip the bootstrap" "it ran anyway" "$out"
else
	report ok
fi
# ...and it must still be a WARNING. A boot hook that refuses to let the session start
# over a malformed config value is worse than the silent skip it replaces.
if (cd "$d" && env AMH_REMOTE=1 bash scripts/session-start.sh >/dev/null 2>&1); then
	report ok
else
	report no "an invalid REMOTE_FLAG is not fatal" "session-start exited non-zero"
fi

# The exec bit must have no vote. This is D-019's shape in the file that boots everything:
# a bootstrap arriving 0644 from an archive extraction vanished without a line.
d=$(mk ss_bootstrap_noexec)
mk_bootstrap "$d" 0
chmod -x "$d/scripts/bootstrap.sh"
out=$(cd "$d" && env AMH_REMOTE=1 bash scripts/session-start.sh 2>&1)
if printf '%s' "$out" | grep -qF "BOOTSTRAP RAN"; then
	report ok
else
	report no "a non-executable bootstrap still runs" "the bootstrap did not run" "$out"
fi

# A failing bootstrap is reported and the session continues — the property the bootstrap's
# own loud-but-non-fatal design depends on, asserted at the caller where it actually holds.
d=$(mk ss_bootstrap_fails)
mk_bootstrap "$d" 1
out=$(cd "$d" && env AMH_REMOTE=1 bash scripts/session-start.sh 2>&1)
rc=$?
if [ "$rc" -eq 0 ] && printf '%s' "$out" | grep -qF "bootstrap reported a problem"; then
	report ok
else
	report no "a failing bootstrap warns without killing the session" "rc=$rc" "$out"
fi

# The flag set, and nothing to run. Legitimate — an adopter may have no toolchain to
# install — but a remote session skipping a configured step deserves its one line.
d=$(mk ss_bootstrap_absent)
out=$(cd "$d" && env AMH_REMOTE=1 bash scripts/session-start.sh 2>&1)
if printf '%s' "$out" | grep -qF "does not exist" && printf '%s' "$out" | grep -qF "SKIPPED"; then
	report ok
else
	report no "a missing bootstrap under the remote flag says so" "no line about it" "$out"
fi

# The negative control, and the reason the cases above are not just asserting that
# session-start.sh prints a lot: with the flag UNSET the bootstrap must not run at all.
d=$(mk ss_not_remote)
mk_bootstrap "$d" 0
out=$(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
if printf '%s' "$out" | grep -qF "BOOTSTRAP RAN"; then
	report no "a local session does not run the bootstrap" "it ran anyway" "$out"
else
	report ok
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
if printf '%s' "$out" | grep -qF "docs/RUNBOOK.md"; then
	report no "the protocol pointer omits a runbook the repo does not have" "it named it anyway" "$out"
else
	report ok
fi

d=$(mk ss_with_runbook)
printf '# RUNBOOK\n' >"$d/docs/RUNBOOK.md"
out=$(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
if printf '%s' "$out" | grep -qF "playbook in docs/RUNBOOK.md"; then
	report ok
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
	if printf '%s' "$out" | grep -qF "tag v3.1.0 is in this clone"; then
		report ok
	else
		report no "an existing local release tag is reported as present" "it was not" "$out"
	fi

	# (b) The state the line EXISTS for: no such tag anywhere. Only this one may say UNRELEASED.
	out=$(cd "$d2" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
	if printf '%s' "$out" | grep -qF "NO tag v3.1.0 exists on origin — UNRELEASED"; then
		report ok
	else
		report no "a version with no tag on origin is reported as unreleased" "it was not" "$out"
	fi

	# (c) The regression this suite previously LOCKED IN: tagged on origin, absent locally.
	# A clone that never fetched tags is the steady state, not an incident, and reporting it as
	# unreleased made the line cry wolf on every session in the repo that ships it. It must
	# name the tag as existing and must NOT say UNRELEASED.
	out=$(cd "$d3" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
	if printf '%s' "$out" | grep -qF "tag v3.1.0 exists on origin" &&
		! printf '%s' "$out" | grep -qF "UNRELEASED"; then
		report ok
	else
		report no "a tag on origin but not in the clone is reported as existing, not as unreleased" \
			"it was not, or it cried unreleased" "$out"
	fi

	# (d) Cannot ask is not an answer. A repo with no origin at all must say so and must make no
	# claim in either direction — the failure being refused is "unreachable" rendering as "absent".
	out=$(cd "$d4" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
	if printf '%s' "$out" | grep -qF "not be reached to check" &&
		! printf '%s' "$out" | grep -qF "UNRELEASED"; then
		report ok
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
if printf '%s' "$out" | grep -qF "no version on its first line" &&
	! printf '%s' "$out" | grep -qF "UNRELEASED"; then
	report ok
else
	report no "an empty VERSION_FILE is reported and no tag claim is made" "it claimed one anyway" "$out"
fi

# Half-configuration is a typo, and silence would render it identically to the adopter who
# deliberately set neither key.
d=$(mk ss_release_half_configured)
printf '3.1.0\n' >"$d/VERSION"
printf 'VERSION_FILE=VERSION\n' >>"$d/amh.conf"
out=$(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
if printf '%s' "$out" | grep -qF "needs BOTH VERSION_FILE and RELEASE_TAG_PREFIX"; then
	report ok
else
	report no "setting one release key and not the other is reported" "it was silent" "$out"
fi

# A directory is not a missing file, and saying so costs one branch.
d=$(mk ss_release_dir_version)
set_release_keys "$d" docs v
out=$(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
if printf '%s' "$out" | grep -qF "is a directory, not a file"; then
	report ok
else
	report no "a VERSION_FILE that is a directory says so" "it reported something else" "$out"
fi

# Only the first line is the version: a trailing note would otherwise be concatenated into a tag
# name no release can match, and the banner would report that mangled string as unreleased.
d=$(mk ss_release_multiline_version)
printf '3.1.0\nnotes about the release\n' >"$d/VERSION"
set_release_keys "$d" VERSION v
out=$(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
if printf '%s' "$out" | grep -qF "says 3.1.0" && ! printf '%s' "$out" | grep -qF "notes"; then
	report ok
else
	report no "only the first line of VERSION_FILE is read" "the rest leaked into the version" "$out"
fi

# The negative control, and the one that protects every existing adopter: amh.conf files
# written before these keys existed leave them empty, and an empty key means silence — not a
# line about a tag prefix nobody chose. A VERSION file is present to make the case sharp.
d=$(mk ss_release_off)
printf '3.1.0\n' >"$d/VERSION"
out=$(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
if printf '%s' "$out" | grep -qiF "release:"; then
	report no "the release line stays off when the keys are unset" "it printed anyway" "$out"
else
	report ok
fi

# Misconfiguration is loud and makes no claim about the tag either way. The failure mode being
# refused is a banner that reads a missing file, gets an empty version, and announces that the
# tag for "" is absent.
d=$(mk ss_release_no_version_file)
set_release_keys "$d" harness/VERSION v
out=$(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
if printf '%s' "$out" | grep -qF "does not exist — release line SKIPPED" &&
	! printf '%s' "$out" | grep -qF "in this clone"; then
	report ok
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
# Mutation: a rail whose self-test fails must turn the ladder red. Without this the
# whole section could print nothing and no fixture would notice.
d=$(mk rail_regressed)
# Mutate the fixture matrix itself, not the tail of the file: a function appended
# after the dispatcher is defined too late to ever run, which is a mutation that
# proves nothing.
sed -i 's/^\tst_allowed .cat README.md./\tst_allowed "cat .env"/' "$d/scripts/command-guard.sh"
expect_fail "a regressed rail self-test fails the ladder" "$d" "self-test failed"

d=$(mk rail_noexec)
sed -i 's/^\tst_allowed .cat README.md./\tst_allowed "cat .env"/' "$d/scripts/command-guard.sh"
chmod -x "$d/scripts/command-guard.sh"
expect_fail "a non-executable rail is still self-tested" "$d" "self-test failed"

# --- shipped-script integrity
# The manifest is the only thing in an adopter's tree that can tell an upgraded script from
# an edited one. Every branch below is a different way for that answer to be wrong, and the
# two that matter most are the ones that must NOT be failures: an adopter with no manifest,
# and a machine with no hashing tool. Both stay green and both say so out loud.
if [ -z "$HASHER" ]; then
	printf '  SKIP 10 shipped-integrity case(s): no sha256sum or shasum on this machine, so no fixture manifest could be built\n' >&2
else
	d=$(mk integrity_ok)
	expect_pass_saying "an untouched tree matches the manifest and says how many it checked" "$d" \
		"   ok    4 shipped script(s) match the published hashes"

	# The whole point of the rung: a local edit to a shipped script. session-start.sh is the
	# subject because nothing else in this suite executes it during a `--guards-only` run, so
	# the only rung that can react is the one under test.
	d=$(mk integrity_edited)
	printf '\n# a local edit to a shipped script\n' >>"$d/scripts/session-start.sh"
	expect_fail "an edited shipped script fails against the published hash" "$d" \
		"does not match the hash the harness published for it"

	# Absence is not a failure — an adopter who upgraded by copying *.sh before the manifest
	# existed has none, and failing them for following the documented path would be a fix
	# billed to the person it broke. It is a WARN and not a `skip`, and `expect_warn` is what
	# asserts that: deleting the manifest is also the documented way to live with a deliberate
	# local patch, so it is the one off-switch an adopter reaches on purpose, and `skip` is
	# counted by nothing and vanishes from the summary line.
	d=$(mk integrity_absent)
	rm -f "$d/scripts/MANIFEST.sha256"
	expect_warn "an absent manifest warns that the rung checked nothing" "$d" \
		"   WARN  scripts/MANIFEST.sha256 is absent"

	# A manifest that outlived the script it names. Same signature as a deleted rung, which is
	# why it is a failure and not a skip.
	d=$(mk integrity_script_gone)
	rm -f "$d/scripts/session-start.sh"
	expect_fail "a manifest entry with no file behind it fails" "$d" \
		"which is not in this tree"

	# A manifest this cannot parse verifies nothing, so it must not be read past in silence.
	d=$(mk integrity_malformed)
	printf 'not-a-hash scripts/ladder.sh\n' >>"$d/scripts/MANIFEST.sha256"
	expect_fail "a malformed manifest line fails rather than being skipped over" "$d" \
		"is not a sha256 entry naming a shipped script"

	# ...and the degenerate case the parser makes possible: a file whose every line is a
	# comment parses cleanly, checks nothing, and would otherwise print `ok 0 shipped
	# script(s)`. A green earned by an empty manifest is the one verdict this rung may never
	# give.
	d=$(mk integrity_empty)
	printf '# nothing but a comment\n' >"$d/scripts/MANIFEST.sha256"
	expect_fail "a manifest listing no scripts fails instead of passing vacuously" "$d" \
		"lists no scripts"

	# Deleting ONE line excuses ONE script, and the entry for the ladder is the one deletion
	# that excuses the file deciding whether anything else is excused. Refused, or every other
	# verdict this rung gives is worth nothing — and this fixture is what stops a later
	# simplification from dropping the self-check as redundant.
	d=$(mk integrity_self_excused)
	grep -v ' scripts/ladder\.sh$' "$d/scripts/MANIFEST.sha256" >"$d/m" &&
		mv "$d/m" "$d/scripts/MANIFEST.sha256"
	printf '\n# a rung I quietly deleted\n' >>"$d/scripts/ladder.sh"
	expect_fail "a manifest that does not cover the ladder itself fails" "$d" \
		"does not cover scripts/ladder.sh"

	# The residue that leaves, asserted rather than left to be discovered: any OTHER line can
	# be removed, and the only signal is the count. The assertion is on the count, because a
	# count nobody reads is not a signal — and if this rung ever grows a way to refuse this
	# case, this fixture is what will notice.
	d=$(mk integrity_one_excused)
	grep -v ' scripts/session-start\.sh$' "$d/scripts/MANIFEST.sha256" >"$d/m" &&
		mv "$d/m" "$d/scripts/MANIFEST.sha256"
	printf '\n# a local edit nobody will hear about\n' >>"$d/scripts/session-start.sh"
	expect_pass_saying "an excused script is unreported except in the count, which drops" "$d" \
		"   ok    3 shipped script(s) match the published hashes"

	# A manifest entry may name a shipped script and nothing else. Left unconstrained the rung
	# will hash any path it is handed and then describe /etc/hostname as a shipped script the
	# harness will restore — a true hash comparison wrapped in a false account of what was
	# checked.
	d=$(mk integrity_stray_path)
	printf '%s  ../outside.sh\n' "$(fixture_sha256 "$d/scripts/ladder.sh")" \
		>>"$d/scripts/MANIFEST.sha256"
	expect_fail "a manifest entry pointing outside scripts/ is refused, not hashed" "$d" \
		"is not a sha256 entry naming a shipped script"

	# No hashing tool on PATH. This is AMH ledger row D019's shape again — the rung is switched
	# off by a property of the MACHINE, which is not its subject — so it warns rather than
	# skipping, and it must stay non-fatal. The PATH is CONSTRUCTED from the tools the ladder
	# needs rather than filtered, because subtracting the directory holding sha256sum deletes
	# /usr/bin on most machines and every rung then dies at exit 127.
	d=$(mk integrity_no_hasher)
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
		elif printf '%s' "$out" | grep -qF "   WARN  neither sha256sum nor shasum is on PATH"; then
			report ok
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
if printf '%s' "$out" | grep -qF "merge mode: branch-train — main's history is squashed"; then
	report ok
else
	report no "a branch-train repo is told its default branch's log is not its past" "no line" "$out"
fi

# The negative control, and the reason the key is read at all: under branch-per-change the
# default branch's history IS the record, so the line would be a lie.
d=$(mk ss_merge_mode_per_change)
out=$(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
if printf '%s' "$out" | grep -qF "branch-train"; then
	report no "a branch-per-change repo is not told its history is squashed" "it said so anyway" "$out"
else
	report ok
fi

# An amh.conf with no MERGE_MODE line at all. `amh.conf.example` ships the key, so this is not
# the state a fresh instantiation is in — it is the state an adopter can put their own file in
# at any time, because that file is theirs and the harness cannot upgrade it. Without the
# default in the script the banner dies under `set -u`, entirely, at the top of the session.
d=$(mk ss_merge_mode_unset)
grep -v '^MERGE_MODE=' "$d/amh.conf" >"$d/t" && mv "$d/t" "$d/amh.conf"
out=$(cd "$d" && env -u AMH_REMOTE bash scripts/session-start.sh 2>&1)
rc=$?
if [ "$rc" -eq 0 ] && printf '%s' "$out" | grep -qF "AMH session start" &&
	! printf '%s' "$out" | grep -qF "branch-train"; then
	report ok
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
out=$(run_local "$d")
if printf '%s' "$out" | grep -qF "touches legislation"; then
	report ok
else
	report no "an uncommitted legislation edit warns" "no rule-review warning" "$out"
fi

# A completed plan may now retire whole into the optional archive. The advisory is coaching,
# not enforcement, but deletion-only coaching directly contradicts P2/P16 at the action point.
d=$(mk advisory_plan_lifecycle)
mkdir -p "$d/docs/plans"
printf '# completed plan\n' >"$d/docs/plans/completed.md"
out=$(run_local "$d")
if printf '%s' "$out" | grep -qF "Move a completed plan worth retaining whole to docs/history/ when that archive tier exists; otherwise delete it"; then
	report ok
else
	report no "an orphaned plan is coached toward archive-or-delete" "no archive-or-delete warning" "$out"
fi

d=$(mk advisory_ci)
sed -i "s|^RULE_FILES=''|RULE_FILES='amh.conf'|" "$d/amh.conf"
printf '\n# an uncommitted legislation edit\n' >>"$d/amh.conf"
out=$(run "$d")
if printf '%s' "$out" | grep -qF "Local advisories"; then
	report no "advisories stay out of CI" "the advisory section ran under CI=1" "$out"
else
	report ok
fi

# =============================================================================
printf '\n%d passed, %d failed\n' "$PASSED" "$FAILED"
[ "$FAILED" -eq 0 ]
