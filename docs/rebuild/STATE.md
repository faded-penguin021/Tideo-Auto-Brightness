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

**targetSdk 35 → 36 bump** (Android 16). Owner's phone is on Android 16, making the bump
on-device verifiable for the first time. Plan: `compileSdk=36` first (Stage 1, shippable
alone), then `targetSdk=36` + version bump (Stage 2). On-device Pass A (regression) + Pass B
(feature-availability) are owner-verified.

| Stage | Date | Status | Notes |
|---|---|---|---|
| 0 — impact matrix | 2026-06-26 | done | Zero required code changes (matrix below) |
| 1 — compileSdk=36 | 2026-06-26 | done | ladder green; lint clean (no new findings); 16 KB alignment verified |
| 2 — targetSdk=36 + ver | 2026-06-26 | done | 1.1.0 / versionCode 7; Robolectric 4.14.1→4.16.1 (see below) |
| 3 — auto regression | 2026-06-26 | done | full ladder green under SDK 36 incl. golden vectors |
| 4 — on-device A/B | — | **pending (owner)** | Pass A/B in the plan; debug APK in `dist/` (temporary) |
| 5 — record + RUNBOOK §7 | 2026-06-26 | done | RUNBOOK §7 added; playbook for next bump (37) |

**Test-infra change:** Robolectric **4.14.1 → 4.16.1** — 4.14.1 caps at `maxSdkVersion=35`
and threw `targetSdkVersion=36 > maxSdkVersion=35` once targetSdk flipped. 4.16 is the first
release with SDK 36 (Baklava) and **requires JDK 21** to run SDK-36 tests (the Gradle JVM here
is JDK 21, so OK; `sourceCompatibility` stays 17 — that's only bytecode target). Tests had no
`@Config(sdk=…)` pins, so they auto-run under the manifest targetSdk; no per-suite config needed.

**Android 16 impact matrix** (surfaces this app touches; all dispositions verified against code):

| Surface | A16 change | Disposition |
|---|---|---|
| Edge-to-edge | opt-out fully disabled | no-op — never opted out; D-097/098/100 already adapted |
| Predictive back | on by default, `onBackPressed()` dead | no-op — only AndroidX `BackHandler` (predictive-native), no override |
| specialUse FGS | `<property>` + Play review | no-op — property declared; review is Play-only (we ship F-Droid) |
| FGS bg-job quotas | jobs obey runtime quota | no-op — one 15-min periodic `MaintenanceWorker`, WorkManager-managed |
| Local network perm | opt-in for raw sockets/mDNS | no-op — geo-IP is remote HTTP; SSID via Shizuku/dumpsys, no sockets |
| Adaptive layouts ≥600dp | orientation attrs ignored | no-op — no `screenOrientation` set |
| 16 KB page size | native libs must be 16 KB-aligned | no-op — transitive libs present (`libdatastore_shared_counter`, `libandroidx.graphics.path`) but all ELF LOAD + zip-aligned to 0x4000, verified at Stage 1 |
| Secure/System writes | (no A16 change) | no-op — owner-verify (Pass B) |
| Boot/notif/tile/widget | (no A16 targeting change) | no-op — owner-verify (Pass A) |

**Blockers:** none.

**Owner test-pass findings (folded into 1.1.0):** D-101 dimming/PWM threshold range 0..100→0..255;
D-102 curve wizard auto-copies %AAB_Test + button row wraps (FlowRow). D-103 (circadian once-a-day
location not persisted → cold-start defaults) and D-104 (graph label overlap, chart-engine fenced)
logged OPEN for focused follow-ups. Not-bugs: reboot (Samsung sideload auto-start, not the bump),
solar-noon "@12" (no-location UTC fallback), override unscale+clamp255 (already ported, task561),
export goes to the SAF-chosen location. Added CI `codeql.yml` (java-kotlin, build-mode none); bumped
CI JDK 17→21 (Robolectric 4.16 needs it); RUNBOOK gained semver guidance (§6) + a CI-failure protocol.

**New deviations (this work):** D-101 (PWM threshold 0..255), D-102 (wizard clipboard+FlowRow),
D-103 (circadian location persisted across restarts), D-104 (chart label declutter + landscape height
cap/scroll), D-105 (geo-IP opt-in) — all fixed in 1.1.0. Next free: **D-106**.

> Write new deviations straight into the permanent registry `DEVIATIONS_LEDGER.md` (its
> "Maintenance deviations" section), not here — this slot is only a transient staging note
> during an in-flight change. Numbering is **one continuous sequence**: next free number is
> **D-101** (historical high-water mark D-100); never restart at D-001. A deviation, once
> numbered, lives in the registry forever — it is never compressed out.

## Changelog

One line per shipped change (newest first). Keep terse.

- 2026-06-26 — 1.1.0 / `versionCode 7`: bumped **targetSdk 35 → 36** (Android 16), `compileSdk`
  36 in app + platform. Android 16 impact review found zero required code changes (edge-to-edge
  already enforced via D-097/098/100; back via AndroidX `BackHandler`; transitive native libs
  already 16 KB-aligned; specialUse FGS property already declared). Robolectric 4.14.1 → 4.16.1
  (needed for SDK 36; runs on JDK 21). Full ladder green; **on-device Pass A/B owner-pending**.
  Added `changelogs/7.txt`, RUNBOOK §7 "Bumping targetSdk", temporary `dist/` debug APK.
  Debug build type now carries `applicationIdSuffix=".debug"` (+ `-debug` versionName, "Tideo
  AB (Debug)" label) so a debug build coexists with the stable signed release without sharing
  data; Shizuku provider authority is now `${applicationId}.shizuku` to follow the suffix.
  Owner test-pass fixes folded in: D-101 (dimming/PWM threshold 0..100→0..255), D-102 (curve
  wizard auto-copies %AAB_Test + FlowRow button wrap). CI JDK 17→21 (Robolectric 4.16); added
  `codeql.yml`; RUNBOOK semver guidance + CI-failure protocol. D-103/D-104 logged OPEN.
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
