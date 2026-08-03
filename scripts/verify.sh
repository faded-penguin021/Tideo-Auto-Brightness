#!/usr/bin/env bash
# Rung 3 of the ladder: this repository's full verification set.
#
# Yours, not shipped — one of the AMH ladder's two extension points, and the reason the
# shipped ladder never needs a local edit (docs/HARNESS_LOCAL.md).
#
# Invoked by scripts/ladder.sh, never directly by CI: CI runs the ladder, so the agent and CI
# execute the same entrypoint by construction and "green locally, red in CI" can only mean
# environment (D-166). The five Gradle rungs are ONE invocation — the same task set
# .github/workflows/build.yml used to spell out, now stated only here.
set -uo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

FAILS=0
step() { printf '\n   · %s\n' "$1"; }
bad() {
	printf '     FAIL %s\n' "$1"
	FAILS=$((FAILS + 1))
}

# The two fixture suites first: seconds of pure shell, and both are load-bearing for the
# guards that already ran above us. A broken guard should fail before a 90-second Gradle run,
# not after it.
step "shipped guard fixture suite (scripts/test-ladder-guards.sh)"
bash scripts/test-ladder-guards.sh || bad "scripts/test-ladder-guards.sh"

step "repo-local guard fixture suite (scripts/tests/local-guards.sh)"
if [ -f scripts/tests/local-guards.sh ]; then
	bash scripts/tests/local-guards.sh || bad "scripts/tests/local-guards.sh"
else
	# Not a skip: scripts/guards/ holds this repo's earned guards, and a suite that has
	# vanished is the one state that must be louder than a pass.
	bad "scripts/tests/local-guards.sh is missing — the repo-local guards under scripts/guards/ are untested"
fi

# If the session-start warm-up (D-173) is still in flight, say so: Gradle's inter-process lock
# serializes this build behind it (total wall time ≈ one cold build — expected, not a hang).
# Informational only — the lock IS the synchronization; no sentinel, no wait (DA-010).
if pgrep -f 'warm-gradle\.sh' >/dev/null 2>&1; then
	printf '     (Gradle warm-up still running — this build queues behind its lock; a long first\n'
	printf '      rung is expected, not a hang. Progress: ~/.gradle-warmup.log)\n'
fi

# CI's single Gradle invocation only ever PAYS the config-cache store cost (nothing reuses
# it), so skip it there; local sessions keep the cache — persistent daemon, repeated
# invocations, which is what it is for (D-161 U4, DA-017). These flags used to sit on
# build.yml's command line, which the shipped ladder has no way to forward.
GRADLE_ARGS=()
[ -n "${CI:-}${GITHUB_ACTIONS:-}" ] && GRADLE_ARGS=(--no-daemon --no-configuration-cache)

step "domain + platform + app tests, lint (hard gate), debug APK"
./gradlew :domain:test :platform:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug \
	${GRADLE_ARGS[@]+"${GRADLE_ARGS[@]}"} || bad "Gradle verification set"

if [ "$FAILS" -gt 0 ]; then
	printf '\n   verification set: %d failure(s)\n' "$FAILS"
	exit 1
fi
printf '\n   verification set: clean\n'
