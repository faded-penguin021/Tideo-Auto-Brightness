"""Recovery converges from an interruption at every port call and every journal write, refuses a
foreign device before touching it, and never overwrites a conflict."""

from __future__ import annotations

import pytest

from tideo_e2e import journal as journal_mod
from tideo_e2e import recovery
from tideo_e2e.device import WRITE_SECURE_SETTINGS
from tideo_e2e.journal import GRANT, PREF, SETTING, Identity, IdentityMismatch, Journal
from tideo_e2e.recovery import Unstable, journal_effects, observe_effects, recover

ID = Identity("d" * 64, 0, "2026-09-01 10:00:00", 26, "c" * 64)
BRIGHT = "system/screen_brightness"
MODE = "system/screen_brightness_mode"
DOZE = "secure/doze_always_on"


class Interrupted(BaseException):
    """A kill: not an Exception, so nothing in the code under test can swallow it."""


class FakeDevice:
    """Settings, grant, prefs and service state. Start stores the brightness mode and sets
    manual; stop restores it (ScreenBrightnessController); a running service drives brightness."""

    def __init__(self):
        self.ident = ID
        self.state = {
            (SETTING, BRIGHT): "200", (SETTING, MODE): "0", (SETTING, DOZE): "0",
            (GRANT, WRITE_SECURE_SETTINGS): "granted", (PREF, "smoothing"): "0.3",
        }
        for ns, key in recovery.footprint(["panic"]).settings:
            self.state.setdefault((SETTING, f"{ns}/{key}"), "0")
        self.running = True
        self.enabled = True  # serviceEnabled; the process may be dead while it stays true
        self.awake = True
        self.points = ['{"b": 120, "l": 5}']
        self.is_paused = False
        self.saved_mode = "1"
        self.writes: list[tuple[str, str, str]] = []
        self.late_write: str | None = None  # DC-047: one pipeline write after teardown
        self.teardown: dict[tuple[str, str], str] = {}  # display baseline reapplied on stop

    def identity(self):
        return self.ident

    def wake(self):
        self.awake = True

    def service_running(self):
        return self.running

    def service_enabled(self):
        return self.enabled

    def override_points(self):
        return list(self.points)

    def owner_mode(self):
        return self.saved_mode if self.saved_mode is not None else self.state[(SETTING, MODE)]

    def service_off(self):
        if not self.awake:
            raise AssertionError("the Dashboard switch needs the screen on")
        self.enabled = False
        if self.running:
            self.running = False
            self.is_paused = False
            if self.saved_mode is not None:
                self.state[(SETTING, MODE)], self.saved_mode = self.saved_mode, None
            self.state.update(self.teardown)
            if self.late_write is not None:
                self.state[(SETTING, BRIGHT)], self.late_write = self.late_write, None

    def service_on(self):
        self.enabled = True
        if not self.running:
            self.running = True
            self.saved_mode = self.state[(SETTING, MODE)]
            self.state[(SETTING, MODE)] = "0"
            self.state[(SETTING, BRIGHT)] = "200"

    def paused(self):
        return self.is_paused

    def pause(self):
        self.is_paused = True

    def read(self, kind, key):
        if kind == journal_mod.OWNER_MODE:
            return self.owner_mode()
        return self.state.get((kind, key))

    def write(self, kind, key, value):
        self.writes.append((kind, key, value))
        self.state[(kind, key)] = value

    def sleep(self, _seconds):
        pass


class CountingPort:
    """Delegates to a FakeDevice and kills the process at call number `kill_at`."""

    def __init__(self, dev: FakeDevice, kill_at: int | None = None):
        self.dev, self.kill_at, self.calls = dev, kill_at, 0

    def __getattr__(self, name):
        target = getattr(self.dev, name)

        def call(*args):
            self.calls += 1
            if self.calls == self.kill_at:
                raise Interrupted(name)
            return target(*args)
        return call


def originals(dev):
    return dict(dev.state), dev.running, dev.enabled, dev.is_paused, dev.saved_mode


def crashed_run(store, dev):
    """A run that changed brightness, mode, a pref and the grant, then died without teardown."""
    with Journal.for_run(store, ID) as j:
        fp = journal_effects(j, dev, ["service_toggle", "settings_write", "revoke"])
        j.expect(PREF, "smoothing", dev.read(PREF, "smoothing"), "0.9")
        dev.write(PREF, "smoothing", "0.9")
        dev.service_off()
        observe_effects(j, dev, fp)
        dev.service_on()
        observe_effects(j, dev, fp)
        j.expect(SETTING, BRIGHT, dev.read(SETTING, BRIGHT), "4095")
        dev.write(SETTING, BRIGHT, "4095")
        dev.write(GRANT, WRITE_SECURE_SETTINGS, "revoked")
        observe_effects(j, dev, fp)


def recover_once(store, port):
    with Journal.for_recovery(store) as j:
        return recover(j, port)


def assert_converged(store, dev, before):
    assert not (store / "journal.json").exists()
    assert originals(dev) == before


def test_clean_recovery_restores_everything(tmp_path):
    dev = FakeDevice()
    before = originals(dev)
    crashed_run(tmp_path, dev)
    report = recover_once(tmp_path, dev)
    assert report.clean
    assert_converged(tmp_path, dev, before)


def test_interruption_at_every_recovery_call_converges(tmp_path):
    probe_dev = FakeDevice()
    crashed_run(tmp_path / "probe", probe_dev)
    probe = CountingPort(probe_dev)
    recover_once(tmp_path / "probe", probe)
    assert probe.calls > 20
    for kill_at in range(1, probe.calls + 1):
        store = tmp_path / f"k{kill_at}"
        dev = FakeDevice()
        before = originals(dev)
        crashed_run(store, dev)
        with pytest.raises(Interrupted):
            recover_once(store, CountingPort(dev, kill_at))
        report = recover_once(store, dev)
        assert report.clean, (kill_at, report)
        assert_converged(store, dev, before)


def test_interruption_at_every_journal_write_converges(tmp_path, monkeypatch):
    real_replace = journal_mod.os.replace
    probe_dev = FakeDevice()
    crashed_run(tmp_path / "probe", probe_dev)
    count = {"n": 0}

    def counting(*a):
        count["n"] += 1
        return real_replace(*a)
    monkeypatch.setattr(journal_mod.os, "replace", counting)
    recover_once(tmp_path / "probe", probe_dev)
    writes = count["n"]
    assert writes >= 2  # drop after verify, runtime cleared
    for kill_at in range(1, writes + 1):
        store = tmp_path / f"w{kill_at}"
        dev = FakeDevice()
        before = originals(dev)
        monkeypatch.setattr(journal_mod.os, "replace", real_replace)
        crashed_run(store, dev)
        count["n"] = 0

        def torn(*a, _at=kill_at):
            count["n"] += 1
            if count["n"] == _at:
                raise Interrupted("journal write")
            return real_replace(*a)
        monkeypatch.setattr(journal_mod.os, "replace", torn)
        with pytest.raises(Interrupted):
            recover_once(store, dev)
        monkeypatch.setattr(journal_mod.os, "replace", real_replace)
        assert recover_once(store, dev).clean, kill_at
        assert_converged(store, dev, before)


@pytest.mark.parametrize("kill_after", ["expect", "write"])
def test_interruption_inside_the_run_converges(tmp_path, kill_after):
    dev = FakeDevice()
    before = originals(dev)
    with Journal.for_run(tmp_path, ID) as j:
        journal_effects(j, dev, ["settings_write"])
        j.expect(SETTING, BRIGHT, dev.read(SETTING, BRIGHT), "4095")
        if kill_after == "write":
            dev.write(SETTING, BRIGHT, "4095")
    dev.running = dev.enabled = False  # the run had stopped the service
    before = ({**before[0]}, False, False, False, before[4])
    assert recover_once(tmp_path, dev).clean
    assert_converged(tmp_path, dev, before)


def test_foreign_identity_is_refused_before_any_call_that_mutates(tmp_path):
    dev = FakeDevice()
    crashed_run(tmp_path, dev)
    dev.ident = Identity(ID.serial_digest, ID.user, ID.first_install_time, 26, "e" * 64)
    snapshot = originals(dev)
    dev.writes.clear()
    with pytest.raises(IdentityMismatch, match="cert_digest"):
        recover_once(tmp_path, dev)
    assert originals(dev) == snapshot and dev.writes == []
    assert (tmp_path / "journal.json").exists()


def test_owner_change_is_a_conflict_kept_and_never_written(tmp_path):
    dev = FakeDevice()
    crashed_run(tmp_path, dev)
    dev.state[(SETTING, DOZE)] = "1"  # the owner, meanwhile
    report = recover_once(tmp_path, dev)
    assert not report.clean and any(DOZE in c for c in report.conflicts)
    assert dev.state[(SETTING, DOZE)] == "1"
    assert (SETTING, DOZE, "0") not in dev.writes
    assert not dev.running  # a running service would write over the conflict
    with pytest.raises(journal_mod.PendingJournal):
        Journal.for_run(tmp_path, ID)
    # A rerun leaves it alone and touches nothing else.
    dev.writes.clear()
    assert not recover_once(tmp_path, dev).clean and dev.writes == [] and not dev.running
    # The owner resolves it; runtime comes back and the next run may start.
    with Journal.for_recovery(tmp_path) as j:
        j.resolve(f"{SETTING}:{DOZE}")
        assert recover(j, dev).clean
    assert dev.running
    Journal.for_run(tmp_path, ID).release()


def test_conflict_is_recorded_before_the_teardown_writes_over_it(tmp_path):
    dev = FakeDevice()
    crashed_run(tmp_path, dev)
    dev.state[(SETTING, DOZE)] = "1"  # the owner
    dev.teardown[(SETTING, DOZE)] = "5"  # then Tideo's teardown, as recovery stops it
    recover_once(tmp_path, dev)
    with Journal.for_recovery(tmp_path) as j:
        assert j.entries[f"{SETTING}:{DOZE}"].seen == "1"


def test_settings_only_journal_leaves_a_running_service_alone(tmp_path):
    dev = FakeDevice()
    dev.running = False
    with Journal.for_run(tmp_path, ID) as j:
        journal_effects(j, dev, ["settings_write"])
        j.expect(SETTING, MODE, dev.read(SETTING, MODE), "1")
        dev.write(SETTING, MODE, "1")
    dev.running = True  # started by someone else; not the run's to stop
    port = CountingPort(dev)
    assert recover_once(tmp_path, port).clean
    assert dev.running and dev.state[(SETTING, MODE)] == "0"


def test_pipeline_drift_on_driven_keys_is_the_runs(tmp_path):
    dev = FakeDevice()
    before = originals(dev)
    crashed_run(tmp_path, dev)
    dev.state[(SETTING, BRIGHT)] = "777"  # the run's pipeline moved it after the last observe
    assert recover_once(tmp_path, dev).clean
    assert_converged(tmp_path, dev, before)


def test_service_that_does_not_start_keeps_the_runtime_entry(tmp_path):
    dev = FakeDevice()
    crashed_run(tmp_path, dev)
    dev.service_on = lambda: None
    with pytest.raises(recovery.RecoveryError, match="did not start"):
        recover_once(tmp_path, dev)
    with Journal.for_recovery(tmp_path) as j:
        # The mode stays until the restart Tideo restores it with has been checked.
        assert j.runtime is not None and list(j.entries) == [f"{SETTING}:{MODE}"]


def test_conflict_clears_itself_when_the_owner_restores_the_original(tmp_path):
    dev = FakeDevice()
    crashed_run(tmp_path, dev)
    dev.state[(SETTING, DOZE)] = "1"
    recover_once(tmp_path, dev)
    dev.state[(SETTING, DOZE)] = "0"
    assert recover_once(tmp_path, dev).clean
    assert not (tmp_path / "journal.json").exists()


def test_late_teardown_write_is_absorbed(tmp_path):
    dev = FakeDevice()
    before = originals(dev)
    crashed_run(tmp_path, dev)
    dev.late_write = "333"  # DC-047: lands as recovery stops the service
    assert recover_once(tmp_path, dev).clean
    assert_converged(tmp_path, dev, before)


def test_unstable_key_keeps_the_journal(tmp_path, monkeypatch):
    dev = FakeDevice()
    crashed_run(tmp_path, dev)
    reads = {"n": 0}
    real_read = dev.read

    def drifting(kind, key):
        if (kind, key) == (SETTING, DOZE):
            reads["n"] += 1
            if reads["n"] > 4:
                return "1"  # moves after the restore
        return real_read(kind, key)
    dev.read = drifting
    with pytest.raises(Unstable):
        recover_once(tmp_path, dev)
    assert (tmp_path / "journal.json").exists()


def test_paused_runtime_is_re_entered_and_reported(tmp_path):
    dev = FakeDevice()
    dev.is_paused = True
    crashed_run(tmp_path, dev)
    report = recover_once(tmp_path, dev)
    assert dev.running and dev.is_paused
    assert any("PAUSE" in n for n in report.notes)


def test_empty_recovery_is_a_no_op(tmp_path):
    dev = FakeDevice()
    port = CountingPort(dev)
    assert recover_once(tmp_path, port).clean
    assert port.calls == 0


def test_prefs_are_restored_after_settings(tmp_path):
    # A Privileged Display restore reads the device back into its draft (S4 review).
    dev = FakeDevice()
    crashed_run(tmp_path, dev)
    dev.writes.clear()
    recover_once(tmp_path, dev)
    kinds = [kind for kind, _key, _value in dev.writes if kind in (SETTING, PREF)]
    assert PREF in kinds and SETTING in kinds
    assert kinds.index(PREF) > max(i for i, k in enumerate(kinds) if k == SETTING)


# ── findings from the S2–S4 blocking review ────────────────────────────────────────────────


def test_owners_saved_mode_survives_recovery(tmp_path):
    # Tideo running, the owner on adaptive brightness (saved "1"); manual written back before
    # the restart would make Tideo save manual as the owner's mode.
    dev = FakeDevice()
    crashed_run(tmp_path, dev)
    assert recover_once(tmp_path, dev).clean
    dev.service_off()  # the owner's next stop
    assert dev.state[(SETTING, MODE)] == "1"


def test_a_changed_owner_mode_is_reported(tmp_path):
    dev = FakeDevice()
    crashed_run(tmp_path, dev)
    real_on = dev.service_on

    def start_losing_it():
        real_on()
        dev.saved_mode = "0"
    dev.service_on = start_losing_it
    report = recover_once(tmp_path, dev)
    assert any("brightness mode" in c for c in report.conflicts)
    with pytest.raises(journal_mod.PendingJournal):  # kept, not just reported
        Journal.for_run(tmp_path, ID)
    assert not recover_once(tmp_path, dev).clean
    dev.saved_mode = "1"  # the owner puts it back
    assert recover_once(tmp_path, dev).clean
    assert not (tmp_path / "journal.json").exists()


def test_enabled_service_with_a_dead_process_is_switched_off(tmp_path):
    dev = FakeDevice()
    dev.running = dev.enabled = False
    with Journal.for_run(tmp_path, ID) as j:
        journal_effects(j, dev, ["service_toggle"])
        dev.service_on()
        dev.running = False  # the process died; serviceEnabled stays true
    assert recover_once(tmp_path, dev).clean
    assert not dev.enabled and not dev.running  # the next launch will not start it


def test_brightness_is_not_driven_when_the_service_never_ran(tmp_path):
    dev = FakeDevice()
    dev.running = dev.enabled = False
    with Journal.for_run(tmp_path, ID) as j:
        journal_effects(j, dev, ["prefs_ui"])
        assert not j.runtime.driven
    dev.state[(SETTING, BRIGHT)] = "999"  # the owner, after the run died
    report = recover_once(tmp_path, dev)
    assert any(BRIGHT in c for c in report.conflicts)
    assert dev.state[(SETTING, BRIGHT)] == "999"


def test_a_run_left_asleep_is_woken_before_the_ui_steps(tmp_path):
    dev = FakeDevice()
    before = originals(dev)
    crashed_run(tmp_path, dev)
    dev.awake = False  # killed right after KEYCODE_SLEEP
    assert recover_once(tmp_path, dev).clean
    assert_converged(tmp_path, dev, before)


def test_a_recorded_curve_point_keeps_the_journal_and_blocks_the_next_run(tmp_path):
    dev = FakeDevice()
    found = list(dev.points)
    with Journal.for_run(tmp_path, ID) as j:
        journal_effects(j, dev, ["override_record"])
        j.set_points(found)
        dev.points.append('{"b": 200, "l": 40}')  # an override, killed before its Discard
    report = recover_once(tmp_path, dev)
    assert any("curve points" in c for c in report.conflicts)
    with pytest.raises(journal_mod.PendingJournal):
        Journal.for_run(tmp_path, ID)
    dev.points = found  # the owner deletes it by hand
    assert recover_once(tmp_path, dev).clean
    assert not (tmp_path / "journal.json").exists()


def test_points_check_survives_an_interruption(tmp_path):
    dev = FakeDevice()
    with Journal.for_run(tmp_path, ID) as j:
        journal_effects(j, dev, ["override_record"])
        j.set_points(list(dev.points))
    with pytest.raises(Interrupted):
        recover_once(tmp_path, _kill_on(dev, "override_points"))
    assert recover_once(tmp_path, dev).clean
    assert not (tmp_path / "journal.json").exists()


def _kill_on(dev, name):
    class Port(CountingPort):
        def __getattr__(self, attr):
            if attr == name:
                def killed(*_a):
                    raise Interrupted(name)
                return killed
            return super().__getattr__(attr)
    return Port(dev)
