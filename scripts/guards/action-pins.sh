#!/usr/bin/env bash
# Repo-local ladder guard — GitHub Actions pin and version-marker agreement (DB-076).
#
# RUNBOOK playbook 8 carries two Scorecard-derived rails and one hand step. This guard mechanises
# the parts that are decidable offline:
#
#   1. Pinned-Dependencies — every remote `uses:` is a 40-hex commit SHA, never a tag ref. A moved
#      tag changes what runs, which is the whole point of the rail.
#   2. Every pinned line carries a `# vX.Y.Z` marker. The SHA is what runs; the marker is the only
#      thing a human reads, so an unlabelled pin is unreviewable.
#   3. The markers agree with each other. One SHA never carries two different version markers, and
#      one action+version never resolves to two different SHAs.
#
# WHAT IT CANNOT CHECK, and why the RUNBOOK step stays: whether a marker names the tag the SHA
# actually belongs to. That needs the network (`git ls-remote`), and the ladder is offline and
# deterministic. So a pin whose marker is wrong EVERYWHERE it appears passes here — playbook 8
# step 2 (resolve the tag on GitHub by hand) is still the only layer that catches it.
#
# Rule 3 is not a consolation prize. DB-038's decay is Dependabot rewriting a SHA while its marker
# rewrite silently fails on lines with trailing prose after the version — which leaves the same SHA
# labelled two ways across the tree, exactly the shape rule 3 sees. That defect shipped to a PR
# twice (v1.9.1 review; `clean-dist.yml` still carries the trailing prose that caused it) and was
# both times found by eye. Fail-closed: every case it fires on is wrong.
set -uo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT" || exit 1

FAILS=0
fail() {
	printf '%s\n' "$1" >&2
	FAILS=$((FAILS + 1))
}

shopt -s nullglob
files=(.github/workflows/*.yml .github/workflows/*.yaml)
if [ "${#files[@]}" -eq 0 ]; then
	# Not a skip. This guard's subject is the workflow set; finding none means it checked
	# nothing while printing a pass, which is the state that must be loudest.
	printf 'no workflows under .github/workflows/ — this guard checked nothing\n' >&2
	exit 1
fi

pins=$(mktemp)
trap 'rm -f "$pins"' EXIT

checked=0
while IFS= read -r record; do
	[ -n "$record" ] || continue
	if [[ ! $record =~ ^([^:]+):([0-9]+):[[:space:]]*-?[[:space:]]*uses:[[:space:]]*([^[:space:]]+)[[:space:]]*(#[[:space:]]*(.*))?$ ]]; then
		continue
	fi
	loc="${BASH_REMATCH[1]}:${BASH_REMATCH[2]}"
	ref="${BASH_REMATCH[3]}"
	comment="${BASH_REMATCH[5]:-}"

	# A path in this repository and a container image are not third-party supply chain.
	case $ref in
	./* | docker://*) continue ;;
	esac
	checked=$((checked + 1))

	if [[ $ref != *@* ]]; then
		fail "$loc: 'uses: $ref' has no ref at all — pin it to a 40-hex commit SHA (RUNBOOK playbook 8)."
		continue
	fi
	action="${ref%@*}"
	sha="${ref##*@}"
	if [[ ! $sha =~ ^[0-9a-f]{40}$ ]]; then
		fail "$loc: '$action@$sha' is a tag or branch ref, not a 40-hex commit SHA. A moved tag changes what runs; re-pin it and label it '# v<version>' (RUNBOOK playbook 8, Pinned-Dependencies)."
		continue
	fi
	if [[ ! $comment =~ ^(v[0-9][0-9A-Za-z.+-]*) ]]; then
		fail "$loc: '$action' is pinned to $sha with no '# v<version>' marker. The SHA is what runs and the marker is what a reviewer reads; an unlabelled pin cannot be reviewed."
		continue
	fi
	# Trailing prose after the version is legal here and is exactly what breaks Dependabot's
	# marker rewrite, so only the leading version token is the marker.
	marker="${BASH_REMATCH[1]}"
	printf '%s\t%s\t%s\t%s\n' "$sha" "$marker" "$action" "$loc" >>"$pins"
done < <(grep -HnE '^[[:space:]]*-?[[:space:]]*uses:[[:space:]]*[^[:space:]]' "${files[@]}" 2>/dev/null)
# -H above, not -n alone: grep omits the filename prefix when handed exactly one file, and a
# repository down to its last workflow is the case that would otherwise parse to nothing.

if [ "$checked" -eq 0 ]; then
	printf 'no third-party "uses:" lines found in %d workflow file(s) — this guard checked nothing\n' \
		"${#files[@]}" >&2
	exit 1
fi

# Rule 3a: one SHA, one marker. Two labels for one commit means at least one label is a lie.
while IFS= read -r sha; do
	[ -n "$sha" ] || continue
	detail=$(awk -F'\t' -v s="$sha" '$1 == s { printf "%s says %s; ", $4, $2 }' "$pins")
	fail "$sha is labelled with more than one version across the workflows — ${detail%; }. Dependabot's marker rewrite fails on lines with trailing prose after the version (DB-038), so resolve the tag on GitHub and correct the stale marker; do not just make them match."
done < <(cut -f1,2 "$pins" | sort -u | cut -f1 | uniq -d)

# Rule 3b: one action+version, one SHA. The same release cannot be two commits.
while IFS= read -r key; do
	[ -n "$key" ] || continue
	action="${key%@*}"
	marker="${key##*@}"
	detail=$(awk -F'\t' -v a="$action" -v m="$marker" '$3 == a && $2 == m { printf "%s pins %s; ", $4, $1 }' "$pins")
	fail "$action $marker resolves to more than one commit across the workflows — ${detail%; }. One release is one commit; resolve the tag on GitHub and correct the wrong pin."
done < <(awk -F'\t' '{ print $3 "@" $2 "\t" $1 }' "$pins" | sort -u | cut -f1 | uniq -d)

if [ "$FAILS" -gt 0 ]; then
	exit 1
fi
printf '%d pinned action reference(s) across %d workflow(s): all SHA-pinned, all labelled, markers self-consistent (tag↔SHA agreement is playbook 8 step 2, by hand)\n' \
	"$checked" "${#files[@]}"
