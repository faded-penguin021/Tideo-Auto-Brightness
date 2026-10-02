"""Harness code reaches the phone only through the gated public APIs.

The guard in device.py is not a sandbox: code that writes to a raw adb socket, calls the saved
originals, spawns a process or starts uiautomator2's agent CLI (a fresh, unpatched interpreter)
would pass around it. Only the boundary modules and their own tests may touch those.
"""

import ast
import re

import pytest

from tideo_e2e.scenarios import E2E_ROOT

BYPASS = re.compile(
    r"_orig_|\.conn\b|send_command|create_connection|_prepare_sync|\bsocket\b|subprocess"
    r"|os\.system|os\.popen|os\.exec|os\.spawn|os\.posix_spawn|os\.fork|from\s+os\s+import"
    r"|\bpty\b|multiprocessing|asyncio\.create_subprocess|__import__|importlib"
    r"|agent_cli|_http_request|_jsonrpc_call|\.jsonrpc\b"
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
    assert BYPASS.search("from os import system")
    assert BYPASS.search("from os import system as run_it")


# device.install admits any payload its caller hands it; only the guard may reach it (S4 review).
# Parsed, not grepped: an import of the name under any alias or line break, an attribute named
# install, a string naming it (getattr), and the request it sends all count.
INSTALL_OWNERS = {"tideo_e2e/device.py", "tideo_e2e/install.py"}


def install_reaches(source: str) -> list[int]:
    lines = []
    for node in ast.walk(ast.parse(source)):
        if isinstance(node, ast.ImportFrom) and any(a.name == "install" for a in node.names):
            lines.append(node.lineno)
        elif isinstance(node, ast.Attribute) and node.attr in ("install", "install_size"):
            lines.append(node.lineno)
        elif isinstance(node, ast.Call) and getattr(node.func, "id", "") == "getattr" \
                and any(isinstance(a, ast.Constant) and a.value == "install" for a in node.args):
            lines.append(node.lineno)
        elif isinstance(node, ast.Constant) and isinstance(node.value, str) \
                and "exec:" in node.value:
            lines.append(node.lineno)
    return lines


def test_only_the_install_guard_installs():
    hits = []
    paths = [*(E2E_ROOT / "tideo_e2e").rglob("*.py"), *(E2E_ROOT / "tests").rglob("*.py")]
    for path in sorted(paths):
        rel = path.relative_to(E2E_ROOT).as_posix()
        if rel in INSTALL_OWNERS or rel.startswith("tests/unit/"):
            continue
        hits += [f"{rel}:{n}" for n in install_reaches(path.read_text(encoding="utf-8"))]
    assert hits == []


@pytest.mark.parametrize("source", [
    "device.install(run.s, blob)",
    "from tideo_e2e.device import install as put_apk",
    "from tideo_e2e.device import (\n    install as put_apk,\n)",
    "put = getattr(device, 'install')",
    "c.send_command(f'exec:cmd package install -S {n}')",
])
def test_install_reaches_are_found(source):
    assert install_reaches(source)


def test_the_cli_dispatch_is_not_a_reach():
    assert not install_reaches("return run_install(argv[1])")
