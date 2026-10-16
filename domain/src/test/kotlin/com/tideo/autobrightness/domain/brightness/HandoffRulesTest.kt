package com.tideo.autobrightness.domain.brightness

import com.tideo.autobrightness.domain.brightness.HandoffRules.Decision
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HandoffRulesTest {

    private val engine = BrightnessEngine()

    private fun curve(form1A: Double = 5.0, form2B: Double = 8.8, maxBrightness: Int = 255): BrightnessCurveConfig {
        val base = BrightnessCurveConfig()
        val c = BrightnessFormulae.deriveContinuityCoefficients(
            form1A, form2B, base.form2C, base.zone1End, base.zone2End, maxBrightness.toDouble(),
        )
        return base.copy(form1A = form1A, form2A = c.form2A, form2B = form2B, form3A = c.form3A, maxBrightness = maxBrightness)
    }

    private fun base(lux: Int, cfg: BrightnessCurveConfig) = engine.mapLuxToBrightness(lux.toDouble(), cfg)

    private fun zone2Inverse(cfg: BrightnessCurveConfig): Double {
        val target = 0.9 * cfg.maxBrightness
        val t = (target - cfg.form2A) / cfg.form2B + (cfg.zone1End - cfg.form2C).pow(0.33)
        return cfg.form2C + t.pow(1 / 0.33)
    }

    private fun assertIsMinimum(min: Int, cfg: BrightnessCurveConfig) {
        val target = 0.9 * cfg.maxBrightness
        assertTrue(base(min, cfg) >= target, "base($min) reaches 0.9 x max")
        assertTrue(base(min - 1, cfg) < target, "base(${min - 1}) falls short")
    }

    @Test
    fun defaultCurve_minimumIs25132() {
        assertEquals(25_132, HandoffRules.minimumHandoffLux(BrightnessCurveConfig()))
        // The app derives Form3A with form2B held as the Float 8.8f.
        assertEquals(25_132, HandoffRules.minimumHandoffLux(curve(form2B = 8.8f.toDouble())))
        assertEquals(ceil(10 * BrightnessCurveConfig().form3A).toInt(), 25_132)
    }

    @Test
    fun reportersPlateau_minimumBelowTheStoredThreshold_keepsTheUsersValue() {
        val cfg = curve(form2B = 35.0)
        assertTrue(base(800, cfg) >= cfg.maxBrightness, "the curve is at max by 800 lx")
        val min = HandoffRules.minimumHandoffLux(cfg)!!
        assertEquals(ceil(zone2Inverse(cfg)).toInt(), min)
        assertIsMinimum(min, cfg)
        assertTrue(min < 2_000)
        assertEquals(2_000, HandoffRules.effectiveThreshold(2_000, cfg))
    }

    @Test
    fun batterySaver_minimumFallsInZone2() {
        val cfg = curve(maxBrightness = 200)
        val min = HandoffRules.minimumHandoffLux(cfg)!!
        assertTrue(min < cfg.zone2End.toInt(), "10 x Form3A (${10 * cfg.form3A}) is short of Zone2End")
        assertEquals(ceil(zone2Inverse(cfg)).toInt(), min)
        assertIsMinimum(min, cfg)
    }

    @Test
    fun negativeForm2B_dipsBelowTarget_minimumIsPastTheDip() {
        val cfg = curve(form1A = 50.0, form2B = -10.0)
        val target = 0.9 * cfg.maxBrightness
        assertTrue(base(30, cfg) >= target, "zone 1 already clears the target")
        assertTrue(base(10_000, cfg) < target, "zone 2 dips below it")
        val min = HandoffRules.minimumHandoffLux(cfg)!!
        assertEquals(ceil(10 * cfg.form3A).toInt(), min)
        assertIsMinimum(min, cfg)
    }

    @Test
    fun form3AZero_zone3SitsAtMax_minimumIsZone2End() {
        val cfg = BrightnessCurveConfig(form3A = 0.0)
        assertEquals(10_000, HandoffRules.minimumHandoffLux(cfg))
    }

    @Test
    fun form3ANegative_zone3AboveMax_minimumStillFound() {
        val cfg = curve(form2B = 35.0)
        assertTrue(cfg.form3A < 0)
        assertTrue(base(HandoffRules.MAX_LUX, cfg) > cfg.maxBrightness)
        assertIsMinimum(HandoffRules.minimumHandoffLux(cfg)!!, cfg)
    }

    @Test
    fun minimumPast100k_isNull_andNeverEnters() {
        val cfg = curve(form2B = -2.0)
        assertTrue(10 * cfg.form3A > HandoffRules.MAX_LUX)
        assertNull(HandoffRules.minimumHandoffLux(cfg))
        assertNull(HandoffRules.effectiveThreshold(30_000, cfg))
        assertEquals(Decision.NONE, HandoffRules.next(false, true, 1_000_000.0, null, 0.8))
    }

    @Test
    fun effectiveThreshold_raisesAStoredValueBelowTheMinimum() {
        val cfg = BrightnessCurveConfig()
        assertEquals(25_132, HandoffRules.effectiveThreshold(1_000, cfg))
        assertEquals(25_132, HandoffRules.effectiveThreshold(25_132, cfg))
        assertEquals(30_000, HandoffRules.effectiveThreshold(30_000, cfg))
    }

    @Test
    fun exitLux_isThresholdTimesHysteresis() {
        assertEquals(20_105.6, HandoffRules.exitLux(25_132, 0.8), 1e-9)
        assertEquals(25_132.0, HandoffRules.exitLux(25_132, 1.0))
    }

    @Test
    fun next_entersStrictlyAboveTheThreshold() {
        assertEquals(Decision.NONE, HandoffRules.next(false, true, 30_000.0, 30_000, 0.8))
        assertEquals(Decision.ENTER, HandoffRules.next(false, true, 30_000.01, 30_000, 0.8))
        assertEquals(Decision.NONE, HandoffRules.next(false, false, 90_000.0, 30_000, 0.8))
    }

    @Test
    fun next_exitsStrictlyBelowTheExitPoint() {
        assertEquals(Decision.STAY, HandoffRules.next(true, true, 24_000.0, 30_000, 0.8))
        assertEquals(Decision.EXIT, HandoffRules.next(true, true, 23_999.99, 30_000, 0.8))
        assertEquals(Decision.STAY, HandoffRules.next(true, true, 90_000.0, 30_000, 0.8))
    }

    @Test
    fun next_fullHysteresis_exitEqualsEntry_bothEdgesStrict() {
        assertEquals(Decision.NONE, HandoffRules.next(false, true, 30_000.0, 30_000, 1.0))
        assertEquals(Decision.STAY, HandoffRules.next(true, true, 30_000.0, 30_000, 1.0))
        assertEquals(Decision.EXIT, HandoffRules.next(true, true, 29_999.99, 30_000, 1.0))
    }

    @Test
    fun next_whileActive_disabledOrNullThresholdExits() {
        assertEquals(Decision.EXIT, HandoffRules.next(true, false, 90_000.0, 30_000, 0.8))
        assertEquals(Decision.EXIT, HandoffRules.next(true, true, 90_000.0, null, 0.8))
    }
}
