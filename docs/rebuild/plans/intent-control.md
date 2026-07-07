# Plan — Intent control for automation frameworks (Tasker / MacroDroid)

> **Persisted execution plan** (RUNBOOK playbook 5, multi-session pattern; precedent D-149–D-152).
> Provisional — the owner may pivot mid-feature. **This file dies at the final unit**: its durable
> content must by then live in `STATE.md` Changelog lines + the `D-157` ledger row. Code comments
> cite `D-157`, never this file.

Branch: `claude/tideo-brightness-intent-control-2rkhx3` · Target release: **1.8.0 / versionCode 18**
(main has v1.7.0/vc17 tagged; `release-preflight` requires vc > 17).

## Why

The app's control verbs — service on/off, pause/resume, reapply, panic, profile load — are reachable
only from its own UI (QS tile, widget, notification). Automation apps (Tasker, MacroDroid) have no
way in. This adds a **deliberate, opt-in** external-control surface: an exported broadcast receiver
with a stable public action namespace, gated by a runtime setting that defaults OFF; plus (final
unit) outbound state-change broadcasts so automation can *react* to Tideo, not just command it.

Everything lives in `:app`. **The golden-tested `:domain` engine and the `:platform` adapters are
NOT touched by any unit** — if a unit seems to need them, STOP and ask the owner.

## Decisions (owner-approved defaults)

1. **Guard = opt-in runtime toggle only** (default OFF, the D-105 `geoIpEnabled` pattern). No shared
   secret: the exposed verbs are exactly what the notification/tile already give the user, no data
   leaves the app, and a token is real friction in the Tasker/MacroDroid UIs.
2. **Verbs = core + profiles**: `SERVICE_ON/OFF/TOGGLE`, `PAUSE`, `RESUME`, `REAPPLY`, `PANIC`,
   `LOAD_PROFILE` (+`name` extra), `CONTEXTS_RESUME`. **No numeric setters** — those open a
   validation surface onto the curve math (the D-146 NaN class); profiles carry parameter sets safely.
3. **Outbound state events: yes**, as the final, independently droppable unit (U5).
4. **Version 1.8.0 / vc18** (new minor: new user-facing feature, RUNBOOK §6).

## Design (shared by all units)

**Public action namespace** — new, distinct from the internal `…runtime.action.*` so internals stay
free to change:

```
com.tideo.autobrightness.control.SERVICE_ON | SERVICE_OFF | SERVICE_TOGGLE
com.tideo.autobrightness.control.PAUSE | RESUME | REAPPLY | PANIC
com.tideo.autobrightness.control.LOAD_PROFILE      (String extra "name")
com.tideo.autobrightness.control.CONTEXTS_RESUME
outbound: com.tideo.autobrightness.event.STATE_CHANGED
          (extras: enabled/running/paused Boolean, profile String?)
```

**Every verb maps onto an existing, already-hardened path:**

| Verb | Existing path reused |
|---|---|
| SERVICE_ON/OFF/TOGGLE | the `settingsDataStore.updateData { copy(serviceEnabled=…) }` + `AutoBrightnessRuntime.onSettingChanged` + widget `pushUpdate` dance — copy `WidgetActionReceiver.toggle` (`app/.../widget/WidgetActionReceiver.kt:40`), parameterized by target state. Do NOT refactor the tile/widget to share it — keep their working code untouched. |
| PAUSE / RESUME / REAPPLY | `AutoBrightnessRuntime.pause/resume/reapply` (`app/.../runtime/AutoBrightnessRuntime.kt:30-41`); service-side D-140 zombie gates already handle "sent while not running". |
| PANIC | new 1-line `AutoBrightnessRuntime.panic(context)` mirroring `pause()`, sending the existing `AmbientMonitoringService.ACTION_PANIC` (same intent the notification Reset button sends; service branch `AmbientMonitoringService.kt:167` self-terminates). |
| LOAD_PROFILE / CONTEXTS_RESUME | logic extracted from `SettingsViewModel.applyProfile` / `resumeContextAutomation` (`app/.../state/SettingsViewModel.kt:103,177`) into a new VM-free `ProfileApplier` (U3). |
| Opt-in gate storage | new tiny `ControlPrefsStore` mirroring `ExperimentPrefsStore.geoIpEnabled` (`app/.../settings/ExperimentPrefsStore.kt:48-53`) + one `preferencesDataStore` line in `app/.../storage/AppDataStores.kt`. Deliberately **NOT** an `AabSettings` field — profile apply/import chokepoints (`applyProfile`, `replaceAll`, `resetDefaults`, legacy import) then can never flip it, and no schema/clamp/drift-test churn. |

**Platform caveat (encode in code comment + help text):** `SERVICE_ON` arriving while the app is
background-restricted may throw `ForegroundServiceStartNotAllowedException` (API 31+ FGS launch
rules). `AutoBrightnessRuntime.startMonitoring` already catches it and marks
`ServiceHealthStore.markDegraded` (surfaced on the Dashboard). Help text tells users to exempt Tideo
from battery optimization for reliable external enable. All other verbs are safe: while the FGS runs
the app is not "background", and when it doesn't, D-140 gates make them no-ops.

**Security posture (goes into the D-157 row):** this deliberately re-opens the class of surface D-147
closed — but opt-in (default OFF), runtime-gated as the receiver's FIRST check, with the
OFF-ignores-everything property pinned by a D-147-style negative test.

## Units (order binding: U0 → U6; U5 droppable)

Each unit ends at the checkpoint (Execution protocol below).

- **U0 — sync base + persist plan (docs-only).** Fast-forward branch onto `origin/main`; write this
  file; add the STATE Active-work checklist; correct STATE "Current state" (1.7.0 shipped/tagged).
  Acceptance: docs coherence + `:app:lintDebug` proves the tree is untouched.
- **U1 — version bump + opt-in pref store.** `app/build.gradle.kts` vc18 / 1.8.0;
  `changelogs/18.txt` (< 500 chars, PR #90 rule). New `control/ControlPrefsStore.kt`
  (`externalControlEnabled` default false) + `controlPrefsDataStore` in `AppDataStores.kt`.
  Tests: new `ControlPrefsStoreTest` (`defaultsToDisabled`, `enableRoundTrips`).
- **U2 — exported control receiver, core verbs (glue-review).** New `control/ControlReceiver.kt`
  handling the 7 core verbs; gate is the FIRST check (`goAsync { if (!enabled) return }`). Manifest:
  register `exported="true"` + intent-filter + D-157 comment. `AutoBrightnessRuntime.panic`.
  Ledger row **D-157**. Tests `ControlReceiverTest`: `controlDisabled_ignoresAllActions_D157`
  (security property), per-verb routing, `unknownAction_ignored`. Glue-review pass.
- **U3 — ProfileApplier extraction + profile verbs (glue-review).** New `settings/ProfileApplier.kt`
  (VM-free) with `applyProfile`/`resumeContextAutomation` bodies moved verbatim; `SettingsViewModel`
  delegates. Receiver gains `LOAD_PROFILE` + `CONTEXTS_RESUME`. **`SettingsViewModelTest` must pass
  UNMODIFIED** (equivalence check). Tests: `ProfileApplierTest`, receiver profile cases. Glue-review.
- **U4 — UI toggle + in-app help.** `ToolsScreen.kt` "Automation control" `AabCard`: Switch bound to
  `ControlPrefsStore` (wire like geo-IP, `CircadianExtrasViewModel`), + "Show actions" dialog (verb
  strings, `name` extra, one `adb shell am broadcast` example, battery-optimization note). All strings
  in `strings.xml` (`HardcodedStringCheckTest` ratchet 0). Update `screen_map.md`.
- **U5 — outbound state events (optional, droppable; glue-review). DONE.** Publisher job from
  `ensureRunning()`, gated by `externalControlEnabled`, distinct-until-changed `sendBroadcast` of
  `event.STATE_CHANGED` (extras `enabled`/`running`/`paused`/`profile`). Landed the final off-state in
  `onDestroy` (BEFORE `scope.cancel()`, D-139-class ordering) rather than only disable/panic teardown —
  it is the single exit common to SERVICE_OFF-toggle/Disable/Panic. `AmbientMonitoringServiceTest` +5.
- **U6 — docs, release polish, plan retirement.** A **new user-facing reference doc**
  `docs/AUTOMATION.md` (the single source of truth for the exposed surface: the opt-in toggle, every
  `com.tideo.autobrightness.control.*` action, the `name` extra, **the outbound `event.STATE_CHANGED`
  event and its extras (U5)**, `adb`/Tasker/MacroDroid usage examples, the SERVICE_ON battery-optimization
  caveat — a superset of the in-app "Show actions" dialog, which lists only the inbound verbs) + a **link to it from `README.md`** under an "Automation (Tasker / MacroDroid)"
  heading (owner ask: README must link out to a doc explaining what intents are exposed and how to use
  them — the README itself stays a pointer, not the recipe list); `DEVICE_TEST_SCRIPT.md` §12 owner
  matrix; finalize `changelogs/18.txt`; `datastore_map.md` `control_prefs` row. **Delete this file.**
  Final `STATE.md` update (collapse Active work → Changelog lines). No `PARITY_CHECKLIST.md` row
  (Tasker-independent feature).

## Execution protocol (binding, every unit)

1. Strictly sequential. **No subagents, no parallel tool fan-out for build/test steps.**
2. Full acceptance ladder green — never leave the branch red:
   `./gradlew :domain:test :platform:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`.
   `:domain`/`:platform` must pass **with zero diffs in those modules** — the no-blast-radius tripwire.
3. Glue-review protocol (RUNBOOK) on U2/U3/U5; result stated in the commit body.
4. Update `STATE.md` (flip the unit's checkbox; last unit writes the Changelog line).
5. Commit (cite D-157; never the literal skip-ci token — D-115) and
   `git push -u origin claude/tideo-brightness-intent-control-2rkhx3` (retry 4× / 2-4-8-16 s on
   network errors only). Never force-push, never touch `main`.
6. Anything requiring `domain/`, `platform/`, or a golden vector → STOP; owner-only scope change.

## Out of scope

No `:domain`/`:platform` edits; no golden churn; no tile/widget/notification refactor "while here";
no numeric-setter verbs; no Tasker plugin-library dependency (plain intents cover both frameworks);
no new manifest permissions.
