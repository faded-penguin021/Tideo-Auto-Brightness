"""§6 super dimming [ELEVATED]: the grant pickup and the stored strength clamp."""

import pytest

from .steps import BASIC, ELEVATED, require_elevated

STRENGTH = "aab_settings/dimmingStrength"


def _flash(run) -> str:
    return run.read_if_shown("aab_flash") or ""  # one dump: the flash lasts 2.5 s


def _require_revoked(run):
    if run.granted():
        pytest.skip("already granted; the BASIC → ELEVATED transition cannot be observed")


@pytest.mark.scenario("s06_16")
def test_s06_16_grant_raises_the_tier_on_resume(run):
    _require_revoked(run)
    run.open("dashboard")
    assert run.read("tier_badge") == BASIC
    run.grant()
    run.open("dashboard")
    run.wait_for(lambda: run.read("tier_badge") == ELEVATED, 10, "ELEVATED badge on resume")


@pytest.mark.scenario("s06_19a")
def test_s06_19a_strength_is_clamped_where_stored(run):
    # The value-level half (reduce_bright_colors_level in a dark room) stays manual. The message
    # is Tideo's own flash (AabFlash) while the app is in front, not an Android toast.
    require_elevated(run)  # the field is disabled below ELEVATED
    for typed, stored, announced in (("100", "65", True), ("64", "64", False),
                                     ("65", "65", False)):
        run.open("super_dimming")
        run.expect_pref(STRENGTH, stored)
        run.set_text("field_dimmingStrength", typed)
        run.tap("apply_settings", observe=False)
        if announced:
            run.wait_for(lambda: "reduced to 65" in _flash(run), 3, "the clamp message")
        else:
            # Apply always flashes "Applied" (DraftApplyBar); only the clamp message may not show.
            assert run.stays(lambda: "reduced to" not in _flash(run), 3), \
                f"a clamp message although nothing was clamped: {_flash(run)!r}"
        run.wait_for(lambda: run.pref(STRENGTH) == stored, 5, f"strength stored as {stored}")
        assert run.read("field_dimmingStrength_text") == stored
        run.open("super_dimming")  # leave and return
        assert run.read("field_dimmingStrength_text") == stored
