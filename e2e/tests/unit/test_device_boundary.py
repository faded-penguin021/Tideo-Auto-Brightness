"""The command boundary admits exactly its templates, refuses evasions, and binds at adb itself."""

import shlex

import adbutils
import pytest

from tideo_e2e import device
from tideo_e2e.device import (
    DEBUG_PKG, RELEASE_PKG, U2_JAR, BoundaryViolation, Denied, Grade, GradeNotAllowed,
    PreconditionFailed, TEMPLATES, admit_shell, deny_reason, fgs_in_dump,
)

from .fake_adb import FakeAdbServer

SERIAL = "emu01"
OTHER = "emu02"
ALL = frozenset(Grade)
RECEIVER = f"{DEBUG_PKG}/com.tideo.autobrightness.app.control.ControlReceiver"
PANIC = "com.tideo.autobrightness.control.PANIC"

# One admitted sample per template, by template name.
ALLOWED = {
    "getprop": "getprop ro.build.version.sdk",
    "dumpsys_package": f"dumpsys package {DEBUG_PKG}",
    "pm_path": f"pm path {DEBUG_PKG}",
    "dumpsys_services": f"dumpsys activity services {RELEASE_PKG}",
    "dumpsys_notification": "dumpsys notification",
    "dumpsys_power": "dumpsys power",
    "dumpsys_windows": "dumpsys window windows",
    "dumpsys_activities": "dumpsys activity activities",
    "dumpsys_top": "dumpsys activity top",
    "private_cat": f"run-as {DEBUG_PKG} cat files/datastore/control_prefs.preferences_pb",
    "private_archive": f"run-as {DEBUG_PKG} tar cf - files shared_prefs no_backup databases",
    "u2_jar_md5": f"toybox md5sum {U2_JAR}",
    "u2_jar_md5_alt": f"md5 {U2_JAR}",
    "u2_server": f"CLASSPATH={U2_JAR} app_process / com.wetest.uia2.Main -p 9008",
    "grant": f"pm grant {DEBUG_PKG} android.permission.WRITE_SECURE_SETTINGS",
    "revoke": f"pm revoke {DEBUG_PKG} android.permission.WRITE_SECURE_SETTINGS",
    "broadcast": f"am broadcast -a {PANIC} -n {RECEIVER}",
    "keyevent": "input keyevent KEYCODE_WAKEUP",
    "force_stop_debug": f"am force-stop {DEBUG_PKG}",
    "force_stop_release": f"am force-stop {RELEASE_PKG}",
    "launch": f"am start -n {RELEASE_PKG}/com.tideo.autobrightness.app.MainActivity",
    "launch_fresh":
        f"am start -f 0x10008000 -n {DEBUG_PKG}/com.tideo.autobrightness.app.MainActivity",
    "current_user": "am get-current-user",
    "settings_get_screen_brightness": "settings get system screen_brightness",
    "settings_put_screen_brightness": "settings put system screen_brightness 4095",
    "settings_get_enabled_accessibility_services":
        "settings get secure enabled_accessibility_services",
    "settings_put_user_disabled_hdr_formats": "settings put global user_disabled_hdr_formats ''",
    "settings_put_accessibility_display_daltonizer":
        "settings put secure accessibility_display_daltonizer -1",
}


@pytest.mark.parametrize("name,cmd", ALLOWED.items(), ids=list(ALLOWED))
def test_template_admits_its_sample(name, cmd):
    assert admit_shell(cmd).name == name


def test_every_non_settings_template_has_a_sample():
    names = {t.name for t in TEMPLATES if not t.name.startswith("settings_")}
    assert names - set(ALLOWED) == set()


def test_every_settings_template_admits_a_value():
    # Settings templates are generated; each must admit at least one canonical command.
    for t in TEMPLATES:
        if t.name.startswith("settings_put_"):
            sample = next(v for v in ("0", "1", "11", "1000", "") if t.argv[4].accepts(v))
            assert admit_shell(shlex.join([*t.argv[:4], sample])).name == t.name


EVASIONS = [
    # the classic wipe, spelled every way a shell would still run it
    "pm clear com.tideo.autobrightness.debug",
    "pm  clear x", "'pm' clear x", "p'm' clear x", "p\\m clear x", '"pm" clear x',
    "/system/bin/pm clear x", "cmd package clear x", "PM CLEAR x", "pm\tclear x",
    "pm\nclear x", "ｐｍ clear x", "sh -c 'pm clear x'", "$(pm clear x)",
    "`pm clear x`", "pm${IFS}clear x", "$'\\x70m' clear x",
    # chaining off an allowed command
    "settings get system screen_brightness; pm clear x",
    "settings get system screen_brightness && rm -rf /sdcard",
    "settings get system screen_brightness | sh",
    "settings get system screen_brightness > /sdcard/x",
    "settings get system screen_brightness ",
    "settings get system  screen_brightness",
    # the denylist classes
    "rm -rf /sdcard", "toybox rm x", "busybox rm x", "rmdir x",
    "settings delete system screen_brightness", "settings reset secure",
    "cmd settings delete global x", "reboot", "svc power reboot", "reboot recovery",
    "setprop debug.hwui.force_dark true", "cmd overlay enable x", "appops reset",
    "content delete --uri content://settings/system", "pm uninstall x",
    "cmd package uninstall x", "pm install -r /data/local/tmp/x.apk", "ime set x",
    # bmgr: every form, the owner-allowed restore included
    f"bmgr restore 3fa2 {DEBUG_PKG}", "bmgr restore", "bmgr restore 3fa2", "bmgr wipe x", "bmgr restore 3fa2 com.android.chrome",
    f"bmgr restore 3fa2 {DEBUG_PKG} com.other", "bmgr backupnow --all",
    # run-as: reads only, debug package only, no traversal
    f"run-as {DEBUG_PKG} rm -rf files", f"run-as {DEBUG_PKG} sh -c id",
    f"run-as {DEBUG_PKG} tar xf - files", f"run-as {DEBUG_PKG} cp a b",
    f"run-as {RELEASE_PKG} cat files/datastore/x.json",
    f"run-as {DEBUG_PKG} cat files/datastore/../../x.json",
    f"run-as {DEBUG_PKG} cat /data/data/x/files/x.json",
    # force-stop of anything else
    "am force-stop com.android.systemui", "am force-stop",
    # settings: inventory keys, typed values
    "settings put secure enabled_accessibility_services x",
    "settings put secure default_input_method x",
    "settings put system screen_brightness 4096", "settings put system screen_brightness 0x10",
    "settings put system screen_brightness 01", "settings put system screen_brightness '1 '",
    "settings put global user_disabled_hdr_formats 1,2,",
    "settings put system screen_brightness_mode 2",
    # grants, broadcasts, keys, starts outside the templates
    f"pm grant {RELEASE_PKG} android.permission.WRITE_SECURE_SETTINGS",
    f"pm grant {DEBUG_PKG} android.permission.READ_LOGS",
    f"am broadcast -a com.tideo.autobrightness.control.LOAD_PROFILE -n {RECEIVER}",
    f"am broadcast -a com.tideo.autobrightness.control.CONTEXTS_RESUME -n {RECEIVER}",
    f"am broadcast -a {PANIC}", f"am broadcast -a {PANIC} -n com.other/.Receiver",
    "input keyevent KEYCODE_POWER", "input tap 100 100", "input text x",
    "am start -a android.intent.action.VIEW -d http://example.org",
    "dumpsys package com.android.chrome", "getprop persist.sys.x", "",
]


@pytest.mark.parametrize("cmd", EVASIONS)
def test_boundary_refuses(cmd):
    with pytest.raises(BoundaryViolation):
        admit_shell(cmd)


@pytest.mark.parametrize("cmd", [
    "pm clear x", "p'm' clear x", "ｐｍ clear x", "sh -c 'pm clear x'",
    "rm -rf /", "settings reset secure", "bmgr restore 3fa2",
    f"run-as {DEBUG_PKG} sh -c id", "am force-stop com.android.systemui",
])
def test_hard_denylist_fires_on_its_own(cmd):
    # Defence in depth: these are refused even before the template layer is consulted.
    with pytest.raises(Denied):
        admit_shell(cmd)


@pytest.mark.parametrize("cmd", ALLOWED.values())
def test_denylist_spares_every_sample(cmd):
    assert deny_reason(cmd) is None


def test_v1_exit_trailer_only_strips_once():
    cmd = "settings get system screen_brightness"
    assert admit_shell(cmd + "; echo X4EXIT:$?", v1=True)
    with pytest.raises(BoundaryViolation):
        admit_shell(cmd + "; echo X4EXIT:$?", v1=False)
    with pytest.raises(BoundaryViolation):
        admit_shell(cmd + "; echo X4EXIT:$?; echo X4EXIT:$?", v1=True)


# ── services, grades and preconditions, judged directly ────────────────────────────────────


def _session(grades=ALL, **kw):
    return device.Session(SERIAL, frozenset(grades), **kw)


@pytest.mark.parametrize("req", [
    "host:kill", "host:connect:<addr>", "host:disconnect:", "host:transport-id:1",
    f"host:transport:{OTHER}", f"host:tport:serial:{OTHER}", f"host-serial:{OTHER}:features",
    f"host-serial:{SERIAL}:forward:tcp:1;tcp:2", f"host-serial:{SERIAL}:killforward-all",
    "host:transport-any", "reboot:", "reboot:recovery", "root:", "unroot:", "remount:",
    "tcpip:5555", "usb:", "exec:pm clear x", "exec:cat x", "abb_exec:package\0clear\0x",
    "abb:settings", "framebuffer:", "reverse:forward:tcp:1 tcp:2", "jdwp:1", "backup:",
    "restore:", "tcp:8080", "localabstract:x", "sync:",
])
def test_service_refused(req):
    with pytest.raises(BoundaryViolation):
        device._admit_service(_session(), req)


def test_read_session_refuses_mutation_and_harness():
    s = _session({Grade.READ})
    assert device._admit_service(s, "shell:dumpsys power") is Grade.READ
    with pytest.raises(GradeNotAllowed):
        device._admit_service(s, "shell:settings put system screen_brightness 10")
    with pytest.raises(GradeNotAllowed):
        device._admit_service(s, f"shell,v2:CLASSPATH={U2_JAR} app_process / "
                                 "com.wetest.uia2.Main -p 9008")


def test_broadcast_spacing(monkeypatch):
    monkeypatch.setattr(device, "_last_broadcast", float("-inf"))
    now = [100.0]
    s = _session(clock=lambda: now[0])
    req = f"shell:am broadcast -a {PANIC} -n {RECEIVER}"
    device._admit_service(s, req)
    now[0] += 1.0
    with pytest.raises(PreconditionFailed):
        device._admit_service(s, req)
    now[0] += 0.6
    device._admit_service(s, req)


def test_release_force_stop_needs_its_service_running():
    with pytest.raises(PreconditionFailed):
        device._admit_service(_session(), f"shell:am force-stop {RELEASE_PKG}")


FGS_DUMP = """ACTIVITY MANAGER SERVICES (dumpsys activity services)
  User 0 active services:
  * ServiceRecord{9f0c u0 com.tideo.autobrightness/.app.runtime.AmbientMonitoringService}
    intent={cmp=com.tideo.autobrightness/.app.runtime.AmbientMonitoringService}
    isForeground=true foregroundId=1001
"""


def test_release_fgs_parse():
    assert fgs_in_dump(FGS_DUMP, RELEASE_PKG)
    assert not fgs_in_dump(FGS_DUMP.replace("isForeground=true", "isForeground=false"), RELEASE_PKG)
    assert not fgs_in_dump(FGS_DUMP.replace("autobrightness/", "autobrightness.debug/"), RELEASE_PKG)
    other = FGS_DUMP.replace("isForeground=true", "isForeground=false") + (
        "  * ServiceRecord{77 u0 com.other/.Svc}\n    isForeground=true\n")
    assert not fgs_in_dump(other, RELEASE_PKG)


# ── end to end: real adbutils / uiautomator2 against a loopback server ─────────────────────


@pytest.fixture
def server():
    s = FakeAdbServer()
    yield s
    s.close()


@pytest.fixture
def client(server):
    return adbutils.AdbClient(host="127.0.0.1", port=server.port)


def test_nothing_reaches_adb_without_a_session(server, client):
    with pytest.raises(BoundaryViolation):
        client.device(SERIAL).shell("dumpsys power")
    assert server.received == []


def test_session_requires_a_real_serial(client):
    with pytest.raises(BoundaryViolation):
        with device.session("<serial>", client=client):
            pass


def test_read_session_end_to_end(server, client):
    with device.session(SERIAL, client=client) as s:
        assert "ok" in device.run(s, "settings", "get", "system", "screen_brightness").output
        with pytest.raises(GradeNotAllowed):
            s.device.shell("settings put system screen_brightness 10")
        with pytest.raises(BoundaryViolation):
            s.device.shell("pm clear " + DEBUG_PKG)
        with pytest.raises(BoundaryViolation):
            device.run(s, "rm", "-rf", "/sdcard")
    sent = [r for r in server.received if r.startswith("shell")]
    assert sent == ["shell:settings get system screen_brightness; echo X4EXIT:$?"]
    assert ("REFUSED", "shell:pm clear " + DEBUG_PKG) in s.log
    assert all(SERIAL not in req for _, req in s.log)


def test_other_serial_never_reaches_adb(server, client):
    with device.session(SERIAL, client=client):
        with pytest.raises(BoundaryViolation):
            client.device(OTHER).shell("dumpsys power")
    assert not any(OTHER in r for r in server.received)


def test_one_session_at_a_time(client):
    with device.session(SERIAL, client=client):
        with pytest.raises(BoundaryViolation):
            with device.session(SERIAL, client=client):
                pass


def test_sync_is_confined_to_the_u2_jar(server, client):
    with device.session(SERIAL, client=client) as s:
        s.device.sync.stat(U2_JAR)
        with pytest.raises(GradeNotAllowed):
            s.device.sync.push(b"x", U2_JAR)
    with device.session(SERIAL, ALL, client=client) as s:
        for attempt in (lambda: s.device.sync.push(b"x", "/sdcard/x"),
                        lambda: s.device.sync.list("/sdcard"),
                        lambda: s.device.sync.read_bytes("/data/local/tmp/x"),
                        lambda: s.device.sync.stat("/sdcard")):
            with pytest.raises(BoundaryViolation):
                attempt()
        with pytest.raises(BoundaryViolation):
            s.device.open_transport().send_command("sync:")
    # push() stats its destination first; only that read ever reached the server.
    assert {r for r in server.received if r.startswith("sync ")} == {f"sync STAT {U2_JAR}"}


def test_adb_binary_is_never_run(client):
    with device.session(SERIAL, ALL, client=client) as s:
        with pytest.raises(BoundaryViolation):
            s.device.adb_output("shell", "pm", "clear", DEBUG_PKG)
    with pytest.raises(BoundaryViolation):
        adbutils.adb_path()


def test_refused_connection_does_not_start_a_server():
    dead = adbutils.AdbClient(host="127.0.0.1", port=1)
    with pytest.raises(BoundaryViolation):
        dead.server_version()


def test_install_and_uninstall_are_refused(server, client):
    with device.session(SERIAL, ALL, client=client) as s:
        with pytest.raises(BoundaryViolation):
            s.device.uninstall(DEBUG_PKG)
        with pytest.raises(BoundaryViolation):
            s.device.shell(["pm", "clear", DEBUG_PKG])
        with pytest.raises(BoundaryViolation):
            s.device.root()
        with pytest.raises(BoundaryViolation):
            s.device.reboot()
    assert not any(("uninstall" in r or "clear" in r or r.startswith(("root", "reboot")))
                   for r in server.received)


# ── findings from the S2 review: binding, single use, exact shapes ─────────────────────────


@pytest.mark.parametrize("path", [
    f"{U2_JAR},{0o120777}",   # symlink
    f"{U2_JAR},{0o104755}",   # setuid
    f"{U2_JAR},{0o100777}",   # world-writable
    f"{U2_JAR},33261,1",
])
def test_jar_push_mode_is_exact(path):
    with pytest.raises(BoundaryViolation):
        device._admit_sync(path, "SEND")
    assert device._admit_sync(f"{U2_JAR},{0o100755}", "SEND") is Grade.HARNESS


@pytest.mark.parametrize("value", ["99", "0", "5", "1,2,3,4,1", "1,"])
def test_hdr_formats_are_typed(value):
    with pytest.raises(BoundaryViolation):
        admit_shell(f"settings put global user_disabled_hdr_formats {value}")


def test_sync_capability_is_single_use():
    device._sync_ok.pending = Grade.READ
    try:
        assert device._admit_service(_session(), "sync:") is Grade.READ
        with pytest.raises(BoundaryViolation):
            device._admit_service(_session(), "sync:")
    finally:
        device._sync_ok.pending = None


def test_broadcast_spacing_survives_a_new_session(monkeypatch):
    monkeypatch.setattr(device, "_last_broadcast", float("-inf"))
    req = f"shell:am broadcast -a {PANIC} -n {RECEIVER}"
    device._admit_service(_session(clock=lambda: 50.0), req)
    with pytest.raises(PreconditionFailed):
        device._admit_service(_session(clock=lambda: 50.5), req)


def test_run_refuses_a_stale_session_and_an_ungranted_grade(client):
    with device.session(SERIAL, client=client) as stale:
        pass
    with device.session(SERIAL, ALL, client=client):
        with pytest.raises(BoundaryViolation):
            device.run(stale, "settings", "put", "system", "screen_brightness", "10")
    with device.session(SERIAL, client=client) as s:
        with pytest.raises(GradeNotAllowed):
            device.run(s, "settings", "put", "system", "screen_brightness", "10")


# ── S4: the install guard's two openings ───────────────────────────────────────────────────

INSTALLED = f"/data/app/~~AbC_12-=/{DEBUG_PKG}-XyZ_9==/base.apk"


def test_only_the_installed_debug_apk_can_be_read():
    assert device._admit_sync(INSTALLED, "RECV") is Grade.READ
    for path, cmd in ((INSTALLED, "SEND"), (INSTALLED.replace(DEBUG_PKG, RELEASE_PKG), "RECV"),
                      (f"/data/app/~~a/../{DEBUG_PKG}-b/base.apk", "RECV"),
                      (f"/data/app/~~a/{DEBUG_PKG}-b/split_config.arm64_v8a.apk", "RECV"),
                      (f"/data/data/{DEBUG_PKG}/files/base.apk", "RECV")):
        with pytest.raises(BoundaryViolation):
            device._admit_sync(path, cmd)


def test_install_is_admitted_once_for_its_exact_size():
    s, req = _session(), "exec:cmd package install -r -S 1234"
    with pytest.raises(PreconditionFailed):
        device._admit_service(s, req)
    s.install_size = 1234
    with pytest.raises(PreconditionFailed):
        device._admit_service(s, "exec:cmd package install -r -S 1235")
    assert device._admit_service(s, req) is Grade.MUTATE
    with pytest.raises(PreconditionFailed):
        device._admit_service(s, req)


@pytest.mark.parametrize("req", [
    "exec:cmd package install -r -d -S 9", "exec:cmd package install -r -g -S 9",
    "exec:cmd package install -r -t -S 9", "exec:cmd package install -S 9",
    "exec:cmd package install -r -S 9 --user 10", "exec:cmd package uninstall " + DEBUG_PKG,
    "exec:pm install -r -S 9", "shell:cmd package install -r -S 9", "abb_exec:package\0install",
])
def test_other_install_shapes_refused(req):
    s = _session()
    s.install_size = 9
    with pytest.raises(BoundaryViolation):
        device._admit_service(s, req)


def test_install_needs_the_open_mutate_session(client):
    with device.session(SERIAL, client=client) as s:
        with pytest.raises(BoundaryViolation):
            device.install(s, b"apk")
        assert s.install_size is None
