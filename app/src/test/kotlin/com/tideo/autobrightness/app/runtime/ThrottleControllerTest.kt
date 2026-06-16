package com.tideo.autobrightness.app.runtime

import kotlin.test.assertEquals
import org.junit.Test

/**
 * S12.8a (G2R-F78): %AAB_Throttle is the ACTUAL `steps×wait` after a change (floored at the user
 * setting), and the Throttle Reinitialization watchdog (task566/prof754) pushes it to the
 * `AnimSteps×MaxWait+10` ceiling after ~10 s of no brightness change.
 */
class ThrottleControllerTest {

    @Test
    fun seed_usesTheSetting() {
        val t = ThrottleController()
        t.seed(1310L)
        assertEquals(1310L, t.throttleMs)
    }

    @Test
    fun afterChange_throttleIsActualStepsTimesWait_flooredAtSetting() {
        val t = ThrottleController()
        t.seed(1310L)
        // A long animation (40×60=2400ms) exceeds the setting → the effective throttle is the actual.
        t.onCycleComplete(now = 1000L, brightnessChanged = true, stepsTimesWaitMs = 2400L, ceilingMs = 6010L, baselineMs = 1310L)
        assertEquals(2400L, t.throttleMs)

        // A short animation (20×25=500ms) is below the setting → floored at the setting.
        t.onCycleComplete(now = 5000L, brightnessChanged = true, stepsTimesWaitMs = 500L, ceilingMs = 6010L, baselineMs = 1310L)
        assertEquals(1310L, t.throttleMs)
    }

    @Test
    fun ceiling_isAnimStepsTimesMaxWaitPlusTen() {
        val t = ThrottleController()
        assertEquals(100L * 100L + 10L, t.ceiling(animSteps = 100, maxWaitMs = 100))
    }

    @Test
    fun afterTenSecondsOfNoChange_throttleClimbsToCeiling() {
        val t = ThrottleController(idleMs = 10_000L)
        t.seed(1310L)
        // Establish a last-change anchor.
        t.onCycleComplete(now = 1_000L, brightnessChanged = true, stepsTimesWaitMs = 600L, ceilingMs = 6010L, baselineMs = 1310L)
        assertEquals(1310L, t.throttleMs)

        // Within the idle window: no change does not yet bump the throttle.
        t.onCycleComplete(now = 9_000L, brightnessChanged = false, stepsTimesWaitMs = 0L, ceilingMs = 6010L, baselineMs = 1310L)
        assertEquals(1310L, t.throttleMs, "still within the idle window")

        // Past the idle window: the watchdog pushes the throttle to the ceiling (stop polling).
        t.onCycleComplete(now = 12_000L, brightnessChanged = false, stepsTimesWaitMs = 0L, ceilingMs = 6010L, baselineMs = 1310L)
        assertEquals(6010L, t.throttleMs, "idle → ceiling")
    }
}
