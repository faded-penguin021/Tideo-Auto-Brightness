package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.platform.sensor.LightSample
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class SensorDiagnosticsTest {

    private fun sample(lux: Float, seq: Int) = LightSample(lux, 3, 0L, seq)

    @Test fun aLateCallbackFromThePreviousRegistration_isNotTheWakesFirstEvent() {
        val log = SensorCallbackLog()
        val sleeping = log.registered(RegistrationCause.START, 1_000L)
        val waking = log.registered(RegistrationCause.WAKE, 9_000L)

        log.callback(sleeping, sample(31.8f, seq = 1), 9_001L)
        log.listenerRegistered(sleeping, ok = false)
        assertNull(log.state.value.first)
        assertNull(log.state.value.listenerRegistered)

        log.callback(waking, sample(0f, seq = 1), 9_002L)
        assertEquals(0.0, log.state.value.first?.lux)
    }

    @Test fun onlyTheClaimThatStartedACycle_canSettleIt() {
        val running = SensorDiagnostics().claimed(claim = 7, atMs = 100L).admitted(claim = 7)

        assertEquals(running, running.completed(CycleResult.ABORTED, 200L, claim = 6))
        val settled = running.completed(CycleResult.ABORTED, 200L, claim = 7)
        assertNull(settled.cycle)
        assertEquals(CompletedCycle(CycleResult.ABORTED, 100L, 200L), settled.lastCycle)
        assertEquals(settled, settled.completed(CycleResult.ABORTED, 300L, claim = 7), "a settled cycle stays settled")
    }

    @Test fun aTickQueuedAcrossARegistration_movesNothingInTheNewOne() {
        val current = SensorDiagnostics(received = 1).claimed(claim = 9, atMs = 100L)

        assertEquals(current, current.admitted(claim = 8))
        assertEquals(current, current.stage(CycleStage.ANIMATE, claim = 8))
        assertEquals(current, current.cycleRejected(SampleRejection.COOLDOWN, 120L, true, claim = 8))
        assertNull(SensorDiagnostics().admitted(claim = 8).cycle, "no claim-less record is ever created")
        assertEquals(0, SensorDiagnostics().admitted(claim = 8).admitted)
    }

    @Test fun unregistering_clearsTheRegistration_andIgnoresTheOldListener() {
        val log = SensorCallbackLog()
        val gone = log.registered(RegistrationCause.START, 1_000L)
        log.listenerRegistered(gone, ok = true)
        log.unregistered()

        log.callback(gone, sample(5f, seq = 2), 2_000L)
        assertNull(log.state.value.cause)
        assertNull(log.state.value.listenerRegistered)
        assertNull(log.state.value.last)
    }

    @Test fun aNewRegistration_resetsTheCounters_butKeepsTheLastCompletedCycle() {
        val before = SensorDiagnostics(received = 5, admitted = 2, rejected = 3)
            .claimed(1, 100L).admitted(claim = 1).completed(CycleResult.APPLIED, 150L, claim = 1)

        val after = before.registered()
        assertEquals(SensorDiagnostics(lastCycle = CompletedCycle(CycleResult.APPLIED, 100L, 150L)), after)
    }
}
