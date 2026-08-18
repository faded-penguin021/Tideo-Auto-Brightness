package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.platform.observe.BrightnessObserver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** OverrideMonitor gate (prof755/task567): surface external writes only when gate fully open (S12.9d backfill). */
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
