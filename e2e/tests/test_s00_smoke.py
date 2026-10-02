"""§0 tier readout and §1 step 4: the service switch. Both are in the S6 smoke set."""

import pytest

from tideo_e2e.state import MONITORING_CHANNEL

from .steps import BASIC, ELEVATED


@pytest.mark.smoke
@pytest.mark.scenario("s00_tier")
def test_s00_tier_badge_matches_the_grant(run):
    run.open("dashboard")
    assert run.read("tier_badge") == (ELEVATED if run.granted() else BASIC)


@pytest.mark.smoke
@pytest.mark.scenario("s01_4")
def test_s01_4_master_switch_starts_the_service(run):
    if run.running():
        run.service(False)
    run.wait_for(lambda: MONITORING_CHANNEL not in run.channels(), 10, "notification gone")
    run.service(True)
    run.wait_for(lambda: MONITORING_CHANNEL in run.channels(), 10, "the foreground notification")
