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
# The SAME script has a second entry point that is not driven by the agent at all:
# `--pre-push` reads git's pre-push stdin and judges each ref (see the pre-push rail
# below). Git invokes it through `.git/hooks/pre-push`, so it binds whatever agent — or
# none — is driving the shell, closing the hole this script's hook mode leaves open for
# an agent whose harness runs no pre-execution hook. It is a guardrail, not a boundary:
# `--no-verify` skips it, and it sees git-CLI pushes only, never a push made through a
# forge API. NEITHER rail carries a branch-prefix check (AMH ledger row DA022).
#
# Design rules this guard is bound by — each one paid for in false positives:
#   * Judge only the LEADING command of each simple-command segment, with quoting
#     respected. Text that merely CONTAINS a forbidden command — a commit message, a
#     doc heredoc, this script's own CLI — must never trip it.
#   * Target agent MISTAKES, not evasion. Quoting and prefix tricks are accepted
#     misses; the layers beneath catch those.
#   * Fail OPEN on malformed input. A guard that bricks every command gets disabled,
#     not fixed. Read that as written: it is about YOUR command being odd, and it is not
#     a licence for THIS SCRIPT to report a clean read of something it never read. When
#     the parser hands back no words for text that plainly has some, nothing was judged,
#     and an unjudged command is blocked with a reason naming the defect — see
#     `parse_produced_nothing` and AMH ledger row DC002. The two states used to share one
#     exit path, and eighteen shipped fixtures went red on stock macOS Bash 3.2 and green
#     again on a re-run at the same commit before anyone could tell them apart.
#
# WHAT THIS GUARD DOES NOT CATCH — the consolidated list, so no one has to reconstruct
# it from the per-scanner notes below and no one mistakes this script for a vault. Each
# scanner still carries its own `Accepted miss:` note at the point it applies; this block
# is the index, and it is deliberately exhaustive about the categories rather than about
# every spelling inside them:
#
#   * SECRET-FILE NAMES ARE A LIST TOO. The block tier fires on `.env` and its path forms,
#     `/proc/*/environ`, and the OpenSSH private keys `id_rsa`, `id_dsa`, `id_ecdsa`,
#     `id_ed25519` and the `_sk` variants — nothing else. A key stored under any other name
#     (`deploy_key`, `id_rsa.bak`, `~/.ssh/work`) is not recognised, and neither are the
#     other private-key containers (`.p12`, `.pfx`, `.jks`, `.keystore`): they carry no
#     name convention this scanner could read. `.pem` and `.key` are handled one tier down,
#     as a one-time advisory rather than a block, because both are CONTAINER extensions
#     rather than secret markers — `fullchain.pem`, `cert.pem` and a CA bundle are public
#     by design, and a block reason claiming they expose credential values would be false
#     about the commonest file bearing the extension.
#   * INTERPRETERS. The secret-file scanners recognise a file read through an enumerated
#     list of reader commands (`cat`, `grep`, `awk`, `wc`, `md5sum` and about thirty more)
#     or a `<` redirection. One-time advisories now block the first command text that names
#     `.env`, `.pem` or `.key`, but after that speed bump the list is still a list, not a
#     category: `python3 -c "open('.env')"`, `perl -e`, `node -e`, `ruby -e` and every
#     other interpreter NOT on it reach the file unjudged. This remains the widest hole
#     and it is structural —
#     enumerating interpreters would not close it, since each has unbounded ways to spell
#     a read. Note the shape of the miss for a listed one: `awk '{print}' .env` is blocked
#     because `awk` leads the segment, while `awk 'BEGIN{while((getline<".env")>0)print}'`
#     hides the read inside the program text and is not.
#   * WRAPPERS — but read which ones. `check_segment` STRIPS a set of transparent prefixes
#     and judges what follows, so `sudo cat .env`, `nohup cat .env`, `nice`, `time`,
#     `command`, `builtin`, `exec` and `env FOO=1 cat .env` ARE blocked. `env` is the one
#     with an option list, and it is read the way POSIX defines it: given a utility to run
#     it is a prefix, given none it PRINTS the environment and is blocked on its own
#     account — so `env`, `env -i`, `env -0`, `env FOO=1` and `env -u FOO` are dumps while
#     `env -u FOO cmd` and `env -i cmd` are prefixes whose `cmd` is judged. Three shapes of
#     that option walk are worth naming because they are the honest edges rather than the
#     rule: `env -S 'cat .env'` and `--split-string=` hide the utility inside a string this
#     guard cannot split, so they get past exactly as `bash -c` does; `env -u` and the other
#     missing-argument spellings are usage errors that run nothing and fail OPEN; and the
#     options that take a SEPARATE argument are a LIST, not a category — `-u`, `-C`, `-P`,
#     `-a` and long-option abbreviations of `--unset`, `--chdir` and `--argv0`. An option
#     outside it is read as a flag, so an `env` implementation with one this list does not
#     model would have `env -X VALUE cat .env` judge `VALUE` and miss the read. What gets
#     past is otherwise every wrapper outside that set: `xargs cat .env`, `timeout 5 cat .env`,
#     `ssh host cat .env`, `bash -c 'cat .env'`, and any shell function or alias standing in
#     for the command. Do not read this bullet as "wrappers defeat the guard" — several do
#     not, and deleting the strip loop because a comment said it was useless would remove
#     real coverage the self-test asserts.
#   * CONSTRUCTED AND ENCODED COMMANDS. `eval`, base64/hex payloads decoded at runtime,
#     and any command assembled from variables are text at scan time, not commands.
#   * A COMMAND SUBSTITUTION INSIDE DOUBLE QUOTES RUNS, AND NO WALKER LOOKS IN. Bash executes
#     `echo "$(git push --force origin main)"`, and every scanner here treats the whole
#     `"…"` as quoted text, so that push is ALLOWED where the unquoted `echo $(…)` is denied.
#     Named here rather than fixed because closing it means the walkers recursing into `$(`
#     while still treating ordinary quoted prose as data, which is a bigger change than the
#     backslash rule beside it and belongs to whoever takes it (AMH ledger row DC028).
#   * HEREDOCS AND LONG LINES. `cmd <<EOF` hides its body until the delimiter, and the
#     window-based scanners give up past `CHAR_LOOKAHEAD` characters — a variable name or
#     redirection target longer than the window is not classified.
#   * WHAT NO SCANNER LOOKS AT AT ALL. Container and service inspect output
#     (`docker inspect` and friends) is prose-only policy: no guard sees it, and none is
#     proposed, because it would block ordinary use to catch a shape the harness does not
#     run. The identity rules are likewise prose here — an identity not yet committed is
#     not on disk to check.
#   * THE DESTRUCTIVE RAIL IS A VERB LIST, and a short one: `rm -r -f`, `git clean -f -d`,
#     `git rm -r -f`, and the tree-mutating git verbs `worktree add|remove|move`,
#     `reset --hard`, `checkout|switch --force`, `restore`. Anything else that empties a
#     path reaches the filesystem unadvised — `mv` over a target, `truncate`, `dd`, `find
#     -delete`, `shred`, a `>` redirection, and every one of these run through an
#     interpreter. Two further limits INSIDE the list: the git verbs added after
#     `git clean` are armed only when the target is unknown at scan time (see
#     `operands_unknown_target`), so a fully literal `git reset --hard origin/main` is
#     deliberately silent; and `git checkout -- "$f"` carries no force flag and is not
#     recognised at all. The rail is a speed bump on the shapes an agent actually
#     mistypes, never an inventory of ways to lose a file.
#   * ITS DATA-PLANE TIER IS A SHORTER VERB LIST AND A WEAKER CLAIM. `supabase db reset`,
#     `prisma migrate reset`, `prisma db push --accept-data-loss|--force-reset`, `rails`/`rake`
#     `db:drop|db:reset|db:schema:load`, `dropdb`, and `psql -c` whose statement STARTS with
#     `DROP DATABASE`, `DROP SCHEMA` or `TRUNCATE` (every `-c` on the line is read, bundled
#     spellings like `-Atc` included; a statement that merely NAMES one of those verbs —
#     `SELECT * FROM truncate_log` — is a read and is not advised). Then `drizzle-kit push` and
#     its three dialect spellings, any `prisma migrate` subcommand carrying
#     `--shadow-database-url` (prisma RESETS whatever that flag names before replaying), and a
#     PACKAGE SCRIPT whose NAME is one of a short list (`db:push`, `db:reset`, `db:drop`,
#     `db:wipe`, `db:schema:load`, `migrate:reset`, `migrate:fresh` and a few more). Reached
#     through a bare invocation or one package runner: `npx`, `pnpx`, `bunx`, and
#     `npm`/`pnpm`/`yarn`/`bun` optionally followed by `exec`, `dlx`, `run`, `run-script` or
#     `x`. That strip runs before the whole dispatch, so it also widens the FILESYSTEM arms —
#     `npx rm -rf x` is now advised where it used to be a command called `npx`.
#     THE SCRIPT ARM JUDGES A NAME, WHICH IS WEAKER THAN EVERYTHING ABOVE IT AND IS NOT HIDDEN.
#     `npm run db:push` is the command the most widely reported incident of this kind actually
#     ran, and what it runs lives in the package manifest, which no scanner here opens (AMH
#     ledger row DB027). So the name is evidence and nothing else: a harmless `db:push` is
#     advised anyway, and a script that drops the database under a name like `seed`, `setup` or
#     `bootstrap` is invisible — as is a Makefile target, a justfile recipe, and every other
#     name outside the list. The advisory says this in its own text rather than letting a
#     cleared prompt read as a judgement about the script, and it says it for any kind reached
#     through `run`/`run-script`, not just the ones on the name list: `npm run dropdb` gets the
#     same disclosure, because the guard read a script name there too.
#     WHAT IS STILL OUTSIDE, and why it is not an oversight: a verb enters this tier when a
#     public report shows it destroying data, and a web search for reported incidents naming
#     `alembic downgrade base`, `manage.py flush`, `redis-cli flushall`, `mysql -e`, `mongosh
#     --eval` or `drizzle-kit drop` returned none, so they stay out (AMH ledger row DC027).
#     `psql -f script.sql` and a migration run from application code stay out for the older
#     reason — the verb is not in the command text at all. And one reported shape is outside any
#     list a command scanner could hold: the PocketOS wipe was a `curl` GraphQL call carrying a
#     token the agent found in an unrelated file, which is the forge/API surface the state file
#     tracks separately. The forge/API rail below closes the command shapes it can actually
#     parse: `gh api`, direct forge API URLs passed to `curl`, their explicit methods, and
#     inline GraphQL mutation documents. It cannot see mutations initiated inside application
#     code, an opaque script, an alias, a shell string such as `bash -c`, or an external UI.
#     Those remain a server-side problem: use least-privilege tokens, protected environments,
#     required approvals, and credentials separate from destructive administration.
#     THE LIMIT THAT MATTERS MOST IS NOT THE LIST. This tier cannot tell production from local
#     and never will: the target is not in the command, it is in a linked project, a config
#     file resolved from the working directory, or `$DATABASE_URL`, and the two invocations are
#     byte-identical. So it buys exactly one thing — the command stops once and the advisory
#     asks which database this resolves to. It is not a confirmation that the answer was
#     right, it does not know whether the agent looked, and a `--local` flag in the command
#     text is a claim rather than a fact. Read a cleared advisory as "this was made
#     deliberate", never as "this was made safe".
#     TWO SMALLER LIMITS, both fail-open and both LISTS rather than categories. The options
#     whose value is a separate word are enumerated (`-h`, `-p`, `-U`, `--host`, `--workdir`,
#     `-p`/`--package` on a runner, and about a dozen more); an option outside that list has
#     its value read as an operand, which is how `dropdb -U prod scratch` and `dropdb -U
#     scratch prod` once shared one signature. And because a consumed value is not recorded,
#     the same database name on two different HOSTS is one signature and one advisory — the
#     advisory's question, not its bookkeeping, is what covers that.
#   * THE PUSH RAIL READS THE PUSH, NOT THE BRANCH NAME. It denies the default branch in each
#     of the three spellings git resolves to it (`main`, `heads/main`, `refs/heads/main`, on
#     either side of a colon), force, deletion, an explicit `refs/tags/` push, exactly two
#     unresolvable destinations (`HEAD` and `@`), and a second ref. Every one of those is a
#     fact of the command standing in front of it. It does NOT check that the branch sits
#     under `BRANCH_PREFIX`: the harness assigns session branch names the repository may not
#     itself prefix, so a namespace rail rejects the very branches it exists to protect (AMH
#     P13, AMH ledger row DA022) — and this one did, blocking a correctly assigned branch.
#     Read the loss that bought, because the namespace test had been stopping these as a side
#     effect of stopping everything unfamiliar, and nothing stops them here now:
#       - ONE explicitly named off-convention ref. `git push -u origin work` is allowed, and
#         that is a real incident from a real session — the naming rule that forbids it is
#         prose the agent is bound by, not a rail.
#       - A tag named without the `refs/` prefix. `git push origin v1` and `git push origin
#         tags/v1` both reach `refs/tags/v1`, and both are indistinguishable from a branch
#         push at scan time, so only the explicit `refs/tags/v1` is judged. Widening to
#         `tags/*` would deny a branch legitimately named `tags/…` — the same mistake as the
#         namespace test, one directory down. The `--pre-push` rail below does NOT share this
#         miss: git resolves the ref before handing it over, so every spelling arrives there
#         as `refs/tags/…`.
#       - Malformed refspecs (`''`, `x:y:z`, `session/x:`, `refs/heads/HEAD`) fail open, the
#         direction every rail here fails.
#     The trade was made knowingly: a rail that cannot tell an assigned name from an invented
#     one blocks both, and the two-item unresolvable list is the honest size of what this
#     scanner can decide rather than a claim to cover the category.
#   * THE SUBAGENT RAIL SEES ONE SPAWN, NEVER THE FLEET. `--pre-task` fires per spawn and holds
#     no view of what is already running, so it cannot tell a second spawn beside a live first
#     from a second after the first finished — it advises every spawn and records the ones that
#     proceeded, which is a COUNT and a rate, never an overlap. Nothing here reads that record,
#     and a count is not a measurement of whether the rule was honoured. It also exists only
#     where the host matches hooks on tool NAME: an agent whose harness has no such matcher has
#     no subagent rail at all, exactly as an agent with no pre-execution hook has no command
#     rail, and neither state is detectable from inside this script.
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
#   command-guard.sh --pre-push       read git's pre-push stdin and judge each ref
#   command-guard.sh --pre-task       one-time advisory before a subagent spawn
#   command-guard.sh --self-test      blocked + allowed fixture matrix
#   command-guard.sh --advisory-report  destructive advisories fired but never re-attempted
#   command-guard.sh --spawn-report     count of subagent spawns that proceeded past the advisory
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
split_segments() { # sets SEGMENTS
	local s=$1
	local i=0 n=${#s} c q='' start=0 parameter_depth=0
	SEGMENTS=()
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
			# THE BACKSLASH RULE, explained here and pointed at from the other walkers.
			# Inside DOUBLE quotes a backslash escapes the next character; inside SINGLE
			# quotes there are no escapes at all, so `'a \'` is a complete string. Without
			# this, the `\"` in `echo "say \"hi\"" && git push --force origin main` read as
			# the CLOSING quote: every operator after it looked unquoted, the `&&` split a
			# segment that bash never runs, and the rails that resolve a leading command saw
			# a different command line than the shell does. That direction is fail-OPEN and
			# it voided the two oldest rails — force-push and push-to-default — plus the
			# destructive and secret-file scanners, on any command line carrying an escaped
			# quote. `expands_secret_var` modelled this correctly from the beginning; the
			# five character walkers did not, which is why the fix is a rule and not a patch
			# (AMH ledger row DC028).
			if [ "$c" = "\\" ] && [ "$q" = '"' ]; then
				i=$((i + 2))
				continue
			fi
			[ "$c" = "$q" ] && q=''
			i=$((i + 1))
			continue
		fi
		case $c in
		"'" | '"') q=$c ;;
		\\) i=$((i + 1)) ;;
		'&')
			# In an fd-duplication redirection (`2>&1`, `<&0`, `>&-`), `&` is
			# syntax inside the redirection rather than a command separator. Splitting
			# here blinds the next segment by leaving the duplicated fd as its first
			# word (`1 push ...`). strip_redirections judges the whole construct later.
			if [ "$i" -gt 0 ]; then
				case ${s:i-1:1}${s:i+1:1} in
				'>'[0-9-] | '<'[0-9-]) i=$((i + 1)); continue ;;
				esac
			fi
			out+=("${s:start:i-start}")
			start=$((i + 1))
			;;
		';' | '|' | $'\n' | '(' | ')' | '`')
			out+=("${s:start:i-start}")
			start=$((i + 1))
			;;
		'{')
			# A `{` immediately consumed by the `$` arm below opens a parameter
			# expansion, not a command group. Nested `${...}` expansions are counted.
			# Other braces remain simple-command separators.
			if [ "$parameter_depth" -eq 0 ]; then
				out+=("${s:start:i-start}")
				start=$((i + 1))
			fi
			;;
		'}')
			if [ "$parameter_depth" -gt 0 ]; then
				parameter_depth=$((parameter_depth - 1))
			else
				out+=("${s:start:i-start}")
				start=$((i + 1))
			fi
			;;
		'$')
			if [ "${wbuf:i-wbase+1:1}" = '(' ]; then
				out+=("${s:start:i-start}")
				i=$((i + 1))
				start=$((i + 1))
			elif [ "${wbuf:i-wbase+1:1}" = '{' ]; then
				parameter_depth=$((parameter_depth + 1))
				i=$((i + 1))
			fi
			;;
		esac
		i=$((i + 1))
	done
	out+=("${s:start}")
	# An ARRAY, not a delimited stream: a quoted NEWLINE stays inside its segment (a commit
	# message body is one argument), where a newline-separated round-trip would re-split it — turning
	# the body's second line into a leading command and blocking a commit on its own
	# message. That is the AMH ledger row D007 class arriving through the transport
	# instead of the scan.
	SEGMENTS=("${out[@]}")
}
SEGMENTS=()

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
			# The backslash rule — see `split_segments` for why this is not a patch.
			if [ "$c" = "\\" ] && [ "$q" = '"' ]; then
				i=$((i + 2))
				continue
			fi
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
KEYMATERIAL_ADVISORY_REASON=''

is_env_template() { # .env.example and friends carry no secrets
	case $1 in
	*.env.example | *.env.sample | *.env.template | *.env.dist) return 0 ;;
	*) return 1 ;;
	esac
}

# True if a path names OpenSSH private key material by its conventional filename. This is
# the only key-file shape narrow enough to block outright: nothing benign is called
# `id_rsa`, so the false-positive population is empty, which is exactly what `.pem` and
# `.key` cannot say for themselves (see the header's secret-file-names block — they get the
# one-time advisory instead).
#
# The public half is meant to be read, copied and pasted into `authorized_keys`, and blocking
# `cat id_rsa.pub` would give a credential reason to a command that exposes nothing — the
# false-reason class this guard's write-destination split exists to avoid. There is NO `.pub`
# arm here, and the absence is the design: the list matches exact literals, so `id_rsa.pub`
# falls through to `return 1` by construction. An explicit arm would be dead code, and a
# comment calling it load-bearing would be this guard's own bug class — a stated mechanism the
# code does not have. Note the contrast with `is_env_template`, whose carve-out IS load-bearing
# because `names_env_file` matches `.env.*` by glob and would otherwise swallow the template.
#
# The three `.pub` fixtures are not ceremony: they fail the moment anyone widens this list to a
# glob (`id_rsa*`), which is the only way the public half can start blocking.
#
# Accepted misses, in the fail-open direction and deliberately not patched with a
# heuristic: any renamed or suffixed key (`id_rsa.bak`, `id_rsa_old`, `deploy_key`,
# `~/.ssh/work`). A suffix wildcard would swallow `id_rsa.pub.bak` and every documentation
# fixture in the same move, and no wildcard reaches a key called something else at all.
#
# Inherited false positive, stated because it is new surface even though the mechanism is
# old: a one-word grep PATTERN is indistinguishable from a path once quotes are stripped, so
# `grep -rn "id_rsa" docs/` is blocked. `.env` has behaved that way since the operand scan
# shipped; the fix would be a change to `split_words` for both names at once, not a carve-out
# here.
names_private_key_file() {
	local base=${1##*/}
	case $base in
	id_rsa | id_dsa | id_ecdsa | id_ecdsa_sk | id_ed25519 | id_ed25519_sk) return 0 ;;
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
	keymaterial) [ -n "${KEYMATERIAL_ADVISORY_STATE+x}" ] && { printf '%s' "$KEYMATERIAL_ADVISORY_STATE"; return 0; } ;;
	destructive) [ -n "${DESTRUCTIVE_ADVISORY_STATE+x}" ] && { printf '%s' "$DESTRUCTIVE_ADVISORY_STATE"; return 0; } ;;
	subagent) [ -n "${SUBAGENT_ADVISORY_STATE+x}" ] && { printf '%s' "$SUBAGENT_ADVISORY_STATE"; return 0; } ;;
	*) return 1 ;;
	esac
	slug=${ROOT//\//_}
	slug=${slug// /_}
	uid=${UID:-unknown}
	printf '/tmp/amh-command-guard-%s-advisory-%s-%s' "$name" "$uid" "$slug"
}

needs_one_time_advisory() { # needs_one_time_advisory <name> <command>
	local name=$1 cmd=$2 state sig when
	case $name in
	dotenv) case $cmd in *.env*) ;; *) return 1 ;; esac ;;
	# `.pem` and `.key` must be followed by a non-alphanumeric character or end the word.
	# That excludes the PLURAL only — `Object.keys(x)`, `jq '.keys[]'`, `data.keys()` — and
	# the honest statement of what remains is that the singular still fires: `jq -r '.key'`
	# and `obj.key` are ordinary program text and get the advisory, fixtured below so it is a
	# recorded decision. No pattern separates a `.key` FILE from a `.key` FIELD, and this tier
	# is one rerun rather than a denial, which is what makes the residue affordable. The
	# plural is worth excluding on its own: it is the commoner shape by far in real code.
	keymaterial) case $cmd in
	*.pem | *.pem[!A-Za-z0-9]* | *.key | *.key[!A-Za-z0-9]*) ;;
	*) return 1 ;;
	esac ;;
	destructive) is_destructive_command "$cmd" || return 1 ;;
	# No condition to test: the caller only invokes this category when a subagent spawn is
	# actually about to happen, and the spawn itself is the whole trigger.
	subagent) ;;
	*) return 1 ;;
	esac
	state=$(advisory_state_file "$name")
	[ -n "$state" ] || return 1
	if [ "$name" = destructive ]; then
		# Rearm per TARGET SET, not per category — the one place this rail's state
		# deliberately diverges from the other two.
		#
		# A single marker file means the first `rm -rf` in a session disarms the rail for
		# every later one. `rm -rf tmp/build` at minute two then buys silence for
		# `rm -rf "$S/base"` at minute forty, on a different path, with a different risk.
		# Nobody has to LEARN to step around a rail that is already spent; it just is. So
		# a repeat of the same deletion passes (which is what "rerun to proceed" means and
		# what the fixtures pin), and a deletion aimed somewhere new gets its own turn.
		sig=$(destructive_signature)
		if [ -e "$state" ] && LC_ALL=C grep -qxF -- "$sig" "$state" 2>/dev/null; then
			# The rerun is the only thing here anyone can OBSERVE. Record it, so that
			# "advised, then went ahead" and "advised, then quietly dropped" stop
			# looking identical in the tree — the second is what the rail's own text
			# calls not-compliance, and until now it left no trace at all. Nothing
			# consumes this file: `--advisory-report` prints it and the ladder shows
			# the line. No gate reads it, and none may (P3, AMH ledger row DC004).
			if [ "$HOOK_INVOCATION" -eq 1 ] &&
				{ [ ! -e "$state.resumed" ] || ! LC_ALL=C grep -qxF -- "$sig" "$state.resumed" 2>/dev/null; }; then
				printf '%s\n' "$sig" >>"$state.resumed" 2>/dev/null || :
			fi
			return 1
		fi
		# If `grep` is unavailable the test above fails and the advisory re-fires — the
		# right direction — at the cost of a duplicate line per attempt. Bounded by the
		# session, and the bootstrap deletes the file; not worth a second mechanism.
		printf '%s\n' "$sig" >>"$state" 2>/dev/null || return 1
	elif [ "$name" = subagent ]; then
		# Rearm per SPAWN, not per session — the same correction AMH ledger row DC004 forced on the
		# destructive rail, for the same reason. A per-session one-shot is spent at exactly
		# the moment the guarded failure happens: the recorded incident is a BURST of three
		# spawns in immediate succession, so a rail that stands down after the first leaves
		# "spawning three is as easy as spawning one" fully intact and merely moves it one
		# spawn to the right. It also fails that row's second half — the sidestep left no
		# trace at all, because the `.resumed` marker above is scoped to the destructive
		# category.
		#
		# So the state file holds an OUTSTANDING advisory rather than a spent one: every
		# spawn is advised once and proceeds on the rerun, which costs the compliant
		# sequential use exactly one turn per spawn and costs a fan-out of three the same
		# turn three times over, with a line recorded for each. Nothing reads that file as
		# evidence of anything (P3): it says a prompt fired and a spawn went ahead, never
		# that anyone thought about it.
		if [ -e "$state" ]; then
			rm -f -- "$state" 2>/dev/null || :
			if [ "$HOOK_INVOCATION" -eq 1 ]; then
				when=$(date -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null) || when='unknown-time'
				printf '%s spawn proceeded past the advisory\n' "$when" >>"$state.resumed" 2>/dev/null || :
			fi
			return 1
		fi
		: >"$state" 2>/dev/null || return 1
	else
		[ -e "$state" ] && return 1
		: >"$state" 2>/dev/null || return 1
	fi
	case $name in
	dotenv)
		# shellcheck disable=SC2016 # the presence-check example must print literally.
		ADVISORY_REASON='This command mentions `.env`. Those files commonly contain live credentials, and even lengths, hashes, excerpts, copies or interpreter reads can disclose or spread secrets. The command guard is stopping this once so you can reconsider: prefer presence-only checks (for example, `[ -n "${MY_KEY:-}" ] && echo set`) or let the tool that needs credentials read them directly. If this warning is not applicable, or this is a false positive such as prose or a template-safe operation, run the same command again; this one-time advisory will not rearm during this session.'
		DOTENV_ADVISORY_REASON=$ADVISORY_REASON
		;;
	subagent)
		# What this rail can and cannot do, stated here because the gap is the whole reason
		# the text reads the way it does. A pre-spawn hook fires once per spawn and holds no
		# view of what else is running, so it CANNOT see concurrency and must not imply it
		# does — prose claiming a check nobody performs is the failure class this harness has
		# a row about. What it can do is make every spawn deliberate, which
		# is exactly where the recorded failure happens: the rule against fanning out was
		# already written at the point of temptation, in prose, and a session read it and
		# fanned out anyway because spawning three is as cheap as spawning one. A speed bump
		# costs one turn and puts the rule in front of the agent at the moment it is being
		# broken (AMH ledger row DC012).
		ADVISORY_REASON='A subagent spawn is about to happen and the command guard is stopping it once. The harness permits ONE fresh-context reviewer at a time and it BLOCKS: you do not keep editing while it runs, and fanning out several because the tooling makes it easy is the exact failure the session-discipline rule names. If this is the single blocking reviewer the rule-review protocol mandates, or another genuinely sequential use, run the same spawn again and it will proceed. EVERY spawn is advised, not just the first, and each one that proceeds is recorded: a burst of three costs this turn three times over and leaves three lines behind, which is the point — the failure this exists for is three spawns in immediate succession, and a rail that stood down after the first would be spent at exactly the moment it was needed. What it can see is that a spawn was advised and that one went ahead; it cannot see whether anything was already running, and nothing may read its record as evidence that a decision was thought about. If you were about to spawn several at once: spawn one, wait for it, and read what it reports before deciding whether a second is needed.'
		;;
	keymaterial)
		# shellcheck disable=SC2016 # the backticked file names are markdown, not substitutions.
		KEYMATERIAL_ADVISORY_REASON='This command names a `.pem` or `.key` file. Those extensions are container formats, not proof of a secret — a certificate or CA bundle is public — but they are also where private keys live, and this rail is what decides whether you look at one. The output filter redacts a key block header and body where it is actually piped; it cannot help with output that never goes through it. The command guard is stopping this once so you can check which kind of file this is: prefer the public half (`.pub`, the certificate) or a presence-only check. If the file is public, or this is prose or a path that never gets read, run the same command again; this one-time advisory will not rearm during this session.'
		ADVISORY_REASON=$KEYMATERIAL_ADVISORY_REASON
		;;
	destructive)
		# The old text suggested "moving the path set to a temporary directory" as an
		# alternative, and a downstream session took exactly that: it renamed the target so
		# the `rm` was not needed, called the rail satisfied, and made no safety check at
		# all — "I routed around the trigger to save a turn." An advisory that names a
		# sidestep gets the sidestep. This one asks for the check instead, and says what
		# the check is (owner, 2026-08-12).
		# The lead sentence follows the VERB. Said of `git worktree add` or `git reset
		# --hard`, "may delete ... source files" is false, and a reader who notices has
		# been handed a correct reason to file the whole advisory as a false positive and
		# rerun without checking — the "cries wolf" failure delivered by the rail's own
		# words. What those verbs do instead is overwrite or discard, which git cannot undo
		# for anything uncommitted.
		# shellcheck disable=SC2016 # the examples must print literally, unexpanded.
		if [ "$DESTRUCTIVE_DELETES" -eq 1 ]; then
			ADVISORY_REASON='This destructive filesystem command may delete guard fixtures, source files, or untracked evidence. The command guard is stopping this ONCE, for this target, so you can spend one turn on the check rather than the deletion.'
		elif [ "$DESTRUCTIVE_DATAPLANE" -eq 1 ] && [ "$DESTRUCTIVE_SCRIPTNAME" -eq 1 ]; then
			# A FOURTH lead, for the reason the third one exists: the sentence has to be true.
			# When the match came from a script NAME the guard has not seen a database command
			# at all, so "this command destroys database state" is asserted on evidence the
			# next paragraph then admits it does not have — a contradiction inside one
			# advisory, and the "cries wolf" failure delivered by the rail's own words.
			ADVISORY_REASON='This command MAY destroy DATABASE state — it carries the name of an operation that drops a schema and replays migrations, drops a database outright, or empties tables, and nothing in git restores any of that. The command guard is stopping this ONCE, for this command, so you can spend one turn on the check rather than the reset.'
		elif [ "$DESTRUCTIVE_DATAPLANE" -eq 1 ]; then
			# A THIRD verb, for the same reason there is a second: neither of the others is
			# true here. Nothing on disk changes, so "may delete source files" is false and
			# "uncommitted edits" is beside the point — git has nothing to do with it, and an
			# agent that reads a false lead sentence has been handed a correct reason to
			# dismiss the whole advisory.
			ADVISORY_REASON='This command destroys DATABASE state — it drops a schema and replays migrations, drops a database outright, or empties tables. Nothing in git restores it and no working-tree check tells you what was in it. The command guard is stopping this ONCE, for this command, so you can spend one turn on the check rather than the reset.'
		else
			ADVISORY_REASON='This command overwrites or discards working-tree state — uncommitted edits, source files, or untracked evidence — and for anything not already committed there is nothing to recover it from. The command guard is stopping this ONCE, for this target, so you can spend one turn on the check rather than the overwrite.'
		fi
		# ADDITIVE, not another arm of the chain below. A command can be both — `rm -rf "$S/x"
		# && supabase db reset` is two hazards in one segment list — and folding this into the
		# path chain would silently drop whichever paragraph lost the race. Each paragraph
		# describes a property the command actually has, so each fires on its own.
		if [ "$DESTRUCTIVE_DATAPLANE" -eq 1 ]; then
			# The paragraph the whole tier exists for. Every other branch here can name the
			# hazard and point at the operand carrying it; this one has to say that the
			# operand does not exist, which is a harder thing to make an agent act on and the
			# reason this text asks for a named answer rather than a moment of care.
			#
			# It must not suggest a way to make the command safe, only a way to find out what
			# it addresses: `--local` is a claim in the command text, not a fact about the
			# environment, and an advisory that blessed it would have taught the sidestep
			# instead of the check — the failure the destructive text above was rewritten to
			# stop (owner, 2026-08-12).
			# shellcheck disable=SC2016 # the examples must print literally, unexpanded.
			ADVISORY_REASON="$ADVISORY_REASON"' WHICH database this hits is not in the command. It is resolved from the environment — a linked project, a config file found from the working directory, `$DATABASE_URL` — so the production and the local spelling of this command are byte-identical, and this guard cannot tell them apart. Neither can a reader of your transcript. Before you rerun, print the resolved target and say which database it is: `supabase status` or the linked project ref, the `DATABASE_URL` HOST (the host, never the whole URL, which carries a password), `\conninfo`, the database name the task is configured with. A `cd` earlier in this session is enough to change the answer, and so is a shell that inherited a different environment than the one you read.'
		fi
		# ADDITIVE again, and for a reason the paragraph itself states: this arm matched a
		# script NAME, and the script is a line in the package manifest that no scanner here reads.
		# Leaving that unsaid would let a cleared prompt read as a judgement about the script,
		# which is the enforcement-asymmetry class the harness keeps a rule about.
		if [ "$DESTRUCTIVE_SCRIPTNAME" -eq 1 ]; then
			# shellcheck disable=SC2016 # the backticked names must print literally.
			ADVISORY_REASON="$ADVISORY_REASON"' This one matched a package SCRIPT NAME, not a command this guard understands. What that script runs is a line in the package manifest and nothing here has opened it, so the name is the entire evidence: it may run something harmless, and the check that settles it is reading the script — `npm run` with no arguments lists them, and the `scripts` block shows the body. The reverse is the part worth carrying away, because no rerun clears it: a script that drops the database under a name like `seed`, `setup` or `bootstrap` reaches the database with no advisory at all, and so does a Makefile target and a justfile recipe. The most widely reported incident of this kind was exactly this shape — an agent ran `npm run db:push` against a production database during a stated code freeze, and the tables came back empty.'
		fi
		if [ "$DESTRUCTIVE_ROOTISH" -eq 1 ]; then
			# shellcheck disable=SC2016 # the examples must print literally, unexpanded.
			ADVISORY_REASON="$ADVISORY_REASON"' A path here BEGINS with a variable and contains a `/`, which is the failure mode this rail is shaped for: if that variable is empty the command addresses an absolute path instead. `rm -rf "$S/base"` with an unset `S` is `rm -rf /base`. The rerun that removes that failure mode rather than merely surviving it is the GUARDED spelling — `rm -rf -- "${S:?}/base"` — because the shell itself aborts on an unset or empty `S`, and this guard treats the guarded and bare spellings as the same target, so rewriting it does not arm a second prompt. Use it IN ADDITION to `printf %s=[%s] S "$S"`, not instead of: the guarded spelling closes the unset-or-empty case and nothing else, so a set-but-wrong `S` — `/` above all, which makes this exact command `rm -rf /base` again — still reaches the filesystem, and only looking catches that one.'
		elif [ "$DESTRUCTIVE_UNEXPANDED" -eq 1 ]; then
			# shellcheck disable=SC2016 # the guarded spelling must print literally.
			ADVISORY_REASON="$ADVISORY_REASON"' A path here still contains a variable. The guard sees the command before the shell expands it, so what the command actually addresses is only knowable on your side: print the expansion before you rerun, and prefer the guarded spelling `"${VAR:?}"` in the rerun, which makes an empty value abort the command instead of widening it. The guarded and bare spellings count as the same target here, so the rewrite is the rerun and not a second prompt.'
		fi
		# The non-compliance clause follows the verb too. "Renaming or relocating the
		# target" is the sidestep an agent reaches for when the command DELETES a path;
		# for an overwrite there is no target to move, and the sidestep with the same
		# shape — clearing the way and calling the rail satisfied — is a different
		# sentence. Naming the wrong one is how the paragraph that exists to close a
		# Goodhart route becomes noise.
		# shellcheck disable=SC2016 # the example must print literally, unexpanded.
		if [ "$DESTRUCTIVE_DELETES" -eq 1 ]; then
			ADVISORY_REASON="$ADVISORY_REASON"' Then rerun the same command to proceed if the deletion is intentional. Two things that are NOT compliance: renaming or relocating the target so the deletion is no longer needed, and rerunning without having looked — both leave the rail spent and the check unmade. Deciding not to delete is a fine answer; arriving at it to avoid the prompt is not.'
		elif [ "$DESTRUCTIVE_DATAPLANE" -eq 1 ]; then
			# The sidestep with this tier'"'"'s shape is not renaming a path or stashing an
			# edit: it is adding a flag that ASSERTS the target is the safe one, or pointing
			# the command at a different database, and calling either of those the check. Both
			# leave the original question — what would this have destroyed — unasked.
			ADVISORY_REASON="$ADVISORY_REASON"' Then rerun the same command to proceed if the reset is intentional. Two things that are NOT compliance: adding a flag that ASSERTS the target is safe, or repointing the command at another database, in place of finding out what the original one was — a flag is a claim in the command text, not a fact about the environment; and rerunning without having looked. Both leave the rail spent and the check unmade. Deciding not to run it, or asking the human whose data it is, is a fine answer; arriving at it to avoid the prompt is not.'
		else
			ADVISORY_REASON="$ADVISORY_REASON"' Then rerun the same command to proceed if the overwrite is intentional. Two things that are NOT compliance: committing or stashing the work only to clear the way, without reading what would otherwise have been lost, and rerunning without having looked — both leave the rail spent and the check unmade. Deciding not to run it is a fine answer; arriving at that to avoid the prompt is not.'
		fi
		# The closing paragraph states what the rearm key IS, so it has to follow the key. For
		# a path the key is the operands as written; for a data-plane command there are barely
		# any operands, and saying "clearing `rm -rf "$S/base"` clears it for every value of
		# `S`" to an agent that just ran `supabase db reset` describes a mechanism its command
		# does not have — the prose/guard drift class, inside one advisory.
		if [ "$DESTRUCTIVE_DATAPLANE" -eq 1 ] && [ "$DESTRUCTIVE_DELETES" -eq 0 ] && [ "$DESTRUCTIVE_UNEXPANDED" -eq 0 ]; then
			# shellcheck disable=SC2016 # the backticked examples must print literally.
			ADVISORY_REASON="$ADVISORY_REASON"' Rerunning clears this advisory for this command TEXT only, and that key is weak here by construction: this command names almost nothing, so it is cleared by its verb and whichever target flags it carries, and the SAME text run later — after a `cd`, after a link, against a different environment — reaches a different database with the advisory already spent. The rerun is your check, not the guard'"'"'s. What this rail can see is that a prompt fired and whether the command came back; it cannot see whether you looked, and `scripts/ladder.sh` prints the ones that never came back.'
		else
			# shellcheck disable=SC2016 # the example must print literally, unexpanded.
			ADVISORY_REASON="$ADVISORY_REASON"' Rerunning clears this advisory for this command TEXT only, and a command aimed somewhere else gets its own. Two limits of that, both worth knowing: the guard keys on the operands AS WRITTEN, so clearing `rm -rf "$S/base"` clears it for every later value of `S` — the rerun is your check, not the guard'"'"'s — and `${S:?}` folds to `$S` for that purpose in both directions. What this rail can see is that a prompt fired and whether the command came back; it cannot see whether you looked, and `scripts/ladder.sh` prints the ones that never came back.'
		fi
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
split_words() { # sets SPLIT_WORDS
	local s=$1
	local i=0 n=${#s} c q='' start=-1 w
	SPLIT_WORDS=()
	local wbase=0 wbuf='' wsafe=0
	while [ "$i" -lt "$n" ]; do
		if [ "$i" -ge "$wsafe" ]; then # windowed scan — see CHAR_WINDOW
			wbase=$i
			wbuf=${s:wbase:CHAR_WINDOW}
			wsafe=$((wbase + CHAR_WINDOW - CHAR_LOOKAHEAD))
		fi
		c=${wbuf:i-wbase:1}
		if [ -n "$q" ]; then
			# The backslash rule — see `split_segments` for why this is not a patch.
			if [ "$c" = "\\" ] && [ "$q" = '"' ]; then
				i=$((i + 2))
				continue
			fi
			[ "$c" = "$q" ] && q=''
			i=$((i + 1))
			continue
		fi
		case $c in
		' ' | $'\t' | $'\n')
			if [ "$start" -ge 0 ]; then
				w=${s:start:i-start}
				SPLIT_WORDS+=("${w//[\'\"]/}")
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
		SPLIT_WORDS+=("${w//[\'\"]/}")
	fi
	return 0
}
SPLIT_WORDS=()

# Remove REDIRECTIONS from a segment, leaving the words the shell would actually hand the
# command. Redirections are syntax, not arguments, and a scanner that reads word lists
# counts them as operands anyway: the push rail read `git push -u origin session/x 2>&1` as
# naming two refs and denied a legal push with a reason — "names another branch or leaves
# the ref implicit" — that was false of the command in front of it. A rail demonstrably
# wrong about what it just read is worse than one that never looked, so this runs before
# any word is judged (AMH ledger row DC001).
#
# POSITION, not presence, and the same three disciplines the scanners above are built on:
#
#   * Quoted text is DATA. `git push origin session/x '2>' x` passes bash a literal `2>`
#     argument, so removing it here would hide a word the command really receives — the
#     AMH ledger row D007 class, arriving one layer down.
#   * An fd number glued to the operator is syntax (`2>err`), but any other word prefix is
#     a word bash still passes on: `abc2>err` hands the command `abc2`. Only an all-digit
#     prefix is eaten.
#   * A bare operator claims the NEXT word (`> /dev/null`, and the `2>` that
#     `split_segments` leaves behind when it consumes the `&` of `2>&1`); an operator with
#     a target glued on claims nothing further.
#
# Slices, never accumulates: the copy is one append per redirection removed, not one per
# character (see `split_segments` on the quadratic shape this avoids).
strip_redirections() { # sets STRIPPED
	local s=$1
	STRIPPED=$s
	case $s in *'<'* | *'>'*) ;; *) return 0 ;; esac
	local i=0 n=${#s} c q='' kept='' keep=0 wstart=0 cut
	while [ "$i" -lt "$n" ]; do
		c=${s:i:1}
		if [ -n "$q" ]; then
			# The backslash rule — see `split_segments` for why this is not a patch.
			if [ "$c" = "\\" ] && [ "$q" = '"' ]; then
				i=$((i + 2))
				continue
			fi
			[ "$c" = "$q" ] && q=''
			i=$((i + 1))
			continue
		fi
		case $c in
		"'" | '"')
			q=$c
			i=$((i + 1))
			continue
			;;
		\\)
			i=$((i + 2))
			continue
			;;
		' ' | $'\t')
			i=$((i + 1))
			wstart=$i
			continue
			;;
		'<' | '>') ;;
		*)
			i=$((i + 1))
			continue
			;;
		esac
		# A redirection starts here. Cut from the fd number when the whole word so far is
		# one, and from the operator otherwise.
		cut=$i
		case ${s:wstart:i-wstart} in
		'' | *[!0-9]*) ;;
		*) cut=$wstart ;;
		esac
		while :; do case ${s:i:1} in '<' | '>') i=$((i + 1)) ;; *) break ;; esac done
		# `>&2` carries its own target; only a bare operator reaches across the space.
		case ${s:i:1} in '&') i=$((i + 1)) ;; esac
		case ${s:i:1} in ' ' | $'\t') while :; do case ${s:i:1} in ' ' | $'\t') i=$((i + 1)) ;; *) break ;; esac done ;; esac
		# The target word, quotes respected — a redirection to `my file` is one word.
		while [ "$i" -lt "$n" ]; do
			c=${s:i:1}
			if [ -n "$q" ]; then
				# The backslash rule — see `split_segments`.
				if [ "$c" = "\\" ] && [ "$q" = '"' ]; then
					i=$((i + 2))
					continue
				fi
				[ "$c" = "$q" ] && q=''
				i=$((i + 1))
				continue
			fi
			case $c in
			"'" | '"') q=$c ;;
			\\) i=$((i + 1)) ;;
			' ' | $'\t') break ;;
			esac
			i=$((i + 1))
		done
		kept+=${s:keep:cut-keep}
		keep=$i
		wstart=$i
	done
	kept+=${s:keep}
	STRIPPED=$kept
}
STRIPPED=''

# TRUE when TEXT holds a non-space character but the parser handed back COUNT of nothing.
# That combination is a defect in this script, never a property of the command: a blank
# segment legitimately yields no words (`>/dev/null` is all redirection), and the two must
# not share an exit path, because one of them means "checked, nothing to object to" and the
# other means "not checked at all" (AMH ledger row DC002).
parse_produced_nothing() { # <text> <count>
	case $1 in *[![:space:]]*) [ "$2" -eq 0 ] && return 0 ;; esac
	return 1
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
redirect_targets() { # sets REDIRECT_TARGETS
	local s=$1
	local i=0 n=${#s} c q='' rest target
	local wbase=0 wbuf='' wsafe=0
	REDIRECT_TARGETS=()
	case $s in *'<'*) ;; *) return 0 ;; esac
	while [ "$i" -lt "$n" ]; do
		if [ "$i" -ge "$wsafe" ]; then # windowed scan — see CHAR_WINDOW
			wbase=$i
			wbuf=${s:wbase:CHAR_WINDOW}
			wsafe=$((wbase + CHAR_WINDOW - CHAR_LOOKAHEAD))
		fi
		c=${wbuf:i-wbase:1}
		if [ -n "$q" ]; then
			# The backslash rule — see `split_segments` for why this is not a patch.
			if [ "$c" = "\\" ] && [ "$q" = '"' ]; then
				i=$((i + 2))
				continue
			fi
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
				[ -n "$UNQUOTED" ] && REDIRECT_TARGETS+=("$UNQUOTED")
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
		if names_private_key_file "$UNQUOTED"; then
			BLOCK_REASON="Reading \`$UNQUOTED\` prints private key material (AMH P17). \`redact.sh\` redacts a key block header and body, but only where output is actually piped through it — nothing here guarantees that. Check that the file exists (\`[ -f $UNQUOTED ] && echo present\`) or read the public half (\`$UNQUOTED.pub\`), or ask the owner for a narrower evidence contract via the Owner queue."
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
		if names_private_key_file "$UNQUOTED"; then
			BLOCK_REASON="\`$cmd\` copies \`$UNQUOTED\` — private key material — to another path, where no rail here can see what reads them next (AMH P17). Leave the key where it is; if a tool needs it, point the tool at this path, or ask the owner for a narrower evidence contract via the Owner queue."
			return 1
		fi
	done
	return 0
}

# GNU's `getopt_long` accepts any unambiguous ABBREVIATION of a long option, so `--u FOO`
# IS `--unset FOO` and `--sp 'cat .env'` is `--split-string`. Matching the full spellings
# alone is not a narrower rail, it is a hole: an abbreviation read as a plain flag hands the
# option's ARGUMENT to the caller as the utility, so `env --u FOO cat .env` reached the file
# unjudged while `env --unset FOO cat .env` was blocked — the same command, three characters
# apart. True of `$1` when it is `--` plus a non-empty prefix of any name in `$2...`, with an
# `=value` suffix optional. Whether the real `env` would call the abbreviation ambiguous is
# not modelled: this list is short enough that no two names here share a first letter.
is_long_opt() {
	local word=${1#--} name
	case $word in *=*) word=${word%%=*} ;; esac
	[ -n "$word" ] || return 1
	shift
	for name in "$@"; do
		case $name in "$word"*) return 0 ;; esac
	done
	return 1
}

# `env` is a transparent prefix only when it is handed a utility to RUN. Handed none it
# prints the environment instead — POSIX spells it `env [-i] [name=value]... [utility
# [argument...]]` — so which of the two a command is comes down to walking `env`'s own
# options and assignments and seeing whether a utility operand is left behind them. The
# first version of this arm skipped that walk and read any leading `-` as proof of a dump,
# which refused `env -u AMH_REMOTE bash scripts/session-start.sh` — a command that unsets
# one name and prints nothing, and the exact spelling the shipped fixture suite runs. Same
# class as the branch-namespace test one rail over (AMH ledger row DC017): a rail rejecting
# a command it exists to permit.
#
# Sets ENV_PREFIX_WIDTH to the number of leading words that belong to `env` itself, so the
# caller can step over them. Returns 0 when what follows is something OTHER than the
# environment — a utility to judge, or an informational option — and 1 when nothing follows,
# which is the dump. Swallowing the argument of `-u NAME` and its friends is the half that
# matters for coverage rather than for false positives: without it `env -u FOO cat .env`
# reads as `env -u FOO` running `FOO`, and the `.env` read walks free.
env_prefix_width() {
	local rest takes_arg
	ENV_PREFIX_WIDTH=0
	while [ "$#" -gt 0 ]; do
		takes_arg=1
		case $1 in
		--*)
			# `--help` and `--version` print their own text and exit, never the
			# environment, so a dump reason would be false about them.
			# `--split-string` supplies the utility INSIDE its own string — in this
			# word for the `=` form, in the next for the spaced one — which this guard
			# cannot split: the accepted miss `bash -c 'cat .env'` already is, not a
			# dump. Deliberately NOT here: `--list-signal-handling`, which prints the
			# handling AND the environment, and the optional-argument signal options
			# (`--block-signal` and friends), whose argument never consumes the next
			# word. Both are plain flags below, and both are dumps with nothing to run.
			if is_long_opt "$1" help version split-string; then
				return 0
			fi
			case $1 in
			*=*) ;; # the argument rides in this word
			*) is_long_opt "$1" unset chdir argv0 && takes_arg=0 ;;
			esac
			;;
		# A lone `-` (an alias for `-i`), `--`, and assignments.
		- | *=*) ;;
		-*)
			# A short cluster, read left to right the way getopt reads it. The first
			# letter that takes an argument swallows either the rest of the word
			# (`-uFOO`) or the word after it (`-iu FOO`). `-u` is GNU's and BSD's,
			# `-C` GNU's, `-P` BSD's; `-a`/`--argv0` is newer GNU and coreutils 9.4
			# rejects it outright. Listing one that does not exist costs nothing — the
			# command is a usage error that runs nothing either way — while omitting
			# one that does hands its argument to the guard as the utility, so the
			# list leans long on purpose. `-S` is out: read as a plain flag it leaves
			# its command string as the next word, which is the answer above.
			rest=${1#-}
			while [ -n "$rest" ]; do
				case $rest in
				[uCPa])
					takes_arg=0
					break
					;;
				[uCPa]*) break ;;
				*) rest=${rest#?} ;;
				esac
			done
			;;
		*) return 0 ;;
		esac
		ENV_PREFIX_WIDTH=$((ENV_PREFIX_WIDTH + 1))
		shift
		[ "$takes_arg" -eq 0 ] || continue
		# The option's argument is the next word, never the utility. Nothing there is a
		# usage error that runs nothing (`env -u`), which is the fail-OPEN direction:
		# your input is strange, and a block reason claiming it printed the environment
		# would be false about a command that printed nothing at all.
		[ "$#" -gt 0 ] || return 0
		ENV_PREFIX_WIDTH=$((ENV_PREFIX_WIDTH + 1))
		shift
	done
	return 1
}

# Classify mutation-capable forge API commands from parsed argv, not substrings of the shell
# text. Ordinary issue/comment writes are outside this deliberately high-consequence inventory.
FORGE_CLIENT=''
FORGE_METHOD=''
FORGE_CLASS=''
forge_operation_class() {
	local lower path tail repo
	lower=$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')
	path=${lower#https://*/}
	path=${path%%\?*}
	# Repository-root administration has no `/settings` or `/delete` suffix: the method is
	# the only structural distinction between reading the repo and changing or deleting it.
	case $path in repos/*/*)
		tail=${path#repos/}; repo=${tail#*/}
		case $repo in */*) ;; *)
			case $FORGE_METHOD in PATCH | PUT) FORGE_CLASS=repository-settings; return 0 ;; DELETE) FORGE_CLASS=destructive; return 0 ;; esac
			;;
		esac
		;;
	esac
	case /$lower/ in
	*/settings/* | */rulesets/* | */branch_protection/* | */hooks/*) FORGE_CLASS=repository-settings ;;
	*/secrets/* | */variables/* | */public-key/* | */deploy-keys/* | */keys/* | */tokens/*) FORGE_CLASS=secrets ;;
	*/environments/* | */deployments/* | *environment*) FORGE_CLASS=environments ;;
	*/actions/caches/* | */artifacts/* | */packages/* | */storage/* | *deletevolume* | *removevolume*) FORGE_CLASS=storage ;;
	*/releases/* | */release-assets/*) FORGE_CLASS=releases ;;
	*/git/refs/* | */git/tags/* | */branches/* | *deleteref* | *createref* | *updateref*) FORGE_CLASS=refs ;;
	*/repos/*/transfer* | *deleterepository* | *archiverepository* | *transferrepository*) FORGE_CLASS=destructive ;;
	*/orgs/*/repos* | */user/repos* | */enterprises/* | *delete*repositories* | *bulk* | *batch*) FORGE_CLASS=bulk-resources ;;
	*) return 1 ;;
	esac
}

forge_api_mutation() {
	local client=$1
	shift
	local method=GET endpoint='' graphql='' a next_method=0 next_field=0 next_skip=0 body=0 get=0 forge=0 lower rest
	case $client in gh | curl) ;; *) return 1 ;; esac
	if [ "$client" = gh ]; then
		[ "${1:-}" = api ] || return 1
		shift; FORGE_CLIENT='gh api'; forge=1
		for a in "$@"; do
			if [ "$next_method" -eq 1 ]; then method=$(printf '%s' "$a" | tr '[:lower:]' '[:upper:]'); next_method=0; continue; fi
			if [ "$next_skip" -eq 1 ]; then next_skip=0; continue; fi
			if [ "$next_field" -eq 1 ]; then case $a in query=*) graphql=${a#query=} ;; esac; next_field=0; continue; fi
			case $a in
			-X | --method) next_method=1 ;; -X?*) method=${a#-X}; method=$(printf '%s' "$method" | tr '[:lower:]' '[:upper:]') ;;
			--method=*) method=${a#*=}; method=$(printf '%s' "$method" | tr '[:lower:]' '[:upper:]') ;;
			-f | -F | --raw-field | --field) body=1; next_field=1 ;;
			-f* | -F*) body=1; case ${a#??} in query=*) graphql=${a#??query=} ;; esac ;;
			--raw-field=* | --field=*) body=1; case ${a#*=} in query=*) graphql=${a#*=query=} ;; esac ;;
			--input) body=1; next_skip=1 ;; --input=*) body=1 ;;
			-H | --header | -p | --preview | --hostname) next_skip=1 ;;
			-H?* | --header=* | -p?* | --preview=* | --hostname=*) ;;
			-*) ;; *) [ -n "$endpoint" ] || endpoint=$a ;;
			esac
		done
		[ "$method" != GET ] || [ "$body" -eq 0 ] || method=POST
	else
		FORGE_CLIENT=curl
		for a in "$@"; do
			if [ "$next_method" -eq 1 ]; then method=$(printf '%s' "$a" | tr '[:lower:]' '[:upper:]'); next_method=0; continue; fi
			if [ "$next_skip" -eq 1 ]; then next_skip=0; continue; fi
			if [ "$next_field" -eq 1 ]; then graphql=$a; next_field=0; continue; fi
			case $a in
			-X | --request) next_method=1 ;; -X?*) method=${a#-X}; method=$(printf '%s' "$method" | tr '[:lower:]' '[:upper:]') ;;
			--request=*) method=${a#*=}; method=$(printf '%s' "$method" | tr '[:lower:]' '[:upper:]') ;; -G | --get) get=1 ;;
			-d | --data | --data-raw | --data-binary | --data-urlencode | -F | --form | --form-string | -T | --upload-file) body=1; next_field=1 ;;
			-d?* | --data=* | --data-raw=* | --data-binary=* | --data-urlencode=* | -F?* | --form=* | --form-string=* | -T?* | --upload-file=*) body=1; graphql="$graphql ${a#*=}" ;;
			-H | --header | -A | --user-agent | -u | --user | -e | --referer | -b | --cookie | -c | --cookie-jar | --proxy | --resolve | --connect-to | -o | --output) next_skip=1 ;;
			-H?* | --header=* | -A?* | --user-agent=* | -u?* | --user=* | --referer=* | --cookie=* | --cookie-jar=* | --proxy=* | --resolve=* | --connect-to=* | -o?* | --output=*) ;;
			https://*) lower=$(printf '%s' "$a" | tr '[:upper:]' '[:lower:]'); case $lower in https://api.github.com/* | https://github.com/api/* | https://gitlab.com/api/* | https://api.bitbucket.org/*) endpoint=$lower; forge=1 ;; esac ;;
			esac
		done
		[ "$get" -eq 1 ] || [ "$method" != GET ] || [ "$body" -eq 0 ] || method=POST
	fi
	case $method in GET | HEAD | OPTIONS) return 1 ;; esac
	FORGE_METHOD=$method
	case $endpoint in *graphql*)
		if [ "$client" = curl ]; then
			# Isolate the JSON query member from variables before looking for the GraphQL
			# operation keyword. This is intentionally not a general JSON parser.
			lower=$(printf '%s' "$graphql" | tr '[:upper:]' '[:lower:]')
			lower=${lower//\\/}
			# split_words removes the JSON member-name quotes, so accept that parsed form as
			# well as the quoted form curl receives.
			case $lower in
			*'"query"'*) rest=${lower#*'"query"'}; rest=${rest%%'"variables"'*}; graphql=$rest ;;
			*query:*) rest=${lower#*query:}; rest=${rest%%variables:*}; graphql=$rest ;;
			*) return 1 ;;
			esac
		fi
		case $graphql in *mutation*) forge_operation_class "$graphql" && return 0 ;; esac
		return 1
		;;
	esac
	[ "$forge" -eq 1 ] || return 1
	forge_operation_class "$endpoint"
}

check_segment() {
	local raw=$1
	local words=() i=0 w
	# Judge the words bash would pass, not the redirections around them. A redirection can
	# sit ANYWHERE — before the command word (`>/dev/null printenv`), between a command and
	# its subcommand (`git >/dev/null push origin main`), or after the operands — and in
	# every one of those positions it used to shift or hide the word this guard judges. The
	# `<`-target scan below deliberately reads `raw` instead: the redirection is exactly
	# what it is looking for.
	strip_redirections "$raw"
	split_words "$STRIPPED"
	words=(${SPLIT_WORDS[@]+"${SPLIT_WORDS[@]}"})

	# A redirection reaches the same file a reader command would, from ANY command:
	# `tr "\0" "\n" < /proc/self/environ` names no reader at all.
	local target
	redirect_targets "$raw"
	for target in ${REDIRECT_TARGETS[@]+"${REDIRECT_TARGETS[@]}"}; do
		if names_env_file "$target"; then
			BLOCK_REASON="Redirecting from \`$target\` feeds credential values into the command (AMH P17). Check key presence instead, or ask the owner for a narrower evidence contract via the Owner queue."
			return 1
		fi
		if names_private_key_file "$target"; then
			BLOCK_REASON="Redirecting from \`$target\` feeds private key material into the command (AMH P17). Check that the file exists, or read the public half (\`$target.pub\`), or ask the owner for a narrower evidence contract via the Owner queue."
			return 1
		fi
	done

	# A non-blank command that parses to NO words is a parser failure, not an empty
	# command, and the two must never share an exit path. The scanners used to reach
	# their word list through a process substitution, whose output arrived empty often
	# enough on stock macOS Bash 3.2 to turn eighteen shipped fixtures red and green
	# again on a re-run at the same commit (AMH ledger row DC002). An empty list took
	# the `no words to judge` branch below and ALLOWED the command: a rail reporting a
	# clean read of something it never read. The subshells are gone, and this arm is
	# what makes their return visible instead of silent.
	if parse_produced_nothing "$STRIPPED" "${#words[@]}"; then
		BLOCK_REASON="The command guard could not parse this command: it has text but produced no words to judge. This is a defect in the guard, not a verdict about your command — nothing was checked, so nothing may be allowed on that basis. Report it with the command text; re-running will not help."
		return 1
	fi

	# Strip leading variable assignments and transparent prefixes so that
	# `env FOO=1 git push --force` is judged as a git command, not an env dump.
	while [ "$i" -lt "${#words[@]}" ]; do
		w=${words[$i]}
		case $w in
		*=*) i=$((i + 1)) ;;
		sudo | nohup | nice | time | command | builtin | exec) i=$((i + 1)) ;;
		env)
			# `env` handed something to run is a prefix; handed nothing it is the dump.
			# `env_prefix_width` above is where that distinction is drawn and argued.
			local env_rest=("${words[@]:$((i + 1))}")
			if env_prefix_width ${env_rest[@]+"${env_rest[@]}"}; then
				i=$((i + 1 + ENV_PREFIX_WIDTH))
			else
				# `env -i` and `env -` print an EMPTIED environment and expose
				# nothing, so the credentials clause would be false about them. They
				# are still stopped, and the reason says why rather than asserting a
				# leak: carving them out would put the option walk in the business of
				# ruling which dumps happen to come back harmless, and nothing needs
				# either spelling without a command after it.
				BLOCK_REASON="\`env\` with no command to run prints the environment instead of prefixing one, and this session's carries credentials (AMH P17). Report key PRESENCE only, e.g. \`[ -n \"\${MY_KEY:-}\" ] && echo set\`. \`env -i\` and \`env -\` print an emptied one and are stopped with the rest rather than carved out. Give \`env\` the command it was meant to prefix — \`env -u FOO cmd\`, \`env FOO=1 cmd\` — and this guard judges \`cmd\`."
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

	if forge_api_mutation "$cmd" ${args[@]+"${args[@]}"}; then
		BLOCK_REASON="Forge/API mutation blocked: client=$FORGE_CLIENT method=$FORGE_METHOD operation-class=$FORGE_CLASS. This local rail never prints payloads or credentials. Use an explicit owner-approved administrative workflow with least-privilege credentials, protected environments and required approvals."
		return 1
	fi

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
		local ref_count=0 implicit_ref=0 positional=0 delete_push=0 skip_option_arg=0
		for a in "${args[@]:$((j + 1))}"; do
			if [ "$skip_option_arg" -eq 1 ]; then skip_option_arg=0; continue; fi
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
				"$DEFAULT_BRANCH" | "refs/heads/$DEFAULT_BRANCH" | "heads/$DEFAULT_BRANCH" | *:"$DEFAULT_BRANCH" | *:"refs/heads/$DEFAULT_BRANCH" | *:"heads/$DEFAULT_BRANCH")
					BLOCK_REASON="\`$a\` is denied twice over: the leading \`+\` force-pushes (AMH P7, pushed checkpoints are immutable) and the target is \`$DEFAULT_BRANCH\` (AMH P13). Push your session branch instead: \`git push -u origin $BRANCH_PREFIX/<codename>\`. The owner merges via squash PR."
					;;
				*)
					BLOCK_REASON="A leading \`+\` on the refspec \`$a\` force-pushes it (AMH P7), and pushed checkpoints are immutable. Drop the \`+\`; if the branch diverged, merge the default branch in — never rewrite pushed history."
					;;
				esac
				return 1
				;;
			--delete | -d) delete_push=1 ;;
			-o | --push-option | --receive-pack | --exec) skip_option_arg=1 ;;
			--push-option=* | --receive-pack=* | --exec=*) ;;
			-*) ;;
			# Every spelling git resolves to the default branch, not just the two obvious
			# ones. Git's DWIM for a bare name is `refs/<name>`, `refs/tags/<name>`,
			# `refs/heads/<name>`, so `main`, `heads/main` and `refs/heads/main` are the
			# complete set that reaches `refs/heads/main` — and `heads/` was the spelling
			# the retired namespace test had been catching by accident.
			"$DEFAULT_BRANCH" | "refs/heads/$DEFAULT_BRANCH" | "heads/$DEFAULT_BRANCH" | *:"$DEFAULT_BRANCH" | *:"refs/heads/$DEFAULT_BRANCH" | *:"heads/$DEFAULT_BRANCH")
				BLOCK_REASON="Pushing to \`$DEFAULT_BRANCH\` is denied (AMH P13). Push your session branch instead: \`git push -u origin $BRANCH_PREFIX/<codename>\`. The owner merges via squash PR."
				return 1
				;;
			# Tagging and publishing a release are owner steps, and the namespace test used
			# to stop a tag push as a side effect of stopping everything else. Accepted
			# miss, deliberately not spelled out in the reason an agent reads: a BARE tag
			# name (`git push origin v1`) is a branch name as far as this scanner can tell,
			# so only the explicit `refs/tags/` spelling is judged here.
			refs/tags/* | *:refs/tags/*)
				BLOCK_REASON="\`$a\` publishes a tag. Release and tag actions are owner steps (AMH P13) — record the tag you want in the Owner queue rather than pushing it."
				return 1
				;;
			*)
				# The first positional is the remote; every later positional is a refspec.
				# What this arm judges is the DESTINATION each refspec names, never the
				# namespace it sits in — see the push-rail bullet in the header block.
				positional=$((positional + 1))
				if [ "$positional" -gt 1 ]; then
					ref_count=$((ref_count + 1))
					case $a in
					# An empty source side deletes the destination: `git push origin :b`.
					# Caught here rather than by the flag arm, which sees only `--delete`.
					:*) delete_push=1 ;;
					# A `HEAD` destination names no branch this rail can read: bare
					# `HEAD` resolves against the worktree, `src:HEAD` against the
					# REMOTE's HEAD symref, and either can land on the default branch.
					# Those are two different mechanisms, so the reason below says only
					# what they share — an unreadable destination — and asserts neither.
					# `HEAD` as the SOURCE (`HEAD:$BRANCH_PREFIX/x`) names an explicit
					# destination and is fine. This list is exactly two spellings long;
					# every other unresolvable destination fails open.
					HEAD | @ | *:HEAD | *:@) implicit_ref=1 ;;
					esac
				fi
				;;
			esac
			done
		if [ "$delete_push" -eq 1 ]; then
			BLOCK_REASON="Deleting a pushed branch rewrites published history and is denied (AMH P7). Leave it for the owner or the forge's post-merge cleanup."
			return 1
		fi
		if [ "$implicit_ref" -eq 1 ]; then
			BLOCK_REASON="This push names \`HEAD\` as its destination, so which branch it lands on is decided elsewhere — by the worktree, or by the remote's own HEAD — and either can be \`$DEFAULT_BRANCH\` (AMH P13). Name the destination branch: \`git push -u origin $BRANCH_PREFIX/<codename>\`."
			return 1
		fi
		if [ "$ref_count" -eq 0 ]; then
			BLOCK_REASON="This push leaves the ref implicit, so what it publishes is decided by remote and push configuration this rail cannot see — which can include \`$DEFAULT_BRANCH\` (AMH P13). Name the branch: \`git push -u origin $BRANCH_PREFIX/<codename>\`."
			return 1
		fi
		if [ "$ref_count" -gt 1 ]; then
			BLOCK_REASON="This push names $ref_count refs. AMH publishes one branch per push (AMH P13), so the extra ref is one you did not mean to send. Push them one at a time: \`git push -u origin $BRANCH_PREFIX/<codename>\`."
			return 1
		fi
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
	split_words "$raw"
	words=(${SPLIT_WORDS[@]+"${SPLIT_WORDS[@]}"})
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

# Normalise ONE operand for the signature. `$d`, `${d}` and `${d:?}` name the same target,
# and after 8.0.0 they must produce the same signature, because the advisory now ASKS for the
# `${d:?}` spelling. A rewrite the rail requested must not read as a new deletion and re-arm
# the prompt it was written to satisfy — that trap is what turns a safety rewrite into a
# second turn, and a second turn is what the session was trying to avoid when it renamed the
# directory instead (AMH ledger row DC004).
#
# Only the two forms that name the same thing are folded. `${d:-/tmp}` and `${d:+x}` are NOT:
# they SUBSTITUTE a different value, so they can address a path the bare variable never
# would, and folding them would let one deletion clear the advisory for another.
#
# The fold is one-directional in intent and symmetric in fact: clearing `${d:?}` also clears
# `$d`. Living with that is deliberate — reaching the guarded spelling means the prompt was
# already seen and answered, which is the whole cost this rail charges.
normalize_operand() { # sets NORMALIZED
	local w=$1 # split: `local w=$1 n=${#w}` expands ${#w} BEFORE w exists — see split_words
	local n=${#w} i=0 acc='' c inner name rest close fold
	while [ "$i" -lt "$n" ]; do
		c=${w:i:1}
		if [ "$c" = '$' ] && [ "${w:i+1:1}" = '{' ]; then
			rest=${w:i+2}
			close=${rest%%\}*}
			if [ "$close" = "$rest" ]; then # no closing brace: copy verbatim
				acc=$acc$c
				i=$((i + 1))
				continue
			fi
			inner=$close
			name=${inner%%[!A-Za-z0-9_]*}
			# The suffix decides, and `:?` must be matched LITERALLY: written as an
			# unquoted pattern the `?` is a glob for any character, which folded
			# `${d:-/tmp}` and `${d:+x}` into `$d` — exactly the substituting forms this
			# must leave alone.
			rest=${inner#"$name"}
			# The character AFTER the closing brace decides whether the braces were
			# load-bearing. `${d}build` is `$d` followed by `build`; `$dbuild` names a
			# different variable, so folding the first into the second collides two
			# unrelated deletions on one signature — the cross-target silence the rearm
			# exists to stop, reached through the idiomatic reason braces exist. Fold only
			# when what follows cannot continue an identifier.
			case ${w:i+2+${#inner}+1:1} in
			[A-Za-z0-9_]) fold=0 ;;
			*) fold=1 ;;
			esac
			if [ "$fold" -eq 1 ] && [ -n "$name" ] && { [ -z "$rest" ] || [ "${rest#':?'}" != "$rest" ]; }; then
				acc=$acc\$$name
			else
				acc=$acc\$\{$inner\}
			fi
			i=$((i + 2 + ${#inner} + 1))
			continue
		fi
		acc=$acc$c
		i=$((i + 1))
	done
	NORMALIZED=$acc
}
NORMALIZED=''

# Record what a confirmed destructive segment is aimed AT. The target is the whole risk
# here — unlike the dotenv and key-material rails, where every hit means the same thing
# ("you are about to read a secret"), two `rm -rf` commands in one session can be a
# scratch directory and a source tree. So the operands are collected, and two properties
# of the command TEXT that no amount of good intent substitutes for:
#
#   DESTRUCTIVE_UNEXPANDED — an operand still contains a `$`. The guard sees the command
#     before the shell expands it, so it cannot know what the variable holds; the agent
#     can, and the check costs one `printf`.
#   DESTRUCTIVE_ROOTISH — an operand begins with a plain VARIABLE reference and contains a
#     `/`. This is the shape that turns into an absolute path when the variable is empty:
#     `rm -rf "$S/base"` with an unset `S` is `rm -rf /base`. It is the failure mode this
#     rail exists for, and it is the one the advisory used not to mention.
#
# "Plain variable reference" is doing real work in that sentence, because this branch emits
# the strongest paragraph the advisory has and an alarm that cries wolf is one an agent
# learns to skim. Three exclusions, each because the empty-expansion failure mode is
# genuinely absent, not merely unlikely:
#
#   `$(pwd)/x`, `$((n))/x`  — a substitution is not a variable; there is nothing to check
#                             for emptiness, and telling the agent to check one is a false
#                             instruction from a rail whose whole value is being believed.
#   `${S:-/tmp}/x`          — a defaulted or alternate expansion cannot yield the empty
#                             string by the route this warns about.
#   `$HOME/x`, `$PWD/x`     — always-set shell variables. These are the commonest safe
#     `$TMPDIR/x`, `$ROOT/x`  spelling of a scratch path by a wide margin.
#
# All three still set DESTRUCTIVE_UNEXPANDED, so they get the weaker "print the expansion"
# text. The exclusion narrows which paragraph fires, never whether the advisory fires.
record_destructive_targets() { # record_destructive_targets <kind> <operand>...
	local kind=$1 w bare name entry
	shift
	# Whether this verb's operands are filesystem PATHS or git REVISIONS decides whether the
	# rootish paragraph is allowed to fire, and it is a correctness question rather than a
	# stylistic one. That paragraph asserts a specific mechanism — "if that variable is empty
	# the command addresses an absolute path instead" — and for a revision operand the
	# assertion is simply false: `git reset --hard "$UPSTREAM/main"` with `$UPSTREAM` unset is
	# `git reset --hard /main`, which git rejects with `fatal: ambiguous argument '/main':
	# unknown revision`. No absolute path is ever addressed. The exclusion list this joins
	# exists for exactly this reason — a rail whose whole value is being believed may not hand
	# the agent a false instruction to check.
	local revision_operands=1
	case $kind in
	git-reset-hard | git-checkout-force | git-switch-force) revision_operands=0 ;;
	esac
	# The data-plane kinds take the same exemption for a stronger version of the same reason:
	# their recorded operands are flag names and database names, never filesystem paths, so
	# the rootish paragraph's mechanism — an empty variable widening a path to an absolute one
	# — describes nothing that can happen to them. They get their own paragraph instead.
	case $kind in
	supabase-* | prisma-* | rails-db-* | dropdb | psql-* | drizzle-kit-* | package-script-*)
		revision_operands=0
		DESTRUCTIVE_DATAPLANE=1
		;;
	esac
	# The script arm judged a NAME and never the script, which is a weaker basis than any other
	# kind in this tier has and gets its own paragraph rather than being folded into the shared
	# one. A rail that let a cleared prompt imply it had read the package manifest would be
	# claiming a check nobody performed.
	#
	# TWO ways that happens, and the second is why this is not a `kind` test alone: the name may
	# be one this dispatch recognises (`db:push`), or `npm run` may resolve a script whose name
	# collides with a tool the tier knows (`npm run dropdb` reaches the `dropdb` arm having read
	# nothing but a script name). Accepted miss in the other direction: `pnpm exec db:push` runs
	# a BINARY with that name and still gets the paragraph, which overstates the guard's
	# uncertainty rather than its coverage — the safe direction, and no such binary exists.
	case $kind in
	package-script-*) DESTRUCTIVE_SCRIPTNAME=1 ;;
	esac
	case ${DESTRUCTIVE_FROM_SCRIPT_NAME:-0} in 1) DESTRUCTIVE_SCRIPTNAME=1 ;; esac
	# Whether the verb DELETES or merely overwrites decides the advisory's lead sentence. The
	# shared text was written for `rm` and says "delete" seven times; against `git worktree
	# add`, which deletes nothing, every one of those is false, and an agent that notices is
	# entitled to classify the whole advisory as a false positive and rerun without looking.
	case $kind in
	rm | git-clean | git-rm | git-worktree-remove) DESTRUCTIVE_DELETES=1 ;;
	esac
	for w in "$@"; do
		case $w in *'$'*) DESTRUCTIVE_UNEXPANDED=1 ;; esac
		# `${S}/base` and `$S/base` are the same question; `$(cmd)/base` is not a
		# variable at all, and a `$` followed by anything else names nothing.
		bare=''
		# shellcheck disable=SC2016 # these patterns are literal `$` in command TEXT.
		case $w in
		'$('*) ;;
		'${'*) bare=${w#??} ;;
		'$'[A-Za-z_]*) bare=${w#?} ;;
		esac
		[ -n "$bare" ] || continue
		name=${bare%%[!A-Za-z0-9_]*}
		# The character terminating the name decides whether a default can rescue it.
		case ${bare#"$name"} in [:=?+-]*) continue ;; esac
		case $name in HOME | PWD | TMPDIR | ROOT) continue ;; esac
		# Only the spellings that carry a path separator can become an absolute path.
		case $bare in *'/'*) [ "$revision_operands" -eq 1 ] && DESTRUCTIVE_ROOTISH=1 ;; esac
	done
	# One entry per destructive SEGMENT, and the entry names the command kind as well as
	# the operands. Without the kind, `git clean -fdx` and a literal `rm -rf '<work tree>'`
	# were the same signature — a sentinel an operand can spell is not a sentinel. Operands
	# are `%q`-quoted before they are joined, so a space or a newline inside one target can
	# no longer read as the boundary between two: `rm -rf "a b"` and `rm -rf a b` are
	# different deletions and must not clear each other.
	if [ "$#" -eq 0 ]; then
		entry=$kind
	else
		# Normalised for the SIGNATURE only. The detection above reads the operand as
		# written, because `${d:?}` and `$d` differ in exactly the property it reports.
		local norm=()
		for w in "$@"; do
			normalize_operand "$w"
			norm+=("$NORMALIZED")
		done
		entry="$kind $(printf '%q\n' "${norm[@]}" | LC_ALL=C sort | tr '\n' ' ')"
	fi
	DESTRUCTIVE_TARGETS+=("$entry")
}

# True when the guard cannot see what a tree-mutating git verb is about to address. The verbs
# added after `git clean` are armed ONLY on this condition, and the narrowing is the whole
# reason they can ship at all.
#
# `git reset --hard HEAD~1`, `git checkout -f main` and `git worktree add wt HEAD` are ordinary,
# correct, high-frequency commands. A rail that advises on every one of them is exactly the
# "alarm that cries wolf" this file's own comment warns about: an agent learns to skim the
# advisory, and it then stops working for `rm -rf` too. So the rail costs its false-positive
# budget only where the target is genuinely unknown at scan time — the shape a public incident
# report showed, where `git worktree add -q --detach "$TEMP_WT" HEAD` ran with `$TEMP_WT` unset
# and the tree did not survive it (AMH ledger row DC011).
#
# TWO ways the target is unknown, and the second one is not optional. Gating on `$` ALONE ships
# a rail whose escape hatch is strictly more destructive than the thing it stops: `git reset
# --hard "$BASE"` is advised, and an agent that drops the operand to silence the complaint gets
# `git reset --hard`, which discards every uncommitted change in the tree. Dropping an argument
# is the first thing anyone tries when a rail objects to an argument, so a gate that rewards it
# is worse than no gate. `.` and `:/` are the same proposition spelled explicitly: the target is
# everything. Neither addition costs a false positive, because every common correct spelling of
# these verbs names a literal ref (`HEAD~1`, `origin/main`, a branch).
#
# `rm` and `git clean` are deliberately NOT gated this way and must not become so. Their
# operands name a deletion whatever they expand to, so a fully literal one still earns its one
# turn of thought; that is the older contract and its fixtures pin it.
#
# Accepted miss, stated because the gate's name over-promises otherwise: this is a WORD-level
# test and cannot tell a path operand from a revision one, so `git worktree add wt "$REF"`
# arms on a commit-ish whose destination is literal and safe. Separating them needs a git
# query, which no rail here may make.
operands_unknown_target() { # operands_unknown_target <whole-tree-when-empty> <operand>...
	local empty_is_whole_tree=$1
	shift
	[ "$#" -eq 0 ] && return "$empty_is_whole_tree"
	local w
	for w in "$@"; do
		case $w in
		*'$'* | '.' | ':/') return 0 ;;
		esac
	done
	return 1
}

# Which target a DATA-PLANE command names — recorded as FLAG NAMES and enumerated literals,
# never as a value. This is not tidiness: the signature is written to a state file and printed
# by `--advisory-report`, so recording the VALUE of a `--db-url` — a connection string, which
# carries the role password in its authority section — would put a live credential in both, the
# one thing this harness's hard boundaries forbid outright. (Spelling that URL out here as an
# example would put a credential-shaped literal in a shipped script, which the repository's own
# secret-shape scan rejects and should: see the runtime construction in the fixture.) So no
# value-bearing flag ever contributes its value, and a bare operand contributes itself only when
# it can not be a connection string (no `://`, no `@`).
#
# What survives is enough to tell the decisions apart that differ: `--local` from `--linked`,
# `dropdb staging_db` from `dropdb prod_db`. What does NOT survive is two different `--db-url`
# values, which share one signature and therefore one advisory. Stated rather than fixed — the
# alternative is hashing a credential-bearing string, and a rail that handles secrets to
# sharpen its own bookkeeping has made itself the incident.
DATA_PLANE_FLAGS=()
data_plane_flags() { # data_plane_flags <record-bare-operands:0|1> <word>...
	local record_bare=$1 w skip_next=0 bare_seen=0
	shift
	DATA_PLANE_FLAGS=()
	for w in "$@"; do
		if [ "$skip_next" -eq 1 ]; then
			skip_next=0
			continue
		fi
		case $w in
		--local | --linked | --remote | --force-reset | --accept-data-loss)
			DATA_PLANE_FLAGS+=("$w")
			;;
		# The name says WHICH knob was turned; `:given` says a value was supplied without
		# saying what it was.
		# `--shadow-database-url` joined this list with the arm that reads it, and it is the
		# one whose value is a connection string BY DEFINITION — the reported incident is a
		# production URL passed to it — so it must reduce to a name here like the rest.
		--db-url | --database-url | --shadow-database-url | --project-ref | --dbname | --url)
			DATA_PLANE_FLAGS+=("$w:given")
			skip_next=1
			;;
		--db-url=* | --database-url=* | --shadow-database-url=* | --project-ref=* | --dbname=* | --url=*)
			DATA_PLANE_FLAGS+=("${w%%=*}:given")
			;;
		# Options whose value is a SEPARATE word. Consuming it is not cosmetic: without this,
		# `dropdb -U prod scratch` and `dropdb -U scratch prod` both recorded the operand pair
		# {prod, scratch}, the signature sort made them identical, and clearing the advisory
		# for the one that drops `scratch` silently cleared it for the one that drops `prod` —
		# the per-target rearm defeated by an ordinary flag permutation rather than by evasion.
		#
		# A LIST, not a category, with the usual consequence: an option outside it has its
		# value read as an operand again. Extend it when a real invocation shows up rather
		# than guessing at the union of six tools' option tables.
		-h | -p | -U | -u | -d | -e | -o | -v | -F | -T | -L | -P | --host | --port | --username | --maintenance-db | --file | --schema | --workdir | --set | --variable | --output | --log-file | --package)
			skip_next=1
			;;
		-*) ;;
		*)
			# Only the FIRST bare operand. Every command in this tier that takes one takes
			# exactly one — a database name — so a second is a flag value this list did not
			# model, and recording it would reopen the permutation collision above by another
			# route.
			[ "$record_bare" -eq 1 ] || continue
			[ "$bare_seen" -eq 0 ] || continue
			bare_seen=1
			case $w in
			*://* | *@*) DATA_PLANE_FLAGS+=('target:redacted') ;;
			*) DATA_PLANE_FLAGS+=("$w") ;;
			esac
			;;
		esac
	done
	return 0
}

is_destructive_segment() {
	local raw=$1 w cmd recursive=1 force=1 descend=1 i=0
	local sub kind='' hard=1 staged=1 worktree_target=1
	local sql='' up='' j=0 matched=1 rest='' stmt=''
	local words=()
	local operands=() end_of_options=1
	# Per SEGMENT, not per command: `npm run db:push && dropdb app` is two decisions and the
	# second one read a real command word, so the script-name disclosure must not carry over.
	DESTRUCTIVE_FROM_SCRIPT_NAME=0
	split_words "$raw"
	words=(${SPLIT_WORDS[@]+"${SPLIT_WORDS[@]}"})
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
	# Package runners are how the data-plane tools below are ACTUALLY invoked — nobody types
	# a bare `prisma`. This strip is deliberately NOT part of the transparent-prefix loop
	# above: that loop feeds every rail in this script, including the secret scanners, and
	# widening it there would change what `cat`, `env` and the rest are judged against for the
	# sake of one tier.
	#
	# It DOES widen the destructive rail, which is a real behaviour change and is stated rather
	# than assumed away: this strip runs before the whole dispatch, so `npx rm -rf /tmp/x` and
	# `pnpm exec git clean -fdx` now reach the filesystem arms and are advised, where before
	# they were commands named `npx` and `pnpm` that no arm recognised. That is the correct
	# direction — the deletion is just as real for being run through a runner — and it has its
	# own fixtures.
	#
	# `npm` joined this list with the package-script arm below, and the widening is the same one
	# stated above: `npm exec prisma migrate reset` and `npm exec rm -rf x` now reach the arms
	# that judge them, where before they were a command called `npm`.
	#
	# Accepted miss, narrowed but not closed: a runner whose TOOL is hidden behind a script name
	# (a package.json script, a Makefile target, a justfile recipe). The tool is not in the
	# command text and no amount of prefix-stripping reaches it — see the header's note on
	# constructed commands. What IS in the command text is the script's NAME, and the arm below
	# judges the short list of names that say outright what they do; every other name, and every
	# Makefile or justfile target, stays in this miss.
	case $cmd in
	npx | pnpx | bunx)
		# `-p pkg` / `--package pkg` names the package to fetch, NOT the command to run, so
		# its value must be consumed: `npx -p prisma@5 prisma migrate reset` otherwise
		# resolves to a command called `prisma@5` that no arm recognises.
		while [ "$i" -lt "${#words[@]}" ]; do
			case ${words[$i]} in
			-p | --package | -c | --call) i=$((i + 2)) ;;
			-*) i=$((i + 1)) ;;
			*) break ;;
			esac
		done
		[ "$i" -lt "${#words[@]}" ] || return 1
		cmd=${words[$i]##*/}
		i=$((i + 1))
		;;
	npm | pnpm | yarn | bun)
		# The two-word options are a LIST with the usual consequence, and the workspace flags
		# are on it because leaving them off made six spellings of the incident's own command
		# silent: `npm -w api run db:push`, `npm --prefix ./app run db:push` and `pnpm -F api
		# db:push` all had the flag's VALUE read as the subcommand. `yarn workspace api
		# db:push` is a subcommand rather than a flag and stays missed.
		while [ "$i" -lt "${#words[@]}" ]; do
			case ${words[$i]} in
			-p | --package | --filter | -C | --dir | -w | --workspace | --prefix | -F | --cwd) i=$((i + 2)) ;;
			-*) i=$((i + 1)) ;;
			*) break ;;
			esac
		done
		[ "$i" -lt "${#words[@]}" ] || return 1
		case ${words[$i]} in
		exec | dlx | run | run-script | x)
			# `run` and `run-script` resolve a SCRIPT NAME; `exec`, `dlx` and `x` resolve a
			# binary. The advisory says something different in the first case and must know
			# which happened — keying that off the resolved KIND instead got it wrong in both
			# directions, telling an agent that `npm run dropdb` "drops a database outright"
			# on the strength of a script name, and withholding the same disclosure from
			# every data-plane kind reached that way.
			case ${words[$i]} in run | run-script) DESTRUCTIVE_FROM_SCRIPT_NAME=1 ;; esac
			i=$((i + 1))
			while [ "$i" -lt "${#words[@]}" ]; do
				case ${words[$i]} in
				-w | --workspace | --prefix | --filter | -F | -C | --dir | --cwd) i=$((i + 2)) ;;
				-*) i=$((i + 1)) ;;
				*) break ;;
				esac
			done
			[ "$i" -lt "${#words[@]}" ] || return 1
			;;
		esac
		cmd=${words[$i]##*/}
		i=$((i + 1))
		;;
	esac
	case $cmd in
	rm)
		for w in "${words[@]:i}"; do
			if [ "$end_of_options" -eq 0 ]; then
				operands+=("$w")
				continue
			fi
			case $w in
			--recursive) recursive=0 ;;
			--force) force=0 ;;
			# Everything after `--` is a path, including one that looks like a flag.
			--) end_of_options=0 ;;
			[^-]*) operands+=("$w") ;;
			-*)
				case ${w#-} in *[rR]*) recursive=0 ;; esac
				case ${w#-} in *f*) force=0 ;; esac
				;;
			esac
			done
		[ "$recursive" -eq 0 ] && [ "$force" -eq 0 ] || return 1
		record_destructive_targets rm ${operands[@]+"${operands[@]}"}
		;;
	git)
		# Skip git's global options, then dispatch on the subcommand.
		#
		# `-C`, `--git-dir` and `--work-tree` take their value as the NEXT word, and that
		# value is a PATH the whole command is then aimed at — so it is collected as an
		# operand instead of being thrown away. Discarding it hid the incident's own class
		# in the one place the guard deliberately drops words: git treats `-C ''` as a
		# no-op, so `git -C "$ROOT_DIR" reset --hard HEAD` with `ROOT_DIR` unset silently
		# hard-resets the CURRENT repository rather than the intended one.
		while [ "$i" -lt "${#words[@]}" ]; do
			w=${words[$i]}
			case $w in
			-C | --git-dir | --work-tree)
				[ $((i + 1)) -lt "${#words[@]}" ] && operands+=("${words[$((i + 1))]}")
				i=$((i + 2))
				;;
			--git-dir=* | --work-tree=*) operands+=("${w#*=}"); i=$((i + 1)) ;;
			-c) i=$((i + 2)) ;;
			-*) i=$((i + 1)) ;;
			*) break ;;
			esac
		done
		[ "$i" -lt "${#words[@]}" ] || return 1
		sub=${words[$i]}
		i=$((i + 1))
		case $sub in
		clean)
			# Require the short options containing both -f and -d. Clusters may be
			# ordered or split.
			for w in "${words[@]:i}"; do
				if [ "$end_of_options" -eq 0 ]; then
					operands+=("$w")
					continue
				fi
				case $w in
				-n | --dry-run) return 1 ;;
				--force) force=0 ;;
				--) end_of_options=0 ;;
				[^-]*) operands+=("$w") ;;
				--*) ;;
				-*)
					case ${w#-} in *n*) return 1 ;; esac
					case ${w#-} in *f*) force=0 ;; esac
					case ${w#-} in *d*) descend=0 ;; esac
					;;
				esac
				done
			[ "$force" -eq 0 ] && [ "$descend" -eq 0 ] || return 1
			# `git clean -fdx` names no pathspec and means "the whole work tree". The
			# kind prefix is what keeps that distinct from a pathspec-scoped clean and
			# from an operand-less `rm`, so no spellable sentinel is needed.
			record_destructive_targets git-clean ${operands[@]+"${operands[@]}"}
			;;
		worktree)
			# `add` populates a path, `remove` deletes one, and `move` takes a bare
			# destination path — all three carry the incident's shape. `list`, `prune`,
			# `lock` and `repair` take no destination an empty variable could redirect.
			case ${words[$i]:-} in add | remove | move) ;; *) return 1 ;; esac
			kind=git-worktree-${words[$i]}
			i=$((i + 1))
			for w in "${words[@]:i}"; do
				if [ "$end_of_options" -eq 0 ]; then
					operands+=("$w")
					continue
				fi
				case $w in
				--) end_of_options=0 ;;
				[^-]*) operands+=("$w") ;;
				-*) ;;
				esac
				done
			;;
		reset)
			# Only `--hard` discards the worktree. `--soft` and `--mixed` move a ref
			# and leave every file on disk, so they are not this rail's business.
			for w in "${words[@]:i}"; do
				if [ "$end_of_options" -eq 0 ]; then
					operands+=("$w")
					continue
				fi
				case $w in
				--hard) hard=0 ;;
				--) end_of_options=0 ;;
				[^-]*) operands+=("$w") ;;
				-*) ;;
				esac
				done
			[ "$hard" -eq 0 ] || return 1
			kind=git-reset-hard
			;;
		checkout | switch)
			# Accepted miss, stated rather than patched: `git checkout -- "$f"`
			# overwrites the worktree copy with no force flag at all, and is not caught
			# here. Reading a bare `--` as the destructive marker would collide with
			# the end-of-options handling every branch above shares, and the flagged
			# spellings are the ones an agent reaches for when it means to discard.
			for w in "${words[@]:i}"; do
				if [ "$end_of_options" -eq 0 ]; then
					operands+=("$w")
					continue
				fi
				case $w in
				--force | --discard-changes) force=0 ;;
				--) end_of_options=0 ;;
				[^-]*) operands+=("$w") ;;
				--*) ;;
				-*) case ${w#-} in *f*) force=0 ;; esac ;;
				esac
				done
			[ "$force" -eq 0 ] || return 1
			kind=git-$sub-force
			;;
		restore)
			# `git restore <path>` overwrites the worktree copy by default, so no flag
			# is required. `--staged` ALONE only unstages and leaves the file on disk;
			# that one is not destructive to the tree and must not be advised.
			for w in "${words[@]:i}"; do
				if [ "$end_of_options" -eq 0 ]; then
					operands+=("$w")
					continue
				fi
				case $w in
				--staged | -S) staged=0 ;;
				--worktree | -W) worktree_target=0 ;;
				--) end_of_options=0 ;;
				[^-]*) operands+=("$w") ;;
				-*) ;;
				esac
				done
			[ "$staged" -eq 0 ] && [ "$worktree_target" -ne 0 ] && return 1
			kind=git-restore
			;;
		rm)
			# The closest sibling the founding case has: `git rm -r -f "$X"` deletes
			# worktree files unconditionally, and before this dispatch existed its
			# absence was explained by the arm only knowing `clean`. It is not any more.
			for w in "${words[@]:i}"; do
				if [ "$end_of_options" -eq 0 ]; then
					operands+=("$w")
					continue
				fi
				case $w in
				-n | --dry-run) return 1 ;;
				--force) force=0 ;;
				-r) recursive=0 ;;
				--) end_of_options=0 ;;
				[^-]*) operands+=("$w") ;;
				--*) ;;
				-*)
					case ${w#-} in *n*) return 1 ;; esac
					case ${w#-} in *r*) recursive=0 ;; esac
					case ${w#-} in *f*) force=0 ;; esac
					;;
				esac
				done
			[ "$recursive" -eq 0 ] && [ "$force" -eq 0 ] || return 1
			kind=git-rm
			;;
		*) return 1 ;;
		esac
		# Every verb added after `clean` is armed only when the target is unknown at scan
		# time — see `operands_unknown_target` for why that narrowing is what lets them
		# ship, and why `$` alone was not enough.
		#
		# The argument is whether an EMPTY operand list means "everything". It does for
		# `git reset --hard`, which discards the whole worktree when given no revision;
		# every other verb here fails with a usage error instead, and arming on those
		# would spend the budget on a command that cannot do damage.
		if [ -n "$kind" ]; then
			case $kind in
			git-reset-hard) operands_unknown_target 0 ${operands[@]+"${operands[@]}"} || return 1 ;;
			*) operands_unknown_target 1 ${operands[@]+"${operands[@]}"} || return 1 ;;
			esac
			record_destructive_targets "$kind" ${operands[@]+"${operands[@]}"}
		fi
		;;
	# --- the DATA PLANE ---------------------------------------------------------
	#
	# Every arm above asks the same question — what does this command do to the working
	# tree — and every one of them can answer it from the command text. These cannot, and
	# that is the whole reason they are a separate tier rather than more verbs on the list.
	#
	# The shape, from the incident that earned it: an agent ran `supabase db reset` inside an
	# app directory while a human watched. That command drops the schema and replays
	# migrations from scratch. WHICH database it drops is not in the command — it comes from
	# a linked project ref, a config file resolved from the working directory, or
	# `$DATABASE_URL`, so the local and the production spelling are byte-identical. Nothing
	# stopped it: the local Docker daemon happened to be down, the command errored, and the
	# agent worked out what it had nearly done only after being asked. An incident whose
	# entire mitigation was an unrelated service being offline is the definition of an
	# unguarded one.
	#
	# So the check this tier asks for is different from the one above it. For `rm -rf "$S/x"`
	# the guard can name the hazard precisely and ask the agent to print an expansion. Here it
	# can only say: the target is not in what I can read, it is in what your environment
	# resolves, so go and read THAT and say which database this is. The advisory text carries
	# that difference (`DESTRUCTIVE_DATAPLANE`), and the path-shaped paragraphs are suppressed
	# for these kinds, because "if that variable is empty the command addresses an absolute
	# path" is simply false of a database reset and a rail is not allowed to be confidently
	# wrong.
	#
	# THE LIST IS EARNED BY ONE INCIDENT AND EXTENDED BY SHAPE, which is a weaker claim than
	# the filesystem rail can make and is stated rather than blurred. `supabase db reset` is
	# the recorded one. The others are here because they are the same command in a different
	# ecosystem — destructive by default, target from ambient config — and because a rail that
	# stops exactly one spelling teaches an agent the spelling instead of the class. They are
	# NOT an inventory; see the header for what is knowingly outside it.
	#
	# Unlike the tree-mutating git verbs, these are NOT gated on `operands_unknown_target`.
	# That gate exists because `git reset --hard HEAD~1` is a correct, high-frequency command
	# whose target is plain; there is no equivalently routine `supabase db reset` whose target
	# is plain, because the target is never in the command at all. These follow the older `rm`
	# contract instead: one turn of thought each, and a literal target does not buy silence.
	supabase)
		# `db reset` only. `supabase start`, `db diff`, `db push`, `migration new` and the
		# rest are ordinary development commands and must not be advised.
		#
		# Global options come before the subcommand and must be skipped to find it, the same
		# way the `git` arm skips `-C` and `--git-dir`. `--workdir` in particular decides which
		# project's config is resolved, which is this tier's whole subject.
		while [ "$i" -lt "${#words[@]}" ]; do
			case ${words[$i]} in
			--workdir | --profile) i=$((i + 2)) ;;
			-*) i=$((i + 1)) ;;
			*) break ;;
			esac
		done
		[ "${words[$i]:-}" = db ] || return 1
		i=$((i + 1))
		[ "${words[$i]:-}" = reset ] || return 1
		i=$((i + 1))
		kind=supabase-db-reset
		data_plane_flags 0 "${words[@]:i}"
		record_destructive_targets "$kind" ${DATA_PLANE_FLAGS[@]+"${DATA_PLANE_FLAGS[@]}"}
		;;
	prisma)
		case ${words[$i]:-} in
		migrate)
			# `migrate reset` drops and replays. `migrate dev`, `deploy`, `status` and
			# `resolve` do not, and `dev` is the one a developer runs all day.
			#
			# `--shadow-database-url` is the exception among them, and it is here because a
			# public report earned it rather than because it resembles the others: an agent
			# passed a production connection string to it on `migrate diff` and prisma did
			# what the flag says — it RESET the database at that URL before replaying, and 22
			# production tables went with it, two of which no migration described. The flag
			# names its own target, which makes this the one shape in the tier the guard
			# could read; it still cannot tell a scratch URL from a production one, so it
			# asks the same question the rest of the tier asks (AMH ledger row DC027).
			if [ "${words[$((i + 1))]:-}" = reset ]; then
				kind=prisma-migrate-reset
			else
				case " ${words[*]:i} " in
				*' --shadow-database-url '* | *' --shadow-database-url='*)
					kind=prisma-migrate-shadow-db
					;;
				*) return 1 ;;
				esac
			fi
			;;
		db)
			# `db push` alone PROMPTS before it destroys data and is a normal development
			# loop; it is the flag that says "do it without asking" that makes this the
			# same command as the others here.
			[ "${words[$((i + 1))]:-}" = push ] || return 1
			# Both spellings of each flag: `--accept-data-loss` and `--accept-data-loss=true`
			# are the same instruction, and matching only the bare one made the `=` form a
			# silent bypass of the tier.
			case " ${words[*]:i} " in
			*' --accept-data-loss '* | *' --accept-data-loss='* | *' --force-reset '* | *' --force-reset='*) ;;
			*) return 1 ;;
			esac
			kind=prisma-db-push-data-loss
			;;
		*) return 1 ;;
		esac
		i=$((i + 2))
		data_plane_flags 0 "${words[@]:i}"
		record_destructive_targets "$kind" ${DATA_PLANE_FLAGS[@]+"${DATA_PLANE_FLAGS[@]}"}
		;;
	rails | rake)
		# `db:migrate`, `db:seed` and `db:prepare` are the everyday tasks and are not here.
		case ${words[$i]:-} in
		db:drop) kind=rails-db-drop ;;
		db:reset) kind=rails-db-reset ;;
		db:schema:load) kind=rails-db-schema-load ;;
		*) return 1 ;;
		esac
		i=$((i + 1))
		data_plane_flags 0 "${words[@]:i}"
		record_destructive_targets "$kind" ${DATA_PLANE_FLAGS[@]+"${DATA_PLANE_FLAGS[@]}"}
		;;
	dropdb)
		# The one command in this tier that says what it does in its own name, and the one
		# whose bare operand IS the database name — worth recording, because `dropdb
		# scratch` and `dropdb app_production` are two different decisions.
		kind=dropdb
		data_plane_flags 1 "${words[@]:i}"
		record_destructive_targets "$kind" ${DATA_PLANE_FLAGS[@]+"${DATA_PLANE_FLAGS[@]}"}
		;;
	psql)
		# `psql` is a REPL and a script runner; the overwhelming majority of invocations are
		# reads. Only a statement passed inline with `-c`/`--command` is judged, and only for
		# the three verbs that destroy a whole object rather than rows a `WHERE` clause chose.
		# `DELETE FROM` is deliberately absent: `DELETE FROM t WHERE id = 1` is routine, and a
		# rail that fires on it is the alarm this file's own comments warn about.
		#
		# EVERY `-c` is collected, not the first. `psql -c "SELECT 1" -c "DROP DATABASE app"`
		# is a legal, ordinary two-statement invocation, and a scan that stopped at the first
		# statement read the harmless half and allowed the other. Bundled short options are
		# read too (`-Atc`, `-qc`, `-Xc`): psql accepts them and an agent writes them, so a
		# pattern requiring `c` in position two was not a narrowing, it was a hole.
		#
		# Accepted misses, in the fail-open direction: `-f script.sql` and a heredoc hide the
		# statement where no scanner here reads; irregular whitespace (`DROP  DATABASE`) does
		# not match; and every other client — `mysql -e`, `mongosh --eval`, an ORM's own shell
		# — is a different command this arm never sees. In the fail-CLOSED direction there is
		# one: a bundle whose `c` is not last (`psql -cA …`) is read as a glued statement.
		j=$i
		while [ "$j" -lt "${#words[@]}" ]; do
			case ${words[$j]} in
			-c | --command)
				j=$((j + 1))
				sql="$sql;${words[$j]:-}"
				;;
			--command=*) sql="$sql;${words[$j]#--command=}" ;;
			-c?*) sql="$sql;${words[$j]#-c}" ;;
			# A bundle ending in `c` takes the statement as the NEXT word, the way psql
			# itself reads it.
			-[!-]*c)
				j=$((j + 1))
				sql="$sql;${words[$j]:-}"
				;;
			esac
			j=$((j + 1))
		done
		[ -n "${sql//;/}" ] || return 1
		up=$(printf '%s' "$sql" | tr '[:lower:]' '[:upper:]')
		# POSITION, not presence — the D007 class, in the one arm of this script that reads a
		# foreign language. `SELECT * FROM truncate_log`, `\d truncate_log` and `WHERE action =
		# 'truncate'` all NAME the verb and destroy nothing, and the first version of this arm
		# advised every one of them: a fail-CLOSED false positive on a read, in a rail whose
		# stated reason for excluding `DELETE FROM` was that it must not cry wolf. So each
		# statement is split out and judged by what it STARTS with.
		matched=1
		rest=$up
		while [ -n "$rest" ]; do
			stmt=${rest%%;*}
			if [ "$stmt" = "$rest" ]; then rest=''; else rest=${rest#*;}; fi
			stmt=${stmt#"${stmt%%[![:space:]]*}"}
			# A leading `BEGIN` does not make the statement after it invisible.
			case $stmt in
			BEGIN | 'BEGIN '*) stmt=${stmt#BEGIN} ; stmt=${stmt#"${stmt%%[![:space:]]*}"} ;;
			esac
			case $stmt in
			'TRUNCATE '* | 'DROP DATABASE '* | 'DROP SCHEMA '*)
				matched=0
				break
				;;
			esac
		done
		[ "$matched" -eq 0 ] || return 1
		# The statement TEXT is never recorded: it is arbitrary SQL and can carry a role
		# password. The kind alone is the signature, so every destructive `psql -c` in a
		# session shares one advisory unless a target flag distinguishes it.
		kind=psql-destructive-statement
		data_plane_flags 0 "${words[@]:i}"
		record_destructive_targets "$kind" ${DATA_PLANE_FLAGS[@]+"${DATA_PLANE_FLAGS[@]}"}
		;;
	drizzle-kit)
		# `push` applies a schema straight to the database, emitting the DROP TABLE and DROP
		# COLUMN statements the diff implies and bypassing migration files entirely. It is here
		# and bare `prisma db push` is not, which is an asymmetry that has to be argued rather
		# than assumed: prisma's push PROMPTS before a destructive statement and that prompt is
		# its documented default, while drizzle's confirmation is reported failing outright —
		# `drizzle-team/drizzle-orm` issue 5249 has DROP TABLE executing with no prompt at all
		# after `strict` was silently deprecated in the 1.0 beta. A tool whose safeguard is
		# reported absent is not carrying the weight the prisma exception rests on.
		#
		# `push:pg`, `push:mysql` and `push:sqlite` are the older per-dialect spellings of the
		# same command. `generate`, `migrate`, `check`, `up` and `studio` are the everyday ones
		# and are not advised; `drop` is deliberately absent, because no public report shows it
		# destroying stored data and this tier's list is earned by reports (AMH ledger row DC027).
		#
		# The four spellings are ENUMERATED and the kind is built from a literal, never from the
		# word. A `push:*` glob put command text into the kind, and the kind is the one part of a
		# signature `record_destructive_targets` does not `%q`-quote: `drizzle-kit 'push:x<newline>
		# package-script-db:push'` wrote a second line into the state file that was a byte-exact
		# forgery of another command's signature, and `npm run db:push` then ran unadvised. That
		# is this file's own rule — a sentinel an operand can spell is not a sentinel — so no arm
		# here may interpolate an unvalidated word into a kind.
		case ${words[$i]:-} in
		push) kind=drizzle-kit-push ;;
		push:pg) kind=drizzle-kit-push-pg ;;
		push:mysql) kind=drizzle-kit-push-mysql ;;
		push:sqlite) kind=drizzle-kit-push-sqlite ;;
		*) return 1 ;;
		esac
		i=$((i + 1))
		data_plane_flags 0 "${words[@]:i}"
		record_destructive_targets "$kind" ${DATA_PLANE_FLAGS[@]+"${DATA_PLANE_FLAGS[@]}"}
		;;
	# The package script, which is the shape the most widely reported incident of this kind
	# actually ran: an agent executed `npm run db:push` against a production database during a
	# stated code freeze and the tables were dropped and replaced with empty ones. Every arm
	# above would have seen a command called `npm`. The runner strip resolves the script NAME
	# into `cmd`, so `npm run db:push`, `pnpm db:push`, `yarn db:push` and `bun run db:push`
	# all arrive here as `db:push`.
	#
	# The list is NAMES, and each is a name that says what it does: the incident's own, the rails
	# task names this tier already covers in their native spelling, prisma's `migrate:reset`, and
	# laravel's `migrate:fresh`/`db:wipe`. It is matched WHOLE — `build:db:pushdocs` is a
	# different script and is not this one — which is the D007 discipline in the arm most exposed
	# to it, since a script name is free text an author chose.
	#
	# What this arm can NOT do is the reason its advisory carries an extra paragraph: the script
	# body is in the package manifest and no scanner here opens a file to classify a command
	# (AMH ledger row DB027). So a harmless `db:push` is advised anyway and a destructive
	# `seed` is not advised at all, and the text says so rather than letting the prompt imply
	# it read the script.
	db:push | db:reset | db:drop | db:wipe | db:nuke | db:schema:load | schema:load | \
		migrate:reset | db:migrate:reset | migrate:fresh | migrate:refresh)
		kind=package-script-$cmd
		data_plane_flags 0 "${words[@]:i}"
		record_destructive_targets "$kind" ${DATA_PLANE_FLAGS[@]+"${DATA_PLANE_FLAGS[@]}"}
		;;
	*) return 1 ;;
	esac
	# `clean`, `rm` and the data-plane arms return through their own
	# `record_destructive_targets`; the shared `if` above yields 0 when `kind` is empty.
	# Saying so explicitly keeps that from being a puzzle a later reader has to re-derive.
	return 0
}

is_destructive_command() {
	local cmd=$1 seg found=1
	DESTRUCTIVE_TARGETS=()
	DESTRUCTIVE_UNEXPANDED=0
	DESTRUCTIVE_ROOTISH=0
	DESTRUCTIVE_DELETES=0
	DESTRUCTIVE_DATAPLANE=0
	DESTRUCTIVE_SCRIPTNAME=0
	cmd=$(strip_heredocs "$cmd")
	# Every destructive segment is scanned, not just the first. A command that deletes two
	# path sets is two decisions, and the advisory should be able to name both — stopping
	# at the first match is how the dangerous half of `rm -rf tmp/x && rm -rf "$S/base"`
	# would inherit the safe half's verdict.
	split_segments "$cmd"
	for seg in ${SEGMENTS[@]+"${SEGMENTS[@]}"}; do
		[ -n "${seg// /}" ] || continue
		is_destructive_segment "$seg" && found=0
	done
	return "$found"
}

# The signature a repeated attempt must match to be let through. It is the target set, not
# the category: see the rearm note in `needs_one_time_advisory`.
destructive_signature() {
	# Entries are built by record_destructive_targets and contain no newline: the kind is a
	# literal and every operand went through `%q`, which renders a newline as `$'\n'`. So
	# joining lines here cannot flatten two entries into one, and the state file's
	# one-signature-per-line format holds without a second round of escaping.
	if [ "${#DESTRUCTIVE_TARGETS[@]}" -eq 0 ]; then
		printf '%s' '<nothing recorded>'
		return
	fi
	# Keep this sort in-process. The 7.0.1 release-tag run on stock macOS Bash 3.2
	# produced collision symptoms in all eight distinct-target rearm fixtures while this
	# nested pipeline built the signature. Removing the implicated process boundary keeps
	# signature construction in the shell; the log does not isolate which pipeline stage
	# lost the distinction, so this deliberately claims no narrower root cause.
	local LC_ALL=C entry i inserted
	local sorted=()
	for entry in "${DESTRUCTIVE_TARGETS[@]}"; do
		inserted=0
		for ((i = 0; i < ${#sorted[@]}; i++)); do
			if [[ $entry < ${sorted[$i]} ]]; then
				sorted=("${sorted[@]:0:i}" "$entry" "${sorted[@]:i}")
				inserted=1
				break
			fi
		done
		[ "$inserted" -eq 1 ] || sorted+=("$entry")
	done
	printf '%s|' "${sorted[@]}"
}

warn_ladder_tail() {
	local cmd=$1 seg lead prev_ladder=0
	case $cmd in *ladder.sh*tail*) ;; *) return 0 ;; esac
	# Warn only for the ordinary mistaken shape: a direct ladder invocation whose
	# output is piped to tail. Reuse the shell-ish segment and word scanners so quoted
	# prose like a commit message stays data, not a warning.
	cmd=$(strip_heredocs "$cmd")
	split_segments "$cmd"
	for seg in ${SEGMENTS[@]+"${SEGMENTS[@]}"}; do
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
	done
}

check_command() {
	local cmd=$1
	local seg
	BLOCK_REASON=''
	WARN_REASON=''
	ADVISORY_REASON=''
	DOTENV_ADVISORY_REASON=''
	KEYMATERIAL_ADVISORY_REASON=''
	if needs_one_time_advisory dotenv "$cmd"; then
		BLOCK_REASON=$ADVISORY_REASON
		return 1
	fi
	if needs_one_time_advisory keymaterial "$cmd"; then
		BLOCK_REASON=$ADVISORY_REASON
		return 1
	fi
	if needs_one_time_advisory destructive "$cmd"; then
		BLOCK_REASON=$ADVISORY_REASON
		return 1
	fi
	warn_ladder_tail "$cmd"
	cmd=$(strip_heredocs "$cmd")
	split_segments "$cmd"
	# Same fail-closed rule as check_segment's, one level up: a non-blank command that
	# yields no segments has not been judged, and "no segments" must not read as "nothing
	# to object to" (AMH ledger row DC002).
	if parse_produced_nothing "$cmd" "${#SEGMENTS[@]}"; then
		BLOCK_REASON="The command guard could not parse this command: it has text but produced no segments to judge. This is a defect in the guard, not a verdict about your command — nothing was checked, so nothing may be allowed on that basis. Report it with the command text; re-running will not help."
		return 1
	fi
	for seg in ${SEGMENTS[@]+"${SEGMENTS[@]}"}; do
		[ -n "${seg// /}" ] || continue
		check_segment "$seg" || return 1
	done
	return 0
}

# --- hook payload -----------------------------------------------------------
extract_command() { # fail-open: print nothing if the payload is not what we expect
	local payload=$1
	if command -v python3 >/dev/null 2>&1; then
		printf '%s' "$payload" | python3 -c 'import json,sys
try:
    d = json.load(sys.stdin)
    if d.get("tool_name") == "Bash":
        print(d.get("tool_input", {}).get("command", ""))
except Exception:
    pass' 2>/dev/null
	else
		# Keep the bash/git/coreutils baseline useful when Python is absent. This
		# deliberately narrow fallback accepts only an object-shaped Bash payload and
		# the documented tool_input.command spelling; anything ambiguous fails open.
		case $payload in
		'{'*'}') ;;
		*) return 0 ;;
		esac
		#
		# Here-strings, NOT `printf ... | grep -q` and `... | head -1`, and here the
		# difference is the rail standing DOWN rather than a false alarm. `grep -q` exits
		# at its first match; a writer with bytes still pending then takes EPIPE, and this
		# file runs under `pipefail`, which promotes that to the pipeline's status — so a
		# SUCCESSFUL match reads as a failure and `|| return 0` fails open on a Bash
		# command nobody inspected. A here-string's writer is not a pipeline member, so
		# nothing it does reaches `PIPESTATUS` and there is no status to promote. (On bash
		# >= 5.1 a here-string is backed by a temporary file only above the pipe-buffer
		# size, so "it is a file, not a pipe" is NOT the reason and would fail exactly
		# where this defect lives. Older bash, 3.2 included, uses a temp file for every
		# here-string whatever its size — which is why the reason has to be PIPESTATUS
		# and not the backing: only PIPESTATUS is true of every version.) Size alone does
		# not reproduce it: grep cannot match until it has
		# a whole LINE, so a single-line payload is consumed entirely no matter how long
		# it is. It takes a payload that is BOTH multi-line and past the pipe buffer —
		# pretty-printed JSON — which the `case` above accepts and the fixture builds.
		grep -qE '"tool_name"[[:space:]]*:[[:space:]]*"Bash"' <<<"$payload" || return 0
		# `head -1` is the same shape with `head` as the early exit, and there the status
		# lands on the FUNCTION. Substitute first, then take the first line with a
		# parameter expansion, so no reader can leave. The `if` keeps the empty case
		# returning 0, which is what the piped form did and what fail-open means here.
		local extracted
		extracted=$(sed -n 's/.*"tool_input"[[:space:]]*:[[:space:]]*{[^}]*"command"[[:space:]]*:[[:space:]]*"\(.*\)"[[:space:]]*}.*/\1/p' <<<"$payload")
		if [ -n "$extracted" ]; then printf '%s\n' "${extracted%%$'\n'*}"; fi
	fi
}

# Set ONLY on the path where a pass means the command actually runs next. `--command` is an
# inspection: it answers "would this be blocked" and executes nothing, so counting it as the
# command coming back would be false on its face — and it would also hand any session a way to
# clear its own abandoned-advisory line by asking the guard about the text twice. The rail's
# threat model is mistakes rather than evasion, but a record that a spectator can write is not
# a record of anything.
HOOK_INVOCATION=0

run_hook() {
	local payload cmd
	HOOK_INVOCATION=1
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

# --- pre-push rail (git-native, P13) ----------------------------------------
# Git feeds a pre-push hook one line per ref on stdin:
#   <local-ref> SP <local-sha> SP <remote-ref> SP <remote-sha>
# The remote sha is all-zeros when the branch is being CREATED, and the local sha is
# all-zeros when it is being DELETED. This rail guards the same publication invariants as the
# `--command` push rail — default-branch, force and deletion — but judged by OUTCOME rather
# than by flag. Neither rail checks the branch NAMESPACE; the two are still not identical:
# judging effect not flag, the single non-fast-forward test catches `--force`,
# `--force-with-lease` and a leading `+` refspec together, while a fast-forward `--force` the
# flag rail blocks is allowed here because no history is rewritten; and where the ancestry
# cannot be decided (a shallow clone's missing objects) the test fails OPEN, the direction
# every rail here fails. The prefix check is deliberately absent from BOTH (AMH ledger row
# DA022): the harness assigns branch names the repository does not name, so a prefix rail
# rejects every legitimately-assigned branch. This rail never had one; the command rail above
# carried one until it blocked a correctly assigned branch, which is what DA022 predicted.
is_zero_sha() { # true when the arg is non-empty and entirely '0' (git's null sha, any width)
	case $1 in "" | *[!0]*) return 1 ;; *) return 0 ;; esac
}

# Wrapped so the self-test can stub it: the real ancestry test needs commit objects a
# string-only self-test does not have, so P12's glue concern wants the wiring pinned apart
# from git's own behaviour. 0 = ancestor (fast-forward), 1 = not, 2 = undetermined.
prepush_is_ancestor() { # <maybe-ancestor> <descendant>
	git merge-base --is-ancestor "$1" "$2" 2>/dev/null
	case $? in 0) return 0 ;; 1) return 1 ;; *) return 2 ;; esac
}

prepush_verdict() { # <local-ref> <local-sha> <remote-ref> <remote-sha> -> 0 allow, 1 block
	local remote_ref=$3 local_sha=$2 remote_sha=$4
	case $remote_ref in
	"refs/heads/$DEFAULT_BRANCH" | "$DEFAULT_BRANCH")
		# Checked before the delete case, so this reason must fit a delete of the default
		# branch too — "targets", not "updates".
		BLOCK_REASON="This push targets \`$DEFAULT_BRANCH\` on the remote, which is denied (AMH P13). Push your session branch instead and let the owner merge via squash PR."
		return 1
		;;
	refs/tags/*)
		# Git hands this rail the RESOLVED ref, so unlike the command rail above it sees
		# `git push origin v1` as `refs/tags/v1` — the bare-name miss the flag rail has to
		# accept does not exist here. Checked before the delete case: deleting a published
		# tag is an owner step too.
		BLOCK_REASON="This push publishes or moves the tag \`$remote_ref\`. Release and tag actions are owner steps (AMH P13) — record the tag you want in the Owner queue rather than pushing it."
		return 1
		;;
	esac
	if is_zero_sha "$local_sha"; then
		BLOCK_REASON="This push DELETES the remote branch \`$remote_ref\`, which rewrites published history and is denied (AMH P7). Leave branch cleanup to the owner or the forge's post-merge pruning."
		return 1
	fi
	# A non-zero remote sha means the remote branch already exists; only then can a push be
	# a non-fast-forward. An undetermined result (missing objects, shallow clone) fails OPEN.
	if ! is_zero_sha "$remote_sha"; then
		prepush_is_ancestor "$remote_sha" "$local_sha"
		if [ $? -eq 1 ]; then
			BLOCK_REASON="This push to \`$remote_ref\` is not a fast-forward — it would overwrite commits the remote already has (a force / non-fast-forward push), and pushed checkpoints are immutable (AMH P7). If the branch diverged, merge the default branch in — never rewrite pushed history. A history rewrite is owner-executed and only for a leaked-credential incident."
			return 1
		fi
	fi
	return 0
}

run_prepush() {
	local local_ref local_sha remote_ref remote_sha
	# The trailing `_` absorbs any extra field so it is not folded into remote_sha.
	while read -r local_ref local_sha remote_ref remote_sha _; do
		# Fail OPEN on a malformed line (P13): a line missing any of the four fields was not
		# understood, and an unparsed line is not a licence to block the push.
		if [ -z "$local_ref" ] || [ -z "$local_sha" ] || [ -z "$remote_ref" ] || [ -z "$remote_sha" ]; then
			continue
		fi
		if ! prepush_verdict "$local_ref" "$local_sha" "$remote_ref" "$remote_sha"; then
			printf 'BLOCKED by the AMH pre-push rail.\n\n%s\n' "$BLOCK_REASON" >&2
			exit 2
		fi
	done
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
st_prepush_blocked() { # <local-ref> <local-sha> <remote-ref> <remote-sha>
	if prepush_verdict "$1" "$2" "$3" "$4"; then
		printf 'SELF-TEST FAIL: pre-push should have been BLOCKED: %s %s %s %s\n' "$1" "$2" "$3" "$4" >&2
		ST_FAILS=$((ST_FAILS + 1))
	fi
}
st_prepush_allowed() { # <local-ref> <local-sha> <remote-ref> <remote-sha>
	if ! prepush_verdict "$1" "$2" "$3" "$4"; then
		printf 'SELF-TEST FAIL: pre-push should have been ALLOWED: %s %s %s %s\n   reason given: %s\n' "$1" "$2" "$3" "$4" "$BLOCK_REASON" >&2
		ST_FAILS=$((ST_FAILS + 1))
	fi
}
# Stub the ancestry seam to a fixed outcome so the fast-forward WIRING is pinned here without
# commit objects; the real `git merge-base` glue is exercised end-to-end in
# test-ladder-guards.sh (P12: glue a string-only fixture cannot see).
st_prepush_ff() { # <ff|nonff|undetermined> <block|allow> <l-ref> <l-sha> <r-ref> <r-sha>
	local mode=$1 expect=$2 saved
	saved=$(declare -f prepush_is_ancestor)
	case $mode in
	ff) prepush_is_ancestor() { return 0; } ;;
	nonff) prepush_is_ancestor() { return 1; } ;;
	undetermined) prepush_is_ancestor() { return 2; } ;;
	esac
	if [ "$expect" = block ]; then st_prepush_blocked "$3" "$4" "$5" "$6"; else st_prepush_allowed "$3" "$4" "$5" "$6"; fi
	eval "$saved"
}
st_parse_integration() { # st_parse_integration <parser to blind: words|segments>
	# The predicate fixtures above pin a truth table; this one pins the WIRING, which is
	# the part that actually changed. Blind one parser so it hands back an empty array for
	# a command that must never be allowed, and require the guard to deny it AND to say the
	# denial is its own defect. Against the pre-8.0.0 script the same blinding produced
	# exit 0 — the fixture fails where the behaviour is absent, which is what earns it.
	local saved rc
	case $1 in
	words)
		saved=$(declare -f split_words)
		split_words() { SPLIT_WORDS=(); }
		;;
	segments)
		saved=$(declare -f split_segments)
		split_segments() { SEGMENTS=(); }
		;;
	esac
	check_command 'git push --force origin main'
	rc=$?
	eval "$saved"
	if [ "$rc" -eq 0 ]; then
		printf 'SELF-TEST FAIL: a blinded %s parser ALLOWED a force push\n' "$1" >&2
		ST_FAILS=$((ST_FAILS + 1))
		return 0
	fi
	case $BLOCK_REASON in
	*'could not parse'*) ;;
	*)
		printf 'SELF-TEST FAIL: a blinded %s parser blocked without naming the guard defect: %s\n' "$1" "$BLOCK_REASON" >&2
		ST_FAILS=$((ST_FAILS + 1))
		;;
	esac
}
st_parse_sanity() { # st_parse_sanity <expect: defect|normal> <text> <count>
	local want=$1 text=$2 count=$3
	if parse_produced_nothing "$text" "$count"; then
		[ "$want" = defect ] && return 0
		printf 'SELF-TEST FAIL: parse sanity called a normal parse a defect: text=%s count=%s\n' "$text" "$count" >&2
	else
		[ "$want" = normal ] && return 0
		printf 'SELF-TEST FAIL: parse sanity missed a defect: text=%s count=%s\n' "$text" "$count" >&2
	fi
	ST_FAILS=$((ST_FAILS + 1))
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

# The `.pem`/`.key` advisory needs BOTH directions fixtured, and each needs a state file of
# its own that has never fired. The negative direction is the load-bearing one: `Object.keys`
# is ordinary program text, and with a spent state file every fixture for it passes whatever
# the pattern says, which is exactly the fixture-that-cannot-fail shape.
st_keymaterial_advisory() { # st_keymaterial_advisory <expect: fires|silent> <command>
	local expect=$1 cmd=$2 state old_set old_state
	old_set=${KEYMATERIAL_ADVISORY_STATE+x}
	old_state=${KEYMATERIAL_ADVISORY_STATE:-}
	state=$(mktemp "${TMPDIR:-/tmp}/amh-keymaterial-advisory-test.XXXXXX") || exit 1
	rm -f -- "$state"
	KEYMATERIAL_ADVISORY_STATE=$state
	if [ "$expect" = fires ]; then
		if check_command "$cmd"; then
			printf 'SELF-TEST FAIL: should have had one-time key-material advisory: %s\n' "$cmd" >&2
			ST_FAILS=$((ST_FAILS + 1))
		elif [ -z "$KEYMATERIAL_ADVISORY_REASON" ]; then
			printf 'SELF-TEST FAIL: key-material advisory did not explain itself in its own voice: %s\n' "$cmd" >&2
			ST_FAILS=$((ST_FAILS + 1))
		elif ! check_command "$cmd"; then
			printf 'SELF-TEST FAIL: second key-material attempt should have reached normal rails: %s\n   reason given: %s\n' "$cmd" "$BLOCK_REASON" >&2
			ST_FAILS=$((ST_FAILS + 1))
		fi
	elif ! check_command "$cmd"; then
		printf 'SELF-TEST FAIL: should NOT have had a key-material advisory: %s\n   reason given: %s\n' "$cmd" "$BLOCK_REASON" >&2
		ST_FAILS=$((ST_FAILS + 1))
	fi
	rm -f -- "$state"
	if [ -n "$old_set" ]; then KEYMATERIAL_ADVISORY_STATE=$old_state; else unset KEYMATERIAL_ADVISORY_STATE; fi
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

# The rearm fixture, and the load-bearing one for this rail: ONE state file across both
# commands, so it fails if a spent advisory covers a deletion aimed somewhere new. Without
# it, "fires once per target" is a claim in a comment.
st_destructive_rearms_per_target() { # st_destructive_rearms_per_target <first> <second>
	local first=$1 second=$2 state old_set old_state
	old_set=${DESTRUCTIVE_ADVISORY_STATE+x}
	old_state=${DESTRUCTIVE_ADVISORY_STATE:-}
	state=$(mktemp "${TMPDIR:-/tmp}/amh-destructive-rearm-test.XXXXXX") || exit 1
	rm -f -- "$state"
	DESTRUCTIVE_ADVISORY_STATE=$state
	if check_command "$first"; then
		printf 'SELF-TEST FAIL: first deletion should have been advised: %s\n' "$first" >&2
		ST_FAILS=$((ST_FAILS + 1))
	elif ! check_command "$first"; then
		printf 'SELF-TEST FAIL: rerunning the SAME deletion should proceed: %s\n   reason given: %s\n' "$first" "$BLOCK_REASON" >&2
		ST_FAILS=$((ST_FAILS + 1))
	elif check_command "$second"; then
		printf 'SELF-TEST FAIL: a deletion aimed at a NEW target must be advised even after an earlier one was cleared: %s (after %s)\n' "$second" "$first" >&2
		ST_FAILS=$((ST_FAILS + 1))
	fi
	rm -f -- "$state"
	if [ -n "$old_set" ]; then DESTRUCTIVE_ADVISORY_STATE=$old_state; else unset DESTRUCTIVE_ADVISORY_STATE; fi
}

# Sorting is semantic, not cosmetic: the same target SET in a different operand order is
# the same deletion and must consume the same advisory. Without this direction, the rearm
# fixtures prove only that different sets differ; a no-op or order-sensitive "sort" passes.
st_destructive_same_target_set() { # <first spelling> <same targets, different order>
	local first=$1 second=$2 state old_set old_state
	old_set=${DESTRUCTIVE_ADVISORY_STATE+x}
	old_state=${DESTRUCTIVE_ADVISORY_STATE:-}
	state=$(mktemp "${TMPDIR:-/tmp}/amh-destructive-same-set-test.XXXXXX") || exit 1
	rm -f -- "$state"
	DESTRUCTIVE_ADVISORY_STATE=$state
	if check_command "$first"; then
		printf 'SELF-TEST FAIL: first deletion should have been advised: %s\n' "$first" >&2
		ST_FAILS=$((ST_FAILS + 1))
	elif ! check_command "$second"; then
		printf 'SELF-TEST FAIL: the same target set in a different order should proceed: %s (after %s)\n' "$second" "$first" >&2
		ST_FAILS=$((ST_FAILS + 1))
	fi
	rm -f -- "$state"
	if [ -n "$old_set" ]; then DESTRUCTIVE_ADVISORY_STATE=$old_state; else unset DESTRUCTIVE_ADVISORY_STATE; fi
}

# The report is the only OBSERVED thing this rail can say about compliance, so pin both
# halves: an advisory that was re-attempted must NOT be listed, and one that was abandoned
# must be. A report that lists everything is as useless as one that lists nothing, and both
# pass a fixture that only counts lines.
st_advisory_report() { # st_advisory_report <abandoned cmd> <resumed cmd>
	local abandoned=$1 resumed=$2 state old_set old_state out
	old_set=${DESTRUCTIVE_ADVISORY_STATE+x}
	old_state=${DESTRUCTIVE_ADVISORY_STATE:-}
	state=$(mktemp "${TMPDIR:-/tmp}/amh-destructive-report-test.XXXXXX") || exit 1
	rm -f -- "$state"
	DESTRUCTIVE_ADVISORY_STATE=$state
	local old_hook=$HOOK_INVOCATION
	HOOK_INVOCATION=1                        # the marker is hook-path only; see run_hook
	check_command "$abandoned" || :          # advised, never re-attempted
	check_command "$resumed" || :            # advised...
	check_command "$resumed" || :            # ...and re-attempted
	HOOK_INVOCATION=$old_hook
	out=$(advisory_report)
	case $out in
	*"${abandoned#rm -rf }"*) ;;
	*)
		printf 'SELF-TEST FAIL: the report omitted an abandoned advisory: %s\n   report: %s\n' "$abandoned" "$out" >&2
		ST_FAILS=$((ST_FAILS + 1))
		;;
	esac
	case $out in
	*"${resumed#rm -rf }"*)
		printf 'SELF-TEST FAIL: the report listed a re-attempted advisory: %s\n   report: %s\n' "$resumed" "$out" >&2
		ST_FAILS=$((ST_FAILS + 1))
		;;
	esac
	rm -f -- "$state" "$state.resumed"
	if [ -n "$old_set" ]; then DESTRUCTIVE_ADVISORY_STATE=$old_state; else unset DESTRUCTIVE_ADVISORY_STATE; fi
}

# The advisory's TEXT is the whole intervention for this rail — nothing downstream consumes
# it, so a silently emptied sentence would cost nothing mechanically and everything in
# practice. Pin the clause that names the failure mode.
st_destructive_reason_names() { # st_destructive_reason_names <substring> <command>
	local want=$1 cmd=$2 state old_set old_state
	old_set=${DESTRUCTIVE_ADVISORY_STATE+x}
	old_state=${DESTRUCTIVE_ADVISORY_STATE:-}
	state=$(mktemp "${TMPDIR:-/tmp}/amh-destructive-reason-test.XXXXXX") || exit 1
	rm -f -- "$state"
	DESTRUCTIVE_ADVISORY_STATE=$state
	if check_command "$cmd"; then
		printf 'SELF-TEST FAIL: should have been advised: %s\n' "$cmd" >&2
		ST_FAILS=$((ST_FAILS + 1))
	else
		case $ADVISORY_REASON in
		*"$want"*) ;;
		*)
			printf 'SELF-TEST FAIL: destructive advisory did not mention %s: %s\n   reason given: %s\n' "$want" "$cmd" "$ADVISORY_REASON" >&2
			ST_FAILS=$((ST_FAILS + 1))
			;;
		esac
	fi
	rm -f -- "$state"
	if [ -n "$old_set" ]; then DESTRUCTIVE_ADVISORY_STATE=$old_state; else unset DESTRUCTIVE_ADVISORY_STATE; fi
}

# The negative direction for the strongest paragraph. Without it, widening the ROOTISH test
# to anything starting with `$` passes every fixture while telling an agent to check a
# variable that does not exist — a rail is not allowed to be confidently wrong.
st_destructive_reason_lacks() { # st_destructive_reason_lacks <substring> <command>
	local unwanted=$1 cmd=$2 state old_set old_state
	old_set=${DESTRUCTIVE_ADVISORY_STATE+x}
	old_state=${DESTRUCTIVE_ADVISORY_STATE:-}
	state=$(mktemp "${TMPDIR:-/tmp}/amh-destructive-lacks-test.XXXXXX") || exit 1
	rm -f -- "$state"
	DESTRUCTIVE_ADVISORY_STATE=$state
	if check_command "$cmd"; then
		printf 'SELF-TEST FAIL: should have been advised: %s\n' "$cmd" >&2
		ST_FAILS=$((ST_FAILS + 1))
	else
		case $ADVISORY_REASON in
		*"$unwanted"*)
			printf 'SELF-TEST FAIL: destructive advisory should NOT have claimed %s: %s\n   reason given: %s\n' "$unwanted" "$cmd" "$ADVISORY_REASON" >&2
			ST_FAILS=$((ST_FAILS + 1))
			;;
		esac
	fi
	rm -f -- "$state"
	if [ -n "$old_set" ]; then DESTRUCTIVE_ADVISORY_STATE=$old_state; else unset DESTRUCTIVE_ADVISORY_STATE; fi
}

# The one fixture in this suite whose subject is what the guard WRITES DOWN rather than what it
# decides. A data-plane command can carry a live password in a `--db-url`, and the signature is
# persisted to a state file and printed by `--advisory-report`, so recording an operand value
# here would disclose it twice — the hard boundary this whole script exists to serve, defeated
# by the rail's own bookkeeping.
#
# The value is CONSTRUCTED AT RUNTIME. A realistic-looking password stored in this file would be
# a secret-shaped literal in the repository, which is the AMH ledger row D004 class: the fixture
# would then trip the very scanners it sits beside.
st_dataplane_signature_omits_value() {
	local state old_set old_state secret cmd sig recorded url
	old_set=${DESTRUCTIVE_ADVISORY_STATE+x}
	old_state=${DESTRUCTIVE_ADVISORY_STATE:-}
	state=$(mktemp "${TMPDIR:-/tmp}/amh-dataplane-signature-test.XXXXXX") || exit 1
	rm -f -- "$state"
	DESTRUCTIVE_ADVISORY_STATE=$state
	secret="pw$$-${RANDOM:-0}-omitme"
	# Assembled in pieces so no LITERAL in this file is credential-shaped. The repository's
	# secret-shape scan anchors on a scheme, a userinfo pair and the `@` that closes it, and it
	# is right to — this comment cannot spell the shape out either. A fixture that
	# stores that shape whole is the thing it is scanning for, whatever the password expands
	# from. `scripts/redact.sh`'s own fixtures are built the same way.
	local at='@'
	url="postgresql://appuser:$secret${at}db.example.internal:5432/app"
	# ALL SIX arms that can see a value — the count is written here on purpose, because the
	# first version of this fixture exercised exactly one of three. A review pass deleted the
	# `--db-url=*` arm and the bare-operand redaction in turn, and the whole suite stayed green
	# while each mutant wrote a live password into the state file that `--advisory-report`
	# prints. A property this rail's ledger row and changelog both headline as guaranteed
	# cannot be pinned by a fixture that passes against most of the ways to break it. The list
	# grew with the arms: every kind that calls `data_plane_flags` belongs here, and the
	# `--shadow-database-url` pair are the ones whose value is a connection string by
	# definition — the reported incident IS a production URL passed to that flag.
	for cmd in \
		"supabase db reset --db-url $url" \
		"supabase db reset --db-url=$url" \
		"dropdb $url" \
		"prisma migrate diff --shadow-database-url $url" \
		"prisma migrate diff --shadow-database-url=$url" \
		"drizzle-kit push $url" \
		"npm run db:push $url"; do
		rm -f -- "$state"
		if check_command "$cmd"; then
			printf 'SELF-TEST FAIL: a data-plane command with a connection string should have been advised\n' >&2
			ST_FAILS=$((ST_FAILS + 1))
			continue
		fi
		sig=$(destructive_signature)
		recorded=$(cat -- "$state" 2>/dev/null) || recorded=''
		# Neither the failure message nor anything else here echoes the command: a fixture
		# that prints the value it is checking for is the diagnostic-leakage class.
		case $sig$recorded in
		*"$secret"*)
			printf 'SELF-TEST FAIL: the destructive signature recorded a connection-string value\n' >&2
			ST_FAILS=$((ST_FAILS + 1))
			;;
		esac
		# The presence half. Without it the check above passes over a signature that recorded
		# nothing at all — including one from a rail that stopped firing.
		case $sig in
		*supabase-db-reset* | *dropdb* | *prisma-migrate-shadow-db* | *drizzle-kit-push* | *package-script-*) ;;
		*)
			printf 'SELF-TEST FAIL: the data-plane signature did not name the command kind\n' >&2
			ST_FAILS=$((ST_FAILS + 1))
			;;
		esac
	done
	# ...and the flag NAME survives, which is what makes `--db-url X` distinguishable from a
	# bare reset without making it distinguishable from `--db-url Y`. Both spellings.
	rm -f -- "$state"
	check_command "supabase db reset --db-url $url" || :
	sig=$(destructive_signature)
	case $sig in
	*'--db-url:given'*) ;;
	*)
		printf 'SELF-TEST FAIL: the data-plane signature dropped the target flag name\n' >&2
		ST_FAILS=$((ST_FAILS + 1))
		;;
	esac
	rm -f -- "$state"
	check_command "supabase db reset --db-url=$url" || :
	sig=$(destructive_signature)
	case $sig in
	*'--db-url:given'*) ;;
	*)
		printf 'SELF-TEST FAIL: the data-plane signature dropped the target flag name from the = spelling\n' >&2
		ST_FAILS=$((ST_FAILS + 1))
		;;
	esac
	# The flag NAME for the arm added with the prisma shadow-database shape, both spellings.
	# Without these the whole entry could be dropped from the redaction list with the suite
	# green, because these two arms pass `record_bare=0` and the value would be discarded by a
	# different route — the fixture would be asserting a property no longer held where it says.
	for cmd in \
		"prisma migrate diff --shadow-database-url $url" \
		"prisma migrate diff --shadow-database-url=$url"; do
		rm -f -- "$state"
		check_command "$cmd" || :
		sig=$(destructive_signature)
		case $sig in
		*'--shadow-database-url:given'*) ;;
		*)
			printf 'SELF-TEST FAIL: the data-plane signature dropped the shadow-database flag name\n' >&2
			ST_FAILS=$((ST_FAILS + 1))
			;;
		esac
	done
	# The two arms whose operand is neither a flag nor a URL: a plain database or script
	# argument must not reach the signature either, which is the `record_bare=0` half of
	# the tier's "no operand VALUE is ever recorded" rule and the half a connection-string fixture
	# cannot see, because the redaction catches a URL whichever way that switch is set.
	for cmd in \
		"npm run db:push scratchdbname" \
		"drizzle-kit push scratchdbname"; do
		rm -f -- "$state"
		check_command "$cmd" || :
		sig=$(destructive_signature)
		recorded=$(cat -- "$state" 2>/dev/null) || recorded=''
		case $sig$recorded in
		*scratchdbname*)
			printf 'SELF-TEST FAIL: a bare operand reached the signature of a script-name or drizzle command\n' >&2
			ST_FAILS=$((ST_FAILS + 1))
			;;
		esac
	done
	# The bare connection string keeps its redaction marker rather than vanishing: a target
	# that disappears entirely collides with a bare `dropdb` on one signature.
	rm -f -- "$state"
	check_command "dropdb $url" || :
	sig=$(destructive_signature)
	case $sig in
	*'target:redacted'*) ;;
	*)
		printf 'SELF-TEST FAIL: a redacted bare target left no marker in the signature\n' >&2
		ST_FAILS=$((ST_FAILS + 1))
		;;
	esac
	rm -f -- "$state"
	if [ -n "$old_set" ]; then DESTRUCTIVE_ADVISORY_STATE=$old_state; else unset DESTRUCTIVE_ADVISORY_STATE; fi
}

# The subagent rail's fixtures. It takes no command, so it needs its own helpers rather than
# the command-shaped ones above: what is asserted is that the FIRST spawn is advised and the
# second is not, which is the whole contract.
st_subagent_advisory_once() {
	local state old_set old_state
	old_set=${SUBAGENT_ADVISORY_STATE+x}
	old_state=${SUBAGENT_ADVISORY_STATE:-}
	state=$(mktemp "${TMPDIR:-/tmp}/amh-subagent-advisory-test.XXXXXX") || exit 1
	rm -f -- "$state"
	SUBAGENT_ADVISORY_STATE=$state
	# Three directions, and the third is the one AMH ledger row DC004 forced. The rerun of a spawn must
	# proceed, or the rail blocks the reviewer the rule-review protocol mandates. But the NEXT
	# spawn must be advised again, or the rail is spent at exactly the moment the guarded
	# failure — a burst — happens. And the spawn that proceeded must leave a line, or the
	# sidestep is invisible.
	local old_hook=$HOOK_INVOCATION
	HOOK_INVOCATION=1
	if ! needs_one_time_advisory subagent ''; then
		printf 'SELF-TEST FAIL: the first subagent spawn should have been advised\n' >&2
		ST_FAILS=$((ST_FAILS + 1))
	elif needs_one_time_advisory subagent ''; then
		printf 'SELF-TEST FAIL: rerunning the SAME spawn should proceed — otherwise the rail blocks the mandated blocking reviewer\n' >&2
		ST_FAILS=$((ST_FAILS + 1))
	elif ! needs_one_time_advisory subagent ''; then
		printf 'SELF-TEST FAIL: a LATER spawn must be advised too; a per-session one-shot is spent exactly when a burst happens\n' >&2
		ST_FAILS=$((ST_FAILS + 1))
	elif [ ! -s "$state.resumed" ]; then
		printf 'SELF-TEST FAIL: a spawn that proceeded left no trace; the sidestep must be visible\n' >&2
		ST_FAILS=$((ST_FAILS + 1))
	fi
	HOOK_INVOCATION=$old_hook
	rm -f -- "$state.resumed"
	rm -f -- "$state"
	if [ -n "$old_set" ]; then SUBAGENT_ADVISORY_STATE=$old_state; else unset SUBAGENT_ADVISORY_STATE; fi
}

st_subagent_reason_names() { # st_subagent_reason_names <substring>
	local want=$1 state old_set old_state
	old_set=${SUBAGENT_ADVISORY_STATE+x}
	old_state=${SUBAGENT_ADVISORY_STATE:-}
	state=$(mktemp "${TMPDIR:-/tmp}/amh-subagent-reason-test.XXXXXX") || exit 1
	rm -f -- "$state"
	SUBAGENT_ADVISORY_STATE=$state
	if needs_one_time_advisory subagent ''; then
		case $ADVISORY_REASON in
		*"$want"*) ;;
		*)
			printf 'SELF-TEST FAIL: subagent advisory did not mention %s\n   reason given: %s\n' "$want" "$ADVISORY_REASON" >&2
			ST_FAILS=$((ST_FAILS + 1))
			;;
		esac
	else
		printf 'SELF-TEST FAIL: should have been advised: subagent spawn\n' >&2
		ST_FAILS=$((ST_FAILS + 1))
	fi
	rm -f -- "$state"
	if [ -n "$old_set" ]; then SUBAGENT_ADVISORY_STATE=$old_state; else unset SUBAGENT_ADVISORY_STATE; fi
}

st_subagent_reason_lacks() { # st_subagent_reason_lacks <substring>
	local unwanted=$1 state old_set old_state
	old_set=${SUBAGENT_ADVISORY_STATE+x}
	old_state=${SUBAGENT_ADVISORY_STATE:-}
	state=$(mktemp "${TMPDIR:-/tmp}/amh-subagent-lacks-test.XXXXXX") || exit 1
	rm -f -- "$state"
	SUBAGENT_ADVISORY_STATE=$state
	if needs_one_time_advisory subagent ''; then
		case $ADVISORY_REASON in
		*"$unwanted"*)
			printf 'SELF-TEST FAIL: subagent advisory claimed a check it cannot perform: %s\n   reason given: %s\n' "$unwanted" "$ADVISORY_REASON" >&2
			ST_FAILS=$((ST_FAILS + 1))
			;;
		esac
	else
		printf 'SELF-TEST FAIL: should have been advised: subagent spawn\n' >&2
		ST_FAILS=$((ST_FAILS + 1))
	fi
	rm -f -- "$state"
	if [ -n "$old_set" ]; then SUBAGENT_ADVISORY_STATE=$old_state; else unset SUBAGENT_ADVISORY_STATE; fi
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
	# PRE-SPENT, for the reason spelled out at the key-material block below. It was NOT, and
	# `st_blocked 'cat .env'` was the fixture that showed the cost: it is the first
	# `.env`-bearing case in the matrix, so it blocked on the advisory and passed even with
	# `names_env_file` neutered — a fixture for the oldest secret-file rail in the script that
	# could not fail. Neutering that predicate now fails it along with 27 others.
	self_dotenv_advisory_state=$(mktemp "${TMPDIR:-/tmp}/amh-dotenv-advisory-self-test.XXXXXX") || exit 1
	DOTENV_ADVISORY_STATE=$self_dotenv_advisory_state
	# PRE-SPENT, unlike the two below, and the difference is deliberate. The advisory fires on
	# any command text naming `.pem` or `.key`, so an unspent state here would make the FIRST
	# such fixture — blocked or allowed — return the advisory instead of the verdict under
	# test, and every fixture after it would be testing a rail the advisory had already
	# short-circuited. `st_keymaterial_advisory` brings its own unspent file for the two
	# fixtures that are about the advisory itself.
	local old_keymaterial_advisory_state_set=${KEYMATERIAL_ADVISORY_STATE+x}
	local old_keymaterial_advisory_state=${KEYMATERIAL_ADVISORY_STATE:-}
	local self_keymaterial_advisory_state
	self_keymaterial_advisory_state=$(mktemp "${TMPDIR:-/tmp}/amh-keymaterial-advisory-self-test.XXXXXX") || exit 1
	KEYMATERIAL_ADVISORY_STATE=$self_keymaterial_advisory_state
	local old_destructive_advisory_state_set=${DESTRUCTIVE_ADVISORY_STATE+x}
	local old_destructive_advisory_state=${DESTRUCTIVE_ADVISORY_STATE:-}
	local self_destructive_advisory_state
	self_destructive_advisory_state=$(mktemp "${TMPDIR:-/tmp}/amh-destructive-advisory-self-test.XXXXXX") || exit 1
	rm -f -- "$self_destructive_advisory_state"
	DESTRUCTIVE_ADVISORY_STATE=$self_destructive_advisory_state

	# --- the parser's own failure is a BLOCK, never a pass
	# A rail that hands back an empty word list has not judged the command, and until
	# 8.0.0 that read as "nothing to object to". These pin the discriminator: text
	# present + nothing parsed is a defect; blank text + nothing parsed is an ordinary
	# empty segment (a segment that was all redirection, once stripped); anything parsed
	# at all is normal. Neutering `parse_produced_nothing` to `return 1` fails the two
	# `defect` rows, which is the demonstration that this fixture can fail.
	st_parse_sanity defect 'git push --force origin main' 0
	st_parse_sanity defect '  sudo rm -rf /  ' 0
	st_parse_sanity normal '' 0
	st_parse_sanity normal '   ' 0
	st_parse_sanity normal 'git push --force origin main' 4
	st_parse_integration words
	st_parse_integration segments

	# --- must block: the rails themselves
	st_blocked 'git push --force origin feature'
	# --- forge/API mutation rail: argv structure separates reads from writes. Diagnostics
	# deliberately contain only client, method and operation class, never these payloads.
	st_allowed 'gh api repos/acme/widget/releases'
	st_allowed 'gh api --method GET repos/acme/widget/environments/prod'
	st_allowed 'curl https://api.github.com/repos/acme/widget/git/refs/heads/main'
	st_allowed 'curl --get --data page=2 https://api.github.com/repos/acme/widget/releases'
	st_allowed 'gh api graphql -f query="query { viewer { login } }" -F environment=mutation'
	st_allowed 'curl --data "{\"query\":\"query { viewer { login } }\",\"variables\":{\"environment\":\"mutation\"}}" https://api.github.com/graphql'
	st_allowed 'curl -H https://api.github.com/repos/acme/widget/releases -X DELETE https://example.com'
	st_blocked 'gh api repos/acme/widget/releases -X DELETE'
	st_blocked 'gh api -X PATCH repos/acme/widget -f visibility=private'
	st_blocked 'gh api -X DELETE repos/acme/widget'
	st_blocked 'gh api --hostname github.example repos/acme/widget/releases -X DELETE'
	st_blocked 'gh api --input body.json repos/acme/widget/releases'
	st_blocked 'gh api repos/acme/widget/actions/variables/PROD -X DELETE'
	st_blocked 'gh api --method PATCH repos/acme/widget/settings -f visibility=private'
	st_blocked 'env FOO=1 gh api -F value="quoted payload" repos/acme/widget/actions/secrets/DEPLOY_KEY'
	st_blocked 'gh api graphql -f variables="{\"name\":\"prod\"}" -f query="mutation { deleteEnvironment(input: {}) { clientMutationId } }"'
	st_blocked 'command gh api graphql --method POST --raw-field query="mutation Drop { deleteRef(input: {}) { clientMutationId } }" -F variables="{\"ref\":\"main\"}"'
	st_blocked 'curl https://api.github.com/repos/acme/widget/releases/1 --request DELETE'
	st_blocked 'curl -X DELETE https://api.github.com/repos/acme/widget'
	st_blocked 'curl -X DELETE https://API.GITHUB.COM/repos/acme/widget/releases/1'
	st_blocked 'curl --data "{\"private\":true}" https://api.github.com/repos/acme/widget/settings'
	st_blocked 'sudo curl https://gitlab.com/api/v4/projects/1/environments/2 -X DELETE'
	st_blocked 'curl -F "file=@artifact.zip" https://api.github.com/repos/acme/widget/releases/1/assets'
	st_blocked 'curl --data-raw "{\"query\":\"mutation { deleteRepository(input: {}) { clientMutationId } }\",\"variables\":{\"id\":\"opaque\"}}" https://api.github.com/graphql'
	st_blocked 'git push -f origin feature'
	st_blocked 'git push --force-with-lease'
	st_blocked "git push origin $DEFAULT_BRANCH"
	st_blocked "git push origin HEAD:$DEFAULT_BRANCH"
	st_blocked "git push origin refs/heads/$DEFAULT_BRANCH"
	st_blocked 'env'
	st_blocked 'env -0'
	# `env` handed no utility prints the environment WHATEVER its options did to it, so
	# each of these is a dump: `-i` empties it first, `-u` drops one name, an assignment
	# adds one. The last was allowed until 10.1.2 — the arm read a leading `-` as the
	# dump signal, so a leading assignment was read as a prefix with nothing prefixed.
	st_blocked 'env -i'
	st_blocked 'env -u AMH_REMOTE'
	st_blocked 'env FOO=1'
	st_blocked 'env --unset AMH_REMOTE'
	# ...and the argument of an option that takes one is never the utility. Read `-u`
	# as a bare flag and `AMH_REMOTE` becomes the command word, which is a name this
	# guard has nothing to say about — the `.env` read behind it would walk free.
	st_blocked 'env -u AMH_REMOTE cat .env'
	st_blocked 'env -C /tmp cat .env'
	st_blocked 'env -iu AMH_REMOTE cat .env'
	st_blocked 'env -uAMH_REMOTE cat .env'
	st_blocked 'env --unset=AMH_REMOTE cat .env'
	st_blocked 'env -u AMH_REMOTE sudo cat .env'
	# An ABBREVIATED long option is the same option — `getopt_long` says so, and these
	# were the hole in the first draft of the walk: read as plain flags they handed
	# `AMH_REMOTE` to the guard as the command word, so the read and the force push
	# behind it went unjudged while the unabbreviated spellings blocked.
	st_blocked 'env --u AMH_REMOTE'
	st_blocked 'env --u AMH_REMOTE cat .env'
	st_blocked 'env --uns AMH_REMOTE cat .env'
	st_blocked 'env --u AMH_REMOTE printenv'
	st_blocked 'env --uns AMH_REMOTE git push --force origin x'
	st_blocked 'env --chd /tmp cat .env'
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
	st_blocked 'git 2>&1 push --mirror origin'
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
	# Private key material by its conventional filename, through each of the tiers that
	# reach a file: the reader list, a redirection, and a copy.
	st_blocked 'cat ~/.ssh/id_rsa'
	st_blocked 'cat id_ed25519'
	st_blocked 'head -1 /home/dev/.ssh/id_ecdsa'
	st_blocked 'base64 id_rsa'
	st_blocked 'md5sum ~/.ssh/id_ed25519_sk'
	st_blocked 'sudo cat id_rsa'
	st_blocked 'tr -d "\n" < id_rsa'
	st_blocked 'python3 app.py < id_rsa'
	st_blocked 'cp ~/.ssh/id_rsa /tmp/k'
	st_blocked 'cp -t /tmp id_rsa'
	st_blocked 'dd if=id_rsa of=/tmp/k'
	st_dotenv_advisory_once 'python3 -c "open('"'"'.env'"'"')"'
	# `.pem`/`.key` get the advisory tier, not the block tier: the extension is a container,
	# not a secret marker. Both directions, each on its own unspent state file.
	st_keymaterial_advisory fires 'openssl rsa -in server.key -noout -text'
	st_keymaterial_advisory fires 'cat client.pem'
	st_keymaterial_advisory fires 'python3 -c "open(\"server.key\").read()"'
	# ...and the residue, stated rather than implied: the exclusion is the PLURAL, so an
	# ordinary singular field access still spends the bump. One rerun, by design.
	st_keymaterial_advisory fires 'jq -r ".key" data.json'
	# ...and the word that made a bare-substring match unaffordable.
	st_keymaterial_advisory silent 'node -e "Object.keys(x)"'
	st_keymaterial_advisory silent 'jq ".keys[]" data.json'
	st_keymaterial_advisory silent 'python3 -c "print(cfg.keys())"'
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
	rm -f -- "$self_destructive_advisory_state"
	# `--` ends the options: the path after it is an operand even when it looks like a flag.
	st_destructive_advisory_once 'rm -rf -- -weird-name'
	rm -f -- "$self_destructive_advisory_state"

	# The tree-mutating git verbs, armed only on an unexpanded operand. The first is the
	# reported incident's command verbatim (AMH ledger row DC011).
	st_destructive_advisory_once 'git worktree add -q --detach "$TEMP_WT" HEAD'
	rm -f -- "$self_destructive_advisory_state"
	st_destructive_advisory_once 'git worktree remove --force "$TEMP_WT"'
	rm -f -- "$self_destructive_advisory_state"
	st_destructive_advisory_once 'git reset --hard "$BASE"'
	rm -f -- "$self_destructive_advisory_state"
	st_destructive_advisory_once 'git checkout -f "$REF"'
	rm -f -- "$self_destructive_advisory_state"
	st_destructive_advisory_once 'git switch --discard-changes "$BRANCH"'
	rm -f -- "$self_destructive_advisory_state"
	st_destructive_advisory_once 'git restore "$D"'
	rm -f -- "$self_destructive_advisory_state"
	st_destructive_advisory_once 'git restore --staged --worktree "$D"'
	rm -f -- "$self_destructive_advisory_state"
	# A global option before the subcommand must not hide it.
	st_destructive_advisory_once 'git -C "$ROOT_DIR" reset --hard "$BASE"'
	rm -f -- "$self_destructive_advisory_state"
	# The rootish paragraph must reach the new verbs too — this is the incident's own shape,
	# and the advisory is worth little if it names the failure mode only for `rm`.
	st_destructive_reason_names 'is empty the command addresses an absolute path' \
		'git worktree add --detach "$TEMP_WT/wt" HEAD'
	st_destructive_reason_names 'print the expansion before you rerun' 'git reset --hard "$BASE"'
	# Distinct verbs are distinct targets: clearing one must not clear another.
	st_destructive_rearms_per_target 'git reset --hard "$A"' 'git checkout -f "$A"'
	st_destructive_rearms_per_target 'git worktree add "$A" HEAD' 'git worktree remove "$A"'

	# The escape hatch, and the reason the gate is not `$` alone. Dropping the operand to
	# silence a complaint about the operand must NOT buy silence for a STRICTLY more
	# destructive command: bare `git reset --hard` discards every uncommitted change.
	st_destructive_advisory_once 'git reset --hard'
	rm -f -- "$self_destructive_advisory_state"
	st_destructive_advisory_once 'git restore .'
	rm -f -- "$self_destructive_advisory_state"
	st_destructive_advisory_once 'git checkout -f .'
	rm -f -- "$self_destructive_advisory_state"
	st_destructive_advisory_once 'git restore :/'
	rm -f -- "$self_destructive_advisory_state"
	# A path arriving through a global option is still the target the command is aimed at.
	# git treats `-C ''` as a no-op, so an empty variable here silently redirects the whole
	# command at the CURRENT repository.
	st_destructive_advisory_once 'git -C "$ROOT_DIR" reset --hard HEAD'
	rm -f -- "$self_destructive_advisory_state"
	st_destructive_advisory_once 'git --work-tree="$W" checkout -f main'
	rm -f -- "$self_destructive_advisory_state"
	# The two verbs the dispatch table left out once it stopped being `clean`-only.
	st_destructive_advisory_once 'git rm -r -f "$X"'
	rm -f -- "$self_destructive_advisory_state"
	st_destructive_advisory_once 'git worktree move "$A" "$B"'
	rm -f -- "$self_destructive_advisory_state"

	# The advisory must not assert a mechanism the command does not have. A REVISION
	# operand cannot become an absolute path: `git reset --hard /main` is an unknown-revision
	# error, not a deletion of `/main`. The rootish paragraph is therefore forbidden here and
	# required for a real path operand — both directions, or the exclusion is untested.
	st_destructive_reason_lacks 'is empty the command addresses an absolute path' \
		'git reset --hard "$UPSTREAM/main"'
	st_destructive_reason_lacks 'is empty the command addresses an absolute path' \
		'git checkout -f "$REMOTE/branch"'
	st_destructive_reason_names 'print the expansion before you rerun' 'git reset --hard "$UPSTREAM/main"'
	st_destructive_reason_names 'is empty the command addresses an absolute path' 'rm -rf "$S/base"'
	# A verb that deletes nothing must not be described as deleting. `git worktree add` is
	# the incident's own command, and it is the one most able to teach an agent that this
	# rail lies.
	st_destructive_reason_lacks 'may delete guard fixtures' 'git worktree add "$TEMP_WT" HEAD'
	st_destructive_reason_lacks 'renaming or relocating the target' 'git reset --hard "$BASE"'
	st_destructive_reason_names 'overwrites or discards working-tree state' 'git reset --hard "$BASE"'
	st_destructive_reason_names 'may delete guard fixtures' 'rm -rf "$S/base"'

	# The downstream incident, in three fixtures. A spent advisory must NOT cover a new
	# target; the same target twice must still proceed; and the advisory must name the
	# empty-variable failure mode rather than a way around itself.
	st_destructive_rearms_per_target 'rm -rf tmp/build' 'rm -rf "$S/base"'
	st_destructive_rearms_per_target 'git clean -fdx' 'rm -rf src'
	st_destructive_reason_names 'is empty the command addresses an absolute path' 'rm -rf "$S/base"'
	st_destructive_reason_names 'print the expansion before you rerun' 'rm -rf tmp/$BUILD'
	st_destructive_reason_names 'renaming or relocating the target' 'rm -rf tmp/build'

	# The parser rewrite: `--` stopped the scan before, so the operands after it were never
	# recorded and every post-`--` deletion shared one signature. Advised-then-allowed alone
	# cannot see that; only a rearm across two different post-`--` paths can.
	st_destructive_rearms_per_target 'rm -rf -- a' 'rm -rf -- b'
	st_destructive_rearms_per_target 'git clean -fd -- a' 'git clean -fd -- b'
	# Scanning every segment rather than stopping at the first destructive one. With the
	# short-circuit restored, the second command inherits the first's cleared verdict and
	# `rm -rf /etc` runs unadvised.
	st_destructive_rearms_per_target 'rm -rf tmp/x' 'rm -rf tmp/x && rm -rf /etc'
	# The kind prefix. Without it an operand-less `rm` and a pathspec-less `git clean`
	# collapse into one signature, and a spellable sentinel could be typed by hand.
	st_destructive_rearms_per_target 'rm -rf' 'git clean -fdx'
	st_destructive_rearms_per_target 'git clean -fdx' 'rm -rf "<work tree>"'
	# Quoting the operands before joining them. `a b` as one target and as two are
	# different deletions; a flattened signature let either clear the other.
	st_destructive_rearms_per_target 'rm -rf a b' 'rm -rf "a b"'
	st_destructive_same_target_set 'rm -rf a b' 'rm -rf b a'
	# The guarded rewrite the advisory now ASKS for must count as the rerun, not as a new
	# deletion — otherwise the rail charges a second turn for doing what it just told the
	# session to do, and the cheapest way out stays the sidestep it is trying to stop.
	st_destructive_same_target_set 'rm -rf $d/build' 'rm -rf -- "${d:?}/build"'
	st_destructive_same_target_set 'rm -rf "${d}/build"' 'rm -rf $d/build'
	st_destructive_same_target_set 'rm -rf "${d:?set d}/build"' 'rm -rf $d/build'
	# Unquoted brace expansions stay intact too: two different variable targets must not
	# share the bare `$` signature and silence one another, and the rootish paragraph must
	# survive on the spelling most exposed to an empty-variable expansion.
	st_destructive_rearms_per_target 'rm -rf ${scratch}/x' 'rm -rf ${root}/y'
	st_destructive_reason_names 'BEGINS with a variable' 'rm -rf ${d}/build'
	# Parameter expansion nesting does not turn its braces into command separators.
	st_destructive_rearms_per_target 'rm -rf ${scratch:-${fallback}}/x' 'rm -rf ${root}/y'
	# ...and the SUBSTITUTING forms must not fold, because they can address a path the bare
	# variable never would. `${d:-/tmp}` with an empty `d` deletes `/tmp`, not `$d`.
	st_destructive_rearms_per_target 'rm -rf "${d:-/tmp}/x"' 'rm -rf $d/x'
	st_destructive_rearms_per_target 'rm -rf "${d:+alt}/x"' 'rm -rf $d/x'
	# The report distinguishes the two outcomes the rail can actually observe.
	st_advisory_report 'rm -rf /tmp/abandoned' 'rm -rf /tmp/resumed'

	# --- the data-plane tier
	# The earned shape first, then its siblings. Each must be advised ONCE and proceed on the
	# rerun, exactly like the filesystem tier.
	st_destructive_advisory_once 'supabase db reset'
	st_destructive_advisory_once 'prisma migrate reset'
	st_destructive_advisory_once 'prisma db push --accept-data-loss'
	st_destructive_advisory_once 'prisma db push --force-reset'
	st_destructive_advisory_once 'rails db:drop'
	st_destructive_advisory_once 'rake db:reset'
	st_destructive_advisory_once 'bin/rails db:schema:load'
	st_destructive_advisory_once 'dropdb app_production'
	st_destructive_advisory_once 'psql -c "DROP DATABASE app"'
	st_destructive_advisory_once 'psql --command="TRUNCATE users"'
	st_destructive_advisory_once 'psql -h db -c "drop schema public cascade"'
	# Every `-c`, not the first: a harmless leading statement must not shelter the one behind
	# it. Both were allowed until the scan stopped breaking at the first match.
	st_destructive_advisory_once 'psql -c "SELECT 1" -c "DROP DATABASE app"'
	st_destructive_advisory_once 'psql -c "SELECT 1 FROM t" -c "TRUNCATE users"'
	# Bundled short options are how psql is actually written, and a pattern anchored on `c` in
	# position two saw none of these.
	st_destructive_advisory_once 'psql -Atc "TRUNCATE users"'
	st_destructive_advisory_once 'psql -qc "DROP DATABASE app"'
	st_destructive_advisory_once 'psql -d app -Xc "DROP SCHEMA public CASCADE"'
	st_destructive_advisory_once 'psql -c "BEGIN; TRUNCATE users"'
	# Package runners are the real invocation. Without the runner strip every one of these
	# reaches the filesystem arms as a command called `npx` and is silently allowed.
	st_destructive_advisory_once 'npx prisma migrate reset'
	st_destructive_advisory_once 'npx --yes prisma migrate reset'
	st_destructive_advisory_once 'pnpm exec prisma migrate reset'
	st_destructive_advisory_once 'pnpm dlx prisma migrate reset'
	st_destructive_advisory_once 'pnpm prisma migrate reset'
	st_destructive_advisory_once 'yarn prisma migrate reset'
	st_destructive_advisory_once 'bunx supabase db reset'
	st_destructive_advisory_once 'bun x supabase db reset'
	# ...and the wrappers the shared prefix loop already strips still work through it.
	st_destructive_advisory_once 'cd apps/api && supabase db reset'
	# The FALSE-POSITIVE direction, which is what decides whether this tier survives contact
	# with a real session. These are the everyday commands in the same tools, and a rail that
	# advises them is one an agent learns to skim — taking `rm -rf` down with it.
	st_allowed 'supabase start'
	st_allowed 'supabase db diff'
	st_allowed 'supabase db push'
	st_allowed 'supabase migration new add_users'
	st_allowed 'prisma migrate dev'
	st_allowed 'prisma migrate deploy'
	st_allowed 'prisma generate'
	st_allowed 'npx prisma db push'
	st_allowed 'rails db:migrate'
	st_allowed 'rake db:seed'
	st_allowed 'rails server'
	st_allowed 'psql -c "SELECT count(*) FROM users"'
	st_allowed 'psql -f migrate.sql'
	st_allowed 'psql'
	# The verb NAMED but not performed. Every one of these is a read, and every one of them was
	# advised while the scan matched the word anywhere in the statement — a fail-CLOSED false
	# positive on `SELECT`, in the arm whose own comment refuses `DELETE FROM` for crying wolf.
	st_allowed 'psql -c "SELECT * FROM truncate_log"'
	st_allowed 'psql -c "EXPLAIN SELECT * FROM truncate_metrics"'
	st_allowed "psql -c \"SELECT id FROM audit WHERE action = 'truncate'\""
	st_allowed 'psql -c "\d truncate_log"'
	st_allowed 'pnpm run build'
	st_allowed 'createdb app_test'
	# Quoted prose naming the verb is DATA — the D007 class, one tier down. A commit message
	# about this rail must not trip it.
	st_allowed 'git commit -m "guard supabase db reset and dropdb"'
	# Each tool is its own decision, and so is each explicitly named target. Sharing one
	# signature across the tier would mean the first reset of a session buys silence for every
	# later one, in a different ecosystem, against a different database.
	st_destructive_rearms_per_target 'supabase db reset' 'prisma migrate reset'
	st_destructive_rearms_per_target 'supabase db reset --local' 'supabase db reset --linked'
	st_destructive_rearms_per_target 'dropdb scratch' 'dropdb app_production'
	# The permutation that defeated the rearm: a value-taking flag whose value was recorded as
	# if it were the database name, then sorted, made these two the SAME signature — so
	# clearing the one that drops `scratch` cleared the one that drops `prod`.
	st_destructive_rearms_per_target 'dropdb -U prod scratch' 'dropdb -U scratch prod'
	# The other half of that fix, pinned as the ACCEPTED MISS it is rather than left to be
	# discovered: consuming a flag's value means the value no longer discriminates either, so
	# the same database name on two different hosts is one signature and one advisory. Naming
	# the host would discriminate, and this rail does not record operand values. The advisory's
	# own text is what covers this — it asks which database the command resolves to, and the
	# host is part of that answer.
	st_destructive_same_target_set 'dropdb -h prod.example.internal app' \
		'dropdb -h staging.example.internal app'
	# The advisory says the thing this tier exists to say, and does NOT say the things that are
	# only true of a path. "may delete guard fixtures" and the absolute-path mechanism are both
	# false of a database reset, and an agent that catches a rail being confidently wrong has a
	# correct reason to dismiss the next one too.
	st_destructive_reason_names 'WHICH database this hits is not in the command' 'supabase db reset'
	st_destructive_reason_names 'destroys DATABASE state' 'supabase db reset'
	st_destructive_reason_names 'a flag is a claim in the command text' 'supabase db reset --local'
	st_destructive_reason_lacks 'may delete guard fixtures' 'supabase db reset'
	st_destructive_reason_lacks 'overwrites or discards working-tree state' 'supabase db reset'
	# The operand carries a `/`, which is what makes this fixture able to fail: `dropdb "$DB"`
	# never sets ROOTISH with or without the exemption, so it asserted nothing.
	st_destructive_reason_lacks 'is empty the command addresses an absolute path' 'dropdb "$D/x"'
	st_destructive_reason_lacks 'renaming or relocating the target' 'supabase db reset'
	# A command that is BOTH gets both paragraphs. Folding the data-plane text into the path
	# chain drops whichever one loses, and the deletion half of this command is no less real
	# for the reset half being scarier.
	st_destructive_reason_names 'is empty the command addresses an absolute path' \
		'rm -rf "$S/base" && supabase db reset'
	st_destructive_reason_names 'WHICH database this hits is not in the command' \
		'rm -rf "$S/base" && supabase db reset'
	# The runner strip widens the FILESYSTEM arms too, which is a behaviour change and gets
	# fixtures rather than a comment asserting it did not happen.
	st_destructive_advisory_once 'npx rm -rf /tmp/x'
	st_destructive_advisory_once 'pnpm exec git clean -fdx'
	# A runner option that takes a value names a package, not a command.
	st_destructive_advisory_once 'npx -p prisma@5 prisma migrate reset'
	# Global options come before the subcommand, the way the git arm has always read them.
	st_destructive_advisory_once 'supabase --workdir . db reset'
	st_destructive_advisory_once 'prisma db push --accept-data-loss=true'
	# Which CLOSING paragraph won. The two hazard paragraphs were asserted and the selector
	# below them was not, so reducing its three conditions to one left the suite green.
	st_destructive_reason_names 'that key is weak here by construction' 'supabase db reset'
	st_destructive_reason_lacks 'that key is weak here by construction' \
		'rm -rf "$S/base" && supabase db reset'
	# The rail must not become the disclosure. A connection string is the one operand in this
	# tier that routinely carries a live password, and the signature it would land in is
	# written to a state file and printed by `--advisory-report`.
	st_dataplane_signature_omits_value

	# --- the shapes a reported incident earned, and the everyday commands beside them.
	# `npm run db:push` is the command from the most widely reported incident of this kind, and
	# every arm here saw a command called `npm` until `npm` joined the runner strip. Each runner
	# spelling is its own fixture because each resolves the script name by a different path.
	st_destructive_advisory_once 'npm run db:push'
	st_destructive_advisory_once 'npm run-script db:push'
	st_destructive_advisory_once 'pnpm run db:reset'
	st_destructive_advisory_once 'yarn db:drop'
	st_destructive_advisory_once 'bun run migrate:reset'
	st_destructive_advisory_once 'npm run db:wipe'
	st_destructive_advisory_once 'npm run migrate:fresh'
	# The tool underneath that script. `push` writes the schema diff straight to the database,
	# and the confirmation prisma's equivalent relies on is reported not firing here.
	st_destructive_advisory_once 'drizzle-kit push'
	st_destructive_advisory_once 'npx drizzle-kit push'
	st_destructive_advisory_once 'drizzle-kit push:pg'
	# `npm` in the runner strip widens the filesystem and data-plane arms alike, exactly as
	# `npx` did — a behaviour change with fixtures rather than a comment claiming it did not
	# happen.
	st_destructive_advisory_once 'npm exec prisma migrate reset'
	st_destructive_advisory_once 'npm exec rm -rf /tmp/x'
	# The FALSE-POSITIVE direction. These are the everyday scripts in the same repositories and
	# the everyday verbs in the same tool; a rail that advises them teaches an agent to skim.
	st_allowed 'npm run build'
	st_allowed 'npm run test'
	st_allowed 'npm run dev'
	st_allowed 'npm install'
	st_allowed 'npm ci'
	st_allowed 'npm run db:migrate'
	st_allowed 'npm run db:seed'
	st_allowed 'npm run db:generate'
	st_allowed 'yarn lint'
	st_allowed 'drizzle-kit generate'
	st_allowed 'drizzle-kit migrate'
	st_allowed 'drizzle-kit studio'
	st_allowed 'drizzle-kit check'
	# Deliberately absent from the arm: no public report shows it destroying stored data, and
	# this tier's list is earned by reports rather than by resemblance.
	st_allowed 'drizzle-kit drop'
	# The name is matched WHOLE. A script whose name merely CONTAINS one of the list is a
	# different script — the D007 class in the arm most exposed to it, since a script name is
	# free text its author chose.
	st_allowed 'npm run build:db:pushdocs'
	st_allowed 'npm run db:push:check'
	st_allowed 'npm run predb:reset'
	# Each script name is its own decision, and so is the tool underneath it: clearing the one
	# that pushes a schema must not clear the one that drops the database.
	st_destructive_rearms_per_target 'npm run db:push' 'npm run db:reset'
	st_destructive_rearms_per_target 'drizzle-kit push' 'npm run db:push'
	# ...and the same script through two runners is the same decision, because the signature is
	# the resolved name rather than the text that reached it.
	st_destructive_same_target_set 'npm run db:push' 'pnpm db:push'
	# The paragraph this arm owes, in BOTH directions and against BOTH of the tier's other
	# kinds — three assertions for three ways it could be wrong, per the N-arms rule that came
	# out of this tier's own review pass. It must fire for a script name, and must NOT fire for
	# a command the guard genuinely understands, or the disclosure stops meaning anything.
	st_destructive_reason_names 'matched a package SCRIPT NAME' 'npm run db:push'
	st_destructive_reason_names 'the check that settles it is reading the script' 'npm run db:push'
	st_destructive_reason_lacks 'matched a package SCRIPT NAME' 'supabase db reset'
	st_destructive_reason_lacks 'matched a package SCRIPT NAME' 'drizzle-kit push'
	# The tier's shared paragraphs still reach the new kinds — a script name resolves a database
	# from the same ambient config, and neither new kind touches a path.
	st_destructive_reason_names 'WHICH database this hits is not in the command' 'npm run db:push'
	st_destructive_reason_names 'destroys DATABASE state' 'drizzle-kit push'
	st_destructive_reason_lacks 'may delete guard fixtures' 'npm run db:push'
	# The disclosure is gated on HOW the command word was resolved, not on which kind it
	# reached, and both halves of that need a fixture. `npm run dropdb` reads a script name and
	# lands on the `dropdb` arm: without the resolution test it was told outright that it
	# "drops a database outright" on the strength of a name nobody read.
	st_destructive_reason_names 'matched a package SCRIPT NAME' 'npm run dropdb'
	st_destructive_reason_names 'matched a package SCRIPT NAME' 'yarn run db:drop'
	st_destructive_reason_lacks 'matched a package SCRIPT NAME' 'npm exec dropdb app'
	st_destructive_reason_lacks 'matched a package SCRIPT NAME' 'npm exec rm -rf /tmp/x'
	# ...and the lead sentence follows the same fact, because asserting "this command destroys
	# DATABASE state" and then admitting the script was never read is a contradiction inside one
	# advisory. Both directions: hedged for a name, flat for a command the guard understands.
	st_destructive_reason_names 'MAY destroy DATABASE state' 'npm run db:push'
	st_destructive_reason_lacks 'destroys DATABASE state' 'npm run db:push'
	st_destructive_reason_lacks 'MAY destroy DATABASE state' 'supabase db reset'
	# The prisma flag a public report earned: an agent pointed `--shadow-database-url` at
	# production and prisma reset what the flag named. Every other `migrate` subcommand stays
	# silent without it, which is the whole false-positive budget for this arm.
	st_destructive_advisory_once 'prisma migrate diff --shadow-database-url postgres://scratch'
	st_destructive_advisory_once 'npx prisma migrate dev --shadow-database-url=postgres://scratch'
	st_allowed 'prisma migrate diff --from-empty --to-schema-datamodel schema.prisma'
	st_allowed 'prisma migrate status'
	# The dialect spellings are ENUMERATED, so the kind can never carry command text. A glob
	# here let a crafted operand spell another command's signature and clear its advisory.
	st_allowed 'drizzle-kit push:x'
	st_allowed 'drizzle-kit push:'
	# The workspace flags, whose values were being read as the subcommand — six silent
	# spellings of the incident's own command in a monorepo, before and after `run`.
	st_destructive_advisory_once 'npm -w api run db:push'
	st_destructive_advisory_once 'npm --prefix ./app run db:push'
	st_destructive_advisory_once 'npm run -w api db:push'
	st_destructive_advisory_once 'pnpm -F api db:push'

	# The subagent-spawn speed bump blocks once, allows the deliberate rerun, then rearms for
	# the next spawn. That direction is load-bearing: a burst must not inherit silence from
	# the first spawn, while the immediate rerun still permits the ONE blocking reviewer.
	st_subagent_advisory_once
	# The advisory must not claim a check it cannot perform. A pre-spawn hook sees one spawn
	# and knows nothing about what else is running; prose asserting otherwise is the class
	# this harness keeps a row about, and the negative fixture is what keeps a later edit from
	# quietly upgrading the claim.
	st_subagent_reason_names 'it cannot see whether anything was already running'
	st_subagent_reason_names 'ONE fresh-context reviewer at a time'
	# The rearm promise is part of the text now, so it is pinned: an edit that reverts the
	# rail to a per-session one-shot has to make this sentence false first.
	st_subagent_reason_names 'EVERY spawn is advised, not just the first'
	st_subagent_reason_lacks 'concurrent spawn detected'
	# The rail must not volunteer its own bypass. The earlier wording told the agent outright
	# that the second and third spawn were unguarded, which is an invitation rather than the
	# honesty AMH ledger row D010 asks for — and after the per-spawn rearm it is simply untrue.
	st_subagent_reason_lacks 'will not stop the second spawn'

	# A substitution is not a variable, and an always-set variable has no empty case. Both
	# still get the advisory — just not the paragraph that names a check they cannot make.
	st_destructive_reason_lacks 'BEGINS with a variable' 'rm -rf "$(pwd)/x"'
	st_destructive_reason_lacks 'BEGINS with a variable' 'rm -rf "$HOME/.cache/x"'
	st_destructive_reason_lacks 'BEGINS with a variable' 'rm -rf "${S:-/tmp}/x"'
	st_destructive_reason_names 'print the expansion before you rerun' 'rm -rf "$HOME/.cache/x"'
	# The guarded spelling is the intervention, so the sentence offering it is pinned in
	# both paragraphs the way the sidestep clause already is.
	st_destructive_reason_names '${S:?}' 'rm -rf $S/base'
	st_destructive_reason_names '${VAR:?}' 'rm -rf "$HOME/.cache/x"'

	# --- must allow: the known false-positive classes.
	# Quoted text naming a forbidden command is DATA, not a command.
	# THE BACKSLASH RULE, in the direction that matters first. An escaped quote inside a
	# double-quoted word is not the closing quote, and until every walker agreed about that,
	# ONE `\"` anywhere on the line voided the rails for everything after it: the `&&` that
	# followed read as unquoted, a segment bash never runs was handed to the scanners, and the
	# force-push rail — the oldest one here — let a real push through. Six walkers carry the
	# rule and each of the six is a way to break it, so the demonstration owed is six
	# mutations, not one (AMH ledger row DC025 on why that count belongs in this comment).
	st_blocked 'echo "say \"hi\"" && git push --force origin main'
	st_blocked 'echo "say \"hi\"" ; git push --force origin main'
	st_blocked 'echo "a \" b" && git push --force origin main'
	st_blocked 'x="\"" && git push --force origin main'
	st_blocked 'echo "1 \"2\" 3 \"4\"" && git push --force origin main'
	st_blocked 'echo "say \"hi\"" && git push origin main'
	st_blocked 'echo "say \"hi\"" && cat .env'
	st_blocked 'echo "a \" b" && printenv'
	st_blocked 'echo "trailing backslash \\" && git push --force origin main'
	st_destructive_advisory_once 'echo "say \"hi\"" && rm -rf /tmp/x'
	rm -f -- "$self_destructive_advisory_state"
	st_destructive_advisory_once 'echo "a\\" && rm -rf /tmp/x'
	rm -f -- "$self_destructive_advisory_state"
	# SINGLE quotes have no escapes at all, so `'"'"'a \'"'"'` is a complete word and the command
	# after it is real. A walker that treated the backslash as an escape there would swallow
	# the closing quote and the push with it — the same hole, one quote character over.
	st_blocked "echo 'a \\' && git push --force origin main"
	# ONE FIXTURE PER WALKER, because six sites are six ways to break this and the first draft
	# of this block demonstrated exactly one of them — every mutation but `split_segments`
	# survived a green suite. Each command below dies with the walker it names; two of them die
	# with a second walker as well, which is why the claim here is "every site has a fixture
	# that fails without it" and NOT the tidier "each covers exactly one" — that sentence was
	# in an earlier draft and was false for the two `>` cases (AMH ledger row DC025).
	#
	# `heredoc_delim` is the sixth and it took three tries to demonstrate, which is the part
	# worth carrying away. Draft one claimed six kills from a mutation run whose suite was
	# ALREADY red, so every mutant died for the wrong reason. Draft two put the `<<` AFTER the
	# quoted word (`echo "a \" <<EOF`), left the string unterminated, and concluded from that
	# one construction that the walker was unreachable on input bash accepts — a claim the
	# review pass falsified by moving the operator INSIDE the word, where the string closes and
	# both lines really run. The lesson is not "write fixtures": it is that "unreachable" is a
	# claim about every input, and one failed construction is not a proof of it.
	#   heredoc_delim: a `<<` inside a quoted WORD — the string still closes, so bash runs both
	#   lines — opens body mode that discards every later line, which is the severity class AMH
	#   ledger row D016 item 1 records: the rails do not fire because they never see the input.
	st_blocked 'echo "a \" << EOF b"
git push --force origin main'
	st_blocked 'echo "write \" <<EOF into the doc"
cat .env'
	#   split_words: the word boundary decides whether `.env` is an operand at all.
	st_blocked 'cat "a \" b" .env'
	#   strip_redirections, outer loop: a quoted `>` read as a real redirection cuts the
	#   operand that names the secret out of the segment before anything judges it.
	st_blocked 'cat "a \" > out" .env'
	st_blocked 'grep -q "a \" > out" .env'
	#   strip_redirections, target loop, and redirect_targets: both fail the OTHER way — a
	#   quoted `<` read as a redirection makes prose about `.env` look like a read of it,
	#   which is AMH ledger row D007 in the scanner D007 was written about.
	st_allowed 'cat < "x \" .env"'
	st_allowed 'grep -q "x \" < .env" file.txt'
	st_allowed 'grep -q "pattern \" < id_rsa" file.txt'
	# ...and none of that is bought by breaking AMH ledger row D007. Quoted prose with an escaped quote
	# is still DATA, including the shape this repository writes constantly.
	st_allowed 'echo "a \"q\" | rm -rf /tmp/x | b"'
	st_allowed 'echo "a \" | db:reset | b"'
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
	# The false-positive budget for the tree-mutating git verbs, and the whole reason they
	# could be added: a LITERAL operand names a target the agent has already written down, so
	# it gets no advisory. Widen the gate to fire on these and the rail starts crying wolf on
	# the commonest correct commands in the repository — which is how an agent learns to skim
	# past it for `rm -rf` as well.
	st_allowed 'git reset --hard HEAD~1'
	st_allowed 'git reset --hard origin/main'
	st_allowed 'git checkout -f main'
	st_allowed 'git switch --discard-changes main'
	st_allowed 'git worktree add wt HEAD'
	st_allowed 'git worktree remove wt'
	st_allowed 'git restore src/main.c'
	# Non-destructive modes of the same verbs, with a variable, must stay silent: the gate is
	# the operand shape, never a licence to advise on the whole verb.
	st_allowed 'git reset --soft "$BASE"'
	st_allowed 'git reset "$BASE"'
	st_allowed 'git restore --staged "$D"'
	st_allowed 'git checkout "$REF"'
	st_allowed 'git switch "$BRANCH"'
	st_allowed 'git worktree list'
	st_allowed 'git worktree prune'
	st_allowed 'git worktree repair'
	# The whole-tree widening must not swallow the ordinary literal spellings, and a global
	# option with a LITERAL value is still a known target.
	st_allowed 'git -C build reset --hard HEAD~1'
	st_allowed 'git rm -r -f build/'
	st_allowed 'git worktree move wt other'
	st_allowed 'git rm --cached "$X"'
	st_allowed 'git rm -r -n "$X"'
	# Prose naming a forbidden path.
	st_allowed 'grep -rn "force-push" docs/RUNBOOK.md'
	# Ordinary correct usage.
	st_allowed "git push -u origin $BRANCH_PREFIX/some-codename"
	st_allowed "git push -u origin $BRANCH_PREFIX/x && echo pushed"
	# The namespace is NOT this rail's business (AMH ledger row DA022). One explicitly named
	# ref passes whatever it is called, because the harness assigns names the repository does
	# not prefix and the rail cannot tell an assigned name from an invented one. `claude/…` is
	# the case that actually bit: the shipped rail blocked a correctly assigned branch.
	st_allowed 'git push -u origin work'
	st_allowed 'git push -u origin claude/some-codename'
	st_allowed "git push origin $BRANCH_PREFIX/x:refs/heads/work"
	# ...and dropping the namespace must not drop what the rail CAN read. Each of these was
	# denied by the namespace test before and has to be denied on its own ground now, or the
	# removal took real coverage with it.
	st_blocked 'git push origin HEAD'
	st_blocked 'git push origin @'
	st_blocked "git push origin $BRANCH_PREFIX/x:HEAD"
	st_blocked 'git push origin'
	st_blocked "git push origin $BRANCH_PREFIX/x other"
	st_blocked "git push origin $BRANCH_PREFIX/x $BRANCH_PREFIX/y"
	st_blocked 'git push origin work other'
	st_blocked "git push --delete origin $BRANCH_PREFIX/x"
	st_blocked "git push origin :$BRANCH_PREFIX/x"
	st_blocked 'git push origin :work'
	# `HEAD` as the SOURCE names an explicit destination — a rail matching `HEAD` anywhere in
	# the refspec would deny this one too.
	st_allowed "git push -u origin HEAD:$BRANCH_PREFIX/x"
	# Every spelling git resolves to the default branch. `heads/<default>` is the one the
	# retired namespace test had been catching by accident, on BOTH sides of the colon, and
	# git really does move the remote branch for each of these.
	st_blocked "git push origin heads/$DEFAULT_BRANCH"
	st_blocked "git push origin $BRANCH_PREFIX/x:heads/$DEFAULT_BRANCH"
	st_blocked "git push origin refs/heads/$BRANCH_PREFIX/x:heads/$DEFAULT_BRANCH"
	st_blocked "git push origin HEAD:heads/$DEFAULT_BRANCH"
	# This one pins the VERDICT only: drop the alias from the `+*` arm and it stays blocked as
	# a plain force push, losing the two-violations-at-once reason the arm exists to give. The
	# self-test compares verdicts, so no fixture can hold that reason — it is a review concern.
	st_blocked "git push origin +heads/$DEFAULT_BRANCH"
	# ...and a branch whose name merely STARTS with `heads/` is not the default branch.
	st_allowed "git push -u origin heads/$BRANCH_PREFIX-x"
	# Tagging is an owner step. Only the explicit spelling is readable here; a bare tag name
	# is a branch name as far as this scanner can tell, which is why the allow case below is
	# in the suite rather than left to be discovered.
	st_blocked 'git push origin refs/tags/amh-v1.2.3'
	# Verdict-only again: this is a deletion too, so it stays blocked without the tag arm.
	st_blocked 'git push origin :refs/tags/amh-v1.2.3'
	st_blocked "git push origin $BRANCH_PREFIX/x:refs/tags/amh-v1.2.3"
	st_allowed 'git push -u origin amh-v1.2.3'
	# A redirection is syntax, not a second ref. Every one of these names ONE session
	# branch, and the shipped rail denied all of them for naming another (AMH ledger row
	# DC001) — the glued-on target, the detached one, and the `2>` that `split_segments`
	# leaves behind when it consumes the `&` of `2>&1`.
	st_allowed "git push -u origin $BRANCH_PREFIX/x 2>&1"
	st_allowed "git push -u origin $BRANCH_PREFIX/x >/dev/null"
	st_allowed "git push -u origin $BRANCH_PREFIX/x > /dev/null 2>&1"
	st_allowed "git push -u origin $BRANCH_PREFIX/x >>push.log"
	st_allowed "git push -u origin $BRANCH_PREFIX/x 2>err.log"
	# An option argument is not a refspec either, and a redirection standing between an
	# option and its argument must not shift the words after it.
	st_allowed "git push -o >/dev/null ci.skip origin $BRANCH_PREFIX/x"
	# ...and dropping the syntax must not drop the words around it. Each of these is
	# discriminating: a skip that always swallowed the following word would let the force
	# push and the second refspec through, and the suite would stay green on the allow
	# cases above.
	st_blocked "git push -u origin >/dev/null --force $BRANCH_PREFIX/x"
	st_blocked "git push -u origin >/dev/null $BRANCH_PREFIX/x $BRANCH_PREFIX/y"
	st_blocked "git push -u origin >/dev/null $DEFAULT_BRANCH"
	st_blocked "git push -u origin 2>&1"
	# A redirection between the command and its SUBCOMMAND hid the push from every check
	# above: bash hands git `push origin <default>` either way. Any position, one rule.
	st_blocked "git >/dev/null push origin $DEFAULT_BRANCH"
	st_blocked "git 2>err.log push --force origin $BRANCH_PREFIX/x"
	# ...and a redirection before the command word hid the command itself, from EVERY rail.
	st_blocked '>/dev/null printenv'
	st_blocked ">/dev/null git push origin $DEFAULT_BRANCH"
	# A QUOTED operator is a literal argument bash really passes, so it is a word, not
	# syntax — the AMH ledger row D007 class one layer down. Treating it as syntax would
	# swallow the word after it, which is `--force` here.
	st_blocked "git push -u origin $BRANCH_PREFIX/x '2>' --force"
	st_blocked "git push -u origin $BRANCH_PREFIX/x '>' $DEFAULT_BRANCH"
	# An fd number is syntax; any other word prefix is a word bash passes on (`abc2>err`
	# hands the command `abc2`), so the operand is still judged.
	st_blocked "git push -u origin $BRANCH_PREFIX/x abc2>/dev/null"
	# Prose about a redirection is prose, in the file this guard is likeliest to be
	# described in.
	st_allowed 'git commit -m "use > to redirect, and read the 2> form"'
	st_allowed "git push -o ci.skip origin $BRANCH_PREFIX/x"
	st_allowed "git push --receive-pack git-receive-pack origin $BRANCH_PREFIX/x"
	st_allowed 'env FOO=1 make test'
	# The rail's own false positive, and the reason this arm was rewritten: unsetting a
	# name prints nothing, and this repository's shipped fixture suite runs that exact
	# spelling. Every form of it was refused before 10.1.2.
	st_allowed 'env -u AMH_REMOTE bash scripts/session-start.sh'
	st_allowed 'env -uAMH_REMOTE make test'
	st_allowed 'env --unset=AMH_REMOTE make test'
	st_allowed 'env --unset AMH_REMOTE make test'
	st_allowed 'env -i make test'
	st_allowed 'env -i FOO=1 make test'
	st_allowed 'env -C /tmp make test'
	st_allowed 'env --u AMH_REMOTE make test'
	st_allowed 'env --version'
	st_allowed 'env --vers'
	# An option whose argument is missing is a usage error that runs nothing. "Your input
	# is strange" fails OPEN, and a block reason saying this printed the environment
	# would be false about a command that printed nothing at all.
	st_allowed 'env -u'
	st_allowed 'env --unset'
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
	# The public half of a key pair is meant to be read, and the extension decides — the
	# stem is `id_rsa` in every one of these.
	st_allowed 'cat ~/.ssh/id_rsa.pub'
	st_allowed 'ssh-keygen -lf id_ed25519.pub'
	st_allowed 'cat id_rsa-cert.pub'
	# A file whose name merely contains the stem is not the key, and prose about one is prose.
	st_allowed 'cat id_rsa_fixture_notes.md'
	st_allowed 'git commit -m "block cat id_rsa at the rail"'
	# Inherited false positive, fixtured so it is a decision rather than a surprise: a
	# one-word search PATTERN is indistinguishable from a path after quote stripping, so
	# grepping the tree for the literal stem is blocked — exactly as `grep -rn ".env" docs/`
	# has always been.
	st_blocked 'grep -rn "id_rsa" docs/'
	# `.pem`/`.key` after the one-time advisory is spent: the container extension alone is
	# not evidence, and the commonest file bearing it is a public certificate.
	st_allowed 'cat fullchain.pem'
	st_allowed 'openssl x509 -in cert.pem -noout -subject'
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
	# A branch whose name merely CONTAINS the default branch name. The first of these was
	# `st_blocked` while the namespace test stood, and passed for the wrong reason — the
	# namespace denied it before the default-branch patterns were ever asked about the
	# substring. Now it tests what its comment always claimed.
	st_allowed "git push -u origin ${DEFAULT_BRANCH}tenance"
	st_allowed "git push -u origin $BRANCH_PREFIX/$DEFAULT_BRANCH-cleanup"
	st_blocked "git push -u origin refs/heads/$DEFAULT_BRANCH"
	# Fail-open on an empty or odd command.
	st_allowed ''
	st_allowed '   '

	# --- the pre-push rail (git-native, P13). prepush_verdict is pure over four fields.
	local pp_zero=0000000000000000000000000000000000000000
	local pp_a=1111111111111111111111111111111111111111
	local pp_b=2222222222222222222222222222222222222222
	# Push to the default branch is denied whatever the shas.
	st_prepush_blocked "refs/heads/$DEFAULT_BRANCH" "$pp_a" "refs/heads/$DEFAULT_BRANCH" "$pp_b"
	st_prepush_blocked "refs/heads/x" "$pp_a" "refs/heads/$DEFAULT_BRANCH" "$pp_zero"
	# Deleting a remote branch (local sha all-zero) is denied.
	st_prepush_blocked '(delete)' "$pp_zero" "refs/heads/$BRANCH_PREFIX/x" "$pp_a"
	# Creating a branch (remote sha all-zero) runs no ancestry test and is allowed.
	st_prepush_allowed "refs/heads/$BRANCH_PREFIX/x" "$pp_a" "refs/heads/$BRANCH_PREFIX/x" "$pp_zero"
	st_prepush_allowed 'refs/heads/feature' "$pp_a" 'refs/heads/feature' "$pp_zero"
	# Fast-forward passes; non-fast-forward (force by effect) is denied; undetermined (missing
	# objects / shallow clone) fails OPEN. The ancestry seam is stubbed; real glue is in
	# test-ladder-guards.sh.
	st_prepush_ff ff allow "refs/heads/$BRANCH_PREFIX/x" "$pp_b" "refs/heads/$BRANCH_PREFIX/x" "$pp_a"
	st_prepush_ff nonff block "refs/heads/$BRANCH_PREFIX/x" "$pp_a" "refs/heads/$BRANCH_PREFIX/x" "$pp_b"
	st_prepush_ff undetermined allow "refs/heads/$BRANCH_PREFIX/x" "$pp_a" "refs/heads/$BRANCH_PREFIX/x" "$pp_b"
	# No branch-prefix check (AMH ledger row DA022): an assigned name the repo does not prefix
	# passes a fast-forward and a create exactly like a session ref.
	st_prepush_ff ff allow 'refs/heads/claude/assigned-name' "$pp_b" 'refs/heads/claude/assigned-name' "$pp_a"
	st_prepush_allowed 'refs/heads/claude/assigned-name' "$pp_a" 'refs/heads/claude/assigned-name' "$pp_zero"
	# Tagging is an owner step, and this rail sees the RESOLVED ref — so unlike the flag rail
	# it also catches `git push origin v1`, which arrives here as `refs/tags/v1`. Creating,
	# moving and deleting are all denied; the delete case is checked by the tag arm, which
	# sits above the delete arm, so its reason has to fit a deletion too.
	st_prepush_blocked 'refs/tags/amh-v1.2.3' "$pp_a" 'refs/tags/amh-v1.2.3' "$pp_zero"
	st_prepush_blocked 'refs/tags/amh-v1.2.3' "$pp_a" 'refs/tags/amh-v1.2.3' "$pp_b"
	st_prepush_blocked '(delete)' "$pp_zero" 'refs/tags/amh-v1.2.3' "$pp_a"
	# A BRANCH whose name merely contains `tags` is not a tag.
	st_prepush_allowed "refs/heads/$BRANCH_PREFIX/tags-cleanup" "$pp_a" "refs/heads/$BRANCH_PREFIX/tags-cleanup" "$pp_zero"

	rm -f -- "$self_keymaterial_advisory_state"
	if [ -n "$old_keymaterial_advisory_state_set" ]; then
		KEYMATERIAL_ADVISORY_STATE=$old_keymaterial_advisory_state
	else
		unset KEYMATERIAL_ADVISORY_STATE
	fi
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

# Print the destructive advisories this session fired and never saw re-attempted, one per
# line. It reports an OBSERVED artifact — the rail's own state files — and it is the whole
# of what this rail can honestly say about compliance: it knows a prompt fired and it knows
# whether the command came back. It does NOT know whether anyone looked, and no guard, gate,
# CI step or decision procedure may read this output as evidence that a check happened or
# did not (P3, AMH ledger row D014). It is a sentence for whoever reads the transcript.
#
# Exit is always 0, including when the state files are absent, which is the ordinary case in
# a session that deleted nothing.
advisory_report() {
	local state advised
	state=$(advisory_state_file destructive) || return 0
	[ -n "$state" ] || return 0
	[ -e "$state" ] || return 0
	while IFS= read -r advised; do
		[ -n "$advised" ] || continue
		if [ -e "$state.resumed" ] && LC_ALL=C grep -qxF -- "$advised" "$state.resumed" 2>/dev/null; then
			continue
		fi
		printf '%s\n' "$advised"
	done <"$state"
	return 0
}

# The subagent-spawn speed bump. A THIRD entry point, and the one with the least vendor
# coupling of the three: it reads no field of the payload and makes no claim about which tool
# fired it, because the only fact it needs is that a spawn is about to happen — the adapter
# that wires it has already established that by matching. Parsing a spawn payload would tie
# this rail to one vendor's JSON for nothing, and an adapter for a host that spells the spawn
# differently can point at the same entry point unchanged.
#
# The payload is drained rather than ignored so a host writing to this process does not take
# EPIPE, and the drain is skipped on a terminal so an interactive invocation does not hang.
run_pretask() {
	HOOK_INVOCATION=1
	[ -t 0 ] || cat >/dev/null 2>&1 || :
	if needs_one_time_advisory subagent ''; then
		printf 'BLOCKED by the AMH command guard.\n\n%s\n' "$ADVISORY_REASON" >&2
		exit 2
	fi
	exit 0
}

# The subagent rail's trace, kept separate from advisory_report on purpose: that function's
# output is printed under a heading naming DESTRUCTIVE advisories, and folding a spawn count
# into it would put a true line under a false label. Prints nothing when no spawn proceeded,
# which is the ordinary case in a session that spawned nothing.
#
# Same bounded claim as its sibling, and it matters more here because a count LOOKS like a
# measurement: this says a spawn was advised and went ahead. It does not say the spawns
# overlapped, that any of them was unnecessary, or that anyone weighed the rule. No guard,
# gate, CI step or decision procedure may read it as evidence of a check (P3, AMH ledger row
# D014).
spawn_report() {
	local state count
	state=$(advisory_state_file subagent) || return 0
	[ -n "$state" ] || return 0
	[ -e "$state.resumed" ] || return 0
	count=$(LC_ALL=C grep -c '' "$state.resumed" 2>/dev/null) || return 0
	[ "$count" -gt 0 ] || return 0
	printf '%s\n' "$count"
	return 0
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
--pre-push) run_prepush ;;
--pre-task) run_pretask ;;
--self-test) self_test ;;
--advisory-report) advisory_report ;;
--spawn-report) spawn_report ;;
*)
	printf 'usage: %s [--command CMD|--pre-push|--pre-task|--self-test|--advisory-report|--spawn-report]\n' "$0" >&2
	exit 2
	;;
esac
