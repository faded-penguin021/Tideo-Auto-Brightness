#!/usr/bin/env bash
# Repo-local ladder guard — F-Droid whatsNew length cap (was ladder guard 6; D-173, DA-019).
#
# RUNBOOK §6: fastlane/metadata/android/en-US/changelogs/<versionCode>.txt must stay under 500
# CHARACTERS (whole file including the trailing newline) or F-Droid's code-quality scan flags
# the whatsNew.
#
# CHARACTERS, not bytes, and that distinction is the guard: F-Droid measures string LENGTH
# (codepoints), so a note with em dashes, accents or emoji can run well past 500 BYTES while
# still being legal. `wc -c` would reject it. Codepoints are counted locale-independently by
# dropping UTF-8 continuation bytes (0x80-0xBF) with LC_ALL=C tr — exactly one byte survives
# per codepoint (the lead byte, or any ASCII byte).
#
# Only the CURRENT versionCode's file is checked: it is the one the next tag ships. Historical
# files (the pre-rule 9.txt among them) are shipped facts, not actionable.
#
# EXISTENCE is deliberately not checked here — that is release-preflight.yml's job, because it
# knows whether the PR ships app code. This guard only rejects an oversize file.
set -uo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT" || exit 1

CAP=500

vc=$(grep -oE 'versionCode[[:space:]]*=[[:space:]]*[0-9]+' app/build.gradle.kts 2>/dev/null |
	grep -oE '[0-9]+' | head -1)
if [ -z "${vc:-}" ]; then
	# Not a skip. This guard's whole subject is "the changelog for the version being shipped",
	# and a versionCode this cannot read means it checked NOTHING while printing a pass.
	printf 'cannot read versionCode from app/build.gradle.kts — this guard checked nothing\n' >&2
	exit 1
fi

cl="fastlane/metadata/android/en-US/changelogs/${vc}.txt"
if [ ! -f "$cl" ]; then
	printf 'no changelog yet for versionCode %s (release-preflight.yml gates existence)\n' "$vc"
	exit 0
fi

chars=$(LC_ALL=C tr -d '\200-\277' <"$cl" | wc -c | tr -d '[:space:]')
if [ "$chars" -gt "$CAP" ]; then
	printf '%s is %s chars — over the %s-char F-Droid whatsNew cap (RUNBOOK §6). Shorten it.\n' \
		"$cl" "$chars" "$CAP" >&2
	exit 1
fi
printf '%s: %s/%s chars\n' "$cl" "$chars" "$CAP"
