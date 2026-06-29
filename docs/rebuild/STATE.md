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

## Changelog

One line per shipped change (newest first). Keep terse.

- 2026-06-28 — 1.4.0 / `versionCode 12` (MINOR — observable user-facing behaviour): **D-117** Curve &
  Brightness graph "Now" line + "Live brightness" card now show PERCEIVED brightness in PWM-sensitive
  mode (`targetBrightness ?: lastAppliedBrightness`, like the Dashboard hero), not the floored hardware
  value (+test). **D-118** Contexts rules modal no longer clips its last rule card under the nav bar —
  edge-to-edge `Dialog` (`decorFitsSystemWindows=false`) + `statusBarsPadding()` + trailing `Spacer(48dp)`,
  mirroring the rule editor (D-097/098). **D-119** `release.yml` `generate_release_notes: true` (auto
  "What's Changed" appended below the owner's UI body; idempotent). Audit note: `build`/`codeql` run on
  push-to-main + PR (green on last main push) and intentionally skip `v*` tags — release.yml re-runs the
  full ladder before signing, so "only sign on tag" is by design. **D-120/D-122** the "Use current
  location" buttons (Circadian + Contexts location rule) actively acquire — `LocationReader.activeFix`
  registers for live provider updates (sensors on → OS location indicator appears) and waits for a fresh
  fix, last-known only as a backup; Circadian also → ipwho.is IP fallback when opted in (default-off
  privacy gate, D-105) — instead of echoing another app's cached fix. Parity: Tasker's active/passive
  listener was used for location context rules. **D-121** geo-IP fallback moved
  from cleartext `ip-api.com` (`lat`/`lon`, `status:fail`) → HTTPS `ipwho.is` (`latitude`/`longitude`,
  `success:false`); network-security-config now pins cleartext OFF (app makes no cleartext traffic);
  fallback stays optional/off. Changelog `12.txt`. Owner squash-merges + publishes the v1.4.0 release.
- 2026-06-28 — 1.3.0 / `versionCode 11` (MINOR — new user-facing setting + surface): **D-116** Panic
  (Reset) gesture reworked from folded-in Tasker source. Trigger is now Upside-Down ∧ Display-On ∧
  **Proximity-NOT-Near** (the significant-motion EVENT is gone); the shake is validated by a 10 s
  sensitivity-gated leaky-bucket (`PanicShakeGate`, pure `:domain`, contract-tested vs the task528 A2
  Java). New GLOBAL **`%AAB_PanicSensitivity`** (0–10, default 8) with a slider at the bottom of the
  Live Debug screen (0 = pass-through, 10 = long vigorous shake; 10 s timeout = veto + re-arm only after
  flipping straight then upside-down again, D-021). Platform: `PanicGestureDetector` → orientation only,
  `PanicGate` → re-arm latch, `AndroidPanicSensorSource` arms + runs the gate over `TYPE_LINEAR_ACCELERATION`
  (fallback high-passed accelerometer); `AppModule` injects sensitivity (new `ContextEngine.effectiveSnapshot`)
  + proximity-near. Structurally retires the S14 grab-to-wake false-fire. Changelog `11.txt`; `tmp/` spec
  deleted. Owner: when squash-merging, STRIP any `[skip ci]` from the commit body (D-115), merge, then
  publish the v1.3.0 release from the GitHub UI.
- 2026-06-28 — 1.2.1 / `versionCode 10` (PATCH re-cut, no app change): **D-115** the v1.2.0 release
  workflow never ran — the squash-merge commit body carried a stray `[skip ci]` token (leaked from a
  commit message that described `clean-dist.yml`), which GitHub honored and skipped every workflow for
  that commit + tag, so v1.2.0 never published its signed APK. `release.yml` now triggers on
  `release: published` (immune to skip-ci) + a `workflow_dispatch` tag fallback; RUNBOOK §6 warns never
  to write the token in a commit/PR message. 1.2.1 re-cuts the SAME app (no runtime change since 1.2.0)
  so the release publishes cleanly. Changelog `10.txt`. Owner opens a NEW PR (v1.2.0's was merged), then
  publishes the v1.2.1 release.
- 2026-06-28 — 1.2.0 / `versionCode 9`: runtime bug fixes + UX. **D-108** service-start battery-saver
  flash (battery "unknown" `-1` sentinel; resolver won't match a battery rule until a real reading).
  **D-109** PWM-sensitive read-out now tracks PERCEIVED brightness (`targetBrightness` = un-floored engine
  value; `lastAppliedBrightness` stays the floored hardware value for override detection). **D-110**
  circadian stale-location fallback across the day rollover + recompute-on-resolve (`onWindowsRefreshed`
  → `reapply`) + staleness hints (`CircadianLocationStatus`) on the Circadian screen + dashboard (gold
  tinted-`Card`, m3_audit-coherent). **D-111** gold "resume context automation" banner + play icon;
  Tasker-style Profiles & Contexts IA (pinned Load/Save/Contexts action bar over the visible profile
  list, each opening its own modal); app-wide icon-vs-glyph consistency (`‹`/`›` back + pager glyphs →
  Material `IconButton`s). **D-112** GitHub Actions Node-20 → Node-24 (all actions bumped to node24
  majors; `build.yml` carries a node24 pin policy comment) + `clean-dist.yml` auto-removes a forgotten
  `dist/` APK from main. **D-113** Contexts rule list/editor: target profile emphasised (gold) +
  active-rule highlight; priority is a 1–100 scale (was 0..∞); "Use current Wi-Fi" appends to the SSID
  list (Tasker parity). **D-114** confirmation prompts before deleting a rule and deleting/overwriting a
  profile (shared `ConfirmDialog`, Tasker parity). RUNBOOK gains a "Design coherence — read m3_audit.md for ANY UI change" callout
  (owner request). Changelog `9.txt`. Owner sideloads `dist/` debug APK, then squash-merges + tags
  `v1.2.0` (dist/ auto-cleaned by CI if forgotten). SEMVER: minor — new user-facing surfaces (staleness
  hints + Profiles redesign) outrank the patch-grade bug fixes (RUNBOOK §6 "highest category wins");
  rationale block in `app/build.gradle.kts`.
- 2026-06-28 — 1.1.1 / `versionCode 8`: (D-107) security hardening — notification (`actionIntent`)
  and home-widget (`DashboardWidgetProvider`) PendingIntents made un-missably explicit (separate
  statements + `setPackage`, still `FLAG_IMMUTABLE`) to clear CodeQL `java/android/implicit-pendingintents`
  (High). The first post-1.1.0 CodeQL scan of `main` flagged the chained `Intent(this, X).setAction()`
  one-liner as implicit (Kotlin dataflow drops the constructor component through `.setAction()`); intents
  were already explicit + immutable at runtime, so no behaviour change. Added `changelogs/8.txt`. Owner
  tags `v1.1.1`.

- 2026-06-26 — 1.1.0 / `versionCode 7`: bumped **targetSdk 35 → 36** (Android 16), `compileSdk`
  36 in app + platform. Android 16 impact review found zero required platform code changes (edge-to-edge
  already enforced via D-097/098/100; back via AndroidX `BackHandler`; transitive native libs
  already 16 KB-aligned; specialUse FGS property already declared; specialUse FGS-from-boot unchanged
  15→16). Robolectric 4.14.1 → 4.16.1 (needed for SDK 36; runs on JDK 21); CI JDK 17→21; added CodeQL
  (`codeql.yml`, java-kotlin build-mode none). Debug build type gets `applicationIdSuffix=".debug"`
  (+ `-debug` versionName, "Tideo AB (Debug)" label, Shizuku authority `${applicationId}.shizuku`) so a
  debug build coexists with the signed release (D-106). **Owner on-device Pass A/B: all passed.**
  Bug/parity/privacy fixes folded in: **D-101** PWM/dimming threshold 0..100→0..255; **D-102** curve
  wizard auto-copies %AAB_Test + FlowRow button wrap; **D-103** circadian once-a-day location persisted
  across restarts (fixes screen-on default-scale drift); **D-104** generic chart label declutter +
  landscape height cap/scroll (S13 chart-engine fence lifted for generic changes); **D-105** ip-api.com
  geo-IP fallback now opt-in (default off). Docs: RUNBOOK §6 semver guidance, §7 "Bumping targetSdk",
  CI-failure protocol; `changelogs/7.txt`. Temporary `dist/` debug APK used for the on-device pass was
  removed before merge. Owner squash-merges + tags `v1.1.0`.
- 2026-06-25 — 1.0.4 / `versionCode 6`: (D-100) main-window bottom controls clipped under the nav bar
  with button/3-key navigation — the draft-settings `DraftApplyBar` (Discard/Apply) and the Menu's
  final "Recheck Permissions" row drew behind the system nav bar (targetSdk 35 enforces edge-to-edge on
  Android 15+). Fix: `navigationBarsPadding()` (+`imePadding()` on the bar) on the two spots not covered
  by an M3 `Scaffold`'s content insets. Unlike D-098's `Dialog` (no bottom inset delivered), these are
  in the MainActivity window where the inset resolves correctly (0 on pre-15, so no double padding).
  UI + version only; bottom-inset behavior is owner device-verified (not Robolectric-testable). Added
  `changelogs/6.txt`. Next free deviation: **D-101**.
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
