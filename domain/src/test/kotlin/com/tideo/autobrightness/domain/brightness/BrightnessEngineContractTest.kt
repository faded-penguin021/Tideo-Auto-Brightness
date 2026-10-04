package com.tideo.autobrightness.domain.brightness

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrightnessEngineContractTest {
    private val engine = BrightnessEngine()

    @Test
    fun lowLuxScenario_keepsBrightnessNearFloor() {
        val output = engine.evaluate(
            BrightnessPolicyInput(
                lux = 0.8,
                time = TimeContext(secondsOfDay = 2 * 3600.0),
                previous = null, // first run: a completed cycle, not an act19 stop
            ),
        )

        assertTrue(output.targetBrightness in 10..25)
        assertTrue(output.luxAlpha in 0.0..1.0)
    }

    @Test
    fun highLuxScenario_movesTowardHighBrightness() {
        val output = engine.evaluate(
            BrightnessPolicyInput(
                lux = 15_000.0,
                time = TimeContext(secondsOfDay = 13 * 3600.0),
                previous = PreviousState(smoothedLux = 8_000.0, threshDynamicPercent = 17.0),
            ),
        )

        assertTrue(output.targetBrightness > 180)
        assertTrue(output.animationSteps >= 1)
    }

    @Test
    fun rapidLuxSpike_isSmoothedByTaskerFormula() {
        // Large spike (20→800): effective_delta ≈ 36.9 → luxAlpha=1.0 (snap to raw — correct per oracle, D-028 gap-07)
        val spikeOutput = engine.evaluate(
            BrightnessPolicyInput(
                lux = 800.0,
                time = TimeContext(secondsOfDay = 12 * 3600.0),
                previous = PreviousState(smoothedLux = 20.0, threshDynamicPercent = 27.0),
            ),
        )
        assertEquals(1.0, spikeOutput.luxAlpha, 1e-9)
        assertEquals(800.0, spikeOutput.smoothedLux, 1e-9)

        // Moderate step just over the dead-band (100→126): effective_delta is small → luxAlpha < 1.0
        val smoothOutput = engine.evaluate(
            BrightnessPolicyInput(
                lux = 126.0,
                time = TimeContext(secondsOfDay = 12 * 3600.0),
                previous = PreviousState(smoothedLux = 100.0, threshDynamicPercent = 25.0),
            ),
        )
        assertEquals(EvaluationOutcome.SMOOTHED, smoothOutput.outcome)
        assertTrue(smoothOutput.luxAlpha > 0.0 && smoothOutput.luxAlpha < 1.0)
        assertTrue(smoothOutput.smoothedLux < 126.0)
    }

    @Test
    fun proximityNear_dampsSmoothingAndTheAnimationAlike() {
        // Tasker task535 A3b: α ×0.1 before the blend; %LuxAlpha and Map Lux's par2 carry the damped α (DD-024).
        val base = BrightnessPolicyInput(
            lux = 400.0,
            time = TimeContext(secondsOfDay = 12 * 3600.0),
            previous = PreviousState(smoothedLux = 100.0, threshDynamicPercent = 25.0),
        )
        val far = engine.evaluate(base)
        val near = engine.evaluate(base.copy(proximityNear = true))
        val damped = Math.round(far.luxAlpha * 0.1 * 1000.0) / 1000.0
        val (steps, wait, throttle) = engine.calculateAnimation(damped, base.animation, null)

        assertTrue(far.luxAlpha > 0.0, "the un-damped alpha should be positive for this step")
        assertEquals(damped, near.luxAlpha, 1e-9)
        assertEquals(400.0 * damped + 100.0 * (1.0 - damped), near.smoothedLux, 0.5)
        assertTrue(near.smoothedLux < far.smoothedLux, "near ${near.smoothedLux} vs far ${far.smoothedLux}")
        assertTrue(near.targetBrightness <= far.targetBrightness)
        assertTrue(near.animationSteps < far.animationSteps, "near ${near.animationSteps} vs far ${far.animationSteps}")
        assertEquals(steps, near.animationSteps)
        assertEquals(wait, near.animationWaitMs)
        assertEquals(throttle, near.transitionDurationMs)
    }

    @Test
    fun manualOverride_shortCircuitsEngine() {
        val output = engine.evaluate(
            BrightnessPolicyInput(
                lux = 100.0,
                time = TimeContext(secondsOfDay = 12 * 3600.0),
                overrides = BrightnessOverrides(manualBrightness = 42),
                previous = PreviousState(smoothedLux = 100.0, threshDynamicPercent = 25.0),
            ),
        )

        assertEquals(42, output.targetBrightness)
        assertEquals(0L, output.transitionDurationMs)
    }

    @Test
    fun circadianWindow_changesScaleAcrossDayNight() {
        val time = TimeContext(
            secondsOfDay = 12 * 3600.0,
            morningStart = 6 * 3600.0,
            morningEnd = 8 * 3600.0,
            eveningStart = 18 * 3600.0,
            eveningEnd = 20 * 3600.0,
        )

        val dayOutput = engine.evaluate(
            BrightnessPolicyInput(
                lux = 200.0,
                time = time,
                dynamicScaling = DynamicScalingConfig(enabled = true, spreadPercent = 15.0),
                previous = null, // first run: an equal reading would stop at act19
            ),
        )

        val nightOutput = engine.evaluate(
            BrightnessPolicyInput(
                lux = 200.0,
                time = time.copy(secondsOfDay = 2 * 3600.0),
                dynamicScaling = DynamicScalingConfig(enabled = true, spreadPercent = 15.0),
                previous = null,
            ),
        )

        assertTrue(dayOutput.scaleDynamic > nightOutput.scaleDynamic)
        assertTrue(dayOutput.targetBrightness >= nightOutput.targetBrightness)
    }

    @Test
    fun hugeCurveOutput_saturatesAtMaxInsteadOfWrapping() {
        val output = engine.evaluate(
            BrightnessPolicyInput(
                lux = 20.0,
                time = TimeContext(secondsOfDay = 12 * 3600.0),
                previous = null,
                curve = BrightnessCurveConfig(form1A = 1e10),
            ),
        )
        assertEquals(255, output.targetBrightness)
    }
}
