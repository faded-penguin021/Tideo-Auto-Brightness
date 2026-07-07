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
= `WRITE_SECURE_SETTINGS` via one-time `pm grant` (super dimming + Privileged Display toggles).
minSdk 31, target/compile 36.

## Current state

**Shipped: v1.7.0** (`versionCode 17`, tagged on `main`) — the Privileged Display Control feature
(D-149–D-155).

**Active work: 1.8.0 / `versionCode 18`** — **intent control for automation frameworks** (Tasker /
MacroDroid): an opt-in exported broadcast surface for the existing control verbs + profile load,
plus outbound state-change events. Multi-unit, persisted plan `plans/intent-control.md`; segment
checklist below. Branch `claude/tideo-brightness-intent-control-2rkhx3`.

`PARITY_CHECKLIST.md` is zero-`pending`; golden parity tests green; TODO/FIXME = 0;
`parity_gaps.md` has 0 open gaps. Full acceptance ladder green 2026-07-05.

How changes are made now: see `RUNBOOK.md` (change-type playbooks; the **glue-review protocol**
is mandatory for `:platform`/runtime diffs; multi-session features follow the playbook-5
persisted-plan pattern). The migration narrative is frozen in `../history/`; the deviations
registry stays live.

> Code/docs elsewhere cite deviations by number (e.g. `STATE.md D-048`, `F50`). All deviations
> — migration and ongoing — live in the permanent registry `DEVIATIONS_LEDGER.md` (200 rows per
> file, then `_A.md`/DA-…, `_B.md`/DB-… — D-153; gate findings are in
> `../history/STATE_rebuild.md`). Look there.

## A11y (TalkBack) backlog + crash-log capture — COMPLETE (folds into 1.8.0/vc18)

All units A0–A7 + C1 shipped (see Changelog); conventions = **D-156**, crash-log capture = **D-158**.
`plans/a11y-diagnostics.md` deleted at the last unit (durable content → those two ledger rows).

**Owner actions pending:**

- **H4 (D-135):** repo Settings → Code security → enable "Dependabot security updates" +
  "Private vulnerability reporting" (the committed files are inert without them).
- **H5 (D-137):** in the fdroiddata submission set `Binaries:` to the release-APK URL pattern
  and `reproducible: yes` (pin the CI's JDK 21) so F-Droid publishes the signed APK.

## Active work — 1.8.0 intent control (plan: `plans/intent-control.md`)

Opt-in exported broadcast surface (default OFF, D-105 pattern) for the existing control verbs +
`LOAD_PROFILE`/`CONTEXTS_RESUME`, plus outbound `STATE_CHANGED` events — so Tasker/MacroDroid can
both command and observe the app. `:app`-only; **`:domain`/`:platform`/goldens untouched.** Each unit
ends shippable: ladder green → this checklist ticked → commit → push (glue-review on U2/U3/U5).

- [x] **U0** — sync branch onto `origin/main`; persist plan; STATE Active-work + "Current state" fix.
- [x] **U1** — vc18 / 1.8.0 + `changelogs/18.txt` already in place (shared with a11y); `ControlPrefsStore`
  (`externalControlEnabled`, default off) + `controlPrefsDataStore`; `ControlPrefsStoreTest` (+2).
- [x] **U2** — exported `ControlReceiver` (gate = FIRST check) + 7 core verbs; `AutoBrightnessRuntime.panic`;
  manifest `exported="true"` intent-filter; ledger **D-157**; `ControlReceiverTest` (+6). Glue-review clean.
- [x] **U3** — VM-free `ProfileApplier` (bodies moved verbatim; `SettingsViewModelTest` UNMODIFIED, green) +
  receiver `LOAD_PROFILE`/`CONTEXTS_RESUME`; `ProfileApplierTest` (+3), receiver profile cases (+3). Glue-review clean.
- [ ] **U4** — Tools "Automation control" toggle + actions-help dialog.
- [ ] **U5** — outbound `STATE_CHANGED` events (optional, droppable).
- [ ] **U6** — README/DEVICE_TEST §12/datastore_map docs; delete plan file; final STATE compression.

## Decided non-items (don't re-litigate without new evidence)

- **Repo/process (2026-06/07):** root `CHANGELOG.md` (redundant with STATE + fastlane + the
  ledger); speculative dependency-currency bumps (only on a security advisory); a standalone
  doc-drift audit (RUNBOOK self-adaptation covers it); action SHA-pinning / Gradle dependency
  verification (declined 2026-06-29 as wrong cost/benefit for a solo F-Droid app).
- **Privileged Display (decided at Segments 4.5–5, D-150–D-152):**
  - **Per-toggle orthogonal scheduling** — D-150 built it, the D-151 pivot removed it.
    Scheduling IS "a Contexts rule loads a profile carrying display fields", winner-takes-all.
  - **A persisted last-applied seed** for `DisplayTogglesCoordinator` — deferred at 4.5: it
    would shrink the D-151 process-death residual by re-introducing exactly the latch-like
    persistence the pivot removed. Revisit only on real-world reports.
  - **QS tile / notification action for grayscale** (or any display toggle) —
    profiles/Contexts are the switching surface.
  - **Refresh-rate forcing** / OEM-specific alternate keys — OEM-fragmented (D-048/D-149).
  - **A manual Extra-Dim toggle** — Extra Dim is pipeline-owned (D-144/D-149).

## Changelog

One line per shipped change (newest first). Keep terse; details live in the ledger.

- 2026-07-07 — folds into **1.8.0 / vc18** (intent-control **U3**; D-157): VM-free `settings/ProfileApplier`
  with the `applyProfile`/`resumeContextAutomation` bodies moved out of `SettingsViewModel` verbatim (the VM
  now delegates via `viewModelScope.launch`); `SettingsViewModelTest` passes **UNMODIFIED** — the equivalence
  check. `ControlReceiver` gains `LOAD_PROFILE` (String extra `name`, read in `onReceive`; missing name is a
  double-guarded no-op) + `CONTEXTS_RESUME`, both driving the shared applier built from the same
  `AppModule.userProfileStore` the UI uses (no duplicated logic). Manifest filter +2 actions. `ProfileApplierTest`
  (+3: apply-latches-lock-preserving-globals, unknown-name no-op, resume-clears-lock) and receiver profile cases
  (+3, incl. no-name no-op; disabled-loop now covers the 2 new verbs). Removed the VM's now-unused `LiveRuntimeState`
  import. `:app`-only; domain/platform untouched. **Glue-review clean**: verbatim extraction, app-context parity,
  gate-still-first, single wiring source.
- 2026-07-07 — folds into **1.8.0 / vc18** (intent-control **U2**; D-157): the exported `ControlReceiver`
  + 7 core verbs. The opt-in gate (`externalControlEnabled`) is the receiver's FIRST check via `goAsync`
  → `handle` → `route`; while off, every action is dropped before it touches settings/service — pinned by
  `controlDisabled_ignoresAllActions_D157` (D-147-style negative test). SERVICE_ON/OFF/TOGGLE reuse the
  `WidgetActionReceiver.toggle` dance parameterized by target state (copied, not shared); PAUSE/RESUME/
  REAPPLY delegate to `AutoBrightnessRuntime`; new 1-line `AutoBrightnessRuntime.panic` sends the existing
  `ACTION_PANIC` (same intent as the notification Reset). Manifest registers it `exported="true"` with a
  7-action intent-filter, no new permission. `ControlReceiverTest` (+6: gate security property, per-verb
  routing for PAUSE/RESUME/REAPPLY/PANIC, unknown-action no-op; SERVICE_* routing carved out like the
  widget toggle — WorkManager/DataStore-singleton). Ledger **D-157**. `:app`-only; domain/platform
  untouched. **Glue-review clean** (mandatory U2 unit): gate-first + genuine, exported exposure gated by
  default-OFF flag, verbs reuse hardened paths, atomic `updateData`, `goAsync` finishes in `finally`.
- 2026-07-07 — folds into **1.8.0 / vc18** (intent-control **U1**; D-157): the opt-in gate storage for the
  external intent-control surface. New `control/ControlPrefsStore` (`externalControlEnabled`, default OFF —
  D-105 opt-in pattern) + `controlPrefsDataStore` (own prefs store, NOT an `AabSettings` field so profile
  apply/import can never flip it). vc18/1.8.0 + `18.txt` already in place from the shared a11y work (both
  fold into this release), so no bump needed. `ControlPrefsStoreTest` (+2: `defaultsToDisabled`,
  `enableRoundTrips`). No receiver yet (U2). `:app`-only; domain/platform untouched. Glue-review: N/A (U1).
- 2026-07-07 — folds into **1.8.0 / vc18** (A11y backlog **C1** — last unit, closes the plan; **D-158**):
  local crash-log capture. New `AabApplication` installs a default uncaught-exception handler that writes
  a timestamped trace to `filesDir/crash` (5-newest ring) then **always delegates** to the previous
  handler (process still dies); no telemetry/network/FileProvider. Tools → Diagnostics gains a "Copy
  latest crash log" row (clipboard, "none recorded" state). **Glue-review clean** (C1 is the only glue
  unit): install idempotent, write-then-delegate-in-`finally`, disk-only state, newest-first rotation.
  Tests +11. `18.txt` +1 line. `plans/a11y-diagnostics.md` **deleted** — a11y backlog COMPLETE.
- 2026-07-07 — folds into **1.8.0 / vc18** (A11y backlog **A7** — final a11y unit, D-156): the 48 dp
  motor-accessibility touch-target floor over the A0–A6 interactive primitive surfaces + DEVICE_TEST §12
  owner TalkBack/Switch-Access checklist. **Zero production fixes** — every hand-authored clickable
  already meets 48 dp. Two guardrail-9 corrections: the plan's `assertTouchWidthIsAtLeast` overload isn't
  in this compose BOM (read `touchBoundsInRoot` directly), and Robolectric doesn't surface Material's
  runtime `minimumInteractiveComponentSize()` for the stock `Slider`/`Switch`/`Checkbox` — so the gate
  floors hand-authored clickables and carves out (a) those Role-tagged M3 form primitives (pinned by a
  test; real tap area owner-verified via §12) and (b) the ~8–10 dp `ChartPager` position dots (redundant
  with the 48 dp ‹ › arrows + swipe). `TouchTargetsA11yTest` (+4). No `18.txt` change (A0's blanket a11y
  note covers it); **no new ledger row** (folds under D-156, leaving D-157 free for intent-control).
  Glue-review: N/A (UI/test semantics-only; no runtime glue).
- 2026-07-07 — folds into **1.8.0 / vc18** (A11y backlog **A6**, D-156): the profiles/contexts + info
  surfaces rendered under the `SemanticsAudit` gate — About, User Guide, standalone Profiles (its
  collapsible manage/legacy sections expanded), the Contexts rule list, and the full rule editor with
  every trigger section open. All already green via the A0/A1 primitives except the battery section's
  RAW `Switch` ("only while charging"), whose label is a sibling `Text` that never merges onto the
  switch node — gave it its own contentDescription (D-156 pattern, like `TriggerSection`). `RuleEditor`
  `private`→`internal` so the audit renders it directly (it lives in a second `Dialog` window that
  `onRoot()` can't reach). `TaskerHelp.kt` is a `@StringRes` registry, not a composable — nothing to
  render. `ScreensInfoA11yTest` (+10: per-surface audit, section-header `heading()` assertions, one
  targeted charging-switch CD assertion). No `18.txt` change (A0's blanket a11y note covers it).
  Glue-review: N/A (UI semantics-only; no runtime glue).
- 2026-07-07 — folds into **1.8.0 / vc18** (A11y backlog **A5**, D-156): the group-2 settings screens
  (Misc / Tools / Privileged Display / Live Debug) rendered under the `SemanticsAudit` gate. The audit
  flagged one RAW M3 `Slider` on each of Tools (wizard τ), Privileged Display (night-light temperature)
  and Live Debug (panic sensitivity) — their visible label is a sibling `Text` that never merges onto
  the slider node — so each got its own `a11y_*` contentDescription (3 new strings; Misc uses only A0
  primitives, clean). `SettingsScreensGroup2A11yTest` (+11: per-screen audit incl. Privileged Display
  at BASIC grant-card and ELEVATED+HDR, section-header `heading()` assertions, targeted CD assertions
  for the three sliders). No `18.txt` change (A0's blanket a11y note covers it). Glue-review: N/A (UI
  semantics-only; no runtime glue).
- 2026-07-07 — folds into **1.8.0 / vc18** (A11y backlog **A4**, D-156): the group-1 settings screens
  (Curve & Brightness / Reactivity / Circadian / Super Dimming) rendered under the `SemanticsAudit`
  gate — audit already green (every control is an A0-labeled slider/switch or a text-carrying button:
  Apply/Discard/Reset bar, the back arrow's `a11y_back` CD, grant link, date/location buttons), so no
  screen-local label fixes were needed. `SettingsScreensA11yTest` (+8: per-screen audit incl. Super
  Dimming at BASIC and ELEVATED tier, + section-header `heading()` assertions per screen). No prod-code
  change; no new `18.txt` note (A0's "part 1 … further screens" covers it). Glue-review: N/A (test-only).
- 2026-07-07 — folds into **1.8.0 / vc18** (A11y backlog **A3**, D-156): Dashboard/Menu/Onboarding
  rendered under the `SemanticsAudit` gate — audit already green (every interactive node inherits a
  text label from the A0/A1 shared components: labeled switches, text-carrying nav rows/cards/buttons,
  heading section headers), so no label fixes were needed. Dashboard's three dynamic amber banners
  (`StaleBanner`/`OverrideCard`/`CircadianStaleHint`) gain `liveRegion = LiveRegionMode.Polite` on the
  banner container so TalkBack announces them when they appear/change. `ScreensA11yTest` (+8: per-screen
  audit — each screen seeded so every conditional surface renders — + the 3 banner liveRegion assertions).
  No new `18.txt` note (A0's "part 1 … further screens" covers it). Glue-review: N/A (UI/semantics only).
- 2026-07-07 — folds into **1.8.0 / vc18** (A11y backlog **A2**, D-156): text alternatives for the
  Canvas graphs (invisible to TalkBack). `ChartCanvas` gains a generic `contentDescription` param
  applied to the Canvas draw node; all eight graphs (brightness curve, dimming, reactivity, alpha,
  circadian dimming/scaling, taper, power draw) pass a one-sentence `a11y_graph_*` summary naming the
  graph + key params. The audit gate also flagged `GraphScaffold`'s pager page-dots as unlabeled
  clickables — fixed ("Go to chart N"), and the ‹ › arrow labels moved off hardcoded English to
  `a11y_chart_prev`/`_next`. `GraphsA11yTest` (+9: per-graph CD assertion + pager arrows/dots + audit).
  No new `18.txt` note (A0's "part 1 … further screens" covers it). Glue-review: N/A (UI/semantics only).
- 2026-07-07 — folds into **1.8.0 / vc18** (A11y backlog **A1**, D-156): TalkBack labels for the
  remaining shared components — the three flagged icon-only toggleables (Dashboard master
  `service_switch`, `TriggerSection` switch, `AppPickerList` checkboxes) now announce their names;
  `KeyValueRow` merges key+value into one announcement; the in-app flash pill is a polite liveRegion.
  Clickable nav rows/cards/buttons already merged their text (audit-verified). `ComponentsA11yTest`
  (+6, audit gate + per-fix assertions). No new `18.txt` note (A0's "part 1 … further screens" covers
  it). Glue-review: N/A (UI/semantics only).
- 2026-07-06 — folds into **1.8.0 / vc18** (A11y backlog **A0**, plan `plans/a11y-diagnostics.md`;
  stacked on the intent-control branch — both features share 1.8.0/vc18):
  **D-156** TalkBack semantics for the S12.5b settings primitives (help-ⓘ named per field,
  slider/switch announce their labels, section headers are headings, readouts merged) + the
  `SemanticsAudit` per-unit test gate (tests +5, written failing-first). Glue-review: N/A
  (UI/semantics only).
- 2026-07-06 — docs/process only (intent-control **U0**): branch synced onto `main` (v1.7.0 tagged);
  persisted plan `plans/intent-control.md` added; STATE gains the 1.8.0 Active-work checklist and
  the "Current state" line now reflects 1.7.0 as shipped. No code.
- 2026-07-05 — docs-only (F-Droid code-quality scan): RUNBOOK F-Droid-changelog bullet gains the
  <500-char `whatsNew` rule (F-Droid flags ≥ 500 as Minor). `changelogs/17.txt` left at 972 chars —
  vc17 is already tagged, so the fix would need a release re-cut for a cosmetic Minor; the rule
  keeps vc18+ within the limit. No fdroiddata change needed (whatsNew is read from the app repo's
  fastlane tree at the pinned build commit).
- 2026-07-05 — folds into 1.7.0/vc17 (owner on-device finding): **D-155** panic (Reset) now
  returns ALL privileged display toggles to DEFAULTS (unconditional writes — not the baseline,
  which may itself impair the screen; temperature untouched) via
  `DisplayTogglesCoordinator.panicReset()`; same-process restart re-asserts the baseline.
  Tests +5. Also documented (D-048, no code): OxygenOS ignores the Night Light Kelvin key —
  temperature slider + D-154 tracking visually inert on OnePlus.
- 2026-07-05 — folds into 1.7.0/vc17 (owner-requested): **D-154** circadian Night Light
  temperature — profile toggle `nightLightCircadianEnabled` (full D-151 fan-out); the
  temperature rides the task90 tanh modifier (profile temp/AOSP-default at night → 4082 K by
  day) via a 60 s only-on-change ticker in `DisplayTogglesCoordinator` (own computation —
  pipeline cycles starve in steady light, D-110). Manual temp changes don't stick while
  tracking (consented). Tests +7. Glue-review: one comparator-replay finding fixed pre-commit.
- 2026-07-05 — docs/process only (owner-instructed): **D-153** deviations-ledger file cap —
  200 rows per file, rollover `DA-001`/`DB-001`…; summarizing rejected (rows are cited by
  number, stay verbatim). Pointers updated in the ledger header, CLAUDE.md, RUNBOOK.
- 2026-07-05 — docs-only (Privileged Display **Segment 5 — feature COMPLETE**): README +
  `screen_map.md` finalized (toggles = ELEVATED **profile settings** applied by
  profiles/Contexts — no standalone scheduler); owner checklist = **`DEVICE_TEST_SCRIPT.md`
  §11**; `plans/privileged-display.md` deleted (content in D-149–D-152); RUNBOOK playbook 5
  gains the multi-session persisted-plan pattern; architecture docs synced; non-items
  recorded above; STATE recompressed.
- 2026-07-05 — folds into 1.7.0/vc17 (UI polish, owner finding; refines D-152, no ledger row):
  the AOSP-keys/OEM note moved off an always-on footer card to a top-bar **ⓘ → dialog**
  (`SettingsScaffold` gains an `actions` slot); i18n ratchet 0.
- 2026-07-05 — folds into 1.7.0/vc17 (4.5 follow-up, owner findings): **D-152** profile port
  complete — AOD / stay-awake / HDR join `AabSettings` (7 display fields); ONE draft-edited
  profile surface + grant card; `applyNow` writes the device directly exactly when the service
  is off (Apply is never a silent no-op). Glue-review: clean.
- 2026-07-04 — folds into 1.7.0/vc17 (**Segment 4.5 — owner-instructed pivot**): **D-151**
  display toggles become PROFILE settings applied on profile change by
  `DisplayTogglesCoordinator` (idempotent only-on-change; seed adopts; resting = baseline; NO
  latch/sweep — accepted process-death residual). D-150 schedule system removed wholesale;
  `ContextMatching` + `SecureDisplayController` kept. Glue-review: clean.
- 2026-07-03 — folds into 1.7.0/vc17 (Segment 4): **D-150** display schedules end-to-end (own
  DataStore, edge-triggered coordinator with death-safe latch + restore, rules UI, shared
  `TriggerEditors.kt` extraction; glue-review: +1 s boundary-wake fix) — **removed again by
  4.5**; the ledger row is the record.
- 2026-07-03 — folds into 1.7.0/vc17 (Segment 3, `:domain`-only): display-rule resolver + the
  behavior-preserving **`ContextMatching` extraction** (goldens untouched and green). The
  resolver died with 4.5; `ContextMatching` stays live.
- 2026-07-03 — folds into 1.7.0/vc17 (Segment 2 — the core ask): the **Privileged Display
  screen** (route always registered; Menu row only at ELEVATED off live `tierFlow()`;
  self-guarding 3-channel grant card; read-back VM). Glue-review: one Mutex finding fixed.
- 2026-07-03 — repo-tooling only: `setup-android-sdk.sh` seeds the Gradle wrapper cache from
  `/opt` (the cloud egress proxy 403s the wrapper download).
- 2026-07-03 — 1.7.0 / `versionCode 17` (MINOR, Segment 1): **D-149** `:platform`
  `SecureDisplayController` — Night Light (+temperature), daltonizer, inversion, AOD,
  stay-awake-charging, Android-14+ force-SDR; AOSP-universal keys only; Extra Dim excluded
  (pipeline-owned). Changelog `17.txt`. Glue-review: one accepted finding documented.
- 2026-07-02 — tests-only (U6 → **F-backlog CLOSED**): **D-148** H3 glue-seam audit's last four
  seams covered (+19 tests).
- 2026-07-02 — docs-only (U5): parity transcription spot-check — **clean, zero disagreements**;
  `XML_RECIPES.md` gains R0.
- 2026-07-02 — 1.6.2/vc16 (U4): **D-146** NaN import guard; **D-147** widget actions off the
  exported provider; `/security-review` clean.
- 2026-07-02 — 1.6.2/vc16 (U3): **D-144** post-death Extra-Dim residual; **D-145** ShizukuShell
  bind-timeout unbind.
- 2026-07-02 — 1.6.2/vc16 (U2): **D-141** rule-edit cooldown bypass; **D-142** wifi `[WIFI]`
  gate + snapshot clear; **D-143** stale ssidFlow resolves dropped.
- 2026-07-02 — 1.6.2 / `versionCode 16` (PATCH, U1): **D-139** panic cancel-and-joins the
  animation consumer; **D-140** zombie-FGS gates. Changelog `16.txt`.
- 2026-07-02 — docs-only: **D-138** F-backlog adopted (U1–U6).
- 2026-07-01 — build-config only (H5): **D-137** release APK **proven reproducible**; owner
  fdroiddata steps under "Owner actions pending".
- 2026-07-01 — tests (H3): **D-136** glue-seam audit + 4 gap-closing suites (+14 tests).
- 2026-07-01 — repo-policy only (H4): **D-135** `SECURITY.md` + security-only Dependabot
  (needs the owner-side Code-security toggles).
- 2026-07-01 — 1.6.1 / `versionCode 15` (PATCH, H2): **D-134** saved pre-service brightness
  mode persisted across process death.
- 2026-07-01 — docs-only: **D-133** hardening backlog adopted; RUNBOOK gains the mandatory
  **glue-review protocol** (H1); `FABLE_HANDOFF.md` deleted.
- 2026-06-30 — 1.6.0 / `versionCode 14` (MINOR): **D-130** no-Location SSID path (DUMP);
  **D-131** full UI i18n (ratchet 0); **D-132** plug/unplug bypasses the battery cooldown.
- 2026-06-29 — CI-only: per-job `timeout-minutes` + wrapper properties in Gradle cache keys;
  stricter supply-chain measures declined with reasons.
- 2026-06-29 — 1.5.0 / `versionCode 13` (MINOR): **D-125** wizard curve suggestion is
  user-driven (preview seeds the draft); **D-126** resume no longer loops back to paused (F64
  settle window also suppresses in-cycle override detection).
- 2026-06-29 — CI-only: **D-124** `release-preflight.yml` PR gate (versionCode/semver/changelog
  when the PR ships app code; skip-ci token scan on every PR).
- 2026-06-29 — CI-only: **D-123** `release.yml` reuses the F-Droid changelog as the GitHub
  Release "What's new".
- 2026-06-28 — 1.4.0 / `versionCode 12` (MINOR): **D-117**–**D-122** (PWM perceived-brightness
  graph, edge-to-edge modal, release-notes auto-append, fresh location fix, HTTPS geo-IP).
- 2026-06-28 — 1.3.0 / `versionCode 11` (MINOR): **D-116** Panic gesture rework +
  `%AAB_PanicSensitivity`.
- 2026-06-28 — 1.2.1 / `versionCode 10` (PATCH re-cut): **D-115** skip-ci token; `release.yml`
  triggers on `release: published`.
- 2026-06-28 — 1.2.0 / `versionCode 9` (MINOR): **D-108**–**D-114** (battery sentinel, IA
  rework, confirmations, priority 1–100).
- 2026-06-28 — 1.1.1 / `versionCode 8` (PATCH): **D-107** explicit PendingIntents.
- 2026-06-26 — 1.1.0 / `versionCode 7` (MINOR): targetSdk/compileSdk 36, Robolectric 4.16.1
  (JDK 21), CodeQL, `.debug` suffix (D-106); folded **D-101**–**D-105**. Owner Pass A/B passed.
- 2026-06-25 — 1.0.4 / `versionCode 6` (PATCH): **D-100** nav-bar padding on bottom controls.
- 2026-06-24 — 1.0.3 / `versionCode 5` (PATCH): **D-098** dialog Save/Cancel clip; **D-099**
  version/tag realignment + RUNBOOK §6 release checklist.
- 2026-06-24 — Wi-Fi context fixes: **D-096** no-Location SSID strategies first; **D-097**
  rule-editor edge-to-edge.
- 2026-06-24 — 1.0.1 / `versionCode 4`: re-tag so the release tag contains `fastlane/`.
- 2026-06-24 — F-Droid prep: fastlane metadata added; submission + tag are owner steps.
- 2026-06-23 — v1.0.0: Tasker→Kotlin rebuild complete; Gate 3 signed off. Full history frozen
  in `../history/`.
