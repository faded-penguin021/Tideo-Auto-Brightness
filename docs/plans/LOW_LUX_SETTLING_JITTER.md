# Low-lux settling jitter (v1.11.0, DC-070 follow-up)

## Status

Spec only. No code has changed. The approved action is committing this plan to `docs/plans/`.
The owner must approve the rule under **Proposed fix** before anyone implements it.

## Symptom (owner report, 2026-09-27)

In a dark room, brightness jitters (2↔3 on the 0–255 scale) as the sensor flickers between
0 and a few tenths of a lux. It started with v1.11.0. The owner describes it as "the deadband
doesn't kick in until a few tenths of lux higher." The Live Debug screenshot shows:
smoothed 0.56 lx, raw 0 lx, dynamic threshold 34.9 %, and dead zone **0 – 0**.

The settling path (DC-070) that fixed the #132 light stall is sensible and desirable and must
stay. Only the band from just above 0 lx up to about 1 lx needs fixing.

## Mechanism

The code has two dead bands, and below about 1 lx they disagree:

| Dead band | Where | Test | Width near 0 lx |
|---|---|---|---|
| Relative stop (task544 act19–23) | `BrightnessEngine.evaluate` → `stop` | `abs(raw − smoothed) / (smoothed + 1) < d` | wide: the `+1` gives about ±0.35 lx |
| Stored band (task546) | `absoluteThresholds`, centred on `lastRawLux` | `raw × (1 ± d)` | about ±0.1 lx at 0.3 lx; **0 – 0** at raw 0 when par1 ≥ 0.2 |

Before v1.11.0, a flicker the relative stop caught changed nothing. DC-070 added one rule: while
smoothed lux lies outside `settledRange(stored band, reading)`, the reading counts as
*unsettled*. The next cycle is then a settling step, and a settling step that does not move
closer is placed on the nearest band edge (`settlingPlacement`). **A stop never moves closer,
so it gets placed immediately.** Every flicker therefore re-centres the narrow band on the new
raw reading and pulls smoothed lux onto its edge.

Simulated at `threshDark = 0.35` (the owner's 34.9 %), with readings 0, 0.4, 0, 0.3, 0, 0.6, …:

```
r=0.0  DEAD_BAND_STOP sm=0.26 band=0.0-0.1   → step 1 SETTLED sm=0.1  tgt 3→2
r=0.3  DEAD_BAND_STOP sm=0.1  band=0.2-0.41  → step 1 SETTLED sm=0.2
r=0.6  SMOOTHED       sm=0.38 band=0.39-0.81 → step 1 SETTLED sm=0.39 tgt 3
r=0.0  DEAD_BAND_STOP sm=0.39 band=0.0-0.1   → step 1 SETTLED sm=0.1  tgt 2
```

The owner's screenshot shows a different state: a DC-070 stall in progress. 0.56 / 1.56 =
0.359 > 0.349, so that cycle smooths with α ≈ 0. Settling exists to finish exactly that stall.

Above about 1 lx the two bands roughly agree, because `+1` is small next to `smoothed`. That is
why the jitter only shows in the dark.

## Rejected fix: "a stop is always settled"

The first attempt skipped placement whenever `stop` was true (engine), and marked a
`deadBandHeld` state flag so the runner stopped settling after a stop. It was tried and
reverted. It **fails 4 tests in `SettlingPlacementTest`**:
`anAct19StopOutsideTheBand_places`, `aReadingTheSpecialCaseBandExcludes_isPartOfTheSettledRange`,
`anAct19StopAtZero_landsOnTheSpecialCaseEdge` and `aPlacementJustOutsideAWideBand_keepsAPositiveAlpha`.

The one that matters is `anAct19StopAtZero`. A drop toward 0 lx can end its creep on a *stop*
(0.4 / 1.4 < 0.299) rather than an α-stall. Without placement, smoothed lux stays parked about
0.3 lx above the 0–0.1 band. That reopens #132, whose stalls mostly appear at 0 lx.

## Proposed fix (awaiting owner approval)

**Rule:** a *fresh* reading (not a settling continuation, not an unchanged repeat) with
**0.2 ≤ raw < 1 lx** whose *first* cycle is `DEAD_BAND_STOP` holds smoothed lux. No settling
follows it. Everything else behaves exactly as in v1.11.0.

- **Engine untouched.** `settlingPlacement` and all `SettlingPlacementTest` cases keep today's
  behaviour.
- **Runner only.** Suggested implementation: `PipelineState.deadBandHeld`, set by
  `PipelineCycleRunner` on a first-cycle (`settlingStep == 0`) stop whose reading falls inside
  `[0.2, 1)`. Cleared by any non-stop cycle and by the controller's state reset. `unsettled`
  returns false while it is set, so prof760's dead band and `continuationRejection` behave as
  they would for a settled state.

Why #132 stays fixed:

- Readings under 0.2 lx (task546's `0 – 0.1` special case) are never held, so a reading at 0 lx
  always settles smoothed lux into `0 – 0.1`. The 42 → 0 lx drop, the dark-room device check
  (DD-006) and `anAct19StopAtZero` all follow unchanged paths.
- A transition that starts with a real smoothing step still settles through all 20 steps, even
  if it finishes on a stop, because the hold applies only to a stop on the first cycle.

Why the jitter stops (0 ↔ 0.3–0.6 lx flicker):

1. The first 0 lx reading settles smoothed lux to 0.1. That is correct.
2. A flicker to 0.3 or 0.5 is a first-cycle stop inside `[0.2, 1)`, so smoothed lux is held at
   0.1.
3. The next 0 lx reading finds 0.1 already inside `0 – 0.1`. prof760 refuses it, and nothing
   moves.

So the output converges and stays still. A real rise that clears the relative threshold
(about 35 %) still smooths and settles normally.

## Considerations and trade-offs

- **Sub-threshold rises inside 0.2–1 lx are not followed.** That is what v1.10 and Tasker did,
  and it is the purpose of a dead band. At `form1A = 5`, 0.1 → 0.45 lx maps from about 1.6 to
  3.4 before scaling: at most about 2 steps held at the dimmest end.
- **This departs from DC-070's endpoint** ("smoothed lux inside the stored band") within the
  held case. That endpoint was an owner decision (Q2), so this needs a new owner-approved ledger
  row, and the `AGENTS.md` invariant that records the DC-070 departure needs updating.
- **Boundaries.** The 0.2 lower edge matches task546's `< 0.2` special case. The 1 lx upper edge
  is where `+1` stops dominating the relative test. Both edges should be named constants.
  Whether 1 lx is right for other `threshDark` values should be checked in the sweep below.
- **Tasker parity.** Tasker has no settling path (DC-071), so the held case is *closer* to
  Tasker than v1.11.0 is. Golden vectors and `LightCycleParityTest` must stay byte-identical,
  and they will, because the engine does not change.
- **Unchanged repeats.** A held state must also make prof760 refuse an identical repeat
  reading. `unsettled == false` covers this. A test should assert it, so no continuation is
  offered.
- **Override and pause paths.** The `Overridden` return in `PipelineCycleRunner` leaves
  `deadBandHeld` as it is. Confirm that is harmless, or clear the flag there too.
- **Alternative considered: a minimum stored-band width at low lux** (for example, `0 – 0.1`
  whenever raw < 0.2, or an absolute floor on the half-width). Rejected as the primary fix.
  It changes the admission gate and task546 parity, and it only narrows the jitter: settling
  would still pull smoothed lux onto the edge of any re-centred band.

## Verification plan (all must pass before commit)

1. `:domain:test` unchanged and green, including all of `SettlingPlacementTest` and
   `LightCycleParityTest`.
2. New runner tests (`BrightnessPipelineControllerTest` style):
   - A 0 ↔ 0.3/0.5 lx flicker after settling produces no brightness writes after the first
     settle.
   - 42 → 0 lx and 160 → 0 lx still end at smoothed ≤ 0.1 (the regression guard for #132).
   - A stop at 0 lx reached mid-settling is still placed.
   - A fresh 0.3 lx stop from smoothed 0.1 is held, and an identical repeat is refused as
     `DEAD_BAND`.
3. A sweep over `threshDark` 0.2–0.5, drops of 1–500 lx → 0, and flicker amplitudes of
   0.1–0.9 lx. No drop may end above the `0.1` edge, and no flicker may cause more than one
   write after convergence.
4. `:app:testDebugUnitTest`. It was blocked by Maven Central HTTP 429 on 2026-09-27, so retry.
5. `scripts/ladder.sh`.
6. Fresh-context review of the diff.
7. Device check in the dark room: DD-006's flashlight run, plus a hand-shaded sensor flicker
   near 0.3 lx.
