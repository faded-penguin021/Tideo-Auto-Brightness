package com.tideo.autobrightness.app.runtime

import kotlin.test.assertEquals
import org.junit.Test

/**
 * S12.8a (G2R-F78): %AAB_Throttle is the engine's ACTUAL animation duration after a change (NOT
 * floored at MaxSteps×MaxWait+10), and the Throttle Reinitialization watchdog (task566/prof754) pushes
 * it to the `AnimSteps×MaxWait+10` ceiling after ~10 s of no brightness change.
 */
class ThrottleControllerTest {

    @Test
    fun seed_usesTheSetting() {
        val t = ThrottleController()
        t.seed(1310L)
        assertEquals(1310L, t.throttleMs)
    }

    @Test
    fun afterChange_throttleIsTheActualEngineValue_notTheCeiling() {
        val t = ThrottleController()
        t.seed(1310L) // seeded to the default/ceiling figure (20×65+10)
        // A small change animates briefly → the throttle drops to that actual value, NOT the ceiling.
        t.onCycleComplete(now = 1000L, brightnessChanged = true, actualThrottleMs = 510L, ceilingMs = 1310L)
        assertEquals(510L, t.throttleMs, "throttle is the actual steps×wait+10, not floored at the ceiling")

        t.onCycleComplete(now = 5000L, brightnessChanged = true, actualThrottleMs = 135L, ceilingMs = 1310L)
        assertEquals(135L, t.throttleMs)
    }

    @Test
    fun ceiling_isAnimStepsTimesMaxWaitPlusTen() {
        val t = ThrottleController()
        assertEquals(20L * 65L + 10L, t.ceiling(animSteps = 20, maxWaitMs = 65))
    }

    @Test
    fun onSample_stableLightForTenSeconds_climbsToCeiling_evenWithoutACycle() {
        // The owner's report: in stable light prof760 drops every reading and no cycle runs, so the
        // watchdog must run on the SAMPLE path. Only-in-band samples for >10 s → ceiling.
        val t = ThrottleController(idleMs = 10_000L)
        t.seed(510L) // a small actual value from the last change
        t.onSample(now = 1_000L, significant = true, ceilingMs = 1310L) // anchor on a real change
        t.onSample(now = 9_000L, significant = false, ceilingMs = 1310L)
        assertEquals(510L, t.throttleMs, "still within the idle window")
        t.onSample(now = 12_000L, significant = false, ceilingMs = 1310L)
        assertEquals(1310L, t.throttleMs, "10 s of in-band readings → throttle climbs to the ceiling")
    }

    @Test
    fun onSample_significantReadingReanchorsTheIdleTimer() {
        val t = ThrottleController(idleMs = 10_000L)
        t.seed(510L)
        t.onSample(now = 1_000L, significant = true, ceilingMs = 1310L)
        t.onSample(now = 9_000L, significant = false, ceilingMs = 1310L)
        // A significant reading at 9 s resets the clock, so 12 s is only 3 s of idle → no ceiling yet.
        t.onSample(now = 9_000L, significant = true, ceilingMs = 1310L)
        t.onSample(now = 12_000L, significant = false, ceilingMs = 1310L)
        assertEquals(510L, t.throttleMs, "a significant reading re-anchored the idle window")
    }

    @Test
    fun afterTenSecondsOfNoChange_throttleClimbsToCeiling() {
        val t = ThrottleController(idleMs = 10_000L)
        t.seed(1310L)
        // Establish a last-change anchor at the actual value.
        t.onCycleComplete(now = 1_000L, brightnessChanged = true, actualThrottleMs = 510L, ceilingMs = 1310L)
        assertEquals(510L, t.throttleMs)

        // Within the idle window: no change does not yet bump the throttle.
        t.onCycleComplete(now = 9_000L, brightnessChanged = false, actualThrottleMs = 0L, ceilingMs = 1310L)
        assertEquals(510L, t.throttleMs, "still within the idle window")

        // Past the idle window: the watchdog pushes the throttle to the ceiling (stop polling).
        t.onCycleComplete(now = 12_000L, brightnessChanged = false, actualThrottleMs = 0L, ceilingMs = 1310L)
        assertEquals(1310L, t.throttleMs, "idle → ceiling")
    }
}
