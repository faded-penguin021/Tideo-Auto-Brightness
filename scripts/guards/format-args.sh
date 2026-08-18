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
# This repo has TWO such sites, and RESOLVERS below must name every one of them — an unnamed
# vararg wrapper is a silent hole, not a missing nicety. `Toaster.invoke(@StringRes resId, vararg
# formatArgs)` (D-131) routes every toast through `context.getString(resId, *formatArgs)`, and
# `ControlReceiver.flashDrop(…, @StringRes resId, vararg args)` (DB-035) does the same for dropped
# control commands. So `toast(id)` throws at display time, and the owner's DB-060 stack trace lands
# on that line. `getString`/`stringResource` are deliberately NOT scanned: the single-argument
# overloads cannot produce this crash, and flagging them would fail the ladder on correct code.
#
# `emptyArray()` spread into such a wrapper is ALSO zero arguments — `f(id, emptyArray())` reaches
# `String.format` with nothing, exactly like `f(id)`. `CircadianScreen.acquireCurrentLocation`
# passes its toasts that way, so a guard that only looked for a lone argument would miss the
# DB-060 shape on the very screen DB-057 edited.
#
# What it checks: for every string in the default-locale values/strings.xml whose value carries a
# format specifier, no tracked .kt file may pass it to a RESOLVERS function with no format
# arguments — as the call's only argument, or with an explicit `emptyArray()`.
#
# What it does NOT catch, and no other layer does either:
#   * an id reaching a resolver through a variable (`val id = …; toast(id)`) — the name is not on
#     the call line, so nothing connects it to a resource, and a spread of a runtime-empty array
#     (`toast(id, *args)` where `args` is empty) is the same blind spot one level down;
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
# EVERY function in this tree that reaches the formatting overload of getString: the Toaster
# (D-131) and ControlReceiver's flashDrop (DB-035). Adding another vararg resolver means adding it
# here; nothing can detect that for you, and the omission is invisible until something crashes.
RESOLVERS='toast|flashDrop'

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
# XML comments are stripped first, so a commented-out `<string>` cannot be classified as live.
# `name` is located by attribute match rather than position, so `<string translatable="false"
# name="x">` parses — requiring `name` first fails OPEN, and one IDE attribute sort is all it takes.
#
# Two ways a percent is legitimately not a specifier, both honoured: `%%` is the escape, and
# `formatted="false"` is Android's own opt-out for prose carrying a bare `%`. Classifying either as
# formatted would fail the ladder on correct code. The flag class covers `-+#0-9.,` but NOT the
# space flag (`% d`), deliberately: admitting a space would read "50 % battery" as a specifier.
formatted=$(awk '
	{
		line = $0
		while (1) {
			if (incomment) {
				i = index(line, "-->")
				if (i == 0) { line = ""; break }
				line = substr(line, i + 3); incomment = 0
			}
			j = index(line, "<!--")
			if (j == 0) break
			k = index(substr(line, j + 4), "-->")
			if (k == 0) { line = substr(line, 1, j - 1); incomment = 1; break }
			line = substr(line, 1, j - 1) substr(line, j + 4 + k + 2)
		}
		print line
	}
' "$STRINGS" | tr '\n' ' ' |
	sed 's|<string |\n<string |g; s|</string>|</string>\n|g' |
	sed -n 's|<string \([^>]*\)>\(.*\)</string>|\1\t\2|p' |
	awk -F'\t' '
		$1 ~ /formatted="false"/ { next }
		match($1, /name="[^"]*"/) == 0 { next }
		{
			name = substr($1, RSTART + 6, RLENGTH - 7)
			body = $2; gsub(/%%/, "", body)
		}
		body ~ /%[0-9]+\$[-+#0-9.,]*[a-zA-Z]|%[-+#0-9.,]*[a-zA-Z]/ { print name }
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
	# Three shapes, all zero format arguments, kept as separate alternatives because no single one
	# covers them without crossing call boundaries on a line holding several calls:
	#   1. the id is the ONLY argument, possibly chosen by a conditional — commas and semicolons
	#      barred on both sides, parens allowed so `toast(if (c) R.string.x else R.string.y)` matches;
	#   2. the id is the LAST argument after others (`flashDrop(ctx, level, R.string.x)`) — commas
	#      allowed before it, but parens and semicolons barred so the match cannot run from one
	#      call's opening paren into a later call's id and invent a finding;
	#   3. the same, closing with an explicit `emptyArray()` spread, which has a comma after the id
	#      and still formats with nothing.
	while IFS= read -r hit; do
		[ -n "$hit" ] || continue
		printf '%s passes R.string.%s to a vararg resolver with NO format arguments, but that string carries a format specifier — that resolver reaches getString(resId, *args), which formats even when the array is empty, so this throws MissingFormatArgumentException at display time (DB-060). Pass the arguments, or drop the specifier from the string.\n' \
			"$hit" "$name" >&2
		fails=$((fails + 1))
	done < <(printf '%s\n' "$kt" | tr '\n' '\0' |
		xargs -0 grep -HnE "(${RESOLVERS})\(([^,;]*R\.string\.${name}\b[^,;]*\)|[^;()]*R\.string\.${name}\b([^,;()]*\)|[^;()]*,[[:space:]]*emptyArray\(\)[[:space:]]*\)))" 2>/dev/null | cut -d: -f1,2)
done <<EOF
$formatted
EOF

[ "$fails" = 0 ] || exit 1
printf '%s formatted string(s), none reaching a vararg resolver bare\n' "$count"
