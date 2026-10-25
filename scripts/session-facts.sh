#!/usr/bin/env bash
# Repo-local session add-on: prints release facts docs/STATE.md may no longer cache (DC-030).
# Rationale, wiring and limits: docs/HARNESS_LOCAL.md "scripts/session-facts.sh".
# Always exits 0 — a bootstrap must never block a session.

set -uo pipefail

ROOT=$(git rev-parse --show-toplevel 2>/dev/null) || exit 0
cd "$ROOT" 2>/dev/null || exit 0

say() { printf '%s\n' "$*"; }

GRADLE=app/build.gradle.kts
[ -f "$GRADLE" ] || exit 0

version=$(sed -n 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' "$GRADLE" | head -1)
code=$(sed -n 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*\([0-9][0-9]*\).*/\1/p' "$GRADLE" | head -1)

# The ahead/behind line used to hardcode `origin/main` — the one value in this script that
# amh.conf already parameterises. Parsed, not sourced: this needs one key out of 29, and
# sourcing would let an unrelated assignment shadow a variable here.
default_branch=$(sed -n 's/^DEFAULT_BRANCH=\(.*\)$/\1/p' amh.conf 2>/dev/null | head -1)
default_branch=${default_branch:-main}

say ''
say '── live facts (computed now; STATE.md deliberately caches none of this) ──'

if [ -n "$version" ] && [ -n "$code" ]; then
	say "· tree: app/build.gradle.kts says $version / vc$code"
else
	say '· ⚠ could not read versionName/versionCode from app/build.gradle.kts'
fi

# Remote tags. GIT_TERMINAL_PROMPT=0 keeps a credential prompt from hanging a session with no
# network. Without `timeout` the probe is SKIPPED rather than run unbounded: the promise this hook
# makes is that it cannot stall a session, and an unbounded network call cannot keep that promise.
# Reachability is judged by exit status ALONE — an empty result from a reachable origin means "no
# tags yet", which is a different fact and must not be reported as unreachable.
remote_tags=''
probe_failed=0
if command -v timeout >/dev/null 2>&1; then
	remote_tags=$(GIT_TERMINAL_PROMPT=0 timeout 15 git ls-remote --tags --refs origin 'refs/tags/v*' 2>/dev/null) || probe_failed=1
else
	probe_failed=2
fi

if [ "$probe_failed" = 2 ]; then
	say '· ⚠ no `timeout` available, so the tag probe was SKIPPED — release status UNKNOWN.'
	say '    Settle it with: git ls-remote --tags --refs origin "refs/tags/v*"'
elif [ "$probe_failed" = 1 ]; then
	say '· ⚠ could not reach origin for tags — release status UNKNOWN this session.'
	say '    Settle it with: git ls-remote --tags --refs origin "refs/tags/v*"'
else
	newest=$(printf '%s\n' "$remote_tags" | awk '
		{ ref = $2; sub(/^refs\/tags\/v/, "", ref)
		  if (ref !~ /^[0-9]+\.[0-9]+\.[0-9]+$/) next
		  split(ref, p, ".")
		  if (!seen || p[1] > a || (p[1] == a && p[2] > b) || (p[1] == a && p[2] == b && p[3] > c)) {
		    seen = 1; a = p[1]; b = p[2]; c = p[3]; best = ref } }
		END { if (seen) print best }')

	if [ -n "$newest" ]; then
		say "· origin: newest release tag is v$newest (plain vX.Y.Z only — pre-release tags are not ranked)"
	else
		say '· origin: reachable, and no vX.Y.Z tag exists yet'
	fi

	if [ -n "$version" ]; then
		# Exact ref comparison, not a grep pattern: a version's dots are regex wildcards.
		if printf '%s\n' "$remote_tags" | awk -v t="refs/tags/v$version" '$2 == t { hit = 1 } END { exit !hit }'; then
			say "· this tree's $version is ALREADY TAGGED on origin — a bump is owed before release work"
		else
			say "· this tree's $version is UNRELEASED (no tag v$version on origin)"
		fi
	fi
fi

# Fetch first, bounded like the tag probe, so the counts below are live rather than "as last
# fetched" — a checkout left behind its own pushed work cost sessions their first turns.
fetched='as last fetched'
if [ "$probe_failed" = 0 ] && GIT_TERMINAL_PROMPT=0 timeout 20 git fetch --quiet origin >/dev/null 2>&1; then
	fetched='fetched now'
fi

branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null)
base=origin/$default_branch
if [ -n "$branch" ] && git rev-parse --verify --quiet "$base" >/dev/null 2>&1; then
	ahead=$(git rev-list --count "$base..HEAD" 2>/dev/null)
	behind=$(git rev-list --count "HEAD..$base" 2>/dev/null)
	if [ -n "$ahead" ] && [ -n "$behind" ]; then
		say "· branch $branch: $ahead ahead / $behind behind $base ($fetched; a shallow clone undercounts)"
	fi
fi

# Any branch HEAD can fast-forward to, whatever its name: work pushed from a worktree under a
# different local branch is the case this exists for. Ranked by commit count, one ref per tip
# (origin's name preferred). A tip whose extra commits are all bot-authored (Dependabot cuts from
# main) is not work left behind. On the default branch only its origin counts, since every session
# branch cut from it would otherwise qualify.
head_sha=$(git rev-parse --verify --quiet HEAD 2>/dev/null)
if [ -n "$head_sha" ]; then
	refs='refs/remotes/origin refs/heads'
	[ "$branch" = "$default_branch" ] && refs="refs/remotes/$base"
	# shellcheck disable=SC2086 # $refs is a list of ref patterns
	ff=$(git for-each-ref --contains HEAD --format='%(objectname) %(refname:short)' $refs 2>/dev/null |
		while read -r sha ref; do
			[ "$sha" = "$head_sha" ] && continue
			case "$ref" in origin/HEAD | origin) continue ;; origin/*) prio=0 ;; *) prio=1 ;; esac
			# shellcheck disable=SC2143 # under pipefail an early-exiting `grep -q` SIGPIPEs a long log
			[ -n "$(git log --format=%ae "HEAD..$sha" 2>/dev/null | grep -vF '[bot]@')" ] || continue
			printf '%s %s %s %s\n' "$(git rev-list --count "HEAD..$sha")" "$sha" "$prio" "$ref"
		done | sort -k2,2 -k3,3n | awk '!seen[$2]++' | sort -k1,1nr | head -3)
	if [ -n "$ff" ]; then
		say "· ⚠ HEAD is BEHIND newer work — fast-forward before reading STATE or the plans:"
		printf '%s\n' "$ff" | while read -r n _ prio ref; do
			where=''
			[ "$prio" = 1 ] && where=' (local only)'
			# %q: a branch name is external data and may carry shell syntax.
			say "    $ref$where is $n commit(s) ahead: git merge --ff-only $(printf '%q' "$ref")"
		done
	fi
fi

say '───────────────────────────────────────────────────────────────────────'
exit 0
