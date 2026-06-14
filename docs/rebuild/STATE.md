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
Status values: DONE · PARTIAL · BLOCKED (see failure protocol in CLAUDE.md).

## Current state

**S12.6a + S12.6b + S12.6c DONE → S12.6d/e remain.** S12.6c (pipeline behaviour correctness, D-051)
fixed the runtime bugs the re-test found: Apply/profile-load + min-brightness now take effect immediately
(reevaluate-fresh-baseline before reapply, G2R-F11/F12); manual override points are captured + persisted
(OverridePointStore) and overlaid on the curve with a ≥9-point fitted curve (G2R-F13/F14); rapid-light
override false-positives are fixed (task567 settle-wait re-read + device-exact AnimationRunner read-back,
G2R-F26/D-049); PWM-sensitive now floors the hardware brightness at the dimming threshold
(G2R-F27/D-050). domain/, golden vectors and ChartCanvas stayed fenced. Ladder GREEN
(`:app:testDebugUnitTest`=104, `:platform:test :domain:test :app:assembleDebug :app:lintDebug`).
**Next: S12.6d/e, then HUMAN GATE 2 re-test after S12.6e.**

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
  PWM-sensitive hardware-floor clamp G2R-F27/D-050 — D-051). Remaining: **S12.6d** (profile
  save/overwrite/factory-restore + SAF legacy import + per-screen reset +
  Apply-gate), **S12.6e** (label/long-press-help audit + context Wi-Fi/location + usage-access flow +
  load toasts). b/c/d/e are a parallel window now that a is merged. Domain/ + golden vectors +
  ChartCanvas API stay fenced. **NOTE for b/c/d/e:** the AAB Menu is now the home `AppRoute.Menu`;
  use `navigateTopLevel` for any new top-level destination so back still returns to the Menu; the
  global debug selector S12.6b moves OFF Misc belongs on the new Live Debug screen reached from the Menu.
- **HUMAN GATE 2 — RE-TEST AGAIN** after S12.6e. Re-verify all G2R-Fn + the original Gate-2 set.
- **S13** (chart replication + static screens) follows S12.6 on the serial spine — preconditions S12.6
  DONE (faithful screens + menu IA), S6 DONE. S13 copies `ui/graph/BrightnessCurveChart.kt` (over
  read-only `ChartCanvas.kt`) into the six remaining charts and fills their `ChartPlaceholder` host slots
  (tagged in screen_map + anonymous_handlers `deferred-S13`), plus About/UserGuide content. Haiku/high.
- Carried for S12.5/S13/S14 (D-040, D-044): unprivileged overlay dimming (task698 DC-like / 653/654) is
  NOT wired (S9b did the ELEVATED secure path only); DimDynamic (circadian dim strength, task646
  ScalingUse branch) passes null pending real solar windows (D-039d); proximity damp (task545) still
  unwired; **curve-wizard override-point capture/persistence still NOT wired (D-044c) — deferred from
  S12.5c (the wizard runs against an empty set → "need ≥ 9"); S13/S14 should add runtime override-point
  capture so the wizard has real input.**

## Deviations & discoveries ledger

Seeded by the S0 audit (details in CLAUDE.md "Facts & corrections ledger"):

- D-001: Java blocks are action code 474 (40), not 598. (Affects S1.)
- D-002: task661 has no Java; curve math is in Variable Set expressions. task663's Java is a
  plot-side copy for cross-validation only. (Affects S1, S4.)
- D-003: Profile-level ConditionLists carry pipeline gating (prof760: absolute thresholds,
  sensor-accuracy trust, %AAB_MainLoop). (Affects S1, S9.)
- D-004: AabSettings schema gaps: %AAB_AnimSteps, %AAB_MaxSteps, %AAB_ThreshMidpoint;
  AnimationConfig defaults conflict with AabSettings. (Affects S1 defaults_audit, S8.)
- D-005: :platform is currently kotlin("jvm"); becomes com.android.library in S3.
- D-006: 125 distinct %AAB_* variables (not 122 as older docs say).
- D-007: settings.gradle.kts has NO pluginManagement block → AGP unresolvable → gradle
  CONFIGURATION fails for every target, even pure-JVM `:domain:test` (verified 2026-06-11 with
  Gradle 8.14.3: "Plugin com.android.application 8.5.2 was not found"). The Codex build was
  never runnable. S3 must add `pluginManagement { repositories { google(); mavenCentral();
  gradlePluginPortal() } }` (or migrate plugin decls accordingly). Until S3: no gradle target
  works at all. (Affects S3; S4 is safe — it depends on S3.)

- D-008: D-004 RESOLVED (S1 `defaults_audit.md`). Canonical from task570: `AAB_AnimSteps=20`,
  `MinWait=25`, `MaxWait=65`, `Throttle=AnimSteps*MaxWait+10=1310`, `ThreshMidpoint=log10(Zone2End)=4`.
  `AAB_MaxSteps` is in the 125-var census but NEVER assigned a default → legacy/unused, do not invent
  one. The salvaged `AnimationConfig` defaults (50/5/30) are WRONG; use 20/25/65. Settings missing from
  `AabSettings.kt`: `AnimSteps`, `ContextOverride`, `SetupTitle` (+ derived `ThreshMidpoint`). Split:
  38 SETTING / 4 DERIVED (form2A/2D/3A, ThreshMidpoint) / 83 RUNTIME. (Affects S5, S8.)
- D-009: prof760 + prof758 multi-clause gates use Tasker `And2`/`Or2` sub-grouping; exact
  parenthesization (and polarity of `%AAB_MainLoop != On`) is UNRESOLVED — literal sequences captured
  in `extraction/profiles.md`, best-effort reading flagged. Validate against runtime in S9. `ThreshAbsLow/High`
  (prof760 abs gate) are written by task546 _Set Thresholds_, NOT task570. (Affects S9; informs S4.)
- D-010: Engine vs Tasker micro-divergences found in S1 spot-check (code left untouched, for S4/S5):
  (a) `BrightnessEngine.luxSmoothing` clamps `luxAlpha.coerceIn(0,1)` — task535 does NOT clamp;
  (b) `mapLuxToBrightness` wraps `^0.33` bases in `.coerceAtLeast(0.0)` — task661 does NOT. Dynamic-threshold
  sigmoid+dark branch is an EXACT match. (Affects S4 golden boundary rows, S5.)
- D-011: BRANCH DISCREPANCY. CLAUDE.md/RUNBOOK say work on `claude/modest-bohr-5ybl2i`, but this
  session's checked-out branch and push target is `claude/practical-mayer-kaopcf` (per session directive).
  S1 committed/pushed to `claude/practical-mayer-kaopcf`. Future sessions: confirm the intended branch;
  if the canonical branch is modest-bohr, these S1 docs may need to be merged/cherry-picked over.

- D-012: BRANCH DISCREPANCY continues. This S2 session ran on `claude/dreamy-brahmagupta-eqd4nu`
  (per session directive), not `claude/modest-bohr-5ybl2i` (CLAUDE.md) nor `claude/practical-mayer-kaopcf`
  (S1, D-011). Three branches now carry program docs. A future session MUST reconcile/merge these onto
  one canonical branch before code segments (S3+) build on them.
- D-013: SOLAR SOURCE RESOLVED (task90). Dynamic Scale COMPUTES sunrise/sunset/dawn/dusk/solar-noon
  ITSELF via inline NOAA solar equations (declination `0.39782*sin(L)`, `H=acos(cosH)`) from
  lat/lon/date — it does NOT consume Tasker `%SUNRISE`/`%SUNSET`. Polar: `cosH` out of [-1,1] →
  `AAB_SunStatus="polar"`, `ss_sunlight_duration=1440` (midnight sun, `-2.0` marker) else `0`. Normal:
  `durationMins=(set-rise)/60`, `+1440` if negative. **S6 must port a NOAA solar calculator**, not call
  any platform sunrise API; golden-test vs NOAA tables incl. polar. (Affects S6.)
- D-014: CONTEXT PRECEDENCE is NOT profile `<pri>` (prof762–768 are all pri 0). Real precedence = the
  per-rule integer `priority` inside `contexts.json`, resolved in task43 PASS 3: highest priority wins,
  ties → specificity (# matched trigger dimensions), final ties → array order. An override swaps the
  ENTIRE active profile via `_ProfileManager LOAD_FILE` (39-key snapshot) + re-runs Set Initial
  Brightness — NOT a scale-only/min-max-only modifier. Two caches: `%AAB_ContextCache`
  =`[BATT][LOC][WIFI],pkg,pkg,` (token gate + app set) and `%AAB_ContextJSONCache` (full JSON RAM copy);
  disk truth = `Download/AAB/configs/contexts.json`. Daily reset prof8→task26 fires 03:00, clears ONLY
  the JSON RAM cache (code 549), forcing disk reload. task43 has per-caller cooldowns
  (Resume 0 / Batt 30s / Loc 8s / Wifi 8s / Time 1s / else 500ms) + signal veto gates. (Affects S10; corrects feature spec §1.)
- D-015: FORMULA VALIDATION = 5 rules. task583 `_RedInvalidFormulae` (3 advisory, marks scene field red,
  no abort): `form2A<0`, `form3A<0`, `form2C>zone1End`. task707 `_ValidateBrightnessParams` (2 safety):
  predicts brightness at 1000 lux via the zone formula that 1000 lux falls in; if `<25` → warning toast +
  `%is_safe=no`; `%AAB_MaxBright` defaults 255 when unset. (Affects S8 `validate()`, S12 red-invalid UI.)
- D-016: PRIVILEGE DETECTION (task378) is a first-hit probe: Root(`su -c id`→uid0) → WriteSecure
  (`checkPermission`) → Shizuku(`getShizukuService`) → ADB-WiFi(TCP 127.0.0.1:5555) → None. ALL positive
  results map to ELEVATED; None = unprivileged overlay. BASIC (WRITE_SETTINGS) is a SEPARATE gate in
  task563 step 1. `%AAB_Privilege` cached unless caller forces re-detect; `%AAB_PermGranted="3"`=all 8
  runtime perms granted. task643 only TEACHES the `pm grant WRITE_SECURE_SETTINGS` adb command (clipboard
  dialog), never grants. (Affects S7 PrivilegeManager, S11 onboarding.)
- D-017: `%AAB_AnimSteps` HAS a user-facing slider (Misc Settings, range 0–100) and `%AAB_ScaleTaperMidpoint`
  a slider (Experiment Settings, 130–240) — confirms both are real settings (reinforces D-008: add AnimSteps
  to AabSettings). All numeric inputs in settings scenes are `EditTextElement` (no SliderElement in
  brightness/reactivity/superdimming); toggles render as overlaid Switch PAIRS (on+off overlay) → collapse
  to one M3 toggle. (Affects S8 schema, S12 UI.)
- D-018: Scene element census = exactly **450** raw `<*Element sr=>` (224 Rect/129 Text/28 Web/22 EditText/
  20 Properties/16 Switch/6 Slider/5 Button); ~264 are functional, the rest are nested `background` rects.
  `screen_map.md` dispositions all 450 (functional → target screen; background rects + PropertiesElement
  scene-chrome → dropped). 8 of 28 WebElements are Chart.js charts → named Compose charts; generators are
  NOT 1:1 with scene names (Experiment Graph uses task549/HTML_Graph4; Taper Graph uses task657/HTML_Graph5).
  Unresolved carried from features extraction: And2/Or2 grouping in task551 OFF-path branch (validate in S9).

- D-019: S3 revealed three pre-existing Codex compile bugs (no prior baseline was runnable due to D-007):
  (a) `BrightnessCurveConfig` was missing `zone1End: Double = 35.0` field used by `mapLuxToBrightness` —
  added with same default as `ThresholdConfig.zone1End`; S4/S5 must keep both fields in sync or unify.
  (b) `AmbientMonitoringService.kt` imported `com.tideo.autobrightness.app.R` but namespace is
  `com.tideo.autobrightness` → corrected to `com.tideo.autobrightness.R`.
  (c) `ProfileImportExportManager.kt` called `AabProfilePayload(settings.validate())` positionally but
  constructor is `(schemaVersion: Int, settings: AabSettings)` → fixed to named arg `(settings = …)`.
  Additionally: `Theme.Material3.DayNight.NoActionBar` requires com.google.android.material (not in SDK
  alone) → used `android:Theme.Material.Light.NoActionBar` (SDK built-in) as XML parent for Compose app.
  Domain test `rapidLuxSpike_isSmoothedByTaskerFormula` fails: lux spike 20→800 gives luxAlpha=1.0
  (no smoothing), but test expects <1.0. Root cause: effectiveDelta ≈ 36.87 → exp(−66) ≈ 0 → alpha=1.
  This is a pre-existing engine parity bug per D-010; S4 will characterize it via reference impl; S5 fixes.
  Final version matrix: Kotlin 2.0.21, AGP 8.7.3, Compose BOM 2024.12.01, Gradle 8.14.3, minSdk 31,
  compileSdk/targetSdk 35. (Affects S4: note the domain compile fix; S5: fix engine parity.)

- D-020: BRANCH POLICY RESOLVED (closes D-011, D-012). Per-session `claude/*` branches are BY DESIGN:
  the owner merges each segment to main via PR after stage completion; sessions start from fresh main.
  CLAUDE.md rewritten accordingly. Future sessions: do NOT log or reconcile branch-name differences.
- D-021: ConditionList semantics VALIDATED (owner-confirmed prof760 staging incl. `%AAB_MainLoop != On`
  mutex polarity; cross-checked on prof758): plain `And`/`Or` bind tighter (inner groups, `And` > `Or`);
  `And2`/`Or2` join those groups left-to-right. Resolves D-009 + INDEX unresolved #1; S9 runtime
  validation downgraded to a Gate-1 sanity check. DISCOVERY: ConditionList children are stored
  ALPHABETICALLY in the XML (`bool10` < `bool2`) — S1's prof758 bool sequence was scrambled by this;
  fixed in profiles.md; recipe R4 updated. task551 act0 reading resolved (D-018 leftover).
  (Affects S4 gate vectors, S9.)
- D-022: prof769 contexts verified (XML L722–743): Event 2083 (significant-motion/shake) + State 120
  arg0=3 (orientation upside-down) + State 123 arg0=1 — panic = flip + shake → max brightness, listeners
  off, `%AAB_Service=Off`. prof759/task545 "Detect Proximity" (L16424, new doc
  tasks/task545_detect-proximity.md) sets `%AAB_Proximity` near/far; damps LuxAlpha ×0.1 in task544 —
  it never pauses the pipeline. (Affects S9.)
- D-023: `%AAB_Debug` = 10 named info categories from the Debug-scene selector (XML L2773–2782): Off /
  Skip Animations / Animation Details / Light Eval Thresholds / Dynamic Scale Calcs / Super Dimming
  Info / Overlay Preview / Graph Metrics / Context Automation / Context Location. S1's inferred glosses
  for 7/8/9 were wrong; features_spec corrected. (Affects S12 debug UI.)
- D-024: SANCTIONED DEVIATIONS (owner): (a) privilege detection must NOT read Tasker pref `adbwp`
  (drop ADB-WiFi probing; elevated truth = checkPermission(WRITE_SECURE_SETTINGS); ADB/Shizuku/root are
  grant channels only — matches existing S7/S11 design); (b) task563's polling-dialog onboarding flow is
  NOT the parity contract — only its 8 gates + order are; S11 keeps its ActivityResultContracts design.
  (Affects S7, S11.)
- D-025: `%AAB_Test` = curve-wizard diagnostics report (R²/nRMSE/bias; task38 logBuffer), copied to
  clipboard via the single code-105 action (L9864), documented to users (guide L8715) — surface in the
  rebuilt wizard UI (S6/S12). INDEX action codes fixed: 590 = Variable Split (was mislabeled "Array
  Push"), 105 = Set Clipboard. defaults_audit: added the 4 non-AAB capital-letter globals
  (%SmoothedLux 15× / %AutoBrightRunning 13× / %LastAAB 10× / %LuxAlpha 9×); `%AAB_MaxSteps`
  owner-confirmed legacy (abandoned predecessor of AnimSteps — do not port). (Affects S4, S6, S8, S12.)
- D-026: ANONYMOUS TASKS: 168 of 276 tasks are unnamed scene-element handlers; ALL are wired from
  scenes (none dead); 34 are `keyTask` = per-scene back/hardware-key behavior that S2 dropped as scene
  chrome. Census: extraction/tasks/anonymous_handlers.md — S12/S13 precondition (every row ported or
  dropped(reason)). Circadian Dimming Graph re-homed Dynamic Scale → Animation & Dimming (owner; opened
  from Superdimming Settings via task517, button visible only when `%AAB_ScalingUse` on; screen_map +
  superdimming_settings gloss fixed — old `_CalibratePowerDraw` gloss was wrong; _disp_group4 already
  agreed). (Affects S12, S13.)

- D-027: S3.6 PLAN HARDENING from external LLM peer review of the S0–S3.5 approach. Adopted:
  (a) S8 preconditions now include S2 (its inputs always listed S2's features_spec.md; the DAG
  just never enforced it — no schedule impact, S2 already DONE). (b) S4 brief gains an explicit
  transcription protocol for code-547 maths expressions (verbatim → parse-tree note in provenance
  comment → cross-validate task661 vs task663 over the golden lux grid → disagreements recorded
  in parity_gaps.md, never resolved by guessing). (c) S9 SPLIT: S9a = pipeline controller +
  AnimationRunner + OverrideMonitor + service rebuild + tests (parity-critical core); S9b =
  super-dimming wiring + tile + boot receiver + legacy rip-out. Gate 1 moves after S9b (the
  reviewer proposed gating after S9a, but Gate 1's reboot/tile/dimming checks need S9b
  deliverables). S10/S11 preconditions → S9a+S9b. (d) BINDING concurrency model (CLAUDE.md +
  S9a brief): single pipeline coroutine, one event runs to completion incl. animation frames;
  events arriving mid-cycle are DROPPED, not queued — prof760's `%AAB_MainLoop != On` clause is
  a re-entry mutex that SUPPRESSES events while a cycle runs. ⚠️ The reviewer claimed "Tasker
  would queue it" — wrong: the gate drops; a queueing/conflating implementation would process
  events Tasker never would. (e) Profile gates are HARDCODED Kotlin booleans with provenance
  comments (no generic ConditionList evaluator) + a dedicated prof758/prof760 truth-table unit
  test in S9a (per-branch true/false — a mis-parenthesized gate silently suppresses sensor
  events). (f) S12 step-0 triage of anonymous_handlers.md into trivial-chrome / settings-mutation
  / complex buckets, committed before screen work. (g) S11 revisits D-019's XML theme workaround
  (Compose M3 needs no Material XML parent). REJECTED from the review: the Tasker
  single-threaded/queueing claim (corrected in d — Tasker runs tasks concurrently by default;
  this project's serialization comes from its own MainLoop mutex); the Tasker expression
  type-coercion concerns as a distinct risk (expressions are already captured verbatim per S1,
  and (b)'s 661-vs-663 cross-validation is the actual safeguard). (Affects S4, S8, S9a, S9b,
  S10, S11, S12.)

- D-028: S4 PARITY GAPS CHARACTERIZED (full detail in `parity_gaps.md`). The Tasker reference
  oracle + 8 golden CSVs are committed and immutable. The current `BrightnessEngine` diverges from
  Tasker in 7 enumerated gaps, all from two systemic causes: **R1** rounding-tie semantics — engine
  uses `kotlin.math.round` (ties-to-even) where Tasker uses Java `Math.round` (ties-toward-+∞) and
  `BigDecimal(double).setScale(n,HALF_UP)`; **R2** clamps/structure the engine added that Tasker
  lacks (luxAlpha `coerceIn`, mapped-brightness clamp inside the mapping vs after scaling, threshold
  `<0.2`/`<10` special-cases, `^0.33` `coerceAtLeast`). gap-01 smoothing(535), gap-02 absThresholds(546),
  gap-03 mapping(661), gap-04 animation(543), gap-05 dynamicThreshold(544), gap-06 taper(548),
  gap-07 the `rapidLuxSpike` CONTRACT test (its `luxAlpha<1.0` expectation is WRONG — a spike of
  20→800 lux legitimately yields alpha=1.0 in BOTH engine and reference; resolves the D-019 hanging
  failure: not an engine bug, a bad fixture). 661-vs-663 cross-validation PASSES (Form2D≡Zone1End per
  D-008/D-025) — no XML re-derivation needed. CONFIRMS D-010(a)(b) at row granularity. (Affects S5:
  close all 7; never edit the reference/vectors to make tests pass.)
- D-029: minor build addition — `domain/build.gradle.kts` gained a `tasks.withType<Test>` stanza that
  forwards the `regenGolden` system property to the test JVM (Gradle does not forward `-D` to forked
  test JVMs by default). Sanctioned by S4's "(if needed) a test-resources stanza" allowance; no
  production/behavior impact. Golden regen command: `./gradlew :domain:test -DregenGolden=1
  --tests "*GoldenVectorGeneratorTest*"`.

- D-030: S5 follow-on corrections from owner PR review (F1–F5, committed on `claude/youthful-newton-nosjpo`):
  (a) **S4 oracle gap**: task700/646/647 were never added to `TaskerReference.kt` in S4 — added in S5
  follow-on as `finalDimLevel()` and `dimProgressAndShell()`. `superdimming.csv` (2016 rows) generated
  and committed; `CorePipelineParityTest` gains `softwareDimming_finalDimLevel_matchesOracle` and
  `softwareDimming_dimShell_matchesOracle`. Existing 8 golden CSVs byte-identical after regen.
  (b) **OverrideRules.recordOverridePoint fix**: task561 act0 gate is `ScalingUse=true AND
  ScaleDynamicCompress!=0`; the initial S5 port only checked `dynamicCompress!=0` (missing scalingUse).
  Fixed. Insertion order was also wrong (newest-last vs Tasker's code355 Array Push at index 1 =
  newest-first); fixed to `listOf(new) + history` + `take(maxEntries)`. New `OverrideRulesTest.kt`
  covers all gate polarities, suppress-echo, shouldCommitPause, and recordOverridePoint edge cases.
  (c) **SoftwareDimming.dimProgress span guard (SANCTIONED DEVIATION)**: production code retains
  `if (span <= 0.0) return 1.0` which has no counterpart in task646/647. Rationale: task646/647
  only invoke `dim_progress` when `target_brightness < DimmingThreshold` (act1 gate), which
  implicitly means `DimmingThreshold > MinBright` in any valid configuration. Division-by-zero would
  be a misconfigured (invalid) settings state. The guard prevents crashes on invalid input and agrees
  with the oracle on all valid inputs (golden vectors exclude `threshold <= minBright` combinations).
  This guard is NOT exercised by `superdimming.csv` and does NOT affect parity. S9a/S9b: rely on
  `SoftwareDimming.finalDimLevel`, `.dimProgress`, `.dimShell` being fully golden-tested/unit-tested.
  (d) **InitialBrightnessTest.kt** added: sweeps vs `TaskerReference.setInitialBrightness` including
  tie cases (0.5 → 1, 1.5 → 2, -2.5 → -2) confirming Math.round semantics.
  (e) **calculateAnimation coerceAtLeast(1)**: the `loops.coerceAtLeast(1)` guard in
  `BrightnessEngine.calculateAnimation` has no Tasker counterpart and is vector-uncovered (animSteps=0
  never appears in animation.csv). Retained as crash-prevention for invalid AnimSteps=0; not a parity
  issue on valid inputs. (Affects S9a for integration tests if animSteps=0 ever occurs at runtime.)

- D-031: S6 PARITY CORRECTION — `computeDynamicScale` + `rampProgress` moved from `BrightnessEngine`
  to `DynamicScaleEngine`. Two behavioral corrections vs the old engine:
  (a) Duration guard: old used `coerceAtLeast(1.0)`, Java block uses `< 1 → 60.0` (D-010 family);
  (b) Scale rounding: old used `round3` (Math.round-based), Java block uses `BigDecimal(raw).setScale(3,
  ROUND_HALF_UP)` (consistent with all other BigDecimal HALF_UP corrections in S5).
  The existing `BrightnessEngineContractTest.circadianWindow_changesScaleAcrossDayNight` still passes
  because it only checks direction (day > night), not exact values. No other test was sensitive to
  the rounding change. (Affects BrightnessEngine.kt; new CircadianParityTest golden-tests the correct
  BigDecimal behavior.)

- D-032: S7 SHIZUKU LIMITATION. `ShizukuGrantGateway` lands binder availability check
  (`Shizuku.pingBinder()`) and permission request (`Shizuku.requestPermission()`), but the
  actual `pm grant WRITE_SECURE_SETTINGS` exec via `Shizuku.newProcess` or a bound user-service
  requires a registered user-service component — deferred to S11 as agreed in the S7 brief's
  failure notes. After any successful grant, the app uses `Settings.Secure` directly (no runtime
  binder dependency, D-024). The stub is clearly commented TODO(S11) in ShizukuGrantGateway.kt.
  (Affects S11.)

- D-033: S8 SCHEMA CORRECTIONS. (a) `scale` field changed from `Int` to `Float` to accommodate
  task592 profile values 0.8 (Battery Saver) and 1.15 (Outdoors). JSON migration is transparent
  (JSON integer `1` decodes as `Float 1.0f`). AabSettingsContract updated to range 0.1..10.0.
  (b) `throttleDefaultMs` default corrected from 1000 to 1310 (= task570 AnimSteps*MaxWait+10 =
  20*65+10; the old value was never audited). (c) `debugLevel` contract range corrected to 0..9
  (was 0..5; there are 10 categories per D-023). (d) AabSettingsContract `%AAB_DefaultThrottle`
  renamed to `%AAB_Throttle` (matching the actual Tasker variable; legacy import handles both).
  (e) `DefaultProfiles.Default` animation values (50/5/30/1510) differ from task570 init values
  (20/25/65/1310) — they come from task592's `getBaseProfile()`, which is the authored initial
  profile, not the settings defaults. The `AabSettings()` constructor still uses task570 values.
  (f) `thresholdMidpoint` in DefaultProfiles.Default is 3.0 (from task592), not 4.0 (task570).
  Both values are correct in their respective contexts. (Affects S9a mapper usage, S12 UI.)

- D-034: S8.5 REVIEW FIXES (S7 surface). (a) **Suppress-echo redesigned**: the S7 token-set
  scheme (registerExpectedWrite/consume-on-match) had four defects under S9a's N-frame
  animation: ContentObserver re-reads the CURRENT value so a delayed callback for frame N
  consumes frame N+1's token (false manual-override pause), CopyOnWriteArraySet collapsed
  duplicate frame values, no-op writes never notify (orphan tokens), and orphans never expired
  (stale token could swallow a real user override). Replaced with last-self-write matching:
  `write()` records the device value; `isSelfWrite(raw)` = equality with the LATEST write, not
  consumed; `clearSelfWriteMarker()` for pause. This is Tasker-faithful (task567 compares the
  observed value against %LastAAB). S9a MUST: share one controller instance between writer and
  observer (per-instance state), and call clearSelfWriteMarker() on pause. (b) OEM
  normalization: `.toInt()` truncation both directions made write(x)→read() drift −1 on
  non-255 devices; now Math.round both ways (round-trip identity), clamps on both paths,
  `deviceMaxOverride` ctor param as test seam. (c) forceManualMode now idempotent (second call
  no longer overwrites the saved AUTOMATIC mode); savedMode still lost on process death —
  S9a should persist it if restore-after-crash matters. (d) PrivilegeManager.writeSettingsIntent()
  added (BASIC grant helper the S7 brief specified). (e) ShizukuGrantGateway: listener now
  removed on denial too; pre-granted permission honored. (f) ForegroundAppMonitor retains
  last-known package across polls (trailing 3s window yielded null for apps foregrounded >3s —
  would have broken S10 app rules); uses ACTIVITY_RESUMED. (g) SecureDimming level clamped
  0..1000 + success-path/clamp tests; ELEVATED shadow-grant tier test; non-vacuous observer
  filter test. KNOWN RESIDUAL (S9a): user override landing exactly on the last self-written
  value is filtered — identical to Tasker %LastAAB behavior, accepted. (Affects S9a, S9b, S10.)
- D-035: MODEL POLICY from S9a onward — code segments upgraded Sonnet → **Opus** (S9a high;
  S9b/S10/S11/S12 medium); S13 stays Haiku; S14 already Opus. Owner observed Sonnet sessions
  compacting (×1) or nearing compaction; in-repo evidence: every Sonnet code segment passed
  its own acceptance gate yet review later found real defects (S5 → D-030 b: gate polarity +
  newest-first order; S7 → D-034 a/b). Golden-vector parity caught none of these because they
  live in glue/platform code outside the vectors — exactly where reviewer attention, not test
  coverage, is the safety net. Compaction events must now be recorded in segment-log rows.
  (Affects S9a…S12 session directives.)

- D-036: S8.5 REVIEW FIXES (S4/S5 domain). Two CRITICAL parity holes found (both invisible to
  the prior golden vectors because no CSV exercised the path) and fixed against the EXISTING
  reference oracle (no oracle/vector edits — the 8 prior CSVs are byte-identical after regen):
  (a) **task661 ScalingUse=false branch + %AAB_Scale were missing.** `BrightnessEngine.evaluate`
  unconditionally ran the task548 taper; task661 act10-14 is `If ScalingUse → taper; Else →
  mapped*%AAB_Scale+%AAB_Offset`. The engine had no `scale`/`scalingUse` field, so a real config
  (ScalingUse off, Scale≠1.0) produced wrong brightness. Added `scalingUse:Boolean=true` +
  `scale:Double=1.0` to `BrightnessCurveConfig`, added the linear branch + a public
  `calculatedBrightness(lux,cfg,scaleDynamic)` (mirrors the oracle's act10-21, clamps as doubles),
  wired the mapper (`scalingUse←scalingEnabled`, `scale←settings.scale`). New golden
  `calculated.csv` (2752 rows: 4 variants × lux grid × {ScalingUse T/F} × scaleDynamic grid) +
  `calculated_matchesEngine` parity test. (b) **%AAB_ScaleDynamicCompress (effectiveScale) was
  computed then discarded.** `compressedDynamicScale` now returns `CompressedScaleResult`
  (calculatedBrightness + effectiveScale); `BrightnessPolicyOutput` gains `scaleDynamicCompress`.
  S9a MUST pass `output.scaleDynamicCompress` as the `dynamicCompress` arg to
  `OverrideRules.recordOverridePoint` (task561 gate: scalingUse=true AND compress≠0). Also fixed:
  taper test now asserts effectiveScale (was unasserted); added direct
  `softwareDimming_dimProgress_matchesOracle` (was only indirectly tested via dimShell); exact
  `form2A`/`form3A` defaults (were 29.58/2513.0 ≈; now 29.58039891549808/2513.1533352729266 —
  unused at runtime since the mapper derives them, but no longer a latent trap). `:domain:test`
  + `:app:testDebugUnitTest` GREEN.
  ACCEPTED (not bugs): `dynamicThreshold(rawLux,…)`'s rawLux is dead — task544 uses par1 only for
  the `relative_change` log var; the threshold uses %SmoothedLux only, so the out-of-sync
  currentLux≠smoothedLux case provably cannot diverge (left in for Tasker-signature fidelity).
  FLAGGED for S9a/S14 (Finding 7, not resolved): task546 stores %AAB_ThreshDynamic as a
  BigDecimal-formatted percentage STRING that task535 re-parses; production passes
  `dynamicThreshold*100` unrounded. The 16512-row smoothing golden is self-consistent, but
  verify whether task546's string-rounding shifts the smoothing input on-device.
  OPEN QUESTION for S9a: the mapper now drives BOTH `curve.scalingUse` and `dynamicScaling.enabled`
  from the single `scalingEnabled` setting — confirm against profiles/contexts extraction whether
  Tasker can run circadian (task90) independently of %AAB_ScalingUse. (Affects S9a, S9b, S14.)

- D-037: S8.5 REVIEW (S6 circadian + wizard). Math verified faithful to task90 Java blocks
  line-by-line (NOAA constants, tanh ramp, polar sentinels, schedule windows) and task38/task655
  (fitting constants, R²/penalties, form output) — no parity bug in the port. BUT one
  methodological hole fixed: **the circadian/wizard "reference" delegates to production**
  (`TaskerReference.solarTimes/buildScheduleWindows/dynamicScale` → SolarCalculator/
  DynamicScaleEngine, TaskerReference.kt:418-424), so `circadian.csv`/`wizard.csv` are generated
  FROM production and CircadianParityTest/WizardParityTest are regression-LOCKS, not independent
  oracles — and S6 never did the NOAA-table cross-check its brief required. FIX: added
  `SolarInvariantTest.kt` (7 assertions on astronomical invariants that hold regardless of
  implementation — equator-equinox ≈12h, dawn<rise<noon<set<dusk twilight ordering, eastward
  longitude advances sunrise [lng sign], N-hemisphere long-June/short-Dec + S reversed [LAT sign],
  high-arctic midnight-sun/polar-night [polar branch]); ALL PASS → the port is now independently
  confirmed correct, not merely self-consistent. Also: assert dawn/dusk epochs in
  circadian_solarTimes_matchesGolden (were unchecked); added wizard abort-path test (<9 points →
  null; no golden case exercised it). ACCEPTED/non-bugs: (a) DynamicScaleEngine derives
  morning/evening duration from window endpoints rather than Tasker's independent
  %AAB_MorningDuration vars — equivalent because act76 defines duration ≡ end−start; if S9a/S12
  ever exposes duration as a standalone setting, add explicit fields. (b) applyToLiveCurve form3a
  floor 0.0 matches task655 (the 0.001 floor is task38's post-blend safeguard, correctly only in
  suggest()). (c) BrightnessEngine.computeDynamicScale hardcodes dimSpreadPercent=0.0 — harmless
  (it returns only scaleDynamic, which depends on scaleSpread; dimDynamic is discarded there).
  S9b IMPACT: when wiring the dimming path, call DynamicScaleEngine.compute with the real
  %AAB_DimSpread and consume dimDynamic — do NOT route it through BrightnessEngine.computeDynamicScale.
  (Affects S9a, S9b, S12, S14.)

- D-038: S8.5 REVIEW (S8 settings/validator/contexts). One CRITICAL default fixed + two
  safety-validator test-vacuity fixes; model verified otherwise correct.
  (a) **CRITICAL — `contextOverride` defaulted `true`.** `%AAB_ContextOverride` is the runtime
  "manual context lock" latch: the watcher gate (contexts_spec §1.1) fires ONLY when
  `ContextOverride != true`, and PASS 4 skips the profile switch when it is true. Defaulting the
  baseline AabSettings to `true` would permanently suppress ALL context switching on every fresh
  install and after v1→v2 migration — S10's context system would never fire. Fixed default →
  `false` (+ AabSettingsContract rule + serializer comment + migration-test assertion). The
  defaults_audit "true (per-profile, task637)" describes the value stored INSIDE a saved
  override-profile file, not the baseline — the conflation that caused the bug. Legacy round-trip
  test unaffected (its fixture sets %AAB_ContextOverride=true explicitly, testing the import map).
  (b) **Validator tests were vacuous on the safety path** (task707): the test labelled "zone1
  formula" actually drove the zone-2 branch (zone-1 selection at line 57-60 was never executed by
  any test), and the "zone3" test did `filterNot { form3A|safetyBrightness|form2A }.size == 0`,
  which passes even with zero errors. Fixed: relabelled the zone-2 test, added a real zone-1
  selection test, rewrote the zone-3 test to assert the safety error actually fires (zone2End=999
  → form3A≈987 → safe_val≈3.3 < 25). The validator LOGIC is correct (zone select
  zone1End>1000→z1 / zone2End>1000→z2 / else z3; formulas match features_spec §5, form2D≡zone1End).
  DOCUMENTED (not fixed): (i) validator's `(1000-form2C).pow(0.33)` is NaN-safe only while form2C
  is range-clamped (1..50); S12 must run AabSettings.validate() (which clamps) before/with
  SettingsValidator, or add a guard — flagged for S12. (ii) `ContextRule.profile` stores a
  FILENAME (Tasker interop), not the 39-key snapshot, and the model carries no baseline for the
  "no winner → revert to %AAB_ProfileUser" path — acceptable per the S8 brief (storage model
  only), but S10 MUST hold the user-baseline profile reference externally and treat profile
  application as load-current-file (a sanctioned simplification vs Tasker's snapshot-at-creation).
  (iii) specificity counts timeRange and days as independent dims; spec wording "time, +1 if days"
  is ambiguous — S10 must confirm against task43 before finalizing the precedence tie-break.
  VERIFIED CLEAN: all 38 SETTING defaults vs task570/defaults_audit; %AAB_MaxSteps absent;
  migration (ignoreUnknownKeys + non-trivial v1 fixture, scale Int→Float transparent); the 4
  mapper conversions; 5 DefaultProfiles vs task592; ContextOverrideRules JSON interop. (Affects
  S10, S12, S14.)

- D-039: S9a RUNTIME CORE design decisions (all sanctioned by the S9a brief; flagged for S9b/S12/S14).
  (a) **Engine owns the dead-band; controller owns the prof760 gate.** The controller applies the
  prof760 ConditionList (accuracy-trust + absolute dead-band using the PREVIOUS cycle's stored
  ThreshAbsLow/High + MainLoop mutex) BEFORE calling the golden-tested BrightnessEngine, which
  internally recomputes its own absolute band (`shouldUpdate`). When a tick passes the controller
  gate but the engine treats it as a no-op (luxAlpha→0, same target), the result is a redundant
  write of the same value — harmless; the controller additionally skips the animation when
  `target == brightness.read()`. No behavioral divergence from the engine (which is the oracle), only
  avoided redundant writes. The two gates use the SAME stored band on the common path so they agree.
  (b) **Fast intra-cycle flags live outside the immutable snapshot.** `autoRunning` and `initializing`
  flip many times within one cycle and are read by the observer coroutine, so they are `@Volatile`
  vars in the controller, not fields of the StateFlow `PipelineState` (which holds the durable
  runtime vars per pipeline_spec §5). Everything durable is written ONLY from the consumer coroutine.
  (c) **MainLoop mutex = `AtomicBoolean inCycle` + `compareAndSet`.** Sensor ticks that lose the CAS
  (or whose gate sees `mainLoopOn`) are dropped at the collector, never enqueued (Tasker drop-not-
  queue, D-021/D-027). Control events (screen/pause/resume/panic/override) go through an UNLIMITED
  channel and are NOT dropped; one event still runs to completion (incl. animation) before the next.
  (d) **secondsOfDay is derived from UTC wall-clock** (`(clock()/1000)%86400`) with default ramp
  windows. Irrelevant in S9a (scalingEnabled defaults false → engine ignores circadian), but S9b/S12
  MUST supply real LOCAL seconds-of-day + solar windows (SolarCalculator) when wiring circadian.
  (e) **prof758 gate transcribed + truth-table-tested in ProfileGates, but task90 is NOT scheduled
  in S9a** — the periodic dynamic-scale recompute (prof758→task90) is S9b/S12. (f) **task696 only**
  in AnimationRunner; the task698 DC-like dimming write + SecureDimmingController wiring is S9b
  (pass `output.scaleDynamicCompress` to OverrideRules.recordOverridePoint — already done in the
  controller's handleOverride). (g) **Proximity damp (task545/prof759) is unwired** — the engine has
  no proximity concept and S9a's scope is the core loop; revisit if a later segment surfaces it.
  (h) **task567's CycleTime re-check wait is collapsed**: handleOverride applies OverrideRules
  .shouldCommitPause immediately rather than waiting %AAB_CycleTime, because the single-cycle model
  already guarantees no animation is mid-flight when an OverrideDetected event is dequeued. (Affects
  S9b, S10, S12, S14.)

- D-040: S9b RUNTIME FEATURES + RIP-OUT decisions (sanctioned by the S9b brief; flagged for S12/S14).
  (a) **Super dimming = ELEVATED secure path only.** SuperDimmingCoordinator wires the privileged
  reduce_bright_colors layer (task646 `dimShell` math → AndroidSecureDimmingController, disengage =
  task645) and runs it from the pipeline coroutine (post-animation in runCycle, plus setInitialBrightness;
  disengaged on override/pause/panic/hibernate). The task698 **DC-like UNPRIVILEGED overlay** transition
  (tasks 653/654) is NOT wired — it is a separate non-secure animation path out of S9b's "engage
  SecureDimmingController" scope; deferred to S12. The brief's deliverable named only SecureDimmingController.
  (b) **DimDynamic passes null** (task646 act6 ScalingUse branch → DimmingStrength×DimDynamic). DimDynamic
  is a DynamicScaleEngine output that needs real solar windows; S9a already defers real circadian wiring
  (D-039d, UTC seconds + default windows) and scalingEnabled defaults false, so the plain-strength else-branch
  is the correct default-config path. S12 must wire DimDynamic when it wires real local solar windows.
  (c) **AppModule is now the DI root.** `AppModule(context).createController(scope)` composes the S7 adapters
  + S9a pipeline + S9b dimming with a shared brightness instance (D-034 suppress-echo); the service delegates
  to it instead of inlining. createController builds fresh adapters per service lifetime.
  (d) **MaintenanceWorker no longer evaluates.** The toy EvaluateAndApply loop is gone; the live FGS owns all
  evaluation. The worker now only re-ensures the service is running (memory-pressure safety net) + writes a
  health heartbeat. The daily context-cache reset (contexts_spec, prof8/task26) is S10's wiring, not added here.
  (e) **QS tile** toggles serviceEnabled + start/stop via AutoBrightnessRuntime.onSettingChanged (task551
  on/off semantics); Robolectric cannot bind a TileService (ServiceController casts the tile binder) so its
  test is instantiation-only (brief-sanctioned downgrade). (Affects S10, S12.)

- D-041: GATE 1 PUNCH-LIST triage of the human on-device findings (G1-F1…F5). Three genuine
  runtime bugs fixed + one sub-bug; two findings owner-deferred to S12.
  (a) **G1-F1 (crash on unprivileged launch) — FIXED.** Root cause: `Settings.System.putInt`
  throws `SecurityException` without WRITE_SETTINGS; it was uncaught in the pipeline coroutine →
  process crash on the first cycle. Fix: platform writes (`AndroidScreenBrightnessController`
  write/forceManualMode/restoreMode; `AndroidSecureDimmingController` setLevel/setActivated) now
  swallow ONLY `SecurityException` (other throwables still propagate) → the loop degrades (no
  brightness change) instead of crashing. The self-write marker is only set on a successful write.
  Plus: MainActivity requests POST_NOTIFICATIONS at launch (Android 13+) so the FGS notification is
  visible; the notification shows "Grant 'Modify system settings'" when `Settings.System.canWrite`
  is false. NOTE for S11: this is minimal robustness, NOT the onboarding stepper — S11 still owns
  the full grant UX (WRITE_SETTINGS re-check, ELEVATED step).
  (b) **G1-F3 (notification Disable ≠ UI toggle) — FIXED.** SettingsViewModel read settings ONCE in
  init and never observed the DataStore, so the service's `serviceEnabled=false` write (Disable
  action) never reached the UI (same in-process ViewModel showed stale "On"). Fix: the VM now
  collects `settingsDataStore.data` → toUiState as the source of truth for enabled/min/max
  (local-only slider group state preserved). S11/S12: keep this flow when rebuilding the dashboard.
  (c) **G1-F4 (panic then Resume leaves brightness stuck) — FIXED.** task528 panic is a FULL STOP
  (act1-2 toggle %AAB_Service Off, act6 brightness 255, act7-8 disable dimming), not a pausable
  state. The S9a impl set serviceOn=false + cancelled the sensor but left a zombie FGS that Resume
  could not revive. Fix: `BrightnessPipelineController.emergencyStop()` (synchronous: cancel all
  jobs + restore 255 + disengage dimming + reset state) and the service's ACTION_PANIC now persists
  serviceEnabled=false + stopForeground/stopSelf (same teardown as Disable, plus the 255 write).
  Removed the channel-based `PipelineEvent.Panic`/`panicInternal`. After panic the user re-enables
  via the toggle/QS tile — coherent with Tasker (%AAB_Service=Off). The notification "Reset" action
  now performs this full stop.
  (d) **G1-F5 (super dimming) — DEFERRED to S12 + tier sub-bug FIXED.** Primary cause: %AAB_DimmingEnabled
  defaults false (task570 parity) and no settings UI persists it yet (S12) — owner chose to defer
  (verify at Gate 2). Sub-bug fixed now: `AndroidPrivilegeManager` caches the tier at construction,
  so a WRITE_SECURE_SETTINGS grant made AFTER the service started was invisible; AppModule's
  tierProvider now calls `refresh()` before each read so the grant is picked up live. S12: wire
  dimmingEnabled (+ strength/threshold) and DimDynamic (D-040) into the dimming path.
  (e) **G1-F2 (manual override pause) — DEFERRED to S12.** %AAB_DetectOverrides defaults Off
  (task570 parity, defaults_audit L55) and has no UI to enable; with it off, both OverrideMonitor
  and AnimationRunner read-back detection are correctly disabled. Owner chose to defer; the override
  machinery itself is unit-tested (S5 OverrideRulesTest + S9a controller test) and unchanged. S12:
  surface the DetectOverrides toggle so this is verifiable at Gate 2.
  (Affects S11, S12, Gate 2.)

- D-042: S10 CONTEXT-OVERRIDE decisions (sanctioned by the S10 brief + extraction; flagged for S12/S14).
  (a) **Override = whole-profile swap, NOT scale/min/max.** The S10 brief's parenthetical "(scale/min/
  max/disable per spec)" predates the S2 correction; contexts_spec §4 (authoritative) + D-014 say
  `_ProfileManager LOAD_FILE` replaces the entire curve/threshold/anim/dynamic/dimming parameter set.
  Implemented as `mergeProfile(baseline, profile)` overlaying exactly task626's 39-key snapshot; fields
  OUTSIDE it (serviceEnabled, contextOverride, debugLevel, setupTitle, schemaVersion, and the
  snapshot-omitted thresholdDynamic) stay from the baseline so a profile can't disable the service.
  (b) **DataStore replaces Tasker's dual RAM/disk cache + prof8 daily reset.** `ContextRuleStore`
  (DataStore<ContextOverrideConfig>) is a single always-fresh source of truth, so contexts_spec §2's
  `%AAB_ContextCache`/`%AAB_ContextJSONCache` and prof8/task26's 03:00 reset (whose only effect was
  forcing a stale-RAM→disk reload) are obsolete — prof8 row marked dropped(cache obsolete). Solar
  times are recomputed fresh each eval (AndroidContextSignalSource), so the daily-reset failsafe has no
  surviving purpose. The cheap signal-token pre-filter (`%AAB_ContextCache` tokens) IS kept, as
  `ContextSignalTokens`, driving the PASS2 veto + app-poll gate.
  (c) **`%AAB_ProfileUser` baseline = the DataStore AabSettings; its NAME defaults "Default".** The
  rebuild has no stored user-profile-name field (D-038(ii)); no-match always reverts to the baseline
  settings regardless of name. userProfileName="Default" only feeds the resolver's fallback
  existence-check + the APP_CHANGED isNonDefault veto. S12 (profile save/load) should track the real
  active user-profile name + extend AppProfileCatalog with user-saved profiles (currently built-ins
  only — an unknown rule.profile name resolves null → engine keeps baseline, a safe degrade).
  (d) **Location is passive-only.** AndroidContextSignalSource uses LocationReader.lastKnownLocation
  (PASSIVE_PROVIDER); task630/631's persistent LocationListener + adaptive backoff/zombie-listener
  watchdog + the Variable-Set prof767 trigger machinery are NOT ported (prof766 deferred; prof765
  partial). LOCATION-caller eval still fires on the WIFI/battery/app signal edges and pipeline ticks.
  Deferred to S12/S14 if device testing shows passive last-known is too stale.
  (e) **Time scheduling approximated by pipeline-tick re-eval**, not prof764's exact self-scheduling
  Time context at `%AAB_NextContextTime`. nextContextTime IS computed (resolver) + exposed as a
  StateFlow; ContextEngine.onPipelineTick (TIME caller, 1s cooldown) re-evaluates each accepted cycle.
  Acceptable: time-window membership is checked on every cycle anyway; S12 may add an exact alarm.
  (f) **PASS1 cooldown lastEvalTime is null until the first eval** (not 0) so a freshly-started engine
  always evaluates once regardless of clock value (the 0-init would have blocked the seed eval when
  clock≈0; harmless in prod but a real edge). (Affects S12, S14.)

- D-043: S11 UI-SHELL decisions (sanctioned by the S11 brief; flagged for S12/S13/S14).
  (a) **Shizuku grant exec closed via a bound user service, NOT reflection (D-032 closed).** The
  owner reported `Shizuku.newProcess`-style reflection being fragile in Tasker-factory apps, so the
  documented user-service pattern was implemented: AIDL `IShizukuUserService` (`destroy()=16777114`,
  `grantWriteSecureSettings(pkg)`), `ShizukuUserService` runs `pm grant ...WRITE_SECURE_SETTINGS` in
  the Shizuku-spawned shell/root process (the same channel the adb instruction uses — WRITE_SECURE_
  SETTINGS is signature|privileged, not a runtime perm, so `grantRuntimePermission` does NOT apply),
  bound via `Shizuku.bindUserService`/`UserServiceArgs`. Enabled `buildFeatures.aidl=true` in
  `platform`. UNVERIFIABLE in this environment (no binder/device) — verify at Gate 2. The binder call
  runs off the callback thread; result surfaces to the onboarding UI via
  `PrivilegeManager.requestShizukuGrant(onResult: (ShizukuGrantGateway.Result)->Unit)` (signature
  changed from the S7 stub's `()`/`{refresh()}`).
  (b) **Live pipeline → UI via a process-wide `LiveRuntimeState` singleton.** The pipeline controller
  lives inside the service; the Dashboard (same process, different component) reads a republished
  `StateFlow<PipelineState>` + activeContext + serviceRunning. The service publishes from its single
  pipeline-collector coroutine (one writer, D-027 concurrency model intact) and `reset()`s on
  disable/destroy so the UI never shows stale "live" data. Not a binding to the service; a snapshot
  mirror. S12/S14: if a second observer of pipeline state is added, keep the single-writer rule.
  (c) **DataStore is the UI source of truth** (G1-F3 pattern carried forward): `DashboardViewModel`
  derives serviceEnabled from `settingsDataStore` (so the notification Disable propagates) and the
  tier from `PrivilegeManager.tierFlow()` (call `refreshTier()` on resume — the manager only updates
  on `refresh()`). `setEnabled` persists serviceEnabled BEFORE start/stop so boot/screen/maintenance
  receivers agree.
  (d) **Toy UI ripped out.** Deleted the Codex-era `SettingsState`/`SettingsViewModel` + the 3 toy
  screens (BrightnessSettings/SettingsGroup/Graph) and decoupled `SettingsStore`/`AabSettingsMapper`
  from `SettingsState` (removed `readSettings()/writeSettings()/toUiState()/fromUiState()`; the 4
  receiver/worker call sites now use `readRawSettings().serviceEnabled`). `LineGraph.kt` KEPT (S12
  may extend/replace it per its brief). The validated `AabSettings` is the single settings model.
  (e) **Screens are split stateless-`Content` + stateful wrapper** so `DashboardContent`/
  `OnboardingContent` render under a Robolectric compose smoke test (`UiShellTest`, 3 tests:
  Dashboard render+toggle, full `AppRoute`-table navigation, Onboarding steps+done). Added test deps
  `compose ui-test-junit4` (testImplementation) + `ui-test-manifest` (debugImplementation). S12/S13:
  follow this split for testability.
  (f) **D-027g RESOLVED.** `values/themes.xml` reduced to a minimal `@android:Theme.DeviceDefault.
  DayNight` no-actionbar parent (no Material XML parent, no colors) — Compose `TideoTheme`
  (dynamic color + DayNight) owns all in-app theming. The XML theme could NOT be removed entirely:
  the manifest `android:theme` + the pre-Compose launch window still require one.
  (g) **Onboarding flow = task563's 8 gates/order only** (D-024): notifications → WRITE_SETTINGS
  (BASIC, onResume `canWrite` re-check) → optional ELEVATED (skippable; adb-copy/Shizuku/root) →
  usage-access (rendered only when ≥1 context rule targets apps). No Tasker prefs (adbwp) read. The
  Done button reads "Skip for now" until BASIC is granted. First-run routing: NavGraph starts on
  Onboarding when tier==NONE. (Affects S12, S13, S14.)

- D-044: S12 SETTINGS/TOOLS SCREENS + CHART ENGINE decisions (sanctioned by the S12 brief; flagged
  for S13/S14/Gate 2).
  (a) **Step-0 triage method (D-027f).** Rather than rewrite 168 doc rows inline, an "S12 Step-0
  triage" section was APPENDED to `anonymous_handlers.md` committing every row to a bucket: (a)
  trivial scene-chrome (props·key back, _ExitButton/scene-nav, longclick help Flashes) and (b)
  settings-mutation (Variable Set / valueselected / _SaveButton* / toggle / reset-defaults) are
  bulk-dropped with one shared reason each (M3 nav + Scaffold back; Compose field state + debounced
  persist + SettingsValidator); (c) ~30 complex behaviors → an explicit per-row port table mapped to
  the owning screen. Chart-generation rows tagged `deferred-S13`. Committed before screen work.
  (b) **DataStore-as-truth, advisory validation (Tasker-faithful).** `SettingsViewModel` persists raw
  edits immediately (no clamp-on-keystroke); `SettingsValidator` (task583/707) reddens fields/banners
  but NEVER blocks the write — matching the Tasker scenes (the _RedInvalidFormulae rows are advisory).
  `validate()` clamping still applies where settings are CONSUMED (DashboardViewModel/runtime), not on
  the editor write path. Per-field range guards in the brief's scenes (task403/513/689/674…) are
  rendered as inline error banners; they do not prevent persistence.
  (c) **Curve-wizard override-point capture NOT wired.** `ToolsScreen`'s wizard runs
  `CurveSuggestionEngine.suggest` against the currently-recorded override points and applies via
  `applyToLiveCurve`, but the rebuild has no persistence that captures runtime override points into a
  UI-readable store yet (OverrideRules records them in pipeline state only). The runner therefore
  starts from an empty set → task38's <9-points error path → "need ≥ 9" message; apply is fully wired
  for when points exist. S13/S14: add override-point capture+persistence so the wizard has real input.
  (d) **Power-draw calibration = entry + chart slot only.** task524's on-device battery-current
  sampling / brightness-ramp measurement (minutes-long, untestable in this env) is NOT ported; the
  Tools screen has the entry + a PowerDrawChart `ChartPlaceholder`. Deferred to S13/Gate.
  (e) **Charts: engine + ONE template.** `ChartCanvas.kt` (generic) + `BrightnessCurveChart.kt`
  (template) are done; the other six charts are `ChartPlaceholder` host slots for S13 (hard-fence:
  S13 must not modify ChartCanvas). In-app debug LOG view (task634/635) deferred — only the 10-label
  selector (D-023) is wired. Unprivileged DC-like overlay dimming (task698/653/654, carried from
  D-040a) still NOT wired.
  (f) **Sun-source (location vs manual times) UI not added** on Dynamic Scale — `AabSettings` has no
  manual-lat/lon/time fields (the runtime computes solar times from passive location, D-042d). Add the
  fields + UI if Gate 2 shows passive location is insufficient. (Affects S13, S14, Gate 2.)

- D-045: S12 UI verdict + SALVAGE PLAN (owner, Gate 2). S12's screens are functionally wired and
  green but "miles off" the Tasker app: it built a GENERIC Material settings app, violating CLAUDE.md's
  prime directive ("port behaviour exactly; modernise the *how*, never the *what*"). The owner chose to
  **merge S12 anyway** (the domain engine, runtime pipeline, chart engine, validator, DataStore models
  and the stateless-Content/wrapper + Robolectric-compose patterns are all sound and worth keeping) and
  to **salvage the UI in S12.5** (split a = design language/app shell, b = preview→Apply interaction
  model + sliders + grouping, c = feature/behaviour fidelity). S12.5 is a UI-LAYER salvage — it must NOT
  touch domain/, runtime decision logic, golden vectors, or the ChartCanvas public API. The full defect
  list is Gate-2 findings G2-F1..F18 (this file). Owner's two binding answers: interaction model =
  **full temporary-preview → Apply with [committed] values + pipeline re-run**; screen layout = **keep
  the 9-screen screen_map but fix grouping (re-add a faithful Misc/General screen; min/max/offset/scale +
  animation belong there)**. (Affects S12.5a/b/c, S13, S14, Gate 2.)

- D-046: S12.5a DESIGN LANGUAGE + APP SHELL (UI-layer salvage; sanctioned by the S12.5a brief;
  addresses Gate-2 G2-F18). **Palette (teal + gold) is derived from the extraction, not invented:**
  primary teal `#007C63`, accent `#00A986`, bright link `#00C79E`, gold/strong `#FFC107`, bg `#333333`,
  card/surface `#383838`, decorative panel `#404040` — provenance per value in `ui/theme/Color.kt`
  (authority: `extraction/scenes/about.md` L51 "bg #333333, banner #007C63, accent #007C63/#00A986,
  links #00C79E, strong #FFC107, license box #383838"; reinforced by the `#FF007C63` "on" indicator
  dots in brightness/reactivity/superdimming settings scenes, the curve-wizard Flash overlays
  task038/task090, and the power-draw chart series #007C63/#FFC107). The chosen schemes:
  `AabDarkColorScheme` (dark-first = faithful: charcoal surfaces, teal primary, gold secondary) +
  derived `AabLightColorScheme`; both in `Theme.kt`. **Dynamic colour is now opt-in OFF** (was S11's
  default-on Material-You) so the brand identity is stable; DayNight kept.
  **App shell** = `ui/components/AppShell.kt`: `AabTopBar` (teal CenterAligned header, gold hamburger,
  title up top) + `AabNavDrawer` (`ModalDrawerSheet`) — the Compose rebuild of the **AAB Menu scene**
  (menu.md, XML L4462): gold-sun teal banner header + destinations grouped into the menu's three
  cards (Profiles&Contexts hero / Settings / Info&Help), current route highlighted; Recheck Permissions
  → Onboarding; Chart.js License entry dropped (screen_map). `DashboardScreen` rewritten so the drawer
  (opened from the hamburger) replaces S11/S12's flat `OutlinedButton` nav list, and **Profiles +
  Contexts are hero cards** (gold-iconed, clickable, Contexts shows the active context). **New dep:**
  `androidx.compose.material:material-icons-core` (resolved from the existing compose BOM, no explicit
  version) for the drawer/hero icons — declared in `libs.versions.toml` + `app/build.gradle.kts`.
  SCOPE BOUNDARY: kept the existing 9-route `AppRoute` set and all field behaviour intact — the
  Misc/General field regrouping + AppRoute changes are S12.5b's job (G2-F2), NOT done here. Other
  screens keep their own back-nav SettingsScaffold; the drawer is the Dashboard's hub. UiShellTest +2
  (drawer→every route via OnClick semantics action [NavigationDrawerItem's selectable tap doesn't
  register under Robolectric gesture injection — use performSemanticsAction]; hero-card navigation).
  (Affects S12.5b, S12.5c, S13 — S13 static screens inherit this theme/shell.)

- D-047: S12.5b INTERACTION MODEL + GROUPING + VALIDATION (UI/settings/runtime-control salvage;
  sanctioned by the S12.5b brief; addresses Gate-2 G2-F1/F2/F3/F4/F5/F6/F7/F10/F11/F13/F16).
  (a) **Temporary-preview → Apply (G2-F1).** New `state/DraftSettingsViewModel.kt` backs the 5
  parameter screens; each resolves its OWN instance via `viewModel()` so the draft is
  NavBackStackEntry-scoped (per-screen, not shared). `edit{}` mutates the draft only; `apply()`
  commits draft→DataStore (preserving the runtime/identity fields serviceEnabled/contextOverride) +
  forces a re-eval; `discard()`/leaving the screen reverts. `dirty` = draft≠committed. The init
  collector seeds the draft once from the first committed snapshot, then re-syncs ONLY
  serviceEnabled/contextOverride/schemaVersion/setupTitle on later emissions so `dirty` reflects only
  this screen's edits. KNOWN BOUNDED EDGE (flagged S12.5c): the draft is the whole `AabSettings`, so a
  context override that mutates curve fields WHILE a settings screen is open would, on Apply, write the
  seeded (pre-override) curve values back — only runtime/identity fields are re-synced mid-edit.
  (b) **Seed-once fields + brackets (G2-F1/F7).** `NumberSettingField` is re-seeded by an `epoch`
  counter (bumped on seed/discard), NOT by the incoming value, killing the "8.8→8.80.0" mid-edit
  corruption; an empty field is allowed (no forced 0). It shows the committed value in `[brackets]`
  when the draft differs (Tasker `_UpdateStaticSceneElements`).
  (c) **Bounded sliders (G2-F3/F13).** New `IntSliderSettingField`; the definitive **6 sliders** +
  ranges from the extraction (misc_settings.md elements4/6/20/22/23 + experiment_settings.md
  elements26): MinBright **0–75**, MaxBright **150–255**, AnimSteps **0–100**, MinWait **1–99**,
  MaxWait **2–100**, TaperMidpoint **130–240**. Everything else stays EditText.
  (d) **Misc/General regrouping (G2-F2).** New `AppRoute.Misc` (+ NavGraph + drawer Settings group +
  screen_map.md). The Misc scene's fields live there: min/max sliders, offset/scale text, anim
  sliders + derived throttle (+ min>max-wait warning), notifications toggle, the 10-label debug
  selector (moved OFF Tools). Curve & Brightness now = curve-zone coefficients + live form2A/3A + draft
  preview chart only; Animation & Dimming = super dimming + PWM only (animation moved out). This is a
  grouping correction WITHIN the owner-approved 20→consolidation (9→10 target screens).
  (e) **Forced re-eval on Apply / profile load (G2-F16).** `BrightnessPipelineController.reapply()`
  (reuses the `ContextChanged`→reapplyProfile→setInitialBrightness path, an UNLIMITED control event,
  never the drop-not-queue sensor mutex) ← `AmbientMonitoringService.ACTION_REAPPLY` ←
  `AutoBrightnessRuntime.reapply(context)`. `DraftSettingsViewModel.apply()` and
  `SettingsViewModel.applyProfile/resetDefaults/replaceAll` call it, **gated on serviceEnabled** (no
  spinning up a disabled service). The wizard-apply path still commits via SettingsViewModel.update
  (instant, no forced reapply — rare, a later tick applies); recorded, not a regression.
  (f) **Mutual exclusivity + gating (G2-F10/F11).** Enabling super dimming clears PWM-sensitive and
  vice-versa (superdimming_settings.md two-toggle pair). The dim-spread field is the CIRCADIAN
  dim-strength spread (task646 DimDynamic) → gated on `scalingEnabled` (∧ ELEVATED) + relabelled.
  (g) **Validation parity (G2-F4/F5/F6).** SettingsValidator gains zone2End<zone1End (the inverted
  range that NaN'd form3A — guarded + the derived readout renders "—" on NaN) and a
  dangerously-low-scale advisory (scale<0.5). `BrightnessCurveChart` floors the previewed curve at
  `minBrightness` so the Min slider moves the curve floor.
  HARD FENCE HONOURED: domain/, golden vectors, and the `ChartCanvas` public API untouched. Tests:
  `DraftSettingsViewModelTest` (real DataStore, Robolectric — edit/dirty/discard, apply commits,
  serviceEnabled preserved) + `SettingsScreensTest` rewritten (validator errors, `[bracket]`, slider
  ranges asserted via ProgressBarRangeInfo, Apply/Discard wiring, Misc debug label).
  (Affects S12.5c, S13, Gate 2.)

- D-048: S12.5c FEATURE & BEHAVIOUR FIDELITY (UI/app/platform-glue salvage; sanctioned by the S12.5c
  brief; addresses Gate-2 G2-F8/F9/F12/F14/F15/F17). HARD FENCE honoured: domain/, golden vectors and
  the `ChartCanvas` public API untouched.
  (a) **G2-F8 — profile load no longer disables override detection.** Root cause: `detectOverrides`
  (%AAB_DetectOverrides) was being overwritten by the loaded profile's value in BOTH profile-apply
  paths — `mergeProfile` (context swap) and `SettingsViewModel.applyProfile`/`replaceAll`. It is a
  GLOBAL reactivity preference, NOT one of task626's curve/min-max/threshold/dimming snapshot keys
  (contexts_spec §4 enumerates the snapshot; detectOverrides is absent), so it is now preserved from
  the baseline/current value in all three, exactly like serviceEnabled/contextOverride. Test:
  `ContextEngineTest.mergeProfile_preservesDetectOverrides_G2F8`.
  (b) **G2-F12 — toasts.** New `ui/components/Toaster.kt` (`rememberToaster()` → short Toast). Wired on
  action confirmations only (help text stays inline supportingText, matching Tasker): Apply (shared
  `DraftApplyBar` → all 5 draft screens), profile apply/reset/import-export (ProfilesScreen), context
  rule save/delete (ContextsScreen), wizard apply + copy-report (ToolsScreen).
  (c) **G2-F14 — context-rule editor.** Manifest gains a `<queries>` LAUNCHER `<intent>` so the
  Android 11+ package-visibility filter no longer empties the app picker. `AppEntry` carries an
  `ImageBitmap` icon (core-ktx `Drawable.toBitmap(96,96).asImageBitmap()`), rendered next to the label
  (sorted). `WifiInfoReader.currentSsid()` (one-shot read off ConnectivityManager active-network caps)
  backs a "Use current Wi-Fi" button. SUNRISE/SUNSET one-tap tokens fill the from/to fields (the
  resolver already accepts the tokens, D-014). When a rule targets ≥1 app and usage access is missing,
  an inline prompt + a deep-link to `ACTION_USAGE_ACCESS_SETTINGS` is shown (on select and on save).
  (d) **G2-F15 — runtime debug toasts.** New `runtime/RuntimeDebug.kt`: `DebugCategory` (the 9 non-Off
  %AAB_Debug categories, D-023, with `level` == the selector index) + `fun interface DebugSink` (lazy
  message, gated on the live `debugLevel`) + `NoOpDebugSink`; `runtime/ToastDebugSink.kt` posts a
  category-labelled Toast on the main looper when `category.level == activeLevel`. Wired through
  AppModule into the pipeline (LIGHT_EVAL/ANIMATION_DETAILS/DYNAMIC_SCALE/GRAPH_METRICS, plus a real
  SKIP_ANIMATIONS branch that writes the target directly), SuperDimmingCoordinator (SUPER_DIMMING) and
  ContextEngine (CONTEXT_AUTOMATION on a profile switch, CONTEXT_LOCATION on each eval). The selector is
  single-valued, so exactly one category is live at a time. Also: the `%AAB_Test` wizard diagnostics
  (R²/nRMSE/bias qualityLines) now copy to the clipboard via a "Copy report" button in Tools (D-025).
  Tests: `RuntimeDebugTest` (3, Robolectric ShadowToast — gating + NoOp + level-index alignment).
  NOTE: `SuperDimmingCoordinator`'s constructor was reordered to `(secureDimming, debugSink, tierProvider)`
  so the existing tests' trailing-lambda still binds `tierProvider`; AppModule uses named args.
  (e) **G2-F9 — super dimming.** No logic bug found: `SuperDimmingCoordinator.apply` correctly engages
  when `dimmingEnabled ∧ ELEVATED ∧ target < dimmingThreshold` (unit-tested S9b), and the S12.5b reapply
  now drives it when the user toggles dimming on in the dark (the S12-era no-re-eval root, G2-F16). Added
  a SUPER_DIMMING debug toast that reports engagement and the precise reason when it does NOT engage
  (disabled / not ELEVATED / above-threshold), so the device tester can localize F9. Documented in code +
  here that the AOSP secure keys (`reduce_bright_colors_activated`/`_level`) are correct for stock
  Android; if the toast logs "ON level N" but the screen does not visibly dim on a device, that is OEM
  secure-key/skin variance, not a logic defect — report the device at the gate.
  (f) **G2-F17 — QS tile.** `BrightnessTileService.renderTile` now sets the Tile `subtitle` (API 29+,
  minSdk 31) to Off / Active / Paused / Starting from `LiveRuntimeState.serviceRunning` + `pipeline.paused`
  (the service's single-writer republished snapshot, D-043b). Tile test stays instantiation-only
  (Robolectric can't bind a TileService).
  DEFERRED (recorded per the S12.5c brief's non-goals): curve-wizard override-point capture/persistence
  (D-044c) — the wizard still runs against an empty recorded set; S13/S14 should add runtime capture.
  (Affects S13, S14, Gate 2.)

- D-049: OVERRIDE-DETECTION FALSE POSITIVES ON RAPID LIGHT CHANGES (owner-reported 2026-06-14, G2R-F26).
  **DEFERRED to S12.6c — not yet fixed.** Symptom: a fast lux swing makes the pipeline pause as a "manual
  override" although nothing external wrote the brightness. Code-grounded analysis (S12.6a investigated,
  did not change source — HARD FENCE: this is `app/runtime` + `platform` glue, NOT domain/):

  Two override-detect paths exist, both suspect, and a Tasker settle step is MISSING:
  1. **Missing cycle-time settle wait + re-read in `BrightnessPipelineController.handleOverride`.** Its own
     comment says it "mirrors the prof755 gate after the cycle-time wait" (task567 act8), but it commits
     the pause IMMEDIATELY via `OverrideRules.shouldCommitPause` with **no delay and no second read**.
     Tasker task567 waits `%AAB_CycleTime` and RE-CHECKS that the brightness is still off-target before
     pausing — exactly the owner's "before the new brightness has taken hold". This is the strongest
     candidate: add the settle wait (≈ `cycleTimeMs`, fallback to throttle) + re-read; only pause if the
     observed value is still not our last self-write after settling.
  2. **Single-latest self-write marker is too weak under rapid multi-frame writes.**
     `ScreenBrightnessController.isSelfWrite` matches ONLY `lastSelfWriteDevice` (the most recent write).
     A fast animation writes f1..fN quickly; the system `ContentObserver` can coalesce/reorder/delay
     callbacks, and `read()` re-reads the CURRENT value. Between back-to-back cycles a delayed callback can
     observe a value that is no longer the latest marker → leaks to `OverrideMonitor` as external. Consider
     a short-lived set OR a "self-write generation + grace window" (suppress any change within ~N ms of our
     last write) instead of a single equality.
  3. **`autoRunning` gate hole.** `runCycle` clears `autoRunning` in its `finally` the instant `animate()`
     returns, but the final frame's `ContentObserver` callback can arrive a few ms later when
     `autoRunning=false`, so the gate then relies solely on the (weak, see #2) marker. A post-write settle
     window (#1/#2) closes this too.
  4. **OEM range round-trip drift in `AnimationRunner` read-back.** `controller.read()` is
     `toDomain(toDevice(x))`, which is NOT identity when `config_screenBrightnessSettingMaximum != 255`
     (e.g. deviceMax 100/1023/2047/4095), so `observed != lastWritten` can fire `OVERRIDDEN` on EVERY
     multi-frame animation on those devices — device-dependent, which may explain why it's intermittent.
     Compare in DEVICE space (raw values) or with a ±1 tolerance, not domain space.
  RECOMMENDED for the next model (S12.6c): start with #1 (the missing settle wait/re-read — most faithful
  to Tasker and matches the owner's intuition), then harden #2/#4. Add a regression test: a rapid
  from→to with an interleaved self-write sequence must NOT yield `OverrideDetected`; and a real external
  write after settling MUST. Keep it inside the single-pipeline-coroutine model (D-027). (Affects S12.6c.)

- D-050: PWM-SENSITIVE MODE DOES NOT LOCK THE HARDWARE BRIGHTNESS FLOOR (owner-reported 2026-06-14, G2R-F27).
  **DEFERRED to S12.6c — not yet fixed.** Symptom: with "Use software dimming (PWM-sensitive)" on, the
  hardware screen brightness is NOT held at the `%AAB_DimmingThreshold` floor when the calculated target
  drops below it (the floor is what keeps the panel above its low-brightness PWM-flicker band; further
  darkening is meant to come from a software/secure overlay, not from lowering hardware brightness).
  Code-grounded analysis (S12.6b investigated, did NOT change source — this is `app/runtime` glue, NOT
  domain/):
  **ROOT CAUSE — `pwmSensitive` is never read by the runtime.** `grep pwmSensitive` over `app/runtime`,
  `domain/`, `platform/` is EMPTY: the setting is persisted (`AabSettings.pwmSensitive`), has a UI toggle
  (SuperDimmingScreen) + legacy round-trip (TaskerLegacyProfileSerializer) + a profile default
  (DefaultProfiles "Night Reading"), but the pipeline never consults it. So the PWM branch of task661
  (act22-26) is unimplemented.
  **What Tasker does (task661 + task698):** task661 act22 — `if PWMSensitive == true AND
  calculated_brightness < DimmingThreshold` → it Performs task700 (software-dimming opacity → `%final_dim`)
  and task698 "Smooth DC-Like Brightness Transition", then **Stops** (act26) so it NEVER reaches the plain
  `Set Display Brightness = calculated_brightness` at act45. task698 step 3 (L36-80 of the extracted Java)
  is the floor: `hardwareTarget = (calculated_bright < dimmingThreshold) ? round(dimmingThreshold) :
  round(calculated_bright)` then writes `SCREEN_BRIGHTNESS = hardwareTarget` — i.e. the hardware brightness
  is CLAMPED UP to the threshold, and the remaining darkening is the overlay alpha (`dim_val`).
  **In the rebuild:** `BrightnessPipelineController.runCycle` writes `output.targetBrightness` (clamped only
  to `[MinBright, MaxBright]` by the engine) straight to hardware regardless of `pwmSensitive`; the floor is
  never applied. `SuperDimmingCoordinator` only drives the ELEVATED secure `reduce_bright_colors` overlay
  and also ignores `pwmSensitive`.
  **RECOMMENDED for S12.6c:** floor the *hardware* write at `dimmingThreshold` when
  `settings.pwmSensitive && target < settings.dimmingThreshold` (controller-level clamp; the overlay/secure
  layer supplies the rest), faithful to task698 step 3. This is pure controller/coordinator glue — keep the
  HARD FENCE on domain/ (task700's opacity math, if/when the software-overlay path is wired, is the
  golden-tested `SoftwareDimming`). Mind the OEM range normalization (compare/clamp consistently with
  `ScreenBrightnessController`'s device↔domain mapping, cf. D-049 #4). Note the broader unprivileged
  software-overlay path (task698 DC-like / 653/654 color filter) is STILL deferred (D-040) — but the
  hardware-floor clamp is independent of the overlay and should land regardless. Add a regression test: with
  `pwmSensitive=true` and a target below the threshold, the applied hardware brightness equals the
  threshold, not the raw (lower) target. (Affects S12.6c.)

- D-051: S12.6c PIPELINE BEHAVIOUR CORRECTNESS (UI/app/platform-glue; sanctioned by the S12.6c brief;
  HARD FENCE honoured — domain/, golden vectors, ChartCanvas public API untouched).
  (a) **G2R-F11/F12 stale-effective-settings → FIXED.** The pipeline's `settingsProvider` is
  `ContextEngine.effectiveSettings()`, which served the cached `_effective` snapshot from the last
  watcher eval; a manual Apply/profile-load edits the DataStore baseline but fires no context signal,
  so the runtime kept using stale settings (min-bright "stuck at 10"; Apply needed a light change).
  Fix: new `ContextEngine.reevaluate()` re-reads the FRESH baseline and re-merges the *currently
  resolved* active profile (it does NOT re-run the watcher resolution → cannot spuriously switch
  context, and handles the manual-context-lock case where the resolver would return a null target);
  `AmbientMonitoringService.ACTION_REAPPLY` now `reevaluate()`s BEFORE `controller.reapply()`. Min
  brightness already threaded correctly through the mapper→engine (`coerceIn(min,max)`), so F12 shared
  F11's root; a controller regression test asserts a high minBrightness floors the applied target.
  (b) **G2R-F13 override-point capture → WIRED (closes D-044c).** New `OverridePointStore`
  (`overridePointsDataStore`, newest-first, capped at `OverridePoints.MAX_POINTS`=50 = task561) +
  `OverridePointSink` (`fun interface`, NoOp default). `BrightnessPipelineController.handleOverride`
  persists the de-compressed point (`history.first()` from `OverrideRules.recordOverridePoint`).
  `AppModule` exposes the store + wires the sink. `SettingsViewModel.overridePoints` (Tools wizard)
  and `DraftSettingsViewModel.overridePoints` (curve overlay) surface it.
  (c) **G2R-F14 curve overlay → DONE.** `BrightnessCurveChart` gains `overridePoints: List<Offset>`
  (rendered as scatter dots via single-point `ChartSeries` — ChartCanvas's existing <2-points→dot
  path, NO ChartCanvas change) + `fittedCurve: BrightnessCurveConfig?`; `CurveBrightnessScreen`
  computes the fit (CurveSuggestionEngine.suggest→applyToLiveCurve) and passes it ONLY at ≥9 points
  (task38 threshold).
  (d) **G2R-F26/D-049 override false-positives → FIXED (#1 + #4).** `handleOverride` is now `suspend`
  and performs the task567 act8 settle: wait `%AAB_CycleTime` (fallback throttle), re-check the gate,
  RE-READ, and pause ONLY if the settled value is still ≠ our last applied brightness (a rapid swing
  that resolves to our own write no longer false-pauses). The AnimationRunner read-back is now
  DEVICE-EXACT via new `ScreenBrightnessController.isOnScreenSelfWrite()` (compares the raw on-screen
  device value to the last self-written device value), removing the OEM `toDomain(toDevice(x))`
  round-trip drift that fired spurious OVERRIDDEN on `deviceMax≠255` (D-049 #4). **DECISION:** the
  single-latest self-write marker (D-049 #2) is RETAINED as-is — D-034 deliberately chose single-latest
  (a multi-value token set had four enumerated defects) and `ScreenBrightnessControllerTest` enforces
  it; the settle re-check (#1) is the authoritative gate and the device-exact comparison (#4) removes
  the actual cause, so a recent-set was judged net-negative. Regression test: a transient settling back
  to our value does NOT pause; a value still external after the settle MUST.
  (e) **G2R-F27/D-050 PWM hardware floor → DONE.** `BrightnessPipelineController.applyPwmFloor` clamps
  the HARDWARE write up to `dimmingThreshold` when `settings.pwmSensitive && target < dimmingThreshold`
  (task698 step 3), applied in `runCycle` + `setInitialBrightness` (domain space; ScreenBrightnessController
  maps to device range). The unprivileged software overlay (task698 DC-like/653/654) stays deferred
  (D-040); the hardware floor is independent. Regression test asserts the applied value == threshold.
  (Affects S12.6d/e, S13, Gate 2.)

Append new entries as D-052, D-053, … with which segments they affect.

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
  (as the Tasker project allows). [decision: scope of overwrite — see S12.6 open questions]
- **G2R-F16** **Cannot load a legacy profile** from `Download/AAB/configs` — the app doesn't see that
  directory (only `Download/AAB` + my own folders), likely a scoped-storage / Tasker-ownership conflict.
- **G2R-F17** Settings screens have **no reset-to-defaults button**.
- **G2R-F18** **Invalid settings are appliable** (e.g. form3A negative). [decision: block vs Tasker's
  advisory model — see S12.6 open questions]

*Labels, tooltips, context editor & onboarding (→ S12.6e):*
- **G2R-F19** Some **labels don't match their meaning** (e.g. *delta factor*). Tasker's long-press shows an
  explanatory toast: *"Controls how much to smooth out sensor readings. Higher values react faster to
  small light changes, but may increase jitter. Lower values are more stable, but might feel sluggish."*
- **G2R-F20** The label mismatches lead to a suspicion that the **underlying behaviour/wiring is also
  wrong** — audit each parameter label → variable → runtime use against the extraction.
- **G2R-F21** The long-press explanations are **embedded in the scenes as long-press triggers** — port/fix
  them all (every scene's help longclicks).
- **G2R-F22** Context rule creation **still cannot get the Wi-Fi SSID** (`_GetWifiNoLocation V3`) nor live
  location.
- **G2R-F23** On rule creation, **usage access is greyed out**; the original permission onboarding had an
  instruction on how to fix it (surface that instruction/flow here).
- **G2R-F24** The setup/permissions screen should show **usage access as optional by default**.
- **G2R-F25** There is **no toast when a rule/profile is loaded**.

### Gate 3 (after S14) — pending

The human records on-device findings here; the next session triages them.
