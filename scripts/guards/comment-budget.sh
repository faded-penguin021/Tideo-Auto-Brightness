#!/usr/bin/env bash
# Repo-local ladder guard — the Kotlin comment budget (DB-028).
#
# The constitution's Conventions section puts durable prose in the .md tier — a ledger row or a
# docs/rebuild/ page — and leaves the code carrying a short `D-NNN` pointer. Nothing enforced
# that, and the tree drifted to 7620 comment lines against 40651 lines of Kotlin (18.7%), much of
# it re-telling a ledger row that already held the same narrative in full. Two copies of one
# lesson is two things to keep in sync, and the code copy is the one nobody updates.
#
# TWO CHECKS, and they catch different regressions:
#
#   1. BLOCK CAP — no contiguous run of comment-only lines may exceed COMMENT_BLOCK_MAX_LINES.
#      This is the structural one. Narrative does not fit in 12 lines, so a comment that wants to
#      tell a story has to go to the .md tier and leave a pointer. It fires on the single edit
#      that introduces it, which is what makes it teachable.
#   2. MODULE BUDGET — total comment lines per module may not exceed its constant. The block cap
#      cannot see density that arrives as hundreds of individually-reasonable two-line comments,
#      which is precisely how the tree got here.
#
# The budgets are a RATCHET, not a target. They were set from the measured post-consolidation
# tree plus a small working margin. Raising one is a legislative act, not housekeeping:
# scripts/guards is in RULE_FILES, so a diff here trips the ladder's rule-review tripwire and the
# review protocol applies. If you are raising a budget to land a change, the prose belongs in the
# ledger instead — that is the whole point of the number.
#
# WHAT THIS CANNOT DO, stated plainly because the rest of this header is a coverage claim and an
# overstated one is what stops the next reader checking by hand:
#   * It cannot tell a load-bearing comment from a worthless one. It counts lines. A file can pass
#     every check here while every surviving comment restates its own function name.
#   * It does not read the .md tier, so it cannot confirm that evicted prose actually landed
#     anywhere. `guard_citations` covers the narrower claim that a cited row still exists.
#   * `--file` mode checks the block cap ONLY. A module total is a property of the whole tree, so
#     one file cannot be judged against it.
#
# Counting is a real Kotlin scan, not a grep for a leading slash: `//` inside a string literal is
# code, a triple-quoted raw string full of `//` lines is code, and block comments nest in Kotlin.
# A grep-based count gets all three wrong, and a guard that miscounts is one people delete.
#
# Tier: FAILS CLOSED (exit 1), like every other repo-local guard here. A budget that only warned
# is a budget the next session spends — see docs/HARNESS_LOCAL.md for the tier rationale.
set -uo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT" || exit 1

COMMENT_BLOCK_MAX_LINES=12

# Floor on `// Tasker` provenance markers across the tree.
#
# This exists because THIS GUARD is the thing that endangers them. The constitution mandates a
# provenance marker on ported logic — `// Tasker: task535 "Lux Smoothing (Java)" XML L15204` — and
# those markers are comments, so every downward pressure the budget applies falls on them too. The
# first consolidation pass deleted four of them (SettingsControls, CircadianScreen, SolarTimes,
# WifiSsidStrategies) despite an explicit instruction not to.
#
# `ProvenanceTest` in :domain already floors this, but only for `BrightnessEngine.kt` — one file of
# the 26 that carry markers. Everywhere else the rule was prose with nothing behind it. A budget
# that pushes comment counts down while the only defence covers one file is a guard that erodes the
# audit trail it was supposed to leave alone.
#
# Counted the way ProvenanceTest counts — lines containing the literal `// Tasker` — deliberately,
# so the two agree rather than each being subtly right in its own way.
#
# A RATCHET, like the budgets: deleting genuinely dead ported logic legitimately lowers this, and
# lowering it is then a one-line diff that trips the rule-review tripwire. That is the point — the
# removal becomes visible instead of silent.
TASKER_PROVENANCE_FLOOR=64

# Per-module comment-line ceilings. Set from the measured tree; see the header on why raising one
# is a rule change. Keep these in lockstep with the row in docs/HARNESS_LOCAL.md.
BUDGET_app=99999
BUDGET_domain=99999
BUDGET_platform=99999

MODULES='app domain platform'

# Character-level Kotlin scan. Emits one record per file:
#   COUNT <file> <comment_lines> <total_lines>
#   BLOCK <file> <start_line> <run_length>          (only for runs over the cap)
#
# State is carried ACROSS lines (block-comment depth, raw-string mode, template nesting), which is
# why this is one awk over the whole file rather than a per-line pattern.
scan_kotlin() { # <file>...
	awk -v cap="$COMMENT_BLOCK_MAX_LINES" '
	function reset() { depth = 0; raw = 0; str = ""; sp = 0; run = 0; runstart = 0; cl = 0; tl = 0 }
	FNR == 1 { if (NR > 1) flush(); reset(); file = FILENAME }
	{
		tl++
		hasc = 0; hascode = 0
		n = length($0); i = 1
		while (i <= n) {
			c  = substr($0, i, 1)
			c2 = substr($0, i, 2)
			c3 = substr($0, i, 3)
			if (depth > 0) {                       # inside /* ... */, which nests
				if (c2 == "/*") { depth++; hasc = 1; i += 2; continue }
				if (c2 == "*/") { depth--; hasc = 1; i += 2; continue }
				hasc = 1; i++; continue
			}
			if (raw == 1) {                        # inside """ ... """
				# A raw string closes at the LAST quote of a run, so `""""` is one content
				# quote followed by the terminator. Consuming a fixed three would leave a
				# stray quote that opens a phantom string literal and silently swallows
				# every comment after it — which is exactly what it did, in
				# HardcodedStringCheckTest.kt, before this loop counted the run.
				if (c3 == "\"\"\"") {
					q = 0
					while (substr($0, i + q, 1) == "\"") q++
					raw = 0; hascode = 1; i += q; continue
				}
				if (c2 == "${") { stack[++sp] = "raw"; raw = 0; braces[sp] = 1; hascode = 1; i += 2; continue }
				if (c != " " && c != "\t") hascode = 1
				i++; continue
			}
			if (str != "") {                       # inside a "..." or a char literal
				if (c == "\\") { hascode = 1; i += 2; continue }
				if (c2 == "${") { stack[++sp] = "str:" str; str = ""; braces[sp] = 1; hascode = 1; i += 2; continue }
				if (c == str) { str = "" }
				hascode = 1; i++; continue
			}
			# --- code mode ---
			if (c2 == "//") { hasc = 1; break }     # rest of the line is a comment
			if (c2 == "/*") { depth = 1; hasc = 1; i += 2; continue }
			if (c3 == "\"\"\"") { raw = 1; hascode = 1; i += 3; continue }
			if (c == "\"" || c == "\047") { str = c; hascode = 1; i++; continue }
			if (c == "{" && sp > 0) { braces[sp]++ }
			else if (c == "}" && sp > 0) {
				if (--braces[sp] == 0) {
					m = stack[sp--]
					if (m == "raw") raw = 1
					else { str = substr(m, 5) }
				}
			}
			if (c != " " && c != "\t") hascode = 1
			i++
		}
		if (hasc && !hascode) {
			cl++
			if (run == 0) runstart = FNR
			run++
		} else {
			if (run > cap) printf "BLOCK %s %d %d\n", file, runstart, run
			run = 0
		}
	}
	function flush() {
		if (run > cap) printf "BLOCK %s %d %d\n", file, runstart, run
		if (file != "") printf "COUNT %s %d %d\n", file, cl, tl
	}
	END { flush() }
	' "$@"
}

# --- hook mode: read a PostToolUse payload on stdin and check the file it wrote --------------
#
# The block cap is the half of this guard that can fire at the moment of writing, and that is the
# whole reason this mode exists. A ladder run happens once, at the end, after the narrative is
# already written and the session has moved on; a rule that only lands there is a rule the writer
# meets as rework. Here it answers back on the edit itself.
#
# Payload parsing mirrors scripts/command-guard.sh exactly — python3 first, a sed fallback second
# — because that is the shipped guard's own idiom for the same problem and neither tool is in
# REQUIRED_TOOLS. If BOTH fail we exit 0: a hook that cannot read its payload has checked nothing,
# and a false failure on every Edit in the session would get the hook deleted within the hour. The
# ladder still covers the tree, so the cost of that silence is bounded to salience, not coverage.
#
# Exit 2, not 1: for PostToolUse, Claude Code feeds stderr back to the model on exit 2 and ignores
# it otherwise. The tool has ALREADY run — this cannot and does not block the edit, it only tells
# the writer what they just did. The ladder invokes this script with no arguments, so this exit
# code never reaches the ladder's repo-local-guard contract, where a WARN-less 2 means a broken
# guard.
if [ "${1:-}" = "--hook" ]; then
	payload=$(cat)
	f=$(printf '%s' "$payload" | python3 -c 'import json,sys
try:
    print(json.load(sys.stdin).get("tool_input", {}).get("file_path", ""))
except Exception:
    pass' 2>/dev/null)
	if [ -z "$f" ]; then
		f=$(printf '%s' "$payload" |
			sed -n 's/.*"file_path"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -1)
	fi
	[ -n "$f" ] || exit 0
	case $f in
	*.kt) ;;
	*) exit 0 ;;
	esac
	[ -f "$f" ] || exit 0
	bad=$(scan_kotlin "$f" | awk '$1 == "BLOCK"')
	[ -n "$bad" ] || exit 0
	printf '%s\n' "$bad" | while read -r _ file start run; do
		printf 'comment budget: %s:%s starts a %s-line comment block, over the %s-line cap.\n' \
			"$file" "$start" "$run" "$COMMENT_BLOCK_MAX_LINES" >&2
	done
	printf 'Durable prose belongs in the .md tier, not in the source: put the narrative in its ledger row (docs/LEDGER_B.md, append-only) or the matching docs/rebuild/ page, and leave a one-line `// D-NNN: <summary>` pointer here. Shorten the block before moving on.\n' >&2
	exit 2
fi

# --- single-file mode (same check, path given directly; for tests and manual use) ------------
if [ "${1:-}" = "--file" ]; then
	f=${2:-}
	case $f in
	*.kt) ;;
	*) exit 0 ;;
	esac
	[ -f "$f" ] || exit 0
	bad=$(scan_kotlin "$f" | awk '$1 == "BLOCK"')
	[ -n "$bad" ] || exit 0
	printf '%s\n' "$bad" | while read -r _ file start run; do
		printf 'comment budget: %s:%s starts a %s-line comment block, over the %s-line cap.\n' \
			"$file" "$start" "$run" "$COMMENT_BLOCK_MAX_LINES" >&2
	done
	printf 'Durable prose belongs in the .md tier, not in the source: put the narrative in its ledger row (docs/LEDGER_B.md, append-only) or the matching docs/rebuild/ page, and leave a one-line `// D-NNN: <summary>` pointer here. A block this long is the thing the budget exists to stop.\n' >&2
	exit 1
fi

if [ "${1:-}" != "" ]; then
	# Exit 3, not 2. The ladder reads a WARN-less exit 2 from a repo-local guard as "broken
	# guard", which is true but tells the reader nothing; a distinct code keeps a mistyped
	# argument from being reported as a parse failure in this script.
	printf 'usage: %s [--hook | --file <path.kt>]\n' "$0" >&2
	exit 3
fi

# --- whole-tree mode (the ladder) ------------------------------------------------------------
fails=0
report=$(mktemp)
trap 'rm -f "$report"' EXIT

list=$(mktemp)
trap 'rm -f "$report" "$list"' EXIT
git ls-files -- '*.kt' >"$list" 2>/dev/null
if [ ! -s "$list" ]; then
	printf 'no Kotlin files tracked — this guard checked NOTHING, which is not a pass\n' >&2
	exit 1
fi
# One awk over every file: the scan carries state across lines, and FNR==1 restarts it per file.
# xargs would split the list into batches on a large tree, which is harmless here only because
# each file is self-contained — but a single invocation keeps that from being a thing to know.
scan_kotlin_from_list() { local IFS=$'\n'; set -f; scan_kotlin $(cat "$list"); set +f; }
scan_kotlin_from_list >"$report"

over=$(awk '$1 == "BLOCK"' "$report")
if [ -n "$over" ]; then
	printf '%s\n' "$over" | while read -r _ file start run; do
		printf 'comment budget: %s:%s starts a %s-line comment block, over the %s-line cap — move the narrative to its ledger row or a docs/rebuild/ page and leave a `// D-NNN:` pointer\n' \
			"$file" "$start" "$run" "$COMMENT_BLOCK_MAX_LINES" >&2
	done
	fails=$((fails + 1))
fi

summary=''
for m in $MODULES; do
	eval "budget=\$BUDGET_$m"
	got=$(awk -v m="$m" '$1 == "COUNT" && index($2, m "/") == 1 { s += $3 } END { print s + 0 }' "$report")
	if [ "$got" -gt "$budget" ]; then
		printf 'comment budget: %s holds %s comment lines, over its %s-line budget by %s — the fix is to move prose to the ledger, NOT to raise the number here (that is a rule change, see this guard'"'"'s header)\n' \
			"$m" "$got" "$budget" "$((got - budget))" >&2
		fails=$((fails + 1))
	fi
	summary="$summary $m=$got/$budget"
done

# Provenance floor. Counted with grep over the same file list rather than through the scanner
# above: ProvenanceTest counts `it.contains("// Tasker")` on raw lines, and this must agree with it
# rather than be independently clever. A marker inside a string literal would be counted by both,
# which is the harmless direction — it cannot cause a false FAILURE, only a false pass on a line
# nobody writes.
tasker=$(xargs -a "$list" grep -h -- '// Tasker' 2>/dev/null | wc -l | tr -d '[:space:]')
if [ "${tasker:-0}" -lt "$TASKER_PROVENANCE_FLOOR" ]; then
	printf 'comment budget: %s `// Tasker` provenance line(s) in the tree, under the floor of %s — %s marker(s) were deleted. These are the XML-to-Kotlin audit trail the constitution mandates, and this guard is what puts them at risk, so restore them rather than lowering the floor. Compare against the branch point:\n  git grep -c "// Tasker" <base> -- "*.kt"\n' \
		"$tasker" "$TASKER_PROVENANCE_FLOOR" "$((TASKER_PROVENANCE_FLOOR - tasker))" >&2
	fails=$((fails + 1))
fi

[ "$fails" = 0 ] || exit 1
printf 'comment budget:%s; no block over %s lines; %s/%s Tasker provenance lines\n' \
	"$summary" "$COMMENT_BLOCK_MAX_LINES" "$tasker" "$TASKER_PROVENANCE_FLOOR"
