#!/usr/bin/env bash
# Entry point: --recover, --install <apk>, or pytest passthrough. --preflight arrives with S5.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
uv="$HOME/.local/bin/uv"
[[ -x "$uv" ]] || { echo "run.sh: uv missing; run e2e/bootstrap.sh first" >&2; exit 2; }

case "${1:-}" in
  --install)
    [[ $# -eq 2 ]] || { echo "usage: run.sh --install <apk>" >&2; exit 2; }
    apk="$(cd "$(dirname "$2")" && pwd)/$(basename "$2")"
    cd "$here"
    exec "$uv" run --frozen python -m tideo_e2e.cli install "$apk"
    ;;
esac

cd "$here"
case "${1:-}" in
  --preflight)
    echo "run.sh: --preflight is not implemented yet" >&2
    exit 2
    ;;
  --recover)
    exec "$uv" run --frozen python -m tideo_e2e.cli recover
    ;;
  pytest) shift ;;
esac

exec "$uv" run --frozen pytest "$@"
