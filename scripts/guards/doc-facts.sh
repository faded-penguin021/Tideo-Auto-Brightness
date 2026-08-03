#!/usr/bin/env bash
# Repo-local ladder guard — falsifiable doc-facts (was ladder guard 11; DA-015).
#
# A load-bearing prose claim earns a machine anchor only AFTER a real drift incident. That
# incident-only bar is BINDING and is what keeps this from becoming a doc-testing framework:
# a fact proposed without one was reviewed out (DA-015). Adding a row here means naming the
# commit where the prose went stale.
#
# Each expected value is a CONSTANT here, in lockstep with the cited doc sentence. The guard
# checks CODE against the constant and never parses prose — changing either side means
# changing both, and the ladder's rule-file tripwire fires on this file so the rule-review
# protocol applies when it does.
#
# These are TRIPWIRES, not proofs. The Shizuku check counts files naming ShizukuShell, not
# semantic call sites; it catches "someone added a third runtime site and left the docs saying
# two", which is the drift that actually happened.
set -uo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT" || exit 1

fails=0

# Fact (drift incident d66de4c): the constitution's "Shizuku is a genuine optional runtime
# dependency in exactly two places", restated in README.md and
# docs/rebuild/architecture/privilege_tiers.md — the three sites d66de4c left disagreeing.
# Consumer files referencing ShizukuShell, excluding its own definition.
shizuku_expected=2
shizuku_sites=$(grep -rl 'ShizukuShell' platform/src/main app/src/main 2>/dev/null |
	grep -cv '/ShizukuShell\.kt$')
if [ "${shizuku_sites:-0}" != "$shizuku_expected" ]; then
	printf 'doc-fact drift: %s file(s) reference ShizukuShell but the docs claim exactly %s runtime places — update the claim in the constitution (CLAUDE.md today; AGENTS.md once the pointer swap lands) and its restatements in README.md, the d66de4c drift site, AND this guard'"'"'s constant in lockstep (DA-015)\n' \
		"$shizuku_sites" "$shizuku_expected" >&2
	fails=$((fails + 1))
fi

[ "$fails" = 0 ] || exit 1
printf 'Shizuku runtime sites = %s\n' "$shizuku_sites"
