"""UI boundary: act only through a scenario's declared allowlist, independent of the command one.

An allowed tap can still trigger destructive app behaviour, so the harness never touches the UI
except by naming a `Target` in its scenario's `UiAllowlist`. Each action dumps the hierarchy,
resolves the target to exactly one node of the declared package, checks the foreground, checks
the node against a global denylist, and only then taps the centre of that node or sets its text.
Test code never supplies coordinates. A target off screen is first scrolled into view by
swiping the package's scrollable (Compose leaves nodes outside the viewport out of the dump).

Importing this module also gates uiautomator2 at its one HTTP chokepoint (`core._http_request`):
read-only RPCs pass; `click`, `setText`, one scroll swipe and `openNotification` pass only for
the one call `Ui` has just authorised; every other RPC is refused. Its
package/IME-mutating conveniences are replaced so they raise before reaching the gate.
"""

from __future__ import annotations

import re
import time
import threading
from dataclasses import dataclass
from typing import Callable, Literal
from xml.etree import ElementTree

import uiautomator2
from uiautomator2 import core

from . import device
from .device import DEBUG_PKG, BoundaryViolation, PACKAGES

SHADE_PKG = "com.android.systemui"
MAX_SWIPES = 15  # per direction, to bring an off-screen target into view
SHADE_WINDOW = "NotificationShade"  # its focused-window title (OxygenOS 16, AOSP alike)
SHADE_POLLS, SHADE_POLL_S = 10, 0.3
# AOSP's row id (status_bar_notification_row.xml); S6's smoke run confirms it on the owner's OEM.
NOTIFICATION_ROW_ID = f"{SHADE_PKG}:id/expandableNotificationRow"
# The named actions on Tideo's own notifications (plan §3.2). Discard forgets the curve point an
# override pause recorded (DD-011), so a scenario that causes one leaves the owner's data as found.
SHADE_ACTIONS = frozenset({"Resume", "Reset", "Discard"})
# The app-name header of Tideo's notification row: appLabel for release and debug. Only the
# header node counts, never the same text in another app's title or body (S2–S4 review).
TIDEO_LABELS = frozenset({"Tideo Auto Brightness", "Tideo AB (Debug)"})
APP_NAME_ID = "android:id/app_name_text"
# A collapsed row shows neither the app name nor the actions (OxygenOS, DD-033). Tideo's
# titles (values/strings.xml notif_title_* and notif_override_title) pick the candidates.
TIDEO_TITLES = frozenset({"Auto Brightness active", "Auto Brightness paused",
                          "Auto Brightness — permission needed", "Manual override detected"})
TITLE_ID, EXPAND_ID, ACTION_ID = "android:id/title", "android:id/expand_button", "android:id/action0"

# Never acted on, whatever a scenario declares, nor any node nested in or around one: profile
# delete/overwrite/load/apply/save/restore-factory, import/export, context-rule editing,
# calibration and grant flows.
_DENY_IDS = re.compile(
    r"(restore_factory|reset_defaults|import_profile|export_profile|choose_legacy_folder"
    r"|save_profile_.*|save_rule|rule_.*|edit_.*|delete_.*|override_delete_.*|load_.*"
    r"|confirm_load_profile|confirm_save_profile|apply_profile_.*|add_context_rule"
    r"|legacy_.*|overwrite_profile_.*|profile_menu_.*|run_wizard|wizard_.*|apply_wizard"
    r"|power_start|power_calibration_overlay|use_current_.*|grant_root|grant_shizuku"
    r"|pd_grant_.*)"
)
_DENY_TEXT = re.compile(
    r"(?i)\s*(delete|overwrite|restore factory.*|reset settings.*|import.*|export.*|load.*"
    r"|calibrat.*)\s*"
)


class UiDenied(BoundaryViolation):
    pass


@dataclass(frozen=True)
class Target:
    """One allowlisted UI element. Locators are exact; at least one is required."""

    name: str
    package: str
    action: Literal["read", "click", "edit"]
    resource_id: str | None = None
    text: str | None = None
    description: str | None = None
    anchor: str | None = None  # shade only: text in the same notification row (Tideo's label)

    def __post_init__(self):
        if not (self.resource_id or self.text or self.description):
            raise UiDenied(f"{self.name}: no locator")
        if self.action not in ("read", "click", "edit"):
            raise UiDenied(f"{self.name}: unknown action {self.action!r}")
        if self.action == "edit" and not self.resource_id:
            raise UiDenied(f"{self.name}: an edit target is located by resource-id alone")
        if self.package == SHADE_PKG:
            if (self.action != "click" or self.text not in SHADE_ACTIONS
                    or self.anchor not in TIDEO_LABELS):
                raise UiDenied(f"{self.name}: the shade allows only {sorted(SHADE_ACTIONS)} "
                               f"on a notification anchored to {sorted(TIDEO_LABELS)}")
        elif self.package not in PACKAGES or self.anchor:
            raise UiDenied(f"{self.name}: package {self.package!r} not allowed")
        if denied_node(self.resource_id or "", self.text or "", self.description or ""):
            raise UiDenied(f"{self.name}: target is on the global UI denylist")


def denied_node(resource_id: str, text: str, description: str) -> bool:
    return bool(_DENY_IDS.fullmatch(resource_id)
                or _DENY_TEXT.fullmatch(text) or _DENY_TEXT.fullmatch(description))


class UiAllowlist:
    def __init__(self, *targets: Target):
        names = [t.name for t in targets]
        if len(set(names)) != len(names):
            raise UiDenied(f"duplicate target names in {names}")
        self._targets = {t.name: t for t in targets}

    def __getitem__(self, name: str) -> Target:
        if name not in self._targets:
            raise UiDenied(f"{name!r} is not in this scenario's UI allowlist")
        return self._targets[name]

    def has_shade(self) -> bool:
        return any(t.package == SHADE_PKG for t in self._targets.values())


# ── resolution (pure: hierarchy XML in, one node out) ───────────────────────────────────────

_BOUNDS = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")


@dataclass(frozen=True)
class Node:
    resource_id: str
    text: str
    description: str
    bounds: tuple[int, int, int, int]
    checked: bool = False
    inner: tuple[str, ...] = ()  # descendants' non-empty texts, in document order
    enabled: bool = True

    def centre(self) -> tuple[int, int]:
        l, t, r, b = self.bounds
        return (l + r) // 2, (t + b) // 2

    def label(self) -> str:
        """Its own text, else its one text-bearing descendant's: a tagged chip, card or row
        dumps empty with the label in a child. Two or more such descendants are ambiguous."""
        if self.text or not self.inner:
            return self.text
        if len(self.inner) > 1:
            raise UiDenied(f"{self.resource_id}: no text of its own and {len(self.inner)} in "
                           "its descendants")
        return self.inner[0]


def _node(e: ElementTree.Element) -> Node:
    m = _BOUNDS.fullmatch(e.get("bounds", ""))
    if not m:
        raise UiDenied(f"node without usable bounds: {e.attrib}")
    l, t, r, b = map(int, m.groups())
    if r <= l or b <= t:
        raise UiDenied(f"node with empty bounds: {e.attrib}")
    inner = tuple(d.get("text") for d in e.iter("node") if d is not e and d.get("text"))
    return Node(e.get("resource-id", ""), e.get("text", ""), e.get("content-desc", ""),
                (l, t, r, b), e.get("checked") == "true", inner, e.get("enabled") != "false")


def _matches(e: ElementTree.Element, t: Target) -> bool:
    return (e.get("package") == t.package
            and (t.resource_id is None or e.get("resource-id") == t.resource_id)
            and (t.text is None or e.get("text") == t.text)
            and (t.description is None or e.get("content-desc") == t.description))


def _candidates(root: ElementTree.Element, t: Target) -> list[ElementTree.Element]:
    if t.package == SHADE_PKG:
        rows = [r for r in root.iter("node") if r.get("resource-id") == NOTIFICATION_ROW_ID
                and any(n.get("resource-id") == APP_NAME_ID and n.get("text") == t.anchor
                        for n in r.iter("node"))]
        if len(rows) != 1:
            raise UiDenied(f"{t.name}: {len(rows)} notification rows carry {t.anchor!r}")
        return [e for e in rows[0].iter("node") if _matches(e, t)]
    return [e for e in root.iter("node") if _matches(e, t)]


def _own_nodes(row: ElementTree.Element):
    """A row's descendants, not descending into a nested row (a group's children)."""
    for child in row:
        if child.get("resource-id") == NOTIFICATION_ROW_ID:
            continue
        yield child
        yield from _own_nodes(child)


def _check_clean(root: ElementTree.Element, e: ElementTree.Element, name: str) -> None:
    # The tap may land on an enclosing clickable or a child, so the whole chain must be clean.
    parents = {child: parent for parent in root.iter() for child in parent}
    chain, p = [], e
    while p is not None:
        chain.append(p)
        p = parents.get(p)
    for n in chain + list(e.iter("node")):
        if denied_node(n.get("resource-id", ""), n.get("text", ""), n.get("content-desc", "")):
            raise UiDenied(f"{name}: resolved node is, contains or sits in a denied control")


def collapsed_rows(xml: str) -> list[tuple[str, Node]]:
    """(title, expand button) of each collapsed notification row titled as Tideo titles its own:
    no app-name header and no action of its own, one title, one expand button inside the row.
    A title proves nothing about the poster; the caller checks that (DD-033)."""
    root, out = ElementTree.fromstring(xml), []
    for r in root.iter("node"):
        if r.get("resource-id") != NOTIFICATION_ROW_ID:
            continue
        own = list(_own_nodes(r))
        ids = [n.get("resource-id") for n in own]
        titles = [n.get("text") for n in own if n.get("resource-id") == TITLE_ID]
        buttons = [n for n in own if n.get("resource-id") == EXPAND_ID
                   and n.get("package") == SHADE_PKG]
        if APP_NAME_ID in ids or ACTION_ID in ids or len(titles) != 1 \
                or titles[0] not in TIDEO_TITLES or len(buttons) != 1:
            continue
        button, row = _node(buttons[0]), _node(r)
        x, y = button.centre()
        (l, t, rr, b) = row.bounds
        if not (l <= x < rr and t <= y < b):
            continue
        _check_clean(root, buttons[0], "expand")
        out.append((titles[0], button))
    return out


_FOCUS = re.compile(r"mCurrentFocus=Window\{\S+ u\d+ ([^\s}]+)\}")
_PACKAGE = re.compile(r"[A-Za-z]\w*(\.\w+)+")


def focus_in_dump(dump: str) -> str | None:
    """`dumpsys window displays`' focused window as SHADE_PKG for the notification shade, the
    package of an app window, or None: none, several that disagree, or an unknown title."""
    lines = re.findall(r"mCurrentFocus=\S*", dump)
    titles = set(_FOCUS.findall(dump))
    if len(titles) != 1 or len(_FOCUS.findall(dump)) != len(lines):  # a null or odd one too
        return None
    title = titles.pop()
    if title == SHADE_WINDOW:
        return SHADE_PKG
    package = title.split("/")[0]
    # Another SystemUI window (a dialog, the volume panel) is not the shade.
    return package if _PACKAGE.fullmatch(package) and package != SHADE_PKG else None


def has_scrollable(xml: str, package: str) -> bool:
    return any(e.get("scrollable") == "true" and e.get("package") == package
               for e in ElementTree.fromstring(xml).iter("node"))


def count(xml: str, t: Target) -> int:
    """How many nodes `t` matches in this hierarchy (the shade row must still be unique)."""
    return len(_candidates(ElementTree.fromstring(xml), t))


def resolve(xml: str, t: Target) -> Node:
    """The one node `t` names in this hierarchy, or raise."""
    root = ElementTree.fromstring(xml)
    candidates = _candidates(root, t)
    if len(candidates) != 1:
        raise UiDenied(f"{t.name}: {len(candidates)} nodes match, need exactly one")
    _check_clean(root, candidates[0], t.name)
    node = _node(candidates[0])
    # A tap lands on whatever window is on top at that point: refuse when another package's
    # node (an overlay, the keyboard, a dialog) covers it. The shade is on top by construction.
    if t.package != SHADE_PKG:
        x, y = node.centre()
        for e in root.iter("node"):
            m = _BOUNDS.fullmatch(e.get("bounds", ""))
            if e.get("package") != t.package and m:
                l, top, r, b = map(int, m.groups())
                if l <= x < r and top <= y < b:
                    raise UiDenied(f"{t.name}: {e.get('package')!r} covers its tap point")
    return node


# ── the uiautomator2 RPC gate ───────────────────────────────────────────────────────────────

READ_RPCS = frozenset({"dumpWindowHierarchy", "deviceInfo", "objInfo", "count", "getText",
                       "waitForExists", "waitUntilGone"})
# One authorisation each, consumed by the call it admits: .click (x, y); .text ((resource-id,
# package), text); .scroll (RPC method, package); .shade bool.
_pending = threading.local()


def _take(name: str):
    value = getattr(_pending, name, None)
    setattr(_pending, name, None)
    return value


def _selector_id(sel) -> tuple[str | None, str | None] | None:
    """A selector's (resourceId, packageName); every selector the gate admits names both."""
    return (sel.get("resourceId"), sel.get("packageName")) if isinstance(sel, dict) else None


def _admit_rpc(method: str, params) -> None:
    if method in READ_RPCS:
        return
    params = list(params or ())
    if method == "click" and getattr(_pending, "click", None) is not None:
        if tuple(params) == _pending.click:
            _take("click")
            return
    if method == "setText" and getattr(_pending, "text", None) is not None and len(params) == 2:
        if (_selector_id(params[0]), params[1]) == _pending.text:
            _take("text")
            return
    if method in ("scrollForward", "scrollBackward") and len(params) == 3 \
            and params[1] is True and isinstance(params[0], dict) \
            and params[0].get("scrollable") is True \
            and (method, params[0].get("packageName")) == getattr(_pending, "scroll", None):
        _take("scroll")
        return
    if method == "openNotification" and _take("shade"):
        return
    raise UiDenied(f"uiautomator2 RPC {method!r} not authorised")


_orig_http_request = core._http_request


def _guarded_http_request(dev, device_port, method, path, data=None, **kwargs):
    if (method, path) == ("POST", "/jsonrpc/0"):
        _admit_rpc((data or {}).get("method", ""), (data or {}).get("params"))
    elif (method, path) != ("GET", "/ping"):
        raise UiDenied(f"uiautomator2 HTTP {method} {path} not allowlisted")
    return _orig_http_request(dev, device_port, method, path, data, **kwargs)


core._http_request = _guarded_http_request


def _refuse(name: str):
    def refused(*_args, **_kwargs):
        raise UiDenied(f"uiautomator2 {name}() is disabled by the UI boundary")
    refused.__name__ = name
    return refused


DISABLED_U2_METHODS = (
    "app_clear", "app_uninstall", "app_uninstall_all", "app_install", "app_stop_all",
    "app_auto_grant_permissions", "set_fastinput_ime", "set_input_ime", "send_keys",
    "clear_text", "show_touch_trace", "open_url", "push", "shell",
)
for _name in DISABLED_U2_METHODS:
    setattr(uiautomator2.Device, _name, _refuse(_name))


# ── the harness's UI handle ─────────────────────────────────────────────────────────────────


class Ui:
    """The only way scenario code touches the screen."""

    def __init__(self, s: device.Session, d: uiautomator2.Device, allowlist: UiAllowlist):
        self._s, self._d, self._allow = s, d, allowlist

    def focus(self) -> str | None:
        """Who holds input focus: SHADE_PKG, a package, or None when it cannot be told."""
        return focus_in_dump(self._s.device.shell(["dumpsys", "window", "displays"]))

    def _foreground(self, t: Target) -> None:
        # adbutils' app_current() reads an open shade as the activity under it (DD-033).
        current = self.focus()
        if current != t.package:
            raise UiDenied(f"{t.name}: focus is {current!r}, not {t.package!r}")

    def _swipe(self, package: str, forward: bool) -> None:
        _pending.scroll = ("scrollForward" if forward else "scrollBackward", package)
        try:
            scroll = self._d(scrollable=True, packageName=package).scroll.vert
            (scroll.forward if forward else scroll.backward)()
        except uiautomator2.exceptions.RPCError:
            pass
        finally:
            _pending.scroll = None

    def _dump(self, t: Target, scroll: bool = True) -> str:
        # Foreground first, then a fresh dump, then the action: the shortest window for the
        # screen to change. resolve() refuses a covered tap point in that dump; a window that
        # appears between the dump and the tap is the accepted residue (DD-021).
        self._foreground(t)
        xml = self._d.dump_hierarchy()
        if not scroll or t.package == SHADE_PKG or not t.resource_id:
            return xml
        # UiScrollable's scrollIntoView stopped after one swipe on Compose: forward, then back,
        # one swipe at a time until the target shows or the dump stops changing (DD-033).
        # Only the package's own scrollable, seen in this dump: one hidden behind another
        # window made each swipe wait out uiautomator2's selector timeout.
        for forward in (True, False):
            for _ in range(MAX_SWIPES):
                if count(xml, t) or not has_scrollable(xml, t.package):
                    return xml
                self._swipe(t.package, forward)
                self._foreground(t)
                before, xml = xml, self._d.dump_hierarchy()
                if xml == before:
                    break
        return xml

    def _locate(self, name: str, action: str) -> tuple[Target, Node]:
        t = self._allow[name]
        if t.action != action:
            raise UiDenied(f"{name} is declared for {t.action}, not {action}")
        return t, resolve(self._dump(t), t)

    def exists(self, name: str) -> bool:
        """Whether the read target is on screen (scrolled to if needed): one node, or none."""
        t = self._allow[name]
        if t.action != "read":
            raise UiDenied(f"{name} is declared for {t.action}, not read")
        xml = self._dump(t)
        if count(xml, t) == 0:
            return False
        resolve(xml, t)  # exactly one, and clean
        return True

    def read(self, name: str) -> str:
        return self._locate(name, "read")[1].label()

    def read_if_shown(self, name: str) -> str | None:
        """One dump: the label, or None when not shown (for a message gone in seconds)."""
        t = self._allow[name]
        if t.action != "read":
            raise UiDenied(f"{name} is declared for {t.action}, not read")
        xml = self._dump(t, scroll=False)  # an overlay: no swipe brings it into view
        return resolve(xml, t).label() if count(xml, t) else None

    def checked(self, name: str) -> bool:
        return self._locate(name, "read")[1].checked

    def enabled(self, name: str) -> bool:
        return self._locate(name, "read")[1].enabled

    def click(self, name: str) -> None:
        _, node = self._locate(name, "click")
        _pending.click = node.centre()
        try:
            self._d.click(*node.centre())
        finally:
            _pending.click = None

    def set_text(self, name: str, text: str) -> None:
        """Replace an edit target's text through accessibility (ACTION_SET_TEXT): no IME."""
        t, _ = self._locate(name, "edit")
        _pending.text = ((t.resource_id, t.package), text)
        try:
            self._d(resourceId=t.resource_id, packageName=t.package).set_text(text)
        finally:
            _pending.text = None

    def open_shade(self) -> None:
        if not self._allow.has_shade():
            raise UiDenied("this scenario declares no notification-shade target")
        _pending.shade = True
        try:
            self._d.open_notification()
        finally:
            _pending.shade = False

    def require_shade(self) -> None:
        if not self._allow.has_shade():
            raise UiDenied("this scenario declares no notification-shade target")
        if (current := self.focus()) != SHADE_PKG:
            raise UiDenied(f"shade: focus is {current!r}, not the shade")

    def expand_own_row(self, owner: Callable[[str], frozenset[str]]) -> bool:
        """Expand Tideo's collapsed row, if the open shade shows one: a single tap, called once
        per opening, so it never collapses the row again. `owner(title)` names the packages
        that posted a notification so titled; only Tideo's alone qualifies, on exactly one
        row (DD-033). Raises until the shade has focus."""
        self.require_shade()
        rows =[b for title, b in collapsed_rows(self._d.dump_hierarchy())
                if owner(title) == {DEBUG_PKG}]
        if len(rows) > 1:
            raise UiDenied(f"expand: {len(rows)} collapsed rows are Tideo's")
        if not rows:
            return False
        _pending.click = rows[0].centre()
        try:
            self._d.click(*rows[0].centre())
        finally:
            _pending.click = None
        return True

    def close_shade(self) -> None:
        """Collapse the shade by command, not Back, which would navigate an app if the shade
        closed first; then wait until it has gone. Unknown focus refuses."""
        if not self._allow.has_shade():
            raise UiDenied("this scenario declares no notification-shade target")
        for _ in range(SHADE_POLLS):
            current = self.focus()
            if current is None:
                raise UiDenied("close: focus cannot be told, so neither can the shade")
            if current != SHADE_PKG:
                return
            self._s.device.shell(["cmd", "statusbar", "collapse"])
            time.sleep(SHADE_POLL_S)
        raise UiDenied("close: the shade still has focus")
