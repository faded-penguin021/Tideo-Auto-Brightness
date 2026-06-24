#!/bin/bash
# SessionStart hook: bootstraps the Android SDK (idempotent, ~4 min on a cold container,
# instant once cached) and points the session at the maintenance docs.
# See CLAUDE.md "Maintenance protocol".
set -euo pipefail

# Only needed in Claude Code on the web (remote container) sessions.
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

"$CLAUDE_PROJECT_DIR/scripts/setup-android-sdk.sh"

echo "Android SDK ready; local.properties written."
echo "Maintenance: read docs/rebuild/STATE.md first, then the matching change-type playbook in docs/rebuild/RUNBOOK.md (protocol in CLAUDE.md)."
