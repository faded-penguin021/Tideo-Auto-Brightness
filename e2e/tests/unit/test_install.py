"""The install guard's decisions, and the local tools it runs (never adb)."""

from pathlib import Path

import pytest

from tideo_e2e import apktools, install
from tideo_e2e.device import DEBUG_PKG
from tideo_e2e.install import ApkFacts, InstallRefused, problems

A, B = "a" * 64, "b" * 64
MANIFEST = (f'<?xml version="1.0" encoding="utf-8"?>\n<manifest '
            f'xmlns:android="http://schemas.android.com/apk/res/android" '
            f'android:versionCode="26" android:versionName="1.12.0-debug" package="{DEBUG_PKG}">'
            f'</manifest>')


def _new(**kw):
    return ApkFacts(**{"package": DEBUG_PKG, "version_code": 26, "version_name": "1.12.0-debug",
                       "signers": (A,), **kw})


def test_manifest_and_signers_parse():
    assert install.parse_manifest(MANIFEST) == (DEBUG_PKG, 26, "1.12.0-debug")
    out = f"Signer #1 certificate DN: CN=Someone\nSigner #1 certificate SHA-256 digest: {A}\n"
    assert install.parse_signers(out) == (A,)


def test_a_matching_newer_or_equal_build_passes():
    assert problems(_new(), 26, (A,)) == []
    assert problems(_new(version_code=27), 26, (A,)) == []


@pytest.mark.parametrize("new,code,have", [
    (_new(package="com.tideo.autobrightness"), 26, (A,)),
    (_new(signers=(B,)), 26, (A,)),
    (_new(signers=(A, B)), 26, (A, B)),
    (_new(), 26, ()),
    (_new(version_code=25), 26, (A,)),
])
def test_guard_refuses(new, code, have):
    assert problems(new, code, have)


def test_a_first_install_is_judged_on_the_new_apk_alone():
    assert problems(_new(), None, None) == []
    assert problems(_new(signers=(A, B)), None, None)
    assert problems(_new(signers=()), None, None)
    assert problems(_new(package="com.tideo.autobrightness"), None, None)


def test_a_first_install_needs_the_package_absent_for_every_user():
    from .test_state import PACKAGE_DUMP
    assert install.installed_anywhere(PACKAGE_DUMP)
    assert not install.installed_anywhere(f"Unable to find package: {DEBUG_PKG}\n")
    for unreadable in ("", "Packages:\n  Package [com.other] (1a2b):\n",
                       f"Packages:\n  Package [{DEBUG_PKG}] (?):\n"):
        with pytest.raises(InstallRefused):
            install.installed_anywhere(unreadable)


@pytest.mark.parametrize("out", [
    "", f"package:/data/app/~~x/{DEBUG_PKG}-y/base.apk\n"
        f"package:/data/app/~~x/{DEBUG_PKG}-y/split_config.en.apk\n",
    f"package:/data/local/tmp/{DEBUG_PKG}.apk\n",
])
def test_installed_path_must_be_one_base_apk(out):
    with pytest.raises(InstallRefused):
        install.installed_apk_path(out)


def test_local_tools_are_the_sdk_ones(tmp_path, monkeypatch):
    jar = tmp_path / "build-tools" / "36.0.0" / "lib" / "apksigner.jar"
    jar.parent.mkdir(parents=True)
    jar.touch()
    monkeypatch.setenv("JAVA_HOME", "/jdk")
    apk = Path("/x.apk")
    assert apktools.argv_manifest(tmp_path, apk) == [
        str(tmp_path / "cmdline-tools/latest/bin/apkanalyzer"), "manifest", "print", "/x.apk"]
    assert apktools.argv_signers(tmp_path, apk) == [
        "/jdk/bin/java", "-jar", str(jar), "verify", "--print-certs", "/x.apk"]


def test_a_pending_journal_refuses_the_install(tmp_path):
    from tideo_e2e.journal import Identity, Journal
    with Journal.for_run(tmp_path, Identity("d", 0, "t", 26, "c")) as j:
        j.watch("setting", "system/screen_brightness", "10")
    (tmp_path / "x.apk").write_bytes(b"apk")
    with pytest.raises(InstallRefused):
        install.run_install(tmp_path / "x.apk", "emu01", tmp_path, None, ask=lambda _p: "")
