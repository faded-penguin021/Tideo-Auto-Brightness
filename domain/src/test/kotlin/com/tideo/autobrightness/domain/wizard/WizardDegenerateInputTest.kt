package com.tideo.autobrightness.domain.wizard

import com.tideo.autobrightness.domain.brightness.BrightnessCurveConfig
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Degenerate-input invariant for the wizard (2026-07-12 final-audit pass): the "Apply suggestion"
 * write path is deliberately validate()-free (`SettingsViewModel.update` keeps raw advisory values,
 * Tasker parity), so it relies on [CurveSuggestionEngine] NEVER emitting a non-finite value — a
 * persisted NaN would bypass the D-146 chokepoint guard (`"NaN".toDouble()` in [applyToLiveCurve]
 * happily deserializes it). Empirically the engine holds the invariant today: zero-lux-variance
 * inputs ABORT to null (the golden-modeled error path), and the fittable degenerate shapes below
 * emit only finite numbers. This test is the tripwire if a future engine change breaks that.
 */
class WizardDegenerateInputTest {

    private fun suggest(points: List<OverridePoint>) =
        CurveSuggestionEngine.suggest(CurveSuggestionInput(overrides = points, currentCurve = BrightnessCurveConfig()))

    private fun assertAllFinite(points: List<OverridePoint>, case: String) {
        val result = suggest(points) ?: return // abort-to-null is always an acceptable outcome
        val cfg = CurveSuggestionEngine.applyToLiveCurve(result, BrightnessCurveConfig())
        val fields = mapOf(
            "form1A" to cfg.form1A, "zone1End" to cfg.zone1End, "form2A" to cfg.form2A,
            "form2B" to cfg.form2B, "form2C" to cfg.form2C, "zone2End" to cfg.zone2End,
            "form3A" to cfg.form3A,
        )
        for ((name, value) in fields) {
            assertTrue(value.isFinite(), "$case: $name must be finite, was $value (raw write path persists it unclamped)")
        }
    }

    @Test
    fun zeroLuxVariance_abortsToNull() {
        // All overrides at one lux (nightstand user): the fit is undefined; the engine must take its
        // existing error path, not emit garbage. Pins the abort observed at the audit.
        assertNull(suggest((0 until 9).map { OverridePoint(50.0, 40.0 + it * 10.0) }), "9 identical-lux points")
        assertNull(suggest((0 until 12).map { OverridePoint(50.0, 40.0 + it * 5.0) }), "12 identical-lux points")
        assertNull(suggest((0 until 9).map { OverridePoint(50.0, 80.0) }), "identical points")
    }

    @Test
    fun degenerateButFittableInputs_emitOnlyFiniteValues() {
        // These DID produce results at the audit (zone1End=0 for the all-zero-lux fit — a poor curve
        // from pathological data, but finite and non-crashing). The invariant is finiteness, not
        // fit quality; abort-to-null also passes should the engine tighten later.
        assertAllFinite((0 until 9).map { OverridePoint(0.0, 40.0 + it * 10.0) }, "all-zero-lux")
        assertAllFinite((0 until 9).map { OverridePoint(if (it < 5) 10.0 else 11.0, 40.0 + it * 10.0) }, "two near-identical lux clusters")
    }
}
