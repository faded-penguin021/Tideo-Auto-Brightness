#!/usr/bin/env bash
# Installs a pinned, checksum-verified uv into ~/.local/bin, then syncs the locked environment.
# No apt packages, no Linux adb: the suite talks to the host adb server (README "Environment").
set -euo pipefail

UV_VERSION=0.12.21
declare -A TARBALL_SHA256=(
  [aarch64]=030b69227b40af8c1981b7301793dc66e71ed3c796ea8688209dd268bd91ec51
  [x86_64]=23f02075b652bb1df64178cfae41b5caf160822e720e2663568f3f5d63bc52c0
)
# The extracted binary's own hash, so an existing ~/.local/bin/uv is trusted by content, not by
# what it prints.
declare -A BINARY_SHA256=(
  [aarch64]=c1fd7d7e0d9b90a3109256bd84496896d0230722e7704085b1375b8bf5aa091a
  [x86_64]=e8a4e7b4fd6283892fccfc4f335fb16cdf01064bb135172e4e2e73b478eb2076
)

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
uv="$HOME/.local/bin/uv"
arch="$(uname -m)"
[[ -n "${TARBALL_SHA256[$arch]:-}" ]] || { echo "bootstrap: unsupported arch $arch" >&2; exit 2; }

binary_ok() { echo "${BINARY_SHA256[$arch]}  $uv" | sha256sum -c --quiet - 2>/dev/null; }

if ! binary_ok; then
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT
  name="uv-$arch-unknown-linux-gnu"
  curl -fsSL -o "$tmp/$name.tar.gz" \
    "https://github.com/astral-sh/uv/releases/download/$UV_VERSION/$name.tar.gz"
  echo "${TARBALL_SHA256[$arch]}  $tmp/$name.tar.gz" | sha256sum -c --quiet -
  tar -xzf "$tmp/$name.tar.gz" -C "$tmp"
  mkdir -p "$(dirname "$uv")"
  install -m 0755 "$tmp/$name/uv" "$uv"
  binary_ok || { echo "bootstrap: installed uv does not match its pinned hash" >&2; exit 1; }
fi

"$uv" --version
cd "$here"
"$uv" sync --frozen
