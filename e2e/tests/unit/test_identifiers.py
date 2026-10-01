"""No personal identifier shapes in e2e/, and the scan itself catches what it claims to."""

import pytest

from tideo_e2e.identifiers import scan_text, scan_tree
from tideo_e2e.scenarios import E2E_ROOT


def test_e2e_tree_is_clean():
    assert scan_tree(E2E_ROOT) == []


@pytest.mark.parametrize("text,kind", [
    ("contact someone@example.org", "email"),  # identifier-fixture
    ("adb connect 192.168.1.20:5555", "adb_wireless"),  # identifier-fixture
    ("host 10.0.0.7", "private_ipv4"),  # identifier-fixture
    (r"C:\Users\alice\AppData\Local\Android", "windows_user_path"),  # identifier-fixture
    ("C:/Users/alice/platform-tools", "windows_user_path"),  # identifier-fixture
    ("/home/alice/.android/adbkey", "unix_home_path"),  # identifier-fixture
    ("/Users/alice/Library", "unix_home_path"),  # identifier-fixture
    ("device R5CT12ABCDE online", "serial_token"),  # identifier-fixture
    ("adb -s 1a2b3c4d shell", "serial_context"),  # identifier-fixture
    ("ANDROID_SERIAL=1a2b3c4d", "serial_context"),  # identifier-fixture
    ('serial = "emulator-5554"', "serial_context"),  # identifier-fixture
    ('SSID: "HomeNet"', "ssid_context"),  # identifier-fixture
    ('{"ssid": "HomeNet"}', "ssid_context"),  # identifier-fixture
    ('{"serial": "1a2b3c4d"}', "serial_context"),  # identifier-fixture
    ('ANDROID_SERIAL="1a2b3c4d"', "serial_context"),  # identifier-fixture
    ("1a2b3c4d\tdevice", "adb_devices_line"),  # identifier-fixture
])
def test_scan_catches(text, kind):
    assert kind in {h.kind for h in scan_text(text)}


@pytest.mark.parametrize("text", [
    r"C:\Users\<user>\platform-tools",
    "/home/<user>/.local/bin",
    "adb -s <serial> shell",
    'ANDROID_SERIAL=$SERIAL',
    "host.docker.internal:5037 and 127.0.0.1",
    "WRITE_SECURE_SETTINGS screen_brightness_mode",
    "uiautomator2==3.7.0",
    'ssid = "<ssid>"',
])
def test_scan_spares_placeholders(text):
    assert scan_text(text) == []


def test_pragma_only_honoured_in_its_own_file(tmp_path):
    (tmp_path / "notes.md").write_text("adb -s 1a2b3c4d shell  # identifier-fixture\n")  # identifier-fixture
    assert [h.kind for h in scan_tree(tmp_path)] == ["serial_context"]


def test_binary_files_are_reported_not_skipped(tmp_path):
    (tmp_path / "blob.bin").write_bytes(b"\xff\xfe\x00")
    assert [h.kind for h in scan_tree(tmp_path)] == ["unscannable_binary"]
