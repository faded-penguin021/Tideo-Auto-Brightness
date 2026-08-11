#!/usr/bin/env bash
# Repo-local ladder guard — the Kotlin comment budget (DB-028).
#
# The constitution's Conventions section puts durable prose in the .md tier — a ledger row or a
# docs/rebuild/ page — and leaves the code carrying a short `D-NNN` pointer. Nothing enforced
# that, and the tree drifted to 7620 comment lines against 40651 lines of Kotlin (18.7%), much of
# it re-telling a ledger row that already held the same narrative in full. Two copies of one
# lesson is two things to keep in sync, and the code copy is the one nobody updates.
#
# THREE CHECKS, and they catch different regressions:
#
#   1. BLOCK CAP — no contiguous run of comment-only lines may exceed COMMENT_BLOCK_MAX_LINES.
#      This is the structural one. Narrative does not fit in 12 lines, so a comment that wants to
#      tell a story has to go to the .md tier and leave a pointer. It fires on the single edit
#      that introduces it, which is what makes it teachable.
#   2. MODULE BUDGET — total comment lines per module may not exceed its constant. The block cap
#      cannot see density that arrives as hundreds of individually-reasonable two-line comments,
#      which is precisely how the tree got here.
#   3. PROVENANCE MANIFEST — every `// Tasker` audit-trail record the manifest names must still be
#      there. This one is a FLOOR, and it exists because checks 1 and 2 are what endanger it.
#
# The budgets are a RATCHET, not a target: the measured post-consolidation tree plus a stated
# margin. Raising one is a legislative act rather than housekeeping — scripts/guards is in
# RULE_FILES, so a diff here trips the ladder's rule-review tripwire and the review protocol
# applies. Raising one is also LEGITIMATE, and the guard says so where it fails: new code with new
# load-bearing documentation is exactly the case the margin cannot always absorb, and the review is
# where that gets argued. What the number forbids is spending it silently. The failure a budget is
# aimed at is durable prose duplicated out of the .md tier; when that is what pushed a module over,
# moving the prose back is the repair and raising the number is evasion.
#
# WHAT THIS CANNOT DO, stated plainly because the rest of this header is a coverage claim and an
# overstated one is what stops the next reader checking by hand:
#   * It cannot tell a load-bearing comment from a worthless one. It counts lines. A file can pass
#     every check here while every surviving comment restates its own function name.
#   * It does not read the .md tier, so it cannot confirm that evicted prose actually landed
#     anywhere. `guard_citations` covers the narrower claim that a cited row still exists.
#   * `--file` mode checks the block cap ONLY. A module total and the provenance manifest are both
#     properties of the whole tree, so one file cannot be judged against either.
#   * The provenance manifest keys on normalised source coordinates, so it sees a DROPPED reference
#     and not a re-pointed one. Its own header states that boundary in full.
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

# Per-module comment-line ceilings, and the margin that sets them.
#
# Each is the measured post-consolidation count for the module plus COMMENT_BUDGET_MARGIN_PCT, so
# the headroom is a stated policy rather than an accident of where the tree happened to land. The
# margin exists because a module that is exactly at its measured size has a budget of zero for the
# next honest KDoc, and a guard whose only possible repair is deleting documentation is one that
# drives out the documentation worth keeping. It is deliberately small: enough for a new adapter or
# a newly-documented signature, not enough for a narrative.
#
# Raising one past the margin is legitimate and is a RULE CHANGE, not housekeeping — scripts/guards
# is in RULE_FILES, so the diff trips the rule-review tripwire and the reviewer's question is "why
# does this narrative need to be in source rather than the .md tier". Answer that in the review and
# the increase lands; the number is not sacred, the justification is. What the budget forbids is
# raising it SILENTLY as the cheap way to land a change.
#
# These numbers live HERE and nowhere else, deliberately. An earlier draft of this comment told the
# next reader to keep them "in lockstep with the row in docs/HARNESS_LOCAL.md" — a row that does not
# exist, so the instruction was an invitation to CREATE the duplicate that DB-025 and DB-027 are
# both about. If you find yourself restating one of these in prose, don't: name the key instead.
COMMENT_BUDGET_MARGIN_PCT=5
BUDGET_app=2400
BUDGET_domain=460
BUDGET_platform=310

MODULES='app domain platform'

# --- Tasker provenance: a per-record manifest, not a population count -------------------------
#
# This exists because THIS GUARD is the thing that endangers the markers. The constitution mandates
# a provenance marker on ported logic — `// Tasker: task535 "Lux Smoothing (Java)" XML L15204` — and
# those markers are comments, so every downward pressure the budget applies falls on them too. The
# first consolidation pass deleted four of them (SettingsControls, CircadianScreen, SolarTimes,
# WifiSsidStrategies) despite an explicit instruction not to.
#
# `ProvenanceTest` in :domain already floors provenance, but only for `BrightnessEngine.kt` — one
# file of the 33 that carry markers. Everywhere else the rule was prose with nothing behind it.
#
# THE FIRST VERSION OF THIS CHECK WAS A SINGLE TREE-WIDE COUNT (`grep -c '// Tasker'` >= 68) AND
# THAT WAS NOT ENOUGH — it protected the population, never any individual marker. Delete a marker
# from one algorithm and add one anywhere else and the total is unchanged, so the guard passes while
# an algorithm has silently lost its audit trail. That is not an adversarial bypass; it is what a
# maintenance change that splits or retires one ported path does by accident. A count cannot name
# which marker left, so its diagnostic could not have pointed at the damage either.
#
# So the unit is a RECORD, keyed by file plus the Tasker SOURCE COORDINATES the marker cites, and
# the check is per-record: every record in the manifest must still be present, at least as often as
# the manifest says. New records need no manifest edit — the manifest is a floor, never a whitelist.
#
# WHY COORDINATES AND NOT THE MARKER TEXT. Pinning the literal line was the obvious design and it is
# wrong: 22 of the 68 markers were reworded by this very branch's consolidation, all of them
# shortening the prose while keeping the same task/act reference. A manifest keyed on text would
# have gone red on all 22, and a rule that fires on every honest prose edit is one that gets
# regenerated by reflex until it means nothing. What is load-bearing in a marker is the pointer into
# the Tasker XML — `task535`, `prof759`, `act28`, `elements26`, `L15204`, `%AAB_Scale` — not the
# sentence around it. Keying on the coordinate set is stable across rewording and still fails when a
# reference is dropped. That is not a hypothesis: normalising this way found two markers in
# `BrightnessPolicyInput.kt` whose `act10/14` and `act26/27/28` coordinates the consolidation had
# quietly dropped while the tree-wide count sat at exactly 68 and passed.
#
# WHAT IT STILL CANNOT DO, stated plainly:
#   * Coordinates are normalised, so `act10/14` and `act10/99` are the same record. A marker
#     re-pointed at a different sub-action inside the same task is not detected.
#   * A record with no recognised coordinate degenerates to "this file has N `// Tasker` mentions"
#     — the old population check, scoped to one file. 15 of the 59 records are of this kind, and
#     four of those are in `ProvenanceTest.kt`, where the matched lines are prose ABOUT the marker
#     syntax rather than markers. They are kept rather than hand-excluded because a hand-maintained
#     exclusion list is a second thing to keep true; the cost is that rewording that test needs a
#     manifest regeneration.
#   * It reads the tree, not the XML. Nothing here checks that `task535` names a real Tasker task.
#
# Marker lines are matched exactly as `ProvenanceTest` matches them — any line containing the
# literal `// Tasker` — so the two agree on what a marker is rather than each being subtly right in
# its own way.
#
# The manifest is a RATCHET. Retiring genuinely dead ported logic legitimately removes a record, and
# removing it is then a visible line in a RULE_FILES diff instead of a silent deletion. Regenerate
# it with the guard's own mode, so the manifest and the checker can never drift into two
# normalisations:
#   scripts/guards/comment-budget.sh --provenance-records
# Baseline it from the MERGE BASE, never the working tree. The first version of the old count was
# set to 64 from a tree that was mid-consolidation — four markers were already gone, so the floor
# would have ratified their loss and called it the baseline. A ratchet read off a tree that is
# mid-change is not a ratchet. This manifest is the branch point (b2b62fc).
#
# Fields are TAB-separated: `<count>\t<path>\t<coordinates>`. Tab, because a path may contain a
# space and a coordinate set may not contain a tab.
TASKER_PROVENANCE_MANIFEST=$(
	cat <<'MANIFEST'
1	app/src/main/kotlin/com/tideo/autobrightness/app/runtime/AmbientMonitoringService.kt	(no-coordinate)
1	app/src/main/kotlin/com/tideo/autobrightness/app/runtime/ContextEngine.kt	%AAB_CurrentActiveProfile,act17
1	app/src/main/kotlin/com/tideo/autobrightness/app/runtime/PipelineCycleRunner.kt	(no-coordinate)
1	app/src/main/kotlin/com/tideo/autobrightness/app/settings/AabSettings.kt	%AAB_AnimSteps,task570
1	app/src/main/kotlin/com/tideo/autobrightness/app/settings/AabSettings.kt	%AAB_ContextOverride
1	app/src/main/kotlin/com/tideo/autobrightness/app/settings/AabSettings.kt	%AAB_Debug
1	app/src/main/kotlin/com/tideo/autobrightness/app/settings/AabSettings.kt	%AAB_PanicPlugged
1	app/src/main/kotlin/com/tideo/autobrightness/app/settings/AabSettings.kt	%AAB_PanicSensitivity
1	app/src/main/kotlin/com/tideo/autobrightness/app/settings/AabSettings.kt	%AAB_Scale,task592
1	app/src/main/kotlin/com/tideo/autobrightness/app/settings/AabSettings.kt	%AAB_SetupTitle
1	app/src/main/kotlin/com/tideo/autobrightness/app/settings/AabSettings.kt	%AAB_ThreshMidpoint
1	app/src/main/kotlin/com/tideo/autobrightness/app/settings/AabSettings.kt	%AAB_Throttle,task570
1	app/src/main/kotlin/com/tideo/autobrightness/app/settings/AabSettingsMapper.kt	%AAB_Scale,%AAB_ScalingUse,act10,task661
1	app/src/main/kotlin/com/tideo/autobrightness/app/settings/AabSettingsMapper.kt	(no-coordinate)
1	app/src/main/kotlin/com/tideo/autobrightness/app/settings/SettingsValidator.kt	(no-coordinate)
1	app/src/main/kotlin/com/tideo/autobrightness/app/settings/TaskerLegacyProfileSerializer.kt	%AAB_DefaultThrottle,%AAB_Throttle
1	app/src/main/kotlin/com/tideo/autobrightness/app/state/DraftSettingsViewModel.kt	(no-coordinate)
1	app/src/main/kotlin/com/tideo/autobrightness/app/ui/components/SettingsControls.kt	(no-coordinate)
1	app/src/main/kotlin/com/tideo/autobrightness/app/ui/graph/DimmingChart.kt	(no-coordinate)
1	app/src/main/kotlin/com/tideo/autobrightness/app/ui/screens/CircadianScreen.kt	elements26
1	app/src/main/kotlin/com/tideo/autobrightness/app/ui/screens/CurveBrightnessScreen.kt	elements22
1	app/src/main/kotlin/com/tideo/autobrightness/app/ui/screens/MiscScreen.kt	(no-coordinate)
1	app/src/main/kotlin/com/tideo/autobrightness/app/ui/screens/MiscScreen.kt	elements20
1	app/src/main/kotlin/com/tideo/autobrightness/app/ui/screens/MiscScreen.kt	elements31
1	app/src/main/kotlin/com/tideo/autobrightness/app/ui/screens/MiscScreen.kt	elements4
1	app/src/main/kotlin/com/tideo/autobrightness/app/ui/screens/ProfilesScreen.kt	(no-coordinate)
1	app/src/main/kotlin/com/tideo/autobrightness/app/ui/screens/SuperDimmingScreen.kt	(no-coordinate)
1	app/src/main/kotlin/com/tideo/autobrightness/app/ui/screens/ToolsScreen.kt	%AAB_Test,act13,task38
1	app/src/main/kotlin/com/tideo/autobrightness/app/ui/screens/ToolsScreen.kt	task38,task655
2	domain/src/main/kotlin/com/tideo/autobrightness/domain/brightness/BrightnessEngine.kt	(no-coordinate)
2	domain/src/main/kotlin/com/tideo/autobrightness/domain/brightness/BrightnessEngine.kt	act10,task548,task661
1	domain/src/main/kotlin/com/tideo/autobrightness/domain/brightness/BrightnessEngine.kt	act16,task661
3	domain/src/main/kotlin/com/tideo/autobrightness/domain/brightness/BrightnessEngine.kt	act28,prof759,task544,task545
1	domain/src/main/kotlin/com/tideo/autobrightness/domain/brightness/BrightnessEngine.kt	task535
1	domain/src/main/kotlin/com/tideo/autobrightness/domain/brightness/BrightnessEngine.kt	task535,task544
1	domain/src/main/kotlin/com/tideo/autobrightness/domain/brightness/BrightnessEngine.kt	task543
3	domain/src/main/kotlin/com/tideo/autobrightness/domain/brightness/BrightnessEngine.kt	task546
1	domain/src/main/kotlin/com/tideo/autobrightness/domain/brightness/BrightnessEngine.kt	task548
1	domain/src/main/kotlin/com/tideo/autobrightness/domain/brightness/BrightnessEngine.kt	task661
1	domain/src/main/kotlin/com/tideo/autobrightness/domain/brightness/BrightnessPolicyInput.kt	%AAB_ScalingUse,act10,task548,task661
1	domain/src/main/kotlin/com/tideo/autobrightness/domain/brightness/BrightnessPolicyInput.kt	%AAB_ThreshMidpoint,act39,task570
1	domain/src/main/kotlin/com/tideo/autobrightness/domain/brightness/BrightnessPolicyInput.kt	act26,task570
1	domain/src/main/kotlin/com/tideo/autobrightness/domain/brightness/BrightnessPolicyInput.kt	prof759,task545
1	domain/src/main/kotlin/com/tideo/autobrightness/domain/circadian/SolarTimes.kt	(no-coordinate)
1	domain/src/main/kotlin/com/tideo/autobrightness/domain/wizard/CurveSuggestionEngine.kt	L649,task38
1	domain/src/main/kotlin/com/tideo/autobrightness/domain/wizard/CurveSuggestionEngine.kt	L659,task38
4	domain/src/test/kotlin/com/tideo/autobrightness/domain/brightness/ProvenanceTest.kt	(no-coordinate)
1	domain/src/test/kotlin/com/tideo/autobrightness/domain/reference/TaskerReference.kt	L40429,L41085,task90
1	platform/src/main/kotlin/com/tideo/autobrightness/platform/brightness/ScreenBrightnessController.kt	task554,task696
1	platform/src/main/kotlin/com/tideo/autobrightness/platform/brightness/SecureDimmingController.kt	task650
1	platform/src/main/kotlin/com/tideo/autobrightness/platform/context/BatteryStateReader.kt	prof763,task43
1	platform/src/main/kotlin/com/tideo/autobrightness/platform/context/ForegroundAppMonitor.kt	prof762,task43
1	platform/src/main/kotlin/com/tideo/autobrightness/platform/context/LocationReader.kt	prof765
1	platform/src/main/kotlin/com/tideo/autobrightness/platform/context/WifiInfoReader.kt	prof768,task43
1	platform/src/main/kotlin/com/tideo/autobrightness/platform/context/WifiSsidStrategies.kt	(no-coordinate)
1	platform/src/main/kotlin/com/tideo/autobrightness/platform/observe/BrightnessObserver.kt	prof755,task567
1	platform/src/main/kotlin/com/tideo/autobrightness/platform/privilege/PrivilegeManager.kt	task378
1	platform/src/main/kotlin/com/tideo/autobrightness/platform/sensor/LightSensorSource.kt	prof760
1	platform/src/test/kotlin/com/tideo/autobrightness/platform/context/WifiSsidStrategyTest.kt	(no-coordinate)
MANIFEST
)

tasker_provenance_records() { # <file>... -> "<path>\t<coordinates>" per marker line
	awk '
	match($0, /\/\/ Tasker/) {
		t = substr($0, RSTART)
		n = split(t, w, /[^A-Za-z0-9_%]+/)
		split("", seen); m = 0
		for (i = 1; i <= n; i++) {
			k = w[i]
			if (k ~ /^(task|prof|act|scene|elements)[0-9]+$/ || k ~ /^L[0-9]+$/ ||
			    k ~ /^%AAB_[A-Za-z_]+$/) {
				if (!(k in seen)) { seen[k] = 1; key[++m] = k }
			}
		}
		for (x = 1; x <= m; x++)
			for (y = x + 1; y <= m; y++)
				if (key[y] < key[x]) { tmp = key[x]; key[x] = key[y]; key[y] = tmp }
		s = ""
		for (x = 1; x <= m; x++) s = s (x == 1 ? "" : ",") key[x]
		printf "%s\t%s\n", FILENAME, (m ? s : "(no-coordinate)")
	}
	' "$@"
}

# Character-level Kotlin scan. Emits one record per file, with the PATH LAST:
#   COUNT <comment_lines> <total_lines> <file>
#   BLOCK <start_line> <run_length> <file>          (only for runs over the cap)
#
# The path goes last, and the numeric fields first, because a Kotlin path may legally contain a
# space. An earlier version emitted `COUNT <file> <n> <m>`, and every consumer read `$2` as the path
# and `$3` as a number — so one tracked file named `Parser Fixtures.kt` would have shifted every
# field, scored the file as zero comment lines against its module budget and printed nonsense line
# numbers in the block diagnostic. With the path last, each consumer takes the fixed count of
# leading numeric fields and treats the whole remainder as the path.
#
# State is carried ACROSS lines (block-comment depth, raw-string mode, template nesting), which is
# why this is one awk over the whole file rather than a per-line pattern.
scan_kotlin() { # <file>...
	awk -v cap="$COMMENT_BLOCK_MAX_LINES" '
	function reset() { depth = 0; raw = 0; str = ""; sp = 0; run = 0; gap = 0; runstart = 0; cl = 0; tl = 0 }
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
		# KDoc TAG lines (@param, @return, @throws, @see, @sample) count toward the module
		# budget but NOT toward the block cap, and the run they sit in is not broken by them.
		#
		# The cap exists to stop NARRATIVE, and a tag list is not narrative: its form is fixed,
		# one fact per line, and it grows with the signature rather than with how much an author
		# felt like writing. A seven-parameter function documented properly cannot fit a
		# `/** … */` into 12 lines, so counting tags made the honest KDoc the violation and
		# deleting the parameter docs the fix — the guard would have been driving out exactly the
		# documentation worth keeping. Found on the TaskerReference dim_progress oracle, where
		# the @params are the %AAB_ variable mapping.
		#
		# This is not a hole a narrative can hide in: prose does not start with @, and any line
		# that does not is still counted.
		#
		# The trailing boundary is spelled `[[:space:]]` rather than `\b`: gawk spells the word
		# boundary `\y` and mawk has none at all, so `\b` matched nothing here and the exemption
		# silently did not apply — a guard change that tests green because it never fired.
		if (hasc && !hascode && $0 ~ /^[[:space:]]*\*?[[:space:]]*@(param|return|throws|see|sample)[[:space:]]/) {
			cl++
			next
		}
		# A BLANK line does not end a comment block; it bridges it. Only CODE ends one.
		#
		# This is the difference between a cap and a formatting preference. With blank lines
		# ending the run, 36 lines of narrative written as three paragraphs separated by blank
		# lines passed at rc=0, and so did a SINGLE `/* … */` comment of 21 narrative lines that
		# happened to contain one empty line — one syntactic comment, obviously one block to any
		# reader, silently uncapped. That falsifies the claim this guard is built on ("narrative
		# does not fit in 12 lines, so it has to go to the .md"), and it was reachable by pressing
		# Enter. Found by the DA-005 rule-review pass, not by any test here.
		#
		# Bridging blank lines are counted into the run, because a paragraph break is part of the
		# narrative it separates. `gap` holds them until we know whether a comment or code comes
		# next; code discards them, so trailing blank lines never inflate a block.
		if (hasc && !hascode) {
			cl++
			if (run == 0) {
				runstart = FNR
			} else {
				run += gap
			}
			gap = 0
			run++
		} else if (!hasc && !hascode && run > 0) {
			gap++
		} else {
			if (run > cap) printf "BLOCK %d %d %s\n", runstart, run, file
			run = 0
			gap = 0
		}
	}
	function flush() {
		if (run > cap) printf "BLOCK %d %d %s\n", runstart, run, file
		if (file != "") printf "COUNT %d %d %s\n", cl, tl, file
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
	printf '%s\n' "$bad" | while read -r _ start run file; do
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
	printf '%s\n' "$bad" | while read -r _ start run file; do
		printf 'comment budget: %s:%s starts a %s-line comment block, over the %s-line cap.\n' \
			"$file" "$start" "$run" "$COMMENT_BLOCK_MAX_LINES" >&2
	done
	printf 'Durable prose belongs in the .md tier, not in the source: put the narrative in its ledger row (docs/LEDGER_B.md, append-only) or the matching docs/rebuild/ page, and leave a one-line `// D-NNN: <summary>` pointer here. A block this long is the thing the budget exists to stop.\n' >&2
	exit 1
fi

mode=check
if [ "${1:-}" = "--provenance-records" ]; then
	# Prints the tree's provenance manifest in the constant's own format, so regenerating it is a
	# copy-paste and the manifest can never be normalised differently from the checker.
	mode=records
elif [ "${1:-}" != "" ]; then
	# Exit 3, not 2. The ladder reads a WARN-less exit 2 from a repo-local guard as "broken
	# guard", which is true but tells the reader nothing; a distinct code keeps a mistyped
	# argument from being reported as a parse failure in this script.
	printf 'usage: %s [--hook | --file <path.kt> | --provenance-records]\n' "$0" >&2
	exit 3
fi

# --- whole-tree mode (the ladder) ------------------------------------------------------------
fails=0
report=$(mktemp)
trap 'rm -f "$report"' EXIT

# `git ls-files -z` and a NUL-delimited read, so a tracked path containing a space or a newline
# reaches the scanner intact. Read into an array rather than word-splitting a newline-joined list:
# the `IFS=$'\n'; set -f` idiom this replaced was already safe for spaces, but only until someone
# reached for `$(cat "$list")` again, and it could never have survived a newline in a path.
files=()
while IFS= read -r -d '' f; do files+=("$f"); done < <(git ls-files -z -- '*.kt' 2>/dev/null)
if [ "${#files[@]}" -eq 0 ]; then
	printf 'no Kotlin files tracked — this guard checked NOTHING, which is not a pass\n' >&2
	exit 1
fi
# --- records mode: print the current tree's provenance manifest (for regenerating the constant) --
if [ "$mode" = "records" ]; then
	tasker_provenance_records "${files[@]}" | sort | uniq -c |
		sed -e 's/^[[:space:]]*//' -e 's/^\([0-9][0-9]*\) /\1	/'
	exit 0
fi

# One awk over every file: the scan carries state across lines, and FNR==1 restarts it per file.
# xargs would split the list into batches on a large tree, which is harmless here only because
# each file is self-contained — but a single invocation keeps that from being a thing to know.
scan_kotlin "${files[@]}" >"$report"

over=$(awk '$1 == "BLOCK"' "$report")
if [ -n "$over" ]; then
	printf '%s\n' "$over" | while read -r _ start run file; do
		printf 'comment budget: %s:%s starts a %s-line comment block, over the %s-line cap — move the narrative to its ledger row or a docs/rebuild/ page and leave a `// D-NNN:` pointer\n' \
			"$file" "$start" "$run" "$COMMENT_BLOCK_MAX_LINES" >&2
	done
	fails=$((fails + 1))
fi

summary=''
for m in $MODULES; do
	eval "budget=\$BUDGET_$m"
	# The path is everything after the three leading fields, so a path containing a space is
	# still matched whole against the module prefix.
	got=$(awk -v m="$m" '$1 == "COUNT" {
		p = $0; sub(/^COUNT [0-9]+ [0-9]+ /, "", p)
		if (index(p, m "/") == 1) s += $2
	} END { print s + 0 }' "$report")
	if [ "$got" -gt "$budget" ]; then
		printf 'comment budget: %s holds %s comment lines, over its %s-line budget by %s. Two repairs, in this order: move durable prose to its ledger row or a docs/rebuild/ page and leave a `// D-NNN:` pointer — that is what the budget is for — or, if what pushed it over is genuinely new load-bearing documentation of new code, raise BUDGET_%s in this guard. Raising it is ALLOWED and is a rule change, not housekeeping: scripts/guards is in RULE_FILES, so the diff gets a rule review, and the review is where you say why this belongs in source rather than the .md tier.\n' \
			"$m" "$got" "$budget" "$((got - budget))" "$m" >&2
		fails=$((fails + 1))
	fi
	summary="$summary $m=$got/$budget"
done

# Provenance manifest. Every record the manifest names must still be present at least as often as
# it says; new records are free. See the manifest's header for why the key is the coordinate set
# and not the marker text.
recs=$(mktemp)
trap 'rm -f "$report" "$recs"' EXIT
tasker_provenance_records "${files[@]}" >"$recs"
prov_total=$(wc -l <"$recs" | tr -d '[:space:]')
lost=$(printf '%s\n' "$TASKER_PROVENANCE_MANIFEST" | awk -F'\t' '
	NR == FNR { if (NF == 3 && $1 != "") need[$2 FS $3] = $1 + 0; next }
	{ have[$1 FS $2]++ }
	END { for (k in need) if (have[k] + 0 < need[k]) printf "%d\t%d\t%s\n", need[k], have[k] + 0, k }
' - "$recs" | sort -t'	' -k3,3)
if [ -n "$lost" ]; then
	printf '%s\n' "$lost" | while IFS='	' read -r want got path coords; do
		printf 'comment budget: %s no longer carries its `// Tasker` provenance for %s (manifest requires %s, found %s).\n' \
			"$path" "$coords" "$want" "$got" >&2
	done
	printf 'These markers are the XML-to-Kotlin audit trail the constitution mandates, and this guard'"'"'s own downward pressure on comments is what puts them at risk — restore the coordinates rather than editing the manifest. Rewording a marker is fine and does not trip this: only DROPPING a task/act/prof/elements/L/%%AAB_ reference does. If ported logic was genuinely retired, regenerate the manifest with `scripts/guards/comment-budget.sh --provenance-records` and let the rule review see the removed line.\n' >&2
	fails=$((fails + 1))
fi

[ "$fails" = 0 ] || exit 1
prov_records=$(printf '%s\n' "$TASKER_PROVENANCE_MANIFEST" | awk -F'\t' 'NF == 3 && $1 != ""' | wc -l | tr -d '[:space:]')
printf 'comment budget:%s; no block over %s lines; %s Tasker provenance marker(s), all %s manifest record(s) intact\n' \
	"$summary" "$COMMENT_BLOCK_MAX_LINES" "$prov_total" "$prov_records"
