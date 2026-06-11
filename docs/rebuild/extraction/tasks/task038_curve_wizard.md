# task38 — `_SuggestCurveParameters V24 (Hybrid)` — Curve Suggestion Wizard

| Field | Value |
|---|---|
| Task id | `task38` (`<id>38</id>`) |
| Name | `_SuggestCurveParameters V24 (Hybrid)` |
| XML line range | L9779–L10913 (17 actions, `act0`–`act16`) |
| Big Java block | `act4`, `<code>474</code>` at L9921, source `arg0` L9922–L10868 (decoded: `docs/rebuild/extraction/java/task38_1_suggestcurveparameters-v24-hybrid.java`, "AAB Curve Fitting Engine V43.8") |
| Applier task | `task655 "_SetSuggestedVariables"` (L32574–L32829) — maps `%suggestion_*` → live `%AAB_*` |

---

## 5-line summary (algorithm + outputs)

The wizard fits a **3-zone piecewise brightness-vs-lux curve** to the user's recorded override points (`%AAB_Overrides1..N`, `"lux,brightness[,weight[,kind]]"`). Zone 1 = `form1a·√lux`; Zone 2 = `form2a + form2b·((lux−form2c)^0.33 − (z1end−form2c)^0.33)`; Zone 3 = `MaxBright − (form3a/lux)·MaxBright`. It searches the two zone boundaries (Zone1End, Zone2End) via a top-K candidate scan + approximate-cost global search + coordinate-descent refinement ("hops"), fitting each zone by **log-weighted least squares** (closed-form for Zone 1 `a` and Zone 2 `b`; gradient descent for Zone 2 offset `c`). Cost = `50·Σ nRMSE + Σ|bias| + size/regularization/R² penalties`. The winner is **blended with the current curve** by a confidence factor `1 − exp(−weightedCount/τ)` ("inertia regularization", τ = `%tau`), guaranteeing it never moves further than the data justifies. Outputs **8 `%suggestion_*` vars** (`zone1end, zone2end, form1a, form2a, form2b, form2c, form2d, form3a`) plus `%suggest`, a `%AAB_Test` log, and 4 human-readable `%suggest_r2_*` quality lines; **task655** writes these into the live `%AAB_*` settings (re-deriving `form2a` and `form3a` itself).

---

## 1. Actions in order

Tasker stores actions string-sorted (`act0, act1, act10, act11, …, act2, …`); the **execution order** is the numeric order below.

| # | sr | code | Meaning | Summary |
|---|----|------|---------|---------|
| 1 | act0 | 37 | If | `%AAB_Overrides(#) > 8` (array element count > 8 → at least 9 overrides). Gates the whole fit. |
| 2 | act1 | 548 | Flash (toast/overlay) | Shows "Commencing curve fitting." overlay (scene `aab_prediction`, color `#007C63`, `Top,0,77`). |
| 3 | act2 | 547 | Variable Set | `%tau` = `0.001` **if not already set** (`arg3=0` = don't overwrite is *false* here → see note). Label: inertia regularization factor; recommended 0.001–5, never 0. This is the **default** for the Java engine's `tau`. |
| 4 | act3 | 547 | Variable Set | `%suggest` = `error` (failsafe default; Java overwrites to `true` on success or leaves/forces `error` on abort). |
| 5 | act4 | 474 | **Java Code** | The entire fitting engine (see §3). Reads `%AAB_*` + `%AAB_Overrides*` + `%tau`; writes `%suggestion_*`, `%suggest`, `%suggest_r2_*`, `%AAB_Test`. |
| 6 | act5 | 49 | Stop / control | `arg0 = "AAB Brightness Settings"` (label/profile name argument — terminates or targets that context after the Java run). Code 49 — *verify exact semantics; treated here as a Stop/Go-to-end control with a named target.* |
| 7 | act6 | 37 | If | `%suggest ~ error` (matches `error`). |
| 8 | act7 | 548 | Flash | "⚠️ Fatal error. Aborted." overlay (scene `aab_prediction`). |
| 9 | act8 | 137 | Stop | Stops the task (abort path on fatal). |
| 10 | act9 | 38 | End If | closes act6. |
| 11 | act10 | 130 | Perform Task | Calls task "Advanced Auto Brightness" with priority `%priority` (returns to main app/scene). |
| 12 | act11 | 548 | Flash | Shows the result summary overlay: `%suggest_r2_1 / _r2_2 / _r2_3 / _r2_4` (the 4 quality lines), scene `aab_prediction`, `Bottom,0,232`, 10000 ms. |
| 13 | act12 | 547 | Variable Set | `%AAB_Test` = `%AAB_Test` (no-op assignment; likely forces variable materialization / trims). |
| 14 | act13 | 105 | Write/Flash file or log | `arg0 = %AAB_Test` — writes/append the diagnostic log. Code 105 — *verify; treated as write of the `%AAB_Test` log buffer.* |
| 15 | act14 | 43 | Else | else-branch of act0 (fewer than 9 overrides). |
| 16 | act15 | 548 | Flash | "⚠️ Not enough data points… ensure at least nine overrides." overlay (scene `%aab_toast`). |
| 17 | act16 | 38 | End If | closes act0. |

> Action-code notes (verify in `XML_RECIPES.md` census): 37=If, 38=End If, 43=Else, 49=Stop/control-with-target, 105=Write log var, 126=Return (used in task655), 130=Perform Task, 137=Stop, 474=Java, 547=Variable Set, 548=Flash. 105/49 exact semantics are not load-bearing for parity (they are UI/logging plumbing); the fit and the var I/O are fully captured.

---

## 2. INPUTS gathered (what feeds the wizard)

Gathered inside the Java block (`act4`) via `tasker.getVariable(...)`:

**Current curve (benchmark / inertia anchor):**
- `%AAB_Form1A` → `current_form1a`
- `%AAB_Form2A` → `current_form2a`
- `%AAB_Form2B` → `current_form2b`
- `%AAB_Form2C` → `current_form2c` (clamped ≥ −50)
- `%AAB_Zone1End` → `current_zone1end`
- `%AAB_Zone2End` → `current_zone2end`
- `%AAB_MaxBright` → `max_bright`

**Training data (the user's recorded overrides):**
- `%AAB_Overrides1`, `%AAB_Overrides2`, … (array, scanned idx 1..999, stops after 3 consecutive nulls). Each is a CSV string `"lux,brightness[,weight[,kind]]"`:
  - `parts[0]` = lux (`pt[0]`)
  - `parts[1]` = brightness (`pt[1]`, clamped to `[0, max_bright]`)
  - `parts[2]` = weight (`pt[2]`, default 1.0)
  - `parts[3]` = kind flag (`pt[3]`): 2-field → kind `1.0`; 3-field → kind `2.0`; 4-field → `parts[3]` (kind `3.0` = ghost/synthetic marker). `isRealDataPoint` = `kind < 2.5`.
- Gate `act0`: `%AAB_Overrides(#) > 8`. Java independently aborts if `dataPoints.size() < 9` (after ghost insertion).

**Tuning knob:**
- `%tau` (default `0.001` from act2; engine default `4.0` if unset). Inertia/regularization strength.

**Ghost points (auto-synthesized inputs):** the engine bins real data into 5 log bins (`<10, <100, <1000, <10000, ≥10000` lux). For each **empty** bin it injects a synthetic point at lux `{3, 31, 316, 3162, 15000}` whose brightness is evaluated from the *current* curve, with weight `ghostWeight = clamp(0.1 + 0.4·realCount/50, …, 0.5)` and kind `3.0`. Ghosts stabilize sparse regions but are down-weighted (×0.05 extra in `getLogWeight`) and excluded from "real" counts.

---

## 3. The fitting / suggestion ALGORITHM (the Java block)

A **heuristic + closed-form weighted-least-squares hybrid** over a fixed 3-zone parametric model. Not a single global least-squares; it searches the two breakpoints and fits each zone analytically.

### Model (the curve)
- **Zone 1** (lux ≤ Zone1End): `y = form1a · √lux`
- **Zone 2** (Zone1End < lux ≤ Zone2End): `y = form2a + form2b · ((lux − form2c)^0.33 − (Zone1End − form2c)^0.33)`
  - `form2d = Zone1End` (the Zone-2 left anchor); `form2a` is pinned for C0 continuity = `form1a·√Zone1End`.
- **Zone 3** (lux > Zone2End): `y = MaxBright − (form3a / lux) · MaxBright`
- Power constant `0.33`, Z2 buffer `0.5`, RMSE cost weight `50.0`, default `tau 4.0` (engine header).

### Weighting
`getLogWeight(pt) = pt[2] / (lux + 2)` (inverse-lux: low-lux points matter more), with ghost points (`kind≈3`) extra-scaled by `0.05`. Weights are globally normalized so mean real weight = 1.

### Per-zone fitting (weighted least squares)
- `fitZone1`: closed-form `form1a = Σ w·√x·y / Σ w·x` ; returns weighted R².
- Zone 2 `b` (`getR2_Z2_only` / inside `calculateFitAndCost`): closed-form `b = Σ w·(y−a)·T / Σ w·T²` where `T = (x−c)^0.33 − (z1end−c)^0.33`, `a` pinned by continuity. Clamped `b ≥ 0.01`.
- Zone 2 offset `c` (`form2c`): **gradient descent**, ≤100 iters, lr `0.2` decaying ×0.95, analytic derivative of the `^0.33` term; clamped `c ≤ form2d−0.5`, `c ≥ −50`, converges when `|Δc| < 0.002`.
- Zone 3 `form3a`: derived for C0 continuity at Zone2End: `form3a = Zone2End·(MaxBright − y_z2end)/MaxBright`, `≥ 0`.

### Metrics & cost (`evaluateMetrics`, `calculateFitAndCost`)
For each zone: weighted **R²**, **nRMSE** (`√(Σw·e² / Σw) / MaxBright`), and **bias** (`Σw·e / Σw`).
`cost = 50·(nRMSE_z1 + nRMSE_z2 + z3_weight·nRMSE_z3) + (|bias1|+|bias2|+|bias3|) + size_penalty + reg_penalty + r2_penalty`.
- `size_penalty = 0.25·(4−count)²` per zone with < 4 points.
- `reg_penalty = 0.001·b² + 0.0005·|c|`.
- `r2_penalty`: extra cost when a zone R² < 0.65 (Z2 weighted ×3, Z3 ×0.5 + down-weight).
- Hard rejects (`cost = 1e6`/`1e12`): `boundary_y2_end > MaxBright`, `z1e ≥ z2e`, < 3 points in Z1 or Z2, etc.

### Search strategy (boundary optimization)
1. **Benchmark** the current curve (`calculateFitAndCost(current_zone1end, current_zone2end)`); if valid it seeds `global_bestCost` and `suggestion_made=true`.
2. **Stage 1 — Top-K Zone1End:** sweep every interior split `i`, score `= R²_z1 + R²_z2 − small-zone penalty`, keep top `TOP_K_Z1 = min(5, N−8)` boundaries (with dedup/bubble-sort).
3. **Stage 2 — global Z2End init:** for each Z1End candidate, probe Z2End at the 75th/90th percentiles and a strided sweep using the cheap `approximateCost`; pick the best initial split.
4. **Stage 3 — coordinate descent ("hops"):** 3 passes; refine Z1End then Z2End using `generateSmartRefinementPoints` (log-spaced, adaptive count) + `approximateCost` proposals, accepting a hop only if the full `calculateFitAndCost` cost strictly drops. Early-stop when `cost < 3.0` (`EARLY_STOP_COST`), max hops `min(8, max(3, N/5))`.
5. Track `global_best*` across all candidates.

### Inertia blending (the "Hybrid" / regularization step)
After the best raw fit is found, it is **not used directly**. Confidence per zone:
`conf = 1 − exp(−weightedCount / tau)`, where `weightedCount = count · (0.25 + 0.75·R²·(1−min(nRMSE,0.5)))`.
Then each parameter is linearly blended toward the current value:
`best = best·conf + current·(1−conf)` for `Zone1End, form1a` (z1 conf) and `Zone2End, form2a, form2b, form2c` (z2 conf). With few/poor points conf→0 ⇒ keep current curve (anti-overfit). High `tau` ⇒ stronger inertia.

### Post-blend finalize
- `form2d = best_z1_end`.
- Recompute Zone-2 end brightness; if it would exceed `MaxBright−0.5`, clamp it and re-solve `form2b`.
- Recompute `form3a` for continuity (`≥ 0.001`).
- **Re-evaluate all R²/nRMSE/bias** on the final blended curve for honest logs.
- Compute a "Fit Stability" rating (max single-point error-impact share) and 4 human-readable quality lines.
- If `suggestion_made` is still false (no valid candidate AND invalid baseline) → set `%suggest = "error"` and abort (no suggestion vars written).

---

## 4. OUTPUTS, and how task655 applies them

### Variables written by task38's Java (`act4`)
On success (`tasker.setVariable`):
- `%suggest` = `"true"`
- `%suggestion_zone1end` = `round(best_z1_end)` (integer string)
- `%suggestion_zone2end` = `round(best_z2_end)` (integer string)
- `%suggestion_form1a` = `%.3f` (3 decimals)
- `%suggestion_form2a` = `%.3f`
- `%suggestion_form2b` = `%.3f`
- `%suggestion_form2c` = `%.3f`
- `%suggestion_form2d` = `round(best_form2d)` (= round(Zone1End))
- `%suggestion_form3a` = `%.3f`
- `%suggest_r2_1` = "🏆 Overall Fit: …" ; `%suggest_r2_2/_3/_4` = Dark/Dim/Bright per-zone quality lines.
- `%AAB_Test` = full diagnostic log (always, in `finally`).

On abort: `%suggest = "error"` (act3 default also leaves it `error`); no `%suggestion_*` written.

> Note: task38 itself does **not** modify the live `%AAB_*` curve settings. It only proposes `%suggestion_*`. Application is a separate user-confirmed step via **task655**.

### task655 `_SetSuggestedVariables` — mapping `%suggestion_*` → live `%AAB_*`
Range L32574–L32829. Execution order (numeric):

1. `act0` (37 If): `%AAB_Package ~ com.tideo.aab` — only apply inside the AAB app context.
2. `act1` (474 Java): **sanitize** decimal commas → periods for `suggestion_form1a, suggestion_zone1end, suggestion_form2b, suggestion_form2c, suggestion_zone2end` (locale fix; see `task655_1_setsuggestedvariables.java`).
3. `act3/act4` (37/547): if `%suggestion_form1a` is set → `%aab_form1a = %suggestion_form1a`.
4. `act6/act7/act8` (37/547/547): if `%suggestion_zone1end` set → `%aab_zone1end = %suggestion_zone1end` **and** `%aab_form2d = %suggestion_zone1end`.
5. `act10/act11` (37/547): if `%suggestion_form2a` set → **`%aab_form2a` is RE-DERIVED, not copied:** `%aab_form2a = %aab_form1a*(sqrt(%aab_zone1end))` (enforces Zone-1↔Zone-2 continuity from the just-written form1a/zone1end).
6. `act13/act14` (37/547): if `%suggestion_form2b` set → `%aab_form2b = %suggestion_form2b`.
7. `act16/act17/act18/act19/act20/act21/act22` (nested If/Else): if `%suggestion_form2c` set:
   - if `%suggestion_form2c < %suggestion_zone1end` (op 6 = "<") → `%aab_form2c = %suggestion_form2c`;
   - **else** → `%aab_form2c = %suggestion_zone1end` (clamp the Zone-2 offset below Zone1End).
8. `act23/act24` (37/547): if `%suggestion_zone2end` set → `%aab_zone2end = %suggestion_zone2end`.
9. `act26/act27` (37/547): if `%suggestion_form3a` set → **`%aab_form3a` is RE-DERIVED, not copied:**
   `%aab_form3a = %aab_zone2end*(%AAB_MaxBright − (%aab_form2a + %aab_form2b*((%aab_zone2end−%aab_form2c)^0.33 − (%aab_zone1end−%aab_form2c)^0.33)))/%AAB_MaxBright`
   (same continuity formula the engine used; recomputed from the freshly-written live vars).
10. `act29` (126 Return) ends the task.

**Net live settings written by task655:** `%aab_form1a, %aab_zone1end, %aab_form2d, %aab_form2a (derived), %aab_form2b, %aab_form2c (clamped), %aab_zone2end, %aab_form3a (derived)`.
Note `%suggestion_form2a` and `%suggestion_form3a` from task38 are **only used as presence flags** — task655 recomputes both from the other live params for continuity. The provenance comment for the rebuild should reflect that `Form2A` and `Form3A` are DERIVED at apply-time (consistent with CLAUDE.md: form2A/3A are derived continuity coefficients, task659).

---

## 5. Variables read / written (consolidated)

**task38 reads:** `%AAB_Overrides(#)`, `%AAB_Overrides1..N`, `%AAB_Form1A`, `%AAB_Form2A`, `%AAB_Form2B`, `%AAB_Form2C`, `%AAB_Zone1End`, `%AAB_Zone2End`, `%AAB_MaxBright`, `%tau`, `%priority`, `%suggest`.

**task38 writes:** `%tau` (default), `%suggest`, `%suggestion_zone1end`, `%suggestion_zone2end`, `%suggestion_form1a`, `%suggestion_form2a`, `%suggestion_form2b`, `%suggestion_form2c`, `%suggestion_form2d`, `%suggestion_form3a`, `%suggest_r2_1..4`, `%AAB_Test`.

**task655 reads:** `%AAB_Package`, `%suggestion_form1a/zone1end/form2a/form2b/form2c/zone2end/form3a`, `%AAB_MaxBright`, and the live `%aab_*` it just wrote (for the derived formulae).

**task655 writes (live curve):** `%aab_form1a`, `%aab_zone1end`, `%aab_form2d`, `%aab_form2a`, `%aab_form2b`, `%aab_form2c`, `%aab_zone2end`, `%aab_form3a` (plus the sanitized in-place rewrite of the `suggestion_*` vars).

### Parity-critical numeric constants
Power `0.33`; ghost luxes `{3, 31, 316, 3162, 15000}`; bin edges `{10,100,1000,10000}`; ghostWeight `0.1 + 0.4·realCount/50` cap `0.5`; ghost weight extra `×0.05`; logWeight denom `lux + 2`; RMSE cost weight `50.0`; size penalty `0.25·(4−n)²`; reg `0.001·b² + 0.0005·|c|`; R²-gate `0.65` (Z2 ×3, Z3 ×0.5); `EARLY_STOP_COST = 3.0`; `TOP_K_Z1 = min(5, N−8)`; `MAX_HOPS = min(8, max(3, N/5))`; gradient-descent lr `0.2`×0.95, 100 iters, conv `0.002`; `form2c` clamp `≥ −50`, `≤ form2d − 0.5`; Z2 buffer `0.5`; confidence `1 − exp(−weightedCount/tau)`, `weightedCount = n·(0.25 + 0.75·R²·(1−min(nRMSE,0.5)))`; `tau` default `4.0` (engine) / `0.001` (act2). Number formatting: zone ends and form2d → `Math.round` (tie toward +∞); form1a/2a/2b/2c/3a → `String.format("%.3f")`.
