"""Tideo verbs over the command and UI boundaries, and the device port that recovery runs on.

Service on/off goes through the Dashboard's `service_switch`, never a broadcast: every
ControlReceiver verb is dropped unless external control is enabled, and enabling it announces
each transition to the owner's automation (DD-015). Paused is in-memory in Tideo, so it is
observed as the Resume action on Tideo's ongoing notification.
"""

from __future__ import annotations

import time
from pathlib import Path
from typing import Callable

from . import device, state
from .device import DEBUG_PKG, LAUNCH_FRESH_FLAGS, MAIN_ACTIVITY, WRITE_SECURE_SETTINGS
from .journal import GRANT, PREF, SETTING, Identity
from .recovery import RecoveryError
from .ui import Target, Ui, UiAllowlist, UiDenied

UI_POLLS = 10
UI_POLL_S = 0.5

# What the port itself may touch: open the Dashboard from the Menu, read and tap the switch.
PORT_TARGETS = (
    Target("menu_dashboard", DEBUG_PKG, "click", resource_id="menu_dashboard"),
    Target("service_switch", DEBUG_PKG, "click", resource_id="service_switch"),
    Target("service_switch_state", DEBUG_PKG, "read", resource_id="service_switch"),
)
PORT_UI = UiAllowlist(*PORT_TARGETS)

# Restoring a Tideo preference is a UI routine per key. A key without one cannot be restored,
# so a scenario must not journal it (S4c registers the ones its scenarios change).
PrefRestorer = Callable[["DevicePort", str], None]
PREF_RESTORERS: dict[str, PrefRestorer] = {}


def _retry(action: Callable[[], object], polls: int, wait: Callable[[float], None]):
    """Retry a UI step while the screen settles; the boundary's refusal is final on the last."""
    for _ in range(polls - 1):
        try:
            return action()
        except UiDenied:
            wait(UI_POLL_S)
    return action()


def _shown(ui: Ui) -> bool | None:
    try:
        return ui.checked("service_switch_state")
    except UiDenied:  # not composed yet
        return None


class DevicePort:
    """`recovery.Port` on a real device. The session must admit READ, HARNESS and MUTATE."""

    def __init__(self, s: device.Session, ui: Ui, store: Path,
                 restorers: dict[str, PrefRestorer] | None = None,
                 sleep: Callable[[float], None] = time.sleep):
        self.s, self.ui, self.store = s, ui, store
        self.restorers = PREF_RESTORERS if restorers is None else restorers
        self._sleep = sleep

    def identity(self) -> Identity:
        return state.identity(self.s, self.store)

    def sleep(self, seconds: float) -> None:
        self._sleep(seconds)

    # ── runtime ──

    def service_running(self) -> bool:
        return state.service_running(self.s)

    def paused(self) -> bool:
        return state.paused_in_dump(device.run(self.s, "dumpsys", "notification").output,
                                    DEBUG_PKG)

    def open_dashboard(self) -> None:
        """Fresh activity → Menu → Dashboard. With Tier NONE it opens on Onboarding instead, and
        the Menu target never appears: the retry ends in the boundary's refusal."""
        device.run(self.s, "am", "start", "-f", LAUNCH_FRESH_FLAGS, "-n",
                   f"{DEBUG_PKG}/{MAIN_ACTIVITY}")
        _retry(lambda: self.ui.click("menu_dashboard"), UI_POLLS, self._sleep)

    def _enabled(self) -> bool:
        return state.read_pref(self.s, "aab_settings/serviceEnabled") == "true"

    def _poll(self, done: Callable[[], bool]) -> bool:
        for _ in range(UI_POLLS):
            if done():
                return True
            self._sleep(UI_POLL_S)
        return done()

    def _set_service(self, on: bool) -> None:
        # The switch shows serviceEnabled, not the service, and the Dashboard renders it false
        # until settings load: tap only once the switch agrees with the stored value.
        self.open_dashboard()
        stored = self._enabled()
        if not self._poll(lambda: _shown(self.ui) == stored):
            raise RecoveryError(f"service_switch never showed the stored serviceEnabled={stored}")
        if stored != on:
            self.ui.click("service_switch")
            if not self._poll(lambda: self._enabled() == on):
                raise RecoveryError(f"serviceEnabled did not become {on} after the tap")

    def service_off(self) -> None:
        self._set_service(False)

    def service_on(self) -> None:
        self._set_service(True)

    def pause(self) -> None:
        if state.read_pref(self.s, "control_prefs/external_control_enabled") != "true":
            raise RecoveryError("the original paused state needs PAUSE, which Tideo drops while "
                                "external control is off; resume it by hand or leave it")
        device.run(self.s, "am", "broadcast", "-a", "com.tideo.autobrightness.control.PAUSE",
                   "-n", f"{DEBUG_PKG}/{device.CONTROL_RECEIVER}")

    # ── keyed state ──

    def read(self, kind: str, key: str) -> str | None:
        if kind == SETTING:
            namespace, _, name = key.partition("/")
            return state.read_setting(self.s, namespace, name)
        if kind == GRANT and key == WRITE_SECURE_SETTINGS:
            return state.grant_state(state.package_dump(self.s), DEBUG_PKG)
        if kind == PREF:
            return state.read_pref(self.s, key)
        raise RecoveryError(f"no reader for {kind}:{key}")

    def write(self, kind: str, key: str, value: str) -> None:
        if kind == SETTING:
            namespace, _, name = key.partition("/")
            r = device.run(self.s, "settings", "put", namespace, name, value)
        elif kind == GRANT and key == WRITE_SECURE_SETTINGS and value in ("granted", "revoked"):
            verb = "grant" if value == "granted" else "revoke"
            r = device.run(self.s, "pm", verb, DEBUG_PKG, WRITE_SECURE_SETTINGS)
        elif kind == PREF and key in self.restorers:
            self.restorers[key](self, value)
            return
        else:
            raise RecoveryError(f"no way to restore {kind}:{key} to {value!r}")
        if r.returncode != 0:
            raise RecoveryError(f"restoring {kind}:{key} exited {r.returncode}")
