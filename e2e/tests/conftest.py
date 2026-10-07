"""Scenario plumbing: one journaled, recovered `Run` per device scenario.

A scenario test is marked `@pytest.mark.scenario("<row id>")` and takes the `run` fixture. Without
`TIDEO_E2E_SERIAL` every scenario SKIPs, so collection and the unit tests never need a device.
Scenarios run in the plan's effect order: read-only → settings → service/wake → grant →
revoke/force-stop → PANIC.
"""

from __future__ import annotations

import os

import pytest
import uiautomator2

from tideo_e2e import cli, device, harness, state
from tideo_e2e.device import Grade
from tideo_e2e.effects import DeviceFacts, footprint, skip_reason
from tideo_e2e.journal import Journal, private_store
from tideo_e2e.recovery import journal_effects, recover
from tideo_e2e.scenarios import load
from tideo_e2e.tideo import PORT_UI, DevicePort, scenario_ui
from tideo_e2e.ui import Ui

ROWS = {r.id: r for r in load()}
SERIAL_ENV = "TIDEO_E2E_SERIAL"
# Owner confirmations for this run, comma-separated: contexts, automation, unlock.
CONFIRM_ENV = "TIDEO_E2E_CONFIRM"


def _row(item):
    marker = item.get_closest_marker("scenario")
    return ROWS[marker.args[0]] if marker else None


def pytest_collection_modifyitems(config, items):
    for item in items:
        if _row(item) is not None:
            item.add_marker(pytest.mark.device)
    # Stable: unit tests keep their order ahead of every scenario.
    items.sort(key=lambda i: (1, footprint(r.effects).stage, r.id) if (r := _row(i))
               else (0, 0, ""))


def _facts(s: device.Session, row) -> DeviceFacts:
    fp = footprint(row.effects)
    held = {k: state.read_setting(s, *k) for k in fp.settings}
    absent = frozenset(k for k, v in held.items() if v is None)
    confirmed = frozenset(c.strip() for c in os.environ.get(CONFIRM_ENV, "").split(",") if c)
    return DeviceFacts(
        absent_rows=absent,
        unrestorable_rows=frozenset(k for k, v in held.items()
                                    if v is not None and not device.restorable(*k, v)),
        context_state=state.context_state(s),
        automation_on=state.read_pref(s, "control_prefs/external_control_enabled") == "true",
        force_dark_opt_in=state.read_pref(s, "control_prefs/force_dark_enabled") == "true",
        confirmed=confirmed,
    )


@pytest.fixture
def run(request):
    row = _row(request.node)
    assert row is not None, "a scenario test needs @pytest.mark.scenario(<row id>)"
    assert row.test == request.node.nodeid.split("[")[0], \
        f"scenarios.toml row {row.id} names {row.test!r}, not this test"
    target = os.environ.get(SERIAL_ENV)
    if not target:
        pytest.skip(f"set {SERIAL_ENV} to the phone's adb serial to run device scenarios")
    store, fp = private_store(), footprint(row.effects)
    with device.session(target, frozenset({Grade.READ}), cli.adb_client()) as s:
        identity = state.identity(s, store)
        facts = _facts(s, row)
        model = state.model(s)
        enabled = state.read_pref(s, "aab_settings/serviceEnabled") == "true"
        running = state.service_running(s)
        points = state.override_points(s) if "override_record" in row.effects else None
    if reason := skip_reason(row.effects, facts):
        pytest.skip(reason)
    # Recovery restores whether the service runs, not serviceEnabled on its own.
    if fp.runtime and enabled != running:
        pytest.skip("serviceEnabled and the running service disagree; start or stop it by hand")
    # At the cap a recorded point evicts the oldest, which no Discard brings back.
    if points is not None and len(points) >= state.OVERRIDE_POINTS_CAP:
        pytest.skip(f"{len(points)} curve points stored; an override would evict the oldest")
    with Journal.for_run(store, identity) as journal, \
            device.session(target, cli.ALL_GRADES, cli.adb_client()) as s:
        u2 = uiautomator2.connect(s.device)
        ui = Ui(s, u2, scenario_ui(row.effects))
        port = DevicePort(s, Ui(s, u2, PORT_UI), store)
        journal_effects(journal, port, row.effects)
        r = harness.Run(row, s, ui, journal, port, harness.profile_for(model))
        if points is not None:
            journal.set_points(points)  # recovery checks them, after a kill too
            r.points_before = points
        try:
            if "privileged_apply" in row.effects:
                r.journal_privileged()
            yield r
        finally:
            if points is not None and state.override_points(s) != points and r.paused():
                try:  # a failure between an override and its Discard; the service holds it
                    r.shade("shade_discard")
                except Exception as e:  # noqa: BLE001 - reported below, recovery must run
                    print(f"teardown Discard failed: {e}")
            report = recover(journal, port)
            for line in report.notes:
                print(f"recover note: {line}")
            if not report.clean:
                pytest.fail("recovery left conflicts; run e2e/run.sh --recover after resolving:\n  "
                            + "\n  ".join(report.conflicts))
