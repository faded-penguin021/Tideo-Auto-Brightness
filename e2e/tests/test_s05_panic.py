"""§5 14a: the non-gesture PANIC entry points (the late PANIC stage)."""

import pytest

from .steps import automation, expect_panic, require_below_s, unpaused


@pytest.mark.scenario("s05_14a")
def test_s05_14a_every_panic_entry_point_resets(run):
    # The S.O.S. vibration (DB-037) is not observed here; the gesture halves stay manual.
    automation(run, True)
    for fire in (lambda: run.shade("shade_reset"), lambda: run.broadcast("PANIC")):
        if not run.running():
            run.service(True)
        unpaused(run)  # Reset is on the notification only while no point is discardable
        require_below_s(run)
        fire()
        expect_panic(run)
