"""§11 Privileged Display toggles [ELEVATED]. Apply also stores each toggle in Tideo's settings,
so every switch a scenario flips is journaled as a preference and put back through the UI."""

import pytest

from .steps import require_elevated, unpaused

STAY_KEY = ("global", "stay_on_while_plugged_in")
INVERSION_KEY = ("secure", "accessibility_display_inversion_enabled")
STAY = "aab_settings/stayAwakeChargingEnabled"
INVERSION = "aab_settings/inversionEnabled"
AOSP_DALTONIZER = (None, "-1", "0", "11", "12", "13")


def _flip(run, switch: str, pref: str) -> bool:
    """Flip a Privileged Display switch in the draft; returns the new position."""
    now = not run.checked(f"{switch}_state")
    run.expect_pref(pref, "true" if now else "false")
    run.tap(switch)
    return now


def _apply(run) -> None:
    run.tap("apply_settings")


@pytest.mark.scenario("s11_32a")
def test_s11_32a_stay_awake_writes_the_whole_mask(run):
    require_elevated(run)
    if run.running():
        run.service(False)  # the direct-write path
    run.put(*STAY_KEY, 0)
    run.open("privileged_display")
    assert not run.checked("switch_stayAwake_state")
    assert _flip(run, "switch_stayAwake", STAY)
    _apply(run)
    run.wait_for(lambda: run.setting(*STAY_KEY) == "15", 5, "mask 15 (AC|USB|WIRELESS|DOCK)")
    assert not _flip(run, "switch_stayAwake", STAY)
    _apply(run)
    run.wait_for(lambda: run.setting(*STAY_KEY) == "0", 5, "mask 0")
    # A mask Tideo did not write: shown as on, with the notice (DB-077).
    run.put(*STAY_KEY, 7)
    run.open("privileged_display")
    assert run.checked("switch_stayAwake_state")
    assert run.shown("pd_stay_awake_custom_preserved")
    # An unrelated Apply must not broaden it; the draft carries the device's ON too.
    run.expect_pref(STAY, "true")
    inverted = _flip(run, "switch_inversion", INVERSION)
    _apply(run)
    run.wait_for(lambda: run.setting(*INVERSION_KEY) == ("1" if inverted else "0"), 5,
                 "the unrelated field applied")
    assert run.setting(*STAY_KEY) == "7", "an unrelated Apply broadened the charger set"
    # "Use Tideo's setting instead" writes at once, without an Apply (DB-078).
    run.tap("pd_stay_awake_custom_preserved_overwrite")
    run.wait_for(lambda: run.setting(*STAY_KEY) == "15", 5, "mask 15 from the notice")
    assert not run.shown("pd_stay_awake_custom_preserved")


@pytest.mark.scenario("s11_32c")
def test_s11_32c_unrecognised_colour_mode_is_read_only(run):
    mode = run.setting("secure", "accessibility_display_daltonizer")
    if mode in AOSP_DALTONIZER:
        pytest.skip(f"daltonizer {mode!r} is a value AOSP writes; nothing to observe")
    run.open("privileged_display")
    assert run.shown("pd_daltonizer_custom_preserved"), \
        f"daltonizer {mode!r} is not one AOSP writes, but no preservation notice is shown"


@pytest.mark.scenario("s11_34")
def test_s11_34_apply_with_the_service_off_hits_the_device(run):
    require_elevated(run)
    if run.running():
        run.service(False)
    run.open("privileged_display")
    inverted = _flip(run, "switch_inversion", INVERSION)
    want = "1" if inverted else "0"
    _apply(run)
    run.wait_for(lambda: run.setting(*INVERSION_KEY) == want, 5, "the direct write (applyNow)")
    run.service(True)
    assert run.stays(lambda: run.setting(*INVERSION_KEY) == want, 5), \
        "starting the service reverted the Apply; the seed must adopt, not write"


@pytest.mark.scenario("s11_36")
def test_s11_36_privileged_row_follows_the_grant(run):
    # A context swap writing nothing below ELEVATED needs a context rule, and stays manual.
    require_elevated(run)
    run.open("menu")
    assert run.shown("menu_privileged_display_shown")
    run.revoke()  # kills the process
    run.wait_for(lambda: not run.running(), 10, "the process to die with the grant")
    run.open("menu")
    run.wait_for(lambda: not run.shown("menu_privileged_display_shown"), 10,
                 "the Privileged row to disappear")
    run.grant()
    run.open("menu")
    run.wait_for(lambda: run.shown("menu_privileged_display_shown"), 10, "the row back")


@pytest.mark.scenario("s11_39")
def test_s11_39_panic_resets_the_privileged_keys(run):
    # Stay-awake and inversion stand in for the script's grayscale + Night Light; the
    # force-stop-mid-override repeat stays manual.
    require_elevated(run)
    if not run.running():
        run.service(True)
    run.open("privileged_display")
    for switch, pref in (("switch_inversion", INVERSION), ("switch_stayAwake", STAY)):
        run.expect_pref(pref, "true")
        if not run.checked(f"{switch}_state"):
            run.tap(switch)
    if run.shown("apply_settings_shown"):  # the switches may show the device, not the profile
        _apply(run)
    run.wait_for(lambda: run.pref(INVERSION) == run.pref(STAY) == "true", 5, "both stored on")
    run.wait_for(lambda: run.setting(*INVERSION_KEY) == "1"
                 and run.setting(*STAY_KEY) not in ("0", None), 10, "both toggles engaged")
    unpaused(run)
    run.shade("shade_reset")
    run.wait_for(lambda: not run.running(), 10, "service stop after PANIC")
    run.wait_for(lambda: run.setting(*INVERSION_KEY) == "0" and run.setting(*STAY_KEY) == "0",
                 10, "every display toggle at its default")
    run.service(True)  # panic is an escape hatch, not an opt-out
    run.wait_for(lambda: run.setting(*INVERSION_KEY) == "1"
                 and run.setting(*STAY_KEY) == "15", 10, "the profile's toggles re-asserted")


@pytest.mark.scenario("s11_39b")
def test_s11_39b_apply_does_not_undo_itself(run):
    require_elevated(run)
    for on in (True, False):
        if run.running() != on:
            run.service(on)
        run.open("privileged_display")
        inverted = _flip(run, "switch_inversion", INVERSION)
        _apply(run)
        assert run.stays(lambda: run.checked("switch_inversion_state") == inverted
                         and not run.shown("apply_settings_shown"), 4), \
            f"the toggle rolled back with the service {'on' if on else 'off'} (DB-047/DB-048)"
