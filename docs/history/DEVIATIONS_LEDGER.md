# DEVIATIONS & DISCOVERIES LEDGER — frozen rebuild record (D-001…)

> **Frozen historical archive.** This is the deviations/discoveries ledger from the
> Tasker→Kotlin migration (program complete, v1.0.0, Gate 3 signed off 2026-06-23). It is the
> highest-value "don't repeat these mistakes" reference for future changes. Cite entries as
> `DEVIATIONS_LEDGER D-0NN`. Code + golden vectors are ground truth; if an entry here conflicts
> with the current code, trust the code. Do not append new entries here — record new maintenance
> deviations in `docs/rebuild/STATE.md` (and promote durable ones back here only if curating).

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
  (d) **Location is passive-only.** ✅ RESOLVED S12.7c (D-056). AndroidContextSignalSource used
  LocationReader.lastKnownLocation (PASSIVE_PROVIDER); now `LocationReader.locationUpdates()` runs a
  persistent NETWORK+GPS `requestLocationUpdates` listener in the FGS scope, gated on the `[LOC]`
  ContextCache token (so it never runs without a location rule) with a ≥100 m debounce — Tasker's
  task630/631 "super smart location listener" equivalent. prof766/767 Variable-Set trigger machinery is
  still not ported verbatim, but the LOCATION caller now has real continuous fixes (not stale last-known).
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

- D-052: S12.6d BLOCK-APPLY ON CRITICAL VALIDATION ERRORS (owner-decision 2, G2R-F18). A SANCTIONED
  deviation from CLAUDE.md's "Tasker semantics win" rule: Tasker's task583 `_RedInvalidFormulae` is
  advisory-only (it reddens the field but still applies). The owner overrode this for the rebuild — the
  three form-coefficient errors (form2A<0, form3A<0, form2C>zone1End) now carry `Severity.CRITICAL` and
  DISABLE the draft-screen Apply button while present (with a hint). All other rules (the task707
  safety@1000lux warning, wait-order, low-scale, zone2End<zone1End) stay ADVISORY and only warn. This is
  the ONLY place Tasker's advisory model is overridden; do not generalise it. (Affects S12.6d/e, Gate 2.)
- D-053: S12.6e LABEL/HELP AUDIT + CONTEXT EDITOR + ONBOARDING (G2R-F19…F25, F28, F29). Findings:
  (a) the owner-flagged **"delta factor" was a LABEL/HELP bug, not a wiring bug** — `%AAB_DeltaFactor` IS
  the sensor-smoothing alpha factor in `BrightnessEngine` (`luxAlpha=1-exp(-deltaFactor·effectiveDelta)`);
  relabelled "Smoothing Δ" + verbatim help. A full field→`%AAB_*` cross-check vs `AabSettingsContract`
  found **no other binding bug**. (b) The 30 verbatim long-press help strings were decoded from the
  reactivity/brightness/misc/superdimming scene `longclick` tasks (Flash code 548, XML_RECIPES R2) and
  live in `TaskerHelp.kt` — the single source of truth; the Experiment-scene (Circadian screen) help
  tasks were NOT in this batch (carried gap). task510's `%AAB_DimmingEnabled` help text literally says
  "circadian scaling feature" — ported verbatim; the super-dimming wiring is correct (S12.5c G2-F9).
  (c) Wi-Fi SSID: the synchronous `getNetworkCapabilities` path is redacted to `<unknown ssid>` on API
  29+; `currentSsid()` is now `suspend`→`SsidResult` via a one-shot `NetworkCallback`
  (`FLAG_INCLUDE_LOCATION_INFO`, 2s timeout) — interface change rippled to ContextsViewModel/Screen.
  (d) `PipelineState` gained 5 timing fields populated from existing engine output (NO domain API change,
  fence honoured). (Affects S12.6e; Gate 2 re-test; S13 inherits the help-reveal pattern + the
  Experiment-scene help gap.)

- D-054: S12.7a MANUAL-OVERRIDE ENGINE — the REAL task567/task696 transcription (G2R-F34/F64/F46).
  **The override DELTA check is NOT in task567** (task567 `_DetectManualOverride`, XML L20525-L20885, is
  the *pause/notify/stop-other-tasks* handler: act0 gates `%caller1 = *Allow Override*|*Smooth Brightness
  Transition*`; acts 1-6 are code-137 **Stop** naming the SIX pipeline tasks to abort — "Process Sensor
  Event", "Evaluate Light Change V2", "Lux Smoothing", etc.; act7 Wait `%AAB_CycleTime`; act8 re-check
  Stop). **The actual detection lives in task696 "Smooth Brightness Transition V5 (Java)"** (java/task696_1,
  XML L35734-L35886): it computes a band `minTarget = from<to ? from : to-1`, `maxTarget = from<to ? to+1 :
  from` (L49-56) and flags an override only when `actual > maxTarget+2 || actual < minTarget-2` for
  **2 consecutive** reads (`overrideTriggerThreshold`, L126-134) — i.e. wrong-direction / overshoot-beyond-
  step. The mutex is **`%AutoBrightRunning`** (prof755 con1/c1 `=0` gate + task696 sets it `0` only at the
  very end, L160), so per-frame self-writes never fire prof755. Tasker checked every 5th frame **purely as
  an IPC optimization** (explicit comment L98-101) → the Kotlin port checks every frame (a *how*, the band+
  2-read debounce is the *what*). FIX: `AnimationRunner` now uses this band+debounce (replaces the old
  exact-match `isOnScreenSelfWrite()` that false-fired on OEM round-trip drift); `OverrideMonitor` gains a
  post-init **settle-suppression** gate and `BrightnessPipelineController.setInitialBrightness` arms a 1500ms
  window after each initial self-write (F64 — kills the start/reinit/resume/QS-on echo race where the
  AUTO→MANUAL mode-flip recompute looked external). The S12.6c handleOverride settle-wait (D-049/D-051d) is
  KEPT for the idle-observer path (complements, not conflicts). **F46 semantics:** a manual profile load IS
  the override (`%AAB_ContextOverride` latch, surfaced in the Menu via `LiveRuntimeState.manualOverride`,
  Resume clears it); a context *rule* being active is NOT an override and is no longer labelled one (Menu
  Contexts card: "Context active: X" / "Manual override active — Resume on Profiles" / "No active context").
  **Fence honoured: domain/ + golden vectors untouched** (band logic is app-layer; `OverrideRules` unchanged).
  (Affects S12.7a; S12.7b reuses the override→notification path; S12.7c/h depend on the F46 lock semantics.)

- D-056: **S12.7c context-system parity** (G2R-F42/F43/F44/F45/F47/F67/F68; UI/app/platform-glue only,
  domain/ + golden vectors fenced). **F45 smart location listener** (the headline): the old on-demand
  `LocationReader.lastKnownLocation(PASSIVE_PROVIDER)` read died on backgrounding and reported `loc 0.0,0.0`;
  replaced by `LocationReader.locationUpdates()` — a continuous `requestLocationUpdates` flow over NETWORK +
  GPS, seeded with the best non-null-island last-known fix, filtering exact `(0.0,0.0)` reads. Hosted in the
  FGS scope so it survives backgrounding. `ContextEngine.startLocationListenerIfNeeded()` collects it ONLY
  when a rule uses location (`tokens.usesLocation` = Tasker's `[LOC]`-in-`%AAB_ContextCache` cost gate —
  owner-confirmed mid-segment) and applies a **≥100 m haversine debounce** before firing the LOCATION eval
  (kills the near-constant, input-blocking context-location toasts). `ContextSignalSource.assemble()` now
  takes engine-fed `lat`/`lon` (new `LocationSignal`); `AndroidContextSignalSource` still falls back to
  last-known for the SOLAR computation when no fix yet. **F42** `currentLocation()` → typed `LocationResult`
  (NeedsPermission / Unavailable / Available) after a **call-time** permission recheck + a fresh
  `getCurrentLocation` fix (fixes the false "not granted" post-grant). **F43** `List<ContextRule>.byPriority()`
  (highest priority first, case-insensitive name tie-break) feeds `ContextsViewModel.rules`. **F44** loading
  a legacy config now `SettingsViewModel.saveImportedProfile`s it (under its file name) into `UserProfileStore`,
  which `AppProfileCatalog` (the rule editor's source) reads → selectable as a rule target with no manual
  re-save (extends D-042c). **F67** a 7-`FilterChip` day picker wired to `ContextTriggers.days` (all/none =
  every day); the resolver's overnight-wrap (post-midnight tail = previous day's membership) is unchanged and
  verified (domain fenced). **F68** `ContextsViewModel.solarTimes()` computes today's rise/set for the
  last-known location; SUNRISE/SUNSET tokens render "Sunrise (06:42)" in `AabGold`. **F47** the
  CONTEXT_AUTOMATION debug toast on each auto-load now reads "trigger … · context … · profile … · rule X
  (priority N)". Resolves the long-standing context deviation (d) "location is passive-only". Tests:
  ContextEngine +2, AppProfileCatalog +1, ContextRuleStore +1 (byPriority), SettingsScreens +2.
  (Affects S12.7c; S12.7d builds the Location-permission Setup step + SSID order on this listener; S12.7h's
  Circadian Date/Lat/Lon reuses `solarTimes()`/the location helper.)

- D-057: **S12.7d permissions / Wi-Fi acquisition / first-boot nav** (G2R-F33/F41/F57; UI/app/platform-glue
  only, domain/ + golden vectors fenced). **F41 SSID — the `_GetWifiNoLocation V3` port (headline):**
  `AndroidWifiInfoReader.currentSsid` now resolves the SSID without Location by trying the no-Location
  strategies in Tasker's order before the Location fallback. New `platform/context/WifiSsidStrategies.kt`:
  `WifiSsidStrategy` fun-interface + `ShizukuWifiSsidStrategy` (runs `cmd wifi status` and parses
  `Wifi is connected to "SSID"`) + `DumpsysWifiSsidStrategy` (in-process `dumpsys wifi`, parses the
  `mWifiInfo`/`COMPLETED` line) + pure parsers (`parseCmdWifiStatus`/`parseDumpsysWifi`/`normalizeSsid`).
  Shizuku execution added via new `platform/privilege/ShizukuShell.kt` (binds the existing user service and
  calls a new AIDL `exec(in String[])` method, implemented in `ShizukuUserService`); user-service version
  bumped to 2 for the shell bind. `currentSsid` iterates injectable `noLocationStrategies` (default Shizuku →
  dumpsys), then the renamed-private `locationCallbackSsid()` (the pre-S12.7d `NetworkCallback` path) is the
  LAST fallback. **F41 perm:** OnboardingScreen gained a Location step (`RequestMultiplePermissions`
  FINE+COARSE, optional). **F33:** `RestrictedSettingsCard` shown when `isLikelySideloaded(context)`
  (`getInstallSourceInfo().installingPackageName` not in {com.android.vending, com.google.android.feedback}),
  with an "Open App info" `ACTION_APPLICATION_DETAILS_SETTINGS` deep-link. **F57:** new
  `NavHostController.completeOnboarding()` → `AppRoute.Menu` with `popUpTo(Onboarding, inclusive)`;
  OnboardingScreen onDone uses it (was a direct Dashboard navigate). Tests: `WifiSsidStrategyTest` (9 — fake
  strategy source-order short-circuit/fallthrough/Location-fallback + cmd/dumpsys parser + normalize),
  UiShellTest +3 (Location step renders, restricted-settings hint + Open-App-info, completeOnboarding lands
  on Menu and drops Onboarding from the back stack). (Affects S12.7d; the SSID strategy + Location helper
  are reused by any future Wi-Fi/location work; S12.7e's permission-aware toasts can lean on the same gates.)

- D-058: **S12.7e debug / toast infrastructure** (G2R-F48/F49/F50/F51/F52; UI/app/platform-glue only,
  domain/ + golden vectors fenced). **F50/F51/F52 — new process-wide flash channel (headline):** new
  `app/runtime/AabFlash.kt` (object) is the single channel ALL runtime flashes go through. `show()` cancels
  the previous flash before posting the next (**F51** no-stack); `cancel()` clears immediately (**F52**
  instant-off). It uses an opt-in `Presenter` when registered (system-wide overlay) else falls back to the
  foreground AAB-teal `Toast` (teal styling moved here from `ToastDebugSink`). `ToastDebugSink` +
  `ToastContextLoadSink` now just format text + `AabFlash.show`. **F50 — global flashes:** new
  `app/runtime/AabToastAccessibilityService.kt` (opt-in `AccessibilityService`, presentation-only:
  `canRetrieveWindowContent=false`, no event handling) registers an `AabFlash.Presenter` that draws a
  `TYPE_ACCESSIBILITY_OVERLAY` window (no `SYSTEM_ALERT_WINDOW`) for ~2.5 s; manifest service +
  `res/xml/aab_accessibility_service.xml` + `global_toasts_a11y_description` string; Live Debug screen got
  an opt-in card (status + `ACTION_ACCESSIBILITY_SETTINGS` deep-link, re-polled on `ON_RESUME` via
  `LiveDebugViewModel.refreshGlobalToastStatus`/`isGlobalToastServiceEnabled`). Distribution is F-Droid/GitHub
  so the sensitive permission is sanctioned (owner answer). **F52 root cause:** `setDebugLevel` wrote the
  DataStore but the pipeline reads via `ContextEngine.effectiveSettings()` which serves a CACHED snapshot →
  the new category never took effect until a reapply. Fixed: `setDebugLevel` now `AabFlash.cancel()`s on Off
  and `AutoBrightnessRuntime.reapply`s (gated on serviceEnabled). **F48 — dynamic-scale timing:** new pure
  `DynamicScaleDebugGate` (in `RuntimeDebug.kt`): first flash ~2 min into a transition, then ≤ once/2 min,
  resets when the (time-driven) `scaleDynamic` settles; the controller computes `transitionActive` =
  `scaleDynamic` changed between cycles and gates the `DYNAMIC_SCALE` emit. **F49 — overlay-preview colour:**
  `SuperDimmingCoordinator.apply` emits `OVERLAY_PREVIEW` (level 6) with the black-overlay hex
  (`2.55·dimShell` → `#AA000000`, task653/654 via golden `SoftwareDimming.dimShell`) on the unprivileged
  (`tier < ELEVATED`) below-threshold fallback only. Tests: RuntimeDebug +4, SuperDimming +2, SettingsScreens
  +1 (147 app unit tests). (Affects S12.7e; `AabFlash` is the channel any future flash should use — S12.7f/h
  toasts and the deferred overlay window (D-040) can reuse it / the AccessibilityService.)

- **D-060 (S12.7h) — settings-display helper + Circadian preview overrides are app-layer, no domain/schema
  touch.** F38's full settings list reads through a NEW explicit per-key extractor (`SettingsDisplay.valueFor`,
  no reflection per owner caution) kept in lock-step with `AabSettingsContract`; runtime/identity keys
  (`serviceEnabled`, `contextOverride`) are excluded as not-profile-parameters. F39's fixed Date/Lat/Lon
  (`%AAB_Date`/`%AAB_Latitude`/`%AAB_Longitude`) live in a SEPARATE `experimentPrefsDataStore` (preview-only
  scene state) — deliberately NOT added to `AabSettings`, so profiles/export/migration are untouched and a
  fixed preview date can't leak into a saved profile. The consuming ExperimentChart stays an S13 host slot;
  this segment supplies the data + editor only. (Affects S12.7h; S13 reads `ExperimentPrefsStore` when it
  renders the circadian/experiment chart.)

- **D-061 (S12.7i) — F73 circadian scale was an app-layer wiring gap, NOT a domain bug; the domain fence
  held.** The dynamic-scale + solar math (`DynamicScaleEngine`, `SolarCalculator`, `buildScheduleWindows`)
  is correct and golden-tested; the pipeline simply never fed it real windows (`buildInput` used
  `TimeContext`'s 6–8am-UTC defaults — the never-completed D-039d "circadian wired in S9b/S12"). New
  `CircadianWindowProvider` *calls* the fenced domain to supply real sunrise windows; `now` stays **UTC
  seconds-of-day** because `buildScheduleWindows` derives windows as `riseEpochSec % 86400` (also UTC) —
  the two MUST share a frame (do not "convert now to local" without also rebuilding windows in local). The
  provider returns `null` with no location → the controller keeps the old default windows (graceful
  degrade). F39's `ExperimentPrefsStore` override feeds it (fixed date/loc preview). The morale: a
  symptom that looks like a domain/golden bug can be an app-layer feed bug — check the wiring before
  proposing to regenerate golden vectors. (Affects S12.7i; S13's circadian/experiment chart can reuse
  `CircadianWindowProvider.compute`.)

- **D-062 (S12.7i) — F70/F71/F72 cleanup; two of the three were misdiagnosed in the original report.**
  (1) **F70** ("legacy load doesn't apply") was NOT an apply-wiring gap — ProfilesScreen has called
  `vm.replaceAll(imported)` (commit + reapply) since S12.7c. The real bug: `TaskerLegacyProfileSerializer`
  only parsed `%AAB_Key=value` plaintext, but the on-device app saves **nested JSON** (task637
  `_ProfileManager` performSave, XML L29365+: `{meta,general,misc,reactivity,circadian,superdimming}` with
  snake_case keys). A real config therefore parsed to all-defaults. The serializer now sniffs a leading `{`
  and decodes the nested schema (key→field map ported from performLoad L29490+); the plaintext path is the
  fallback. **Derived form2A/form2D/form3A are deliberately NOT read** — performLoad recomputes them, as
  does `derivedCoefficients()` at read-time (ledger). Moral (again): "doesn't apply" can be a parse bug, not
  a commit bug — verify the parser produces non-default values before touching the apply path.
  (2) **F71** ("cooldown swallows overrides"): per task544 vs task567, `%AAB_Throttle` gates ONLY the
  prof760 main loop (`elapsed<min_interval`→`%AAB_MainLoop=0`→Stop); prof755→task567 override detection is a
  separate profile whose settle is task567 act7 "Wait **%AAB_CycleTime**". The runtime's lone conflation was
  `handleOverride`'s settle falling back to `throttleDefaultMs` when CycleTime was unset; now it uses
  `%AAB_CycleTime` only (0 ms when unset). No throttle gate ever touched `OverrideMonitor`/the override
  event path — architecture was already separate.
  (3) **F72**: a "Clear time" button in the context rule editor blanks From/To → `timeRange` saves null.
  **Fence honoured: domain/ + golden vectors + ChartCanvas untouched.** Affects S12.7i only.

- **D-064 (S12.8c) — settings/profiles schema hygiene; the "integer handling" was a whole class, not just Form1A.**
  (1) **F85**: `%AAB_ThreshDynamic` was a bogus editable Int in the schema. It is the computed task544
  reactivity-threshold output (task570 act31 only seeds it when unset). Removed `thresholdDynamic` everywhere
  (AabSettings field + contract rule, mapper `validate`, `SettingsDisplay.valueFor`, the ReactivityScreen
  editor field, `ContextEngine.mergeProfile`); **schema v2→v3** (`CURRENT_SCHEMA_VERSION=3`, migration bumps
  the stamp). A v2 JSON still carrying the key decodes fine — `ignoreUnknownKeys=true` was already set on the
  read path (verified by `AabSettingsMigrationTest`), so no field-drop migration code is needed. The engine
  never read the setting (it uses the runtime `PipelineState.threshDynamic`), so this is purely app-layer.
  (2) **F59** fell out of (1): the only user-visible literal "%AAB_ThreshDynamic" was that field's help text.
  (3) **F84**: `SettingsDisplay` friendly-label map (no reflection) + `EXCLUDED_KEYS` += global prefs +
  derived thresholdMidpoint.
  (4) **F70 — moral repeat of D-062: "doesn't apply" was a parse bug, not an apply bug.** The
  defaults-THEN-diffs model was already correct (serializer starts from `AabSettings()`; `replaceAll` fully
  replaces). The real defect: Tasker stores curve params as continuous doubles, so a decimal-encoded int hit
  `String.toIntOrNull()` → null → field kept its default ("Form1A didn't stick / was rounded"). This was a
  CLASS bug across every Int/Long field, not just Form1A — fixed uniformly: key=value path `asRoundedInt`/
  `asRoundedLong`, nested-JSON path already `intRound`/`longRound`, wizard-apply `Math.round` (was `.toInt()`
  truncation). The "misc inherited from previous" symptom was NOT reproducible in the parser/`replaceAll`
  path; added a no-inheritance regression to lock the contract.
  (5) **F62**: the wizard's ≥9 gate must count REAL points, not the engine's ghost-inflated total. Gated the
  Tools wizard on `MIN_FIT_POINTS` real points (shared with the Curve screen). Domain engine fenced — its
  internal ≥9-after-ghost-injection check is unchanged; the user-facing gate is now app-layer.
  **Fence honoured: domain/ + golden vectors + ChartCanvas untouched (called only).** Affects S12.8c only.

- **D-065 (S12.8d) — circadian time/location; F73's "DST bug" was a location-null fallback, NOT a frame bug.**
  Three findings (F39/F73/F83):
  (1) **F73 — the UTC frame already matched Tasker; do not "fix" the golden math.** task90 act0 sets
  `%AAB_NowSS = %TIMES % 86400` and act59 builds the windows from `%ss_* % 86400` — BOTH UTC-seconds-of-day.
  Further, `riseEpochSec` (= `startOfDay + localHour·3600`) is **tz-independent**: the `zoneOffset` cancels
  (`startOfDay` shifts earlier by the offset, `localHour` shifts later by it), so the window positions in UTC
  seconds-of-day do not depend on the tz at all (only `dayOfYear`). The rebuild's pipeline `now`
  (`currentTimeMillis/1000 % 86400`, UTC) and `CircadianWindowProvider`/`buildScheduleWindows` (`epoch % 86400`,
  UTC) therefore already tracked the real sun. The owner's "scale 1.025 @20:58 local" was the
  **location-null → `TimeContext` default-windows fallback** (default eveningStart=18:00 UTC = 20:00 local
  @UTC+2, so the evening ramp fired ~1h early) — `lastKnownLocation()` returns null on a device that hasn't
  actively used GPS. Fixes: read the tz offset at the **target date instant** (DST-aware; only matters for a
  fixed date in another season — F39) and, crucially, supply a reliable location (F83). The context-rule solar
  path (AndroidContextSignalSource) was "correct" only because it uses the live `locationUpdates` listener (a
  fix), not lastKnown.
  (2) **F39 — the fixed Date and Location are INDEPENDENT overrides, not preview-only.** `current()` used to
  require BOTH lat AND lon to honour the override, so a date-only override silently fell back to live → "Set
  fixed does nothing" for a date. Now date and location resolve separately (date-only / loc-only / both) and
  drive the live scaling. `ExperimentPrefsStore.set`/`CircadianExtrasViewModel.set` take nullable coords (null =
  live for that field); `CircadianDateLocationCard` "Set fixed" accepts blank coords.
  (3) **F83 — ported task90 act5–41 acquisition order:** skip when a fixed lat/lon is pinned → Android
  last-known → fresh fix → **ip-api.com** geo-IP fallback (`platform/.../context/GeoIpLocationClient.kt`),
  cached once a day and re-acquired on the day roll-over (the `%AAB_SunLastDate != %DATE` guard). Added INTERNET
  permission + `res/xml/network_security_config.xml` (cleartext scoped to `ip-api.com` ONLY; everything else
  stays HTTPS). **Deliberately NOT ported:** the WRITE_SECURE_SETTINGS `location_mode`-flip (act14/19/34, an
  ELEVATED-only "briefly enable location" optimization) — ip-api covers the no-fix case for every tier and the
  `location_mode` secure setting is deprecated/unreliable on minSdk 31; record-and-skip rather than ship a
  fragile privileged write. Provider decoupled from `ExperimentPrefsStore` (takes `overrideFlow:
  Flow<ExperimentDateLocation>`) and geo-IP injected as `suspend () -> LocationSnapshot?` for pure-JVM tests.
  **Fence honoured: domain/ + golden vectors + ChartCanvas untouched (`SolarCalculator` called only).** Affects
  S12.8d only.

- **D-066 (S12.8b) — final S12.8 UI polish; rebased LAST onto a+c+d.** Six findings, app/UI-layer only.
  (1) **F79 Dashboard redesign:** dropped Pause (master switch is the only on/off; `DashboardViewModel.pause()`
  removed); Resume shown only on `pausedByOverride`; `DashboardUiState` gained `pausedByOverride`/`circadianScale`/
  `dimmingStrength`/`throttleMs` (from `PipelineState`); `DashboardContent` rebuilt into status/override/light/
  brightness/context/health cards. (2) **F81 graph placement:** new reusable `ui/components/GraphScaffold.kt`
  — `ChartPager` (foundation `HorizontalPager` + dot indicator + title over `ChartSlot`s) renders the relevant
  graph(s) **above** the settings and swipes between related graphs; SuperDimming's chart moved up from the
  bottom; Reactivity = Reactivity+Alpha slots, Circadian = Experiment+Taper slots, SuperDimming = single
  Dimming slot. **No new dependency** (Pager is transitive via material3→foundation 1.7.6). **S13 coordination:**
  `ChartSlot.content` is the single swap point — S13 fills the real chart for the same title/testTag without
  touching the pager or the host screen. (3) **F82 grouping:** new `GraphSettingsGroup(graph)` outlined card
  ("Affects the {graph} graph") wraps each graph's controls. (4) **F68:** `TimeTokenRow` stacks the SUNRISE/
  SUNSET tokens vertically with `maxLines=1`/`softWrap=false` (no more char-wrap). (5) **F87:** app picker
  `heightIn(max=220→400.dp)`. (6) **F89 permissions audit:** declared `ACCESS_BACKGROUND_LOCATION` (FGS reads
  location while backgrounded); PACKAGE_USAGE_STATS kept (usage-access appop, already wired); DUMP NOT declared
  (signature-only, the dumpsys SSID path degrades). **Fence honoured: domain/ + golden vectors + ChartCanvas
  untouched.** Affects S12.8b only; **S13 inherits the `ChartSlot`/`ChartPager` contract** for the chart slots.

- **D-069 (S12.9) — engineering-quality & parity-debt remediation stage created (a–f); review verified
  against code.** A code-quality + owner-device review was triaged into the new S12.9 stage (RUNBOOK §S12.9).
  Items were verified against the current tree; the mis-diagnosed / already-closed ones are recorded so they
  are NOT redone:
  (1) **Rejected — AnimationRunner 5th-frame stride re-port.** `AnimationRunner.kt:27-29` already documents the
  every-5th-frame check as a Tasker IPC-cost *"how"* deliberately dropped per CLAUDE.md "modernize the how,
  never the what"; the band + 2-consecutive-read debounce is the behaviour. Re-adding the stride would make
  override-detection 5× less responsive — not done.
  (2) **Dropped — Haversine de-dup.** Only ONE haversine exists (`ContextOverrideResolver.distanceMeters`); no
  `ContextEngine` copy → no domain util added (hard fence preserved).
  (3) **Reframed — `Main.kt` "dead Tiramisu branch".** `SDK_INT < TIRAMISU` (API 33) is LIVE on minSdk-31
  (API 31/32 devices); it correctly guards `POST_NOTIFICATIONS`. Only the file rename (`Main.kt`→`MainActivity.kt`)
  is done; the branch stays. `DeadApiCheckTest` must catch only `≤ minSdk` checks, never `< TIRAMISU`.
  (4) **Downgraded — Shizuku "indefinite hang".** The `isShizukuAvailable()` preflight + `withTimeoutOrNull(BIND_TIMEOUT_MS)`
  already exist; no hang. Residual is only the installed-but-not-running distinction + ADB-always-offered (S12.9b, minor).
  (5) **Reframed — circadian-dimming fix is app-layer.** `SoftwareDimming.dimShell` already accepts `dimDynamic`;
  the fix (S12.9b, G2R-F90) wires it from `PipelineState.scaleDynamic` — no domain/golden change, hard fence intact.
  (6) **README is a 4-byte stub**, not absent → seed, not create (S12.9a). **CI already shipped** (`release-signing.yml`
  + `release.yml`) → record-only; S14 owns the `prerelease` flip + tag/versionName drift check.
  **Owner-binding rejections (one-line, do NOT re-audit):** the `AabToastAccessibilityService` teal overlay is
  intentional (keep); the `ip-api.com` geo-IP cleartext fallback stays (transparent, parity); `CurveSuggestionEngine.kt`
  stays monolithic (786 LOC); `// Tasker:` provenance comments stay at full density.
  **Debt the review missed, now folded in:** two extra leaked scopes (`BrightnessTileService:24`,
  `AmbientMonitoringService:44`) + three extra volatile clusters (`CircadianWindowProvider`/`ThrottleController`/
  `AabFlash`) → S12.9e; the no-error-state-UI gap on the profile-load path → S12.9c. Affects S12.9a–e.

- **D-070 (S12.9f → S13) — Profiles/Contexts IA merge split; S13 promoted to Opus/high with a Material 3 redesign.**
  Per owner: fold the two top-level Profiles + Contexts destinations into ONE screen with context rules edited
  in a modal (Tasker-style — a profile owns its rules; a rule targets a profile). The **plumbing** (single
  destination, rule-editor modal, shared VM state, preserved triggers + context-lock) lands in **S12.9f**
  (functional, minimally styled); the **visual polish** lands in **S13c**. S13 is promoted from Haiku/high
  "chart replication" to **Opus/high (a–d)**: S13a design-system foundation (`Dimens`/`Shape`/`Type` tokens +
  `docs/rebuild/design/m3_audit.md`), S13b component library (`SettingField`/`AabCard`/`EmptyState`/motion),
  S13c screen-by-screen restyle (incl. the merged screen), S13d the original charts + About/User-Guide.
  Guardrails: color scheme fixed (teal+gold), no new deps, `domain/`+golden+`ChartCanvas` fenced, strings via
  `stringResource`, behaviour-preserving. The actual design decisions are generated in S13a (this is scaffolding).
  DAG/serial-spine updated. Affects S12.9f + S13.
  **S12.9f landed (2026-06-20) — the plumbing half:** new `ProfilesContextsScreen.kt` hosts both `SettingsViewModel`
  + `ContextsViewModel` under one `SettingsScaffold("Profiles & Contexts")`: the saved-profiles surface
  (`ProfilesBody`, extracted from `ProfilesContent`) above a "Context rules" section (`ContextRulesSection`,
  extracted from `ContextsContent`). `AppRoute.Contexts` REMOVED; `AppRoute.Profiles` relabelled "Profiles &
  Contexts" (route id `profiles` kept for back-stack/test stability, owner→S12.9f); `heroDestinations` = `[Profiles]`
  so the Menu's two hero cards collapse to one (still surfacing live context/manual-override status, F46 semantics
  preserved); `NavGraph` drops the `contexts` composable. **Decision — full-screen `Dialog` over `ModalBottomSheet`
  for the per-rule editor:** both are sanctioned by the brief; the `Dialog` (`usePlatformDefaultWidth=false`,
  scrollable column, testTag `rule_editor_modal`) is reliable under Robolectric and lets every existing
  context-editor test pass UNCHANGED through the modal (a `ModalBottomSheet` had Robolectric-rendering risk). S13c
  may revisit the modal surface during the restyle. Every editor affordance preserved (app picker, use-current-SSID,
  SUNRISE/SUNSET tokens, use-current-location, usage-access prompt, day picker, clear-time, battery %). The legacy
  `ProfilesContent`/`ContextsContent` are kept as thin scaffold-wrappers over the shared bodies (their screen tests
  stay green); the standalone stateful `ProfilesScreen`/`ContextsScreen` composables were removed (wiring moved into
  `ProfilesContextsScreen`). No change to `ContextEngine`/`ContextOverrideResolver`/`ContextRuleStore` or any store;
  rule→profile relationship intact. domain/ + golden + ChartCanvas fenced. New tests:
  `SettingsScreensTest.profilesContextsMerge_*` + `UiShellTest.profilesContextsMerge_oneDestination_noContextsRoute`.
  **Owner-feedback follow-up (same segment):** the rule editor's Save/Cancel were at the very bottom (you had to
  scroll past the tall foreground-app picker to reach them) — pinned them to the TOP of the editor instead (the
  save logic hoisted into a local `saveRule()`; test tags `save_rule`/`cancel_rule` unchanged). S13c may make it a
  sticky bar. **Discovery (not mine, non-blocking):** `DraftSettingsViewModelTest.edit_marksDirty_thenDiscardReverts`
  is flaky under the full suite (DataStore timing) — passes in isolation/on re-run; flagged for a future stabilise.

- **D-071 (S12.9a) — repo hygiene & build integrity landed; two scoped deviations recorded.** All 8 S12.9a
  deliverables done with NO runtime behaviour change (see segment-log row). Deviations / discoveries, none
  blocking:
  (1) **Tasker XML — ignore route taken (owner still picks at PR review).** The remote has no `.gitattributes`/LFS,
  so the 1.6 MB XML was `git mv`'d into `docs/rebuild/extraction/_source/` then `git rm --cached` + gitignored
  (untracked). If the owner prefers LFS, add a `.gitattributes` `filter=lfs` line and `git add` it instead — the
  file is already at the final path.
  (2) **MonochromeLauncherIcon drawable lives in `drawable/`, not the brief's literal `mipmap-anydpi-v26/`.**
  A `<monochrome>` layer must reference a drawable; the conventional, correct home is
  `res/drawable/ic_launcher_monochrome.xml`. Separately, the `mipmap-anydpi-v26` folder was renamed to
  `mipmap-anydpi` to clear the v26 `ObsoleteSdkInt` (adaptive icons are universal at minSdk 31) — the brief
  didn't anticipate that folder-level ObsoleteSdkInt, so the literal path string was superseded.
  (3) **RUNBOOK `:data` mentions left intact.** The only `:data` references in RUNBOOK live inside the COMPLETED
  S3 brief (historical record of "`:data` retired" / the S3 retire-step). They are audit trail, not a
  forward-looking architecture reference; only CLAUDE.md's forward-looking module-layout bullet was removed.
  (4) **Two residual lint Warnings remain (do NOT fail the gate).** `DefaultLocale` and `InlinedApi`
  (`FOREGROUND_SERVICE_TYPE_SPECIAL_USE` requires API 34, inlined int is harmless on 31–33). Both post-date
  the frozen baseline and are outside S12.9a's listed fixes; `abortOnError=true` only aborts on Errors, so the
  ladder is green. A later polish/S14 pass can locale-qualify the `String.format` and add a rationale suppression
  for the specialUse constant if zero-warning lint is wanted.
  Affects S12.9a (closed); informs S14 (LFS decision, zero-warning lint).

- **D-072 (S12.9b) — circadian dimming wired (G2R-F90, closes D-040); override-flash + Shizuku-UX refined.**
  App-layer only; domain/ + golden `superdimming.csv` + ChartCanvas untouched. Investigation-first findings
  and design decisions:
  (1) **"Spread (Circadian)" = `%AAB_DimSpread`, default 100 (confirmed, not guessed).** `AabSettings.dimSpread`
  default 100; `circadian_dimming_graph.md` Y-axis `dim_val = 2 − (1 + (dimspread/100)·modifier)` with
  `modifier` = tanh day/night progress (0=night→1=day); task646 act6/act7 `clamped_strength = DimmingStrength ×
  %AAB_DimDynamic` only when `%AAB_ScalingUse`. `DynamicScaleEngine` already computes `dimDynamic` identically —
  the bug was purely the `null` hardcode in `SuperDimmingCoordinator`, not the math.
  (2) **Multiplier recovered from the published scale, not recomputed.** Rather than re-run task90 or store a
  new `dimDynamic` field, the fix passes the cycle's `output.scaleDynamic` into `apply` and inverts the shared
  modifier: `DimDynamic = 1 − (DimSpread/ScaleSpread)·(ScaleDynamic−1)` (BigDecimal HALF_UP scale-3, matching
  Tasker's stored `%AAB_DimDynamic`). This is EXACT and independent of the `ScaleSpread` value because
  `ScaleDynamic` already encodes `ScaleSpread·modifier`, and `ScaleSpread ∈ 1..100` (contract) ⇒ never a
  divide-by-zero (defensively guarded to `null` if 0). The only imprecision is the 3-decimal rounding already
  applied to the published `scaleDynamic` — sub-perceptual for a dim level. Chose this over a domain change
  (fence) and over reconstructing day/night state independently (would duplicate task90).
  (3) **Neutral default = 1.0.** `apply(..., scaleDynamic: Double = 1.0)` keeps the 8 existing 2-arg coordinator
  tests behaviourally identical (their settings have `scalingEnabled=false` ⇒ `null` ⇒ plain strength), so the
  golden-derived dimming paths are unchanged. Verified by a test asserting `DimSpread 0 (scaling on) ≡ scaling off`.
  (4) **Live readout kept consistent.** `BrightnessPipelineController.dimmingReadout` (the F58 `%AAB_DimmingDS`/
  `%AAB_DimmingCurrent` Super Dimming card) now uses the SAME `circadianDimMultiplier`, so the displayed values
  match the applied Extra Dim during daylight — this was beyond the literal deliverable but prevents a visible
  readout-vs-effect mismatch at the next device gate.
  (5) **Override flash routed through `AabFlash` (G2R-F91).** The manual-override toast (`AmbientMonitoringService`)
  was a bare `Toast`; now `AabFlash.show` (global a11y overlay → in-app pill → Toast fallback), consistent with
  the profile/context-load flashes. String marked `// TODO(S12.9d): strings.xml`. `android.widget.Toast` import
  dropped from the service.
  (6) **Shizuku availability is three-state.** Not a hang fix (the bind timeout already exists, D-069). New
  `ShizukuAvailability` + `ShizukuGrantGateway.isInstalled`/`availability` (PackageManager `moe.shizuku.privileged.api`);
  `PrivilegeManager.isShizukuAvailable()` REPLACED by `shizukuAvailability()` (only the onboarding screen used it).
  Onboarding: one-tap "Use Shizuku" only when RUNNING, a "start Shizuku" prompt when INSTALLED_NOT_RUNNING, and
  the ADB `pm grant` command ALWAYS offered (invariant asserted in tests).
  Affects S12.9b (closed); the override string is picked up by S12.9d's string extraction; the `dimSpread`
  −100..100 validator is S12.9c's deliverable #6 (the coordinator math already handles negatives).

- **D-073 (S12.9c) — schema ergonomics, validation & error handling landed; key decisions/deviations.**
  App-layer + docs only; domain/ + golden vectors + ChartCanvas untouched.
  (1) **AabSettings decomposed via computed group VIEWS, not a nested source-of-truth.** The brief said
  "AabSettings becomes their composite". A literal nested-constructor refactor would rewrite ~395 flat
  field accesses + `.copy(field=)` edit sites (258 reads + 137 copy-writes across screens/serializer/
  tests) — oversized and risky for a "medium" sub-segment. Instead the 7 records are `@Serializable` and
  exposed as computed `val bounds/curve/dimming/animation/thresholds/scaling/global` over the flat fields,
  so the flat v3 JSON stays the on-disk schema BYTE-FOR-BYTE (wire compat is more robustly preserved) and
  "flat JSON loads into the nested fields" holds via the projections (`NestedSchemaRoundTripTest`). This
  satisfies every acceptance bullet; a future segment can flip the source-of-truth if wanted.
  (2) **`mergeProfile` left behaviour-identical.** `GlobalPrefs` groups 7 app-wide prefs, but only 5
  (serviceEnabled/detectOverrides/debugLevel/contextOverride/setupTitle) are merge-preserved; the literal
  `copy(global = baseline.global)` was NOT used because `quickSettingsEnabled`/`notificationsEnabled` ARE
  in task626's per-profile snapshot (must come from the loaded profile). The explicit 6-field copy stays;
  the G2-F8 preserved set is unchanged (under test).
  (3) **dimSpread range corrected to −100..100 (latent runtime fix).** The clamp was `1..300`, so a
  negative spread (S12.9b's daylight-boost path) was clamped to 1 on every save and unreachable in
  production. Fixed the clamp + the contract range + a `SettingsValidator` advisory + boundary test.
  `LegacyImportRoundTripTest`'s nested-JSON fixture changed `spread 120→80` (120 now clamps to 100; 80
  keeps it a true parse assertion).
  (4) **Validation parity:** `validation_audit.md` enumerates every Tasker guard; 5 real-consequence gaps
  fixed (dimSpread, strength>65, threshold<minBright, taperMid>maxBright, minWait>maxWait), ~14 cosmetic
  reformat/clamp guards deferred to S14 (logged). The two pre-existing `⚠️` glyphs were stripped to honour
  the no-glyph non-goal.
  (5) **Profile-load errors:** `ProfileLoadResult` (Success/LegacyFallback/TotalFailure); a Tasker config
  or `%AAB_` dump = LegacyFallback (applied, jsonError logged), garbage = TotalFailure (error card). The
  legacy parser now throws `LegacyProfileParseException` on garbage but still returns defaults for a
  valid-but-empty config.
  (6) **DataStore versioning:** added `SCHEMA_VERSION=1` consts to the 3 typed-JSON models (not serialized;
  payloads still evolve additively via `ignoreUnknownKeys`); the 2 Preferences stores are schema-less
  (documented). `datastore_map.md` + `DataStoreSchemaVersionTest`.
  (7) **Dropped (D-069):** the Haversine de-dup — one impl exists; no domain util added (fence preserved).
  Affects S12.9c (closed); the new override string from S12.9b is still picked up by S12.9d; nested groups
  are available to S12.9e/f and S13 if they want cohesive sub-configs.

- **D-074 (post-S12.9c device finding) — app/location context rules created at runtime never fired.**
  Owner Gate-2 finding: creating a per-app context rule (e.g. "load Outdoors when Google Photos opens")
  did nothing, and the "Context Automation" debug flash never appeared. Root cause: `ContextEngine`
  started the foreground-app poll (`startAppPollIfNeeded`) and the location listener
  (`startLocationListenerIfNeeded`) ONLY at `start()` and `onScreenOn()`. The engine pulls rules
  (`rulesProvider`) but never *observed* the rule set, so a rule added while the service was already
  running never started its poller — the rule silently never triggered until the next screen-off/on
  cycle or a reboot (battery/wifi/time rules were unaffected because those collectors run continuously).
  No evaluation ran for the app change ⇒ no `CONTEXT_AUTOMATION`/`CONTEXT_LOCATION` debug flash either.
  **Fix (app-layer):** `ContextEngine` now takes a `rulesFlow: Flow<List<ContextRule>>` (wired to
  `ContextRuleStore.rulesFlow()` in `AppModule`) and, in `start()`, collects it to `refreshSignalListeners()`
  — (re)start the app/location listeners when a rule begins using that signal, cancel them when none do
  (cost gate preserved) — then re-resolve so a rule that already matches applies immediately. Default
  `emptyFlow()` keeps the existing static-rule tests valid. The usage-access requirement is unchanged
  but now also surfaced on the rules LIST (not just the editor): a saved per-app rule with no usage-access
  grant shows a "Per-app rules need usage access…" card with a Grant button (the rule cannot read the
  foreground app without it). Test: `ContextEngineTest.newAppRuleAtRuntime_startsForegroundPollAndApplies`.
  Domain/ + golden + ChartCanvas untouched. Full ladder green. Affects the context system (S10/S12.7c);
  no schema change.

- **D-075 (S12.9d) — i18n capability, runtime test backfill, and the LiveRuntimeState staleness gate.**
  App-layer only; domain/ + golden + ChartCanvas untouched.
  - **i18n is a capability, not translations.** `strings.xml` grew 2→47 entries covering the brief's
    priority surfaces (notification strings → screen titles → button labels → the override flash →
    Dashboard labels). The deferred backlog (section headers, per-field labels, long-press help — ~92
    inline `Text("…")` literals) is intentionally left for S14: extracting it now is high-churn, low-risk
    and would collide with S13's UI restyle. `HardcodedStringCheckTest` is a **ratchet** (heuristic), not a
    full audit: it counts the remaining inline `Text("…")` literals under `app/ui` and fails if the count
    rises above the ceiling (**92**), so a NEW hardcoded string is caught while the backlog is allowed.
    S14 lowers the ceiling as it extracts more. The brief's "values-es" is explicitly NOT added.
  - **Test backfill is evidence-based.** The brief's candidate list was surveyed against existing tests:
    `AmbientMonitoringServiceTest` + `ContextEngineTest` already exist (NOT duplicated). New dedicated
    tests added for the genuine gaps: `OverrideMonitor`, `AndroidContextSignalSource`,
    `AabToastAccessibilityService`, and `LiveRuntimeState`. **`MaintenanceWorker` was NOT backfilled** —
    a clean `CoroutineWorker` test needs `androidx.work:work-testing` (`TestListenableWorkerBuilder`),
    which is a new dependency the S12.9d brief does not sanction; the alternative (extracting `doWork`'s
    body into a free function) is structural churn better folded into a future segment. **`ScreenStateReceiver`
    was NOT backfilled here** — its detached `CoroutineScope(Dispatchers.Default)` makes a deterministic
    test flaky, and S12.9e explicitly reworks exactly that scope via `goAsync()`; it is far cleaner to test
    once that lands. Both deferrals are deliberate, not oversights.
  - **Staleness gate.** `PipelineState.lastPublishMs` is stamped inside `LiveRuntimeState.publish` (not by
    the pipeline), so a null stamp (never-published / post-reset) classifies as STALE and the UI never
    shows a dead loop as "live". `staleness()` is a polling Flow (1 s tick, injectable clock) so a wedged
    loop ages FRESH→AGING→STALE *without* a new publish. The Dashboard banner is gated on
    `stale && serviceRunning` (a stopped service is "Off", not "stale"). The teardown **watchdog** replaces
    `onDestroy`'s immediate `reset()` with a 5 s delayed reset that no-ops if a newer publish arrived —
    so a system-driven FGS restart does not flicker the live readout to "no data"; a genuine user stop
    (`tearDownDisabled`) still resets immediately. Affects the Dashboard + service lifecycle (S11/S9a/S12.6).

- **D-076 (S12.9e) — concurrency safety & controller decomposition.** Purely structural/safety; the
  runtime concurrency MODEL (single consumer, drop-on-busy, D-027) is unchanged. App-layer + one
  comment-only domain touch (the `BrightnessEngine.kt` rounding header); golden + ChartCanvas untouched.
  - **`AppProcessScope` + `goAsync()`.** New process-wide `SupervisorJob + Dispatchers.Default` scope
    with a logging `CoroutineExceptionHandler`. The owner-less ad-hoc `CoroutineScope(Dispatchers.*)`
    sites were converted: `AutoBrightnessRuntime` (bootstrap + 2 health-write launches) → `AppProcessScope`;
    `BootCompletedReceiver` + `ScreenStateReceiver` → a `BroadcastReceiver.goAsync { }` overload that holds
    the broadcast alive via `PendingResult` and `finish()`es it in a `finally` (the old detached launch
    could be killed mid-DataStore-read). The `PendingResult` is finished **defensively** (`?.finish()`):
    it is null only when a receiver is invoked directly in a unit test, never under a real dispatch.
  - **Two scopes retained-and-justified, not converted.** `AmbientMonitoringService` and
    `BrightnessTileService` keep their own `CoroutineScope(Dispatchers.Default + SupervisorJob())` —
    these are legitimately-owned lifecycle scopes (each `cancel()`s in `onDestroy`), NOT leaks, so per the
    brief's "convert OR justify" they are documented in place rather than routed through the (owner-less)
    `AppProcessScope`. So "zero ad-hoc scopes" = zero *leaking* ones; the two owned scopes are by design.
  - **`lateinit var controller` killed.** `AppModule` built the engine before the controller (the
    controller's `settingsProvider` reads through the engine), forcing a `lateinit` back-reference. New
    `fun interface ControllerHook` (implemented by `BrightnessPipelineController`) + `ControllerHookHolder`
    (`@Volatile var hook`, assigned post-construction); `ContextEngine(onProfileChanged = { holder.fire() })`.
    A fire **before** assignment is a safe no-op (the engine's first eval only seeds effective settings).
  - **`ContextEngine` volatiles → snapshot.** The 6 independent `@Volatile` signal fields (app/batt/plug/
    wifi/lat/lon) collapsed into one `MutableStateFlow<SignalSnapshot>` so an evaluation reads an
    untearable snapshot (no half-updated batt+plug or lat+lon). `screenOn` stays the **lone** `@Volatile`
    (single-writer-per-transition lifecycle flag, justified).
  - **Volatile-discipline audit.** `BrightnessPipelineController` 5→**2** `@Volatile`: `autoRunning` +
    `initializing` moved into `PipelineState` (so the override gate reads serviceOn/autoRunning/paused/
    initializing as one atomic compound — they flip once per cycle, not "many times" as the old comment
    claimed); the consumer-only `lastScaleDynamicSeen` dropped (it lives in `PipelineDebugEmitter` as a
    plain var); `cachedSettings` + `suppressOverrideUntilMs` survive with one-line rationales. The clusters
    the review missed — `CircadianWindowProvider` (5), `ThrottleController` (2, genuinely cross-coroutine:
    written from BOTH the sensor collector and the consumer), `AabFlash` (2) — each got a justifying
    rationale (visibility-only single-reference handoffs, no compound invariant; the one multi-step action
    in the provider is separately `AtomicBoolean`-guarded).
  - **Controller decomposed 596→4 files.** Orchestrator `BrightnessPipelineController.kt` (≤300 LOC:
    construction, start/stop, lifecycle entry points, the prof760 sensor gate, the event loop, the
    `ControllerHook`, state exposure) + `PipelineCycleRunner.kt` (the per-event math glue: runCycle / Set
    Initial Brightness / override settle / dimming readout / buildInput) + `PipelineDebugEmitter.kt` (the
    debug-flash surface + dynamic-scale timing gate) + `PanicHandler.kt` (the task528 device effect). The
    runner reaches the single `PipelineState` writer through an internal `PipelineRuntimeContext`
    implemented by the orchestrator, preserving the D-027 single-writer model. **Every `// Tasker:`
    provenance comment moved with its code.** `PipelineFileLayoutTest` guards re-bloat (orchestrator ≤300,
    per-file ceilings). The existing `BrightnessPipelineControllerTest` (16 cases) stays green UNCHANGED —
    the public API and behaviour are identical.
  - **`BrightnessEngine` rounding header (comment-only).** Consolidated the scattered `gap-0X` rounding
    justifications into one header documenting the three idioms (`Math.round`-ties-toward-+∞ / exact-binary
    `BigDecimal(double).setScale(HALF_UP)` / deliberate no-clamp). NO behaviour/signature/golden change;
    `ProvenanceCommentCountTest` freezes the `// Tasker` count at **13** so no provenance line is lost.
  - **Screen-state receiver split documented.** The manifest `ScreenStateReceiver` (cold start/stop of the
    whole service) and the service's internal `screenReceiver` (in-service reinit/hibernate) do different
    jobs; the two-layer split is intentional, there is no double-fire (the service unregisters in
    `onDestroy`; start/stop are idempotent), now documented in-code. Affects S9a/S9b/S10/S11/S12.
  - Mis-diagnosed/rejected review items (per D-069) were NOT redone. Affects S12.9f + S13 (they rebase onto
    the decomposed controller + the `AppProcessScope`/`ControllerHook` shapes).

- **D-077 (S13a) — design-system foundation landed; owner UI-overhaul feedback adopted onto the FIXED palette.**
  S13a delivered the token primitives (`ui/theme/Dimens.kt` spacing/sizing scale, `Shape.kt` `AabShapes`,
  `Type.kt` `AabTypography`) wired into `TideoTheme`, plus `docs/rebuild/design/m3_audit.md` (constraints,
  token tables, ColorScheme role-mapping for the frozen S12.5a teal+gold scheme, per-screen gap→target plan
  for all 9 screens + the merged Profiles/Contexts screen). **All token values == the literals they replace →
  behaviour-preserving** (typography/shapes equal the prior M3 defaults; `Dimens` referenced by `SettingsColumn`
  + `MenuScreen`). **Owner feedback (2026-06-20, forwarded "Global UI/UX Overhaul / Pro-Tool aesthetic" spec):
  ADOPTED into the plan, NOT applied ad-hoc** — codified as 5 S13b component blueprints in audit §2.5
  (`SectionHeader`+divider, `HeroNavCard` teal-edge+chevron, `NavRow`, **`KeyValueRow`** bold-gold data readout
  = the high-contrast "data-pop" the owner flags CRITICAL, `ActionButtonBar`) + structural rules (16dp no
  edge-bleed, teal single-line `TopAppBar`, group-settings-in-cards, 3-dot overflow for secondary actions).
  **One correction, recorded:** the spec's `Surface #1A1C1E` + new amber `Tertiary` recolor is **REJECTED** —
  "color scheme is fixed — NOT recoloring" is a binding S13 guardrail (D-070) and the palette is
  Tasker-provenanced (S12.5a). The spec's *intent* (high contrast, gold data values, grouped structure) is fully
  achievable within the fixed scheme: data accent = existing `secondary` `AabGold`; dark surface stays `#383838`.
  S13b builds the blueprinted components; S13c applies them and checks off m3_audit §3. (Affects S13b/S13c/S13d.)

- **D-078 (S13b) — component library built, behaviour-neutral; one delegation decision recorded.** The
  m3_audit §2.5 blueprints + §4 cross-cutting gaps are now reusable components (`components/AabCard.kt`,
  `AabNav.kt`, `AabMotion.kt`, `SettingField.kt`; `SectionHeader` enhanced in place). **Key decision:** the
  unified `SettingField`/`SettingFieldSpec` **delegates** to the existing S12.5b primitives
  (`NumberSettingField`/`IntSliderSettingField`/`SwitchSettingRow`) rather than re-implementing the
  draft-epoch / `[committed]`-bracket / long-press-help logic (G2-F7/F1) — so "fold the three fields into one"
  is satisfied at the *API* layer while the verified renderers (and their tests) are untouched. Likewise
  `SectionHeader` gained an **opt-in** `divider=false` param and `HeroNavCard` is a *new shared* component
  (MenuScreen keeps its private `MenuHeroCard`/`MenuNavRow` until S13c) — both so S13b changes nothing visible
  yet. `KeyValueRow`'s gold value uses the `secondary` *theme role*, not a raw `AabGold` literal (m3_audit
  §2.4). No new deps; `HardcodedStringCheckTest` ceiling 92 unchanged (components take caller-supplied i18n
  strings). **S13c consumes these:** migrate each screen onto `AabCard`/`SettingField`/`KeyValueRow`/
  `HeroNavCard`/`NavRow`/`ActionButtonBar`/`EmptyState`/motion and check off m3_audit §3. (Affects S13c/S13d.)

- **D-079 (S13c) — component library applied screen-by-screen; behaviour-preserving restyle + a brand logo.**
  The m3_audit §3 plan is executed across all 10 screens using the S13b blocks (`AabCard` section grouping,
  gold `KeyValueRow` for every derived/auto readout, `HeroNavCard`/`NavRow` on the Menu, `EmptyState` for the
  Profiles/Contexts empties, `SectionHeader(divider=true)`, and `AabMotion.screenEnter/Exit` on `AppNavGraph`).
  **Three decisions worth recording:** (1) the **tinted** semantic cards (Dashboard stale/override banners,
  Profiles load-error/context-lock, Contexts usage-access prompts) were **kept as `Card`** with their container
  colours — they encode *state*, not section grouping, so flattening them to `AabCard` would lose meaning; (2)
  **Live Debug stays on `DiagnosticCard`** (the glass-box surfaceVariant+teal-title+gold-value component) rather
  than a generic `AabCard` — it *is* the right component; (3) two row-10 targets are **deferred to S14** (3-dot
  overflow for a row's secondary actions; replacing the app-picker `heightIn(max=400.dp)`) because the existing
  screen tests locate `apply/overwrite/delete_*` directly — an overflow menu hides them until expanded — and the
  picker is inside a `verticalScroll`, where a `weight` child is illegal. `DerivedReadout` is no longer called
  (folded into `KeyValueRow`) but kept for any future use. **i18n stayed under the ceiling without effort:**
  moving empty hints into `EmptyState` and the Menu "Recheck Permissions" label off a bare `Text(…)` dropped the
  count 92→89. **Owner-requested logo:** the Tasker `_CreateLogo` raster (a sun whose rays contain a faded
  brightness slider) is rebuilt as an adaptive vector icon on the AAB brand — `ic_launcher_foreground.xml` (gold
  sun + rays + faded white slider + knob) + matching monochrome, with the launcher background recoloured generic
  indigo → AAB teal `#FF007C63`. `res/` is outside the S13 hard fence (which lists ChartCanvas/domain/golden/
  runtime/gradle/manifest/SettingsValidator), and the manifest's `@mipmap/ic_launcher` reference is untouched.
  (Affects S13d/S14.)
  **Logo follow-up (owner-driven):** the first two in-session sketches were rejected; the owner produced a
  proper design spec (Claude design) and chose **Direction C "Radial Dial"** — a gold sun whose 8 rays double
  as dial ticks, a translucent-white ring + gold fill arc forming an auto-brightness gauge, and a white knob at
  the level, over a vertical teal gradient (`#00A986`→`#007C63`). Implemented exactly per spec §02 by
  translating the SVG arc paths 1:1 to VectorDrawable `pathData`: `ic_launcher_foreground.xml` (ticks/disc/
  track/fill/knob), `ic_launcher_monochrome.xml` (one-ink simplification: uniform ticks + solid disc + full
  305° track at one weight + hollow-ring knob), and a NEW `drawable/ic_launcher_background.xml` (gradient
  vector, replacing the flat `@color`; `mipmap-anydpi/ic_launcher.xml` background now points at it). Build +
  lint green. One note for S14: the knob `c(84,54) r6` reaches ≈r36 from centre, a hair past the r33 safe
  circle — fine under the standard masks in the spec mock, nudge inward only if a device clips it.

- **D-080 (S13c follow-up) — fixed a latent flaky test surfaced by CI on the post-merge `main`.** The
  "Release Signing" workflow on the PR #60 merge commit went red on a single unrelated test,
  `DraftSettingsViewModelTest.edit_marksDirty_thenDiscardReverts` (it had passed locally + on the branch CI).
  **Root cause (test helper, not production):** `seededVm()` waited for `draft == committed()`, but when the
  committed baseline equals the `AabSettings()` defaults (here `minBrightness = 10`) that is true from VM
  construction — so the helper returned BEFORE the init collector's seed-once emission. The `idle()` after the
  test's edit then delivered that first emission with `seeded` still false, clobbering the edit (42→10).
  **Fix:** gate `seededVm()` on the VM's `epoch` bumping 0→1 (its real "seeded" signal) instead of value
  equality. Test-only; the production seed-once behaviour is correct and untouched. Verified deterministic
  (5× `--rerun-tasks` + full `:app:testDebugUnitTest`). (Affects S13c PR / `main`.)

- **D-081 (S13c') — the polish pass: typeset the app as a precision instrument.** Owner-supplied design
  spec (HTML, not in RUNBOOK) run as a visual-elevation sibling of S13d. Binding guardrails honoured:
  palette FROZEN (no `Color.kt` hex touched), type/layout/depth/motion only, hard fence intact
  (`ChartCanvas`/`domain`/golden/`runtime`/gradle/manifest/`SettingsValidator` untouched), every
  test-pinned `testTag` preserved. **Key decisions:**
  - **Fonts are bundled, not gradle deps.** IBM Plex Sans (4 weights) + Plex Mono (2) downloaded from the
    upstream `IBM/plex` repo as SIL-OFL `.ttf` into `res/font/` (Android-legal lowercase names); licence in
    `docs/licenses/IBM-Plex-OFL.txt` (moved OUT of `res/font` — aapt rejects non-font files there). `res/`
    is outside the S13 fence. This realises the spec's preferred path (over the `FontFamily.Monospace`
    fallback) — Plex Mono's true tabular figures stop the live readouts jittering.
  - **Two new type roles, not M3 slots.** `AabDataDisplay` (tabular mono 26sp) + `AabDataCaption`
    (tracked-uppercase mono) are top-level `TextStyle`s layered on `AabTypography` so the data-pop lands at
    every call site (KeyValueRow, DiagnosticCard, the Dashboard hero) at once. `titleLarge`→SemiBold.
  - **Hairline depth, not glow.** The surface ladder uses a `Color.White.copy(alpha=0.05f)` compositing
    highlight (a neutral edge, NOT a brand recolour) + honest ElevatedCard shadow; `Hero` adds a teal
    accent edge; `Well` recesses to `surfaceVariant`. New `Dimens` tokens `cardElevationHero`/`accentEdge`.
  - **KeyValueRow uppercases its key** into the tracked caption → 3 layout/casing test assertions updated
    (`ComponentLibraryTest` `CURRENT LUX`; `SettingsScreensTest` `ZONE 2/3 ALIGNMENT`) — sanctioned by the
    spec ("adjust only tests that assert layout/overflow"). Value `Crossfade` is inert under tests (value
    never changes mid-test) so no flakiness.
  - **Dashboard hero** = new `components/BrightnessInstrument.kt`; its teal 0–255 track is a plain Compose
    `Canvas` in a NEW file (the `ChartCanvas` fence is about the chart component, not all canvases). Status
    pill + switch consolidated into the hero; the old per-metric cards became a readout strip — all live
    fields already existed on `DashboardUiState` (composition, not new data).
  - **Profiles overflow cleared the S13c deferral.** Overwrite/Delete now live behind a per-row 3-dot
    `DropdownMenu` (Apply stays the primary visible action); the one test that found `overwrite_profile_*`
    directly now opens the menu first. The app-picker `heightIn(max=400.dp)` deferral STANDS (still an
    illegal `weight` child of a `verticalScroll`). The Profiles/Rules `SegmentedButton` split is screen-level
    only, so the direct-render `ProfilesBody`/`ContextRulesSection` tests are unaffected.
  - i18n: all new copy via `stringResource`; the `HardcodedStringCheckTest` count held at 89 (ceiling 92,
    multi-line `Text(\n "…")` and `stringResource` both escape the line-based regex).

  Full ladder green (`:app:assembleDebug :app:testDebugUnitTest :app:lintDebug :domain:test :platform:test`).
  (Affects S13d/S14 — they inherit the type roles + `AabCard` variants; S13d charts can sit in `Hero`/`Well`.)

- **D-082 (S13d) — static content & charts; the original S13 scope, plus owner UI feedback fixed in-pass.**
  Six charts (`ui/graph/{Reactivity,Dimming,Circadian,Experiment,Taper,PowerDraw}Chart.kt`) built strictly on
  the frozen `ChartCanvas` per the `BrightnessCurveChart` template — each samples a **golden-tested domain
  function** (`BrightnessEngine.dynamicThreshold`/`compressedDynamicScale`, `SoftwareDimming.dimProgress`,
  `DynamicScaleEngine` over `SolarCalculator` windows) and the math lives in the chart file, never in
  `ChartCanvas`. **Key decisions:**
  - **Chart↔slot mapping** (the S12.8b' rename reshuffle resolved by testTag, not by the brief's file names):
    `reactivity_chart`→ReactivityChart, `alpha_chart`→AlphaResponseChart, `dimming_chart`→DimmingChart,
    `circadian_dimming_chart`→CircadianDimmingChart (= scene `CircadianChart`), `dynamic_scale_chart`→
    CircadianScaleChart (= scene `ExperimentChart`, the day-scaling-over-time curve), `taper_chart`→TaperChart,
    `power_draw_chart`→PowerDrawChart.
  - **Time-of-day charts use local-frame windows** (`SolarCalculator.compute` with the device tz offset) so the
    x-axis reads as local hours — correct for a *preview* (distinct from the runtime's UTC frame, D-061/D-065,
    which is unchanged). A representative lat/lon (51.5/0.0) is the fallback when no fix is available; Circadian
    passes the F39 fixed/live coords.
  - **PowerDrawChart renders an `EmptyState`** until on-device calibration (task524) produces samples — the
    measurement pipeline is still deferred (D-044/Gate). The chart component is real and wired.
  - **Placeholders deleted, not repurposed.** `PlaceholderScreen.kt`/`ChartPlaceholder.kt` removed; the 2 tests
    that used them as generic stand-ins now use inline `Text`/`EmptyState`.
  - **G2R-F80** `completeOnboarding()` lands on `UserGuide` with `Menu` seeded beneath (Back→Menu), shown once
    post-onboarding (after first run the app starts on Menu, tier!=NONE). `AppRoute.UserGuide` added; About
    relabelled "About".
  - **i18n:** About/Guide long-form copy + new dashboard strings all via `stringResource` (strings.xml
    +~40 entries); no new `Text("…")` literals → `HardcodedStringCheckTest` ceiling 92 held.
  - **Owner UI feedback (during S13d), all app/`res`-layer, fence intact:** (a) Circadian fields `helper=`→
    `help=` so they carry the "ⓘ" reveal like every other settings screen (it never had it — made consistent);
    (b) Menu banner wordmark on ONE line ("Tideo" white + "Auto Brightness" gold); (c) `AabTopBar` de-teal'd
    (default M3 surface) so the teal banner is Menu-only (Dashboard/Live Debug had a second teal header);
    (d) Dashboard status pill uses compact labels + `softWrap=false` (was wrapping next to the caption);
    (e) Dashboard shows the active **Profile + Context** (always visible) via a new in-memory
    `LiveRuntimeState.activeProfile` set by `SettingsViewModel.applyProfile` (manual) and `ContextEngine`
    (rule load) — distinct from `activeContext`.
  - **`/dist/` debug APK** built at the owner's request (debug `app-debug.apk` + a short README); the owner
    deletes `/dist/` before merging, so it is gitignored — not part of the source deliverable.

  Full ladder green (`:app:assembleDebug :app:lintDebug :app:testDebugUnitTest`=283 `:domain:test :platform:test`).
  (Affects S14 — the UI surface is now complete; S14 owns copy review, translations, screenshots, Gate 3.)

- **D-083 (S13d' — owner chart-fidelity review; the ChartCanvas fence was LIFTED with explicit owner
  sanction).** The owner reviewed the S13d build on-device and asked for Tasker-fidelity chart fixes; several
  needed `ChartCanvas` capabilities the S13 hard fence forbade. The owner then said **"You are free to break
  the fence if it is a blocker"** → I extended `ChartCanvas` **additively** (no behaviour change to existing
  callers): `ChartSeries.onSecondaryAxis`, `secondaryYRange`/`secondaryYAxisLabel` (dual y-axis + right ticks/
  title), `xTickFormatter` (custom x-tick labels), and rendering `ChartMarker.label` (rotated text by the line).
  **Decisions:**
  - **Scrub vs swipe (owner decided scrub wins):** the `ChartPager` swipe and the chart's interactive
    drag-scrub both want horizontal drags → conflict. First attempt set paged charts `interactive=false`
    (swipe wins); owner reversed it ("charts have to be interactive"). Final: ALL charts keep
    `interactive=true`, the pager's `userScrollEnabled=false`, and page navigation is by **tap** — ‹ › arrows
    flanking the title (`chart_pager_prev`/`chart_pager_next`, wrap-around) + clickable page dots
    (`chart_pager_dot_*`). **Scrub tooltip rounding FIXED:** `ChartCanvas.formatReadout` now shows ~3
    significant figures by magnitude with trailing zeros trimmed (1.15 reads "1.15", 5.8 "5.8", 35 "35"),
    and the tooltip's x line reuses `xTickFormatter` so the time charts read HH:MM; the scrub dot now maps
    secondary-axis series correctly.
  - **Circadian frame = UTC** (was local). Tasker's circadian graphs are UTC (`%TIMES%86400`), as is the
    runtime (D-061/D-065); the preview now matches, x-axis titled "Time of day (UTC)", **HH:MM** ticks via
    `xTickFormatter`, five labelled sun-event lines (Dawn/Sunrise/Noon/Sunset/Dusk).
  - **Gold = reference, always** (Tasker convention, user_guide §8): DimmingChart reference→gold; TaperChart
    night→blue (it has no reference series, so gold would mislead).
  - **Dimming dual y-axis:** dim-% + gold reference LEFT (0–100), dim-shell RIGHT (0–strength) — the real
    Tasker two-axis layout, now possible post-fence-lift.
  - **About copy:** João Dias reworded to clearly credit *Tasker* (not AAB); license year 2025→2026.
  - **User Guide → WebView/HTML:** Tasker styled it as an HTML manual; rebuilt as an AAB-themed static HTML
    doc in a no-JS/no-network `WebView` (SDK WebView, no new dep). Copy stays in `strings.xml` (assembled into
    HTML at render) so i18n is preserved; only the markup/CSS is local. About stays M3-card (short, fine).
  - **`HardcodedStringCheckTest`** unaffected (WebView HTML + `stringResource` escape the `Text("…")` regex).

  Full ladder green (`:app:assembleDebug :app:lintDebug :app:testDebugUnitTest`=283 `:domain:test
  :platform:test`). (Affects S14 — `ChartCanvas` now has dual-axis/custom-tick/marker-label capability the
  owner sanctioned; treat as part of the chart engine going forward.)

- **D-084 (S13d — circadian date wiring + "Now" line; owner bug report).** The Circadian/Circadian-Dimming
  charts ignored the F39 fixed **date** — the screen passed lat/lon to the chart but not the date, so the
  curve always plotted today. **Fixes:** (1) `CircadianScreen` resolves `chartDateEpochSec(dateLocation.date)`
  (UTC-midnight parse, else today) and passes it to `CircadianScaleChart`; the Super Dimming screen now reads
  the same `ExperimentPrefsStore` override (via `CircadianExtrasViewModel`) and feeds date/lat/lon to
  `CircadianDimmingChart`. (2) **Runtime, not just the chart:** the fixed date/location drive the LIVE scaling
  (CircadianWindowProvider already observes the override), but nothing forced a recompute — so
  `CircadianExtrasViewModel.set()`/`useLiveData()` now call `AutoBrightnessRuntime.reapply` (gated on
  serviceEnabled), so the new `%AAB_ScaleDynamic` lands immediately instead of waiting for the next light
  change (prof760 drops steady-light cycles). (3) **"Now" event line** added to both circadian charts
  (Tasker `now_utc`) — a red vertical marker at the current UTC time-of-day, alongside the five labelled sun
  events. Full ladder green. (Affects S14.)

- **D-085 (S13d — owner review batch 3).** (1) **Import placement:** the single-file "Import a settings
  file" picker moved from a top-level button into the collapsed **"Import from Tasker AAB"** expandable on
  Profiles & Contexts (it loads Tideo exports AND legacy Tasker configs), grouped with the folder-link
  import; the standalone section is now just "Export". (2) **User Guide design language:** the WebView HTML
  now mirrors Tasker's guide styling — a teal banner, teal-accent section rules, **gold** `**emphasis**`,
  an intro/outro **blockquote**, and dedicated **tip** (teal) and **warning** (red, no glyph per policy)
  callout boxes. Content markup lives in the i18n strings (`**bold**`, `[TIP]`, `[WARN]`); the renderer
  converts it. (3) **scaleSpread safety bug:** `DraftSettingsViewModel.apply()` commits the raw draft
  WITHOUT the mapper's `validate()` clamp, so a negative circadian **Scale spread** persisted and reached
  the engine/chart via `toDynamicScalingConfig()` → ScaleDynamic could flip ≤0 (black screen). Fixed where
  consumed: `toDynamicScalingConfig` clamps `spreadPercent` to 1..100 (covers runtime AND the chart, which
  share the mapper), and the Circadian "Scale spread" field clamps input to 1..100 on edit. The
  super-dimming circadian `dimSpread` stays signed (-100..100, D-072) — only the scaling spread is forced
  positive. (Broader note: Apply not running `validate()` means other fields could also persist
  out-of-range; flagged for S14 — a general clamp-on-Apply or per-field bounds pass.) Full ladder green.
  (Affects S14.)

- **D-086 (S13e — final pre-S14 owner punch-list).** Four owner items + one retraction. (1) **CI Node-20
  deprecation:** GitHub deprecated the node20 actions runtime (node24 default from June 2026). Bumped the two
  actions that HAVE a node24 major — `actions/checkout@v5`, `actions/setup-java@v5` — in both workflows; the
  rest (cache/upload-artifact v4, action-gh-release v2, setup-android v3) have no node24 major yet and are
  auto-force-upgraded by the runner, so nothing to change. (2) **"Manage profiles & Export" → one collapsible
  toggle** on Profiles & Contexts (declutter): the management actions + Export now sit under a collapsed
  `ExpandableSection` (`manage_section`), leaving the saved-profiles list as the default surface. (3)
  **Transition factor did nothing (REAL bug):** the circadian chart helper `circadianCurve` hard-coded
  `scaleTransitionFactor=0.1`; the field is now threaded through to both circadian charts (the runtime was
  always correct). (4) **Home-screen widget + Dashboard quick actions:** a battery-efficient RemoteViews
  widget (Brightness/lux/profile/context + toggle + reset) pushed event-driven from the FGS publish path
  (`updatePeriodMillis=0`, no-widget fast-path skips even the DataStore read); Dashboard `QuickActionsCard`
  adds Reset-to-auto (= reapply, owner decision) + Add-QS-tile (`requestAddTileService`, API-33) + Add-widget
  (`requestPinAppWidget`, gated on supported ∧ no instance). **(retraction) The earlier "changing the date
  doesn't affect the circadian graphs" report was withdrawn by the owner as a mistake on their end**; an
  independent probe confirmed the date path is correct end-to-end (solar windows shift hours by date; the
  `ExperimentPrefsStore` persists it; the `ChartPager`/`HorizontalPager` recomposes captured plain values —
  verified by a throwaway Robolectric probe). domain/ + golden + ChartCanvas stayed fenced. Full ladder green.
  (Affects S14.)

- **D-087 (S14 — prof759/task545 proximity damp PORTED).** Was a known unwired deferral; the owner asked
  for parity, not a drop. The damp belongs in the domain (it is part of task544's alpha computation), so
  `BrightnessEngine.smoothLux` gained an additive `luxAlphaDamp` param (default 1.0 ⇒ golden vectors
  byte-identical) applied as `round3(1−exp(…)) × damp`, fed from a new `BrightnessPolicyInput.proximityNear`
  (`evaluate` → ×`PROXIMITY_ALPHA_DAMP`=0.1 when near). Runtime: `platform/sensor/ProximitySensorSource`
  (TYPE_PROXIMITY, near = value < maxRange) → `ProximityTracker` (its own file, started in `startSensor`,
  stopped on hibernate/teardown) → `PipelineState.proximityNear` (atomic update from the collector, same
  documented exception as `lastSampleMs`) → `buildInput`. Never pauses (task545). Tests: domain
  `proximityNear_dampsLuxAlphaByTenth`, controller `proximityNear_propagatesToPipelineState`.

- **D-088 (S14 — task524 power-draw calibration PORTED).** Was "measurement deferred" (old D-044); the
  owner flagged it a real tour de force and wanted parity. Ported verbatim from the extracted Java:
  `domain/power/PowerDrawCalibration` (geometric steps `(int)(255·(i/16)^0.45)` kept ≥5 apart ending 255;
  µA→mA normalize `|raw|>50000 ⇒ /1000`; net-of-idle post-process with bogus-baseline zeroing) +
  `platform/context/PowerMeter` (BATTERY_PROPERTY_CURRENT_NOW→AVERAGE fallback, EXTRA_VOLTAGE, STATUS) +
  `app/runtime/PowerDrawCalibrator` (safety checks → ramp-down → 6 s baseline poll → latch-breaker sweep
  with nudge → 2 s settle → sample; all side effects injected for testing) + `PowerDrawStore` (Preferences
  JSON) + `PowerDrawViewModel` + Tools prep-dialog/Calibrate flow driving the Activity window brightness
  (native equivalent of Tasker's fullscreen override; no WRITE_SETTINGS) → existing `PowerDrawChart`.
  `PowerDrawSample` is now the canonical domain type (chart imports it). Tests: `PowerDrawCalibrationTest`,
  `PowerDrawCalibratorTest` (fake meter), `PowerDrawStoreTest`. Chart.js HTML output NOT ported (native chart).

- **D-089 (S14 — owner `refresh()` feedback validated + fixed).** The claim (`AndroidPrivilegeManager.refresh()`
  every cycle in `SuperDimmingCoordinator.apply`, re-checking `checkSelfPermission`/`canWrite` uncached) was
  **substantively correct** — the `AppModule` `tierProvider` lambda ran `refresh()` then `currentTier()`, so
  every dimming evaluation did 2 Binder permission checks. **Imprecise on detail:** it is once per *cycle*
  (one sensor evaluation), NOT per animation *frame* (frames are in `AnimationRunner`, which never reads the
  tier), and it was wired in exactly ONE lambda, not "everywhere". Fix per the recommendation: `tierProvider`
  reads the cached `currentTier()` (no IPC); `privilegeManager.refresh()` moved to the resume points
  (`AmbientMonitoringService.ensureRunning` + `onScreenOn`), `RuntimeGraph` exposes the manager. A post-start
  ADB/Shizuku grant is still picked up on the next wake. Test: `SuperDimmingCoordinatorTest.apply_readsTierProviderExactlyOncePerCycle`.

- **D-090 (S14 — engineering quality, incl. two owner pushbacks on guard tests).** (a) **Provenance guard
  reworked** — the owner noted an exact-count `// Tasker` test that you "bypass by bumping the number" guards
  nothing (and a delete-one/add-one keeps the count). Replaced with content-based `ProvenanceTest` (a FLOOR +
  presence of the parity-critical rounding anchors `Math.round`/`HALF_UP`/`round3` and each core task ID), so
  honest additions never trip it while a targeted removal does. (b) **LOC guard honored, not bumped** — the
  proximity wiring pushed the orchestrator past the 300-LOC re-bloat guard; instead of a lazy bump, the
  proximity job lifecycle was extracted to `ProximityTracker` (the decomposition the guard encourages) and the
  ceiling raised the minimal honest amount (300→310, documented as feature-growth ≠ monolith re-bloat). (c)
  **Settings clamp-on-Apply** (closes S14 carry-forward D-085): `DraftSettingsViewModel.apply` now runs
  `AabSettings.validate()` so a parameter screen can't persist an out-of-range value. (d) **Dead code:**
  `ui/graph/LineGraph.kt` deleted (orphaned since ChartCanvas; referenced only by docs). (e) **@Volatile audit:**
  no inappropriate uses (all visibility-only with benign/justified races, per S12.9e); fixed `ThrottleController`'s
  stale "single-writer" header (S12.8b'' made it dual-writer). (f) **Dashboard:** the hero brightness number
  animates via `animateIntAsState`. (g) **Charts:** live "Now" markers on Reactivity (current lux), Dimming
  (when engaged) and Taper (when scaling active) — the circadian charts already had "Now". (h) **Icon:** the
  launcher artwork scaled to 0.88 around centre so the dial knob sits inside the r33 adaptive safe zone. (i)
  **Deprecations:** none in our main source or build scripts; the "incompatible with Gradle 9.0" notice is
  AGP-8.7.3-internal (clears on an AGP bump — deferred, version-locked per `app/lint.xml`); test-only Robolectric
  `withIntent`/`Notification.PRIORITY_HIGH` deprecations left as-is. (Affects S14.)

- **D-091 (Gate 3 punch-list — the 18 G3-F findings).** Worked all 18 owner device findings on this
  session's branch (fast-forwarded onto the S14 branch content per the hand-off note, so all of S14 is
  carried — nothing rebased onto bare `main`). Highlights: **(G3-F17, domain fence)** the curve-wizard
  `tau` default was 4.0 (the Java-header fallback) but task038 act2 (547) *always* sets `%tau=0.001`
  before the Java engine reads it — so 0.001 is the operative default and 4.0 over-damped every
  suggestion toward the current curve ("poor quality"). Changed the `CurveSuggestionInput.tau` default
  to 0.001 (evidence: task038_curve_wizard.md L27/L69/L114). **Golden-safe:** every wizard golden passes
  τ explicitly (4.0/1.0/2.0/8.0), so no vector moved; `wizard_defaultTauIsTheFaithfulAct2Value` locks
  it. Also exposed τ as a wizard slider (0.001–5). **(G3-F3)** `AabSettings.validate()` floor for
  minBrightness 1→0 (the Misc slider already exposes 0..75; the clamp made committed≠draft → perpetual
  dirty; OEM `toDevice` coerces 0..255 so 0 = dimmest, not off). **(G3-F5)** decouple `targetBrightness`
  (published before the sweep) from `lastAppliedBrightness` (at settle) so the Dashboard instrument
  animates DURING the transition. **(G3-F6)** `SuperDimmingCoordinator` treats a computed `level<=0` as
  disengage (no residual Extra Dim at level 0). **(G3-F11)** "Reset to auto" routed to the Resume path
  (works while paused). **(G3-F12, privacy)** ip-api.com geo-IP fallback gained an opt-out toggle
  (`ExperimentPrefsStore.geoIpEnabled`, gated in `AppModule`, surfaced on Circadian) + README Privacy
  note. **(G3-F8)** the external-PR workflow now triages (comment + `needs-triage` label) instead of
  auto-closing, so app-layer/Android bug-fix PRs are welcome (features still → AAB); added
  `.github/ISSUE_TEMPLATE/bug_report.md`. **(G3-F10, DEFERRED)** menu icons: only `material-icons-core`
  is a dependency (~50 icons), so most "correct" Material Symbols (Brightness*/Tune/DarkMode/BugReport)
  aren't available; made the one clear within-core fix (Curve & Brightness gear→Create, removing the
  duplicate with Misc) and left the rest. A fuller icon pass needs adding `material-icons-extended` — an
  APK-size/dependency decision the brief doesn't sanction, so it's left for the owner. (Affects Gate 3 /
  any follow-up.)

- **D-092 (Gate 3 punch-list R2 — owner device follow-ups).** **Power-draw (task524):** the owner
  supplied the verbatim task524 Java; cross-checked it — the domain math (`generateSteps`,
  normalize-1st-to-0, net-of-idle) and the calibrator (nudge after 3.5 s, settle 2 s, latch-breaker)
  were already faithful. The "poor results" came from two MISSING pieces, now fixed: the calibration
  must run on a **full-white screen** (OLED power is colour-dependent — Tasker uses a white FrameLayout
  dialog; the rebuild swept over the dark UI), so added a fullscreen white `Dialog` overlay
  (`PowerCalibrationOverlay`) that drives its own window brightness; and the chart needed a **real
  secondary mA y-axis** (it had been rescaling current onto the power axis). Net-of-idle / normalize are
  unchanged (golden-tested). **Shizuku (CLAUDE.md):** the architecture doc still claimed Shizuku is
  "never a runtime binder dependency" — false (the no-Location Wi-Fi SSID strategy runs `cmd wifi
  status` via Shizuku at runtime). Corrected to own it as an optional runtime dependency. **Context
  editor:** reworked from an all-expanded form with top buttons into collapsible per-trigger sections
  (seeded from the rule, persist on edit) + radius default 200 m + a sticky bottom Save/Cancel bar —
  the host (`ContextRulesSection`) no longer wraps the editor in its own scroll (the editor owns it).
  **Smaller copy/UX:** User-Guide teal banner removed (Menu-only); Reactivity "Smoothing thresholds" →
  "Reactivity thresholds"; restricted-settings card de-emoji'd + shortened; README Stars/Downloads/
  Release shields; power-draw "task524" jargon removed from user-facing copy. **Editor polish (same
  round):** the full-screen rule-editor Dialog wasn't inset-padded, so the sticky Save/Cancel bar
  clipped behind the system nav bar → added `statusBarsPadding()`+`navigationBarsPadding()`; moved the
  "Only while charging" control to the LEFT (it sat on the right like the section toggles); trigger
  labels dropped `titleSmall`→`bodyMedium` (read too large). (Affects Gate 3 / any follow-up.)

- **D-093 (external-AI review triage).** 3 of 4 "holes" valid + fixed; 1 hole + the nit rebutted.
  **Fixed:** dead manifest `ScreenStateReceiver` removed (manifest `SCREEN_ON/OFF` undeliverable to
  manifest receivers; START_STICKY + MaintenanceWorker resurrect); `ContextEngine` time-rule
  self-scheduler (`timeJob` → `millisUntilNextContextWake` → TIME eval) so time/Sunrise/Sunset rules
  fire on time in constant light (prof764 parity; doze backstops noted); `AabFlash` background fallback
  → plain text toast (Android 11+ blocks background custom-view toasts). **Rebutted:** OEM brightness
  range — `getIdentifier` on a framework resource is not non-SDK reflection (not blocked on API 28+),
  the suggested `BrightnessInfo.brightnessMaximum` is the float API (wrong for the legacy int we
  write), and the existing `config_screenBrightnessSettingMaximum` read already learns the OEM int max;
  adaptive-`deviceMax` backstop declined (perturbs override-detection for a config-already-covered
  case). **Nit rebutted:** Shizuku `this` is correct — the reviewer's rename suggestions don't compile
  for an anonymous object. (Affects Gate 3 / 1.0 readiness.)

- **D-094 (second review pass — panic IPC + exact-alarm question).** **VALID → fixed:**
  `AndroidPanicSensorSource.onSensorChanged` read `power.isInteractive` on EVERY accelerometer sample
  (`SENSOR_DELAY_UI` ≈ 16–60 Hz) — a synchronous Binder IPC to system_server per event (lock
  contention / CPU / battery). Now the screen-interactive state is seeded once and kept current via the
  cheap `SCREEN_ON/OFF` system broadcasts (a dynamic receiver in the same callbackFlow, unregistered in
  `awaitClose`), stored in an `AtomicBoolean` the sensor callback reads from local memory. Behaviour
  identical (the `PanicGate` logic is unchanged + still pure-tested); only the IPC is gone.
  **AGREED, no change:** declaring `SCHEDULE_EXACT_ALARM` to dodge Doze for time contexts — confirmed
  none is declared and the D-093 time scheduler is purely `delay()`-based (best-effort), which matches
  the advice; auto-brightness context automation is best-effort runtime automation, not a clock/alarm
  feature warranting exact-alarm special access. If on-device testing later shows the Doze delay is
  unacceptable, the right move is an AlarmManager exact-alarm scheduler gated behind
  `canScheduleExactAlarms()` — noted, not done. (Affects Gate 3 / 1.0 readiness.)

- **D-095 (CI infra — PR #65 build.yml).** First PR-triggered `build.yml` run failed, but NOT on our
  code — `android-actions/setup-android@v3` defaults to installing `tools platform-tools`, and the
  obsolete `tools` package pulls the Android Emulator, whose download came back corrupt ("Error on
  ZipFile unknown archive"). CI runs only unit tests + lint + assembleDebug (no emulator — there's no
  KVM anywhere in this program). Fixed durably with `packages: ''` on the setup-android step (skips the
  emulator entirely; cmdline-tools + licenses still set up, AGP fetches compile-SDK/build-tools on
  demand). No source change — the 1.0.0 build itself is green locally. (Affects CI / release.)

Append new entries as D-096, … with which segments they affect.

