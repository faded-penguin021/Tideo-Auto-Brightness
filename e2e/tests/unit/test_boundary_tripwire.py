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
# drift: patterns only; apktools: local SDK tools and git on local files, never adb.
EXEMPT = {"tideo_e2e/device.py", "tideo_e2e/ui.py", "tideo_e2e/drift.py", "tideo_e2e/apktools.py"}


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


# device.install admits any payload its caller hands it; only the guard may call it (S4 review).
INSTALL_CALL = re.compile(r"\binstall\s*\(|\.install_size\b|exec:")
INSTALL_OWNERS = {"tideo_e2e/device.py", "tideo_e2e/install.py"}


def test_only_the_install_guard_installs():
    hits = []
    paths = [*(E2E_ROOT / "tideo_e2e").rglob("*.py"), *(E2E_ROOT / "tests").rglob("*.py")]
    for path in sorted(paths):
        rel = path.relative_to(E2E_ROOT).as_posix()
        if rel in INSTALL_OWNERS or rel.startswith("tests/unit/"):
            continue
        for n, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if INSTALL_CALL.search(line):
                hits.append(f"{rel}:{n}: {line.strip()}")
    assert hits == []
    assert INSTALL_CALL.search("device.install(run.s, blob)")
    assert not INSTALL_CALL.search("return run_install(argv[1])")
