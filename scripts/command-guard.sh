#!/bin/bash
# Agent-neutral pre-execution command guard (DA-009). Checks a shell command against the
# repo's hard rails BEFORE it runs and, on a violation, blocks with an INSTRUCTIVE reason —
# an adapter feeds the reason back to the agent, which then self-corrects instead of
# fighting an opaque denial (the whole point over a static deny list).
#
# Rails enforced (the regex-checkable subset of CLAUDE.md's rules; the prose still binds
# everything a regex can't see, and non-hook agents entirely):
#   - no force-push in any spelling (--force, --force-with-lease/-if-includes, -f, +refspec)
#   - no push targeting main (positional, HEAD:main, :main deletion, refs/heads/main)
#   - no environment dumps (bare `env`, `printenv`, /proc/*/environ) — D-175
#
# Modes:
#   command-guard.sh 'git push -f'        check argv as one command string
#   command-guard.sh --claude-hook        Claude Code PreToolUse hook: reads the hook JSON
#                                         on stdin, checks .tool_input.command; on violation
#                                         exits 2 with the reason on stderr (the harness
#                                         shows that reason to the model)
#   command-guard.sh --self-test          blocked/allowed fixture matrix; ladder guard 10
#
# Fail-open on malformed hook input BY DESIGN (no jq/python3, no command field): a guard
# that bricks every Bash call on a parse error gets disabled, not fixed. The adapter's
# static deny rules and the prose rules remain as backstop layers. Known misses, accepted
# for the same layering reason (the threat model is agent MISTAKES, not evasion): prefixed
# invocations (`sudo git push -f`, `git -C <path> push`, `git -c k=v push`), folded short
# flags (`-uf`), and quoting/substitution evasions (`git push origin 'main'`, backticks,
# backslash-newline continuations).
set -uo pipefail

# Each simple-command segment is judged alone (split on newlines and ; | & parentheses), so
# a flag in one segment never taints another: `rm -f x && git push -u origin b` is allowed.
check_cmd() {  # $1 = command string. rc 0 = allowed; rc 1 = blocked, reason on stdout.
  local seg
  while IFS= read -r seg; do
    seg="${seg#"${seg%%[![:space:]]*}"}"
    [ -n "$seg" ] || continue
    # Git rails judge only the segment's LEADING command (optional VAR=… prefixes allowed):
    # quoted text that merely CONTAINS "git push origin main" — a commit message, a doc
    # heredoc, this guard's own argv mode — must not trip them (a live false-positive class
    # the DA-005 review reproduced). `push` must be git's verb (only flags between the two).
    if printf '%s\n' "$seg" | grep -qE '^([A-Za-z_][A-Za-z0-9_]*=[^[:space:]]*[[:space:]]+)*git([[:space:]]+-[^[:space:]]+)*[[:space:]]+push([[:space:]]|$)'; then
      if printf '%s\n' "$seg" | grep -qE -- '--force(-with-lease|-if-includes)?([[:space:]=]|$)|--(mirror|prune)([[:space:]]|$)|(^|[[:space:]])-f([[:space:]]|$)|[[:space:]]\+[^[:space:]]'; then
        echo "BLOCKED: force-push (and ref-deleting pushes: --mirror/--prune) is forbidden in this repo — pushed checkpoints are immutable (CLAUDE.md Git rules; the one exception, a leaked-credential history rewrite, is owner-executed, never the agent). Drop --force/-f/--mirror/--prune/+refspec and push normally; if the branch diverged, 'git merge origin/main' instead of rewriting pushed history."
        return 1
      fi
      if printf '%s\n' "$seg" | grep -qE '[[:space:]:+/]main([[:space:]:]|$)'; then
        echo "BLOCKED: never push to main — main advances only via the owner's single squash-merge PR (CLAUDE.md Git rules, branch-train DA-002). Push to your session branch instead: git push -u origin <session-branch>."
        return 1
      fi
    fi
    # Env-dump rail: bare `env` (flags/assignments but NO command still dumps), `printenv`,
    # `export -p`, and PID-path /proc reads. The /proc pattern requires a PID-ish segment
    # ([a-zA-Z0-9$]) so PROSE naming the rule as /proc/*/environ (docs, ledger rows written
    # via heredoc) doesn't trip the rail — a live false positive found the day this shipped.
    if printf '%s\n' "$seg" | grep -qE '^env([[:space:]]+(-0|--null|[A-Za-z_][A-Za-z0-9_]*=[^[:space:]]*))*[[:space:]]*([><].*)?$|^printenv([[:space:]]|$)|^export[[:space:]]+-p([[:space:]]*([><].*)?)?$' \
       || printf '%s\n' "$seg" | grep -qE '/proc/[a-zA-Z0-9$]+/environ'; then
      echo "BLOCKED: environment dumps are forbidden — the session environment carries credentials (CLAUDE.md Secret hygiene, D-175). Never print values; test presence only, e.g.: [ -n \"\${SOME_KEY:-}\" ] && echo 'SOME_KEY is set'. (Prefix usage 'env VAR=x cmd' is allowed.)"
      return 1
    fi
  done < <(printf '%s\n' "$1" | tr ';|&()' '\n')
  return 0
}

self_test() {
  local blocked=(
    'git push --force origin claude/x'
    'git push -f'
    'git push --force-with-lease origin b'
    'git push --force-if-includes'
    'git push origin +claude/x:claude/x'
    'git push origin main'
    'git push -u origin main'
    'git push origin HEAD:main'
    'git push origin :main'
    'git push origin refs/heads/main'
    'git fetch origin && git push origin main'
    'git push --mirror origin'
    'git push --prune origin claude/x'
    'env'
    'env FOO=1'
    'export -p'
    'env | grep -c PATH'
    'env > /tmp/e.txt'
    'printenv HOME'
    'cat /proc/self/environ'
    'tr "\0" "\n" < /proc/4242/environ'
  )
  local allowed=(
    'git push -u origin claude/agent-agnostic-harness-fmb35b'
    'git push origin claude/foo:claude/foo'
    'git push origin main-backup'
    'rm -f build.log && git push -u origin claude/foo'
    'git commit -m "docs: never push to main"'
    'git commit -m "revert: undo git push origin main mistake"'
    'echo "never run: git push --force"'
    "scripts/command-guard.sh 'git push -f'"
    'git log origin/main..HEAD'
    'env AAB_REMOTE=1 scripts/session-start.sh'
    'export AAB_REMOTE=1'
    'grep -rn environ docs'
    'echo "the rule covers /proc/*/environ reads"'
    'scripts/ladder.sh --guards-only'
  )
  local c rc=0
  for c in "${blocked[@]}"; do
    if check_cmd "$c" >/dev/null; then echo "command-guard self-test FAIL (should BLOCK): $c" >&2; rc=1; fi
  done
  for c in "${allowed[@]}"; do
    if ! check_cmd "$c" >/dev/null; then echo "command-guard self-test FAIL (should ALLOW): $c" >&2; rc=1; fi
  done
  [ "$rc" -eq 0 ] && echo "command-guard self-test OK ($(( ${#blocked[@]} + ${#allowed[@]} )) cases)"
  return "$rc"
}

case "${1:-}" in
  --self-test)
    self_test
    ;;
  --claude-hook)
    input=$(cat)
    cmd=""
    if command -v jq >/dev/null 2>&1; then
      cmd=$(printf '%s' "$input" | jq -r '.tool_input.command // empty' 2>/dev/null) || cmd=""
    elif command -v python3 >/dev/null 2>&1; then
      cmd=$(printf '%s' "$input" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("tool_input",{}).get("command",""))' 2>/dev/null) || cmd=""
    fi
    [ -n "$cmd" ] || exit 0
    if reason=$(check_cmd "$cmd"); then
      exit 0
    else
      printf '%s\n' "$reason" >&2
      exit 2
    fi
    ;;
  *)
    if [ $# -eq 0 ]; then
      echo "usage: command-guard.sh <command string> | --claude-hook | --self-test" >&2
      exit 64
    fi
    if reason=$(check_cmd "$*"); then
      echo "ALLOWED"
    else
      printf '%s\n' "$reason"
      exit 1
    fi
    ;;
esac
