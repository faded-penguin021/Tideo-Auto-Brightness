"""Identifier-shape scan: a tripwire for personal identifiers in e2e/, not proof of absence.

Owner rule (2026-09-13): no Windows username, email, device serial or other personal identifier
in any file under e2e/. Placeholders are written as <angle-bracketed> names.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

_PATTERNS: dict[str, re.Pattern[str]] = {
    "email": re.compile(r"[\w.+-]+@[\w-]+\.[\w.-]+"),
    "private_ipv4": re.compile(
        r"\b(?:10|192\.168|172\.(?:1[6-9]|2\d|3[01]))(?:\.\d{1,3}){1,2}\b"
    ),
    "adb_wireless": re.compile(r"\b\d{1,3}(?:\.\d{1,3}){3}:\d{4,5}\b"),
    "windows_user_path": re.compile(r"(?i)[a-z]:[\\/]+users[\\/]+(?!<)[^\\/\s\"'`]+"),
    "unix_home_path": re.compile(r"/(?:home|Users)/(?!<)[A-Za-z0-9._-]+"),
    # Mixed upper-case/digit token of serial length.
    "serial_token": re.compile(r"\b(?=[A-Z0-9]*\d)(?=[A-Z0-9]*[A-Z])[A-Z0-9]{8,20}\b"),
    # Any token after a serial context: adb -s X, ANDROID_SERIAL=X, serial = "X", "serial": "X".
    "serial_context": re.compile(
        r"(?:\badb\s+-s\s+|\bANDROID_SERIAL[\"']?\s*[=:]\s*[\"']?|\bserial[\"']?\s*[=:]\s*[\"']?)"
        r"(?!<|\$)[\w.:-]{6,}"
    ),
    # A pasted `adb devices` line: <serial><TAB>device.
    "adb_devices_line": re.compile(r"^\s*[\w.:-]{6,}\t(?:device|unauthorized|offline)\b"),
    "ssid_context": re.compile(r"(?i)\bssid[\"']?\s*[=:]\s*[\"'](?!<)[^\"']+[\"']"),
}

# Literals that match a shape but are not identifiers.
_ALLOWED = {"127.0.0.1", "0.0.0.0"}

# Ends a line holding a synthetic example; honoured only in FIXTURE_FILE.
FIXTURE_PRAGMA = "# identifier-fixture"
FIXTURE_FILE = "tests/unit/test_identifiers.py"

SKIP_DIRS = {".venv", ".state", "evidence", "__pycache__", ".pytest_cache"}
SKIP_FILES = {"uv.lock"}  # machine-generated hashes; dependency names only


@dataclass(frozen=True)
class Hit:
    path: str
    line: int
    kind: str


def scan_text(text: str, label: str = "<text>", allow_fixtures: bool = False) -> list[Hit]:
    hits = []
    for n, line in enumerate(text.splitlines(), 1):
        if allow_fixtures and line.rstrip().endswith(FIXTURE_PRAGMA):
            continue
        for kind, pattern in _PATTERNS.items():
            if any(m.group(0) not in _ALLOWED for m in pattern.finditer(line)):
                hits.append(Hit(label, n, kind))
    return hits


def scan_tree(root: Path) -> list[Hit]:
    hits = []
    for path in sorted(root.rglob("*")):
        rel = path.relative_to(root)
        if path.is_dir() or SKIP_DIRS.intersection(rel.parts) or rel.as_posix() in SKIP_FILES:
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            hits.append(Hit(str(rel), 0, "unscannable_binary"))
            continue
        hits.extend(scan_text(text, str(rel), allow_fixtures=rel.as_posix() == FIXTURE_FILE))
    return hits
