"""Pure state parsers, pinned on captured output shapes."""

from __future__ import annotations

import struct

import pytest

from tideo_e2e import state
from tideo_e2e.device import DEBUG_PKG, RELEASE_PKG

PACKAGE_DUMP = f"""\
Packages:
  Package [{DEBUG_PKG}] (8c1f2e0):
    userId=10345
    versionCode=26 minSdk=31 targetSdk=36
    versionName=1.12.0-debug
    timeStamp=2026-09-28 10:01:02
    lastUpdateTime=2026-09-28 10:01:03
    signatures=PackageSignatures{{4b1d2a7 version:2, signatures:[a1b2c3d4], past signatures:[]}}
    install permissions:
      android.permission.WRITE_SETTINGS: granted=true
      android.permission.WRITE_SECURE_SETTINGS: granted=true
    User 0: ceDataInode=1 installed=true hidden=false suspended=false stopped=false
      installReason=0
      firstInstallTime=2026-06-23 18:44:10
      uninstallReason=0
    User 10: ceDataInode=2 installed=true hidden=false suspended=false stopped=false
      firstInstallTime=2026-09-30 09:00:00

  Package [{RELEASE_PKG}] (3e2d1c0):
    versionCode=25 minSdk=31 targetSdk=36
    firstInstallTime=2026-01-01 00:00:00
    signatures=PackageSignatures{{9 version:2, signatures:[ffff0000], past signatures:[]}}
    User 0: ceDataInode=3 installed=true hidden=false suspended=false stopped=false
"""


def test_setting_value():
    assert state.setting_value("128\n") == "128"
    assert state.setting_value("null\n") is None
    assert state.setting_value("\n") == ""


def test_package_identity_reads_user_0_in_its_own_block():
    # Android 14+: per-user install time, user 0's, never user 10's.
    assert state.package_identity(PACKAGE_DUMP, DEBUG_PKG) == (
        "2026-06-23 18:44:10", 26, "a1b2c3d4")
    # Older releases: one package-level install time.
    assert state.package_identity(PACKAGE_DUMP, RELEASE_PKG) == (
        "2026-01-01 00:00:00", 25, "ffff0000")


def test_package_identity_refuses_a_missing_package_user_or_field():
    with pytest.raises(state.StateError, match="not installed"):
        state.package_identity("Packages:\n", DEBUG_PKG)
    with pytest.raises(state.StateError, match="user 0"):
        state.package_identity(PACKAGE_DUMP.replace("    User 0:", "    User 11:"), DEBUG_PKG)
    with pytest.raises(state.StateError, match="user 0"):
        state.package_identity(PACKAGE_DUMP.replace("ceDataInode=1 installed=true",
                                                    "ceDataInode=1 installed=false"), DEBUG_PKG)
    with pytest.raises(state.StateError, match="missing"):
        state.package_identity(PACKAGE_DUMP.replace("signatures:[a1b2c3d4]", "x"), DEBUG_PKG)


_GRANT = "android.permission.WRITE_SECURE_SETTINGS: granted=true"


def test_grant_state():
    assert state.grant_state(PACKAGE_DUMP, DEBUG_PKG) == "granted"
    revoked = PACKAGE_DUMP.replace(_GRANT, _GRANT.replace("true", "false"))
    assert state.grant_state(revoked, DEBUG_PKG) == "revoked"
    # The release block's grants never leak into the debug answer, nor the other way round.
    assert state.grant_state(PACKAGE_DUMP, RELEASE_PKG) == "revoked"


def test_grant_state_is_user_0s():
    per_user = PACKAGE_DUMP.replace(_GRANT, _GRANT.replace("true", "false") + ", userId=0\n      "
                                    + _GRANT + ", flags=[ ], userId=10")
    assert state.grant_state(per_user, DEBUG_PKG) == "revoked"
    clash = PACKAGE_DUMP.replace(_GRANT, _GRANT + "\n      " + _GRANT.replace("true", "false"))
    with pytest.raises(state.StateError, match="ambiguous"):
        state.grant_state(clash, DEBUG_PKG)


NOTIFICATIONS = f"""\
  Notification List:
    NotificationRecord(0x0a1b2c3d: pkg={DEBUG_PKG} user=UserHandle{{0}} id=1001 tag=null \
importance=2 key=0|{DEBUG_PKG}|1001|null|10345: Notification(channel=ambient_monitoring \
shortcut=null contentView=null vibrate=null sound=null defaults=0 flags=ONGOING_EVENT))
    NotificationRecord(0x0d0e0f00: pkg={RELEASE_PKG} user=UserHandle{{0}} id=1002 tag=null \
importance=4 key=0|{RELEASE_PKG}|1002|null|10346: Notification(channel=manual_override \
shortcut=null))
"""


def test_notification_channels_are_per_package():
    assert state.notification_channels(NOTIFICATIONS, DEBUG_PKG) == {"ambient_monitoring"}
    assert state.notification_channels(NOTIFICATIONS, RELEASE_PKG) == {"manual_override"}
    assert state.notification_channels("", DEBUG_PKG) == frozenset()


def ongoing(*actions: str, pkg: str = DEBUG_PKG, nid: int = 1001) -> str:
    """One `dumpsys notification` record as NotificationRecord.dump prints it."""
    lines = [f"    NotificationRecord(0x0a1b2c3d: pkg={pkg} user=UserHandle{{0}} id={nid} "
             f"tag=null importance=2 key=0|{pkg}|{nid}|null|10345: Notification("
             "channel=ambient_monitoring shortcut=null flags=ONGOING_EVENT))",
             "      uid=10345 userId=0"]
    if actions:
        lines.append("      actions={")
        lines += [f'        [{i}] "{a}" -> PendingIntent{{1f{i}: PendingIntentRecord}}'
                  for i, a in enumerate(actions)]
        lines.append("      }")
    return "\n".join(lines) + "\n"


def test_paused_is_resume_on_the_ongoing_notification():
    assert state.paused_in_dump(ongoing("Resume", "Reset", "Disable"), DEBUG_PKG)
    assert state.paused_in_dump(ongoing("Discard", "Resume", "Disable"), DEBUG_PKG)
    assert not state.paused_in_dump(ongoing("Reset", "Disable"), DEBUG_PKG)
    assert not state.paused_in_dump("", DEBUG_PKG)  # service off: no notification
    # Another package's, or another id's, Resume says nothing about Tideo debug.
    assert not state.paused_in_dump(ongoing("Resume", pkg=RELEASE_PKG) + ongoing("Reset"),
                                    DEBUG_PKG)
    assert not state.paused_in_dump(ongoing("Resume", nid=7), DEBUG_PKG)


def test_paused_refuses_unreadable_actions():
    redacted = ongoing("Resume").replace('"Resume"', "Resume")
    with pytest.raises(state.StateError, match="unreadable"):
        state.paused_in_dump(redacted, DEBUG_PKG)


def test_current_user():
    assert state.current_user("0\n") == 0
    with pytest.raises(state.StateError):
        state.current_user("Error: unknown command\n")


# ── a hand-encoded Preferences DataStore file ──


def _varint(n: int) -> bytes:
    out = b""
    while True:
        b, n = n & 0x7F, n >> 7
        if n:
            out += bytes([b | 0x80])
        else:
            return out + bytes([b])


def _ld(field: int, payload: bytes) -> bytes:
    return _varint(field << 3 | 2) + _varint(len(payload)) + payload


def _entry(key: str, value: bytes) -> bytes:
    return _ld(1, _ld(1, key.encode()) + _ld(2, value))


def prefs_blob(**values) -> bytes:
    """Encode {key: bool|int|str|float} the way androidx DataStore Preferences does."""
    out = b""
    for key, v in values.items():
        if isinstance(v, bool):
            value = _varint(1 << 3 | 0) + _varint(int(v))
        elif isinstance(v, int):
            value = _varint(3 << 3 | 0) + _varint(v & (1 << 64) - 1)
        elif isinstance(v, str):
            value = _ld(5, v.encode())
        else:
            value = _varint(2 << 3 | 5) + struct.pack("<f", v)
        out += _entry(key, value)
    return out


def test_preferences_decode():
    blob = prefs_blob(external_control_enabled=True, force_dark_enabled=False, n=-3, s="ok",
                      f=0.5)
    assert state.preferences(blob) == {
        "external_control_enabled": "true", "force_dark_enabled": "false", "n": "-3", "s": "ok",
        "f": "0.5",
    }
    assert state.preferences(b"") == {}


def test_preferences_refuse_damage_rather_than_guess():
    with pytest.raises(state.StateError):
        state.preferences(prefs_blob(external_control_enabled=True)[:-1])
    string_set = _entry("k", _ld(6, b""))
    with pytest.raises(state.StateError, match="undecoded"):
        state.preferences(string_set)


def test_json_settings_keeps_scalars_as_json_text():
    doc = '{"serviceEnabled": true, "contextOverride": false, "profile": "Home", "curve": [1]}'
    assert state.json_settings(doc) == {
        "serviceEnabled": "true", "contextOverride": "false", "profile": '"Home"'}
    with pytest.raises(state.StateError):
        state.json_settings("[]")
