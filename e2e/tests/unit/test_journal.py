"""The journal: lock, write-ahead persistence, identity binding, pending-journal refusal."""

import json
import os
import stat

import pytest

from tideo_e2e import journal as journal_mod
from tideo_e2e.journal import (
    GRANT, PREF, SETTING, Identity, IdentityMismatch, Journal, JournalError, JournalLocked,
    PendingJournal, UnrestorableOriginal, serial_digest,
)

ID = Identity("d" * 64, 0, "2026-09-01 10:00:00", 26, "c" * 64)


def on_disk(store):
    return json.loads((store / "journal.json").read_text())


def test_store_and_files_are_private(tmp_path):
    store = tmp_path / "tideo-e2e"
    with Journal.for_run(store, ID):
        pass
    assert stat.S_IMODE(store.stat().st_mode) == 0o700
    assert stat.S_IMODE((store / "journal.json").stat().st_mode) == 0o600


def test_second_opener_is_locked_out_until_release(tmp_path):
    j = Journal.for_run(tmp_path, ID)
    with pytest.raises(JournalLocked):
        Journal.for_recovery(tmp_path)
    j.release()
    Journal.for_recovery(tmp_path).release()


def test_a_journal_on_disk_blocks_the_next_run_and_releases_the_lock(tmp_path):
    Journal.for_run(tmp_path, ID).release()
    with pytest.raises(PendingJournal):
        Journal.for_run(tmp_path, ID)
    Journal.for_recovery(tmp_path).release()  # the refusal did not keep the lock


def test_entry_is_on_disk_before_expect_returns(tmp_path):
    with Journal.for_run(tmp_path, ID) as j:
        j.expect(SETTING, "system/screen_brightness", "120", "4095")
        [row] = on_disk(tmp_path)["entries"]
        assert row["original"] == "120" and row["expected"] == "4095"


def test_watch_keeps_the_first_original_and_attributes_only_the_latest(tmp_path):
    key = "aab_settings/dimmingStrength"
    with Journal.for_run(tmp_path, ID) as j:
        j.watch(PREF, key, "25")
        j.watch(PREF, key, "999")
        j.observe(PREF, key, "30")
        j.expect(PREF, key, "30", "65")  # write-ahead: either may be on the device after a kill
        row = on_disk(tmp_path)["entries"][0]
        assert (row["attributable"], row["expected"]) == (["30"], "65")
        j.observe(PREF, key, "65")
        j.expect(PREF, key, "65", "64")
        j.observe(PREF, key, "64")
        j.expect(PREF, key, "64", "65")
        j.observe(PREF, key, "65")
        # 64 was the run's once; if it reappears now, the owner chose it (S2–S4 review).
        [row] = on_disk(tmp_path)["entries"]
        assert row["original"] == "25" and row["attributable"] == ["65"]
        j.observe(PREF, key, "25")  # back at the original
        assert on_disk(tmp_path)["entries"][0]["attributable"] == []


def test_absent_original_is_refused(tmp_path):
    with Journal.for_run(tmp_path, ID) as j:
        with pytest.raises(UnrestorableOriginal):
            j.watch(SETTING, "secure/doze_always_on", None)
        j.watch(GRANT, "perm", "granted")
        with pytest.raises(UnrestorableOriginal):
            j.observe(GRANT, "perm", None)


def test_identity_round_trips_and_mismatch_names_the_field(tmp_path):
    with Journal.for_run(tmp_path, ID) as j:
        j.set_runtime(True, False)
    with Journal.for_recovery(tmp_path) as j:
        j.check_identity(ID)
        other = Identity(ID.serial_digest, ID.user, "2026-09-02 00:00:00", 26, ID.cert_digest)
        with pytest.raises(IdentityMismatch, match="first_install_time"):
            j.check_identity(other)


def test_unbound_journal_is_refused(tmp_path):
    with Journal.for_run(tmp_path, ID) as j:
        j.set_runtime(True, False)
    doc = on_disk(tmp_path)
    doc["identity"] = None
    (tmp_path / "journal.json").write_text(json.dumps(doc))
    with pytest.raises(JournalError, match="identity"):
        Journal.for_recovery(tmp_path)
    with pytest.raises(JournalError, match="identity"):  # not JournalLocked: lock released
        Journal.for_recovery(tmp_path)


def test_crash_mid_persist_leaves_the_previous_journal(tmp_path, monkeypatch):
    with Journal.for_run(tmp_path, ID) as j:
        j.expect(SETTING, "system/screen_brightness", "120", "4095")

        def torn(*_a):
            raise OSError("power cut")
        monkeypatch.setattr(journal_mod.os, "replace", torn)
        with pytest.raises(OSError):
            j.expect(SETTING, "system/screen_brightness_mode", "1", "0")
        monkeypatch.undo()
        # Memory is ahead of the disk: a retry must not skip the write it never made.
        with pytest.raises(JournalError, match="write failed"):
            j.expect(SETTING, "system/screen_brightness_mode", "1", "0")
    with Journal.for_recovery(tmp_path) as j:
        assert list(j.entries) == ["setting:system/screen_brightness"]


def test_a_released_journal_cannot_delete_the_next_holders_file(tmp_path):
    a = Journal.for_run(tmp_path, ID)
    a.release()
    with Journal.for_recovery(tmp_path) as b:
        b.set_runtime(True, False)
        with pytest.raises(JournalError, match="released"):
            a.close_clean()
    assert (tmp_path / "journal.json").exists()


def test_damaged_salt_is_refused(tmp_path):
    serial_digest(tmp_path, "emu01")
    (tmp_path / "salt").write_bytes(b"short")
    with pytest.raises(JournalError, match="damaged"):
        serial_digest(tmp_path, "emu01")


def test_only_conflicts_resolve_and_only_empty_closes(tmp_path):
    with Journal.for_run(tmp_path, ID) as j:
        e = j.watch(SETTING, "system/screen_brightness", "120")
        with pytest.raises(JournalError):
            j.resolve(e.id)
        with pytest.raises(JournalError):
            j.close_clean()
        j.mark_conflict(e, "77")
        j.resolve(e.id)
        j.close_clean()
    assert not (tmp_path / "journal.json").exists()


def test_serial_digest_is_salted_stable_and_never_the_serial(tmp_path):
    a = serial_digest(tmp_path / "a", "emu01")
    assert a == serial_digest(tmp_path / "a", "emu01")
    assert a != serial_digest(tmp_path / "b", "emu01")
    assert "emu01" not in a
    assert stat.S_IMODE(os.stat(tmp_path / "a" / "salt").st_mode) == 0o600


def test_an_expected_value_survives_observations_until_it_lands(tmp_path):
    # Apply commits asynchronously: observations before it lands still read the old value.
    key = "aab_settings/dimmingStrength"
    with Journal.for_run(tmp_path, ID) as j:
        j.expect(PREF, key, "30", "65")
        j.observe(PREF, key, "30")  # the edit, before Apply
        [row] = on_disk(tmp_path)["entries"]
        assert row["expected"] == "65" and row["attributable"] == []
        j.observe(PREF, key, "65")  # Apply landed
        [row] = on_disk(tmp_path)["entries"]
        assert row["expected"] is None and row["attributable"] == ["65"]
