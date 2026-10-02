#!/usr/bin/env bash
# Entry point: --recover, or pytest passthrough. --preflight arrives with S5.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
uv="$HOME/.local/bin/uv"
[[ -x "$uv" ]] || { echo "run.sh: uv missing; run e2e/bootstrap.sh first" >&2; exit 2; }

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
