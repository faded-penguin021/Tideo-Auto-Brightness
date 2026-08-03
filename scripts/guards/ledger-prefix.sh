#!/usr/bin/env bash
# Repo-local ladder guard — a citation's PREFIX names its ledger volume (D-153/DA-001).
#
# The shipped citation guard pools every ledger file and asks only "does this ID resolve
# somewhere?". That is the right question for it — an adopter may have one volume or five, and
# it cannot know their naming. Here the naming IS the rule: rollover opens a new volume with a
# new letter, so `D-` rows live in LEDGER.md, `DA-` in LEDGER_A.md, `DB-` in LEDGER_B.md, and
# a row that lands in the wrong file is a mis-numbered append.
#
# The shipped guard cannot see that: a DB- row appended by mistake to LEDGER_A.md resolves
# fine, pools cleanly and passes. This is the half that stays ours.
#
# It checks the ROWS, not the citations — every row must sit in the volume its prefix names,
# whether or not any code cites it — so a mis-numbered append is caught at the moment it is
# written rather than whenever something first cites it.
set -uo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT" || exit 1

LEDGER_DIR=${LEDGER_DIR:-docs}
LEDGER_BASENAME=${LEDGER_BASENAME:-LEDGER}

# Suffix -> expected prefix. The base volume takes the bare `D-`; `_A` takes `DA-`, and so on,
# so the mapping is derived rather than listed and a future `_C.md` needs no edit here.
expected_prefix() { # <file> -> the ID prefix rows in it must carry
	local base=$1 suffix
	suffix=${base#"${LEDGER_BASENAME}"}
	suffix=${suffix%.md}
	case $suffix in
	'') printf 'D-' ;;
	_[A-Z]) printf 'D%s-' "${suffix#_}" ;;
	*) printf '' ;;
	esac
}

fails=0
seen=0
for f in "$LEDGER_DIR/$LEDGER_BASENAME.md" "$LEDGER_DIR/${LEDGER_BASENAME}"_*.md; do
	[ -f "$f" ] || continue
	base=$(basename -- "$f")
	want=$(expected_prefix "$base")
	if [ -z "$want" ]; then
		printf '%s does not follow the rollover naming (%s.md, then %s_A.md, %s_B.md, …) — a volume this cannot classify is a volume whose rows are unchecked\n' \
			"$f" "$LEDGER_BASENAME" "$LEDGER_BASENAME" "$LEDGER_BASENAME" >&2
		fails=$((fails + 1))
		continue
	fi
	seen=$((seen + 1))
	while IFS= read -r id; do
		[ -n "$id" ] || continue
		case $id in
		"$want"*) ;;
		*)
			printf 'ledger row %s is in %s, which holds %sNNN rows — a rollover opens a NEW volume and never moves or renumbers existing rows, so this is a mis-numbered append. Renumber the append to the live volume it belongs in (D-153/DA-001).\n' \
				"$id" "$f" "$want" >&2
			fails=$((fails + 1))
			;;
		esac
	done < <(sed -n 's/^- \(D[A-Z]\?-[0-9]\+\)\( \[cited\]\)\?:.*/\1/p' "$f")
done

if [ "$seen" = 0 ]; then
	printf 'no ledger volumes found under %s/ — this guard checked nothing\n' "$LEDGER_DIR" >&2
	exit 1
fi

[ "$fails" = 0 ] || exit 1
printf '%s ledger volume(s), every row in the volume its prefix names\n' "$seen"
