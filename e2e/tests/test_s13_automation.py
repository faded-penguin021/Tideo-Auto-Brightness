"""§13 automation control: the intent surface, through adb broadcasts to the debug receiver."""

import pytest

from .steps import automation, expect_panic, require_below_s, unpaused


def _stopped(run) -> bool:
    return run.stays(lambda: not run.running(), 5)


@pytest.mark.scenario("s13_42")
def test_s13_42_commands_ignored_until_opted_in(run):
    automation(run, False)
    if run.running():
        run.service(False)
    run.broadcast("SERVICE_ON")
    assert _stopped(run), "SERVICE_ON acted with Automation control off (D-157)"
    automation(run, True)
    run.broadcast("SERVICE_ON")
    run.wait_for(run.running, 10, "service start once opted in")


@pytest.mark.scenario("s13_43")
def test_s13_43_core_verbs_route(run):
    require_below_s(run)
    automation(run, True)
    if not run.running():
        run.service(True)
    unpaused(run)
    run.broadcast("PAUSE")
    run.wait_for(run.paused, 10, "PAUSE")
    run.broadcast("RESUME")
    run.wait_for(lambda: not run.paused(), 10, "RESUME")
    # REAPPLY is partial: a fresh, attributable write cannot be forced without controlling lux.
    run.broadcast("REAPPLY")
    assert run.running() and not run.paused()
    run.broadcast("SERVICE_TOGGLE")
    run.wait_for(lambda: not run.running(), 10, "SERVICE_TOGGLE off")
    run.broadcast("SERVICE_TOGGLE")
    run.wait_for(run.running, 10, "SERVICE_TOGGLE on")
    run.broadcast("SERVICE_OFF")
    run.wait_for(lambda: not run.running(), 10, "SERVICE_OFF")
    run.broadcast("RESUME")
    assert _stopped(run), "an external RESUME overrode the master switch (D-160)"
    run.broadcast("SERVICE_ON")
    run.wait_for(run.running, 10, "SERVICE_ON")
    assert run.brightness() < run.stored_max, "brightness reached S on its own; rerun dimmer"
    run.broadcast("PANIC")
    expect_panic(run)


@pytest.mark.scenario("s13_45")
def test_s13_45_service_on_while_stopped(run):
    automation(run, True)
    if run.running():
        run.service(False)
    run.broadcast("SERVICE_ON")
    run.wait_for(run.running, 10,
                 "start; if the Dashboard shows degraded, exempt Tideo from battery optimisation")
