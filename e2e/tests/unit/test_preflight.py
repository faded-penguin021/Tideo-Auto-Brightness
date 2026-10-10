"""`run.sh --preflight`: the language rule, and a whole check through the real boundary."""

from __future__ import annotations

import json

import pytest

from tideo_e2e import cli, preflight, state
from tideo_e2e.device import DEBUG_PKG, RELEASE_PKG, TEMPLATES, Grade, admit_shell
from tideo_e2e.effects import SNAPSHOT_KEYS
from tideo_e2e.journal import SETTING, Identity, Journal

from .fake_adb import FakeAdbServer
from .test_state import PACKAGE_DUMP

SERIAL = "emu01"


@pytest.mark.parametrize("out,tags", [
    (f"Locales for {DEBUG_PKG} for user 0 are []\n", []),
    (f"Locales for {DEBUG_PKG} for user 0 are [en]\n", ["en"]),
    (f"Locales for {DEBUG_PKG} for user 0 are [zh-Hans,en]\n", ["zh-Hans", "en"]),
])
def test_app_locales(out, tags):
    assert preflight.app_locales(out) == tags


def test_app_locales_refuses_another_shape():
    with pytest.raises(state.StateError):
        preflight.app_locales(f"Unknown package {DEBUG_PKG} for userId 0\n")


@pytest.mark.parametrize("app,system,ok", [
    ([], ["nl-NL"], True),             # no Dutch strings: English fallback
    ([], ["en-US", "zh-CN"], True),
    (["en"], ["zh-CN"], True),         # the app's own choice wins
    (["zh-Hans"], ["en-US"], False),
    ([], ["zh-CN"], False),
    ([], ["nl-NL", "zh-CN"], False),   # a later Chinese entry may win; not certain, so refused
    (["qaa"], ["zh-CN"], False),       # an unsupported app locale falls through to the system's
    ([], [], False),
    ([], [""], False),                 # no evidence is not English
    (["en"], [], False),
])
def test_language_problem(app, system, ok):
    assert (preflight.language_problem(app, system) is None) == ok


def _cat(path):
    return f"run-as {DEBUG_PKG} cat {path}"


@pytest.fixture
def server(tmp_path, monkeypatch):
    s = FakeAdbServer()
    monkeypatch.setenv("XDG_STATE_HOME", str(tmp_path))
    monkeypatch.setenv("TIDEO_E2E_SERIAL", SERIAL)
    monkeypatch.setenv("ADB_SERVER_HOST", "127.0.0.1")
    monkeypatch.setenv("ADB_SERVER_PORT", str(s.port))
    monkeypatch.delenv("TIDEO_E2E_CONFIRM", raising=False)
    r = s.responses
    r["getprop ro.build.version.sdk"] = (b"36\n", 0)
    r["getprop ro.product.model"] = (b"CPH2653\n", 0)
    r["getprop ro.serialno"] = (b"fakeserial01\n", 0)
    r["am get-current-user"] = (b"0\n", 0)
    for pkg in (DEBUG_PKG, RELEASE_PKG):
        r[f"dumpsys package {pkg}"] = (PACKAGE_DUMP.encode(), 0)
        r[f"dumpsys activity services {pkg}"] = (b"", 0)
    r[f"pm path {DEBUG_PKG}"] = (f"package:/data/app/~~x/{DEBUG_PKG}-y/base.apk\n".encode(), 0)
    r[f"cmd locale get-app-locales {DEBUG_PKG} --user 0"] = (
        f"Locales for {DEBUG_PKG} for user 0 are []\n".encode(), 0)
    for ns, key in SNAPSHOT_KEYS:
        r[f"settings get {ns} {key}"] = (b"1\n", 0)
    r["settings get system screen_brightness"] = (b"2000\n", 0)
    r["settings get system system_locales"] = (b"nl-NL\n", 0)
    r["dumpsys notification"] = (b"", 0)
    r["dumpsys power"] = (b"mWakefulness=Awake\n", 0)
    for path in (state.SETTINGS_STORE, state.CONTEXT_RULES_STORE, state.BASELINE_STORE,
                 state.OVERRIDE_POINTS_STORE):
        r[_cat(path)] = (b"{}", 0)
    r[_cat(state.CONTROL_STORE)] = (b"", 0)
    r[_cat(state.SAVED_MODE_STORE)] = (b"<map />", 0)
    r[f"run-as {DEBUG_PKG} tar cf - files shared_prefs no_backup databases"] = (b"TAR", 1)
    yield s
    s.close()


def _shells(server):
    return [r[len("shell:"):].removesuffix("; echo X4EXIT:$?") for r in server.received
            if r.startswith("shell:")]


def _record(tmp_path):
    (path,) = (tmp_path / "tideo-e2e").glob("preflight-*.json")
    return json.loads(path.read_text())


def test_a_clean_phone_passes_with_reads_only(server, tmp_path, capsys):
    assert cli.main(["preflight"]) == 0
    sent = _shells(server)
    assert sent and all(admit_shell(c).grade is Grade.READ for c in sent)
    assert not any(r.startswith(("sync", "tcp:")) for r in server.received)
    out = capsys.readouterr().out
    assert "REFUSED" not in out and SERIAL not in out and "fakeserial" not in out
    rec = _record(tmp_path)
    assert rec["snapshot"]["settings"]["system/screen_brightness"] == "2000"
    assert rec["snapshot"]["archive"]["bytes"] == 3
    assert rec["skips"]  # every scenario with a test is evaluated
    archive = tmp_path / "tideo-e2e" / rec["snapshot"]["archive"]["file"]
    assert archive.read_bytes() == b"TAR" and archive.stat().st_mode & 0o777 == 0o600


def test_chinese_refuses_and_leaves_paused_unread(server, tmp_path, capsys):
    server.responses[f"cmd locale get-app-locales {DEBUG_PKG} --user 0"] = (
        f"Locales for {DEBUG_PKG} for user 0 are [zh-Hans]\n".encode(), 0)
    assert cli.main(["preflight"]) == 1
    assert "set Tideo's language to English" in capsys.readouterr().out
    assert _record(tmp_path)["snapshot"]["paused"] is None


def test_brightness_above_the_profile_scale_refuses(server, capsys):
    server.responses["settings get system screen_brightness"] = (b"4096\n", 0)
    assert cli.main(["preflight"]) == 1
    assert "exceeds the profile's S 4095" in capsys.readouterr().out


def test_no_debug_build_refuses_before_reading_tideo(server, capsys):
    server.responses[f"dumpsys package {DEBUG_PKG}"] = (
        f"Unable to find package: {DEBUG_PKG}\n".encode(), 0)
    assert cli.main(["preflight"]) == 1
    assert "install a debug build first" in capsys.readouterr().out
    assert not any(c.startswith("run-as") for c in _shells(server))


def test_a_pending_journal_refuses_without_device_contact(server, tmp_path, capsys):
    with Journal.for_run(tmp_path / "tideo-e2e", Identity("d", 0, "t", 26, "c")) as j:
        j.watch(SETTING, "system/screen_brightness", "100")
    assert cli.main(["preflight"]) == 1
    assert "run e2e/run.sh --recover first" in capsys.readouterr().out
    assert server.received == []


def test_preflight_needs_the_serial(monkeypatch):
    monkeypatch.delenv("TIDEO_E2E_SERIAL", raising=False)
    assert cli.main(["preflight"]) == 2


def test_the_preflight_templates_are_reads():
    for t in TEMPLATES:
        if t.name in ("app_locales", "settings_get_system_locales"):
            assert t.grade is Grade.READ
