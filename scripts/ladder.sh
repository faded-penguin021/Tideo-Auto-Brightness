#!/usr/bin/env bash
# AMH — the acceptance ladder: ONE verification entrypoint, shared by the agent and
# CI by construction (P4). CI invokes this exact script, so "green locally, red in
# CI" can only ever mean environment, never a lockstep the humans forgot to update.
#
#   scripts/ladder.sh                 fast guards, then the full verification set
#   scripts/ladder.sh --guards-only   guards only (seconds) — for docs-only work
#
# Repo-agnostic by design, which is what lets a repo verify it runs the harness's
# own artifact byte-for-byte. Everything repo-specific lives in three places:
#   amh.conf          values (branches, size bands, scan scope)
#   scripts/guards/*  extra guards this repo has earned
#   scripts/verify.sh the full test/build/lint set (rung 3)
#
# No `set -e`: every guard must run so one change gets ONE complete report instead of
# a whack-a-mole sequence of first failures. Failures are counted, not thrown.
#
# On `AMH ledger row DNNN` references below: they point at the HARNESS's ledger, which
# explains why this script is shaped the way it is. They are deliberately NOT written as
# `D-NNN` citations, because a citation is a promise that the ID resolves — and in your
# repository it never can, since those rows are ours and cannot appear in your ledger.
# Written as citations they made the ladder's citation guard fail on a repo its owner had
# not yet touched, for rows they could not have written.

set -uo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

GUARDS_ONLY=0
case "${1:-}" in
--guards-only) GUARDS_ONLY=1 ;;
"") ;;
-h | --help)
	sed -n '2,16p' "$0"
	exit 0
	;;
*)
	printf 'usage: %s [--guards-only]\n' "$0" >&2
	exit 2
	;;
esac

# --- configuration ----------------------------------------------------------
DEFAULT_BRANCH=main
# No BRANCH_PREFIX default here: the ladder never reads it. A default for a key this
# script does not use is a claim it honours one — command-guard.sh and session-start.sh
# are where the prefix lives.
STATE_FILE=docs/STATE.md
# The pair of post-action ceilings comprises BOTH of these, and a landing has to satisfy both: see
# count_sentences for why one number cannot do this job alone.
STATE_COMPRESS_TO_KB=9
STATE_COMPRESS_TO_SENTENCES=50
STATE_WARN_KB=14
STATE_HARD_KB=16
# Largest shrink above the compression trigger that still counts as an ordinary edit rather than a
# compression pass that stopped short. See guard_state_size for why this exists.
STATE_EDIT_DELTA_BYTES=1024
STATE_REQUIRED_SECTIONS='## Project|## Current state|## Changelog'
STATE_OWNER_QUEUE_SECTION='## Owner queue'
LEDGER_DIR=docs
LEDGER_BASENAME=LEDGER
LEDGER_LINE_CAP=800
LEDGER_ROW_SENTENCE_CAP=6
# A second rejection boundary that catches pathologically dense sentences.
LEDGER_ROW_CHAR_CAP=2000
CITATION_SCAN_PATHS='scripts .github'
CITATION_EXCLUDE=''
POISON_TOKENS='[skip ci]|[ci skip]'
# Extended regex an author/committer address must match WHOLE (it is wrapped in ^(…)$).
# EMPTY here on purpose, and empty is a valid setting: see guard_author_identity. An
# adopter who never sets this key still gets the half of that guard that needs no list.
AUTHOR_EMAIL_ALLOW=''
PLAN_DIR=docs/plans
RULE_FILES=''
# shellcheck source=/dev/null
[ -f "$ROOT/amh.conf" ] && . "$ROOT/amh.conf"

FAILS=0
WARNS=0
section() { printf '\n▸ %s\n' "$1"; }
ok() { printf '   ok    %s\n' "$1"; }
warn() {
	printf '   WARN  %s\n' "$1"
	WARNS=$((WARNS + 1))
}
fail() {
	printf '   FAIL  %s\n' "$1"
	FAILS=$((FAILS + 1))
}
skip() { printf '   skip  %s\n' "$1"; }
# NOT a verdict, and the distinction is load-bearing: `note` touches no counter, changes no
# exit code, and nothing branches on it. The four above answer "did this check pass"; this one
# reports something observed that no check is being made about. A run-receipt vocabulary was
# refused once (AMH ledger row DA025) and this is not one — it adds no second answer to the
# ladder's question, and if anything ever consumes a `note` line, it has become the thing that
# was refused.
# (`scripts/guards/adapter-set.sh` also defines a `note`, and there it ACCUMULATES failures.
# Different file, different scope, no shared code — but the word is not free in this tree, so
# do not read one from the other.)
note() { printf '   note  %s\n' "$1"; }

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

in_ci() { [ -n "${CI:-}" ]; }
has_git() { git rev-parse --git-dir >/dev/null 2>&1; }
upstream_ref() {
	local r="origin/$DEFAULT_BRANCH"
	git rev-parse --verify --quiet "$r" >/dev/null 2>&1 && printf '%s' "$r"
}

# =============================================================================
# 1. GUARDS — seconds, no build. Each one checks an artifact the work produces
#    anyway (P3): file sizes, diffs, commit messages, citations. Never a
#    self-reported attestation, which an agent can emit without doing the work.
# =============================================================================

# The second unit every AIM-POINT in this harness is bounded in, beside bytes.
#
# Bytes are continuous, and a continuous target can always be approached by shaving: drop an
# adjective, tighten a clause, re-measure, repeat until the guard goes quiet. Two reported
# instances, one shape — a ledger row drafted at 874 bytes against an 800-byte cap and
# trimmed until it fit, and a state file shaved across a dozen edits to land 7 bytes under
# its ceiling. Removing the threshold from green output (AMH ledger row DB040) did not reach
# either one: the session measures its own draft, so the anchor is the cap itself and not
# the report of it.
#
# Shaving words cannot move a sentence count. Removing a sentence can, which is a decision
# about content and exactly the move the compression rules ask for. That is the whole of
# what this unit buys, and it is deliberately NOT the claim that the count cannot be gamed:
# rewriting `. T` to `; t` across a file halves it while removing nothing at all, verified
# on this repository's own state file (85 → 41, zero bytes). So a sentence ceiling cannot
# stand alone either, and the aim-points are bounded in BOTH units — the byte ceiling stops
# the punctuation rewrite, which frees no space, and the sentence ceiling stops the shave,
# which removes no content. Neither number is sufficient; together neither cheap move
# passes. Bytes still stand alone where nobody aims: the soft and rejection boundarys, which decide
# WHEN to compress rather than how far, and the edit delta, which classifies a shrink
# already made.
#
# The counter sees what it can see and deliberately UNDERCOUNTS. A terminator ends a
# sentence only when what follows starts another one — a capital, a backtick, bold, a quote,
# an opening paren — after any closing markup, and `e.g.`/`i.e.` are folded away first. A
# missed sentence makes the guard lenient; a phantom one would red-line honest prose, and a
# rule that rejects correct work gets deleted rather than obeyed. Markdown headings, table
# rows and bare list fragments carry no terminator and so count as nothing, which is the
# same leniency and not an oversight. CRLF line endings and sentences opening with non-ASCII
# both fall the same way, toward a lower count.
#
# It returns non-zero rather than an empty string when awk produces no number, and every
# caller turns that into a loud verdict. An empty count would flow into `[ "$n" -gt … ]`,
# which errors to stderr and takes the ELSE branch — a green line reporting a measurement
# nobody made, the AMH ledger row DC002 shape one layer out.
count_sentences() { # <file> — the sentences awk can see, one number on stdout
	local n
	n=$(LC_ALL=C awk '
		{ buf = buf $0 " " }
		END {
			gsub(/[Ee]\.[Gg]\./, "eg", buf)
			gsub(/[Ii]\.[Ee]\./, "ie", buf)
			# Common titles and initialisms are not sentence ends. Prefer an
			# undercount here: a false boundary rejects honest prose, while a missed
			# boundary merely leaves the byte half of the limit to do its job.
			gsub(/(^|[ \t])(Mr|Mrs|Ms|Dr|Prof|Sr|Jr|St|No)\./, " title", buf)
			while (gsub(/[A-Z]\.[A-Z]\./, "initials", buf)) { }
			# A sentinel so a terminator at end of file counts like any other.
			buf = buf "X"
			print gsub(/[.!?][*"`_)]*[ \t][ \t]*[A-Z*"`(]/, "", buf)
		}
	' "$1") || return 1
	case $n in '' | *[!0-9]*) return 1 ;; esac
	printf '%s' "$n"
}

guard_state_size() {
	section "Working memory: $STATE_FILE size band (hysteresis)"
	if [ ! -f "$STATE_FILE" ]; then
		fail "$STATE_FILE is missing — it is protocol step 1 for every session"
		return
	fi
	local cur warn_b hard_b comp_b ceiling prev prev_file prev_s cur_s shrank delta
	cur=$(wc -c <"$STATE_FILE")
	warn_b=$((STATE_WARN_KB * 1024))
	hard_b=$((STATE_HARD_KB * 1024))
	comp_b=$((STATE_COMPRESS_TO_KB * 1024))

	# Same loud fallback as the edit delta below, for the same reason: a malformed ceiling
	# arriving from config must not decide anything quietly.
	ceiling=$STATE_COMPRESS_TO_SENTENCES
	case $ceiling in
	'' | 0 | *[!0-9]*)
		warn "STATE_COMPRESS_TO_SENTENCES='$ceiling' is not a positive sentence count — using 50. Fix it in amh.conf; a guard that reads a malformed threshold and carries on quietly is how a band gets widened by accident."
		ceiling=50
		;;
	esac

	if [ "$cur" -gt "$hard_b" ]; then
		fail "$((cur / 1024)) KB crosses the ${STATE_HARD_KB} KB rejection boundary; post-action ceilings require ≤ ${STATE_COMPRESS_TO_KB} KB AND ≤ ${ceiling} sentences"
	elif [ "$cur" -gt "$warn_b" ]; then
		warn "$((cur / 1024)) KB crosses the ${STATE_WARN_KB} KB compression trigger; post-action ceilings require ≤ ${STATE_COMPRESS_TO_KB} KB AND ≤ ${ceiling} sentences"
	else
		# The green line prints the MEASUREMENT and no threshold, deliberately. A clean
		# run that says "8 KB (compression trigger 14 KB)" re-anchors the cap in the context of the
		# agent who will next compress this file, and the number in front of you is the
		# number you optimize toward: a reported instance shaved clauses across a dozen
		# edits to land 7 bytes under the post-action ceilings, having never considered 7 KB. Every
		# verdict that TURNS ON a threshold still names it — the warn, both fails, and
		# the landing line below — because a rejection must say what it rejected
		# against. A pass rejects nothing, so it owes no number.
		ok "$((cur / 1024)) KB, within the band"
	fi

	# Landing check. Size thresholds alone are Goodhart-able: a trim that stops short of
	# the post-action ceilings passes and re-arms the warning a session later. Compare against the
	# committed size and require a compression, once started, to actually LAND on the
	# ceiling.
	#
	# "Any shrink above the cap IS a compression pass" was the first form of that, and it
	# is wrong in the other direction: it failed a 15-byte deletion twice, once for fixing
	# a wrong path and once for closing a queue item, and the only compliant move each
	# time was to pad the file back. So judge the shrink's SIZE and whether it CROSSES
	# the cap, in three branches:
	#
	#   1. crosses from above the cap to at or below it — must land on the post-action ceilings. This is
	#      the original hole verbatim and stays closed.
	#   2. stays above the cap and is smaller than the edit delta — an ordinary edit.
	#      Allowed; the size warning above is still armed, so the compression is still owed.
	#   3. stays above the cap and reaches the delta — a compression pass that stopped
	#      short, which is the grow-to-15.5 / trim-to-14.2 loop the debounce exists to
	#      prevent. Must reach the post-action ceilings.
	#
	# The delta sits in a wide empty gap: no plausible ordinary edit runs to 1 KB, and no
	# real compression pass on a file this size comes in under about 5 KB. It is the SHRINK
	# that is being measured, never the band — widening the band is what reopens the
	# original hole, and nothing here touches it. Growth, and edits that start below the
	# cap, never reach this check at all.
	has_git || return
	# Materialised rather than piped, because the landing verdict needs the committed
	# file's SENTENCE count as well as its size and one `git show` should answer both.
	prev_file=$TMP/state-head
	git show "HEAD:$STATE_FILE" >"$prev_file" 2>/dev/null || : >"$prev_file"
	prev=$(wc -c <"$prev_file")
	if [ "$prev" = "$cur" ]; then
		git show "HEAD~1:$STATE_FILE" >"$prev_file" 2>/dev/null || : >"$prev_file"
		prev=$(wc -c <"$prev_file")
	fi
	[ "${prev:-0}" -gt 0 ] || return
	[ "$prev" -gt "$warn_b" ] && [ "$cur" -lt "$prev" ] || return
	shrank=$((prev - cur))

	# A threshold arriving from config is exactly the kind of property that is not this
	# guard's subject, so a malformed one must not decide anything quietly. Left alone,
	# `[ "$shrank" -lt "$delta" ]` on a non-numeric delta writes an error to stderr and
	# takes the ELSE branch — the guard then fails an ordinary edit while printing two
	# numbers that contradict its own verdict. Fall back to the shipped default, loudly.
	delta=$STATE_EDIT_DELTA_BYTES
	case $delta in
	'' | 0 | *[!0-9]*)
		warn "STATE_EDIT_DELTA_BYTES='$delta' is not a positive byte count — using 1024. Fix it in amh.conf; a guard that reads a malformed threshold and carries on quietly is how a band gets widened by accident."
		delta=1024
		;;
	esac

	# Byte counts, not KB, wherever a verdict below reports a SIZE. Integer KB rounds toward
	# zero on both sides of a comparison, so the honest outcome prints as a contradiction:
	# a 14848-byte stop reads `stops short at 14 KB, still above the 14 KB compression trigger`. Bytes
	# are what the guard actually compared. The landing verdict reports neither, because
	# what it compares is a sentence count.
	if [ "$cur" -le "$warn_b" ]; then
		# Branch 1 deliberately does NOT consult the shrink: once the file is back under
		# the cap, how it got there does not matter — the post-action ceilings are the post-action ceilings. So the
		# wording describes the CROSSING, and claims no classification the guard did not
		# make. A one-byte deletion from 14337 lands here, and that is the owner's rule.
		#
		# The pair of post-action ceilings comprises TWO conditions and a landing satisfies both, which is what puts
		# "how it got there" partly back inside the guard's reach. A pass that shaved words
		# to cross the cap arrives carrying every sentence it started with and fails on the
		# sentence ceiling; a pass that rewrote `. T` to `; t` to halve the sentence count
		# frees no space and fails on the byte ceiling. Either number alone is satisfiable by
		# a move that removes nothing, and that is why neither stands here alone.
		cur_s=$(count_sentences "$STATE_FILE") || {
			fail "could not count the sentences in $STATE_FILE — this landing check judged NOTHING, and a green line here would say it had"
			return
		}
		if [ "$cur" -gt "$comp_b" ] || [ "$cur_s" -gt "$ceiling" ]; then
			fail "compression result is $cur bytes and $cur_s sentences — post-action ceilings require ≤ $comp_b bytes (${STATE_COMPRESS_TO_KB} KB) AND ≤ $ceiling sentences"
		else
			# Reports the MARGIN, not the post-action ceilings it was measured against. The landing
			# line is the one an agent reads immediately after compressing, so the
			# quantity it makes salient is the quantity the next pass aims at: "at or
			# under the 50-sentence ceiling" teaches that the post-action ceilings are the target, and
			# landing ON it reads as a job well done. Headroom below the post-action ceilings are the
			# same fact without that pull. It is NOT a score to maximise either: a file
			# gutted to stubs prints a big number and passes, and no guard can see the
			# difference — the rule that governs it is the state file's own (fold whole
			# completed stages; do not shave), and this line is a measurement, not a
			# grade. The configured ceilings remain one addition away for anyone who wants it, and
			# the fail branch beside this one names it outright.
			prev_s=$(count_sentences "$prev_file") || prev_s='?'
			ok "crossed below the compression trigger and landed at $cur bytes and $cur_s sentences, $((comp_b - cur)) bytes and $((ceiling - cur_s)) sentences below the post-action ceilings (from $prev bytes and $prev_s sentences)"
		fi
	elif [ "$shrank" -lt "$delta" ]; then
		ok "edit above the compression trigger (shrank $shrank bytes, below the $delta-byte edit-delta trigger); compression still owed"
	else
		fail "unfinished compression pass: shrank $shrank bytes, crossing the $delta-byte edit-delta trigger, and stopped at $cur bytes — still above the compression trigger ($warn_b bytes); post-action ceilings require ≤ $comp_b bytes AND ≤ $ceiling sentences"
	fi
}

# A section is present only if its header stands at the start of a line AND something
# non-blank follows before the next header. Header-presence alone is trivially gamed:
# the cheapest way to "survive compression" is to keep four headings and delete every
# body under them. This does not judge whether the content is any GOOD — no guard can —
# it only refuses to call an empty shell a section.
section_has_body() { # <file> <header>
	awk -v h="$2" '
		index($0, h) == 1 && !inside { inside = 1; next }
		inside && /^#{1,6} / { exit }
		inside && NF { found = 1; exit }
		END { exit(found ? 0 : 1) }
	' "$1"
}

guard_state_structure() {
	section "Working memory: required sections"
	[ -f "$STATE_FILE" ] || return
	# EVERY level-2 heading, not just the required ones. A scripted edit to this file
	# anchored on a string its own preamble also contains and spliced the whole document in
	# after itself; sections appeared twice, one copy carrying state a later session had
	# already superseded, and it passed the ladder, CI and a push. Nothing else here can see
	# a duplicate: the caps measure bytes and the landing check measures shrink, both of
	# which a duplicate satisfies, and existence was satisfied twice over.
	#
	# Scoped to the CONFIGURED sections it would have closed that incident and nothing
	# around it — the heading the botched edit actually keyed on was the owner queue, which
	# is checked separately below, and the non-items heading is in no list at all. A splice
	# duplicates whatever it duplicates, so the question has to be asked of the document
	# rather than of a list: any repeated `## ` heading is the signature.
	local problems=0 dupes
	dupes=$(grep '^##[[:space:]]' "$STATE_FILE" | sed 's/[[:space:]]*$//' | sort | uniq -d)
	if [ -n "$dupes" ]; then
		while IFS= read -r sec; do
			[ -n "$sec" ] || continue
			fail "heading '$sec' appears more than once — this file is the session's working memory, and two copies of a section are two answers to the same question. Usually a scripted edit that spliced the document into itself: delete the stale copy rather than merging them."
			problems=$((problems + 1))
		done <<<"$dupes"
	fi
	local sec
	while IFS= read -r sec; do
		[ -n "$sec" ] || continue
		if ! grep -q "^${sec}[[:space:]]*$" "$STATE_FILE"; then
			fail "section '$sec' is missing — over-compression deleted it"
			problems=$((problems + 1))
		elif ! section_has_body "$STATE_FILE" "$sec"; then
			fail "section '$sec' is empty — the header survived compression but its content did not"
			problems=$((problems + 1))
		fi
	done < <(printf '%s\n' "$STATE_REQUIRED_SECTIONS" | tr '|' '\n')
	[ "$problems" = 0 ] && ok "all required sections present and non-empty"
	# WARN, not fail: the owner's channel is theirs, and a session that has genuinely
	# closed every item should not be blocked. The asymmetry is deliberate and is
	# stated wherever this section is described as protected.
	if ! grep -q "^${STATE_OWNER_QUEUE_SECTION}[[:space:]]*$" "$STATE_FILE"; then
		warn "'$STATE_OWNER_QUEUE_SECTION' has vanished — that section is the owner's channel; its items are theirs to close"
	fi
}

# THE VOLUME CHAIN. A volume is not a file whose name looks right — it is a file the
# scheme can REACH: start at the base volume and apply the carry rule below until the
# next name is missing. Membership is reachability, and the walk stops at the first gap.
#
# Two rejected rules, both of which failed on a real tree and both of which failed
# QUIETLY, which is why this one is computed rather than matched:
#
#   * last glob match — the shell's collation, not volume age. Under C, LEDGER_AA.md
#     sorts BETWEEN LEDGER_A.md and LEDGER_B.md; under a locale ignoring punctuation at
#     the primary level it sorts before LEDGER_A.md. Either way the live volume sticks
#     at Z forever.
#   * greatest `[A-Z]+` suffix in shortlex order (length, then alphabet) — right about
#     the numbering and wrong about membership. LEDGER_ARCHIVE.md is all capitals and
#     LONG, so it outranks every real volume and pins the cap rung on a file nobody
#     writes to, reporting `ok` forever. A one-line untracked file switched the rung off.
#
# A chain cannot be joined by naming a file well, and the same walk answers both
# questions this script asks — which file is live, and which files hold rows.
volume_path() { # <suffix, empty for the base volume>
	if [ -z "$1" ]; then
		printf '%s/%s.md' "$LEDGER_DIR" "$LEDGER_BASENAME"
	else
		printf '%s/%s_%s.md' "$LEDGER_DIR" "$LEDGER_BASENAME" "$1"
	fi
}

# One copy of the name-parsing rule. The prefix is CHECKED rather than assumed: `${name#pre}`
# is a no-op when the prefix is absent, so without the case below this answers "volume,
# suffix OTHER" for docs/OTHER.md. Unreachable from the two callers here, which feed it
# names this file constructed — and a helper that is only correct because of where it is
# called from is a trap for the next caller.
#
# The bracket range is spelled out instead of `A-Z`: a glob range is collation-dependent,
# and a locale with dictionary ordering can admit lowercase letters into `A-Z`.
volume_suffix() { # <ledger path>; prints its suffix ('' for the base volume), 1 if not a volume
	local name suffix
	name=${1##*/}
	name=${name%.md}
	[ "$name" = "$LEDGER_BASENAME" ] && return 0
	case $name in "${LEDGER_BASENAME}_"*) ;; *) return 1 ;; esac
	suffix=${name#"${LEDGER_BASENAME}_"}
	case $suffix in
	'' | *[!ABCDEFGHIJKLMNOPQRSTUVWXYZ]*) return 1 ;;
	esac
	printf '%s' "$suffix"
}

# Every volume the chain reaches, base first, one path per line. Empty output means no
# ledger at all — NOT "no volumes past the base", which is one line.
chain_volumes() {
	local suffix='' path
	while :; do
		path=$(volume_path "$suffix")
		[ -f "$path" ] || return 0
		printf '%s\n' "$path"
		suffix=$(next_volume_suffix "$suffix") || return 0
	done
}

live_ledger() { chain_volumes | tail -1; }

extract_ledger_rows() { # <tree-dir> <rows-dir>
	local tree=$1 rows=$2 path
	mkdir -p "$rows"
	while IFS= read -r path; do
		awk -v out="$rows" '
			function flush(    file, n) {
				if (id == "") return
				n = count
				while (n > 1 && lines[n] == "") n--
				file = out "/" id
				for (i = 1; i <= n; i++) print lines[i] >file
				close(file)
				delete lines
				count = 0
			}
			/^- D[A-Z]*-[0-9]+( \[cited\])?: / {
				flush()
				id = $2
				sub(/ \[cited\]:$/, "", id)
				sub(/:$/, "", id)
				lines[++count] = $0
				next
			}
			id != "" { lines[++count] = $0 }
			END { flush() }
		' "$tree/$path"
	done
}

guard_new_ledger_row_lengths() {
	local cap=${LEDGER_ROW_CHAR_CAP:-0} sent_cap=${LEDGER_ROW_SENTENCE_CAP:-0}
	local changed path suffix='' next checked=0 row id count sentences lengths=''
	case $cap in
		'' | *[!0-9]*)
			fail "LEDGER_ROW_CHAR_CAP must be a non-negative integer, got '${LEDGER_ROW_CHAR_CAP:-}'"
			return
			;;
	esac
	case $sent_cap in
		'' | *[!0-9]*)
			fail "LEDGER_ROW_SENTENCE_CAP must be a non-negative integer, got '${LEDGER_ROW_SENTENCE_CAP:-}'"
			return
			;;
	esac
	# Either limit alone is a working configuration; both at zero switches the rung off.
	[ "$cap" -gt 0 ] || [ "$sent_cap" -gt 0 ] || return
	git rev-parse --verify -q HEAD >/dev/null 2>&1 || return
	changed=$(git diff --name-only HEAD -- "$LEDGER_DIR" | awk -v dir="$LEDGER_DIR" -v base="$LEDGER_BASENAME" '
		$0 == dir "/" base ".md" { found = 1 }
		$0 ~ "^" dir "/" base "_[A-Z]+[.]md$" { found = 1 }
		END { exit found ? 0 : 1 }
	') || return
	: "$changed"
	mkdir -p "$TMP/head-ledger/$LEDGER_DIR" "$TMP/work-ledger/$LEDGER_DIR" "$TMP/head-rows" "$TMP/work-rows"
	: >"$TMP/head-chain"
	while :; do
		path=$(volume_path "$suffix")
		if ! git cat-file -e "HEAD:$path" 2>/dev/null; then
			[ -n "$suffix" ] && break
			return
		fi
		git show "HEAD:$path" >"$TMP/head-ledger/$path" || return
		printf '%s\n' "$path" >>"$TMP/head-chain"
		next=$(next_volume_suffix "$suffix") || break
		suffix=$next
	done
	while IFS= read -r path; do
		mkdir -p "$TMP/work-ledger/$(dirname "$path")"
		cp "$path" "$TMP/work-ledger/$path" || return
	done <"$TMP/chain"
	extract_ledger_rows "$TMP/head-ledger" "$TMP/head-rows" <"$TMP/head-chain"
	extract_ledger_rows "$TMP/work-ledger" "$TMP/work-rows" <"$TMP/chain"
	for row in "$TMP"/work-rows/D*-*; do
		[ -e "$row" ] || continue
		id=${row##*/}
		[ -f "$TMP/head-rows/$id" ] && continue
		checked=$((checked + 1))
		# A rejection boundary, never a desired size. Counting sentences discourages
		# word-by-word shaving; a row near it probably contains narrative or multiple lessons.
		sentences=$(count_sentences "$row") || {
			fail "$id: could not count the sentences in this new row — the rung judged NOTHING, and a green line here would say it had"
			return
		}
		if [ "$sent_cap" -gt 0 ] && [ "$sentences" -gt "$sent_cap" ]; then
			fail "$id: new ledger row runs to $sentences sentences, crossing rejection boundary LEDGER_ROW_SENTENCE_CAP=$sent_cap; historical committed rows and sanctioned metadata-only additions are exempt"
			return
		fi
		# The second rejection boundary, in bytes, catches pathologically dense sentences
		# that the sentence count cannot see. Locale-stable character
		# policy — count bytes with LC_ALL=C. For ordinary ASCII ledger prose that is one
		# byte per character; UTF-8 non-ASCII text is charged by encoded bytes so the
		# verdict is identical across host locales.
		count=$(LC_ALL=C wc -c <"$row") || return
		count=${count//[[:space:]]/}
		if [ "$cap" -gt 0 ] && [ "$count" -gt "$cap" ]; then
			fail "$id: new ledger row is $count byte-counted character(s), crossing rejection boundary LEDGER_ROW_CHAR_CAP=$cap; historical committed rows and sanctioned metadata-only additions are exempt"
			return
		fi
		lengths="$lengths $id=$sentences"
	done
	# The counts themselves, and not the cap they cleared. "checked 2 rows against
	# LEDGER_ROW_SENTENCE_CAP=6" re-anchors 6 on every clean run, and the byte lengths
	# this line used to print were the anchor in their own right — the reported
	# consequence was rows drafted long and trimmed to just fit, 828 and 805 in one
	# session, where a three-sentence row stating the lesson tersely was the better
	# artifact and never occurred to the author. A sentence count is the measurement an
	# author cannot act on by shaving; the fail branches above still name the cap they
	# turn on, because a rejection must say what it rejected against.
	[ "$checked" -gt 0 ] && ok "checked $checked new ledger row(s) —${lengths} sentence(s)"
}


# The suffix of the volume AFTER the given one, as an odometer over A–Z with carry:
# '' → A, A → B, Z → AA, AZ → BA, ZZ → AAA. Computed rather than looked up, because a
# table is the thing that has a last entry — the single-letter scheme this replaces was a
# table with Z at the end of it, and nothing said what came next.
next_volume_suffix() { # <current suffix, empty for the base volume>
	local s=$1 alphabet=ABCDEFGHIJKLMNOPQRSTUVWXYZ i c head out='' carry=1
	if [ -z "$s" ]; then
		printf 'A'
		return
	fi
	i=$((${#s} - 1))
	while [ "$i" -ge 0 ]; do
		c=${s:i:1}
		if [ "$carry" -eq 1 ]; then
			if [ "$c" = Z ]; then
				c=A # carry stays set: Z rolls to A and the digit to its left advances
			else
				head=${alphabet%%"$c"*}
				# A character this odometer does not know leaves `head` as the WHOLE
				# alphabet, and the substring one past its end is empty — so the digit
				# would silently vanish and the answer come back one character short.
				# Refuse instead. The callers here only ever pass a validated suffix,
				# so this is declared unreachable rather than fixtured; it exists
				# because a shorter name is the one wrong answer nobody would notice.
				[ "${#head}" -lt 26 ] || return 1
				c=${alphabet:${#head}+1:1}
				carry=0
			fi
		fi
		out=$c$out
		i=$((i - 1))
	done
	[ "$carry" -eq 1 ] && out=A$out
	printf '%s' "$out"
}

guard_ledger_rollover() {
	section "Permanent memory: ledger file cap"
	local live lines last_row size suffix next f orphans=''
	live=$(live_ledger)
	if [ -z "$live" ]; then
		# The chain starts at the base volume, so a tree holding continuation volumes
		# and no base has nothing this rung can measure — and `skip` would render that
		# identically to a repository that has not started a ledger yet. Say which.
		for f in "$LEDGER_DIR/${LEDGER_BASENAME}"_*.md; do
			if [ -f "$f" ]; then
				fail "$(volume_path '') is missing while continuation volume(s) exist — the chain is walked from the base volume, so no cap can be checked until it is back (volumes are never deleted)"
				return
			fi
		done
		skip "no ledger yet"
		return
	fi
	# Volume-SHAPED files the chain does not reach: a volume that was deleted from the
	# middle, or a file that merely looks like one. This rung cannot tell those apart and
	# does not guess — but it will not stay quiet either, because rows in an unreachable
	# file are invisible to every check here. The cap below is still the live volume's.
	chain_volumes >"$TMP/chain"
	guard_new_ledger_row_lengths
	for f in "$LEDGER_DIR/${LEDGER_BASENAME}"_*.md; do
		[ -f "$f" ] || continue
		grep -qxF "$f" "$TMP/chain" && continue
		orphans="$orphans $f"
	done
	[ -n "$orphans" ] &&
		warn "volume-shaped file(s) the chain does not reach:$orphans — either a volume is missing from the chain or these are not volumes. Nothing reads their rows."
	lines=$(wc -l <"$live")
	# Bytes are REPORTED, never gated. The cap counts lines because a line is what a
	# row is appended in, but the quantity it stands in for is read cost — and the two
	# drift, because rows are prose and prose wraps at whatever width its author used.
	# A second failing threshold on bytes was refused (AMH ledger row DA022): no
	# context-overflow incident is on record, and this harness does not ship guards for
	# harms it has not seen. Printing the number costs nothing and lets the owner see
	# the proxy diverging before deciding whether it needs a gate at all.
	# One decimal, not integer KB: a volume under 1024 bytes would otherwise report
	# `0 KB`, which is both false and exactly the kind of quiet rounding this figure
	# exists to expose. Tenths in integer arithmetic — no bc, no float.
	size=$(wc -c <"$live")
	size=$((size * 10 / 1024))
	size="$((size / 10)).$((size % 10))"
	# The row pattern admits ANY number of volume letters (a `DAA-` row, a `DAAA-` row),
	# not the one it used to. A scheme that stops at Z is a scheme with a silent failure at
	# the end of it: rows in a volume the pattern cannot match are invisible here, so the
	# cap can never fire on them, and invisible to the citation scan below in both
	# directions at once — every rung green, on a file nobody is writing to.
	# The examples above stop at the hyphen on purpose: a complete id in a shipped comment
	# IS a citation as far as the guard below is concerned, and these rows do not exist.
	last_row=$(grep -n '^- D[A-Z]*-[0-9]\+' "$live" | tail -1 | cut -d: -f1)
	# Every branch carries the size, the FAIL branch above all: that is the volume at
	# its largest, and rollover is the one moment the owner is deciding whether a line
	# cap still stands in for read cost at all.
	if [ -n "$last_row" ] && [ "$last_row" -gt "$LEDGER_LINE_CAP" ]; then
		# The next volume's name is COMPUTED, so the diagnostic stays right past Z — and
		# it names the row prefix too, because the file name and the prefix are the same
		# suffix and a rollover that gets one of them wrong is not caught by anything.
		suffix=$(volume_suffix "$live")
		next=$(next_volume_suffix "$suffix")
		fail "$live: a row starts at line $last_row (${size} KB measured), crossing the ${LEDGER_LINE_CAP}-line rollover boundary — open $LEDGER_DIR/${LEDGER_BASENAME}_$next.md, numbering from D$next-001 (rows are never moved or renumbered)"
	else
		# Lines and size, not lines-over-cap: this is the third green line to lose its
		# threshold, and the one that most nearly escaped, because `800/2000 lines` reads
		# as context rather than as an anchor. It is the same anchor.
		ok "$live: $lines lines, ${size} KB (grep it; a volume is retrieval storage, not a read)"
	fi
}

guard_citations() {
	section "Citations: code ↔ ledger, both directions"
	local scan_files=$TMP/scanfiles rows=$TMP/rows cited=$TMP/cited marked=$TMP/marked
	# NUL-separated throughout, for the reason the secret scan states below: a
	# word-split file list drops every name containing a space, and the drop is
	# invisible — the guard reports the same green it reports for a clean tree.
	: >"$scan_files"
	# `set -f` for the two config lists below: they are split on whitespace ON PURPOSE,
	# but an unquoted expansion also GLOBS, so an entry containing `?` or `*` would be
	# rewritten into whatever happens to sit in the working directory — a third way for
	# the scanned scope to differ from what amh.conf says it is.
	set -f
	local p
	for p in $CITATION_SCAN_PATHS; do
		[ -e "$p" ] || continue
		if has_git; then
			git ls-files -co --exclude-standard -z -- "$p" >>"$scan_files"
		else
			find "$p" -type f -print0 >>"$scan_files"
		fi
	done
	local ex f
	for ex in $CITATION_EXCLUDE; do
		# Whole paths and directory prefixes, matched literally. The grep form this
		# replaces interpolated $ex as a REGEX and kept the unfiltered list whenever
		# the exclusion emptied it — two more ways for the same scope to drift.
		: >"$scan_files.t"
		while IFS= read -r -d '' f; do
			case $f in "$ex" | "$ex"/*) continue ;; esac
			printf '%s\0' "$f" >>"$scan_files.t"
		done <"$scan_files"
		mv "$scan_files.t" "$scan_files"
	done
	set +f

	# Every ledger row, and whether it carries the machine-synced [cited] marker.
	: >"$rows"
	: >"$marked"
	# The CHAIN, not the glob. Globbing would harvest rows from any LEDGER_*.md sitting in
	# the directory, so a scratch file could supply a duplicate row id — and the rung above
	# would refuse to call that same file a volume. Two guards disagreeing about what a
	# volume is was the shape being fixed; the walk is shared so they cannot.
	local f
	while IFS= read -r f; do
		sed -E -n 's/^- (D[A-Z]*-[0-9]+)( \[cited\])?:.*/\1\2/p' "$f" >>"$rows.raw"
	done < <(chain_volumes)
	if [ -f "$rows.raw" ]; then
		awk '{print $1}' "$rows.raw" | sort >"$rows"
		awk 'NF>1{print $1}' "$rows.raw" | sort >"$marked"
	else
		: >"$rows"
	fi

	local dupes
	dupes=$(uniq -d <"$rows")
	if [ -n "$dupes" ]; then
		fail "duplicate ledger row numbers: $(printf '%s' "$dupes" | tr '\n' ' ')"
	fi

	: >"$cited"
	if [ -s "$scan_files" ]; then
		# Same unbounded volume pattern the row scan uses: an id the scan cannot see is
		# not an unresolved citation, it is no citation at all, and every `DAA-` row used
		# to be exactly that in both directions.
		#
		# `-w` is what keeps the widening honest, and it is not decoration. Unanchored,
		# `D[A-Z]*-[0-9]+` matches INSIDE a word: `README-<n>` and `PRODUCTION-<n>` each
		# yield an id built from the tail of the word — an id that appears nowhere in the
		# tree, reported as an unresolved citation the reader cannot grep for. (Spelling
		# those two out here would file this comment as a citation to them. Widening a
		# pattern changes what the text around it MEANS, this file included.) Whole-word
		# matching also closes the same trap one letter down, where `XL-003` used to
		# yield a citation to L-003 (AMH ledger row DB004(g)); that one shipped.
		#
		# `-I` is not an optimization. A binary file whose bytes happen to match makes
		# grep print `Binary file <path> matches` INSTEAD of the match — and which stream
		# it prints that on is version-dependent: stderr on grep >= 3.5, which the
		# redirection below already discards, but STDOUT on <= 3.4, and Git for Windows
		# ships 3.0. There the notice lands in `$cited` as a citation token no ledger row
		# can ever resolve, and the rung fails naming a font file. Same class as the sed
		# assumption in AMH ledger row DC030: a shipped guard depending on a third-party
		# tool's behaviour that only holds on the platform the fixtures run on. `-I`
		# settles it in every version — a binary file is not a citation site — and it is
		# what the secret scan already uses to decide the same question.
		#
		# `LC_ALL=C` is half of that flag, not decoration beside it. Grep before 3.5 — the
		# versions `-I` is here for — also calls a file binary on an ENCODING ERROR in the
		# current locale, so under a UTF-8 locale a Latin-1 file in the scan paths would be
		# skipped: its citations would vanish, an unmarked row would then pass, and a
		# `[cited]` marker whose only site was that file would fail as stale. That is scope
		# drift of exactly the kind the `set -f` above exists to stop. Under `C` the
		# question is the NUL byte on every host and locale, which is how the secret scan
		# and `shipped-citations.sh` both ask it.
		LC_ALL=C xargs -0 grep -I -hwoE 'D[A-Z]*-[0-9]+' <"$scan_files" 2>/dev/null | sort -u >"$cited"
	fi

	local unresolved missing_marker stale_marker
	unresolved=$(comm -23 "$cited" <(sort -u "$rows"))
	if [ -n "$unresolved" ]; then
		fail "cited from configured implementation paths but no such ledger row: $(printf '%s' "$unresolved" | tr '\n' ' ')"
	fi
	missing_marker=$(comm -23 "$cited" <(sort -u "$marked"))
	if [ -n "$missing_marker" ]; then
		fail "cited from configured implementation paths but not marked [cited] in the ledger: $(printf '%s' "$missing_marker" | tr '\n' ' ') — the marker warns the next reader that an implementation artifact depends on the row"
	fi
	stale_marker=$(comm -13 "$cited" <(sort -u "$marked"))
	if [ -n "$stale_marker" ]; then
		fail "marked [cited] but no longer cited from configured implementation paths: $(printf '%s' "$stale_marker" | tr '\n' ' ') — drop the marker (never the row)"
	fi
	[ -z "$unresolved$missing_marker$stale_marker$dupes" ] && ok "$(wc -l <"$cited" | tr -d ' ') citation(s) resolve; markers in sync"
}

guard_secret_shapes() {
	section "Secret-shape scan (the redaction filter IS the scan)"
	# This guard is the repo's ENTIRE secret scan (AMH ledger row D004), so it must not be
	# possible to switch it off by accident. It used to test `-x` and print `skip` when the bit
	# was missing: `chmod -x scripts/redact.sh` — or an archive download, or
	# core.fileMode=false — turned the scan into a green line with a live credential
	# sitting in the tree. Presence is now the question, and the answer to "absent" is
	# a failure, not a skip. The exec bit no longer decides anything: the filter is run
	# through `bash` explicitly.
	if [ ! -f scripts/redact.sh ]; then
		fail "scripts/redact.sh is missing — it IS this repo's secret scan, so its absence is a failure, not a skip"
		return
	fi
	# ...and PRESENCE is not the same as WORKING. The verdict below is "the filter's
	# output differs from the file", which an empty, truncating, crashing or
	# pass-through filter satisfies for nothing at all — every file reads as clean and
	# the rung prints ok. A positive control first, so the scan has to prove it can
	# still catch something before its silence is allowed to mean anything. The token is
	# generated at runtime: a stored literal would make this file fail its own scan
	# (AMH ledger row D004).
	local canary
	# Bounded read, then slice: `tr </dev/urandom | head -c N` leaves tr writing into a
	# closed pipe, so every run printed `tr: write error: Broken pipe` into the ladder's
	# output. Harmless, but noise in a guard's output trains readers to skim it.
	canary=''
	while [ "${#canary}" -lt 16 ]; do
		canary=$canary$(head -c 512 /dev/urandom | LC_ALL=C tr -dc 'A-Z0-9')
	done
	canary="AKIA${canary:0:16}"
	if printf 'x %s x\n' "$canary" | bash scripts/redact.sh 2>/dev/null | grep -qF "$canary"; then
		fail "scripts/redact.sh did not redact a generated test token — the filter is empty, broken or pass-through, and this scan would report green on everything"
		return
	fi
	# ...and a second control, for the second thing this scan now depends on. A difference
	# between the filter's output and the file is only a credential if everything else in
	# the filter is byte-transparent, and `sed` — which is what the filter is built out of —
	# is not, on every platform: the MSYS2 build shipped with Git for Windows rewrites CRLF
	# to LF for a script that matches nothing at all (AMH ledger row DC030). `--baseline`
	# runs the same stages with no substitutions, so it carries that newline handling and no
	# redaction, and the comparison below can subtract one from the other. If it is missing
	# — an older redact.sh beside a newer ladder — there is nothing to subtract, so this
	# control establishes that the mode EXISTS and emits its input. It is deliberately not
	# the whole check: one canary proves one class, and a baseline that redacted some other
	# class would sail through it. What proves the baseline reproduced a particular file is
	# the per-file comparison below, which runs on that file's own bytes. Refusing to scan
	# is the honest verdict for both: this rung is the repo's whole secret defence, and
	# "I did not manage to look" must never render as clean (AMH ledger row DC002).
	if ! printf 'x %s x\n' "$canary" | bash scripts/redact.sh --baseline 2>/dev/null | grep -qF "$canary"; then
		fail "scripts/redact.sh has no working --baseline mode, so this scan cannot tell a redaction from the platform's own newline handling and did NOT scan this tree. The copy of redact.sh beside this ladder is older than it or broken — re-run the harness's init script to restore the matching pair."
		return
	fi
	local list=$TMP/files.nul hits=0
	if has_git; then
		git ls-files -co --exclude-standard -z >"$list"
	else
		find . -type f -not -path './.git/*' -print0 >"$list"
	fi
	# NUL-separated: a word-split list silently skips names with spaces or non-ASCII
	# characters, and a scan with a silent hole is worse than no scan.
	local f pos cmperr=$TMP/cmp.err base=$TMP/redact.base
	while IFS= read -r -d '' f; do
		[ -f "$f" ] || continue
		LC_ALL=C grep -qI . "$f" 2>/dev/null || continue # text files only
		# `cmp`'s stderr carries the truncation verdict (`EOF on -`) while its stdout
		# carries the difference verdict. Discarding stderr made a filter that stopped
		# mid-stream indistinguishable from a clean file.
		# shellcheck disable=SC2094 # "$f" is opened twice for READING only — once as the
		# filter's stdin, once as cmp's operand. SC2094 warns about a read/write pair;
		# nothing in this pipeline writes "$f", and comparing a file against its own
		# filtered stream is precisely what the scan is.
		pos=$(bash scripts/redact.sh <"$f" 2>/dev/null | cmp - "$f" 2>"$cmperr")
		# A difference is a SUSPICION, not yet a finding. The byte-exact comparison above
		# holds only where every stage of the filter is byte-transparent, and on a Windows
		# checkout it is not: with `core.autocrlf=true` — Git for Windows's own installer
		# default — the worktree is CRLF, MSYS2's sed drops the CR, and this rung reported a
		# credential in every text file in the tree, its own shipped scripts included
		# (AMH ledger row DC030). So re-compare against the baseline, which is the same
		# stages with nothing to substitute. Done only for a file that already differed, so
		# the exact comparison stays the fast path and a tree on a transparent platform pays
		# nothing for the second.
		if [ -s "$cmperr" ] || [ -n "$pos" ]; then
			bash scripts/redact.sh --baseline <"$f" >"$base" 2>/dev/null
			# The baseline is now standing in for the file, so it has to EARN that: it must
			# reproduce this file's own bytes apart from carriage returns, which is the one
			# difference the platform is allowed to make. Without this arm the subtraction
			# cancels anything the stages do to BOTH streams — a sed that truncates its
			# output truncates the baseline identically, the two agree, and the rung prints
			# a green over bytes it never read. That is the same fail-open the truncation
			# arm below exists to refuse, and it is why the tolerance is named (`\r`) rather
			# than inherited from whatever the tool happens to do.
			if ! LC_ALL=C tr -d '\r' <"$base" | cmp -s - <(LC_ALL=C tr -d '\r' <"$f"); then
				fail "scripts/redact.sh --baseline did not reproduce $f apart from line endings, so the filter's own stages are altering or dropping this file's bytes and NOTHING here scanned it. A filter whose stages rewrite content cannot be told apart from one that redacted something — fix the filter or the sed it runs on; a green from this rung would be a green over bytes nobody read."
				hits=$((hits + 1))
				continue
			fi
			pos=$(bash scripts/redact.sh <"$f" 2>/dev/null | cmp - "$base" 2>"$cmperr")
		fi
		if [ -s "$cmperr" ]; then
			# `cmp`'s own text names whichever operand it read, which is the scratch
			# baseline rather than anything the reader has. Say which stream that was.
			local why
			why=$(tr -d '\n' <"$cmperr")
			fail "scripts/redact.sh did not filter all of $f (${why//"$base"/the baseline stream}) — a truncated stream reads as clean"
			hits=$((hits + 1))
			continue
		fi
		if [ -n "$pos" ]; then
			# Report the POSITION only. A diagnostic that regresses to printing the
			# matched line defeats the entire point of the guard.
			fail "credential-shaped string in $f (${pos#*differ: })"
			hits=$((hits + 1))
		fi
	done <"$list"
	[ "$hits" = 0 ] && ok "no credential-shaped strings in tracked or untracked text files"
}

guard_poison_tokens() {
	section "Commit messages: poison tokens"
	local base
	base=$(upstream_ref)
	if ! has_git || [ -z "$base" ]; then
		# WARN, not skip. Without `origin/$DEFAULT_BRANCH` this guard has nothing to diff
		# against and checks nothing — it ran inert in the reference repo for its entire
		# life while printing a line that read like a considered pass. A guard that is
		# switched off must say so more loudly than one that passed (AMH ledger row D019).
		warn "no $DEFAULT_BRANCH reference to compare against — this guard checked NOTHING. Fetch it (\`git fetch origin $DEFAULT_BRANCH\`) or accept that poison tokens are unguarded locally."
		return
	fi
	local msgs tok hits=0
	msgs=$(git log --format='%B' "$base..HEAD" 2>/dev/null)
	if [ -z "$msgs" ]; then
		ok "no new commits to check"
		return
	fi
	while IFS= read -r tok; do
		[ -n "$tok" ] || continue
		# A here-string, NOT `printf ... | grep -q`. `grep -q` exits at its first match;
		# with bytes still pending the writer takes EPIPE, and under `pipefail` that
		# becomes the pipeline's status — so a token found EARLY in a long enough set of
		# messages reads as absent and this rung fails OPEN, silently. Commit messages are
		# inherently multi-line, so unlike the payload case size alone is enough here: at
		# ~64 KB of `git log` output a token in the newest commit is simply not reported.
		# A here-string's writer is not a pipeline member and never reaches `PIPESTATUS`
		# (AMH ledger row DC034, DC035).
		if grep -qF -- "$tok" <<<"$msgs"; then
			fail "commit message contains '$tok' — a squash merge would fold it onto $DEFAULT_BRANCH, and force-push is forbidden, so it is permanent until merge"
			hits=$((hits + 1))
		fi
	done < <(printf '%s\n' "$POISON_TOKENS" | tr '|' '\n')
	[ "$hits" = 0 ] && ok "clean"
}

# Git author identity, over `%ae` AND `%ce` across origin/<default>..HEAD.
#
# **What this guard cannot do, plainly, because implying more than a guard delivers is
# what stops the next reader checking by hand: it cannot tell a personal address from a
# work one, nor a forge no-reply alias from the address it stands in for.** A
# well-formed address that is simply the wrong identity for this project passes here.
# Choosing which identity to commit under stays a prose rule; this rung catches only the
# machine-generated case below, plus whatever pattern the repository chose to state.
#
# Two halves, deliberately unequal.
#
# ZERO-CONFIG: fail on the identities git invents when nobody configured one — `root@box`,
# `you@localhost`, `you@laptop.local`, `you@box.localdomain`, `(none)`, an empty field,
# anything with no `@` at all. These are machine names rather than addresses, which is why
# this half needs no list of who is allowed to commit here. The surface is small but it is
# NOT empty, and saying it was empty would be the false-coverage claim this file warns
# about everywhere else: `.local` is a real Active Directory and mDNS suffix, and a build
# account can legitimately be `root@` a real domain. That is what the override below is
# for. Using the repository's OWN history as an allowlist was considered and rejected: a
# first-time contributor and a misconfigured one are indistinguishable, so it would fail
# every commit of a new branch and teach the reader to skip the rung.
#
# OPT-IN: AUTHOR_EMAIL_ALLOW, an extended regex matched against the WHOLE address, empty
# by default IN THIS SCRIPT. That default is load-bearing rather than lazy — amh.conf is
# yours forever and this harness cannot upgrade it, so a rung that needed a new key would
# turn an existing adopter's ladder red until they hand-edited a file they were told they
# own. Unset means the zero-config half alone, and the ok line says which.
#
# The allowlist is consulted FIRST and a match ends the check, so naming an address makes
# it acceptable whatever shape it has. Without that ordering the zero-config half is a
# dead end: `alice@corp.local` would be rejected, adding it to the very key this file
# offers for "state your identities" would not help, and the only remedy left would be
# editing a shipped script whose header says not to. No adopter should ever be told that.
# With the key unset — the default — nothing overrides anything.
#
# One caveat about anchoring, since the wrapper cannot defend against everything: the
# pattern is wrapped in `^(…)$`, so a top-level alternation is fine (`a@x\.com|b@y\.com`),
# but one carrying unbalanced-looking parentheses of its own — `a)|(b` — reparses into
# `^(a)|(b)$` and is silently unanchored. It is still a valid regex, so the probe below
# cannot catch it. Write alternations without stray parentheses.
#
# The window is origin/<default>..HEAD because that is where the fix is still available:
# an unpushed commit is amendable, a merged one is not, and a repo that forbids itself
# force-push has no other chance. A PRE-commit guard is impossible here — an identity you
# have not committed yet is not on disk to check — but that is a fact about one moment,
# not about all of them.
#
# The failure lines print the offending identity. It is already in the commit object this
# guard is naming, so the line publishes nothing the metadata does not, and a rung that
# said only "some identity is wrong" would leave you grepping for which.
guard_author_identity() {
	section "Git author identity ($DEFAULT_BRANCH..HEAD)"
	local base
	base=$(upstream_ref)
	if ! has_git || [ -z "$base" ]; then
		# WARN, not skip, for the reason the poison-token scan states above: a guard that
		# resolved no ref checked NOTHING and must say so louder than a pass does
		# (AMH ledger row D019). The message LEADS with its own subject rather than with
		# the condition, because the scan above emits the same condition in the same words:
		# a fixture grepping the shared opening is satisfied by whichever rung printed it,
		# and the distinguishing clause sat past a backtick no assertion could quote
		# without tripping the linter.
		warn "author identity is unguarded locally: no $DEFAULT_BRANCH reference to compare against, so this guard checked NOTHING. Fetch it (\`git fetch origin $DEFAULT_BRANCH\`)."
		return
	fi

	# A malformed regex must not decide anything quietly. Left alone, `grep -E` on one
	# exits 2 — indistinguishable from "no match" to an `if` — so every identity in the
	# repository would fail on an allowlist that allows nothing. Probe it against empty
	# input once: exit 0 or 1 is a verdict, 2 or more is a broken pattern.
	# `state` carries WHY no allowlist was applied, so the ok line cannot claim the key is
	# unset when it is set and invalid. A verdict line that contradicts the warning above
	# it is the shape this script has already been burned by once.
	local allow=$AUTHOR_EMAIL_ALLOW rc=0 state=unset
	if [ -n "$allow" ]; then
		state=applied
		grep -qE "^($allow)$" </dev/null 2>/dev/null || rc=$?
		if [ "$rc" -gt 1 ]; then
			warn "AUTHOR_EMAIL_ALLOW is not a valid extended regex — ignoring it, so only the zero-config half of this guard ran. Fix it in amh.conf; an allowlist that silently allows nothing fails every identity for the wrong reason."
			allow=''
			state=ignored
		fi
	fi

	local commits idents
	commits=$(git rev-list --count "$base..HEAD" 2>/dev/null)
	# One line per field per commit, so the diagnostic can name WHICH field is wrong. A
	# rebase or an amend by another tool rewrites the committer while the author survives
	# untouched, which is the case checking `%ae` alone misses entirely.
	idents=$(git log --format='author %ae%ncommitter %ce' "$base..HEAD" 2>/dev/null | sort -u)
	if [ -z "$idents" ]; then
		ok "no new commits to check"
		return
	fi

	# One arm per distinguishable shape, each with its OWN wording. A single message shared
	# across the invented shapes reads fine and is untestable: with one fixture behind the
	# lot, four of the five globs could be deleted and every assertion still passed, because
	# nothing could tell which pattern had matched. `.local` and `.localdomain` do share an
	# arm, and its message names both — they are one shape (a LAN machine name) reached by
	# two suffixes, and each has its own fixture.
	local line field addr lower bad hits=0 seen=0
	while IFS= read -r line; do
		field=${line%% *}
		addr=${line#* }
		[ "$addr" = "$line" ] && addr='' # "author" with an empty field and no trailing text
		seen=$((seen + 1))

		# The stated identities win over everything below — see the ordering note above.
		if [ -n "$allow" ] && printf '%s' "$addr" | grep -qE "^($allow)$"; then
			continue
		fi

		# Lower-cased for matching only; every message prints the address as committed.
		# git stores what it was handed, so `ROOT@LOCALHOST` is a real thing to receive,
		# and `case` globs are case-sensitive — without this the whole half below is
		# bypassed by holding down shift.
		lower=$(printf '%s' "$addr" | LC_ALL=C tr '[:upper:]' '[:lower:]')
		bad=''
		case $lower in
		'')
			bad="is EMPTY — git recorded no address at all in this field"
			;;
		*'(none)'*)
			bad="carries git's '(none)' placeholder, which is what it writes when the machine's hostname has no domain — the usual identity of an unconfigured container"
			;;
		root@*)
			bad="is the machine's root account, which git falls back to when no user.email is set — not an address anyone reads"
			;;
		*@localhost)
			bad="names localhost, which is every machine and therefore no address"
			;;
		*@*.local | *@*.localdomain)
			bad="names a local-only host (.local, .localdomain), which is an mDNS or LAN machine name rather than a deliverable address"
			;;
		*@*) ;;
		*)
			bad="is not an email address — it has no '@', so git took a bare name"
			;;
		esac
		if [ -n "$bad" ]; then
			fail "$field identity '$addr' $bad. Set user.email and amend before pushing; a pushed commit cannot be repaired without the rewrite this repo forbids."
			hits=$((hits + 1))
			continue
		fi
		if [ -n "$allow" ]; then
			fail "$field identity '$addr' does not match AUTHOR_EMAIL_ALLOW — this repository states which identities its commits carry, and this is not one of them."
			hits=$((hits + 1))
		fi
	done <<<"$idents"

	if [ "$hits" = 0 ]; then
		case $state in
		applied)
			ok "$seen distinct field/address pair(s) over $commits commit(s); all well-formed and admitted by AUTHOR_EMAIL_ALLOW"
			;;
		ignored)
			ok "$seen distinct field/address pair(s) over $commits commit(s); all well-formed. AUTHOR_EMAIL_ALLOW was IGNORED as malformed (see the warning above), so no allowlist was applied"
			;;
		*)
			ok "$seen distinct field/address pair(s) over $commits commit(s); all well-formed. AUTHOR_EMAIL_ALLOW is unset, so no allowlist was applied"
			;;
		esac
	fi
}

guard_rail_selftests() {
	section "Rail self-tests (a silently regressed rail is no rail)"
	local s
	for s in scripts/redact.sh scripts/command-guard.sh; do
		# `[ -x ]` here printed nothing at all when the bit was missing — this whole
		# section went blank and the ladder stayed green. Absence gets a `skip` line,
		# the script's convention everywhere else; the exec bit gets no vote.
		if [ ! -f "$s" ]; then
			skip "$s is not a readable file — nothing self-tested it"
			continue
		fi
		if out=$(bash "$s" --self-test 2>&1); then
			ok "$s"
		else
			fail "$s self-test failed:"
			printf '%s\n' "$out" | sed 's/^/         /'
		fi
	done
}

# sha256 for one file, through whichever tool this machine has. Reads from STDIN rather than
# passing the path, so no filename ever reaches a tool's argument parsing and both tools emit
# the same `<hash>  -` shape. The trailing `sed` keeps the leading hex run and drops the rest.
# Prints nothing if neither tool exists — the caller decides what that means, and it is
# not allowed to mean "clean".
amh_sha256_tool() {
	if command -v sha256sum >/dev/null 2>&1; then
		printf 'sha256sum'
	elif command -v shasum >/dev/null 2>&1; then
		printf 'shasum'
	fi
}
amh_sha256() { # <tool> <file>
	case $1 in
	sha256sum) sha256sum <"$2" ;;
	shasum) shasum -a 256 <"$2" ;;
	esac | sed 's/[^0-9a-f].*//'
}

# The shipped scripts are still the ones the harness shipped.
#
# This is the SECOND integrity check over these files in the harness's own repository and
# the FIRST one in yours, and the distinction is the whole point: the meta-repo's copy-drift
# guard proves *it runs what it ships*, this rung proves *you still run what we shipped you*.
# Different claims, different trees, and only this one travels.
#
# What it is actually defending against is not malice. A shipped script edited locally —
# a threshold nudged, a rung deleted, a `return` added at the top of a guard — turns every
# future upgrade from a copy into a merge, and does it silently: the edit works, the ladder
# stays green, and the cost lands on whoever runs the next upgrade a year later. The three
# places a local change legitimately goes are amh.conf, scripts/guards/*.sh and
# scripts/verify.sh, and this rung's failure message names them, because a guard that only
# says "no" teaches people to delete the guard.
#
# WHAT IT CANNOT SEE, stated plainly because the rest of this comment is a coverage claim and
# an overstated one is what stops the next reader checking by hand:
#
#   1. The edit that DELETES this rung. A ladder that no longer calls this function reports
#      nothing, and no rung inside a script can be the thing that notices the script was cut.
#      The manifest is checkable by hand for exactly this reason — `sha256sum -c` against it
#      needs none of this code — and the header of the manifest says so.
#   2. A line REMOVED from the manifest excuses that file. This rung refuses the one removal
#      that would be self-serving (the entry for the ladder itself, see below) and reports the
#      count of what it checked, so a shrinking count is the signal for the rest. That is a
#      weaker claim than "no file can be excused", and the prose around this feature must not
#      make the stronger one.
#
# ABSENCE IS NOT A FAILURE, and that asymmetry is deliberate. The documented upgrade path is a
# copy out of the harness checkout; someone who copied only `*.sh` before the manifest existed
# has no manifest at all, and a rung that failed on absence would turn their ladder red for
# having followed the instructions in front of them — a fix that bills the person it broke.
#
# It is a WARN rather than a `skip`, which is the one place this rung breaks the convention the
# rest of this file uses for an absent artifact (no ledger, no repo-local guards). Those are
# repo-shape choices that assert nothing; this one is different in kind — deleting the manifest
# is also the documented way to live with a deliberate local patch, so the state an adopter
# reaches ON PURPOSE to switch the rung off must not be the quietest line the ladder prints. A
# disabled guard has to be louder than a passing one, and `skip` is counted by nothing.
#
# The same reasoning governs the STALE case, which is the likelier one and is worth stating
# in the failure text rather than leaving to be discovered: an adopter who upgrades with
# `cp .../scripts/*.sh scripts/` gets new scripts against last version's manifest, and every
# one of them then reads as tampered. The manifest sits in the same directory as the scripts
# it describes precisely so that copying the directory keeps them together.
#
# The path is a CONSTANT here, not an amh.conf key. A configurable path is a supported way
# to point the rung at nothing and collect a green `skip` — the shape this harness has
# already been burned by, one layer down, and the reason the secret scan stopped consulting
# a file mode.
guard_shipped_integrity() {
	section "Shipped scripts: integrity against the install manifest"
	local manifest=scripts/MANIFEST.sha256 self=scripts/ladder.sh
	if [ ! -f "$manifest" ]; then
		warn "$manifest is absent, so the shipped scripts were NOT checked against the hashes the harness published for them — this rung checked NOTHING. An install or upgrade through the harness's init script writes one; a hand copy of *.sh alone does not."
		return
	fi
	local tool
	tool=$(amh_sha256_tool)
	if [ -z "$tool" ]; then
		# WARN, not skip: absence of the manifest is a state the adopter chose, absence of a
		# hashing tool is a property of the machine that has nothing to do with the subject.
		warn "neither sha256sum nor shasum is on PATH, so $manifest was NOT verified — this rung checked NOTHING. Install coreutils (or Perl's shasum) if you want the shipped scripts covered here."
		return
	fi
	local line n=0 checked=0 bad=0 covers_self=0 want rest file got eol
	while IFS= read -r line || [ -n "$line" ]; do
		n=$((n + 1))
		# A CRLF checkout — the default on Windows, where the installer sets
		# `core.autocrlf=true` system-wide — hands every line a trailing CR, and this rung
		# was the one place it could not be shrugged off. The hash field comes FIRST, so it
		# still measured 64 characters and the corruption check below stayed quiet; only the
		# filename was polluted, and the rung went on to report five shipped scripts as
		# deleted and to tell the reader to re-run the init script to restore files that
		# were present all along — a true hash comparison wrapped in a false account of what
		# was checked, which the note below says is the one thing this rung must never do
		# (AMH ledger row DC030). Stripping it is unambiguous: a filename cannot contain a
		# CR, and a hash field is hex.
		line=${line%$'\r'}
		case $line in '' | '#'*) continue ;; esac
		# sha256sum's own format: `<hash>  <path>`, with `*` marking binary mode. Parsed
		# rather than trusted — a manifest this cannot read is a manifest that checked
		# nothing, so a malformed line FAILS instead of being skipped past.
		want=${line%% *}
		rest=${line#"$want"}
		rest=${rest#"${rest%%[![:space:]]*}"}
		file=${rest#\*}
		case $want in
		*[!0-9a-f]* | '') file='' ;;
		esac
		# A manifest entry names a shipped script and nothing else: `scripts/<name>`, one
		# level, no `..` and no absolute path. Without this the rung will happily hash
		# /etc/hostname and then tell you re-running the harness's init script will restore
		# it — a true verdict wrapped in a false description of what was checked, which is
		# worse than no verdict. The constraint is also what bounds the damage a hand-edited
		# manifest can do: it can excuse a shipped script, it cannot point the rung at
		# somewhere else entirely.
		case $file in
		scripts/*/* | scripts/ | *'..'*) file='' ;;
		scripts/?*) ;;
		*) file='' ;;
		esac
		if [ -z "$file" ] || [ "${#want}" -ne 64 ]; then
			fail "$manifest line $n is not a sha256 entry naming a shipped script — the form is a 64-character hash, two spaces, then scripts/<name>. The file is corrupt or hand-edited, and a manifest that cannot be read verifies nothing. Re-install it from the harness checkout."
			bad=$((bad + 1))
			continue
		fi
		[ "$file" = "$self" ] && covers_self=1
		checked=$((checked + 1))
		if [ ! -f "$file" ]; then
			fail "$manifest names $file, which is not in this tree — a shipped script has been deleted, or the manifest belongs to a different version. Re-run the harness's init script against this repo to restore both."
			bad=$((bad + 1))
			continue
		fi
		got=$(amh_sha256 "$tool" "$file")
		if [ "$got" != "$want" ]; then
			# Ask Git about the affected path instead of inspecting its bytes with another
			# text-mode tool. `ls-files --eol` reports the worktree representation Git
			# actually established, and prints nothing for an untracked file.
			eol=''
			has_git && eol=$(git ls-files --eol -- "$file" 2>/dev/null)
			case $eol in
			*' w/crlf '*)
				fail "$file does not match the hash the harness published for it, and Git reports a CRLF worktree for this tracked file. Line-ending conversion may have changed this byte-bound artifact. Retain or restore the harness-provided .gitattributes, re-normalize and re-check out the affected file, and only then re-run the harness's init script or this ladder."
				;;
			*)
				fail "$file does not match the hash the harness published for it. If you edited it: the change belongs in amh.conf, in a guard under scripts/guards/, or in scripts/verify.sh — re-running the harness's init script puts the shipped copy back. If you upgraded by copying *.sh by hand: copy $manifest out of the same directory too, or this rung reports every new script as edited."
				;;
			esac
			bad=$((bad + 1))
		fi
	done <"$manifest"
	if [ "$checked" = 0 ] && [ "$bad" = 0 ]; then
		# Every line a comment, or no lines at all. Green here would be a pass earned by an
		# empty file, which is the one verdict this rung must never give.
		fail "$manifest lists no scripts — an empty manifest passes everything. Re-install it from the harness checkout."
		return
	fi
	# The one entry that may never go missing is the one covering the file you are reading.
	# Deleting a line excuses that script; deleting THIS line excuses the script that decides
	# whether anything else is excused, and from there every other verdict here is worth
	# nothing. It is the only self-serving omission, so it is the only one that can be refused
	# without a list of shipped names — which this script must not carry, since the set changes
	# between versions and yours may be older or newer than the manifest's.
	if [ "$covers_self" = 0 ]; then
		fail "$manifest does not cover $self — a manifest that omits the ladder cannot vouch for anything, because the ladder is what reads it. Re-install it from the harness checkout."
		bad=$((bad + 1))
	fi
	[ "$bad" = 0 ] && ok "$checked shipped script(s) match the published hashes ($tool) — a lower count than your version ships means the manifest was edited"
}

# The section header is printed unconditionally, and the number of guards that actually
# RAN is always reported. Both were conditional on finding a guard, so `rm -rf
# scripts/guards` left this rung emitting nothing whatsoever — no header, no skip, no
# count — and the ladder stayed green. An empty extension point is a legitimate state for
# an adopter who has earned no repo-local guards yet; printing NOTHING for it is not, and
# it is indistinguishable from five guards that all passed silently. The disabled state
# must be louder than the passing one (AMH ledger row D019), so absence gets a `skip` line,
# the convention this script uses everywhere else, and the count is stated either way.
guard_repo_local() {
	section "Repo-local guards"
	if [ -e scripts/guards ] && [ ! -d scripts/guards ]; then
		skip "scripts/guards exists but is not a directory — 0 repo-local guard(s) ran"
		return
	fi
	if [ ! -d scripts/guards ]; then
		skip "scripts/guards (directory absent) — 0 repo-local guard(s) ran"
		return
	fi
	# Three verdicts, not two. A repo-local guard exits 0 for pass and non-zero for fail, with
	# ONE exception: exit 2 whose output begins `WARN ` is a warning — it prints through the
	# ladder's own warn channel, lands in the warning count and the verdict line, and does not
	# turn the ladder red. That is for a rule whose violation is usually wrong but sometimes
	# legitimately right, where failing closed would teach the adopter to delete the guard.
	#
	# The marker is required BECAUSE bash itself exits 2 on a syntax error. Without it, a guard
	# that stopped parsing would be downgraded from a failure into a warning — a broken guard
	# reporting as a mild opinion is the fail-open shape this file refuses everywhere else. A
	# syntax error's message does not begin with `WARN `, so the unmarked exit 2 stays a failure
	# and says why — as do `grep` and `diff`, which exit 2 on trouble, so the diagnostic names
	# a class rather than one cause. The marker is matched against the guard's MERGED output
	# (stdout and stderr, as everywhere else here), so a guard that prints to stdout before
	# warning on stderr does not warn. The warn text is the first line; the rest is indented.
	local g ran=0 seen=0 out rc
	for g in scripts/guards/*.sh; do
		# `-e` is false for an unmatched glob (bash leaves the pattern itself) AND for a
		# broken symlink, so `-L` is tested too. Anything the glob matched is COUNTED and,
		# if it cannot be run, named: `[ -f ]` alone silently dropped a broken symlink or
		# a directory called `x.sh`, and the count line then said "holds no *.sh" — an
		# affirmative false, which is worse than the silence this function was fixed to
		# stop. The same defect one level in.
		[ -e "$g" ] || [ -L "$g" ] || continue
		seen=$((seen + 1))
		if [ ! -f "$g" ]; then
			skip "$(basename "$g") is not a regular file — NOT run"
			continue
		fi
		ran=$((ran + 1))
		out=$(bash "$g" 2>&1)
		rc=$?
		case $rc in
		0)
			ok "$(basename "$g")${out:+ — $out}"
			;;
		2)
			case $out in
			'WARN '*)
				# FIRST LINE ONLY as the warn text, the rest indented like every other
				# continuation this file prints. A guard that warns in several lines would
				# otherwise hand raw text straight into the transcript at column zero — and
				# the ladder's own vocabulary lives at column zero, so `   ok    ...` or a
				# `✓ ladder green` line inside a guard's warning would render as the
				# ladder's verdict rather than as the guard's opinion of it.
				warn "$(basename "$g") — $(printf '%s' "${out#WARN }" | head -1)"
				printf '%s\n' "${out#WARN }" | tail -n +2 | sed 's/^/         /'
				;;
			*)
				fail "$(basename "$g") exited 2 without the leading WARN marker — read as a broken guard, not a warning (bash exits 2 on a syntax error, and grep and diff exit 2 on trouble):"
				printf '%s\n' "$out" | sed 's/^/         /'
				;;
			esac
			;;
		*)
			fail "$(basename "$g"):"
			printf '%s\n' "$out" | sed 's/^/         /'
			;;
		esac
	done
	if [ "$seen" = 0 ]; then
		skip "scripts/guards holds no *.sh — 0 repo-local guard(s) ran"
	elif [ "$ran" = 0 ]; then
		skip "nothing in scripts/guards was runnable — 0 repo-local guard(s) ran"
	else
		ok "$ran repo-local guard(s) ran"
	fi
}

# --- local-only advisories --------------------------------------------------
# WARN-only, skipped in CI: they describe the state of a working session, which CI
# does not have. Warn fatigue kills tripwires, so this list stays short.
advisories() {
	in_ci && return
	has_git || return
	local base
	base=$(upstream_ref)
	section "Local advisories (not run in CI)"

	if [ -n "$base" ]; then
		local changed
		changed=$(git diff --name-only "$base...HEAD" 2>/dev/null)
		if [ -n "$changed" ] && ! printf '%s\n' "$changed" | grep -qF "$STATE_FILE"; then
			warn "this branch changes files but not $STATE_FILE — the checkpoint's changelog line is probably missing"
		fi

		if ! git merge-base --is-ancestor "$base" HEAD 2>/dev/null; then
			local mt rc tree
			mt=$(git merge-tree --write-tree "$base" HEAD 2>/dev/null)
			rc=$?
			tree=$(printf '%s' "$mt" | head -1)
			if [ "$rc" -eq 0 ] && [ -n "$mt" ] && [ "$tree" = "$(git rev-parse 'HEAD^{tree}' 2>/dev/null)" ]; then
				warn "behind $base, but a clean test-merge leaves this tree unchanged — structural (the default branch advanced by a squash of this very work). Do NOT merge."
			elif [ "$rc" -eq 0 ] && [ -n "$mt" ]; then
				warn "behind $base and the merge would bring content — inspect what it brings, then merge it in (never rebase pushed history)."
			elif [ -z "$mt" ]; then
				warn "behind $base — could not classify (shallow clone or an older git). Usually structural; inspect before merging."
			else
				warn "behind $base and a test-merge conflicts — inspect what the merge would bring first (a deliberate revert on this branch looks like missing content)."
			fi
		fi
	fi

	if [ -d "$PLAN_DIR" ]; then
		local p
		for p in "$PLAN_DIR"/*; do
			[ -f "$p" ] || continue
			if ! grep -qF "$(basename "$p")" "$STATE_FILE" 2>/dev/null; then
				warn "$p is not referenced from $STATE_FILE — a finished or pivoted plan missed its completion step. Move a completed plan worth retaining whole to docs/history/ when that archive tier exists; otherwise delete it. Code cites ledger rows, never plans."
			fi
		done
	fi

	if [ -n "$RULE_FILES" ]; then
		local dirty rf touched=''
		dirty=$( (
			git diff --name-only
			git diff --cached --name-only
			git ls-files -o --exclude-standard
		) 2>/dev/null | sort -u)
		# Literal whole-path or directory-prefix matches, and `set -f` so an entry is
		# never glob-expanded against the working directory — see guard_citations. The
		# grep form this replaces interpolated each entry as a regex.
		set -f
		local d
		for rf in $RULE_FILES; do
			while IFS= read -r d; do
				case $d in
				"$rf" | "$rf"/*)
					touched="$touched $rf"
					break
					;;
				esac
			done <<<"$dirty"
		done
		set +f
		if [ -n "$touched" ]; then
			warn "uncommitted diff touches legislation:$touched — the rule-review protocol applies (fresh-context reviewer, strongest tier, no self-review fallback) BEFORE commit."
		fi
	fi

	# Destructive advisories that fired and were never re-attempted. The rail blocks such a
	# command once so the session spends a turn on the check; a session can also clear the
	# prompt by dropping the deletion — renaming the target, say — and that route left no
	# trace anywhere, which is how it became the cheap one. This line is the trace.
	#
	# It is deliberately NOT a warning and NOT a failure. Abandoning a deletion is a
	# legitimate outcome — the advisory says so in as many words — and a rung that went
	# amber every time an agent thought better of an `rm` would be noise inside a week.
	# Nothing consumes this: it reports the rail's own state files, it cannot see whether
	# anyone looked, and no guard, gate or decision procedure may read it as evidence that a
	# check happened (P3, AMH ledger row D014). It is a sentence for whoever reads the transcript.
	if [ -f scripts/command-guard.sh ]; then
		local unresolved
		unresolved=$(bash scripts/command-guard.sh --advisory-report 2>/dev/null)
		if [ -n "$unresolved" ]; then
			# The caveat rides on the LINE, not only in this comment: a transcript
			# reader sees the output and never the source, and the sentence claims
			# less than it appears to. The rail matches on command TEXT, so a
			# re-attempt bundled with a second deletion is a different signature and
			# leaves the first one listed even though it ran.
			note "destructive advisories fired and never re-attempted under this exact text (a bundled or reworded rerun still counts as never):"
			# The signature is the rail's identity for a target set: a command kind,
			# then each operand `%q`-quoted and joined. Printed with the joiner
			# trimmed, because this line is read by a person and the trailing `|`
			# says nothing to one. Nothing parses it back.
			printf '%s\n' "$unresolved" | sed -e 's/ *|$//' -e 's/^/         /'
		fi
		# The subagent rail's trace, printed separately because the heading above names
		# destructive advisories and this is not one. Same bounded claim: a spawn was
		# advised and went ahead. It does NOT say the spawns overlapped or that any was
		# unnecessary — the rail cannot see either — and nothing reads this line
		# (AMH ledger row D014).
		local spawns
		spawns=$(bash scripts/command-guard.sh --spawn-report 2>/dev/null)
		if [ -n "$spawns" ] && [ "$spawns" != 0 ]; then
			note "subagent spawns that proceeded past the advisory this session: $spawns (the rule permits ONE reviewer at a time, blocking; this line says a spawn went ahead, never that any two overlapped)"
		fi
	fi
	[ "$WARNS" = 0 ] && ok "nothing to flag"
}

# --- the verdict's subject --------------------------------------------------
# Every verdict line below ends with the commit and the worktree state it is a verdict
# ABOUT. The ladder said "green" and never said green OF WHAT — not here, not in the boot
# banner, nowhere in a session's output — for three releases, until an external reader
# found it in one pass (AMH ledger row DA025).
#
# It is one printf on lines that already existed, deliberately. The alternative on offer
# was a JSON run receipt written to an ignored directory; it was refused as forgeable and
# as a second verdict vocabulary, and this is the same intent reported in output the ladder
# already prints — the shape DA022(d) settled on for the ledger's byte count.
#
# Nothing consumes this string. It is not parsed, no exit code varies with it, and no
# guard, CI step or agent decision procedure may take it as input: it is a sentence for
# whoever reads the transcript, and the moment something branches on it, it has become the
# self-report gate the harness bans (P3).
#
# The dirty case is the whole reason the line exists, so it does not merely append a word.
# The ladder verifies the WORKING TREE — the secret scan and the citation scan both read
# untracked files — so attributing a green run to `HEAD` while the tree differs from it is
# a claim about a commit nobody verified. It says so in those words.
#
# THE DIRTINESS PROBE IS NOT `git status --porcelain`, and that is the single most
# important line in this function. `status` honours `status.showUntrackedFiles`, which can
# be set in the repository's own `.git/config` or in a user-level `~/.gitconfig` that no
# diff can see; `git ls-files -co --exclude-standard` — what the secret scan and the
# citation scan actually read — does not. With that key set to `no`, `status` reports a
# tree as clean while the scans are reading, and failing on, untracked files that are not
# in HEAD: the exact misattribution this line exists to prevent, reachable by one command
# that edits no tracked file and therefore appears in no diff and trips no guard. So the
# probe is built from the SAME sources the guards read. `.gitignore` is honoured by both,
# which is why an ignored build directory still does not render every run dirty.
#
# `--no-renames` because the count claims to be PATHS: rename detection collapses a moved
# file into one entry, so `git mv a b` would report one path having changed two.
#
# What this still cannot see, stated because the paragraph above is a coverage claim:
# `git update-index --assume-unchanged` hides a modified tracked file from `diff` and from
# `status` alike, so such a file reads as clean here. That is a deliberate act on one path,
# not a passive misconfiguration, and no probe built out of git's own plumbing escapes it.
#
# Four states, and none of them may be reported as one of the others:
#
#   (a) git names no repository from here — an extracted tarball outside any checkout, or
#                               an adopter mid-setup. Names no commit. Note what `has_git`
#                               actually asks, since every guard above uses it too: whether
#                               git resolves A repository from the working directory, which
#                               inside a monorepo `vendor/` or a dotfiles $HOME is the
#                               ENCLOSING one. A tree with no `.git` of its own is then
#                               described by its parent's commit, and so is the rest of the
#                               ladder. A repository too broken for `rev-parse --git-dir`
#                               also lands here, which understates it and is accepted: both
#                               readings tell the reader not to trust a commit name.
#   (b) git will not answer   — a corrupt index, above all. (An index LOCK is not this: a
#                               stale `.git/index.lock` leaves `git diff` exiting 0.) An
#                               empty answer is INDISTINGUISHABLE from a clean tree, so the
#                               exit status is read and an unusable answer is reported as
#                               UNKNOWN rather than collapsed into "clean" (AMH ledger row
#                               D019).
#   (c) unborn HEAD           — `git init` with nothing committed. There is no commit to
#                               name, which is not the same as there being no repository.
#   (d) a commit, clean or dirty.
#
# Only the COUNT is printed: the names are the tree's business and a verdict line is not
# where a path belongs. It is sampled AFTER rung 3 returns, so a verification set that
# wrote into its own worktree would be counted — none of the shipped ones does, and one
# that did would be reporting something true.
subject() {
	local sha paths rc n
	if ! has_git; then
		printf 'git names no repository from here, so this verdict names no commit'
		return
	fi
	sha=$(git rev-parse --short HEAD 2>/dev/null)
	# `&&`-chained rather than piped, so a git that refuses is caught by this assignment's
	# own exit status instead of by whatever `sort` thought of it. Not sorted here for the
	# same reason: the dedup happens below, once the status has been read.
	paths=$({
		git diff --name-only --no-renames &&
			git diff --cached --name-only --no-renames &&
			git ls-files -o --exclude-standard
	} 2>/dev/null)
	rc=$?
	if [ "$rc" -ne 0 ]; then
		if [ -n "$sha" ]; then
			printf 'HEAD %s, worktree state UNKNOWN (git would not report it) — this verdict may not be about that commit' "$sha"
		else
			printf 'commit and worktree state both UNKNOWN (git would not report them) — this verdict names no commit'
		fi
		return
	fi
	if [ -z "$paths" ]; then
		n=0
	else
		n=$(printf '%s\n' "$paths" | sort -u | wc -l | tr -d ' ')
	fi
	if [ -z "$sha" ]; then
		printf 'no commit yet (unborn HEAD), %s uncommitted path(s) — this verdict names no commit' "$n"
	elif [ "$n" = 0 ]; then
		printf 'HEAD %s, worktree clean' "$sha"
	else
		printf 'HEAD %s + %s uncommitted path(s) — the tree just verified is NOT that commit' "$sha" "$n"
	fi
}

# =============================================================================
run_guards() {
	guard_state_size
	guard_state_structure
	guard_ledger_rollover
	guard_citations
	guard_secret_shapes
	guard_poison_tokens
	guard_author_identity
	guard_rail_selftests
	guard_shipped_integrity
	guard_repo_local
	advisories
}

run_guards

if [ "$FAILS" -gt 0 ]; then
	printf '\n✗ guards: %d failure(s), %d warning(s) — %s\n' "$FAILS" "$WARNS" "$(subject)"
	exit 1
fi

if [ "$GUARDS_ONLY" = 1 ]; then
	printf '\n✓ guards clean (%d warning(s)), verification set NOT executed (guards-only run) — %s\n' "$WARNS" "$(subject)"
	exit 0
fi

# =============================================================================
# 3. The full verification set, in one invocation.
# =============================================================================
section "Verification set (scripts/verify.sh)"
if [ ! -x scripts/verify.sh ]; then
	fail "scripts/verify.sh is missing or not executable — the ladder has no verification rung"
	printf '\n✗ ladder red — %s\n' "$(subject)"
	exit 1
fi
if scripts/verify.sh; then
	printf '\n✓ ladder green (%d warning(s)) — %s\n' "$WARNS" "$(subject)"
	exit 0
fi
printf '\n✗ ladder red (verification set failed) — %s\n' "$(subject)"
exit 1
