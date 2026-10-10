"""The UI boundary acts only on allowlisted, uniquely resolved, non-denied nodes."""

from types import SimpleNamespace

import pytest
import uiautomator2
from uiautomator2 import core

from tideo_e2e import ui
from tideo_e2e.device import DEBUG_PKG
from tideo_e2e.ui import (
    APP_NAME_ID, DISABLED_U2_METHODS, NOTIFICATION_ROW_ID, SHADE_PKG, Target, Ui, UiAllowlist, UiDenied,
    resolve,
)

ROW = f'package="{SHADE_PKG}" resource-id="{NOTIFICATION_ROW_ID}"'
HEADER = f'package="{SHADE_PKG}" resource-id="{APP_NAME_ID}"'
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
    <node {HEADER} text="Other app" bounds="[0,800][100,820]"/>
    <node package="{SHADE_PKG}" text="Resume" bounds="[0,850][100,890]"/>
  </node>
  <node {ROW} bounds="[0,900][1000,1000]">
    <node {HEADER} text="Tideo AB (Debug)" bounds="[0,900][100,920]"/>
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


class FakeAdb:
    """`dumpsys window displays` focus, and the shade collapse command, which takes focus back."""

    def __init__(self, focus):
        self.focus, self.commands = focus, []

    def shell(self, argv):
        if argv == ["dumpsys", "window", "displays"]:
            title = "NotificationShade" if self.focus == SHADE_PKG else f"{self.focus}/.Main"
            return f"  mCurrentFocus=Window{{4f2a u0 {title}}}\n"
        assert argv == ["cmd", "statusbar", "collapse"], argv
        self.commands.append(argv)
        self.focus = DEBUG_PKG
        return ""


def _ui(foreground=DEBUG_PKG, *targets):
    d = FakeU2()
    return Ui(SimpleNamespace(device=FakeAdb(foreground)), d, UiAllowlist(*targets)), d


def test_click_and_read():
    handle, d = _ui(DEBUG_PKG, SWITCH, LUX)
    handle.click("switch")
    assert d.clicks == [(100, 150)]
    assert handle.read("lux") == "12.5"


def test_read_takes_a_container_label_from_its_one_text_child():
    # A Compose AssistChip on device: the tagged node is empty, the label is a child TextView.
    chip = (f'<hierarchy><node package="{DEBUG_PKG}" resource-id="badge" text="" '
            f'bounds="[0,0][100,50]">{{}}</node></hierarchy>')
    one = f'<node package="{DEBUG_PKG}" text="Basic" bounds="[10,10][90,40]"/>'
    blank = f'<node package="{DEBUG_PKG}" text="" bounds="[0,5][100,45]"/>'
    two = one + f'<node package="{DEBUG_PKG}" text="Elevated" bounds="[10,10][90,40]"/>'
    badge = Target("badge", DEBUG_PKG, "read", resource_id="badge")
    assert resolve(chip.format(one + blank), badge).label() == "Basic"
    assert resolve(chip.format(blank), badge).label() == ""
    with pytest.raises(UiDenied):
        resolve(chip.format(two), badge).label()
    assert resolve(HIERARCHY, LUX).label() == "12.5"


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
    handle, d = _ui(SHADE_PKG, SHADE_RESUME)
    handle.open_shade()
    handle.click("resume")
    assert d.shade_opened == 1 and d.clicks == [(250, 970)]
    # adbutils' app_current() named the app under an open shade (DD-033): focus decides.
    handle, d = _ui(DEBUG_PKG, SHADE_RESUME)
    with pytest.raises(UiDenied):
        handle.click("resume")
    assert d.clicks == []


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
    field = {"resourceId": "field_dimmingStrength", "packageName": DEBUG_PKG}
    apply = {"resourceId": "apply_settings", "packageName": DEBUG_PKG}
    ours = {"scrollable": True, "packageName": DEBUG_PKG}
    ui._pending.text = (("field_dimmingStrength", DEBUG_PKG), "65")
    ui._pending.scroll = ("scrollForward", DEBUG_PKG)
    ui._pending.back = True
    try:
        with pytest.raises(UiDenied):
            ui._admit_rpc("setText", [field, "66"])
        with pytest.raises(UiDenied):  # any package's node with that id
            ui._admit_rpc("setText", [{"resourceId": "field_dimmingStrength"}, "65"])
        ui._admit_rpc("setText", [field, "65"])
        with pytest.raises(UiDenied):
            ui._admit_rpc("setText", [field, "65"])
        with pytest.raises(UiDenied):  # the other direction
            ui._admit_rpc("scrollBackward", [ours, True, 55])
        with pytest.raises(UiDenied):  # scrollIntoView is no longer admitted
            ui._admit_rpc("scrollTo", [ours, apply, True])
        with pytest.raises(UiDenied):  # another package's scrollable
            ui._admit_rpc("scrollForward", [{"scrollable": True}, True, 55])
        ui._admit_rpc("scrollForward", [ours, True, 55])
        with pytest.raises(UiDenied):
            ui._admit_rpc("scrollForward", [ours, True, 55])
        with pytest.raises(UiDenied):
            ui._admit_rpc("pressKey", ["home"])
        with pytest.raises(UiDenied):  # the shade closes by command now (DD-033)
            ui._admit_rpc("pressKey", ["back"])
    finally:
        ui._pending.text = ui._pending.scroll = ui._pending.back = None


class ScrollingU2(FakeU2):
    """`pages` dumps in scroll order: a forward swipe moves one page on, until the last."""

    def __init__(self, pages, start=0):
        super().__init__(pages[start])
        self.pages, self.at, self.swipes = pages, start, []

    def __call__(self, **selector):
        fake = self

        class _Vert:
            def _go(self, method, step):
                ui._admit_rpc(method, [selector, True, 55])
                fake.swipes.append(method)
                fake.at = max(0, min(len(fake.pages) - 1, fake.at + step))
                fake.xml = fake.pages[fake.at]

            def forward(self):
                self._go("scrollForward", 1)

            def backward(self):
                self._go("scrollBackward", -1)

        return SimpleNamespace(scroll=SimpleNamespace(vert=_Vert()))


def _scrolling_ui(pages, start=0):
    app = FakeAdb(DEBUG_PKG)
    d = ScrollingU2(pages, start)
    return Ui(SimpleNamespace(device=app), d, UiAllowlist(SWITCH)), d


def test_an_off_screen_target_is_swiped_into_view_in_either_direction():
    blank = '<hierarchy rotation="0"><node package="{}" scrollable="true" text="p{}"/></hierarchy>'
    pages = [blank.format(DEBUG_PKG, i) for i in range(4)]
    # Three swipes forward reach it (scrollIntoView stopped after one on Compose, DD-033).
    handle, d = _scrolling_ui(pages[:3] + [HIERARCHY])
    handle.click("switch")
    assert d.swipes == ["scrollForward"] * 3 and d.clicks == [(100, 150)]
    # Above the viewport: forward to the end (an unchanged dump), then back up to it.
    handle, d = _scrolling_ui([HIERARCHY] + pages[:2], start=1)
    handle.click("switch")
    assert d.swipes == ["scrollForward"] * 2 + ["scrollBackward"] * 2


def test_an_absent_target_stops_at_both_ends():
    blank = (f'<hierarchy rotation="0"><node package="{DEBUG_PKG}" scrollable="true" '
             f'text="x"/></hierarchy>')
    handle, d = _scrolling_ui([blank])
    with pytest.raises(UiDenied):
        handle.click("switch")
    assert d.swipes == ["scrollForward", "scrollBackward"]


def test_no_swipe_without_the_packages_own_scrollable_in_the_dump():
    # Hidden behind another window, uiautomator2 waited out its selector timeout per swipe.
    for node in (f'<node package="{DEBUG_PKG}" text="x"/>',
                 f'<node package="{SHADE_PKG}" scrollable="true" text="x"/>'):
        handle, d = _scrolling_ui([f'<hierarchy rotation="0">{node}</hierarchy>'] * 2)
        with pytest.raises(UiDenied):
            handle.click("switch")
        assert d.swipes == []


PAUSED = "Auto Brightness paused"


def _row(title, top, extra="", button=(900, 1000)):
    """An OxygenOS notification row: collapsed it has a title and an expand button only."""
    return (f'<node {ROW} bounds="[0,{top}][1000,{top + 100}]">'
            f'<node package="{SHADE_PKG}" resource-id="android:id/title" text="{title}" '
            f'bounds="[0,{top}][500,{top + 40}]"/>'
            f'<node package="{SHADE_PKG}" resource-id="android:id/expand_button" '
            f'bounds="[{button[0]},{top}][{button[1]},{top + 40}]"/>{extra}</node>')


def _shade(*rows, front=SHADE_PKG):
    app = FakeAdb(front)
    d = FakeU2('<hierarchy rotation="0">' + "".join(rows) + "</hierarchy>")
    return Ui(SimpleNamespace(device=app), d, UiAllowlist(SHADE_RESUME)), d


def _posters(table):
    return lambda title: frozenset(table.get(title, ()))


def test_tideos_own_collapsed_row_is_expanded_once():
    handle, d = _shade(_row("Someone else's title", 0), _row(PAUSED, 900))
    assert handle.expand_own_row(_posters({PAUSED: [DEBUG_PKG]})) is True
    assert d.clicks == [(950, 920)]  # its expand button, nothing else


@pytest.mark.parametrize("rows, posters", [
    # Another app's row with Tideo's title (Sol): the title alone proves nothing.
    ([_row(PAUSED, 900)], {PAUSED: ["com.other"]}),
    ([_row(PAUSED, 900)], {PAUSED: [DEBUG_PKG, "com.other"]}),
    # Already expanded: a header or an action of its own; a tap would collapse it.
    ([_row(PAUSED, 900, f'<node {HEADER} text="Tideo AB (Debug)" bounds="[0,950][9,960]"/>')],
     {PAUSED: [DEBUG_PKG]}),
    ([_row(PAUSED, 900, f'<node package="{SHADE_PKG}" resource-id="android:id/action0" '
                        f'text="Resume" bounds="[0,950][90,990]"/>')], {PAUSED: [DEBUG_PKG]}),
    # A group's parent does not inherit its child row's title.
    ([f'<node {ROW} bounds="[0,0][1000,400]"><node package="{SHADE_PKG}" '
      f'resource-id="android:id/expand_button" bounds="[900,0][1000,40]"/>'
      + _row(PAUSED, 200, f'<node {HEADER} text="x" bounds="[0,250][9,260]"/>') + "</node>"],
     {PAUSED: [DEBUG_PKG]}),
    # An expand button outside its row's bounds.
    ([_row(PAUSED, 900, button=(1100, 1200))], {PAUSED: [DEBUG_PKG]}),
])
def test_no_other_row_is_expanded(rows, posters):
    handle, d = _shade(*rows)
    assert handle.expand_own_row(_posters(posters)) is False
    assert d.clicks == []


def test_expansion_needs_the_shade_in_front_and_a_clean_row():
    handle, d = _shade(_row(PAUSED, 900), front=DEBUG_PKG)
    with pytest.raises(UiDenied):
        handle.expand_own_row(_posters({PAUSED: [DEBUG_PKG]}))
    handle, d = _shade(_row(PAUSED, 0), _row(PAUSED, 900))  # two qualify: neither is tapped
    with pytest.raises(UiDenied):
        handle.expand_own_row(_posters({PAUSED: [DEBUG_PKG]}))
    denied = (f'<node package="{SHADE_PKG}" resource-id="restore_factory" text="" '
              f'bounds="[0,0][1000,2000]">' + _row(PAUSED, 900) + "</node>")
    handle, d = _shade(denied)
    with pytest.raises(UiDenied):
        handle.expand_own_row(_posters({PAUSED: [DEBUG_PKG]}))
    assert d.clicks == []


def test_edit_targets_need_a_resource_id():
    with pytest.raises(UiDenied):
        Target("x", DEBUG_PKG, "edit", text="65")


def test_discard_is_a_shade_action_on_tideo_only():
    Target("d", SHADE_PKG, "click", text="Discard", anchor="Tideo AB (Debug)")
    with pytest.raises(UiDenied):
        Target("d", SHADE_PKG, "click", text="Disable", anchor="Tideo AB (Debug)")


def test_enabled_reads_a_greyed_out_control():
    # Apply is always on screen and only enabled with something to apply (DraftApplyBar).
    bar = Target("bar", DEBUG_PKG, "read", resource_id="apply_settings")
    xml = ('<hierarchy><node package="{}" resource-id="apply_settings" enabled="{}" '
           'bounds="[0,0][10,10]"/></hierarchy>')
    assert resolve(xml.format(DEBUG_PKG, "false"), bar).enabled is False
    assert resolve(xml.format(DEBUG_PKG, "true"), bar).enabled is True


def test_the_shade_closes_by_command_and_only_when_open():
    handle, d = _ui(DEBUG_PKG, SHADE_RESUME)
    handle.close_shade()  # already closed: nothing is sent
    assert handle._s.device.commands == []
    handle, d = _ui(SHADE_PKG, SHADE_RESUME)
    handle.close_shade()
    assert handle._s.device.commands == [["cmd", "statusbar", "collapse"]]
    assert handle.focus() == DEBUG_PKG


@pytest.mark.parametrize("dump, focus", [
    ("  mCurrentFocus=Window{1a u0 NotificationShade}\n", SHADE_PKG),
    (f"  mCurrentFocus=Window{{1a u0 {DEBUG_PKG}/com.x.MainActivity}}\n", DEBUG_PKG),
    (f"  mCurrentFocus=Window{{1a u0 {DEBUG_PKG}}}\n", DEBUG_PKG),     # a dialog's window
    ("  mCurrentFocus=null\n", None),
    ("  mCurrentFocus=Window{1a u0 StatusBar}\n", None),              # unknown title
    ("  mCurrentFocus=Window{1a u0 NotificationShade}\n"
     f"  mCurrentFocus=Window{{2b u0 {DEBUG_PKG}/.Main}}\n", None),   # displays disagree
    (f"  mCurrentFocus=null\n  mCurrentFocus=Window{{2b u0 {DEBUG_PKG}/.Main}}\n", None),
    (f"  mCurrentFocus=Window{{1a u0 {SHADE_PKG}/.VolumeDialog}}\n", None),  # not the shade
])
def test_focus_reads_the_shade_an_app_or_unknown(dump, focus):
    assert ui.focus_in_dump(dump) == focus


# ── findings from the S2–S4 blocking review: notification ownership, covered tap points ─────


def test_tideo_label_outside_the_header_does_not_anchor():
    # Another app's notification carrying Tideo's label in its body, with a Reset action.
    xml = (f'<hierarchy><node {ROW} bounds="[0,0][1000,100]">'
           f'<node {HEADER} text="Other App" bounds="[0,0][100,20]"/>'
           f'<node package="{SHADE_PKG}" text="Tideo AB (Debug)" bounds="[0,30][100,50]"/>'
           f'<node package="{SHADE_PKG}" text="Reset" bounds="[0,60][100,90]"/>'
           f'</node></hierarchy>')
    with pytest.raises(UiDenied):
        resolve(xml, Target("r", SHADE_PKG, "click", text="Reset", anchor="Tideo AB (Debug)"))


def test_a_covered_tap_point_is_refused():
    xml = HIERARCHY.replace(
        "</hierarchy>",
        '<node package="com.google.android.inputmethod.latin" bounds="[0,120][1000,400]"/>'
        "</hierarchy>")
    with pytest.raises(UiDenied):
        resolve(xml, SWITCH)
    handle, d = _ui(DEBUG_PKG, SWITCH)
    d.xml = xml
    with pytest.raises(UiDenied):
        handle.click("switch")
    assert d.clicks == []
