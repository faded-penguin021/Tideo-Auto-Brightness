package com.tideo.autobrightness.domain.wizard

import com.tideo.autobrightness.domain.brightness.BrightnessCurveConfig
import java.util.Locale
import kotlin.math.*

/**
 * One user-recorded override point stored in %AAB_Overrides<N>.
 *
 * Encoding: `"lux,brightness[,weight[,kind]]"`.
 *  - 2-field: kind=1.0 (real, user-recorded)
 *  - 3-field: kind=2.0 (real, weighted)
 *  - 4-field: kind from parts[3] (3.0 = ghost/synthetic)
 *
 * Tasker: task38 Java Block #1, data loading L484–512.
 */
data class OverridePoint(
    val lux: Double,
    val brightness: Double,
    val weight: Double = 1.0,
    /** 1.0 = 2-field real, 2.0 = 3-field real, 3.0 = ghost */
    val kind: Double = 1.0,
)

/**
 * Inputs for the curve-suggestion wizard.
 *
 * Tasker: task38 Java Block #1 gathers these via `tasker.getVariable(...)`.
 */
data class CurveSuggestionInput(
    /** Training data (the user's manually-recorded override points). Must have ≥ 9. */
    val overrides: List<OverridePoint>,
    /** Current active curve — used as benchmark and inertia anchor. */
    val currentCurve: BrightnessCurveConfig,
    /**
     * Inertia regularization strength. Higher τ = stronger pull toward the current curve; lower τ ⇒
     * confidence `1 − exp(−weightedCount/τ) → 1` ⇒ the fit follows the recorded points.
     *
     * G3-F17 (Gate 3): the faithful default is **0.001**, not 4.0. In Tasker task38 act2 (547) always
     * sets `%tau = 0.001` BEFORE the Java engine runs (task038_curve_wizard.md L27/L69: "the default
     * for the Java engine's tau"), so the engine reads 0.001 every real run. The 4.0 in the Java
     * header is only an unreachable fallback for an unset `%tau`. Defaulting to 4.0 over-damped every
     * suggestion toward the current curve — the owner's "suggestion quality is poor". 0.001 ≈ 0 (the
     * label recommends 0.001–5, never exactly 0, since τ is a divisor). All wizard goldens pass τ
     * explicitly (4.0/1.0/2.0/8.0), so this default change touches no golden vector.
     */
    val tau: Double = 0.001,
)

/**
 * Successful curve suggestion — all outputs of task38 Java Block #1.
 *
 * Integer fields (zone1End, zone2End, form2d) use `Math.round(double)` (ties toward +∞).
 * String fields (form1a … form3a) use `String.format("%.3f")` (Locale.US).
 *
 * Tasker: task38 Java Block #1, output writes L853–861.
 */
data class CurveSuggestionResult(
    val zone1End: Long,
    val zone2End: Long,
    val form1a: String,
    val form2a: String,
    val form2b: String,
    val form2c: String,
    val form2d: Long,
    val form3a: String,
    val diagnosticsLog: String,
    /** 4 human-readable quality lines (%suggest_r2_1..4). */
    val qualityLines: List<String>,
)

/**
 * Curve-suggestion engine — "AAB Curve Fitting Engine V43.8 (Confidence Fix)".
 *
 * Pure-domain, deterministic: no I/O, no randomness. Ported from task38 Java Block #1
 * (XML L9922–L10868) with Java semantics preserved:
 *  - `Math.round(double)` for integer zone ends (ties toward +∞)
 *  - `String.format(Locale.US, "%.3f", …)` for curve parameters
 *  - All numeric constants verbatim from the source
 *
 * Returns null if the override set has fewer than 9 entries after ghost injection,
 * or if no valid curve boundary was found (same as task38's `%suggest = "error"` path).
 *
 * Tasker: task38 "_SuggestCurveParameters V24 (Hybrid)". XML L9779–L10913.
 */
object CurveSuggestionEngine {

    /**
     * Run the fitting engine on [input].
     *
     * @return [CurveSuggestionResult] on success, null on abort ("error" path).
     */
    fun suggest(input: CurveSuggestionInput): CurveSuggestionResult? {
        val log = StringBuilder("--- Engine V43.8 (Confidence Fix) ---\n")
        val cur = input.currentCurve
        val maxBright = cur.maxBrightness.toDouble()
        val currentForm1a = cur.form1A
        val currentForm2a = cur.form2A
        val currentForm2b = cur.form2B
        var currentForm2c = cur.form2C
        if (currentForm2c < -50.0) currentForm2c = -50.0
        val currentZone1end = cur.zone1End
        val currentZone2end = cur.zone2End

        log.append("[Input Parameters]\n")
        log.append(String.format(Locale.US, "  Form1a (current): %.3f\n", currentForm1a))
        log.append(String.format(Locale.US, "  Form2a (current): %.3f\n", currentForm2a))
        log.append(String.format(Locale.US, "  Form2b (current): %.3f\n", currentForm2b))
        log.append(String.format(Locale.US, "  Form2c (current): %.3f\n", currentForm2c))
        log.append(String.format(Locale.US, "  Zone1End (current): %.1f\n", currentZone1end))
        log.append(String.format(Locale.US, "  Zone2End (current): %.1f\n", currentZone2end))
        log.append(String.format(Locale.US, "  MaxBright: %.1f\n\n", maxBright))

        // Build sorted data points from override list
        val dataPoints: MutableList<DoubleArray> = mutableListOf()
        for (pt in input.overrides) {
            val dp = doubleArrayOf(pt.lux, pt.brightness.coerceIn(0.0, maxBright), pt.weight, pt.kind)
            dataPoints.add(dp)
        }
        dataPoints.sortWith { a, b -> a[0].compareTo(b[0]) }

        // Count real data and bins for ghost injection
        var realCount = 0
        val binsFilled = BooleanArray(5)
        for (pt in dataPoints) {
            if (isRealDataPoint(pt)) {
                realCount++
                when {
                    pt[0] < 10.0 -> binsFilled[0] = true
                    pt[0] < 100.0 -> binsFilled[1] = true
                    pt[0] < 1000.0 -> binsFilled[2] = true
                    pt[0] < 10000.0 -> binsFilled[3] = true
                    else -> binsFilled[4] = true
                }
            }
        }

        var ghostWeight = 0.1 + (0.4 * (realCount / 50.0))
        if (ghostWeight > 0.5) ghostWeight = 0.5

        // Current curve's zone-2 end point and form3a for ghost brightness evaluation
        val curTermD = safePowDelta(currentZone1end - currentForm2c, 0.33)
        val curYZ2 = currentForm2a + currentForm2b * (safePowDelta(currentZone2end - currentForm2c, 0.33) - curTermD)
        var curForm3a = if (maxBright > 0.01) currentZone2end * (maxBright - curYZ2) / maxBright else 0.0
        if (curForm3a < 0.0) curForm3a = 0.0

        val ghostLuxes = doubleArrayOf(3.0, 31.0, 316.0, 3162.0, 15000.0)
        var ghostsAdded = false
        for (i in 0..4) {
            if (!binsFilled[i]) {
                val gLux = ghostLuxes[i]
                var gBright = when {
                    gLux <= currentZone1end -> currentForm1a * sqrt(gLux)
                    gLux <= currentZone2end ->
                        currentForm2a + currentForm2b * (safePowDelta(gLux - currentForm2c, 0.33) - curTermD)
                    else -> maxBright - (curForm3a / gLux) * maxBright
                }
                if (gBright < 0.0) gBright = 0.0
                if (gBright > maxBright) gBright = maxBright
                dataPoints.add(doubleArrayOf(gLux, gBright, ghostWeight, 3.0))
                ghostsAdded = true
                log.append("  + Ghost Point: $gLux lux -> ${Math.round(gBright)} br (Bin ${i + 1} empty)\n")
            }
        }
        if (ghostsAdded) dataPoints.sortWith { a, b -> a[0].compareTo(b[0]) }

        // Global weight normalization: mean real weight → 1
        var sumRaw = 0.0; var cntRaw = 0
        for (pt in dataPoints) if (isRealDataPoint(pt)) { sumRaw += pt[2]; cntRaw++ }
        val meanRaw = if (cntRaw > 0) sumRaw / cntRaw else 1.0
        val globalScale = 1.0 / maxOf(meanRaw, 1e-9)
        for (pt in dataPoints) pt[2] = pt[2] * globalScale
        log.append(String.format(Locale.US, "Global Weight Norm: Scale=%.4f (Mean Raw=%.4f)\n\n", globalScale, meanRaw))

        if (dataPoints.size < 9) {
            log.append("Not enough data points (${dataPoints.size}). Aborting.\n")
            return null
        }

        log.append("[Input Data] ${dataPoints.size} points from ${dataPoints[0][0]} to ${dataPoints.last()[0]} lux.\n\n")

        val n = dataPoints.size
        val topKZ1 = maxOf(1, minOf(5, n - 8))
        val earlyStopCost = 3.0
        val maxHops = minOf(8, maxOf(3, n / 5))

        // Benchmark current curve
        var globalBestCost = 1e12
        var suggestionMade = false
        var bestZ1End = currentZone1end; var bestZ2End = currentZone2end
        var bestForm1a = currentForm1a; var bestForm2a = currentForm2a; var bestForm2b = currentForm2b
        var bestForm2c = currentForm2c; var bestForm2d = currentZone1end; var bestForm3a = curForm3a
        var bestR2Z1 = 0.0; var bestR2Z2 = 0.0; var bestR2Z3 = 0.0
        var bestZ1Nrmse = 0.0; var bestZ2Nrmse = 0.0; var bestZ3Nrmse = 0.0
        var bestZ1Bias = 0.0; var bestZ2Bias = 0.0; var bestZ3Bias = 0.0

        val currentFit = calculateFitAndCost(currentZone1end, currentZone2end, dataPoints, n, currentForm2b, currentForm2c, maxBright)
        if (currentFit[0] < 1e11) {
            globalBestCost = currentFit[0]
            bestZ1End = currentFit[1]; bestZ2End = currentFit[2]
            bestForm1a = currentFit[3]; bestForm2a = currentFit[4]; bestForm2b = currentFit[5]
            bestForm2c = currentFit[6]; bestForm2d = currentFit[7]; bestForm3a = currentFit[8]
            bestR2Z1 = currentFit[9]; bestR2Z2 = currentFit[10]; bestR2Z3 = currentFit[11]
            bestZ1Nrmse = currentFit[12]; bestZ2Nrmse = currentFit[13]; bestZ3Nrmse = currentFit[14]
            bestZ1Bias = currentFit[15]; bestZ2Bias = currentFit[16]; bestZ3Bias = currentFit[17]
            suggestionMade = true
            log.append(String.format(Locale.US, "Current Benchmark Valid: Cost=%.5f (Z1e=%.1f, Z2e=%.1f)\n\n", globalBestCost, bestZ1End, bestZ2End))
        } else {
            globalBestCost = 1e12; suggestionMade = false
            log.append("Current Benchmark INVALID. Optimizer forced to find new layout.\n\n")
        }

        // Stage 1: Top-K Zone1End candidates
        log.append("--- Stage 1: Searching for Top $topKZ1 Zone1End Candidates ---\n")
        val z1Scores = DoubleArray(topKZ1) { -9999.0 }
        val z1Values = DoubleArray(topKZ1) { currentZone1end }

        for (i in 2 until n - 6) {
            val z1Pts = dataPoints.subList(0, i + 1)
            val z2Pts = dataPoints.subList(i + 1, n)
            if (z1Pts.size < 3 || z2Pts.size < 3) continue
            val fitZ1 = fitZone1(z1Pts); val r2Z1Temp = fitZ1[1]
            val tempForm2d = z1Pts.last()[0]
            val r2Z2Temp = getR2Z2Only(z2Pts, currentForm2c, tempForm2d, currentForm2a, currentForm2b)
            val penalty = if (z1Pts.size < 4) 0.2 else 0.0
            val combinedScore = r2Z1Temp + r2Z2Temp - penalty

            val dupIdx = (0 until topKZ1).firstOrNull { z1Scores[it] > -9998.0 && abs(z1Values[it] - tempForm2d) < 1e-9 } ?: -1
            if (dupIdx != -1) {
                if (combinedScore > z1Scores[dupIdx]) {
                    z1Scores[dupIdx] = combinedScore
                    var t = dupIdx
                    while (t > 0 && z1Scores[t] > z1Scores[t - 1]) {
                        var tmp = z1Scores[t - 1]; z1Scores[t - 1] = z1Scores[t]; z1Scores[t] = tmp
                        tmp = z1Values[t - 1]; z1Values[t - 1] = z1Values[t]; z1Values[t] = tmp
                    }
                }
            } else if (combinedScore > z1Scores[topKZ1 - 1]) {
                z1Scores[topKZ1 - 1] = combinedScore; z1Values[topKZ1 - 1] = tempForm2d
                var t = topKZ1 - 1
                while (t > 0 && z1Scores[t] > z1Scores[t - 1]) {
                    var tmp = z1Scores[t - 1]; z1Scores[t - 1] = z1Scores[t]; z1Scores[t] = tmp
                    tmp = z1Values[t - 1]; z1Values[t - 1] = z1Values[t]; z1Values[t] = tmp
                }
            }
        }
        for (i in 0 until topKZ1) {
            log.append(String.format(Locale.US, "  Top Cand #%d: Z1End=%.2f (Score: %.3f)\n", i + 1, z1Values[i], z1Scores[i]))
        }

        // Stage 2 & 3: Global search + coordinate descent
        log.append("\n--- Stage 2 & 3: Global Search & Coordinate Descent ---\n")
        for (k in 0 until topKZ1) {
            if (k > 0 && globalBestCost < earlyStopCost) break
            if (z1Scores[k] <= -9998.0) continue

            val candZ1End = z1Values[k]
            val remaining = dataPoints.filter { it[0] > candZ1End }.toMutableList()
            if (remaining.size < 4) continue

            var bestZ2InitCost = 1e12
            var candZ2End = currentZone2end

            if (remaining.size > 4) {
                val idx75 = minOf((remaining.size * 0.75).toInt(), remaining.size - 2)
                val idx90 = minOf((remaining.size * 0.90).toInt(), remaining.size - 2)
                val testZ2e75 = remaining[idx75][0]
                val testZ2e90 = remaining[idx90][0]
                val cost75 = approximateCost(candZ1End, testZ2e75, dataPoints, n, currentForm1a, currentForm2b, currentForm2c, maxBright)
                val cost90 = approximateCost(candZ1End, testZ2e90, dataPoints, n, currentForm1a, currentForm2b, currentForm2c, maxBright)
                if (cost75 < bestZ2InitCost) { bestZ2InitCost = cost75; candZ2End = testZ2e75 }
                if (cost90 < bestZ2InitCost) { bestZ2InitCost = cost90; candZ2End = testZ2e90 }
            }

            val step = maxOf(1, remaining.size / 10)
            var j = 1
            while (j < remaining.size - 2) {
                val testZ2e = remaining[j][0]
                if (testZ2e > candZ1End + 2.0) {
                    val testCost = approximateCost(candZ1End, testZ2e, dataPoints, n, currentForm1a, currentForm2b, currentForm2c, maxBright)
                    if (testCost < bestZ2InitCost) { bestZ2InitCost = testCost; candZ2End = testZ2e }
                }
                j += step
            }

            log.append(String.format(Locale.US, " -> Cand(k=%d): Z1e=%.2f, Initial Z2e Split=%.1f\n", k, candZ1End, candZ2End))

            var kBestResults = calculateFitAndCost(candZ1End, candZ2End, dataPoints, n, currentForm2b, currentForm2c, maxBright)
            if (kBestResults[0] > 1e11) continue
            var kBestCost = kBestResults[0]

            if (kBestCost > 1e5) {
                log.append(String.format(Locale.US, "    ! Skipped: boundary violation (cost=%.0f, likely Z2End > MaxBright)\n", kBestCost))
                continue
            }

            for (iterPass in 0..2) {
                var hopCount = 0; var hopped = true
                while (hopped && hopCount < maxHops && kBestResults[12] > 0.015) {
                    hopped = false
                    var luxBeforeZ1 = dataPoints[0][0]
                    var luxAfterZ1 = dataPoints.last()[0]
                    for (ptIdx in 1 until dataPoints.size) {
                        if (dataPoints[ptIdx][0] >= kBestResults[1]) {
                            luxBeforeZ1 = dataPoints[ptIdx - 1][0]
                            luxAfterZ1 = if (ptIdx < dataPoints.size - 1) dataPoints[ptIdx + 1][0] else dataPoints[ptIdx][0]
                            break
                        }
                    }
                    val z1Candidates = generateSmartRefinementPoints(luxBeforeZ1, luxAfterZ1, kBestResults[1], calculateRefinePoints(luxBeforeZ1, luxAfterZ1, kBestResults[1]))

                    var bestApproxCostZ1 = 1e12; var bestApproxZ1e = kBestResults[1]
                    for (candZ1 in z1Candidates) {
                        if (candZ1 >= kBestResults[2] - 1.0) continue
                        val ac = approximateCost(candZ1, kBestResults[2], dataPoints, n, kBestResults[3], kBestResults[5], kBestResults[6], maxBright)
                        if (ac < bestApproxCostZ1) { bestApproxCostZ1 = ac; bestApproxZ1e = candZ1 }
                    }

                    val hopThreshold = maxOf(0.5, kBestResults[1] * 0.005)
                    if (abs(bestApproxZ1e - kBestResults[1]) > hopThreshold) {
                        val z1RefineResults = calculateFitAndCost(bestApproxZ1e, kBestResults[2], dataPoints, n, kBestResults[5], kBestResults[6], maxBright)
                        if (z1RefineResults[0] < kBestCost) {
                            kBestCost = z1RefineResults[0]; kBestResults = z1RefineResults; hopped = true; hopCount++
                            log.append(String.format(Locale.US, "    [Pass %d] * Z1 Hopped to %.2f (Cost: %.4f)\n", iterPass + 1, kBestResults[1], kBestCost))
                        }
                    }
                }

                if (kBestCost >= earlyStopCost) {
                    var currentZ2Idx = 0
                    for (m in dataPoints.indices) {
                        if (dataPoints[m][0] >= kBestResults[2]) { currentZ2Idx = m; break }
                    }
                    val startIdx = maxOf(0, currentZ2Idx - 3)
                    val endIdx = minOf(dataPoints.size - 1, currentZ2Idx + 5)
                    val z2SearchMin = dataPoints[startIdx][0]
                    val z2SearchMax = dataPoints[endIdx][0]
                    val z2Candidates = generateSmartRefinementPoints(z2SearchMin, z2SearchMax, kBestResults[2], 8)

                    var bestApproxCostZ2 = 1e12; var bestApproxZ2e = kBestResults[2]
                    for (candZ2 in z2Candidates) {
                        if (candZ2 <= kBestResults[1] + 5.0) continue
                        val ac = approximateCost(kBestResults[1], candZ2, dataPoints, n, kBestResults[3], kBestResults[5], kBestResults[6], maxBright)
                        if (ac < bestApproxCostZ2) { bestApproxCostZ2 = ac; bestApproxZ2e = candZ2 }
                    }

                    if (abs(bestApproxZ2e - kBestResults[2]) > 1.0) {
                        val z2RefineResults = calculateFitAndCost(kBestResults[1], bestApproxZ2e, dataPoints, n, kBestResults[5], kBestResults[6], maxBright)
                        if (z2RefineResults[0] < kBestCost) {
                            kBestCost = z2RefineResults[0]; kBestResults = z2RefineResults
                            log.append(String.format(Locale.US, "    * Z2 Hopped to %.2f (Cost: %.4f)\n", kBestResults[2], kBestCost))
                        }
                    }
                }
            }

            if (kBestResults[0] < globalBestCost) {
                globalBestCost = kBestResults[0]
                bestZ1End = kBestResults[1]; bestZ2End = kBestResults[2]
                bestForm1a = kBestResults[3]; bestForm2a = kBestResults[4]; bestForm2b = kBestResults[5]
                bestForm2c = kBestResults[6]; bestForm2d = kBestResults[7]; bestForm3a = kBestResults[8]
                bestR2Z1 = kBestResults[9]; bestR2Z2 = kBestResults[10]; bestR2Z3 = kBestResults[11]
                bestZ1Nrmse = kBestResults[12]; bestZ2Nrmse = kBestResults[13]; bestZ3Nrmse = kBestResults[14]
                bestZ1Bias = kBestResults[15]; bestZ2Bias = kBestResults[16]; bestZ3Bias = kBestResults[17]
                suggestionMade = true
            }
        }

        if (!suggestionMade) {
            log.append("\n--- FATAL: No valid candidates found AND baseline is invalid. ---\n")
            log.append("⚠️ ABORTING. Cannot generate a safe curve.\n")
            log.append("\n--- Analysis Engine Finished ---\n")
            return null
        }

        // Count real points per zone (pre-blend) for confidence calculation
        var nZ1 = 0; var nZ2 = 0
        for (pt in dataPoints) {
            if (!isRealDataPoint(pt)) continue
            when {
                pt[0] <= bestZ1End -> nZ1++
                pt[0] <= bestZ2End -> nZ2++
            }
        }

        val n1W = getWeightedCount(nZ1, bestR2Z1, bestZ1Nrmse)
        val n2W = getWeightedCount(nZ2, bestR2Z2, bestZ2Nrmse)
        val tau = input.tau

        val confZ1 = 1.0 - exp(-n1W / tau)
        val confZ2 = 1.0 - exp(-n2W / tau)
        log.append(String.format(Locale.US, "\n[Inertia Blending] Confidence: Z1=%.2f, Z2=%.2f (Tau=%.1f)\n", confZ1, confZ2, tau))

        // Inertia blending
        bestZ1End = bestZ1End * confZ1 + currentZone1end * (1.0 - confZ1)
        bestForm1a = bestForm1a * confZ1 + currentForm1a * (1.0 - confZ1)
        bestZ2End = bestZ2End * confZ2 + currentZone2end * (1.0 - confZ2)
        bestForm2a = bestForm2a * confZ2 + currentForm2a * (1.0 - confZ2)
        bestForm2b = bestForm2b * confZ2 + currentForm2b * (1.0 - confZ2)
        bestForm2c = bestForm2c * confZ2 + currentForm2c * (1.0 - confZ2)
        bestForm2d = bestZ1End

        // Post-blend clamp: ensure Z2 boundary ≤ maxBright - 0.5
        val termDFinal = safePowDelta(bestZ1End - bestForm2c, 0.33)
        val termXFinal = safePowDelta(bestZ2End - bestForm2c, 0.33)
        var yBoundaryZ2 = bestForm2a + bestForm2b * (termXFinal - termDFinal)
        if (yBoundaryZ2 > maxBright - 0.5) {
            yBoundaryZ2 = maxBright - 0.5
            val den = termXFinal - termDFinal
            if (den > 1e-9) bestForm2b = (yBoundaryZ2 - bestForm2a) / den
            if (bestForm2b < 0.0) bestForm2b = 0.001
        }
        bestForm3a = if (maxBright > 0.01) bestZ2End * (maxBright - yBoundaryZ2) / maxBright else 0.0
        if (bestForm3a < 0.001) bestForm3a = 0.001

        // Recalculate metrics AFTER blending (honest logs)
        val finalZ1: MutableList<DoubleArray> = mutableListOf()
        val finalZ2: MutableList<DoubleArray> = mutableListOf()
        val finalZ3: MutableList<DoubleArray> = mutableListOf()
        for (pt in dataPoints) {
            when {
                pt[0] <= bestZ1End -> finalZ1.add(pt)
                pt[0] <= bestZ2End -> finalZ2.add(pt)
                else -> finalZ3.add(pt)
            }
        }
        val fz1 = evaluateMetrics(1, finalZ1, maxBright, bestForm1a, 0.0, 0.0, 0.0)
        val fz2 = evaluateMetrics(2, finalZ2, maxBright, bestForm2a, bestForm2b, bestForm2c, bestZ1End)
        val fz3 = evaluateMetrics(3, finalZ3, maxBright, bestForm3a, 0.0, 0.0, 0.0)
        bestR2Z1 = fz1[0]; bestZ1Nrmse = fz1[1]; bestZ1Bias = fz1[2]
        bestR2Z2 = fz2[0]; bestZ2Nrmse = fz2[1]; bestZ2Bias = fz2[2]
        bestR2Z3 = fz3[0]; bestZ3Nrmse = fz3[1]; bestZ3Bias = fz3[2]

        // Fit stability
        var maxImpact = 0.0
        if (dataPoints.size > 3) {
            val termDZ2 = safePowDelta(bestZ1End - bestForm2c, 0.33)
            var baseSqErr = 0.0
            for (pt in dataPoints) {
                val w = getLogWeight(pt)
                val yCurve = evalCurve(pt[0], bestForm1a, bestForm2a, bestForm2b, bestForm2c, bestZ1End, bestZ2End, bestForm3a, maxBright, termDZ2)
                baseSqErr += w * (yCurve - pt[1]).pow(2.0)
            }
            for (pt in dataPoints) {
                val w = getLogWeight(pt)
                val yCurve = evalCurve(pt[0], bestForm1a, bestForm2a, bestForm2b, bestForm2c, bestZ1End, bestZ2End, bestForm3a, maxBright, termDZ2)
                val errContrib = w * (yCurve - pt[1]).pow(2.0)
                val impact = errContrib / (baseSqErr + 1e-9)
                if (impact > maxImpact) maxImpact = impact
            }
        }
        val stabilityRating = if (maxImpact < 0.3) "High" else if (maxImpact < 0.6) "Moderate" else "Low (Outlier Driven)"

        log.append("\n--- Final Curve Diagnostics (Post-Blend) ---\n")
        log.append(String.format(Locale.US, "Fit Stability: %s (Max Impact: %.1f%%)\n\n", stabilityRating, maxImpact * 100.0))
        log.append("[Zone Boundaries]\n")
        log.append(String.format(Locale.US, "  Zone1End: %.2f (lux)\n", bestZ1End))
        log.append(String.format(Locale.US, "  Zone2End: %.2f (lux)\n\n", bestZ2End))
        log.append("[Curve Parameters]\n")
        log.append(String.format(Locale.US, "  Form1a (sqrt scale): %.4f\n", bestForm1a))
        log.append(String.format(Locale.US, "  Form2a (align): %.4f\n", bestForm2a))
        log.append(String.format(Locale.US, "  Form2b (scale): %.4f\n", bestForm2b))
        log.append(String.format(Locale.US, "  Form2c (offset): %.4f\n", bestForm2c))
        log.append(String.format(Locale.US, "  Form2d (Z1 align): %.4f\n", bestForm2d))
        log.append(String.format(Locale.US, "  Form3a (tail align): %.4f\n\n", bestForm3a))
        log.append("[Goodness of Fit (R²)]\n")
        log.append(String.format(Locale.US, "  R² Zone 1: %.3f\n", bestR2Z1))
        log.append(String.format(Locale.US, "  R² Zone 2: %.3f\n", bestR2Z2))
        log.append(String.format(Locale.US, "  R² Zone 3: %.3f\n\n", bestR2Z3))
        log.append("[Error Metrics]\n")
        log.append(String.format(Locale.US, "  nRMSE Z1: %.2f%%\n", bestZ1Nrmse * 100.0))
        log.append(String.format(Locale.US, "  nRMSE Z2: %.2f%%\n", bestZ2Nrmse * 100.0))
        log.append(String.format(Locale.US, "  nRMSE Z3: %.2f%%\n", bestZ3Nrmse * 100.0))
        log.append(String.format(Locale.US, "  Bias Z1:  %.2f\n", bestZ1Bias))
        log.append(String.format(Locale.US, "  Bias Z2:  %.2f\n", bestZ2Bias))
        log.append(String.format(Locale.US, "  Bias Z3:  %.2f\n", bestZ3Bias))
        log.append("--------------------------\n\n")

        // Quality lines
        val z1R2Str = if (bestR2Z1 > -2.0) String.format(Locale.US, "%.2f", bestR2Z1) else "N/A"
        val z2R2Str = if (bestR2Z2 > -2.0) String.format(Locale.US, "%.2f", bestR2Z2) else "N/A"
        val z3R2Str = if (bestR2Z3 > -2.0) String.format(Locale.US, "%.2f", bestR2Z3) else "N/A"

        val z1ErrPct = bestZ1Nrmse * 100.0; val z2ErrPct = bestZ2Nrmse * 100.0; val z3ErrPct = bestZ3Nrmse * 100.0
        val z1ErrQual = errQuality(z1ErrPct); val z2ErrQual = errQuality(z2ErrPct); val z3ErrQual = errQuality(z3ErrPct)
        val z1BiasDir = biasDir(bestZ1Bias); val z2BiasDir = biasDir(bestZ2Bias); val z3BiasDir = biasDir(bestZ3Bias)
        val z1BiasQual = biasQuality(abs(bestZ1Bias)); val z2BiasQual = biasQuality(abs(bestZ2Bias)); val z3BiasQual = biasQuality(abs(bestZ3Bias))
        val z1ShapeQual = shapeQuality(bestR2Z1); val z2ShapeQual = shapeQuality(bestR2Z2); val z3ShapeQual = shapeQuality(bestR2Z3)

        val avgErr = (z1ErrPct + z2ErrPct + z3ErrPct) / 3.0
        val overallQual = errQuality(avgErr).let {
            if (avgErr < 2) "Excellent" else if (avgErr < 4) "Very Good" else if (avgErr < 8) "Good" else if (avgErr < 15) "Fair" else "Poor"
        }

        val overallLine = "🏆 Overall Fit: $overallQual"
        val z1Line = String.format(Locale.US, "⚫ Dark: %s (%.1f%%) | Shape: %s (R²: %s) | Bias: %s %s (%.1f)", z1ErrQual, z1ErrPct, z1ShapeQual, z1R2Str, z1BiasQual, z1BiasDir, bestZ1Bias)
        val z2Line = String.format(Locale.US, "⚪ Dim: %s (%.1f%%) | Shape: %s (R²: %s) | Bias: %s %s (%.1f)", z2ErrQual, z2ErrPct, z2ShapeQual, z2R2Str, z2BiasQual, z2BiasDir, bestZ2Bias)
        val z3Line = String.format(Locale.US, "☀️ Bright: %s (%.1f%%) | Shape: %s (R²: %s) | Bias: %s %s (%.1f)", z3ErrQual, z3ErrPct, z3ShapeQual, z3R2Str, z3BiasQual, z3BiasDir, bestZ3Bias)

        log.append("[Human Summary]\n$overallLine\n$z1Line\n$z2Line\n$z3Line\n")
        log.append("\n--- Analysis Engine Finished ---\n")

        return CurveSuggestionResult(
            zone1End = Math.round(bestZ1End),
            zone2End = Math.round(bestZ2End),
            form1a = String.format(Locale.US, "%.3f", bestForm1a),
            form2a = String.format(Locale.US, "%.3f", bestForm2a),
            form2b = String.format(Locale.US, "%.3f", bestForm2b),
            form2c = String.format(Locale.US, "%.3f", bestForm2c),
            form2d = Math.round(bestForm2d),
            form3a = String.format(Locale.US, "%.3f", bestForm3a),
            diagnosticsLog = log.toString(),
            qualityLines = listOf(overallLine, z1Line, z2Line, z3Line),
        )
    }

    /**
     * Apply a suggestion to the current curve — task655 "_SetSuggestedVariables" logic.
     *
     * Re-derives form2a and form3a for C0 continuity instead of copying from the suggestion
     * (task655 act10/27 re-compute them from the freshly-written live params).
     *
     * Tasker: task655 XML L32574–L32829.
     */
    fun applyToLiveCurve(suggestion: CurveSuggestionResult, current: BrightnessCurveConfig): BrightnessCurveConfig {
        val form1a = suggestion.form1a.replace(',', '.').toDouble()
        val zone1End = suggestion.zone1End.toDouble()
        val form2b = suggestion.form2b.replace(',', '.').toDouble()
        var form2c = suggestion.form2c.replace(',', '.').toDouble()
        val zone2End = suggestion.zone2End.toDouble()
        val maxBright = current.maxBrightness.toDouble()

        // task655 act10: form2a re-derived — form1a * sqrt(zone1end)
        val form2a = form1a * sqrt(zone1End)

        // task655 act16-22: clamp form2c below zone1End
        if (form2c >= zone1End) form2c = zone1End

        // task655 act26-27: form3a re-derived for C0 continuity at zone2End
        val termD = safePowDelta(zone1End - form2c, 0.33)
        val termX = safePowDelta(zone2End - form2c, 0.33)
        val yZ2End = form2a + form2b * (termX - termD)
        val form3a = maxOf(0.0, zone2End * (maxBright - yZ2End) / maxBright)

        return current.copy(
            form1A = form1a,
            zone1End = zone1End,
            form2A = form2a,
            form2B = form2b,
            form2C = form2c,
            zone2End = zone2End,
            form3A = form3a,
        )
    }

    // ---- Private helpers (faithful ports of Java inner methods) ----------------------------

    private fun safePowDelta(v: Double, p: Double): Double = maxOf(v, 1e-9).pow(p)

    private fun getLogWeight(pt: DoubleArray): Double {
        if (pt.size < 3) return 0.1
        return if (pt.size > 3 && abs(pt[3] - 3.0) < 0.1) (pt[2] * 0.05) / (pt[0] + 2.0)
        else pt[2] / (pt[0] + 2.0)
    }

    private fun isRealDataPoint(pt: DoubleArray) = pt.size <= 3 || pt[3] < 2.5

    private fun calculateRefinePoints(gapStart: Double, gapEnd: Double, currentPos: Double): Int {
        if (gapStart <= 1e-9) return 5
        val gapRatio = gapEnd / gapStart
        val gapSize = gapEnd - gapStart
        val isDarkZone = currentPos < 100.0
        if (gapRatio < 1.5 || gapSize < 10.0) return 0
        if (gapRatio < 3.0) return if (isDarkZone) 3 else 5
        return if (isDarkZone) 5 else 8
    }

    private fun generateSmartRefinementPoints(start: Double, end: Double, current: Double, count: Int): List<Double> {
        val points = mutableListOf<Double>()
        if (count <= 0) { points.add(current); return points }
        if (start <= 1e-9 || end <= 1e-9 || start >= end || current < start || current > end) {
            points.add(current); return points
        }
        val logStart = ln(start); val logEnd = ln(end); val logCurrent = ln(current)
        val totalSpan = logEnd - logStart
        if (totalSpan < 1e-9) { points.add(current); return points }
        val leftSpan = logCurrent - logStart
        val leftPoints = Math.round(count * (leftSpan / totalSpan)).toInt()
        val rightPoints = count - leftPoints
        if (leftPoints > 0 && leftSpan > 1e-9) {
            val leftStep = leftSpan / (leftPoints + 1)
            for (i in 1..leftPoints) points.add(exp(logStart + i * leftStep))
        }
        points.add(current)
        if (rightPoints > 0 && (logEnd - logCurrent) > 1e-9) {
            val rightStep = (logEnd - logCurrent) / (rightPoints + 1)
            for (i in 1..rightPoints) points.add(exp(logCurrent + i * rightStep))
        }
        return points
    }

    private fun evaluateMetrics(zone: Int, points: List<DoubleArray>, maxBright: Double, fA: Double, fB: Double, fC: Double, z1e: Double): DoubleArray {
        val res = doubleArrayOf(-2.0, 0.0, 0.0)
        if (points.isEmpty()) return res
        var sumW = 0.0; var sumWy = 0.0
        for (pt in points) { val w = getLogWeight(pt); sumW += w; sumWy += w * pt[1] }
        if (sumW < 1e-9) return res
        val meanY = sumWy / sumW
        val termD2 = if (zone == 2) safePowDelta(z1e - fC, 0.33) else 0.0
        var ssTot = 0.0; var ssRes = 0.0; var sumSq = 0.0; var sumErr = 0.0; var countValid = 0
        for (pt in points) {
            val w = getLogWeight(pt); val y = pt[1]
            val yPred = when (zone) {
                1 -> fA * sqrt(pt[0])
                2 -> { if (pt[0] <= fC) continue; fA + fB * (safePowDelta(pt[0] - fC, 0.33) - termD2) }
                else -> maxBright - (fA / pt[0]) * maxBright
            }
            val e = yPred - y
            sumErr += w * e; sumSq += w * e * e
            ssTot += w * (y - meanY).pow(2.0); ssRes += w * e * e
            countValid++
        }
        if (countValid == 0) return res
        res[0] = if (ssTot < 1e-9) 0.0 else 1.0 - ssRes / ssTot
        res[1] = if (maxBright > 1e-9) sqrt(sumSq / sumW) / maxBright else 0.0
        res[2] = sumErr / sumW
        return res
    }

    private fun fitZone1(points: List<DoubleArray>): DoubleArray {
        val result = doubleArrayOf(0.0, -2.0)
        if (points.size < 3) return result
        var sumWy = 0.0; var sumW = 0.0
        for (pt in points) { val w = getLogWeight(pt); sumWy += w * pt[1]; sumW += w }
        val meanYW = if (sumW > 1e-9) sumWy / sumW else 0.0
        var sumWxy = 0.0; var sumWxx = 0.0
        for (pt in points) { val w = getLogWeight(pt); val xp = sqrt(pt[0]); sumWxy += w * xp * pt[1]; sumWxx += w * xp * xp }
        val paramA = if (sumWxx > 1e-9) sumWxy / sumWxx else 0.0
        result[0] = paramA
        var ssTotW = 0.0; var ssResW = 0.0
        for (pt in points) { val w = getLogWeight(pt); val yPred = paramA * sqrt(pt[0]); ssTotW += w * (pt[1] - meanYW).pow(2.0); ssResW += w * (pt[1] - yPred).pow(2.0) }
        result[1] = if (ssTotW < 1e-9) 0.0 else 1.0 - ssResW / ssTotW
        return result
    }

    private fun getR2Z2Only(points: List<DoubleArray>, form2c: Double, form2d: Double, startA: Double, startB: Double): Double {
        if (points.size < 3) return -2.0
        var c = form2c; if (c < -50.0) c = -50.0
        val termD = safePowDelta(form2d - c, 0.33)
        val validPts = points.filter { it[0] > c && form2d > c }
        if (validPts.size < 3) return -2.0
        var sumWyV = 0.0; var sumWV = 0.0
        for (pt in validPts) { val w = getLogWeight(pt); sumWyV += w * pt[1]; sumWV += w }
        val meanYV = if (sumWV > 1e-9) sumWyV / sumWV else 0.0
        val a = startA; var sumNum = 0.0; var sumDen = 0.0
        for (pt in validPts) { val w = getLogWeight(pt); val term = safePowDelta(pt[0] - c, 0.33) - termD; sumNum += w * (pt[1] - a) * term; sumDen += w * term * term }
        var b = if (sumDen > 1e-9) sumNum / sumDen else startB
        if (b < 0.0) b = 0.01
        var ssTotW = 0.0; var ssResW = 0.0
        for (pt in validPts) { val w = getLogWeight(pt); val term = safePowDelta(pt[0] - c, 0.33) - termD; val yPred = a + b * term; ssTotW += w * (pt[1] - meanYV).pow(2.0); ssResW += w * (pt[1] - yPred).pow(2.0) }
        return if (ssTotW < 1e-9) 0.0 else 1.0 - ssResW / ssTotW
    }

    private fun calculateSizePenalty(count: Int): Double {
        if (count >= 4) return 0.0
        val diff = (4 - count).toDouble(); return 0.25 * diff * diff
    }

    private fun getWeightedCount(count: Int, r2: Double, nrmse: Double): Double {
        if (count <= 0) return 0.0
        val r2Safe = r2.coerceIn(0.0, 1.0)
        val errPen = minOf(nrmse, 0.5)
        return count * (0.25 + 0.75 * r2Safe * (1.0 - errPen))
    }

    private fun approximateCost(z1e: Double, z2e: Double, dataPoints: List<DoubleArray>, n: Int, form1a: Double, oldForm2b: Double, form2c: Double, maxBright: Double): Double {
        if (z1e >= z2e) return 1e12
        val z1Pts = dataPoints.filter { it[0] <= z1e }
        val z2Pts = dataPoints.filter { it[0] > z1e && it[0] <= z2e }
        val z3Pts = dataPoints.filter { it[0] > z2e }
        if (z1Pts.size < 3 || z2Pts.size < 3) return 1e12
        if (z3Pts.isEmpty() && n < 12) return 1e12
        val approxForm2a = form1a * sqrt(z1e)
        val termD = safePowDelta(z1e - form2c, 0.33)
        var sumNum = 0.0; var sumDen = 0.0
        for (pt in z2Pts) {
            if (pt[0] <= form2c) continue
            val w = getLogWeight(pt); val termX = safePowDelta(pt[0] - form2c, 0.33) - termD
            sumNum += w * (pt[1] - approxForm2a) * termX; sumDen += w * termX * termX
        }
        var bestB = if (sumDen > 1e-9) sumNum / sumDen else oldForm2b
        if (bestB < 0.0) bestB = 0.01
        val boundaryY2End = approxForm2a + bestB * (safePowDelta(z2e - form2c, 0.33) - termD)
        if (boundaryY2End > maxBright) return 1e12
        val approxForm3a = if (maxBright > 0.01) maxOf(0.0, z2e * (maxBright - boundaryY2End) / maxBright) else 0.0
        val ev1 = evaluateMetrics(1, z1Pts, maxBright, form1a, 0.0, 0.0, 0.0)
        val ev2 = evaluateMetrics(2, z2Pts, maxBright, approxForm2a, bestB, form2c, z1e)
        val z1Nrmse = ev1[1]; val z1Bias = ev1[2]; val z2Nrmse = ev2[1]; val z2Bias = ev2[2]
        val z3Nrmse: Double; val z3Bias: Double
        if (z3Pts.size >= 2) { val ev3 = evaluateMetrics(3, z3Pts, maxBright, approxForm3a, 0.0, 0.0, 0.0); z3Nrmse = ev3[1]; z3Bias = ev3[2] } else { z3Nrmse = 0.0; z3Bias = 0.0 }
        val sizeP = calculateSizePenalty(z1Pts.size) + calculateSizePenalty(z2Pts.size) + calculateSizePenalty(z3Pts.size)
        val regP = 0.001 * bestB * bestB + 0.0005 * abs(form2c)
        return 50.0 * (z1Nrmse + z2Nrmse + z3Nrmse) + (abs(z1Bias) + abs(z2Bias) + abs(z3Bias)) + sizeP + regP
    }

    private fun calculateFitAndCost(z1e: Double, z2e: Double, dataPoints: List<DoubleArray>, n: Int, currentForm2b: Double, currentForm2c: Double, maxBright: Double): DoubleArray {
        val results = DoubleArray(18) { 0.0 }; results[0] = 1e12
        if (z1e >= z2e || z1e < 0) return results
        val z1Pts: MutableList<DoubleArray> = mutableListOf()
        val z2Pts: MutableList<DoubleArray> = mutableListOf()
        val z3Pts: MutableList<DoubleArray> = mutableListOf()
        for (pt in dataPoints) when { pt[0] <= z1e -> z1Pts.add(pt); pt[0] <= z2e -> z2Pts.add(pt); else -> z3Pts.add(pt) }
        if (z1Pts.size < 3 || z2Pts.size < 3) return results

        val fitZ1Final = fitZone1(z1Pts); val candForm1a = fitZ1Final[0]; val candR2Z1 = fitZ1Final[1]
        var b = currentForm2b; var c = currentForm2c; if (c < -50.0) c = -50.0
        val candForm2a = candForm1a * sqrt(z1e); val form2d = z1e
        var lrC = 0.2; var prevC = c

        for (iter in 0..99) {
            var sumNum = 0.0; var sumDen = 0.0; var validZ2Pts = 0; var sumW2 = 0.0
            val baseD = maxOf(form2d - c, 0.5); val termD = safePowDelta(baseD, 0.33)
            for (pt in z2Pts) {
                if (pt[0] <= c || form2d <= c) continue
                val w = getLogWeight(pt); val termX = safePowDelta(pt[0] - c, 0.33) - termD
                sumNum += w * (pt[1] - candForm2a) * termX; sumDen += w * termX * termX
                validZ2Pts++; sumW2 += w
            }
            if (validZ2Pts < 3) break
            if (sumDen > 1e-9) b = sumNum / sumDen
            if (b < 0.0) b = 0.01

            var errC = 0.0
            for (pt in z2Pts) {
                val x = pt[0]; val yActual = pt[1]; val w = getLogWeight(pt)
                if (x <= c || form2d <= c) continue
                val termX = safePowDelta(x - c, 0.33)
                val yPredicted = candForm2a + b * (termX - termD)
                val error = yPredicted - yActual
                val base = maxOf(x - c, 0.5)
                val dTermC = (0.33 * b) * (baseD.pow(-0.67) - base.pow(-0.67))
                errC += w * error * dTermC
            }
            if (sumW2 > 1e-9) c -= (lrC * errC) / sumW2
            c = minOf(c, form2d - 0.5); if (c < -50.0) c = -50.0
            if (abs(c - prevC) < 0.002) break
            prevC = c; lrC *= 0.95
        }

        val candForm2b = b; val candForm2c = c; val candForm2d = form2d
        val candR2Z2 = getR2Z2Only(z2Pts, candForm2c, candForm2d, candForm2a, candForm2b)
        val boundaryY2End = candForm2a + candForm2b * (safePowDelta(z2e - candForm2c, 0.33) - safePowDelta(z1e - candForm2c, 0.33))
        var candForm3a = 0.0
        if (maxBright > 0.01) candForm3a = z2e * (maxBright - boundaryY2End) / maxBright
        if (candForm3a < 0.0) candForm3a = 0.0

        var z1Nrmse = 0.0; var z1Bias = 0.0; var z2Nrmse = 0.0; var z2Bias = 0.0; var z3Nrmse = 0.0; var z3Bias = 0.0; var candR2Z3 = -2.0
        if (z1Pts.isNotEmpty()) { val ev1 = evaluateMetrics(1, z1Pts, maxBright, candForm1a, 0.0, 0.0, 0.0); z1Nrmse = ev1[1]; z1Bias = ev1[2] }
        if (z2Pts.isNotEmpty()) { val ev2 = evaluateMetrics(2, z2Pts, maxBright, candForm2a, candForm2b, candForm2c, z1e); z2Nrmse = ev2[1]; z2Bias = ev2[2] }
        if (z3Pts.isNotEmpty()) { val ev3 = evaluateMetrics(3, z3Pts, maxBright, candForm3a, 0.0, 0.0, 0.0); candR2Z3 = ev3[0]; z3Nrmse = ev3[1]; z3Bias = ev3[2] }

        val sizeP = calculateSizePenalty(z1Pts.size) + calculateSizePenalty(z2Pts.size) + calculateSizePenalty(z3Pts.size)
        val regP = 0.001 * candForm2b * candForm2b + 0.0005 * abs(candForm2c)
        var z3Weight = 1.0; var r2P = 0.0
        if (candR2Z3 > -2.0) { if (candR2Z3 < 0.65) { z3Weight = 0.5; r2P += 0.5 * (0.65 - candR2Z3) } } else { if (n > 12) r2P += 0.5 }
        if (candR2Z2 > -2.0 && candR2Z2 < 0.65) r2P += 3.0 * (0.65 - candR2Z2)

        val cost = if (boundaryY2End > maxBright) 1e6 else {
            50.0 * (z1Nrmse + z2Nrmse + z3Weight * z3Nrmse) + (abs(z1Bias) + abs(z2Bias) + abs(z3Bias)) + sizeP + regP + r2P
        }

        results[0] = cost; results[1] = z1e; results[2] = z2e; results[3] = candForm1a
        results[4] = candForm2a; results[5] = candForm2b; results[6] = candForm2c
        results[7] = candForm2d; results[8] = candForm3a; results[9] = candR2Z1
        results[10] = candR2Z2; results[11] = candR2Z3; results[12] = z1Nrmse
        results[13] = z2Nrmse; results[14] = z3Nrmse; results[15] = z1Bias
        results[16] = z2Bias; results[17] = z3Bias
        return results
    }

    private fun evalCurve(lux: Double, f1a: Double, f2a: Double, f2b: Double, f2c: Double, z1e: Double, z2e: Double, f3a: Double, maxBright: Double, termDZ2: Double): Double = when {
        lux <= z1e -> f1a * sqrt(lux)
        lux <= z2e -> f2a + f2b * (safePowDelta(lux - f2c, 0.33) - termDZ2)
        else -> maxBright - (f3a / lux) * maxBright
    }

    private fun errQuality(pct: Double) = when {
        pct < 1 -> "Excellent"; pct < 3 -> "Very Good"; pct < 7 -> "Good"; pct < 15 -> "Fair"; else -> "Poor"
    }
    private fun biasDir(bias: Double) = when { bias > 0.2 -> "brighter"; bias < -0.2 -> "dimmer"; else -> "neutral" }
    private fun biasQuality(absBias: Double) = when { absBias < 0.5 -> "Minimal"; absBias < 2 -> "Slight"; absBias < 5 -> "Moderate"; else -> "Strong" }
    private fun shapeQuality(r2: Double) = when { r2 >= 0.9 -> "Excellent"; r2 >= 0.75 -> "Good"; r2 >= 0.4 -> "Moderate"; else -> "Poor" }
}
