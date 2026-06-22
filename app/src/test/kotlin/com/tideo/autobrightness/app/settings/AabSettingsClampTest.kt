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
}
