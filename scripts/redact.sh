#!/bin/bash
# Agent-neutral secret-redaction filter (DA-007). stdin -> stdout; replaces KNOWN credential
# token shapes with [REDACTED:<class>] so terminal output can be filtered BEFORE an agent's
# context window sees it (defense-in-depth for the D-175/P17 prose discipline — a regex only
# catches known shapes; the prose rule still binds for everything else).
#
# Adapters pipe tool/terminal output through this where their harness supports an
# output-filter hook (see CLAUDE.md "Secret hygiene"). Also usable manually:
#   some-command 2>&1 | scripts/redact.sh
#
# Patterns are deliberately prefix-anchored token formats (low false-positive rate); do NOT
# add generic high-entropy matching here — it mangles ordinary build output. POSIX/BSD-safe:
# no GNU-only sed flags. An UNTERMINATED private-key block redacts to end-of-input by design
# (fail-closed: over-redaction beats a leaked key half).
#
# --self-test generates format-valid FAKE tokens at runtime (never stored in the repo: a
# committed format-valid string would trip push-protection secret scanners — including this
# script's own test fixtures, which is why every one is assembled at runtime). The ladder
# runs it as guard 8 — a silent regex regression must fail the build, not pass quietly.
#
# --scan FILE... (DA-008, ladder guard 9): exits 1 if any text file contains secret-shaped
# content, defined as "redacting the file would change it" — the scan IS the filter, so the
# two can never drift. --scan-staged applies the same test to staged blobs that differ from
# HEAD (the content `git commit` would actually record — a worktree scan alone misses a
# staged-then-reverted secret). Output is value-free (file + diff hunk positions, never the
# match). Known limits, both covered by the push-protection layer (DA-006/DA-008): binary
# files are skipped (a NUL byte defeats a sed-based scan); under BSD sed a text file with no
# trailing newline may FALSE-POSITIVE (BSD sed appends one; GNU sed preserves) — safe
# direction, add the newline.
set -euo pipefail

redact() {
  sed -E \
    -e 's/gh[pousr]_[A-Za-z0-9]{20,}/[REDACTED:github-token]/g' \
    -e 's/github_pat_[A-Za-z0-9_]{20,}/[REDACTED:github-token]/g' \
    -e 's/glpat-[A-Za-z0-9_-]{20,}/[REDACTED:gitlab-token]/g' \
    -e 's/xox[baeprs]-[A-Za-z0-9-]{10,}/[REDACTED:slack-token]/g' \
    -e 's/AKIA[0-9A-Z]{16}/[REDACTED:aws-key-id]/g' \
    -e 's/sk-ant-[A-Za-z0-9_-]{20,}/[REDACTED:anthropic-key]/g' \
    -e 's/sk-[A-Za-z0-9_-]{20,}/[REDACTED:api-key]/g' \
    -e 's/AIza[0-9A-Za-z_-]{35}/[REDACTED:google-api-key]/g' \
    -e 's/npm_[A-Za-z0-9]{36}/[REDACTED:npm-token]/g' \
    -e 's/eyJ[A-Za-z0-9_-]{10,}\.eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}/[REDACTED:jwt]/g' \
    -e 's/([Aa]uthorization[[:space:]]*:[[:space:]]*[Bb]earer)[[:space:]]+[A-Za-z0-9._~+\/-]{8,}=*/\1 [REDACTED:bearer]/g' \
    -e '/-----BEGIN [A-Z ]*PRIVATE KEY-----/,/-----END [A-Z ]*PRIVATE KEY-----/s/.*/[REDACTED:private-key]/'
}

if [ "${1:-}" = "--scan" ]; then
  shift; hits=0
  tmp=$(mktemp); trap 'rm -f "$tmp"' EXIT
  for f in "$@"; do
    [ -f "$f" ] || continue
    grep -Iq . "$f" 2>/dev/null || continue      # binary → skip (see header limits)
    redact <"$f" >"$tmp"
    if ! cmp -s "$f" "$tmp"; then
      # NcN hunk positions only — the matched value must never be printed (D-175).
      echo "SECRET-SHAPED: $f (at diff position(s): $(diff "$f" "$tmp" | grep -E '^[0-9]' | cut -d'c' -f1 | tr '\n' ' '))" >&2
      hits=1
    fi
  done
  exit "$hits"
elif [ "${1:-}" = "--scan-staged" ]; then
  # Scan the INDEX copies of files staged with changes vs HEAD — what a commit would record.
  hits=0
  src=$(mktemp); tmp=$(mktemp); trap 'rm -f "$src" "$tmp"' EXIT
  while IFS= read -r -d '' f; do
    git show ":$f" > "$src" 2>/dev/null || continue
    grep -Iq . "$src" || continue                # binary → skip
    redact <"$src" >"$tmp"
    if ! cmp -s "$src" "$tmp"; then
      echo "SECRET-SHAPED (staged blob): $f (at diff position(s): $(diff "$src" "$tmp" | grep -E '^[0-9]' | cut -d'c' -f1 | tr '\n' ' '))" >&2
      hits=1
    fi
  done < <(git diff --cached --name-only -z --diff-filter=d 2>/dev/null)
  exit "$hits"
elif [ "${1:-}" = "--self-test" ]; then
  pad() { printf "x%.0s" $(seq 1 "$1"); }
  fails=0
  check() {  # $1 = label, $2 = input line, $3 = raw secret fragment: output must contain
             # REDACTED and must NOT contain the raw fragment (partial redaction fails too)
    local out; out=$(printf '%s\n' "$2" | redact)
    if ! grep -q 'REDACTED' <<<"$out" || grep -qF "$3" <<<"$out"; then
      echo "SELF-TEST FAIL: $1 not fully redacted" >&2; fails=$((fails+1))
    fi
  }
  keep() {  # $1 = label, $2 = line that must pass through byte-identical (near-miss guard)
    [ "$(printf '%s\n' "$2" | redact)" = "$2" ] \
      || { echo "SELF-TEST FAIL: $1 was altered (over-eager pattern)" >&2; fails=$((fails+1)); }
  }
  t="ghp_$(pad 36)";              check github    "token $t leaked" "$t"
  t="github_pat_$(pad 22)";      check gh-pat    "$t" "$t"
  t="glpat-$(pad 20)";           check gitlab    "$t" "$t"
  t="xoxb-$(pad 10)-$(pad 12)"; check slack "$t" "$t"
  t="AKIA$(printf 'A%.0s' $(seq 1 16))"; check aws "$t" "$t"
  t="sk-ant-$(pad 24)";          check anthropic "$t" "$t"
  t="sk-$(pad 24)";              check openai    "$t" "$t"
  t="AIza$(pad 35)";             check google    "$t" "$t"
  t="npm_$(pad 36)";             check npm       "$t" "$t"
  t="eyJ$(pad 12).eyJ$(pad 12).$(pad 12)"; check jwt "$t" "$t"
  t="abc123def456ghi789"
  check bearer    "Authorization: Bearer $t" "$t"
  check bearer-lc "authorization: bearer $t" "$t"   # HTTP/2 lowercase (curl -v prints this)
  b="-----BEGIN"; check pem "$b RSA PRIVATE KEY-----" "BEGIN RSA"   # header assembled at
                                                    # runtime: the literal would trip --scan
  # Near-miss negative controls: correct prefixes that must NOT be redacted.
  keep plain        "BUILD SUCCESSFUL in 4m 12s; 98 tests, sk8er_boi, github.com/foo"
  keep short-tokens "sk-short ghp_tooshort glpat-tiny npm_abc"
  keep bearer-prose "Authorization: Bearer tokens are required for this endpoint"
  # --scan round-trip: a file holding a runtime token must scan dirty; a clean one, clean;
  # and the scan's diagnostic output must be VALUE-FREE (the raw token never appears, D-175).
  st=$(mktemp); trap 'rm -f "$st"' EXIT
  stok="ghp_$(pad 36)"
  printf 'log line with %s inside\n' "$stok" > "$st"
  scan_out=$("$0" --scan "$st" 2>&1) && { echo "SELF-TEST FAIL: --scan missed a token file" >&2; fails=$((fails+1)); }
  grep -qF "$stok" <<<"$scan_out" && { echo "SELF-TEST FAIL: --scan printed the secret value" >&2; fails=$((fails+1)); }
  printf 'BUILD SUCCESSFUL\n' > "$st"
  "$0" --scan "$st" >/dev/null 2>&1 || { echo "SELF-TEST FAIL: --scan flagged a clean file" >&2; fails=$((fails+1)); }
  [ "$fails" -eq 0 ] && echo "redact.sh self-test PASS (19 cases)" || exit 1
else
  redact
fi
