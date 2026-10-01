#!/usr/bin/env bash
# Entry point: pytest passthrough. --preflight and --recover arrive with their segments.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
uv="$HOME/.local/bin/uv"
[[ -x "$uv" ]] || { echo "run.sh: uv missing; run e2e/bootstrap.sh first" >&2; exit 2; }

case "${1:-}" in
  --preflight|--recover)
    echo "run.sh: $1 is not implemented yet" >&2
    exit 2
    ;;
  pytest) shift ;;
esac

cd "$here"
exec "$uv" run --frozen pytest "$@"
