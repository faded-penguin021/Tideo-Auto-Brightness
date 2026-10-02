"""§2 manual override detect / resume. Every trigger is injected, never waited for (DB-083), and
every quiet half has a control in the same test, so a build with detection disabled FAILs.
Each control pause is discarded from its notification, so no curve point is left behind."""

import pytest

from tideo_e2e.harness import far_domain

from .steps import (
    QUIET_S, automation, disposition, discard, expect_pause, last_disposition,
    ready_for_override, settled_domain, unpaused, woke_unlocked,
)

WAKE_WINDOW_S = 1.5  # DEVICE_TEST_SCRIPT §2 10a: the write must land inside it


def _quiet(run) -> bool:
    return run.stays(lambda: not run.paused(), QUIET_S)


@pytest.mark.scenario("s02_8")
def test_s02_8_brightness_change_pauses(run):
    ready_for_override(run)
    run.put_brightness(far_domain(settled_domain(run)))
    expect_pause(run, "a far brightness write")
    discard(run)


@pytest.mark.scenario("s02_9")
def test_s02_9_resume_from_the_notification(run):
    # PAUSE rather than an override: an override's Resume would keep its curve point.
    if not run.running():
        run.service(True)
    automation(run, True)
    unpaused(run)
    run.broadcast("PAUSE")
    run.wait_for(run.paused, 10, "pause")
    run.shade("shade_resume")
    run.wait_for(lambda: not run.paused(), 10, "resume")
    run.open("live_debug")
    assert run.metric("debug_override") == "No"


@pytest.mark.scenario("s02_10a")
def test_s02_10a_no_false_pause_on_wake(run):
    ready_for_override(run)
    d = settled_domain(run)
    run.keyevent("KEYCODE_SLEEP")
    run.sleep(3)
    run.keyevent("KEYCODE_WAKEUP", observe=False)
    woke = run.now()
    run.put_brightness(far_domain(d))
    assert run.last_put_at - woke < WAKE_WINDOW_S, \
        f"the write landed {run.last_put_at - woke:.2f} s after wake, outside the window"
    assert _quiet(run), "a write just after wake paused (#123)"
    woke_unlocked(run)
    # Control: the same write 5 s after wake is a user adjustment.
    unpaused(run)
    d = settled_domain(run)
    run.keyevent("KEYCODE_SLEEP")
    run.sleep(3)
    run.keyevent("KEYCODE_WAKEUP")
    run.sleep(5)
    run.put_brightness(far_domain(d))
    expect_pause(run, "the control; silence means detection is disabled")
    discard(run)


@pytest.mark.scenario("s02_10b")
def test_s02_10b_deadband_boundary(run):
    ready_for_override(run)
    for step, pauses in ((1, False), (2, True)):
        unpaused(run)
        d = settled_domain(run)
        n = d + step if d + step <= 255 else d - step
        before = last_disposition(run)
        since = run.now()
        run.put_brightness(n)
        if pauses:
            expect_pause(run, f"domain step {step}; silence means detection is disabled")
        else:
            assert _quiet(run), f"one domain step ({d} → {n}) paused (DC-005)"
        name, values = disposition(run, since, before)
        assert values == (n, n, d), f"observed/settled/expected {values}, injected {n} over {d}"
        if pauses:
            discard(run)
        else:
            assert name == "DISMISSED_DRIFT", f"quiet for the wrong reason: {name}"


@pytest.mark.scenario("s02_10c")
def test_s02_10c_mode_conflict_dismisses(run):
    ready_for_override(run)
    d = settled_domain(run)
    n = far_domain(d)  # far outside the deadband, or the quiet half proves nothing (DC-013)
    before = last_disposition(run)
    since = run.now()
    run.put("system", "screen_brightness_mode", 1)
    run.put_brightness(n)
    assert _quiet(run), "a write under automatic mode paused (#127)"
    run.wait_for(lambda: run.setting("system", "screen_brightness_mode") == "0", 10,
                 "mode reclaimed to manual")
    name, values = disposition(run, since, before)
    assert name == "DISMISSED_MODE" and values[0] == n, f"{name} {values}"
    # Control: with the mode already manual, the same kind of write pauses.
    unpaused(run)
    d = settled_domain(run)
    run.put_brightness(far_domain(d, n))
    expect_pause(run, "the control; silence means detection is disabled")
    discard(run)


@pytest.mark.scenario("s02_10e")
def test_s02_10e_no_pause_from_a_write_while_off(run):
    ready_for_override(run)
    d = settled_domain(run)
    run.keyevent("KEYCODE_SLEEP")
    run.sleep(1)
    raw = run.put_brightness(far_domain(d))
    assert run.brightness() == raw, "the write did not land while the screen was off"
    run.keyevent("KEYCODE_WAKEUP")
    assert _quiet(run), "a write made while the screen was off paused on wake (DC-042)"
    woke_unlocked(run)
    # Control (step 8): detection still works once awake.
    run.sleep(5)
    unpaused(run)
    run.put_brightness(far_domain(settled_domain(run)))
    expect_pause(run, "the control; silence means detection is disabled")
    discard(run)
