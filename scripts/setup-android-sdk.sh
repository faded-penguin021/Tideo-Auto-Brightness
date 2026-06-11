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
