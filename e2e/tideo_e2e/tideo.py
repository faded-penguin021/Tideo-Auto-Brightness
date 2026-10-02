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
from .ui import SHADE_PKG, Target, Ui, UiAllowlist, UiDenied

UI_POLLS = 10
UI_POLL_S = 0.5

SHADE_LABEL = "Tideo AB (Debug)"


def _t(name: str, action: str, rid: str | None = None, **kw) -> Target:
    return Target(name, DEBUG_PKG, action, resource_id=rid or name, **kw)


def _shade(name: str, text: str) -> Target:
    return Target(name, SHADE_PKG, "click", text=text, anchor=SHADE_LABEL)


DALTONIZER_MODES = ("OFF", "GRAYSCALE", "PROTANOMALY", "DEUTERANOMALY", "TRITANOMALY")
# The Privileged Display preferences its screen reads back from the device and Apply persists
# with the whole draft (DeviceDisplaySnapshot.withDeviceSnapshot), each with the switch that sets
# it; the Kelvin has no automatable control and the colour mode is a row of chips.
PD_SWITCHES = {
    "aab_settings/nightLightEnabled": "switch_nightLight",
    "aab_settings/inversionEnabled": "switch_inversion",
    "aab_settings/alwaysOnDisplayEnabled": "switch_alwaysOn",
    "aab_settings/stayAwakeChargingEnabled": "switch_stayAwake",
    "aab_settings/hdrForceSdrEnabled": "switch_hdrForceSdr",
}
PD_PREFS = (*PD_SWITCHES, "aab_settings/nightLightTemperature", "aab_settings/daltonizerMode")
AUTOMATION = "control_prefs/external_control_enabled"
STRENGTH = "aab_settings/dimmingStrength"

# Every Tideo control the suite may act on, with the effect kinds a tap on it can have; a scenario
# may tap a target only when its row declares one of them (harness.Run.tap). Reads change nothing.
# `apply_settings` is the shared draft Apply bar: Super Dimming's writes prefs, Privileged
# Display's writes display keys too, and prefs_ui's footprint covers both. A shade action also
# needs notification_action: Reset is PANIC, and Discard deletes a curve point.
TARGETS: dict[str, tuple[Target, frozenset[str]]] = {
    t.name: (t, frozenset(effects)) for t, effects in (
        *((_t(f"menu_{route}", "click"), ("ui_nav",)) for route in (
            "dashboard", "live_debug", "privileged_display", "super_dimming", "tools")),
        (_t("service_switch", "click"), ("service_toggle",)),
        (_t("automation_toggle", "click"), ("prefs_ui",)),
        (_t("field_dimmingStrength", "edit"), ("prefs_ui",)),
        (_t("apply_settings", "click"), ("prefs_ui", "privileged_apply")),
        *((_t(switch, "click"), ("privileged_apply",)) for switch in PD_SWITCHES.values()),
        *((_t(f"daltonizer_{m.lower()}", "click"), ("privileged_apply",))
          for m in DALTONIZER_MODES),
        (_t("pd_stay_awake_custom_preserved_overwrite", "click"), ("privileged_apply",)),
        (_shade("shade_resume", "Resume"), ("notification_action",)),
        (_shade("shade_discard", "Discard"), ("override_record",)),
        (_shade("shade_reset", "Reset"), ("panic",)),
        *((_t(f"{switch}_state", "read", switch), ()) for switch in PD_SWITCHES.values()),
        *((_t(name, "read", rid), ()) for name, rid in (
            ("service_switch_state", "service_switch"),
            ("automation_toggle_state", "automation_toggle"),
            ("apply_settings_shown", "apply_settings"),
            ("menu_privileged_display_shown", "menu_privileged_display"),
            ("menu_dashboard_shown", "menu_dashboard"),
            ("field_dimmingStrength_text", "field_dimmingStrength"),
            ("tier_badge", None), ("override_card", None), ("aab_flash", None),
            ("pd_stay_awake_custom_preserved", None), ("pd_daltonizer_custom_preserved", None),
            ("debug_override", None), ("debug_service", None), ("debug_current_bright", None),
            ("debug_override_disposition", None), ("debug_override_values", None),
            ("debug_override_age", None),
        )),
    )
}
# The preferences a control may change: each must be journaled before it is touched.
TARGET_PREFS: dict[str, frozenset[str]] = {
    "automation_toggle": frozenset({AUTOMATION}),
    "field_dimmingStrength": frozenset({STRENGTH}),
    "apply_settings": frozenset({STRENGTH, *PD_PREFS}),
    "pd_stay_awake_custom_preserved_overwrite": frozenset(PD_PREFS),
    **{switch: frozenset(PD_PREFS) for switch in PD_SWITCHES.values()},
    **{f"daltonizer_{m.lower()}": frozenset(PD_PREFS) for m in DALTONIZER_MODES},
}
SUITE_UI = UiAllowlist(*(t for t, _ in TARGETS.values()))
# What recovery may touch: the service switch, and every control a pref restorer uses.
PORT_UI = UiAllowlist(*(TARGETS[n][0] for n in (
    "menu_dashboard", "menu_dashboard_shown", "menu_privileged_display", "menu_super_dimming",
    "menu_tools", "service_switch", "service_switch_state", "automation_toggle",
    "automation_toggle_state", "field_dimmingStrength", "apply_settings", "apply_settings_shown",
    *PD_SWITCHES.values(), *(f"{s}_state" for s in PD_SWITCHES.values()),
    *(f"daltonizer_{m.lower()}" for m in DALTONIZER_MODES))))

# Restoring a Tideo preference is a UI routine per key. A key without one cannot be restored,
# so a scenario must not journal it (harness.Run.expect_pref refuses). Recovery restores prefs
# after device settings, so the Privileged Display screen then reads the original device state
# back into its draft, and one Apply stores it whole.
PrefRestorer = Callable[["DevicePort", str], None]


def _set_switch(port: DevicePort, switch: str, value: str) -> None:
    if _retry(lambda: port.ui.checked(f"{switch}_state"), UI_POLLS, port.sleep) != (
            value == "true"):
        port.ui.click(switch)


def _apply_if_shown(port: DevicePort) -> None:
    if port.ui.exists("apply_settings_shown"):
        port.ui.click("apply_settings")


def _restore_automation(port: DevicePort, value: str) -> None:
    port.open_screen("tools")
    _set_switch(port, "automation_toggle", value)


def _restore_strength(port: DevicePort, value: str) -> None:
    port.open_screen("super_dimming")
    _retry(lambda: port.ui.set_text("field_dimmingStrength", value), UI_POLLS, port.sleep)
    _apply_if_shown(port)


def _restore_privileged(key: str) -> PrefRestorer:
    def restore(port: DevicePort, value: str) -> None:
        port.open_screen("privileged_display")
        if key in PD_SWITCHES:
            _set_switch(port, PD_SWITCHES[key], value)
        elif key == "aab_settings/daltonizerMode":
            mode = value.strip('"')
            if mode not in DALTONIZER_MODES:
                raise RecoveryError(f"no chip for colour mode {value}")
            _retry(lambda: port.ui.click(f"daltonizer_{mode.lower()}"), UI_POLLS, port.sleep)
        _apply_if_shown(port)  # the Kelvin: the read-back draft carries the device's own
    return restore


PREF_RESTORERS: dict[str, PrefRestorer] = {
    AUTOMATION: _restore_automation,
    STRENGTH: _restore_strength,
    **{key: _restore_privileged(key) for key in PD_PREFS},
}


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

    def open_screen(self, route: str) -> None:
        """Fresh activity → Menu → `route` ("menu" stays there). With Tier NONE it opens on
        Onboarding instead, and the Menu never appears: the retry ends in a refusal."""
        device.run(self.s, "am", "start", "-f", LAUNCH_FRESH_FLAGS, "-n",
                   f"{DEBUG_PKG}/{MAIN_ACTIVITY}")
        if route == "menu":
            _retry(lambda: self.ui.read("menu_dashboard_shown"), UI_POLLS, self._sleep)
        else:
            _retry(lambda: self.ui.click(f"menu_{route}"), UI_POLLS, self._sleep)

    def open_dashboard(self) -> None:
        self.open_screen("dashboard")

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
