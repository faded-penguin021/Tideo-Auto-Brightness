# parity_gaps.md — engine ↔ Tasker-oracle divergences (S4 → S5)

S4 built the Tasker reference oracle (`domain/src/test/.../reference/TaskerReference.kt`) and
committed golden vectors (`domain/src/test/resources/golden/*.csv`). `CorePipelineParityTest`
asserts the current production `BrightnessEngine` against those vectors at tolerance `1e-9`.

**Status (S5 closed all 7 gaps):** 0 `@Ignore` annotations remain in `domain/src/test`. All
production engine helpers (`smoothLux`, `dynamicThreshold`, `absoluteThresholds`,
`mapLuxToBrightness`, `compressedDynamicScale`, `calculateAnimation`, `BrightnessFormulae`) pass
their golden-vector parity tests at 1e-9. `SoftwareDimming` (`finalDimLevel`, `dimShell`) is
additionally covered by `superdimming.csv` (2016 rows). `OverrideRules` and `InitialBrightness`
are covered by dedicated unit-test classes. See STATE.md D-030 for residual deviations.

The rows below are preserved as a historical record of each gap's root cause and S5 fix.

## Root-cause summary

Two systemic causes account for all gaps, both already foreseen (risk register #2, D-010):

- **R1 — rounding tie semantics.** Tasker's Java uses `Math.round` (ties toward **+∞**) and
  `BigDecimal(double).setScale(n, HALF_UP)`. The production engine uses `kotlin.math.round`
  (ties to **even**). They differ only on exact half-way values, but those occur in the golden
  grid (e.g. `Math.round(296.5)=297` vs `kotlin.math.round(296.5)=296`). **S5 fix:** route every
  engine rounding through Java `Math.round` / BigDecimal helpers mirroring `TaskerReference`.
- **R2 — clamping/structure the engine added that Tasker does not have** (D-010a/b): luxAlpha
  coercion, mapped-brightness clamping, threshold special-cases, and the always-on compressed-scale
  path. **S5 fix:** move/remove the coercions to match the Tasker control flow.

---

## gap-01 — smoothing (task535) — `smoothing_matchesEngine`

- Vectors: `golden/smoothing.csv` (16 512 rows), engine helper `BrightnessEngine.smoothLux`.
- Divergences:
  - **R2:** `BrightnessEngine.smoothLux` clamps `luxAlpha.coerceIn(0,1)`; task535 does **not**
    clamp (D-010a). When `effective_delta < 0` (raw lux within the dynamic threshold of the
    previous smoothed value), the oracle's `lux_alpha` is negative and the smoothed value moves
    *past* `par2`; the engine pins `alpha=0` and holds. Many small-delta rows differ.
  - **R1:** final smoothed value — oracle uses `BigDecimal(raw).setScale(2|0, HALF_UP)`; engine
    uses `roundN` over `kotlin.math.round`. Differs on exact halves and on exact-binary edge
    values the `*100/100` idiom cannot represent.
- Reference value vs engine value: see test failure dump (`engine=(…) ref=(…)`).
- **S5:** drop the `coerceIn` on `luxAlpha`; replace `roundN(_,2)`/`round(_)` final rounding with
  BigDecimal HALF_UP matching task535.

## gap-02 — absolute thresholds (task546) — `absoluteThresholds_matchesEngine`

- Vectors: `golden/threshold.csv` columns `threshAbsLow/High` (688 rows); helper
  `BrightnessEngine.absoluteThresholds`.
- Divergences:
  - **R2:** task546 has a hard special-case `par1 < 0.2 → ("1","0","0.1")` (132/688 grid rows);
    the engine has none.
  - **R1:** task546 rounds via BigDecimal HALF_UP to 2-dp (`par1<10`) or 0-dp (`par1≥10`); the
    engine returns the raw unrounded product.
- **S5:** port the `<0.2` and `<10/≥10` BigDecimal-rounded branches into the threshold writer.

## gap-03 — lux→brightness mapping (task661) — `mapping_matchesEngine`

- Vectors: `golden/mapping.csv` column `mappedRaw` (688 rows); helper
  `BrightnessEngine.mapLuxToBrightness`.
- Divergences (**R2**, D-010b): the engine clamps the mapped value to `[minBright, maxBright]`
  *inside* the mapping helper (361/688 grid rows clamp), and wraps the `^0.33` bases in
  `coerceAtLeast(0.0)`. task661 does **neither** — `mapped_brightness` is raw; the `[min,max]`
  clamp happens only later, on `calculated_brightness` *after* scaling + offset (act16–21). Order
  matters when `mapped < minBright`: Tasker scales the sub-floor value first, the engine floors it
  first. (The `coerceAtLeast` never bites for in-zone lux but is still non-faithful.)
- **S5:** return raw mapped brightness from the mapping; apply the `[min,max]` clamp at the end of
  the calculated-brightness pipeline; drop the `coerceAtLeast` wrappers. Cross-check against the
  `calculatedBrightness` reference path which encodes the correct clamp position.

## gap-04 — animation (task543) — `animation_matchesEngine`

- Vectors: `golden/animation.csv` (927 rows); helper `BrightnessEngine.calculateAnimation`.
- Divergence (**R1**): e.g. `alpha=0.5, animSteps=20` → `loops = round(1 + 0.5·19) = round(10.5)`;
  oracle `Math.round=11`, engine `kotlin.math.round=10`. Same tie issue can hit `wait`/`throttle`.
- **S5:** compute `loops`/`wait`/`throttle` with Java `Math.round` semantics.

## gap-05 — dynamic threshold (task544) — `dynamicThreshold_matchesEngine`

- Vectors: `golden/threshold.csv` column `dynamicThreshold` (688 rows); helper
  `BrightnessEngine.dynamicThreshold`.
- Divergence (**R1**): e.g. `lux=2.45` linear branch
  `0.3 − ((0.3−0.25)/35)·2.45 = 0.2965`; oracle `round3 → Math.round(296.5)=297 → 0.297`, engine
  `kotlin.math.round(296.5)=296 → 0.296`. Formula/structure otherwise identical.
- **S5:** route `round3` through Java `Math.round`.

## gap-06 — compressed dynamic scale / taper (task548) — `taper_matchesEngine`

- Vectors: `golden/taper.csv` column `calculatedBrightness` (1148 rows); helper
  `BrightnessEngine.compressedDynamicScale`.
- Divergence (**R1**): e.g. `mapped=6.5, scale=0.5` final `round1` tie — oracle `3.3`, engine
  `3.2`. The sigmoid/cap/floor structure matches; only the rounding ties differ.
- **S5:** route `round3`/`round1` through Java `Math.round`.

## gap-07 — contract test fixture wrong — `rapidLuxSpike_isSmoothedByTaskerFormula`

- Pre-existing failure flagged in D-019, now CHARACTERIZED by the oracle: for the fixture
  (lux 20→800, deltaFactor 1.8) `effective_delta ≈ 37.1` ⇒
  `lux_alpha = round3(1 − exp(−1.8·37.1)) = 1.0` in **both** the engine **and** the Tasker
  reference. The test's assertion `luxAlpha < 1.0` is therefore **incorrect** — a delta that large
  legitimately yields `alpha = 1.0` (no smoothing, snap to raw). This is a bad test expectation,
  not an engine bug.
- **S5:** rewrite the contract assertion to a fixture whose delta actually produces `alpha < 1.0`
  (e.g. a small lux step), or assert `alpha == 1.0` for this spike; then remove the `@Ignore`.
  Logged here so S5 does not "fix" the engine to satisfy a wrong test.

---

## Cross-validation: task661 (runtime) vs task663 (plot copy) — PASS (no gap)

`mapping661VsPlot663_agree` runs both 3-zone formulas over the full golden lux grid × all variants
and asserts equality at `1e-9`. They agree by construction: task661's zone-2 upper anchor is
`%AAB_Form2D`, which `defaults_audit.md` (D-008/D-025) confirms is the derived value `≡ Zone1End`,
exactly what task663 hard-codes. No disagreement found → nothing to re-derive from XML.
