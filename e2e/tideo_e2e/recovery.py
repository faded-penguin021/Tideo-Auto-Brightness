"""The single serialized, idempotent recovery procedure.

Test teardown, signal handlers and `run.sh --recover` all call `recover`. Each stage persists
its progress through the journal before moving on, so an interruption anywhere leaves a journal
that a rerun converges from:

0. wake the screen, which a run may have left asleep (the UI steps below need it);
1. quiesce writers, when the run touched runtime state — a key already holding a value the run
   did not cause is marked a conflict first; then service off, serviceEnabled false and FGS gone,
   a settle window, and what Tideo's own teardown left on watched keys is attributed to the run
   (the accepted DC-047 late write lands here);
2. restore the grant, with the service still off;
3. restore settings, conflict-aware, then Tideo prefs: a Privileged Display restore reads the
   device back into its draft, so the device must hold its originals first;
4. verify stability — every restored key reads `original` twice, a settle window apart;
5. restore the original runtime state and check it took — unless conflicts remain, since a
   running service would write over them; the service then stays off until they are resolved;
6. check the curve points are those the run found; a difference stays a conflict.

Entries leave the journal only after stage 4 verifies them. Conflicts are never written.

The brightness mode under a service that was running is the exception to stage 3: its original
is Tideo's forced manual, and the owner's own mode is in Tideo's private saved_brightness_mode,
which its teardown writes back and its next start saves again. Writing manual back before the
restart would make Tideo save manual as the owner's mode, so the mode is left to Tideo, and
stage 5 checks that the owner's mode survived (`Runtime.owner_mode`); it cannot write it.

Accepted limits, each failing towards a conflict or a lost owner change, never a wrong
restore: Android has no conditional settings write, so a change landing between a read and the
restore that follows it is overwritten; a value the owner sets during an action is attributed
to the run by `observe`; Tideo's teardown in stage 1 writes over whatever it writes over when
the owner stops the service; a kill between an indirect write and its `observe` leaves a
conflict for the owner rather than a restore. The exception is `DRIVEN`: under a run during
which the pipeline could write brightness, an owner's hand change to brightness is
indistinguishable from the pipeline's own and is restored over.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Protocol

from .device import WRITE_SECURE_SETTINGS
from .effects import Footprint, Key, footprint
from .journal import GRANT, OWNER_MODE, PREF, SETTING, Entry, Identity, Journal

SETTLE_S = 3.0
POLLS = 20
POLL_S = 0.5


class RecoveryError(RuntimeError):
    pass


class Unstable(RecoveryError):
    """A restored key moved after restore; the journal keeps it for a rerun."""


class Port(Protocol):
    """What recovery needs from the device. S4's state/tideo modules implement it through the
    command and UI boundaries; the unit tests use a fake."""

    def identity(self) -> Identity: ...
    def wake(self) -> None: ...
    def service_running(self) -> bool: ...
    def service_enabled(self) -> bool: ...
    def owner_mode(self) -> str | None: ...
    def override_points(self) -> list[str]: ...
    def service_off(self) -> None: ...
    def service_on(self) -> None: ...
    def paused(self) -> bool: ...
    def pause(self) -> None: ...
    def read(self, kind: str, key: str) -> str | None: ...
    def write(self, kind: str, key: str, value: str) -> None: ...
    def sleep(self, seconds: float) -> None: ...


@dataclass
class Report:
    restored: list[str] = field(default_factory=list)
    conflicts: list[str] = field(default_factory=list)
    notes: list[str] = field(default_factory=list)

    @property
    def clean(self) -> bool:
        return not self.conflicts


def setting_key(key: Key) -> str:
    return "/".join(key)


def journal_effects(journal: Journal, port: Port, effects) -> Footprint:
    """Before a scenario acts: watch every key and state its declared effects may change.
    A pref the scenario changes is journaled by the scenario itself, with `expect`."""
    fp = footprint(effects)
    if fp.runtime:
        running = port.service_running()
        journal.set_runtime(running, port.paused(), port.owner_mode(),
                            driven=running or fp.starts_service)
    if fp.grant:
        journal.watch(GRANT, WRITE_SECURE_SETTINGS, port.read(GRANT, WRITE_SECURE_SETTINGS))
    for key in sorted(fp.settings):
        journal.watch(SETTING, setting_key(key), port.read(SETTING, setting_key(key)))
    return fp


def observe_effects(journal: Journal, port: Port, fp: Footprint) -> None:
    """After each action the scenario performed: attribute what it left on watched keys."""
    if fp.grant:
        journal.observe(GRANT, WRITE_SECURE_SETTINGS, port.read(GRANT, WRITE_SECURE_SETTINGS))
    for key in sorted(fp.settings):
        journal.observe(SETTING, setting_key(key), port.read(SETTING, setting_key(key)))


def recover(journal: Journal, port: Port) -> Report:
    report = Report()
    if journal.identity is not None:
        journal.check_identity(port.identity())  # before the first mutation
    elif not journal.empty:
        raise RecoveryError("a non-empty journal without an identity binding")
    if journal.pending() or journal.runtime is not None:
        port.wake()
        if journal.runtime is not None:
            _quiesce(journal, port)
        for kind in (GRANT, SETTING, PREF):
            _restore(journal, port, kind, report)
        _verify(journal, port)
    for e in journal.conflicts():
        now = port.read(e.kind, e.key)
        if now == e.original:  # the owner put it back
            journal.drop([e])
            report.notes.append(f"{e.id}: conflict cleared, the original is back")
        else:
            report.conflicts.append(f"{e.id}: original {e.original!r}, now {now!r}; left as is")
    if journal.runtime is not None:
        if report.conflicts:
            report.notes.append("runtime not restored while conflicts remain; resolve them, "
                                "then rerun --recover")
        else:
            _restore_runtime(journal, port, report)
    if journal.points is not None:
        if port.override_points() == journal.points:
            journal.clear_points()
        else:  # kept, so no run starts until the owner settles it
            report.conflicts.append(
                "the curve points differ from those the run found: delete the point it "
                "recorded in Curve & Brightness, then rerun --recover")
    if journal.empty:
        journal.close_clean()
    return report


# Keys a running pipeline rewrites on every lux change: while the run had the service up, no
# observe could keep pace, so any value they hold under a runtime journal is the run's.
DRIVEN = frozenset({"system/screen_brightness", "secure/reduce_bright_colors_level"})
MODE = "system/screen_brightness_mode"


def _attributable(journal: Journal, e: Entry, value: str | None) -> bool:
    if value is None:
        return False
    if journal.runtime is not None and journal.runtime.driven and e.kind == SETTING \
            and e.key in DRIVEN:
        return True
    return value == e.original or value in e.attributable or value == e.expected


def _left_to_tideo(journal: Journal, e: Entry) -> bool:
    """The brightness mode under a service that was running: Tideo's restart restores it."""
    rt = journal.runtime
    return rt is not None and rt.service_on and e.kind == SETTING and e.key == MODE


def _poll(port: Port, done) -> bool:
    for _ in range(POLLS):
        if done():
            return True
        port.sleep(POLL_S)
    return done()


def _quiesce(journal: Journal, port: Port) -> None:
    before = {}
    for e in journal.pending():
        if _left_to_tideo(journal, e):
            continue
        now = port.read(e.kind, e.key)
        if _attributable(journal, e, now):
            before[e.id] = (e, now)
        else:  # someone else's value: record it before the teardown can touch it
            journal.mark_conflict(e, now)
    # serviceEnabled too: with it true and the process dead, the next launch would start it.
    stopped = port.service_running() or port.service_enabled()
    if stopped:
        port.service_off()
        if not _poll(port, lambda: not port.service_running()):
            raise RecoveryError("the monitoring service is still in the foreground")
    port.sleep(SETTLE_S)  # also on a rerun: a late write from an earlier stop may still land
    if stopped:
        for e, was in before.values():
            now = port.read(e.kind, e.key)
            if now != was and now is not None:  # moved across the run's own service_off
                journal.observe(e.kind, e.key, now)


def _restore(journal: Journal, port: Port, kind: str, report: Report) -> None:
    for e in journal.pending(kind):
        if _left_to_tideo(journal, e):
            continue
        now = port.read(e.kind, e.key)
        if now == e.original:
            continue
        if _attributable(journal, e, now):
            port.write(e.kind, e.key, e.original)
            report.restored.append(e.id)
        else:
            journal.mark_conflict(e, now)


def _verify(journal: Journal, port: Port) -> None:
    entries = [e for e in journal.pending() if not _left_to_tideo(journal, e)]
    first = {e.id: port.read(e.kind, e.key) for e in entries}
    port.sleep(SETTLE_S)
    moved = []
    for e in entries:
        now = port.read(e.kind, e.key)
        if first[e.id] != e.original or now != e.original:
            moved.append(f"{e.id}: {first[e.id]!r} then {now!r}, original {e.original!r}")
    if moved:
        raise Unstable("restored keys did not hold; rerun --recover:\n  " + "\n  ".join(moved))
    journal.drop(entries)


def _restore_runtime(journal: Journal, port: Port, report: Report) -> None:
    rt = journal.runtime
    if rt.service_on:
        if not port.service_running():
            port.service_on()
        if not _poll(port, port.service_running):
            raise RecoveryError("the monitoring service did not start; rerun --recover")
        if rt.paused:
            # Pause is reconstructable; the override record behind the original is not.
            if not port.paused():
                port.pause()
            if not _poll(port, port.paused):
                raise RecoveryError("PAUSE did not take; rerun --recover")
            report.notes.append("paused again through PAUSE; the original override record "
                                "is not reconstructed, and the brightness mode stays as Tideo's "
                                "stop left it until Tideo next changes brightness")
        mode = port.owner_mode()
        if rt.owner_mode is not None and mode != rt.owner_mode:
            # Kept in the journal before the entries go: it blocks the next run until it is back.
            e = journal.watch(OWNER_MODE, "brightness", rt.owner_mode)
            journal.mark_conflict(e, mode)
            report.conflicts.append(
                f"{e.id}: the brightness mode Tideo restores on stop was {rt.owner_mode!r} and "
                f"is now {mode!r}; stop Tideo, set adaptive brightness back by hand, start it")
        journal.drop([e for e in journal.pending() if _left_to_tideo(journal, e)])
    elif rt.paused:
        report.notes.append("the original paused state belongs to a stopped service; not "
                            "reconstructed")
    journal.clear_runtime()
