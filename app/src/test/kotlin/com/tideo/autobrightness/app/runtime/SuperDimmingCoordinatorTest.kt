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

    // S14 (refresh-caching fix): apply() must read the tier provider EXACTLY ONCE per cycle, never per
    // animation frame. The provider now returns the cached tier (AppModule); the per-cycle permission
    // check (privilegeManager.refresh()) was moved to the service resume points. Guards against a
    // regression that re-introduces an IPC permission check inside the dimming hot path.
    @Test
    fun apply_readsTierProviderExactlyOncePerCycle() {
        var reads = 0
        val coordinator = SuperDimmingCoordinator(FakeSecureDimming()) { reads++; Tier.ELEVATED }

        coordinator.apply(targetBrightness = 5, settings = dimmingOn)

        assertEquals(1, reads, "the tier should be sampled once per dimming cycle, not repeatedly")
    }

    @Test
    fun elevated_belowThreshold_engagesAndWritesLevel() {
        val secure = FakeSecureDimming()
        val coordinator = SuperDimmingCoordinator(secure) { Tier.ELEVATED }

        coordinator.apply(targetBrightness = 5, settings = dimmingOn)

        assertEquals(true, secure.activated, "reduce_bright_colors_activated should be set on")
        assertTrue(secure.levels.isNotEmpty(), "a dim level should be written")
        assertTrue(secure.levels.last() > 0, "below-threshold dimming should be a positive level")
    }

    // G2R-F65 (REOPENED): PWM-sensitive mode also engages Extra Dim below the threshold (via the
    // task700 finalDimLevel), not only the hardware floor. The previous build only floored.
    @Test
    fun pwmSensitive_belowThreshold_engagesExtraDim() {
        val secure = FakeSecureDimming()
        val coordinator = SuperDimmingCoordinator(secure) { Tier.ELEVATED }
        val pwm = AabSettings(
            dimmingEnabled = false,
            pwmSensitive = true,
            dimmingThreshold = 40,
            pwmExponent = 0.8f,
            minBrightness = 1,
        )

        coordinator.apply(targetBrightness = 5, settings = pwm)

        assertEquals(true, secure.activated, "PWM-sensitive below threshold should engage Extra Dim")
        assertTrue(secure.levels.last() > 0, "a positive reduce_bright_colors level should be written")
    }

    @Test
    fun pwmSensitive_aboveThreshold_doesNotEngage() {
        val secure = FakeSecureDimming()
        val coordinator = SuperDimmingCoordinator(secure) { Tier.ELEVATED }
        val pwm = AabSettings(dimmingEnabled = false, pwmSensitive = true, dimmingThreshold = 40)

        coordinator.apply(targetBrightness = 100, settings = pwm)

        assertNull(secure.activated, "above the threshold PWM dimming must not engage")
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

    // G2R-F90: circadian dimming was inert (dimDynamic hardcoded null, D-040). With %AAB_ScalingUse on,
    // the dim strength is scaled by %AAB_DimDynamic (task646 act7). During daylight (scaleDynamic 1.15
    // ⇒ modifier 1.0 at the default ScaleSpread 15):
    //   DimSpread 100  → DimDynamic 0   → dimming SUPPRESSED (level 0)
    //   DimSpread 0    → DimDynamic 1   → dims normally (identical to the no-scaling path)
    //   DimSpread −100 → DimDynamic 2   → dimming BOOSTED (level above the DimSpread-0 level)
    private val scalingDay = dimmingOn.copy(scalingEnabled = true, scaleSpread = 15)
    private val daylightScale = 1.15 // 1 + (15/100)·1.0

    private fun engageLevel(settings: AabSettings, scaleDynamic: Double): Int {
        val secure = FakeSecureDimming()
        SuperDimmingCoordinator(secure) { Tier.ELEVATED }
            .apply(targetBrightness = 5, settings = settings, scaleDynamic = scaleDynamic)
        return secure.levels.last()
    }

    @Test
    fun circadian_spread100_daylight_suppressesDimming() {
        // G3-F6 (Gate 3): DimSpread 100 in full daylight drives DimDynamic→0 ⇒ dim_shell rounds to 0.
        // The coordinator must DISENGAGE entirely — NOT write reduce_bright_colors_activated=1 at level
        // 0, which still leaves Android's Extra Dim engaged and dims the screen slightly on-device.
        val secure = FakeSecureDimming()
        SuperDimmingCoordinator(secure) { Tier.ELEVATED }
            .apply(targetBrightness = 5, settings = scalingDay.copy(dimSpread = 100), scaleDynamic = daylightScale)
        assertTrue(
            secure.activated != true,
            "DimSpread 100 in daylight must not engage Extra Dim (no residual dim at level 0)",
        )
        assertTrue(
            secure.levels.none { it > 0 },
            "no positive reduce_bright_colors level should be written when dimming is suppressed",
        )
    }

    @Test
    fun circadian_spread0_daylight_matchesNonScaledDimming() {
        // DimSpread 0 ⇒ DimDynamic 1.0, which must equal the plain-strength (scaling off) level.
        val scaled = engageLevel(scalingDay.copy(dimSpread = 0), daylightScale)
        val plain = engageLevel(dimmingOn, scaleDynamic = 1.0) // scalingEnabled = false → dimDynamic null
        assertEquals(plain, scaled, "DimSpread 0 must dim identically to the no-scaling path")
        assertTrue(scaled > 0, "normal dimming below the threshold should be a positive level")
    }

    @Test
    fun circadian_spreadNegative100_daylight_boostsBeyondNormal() {
        val boosted = engageLevel(scalingDay.copy(dimSpread = -100), daylightScale)
        val normal = engageLevel(scalingDay.copy(dimSpread = 0), daylightScale)
        assertTrue(boosted > normal, "DimSpread −100 should boost dimming above the DimSpread-0 level")
    }

    @Test
    fun circadian_atNight_spreadHasNoEffect() {
        // scaleDynamic 1.0 ⇒ modifier 0 ⇒ DimDynamic 1.0 for every spread (night = no circadian shift).
        val night = 1.0
        assertEquals(
            engageLevel(scalingDay.copy(dimSpread = 100), night),
            engageLevel(scalingDay.copy(dimSpread = -100), night),
            "at night (no daylight modifier) the spread must not change the dim level",
        )
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
