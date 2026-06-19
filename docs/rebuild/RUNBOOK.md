# RUNBOOK — segment briefs for the Tasker → Kotlin rebuild

How to run a segment: start a fresh Claude Code session with the model/effort listed, prompt it
with: *"Execute segment Sx of docs/rebuild/RUNBOOK.md"* (nothing else is required — CLAUDE.md
routes the session through the protocol). Segments must check their **preconditions** against
`STATE.md` and refuse to start if unmet (log a BLOCKED row instead).

Models are Opus / Sonnet / Haiku at reasoning effort low / medium / high.

**Model policy since S8.5 (D-035):** S9a onward, code segments run on **Opus**, not Sonnet.
Rationale: every Sonnet code segment passed its own acceptance gate yet left correctness
issues that only review caught (S5 → D-030 gate-polarity + insertion-order bugs; S7 → D-034
suppress-echo race + OEM rounding drift), and the owner observed Sonnet sessions hitting or
nearing context compaction, which degrades exactly the long-tail care these briefs need.
S13 stays Haiku (fenced template replication). **Compaction rule (all models):** if a session
compacts, record it in its STATE.md segment-log row (e.g. "Sonnet/high, compacted ×1") so
quality can be correlated; prefer delegating bulk file reading to subagents over reading
into the main context.

## Execution DAG

```
S0 ─┬─ S1 ─┬──────────── S4 ── S5 ─┬─ S6 ─────┐
    ├─ S2 ─┤(S2 feeds S6,S8,S10,S12+) S7 ─────┼─ S9a ── S9b ─┬─ S10 ─┬─ S12 ── S13 ── S14
    └─ S3 ─┴─(gates all builds)────┴─ S8 ─────┘        GATE1 └─ S11 ─┘ GATE2          GATE3
```

Parallel windows (disjoint files; if raced, `git pull --rebase` before push):
**A:** S1 ∥ S2 ∥ S3 · **B:** S6 ∥ S7 ∥ S8 (after S5; S6 and S8 also need S2) ·
**C:** S10 ∥ S11 (after S9b).
Serial spine: S0 → A → S4 → S5 → B → S9a → S9b → C → S12 → **S12.5{a,b,c} →
S12.6{a…e} → S12.7{a…i} → S12.8{a…d} → S12.9{a…f}** → S13{a…d} → S14.
(S9 was split into S9a/S9b in S3.6 — see D-027. S12.5 added after Gate 2 — see D-045; Gate 2 is
re-tested after S12.5c. S12.6 added after the Gate-2 RE-TEST — findings G2R-F1…F25; S12.6a is serial-
first [menu-as-home reshape + renames touch all nav], then S12.6b/c/d/e are a parallel window [disjoint;
rebase before push]; Gate 2 is re-tested AGAIN after S12.6e. S12.7 (a–i) + S12.8 (a–d) followed as
further Gate-2 re-test salvage. **S12.9 (a–f)** is an engineering-quality + parity-debt remediation
stage inserted between S12.8 and S13 — see §S12.9 and D-069. **S13** is promoted to Opus/high and
split into a–d — design-system foundation/components/restyle + charts/static screens — see D-070.)

## Global failure protocol

Referenced by every segment; defined in CLAUDE.md: never leave the branch red; BLOCKED/PARTIAL
rows in STATE.md carry failing command + error tail + suspected cause + recommended next
action/model escalation; side branch `claude/blocked-Sx` for unfinishable code.

---

## S1 — Ground-truth extraction A: core pipeline + all 40 Java blocks

**Model:** Opus / high · **Size:** large · **Preconditions:** S0 (this scaffolding).
**SDK not needed** (docs-only segment — skip setup script).

**Objective:** Verbatim, provenance-stamped extraction of every embedded Java block and
action-by-action specs of the core pipeline tasks + their triggering profiles, so no later
segment ever re-reads the XML for pipeline logic.

**Inputs:** `docs/rebuild/XML_RECIPES.md` (mandatory read; use R7's census table as your work
queue); XML strictly via recipes; `docs/migration/variable_contracts.md` and
`docs/migration/aab_settings_schema.md` as cross-checks only (they are NOT ground truth).

**Deliverables:**
1. `docs/rebuild/extraction/java/<taskid>_<n>_<slug>.java` — all 40 blocks (`<n>` = 1 or 2 for
   dual-block tasks), XML-entity-decoded verbatim, header comment: task id, task name, XML line
   range, `arg1` output variable, list of `%vars` consumed (these become parameters in S4).
   Decode with python3 `html.unescape` or equivalent — do not hand-decode.
2. `docs/rebuild/extraction/tasks/task<id>_<slug>.md` for EACH of: 554, 544, 535, 546, 661,
   548, 659, 543, 696, 698, 700, 618, 585, 566, 567, 569, 561, 528, 570, 645, 646, 647, 650,
   653, 654, 644, 551, 584. Per file: every action in order — code, decoded args, condition
   expressions, loop/goto structure, labels — plus variables read/written. For Variable Set
   (547) actions capture the maths expression EXACTLY (task661's curve math lives there, see
   STATE.md D-002).
3. `docs/rebuild/extraction/profiles.md` — for profiles 753–761, 769: context type+code+args
   AND the full `<ConditionList>` semantics (D-003: prof760 carries the absolute-threshold
   gate: `%as_values1` vs `%AAB_ThreshAbsLow/High`, accuracy trust, `%AAB_MainLoop`), enter/exit
   task ids, priority. (Context profiles 762–768 + 8 belong to S2 — skip.)
4. `docs/rebuild/extraction/pipeline_spec.md` — prose+pseudocode end-to-end flow: sensor event
   → profile gate → ingest → threshold/smoothing → mapping → scale → animation plan →
   per-frame writes with read-back override detection → super dimming; PLUS the override
   detect/pause/resume state machine (567/569/561 + profiles 755/756), hibernate (753/585),
   throttle reinit (754/566), initial brightness on wake (761/618), panic (769/528).
   State variables table: every runtime (non-settings) `%AAB_*` var with meaning + lifecycle.
5. `docs/rebuild/extraction/defaults_audit.md` — ALL 125 `%AAB_*` vars: default value (from
   task570 Initialize AAB Defaults + task592 _CreateDefaultProfiles + task637), type,
   user-facing-setting vs runtime-state classification, present-in-`AabSettings.kt`? Explicitly
   resolve D-004 (`%AAB_AnimSteps`, `%AAB_MaxSteps`, `%AAB_ThreshMidpoint`, AnimationConfig
   default conflicts → state which values are canonical).
6. `docs/rebuild/extraction/INDEX.md` — inventory of all produced files; incrementally-built
   action-code legend (code → meaning, derived from in-place evidence); **"unresolved"
   section** for any action whose semantics you could not establish — record code + raw XML
   snippet + location, NEVER guess; spot-check section: 3 formulas with XML line provenance
   cross-checked against `domain/.../brightness/BrightnessEngine.kt` (note match/mismatch —
   do not fix code).

**Non-goals:** No Kotlin/gradle changes. No scene extraction, no task90/38/contexts (S2 owns
them — but extract their Java blocks anyway: ALL 40 means all 40). No "improving" Tasker logic.

**Steps:** (1) Work through R7's table: for each line anchor, `sed -n` a window, find the
enclosing `<Str sr="arg0">…</Str>`, decode, save; verify
`ls docs/rebuild/extraction/java | wc -l` == 40. (2) awk-extract each listed task; transcribe
actions using R8 histograms to size them first. (3) Extract listed profiles (R4 — mind the
`ve="2"` pattern). (4) Build pipeline_spec.md from the above. (5) defaults_audit.md: awk task570
(+592/637 deltas). (6) INDEX.md + flip PARITY_CHECKLIST "Extracted" cells for your rows.
(7) STATE.md row + push.

**Acceptance:** 40 java files; all listed task/profile docs exist; INDEX.md complete with
legend + unresolved section (empty is fine, absent is not); checklist cells flipped; pushed.

**Failure notes:** If a Java block spans nested CDATA-like oddities or an action's arg encoding
is ambiguous → unresolved section, continue. Context-window pressure: process tasks one at a
time, never hold multiple raw dumps.

---

## S2 — Ground-truth extraction B: features, contexts, scenes → screen map

**Model:** Opus / medium · **Size:** large · **Preconditions:** S0. Parallel-safe with S1/S3.
**SDK not needed.**

**Objective:** Extract the non-pipeline features and the entire UI surface; produce the
scene→screen consolidation matrix that S11–S13 implement.

**Inputs:** XML_RECIPES.md (R4/R5 anchors are your map); `docs/migration/tasker_feature_spec.md`
as an outline checklist (verify, don't trust).

**Deliverables:**
1. `docs/rebuild/extraction/tasks/task090_dynamic_scale.md` — all ~80 actions of task90 + its
   two Java blocks' logic explained: solar inputs (does it COMPUTE sunrise/sunset or consume
   Tasker built-ins like %SUNRISE? — answer explicitly, it decides S6's approach), tanh ramp,
   polar day/night branches, location/timezone handling, `%AAB_Scale*` parameter semantics.
2. `…/tasks/task038_curve_wizard.md` — all ~95 actions of the suggestion wizard: inputs
   gathered, fitting/suggestion algorithm, outputs (which `%AAB_Form*` it writes via task655).
3. `…/contexts_spec.md` — profiles 762–768 + 8 and tasks 43, 623, 624, 625, 626, 628, 630,
   631, 633, 105, 26: per-app/WiFi/battery/time/location override semantics, the serialized
   context cache + daily reset, precedence/conflict rules (use profile `<pri>` values),
   what an "override" changes (scale? min/max? disable?), resume semantics (626).
4. `…/features_spec.md` — QS tile (551/552), foreground notification incl. button actions
   (584, 692), power-draw calibration (524), debug levels + log surface (634/635), formula
   validation rules (583 _RedInvalidFormulae + 707 _ValidateBrightnessParams — exact per-field
   rules for S8's `validate()`), import/export + default profiles (592/637/622), privilege
   detection/learn flows (378, 643, 563 — feeds S7's PrivilegeManager UX).
5. `docs/rebuild/extraction/scenes/<slug>.md` × 20 — per scene: every element (type, name,
   bound variable, value range, tap/long-tap handler task), WebElement chart contents (which
   series/axes the Chart.js HTML plots — extract from the generator tasks 549/556/557/657/663/
   703/705 rather than the HTML blobs).
6. `docs/rebuild/screen_map.md` — THE consolidation matrix: 20 scenes / 450 elements → target
   M3 screens (default proposal: Dashboard · Curve & Brightness · Reactivity · Animation &
   Dimming · Dynamic Scale · Contexts · Tools (wizard/calibration/debug/experiments) ·
   Profiles & Import/Export · About+Guide+Onboarding). Per element: `kept-as <target>` /
   `merged-into <target>` / `dropped(<reason>)`. Every Chart.js WebElement maps to a named
   Compose chart (BrightnessCurveChart, ReactivityChart, DimmingChart, CircadianChart,
   TaperChart, PowerDrawChart, ExperimentChart). Zero unmapped elements.

**Non-goals:** No code. No pipeline tasks (S1's list). No visual design beyond the matrix.

**Steps:** size each scene/task with R8/`wc -l` before dumping; tabulate elements; derive
context precedence from `<pri>`; cross-check feature list against docs/migration/
tasker_feature_spec.md and record anything it missed; flip checklist "Extracted"/scene-anchor
cells; STATE.md row; push.

**Acceptance:** 20 scene files; screen_map.md has a disposition for all 450 elements (spot
count: `grep -c 'kept-as\|merged-into\|dropped' screen_map.md` ≥ 450); contexts_spec.md covers
all 8 context profiles; task090/task038 docs answer the solar-source question explicitly;
pushed.

---

## S3 — Toolchain modernization + first green build

**Model:** Sonnet / medium · **Size:** medium · **Preconditions:** S0. Parallel-safe with S1/S2
(touches only build files, res/, manifest).

**Objective:** Modern reproducible build: wrapper, version catalog, Kotlin 2.0.x, AGP 8.7.x,
compose BOM, minimal res/ (kills the `R.mipmap.ic_launcher` blocker), correct manifest,
`:platform` → Android library, `:data` retired, lint baseline frozen.

**Inputs:** run `scripts/setup-android-sdk.sh` first; all `*.gradle.kts`, `gradle.properties`,
`app/src/main/AndroidManifest.xml`.

**Steps & deliverables:**
1. `/opt/gradle/bin/gradle wrapper --gradle-version 8.14.3` → commit gradlew + wrapper dir
   (ensure `gradle-wrapper.jar` is committed; check .gitignore doesn't eat it).
2. Fix D-007 FIRST: `settings.gradle.kts` needs `pluginManagement { repositories { google();
   mavenCentral(); gradlePluginPortal() } }` + `dependencyResolutionManagement` — without it
   NOTHING configures (AGP is not on the Gradle Plugin Portal alone).
3. `gradle/libs.versions.toml`: kotlin `2.0.21`, AGP `8.7.3`, compose-bom `2024.12.01`,
   activity-compose `1.9.3`, navigation-compose `2.8.5`, lifecycle `2.8.7`, datastore `1.1.1`,
   work-runtime-ktx `2.10.0`, kotlinx-serialization-json `1.7.3`, coroutines `1.9.0`,
   robolectric `4.14.1`, androidx-test-core/junit, shizuku-api+provider `13.1.5` (declare only;
   first used in S7). On any resolution failure: step DOWN one minor version at a time, record
   the final matrix in STATE.md. Migrate every build file to catalog refs.
4. Kotlin 2 compose migration: apply `org.jetbrains.kotlin.plugin.compose` in `:app`; DELETE
   `composeOptions { kotlinCompilerExtensionVersion = … }`.
5. `gradle.properties`: `org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8`,
   `android.useAndroidX=true`, `org.gradle.caching=true`.
6. `app/src/main/res/`: `values/strings.xml` (app_name "Tideo Auto Brightness"),
   `values/themes.xml` (Theme.Material3.DayNight.NoActionBar parent),
   `drawable/ic_launcher_foreground.xml` (simple brightness-sun vector),
   `mipmap-anydpi-v26/ic_launcher.xml` adaptive icon (+ `values/ic_launcher_background.xml`
   color). Notification small icon: `drawable/ic_stat_brightness.xml` vector.
7. Manifest: `<uses-permission>` WRITE_SETTINGS (+`tools:ignore="ProtectedPermissions"`),
   WRITE_SECURE_SETTINGS (same), POST_NOTIFICATIONS, FOREGROUND_SERVICE,
   FOREGROUND_SERVICE_SPECIAL_USE, RECEIVE_BOOT_COMPLETED, PACKAGE_USAGE_STATS
   (`tools:ignore`), ACCESS_COARSE_LOCATION + ACCESS_FINE_LOCATION (wifi SSID/location
   contexts; runtime-requested later). Service: `foregroundServiceType="specialUse"` +
   child `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
   android:value="Continuous ambient-light monitoring to drive adaptive screen brightness"/>`.
   `application android:theme="@style/Theme.TideoAutoBrightness"
   android:icon="@mipmap/ic_launcher"`. Verify activity name matches the class in `Main.kt`.
8. `app/build.gradle.kts`: minSdk **31**, compileSdk/targetSdk 35, namespace unchanged,
   buildFeatures.compose true, deps via BOM. Replace the
   `R.mipmap.ic_launcher` reference in `AmbientMonitoringService.kt:85` with
   `R.drawable.ic_stat_brightness` (this is the ONLY permitted source-file edit).
9. `:platform` → `com.android.library` (namespace `com.tideo.autobrightness.platform`,
   minSdk 31, compileSdk 35, Robolectric-ready: `testOptions.unitTests
   { isIncludeAndroidResources = true; isReturnDefaultValues = true }`). Keep
   `SystemAdapters.kt` compiling as-is (deleted in S9b, not now).
10. Retire `:data`: `grep -rn "autobrightness.data" app domain platform` — if (expected) zero
   references: remove from `settings.gradle.kts`, `git rm -r data/`. If referenced: record in
   STATE.md and defer deletion to S9b.
11. Lint baseline: `./gradlew :app:lintDebug` → commit `app/lint-baseline.xml`
    (`lint { baseline = file("lint-baseline.xml") }`). Policy: baseline never grows.
12. STATE.md (record exact final version matrix) + checklist untouched (no parity rows here) +
    push.

**Non-goals:** No behavior changes, no new features, no DI framework, no source edits beyond
step 8's single line, no res beyond the minimal set.

**Acceptance:** `./gradlew :app:assembleDebug :domain:test :platform:test :app:lintDebug` all
green; `ls app/build/outputs/apk/debug/app-debug.apk` succeeds; pushed.

**Failure notes:** AGP/Kotlin/SDK mismatch errors name the required versions — follow them one
step at a time and log. If `:domain:test` was already failing pre-segment, note it but your
gate is "no NEW failures" (record exact pre/post failure lists in STATE.md).

---

## S4 — Tasker reference implementation + golden vectors

**Model:** Opus / medium · **Size:** large · **Preconditions:** S1 DONE, S3 DONE.

**Objective:** Transcribe S1's extracted Java into a test-only JVM "Tasker reference
implementation", generate committed golden vectors, wire parity tests. The reference is the
behavioral oracle for all later domain work.

**Inputs:** `docs/rebuild/extraction/java/` (pipeline blocks: 554, 544, 535, 546, 548, 543,
696, 698, 700, 618 + task661's action-math from `extraction/tasks/task661_*.md` and 659's
continuity math), `pipeline_spec.md`, `defaults_audit.md`;
`domain/.../brightness/BrightnessEngine.kt`, `BrightnessPolicyInput.kt`.

**Deliverables:**
1. `domain/src/test/kotlin/com/tideo/autobrightness/domain/reference/TaskerReference.kt` —
   one function per Java block / per task661+659 math unit. **Java semantics preserved
   exactly**: `Math.round` (ties toward +∞ — differs from `kotlin.math.round` on negative
   halves), `BigDecimal.setScale(n, RoundingMode.HALF_UP)` where Tasker used it, string
   formatting where Tasker produced strings, int truncation. Every `%var` the block consumed
   is a parameter (S1 headers list them). Provenance header per function.
2. `…/reference/GoldenVectorGenerator.kt` — main() (or test gated on
   `System.getProperty("regenGolden")`) writing CSVs to `domain/src/test/resources/golden/`.
3. Committed vectors `golden/{threshold,smoothing,mapping,formulae,animation,taper,dimming,
   transition}.csv` — header row naming inputs/outputs; ≥500 rows each: log-spaced lux
   0.01→120000, × ≥4 settings variants (extracted defaults + edge variants: min==max bright,
   zone1End boundary, negative effective-delta ties, scale >1 and <1); explicit boundary rows
   (lux == zone1End, == zone2End, 0, sensor max).
4. `domain/src/test/kotlin/.../parity/CorePipelineParityTest.kt` — asserts CURRENT
   `BrightnessEngine` (and helpers) against the vectors. Numeric tolerance 1e-9; strings exact.
5. `docs/rebuild/parity_gaps.md` — every failing case triaged: gap id, vector file+row range,
   reference value vs engine value, root-cause hypothesis. Mark failing parity tests
   `@Ignore("S5: gap-<id>")` INDIVIDUALLY (never blanket-ignore).

**Non-goals:** Do NOT fix `BrightnessEngine` (S5's job). No circadian/wizard (S6). No app/
platform changes. No gradle changes beyond (if needed) a test-resources stanza.

**Expression transcription protocol (code-547 maths — task661 curve math, task659 continuity;
D-002/D-027):** Java blocks get verbatim decode, but Variable Set maths expressions need their
own discipline: (a) start from the verbatim expression in the extraction doc; (b) write the
parse tree you inferred into the function's provenance comment (note operator precedence/
associativity assumptions explicitly); (c) cross-validate the task661 transcription against
task663's plot-side Java copy of the same 3-zone formula (CLAUDE.md ledger) by running both
over the golden lux grid; (d) if 661 and 663 disagree on any row, record both values in
`parity_gaps.md` and re-derive from the XML via recipes — NEVER pick one by guessing. This is
the highest-risk transcription in the program: a divergence here is wrong brightness everywhere.

**Steps:** transcribe block-by-block with the original Java in a side comment where subtle;
sanity-test each reference function against hand-computed values from XML comments/Flash
debug strings; generate vectors; run `./gradlew :domain:test`; triage failures; STATE.md +
checklist ("Reference impl" cells) + push.

**Acceptance:** `./gradlew :domain:test` green (documented `@Ignore`s only); vectors
committed; `parity_gaps.md` enumerates every ignored case (cross-check:
`grep -c @Ignore domain/src/test` == gap count); pushed.

**Failure notes:** If extraction proves ambiguous mid-transcription (e.g. operator precedence
in a Variable Set maths expression), re-derive from XML via recipes, update the extraction doc
+ note in STATE.md (extraction docs are correctable by EVIDENCE, never by convenience).

---

## S5 — Domain engine parity completion

**Model:** Sonnet / high · **Size:** large · **Preconditions:** S4 DONE.

**Objective:** Make production domain code match the reference exactly; add the missing pure
components; finalize the domain API the runtime consumes.

**Inputs:** `parity_gaps.md`, golden CSVs + `TaskerReference.kt`, extraction docs for tasks
659, 700, 567/569/561, 618; `defaults_audit.md`.

**Deliverables:**
1. `BrightnessEngine.kt` fixed: every gap closed, all `@Ignore`s removed.
2. `domain/.../brightness/BrightnessFormulae.kt` — `deriveContinuityCoefficients(...)` →
   form2A/form3A per task659 (golden-tested via `formulae.csv`).
3. `domain/.../brightness/SoftwareDimming.kt` — reduce_bright_colors level calculation incl.
   PWM-sensitive exponent compression, dimmingThreshold/strength/spread semantics (task700 +
   645–654 privileged/unprivileged variants — the privilege SPLIT is platform's job; the MATH
   is yours).
4. `domain/.../brightness/OverrideRules.kt` — pure detect/pause/resume decision logic
   (tasks 567/569/561): when is an external write "manual override", when does auto resume
   (lux delta? timer? screen cycle? — per extraction).
5. `domain/.../brightness/InitialBrightness.kt` — wake-time initial set logic (task618).
6. Config data classes reconciled with `defaults_audit.md` canonical values (D-004): update
   `BrightnessPolicyInput.kt` defaults; keep the 6 existing `BrightnessEngineContractTest`
   tests passing (update only if extraction proves them wrong — STATE.md note).

**Non-goals:** No platform/app changes. NEVER edit reference impl or vectors to make tests
pass (only evidence-backed extraction corrections, logged).

**Acceptance:** `./gradlew :domain:test` fully green; `grep -rn "@Ignore"
domain/src/test` → empty; pushed.

---

## S6 — Circadian solar engine + curve-wizard math

**Model:** Sonnet / high · **Size:** medium-large · **Preconditions:** S2 DONE, S5 DONE.
Parallel window B (with S7, S8).

**Objective:** Port task90 (dynamic scale: solar times, tanh ramp, polar handling) and task38
(curve suggestion wizard) into pure domain code with reference + goldens.

**Inputs:** `extraction/tasks/task090_dynamic_scale.md`, `task038_curve_wizard.md`, their Java
blocks in `extraction/java/`, `TaskerReference.kt` conventions.

**Deliverables:** `domain/.../circadian/SolarTimes.kt` (IF task90 computes sun times: port its
exact algorithm; IF it consumed Tasker's built-in %SUNRISE/%SUNSET: implement the NOAA solar
equations and golden-test against published NOAA tables for ≥6 lat/date combos incl. polar
day/night — S2's doc says which case applies; record choice in STATE.md);
`domain/.../circadian/DynamicScaleEngine.kt` (absorb/refactor `computeDynamicScale` +
`rampProgress` out of BrightnessEngine, driven by SolarTimes + manual-times fallback);
`domain/.../wizard/CurveSuggestionEngine.kt` (task38 + task655 output mapping); reference
functions + `golden/{circadian,wizard}.csv` + parity tests; polar assertions (progress pins
1/0 when sunlightDurationMinutes ≷ 1380 — verify against extraction, the current
BrightnessEngine hardcodes 1380).

**Non-goals:** No location acquisition (platform S7), no UI, no scheduling.

**Acceptance:** `./gradlew :domain:test` green incl. new parity + polar tests; pushed.

---

## S7 — Platform adapters + tiered privilege manager

**Model:** Sonnet / medium · **Size:** large · **Preconditions:** S3 DONE, S5 DONE.
Parallel window B.

**Objective:** Replace every fake with real Android adapters behind small interfaces,
including the BASIC/ELEVATED privilege model and OEM brightness-range normalization.

**Inputs:** `pipeline_spec.md` (sensor + write semantics), `extraction/tasks/task700_*.md` +
645–654 (dimming write paths), `features_spec.md` privilege section (378/643/563),
existing `platform/SystemAdapters.kt` (supersede, don't delete).

**Deliverables** (all in `platform/src/main/kotlin/com/tideo/autobrightness/platform/`, each
as interface + impl + Robolectric/fake test):
- `sensor/LightSensorSource.kt` — `SensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)`,
  `registerListener(..., SENSOR_DELAY_NORMAL)`, expose `callbackFlow<LightSample>`
  (lux + accuracy + timestamp; accuracy matters: prof760 trust-gate), `awaitClose
  { unregisterListener }`.
- `brightness/ScreenBrightnessController.kt` — read/write `Settings.System.SCREEN_BRIGHTNESS`;
  force `SCREEN_BRIGHTNESS_MODE_MANUAL` on enable (restore prior mode on disable); **range
  normalization**: device max via `Resources.getSystem().getIdentifier(
  "config_screenBrightnessSettingMaximum", "integer", "android")` (fallback 255), expose
  domain-facing 0–255 (Tasker parity) mapped to device scale; suppress-echo hook: caller
  registers expected values so self-writes aren't "manual overrides".
- `brightness/SecureDimmingController.kt` — `Settings.Secure.putInt(resolver,
  "reduce_bright_colors_level", n)` + `"reduce_bright_colors_activated"` 0/1; returns
  Result.failure with status when tier < ELEVATED (callers degrade gracefully).
- `privilege/PrivilegeManager.kt` — `enum Tier { NONE, BASIC, ELEVATED }`;
  BASIC ⇔ `Settings.System.canWrite(context)` (request via
  `Settings.ACTION_MANAGE_WRITE_SETTINGS` intent w/ package uri);
  ELEVATED ⇔ `context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
  PERMISSION_GRANTED`; grant helpers: (a) adb instruction string
  `adb shell pm grant com.tideo.autobrightness android.permission.WRITE_SECURE_SETTINGS`,
  (b) Shizuku: `Shizuku.pingBinder()`, `Shizuku.checkSelfPermission()`/`requestPermission(code)`,
  then run the same `pm grant` via Shizuku (`Shizuku.newProcess` or user-service exec) —
  add `ShizukuProvider` to app manifest (S11 wires UI; you land the manifest entry + deps),
  (c) root: `Runtime.exec(arrayOf("su","-c","pm grant …"))`. After grant the app writes
  Settings.Secure DIRECTLY — Shizuku is not a runtime dependency. Expose `tierFlow`.
- `observe/BrightnessObserver.kt` — `ContentObserver` on
  `Settings.System.getUriFor(SCREEN_BRIGHTNESS)` via `registerContentObserver(uri, false, …)`
  → callbackFlow of external changes, filtered through the suppress-echo hook.
- `context/BatteryStateReader.kt` (sticky `ACTION_BATTERY_CHANGED` + `BatteryManager`),
  `context/LocationReader.kt` (`LocationManager.getLastKnownLocation(PASSIVE_PROVIDER)`,
  nullable; manual lat/lon fallback comes from settings),
  `context/ForegroundAppMonitor.kt` (`UsageStatsManager.queryEvents` trailing-window →
  current foreground package; permission check via `AppOpsManager.unsafeCheckOpNoThrow(
  OPSTR_GET_USAGE_STATS, …)`; grant intent `Settings.ACTION_USAGE_ACCESS_SETTINGS`),
  `context/WifiInfoReader.kt` (`ConnectivityManager.registerNetworkCallback` with
  `FLAG_INCLUDE_LOCATION_INFO`, read `transportInfo as WifiInfo` SSID; document
  FINE_LOCATION requirement).
- Mark `SystemAdapters.kt` classes `@Deprecated("S9b removes")` (toy loop still uses them).

**Non-goals:** No service/runtime rewiring (S9a/S9b), no UI, no context POLICY (S10 — you build
readers only), no deletion of fakes.

**Acceptance:** `./gradlew :platform:test :app:assembleDebug` green. Robolectric coverage:
brightness write/read + mode force (`ShadowSettings`), tier detection (shadow grant),
observer dispatch (`ShadowContentResolver.notifyChange`), dimming controller tier-gating;
sensor source covered by a fake-driven flow test (Robolectric sensors are limited — instantiation
+ unregister-on-cancel is enough). Pushed.

**Failure notes:** Shizuku exec API friction → land the interface + adb/root paths + a
`ShizukuGrantGateway` stub recording the limitation in STATE.md; S11 can finish the flow.

---

## S8 — Settings schema v2, persistence, import/export

**Model:** Sonnet / medium · **Size:** medium · **Preconditions:** S1 DONE, S2 DONE
(features_spec.md carries the 583/707 validation rules + 592/637/622 import/export semantics),
S5 DONE. Parallel window B.

**Objective:** Close every settings-schema gap, version the schema with migration, make
import/export + default profiles real, expose validation for the UI.

**Inputs:** `defaults_audit.md` (canonical), `extraction/features_spec.md` (592/637/622 +
583/707 validation rules), existing `app/.../settings/*` + `storage/AppDataStores.kt`.

**Deliverables:** `AabSettings` v2 — add `animSteps`, `maxSteps`(if distinct), `thresholdMidpoint`,
and every var `defaults_audit.md` classifies user-facing that's missing; ALL defaults
reconciled to audit values; `CURRENT_SCHEMA_VERSION = 2` + migration (v1 JSON → v2 fills new
fields with audit defaults) + migration unit test; `AabSettingsMapper` completed: settings →
`ThresholdConfig`/`AnimationConfig`/`BrightnessCurveConfig`/`DynamicScalingConfig` incl.
derived form2A/form3A via `BrightnessFormulae` (D-002 chain); built-in default profile set per
task637/592; legacy Tasker import round-trip test (fixture built from audit defaults through
`TaskerLegacyProfileSerializer`); `SettingsValidator.validate(AabSettings):
List<FieldError(field, message)>` implementing 583/707 rules exactly (for S12's red-invalid UI);
context-override rules storage MODEL (data classes + serializer only — engine is S10).

**Non-goals:** No UI, no runtime wiring, no DataStore re-architecture.

**Acceptance:** `./gradlew :app:testDebugUnitTest :app:assembleDebug` green incl. migration +
round-trip + validator tests; pushed.

---

## S9a — Runtime core: the real pipeline service

**Model:** Opus / high (upgraded from Sonnet per D-035 — parity-critical core before Gate 1)
· **Size:** large · **Preconditions:** S5, S6, S7, S8 all DONE. (Split from the original S9
per D-027 so the parity-critical core can land/block independently of features+cleanup.)

**Objective:** Rebuild the runtime as the sensor-event-driven Tasker pipeline with animated
writes, override detect/resume, hibernate, throttle, panic, actionable notification.

**Inputs:** `pipeline_spec.md` (THE spec), extraction docs for 554/544/535/661/548/543/696/
698/700/618/585/566/567/569/561/528/584, `extraction/profiles.md` (D-021 grouping), all S7
adapters, S8 mapper, existing `app/.../runtime/*`.

**Concurrency model (BINDING, D-027):** the pipeline is serialized — a single pipeline
coroutine processes one event to completion (including its animation frames) before looking at
the next. Sensor events arriving mid-cycle are **DROPPED, not queued** — this is exactly what
prof760's `%AAB_MainLoop != On` clause does in Tasker (a re-entry mutex, D-021): the profile
simply doesn't fire while a cycle runs. Implement as a busy-gate (e.g. `Channel` with
drop-when-busy `trySend`, or an atomic in-cycle flag); the MainLoop flag maps to it 1:1. All
runtime state (smoothedLux, lastRawLux, thresholds, cycleTime…) lives in one state holder
written ONLY from the pipeline coroutine; other coroutines (UI, notification) read via
StateFlow snapshots. No state writes from observer/animation callbacks — they signal the
pipeline coroutine instead.

**Profile-gate strategy (D-027):** HARDCODE each profile's ConditionList as a Kotlin boolean
expression with a provenance comment showing the D-021 parenthesization — do NOT build a
generic ConditionList evaluator (we port fixed profiles, not a Tasker runtime).

**Deliverables:**
1. `app/.../runtime/BrightnessPipelineController.kt` — owns Tasker runtime state (smoothedLux,
   lastRawLux, thresholds-abs, cycleTime, scaleDynamic, dimming state…); consumes
   LightSensorSource flow → profile-gate conditions (D-003: absolute thresholds, accuracy
   trust, main-loop flag) → throttle gate → `BrightnessEngine.evaluate` → animation plan.
2. `runtime/AnimationRunner.kt` — coroutine executing N steps × waitMs: per-frame
   `ScreenBrightnessController.write` + read-back compare (Tasker parity, task696/698) —
   abort + signal override on external mismatch; respects suppress-echo registration.
3. `runtime/OverrideMonitor.kt` — `BrightnessObserver` flow + `OverrideRules` → pause/resume
   state machine incl. resume conditions and notification-text updates (profiles 755/756).
4. `AmbientMonitoringService.kt` REBUILT — `ServiceCompat.startForeground(...,
   FOREGROUND_SERVICE_TYPE_SPECIAL_USE)`; notification: live lux/target (throttled updates),
   actions Pause/Resume/Disable + panic-reset (528: restore sane brightness, clear state);
   SCREEN_OFF → hibernate (sensor unregister, state reset per 585), SCREEN_ON → reinit
   (throttle reset 566 + initial brightness 618 via domain `InitialBrightness`).
5. Gate truth-table unit test (D-027): construct the prof760 AND prof758 condition expressions
   exactly per D-021 grouping and assert true/false for every branch of each clause — a
   mis-parenthesized gate silently suppresses sensor events and no other test catches it.
6. Robolectric/unit tests: service start → foreground notification posted; SCREEN_OFF/ON
   hibernate/reinit (sensor flow subscription state); observer event → paused state; pipeline
   controller unit-tested with fake adapters end-to-end (lux sequence → expected write
   sequence from goldens); mid-cycle event is dropped (concurrency model assertion).

**Non-goals:** Super dimming wiring, QS tile, boot receiver, legacy rip-out (ALL S9b).
Context-override policy (S10), UI screens (S11/S12), onboarding flow (S11).

**Acceptance:** `./gradlew :app:assembleDebug :app:testDebugUnitTest :domain:test
:platform:test :app:lintDebug` green (legacy fakes still present — S9b deletes them); pushed.

**Failure notes:** `ForegroundServiceTypeException` on start → re-check manifest property +
type flag pairing. If blocked, S9b can still NOT proceed (it deletes code S9a's graph replaces)
— log BLOCKED and recommend escalation.

---

## S9b — Runtime features + legacy rip-out

**Model:** Opus / medium (upgraded from Sonnet per D-035) · **Size:** medium ·
**Preconditions:** S9a DONE. Same window — ideally the immediately following session.

**Objective:** Finish the runtime surface (super dimming, QS tile, boot start) and delete
every legacy fake, then declare Gate 1.

**Inputs:** extraction docs 645–654/700 (dimming), 551 (tile), `features_spec.md`;
S9a runtime; `AppModule.kt`.

**Deliverables:**
1. Super dimming: below threshold engage `SecureDimmingController` (math from domain
   `SoftwareDimming`) when tier ELEVATED; clean disengage path (645–654 semantics); wired
   into the S9a pipeline cycle (writes happen from the pipeline coroutine — see S9a
   concurrency model).
2. `runtime/BrightnessTileService.kt` — `TileService` toggling the service; manifest entry
   with `android.permission.BIND_QUICK_SETTINGS_TILE` + icon.
3. `BootCompletedReceiver` — start service if enabled (specialUse FGS is boot-eligible;
   if start fails log + post notification prompting open).
4. RIP-OUT: delete `domain/BrightnessPolicyEngine.kt`, `domain/EvaluateAndApplyBrightnessUseCase.kt`,
   `domain/Ports.kt`, `platform/SystemAdapters.kt`, `app/ui/graph/WebViewGraphFallback.kt`,
   `app/onboarding/PermissionOnboardingStateMachine.kt`, leftover `data/` references; rewrite
   `AppModule.kt` composing the real graph; keep `ServiceHealthStore` telemetry.
5. Robolectric tests: tile instantiation; boot receiver → service start intent; dimming
   engage/disengage tier-gated.

**Non-goals:** Context-override policy (S10), UI screens (S11/S12), onboarding flow (S11).

**Acceptance:** `./gradlew :app:assembleDebug :app:testDebugUnitTest :domain:test
:platform:test :app:lintDebug` green; `grep -rn "FakeAmbientLux\|BrightnessPolicyEngine\|
EvaluateAndApplyBrightnessUseCase" app domain platform` → empty; STATE.md row notes "GATE 1
READY"; pushed. → **HUMAN GATE 1** (checklist below).

**Failure notes:** Robolectric TileService gaps → downgrade to instantiation-only test +
STATE.md note. If rip-out breaks compilation in ways out of scope, prefer reverting the
specific deletion and logging PARTIAL over leaving the branch red.

---

## S10 — Context override engine

**Model:** Opus / medium (upgraded from Sonnet per D-035) · **Size:** medium ·
**Preconditions:** S2, S7, S9a+S9b DONE. Parallel window C (with S11).

**Objective:** Port the context system: per-app / WiFi / battery / time / location overrides
with serialized cache + daily reset and Tasker-priority-faithful precedence.

**Inputs:** `extraction/contexts_spec.md`, extraction docs 43/623–633/105/26, S7 readers,
S8 rules storage model, `BrightnessPipelineController`.

**Deliverables:** `domain/.../context/ContextOverrideResolver.kt` — pure precedence/merge
logic + table-driven unit tests mirroring the spec's precedence matrix;
`app/.../runtime/ContextEngine.kt` — foreground-app polling (2–3 s) ONLY while screen on ∧
service running ∧ ≥1 app rule configured; battery/wifi via S7 reader flows; time windows
evaluated on pipeline cycles; location = coarse radius check on passive provider; serialized
context cache + daily reset via existing `MaintenanceWorker`; pipeline hook applying resolved
override (scale/min/max/disable per spec) + notification context line; persistence wiring for
rule CRUD (model from S8).

**Non-goals:** Rule-editing UI (S12), AccessibilityService, geofencing, background location.

**Acceptance:** `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug` green;
resolver test matrix matches spec table 1:1; pushed.

---

## S11 — UI shell, onboarding/privileges, dashboard

**Model:** Opus / medium (upgraded from Sonnet per D-035) · **Size:** large ·
**Preconditions:** S7, S8, S9a+S9b DONE. Parallel window C (with S10 — disjoint packages;
rebase before push).

**Objective:** Compose M3 navigation shell (screen set per `screen_map.md`), privilege
onboarding via ActivityResultContracts, live Dashboard. Onboarding parity contract = task563's
8 gates + order (features_spec); the polling-dialog flow itself is a sanctioned deviation
(D-024) — do not port it, and never read Tasker prefs (`adbwp`) in privilege detection.

**Inputs:** `screen_map.md`, existing `app/.../ui/*` + `navigation/*` + `state/
SettingsViewModel.kt`, `PrivilegeManager`, `ServiceHealthStore`, pipeline state flows.

**Deliverables:** rebuilt `AppRoute`/`NavGraph` for the target screens (stubs OK for S12-owned
screens — `Text("S12")` placeholders are acceptable, navigation must resolve);
`ui/onboarding/OnboardingScreen.kt` — stepper: POST_NOTIFICATIONS
(`ActivityResultContracts.RequestPermission`), WRITE_SETTINGS
(`StartActivityForResult(ACTION_MANAGE_WRITE_SETTINGS + package uri)` + `canWrite` re-check in
`onResume`), optional ELEVATED step (adb command with copy button, Shizuku request flow,
root attempt button, live tier badge; all three paths from S7 `PrivilegeManager`; skippable),
usage-access step shown only when app rules exist (deep-link `ACTION_USAGE_ACCESS_SETTINGS`);
`ui/screens/DashboardScreen.kt` rebuilt — live lux + smoothed lux + current/target brightness
(pipeline flows), service master switch (start/stop FGS), pause/resume, active context line,
tier badge → onboarding, service health (last sensor event, degraded flags); M3 dynamic color
+ DayNight; first-run routing → onboarding when tier == NONE.
Also revisit D-019's XML theme workaround (`android:Theme.Material.Light.NoActionBar`):
Compose M3 generates its own theming and needs no Material XML parent — evaluate simplifying
`values/themes.xml` to a minimal/no-op parent (watch the pre-Compose window background +
status-bar look); record the outcome in STATE.md either way (D-027g).

**Non-goals:** Parameter screens, charts, tools, context CRUD (all S12/S13). Notification
styling (S9a owns it).

**Acceptance:** `./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest` green;
Robolectric compose smoke test: launch → Dashboard renders, each route navigates; pushed.

---

## S12 — Settings & tools screens + chart engine core

**Model:** Opus / medium (upgraded from Sonnet per D-035) · **Size:** large ·
**Preconditions:** S8, S10, S11 DONE (S6 for wizard/chart math).

**Objective:** Every parameter/tool/profile screen with Tasker-faithful validation, plus the
reusable Compose-Canvas chart engine with the brightness-curve chart as template instance.

**Inputs:** `screen_map.md` + `extraction/scenes/*` (element dispositions) +
`extraction/tasks/anonymous_handlers.md` (S3.5/D-026: **168 anonymous scene-handler tasks —
every row owned by your screens must end up ported or dropped(reason); includes the 34
`keyTask` rows = per-scene back-key behavior**), S8 `SettingsValidator`, S6
`CurveSuggestionEngine`, existing `LineGraph.kt` (extend or replace — your call, record it),
`extraction/features_spec.md` (calibration/debug — debug selector uses the 10 verbatim labels, D-023).

**Step 0 — handler triage (BEFORE any porting, D-027f):** sweep `anonymous_handlers.md` and
add a disposition-class column committing each of the 168 rows to one of three buckets:
(a) **trivial scene-chrome** (background-rect clicks, scene close/back `keyTask` rows) →
bulk-drop with one shared reason; (b) **settings mutations** (toggle/write a single variable,
EditText value-selected) → dropped(`absorbed-by-compose-state`) — Compose field state +
debounced persist already covers them; (c) **complex** (multi-action handlers with branching/
side effects) → enumerate explicitly; THESE are your 1:1 port list. Commit the triaged file
before starting screens so a follow-up session can resume from it. S13 owns the chart-scene
rows — mark them `deferred-S13`, don't port them.

**Deliverables:** screens per screen_map: Curve & Brightness (min/max/offset/scale, zone ends,
form params + LIVE derived form2A/form3A readout), Reactivity (thresholds incl. midpoint/
steepness, deltaFactor, trust-unreliable), Animation & Dimming (animSteps, min/max wait,
throttle; dimming enable/strength/exponent/threshold/spread, PWM toggle + exponent; ELEVATED-
gated rows disabled w/ tier hint + onboarding link), Dynamic Scale (enable, spread, steepness,
taper midpoint/steepness, transition factor, sun source: location vs manual times), Contexts
(rule list CRUD: app picker via `PackageManager.getInstalledApplications` + launcher-intent
filter, SSID field, time window, charging toggle, per-rule override values), Tools (wizard
runner UI over CurveSuggestionEngine w/ apply-suggestions per task655; power-draw calibration
flow per task524; debug level selector + in-app log view), Profiles (defaults reset, named
profile save/load, export/import via `ActivityResultContracts.CreateDocument/OpenDocument`
using S8 manager incl. legacy Tasker format). EVERY numeric field: outlined text field with
red error state + message from `SettingsValidator` (583/707 parity), debounced persist on
valid. `ui/graph/ChartCanvas.kt`: axes + ticks, linear & log10 x-scale, gridlines, multi-series
polylines, threshold/marker lines, M3 theming, modest size; `ui/graph/BrightnessCurveChart.kt`
— lux→brightness sampled from domain `mapLuxToBrightness` + taper overlay + current-point
marker — written as THE template (clear "copy this pattern" comment for S13/Haiku).

**Non-goals:** The remaining 6 charts and About/Guide (S13). No new chart libraries.

**Acceptance:** `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` green;
validator-to-UI unit test (invalid input → FieldError rendered); screen_map.md + checklist
scene rows flipped for covered scenes; pushed. → **HUMAN GATE 2.**

---

## S12.5 — UI salvage: faithful AAB look & interaction model (a/b/c)

**Model:** Opus / high · **Size:** large (3 sub-segments) · **Preconditions:** S12 merged to main.
**Run setup script** (these are real UI segments). **Origin:** D-045, Gate-2 findings G2-F1..F18.

### READ FIRST — what went wrong in S12 (do not repeat it)

S12 shipped all seven parameter/tool/profile screens green, but Gate 2 (owner, on-device) judged the
result **"miles off the Tasker version."** Root cause: **S12 built a generic Material-3 settings app
and broke CLAUDE.md's prime directive** — *"Tasker semantics win over taste; port behaviour exactly;
modernise the* how, *never the* what." It modernised the *what*. Concretely it: committed every
keystroke instantly (no temporary-preview→Apply, no `[committed]` values), used unbounded free-text
where Tasker has bounded sliders, dropped all Flash/toast feedback, scattered fields across the wrong
screens, did not re-run the pipeline on settings changes, and used default Material dynamic-color
theming with a flat button list instead of AAB's identity.

**This is a UI-LAYER SALVAGE, not a redesign and NOT a domain change.** The engine, runtime pipeline,
chart engine, validator, DataStore models, and test patterns are sound. **KEEP & REUSE** (do not
rewrite): `domain/**` (untouched — hard fence), `app/runtime/**` decision logic, golden vectors,
`ui/graph/ChartCanvas.kt` (public API frozen — S13 depends on it) + `ui/graph/BrightnessCurveChart.kt`
(the template), `settings/SettingsValidator.kt`, `settings/AabSettings*` + mappers, the
stateless-`Content` + stateful-wrapper split, and the Robolectric-compose test pattern (`UiShellTest`,
`SettingsScreensTest`). You are reskinning + rewiring the *presentation*, not the logic.

**Faithfulness contract for every screen:** port the Tasker scene's *behaviour and feel* exactly —
the only sanctioned deviation is the owner-approved 20→9 screen consolidation (`screen_map.md`) and
the D-024 onboarding flow. When the extraction shows a slider, a toast, a bounded range, a help
tooltip, a `[committed]` value, or a specific colour — replicate it. The owner's defect examples
(below) are ILLUSTRATIVE, not exhaustive; cross-check each screen against `extraction/scenes/*.md`
and reproduce what is there.

**Inputs:** `screen_map.md` (+ its S12 status note), `extraction/scenes/*.md` (per-element truth:
types, ranges, sliders, colours, tooltips, handlers), `extraction/tasks/anonymous_handlers.md` (S12
triage — the (c) port list + the help-toast rows), `features_spec.md` (colours 639/379/579/652;
debug 634/635; calibration 524), `defaults_audit.md`, the live S12 screens under `app/ui/**`, and
Gate-2 findings G2-F1..F18 in STATE.md.

---

### S12.5a — Design language + app shell  *(Opus/high · medium)*

**Objective:** Make the app *look* like AAB before touching field behaviour.

**Deliverables:**
1. **Teal + gold theme.** Replace the dynamic-color M3 scheme in `ui/theme/Theme.kt` with AAB's
   palette. **Derive the exact colours from the extraction** (scene background/text/button colours;
   colour tasks 639/379/579/652 — `features_spec.md`/`scenes/*.md`), do NOT invent hex values; record
   the chosen light/dark `ColorScheme` + provenance in STATE.md. Keep DayNight. Dynamic color OFF (or
   only as an opt-in) so the brand identity is stable.
2. **App shell with identity:** the **project name / logo in a top header** (Tasker shows it up top),
   and a **hamburger nav-drawer** (`ModalNavigationDrawer`) that is the rebuild of the **AAB Menu
   scene** (L4462) — every destination reachable from the drawer, current route highlighted. Replace
   the Dashboard's flat `OutlinedButton` list (the `nav_*` testTags) with the drawer; keep a sensible
   Dashboard summary.
3. **Hero cards** for **Profiles** and **Contexts** (and any others the scenes show) — prominent
   summary cards (active profile, active context, quick actions), not plain list rows.
4. Typography/spacing/iconography pass to match the Tasker feel (cards, dividers, accents in gold).
   A small icon set is permitted (declare `androidx.compose.material:material-icons-core` or vectors
   in `res/drawable`); record any new dep in STATE.md.

**Non-goals:** field behaviour, sliders, preview/Apply (that's b). Keep screens functional via the
existing VMs so the build stays green.

**Acceptance:** `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` green; drawer navigates to
every route (update/extend `UiShellTest`); STATE.md records the palette + provenance; pushed.

---

### S12.5b — Interaction model: temporary preview → Apply, sliders, grouping  *(Opus/high · large)*

**Objective:** Port AAB's actual editing model and input controls (owner's binding choice).

**Deliverables:**
1. **Temporary-preview → Apply** (G2-F1). Each parameter screen edits a **draft** `AabSettings`
   (local), the **graph previews the draft live**, and an **Apply** button commits draft→DataStore
   **and re-runs the pipeline**; back/Discard reverts to committed (confirm if dirty). Each field shows
   the **committed/active value in `[brackets]`** when the draft differs (Tasker's `_UpdateStaticScene
   Elements` behaviour). Drafts are per-screen (NavBackStackEntry-scoped VM) so screens don't share a
   draft. Implement the draft/commit/discard/dirty/[bracket] logic in the VM; fix the
   re-seed-mid-edit corruption (G2-F7 — seed field text once per draft epoch, allow empty over forced 0).
2. **Pipeline re-run on Apply** (G2-F16). Add a control-event path (e.g. `AutoBrightnessRuntime.reapply`
   → service action → `BrightnessPipelineController` forced re-evaluate; an UNLIMITED control event, not
   a dropped sensor tick) so an Apply/profile-load takes effect immediately even with no new sensor
   reading. (Reuse/extend the existing `onContextChanged`/`reapplyProfile` plumbing.)
3. **Bounded sliders** (G2-F3/F13) for the slider-backed settings — get the **definitive 6 sliders +
   ranges from `extraction/scenes/*.md`** (D-017/D-018 note "6 Slider"; known: animSteps 0–100,
   scaleTaperMidpoint 130–240) and render those as M3 `Slider` with the exact ranges; keep EditText for
   the rest. Record the slider/field split (with provenance) in STATE.md.
4. **Faithful field grouping** (G2-F2): re-add a **Misc / General** screen and move
   min/max/offset/scale + animation (steps, min/max wait, throttle) + notifications/debug there per the
   extraction (these are Misc-scene fields), keeping the 9-screen `screen_map` consolidation otherwise.
   Update `AppRoute`/drawer/`screen_map.md` accordingly.
5. **Validation parity:** dangerously-low-scale warning (G2-F5); zone2End<zone1End NaN guard + warning
   (G2-F6); confirm form2C>zone1End / safety / wait-order warnings still fire. PWM-sensitive ⊻ super-
   dimming **mutual exclusivity** (G2-F10 — enabling one disables the other, per `_DimmingUIToggle`/
   `_PWMToggle`). Dim-spread **gated on circadian/scaling enabled** + correct label (G2-F11).

**Non-goals:** context-editor rebuild, toasts, debug toasts (that's c).

**Acceptance:** `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` green; a unit test proves
draft→Apply commits + back discards + `[bracket]` shows committed; slider ranges asserted; pushed.

---

### S12.5c — Feature & behaviour fidelity  *(Opus/medium · medium)*

**Objective:** Close the remaining behavioural gaps so Gate 2 can pass.

**Deliverables:**
1. **Context-rule editor fidelity** (G2-F14): fix the foreground-app list (Android 11+ package
   visibility — add a `<queries>` LAUNCHER block or `QUERY_ALL_PACKAGES` with justification) and show
   **app icons + labels**, sorted; add a **"use current SSID"** helper (Wi-Fi reader); expose
   **SUNRISE/SUNSET** tokens for from/to (the resolver already accepts them); **prompt for usage access**
   when an app trigger is added (deep-link `ACTION_USAGE_ACCESS_SETTINGS`).
2. **Toasts** (G2-F12): restore Flash/toast feedback where Tasker uses it — confirmations, save/apply,
   warnings (the (c) handler rows + help longclicks). Help tooltips may stay as supportingText, but
   action confirmations/warnings should toast (an in-app Toast/Snackbar wrapper is fine).
3. **Debug level → runtime toasts** (G2-F15): wire the 10 `%AAB_Debug` categories (D-023) so selecting
   a level produces the corresponding runtime debug toasts (in the pipeline/service). Surface
   `%AAB_Test` curve-wizard diagnostics → clipboard (D-025) in Tools.
4. **G2-F8 bug — profile load disables manual-override detection.** Find why applying/loading a profile
   leaves `DetectOverrides` off at runtime (the profile-merge / `effectiveSettings` swap or the
   controller reapply dropping `detectOverrides`) and fix so overrides keep working after a profile load.
5. **Super-dimming engagement** (G2-F9): investigate why ELEVATED reduce_bright_colors does not engage
   (likely the no-re-eval root [fixed in b] and/or OEM secure-key differences); verify the
   `SuperDimmingCoordinator` engages below threshold. If it is purely OEM-key variance not reproducible
   here, document precisely for the device gate.
6. **QS tile** reflects paused/running state (G2-F17).

**Non-goals:** the six non-template charts + About/Guide (still S13); on-device power-draw measurement
(Gate/S13). Override-point capture for the wizard (D-044c) may be picked up here or deferred — record.

**Acceptance (S12.5 overall):** `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug :domain:test
:platform:test` green; the relevant Gate-2 findings flipped in STATE.md with how each was addressed;
`screen_map.md`/checklist updated; pushed. → **re-run HUMAN GATE 2.**

---

## S12.6 — Gate-2 re-test salvage: IA, glass-box diagnostics, behaviour & profiles (a–e)

**Model:** Opus / high (all sub-segments) · **Size:** large (5 sub-segments) · **Preconditions:** S12.5c
merged to main. **Run setup script.** **Origin:** the Gate-2 RE-TEST (STATE.md "Gate 2 RE-TEST"),
findings **G2R-F1…F25**. Same prime directive as S12.5: **port Tasker behaviour/feel exactly; modernise
the *how*, never the *what*.** UI/app/platform-glue layer only — **domain/, golden vectors and the
`ChartCanvas` public API stay fenced** (the chart-overlay work uses ChartCanvas as-is).

### Owner decisions (binding — from the re-test Q&A; do NOT re-litigate)
1. **Menu = a real home screen.** The AAB Menu becomes a navigable destination and the app hub; the
   Dashboard becomes a separate live-status screen reached from the menu. Back from any settings screen
   returns to the **menu**, not the Dashboard. (G2R-F1/F2.)
2. **Block Apply on critical validation errors** (form2A<0, form3A<0, form2C>zone1End) — a SANCTIONED
   deviation from Tasker's advisory model (D-015): keep reddening, but disable Apply while a critical
   error stands. Advisory-only rules (safety@1000lux, wait-order, low-scale) still just warn. (G2R-F18.)
3. **All profiles are editable saved entries** + a **"Restore factory profiles"** action; the 5 built-ins
   are seeded once and may be overwritten. (G2R-F15.)
4. **Legacy import via a SAF folder grant** to `Download/AAB/configs` (one-time `OpenDocumentTree`), then
   list/load the JSONs there — NO `MANAGE_EXTERNAL_STORAGE`. (G2R-F16.)

**Inputs:** `extraction/scenes/*.md` (per-element labels, **long-press help text**, diagnostic-card
contents, colours), `features_spec.md` (debug 634/635; colours 639/379/579/652; `_GetWifiNoLocation`
task105/633), `defaults_audit.md`, `pipeline_spec.md` (which `%AAB_*` runtime vars exist + lifecycle),
the live S12.5 screens, STATE.md G2R-F1…F25 + D-046/047/048. **Re-read scene docs for every label/tooltip
you touch — do not invent help text or rename a control without the extraction.**

**Sub-segment ordering:** S12.6a FIRST (renames + the menu-as-home reshape touch every screen's nav and
all testTags). Then S12.6b/c/d/e — mostly disjoint; if run in parallel sessions, rebase before push and
expect STATE.md/screen_map append-merges. Each sub-segment ends green on the full ladder + pushes; the
**owner re-tests Gate 2 again after S12.6e**.

---

### S12.6a — Information architecture & naming  *(Opus/high · medium)*

**Objective:** Make the AAB Menu the home/hub screen, fix navigation, and rename two screens to match
Tasker — before any behaviour work depends on the new structure.

**Deliverables:**
1. **AAB Menu as a home screen** (G2R-F1/F2). Promote the `AabNavDrawer` content (S12.5a/D-046) into a
   real `AppRoute.Menu` destination = the app hub: branded gold-sun teal banner + the grouped
   destinations + the **Profiles & Contexts hero cards** (moved OFF the Dashboard). Make `Menu` the
   start destination after onboarding (first-run routing → Onboarding when tier==NONE, else Menu). A
   slide-over drawer MAY remain for quick nav (owner allowed it), but the Menu screen is the canonical
   hub and the **back-target from every settings/tool screen** (`SettingsScaffold`/`DraftSettingsScaffold`
   back → Menu, not Dashboard). The Dashboard stays a destination (live status), reached from the Menu.
2. **Renames** (G2R-F3/F4): `Animation & Dimming` → **"Super Dimming"** (its content is super dimming +
   PWM after S12.5b); `Dynamic Scale` → **"Circadian"**. Rename `AppRoute` entries, screen titles, drawer/
   menu labels, `screen_map.md`, and any testTags/tests that key on the old names. Keep the routes' content.
3. **Dashboard "Last sensor sample: never" fix** (G2R-F5). Wire a real last-sample timestamp: the
   pipeline already sees each `LightSample` — store its time in `PipelineState` (e.g. `lastSampleMs`),
   republish via `LiveRuntimeState`, and render it (relative "Xs ago") on the Dashboard. Confirm it
   advances while the service runs.

**Acceptance:** `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` green; `UiShellTest` updated
for Menu-as-home + the renames (Menu is start dest; back from a settings screen lands on Menu; renamed
routes resolve); STATE.md + screen_map updated; pushed.

---

### S12.6b — Glass-box diagnostics + Live Debug scene  *(Opus/high · large)*

**Objective:** Surface the runtime "glass box": a dedicated Live Debug screen + per-screen diagnostic
cards driven by the live pipeline state, and make the debug selector a global control.

**Deliverables:**
1. **Live Debug Info screen** (`AppRoute.LiveDebug`, in the Menu) (G2R-F6): a glass-box readout of the
   live `%AAB_*` runtime vars from `LiveRuntimeState`/`PipelineState` — smoothed/raw lux, dynamic
   threshold, absolute dead-band low/high, uncompressed + compressed dynamic scale, current/target
   brightness, cycle time, last sample age, active context, paused/override state. Group + label per the
   Tasker debug scene; values in gold `#FFC107`.
2. **Per-screen diagnostic cards** (G2R-F7/F8): a reusable `DiagnosticCard` reading the live state,
   embedded on the relevant parameter screens. At minimum Reactivity ("Current threshold
   `[%AAB_ThreshDynamic]` at `[%SmoothedLux]` lx; Sensor dead zone `[%AAB_ThreshAbsLow]`–`[%AAB_ThreshAbsHigh]`
   lx") and Circadian ("Uncompressed scale `[%AAB_ScaleDynamic]` at `[%TIME]`; True scale
   `[%AAB_ScaleDynamicCompress]` at `[%AAB_CurrentBright]` brightness (`%AAB_MinBright`–`%AAB_MaxBright`)").
   This needs `%AAB_ScaleDynamic` (uncompressed) + `%AAB_ThreshDynamic` surfaced from the engine output
   into `PipelineState` — extend the runtime state holder (NOT the domain engine API) to carry them; if a
   value is only produced inside `BrightnessEngine.evaluate`, return it on the existing output type only
   if that is already exposed, else compute it in the controller from existing outputs (record the choice).
3. **Debug selector → global + relocated** (G2R-F9): move the 10-category selector OFF Misc onto the Live
   Debug screen, and make `debugLevel` a GLOBAL setting that does NOT change on profile load — preserve it
   in `applyProfile`/`replaceAll`/`mergeProfile` exactly like `detectOverrides` (S12.5c D-048a). (It is
   already preserved in `mergeProfile`; add it to the profile-apply paths + remove it from the per-profile
   reset.)
4. **Teal debug toasts** (G2R-F10): style the runtime debug output in the AAB teal. System Toasts can't be
   recoloured reliably → use a custom toast view (inflated teal layout) or an in-app teal Snackbar/overlay
   from `ToastDebugSink`; keep the category label.

**Acceptance:** `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` green; a test that the Live
Debug screen + a diagnostic card render live values from a seeded `PipelineState`, and that `debugLevel`
survives a profile apply; STATE.md/screen_map/checklist updated; pushed.

---

### S12.6c — Pipeline behaviour correctness  *(Opus/high · large)*

**Objective:** Fix the runtime bugs the re-test found, and wire manual-override-point capture so the curve
overlay + wizard have real input.

**Deliverables:**
1. **Apply / profile load takes effect without a light change** (G2R-F11). Diagnose the reapply path:
   `reapply()` → `ContextChanged` → `reapplyProfile()` calls `setInitialBrightness(settingsProvider())`,
   where `settingsProvider = contextEngine.effectiveSettings()` returns the possibly-STALE `_effective`
   snapshot (last context eval), so a manual DataStore write isn't reflected until the engine re-evaluates
   (the D-047 bounded-edge). Fix so a manual Apply/profile-load re-reads the FRESH baseline (e.g. reapply
   re-seeds `_effective` from the baseline / forces a `ContextEngine` re-eval before `setInitialBrightness`).
2. **Min brightness honoured at runtime** (G2R-F12 — graph already honours it, runtime "stuck at 10").
   Likely the same stale-settings root as (1), or a mapper/clamp not threading `minBrightness`. Trace
   `minBrightness` from `AabSettings` → mapper → engine → applied brightness and fix; add a regression test
   (a low/high `minBrightness` changes the applied target with a fixed lux).
3. **Manual override-point capture + persistence** (G2R-F13, D-044c): persist the override points the
   pipeline already detects (`OverrideRules.recordOverridePoint` → `PipelineState.overrideHistory`) into a
   store the Tools wizard + the curve chart can read. (lux,brightness) pairs; cap per Tasker.
4. **Curve chart overlays recorded override points** (G2R-F14): `BrightnessCurveChart` plots the recorded
   points as markers; the fitted/suggested curve shows only when ≥ 9 points exist (task38 threshold) — use
   the existing `ChartCanvas` marker API (do not modify ChartCanvas).
5. **Fix manual-override FALSE POSITIVES on rapid light changes** (G2R-F26, added 2026-06-14; full
   code-grounded analysis in STATE.md **D-049**). A fast lux swing pauses the pipeline as if the user moved
   the slider. Primary suspect: `BrightnessPipelineController.handleOverride` commits the pause immediately,
   omitting Tasker task567 act8's **cycle-time settle wait + re-read** ("before the new brightness has taken
   hold"). Add that settle/re-check; then harden the single-latest self-write marker
   (`ScreenBrightnessController.isSelfWrite`) and the OEM-range round-trip drift in `AnimationRunner`'s
   read-back (compare in device space / with tolerance — see D-049 #2/#4). Add a regression test: a rapid
   from→to with interleaved self-writes must NOT emit `OverrideDetected`, but a genuine external write after
   settling MUST. Stay within the single-pipeline-coroutine model (D-027).
6. **PWM-sensitive: lock the hardware brightness floor** (G2R-F27, added 2026-06-14; full code-grounded
   analysis in STATE.md **D-050**). `pwmSensitive` is persisted + toggled in the UI but **never read by the
   pipeline**, so the task661 act22-26 / task698 floor is unimplemented: when "Use software dimming
   (PWM-sensitive)" is on and the target falls below `dimmingThreshold`, the *hardware* brightness must be
   floored at the threshold (task698 step 3: `hardwareTarget = max(round(target), round(dimmingThreshold))`
   when below), with further darkening left to the secure/overlay layer — not by writing a lower hardware
   value. Wire this as a controller/coordinator-level clamp (mind OEM range normalization, cf. D-049 #4).
   The broader unprivileged software-overlay path stays deferred (D-040); the hardware floor is independent.
   Add a regression test: `pwmSensitive=true` + target below threshold ⇒ applied hardware brightness equals
   the threshold, not the raw target.

**Non-goals:** the curve-fitting math (S6, golden), ChartCanvas internals. **HARD FENCE: domain/ untouched.**

**Acceptance:** `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug :domain:test :platform:test`
green; regression tests for the reapply-uses-fresh-settings + min-brightness paths + override-point
persistence + the rapid-light-change override false-positive (G2R-F26/D-049) + the PWM-sensitive
hardware-floor clamp (G2R-F27/D-050); STATE.md/checklist updated; pushed.

---

### S12.6d — Profiles: save / overwrite / factory-restore + legacy import + reset + validation gate  *(Opus/high · large)*

**Objective:** Real user-profile management and legacy import, per the owner decisions.

**Deliverables:**
1. **User-editable profiles + overwrite + factory restore** (G2R-F15, owner-decision 3): a profile store
   (DataStore) of named `AabSettings`; "Save current as…", overwrite an existing entry (including the 5
   built-ins, which are seeded once into the store), and a **"Restore factory profiles"** action that
   re-seeds the built-ins from `DefaultProfiles`. Extend `AppProfileCatalog` to read the store so context
   rules can target user profiles too (closes the D-042c "unknown rule.profile → null" gap). Preserve the
   global fields (serviceEnabled/contextOverride/detectOverrides/debugLevel) on apply (S12.5c D-048a).
2. **Legacy import via SAF folder grant** (G2R-F16, owner-decision 4): `OpenDocumentTree` to grant
   `Download/AAB/configs` once (persist the URI permission), then list the `*.json` there and import via
   the existing `TaskerLegacyProfileSerializer`/`ProfileImportExportManager`. Keep the single-file picker as
   a fallback. NO `MANAGE_EXTERNAL_STORAGE`.
3. **Reset-to-defaults on every settings screen** (G2R-F17): a per-screen reset that restores that screen's
   fields to the task570 baseline (reuse `SettingsViewModel.resetDefaults` semantics, scoped) + a toast.
4. **Block Apply on critical validation errors** (G2R-F18, owner-decision 2): `DraftApplyBar` Apply is
   disabled while a CRITICAL error (form2A<0, form3A<0, form2C>zone1End) is present on the draft (keep the
   red field state); advisory rules still only warn. Document this as a sanctioned deviation from Tasker's
   advisory model (new D-0xx), since CLAUDE.md says Tasker semantics win — this one is owner-overridden.

**Acceptance:** `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` green; tests for save/overwrite/
factory-restore round-trip, the Apply-disabled-on-critical-error gate, and per-screen reset; STATE.md/
checklist updated; pushed.

---

### S12.6e — Labels & long-press help audit + context editor + onboarding  *(Opus/high · large)*

**Objective:** Make every label/tooltip faithful (and verify the wiring behind suspected-wrong labels),
finish the context editor's Wi-Fi/location/usage-access, and fix onboarding.

**Deliverables:**
1. **Label + long-press-help audit** (G2R-F19/F20/F21): for EVERY parameter control, cross-check the label
   AND the Tasker long-press help text from `extraction/scenes/*.md` (the help is stored as scene long-press
   triggers). Port the help as a long-press/tooltip (or visible supportingText) verbatim per scene; where a
   label disagrees with the extraction, FIX the label, and **verify the control is wired to the correct
   `%AAB_*` variable + runtime use** (a wrong label often means a wrong binding — e.g. *delta factor* whose
   Tasker help describes sensor-smoothing reactivity). Any label↔behaviour mismatch that turns out to be a
   real wiring bug → fix + record (this is parity-critical; never guess, re-derive from extraction).
2. **Context editor Wi-Fi + live location** (G2R-F22): make "use current Wi-Fi" actually return the SSID
   (the `_GetWifiNoLocation V3` path — task105/633; confirm the FINE_LOCATION runtime grant + that
   `currentSsid()` reads the connected SSID, not null), and add a "use current location" helper for
   location rules.
3. **Usage-access flow** (G2R-F23/F24): the rule editor's usage-access control must not be dead/greyed —
   surface the same instruction/deep-link the onboarding uses; and the onboarding/setup screen must present
   **usage access as OPTIONAL by default** (only required once an app rule exists, per D-024/task563).
4. **Toast on rule/profile load** (G2R-F25): a confirmation toast when a context rule loads its profile
   (runtime) and when a profile is applied from the Profiles screen.

**Non-goals:** new charts/About (S13); power-draw measurement.

**Acceptance:** `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug :domain:test :platform:test`
green; tests for the SSID helper returning a value, the usage-access prompt state, and at least one
label/tooltip rendering from the extraction; **every G2R-Fn flipped in STATE.md with how it was
addressed**; screen_map/checklist updated; pushed. → **re-run HUMAN GATE 2.**

---

## S12.7 — Gate-2 re-re-test salvage: runtime correctness, context, permissions, debug, fidelity (a–h)

**Model:** Opus / high (ALL sub-segments) · **Size:** large (8 sub-segments) · **Preconditions:** S12.6
merged to main. **Run setup script.** **Origin:** the Gate-2 RE-RE-TEST (STATE.md "Gate 2 RE-RE-TEST"),
findings **G2R-F33…F68** + the owner answers logged there. Same prime directive: **port Tasker behaviour/
feel exactly; modernise the *how*, never the *what*.** UI/app/platform-glue layer only — **domain/ + golden
vectors stay fenced; `ChartCanvas` may be extended ONLY in S12.7g** (the chart sub-segment).

### Binding owner answers (from the re-re-test Q&A; do NOT re-litigate)
1. **Transcribe the real task567 override logic** from the XML (XML_RECIPES R2) — do NOT approximate. The
   intended behaviour: a manual override is a brightness change that is **inconsistent with our own in-flight
   animation step** (wrong direction, or magnitude beyond our step), checked **target-vs-actual**; while an
   animation is mid-flight there is a **mutex** so our own per-frame writes never self-trigger. (F34/F64.)
2. **Manual profile load = an override** (latch `%AAB_ContextOverride`, show it in the Menu, Resume clears
   it). **A context rule being active is NOT an override** — never label it one. (F46.)
3. **Global toasts via an AccessibilityService** are sanctioned (distribution is F-Droid/GitHub, not Play
   Store). It must be optional/opt-in with a clear rationale screen. (F50.)
4. **Wi-Fi SSID without Location**: prefer `_GetWifiNoLocation V3` (task105/633) — Shizuku
   `cmd wifi status | grep "Wifi is connected to"` then `dumpsys wifi | grep mWifiInfo | grep COMPLETED`
   with a regex; the Location `NetworkCallback` path is the **last** fallback. Guide the user on granting
   `DUMP` / using Shizuku. (F41.)

**Sub-segment ordering:** S12.7a FIRST (override-engine correctness underpins the service surfaces in b and
the override semantics that c/h depend on). b–h are largely disjoint; if run in parallel sessions, rebase
before push and expect STATE.md/screen_map append-merges. Each ends green on the full ladder + pushes; the
**owner re-tests Gate 2 again after S12.7h**. Re-read the cited extraction docs/tasks before each fix; do
not invent behaviour.

---

### S12.7a — Manual-override engine correctness *(Opus/high · large)*

**Objective:** Eliminate override false-positives and the spurious instant-override-on-start, by porting
Tasker's actual override-detection logic and fixing the start/reinit race.

**Deliverables:**
1. **Transcribe task567** `_DetectManualOverride` (+ task525/526 toggle, prof755 gate) from the XML and
   re-implement `OverrideMonitor`/`handleOverride` to match: a **target-vs-actual delta check** (compare the
   observed brightness against our last-applied target — same direction & within our step ⇒ ours; opposite
   direction or beyond ⇒ override), gated by an **animation mutex** so per-frame self-writes never trigger
   (F34). Supersedes the S12.6c settle-wait (D-049/D-051d) where they conflict; record the transcription.
2. **Kill the instant-override-on-start race** (F64): override detection must be **suppressed until the
   first `setInitialBrightness` self-write has settled** on service start, screen-on reinit, resume, and
   QS-on. Repro path: QS Off→On / display off→on landing in override/paused.
3. **Override semantics** (F46): a **manual profile load latches the override** (Menu reflects it, Resume
   clears it); a context rule being active is **not** an override. Align `ContextEngine`/Menu/labels.

**Acceptance:** ladder green; a regression test that a from→to animation with interleaved self-writes emits
NO override but an opposing external write does; a test that start/reinit does not emit a spurious override;
STATE.md flips F34/F64/F46. **Fence: domain/ untouched.**

---

### S12.7b — Runtime feedback surfaces: notification, QS tile, super dimming *(Opus/high · medium)*

**Deliverables:**
1. **Override notification + toast** (F35): a manual override posts a **high-priority notification with
   vibration** + a toast (Tasker parity).
2. **Notification Resume action** (F40): add Resume alongside Pause; reflect paused/override state.
3. **QS tile live state** (F63): `Tile.updateTile` on every state change + refresh on `onStartListening`
   (+ `requestListeningState`) so Off→Starting→Active renders without reopening the panel.
4. **Super dimming Extra-Dim fix** (F65): the ELEVATED secure `reduce_bright_colors` engage path locks the
   PWM floor but never actually dims — diagnose `SuperDimmingCoordinator`/`AndroidSecureDimmingController`
   (is the secure key written? is engage gated out?) and make Extra Dim apply below the threshold.

**Acceptance:** ladder green; tests for the override notification (high-priority + action) and QS tile state
mapping; Super-dimming engage path covered/diagnosed; STATE.md flips F35/F40/F63/F65.

---

### S12.7c — Context system: location lifecycle, ordering, legacy targets, days *(Opus/high · large)*

**Deliverables:**
1. **Foreground/zombie-guarded location listener** (F45): the listener must survive backgrounding (it dies
   instantly today → reverts to no-rule); fix the **0.0,0.0** reads; **debounce to ≥100 m** changes so the
   debug toasts aren't near-constant / input-blocking.
2. **use-current-location perm recheck** (F42): re-check the grant at call time (it wrongly reports
   not-granted; cf. the permission-propagation delay the owner saw).
3. **Rule list ordered by priority** (highest first), not creation time (F43).
4. **Legacy-imported profiles as rule targets** (F44): a profile imported from `Download/AAB/configs` must
   register into the catalog the rule editor reads (extends D-042c) without a manual re-save.
5. **Day-of-week rules + smart midnight wrapping** (F67): expose `ContextTriggers.days` in the editor; the
   resolver already supports it (D-014) — verify the overnight/midnight-wrap interaction.
6. **Sunrise/Sunset show the resolved time** (F68): the tokens display today's computed time in theme gold
   (e.g. "SUNRISE (06:42)").
7. **Context-automation debug toasts** (F47): on app switch / auto profile load, flash **trigger, context,
   profile, rule (with priority)** under the Context Automation category.

**Acceptance:** ladder green; tests for priority ordering, legacy-target visibility, the day picker, and the
sunrise-value formatting; STATE.md flips F42/F43/F44/F45/F47/F67/F68. **Fence: domain/ untouched.**

---

### S12.7d — Permissions, Wi-Fi acquisition, first-boot nav *(Opus/high · medium)*

**Deliverables:**
1. **Restricted-settings flow** (F33): detect the Android "restricted settings" block for sideloaded apps
   and instruct the user to "Allow restricted settings" (App info) before the grant flow.
2. **Location grant in Setup** (F41): add a Location-permission step (needed for the SSID fallback + context
   location).
3. **Wi-Fi SSID without Location** (F41): implement the `_GetWifiNoLocation V3` order — Shizuku
   `cmd wifi status` → `dumpsys wifi` (DUMP) → Location `NetworkCallback` last; surface grant guidance for
   DUMP/Shizuku. Keep the typed `SsidResult` messaging.
4. **First-boot navigation** (F57): after granting permissions the app must land on the **Menu** hub (not a
   dead Dashboard); Back must navigate, not close the app.

**Acceptance:** ladder green; tests for the SSID-source selection order (fake Shizuku/dump/callback) and the
post-onboarding route to Menu; STATE.md flips F33/F41/F57.

---

### S12.7e — Debug / toast infrastructure *(Opus/high · medium)*

**Deliverables:**
1. **Accessibility global toasts** (F50): an opt-in `AccessibilityService` that shows the AAB flash messages
   system-wide (foreground-only today). Rationale/opt-in screen; degrade gracefully when off.
2. **Toasts cancel, not stack** (F51): each new debug toast cancels the previous; disabling a category stops
   its queue immediately.
3. **Instant debug-off** (F52): the selector applies immediately (no back-out needed).
4. **Dynamic-scale debug timing** (F48): fire only **~2 min into a dawn/dusk transition**, not on every
   light change.
5. **Overlay-preview colour toast** (F49): the privilege≈none overlay-preview category toasts the overlay
   colour.

**Acceptance:** ladder green; tests for toast-cancel + instant-off + the dynamic-scale timing gate; STATE.md
flips F48/F49/F50/F51/F52.

---

### S12.7f — Per-screen live readouts + label/value fidelity *(Opus/high · medium)*

**Deliverables (re-derive every string/value from extraction/scenes):**
1. **Live readout blocks** on every settings screen (F58): big-bold label + gold value, e.g. Curve &
   Brightness → "Current smoothed lux [%SmoothedLux]" + "Current brightness (%AAB_MinBright–%AAB_MaxBright)
   [%AAB_CurrentBright]"; Misc → "Current throttle [%AAB_Throttle] ms" + "Current smoothing α [%LuxAlpha]".
2. **Reactivity threshold as a percentage** (F56): live reactivity shows 0.5 → "50%".
3. **Dynamic-threshold description** (F59): substitute the live `%AAB_ThreshDynamic` value (not the literal)
   and move it behind the ⓘ reveal like the others (or make it a live readout).
4. **Misc "Scale" → auto dynamic-scale readout** when circadian scaling is enabled (F60).
5. **form2A/form3A labels** → "Zone 2 alignment" / "Zone 3 alignment" (F61).

**Acceptance:** ladder green; tests that the live-readout block renders seeded values + the percentage
formatting + the form2A/3A labels; STATE.md flips F56/F58/F59/F60/F61. **Fence: domain/ untouched.**

---

### S12.7g — Charts & curve view *(Opus/high · large — MAY extend ChartCanvas)*

**Note:** this is the one sub-segment allowed to extend `ChartCanvas` (axis labels + interactive readout are
engine features); coordinate with S13 (which copies this template). domain/ + golden vectors still fenced.

**Deliverables:**
1. **Axis labels + log-x** (F55): label both axes; the lux x-axis is **log** starting at **0.1**; pick
   logical y ticks (no 191.25 artefacts).
2. **Interactive scrub** (F55): drag a finger across the chart to read the current + reference values at the
   touch point (Chart.js parity).
3. **Reference-line legend** (F66): legend distinguishing the live curve from the dashed gold reference line.
4. **Curve fitting on the curve view** (F62): show the wizard's suggested curve **on the Curve & Brightness
   chart** against the recorded points + reference, so the impact is visible (not Tools-only).
5. **Tap-to-delete override points** (F36): points are larger tap targets; tapping shows the lux/brightness
   pair and confirms deletion (writes back to `OverridePointStore`).
6. **Fixed reference line** (F69): the dashed gold reference line on the Curve & Brightness chart currently
   **moves as the user edits field values** — it must be a FIXED snapshot of the committed/default curve so
   draft edits show *against* it. Sample the reference series from a committed/default settings snapshot, not
   the live draft; the live curve tracks the draft.

**Acceptance:** ladder green; tests for the log-x/tick mapping, the legend, and override-point deletion;
STATE.md flips F36/F55/F62/F66/F69; screen_map/checklist updated. **S13 scope note:** after this, S13 = the
remaining charts (copying this richer template) + About/User Guide static screens.

---

### S12.7h — Rich editors / scene fidelity *(Opus/high · large — owner-facing polish)*

**Inputs:** `extraction/scenes/profile.md`, `menu.md`, `experiment_settings.md` + the scene HTML for the
load/save/create-rule/edit-rule modals.

**Deliverables:**
1. **Polished profile/context modals** (F38): rebuild the load / save / create-rule / edit-rule dialogs to
   match Tasker's feel; show a **full list of every setting with its value, gold-highlighting any value
   changed vs default**; make **context resume** as smooth as Tasker.
2. **Circadian Date/Lat/Lon element** (F39): a Date + Latitude + Longitude editor on the Circadian screen;
   unset → defaults to **today + current location** (reuse the S12.7c location helper).

**Acceptance:** ladder green; tests for the changed-vs-default gold highlighting + the Circadian date/loc
defaults; STATE.md flips F38/F39; screen_map/checklist updated. → **re-run HUMAN GATE 2 (4th).**

---

### S12.7i — During-S12.7 deferral cleanup *(Opus/high · medium)*

**Model:** Opus / high · **Size:** medium · **Preconditions:** S12.7a–h merged to main. **Run setup
script.** **Origin:** four findings the owner logged *during* the S12.7 work and deferred (STATE.md
G2R-F70/F71/F72/F73). Same prime directive: **port Tasker behaviour exactly; modernise the *how*, never
the *what*.** UI/app/runtime-glue only — **domain/ + golden vectors stay fenced.** Re-read the cited
XML/extraction before each fix; do not invent behaviour.

> **F73 turned out APP-LAYER, not a domain bug — already landed (2026-06-15).** The suspected
> UTC-vs-local issue was real *in spirit* but the root cause was that the live pipeline never wired real
> solar windows: `BrightnessPipelineController.buildInput` fed the dynamic-scale engine `TimeContext`'s
> **fixed defaults** (6–8am / 18–20pm *UTC* morning/evening), so "morning" was pinned to 06–08 UTC = a
> couple hours late in local time → `%AAB_ScaleDynamic` 0.852 at 06:13 UTC regardless of the user's clock
> (07:13 at UTC+1, 08:13 at UTC+2 — both 06:13 UTC, the owner confirmed both). The fix wires the
> already-fenced, golden-tested `SolarCalculator.compute`/`buildScheduleWindows` (new
> `CircadianWindowProvider`, F39 date/loc override aware) into `buildInput`; `now` stays UTC seconds-of-day
> to match `buildScheduleWindows`' UTC-frame windows. **domain/ + golden vectors untouched.** Verified:
> Utrecht 2026-06-15 sunrise 05:18 local → scaleDynamic 1.15 at 08:13 local (was 0.852). Done as the first
> S12.7i deliverable; remaining work below.

**Deliverables:**
0. **(DONE 2026-06-15) Circadian solar-window wiring** (F73): `buildInput` now feeds the real
   sunrise/sunset ramp windows. `CircadianWindowProvider` (+ pure `compute`) + `CircadianWindowProviderTest`
   (Utrecht bug→fix regression). See the box above; domain/ fenced.
1. **Legacy load actually applies** (F70): importing a legacy Tasker `.json` (Profiles → legacy folder/file
   load) currently registers the profile + latches the context override but **never commits the parsed
   settings into the active DataStore + re-evaluates**, so nothing changes on screen. Make a legacy load
   commit the parsed `AabSettings` exactly like a profile apply — `SettingsViewModel.replaceAll(parsed)`
   (or equivalent) **+ `AutoBrightnessRuntime.reapply`** — in addition to the existing
   `saveImportedProfile` registration. Verify `LegacyConfigImporter`/`TaskerLegacyProfileSerializer`
   actually parse the file's values (not just the name). **App-layer.**
2. **Reactivity cooldown must not swallow manual overrides** (F71): the runtime gates the reactivity
   cooldown on a fixed `%AAB_Throttle` window, and a genuine manual override during that window is
   missed/suppressed. **Re-derive the real task543/task567 cooldown-vs-override interaction from the XML
   (XML_RECIPES R2)** and re-implement so an override is still detected during the cooldown — do NOT
   approximate. Likely touches `BrightnessPipelineController`/`OverrideMonitor` (distinct from S12.7a's
   detector work: this is the cooldown gate). Record the transcription. **domain/ stays fenced.**
3. **Clear/remove a time rule from a context** (F72): in the context rule editor a From/To time, once set,
   cannot be unset back to "no time constraint". Add a "Clear time" affordance that nulls the time range
   (`ContextTriggers.timeRange` → null) so a time-constrained rule can become time-agnostic again.
   **App-layer (ContextsScreen rule editor).**

**Acceptance:** ladder green; tests for (1) a legacy import committing parsed values + triggering reapply,
(2) an override detected during the throttle/cooldown window (controller/monitor test against the
transcribed task543/567 logic), (3) the clear-time affordance nulling `timeRange`; F73 already has its
regression test; STATE.md flips F70/F71/F72/F73; checklist updated. **Fence: domain/ + golden vectors
untouched.**

---

## S12.8 — Gate-2 4th-re-test salvage: runtime, UI, settings/profiles, circadian (a–d)

**Model:** Opus / high (ALL sub-segments) · **Size:** large (4 sub-segments) · **Preconditions:** S12.7
merged to main. **Run setup script.** **Origin:** the **Gate 2 (4th re-test)** on-device against the S12.7i
build (OnePlus 13 / Android 16) — findings recorded in STATE.md "Gate findings → Gate 2 (4th re-test)":
reopens with corrected specs (**F39/F62/F65/F70/F73**), follow-ups (**F35/F50/F58/F59/F68**), still-open
(**F45/F67/F71**), and new findings **G2R-F74…F89** (incl. **CRITICAL F85**). Same prime directive: **port
Tasker behaviour/feel exactly; modernise the *how*, never the *what*.**

### Binding owner decisions (from the 4th-re-test planning Q&A; do NOT re-litigate)
1. **Segmentation = 4 larger sub-segments** (Runtime · UI · Settings/Profiles · Circadian), below.
2. **F86 negative LuxAlpha = clamp the DISPLAY only.** The domain formula is intentional Tasker parity
   (task535 unclamped, D-010a) — `domain/` stays untouched; only the Live-Debug/readout rendering clamps ≥0.
3. **F83 circadian location = FULL parity.** Port Tasker's once-a-day location refresh (briefly enable
   location via `WRITE_SECURE_SETTINGS` when `%AAB_SunLastDate != %DATE` — ELEVATED), **skip** when fixed
   lat/lon set (F39), final fallback **ip-api.com** (`http://ip-api.com/json`).

**Stage-wide fence:** `domain/` + golden vectors + `ChartCanvas.kt` are **NOT modified** — they may only be
*called* (`SoftwareDimming.dimShell`, `SolarCalculator`, and `BrightnessCurveChart` already exposes a
`fittedCurve` param, so no ChartCanvas change is needed). Each sub-segment ends green on the full ladder
(`:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :domain:test :platform:test`), updates STATE.md +
PARITY_CHECKLIST, and pushes on its own session branch.

**Sub-segment ordering / rebase:** the three chart-host screens (Reactivity / SuperDimming / Circadian) are
touched by multiple sub-segments — land **a → c → d**, then **b last** (its graph-placement reshape rebases
onto the settled screen content). Expect STATE.md append-merges.

**Recorded as deferred (NOT S12.8 work items):** **F45** (location lifecycle) + **F67** (midnight wrap) —
owner couldn't verify on device, re-test after S12.8; **F50** accessibility self-disable (Android 16/
OnePlus 13) — likely an OS limit, owner content to **wontfix**; **F80** (show the User Guide after
onboarding on first launch) → **S13**. **F71** gets its confirmation regression in S12.8a.

---

### S12.8a — Runtime: override feedback, throttle, panic, super-dimming/PWM *(Opus/high · large)*

**Objective:** fix the runtime feedback surfaces + behaviours the re-test flagged; port the missing panic
detector and the throttle-reinitialization. **Fence: domain/ + golden + ChartCanvas untouched (call only).**

**Deliverables:**
1. **F74** — the **Resume** action on the high-priority override notification does nothing; make it resume
   the pipeline (task569). Trace `AmbientMonitoringService` override-notif action → `ACTION_RESUME` →
   `controller.resume()`; debug why it's inert when paused-by-override.
2. **F75** — the high-priority override notification must **cancel/replace** any prior override notification
   and not stack with the ongoing one (manage `OVERRIDE_NOTIFICATION_ID` lifecycle).
3. **F76** — **remove the Pause action from the ongoing service notification** (it behaves like an override
   and confuses; stopping = disable the service). Keep Reset/Disable; keep Resume while paused.
4. **F71** — add a clean regression confirming a manual override is detected **inside** the throttle window
   (the owner was unsure their manual test was valid). Controller test against the S12.7i settle change.
5. **F88** — **tap-to-dismiss** flashes (Tasker lets you tap a flash to dismiss it). `AabFlash` /
   `ToastDebugSink` presentation.
6. **F78** — derive the effective throttle from the **actual `steps*wait`** used this cycle, and port the
   **Throttle Reinitialization** watchdog (task566 / prof754: after ~10 s of no brightness change push the
   throttle up to the `AnimSteps*MaxWait+10` ceiling so the sensor stops polling). Runtime state in
   `BrightnessPipelineController` (throttle gate ~`:238`); `extraction/tasks/task566_reset-throttle.md`.
7. **F77** — **panic / S.O.S. detector** (prof769 + task528): a NEW platform sensor source
   (`TYPE_SIGNIFICANT_MOTION`/shake + orientation **upside-down** + display-on) that triggers the SOS
   **morse flash pattern** + brightness 255 + disable super dimming + `%AAB_Service=Off`. Extend
   `emergencyStop()`; `extraction/profiles.md` prof769, `extraction/tasks/task528_panicbutton.md`. Currently
   only the manual notification "Reset" invokes the panic; there is NO sensor detector.
8. **F65 (REOPENED — corrected spec)** — PWM-sensitive mode is **mutually exclusive** with the Super-Dimming
   toggle (already enforced in UI). Its real behaviour: **lock the hardware brightness** (existing
   `applyPwmFloor`) **AND dim below the floor via Android Extra Dim** — today PWM-sensitive only floors and
   never dims. Port **Map Lux to Brightness (Java) V2 actions 23→38** (extract from the XML; cross-check the
   ledger note that task661's runtime math lives in Variable-Set 547, not Java). `SuperDimmingCoordinator` +
   the `BrightnessPipelineController` dimming path. (The S12.7b "two cooperating layers" model was wrong.)
9. **F58** — Super-Dimming screen **live readout**: dimming strength *rel* `[%AAB_DimmingCurrent]` + *abs*
   `[%AAB_DimmingDS]` at `[%AAB_CurrentBright]` brightness. Compute from `SoftwareDimming` (domain,
   call-only) into new `PipelineState` fields; render on `SuperDimmingScreen.kt`.
10. **F86** — clamp the **displayed** LuxAlpha to ≥ 0 in Live Debug / readouts; **leave the engine value
    unchanged** (parity, D-010a — `domain/` untouched). Note it in STATE.md.

**Acceptance:** ladder green; tests for override-notif Resume + no-stack, throttle reinit (idle→ceiling) +
actual-steps throttle, panic detector trigger→SOS, PWM Extra-Dim engage, the dimming readout, and the
LuxAlpha display clamp; STATE.md flips F58/F65/F71/F74/F75/F76/F77/F78/F86/F88.

---

### S12.8b — UI: dashboard, graph placement/grouping, context editor, permissions *(Opus/high · large)*

**Objective:** the cross-cutting UI/layout polish. **Preconditions also:** rebase onto S12.8a/c/d
screen-content changes (shared chart-host screens). **Fence: domain/ + golden + ChartCanvas untouched.**

**Deliverables:**
1. **F79** — **redesign the Dashboard** to be insightful: drop the confusing Pause control, keep
   Resume-after-override, surface genuinely useful live state. `DashboardScreen`/`DashboardViewModel`/
   `DashboardState`, `runtime/LiveRuntimeState`.
2. **F81** — **consistent graph placement**: the relevant chart sits **above** its settings on every screen
   (today SuperDimming/Circadian render it below); **cycle** between the relevant graphs by **swipe** (a
   pager), not vertical stacking. Shared scaffold across the chart-host screens; **coordinate with S13**
   (which fills the chart slots).
3. **F82** — **per-graph settings grouping**: settings feeding one graph are visually grouped and it is
   clear which graph they affect.
4. **F68 (UI bug)** — fix the **"Sunset (22:00)" vertical one-letter-per-line wrap** in the context rule
   editor (`ContextsScreen.kt` `TimeTokenRow` — add `maxLines=1` / width handling; "Sunrise (5:20)" is fine).
5. **F87** — make the context-rule **app list taller** (still scrollable); `ContextsScreen.kt`
   `heightIn(max=220.dp)`.
6. **F89** — **permissions audit**: background location, DUMP, package usage stats appeared in ADB's per-app
   list but weren't explicitly requested — confirm each is requestable/needed; request or wontfix-with-note.

**Acceptance:** ladder green; tests for the redesigned dashboard content, graph-above-settings ordering +
swipe pager, the single-line sunset label, and the taller app list; STATE.md flips F68/F79/F81/F82/F87/F89.

---

### S12.8c — Settings & profiles: schema hygiene, labels, legacy load, wizard *(Opus/high · large)*

**Objective:** remove the dead/cryptic settings, fix legacy-load fidelity and the wizard fit. **Fence:
domain/ + golden + ChartCanvas untouched (call only).**

**Deliverables:**
1. **F85 (CRITICAL)** — `%AAB_ThreshDynamic` must **NOT** be a user-editable setting (it is the *computed*
   threshold output for the current lux). Remove `thresholdDynamic` from `AabSettings`, `AabSettingsContract`,
   the `ReactivityScreen` editor, `SettingsDisplay`, and the legacy key=value parser; **keep** the computed
   `PipelineState.threshDynamic`. Bump `CURRENT_SCHEMA_VERSION` + migrate (drop the field; ensure
   `ignoreUnknownKeys` on read). **App-layer only** — the engine already ignores the field.
2. **F59** — the dynamic-threshold help text must substitute **the value only**, not the literal string
   "`%AAB_ThreshDynamic`" (`ReactivityScreen.kt`).
3. **F84 + modal exclusions** — give the settings-diff modal **friendly labels** (replace the raw
   `humanize()` of keys like "form1A"; `SettingsDisplay.kt`) and **exclude irrelevant keys** from the diff
   (`debugLevel`, `detectOverrides`, `quickSettingsEnabled`, `notificationsEnabled`, derived
   `thresholdMidpoint`) via `SettingsDisplay` EXCLUDED_KEYS.
4. **F70 (REOPENED)** — **legacy-load fidelity**: reproduce + fix "Form1A didn't stick / misc inherited from
   the previous profile". A legacy load must = **hard-coded task570 defaults THEN the file's diffs** (legacy
   configs store only diffs vs the hard-coded defaults); verify Form1A integer handling in the nested-JSON
   parser. `TaskerLegacyProfileSerializer`, `SettingsViewModel.replaceAll`.
5. **F62 (REOPENED)** — wizard fit: **don't count synthetic/ghost priors** toward the ≥9-point gate
   (reconcile the UI gate `ToolsScreen` vs the domain gate `CurveSuggestionEngine` vs the OverridePointStore
   contents — the owner ran it on just 7 real points), and make the wizard's **suggested curve actually
   draw** on the Curve & Brightness chart (it shows the plain line today). `BrightnessCurveChart` already
   accepts `fittedCurve` — wire it from the wizard result (ChartCanvas untouched).

**Acceptance:** ladder green; tests for thresholdDynamic removal + migration, the value-only help, friendly
labels + exclusions, legacy load = defaults+diffs (Form1A sticks), and the ghost-point gate + suggestion-line
draw; STATE.md flips F59/F62/F70/F84/F85.

---

### S12.8d — Circadian time & location correctness *(Opus/high · medium-large)*

**Objective:** make the dynamic-scale ramp track the real local sun and the fixed-date/loc override work;
port Tasker's location acquisition. **Fence: domain/ + golden untouched (call `SolarCalculator` only).**

**Deliverables:**
1. **F73 (REOPENED)** — fix the **~1 h DST/offset** error (owner saw scale 1.025 at 20:58 local while the
   **context-rule** sunrise/sunset are correct). Audit the tz offset `CircadianWindowProvider.compute`
   passes to `SolarCalculator.compute` — it must reflect the device's **current** offset incl. **DST** — and
   ensure the dynamic-scale ramp matches the (already-correct) context solar path. App-layer
   (`CircadianWindowProvider`, `BrightnessPipelineController.buildInput`); the golden domain math is correct.
2. **F39 (REOPENED — spec was wrong)** — make the Circadian **fixed Date/Lat/Lon** actually persist + apply
   ("Set fixed" is inert today). It is NOT preview-only: setting a date (e.g. 21 Dec) and/or a location must
   change the circadian scaling. Debug the `CircadianScreen` card handler → `ExperimentPrefsStore` →
   `CircadianWindowProvider` path; allow **either or both** (date and/or location).
3. **F83 (FULL parity)** — location acquisition: port Tasker's **once-a-day** refresh (when
   `%AAB_SunLastDate != %DATE`, briefly enable location via `WRITE_SECURE_SETTINGS` — ELEVATED), **skip**
   when fixed lat/lon set (F39), and **fall back to ip-api.com** (`http://ip-api.com/json`) when no Android
   fix. New platform geolocation client + **INTERNET** permission;
   `extraction/tasks/task090_dynamic_scale.md` (act26–30), `CircadianWindowProvider`,
   `platform/.../context/LocationReader`.

**Acceptance:** ladder green; tests for the DST-correct ramp framing, the fixed date/loc round-trip applying
to the windows, and the ip-api fallback (+ skip-when-fixed); STATE.md flips F39/F73/F83. **→ re-run HUMAN
GATE 2 (5th).**

---

## S12.9 — Engineering-quality & parity-debt remediation: hygiene, runtime parity, schema, i18n/UX, concurrency, IA merge (a–f)

**Model:** Opus / high · **Size:** large (6 sub-segments) · **Preconditions:** S12.8 (a–d + b'/b'') DONE;
Gate 2 (5th re-test) device-passed. **Run setup script.**

**Objective:** an engineering-quality + parity-debt review (code quality + owner device observations)
surfaced repo-hygiene ballast, ONE real runtime-parity gap (circadian dimming inert), schema/validation
ergonomics, an i18n gap, concurrency-discipline debt, and an owner request to fold Profiles+Contexts into
one screen. This stage lands the minimum to make the codebase honest and correct before S13's UI work;
release-grade polish (README expansion, CI `prerelease` flip, lint-baseline shrink, permission-drop
decisions) stays S14. The review was verified against current code; mis-diagnosed or already-closed items
are recorded as such in D-069 and NOT redone (notably: the AnimationRunner 5th-frame stride re-port is
**rejected** — it was a deliberate "modernize the how, not the what" drop; the Haversine de-dup is a no-op
— only one copy exists; the Shizuku "indefinite hang" is already defended by an existing bind timeout; the
`Main.kt` `< TIRAMISU` notification guard is **live** on API 31/32, not dead code).

**Stage fence:** `domain/` + golden vectors + `ChartCanvas.kt` are NOT modified, with ONE explicit
exception — S12.9e adds a comment-only rounding-mode header to `BrightnessEngine.kt` (no behaviour/
signature/golden change). The circadian-dimming fix (S12.9b) is **app-layer only**: `SoftwareDimming.dimShell`
already accepts the circadian parameter, so no domain or golden change is needed. Each sub-segment ends
green on the full ladder (`:domain:test :platform:test :app:testDebugUnitTest :app:assembleDebug
:app:lintDebug`), appends a STATE.md segment-log row + a `D-###`, and pushes on its own `claude/<codename>`
branch (prefix `[S12.9x] `). Land order **a → b → c → d → e → f** (hygiene first/lowest-risk; the runtime
fix before the schema refactor; concurrency before the IA merge; the IA merge last so it rebases onto
settled state). Expect STATE.md append-merges at each boundary.

### S12.9a — Repo hygiene & build integrity *(Opus/high · medium)*

**Objective:** remove repo ballast and dead code and make lint a build gate, without touching runtime
behaviour. App-layer + gradle + repo-root + `docs/rebuild/` only.

**Deliverables:**
1. **Tasker XML out of the tree.** Move `Advanced_Auto_Brightness_V3.3.prj_9.xml` (1.6 MB, repo root) to
   `docs/rebuild/extraction/_source/` + add that path to `.gitignore` (and a `.gitattributes` LFS line if
   the remote has LFS — owner picks LFS-vs-ignore at PR review). Update the `CLAUDE.md` line-4 pointer + a
   one-line note in `XML_RECIPES.md`.
2. **`.gitignore` Android pass.** Add `.idea/`, `*.iml`, `.DS_Store`, `captures/`, `.cxx/`, `*.hprof`.
3. **Delete dead API-26 branches.** `AutoBrightnessRuntime.kt:47,73` — `if (SDK_INT >= O) startForegroundService
   else startService`; O = API 26 ≤ minSdk 31, so the `else` is dead. Call `startForegroundService` directly.
   Add `DeadApiCheckTest` grepping `app/src/main/kotlin/` for always-true `SDK_INT >= …` checks where the API
   ≤ minSdk (31). **Boundary note (do NOT regress):** `SDK_INT < TIRAMISU` (API 33) is NOT dead — API 31/32
   devices exist; those branches (`MainActivity.kt`, `OnboardingScreen.kt:148,360`) correctly guard
   `POST_NOTIFICATIONS` and MUST be preserved. The test must not flag them.
4. **Relocate the non-compiling Java extracts.** Move the 40 `docs/rebuild/extraction/java/*.java`
   (Tasker `%var`-sigil reference transcripts, not buildable) to `docs/rebuild/extraction/_source/java/`,
   rename `.java` → `.java.txt`, add an `INDEX.md` (these are `html.unescape`-decoded embedded-Java extracts).
   Update path references in `extraction/tasks/*.md` + `extraction/scenes/*.md` + `extraction/INDEX.md`.
5. **Exorcise the `:data` ghost.** Remove the retired-`:data` bullet from `CLAUDE.md`'s module layout + any
   forward-looking `:data` reference in `RUNBOOK.md`'s architecture section (it is not in `settings.gradle.kts`).
   Keep S0–S3 segment-log mentions (audit trail).
6. **`Main.kt` → `MainActivity.kt`** (filename matches `class MainActivity`); FQ imports make the code change a
   no-op. **The `TIRAMISU` notification guard stays** (see #3).
7. **Lint becomes a gate.** Delete `app/lint-baseline.xml`; fix the three real issues it hid (`ObsoleteSdkInt`
   gone after #3; `VectorPath`; `MonochromeLauncherIcon` → add `mipmap-anydpi-v26/ic_launcher_monochrome.xml`;
   `DataExtractionRules` → add `res/xml/data_extraction_rules.xml` stub); add `app/lint.xml` with targeted
   `GradleDependency` + `AndroidGradlePluginVersion` suppressions (rationale comments, no blanket `*`); flip
   `abortOnError = false → true`.
8. **README seed (S14 hand-off).** Replace the 4-byte `README.md` stub with a ~60-line human-facing README
   (what Tideo is, the 3-module layout, the 4 gradle commands, links to `docs/rebuild/STATE.md`/`RUNBOOK.md`/
   `CLAUDE.md`, and a Status line: "Pre-release; `versionCode = 1`, no public tag; Gate 2 closed, Gate 3
   pending"). Add `<!-- TODO(S14): bump versionCode + release-grade README -->`. **CI is already shipped**
   (`release-signing.yml` + `release.yml`) — record as done; S12.9a does no CI work.

**Non-goals:** no behaviour change; no `versionCode` bump; no CI authoring; domain/golden/ChartCanvas untouched.

**Acceptance:** full ladder green with `abortOnError = true` and no baseline file; `DeadApiCheckTest` passes
and does NOT flag any `< TIRAMISU` branch; `git ls-files | grep prj_9.xml` empty (or an LFS pointer); no
tracked `extraction/java/*.java`; `MainActivity.kt` present, `Main.kt` gone; `README.md` seeded. STATE.md
row + D-069.

### S12.9b — Runtime parity: circadian dimming wiring *(Opus/high · medium)*

**Objective:** fix the one real runtime-parity gap the owner found on device (G2R-F90), align the
manual-override toast surface, and refine the Shizuku grant UX. Lands before the schema refactor so the
schema work doesn't paper over the live bug. **Fence: domain/ + golden + ChartCanvas untouched —
`SoftwareDimming.dimShell` already accepts the circadian parameter, so this is app-layer only.**

**Deliverables:**
1. **G2R-F90 — Circadian dimming is inert (headline parity bug).** Owner's two proofs: Spread (Circadian)
   at 100 vs 0 produces no change; super-dimming still engages in a dark room during circadian-scaled daylight
   (`scaleDynamic ≈ 1.15`). Root cause: `SuperDimmingCoordinator.apply` (`app/.../runtime/SuperDimmingCoordinator.kt:106`)
   hardcodes `dimDynamic = null` in its `SoftwareDimming.dimShell` call, never consulting
   `PipelineState.scaleDynamic` (a known deferral, D-040). **Investigation-first** (S4 maths-transcription
   protocol): confirm which `AabSettings` field is Tasker's "Spread (Circadian)" (likely `dimSpread`, default
   100 — D-026 re-homed it into the Circadian-Dimming group) via `extraction/scenes/circadian_dimming_graph.md`
   + the task646 act6 ScalingUse branch; do NOT guess. **Fix (app-layer):** compute the circadian strength
   multiplier from `PipelineState.scaleDynamic` + the spread setting per Tasker semantics (spread=100 →
   suppress super-dimming proportionally to the daylight scale; spread=0 → engage normally; spread=−100 →
   boost) and pass it as `dimDynamic` instead of `null`. `dimShell` and the golden `superdimming.csv` are
   **unchanged** (the parameter already exists; the default `null`/scale-1.0 path keeps every existing golden
   row valid). Update `SuperDimmingCoordinatorTest` with spread 100/0/−100 cases. Flip the PARITY_CHECKLIST
   circadian-dimming row to `ported` (device-verify at the next gate).
2. **G2R-F91 — Manual-override toast surface.** Verify whether the manual-override flash routes through the
   teal `AabFlash` operational surface like the profile/context-load flashes. If yes, record as
   already-consistent; if it falls back to a plain `Toast`, route it through `AabFlash`. Test the chosen
   surface. Mark any new user string `// TODO(S12.9d): strings.xml`.
3. **Shizuku grant UX refinement (minor — not a hang fix).** The preflight (`PrivilegeManager.isShizukuAvailable()`)
   and the bind timeout (`ShizukuShell`/`ShizukuGrantGateway` `withTimeoutOrNull(BIND_TIMEOUT_MS)`) already
   exist — there is no indefinite hang. Residual: `pingBinder()` can't distinguish *not-installed* from
   *installed-but-not-running*; add an installed-check so the UI shows a "start Shizuku" prompt for the
   latter, and assert the ADB path is always offered. `PrivilegeManagerTest` cases for the availability states.
   No `⚠️` glyph.

**Non-goals:** do NOT re-port Tasker's 5th-frame AnimationRunner stride — it was a deliberate, documented
"modernize the how, not the what" drop (`AnimationRunner.kt:27-29`); re-adding it makes override detection
less responsive (recorded rejected in D-069). No domain/golden change.

**Acceptance:** ladder green; `SuperDimmingCoordinatorTest` spread cases pass with `superdimming.csv`
unchanged; override-toast surface tested; `PrivilegeManagerTest` covers the availability states. STATE.md
row + D-### (G2R-F90 is the headline — call it out in the segment-log row); PARITY_CHECKLIST circadian-dimming
row flipped.

### S12.9c — Schema ergonomics, validation & error handling *(Opus/high · medium)*

**Objective:** make `AabSettings` decomposable, fail-fast on schema drift, surface profile-load failures,
validate the circadian spread, audit validation parity vs Tasker, honest-ify the placeholder screens, and
document the DataStore/privilege/permission surfaces. App-layer + `docs/rebuild/` only.

**Deliverables:**
1. **Decompose `AabSettings` (42 flat fields).** Introduce nested `@Serializable` records (`BrightnessBounds`,
   `CurveParams`, `DimmingConfig`, `AnimationConfig`, `ThresholdConfig`, `ScalingConfig`, `GlobalPrefs`);
   `AabSettings` becomes their composite. **Wire compat is binding:** the serializer keeps the flat v3 JSON on
   disk (`CURRENT_SCHEMA_VERSION` stays 3, no migration); a `LegacyImportRoundTrip` test asserts flat v3 JSON
   loads into the nested fields. `mergeProfile` becomes a targeted `copy(global = baseline.global, …)`; keep
   the 6 baseline-preserved fields (G2-F8) under test. `AabSettingsContract` keys stay the flat strings.
2. **`SettingsDisplay.valueFor` fail-fast.** Replace `else -> ""` with a thrown `IllegalArgumentException`; add
   `SettingsDisplayContractDriftTest` asserting every `AabSettingsContract.rules` key resolves and the count
   matches (drift fails the test).
3. **`ProfileImportExportManager` surfaces failures (also closes the no-error-UI debt for this path).** Replace
   the silent `runCatching{}.getOrElse{legacy}` with a `ProfileLoadResult` sealed type
   (`Success`/`LegacyFallback(jsonError)`/`TotalFailure(jsonError, legacyError)`); the caller logs
   (`android.util.Log`) and shows a user-visible error card on `TotalFailure`. Tests for all three branches.
4. **`TaskerLegacyProfileSerializer` throws on garbage.** Keep defaults for a successful no-inheritance parse,
   but throw `LegacyProfileParseException` for structurally-invalid input (no `%AAB_`/JSON tokens) so the
   manager can build `TotalFailure`. Test: empty + random strings throw.
5. **DataStore map (document, don't merge).** Add `docs/rebuild/architecture/datastore_map.md` listing the 6
   stores (settings/health/experiment-prefs/context-rules/override-points/user-profiles), their schemas, and
   why each is independent; `DataStoreSchemaVersionTest` asserts each version constant matches its serializer.
6. **Spread (Circadian) validation.** Validate the spread field (per S12.9b's confirmed field) to −100..100
   with red `supportingText` (no `⚠️` glyph); `SettingsValidatorTest` boundary cases (−101/−100/0/100/101).
7. **Validation parity audit.** Produce `docs/rebuild/extraction/validation_audit.md`: every Tasker-validated
   setting (the `[Tasker warning toast]` signals in the scene docs) vs the rebuild's `SettingsValidator`/contract
   coverage; add validators for the gaps (plain-English messages, no glyph). If > 10 gaps, fix the top ones +
   defer the rest to S14 (logged).
8. **Placeholder screens — honest or gone.** `PlaceholderScreen.kt` / `ChartPlaceholder.kt` either deleted
   (with their `NavGraph`/`AppRoute` entries) or honest-ified with a `// TODO(S13): implement` marker —
   coordinate with S13 (which implements About/Guide/charts). `PlaceholderScreenAuditTest` fails on any
   `"Coming in"` text in `ui/screens/`.
9. **Privilege + permission docs.** `docs/rebuild/architecture/privilege_tiers.md` (root / Shizuku / ADB
   channels + preflights + the "ADB always offered" invariant) and `docs/rebuild/architecture/permission_audit.md`
   (each declared permission: use, feature, v1-scope, removable, S14 recommendation). Docs only — S14 acts.

**Note (dropped item):** the suggested Haversine de-dup is a no-op — there is ONE haversine implementation
(`ContextOverrideResolver.distanceMeters`); no domain util is added (hard fence preserved). Recorded in D-069.

**Non-goals:** no domain/golden change; no DataStore merge; no `CurveSuggestionEngine` decomposition
(owner-binding); no `⚠️` glyph anywhere.

**Acceptance:** ladder green; nested `AabSettings` with flat-JSON round-trip test; `valueFor` throws + drift
test; `ProfileLoadResult` + error card + 3-branch test; `datastore_map.md` + version test; spread validator
+ boundary test; `validation_audit.md` + gap validators; `PlaceholderScreenAuditTest` green; `privilege_tiers.md`
+ `permission_audit.md` present. STATE.md row + D-###.

### S12.9d — i18n capability & test backfill *(Opus/high · medium)*

**Objective:** give the UI an i18n surface, backfill tests for genuinely-untested runtimes, and add a
staleness gate to `LiveRuntimeState`. App-layer only. Lands after S12.9c so the new strings/tests cover the
post-schema surface.

**Deliverables:**
1. **Extract user-visible strings** (`strings.xml` has 2 entries; ~97 `Text("…")` + ~30 toasts hardcoded).
   Priority: notification strings → screen titles → button labels → toasts (incl. the S12.9b override-toast
   TODO) → validator/error messages → (section headers/help text only if budget allows; else defer to S14,
   logged). Keys snake_case, screen/feature-prefixed; access via `stringResource` / `context.getString`. Add a
   `HardcodedStringCheckTest` heuristic that fails on new hardcoded UI strings. Do NOT extract log strings,
   `%AAB_*` provenance strings, or developer-facing exception messages. No `values-es/` (capability, not
   translations).
2. **Evidence-based test backfill.** Survey existing tests first — `AmbientMonitoringServiceTest` and
   `ContextEngineTest` already exist; do NOT duplicate. Add dedicated tests only for the genuinely-untested
   runtimes among `OverrideMonitor`, `LiveRuntimeState`, `MaintenanceWorker`, `AndroidContextSignalSource`,
   `ScreenStateReceiver`, `AabToastAccessibilityService` (the last is owner-intentional — test its behaviour,
   not its necessity).
3. **`LiveRuntimeState` staleness gate.** Add `lastPublishMs` to `PipelineState` (stamped on publish); expose
   `staleness(): Flow<Staleness>` (FRESH <3 s / AGING 3–10 s / STALE >10 s); `DashboardViewModel` surfaces
   `stale`; `DashboardScreen` shows an amber "live data may be stale" banner when `stale && serviceRunning`.
   Add a watchdog in `AmbientMonitoringService.onDestroy`/`onTaskRemoved` that resets `LiveRuntimeState` after
   a 5 s grace if no new publish arrived. `LiveRuntimeStateTest` covers publish-stamp / FRESH / STALE-after-11s
   / reset.

**Non-goals:** no translations; no concurrency-model change (the watchdog uses the existing scope pattern,
hardened in S12.9e); domain/golden/ChartCanvas untouched.

**Acceptance:** ladder green; `strings.xml` ≥ ~40 entries; `HardcodedStringCheckTest` green; new runtime tests
present (only for true gaps); `LiveRuntimeState` staleness Flow + Dashboard banner + watchdog +
`LiveRuntimeStateTest`. STATE.md row + D-###.

### S12.9e — Concurrency safety & controller decomposition *(Opus/high · medium-large; lands before the IA merge)*

**Objective:** replace leaked coroutine scopes, remove the `lateinit` controller cycle, tighten volatile
discipline across the runtime, and decompose the 596-LOC controller — purely structural/safety, no behaviour
change. The runtime concurrency MODEL (single pipeline consumer, drop-on-busy, D-027) is unchanged.
**Fence: domain/ + golden + ChartCanvas untouched EXCEPT a comment-only rounding-mode header in `BrightnessEngine.kt`.**

**Deliverables:**
1. **`AppProcessScope` + `goAsync()`.** Add a process-wide supervised scope with a logging
   `CoroutineExceptionHandler`. Replace the **7** ad-hoc `CoroutineScope(Dispatchers.*)` sites —
   `AutoBrightnessRuntime.kt:23,79,85`, `BootCompletedReceiver.kt:15`, `ScreenStateReceiver.kt:14`, **plus the
   two the review missed: `BrightnessTileService.kt:24` and `AmbientMonitoringService.kt:44`** (audit whether
   each is a genuine leak or a legitimately-owned service/tile scope; convert or justify). Receivers use
   `goAsync()` + scope-launch + `pendingResult.finish()` in `finally`. Update the receiver tests.
2. **Kill `lateinit var controller` (`AppModule.kt:67,73`).** Introduce a `ControllerHook` interface
   (implemented by `BrightnessPipelineController`) + a `ControllerHookHolder` whose `@Volatile var hook` is set
   after construction; `ContextEngine(onProfileChanged = { holder.hook?.onContextChanged() })`. `AppModuleTest`
   asserts the hook is wired and a pre-assignment fire is a no-op, not a crash.
3. **`ContextEngine` volatiles → snapshot.** Replace the 7 `@Volatile` signal fields with a single
   `MutableStateFlow<SignalSnapshot>` (atomic, untearable reads); keep `@Volatile` only where genuinely
   single-writer/single-reader, with a justifying comment.
4. **Volatile-discipline audit (whole runtime).** Audit `BrightnessPipelineController` (5 → ≤2; move
   `autoRunning`/`initializing` into `PipelineState` for atomic compound reads; drop the consumer-only
   `lastScaleDynamicSeen`) **and the clusters the review missed — `CircadianWindowProvider` (5),
   `ThrottleController` (2), `AabFlash` (2)** — justifying or fixing each (StateFlow / `Mutex` / documented
   single-writer). Each surviving `@Volatile` carries a one-line rationale.
5. **Decompose `BrightnessPipelineController.kt` (596 LOC).** Split into an orchestrator (≤300 LOC:
   construction, start/stop, `ControllerHook`, state exposure) + `PipelineCycleRunner` + `PipelineDebugEmitter`
   + `PanicHandler`. Purely structural — the existing controller tests stay green unchanged; `PipelineFileLayoutTest`
   guards re-bloating. All `// Tasker:` provenance comments move with their code (owner-binding: keep them).
6. **`BrightnessEngine` rounding-mode header (comment-only domain touch).** Consolidate the repeated `gap-0X`
   rounding justifications into one header at the top of `BrightnessEngine.kt`; KEEP every `// Tasker: task###`
   provenance comment. No behaviour/signature/golden change; `ProvenanceCommentCountTest` asserts the provenance
   count is unchanged; `:domain:test` stays green.
7. **Clarify the screen-state receiver split.** The manifest `ScreenStateReceiver` (start/stop monitoring) and
   the service's internal receiver (in-service pipeline reaction via `onScreenOn/Off`) do different jobs, and
   the service already `unregisterReceiver`s in `onDestroy` — document the two-layer split (comment + the
   existing `AmbientMonitoringServiceTest`); only de-duplicate if the audit finds a true double-fire.

**Non-goals:** no concurrency-MODEL redesign; no new dependency (no Hilt/Timber); domain logic/golden/ChartCanvas
untouched (header comment excepted).

**Acceptance:** ladder green; zero ad-hoc `CoroutineScope(Dispatchers.*)` in `app/.../runtime/`; no `lateinit
var controller` in `AppModule`; `ContextEngine` ≤1 `@Volatile`; `BrightnessPipelineController` ≤2 `@Volatile`
(+ the other clusters audited); controller split into 4 files (`PipelineFileLayoutTest`); `BrightnessEngine`
header in place with provenance count unchanged. STATE.md row + D-###.

### S12.9f — Profiles & Contexts IA merge (plumbing) *(Opus/high · medium)*

**Objective:** fold the separate Profiles and Contexts destinations into ONE screen, with context rules edited
in a modal — mirroring the Tasker UX where a profile owns its context rules and a rule targets a saved
profile. Structure + navigation + state wiring only; visual polish is handed to S13. App-layer only;
behaviour-preserving. **Fence: domain/ + golden + ChartCanvas untouched.**

**Inputs:** `ui/screens/ProfilesScreen.kt` + `state/SettingsViewModel.kt`; `ui/screens/ContextsScreen.kt` +
`state/ContextsViewModel.kt`; `settings/ContextOverrideRules.kt` (the `ContextRule`/`ContextTriggers` model);
`navigation/AppRoute.kt` + `NavGraph.kt`; existing `AlertDialog`/`ModalBottomSheet` precedents.

**Deliverables:**
1. **One destination.** Merge the `AppRoute.Profiles` + `AppRoute.Contexts` hero destinations into a single
   "Profiles & Contexts" screen (keep one route; remove the other from `heroDestinations`/`NavGraph`). The
   screen shows the saved-profiles surface (save/load/overwrite/delete/import/export/factory-restore —
   unchanged) plus a "Context rules" section listing the priority-ordered rules with their target profile.
2. **Contexts as a rule-editing modal.** Move the rule list + per-rule editor (`EditRuleContent` —
   apps/wifi/battery/location/time/days/priority + target-profile dropdown) into a `ModalBottomSheet` (or
   full-screen dialog) launched from the unified screen, replacing the standalone `ContextsScreen` navigation.
   Preserve every editor affordance (installed-app picker, use-current-SSID, SUNRISE/SUNSET tokens,
   use-current-location, usage-access prompt, day picker, "clear time").
3. **Shared state.** The unified screen hosts both `SettingsViewModel` (profiles) and `ContextsViewModel`
   (rules); preserve the manual-load context lock (`%AAB_ContextOverride`) + Resume banner, and the
   rule→profile relationship (a rule's `profile` targets a saved profile; the catalog already reads user
   profiles). No change to `ContextEngine`/`ContextOverrideResolver` or the context-rules store.
4. **Functional, minimally styled.** Land it working and tested; do NOT invest in visual design — S13c restyles
   the merged screen with the new Material 3 component library.

**Non-goals:** no rule-model change; no resolver/engine change; no visual redesign (S13); existing context
tests stay green.

**Acceptance:** ladder green; one merged destination; rule editing works in a modal with all triggers
preserved; context tests + a nav/merge test green. STATE.md row + D-070.

---

## S13 — UI finalization: Material 3 redesign + charts + static screens (a–d)

**Model:** Opus / high · **Size:** large (4 sub-segments) · **Preconditions:** S12.9 (a–f) DONE (hygiene,
circadian-dimming fix, nested settings + i18n strings, concurrency, Profiles/Contexts merge); S6 DONE (chart
math); S2 scene docs + `extraction/tasks/anonymous_handlers.md` (S3.5).

**Objective:** bring the UI from "functional wireframe" to a polished Material 3 (Material-Expressive-leaning)
surface, replace the remaining chart placeholders with real charts, and port the static About/User-Guide
screens. The actual design decisions (token values, component specs, per-screen layouts) are **generated
inside this segment** (S13a's audit produces them) — this brief is the scaffolding + guardrails.

**Binding guardrails (all sub-segments):**
- **Color scheme is fixed** — the teal+gold `AabLightColorScheme`/`AabDarkColorScheme` (S12.5a) stay; the
  restyle is layout/spacing/elevation/typography/motion, NOT recoloring.
- **No new dependencies** (Compose M3 + foundation only; no third-party design libs).
- **Hard fence:** `ChartCanvas.kt`, `domain/`, golden vectors, `runtime/`, gradle, manifest, `SettingsValidator`
  NOT modified — charts build composables ON TOP of `ChartCanvas`.
- **i18n:** all new/affected UI strings via `stringResource` (the `HardcodedStringCheckTest` from S12.9d enforces this).
- **Behaviour-preserving:** the restyle changes presentation only; existing screen tests stay green. No `⚠️` glyph.
- **Provenance:** keep `// Tasker:` comments on any ported UI logic (owner-binding).

#### S13a — Design-system foundation & audit *(Opus/high · medium)*
**Objective:** establish the reusable design-system primitives + a written per-screen redesign plan.
**Deliverables (decide the token values HERE):** a spacing/dimension scale (`ui/theme/Dimens.kt`), shape
tokens (`ui/theme/Shape.kt`), a finalized type scale (`ui/theme/Type.kt`), the documented ColorScheme
role-mapping (which token = which semantic use); `docs/rebuild/design/m3_audit.md` cataloguing each screen's
current wireframe gaps (ad-hoc `dp` literals, flat cards, missing empty-states, no motion) → the target M3
pattern. **Acceptance:** tokens compile + are referenced by ≥1 screen; the audit doc enumerates all ~9 screens
+ the merged Profiles/Contexts screen.

#### S13b — Component library *(Opus/high · medium)*
**Objective:** build the reusable components that replace the ad-hoc per-screen compositions.
**Deliverables:** a unified `SettingField` (folding `NumberSettingField`/`IntSliderSettingField`/`SwitchSettingRow`
+ validation + help), consistent `AabCard`/section containers with proper elevation, an `EmptyState`
component, and motion/transition helpers — all driven by S13a's tokens. **Acceptance:** components have
instantiation tests; behaviour-preserving against the existing field tests.

#### S13c — Screen-by-screen restyle *(Opus/high · large)*
**Objective:** apply the tokens + component library across every screen, including the merged Profiles/Contexts
screen from S12.9f. **Deliverables:** restyle Menu, Dashboard, Curve & Brightness, Reactivity, Super Dimming,
Circadian, Misc, Tools, Live Debug, and Profiles/Contexts — consistent spacing, elevation, empty-states, and
motion; no logic change. **Acceptance:** screens render via the new components; existing screen tests green;
`m3_audit.md` rows checked off.

#### S13d — Static content & charts *(Opus/high · large)*
**Objective:** the original S13 scope — real charts + About/User-Guide. **Deliverables:**
`ui/graph/{ReactivityChart,DimmingChart,CircadianChart,TaperChart,PowerDrawChart,ExperimentChart}.kt` each
sampling its domain function per the scene doc and rendering via `ChartCanvas`, filling the `ChartSlot.content`
swap points left by S12.8b (D-066) — no pager/screen change; `ui/screens/AboutScreen.kt` + `UserGuideScreen.kt`
porting the extracted text (drop the Chart.js license screen — flip its checklist row `dropped(Chart.js
removed)`); replace `PlaceholderScreen.kt`/`ChartPlaceholder.kt`; **G2R-F80:** wire `UserGuideScreen` as the
post-onboarding first-run destination. If a chart needs a `ChartCanvas` capability that doesn't exist: SKIP it,
record PARTIAL in STATE.md, move on (do NOT modify `ChartCanvas`). **Acceptance:** charts instantiate + wire
into their hosts; About/Guide present; placeholders gone (`PlaceholderScreenAuditTest` green); checklist
graph-scene rows flipped.

**Acceptance (S13 whole):** `./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :domain:test
:platform:test` green; checklist graph-scene rows flipped; `PlaceholderScreenAuditTest`/`HardcodedStringCheckTest`
green; pushed. **S13 → S14:** a complete, polished UI surface; S14 owns release-grade finalization
(screenshots, copy review, translations, permission decisions, Gate 3).

---

## S14 — Final integration review, parity audit, release prep

**Model:** Opus / high · **Size:** large · **Preconditions:** S1–S13 DONE; Gate 1 & 2
findings triaged (any open BLOCKED rows resolved or explicitly accepted by the human).

**Objective:** Whole-system review against the XML and checklist; close every gap; prepare the
device acceptance script and release artifacts.

**Inputs:** `PARITY_CHECKLIST.md` (every row), `STATE.md` full ledger + gate findings,
`git log --stat` whole-program diff review, XML via recipes for any suspicious row.

**Deliverables:** every checklist row → `ported`/`dropped(reason)` (zero `pending`; re-verify a
sample of `ported` rows against extraction docs); fixes for findings (engine edge cases,
missed notification actions, validation gaps, dead code, TODOs:
`grep -rn "TODO\|FIXME" app domain platform` triaged); `docs/rebuild/DEVICE_TEST_SCRIPT.md` —
Gate-3 numbered steps with expected outcomes covering every profile-equivalent behavior;
`README.md` — features, install, privilege setup incl. exact
`adb shell pm grant com.tideo.autobrightness android.permission.WRITE_SECURE_SETTINGS`,
Shizuku walkthrough, troubleshooting (OEM brightness quirks, battery optimization);
`versionName "0.9.0"`; optional `.github/workflows/build.yml` (assembleDebug + all tests) —
attempt push ONCE, if rejected for workflow scope: delete the file, record in STATE.md, move
on; lint-baseline shrink pass (delete entries that no longer fire).

**Acceptance:** `./gradlew build` (full: all modules, lint, all tests) green;
`grep -c pending docs/rebuild/PARITY_CHECKLIST.md` → 0; pushed. → **HUMAN GATE 3.**

---

## Human gates (the user + their phone; findings go to STATE.md "Gate findings")

**GATE 1 — core loop (after S9b).** Install `app/build/outputs/apk/debug/app-debug.apk`.
Grant notifications + WRITE_SETTINGS via onboarding-less path if S11 not done yet (Settings →
Apps → Special access → Modify system settings). Enable service. Verify: cover light sensor →
brightness animates DOWN smoothly (no jumps); shine light → animates UP; drag system slider
mid-run → auto pauses (notification says so), resumes per rules; screen off→on → reinit +
initial brightness; reboot → service self-starts; notification Pause/Resume/Disable work;
optionally `adb shell pm grant … WRITE_SECURE_SETTINGS` → in darkness below dimming threshold
extra dimming engages and disengages cleanly.

**GATE 2 — surfaces & tiers (re-tested after S12.6e; 1st attempt after S12 FAILED → D-045/S12.5; 2nd
attempt after S12.5c surfaced G2R-F1…F25 → S12.6).** Full UI walkthrough: every field edits/persists/
rejects-invalid-with-red; derived form2A/form3A update live; wizard produces and applies
suggestions; Shizuku ELEVATED flow end-to-end on-device; QS tile add + toggle; per-app
override (grant usage access → switch apps → rule applies); charging + time contexts;
brightness chart shape sanity vs the old Tasker graph scene; legacy Tasker profile import.

**GATE 3 — acceptance (after S14).** Run `docs/rebuild/DEVICE_TEST_SCRIPT.md` end-to-end;
24 h soak: survives doze, battery drain acceptable, no ANRs; flip checklist rows to
`device-verified`; remaining findings → STATE.md for a punch-list session (re-run an S9/S12
style segment scoped to the findings).

## Verification architecture (program-wide)

- Per-session: SessionStart hook bootstraps SDK; acceptance ladder
  `./gradlew :domain:test :platform:test :app:testDebugUnitTest :app:assembleDebug
  :app:lintDebug` (subset per brief); segment ends green + pushed, else failure protocol.
- Parity: committed golden CSVs from the test-only Tasker reference implementation (Java
  semantics preserved). Vectors/reference immutable except evidence-backed extraction fixes
  (STATE.md-logged). Tolerance 1e-9; Tasker-string outputs compared exactly.
- Robolectric: service lifecycle/FGS, receivers, ContentObserver (ShadowContentResolver),
  Settings shadows, privilege tiers, DataStore migration, compose smoke, tile instantiation.
  NOT covered (→ human gates): real sensor timing, OEM ranges, Shizuku binder, doze.
- Traceability: PARITY_CHECKLIST.md flipped per segment; S14 enforces zero pending.

## Risk register

| # | Risk | Mitigation | Owner |
|---|---|---|---|
| 1 | Extraction misreading | Opus on S1/S2/S4; verbatim dumps + line provenance; census reconciliation (40/18/20/125); "unresolved" over guessing | S1/S2 |
| 2 | Rounding/tie divergence (Math.round ties, BigDecimal HALF_UP, string outputs) | Reference keeps Java semantics; explicit tie/boundary golden rows | S4/S5 |
| 3 | Kotlin 2 / AGP migration friction | Pinned matrix; step-down-one-minor protocol; STATE log | S3 |
| 4 | FGS specialUse / boot policy (API 34/35) | Manifest property + type flag pairing; Gate-1 reboot test; dataSync abandoned | S3/S9a/S9b |
| 5 | OEM brightness range ≠ 255 | config_screenBrightnessSettingMaximum lookup + normalization; Gate-1 sweep | S7 |
| 6 | Shizuku integration complexity | One-time-grant design (no runtime binder); adb fallback always present; gateway faked in tests | S7/S11 |
| 7 | Scene-consolidation feature loss | S2 450-element disposition matrix; Gate 2 walkthrough; S14 zero-pending | S2/S12/S14 |
| 8 | Settings schema drift | S1 full 125-var audit; schema v2 + migration tests | S1/S8 |
| 9 | Parallel-segment push races | Disjoint file ownership per window; rebase-before-push rule; STATE.md append-merges | all |
