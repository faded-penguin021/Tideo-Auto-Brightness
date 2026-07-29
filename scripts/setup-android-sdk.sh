#!/usr/bin/env bash
# Idempotent Android SDK bootstrap for remote agent containers.
# Usage: scripts/setup-android-sdk.sh   (takes ~3-5 min on first run, instant after)
# Installs commandline-tools + platform-tools + compile SDK 36 + build-tools 35.0.0
# into $HOME/android-sdk and writes local.properties at the repo root.
set -eu

SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
source "$REPO_DIR/scripts/build-toolchain.sh"
PLATFORM_PACKAGE="platforms;android-$AAB_ANDROID_PLATFORM"
BUILD_TOOLS_PACKAGE="build-tools;$AAB_ANDROID_BUILD_TOOLS"

write_local_properties() {
    printf 'sdk.dir=%s\n' "$SDK_ROOT" > "$REPO_DIR/local.properties"
}

# The wrapper's distribution download is blocked in Claude Code cloud sessions
# (services.gradle.org redirects to github releases, which the egress proxy denies with 403),
# but the trusted container image pre-installs a version-matched Gradle at /opt/gradle-<version>.
# Seed the wrapper dist cache from it so the documented `./gradlew` commands work. This selects the
# wrapper-declared version; it is not a separate cryptographic provenance check. Idempotent;
# silently no-ops on machines without /opt/gradle-<version> (e.g. a dev laptop with network).
seed_gradle_wrapper() {
    local props="$REPO_DIR/gradle/wrapper/gradle-wrapper.properties"
    [ -f "$props" ] || return 0
    local version
    version="$(sed -n 's/^distributionUrl=.*gradle-\(.*\)-bin\.zip$/\1/p' "$props")"
    { [ -n "$version" ] && [ -d "/opt/gradle-$version" ]; } || return 0
    local base="$HOME/.gradle/wrapper/dists/gradle-$version-bin"
    if find "$base" -maxdepth 2 -name '*.zip.ok' 2>/dev/null | grep -q .; then return 0; fi
    # Let the wrapper create its URL-hash directory (its download attempt fails — expected).
    (cd "$REPO_DIR" && timeout 90 ./gradlew --version >/dev/null 2>&1) || true
    local hashdir
    hashdir="$(find "$base" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | head -1)"
    [ -n "$hashdir" ] || return 0
    if [ ! -d "$hashdir/gradle-$version" ]; then
        cp -al "/opt/gradle-$version" "$hashdir/gradle-$version" 2>/dev/null ||
            cp -a "/opt/gradle-$version" "$hashdir/gradle-$version"
    fi
    touch "$hashdir/gradle-$version-bin.zip.ok"
    echo "[setup-android-sdk] gradle wrapper cache seeded from /opt/gradle-$version"
}

seed_gradle_wrapper

if [ -d "$SDK_ROOT/platforms/android-$AAB_ANDROID_PLATFORM" ] &&
   [ -x "$SDK_ROOT/platform-tools/adb" ] &&
   [ -x "$SDK_ROOT/build-tools/$AAB_ANDROID_BUILD_TOOLS/apksigner" ]; then
    write_local_properties
    echo "[setup-android-sdk] SDK already present at $SDK_ROOT; local.properties refreshed."
    exit 0
fi

start="$(date +%s)"
echo "[setup-android-sdk] installing SDK into $SDK_ROOT ..."

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
curl -fsSL "$AAB_ANDROID_CLT_URL" -o "$tmp/clt.zip"
unzip -q "$tmp/clt.zip" -d "$tmp"
mkdir -p "$SDK_ROOT/cmdline-tools"
rm -rf "$SDK_ROOT/cmdline-tools/latest"
mv "$tmp/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"

SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
# `yes` dies of SIGPIPE when sdkmanager exits; the subshell || true keeps set -e happy.
(yes || true) | "$SDKMANAGER" --sdk_root="$SDK_ROOT" --licenses > /dev/null
"$SDKMANAGER" --sdk_root="$SDK_ROOT" "platform-tools" "$PLATFORM_PACKAGE" "$BUILD_TOOLS_PACKAGE" > /dev/null

write_local_properties
echo "[setup-android-sdk] done in $(( $(date +%s) - start ))s. sdk.dir=$SDK_ROOT"
