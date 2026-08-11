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

cb_git_tree() { # <dir> <budget> [tasker-floor, default 0]
	local d=$SANDBOX/$1
	rm -rf "$d"
	mkdir -p "$d/app"
	cb_guard "$1"
	# Lower the budget in the SANDBOX COPY so the arithmetic can be exercised without a
	# multi-thousand-line fixture. The real constants are not what is under test here.
	sed -i.bak "s/^BUDGET_app=.*/BUDGET_app=$2/" "$d/scripts/guards/comment-budget.sh"
	sed -i.bak "s/^TASKER_PROVENANCE_FLOOR=.*/TASKER_PROVENANCE_FLOOR=${3:-0}/" \
		"$d/scripts/guards/comment-budget.sh"
	rm -f "$d/scripts/guards/comment-budget.sh.bak"
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
if printf '%s' "$OUT" | grep -qF 'NOT to raise the number'; then
	ok
else
	bad "the budget diagnostic must name the remedy (move prose to the ledger), not just the violation: $OUT"
fi

# --- Tasker provenance floor -----------------------------------------------------------------
#
# This guard is the thing that endangers provenance markers: they are comments, so every downward
# pressure the budget applies falls on them too. The first consolidation pass deleted four despite
# an explicit instruction not to, which is why the floor exists and why it is fixtured.

cb_git_tree cb-prov-ok 100 2
{
	printf 'package x\n'
	printf '// Tasker: task535 "Lux Smoothing (Java)" XML L15204\n'
	printf 'val a = 1\n'
	printf '// Tasker: task661 act22\n'
	printf 'val b = 2\n'
} >"$SANDBOX/cb-prov-ok/app/A.kt"
git -C "$SANDBOX/cb-prov-ok" add -A
run_cb cb-prov-ok
expect_pass "a tree meeting the Tasker provenance floor passes"

cb_git_tree cb-prov-low 100 5
{
	printf 'package x\n'
	printf '// Tasker: task535 "Lux Smoothing (Java)" XML L15204\n'
	printf 'val a = 1\n'
} >"$SANDBOX/cb-prov-low/app/A.kt"
git -C "$SANDBOX/cb-prov-low" add -A
run_cb cb-prov-low
expect_fail "deleting Tasker provenance below the floor fails" "under the floor of 5"

# The remedy must point at restoring the markers, not at lowering the number — the floor is only
# worth having if the obvious way out is closed.
if printf '%s' "$OUT" | grep -qF 'restore them rather than lowering the floor'; then
	ok
else
	bad "the provenance diagnostic must tell the reader to restore the markers, not lower the floor: $OUT"
fi

# A tree with no Kotlin at all must not collect a green pass — it checked nothing.
cb_git_tree cb-empty 100
printf 'hello\n' >"$SANDBOX/cb-empty/app/notes.txt"
git -C "$SANDBOX/cb-empty" add -A
run_cb cb-empty
expect_fail "a tree with no tracked Kotlin fails rather than passing vacuously" "checked NOTHING"

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
