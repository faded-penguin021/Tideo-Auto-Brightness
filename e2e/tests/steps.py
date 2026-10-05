"""Steps several scenarios share. Each takes the scenario's `Run`, so its effects are gated by
that scenario's row."""

from __future__ import annotations

import pytest

from tideo_e2e import state
from tideo_e2e.harness import Run
from tideo_e2e.state import OVERRIDE_CHANNEL
from tideo_e2e.ui import UiDenied

DETECT = "aab_settings/detectOverrides"

EXTERNAL_CONTROL = "control_prefs/external_control_enabled"
# The Dashboard tier badge (strings.xml dashboard_tier_*).
BASIC = "Basic access (Modify system settings)"
ELEVATED = "Elevated access (super dimming ready)"
QUIET_S = 6  # how long "nothing happens" is watched for: several pipeline cycles


def override_paused(run: Run) -> bool:
    """Paused by an override: Resume on the ongoing notification AND the manual_override
    channel. The Dashboard's override_card is checked once the pause is seen."""
    return run.paused() and OVERRIDE_CHANNEL in run.channels()


def unpaused(run: Run) -> None:
    """DC-012: a paused pipeline drops every injected event, so each half starts unpaused."""
    assert not run.paused(), "paused before the injection (DC-012)"
    assert OVERRIDE_CHANNEL not in run.channels(), "an override notification is already up"


def ready_for_override(run: Run) -> None:
    """Service running, Override Detection on, one cycle completed, not paused. The debug
    build's Override Detection is turned on, journaled and restored (owner, 2026-10-05)."""
    if run.paused():
        pytest.skip("already paused; resume it by hand first (DC-012)")
    if run.pref(DETECT) != "true":
        run.expect_pref(DETECT, "true")
        run.open("reactivity")
        if not run.checked("switch_detectOverrides_state"):
            run.tap("switch_detectOverrides")
        run.tap("apply_settings")
        run.wait_for(lambda: run.pref(DETECT) == "true", 5, "Override Detection stored on")
    if run.paused():
        pytest.skip("already paused; resume it by hand first (DC-012)")
    if not run.running():
        run.service(True)
    run.open("live_debug")
    run.wait_for(lambda: run.metric("debug_current_bright").isdigit(), 15, "first cycle")
    assert run.setting("system", "screen_brightness_mode") == "0", "Tideo holds manual mode"
    unpaused(run)


def settled_domain(run: Run) -> int:
    """The stored brightness on the domain scale, once Live Debug's last write agrees with it:
    a verified starting value, not one the pipeline is about to move."""
    seen = {}

    def agree() -> bool:
        seen["d"] = run.stored_domain()
        return run.metric("debug_current_bright") == str(seen["d"])
    run.open("live_debug")
    run.wait_for(agree, 15, "Live Debug's brightness to match the stored value")
    return seen["d"]


def expect_pause(run: Run, what: str) -> None:
    run.wait_for(lambda: override_paused(run), 10, f"override pause ({what})")
    run.open("dashboard")
    assert run.shown("override_card"), "paused, but the Dashboard shows no Resume card"


def discard(run: Run) -> None:
    """Forget the curve point the pause recorded, which also resumes (DD-011)."""
    run.shade("shade_discard")
    run.wait_for(lambda: not run.paused(), 10, "resume after Discard")


def last_disposition(run: Run) -> tuple[str, str] | None:
    """The Brightness Writes record as shown now, to tell a fresh one from it afterwards."""
    run.open("live_debug")
    if not run.shown("debug_override_age"):
        return None
    return run.metric("debug_override_disposition"), run.metric("debug_override_values")


def disposition(run: Run, since: float, before: tuple[str, str] | None
                ) -> tuple[str, tuple[int, int, int | None]]:
    """Live Debug → Brightness Writes, refused unless it differs from the record `before` the
    injection and "Override seen" is no older than the injection at `since` (DC-015): a stale
    record means the monitor never delivered the event."""
    now = last_disposition(run)
    if now is None:
        raise AssertionError("no override disposition recorded at all")
    assert now != before, f"the disposition did not change from {before}"
    age = state.age_seconds(run.metric("debug_override_age"))
    elapsed = run.now() - since
    assert age is not None and age <= elapsed, \
        f"stale disposition: seen {age} s ago, injected {elapsed:.0f} s ago"
    return state.disposition(now[0]), state.override_values(now[1])


def woke_unlocked(run: Run) -> None:
    """After KEYCODE_WAKEUP the UI steps and recovery need Tideo reachable, not a keyguard."""
    try:
        run.open("dashboard")
    except UiDenied as e:
        pytest.fail(f"the phone came back locked ({e}); wake scenarios need it to wake "
                    f"unlocked. Unlock it, then run e2e/run.sh --recover")


def automation(run: Run, on: bool) -> None:
    """Tools → Automation control, journaled so recovery puts it back."""
    want = "true" if on else "false"
    run.open("tools")
    if run.checked("automation_toggle_state") != on:
        run.expect_pref(EXTERNAL_CONTROL, want)
        run.tap("automation_toggle")
    run.wait_for(lambda: run.pref(EXTERNAL_CONTROL) == want, 5, f"automation control {want}")


def require_elevated(run: Run) -> None:
    if not run.granted():
        pytest.skip("needs WRITE_SECURE_SETTINGS on the debug package")


def require_below_s(run: Run) -> None:
    """PANIC is judged by a transition to S, so it needs a distinct starting value."""
    if run.brightness() >= run.stored_max:
        pytest.skip("brightness is already at S; PANIC's transition cannot be observed here")


def expect_panic(run: Run) -> None:
    s_max = run.stored_max
    run.wait_for(lambda: run.brightness() == s_max, 10, "brightness at S after PANIC")
    run.wait_for(lambda: not run.running(), 10, "service stop after PANIC")
    assert run.pref("aab_settings/serviceEnabled") == "false", "PANIC persists the stop"
