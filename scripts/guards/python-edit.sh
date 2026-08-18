#!/usr/bin/env bash
# Repo-local PRE-EXECUTION advisory — inline Python used to edit files (DB-062).
#
# The harness supplies Write, Edit and MultiEdit. Those render a reviewable diff, and the owner
# sees WHAT changed as it changes. A `python3 - <<EOF` heredoc that rewrites the same file is
# opaque while it runs: the transcript shows a blob of Python, the tool result shows nothing, and
# whether the edit did what it claimed is only knowable afterwards by reading the file back. This
# advisory makes the agent stop once and reconsider before reaching for the interpreter.
#
# IT BLOCKS EXACTLY ONCE, then stays out of the way. Same mechanism and same rationale as the
# shipped command guard's `.env` and destructive-command advisories: a marker file per repo and
# uid under /tmp. Once per MARKER LIFETIME, not once per session — the marker name has no session
# component, so in a long-lived container the advisory is spent by the first session that trips it
# and every later session passes unadvised (DB-063 F3). Delete the marker to re-arm. In a container
# that starts with a clean /tmp the two coincide, which is what made the looser claim look true.
# That is deliberate, and it is the whole design — this is a speed bump
# aimed at the reflex, not a permission rail. Scripted bulk edits are a legitimate tool and the
# session that genuinely needs one gets it by re-running the command.
#
# WHAT IT MATCHES: a Python invocation carrying INLINE source (`-c`, or a heredoc / pipe into
# `python -`) whose source text contains a file-write operation. All three must hold.
#
# WHAT IT DOES NOT MATCH, stated plainly because a coverage claim nobody checks is worse than
# no claim:
#   * `python script.py`, and equally `cat edit.py | python3` — the source is a file, not the
#     command text. A checked-in script is reviewable on its own terms, which is the property
#     this advisory is protecting; it is also invisible to a matcher that only sees the command
#     line. Writing the script and then running it therefore passes, and that is the intended
#     shape for an edit big enough to want a script.
#   * `sed -i`, `perl -pi -e`, `ed`, `awk` into a temp file, a shell redirection that clobbers a
#     source file. Same opacity, same objection — the owner asked about Python, and widening a
#     rule past its request is how rules stop being read. Widening it is a rule change, so it
#     goes through the DA-005 review like this one did.
#   * Anything constructed at runtime — `eval`, base64, a command assembled from variables. Text
#     at scan time is not code, and the shipped command guard's header makes the same admission.
#   * The SECOND and later inline-Python edit per marker lifetime. By design, see above.
#   * Pure filesystem moves — `os.makedirs`, `os.rename`, `os.remove`, `shutil.move`. They were
#     matched at first and should not have been (DB-063 F4): the remedy this advisory offers is
#     `Edit`/`Write`, and the harness has no tool that makes a directory or deletes a file, so it
#     blocked and then named a fix nobody could take. Destructive shell work is the shipped
#     command guard's advisory, not this one. `shutil.copyfile`/`copytree` stay: they overwrite
#     file CONTENT, which is the thing being protected.
# So: this is an advisory that catches the common shape once. It is not a containment boundary,
# and nothing here should be read as one.
#
# Matching is deliberately COARSE — the whole command text, not a parse. A commit message quoting
# `python -c "open('f','w')"` trips it. That is an accepted cost at a one-time advisory whose
# remedy is running the command again, and it is the same trade the shipped `.env` advisory makes
# with `case $cmd in *.env*`. A precise matcher here would be a Python parser in bash.
#
# Modes:
#   python-edit.sh                 fixture matrix (the ladder rung — a matcher nobody tests is
#                                  a matcher that quietly stops matching)
#   python-edit.sh --hook          read a PreToolUse payload (JSON) on stdin; exit 2 to block
#   python-edit.sh --command 'CMD' check one command; exit 2 if it would advise
#
# Fails OPEN on anything it cannot parse, like every other pre-execution rail here: a guard that
# bricks unrelated commands is one the next session deletes rather than fixes.
#
# Tier: the LADDER mode fails closed (a broken matcher is a broken guard). The HOOK mode blocks
# once and then passes forever, which is not a tier so much as a nudge. Deliberate.
set -uo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"

ADVICE='This command uses inline Python to edit a file. The harness supplies Write, Edit and MultiEdit, which show the change as a reviewable diff; an interpreter heredoc is opaque while it runs, so a mistake in it is only visible afterwards by reading the file back. Prefer Edit for a targeted replacement or Write for a whole file. The guard is stopping this ONCE so you can reconsider — if a scripted bulk edit is genuinely the right tool here, run the same command again and it will proceed; this advisory does not rearm until the marker under /tmp is cleared.'

# --- the matcher ------------------------------------------------------------------------------

# True (0) if the command invokes Python with inline source that writes to a file.
is_python_inline_edit() { # <command>
	local cmd=$1 src

	# 1. A Python invocation at all. Word-boundary matched so `pythonic-notes.md` is prose. `/` is
	#    NOT a boundary character: excluding it let `/usr/bin/python3` and `.venv/bin/python`
	#    through, which is the first spelling anyone reaches for (DB-063 F1).
	printf '%s' "$cmd" | grep -Eq '(^|[^[:alnum:]_.-])(python|python3|python2|py)([[:space:]]|$)' || return 1

	# 2. Inline source: `-c`, or a heredoc / pipe feeding the interpreter on stdin. Without one
	#    of these the source lives in a file this matcher cannot see, and (see header) a
	#    checked-in script is not what this advisory is aimed at.
	#    Interpreter flags may sit between the interpreter and `-c` (`-u`, `-B`, `-X utf8`), so the
	#    inline-source token is not required to be adjacent (DB-063 F1).
	printf '%s' "$cmd" | grep -Eq -- '(python|python3|python2|py)[[:space:]]+((-[[:alnum:]]+[[:space:]]+([[:alnum:]=.]+[[:space:]]+)?)*(-[[:alnum:]]*c|-[[:space:]]*$|-[[:space:]])|.*<<)' ||
		printf '%s' "$cmd" | grep -Eq -- '\|[[:space:]]*(python|python3|python2|py)([[:space:]]|$)' || return 1

	# 3. A write in the source text. Standard-stream writes are how a legitimate read-and-report
	#    one-liner prints, so they are removed before the test rather than special-cased inside
	#    it — `sys.stdout.write` must not be the thing that trips a guard about editing files.
	src=$(printf '%s' "$cmd" | sed -e 's/sys\.stdout\.write(//g' -e 's/sys\.stderr\.write(//g' -e 's/stdout\.write(//g' -e 's/stderr\.write(//g')

	printf '%s' "$src" | grep -Eq \
		-e "open\([^)]*,[[:space:]]*['\"][^'\"]*[wax+]" \
		-e '\.write\(' \
		-e '\.writelines\(' \
		-e '\.write_text\(' \
		-e '\.write_bytes\(' \
		-e '\.truncate\(' \
		-e 'shutil\.(copyfile|copytree)' \
		-e 'inplace[[:space:]]*=[[:space:]]*True' \
		-e '(json|yaml|toml)\.dump\(' \
		-e 'Path\([^)]*\)\.write' ||
		return 1

	return 0
}

# --- one-time state ---------------------------------------------------------------------------
#
# Mirrors the shipped command guard's advisory_state_file: per repo, per uid, under /tmp, so it
# is per-session in any container that starts with a clean /tmp. PYTHON_EDIT_ADVISORY_STATE
# overrides it, which is how the fixture suite exercises both the armed and disarmed paths
# without touching the real marker.
advisory_state_file() {
	local slug uid
	# DB-063 F7: `:-` not `+x`, so an exported-but-EMPTY override falls back to the real marker
	# instead of returning "" and disarming the rail silently.
	[ -n "${PYTHON_EDIT_ADVISORY_STATE:-}" ] && { printf '%s' "$PYTHON_EDIT_ADVISORY_STATE"; return 0; }
	slug=${ROOT//\//_}
	slug=${slug// /_}
	uid=${UID:-unknown}
	printf '/tmp/amh-python-edit-advisory-%s-%s' "$uid" "$slug"
}

# True (0) if this command should be advised against right now: it matches AND the advisory is
# still armed. Arming is consumed here, so a second call passes even for the same command.
needs_advisory() { # <command>
	local state
	is_python_inline_edit "$1" || return 1
	state=$(advisory_state_file)
	[ -n "$state" ] || return 1
	[ -e "$state" ] && return 1
	: >"$state" 2>/dev/null || return 1
	return 0
}

# --- hook mode --------------------------------------------------------------------------------

extract_command() { # fail open: print nothing if the payload is not what we expect
	# No python3 here on purpose — a guard about reaching for Python should not need it to run,
	# and the sed path is the one the shipped guard already falls back to.
	#
	# DB-063 F2: the capture must stop at the END OF THE STRING, not the last quote on the line.
	# A greedy `\(.*\)"` swallowed every sibling field, so the model-written `description` that
	# follows `command` in the Bash payload was scanned as if it were the command — and a
	# description merely MENTIONING Python blocked an unrelated command. `[^"\\]*(\\.[^"\\]*)*`
	# consumes escaped quotes without running past the closing one.
	printf '%s' "$1" |
		sed -n 's/.*"command"[[:space:]]*:[[:space:]]*"\([^"\\]*\(\\.[^"\\]*\)*\)".*/\1/p' |
		head -1
}

if [ "${1:-}" = "--hook" ]; then
	payload=$(cat) || exit 0
	cmd=$(extract_command "$payload")
	[ -n "$cmd" ] || exit 0
	if needs_advisory "$cmd"; then
		printf 'BLOCKED ONCE by the repo-local inline-Python advisory (DB-062).\n\n%s\n' "$ADVICE" >&2
		exit 2
	fi
	exit 0
fi

if [ "${1:-}" = "--command" ]; then
	cmd=${2:-}
	if needs_advisory "$cmd"; then
		printf '%s\n' "$ADVICE" >&2
		exit 2
	fi
	exit 0
fi

if [ -n "${1:-}" ]; then
	printf 'usage: %s [--hook | --command <CMD>]\n' "$0" >&2
	exit 3
fi

# --- ladder mode: the fixture matrix ----------------------------------------------------------
#
# The hook cannot be verified from inside the repo — nothing here can prove Claude Code still
# fires it. What CAN be verified is that the matcher still recognises what it claims to, so that
# is what the ladder rung does. Arming is bypassed with a throwaway state path per case, because
# these cases test the MATCHER, not the one-time bookkeeping (which gets its own cases below).
SELF_FAILS=0
MATCHED=0
PASSED=0
matcher_says_yes() { # <label> <command>
	MATCHED=$((MATCHED + 1))
	if ! is_python_inline_edit "$2"; then
		printf 'python-edit: matcher MISSED %s: %s\n' "$1" "$2" >&2
		SELF_FAILS=$((SELF_FAILS + 1))
	fi
}
matcher_says_no() { # <label> <command>
	PASSED=$((PASSED + 1))
	if is_python_inline_edit "$2"; then
		printf 'python-edit: matcher FALSE POSITIVE on %s: %s\n' "$1" "$2" >&2
		SELF_FAILS=$((SELF_FAILS + 1))
	fi
}

# Shapes that ARE an inline Python file edit.
matcher_says_yes 'heredoc rewrite' "python3 - <<'EOF'
s = open('f.kt').read()
open('f.kt','w').write(s)
EOF"
matcher_says_yes 'dash-c open-for-write' "python3 -c \"open('a.txt','w').write('x')\""
matcher_says_yes 'pathlib write_text' "python3 -c 'from pathlib import Path; Path(\"a\").write_text(\"b\")'"
matcher_says_yes 'fileinput inplace' "python -c 'import fileinput
for l in fileinput.input(\"f\", inplace=True): print(l)'"
matcher_says_no 'shutil move is a filesystem op, not an edit (DB-063 F4)' "python3 -c 'import shutil; shutil.move(\"a\",\"b\")'"
matcher_says_no 'os.replace is a filesystem op, not an edit (DB-063 F4)' "python3 -c 'import os; os.replace(\"a\",\"b\")'"
matcher_says_yes 'piped inline source' "echo \"open('a','w').write('x')\" | python3"
matcher_says_yes 'json dump' "python3 -c 'import json; json.dump(d, open(\"f\",\"w\"))'"

# DB-063 F1: the two spellings the first matcher missed. An absolute path and an
# interpreter flag before -c are what a real session types, so a guard blind to them was
# advisory theatre against everything but the textbook form.
matcher_says_yes 'absolute interpreter path' "/usr/bin/python3 -c \"open('a','w').write('x')\""
matcher_says_yes 'venv-relative interpreter' ".venv/bin/python -c \"open('a','w').write('x')\""
matcher_says_yes 'flag before -c' "python3 -u -c \"open('a','w').write('x')\""
matcher_says_yes 'valued flag before -c' "python3 -X utf8 -c \"open('a','w').write('x')\""

# Shapes that are NOT, and must stay quiet. These are the cases that decide whether the guard
# survives contact with a real session.
matcher_says_no 'read and print' "python3 -c 'print(open(\"f.kt\").read().count(chr(10)))'"
matcher_says_no 'stdout write' "python3 -c 'import sys; sys.stdout.write(\"hi\")'"
matcher_says_no 'json read' "python3 -c 'import json,sys; print(json.load(sys.stdin)[\"k\"])'"
matcher_says_no 'a script file' 'python3 scripts/fdroid-check.py'
matcher_says_no 'a script file piped in' 'cat edit.py | python3'
matcher_says_no 'module run' 'python3 -m pytest -q'
matcher_says_no 'version' 'python3 --version'
matcher_says_no 'no python at all' "sed -i 's/a/b/' f.kt"
matcher_says_no 'prose mentioning python' 'git commit -m "port the python helper to Kotlin"'
matcher_says_no 'gradle' './gradlew :app:testDebugUnitTest'

# The one-time bookkeeping: armed once, then silent — the property the whole design rests on.
tmp_state=$(mktemp -u)
PYTHON_EDIT_ADVISORY_STATE=$tmp_state
edit_cmd="python3 -c \"open('a','w').write('x')\""
needs_advisory "$edit_cmd" || {
	printf 'python-edit: first matching command was NOT advised against\n' >&2
	SELF_FAILS=$((SELF_FAILS + 1))
}
if needs_advisory "$edit_cmd"; then
	printf 'python-edit: advisory rearmed on the second call — it must block exactly once\n' >&2
	SELF_FAILS=$((SELF_FAILS + 1))
fi
if needs_advisory "python3 -c \"open('b','w').write('y')\""; then
	printf 'python-edit: advisory fired for a second, different command — one block per session\n' >&2
	SELF_FAILS=$((SELF_FAILS + 1))
fi
rm -f "$tmp_state"
unset PYTHON_EDIT_ADVISORY_STATE

if [ "$SELF_FAILS" -gt 0 ]; then
	printf 'python-edit.sh: %d matcher fixture(s) failed\n' "$SELF_FAILS" >&2
	exit 1
fi

# DB-063 F6: counted, not typed — a hardcoded summary drifts the first time a fixture is added.
printf '%s edit shape(s) matched, %s legitimate use(s) passed, one-time arming verified; pre-execution advisory (hook firing is not verifiable from here)\n' "$MATCHED" "$PASSED"
exit 0
