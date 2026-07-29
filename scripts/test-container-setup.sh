#!/usr/bin/env bash
# Hermetic-enough contract checks for the sourceable standard-container bootstrap.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SETUP="$ROOT/scripts/setup-container.sh"

bash -n "$ROOT/scripts/build-toolchain.sh" "$ROOT/scripts/setup-android-sdk.sh" \
  "$ROOT/scripts/setup-container.sh" "$ROOT/scripts/session-start.sh"

set +e
"$SETUP" >/dev/null 2>&1
direct_code=$?
set -e
[[ $direct_code -eq 2 ]] || { echo "FAIL: direct execution must exit 2" >&2; exit 1; }

(
  source "$SETUP" >/dev/null
  first_path="$PATH"
  source "$SETUP" >/dev/null
  [[ "$PATH" == "$first_path" ]]
  [[ "$JAVA_HOME" == "/usr/lib/jvm/java-21-openjdk-amd64" ]]
  [[ "$(java -XshowSettings:properties -version 2>&1 |
    sed -n 's/^[[:space:]]*java.specification.version = //p')" == "21" ]]
  [[ -d "$ANDROID_SDK_ROOT/platforms/android-36" ]]
  [[ -x "$ANDROID_SDK_ROOT/build-tools/35.0.0/apksigner" ]]
)

for path_fixture in \
  ":/usr/lib/jvm/java-21-openjdk-amd64/bin:/bin:/usr/bin|/usr/lib/jvm/java-21-openjdk-amd64/bin::/bin:/usr/bin" \
  "/bin:/usr/lib/jvm/java-21-openjdk-amd64/bin:::/usr/bin|/usr/lib/jvm/java-21-openjdk-amd64/bin:/bin:::/usr/bin" \
  ":/usr/lib/jvm/java-21-openjdk-amd64/bin:/bin::/usr/bin|/usr/lib/jvm/java-21-openjdk-amd64/bin::/bin::/usr/bin"; do
  (
    PATH="${path_fixture%%|*}"
    expected_path="${path_fixture#*|}"
    source "$SETUP" >/dev/null
    [[ "$PATH" == "$expected_path" ]]
  )
done

(
  before_path="$PATH"
  before_java_home="${JAVA_HOME-}"
  if AAB_JAVA_HOME=/definitely/missing source "$SETUP" >/dev/null 2>&1; then
    echo "FAIL: missing JDK should fail" >&2
    exit 1
  fi
  [[ "$PATH" == "$before_path" ]]
  [[ "${JAVA_HOME-}" == "$before_java_home" ]]
  [[ -z "${AAB_JAVA_HOME_DEFAULT+x}" ]]
  [[ -z "${AAB_ANDROID_PLATFORM+x}" ]]
  [[ -z "${AAB_ANDROID_BUILD_TOOLS+x}" ]]
  [[ -z "${AAB_ANDROID_CLT_URL+x}" ]]
)

echo "container setup tests: PASS"
