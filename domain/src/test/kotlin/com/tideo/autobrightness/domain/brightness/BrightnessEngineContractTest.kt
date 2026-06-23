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
                previous = PreviousState(smoothedLux = 1.0, lastRawLux = 1.0),
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
                previous = PreviousState(smoothedLux = 8_000.0, lastRawLux = 8_000.0),
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
                previous = PreviousState(smoothedLux = 20.0, lastRawLux = 20.0),
            ),
        )
        assertEquals(1.0, spikeOutput.luxAlpha, 1e-9)
        assertEquals(800.0, spikeOutput.smoothedLux, 1e-9)

        // Moderate step just over the dead-band (100→126): effective_delta is small → luxAlpha < 1.0
        val smoothOutput = engine.evaluate(
            BrightnessPolicyInput(
                lux = 126.0,
                time = TimeContext(secondsOfDay = 12 * 3600.0),
                previous = PreviousState(smoothedLux = 100.0, lastRawLux = 100.0),
            ),
        )
        assertTrue(smoothOutput.luxAlpha < 1.0)
        assertTrue(smoothOutput.smoothedLux < 126.0)
    }

    @Test
    fun proximityNear_dampsLuxAlphaByTenth() {
        // prof759/task545 (task544 act28/29): while the proximity sensor reads "near", LuxAlpha is
        // damped ×0.1 so a hand/ear over the light sensor doesn't jerk the brightness. It never pauses.
        val base = BrightnessPolicyInput(
            lux = 126.0,
            time = TimeContext(secondsOfDay = 12 * 3600.0),
            previous = PreviousState(smoothedLux = 100.0, lastRawLux = 100.0),
        )
        val far = engine.evaluate(base)
        val near = engine.evaluate(base.copy(proximityNear = true))

        assertTrue(far.luxAlpha > 0.0, "the un-damped alpha should be positive for this step")
        assertEquals(far.luxAlpha * 0.1, near.luxAlpha, 1e-9)
        // Lower alpha tracks the previous smoothed value more closely (less reactive), but still moves.
        assertTrue(
            kotlin.math.abs(near.smoothedLux - 100.0) <= kotlin.math.abs(far.smoothedLux - 100.0),
            "near damps reactivity (smoothed stays closer to the previous value)",
        )
    }

    @Test
    fun manualOverride_shortCircuitsEngine() {
        val output = engine.evaluate(
            BrightnessPolicyInput(
                lux = 100.0,
                time = TimeContext(secondsOfDay = 12 * 3600.0),
                overrides = BrightnessOverrides(manualBrightness = 42),
                previous = PreviousState(smoothedLux = 100.0, lastRawLux = 100.0),
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
                previous = PreviousState(smoothedLux = 200.0, lastRawLux = 200.0),
            ),
        )

        val nightOutput = engine.evaluate(
            BrightnessPolicyInput(
                lux = 200.0,
                time = time.copy(secondsOfDay = 2 * 3600.0),
                dynamicScaling = DynamicScalingConfig(enabled = true, spreadPercent = 15.0),
                previous = PreviousState(smoothedLux = 200.0, lastRawLux = 200.0),
            ),
        )

        assertTrue(dayOutput.scaleDynamic > nightOutput.scaleDynamic)
        assertTrue(dayOutput.targetBrightness >= nightOutput.targetBrightness)
    }
}
