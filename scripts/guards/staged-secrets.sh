#!/usr/bin/env bash
# Repo-local ladder guard — secret-shape scan over STAGED BLOBS (was the second half of ladder
# guard 9; DA-008).
#
# The shipped ladder's guard_secret_shapes scans the WORKING TREE. That misses the
# staged-then-reverted secret: a credential added, `git add`ed, then edited out of the file on
# disk is still what `git commit` would record. The index is what a commit records, so the
# index is what this scans.
#
# The scan IS the redaction filter, exactly as the shipped one is: "secret-shaped" means
# "redacting it would change it", so the two can never drift apart into a scan that recognises
# a different set of shapes than the filter does.
#
# Output is VALUE-FREE — file and byte position only, never the match. A diagnostic that
# printed the matched line would defeat the guard entirely.
#
# Known limits, both carried by the server-side push-protection layer (DA-006/DA-008): binary
# blobs are skipped (a NUL byte defeats a sed-based filter), and a regex catches only known
# shapes — the constitution's prose rule still binds for everything else.
set -uo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT" || exit 1

git rev-parse --git-dir >/dev/null 2>&1 || {
	printf 'not a git work tree — the staged-blob scan checked nothing\n' >&2
	exit 1
}

[ -f scripts/redact.sh ] || {
	printf 'scripts/redact.sh is missing — it IS the filter this scan runs, so its absence is a failure, not a skip\n' >&2
	exit 1
}

# Positive control before the scan's silence is allowed to mean anything: an empty, truncating
# or pass-through filter reports every blob clean. The token is generated at RUNTIME — a stored
# literal would make this file fail the tree scan it belongs to (DA-008).
canary=''
while [ "${#canary}" -lt 16 ]; do
	canary=$canary$(head -c 512 /dev/urandom | LC_ALL=C tr -dc 'A-Z0-9')
done
canary="AKIA${canary:0:16}"
if printf 'x %s x\n' "$canary" | bash scripts/redact.sh 2>/dev/null | grep -qF "$canary"; then
	printf 'scripts/redact.sh did not redact a generated test token — the filter is empty, broken or pass-through, and this scan would report green on everything\n' >&2
	exit 1
fi

# Staged paths, NUL-separated: a word-split list silently skips names with spaces or non-ASCII
# characters, and a scan with a silent hole is worse than no scan.
list=$(mktemp)
tmpblob=$(mktemp)
cmperr=$(mktemp)
trap 'rm -f "$list" "$tmpblob" "$cmperr"' EXIT

if git rev-parse --verify -q HEAD >/dev/null 2>&1; then
	git diff --cached --name-only --diff-filter=ACMR -z >"$list"
else
	git diff --cached --name-only --diff-filter=ACMR -z --root >"$list"
fi

hits=0
scanned=0
while IFS= read -r -d '' f; do
	git show ":$f" >"$tmpblob" 2>/dev/null || continue
	LC_ALL=C grep -qI . "$tmpblob" 2>/dev/null || continue # text blobs only
	scanned=$((scanned + 1))
	# cmp's stderr carries the truncation verdict (`EOF on -`) while stdout carries the
	# difference verdict. Discarding stderr makes a filter that stopped mid-stream
	# indistinguishable from a clean blob.
	pos=$(bash scripts/redact.sh <"$tmpblob" 2>/dev/null | cmp - "$tmpblob" 2>"$cmperr")
	if [ -s "$cmperr" ]; then
		printf 'scripts/redact.sh did not filter all of staged %s (%s) — a truncated stream reads as clean\n' \
			"$f" "$(tr -d '\n' <"$cmperr")" >&2
		hits=$((hits + 1))
		continue
	fi
	if [ -n "$pos" ]; then
		printf 'credential-shaped string in the STAGED blob of %s (%s) — unstage and purge it before committing; the index is what a commit records (DA-008)\n' \
			"$f" "${pos#*differ: }" >&2
		hits=$((hits + 1))
	fi
done <"$list"

[ "$hits" = 0 ] || exit 1
printf '%s staged text blob(s) clean\n' "$scanned"
