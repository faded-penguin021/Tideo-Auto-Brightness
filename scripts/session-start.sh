#!/usr/bin/env bash
# AMH — agent-neutral session bootstrap (P14). The machine's boot sequence; the
# ladder's guards are its power-on self-test.
#
# Idempotent and safe to run repeatedly. Any agent's session-start hook invokes it;
# an agent with no hook mechanism runs it manually (the constitution says so).
# It self-locates the repo root from its own path and keys remote-only steps off an
# explicit neutral flag — never one agent's environment variables.
#
# Shipped by the Agentic Maintenance Harness. Repo-agnostic: repo-specific toolchain
# setup belongs in scripts/bootstrap.sh, which this script calls when present.

set -uo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 0

DEFAULT_BRANCH=main
BRANCH_PREFIX=session
# Defaulted here because this script did not read the key until the squash-history line
# below existed. An adopter's amh.conf is theirs forever and the harness cannot upgrade it,
# so a key this script needs but their file predates must have a value in the script or the
# whole banner dies under `set -u` on the first upgraded session.
MERGE_MODE='branch-per-change'
# Both empty by default, which switches the release-window line off entirely. Same reasoning as
# MERGE_MODE above: an adopter's amh.conf predates these keys and is theirs forever. Off is also
# the right default on merit — a repo that does not tag releases would get a line about a tag it
# never intends to cut.
VERSION_FILE=''
RELEASE_TAG_PREFIX=''
STATE_FILE=docs/STATE.md
STATE_WARN_KB=14
STATE_COMPRESS_TO_KB=9
REMOTE_FLAG=AMH_REMOTE
# Both empty by default, which switches the runtime-inventory lines off entirely. Same
# reasoning as MERGE_MODE and the release keys above: an adopter's amh.conf is theirs forever
# and the harness cannot upgrade it, so a key this script reads but their file predates must
# have a value here, or the whole banner dies under `set -u` on the first upgraded session.
# Both lists live in amh.conf rather than here so this script names no vendor and no
# toolchain of its own — the adopter declares theirs (P14).
REQUIRED_TOOLS=''
ADAPTER_FILES=''
# shellcheck source=/dev/null
[ -f "$ROOT/amh.conf" ] && . "$ROOT/amh.conf"

say() { printf '%s\n' "$*"; }


reset_dotenv_advisory() {
	local slug uid state
	slug=${ROOT//\//_}
	slug=${slug// /_}
	uid=${UID:-unknown}
	state=/tmp/amh-command-guard-dotenv-advisory-$uid-$slug
	rm -f -- "$state" 2>/dev/null || true
}

reset_dotenv_advisory

say "── AMH session start ─────────────────────────────────────────"

# 1. Toolchain bootstrap — remote/ephemeral containers only, gated on an explicit
#    flag. A heuristic here would surprise someone on their own machine.
#
#    Every way this step can be switched off by something that is NOT the flag's value
#    now says so. (A flag set to anything other than 1 stays silent, correctly: that is
#    the flag doing its job, not a property unrelated to it.) There used to be three
#    silent ones, all of the same shape — a property that is not the step's subject
#    switched it off, and the output was indistinguishable from a machine that simply is
#    not remote.
#
#    (a) `${!REMOTE_FLAG}` on a name that is not a shell identifier — `AMH-REMOTE`, say —
#        writes a bad-substitution error to stderr and yields nothing, so the test fails
#        and the bootstrap never runs. The value is validated here rather than trusted:
#        amh.conf presents the flag as free-form and nothing downstream constrains it.
#    (b) The gate was `-x scripts/bootstrap.sh`, so a file present but 0644 — an archive
#        extraction, `core.fileMode=false`, one stray chmod — disappeared without a word.
#        Presence is the question; the script is invoked through `bash`, which deletes the
#        dependency on the mode instead of policing it.
#    (c) The flag set and no bootstrap at all is a legitimate state (an adopter may have
#        no toolchain to install), but it is worth one line, because the alternative is a
#        remote session silently missing a step it was configured to take.
#
#    None of these is fatal. A boot hook that refuses to let the session start is worse
#    than one that skips a tool, and the ladder re-checks everything that matters anyway.
case $REMOTE_FLAG in
'' | [!A-Za-z_]* | *[!A-Za-z0-9_]*)
	say "· ⚠ REMOTE_FLAG '$REMOTE_FLAG' is not a valid shell variable name — toolchain bootstrap SKIPPED"
	say "    Set REMOTE_FLAG in amh.conf to a plain identifier (letters, digits, underscore; not starting with a digit)."
	;;
*)
	if [ "${!REMOTE_FLAG:-0}" = "1" ]; then
		if [ -f scripts/bootstrap.sh ]; then
			say "· remote environment ($REMOTE_FLAG=1): running scripts/bootstrap.sh"
			bash scripts/bootstrap.sh || say "  ! bootstrap reported a problem — the first ladder run will show it"
		else
			say "· ⚠ remote environment ($REMOTE_FLAG=1) but scripts/bootstrap.sh does not exist — toolchain bootstrap SKIPPED"
		fi
	fi
	;;
esac

# 2. Branch check. The first misplaced commit is the expensive one.
branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)
if [ "$branch" = "HEAD" ]; then
	say "· ⚠ DETACHED HEAD — commits here are easy to lose. Check out a $BRANCH_PREFIX/<codename> branch."
elif [ "$branch" = "$DEFAULT_BRANCH" ]; then
	say "· ⚠ You are on '$DEFAULT_BRANCH'. Pushing to it is denied (AMH P13)."
	say "    git checkout -b $BRANCH_PREFIX/<codename>"
else
	say "· branch: $branch"
fi

# 2b. Under branch-train, say once what the default branch's history is NOT.
#
# A train squashes each superset branch into one commit on the default branch, so `git log`
# there is a list of merges, not a record of how anything was decided — and a session that
# reaches for it to answer "why is this like this?" gets a plausible, wrong answer and
# carries on. The memory tiers are the history; that is what P2 is for.
#
# Printed only under branch-train, because under branch-per-change the default branch's log
# IS the record and this line would be false. A pre-execution warning on `git log` itself was
# considered and declined: the rail is binary, the command is right nearly every time (two
# shipped rungs run it), and the mistake is the generalisation rather than the command.
if [ "$MERGE_MODE" = branch-train ]; then
	say "· merge mode: branch-train — $DEFAULT_BRANCH's history is squashed, so \`git log\` there is"
	say "  not this repo's past. The state file and the ledger are (AMH P2)."
fi

# 2c. The release window: is the version in the tree actually tagged?
#
# A version file is a claim about a release, and docs written against it — an install command
# naming a tag, an upgrade note — become true only when someone cuts that tag. Between the merge
# and the tag they are false, and no guard here can see it: a version-consistency check compares
# strings inside the tree, and the release workflow that validates a tag runs *after* the tag
# exists (AMH ledger rows D010, DA010).
#
# The banner is the right layer precisely because this is not enforceable. It states a fact and
# lets the session judge, which is also why it is worth having for the state it CANNOT see: a
# queue item saying "tag vX" outlives the tagging, and the session that inherits it will restate
# it as pending unless something puts the world in front of it first (AMH ledger row DA011).
#
# The absent case asks the REMOTE, and that is the whole design rather than a refinement.
#
# A local-refs-only check answers a different question from the one this line asks — many clones
# never fetch tags at all (the default refspec covers heads, and a shallow or filtered clone may
# carry none), so "not in this clone" is the steady state in a repo where every release IS
# tagged. A line that alarms every session is indistinguishable from one that alarms correctly,
# and the tripwire is then worth nothing: warn fatigue is why. So the local ref is the fast path,
# and its absence is escalated to `ls-remote` rather than reported as absence.
#
# Four outcomes, and NONE of them is "cannot tell" reported as "not there": unreachable is its
# own branch, because a session that cannot ask must not be told the answer. The remote probe is
# bounded by `timeout` where that exists and by GIT_TERMINAL_PROMPT=0 always — a boot sequence
# that blocks on a credential prompt is worse than one that reports it could not look.
if [ -n "$VERSION_FILE" ] || [ -n "$RELEASE_TAG_PREFIX" ]; then
	if [ -z "$VERSION_FILE" ] || [ -z "$RELEASE_TAG_PREFIX" ]; then
		# Half-configured is a typo, not a choice. Silence here would be identical to the
		# output of the adopter who deliberately set neither, which is the louder-when-off
		# failure the harness refuses elsewhere.
		say "· ⚠ release line needs BOTH VERSION_FILE and RELEASE_TAG_PREFIX in amh.conf; only one is set — SKIPPED"
	elif [ -d "$VERSION_FILE" ]; then
		say "· ⚠ VERSION_FILE '$VERSION_FILE' is a directory, not a file — release line SKIPPED"
	elif [ ! -f "$VERSION_FILE" ]; then
		say "· ⚠ VERSION_FILE '$VERSION_FILE' is set in amh.conf but does not exist — release line SKIPPED"
	else
		# First line only: a version file with a trailing note would otherwise be concatenated
		# into a tag name no release could ever match.
		version=$(head -1 -- "$VERSION_FILE" | tr -d '[:space:]')
		tag="$RELEASE_TAG_PREFIX$version"
		if [ -z "$version" ]; then
			say "· ⚠ VERSION_FILE '$VERSION_FILE' has no version on its first line — release line SKIPPED"
		elif git rev-parse -q --verify "refs/tags/$tag" >/dev/null 2>&1; then
			say "· release: $VERSION_FILE says $version, and tag $tag is in this clone."
		else
			if command -v timeout >/dev/null 2>&1; then
				remote=$(GIT_TERMINAL_PROMPT=0 timeout 10 git ls-remote --tags origin "refs/tags/$tag" 2>/dev/null)
				rc=$?
			else
				remote=$(GIT_TERMINAL_PROMPT=0 git ls-remote --tags origin "refs/tags/$tag" 2>/dev/null)
				rc=$?
			fi
			if [ "$rc" -ne 0 ]; then
				say "· release: $VERSION_FILE says $version. Tag $tag is not in this clone, and origin could"
				say "  not be reached to check — offline, no remote, or no git. This is not evidence either way."
			elif [ -n "$remote" ]; then
				say "· release: $VERSION_FILE says $version, and tag $tag exists on origin (not fetched into"
				say "  this clone — \`git fetch --tags\` if you need it locally). The release is cut."
			else
				say "· release: $VERSION_FILE says $version, and NO tag $tag exists on origin — UNRELEASED."
				say "  Docs naming that tag, an install command above all, are promises nobody has kept yet."
			fi
		fi
	fi
fi

# 3. Working-memory headroom, BEFORE any writing — so a session that needs to
#    compress learns it now, not from a failed commit-time guard.
if [ -f "$STATE_FILE" ]; then
	bytes=$(wc -c <"$STATE_FILE")
	warn_b=$((STATE_WARN_KB * 1024))
	printf '· %s: %s KB of %s KB soft cap\n' "$STATE_FILE" "$((bytes / 1024))" "$STATE_WARN_KB"
	if [ "$bytes" -gt "$warn_b" ]; then
		say "    ⚠ over the soft cap — run ONE deep compression pass to ≤ ${STATE_COMPRESS_TO_KB} KB before adding to it."
	fi
else
	say "· ⚠ $STATE_FILE is missing — working memory is where every session starts."
fi

# 3b. Runtime inventory: which declared tools are on PATH, and which adapter files this
#     repository ships. Both lists come from amh.conf, so this script names no vendor.
#
#     NOTHING CONSUMES THIS. It is a line a human reads, printed and discarded. No guard, CI
#     step, gate or agent decision procedure may branch on these states, and none does — the
#     manifest that would have stored them was refused precisely so that no future code could
#     (AMH ledger rows DA024 and DA001).
#
#     Two vocabularies, and the asymmetry between them is the entire point:
#
#     (a) A TOOL is `observed` or `unavailable`. `type -P` is a real probe — it ran, and its
#         answer is a fact about this environment. Only the NAME is printed, never the resolved
#         path: that leaks a home directory and a username into the transcript for no diagnostic
#         gain (P17).
#
#         `type -P` and NOT `command -v`, which resolves builtins, functions and aliases before
#         it looks at PATH. Under `command -v` this script reports its own `say()` helper — and
#         anything an adopter's sourced amh.conf happens to define — as an installed tool, and
#         `printf` as `observed` on a machine holding no binaries at all. `observed` is the one
#         state whose entire warrant is "the probe ran and the answer is a fact about this
#         environment"; a builtin makes it a fact about this bash instead. The design is careful
#         that `unknown` never becomes `unavailable`; this is the symmetric hazard, a non-fact
#         becoming `observed`, and it is the one the fixtures pin.
#     (b) An ADAPTER FILE is `configured` or `unknown` — NEVER `observed`, and never
#         `unavailable`. Present means the repository REQUESTS an integration; it is not evidence
#         that a hook ever fired, and nothing here can see one fire. That is why the lifecycle
#         probe layer was refused rather than deferred: a marker cannot name its caller, and the
#         manual path the constitution mandates writes a byte-identical one. Absent means this
#         repository declares none — a user-level or globally-configured adapter is invisible
#         from inside the tree, so `unavailable` would be a claim about the world derived from a
#         fact about the repo. `unknown` is never translated into `unavailable`, `disabled` or
#         `safe`.
#
#     `set -f` around both loops. An entry containing a glob character would otherwise be
#     expanded against the working directory, so `REQUIRED_TOOLS='*'` would report every file in
#     the repo root as a missing tool. Neither list can express a name containing a space, and
#     saying so here is cheaper than a quoting scheme nobody needs.
#     Each line is gated on whether its LOOP produced anything, not on whether the config value
#     was non-empty: a whitespace-only value splits to nothing, and the raw test would print a
#     bare `· tools:` header, or `· adapters:` followed by two lines of gloss explaining zero
#     states.
if [ -n "$REQUIRED_TOOLS" ]; then
	inv=''
	set -f
	for t in $REQUIRED_TOOLS; do
		if type -P -- "$t" >/dev/null 2>&1; then
			inv="$inv $t observed ·"
		else
			inv="$inv $t unavailable ·"
		fi
	done
	set +f
	[ -n "$inv" ] && say "· tools:${inv% ·}"
fi

if [ -n "$ADAPTER_FILES" ]; then
	inv=''
	set -f
	for a in $ADAPTER_FILES; do
		if [ -e "$a" ]; then
			inv="$inv $a configured ·"
		else
			inv="$inv $a unknown ·"
		fi
	done
	set +f
	if [ -n "$inv" ]; then
		say "· adapters:${inv% ·}"
		say "  configured = requested by this repo, never observed firing; unknown = none declared here,"
		say "  which is not evidence that none exists. Nothing reads these states."
	fi
fi

# 4. Protocol pointer.
#
# The runbook clause is conditional on the file EXISTING, because not every install profile
# ships one and the smallest profile — the default — deliberately does not. Pointing a fresh
# session at a document that is not there teaches it to distrust the banner, and the adopter
# cannot fix it themselves: this script is overwritten on every upgrade by design.
if [ -f "$ROOT/docs/RUNBOOK.md" ]; then
	say "· protocol: read $STATE_FILE first (incl. the Owner queue), then the matching"
	say "  playbook in docs/RUNBOOK.md. Verify with scripts/ladder.sh. Never leave the branch red."
else
	say "· protocol: read $STATE_FILE first (incl. the Owner queue), then the constitution's"
	say "  playbooks. Verify with scripts/ladder.sh. Never leave the branch red."
fi
say "──────────────────────────────────────────────────────────────"
exit 0
