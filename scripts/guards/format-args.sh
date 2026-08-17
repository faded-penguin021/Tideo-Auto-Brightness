#!/usr/bin/env bash
# Repo-local ladder guard — a formatted string must never reach the Toaster with no arguments
# (DB-060).
#
# THE MECHANISM, stated precisely, because a guard that misnames the crash teaches the wrong
# lesson and fires on the wrong code. `Resources.getString(int)` does NOT format — it is
# `getText(id).toString()`, and Compose's `stringResource(id)` delegates to it. Resolving a
# `%1$d` string through either renders the template literally; that is a display bug, and
# `template.format(...)` afterwards is a legitimate idiom this tree already uses. Only the
# VARARG overload `getString(int, Object...)` calls `String.format`, and it calls it even when
# the vararg array is empty — which is where `MissingFormatArgumentException` comes from.
#
# This repo has exactly one such site: `Toaster.invoke(@StringRes resId, vararg formatArgs)`
# (D-131) routes every toast through `context.getString(resId, *formatArgs)`. So `toast(id)` with
# a formatted string throws at display time, and the owner's DB-060 stack trace lands on that
# line. `getString`/`stringResource` are deliberately NOT scanned: they cannot produce this crash,
# and flagging them would fail the ladder on correct code.
#
# What it checks: for every string in the default-locale values/strings.xml whose value carries a
# format specifier, no tracked .kt file may pass it to `toast(...)` as the call's only argument.
#
# What it does NOT catch, and no other layer does either:
#   * an id reaching `toast` through a variable (`val id = …; toast(id)`) — the name is not on the
#     call line, so nothing connects it to a resource;
#   * multi-line CALL sites — the call scan is line-oriented (the resource parse is not: it
#     flattens the XML first, so a wrapped `<string>` element is still seen);
#   * `<plurals>`/`<string-array>` items — those resolve through
#     `getQuantityString`/`getStringArray`, which this does not model;
#   * argument COUNT or TYPE past zero — `%1$d %2$d` given one arg still throws, and `%d` given a
#     String throws IllegalFormatConversionException. Zero args is the unambiguous half; counting
#     args means parsing Kotlin expressions, which this deliberately does not attempt;
#   * translations — a values-xx/strings.xml whose specifiers drift from the default locale is
#     Android lint's territory.
# It scans TRACKED files (`git ls-files`), so `git add` before verifying — the DB-056 rule holds
# for this guard exactly as AGENTS.md states it for the others.
set -uo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT" || exit 1

STRINGS=app/src/main/res/values/strings.xml
# The one function in this tree that reaches the formatting overload of getString (D-131). Adding
# another vararg resolver means adding it here; nothing can detect that for you.
RESOLVERS='toast'

if [ ! -f "$STRINGS" ]; then
	printf '%s is missing — this guard checked nothing\n' "$STRINGS" >&2
	exit 1
fi

# Names whose value carries a format specifier.
#
# The XML is FLATTENED first and re-split one element per line, so a `<string>` whose text wraps
# across lines is still seen — a line-oriented parse fails OPEN on those, silently, and an IDE
# reflow or a translation round-trip is all it takes to wrap one.
#
# Two ways a percent is legitimately not a specifier, both honoured: `%%` is the escape, and
# `formatted="false"` is Android's own opt-out for prose carrying a bare `%` (three strings here
# use it). Classifying either as formatted would fail the ladder on correct code.
formatted=$(tr '\n' ' ' <"$STRINGS" |
	sed 's|<string |\n<string |g; s|</string>|</string>\n|g' |
	sed -n 's|<string name="\([^"]*\)"\([^>]*\)>\(.*\)</string>|\1\t\2\t\3|p' |
	awk -F'\t' '
		$2 ~ /formatted="false"/ { next }
		{ body = $3; gsub(/%%/, "", body) }
		body ~ /%[0-9]+\$[-0-9.,]*[a-zA-Z]|%[-0-9.,]*[a-zA-Z]/ { print $1 }
	' | sort -u)

if [ -z "$formatted" ]; then
	printf '%s yielded 0 formatted strings — either none exist or the parse broke; this guard checked NOTHING\n' \
		"$STRINGS" >&2
	exit 1
fi

kt=$(git ls-files '*.kt' 2>/dev/null)
if [ -z "$kt" ]; then
	printf 'no tracked .kt files — this guard checked NOTHING\n' >&2
	exit 1
fi

count=$(printf '%s\n' "$formatted" | wc -l | tr -d ' ')
fails=0
while IFS= read -r name; do
	[ -n "$name" ] || continue
	# `[^,]*` on both sides is what makes an id-selecting conditional visible: the argument list
	# has to close with no top-level comma, so `toast(R.string.x)` and
	# `toast(if (c) R.string.x else R.string.y)` both match while `toast(R.string.x, arg)` cannot.
	while IFS= read -r hit; do
		[ -n "$hit" ] || continue
		printf '%s passes R.string.%s to toast() as its only argument, but that string carries a format specifier — Toaster routes through the vararg getString(resId, *formatArgs), which formats even when the vararg array is empty, so this throws MissingFormatArgumentException at display time (DB-060). Pass the arguments, or drop the specifier from the string.\n' \
			"$hit" "$name" >&2
		fails=$((fails + 1))
	done < <(printf '%s\n' "$kt" | tr '\n' '\0' |
		xargs -0 grep -HnE "(${RESOLVERS})\([^,]*R\.string\.${name}\b[^,]*\)" 2>/dev/null | cut -d: -f1,2)
done <<EOF
$formatted
EOF

[ "$fails" = 0 ] || exit 1
printf '%s formatted string(s), none reaching toast() bare\n' "$count"
