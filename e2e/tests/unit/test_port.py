"""The device port, through the real command boundary against a fake adb server."""

from __future__ import annotations

import adbutils
import pytest

from tideo_e2e import cli, device
from tideo_e2e.device import DEBUG_PKG, Grade, WRITE_SECURE_SETTINGS
from tideo_e2e.journal import GRANT, PREF, SETTING, Journal
from tideo_e2e.recovery import RecoveryError
from tideo_e2e.state import StateError
from tideo_e2e.tideo import DevicePort

from .fake_adb import FakeAdbServer
from .test_state import PACKAGE_DUMP, ongoing, prefs_blob

SERIAL = "emu01"
MAIN = f"{DEBUG_PKG}/com.tideo.autobrightness.app.MainActivity"
CONTROL = "run-as com.tideo.autobrightness.debug cat files/datastore/control_prefs.preferences_pb"


SETTINGS_CAT = "run-as com.tideo.autobrightness.debug cat files/datastore/aab_settings.json"


class FakeUi:
    """The Dashboard: renders `placeholder` reads of false before settings load, and persists
    serviceEnabled into the fake device's settings store when the switch is tapped."""

    def __init__(self, server=None, enabled: bool = True, placeholder: int = 0):
        self.server, self.clicks, self.placeholder = server, [], placeholder
        self._store(enabled)

    def _store(self, enabled: bool):
        self.enabled = enabled
        if self.server is not None:
            self.server.responses[SETTINGS_CAT] = (
                f'{{"serviceEnabled": {str(enabled).lower()}}}'.encode(), 0)

    def click(self, name):
        self.clicks.append(name)
        if name == "service_switch":
            self._store(not self.checked("service_switch_state"))

    def checked(self, name):
        assert name == "service_switch_state"
        if self.placeholder:
            self.placeholder -= 1
            return False
        return self.enabled


@pytest.fixture
def server():
    s = FakeAdbServer()
    yield s
    s.close()


@pytest.fixture
def session(server):
    client = adbutils.AdbClient(host="127.0.0.1", port=server.port)
    with device.session(SERIAL, frozenset(Grade), client) as s:
        yield s


def _port(session, tmp_path, ui=None, **kw):
    return DevicePort(session, ui or FakeUi(), tmp_path, sleep=lambda _s: None, **kw)


def _shells(server):
    return [r[len("shell:"):].removesuffix("; echo X4EXIT:$?") for r in server.received
            if r.startswith("shell:")]


@pytest.mark.parametrize("enabled,on,taps", [
    (True, False, 1), (False, False, 0), (False, True, 1), (True, True, 0),
])
def test_service_switch_is_tapped_only_to_change_it(server, session, tmp_path, enabled, on, taps):
    port = _port(session, tmp_path, FakeUi(server, enabled))
    port.service_on() if on else port.service_off()
    assert _shells(server)[0] == f"am start -f 0x10008000 -n {MAIN}"
    assert port.ui.clicks == ["menu_dashboard"] + ["service_switch"] * taps
    assert port.ui.enabled is on


def test_the_placeholder_false_is_never_acted_on(server, session, tmp_path):
    # Enabled, but the first reads show the Dashboard's pre-load false: OFF must still tap
    # once (not zero), and ON must not tap at all (not flip it off).
    port = _port(session, tmp_path, FakeUi(server, True, placeholder=3))
    port.service_on()
    assert port.ui.clicks == ["menu_dashboard"] and port.ui.enabled
    port = _port(session, tmp_path, FakeUi(server, True, placeholder=3))
    port.service_off()
    assert port.ui.clicks == ["menu_dashboard", "service_switch"] and not port.ui.enabled


def test_a_switch_that_never_agrees_refuses_to_tap(server, session, tmp_path):
    port = _port(session, tmp_path, FakeUi(server, True, placeholder=99))
    with pytest.raises(RecoveryError, match="never showed"):
        port.service_off()
    assert "service_switch" not in port.ui.clicks


def test_read_setting_grant_and_pref(server, session, tmp_path):
    server.responses["settings get system screen_brightness"] = (b"812\n", 0)
    server.responses["settings get global user_disabled_hdr_formats"] = (b"null\n", 0)
    server.responses[f"dumpsys package {DEBUG_PKG}"] = (PACKAGE_DUMP.encode(), 0)
    server.responses[CONTROL] = (prefs_blob(external_control_enabled=True), 0)
    port = _port(session, tmp_path)
    assert port.read(SETTING, "system/screen_brightness") == "812"
    assert port.read(SETTING, "global/user_disabled_hdr_formats") is None
    assert port.read(GRANT, WRITE_SECURE_SETTINGS) == "granted"
    assert port.read(PREF, "control_prefs/external_control_enabled") == "true"
    # Absent values are the app's defaults: never-written keys, and JSON fields kotlinx omits.
    assert port.read(PREF, "control_prefs/force_dark_enabled") == "false"
    server.responses[SETTINGS_CAT] = (b'{"contextOverride": true}', 0)
    assert port.read(PREF, "aab_settings/serviceEnabled") == "true"
    assert port.read(PREF, "aab_settings/contextOverride") == "true"
    with pytest.raises(StateError, match="unknown preference"):
        port.read(PREF, "control_prefs/never_heard_of")


def test_pref_store_never_written_reads_the_default(server, session, tmp_path):
    server.responses[CONTROL] = (b"cat: files/datastore/x: No such file or directory\n", 1)
    assert _port(session, tmp_path).read(PREF, "control_prefs/external_control_enabled") == "false"


def test_writes_go_through_templates(server, session, tmp_path):
    port = _port(session, tmp_path)
    port.write(SETTING, "system/screen_brightness", "812")
    port.write(SETTING, "global/user_disabled_hdr_formats", "")
    port.write(GRANT, WRITE_SECURE_SETTINGS, "revoked")
    port.write(GRANT, WRITE_SECURE_SETTINGS, "granted")
    assert _shells(server) == [
        "settings put system screen_brightness 812",
        "settings put global user_disabled_hdr_formats ''",
        f"pm revoke {DEBUG_PKG} {WRITE_SECURE_SETTINGS}",
        f"pm grant {DEBUG_PKG} {WRITE_SECURE_SETTINGS}",
    ]


def test_a_failed_write_raises(server, session, tmp_path):
    server.responses["settings put system screen_brightness 812"] = (b"", 255)
    with pytest.raises(RecoveryError, match="exited 255"):
        _port(session, tmp_path).write(SETTING, "system/screen_brightness", "812")


def test_unrestorable_writes_refuse_before_sending(server, session, tmp_path):
    port = _port(session, tmp_path)
    for kind, key, value in [(PREF, "aab_settings/contextOverride", "true"),
                             (GRANT, WRITE_SECURE_SETTINGS, "maybe"),
                             (GRANT, "android.permission.CAMERA", "granted")]:
        with pytest.raises(RecoveryError, match="no way to restore"):
            port.write(kind, key, value)
    assert _shells(server) == []


def test_a_registered_pref_restorer_runs(session, tmp_path):
    seen = []
    port = _port(session, tmp_path, restorers={"aab_settings/x": lambda p, v: seen.append(v)})
    port.write(PREF, "aab_settings/x", "true")
    assert seen == ["true"]


def test_paused_reads_resume_on_the_ongoing_notification(server, session, tmp_path):
    server.responses["dumpsys notification"] = (ongoing("Reset", "Disable").encode(), 0)
    assert not _port(session, tmp_path).paused()
    server.responses["dumpsys notification"] = (ongoing("Resume", "Reset").encode(), 0)
    assert _port(session, tmp_path).paused()


def test_pause_needs_external_control(server, session, tmp_path):
    server.responses[CONTROL] = (prefs_blob(external_control_enabled=False), 0)
    with pytest.raises(RecoveryError, match="external control is off"):
        _port(session, tmp_path).pause()
    assert not any(c.startswith("am broadcast") for c in _shells(server))
    server.responses[CONTROL] = (prefs_blob(external_control_enabled=True), 0)
    _port(session, tmp_path).pause()
    assert _shells(server)[-1] == (
        "am broadcast -a com.tideo.autobrightness.control.PAUSE "
        f"-n {DEBUG_PKG}/com.tideo.autobrightness.app.control.ControlReceiver")


def test_identity(server, session, tmp_path):
    server.responses["getprop ro.serialno"] = (b"fakeserial01\n", 0)
    server.responses[f"dumpsys package {DEBUG_PKG}"] = (PACKAGE_DUMP.encode(), 0)
    server.responses["am get-current-user"] = (b"0\n", 0)
    ident = _port(session, tmp_path).identity()
    assert (ident.user, ident.first_install_time, ident.version_code, ident.cert_digest) == (
        0, "2026-06-23 18:44:10", 26, "a1b2c3d4")
    assert "fakeserial" not in ident.serial_digest and len(ident.serial_digest) == 64


def test_identity_refuses_a_secondary_user(server, session, tmp_path):
    server.responses["am get-current-user"] = (b"10\n", 0)
    with pytest.raises(StateError, match="only as user 0"):
        _port(session, tmp_path).identity()


# ── run.sh --recover ──


def test_recover_on_an_empty_journal_needs_no_device(tmp_path, monkeypatch, capsys):
    monkeypatch.setenv("XDG_STATE_HOME", str(tmp_path))
    monkeypatch.delenv("TIDEO_E2E_SERIAL", raising=False)
    assert cli.main(["recover"]) == 0
    assert "nothing to do" in capsys.readouterr().out


def test_recover_on_a_pending_journal_needs_the_serial(tmp_path, monkeypatch):
    monkeypatch.setenv("XDG_STATE_HOME", str(tmp_path))
    monkeypatch.delenv("TIDEO_E2E_SERIAL", raising=False)
    store = tmp_path / "tideo-e2e"
    from tideo_e2e.journal import Identity
    with Journal.for_run(store, Identity("d", 0, "t", 26, "c")) as j:
        j.watch(SETTING, "system/screen_brightness", "100")
    assert cli.main(["recover"]) == 2
    assert (store / "journal.json").exists()


def test_recover_checks_identity_before_anything_but_reads(server, tmp_path, monkeypatch):
    monkeypatch.setenv("XDG_STATE_HOME", str(tmp_path))
    monkeypatch.setenv("TIDEO_E2E_SERIAL", SERIAL)
    monkeypatch.setenv("ADB_SERVER_HOST", "127.0.0.1")
    monkeypatch.setenv("ADB_SERVER_PORT", str(server.port))
    server.responses["getprop ro.serialno"] = (b"fakeserial01\n", 0)
    server.responses[f"dumpsys package {DEBUG_PKG}"] = (PACKAGE_DUMP.encode(), 0)
    server.responses["am get-current-user"] = (b"0\n", 0)
    store = tmp_path / "tideo-e2e"
    from tideo_e2e.journal import Identity
    with Journal.for_run(store, Identity("another phone", 0, "t", 26, "c")) as j:
        j.watch(SETTING, "system/screen_brightness", "100")
    assert cli.main(["recover"]) == 1
    assert _shells(server) == ["am get-current-user", "getprop ro.serialno",
                               f"dumpsys package {DEBUG_PKG}"]
    assert not any(r.startswith(("sync", "tcp:")) for r in server.received)
    assert (store / "journal.json").exists()


def test_cli_usage():
    assert cli.main([]) == 2
