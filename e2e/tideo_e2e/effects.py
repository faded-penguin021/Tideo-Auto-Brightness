"""The effect inventory: what each effect kind may write, directly or through Tideo.

Preflight's SKIP rules, the journal's watch list and the execution order all derive from this one
table, so a scenario's declared `effects` decide what is snapshotted, journaled and restored.
"""

from __future__ import annotations

from dataclasses import dataclass, field

from .device import SETTINGS
from .scenarios import EFFECTS

Key = tuple[str, str]  # (settings namespace, key)

BRIGHTNESS: frozenset[Key] = frozenset({
    ("system", "screen_brightness"), ("system", "screen_brightness_mode"),
})
# SecureDisplayController: the Privileged Display toggles, their teardown baseline and PANIC's
# defaults. night_display_auto_mode is only read by Tideo, so it is snapshotted, never watched.
DISPLAY_TOGGLES: frozenset[Key] = frozenset({
    ("secure", "night_display_activated"), ("secure", "night_display_color_temperature"),
    ("secure", "accessibility_display_daltonizer"),
    ("secure", "accessibility_display_daltonizer_enabled"),
    ("secure", "accessibility_display_inversion_enabled"), ("secure", "doze_always_on"),
    ("global", "stay_on_while_plugged_in"), ("global", "user_disabled_hdr_formats"),
    ("global", "are_user_disabled_hdr_formats_allowed"),
})
EXTRA_DIM: frozenset[Key] = frozenset({
    ("secure", "reduce_bright_colors_level"), ("secure", "reduce_bright_colors_activated"),
})
EVERY_TIDEO_KEY = BRIGHTNESS | DISPLAY_TOGGLES | EXTRA_DIM

# Read-only at preflight and per scenario: every settings key the boundary can restore.
SNAPSHOT_KEYS: frozenset[Key] = frozenset(
    (ns, key) for ns, keys in SETTINGS.items() for key, value in keys.items() if value is not None
)

# Execution stages, in the plan's order: settings-only → service/wake → grant →
# revoke/force-stop → PANIC. A scenario runs in the stage of its latest effect.
READ, SETTINGS_ONLY, SERVICE, GRANT, KILL, PANIC = range(6)


@dataclass(frozen=True)
class Footprint:
    settings: frozenset[Key] = frozenset()
    grant: bool = False          # WRITE_SECURE_SETTINGS on the debug package
    prefs: bool = False          # Tideo preferences changed through the UI
    runtime: bool = False        # service on/off or paused state
    private: bool = False        # Tideo's private logical state (DataStore, baseline, savedMode)
    starts_service: bool = False
    reevaluates_contexts: bool = False
    automation_events: bool = False
    wakes_locked: bool = False   # the phone wakes to a keyguard only the owner can clear
    stage: int = READ

    def __or__(self, other: Footprint) -> Footprint:
        return Footprint(
            settings=self.settings | other.settings,
            **{f: getattr(self, f) or getattr(other, f) for f in _FLAGS},
            stage=max(self.stage, other.stage),
        )


_FLAGS = ("grant", "prefs", "runtime", "private", "starts_service", "reevaluates_contexts",
          "automation_events", "wakes_locked")

# Start stores and stop removes saved_brightness_mode; start applies and teardown reapplies the
# display baseline; a running pipeline writes brightness and may engage Extra Dim. Anything that
# may (re)start the service — a verb, a Resume on a fresh process, a relaunch after process
# death with serviceEnabled persisted — carries this whole footprint.
_SERVICE = Footprint(settings=EVERY_TIDEO_KEY, runtime=True, private=True, starts_service=True,
                     reevaluates_contexts=True, stage=SERVICE)

INVENTORY: dict[str, Footprint] = {
    "read_only": Footprint(),
    "ui_nav": Footprint(),
    # With the service running, a preference change reaches the pipeline and display toggles,
    # so recovery must quiesce it before restoring (runtime) and restart it after.
    "prefs_ui": Footprint(settings=EVERY_TIDEO_KEY, prefs=True, private=True, runtime=True,
                          stage=SETTINGS_ONLY),
    # The harness's own brightness injection (DC-012 override path).
    "settings_write": Footprint(settings=BRIGHTNESS, stage=SETTINGS_ONLY),
    # Apply also stores the toggles in Tideo's settings, which a UI restorer puts back.
    "privileged_apply": Footprint(settings=DISPLAY_TOGGLES, prefs=True, private=True,
                                  runtime=True, stage=SETTINGS_ONLY),
    "service_toggle": _SERVICE,
    # Screen on clears contextOverride and re-evaluates contexts, which write whole profiles.
    "screen_wake": Footprint(settings=EVERY_TIDEO_KEY, private=True, reevaluates_contexts=True,
                             wakes_locked=True, stage=SERVICE),
    "override_record": Footprint(private=True, runtime=True, stage=SERVICE),
    "notification_action": _SERVICE,
    "broadcast": _SERVICE,
    "automation_events": Footprint(automation_events=True, stage=SERVICE),
    # Restoring an original revoked grant kills the process, so recovery must quiesce and
    # restart the service around it, as for a revoke.
    "grant": _SERVICE | Footprint(grant=True, stage=GRANT),
    "revoke": _SERVICE | Footprint(grant=True, stage=KILL),
    "force_stop": _SERVICE | Footprint(stage=KILL),
    # Persists serviceEnabled=false, writes every display toggle to its default, zeroes Extra Dim.
    "panic": _SERVICE | Footprint(stage=PANIC),
}


def footprint(effects: tuple[str, ...] | list[str]) -> Footprint:
    out = Footprint()
    for kind in effects:
        if kind not in INVENTORY:
            raise KeyError(f"undeclared effect kind {kind!r}")
        out |= INVENTORY[kind]
    return out


def run_order(scenarios) -> list:
    """Scenarios sorted by stage, then id: read-only first, PANIC last."""
    return sorted(scenarios, key=lambda s: (footprint(s.effects).stage, s.id))


@dataclass(frozen=True)
class DeviceFacts:
    """What preflight observed, plus what the owner confirmed for this run."""

    absent_rows: frozenset[Key] = frozenset()
    unrestorable_rows: frozenset[Key] = frozenset()  # held values the put template refuses
    context_state: bool = False     # enabled context rules, contextOverride or a baseline set
    automation_on: bool = False
    force_dark_opt_in: bool = False
    confirmed: frozenset[str] = field(default_factory=frozenset)  # contexts, automation, unlock


def skip_reason(effects, facts: DeviceFacts) -> str | None:
    """The SKIP rule this scenario trips on this device, or None."""
    fp = footprint(effects)
    # Any write to an absent row creates it, and only `settings delete` could undo that.
    if absent := sorted(fp.settings & facts.absent_rows):
        return f"may create absent rows {absent}; restoring them needs settings delete"
    # A journaled original the boundary would refuse to write back leaves a stuck journal.
    if held := sorted(fp.settings & facts.unrestorable_rows):
        return f"rows {held} hold values the boundary cannot write back"
    # Recovery stops and restarts a service the run found running, so any runtime footprint
    # may start it (S2–S4 review), whatever the scenario itself does.
    starts = fp.starts_service or fp.runtime
    if (starts or fp.reevaluates_contexts) and facts.context_state \
            and "contexts" not in facts.confirmed:
        return "context rules, contextOverride or a baseline are set; owner has not confirmed"
    # With automation on, every runtime transition is announced.
    touches_automation = fp.automation_events or (fp.runtime and facts.automation_on)
    if touches_automation and "automation" not in facts.confirmed:
        return "STATE_CHANGED broadcasts; owner has not confirmed no receiver acts on them"
    # The owner unlocks by fingerprint after every wake (owner, 2026-10-06).
    if fp.wakes_locked and "unlock" not in facts.confirmed:
        return "wakes the screen locked; the owner must be at the phone to unlock it"
    if starts and facts.force_dark_opt_in:
        return "force-dark opt-in is on; a service start would set debug.hwui.force_dark"
    return None
