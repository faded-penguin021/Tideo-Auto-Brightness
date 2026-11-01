#!/usr/bin/env bash
# Repo-local ladder guard — codemap's registry resolves and codemap/map.json is current (DE-010,
# DE-021).
#
# It runs `python3 scripts/codemap.py check`, which fails when codemap/features.toml is malformed,
# when an anchor resolves to no top-level symbol or to more than one, when a tracked Kotlin file
# in scope cannot be scanned, and when codemap/map.json is missing or differs byte for byte from a
# fresh generation. The tool owns those semantics and its tests in scripts/tests/codemap/ define
# them; this file only gives the ladder a fail-closed verdict and names a missing interpreter.
#
# Fail-closed, never WARN: a stale generated file is wrong every time it fires, and the fix is one
# command (`python3 scripts/codemap.py write`). Every non-zero status from the tool, its usage
# exit 2 included, becomes exit 1 here, so nothing it prints can pass for the ladder's WARN tier.
#
# What it does NOT catch:
#   * duplication — it is not a duplication detector; it checks that declared anchors exist and
#     that the generated map is current, nothing about what a new file reimplements;
#   * a deleted anchor — removing an anchor instead of updating it passes; the edit shows in the
#     features.toml diff and the members it drops show in the map diff, for a reviewer;
#   * coverage — a feature nobody registered is invisible to every query, and no check asks;
#   * an unstaged new Kotlin file — the scan reads `git ls-files`, so the map can be current for
#     the tracked set while a new file sits outside it until `git add`.
set -uo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT" || exit 1

if ! command -v python3 >/dev/null 2>&1; then
	printf 'python3 is not on PATH, so scripts/codemap.py cannot run — this guard checked nothing\n' >&2
	exit 1
fi

out=$(python3 -B scripts/codemap.py check 2>&1)
rc=$?
if [ "$rc" -ne 0 ]; then
	printf 'codemap check failed (codemap.py exit %s):\n%s\n' "$rc" "$out" >&2
	exit 1
fi
printf '%s\n' "$out"
