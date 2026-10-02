"""UI boundary: act only through a scenario's declared allowlist, independent of the command one.

An allowed tap can still trigger destructive app behaviour, so the harness never touches the UI
except by naming a `Target` in its scenario's `UiAllowlist`. Each action dumps the hierarchy,
resolves the target to exactly one node of the declared package, checks the foreground, checks
the node against a global denylist, and only then taps the centre of that node or sets its text.
Test code never supplies coordinates. A target off screen is first scrolled into view by
selector (Compose leaves nodes outside the viewport out of the dump).

Importing this module also gates uiautomator2 at its one HTTP chokepoint (`core._http_request`):
read-only RPCs pass; `click`, `setText`, `scrollTo`, `openNotification` and `pressKey` "back"
pass only for the one call `Ui` has just authorised; every other RPC is refused. Its
package/IME-mutating conveniences are replaced so they raise before reaching the gate.
"""

from __future__ import annotations

import re
import threading
from dataclasses import dataclass
from typing import Literal
from xml.etree import ElementTree

import uiautomator2
from uiautomator2 import core

from . import device
from .device import BoundaryViolation, PACKAGES

SHADE_PKG = "com.android.systemui"
# AOSP's row id (status_bar_notification_row.xml); S6's smoke run confirms it on the owner's OEM.
NOTIFICATION_ROW_ID = f"{SHADE_PKG}:id/expandableNotificationRow"
# The named actions on Tideo's own notifications (plan §3.2). Discard forgets the curve point an
# override pause recorded (DD-011), so a scenario that causes one leaves the owner's data as found.
SHADE_ACTIONS = frozenset({"Resume", "Reset", "Discard"})
# The app-name header of Tideo's notification row: appLabel for release and debug. Only the
# header node counts, never the same text in another app's title or body (S2–S4 review).
TIDEO_LABELS = frozenset({"Tideo Auto Brightness", "Tideo AB (Debug)"})
APP_NAME_ID = "android:id/app_name_text"

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

    def centre(self) -> tuple[int, int]:
        l, t, r, b = self.bounds
        return (l + r) // 2, (t + b) // 2


def _node(e: ElementTree.Element) -> Node:
    m = _BOUNDS.fullmatch(e.get("bounds", ""))
    if not m:
        raise UiDenied(f"node without usable bounds: {e.attrib}")
    l, t, r, b = map(int, m.groups())
    if r <= l or b <= t:
        raise UiDenied(f"node with empty bounds: {e.attrib}")
    return Node(e.get("resource-id", ""), e.get("text", ""), e.get("content-desc", ""),
                (l, t, r, b), e.get("checked") == "true")


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


def count(xml: str, t: Target) -> int:
    """How many nodes `t` matches in this hierarchy (the shade row must still be unique)."""
    return len(_candidates(ElementTree.fromstring(xml), t))


def resolve(xml: str, t: Target) -> Node:
    """The one node `t` names in this hierarchy, or raise."""
    root = ElementTree.fromstring(xml)
    candidates = _candidates(root, t)
    if len(candidates) != 1:
        raise UiDenied(f"{t.name}: {len(candidates)} nodes match, need exactly one")
    # The tap may land on an enclosing clickable or a child, so the whole chain must be clean.
    parents = {child: parent for parent in root.iter() for child in parent}
    chain, e = [], candidates[0]
    while e is not None:
        chain.append(e)
        e = parents.get(e)
    for e in chain + list(candidates[0].iter("node")):
        if denied_node(e.get("resource-id", ""), e.get("text", ""), e.get("content-desc", "")):
            raise UiDenied(f"{t.name}: resolved node is, contains or sits in a denied control")
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
# package), text); .scroll (resource-id, package); .shade and .back bool.
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
    if method == "scrollTo" and getattr(_pending, "scroll", None) is not None \
            and len(params) == 3 and params[2] is True and isinstance(params[0], dict) \
            and params[0].get("scrollable") is True \
            and params[0].get("packageName") == _pending.scroll[1] \
            and _selector_id(params[1]) == _pending.scroll:
        _take("scroll")
        return
    if method == "openNotification" and _take("shade"):
        return
    if method == "pressKey" and params == ["back"] and _take("back"):
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

    def _foreground(self, t: Target) -> None:
        if t.package != SHADE_PKG:
            current = self._s.device.app_current().package
            if current != t.package:
                raise UiDenied(f"{t.name}: foreground is {current!r}, not {t.package!r}")

    def _scroll_to(self, t: Target) -> None:
        """Scroll the screen's scrollable until `t` is in view, by selector; never a swipe by
        coordinates. Nothing to scroll, or no such node, leaves the screen as it was."""
        if t.package == SHADE_PKG or not t.resource_id:
            return
        _pending.scroll = (t.resource_id, t.package)
        try:
            self._d(scrollable=True, packageName=t.package).scroll.vert.to(
                resourceId=t.resource_id, packageName=t.package)
        except uiautomator2.exceptions.RPCError:
            pass
        finally:
            _pending.scroll = None

    def _dump(self, t: Target) -> str:
        # Foreground first, then a fresh dump, then the action: the shortest window for the
        # screen to change. resolve() refuses a covered tap point in that dump; a window that
        # appears between the dump and the tap is the accepted residue (DD-021).
        self._foreground(t)
        xml = self._d.dump_hierarchy()
        if count(xml, t) == 0 and t.package != SHADE_PKG and t.resource_id:
            self._scroll_to(t)
            self._foreground(t)
            xml = self._d.dump_hierarchy()
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
        return self._locate(name, "read")[1].text

    def checked(self, name: str) -> bool:
        return self._locate(name, "read")[1].checked

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

    def close_shade(self) -> None:
        """Back, which collapses the shade; only with SystemUI in front, never an app screen."""
        if not self._allow.has_shade():
            raise UiDenied("this scenario declares no notification-shade target")
        current = self._s.device.app_current().package
        if current != SHADE_PKG:
            raise UiDenied(f"back with {current!r} in front would navigate it, not the shade")
        _pending.back = True
        try:
            self._d.press("back")
        finally:
            _pending.back = False
