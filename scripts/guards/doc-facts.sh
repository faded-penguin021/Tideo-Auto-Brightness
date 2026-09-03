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

# Fact (drift incident 4e22273): an upgrade has two halves — a file copy of the shipped scripts,
# and the hand-applied seed prose that rewrites binding rules across RULE_FILES — and they can
# land in separate commits. That commit shipped AMH_VERSION=14.0.0 while AGENTS.md, the RUNBOOK
# and STATE still carried 9.1.0-era rules. Nothing could see it: the version-pair fact above
# passes precisely BECAUSE both surfaces agree on the new number, so the only anchor in this area
# affirmed the pairing. The disclosure lived in docs/STATE.md alone — compressible working memory,
# which a later compression pass is free to drop — while the claim it qualifies sat in amh.conf,
# permanent and guard-checked. This anchor moves the disclosure to the same side as the claim.
#
# AMH_PROSE_VERSION (ours) names the version whose binding prose the tree actually follows.
# Absent, or equal to AMH_VERSION, nothing is owed and this is silent. Different, and AGENTS.md
# MUST disclose the gap — that requirement is the enforcement; the warning is what keeps the debt
# audible on every run until it is paid. The warn tier is deliberate: the split is a legitimate
# owner-directed state, and failing closed would hold the branch red for as long as the owner
# wants it to stand, which is how a rule gets deleted rather than obeyed (DC-031).
prose_version=$(sed -n 's/^AMH_PROSE_VERSION=\(.*\)$/\1/p' amh.conf | head -1)
prose_owed=''
if [ -n "$prose_version" ] && [ -n "$conf_version" ] && [ "$prose_version" != "$conf_version" ]; then
	if grep -qF "binding prose is AMH $prose_version" AGENTS.md; then
		prose_owed="seed prose owed — amh.conf runs AMH $conf_version but the binding prose is AMH $prose_version. Read harness/CHANGELOG.md forward from $prose_version, NOT from AMH_VERSION (docs/HARNESS_LOCAL.md \"Upgrading\"), and set AMH_PROSE_VERSION=$conf_version in the commit that lands it."
	else
		printf 'doc-fact drift: amh.conf sets AMH_PROSE_VERSION=%s against AMH_VERSION=%s, so seed prose is owed — but AGENTS.md carries no "binding prose is AMH %s" disclosure. The constitution then claims AMH %s with nothing saying which of its rules that version does not yet describe: false by omission, in the permanent tier (4e22273, DC-031)\n' \
			"$prose_version" "$conf_version" "$prose_version" "$conf_version" >&2
		fails=$((fails + 1))
	fi
fi

[ "$fails" = 0 ] || exit 1

summary="Shizuku runtime sites = $shizuku_sites; sub-item citations parenthesised; constitution records AMH $doc_version = AMH_VERSION"

# The ladder reads the MERGED stream and takes `WARN ` only at its very start, so nothing —
# including the summary — may print ahead of it.
if [ -n "$prose_owed" ]; then
	printf 'WARN %s\n' "$prose_owed"
	printf '%s\n' "$summary"
	exit 2
fi
printf '%s\n' "$summary"
