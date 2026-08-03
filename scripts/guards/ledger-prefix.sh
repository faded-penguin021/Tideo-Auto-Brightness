#!/usr/bin/env bash
# Repo-local ladder guard — a citation's PREFIX names its ledger volume (D-153/DA-001).
#
# The shipped citation guard pools every ledger file and asks only "does this ID resolve
# somewhere?". That is the right question for it — an adopter may have one volume or five, and
# it cannot know their naming. Here the naming IS the rule: rollover opens a new volume with a
# new letter, so `D-` rows live in LEDGER.md, `DA-` in LEDGER_A.md, `DB-` in LEDGER_B.md, and
# a row that lands in the wrong file is a mis-numbered append. Both of this guard's own
# defects — counting volumes instead of rows, and inheriting config it was never handed — are
# recorded in DB-016.
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

# amh.conf is SOURCED here, not inherited. The ladder assigns these keys plainly and runs each
# guard as `bash <guard>`, which is a child process — a non-exported variable does not cross
# that boundary, so reading them from the environment silently pinned this guard to the
# defaults below. They happen to equal the configured values today, which is exactly what made
# it invisible: point LEDGER_DIR somewhere else and this guard would keep checking `docs/`
# while the real ledger went unchecked.
LEDGER_DIR=docs
LEDGER_BASENAME=LEDGER
# shellcheck source=/dev/null
[ -f "$ROOT/amh.conf" ] && . "$ROOT/amh.conf"

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
total_rows=0
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
	rows=0
	while IFS= read -r id; do
		[ -n "$id" ] || continue
		rows=$((rows + 1))
		case $id in
		"$want"*) ;;
		*)
			printf 'ledger row %s is in %s, which holds %sNNN rows — a rollover opens a NEW volume and never moves or renumbers existing rows, so this is a mis-numbered append. Renumber the append to the live volume it belongs in (D-153/DA-001).\n' \
				"$id" "$f" "$want" >&2
			fails=$((fails + 1))
			;;
		esac
	done < <(sed -n 's/^- \(D[A-Z]\?-[0-9]\+\)\( \[cited\]\)\?:.*/\1/p' "$f")
	# A volume that yields NO parseable rows is the silent-pass hole, not an empty volume: the
	# loop above simply never runs and the success line below then claims every row is filed
	# correctly on the strength of having read none. The shape is real — every row in this repo
	# opened bold (`- **D-NNN**: …`) until they were normalized, and re-bolding one volume would
	# disarm this guard for that volume while printing its strongest line. The shipped citation
	# guard catches a TOTAL parse failure via unresolved citations; a partial one, in a volume
	# nothing currently cites, is only visible here.
	if [ "$rows" = 0 ]; then
		printf '%s yielded 0 parseable rows — this guard checked NOTHING for that volume. A row header must read `- D-NNN[ [cited]]: …`; anything else (a bold opener, say) is invisible to every ledger parser in the tree.\n' "$f" >&2
		fails=$((fails + 1))
	fi
	total_rows=$((total_rows + rows))
done

if [ "$seen" = 0 ]; then
	printf 'no ledger volumes found under %s/ — this guard checked nothing\n' "$LEDGER_DIR" >&2
	exit 1
fi

[ "$fails" = 0 ] || exit 1
printf '%s ledger volume(s), %s row(s), every row in the volume its prefix names\n' "$seen" "$total_rows"
