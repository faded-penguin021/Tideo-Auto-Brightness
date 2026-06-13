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

| S8.5 review (Fable→Opus) | 2026-06-12/13 | Fable+Opus | IN PROGRESS | 3c6a585, cd3fd15, (this) | Sequential reviews (one agent at a time per owner). DONE: full acceptance suite green; S7 review → D-034 (suppress-echo redesign, OEM rounding, +8 tests); D-035 model policy (Opus from S9a); checklist unstale'd; **S4/S5 review → D-036** (2 CRITICAL parity holes fixed: task661 ScalingUse=false/%AAB_Scale branch + %AAB_ScaleDynamicCompress surfacing; new calculated.csv golden + 3 tests; existing 8 CSVs byte-identical); **S6 review → D-037** (port verified faithful; fixed oracle-circularity by adding independent SolarInvariantTest [7 astronomical invariants, all pass] + wizard abort test + dawn/dusk golden assertions). NOT DONE: S8 deep review (rerun, one agent). Resume at S8 before S9a. |

Status values: DONE · PARTIAL · BLOCKED (see failure protocol in CLAUDE.md).

## Current state

S1 through S8 DONE. Build is GREEN: `:platform:test` 19 tests; `:app:testDebugUnitTest` 20 new tests (+ pre-existing); `:app:assembleDebug` ✅ `:app:lintDebug` ✅.
Settings schema v2 complete: AabSettings gains animSteps/thresholdMidpoint/contextOverride/setupTitle; scale changed to Float; throttle default corrected to 1310. Mapper extended with all 4 domain config conversion functions. SettingsValidator implements all 5 Tasker validation rules (task583×3 advisory + task707×2 safety). DefaultProfiles has all 5 built-in profiles from task592. ContextOverrideRules data model is ready for S10 engine wiring. Parallel window B complete.

## Next up

- S9a: Runtime pipeline core (preconditions S5 ✅, S6 ✅, S7 ✅, S8 ✅)
- Then S9b → Gate 1.

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

Append new entries as D-038, D-039, … with which segments they affect.

## Blockers

(none)

## Gate findings

### Gate 1 (after S9) — pending
### Gate 2 (after S12) — pending
### Gate 3 (after S14) — pending

The human records on-device findings here; the next session triages them.
