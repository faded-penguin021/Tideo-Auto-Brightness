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

Status values: DONE · PARTIAL · BLOCKED (see failure protocol in CLAUDE.md).

## Current state

S1 + S2 + S3 DONE. Build is now GREEN: `./gradlew :app:assembleDebug :platform:test :app:lintDebug`
all pass. APK at `app/build/outputs/apk/debug/app-debug.apk` (28 MB). Gradle 8.14.3 wrapper
committed; version catalog at `gradle/libs.versions.toml`; :platform is now an Android library;
:data retired. One pre-existing domain test failure remains (`rapidLuxSpike_isSmoothedByTaskerFormula`
— engine parity bug per D-019, not a new regression). S4 is now unblocked.
S3.5 (owner-review errata) applied on top: extraction docs corrected per D-020…D-026 — read those
entries before trusting any pre-S3.5 reading of profile gates, debug levels, or action codes 590/105.
S3.6 (peer-review plan hardening) applied: RUNBOOK restructured per D-027 — S9 is now S9a+S9b,
runtime concurrency model is binding, S4/S8/S11/S12 briefs amended. No code changes.
S4 DONE: the Tasker reference oracle + golden vectors + parity harness exist. `:domain:test` is
GREEN with exactly 7 documented `@Ignore("S5: gap-NN")` (see `parity_gaps.md`, D-028). The engine's
divergences from Tasker are now CHARACTERIZED, not guessed — S5 closes them against immutable vectors.

## Next up

- S5 (Sonnet/high) — domain engine parity completion. Preconditions: S4 DONE ✅. Close gap-01…gap-07
  (see `parity_gaps.md`): the two systemic causes are R1 rounding-ties (`kotlin.math.round` →
  Java `Math.round`/BigDecimal) and R2 engine-added clamps/structure (D-010a/b). Remove all 7
  `@Ignore`s; do NOT edit `TaskerReference.kt` or the golden CSVs (immutable oracle).
- S6 ∥ S7 ∥ S8 (parallel window B) after S5. (S8 preconditions now formally include S2 — already DONE ✅.)
- Then S9a → S9b (split per D-027) → Gate 1.

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

Append new entries as D-030, D-031, … with which segments they affect.

## Blockers

(none)

## Gate findings

### Gate 1 (after S9) — pending
### Gate 2 (after S12) — pending
### Gate 3 (after S14) — pending

The human records on-device findings here; the next session triages them.
