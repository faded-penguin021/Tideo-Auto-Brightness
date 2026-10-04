"""`run.sh --recover`: run the one recovery procedure against the journal in the private store.

The device is named by `TIDEO_E2E_SERIAL` (the adb serial, never written to a file) and reached
through the host adb server at `ADB_SERVER_HOST:ADB_SERVER_PORT`.
"""

from __future__ import annotations

import os
import sys

import adbutils
import uiautomator2

from . import device, state
from .device import Grade
from .journal import Journal, JournalError, private_store
from .recovery import RecoveryError, recover
from .tideo import PORT_UI, DevicePort
from .ui import Ui

ALL_GRADES = frozenset({Grade.READ, Grade.HARNESS, Grade.MUTATE})


def adb_client() -> adbutils.AdbClient:
    return adbutils.AdbClient(host=os.environ.get("ADB_SERVER_HOST", "host.docker.internal"),
                              port=int(os.environ.get("ADB_SERVER_PORT", "5037")))


def run_recover() -> int:
    store = private_store()
    try:
        with Journal.for_recovery(store) as journal:
            if journal.empty:
                journal.close_clean()
                print("recover: the journal is empty; nothing to do")
                return 0
            target = os.environ.get("TIDEO_E2E_SERIAL", "")
            if not target:
                print("recover: set TIDEO_E2E_SERIAL to the phone's adb serial", file=sys.stderr)
                return 2
            # Identity first, READ only: connecting uiautomator2 pushes and starts its server.
            with device.session(target, frozenset({Grade.READ}), adb_client()) as s:
                journal.check_identity(state.identity(s, store))
            with device.session(target, ALL_GRADES, adb_client()) as s:
                ui = Ui(s, uiautomator2.connect(s.device), PORT_UI)
                report = recover(journal, DevicePort(s, ui, store))
    except (JournalError, RecoveryError, state.StateError, device.BoundaryViolation) as e:
        print(f"recover: stopped, the journal keeps what is left: {e}", file=sys.stderr)
        return 1
    for line in report.restored:
        print(f"restored  {line}")
    for line in report.notes:
        print(f"note      {line}")
    for line in report.conflicts:
        print(f"CONFLICT  {line}")
    return 0 if report.clean else 1


def run_install(apk: str) -> int:
    from pathlib import Path

    from .apktools import ToolError
    from .install import InstallRefused, run_install as guarded

    target = os.environ.get("TIDEO_E2E_SERIAL", "")
    if not target:
        print("install: set TIDEO_E2E_SERIAL to the phone's adb serial", file=sys.stderr)
        return 2
    try:
        return guarded(Path(apk), target, private_store(), adb_client())
    except (InstallRefused, ToolError, JournalError, state.StateError,
            device.BoundaryViolation) as e:
        print(f"install: stopped: {e}", file=sys.stderr)
        return 1


def run_preflight() -> int:
    from . import preflight

    target = os.environ.get("TIDEO_E2E_SERIAL", "")
    if not target:
        print("preflight: set TIDEO_E2E_SERIAL to the phone's adb serial", file=sys.stderr)
        return 2
    confirmed = frozenset(c.strip() for c in os.environ.get("TIDEO_E2E_CONFIRM", "").split(",")
                          if c.strip())
    try:
        return preflight.run(target, private_store(), adb_client(), confirmed)
    except (JournalError, state.StateError, device.BoundaryViolation) as e:
        print(f"preflight: stopped: {e}", file=sys.stderr)
        return 1


def main(argv: list[str]) -> int:
    if argv == ["recover"]:
        return run_recover()
    if argv == ["preflight"]:
        return run_preflight()
    if len(argv) == 2 and argv[0] == "install":
        return run_install(argv[1])
    print("usage: python -m tideo_e2e.cli preflight | recover | install <apk>", file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
