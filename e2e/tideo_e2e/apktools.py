"""Local reads of APK files with the Android SDK's own tools, and the commit a build came from.

Runs exactly three local programs on local files: apkanalyzer and apksigner (on the JDK in
JAVA_HOME) and git. It never runs adb and never reaches the device, which is the only reason it
is exempt from the boundary tripwire (test_boundary_tripwire.py); test_install.py pins its argv.
A certificate's subject can name a person, so only SHA-256 digests leave this module.
"""

from __future__ import annotations

import os
import re
import subprocess
from pathlib import Path

from .scenarios import REPO_ROOT

TIMEOUT_S = 300  # the x86_64 JDK runs under emulation on the ARM hosts


class ToolError(RuntimeError):
    pass


def sdk_dir() -> Path:
    for var in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if value := os.environ.get(var):
            return Path(value)
    props = REPO_ROOT / "local.properties"
    if props.exists():
        if m := re.search(r"(?m)^sdk\.dir=(.+)$", props.read_text(encoding="utf-8")):
            return Path(m.group(1).strip())
    raise ToolError("no Android SDK: set ANDROID_HOME or sdk.dir in local.properties")


def _java_home() -> str:
    home = os.environ.get("JAVA_HOME")
    if not home:
        raise ToolError("set JAVA_HOME to a JDK 17+ for apkanalyzer and apksigner")
    return home


def _apksigner_jar(sdk: Path) -> Path:
    jars = sorted(sdk.glob("build-tools/*/lib/apksigner.jar"),
                  key=lambda p: [int(x) for x in re.findall(r"\d+", p.parent.parent.name)])
    if not jars:
        raise ToolError(f"no build-tools/*/lib/apksigner.jar under {sdk}")
    return jars[-1]


def argv_manifest(sdk: Path, apk: Path) -> list[str]:
    return [str(sdk / "cmdline-tools" / "latest" / "bin" / "apkanalyzer"), "manifest", "print",
            str(apk)]


def argv_signers(sdk: Path, apk: Path) -> list[str]:
    return [str(Path(_java_home()) / "bin" / "java"), "-jar", str(_apksigner_jar(sdk)),
            "verify", "--print-certs", str(apk)]


def _run(argv: list[str]) -> str:
    env = dict(os.environ, JAVA_HOME=_java_home())
    r = subprocess.run(argv, capture_output=True, text=True, timeout=TIMEOUT_S, env=env)
    if r.returncode != 0:
        # stderr may quote a certificate subject; report the tool and code only.
        raise ToolError(f"{Path(argv[0]).name} exited {r.returncode}")
    return r.stdout


def manifest_xml(apk: Path) -> str:
    return _run(argv_manifest(sdk_dir(), apk))


def signer_output(apk: Path) -> str:
    """apksigner verifies the signature and prints each signer; a bad signature exits non-zero."""
    return _run(argv_signers(sdk_dir(), apk))


def head_commit() -> str:
    head = subprocess.run(["git", "-C", str(REPO_ROOT), "rev-parse", "HEAD"], capture_output=True,
                          text=True, timeout=30)
    dirty = subprocess.run(["git", "-C", str(REPO_ROOT), "status", "--porcelain",
                            "--untracked-files=no"], capture_output=True, text=True, timeout=30)
    if head.returncode != 0:
        return "unknown"
    return head.stdout.strip()[:12] + ("+dirty" if dirty.stdout.strip() else "")
