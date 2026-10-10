"""Dependency drift alarm: the lines of adbutils and uiautomator2 that reach a device or a process.

A drift alarm, not proof of confinement: device.py and ui.py gate the chokepoints these lines
lead to. When a pin moves, the fingerprint changes, and whoever bumps it reads the new lines and
re-checks that they still pass through a gate before regenerating the baseline:

    cd e2e && ~/.local/bin/uv run --frozen python -m tideo_e2e.drift \
        > tests/unit/dependency_surface.txt

Fingerprints are hashes of (file, stripped line), so the baseline carries no upstream text.
"""

from __future__ import annotations

import hashlib
import importlib.util
import re
import sys
from pathlib import Path

PACKAGES = ("adbutils", "uiautomator2")

SURFACE = re.compile(
    r"send_command\(|\.conn\.send|\bsendall\(|socket\.socket\(|create_connection\("
    r"|subprocess|os\.system|os\.popen|adb_path\(|_http_request\(|_jsonrpc_call\("
    r"|install|uninstall|\bclear\b|\brm\b|settings (put|delete|reset)|setprop|reboot|\broot\b"
    r"|force-stop|\bpm\b|\bam\b|\bime\b|\bbmgr\b|\bcontent\b"
)


def surface() -> dict[str, str]:
    """fingerprint → 'package/relpath: line' for every surface line in the pinned packages."""
    out = {}
    for name in PACKAGES:
        root = Path(importlib.util.find_spec(name).origin).parent
        for path in sorted(root.rglob("*.py")):
            rel = f"{name}/{path.relative_to(root).as_posix()}"
            for line in path.read_text(encoding="utf-8").splitlines():
                stripped = line.strip()
                if stripped and not stripped.startswith("#") and SURFACE.search(stripped):
                    key = hashlib.sha256(f"{rel}\t{stripped}".encode()).hexdigest()[:16]
                    out[key] = f"{rel}: {stripped}"
    return out


if __name__ == "__main__":
    sys.stdout.write("".join(f"{k}\n" for k in sorted(surface())))
