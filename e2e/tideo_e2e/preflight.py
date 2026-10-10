"""`run.sh --preflight`: strictly read-only checks before any device scenario.

One READ-grade session: connectivity, identity, package and signer, the release build's service,
the app language, the settings/runtime/private-state snapshot, the SKIP rules per scenario, and
an archive of Tideo's data dir. Raw values and the archive go to the private store only; stdout
carries settings values, versions and verdicts, never a serial or a path under /data.
"""

from __future__ import annotations

import hashlib
import json
import re
import time
from dataclasses import dataclass, field
from pathlib import Path

from . import device, harness, state
from .device import DEBUG_PKG, RELEASE_PKG, Grade
from .effects import EVERY_TIDEO_KEY, SNAPSHOT_KEYS, DeviceFacts, footprint, skip_reason
from .journal import Journal
from .scenarios import load

ARCHIVE_DIRS = ("files", "shared_prefs", "no_backup", "databases")
_PRIVATE_DIGESTS = (state.SETTINGS_STORE, state.CONTROL_STORE, state.SAVED_MODE_STORE,
                    state.OVERRIDE_POINTS_STORE, state.CONTEXT_RULES_STORE, state.BASELINE_STORE)


@dataclass
class Report:
    refusals: list[str] = field(default_factory=list)
    lines: list[str] = field(default_factory=list)
    skips: dict[str, str | None] = field(default_factory=dict)
    snapshot: dict = field(default_factory=dict)

    def refuse(self, why: str) -> None:
        self.refusals.append(why)

    def note(self, line: str) -> None:
        self.lines.append(line)


# ── pure parsers ────────────────────────────────────────────────────────────────────────────


def app_locales(out: str) -> list[str]:
    """`cmd locale get-app-locales`: "Locales for <pkg> for user 0 are [zh-Hans]"; [] unset."""
    m = re.fullmatch(r"Locales for \S+ for user 0 are \[([^\]]*)\]", out.strip())
    if not m:
        raise state.StateError(f"unexpected get-app-locales output: {out.strip()[:120]!r}")
    return [t for t in m.group(1).split(",") if t]


def language_problem(app: list[str], system: list[str]) -> str | None:
    """Why Tideo may not render English, or None. Resolution walks the app's own list, then the
    system's. Tideo ships English (values/) and zh-Hans, and library resources weigh in too, so
    anything short of certain is refused: English first, or no Chinese anywhere, is certain.
    SDK 31–32 has no `cmd locale`; that read fails and preflight stops, which is safe."""
    chain = [t.strip() for t in app + system]
    if not system or not all(re.fullmatch(r"[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*", t) for t in chain):
        return f"the language lists are missing or unreadable: app {app}, system {system}"
    langs = [t.split("-")[0].lower() for t in chain]
    if langs[0] == "en" or "zh" not in langs:
        return None
    return (f"Tideo's language resolves through {','.join(chain)}; the suite reads English "
            "text, so set Tideo's language to English (Misc → Language) before running")


# ── the check ───────────────────────────────────────────────────────────────────────────────


_out = state._out


def _digest(blob: bytes | None) -> str | None:
    return None if blob is None else hashlib.sha256(blob).hexdigest()


def check(s: device.Session, store: Path, confirmed: frozenset[str]) -> Report:
    rep = Report()
    sdk = _out(s, "getprop", "ro.build.version.sdk").strip()
    model = state.model(s)
    rep.note(f"device     model {model}, SDK {sdk}")
    profile = harness.profile_for(model)
    if profile is None:
        rep.refuse(f"no device profile e2e/devices/{model}.toml: the stored scale S is unknown")

    # The release build: the harness may force-stop it only while its service runs (owner).
    try:
        rel = state.package_dump(s, RELEASE_PKG)
        _f, rel_code, _c = state.package_identity(rel, RELEASE_PKG)
        rep.note(f"release    installed vc{rel_code}, service "
                 + ("RUNNING: it writes brightness alongside the debug build"
                    if state.service_running(s, RELEASE_PKG) else "not running"))
    except state.StateError:
        rep.note("release    not installed")

    try:
        identity = state.identity(s, store)
    except state.StateError as e:
        rep.refuse(f"{e}; install a debug build first with e2e/run.sh --install <apk>")
        return rep
    dump = state.package_dump(s)
    name = re.search(r"versionName=(\S+)", state.package_section(dump, DEBUG_PKG))
    rep.note(f"debug      {name.group(1) if name else '?'} vc{identity.version_code}, "
             f"signer {identity.cert_digest[:8]}…")
    paths = [ln for ln in device.run(s, "pm", "path", DEBUG_PKG).output.splitlines() if ln]
    if len(paths) != 1:
        rep.refuse(f"pm path lists {len(paths)} APKs; the install guard compares one base.apk")

    app = app_locales(_out(s, "cmd", "locale", "get-app-locales", DEBUG_PKG, "--user", "0"))
    system = (state.read_setting(s, "system", "system_locales") or "").split(",")
    system = [t for t in system if t] or [_out(s, "getprop", "persist.sys.locale").strip()]
    rep.note(f"language   app [{','.join(app)}], system [{','.join(system)}]")
    if why := language_problem(app, system):
        rep.refuse(why)

    settings = {f"{ns}/{key}": state.read_setting(s, ns, key) for ns, key in sorted(SNAPSHOT_KEYS)}
    absent = frozenset(k for k in EVERY_TIDEO_KEY if settings[f"{k[0]}/{k[1]}"] is None)
    brightness = settings["system/screen_brightness"]
    if profile is not None and brightness is not None and int(brightness) > profile.stored_max:
        rep.refuse(f"screen_brightness {brightness} exceeds the profile's S {profile.stored_max}")

    prefs = state.read_prefs(s, state.PREF_DEFAULTS)
    running = state.service_running(s)
    enabled = prefs["aab_settings/serviceEnabled"] == "true"
    notifications = _out(s, "dumpsys", "notification")
    # Read in English only: a Chinese notification would read as unpaused (S5).
    paused = None if rep.refusals else state.paused_in_dump(notifications, DEBUG_PKG)
    contexts = state.context_state(s)
    points = state.override_points(s)
    grant = state.grant_state(dump, DEBUG_PKG)
    rep.note(f"runtime    service {'running' if running else 'stopped'}, serviceEnabled "
             f"{enabled}, paused {paused}, screen {'on' if state.awake(s) else 'off'}")
    rep.note(f"tideo      grant {grant}, context state {contexts}, curve points {len(points)}, "
             f"automation {prefs['control_prefs/external_control_enabled']}, force-dark "
             f"{prefs['control_prefs/force_dark_enabled']}")
    if absent:
        rep.note("absent     " + ", ".join(f"{ns}/{k}" for ns, k in sorted(absent)))
    if enabled != running:
        rep.note("mismatch   serviceEnabled and the running service disagree; runtime scenarios "
                 "SKIP until the owner starts or stops it")

    facts = DeviceFacts(
        absent_rows=absent, context_state=contexts,
        unrestorable_rows=frozenset(
            k for k in EVERY_TIDEO_KEY if (v := settings[f"{k[0]}/{k[1]}"]) is not None
            and not device.restorable(*k, v)),
        automation_on=prefs["control_prefs/external_control_enabled"] == "true",
        force_dark_opt_in=prefs["control_prefs/force_dark_enabled"] == "true",
        confirmed=confirmed,
    )
    for row in load():
        if row.test and not row.pending:
            reason = skip_reason(row.effects, facts)
            if reason is None and enabled != running and footprint(row.effects).runtime:
                reason = "serviceEnabled and the running service disagree"
            rep.skips[row.id] = reason

    rep.snapshot = {
        "settings": settings, "prefs": prefs, "grant": grant, "service_running": running,
        "paused": paused, "context_state": contexts, "override_points": len(points),
        "saved_mode": state.saved_mode(s), "version_code": identity.version_code,
        "digests": {p: _digest(state.private_file(s, p)) for p in _PRIVATE_DIGESTS},
    }
    archive = device.run(s, "run-as", DEBUG_PKG, "tar", "cf", "-", *ARCHIVE_DIRS, raw=True)
    rep.snapshot["archive"] = _save_archive(store, archive.output, archive.returncode)
    return rep


def _save_archive(store: Path, blob: bytes, code: int) -> dict:
    """The data-dir tar, for owner-driven recovery only; the harness never writes it back.
    toybox tar exits 1 when one of the dirs does not exist yet and still archives the rest."""
    path = store / f"preflight-{time.strftime('%Y%m%dT%H%M%S')}.tar"
    path.write_bytes(blob)
    path.chmod(0o600)
    return {"file": path.name, "bytes": len(blob), "exit": code,
            "sha256": hashlib.sha256(blob).hexdigest()}


def run(target: str, store: Path, client, confirmed: frozenset[str]) -> int:
    with Journal.for_recovery(store) as journal:  # held: no run starts while we look
        if not journal.empty:
            print("preflight: REFUSED — a journal is pending; run e2e/run.sh --recover first")
            return 1
        with device.session(target, frozenset({Grade.READ}), client) as s:
            rep = check(s, store, confirmed)
            log = [f"{verdict} {req}" for verdict, req in s.log]
    stamp = time.strftime("%Y%m%dT%H%M%S")
    record = store / f"preflight-{stamp}.json"
    record.write_text(json.dumps({"refusals": rep.refusals, "skips": rep.skips,
                                  "snapshot": rep.snapshot, "commands": log}, indent=1))
    record.chmod(0o600)
    for line in rep.lines:
        print(line)
    if rep.skips:
        runnable = sorted(i for i, r in rep.skips.items() if r is None)
        print(f"scenarios  {len(runnable)} of {len(rep.skips)} would run: {' '.join(runnable)}")
        for sid, reason in sorted(rep.skips.items()):
            if reason:
                print(f"  SKIP {sid}: {reason}")
    for why in rep.refusals:
        print(f"REFUSED    {why}")
    print(f"preflight: {len(log)} adb requests, all READ; record {record.name} in the private store")
    return 1 if rep.refusals else 0
