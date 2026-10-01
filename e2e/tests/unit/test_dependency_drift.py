"""Drift alarm over the pinned device libraries, and proof the gates are actually installed."""

import hashlib
import tomllib
from importlib.metadata import version
from pathlib import Path

import adbutils
from adbutils import _adb, sync
from uiautomator2 import core

from tideo_e2e import device, ui
from tideo_e2e.drift import surface
from tideo_e2e.scenarios import E2E_ROOT

BASELINE = Path(__file__).with_name("dependency_surface.txt")


def test_installed_versions_are_the_pins():
    deps = tomllib.loads((E2E_ROOT / "pyproject.toml").read_text())["project"]["dependencies"]
    pins = dict(d.split("==") for d in deps)
    for name in ("adbutils", "uiautomator2"):
        assert version(name) == pins[name]


def test_dependency_surface_unchanged():
    current = surface()
    baseline = set(BASELINE.read_text().split())
    added = sorted(v for k, v in current.items() if k not in baseline)
    removed = len(baseline - set(current))
    assert not added and not removed, (
        "the device-facing lines of a pinned library changed; check each still passes a gate "
        f"(tideo_e2e/drift.py says how to rebaseline). {removed} removed, added:\n"
        + "\n".join(added)
    )


def test_gates_are_installed():
    assert _adb.AdbConnection.send_command is device._guarded_send_command
    assert sync.Sync._prepare_sync is device._guarded_prepare_sync
    assert core._http_request is ui._guarded_http_request
    for module in (adbutils, adbutils._utils, adbutils._adb, adbutils._device_base,
                   adbutils.screenrecord):
        assert module.adb_path is device._no_adb_binary


def test_u2_jar_is_the_pinned_asset():
    # The HARNESS grade lets uiautomator2 push and run this jar; its content is pinned here.
    jar = Path(core.__file__).with_name("assets") / "u2.jar"
    assert hashlib.sha256(jar.read_bytes()).hexdigest() == (
        "0b74e83c55f443539a9f76f5ce023a51466b764b1100e4097a897053fdfc0eb6")


def test_u2_constants_match_the_templates():
    assert core.DEFAULT_SERVER_PORT == device.U2_PORT
    assert f'"{device.U2_JAR}"' in Path(core.__file__).read_text()
