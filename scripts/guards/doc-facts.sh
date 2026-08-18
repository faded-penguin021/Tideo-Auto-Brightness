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
	printf 'doc-fact drift: %s file(s) reference ShizukuShell but the docs claim exactly %s runtime places — update the claim in the constitution (AGENTS.md) and its restatement in README.md, the d66de4c drift site, AND this guard'"'"'s constant in lockstep (DA-015)\n' \
		"$shizuku_sites" "$shizuku_expected" >&2
	fails=$((fails + 1))
fi

# Fact (drift incident 3949383): the constitution's "a sub-item is cited as `D-042(c)`" — the
# sub-item letter is parenthesised, never appended bare to the row number. The ladder matches
# citations as whole words (AMH 4.0.0), so a bare-suffixed id resolves to NOTHING: the citation
# rung stays green while the row loses its only pointer and its [cited] marker reads as stale.
# That is not a failure the ladder can see, which is exactly why the rule needs an anchor here.
# The forbidden spelling is deliberately not written out anywhere in this file — this guard
# scans its own directory, and an illustrative example would fail it (the same trap that caught
# an illustrative row header in a guard comment at 4698ce9).
# Scope mirrors CITATION_SCAN_PATHS minus CITATION_EXCLUDE.
suffixed=$(grep -rnE '\bD[A-Z]*-[0-9]+[a-z]\b' app domain platform .github scripts 2>/dev/null |
	grep -v '^scripts/test-ladder-guards\.sh:' | grep -v '^scripts/tests/')
if [ -n "$suffixed" ]; then
	printf 'doc-fact drift: suffixed sub-item citation(s) found — the ladder matches citations as whole words, so these resolve to no ledger row and silently drop the row from the [cited] accounting. Write them as D-042(c) (DB-022):\n%s\n' \
		"$suffixed" >&2
	fails=$((fails + 1))
fi

# Fact (drift incident DB-019): the constitution states a numeric AMH version and amh.conf's
# AMH_VERSION is the authority. Nothing upstream checks the pair, and DB-019 is the record of
# them drifting apart across an upgrade — the constitution carried no version at all while
# amh.conf said 3.0.0.
conf_version=$(sed -n 's/^AMH_VERSION=\(.*\)$/\1/p' amh.conf | head -1)
doc_version=$(sed -n 's/.*[Tt]his constitution records \*\*AMH \([0-9][0-9.]*\)\*\*.*/\1/p' AGENTS.md | head -1)
if [ -z "$doc_version" ]; then
	printf 'doc-fact drift: AGENTS.md states no "This constitution records **AMH <version>**" line, but the upgrade contract requires one (docs/UPGRADING.md step 7, DB-019)\n' >&2
	fails=$((fails + 1))
elif [ "$doc_version" != "$conf_version" ]; then
	printf 'doc-fact drift: AGENTS.md records AMH %s but amh.conf sets AMH_VERSION=%s — amh.conf is the authority; move the two together (DB-019)\n' \
		"$doc_version" "$conf_version" >&2
	fails=$((fails + 1))
fi

[ "$fails" = 0 ] || exit 1
printf 'Shizuku runtime sites = %s; sub-item citations parenthesised; constitution records AMH %s = AMH_VERSION\n' \
	"$shizuku_sites" "$doc_version"
