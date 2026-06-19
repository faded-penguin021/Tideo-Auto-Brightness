package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.platform.observe.BrightnessObserver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * S12.9d backfill — the [OverrideMonitor] prof755/task567 gate over the platform brightness observer.
 * Observed external writes only surface as overrides when the gate is fully open (service on, auto not
 * running, not already paused, not initializing, detection enabled, settle window closed).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OverrideMonitorTest {

    private class FakeObserver(private val flow: Flow<Int>) : BrightnessObserver {
        override fun externalChanges(): Flow<Int> = flow
    }

    private val openGate = OverrideMonitor.GateState(
        serviceOn = true,
        autoRunning = false,
        paused = false,
        initializing = false,
        detectOverrides = true,
        suppressed = false,
    )

    private suspend fun overridesFor(gate: OverrideMonitor.GateState, vararg observed: Int): List<Int> =
        OverrideMonitor(FakeObserver(flowOf(*observed.toTypedArray())), { gate }).overrides().toList()

    @Test
    fun openGate_emitsEveryExternalChange() = runTest {
        assertEquals(listOf(120, 200), overridesFor(openGate, 120, 200))
    }

    @Test
    fun settleWindow_suppressesAll() = runTest {
        assertEquals(emptyList<Int>(), overridesFor(openGate.copy(suppressed = true), 120, 200))
    }

    @Test
    fun autoRunning_dropsAll() = runTest {
        // A write while the pipeline is mid-animation is our own, not a manual override.
        assertEquals(emptyList<Int>(), overridesFor(openGate.copy(autoRunning = true), 120))
    }

    @Test
    fun detectionOff_dropsAll() = runTest {
        assertEquals(emptyList<Int>(), overridesFor(openGate.copy(detectOverrides = false), 120))
    }

    @Test
    fun serviceOff_dropsAll() = runTest {
        assertEquals(emptyList<Int>(), overridesFor(openGate.copy(serviceOn = false), 120))
    }
}
