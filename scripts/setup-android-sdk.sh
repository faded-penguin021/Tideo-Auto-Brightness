#!/usr/bin/env bash
# Idempotent Android SDK bootstrap for Claude Code cloud sessions.
# Usage: scripts/setup-android-sdk.sh   (takes ~3-5 min on first run, instant after)
# Installs commandline-tools + platform-tools + android-35 + build-tools 35.0.0
# into $HOME/android-sdk and writes local.properties at the repo root.
set -eu

SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
CLT_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

write_local_properties() {
    printf 'sdk.dir=%s\n' "$SDK_ROOT" > "$REPO_DIR/local.properties"
}

# The wrapper's distribution download is blocked in Claude Code cloud sessions
# (services.gradle.org redirects to github releases, which the egress proxy denies with 403),
# but the container image pre-installs the exact pinned Gradle at /opt/gradle-<version>.
# Seed the wrapper dist cache from it so the documented `./gradlew` commands work. Idempotent;
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

if [ -d "$SDK_ROOT/platforms/android-35" ] && [ -x "$SDK_ROOT/platform-tools/adb" ]; then
    write_local_properties
    echo "[setup-android-sdk] SDK already present at $SDK_ROOT; local.properties refreshed."
    exit 0
fi

start="$(date +%s)"
echo "[setup-android-sdk] installing SDK into $SDK_ROOT ..."

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
curl -fsSL "$CLT_URL" -o "$tmp/clt.zip"
unzip -q "$tmp/clt.zip" -d "$tmp"
mkdir -p "$SDK_ROOT/cmdline-tools"
rm -rf "$SDK_ROOT/cmdline-tools/latest"
mv "$tmp/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"

SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
# `yes` dies of SIGPIPE when sdkmanager exits; the subshell || true keeps set -e happy.
(yes || true) | "$SDKMANAGER" --sdk_root="$SDK_ROOT" --licenses > /dev/null
"$SDKMANAGER" --sdk_root="$SDK_ROOT" "platform-tools" "platforms;android-35" "build-tools;35.0.0" > /dev/null

write_local_properties
echo "[setup-android-sdk] done in $(( $(date +%s) - start ))s. sdk.dir=$SDK_ROOT"
