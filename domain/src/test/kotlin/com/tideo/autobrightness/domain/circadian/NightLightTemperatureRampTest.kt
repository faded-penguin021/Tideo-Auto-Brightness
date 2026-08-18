package com.tideo.autobrightness.domain.circadian

import org.junit.Test
import kotlin.test.assertEquals

/** NightLightTemperatureRamp (D-154): modifier −1 → night anchor, +1 → day anchor, linear between, defensive clamping. */
class NightLightTemperatureRampTest {

    @Test
    fun deepNight_returnsTheNightAnchor() {
        assertEquals(2850, NightLightTemperatureRamp.temperature(-1.0, nightKelvin = 2850, dayKelvin = 4082))
    }

    @Test
    fun fullDay_returnsTheDayAnchor() {
        assertEquals(4082, NightLightTemperatureRamp.temperature(1.0, nightKelvin = 2850, dayKelvin = 4082))
    }

    @Test
    fun midpoint_isTheLinearBlend_rounded() {
        // (2850 + 4082) / 2 = 3466.0
        assertEquals(3466, NightLightTemperatureRamp.temperature(0.0, nightKelvin = 2850, dayKelvin = 4082))
        // Rounding: night 2596, day 4082 → mid 3339.0; quarter-day (−0.5) → 2967.5 → 2968.
        assertEquals(2968, NightLightTemperatureRamp.temperature(-0.5, nightKelvin = 2596, dayKelvin = 4082))
    }

    @Test
    fun outOfRangeModifier_pinsToTheAnchors() {
        assertEquals(2850, NightLightTemperatureRamp.temperature(-3.0, nightKelvin = 2850, dayKelvin = 4082))
        assertEquals(4082, NightLightTemperatureRamp.temperature(3.0, nightKelvin = 2850, dayKelvin = 4082))
    }

    @Test
    fun invertedAnchors_stillClampToTheirRange() {
        // User may set night anchor COOLER than day anchor (legal); ramp runs other way, stays inside [day, night].
        assertEquals(4000, NightLightTemperatureRamp.temperature(-1.0, nightKelvin = 4000, dayKelvin = 3000))
        assertEquals(3000, NightLightTemperatureRamp.temperature(1.0, nightKelvin = 4000, dayKelvin = 3000))
        assertEquals(3500, NightLightTemperatureRamp.temperature(0.0, nightKelvin = 4000, dayKelvin = 3000))
    }
}
