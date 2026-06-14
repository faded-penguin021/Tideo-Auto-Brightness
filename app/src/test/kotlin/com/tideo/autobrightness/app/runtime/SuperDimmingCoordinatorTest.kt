package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.platform.brightness.SecureDimmingController
import com.tideo.autobrightness.platform.privilege.Tier
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class SuperDimmingCoordinatorTest {

    private class FakeSecureDimming : SecureDimmingController {
        var activated: Boolean? = null
        val levels = mutableListOf<Int>()
        override fun setLevel(level: Int): Result<Unit> {
            levels += level
            return Result.success(Unit)
        }
        override fun setActivated(on: Boolean): Result<Unit> {
            activated = on
            return Result.success(Unit)
        }
    }

    /** Records flashes that the live debugLevel actually selects (mirrors the real sink gate). */
    private class RecordingDebugSink : DebugSink {
        val emitted = mutableListOf<Pair<DebugCategory, String>>()
        override fun emit(category: DebugCategory, activeLevel: Int, message: () -> String) {
            if (category.level == activeLevel) emitted += category to message()
        }
    }

    private val dimmingOn = AabSettings(
        dimmingEnabled = true,
        dimmingThreshold = 15,
        minBrightness = 0,
    )

    @Test
    fun elevated_belowThreshold_engagesAndWritesLevel() {
        val secure = FakeSecureDimming()
        val coordinator = SuperDimmingCoordinator(secure) { Tier.ELEVATED }

        coordinator.apply(targetBrightness = 5, settings = dimmingOn)

        assertEquals(true, secure.activated, "reduce_bright_colors_activated should be set on")
        assertTrue(secure.levels.isNotEmpty(), "a dim level should be written")
        assertTrue(secure.levels.last() > 0, "below-threshold dimming should be a positive level")
    }

    @Test
    fun basicTier_doesNotEngage() {
        val secure = FakeSecureDimming()
        val coordinator = SuperDimmingCoordinator(secure) { Tier.BASIC }

        coordinator.apply(targetBrightness = 5, settings = dimmingOn)

        assertNull(secure.activated, "BASIC tier must not engage secure dimming")
        assertTrue(secure.levels.isEmpty())
    }

    @Test
    fun dimmingDisabled_doesNotEngage() {
        val secure = FakeSecureDimming()
        val coordinator = SuperDimmingCoordinator(secure) { Tier.ELEVATED }

        coordinator.apply(targetBrightness = 5, settings = dimmingOn.copy(dimmingEnabled = false))

        assertNull(secure.activated)
        assertTrue(secure.levels.isEmpty())
    }

    @Test
    fun aboveThreshold_afterEngaged_disengages() {
        val secure = FakeSecureDimming()
        val coordinator = SuperDimmingCoordinator(secure) { Tier.ELEVATED }

        coordinator.apply(targetBrightness = 5, settings = dimmingOn) // engage
        assertEquals(true, secure.activated)

        coordinator.apply(targetBrightness = 100, settings = dimmingOn) // above threshold

        assertEquals(false, secure.activated, "above-threshold should clear activation")
        assertEquals(0, secure.levels.last(), "disengage writes level 0")
    }

    @Test
    fun disengage_whenNeverEngaged_isNoOp() {
        val secure = FakeSecureDimming()
        val coordinator = SuperDimmingCoordinator(secure) { Tier.ELEVATED }

        coordinator.disengage()

        assertNull(secure.activated)
        assertTrue(secure.levels.isEmpty())
    }

    @Test
    fun unprivilegedFallback_belowThreshold_flashesOverlayColour() {
        // G2R-F49: with dimming on + target below threshold but the tier unable to write secure
        // settings, the OVERLAY_PREVIEW category (level 6) flashes the computed overlay colour.
        val secure = FakeSecureDimming()
        val sink = RecordingDebugSink()
        val coordinator = SuperDimmingCoordinator(secure, debugSink = sink) { Tier.BASIC }

        coordinator.apply(targetBrightness = 5, settings = dimmingOn.copy(debugLevel = 6))

        // No secure write (unprivileged), but the overlay colour is surfaced.
        assertNull(secure.activated)
        val overlay = sink.emitted.firstOrNull { it.first == DebugCategory.OVERLAY_PREVIEW }
        assertTrue(overlay != null, "an OVERLAY_PREVIEW flash should be offered")
        assertTrue(
            Regex("overlay #[0-9A-F]{2}000000").containsMatchIn(overlay!!.second),
            "overlay flash should carry a black ARGB hex: ${overlay.second}",
        )
    }

    @Test
    fun elevated_belowThreshold_doesNotFlashOverlayPreview() {
        // The overlay preview is the unprivileged fallback only; the privileged path engages instead.
        val sink = RecordingDebugSink()
        val coordinator = SuperDimmingCoordinator(FakeSecureDimming(), debugSink = sink) { Tier.ELEVATED }

        coordinator.apply(targetBrightness = 5, settings = dimmingOn.copy(debugLevel = 6))

        assertTrue(sink.emitted.none { it.first == DebugCategory.OVERLAY_PREVIEW })
    }

    @Test
    fun explicitDisengage_afterEngaged_clearsState() {
        val secure = FakeSecureDimming()
        val coordinator = SuperDimmingCoordinator(secure) { Tier.ELEVATED }
        coordinator.apply(targetBrightness = 5, settings = dimmingOn)

        coordinator.disengage()

        assertEquals(false, secure.activated)
        assertEquals(0, secure.levels.last())
        // A second disengage is a no-op (engaged latch cleared).
        val levelsAfter = secure.levels.size
        coordinator.disengage()
        assertEquals(levelsAfter, secure.levels.size)
        assertFalse(secure.levels.size > levelsAfter)
    }
}
