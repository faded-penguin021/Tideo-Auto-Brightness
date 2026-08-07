#!/usr/bin/env bash
# AMH — instructive pre-execution command guard (P13).
#
# Checks a shell command against the harness's hard rails BEFORE it runs and blocks
# with a reason that names the rule and the correct alternative, so the agent
# self-corrects in one step instead of fighting a mute prefix-matched denial.
#
# This is the TOP layer of three. Beneath it: the agent's static permission deny
# rules, and (server-side) branch protection + secret-scanning push protection. Those
# layers bind actors that never load this script.
#
# Design rules this guard is bound by — each one paid for in false positives:
#   * Judge only the LEADING command of each simple-command segment, with quoting
#     respected. Text that merely CONTAINS a forbidden command — a commit message, a
#     doc heredoc, this script's own CLI — must never trip it.
#   * Target agent MISTAKES, not evasion. Quoting and prefix tricks are accepted
#     misses; the layers beneath catch those.
#   * Fail OPEN on malformed input. A guard that bricks every command gets disabled,
#     not fixed.
#
# WHAT THIS GUARD DOES NOT CATCH — the consolidated list, so no one has to reconstruct
# it from the per-scanner notes below and no one mistakes this script for a vault. Each
# scanner still carries its own `Accepted miss:` note at the point it applies; this block
# is the index, and it is deliberately exhaustive about the categories rather than about
# every spelling inside them:
#
#   * INTERPRETERS. The secret-file scanners recognise a file read through an enumerated
#     list of reader commands (`cat`, `grep`, `awk`, `wc`, `md5sum` and about thirty more)
#     or a `<` redirection. A one-time `.env` advisory now blocks the first command text
#     that names the path, but after that speed bump the list is still a list, not a
#     category: `python3 -c "open('.env')"`, `perl -e`, `node -e`, `ruby -e` and every
#     other interpreter NOT on it reach the file unjudged. This remains the widest hole
#     and it is structural —
#     enumerating interpreters would not close it, since each has unbounded ways to spell
#     a read. Note the shape of the miss for a listed one: `awk '{print}' .env` is blocked
#     because `awk` leads the segment, while `awk 'BEGIN{while((getline<".env")>0)print}'`
#     hides the read inside the program text and is not.
#   * WRAPPERS — but read which ones. `check_segment` STRIPS a set of transparent prefixes
#     and judges what follows, so `sudo cat .env`, `nohup cat .env`, `nice`, `time`,
#     `command`, `builtin`, `exec` and `env FOO=1 cat .env` ARE blocked (and a bare `env`
#     or `env -i` is itself a dump, blocked on its own account). What gets past is every
#     wrapper outside that set: `xargs cat .env`, `timeout 5 cat .env`, `ssh host cat
#     .env`, `bash -c 'cat .env'`, and any shell function or alias standing in for the
#     command. Do not read this bullet as "wrappers defeat the guard" — several do not,
#     and deleting the strip loop because a comment said it was useless would remove real
#     coverage the self-test asserts.
#   * CONSTRUCTED AND ENCODED COMMANDS. `eval`, base64/hex payloads decoded at runtime,
#     and any command assembled from variables are text at scan time, not commands.
#   * HEREDOCS AND LONG LINES. `cmd <<EOF` hides its body until the delimiter, and the
#     window-based scanners give up past `CHAR_LOOKAHEAD` characters — a variable name or
#     redirection target longer than the window is not classified.
#   * WHAT NO SCANNER LOOKS AT AT ALL. Container and service inspect output
#     (`docker inspect` and friends) is prose-only policy: no guard sees it, and none is
#     proposed, because it would block ordinary use to catch a shape the harness does not
#     run. The identity rules are likewise prose here — an identity not yet committed is
#     not on disk to check.
#
# None of this is a defect list. This guard exists to make the honest mistake expensive
# and instructive; the deny rails beneath it add the spellings a prefix matcher can
# express, and the rules in the constitution bind the agent whether or not any script can
# see the shape it chose. Treat a green run as "no mistake this scanner recognises", never
# as "this command is safe".
#
# Usage:
#   command-guard.sh                  read a hook payload (JSON) on stdin
#   command-guard.sh --command 'CMD'  check one command directly
#   command-guard.sh --self-test      blocked + allowed fixture matrix
#
# Exit codes: 0 = allowed (or fail-open; warnings may print on stderr), 2 = blocked (reason on stderr).
#
# Shipped by the Agentic Maintenance Harness. Repo-agnostic: do not edit locally.
#
# On `AMH ledger row DNNN` references below: they point at the HARNESS's ledger, which
# explains why this script is shaped the way it is. They are deliberately NOT written as
# `D-NNN` citations, because a citation is a promise that the ID resolves — and in your
# repository it never can, since those rows are ours and cannot appear in your ledger.
# Written as citations they made the ladder's citation guard fail on a repo its owner had
# not yet touched, for rows they could not have written.

set -uo pipefail

# Byte semantics, deliberately. Every scanner below indexes a string one character
# at a time; under a UTF-8 locale bash decodes from the start of the string for each
# index, which makes each scan quadratic in the command's length — 32 KB of command
# text took ~14s here, and agents write multi-KB heredocs routinely. The guard makes
# no linguistic judgements, only ASCII shell-syntax ones, so bytes are the right unit.
export LC_ALL=C

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"

DEFAULT_BRANCH=main
BRANCH_PREFIX=session
# shellcheck source=/dev/null
[ -f "$ROOT/amh.conf" ] && . "$ROOT/amh.conf"

# --- windowed character scanning --------------------------------------------
# Every scanner below walks a string one character at a time. `${s:i:1}` re-walks the
# string from the start on each index, so those scans are quadratic in the command's
# length: 32 KB took ~14s before the locale fix above and ~3.5s after, and agents
# write multi-KB heredocs routinely. A guard slow enough to hit the hook timeout is a
# guard that gets removed, not fixed.
#
# So each loop copies a WINDOW out of the string and indexes inside that instead,
# refreshing whenever fewer than CHAR_LOOKAHEAD characters remain ahead of the cursor.
# The idiom is four lines and appears in each scanner (a helper function per character
# costs more than it saves); this is the one place it is explained. Every one of these
# cursors advances monotonically, which is what makes a forward-only window safe.
#
# Accepted miss: a variable name or redirection target longer than CHAR_LOOKAHEAD is
# truncated before it is judged. That is the fail-open direction in `redirect_targets`
# and `heredoc_delim`, but NOT uniformly: a name truncated mid-`${VAR:+…}` loses the
# modifier and blocks, and a truncated `.env.example` stops looking like a template and
# blocks. Both need a 512-character token to reach, so the direction is stated honestly
# rather than defended.
CHAR_WINDOW=2048
CHAR_LOOKAHEAD=512

# --- segment splitting ------------------------------------------------------
# Emit one line per simple-command segment, splitting on UNQUOTED shell operators.
# Quoted regions stay inside their segment and are therefore never a leading command.
#
# Slices, never accumulates: `seg+=$c` per character reallocates the segment on
# every byte, which is the second half of the quadratic blow-up (the first is the
# locale, above). Splitting on index boundaries copies each segment exactly once.
split_segments() {
	local s=$1
	local i=0 n=${#s} c q='' start=0
	local wbase=0 wbuf='' wsafe=0
	local out=()
	while [ "$i" -lt "$n" ]; do
		if [ "$i" -ge "$wsafe" ]; then # windowed scan — see CHAR_WINDOW
			wbase=$i
			wbuf=${s:wbase:CHAR_WINDOW}
			wsafe=$((wbase + CHAR_WINDOW - CHAR_LOOKAHEAD))
		fi
		c=${wbuf:i-wbase:1}
		if [ -n "$q" ]; then
			[ "$c" = "$q" ] && q=''
			i=$((i + 1))
			continue
		fi
		case $c in
		"'" | '"') q=$c ;;
		\\) i=$((i + 1)) ;;
		';' | '&' | '|' | $'\n' | '(' | ')' | '{' | '}' | '`')
			out+=("${s:start:i-start}")
			start=$((i + 1))
			;;
		'$')
			if [ "${wbuf:i-wbase+1:1}" = '(' ]; then
				out+=("${s:start:i-start}")
				i=$((i + 1))
				start=$((i + 1))
			fi
			;;
		esac
		i=$((i + 1))
	done
	out+=("${s:start}")
	# NUL-separated: a quoted NEWLINE stays inside its segment (a commit message body
	# is one argument), and a newline-separated round-trip would re-split it — turning
	# the body's second line into a leading command and blocking a commit on its own
	# message. That is the AMH ledger row D007 class arriving through the transport
	# instead of the scan.
	printf '%s\0' "${out[@]}"
}

# Remove here-document BODIES before segmenting. A heredoc body is data — a
# commit message, a doc block, a file being written — and it routinely quotes
# the very commands this guard blocks. Backticks split segments (command
# substitution), so prose like "the guard blocks `env`" becomes a segment whose
# leading word is `env`, and the rail fires on a commit message describing
# itself. Found in live use, writing the commit that shipped this guard.
#
# Accepted miss, deliberately: `cmd <<EOF` hides everything until the
# terminator, so a real dump command placed inside a heredoc body is not
# judged. That is the fail-open direction, and heredoc-as-evasion is not the
# agent mistake this guard targets.
#
# Opening body mode is the most dangerous thing this file does: every line until the
# delimiter goes UNJUDGED, so a delimiter no line can ever match discards the rest of
# the command whole. A substring test for `<<` matched three things that open no body
# at all — `<<<` here-strings, `$((1<<8))` shifts, and `<<` inside quoted prose — and
# each resolved to a delimiter (`<`, `8))`, a word) that never recurred, so
# `grep -q x <<<"$v"; git push --force origin main` was ALLOWED. That voided every
# rail, force-push and push-to-`main` included. So: this scan is fail-CLOSED. When the
# operator is not unmistakably a here-document, do not open body mode.
#
# Sets HEREDOC_DELIM and returns 0 when $1 opens a here-document body.
HEREDOC_DELIM=''
heredoc_delim() {
	local s=$1
	local i=0 n=${#s} c q='' rest delim
	local wbase=0 wbuf='' wsafe=0
	HEREDOC_DELIM=''
	case $s in *'<<'*) ;; *) return 1 ;; esac # no operator at all: skip the scan
	while [ "$i" -lt "$n" ]; do
		if [ "$i" -ge "$wsafe" ]; then # windowed scan — see CHAR_WINDOW
			wbase=$i
			wbuf=${s:wbase:CHAR_WINDOW}
			wsafe=$((wbase + CHAR_WINDOW - CHAR_LOOKAHEAD))
		fi
		c=${wbuf:i-wbase:1}
		if [ -n "$q" ]; then
			[ "$c" = "$q" ] && q=''
			i=$((i + 1))
			continue
		fi
		case $c in
		"'" | '"') q=$c ;;
		\\) i=$((i + 1)) ;;
		'<')
			# `<<<` is a here-STRING: it redirects one line of text and opens no body.
			if [ "${wbuf:i-wbase+1:1}" = '<' ] && [ "${wbuf:i-wbase+2:1}" != '<' ]; then
				rest=${wbuf:i-wbase+2}
				rest=${rest#-}
				rest=${rest#"${rest%%[![:space:]]*}"}
				delim=${rest%%[[:space:];|&<>)(\`]*}
				# An operand the arithmetic close-paren terminates is a SHIFT, not a
				# delimiter: `$((mask << bits))` names no here-document, and a digit
				# test alone only catches the literal `$((1 << 8))` form. Checked
				# before the quote and backslash strips below, which change the length.
				case ${rest:${#delim}:1} in ')') delim='' ;; esac
				delim=${delim#[\'\"]}
				delim=${delim%[\'\"]}
				# `cat <<\EOF` is a real here-document whose terminator is `EOF`: bash
				# removes the quoting backslash from the delimiter word. Keeping it
				# stores `\EOF`, which no line ever matches — body mode then never
				# closes and every remaining line goes unjudged.
				delim=${delim//\\/}
				case $delim in
				# A digit-led operand is an arithmetic shift (`$((1 << 8))`), not a
				# delimiter — no shell accepts `8` as a here-document terminator word.
				'' | [0-9]*) ;;
				*)
					HEREDOC_DELIM=$delim
					return 0
					;;
				esac
			fi
			# Skip the whole operator either way, so `<<<` is never re-read as `<`.
			i=$((i + 1))
			;;
		esac
		i=$((i + 1))
	done
	return 1
}

strip_heredocs() {
	local s=$1
	local line trimmed delim='' body=1
	local out=()
	while IFS= read -r line; do
		if [ "$body" -eq 0 ]; then
			trimmed=${line#"${line%%[![:space:]]*}"} # <<- strips leading tabs
			[ "$trimmed" = "$delim" ] && body=1
			continue
		fi
		out+=("$line")
		if heredoc_delim "$line"; then
			delim=$HEREDOC_DELIM
			body=0
		fi
	done <<<"$s"
	[ "${#out[@]}" -gt 0 ] && printf '%s\n' "${out[@]}"
	return 0
}

# --- rails ------------------------------------------------------------------
BLOCK_REASON=''
WARN_REASON=''
ADVISORY_REASON=''
DOTENV_ADVISORY_REASON=''

is_env_template() { # .env.example and friends carry no secrets
	case $1 in
	*.env.example | *.env.sample | *.env.template | *.env.dist) return 0 ;;
	*) return 1 ;;
	esac
}


# A broad, one-time speed bump for commands that merely CONTAIN `.env`, including
# interpreter snippets this guard deliberately cannot parse as file reads. This is an
# advisory block, not a proof of danger: the second attempt in the same session is
# allowed through to the normal rails so false positives do not brick the agent.
#
# Session identity is not portable across agent vendors, so the default state is a
# per-repository, per-category file under /tmp. Tests may override either category's
# file explicitly; DOTENV_ADVISORY_STATE remains supported for compatibility.
advisory_state_file() { # advisory_state_file <name>
	local name=$1 slug uid
	case $name in
	dotenv) [ -n "${DOTENV_ADVISORY_STATE+x}" ] && { printf '%s' "$DOTENV_ADVISORY_STATE"; return 0; } ;;
	destructive) [ -n "${DESTRUCTIVE_ADVISORY_STATE+x}" ] && { printf '%s' "$DESTRUCTIVE_ADVISORY_STATE"; return 0; } ;;
	*) return 1 ;;
	esac
	slug=${ROOT//\//_}
	slug=${slug// /_}
	uid=${UID:-unknown}
	printf '/tmp/amh-command-guard-%s-advisory-%s-%s' "$name" "$uid" "$slug"
}

needs_one_time_advisory() { # needs_one_time_advisory <name> <command>
	local name=$1 cmd=$2 state
	case $name in
	dotenv) case $cmd in *.env*) ;; *) return 1 ;; esac ;;
	destructive) is_destructive_command "$cmd" || return 1 ;;
	*) return 1 ;;
	esac
	state=$(advisory_state_file "$name")
	[ -n "$state" ] || return 1
	[ -e "$state" ] && return 1
	: >"$state" 2>/dev/null || return 1
	case $name in
	dotenv)
		# shellcheck disable=SC2016 # the presence-check example must print literally.
		ADVISORY_REASON='This command mentions `.env`. Those files commonly contain live credentials, and even lengths, hashes, excerpts, copies or interpreter reads can disclose or spread secrets. The command guard is stopping this once so you can reconsider: prefer presence-only checks (for example, `[ -n "${MY_KEY:-}" ] && echo set`) or let the tool that needs credentials read them directly. If this warning is not applicable, or this is a false positive such as prose or a template-safe operation, run the same command again; this one-time advisory will not rearm during this session.'
		DOTENV_ADVISORY_REASON=$ADVISORY_REASON
		;;
	destructive)
		ADVISORY_REASON='This destructive filesystem command may delete guard fixtures, source files, or untracked evidence. Prefer targeted removal, moving the path set to a temporary directory, or confirming the complete path set before deletion. The command guard is stopping this once so you can reconsider; rerun the command to proceed if the deletion is intentional.'
		;;
	esac
	return 0
}

names_env_file() {
	case $1 in
	.env | .env.* | */.env | */.env.*) is_env_template "$1" && return 1 || return 0 ;;
	# The kernel's copy of a live process's environment — the same dump `env` makes,
	# reachable with a file reader instead of a command.
	/proc/*/environ) return 0 ;;
	*) return 1 ;;
	esac
}

# True if a variable NAME is credential-shaped. Matched on the LAST underscore-
# delimited component, never as a substring: `$AWS_SECRET_ACCESS_KEY` is a secret,
# `$SSH_KEY_PATH` is a path, `$AWS_ACCESS_KEY_ID` is an identifier and `$MONKEY` is
# a monkey. Substring matching blocks all four, and a rail that blocks
# `echo "$SSH_KEY_PATH"` gets disabled, not fixed.
#
# The last component ALONE overshoots the other way, which is what shipped: `$key`,
# `$sort_key`, `$page_token`, `$csrf_token`, `$public_key` and `$LICENSE_KEY` are
# ordinary program variables and were all blocked. Two narrowings, both deliberately
# lists rather than heuristics — extend them when a false positive shows up:
#   * `key` and `token` on their OWN are the generic-programming words (a loop
#     variable, a map key, a pagination cursor), so a bare one is not a credential.
#     Bare `secret`, `password` and `credentials` still are — nothing benign is called
#     that.
#   * a qualifier that names the thing as public (`public_key`) or as a structural
#     key (`sort_key`, `page_token`, `csrf_token`) clears it. `api`, `access`,
#     `private`, `auth` and everything not listed do NOT clear it.
# Accepted misses, in the fail-open direction: `$TOKEN_B64`, `$KEY2`, `$MY_KEY_V2`.
BENIGN_KEY_QUALIFIERS=' public sort page paging next prev previous cursor csrf xsrf license licence cache primary foreign partition idempotency map dict index order group row column col lookup shard '
is_secret_name() {
	local name=$1 tail rest qual
	tail=${name##*_}
	case $tail in
	[Kk][Ee][Yy] | [Tt][Oo][Kk][Ee][Nn]) ;;
	[Ss][Ee][Cc][Rr][Ee][Tt] | [Pp][Aa][Ss][Ss][Ww][Oo][Rr][Dd] | [Pp][Aa][Ss][Ss][Ww][Dd]) return 0 ;;
	[Cc][Rr][Ee][Dd][Ee][Nn][Tt][Ii][Aa][Ll][Ss] | [Cc][Rr][Ee][Dd][Ee][Nn][Tt][Ii][Aa][Ll]) return 0 ;;
	*) return 1 ;;
	esac
	if [ "$tail" = "$name" ]; then
		# A bare LOWERCASE `key`/`token` is the program variable the exemption is for.
		# In the shell's uppercase convention a bare `$TOKEN` or `$KEY` is a credential
		# name and one of the commonest there is — exempting it by case-insensitive
		# match would hand back the rail this narrowing is supposed to keep.
		case $name in [a-z_]*) return 1 ;; *) return 0 ;; esac
	fi
	rest=${name%_*}
	qual=${rest##*_}
	qual=$(printf '%s' "$qual" | tr '[:upper:]' '[:lower:]')
	case $BENIGN_KEY_QUALIFIERS in
	*" $qual "*) return 1 ;;
	esac
	return 0
}

# True if a raw argument would EXPAND a credential-shaped variable into output.
# Quoting decides: `echo "$GITHUB_TOKEN"` leaks, `echo 'set $GITHUB_TOKEN first'`
# and `echo "run with \$GITHUB_TOKEN"` expand nothing and are ADVICE — the shape an
# agent writes when a credential is missing. Quoted text is DATA, never a command;
# the remediation instead of the leak is the false positive that kills a rail.
#
# `${VAR:+set}` and `${VAR+set}` expand to the LITERAL, never to the value — the same
# presence-not-value answer the block reason's own `[ -n "${MY_KEY:-}" ] && echo set`
# gives, reached by a different modifier. Blocking it left the agent no spelling of the
# thing the guard was telling it to do. `${#VAR}` is the length, which the rule forbids
# printing, and was not caught at all.
expands_secret_var() {
	local s=$1 # split: `local s=$1 n=${#s}` expands ${#s} BEFORE s exists (set -u)
	local i=0 n=${#s} c q='' rest name op1 op2
	local wbase=0 wbuf='' wsafe=0
	case $s in *'$'*) ;; *) return 1 ;; esac # nothing to expand: skip the scan
	while [ "$i" -lt "$n" ]; do
		if [ "$i" -ge "$wsafe" ]; then # windowed scan — see CHAR_WINDOW
			wbase=$i
			wbuf=${s:wbase:CHAR_WINDOW}
			wsafe=$((wbase + CHAR_WINDOW - CHAR_LOOKAHEAD))
		fi
		c=${wbuf:i-wbase:1}
		case $c in
		\\)
			# Outside single quotes a backslash escapes the next character.
			if [ "$q" != "'" ]; then
				i=$((i + 2))
				continue
			fi
			;;
		"'")
			if [ "$q" = "'" ]; then q=''; elif [ -z "$q" ]; then q="'"; fi
			;;
		'"')
			if [ "$q" = '"' ]; then q=''; elif [ -z "$q" ]; then q='"'; fi
			;;
		'$')
			if [ "$q" != "'" ]; then
				rest=${wbuf:i-wbase+1}
				if [ "${rest:0:1}" = '{' ]; then
					rest=${rest#\{}
					rest=${rest#\#} # `${#VAR}` — the length is forbidden output too
					name=${rest%%[!A-Za-z0-9_]*}
					op1=${rest:${#name}:1}
					op2=${rest:${#name}+1:1}
					if [ "$op1" = '+' ] || { [ "$op1" = ':' ] && [ "$op2" = '+' ]; }; then
						: # expands to the alternate literal, never to the value
					elif [ -n "$name" ] && is_secret_name "$name"; then
						return 0
					fi
				else
					name=${rest%%[!A-Za-z0-9_]*}
					[ -n "$name" ] && is_secret_name "$name" && return 0
				fi
			fi
			;;
		esac
		i=$((i + 1))
	done
	return 1
}

# True if a shell builtin is being used in its DUMP-EVERYTHING form. `set -euo
# pipefail`, `export FOO=1` and `declare -a xs` are ordinary usage and must pass;
# bare `set`, `export -p` and `declare -x` print every variable's value.
is_env_dump_builtin() {
	local cmd=$1
	shift
	local a
	case $cmd in
	set)
		[ "$#" -eq 0 ] && return 0
		return 1
		;;
	export | typeset | declare | readonly)
		[ "$#" -eq 0 ] && return 0
		local dump_flag=1 operand=1
		for a in "$@"; do
			case $a in
			*=*) return 1 ;;         # an assignment, not a dump
			-*p* | -*x*) dump_flag=0 ;;
			-*) ;;
			*) operand=0 ;; # names a variable: prints that one, not the environment
			esac
		done
		[ "$dump_flag" -eq 0 ] && [ "$operand" -ne 0 ] && return 0
		return 1
		;;
	esac
	return 1
}

# Split a segment into words on UNQUOTED whitespace, then remove the quote
# characters. `cat ".env"` names a file; the pattern in `grep -q "cat .env" f` is ONE
# word containing a space and names nothing. Raw `${var}` word-splitting cannot tell
# them apart — it is the same presence-vs-position error AMH ledger row D007 records for
# `<`, and it is why the operand scan blocked a grep for the string ".env".
split_words() {
	local s=$1
	local i=0 n=${#s} c q='' start=-1 w
	local wbase=0 wbuf='' wsafe=0
	while [ "$i" -lt "$n" ]; do
		if [ "$i" -ge "$wsafe" ]; then # windowed scan — see CHAR_WINDOW
			wbase=$i
			wbuf=${s:wbase:CHAR_WINDOW}
			wsafe=$((wbase + CHAR_WINDOW - CHAR_LOOKAHEAD))
		fi
		c=${wbuf:i-wbase:1}
		if [ -n "$q" ]; then
			[ "$c" = "$q" ] && q=''
			i=$((i + 1))
			continue
		fi
		case $c in
		' ' | $'\t' | $'\n')
			if [ "$start" -ge 0 ]; then
				w=${s:start:i-start}
				printf '%s\0' "${w//[\'\"]/}"
				start=-1
			fi
			;;
		"'" | '"')
			[ "$start" -lt 0 ] && start=$i
			q=$c
			;;
		\\)
			[ "$start" -lt 0 ] && start=$i
			i=$((i + 1))
			;;
		*) [ "$start" -lt 0 ] && start=$i ;;
		esac
		i=$((i + 1))
	done
	if [ "$start" -ge 0 ]; then
		w=${s:start}
		printf '%s\0' "${w//[\'\"]/}"
	fi
	return 0
}

strip_quotes() { # sets UNQUOTED
	UNQUOTED=$1
	UNQUOTED=${UNQUOTED%\"}
	UNQUOTED=${UNQUOTED#\"}
	UNQUOTED=${UNQUOTED%\'}
	UNQUOTED=${UNQUOTED#\'}
}
UNQUOTED=''

# Emit the target of each UNQUOTED input redirection, one per line. POSITION, not
# presence: the shipped scan read the word-split argv, so any `<` anywhere was a
# redirection and `git commit -m "never read < .env directly"` was BLOCKED on its own
# commit message — AMH ledger row D007 verbatim, the exact false-positive class this
# guard's design rules open with. Quoted text is data. `<<` and `<<<` redirect from text
# rather than
# from a file and are skipped; a digit prefix (`0<file`) is just an fd number and
# needs no special case, since the `<` is what this scan looks for.
redirect_targets() {
	local s=$1
	local i=0 n=${#s} c q='' rest target
	local wbase=0 wbuf='' wsafe=0
	case $s in *'<'*) ;; *) return 0 ;; esac
	while [ "$i" -lt "$n" ]; do
		if [ "$i" -ge "$wsafe" ]; then # windowed scan — see CHAR_WINDOW
			wbase=$i
			wbuf=${s:wbase:CHAR_WINDOW}
			wsafe=$((wbase + CHAR_WINDOW - CHAR_LOOKAHEAD))
		fi
		c=${wbuf:i-wbase:1}
		if [ -n "$q" ]; then
			[ "$c" = "$q" ] && q=''
			i=$((i + 1))
			continue
		fi
		case $c in
		"'" | '"') q=$c ;;
		\\) i=$((i + 1)) ;;
		'<')
			if [ "${wbuf:i-wbase+1:1}" = '<' ]; then
				i=$((i + 1)) # a here-doc or here-string, not a file
			else
				rest=${wbuf:i-wbase+1}
				rest=${rest#"${rest%%[![:space:]]*}"}
				target=${rest%%[[:space:];|&<>)(\`]*}
				strip_quotes "$target"
				[ -n "$UNQUOTED" ] && printf '%s\0' "$UNQUOTED"
			fi
			;;
		esac
		i=$((i + 1))
	done
}

# Block if any operand names a credential file. Used for the commands that PRINT
# what they read; write DESTINATIONS are filtered out by the caller, because
# `cp .env.example .env` exposes no value and "Reading `.env` exposes credential
# values" is a reason that command does not earn.
reads_env_operand() {
	local a
	for a in "$@"; do
		case $a in -*) continue ;; esac
		strip_quotes "$a"
		if names_env_file "$UNQUOTED"; then
			BLOCK_REASON="Reading \`$UNQUOTED\` exposes credential values (AMH P17). Check key presence instead, or ask the owner for a narrower evidence contract via the Owner queue."
			return 1
		fi
	done
	return 0
}

# The same check for a command that COPIES rather than prints. `cp .env /tmp/e` puts
# no value in the transcript, so the reading reason would assert behaviour the command
# lacks — the false-reason class again — but it does move credentials to a path
# nothing here watches, which is its own thing worth blocking.
copies_env_operand() {
	local a
	for a in "$@"; do
		case $a in -*) continue ;; esac
		strip_quotes "$a"
		if names_env_file "$UNQUOTED"; then
			BLOCK_REASON="\`$cmd\` copies \`$UNQUOTED\` — credential values — to another path, where no rail here can see what reads them next (AMH P17). Leave the file where it is; if a tool needs the values, let it read the file itself, or ask the owner for a narrower evidence contract via the Owner queue."
			return 1
		fi
	done
	return 0
}

check_segment() {
	local raw=$1
	local words=() i=0 w
	while IFS= read -r -d '' w; do words+=("$w"); done < <(split_words "$raw")

	# A redirection reaches the same file a reader command would, from ANY command:
	# `tr "\0" "\n" < /proc/self/environ` names no reader at all.
	local target
	while IFS= read -r -d '' target; do
		if names_env_file "$target"; then
			BLOCK_REASON="Redirecting from \`$target\` feeds credential values into the command (AMH P17). Check key presence instead, or ask the owner for a narrower evidence contract via the Owner queue."
			return 1
		fi
	done < <(redirect_targets "$raw")

	# Strip leading variable assignments and transparent prefixes so that
	# `env FOO=1 git push --force` is judged as a git command, not an env dump.
	while [ "$i" -lt "${#words[@]}" ]; do
		w=${words[$i]}
		case $w in
		*=*) i=$((i + 1)) ;;
		sudo | nohup | nice | time | command | builtin | exec) i=$((i + 1)) ;;
		env)
			# `env` with an assignment or a command after it is a prefix, not a dump.
			if [ $((i + 1)) -lt "${#words[@]}" ]; then
				case ${words[$((i + 1))]} in
				-*)
					BLOCK_REASON="\`env\` dumps the session environment, which carries credentials (AMH P17). Report key PRESENCE only, e.g. \`[ -n \"\${MY_KEY:-}\" ] && echo set\`."
					return 1
					;;
				*) i=$((i + 1)) ;;
				esac
			else
				BLOCK_REASON="\`env\` dumps the session environment, which carries credentials (AMH P17). Report key PRESENCE only, e.g. \`[ -n \"\${MY_KEY:-}\" ] && echo set\`."
				return 1
			fi
			;;
		*) break ;;
		esac
	done
	[ "$i" -lt "${#words[@]}" ] || return 0

	local cmd=${words[$i]}
	cmd=${cmd##*/}
	local args=("${words[@]:$((i + 1))}")

	case $cmd in
	printenv)
		BLOCK_REASON="\`printenv\` prints credential values (AMH P17). Report key PRESENCE only — never a value, prefix, suffix, length or hash."
		return 1
		;;
	set | export | declare | typeset | readonly)
		# A shell builtin can dump the whole environment without going near `env`.
		if is_env_dump_builtin "$cmd" ${args[@]+"${args[@]}"}; then
			BLOCK_REASON="\`$cmd\` in this form prints every variable's VALUE, which dumps the session's credentials (AMH P17). Report key PRESENCE only, e.g. \`[ -n \"\${MY_KEY:-}\" ] && echo set\`."
			return 1
		fi
		# `declare -p NAME` PRINTS that variable's value. `export NAME` does not —
		# it sets an attribute and prints nothing, so blocking it would be both a
		# false positive and a block reason asserting behaviour the command lacks.
		# `-p` is what separates them, so `export -p NAME` and `readonly -p NAME`
		# belong on the printing side.
		case $cmd in declare | typeset | export | readonly) ;; *) return 0 ;; esac
		local a prints=1
		for a in ${args[@]+"${args[@]}"}; do
			case $a in -*p*) prints=0 ;; esac
		done
		[ "$prints" -eq 0 ] || return 0
		for a in ${args[@]+"${args[@]}"}; do
			case $a in -* | *=*) continue ;; esac
			if is_secret_name "$a"; then
				BLOCK_REASON="\`$cmd $a\` prints that credential's value (AMH P17). Never a value, prefix, suffix, length or hash — report presence only."
				return 1
			fi
		done
		;;
	echo | printf | print)
		# The commonest leak shape by far: an agent echoing a credential to see it.
		# Scan the RAW argument text, not the split words: word-splitting destroys the
		# quoting context, and quoting is the entire difference between printing a
		# credential and printing advice about one.
		if expands_secret_var "${raw#*"$cmd"}"; then
			BLOCK_REASON="That command expands a credential-shaped variable into output (AMH P17) — never print a value, prefix, suffix, length or hash. Report presence only: \`[ -n \"\${MY_KEY:-}\" ] && echo set\`. If a diagnostic seems to need the value, that is an Owner-queue question, not raw output."
			return 1
		fi
		;;
	git)
		# `push` must be the SUBCOMMAND, not any word anywhere in the line — otherwise
		# `git commit -m "never git push --force"` trips the rail on its own message.
		local j=0 a
		while [ "$j" -lt "${#args[@]}" ]; do
			case ${args[$j]} in
			-C | -c | --exec-path) j=$((j + 2)) ;;
			-*) j=$((j + 1)) ;;
			*) break ;;
			esac
		done
		[ "$j" -lt "${#args[@]}" ] && [ "${args[$j]}" = push ] || return 0
		for a in "${args[@]:$((j + 1))}"; do
			case $a in
			--force | -f | --force-with-lease | --force-with-lease=* | --force-if-includes)
				BLOCK_REASON="Force-push is denied (AMH P7): pushed checkpoints are immutable. If the branch diverged, merge the default branch in — never rewrite pushed history. A history rewrite is owner-executed and only for a leaked-credential incident."
				return 1
				;;
			--mirror | --all)
				BLOCK_REASON="\`git push $a\` pushes refs you did not name — including \`$DEFAULT_BRANCH\` (and, for \`--mirror\`, force-updating and deleting them). Both are denied (AMH P7, P13). Push one branch explicitly: \`git push -u origin $BRANCH_PREFIX/<codename>\`."
				return 1
				;;
			# A leading `+` on a refspec IS a force push — `git push origin +main` needs
			# no flag to rewrite the default branch, and passed both rail layers.
			+*)
				# `+main` is BOTH violations at once. This arm sits above the
				# default-branch patterns, so it must say so itself — a reason that
				# names only P7 and says "drop the +" points the agent at
				# `git push origin main`, which the very next rail denies. One-step
				# self-correction is the whole point of an instructive guard.
				case ${a#+} in
				"$DEFAULT_BRANCH" | "refs/heads/$DEFAULT_BRANCH" | *:"$DEFAULT_BRANCH" | *:"refs/heads/$DEFAULT_BRANCH")
					BLOCK_REASON="\`$a\` is denied twice over: the leading \`+\` force-pushes (AMH P7, pushed checkpoints are immutable) and the target is \`$DEFAULT_BRANCH\` (AMH P13). Push your session branch instead: \`git push -u origin $BRANCH_PREFIX/<codename>\`. The owner merges via squash PR."
					;;
				*)
					BLOCK_REASON="A leading \`+\` on the refspec \`$a\` force-pushes it (AMH P7), and pushed checkpoints are immutable. Drop the \`+\`; if the branch diverged, merge the default branch in — never rewrite pushed history."
					;;
				esac
				return 1
				;;
			-*) ;;
			"$DEFAULT_BRANCH" | "refs/heads/$DEFAULT_BRANCH" | *:"$DEFAULT_BRANCH" | *:"refs/heads/$DEFAULT_BRANCH")
				BLOCK_REASON="Pushing to \`$DEFAULT_BRANCH\` is denied (AMH P13). Push your session branch instead: \`git push -u origin $BRANCH_PREFIX/<codename>\`. The owner merges via squash PR."
				return 1
				;;
			esac
		done
		;;
	source | .)
		# Sourcing does not print anything, so the reader reason would be false — but
		# it loads every credential in the file into the shell, where any later command
		# (including one this guard never sees) can print them.
		local a
		for a in ${args[@]+"${args[@]}"}; do
			case $a in -*) continue ;; esac
			strip_quotes "$a"
			if names_env_file "$UNQUOTED"; then
				BLOCK_REASON="\`$cmd $UNQUOTED\` loads every credential in that file into the session environment (AMH P17), where any later command can print them. Let the tool that needs them read the file itself (e.g. \`--env-file\`), or ask the owner for a narrower evidence contract via the Owner queue."
				return 1
			fi
		done
		;;
	# Commands that PRINT what they read. `wc`, `md5sum` and friends are here because
	# a length and a hash are exactly what P17 forbids reporting — not because they
	# show the file. This list is a list: after the one-time `.env` advisory is spent,
	# `python3 -c "open('.env')"` is an accepted miss and the prose must not claim
	# otherwise.
	cat | less | more | head | tail | bat | xxd | od | strings | nl | \
		grep | egrep | fgrep | rg | awk | cut | tr | base64 | uniq | \
		wc | md5sum | sha1sum | sha256sum | sha512sum | shasum | cksum | sum | cmp | diff)
		reads_env_operand ${args[@]+"${args[@]}"} || return 1
		;;
	sed)
		# `sed -i … .env` edits in place and prints NOTHING; every other form prints
		# what it reads.
		local a in_place=1
		for a in ${args[@]+"${args[@]}"}; do
			case $a in -i | -i.* | --in-place | --in-place=* | -*i) in_place=0 ;; esac
		done
		[ "$in_place" -eq 0 ] && return 0
		reads_env_operand ${args[@]+"${args[@]}"} || return 1
		;;
	sort)
		# `-o FILE` is a write destination, not a read.
		local a skip=1 ops=()
		for a in ${args[@]+"${args[@]}"}; do
			if [ "$skip" -eq 0 ]; then
				skip=1
				continue
			fi
			case $a in -o) skip=0 ;; -o*) ;; *) ops+=("$a") ;; esac
		done
		reads_env_operand ${ops[@]+"${ops[@]}"} || return 1
		;;
	cp | mv | install | tee | dd)
		# Write DESTINATIONS are not reads. `cp .env.example .env`, `tee .env` and
		# `sed -i … .env` put no credential value anywhere it was not already, and
		# were blocked with "Reading `.env` exposes credential values" — a reason the
		# command has not earned. Only the SOURCE side is a read.
		local a ops=()
		case $cmd in
		tee) ;; # every operand is a destination
		dd)
			for a in ${args[@]+"${args[@]}"}; do
				case $a in if=*) ops+=("${a#if=}") ;; esac
			done
			;;
		*)
			# `-t DIR` / `--target-directory=DIR` INVERT the operand order: every
			# operand is then a source, and dropping the last one as "the destination"
			# hands `cp -t /tmp .env` a free pass.
			local to_dir=1 skip=1
			for a in ${args[@]+"${args[@]}"}; do
				if [ "$skip" -eq 0 ]; then
					skip=1
					continue
				fi
				case $a in
				-t | --target-directory)
					to_dir=0
					skip=0
					;;
				--target-directory=*) to_dir=0 ;;
				-*) ;;
				*) ops+=("$a") ;;
				esac
			done
			# the last operand is the destination, unless a flag already named one
			if [ "$to_dir" -ne 0 ]; then
				if [ "${#ops[@]}" -gt 1 ]; then
					unset "ops[$((${#ops[@]} - 1))]"
				else
					ops=() # a lone operand is the destination, or the command is malformed
				fi
			fi
			;;
		esac
		copies_env_operand ${ops[@]+"${ops[@]}"} || return 1
		;;
	esac
	return 0
}

leading_command() {
	local raw=$1 w i=0
	local words=()
	while IFS= read -r -d '' w; do words+=("$w"); done < <(split_words "$raw")
	while [ "$i" -lt "${#words[@]}" ]; do
		w=${words[$i]}
		case $w in
		*=*) i=$((i + 1)) ;;
		sudo | nohup | nice | time | command | builtin | exec) i=$((i + 1)) ;;
		env)
			# Mirror check_segment's transparent-prefix treatment for ordinary
			# `env NAME=value cmd` forms. An `env` dump is not the advisory's subject.
			if [ $((i + 1)) -lt "${#words[@]}" ]; then
				case ${words[$((i + 1))]} in -*) break ;; *) i=$((i + 1)) ;; esac
			else
				break
			fi
			;;
		*) break ;;
		esac
	done
	[ "$i" -lt "${#words[@]}" ] || return 1
	printf '%s' "${words[$i]##*/}"
}

is_destructive_segment() {
	local raw=$1 w cmd recursive=1 force=1 descend=1 i=0
	local words=()
	while IFS= read -r -d '' w; do words+=("$w"); done < <(split_words "$raw")
	# Find the same leading command that leading_command reports, without treating
	# later quoted prose or operands as commands.
	while [ "$i" -lt "${#words[@]}" ]; do
		w=${words[$i]}
		case $w in
		*=* | sudo | nohup | nice | time | command | builtin | exec) i=$((i + 1)) ;;
		env)
			if [ $((i + 1)) -lt "${#words[@]}" ]; then
				case ${words[$((i + 1))]} in -*) break ;; *) i=$((i + 1)) ;; esac
			else break
			fi
			;;
		*) break ;;
		esac
	done
	[ "$i" -lt "${#words[@]}" ] || return 1
	cmd=${words[$i]##*/}
	i=$((i + 1))
	case $cmd in
	rm)
		for w in "${words[@]:i}"; do
			case $w in
			--recursive) recursive=0 ;;
			--force) force=0 ;;
			--) break ;;
			[^-]*) ;;
			-*)
				case ${w#-} in *[rR]*) recursive=0 ;; esac
				case ${w#-} in *f*) force=0 ;; esac
				;;
			esac
			done
		[ "$recursive" -eq 0 ] && [ "$force" -eq 0 ]
		;;
	git)
		# Skip git's global options, then require the clean subcommand and short
		# options containing both -f and -d. Clusters may be ordered or split.
		while [ "$i" -lt "${#words[@]}" ]; do
			w=${words[$i]}
			case $w in -C | -c | --git-dir | --work-tree) i=$((i + 2)) ;; -*) i=$((i + 1)) ;; *) break ;; esac
		done
		[ "$i" -lt "${#words[@]}" ] && [ "${words[$i]}" = clean ] || return 1
		i=$((i + 1))
		for w in "${words[@]:i}"; do
			case $w in
			-n | --dry-run) return 1 ;;
			--force) force=0 ;;
			--) break ;;
			[^-]*) ;;
			--*) ;;
			-*)
				case ${w#-} in *n*) return 1 ;; esac
				case ${w#-} in *f*) force=0 ;; esac
				case ${w#-} in *d*) descend=0 ;; esac
				;;
			esac
			done
		[ "$force" -eq 0 ] && [ "$descend" -eq 0 ]
		;;
	*) return 1 ;;
	esac
}

is_destructive_command() {
	local cmd=$1 seg
	cmd=$(strip_heredocs "$cmd")
	while IFS= read -r -d '' seg; do
		[ -n "${seg// /}" ] || continue
		is_destructive_segment "$seg" && return 0
	done < <(split_segments "$cmd")
	return 1
}

warn_ladder_tail() {
	local cmd=$1 seg lead prev_ladder=0
	case $cmd in *ladder.sh*tail*) ;; *) return 0 ;; esac
	# Warn only for the ordinary mistaken shape: a direct ladder invocation whose
	# output is piped to tail. Reuse the shell-ish segment and word scanners so quoted
	# prose like a commit message stays data, not a warning.
	cmd=$(strip_heredocs "$cmd")
	while IFS= read -r -d '' seg; do
		[ -n "${seg// /}" ] || continue
		lead=$(leading_command "$seg") || { prev_ladder=0; continue; }
		# split_segments treats the `&` in `2>&1` as an operator, yielding a bare
		# file-descriptor segment between the ladder and the real pipe target. Ignore
		# that artifact so the common `2>&1 | tail` spelling still warns.
		[ "$prev_ladder" -eq 1 ] && case $lead in [0-9]*) continue ;; esac
		if [ "$prev_ladder" -eq 1 ] && [ "$lead" = tail ]; then
			WARN_REASON="Running the AMH ladder through \`tail\` can hide the ladder exit status. Run \`scripts/ladder.sh\` directly when verifying; use a separate read-only command only after the direct run."
			return 0
		fi
		case $lead in ladder.sh) prev_ladder=1 ;; *) prev_ladder=0 ;; esac
	done < <(split_segments "$cmd")
}

check_command() {
	local cmd=$1
	local seg
	BLOCK_REASON=''
	WARN_REASON=''
	ADVISORY_REASON=''
	DOTENV_ADVISORY_REASON=''
	if needs_one_time_advisory dotenv "$cmd"; then
		BLOCK_REASON=$ADVISORY_REASON
		return 1
	fi
	if needs_one_time_advisory destructive "$cmd"; then
		BLOCK_REASON=$ADVISORY_REASON
		return 1
	fi
	warn_ladder_tail "$cmd"
	cmd=$(strip_heredocs "$cmd")
	while IFS= read -r -d '' seg; do
		[ -n "${seg// /}" ] || continue
		check_segment "$seg" || return 1
	done < <(split_segments "$cmd")
	return 0
}

# --- hook payload -----------------------------------------------------------
extract_command() { # fail-open: print nothing if the payload is not what we expect
	local payload=$1
	if command -v python3 >/dev/null 2>&1; then
		printf '%s' "$payload" | python3 -c 'import json,sys
try:
    d = json.load(sys.stdin)
    print(d.get("tool_input", {}).get("command", ""))
except Exception:
    pass' 2>/dev/null
	else
		printf '%s' "$payload" | sed -n 's/.*"command"[[:space:]]*:[[:space:]]*"\(.*\)".*/\1/p' | head -1
	fi
}

run_hook() {
	local payload cmd
	payload=$(cat) || exit 0
	cmd=$(extract_command "$payload")
	[ -n "$cmd" ] || exit 0 # malformed or non-Bash tool: fail open
	if ! check_command "$cmd"; then
		printf 'BLOCKED by the AMH command guard.\n\n%s\n' "$BLOCK_REASON" >&2
		exit 2
	fi
	[ -n "$WARN_REASON" ] && printf 'WARNING from the AMH command guard.\n\n%s\n' "$WARN_REASON" >&2
	exit 0
}

# --- self-test --------------------------------------------------------------
ST_FAILS=0
st_blocked() {
	if check_command "$1"; then
		printf 'SELF-TEST FAIL: should have been BLOCKED: %s\n' "$1" >&2
		ST_FAILS=$((ST_FAILS + 1))
	fi
}
st_allowed() {
	if ! check_command "$1"; then
		printf 'SELF-TEST FAIL: should have been ALLOWED: %s\n   reason given: %s\n' "$1" "$BLOCK_REASON" >&2
		ST_FAILS=$((ST_FAILS + 1))
	elif [ -n "$WARN_REASON" ]; then
		printf 'SELF-TEST FAIL: should have been ALLOWED WITHOUT WARNING: %s\n   warning given: %s\n' "$1" "$WARN_REASON" >&2
		ST_FAILS=$((ST_FAILS + 1))
	fi
}
st_dotenv_advisory_once() {
	local state old_set old_state
	old_set=${DOTENV_ADVISORY_STATE+x}
	old_state=${DOTENV_ADVISORY_STATE:-}
	state=$(mktemp "${TMPDIR:-/tmp}/amh-dotenv-advisory-test.XXXXXX") || exit 1
	rm -f -- "$state"
	DOTENV_ADVISORY_STATE=$state
	if check_command "$1"; then
		printf 'SELF-TEST FAIL: should have had one-time .env advisory: %s\n' "$1" >&2
		ST_FAILS=$((ST_FAILS + 1))
	elif [ -z "$DOTENV_ADVISORY_REASON" ]; then
		printf 'SELF-TEST FAIL: .env advisory did not explain itself: %s\n' "$1" >&2
		ST_FAILS=$((ST_FAILS + 1))
	elif ! check_command "$1"; then
		printf 'SELF-TEST FAIL: second .env advisory attempt should have reached normal rails: %s\n   reason given: %s\n' "$1" "$BLOCK_REASON" >&2
		ST_FAILS=$((ST_FAILS + 1))
	fi
	rm -f -- "$state"
	if [ -n "$old_set" ]; then
		DOTENV_ADVISORY_STATE=$old_state
	else
		unset DOTENV_ADVISORY_STATE
	fi
}

st_destructive_advisory_once() {
	local state old_set old_state
	old_set=${DESTRUCTIVE_ADVISORY_STATE+x}
	old_state=${DESTRUCTIVE_ADVISORY_STATE:-}
	state=$(mktemp "${TMPDIR:-/tmp}/amh-destructive-advisory-test.XXXXXX") || exit 1
	rm -f -- "$state"
	DESTRUCTIVE_ADVISORY_STATE=$state
	if check_command "$1"; then
		printf 'SELF-TEST FAIL: should have had one-time destructive advisory: %s\n' "$1" >&2
		ST_FAILS=$((ST_FAILS + 1))
	elif [ -z "$ADVISORY_REASON" ]; then
		printf 'SELF-TEST FAIL: destructive advisory did not explain itself: %s\n' "$1" >&2
		ST_FAILS=$((ST_FAILS + 1))
	elif ! check_command "$1"; then
		printf 'SELF-TEST FAIL: second destructive attempt should have reached normal rails: %s\n   reason given: %s\n' "$1" "$BLOCK_REASON" >&2
		ST_FAILS=$((ST_FAILS + 1))
	fi
	rm -f -- "$state"
	if [ -n "$old_set" ]; then DESTRUCTIVE_ADVISORY_STATE=$old_state; else unset DESTRUCTIVE_ADVISORY_STATE; fi
}

st_warn_allowed() {
	if ! check_command "$1"; then
		printf 'SELF-TEST FAIL: should have been ALLOWED WITH WARNING: %s\n   reason given: %s\n' "$1" "$BLOCK_REASON" >&2
		ST_FAILS=$((ST_FAILS + 1))
	elif [ -z "$WARN_REASON" ]; then
		printf 'SELF-TEST FAIL: should have WARNED: %s\n' "$1" >&2
		ST_FAILS=$((ST_FAILS + 1))
	fi
}

# shellcheck disable=SC2016  # every fixture below is command TEXT to be judged, not
# text to be evaluated: single quotes are what stop `$GITHUB_TOKEN` expanding in this
# shell, and expanding it would test nothing. Scoped to this function on purpose.
self_test() {
	local old_dotenv_advisory_state_set=${DOTENV_ADVISORY_STATE+x}
	local old_dotenv_advisory_state=${DOTENV_ADVISORY_STATE:-}
	local self_dotenv_advisory_state
	self_dotenv_advisory_state=$(mktemp "${TMPDIR:-/tmp}/amh-dotenv-advisory-self-test.XXXXXX") || exit 1
	rm -f -- "$self_dotenv_advisory_state"
	DOTENV_ADVISORY_STATE=$self_dotenv_advisory_state
	local old_destructive_advisory_state_set=${DESTRUCTIVE_ADVISORY_STATE+x}
	local old_destructive_advisory_state=${DESTRUCTIVE_ADVISORY_STATE:-}
	local self_destructive_advisory_state
	self_destructive_advisory_state=$(mktemp "${TMPDIR:-/tmp}/amh-destructive-advisory-self-test.XXXXXX") || exit 1
	rm -f -- "$self_destructive_advisory_state"
	DESTRUCTIVE_ADVISORY_STATE=$self_destructive_advisory_state

	# --- must block: the rails themselves
	st_blocked 'git push --force origin feature'
	st_blocked 'git push -f origin feature'
	st_blocked 'git push --force-with-lease'
	st_blocked "git push origin $DEFAULT_BRANCH"
	st_blocked "git push origin HEAD:$DEFAULT_BRANCH"
	st_blocked "git push origin refs/heads/$DEFAULT_BRANCH"
	st_blocked 'env'
	st_blocked 'env -0'
	st_blocked 'printenv'
	st_blocked 'printenv AWS_SECRET_ACCESS_KEY'
	st_blocked 'cat .env'
	st_blocked 'cat config/.env.production'
	st_blocked 'ls -la && git push --force origin x'
	st_blocked 'make build; printenv'
	st_blocked 'echo hi | cat .env'
	st_blocked 'RESULT=$(git push --force origin x)'
	st_blocked 'git -C /some/repo push --force origin x'
	st_blocked 'cat ".env"'
	st_blocked 'sudo printenv'
	# Whole-environment dumps that never mention `env`.
	st_blocked 'set'
	st_blocked 'export -p'
	st_blocked 'declare -x'
	st_blocked 'typeset -x'
	st_blocked 'declare -p'
	st_blocked 'cat /proc/self/environ'
	st_blocked 'strings /proc/1/environ'
	# Printing one credential's value.
	st_blocked 'echo $GITHUB_TOKEN'
	st_blocked 'echo "$AWS_SECRET_ACCESS_KEY"'
	st_blocked 'echo "${OPENAI_API_KEY}"'
	st_blocked 'printf "%s\n" "$MY_PASSWORD"'
	st_blocked 'echo "token is $npm_token"'
	st_blocked 'echo "${DEPLOY_PRIVATE_KEY:0:4}"'
	st_blocked 'declare -p GITHUB_TOKEN'
	# The same file through readers other than `cat`, and through a redirection.
	st_blocked 'grep -a . /proc/self/environ'
	st_blocked 'awk 1 /proc/self/environ'
	st_blocked 'cp /proc/self/environ /tmp/e'
	st_blocked 'tr "\0" "\n" < /proc/self/environ'
	st_blocked 'grep DATABASE_URL .env'
	st_blocked 'while read -r l; do echo "$l"; done < .env'
	# A here-STRING opens no here-document body. Treating it as one discarded every
	# later line unjudged and voided the two oldest rails outright.
	st_blocked 'grep -q x <<< "$v"
git push --force origin main'
	st_blocked 'grep -q x <<<"$v"; git push --force origin feature'
	st_blocked 'echo $((1 << 8)); printenv'
	# Body mode only ever discards SUBSEQUENT lines, so a single-line fixture cannot
	# exercise it — every one-line case above passes against the broken script too.
	# These are the ones that discriminate: a shift with a VARIABLE operand, and the
	# backslash-quoted delimiter form, each followed by a line that must still be judged.
	st_blocked 'echo $((mask << bits))
printenv'
	st_blocked 'x=$((1<<n))
git push --force origin main'
	st_blocked 'cat <<\EOF
hello
EOF
printenv'
	st_blocked 'echo "write <<EOF into the doc"; cat .env'
	# Redirections the word-split scan never saw.
	st_blocked 'tr "\0" "\n" 0</proc/self/environ'
	st_blocked 'cat<.env'
	# Length, and the presence-check idiom's evil twin.
	st_blocked 'echo "${#GITHUB_TOKEN}"'
	st_blocked 'echo "${GITHUB_TOKEN:-none}"'
	st_blocked 'readonly -p'
	st_blocked 'readonly'
	st_blocked 'export -p GITHUB_TOKEN'
	# Refspec-level force, and the pushes that name no ref at all.
	st_blocked "git push origin +$DEFAULT_BRANCH"
	st_blocked "git push origin +HEAD:$DEFAULT_BRANCH"
	st_blocked 'git push origin +feature'
	st_blocked 'git push --mirror origin'
	st_blocked 'git push --all origin'
	# Sourcing loads the values into the shell.
	st_blocked 'source .env'
	st_blocked '. ./.env'
	# Readers that print a hash or a length — exactly what P17 forbids reporting.
	st_blocked 'wc -c .env'
	st_blocked 'md5sum .env'
	st_blocked 'sha256sum config/.env.production'
	st_blocked 'cmp .env .env.example'
	# Write destinations are not reads, but sources still are.
	st_blocked 'cp .env /tmp/e'
	st_blocked 'cp -t /tmp .env'
	st_blocked 'cp --target-directory=/tmp .env'
	st_blocked 'mv -t /tmp .env'
	st_blocked 'tee /tmp/x < .env'
	st_blocked 'dd if=.env of=/tmp/e'
	st_blocked 'sed -n "/KEY/p" .env'
	st_blocked 'sort .env'
	st_dotenv_advisory_once 'python3 -c "open('"'"'.env'"'"')"'
	st_destructive_advisory_once 'rm -rf tmp/build'
	rm -f -- "$self_destructive_advisory_state"
	st_destructive_advisory_once 'rm -fr tmp/build'
	rm -f -- "$self_destructive_advisory_state"
	st_destructive_advisory_once 'rm -r -f tmp/build'
	rm -f -- "$self_destructive_advisory_state"
	st_destructive_advisory_once 'rm -f -r tmp/build'
	rm -f -- "$self_destructive_advisory_state"
	st_destructive_advisory_once 'rm tmp/build -rf'
	rm -f -- "$self_destructive_advisory_state"
	st_destructive_advisory_once 'git clean -fdx'
	rm -f -- "$self_destructive_advisory_state"
	st_destructive_advisory_once 'git clean -df'

	# --- must allow: the known false-positive classes.
	# Quoted text naming a forbidden command is DATA, not a command.
	st_allowed 'git commit -m "never git push --force on this repo"'
	st_allowed "git commit -m 'document why printenv is denied'"
	st_allowed 'echo "cat .env is forbidden by P17"'
	st_allowed 'grep -rn "printenv" docs/'
	st_allowed 'scripts/command-guard.sh --self-test'
	st_warn_allowed 'scripts/ladder.sh | tail -20'
	st_warn_allowed './scripts/ladder.sh --guards-only 2>&1 | tail -40'
	st_allowed 'git commit -m "document scripts/ladder.sh | tail warning"'
	st_allowed 'git commit -m "document rm -rf risk"'
	st_allowed 'rm file.txt'
	st_allowed 'git clean --force --dry-run'
	st_allowed 'git clean -nfd'
	# Prose naming a forbidden path.
	st_allowed 'grep -rn "force-push" docs/RUNBOOK.md'
	# Ordinary correct usage.
	st_allowed "git push -u origin $BRANCH_PREFIX/some-codename"
	st_allowed "git push -u origin $BRANCH_PREFIX/x && echo pushed"
	st_allowed 'env FOO=1 make test'
	st_allowed 'FOO=1 make test'
	st_allowed 'cat .env.example'
	st_allowed 'cat README.md'
	# Ordinary shell usage the dump rail must not swallow.
	st_allowed 'set -euo pipefail'
	st_allowed 'set -x'
	st_allowed 'export PATH=/usr/local/bin:$PATH'
	st_allowed 'export AMH_REMOTE=1'
	st_allowed 'declare -a items'
	st_allowed 'declare -x MY_FLAG=1'
	st_allowed 'export -f my_function'
	# The presence check the guard itself recommends, and non-secret expansions.
	st_allowed '[ -n "${MY_KEY:-}" ] && echo set'
	st_allowed 'echo "$HOME"'
	st_allowed 'echo "$AUTHOR wrote it"'
	st_allowed 'echo "$API_URL"'
	st_allowed 'printf "%s\n" "$BRANCH"'
	# A heredoc body is DATA. This is the shape that blocked the very commit
	# shipping this rail: backticks split segments, so `env` in prose became a
	# leading command. Both the quoted and unquoted delimiter forms.
	st_allowed "$(printf '%s\n' "git commit -F - <<'EOF'" 'The guard blocks `env`, `printenv` and `.env` reads.' 'EOF')"
	st_allowed "$(printf '%s\n' 'cat <<EOF >notes.md' 'Run `printenv` to see why this is denied.' 'EOF')"
	st_allowed "$(printf '%s\n' "cat <<-'EOF'" $'\tset' $'\tenv' $'\tEOF')"
	# ...but a real command AFTER the terminator is still judged.
	st_blocked "$(printf '%s\n' "git commit -F - <<'EOF'" 'prose about `env`' 'EOF' 'printenv')"
	# Prose naming the shapes, and the guard's own fixtures.
	st_allowed 'grep -rn "GITHUB_TOKEN" docs/'
	st_allowed 'git commit -m "block echo $GITHUB_TOKEN at the rail"'
	# Load-bearing against the false-positive classes this rail can produce.
	# Unexpanded text: advice about a credential is not a credential.
	st_allowed "echo 'Set \$GITHUB_TOKEN in your environment before running gh'"
	st_allowed "printf 'export \$NPM_TOKEN first\n'"
	st_allowed 'echo "remember to set \$GITHUB_TOKEN"'
	# Names that merely CONTAIN a secret word: a path, an identifier, a monkey.
	st_allowed 'echo "$SSH_KEY_PATH"'
	st_allowed 'echo "$AWS_ACCESS_KEY_ID"'
	st_allowed 'echo "$GPG_KEY_ID"'
	st_allowed 'echo "$KEYCLOAK_URL"'
	st_allowed 'echo "$PRIVATE_REPO_URL"'
	st_allowed 'printf "using keyring %s\n" "$KEYRING_BACKEND"'
	st_allowed 'echo "$MONKEY"'
	# Builtins that set an attribute and print nothing.
	st_allowed 'export GITHUB_TOKEN'
	st_allowed 'export NPM_TOKEN OPENAI_API_KEY'
	st_allowed 'declare -p my_array'
	# Names that END in a secret word but are ordinary program variables. Every
	# fixture above puts the benign word LAST, so they passed by construction and
	# hid the overshoot that shipped: `$key` and `$page_token` were both blocked.
	st_allowed 'echo "$key"'
	st_allowed 'echo "$token"'
	st_allowed 'echo "$sort_key"'
	st_allowed 'echo "$page_token"'
	st_allowed 'echo "$csrf_token"'
	st_allowed 'echo "$public_key"'
	st_allowed 'echo "$LICENSE_KEY"'
	st_allowed 'printf "%s\n" "${cursor_token}"'
	# ...but the exemption is for the lowercase program variable, not for the shell's
	# uppercase convention, where a bare `$TOKEN` is as credential-shaped as it gets.
	st_blocked 'echo "$TOKEN"'
	st_blocked 'echo "$KEY"'
	st_blocked 'declare -p TOKEN'
	# ...and the qualifier list is a list, not a wildcard.
	st_blocked 'echo "$api_key"'
	st_blocked 'echo "$access_token"'
	st_blocked 'echo "$refresh_token"'
	# `${VAR:+…}` and `${VAR+…}` expand to the literal — this IS the presence check
	# the block reason recommends, and blocking it contradicted the remedy.
	st_allowed 'echo "${GITHUB_TOKEN:+set}"'
	st_allowed 'echo "${GITHUB_TOKEN+present}"'
	st_allowed '[ -n "${AWS_SECRET_ACCESS_KEY:+x}" ] && echo set'
	# A here-string is data, and an arithmetic shift is arithmetic.
	st_allowed 'grep -q "cat .env" <<< "$line"'
	st_allowed 'echo $((1 << 8))'
	# A commit BODY is one quoted argument. Splitting the guard's own segment stream on
	# newlines re-split it and made the body's second line a leading command — that is
	# the AMH ledger row D007 class, arriving through the transport rather than the scan.
	st_allowed 'git commit -m "Fix the guard.

cat .env was blocked by its own message.
"'
	st_allowed 'git commit -m "line one
git push --force origin main
"'
	# `<` inside quoted text is prose, not a redirection (AMH ledger row D007).
	st_allowed 'git commit -m "never read < .env directly"'
	st_allowed "git commit -m 'use < .env nowhere'"
	st_allowed 'echo "a < b"'
	# Write destinations expose nothing; the in-place edit prints nothing.
	st_allowed 'cp .env.example .env'
	st_allowed 'sed -i "s/x/y/" .env'
	st_allowed 'tee .env'
	st_allowed 'sort -o .env /tmp/pairs'
	# Ordinary pushes that merely LOOK like the new rails.
	st_allowed "git push -u origin $BRANCH_PREFIX/mirror-work"
	st_allowed "git push -u origin $BRANCH_PREFIX/x:$BRANCH_PREFIX/x"
	# Readers pointed at ordinary files, and a redirection from one.
	st_allowed 'awk 1 README.md'
	st_allowed 'wc -l README.md'
	st_allowed 'md5sum harness/dist/AMH.md'
	st_allowed 'tr "a" "b" < README.md'
	st_allowed 'sort docs/LEDGER.md'
	# A branch whose name merely CONTAINS the default branch name.
	st_allowed "git push -u origin ${DEFAULT_BRANCH}tenance"
	st_allowed "git push -u origin $BRANCH_PREFIX/$DEFAULT_BRANCH-cleanup"
	# Fail-open on an empty or odd command.
	st_allowed ''
	st_allowed '   '

	rm -f -- "$self_dotenv_advisory_state"
	if [ -n "$old_dotenv_advisory_state_set" ]; then
		DOTENV_ADVISORY_STATE=$old_dotenv_advisory_state
	else
		unset DOTENV_ADVISORY_STATE
	fi
	rm -f -- "$self_destructive_advisory_state"
	if [ -n "$old_destructive_advisory_state_set" ]; then
		DESTRUCTIVE_ADVISORY_STATE=$old_destructive_advisory_state
	else
		unset DESTRUCTIVE_ADVISORY_STATE
	fi
	if [ "$ST_FAILS" -ne 0 ]; then
		printf 'command-guard.sh self-test: %d failure(s)\n' "$ST_FAILS" >&2
		return 1
	fi
	printf 'command-guard.sh self-test: ok\n'
}

case "${1:-}" in
"") run_hook ;;
--command)
	if check_command "${2:-}"; then
		[ -n "$WARN_REASON" ] && printf 'WARNING from the AMH command guard.\n\n%s\n' "$WARN_REASON" >&2
		exit 0
	fi
	printf 'BLOCKED by the AMH command guard.\n\n%s\n' "$BLOCK_REASON" >&2
	exit 2
	;;
--self-test) self_test ;;
*)
	printf 'usage: %s [--command CMD|--self-test]\n' "$0" >&2
	exit 2
	;;
esac
