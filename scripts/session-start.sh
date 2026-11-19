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
STATE_COMPRESS_TO_SENTENCES=50
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


# Rearm the command guard's one-time advisories. Each is a per-session speed bump, and this
# boot is what makes "one session" mean anything: the state file the guard writes under /tmp
# outlives the session otherwise, so in a long-lived container the first warning of the
# container's lifetime spends the advisory for every later session in the same repository.
# The `.env` diagnostic states that scope in words; the destructive-command one does not, and
# would silently have been once per container. Read command-guard.sh for what each says.
#
# The CATEGORY is a wildcard, deliberately. This function was written for `dotenv` alone and
# named the category literally, so when the guard grew a second category the bootstrap kept
# rearming exactly one of them and nothing said so: a list here is the same defect with an
# extra step. The rest of the name is quoted, so only the category slot globs — a repository
# path containing a glob character stays a literal rather than widening the pattern. That slot
# is a greedy `*` that does not cross `/`, so the widest reach is another repository's file of
# exactly this shape under /tmp or TMPDIR, and erasing one of those is harmless: an advisory that rearms
# early blocks one extra command and explains why, which is the direction this file is allowed
# to fail in. Erasing one too FEW removes a rail in silence, which is why the pattern is wide.
#
# Globbing is forced on for the expansion rather than assumed. amh.conf is sourced above, it is
# the adopter's file forever, and a `set -f` or `GLOBIGNORE` in it would leave the pattern
# unexpanded — with `rm -f` swallowing the literal, that is a rail switched off in silence, the
# same shape as the defect this function exists to fix. The no-match case still reaches `rm`
# unexpanded, so the loop tests each entry exists rather than trusting the expansion.
#
# The `.resumed` sibling is named EXPLICITLY, and this is the one place the paragraph above does
# not apply. The guard writes an advisory state file for every category, and for TWO of them —
# destructive and subagent — a `.resumed` ledger of the advisories a session went ahead with; the
# other categories never gain one. For as long as this loop stopped at
# `$slug` it deleted the state and never the sibling, so the reports built on `.resumed` spanned
# every session that shared the container: `--spawn-report` counted spawns from sessions long
# gone, and `--advisory-report` went SILENT about a deletion abandoned this session whenever the
# same command text had been resumed in an earlier one. That is the report's whole job, so the
# sibling has to go the way the state file does.
#
# Widening the pattern to `"$slug"*` would have reached it, and is refused: the wide-is-safe
# argument above holds for the state file, where an early rearm costs one extra prompt, and
# INVERTS for `.resumed`, where erasing a neighbouring repository's copy destroys the record of
# what that repository's sessions did. `/home/user/AMH*` also matches `/home/user/AMH-fork`.
# So the two names are enumerated instead. A new sibling suffix in the guard needs a new entry
# here; that coupling is the price of not globbing WIDER across repository boundaries. It narrows
# that reach rather than closing it: the category slot is still a greedy `*`, so a neighbouring
# repository whose path embeds this exact shape stays reachable. Contrived, and not a reason to
# widen — but the absolute would be false.
#
# A marker that cannot be removed is REPORTED, not swallowed. A state file left behind is a
# spent advisory this session inherits, so the rail is off for it with nothing on screen saying
# so; a `.resumed` left behind makes both reports span earlier sessions. A `/tmp` this process
# sees read-only while the hook that wrote there did not is the plain way to get either. The
# warning names the directory by its ROLE — `/tmp` or `TMPDIR` — and prints neither the marker,
# whose name embeds this repository's path (AMH ledger row DD097), nor TMPDIR's value, which
# can embed a home directory. It is a banner line and nothing else: the exit status stays 0,
# because a boot step that cannot clear a file must not stop the session (P14). The labels are
# kept in a plain string, not read back through a here-string, which bash before 5.1 builds as
# a temporary file — in the very directory that may be unwritable.
ADVISORY_RESET_FAILED=''
reset_command_guard_advisories() {
	local slug uid f dir had_globignore old_globignore noglob=0 failed
	local -a dirs=(/tmp)
	case $- in *f*) noglob=1 ;; esac
	set +f
	had_globignore=${GLOBIGNORE+x}
	old_globignore=${GLOBIGNORE:-}
	GLOBIGNORE=
	slug=${ROOT//\//_}
	slug=${slug// /_}
	uid=${UID:-unknown}
	# Both places the command guard may have written: /tmp, and TMPDIR when it names an absolute
	# directory (its advisory_state_dir). A hook that saw no TMPDIR wrote to /tmp even when this
	# process sees one, so both are cleared. An unwritable directory with no marker in it says
	# nothing; one holding a marker that stays after `rm` is reported (see above).
	dir=${TMPDIR:-}
	case $dir in /*) ;; *) dir= ;; esac
	while [ "$dir" != "${dir%/}" ]; do dir=${dir%/}; done
	[ -n "$dir" ] && [ "$dir" != /tmp ] && dirs+=("$dir")
	for dir in "${dirs[@]}"; do
		failed=0
		for f in "$dir"/amh-command-guard-*-advisory-"$uid"-"$slug" \
			"$dir"/amh-command-guard-*-advisory-"$uid"-"$slug".resumed; do
			if [ -e "$f" ]; then
				rm -f -- "$f" 2>/dev/null
				[ -e "$f" ] && failed=1
			fi
		done
		if [ "$failed" = 1 ]; then
			if [ "$dir" = /tmp ]; then
				ADVISORY_RESET_FAILED="$ADVISORY_RESET_FAILED /tmp"
			else
				ADVISORY_RESET_FAILED="$ADVISORY_RESET_FAILED TMPDIR"
			fi
		fi
	done
	if [ -n "$had_globignore" ]; then GLOBIGNORE=$old_globignore; else unset GLOBIGNORE; fi
	[ "$noglob" = 1 ] && set -f
	return 0
}

reset_command_guard_advisories

# Install the git-native pre-push rail (P13) into .git/hooks/pre-push. Git invokes it on every
# push regardless of which agent — or none — drives the shell, so it binds where a per-agent
# pre-execution hook cannot: an agent whose harness runs no such hook still gets this one. It
# is a guardrail, not a boundary — `--no-verify` skips it, and it sees git-CLI pushes only.
#
# NON-CLOBBERING by design (owner decision): write the wrapper only when no pre-push hook
# exists, never touch a hook this script did not write, and step aside entirely when the adopter
# manages hooks through core.hooksPath. Taking over someone else's pre-push lifecycle is a cost
# the rail is not worth. `.git/hooks` is untracked and a fresh clone starts without it, which is
# why this install lives in the boot sequence, not only in the one-time initializer. Every arm
# fails OPEN: a boot step that cannot write a hook must not stop the session (P14).
install_prepush_hook() {
	git rev-parse --git-dir >/dev/null 2>&1 || return 0
	if [ -n "$(git config --get core.hooksPath 2>/dev/null)" ]; then
		say "· pre-push rail: core.hooksPath is set, so AMH leaves your hooks alone. To keep the"
		say "  rail, chain \`scripts/command-guard.sh --pre-push\` into your own pre-push hook (P13)."
		return 0
	fi
	local hooks_dir hook
	hooks_dir=$(git rev-parse --git-path hooks 2>/dev/null)
	[ -n "$hooks_dir" ] || hooks_dir="$(git rev-parse --git-dir 2>/dev/null)/hooks"
	[ -n "$hooks_dir" ] || return 0
	# `--git-path` returns a path relative to cwd; cwd is $ROOT here, but anchor it explicitly
	# so a relative result is correct even if this ever runs from elsewhere (parity with amh-init).
	case $hooks_dir in /*) ;; *) hooks_dir="$ROOT/$hooks_dir" ;; esac
	hook="$hooks_dir/pre-push"
	if [ -e "$hook" ]; then
		grep -q 'AMH pre-push rail' "$hook" 2>/dev/null && return 0
		say "· pre-push rail: a pre-push hook already exists ($hook) — left untouched. To add the"
		say "  rail, chain \`scripts/command-guard.sh --pre-push\` into it (P13)."
		return 0
	fi
	mkdir -p -- "$hooks_dir" 2>/dev/null || return 0
	# The wrapper is written as literal text, so its `$root`/`$@` must NOT expand here.
	# shellcheck disable=SC2016
	{
		printf '%s\n' '#!/usr/bin/env bash' \
			'# AMH pre-push rail (P13) — installed by scripts/session-start.sh.' \
			'# Git runs this on every push, whatever agent (or none) drives the shell.' \
			'# A guardrail, not a boundary: --no-verify skips it. Delete this file to remove it.' \
			'root=$(git rev-parse --show-toplevel 2>/dev/null) || exit 0' \
			'[ -x "$root/scripts/command-guard.sh" ] || exit 0' \
			'exec bash "$root/scripts/command-guard.sh" --pre-push "$@"'
	} >"$hook" 2>/dev/null || return 0
	chmod +x -- "$hook" 2>/dev/null
	say "· pre-push rail: installed at $hook (git-native publication guard; --no-verify bypasses)."
}

say "── AMH session start ─────────────────────────────────────────"
for d in /tmp TMPDIR; do
	case "$ADVISORY_RESET_FAILED " in *" $d "*)
		say "· ⚠ could not clear the command guard's one-time advisory state under $d — what an"
		say "  earlier session spent or resumed there carries over: an advisory may never fire, and"
		say "  the advisory reports may count earlier sessions. Make it clearable, then re-run this script."
		;;
	esac
done

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

# 1b. Install the git-native pre-push rail (non-clobbering; see install_prepush_hook above).
install_prepush_hook

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
	say "  not a record of what happened inside a train. The state file and the ledger are (AMH P2)."
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
	printf '· %s: %s KB measured; compression trigger is %s KB\n' "$STATE_FILE" "$((bytes / 1024))" "$STATE_WARN_KB"
	if [ "$bytes" -gt "$warn_b" ]; then
		say "    ⚠ compression trigger crossed — run ONE deep compression pass; post-action ceilings require ≤ ${STATE_COMPRESS_TO_KB} KB AND ≤ ${STATE_COMPRESS_TO_SENTENCES} sentences before adding to it."
	fi
else
	say "· ⚠ $STATE_FILE is missing — working memory is where every session starts."
fi

# 3a. Newer working memory on top of this checkout.
#
# The state file is read first, so a checkout that is behind the work it records hands the
# session a stale plan and nothing says so. The shape that earned this line (AMH ledger row
# DD049): a unit finished in a second worktree and was pushed with `git push origin
# <local>:<remote>`, which moves the remote branch and leaves this checkout's branch where it
# was. The next session read the old state file, reported the finished unit as not started and
# built the wrong artifact for a device check.
#
# The test is ancestry plus the state file, and never this branch's name or its upstream's,
# which that shape spells differently. A ref or a detached worktree counts when HEAD is a strict
# ancestor of it AND a commit in between changes the state file — a bot never edits that file,
# and every unit does (the checkpoint invariant). Newest-commit-wins was refused: a bot's daily
# bump is always newer, and commit dates do not follow ancestry.
#
# ONE name is used, DEFAULT_BRANCH, and only to tell the two cases apart. A HEAD with commits of
# its own past the default branch is an ancestor only of work built on it, so every such ref
# is a continuation. A HEAD with none — a session freshly cut — is an ancestor of every branch
# cut since, and a sibling unit edits the state file exactly as a continuation does; which one
# carries a train is the owner's to say, never a guess the banner may make. So there it judges
# the default branch alone and says the rest was not judged.
#
# Refs as last fetched: this step does not fetch. A push updates the remote-tracking ref only
# where the remote's fetch refspec maps the pushed branch — a single-branch clone's does not.
# Every git call here runs with GIT_NO_LAZY_FETCH, so a partial clone reports that it could not
# count rather than downloading trees (measured on git 2.43; a git that predates the variable
# ignores it), and under `timeout` where one exists, so such a git or a huge history costs a
# bounded wait, not a stalled session.
# Missed, and said here: a continuation that was rebased, or cut from a newer commit than HEAD,
# does not descend from HEAD; a shallow clone cannot see a branch whose history was cut off, so
# its "nothing" is hedged. A detached worktree is named by its commit and never its path, which
# can carry a home directory, and one whose directory is gone (prunable) is not a place to read.
#
# Three outcomes, as with the release line: found, none, or could not look — and the last is
# never reported as none. An unborn HEAD prints nothing: no commit, so nothing can build on it.
wm_git() {
	if command -v timeout >/dev/null 2>&1; then
		GIT_NO_LAZY_FETCH=1 GIT_TERMINAL_PROMPT=0 timeout 10 git "$@"
	else
		GIT_NO_LAZY_FETCH=1 GIT_TERMINAL_PROMPT=0 git "$@"
	fi
}

wm_not_checked() { # <what could not be done>
	say "· working memory: $1 — NOT checked, which is not evidence either way."
}

report_newer_state() {
	local head base='' b label refs wts sha sym name n found=''
	if ! git rev-parse --git-dir >/dev/null 2>&1; then
		wm_not_checked "git could not read this repository"
		return 0
	fi
	head=$(git rev-parse -q --verify HEAD 2>/dev/null) || return 0
	for b in "refs/remotes/origin/$DEFAULT_BRANCH" "refs/heads/$DEFAULT_BRANCH"; do
		git rev-parse -q --verify "$b^{commit}" >/dev/null 2>&1 || continue
		base=$b
		break
	done
	if [ -n "$base" ] && wm_git merge-base --is-ancestor "$head" "$base" 2>/dev/null; then
		label=${base#refs/remotes/}
		label=${label#refs/heads/}
		if ! n=$(wm_git rev-list --count "$head..$base" -- "$STATE_FILE" 2>/dev/null); then
			wm_not_checked "could not count what $label adds to $STATE_FILE"
		elif [ "$n" -gt 0 ]; then
			say "· ⚠ working memory: $STATE_FILE here is OLDER than on $label, and this checkout has no"
			say "  commits of its own past it: $n newer commit(s) there change it. Bring this checkout up"
			say "  to it before acting on it. Refs as last fetched."
		else
			say "· working memory: $label has no newer $STATE_FILE (refs as last fetched). This checkout"
			say "  has no commits of its own, so branches cut from it are not judged: a sibling unit"
			say "  cannot be told from a continuation."
		fi
		return 0
	fi
	# `:` separates the fields because a refname cannot contain one and an empty %(symref) must
	# survive the split; IFS whitespace would collapse it. Symbolic refs (origin/HEAD) are aliases
	# of a ref already listed.
	if ! refs=$(wm_git for-each-ref --contains "$head" \
		--format='%(objectname):%(symref):%(refname:short)' refs/heads refs/remotes 2>/dev/null) ||
		! wts=$(wm_git worktree list --porcelain 2>/dev/null); then
		wm_not_checked "could not list what builds on this checkout"
		return 0
	fi
	# A worktree on a branch is already a ref above; only a detached one, still on disk, needs
	# its own entry. Porcelain blocks end at a blank line.
	wts=$(printf '%s\n\n' "$wts" | awk '
		/^HEAD / { h = $2 } /^detached$/ { d = 1 } /^prunable/ { p = 1 }
		/^$/ { if (d && !p && h != "") print h; h = ""; d = 0; p = 0 }')
	while IFS=: read -r sha sym name; do
		{ [ -n "$sha" ] && [ "$sha" != "$head" ] && [ -z "$sym" ]; } || continue
		found="$found$sha $name"$'\n'
	done <<<"$refs"
	while IFS= read -r sha; do
		{ [ -n "$sha" ] && [ "$sha" != "$head" ]; } || continue
		wm_git merge-base --is-ancestor "$head" "$sha" 2>/dev/null || continue
		found="${found}$sha a detached worktree"$'\n'
	done <<<"$wts"
	# One line per commit with every name for it, kept when its descent from HEAD changes the
	# state file. A count that failed is marked, never read as zero.
	found=$(printf '%s' "$found" | sort | awk '
		$1 != s { if (s != "") print s, names; s = $1; names = ""; }
		{ sub(/^[^ ]+ /, ""); names = (names == "" ? $0 : names ", " $0) }
		END { if (s != "") print s, names }' |
		while read -r sha name; do
			n=$(wm_git rev-list --count "$head..$sha" -- "$STATE_FILE" 2>/dev/null) || n=x
			[ "$n" = 0 ] || printf '%s %s %s\n' "$n" "$sha" "$name"
		done)
	# A `case` match, not `printf | grep -q`: with a failed count early in a long enough list the
	# early-exiting reader under `pipefail` read it as absent and printed `x` as a count. `case`
	# needs no pipe, and no temporary file, which a here-string past the pipe buffer does and a
	# sandbox may not allow (AMH ledger row DD110).
	if case $'\n'"$found" in *$'\n''x '*) true ;; *) false ;; esac; then
		wm_not_checked "could not count the commits that build on this checkout"
	elif [ -n "$found" ]; then
		say "· ⚠ working memory: $STATE_FILE here is OLDER than on work that builds on this checkout:"
		printf '%s\n' "$found" | while read -r n sha name; do
			say "    $(git rev-parse --short "$sha" 2>/dev/null || printf '%s' "$sha") ($name): $n newer commit(s) change it"
		done
		say "  Read it there, or bring this checkout up to it, before acting on it. Refs as last fetched."
	elif [ -f "$(git rev-parse --git-path shallow 2>/dev/null)" ]; then
		wm_not_checked "nothing found, but this clone is shallow and cannot see a branch whose history was cut off"
	else
		say "· working memory: nothing that builds on this checkout changes $STATE_FILE (refs as last fetched)."
	fi
	return 0
}

report_newer_state

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
# The runbook clause is conditional on the file EXISTING. Every install profile ships one, so a
# tree without it is an incomplete install or a legacy one — an older light profile went without,
# and its adopters were told to fold the playbooks into the constitution. Pointing a session at a
# document that is not there teaches it to distrust the banner, and the adopter cannot fix this
# line themselves: this script is overwritten on every upgrade by design.
if [ -f "$ROOT/docs/RUNBOOK.md" ]; then
	say "· protocol: read $STATE_FILE first (incl. the Owner queue), then the matching"
	say "  playbook in docs/RUNBOOK.md. Verify with scripts/ladder.sh. Never leave the branch red."
else
	say "· protocol: read $STATE_FILE first (incl. the Owner queue), then the constitution's"
	say "  playbooks. Verify with scripts/ladder.sh. Never leave the branch red."
fi
say "──────────────────────────────────────────────────────────────"
exit 0
