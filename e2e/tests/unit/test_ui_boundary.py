"""The UI boundary acts only on allowlisted, uniquely resolved, non-denied nodes."""

from types import SimpleNamespace

import pytest
import uiautomator2
from uiautomator2 import core

from tideo_e2e import ui
from tideo_e2e.device import DEBUG_PKG
from tideo_e2e.ui import (
    DISABLED_U2_METHODS, NOTIFICATION_ROW_ID, SHADE_PKG, Target, Ui, UiAllowlist, UiDenied,
    resolve,
)

ROW = f'package="{SHADE_PKG}" resource-id="{NOTIFICATION_ROW_ID}"'
HIERARCHY = f"""<hierarchy rotation="0">
  <node package="{DEBUG_PKG}" resource-id="service_switch" text="" content-desc=""
        bounds="[0,100][200,200]"/>
  <node package="{DEBUG_PKG}" resource-id="value_lux" text="12.5" content-desc=""
        bounds="[0,300][200,340]"/>
  <node package="{DEBUG_PKG}" resource-id="restore_factory" text="Restore factory profiles"
        bounds="[0,400][200,440]"/>
  <node package="{DEBUG_PKG}" resource-id="confirm" text="Delete" bounds="[0,500][200,540]"/>
  <node package="{DEBUG_PKG}" resource-id="twin" text="" bounds="[0,600][10,610]"/>
  <node package="{DEBUG_PKG}" resource-id="twin" text="" bounds="[0,620][10,630]"/>
  <node package="com.other" resource-id="service_switch" bounds="[0,700][10,710]"/>
  <node {ROW} bounds="[0,800][1000,900]">
    <node package="{SHADE_PKG}" text="Other app" bounds="[0,800][100,820]"/>
    <node package="{SHADE_PKG}" text="Resume" bounds="[0,850][100,890]"/>
  </node>
  <node {ROW} bounds="[0,900][1000,1000]">
    <node package="{SHADE_PKG}" text="Tideo AB (Debug)" bounds="[0,900][100,920]"/>
    <node package="{SHADE_PKG}" text="Resume" bounds="[200,950][300,990]"/>
  </node>
</hierarchy>"""

SWITCH = Target("switch", DEBUG_PKG, "click", resource_id="service_switch")
LUX = Target("lux", DEBUG_PKG, "read", resource_id="value_lux")
SHADE_RESUME = Target("resume", SHADE_PKG, "click", text="Resume", anchor="Tideo AB (Debug)")


def test_resolves_the_declared_node():
    assert resolve(HIERARCHY, SWITCH).centre() == (100, 150)


def test_shade_action_is_taken_from_tideo_row_only():
    assert resolve(HIERARCHY, SHADE_RESUME).centre() == (250, 970)


@pytest.mark.parametrize("target", [
    Target("twin", DEBUG_PKG, "click", resource_id="twin"),          # ambiguous
    Target("gone", DEBUG_PKG, "click", resource_id="absent"),         # missing
    Target("confirm", DEBUG_PKG, "click", resource_id="confirm"),     # node text is "Delete"
    Target("resume", SHADE_PKG, "click", text="Resume", anchor="Tideo Auto Brightness"),
])
def test_resolution_refuses(target):
    with pytest.raises(UiDenied):
        resolve(HIERARCHY, target)


def test_resolution_ignores_other_packages():
    # com.other's service_switch must not count toward or replace Tideo's.
    xml = HIERARCHY.replace(f'package="{DEBUG_PKG}" resource-id="service_switch"',
                            'package="com.x" resource-id="service_switch"')
    with pytest.raises(UiDenied):
        resolve(xml, SWITCH)


@pytest.mark.parametrize("kwargs", [
    dict(name="x", package=DEBUG_PKG, action="click"),                          # no locator
    dict(name="x", package=DEBUG_PKG, action="click", resource_id="restore_factory"),
    dict(name="x", package=DEBUG_PKG, action="click", resource_id="delete_profile_Night"),
    dict(name="x", package=DEBUG_PKG, action="click", resource_id="rule_editor_modal"),
    dict(name="x", package=DEBUG_PKG, action="click", resource_id="load_Outdoor"),
    dict(name="x", package=DEBUG_PKG, action="click", resource_id="run_wizard"),
    dict(name="x", package=DEBUG_PKG, action="click", text="Overwrite"),
    dict(name="x", package=DEBUG_PKG, action="click", text="Import profile"),
    dict(name="x", package=DEBUG_PKG, action="swipe", resource_id="service_switch"),
    dict(name="x", package="com.android.settings", action="click", text="OK"),
    dict(name="x", package=SHADE_PKG, action="click", text="Clear all", anchor="Tideo AB (Debug)"),
    dict(name="x", package=SHADE_PKG, action="click", text="Resume"),             # no anchor
    dict(name="x", package=SHADE_PKG, action="read", text="Resume", anchor="Tideo AB (Debug)"),
    dict(name="x", package=DEBUG_PKG, action="click", resource_id="a", anchor="Tideo AB (Debug)"),
])
def test_target_declaration_refused(kwargs):
    with pytest.raises(UiDenied):
        Target(**kwargs)


def test_allowlist_refuses_unknown_and_duplicate_names():
    with pytest.raises(UiDenied):
        UiAllowlist(SWITCH, SWITCH)
    with pytest.raises(UiDenied):
        UiAllowlist(SWITCH)["restore"]


# ── the Ui handle, against a fake device ───────────────────────────────────────────────────


class FakeU2:
    def __init__(self, xml=HIERARCHY):
        self.xml, self.clicks, self.shade_opened = xml, [], 0

    def dump_hierarchy(self):
        return self.xml

    def click(self, x, y):
        ui._admit_rpc("click", (x, y))  # what the real gate sees
        self.clicks.append((x, y))

    def open_notification(self):
        ui._admit_rpc("openNotification", [])
        self.shade_opened += 1


def _ui(foreground=DEBUG_PKG, *targets):
    app = SimpleNamespace(app_current=lambda: SimpleNamespace(package=foreground))
    d = FakeU2()
    return Ui(SimpleNamespace(device=app), d, UiAllowlist(*targets)), d


def test_click_and_read():
    handle, d = _ui(DEBUG_PKG, SWITCH, LUX)
    handle.click("switch")
    assert d.clicks == [(100, 150)]
    assert handle.read("lux") == "12.5"


def test_wrong_foreground_refused():
    handle, d = _ui("com.android.launcher", SWITCH)
    with pytest.raises(UiDenied):
        handle.click("switch")
    assert d.clicks == []


def test_action_must_match_declaration():
    handle, d = _ui(DEBUG_PKG, SWITCH, LUX)
    with pytest.raises(UiDenied):
        handle.click("lux")
    with pytest.raises(UiDenied):
        handle.read("switch")


def test_shade_only_with_a_declared_shade_target():
    handle, d = _ui(DEBUG_PKG, SWITCH)
    with pytest.raises(UiDenied):
        handle.open_shade()
    handle, d = _ui("com.android.launcher", SHADE_RESUME)
    handle.open_shade()
    handle.click("resume")
    assert d.shade_opened == 1 and d.clicks == [(250, 970)]


# ── the RPC gate and disabled conveniences ─────────────────────────────────────────────────


@pytest.mark.parametrize("method,params", [
    ("click", [1, 2]), ("pressKey", ["back"]), ("setText", [{}, "x"]), ("clearTextField", [{}]),
    ("swipe", [0, 0, 1, 1, 10]), ("injectInputEvent", [0, 1, 1, 0]), ("openNotification", []),
    ("openQuickSettings", []), ("dragTo", []), ("longClick", []),
])
def test_rpc_refused_without_authorisation(method, params):
    with pytest.raises(UiDenied):
        core._http_request(None, 9008, "POST", "/jsonrpc/0",
                           {"jsonrpc": "2.0", "id": 1, "method": method, "params": params})


def test_click_authorisation_is_exact_and_single_use():
    ui._pending.click = (5, 6)
    try:
        with pytest.raises(UiDenied):
            ui._admit_rpc("click", (5, 7))
        ui._admit_rpc("click", (5, 6))
        with pytest.raises(UiDenied):
            ui._admit_rpc("click", (5, 6))
    finally:
        ui._pending.click = None


def test_read_rpcs_pass_the_gate():
    for method in ui.READ_RPCS:
        ui._admit_rpc(method, [])


def test_other_http_paths_refused():
    with pytest.raises(UiDenied):
        core._http_request(None, 9008, "GET", "/screenshot/0")


@pytest.mark.parametrize("name", DISABLED_U2_METHODS)
def test_u2_conveniences_disabled(name):
    d = uiautomator2.Device.__new__(uiautomator2.Device)
    with pytest.raises(UiDenied):
        getattr(d, name)("com.tideo.autobrightness.debug")


# ── findings from the S2 review: nesting, anchors, missing tags ────────────────────────────


@pytest.mark.parametrize("xml", [
    # an allowed-looking child inside a denied control
    f'<hierarchy><node package="{DEBUG_PKG}" resource-id="export_profile" bounds="[0,0][9,9]">'
    f'<node package="{DEBUG_PKG}" resource-id="label" text="x" bounds="[0,0][9,9]"/>'
    f'</node></hierarchy>',
    # an allowed-looking container around a denied control
    f'<hierarchy><node package="{DEBUG_PKG}" resource-id="label" bounds="[0,0][9,9]">'
    f'<node package="{DEBUG_PKG}" resource-id="restore_factory" bounds="[0,0][9,9]"/>'
    f'</node></hierarchy>',
    # a denied label beside the target, in the same clickable
    f'<hierarchy><node package="{DEBUG_PKG}" resource-id="label" bounds="[0,0][9,9]">'
    f'<node package="{DEBUG_PKG}" text="Reset settings to defaults" bounds="[0,0][9,9]"/>'
    f'</node></hierarchy>',
])
def test_denied_control_around_or_inside_the_target_refused(xml):
    with pytest.raises(UiDenied):
        resolve(xml, Target("label", DEBUG_PKG, "click", resource_id="label"))


@pytest.mark.parametrize("anchor", ["Other app", "Tideo", "", None])
def test_shade_anchor_must_be_tideo(anchor):
    with pytest.raises(UiDenied):
        Target("r", SHADE_PKG, "click", text="Reset", anchor=anchor)


@pytest.mark.parametrize("kwargs", [
    dict(resource_id="confirm_load_profile"), dict(resource_id="confirm_save_profile"),
    dict(resource_id="apply_profile_Night"), dict(resource_id="add_context_rule"),
    dict(resource_id="apply_wizard"), dict(text="Reset settings to defaults"),
    dict(text="Export current settings…"), dict(text="Load"),
])
def test_review_found_targets_denied(kwargs):
    with pytest.raises(UiDenied):
        Target("x", DEBUG_PKG, "click", **kwargs)


def test_foreground_is_checked_before_the_dump():
    handle, d = _ui("com.android.launcher", SWITCH)
    d.xml = "not xml"  # a dump would raise ParseError, not UiDenied
    with pytest.raises(UiDenied):
        handle.click("switch")


# ── S4: text, scroll-into-view, back from the shade, Discard ──────────────────────────────


def test_set_text_scroll_and_back_are_single_use_and_exact():
    ui._pending.text = ("field_dimmingStrength", "65")
    ui._pending.scroll = "apply_settings"
    ui._pending.back = True
    try:
        with pytest.raises(UiDenied):
            ui._admit_rpc("setText", [{"resourceId": "field_dimmingStrength"}, "66"])
        ui._admit_rpc("setText", [{"resourceId": "field_dimmingStrength"}, "65"])
        with pytest.raises(UiDenied):
            ui._admit_rpc("setText", [{"resourceId": "field_dimmingStrength"}, "65"])
        with pytest.raises(UiDenied):
            ui._admit_rpc("scrollTo", [{"scrollable": True}, {"resourceId": "restore_x"}, True])
        ui._admit_rpc("scrollTo", [{"scrollable": True}, {"resourceId": "apply_settings"}, True])
        with pytest.raises(UiDenied):
            ui._admit_rpc("pressKey", ["home"])
        ui._admit_rpc("pressKey", ["back"])
        with pytest.raises(UiDenied):
            ui._admit_rpc("pressKey", ["back"])
    finally:
        ui._pending.text = ui._pending.scroll = ui._pending.back = None


def test_edit_targets_need_a_resource_id():
    with pytest.raises(UiDenied):
        Target("x", DEBUG_PKG, "edit", text="65")


def test_discard_is_a_shade_action_on_tideo_only():
    Target("d", SHADE_PKG, "click", text="Discard", anchor="Tideo AB (Debug)")
    with pytest.raises(UiDenied):
        Target("d", SHADE_PKG, "click", text="Disable", anchor="Tideo AB (Debug)")


def test_back_is_refused_while_tideo_is_in_front():
    handle, d = _ui(DEBUG_PKG, SHADE_RESUME)
    with pytest.raises(UiDenied):
        handle.close_shade()
