#!/bin/bash
# SessionStart hook: on remote (web) containers bootstraps the Android SDK (idempotent, ~4 min
# on a cold container, instant once cached); on ALL sessions verifies the working branch and
# points the session at the maintenance docs (D-166 — local/CLI sessions used to get neither).
# See CLAUDE.md "Maintenance protocol".
set -euo pipefail

# SDK bootstrap is only needed in Claude Code on the web (remote container) sessions.
if [ "${CLAUDE_CODE_REMOTE:-}" = "true" ]; then
  "$CLAUDE_PROJECT_DIR/scripts/setup-android-sdk.sh"
  echo "Android SDK ready; local.properties written."

  # D-173: warm Gradle in the background while the session reads the maintenance docs — a
  # fresh container's first ladder run otherwise pays the full dependency+compile+test cost
  # serially. Gradle's own locking serializes it against any build the session starts; the
  # script self-skips when a Gradle process already exists (container already warm).
  if [ "${AAB_SKIP_WARMUP:-}" != "1" ]; then
    nohup "$CLAUDE_PROJECT_DIR/scripts/warm-gradle.sh" >/dev/null 2>&1 &
    echo "Gradle warm-up launched in background (log: ~/.gradle-warmup.log; AAB_SKIP_WARMUP=1 disables)."
  fi
fi

# Branch check (every session): work happens on the assigned claude/* session branch, never
# main or a detached HEAD — the first misplaced commit is the expensive one to unwind.
branch=$(git -C "$CLAUDE_PROJECT_DIR" rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")
if [ -z "$branch" ]; then
  echo "WARNING: not in a git work tree — cannot verify the session branch."
elif [ "$branch" = "HEAD" ]; then
  echo "WARNING: detached HEAD — check out your assigned claude/* session branch before committing."
elif [ "$branch" = "main" ]; then
  echo "WARNING: on 'main' — switch to your assigned claude/* session branch before committing (never commit or push to main)."
else
  echo "Branch: $branch"
fi

# Maintenance pointer (every session).
echo "Maintenance: read docs/rebuild/STATE.md first, then the matching change-type playbook in docs/rebuild/RUNBOOK.md (protocol in CLAUDE.md)."
