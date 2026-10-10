"""The scenario runtime: conversions, and every action refused for an undeclared effect before
anything is sent; plus a static check that each scenario's calls fit its row."""

from __future__ import annotations

import ast
from types import SimpleNamespace

import pytest

from tideo_e2e import harness, state
from tideo_e2e.harness import Run, UndeclaredEffect, far_domain, to_domain, to_raw
from tideo_e2e.scenarios import E2E_ROOT, Scenario, load
from tideo_e2e.tideo import TARGETS, scenario_ui
from tideo_e2e.ui import UiDenied


# DEVICE_TEST_SCRIPT §2 10b's own record at S = 4095 (DC-027).
@pytest.mark.parametrize("raw,domain", [(1092, 68), (931, 58), (4095, 255), (0, 0)])
def test_to_domain(raw, domain):
    assert to_domain(raw, 4095) == domain


@pytest.mark.parametrize("domain,raw", [(69, 1108), (60, 964), (255, 4095)])
def test_to_raw(domain, raw):
    assert to_raw(domain, 4095) == raw


def test_far_domain_keeps_its_distance():
    for d in range(256):
        assert abs(far_domain(d) - d) >= 60
    assert far_domain(200, 60) == 130


def test_owner_profile_loads():
    assert harness.profile_for("CPH2653").stored_max == 4095
    assert harness.profile_for("../CPH2653") is None


class Refuser:
    """Any device, UI or journal use fails the test: the refusal must come first."""

    def __getattr__(self, name):
        raise AssertionError(f"reached {name} before the refusal")


def _run(*effects):
    return Run(Scenario("sx", "auto", tuple(effects)), Refuser(), Refuser(), Refuser(),
               Refuser(), harness.Profile(4095), sleep=lambda _s: None)


@pytest.mark.parametrize("effects,action", [
    ((), lambda r: r.put("system", "screen_brightness", 10)),
    (("privileged_apply",), lambda r: r.put("system", "screen_brightness", 10)),
    (("settings_write",), lambda r: r.put("global", "stay_on_while_plugged_in", 0)),
    (("ui_nav",), lambda r: r.keyevent("KEYCODE_SLEEP")),
    (("broadcast",), lambda r: r.broadcast("PANIC")),
    (("revoke",), lambda r: r.grant()),
    (("grant",), lambda r: r.revoke()),
    (("ui_nav",), lambda r: r.service(True)),
    (("ui_nav",), lambda r: r.tap("service_switch")),
    (("ui_nav",), lambda r: r.tap("apply_settings")),
    (("ui_nav",), lambda r: r.set_text("field_dimmingStrength", "9")),
    (("ui_nav",), lambda r: r.shade("shade_discard")),
    (("service_toggle",), lambda r: r.open("dashboard")),
    (("prefs_ui",), lambda r: r.expect_pref("aab_settings/contextOverride", "true")),
])
def test_undeclared_actions_refused_before_sending(effects, action):
    with pytest.raises(UndeclaredEffect):
        action(_run(*effects))


def test_launch_that_would_start_the_service_needs_a_service_effect(monkeypatch):
    r = _run("ui_nav")
    r.port = SimpleNamespace(service_running=lambda: False)
    monkeypatch.setattr(state, "read_pref", lambda _s, _k: "true")
    with pytest.raises(UndeclaredEffect, match="start the service"):
        r.open("dashboard")


# ── static: every Run call in a scenario is covered by its row's effects ───────────────────

VERB_EFFECTS = {
    "put": {"settings_write"}, "put_brightness": {"settings_write"},
    "keyevent": {"screen_wake"}, "broadcast": {"broadcast"}, "grant": {"grant"},
    "revoke": {"revoke"}, "service": {"service_toggle"}, "open": {"ui_nav"},
    "expect_pref": {"prefs_ui", "privileged_apply"},
}
TARGET_VERBS = ("tap", "set_text", "shade")


def _needs(fn: ast.FunctionDef, helpers: dict) -> list[set[str]]:
    """One any-of set per gated call in `fn`, following calls into tests/steps.py."""
    out = []
    for node in ast.walk(fn):
        if not isinstance(node, ast.Call):
            continue
        f = node.func
        if isinstance(f, ast.Attribute) and isinstance(f.value, ast.Name) and f.value.id == "run":
            if f.attr in VERB_EFFECTS:
                out.append(VERB_EFFECTS[f.attr])
            elif f.attr in TARGET_VERBS:
                if f.attr == "shade":
                    out.append({"notification_action"})
                arg = node.args[0]
                names = [arg.value] if isinstance(arg, ast.Constant) else []
                if not names:  # a variable: every target it could name must be covered
                    names = [n.value for n in ast.walk(fn) if isinstance(n, ast.Constant)
                             and isinstance(n.value, str) and n.value in TARGETS]
                out.extend(set(TARGETS[n][1]) for n in names if TARGETS[n][1])
        elif isinstance(f, ast.Name) and f.id in helpers:
            out.extend(_needs(helpers[f.id], helpers))
    return out


def _functions(path):
    return {n.name: n for n in ast.parse(path.read_text(encoding="utf-8")).body
            if isinstance(n, ast.FunctionDef)}


def _scenario_tests():
    helpers = _functions(E2E_ROOT / "tests" / "steps.py")
    for path in sorted((E2E_ROOT / "tests").glob("test_s*.py")):
        local = {**helpers, **_functions(path)}
        for fn in local.values():
            for dec in fn.decorator_list:
                if isinstance(dec, ast.Call) and getattr(dec.func, "attr", "") == "scenario":
                    yield dec.args[0].value, f"tests/{path.name}::{fn.name}", fn, local


ROWS = {r.id: r for r in load()}
SCENARIOS = list(_scenario_tests())


def test_every_auto_row_has_its_scenario_and_back():
    marked = {sid: node for sid, node, _f, _l in SCENARIOS}
    assert {r.id: r.test for r in ROWS.values() if r.test} == marked


# Run's handles: a scenario reaching through one would skip every gate above (S2–S4 review).
RUN_INTERNALS = {"ui", "port", "s", "journal", "fp", "row", "profile"}


def test_scenarios_use_only_runs_gated_methods():
    paths = [E2E_ROOT / "tests" / "steps.py", *sorted((E2E_ROOT / "tests").glob("test_s*.py"))]
    hits = [f"{p.name}:{n.lineno}: run.{n.attr}" for p in paths
            for n in ast.walk(ast.parse(p.read_text(encoding="utf-8")))
            if isinstance(n, ast.Attribute) and isinstance(n.value, ast.Name)
            and n.value.id == "run" and (n.attr in RUN_INTERNALS or n.attr.startswith("_"))]
    assert hits == []


def test_a_scenarios_ui_allowlist_holds_only_its_effects_controls():
    allow = scenario_ui(("ui_nav",))
    allow["menu_dashboard"], allow["apply_settings_shown"]  # noqa: B018 - reads and navigation
    for name in ("automation_toggle", "service_switch", "apply_settings", "shade_reset"):
        with pytest.raises(UiDenied):
            allow[name]  # noqa: B018


@pytest.mark.parametrize("sid,node,fn,local", SCENARIOS, ids=[s[0] for s in SCENARIOS])
def test_scenario_calls_fit_its_declared_effects(sid, node, fn, local):
    declared = set(ROWS[sid].effects)
    missing = [sorted(need) for need in _needs(fn, local) if not need & declared]
    assert missing == [], f"{sid} acts beyond its effects {sorted(declared)}"


# ── S4 review: preference journaling and the shade's two dangerous actions ─────────────────


def _journaled(*keys):
    return SimpleNamespace(pending=lambda kind=None: [SimpleNamespace(key=k) for k in keys])


@pytest.mark.parametrize("name,journaled", [
    ("automation_toggle", ()),
    ("switch_inversion", ("aab_settings/inversionEnabled",)),  # the rest of the draft is not
    ("pd_stay_awake_custom_preserved_overwrite", ()),
])
def test_pref_controls_need_their_prefs_journaled(name, journaled):
    r = _run("prefs_ui", "privileged_apply")
    r.journal = _journaled(*journaled)
    with pytest.raises(UndeclaredEffect, match="unjournaled"):
        r.tap(name)


def test_apply_is_modelled_per_screen():
    r = _run("prefs_ui", "privileged_apply")
    r.journal = _journaled("aab_settings/dimmingStrength")
    r._screen = "privileged_display"
    with pytest.raises(UndeclaredEffect, match="unjournaled"):
        r.tap("apply_settings")
    r._screen = "tools"
    with pytest.raises(UndeclaredEffect, match="not modelled"):
        r.tap("apply_settings")


def test_reset_is_panic_and_discard_is_override_record():
    with pytest.raises(UndeclaredEffect):
        _run("notification_action").shade("shade_reset")
    with pytest.raises(UndeclaredEffect):
        _run("notification_action").shade("shade_discard")


@pytest.mark.parametrize("before,now", [
    (["a"], ["a"]),            # nothing recorded: the Discard would hit the owner's point
    (["a"], ["b", "c"]),       # the owner's point is already gone
    (["a"], ["a", "b", "c"]),  # more than one new point
])
def test_discard_only_removes_the_runs_own_point(monkeypatch, before, now):
    r = _run("notification_action", "override_record")
    r.points_before = before
    monkeypatch.setattr(state, "override_points", lambda _s: now)
    with pytest.raises(UndeclaredEffect, match="Discard"):
        r.shade("shade_discard")
