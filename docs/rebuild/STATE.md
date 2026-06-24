# STATE — project state & session memory

> **Length guard (read before editing).** Steady-state target ≤ 12 KB. **If this file exceeds
> 32 KB, aggressively compress before committing:** collapse each completed *Active work* stage
> into one Changelog line, move any durable gotcha into `DEVIATIONS_LEDGER.md` (the permanent,
> append-only registry — never compressed), and delete narrative/punch-list prose. The
> **Project** and **Current state** sections must always survive compression. The full migration
> narrative is already frozen in `../history/` — do not re-accumulate it here.

## Project

Native **Kotlin/Compose** Android app that is a feature-parity rebuild of the Tasker project
`Advanced_Auto_Brightness_V3.3`. Three modules: **`:domain`** (pure-JVM math/decision logic,
golden-tested), **`:platform`** (Android system adapters behind small interfaces), **`:app`**
(Compose M3 UI, DataStore `AabSettings`, foreground-service runtime, QS tile, boot receiver).
Privilege tiers: **BASIC** = user-grantable `WRITE_SETTINGS` (full core pipeline); **ELEVATED**
= `WRITE_SECURE_SETTINGS` via one-time `pm grant` (super dimming). minSdk 31, target/compile 35.

## Current state

**Shipped: v1.0.0** (`versionCode 3`), migration complete — all three on-device human gates
passed (Gate 1 core loop, Gate 2 surfaces & tiers, Gate 3 acceptance soak, signed off
2026-06-23). `PARITY_CHECKLIST.md` is zero-`pending`; golden parity tests green; TODO/FIXME = 0.
No active work in flight.

How changes are made now: see `RUNBOOK.md` (change-type playbooks). The migration narrative
(segment briefs, gate findings) is frozen in `../history/`; the deviations registry stays live.

> Code/docs elsewhere cite deviations by number (e.g. `STATE.md D-048`, `F50`). All deviations
> — migration and ongoing — live in the permanent registry `DEVIATIONS_LEDGER.md` (gate
> findings are in `../history/STATE_rebuild.md`). Look there.

## Active work

*(none — base state. Populate this section only while a change needs multiple stages; clear it
to a Changelog line on completion.)*

When in use, track stages here:

| Stage | Date | Status | Notes |
|---|---|---|---|

**Blockers:** none.

**New deviations (this work):** none.

> Write new deviations straight into the permanent registry `DEVIATIONS_LEDGER.md` (its
> "Maintenance deviations" section), not here — this slot is only a transient staging note
> during an in-flight change. Numbering is **one continuous sequence**: next free number is
> **D-100** (historical high-water mark D-099); never restart at D-001. A deviation, once
> numbered, lives in the registry forever — it is never compressed out.

## Changelog

One line per shipped change (newest first). Keep terse.

- 2026-06-24 — 1.0.3 / `versionCode 5`: (D-098) rule-editor Save/Cancel STILL clipped after D-097 and a
  follow-up that drove the dialog `Window` edge-to-edge — this Compose `Dialog` never delivers a bottom
  (nav-bar/ime) inset to its content, only the top. Stopped fighting insets: dropped the sticky bottom bar,
  Save/Cancel now ride at the end of the editor's scroll with a trailing `Spacer(48dp)` so they always
  scroll clear of the gesture pill (top still uses `statusBarsPadding()`; `imePadding()` shrinks the
  viewport for the keyboard). Tests updated to `performScrollTo()` the Save button. (D-099) in-app version
  had drifted behind the `v1.0.2` tag (build said 1.0.1/4) — realigned to 1.0.3/5, added `changelogs/5.txt`,
  and added RUNBOOK §6 "Cutting a release / version bump". UI + version only; bottom-inset behavior is owner
  device-verified (not Robolectric-testable).
- 2026-06-24 — Wi-Fi context fixes: (D-096) `WifiInfoReader.ssidFlow()` now runs the no-Location
  strategies (Shizuku→dumpsys) first like task43's `bypass_ssid`, so Wi-Fi context rules match with
  Location services OFF (was Location-only at eval time, even though the rule editor already read the
  SSID via Shizuku). (D-097) per-rule editor `Dialog` made edge-to-edge (`decorFitsSystemWindows =
  false`) so its status/nav-bar insets apply — the Save/Cancel bar no longer sits clipped under the
  gesture nav bar. Tests: WifiSsidStrategyTest +1 (flow uses no-Location strategy).
- 2026-06-24 — F-Droid: bumped to 1.0.1 / `versionCode 4` (packaging only — gives a release tag
  that contains `fastlane/`, which the 1.0.0 tag predated; F-Droid reads metadata from the built
  commit). Added `changelogs/4.txt`. No app behaviour change. Owner tags `v1.0.1` after merge.
- 2026-06-24 — F-Droid prep: added `fastlane/metadata/android/en-US/` (title, short/full
  description, `changelogs/3.txt`, 4 phoneScreenshots). Repo-side only; submission to fdroiddata
  + release tag are owner steps. No code/build change.
- 2026-06-23 — v1.0.0: Tasker→Kotlin rebuild complete; Gate 3 signed off. Full history frozen
  in `../history/`.
