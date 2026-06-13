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
Serial spine: S0 → A → S4 → S5 → B → S9a → S9b → C → S12 → S13 → S14.
(S9 was split into S9a/S9b in S3.6 — see D-027.)

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

## S13 — Chart replication + static screens

**Model:** Haiku / high · **Size:** medium · **Preconditions:** S12 DONE (template exists),
S6 DONE (chart math), S2 scene docs + `extraction/tasks/anonymous_handlers.md` (S3.5).

**Objective:** Replicate the remaining charts from the template and port static content
screens. Pure pattern work.

**Inputs:** `ui/graph/BrightnessCurveChart.kt` (THE template — copy its structure exactly),
`ChartCanvas.kt` (read-only!), `extraction/scenes/scene_*graph*.md` (series/axis definitions
per chart), domain functions: smoothing-alpha curve (reactivity), `SoftwareDimming` (dimming),
`DynamicScaleEngine` (circadian), taper from engine, calibration data store (power draw),
`extraction/scenes/` About/User Guide content.

**Deliverables:** `ui/graph/{ReactivityChart,DimmingChart,CircadianChart,TaperChart,
PowerDrawChart,ExperimentChart}.kt` each: sample its domain function per the scene doc's
series/axes, render via ChartCanvas, wire into its host screen (slots were left by S12);
`ui/screens/AboutScreen.kt` + `UserGuideScreen.kt` porting the extracted text (drop Chart.js
license screen — flip its checklist row `dropped(Chart.js removed)`); a trivial compose
instantiation test per chart.

**Non-goals (HARD FENCE):** Do NOT modify `ChartCanvas.kt`, anything in `domain/`, `runtime/`,
gradle files, manifest, or `SettingsValidator`. If a chart needs a ChartCanvas capability that
doesn't exist: SKIP that chart, record precisely what's missing in STATE.md (status PARTIAL),
move on. Do not redesign APIs.

**Acceptance:** `./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest` green;
checklist graph-scene rows flipped; pushed.

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

**GATE 2 — surfaces & tiers (after S12).** Full UI walkthrough: every field edits/persists/
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
