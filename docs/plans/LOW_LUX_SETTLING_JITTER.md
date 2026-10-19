# Low-lux settling jitter (v1.11.0, DC-070 follow-up)

## Status

Spec only. No code has changed. The owner approved the rule under **Proposed fix** as written
(2026-10-08). It was meant for the train after 1.14.0, but the owner deferred it to the train
after 1.15.0 (2026-10-17), and each step needs the owner's go-ahead. That train makes its own
version bump. The new
ledger row recording the DC-070 departure lands with the code.

**Reopened 2026-10-19.** The owner's dark-room readings (**Device findings** below) show that the
approved rule never fires on the owner's phone: its sensor never reports a value between 0 and
1 lx. The approved rule stays on record, but nothing is built until the owner picks one of the
options under **Options after the device findings** (Owner queue).

## Symptom (owner report, 2026-09-27)

In a dark room, brightness jitters (2↔3 on the 0–255 scale) as the sensor flickers between
0 and a few tenths of a lux. It started with v1.11.0. The owner describes it as "the deadband
doesn't kick in until a few tenths of lux higher." The Live Debug screenshot shows:
smoothed 0.56 lx, raw 0 lx, dynamic threshold 34.9 %, and dead zone **0 – 0**.

The settling path (DC-070) that fixed the #132 light stall is sensible and desirable and must
stay. Only the band from just above 0 lx up to about 1 lx needs fixing.

The "few tenths of a lux" above turned out to be **smoothed** lux, not raw readings. See the
next section.

## Device findings (owner's OnePlus, 2026-10-19)

The owner watched the Light Sensor card in Live Debug, and separately had Tasker flash
`%as_values1`, in a dark room.

- **Raw readings are 0, or about 1.04 lx and up. Nothing in between.** The lowest non-zero values
  Tasker showed were `1.0417844` and `1.0856172`. Live Debug showed `0,0`, `1,0`, `1,1` and
  `1,2` lx. Neither tool ever showed a value strictly between 0 and 1.
- **Tideo does not floor or round the reading.** `LightSensorSource` passes `event.values[0]`
  through as a `Float`, and the runner feeds it to the engine as a `Double`. The engine stores
  `lastRawLux` at 3 dp (`bigScale(lux, 3)`, task554). Only the Light Sensor card rounds it, to
  1 dp (`fmt(it.lux)`), so `1.0417844` shows as `1,0`. That is why the owner saw `1,0` in Tideo
  but never `1.0` in Tasker.
- **The values look like a sensor computation, not a fixed quantum.** Both are exact float32
  values (`0x3F855931`, `0x3F8AF581`). They differ by 0.0438, and neither is a clean multiple of
  any common step. That fits a HAL that computes lux from raw ADC counts with calibration
  coefficients (often from two channels, visible and IR) and reports 0 below a noise cutoff
  near 1 lx. The cutoff is a property of this phone's sensor stack. Phones that report tenths of
  a lux exist, so a fix must not assume either behaviour.
- **The sensor fires about 4 times a second in the dark** (167 callbacks in 44 s, and 74 in
  19 s). Most are refused: 118 of 167 were `DEAD_BAND`.

### What the jitter actually is

The 1.04 / 1.1 / 1.2 lx wobble is harmless. Those are relative changes of about 10 %, well
under the roughly 35 % dark threshold, so they are dead-band stops and refusals.

The jitter is the switch between 0 and ≥ 1.04 lx. From smoothed 0, a reading of 1.04 is a
104 % change. From smoothed 0.7, a reading of 0 is a 41 % change. Both clear the threshold, so
each switch smooths and settles as a real change and writes a new brightness: the minimum at
0 lx, and the curve at about 0.7–0.9 lx (`form1A·√lux` ≈ 4.2–4.6 before scaling). Simulated with
the engine maths (threshDark 0.35, the runner's settling loop approximated):

```
raw 1.0 → SMOOTHED  sm=0.83  band 0.65–1.35
raw 1.2 → STOP      sm=0.83                 (1.0–1.2 wobble ignored)
raw 0   → SETTLED   sm=0.00  band 0–0       (brightness to minimum)
raw 1.0 → SMOOTHED  sm=0.69                 (brightness back up)
raw 1.1 → SETTLED   sm=0.72                 (DC-070 placement; a tiny nudge)
```

The DC-070 placement this plan was written about still happens. Here it is only a nudge of a
few hundredths of a lux and does not cause the visible jitter.

### Why the approved rule does not help on this phone

The approved rule holds only a **first-cycle `DEAD_BAND_STOP` with 0.2 ≤ raw < 1 lx**. On this
sensor:

- no reading ever lands in `[0.2, 1)`, and
- the 0 ↔ 1.04 switch is never a stop, because it always clears the relative threshold.

So the rule would be built, tested and shipped without changing anything on the owner's phone.
Moving its upper edge above 1 lx would not help either, because the hold applies only to stops.
The verification sweep's flicker amplitudes (0.1–0.9 lx) also miss this case.

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

## Proposed fix (owner-approved, 2026-10-08)

> On hold since 2026-10-19: it does not reach the owner's phone (**Device findings**). Kept as
> written, because the owner may still choose it for sensors that do report tenths of a lux.

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

## Options after the device findings (owner decision pending, 2026-10-19)

All three options share these requirements:

- **The engine stays untouched**, so golden vectors and `LightCycleParityTest` stay
  byte-identical. Each option is a runner or admission change.
- **They act only on a fresh reading while the state is settled** (smoothed lux inside the stored
  band). They never act on a settling continuation or mid-run. Otherwise a drop such as 42 → 0 lx
  could stall as it creeps through the last lux, which would reopen #132.
- **They act only in the dark:** while smoothed lux is below a named constant `L` (about 2 lx).
- **They are a Tasker departure** (Tasker has neither gate). Each needs a new owner-approved
  ledger row, and the `AGENTS.md` light-reading invariant gets a sentence naming it.

### Option A: an absolute dead band in the dark

While settled and below `L`, refuse a fresh reading that is within `S` lx of smoothed lux (`S` a
named constant, about 1.5 lx, just above this sensor's 1.04 lx first step).

- On this phone, the 0 ↔ 1.04–1.2 switch is refused in both directions. Smoothed lux stays where
  it first settled, at 0 or at about 0.7, and the brightness stops changing.
- It also covers the original 0 ↔ tenths flicker on sensors that report tenths, because those
  changes are under `S` too. **It can replace the approved rule.**
- Cost: a real change smaller than `S` in the dark is not followed. With `form1A = 5`, 0 → 1.4 lx
  maps from 0 to about 5.9 before scaling, so a few steps at the dimmest end. If the room went
  dark from an already-dark settled state, brightness can stay at the about-0.7 lx level instead
  of the minimum. A light switched on (tens of lux) passes as before.
- Fits the concurrency model as it is: it is a refusal, like prof760's dead band.

### Option B: wait before acting on a switch between 0 and non-zero

While settled and below `L`, a fresh reading that switches between 0 and non-zero takes effect
only once readings stay on the new side for `T` seconds (about 2–3 s, 8–12 callbacks here).

- Follows every real change, only later, and loses no accuracy at the dimmest end.
- Cost: a held-back reading is a new kind of deferral. The concurrency model (DC-069) allows one
  pending slot and never queues readings, so this changes that model. `TYPE_LIGHT` is an
  on-change sensor: in steady light it can stop firing, so a count of callbacks may never
  complete and the wait needs a timer of its own. More code and more state than A.
- Does not cover the tenths flicker unless that is also treated as a switch, so the approved
  rule would stay as well.

### Option C: Option A with a per-device first step

Same as A, but `S` comes from this device: either a setting, or learned as the smallest non-zero
reading seen (here 1.04 lx), plus a margin.

- Exact on every sensor, so phones that report tenths keep their full low-lux resolution.
- Cost: a learned value needs persisting and resetting, and a setting needs UI, help text, a
  schema entry and new strings, which adds to the untranslated Chinese strings in Owner queue
  item 1.

### Recommendation

**Option A, replacing the approved rule.** It is the smallest change, it stops the jitter on this
phone and on tenths-reporting sensors, and it leaves the concurrency model alone. Its cost is a
few brightness steps at the very dimmest end, which is the trade-off a dead band exists to make.
C can follow later if a fixed `S` proves too coarse on some phone.

### Side fix, independent of the choice

Show the raw reading on Live Debug's Light Sensor card at its stored precision (`fmtLux`,
3 dp) instead of 1 dp. `1,0 lx` for a 1.0418 reading hid this sensor's real first step.

## Verification plan (all must pass before commit)

These steps were written for the approved rule. Whichever option is chosen also needs:

- runner tests replaying the owner's sequence (0, 1.04, 1.09, 1.2, 0, 1.04 …) that show no
  brightness write after the first settle;
- the sweep below with flicker values of 1.0–1.3 lx added, as well as 0.1–0.9 lx;
- the dark-room device check repeated on the owner's phone, watching `APPLIED` cycles on the
  Light Sensor card.

The steps for the approved rule:

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
