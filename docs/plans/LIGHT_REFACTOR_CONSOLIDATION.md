# Light refactor: one source for each duplicated calculation

## Status

- **Spec only. No code has changed.** The owner asked on 2026-10-21 for a plan to lightly refactor
  Tideo where logic is duplicated or implemented differently, then authorised **Unit 0 only**:
  committing this file. Every later unit needs the owner's separate go-ahead.
- The owner accepted every recommendation in Owner decisions D1–D5 (2026-10-21).
- Three independent audits fed it: Opus, Astra (`gpt-6-astra`, high effort) and an earlier ChatGPT
  review of v1.14.1. Every finding below was re-checked against `math-explained` at `20ad58b`. A
  finding that one model made and the code did not bear out was dropped.
- **Re-read against the tree at `2ccac7e` on 2026-11-11** (owner asked; DE-036). All seven findings
  held. The owner accepted the corrections it produced, and they are folded in below:
  - Unit 1 publishes from the coordinator, and takes the dim multiplier as its input;
  - Unit 2 names the Curve card label and its two hand-off cases;
  - Unit 3 records the ordinary-mode chart as retained;
  - Unit 4 is a behaviour change;
  - Unit 5 reuses the hand-off helper that already exists;
  - the conversion freeze is gone from Unit 7's entry.
- Rows this plan asks for go in the live ledger volume, whatever its prefix is by then.
- It does not touch the Night Light write paths, which had their own plan, now closed
  (DD-090…DD-093). The two plans share no files.

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
| 6 | Circadian dim multiplier | runtime rebuilds it from the rounded `scaleDynamic` (D-072); `DynamicScaleEngine` computes it directly, and the chart uses that | Small: up to 1 dim level at extreme spreads | 6 |
| 7 | Nothing stops the next copy | no "one source" rule; deliberate differences recorded only in this plan | Future drift | 7 |

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
    once from target, settings and the circadian dim multiplier.
  - **Its input is the multiplier, not the scale.** `DimmingCoordinator.apply` takes `dimDynamic`
    in place of `scaleDynamic`. The pipeline derives it with `circadianDimMultiplier` until Unit 6
    swaps the source, so Unit 6 changes no dimming signature and none of this unit's tests.
  - **The coordinator publishes.** It is built with a readout callback. `apply` publishes the
    plan's readout when it engaged and zeros when it did not.
  - **Disengage.** `disengage` publishes zeros itself, before its `engaged == false` early return.
    None of the 8 call sites changes, and a ninth cannot miss it. The call sites are the wrong
    place: `PanicHandler` holds no pipeline state.
  - **Pipeline.** One pipeline helper calls `apply` for both `runCycle` and `setInitialBrightness`,
    which removes the duplicated floor → write → dim tail of those two functions. Neither
    function writes `dimmingCurrent`/`dimmingDS` itself any more.
  - **Wiring, for the unit to settle.** `AppModule` builds the coordinator before the controller
    that owns the state, so the unit picks how the callback is bound.
  - **Glue review.** `stop()` and the panic path call `disengage` off the pipeline coroutine. The
    state update is atomic and `stop()` already updates state there; the review checks the publish
    against D-027 all the same.
  - **Keep.** Keep the task650 `DS × progress` relative readout as it is.
- **Tests:**
  - the readout after `setInitialBrightness`;
  - the readout after an override pause;
  - the readout below ELEVATED;
  - the readout is 0 after a failed level write, and after a failed activation (D1's rule: not
    engaged reads 0);
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
  - Live Debug already shows both, as "Current" (hardware) and "Target" (perceived). They become
    "Hardware (written)" and "Perceived";
  - the Dashboard's "Applied" label stops describing a perceived number;
  - the Curve card's "Current brightness" (`diag_curve_current_bright`) shows the perceived value
    under the words Live Debug uses for the hardware one, so it takes the perceived label;
  - the `DiagnosticCard` circadian and dimming lines say "hardware".
- **No value changes outside hand-off.** Only selection and labels change, with one exception and
  one line that deliberately stays as it is:
  - **Live Debug's perceived line reads `shownBrightness`.** Today it reads `targetBrightness`,
    which is not gated on hand-off, so it shows the last target from before Android took over.
    After the change it shows what the Dashboard shows.
  - **The `DiagnosticCard` circadian and dimming lines keep reading `lastAppliedBrightness`.** They
    describe Tideo's own write, and show "—" while handed off. `currentBrightness` would put
    Android's value beside Tideo's scale and dim level. This is the one consumer that does not go
    through the two definitions, and the KDoc says why.
- **Strings.** Changed strings join Owner-queue item 1 (untranslated Chinese strings).

### 3. Dimming chart follows the active mode

- `DimmingChart` takes `pwmSensitive` and `pwmExponent`.
- In PWM mode it plots `SoftwareDimming.finalDimLevel` over the same x-range, so the live marker sits
  on the curve the runtime uses.
- This departs from Tasker's dimming graph (`dimming_graph.md`), so it needs a ledger row.
- **Ordinary mode stays as Tasker draws it.** There the chart plots `dimmingstrength × dim_progress`
  without the circadian multiplier the runtime applies. That is the transcribed graph, and the
  circadian effect has its own chart beside it. It is listed under Deliberately left alone so that
  Unit 7's preview clause does not read as an instruction to change it.

### 4. One helper for global fields kept across a swap

- `AabSettings.keepGlobalsFrom(current)` holds the list that `mergeProfile` holds today.
- Manual load, import and reset call it. They state their own extras at the call site:
  - the context lock;
  - import's preserved secure-display fields.
- **This changes behaviour, so it gets a ledger row.** Manual load and import take `setupTitle` and
  `schemaVersion` from the incoming profile today, and reset takes the defaults. With the helper
  all three keep the current ones, as a context swap already does. Nothing reads either field in
  a way a user would see: the schema migration only raises the number, and the stored settings
  are already at the current one.
- **The draft stays separate, at both of its sites** in `DraftSettingsViewModel`: the rebase in
  its settings collector, and the commit in `apply`. The draft edits `detectOverrides` on purpose,
  so neither takes the helper.
- **Not a blanket copy.** `GlobalPrefs` is the wrong boundary, because it also holds per-profile
  fields (`ContextEngine.kt` KDoc on `mergeProfile`).
- **Test.** One test asserts that every swap path keeps the same global set. The two draft sites
  are not swap paths and are outside it.

### 5. Small pure formulas, one copy each (no behaviour change)

- **Validator.** `SettingsValidator` calls the raw `BrightnessEngine.mapLuxToBrightness(1000.0, …)`
  instead of its own three zones. Keep it raw: no scale, offset or clamp. It also calls
  `AabSettings.minRequiredMaxBrightness()` instead of recomputing it. That helper moves from
  `app.state` (`SettingsViewModel.kt`) to `app.settings`, so the validator imports nothing from
  `state`.
- **Wizard.** `AabSettings.withCurveSuggestion(cfg)` replaces the two five-field copies in
  `ToolsScreen`. Preview and Apply keep their different validation timing.
- **Hand-off lux.** The helper already exists: `HandoffRules.effectiveThreshold(storedLux, cfg)`
  is in `:domain`, tested, and has no production caller. Do not add another.
  - Add an overload that takes the minimum, `effectiveThreshold(storedLux, minimum)`, and have the
    existing one delegate to it.
  - It replaces `maxOf(...)` in `HandoffCoordinator` and `HandoffScreen`. Both already hold the
    minimum, and the coordinator caches it because the scan behind it runs up to 100 000 steps.
- **Distance.** `ContextEngine`'s location debounce uses `ContextMatching`'s haversine and Earth
  radius.

### 6. Take the circadian dim multiplier from the engine (D5: yes)

- **Change:**
  - `BrightnessEngine.dynamicScale` passes `dimSpreadPercent` and publishes `dimDynamic`.
  - The pipeline passes that to `apply` in place of the value it rebuilds, and
    `circadianDimMultiplier` is deleted. Unit 1 already made the multiplier the input, so no
    dimming signature changes here.
  - **Keep the gate that function holds.** Task646 applies the multiplier only when
    `%AAB_ScalingUse` is on (D-072), so with circadian scaling off the pipeline still passes none.
- **Why it changes behaviour.** This is Tasker task90's own value. The current rebuild divides the
  3-decimal-rounded scale by `ScaleSpread`, which multiplies the rounding error by up to 100.
  - Worked example (Astra): ScaleSpread 1, DimSpread 100, strength 25. The chart says 1.540, the
    runtime 1.500: level 39 vs 38.
  - At the defaults (15 / 100) the difference is ≤ 0.004.
- **Needs the owner's decision.** It revisits D-072 and changes an output interface, so it needs a
  ledger row.

### 7. Keep it from coming back (owner, 2026-10-21)

Units 1–6 fix today's copies. Nothing stops a fresh session from writing the next one, or from
"fixing" a deliberate difference listed below. This unit narrows both gaps in prose; it cannot
close them, because nothing mechanical enforces either one. It is legislation, so the rule-review
protocol applies (see Execution rules).

The text below was revised after an Astra review of the first draft (2026-10-21). That review found
four gaps:
- no clause covered an entry point that skips the shared path;
- searching first did not require reuse;
- calling the same function did not mean using the same inputs;
- "deliberate plus a citation" let a session exempt itself.

It also found three over-reaches:
- the wording could pull presentation choices into `:domain`;
- the wording could sweep in golden fixtures;
- it could turn scope deferrals into permanent declines.

- **AGENTS.md → Conventions: a "one source" paragraph.** It carries its own Tasker precedence, so
  nothing in it depends on this file. The rule review may trim it, but two clauses must survive:
  the every-entry-point clause, and the same-inputs clause.

  > **One source for each calculation and policy.** Before adding or changing production logic,
  > find its existing owner and callers (`python3 scripts/codemap.py feature <name>` and
  > `impact <file>`, per AGENTS.md's codemap sentence). Reuse one implementation for the same formula, readout
  > selection or preserved-field policy; keep mathematics in `:domain` and presentation policy in
  > its owning layer. Consumers use the shared definition matching what they claim to show.
  > Runtime previews use the same calculation and applicable mode, inputs and rounding contract.
  > Check every applicable entry point, including reapply and disengage, for shared application
  > and publication. Preserve Tasker semantics and independent golden/reference fixtures. Distinct
  > contracts may stay separate; record the reason in a ledger row and cite it at the
  > implementation. Read `docs/rebuild/DECIDED_NON_ITEMS.md` before proposing consolidation; only
  > the owner can reopen its declines. Prose only: no guard detects duplication or missing callers.

  - **A citation must earn the exemption.** The cited row has to show that the two contracts
    differ. A row cannot reopen an owner decline. The citation guard does not scan docs, so the
    rule review checks every row this paragraph cites.
  - **No conflict with AGENTS.md → Invariants.** The task663 plotting copy kept for
    cross-validation is a distinct contract. It stays.
- **`docs/rebuild/DECIDED_NON_ITEMS.md`: one entry, binding declines only.** Shape (the row
  citations are filled in and verified when the unit lands):

  > **Deliberate differences retained by the light-refactor decision (owner, 2026-10-21;
  > `<ledger-row>`).** Do not propose unifying these contracts unless the owner reopens them:
  > - Alpha remains the transcribed Tasker preview; the brightness chart retains its labelled base
  >   curve and final-output marker.
  > - The ordinary-mode dimming chart remains the transcribed Tasker graph, without the circadian
  >   multiplier.
  > - The wizard evaluator retains its distinct Tasker bounds, power handling and clamp.
  > - The brightness setting, display calibration and window brightness retain their own API
  >   contracts. This does not bar a change to any one of them, DE-019's included.
  > - Override tolerances 2 and 1 answer different questions.
  > - `evaluate`/`calculatedBrightness` and `round3`/`bigScale3` retain their rounding contracts.
  > - Per-screen lux precision and domain/app animation defaults retain their distinct uses.
  > - Night Light retains separate service-on/off state machines; this does not bar the planned
  >   fixes or a shared write-order helper.

- **Scope deferrals are not declines.** These go in a ledger row as scope and evidence, never in
  Decided non-items:
  - the settings schema rewrite ("not light");
  - the contract default-string audit (checked, all equal).

  A later plan may take them up without reopening anything.
- **Rows.** Only Units 1, 2, 3, 4 and 6 write ledger rows, and they cover what those units
  change. This unit writes the row behind the Decided non-items entry, and any row a retained
  difference lacks.
  It verifies every row it cites.
- **Why prose and no guard.** A duplication or missing-caller detector would be either noisy or easy
  to satisfy without meeting its intent; DA-004 records that Goodhart risk. The tests that Units 1–5
  add are regression coverage for sampled behaviour, not enforcement of this rule. They add no
  Graph Metrics wiring tests, which Decided non-items declines.

## Deliberately left alone

- **Night Light service-on vs service-off writes:**
  - The two paths share the order bug, and the existing plan fixes it in both.
  - They otherwise differ by design: anchors, restore and ticks on one side, a device-snapshot diff on
    the other.
  - At most a tiny shared write-order helper, which is that plan's Unit 3 (owner, 2026-10-25).
    It was not landed, since it came out longer than the inline orders (DD-092).
- **Brightness curve chart:**
  - It plots the base curve, but its "Now" marker shows the final output, which includes
    scale/offset/taper.
  - So the marker can sit off the line. That is a preview, by design (Tasker scene). Owner decision D4
    picks between a label and a change.
- **Alpha response chart, and the wizard's own curve evaluator:**
  - The alpha chart is a transcribed Tasker preview.
  - The wizard evaluator is a different Tasker algorithm: inclusive bounds, `safePowDelta`, clamped
    `form3A`. AGENTS.md says to preserve quirks.
- **Dimming chart in ordinary mode:**
  - It plots Tasker's `dimmingstrength × dim_progress` and leaves out the circadian multiplier the
    runtime applies. That is the transcribed graph (`dimming_graph.md`).
  - Unit 3 changes the chart in PWM mode only.
- **Brightness conversions:**
  - The brightness setting, display calibration and window brightness: different APIs.
  - The setting is no longer converted at all. From 1.15.3 Tideo reads and writes it as 0–255 on
    every device (DE-023).
  - Nothing here is frozen. The freeze DC-019 named ended with the owner's ruling in DC-026, DE-023
    has since overruled part of that ruling, and DE-019 holds the fix for a device shown to read a
    wider scale. Learning the scale stays declined (DC-026).
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
  - Behaviour-changing units (1, 2, 3, 4, 6) get ledger rows and ride the train's next version
    bump; they never bump on their own.
  - Code cites ledger rows, never this file.
- **Comment budget.** A comment-budget raise, if one is needed, is a rule change and is asked for
  first.
- **Rule review for Unit 7.** Unit 7 edits `AGENTS.md` and Decided non-items, so it runs the
  rule-review protocol (DA-005):
  - a fresh-context reviewer at the strongest tier;
  - no self-review fallback;
  - the verdict goes in the commit body.

  It runs after Units 1–6, so that the rows it cites exist.

## Units (sequential)

0. Commit this plan, and add the STATE `## Active work` checklist. **Stop.**
1. Dimming plan, one publish path, zeroed on disengage (finding 1, D1).
2. Brightness readout definitions and labels (finding 2, D2).
3. PWM-aware dimming chart (finding 3, D3).
4. `keepGlobalsFrom` (finding 4).
5. Small pure-formula dedups (finding 5).
6. Engine-sourced `dimDynamic` (finding 6, D5).
7. AGENTS.md "one source" paragraph, the Decided non-items entry and the scope-deferral row
   (finding 7), under rule review.
8. Owner device check of Units 1–3 in PWM mode, then close out:
   - confirm that every item under "Deliberately left alone" has a lasting record: a Decided
     non-items line, a ledger row, or a landed plan;
   - then delete this file.
