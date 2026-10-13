#!/usr/bin/env bash
# AMH — post-execution tool-output redaction (P17), for agents that can replace or suppress
# a tool result before the context window sees it.
#
# Reads one Claude Code or Codex PostToolUse hook payload (JSON) on stdin. Walks the STRING
# leaves of the payload's `tool_response`, runs each through `redact.sh` beside this script,
# and — only if a leaf actually gained a `[REDACTED:` marker — prints a host-specific hook
# response that keeps the original result from the agent's context:
#
# Claude Code can replace a result in place:
#   {"hookSpecificOutput":{"hookEventName":"PostToolUse","updatedToolOutput": <same shape>}}
# Codex cannot currently rewrite a result. When redaction changed a leaf, its equivalent
# safe direction is to block delivery of the original and return the filtered result as the
# hook's model-facing reason:
#   {"decision":"block","reason":"<filtered result>"}
#
# Usage:
#   redact-tool-output.sh              hook mode: payload on stdin, response on stdout
#   redact-tool-output.sh --self-test  fixture matrix (tokens generated at runtime)
#
# Shipped by the Agentic Maintenance Harness. Repo-agnostic: do not edit locally.
#
# --- WHAT THIS IS, AND WHAT IT IS NOT ---------------------------------------------------
#
# It narrows a window. It is not containment, and four bounds say why:
#
# 1. The value was already produced. The command ran, the file was read, and the bytes exist
#    in the tool's own execution, in the session transcript on disk, and in whatever
#    telemetry the host keeps. This layer changes what the MODEL reads, nothing else.
# 2. Both host contracts fail OPEN on hook failure. Claude also uses the ORIGINAL output when
#    a replacement misses the tool's response schema; Codex uses it when no blocking response
#    is produced or honored. Every uncertain path here therefore prints nothing and exits 0,
#    which lands in exactly the same place: the unmodified result.
# 3. It sees successful tool calls only, per the host's PostToolUse contract. The
#    credential printed by a command that FAILED is the common leak and may never reach
#    this script at all. Prevention stays with the pre-execution command guard.
# 4. It catches the shapes `redact.sh` enumerates and no more. A private token with no
#    recognisable prefix, a random password, a database credential: this sees none of them.
#    Deliberately NOT a second vocabulary — one filter, one class list, one place to fix.
#
# The rule ("never print a credential's value, prefix, suffix, length or hash") and the
# permission deny rails are the other two layers, and they are the ones that hold.
#
# --- WHY THE LEAVES, AND NOT THE SERIALISED JSON ----------------------------------------
#
# Filtering the serialised `tool_response` text would be one line and would be WRONG, in a
# way that reads as handled:
#
#   `redact.sh` redacts a private key in two stages. A line-range stage replaces the
#   base64 BODY between the BEGIN and END markers; a per-line stage then replaces the
#   BEGIN marker itself. In serialised JSON the whole key sits inside one string literal
#   with its newlines written as two-character `\n` escapes, so there are no lines for the
#   range stage to open on — and the marker stage still fires. The result is
#   `[REDACTED:private_key_block]` printed directly above the key, in the clear.
#
# A marker over a live value is worse than no class at all, because it reads as handled
# (P17 says this about the same filter). So the leaf is DECODED to real text first, which
# is what `redact.sh` was written against, and re-encoded afterwards by the JSON writer.
#
# Structure is preserved by construction rather than by hope: the parsed object is rebuilt
# with string leaves replaced in place. Numbers stay numbers, booleans stay booleans, null
# stays null, array lengths and object keys are untouched. Nothing here reconstructs what a
# tool's response "should" look like — this script does not know, and a guess that misses
# the schema is silently discarded by the host (bound 2 above).
#
# OBJECT KEYS ARE NOT FILTERED. A key is a field name in the tool's schema; rewriting one
# renames a field, which is a different result rather than a redacted one. A credential
# used as a key survives this layer, and the rule is what covers it.
#
# --- INTERPRETER ------------------------------------------------------------------------
#
# A Python 3 that RUNS when there is one, and NOTHING when there is not — optional for the reason
# `command-guard.sh` also treats it as optional when reading a hook payload: the harness targets
# bash, git and coreutils, and a JSON reader is not in that floor. (That script has a narrow
# bash fallback for its one flat string, and falls back to it when its `python3` does not run;
# this one has no fallback and stands down — see the last paragraph of this section.) `python3` is
# tried first and `python` second, each accepted only if it starts and reports Python 3: see
# the resolver below for why finding the name is not enough. Absent a working one,
# this prints nothing and the agent sees the unmodified output, which is precisely the
# state every adapter was in before this script existed. It is not a regression and it is
# not announced by this script either: a hook's stderr on a zero exit goes to the host's debug
# log and nowhere a reader looks. What reports it is the session banner's tool line, and only
# where `REQUIRED_TOOLS` in `amh.conf` names `python3` — the shipped example ships that key
# EMPTY, so an adopter who wants the report adds the name. Saying "the banner reports it"
# without that clause was false in this repository until the name was added, which is the
# enforcement-asymmetry class: prose implying a report nothing produces. And the banner looks
# a NAME up on PATH, so it calls a Windows Store alias present; the self-test, which runs the
# interpreter, is what reports that host honestly — as a SKIP, which the ladder shows as one.
#
# A bash fallback was considered and refused. `command-guard.sh` can afford one because it
# extracts a single documented string from a flat object and fails open on anything else.
# Here the input is an ARBITRARY nested response from an arbitrary tool, including tools
# this harness has never heard of, and a half-parser over that does not fail open — it
# emits a document that parses and says something else.

set -uo pipefail

HERE=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd) || exit 0
REDACT="$HERE/redact.sh"

# Bytes we are willing to hold and filter in one call. Past this the script stands down
# rather than growing unbounded work inside a hook the user is waiting on. The stand-down
# direction is the host's own (bound 2): unmodified output.
MAX_BYTES=$((8 * 1024 * 1024))
# String leaves we are willing to filter, one subprocess each. Past this the script stands
# down for the same reason as the byte ceiling: bounded work inside a hook, and the
# stand-down direction is the host's own — unmodified output.
MAX_LEAVES=512

# The interpreter, resolved by RUNNING it rather than by finding its name. On a stock Windows
# desktop a `python3` can be on PATH and run nothing — the Store's app-execution alias answers
# every call with an install prompt and a non-zero exit — and `command -v` calls that present,
# after which every call of this hook stood down (AMH ledger row DD030). `python` is tried
# second and accepted only as a Python 3. The probe costs one interpreter start per hook call,
# about 10 ms on Linux and more on Windows; knowing the interpreter runs is worth that.
PY=''
for candidate in python3 python; do
	command -v "$candidate" >/dev/null 2>&1 || continue
	"$candidate" -c 'import sys; sys.exit(0 if sys.version_info[0] == 3 else 1)' </dev/null >/dev/null 2>&1 || continue
	PY=$candidate
	break
done

# The shell that runs redact.sh is THIS one, named by path. Handing the walker the bare word
# `bash` let a native Windows Python resolve it through CreateProcess, which searches System32
# before PATH and finds WSL's launcher there — another shell in another filesystem namespace,
# on exactly the hosts this harness has recorded carrying one. Under Git Bash, `cygpath -m`
# turns this shell's own path into one a native program can start, and the `.exe` is spelled
# out: CreateProcess documents that it does NOT append one to a file name that carries a path.
# Everywhere else `$BASH` already is such a path.
BASH_BIN=${BASH:-bash}
if command -v cygpath >/dev/null 2>&1; then
	bash_native=$BASH_BIN
	case $bash_native in
	*.exe | *.EXE) ;;
	*) [ -f "$bash_native.exe" ] && bash_native=$bash_native.exe ;;
	esac
	BASH_BIN=$(cygpath -m "$bash_native" 2>/dev/null) || BASH_BIN=${BASH:-bash}
fi

# The whole program, as one heredoc, so the quoting story is "bash never interpolates into
# this" rather than a per-line judgement call. Everything it needs arrives in argv.
read -r -d '' WALKER <<'PY' || true
import json, os, subprocess, sys, time

redact = sys.argv[1]
max_bytes, max_leaves = int(sys.argv[2]), int(sys.argv[3])
# The bash to run redact.sh with, by path — never the bare word, which a native Windows
# Python resolves through System32 first (see BASH_BIN in the shell half).
bash_bin = sys.argv[4] if len(sys.argv) > 4 else "bash"

def stand_down(why=""):
    # Print NOTHING. The host then uses the original tool output, which is the safe
    # direction for every case that reaches here: we did not manage to look.
    if why:
        sys.stderr.write("redact-tool-output: %s\n" % why)
    sys.exit(0)

try:
    payload = json.load(sys.stdin)
except Exception:
    stand_down()
if not isinstance(payload, dict):
    stand_down()
if payload.get("hook_event_name") != "PostToolUse":
    stand_down()
if "tool_response" not in payload:
    stand_down()

response = payload["tool_response"]

# Collect every string leaf in document order. Depth is capped because a pathological
# document should stand down, not raise. Nothing is rebuilt yet: the rebuild below walks
# the ORIGINAL again in the same order, so the two traversals agree by construction rather
# than by a placeholder that a real JSON null could be mistaken for.
leaves = []
def collect(node, depth=0):
    if depth > 64:
        raise ValueError("nesting too deep")
    if isinstance(node, str):
        leaves.append(node)
    elif isinstance(node, list):
        for v in node:
            collect(v, depth + 1)
    elif isinstance(node, dict):
        for v in node.values():
            collect(v, depth + 1)

try:
    collect(response)
except Exception:
    stand_down()

if not leaves:
    stand_down()
if sum(len(s.encode("utf-8", "surrogatepass")) for s in leaves) > max_bytes:
    stand_down("tool_response is larger than this hook will filter; output left unmodified")
if any("\0" in s for s in leaves):
    # `sed` and NUL bytes do not have one portable story. Do not find out inside a rail.
    stand_down("tool_response contains a NUL byte; output left unmodified")

# ONE `redact.sh` invocation PER LEAF, and the reason is not tidiness.
#
# `redact.sh`'s private-key stage is a sed line RANGE: it opens on a line matching the BEGIN
# marker and closes on one matching END. Batching the leaves into a single stream — the first
# shape this script had — let an unterminated BEGIN in one leaf hold that range open across
# every LATER leaf, where `KEY_BLOCK_BODY` matches any all-base64 line: ordinary single words
# and most slash-only paths. A review measured it: a Read response came back with `filePath`
# replaced by `[REDACTED:private_key_body]`, and the marker rule below did NOT catch it,
# because the corrupting change genuinely adds a marker. Per-leaf is what makes each leaf's
# range state its own, and a filter with ANY cross-line state makes batching wrong by
# construction rather than by accident.
#
# Bounded because per-leaf means one subprocess per leaf: a leaf ceiling and a wall-clock
# deadline, both standing down rather than growing unbounded work inside a hook a user is
# waiting on.
if len(leaves) > max_leaves:
    stand_down("tool_response has more string leaves than this hook will filter; output left unmodified")

def filter_leaf(leaf):
    # A trailing marker line of our own, so the exact bytes come back. Every record ends in a
    # newline before the marker, which is what makes `sed` appending a final newline to a
    # stream that lacked one — which it does — invisible here. The marker is generated per
    # call: a stored one would be a constant an input could carry, and 128 random bits cannot
    # be guessed by output produced before this process started.
    mark = "AMH-REDACT-" + os.urandom(16).hex()
    sep = "\n" + mark + "\n"
    try:
        done = subprocess.run(
            [bash_bin, redact],
            input=(leaf + sep).encode("utf-8", "surrogatepass"),
            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=30,
        )
    except Exception:
        return None
    if done.returncode != 0:
        return None
    try:
        text = done.stdout.decode("utf-8", "surrogatepass")
    except Exception:
        # Not valid UTF-8 coming back is "I did not manage to look", not "nothing to redact".
        return None
    parts = text.split(sep)
    # One record, one separator, and an empty tail after it. Anything else means the filter
    # moved bytes we cannot account for, and an unaccountable rewrite of a tool result is not
    # something to publish.
    if len(parts) != 2 or parts[1] != "":
        return None
    return parts[0]

# The rule that decides whether a changed leaf is accepted: it must have gained a redaction
# marker, counted rather than merely present. `redact.sh` documents that its own stages are
# not byte-transparent on every platform — the GNU sed shipped with Git for Windows rewrites
# CRLF to LF for a script that matches nothing — so "the bytes differ" is NOT evidence of
# redaction, and taking a differing-but-unmarked leaf would let this hook quietly rewrite line
# endings inside a tool result. A CONTAINS test was the first shape and had a hole a review
# measured: a leaf that already carried the literal `[REDACTED:` — common in real output here,
# since this filter's own results and this repository's prose both contain it — was accepted on
# any byte change at all. Counting closes that: strictly more markers than before, or no deal.
MARKER = "[REDACTED:"
deadline = time.monotonic() + 30.0
out, changed = [], False
for original in leaves:
    if time.monotonic() > deadline:
        stand_down("filtering ran past this hook's deadline; output left unmodified")
    new = filter_leaf(original)
    if new is not None and new != original and new.count(MARKER) > original.count(MARKER):
        out.append(new)
        changed = True
    else:
        out.append(original)

if not changed:
    stand_down()

# Driven by the ORIGINAL node's type, never by a placeholder written into the string
# slots: a placeholder of None is indistinguishable from a JSON null already in the
# response, so a document carrying one would consume a filtered leaf and shift every
# later one — the model then reads one field's text under another field's name.
it = iter(out)
def merge(original, depth=0):
    if depth > 64:
        raise ValueError("nesting too deep")
    if isinstance(original, str):
        return next(it)
    if isinstance(original, list):
        return [merge(v, depth + 1) for v in original]
    if isinstance(original, dict):
        return dict((k, merge(v, depth + 1)) for k, v in original.items())
    return original

try:
    updated = merge(response)
    leftover = next(it, "!")
except Exception:
    stand_down()
if leftover != "!":
    stand_down("leaf accounting disagreed with the response shape")

notice = "AMH redacted one or more known credential shapes from this tool result before it reached the context. The unredacted value still exists where the tool produced it."
# Codex's payload carries a turn_id; Claude Code's does not. Codex PostToolUse supports
# blocking and model feedback but not arbitrary result replacement. Returning Claude's
# updatedToolOutput there is rejected as unsupported and FAILS OPEN, exposing the original.
# Blocking makes the completed tool look failed to the model, so this path is deliberately
# used only after a known shape was actually redacted. A string result remains text; a
# structured result becomes JSON text because Codex's reason field is necessarily a string.
if "turn_id" in payload:
    if isinstance(updated, str):
        filtered = updated
    else:
        try:
            filtered = json.dumps(updated)
        except Exception:
            stand_down()
    body = {"decision": "block", "reason": notice + "\n\n" + filtered}
else:
    body = {
        "hookSpecificOutput": {
            "hookEventName": "PostToolUse",
            "updatedToolOutput": updated,
            "systemMessage": notice,
        }
    }
# Serialised FIRST, then written once. Encoding inside the write would let a failure land
# after a partial document is already on stdout, which contradicts this script's own promise
# that an uncertain path prints nothing.
try:
    rendered = json.dumps(body)
except Exception:
    stand_down()
sys.stdout.write(rendered)
PY

run_hook() {
	[ -n "$PY" ] || exit 0
	[ -f "$REDACT" ] || exit 0
	# stderr is NOT discarded. The host ignores a zero-exit hook's stderr except in its debug
	# log, and that log is the only place a stand-down reason can be read at all — swallowing
	# it made "I did not manage to look" and "nothing to redact" the same observation.
	"$PY" -c "$WALKER" "$REDACT" "$MAX_BYTES" "$MAX_LEAVES" "$BASH_BIN" || exit 0
	exit 0
}

# --- self-test --------------------------------------------------------------------------
#
# Every secret-shaped fixture is GENERATED here and never stored: a literal in this file
# would make the file fail the repository's own secret scan, and the scan would be right
# (AMH ledger row D004).

st_fails=0
st_pass() { printf '  ok   %s\n' "$1"; }
st_fail() {
	printf '  FAIL %s\n' "$1"
	printf '       %s\n' "$2"
	st_fails=$((st_fails + 1))
}

rand_upper() { # <length>
	local n=$1 pool=''
	while [ "${#pool}" -lt "$n" ]; do
		pool=$pool$(head -c 512 /dev/urandom | LC_ALL=C tr -dc 'A-Z0-9')
	done
	printf '%s' "${pool:0:n}"
}
rand_b64() { # <length>
	local n=$1 pool=''
	while [ "${#pool}" -lt "$n" ]; do
		pool=$pool$(head -c 512 /dev/urandom | LC_ALL=C tr -dc 'A-Za-z0-9')
	done
	printf '%s' "${pool:0:n}"
}

# Build a payload from a JSON tool_response literal handed in on argv, so the test says
# what shape it is testing rather than hiding it behind a builder.
st_payload() { # <tool_response JSON>
	printf '{"hook_event_name":"PostToolUse","tool_name":"Bash","tool_input":{},"tool_response":%s}' "$1"
}

st_codex_payload() { # <tool_response JSON>
	printf '{"hook_event_name":"PostToolUse","turn_id":"turn-test","tool_name":"Bash","tool_input":{},"tool_response":%s}' "$1"
}

st_run() { # <payload> -> stdout of the hook
	printf '%s' "$1" | "$PY" -c "$WALKER" "$REDACT" "$MAX_BYTES" "$MAX_LEAVES" "$BASH_BIN" 2>/dev/null
}

# Extract a field with Python rather than grep: this suite is about JSON shape, and a
# grep over a JSON document cannot tell a value from a key that happens to spell it.
st_jq() { # <json> <python expression over `d`>
	printf '%s' "$1" | "$PY" -c 'import json,sys
d = json.load(sys.stdin)
sys.stdout.write(str(eval(sys.argv[1])))' "$2" 2>/dev/null
}

st_silent() { # <label> <payload> — a payload this hook must not answer
	local out
	out=$(st_run "$2")
	if [ -z "$out" ]; then st_pass "$1"; else st_fail "$1" "expected no output, got: ${out:0:160}"; fi
}

self_test() {
	printf 'redact-tool-output.sh self-test\n'
	if [ -z "$PY" ]; then
		# The reason stays on the SKIP line itself: the ladder reports that one line and no more.
		printf '  SKIP no Python 3 that runs on PATH (python3, then python; a Store alias counts as absent)\n'
		printf '       This hook stands down entirely on this host, which is the documented\n'
		printf '       absent-interpreter state, not a pass.\n'
		return 0
	fi
	if [ ! -f "$REDACT" ]; then
		printf '  FAIL redact.sh is not beside this script (%s) — nothing to filter with\n' "$REDACT"
		return 1
	fi

	local tok out shape body priv dash

	# 1. A known shape inside a nested response is redacted, and the response SHAPE is
	#    unchanged — the criterion that actually matters, because a replacement that does
	#    not match the tool's schema is discarded by the host and this rail becomes a
	#    no-op that reads as wired.
	tok="AKIA$(rand_upper 16)"
	out=$(st_run "$(st_payload "$(printf '{"stdout":"downloading as %s\\n","stderr":"","interrupted":false,"exitCode":0,"tags":["a","b"]}' "$tok")")")
	if [ -z "$out" ]; then
		st_fail 'redacts a known shape' 'the hook produced no output at all'
	elif printf '%s' "$out" | grep -qF "$tok"; then
		st_fail 'redacts a known shape' 'the generated token survived into the hook response'
	elif ! printf '%s' "$out" | grep -qF '[REDACTED:aws_access_key_id]'; then
		st_fail 'redacts a known shape' 'no redaction marker in the hook response'
	else
		st_pass 'redacts a known shape'
	fi

	if [ -n "$out" ]; then
		shape=$(st_jq "$out" 'd["hookSpecificOutput"]["hookEventName"]')
		if [ "$shape" = PostToolUse ]; then
			st_pass 'declares hookEventName PostToolUse'
		else
			st_fail 'declares hookEventName PostToolUse' "got: $shape"
		fi

		# Scalar types survive. A hook that stringified `false` or `0` would still look
		# redacted and would hand the model a different result.
		shape=$(st_jq "$out" '[type(d["hookSpecificOutput"]["updatedToolOutput"]["interrupted"]).__name__, type(d["hookSpecificOutput"]["updatedToolOutput"]["exitCode"]).__name__, len(d["hookSpecificOutput"]["updatedToolOutput"]["tags"]), sorted(d["hookSpecificOutput"]["updatedToolOutput"].keys())]')
		if [ "$shape" = "['bool', 'int', 2, ['exitCode', 'interrupted', 'stderr', 'stdout', 'tags']]" ]; then
			st_pass 'preserves the native result shape (types, keys, array length)'
		else
			st_fail 'preserves the native result shape (types, keys, array length)' "got: $shape"
		fi
	fi

	# 2. The defect this script exists to avoid. A private key inside ONE JSON string is
	#    the shape that a filter run over the SERIALISED response marks and leaves in the
	#    clear. Assert the BODY is gone, not merely that a marker appeared.
	body=$(rand_b64 64)
	# The markers are ASSEMBLED, never written out. A literal one in this file would be
	# rewritten by the repository's own secret scan — which runs `redact.sh` over every
	# tracked file and calls any changed byte a credential — and the scan would be right to.
	dash=$(printf '%s' '-----')
	priv=$(printf -- '%sBEGIN RSA PRIVATE KEY%s\\n%s\\n%sEND RSA PRIVATE KEY%s' "$dash" "$dash" "$body" "$dash" "$dash")
	out=$(st_run "$(st_payload "$(printf '{"stdout":"%s\\n","stderr":""}' "$priv")")")
	if [ -z "$out" ]; then
		st_fail 'redacts a private key body carried in one JSON string' 'the hook produced no output at all'
	elif printf '%s' "$out" | grep -qF "$body"; then
		st_fail 'redacts a private key body carried in one JSON string' \
			'the base64 body survived — the marker was replaced over a live value'
	elif ! printf '%s' "$out" | grep -qF '[REDACTED:private_key_body]'; then
		st_fail 'redacts a private key body carried in one JSON string' 'no body marker in the response'
	else
		st_pass 'redacts a private key body carried in one JSON string'
	fi

	# 3. A bare-string tool_response is a shape too, and it must stay a string.
	tok="AKIA$(rand_upper 16)"
	out=$(st_run "$(st_payload "$(printf '"token %s here"' "$tok")")")
	shape=$(st_jq "$out" 'type(d["hookSpecificOutput"]["updatedToolOutput"]).__name__')
	if [ "$shape" = str ] && ! printf '%s' "$out" | grep -qF "$tok"; then
		st_pass 'a string tool_response stays a string'
	else
		st_fail 'a string tool_response stays a string' "type: $shape"
	fi

	# Codex accepts PostToolUse blocking plus a string reason, not Claude's arbitrary
	# updatedToolOutput. The original must be blocked only when a redaction occurred, and the
	# filtered text must be the feedback the model receives.
	tok="AKIA$(rand_upper 16)"
	out=$(st_run "$(st_codex_payload "$(printf '"token %s here"' "$tok")")")
	shape=$(st_jq "$out" '[d.get("decision"), type(d.get("reason")).__name__, "hookSpecificOutput" in d]')
	if [ "$shape" = "['block', 'str', False]" ] &&
		! printf '%s' "$out" | grep -qF "$tok" &&
		printf '%s' "$out" | grep -qF '[REDACTED:aws_access_key_id]'; then
		st_pass 'Codex blocks the original and returns filtered model feedback'
	else
		st_fail 'Codex blocks the original and returns filtered model feedback' "got: ${out:0:200}"
	fi
	st_silent 'Codex does not block an unchanged result' \
		"$(st_codex_payload '"all tests passed"')"

	# 4. A real JSON null must not consume a filtered leaf. This is the placeholder
	#    collision the rebuild walks the ORIGINAL to avoid; without that, every leaf after
	#    the null shifts by one and the model reads one field's text under another's name.
	tok="AKIA$(rand_upper 16)"
	out=$(st_run "$(st_payload "$(printf '{"a":null,"b":"%s","c":"tail"}' "$tok")")")
	shape=$(st_jq "$out" '[d["hookSpecificOutput"]["updatedToolOutput"]["a"], d["hookSpecificOutput"]["updatedToolOutput"]["c"]]')
	if [ "$shape" = "[None, 'tail']" ]; then
		st_pass 'a JSON null does not shift the leaf accounting'
	else
		st_fail 'a JSON null does not shift the leaf accounting' "got: $shape"
	fi

	# 5–8. Every stand-down path answers with nothing, which is what leaves the original
	#      tool output in place. A hook that answered here would be rewriting a result on
	#      the strength of a payload it did not understand.
	st_silent 'says nothing about output with no known shape in it' \
		"$(st_payload '{"stdout":"all tests passed\n","stderr":""}')"
	st_silent 'says nothing about a non-PostToolUse payload' \
		'{"hook_event_name":"PreToolUse","tool_name":"Bash","tool_input":{"command":"ls"}}'
	st_silent 'says nothing about a payload with no tool_response' \
		'{"hook_event_name":"PostToolUse","tool_name":"Bash","tool_input":{}}'
	st_silent 'says nothing about malformed JSON' '{"hook_event_name":'
	st_silent 'says nothing about an empty payload' ''

	# 9. Cross-leaf contamination. `redact.sh`'s key stage is a sed line RANGE, so an
	#    unterminated BEGIN marker in one leaf held it open across every later leaf when the
	#    leaves went through as one stream — and `KEY_BLOCK_BODY` matches any all-base64 line,
	#    which ordinary words and slash-only paths are. The marker rule cannot catch it: the
	#    corrupting change genuinely adds a marker. This fixture fails against any batched
	#    implementation and is the reason filtering is per leaf.
	body=$(rand_b64 48)
	out=$(st_run "$(st_payload "$(printf '{"stdout":"%sBEGIN RSA PRIVATE KEY%s","stderr":"hello\\nworld\\n","path":"/home/user/scratch","token":"%s"}' "$dash" "$dash" "$body")")")
	if [ -z "$out" ]; then
		st_fail 'one leaf cannot redact another leaf' 'the hook produced no output at all'
	elif ! printf '%s' "$out" | grep -qF 'hello' ||
		! printf '%s' "$out" | grep -qF '/home/user/scratch' ||
		! printf '%s' "$out" | grep -qF "$body"; then
		st_fail 'one leaf cannot redact another leaf' \
			"a later leaf was rewritten by an earlier leaf's open range: ${out:0:200}"
	else
		st_pass 'one leaf cannot redact another leaf'
	fi

	# 10. The marker rule COUNTS markers rather than testing for one. A leaf that already
	#     carries the literal text — common here, since this filter's own output and this
	#     repository's prose both contain it — must not be accepted on a byte change that
	#     redacted nothing. Exercised against a stub filter that only strips CR, which is
	#     exactly what `redact.sh` documents its sed doing on a Windows checkout, so this
	#     fixture fails on a platform-independent substitute for a platform-specific defect.
	st_stub_case() { # <label> <leaf JSON string literal> — expects silence
		local label=$1 leaf=$2 tmp stub_out
		tmp=$(mktemp -d) || return 1
		cp -- "$0" "$tmp/hook.sh" || { rm -rf -- "$tmp"; return 1; }
		# A filter that substitutes NOTHING and only removes CR. Any acceptance here is the
		# hook publishing a line-ending rewrite as a redaction.
		printf '#!/usr/bin/env bash\nexec tr -d "\\r"\n' >"$tmp/redact.sh"
		chmod 755 "$tmp/hook.sh" "$tmp/redact.sh"
		stub_out=$(printf '%s' "$(st_payload "{\"stdout\":$leaf}")" | bash "$tmp/hook.sh" 2>/dev/null)
		rm -rf -- "$tmp"
		if [ -z "$stub_out" ]; then
			st_pass "$label"
		else
			st_fail "$label" "a filter that redacted nothing was published as a redaction: ${stub_out:0:200}"
		fi
	}
	st_stub_case 'a substitution-free filter is never published as a redaction' '"first\r\nsecond\r\n"'
	st_stub_case 'a leaf that ALREADY carries the marker text is counted, not merely matched' \
		'"[REDACTED:aws_access_key_id]\r\nsecond\r\n"'

	# 11. Both ceilings stand down rather than growing unbounded work, and nesting past the
	#     depth cap stands down rather than raising. Each is a path the matrix would otherwise
	#     never enter, so a regression in any of them would be invisible.
	st_silent 'stands down past the leaf ceiling' \
		"$(st_payload "$("$PY" -c 'import json;print(json.dumps(["x"] * 600))')")"
	st_silent 'stands down past the depth cap' \
		"$(st_payload "$("$PY" -c 'import json
d = "leaf"
for _ in range(80):
    d = [d]
print(json.dumps(d))')")"

	# 12. Line endings alone are not a redaction on THIS platform either — a weaker check than
	#     case 10, kept because it runs against the real `redact.sh` rather than a stub.
	out=$(st_run "$(st_payload "$(printf '{"stdout":"first\\r\\nsecond\\r\\n"}')")")
	if [ -z "$out" ]; then
		st_pass 'a CRLF-only difference is not accepted as a redaction'
	else
		st_fail 'a CRLF-only difference is not accepted as a redaction' "the hook rewrote a leaf with no marker: ${out:0:160}"
	fi

	# 13. The two Windows hazards, reproduced on ANY host so the Linux leg can see them. Shims
	#     first on PATH: a `python3` that answers like the Store alias (a message, a non-zero
	#     exit), a `python` that is the real interpreter, and a `bash` that refuses to run.
	#     The hook is started through its real entry point with THIS shell named by path, so
	#     the shim bash never runs the hook itself — only something that asked PATH for `bash`
	#     would reach it, and the walker asking for it by name is the defect under test. Both
	#     halves are needed for a redaction to come back: resolving `python3` by name finds the
	#     stub and stands down, and — on a POSIX host — running `bash` by name finds the shim and
	#     fails the leaf. On Windows a native Python never finds an extensionless shim, so there
	#     this case tests the interpreter half only; the bash half is what the Linux leg is for.
	#     The `python` shim execs the interpreter's OWN path, from `sys.executable`: a
	#     version-manager shim (pyenv, asdf) is itself an `env bash` script, and routed through
	#     the refusing `bash` it would fail this case for a reason that is not the code's.
	local shim real_py
	real_py=$("$PY" -c 'import sys; print(sys.executable)' 2>/dev/null)
	if [ -n "$real_py" ] && command -v cygpath >/dev/null 2>&1; then
		real_py=$(cygpath -u "$real_py" 2>/dev/null) || real_py=''
	fi
	shim=$(mktemp -d "${TMPDIR:-/tmp}/amh-rto-shim.XXXXXX") || shim=''
	if [ -z "$shim" ] || [ -z "$real_py" ]; then
		st_fail 'a Python that does not run is passed over, and redact.sh runs under this shell' \
			'could not build the shim directory — this case checked NOTHING'
	else
		printf '#!/bin/sh\necho "Python was not found; install it from the Store" >&2\nexit 49\n' >"$shim/python3"
		printf '#!/bin/sh\nexec "%s" "$@"\n' "$real_py" >"$shim/python"
		printf '#!/bin/sh\nexit 1\n' >"$shim/bash"
		chmod 755 "$shim/python3" "$shim/python" "$shim/bash"
		tok="AKIA$(rand_upper 16)"
		out=$(printf '%s' "$(st_payload "$(printf '{"stdout":"key %s\\n"}' "$tok")")" |
			PATH="$shim:$PATH" "$BASH" "${BASH_SOURCE[0]}" 2>/dev/null)
		rm -rf -- "$shim"
		if [ -z "$out" ]; then
			st_fail 'a Python that does not run is passed over, and redact.sh runs under this shell' \
				'the hook produced no output: it used the stub python3, or ran redact.sh with the bash PATH names'
		elif printf '%s' "$out" | grep -qF "$tok"; then
			st_fail 'a Python that does not run is passed over, and redact.sh runs under this shell' \
				'the token survived'
		else
			st_pass 'a Python that does not run is passed over, and redact.sh runs under this shell'
		fi
	fi

	# 14. A Python 2 is passed over too. A stub that simply fails cannot pin the probe's
	#     VERSION test, only its "does it start" half, so this `python3` answers the probe the
	#     way a Python 2 would — major version 2, so the `== 3` test exits 1 — and fails on
	#     anything else the way a Python 2 fails on this program. Accepted, it would stand the
	#     hook down; only a probe that checks the major version leaves `python` to do the work.
	shim=$(mktemp -d "${TMPDIR:-/tmp}/amh-rto-shim.XXXXXX") || shim=''
	if [ -z "$shim" ] || [ -z "$real_py" ]; then
		st_fail 'a Python 2 is passed over' 'could not build the shim directory — this case checked NOTHING'
	else
		# shellcheck disable=SC2016 # the shim's `$2` and `$@` belong to the shim, not to this script
		printf '#!/bin/sh\ncase "$2" in *version_info*) exec "%s" -c "import sys; sys.version_info = (2, 7, 18); exec(sys.argv[1])" "$2" ;; esac\nexit 1\n' "$real_py" >"$shim/python3"
		printf '#!/bin/sh\nexec "%s" "$@"\n' "$real_py" >"$shim/python"
		chmod 755 "$shim/python3" "$shim/python"
		tok="AKIA$(rand_upper 16)"
		out=$(printf '%s' "$(st_payload "$(printf '{"stdout":"key %s\\n"}' "$tok")")" |
			PATH="$shim:$PATH" "$BASH" "${BASH_SOURCE[0]}" 2>/dev/null)
		rm -rf -- "$shim"
		if [ -n "$out" ] && ! printf '%s' "$out" | grep -qF "$tok"; then
			st_pass 'a Python 2 is passed over'
		else
			st_fail 'a Python 2 is passed over' 'the hook did not redact: it accepted the Python 2 and stood down'
		fi
	fi

	if [ "$st_fails" -eq 0 ]; then
		printf 'redact-tool-output.sh self-test: all checks passed\n'
		return 0
	fi
	printf 'redact-tool-output.sh self-test: %s check(s) failed\n' "$st_fails"
	return 1
}

case ${1-} in
--self-test) self_test ;;
'') run_hook ;;
*)
	printf 'usage: %s [--self-test]\n' "$0" >&2
	exit 2
	;;
esac
