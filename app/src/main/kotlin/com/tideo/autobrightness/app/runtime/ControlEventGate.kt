package com.tideo.autobrightness.app.runtime

import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * DA-043: admission bound on control events entering pipeline. Consecutive-duplicate coalescing
 * (pausing twice = paused; keeps alternating sequences) + hard cap at maxPending (drop newest
 * if flooded). PANIC/DISABLE bypass here; sensor ticks use D-027 drop-not-queue mutex.
 */
internal class ControlEventGate(private val maxPending: Int = MAX_PENDING_CONTROL) {

    // UNLIMITED to prevent binder-thread suspension; bound is this class's job.
    private val events = Channel<PipelineEvent>(Channel.UNLIMITED)

    private val lastType = AtomicReference<Class<*>?>(null)
    private val pending = AtomicInteger(0)
    private val dropped = AtomicInteger(0)

    val pendingCount: Int get() = pending.get()
    val droppedCount: Int get() = dropped.get()

    /** Offer event, honouring both layers. coalescible=false for value-carrying events. */
    fun admit(event: PipelineEvent, coalescible: Boolean): Boolean {
        val type = event::class.java
        if (coalescible && lastType.get() == type) return false
        if (pending.get() >= maxPending) {
            dropped.incrementAndGet()
            return false
        }
        if (events.trySend(event).isFailure) return false
        pending.incrementAndGet()
        lastType.set(type)
        return true
    }

    /** Sensor ticks bypass the bound: they carry their own drop-not-queue mutex (D-027). */
    fun offerSensorTick(event: PipelineEvent): Boolean = events.trySend(event).isSuccess

    /** Release coalescing slot as event leaves queue, not after handling. */
    suspend fun consumeEach(handle: suspend (PipelineEvent) -> Unit) {
        for (event in events) {
            if (event !is PipelineEvent.SensorTick) {
                pending.decrementAndGet()
                lastType.compareAndSet(event::class.java, null)
            }
            handle(event)
        }
    }

    private companion object {
        const val MAX_PENDING_CONTROL = 64
    }
}
