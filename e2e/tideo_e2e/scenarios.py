"""scenarios.toml loader and the DEVICE_TEST_SCRIPT step extractor it is checked against."""

from __future__ import annotations

import re
import tomllib
from dataclasses import dataclass, field
from pathlib import Path

E2E_ROOT = Path(__file__).resolve().parent.parent
REPO_ROOT = E2E_ROOT.parent
SCRIPT = REPO_ROOT / "docs" / "rebuild" / "DEVICE_TEST_SCRIPT.md"
MANIFEST = E2E_ROOT / "scenarios.toml"

STATUSES = frozenset({"auto", "partial", "manual"})

# Coarse effect kinds; effects.INVENTORY maps each to concrete keys, flags and a run stage.
EFFECTS = frozenset({
    "read_only",            # reads only
    "ui_nav",               # navigates Tideo's UI, changes nothing
    "prefs_ui",             # changes a Tideo preference through the UI, restored after
    "service_toggle",       # service start/stop and their indirect writes
    "screen_wake",          # input keyevent SLEEP/WAKEUP; clears contextOverride, re-evaluates
    "settings_write",       # a journaled settings put
    "override_record",      # Tideo records the user adjustment (curve dot)
    "notification_action",  # Resume/Reset on Tideo's own notification
    "broadcast",            # am broadcast to the debug ControlReceiver
    "automation_events",    # outbound STATE_CHANGED broadcasts other apps may act on
    "grant",                # pm grant WRITE_SECURE_SETTINGS (debug package)
    "revoke",               # pm revoke WRITE_SECURE_SETTINGS (kills the process)
    "force_stop",           # am force-stop of the debug package
    "panic",                # PANIC and its writes
    "privileged_apply",     # Privileged Display Apply (secure/global keys)
})

_SECTION = re.compile(r"^## (\d+)\. ")
_STEP = re.compile(r"^(\d+[a-z]?)\. ")


def script_step_ids(path: Path = SCRIPT) -> list[str]:
    """Every numbered step at line start, keyed by its section: `s02_10b`."""
    ids: list[str] = []
    section: int | None = None
    for line in path.read_text(encoding="utf-8").splitlines():
        if m := _SECTION.match(line):
            section = int(m.group(1))
        elif section is not None and (m := _STEP.match(line)):
            ids.append(f"s{section:02d}_{m.group(1)}")
    return ids


_POINTER = re.compile(r"^E2E auto: (.*)$")
_POINTER_ID = re.compile(r"`(s\d{2}_\w+)`")


def script_e2e_pointers(path: Path = SCRIPT) -> dict[int, list[list[str]]]:
    """Each section's `E2E auto:` lines, as the ids each names, keyed by section number."""
    out: dict[int, list[list[str]]] = {}
    section: int | None = None
    for line in path.read_text(encoding="utf-8").splitlines():
        if m := _SECTION.match(line):
            section = int(m.group(1))
            out[section] = []
        elif section is not None and (m := _POINTER.match(line)):
            out[section].append(_POINTER_ID.findall(m.group(1)))
    return out


@dataclass(frozen=True)
class Scenario:
    id: str
    status: str
    effects: tuple[str, ...] = ()
    test: str | None = None
    pending: bool = False
    extra: bool = False
    reason: str | None = None
    note: str | None = None
    unknown: tuple[str, ...] = field(default=())


_TYPES = {
    "id": str, "status": str, "effects": list, "test": str,
    "pending": bool, "extra": bool, "reason": str, "note": str,
}


class ManifestError(ValueError):
    pass


def load(path: Path = MANIFEST) -> list[Scenario]:
    doc = tomllib.loads(path.read_text(encoding="utf-8"))
    if set(doc) != {"scenario"}:
        raise ManifestError(f"top-level keys must be exactly [[scenario]], got {sorted(doc)}")
    out = []
    for row in doc["scenario"]:
        for key, kind in _TYPES.items():
            value = row.get(key)
            if value is None:
                continue
            if not isinstance(value, kind) or (kind is str and not value.strip()):
                raise ManifestError(f"{row.get('id')}: {key} must be a non-empty {kind.__name__}")
            if kind is list and not all(isinstance(e, str) for e in value):
                raise ManifestError(f"{row.get('id')}: effects must be strings")
        known = {k: v for k, v in row.items() if k in _TYPES}
        known["effects"] = tuple(known.get("effects", ()))
        out.append(Scenario(**known, unknown=tuple(sorted(set(row) - set(_TYPES)))))
    return out
