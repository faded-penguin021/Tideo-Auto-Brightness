package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.platform.sensor.LightSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

enum class SampleRejection {
    SETTINGS_NOT_LOADED, SERVICE_DISABLED, ACCURACY, DEAD_BAND, MUTEX, QUEUE_CLOSED, PAUSED, SCREEN_OFF,
}

enum class RegistrationCause { START, WAKE }

enum class CycleStage { CLAIMED, EVALUATE, ANIMATE }

enum class CycleResult {
    APPLIED, UNCHANGED, SETTLED, DEAD_BAND_STOP, OVERRIDDEN, ABORTED;

    companion object {
        fun of(settled: Boolean, brightnessChanged: Boolean) = when {
            settled -> SETTLED
            brightnessChanged -> APPLIED
            else -> UNCHANGED
        }
    }
}

data class SensorCallback(
    val lux: Double,
    val accuracy: Int,
    val seq: Int,
    val sensorTimestampNanos: Long,
    val callbackElapsedNanos: Long,
    val atMs: Long,
)

data class SensorCallbacks(
    val generation: Int = 0,
    val cause: RegistrationCause? = null,
    val registeredAtMs: Long? = null,
    val listenerRegistered: Boolean? = null,
    val first: SensorCallback? = null,
    val last: SensorCallback? = null,
)

class SensorCallbackLog {
    private val _state = MutableStateFlow(SensorCallbacks())
    val state: StateFlow<SensorCallbacks> = _state.asStateFlow()

    fun registered(cause: RegistrationCause, atMs: Long): Int = _state.updateAndGet {
        SensorCallbacks(generation = it.generation + 1, cause = cause, registeredAtMs = atMs, last = it.last)
    }.generation

    fun unregistered() {
        _state.update { SensorCallbacks(generation = it.generation + 1, last = it.last) }
    }

    fun listenerRegistered(generation: Int, ok: Boolean) {
        _state.update { if (it.generation == generation) it.copy(listenerRegistered = ok) else it }
    }

    fun callback(generation: Int, sample: LightSample, atMs: Long) {
        val record = SensorCallback(
            lux = sample.lux.toDouble(),
            accuracy = sample.accuracy,
            seq = sample.seq,
            sensorTimestampNanos = sample.timestampNanos,
            callbackElapsedNanos = sample.callbackElapsedNanos,
            atMs = atMs,
        )
        _state.update { s ->
            if (s.generation != generation) s
            else s.copy(first = s.first ?: record.takeIf { it.seq == 1 }, last = record)
        }
    }
}

data class CycleProgress(val stage: CycleStage, val startMs: Long, val claim: Int)

data class CompletedCycle(val result: CycleResult, val startMs: Long, val endMs: Long)

data class SampleRejectionRecord(val reason: SampleRejection, val atMs: Long, val trustUnreliable: Boolean?)

data class SensorDiagnostics(
    val received: Int = 0,
    val admitted: Int = 0,
    val rejected: Int = 0,
    val deferred: Int = 0,
    val replaced: Int = 0,
    val settling: Int = 0,
    val lastRejection: SampleRejectionRecord? = null,
    val cycle: CycleProgress? = null,
    val lastCycle: CompletedCycle? = null,
) {
    fun registered() = SensorDiagnostics(lastCycle = lastCycle)

    fun rejected(reason: SampleRejection, atMs: Long, trustUnreliable: Boolean?) =
        copy(rejected = rejected + 1, lastRejection = SampleRejectionRecord(reason, atMs, trustUnreliable))

    fun claimed(claim: Int, atMs: Long) = copy(cycle = CycleProgress(CycleStage.CLAIMED, atMs, claim))

    fun received(rejection: SampleRejection?, claim: Int, atMs: Long, trustUnreliable: Boolean?, replacing: Boolean) =
        copy(received = received + 1).replacing(replacing).run {
            when {
                rejection != null -> rejected(rejection, atMs, trustUnreliable)
                claim != 0 -> claimed(claim, atMs)
                else -> copy(deferred = deferred + 1)
            }
        }

    fun replacing(replaced: Boolean) = if (replaced) copy(replaced = this.replaced + 1) else this

    private fun owns(claim: Int) = cycle?.claim == claim

    fun cycleRejected(reason: SampleRejection, atMs: Long, trustUnreliable: Boolean?, claim: Int, counted: Boolean = true) = when {
        !owns(claim) -> this
        counted -> rejected(reason, atMs, trustUnreliable).copy(cycle = null)
        else -> copy(cycle = null)
    }

    fun cycleDeferred(claim: Int, replaced: Boolean) =
        if (!owns(claim)) this else copy(cycle = null, deferred = deferred + 1).replacing(replaced)

    fun abandoned(claim: Int) = if (!owns(claim)) this else copy(cycle = null)

    fun admitted(claim: Int, continuation: Boolean = false) = when {
        !owns(claim) -> this
        continuation -> copy(settling = settling + 1).stage(CycleStage.EVALUATE, claim)
        else -> copy(admitted = admitted + 1).stage(CycleStage.EVALUATE, claim)
    }

    fun stage(stage: CycleStage, claim: Int) = if (!owns(claim)) this else copy(cycle = cycle?.copy(stage = stage))

    fun completed(result: CycleResult, atMs: Long, claim: Int): SensorDiagnostics {
        val c = cycle?.takeIf { it.claim == claim } ?: return this
        return copy(cycle = null, lastCycle = CompletedCycle(result, c.startMs, atMs))
    }
}
