#!/bin/bash
# Agent-neutral session bootstrap (D-176; formerly .claude/hooks/session-start.sh, D-166):
# on remote (ephemeral-container) sessions bootstraps the Android SDK (idempotent, ~4 min on
# a cold container, instant once cached); on ALL sessions verifies the working branch and
# points the session at the maintenance docs. Any agent's adapter runs this at session start
# (Claude Code: SessionStart hook in .claude/settings.json). See CLAUDE.md "Agent harness".
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# SDK bootstrap is only wanted on remote containers — never implicitly on a developer
# machine (manual local setup is Maintenance protocol step 1). Neutral signal: AAB_REMOTE=1;
# CLAUDE_CODE_REMOTE=true (set by Claude Code on the web) is honored for back-compat (D-176).
if [ "${AAB_REMOTE:-}" = "1" ] || [ "${CLAUDE_CODE_REMOTE:-}" = "true" ]; then
  "$ROOT/scripts/setup-android-sdk.sh"
  echo "Android SDK ready; local.properties written."

  # D-173: warm Gradle in the background while the session reads the maintenance docs — a
  # fresh container's first ladder run otherwise pays the full dependency+compile+test cost
  # serially. Gradle's own locking serializes it against any build the session starts; the
  # script self-skips when a Gradle process already exists (container already warm).
  if [ "${AAB_SKIP_WARMUP:-}" != "1" ]; then
    nohup "$ROOT/scripts/warm-gradle.sh" >/dev/null 2>&1 &
    echo "Gradle warm-up launched in background (log: ~/.gradle-warmup.log; AAB_SKIP_WARMUP=1 disables)."
  fi
fi

# Branch check (every session): work happens on your assigned session branch, never main or
# a detached HEAD — the first misplaced commit is the expensive one to unwind.
branch=$(git -C "$ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")
if [ -z "$branch" ]; then
  echo "WARNING: not in a git work tree — cannot verify the session branch."
elif [ "$branch" = "HEAD" ]; then
  echo "WARNING: detached HEAD — check out your assigned session branch before committing."
elif [ "$branch" = "main" ]; then
  echo "WARNING: on 'main' — switch to your assigned session branch before committing (never commit or push to main)."
else
  echo "Branch: $branch"
fi

# Maintenance pointer (every session).
echo "Maintenance: read docs/rebuild/STATE.md first, then the matching change-type playbook in docs/rebuild/RUNBOOK.md (protocol in CLAUDE.md; AGENTS.md points there)."
