"""Harness code reaches the phone only through the gated public APIs.

The guard in device.py is not a sandbox: code that writes to a raw adb socket, calls the saved
originals, spawns a process or starts uiautomator2's agent CLI (a fresh, unpatched interpreter)
would pass around it. Only the boundary modules and their own tests may touch those.
"""

import re

from tideo_e2e.scenarios import E2E_ROOT

BYPASS = re.compile(
    r"_orig_|\.conn\b|send_command|create_connection|_prepare_sync|\bsocket\b|subprocess"
    r"|os\.system|os\.popen|os\.exec|agent_cli|_http_request|_jsonrpc_call|\.jsonrpc\b"
)
EXEMPT = {"tideo_e2e/device.py", "tideo_e2e/ui.py", "tideo_e2e/drift.py"}  # drift: patterns only


def test_no_harness_code_bypasses_the_boundary():
    hits = []
    paths = [*(E2E_ROOT / "tideo_e2e").rglob("*.py"), *(E2E_ROOT / "tests").rglob("*.py")]
    for path in sorted(paths):
        rel = path.relative_to(E2E_ROOT).as_posix()
        if rel in EXEMPT or rel.startswith("tests/unit/"):
            continue
        for n, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if BYPASS.search(line):
                hits.append(f"{rel}:{n}: {line.strip()}")
    assert hits == []


def test_tripwire_catches_a_bypass():
    assert BYPASS.search("conn = dev.open_transport(); conn.send_command('shell:pm clear x')")
    assert BYPASS.search("device._orig_send_command(c, 'host:kill')")
    assert BYPASS.search("import subprocess")
