#!/usr/bin/env bash
# Source this file in the repository root of the standard agent container:
#   source scripts/setup-container.sh
#
# DA-032: JDK 25 is the image default, but AGP/Robolectric and every CI/reproducibility lane use JDK 21.
# Sourcing (rather than executing) keeps JAVA_HOME/PATH/ANDROID_* in the caller's shell.

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  echo "ERROR: source this script so its JDK/SDK exports persist:" >&2
  echo "  source scripts/setup-container.sh" >&2
  exit 2
fi

_aab_setup_container() {
  local root java_home sdk_root java_spec gradle_version path_without_java wrapped_path java_path_entry
  local AAB_JAVA_HOME_DEFAULT AAB_ANDROID_PLATFORM AAB_ANDROID_BUILD_TOOLS AAB_ANDROID_CLT_URL

  root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)" || return 1
  source "$root/scripts/build-toolchain.sh" || return 1
  java_home="${AAB_JAVA_HOME:-$AAB_JAVA_HOME_DEFAULT}"
  sdk_root="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"

  if [[ ! -x "$java_home/bin/java" ]]; then
    echo "ERROR: the container's JDK 21 was not found at $java_home" >&2
    return 1
  fi

  # Bootstrap transactionally: a download/setup failure must not leave the caller half-switched.
  JAVA_HOME="$java_home" \
  PATH="$java_home/bin:${PATH:-}" \
  ANDROID_SDK_ROOT="$sdk_root" \
    "$root/scripts/setup-android-sdk.sh" || return 1

  java_spec="$("$java_home/bin/java" -XshowSettings:properties -version 2>&1 |
    sed -n 's/^[[:space:]]*java.specification.version = //p')"
  if [[ "$java_spec" != "21" ]]; then
    echo "ERROR: expected Java 21, got ${java_spec:-unknown}" >&2
    return 1
  fi

  if [[ ! -d "$sdk_root/platforms/android-$AAB_ANDROID_PLATFORM" ]] ||
     [[ ! -x "$sdk_root/build-tools/$AAB_ANDROID_BUILD_TOOLS/apksigner" ]]; then
    echo "ERROR: Android compile SDK $AAB_ANDROID_PLATFORM / build-tools $AAB_ANDROID_BUILD_TOOLS setup is incomplete" >&2
    return 1
  fi

  gradle_version="$(JAVA_HOME="$java_home" PATH="$java_home/bin:${PATH:-}" \
    "$root/gradlew" --version 2>/dev/null | sed -n 's/^Gradle //p' | head -1)"
  if [[ -z "$gradle_version" ]]; then
    echo "ERROR: the repository Gradle wrapper could not start under JDK 21" >&2
    return 1
  fi

  # Commit exports only after every check succeeds. Remove an existing identical JDK bin entry so
  # repeated sourcing is idempotent (session-start plus a manual source is expected).
  wrapped_path=":${PATH-}:"
  java_path_entry=":$java_home/bin:"
  while [[ "$wrapped_path" == *"$java_path_entry"* ]]; do
    wrapped_path="${wrapped_path/"$java_path_entry"/:}"
  done
  path_without_java="${wrapped_path#:}"
  path_without_java="${path_without_java%:}"
  export JAVA_HOME="$java_home"
  export PATH="$JAVA_HOME/bin${path_without_java:+:$path_without_java}"
  export ANDROID_SDK_ROOT="$sdk_root"
  export ANDROID_HOME="$ANDROID_SDK_ROOT"

  echo "Container ready: JDK 21; Android SDK $AAB_ANDROID_PLATFORM; build-tools $AAB_ANDROID_BUILD_TOOLS; Gradle wrapper $gradle_version."
  echo "Run scripts/ladder.sh for the repository acceptance pipeline."
}

_aab_setup_container
_aab_setup_status=$?
unset -f _aab_setup_container
if [[ $_aab_setup_status -ne 0 ]]; then
  unset _aab_setup_status
  return 1
fi
unset _aab_setup_status
