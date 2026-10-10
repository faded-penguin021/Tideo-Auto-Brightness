"""A scenario's run on the device: every action is admitted only for an effect its row declares.

A scenario test receives a `Run` from tests/conftest.py, which has already journaled the row's
declared effects and runs the one recovery procedure when the test ends, pass or fail. `Run`
refuses, before anything is sent, an action whose effect kind the row in scenarios.toml does not
declare; a setting the footprint does not journal; and a preference no UI routine can restore.
"""

from __future__ import annotations

import math
import re
import time
import tomllib
from dataclasses import dataclass
from typing import Callable

import pytest

from . import device, state
from .device import CONTROL_RECEIVER, DEBUG_PKG, WRITE_SECURE_SETTINGS, BoundaryViolation
from .effects import footprint
from .journal import PREF, SETTING, Journal
from .recovery import observe_effects
from .scenarios import E2E_ROOT, Scenario
from .tideo import (
    DETECT, PD_PREFS, PREF_RESTORERS, STRENGTH, TARGET_PREFS, TARGETS, DevicePort, UI_POLL_S,
    UI_POLLS, _retry,
)
from .ui import Ui

DEVICES = E2E_ROOT / "devices"
# What the draft Apply bar stores, per screen.
APPLY_PREFS = {"super_dimming": frozenset({STRENGTH}), "privileged_display": frozenset(PD_PREFS),
               "reactivity": frozenset({DETECT})}
POLL_S = 0.5


class UndeclaredEffect(BoundaryViolation):
    """The scenario tried an action its scenarios.toml row does not declare."""


# ── the brightness scales (DEVICE_TEST_SCRIPT §2 10b; DC-010, DC-025) ──────────────────────


@dataclass(frozen=True)
class Profile:
    """The owner's hand measurements for one phone model; never calibrated through PANIC."""

    stored_max: int  # S: what `settings get system screen_brightness` reads at the slider's top


def profile_for(model: str) -> Profile | None:
    if not re.fullmatch(r"[A-Za-z0-9_-]{1,40}", model):
        return None
    path = DEVICES / f"{model}.toml"
    if not path.exists():
        return None
    doc = tomllib.loads(path.read_text(encoding="utf-8"))
    return Profile(stored_max=int(doc["stored_max"]))


def _half_up(x: float) -> int:
    return math.floor(x + 0.5)


def to_domain(raw: int, s_max: int) -> int:
    """The domain value (0–255) the app sees for stored value `raw`: round(V × 255 / S)."""
    return _half_up(raw * 255 / s_max)


def to_raw(n: int, s_max: int) -> int:
    """The stored value that lands on domain `n`: round(n × S / 255)."""
    return _half_up(n * s_max / 255)


def far_domain(*avoid: int, distance: int = 60) -> int:
    """A domain value at least `distance` from every value in `avoid`, away from both ends."""
    for n in (200, 60, 230, 30, 130):
        if all(abs(n - a) >= distance for a in avoid):
            return n
    raise ValueError(f"no domain value {distance} away from {avoid}")


# ── the run ─────────────────────────────────────────────────────────────────────────────────


class Run:
    def __init__(self, row: Scenario, s: device.Session, ui: Ui, journal: Journal,
                 port: DevicePort, profile: Profile | None,
                 sleep: Callable[[float], None] = time.sleep,
                 clock: Callable[[], float] = time.monotonic):
        self.row, self.s, self.ui, self.journal, self.port = row, s, ui, journal, port
        self.profile, self._sleep, self._clock = profile, sleep, clock
        self.fp = footprint(row.effects)
        self._screen: str | None = None
        self.last_put_at: float | None = None
        self.points_before: list[str] | None = None  # the conftest's snapshot (override_record)

    # ── gating ──

    def _need(self, kinds, what: str) -> None:
        if not set(kinds) & set(self.row.effects):
            raise UndeclaredEffect(f"{self.row.id}: {what} needs one of {sorted(kinds)} in its "
                                   f"scenarios.toml effects")

    def _observe(self) -> None:
        observe_effects(self.journal, self.port, self.fp)
        if keys := self._journaled_prefs():
            for key, value in state.read_prefs(self.s, keys).items():
                self.journal.observe(PREF, key, value)

    def _journaled_prefs(self) -> list[str]:
        return [e.key for e in self.journal.pending(PREF)]

    def _need_prefs(self, name: str) -> None:
        """A control that changes preferences only once each is journaled (and restorable)."""
        if name == "apply_settings":
            wanted = APPLY_PREFS.get(self._screen)
            if wanted is None:
                raise UndeclaredEffect(f"{self.row.id}: Apply on {self._screen!r} is not modelled")
        else:
            wanted = TARGET_PREFS.get(name, frozenset())
        if missing := sorted(set(wanted) - set(self._journaled_prefs())):
            raise UndeclaredEffect(f"{self.row.id}: {name} may change unjournaled {missing}")

    def journal_privileged(self) -> None:
        """Before anything else: journal every preference Privileged Display's Apply stores, and
        require its screen to open clean. A screen that opens dirty shows device values its
        profile does not hold, which an Apply would store and a restore could not converge."""
        self._need({"privileged_apply"}, "journaling Privileged Display")
        for key, value in state.read_prefs(self.s, PD_PREFS).items():
            self.journal.watch(PREF, key, value)
        self.open("privileged_display")
        if self.dirty():
            pytest.skip("Privileged Display opens with the device differing from the profile; "
                        "Apply or Discard there by hand first")

    @property
    def stored_max(self) -> int:
        if self.profile is None:
            raise UndeclaredEffect("no device profile: add e2e/devices/<model>.toml with the "
                                   "owner-measured stored_max")
        return self.profile.stored_max

    # ── waiting ──

    def wait_for(self, cond: Callable[[], bool], timeout: float, what: str) -> None:
        end = self._clock() + timeout
        while not cond():
            if self._clock() >= end:
                raise AssertionError(f"{self.row.id}: no {what} within {timeout:g} s")
            self._sleep(POLL_S)

    def stays(self, cond: Callable[[], bool], seconds: float) -> bool:
        """Whether `cond` holds at every poll for `seconds`: the 'nothing happens' oracle."""
        end = self._clock() + seconds
        while self._clock() < end:
            if not cond():
                return False
            self._sleep(POLL_S)
        return cond()

    def sleep(self, seconds: float) -> None:
        self._sleep(seconds)

    def now(self) -> float:
        return self._clock()

    # ── UI ──

    def open(self, route: str) -> None:
        """A fresh activity on Menu → `route`. Launching starts the service when serviceEnabled
        is stored and it is not running, so that needs a declared service effect."""
        self._need({"ui_nav"}, f"opening {route}")
        if not self.fp.starts_service and not self.running() \
                and self.pref("aab_settings/serviceEnabled") == "true":
            raise UndeclaredEffect(f"{self.row.id}: launching would start the service")
        self._screen = route
        self.port.open_screen(route, self.ui)
        self._observe()

    def tap(self, name: str, observe: bool = True) -> None:
        """`observe=False` when the next read must follow at once (19a's 2.5 s flash); the
        next action's own observation then attributes what the tap wrote."""
        _target, effects = TARGETS[name]
        if effects:
            self._need(effects, f"tapping {name}")
        self._need_prefs(name)
        _retry(lambda: self.ui.click(name), UI_POLLS, self._sleep)
        if observe:
            self._observe()

    def set_text(self, name: str, text: str) -> None:
        self._need(TARGETS[name][1], f"editing {name}")
        self._need_prefs(name)
        _retry(lambda: self.ui.set_text(name, text), UI_POLLS, self._sleep)
        self._observe()

    def read(self, name: str) -> str:
        return _retry(lambda: self.ui.read(name), UI_POLLS, self._sleep)

    def checked(self, name: str) -> bool:
        return _retry(lambda: self.ui.checked(name), UI_POLLS, self._sleep)

    def shown(self, name: str) -> bool:
        return self.ui.exists(name)

    def read_if_shown(self, name: str) -> str | None:
        return self.ui.read_if_shown(name)

    def dirty(self) -> bool:
        """Whether Apply is enabled: the bar is always shown, and Apply is enabled when the
        draft is dirty and free of validation errors (SettingsControls.DraftApplyBar)."""
        return _retry(lambda: self.ui.enabled("apply_settings_shown"), UI_POLLS, self._sleep)

    def metric(self, tag: str) -> str:
        """A Live Debug line's value; the screen must be open."""
        return state.metric_value(self.read(tag))

    def shade(self, name: str) -> None:
        """One named action on Tideo's own notification, then the shade closes again. Discard
        only while the run has added exactly one curve point to those it found (DD-011)."""
        self._need({"notification_action"}, f"the notification's {name}")
        self._need(TARGETS[name][1], f"the notification's {name}")
        if name == "shade_discard":
            before, now = self.points_before or [], state.override_points(self.s)
            extra = list(now)
            for p in before:
                if p in extra:
                    extra.remove(p)
            if len(now) != len(before) + 1 or len(extra) != 1:
                raise UndeclaredEffect(f"{self.row.id}: Discard only removes the one point this "
                                       f"run recorded; found {len(before)} → {len(now)}")
        self.ui.open_shade()
        try:
            # Only the wait for shade focus is retried: the expand tap is sent once (DD-033).
            _retry(self.ui.require_shade, UI_POLLS, self._sleep)
            if self.ui.expand_own_row(lambda title: state.title_posters(self.s, title)):
                self._sleep(UI_POLL_S)
            _retry(lambda: self.ui.click(name), UI_POLLS, self._sleep)
        finally:
            self._sleep(UI_POLL_S)
            self.ui.close_shade()
        self._observe()

    # ── device verbs ──

    def setting(self, namespace: str, key: str) -> str | None:
        return self.port.read(SETTING, f"{namespace}/{key}")

    def brightness(self) -> int:
        value = self.setting("system", "screen_brightness")
        if value is None or not value.isdigit():
            raise AssertionError(f"screen_brightness reads {value!r}")
        return int(value)

    def stored_domain(self) -> int:
        return to_domain(self.brightness(), self.stored_max)

    def put_brightness(self, domain: int) -> int:
        """Inject the stored value that lands on `domain` (convert, never step: DC-010)."""
        if not 0 <= domain <= 255:
            raise ValueError(f"domain {domain} is outside 0–255")
        raw = to_raw(domain, self.stored_max)
        self.put("system", "screen_brightness", raw)
        return raw

    def put(self, namespace: str, key: str, value: int | str) -> None:
        self._need({"settings_write"}, f"settings put {namespace} {key}")
        if (namespace, key) not in self.fp.settings:
            raise UndeclaredEffect(f"{self.row.id}: {namespace}/{key} is not in its footprint")
        name = f"{namespace}/{key}"
        self.journal.expect(SETTING, name, self.setting(namespace, key), str(value))
        r = device.run(self.s, "settings", "put", namespace, key, str(value))
        self.last_put_at = self._clock()  # before the observation, for timing-bound steps
        if r.returncode != 0:
            raise AssertionError(f"settings put {name} exited {r.returncode}")
        self._observe()

    def keyevent(self, key: str, observe: bool = True) -> None:
        """`observe=False` when the next action must follow at once (10a's 1.5 s window); that
        action's own observation then attributes what the wake wrote."""
        self._need({"screen_wake"}, f"input keyevent {key}")
        device.run(self.s, "input", "keyevent", key)
        if observe:
            self._observe()

    def broadcast(self, verb: str) -> None:
        self._need({"broadcast"}, f"broadcast {verb}")
        if verb == "PANIC":
            self._need({"panic"}, "broadcast PANIC")
        self._sleep(device.BROADCAST_SPACING_S)  # the boundary refuses a closer one
        r = device.run(self.s, "am", "broadcast", "-a", f"com.tideo.autobrightness.control.{verb}",
                       "-n", f"{DEBUG_PKG}/{CONTROL_RECEIVER}")
        if r.returncode != 0:
            raise AssertionError(f"broadcast {verb} exited {r.returncode}")
        self._observe()

    def grant(self) -> None:
        self._need({"grant"}, "pm grant")
        device.run(self.s, "pm", "grant", DEBUG_PKG, WRITE_SECURE_SETTINGS)
        self._observe()

    def revoke(self) -> None:
        self._need({"revoke"}, "pm revoke")
        device.run(self.s, "pm", "revoke", DEBUG_PKG, WRITE_SECURE_SETTINGS)
        self._observe()

    def service(self, on: bool) -> None:
        """Through the Dashboard's switch (DD-019); waits for the FGS to follow."""
        self._need({"service_toggle"}, "the service switch")
        (self.port.service_on if on else self.port.service_off)()
        self.wait_for(lambda: self.running() == on, 10, f"service {'start' if on else 'stop'}")
        self._observe()

    # ── preferences ──

    def pref(self, key: str) -> str:
        return state.read_pref(self.s, key)

    def expect_pref(self, key: str, value: str) -> None:
        """Journal a preference the next UI action changes. Only keys with a restorer."""
        self._need({"prefs_ui", "privileged_apply"}, f"changing {key}")
        if key not in PREF_RESTORERS:
            raise UndeclaredEffect(f"{key} has no UI restorer, so the run may not change it")
        self.journal.expect(PREF, key, self.pref(key), value)

    # ── observed state ──

    def running(self) -> bool:
        return self.port.service_running()
    def paused(self) -> bool:
        return self.port.paused()

    def owner_mode(self) -> str | None:
        """The brightness mode Tideo gives back on stop (recovery.Runtime.owner_mode)."""
        return self.port.owner_mode()

    def channels(self) -> frozenset[str]:
        return state.channels(self.s)

    def granted(self) -> bool:
        return state.grant_state(state.package_dump(self.s), DEBUG_PKG) == "granted"

    def locked(self) -> bool:
        return state.locked(self.s)
