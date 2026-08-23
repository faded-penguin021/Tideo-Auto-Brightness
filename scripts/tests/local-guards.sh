#!/usr/bin/env bash
# Fixture suite for the repo-local ladder guards under scripts/guards/.
#
# Yours, not shipped. The AMH's own scripts/test-ladder-guards.sh covers the SHIPPED ladder;
# nothing upstream knows these guards exist, so without this file they are eight scripts whose
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

# A tree the guard's OTHER rows pass on, so each case isolates one fact. The version pair and
# the citation scan scope are seeded clean here and perturbed by the cases that test them.
doc_facts_baseline() { # <dir>
	local d=$SANDBOX/$1
	mkdir -p "$d"
	printf 'AMH_VERSION=4.1.0\n' >"$d/amh.conf"
	printf 'This constitution records **AMH 4.1.0**; `AMH_VERSION` in `amh.conf` is the authority.\n' \
		>"$d/AGENTS.md"
}

shizuku_tree() { # <dir> <consumer-count>
	local d=$SANDBOX/$1 i=0
	rm -rf "$d"
	mkdir -p "$d/platform/src/main" "$d/app/src/main"
	doc_facts_baseline "$1"
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

# Sub-item citation form (DB-022). The bare-suffixed spelling resolves to no ledger row under
# the whole-word matcher, so the shipped citation rung cannot see it — only this guard can.
shizuku_tree df-cite-ok 2
printf '// parity note, see D-042(c) and D-010(a)/(b)\n' >"$SANDBOX/df-cite-ok/app/src/main/Cite.kt"
run_guard df-cite-ok doc-facts
expect_pass "parenthesised sub-item citations pass"

shizuku_tree df-cite-bad 2
printf '// parity note, see D-042c\n' >"$SANDBOX/df-cite-bad/app/src/main/Cite.kt"
run_guard df-cite-bad doc-facts
expect_fail "a bare-suffixed sub-item citation fails" "Cite.kt"

# The exclusions must hold, or the guard fires on the synthetic ids its own sibling suites use.
shizuku_tree df-cite-excluded 2
mkdir -p "$SANDBOX/df-cite-excluded/scripts/tests"
printf '// fixture id D-999z\n' >"$SANDBOX/df-cite-excluded/scripts/tests/fixtures.sh"
printf '// fixture id D-998y\n' >"$SANDBOX/df-cite-excluded/scripts/test-ladder-guards.sh"
run_guard df-cite-excluded doc-facts
expect_pass "synthetic ids in the excluded fixture paths are not citations"

# Version pair (DB-019). amh.conf is the authority; the constitution must state the same number.
shizuku_tree df-ver-drift 2
printf 'AMH_VERSION=4.2.0\n' >"$SANDBOX/df-ver-drift/amh.conf"
run_guard df-ver-drift doc-facts
expect_fail "a constitution version behind amh.conf fails" "records AMH 4.1.0 but amh.conf sets AMH_VERSION=4.2.0"

# The absent-version case is the one that actually happened (DB-019): amh.conf said 3.0.0 and
# the constitution named no version at all, so nothing could be compared.
shizuku_tree df-ver-absent 2
printf '`AMH_VERSION` in `amh.conf` is the authority on which release.\n' >"$SANDBOX/df-ver-absent/AGENTS.md"
run_guard df-ver-absent doc-facts
expect_fail "a constitution stating no version fails rather than passing vacuously" "states no"

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

# The narrower vacuous pass: volumes EXIST, but one yields no parseable rows, so the loop that
# judges prefixes never runs for it. Before this case the guard counted volumes rather than
# rows and printed its strongest success line having read nothing. The bold shape below is the
# real one — every row in this repo carried it until they were normalized.
ledger_tree lp-unparseable
printf -- '- D-001: base row.\n' >"$SANDBOX/lp-unparseable/docs/LEDGER.md"
printf -- '- **DA-001**: a re-bolded row no parser reads.\n' >"$SANDBOX/lp-unparseable/docs/LEDGER_A.md"
run_guard lp-unparseable ledger-prefix
expect_fail "a volume yielding 0 parseable rows fails instead of passing on rows it never read" "0 parseable rows"

# The guard must read LEDGER_DIR from amh.conf, not from the environment: the ladder assigns it
# without exporting and runs guards in a child shell, so an inherited read silently pins the
# guard to its own defaults. Configure a non-default location and it must follow.
ledger_tree lp-configured
mkdir -p "$SANDBOX/lp-configured/memory"
printf 'LEDGER_DIR=memory\nLEDGER_BASENAME=LEDGER\n' >"$SANDBOX/lp-configured/amh.conf"
printf -- '- D-001: a row in the configured location.\n' >"$SANDBOX/lp-configured/memory/LEDGER.md"
run_guard lp-configured ledger-prefix
expect_pass "the guard follows LEDGER_DIR from amh.conf rather than its own default"

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
printf '· comment-budget\n'

# This guard counts Kotlin comment LINES, so every case here is really a question about the
# scanner: what it calls a comment. The negative cases below are the ones that matter — a scanner
# that greps for a leading slash passes the first two and gets every remaining one wrong, and a
# guard that miscounts is a guard the next session deletes rather than obeys.

cb_guard() { # <dir>  — copy the guard in, optionally with a lowered budget
	local d=$SANDBOX/$1
	mkdir -p "$d/scripts/guards"
	cp "$ROOT/scripts/guards/comment-budget.sh" "$d/scripts/guards/comment-budget.sh"
}

run_cb() { # <dir> <args...>
	local d=$SANDBOX/$1
	shift
	OUT=$( (cd "$d" && bash scripts/guards/comment-budget.sh "$@" 2>&1) )
	RC=$?
}

cb_tree() { # <dir>
	local d=$SANDBOX/$1
	rm -rf "$d"
	mkdir -p "$d/app"
	cb_guard "$1"
}

# --- block cap, via --file (needs no git) ---------------------------------------------------

cb_tree cb-short
printf 'package x\n// one\n// two\nval a = 1\n' >"$SANDBOX/cb-short/app/A.kt"
run_cb cb-short --file app/A.kt
expect_pass "a two-line comment block passes"

cb_tree cb-long
{
	printf 'package x\n'
	i=0
	while [ "$i" -lt 20 ]; do
		printf '// narrative line %s\n' "$i"
		i=$((i + 1))
	done
	printf 'val a = 1\n'
} >"$SANDBOX/cb-long/app/A.kt"
run_cb cb-long --file app/A.kt
expect_fail "a 20-line comment block fails" "over the 12-line cap"

# THE false positive that would make this guard untrustworthy. A raw string full of comment-shaped
# lines is a STRING — this is real in the tree (test fixtures embed Kotlin snippets), and a scanner
# that flags it teaches people the guard is broken.
cb_tree cb-rawstring
{
	printf 'package x\n'
	printf 'val fixture = """\n'
	i=0
	while [ "$i" -lt 20 ]; do
		printf '// this is string content, not a comment %s\n' "$i"
		i=$((i + 1))
	done
	printf '"""\n'
} >"$SANDBOX/cb-rawstring/app/A.kt"
run_cb cb-rawstring --file app/A.kt
expect_pass "20 comment-shaped lines inside a raw string pass — they are string content"

# The four-quote run. Kotlin closes a raw string at the LAST quote of the run, so `""""` is one
# content quote plus the terminator. Consuming a fixed three leaves a stray quote that opens a
# phantom string and silently swallows every comment after it — this regressed exactly once, in
# app/src/test/.../HardcodedStringCheckTest.kt, and the guard reported two fewer comment lines
# than the file had. A scanner with that bug passes every other case in this file.
cb_tree cb-quoterun
{
	printf 'package x\n'
	printf 'val re = Regex("""foo\\s*"""")\n'
	i=0
	while [ "$i" -lt 20 ]; do
		printf '// after the four-quote run, this IS a comment %s\n' "$i"
		i=$((i + 1))
	done
	printf 'val a = 1\n'
} >"$SANDBOX/cb-quoterun/app/A.kt"
run_cb cb-quoterun --file app/A.kt
expect_fail "comments after a four-quote raw-string terminator are still counted" "over the 12-line cap"

# Block comments nest in Kotlin, so the inner */ does not end the outer comment.
cb_tree cb-nested
{
	printf 'package x\n/*\n'
	i=0
	while [ "$i" -lt 8 ]; do
		printf ' * outer /* inner */ still outer %s\n' "$i"
		i=$((i + 1))
	done
	i=0
	while [ "$i" -lt 8 ]; do
		printf ' * more %s\n' "$i"
		i=$((i + 1))
	done
	printf '*/\nval a = 1\n'
} >"$SANDBOX/cb-nested/app/A.kt"
run_cb cb-nested --file app/A.kt
expect_fail "a nested block comment is counted as one long block" "over the 12-line cap"

# A trailing comment sits on a line that also carries code, so it is not a comment-only line and
# cannot start a block. 20 of them in a row must pass.
cb_tree cb-trailing
{
	printf 'package x\n'
	i=0
	while [ "$i" -lt 20 ]; do
		printf 'val v%s = %s // why this value\n' "$i" "$i"
		i=$((i + 1))
	done
} >"$SANDBOX/cb-trailing/app/A.kt"
run_cb cb-trailing --file app/A.kt
expect_pass "20 consecutive trailing comments pass — those lines carry code"

cb_tree cb-notkt
printf 'not kotlin\n' >"$SANDBOX/cb-notkt/app/A.txt"
run_cb cb-notkt --file app/A.txt
expect_pass "a non-Kotlin path is not this guard's business"

# --- hook mode ------------------------------------------------------------------------------

cb_hook() { # <dir> <payload>
	local d=$SANDBOX/$1
	OUT=$( (cd "$d" && printf '%s' "$2" | bash scripts/guards/comment-budget.sh --hook 2>&1) )
	RC=$?
}

cb_hook cb-long '{"tool_name":"Edit","tool_input":{"file_path":"app/A.kt"}}'
if [ "$RC" = 2 ] && printf '%s' "$OUT" | grep -qF 'over the 12-line cap'; then
	ok
else
	bad "hook mode must exit 2 with the diagnostic on stderr (Claude Code feeds stderr back only on 2); got rc=$RC: $OUT"
fi

# A hook that cannot read its payload must stay silent. Failing here would fire on every Edit in
# the session, and a hook that cries wolf on every edit is one the next session removes.
cb_hook cb-long 'this is not json'
expect_pass "hook mode on an unparseable payload exits 0 rather than false-failing every edit"

cb_hook cb-long '{"tool_input":{"file_path":"README.md"}}'
expect_pass "hook mode ignores a non-Kotlin file_path"

run_cb cb-long --nonsense
if [ "$RC" = 3 ]; then
	ok
else
	bad "a bad argument must exit 3, never 2 — the ladder reads a WARN-less 2 from a repo-local guard as a broken guard; got rc=$RC"
fi

# --- the KDoc-tag exemption, and its BOUNDARY -----------------------------------------------
#
# `@param`/`@return`/`@throws`/`@see`/`@sample` lines do not count toward the block cap, because a
# seven-parameter KDoc cannot fit in 12 lines and counting them made deleting the parameter docs
# the cheapest way to pass.
#
# The cases below exist to make WIDENING that set expensive. The exemption is the guard's only
# hole, and a hole nobody counts is one that grows an entry at a time — each addition individually
# reasonable, exactly like the comment bloat this guard exists to stop. `unknown-tag` is the
# load-bearing case: it fails the moment someone adds a sixth tag, so the widening cannot be
# silent. It cannot PREVENT a session from editing both this file and the guard — nothing can —
# but it forces the change to appear in the diff of two files that are both in RULE_FILES, where
# a reviewer can see it and ask why.
#
# If you are here because this case went red: the question to answer in the STATE entry is not
# "is this tag harmless" but "why does this narrative need to live in source rather than in the
# .md tier", which is the whole subject of DB-028.

cb_tagged_tree() { # <dir> <tag>
	cb_tree "$1"
	{
		printf 'package x\n/**\n * Summary line.\n'
		i=0
		while [ "$i" -lt 20 ]; do
			printf ' * %s p%s the parameter description\n' "$2" "$i"
			i=$((i + 1))
		done
		printf ' */\nfun f() {}\n'
	} >"$SANDBOX/$1/app/A.kt"
}

for tag in @param @return @throws @see @sample; do
	dir="cb-tag-$(printf '%s' "$tag" | tr -d '@')"
	cb_tagged_tree "$dir" "$tag"
	run_cb "$dir" --file app/A.kt
	expect_pass "20 $tag lines do not count toward the block cap"
done

# THE BOUNDARY. A tag outside the exempt set must still count, or the exemption is "any line
# starting with @" and the cap is decorative.
cb_tagged_tree cb-tag-unknown '@note'
run_cb cb-tag-unknown --file app/A.kt
expect_fail "a tag OUTSIDE the exempt set still counts" "over the 12-line cap"

# The behavioural case above only fixtures ONE tag, so it does not fire for the additions a Kotlin
# repo would actually reach for — the DA-005 pass widened the set to
# `@property|@constructor|@receiver` and every case here stayed green. Pin the literal instead:
# ANY change to the exempt set turns this red, whatever the new tag is.
expected_tags='@(param|return|throws|see|sample)[[:space:]]'
actual_tags=$(grep -oE '@\(param[^)]*\)\[\[:space:\]\]' "$ROOT/scripts/guards/comment-budget.sh" | head -1)
if [ "$actual_tags" = "$expected_tags" ]; then
	ok
else
	bad "the KDoc-tag exemption set changed (expected '$expected_tags', found '${actual_tags:-nothing}'). This is the guard's only hole; widening it needs a STATE entry answering why the narrative must live in source rather than the .md tier (DB-028), not just a passing suite."
fi

# ...and the pinned literal must actually be the one the scanner uses, or the pin guards a string
# nobody reads. Kotlin's own @property is the probe: it is NOT exempt today.
cb_tagged_tree cb-tag-property '@property'
run_cb cb-tag-property --file app/A.kt
expect_fail "@property is not exempt — the pinned set is the set in force" "over the 12-line cap"

# Prose is never exempt, however it is indented.
cb_tree cb-tag-prose
{
	printf 'package x\n/**\n'
	i=0
	while [ "$i" -lt 20 ]; do
		printf ' * narrative about @param and how it works %s\n' "$i"
		i=$((i + 1))
	done
	printf ' */\nfun f() {}\n'
} >"$SANDBOX/cb-tag-prose/app/A.kt"
run_cb cb-tag-prose --file app/A.kt
expect_fail "prose merely MENTIONING @param is not exempt" "over the 12-line cap"

# --- blank lines bridge a block; only CODE ends one ------------------------------------------
#
# Both cases below PASSED before the DA-005 review found them, and each defeats the guard's own
# premise ("narrative does not fit in 12 lines"). A paragraph break is not an escape hatch.

cb_tree cb-blank-blockcomment
{
	printf 'package x\n/*\n'
	i=0
	while [ "$i" -lt 10 ]; do printf ' * narrative %s\n' "$i"; i=$((i + 1)); done
	printf '\n'
	i=0
	while [ "$i" -lt 10 ]; do printf ' * more narrative %s\n' "$i"; i=$((i + 1)); done
	printf ' */\nval a = 1\n'
} >"$SANDBOX/cb-blank-blockcomment/app/A.kt"
run_cb cb-blank-blockcomment --file app/A.kt
expect_fail "ONE block comment containing a blank line is still one block" "over the 12-line cap"

cb_tree cb-blank-chunks
{
	printf 'package x\n'
	c=1
	while [ "$c" -lt 4 ]; do
		i=0
		while [ "$i" -lt 12 ]; do printf '// narrative %s-%s\n' "$c" "$i"; i=$((i + 1)); done
		printf '\n'
		c=$((c + 1))
	done
	printf 'val a = 1\n'
} >"$SANDBOX/cb-blank-chunks/app/A.kt"
run_cb cb-blank-chunks --file app/A.kt
expect_fail "narrative split into blank-separated chunks is still one block" "over the 12-line cap"

# The other direction: CODE between comments really does end a block, or every file with many
# short comments would fail and the guard would be deleted within the week.
cb_tree cb-blank-code
printf 'package x\n// one\nval a = 1\n\n// two\nval b = 2\n\n// three\nval c = 3\n' \
	>"$SANDBOX/cb-blank-code/app/A.kt"
run_cb cb-blank-code --file app/A.kt
expect_pass "comments separated by CODE are separate blocks"

# --- the guard must be EXECUTABLE ------------------------------------------------------------
#
# .claude/settings.json invokes it as a bare path with no interpreter, so a missing exec bit makes
# the PostToolUse hook exit 126 and the whole salience layer silently never fires — while the
# ladder and every case in this file stay green, because both run it as `bash <path>`. It shipped
# 100644 and the DA-005 pass caught it; nothing here could have. This case is the check.
if [ -x "$ROOT/scripts/guards/comment-budget.sh" ]; then
	ok
else
	bad "scripts/guards/comment-budget.sh is not executable — .claude/settings.json runs it as a bare path, so the PostToolUse hook exits 126 and never fires. chmod +x it (and commit the mode)."
fi

# --- module budget, via the whole-tree mode (needs a git tree) -------------------------------

cb_git_tree() { # <dir> <budget> [manifest-record...]
	local d=$SANDBOX/$1
	rm -rf "$d"
	mkdir -p "$d/app"
	cb_guard "$1"
	# Lower the budget in the SANDBOX COPY so the arithmetic can be exercised without a
	# multi-thousand-line fixture. The real constants are not what is under test here.
	#
	# The ceilings are COMPUTED from MEASURED_* and the margin, so the knob is the measurement, not
	# the ceiling. Setting the margin to 0 makes budget == MEASURED == $2 exactly, which is what
	# these cases assert against. An earlier version sed'd `^BUDGET_app=`, a line that stopped
	# existing when the budgets became computed — it matched nothing, every budget case silently ran
	# against the real 2403-line ceiling, and three of them passed for the wrong reason.
	sed -i.bak -e "s/^MEASURED_app=.*/MEASURED_app=$2/" \
		-e "s/^COMMENT_BUDGET_MARGIN_PCT=.*/COMMENT_BUDGET_MARGIN_PCT=0/" \
		"$d/scripts/guards/comment-budget.sh"
	rm -f "$d/scripts/guards/comment-budget.sh.bak"
	grep -q "^MEASURED_app=$2\$" "$d/scripts/guards/comment-budget.sh" ||
		bad "cb_git_tree could not set MEASURED_app in the sandbox copy — the budget cases would run against the real ceiling and pass vacuously"
	# Replace the shipped provenance manifest with the fixture's own records (tab-separated
	# `count<TAB>path<TAB>coordinates`, passed as $3...), or with an empty one when none are given.
	# Rewriting the whole heredoc rather than patching a number is the point: the manifest IS the
	# check now, so a fixture that exercises it has to state records the way the real one does.
	local g=$d/scripts/guards/comment-budget.sh
	local mf=$d/.fixture-manifest
	: >"$mf"
	local r
	for r in "${@:3}"; do printf '%s\n' "$r" >>"$mf"; done
	awk -v mf="$mf" '
		/^TASKER_PROVENANCE_MANIFEST=\$\($/ {
			print
			print "\tcat <<'"'"'MANIFEST'"'"'"
			while ((getline l < mf) > 0) print l
			print "MANIFEST"
			print ")"
			skip = 1
			next
		}
		skip && /^\)$/ { skip = 0; next }
		skip { next }
		{ print }
	' "$g" >"$g.new" && mv "$g.new" "$g"
	git -C "$d" init -q 2>/dev/null
	git -C "$d" config user.email t@example.com
	git -C "$d" config user.name t
}

cb_git_tree cb-budget-ok 100
printf 'package x\n// one\n// two\nval a = 1\n' >"$SANDBOX/cb-budget-ok/app/A.kt"
git -C "$SANDBOX/cb-budget-ok" add -A
run_cb cb-budget-ok
expect_pass "a module under its comment budget passes"

cb_git_tree cb-budget-over 3
{
	printf 'package x\n'
	i=0
	while [ "$i" -lt 4 ]; do
		printf '// c%s\nval v%s = %s\n' "$i" "$i" "$i"
		i=$((i + 1))
	done
} >"$SANDBOX/cb-budget-over/app/A.kt"
git -C "$SANDBOX/cb-budget-over" add -A
run_cb cb-budget-over
expect_fail "a module over its comment budget fails" "over its 3-line budget"

# The remedy has to be in the diagnostic. A guard that says only "no" gets its number raised.
if printf '%s' "$OUT" | grep -qF 'move durable prose to its ledger row'; then
	ok
else
	bad "the budget diagnostic must name the remedy (move prose to the ledger), not just the violation: $OUT"
fi

# ...and it must ALSO name the escape hatch, because the escape hatch is real. An earlier version
# said the fix was "NOT to raise the number here", while the guard's own header and
# docs/HARNESS_LOCAL.md both describe raising a constant as the intended reviewable adjustment.
# A diagnostic that forbids what the documentation permits either gets the documentation ignored or
# gets an honest change parked; either way the reader stops trusting the guard. Both repairs, in
# priority order, or this case is red.
if printf '%s' "$OUT" | grep -qF 'rule change, not housekeeping'; then
	ok
else
	bad "the budget diagnostic must also say that raising the budget is allowed and is a reviewed rule change — the header and HARNESS_LOCAL.md both say so, and a diagnostic that contradicts them is the enforcement asymmetry DA-005 hunts: $OUT"
fi

# --- Tasker provenance manifest ---------------------------------------------------------------
#
# This guard is the thing that endangers provenance markers: they are comments, so every downward
# pressure the budget applies falls on them too. The first consolidation pass deleted four despite
# an explicit instruction not to, which is why the floor exists and why it is fixtured.
#
# The first version of the check was a single tree-wide COUNT, and the cases below are mostly about
# why that was not enough. A count protects the population, never any individual marker, so the
# substitution case — delete one marker, add another elsewhere — passed it while an algorithm lost
# its audit trail. That is the shape a real maintenance change has, not an adversarial one.

# The baseline: a tree whose markers match its manifest.
cb_git_tree cb-prov-ok 100 \
	$'1\tapp/A.kt\tL15204' $'1\tapp/A.kt\ttask535' \
	$'1\tapp/A.kt\tact22' $'1\tapp/A.kt\ttask661'
{
	printf 'package x\n'
	printf '// Tasker: task535 "Lux Smoothing (Java)" XML L15204\n'
	printf 'val a = 1\n'
	printf '// Tasker: task661 act22\n'
	printf 'val b = 2\n'
} >"$SANDBOX/cb-prov-ok/app/A.kt"
git -C "$SANDBOX/cb-prov-ok" add -A
run_cb cb-prov-ok
expect_pass "a tree carrying every manifest provenance record passes"

cb_git_tree cb-prov-gone 100 \
	$'1\tapp/A.kt\tL15204' $'1\tapp/A.kt\ttask535' \
	$'1\tapp/A.kt\tact22' $'1\tapp/A.kt\ttask661'
{
	printf 'package x\n'
	printf '// Tasker: task535 "Lux Smoothing (Java)" XML L15204\n'
	printf 'val a = 1\n'
	printf 'val b = 2\n'
} >"$SANDBOX/cb-prov-gone/app/A.kt"
git -C "$SANDBOX/cb-prov-gone" add -A
run_cb cb-prov-gone
expect_fail "deleting a manifest provenance record fails" "no longer carries its"

# It must name WHICH record went, or the diagnostic sends the reader to diff 68 markers by hand.
# This is the whole reason the unit is a record and not a number.
if printf '%s' "$OUT" | grep -qE 'act22|task661'; then
	ok
else
	bad "the provenance diagnostic must name the coordinate of the record that went missing: $OUT"
fi

# THE CASE THE OLD COUNT COULD NOT SEE, and the reason this check was rewritten. One marker is
# deleted and an unrelated one is added, so the tree-wide total is unchanged at 2 — the count
# passed this and called the tree healthy.
cb_git_tree cb-prov-swap 100 \
	$'1\tapp/A.kt\tL15204' $'1\tapp/A.kt\ttask535' \
	$'1\tapp/A.kt\tact22' $'1\tapp/A.kt\ttask661'
{
	printf 'package x\n'
	printf '// Tasker: task535 "Lux Smoothing (Java)" XML L15204\n'
	printf 'val a = 1\n'
	printf '// Tasker: task999 act1 — newly ported logic, unrelated\n'
	printf 'val b = 2\n'
} >"$SANDBOX/cb-prov-swap/app/A.kt"
git -C "$SANDBOX/cb-prov-swap" add -A
run_cb cb-prov-swap
expect_fail "substituting one marker for another fails even though the total is unchanged" "task661"

# Relocation is substitution across files, and is the form a refactor actually takes: the marker
# still exists somewhere, so a tree-wide count is satisfied while the algorithm that was ported
# no longer says where it came from.
cb_git_tree cb-prov-moved 100 $'1\tapp/A.kt\tact22' $'1\tapp/A.kt\ttask661'
printf 'package x\nval a = 1\n' >"$SANDBOX/cb-prov-moved/app/A.kt"
printf 'package x\n// Tasker: task661 act22\nval b = 2\n' >"$SANDBOX/cb-prov-moved/app/B.kt"
git -C "$SANDBOX/cb-prov-moved" add -A
run_cb cb-prov-moved
expect_fail "a marker relocated to another file fails — the manifest keys on the file too" "app/A.kt"

# The other direction, and it is what makes the manifest livable. 22 of the 68 real markers were
# REWORDED by the consolidation this guard shipped with, every one keeping its task/act reference
# while shortening the prose. A manifest keyed on the marker TEXT would have gone red on all 22,
# and a rule that fires on every honest prose edit gets regenerated by reflex until it means
# nothing. Only a dropped COORDINATE is a finding.
cb_git_tree cb-prov-reworded 100 $'1\tapp/A.kt\tact22' $'1\tapp/A.kt\ttask661'
printf 'package x\n// Tasker task661 act22: a much shorter restatement.\nval a = 1\n' \
	>"$SANDBOX/cb-prov-reworded/app/A.kt"
git -C "$SANDBOX/cb-prov-reworded" add -A
run_cb cb-prov-reworded
expect_pass "rewording a marker while keeping its coordinates passes"

# New provenance needs no manifest edit. The manifest is a floor, not a whitelist — if adding
# newly-ported logic required a rule review, the guard would be taxing the thing it wants.
cb_git_tree cb-prov-added 100 $'1\tapp/A.kt\tact22' $'1\tapp/A.kt\ttask661'
{
	printf 'package x\n'
	printf '// Tasker: task661 act22\n'
	printf 'val a = 1\n'
	printf '// Tasker: task700 act5 — newly ported\n'
	printf 'val b = 2\n'
} >"$SANDBOX/cb-prov-added/app/A.kt"
git -C "$SANDBOX/cb-prov-added" add -A
run_cb cb-prov-added
expect_pass "adding new provenance needs no manifest change — the manifest is a floor, not a whitelist"

# ENRICHING a marker drops nothing and must pass. Keyed on the exact coordinate SET, it did not:
# `task661` → `task661 act22` destroyed the old key and the guard reported the file had lost its
# provenance. A guard that fails when you ADD a reference teaches people to stop adding references.
cb_git_tree cb-prov-enriched 100 $'1\tapp/A.kt\ttask661'
printf 'package x\n// Tasker: task661 act22 — now says which action too.\nval a = 1\n' \
	>"$SANDBOX/cb-prov-enriched/app/A.kt"
git -C "$SANDBOX/cb-prov-enriched" add -A
run_cb cb-prov-enriched
expect_pass "adding a coordinate to an existing marker passes — nothing was dropped"

# MERGING two markers that cite the same coordinates drops nothing either, and is exactly what the
# block cap and the budget push a maintainer toward. Under a per-record COUNT this failed with
# "manifest requires 2, found 1" — the provenance half firing on the consolidation the budget half
# demands.
cb_git_tree cb-prov-merged 100 $'1\tapp/A.kt\ttask546'
{
	printf 'package x\n'
	printf '// Tasker: task546 — one marker where there were two.\n'
	printf 'val a = 1\n'
	printf 'val b = 2\n'
} >"$SANDBOX/cb-prov-merged/app/A.kt"
git -C "$SANDBOX/cb-prov-merged" add -A
run_cb cb-prov-merged
expect_pass "merging two markers that cite the same coordinate passes — the reference survives"

# A manifest line that does not parse must FAIL, not be skipped. This was the cheapest bypass in
# the guard: delete a real marker, then convert that one manifest line's tabs to spaces, and the
# guard printed "all N record(s) intact" and exited 0 while N had quietly dropped by one. Reachable
# by accident too — any editor or heredoc re-indent that normalises tabs.
cb_git_tree cb-prov-unparsable 100 $'1\tapp/A.kt\ttask661' '1  app/A.kt  task535'
printf 'package x\n// Tasker: task661 act22\nval a = 1\n' >"$SANDBOX/cb-prov-unparsable/app/A.kt"
git -C "$SANDBOX/cb-prov-unparsable" add -A
run_cb cb-prov-unparsable
expect_fail "a manifest line with spaces instead of tabs fails rather than being skipped" "unparsable"

# The bypass end to end: the tampered line is the one covering the marker that was deleted. Under
# the old parse this was rc=0.
cb_git_tree cb-prov-bypass 100 $'1\tapp/A.kt\ttask661' '1  app/A.kt  task535'
printf 'package x\n// Tasker: task661 act22\nval a = 1\n' >"$SANDBOX/cb-prov-bypass/app/A.kt"
git -C "$SANDBOX/cb-prov-bypass" add -A
run_cb cb-prov-bypass
if [ "$RC" != 0 ]; then
	ok
else
	bad "deleting a marker and blanking its manifest line's tabs must not pass — that is the cheapest bypass in this guard: $OUT"
fi

# Coordinate-less markers carry nothing to key on, so their record is a per-file COUNT and that
# count is a real floor. This is the one place multiplicity still means something, and the headers
# say so rather than implying those records are protected like the rest.
cb_git_tree cb-prov-nocoord 100 $'2\tapp/A.kt\t(no-coordinate)'
{
	printf 'package x\n'
	printf '// Tasker parity note with no source coordinate at all.\n'
	printf 'val a = 1\n'
} >"$SANDBOX/cb-prov-nocoord/app/A.kt"
git -C "$SANDBOX/cb-prov-nocoord" add -A
run_cb cb-prov-nocoord
expect_fail "dropping one of two coordinate-less markers fails on the per-file count" "(no-coordinate)"

# ...and the same record is a FLOOR, not an equality. Gaining a coordinate-less marker must pass,
# or adding a parity note becomes a guard failure. Mutating the comparison from `<` to `!=` turned
# no case red until this one existed: every other provenance fixture has the tree exactly matching
# its manifest, so a floor and an exact match are indistinguishable across all of them.
cb_git_tree cb-prov-nocoord-extra 100 $'2\tapp/A.kt\t(no-coordinate)'
{
	printf 'package x\n'
	printf '// Tasker parity note, no coordinate.\n'
	printf 'val a = 1\n'
	printf '// Tasker another parity note, no coordinate.\n'
	printf 'val b = 2\n'
	printf '// Tasker a third, newly added.\n'
	printf 'val c = 3\n'
} >"$SANDBOX/cb-prov-nocoord-extra/app/A.kt"
git -C "$SANDBOX/cb-prov-nocoord-extra" add -A
run_cb cb-prov-nocoord-extra
expect_pass "gaining a coordinate-less marker passes — the record is a floor, not an equality"

# The remedy must point at restoring the markers, not at regenerating the manifest — the floor is
# only worth having if the obvious way out is closed.
run_cb cb-prov-gone
if printf '%s' "$OUT" | grep -qF 'restore the coordinates rather than editing the manifest'; then
	ok
else
	bad "the provenance diagnostic must tell the reader to restore the coordinates, not to regenerate the manifest: $OUT"
fi

# The manifest and the checker must share ONE normalisation, or the manifest pins records the
# checker can never match and the guard is permanently red or permanently vacuous. `--provenance-records`
# is the regeneration path the guard's header sends people to, so its output must be exactly what
# the manifest wants: feed a tree's own emitted records back in as its manifest and it must pass.
cb_git_tree cb-prov-roundtrip 100
{
	printf 'package x\n'
	printf '// Tasker: task535 "Lux Smoothing (Java)" XML L15204\n'
	printf 'val a = 1\n'
	printf '// Tasker: prof759/task545 proximity damp\n'
	printf 'val b = 2\n'
} >"$SANDBOX/cb-prov-roundtrip/app/A.kt"
git -C "$SANDBOX/cb-prov-roundtrip" add -A
run_cb cb-prov-roundtrip --provenance-records
# Count only manifest-SHAPED lines: the mode also prints a merge-base caveat on stderr, which
# run_cb folds into OUT, and counting raw lines made this case depend on the length of a warning.
prov_emitted=$(printf '%s\n' "$OUT" | grep -c '^[0-9][0-9]*	')
if [ "$RC" = 0 ] && [ "$prov_emitted" = 4 ]; then
	ok
else
	bad "--provenance-records must emit one manifest line per (file, coordinate) pair — 4 here: task535, L15204, prof759, task545 (rc=$RC): $OUT"
fi
# Feed those exact lines back as the manifest — verbatim, tabs and all — rather than retyping
# them, so this really is a round trip and not two hand-written strings that happen to agree.
prov_records=()
while IFS= read -r line; do
	case $line in
	[0-9]*"	"*) prov_records+=("$line") ;;
	esac
done <<EOF
$OUT
EOF
cb_git_tree cb-prov-roundtrip2 100 "${prov_records[@]}"
{
	printf 'package x\n'
	printf '// Tasker: task535 "Lux Smoothing (Java)" XML L15204\n'
	printf 'val a = 1\n'
	printf '// Tasker: prof759/task545 proximity damp\n'
	printf 'val b = 2\n'
} >"$SANDBOX/cb-prov-roundtrip2/app/A.kt"
git -C "$SANDBOX/cb-prov-roundtrip2" add -A
run_cb cb-prov-roundtrip2
expect_pass "records emitted by --provenance-records satisfy the manifest they are pasted into"

# --- paths containing a space -----------------------------------------------------------------
#
# The scanner serialises its findings as text, so the record format decides whether a path with a
# space survives the trip. It did not: the fields were `COUNT <file> <n> <m>`, and every consumer
# read `$2` as the path and `$3` as a number, so one tracked `Parser Fixtures.kt` shifted every
# field — the module sum scored the file as zero, and the block diagnostic printed a filename
# fragment where the line number belonged. Kotlin filenames with spaces are unconventional and
# perfectly legal, and this harness already treats whitespace-safe paths as a property worth having.
# Both halves are fixtured because they parse the record separately.

cb_git_tree cb-space-block 100
{
	printf 'package x\n'
	i=0
	while [ "$i" -lt 20 ]; do
		printf '// narrative line %s\n' "$i"
		i=$((i + 1))
	done
	printf 'val a = 1\n'
} >"$SANDBOX/cb-space-block/app/Parser Fixtures.kt"
git -C "$SANDBOX/cb-space-block" add -A
run_cb cb-space-block
expect_fail "the block cap sees a path containing a space" "over the 12-line cap"
# The diagnostic must carry the WHOLE path and a real line number, not the fragment before the
# space followed by the rest of the filename where the count belongs.
if printf '%s' "$OUT" | grep -qF 'app/Parser Fixtures.kt:2 starts a 20-line'; then
	ok
else
	bad "the block diagnostic mangled a path containing a space — expected 'app/Parser Fixtures.kt:2 starts a 20-line': $OUT"
fi

# The budget half reads the same record and has its own parse. With the fields shifted, this file's
# 20 comment lines summed as zero and the module passed a budget it was well over.
cb_git_tree cb-space-budget 3
{
	printf 'package x\n'
	i=0
	while [ "$i" -lt 4 ]; do
		printf '// c%s\nval v%s = %s\n' "$i" "$i" "$i"
		i=$((i + 1))
	done
} >"$SANDBOX/cb-space-budget/app/Parser Fixtures.kt"
git -C "$SANDBOX/cb-space-budget" add -A
run_cb cb-space-budget
expect_fail "the module budget counts a file whose path contains a space" "over its 3-line budget"

# ...and provenance, which reads the tree through the same file list.
cb_git_tree cb-space-prov 100 $'1\tapp/Parser Fixtures.kt\tact22,task661'
printf 'package x\nval a = 1\n' >"$SANDBOX/cb-space-prov/app/Parser Fixtures.kt"
git -C "$SANDBOX/cb-space-prov" add -A
run_cb cb-space-prov
expect_fail "the provenance manifest tracks a path containing a space" "app/Parser Fixtures.kt"

# A tree with no Kotlin at all must not collect a green pass — it checked nothing.
cb_git_tree cb-empty 100
printf 'hello\n' >"$SANDBOX/cb-empty/app/notes.txt"
git -C "$SANDBOX/cb-empty" add -A
run_cb cb-empty
expect_fail "a tree with no tracked Kotlin fails rather than passing vacuously" "checked NOTHING"

# =============================================================================
printf '\n· format-args\n'

# <dir> <strings-body> [kotlin-body] [second-kotlin-body]. The guard scans `git ls-files`, so each
# sandbox is a real repo with the sources staged — which is also what makes the untracked case below
# expressible.
fa_tree() {
	local d=$SANDBOX/$1
	mkdir -p "$d/app/src/main/res/values" "$d/app/src/main/kotlin"
	{
		printf '<resources>\n'
		printf '%s\n' "$2"
		printf '</resources>\n'
	} >"$d/app/src/main/res/values/strings.xml"
	[ -n "${3:-}" ] && printf '%s\n' "$3" >"$d/app/src/main/kotlin/Screen.kt"
	[ -n "${4:-}" ] && printf '%s\n' "$4" >"$d/app/src/main/kotlin/Other.kt"
	git -C "$d" init -q 2>/dev/null
	git -C "$d" add -A 2>/dev/null
}

fa_tree fa-ok '<string name="wait">up to %1$d seconds</string>' \
	'toast(R.string.wait, ACTIVE_FIX_SECONDS)'
run_guard fa-ok format-args
expect_pass "a formatted string toasted WITH an argument passes"

# THE case this guard exists for (DB-060): the string gained a specifier, one caller did not.
fa_tree fa-bare '<string name="wait">up to %1$d seconds</string>' \
	'toast(R.string.wait)'
run_guard fa-bare format-args
expect_fail "a formatted string toasted with no arguments fails" "NO format arguments"

# Precision sits BETWEEN the positional index and the conversion, so `%1$.4f` does not match
# `%[0-9]+\$[a-zA-Z]` — a first draft's regex missed it, and it is the form the sibling line at
# the DB-060 crash site uses, so the miss would have been invisible.
fa_tree fa-precision '<string name="fix">Location: %1$.4f, %2$.4f</string>' \
	'toast(R.string.fix)'
run_guard fa-precision format-args
expect_fail "a positional specifier carrying a precision is still a specifier" "NO format arguments"

# A `<string>` whose text wraps is invisible to a line-oriented XML parse, and that failure is
# SILENT and OPEN: the name never enters the formatted set, so every bare toast of it passes. An
# IDE reflow or a translation round-trip is all it takes, and this tree has long `%1$d` strings.
fa_tree fa-wrapped '<string name="stale">Sun position cached
        %1$d day(s) ago — turn Location on.</string>' \
	'toast(R.string.stale)'
run_guard fa-wrapped format-args
expect_fail "a <string> element wrapped across lines is still parsed" "NO format arguments"

# The id can be chosen by an expression. Both branches are checked, because a DB-057-shaped edit
# (add a specifier, update one branch) is exactly the incident again.
fa_tree fa-conditional '<string name="wait">up to %1$d seconds</string>
    <string name="off">Location is off</string>' \
	'toast(if (servicesOn) R.string.wait else R.string.off)'
run_guard fa-conditional format-args
expect_fail "an id chosen by a conditional is still checked" "NO format arguments"

fa_tree fa-mixed '<string name="close">Close</string>
    <string name="wait">up to %1$d seconds</string>' \
	'toast(R.string.close)'
run_guard fa-mixed format-args
expect_pass "an UNformatted string toasted bare is not a finding"

# THE false-positive class, and the reason `stringResource`/`getString` are not scanned:
# `Resources.getString(int)` does not format, so resolving a template and formatting it afterwards
# is correct code. A guard that failed the ladder here would be regenerated away by the next
# session, which is how a rule stops meaning anything.
fa_tree fa-template '<string name="wait">up to %1$d seconds</string>' \
	'val t = stringResource(R.string.wait)
val shown = t.format(seconds)'
run_guard fa-template format-args
expect_pass "resolving a template and formatting it afterwards is not a finding"

# `%%` is the escape for a literal percent. The body here is `%%d` ON PURPOSE: `100%% brightness`
# would pass whether or not the guard strips `%%`, because `% ` is not a specifier either way — an
# inert fixture that pins nothing. `%%d` is only harmless if the stripping actually happens.
fa_tree fa-escaped '<string name="pct">100%%d brightness</string>
    <string name="wait">up to %1$d seconds</string>' \
	'toast(R.string.pct)'
run_guard fa-escaped format-args
expect_pass "an escaped literal percent is not a format specifier"

# `formatted="false"` is Android's own opt-out for prose carrying a bare `%`. Same trap as above:
# the body must contain something the specifier regex WOULD match, or the case passes for the wrong
# reason and the opt-out branch is dead code wearing a fixture.
fa_tree fa-optout '<string name="spread" formatted="false">Use %d percent of the scale</string>
    <string name="wait">up to %1$d seconds</string>' \
	'toast(R.string.spread)'
run_guard fa-optout format-args
expect_pass 'a formatted="false" string carrying a bare percent is not a specifier'

# A second vararg resolver exists (ControlReceiver.flashDrop, DB-035) and takes the id as its THIRD
# argument, so a pattern anchored on "id is the first thing after the paren" cannot see it.
fa_tree fa-flashdrop '<string name="drop">Dropped %1$s</string>' \
	'flashDrop(ctx, level, R.string.drop)'
run_guard fa-flashdrop format-args
expect_fail "a resolver taking the id as a later argument is still checked" "NO format arguments"

# `f(id, emptyArray())` is zero format arguments too — the array spreads to nothing and String.format
# still runs. CircadianScreen passes its toasts exactly this way, so missing it would leave the
# DB-060 shape unguarded on the screen DB-057 edited.
fa_tree fa-emptyarray '<string name="wait">up to %1$d seconds</string>' \
	'toast(R.string.wait, emptyArray())'
run_guard fa-emptyarray format-args
expect_fail "an explicit emptyArray() spread is zero arguments" "NO format arguments"

# The counterpart risk: a pattern loose enough for the two cases above can run from one call's
# opening paren into a LATER call's id and invent a finding. Both ids here are formatted; the first
# is correctly supplied, the second is not passed to a resolver at all.
fa_tree fa-crosstalk '<string name="haz">Has %1$d args</string>
    <string name="innocent">Also %1$d here</string>' \
	'fun g() { toast(R.string.haz, n); foo(R.string.innocent) }'
run_guard fa-crosstalk format-args
expect_pass "the scan does not run across call boundaries on a shared line"

# `name` is not required to be the first attribute, and requiring it fails OPEN — the string never
# enters the formatted set, so every bare call on it passes.
fa_tree fa-attrorder '<string translatable="false" name="wait">up to %1$d seconds</string>' \
	'toast(R.string.wait)'
run_guard fa-attrorder format-args
expect_fail "a <string> whose name is not the first attribute is still parsed" "NO format arguments"

# A commented-out resource is not a resource. Fail-closed direction, but a guard that fires on dead
# XML is one the next session deletes.
fa_tree fa-xmlcomment '<!-- <string name="ghost">%1$d ghosts</string> -->
    <string name="wait">up to %1$d seconds</string>' \
	'toast(R.string.ghost)'
run_guard fa-xmlcomment format-args
expect_pass "a commented-out <string> is not classified as live"

# Flags sit between the % and the conversion. A first draft admitted only `-0-9.,`, so `%+.2f` — the
# natural spelling for a signed lux or brightness offset — was silently unformatted.
fa_tree fa-flags '<string name="off">Offset %+.2f stops</string>' \
	'toast(R.string.off)'
run_guard fa-flags format-args
expect_fail "a + flag between the %% and the conversion is still a specifier" "NO format arguments"

# More than one offending file: the loop must report every hit, not stop at the first.
fa_tree fa-multi '<string name="wait">up to %1$d seconds</string>' \
	'toast(R.string.wait)' \
	'toast(R.string.wait)'
run_guard fa-multi format-args
expect_fail "a second offending file is reported too" "Other.kt"

# DB-056 semantics: the guard reads tracked files, so an unstaged file is out of scope — the same
# contract AGENTS.md states for every other guard, and the reason `git add` comes before verifying.
fa_tree fa-untracked '<string name="wait">up to %1$d seconds</string>'
printf 'toast(R.string.wait)\n' >"$SANDBOX/fa-untracked/app/src/main/kotlin/Screen.kt"
run_guard fa-untracked format-args
expect_fail "an UNTRACKED offending file is not scanned, so the tree has no tracked Kotlin at all" "checked NOTHING"

fa_tree fa-plain '<string name="close">Close</string>' \
	'toast(R.string.close)'
run_guard fa-plain format-args
expect_fail "a strings.xml with no formatted string at all fails rather than passing vacuously" "checked NOTHING"

fa_tree fa-nokt '<string name="wait">up to %1$d seconds</string>'
run_guard fa-nokt format-args
expect_fail "a tree with no tracked Kotlin fails rather than passing vacuously" "checked NOTHING"

mkdir -p "$SANDBOX/fa-nostrings"
run_guard fa-nostrings format-args
expect_fail "a tree with no strings.xml fails rather than passing vacuously" "checked nothing"

# =============================================================================
# python-edit.sh — the inline-Python advisory (DB-062)
# =============================================================================
#
# This guard carries its OWN matcher matrix in its ladder mode, and that is where the shape
# coverage lives. What belongs here is the part its self-test cannot honestly check about itself:
# that the ladder mode really does go red when the matcher regresses (a self-test that reports its
# own health is worth nothing if a broken matcher still exits 0), and that the one-time arming
# behaves across separate PROCESSES rather than within one.
#
# Each case gets its own state path, because a shared one would make these cases order-dependent —
# and an order-dependent fixture is how a suite starts passing for the wrong reason.
pe_run() { # <state-file> <args...>; sets RC and OUT
	local state=$1
	shift
	OUT=$(PYTHON_EDIT_ADVISORY_STATE=$state bash "$ROOT/scripts/guards/python-edit.sh" "$@" 2>&1)
	RC=$?
}

# The ladder mode passes on the real guard, and its summary must not claim more than it verifies.
pe_run "$SANDBOX/pe-unused" # no args: fixture matrix
expect_pass "python-edit ladder mode passes"
if printf '%s' "$OUT" | grep -qF 'hook firing is not verifiable from here'; then
	ok
else
	bad "python-edit's summary drops the admission that it cannot verify the hook fires: $OUT"
fi

# A write shape is advised against ONCE, then never again — across processes, which is the
# property the /tmp marker exists for and the one a single-process self-test cannot demonstrate.
pe_state=$SANDBOX/pe-state-1
pe_run "$pe_state" --command "python3 -c \"open('a.kt','w').write('x')\""
expect_fail "the first inline-Python edit is advised against" "stopping this ONCE"
pe_run "$pe_state" --command "python3 -c \"open('a.kt','w').write('x')\""
expect_pass "the same command passes on a second, separate invocation"
pe_run "$pe_state" --command "python3 -c \"open('b.kt','w').write('y')\""
expect_pass "a DIFFERENT inline-Python edit also passes once the advisory is spent"

# Legitimate uses never arm it in the first place — verified by the state file still not
# existing, not merely by the exit code, since exit 0 is also what a spent advisory returns.
pe_state=$SANDBOX/pe-state-2
pe_run "$pe_state" --command "python3 -c 'print(open(\"f.kt\").read())'"
expect_pass "reading a file with Python is not advised against"
pe_run "$pe_state" --command './gradlew :app:testDebugUnitTest'
expect_pass "an unrelated command is not advised against"
if [ -e "$pe_state" ]; then
	bad "a read-only Python command consumed the one-time advisory — the next real edit would pass unadvised"
else
	ok
fi

# Hook mode: a PreToolUse payload blocks with exit 2, and anything unparseable fails OPEN. A rail
# that bricks every Bash command when a payload shape changes is one the next session deletes.
pe_state=$SANDBOX/pe-state-3
OUT=$(printf '%s' '{"tool_name":"Bash","tool_input":{"command":"python3 -c \"open(1,2).write(3)\""}}' |
	PYTHON_EDIT_ADVISORY_STATE=$pe_state bash "$ROOT/scripts/guards/python-edit.sh" --hook 2>&1)
RC=$?
expect_fail "hook mode blocks a matching payload" "BLOCKED ONCE"

pe_state=$SANDBOX/pe-state-4
OUT=$(printf 'not json at all' |
	PYTHON_EDIT_ADVISORY_STATE=$pe_state bash "$ROOT/scripts/guards/python-edit.sh" --hook 2>&1)
RC=$?
expect_pass "hook mode fails OPEN on an unparseable payload"

# DB-063: the payload carries a model-written `description` AFTER `command`. A greedy
# capture ran to the last quote on the line and scanned that text as if it were the command,
# so a description merely MENTIONING Python blocked an unrelated one. This is the
# false-positive shape that gets a rail deleted rather than fixed.
pe_state=$SANDBOX/pe-state-5
OUT=$(printf '%s' '{"tool_name":"Bash","tool_input":{"command":"git status","description":"python3 -c open(x,w).write(y) equivalent"}}' |
	PYTHON_EDIT_ADVISORY_STATE=$pe_state bash "$ROOT/scripts/guards/python-edit.sh" --hook 2>&1)
RC=$?
expect_pass "hook mode reads only the command field, not a sibling description"

# The escaped-quote half of F2, and it must be LOAD-BEARING (DB-064): the first version of
# this case asserted a command with no Python in it, so it passed under any extractor — including
# one returning nothing at all — and asserted nothing. The payload below carries escaped quotes
# BEFORE the Python, so a naive `[^"]*` capture stops at the first `\"` and yields `echo \`, which
# does not match and would let this through. Only an extractor that consumes escapes reaches the
# write and blocks. Mutate the extractor and this case goes red.
pe_state=$SANDBOX/pe-state-6
OUT=$(printf '%s' '{"tool_name":"Bash","tool_input":{"command":"echo \"x\" && python3 -c \"open(\\\"a.kt\\\",\\\"w\\\").write(1)\"","description":"x"}}' |
	PYTHON_EDIT_ADVISORY_STATE=$pe_state bash "$ROOT/scripts/guards/python-edit.sh" --hook 2>&1)
RC=$?
expect_fail "hook mode reads PAST escaped quotes to the inline edit behind them" "BLOCKED ONCE"

# F7 had no fixture of its own (DB-064): reverting `:-` to `+x` disarms the rail completely —
# an exported-but-empty override makes advisory_state_file print nothing and needs_advisory bail —
# and nothing went red. Exercising the fallback means letting the guard compute the REAL marker
# path, so the guard runs from a sandbox copy: its ROOT differs, so the /tmp marker it derives is
# its own and the live one is untouched.
pe_fallback_root=$SANDBOX/pe-fallback-root
mkdir -p "$pe_fallback_root/scripts/guards"
cp "$ROOT/scripts/guards/python-edit.sh" "$pe_fallback_root/scripts/guards/python-edit.sh"
pe_fallback_marker="/tmp/amh-python-edit-advisory-${UID:-unknown}-$(printf '%s' "${pe_fallback_root//\//_}" | tr ' ' '_')"
rm -f "$pe_fallback_marker"
pe_edit_cmd="python3 -c \"open('a.kt','w').write('x')\""
OUT=$(PYTHON_EDIT_ADVISORY_STATE= bash "$pe_fallback_root/scripts/guards/python-edit.sh" --command "$pe_edit_cmd" 2>&1)
RC=$?
expect_fail "an EMPTY state override falls back to the real marker instead of disarming" "stopping this ONCE"
if [ -e "$pe_fallback_marker" ]; then
	ok
else
	bad "the empty-override fallback did not create its marker — the advisory is not armed, it is off"
fi
OUT=$(PYTHON_EDIT_ADVISORY_STATE= bash "$pe_fallback_root/scripts/guards/python-edit.sh" --command "$pe_edit_cmd" 2>&1)
RC=$?
expect_pass "the fallback marker is then honoured on the next invocation"
rm -f "$pe_fallback_marker"

# THE case that decides whether the ladder mode is worth running: break the matcher in a copy and
# the guard must go RED. Without this, the self-test is a script that grades its own homework.
mkdir -p "$SANDBOX/pe-broken"
awk '{ print } /local cmd=\$1 src/ { print "\treturn 1" }' \
	"$ROOT/scripts/guards/python-edit.sh" >"$SANDBOX/pe-broken/python-edit.sh"
OUT=$(PYTHON_EDIT_ADVISORY_STATE=$SANDBOX/pe-broken-state bash "$SANDBOX/pe-broken/python-edit.sh" 2>&1)
RC=$?
expect_fail "a matcher with a write shape removed fails its own ladder rung" "fixture(s) failed"

# =============================================================================
printf '\n· action-pins\n'

SHA_A=3d3c42e5aac5ba805825da76410c181273ba90b1
SHA_B=fbc6f3992d24b796d5a048ff273f7fcc4a7b6c09

# <dir> then one "<file>|<uses-line-body>" per remaining argument.
ap_tree() {
	local d=$SANDBOX/$1
	shift
	rm -rf "$d"
	mkdir -p "$d/.github/workflows"
	local spec file body
	for spec in "$@"; do
		file=${spec%%|*}
		body=${spec#*|}
		if [ ! -f "$d/.github/workflows/$file" ]; then
			printf 'name: %s\njobs:\n  j:\n    runs-on: ubuntu-latest\n    steps:\n' \
				"$file" >"$d/.github/workflows/$file"
		fi
		printf '      - uses: %s\n' "$body" >>"$d/.github/workflows/$file"
	done
}

ap_tree ap-ok \
	"build.yml|actions/checkout@$SHA_A  # v7.0.1" \
	"codeql.yml|actions/checkout@$SHA_A  # v7.0.1" \
	"codeql.yml|github/codeql-action/init@$SHA_B  # v4.37.7" \
	"codeql.yml|github/codeql-action/analyze@$SHA_B  # v4.37.7"
run_guard ap-ok action-pins
expect_pass "consistent pins pass, and two sub-actions of one repo may share a SHA"

# THE case this guard exists for, reproduced from the defect itself: Dependabot moved
# clean-dist.yml's checkout SHA to v7.0.1's but its marker rewrite failed on the trailing prose,
# leaving `# v5.1.0` on the new commit while four other files said v7.0.1 (DB-038 decay, v1.9.1
# review). One SHA, two labels.
ap_tree ap-stale-marker \
	"build.yml|actions/checkout@$SHA_A  # v7.0.1" \
	"clean-dist.yml|actions/checkout@$SHA_A  # v5.1.0 - node24 runtime (see build.yml)"
run_guard ap-stale-marker action-pins
expect_fail "one SHA carrying two version markers fails" "labelled with more than one version"

# The mirror image: the marker agrees everywhere but one file kept the OLD commit, so a single
# release claims two commits. Rule 3a cannot see this one — the labels match.
ap_tree ap-split-sha \
	"build.yml|actions/checkout@$SHA_A  # v7.0.1" \
	"release.yml|actions/checkout@$SHA_B  # v7.0.1"
run_guard ap-split-sha action-pins
expect_fail "one action+version resolving to two commits fails" "more than one commit"

ap_tree ap-tag-ref "build.yml|actions/checkout@v7"
run_guard ap-tag-ref action-pins
expect_fail "a tag ref instead of a commit SHA fails" "not a 40-hex commit SHA"

ap_tree ap-unlabelled "build.yml|actions/checkout@$SHA_A"
run_guard ap-unlabelled action-pins
expect_fail "a SHA pin with no version marker fails" "no '# v<version>' marker"

# A path inside the repository and a container image are not third-party supply chain, but a tree
# holding ONLY those has nothing to say — so it must be loud, not a silent pass.
ap_tree ap-local-only "build.yml|./.github/actions/setup" "build.yml|docker://alpine:3.20"
run_guard ap-local-only action-pins
expect_fail "a tree with no third-party uses: is reported, not passed" "checked nothing"

rm -rf "$SANDBOX/ap-empty"
mkdir -p "$SANDBOX/ap-empty/.github/workflows"
run_guard ap-empty action-pins
expect_fail "no workflow files at all is reported, not passed" "checked nothing"

# Trailing prose after the version is legal — it is the shape Dependabot chokes on, not a defect
# in the file — so the guard must read the leading token as the marker and not the whole comment.
ap_tree ap-prose \
	"build.yml|actions/checkout@$SHA_A  # v7.0.1" \
	"clean-dist.yml|actions/checkout@$SHA_A  # v7.0.1 - node24 runtime (see build.yml node24 policy)"
run_guard ap-prose action-pins
expect_pass "a marker followed by prose is read as just the version"

# Every guard AND this suite must be executable. python-edit.sh needs it because the ladder runs
# it with bash but the PreToolUse hook does not; this suite needs it because .claude/settings.json
# pre-allows the bare `scripts/tests/local-guards.sh` spelling, while verify.sh runs it through
# `bash` — so a dropped mode bit breaks only the hand-run path and CI stays green (DB-064).
# A file mode is not part of any diff hunk, which is exactly why it needs a check rather than eyes.
pe_nonexec=""
for f in "$ROOT"/scripts/guards/*.sh "$ROOT"/scripts/tests/*.sh; do
	[ -x "$f" ] || pe_nonexec="$pe_nonexec ${f#"$ROOT"/}"
done
if [ -z "$pe_nonexec" ]; then
	ok
else
	bad "not executable:$pe_nonexec — a pre-allowed bare invocation of these exits 126, and running them through bash hides it"
fi

# =============================================================================
# The case count stated in docs/HARNESS_LOCAL.md must match the count actually run.
#
# Drift incident: the change that added the comment-budget cases updated that sentence to "42"
# while the suite ran 49, and nothing noticed — the number had been correct immediately before, so
# this is a fact that WAS true and a diff made false. The DA-005 reviewer found it by running the
# suite, which is the only way it could be found.
#
# It lives here rather than in doc-facts.sh because that guard is itself fixtured by this file:
# doc-facts.sh running this suite would re-enter doc-facts.sh in a sandbox, and the recursion would
# be bounded only by the sandbox happening to lack a copy of the suite. The count is known here for
# free, at the one moment it is authoritative.
#
# `+ 1` counts this case itself, which has not been tallied yet at the point of comparison.
stated=$(sed -n 's/.*fixture suite — \([0-9]\{1,\}\) cases.*/\1/p' "$ROOT/docs/HARNESS_LOCAL.md" | head -1)
if [ -z "$stated" ]; then
	bad "docs/HARNESS_LOCAL.md no longer states a fixture case count in the form 'fixture suite — N cases' — the sentence is the only place that number lives, so a reworded one drifts silently"
elif [ "$stated" != "$((CASES + 1))" ]; then
	bad "docs/HARNESS_LOCAL.md says $stated fixture cases; this suite runs $((CASES + 1)). Update the sentence in the same change that adds or removes a case."
else
	ok
fi

printf '\n'
if [ "$FAILS" -gt 0 ]; then
	printf 'repo-local guard fixtures: %d/%d case(s) FAILED\n' "$FAILS" "$CASES" >&2
	exit 1
fi
printf 'repo-local guard fixtures: %d cases pass\n' "$CASES"
