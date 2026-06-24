# STATE (rebuild) — frozen cross-session memory from the migration

> **Frozen historical archive.** Cross-session memory from the Tasker→Kotlin migration
> (program complete, v1.0.0, Gate 3 signed off 2026-06-23): segment log S0–S14, the layered
> "Current state" punch-list history, gate findings, and check results. The deviations ledger
> that lived here was split into `DEVIATIONS_LEDGER.md`. For ongoing maintenance state see the
> live `docs/rebuild/STATE.md`.

---

# STATE — cross-session memory for the rebuild program

Every session: read this first; append/update before your final commit. Keep entries terse and
factual. This file is the ONLY shared memory between sessions — if it isn't written here, the
next session does not know it.

## Segment log

| Segment | Date | Model | Status | Commit | Notes |
|---|---|---|---|---|---|
| S0 scaffolding | 2026-06-11 | Fable (planning session) | DONE | (this commit) | CLAUDE.md, RUNBOOK, recipes, checklist, SDK script, hook authored. No source/gradle changes. |
| S1 extraction A | 2026-06-11 | Opus/high | DONE | (this commit) | 40 Java blocks decoded; 28 task docs; profiles.md (753–761,769); pipeline_spec.md; defaults_audit.md (125 vars); INDEX.md. Docs-only. Java "Extracted" checklist cells flipped. |
| S2 extraction B | 2026-06-11 | Opus/medium | DONE | (this commit) | task090_dynamic_scale.md (+solar answer), task038_curve_wizard.md, contexts_spec.md, features_spec.md, 20 scene docs + 4 _disp fragments, screen_map.md (450-element matrix → 9 M3 screens). Docs-only. Scene/context-profile/non-pipeline-cluster checklist cells annotated "S2 extracted". |
| S3 toolchain | 2026-06-11 | Sonnet/medium | DONE | (see push) | Gradle 8.14.3 wrapper; D-007 fixed (pluginManagement); libs.versions.toml (Kotlin 2.0.21, AGP 8.7.3, compose-bom 2024.12.01); :platform → com.android.library; :data retired (git rm); res/ created; manifest updated (specialUse FGS + all permissions); lint-baseline.xml frozen. Pre-existing compile bugs fixed (D-019). :app:assembleDebug ✅ :platform:test ✅ :app:lintDebug ✅; :domain:test 4/5 pass (1 pre-existing parity failure — rapidLuxSpike, D-019, S4/S5 fix). |
| S3.5 errata (owner review) | 2026-06-11 | Fable | DONE | (this commit) | Owner-review corrections folded into extraction docs + CLAUDE.md (D-020…D-026): branch policy settled; And2/Or2 rule validated + alphabetical-XML-ordering trap found (prof758 bool sequence fixed); prof759/769 semantics corrected; debug = 10 named categories; 590=Variable Split, 105=Set Clipboard; %AAB_Test = wizard report→clipboard; non-AAB globals censused; Circadian Dimming Graph re-homed; 168-anonymous-task census added (tasks/anonymous_handlers.md) + task545 doc. Docs-only — build untouched. |
| S3.6 plan hardening (LLM peer review) | 2026-06-12 | Fable | DONE | (this commit) | 6 of 8 review findings adopted, 2 adopted-with-correction (D-027): S9→S9a+S9b split (Gate 1 after S9b; S10/S11 preconds updated); binding runtime concurrency model (drop-not-queue, MainLoop=mutex); S8 preconds += S2; S4 code-547 expression transcription protocol; hardcoded profile gates + truth-table test; S12 step-0 handler triage; S11 theme-workaround revisit. RUNBOOK + CLAUDE.md updated. Docs-only — build untouched. |
| S4 reference impl + golden vectors | 2026-06-12 | Opus/medium | DONE | (see push) | `TaskerReference.kt` (12 Java-faithful blocks: 554/535/544/546/548/659/661/543/696/698/618 + Math.round/BigDecimal helpers); `GoldenVectorGenerator.kt` (regen via `-DregenGolden=1`); 8 committed golden CSVs (smoothing 16512, taper 1148, animation 927, mapping/threshold 688, formulae 540, transition 680, dimming 510 rows). `CorePipelineParityTest.kt` asserts current engine vs vectors @1e-9; 661-vs-663 cross-validation PASSES (Form2D≡Zone1End). 7 gaps found (D-028) → `parity_gaps.md`, 7 `@Ignore("S5: gap-NN")`. `:domain:test` GREEN. Added a `tasks.withType<Test>` regen-property passthrough to `domain/build.gradle.kts`. |
| S5 domain engine parity | 2026-06-12 | Sonnet/high | DONE | (see push) | All 7 parity gaps closed; 0 @Ignore remain. R1 fix: `roundN` now uses `Math.round` (gap-04/05/06); `smoothLux` final rounding uses BigDecimal HALF_UP (gap-01 R1); `absoluteThresholds` uses BigDecimal HALF_UP (gap-02 R1). R2 fixes: removed `coerceIn` from `luxAlpha` (gap-01), added `par1<0.2` special-case to `absoluteThresholds` + added `currentLux` param (gap-02), removed clamp+`coerceAtLeast` from `mapLuxToBrightness` (gap-03). gap-07: test fixture fixed. New files: `BrightnessFormulae.kt`, `SoftwareDimming.kt`, `OverrideRules.kt`, `InitialBrightness.kt`. Defaults corrected (AnimationConfig 20/25/65ms; ThresholdConfig.threshMidpoint 4.0). Follow-on (F1–F5, D-030): task700/646/647 oracle functions + superdimming.csv (2016 rows) + CorePipelineParityTest parity tests; OverrideRules.recordOverridePoint scalingUse param + newest-first order fix; OverrideRulesTest.kt + InitialBrightnessTest.kt added; parity_gaps.md + checklist updated. `:domain:test :app:assembleDebug` GREEN. |
| S6 circadian solar + curve wizard | 2026-06-12 | Sonnet/high | DONE | (see push) | `SolarTimes.kt` (NOAA solar calculator + buildScheduleWindows — SolarCalculator.compute/buildScheduleWindows); `DynamicScaleEngine.kt` (tanh ramp + progress, absorbs computeDynamicScale+rampProgress from BrightnessEngine — BigDecimal HALF_UP parity fix D-031); `CurveSuggestionEngine.kt` (AAB Curve Fitting Engine V43.8 — full ~600-line port of task38 + applyToLiveCurve from task655). BrightnessEngine now delegates computeDynamicScale to DynamicScaleEngine (rampProgress removed). TaskerReference.kt extended with solarTimes/buildScheduleWindows/dynamicScale wrappers. GoldenVectorGenerator gains writeCircadian (576 rows) + writeWizard (12 rows). New parity tests: CircadianParityTest.kt (solar times + schedule windows + dynamic scale + 4 polar assertions) + WizardParityTest.kt (12 scenarios). Total: 50 tests, 0 @Ignore. `:domain:test` GREEN. |
| S8 settings schema v2 + validator | 2026-06-12 | Sonnet/medium | DONE | (see push) | `AabSettings` v2 (animSteps, thresholdMidpoint, contextOverride, setupTitle added; scale Int→Float; throttleDefaultMs 1000→1310; debugLevel range 0..9; CURRENT_SCHEMA_VERSION=2); `AabSettingsSerializer` migration v1→v2; `AabSettingsMapper` completed (toThresholdConfig/toAnimationConfig/toBrightnessCurveConfig/toDynamicScalingConfig + validate fixes); `TaskerLegacyProfileSerializer` updated (new fields + scale Float); `DefaultProfiles.kt` (5 profiles from task592); `SettingsValidator.kt` (5 rules: task583×3 advisory + task707×2 safety); `ContextOverrideRules.kt` (ContextRule/ContextTriggers/BatteryTrigger/LocationTrigger/ContextOverrideConfig + Tasker JSON interop); 20 new unit tests (migration×6, legacy round-trip×5, validator×9). `:app:testDebugUnitTest` ✅ `:app:assembleDebug` ✅ `:app:lintDebug` ✅ `:domain:test` ✅ |
| S7 platform adapters + privilege | 2026-06-12 | Sonnet/medium | DONE | (see push) | `sensor/LightSensorSource.kt` (TYPE_LIGHT callbackFlow); `brightness/ScreenBrightnessController.kt` (read/write 0–255, OEM range norm via config_screenBrightnessSettingMaximum, suppress-echo hook); `brightness/SecureDimmingController.kt` (reduce_bright_colors via Settings.Secure, ELEVATED-gated); `privilege/PrivilegeManager.kt` (Tier NONE/BASIC/ELEVATED; BASIC=canWrite, ELEVATED=checkPermission; tierFlow; root+Shizuku grant helpers); `privilege/ShizukuGrantGateway.kt` (binder check + permission request stub — exec TODO S11, D-032); `observe/BrightnessObserver.kt` (ContentObserver callbackFlow, null-Handler for synchronous dispatch, self-write filter via suppress-echo); `context/{BatteryStateReader,LocationReader,ForegroundAppMonitor,WifiInfoReader}.kt`. ShizukuProvider added to manifest; shizuku-api added to platform + app deps; shizuku-provider added to app deps. SystemAdapters.kt marked @Deprecated("S9b removes"). Robolectric tests: 19 total (brightness write/read/mode-force, tier-gating, observer dispatch+self-write-filter, LightSensorSource cancel). `:platform:test` GREEN (19 tests); `:app:assembleDebug` GREEN. |
| S8.5 review (Fable→Opus) | 2026-06-12/13 | Fable+Opus | IN PROGRESS | 3c6a585, cd3fd15, (this) | Sequential reviews (one agent at a time per owner). DONE: full acceptance suite green; S7 review → D-034 (suppress-echo redesign, OEM rounding, +8 tests); D-035 model policy (Opus from S9a); checklist unstale'd; **S4/S5 review → D-036** (2 CRITICAL parity holes fixed: task661 ScalingUse=false/%AAB_Scale branch + %AAB_ScaleDynamicCompress surfacing; new calculated.csv golden + 3 tests; existing 8 CSVs byte-identical); **S6 review → D-037** (port verified faithful; fixed oracle-circularity by adding independent SolarInvariantTest [7 astronomical invariants, all pass] + wizard abort test + dawn/dusk golden assertions); **S8 review → D-038** (CRITICAL: contextOverride default true→false [would lock context switching on fresh install]; fixed 2 vacuous safety-validator tests). All four reviews (S4/S5, S6, S7, S8) COMPLETE. Build green. S9a may proceed (Opus per D-035). |
| S9a runtime core | 2026-06-13 | Opus/high | DONE | (see push) | Runtime pipeline rebuilt (D-039). New: `runtime/ProfileGates.kt` (hardcoded prof760/758/755 ConditionList booleans, D-021 provenance); `PipelineState.kt` (single runtime-state holder + PipelineEvent sealed type); `AnimationRunner.kt` (task696 per-frame write + read-back override detect, suppress-echo); `OverrideMonitor.kt` (BrightnessObserver→OverrideRules prof755 gate); `BrightnessPipelineController.kt` (single-coroutine pipeline, drop-not-queue MainLoop mutex via AtomicBoolean, sensor→gate→throttle→BrightnessEngine.evaluate→animate, override/pause/resume, hibernate/reinit/panic); `AmbientMonitoringService.kt` REBUILT (ServiceCompat specialUse FGS, live lux/target notification + Pause/Resume/Reset/Disable actions, dynamic SCREEN_ON/OFF receiver→reinit/hibernate). Tests: ProfileGatesTest (prof760+758+755 truth table), AnimationRunnerTest (4), BrightnessPipelineControllerTest (first-run/throttle/mid-cycle-drop/override/resume/hibernate, 5), AmbientMonitoringServiceTest (Robolectric foreground notif + lifecycle, 2). Full ladder GREEN: `:app:assembleDebug :app:testDebugUnitTest :domain:test :platform:test :app:lintDebug`. Legacy fakes still present (S9b rips out). No compaction. |
| S9b runtime features + legacy rip-out | 2026-06-13 | Opus/medium | DONE | (see push) | Super dimming + QS tile + boot start wired; legacy graph deleted (D-040). New: `runtime/SuperDimmingCoordinator.kt` (DimmingCoordinator iface + NoOp + SuperDimmingCoordinator — engages task646 `SoftwareDimming.dimShell` → `AndroidSecureDimmingController` reduce_bright_colors when tier ELEVATED ∧ dimmingEnabled ∧ target<DimmingThreshold; disengages above threshold/task645); `runtime/BrightnessTileService.kt` (QS tile toggles serviceEnabled + start/stop FGS via AutoBrightnessRuntime). BrightnessPipelineController gains optional `dimming` param: `dimming.apply(target,settings)` from the cycle + setInitialBrightness; `dimming.disengage()` on override/pause/panic/hibernate. AppModule REWRITTEN as real DI root (`createController(scope)` composes S7 adapters + S9a pipeline + S9b dimming, shared brightness instance D-034); AmbientMonitoringService uses it. MaintenanceWorker stripped of the toy use case (health heartbeat + service re-ensure only). RIP-OUT: `git rm` BrightnessPolicyEngine, EvaluateAndApplyBrightnessUseCase, Ports.kt, SystemAdapters.kt, WebViewGraphFallback.kt, PermissionOnboardingStateMachine.kt. Manifest: QS_TILE service (BIND_QUICK_SETTINGS_TILE). Tests: SuperDimmingCoordinatorTest (6, tier-gated engage/disengage), BootCompletedReceiverTest (2, Robolectric service-start intent + non-boot ignore), BrightnessTileServiceTest (instantiation smoke — Robolectric can't bind a tile). Rip-out grep empty. Full ladder GREEN: `:domain:test :platform:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`. No compaction. **GATE 1 READY.** |
| Gate 1 punch-list (findings triage) | 2026-06-13 | Opus/high | DONE | (see push) | Triaged the 6 human Gate-1 findings (D-041). Fixed 3 genuine runtime bugs + 1 sub-bug: **G1-F1** crash — `AndroidScreenBrightnessController.write/forceManualMode/restoreMode` + `AndroidSecureDimmingController` now swallow `SecurityException` (unprivileged install degrades, no process crash); MainActivity requests POST_NOTIFICATIONS at launch; service notification shows a "Grant Modify system settings" hint when `!canWrite`. **G1-F3** Disable/UI desync — SettingsViewModel now collects `settingsDataStore.data` as source of truth so the notification's serviceEnabled=false propagates to the toggle. **G1-F4** panic/resume zombie — task528 panic is a FULL STOP not a pausable state: `controller.emergencyStop()` (restore 255 + drop dimming + cancel jobs) → service persists serviceEnabled=false + stopForeground/stopSelf (removed PipelineEvent.Panic/panicInternal). **G1-F5** sub-bug — AppModule tierProvider now `refresh()`es each cycle so a post-start Shizuku/ADB grant is seen. **G1-F2/F5 deferred to S12 (owner decision):** DetectOverrides + DimmingEnabled default Off (Tasker task570 parity, defaults_audit confirmed) and have no UI until S12 — expected-not-bugs. New test: BrightnessPipelineControllerTest.emergencyStop_restoresMaxBrightnessAndFullStops. Full ladder GREEN (59 app unit tests). No compaction. |
| S10 context override engine | 2026-06-13 | Opus/medium | DONE | (see push) | Context system ported (D-042). Domain: `context/ContextOverrideResolver.kt` (pure task43 PASS3/4 — match+rank precedence priority→specificity→array-order, overnight time ranges w/ yesterday membership, SUNRISE/SUNSET tokens, haversine location gate, nextContextTime HH.MM) + `context/ContextModel.kt` (ContextRuleSpec/ContextSignals/ContextResolution); 21-case 1:1 matrix test. App: `runtime/ContextEngine.kt` (PASS1 per-caller cooldown + PASS2 signal-change veto + %AAB_ContextState, applies override by swapping the ENTIRE profile via mergeProfile = task626 39-key snapshot, fires onContextChanged→task43 act21 re-init); `runtime/AndroidContextSignalSource.kt` (S7 readers + Calendar day/seconds + SolarCalculator local sunrise/sunset); `runtime/AppProfileCatalog.kt` (built-in profiles; S12 extends); `settings/ContextRuleStore.kt` (task623 upsert/delete CRUD over new contextRulesDataStore) + `ContextRulesSerializer.kt` + `ContextRuleMapping.kt` (app→domain spec + signal tokens). Wired: AppModule→createRuntime composes engine+pipeline (settingsProvider=engine.effectiveSettings); BrightnessPipelineController gains ContextChanged event→reapplyProfile; AmbientMonitoringService starts engine, screen on/off→engine, pipeline-tick→time re-eval, notification subText = active context. Tests: ContextOverrideResolverTest(21), ContextEngineTest(5, fakes), ContextRuleStoreTest(5). Full ladder GREEN: `:domain:test :platform:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`. No compaction. |
| S11 UI shell + onboarding + dashboard | 2026-06-13 | Opus/medium | DONE | (see push) | M3 nav shell over the screen_map target set rebuilt (D-043). New: `navigation/AppRoute.kt` (enum: Dashboard/Onboarding real + 8 S12/S13 placeholders) + rewritten `NavGraph.kt` (first-run routing → Onboarding when tier==NONE) + `ui/screens/PlaceholderScreen.kt`; `ui/theme/Theme.kt` (M3 dynamic color + DayNight); rebuilt `ui/screens/DashboardScreen.kt` (stateless `DashboardContent` + VM wrapper: live lux/smoothed/current→target, master switch, pause/resume, active-context line, tier badge→onboarding, health) driven by `state/DashboardViewModel.kt` + `state/DashboardState.kt` + new `runtime/LiveRuntimeState.kt` (process-wide bridge from the service's pipeline StateFlow to the UI); `ui/onboarding/OnboardingScreen.kt` (stateless `OnboardingContent` + wrapper: POST_NOTIFICATIONS → WRITE_SETTINGS w/ onResume canWrite re-check → optional ELEVATED [adb-copy/Shizuku/root + live tier] → usage-access shown only when app rules exist). **Shizuku grant exec completed (closes D-032):** AIDL `IShizukuUserService` + `ShizukuUserService` (bound user service execs `pm grant WRITE_SECURE_SETTINGS`) wired through `ShizukuGrantGateway.requestGrant`→`PrivilegeManager.requestShizukuGrant(onResult)`; platform `buildFeatures.aidl=true`; NO java reflection (owner caution). Service republishes pipeline state to LiveRuntimeState + resets on teardown. themes.xml simplified to minimal DayNight no-actionbar platform parent (D-027g resolved). RIP-OUT of toy UI: deleted SettingsState/SettingsViewModel + 3 toy screens; SettingsStore/AabSettingsMapper decoupled from the toy SettingsState (readRawSettings().serviceEnabled at 4 call sites). New deps: compose ui-test-junit4/manifest. Tests: `UiShellTest` (3 Robolectric compose). Full ladder GREEN: `:app:assembleDebug :app:testDebugUnitTest`(72) `:platform:test :domain:test :app:lintDebug`. No compaction. |
| S12 settings/tools/profile screens + chart engine | 2026-06-13 | Opus/medium | DONE | (see push) | The 7 parameter/tool/profile placeholder screens filled + the reusable chart engine landed (D-044). **Step-0 triage** committed first: `anonymous_handlers.md` 168 rows bucketed (a) trivial-chrome / (b) settings-mutation (both bulk-dropped w/ shared reasons) / (c) ~30 complex behaviors → explicit port list. New: `state/SettingsViewModel.kt` (DataStore-as-truth, advisory SettingsValidator errors, reset/applyProfile/replaceAll) + `state/ContextsViewModel.kt` (rule CRUD + installed-app picker); `ui/components/{SettingsControls,SettingsScaffold}.kt` (NumberSettingField w/ red-error supportingText, SwitchSettingRow, DerivedReadout, back-nav scaffold); `ui/graph/ChartCanvas.kt` (generic axes/ticks/log10/multi-series/markers engine — the S13 template base) + `ui/graph/BrightnessCurveChart.kt` (THE template instance) + `ui/screens/ChartPlaceholder.kt` (deferred-S13 slots); screens `CurveBrightnessScreen` (fields + validator + live form2A/3A + curve chart), `ReactivityScreen` (thresholds + **DetectOverrides toggle, G1-F2**), `AnimationDimmingScreen` (anim + derived throttle + **ELEVATED-gated DimmingEnabled, G1-F5** + PWM), `DynamicScaleScreen` (scaling/taper + task517/674/689 warnings), `ContextsScreen` (rule CRUD), `ToolsScreen` (wizard runner + 10-label debug selector + calibration entry), `ProfilesScreen` (apply/reset + Create/OpenDocument import-export incl. legacy). NavGraph wires all 7 (About → S13 placeholder). Tests: `SettingsScreensTest` (5 — validator→UI form2C error, safety banner, DetectOverrides edit, dimming tier-gate, debug label). Acceptance ladder GREEN: `:app:testDebugUnitTest`(77) `:app:assembleDebug :app:lintDebug`. No compaction. **GATE 2 READY.** |
| S12.5a design language + app shell | 2026-06-13 | Opus/high | DONE | (see push) | UI-layer reskin to AAB identity (D-046, Gate-2 G2-F18). New: `ui/theme/Color.kt` (teal+gold palette, per-value provenance from extraction — about.md L51 + the "on" indicator dots/Flash overlays) + rewritten `ui/theme/Theme.kt` (static AAB dark-first/light `ColorScheme`, dynamic colour now opt-in OFF, DayNight kept); `ui/components/AppShell.kt` (`AabTopBar` branded teal header w/ hamburger + `AabNavDrawer` = Compose rebuild of the AAB Menu scene menu.md/L4462: gold-sun teal banner + grouped destinations Profiles&Contexts / Settings / Info&Help, current-route highlight, Recheck Permissions→Onboarding, Chart.js License dropped). `DashboardScreen` rewritten: flat OutlinedButton nav list (nav_* tags) replaced by the drawer; Profiles + Contexts surfaced as prominent **hero cards** (gold-iconed, clickable). New dep `androidx.compose.material:material-icons-core` (from BOM, no version) — declared in libs.versions.toml + app build.gradle. UiShellTest extended (+2: drawer navigates to every route via OnClick semantics; hero cards navigate). Scope kept to identity+nav — field behaviour/sliders/grouping untouched (those are S12.5b). Full ladder GREEN: `:app:assembleDebug :app:testDebugUnitTest`(81) `:app:lintDebug :domain:test :platform:test`. No compaction. |
| S12.5b interaction model (preview→Apply, sliders, grouping) | 2026-06-13 | Opus/high | DONE | (see push) | Ported AAB's temporary-preview→Apply editing model + bounded sliders + faithful Misc grouping + validation parity (D-047; addresses G2-F1/F2/F3/F4/F5/F6/F7/F10/F11/F13/F16). New `state/DraftSettingsViewModel.kt` (per-screen NavBackStackEntry-scoped draft: edit→draft only, **Apply** commits draft→DataStore + forces re-eval, **Discard**/dirty-back reverts; `epoch`-seeded fields fix mid-edit corruption G2-F7; advisory errors on the draft). `SettingsControls.kt`: seed-once `NumberSettingField` w/ committed `[bracket]` + empty-allowed (G2-F1/F7), new bounded `IntSliderSettingField` (G2-F3/F13), `DraftApplyBar` + `DraftSettingsScaffold` (Apply/Discard + dirty-back confirm). **6 sliders w/ exact Tasker ranges** (misc_settings.md / experiment_settings.md): MinBright 0–75, MaxBright 150–255, AnimSteps 0–100, MinWait 1–99, MaxWait 2–100, TaperMidpoint 130–240. New **Misc** screen (`AppRoute.Misc`, drawer Settings group, NavGraph wired) holds min/max sliders + offset/scale + anim sliders + derived throttle + notifications + the 10-label debug selector (moved off Tools) — the G2-F2 regrouping; Curve & Brightness now only the curve-zone coefficients, Animation & Dimming only super-dimming+PWM (mutually exclusive, G2-F10) + circadian-gated dim spread (G2-F11). Forced re-eval path: `AutoBrightnessRuntime.reapply`→service `ACTION_REAPPLY`→`controller.reapply()` (UNLIMITED ContextChanged event), gated on serviceEnabled; SettingsViewModel applyProfile/reset/replaceAll reapply too (G2-F16). Validator: +zone2End<zone1End NaN guard (G2-F6) + dangerously-low-scale (G2-F5); BrightnessCurveChart floors at minBrightness (G2-F4). Tests: `DraftSettingsViewModelTest`(3, real DataStore — edit/dirty/discard, apply commits, serviceEnabled preserved) + `SettingsScreensTest` rewritten (8: validator errors, draft bracket, slider ranges asserted, Apply/Discard wiring, debug label on Misc). Full ladder GREEN: `:app:assembleDebug :app:testDebugUnitTest`(85) `:app:lintDebug :domain:test :platform:test`. No compaction. |
| S12.5c feature & behaviour fidelity | 2026-06-14 | Opus/high | DONE | (see push) | Closed the remaining Gate-2 behaviour gaps (D-048). **G2-F8** profile-load-disables-overrides FIXED: `detectOverrides` is a global preference, not a task626 snapshot key → preserved in `mergeProfile` + `SettingsViewModel.applyProfile/replaceAll`. **G2-F12** toasts restored via `ui/components/Toaster.kt` (`rememberToaster`): Apply (shared `DraftApplyBar`), profile apply/reset/import-export, context save/delete, wizard apply/copy. **G2-F14** context-rule editor fidelity: manifest `<queries>` LAUNCHER block + app icons (`AppEntry.icon`, core-ktx `toBitmap`), "use current Wi-Fi" (`WifiInfoReader.currentSsid()`), SUNRISE/SUNSET time tokens, usage-access prompt+deep-link when an app trigger is set. **G2-F15** runtime debug toasts: new `runtime/RuntimeDebug.kt` (`DebugCategory`×9/`DebugSink`/NoOp) + `ToastDebugSink` wired into the pipeline (LIGHT_EVAL/ANIMATION_DETAILS/DYNAMIC_SCALE/GRAPH_METRICS + SKIP_ANIMATIONS behaviour), SuperDimmingCoordinator (SUPER_DIMMING), ContextEngine (CONTEXT_AUTOMATION/LOCATION), gated on the live debugLevel; `%AAB_Test` wizard report → clipboard in Tools. **G2-F9** super-dimming: engagement logic verified + now driven by the S12.5b reapply; added a SUPER_DIMMING debug toast (engage / why-not) + precise AOSP-secure-key/OEM-variance note for the device gate (no logic bug found). **G2-F17** QS tile subtitle = Off/Active/Paused/Starting from LiveRuntimeState. New tests: RuntimeDebugTest(3), ContextEngineTest.mergeProfile_preservesDetectOverrides, SettingsScreensTest +2 (context editor SUNRISE/SSID + usage-access prompt). Full ladder GREEN: `:app:assembleDebug :app:testDebugUnitTest`(91) `:app:lintDebug :domain:test :platform:test`. Lint baseline unchanged. No compaction. **GATE 2 RE-TEST READY.** |
| S12.6a IA & naming | 2026-06-14 | Opus/high | DONE | (see push) | Menu-as-home reshape + two renames + Dashboard last-sample fix (G2R-F1…F5). **AAB Menu promoted to a real home hub** (`AppRoute.Menu` + `ui/screens/MenuScreen.kt`): gold-sun teal banner (rebranded "Tideo Auto Brightness", not "Advanced"), Profiles/Contexts **hero cards moved off the Dashboard**, Settings/Info&Help nav groups; it is the start destination after onboarding (tier!=NONE→Menu) and the back-target from every settings/tool screen via new `NavHostController.navigateTopLevel` (popUpTo(menu)). The S12.5a `AabNavDrawer` retired; `AabTopBar` gained an optional back arrow (AutoMirrored). Dashboard slimmed to live-status (tier badge, master switch, live readout, health) + back→Menu. **Renames:** `AnimationDimming`→`SuperDimming` (route `super_dimming`, title "Super Dimming", G2R-F3); `DynamicScale`→`Circadian` (route `circadian`, title "Circadian", G2R-F4) — files `AnimationDimmingScreen.kt`→`SuperDimmingScreen.kt`, `DynamicScaleScreen.kt`→`CircadianScreen.kt` (composables + content fns + NavGraph + tests). **Last-sample fix (G2R-F5):** `PipelineState.lastSampleMs` recorded for every delivered sample in `onSensorSample` (atomic StateFlow.update), surfaced via LiveRuntimeState→DashboardUiState, rendered as relative "Xs ago" (replaces the never-written health-store source). Domain/golden/ChartCanvas untouched. Tests: `UiShellTest` rewritten (Menu hero+nav, renames resolve, start-on-Menu + back-from-settings→Menu, last-sample renders); `SettingsScreensTest` SuperDimming rename. Ladder GREEN: `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug`. No compaction. |
| S12.6b glass-box diagnostics + Live Debug scene | 2026-06-14 | Opus/high | DONE | (see push) | The runtime "glass box" surfaced (G2R-F6…F10). New: **Live Debug Info** scene (`AppRoute.LiveDebug`, in the Menu Info&Help group + `navigateTopLevel` back→Menu) = `LiveDebugScreen`/`LiveDebugContent` over `LiveDebugViewModel` (combines LiveRuntimeState pipeline/context/running + DataStore min/max/debugLevel) — live `%AAB_*` readout grouped per debug.md HTML cards (Core Metrics / Circadian & Scale / System Status / Performance & Timings), gold `#FFC107` values via new `ui/components/DiagnosticCard.kt` (`DiagnosticCard`/`DiagnosticLine`/`goldValue`). **Per-screen DiagnosticCards** (G2R-F7/F8): `ReactivityDiagnosticCard` (threshold + dead zone) + `CircadianDiagnosticCard` (uncompressed vs true scale + min/max) embedded on those screens; stateless `*Content(state)` builders for tests. To feed them, `PipelineState` gains `threshDynamic` (%AAB_ThreshDynamic) + `scaleDynamic` (%AAB_ScaleDynamic uncompressed), populated in `runCycle` from existing `BrightnessPolicyOutput.dynamicThreshold`/`scaleDynamic` (no domain API change). **Debug selector → global + relocated** (G2R-F9): moved off Misc to Live Debug (`LiveDebugViewModel.setDebugLevel` writes DataStore directly); `debugLevel` now preserved across `SettingsViewModel.applyProfile/replaceAll/resetDefaults` + `DraftSettingsViewModel.apply`/re-sync (already in `mergeProfile`) — `DEBUG_LABELS`/`DebugLevelSelector` moved MiscScreen→LiveDebugScreen. **Teal debug toasts** (G2R-F10): `ToastDebugSink` builds a teal-rounded custom TextView (via `makeText`+`setView` so ShadowToast still records text). Tests: SettingsScreensTest (Live Debug + Reactivity/Circadian diagnostic cards render seeded PipelineState; selector relocation) + new `SettingsViewModelTest` (debugLevel survives profile apply + reset) + ContextEngineTest `mergeProfile_preservesDebugLevel`. Full ladder GREEN: `:app:testDebugUnitTest`(102) `:app:assembleDebug :app:lintDebug :domain:test :platform:test`. No compaction. **NEW FINDING recorded (verified, deferred to S12.6c): G2R-F27/D-050 — PWM-sensitive mode never locks the hardware brightness floor (`pwmSensitive` unread by the runtime).** |
| S12.6c pipeline behaviour correctness | 2026-06-14 | Opus/high | DONE | (see push) | Fixed the runtime bugs the re-test found + wired override-point capture (D-051; G2R-F11/F12/F13/F14/F26/F27). **G2R-F11/F12** (Apply/profile-load + min-brightness ignored until a light change): root = the pipeline's `settingsProvider`=`ContextEngine.effectiveSettings()` served the STALE cached `_effective` snapshot. Added `ContextEngine.reevaluate()` (re-reads the FRESH baseline + re-merges the active profile, no watcher re-resolution) and the service's `ACTION_REAPPLY` now calls it BEFORE `controller.reapply()` → manual edits take effect immediately (min-bright no longer "stuck at 10"). **G2R-F13** override-point capture (closes D-044c): new `OverridePointStore` (DataStore, newest-first cap 50) + `OverridePointSink`; `handleOverride` persists the de-compressed point; `SettingsViewModel`/`DraftSettingsViewModel` expose `overridePoints`; Tools wizard reads the recorded set. **G2R-F14** `BrightnessCurveChart` overlays the recorded points as dots + shows the fitted/suggested curve only at ≥9 points (ChartCanvas unchanged). **G2R-F26/D-049** override false-positives: `handleOverride` now does the task567 act8 settle (wait %AAB_CycleTime → re-read → only pause if still ≠ our last applied) + the AnimationRunner read-back is now device-exact (`ScreenBrightnessController.isOnScreenSelfWrite`, kills OEM round-trip drift, D-049 #4). **G2R-F27/D-050** PWM-sensitive hardware floor: `applyPwmFloor` clamps the hardware write up to `dimmingThreshold` when `pwmSensitive && target<threshold` (task698 step3) in runCycle + setInitialBrightness. **HARD FENCE honoured: domain/, golden vectors, ChartCanvas API untouched.** New tests: controller minBrightness/PWM-floor/override-false-positive (3), ContextEngine reevaluate-fresh-baseline, OverridePointStore (3). Full ladder GREEN: `:platform:test :app:testDebugUnitTest`(104) `:domain:test :app:assembleDebug :app:lintDebug`. No compaction. |
| S12.6e labels/help audit + context editor + onboarding | 2026-06-14 | Opus/high | DONE | (this push) | Last S12.6 sub-segment (D-053; G2R-F19…F25 + F28/F29). **Label + verbatim long-press-help audit** (G2R-F19/F20/F21): new `TaskerHelp.kt` holds the 30 verbatim Flash help strings (decoded from the reactivity/brightness/misc/superdimming scene `longclick` tasks via XML_RECIPES R2); `NumberSettingField`/`IntSliderSettingField`/`SwitchSettingRow` gained a `help=` param surfaced via an "ⓘ" reveal (Tasker longtap → tap, `helptext_<tag>`); every control on Reactivity/Curve/Misc/SuperDimming relabelled to the Tasker scene labels + the verbatim help wired. **WIRING AUDIT (G2R-F20): the owner-flagged "delta factor" was MIS-LABELLED/MIS-HELPED, not mis-wired** — `%AAB_DeltaFactor` IS the sensor-smoothing alpha factor (`luxAlpha=1-exp(-deltaFactor·effectiveDelta)`, BrightnessEngine); relabelled "Smoothing Δ" + verbatim help. No other binding bug found; all field→`%AAB_*` bindings cross-checked vs AabSettingsContract. **Context Wi-Fi/location (G2R-F22):** `WifiInfoReader.currentSsid()` is now a `suspend` returning a typed `SsidResult` (one-shot `NetworkCallback` w/ `FLAG_INCLUDE_LOCATION_INFO`+2s timeout; targeted NotOnWifi/NeedsLocationPermission/LocationServicesOff/Unknown messages — fixes the API-29+ `<unknown ssid>` redaction); rule editor gained lat/lon/radius fields + "Use current location" (AndroidLocationReader). **Time picker (G2R-F28):** From/To open the M3 `TimePicker` modal (SUNRISE/SUNSET tokens kept). **Usage access (G2R-F23/F24):** onboarding always shows the usage step, labelled "(optional)" by default / "(needed for per-app rules)" once an app rule exists; the rule-editor grant prompt was already present. **Toast on load (G2R-F25):** new `ContextLoadSink`/`ToastContextLoadSink` → unconditional teal-less toast when a runtime context rule loads its profile; Profiles-screen apply already toasted. **Live Debug Performance & Timings (G2R-F29):** `PipelineState` gains `luxAlpha`/`animationSteps`/`animationWaitMs`/`throttleMs`/`lastUpdateMs` (populated in runCycle from existing engine output, no domain change) → Live Debug card now shows LuxAlpha / cycle / reactivity-cooldown / last-animation (steps×wait) / last-update. **HARD FENCE honoured: domain/, golden vectors, ChartCanvas untouched.** Tests: SettingsScreens +5 (delta-factor verbatim help reveal, time-picker modal, use-current-SSID fills field, location fields, Live Debug timings) + ContextEngine contextLoad-fires-sink (1). Full ladder GREEN (`:app:testDebugUnitTest`=122 `:app:assembleDebug :app:lintDebug :domain:test :platform:test`). No compaction. **GATE 2 RE-TEST (again) READY.** |
| S12.6d profiles + legacy import + reset + Apply-gate | 2026-06-14 | Opus/high | DONE | (see push) | Real user-profile management + legacy import + validation gate (D-052; G2R-F15/F16/F17/F18/F30 + owner findings F31/F32). **G2R-F15** user-editable profiles: new `UserProfileStore` (DataStore `SavedProfiles`: built-ins seeded once, Save-current-as, overwrite-in-place [keeps built-in flag], delete, **Restore factory profiles**); `AppProfileCatalog` converted object→class reading the store (built-in fallback) so context rules target user profiles too (closes D-042c); `SettingsViewModel` gains saveCurrentAs/deleteProfile/restoreFactoryProfiles/profiles flow; ContextsViewModel.profileNames now a StateFlow off the store. **G2R-F16** legacy SAF import: `LegacyConfigImporter` (OpenDocumentTree grant→takePersistableUriPermission, list `*.json` via DocumentsContract — no MANAGE_EXTERNAL_STORAGE/no new dep) wired into the rewritten ProfilesScreen (link-folder + per-file Load) alongside the single-file picker. **G2R-F17** per-screen reset: `DraftSettingsScaffold` gains an `onReset` TopAppBar action; each of the 5 draft screens resets only its own fields to the task570 baseline + toast. **G2R-F18/D-052** block-Apply: `FieldError` gains `Severity`; the 3 task583 form errors (form2A/3A<0, form2C>zone1End) are CRITICAL; `DraftSettingsViewModel.hasCriticalError` disables `DraftApplyBar` Apply (+ hint) while one stands — sanctioned deviation from Tasker's advisory model. **G2R-F30** manual-load context lock: applyProfile/replaceAll latch `contextOverride=true`; `ContextEngine.reevaluate()` honours the lock (drops active context, runs the manual baseline); Profiles screen shows a Resume banner → `resumeContextAutomation()` clears it + reapplies. **Owner findings during S12.6d:** **G2R-F31** battery % from/to added to the context rule editor (BatteryTrigger min/max; resolver already supported it); **G2R-F32** curve-wizard report was too terse — Tools now shows + copies the FULL `diagnosticsLog` (the engine already produced it; app-layer only). **HARD FENCE honoured: domain/, golden vectors, ChartCanvas untouched.** Tests: UserProfileStoreTest(5), SettingsValidator severity(1), SettingsScreens criticalError-gate/reset/profiles/context-lock/battery(5), ContextEngine reevaluate-lock(1). Full ladder GREEN: `:app:testDebugUnitTest`(116) `:app:assembleDebug :app:lintDebug :domain:test :platform:test`. No compaction. |
| S12.7b runtime feedback surfaces | 2026-06-14 | Opus/high | DONE | (this push) | Notification/QS-tile/super-dimming surfaces fixed (D-055; G2R-F35/F40/F63/F65). **F65 (the real bug):** super dimming never engaged with PWM-sensitive on because `runCycle`/`setInitialBrightness` fed the **PWM-floored** target (raised UP to `dimmingThreshold`) to `dimming.apply`, so `target < dimmingThreshold` was never true → Extra Dim off. Now pass the **un-floored `output.targetBrightness`** (= task646 act1/act2 `%AAB_CurrentBright`); the hardware sits at the PWM floor while the secure `reduce_bright_colors` layer darkens below it (the two layers cooperate; coordinator value `dim_shell` was already correct per task650 act8). **F35:** new `pausedByOverride` PipelineState flag (set in `handleOverride`, cleared by user Pause/Resume) → service posts a **high-priority vibrating override notification** (new `manual_override` IMPORTANCE_HIGH channel + Resume action) **+ toast**, once on the rising edge. **F40:** ongoing FGS notification already toggled Pause↔Resume on `paused`; verified + test. **F63:** `BrightnessTileService` now **live-collects** (serviceEnabled ⊕ LiveRuntimeState running/paused) in `onStartListening`→`updateTile` on every change (cancel in `onStopListening`), and the service calls `TileService.requestListeningState` on each state publish so Off→Starting→Active/Paused renders without reopening the panel; subtitle mapping extracted to a pure `tileSubtitle()`. Tests: controller +2 (un-floored dimming target / pausedByOverride latch), AmbientMonitoringService +1 (high-pri notif + Resume + toast), BrightnessTileService +1 (subtitle mapping). **Fence: domain/ + golden vectors untouched.** Full ladder GREEN: `:app:testDebugUnitTest`(132) `:app:assembleDebug :app:lintDebug :domain:test :platform:test`. No compaction. |
| S12.7c context system: location lifecycle, ordering, legacy targets, days | 2026-06-14 | Opus/high | DONE | (this push) | Context-system parity batch (D-056; G2R-F42/F43/F44/F45/F47/F67/F68). **F45 smart location listener:** `LocationReader.locationUpdates()` (continuous `requestLocationUpdates` NETWORK+GPS, seeds best last-known, filters (0,0) null-island reads) replaces the on-demand passive read that died on backgrounding + read `0.0,0.0`; hosted in the FGS scope; `ContextEngine.startLocationListenerIfNeeded()` gated on `tokens.usesLocation` (Tasker's `[LOC]`-in-ContextCache cost gate — owner-confirmed) + a ≥100 m haversine debounce before firing the LOCATION eval (kills the near-constant/input-blocking toasts); `assemble()` now takes engine-fed lat/lon (new `LocationSignal`). **F42** `currentLocation()` → typed `LocationResult` (NeedsPermission/Unavailable/Available) with a call-time permission recheck + fresh `getCurrentLocation` fix (no longer falsely "not granted" post-grant). **F43** rule list `byPriority()` (highest first, name tie-break) not creation order. **F44** legacy load registers the profile into `UserProfileStore` (file name) → selectable as a rule target without a re-save (`SettingsViewModel.saveImportedProfile`). **F67** day-of-week `FilterChip` picker → `ContextTriggers.days` (resolver/overnight-wrap already supported, domain fenced). **F68** SUNRISE/SUNSET tokens show today's resolved time in theme gold ("Sunrise (06:42)", `ContextsViewModel.solarTimes()`). **F47** Context Automation debug toast enriched: trigger · context · profile · rule (priority). Tests: ContextEngine +2 (location ≥100 m debounce, enriched F47 toast), AppProfileCatalog +1 (legacy target visible), ContextRuleStore +1 (byPriority), SettingsScreens +2 (day picker saves DAY_OF_WEEK, sunrise resolved-time). **Fence: domain/ + golden vectors untouched.** Full ladder GREEN: `:app:testDebugUnitTest`(138) `:platform:test :domain:test :app:assembleDebug :app:lintDebug`. No compaction. |
| S12.7d permissions, Wi-Fi acquisition, first-boot nav | 2026-06-14 | Opus/high | DONE | (this push) | Permissions/SSID/nav batch (D-057; G2R-F33/F41/F57). **F41 (SSID):** ported the real `_GetWifiNoLocation V3` order (task105/633) — `AndroidWifiInfoReader.currentSsid` now tries the no-Location strategies FIRST (`WifiSsidStrategies.kt`: `ShizukuWifiSsidStrategy` runs `cmd wifi status` via new `ShizukuShell.exec` + AIDL `exec(String[])` on the existing user service → `DumpsysWifiSsidStrategy` runs in-process `dumpsys wifi`, parsing the `mWifiInfo`/`COMPLETED` line), and only falls back to the Location-gated `NetworkCallback` last; strategies are constructor-injectable so the source-order is unit-tested. **F41 (perm):** Setup gained a Location step (RequestMultiplePermissions FINE+COARSE, labelled optional). **F33:** onboarding shows a `RestrictedSettingsCard` ("Allow restricted settings" + Open-App-info deep-link) when `isLikelySideloaded` (install source not a known store). **F57:** new `NavHostController.completeOnboarding()` lands on `AppRoute.Menu` with `popUpTo(Onboarding, inclusive)` (was Dashboard) → Back from Menu exits cleanly. **Fence: domain/ + golden vectors untouched.** Tests: `WifiSsidStrategyTest` (9 — source order with fakes + cmd/dumpsys parsers + normalize), UiShell +3 (Location step renders, restricted hint when sideloaded, completeOnboarding→Menu drops Onboarding). Full ladder GREEN: `:platform:test :app:testDebugUnitTest`(140) `:domain:test :app:assembleDebug :app:lintDebug`. No compaction. |
| S12.7a manual-override engine correctness | 2026-06-14 | Opus/high | DONE | (this push) | Ported the REAL task567/task696 override logic (D-054; G2R-F34/F64/F46). **F34:** `AnimationRunner` replaced the exact-match `isOnScreenSelfWrite()` (false-fired on OEM round-trip drift) with task696's band+debounce — band `[minTarget-2, maxTarget+2]` spanning the sweep, override only after **2 consecutive** out-of-band reads (every-frame, since the every-5th was a Tasker IPC optimization). **F64:** `OverrideMonitor` gained a settle-suppression gate; `setInitialBrightness` arms a 1500ms window after each initial self-write so the start/reinit/resume/QS-on observer echo (incl. the AUTO→MANUAL mode-flip recompute) is not flagged as an override; the S12.6c idle-path settle-wait kept. **F46:** manual profile load = override (`LiveRuntimeState.manualOverride` published from `%AAB_ContextOverride` via the service) shown in the Menu; a context rule active is no longer labelled an override (Menu Contexts card relabelled). Tests: AnimationRunner +3 (self-writes complete / opposing-write overridden / single-transient debounced), controller +1 (init-echo suppressed then real-write pauses post-window), UiShell +2 (Menu label semantics). **Fence: domain/ + golden vectors untouched.** Full ladder GREEN: `:app:testDebugUnitTest`(128) `:domain:test :platform:test :app:assembleDebug :app:lintDebug`. No compaction. |
| S12.7e debug / toast infrastructure | 2026-06-14 | Opus/high | DONE | (this push) | Debug/toast surfaces fixed (D-058; G2R-F48/F49/F50/F51/F52). **F50** global flashes: new `AabToastAccessibilityService` (opt-in `AccessibilityService`, `TYPE_ACCESSIBILITY_OVERLAY` window — no `SYSTEM_ALERT_WINDOW`, presentation-only, `canRetrieveWindowContent=false`) registers as the presenter of a new process-wide `AabFlash` channel; manifest service + `res/xml/aab_accessibility_service.xml` + rationale string; Live Debug gained an opt-in card (status + Accessibility-settings deep-link, re-polled on resume). Degrades to the foreground teal toast when off. **F51** cancel-not-stack: ALL flashes (`ToastDebugSink` + `ToastContextLoadSink`) route through `AabFlash.show`, which cancels the previous flash before posting the next (teal styling moved into `AabFlash`). **F52** instant debug-off: `LiveDebugViewModel.setDebugLevel` now `AabFlash.cancel()`s on Off **and** triggers `AutoBrightnessRuntime.reapply` (gated on serviceEnabled) so the pipeline's stale `ContextEngine` effective-snapshot picks up the new category immediately (root cause: `effectiveSettings()` served the cached snapshot). **F48** dynamic-scale timing: new pure `DynamicScaleDebugGate` (fires only ~2 min into a dawn/dusk transition, then ≤ once per 2 min; resets when the time-driven scale settles) gates the `DYNAMIC_SCALE` flash in `runCycle` (transition = `scaleDynamic` changing between cycles) — no longer per light change. **F49** overlay-preview colour: `SuperDimmingCoordinator` now emits `OVERLAY_PREVIEW` (level 6) with the computed black-overlay hex (task653 `dim_alpha_dec=2.55·dim_shell` → task654 `%AAB_HexOverlay`, via golden `SoftwareDimming.dimShell`) on the unprivileged (`<ELEVATED`) below-threshold fallback. **Fence: domain/ + golden vectors untouched.** Tests: RuntimeDebug +4 (gate timing ×2, flash cancel-previous/instant-cancel, foreground fallback), SuperDimming +2 (overlay-preview on unprivileged / none when elevated), SettingsScreens +1 (global-flash card). Full ladder GREEN: `:app:testDebugUnitTest`(147) `:domain:test :platform:test :app:assembleDebug :app:lintDebug`. No compaction. |
| S12.7f per-screen live readouts + label/value fidelity | 2026-06-15 | Opus/high | DONE | (see push) | Live-readout/label batch (G2R-F56/F58/F59/F60/F61). **F58** Tasker live-readout blocks added to the two screens missing them: **Curve & Brightness** (`CurveBrightnessDiagnosticCard*`, brightness_settings.md `current_lux_and_bright` — "Current smoothed lux [%SmoothedLux]" + "Current brightness (min–max) [%AAB_CurrentBright]") and **Misc** (`MiscDiagnosticCard*`, misc_settings.md `current_throttle_and_alpha` — "Current throttle [%AAB_Throttle] ms" + "Current smoothing α [%LuxAlpha]"); Reactivity already had its card. **F56** the Live-reactivity card's dynamic threshold now renders as a **percentage** (new `fmtPercent`, 0.5→"50%"; the bound `%aab_thresh*pc` are percentages). **F59** Reactivity "Dynamic threshold" description now **substitutes the live `%AAB_ThreshDynamic`** value (as %) and sits **behind the ⓘ reveal** (`help=` not `helper=`). **F60** Misc **"Scale" becomes a read-only "(auto — dynamic)" readout** of `%AAB_ScaleDynamicCompress` when circadian scaling is on (editable field otherwise). **F61** `form2A`/`form3A` relabelled **"Zone 2 alignment" / "Zone 3 alignment"**. The 3 screens gained a `live: PipelineState` param (wrappers collect `LiveRuntimeState.pipeline`). **Also (owner msg):** `rememberToaster` now routes through the shared teal `AabFlash` channel so UI confirmations (profile apply/save/import/copy) get the **same AAB-teal styling** as runtime flashes + cancel-not-stack (was plain system toasts). **3 owner findings logged + deferred: G2R-F70/F71/F72.** Tests: SettingsScreens +5 (threshold-%, curve+misc live readouts, F61 labels, F60 auto-scale on/off); existing reactivity-card test updated to %. **Fence: domain/ + golden vectors untouched.** Ladder GREEN: `:app:testDebugUnitTest`(152) `:app:assembleDebug :app:lintDebug :domain:test :platform:test`. No compaction. |
| S12.7g charts & curve view | 2026-06-15 | Opus/high | DONE | (this push) | Curve chart enriched + `ChartCanvas` extended (D-059; G2R-F36/F55/F62/F66/F69). **Merged with S12.7f** (PR #40; CurveBrightnessScreen.kt `live` readout + SettingsScreensTest both kept). **F55** `ChartCanvas` gained `xAxisLabel`/`yAxisLabel` (Lux/Brightness), a `niceTicks` 1/2/5×10ⁿ y-tick generator (0/50/…/250, kills the 191.25 artefact), log-x now sampled from **0.1**→100k, and an opt-in `interactive` drag/tap **scrub** that draws a vertical line + per-series readout box at the touch point (pure `seriesValueAt` interpolation). **F66** opt-in `showLegend` Row above the canvas — solid/dashed swatch + label per series (`legend_Curve`, dashed `legend_Reference`, `legend_Suggested`) + "Overrides" scatter. **F69** new `referenceCurve` param: `CurveBrightnessContent` samples it from the **committed** snapshot (`remember(committed)`) → FIXED dashed-gold reference that does NOT track the draft (replaces the old draft-derived moving "Taper" overlay); the live "Curve" tracks the draft. **F36** override points are now a tappable `ChartScatter` (12px dots, white ring); `ChartCanvas` hit-tests via `nearestIndex` → data-space callback → `OverridePointDeleteDialog` (lux/brightness pair + confirm) → new `OverridePointStore.delete` + `DraftSettingsViewModel.deleteOverridePoint`. **F62** verified the wizard's "Suggested" fit (≥9 pts, task38) draws on the curve view against scatter+reference. New owner finding logged + deferred: **G2R-F73** (`%AAB_ScaleDynamic` <1 at ~07:13, suspected UTC bug; domain-fenced). **Fence: domain/ + golden vectors untouched; ChartCanvas extended (sanctioned for S12.7g only).** Tests: `ChartCanvasTest` (4 — niceTicks/logSpaced/seriesValueAt/nearestIndex, pure JVM), OverridePointStore.delete (1), SettingsScreens +2 (legend Reference/Curve, delete-dialog confirm). Full ladder GREEN: `:app:testDebugUnitTest`(159) `:domain:test :platform:test :app:assembleDebug :app:lintDebug`. No compaction. |
| S12.7h rich editors / scene fidelity | 2026-06-15 | Opus/high | DONE | (this push) | The last S12.7 sub-segment — profile/settings-list fidelity + Circadian date/location (D-060; G2R-F38/F39). **F38** the Tasker dashboard's "full list of every setting w/ gold changed-vs-default": new `settings/SettingsDisplay.kt` (`AabSettings.displayRows(reference)`/`changedCount`/`valueFor` — explicit per-key `when`, NO reflection per owner caution; excludes runtime/identity keys serviceEnabled/contextOverride) + `ui/components/SettingsDiffList.kt` (changed rows in teal-gold `AabGold` + SemiBold, count summary). Wired into a new **`LoadProfileDialog`** ("Load Anyway" preview→Apply modal, replaces the old direct Apply button), the **Save dialog** (shows the live set being saved), and a **"View current settings"** dialog (active-vs-default compare = the dashboard); each ProfileCard now shows its changed-count. **F39** Circadian fixed Date/Lat/Lon element (experiment_settings.md elements35–37, `_ExperimentSetDate`/`_ExperimentClearDate`): new `CircadianDateLocationCard` (M3 DatePicker + lat/lon fields + "Use current location" + "Use live data") backed by new `settings/ExperimentPrefsStore.kt` (`experimentPrefsDataStore` — `%AAB_Date`/`%AAB_Latitude`/`%AAB_Longitude`) via new `state/CircadianExtrasViewModel.kt`; unset → fields pre-fill **today + `lastKnownLocation`**; preview-only (never enters AabSettings/profiles/export). The ExperimentChart remains the S13 host slot. Tests: SettingsScreens +5 (diff-list changed/all-default, LoadProfileDialog confirm, Circadian defaults-to-today+loc, set-fixed emits) + new `SettingsDisplayTest` (3, pure JVM). **Fence: domain/ + golden vectors + ChartCanvas untouched.** Full ladder GREEN: `:app:testDebugUnitTest`(167) `:app:assembleDebug :app:lintDebug :domain:test :platform:test`. No compaction. **ALL S12.7 (a–h) DONE → GATE 2 RE-RE-TEST (4th) READY. S12.7i queued for the during-S12.7 deferrals (F70/F71/F72); F73 needs a domain segment.** |
| S12.7i (F73 first) circadian solar-window wiring | 2026-06-15 | Opus/high | DONE (F73) | (this push) | First S12.7i deliverable: **F73 fixed — and it was APP-LAYER, not the suspected domain bug** (D-061). Root cause: `BrightnessPipelineController.buildInput` built `TimeContext(secondsOfDay=…)` but left every solar window at its **default (6–8am / 18–20pm UTC)** — the real sunrise was NEVER wired (the D-039d "circadian wired in S9b/S12" carryover that never happened). So the dynamic-scale morning ramp ran 06–08 UTC for everyone → `%AAB_ScaleDynamic`=0.852 at **06:13 UTC** irrespective of the device clock (owner saw it at 07:13 @UTC+1 AND 08:13 @UTC+2 — both 06:13 UTC, confirming the frame, not a local bug). Fix: new `runtime/CircadianWindowProvider.kt` (pure `compute(lat,lon,dateEpochSec,tz,factor)` → `CircadianWindows` via the **already-fenced, golden-tested** `SolarCalculator.compute`/`buildScheduleWindows`; stateful `current()` resolves the F39 fixed date/loc override else today + `lastKnownLocation`, day/loc/factor-cached, returns null→keep old defaults when no fix). `buildInput` now feeds the real morning/evening/sunlight/polar fields; `now` stays UTC seconds-of-day to match `buildScheduleWindows`' UTC-frame windows. `BrightnessPipelineController` gains `circadianWindowsProvider` (default `{null}` → existing tests/behaviour intact); `AppModule.createRuntime` wires the live provider. **Verified end-to-end: Utrecht 2026-06-15 sunrise 05:18 local → scaleDynamic 1.15 at 08:13 local (was 0.852).** Tests: `CircadianWindowProviderTest` (4, pure JVM — Utrecht sunrise≈05:18 + daytime 1.15, default-window bug repro 0.852, window ordering/non-polar). **Fence honoured: domain/ + golden vectors + ChartCanvas untouched (only *called*).** Full ladder GREEN: `:app:testDebugUnitTest`(171) `:app:assembleDebug :app:lintDebug :domain:test :platform:test`. No compaction. **S12.7i F70/F71/F72 still remain.** |
| S12.7i (F70/F71/F72) deferral cleanup | 2026-06-15 | Opus/high | DONE | (this push) | The three remaining during-S12.7 deferrals closed (D-062; F73 already landed). **F70 legacy-load-doesn't-apply — root cause was the PARSER, not the wiring:** ProfilesScreen already called `vm.replaceAll(imported)` (commit+reapply) since S12.7c, but `TaskerLegacyProfileSerializer` only parsed `%AAB_Key=value` plaintext, so a REAL Tasker config (nested JSON — `{meta,general,misc,reactivity,circadian,superdimming}`, task637 `_ProfileManager.performSave` XML L29365+) parsed to all-defaults → "loaded by name", nothing changed. Rewrote the serializer to detect `{`-JSON and map the snake_case keys per task637 `performLoad` (L29490+); derived form2A/2D/3A intentionally NOT stored (recomputed at read-time, ledger). `%AAB_Key=value` fallback kept. **F71 reactivity-cooldown-swallows-overrides:** transcribed task544 (throttle gate `elapsed<%AAB_Throttle`→`%AAB_MainLoop=0`→Stop is the MAIN-LOOP gate, prof760) vs task567 (prof755 override handler: act7 "Wait %AAB_CycleTime", act8 re-gate) — the throttle gates ONLY the task544 main loop; prof755→task567 override detection is a SEPARATE profile. `handleOverride`'s settle fallback to `throttleDefaultMs` was the lone conflation; changed to `%AAB_CycleTime` only (0 when unset) so an override is detected inside the cooldown window. **F72 can't-clear-a-time-rule:** added a "Clear time" affordance in the context rule editor that blanks From/To → `ContextTriggers.timeRange` saves null. **Fence: domain/ + golden vectors + ChartCanvas untouched.** Tests: LegacyImportRoundTrip +2 (nested-JSON full parse, partial-section defaults), SettingsViewModel +1 (replaceAll commits parsed legacy values + triggers ACTION_REAPPLY), BrightnessPipelineController +1 (override settle not gated by a 60s throttle), SettingsScreens +1 (Clear time nulls timeRange). Full ladder GREEN: `:app:testDebugUnitTest`(176) `:app:assembleDebug :app:lintDebug :domain:test :platform:test`. No compaction. **ALL S12.7 (a–i) DONE → GATE 2 RE-RE-TEST (4th) READY.** |
| S12.8a runtime: override feedback, throttle, panic, PWM-dimming | 2026-06-16 | Opus/high | DONE | (this push) | First S12.8 sub-segment — 10 runtime findings (D-063; F58/F65/F71/F74/F75/F76/F77/F78/F86/F88). **F74** (Resume inert) ROOT CAUSE: a notification action can be delivered to a freshly (re)created service whose pipeline consumer was never `start()`ed (the paused-override notification survives a service kill, prof756) → the Resume event sat unconsumed in the channel. `ACTION_RESUME` now `ensureRunning()` BEFORE `controller.resume()`. **F75** override alert cancelled on the falling edge of `pausedByOverride` + on resume/teardown (no stacking with the ongoing notif). **F76** removed the **Pause** action from the ongoing notification (Reset/Disable kept; Resume only while paused). **F77** NEW prof769 panic detector: `platform/.../sensor/PanicSensorSource.kt` (`PanicGestureDetector` pure low-pass-gravity upside-down + linear-accel shake; `AndroidPanicSensorSource` gates on display-on + cooldown) → service collects → SOS morse **vibration** (task528 act0 code62 pattern) + `controller.emergencyStop()` (255 + drop dimming + Service=Off). VIBRATE permission added. **F78** new `ThrottleController` (%AAB_Throttle = ACTUAL `steps×wait` floored at the setting; **Throttle Reinitialization** watchdog task566/prof754 → ceiling `AnimSteps×MaxWait+10` after ~10 s of no change); controller uses it for the throttle gate + publishes it. **F65** (REOPENED) PWM-sensitive now ALSO dims below the floor via Extra Dim: `SuperDimmingCoordinator` engages `reduce_bright_colors` using task700 `finalDimLevel` (Map Lux to Brightness V2 act23) when `pwmSensitive`, vs `dimShell` for the super-dimming toggle. **F58** Super Dimming live readout (%AAB_DimmingCurrent rel / %AAB_DimmingDS abs at %AAB_CurrentBright) — new PipelineState fields computed from golden `SoftwareDimming`, `SuperDimmingDiagnosticCard` on the screen. **F86** displayed LuxAlpha clamped ≥0 (`fmtAlpha`; engine value untouched, parity D-010a). **F88** tap-to-dismiss flashes (AccessibilityService overlay made touchable → click hides; foreground-toast fallback can't be tapped — Android limit, noted). **Fence honoured: domain/ + golden vectors + ChartCanvas untouched (called only).** Tests: PanicGestureDetector(4, platform), ThrottleController(4), SuperDimmingCoordinator +2 (PWM engage/above-threshold), BrightnessPipelineController +2 (actual-steps throttle / dimming readout), AmbientMonitoringService +1 (no Pause action), SettingsScreens +2 (F58 readout, F86 clamp) +1 (F88 FlashPill tap). **2nd pass (owner device re-test):** F75 now folds the alert into the single FGS notification ID (no stacking); F77 requires a SUSTAINED dominant-axis inversion (no upright/flat trigger, heavier low-pass); F78 uses the engine's actual `transitionDurationMs` (the setting floor I first added equalled the ceiling, so it always read MaxSteps×MaxWait+10 — removed); F88 adds an in-app tap-to-dismiss surface (`AabFlashHost`/`FlashPill`) since a Toast can't be tapped. Full ladder GREEN: `:platform:test :domain:test :app:testDebugUnitTest`(188) `:app:assembleDebug :app:lintDebug`. No compaction. |
| S12.8c settings/profiles: schema hygiene, labels, legacy load, wizard | 2026-06-16 | Opus/high | DONE | (this push) | Third S12.8 sub-segment — 5 findings (D-064; F59/F62/F70/F84/F85). **F85 (CRITICAL)** `%AAB_ThreshDynamic` is the COMPUTED task544 reactivity-threshold output (task570 act31 only seeds it), never a user input — removed the bogus editable `thresholdDynamic: Int` from `AabSettings` + `AabSettingsContract` + mapper `validate` + `SettingsDisplay.valueFor` + the `ReactivityScreen` editor field + `ContextEngine.mergeProfile`. **Schema v2→v3**: `CURRENT_SCHEMA_VERSION=3`; serializer migration bumps the stamp and the stale key is dropped on read via the existing `ignoreUnknownKeys=true` (verified by test). App-layer only — the engine never consumed the field (it reads the runtime `PipelineState.threshDynamic`, kept). **F59** resolved by the F85 removal: the only user-visible literal "%AAB_ThreshDynamic" string was that field's help; the live reactivity card already shows the VALUE as a %. **F84 + exclusions** `SettingsDisplay` diff list now uses friendly labels (`form1A`→"Zone 1 scaling", etc.; explicit map, no reflection) and `EXCLUDED_KEYS` adds debugLevel/detectOverrides/quickSettingsEnabled/notificationsEnabled (global prefs) + thresholdMidpoint (derived). **F70** legacy-load fidelity: the serializer ALREADY did task570-defaults-THEN-diffs (starts from `AabSettings()`), proven by a new no-inheritance regression through `replaceAll`. The real "Form1A didn't stick / was rounded" bug was an **integer-handling class bug**: decimal-encoded ints (Tasker stores curve params as continuous doubles) silently dropped — `String.toIntOrNull()` returns null on "6.8". Fixed across the WHOLE class: the key=value path now rounds every int/long field (`asRoundedInt`/`asRoundedLong`), the nested-JSON path already used `intRound`, and the wizard-apply now `Math.round`s form1A/form2C (was `.toInt()` truncation). **F62** the Tools wizard now gates on ≥9 **real** recorded points (shared `MIN_FIT_POINTS` with the Curve screen) — the domain engine injects synthetic "ghost" priors that cleared its own ≥9 gate at 7 real points; the suggested curve already draws on the Curve & Brightness chart at ≥9 real points (verified). **Fence honoured: domain/ + golden vectors + ChartCanvas untouched (called only).** Tests: LegacyImportRoundTrip +3 (decimal-round, JSON-fractional Form1A, partial-config-resets-to-defaults), AabSettingsMigration +1 (v2→v3 drops thresholdDynamic), SettingsDisplay +2 (exclusions + friendly labels), SettingsViewModel +1 (replaceAll no-inheritance), SettingsScreens +1 (wizard <9-real-points gate). Full ladder GREEN: `:app:testDebugUnitTest`(196) `:app:assembleDebug :app:lintDebug :domain:test :platform:test`. No compaction. |
| S12.8d circadian time & location correctness | 2026-06-16 | Opus/high | DONE | (this push) | Fourth S12.8 sub-segment — 3 findings (D-065; F39/F73/F83). **F73 (REOPENED) — the UTC frame was NOT the bug.** Tasker task90 act0 (`%AAB_NowSS=%TIMES%86400`) and act59 (windows `%ss_*%86400`) are BOTH UTC-seconds-of-day, and `riseEpochSec` is tz-independent (the `zoneOffset` cancels between `startOfDay` and the local event hour), so the rebuild's UTC frame already matched Tasker exactly. The residual ~1h-early evening ramp (owner: scale 1.025 @20:58 local) was the **location-null → default-windows fallback** (`TimeContext` default eveningStart=18:00 UTC = 20:00 local @UTC+2) — `lastKnownLocation()` is frequently null. Fixes: (a) `CircadianWindowProvider` now reads the tz offset at the **target date instant** (DST-aware; matters for fixed dates in another season) via `tzOffsetForDate`, and (b) a real location is supplied (F83). **F39 (REOPENED — was wrongly preview-only):** the fixed Date and Location now resolve **independently** — date-only / loc-only / both — and DRIVE the live scaling (the old `current()` only honoured the override when BOTH lat AND lon were set, so a date-only override silently fell to live). `ExperimentPrefsStore.set` + `CircadianExtrasViewModel.set` take nullable coords (null field = live for that field); the `CircadianDateLocationCard` "Set fixed" now accepts blank coords (date-only). **F83 (FULL parity):** ported task90 act5–41 acquisition order — `CircadianWindowProvider` acquires once a day (re-acquired when the day rolls over, the `%AAB_SunLastDate != %DATE` guard), **skips** entirely when a fixed lat/lon is pinned, and falls back through Android last-known → fresh fix → **ip-api.com** geo-IP (new `platform/.../context/GeoIpLocationClient.kt`, injectable HTTP fetch, regex parse). Added INTERNET permission + `res/xml/network_security_config.xml` (cleartext scoped to ip-api.com only). The WRITE_SECURE_SETTINGS `location_mode` toggle (act14/19/34, ELEVATED) is intentionally NOT ported — ip-api covers the no-fix case for all tiers and `location_mode` secure-writes are unreliable on minSdk 31 (noted in D-065). Provider decoupled from the store (takes `overrideFlow: Flow<ExperimentDateLocation>`) + geo-IP injected as `suspend () -> LocationSnapshot?` for pure-JVM tests. **HARD FENCE honoured: domain/ + golden vectors + ChartCanvas untouched (SolarCalculator only *called*).** Tests: GeoIpLocationClient (4, platform — parse success/fail/null-island/injected-fetch), CircadianWindowProvider +6 (fixed-date shortens daylight, fixed-loc applies+skips-acquire, date-only uses live loc, ip-api fallback, no-fix→null, tz-at-target-instant), SettingsScreens +1 (date-only set → null coords). Full ladder GREEN: `:app:testDebugUnitTest`(203) `:platform:test`(47) `:domain:test :app:assembleDebug :app:lintDebug`. No compaction. **→ all of S12.8 a/c/d done on their branches; S12.8b (UI) rebases LAST, then re-run HUMAN GATE 2 (5th).** |
| S12.8b UI: dashboard, graph placement, context editor, permissions | 2026-06-16 | Opus/high | DONE | (this push) | Final S12.8 sub-segment — the cross-cutting UI polish (D-066; F68/F79/F81/F82/F87/F89). **Rebased LAST onto S12.8a+c+d** (all merged to main first), so the chart-host screen *content* was already settled. **F79 Dashboard redesign:** dropped the confusing **Pause** control (master switch = on/off; `DashboardViewModel.pause()` removed), kept **Resume** but only on a DETECTED manual override (new `pausedByOverride`/`circadianScale`/`dimmingStrength`/`throttleMs` in `DashboardUiState`, fed from `PipelineState`); rebuilt `DashboardContent` into purposeful cards — status headline, Resume-after-override card (only when `pausedByOverride`), ambient-light (raw/smoothed + last-sample age), brightness (current→target + circadian scale ×/super-dim % when active), active-context, degraded-only health. **F81 graph placement:** new reusable `ui/components/GraphScaffold.kt` — `ChartPager` (HorizontalPager over `ChartSlot`s + dot indicator + title; foundation Pager, no new dep) puts the relevant graph(s) **above** the settings on every chart-host screen and **swipes** between related graphs instead of vertical stacking; SuperDimming's chart moved up from the bottom; Reactivity pages Reactivity↔Alpha, Circadian pages Experiment↔Taper, SuperDimming = single Dimming slot. **S13 coordination:** `ChartSlot.content` is the swap point — S13 fills the real chart for the same title/testTag with no pager/screen change. **F82 grouping:** new `GraphSettingsGroup(graph)` outlined card labels "Affects the {graph} graph" and wraps the controls feeding it (Reactivity curve / Smoothing α / Experiment / Taper / Dimming). **F68:** the context-editor SUNRISE/SUNSET tokens stack vertically with `maxLines=1`/`softWrap=false` → "Sunset (22:00)" no longer char-wraps. **F87:** context app picker `heightIn(max=220→400.dp)`. **F89 permissions audit:** declared `ACCESS_BACKGROUND_LOCATION` (context location gate + daily sun refresh read location from the specialUse FGS while backgrounded; "Allow all the time" offered from the Setup Location step) + documented PACKAGE_USAGE_STATS (usage-access appop, already wired) and DUMP (signature-only → NOT declared, the dumpsys SSID path degrades). **HARD FENCE honoured: domain/ + golden vectors + ChartCanvas untouched.** Tests: UiShell +2 (Dashboard Resume-only-on-override / no-override + Pause gone), SettingsScreens +4 (ChartPager swipe indicator, Reactivity graph-above+grouped, SuperDimming chart-above+grouped, single-line Sunset token). Full ladder GREEN: `:app:assembleDebug :app:testDebugUnitTest`(209) `:app:lintDebug :domain:test :platform:test`. No compaction. **→ ALL S12.8 (a–d) DONE → re-run HUMAN GATE 2 (5th).** |
| S12.8b' Gate-2(5th) follow-ups | 2026-06-16 | Opus/high | DONE | (this push) | On-device Gate-2 (5th) pass on the S12.8b build: most findings PASS; this commit closes the follow-up observations + the reopened **F70** (D-067; same branch). **F70 (Form1A decimal) — REOPENED & properly fixed:** the owner loaded a legacy config with `Form1A=5.833` and the screen showed `6` — 8c had chosen to *round* into the Int schema (the comment even said so). Root fix: **`AabSettings.form1A` Int→Double** (it is a continuous Tasker curve coefficient; the wizard suggests e.g. 5.833). Threaded through the contract (`AabValueType.Double`), mapper (`coerceIn(1.0,20.0)`), validator, `SettingsDisplay` (whole→no `.0`), the legacy parser (nested `dbl("form1a")` + key=value `toDoubleOrNull`, NO rounding), the Curve & Brightness field (`isInt=false`), the wizard-apply (form1A lands exactly), and `DefaultProfiles`. No schema-version bump needed (int-encoded JSON reads into Double transparently). Tests updated: LegacyImportRoundTrip now asserts 6.8/6.834 are PRESERVED (not rounded); validator/screens/VM fixtures use Double literals. **Obs(dashboard two-value):** the pipeline only publishes state AFTER the animation settles, so applied==target always → "X → Y" was always X==Y; `BrightnessCard` now shows a single `N / 255` (falls back to the arrow only if a snapshot ever differs). **Obs(chart naming/grouping):** Circadian's scaling chart renamed "Experiment"→**"Circadian"** (it IS the AAB Experiment Graph = circadian scale); Super Dimming now pages **two** graphs — "Dimming curve" + **"Circadian Dimming"** (the AAB Circadian Dimming Graph, re-homed D-026) — and the **Spread (circadian)** field moved into the new "Circadian Dimming" group (it drives that graph, not the lux→dim curve, matching Tasker). **Obs(chart colour):** the suggested-fit line + override scatter were gold/red; restored to Tasker's `rgb(54,162,235)` Chart.js blue (`AabChartBlue`, the `Yo[0]` palette entry grepped from task663 in the XML). **F59 explained (no code):** F85's removal of the editable Dynamic-threshold field already eliminated the literal-`%AAB_ThreshDynamic` help text; the value now appears only as a % in the live reactivity card — so F59 was resolved *by* F85, nothing further was needed. **F39 verified (no code — confirms F73 residual):** independent compute (Vianen, fixed 21 Dec, spread 15%) → sunrise 08:45/sunset 16:29 (almanac-correct) and scaleDynamic at 15:34 UTC = **1.132**, matching the owner's observed **1.131** — but that is 17:34 at *today's* UTC+2 offset applied to a winter date; on 21 Dec's own UTC+1 it'd be 0.921 (night). So 1.131 is self-consistent yet **~1h off** the intuitive "21 Dec 17:34 local" — the **F73 fixed-date offset residual** (S12.8d/circadian, owner verifying separately), NOT a B-layer bug. **HARD FENCE honoured: domain/ + golden vectors + ChartCanvas untouched.** Full ladder GREEN: `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :domain:test :platform:test`. |
| S12.8b'' Gate-2(5th) round-2 | 2026-06-16 | Opus/high | DONE | (this push) | Second Gate-2(5th) device-pass follow-ups (D-068; same branch). **F39/F73 confirmed full parity by owner** (1.148 @20:45 at their location). Four observations fixed: **(1) Throttle never rose in stable light** — `prof760`'s dead-band gate drops every reading when light is steady, so `runCycle`/`onCycleComplete` (hence the task566/prof754 watchdog) never ran → throttle stuck at the last small value. Fix: `ThrottleController.onSample(now, significant, ceiling)` is now driven from `onSensorSample` on EVERY delivered sample — a reading outside `[ThreshAbsLow,ThreshAbsHigh]` re-anchors the idle timer; 10 s of only-in-band readings raises `%AAB_Throttle` to the `AnimSteps×MaxWait+10` ceiling (now also published from the sensor path so Live Debug shows the climb). `throttleMs`/`lastChangeMs` made `@Volatile` (collector+consumer touch them). **(2) Screen off→on now resumes context automation** (Tasker reinit, prof761): new `AmbientMonitoringService.onScreenOn()` clears the manual context lock (`%AAB_ContextOverride`) when set → `contextEngine.reevaluate()` + `controller.reapply()` so context rules take over again on wake. **(3) Sub-0.1-lux overrides visible:** `CurveBrightnessContent` clamps the DISPLAYED override-scatter lux up to 0.1 (the log x-axis floor) so a 0-lux override draws at the left edge + stays tappable (recorded value untouched; deletion still matches the original). **(4) Wizard "Preview graph" button:** Tools wizard result now has a `preview_graph` button → navigates to Curve & Brightness to see the suggested line on the chart. **HARD FENCE honoured: domain/ + golden vectors + ChartCanvas untouched.** Tests: ThrottleController +2 (onSample idle→ceiling without a cycle / significant-reading re-anchor), SettingsScreens +1 (wizard preview button navigates). Full ladder GREEN: `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :domain:test :platform:test`. |
| S12.9a repo hygiene & build integrity | 2026-06-19 | Opus/high | DONE | (this push) | First S12.9 sub-segment — repo-ballast + dead-code cleanup + lint-as-gate, NO runtime behaviour change (D-071). **(1) Tasker XML out of the tree:** `git mv` `Advanced_Auto_Brightness_V3.3.prj_9.xml` (1.6 MB) repo-root→`docs/rebuild/extraction/_source/`, then `git rm --cached` + `.gitignore` that path (no remote LFS → owner picks LFS-vs-ignore at PR review). CLAUDE.md line-4 pointer + XML_RECIPES.md intro/`X=`/python-recipe paths updated. `git ls-files \| grep prj_9` now EMPTY. **(2) `.gitignore` Android pass:** `.idea/`, `*.iml`, `.DS_Store`, `captures/`, `.cxx/`, `*.hprof`. **(3) Dead API-26 branches removed:** `AutoBrightnessRuntime.kt` `if (SDK_INT >= O) startForegroundService else startService` (×2) → direct `startForegroundService` (O=26 ≤ minSdk 31); unused `Build` import dropped. New `DeadApiCheckTest` greps `app/src/main/kotlin/` for statically-constant `SDK_INT` comparisons vs API ≤ 31 — and a self-test proving the LIVE `< TIRAMISU` (33) POST_NOTIFICATIONS guards (MainActivity/OnboardingScreen) are NOT flagged. **(4) Java extracts relocated:** 40 `extraction/java/*.java` → `extraction/_source/java/*.java.txt` (+ `INDEX.md`); all `java/task*_*.java` refs in tasks/*.md + features_spec/contexts_spec/INDEX.md updated. **(5) `:data` ghost:** removed the retired-`:data` bullet from CLAUDE.md module layout (RUNBOOK's only `:data` mentions are inside the COMPLETED S3 brief = audit trail, left intact). **(6) `Main.kt`→`MainActivity.kt`** (class already `MainActivity`, FQ manifest `.app.MainActivity` — pure rename). **(7) Lint is a gate:** deleted `app/lint-baseline.xml`, flipped `abortOnError=false→true`; fixed the hidden issues — `ObsoleteSdkInt` (gone via #3 + `mipmap-anydpi-v26`→`mipmap-anydpi` folder rename), `VectorPath` (959-char `ic_stat_brightness` path → short circle+stroked-rays), `MonochromeLauncherIcon` (new `drawable/ic_launcher_monochrome.xml` + `<monochrome>` in adaptive icon — note: drawable/, not the brief's literal mipmap path, D-071), `DataExtractionRules` (new `res/xml/data_extraction_rules.xml` stub + manifest attr); new `app/lint.xml` ignores `GradleDependency`+`AndroidGradlePluginVersion` with rationale (version-locked build, S14 owns bumps). **(8) README seeded** (~60 lines: what Tideo is, 3-module layout, 4 gradle commands, doc links, Status line + `TODO(S14)`); CI already shipped (record-only). **Fence honoured: domain/ + golden vectors + ChartCanvas untouched.** 2 residual lint **Warnings** remain (DefaultLocale, InlinedApi FOREGROUND_SERVICE_TYPE_SPECIAL_USE) — post-date the frozen baseline, out of S12.9a scope, do NOT fail the gate (warnings≠errors). Full ladder GREEN: `:domain:test :platform:test :app:testDebugUnitTest`(211) `:app:assembleDebug :app:lintDebug` (abortOnError=true, no baseline). No compaction. |
| S12.9b runtime parity: circadian dimming wiring | 2026-06-19 | Opus/high | DONE | (this push) | Second S12.9 sub-segment — the ONE real runtime-parity gap + two UX refinements, app-layer only (D-072). **HEADLINE G2R-F90 — circadian dimming was inert (closes the `dimDynamic=null` deferral D-040):** `SuperDimmingCoordinator.apply` hardcoded `dimDynamic = null`, so `%AAB_DimSpread` (Spread (Circadian), default 100 — confirmed via `circadian_dimming_graph.md` Y-formula + task646 act6/act7) never modulated super-dimming and dimming engaged at full strength even in circadian-scaled daylight (`scaleDynamic ≈ 1.15`). **Fix (no domain/golden change — `dimShell` already accepts the param):** `DimmingCoordinator.apply` gains a `scaleDynamic` arg (neutral default 1.0); the controller passes the cycle's `output.scaleDynamic` at both call sites (runCycle + setInitialBrightness). New top-level `circadianDimMultiplier(scaleDynamic, settings)` recovers task90's shared tanh day/night `modifier` from the published scale — `DimDynamic = 1 − (DimSpread/ScaleSpread)·(ScaleDynamic−1)`, BigDecimal HALF_UP scale-3, returns `null` when `!scalingEnabled` (task646 act9 plain-strength) — exact & ScaleSpread-independent because `ScaleSpread ∈ 1..100` (contract). Semantics: DimSpread 100 → suppress in daylight (→0), 0 → engage normally (1.0 ≡ the old null path), −100 → boost (→2.0). The F58 live readout (`dimmingReadout`) uses the SAME multiplier so the Super Dimming card matches the applied Extra Dim. **G2R-F91 — override flash:** the manual-override toast was a bare `Toast` (could stack, not tappable); now routed through the shared teal `AabFlash` operational surface (global a11y overlay → in-app pill → Toast fallback), consistent with the profile/context-load flashes; string marked `// TODO(S12.9d): strings.xml`. **Shizuku grant UX (not a hang fix):** `pingBinder()` couldn't tell *not-installed* from *installed-not-running*; new `ShizukuAvailability` (RUNNING/INSTALLED_NOT_RUNNING/NOT_INSTALLED) via `ShizukuGrantGateway.isInstalled`/`availability` (PackageManager `moe.shizuku.privileged.api`); `PrivilegeManager.isShizukuAvailable()`→`shizukuAvailability()`; onboarding shows a "start Shizuku" prompt for INSTALLED_NOT_RUNNING, the one-tap button only when RUNNING, and ADB ALWAYS offered. **Rejected (D-069): NO AnimationRunner 5th-frame stride re-port.** Fence honoured: domain/ + golden `superdimming.csv` + ChartCanvas untouched. Tests: SuperDimmingCoordinator spread 100/0/−100 + night-no-effect (4), `circadian_spread0 ≡ no-scaling` parity; AmbientMonitoringService override-flash-through-AabFlash; PrivilegeManager availability states + ADB-always; UiShell Shizuku running/installed-not-running; controller `FakeDimming` records scaleDynamic. PARITY_CHECKLIST L76 flipped. Full ladder GREEN: `:domain:test :platform:test`(50) `:app:testDebugUnitTest`(221) `:app:assembleDebug :app:lintDebug`. No compaction. |
| S12.9c schema ergonomics, validation & error handling | 2026-06-19 | Opus/high | DONE | (this push) | Third S12.9 sub-segment — make the settings model decomposable, fail-fast on drift, surface profile-load errors, validate the circadian spread, audit validation parity, honest-ify placeholders, and document the storage/privilege/permission surfaces. App-layer + docs only (D-073). **(1) Nested AabSettings:** new `AabSettingsGroups.kt` with 7 `@Serializable` records (`BrightnessBounds`/`CurveParams`/`DimmingConfig`/`AnimationConfig`/`ThresholdConfig`/`ScalingConfig`/`GlobalPrefs`) exposed as **computed group views** over the flat fields — flat v3 JSON stays the on-disk schema (`CURRENT_SCHEMA_VERSION`=3, no migration; ~395-call-site churn avoided, D-073). `NestedSchemaRoundTripTest` asserts flat JSON loads into the groups + re-encode stays flat. `mergeProfile` kept behaviour-identical (preserves exactly the 5 GlobalPrefs identity/runtime fields + schemaVersion; NOT quickSettings/notifications, which are in task626's profile snapshot — documented). **(2) `valueFor` fail-fast:** `else -> ""` → `throw IllegalArgumentException`; `SettingsDisplayContractDriftTest` (every contract key resolves, count matches, unknown throws). **(3) `ProfileLoadResult`** sealed type (Success/LegacyFallback/TotalFailure); `ProfileImportExportManager.decodePayload` logs via `android.util.Log` + returns it; ProfilesScreen shows a user-visible error card on TotalFailure (`load_error_card`); 4-case `ProfileLoadResultTest`. **(4) Legacy parser throws** `LegacyProfileParseException` on structurally-invalid input (no JSON object / no `%AAB_` lines); a valid-but-empty config still loads to task570 defaults; `LegacyProfileParseTest`. **(5) DataStore map:** `architecture/datastore_map.md` (6 stores, schemas, independence rationale) + `SCHEMA_VERSION` consts on the 3 typed-JSON models + `DataStoreSchemaVersionTest` (const matches serializer default). **(6) Spread (Circadian) validation:** `SettingsValidator` dimSpread −100..100 advisory + boundary test (−101/−100/0/100/101); ALSO fixed `AabSettings.validate()` clamp `1..300`→`-100..100` + contract range — the old clamp turned S12.9b's negative-boost path into 1 on every save (latent runtime fix). **(7) Validation parity audit:** `extraction/validation_audit.md` (every Tasker guard vs coverage); added the top gap validators (dimming-strength>65 task607, dimming-threshold<minBright task513, taper-mid>maxBright task665/689, min-wait>max-wait task403); ~14 cosmetic reformat guards deferred to S14 (logged); 2 pre-existing `⚠️` glyphs stripped (no-glyph policy). **(8) Placeholders honest:** `PlaceholderScreen`/`ChartPlaceholder` "Coming in …" → "not available yet" + `TODO(S13)` marker; `PlaceholderScreenAuditTest` fails on any "coming in" in `ui/screens/`. **(9) Docs:** `architecture/privilege_tiers.md` + `architecture/permission_audit.md`. **Dropped (D-069): Haversine de-dup is a no-op (one impl).** Fence honoured: domain/ + golden vectors + ChartCanvas untouched. New tests: NestedSchemaRoundTrip(3), SettingsDisplayContractDrift(3), ProfileLoadResult(4), LegacyProfileParse(5), DataStoreSchemaVersion(4), SettingsValidator spread(3), PlaceholderScreenAudit(1); LegacyImportRoundTrip dimSpread fixture 120→80 (now clamped). Full ladder GREEN: `:domain:test :platform:test`(50) `:app:testDebugUnitTest`(244) `:app:lintDebug :app:assembleDebug`. No compaction. |
| S12.9d i18n capability + test backfill + staleness gate | 2026-06-19 | Opus/high | DONE | (this push) | Fourth S12.9 sub-segment — i18n surface, evidence-based runtime test backfill, `LiveRuntimeState` staleness gate; app-layer only (D-075). **(1) i18n:** `strings.xml` 2→47 entries — notification strings (all of `AmbientMonitoringService`), 9 screen titles, Dashboard labels/buttons/tier-badges/status, shared action labels (Resume/Reset/Disable), the override flash — extracted to `stringResource`/`getString`. Section headers, per-field labels, and long-press help DEFERRED to S14 (logged). New `HardcodedStringCheckTest` ratchets the remaining inline `Text("…")` literal count (ceiling **92**, RATCHET DOWN ONLY) + asserts the notification surface has no literal `setContentTitle/Text`/`addAction` strings. No `values-es/` (capability, not translations); log/provenance/exception strings NOT extracted. **(2) Test backfill (true gaps only):** surveyed existing first — `AmbientMonitoringServiceTest`/`ContextEngineTest` already cover their runtimes (not duplicated). Added `OverrideMonitorTest` (prof755 gate: open / settle-suppressed / auto-running / detection-off / service-off), `AndroidContextSignalSourceTest` (clock→day/seconds derive + default 06:00/18:00 vs computed solar window, fakes + injected clock), `AabToastAccessibilityServiceTest` (connect→global `AabFlash` presenter, unbind→degrade, show routes; owner-intentional — tests behaviour). **`MaintenanceWorker` deferred** (clean test needs the `work-testing` dep, unsanctioned by the brief, or a refactor — logged); `ScreenStateReceiver` deferred to S12.9e (its ad-hoc detached scope is reworked there via `goAsync()`). **(3) Staleness gate:** `PipelineState.lastPublishMs` stamped by `LiveRuntimeState.publish`; `LiveRuntimeState.staleness()` Flow (FRESH <3s / AGING 3–10s / STALE >10s, injectable clock, 1s tick) + pure `classifyStaleness`; `DashboardViewModel.stale` → amber `dashboard_stale_banner` shown only when `stale && serviceRunning`; `AmbientMonitoringService.onDestroy`/`onTaskRemoved` arm a 5s reset watchdog (skips the reset if a newer publish arrived — avoids a flicker on a system-driven restart; `tearDownDisabled` still resets immediately). `LiveRuntimeStateTest` (classify boundaries, publish-stamp, FRESH→STALE-after-11s, reset). **Fence honoured: domain/ + golden vectors + ChartCanvas untouched.** Full ladder GREEN: `:app:testDebugUnitTest`(262) `:app:assembleDebug :app:lintDebug :domain:test :platform:test`. No compaction. |
| S12.9e concurrency safety + controller decomposition | 2026-06-19 | Opus/high | DONE | (this push) | Fifth S12.9 sub-segment — purely structural/safety, NO behaviour change; the D-027 concurrency MODEL (single consumer, drop-on-busy) is untouched (D-076). App-layer + ONE comment-only domain touch (golden + ChartCanvas untouched). **(1) `AppProcessScope` + `goAsync()`:** new process-wide `SupervisorJob + Dispatchers.Default` scope w/ a logging `CoroutineExceptionHandler`; the owner-less ad-hoc `CoroutineScope(Dispatchers.*)` in `AutoBrightnessRuntime` (bootstrap + 2 health writes) → `AppProcessScope`, and `BootCompletedReceiver`/`ScreenStateReceiver` → a `BroadcastReceiver.goAsync { }` overload (`PendingResult` held + `finish()`ed in `finally`; defensive `?.finish()` for direct-invocation tests). The 2 service/tile scopes (`AmbientMonitoringService`/`BrightnessTileService`) are legitimately-owned (cancel in `onDestroy`) → **justified in place**, not converted. **(2) Killed `lateinit var controller`** (`AppModule`): new `fun interface ControllerHook` + `ControllerHookHolder` (`@Volatile hook` set post-construction; pre-assignment `fire()` = no-op); engine→controller via the holder. **(3) `ContextEngine` 6 `@Volatile` signal fields → one `MutableStateFlow<SignalSnapshot>`** (untearable per-eval read); `screenOn` is the lone surviving `@Volatile`. **(4) Volatile audit:** controller 5→**2** (`autoRunning`+`initializing` moved into `PipelineState` for atomic compound gate reads; `lastScaleDynamicSeen` dropped into `PipelineDebugEmitter`; `cachedSettings`+`suppressOverrideUntilMs` justified); `CircadianWindowProvider`(5)/`ThrottleController`(2)/`AabFlash`(2) each given a one-line rationale. **(5) Controller 596→4 files:** orchestrator `BrightnessPipelineController.kt`(292) + `PipelineCycleRunner.kt`(math glue, via internal `PipelineRuntimeContext` single-writer) + `PipelineDebugEmitter.kt` + `PanicHandler.kt`; **all `// Tasker:` provenance moved with its code**; existing 16-case controller test green UNCHANGED. **(6) `BrightnessEngine` rounding-mode header** (comment-only; `// Tasker` count frozen at 13). **(7) Documented the two-layer screen-receiver split** (no double-fire). New tests: `PipelineFileLayoutTest`(3, re-bloat guard), `AppModuleTest`(3, hook wiring + no-op-before-assign), `ScreenStateReceiverTest`(2), `BootCompletedReceiverTest`(+1), `ProvenanceCommentCountTest`(domain, 1). **Fence honoured: golden vectors + ChartCanvas untouched; the only domain edit is the comment-only header.** Full ladder GREEN: `:domain:test`(82) `:platform:test`(100) `:app:testDebugUnitTest`(271) `:app:assembleDebug :app:lintDebug`. No compaction. **→ S12.9f (Profiles+Contexts IA merge) is the last S12.9 sub-segment.** |
| S12.9f Profiles & Contexts IA merge (plumbing) | 2026-06-20 | Opus/high | DONE | (this push) | Sixth & LAST S12.9 sub-segment — fold the separate Profiles and Contexts destinations into ONE screen mirroring the Tasker UX (a profile owns its context rules; a rule targets a saved profile). Structure/navigation/state-wiring only; behaviour-preserving; app-layer (D-070). **(1) One destination:** new `ProfilesContextsScreen.kt` hosts BOTH `SettingsViewModel` (profiles) + `ContextsViewModel` (rules) under one `SettingsScaffold("Profiles & Contexts")` — the saved-profiles surface (`ProfilesBody`, extracted from `ProfilesContent`) above a "Context rules" section (`ContextRulesSection`). `AppRoute.Contexts` REMOVED; `AppRoute.Profiles` relabelled "Profiles & Contexts" (route id `profiles` kept, owner→S12.9f); `heroDestinations` = `[Profiles]` (the Menu's two hero cards collapse to one, still surfacing live context/manual-override status); `NavGraph` drops the `contexts` composable + `ContextsScreen` import → `ProfilesContextsScreen`. **(2) Rule editing in a modal:** `ContextRulesSection` renders the priority-ordered rule list (`RuleCard` → target profile · priority · trigger summary) + "Add rule"; edit/add opens the full `RuleEditor` in a **full-screen `Dialog`** (`usePlatformDefaultWidth=false`, scrollable; chosen over `ModalBottomSheet` for Robolectric reliability — both sanctioned by the brief; S13c can revisit) — every affordance preserved (installed-app picker, use-current-SSID, SUNRISE/SUNSET tokens, use-current-location, usage-access prompt, day picker, clear-time, battery %). **(3) Shared state:** manual-load context lock + Resume banner preserved (`ProfilesBody`), rule→profile relationship preserved (catalog reads user profiles). **No change to `ContextEngine`/`ContextOverrideResolver` or any store.** Legacy `ProfilesContent`/`ContextsContent` kept (now scaffold-wrappers over the shared bodies) so the existing screen tests stay green unchanged. **(4) Tests:** existing context-editor tests pass UNCHANGED through the new modal; new `SettingsScreensTest.profilesContextsMerge_*` (both surfaces coexist + rule edits via the `rule_editor_modal`), `UiShellTest.profilesContextsMerge_oneDestination_noContextsRoute` (no `contexts` route, label, single hero) + updated hero-card assertions. `HardcodedStringCheckTest` ceiling unchanged (no new `Text("…")` literals; `title_profiles_contexts` added to `strings.xml`). **Fence honoured: domain/ + golden vectors + ChartCanvas untouched.** Full ladder GREEN: `:domain:test :platform:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`. No compaction. **→ ALL of S12.9 (a–f) DONE.** |
| S13a design-system foundation | 2026-06-20 | Opus/high | DONE | (this push) | First S13 sub-segment — design-system tokens + written redesign plan; NO logic/recolor (D-077). **(1) Tokens:** `ui/theme/Dimens.kt` (raw 4dp grid `space1..space7` + semantic spacing/card/component tokens, every value == the literal it replaces → behaviour-preserving), `ui/theme/Shape.kt` (`AabShapes` = M3 default shape scale, formalised), `ui/theme/Type.kt` (`AabTypography` = explicit M3 type scale, 6 used roles restated + rest inherited). **(2) Wired:** `TideoTheme` now passes `typography=AabTypography, shapes=AabShapes` (values == prior defaults, no visual change); `Dimens` referenced by `components/SettingsScaffold.kt::SettingsColumn` (inherited by every settings screen) + `screens/MenuScreen.kt` (removed its now-unused `dp` import). **(3) Audit:** `docs/rebuild/design/m3_audit.md` — constraints, token tables, **ColorScheme role-mapping** for the frozen S12.5a teal+gold scheme, per-screen gap→target plan for all 9 screens + the merged Profiles/Contexts screen, cross-cutting gaps, acceptance checklist. **(4) Owner UI-overhaul feedback (2026-06-20) ADOPTED into the plan** (audit §2.5): forwarded "Pro-Tool aesthetic" spec → 5 S13b component blueprints (`SectionHeader`+divider, `HeroNavCard` w/ teal edge+chevron, `NavRow`, **`KeyValueRow`** bold-gold data readout [the critical high-contrast data-pop], `ActionButtonBar`) + structural rules (16dp no edge-bleed, teal single-line app bar, group-in-cards, 3-dot overflow for secondary actions) — **mapped onto the FROZEN palette**: spec's `#1A1C1E`/new-tertiary recolor REJECTED per the binding "color is fixed — NOT recoloring" guardrail; gold data accent = existing `secondary` `AabGold`, dark surface stays `#383838`. **Fence honoured: ChartCanvas/domain/golden/runtime/gradle/manifest/SettingsValidator untouched.** GREEN: `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` (+ `:domain:test :platform:test` unaffected). No compaction. |
| S13b component library | 2026-06-20 | Opus/high | DONE | (this push) | Second S13 sub-segment — build the reusable "Lego blocks" from `m3_audit.md` §2.5 blueprints + §4 cross-cutting gaps; behaviour-neutral until S13c adopts them (no screen rewrites, no recolor, fence honoured). **New files:** `components/AabCard.kt` (`AabCard` elevated section container [medium shape, `cardElevation`/`Raised`, `cardPadding`, default `fieldSpacing` rhythm]; **`KeyValueRow`** B4 — the critical bold-**gold** [`secondary` role, not raw `AabGold`] data-pop with `outlineVariant` bottom border; `EmptyState` icon+muted-text placeholder); `components/AabNav.kt` (**`HeroNavCard`** B2 — promoted from MenuScreen's private hero: `large` shape, teal left-edge accent bar, gold-sun icon [sanctioned raw tint], chevron, press-scale motion; **`NavRow`** B3 — in-card clickable row w/ chevron; **`ActionButtonBar`** B5 — generalised from `DraftApplyBar`, weight-even tonal/outlined buttons w/ leading icons via `ActionButton`/`ActionButtonStyle`); `components/AabMotion.kt` (`AabMotion` object — screen enter/exit, list item enter/exit, `valueSpec`, `PRESS_SCALE`); `components/SettingField.kt` (unified **`SettingField`** + `SettingFieldSpec` sealed `Decimal`/`Slider`/`Toggle` — **delegates** to the existing `NumberSettingField`/`IntSliderSettingField`/`SwitchSettingRow` so the draft-epoch/committed-bracket/help logic [G2-F7/F1] is unchanged → existing field tests stay green). **Enhanced in place:** `SectionHeader(text, divider=false)` (B1 — opt-in `outlineVariant` rule below; default false keeps all current call sites unchanged). **Acceptance:** `ComponentLibraryTest` (Robolectric+Compose, rendered in real `TideoTheme`) — 10 instantiation tests: AabCard groups content, KeyValueRow key+gold value tags, EmptyState, NavRow click, HeroNavCard title/subtitle/click, ActionButtonBar weighted+disabled+click, SectionHeader+divider, SettingField Decimal `[committed]` bracket / Toggle flip / Slider in-range. The MenuScreen's private `MenuHeroCard`/`MenuNavRow` stay until S13c migrates screens. **Fence honoured: `ChartCanvas`/`domain`/golden/`runtime`/gradle/manifest/`SettingsValidator` untouched; no new deps; `HardcodedStringCheckTest` ceiling 92 unchanged (components take caller-supplied strings — no new literals).** Full ladder GREEN: `:app:assembleDebug :app:testDebugUnitTest`(254) `:app:lintDebug :domain:test :platform:test`. No compaction. **Next: S13c** (apply the library screen-by-screen; check off m3_audit §3). |
| S13c screen-by-screen restyle | 2026-06-20 | Opus/high | DONE | (this push) | Third S13 sub-segment — apply the S13b component library across every screen; behaviour-preserving (every test-pinned `testTag` + the draft/validation/diagnostic semantics unchanged), no recolor, fence honoured (D-079). **Restyled (m3_audit §3 rows 1–10 all flipped ✅):** **Menu** — hero → shared `HeroNavCard` (teal edge + press-scale motion), Dashboard/Settings/Info rows → `NavRow` grouped in `AabCard` sections (private `MenuHeroCard`/`MenuNavRow` deleted); **Dashboard** — plain status/light/brightness/context/health blocks → `AabCard` (the stale/override **tinted** banners kept as semantic `Card`); **Curve & Brightness** — curve-zone fields in an `AabCard`, derived form2A/form3A → distinct `AabCard` of gold `KeyValueRow` data-pops (B4); **Reactivity** — override/trust switches grouped into an `AabCard` (threshold/α keep `GraphSettingsGroup` for the `group_<graph>` G2R-F82 contract); **Super Dimming** — `SectionHeader(divider=true)` over the existing `GraphSettingsGroup`s; **Circadian** — date/location block → `AabCard`; **Misc** — Brightness-range/Animation/Notifications each an `AabCard`, throttle + auto-scale readouts → gold `KeyValueRow`; **Tools** — `WizardCard`/report/power-draw → `AabCard`; **Live Debug** — left on the glass-box `DiagnosticCard` (already the right component); **Profiles & Contexts** — profile/rule/legacy rows → `AabCard`, "no profiles"/"no rules" hints → `EmptyState`, rule-editor modal gains `tonalElevation`. **Cross-cutting motion (§4):** app-wide screen enter/exit wired via `AabMotion.screenEnter/Exit` on `AppNavGraph`'s `NavHost`. **`DerivedReadout` retired** at call sites (folded into `KeyValueRow`; the helper remains for any future use). **Owner extra — launcher logo refresh:** rebuilt the Tasker `_CreateLogo` art (sun whose rays hold a faded brightness slider) as a modern adaptive icon on the AAB brand — `ic_launcher_foreground.xml` (gold sun + rays + faded white slider + knob), matching `ic_launcher_monochrome.xml`, and `ic_launcher_background` recoloured generic indigo → AAB teal `#FF007C63`. **Deferred to S14 (recorded, not blocking):** Profiles/Contexts 3-dot overflow for secondary row actions + dropping the app-picker `heightIn(max=400.dp)` — both kept because the screen tests find `apply/overwrite/delete_*` directly (overflow hides them) and the picker sits inside a `verticalScroll` (a `weight` child is illegal there). **i18n:** no new `Text("…")` literals — converting empty hints to `EmptyState` + the Menu row label off `Text(…)` actually **lowered** the count 92→89 (ceiling 92, `HardcodedStringCheckTest` green). **Fence honoured: `ChartCanvas`/`domain`/golden/`runtime`/gradle/manifest/`SettingsValidator` untouched; no new deps** (`res/` is not fenced). Full ladder GREEN: `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug :domain:test :platform:test`. No compaction. **Next: S13d** (real charts filling the `ChartSlot` swap points + About/User-Guide static screens; remove placeholders). |
| S13c' polish pass | 2026-06-20 | Opus/high | DONE | (this push) | Owner-supplied design spec (not in RUNBOOK) run as a visual-elevation sibling of S13d: make the instrument *land*. Palette FROZEN; type/layout/depth/motion only; `domain`/`runtime`/`ChartCanvas`/golden/gradle/manifest untouched; all test-pinned `testTag`s preserved (m3_audit §6). **(1) Typography (the big lever):** bundled **IBM Plex Sans** (UI) + **IBM Plex Mono** (readouts) as SIL-OFL `.ttf` in `res/font` (licence `docs/licenses/IBM-Plex-OFL.txt` — a static asset, NOT a gradle dep; `res/` unfenced). `Type.kt` → `AabSans`/`AabMono`, two new instrument roles `AabDataDisplay` (Plex Mono Medium, tabular `tnum`, 26sp) + `AabDataCaption` (tracked-uppercase mono), `titleLarge`→SemiBold; lands app-wide via `TideoTheme`. **(2) Surface ladder:** `AabCard` gains `variant` (`Resting`/`Hero`/`Well`) — 1px white-alpha hairline + honest shadow, teal accent-edge on `Hero`, recessed `surfaceVariant` `Well`; new tokens `cardElevationHero`=6 / `accentEdge`=3. **(3) Data-pop:** `KeyValueRow` rebuilt as a readout line (tracked mono caption / big tabular gold value / demoted `unit:` param / value `Crossfade`); `DiagnosticCard.goldValue()` → Plex Mono tabular; `value_*` tags intact. **(4) Dashboard hero:** new `components/BrightnessInstrument.kt` — applied 0–255 in near-white Plex Mono, a thin teal `Canvas` track eased via `animateFloatAsState` (NOT `ChartCanvas`), status pill + master switch, greys out when off; lux·circadian·context demoted to a readout strip. Kept `dashboard_brightness`/`dashboard_status`/`service_switch`/`last_sample_age`/`tier_badge`/`override_card`/`resume_button`; no `pause_button`. **(5) Declutter Profiles & Contexts:** a `SegmentedButton` splits **Profiles**/**Rules** (one job at a time, single destination kept); profile-row secondary actions (Overwrite/Delete) → per-row 3-dot overflow (Apply stays primary) — **clears the S13c-deferred §3-row-10 overflow item**; legacy Tasker import → collapsed `ExpandableSection`. App-picker `heightIn(max=400.dp)` still kept (illegal `weight` child of `verticalScroll`) — recorded. **(6) Polish:** `fieldSpacing` 10→12, `heroCardPadding` 20→24, `sectionSpacing` 8→16 (grid); brand banner gets Plex wordmark + tracked-mono tagline; nav icons muted to `onSurfaceVariant`. **i18n:** new strings via `stringResource`; `Text("…")` literals held at 89 (ceiling 92, `HardcodedStringCheckTest` green). **Tests touched (sanctioned, layout/casing only):** `ComponentLibraryTest`/`SettingsScreensTest` KeyValueRow uppercase-caption assertions; `SettingsScreensTest` profile overflow-menu open. Full ladder GREEN: `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug :domain:test :platform:test`. No compaction. **Next: S13d** (real charts into the `ChartSlot` swap points + About/User-Guide; remove placeholders). |
| S13d static content & charts | 2026-06-20 | Opus/high | DONE | (this push) | Final S13 sub-segment — the original S13 scope: real charts + About/User-Guide, replacing all placeholders. Followed the `BrightnessCurveChart` template exactly (sample a domain fn over a grid → `List<Offset>` → `ChartSeries` → `ChartCanvas`); **`ChartCanvas` fence honoured (called only)**. **6 chart files filling the S12.8b `ChartSlot.content` swap points (D-066):** `ReactivityChart.kt` (`ReactivityChart` = `BrightnessEngine.dynamicThreshold×100` vs hardcoded ref over log lux → `reactivity_chart`; `AlphaResponseChart` = `1−exp(−deltaFactor·Δ)` vs ref 1.8 → `alpha_chart`), `DimmingChart.kt` (`SoftwareDimming.dimProgress×100` + dim-shell + ref `pow(1−b/15,2.5)` → `dimming_chart`), `CircadianChart.kt` (`CircadianDimmingChart` = `DynamicScaleEngine.dimDynamic` over the day via `SolarCalculator` windows + shared `circadianDaySamples`/`deviceTzOffsetHours` helpers → `circadian_dimming_chart`), `ExperimentChart.kt` (`CircadianScaleChart` = `DynamicScaleEngine.scaleDynamic` over the day → `dynamic_scale_chart`), `TaperChart.kt` (`BrightnessEngine.compressedDynamicScale.effectiveScale` day/night over brightness → `taper_chart`), `PowerDrawChart.kt` (renders measured power/current; `EmptyState` until on-device calibration runs, D-044/Gate → `power_draw_chart`). All 4 chart-host screens (Reactivity/Circadian/SuperDimming/Tools) swapped their `ChartPlaceholder` for the real charts; Circadian passes the F39 fixed/live lat-lon to the solar charts. **Static screens:** `AboutScreen.kt` (banner + intro + acknowledgments + MIT license + app version; Chart.js ack DROPPED) + `UserGuideScreen.kt` (9-section manual in M3 cards, ported from user_guide.md). **`PlaceholderScreen.kt`/`ChartPlaceholder.kt` deleted** (git rm); the 2 tests that used them as generic stand-ins updated (inline `Text`/`EmptyState`). **G2R-F80:** `AppRoute.UserGuide` added; `completeOnboarding()` now lands on the User Guide (Menu seeded beneath → Back→Menu) as the post-onboarding first-run destination; About relabelled "About", both in the Menu Info&Help group. **i18n:** all About/Guide copy + new dashboard strings via `stringResource` (strings.xml +~40); no new `Text("…")` literals (ceiling 92 held). **Owner UI feedback (during S13d) also fixed:** (a) Circadian fields converted `helper=`→`help=` so they get the "ⓘ tap to view explanation" affordance like every sibling screen; (b) Menu banner wordmark now ONE line ("Tideo" white + "Auto Brightness" gold); (c) `AabTopBar` de-teal'd (default M3 surface) so the teal banner is Menu-only (Dashboard/Live Debug no longer carry a second teal header); (d) Dashboard status pill uses compact labels (no wrap next to "APPLIED BRIGHTNESS"); (e) **Dashboard now shows the active Profile + active Context** (always-visible readout) via a new in-memory `LiveRuntimeState.activeProfile` (`%AAB_CurrentActiveProfile`) published by `SettingsViewModel.applyProfile` (manual) + `ContextEngine` (rule loads), surfaced through `DashboardViewModel`/`DashboardUiState`. **Fence honoured: `ChartCanvas`/`domain`/golden/`runtime` pipeline/gradle/manifest/`SettingsValidator` untouched.** Full ladder GREEN: `:app:assembleDebug :app:lintDebug :app:testDebugUnitTest`(283) `:domain:test :platform:test`. No compaction. **→ ALL of S13 (a–d) DONE → S14 (release-grade finalization).** |
| S13d' chart fidelity + guide WebView (owner feedback) | 2026-06-21 | Opus/high | DONE | (this push) | Owner device-review follow-ups on S13d (D-083). **ChartCanvas fence LIFTED with explicit owner sanction** ("free to break the fence if it is a blocker") — additive only: `ChartSeries.onSecondaryAxis`, `ChartCanvas.secondaryYRange`/`secondaryYAxisLabel` (dual y-axis, right ticks+title), `xTickFormatter` (custom x labels), and `ChartMarker.label` now rendered (rotated). **Fixes:** (1) **charts stay interactive (owner: "charts have to be interactive")** — the drag-scrub readout is kept on, which consumes the `ChartPager` horizontal swipe, so page navigation moved to **tap**: ‹ › arrows flanking the title + clickable page dots (`userScrollEnabled=false`). Swipe-between-graphs is intentionally gone. (2) **dynamic y-axis** on Reactivity (was fixed 0–100; thresholds top ~35 %). (3) **reference is always gold** — DimmingChart reference→gold, dim-% teal, dim-shell blue; TaperChart night→blue (gold reserved for references). (4) **Dimming dual y-axis** — dim-% + gold reference on LEFT (0–100), dim-shell on RIGHT (0–strength), matching Tasker. (5) **Circadian charts** — switched to the **UTC frame** (tz=0, matches Tasker `%TIMES%86400` + runtime D-061/D-065), x-axis now **HH:MM** via `xTickFormatter` (no more "5.8h"), the five **sun-event lines labelled** Dawn/Sunrise/Noon/Sunset/Dusk, axis titled "Time of day (UTC)"; the weird scrub rounding (1.1 vs 1.15) is gone with `interactive=false` (y-ticks show 1.00/1.05/1.10/1.15). (6) **About** — acknowledgements reworded so João Dias is clearly Tasker's creator (not AAB's), author credit to /u/v_uurtjevragen; **MIT license year 2025→2026**. (7) **User Guide → WebView/HTML** (Tasker rendered it as styled HTML) — `UserGuideScreen` now renders an AAB-themed static HTML doc (teal headers/gold accents/dark surface) in a no-JS/no-network `WebView` (no new dep); copy still from `strings.xml` (i18n preserved), markup local. Fresh `/dist/` debug APK rebuilt. Tests/ladder GREEN (`:app:testDebugUnitTest`=283 `:app:assembleDebug :app:lintDebug :domain:test :platform:test`). No compaction. |
| S13e final pre-S14 owner pass | 2026-06-21 | Opus/high | DONE | (this push) | Owner-sanctioned punch-list (not in RUNBOOK), 4 items + 1 retraction (D-086). **(1) CI Node-20 deprecation:** bumped `actions/checkout@v4→v5` + `actions/setup-java@v4→v5` (node24) in both `release.yml`/`release-signing.yml`; the remaining official actions (cache/upload-artifact v4, action-gh-release v2, setup-android v3) have no node24 major yet and are force-upgraded by the runner from June 2026 — nothing to bump. **(2) Profiles & Export collapsible:** folded the "Manage profiles" + "Export" sections of `ProfilesBody` into ONE collapsed `ExpandableSection` ("Manage profiles & Export", `manage_section` tag) so the saved-profiles list is the uncluttered default; SettingsScreensTest expands it first. **(3) Transition factor does nothing (REAL bug):** `circadianCurve` hard-coded `scaleTransitionFactor=0.1`, so the Circadian-screen "Transition factor" field changed nothing on the graph (the *runtime* honoured it via PipelineCycleRunner). Threaded the real `draft.scaleTransitionFactor` into `circadianCurve`/`CircadianScaleChart`/`CircadianDimmingChart` (added to the `remember` key); both Circadian + Super-Dimming charts now respond. **(4) Home-screen widget + Dashboard quick actions (owner):** new `widget/DashboardWidgetProvider` (RemoteViews: Brightness/lux/profile/context + service toggle + reset; `updatePeriodMillis=0`, repainted EVENT-DRIVEN from the FGS publish path's existing `distinctUntilChanged` collect — no polling; no-widget fast-path early-returns before any DataStore read), `res/layout/widget_dashboard.xml` + `res/xml/dashboard_widget_info.xml` + bg drawables + manifest receiver (APPWIDGET_UPDATE + TOGGLE/RESET actions). Dashboard gained a `QuickActionsCard`: **Reset to auto** (= re-apply/snap-to-auto per owner; `DashboardViewModel.resetToAuto`→`AutoBrightnessRuntime.reapply`), **Add Quick Settings tile** (`StatusBarManager.requestAddTileService`, API-33+ gated via auto-inferred `canAddTile()`, de-dupes via TILE_ALREADY_ADDED→toast), **Add home-screen widget** (`requestPinAppWidget`, shown only when supported ∧ `!hasInstances`). **(retraction) "date doesn't affect circadian graphs" — FALSE (owner mistake on their end); investigation independently confirmed the path is correct** (solar windows shift hours by date [Jun rise 3.71 vs Dec 8.05 UTC], store persists it, pager recomposes captured plain values). **Fence honoured: domain/ + golden vectors + ChartCanvas untouched** (chart change is app-layer wiring only). New tests: `DashboardWidgetProviderTest`(4, status mapping), UiShell +2 (quick-actions render/hidden). Full ladder GREEN: `:domain:test :platform:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` (lint 0-error hard gate). No compaction. |
| S14 final integration, parity audit, release prep | 2026-06-21 | Opus/high | DONE | (this push) | Whole-system finalization (D-087…D-090). **Parity audit:** every PARITY_CHECKLIST row → ported/dropped, **zero `pending`** (resolved 18 — most were already-implemented rows never flipped; verified each ported claim against real code). **Two deferred features PORTED to parity (owner: don't drop, parity is the goal):** (1) **prof759/task545 proximity damp** (D-087) — `platform/sensor/ProximitySensorSource` (TYPE_PROXIMITY near/far) → `ProximityTracker` → `PipelineState.proximityNear` → `BrightnessEngine.smoothLux` LuxAlpha ×0.1 (task544 act28/29, additive golden-preserving `luxAlphaDamp` param + `PROXIMITY_ALPHA_DAMP`); never pauses; (2) **task524 power-draw calibration** (D-088) — `domain/power/PowerDrawCalibration` (geometric steps + µA→mA + net-of-idle, tested) + `platform/context/PowerMeter` + `app/runtime/PowerDrawCalibrator` (ramp→baseline→latch-breaker sweep, fake-meter tested) + `PowerDrawStore` + Tools calibrate UI (prep dialog → drives the Activity window) → `PowerDrawChart`. **Validated owner feedback (refresh()):** TRUE that `SuperDimmingCoordinator.apply` triggered `privilegeManager.refresh()` (2 Binder permission checks) every cycle — but per *cycle*, not per *frame*; FIXED by caching the tier (`tierProvider = { currentTier() }`) and refreshing only on resume (service start + screen-on, AmbientMonitoringService). **Owner fixes:** panic too sensitive → `PanicGate` 3 s post-wake grace + detector reset on wake + stricter inversion (8.0); Dashboard brightness number now animates (`animateIntAsState`); live "Now" markers added to Reactivity (current lux) / Dimming (when engaged) / Taper (when scaling) charts; launcher icon scaled 0.88 (knob inside the r33 safe zone); **settings clamp-on-Apply** (D-085 — `DraftSettingsViewModel.apply` runs `validate()`). **Quality:** dead `LineGraph.kt` deleted; @Volatile audit (no inappropriate uses; fixed ThrottleController's stale "single-writer" header); **owner pushback adopted** — the brittle exact-count `ProvenanceCommentCountTest` → content-based `ProvenanceTest` (floor + rounding/task anchors; never needs bumping on legit additions), and the LOC guard was HONORED by extracting `ProximityTracker` (orchestrator stayed an orchestrator) with a minimal justified 300→310 bump, not a lazy one. **Deprecations:** none in our main source/build scripts; the "Gradle 9.0" notice is AGP-8.7.3-internal (needs an AGP bump, deferred); test-only Robolectric `withIntent`/`Notification.PRIORITY_HIGH` left as-is. **Release:** release-grade `README.md` (AAB-inspired, points contributions upstream) + `CONTRIBUTING.md` + `DEVICE_TEST_SCRIPT.md` (Gate-3); `versionName 0.9.0`/`versionCode 2`; CI `build.yml` + `redirect-external-prs.yml` (separate push — workflow scope). TODO/FIXME = 0. Full ladder GREEN (`:domain:test :platform:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`). No compaction. **→ HUMAN GATE 3.** |
| Gate 3 punch-list (G3-F1…F18) | 2026-06-22 | Opus/high | DONE | (this push) | Worked the 18 owner device findings on this branch (per the binding hand-off note). **Real bugs fixed:** **G3-F3** `AabSettings.validate()` floored minBrightness 1→0 (Misc slider already exposes 0..75; the 0→1 clamp made committed≠draft so the screen stayed perpetually dirty and 0 never stuck; OEM `toDevice` already coerces 0..255 → dimmest, not off); contract range 1..255→0..255; `AabSettingsClampTest`. **G3-F4** QS-tile "already added" on first add — the result `when` mapped `2` (TILE_ADDED = success) to the "already added" toast; corrected to 2→added / 1→already-added (+`dashboard_tile_add_success`). **G3-F5** Dashboard didn't animate on-device — `PipelineCycleRunner` published `lastAppliedBrightness==targetBrightness` only AFTER the multi-second sweep, so the instrument snapped post-settle; now publishes `targetBrightness` (the destination) BEFORE animating and the `BrightnessInstrument` animates toward `target` → rolls DURING the transition. **G3-F6** super-dim residual at circadian Spread 100 daylight — `dimDynamic→0` ⇒ `dim_shell` rounds to 0 but the coordinator still wrote `activated=1` at level 0 (Android Extra Dim stayed engaged); now `level<=0` ⇒ disengage; updated `SuperDimmingCoordinatorTest.circadian_spread100…` to assert disengaged, not level-0-written. **G3-F11** "Reset to auto" no-op when paused — `reapply`→`reapplyProfile` early-returns on `paused` (the override state where you'd reset); rewired `DashboardViewModel.resetToAuto` to the Resume path (clears the pause + unconditionally re-runs Set Initial Brightness) + a hint line. **G3-F15** Alpha graph x-axis was "Lux change" but is a fold change; relabel "Relative lux change %" + rescale to 1..2000 % (task557 labelValues), + live "Now" alpha line. **G3-F16** applied wizard suggestion left the blue "Suggested" line overdrawing the teal curve; suppress the fitted series when its sampled curve matches the live curve (≤1 level). **G3-F2** brightness-curve "Now" cross-hair never rendered (screen never passed currentLux/currentBrightness) — now wired; alpha "Now" added. **G3-F17 (domain fence, evidence-backed):** curve-wizard `tau` default 4.0→**0.001** — task038 act2 (547) always sets `%tau=0.001` before the Java engine, so 0.001 is the operative default; 4.0 was the unreachable Java-header fallback that over-damped every suggestion ("poor quality"). All wizard goldens pass τ explicitly → no golden touched (`wizard_defaultTauIsTheFaithfulAct2Value` locks it); τ also **exposed as a wizard slider** (0.001–5, owner ask). **Docs/UX:** **G3-F1** User Guide/About branding AAB→Tideo + long-press→ⓘ wording; **G3-F7** README shields.io badges; **G3-F8** CONTRIBUTING/redirect-workflow now **triage (comment+label), not auto-close** — app-layer/Android bug-fix PRs welcome, features→AAB; `.github/ISSUE_TEMPLATE/bug_report.md` added; **G3-F9** README + `ShizukuGrantGateway` comment corrected — Shizuku has one runtime use (no-Location Wi-Fi SSID `cmd wifi status`), not "never runtime"; **G3-F12** ip-api.com geo-IP fallback now has a **privacy opt-out toggle** (Circadian → Date & location, `ExperimentPrefsStore.geoIpEnabled`, gated in AppModule) + a README Privacy section; **G3-F13** restricted-settings card adds "tap it anyway" + covers usage-access; **G3-F14** Profiles & Contexts hero toned down (`HeroNavCard(prominent=false)` — no teal edge, resting elevation); **G3-F10** Curve & Brightness icon Settings→Create (removed the dup with Misc) — fuller Material Symbols pass needs `material-icons-extended` (a dependency decision, deferred to owner, D-091). **G3-F18** battery baseline = info, recorded (no action). Full ladder GREEN. No compaction. **→ re-run HUMAN GATE 3.** |
| Gate 3 punch-list R2 (owner device follow-ups) | 2026-06-22 | Opus/high | DONE | (this push) | Second owner round on the Gate-3 build (D-092). **Power-draw parity (task524 — owner supplied the verbatim Java):** the algorithm was already faithful (geometric steps, normalize-1st-to-0 + net-of-idle in `PowerDrawCalibration.postProcess`, nudge+settle latch-breaker in `PowerDrawCalibrator`), but two pieces were missing → "poor results": (1) **white screen** — Tasker measures on a white `FrameLayout` dialog (OLED power is colour-dependent); the rebuild swept brightness over the dark Tools UI. Added `PowerCalibrationOverlay` (fullscreen white `Dialog` + status/progress, drives ITS OWN window brightness — replaces the Activity-window approach); (2) **real dual y-axis** — `PowerDrawChart` faked the mA series by rescaling onto the power axis; now Power on the left axis, real **Current (mA) on a secondary right axis** (`secondaryYRange`), matching the Chart.js graph. **De-jargon:** `power_desc`/prep copy dropped "task524" and now say the screen turns white. **User Guide:** removed the in-HTML teal banner — the teal brand banner is Menu-only (the scaffold already titles the screen). **Context editor overhaul (owner: inferior to Tasker):** triggers are now **collapsible** — a `TriggerSection` per Wi-Fi / Time & day / Location / Battery / Foreground apps with an enable Switch (`trigger_toggle_*`), seeded from whether the rule uses that trigger (persists on edit), showing only what's selected; **radius defaults to 200 m** (never blank); **Save/Cancel moved to a sticky bottom bar** (was pinned top). `RuleEditor` owns its scroll + bottom bar; host simplified. **README:** added Stars + Downloads (+ dynamic Release) shields. **Shizuku honesty:** `CLAUDE.md` "used only in the grant flow, never a runtime binder dependency" was still false → corrected to own the optional runtime SSID use (`ShizukuWifiSsidStrategy` `cmd wifi status`). **Reactivity copy:** "Smoothing thresholds" → "Reactivity thresholds" (they're reactivity dead-zone levels). **Restricted-settings card:** removed the emoji + trimmed the verbosity (kept "tap it anyway" + usage-access). Tests: `AabSettingsClampTest` unchanged; new `contextEditor_triggersCollapsedByDefault_radiusDefaults200_G3`; updated 7 context-editor tests to enable sections / drop bottom-bar scroll. Full ladder GREEN. No compaction. **→ re-run HUMAN GATE 3.** |
| External-AI review triage (4 holes + 1 nit) | 2026-06-23 | Opus/high | DONE | (this push) | Owner forwarded an external AI review claiming 4 "glaring holes" + 1 nit; verified each against real code (D-093). **3 VALID → fixed:** (1) **dead `ScreenStateReceiver`** — manifest `SCREEN_ON/OFF` are never delivered to manifest receivers (the class's own docstring claimed a cold-start role it can't fulfil); removed the manifest `<receiver>` + deleted the class + its test; resurrection is covered by `START_STICKY` (verified `startForeground` runs first on null-intent recreation) + the 15-min `MaintenanceWorker`. (2) **time contexts stall in constant light** (real + parity gap) — `ContextEngine` only re-evaluated time rules on a sensor sample / screen-on, but `TYPE_LIGHT` is on-change so in steady light nothing ticks and a 20:00/Sunset rule fired late; added a `timeJob` that `delay()`s until `millisUntilNextContextWake(nextContextTime)` then fires a TIME eval — matching Tasker prof764's self-scheduling Time context (the resolver comment already said `nextContextTime` "drives prof764's self-scheduling"); doze caveat documented (screen-on eval + MaintenanceWorker remain backstops); new pure `ContextScheduleTest`. (3) **background custom toast blocked (Android 11+)** — `AabFlash`'s fallback used `Toast.setView` (custom view), which the OS silently drops for background apps, so the manual-override flash vanished exactly when the user pulled the shade; fallback is now a plain text `Toast.makeText` (the presenters keep the styled/tap-to-dismiss surfaces for foreground/overlay). **1 REBUTTED:** OEM brightness range / "hidden API" — `Resources.getSystem().getIdentifier(config_screenBrightnessSettingMaximum)` is a framework *resource* lookup, NOT non-SDK reflection, so it is NOT blocked on API 28+ and does not "return 0"; the suggested `BrightnessInfo.brightnessMaximum` is the float (0.0–1.0) system, wrong for the legacy int `Settings.System.SCREEN_BRIGHTNESS` we read/write; the existing `config_screenBrightnessSettingMaximum` read is the correct, framework-blessed way to learn an OEM's int max and already handles 0–1023/0–4095 ranges. Declined an adaptive-`deviceMax` backstop (perturbs the override-detection band for a scenario the config read already covers). **1 NIT (non-issue):** Shizuku `unbindUserService(args, this)` — `this` correctly resolves to the enclosing `ServiceConnection` (a plain `thread{}` is not a receiver lambda); attempted the reviewer's "name it" tweak and it FAILED to compile (`this@ServiceConnection` / a captured `connection` ref are both illegal for an anonymous object), confirming `this` is the only clean form (reverted; added a clarifying comment). Full ladder GREEN. No compaction. |
| Gate 3 sign-off + 1.0.0 release | 2026-06-23 | Opus/high | DONE | (this push) | Owner signed off Gate 3 → all three human gates passed; the Tasker→Kotlin rebuild is feature-complete and parity-verified. Finalization: `versionName 0.9.0→1.0.0` / `versionCode 2→3`; removed the throwaway `/dist/` debug-APK folder pre-merge (owner request); README status → "all gates passed / 1.0.0"; STATE Current-state + Gate-3 findings header marked SIGNED OFF. Completion sweep: TODO/FIXME = 0, `TODO(` = 0, PARITY_CHECKLIST `pending` = 0, no stale live `0.9.0`/"Gate 3 pending" copy (only historical segment-log/RUNBOOK refs retained). Full ladder GREEN. Owner merges to `main` to ship 1.0.0. |
Status values: DONE · PARTIAL · BLOCKED (see failure protocol in CLAUDE.md).

## Current state

**🎉 PROGRAM COMPLETE — GATE 3 SIGNED OFF (2026-06-23), v1.0.0.** All three on-device human gates
passed (Gate 1 core loop, Gate 2 surfaces & tiers, Gate 3 acceptance soak). The Tasker
`Advanced_Auto_Brightness_V3.3` project is fully rebuilt as a native Kotlin/Compose app with
feature parity: PARITY_CHECKLIST is zero-`pending`, TODO/FIXME = 0, all golden parity tests green.
**Release finalization:** `versionName 1.0.0` / `versionCode 3`; the throwaway `/dist/` debug-APK
folder removed pre-merge (owner request); README status → "all gates passed". The two external-AI
review passes were triaged (D-093/D-094): 3 real OS-lifecycle holes fixed (dead `ScreenStateReceiver`,
time-context self-scheduler, background-toast `setView`), 1 panic-sensor IPC hot-loop fixed, and the
brightness-range "hidden API" + Shizuku-`this` + exact-alarm claims rebutted with evidence. Owner
merges this `claude/*` branch to `main` to ship 1.0.0.

**Gate 3 punch-list R2 DONE (2026-06-22) — owner device follow-ups → re-run HUMAN GATE 3.** **Power-draw
faithfulness (task524, owner gave the verbatim Java):** the math + nudge/settle were already faithful;
the "poor results" were the missing **white calibration screen** (OLED power is colour-dependent — now a
fullscreen white `Dialog` overlay driving its own window brightness) and the **real secondary mA y-axis**
(was rescaled onto the power axis). **Context editor reworked** to collapsible per-trigger sections
(seeded from the rule, persist on edit), **radius default 200 m**, and a **sticky bottom Save/Cancel bar**
(was top). **User-Guide teal banner removed** (Menu-only). **Shizuku honesty:** corrected CLAUDE.md's false
"never a runtime dependency" claim (the no-Location SSID path uses `cmd wifi status` at runtime).
**Reactivity** "Smoothing thresholds" → "Reactivity thresholds"; **restricted-settings** card de-emoji'd +
trimmed; **README** Stars/Downloads/Release shields; power-draw "task524" jargon removed from UI copy.
New/updated tests; full ladder GREEN (D-092).

**Gate 3 punch-list DONE (2026-06-22) — all 18 owner device findings (G3-F1…F18) worked → re-run HUMAN
GATE 3.** Worked on this session's branch, carried forward from the S14 branch's content (all of S14
intact — nothing based on bare `main`). **Real bugs fixed:** min-brightness-0 clamp/perpetual-dirty
(G3-F3), QS-tile "already added" on first add (G3-F4 — `2`=TILE_ADDED was mapped to the wrong toast),
Dashboard not animating on-device (G3-F5 — publish the target BEFORE the sweep, animate toward it),
super-dim residual at circadian Spread 100 (G3-F6 — `level<=0` ⇒ disengage), "Reset to auto" no-op while
paused (G3-F11 — route to Resume), alpha-axis mislabel + rescale to % (G3-F15), applied-suggestion line
overdraw (G3-F16 — suppress when it matches the live curve), brightness/alpha "Now" markers (G3-F2).
**Domain-fence, evidence-backed:** curve-wizard `tau` default 4.0→**0.001** (the task038 act2 value the
Java engine actually reads; 4.0 was the unreachable fallback that over-damped suggestions) + `tau` exposed
as a wizard slider (G3-F17) — **no golden touched** (all wizard goldens pass τ explicitly). **Docs/UX:**
branding AAB→Tideo + long-press→ⓘ (G3-F1), README badges (G3-F7), CONTRIBUTING/workflow now triage-not-
auto-close + bug-report template (G3-F8), honest Shizuku-runtime note (G3-F9), ip-api.com geo-IP **privacy
opt-out toggle** + README Privacy section (G3-F12), "tap it anyway"/usage-access restricted-settings
guidance (G3-F13), Profiles & Contexts hero toned down (G3-F14), one within-core menu-icon fix (G3-F10;
fuller Material Symbols pass needs `material-icons-extended` → D-091). G3-F18 (battery baseline) = info,
recorded. New tests: `AabSettingsClampTest`, `WizardParityTest.wizard_defaultTauIsTheFaithfulAct2Value`;
`SuperDimmingCoordinatorTest.circadian_spread100…` updated to assert disengagement. Full ladder GREEN.

**S14 DONE (2026-06-21) — final integration, parity audit & release prep → HUMAN GATE 3.** PARITY_CHECKLIST
is **zero-`pending`** (all 18 resolved; ported claims verified against real code). Two previously-deferred
features were **ported to parity at the owner's request** (parity is the goal, not dropping): **prof759/
task545 proximity damp** (ProximitySensorSource → ProximityTracker → `BrightnessEngine` LuxAlpha ×0.1,
task544 act28/29, additive golden-preserving) and **task524 power-draw calibration** (PowerDrawCalibration
domain math + PowerMeter + PowerDrawCalibrator latch-breaker sweep + PowerDrawStore + Tools UI →
PowerDrawChart). The owner `refresh()` feedback was **validated and fixed** (tier now cached + refreshed on
resume, not 2 Binder checks/cycle). Owner UX fixes: panic 3 s post-wake grace + stricter inversion
(`PanicGate`); Dashboard brightness number animates; live "Now" markers on Reactivity/Dimming/Taper charts;
launcher icon scaled to 0.88; clamp-on-Apply (D-085). Quality: dead `LineGraph.kt` removed, @Volatile audit
clean, and — per owner pushback — the brittle exact-count provenance test became content-based (`ProvenanceTest`)
and the LOC guard was honored by extracting `ProximityTracker` (minimal 300→310 bump, not a lazy one).
Release-grade README + CONTRIBUTING + DEVICE_TEST_SCRIPT; `versionName 0.9.0`; CI `build.yml` +
`redirect-external-prs.yml`. Full ladder GREEN; TODO/FIXME = 0.

**→ GATE 3 RAN (owner device pass) — 18 deviations recorded in "Gate findings" → "Gate 3 (after S14)"
(G3-F1…F18). NEXT SESSION (owner-binding): `git checkout claude/adoring-bardeen-37fb6m` and work the
punch-list ON THIS BRANCH — do NOT branch from `main` (the S14 PR is unmerged, so main lacks all of S14).
Several are real bugs (min-bright-0 clamp, dashboard not animating on-device, super-dim spread-100
residual, QS-tile race, reset-to-auto no-op, alpha-axis label, applied-suggestion line); a few may touch
the domain fence (curve-wizard `tau`/task38 — evidence-backed only). Gate 3 pass → bump to 1.0.0.**

**S13d DONE (2026-06-20) — ALL of S13 (a–d) COMPLETE → next is S14.** The original S13 scope landed: six
real charts (`ui/graph/{Reactivity,Dimming,Circadian,Experiment,Taper,PowerDraw}Chart.kt`) built on the frozen
`ChartCanvas` per the `BrightnessCurveChart` template, filling the S12.8b `ChartSlot` swap points on
Reactivity/Circadian/Super Dimming/Tools; `AboutScreen` + `UserGuideScreen` ported from the scene docs (Chart.js
ack dropped); `PlaceholderScreen`/`ChartPlaceholder` deleted; **G2R-F80** wires the User Guide as the
post-onboarding first-run destination. Owner UI feedback fixed in the same pass: Circadian ⓘ-help affordance
restored, Menu wordmark on one line, teal banner is Menu-only (`AabTopBar` de-teal'd), Dashboard status pill no
longer wraps, and the **Dashboard now shows the active Profile + Context** (new in-memory
`LiveRuntimeState.activeProfile`). Fence honoured (`ChartCanvas`/domain/golden/runtime/gradle/manifest
untouched). Full ladder green (`:app:testDebugUnitTest`=283); `Text("…")` literals held at ceiling 92.

**S13c' DONE (2026-06-20) — the polish pass (owner design spec, visual-elevation sibling of S13d): the app
now reads as a precision instrument. Bundled IBM Plex Sans/Mono with two tabular instrument type roles
(`AabDataDisplay`/`AabDataCaption`, `titleLarge`→SemiBold); `AabCard` surface ladder (`Resting`/`Hero`/`Well`
with hairline + teal accent edge); `KeyValueRow` rebuilt as a caption/big-tabular-gold-value/unit readout line
(+ `DiagnosticCard` gold runs → Plex Mono); a Dashboard `BrightnessInstrument` hero (applied 0–255 + eased teal
`Canvas` track + status pill, greys out when off) over a lux·circadian·context strip; Profiles & Contexts
decluttered via a Profiles/Rules `SegmentedButton`, per-row overflow (clears the S13c overflow deferral) and a
collapsed legacy-import section; spacing normalised, brand banner + nav icons polished. Palette frozen,
fence honoured, all testTags intact, `Text("…")` literals 89/92. Full ladder green. Next: S13d (real charts +
About/User-Guide static screens; remove placeholders).**

**S13c DONE (2026-06-20) — screen-by-screen Material 3 restyle: the S13b component library is now applied
across all 10 screens (m3_audit §3 rows 1–10 ✅) — Menu on `HeroNavCard`/`NavRow`+`AabCard`, derived/auto
readouts on gold `KeyValueRow`, settings clusters grouped into `AabCard`, empty hints → `EmptyState`, app-wide
screen motion via `AabMotion` on `AppNavGraph`. Behaviour-preserving (all test-pinned tags + draft/validation
semantics intact), no recolor, fence honoured; `Text("…")` literals 92→89 (ceiling 92). Owner extra: launcher
logo rebuilt as the Tasker sun-with-slider-in-rays on AAB teal+gold (adaptive `ic_launcher_*`). Deferred to S14
(recorded): Profiles/Contexts 3-dot overflow + app-picker fixed height. Full ladder green. Next: S13d (real
charts into the `ChartSlot` swap points + About/User-Guide; remove placeholders).**

**S13b DONE (2026-06-20) — component library built from the m3_audit §2.5 blueprints: `AabCard`,
`KeyValueRow` (bold-gold data-pop B4), `EmptyState`, `HeroNavCard` (B2, promoted+press-motion), `NavRow`
(B3), `ActionButtonBar` (B5), `AabMotion` helpers, unified `SettingField`/`SettingFieldSpec` (delegates to
the S12.5b primitives → behaviour-preserving), and `SectionHeader(divider=…)` (B1, opt-in). 10 instantiation
tests in `ComponentLibraryTest`; fence honoured, ceiling 92 unchanged, full ladder green. The components are
built-not-yet-applied (screens still use their existing compositions) — S13c adopts them screen-by-screen and
checks off m3_audit §3. Next: S13c restyle.**

**S13a DONE (2026-06-20) — design-system foundation: `Dimens`/`Shape`/`Type` tokens wired into `TideoTheme`
(behaviour-preserving) + `docs/rebuild/design/m3_audit.md` redesign plan (all 9 screens + merged Profiles/
Contexts) incl. ColorScheme role-mapping; owner "Pro-Tool" UI-overhaul feedback adopted into the S13b
component blueprints (audit §2.5) — structure/contrast/gold-data-pop honoured, the `#1A1C1E`/new-tertiary
recolor REJECTED per the fixed-palette guardrail (D-077).**

**S12.8 (a–d + b'/b'') DONE → Gate 2 (5th re-test) device-passed. S12.9a DONE (repo hygiene + lint-as-gate +
README seed, D-071). S12.9b DONE (circadian-dimming wiring + override-flash + Shizuku-UX, D-072). S12.9c DONE
(nested `AabSettings` group views + fail-fast `valueFor` + `ProfileLoadResult` error card + legacy-parse throw +
DataStore map/version test + signed dimSpread −100..100 validator&clamp fix + validation_audit + honest
placeholders + privilege/permission docs, D-073). S12.9d DONE (i18n surface: `strings.xml` 2→47 entries +
`HardcodedStringCheckTest` ratchet; runtime test backfill — OverrideMonitor/AndroidContextSignalSource/
AabToastAccessibilityService [MaintenanceWorker deferred: needs work-testing dep; ScreenStateReceiver →
S12.9e]; `LiveRuntimeState` staleness gate + Dashboard amber banner + 5s reset watchdog, D-075). S12.9e DONE
(concurrency safety + controller decomposition — `AppProcessScope`/`goAsync()`, killed the `lateinit` controller
cycle via `ControllerHook`/`ControllerHookHolder`, `ContextEngine` 6 signal volatiles → one `SignalSnapshot`
StateFlow, controller 5→2 `@Volatile`, split the 596-LOC controller into orchestrator +
`PipelineCycleRunner`/`PipelineDebugEmitter`/`PanicHandler`, comment-only `BrightnessEngine` rounding header;
all provenance preserved, no behaviour change, D-076). S12.9f DONE (Profiles & Contexts IA merge — folded the two
destinations into one `ProfilesContextsScreen`: saved-profiles surface + a "Context rules" section, per-rule editing
in a full-screen modal; `AppRoute.Contexts` removed, `Profiles` relabelled "Profiles & Contexts", Menu hero cards
collapsed to one; behaviour-preserving plumbing, no engine/store/domain change; S13c restyles, D-070). **→ ALL of
S12.9 (a–f) DONE → re-run HUMAN GATE 2 (6th) against an S12.9 build, then S13.** S12.9 lands repo hygiene
(XML + extraction-java out of the tree, dead API-26 branches, lint-as-gate, README seed, `:data` ghost,
`Main.kt`→`MainActivity.kt`), the one real runtime-parity fix (**G2R-F90** circadian dimming inert —
`SuperDimmingCoordinator` hardcoded `dimDynamic=null`; FIXED app-layer in S12.9b, no domain/golden change), schema
ergonomics (nested `AabSettings`, fail-fast `valueFor`, surfaced profile-load errors, spread validation,
validation/privilege/permission/datastore docs), i18n strings + test backfill + a `LiveRuntimeState` staleness
gate, concurrency hardening (`AppProcessScope`/`goAsync`, kill the `lateinit` controller cycle, volatile audit
incl. `CircadianWindowProvider`/`ThrottleController`/`AabFlash`, decompose the 596-LOC controller), and the
**Profiles+Contexts → one-screen merge** (plumbing; S12.9f). **S13 is promoted to Opus/high (a–d):** Material 3
design-system foundation + component library + screen-by-screen restyle + the original charts/About/Guide
(D-070). The review's mis-diagnosed items were rejected (AnimationRunner stride re-port, Haversine de-dup,
`Main.kt` Tiramisu-branch deletion, Shizuku "hang") — see D-069.

**S12.7h DONE (2026-06-15) → ALL of S12.7 (a–h) COMPLETE → HUMAN GATE 2 RE-RE-TEST (4th) READY.** S12.7h
landed the last two Gate-2 re-re-test findings (D-060; G2R-F38/F39): **F38** the Tasker Profile dashboard's
full settings list with **gold changed-vs-default** highlighting (`SettingsDisplay.kt` `displayRows`/
`changedCount` — explicit no-reflection per-key extractor; `SettingsDiffList.kt` component) wired into a new
**`LoadProfileDialog`** ("Load Anyway" preview→Apply modal), the **Save** dialog, and a **"View current
settings"** dashboard dialog on Profiles; **F39** the Circadian **fixed Date/Lat/Lon element**
(`CircadianDateLocationCard` + `ExperimentPrefsStore`/`CircadianExtrasViewModel`) defaulting to **today +
current location** when unset, preview-only (never enters profiles/export). domain/ + golden vectors +
ChartCanvas stayed fenced. Ladder GREEN (`:app:testDebugUnitTest`=167).

**S12.7i COMPLETE (2026-06-15): all four deferrals (F70/F71/F72/F73) closed → ALL of S12.7 (a–i) DONE.**
F73 landed first (app-layer solar-window wiring, D-061). This final push closed the last three (D-062):
**F70** legacy "Load" now parses the REAL nested-JSON Tasker config (the parser, not the wiring, was the
bug — replaceAll+reapply was already wired) and commits+reapplies the parsed settings; **F71** the manual-
override settle waits `%AAB_CycleTime` (task567 act7), never the `%AAB_Throttle` cooldown (which gates only
the task544 main loop), so an override is detected inside the throttle window; **F72** a "Clear time"
affordance nulls a rule's `timeRange`. `domain/` + golden vectors + ChartCanvas stayed fenced. Ladder GREEN
(`:app:testDebugUnitTest`=176).

**S12.8a follow-up (2026-06-16, owner device re-test — D-063):** 4 of the 10 needed a second pass:
**F75** the override alert is now the SAME foreground notification (NOTIFICATION_ID) raised to the
high-priority channel on the rising edge — never a separate ID — so it cannot stack/leave a stale alert
(the prior fix posted a 2nd ID that didn't get cancelled on a new override). **F77** the detector
triggered upright too; now requires a **sustained ≥5-frame, dominant-y inversion** with a heavier low-pass
(α 0.9). (3rd pass: also a **sign-convention fix** — Android reads +9.8 on the axis pointing up, so
upside-down is `gravity.y ≈ −9.8`; the gate is `gy < −7`, not `gy > +7` which matched upright.)
**F78** root cause: I floored the throttle at `throttleDefaultMs` which equals `MaxSteps×MaxWait+10`, so
it always read the ceiling; now uses the engine's actual `BrightnessPolicyOutput.transitionDurationMs`
(loops×wait+10, golden task543) with NO floor (ceiling only via the idle watchdog). **F88** the foreground
fallback was a non-tappable Toast; added an in-app tap-to-dismiss surface (`AabFlashHost`/`FlashPill`
registered as the foreground `AabFlash` presenter; priority: global a11y overlay → in-app → Toast).
F58/F65/F86/F74/F76/F71 confirmed correct on device. Ladder GREEN (`:app:testDebugUnitTest`=188).

**S12.8a DONE (2026-06-16):** the **Runtime** sub-segment of the Gate-2 4th-re-test salvage shipped
(D-063) — 10 runtime findings: F74 Resume-inert (consumer-not-started after a service kill →
`ACTION_RESUME` now `ensureRunning()` first), F75 override-alert no-stack, F76 Pause action removed from
the ongoing notification, **F77 NEW prof769 panic detector** (upside-down + shake → SOS vibration +
brightness 255 + Service Off; new `PanicSensorSource`/`PanicGestureDetector` + VIBRATE perm), F78 throttle
= actual `steps×wait` + task566/prof754 idle→ceiling watchdog (`ThrottleController`), **F65 PWM-sensitive
now dims below the floor via Extra Dim** (task700 `finalDimLevel`), F58 Super Dimming live readout, F86
LuxAlpha display clamp (engine untouched), F88 tap-to-dismiss flashes. domain/ + golden + ChartCanvas
fenced (called only). Ladder GREEN (`:app:testDebugUnitTest`=187). Debug APK in `/dist/` for Gate testing.

**S12.8c DONE (2026-06-16):** the **Settings & profiles** sub-segment shipped (D-064) — 5 findings:
**F85 (CRITICAL)** removed the bogus editable `thresholdDynamic` (it is the computed task544 output,
not an input) → schema **v2→v3** (migration bumps the stamp; the stale key drops via `ignoreUnknownKeys`
on read); **F59** resolved by that removal (the live card already shows the VALUE as a %); **F84** diff
list uses friendly labels + excludes global/derived keys; **F70** legacy load is task570-defaults-THEN-
diffs (already correct — proven by a no-inheritance regression) and the real bug was an integer-handling
**class** (decimal-encoded ints silently dropped) fixed across both parser paths + wizard-apply (round,
don't truncate/drop → Form1A sticks); **F62** the Tools wizard now gates on ≥9 **real** points (no ghost-
prior inflation). domain/ + golden vectors + ChartCanvas stayed fenced (called only). Ladder GREEN
(`:app:testDebugUnitTest`=196).

**S12.8d DONE (2026-06-16):** the **Circadian time & location** sub-segment shipped (D-065) — 3 findings:
**F73** the UTC frame was already Tasker-faithful (act0/act59 both `%…%86400`); the real ~1h-early evening
ramp was the location-null → default-window fallback, so the fix was a robust location + a target-instant
DST-aware tz offset, NOT the golden math; **F39** the fixed Date/Location now resolve independently
(date-only/loc-only/both) and drive the live scaling (no longer preview-only); **F83** ported task90's
once-a-day location acquisition with skip-when-fixed + ip-api.com geo-IP fallback (`GeoIpLocationClient`,
INTERNET perm + ip-api-scoped cleartext config). domain/ + golden vectors + ChartCanvas stayed fenced
(SolarCalculator called only). Ladder GREEN (`:app:testDebugUnitTest`=203, `:platform:test`=47).

**S12.8b DONE (2026-06-16) → ALL of S12.8 (a–d) COMPLETE → re-run HUMAN GATE 2 (5th).** The final UI-polish
sub-segment shipped (D-066) — rebased LAST onto the merged a+c+d (settled shared screen content): **F79**
Dashboard redesign (Pause dropped, Resume-on-override only, purposeful live cards); **F81** new `ChartPager`
(`ui/components/GraphScaffold.kt`) puts the graph **above** its settings on every chart-host screen and
**swipes** between related graphs (S13 fills the `ChartSlot`s); **F82** `GraphSettingsGroup` per-graph
grouping; **F68** single-line Sunset token; **F87** taller context app list; **F89** permissions audit
(declared ACCESS_BACKGROUND_LOCATION; DUMP wontfix signature-only; usage-stats kept). domain/ + golden
vectors + ChartCanvas stayed fenced. Ladder GREEN (`:app:testDebugUnitTest`=209).

**Next:** **re-run HUMAN GATE 2 (5th)** against an S12.8 build; then **S13** (remaining charts via the S12.7g
template + About/User Guide, which carries F80 — and **inherits the `ChartSlot`/`ChartPager` contract** from
S12.8b for the chart slots). The Gate-2
(4th) results are recorded under "Gate findings → Gate 2 (4th re-test)":
most of F33–F73 are **confirmed fixed**; reopened with corrected specs = **F39/F62/F65/F70/F73**; follow-ups
on F35/F50/F58/F59/F68; still-open **F45/F67/F71**; and **20 new findings G2R-F74…F89** (incl. **CRITICAL
F85**: `%AAB_ThreshDynamic` must not be a user setting — schema change, re-scope the domain fence).

---

(historical) **S12.7f + S12.7g DONE (2026-06-15; g merged onto f via PR #40) → only S12.7h remains.** S12.7g enriched the
Curve & Brightness chart and extended `ChartCanvas` (the one sub-segment allowed to — D-059;
G2R-F36/F55/F62/F66/F69) on top of the already-merged S12.7f (live readouts/labels). The chart engine now
carries **axis labels**, a **`niceTicks` 1/2/5×10ⁿ** y-tick generator (0/50/…/250 — no 191.25), **log-x
from 0.1**, an opt-in **interactive scrub** readout (drag/tap → vertical line + per-series value box, pure
`seriesValueAt`), an opt-in **legend** Row (solid/dashed swatches), and a tappable **`ChartScatter`** with
`nearestIndex` hit-testing. The curve view: a **FIXED dashed-gold reference** sampled from the *committed*
snapshot (F69 — no longer tracks the draft; replaces the old moving "Taper" overlay), the **wizard fit**
("Suggested" series ≥9 pts, F62), and **tap-to-delete** override points (F36 → `OverridePointDeleteDialog`
→ `OverridePointStore.delete`). The S12.7f `live` readout card on Curve & Brightness was preserved through
the merge. domain/ + golden vectors stayed fenced. Ladder GREEN (`:app:testDebugUnitTest`=159).

S12.7f (merged first) had closed the per-screen live-readout / label-fidelity batch (G2R-F56/F58/F59/F60/F61):
Curve & Brightness + Misc gained Tasker live-readout cards (Reactivity already had one, now with the
threshold as a **percentage**, F56); Reactivity's dynamic-threshold help substitutes the live value behind
the ⓘ reveal (F59); Misc's "Scale" turns read-only "(auto)" under circadian scaling (F60); form2A/3A →
"Zone 2/3 alignment" (F61); `rememberToaster` now routes through the teal `AabFlash` channel. **Next: S12.7h
(rich editors / scene fidelity, F38/F39), then owner re-tests Gate 2 again.**

⚠️ **NEW owner finding 2026-06-15 (G2R-F73, DEFERRED — domain-fenced):** `%AAB_ScaleDynamic` erroneously
drops **below 1 around 07:13 in the morning** — suspected a **UTC vs local-time** bug in the solar/dynamic-
scale ramp. The dynamic-scale + solar math lives in `domain/` (`DynamicScaleEngine`/`SolarTimes`,
`secondsOfDay` is derived from UTC wall-clock — see D-039d/the S5 note), which S12.7 fences off, so it
cannot be fixed in this stage. Needs a domain segment (re-open with golden-vector proof per the coding
rules). Logged for triage; **do not "fix" by guessing.** (Numbered F73 because S12.7f claimed F70/F71/F72.)

**(historical) S12.7e DONE (2026-06-14) → S12.7 f–h remain.** S12.7e closed the debug/toast-infrastructure batch (D-058;
G2R-F48/F49/F50/F51/F52). The headline is **F50's global flashes**: a new opt-in
`AabToastAccessibilityService` (presentation-only `AccessibilityService` drawing a
`TYPE_ACCESSIBILITY_OVERLAY` window — no `SYSTEM_ALERT_WINDOW`, never reads window content) registers as
the presenter of a **new process-wide `AabFlash` channel** that ALL flashes (debug + context-load) now
route through. That channel also fixes **F51** (each `show` cancels the previous flash → no stacking)
and **F52** (`setDebugLevel` `AabFlash.cancel()`s on Off **and** `reapply`s so the pipeline's stale
`ContextEngine` effective-snapshot picks the new category up immediately — the real root cause of the
"not instant" bug). **F48:** a pure `DynamicScaleDebugGate` fires the dynamic-scale flash only ~2 min
into a dawn/dusk transition (scale changing) then ≤ once/2 min, never per light change. **F49:** the
unprivileged super-dimming fallback now flashes the computed black-overlay hex (`OVERLAY_PREVIEW`,
task653/654 math via golden `SoftwareDimming.dimShell`). Live Debug gained an opt-in global-flash card
(status + Accessibility deep-link, re-polled on resume). domain/ + golden vectors stayed fenced. Ladder
GREEN (`:app:testDebugUnitTest`=147). **Next: S12.7 f–h (largely disjoint; rebase before push), then
owner re-tests Gate 2 again after S12.7h.**

**(historical) S12.7d DONE (2026-06-14) → S12.7 e–h remain.** S12.7d closed the permissions/SSID/first-boot-nav batch
(D-057; G2R-F33/F41/F57). The headline is **F41's `_GetWifiNoLocation V3` port**: `AndroidWifiInfoReader`
now reads the connected SSID **without Location** by trying the no-Location strategies first — Shizuku
`cmd wifi status` (via a new `ShizukuShell.exec` + an `exec(String[])` method added to the existing AIDL
user service) then in-process `dumpsys wifi` (parsing the `mWifiInfo`/`COMPLETED` line) — and only falling
back to the Location-gated `NetworkCallback` as a last resort (`WifiSsidStrategies.kt`; strategies are
constructor-injectable so the source-selection order is unit-tested). Setup also gained the missing
**Location grant step** (F41-perm, optional) and a **restricted-settings hint card** for sideloaded installs
(F33, `isLikelySideloaded` → "Allow restricted settings" + Open-App-info). **F57:** finishing onboarding now
lands on the **Menu hub** (not the dead Dashboard) via `completeOnboarding()` with `popUpTo(Onboarding,
inclusive)` so Back exits cleanly. domain/ + golden vectors stayed fenced. Ladder GREEN
(`:app:testDebugUnitTest`=140). **Next: S12.7 e–h (largely disjoint; rebase before push), then owner
re-tests Gate 2 again after S12.7h.**

**(historical) S12.7c DONE (2026-06-14) → S12.7 d–h remain.** S12.7c closed the context-system parity batch (D-056;
G2R-F42/F43/F44/F45/F47/F67/F68). The headline is **F45's smart location listener**: a continuous
`requestLocationUpdates` flow (`LocationReader.locationUpdates`, NETWORK+GPS, last-known seed, (0,0)
null-island filter) hosted in the FGS scope replaces the on-demand passive read that died on backgrounding
and reported `loc 0.0,0.0`; the `ContextEngine` collects it **gated on `tokens.usesLocation`** (Tasker's
`[LOC]`-in-`%AAB_ContextCache` cost gate, owner-confirmed) with a **≥100 m haversine debounce** before
firing a LOCATION eval (kills the near-constant input-blocking toasts). Also: typed
`currentLocation()`→`LocationResult` with a call-time permission recheck + fresh fix (F42); priority-ordered
rule list (F43); legacy-imported profiles auto-registered as rule targets (F44); a day-of-week chip picker
wired to the already-supported `ContextTriggers.days`/overnight-wrap resolver (F67, domain fenced);
SUNRISE/SUNSET tokens showing today's resolved time in gold (F68); and the enriched Context Automation
debug toast (trigger·context·profile·rule+priority, F47). domain/ + golden vectors stayed fenced. Ladder
GREEN (`:app:testDebugUnitTest`=138). **Next: S12.7 d–h (d–h largely disjoint; rebase before push), then
owner re-tests Gate 2 again after S12.7h.** Resolves the long-standing context deviation (d) "location is
passive-only".

**(historical) S12.7b DONE (2026-06-14) → S12.7 c–h remain.** S12.7b fixed the runtime feedback surfaces (D-055):
the **F65 super-dimming Extra-Dim bug** (root cause: the pipeline fed `dimming.apply` the PWM-FLOORED
target, so the `target < dimmingThreshold` engage gate was never true — now feeds the un-floored engine
target, task646 `%AAB_CurrentBright`, so the secure `reduce_bright_colors` layer darkens below the floor);
a **high-priority vibrating override notification + toast** on the override rising edge (F35, new
`pausedByOverride` flag + `manual_override` HIGH channel); Resume on the notification verified (F40); and a
**live QS tile** (F63, `onStartListening` live-collect + `requestListeningState` on each state publish).
domain/ + golden vectors stayed fenced. Ladder GREEN (`:app:testDebugUnitTest`=132). **Next: S12.7 c–h
(largely disjoint; rebase before push), then owner re-tests Gate 2 again after S12.7h.**

**(historical) S12.7a DONE (2026-06-14) → S12.7 b–h remain.** S12.7a ported the real task567/task696 manual-override
logic (D-054): the band+debounce override detector (F34, was an exact-match self-write check that false-
fired on OEM drift), a post-init settle-suppression window (F64, kills the start/reinit/resume/QS-on echo
race), and the F46 override-vs-context-rule semantics (manual profile load = override surfaced in the Menu;
a context rule active is no longer labelled one). domain/ + golden vectors stayed fenced. Ladder GREEN
(`:app:testDebugUnitTest`=128). **Next: S12.7 b–h (b–h largely disjoint; rebase before push), then owner
re-tests Gate 2 a 4th time after S12.7h.**

**(historical) ALL of S12.6 (a–e) DONE; GATE 2 RE-RE-TESTED (2026-06-14) → 36 new findings G2R-F33…F68 → S12.7 (a–h) is
the next work (planned in RUNBOOK, all Opus/high).** The owner re-tested the S12.6e dist/ APK on-device:
"definitely going in the right direction" but a further batch of parity/behaviour gaps remains (see "Gate 2
RE-RE-TEST" below) — manual-override false-positives + spurious instant-override-on-start race (need the real
task567 logic: anim mutex + target-vs-actual delta), no Resume on the notification, QS tile not live, super
dimming doesn't Extra-Dim, Wi-Fi SSID should use the Shizuku/dump `_GetWifiNoLocation V3` path (not
Location), context location listener dies on background + 0.0,0.0 reads + needs day-of-week rules,
"manual load = override" semantics, missing per-screen live readouts, global/Accessibility toasts +
toast-cancel + instant-off, and several label/wiring polish items + the polished load/save/rule modals.
**Gate 2 stays NOT signed off.** The dist/ APK + README were **removed** (owner about to merge S12.6).
**Next: execute S12.7 a–h, then re-test Gate 2 again.**

**(historical) ALL of S12.6 (a–e) DONE → HUMAN GATE 2 RE-TEST (again) is next.** S12.6e (D-053) closed the last batch:
the **label + verbatim long-press-help audit** (G2R-F19/F20/F21) — `TaskerHelp.kt` carries the 30 decoded
Flash help strings, surfaced via an "ⓘ" reveal on every Reactivity/Curve/Misc/SuperDimming control, with
the labels re-matched to the Tasker scene labels; the owner-flagged **"delta factor" was a label/help bug,
NOT a wiring bug** (`%AAB_DeltaFactor` is the sensor-smoothing alpha factor — binding was correct →
relabelled "Smoothing Δ"). **Context editor:** Wi-Fi SSID now read via a `suspend NetworkCallback` with
`FLAG_INCLUDE_LOCATION_INFO` returning a typed `SsidResult` (targeted messages, fixes API-29+ redaction,
G2R-F22), live location lat/lon/radius + "use current location" added, and the time inputs open the M3
**TimePicker** modal (G2R-F28). **Onboarding** shows usage access as **optional by default** (G2R-F24);
**runtime context loads toast** via `ContextLoadSink` (G2R-F25). **Live Debug Performance & Timings**
reached full Tasker parity (luxAlpha / cycle / throttle / last-animation steps×wait / last-update,
G2R-F29) by surfacing existing engine output into `PipelineState` (no domain change). domain/, golden
vectors and ChartCanvas stayed fenced. Ladder GREEN (`:app:testDebugUnitTest`=122, `:platform:test
:domain:test :app:assembleDebug :app:lintDebug`). **Next: HUMAN GATE 2 — re-test AGAIN (all G2R-Fn + the
original Gate-2 set). Then S13.**

**(historical) S12.6a + S12.6b + S12.6c + S12.6d DONE → S12.6e remains.** S12.6d (profiles + legacy import + reset +
Apply-gate, D-052) landed real user-profile management: `UserProfileStore` (built-ins seeded once,
Save-current-as, overwrite, **Restore factory profiles**, G2R-F15) read by `AppProfileCatalog` so context
rules can target user profiles (closes D-042c); **SAF folder grant** legacy import from
`Download/AAB/configs` via `LegacyConfigImporter` (no MANAGE_EXTERNAL_STORAGE, G2R-F16); per-screen reset
on every draft screen (G2R-F17); **block-Apply on CRITICAL validation errors** (form2A/3A<0,
form2C>zone1End now `Severity.CRITICAL` → Apply disabled, G2R-F18/**D-052** sanctioned deviation);
manual-load **context lock + Resume** (G2R-F30). Two owner findings reported mid-segment were also fixed
(app-layer only): **G2R-F31** battery % from/to in the context rule editor, **G2R-F32** curve-wizard report
now shows/copies the full verbose `diagnosticsLog`. domain/, golden vectors and ChartCanvas stayed fenced.
Ladder GREEN (`:app:testDebugUnitTest`=116, `:platform:test :domain:test :app:assembleDebug :app:lintDebug`).
**Next: S12.6e, then HUMAN GATE 2 re-test after S12.6e.**

**(historical) S12.6a (IA & naming) + S12.6b (glass-box diagnostics) DONE → S12.6c/d/e remain (parallel window).**
The AAB Menu is the home hub; Super Dimming / Circadian renames + Dashboard last-sample fix landed in
S12.6a. **S12.6b** added the runtime glass box: a dedicated **Live Debug Info** scene in the Menu (live
`%AAB_*` readout grouped per the Tasker debug scene, gold values) + live `DiagnosticCard`s on Reactivity
(threshold + dead zone) and Circadian (uncompressed vs true scale); the 10-category debug selector moved
off Misc onto Live Debug and is now a GLOBAL preference (preserved across profile/reset/draft applies,
like `detectOverrides`); debug toasts are AAB-teal. `PipelineState` now carries `threshDynamic` +
`scaleDynamic` (surfaced from the existing engine output, no domain change). Ladder GREEN
(`:app:testDebugUnitTest`=102, `:app:assembleDebug :app:lintDebug :domain:test :platform:test`).
**A new owner finding surfaced during S12.6b and is DEFERRED to S12.6c: G2R-F27/D-050 — PWM-sensitive
mode does not lock the hardware brightness floor (`pwmSensitive` is persisted/toggled but never read by
the pipeline → task661/task698 floor unimplemented).** **Next: S12.6c/d/e, then HUMAN GATE 2 re-test
after S12.6e.**

**(historical) S12.5 UI salvage DONE + GATE-2 RE-TEST done → S12.6 planned (next work).** The owner re-tested the
S12.5c build on-device (2026-06-14): two S12.5 fixes re-confirmed (min-bright graph, QS tile) but a
second, larger batch of parity/behaviour gaps surfaced — **25 findings G2R-F1…F25** (see "Gate 2
RE-TEST" below), structured and routed into a new **S12.6** (a–e, all Opus/high; RUNBOOK). Four binding
owner decisions taken: (1) the **AAB Menu becomes a real home screen** (hub + back-target; Dashboard
becomes a live-status screen); (2) **block Apply on critical validation errors** (form2A/form3A<0,
form2C>zone1End — sanctioned deviation from Tasker's advisory model); (3) **all profiles editable +
"Restore factory profiles"**; (4) **legacy import via a one-time SAF folder grant** to
`Download/AAB/configs` (no MANAGE_EXTERNAL_STORAGE). **Gate 2 stays NOT signed off** — re-tested again
after S12.6e. The dist/ Gate-2 re-test APK + README are committed for the owner (delete before merge).

**(historical) S12.5c (feature & behaviour fidelity) DONE.** The remaining six Gate-2 behaviour gaps are closed (D-048), all in the UI/app/platform-glue
layer (domain/, golden vectors, ChartCanvas API untouched): **G2-F8** — loading a profile no longer
disables manual-override detection (`detectOverrides` is a global preference, not a task626 snapshot
key, so it is preserved across `mergeProfile` + `applyProfile`/`replaceAll`). **G2-F12** — Flash/toast
feedback is back (`rememberToaster`): Apply, profile apply/reset/import-export, context save/delete,
wizard apply/copy. **G2-F14** — the context-rule editor is faithful: a manifest `<queries>` LAUNCHER
block fixes the empty app list, apps show icons+labels, a "use current Wi-Fi" button fills the SSID
(`WifiInfoReader.currentSsid()`), SUNRISE/SUNSET time tokens are one-tap, and a usage-access prompt
deep-links when an app trigger is set. **G2-F15** — the 10 `%AAB_Debug` categories now Flash runtime
toasts via a `DebugSink`/`ToastDebugSink` gated on the live debugLevel (pipeline LIGHT_EVAL/ANIMATION/
DYNAMIC_SCALE/GRAPH_METRICS + a real SKIP_ANIMATIONS behaviour; SUPER_DIMMING in the coordinator;
CONTEXT_AUTOMATION/LOCATION in the engine), and the `%AAB_Test` wizard diagnostics copy to the
clipboard in Tools. **G2-F9** — super-dimming engagement is verified (logic correct, now driven by the
S12.5b reapply) with a SUPER_DIMMING diagnostic toast + a precise AOSP-secure-key/OEM-variance note for
the device gate (no logic bug). **G2-F17** — the QS tile subtitle reflects Off/Active/Paused/Starting.
Build GREEN across the full ladder (91 app tests); lint baseline unchanged. **Next: re-run HUMAN GATE 2.**

**(historical) S12.5b (interaction model: preview→Apply, sliders, grouping) DONE.** The parameter screens now port
AAB's actual editing model (D-047): each is backed by a per-screen, NavBackStackEntry-scoped
**`DraftSettingsViewModel`** — edits mutate a **draft** only, the graph previews the draft live, and an
**Apply** button commits draft→DataStore **and forces an immediate pipeline re-evaluate** (an UNLIMITED
`ACTION_REAPPLY`→`controller.reapply()` control event, gated on serviceEnabled). Back/Discard reverts
to committed (dirty-back is confirmed); each numeric field shows the committed/active value in
`[brackets]` when the draft differs, and is **seed-once-per-epoch** to fix the mid-edit text corruption
(G2-F1/F7). Six settings now render as **bounded M3 sliders** with the exact Tasker ranges (MinBright
0–75, MaxBright 150–255, AnimSteps 0–100, MinWait 1–99, MaxWait 2–100, TaperMidpoint 130–240; G2-F3/F13).
A dedicated **Misc** screen is re-added (G2-F2): brightness range + animation + notifications + debug
live there, Curve & Brightness keeps only the curve-zone coefficients, Animation & Dimming only super
dimming + PWM (now **mutually exclusive**, G2-F10) with a **circadian-gated** dim-spread field (G2-F11).
Validation parity: zone2End<zone1End NaN guard (G2-F6), dangerously-low-scale advisory (G2-F5), the
curve chart floors at minBrightness (G2-F4). profile-load / reset / import also force a re-eval (G2-F16).
Scope stayed in the UI/settings/runtime-control layer — **domain/, golden vectors, ChartCanvas public
API untouched**. The remaining Gate-2 gaps (context editor G2-F14, toasts G2-F12, debug→runtime toasts
G2-F15, profile-load-disables-overrides G2-F8, super-dimming engagement G2-F9, QS paused state G2-F17)
are **S12.5c**. Build GREEN across the full ladder.

**(historical) S12.5a (design language + app shell) DONE.** The app wears the AAB **teal + gold**
identity (D-046): a static brand `ColorScheme` (dynamic colour opt-in OFF, DayNight kept), a branded
teal top header with a hamburger that opens the **AAB-Menu nav drawer** (Compose rebuild of scene
menu.md/L4462 — gold-sun banner + grouped destinations, current-route highlight), and **hero cards**
for Profiles + Contexts on the Dashboard (the flat OutlinedButton nav list is gone). Addresses
**G2-F18**. Scope was strictly the UI layer — domain/runtime/chart-engine/validator untouched.

**(historical) S12 (settings & tools screens + chart engine core) DONE but GATE 2 FAILED → merged + salvaged in
S12.5 (D-045).** Gate 2 found the UI "miles off" the Tasker app (generic Material, no AAB design
language/preview-Apply model/sliders; findings G2-F1..F18). The branch is merged as-is (domain/runtime/
chart-engine sound); the UI is rebuilt in S12.5a/b/c. The wiring below still describes what shipped: all seven
parameter/tool/profile screens are real Compose M3 over the screen_map target set: Curve & Brightness
(fields + task583/707 validator red-errors + live form2A/form3A + the BrightnessCurveChart template),
Reactivity (thresholds + the **DetectOverrides** toggle deferred from Gate 1), Animation & Dimming
(animation + derived throttle + **ELEVATED-gated DimmingEnabled** + PWM — both Gate-1 deferrals now
verifiable at Gate 2), Dynamic Scale, Contexts (rule CRUD + app picker over the S10 store), Tools
(curve-wizard runner + 10-label debug selector + calibration entry), Profiles (apply/reset +
JSON/legacy import-export). The reusable `ChartCanvas` engine + the one `BrightnessCurveChart`
template instance are the copy-this-pattern base for S13. Deferred to S13/later (D-044): the six
non-template charts' render, on-device power-draw measurement, in-app debug log, unprivileged overlay
dimming. Step-0 anonymous-handler triage committed before screen work. Build GREEN.

(historical) S1 through S9b DONE + **GATE 1 PASSED**; **S10 (context override engine) DONE**; **S11 (UI shell +
onboarding + dashboard) DONE** — parallel window C complete. The app now has a real Compose M3
navigation shell (dynamic color + DayNight): a live **Dashboard** (lux/brightness readout, master
switch, pause/resume, active-context line, privilege tier badge, service health) backed by
`DashboardViewModel` over the DataStore (source of truth, G1-F3 pattern) + a process-wide
`LiveRuntimeState` bridge the service republishes its pipeline StateFlow into; and an **Onboarding**
stepper implementing task563's 8 gates/order via ActivityResultContracts (notifications →
WRITE_SETTINGS → optional ELEVATED → usage-access). The Shizuku WRITE_SECURE_SETTINGS grant is now
fully wired through a bound user service (AIDL, closes D-032 — no reflection). Parameter/tool/profile
screens (S12) and About/Guide + charts (S13) are labelled placeholders that navigation resolves to.
Build is GREEN across the full ladder.

(historical) S1 through S9b DONE → GATE 1 READY. Build is GREEN across
the full ladder: `:domain:test`, `:platform:test`, `:app:testDebugUnitTest`, `:app:assembleDebug`,
`:app:lintDebug`. The runtime is the real sensor-event-driven Tasker pipeline: BrightnessPipelineController
owns all runtime state and drives a single serialized cycle (drop-not-queue MainLoop mutex); AnimationRunner
does per-frame writes with read-back override detection; OverrideMonitor + controller implement
detect/pause/resume; AmbientMonitoringService is a specialUse FGS with a live notification
(Pause/Resume/Reset/Disable) and SCREEN_ON/OFF → reinit/hibernate; SuperDimmingCoordinator drives the
ELEVATED secure reduce_bright_colors layer from the cycle; BrightnessTileService toggles the service from
a QS tile; BootCompletedReceiver self-starts on boot. **Legacy graph fully removed** (BrightnessPolicyEngine,
EvaluateAndApplyBrightnessUseCase, Ports, SystemAdapters, WebViewGraphFallback, PermissionOnboardingStateMachine);
AppModule is now the real DI root.

## Next up

- **S13 — UI finalization (a–d, Opus/high), IN PROGRESS.** **S13a DONE** (design-system foundation:
  `Dimens`/`Shape`/`Type` tokens + `docs/rebuild/design/m3_audit.md` redesign plan incl. ColorScheme
  role-mapping + the owner "Pro-Tool" UI-overhaul feedback folded into the S13b component blueprints,
  audit §2.5 — fixed-palette guardrail upheld, D-077). **S13b DONE** (component library: `AabCard`,
  `KeyValueRow`, `EmptyState`, `HeroNavCard`, `NavRow`, `ActionButtonBar`, `AabMotion`, unified
  `SettingField`/`SettingFieldSpec` [delegates to S12.5b primitives], `SectionHeader`+divider — all driven
  by S13a tokens & §2.5 blueprints; 10 instantiation tests; D-078). **S13c DONE** (screen-by-screen restyle:
  all 10 m3_audit §3 rows ✅ — Menu on `HeroNavCard`/`NavRow`+`AabCard`, derived/auto readouts on gold
  `KeyValueRow`, settings grouped in `AabCard`, empties → `EmptyState`, app-wide `AabMotion` screen
  transitions; behaviour-preserving, fence honoured, `Text` literals 92→89; owner-requested launcher logo
  rebuilt as the Tasker sun-with-slider-in-rays on AAB teal+gold; 3-dot overflow + app-picker height deferred
  to S14; D-079). **S13c' DONE** (owner design-spec polish pass, sibling of S13d, m3_audit §6: bundled IBM
  Plex Sans/Mono + tabular instrument type roles; `AabCard` surface ladder `Resting`/`Hero`/`Well`;
  `KeyValueRow` readout-line rebuild + `DiagnosticCard` mono gold; Dashboard `BrightnessInstrument` hero +
  eased teal `Canvas` track; Profiles/Rules `SegmentedButton` + per-row overflow [clears the S13c overflow
  deferral] + collapsed legacy import; spacing/banner/icon polish — palette frozen, fence honoured, testTags
  intact, literals 89/92; D-081). **S13d DONE** (static content & charts — the original S13 scope: six real
  charts on the frozen `ChartCanvas` filling the S12.8b `ChartSlot`s [Reactivity/Alpha, Circadian-scale/Taper,
  Dimming/Circadian-Dimming, Power-Draw], `AboutScreen`+`UserGuideScreen` ported [Chart.js ack dropped],
  `Placeholder`/`ChartPlaceholder` deleted, **G2R-F80** User-Guide-first-run; plus owner UI fixes — Circadian
  ⓘ-help, one-line Menu wordmark, Menu-only teal banner, Dashboard pill no-wrap, Dashboard active
  Profile+Context via `LiveRuntimeState.activeProfile`; D-082). **→ ALL of S13 (a–d) DONE → S14.**
- **S13e — final pre-S14 owner punch-list (Opus/high), DONE (D-086).** CI node20→node24 (checkout/setup-java
  @v5); "Manage profiles & Export" folded under one collapsible; **transition-factor chart bug fixed** (was
  hard-coded 0.1); **home-screen widget** (battery-efficient, event-driven from the FGS publish path) +
  Dashboard quick actions (Reset-to-auto, Add-QS-tile, Add-widget). The "date doesn't affect circadian graphs"
  report was **retracted by the owner** (verified correct). domain/golden/ChartCanvas fenced; ladder green.
- **S14 DONE — final integration, parity audit & release prep (D-087…D-090) → HUMAN GATE 3.** Zero-`pending`
  checklist; proximity damp (D-087) + power-draw calibration (D-088) ported to parity; `refresh()` tier
  caching (D-089); guard tests reworked + clamp-on-Apply + UX/icon/chart fixes (D-090). README/CONTRIBUTING/
  DEVICE_TEST_SCRIPT shipped; `versionName 0.9.0`. Owner runs `DEVICE_TEST_SCRIPT.md`; pass → 1.0.0.
- **S14 carry-forward — settings safety (D-085) — RESOLVED in S14 (D-090c).** `DraftSettingsViewModel.apply()`
  used to commit the raw draft to DataStore **without** the mapper's `validate()` clamp, so out-of-range
  values entered on a draft screen reached the engine. S14 runs `AabSettings.validate()` on commit (the same
  per-field clamp every other persistence path uses; `dimSpread` stays signed −100..100). Test:
  `DraftSettingsViewModelTest.apply_clampsOutOfRangeValues`.
- **HUMAN GATE 1** (RUNBOOK "Human gates"): install app-debug.apk, grant WRITE_SETTINGS, verify the
  core loop (sensor → animate, slider → pause/resume, screen off/on → reinit, reboot → self-start,
  notification actions; optionally grant WRITE_SECURE_SETTINGS → super dimming engages below threshold).
  Findings → "Gate findings" below.
- Parallel window C: **S10** (context override engine) DONE ∥ **S11** (UI shell + onboarding) DONE.
- **S12.5 — UI salvage (a/b/c) COMPLETE** (brief in RUNBOOK, D-045). **S12.5a DONE** (teal+gold design
  language + AAB-Menu nav drawer + hero cards — D-046). **S12.5b DONE** (preview→Apply draft model +
  `[committed]` brackets + pipeline re-run + bounded sliders + Misc regrouping + validation parity —
  D-047). **S12.5c DONE** (context-editor fidelity G2-F14, toasts G2-F12, debug→runtime toasts G2-F15,
  profile-load-keeps-DetectOverrides G2-F8, super-dimming verify+diagnose G2-F9, QS-tile paused state
  G2-F17 — D-048).
- **GATE-2 RE-TEST DONE (2026-06-14) → 25 findings G2R-F1…F25** (see "Gate 2 RE-TEST"). Gate 2 NOT
  signed off.
- **S12.6 — Gate-2 re-test salvage (a–e, all Opus/high)** is the NEXT work (brief in RUNBOOK). Owner
  decisions are binding (menu-as-home; block-Apply-on-critical-errors; all-profiles-editable+factory-
  restore; SAF folder grant for legacy import). **S12.6a DONE** (menu-as-home reshape + Super Dimming /
  Circadian renames + Dashboard last-sample fix — landed the nav/testTag reshape b/c/d/e depend on).
  **S12.6b DONE** (Live Debug scene + per-screen diagnostic cards + global debug selector + teal toasts).
  **S12.6c DONE** (reapply-uses-fresh-settings + min-bright runtime fix G2R-F11/F12 + override-point
  capture/persistence G2R-F13 + curve overlay G2R-F14 + override false-positives G2R-F26/D-049 +
  PWM-sensitive hardware-floor clamp G2R-F27/D-050 — D-051). **S12.6d DONE** (user-editable profiles +
  overwrite + factory-restore G2R-F15 + SAF legacy import G2R-F16 + per-screen reset G2R-F17 +
  block-Apply-on-critical-error G2R-F18/D-052 + manual-load context-lock/Resume G2R-F30; plus owner
  findings G2R-F31 battery % editor + G2R-F32 verbose wizard report). **S12.6e DONE** (label +
  verbatim long-press-help audit G2R-F19/F20/F21 [delta-factor was a label bug not a wiring bug] +
  context Wi-Fi `SsidResult`/live-location G2R-F22 + time-picker modal G2R-F28 + usage-access-optional
  G2R-F24 + runtime context-load toast G2R-F25 + Live Debug Performance & Timings parity G2R-F29 —
  D-053). **ALL S12.6 sub-segments complete.** Domain/ + golden vectors + ChartCanvas API stayed fenced.
- **GATE 2 RE-RE-TEST DONE (2026-06-14) → 36 findings G2R-F33…F68** (see "Gate 2 RE-RE-TEST"). Gate 2
  still NOT signed off.
- **S12.7 — Gate-2 re-re-test salvage (a–h, all Opus/high)** (brief in RUNBOOK). **a DONE** (D-054:
  band+debounce override detector from task696 + post-init settle-suppression window + F46 override/
  context-rule semantics). **b–h remain.** Split:
  **a** manual-override engine (transcribe task567: anim mutex + target-vs-actual delta) + instant-override
  race + override semantics (F34/F64/F46) — DONE; **b** runtime feedback surfaces — override notification+vibration
  +toast, notification Resume, QS tile live refresh, super-dimming Extra-Dim fix (F35/F40/F63/F65) — DONE; **c**
  context system — location listener lifecycle/0.0,0.0/debounce, use-current-location perm recheck, priority
  ordering, legacy-profile rule targets, context-automation debug toasts, day-of-week rules+midnight wrap,
  sunrise/sunset gold value (F42/F43/F44/F45/F47/F67/F68) — DONE; **d** permissions & Wi-Fi & first-boot nav —
  restricted-settings guidance, Location grant in Setup, `_GetWifiNoLocation V3` Shizuku/dump SSID path +
  grant guidance, first-boot→Menu (F33/F41/F57) — DONE (D-057); **e** debug/toast infra — Accessibility global toasts,
  cancel-previous, instant debug-off, dynamic-scale timing, overlay-preview colour toast (F48/F49/F50/F51/
  F52) — DONE (D-058); **f** per-screen live readouts + labels — readout blocks, reactivity %, var-substitution+info-gate,
  Misc auto-scale, form2A/3A labels (F56/F58/F59/F60/F61); **g** charts & curve view (may lift the
  ChartCanvas fence) — axis labels + interactive scrub + log-x, curve-fitting on the curve view, reference-
  line legend (F55/F62/F66 [+F36 tap-to-delete points]); **h** rich editors / scene fidelity — polished
  load/save/create/edit-rule modals + full settings list w/ gold changed-vs-default (F38), Circadian
  Date/Lat/Lon element (F39). domain/ + golden vectors stay fenced except S12.7g (charts) which may extend
  ChartCanvas.
- **HUMAN GATE 2 — RE-TEST (4th) AGAIN** after S12.7. Re-verify all G2R-Fn + the original Gate-2 set.
- **GATE 2 (4th re-test) → S12.8 salvage (a–d, all Opus/high)** (brief in RUNBOOK). **a DONE** (D-063:
  override notif Resume/no-stack, no-Pause notif, throttle reinit + actual-steps, panic SOS detector,
  PWM Extra-Dim, dimming readout, LuxAlpha display clamp). **c DONE** (D-064: F85 thresholdDynamic removed
  + schema v3 migration, value-only help, friendly diff labels + exclusions, legacy load defaults+diffs,
  ghost-point wizard gate). **d DONE** (D-065: DST/location ramp framing, fixed date/loc apply, ip-api
  fallback). **b DONE** (D-066: Dashboard redesign F79, ChartPager graph-above+swipe F81, per-graph grouping
  F82, single-line Sunset F68, taller app list F87, permissions audit F89). **ALL S12.8 (a–d) COMPLETE.**
  domain/ + golden vectors + ChartCanvas stayed fenced throughout.
- **HUMAN GATE 2 — RE-TEST (5th)** after S12.8 — **device-passed.**
- **S12.9 — engineering-quality & parity-debt remediation (a–f, Opus/high) — ALL DONE** (brief in RUNBOOK
  §S12.9, D-069). **a DONE** (D-071: XML+Java-extracts out of the tree, dead API-26 branches + `DeadApiCheckTest`,
  `Main.kt`→`MainActivity.kt`, `:data` ghost, lint-as-gate [baseline deleted, `abortOnError=true`, `app/lint.xml`,
  VectorPath/Monochrome/DataExtractionRules/ObsoleteSdkInt fixed], README seed). **b DONE** (D-072: **G2R-F90**
  circadian dimming wired from `PipelineState.scaleDynamic`, app-layer; override-toast→`AabFlash` G2R-F91;
  Shizuku three-state availability). **c DONE** (D-073: nested `AabSettings` group views + fail-fast `valueFor`
  + `ProfileLoadResult` error card + legacy-parse throw + DataStore map/version test + signed dimSpread
  −100..100 validator&clamp fix + validation_audit + honest placeholders + privilege/permission docs).
  **d DONE** (D-075: i18n strings 2→47 + `HardcodedStringCheckTest` ratchet, runtime test backfill,
  `LiveRuntimeState` staleness gate + Dashboard amber banner + watchdog). **e DONE** (D-076: `AppProcessScope`/
  `goAsync`, killed the `lateinit` controller cycle, volatile audit, decomposed the 596-LOC controller into 4
  files). **f DONE** (D-070: **Profiles+Contexts → one-screen merge** — `ProfilesContextsScreen` folds both
  destinations [saved-profiles surface + "Context rules" section, per-rule editing in a modal]; `AppRoute.Contexts`
  removed, `Profiles` relabelled "Profiles & Contexts", Menu hero cards collapsed to one; behaviour-preserving
  plumbing, S13c restyles). Mis-diagnosed audit items rejected in D-069 (AnimationRunner stride, Haversine,
  `Main.kt` Tiramisu deletion, Shizuku "hang"). domain/ + golden vectors + ChartCanvas stayed fenced (one
  comment-only `BrightnessEngine` header exception in S12.9e). **→ re-run HUMAN GATE 2 (6th), then S13.**
- **S13 — UI finalization (a–d, Opus/high, promoted from Haiku — D-070)** follows S12.9 on the serial spine.
  S13a design-system foundation (`Dimens`/`Shape`/`Type` + `docs/rebuild/design/m3_audit.md`), S13b component
  library (`SettingField`/`AabCard`/`EmptyState`/motion), S13c screen-by-screen Material 3 restyle (incl. the
  merged Profiles/Contexts screen from S12.9f), S13d the original charts — fill the **`ChartSlot.content` lambdas
  inside `ChartPager`** (S12.8b/D-066) over read-only `ChartCanvas.kt`, replace the remaining `ChartPlaceholder`s,
  plus About/UserGuide content + F80 (User Guide after onboarding). Color scheme fixed (teal+gold); the actual
  design decisions are generated in S13a (scaffolding + guardrails).
- Carried for S12.5/S13/S14 (D-040, D-044): unprivileged overlay dimming (task698 DC-like / 653/654) is
  NOT wired (S9b did the ELEVATED secure path only); DimDynamic (circadian dim strength, task646
  ScalingUse branch) passes null pending real solar windows (D-039d) — **the G2R-F90 root cause, now wired in
  S12.9b** (`SuperDimmingCoordinator` reads `PipelineState.scaleDynamic`, app-layer; `dimShell`/golden unchanged);
  proximity damp (task545) still unwired; **curve-wizard override-point capture/persistence still NOT wired (D-044c) — deferred from
  S12.5c (the wizard runs against an empty set → "need ≥ 9"); S13/S14 should add runtime override-point
  capture so the wizard has real input.**


> _(Deviations & discoveries ledger D-001… extracted to `DEVIATIONS_LEDGER.md`.)_

## Blockers

(none)

## Gate findings

### Gate 1 (after S9b) — PASSED (core loop; F2/F5 → Gate 2)

**Human re-test 2026-06-13 11:15 UTC:** owner confirmed **G1-F1, G1-F3, G1-F4 fixed and behaving
per the README** on-device. Combined with the originally-passing checks (1,2,4,5,6a,6b), the
Gate-1 core loop is signed off. The two remaining findings are owner-deferred (not failures):
F2 (manual-override pause) and F5 (super dimming) need the S12 settings UI to enable their
toggles (DetectOverrides / DimmingEnabled both default Off, Tasker parity) → **verify at Gate 2**.

**Triage outcome (Gate-1 punch-list session, 2026-06-13, full detail D-041):**
- **G1-F1 crash → FIXED + CONFIRMED** (platform writes swallow SecurityException; POST_NOTIFICATIONS
  requested at launch; "permission needed" notification hint).
- **G1-F3 Disable desync → FIXED + CONFIRMED** (SettingsViewModel observes the DataStore).
- **G1-F4 panic/resume → FIXED + CONFIRMED** (panic is now a full stop = task528 %AAB_Service Off).
- **G1-F5 super dimming → DEFERRED S12** (DimmingEnabled defaults off, no UI yet) + tier-refresh
  sub-bug FIXED.
- **G1-F2 override pause → DEFERRED S12** (DetectOverrides defaults off, no UI yet — Tasker parity).
- Throwaway `dist/` re-test APK removed after sign-off (it lives in branch history only).

#### Original human findings (preserved)

**Test Execution Context:** 
The tester executed the Gate 1 instructions exactly as provided. The `tideo-auto-brightness-gate1-debug.apk` was installed (bypassing the expected Google Play Protect warning). Permissions were manually granted via Android Settings/App Ops: `POST_NOTIFICATIONS` (after initial crash), `WRITE_SETTINGS` (Modify system settings), and `WRITE_SECURE_SETTINGS` (via ADB/Shizuku, verified in Shizuku authorized apps). 

## Passed Checks

*   **Step 1 (Service & Notification):** Service enabled successfully. Notification appeared showing live `Lux X --> Brightness Y` updates.
*   **Step 2 (Smooth Animation):** Covering the light sensor resulted in brightness animating down smoothly. Moving into bright light resulted in brightness animating up smoothly.
*   **Step 4 (Screen Off / On Re-init):** Display cycled off in the dark, then on in the light. Brightness instantly snapped to the correct target value (instant set on wake is correct/acceptable behavior).
*   **Step 5 (Reboot Self-Start):** Rebooting the device successfully triggers the service to start automatically. `BootCompletedReceiver` is functioning correctly.
*   **Step 6a (Pause Action):** Notification "Pause" action successfully stops brightness changes and triggers manual override state.
*   **Step 6b (Reset Action):** Notification "Reset" action successfully triggers panic behavior, setting brightness to maximum (correct Tasker parity for Task528).

## Failed / Anomalous Checks

**G1-F1: App crashes on first launch (missing permissions)**
*   **Observation:** App instantly crashes on launch before any notification permission prompt appears. Crash is resolved only by manually granting permissions via Android Settings/App Ops.

**G1-F2: Manual override detection failing (System slider grab does not pause pipeline)**
*   **Observation:** Dragging the *Android system brightness slider* mid-animation locks the UI slider in place, but the Tideo app continues to overwrite brightness as lighting conditions change. No "Paused" notification appears.

**G1-F3: State desync between Notification 'Disable' and App UI**
*   **Observation:** Tapping "Disable" in the notification successfully stops the service and removes the notification. However, opening the app afterwards shows the main service toggle as "On".

**G1-F4: Pipeline fails to resume after Panic Reset**
*   **Observation:** Selecting "Reset" sets brightness to 100% (correct). However, selecting "Resume" afterward updates the notification to "Monitoring ambient light", while the brightness remains locked at 100% and does not react to lighting changes.

**G1-F5: Super Dimming not activating**
*   **Observation:** After granting `WRITE_SECURE_SETTINGS` (verified via Shizuku authorized applications), reducing ambient light below the dimming threshold does not engage Android's Extra Dim / reduce bright colors feature.
### Gate 2 (after S12) — findings recorded (triage pending decisions)

**Human test 2026-06-13 ~18:15 UTC.** Onboarding incl. Shizuku binding smooth. Many individual
fields/validators work (see PASSED). But the owner's headline verdict: **"miles off the Tasker
version — UX is poor, screens restructured, behaviours don't match Tasker; I expected a faithful
port."** Root cause (self-assessed): S12 built a generic Material settings app, violating CLAUDE.md
"port behaviour exactly / modernise the how not the what". The interaction MODEL was not ported.

**PASSED:** permissions onboarding + Shizuku; form2C>zone1End red error; form2A/form3A live; safety
warning @1000lux; min-wait>max-wait error; transition-factor>0.5 warning; taper-midpoint>maxBright
warning; time-context rule loads its profile (min-bright kicks in); reset-to-defaults; QS tile toggles.

**FINDINGS (G2-Fn):**
- **G2-F1 (parity, major) — no temporary-preview / Apply model.** Tasker AAB edits TEMP values that
  drive the graph, then an Apply commits them and shows the committed/active value in `[brackets]`
  next to each setting. S12 commits every keystroke instantly, no preview, no bracketed active value.
  **→ ADDRESSED by S12.5b (D-047a/b):** per-screen `DraftSettingsViewModel` (draft→Apply, `[bracket]`).
- **G2-F2 (parity/structure) — field grouping wrong.** min/max/offset/scale + animation settings are
  on the **Misc** scene in Tasker; S12 scattered them onto Curve & Brightness / Animation & Dimming.
  (Note: the 20→9 consolidation itself is the owner-approved screen_map/S2 plan; this is a grouping
  error WITHIN that, vs the extraction.) **→ ADDRESSED by S12.5b (D-047d):** re-added Misc screen.
- **G2-F3 (parity/UX) — bounded sliders replaced by unbounded free-text.** Tasker uses bounded
  sliders for min/max brightness, taper midpoint (130–240), animSteps, etc. (D-017/D-018: 6 sliders).
  S12 used free-text everywhere → e.g. min-bright range shown as 1..255 with no bound, taper midpoint
  free text. **→ ADDRESSED by S12.5b (D-047c):** 6 `IntSliderSettingField`s with the exact ranges.
- **G2-F4 (bug) — min brightness doesn't update the curve graph.** BrightnessCurveChart floors at 0,
  not minBrightness, so the curve floor never moves. **→ ADDRESSED by S12.5b (D-047g):** floors at min.
- **G2-F5 (validation gap) — scale=0.01 gives no "dangerously low curve" warning.** Tasker warns.
  **→ ADDRESSED by S12.5b (D-047g):** advisory `scale<0.5` rule.
- **G2-F6 (bug) — zone2End < zone1End → form3A shows NaN with no warning.** Need a guard + warning.
  **→ ADDRESSED by S12.5b (D-047g):** validator rule + NaN-guarded readout.
- **G2-F7 (bug) — numeric text entry corrupts.** NumberSettingField re-seeds from the committed value
  mid-edit: 8.8 → backspace → … → typing 8.8 yields "8.80.0". Want empty/null over a forced 0.
  **→ ADDRESSED by S12.5b (D-047b):** epoch-seeded field, empty allowed.
- **G2-F8 (runtime — CLARIFIED by owner) — manual override detection is reliable; loading a profile
  DISABLES it.** Owner confirmed overrides work consistently EXCEPT after a profile load, which leaves
  override detection off. Real bug for S12.5 (the profile-apply path must not clear/disable the
  DetectOverrides runtime state; likely the reapply/setInitialBrightness path or the
  effectiveSettings swap dropping detectOverrides). Investigate the ContextEngine/profile merge +
  controller reapply.
  **→ FIXED by S12.5c (D-048a):** `detectOverrides` is a global preference (not a task626 snapshot
  key) → preserved across `mergeProfile` + `applyProfile`/`replaceAll`; test added. Re-verify at Gate 2.
- **G2-F9 (runtime/dimming) — super dimming does not engage Android Extra Dim even with ELEVATED.**
  DimmingEnabled now persists (S12) but reduce_bright_colors does not activate. Likely shares the
  no-re-eval root cause (F16) and/or OEM secure-key differences; needs device investigation.
  **→ ADDRESSED by S12.5c (D-048e):** engagement logic verified (correct + unit-tested) + now driven by
  the S12.5b reapply (the F16 root); added a SUPER_DIMMING debug toast (engage / why-not) so the device
  tester can localize it. If it logs "ON level N" but no visible dim → OEM secure-key variance, report
  the device at Gate 2.
- **G2-F10 (parity) — PWM-sensitive and super-dimming are not mutually exclusive.** Tasker disables
  one when the other is enabled. **→ ADDRESSED by S12.5b (D-047f):** each toggle clears the other.
- **G2-F11 (parity + bug) — dim spread editable while circadian/scaling disabled (should be gated);
  dim-spread label is wrong.** **→ ADDRESSED by S12.5b (D-047f):** gated on `scalingEnabled`, relabelled.
- **G2-F12 (parity/UX) — Flash/toast actions only render inline; no toasts anywhere.** Tasker uses
  toasts for confirmations/warnings/help (longclick). S12 converted all to supportingText/banners.
  **→ ADDRESSED by S12.5c (D-048b):** `rememberToaster()` wired on Apply / profile apply-reset-import /
  context save-delete / wizard apply-copy; help text stays inline (matching Tasker).
- **G2-F13 (parity/UX) — taper midpoint is unbounded free text (should be a 130–240 slider).** (⊂ F3)
  **→ ADDRESSED by S12.5b (D-047c):** taper-midpoint slider 130–240 on Dynamic Scale.
- **G2-F14 (feature gaps) — context rule editor weak:** no "get current SSID" helper; no SUNRISE/SUNSET
  for from/to (resolver supports the tokens — editor doesn't expose them); foreground-app list is tiny
  (Android 11+ package-visibility: needs `<queries>`/LAUNCHER or QUERY_ALL_PACKAGES) + no app icons;
  no usage-access prompt when an app trigger is added.
  **→ ADDRESSED by S12.5c (D-048c):** `<queries>` LAUNCHER block + app icons, "use current Wi-Fi"
  (`WifiInfoReader.currentSsid()`), SUNRISE/SUNSET token buttons, usage-access prompt + deep-link.
- **G2-F15 (gap) — debug selector persists but does nothing.** %AAB_Debug = 10 runtime TOAST categories
  (D-023); no runtime debug output is wired.
  **→ ADDRESSED by S12.5c (D-048d):** `DebugSink`/`ToastDebugSink` Flash all 10 categories at runtime,
  gated on the live debugLevel; `%AAB_Test` wizard report → clipboard in Tools.
- **G2-F16 (bug, high value) — settings/profile changes don't re-run the pipeline.** Applying a profile
  in the dark changes the numbers but not the screen (no new sensor event → drop-not-queue → no
  re-eval). Time-context switching DOES apply (it fires ContextChanged). Tasker re-runs "Advanced Auto
  Brightness" on save/apply. Need a settings-change → forced re-eval control event (likely also fixes F9).
  **→ ADDRESSED by S12.5b (D-047e):** `ACTION_REAPPLY`→`controller.reapply()` on Apply/profile-load
  (F9 still needs the device check in S12.5c).
- **G2-F17 (minor) — QS tile:** works; unclear whether it reflects paused state.
  **→ ADDRESSED by S12.5c (D-048f):** tile subtitle now shows Off/Active/Paused/Starting from
  LiveRuntimeState.
- **G2-F18 (design language — major) — the app does not look or feel like AAB.** The Tasker project
  has a distinctive **teal + gold** design language, the **project name in a header up top**, a
  **hamburger / nav-drawer menu** (the AAB Menu scene), and **hero cards** for Profiles and Contexts.
  S12 shipped default Material 3 (dynamic color), a plain top bar, and a flat list of outlined nav
  buttons on the Dashboard — generic, not AAB. (Owner's examples are illustrative, NOT exhaustive: the
  whole visual/interaction identity needs to match the Tasker app.)
  **→ ADDRESSED by S12.5a (D-046):** teal+gold brand `ColorScheme` (dynamic colour off), branded teal
  top header with the project name + hamburger, the AAB-Menu `ModalNavigationDrawer` rebuild, and
  Profiles/Contexts hero cards replacing the flat button list. Re-verify the full look/feel at Gate 2.

**Decision (owner, 2026-06-13 19:00):** MERGE this branch as-is (S12 compiles + tests green; domain/
runtime/chart-engine are sound) and **salvage the UI in a new S12.5** (see RUNBOOK; split a/b/c).
Gate 2 stays open and is re-tested after S12.5. **Gate 2 NOT signed off.** D-045 records the plan.

**S12.5 salvage complete (S12.5a D-046 ∥ S12.5b D-047 ∥ S12.5c D-048):** all 18 findings now have an
ADDRESSED/FIXED disposition (F1–F7,F10,F11,F13,F16 → S12.5b; F18 → S12.5a; F8,F9,F12,F14,F15,F17 →
S12.5c). The on-device re-test (below) confirmed two fixes and surfaced a fresh, larger batch → **S12.6**.

### Gate 2 RE-TEST (after S12.5c) — findings recorded → S12.6 (Gate 2 still NOT signed off)

**Owner on-device re-test 2026-06-14 ~08:00–09:30 UTC.** The S12.5 reskin is "a decent improvement" but
a substantial second batch of parity/behaviour gaps remains. Findings structured below as **G2R-Fn**
(Gate-2 Re-test) and routed to the S12.6 sub-segments (RUNBOOK). The owner's verbatim Tasker diagnostic
snippets are preserved (gold `#FFC107` highlight = the AAB value colour).

**RE-CONFIRMED FIXED (S12.5):** min-brightness moves the curve graph (S12.5b G2-F4); QS tile works
(S12.5c G2-F17).

**FINDINGS:**

*Information architecture & naming (→ S12.6a):*
- **G2R-F1** Back from a settings screen returns to the Dashboard; it should return to the **menu**.
- **G2R-F2** The Profiles + Contexts **hero cards belong in the menu**, not on the Dashboard.
- **G2R-F3** "Animation & Dimming" is **misnamed — it is Super Dimming**; rename.
- **G2R-F4** "Dynamic Scale" should be **renamed "Circadian"**.
- **G2R-F5** Dashboard always shows **"Last sensor sample: never"** regardless of activity (bug).

*Glass-box diagnostics & live debug (→ S12.6b):*
- **G2R-F6** There is **no extensive Live Debug Info scene** — vital for the glass-box metrics.
- **G2R-F7** Every settings screen should carry a few **live diagnostic cards**. Reactivity example:
  "Current threshold `[%AAB_ThreshDynamic]` at `[%SmoothedLux]` lx; Sensor dead zone `[%AAB_ThreshAbsLow]`
  lx to `[%AAB_ThreshAbsHigh]` lx" (values in gold #FFC107).
- **G2R-F8** Circadian example card: "Uncompressed scale `[%AAB_ScaleDynamic]` at `[%TIME]`; True scale
  `[%AAB_ScaleDynamicCompress]` at `[%AAB_CurrentBright]` brightness (`%AAB_MinBright`–`%AAB_MaxBright`)".
- **G2R-F9** The **debug-category selector must be GLOBAL** — it must NOT change on profile load — and
  belongs on the live debug-info scene, not Misc.
- **G2R-F10** Minor: the debug toasts should use the **characteristic teal colour**.

*Pipeline behaviour correctness (→ S12.6c):*
- **G2R-F11** Applying a profile manually **does not take effect until a light change**.
  **→ FIXED by S12.6c (D-051a):** `ContextEngine.reevaluate()` re-reads the fresh baseline before
  `controller.reapply()` so a manual Apply/profile-load re-evaluates immediately.
- **G2R-F12** **Min brightness is ignored in actual behaviour — appears stuck at 10.**
  **→ FIXED by S12.6c (D-051a):** same stale-effectiveSettings root as F11; min-bright threads correctly
  through the mapper→engine; controller regression test added.
- **G2R-F13** **Manual override points are not recorded.**
  **→ FIXED by S12.6c (D-051b):** `OverridePointStore` persists the points the pipeline detects; the
  wizard reads the recorded set.
- **G2R-F14** The brightness curve used to **overlay all recorded override points**; the fitted curve only
  appeared with **> 8 points**.
  **→ FIXED by S12.6c (D-051c):** `BrightnessCurveChart` overlays the points as dots + shows the fitted
  curve only at ≥9 (ChartCanvas unchanged).
- **G2R-F26** (owner-reported 2026-06-14, post-S12.6a) **Manual-override FALSE POSITIVES on rapid light
  changes.**
  **→ FIXED by S12.6c (D-051d):** task567 act8 settle-wait re-read in `handleOverride` (pause only if
  still ≠ our last applied) + device-exact AnimationRunner read-back (`isOnScreenSelfWrite`, kills OEM
  round-trip drift). Single-latest marker (D-049 #2) retained per D-034 (decision recorded).
- **G2R-F27** (owner-reported 2026-06-14, post-S12.6a) **PWM-sensitive mode does not lock the brightness
  floor.**
  **→ FIXED by S12.6c (D-051e):** `applyPwmFloor` clamps the hardware write up to `dimmingThreshold` when
  `pwmSensitive && target<threshold` (task698 step 3), in runCycle + setInitialBrightness.

*Profiles & persistence (→ S12.6d):*
- **G2R-F15** **Cannot save a custom profile**, and want to be able to **overwrite existing profiles** too
  (as the Tasker project allows).
  **→ FIXED by S12.6d:** `UserProfileStore` — built-ins seeded once, Save-current-as, overwrite-in-place,
  delete, Restore factory profiles; `AppProfileCatalog` reads it so context rules target user profiles.
- **G2R-F16** **Cannot load a legacy profile** from `Download/AAB/configs` — the app doesn't see that
  directory (only `Download/AAB` + my own folders), likely a scoped-storage / Tasker-ownership conflict.
  **→ FIXED by S12.6d:** SAF folder grant (`OpenDocumentTree`→persisted permission) + `LegacyConfigImporter`
  lists/loads `*.json` via DocumentsContract; no MANAGE_EXTERNAL_STORAGE. Single-file picker kept as fallback.
- **G2R-F17** Settings screens have **no reset-to-defaults button**.
  **→ FIXED by S12.6d:** `DraftSettingsScaffold` Reset action on all 5 draft screens (resets only that
  screen's fields to the task570 baseline + toast; user Applies to commit).
- **G2R-F18** **Invalid settings are appliable** (e.g. form3A negative). [owner-decision 2: block.]
  **→ FIXED by S12.6d (D-052):** the 3 task583 form errors are `Severity.CRITICAL`; `DraftApplyBar` Apply
  is disabled (with a hint) while one stands. Advisory rules still only warn.
- **G2R-F30** (NEW, S12.6d) manual profile load did not pause context automation / offer Resume.
  **→ FIXED by S12.6d:** applyProfile/replaceAll latch `contextOverride=true`; `ContextEngine.reevaluate()`
  honours the lock; Profiles screen Resume banner → `resumeContextAutomation()`.
- **G2R-F31** (owner-reported during S12.6d) the context rule editor has **no battery percentage from/to**.
  **→ FIXED by S12.6d:** battery % min/max fields added to the rule editor (BatteryTrigger min/max — the
  domain resolver already evaluated them; this was a pure UI gap).
- **G2R-F32** (owner-reported during S12.6d) the **curve-fitting report is far less verbose than Tasker's**.
  **→ FIXED by S12.6d (app-layer only, domain fenced):** the engine already produced the full Tasker-style
  `%AAB_Test` report in `CurveSuggestionResult.diagnosticsLog` — the Tools screen showed only the 4-line
  summary. It now renders + copies the FULL diagnosticsLog (zone boundaries, curve params, per-zone
  R²/nRMSE/bias, fit stability).

*Labels, tooltips, context editor & onboarding (→ S12.6e):*
- **G2R-F19** Some **labels don't match their meaning** (e.g. *delta factor*). Tasker's long-press shows an
  explanatory toast: *"Controls how much to smooth out sensor readings. Higher values react faster to
  small light changes, but may increase jitter. Lower values are more stable, but might feel sluggish."*
  **→ FIXED by S12.6e (D-053):** every Reactivity/Curve/Misc/SuperDimming control relabelled to the Tasker
  scene label + the **verbatim** Flash help (`TaskerHelp.kt`, 30 strings decoded from the `longclick`
  tasks) surfaced via an "ⓘ" reveal. "Delta factor" → **"Smoothing Δ"** with the exact owner-quoted help.
- **G2R-F20** The label mismatches lead to a suspicion that the **underlying behaviour/wiring is also
  wrong** — audit each parameter label → variable → runtime use against the extraction.
  **→ AUDITED, S12.6e (D-053):** the delta-factor case was a **label/help bug, not a wiring bug** —
  `%AAB_DeltaFactor` IS the sensor-smoothing alpha factor (`luxAlpha=1-exp(-deltaFactor·effectiveDelta)`,
  BrightnessEngine). All other field→`%AAB_*` bindings cross-checked vs `AabSettingsContract`; **no other
  binding bug found.** One verbatim oddity recorded: task510's help for `%AAB_DimmingEnabled` ("Use super
  dimming") describes "circadian scaling feature" — ported verbatim; super-dimming wiring already verified
  correct (S12.5c G2-F9), so this is Tasker's own help text, not a rebuild bug.
- **G2R-F21** The long-press explanations are **embedded in the scenes as long-press triggers** — port/fix
  them all (every scene's help longclicks).
  **→ FIXED by S12.6e (D-053):** ported verbatim as the "ⓘ" reveal text. **Carried gap:** the Experiment
  Settings scene help tasks (Circadian screen: scaleSpread/steepness/transition/taper) were NOT in this
  extraction batch — those fields keep their concise S12 helpers; a future pass should decode them too.
- **G2R-F22** Context rule creation **still cannot get the Wi-Fi SSID** (`_GetWifiNoLocation V3`) nor live
  location.
  **→ FIXED by S12.6e (D-053):** `WifiInfoReader.currentSsid()` is now `suspend`→`SsidResult` via a
  one-shot `NetworkCallback(FLAG_INCLUDE_LOCATION_INFO)` (2s timeout) — fixes the synchronous-path
  `<unknown ssid>` redaction on API 29+ — with targeted messages (NotOnWifi / NeedsLocationPermission /
  LocationServicesOff / Unknown). Live location: rule editor gained lat/lon/radius fields + "Use current
  location" (AndroidLocationReader).
- **G2R-F23** On rule creation, **usage access is greyed out**; the original permission onboarding had an
  instruction on how to fix it (surface that instruction/flow here).
  **→ ADDRESSED (S12.5c + S12.6e):** the rule editor shows a usage-access prompt + grant deep-link when an
  app trigger lacks the grant (already in S12.5c); not greyed.
- **G2R-F24** The setup/permissions screen should show **usage access as optional by default**.
  **→ FIXED by S12.6e (D-053):** onboarding always shows the usage step, labelled "(optional)" by default
  and "(needed for per-app rules)" once an app rule exists (per D-024/task563); Done is never blocked.
- **G2R-F25** There is **no toast when a rule/profile is loaded**.
  **→ FIXED by S12.6e (D-053):** `ContextLoadSink`/`ToastContextLoadSink` toasts on a runtime context-rule
  profile load (unconditional); the Profiles screen already toasted on manual apply (S12.5c).

#### Additional owner findings during S12.6c testing (2026-06-14) — triaged to future sub-segments
These five were reported while S12.6c was in flight. S12.6c investigated/triaged them but did NOT
implement them: each belongs to a future S12.6 sub-segment (owner-confirmed rule — "if it was supposed
to be addressed already, fix now; otherwise leave for future stages"; none are S12.6c scope). Routed:
- **G2R-F22 (S12.6e) — Wi-Fi "use current SSID" reports "Not connected" — ROOT CAUSE FOUND (S12.6c
  investigation).** `AndroidWifiInfoReader.currentSsid()` uses the SYNCHRONOUS
  `ConnectivityManager.getNetworkCapabilities(activeNetwork)`, whose `transportInfo` WifiInfo SSID is
  REDACTED to `<unknown ssid>` on API 29+ (our minSdk 31) — only a `NetworkCallback` registered with
  `FLAG_INCLUDE_LOCATION_INFO` (as `ssidFlow()` already does) returns the real SSID, and only with
  FINE_LOCATION granted AND location services ON. **S12.6e fix:** make `currentSsid()` a `suspend` that
  reads the SSID via a one-shot `registerNetworkCallback(..., FLAG_INCLUDE_LOCATION_INFO)` with a short
  timeout; surface targeted messages for not-on-Wi-Fi vs needs-FINE_LOCATION vs location-services-off
  (don't conflate all three as "Not connected"). Also G2R-F22's **live location** half (LocationTrigger
  lat/lon/radius editor + "use current location" via the existing `AndroidLocationReader`) is still
  unimplemented — the rule editor exposes no location fields at all.
- **G2R-F28 (S12.6e, NEW) — context-rule time inputs are free-text "HH:MM"; should open the system
  TimePicker modal.** The From/To fields in `ContextsScreen.RuleEditor` are `OutlinedTextField`s; replace
  with a tap→Material3 `TimePicker` dialog (keep the SUNRISE/SUNSET token buttons).
  **→ FIXED by S12.6e (D-053):** `TimeField` opens an M3 `TimePicker` `AlertDialog` (seeded from the
  current HH:MM); the SUNRISE/SUNSET token buttons are kept.
- **G2R-F29 (S12.6b refinement, NEW) — Live Debug "Performance & Timings" lacks full Tasker parity.** The
  Tasker debug scene (debug.md L19-23) surfaces `%LuxAlpha`, `%AAB_CycleTotal`, Reactivity Cooldown
  (throttle), Last Animation (steps×wait) and Last Update under Performance/Automation; the rebuilt card
  shows only cycle time + last sample. A future Live Debug pass should add luxAlpha / animation
  (steps×wait) / throttle / last-update to `PipelineState` (runtime holder, NOT the domain engine API)
  and render them.
  **→ FIXED by S12.6e (D-053):** `PipelineState` gained `luxAlpha`/`animationSteps`/`animationWaitMs`/
  `throttleMs`/`lastUpdateMs` (populated in `runCycle` from the existing `BrightnessPolicyOutput`, no
  domain change); the Performance & Timings card now renders LuxAlpha / cycle / reactivity-cooldown /
  last-animation (steps×wait) / last-update / last-sample.
- **G2R-F30 (S12.6d, NEW) — manually loading a profile does not pause context automation or offer a
  Resume.** In Tasker a manual profile load latches `%AAB_ContextOverride=true` (the manual context lock,
  D-014/D-038a) so watchers stop overriding the user's choice; the rebuild's `applyProfile` does not set
  it and the UI shows no "context automation paused / Resume" affordance. S12.6d (profiles) should set
  the lock on manual apply + surface a resume control that clears `contextOverride` (+ re-evaluates).

### Gate 2 RE-RE-TEST (after S12.6e) — findings recorded → S12.7 (Gate 2 still NOT signed off)

**Owner on-device re-re-test 2026-06-14 ~18:45 + 19:24 UTC** (S12.6e build, dist/ APK). "Definitely going in
the right direction." A further batch of parity/behaviour gaps remains → **G2R-F33…F68**, recorded verbatim
(structured below; owner's technical hints preserved — they are parity-critical). **Gate 2 stays NOT signed
off**; these route to a new **S12.7** (a–h, all Opus/high; brief in RUNBOOK). Several are S13 chart
work or large HTML-scene design ports — flagged inline. The owner did NOT follow the dist/README test
order, so the README's S12.6a–d re-confirmation items are still UNVERIFIED this round (see "untested" note
at the end).

*Onboarding / permissions:*
- **G2R-F33** ✅ RESOLVED S12.7d (D-057). First launch asks permissions but does NOT surface/guide the Android
  **"restricted settings" / greyed-out** fix flow (sideloaded apps must "Allow restricted settings" in App
  info before certain grants take effect). Onboarding must detect + instruct this. → onboarding shows a
  `RestrictedSettingsCard` (with an "Open App info" deep-link) when the install source is not a known store
  (`isLikelySideloaded` via `getInstallSourceInfo`).
- **G2R-F41 (perm half)** ✅ RESOLVED S12.7d (D-057). There is **no option to grant Location permission in Setup** —
  needed for the location-based SSID path + context location. → added a Location step (RequestMultiplePermissions
  FINE+COARSE), labelled optional (the Shizuku/dump SSID path needs no Location).

*Manual override detection & feedback:*
- **G2R-F34** ✅ RESOLVED S12.7a (D-054). Manual-override **false positives persist** — the app can't tell a
  true override from a new sensor reading. Owner spec: while an animation is **mid-flight there must be a
  mutex lock**, plus a **target-vs-actual delta check** — if target is e.g. −20 from current and the observed
  value is +1 or −21 (i.e. wrong direction / overshoot beyond our step) that's an override; a value
  consistent with our own in-flight step is not. The owner is fairly sure the Tasker version does exactly
  this. (Refines G2R-F26/D-049 — the S12.6c settle-wait was insufficient.) → Ported task696's band+2-read
  debounce in `AnimationRunner`; mutex = `%AutoBrightRunning`.
- **G2R-F35** ✅ RESOLVED S12.7b (D-055). A manual override should post a **high-priority notification (with vibration) + a toast**,
  same as Tasker. → `pausedByOverride` rising edge → new `manual_override` HIGH channel notif (Resume action) + toast.

*Override points / graphs (much of this is S13 chart work):*
- **G2R-F36** ✅ RESOLVED S12.7g (D-059). Manual override points on the curve graph are **deletable by tapping** them (Tasker draws
  them slightly larger for a bigger tap target); a tap shows the lux/brightness pair and asks to confirm
  deletion. → `ChartScatter` draws larger (12px) tap targets; `ChartCanvas` hit-tests taps via
  `nearestIndex` and calls back the data-space point; `OverridePointDeleteDialog` confirms → `OverridePointStore.delete`.
- **G2R-F37** All Tasker graphs have a **dashed golden reference line** = the default (mostly hard-coded)
  values that were loaded; currently missing on every chart.
- **G2R-F55** ✅ RESOLVED S12.7g (D-059). Graphs have **no axis labels**, and Chart.js let you **drag a finger across to read the
  numeric current + reference values**. The y-axis values feel arbitrary (e.g. 191.25 — should be logical
  integers); the x-axis should start at **0.1, not 1** (log lux). → `ChartCanvas` gains `xAxisLabel`/
  `yAxisLabel` (Lux / Brightness), `niceTicks` (1/2/5×10ⁿ — 0/50/…/250, no 191.25), log-x sampled from
  **0.1**→100k, and an `interactive` drag/tap scrub that draws a vertical line + per-series readout box.
- **G2R-F62** ✅ RESOLVED S12.7g (D-059). **Curve fitting belongs on the Curve view**, not Tools-only — the point is to *see* the
  fitted curve's impact against the recorded data points + the reference curve. → the wizard's suggested
  curve (≥9 recorded points, task38) is drawn as the "Suggested" series on `BrightnessCurveChart` against
  the override scatter + the fixed reference (was wired in G2R-F14; verified on the curve view here).

*Profile & Context UI design (large HTML-scene ports):*
- **G2R-F38** ✅ RESOLVED S12.7h. The Tasker **Profile + Contexts scenes showed a full list of every setting
  and its value, with any value changed-vs-default coloured gold**, via many modals. → new
  `settings/SettingsDisplay.kt` (`AabSettings.displayRows(reference)`/`changedCount` — explicit per-key
  extractor, no reflection; runtime/identity keys excluded) + `ui/components/SettingsDiffList.kt` (gold +
  semibold for changed rows, count summary). Wired into the **Load-Anyway modal** (`LoadProfileDialog`, the
  Tasker `LOAD_FILE` confirm), the **Save dialog** (shows what's being saved), and a **"View current
  settings"** dialog on the Profiles screen (the dashboard's active-vs-default compare). Each ProfileCard
  shows its changed-count. Context resume already smooth (S12.7a F46 latch + Profiles Resume banner F30).
- **G2R-F39** ✅ RESOLVED S12.7h. The **Circadian scene had a Date/Latitude/Longitude web element** to set
  them directly; unset → defaults to **today + current location**. → `CircadianDateLocationCard` on the
  Circadian screen (date via M3 DatePicker + lat/lon fields + "Use current location"), backed by new
  `settings/ExperimentPrefsStore.kt` (`experimentPrefsDataStore`, `%AAB_Date`/`%AAB_Latitude`/
  `%AAB_Longitude` — `_ExperimentSetDate`/`_ExperimentClearDate`) via `state/CircadianExtrasViewModel.kt`.
  Unset fields pre-fill with today + `AndroidLocationReader.lastKnownLocation`; "Use live data" clears the
  override. Preview-only state (never enters AabSettings/profiles/export). The consuming ExperimentChart is
  still the S13 host slot.

*Notification:*
- **G2R-F40** ✅ RESOLVED S12.7b (D-055). The notification offers **Pause but no Resume** action — add Resume (and reflect paused
  state), parity with Tasker. → ongoing FGS notif toggles Pause↔Resume on `paused`; the F35 override now latches `paused` so Resume actually surfaces.

*Wi-Fi SSID acquisition (extends G2R-F22 — the S12.6e fix was the wrong primary path):*
- **G2R-F41 (SSID half)** ✅ RESOLVED S12.7d (D-057). Don't require Location for the SSID. Per
  **`_GetWifiNoLocation V3`** (task105/633): 1) **with Shizuku**, run `cmd wifi status | grep "Wifi is
  connected to" | cut -d\" -f2`; 2) **with WRITE_SECURE_SETTINGS / dump access (no Shizuku)**, run
  `dumpsys wifi | grep mWifiInfo | grep COMPLETED` and regex the SSID out; 3) only fall back to the
  Location-gated `NetworkCallback` path. → `AndroidWifiInfoReader.currentSsid` now runs the no-Location
  strategies first (`WifiSsidStrategies.kt`: `ShizukuWifiSsidStrategy` via `ShizukuShell.exec`/new AIDL
  `exec` → `DumpsysWifiSsidStrategy` in-process `dumpsys wifi`), Location `NetworkCallback` is the LAST
  fallback. Strategies are injectable → source-order unit-tested. The Setup option now exists (F41-perm).

*Context system:*
- **G2R-F42** ✅ RESOLVED S12.7c (D-056). Context-rule "use current location" **wrongly reports Location permission not granted** (even
  when it is) — likely a stale/sync permission check; recheck on use (cf. the permission-propagation delay
  the owner saw in F44). → `currentLocation()` now returns a typed `LocationResult` after a call-time
  permission recheck + a fresh `getCurrentLocation` fix; the editor shows a targeted message per outcome.
- **G2R-F43** ✅ RESOLVED S12.7c (D-056). The **context-rule list is ordered by creation time** (oldest first); it should be ordered by
  **priority (highest first)** to match the resolution model (D-014). → `List<ContextRule>.byPriority()`
  (highest first, case-insensitive name tie-break) in `ContextsViewModel.rules`.
- **G2R-F44** ✅ RESOLVED S12.7c (D-056). Context-rule creation only offers **built-in profiles**; **imported legacy profiles** (loaded
  from `Download/AAB/configs`) are NOT selectable as a rule target — only after re-saving them as a new
  profile. The legacy import must register into the profile catalog the rule editor reads (extends D-042c).
  → loading a legacy config now `saveImportedProfile`s it (file name) into `UserProfileStore`, which
  `AppProfileCatalog` (the rule editor's source) reads — selectable immediately, no manual re-save.
- **G2R-F45** ✅ RESOLVED S12.7c (D-056). **Context location matching + listener lifecycle.** Test setup (lat 55.83, lon 4.99, 2000 m,
  highest priority) initially read "active rule: none"; debug toasts showed **`loc 0.0,0.0`** even with
  Location enabled. It later started working (permission-propagation delay), BUT the **location listener
  dies the instant the app is backgrounded** → reverts to no-rule. Needs a proper **foreground/zombie-
  guarded location listener** (Tasker's "super smart location listener"), and the **0.0,0.0** read is a
  bug. Also the context-location debug toasts are **not debounced to ≥100 m** changes and are near-constant,
  **blocking text input**. → `LocationReader.locationUpdates()` continuous `requestLocationUpdates`
  (NETWORK+GPS, best-last-known seed, (0,0) null-island filter) hosted in the FGS scope (survives
  backgrounding); `ContextEngine` collects it gated on `tokens.usesLocation` (the `[LOC]` cost gate,
  owner-confirmed) with a ≥100 m haversine debounce before firing the LOCATION eval.
- **G2R-F46 (semantics)** ✅ RESOLVED S12.7a (D-054). **"Manual profile load = override" misunderstanding.** After a manual profile
  load the Menu says **no context override active** — but a manual load IS the override (it should latch +
  display it, smoother Resume). Conversely, **a context rule being active is NOT an "override"** and must
  not be labelled one. Fix the override/lock semantics + the Menu/label wording (refines G2R-F30/D-038a).

*Debug / toasts:*
- **G2R-F47** ✅ RESOLVED S12.7c (D-056). **Context-automation debug toasts are missing.** Tasker flashed these constantly: on app
  switch, on auto-loading a profile — showing **context trigger, context, profile, and rule (with
  priority)**. Surface them under the Context Automation debug category. → the CONTEXT_AUTOMATION toast on
  each auto-load now reads "trigger … · context … · profile … · rule X (priority N)".
- **G2R-F48** ✅ RESOLVED S12.7e (D-058). **Dynamic Scale debug** fired on **every light change**; should fire **~2 minutes
  into a dawn/dusk transition** only. → new pure `DynamicScaleDebugGate` (first flash 2 min into a
  transition, then ≤ once/2 min, resets when the time-driven scale settles) gates the `DYNAMIC_SCALE`
  flash in `runCycle`; a "transition" = `scaleDynamic` changing between cycles.
- **G2R-F49** ✅ RESOLVED S12.7e (D-058). **Overlay Preview debug** (privilege≈none fallback) should **toast the colour of the
  semi-transparent overlay**. → `SuperDimmingCoordinator` emits `OVERLAY_PREVIEW` with the computed
  black-overlay hex (task653 `2.55·dim_shell` → task654 `%AAB_HexOverlay`) on the unprivileged
  below-threshold fallback (overlay window itself still deferred, D-040).
- **G2R-F50** ✅ RESOLVED S12.7e (D-058). Toasts only showed **in the foreground**; Tasker flashed **everywhere** (Accessibility).
  → opt-in `AabToastAccessibilityService` (presentation-only `TYPE_ACCESSIBILITY_OVERLAY`) registers as
  the presenter of the new process-wide `AabFlash` channel; Live Debug opt-in card + rationale; degrades
  to the foreground teal toast when off.
- **G2R-F51** ✅ RESOLVED S12.7e (D-058). Toasts **stacked instead of cancelling**. → all flashes route through `AabFlash.show`,
  which cancels the previous flash before posting the next.
- **G2R-F52** ✅ RESOLVED S12.7e (D-058). **"Debug Off" was not instant**. → `setDebugLevel` now `AabFlash.cancel()`s on Off and
  triggers `reapply` so the pipeline's cached `ContextEngine` effective-snapshot picks up the new
  category immediately (root cause: the stale `effectiveSettings()` snapshot).

*Pipeline correctness / diagnostics:*
- **G2R-F53** On the Live Debug screen, **LuxAlpha shows a NEGATIVE value for one cycle** after it settles
  — impossible for `1 - exp(-…)` ∈ [0,1]; investigate (stale/uninitialised read or a settle-cycle artefact).
- **G2R-F54** **Sensor dead zone may be mis-configured** — lots of activity under Performance & Timings in
  a dimly lit (stable) room. Verify `%AAB_ThreshAbsLow/High` seeding + the prof760 gate.

*Per-screen live readouts & labels (extends G2R-F7/F8):*
- **G2R-F56** **Reactivity "live reactivity" threshold should be shown as a percentage** (current 0.5 →
  display "50%"). (The bound vars are `%aab_thresh*pc` percentages.)
- **G2R-F57** ✅ RESOLVED S12.7d (D-057). **First boot after granting permissions loads a non-functional Dashboard**
  — can't navigate back to the Menu, and the hardware Back key closes the app. (Onboarding "Done" routes to
  Dashboard, not Menu — should land on the Menu hub, cf. S12.6a.) → new `NavHostController.completeOnboarding()`
  navigates to `AppRoute.Menu` with `popUpTo(Onboarding, inclusive)`; OnboardingScreen's onDone uses it.
- **G2R-F58** **Every settings screen needs the Tasker live-readout block.** Examples: Curve & Brightness →
  "Current smoothed lux [%SmoothedLux]" + "Current brightness (%AAB_MinBright–%AAB_MaxBright)
  [%AAB_CurrentBright]"; Misc → "Current throttle [%AAB_Throttle] ms" + "Current smoothing α [%LuxAlpha]".
  (Big-bold label + gold value, like the diagnostic cards.)
- **G2R-F59** Reactivity **"dynamic threshold" description shows the literal `%AAB_ThreshDynamic`** (Tasker
  substitutes the live value), AND that description is **not gated behind the ⓘ info button** like the
  other labels. Substitute the value + move it behind the info reveal (or make it a live readout).
- **G2R-F60** Under **Misc, "Scale" should become an automatic read-only field showing the current
  dynamic scale** when circadian scaling is enabled (mirrors the Tasker scale_dynamic auto field).
- **G2R-F61** Under **Curve & Brightness, form2A / form3A look like placeholders** — label them **"Zone 2
  alignment" / "Zone 3 alignment"** (they are the derived continuity hinge points).

*Re-re-test clarifications + additional findings (owner, 2026-06-14 19:24) + owner answers to the open questions:*
- **CONFIRMED WORKING (retractions / no action):** boot self-start; screen off→on reinit; block-Apply gate;
  per-screen reset; override-point persistence; PWM-sensitive **hardware-floor** lock. **G2R-F37 RETRACTED**
  — the dashed **gold reference line IS present** on the graphs; it only needs a **legend** now (→ G2R-F66).
- **G2R-F63** ✅ RESOLVED S12.7b (D-055). **QS tile state is not live** — Off→Starting "hangs" until the QS panel is closed+reopened;
  state transitions only render when not being watched. Needs `TileService.requestListeningState` /
  `Tile.updateTile` on every state change (and a refresh on `onStartListening`). → tile live-collects in
  `onStartListening`→`updateTile`; service calls `requestListeningState` on each state publish.
- **G2R-F64** ✅ RESOLVED S12.7a (D-054). **Spurious "instant override" on service start / display-on (race).** QS Off→On sometimes
  lands in *override* instead of resuming; a display off→on cycle sometimes leaves it *paused*. Both point
  to a manual-override being detected at start/reinit (the observer firing on our own initial write).
  Confirmed in the Dashboard; hard to reproduce once cleared. Likely a race in OverrideMonitor vs the
  initial `setInitialBrightness` write — gate override detection until the first self-write settles.
  (Tightly related to G2R-F34; fix together.)
- **G2R-F65** ✅ RESOLVED S12.7b (D-055). **Super dimming does not Extra-Dim.** With PWM-sensitive on, the hardware floor locks to the
  setpoint correctly, BUT the **secure `reduce_bright_colors` (Extra Dim) layer never actually dims** below
  it. The ELEVATED super-dimming engage path is not taking effect on-device (separate from the PWM floor).
  → ROOT CAUSE: the pipeline fed `dimming.apply` the **PWM-floored** target (== `dimmingThreshold`), so the
  coordinator's `target < dimmingThreshold` gate was never true. Now feeds the **un-floored engine target**
  (task646 `%AAB_CurrentBright`); the hardware floors while the secure layer darkens below it.
- **G2R-F66** ✅ RESOLVED S12.7g (D-059). Graphs need a **legend** for the (now-confirmed) dashed gold reference line + the live curve.
  → `ChartCanvas` renders an opt-in `showLegend` Row above the canvas: a solid/dashed swatch + label per
  series (`legend_Curve`, dashed `legend_Reference`, `legend_Suggested`) + an "Overrides" scatter swatch.
- **G2R-F67** ✅ RESOLVED S12.7c (D-056). Context rules also need **day-of-week selection** (Tasker `<Day>` rules) with **smart
  midnight wrapping**. The domain `ContextTriggers.days` + resolver already exist (D-014); the rule editor
  exposes no day picker. → added a 7-`FilterChip` day picker wired to `ContextTriggers.days` (all/none =
  every day); the resolver's overnight-wrap (post-midnight tail = previous day's membership) is unchanged
  (domain fenced) and verified.
- **G2R-F68** ✅ RESOLVED S12.7c (D-056). The rule editor's **Sunrise/Sunset tokens should also display the resolved current
  time-of-day** for today, in theme **gold** (Tasker shows e.g. "SUNRISE (06:42)"). → `solarTimes()`
  computes today's rise/set for the last-known location; the tokens render "Sunrise (06:42)" in `AabGold`.
- **G2R-F69** ✅ RESOLVED S12.7g (D-059). On the **Curve & Brightness** screen the
  **dashed gold reference line moves as you edit the field values** — it should be a FIXED reference (the
  default / last-loaded curve) so edits are visible *against* it. → `BrightnessCurveChart` gained a
  `referenceCurve` param; `CurveBrightnessContent` samples it from the **committed** snapshot
  (`remember(committed)`), drawn dashed gold and FIXED, while the live "Curve" series tracks the draft.
  (Replaces the old draft-derived "Taper" overlay that moved.)
- **G2R-F70** ✅ RESOLVED S12.7i (D-062) — **the bug was the PARSER, not the apply-wiring.** ProfilesScreen
  already called `vm.replaceAll(imported)` (commit + `AutoBrightnessRuntime.reapply`) since S12.7c, but
  `TaskerLegacyProfileSerializer` only understood `%AAB_Key=value` plaintext. A real Tasker AAB config is
  **nested JSON** (`{meta,general,misc,reactivity,circadian,superdimming}`, task637 `_ProfileManager`
  performSave XML L29365+ / performLoad L29490+), so it parsed to all-defaults → the file "loaded" by name
  but no value changed. The serializer now detects `{`-JSON and maps the snake_case keys per performLoad
  (derived form2A/2D/3A not stored — recomputed at read-time, per the ledger); the plaintext path is kept.
  Original report below.
  **Loading a legacy Tasker JSON config does not apply.** Importing a legacy `.json` (Profiles screen → legacy folder/file load) **toasts "loaded" and the
  Menu shows the context-override latch, but none of the imported settings actually take hold** (the live
  brightness/curve/etc. behaviour is unchanged). The import path appears to register the profile + latch the
  override (S12.7c F44 wired `saveImportedProfile`) but never **commits the parsed settings into the active
  DataStore + forces a re-eval**. Suspect: `LegacyConfigImporter`/`TaskerLegacyProfileSerializer` parse →
  `UserProfileStore` registration succeeds, but the "load" action doesn't call the equivalent of
  `SettingsViewModel.replaceAll(parsed)` + `AutoBrightnessRuntime.reapply` (cf. the manual Apply path).
  Repro: import a legacy config that differs from current, observe no change. Fix: make legacy load apply the
  parsed settings exactly like a profile apply (commit + reapply), not just register/latch. **App-layer.**
- **G2R-F71** ✅ RESOLVED S12.7i (D-062). Transcribed task544 (the throttle gate
  `elapsed<%AAB_Throttle`→`%AAB_MainLoop=0`→Stop gates ONLY the prof760 main loop) vs task567 (prof755
  override handler: act7 "Wait %AAB_CycleTime", act8 re-gate) — override detection is a SEPARATE Tasker
  profile from the throttle-gated main loop. The only conflation in the runtime was `handleOverride`'s
  settle falling back to `throttleDefaultMs`; now it waits `%AAB_CycleTime` only (0 ms when unset), so a
  genuine override is acted on inside the throttle window. domain/ fenced. Original report below.
  **Reactivity cooldown is fixed to `%AAB_Throttle`, which blocks manual overrides — not how Tasker does it.** The runtime currently gates the
  reactivity cooldown on a fixed `%AAB_Throttle` window; while that window is in force a genuine manual
  brightness override is missed/suppressed. Tasker's real model does NOT hard-pin the cooldown to throttle in
  a way that swallows overrides — re-derive the actual task543/task567 cooldown-vs-override interaction from
  the XML (XML_RECIPES R2) and re-implement so an override is still detected during the cooldown. **Likely
  touches the runtime override/throttle interaction (BrightnessPipelineController/OverrideMonitor); verify
  against the real task logic — do NOT approximate. domain/ stays fenced.** (Related to the S12.7a override
  work but distinct: this is the cooldown gate, not the detector.)
- **G2R-F72** ✅ RESOLVED S12.7i (D-062) — the context rule editor now shows a "Clear time" action
  (visible once a From/To is set) that blanks both fields → `ContextTriggers.timeRange` saves null, making a
  time-constrained rule time-agnostic again. Original report below.
  **Cannot remove a time rule from a context.**
  In the context rule editor, once a context has a time (From/To) rule there is **no way to clear/remove it**
  — the time fields can be set but not unset back to "no time constraint". Add a clear/remove affordance
  (e.g. a "Clear time" action that nulls `ContextTriggers.from`/`to`) so a time-constrained rule can become
  time-agnostic again. **App-layer (ContextsScreen rule editor).**
- **G2R-F73** ✅ RESOLVED S12.7i (D-061) — **and it was APP-LAYER, not the domain bug it looked like.**
  `%AAB_ScaleDynamic` dropped below 1 (0.852) in the morning. Suspected UTC-vs-local in
  `DynamicScaleEngine`/`SolarTimes` — but those (and `SolarCalculator`) are correct + golden-tested. The
  real cause: `BrightnessPipelineController.buildInput` fed the dynamic-scale engine `TimeContext`'s
  **fixed default windows** (6–8am / 18–20pm UTC) and **never wired the real sunrise** (the D-039d
  carryover). So "morning" was 06–08 UTC for everyone → 0.852 at **06:13 UTC** regardless of clock (owner
  saw it at 07:13 @UTC+1 *and* 08:13 @UTC+2 = both 06:13 UTC — confirming the frame, not a local bug). →
  new app-layer `CircadianWindowProvider` wires the fenced `SolarCalculator.compute`/`buildScheduleWindows`
  (F39 date/loc override aware) into `buildInput`; `now` kept UTC seconds-of-day to match the windows'
  UTC frame. **domain/ + golden vectors untouched.** Verified: Utrecht 2026-06-15 sunrise 05:18 local →
  scaleDynamic 1.15 at 08:13 local. (Numbered F73 because S12.7f claimed F70/F71/F72.)
- **OWNER ANSWERS to S12.6e open questions:** (F34) **yes — transcribe the real task567 override logic**
  from the XML, don't approximate. (F41) provide **guidance on granting `DUMP` and/or using Shizuku** for
  the no-Location SSID path. (F50) **yes, use an AccessibilityService** for global toasts — distribution is
  **F-Droid/GitHub, not Play Store**, so the sensitive permission is acceptable. (F38/F39) the "modals" =
  the **load / save / create-rule / edit-rule** dialogs — they feel **more polished** in Tasker's WebViews
  than the current M3; raise the fidelity (full settings list w/ gold changed-vs-default values; smoother
  context resume). (F46) **confirmed** — manual profile load = an override (latch + Menu shows it + Resume
  clears); a context rule being active is NOT an override.

### Gate 2 (4th re-test, after S12.7i) — findings recorded → next polish stage (Gate 2 still NOT signed off)

**Owner on-device test 2026-06-15 ~19:00 UTC** (S12.7i build, dist/ APK; dist/ since removed). Device:
**OnePlus 13, Android 16.** "Definitely hitting polishing territory." The large majority of the S12.7
finding set (F33–F73) is **confirmed fixed**; a handful are reopened with corrected specs (the original spec
was wrong in some cases), a few remain untestable, and the owner raised **20 new observations → G2R-F74…F89
plus reopens**. Gate 2 stays NOT signed off; these route to the next polish stage (a future segment — NOT
yet briefed in RUNBOOK). domain/ is touched by at least F85 (schema) and possibly F73 — re-scope the fence
when briefing. Recorded verbatim-faithful below; owner parity hints preserved (they are authoritative).

*Confirmed FIXED this round (close):* F33, F34, F40, F41, F42, F43, F44, F46, F47, F48, F49, F51, F52, F55,
F56, F57, F60, F61, F63, F66, F69, F72, F36. (F35/F38/F50/F58/F59/F68 also "correct" but carry follow-ups —
see below.)

*Untestable / partially verified (keep OPEN):*
- **G2R-F45 OPEN** — foreground/zombie-guarded location listener: owner could not test at home this round;
  keep open until they can exercise the location lifecycle on the move.
- **G2R-F67 PARTIAL** — day-of-week picker present; **midnight/overnight wrapping not yet verified** on device.
- **G2R-F71 TENTATIVE** ✅ CONFIRMED S12.8a (D-063). The S12.7i settle (waits %AAB_CycleTime, never the throttle
  cooldown) is now covered by the `override_settleIsNotGatedByThrottleCooldown` controller regression — a manual
  override is detected INSIDE the throttle window.

*Reopened with CORRECTED specs (the original understanding was wrong):*
- **G2R-F65 REOPENED** ✅ RESOLVED S12.8a (D-063). PWM-sensitive now engages Extra Dim below the floor via the
  task700 `finalDimLevel` (`SuperDimmingCoordinator` PWM branch); the super-dimming toggle keeps the `dimShell`
  path. — **the S12.7b "two cooperating layers" model was WRONG.** PWM-sensitive mode and the
  Super-Dimming toggle are **intentionally mutually exclusive**. PWM-sensitive means: **lock the hardware
  brightness, then dim using Android "Extra Dim"** — see **Map Lux to Brightness (Java) V2, actions 23→38**
  (cross-check the ledger note that task661's runtime math lives in Variable-Set 547, not Java). The
  S12.7b change that fed the un-floored target so the secure layer engaged *alongside* PWM-floor must be
  re-examined against this exclusivity.
- **G2R-F70 REOPENED** ✅ RESOLVED S12.8c (D-064). The defaults-THEN-diffs model was ALREADY correct (the
  serializer starts from `AabSettings()` = task570 baseline; `replaceAll` fully replaces, no merge) — proven
  by a new no-inheritance regression. The real bug was an **integer-handling class bug**: Tasker stores curve
  params as continuous doubles, so a decimal-encoded int ("6.8") hit `String.toIntOrNull()` → null → the
  field kept its default ("Form1A didn't stick"). Fixed across the whole class: key=value path rounds every
  int/long (`asRoundedInt`/`asRoundedLong`), nested-JSON already used `intRound`, wizard-apply now `Math.round`s
  form1A/form2C (was `.toInt()` truncation). The "misc inherited" symptom could not be reproduced in the
  parser/`replaceAll` path (both reset to defaults); the integer-drop fix is the most likely on-device cause.
- **G2R-F62 REOPENED** ✅ RESOLVED S12.8c (D-064). The ghost-point inflation was the cause: the domain engine
  injects synthetic priors that cleared its own post-injection ≥9 gate at 7 real points. The Tools wizard now
  gates on ≥9 **real** recorded points (shared `MIN_FIT_POINTS` with the Curve screen; `OverridePointStore`
  holds only real points). The suggested curve already draws on the Curve & Brightness chart at ≥9 real
  points (S12.7g wiring, verified) — domain engine fenced (its internal ≥9-after-ghost check is untouched).
- **G2R-F73 REOPENED** ✅ RESOLVED S12.8d (D-065) — **and per D-061's lesson it was APP-LAYER (location), not
  the tz/DST frame.** The UTC-seconds-of-day frame already matched Tasker exactly (act0 `%TIMES%86400`, act59
  `%ss_*%86400`) and `riseEpochSec` is tz-independent, so the windows were astronomically correct. The ~1h-early
  evening ramp at 20:58 local was the **location-null → `TimeContext` default-windows fallback** (eveningStart
  18:00 UTC = 20:00 local @UTC+2); the context path looked correct only because it uses the live location
  listener, not `lastKnownLocation()`. Fix: supply a robust location (F83) + read the tz offset at the target
  date instant (DST-aware, for fixed dates). domain/ + golden fenced.
- **G2R-F39 REOPENED** ✅ RESOLVED S12.8d (D-065). The fixed **Date / Lat / Lon** now resolve **independently**
  (date-only / loc-only / both) and DRIVE the live circadian scaling — not preview-only. Root cause of "Set
  fixed does nothing": `current()` honoured the override only when BOTH lat AND lon were set, so a date-only
  override silently fell to live. `ExperimentPrefsStore`/`CircadianExtrasViewModel`/`CircadianDateLocationCard`
  now persist nullable coords; the provider resolves date and location separately.

*Follow-ups on otherwise-correct findings:*
- **G2R-F58 PARTIAL** ✅ RESOLVED S12.8a (D-063). The Super Dimming screen now shows the live readout
  (`SuperDimmingDiagnosticCard`): dimming strength (rel) `%AAB_DimmingCurrent` + (abs) `%AAB_DimmingDS` at
  `%AAB_CurrentBright`, computed from the golden `SoftwareDimming` into new PipelineState fields.
- **G2R-F59 PARTIAL** ✅ RESOLVED S12.8c (D-064) — resolved by the F85 removal: the only user-visible literal
  "%AAB_ThreshDynamic" string was that editable field's help, now deleted. The live reactivity card already
  substitutes the VALUE (as a %), not the token.
- **G2R-F68 PARTIAL (UI bug)** ✅ RESOLVED S12.8b (D-066). The context-editor SUNRISE/SUNSET tokens now stack
  vertically (each gets the full From/To column width) with `maxLines=1`/`softWrap=false` (`ContextsScreen`
  `TimeTokenRow`) → "Sunset (22:00)" renders on one line.
- **G2R-F50 NOTE (likely wontfix)** — the Accessibility service **sometimes disables itself automatically**
  (Android 16 / OnePlus 13). Probably an OS limitation; owner is content to wontfix.
- **G2R-F35 NOTE** — high-priority override notification works; but its Resume action and stacking behaviour
  are buggy → see F74/F75.

*New findings (G2R-F74…F89):*
- **G2R-F74** ✅ RESOLVED S12.8a (D-063). Resume was inert because the action reached a (re)created service whose
  pipeline consumer was never `start()`ed; `ACTION_RESUME` now `ensureRunning()` before `controller.resume()`.
- **G2R-F75** ✅ RESOLVED S12.8a (D-063, 2nd pass). The override alert reuses the SAME foreground notification ID,
  raised to the high-priority channel on the rising edge then settled back — never a separate ID, so it cannot
  stack with / leave a stale override notification.
- **G2R-F76** ✅ RESOLVED S12.8a (D-063). The Pause action was removed from the ongoing notification (Reset/Disable
  kept; Resume shown only while paused).
- **G2R-F77** ✅ RESOLVED S12.8a (D-063, 3rd pass). New prof769 detector (`PanicGestureDetector`) — inverted ONLY
  (sustained ≥5 frames, dominant y-axis, heavy low-pass). **Sign-convention fix:** Android reads +9.8 on the axis
  pointing UP, so upright = `gravity.y ≈ +9.8` and upside-down = `gravity.y ≈ −9.8`; the gate is `gy < −7` (the
  2nd-pass `gy > +7` matched upright). Display-on gated → SOS morse vibration (task528 act0) + `emergencyStop()`.
- **G2R-F78** ✅ RESOLVED S12.8a (D-063, 2nd pass). %AAB_Throttle = the engine's ACTUAL `transitionDurationMs`
  (loops×wait+10, golden task543) with NO setting floor (the prior floor equalled the ceiling, so it always read
  `MaxSteps×MaxWait+10`); the task566/prof754 watchdog raises it to that ceiling only after ~10 s idle.
- **G2R-F79** ✅ RESOLVED S12.8b (D-066). The **Pause** control is gone (master switch = on/off); **Resume**
  remains only when auto-control paused itself for a DETECTED manual override (`pausedByOverride`). `DashboardContent`
  rebuilt into purposeful live cards — status headline, override+Resume card, ambient-light (raw/smoothed +
  last-sample age), brightness (current→target + circadian scale ×/super-dim % when active), active-context,
  degraded-only health. `DashboardViewModel.pause()` removed.
- **G2R-F80** — show the **User Guide after permissions onboarding on first launch** (Tasker does this).
  Deferred to **S13** (User Guide screen is built there).
- **G2R-F81** ✅ RESOLVED S12.8b (D-066). New `ui/components/GraphScaffold.kt` `ChartPager` (foundation
  HorizontalPager + dot indicator + title) places the relevant graph(s) **above** the settings on every
  chart-host screen and **swipes** between related graphs (Reactivity↔Alpha, Experiment↔Taper; SuperDimming =
  single Dimming slot, moved up from the bottom). `ChartSlot.content` is the S13 swap point (real chart, same
  title/testTag, no pager/screen change). No new dep (Pager is transitive via material3→foundation).
- **G2R-F82** ✅ RESOLVED S12.8b (D-066). New `GraphSettingsGroup(graph)` outlined card labels "Affects the
  {graph} graph" and wraps the controls feeding it (Reactivity curve / Smoothing α / Experiment / Taper /
  Dimming).
- **G2R-F83** ✅ RESOLVED S12.8d (D-065). The rebuild had **none** of this (confirmed). Ported task90 act5–41:
  `CircadianWindowProvider` acquires location **once a day** (re-acquired on the day roll-over = the
  `%AAB_SunLastDate != %DATE` guard), **skips** entirely when a fixed `%AAB_Latitude`/`%AAB_Longitude` is
  pinned, and falls back Android last-known → fresh fix → **ip-api.com** (`GeoIpLocationClient`, INTERNET perm +
  ip-api-scoped cleartext config). The ELEVATED `location_mode`-flip (act14/19/34) was deliberately NOT ported
  (ip-api covers no-fix for all tiers; the secure setting is unreliable on minSdk 31). Feeds F39/F73.
- **G2R-F84** ✅ RESOLVED S12.8c (D-064). `SettingsDisplay` diff rows use friendly labels (`form1A`→"Zone 1
  scaling", etc.; explicit map, no reflection) and `EXCLUDED_KEYS` now also drops the global prefs
  (debugLevel/detectOverrides/quickSettingsEnabled/notificationsEnabled) + derived thresholdMidpoint.
- **G2R-F85 (CRITICAL)** ✅ RESOLVED S12.8c (D-064). Removed `thresholdDynamic` from `AabSettings`/contract/
  mapper/`SettingsDisplay`/`ReactivityScreen`/`ContextEngine.mergeProfile`; **schema v2→v3** (migration bumps
  the stamp, the stale key drops via `ignoreUnknownKeys` on read). App-layer only — the engine reads the
  runtime `PipelineState.threshDynamic`, never the setting. — It is the *outcome* of the threshold calculation
  for the current lux level, not an input (task570 act31 only seeds it).
- **G2R-F86** ✅ RESOLVED S12.8a (D-063). The DISPLAY clamps to ≥ 0 (`fmtAlpha` on the Misc card + Live Debug);
  the engine value is intentionally left unclamped (task535 parity, D-010a — domain/ untouched per owner decision).
- **G2R-F87** ✅ RESOLVED S12.8b (D-066). The context app picker `heightIn(max=220→400.dp)` (still scrollable).
- **G2R-F88** ✅ RESOLVED S12.8a (D-063, 2nd pass). Added an in-app tap-to-dismiss flash surface
  (`AabFlashHost`/`FlashPill`, registered as the foreground `AabFlash` presenter) so in-app flashes ("Applied",
  foreground debug toasts) are tappable; the global a11y overlay is also touchable. Priority: global overlay →
  in-app surface → plain Toast (the Toast fallback, only when backgrounded + overlay off, stays non-interactive).
- **G2R-F89** ✅ RESOLVED S12.8b (D-066). Audit: **(1) PACKAGE_USAGE_STATS** — needed (per-app rules read the
  foreground app), granted via the usage-access onboarding deep-link, already wired; kept + documented. **(2)
  background location** — `ACCESS_BACKGROUND_LOCATION` now declared (the context location gate + daily sun
  refresh read location from the specialUse FGS while the UI is backgrounded, which needs it on API 29+); the
  "Allow all the time" upgrade is offered from the Setup Location step. **(3) DUMP** — signature|privileged,
  **not grantable to a normal app** → deliberately NOT declared (wontfix-with-note); the no-Location `dumpsys
  wifi` SSID path is permission-denied without it and falls through to the next strategy.

### Code-quality & parity audit (after S12.8, pre-S13) — findings recorded → S12.9 (Gate 2 still NOT signed off)

A combined engineering-quality + owner-device review surfaced one real runtime-parity gap plus a tranche of
code-quality / tech-debt items; the latter are tracked as S12.9 deliverables (RUNBOOK §S12.9, D-069), not as
gate findings. The two owner-reported behavioural findings:

- **G2R-F90** (owner-reported, post-S12.8) **Circadian dimming is inert.** Two proofs: Spread (Circadian) at
  100 vs 0 produces no observable change; super-dimming still engages in a dark room during circadian-scaled
  daylight (`scaleDynamic ≈ 1.15`, a morning boost). **Root cause located:** `SuperDimmingCoordinator.apply`
  (`app/.../runtime/SuperDimmingCoordinator.kt:106`) hardcodes `dimDynamic = null` in its `SoftwareDimming.dimShell`
  call, never consulting the published `PipelineState.scaleDynamic` (a known deferral, D-040). **→ to be FIXED
  by S12.9b:** wire `dimDynamic` from `scaleDynamic` + the confirmed spread field per task646 act6 semantics
  (spread=100 suppresses super-dimming during daylight, 0 engages normally, −100 boosts). App-layer only —
  `dimShell` and the golden `superdimming.csv` already support the parameter, so no domain/golden change. Owner
  re-verifies on device at the next gate; the PARITY_CHECKLIST circadian-dimming row flips to `ported` when
  S12.9b lands.
- **G2R-F91** (owner-reported, post-S12.8) **Manual-override toast surface.** The manual-override flash may not
  use the teal `AabFlash` operational surface like the profile/context-load flashes (visually inconsistent —
  reads as a debug toast). **→ S12.9b:** verify the override path and route it through `AabFlash` only if it
  isn't already (record as already-consistent if it is). Minor.

### Gate 3 (after S14) — owner device findings → ✅ SIGNED OFF (2026-06-23)

> **✅ GATE 3 SIGNED OFF (2026-06-23): owner satisfied with the re-test after the punch-list rounds
> (R1 G3-F1…F18 + R2 follow-ups + the two external-AI review passes). All three gates passed → v1.0.0.
> The findings below are retained as the historical record.**

> **STATUS (2026-06-22): ALL 18 worked in the "Gate 3 punch-list" segment above (see the segment-log row
> for the per-finding fix). 17 fixed in code/docs; G3-F18 was info-only (recorded); G3-F10 partially done
> (one within-core icon swap) with the fuller Material Symbols pass deferred pending a `material-icons-
> extended` dependency decision (D-091). Re-run HUMAN GATE 3 on this build.** The findings below are kept
> verbatim as the historical record.
>
> **(Original hand-off note, now satisfied:)** the work was done on `claude/adoring-bardeen-37fb6m`'s
> content (the next session fast-forwarded its own branch onto it, so all of S14 — proximity damp,
> power-draw calibration, parity audit, README/CONTRIBUTING/DEVICE_TEST_SCRIPT, CI — was carried, not
> dropped). Owner ran `DEVICE_TEST_SCRIPT.md`; everything else passed — these were the deviations only.

Findings (owner wording preserved; triage hints from the S14 session in brackets):

- **G3-F1 User Guide branding + stale tooltip text.** UserGuide still says "Advanced Auto Brightness" /
  "AAB" — should be "Tideo Auto Brightness" / "TAB" (or just "Tideo" where it reads better). It also says
  you can long-press tooltips, but the UI now uses a tappable "ⓘ" — fix that wording too.
  [strings.xml `guide_*` / `about_*`; the long-press→ⓘ change was S12.6e.]
- **G3-F2 Missing "now" indicators on graphs.** Brightness graph and Alpha graph have no live "now"
  line. **Double-check ALL graphs** — if a current value can be shown as an event line, do it. [S14 added
  "Now" markers to Reactivity/Dimming/Taper and the curve already had a current-point cross-hair, but the
  owner still sees none on the brightness curve + alpha — verify the markers actually render on-device and
  that `live`/`currentLux` is wired to those slots; Alpha needs a current value too (see G3-F15).]
- **G3-F3 (BUG) Min brightness 0 is wrongly clamped to 1; Misc shows save/discard after Apply.** Setting
  min brightness to 0 doesn't take hold (clamps to 1); 0 should be valid. And Misc shows the dirty
  save/discard prompt even after applying. [`AabSettings.validate()` does `minBrightness.coerceIn(1,255)`
  — S14's clamp-on-Apply (D-090c) now ROUNDS/clamps on commit, so committed=1 ≠ draft=0 → perpetually
  dirty. Fix: allow min brightness 0 (change the clamp floor to 0 — verify the engine/OEM-range mapping
  handles 0) so the value sticks and dirty clears. Likely the same root for the "always dirty" report.]
- **G3-F4 (BUG?) "Add Quick Settings tile" says tile already exists on first add.** Possible race on the
  first add. [`DashboardViewModel.addTile` / `canAddTile`; TILE_ALREADY_ADDED(2) returned spuriously.]
- **G3-F5 (BUG/REGRESSION) Dashboard does not animate on device.** Numbers don't roll and the slider
  doesn't animate. [S14 added `animateIntAsState` for the number + the bar already used
  `animateFloatAsState`; on-device neither animates — investigate. Likely the pipeline publishes only a
  settled value so target==applied each emission (no delta to animate from), or the state updates aren't
  recomposing the instrument. May need to animate toward target during the cycle, not after it settles.]
- **G3-F6 (BUG?) Super dimming at daytime with Spread (Circadian)=100 still dims slightly.** Extra Dim
  engaging with effective dimming strength ~0 — Android quirk or genuine bug? If genuine, **force super
  dimming OFF when dim_shell ≈ 0**. NB: reduce_bright_colors only takes INTEGER levels (so a tiny
  fractional shell rounds to 1 and still dims). [`SuperDimmingCoordinator` — round the level and treat 0
  as disengage; check `circadianDimMultiplier` at spread 100 doesn't leave a residual 1.]
- **G3-F7 README lacks shields.io badges** like AAB has. [Add build/license/version badges to README.md.]
- **G3-F8 CONTRIBUTING policy is too strict — blocks legitimate Android/Kotlin-layer contributions.**
  Owner's point: a fix for a Tideo-only bug (e.g. `ShizukuGrantGateway` crashing on Android 15,
  `AmbientMonitoringService` killed by Xiaomi battery saver, a `ChartCanvas` memory leak) can't go to
  Tideo (auto-closed) NOR to AAB (Tasker repo, bug doesn't exist there). We're blocking community help for
  exactly the layer where contributors are most useful (domain math is locked). **Rethink the policy:**
  ALLOW Android/Kotlin-layer bug-fix PRs to Tideo; keep feature/brightness-logic upstream at AAB. Add
  `.github/ISSUE_TEMPLATE/bug_report.md` (and likely loosen `redirect-external-prs.yml` so it redirects
  *feature* PRs but not app-layer bug fixes, or drop the auto-close in favour of a label/triage). Update
  CONTRIBUTING.md + README "Contributing" accordingly.
- **G3-F9 (DOC BUG) README "Shizuku … never a runtime dependency" is inaccurate.** The `_GetWifiNoLocation`
  SSID path runs `cmd wifi status` via Shizuku's shell at runtime for context automation (S12.7d
  `ShizukuWifiSsidStrategy`/`ShizukuShell`). Update the README claim AND any code comments that repeat it
  (grep "never … runtime"/"one-time grant"); state Shizuku's runtime SSID use honestly.
- **G3-F10 Menu icons — review against Material Symbols.** Visit fonts.google.com/icons and pick better/
  correct icons where the current choices are off. [MenuScreen / AabNav nav rows.]
- **G3-F11 (BUG) Dashboard "Reset to auto" does nothing.** [S13e wired it to
  `DashboardViewModel.resetToAuto` → `AutoBrightnessRuntime.reapply`; verify it actually re-applies / snaps
  to the computed brightness, and clarify the label/affordance — owner couldn't tell what it does.]
- **G3-F12 Safety audit.** Any glaring safety issues? Known/accepted: the cleartext ip-api.com geo-IP
  fallback — must be **transparently documented** so privacy-conscious users can avoid it and ensure it is
  never triggered (a visible setting/toggle + a README/privacy note; today it's silent). Sweep for others.
- **G3-F13 Restricted-settings guidance incomplete.** Tasker explicitly tells the user to **"tap it
  anyway"** (the slow/greyed restricted-settings option must be tapped to reveal it). Also the restricted
  state can appear for **usage access**, not just WRITE_SETTINGS. [Onboarding `RestrictedSettingsCard`
  (S12.7d/F33) — add the "tap it anyway" instruction + cover usage-access.]
- **G3-F14 Profiles & Contexts hero card too prominent.** Make it more subtle as a hero on the Menu.
  [MenuScreen `heroDestinations`/`HeroNavCard`.]
- **G3-F15 (BUG) Alpha graph x-axis mislabelled.** It says "lux change" but it is a **fold change** — in
  Tasker the axis is "relative change above the threshold" in **%**. [S14-confirmed: `AlphaResponseChart`
  x is the smoothing delta, not lux. Relabel + rescale to %, and add the live "now" value (G3-F2).]
- **G3-F16 (BUG) Applied suggestion leaves the "Suggested" line on the curve.** After Apply, the curve
  should return to teal (the live curve) instead of continuing to draw the suggested line.
  [`BrightnessCurveChart` `fittedCurve` should clear once applied; ToolsScreen `onApplyWizard`/state.]
- **G3-F17 (PARITY) Curve suggestion quality is poor — verify the task38 port.** Is the Tasker curve-
  fitting engine (`CurveSuggestionEngine`, S6) faithfully ported? **`tau` should be 0**; a default of 4
  seems overkill — **expose `tau` as a user parameter in the wizard**. [May touch the domain fence
  (`CurveSuggestionEngine` is in `:domain`); cross-check against task38 + the S6 reference/goldens before
  changing — evidence-backed only.]
- **G3-F18 (INFO) Battery usage baseline (not a bug).** ~5h30 SOT: Sensors **13 mAh** (good — the
  event-driven loop + proximity/light are cheap), Wi-Fi 80 mAh, CPU 612 mAh, Screen 617 mAh, Media 198,
  BT 151 (variance 685 = owner's own system mis-report, ignore). Record as the on-device power baseline;
  no action unless a later change regresses sensors/CPU.




