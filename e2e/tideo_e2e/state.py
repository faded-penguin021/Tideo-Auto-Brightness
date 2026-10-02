"""Read-only state readers: settings, dumpsys, Tideo's private stores.

The parsers are pure (text in, value out) so the unit tests pin them on captured shapes; the
readers issue only READ-grade templates through `device.run`.
"""

from __future__ import annotations

import json
import re
import struct
from pathlib import Path

from . import device
from .device import DEBUG_PKG, WRITE_SECURE_SETTINGS
from .journal import Identity, serial_digest

OVERRIDE_CHANNEL = "manual_override"
MONITORING_CHANNEL = "ambient_monitoring"
ONGOING_ID = "1001"

# Tideo's private stores, relative to its data dir (device._PRIVATE_FILE admits these).
SETTINGS_STORE = "files/datastore/aab_settings.json"
CONTROL_STORE = "files/datastore/control_prefs.preferences_pb"
SAVED_MODE_STORE = "shared_prefs/screen_brightness_controller.xml"
PREF_STORES = {"aab_settings": SETTINGS_STORE, "control_prefs": CONTROL_STORE}
# The preferences the harness may read, with the app's default. Both stores omit a value equal
# to its default (kotlinx JSON without encodeDefaults; DataStore keys never written), so an
# absent value IS the default. Values are as each store renders them.
PREF_DEFAULTS = {
    "aab_settings/serviceEnabled": "true",
    "aab_settings/contextOverride": "false",
    "aab_settings/panicRequiresPlugged": "false",
    "control_prefs/external_control_enabled": "false",
    "control_prefs/force_dark_enabled": "false",
}


class StateError(RuntimeError):
    pass


# ── pure parsers ────────────────────────────────────────────────────────────────────────────


def setting_value(out: str) -> str | None:
    """`settings get` prints the literal `null` for an absent row."""
    value = out.strip()
    return None if value == "null" else value


def package_section(dump: str, package: str) -> str:
    """The `Package [pkg]` block of a `dumpsys package` dump, or raise if not installed."""
    m = re.search(r"(?m)^  Package \[" + re.escape(package) + r"\] \([0-9a-f]+\):\n"
                  r"((?:    .*\n|\n)*)", dump)
    if not m:
        raise StateError(f"{package} is not installed")
    return m.group(1)


def _user0_block(sec: str) -> str:
    """User 0's header line and its indented detail, or "" unless it is installed for user 0."""
    m = re.search(r"(?m)^    User 0:.*\n(?:      .*\n)*", sec)
    return m.group(0) if m and " installed=true" in m.group(0).splitlines()[0] else ""


def package_identity(dump: str, package: str) -> tuple[str, int, str]:
    """(firstInstallTime, versionCode, signature hash) of an installed package, for user 0.

    Android 14+ keeps firstInstallTime per user, inside the `User N:` block; older releases
    print one at package level. The signature hash is the platform's own short digest from
    `signatures:[…]`: it binds the journal to one signing identity, not to a full certificate
    (the install guard checks that)."""
    sec = package_section(dump, package)
    user0 = _user0_block(sec)
    if not user0:
        raise StateError(f"{package} is not installed for user 0")
    first = (re.search(r"firstInstallTime=(.+)", user0)
             or re.search(r"(?m)^    firstInstallTime=(.+)", sec))
    code = re.search(r"versionCode=(\d+)", sec)
    sig = re.search(r"signatures:\[([0-9a-f, ]+)\]", sec)
    if not (first and code and sig):
        raise StateError(f"{package}: identity fields missing from dumpsys package")
    return first.group(1).strip(), int(code.group(1)), sig.group(1).replace(" ", "")


def grant_state(dump: str, package: str) -> str:
    """WRITE_SECURE_SETTINGS on `package` for user 0, as "granted" or "revoked" (the journal's
    GRANT values). A grant that differs between users carries a `userId=` suffix; only user 0's
    line, or one without a suffix, counts. Two disagreeing lines for user 0 are refused."""
    sec = package_section(dump, package)
    seen = {granted for granted, user in re.findall(
        re.escape(WRITE_SECURE_SETTINGS) + r": granted=(true|false)(?:, [^\n]*?userId=(\d+))?",
        sec) if user in ("", "0")}
    if len(seen) > 1:
        raise StateError(f"{package}: user 0's {WRITE_SECURE_SETTINGS} grant is ambiguous")
    return "granted" if seen == {"true"} else "revoked"


_NOTIFICATION = re.compile(r"NotificationRecord\(0x[0-9a-f]+: pkg=(\S+) .*?"
                           r"Notification\(channel=(\S+)")


def notification_channels(dump: str, package: str) -> frozenset[str]:
    """Channels of the notifications `package` has posted, from `dumpsys notification`."""
    return frozenset(ch for pkg, ch in _NOTIFICATION.findall(dump) if pkg == package)


_RECORD = re.compile(r"(?m)^\s*(?=NotificationRecord\()")
_ACTION = re.compile(r'(?m)^\s*\[\d+\] "(.*)" -> ')


def paused_in_dump(dump: str, package: str) -> bool:
    """Whether Tideo's ongoing notification (id 1001) offers Resume: it does exactly while the
    pipeline is paused, by PAUSE or by an override (AmbientMonitoringService.buildNotification).
    An actions block whose titles cannot be read is refused rather than read as unpaused."""
    for block in _RECORD.split(dump):
        if not re.match(r"NotificationRecord\(0x[0-9a-f]+: pkg=" + re.escape(package)
                        + r" .*? id=" + ONGOING_ID + r" ", block):
            continue
        titles = _ACTION.findall(block)
        if "actions={" in block and not titles:
            raise StateError("notification actions are present but their titles are unreadable")
        return "Resume" in titles
    return False


def current_user(out: str) -> int:
    value = out.strip()
    if not value.isdigit():
        raise StateError(f"unexpected `am get-current-user` output: {value!r}")
    return int(value)


# A Preferences DataStore file is a protobuf PreferenceMap: map<string, Value> preferences = 1.
# Only the scalar Value cases Tideo writes are decoded; anything else is reported, not guessed.
_VALUE_FIELDS = {1: "boolean", 2: "float", 3: "integer", 4: "long", 5: "string", 7: "double"}


def _varint(buf: bytes, i: int) -> tuple[int, int]:
    shift = out = 0
    while True:
        if i >= len(buf):
            raise StateError("truncated protobuf varint")
        b = buf[i]
        i += 1
        out |= (b & 0x7F) << shift
        if not b & 0x80:
            return out, i
        shift += 7


def _fields(buf: bytes):
    i = 0
    while i < len(buf):
        tag, i = _varint(buf, i)
        number, wire = tag >> 3, tag & 7
        if wire == 0:
            value, i = _varint(buf, i)
        elif wire == 1:
            value, i = buf[i:i + 8], i + 8
        elif wire == 2:
            n, i = _varint(buf, i)
            value, i = buf[i:i + n], i + n
        elif wire == 5:
            value, i = buf[i:i + 4], i + 4
        else:
            raise StateError(f"unsupported protobuf wire type {wire}")
        if i > len(buf):
            raise StateError("truncated protobuf field")
        yield number, wire, value


def _pref_value(buf: bytes) -> str:
    for number, _wire, raw in _fields(buf):
        kind = _VALUE_FIELDS.get(number)
        if kind == "boolean":
            return "true" if raw else "false"
        if kind in ("integer", "long"):
            return str(raw - (1 << 64) if raw >= 1 << 63 else raw)
        if kind == "string":
            return raw.decode("utf-8")
        if kind == "float":
            return repr(struct.unpack("<f", raw)[0])
        if kind == "double":
            return repr(struct.unpack("<d", raw)[0])
    raise StateError("preference value of an undecoded type")


def preferences(blob: bytes) -> dict[str, str]:
    """A Preferences DataStore file as {key: value-as-text}."""
    out = {}
    for number, wire, entry in _fields(blob):
        if number != 1 or wire != 2:
            continue
        key = value = None
        for n, _w, raw in _fields(entry):
            if n == 1:
                key = raw.decode("utf-8")
            elif n == 2:
                value = _pref_value(raw)
        if key is not None and value is not None:
            out[key] = value
    return out


def json_settings(text: str) -> dict[str, str]:
    """Tideo's JSON settings store as {field: value-as-text}; JSON scalars only, as JSON text."""
    doc = json.loads(text)
    if not isinstance(doc, dict):
        raise StateError("settings store is not a JSON object")
    return {k: json.dumps(v) for k, v in doc.items() if not isinstance(v, (dict, list))}


# ── device readers (READ-grade templates only) ──────────────────────────────────────────────


def _out(s: device.Session, *argv: str) -> str:
    r = device.run(s, *argv)
    if r.returncode != 0:
        raise StateError(f"{argv[0]} {argv[1]} exited {r.returncode}")
    return r.output


def read_setting(s: device.Session, namespace: str, key: str) -> str | None:
    return setting_value(_out(s, "settings", "get", namespace, key))


def package_dump(s: device.Session, package: str = DEBUG_PKG) -> str:
    return _out(s, "dumpsys", "package", package)


def service_running(s: device.Session, package: str = DEBUG_PKG) -> bool:
    return device.fgs_in_dump(_out(s, "dumpsys", "activity", "services", package), package)


def channels(s: device.Session, package: str = DEBUG_PKG) -> frozenset[str]:
    return notification_channels(_out(s, "dumpsys", "notification"), package)


def private_file(s: device.Session, path: str) -> bytes | None:
    """One file from Tideo's data dir, or None when it does not exist yet."""
    r = device.run(s, "run-as", DEBUG_PKG, "cat", path, raw=True)
    if r.returncode != 0:
        if b"No such file" in r.output:
            return None
        raise StateError(f"run-as cat {path} exited {r.returncode}")
    return r.output


def read_pref(s: device.Session, key: str) -> str:
    """`<store>/<key>` from PREF_DEFAULTS, e.g. `control_prefs/external_control_enabled`."""
    if key not in PREF_DEFAULTS:
        raise StateError(f"unknown preference {key!r}")
    store, _, name = key.partition("/")
    blob = private_file(s, PREF_STORES[store])
    if blob is None:
        return PREF_DEFAULTS[key]
    values = json_settings(blob.decode("utf-8")) if store == "aab_settings" else preferences(blob)
    return values.get(name, PREF_DEFAULTS[key])


def identity(s: device.Session, store: Path) -> Identity:
    # run-as, settings and the dumpsys parsers all act on the current user; only user 0 is bound.
    user = current_user(_out(s, "am", "get-current-user"))
    if user != 0:
        raise StateError(f"the current Android user is {user}; the harness runs only as user 0")
    serial = _out(s, "getprop", "ro.serialno").strip()
    if not serial:
        raise StateError("ro.serialno is empty; the device cannot be bound")
    first, code, cert = package_identity(package_dump(s), DEBUG_PKG)
    return Identity(serial_digest(store, serial), user, first, code, cert)
