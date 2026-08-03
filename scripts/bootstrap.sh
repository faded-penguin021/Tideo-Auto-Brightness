#!/usr/bin/env bash
# Repo-specific toolchain bootstrap. The AMH session bootstrap (scripts/session-start.sh)
# calls this — and ONLY this — when the remote flag named by REMOTE_FLAG in amh.conf is 1.
# Never called on a developer machine unless they set that flag themselves, which is why
# nothing here asks whether it is wanted: reaching this file IS the answer.
#
# Yours, not shipped: this is the AMH's toolchain extension point (docs/HARNESS_LOCAL.md).
# It carries what the pre-AMH scripts/session-start.sh did on remote containers (D-173/D-176).
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

# Android SDK + local.properties. Idempotent: ~4 min on a cold container, instant once cached.
"$ROOT/scripts/setup-android-sdk.sh"
echo "Android SDK ready; local.properties written."

# D-173: warm Gradle in the background while the session reads the maintenance docs — a fresh
# container's first ladder run otherwise pays the full dependency+compile+test cost serially.
# Gradle's own inter-process lock serializes it against any build the session starts; the
# script self-skips when a Gradle process already exists (container already warm).
if [ "${AAB_SKIP_WARMUP:-}" != "1" ]; then
	nohup "$ROOT/scripts/warm-gradle.sh" >/dev/null 2>&1 &
	echo "Gradle warm-up launched in background (log: ~/.gradle-warmup.log; AAB_SKIP_WARMUP=1 disables)."
fi
