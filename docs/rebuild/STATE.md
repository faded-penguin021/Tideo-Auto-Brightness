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

**Code-complete, awaiting owner release cut: 1.8.0 / `versionCode 18`** — two features folded together:
(a) **intent control for automation frameworks** (Tasker / MacroDroid, **D-157**) — an opt-in exported
broadcast surface for the control verbs + `LOAD_PROFILE`/`CONTEXTS_RESUME`, plus outbound
`event.STATE_CHANGED` events; `:app`-only, `:domain`/`:platform`/goldens untouched; user reference in
`docs/AUTOMATION.md`. All units U0–U6 shipped (see Changelog). (b) the **A11y (TalkBack) backlog +
crash-log capture** (units A0–A7 + C1) — conventions **D-156**, crash capture **D-158**. `changelogs/18.txt`
final (455 chars). No active multi-unit work; both persisted plan files deleted at their final units.

**Pre-release audit passed (2026-07-09, Fable):** the 1.8.0 branch was audited end-to-end — D-157
design/tests/docs validated; one real finding fixed (**D-160**, external RESUME zombie gate) and
`DEVICE_TEST_SCRIPT.md` **§14** (edge-to-edge/IME sweep) added; the branch sits directly on main
HEAD (no rebase needed; `claude/intent-control-u4-i2i7e1` is fully subsumed — 0 unique commits —
delete both branches after merge). **Full 5-rung ladder green in-session 2026-07-09.**

`PARITY_CHECKLIST.md` is zero-`pending`; golden parity tests green; TODO/FIXME = 0;
`parity_gaps.md` has 0 open gaps. H4/D-135 + H5/D-137 done 2026-07-07.

**Owner actions pending (the 1.8.0 release path):**

1. **PR is deliberately deferred until the F-Droid review completes** (owner decision 2026-07-09) —
   CI (build/CodeQL/release-preflight) first runs there; the local ladder above is the equivalent
   build.yml task set. When ready, open it from `claude/user-guide-accuracy-check-i5fxex` → `main`.
   Ready-made draft (no `[skip ci]`-class tokens, D-115):
   - *Title:* `1.8.0: intent control (D-157), TalkBack a11y (D-156/D-158), IME fix (D-159), RESUME gate (D-160)`
   - *Body bullets:* opt-in automation surface (verbs + LOAD_PROFILE/CONTEXTS_RESUME + outbound
     STATE_CHANGED; docs/AUTOMATION.md) · TalkBack backlog A0–A7 + crash-log capture C1 · D-159
     three-part IME fix · D-160 audit fix (glue-review verdicts in the U2/U5/38c66cd commit bodies) ·
     owner on-device checklist = DEVICE_TEST §§12–14 · vc18/1.8.0, changelog 455 chars.
2. After CI green: on-device pass of `DEVICE_TEST_SCRIPT.md` **§12 (TalkBack), §13 (automation), §14
   (insets)**; findings → "Gate findings" here.
3. Squash-merge, cut **v1.8.0 / vc18** from `main` via the Release UI, then delete both work branches.

How changes are made now: see `RUNBOOK.md` (change-type playbooks; the **glue-review protocol**
is mandatory for `:platform`/runtime diffs; multi-session features follow the playbook-5
persisted-plan pattern). The migration narrative is frozen in `../history/`; the deviations
registry stays live.

> Code/docs elsewhere cite deviations by number (e.g. `STATE.md D-048`, `F50`). All deviations
> — migration and ongoing — live in the permanent registry `DEVIATIONS_LEDGER.md` (200 rows per
> file, then `_A.md`/DA-…, `_B.md`/DB-… — D-153; gate findings are in
> `../history/STATE_rebuild.md`). Look there.

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

- 2026-07-09 — no-code (pre-release audit close-out): full 5-rung ladder green on the 1.8.0 branch
  in-session (goldens incl.); `changelogs/18.txt` re-verified 455 chars; Current-state rewritten with
  the owner release path (PR deferred until F-Droid review — owner decision — with a ready-made PR
  draft above); branch topology verified (based on main HEAD; `intent-control-u4` subsumed, 0 unique).
- 2026-07-09 — docs-only (pre-release audit): `DEVICE_TEST_SCRIPT.md` gains **§14 edge-to-edge +
  keyboard insets** (items 47–50) — the D-159 `enableEdgeToEdge()` change is app-wide and invisible
  to CI, but had no on-device checklist entry; §14 covers the draft-screen keyboard lift (the
  original bug), Dialog-editor isolation (D-098), an all-screens insets sweep (D-100 class), and
  non-draft keyboard surfaces, each under gesture AND 3-button nav.
- 2026-07-09 — folds into 1.8.0/vc18 (pre-release audit finding): **D-160** external `RESUME`
  (D-157 surface) could resurrect a user-disabled service — the F74 "resurrect" contract's implicit
  precondition (paused-override notification ⇒ serviceEnabled) didn't survive the verb's re-exposure.
  Gated at `ControlReceiver.route` (`serviceEnabled=false` → drop; notification path untouched);
  pinned by `resume_whileServiceDisabled_isDropped_D160`; AUTOMATION.md/KDoc no-op claims corrected to
  per-verb truth; DEVICE_TEST §13.43 gains the expectation. `:app`-only. Glue-review of the audit fix:
  see commit body.
- 2026-07-08 — folds into 1.8.0/vc18 (owner on-device bug): **D-159** keyboard-tall dead gap at the end
  of the draft-settings screens. `DraftApplyBar` carried `imePadding()` inside the Scaffold `bottomBar`,
  so an open keyboard inflated the bar's height and Scaffold reserved that as content bottom-padding — a
  keyboard-tall strip at the end of the scroll, only visible when the bottommost field is focused (hence
  "only that field"). Two-part fix: (1) `MainActivity.enableEdgeToEdge()` so the IME is an inset, not a
  legacy window resize; (2) move the lift to Scaffold level — `DraftSettingsScaffold`
  `Scaffold(Modifier.imePadding())`, `DraftApplyBar` drops its own imePadding (keeps
  navigationBarsPadding). `:app`-only (MainActivity + SettingsControls); no `:domain`/`:platform`/goldens
  touched. Ladder green (assembleDebug + testDebugUnitTest + lintDebug). On-device visual re-check is an
  owner step. NOTE: the fix is the full 3-part edge-to-edge keyboard recipe —
  (1) `enableEdgeToEdge()`, (2) manifest `android:windowSoftInputMode="adjustResize"`, (3) Scaffold-level
  `imePadding()`. Earlier branch commits shipped (1) then (3) but omitted (2), and the gap survived;
  all three are required.
- 2026-07-08 — docs/copy-only (user-guide accuracy audit): corrected `guide_s5_body` (Profile Management).
  It claimed "the default profile is modifiable but not deletable" and named only Battery Saver + Outdoors as
  presets — both stale. Reality (owner-decision 3, S12.6d/G2R-F15): all FIVE built-ins (Default, Battery Saver,
  Video Streaming, Outdoors, Night Reading) are editable AND deletable, with "Restore factory profiles" to
  re-seed — a deliberate deviation from Tasker's delete-guard. Guide corrected to shipped behavior (not the
  code); owner picked doc-fix over restoring the guard. Other 8 guide sections spot-audited against code —
  all accurate. No test/i18n change (single `values/strings.xml`; no test pins the copy).
- 2026-07-07 — **1.8.0 / vc18 — intent control for automation frameworks (Tasker / MacroDroid), COMPLETE**
  (**D-157**; `:app`-only, `:domain`/`:platform`/goldens untouched; full detail in the ledger row). Opt-in
  exported broadcast surface, default OFF (D-105 pattern). **U1** `ControlPrefsStore`/`controlPrefsDataStore`
  opt-in gate (its own prefs store, never an `AabSettings` field). **U2** exported `ControlReceiver` + 7 core
  verbs (gate = FIRST check, pinned by `controlDisabled_ignoresAllActions_D157`) + 1-line
  `AutoBrightnessRuntime.panic`, manifest `exported="true"` intent-filter, no new permission. **U3** VM-free
  `ProfileApplier` (bodies moved verbatim — `SettingsViewModelTest` passes UNMODIFIED) + `LOAD_PROFILE`(extra
  `name`)/`CONTEXTS_RESUME`. **U4** Tools "Automation control" card — `ControlPrefsViewModel` toggle + "Show
  actions" dialog (strings ratchet 0). **U5** outbound `event.STATE_CHANGED` (extras
  enabled/running/paused/profile) — publisher gated by the flag; `onDestroy` emits the single authoritative
  OFF before `scope.cancel()` (covers SERVICE_OFF/Disable/Panic). **U6** `docs/AUTOMATION.md` + README link +
  `DEVICE_TEST_SCRIPT.md` §13 + `datastore_map.md` `control_prefs`/`power_draw` rows; plan file deleted.
  Tests +20 across `ControlPrefsStoreTest`/`ControlReceiverTest`/`ProfileApplierTest`/`ToolsAutomationControlTest`/`AmbientMonitoringServiceTest`.
  Glue-review clean on U2/U3/U5. Also fixed a pre-existing `AmbientMonitoringServiceTest` flake (seed
  `serviceEnabled=true` before `ACTION_START` so the D-140 background guard can't race the assertion).
- 2026-07-07 — owner-completed (no code): **H4** (D-135) Dependabot security updates + private vulnerability
  reporting, and **H5** (D-137) fdroiddata `Binaries:`/`reproducible: yes` — the "Owner actions pending"
  block is now empty.
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
