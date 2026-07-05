package com.tideo.autobrightness.app.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the clamp-on-commit [AabSettings.validate] (D-085) — the per-field guard the draft Apply,
 * SettingsStore, import/export and legacy paths all run before persisting.
 */
class AabSettingsClampTest {

    @Test
    fun `min brightness 0 is preserved, not clamped to 1`() {
        // G3-F3 (Gate 3): the Misc slider exposes 0..75 (Tasker %AAB_MinBright). The old clamp floored
        // 0→1 on Apply, so committed(1) ≠ draft(0) left the screen perpetually dirty and 0 never stuck.
        val validated = AabSettings(minBrightness = 0).validate()
        assertEquals(0, validated.minBrightness, "min brightness 0 must survive validate() (dimmest, not off)")
    }

    @Test
    fun `negative min brightness still clamps to 0`() {
        assertEquals(0, AabSettings(minBrightness = -5).validate().minBrightness)
    }

    @Test
    fun `out-of-range max brightness is still clamped`() {
        // Sanity: the floor change for min must not loosen the max clamp.
        assertEquals(255, AabSettings(maxBrightness = 999).validate().maxBrightness)
    }

    @Test
    fun `applying validate twice is idempotent for a valid min of 0`() {
        // The perpetual-dirty bug was committed != draft after one validate(); a fixed point clears it.
        val once = AabSettings(minBrightness = 0).validate()
        assertTrue(once == once.validate(), "validate() must be a fixed point for an already-valid value")
    }

    @Test
    fun `NaN numeric fields reset to their defaults instead of poisoning the settings D146`() {
        // D-146: NaN passes straight through coerceIn (every comparison is false → returns NaN), and
        // the legacy import parsers accept it ("NaN".toDoubleOrNull() parses) — so a malformed profile
        // file could persist NaN into the curve math and drive the pipeline output to garbage. validate()
        // is the shared chokepoint for Apply/import/store, so it must sanitize every non-finite float.
        val poisoned = AabSettings(
            scale = Float.NaN,
            form1A = Double.NaN,
            form2B = Float.NaN,
            dimmingExponent = Float.NaN,
            pwmExponent = Float.NaN,
            deltaFactor = Float.NaN,
            thresholdBright = Float.NaN,
            thresholdDark = Float.NaN,
            thresholdDim = Float.NaN,
            thresholdSteepness = Float.NaN,
            thresholdMidpoint = Double.NaN,
            scaleTaperSteepness = Float.NaN,
            scaleTransitionFactor = Float.NaN,
        ).validate()
        assertEquals(AabSettings().validate(), poisoned, "every NaN field must fall back to its default")
    }

    @Test
    fun `infinities clamp to the range edges D146`() {
        // ±Infinity needs no special handling — coerceIn's comparisons work for it. Pin that down so a
        // future refactor of the NaN guard doesn't accidentally regress it.
        val validated = AabSettings(scale = Float.POSITIVE_INFINITY, thresholdMidpoint = Double.NEGATIVE_INFINITY).validate()
        assertEquals(10.0f, validated.scale, "+Inf clamps to the scale max")
        assertEquals(0.0, validated.thresholdMidpoint, "-Inf clamps to the midpoint min")
    }

    // --- D-151 display-toggle profile fields -----------------------------------------------------

    @Test
    fun `night light temperature clamps into the sanity band but null stays null D151`() {
        assertEquals(1_000, AabSettings(nightLightTemperature = 5).validate().nightLightTemperature)
        assertEquals(10_000, AabSettings(nightLightTemperature = 99_999).validate().nightLightTemperature)
        assertEquals(2_700, AabSettings(nightLightTemperature = 2_700).validate().nightLightTemperature)
        // null = "no temperature opinion / device default" — validate() must not invent a value.
        assertEquals(null, AabSettings().validate().nightLightTemperature)
    }

    @Test
    fun `unknown daltonizer mode resets to OFF instead of poisoning the profile D151`() {
        // The D-146 spirit for the string field: a newer-schema / hand-edited import cannot persist
        // a value the coordinator and the mode picker cannot represent.
        assertEquals(DALTONIZER_OFF, AabSettings(daltonizerMode = "sepia").validate().daltonizerMode)
        assertEquals("GRAYSCALE", AabSettings(daltonizerMode = "GRAYSCALE").validate().daltonizerMode)
    }
}
