"""The only adb gateway: deny-by-default command templates, enforced where adb services open.

Every request any library makes to the adb server passes through `AdbConnection.send_command`,
and every sync sub-request through `Sync._prepare_sync`. Importing this module replaces both, so
adbutils and uiautomator2 internals are judged by the same rules as the harness's own calls.
With no `session()` active, every request is refused.

A shell command is admitted only if (1) no hard-denylist rule fires on its normalised tokens,
(2) it is in canonical form — `shlex.join(shlex.split(cmd)) == cmd`, so no unquoted shell
operator, substitution or variable can reach the device shell — and (3) its argv matches an
exact template with typed parameters. Templates are graded READ, HARNESS (uiautomator2's own
disclosed side effects: pushing u2.jar to /data/local/tmp and running its server) and MUTATE;
a session admits only the grades it was opened with, READ alone by default.
"""

from __future__ import annotations

import enum
import re
import shlex
import stat
import threading
import time
import unicodedata
from contextlib import contextmanager
from dataclasses import dataclass, field
from typing import Callable, Iterator

import adbutils
import adbutils._device_base
import adbutils._utils
import adbutils.screenrecord
from adbutils import _adb, sync

DEBUG_PKG = "com.tideo.autobrightness.debug"
RELEASE_PKG = "com.tideo.autobrightness"
PACKAGES = frozenset({DEBUG_PKG, RELEASE_PKG})

_APP_NS = "com.tideo.autobrightness.app"
MAIN_ACTIVITY = f"{_APP_NS}.MainActivity"
CONTROL_RECEIVER = f"{_APP_NS}.control.ControlReceiver"
MONITORING_SERVICE = f"{_APP_NS}.runtime.AmbientMonitoringService"
WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"

_ACTION_NS = "com.tideo.autobrightness.control"
# LOAD_PROFILE and CONTEXTS_RESUME are absent on purpose: profile-changing steps are manual
# (DD-015), because the settings, baseline and profile-name tuple cannot be restored.
CONTROL_ACTIONS = frozenset(
    f"{_ACTION_NS}.{verb}"
    for verb in ("SERVICE_ON", "SERVICE_OFF", "SERVICE_TOGGLE", "PAUSE", "RESUME", "REAPPLY", "PANIC")
)
BROADCAST_SPACING_S = 1.5
LAUNCH_FRESH_FLAGS = "0x10008000"

U2_JAR = "/data/local/tmp/u2.jar"
U2_PORT = 9008  # uiautomator2.core.DEFAULT_SERVER_PORT; the drift alarm pins it


class BoundaryViolation(RuntimeError):
    """A request the boundary refuses. Raised before anything is sent."""


class Denied(BoundaryViolation):
    """A hard-denylist rule fired."""


class Unlisted(BoundaryViolation):
    """No template admits the request."""


class GradeNotAllowed(BoundaryViolation):
    """A template matched, but this session was not opened for its grade."""


class PreconditionFailed(BoundaryViolation):
    """A template matched, but its runtime precondition does not hold."""


class Grade(enum.Enum):
    READ = "read"
    HARNESS = "harness"
    MUTATE = "mutate"


# ── typed parameters ────────────────────────────────────────────────────────────────────────


@dataclass(frozen=True)
class P:
    """One typed argv parameter."""

    name: str
    accepts: Callable[[str], bool]


def one_of(name: str, *values: str) -> P:
    allowed = frozenset(values)
    return P(name, allowed.__contains__)


def int_in(name: str, lo: int, hi: int) -> P:
    def accepts(v: str) -> bool:
        return re.fullmatch(r"-?(0|[1-9]\d{0,5})", v) is not None and lo <= int(v) <= hi
    return P(name, accepts)


def pattern(name: str, regex: str) -> P:
    compiled = re.compile(regex)
    return P(name, lambda v: compiled.fullmatch(v) is not None)


# ── settings keys: namespace → key → value parameter (None: read-only) ─────────────────────
# The S3 effect inventory refines these into journaled effects; a key absent here can be
# neither read nor written. Ranges are type bounds, not plausibility: a restore must be able to
# write back whatever the device held. Brightness is bounded by the owner's stored scale S.

BRIGHTNESS_MAX = 4095  # DEVICE_TEST_SCRIPT §2 10b, the owner's measured S

_BIT = int_in("bit", 0, 1)
SETTINGS: dict[str, dict[str, P | None]] = {
    "system": {
        "screen_brightness": int_in("brightness", 0, BRIGHTNESS_MAX),
        "screen_brightness_mode": _BIT,
        # The full system language list, which resource resolution walks (S5's language check).
        "system_locales": None,
    },
    "secure": {
        "night_display_activated": _BIT,
        "night_display_color_temperature": int_in("kelvin", 686, 10000),
        "night_display_auto_mode": int_in("auto_mode", 0, 2),
        "accessibility_display_daltonizer": one_of("daltonizer", "-1", "0", "11", "12", "13"),
        "accessibility_display_daltonizer_enabled": _BIT,
        "accessibility_display_inversion_enabled": _BIT,
        "doze_always_on": _BIT,
        "reduce_bright_colors_level": int_in("rbc_level", 0, 100),
        "reduce_bright_colors_activated": _BIT,
        # Holds Tideo's own accessibility service (§12): readable, never writable.
        "enabled_accessibility_services": None,
    },
    "global": {
        "stay_on_while_plugged_in": int_in("plugged_mask", 0, 15),
        # HdrCapabilities types 1–4. Typed only: the harness writes this key solely to restore a
        # journaled original, never a synthesised set (DB-071).
        "user_disabled_hdr_formats": pattern("hdr_formats", r"([1-4](,[1-4]){0,3})?"),
        "are_user_disabled_hdr_formats_allowed": _BIT,
    },
}

def restorable(namespace: str, key: str, value: str) -> bool:
    """Whether the `settings put` template would accept writing this value back."""
    param = SETTINGS[namespace][key]
    return param is not None and param.accepts(value)


GETPROPS = frozenset({
    "ro.build.version.sdk", "ro.build.version.release", "ro.product.manufacturer",
    "ro.product.model", "ro.serialno", "debug.hwui.force_dark", "persist.sys.locale",
})

# Private files run-as may read; relative to the app data dir, no traversal.
_PRIVATE_FILE = r"(files/datastore/[a-z0-9_]+\.(preferences_pb|pb|json)|shared_prefs/[a-z0-9_.]+\.xml)"
_ARCHIVE_DIRS = ("files", "shared_prefs", "no_backup", "databases")


# ── templates ───────────────────────────────────────────────────────────────────────────────


@dataclass(frozen=True)
class Template:
    name: str
    grade: Grade
    argv: tuple[str | P, ...]
    precondition: str | None = None  # a Session method name, checked before admitting

    def matches(self, argv: list[str]) -> bool:
        return len(argv) == len(self.argv) and all(
            (a == t) if isinstance(t, str) else t.accepts(a) for a, t in zip(argv, self.argv)
        )


def _templates() -> tuple[Template, ...]:
    pkg = one_of("package", *PACKAGES)
    out = [
        Template("getprop", Grade.READ, ("getprop", one_of("prop", *GETPROPS))),
        Template("dumpsys_package", Grade.READ, ("dumpsys", "package", pkg)),
        Template("pm_path", Grade.READ, ("pm", "path", DEBUG_PKG)),
        Template("dumpsys_services", Grade.READ, ("dumpsys", "activity", "services", pkg)),
        Template("dumpsys_notification", Grade.READ, ("dumpsys", "notification")),
        # Titles, for who posted a collapsed shade row (DD-033). Every app's text: parsed in
        # memory to the posting packages, never kept (the audit log holds requests only).
        Template("dumpsys_notification_titles", Grade.READ,
                 ("dumpsys", "notification", "--noredact")),
        Template("dumpsys_power", Grade.READ, ("dumpsys", "power")),
        # adbutils.app_current(), with the next two.
        Template("dumpsys_windows", Grade.READ, ("dumpsys", "window", "windows")),
        # ui.py's focus check: Android 16 prints mCurrentFocus here, not under windows (DD-033).
        Template("dumpsys_displays", Grade.READ, ("dumpsys", "window", "displays")),
        Template("dumpsys_activities", Grade.READ, ("dumpsys", "activity", "activities")),
        Template("dumpsys_top", Grade.READ, ("dumpsys", "activity", "top")),
        Template("current_user", Grade.READ, ("am", "get-current-user")),
        # The per-app language (#141's picker stores it in the platform's LocaleManager).
        Template("app_locales", Grade.READ,
                 ("cmd", "locale", "get-app-locales", pkg, "--user", "0")),
        Template("private_cat", Grade.READ,
                 ("run-as", DEBUG_PKG, "cat", pattern("file", _PRIVATE_FILE))),
        Template("private_archive", Grade.READ,
                 ("run-as", DEBUG_PKG, "tar", "cf", "-", *_ARCHIVE_DIRS)),
        Template("u2_jar_md5", Grade.READ, ("toybox", "md5sum", U2_JAR)),
        Template("u2_jar_md5_alt", Grade.READ, ("md5", U2_JAR)),
        Template("u2_server", Grade.HARNESS,
                 (f"CLASSPATH={U2_JAR}", "app_process", "/", "com.wetest.uia2.Main",
                  "-p", str(U2_PORT))),
        Template("grant", Grade.MUTATE, ("pm", "grant", DEBUG_PKG, WRITE_SECURE_SETTINGS)),
        Template("revoke", Grade.MUTATE, ("pm", "revoke", DEBUG_PKG, WRITE_SECURE_SETTINGS)),
        Template("broadcast", Grade.MUTATE,
                 ("am", "broadcast", "-a", one_of("action", *CONTROL_ACTIONS),
                  "-n", f"{DEBUG_PKG}/{CONTROL_RECEIVER}"),
                 precondition="broadcast_spacing"),
        # Closes the notification shade without a Back that could reach an app (DD-033).
        Template("shade_collapse", Grade.MUTATE, ("cmd", "statusbar", "collapse")),
        Template("keyevent", Grade.MUTATE,
                 ("input", "keyevent", one_of("key", "KEYCODE_SLEEP", "KEYCODE_WAKEUP"))),
        Template("force_stop_debug", Grade.MUTATE, ("am", "force-stop", DEBUG_PKG)),
        # Owner decision 2026-09-13: only while its service is running.
        Template("force_stop_release", Grade.MUTATE, ("am", "force-stop", RELEASE_PKG),
                 precondition="release_fgs_running"),
        Template("launch", Grade.MUTATE, ("am", "start", "-n", one_of(
            "component", *(f"{p}/{MAIN_ACTIVITY}" for p in PACKAGES)))),
        # NEW_TASK | CLEAR_TASK: a fresh activity on the start destination, so every UI route
        # begins from a known screen. Like any launch, it starts the service if serviceEnabled.
        Template("launch_fresh", Grade.MUTATE,
                 ("am", "start", "-f", LAUNCH_FRESH_FLAGS, "-n", f"{DEBUG_PKG}/{MAIN_ACTIVITY}")),
    ]
    for ns, keys in SETTINGS.items():
        for key, value in keys.items():
            out.append(Template(f"settings_get_{key}", Grade.READ, ("settings", "get", ns, key)))
            if value is not None:
                out.append(Template(f"settings_put_{key}", Grade.MUTATE,
                                    ("settings", "put", ns, key, value)))
    return tuple(out)


TEMPLATES = _templates()


# ── hard denylist (defence in depth; the templates already deny by default) ─────────────────

_DENY_WORDS = frozenset({
    "rm", "rmdir", "unlink", "dd", "mkfs", "reboot", "shutdown", "setprop", "wipe", "fastboot",
    "recovery", "bootloader", "uninstall", "install", "install-create", "install-write",
    "install-commit", "install-existing", "remount", "su",
    # Even the owner-allowed `bmgr restore <token> <package>`: no scenario needs it, and a failed
    # restore can wipe the package's data (S2–S4 review).
    "bmgr",
})
# (first, later): denied when `later` follows `first` anywhere in the command.
_DENY_PAIRS = (
    ("pm", "clear"), ("package", "clear"), ("pm", "disable"), ("pm", "disable-user"),
    ("pm", "hide"), ("pm", "suspend"), ("pm", "reset-permissions"), ("pm", "trim-caches"),
    ("settings", "delete"), ("settings", "reset"),
    ("cmd", "overlay"), ("appops", "reset"), ("appops", "set"),
    ("content", "delete"), ("content", "update"), ("content", "insert"), ("content", "call"),
    ("ime", "set"), ("ime", "enable"), ("ime", "disable"),
)
_RUN_AS_READERS = {"cat", "tar"}


def _tokens(cmd: str) -> list[str]:
    text = unicodedata.normalize("NFKC", cmd).lower()
    text = re.sub(r"[\"'\\]", "", text)  # p'm', "pm", p\m all collapse to pm
    return [t.rsplit("/", 1)[-1] for t in re.split(r"[^a-z0-9_.=/-]+", text) if t]


def deny_reason(cmd: str) -> str | None:
    """The first hard-denylist rule `cmd` trips, or None."""
    tokens = _tokens(cmd)
    for t in tokens:
        if t in _DENY_WORDS:
            return f"denied word {t!r}"
    for first, later in _DENY_PAIRS:
        if first in tokens and later in tokens[tokens.index(first) + 1:]:
            return f"denied {first} … {later}"
    for i, t in enumerate(tokens):
        if t == "force-stop" and (i + 1 >= len(tokens) or tokens[i + 1] not in PACKAGES):
            return "force-stop of a package other than Tideo's"
        if t == "run-as":
            rest = tokens[i + 2:i + 3]
            if not rest or rest[0] not in _RUN_AS_READERS:
                return "run-as other than a read"
            if rest[0] == "tar" and tokens[i + 3:i + 4] != ["cf"]:
                return "run-as tar other than create-to-stdout"
    return None


_V1_SUFFIX = "; echo X4EXIT:$?"  # adbutils' exit-code trailer when shell v2 is unavailable


def admit_shell(cmd: str, v1: bool = False) -> Template:
    """The template admitting shell command `cmd`, or raise. Grade is the caller's concern."""
    if reason := deny_reason(cmd):
        raise Denied(f"{reason}: {cmd!r}")
    if v1 and cmd.endswith(_V1_SUFFIX):
        cmd = cmd[: -len(_V1_SUFFIX)]
    try:
        argv = shlex.split(cmd)
    except ValueError as e:
        raise Unlisted(f"unparseable: {cmd!r}") from e
    if not argv or shlex.join(argv) != cmd:
        raise Unlisted(f"not in canonical form: {cmd!r}")
    for t in TEMPLATES:
        if t.matches(argv):
            return t
    raise Unlisted(f"no template: {cmd!r}")


# ── sessions and the service-level guard ────────────────────────────────────────────────────


@dataclass
class Session:
    """One device, one set of grades. Holds the audit log and runtime precondition state."""

    serial: str
    grades: frozenset[Grade]
    device: adbutils.AdbDevice | None = None
    log: list[tuple[str, str]] = field(default_factory=list)
    clock: Callable[[], float] = time.monotonic
    install_size: int | None = None  # one streamed install of exactly this size; see install()

    def record(self, verdict: str, request: str) -> None:
        self.log.append((verdict, request.replace(self.serial, "<serial>")))

    # preconditions: True admits; they may issue READ requests of their own. They run under the
    # guard lock, so the send they admit follows with nothing in between.

    def broadcast_spacing(self) -> bool:
        global _last_broadcast  # process-wide, so a fresh session cannot reset the spacing
        now = self.clock()
        if now - _last_broadcast < BROADCAST_SPACING_S:
            return False
        _last_broadcast = now
        return True

    def release_fgs_running(self) -> bool:
        if self.device is None:
            return False
        out = self.device.shell(["dumpsys", "activity", "services", RELEASE_PKG])
        return fgs_in_dump(out, RELEASE_PKG)


def fgs_in_dump(dump: str, package: str) -> bool:
    """Whether a `dumpsys activity services` dump shows `package`'s monitoring service in the
    foreground. Newer builds append ` c:<client package>` inside the record's braces."""
    record = re.compile(
        r"\* ServiceRecord\{[^}]*\s" + re.escape(package) + r"/("
        + re.escape(MONITORING_SERVICE) + r"|\.app\.runtime\.AmbientMonitoringService)( c:[\w.]+)?\}"
    )
    blocks = re.split(r"(?m)^\s*(?=\* ServiceRecord\{)", dump)
    return any(record.match(b) and re.search(r"isForeground=true", b) for b in blocks)


# Held from admission through transmission, so a request is sent under the session that admitted
# it. A threat-model note: the guard confines library internals and harness code that uses the
# public APIs; it is not a sandbox against code that writes to a raw socket or calls the saved
# originals on purpose. test_boundary_tripwire.py scans the harness for exactly that.
_lock = threading.RLock()
_active: Session | None = None
_last_broadcast = float("-inf")
_sync_ok = threading.local()  # .pending: one admitted sync request's grade, consumed by "sync:"

_HOST_READS = frozenset({"host:version", "host:devices", "host:devices-l", "host:features",
                         "host:host-features"})
_SERIAL_READS = frozenset({"features", "get-state"})


def _admit_service(s: Session, req: str) -> Grade:
    """Judge one adb service request; return its grade or raise."""
    if req in _HOST_READS:
        return Grade.READ
    if req in (f"host:transport:{s.serial}", f"host:tport:serial:{s.serial}"):
        return Grade.READ
    prefix = f"host-serial:{s.serial}:"
    if req.startswith(prefix) and req[len(prefix):] in _SERIAL_READS:
        return Grade.READ
    if req == f"tcp:{U2_PORT}":
        return Grade.HARNESS
    if m := _INSTALL.fullmatch(req):
        if s.install_size is None or int(m.group(1)) != s.install_size:
            raise PreconditionFailed("an install is admitted only from install() after the guard")
        s.install_size = None
        return Grade.MUTATE
    if req == "sync:":
        grade, _sync_ok.pending = getattr(_sync_ok, "pending", None), None
        if grade is None:
            raise Unlisted("sync: opened outside an admitted sync request")
        return grade
    for service, v1 in (("shell,v2:", False), ("shell:", True)):
        if req.startswith(service):
            t = admit_shell(req[len(service):], v1=v1)
            if t.grade not in s.grades:
                raise GradeNotAllowed(f"{t.name} is {t.grade.value}; session allows "
                                      f"{sorted(g.value for g in s.grades)}")
            if t.precondition and not getattr(s, t.precondition)():
                raise PreconditionFailed(f"{t.name}: {t.precondition} does not hold")
            return t.grade
    raise Unlisted(f"service not allowlisted: {req!r}")


# u2's own push: a regular file, mode 0755 (sync.py builds "<dst>,<S_IFREG|mode>"). The jar's
# content is pinned by the drift alarm's digest of the packaged asset.
_SYNC_REQUESTS = {
    ("STAT", U2_JAR): Grade.READ,
    ("SEND", f"{U2_JAR},{stat.S_IFREG | 0o755}"): Grade.HARNESS,
}


# The install guard reads the installed debug APK to compare certificates (`pm path` output).
INSTALLED_APK = re.compile(r"/data/app/(~~[A-Za-z0-9_=-]+/)?" + re.escape(DEBUG_PKG)
                           + r"-[A-Za-z0-9_=-]+/base\.apk")
# Streamed install (`adb install --streaming`): replace only, never -d, -g or -t.
_INSTALL = re.compile(r"exec:cmd package install -r -S ([1-9]\d{0,9})")


def _admit_sync(path: str, cmd: str) -> Grade:
    if (cmd, path) in _SYNC_REQUESTS:
        return _SYNC_REQUESTS[(cmd, path)]
    if cmd == "RECV" and INSTALLED_APK.fullmatch(path):
        return Grade.READ
    raise Unlisted(f"sync {cmd} {path!r} not allowlisted")


_orig_send_command = _adb.AdbConnection.send_command
_orig_prepare_sync = sync.Sync._prepare_sync


def _guarded_send_command(self, cmd: str):
    with _lock:
        s = _active
        if s is None:
            raise BoundaryViolation(f"no device session is open; refused {cmd!r}")
        try:
            grade = _admit_service(s, cmd)
            if grade not in s.grades:
                raise GradeNotAllowed(f"{grade.value} request in a session allowing "
                                      f"{sorted(g.value for g in s.grades)}: {cmd!r}")
        except BoundaryViolation:
            s.record("REFUSED", cmd)
            raise
        s.record("sent", cmd)
        return _orig_send_command(self, cmd)


@contextmanager
def _guarded_prepare_sync(self, path: str, cmd: str):
    # The lock spans the whole transfer: the session cannot close under a push.
    with _lock:
        s = _active
        if s is None:
            raise BoundaryViolation(f"no device session is open; refused sync {cmd} {path!r}")
        try:
            grade = _admit_sync(path, cmd)
            if grade not in s.grades:
                raise GradeNotAllowed(f"sync {cmd} is {grade.value}")
        except BoundaryViolation:
            s.record("REFUSED", f"sync {cmd} {path}")
            raise
        s.record("sent", f"sync {cmd} {path}")
        _sync_ok.pending = grade
        try:
            with _orig_prepare_sync(self, path, cmd) as c:
                yield c
        finally:
            _sync_ok.pending = None


_adb.AdbConnection.send_command = _guarded_send_command
sync.Sync._prepare_sync = _guarded_prepare_sync


# The socket path above is the only way out. adbutils can also run an `adb` binary: adb_output()
# passes argv straight to it, and a refused connection auto-starts a local server. Both resolve
# the binary through adb_path(), so every module's copy of it is replaced.
def _no_adb_binary(*_args, **_kwargs):
    raise BoundaryViolation("the adb binary is never run; the host server is reached over TCP")


for _module in (adbutils, adbutils._utils, adbutils._adb, adbutils._device_base,
                adbutils.screenrecord):
    _module.adb_path = _no_adb_binary
adbutils._device_base.BaseDevice.adb_output = _no_adb_binary


@contextmanager
def session(serial: str, grades: frozenset[Grade] = frozenset({Grade.READ}),
            client: adbutils.AdbClient | None = None) -> Iterator[Session]:
    """Open the one device session. Requests outside it, or for another serial, are refused.

    The device is bound by serial, never by transport id (which the guard cannot tie to a
    serial), so hand uiautomator2 `session.device`, not a serial to look up."""
    global _active
    if not serial or "<" in serial:
        raise BoundaryViolation("a real serial is required; never write one into a file")
    device = client.device(serial) if client is not None else None
    with _lock:
        if _active is not None:
            raise BoundaryViolation("a device session is already open")
        _active = Session(serial, frozenset(grades), device)
        s = _active
    try:
        yield s
    finally:
        with _lock:
            _active = None


def install(s: Session, apk: bytes) -> str:
    """Stream one APK to `cmd package install -r`. Only install.py calls this, after its guard
    and the owner's approval; the request is admitted once, for exactly this size."""
    if s is not _active or s.device is None or Grade.MUTATE not in s.grades:
        raise BoundaryViolation("an install needs the open MUTATE session")
    with _lock:
        s.install_size = len(apk)
    try:
        with s.device.open_transport() as c:
            c.send_command(f"exec:cmd package install -r -S {len(apk)}")
            c.check_okay()
            c.conn.sendall(apk)
            return c.read_until_close()
    finally:
        s.install_size = None


def run(s: Session, *argv: str, raw: bool = False):
    """Run one templated command. Checked here to fail before a connection opens, and again
    at the service guard, which is the binding check. `raw` returns bytes output."""
    if s is not _active or s.device is None:
        raise BoundaryViolation("not the open session, or it has no device")
    if (grade := admit_shell(shlex.join(argv)).grade) not in s.grades:
        raise GradeNotAllowed(f"{grade.value} command in a session allowing "
                              f"{sorted(g.value for g in s.grades)}")
    return s.device.shell2(list(argv), encoding=None if raw else "utf-8")
