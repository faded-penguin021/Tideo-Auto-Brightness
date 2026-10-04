"""`run.sh --install <apk>`: the guarded install of a debug build (plan §3.8).

Refused unless the journal is empty. The new APK must be the debug package, signed by exactly the
certificate the installed debug package carries (apksigner on the base.apk pulled from the phone),
with a versionCode no lower than the installed one; the owner then approves after seeing both
sides. With no debug package on the phone at all, the first install's one signer becomes the one
every later install must match (owner, 2026-10-04). The install is `cmd package install -r`, streamed once; never -d, -g, -t or an uninstall.
"""

from __future__ import annotations

import hashlib
import json
import re
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Callable
from xml.etree import ElementTree

from . import apktools, device, state
from .device import DEBUG_PKG, INSTALLED_APK, Grade
from .journal import Journal

_ANDROID = "{http://schemas.android.com/apk/res/android}"
_DIGEST = re.compile(r"(?m)^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]{64})$")
RECORD = "installs.jsonl"


class InstallRefused(RuntimeError):
    pass


@dataclass(frozen=True)
class ApkFacts:
    package: str
    version_code: int
    version_name: str
    signers: tuple[str, ...]


def parse_manifest(xml: str) -> tuple[str, int, str]:
    root = ElementTree.fromstring(xml)
    code = root.get(f"{_ANDROID}versionCode", "")
    if root.tag != "manifest" or not code.isdigit():
        raise InstallRefused("apkanalyzer printed no manifest with a versionCode")
    return root.get("package", ""), int(code), root.get(f"{_ANDROID}versionName", "")


def parse_signers(out: str) -> tuple[str, ...]:
    return tuple(_DIGEST.findall(out))


def apk_facts(apk: Path) -> ApkFacts:
    package, code, name = parse_manifest(apktools.manifest_xml(apk))
    return ApkFacts(package, code, name, parse_signers(apktools.signer_output(apk)))


def installed_version_name(dump: str) -> str:
    m = re.search(r"versionName=(\S+)", state.package_section(dump, DEBUG_PKG))
    return m.group(1) if m else "?"


def installed_anywhere(dump: str) -> bool:
    """Whether `dumpsys package` lists the debug package for any user. Absence is only the
    platform's own line; any other output that does not parse is refused, never read as absent."""
    if dump.strip() == f"Unable to find package: {DEBUG_PKG}":
        return False
    try:
        state.package_section(dump, DEBUG_PKG)
    except state.StateError:
        raise InstallRefused("dumpsys package neither lists nor disowns the debug package")
    return True


def installed_apk_path(pm_path_output: str) -> str:
    """The single base.apk `pm path` names; split APKs are refused (one file is compared)."""
    paths = [ln.removeprefix("package:").strip() for ln in pm_path_output.splitlines() if ln]
    if len(paths) != 1 or not INSTALLED_APK.fullmatch(paths[0]):
        raise InstallRefused(f"expected one installed base.apk, got {len(paths)} path(s)")
    return paths[0]


def problems(new: ApkFacts, installed_code: int | None,
             installed_signers: tuple[str, ...] | None) -> list[str]:
    """None for both installed sides: a first install, judged on the new APK alone."""
    out = []
    if new.package != DEBUG_PKG:
        out.append(f"the APK is {new.package!r}, not {DEBUG_PKG}")
    if installed_signers is None or installed_code is None:
        if len(new.signers) != 1:
            out.append("the APK must have exactly one signer")
        return out
    if len(new.signers) != 1 or len(installed_signers) != 1:
        out.append("both sides must have exactly one signer")
    elif new.signers != installed_signers:
        out.append("STOP: the certificates differ; never uninstall to get past this")
    if new.version_code < installed_code:
        out.append(f"versionCode {new.version_code} is below the installed {installed_code}")
    return out


def _previous(store: Path, sha256: str) -> dict:
    path = store / RECORD
    if not path.exists():
        return {}
    rows = [json.loads(ln) for ln in path.read_text(encoding="utf-8").splitlines() if ln]
    return next((r for r in reversed(rows) if r.get("sha256") == sha256), {})


def run_install(apk: Path, target: str, store: Path, client,
                ask: Callable[[str], str] = input) -> int:
    if not sys.stdin.isatty() and ask is input:
        print("install: needs the owner at a terminal to approve", file=sys.stderr)
        return 2
    blob = apk.read_bytes()  # read once: the tools inspect a copy of exactly what is streamed
    with Journal.for_recovery(store) as journal:  # held, so no run starts during the install
        if not journal.empty:
            raise InstallRefused("the journal is not empty; run --recover first")
        candidate = store / "candidate.apk"
        candidate.write_bytes(blob)
        candidate.chmod(0o600)
        new = apk_facts(candidate)
        with device.session(target, frozenset({Grade.READ}), client) as s:
            dump = state.package_dump(s)
            first_install = not installed_anywhere(dump)
            if first_install:
                code = have_signers = pulled = None
            else:
                _first, code, _cert = state.package_identity(dump, DEBUG_PKG)
                name = installed_version_name(dump)
                r = device.run(s, "pm", "path", DEBUG_PKG)
                pulled = b"".join(s.device.sync.iter_content(installed_apk_path(r.output)))
        if pulled is not None:
            copy = store / "installed-base.apk"
            copy.write_bytes(pulled)
            copy.chmod(0o600)
            have_signers = parse_signers(apktools.signer_output(copy))
        if found := problems(new, code, have_signers):
            print("install: refused\n  " + "\n  ".join(found), file=sys.stderr)
            return 1
        sha_new = hashlib.sha256(blob).hexdigest()
        commit = apktools.head_commit()
        if first_install:
            print(f"installed  none: a first install; signer {new.signers[0][:16]} becomes the "
                  "one every later install must match")
        else:
            before = _previous(store, hashlib.sha256(pulled).hexdigest())
            print(f"installed  {name} vc{code}  commit {before.get('commit', 'unknown')}")
        print(f"new        {new.version_name} vc{new.version_code}  commit {commit}  "
              f"sha256 {sha_new[:16]}")
        if ask("Type INSTALL to replace the installed debug build: ").strip() != "INSTALL":
            print("install: not approved; nothing sent")
            return 1
        with device.session(target, frozenset(Grade), client) as s:
            out = device.install(s, blob)
            if "Success" not in out:
                raise InstallRefused(f"the package manager did not report Success: {out[:200]!r}")
            got = state.package_identity(state.package_dump(s), DEBUG_PKG)[1]
        if got != new.version_code:
            raise InstallRefused(f"installed versionCode reads {got}, expected {new.version_code}")
        with (store / RECORD).open("a", encoding="utf-8") as f:
            f.write(json.dumps({"sha256": sha_new, "commit": commit, "version_code": got,
                                "version_name": new.version_name, "time": int(time.time())})
                    + "\n")
    print(f"install: {new.version_name} vc{got} installed")
    return 0
