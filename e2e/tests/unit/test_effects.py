"""The effect inventory: every key is restorable, order follows the plan, SKIP rules fire."""

import pytest

from tideo_e2e import device
from tideo_e2e.effects import (
    DeviceFacts, EVERY_TIDEO_KEY, INVENTORY, KILL, PANIC, READ, SERVICE, SNAPSHOT_KEYS,
    footprint, run_order, skip_reason,
)
from tideo_e2e.scenarios import EFFECTS, Scenario, load


def test_inventory_covers_exactly_the_manifest_vocabulary():
    assert set(INVENTORY) == EFFECTS


def test_every_inventory_key_is_writable_through_the_boundary():
    for ns, key in EVERY_TIDEO_KEY:
        assert device.SETTINGS[ns][key] is not None, (ns, key)
    assert EVERY_TIDEO_KEY <= SNAPSHOT_KEYS


def test_every_manifest_row_has_a_footprint():
    for row in load():
        footprint(row.effects)


def test_unknown_kind_raises():
    with pytest.raises(KeyError):
        footprint(["wipe"])


def test_panic_footprint_covers_its_indirect_writes():
    fp = footprint(["panic"])
    assert fp.settings == EVERY_TIDEO_KEY and fp.runtime and fp.private


@pytest.mark.parametrize("kind", ["revoke", "force_stop", "broadcast", "notification_action", "grant"])
def test_anything_that_may_restart_the_service_carries_its_footprint(kind):
    fp = footprint([kind])
    assert fp.starts_service and fp.settings == EVERY_TIDEO_KEY and fp.runtime


def test_prefs_reach_every_tideo_key():
    assert footprint(["prefs_ui"]).settings == EVERY_TIDEO_KEY


def test_order_is_settings_then_service_then_kill_then_panic():
    rows = [Scenario("a", "auto", ("panic",)), Scenario("b", "auto", ("force_stop",)),
            Scenario("c", "auto", ("service_toggle",)), Scenario("d", "auto", ("settings_write",)),
            Scenario("e", "auto", ("ui_nav",))]
    assert [r.id for r in run_order(rows)] == ["e", "d", "c", "b", "a"]
    assert footprint(["ui_nav"]).stage == READ
    assert footprint(["service_toggle", "panic"]).stage == PANIC
    assert footprint(["revoke"]).stage == KILL and footprint(["broadcast"]).stage == SERVICE


@pytest.mark.parametrize("effects,facts,expect", [
    (["panic"], DeviceFacts(absent_rows=frozenset({("secure", "doze_always_on")})), "absent"),
    (["privileged_apply"],
     DeviceFacts(absent_rows=frozenset({("global", "user_disabled_hdr_formats")})), "absent"),
    (["settings_write"], DeviceFacts(absent_rows=frozenset({("secure", "doze_always_on")})), None),
    (["service_toggle"],
     DeviceFacts(absent_rows=frozenset({("secure", "reduce_bright_colors_level")})), "absent"),
    (["service_toggle"], DeviceFacts(context_state=True), "context"),
    (["screen_wake"], DeviceFacts(context_state=True), "context"),
    (["service_toggle"], DeviceFacts(context_state=True, confirmed=frozenset({"contexts"})), None),
    (["automation_events"], DeviceFacts(), "STATE_CHANGED"),
    (["broadcast"], DeviceFacts(automation_on=True), "STATE_CHANGED"),
    (["broadcast"], DeviceFacts(), None),
    (["service_toggle"], DeviceFacts(automation_on=True, confirmed=frozenset({"automation"})),
     None),
    (["service_toggle"], DeviceFacts(force_dark_opt_in=True), "force-dark"),
    (["ui_nav"], DeviceFacts(context_state=True, automation_on=True, force_dark_opt_in=True),
     None),
])
def test_skip_rules(effects, facts, expect):
    reason = skip_reason(effects, facts)
    assert (reason is None) if expect is None else (expect in reason)
