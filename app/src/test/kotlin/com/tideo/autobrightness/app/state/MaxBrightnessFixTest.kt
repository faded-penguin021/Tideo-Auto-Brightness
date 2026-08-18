package com.tideo.autobrightness.app.state

import com.tideo.autobrightness.app.settings.AabSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** D-169: Apply raises MaxBright to curve minimum (Zone 2 End) when form3A < 0 and < 255. */
class MaxBrightnessFixTest {

    /** A steep zone-2 curve whose value at Zone 2 End is ~252 → form3A < 0 at MaxBright 150. */
    private val steepCurve = AabSettings(
        form1A = 5.0, zone1End = 35, form2B = 20f, form2C = 10, zone2End = 3000, maxBrightness = 150,
    )

    @Test
    fun raisesMaxBrightToTheCurveMinimumWhenForm3aNegative() {
        val minReq = steepCurve.minRequiredMaxBrightness()
        assertEquals(253, minReq, "ceil of the Zone 2 End brightness")

        val fix = steepCurve.raiseMaxBrightnessForCurve()
        assertEquals(minReq, fix.raisedTo, "the fix reports the new value")
        assertEquals(minReq, fix.settings.maxBrightness, "MaxBright is raised to fit the curve")
    }

    @Test
    fun defaultCurveIsUntouched() {
        val fix = AabSettings().raiseMaxBrightnessForCurve()
        assertNull(fix.raisedTo, "the default curve has zone-3 headroom — no adjustment")
        assertEquals(AabSettings().maxBrightness, fix.settings.maxBrightness)
    }

    @Test
    fun a5GateAt255IsPreserved() {
        // Even a too-hot curve is left alone once MaxBright is already at the 255 ceiling (Tasker A5:
        // `If maxbright < 255`). The form3A advisory covers that degenerate case instead.
        val fix = steepCurve.copy(maxBrightness = 255).raiseMaxBrightnessForCurve()
        assertNull(fix.raisedTo, "no raise once MaxBright is at 255")
        assertEquals(255, fix.settings.maxBrightness)
    }

    @Test
    fun neverLowersMaxBright() {
        // A gentle curve whose Zone 2 End brightness is far below MaxBright must not be touched.
        val fix = AabSettings(maxBrightness = 240).raiseMaxBrightnessForCurve()
        assertNull(fix.raisedTo)
        assertEquals(240, fix.settings.maxBrightness)
    }
}
