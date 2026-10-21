# Light refactor: one source for each duplicated calculation

## Status

- **Spec only. No code has changed.** The owner asked on 2026-10-21 for a plan to lightly refactor
  Tideo where logic is duplicated or implemented differently, then authorised **Unit 0 only**:
  committing this file. Every later unit needs the owner's separate go-ahead.
- The owner accepted every recommendation in Owner decisions D1–D5 (2026-10-21).
- Three independent audits fed it: Opus, Astra (`gpt-6-astra`, high effort) and an earlier ChatGPT
  review of v1.14.1. Every finding below was re-checked against `math-explained` at `20ad58b`. A
  finding that one model made and the code did not bear out was dropped.
- It does not touch the Night Light write paths, which have their own plan
  (`NIGHT_LIGHT_SWITCH_ON_ORDER.md`). The two plans share no files.

## The motivating example

Live Debug's **Current brightness** (`LiveDebugScreen.kt:124`) shows `PipelineState.currentBrightness`,
which is the hardware value. The Dashboard, widget, notification and curve chart show the perceived
value, `shownBrightness`. With PWM-sensitive mode off the two are equal. With it on, the hardware
write is floored to the dimming threshold and the perceived value is not: for an engine target of 5
and a threshold of 40, Live Debug says 40 and the Dashboard says 5.

- **Both numbers are right and both are needed** (D-109; pinned by `BrightnessPipelineControllerTest`).
  The defect is that each screen chooses between them on its own, and the labels contradict each other:
  - the Dashboard labels the perceived value **"Applied brightness"**;
  - Live Debug labels the hardware value **"Current brightness"**.
- The perceived value is really the *requested* one. It is published even when the write fails.

## Findings, ranked

| # | Duplicated logic | Copies | Visible today? | Unit |
|---|---|---|---|---|
| 1 | Dimming level and its readout | `SuperDimmingCoordinator.apply`, `PipelineCycleRunner.dimmingReadout`, `emitOverlayPreview` | **Yes**: stale and over-reporting readout | 1 |
| 2 | Which brightness a screen shows | `PipelineState.shownBrightness`, hand-rebuilt in `DashboardViewModel`/`BrightnessInstrument`; Circadian and Super Dimming charts read `targetBrightness` directly; `DiagnosticCard` mixes both | **Yes**: PWM mode, contradictory labels | 2 |
| 3 | Dimming chart vs runtime formula | `DimmingChart` always plots ordinary dimming; runtime uses `finalDimLevel` in PWM mode | **Yes**: PWM mode | 3 |
| 4 | Global fields kept across a profile swap | `mergeProfile`, `ProfileApplier`, `SettingsViewModel.resetDefaults`, `.replaceAll` | Latent; lists already differ (`setupTitle`, `schemaVersion`) | 4 |
| 5 | Small pure formulas written twice | validator's 1000-lux curve and min-required MaxBright; wizard result → settings; effective hand-off lux; haversine | Latent | 5 |
| 6 | Circadian dim multiplier | runtime rebuilds it from the rounded `scaleDynamic` (D-072); `DynamicScaleEngine` computes it directly, and the chart uses that | Small: up to 1 dim level at extreme spreads | 6 (optional) |

### 1. Dimming: one calculation, one result, published everywhere

- **Today:**
  - The coordinator and the readout each pick PWM vs ordinary dimming and compute the level separately.
  - The coordinator also checks the privilege tier, rounds the level, disengages at level ≤ 0 and
    handles a failed write. The readout does none of these.
- **Visible effects:**
  - **Never cleared on disengage.** None of the 8 `dimming.disengage()` sites clears
    `dimmingCurrent`/`dimmingDS`: override pause, screen off, panic, calibration, hand-off, stop. The
    Dashboard dimming value and the Super Dimming chart's "Now" marker keep the last value. That
    marker uses `dimmingDS > 0` as its "engaged" test.
  - **Missed on reapply.** `setInitialBrightness` (profile switch, resume, screen on) applies dimming
    but never publishes the readout. A switch to a profile without dimming keeps showing the old
    profile's dimming until a sensor cycle passes the dead band.
  - **Over-reports.** Below ELEVATED, the readout shows dimming that was never applied.
- **Change:**
  - **Pure plan.** One pure `DimmingPlan` (mode, raw DS, relative readout, integer level) computed
    once from target, settings and scale.
  - **Coordinator.** `DimmingCoordinator.apply` returns it, together with whether it engaged.
  - **Pipeline.** One pipeline helper publishes the readout, called from both `runCycle` and
    `setInitialBrightness`. That also removes the duplicated floor → write → dim tail of those two
    functions.
  - **Disengage.** Every disengage publishes zeros.
  - **Keep.** Keep the task650 `DS × progress` relative readout as it is.
- **Tests:**
  - the readout after `setInitialBrightness`;
  - the readout after an override pause;
  - the readout below ELEVATED;
  - PWM and ordinary levels unchanged when engaged.
  - The golden `superdimming.csv` is untouched.

### 2. Brightness readouts: one definition, honest labels

- **Definitions.** `PipelineState` becomes the only place that defines the two values (KDoc on each):
  - **perceived:** `shownBrightness`;
  - **hardware:** `currentBrightness`.
  - **Chart marker.** Add one `liveMarkerBrightness` that applies the service-on / not-handed-off gate
    the charts repeat.
- **Dashboard.** It takes `shownBrightness` and `currentBrightness` instead of rebuilding them.
- **Charts.** The Circadian and Super Dimming charts take the shared marker instead of
  `targetBrightness`.
- **Labels.** Each label names the value it shows:
  - Live Debug shows both, as "Perceived" and "Hardware (written)";
  - the Dashboard's "Applied" label stops describing a perceived number;
  - the `DiagnosticCard` circadian and dimming lines say "hardware".
- **No value changes.** Only selection and labels change.
- **Strings.** Changed strings join Owner-queue item 1 (untranslated Chinese strings).

### 3. Dimming chart follows the active mode

- `DimmingChart` takes `pwmSensitive` and `pwmExponent`.
- In PWM mode it plots `SoftwareDimming.finalDimLevel` over the same x-range, so the live marker sits
  on the curve the runtime uses.
- This departs from Tasker's dimming graph (`dimming_graph.md`), so it needs a DD row.

### 4. One helper for global fields kept across a swap

- `AabSettings.keepGlobalsFrom(current)` holds the list that `mergeProfile` holds today.
- Manual load, import and reset call it. They state their own extras at the call site:
  - the context lock;
  - import's preserved secure-display fields.
- **Draft rebase stays separate.** It lets `detectOverrides` be edited on purpose.
- **Not a blanket copy.** `GlobalPrefs` is the wrong boundary, because it also holds per-profile
  fields (`ContextEngine.kt` KDoc on `mergeProfile`).
- **Test.** One test asserts that every swap path keeps the same global set.

### 5. Small pure formulas, one copy each (no behaviour change)

- **Validator.** `SettingsValidator` calls the raw `BrightnessEngine.mapLuxToBrightness(1000.0, …)`
  instead of its own three zones. Keep it raw: no scale, offset or clamp. It also calls
  `AabSettings.minRequiredMaxBrightness()` instead of recomputing it.
- **Wizard.** `AabSettings.withCurveSuggestion(cfg)` replaces the two five-field copies in
  `ToolsScreen`. Preview and Apply keep their different validation timing.
- **Hand-off lux.** `HandoffRules.effectiveLux(handoffLux, curve)` replaces `maxOf(...)` in
  `HandoffCoordinator` and `HandoffScreen`.
- **Distance.** `ContextEngine`'s location debounce uses `ContextMatching`'s haversine and Earth
  radius.

### 6. Optional: take the circadian dim multiplier from the engine

- **Change:**
  - `BrightnessEngine.dynamicScale` passes `dimSpreadPercent` and publishes `dimDynamic`.
  - The coordinator uses it, and `circadianDimMultiplier` is deleted.
- **Why it changes behaviour.** This is Tasker task90's own value. The current rebuild divides the
  3-decimal-rounded scale by `ScaleSpread`, which multiplies the rounding error by up to 100.
  - Worked example (Astra): ScaleSpread 1, DimSpread 100, strength 25. The chart says 1.540, the
    runtime 1.500: level 39 vs 38.
  - At the defaults (15 / 100) the difference is ≤ 0.004.
- **Needs the owner's decision.** It revisits D-072 and changes an output interface, so it needs a DD
  row.

## Deliberately left alone

- **Night Light service-on vs service-off writes:**
  - The two paths share the order bug, and the existing plan fixes it in both.
  - They otherwise differ by design: anchors, restore and ticks on one side, a device-snapshot diff on
    the other.
  - After that plan lands, at most a tiny shared write-order helper.
- **Brightness curve chart:**
  - It plots the base curve, but its "Now" marker shows the final output, which includes
    scale/offset/taper.
  - So the marker can sit off the line. That is a preview, by design (Tasker scene). Owner decision D4
    picks between a label and a change.
- **Alpha response chart, and the wizard's own curve evaluator:**
  - The alpha chart is a transcribed Tasker preview.
  - The wizard evaluator is a different Tasker algorithm: inclusive bounds, `safePowDelta`, clamped
    `form3A`. AGENTS.md says to preserve quirks.
- **Brightness conversions:**
  - Settings 0–255 conversion, display calibration and window brightness: different APIs.
  - The conversion path is frozen (DC-011…).
- **Settings contract, defaults and limits:**
  - The contract's default strings were checked and match the code.
  - A schema rewrite is not light.
  - Domain animation defaults differ from the app's, but production always passes the app's.
- **Lower-value items:**
  - `evaluate` vs `calculatedBrightness`: different rounding contracts.
  - Lux formatting precision per screen.
  - Override tolerances 2 vs 1: different questions.
  - `round3`/`bigScale3`: parity-specific rounding.

## Owner decisions (all decided 2026-10-21)

1. **D1. The dimming readout when Extra Dim is not engaged** (no privilege, or a level that rounds
   to 0). **Decided: 0**, which is truthful. Rejected: keeping the computed intent with a "not
   applied" flag.
2. **D2. The label words for the two brightness values.** **Decided:** "Perceived" and
   "Hardware (written)".
3. **D3. The PWM dimming chart (Unit 3).** **Decided: yes.**
4. **D4. The brightness curve chart.** **Decided: keep the base curve**, labelled as the base curve.
   Rejected: plotting the effective output.
5. **D5. Unit 6.** **Decided: yes**, after Units 1–5, as its own behaviour fix.

## Execution rules

- **Units.** One unit at a time, each SHIPPABLE:
  - a STATE Changelog line;
  - `scripts/ladder.sh` green;
  - the commit;
  - `--guards-only`;
  - a push to the session branch.
- **Reviews.**
  - Units 1, 2 and 6 touch runtime glue, so they get the glue review.
  - Each code unit gets a Sol review before its commit.
- **Records.**
  - Behaviour-changing units (1, 2, 3, 6) get DD rows and ride the train's next version bump; they
    never bump on their own.
  - Code cites DD rows, never this file.
- **Comment budget.** A comment-budget raise, if one is needed, is a rule change and is asked for
  first.

## Units (sequential)

0. Commit this plan, and add the STATE `## Active work` checklist. **Stop.**
1. Dimming plan, one publish path, zeroed on disengage (finding 1, D1).
2. Brightness readout definitions and labels (finding 2, D2).
3. PWM-aware dimming chart (finding 3, D3).
4. `keepGlobalsFrom` (finding 4).
5. Small pure-formula dedups (finding 5).
6. Optional: engine-sourced `dimDynamic` (finding 6, D5).
7. Owner device check of Units 1–3 in PWM mode, then close out: delete this file once its content
   lives in STATE and the ledger.
