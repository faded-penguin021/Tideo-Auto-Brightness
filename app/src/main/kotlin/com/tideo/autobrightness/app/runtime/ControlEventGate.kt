package com.tideo.autobrightness.app.runtime

import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * DA-043: the admission bound on control events entering the pipeline queue.
 *
 * `ControlReceiver` admits one external command at a time, but it releases that slot as soon as
 * *routing* finishes — and most verbs finish routing by posting an event. So the receiver bounds
 * concurrent broadcast coroutines, not the work they leave behind: a sequential flood simply moved
 * the backlog downstream into the pipeline's `UNLIMITED` channel.
 *
 * Two layers, because neither is sufficient alone:
 *
 *  1. **Consecutive-duplicate coalescing.** Every control event is idempotent with respect to its
 *     immediate predecessor of the same type — pausing twice is paused, two back-to-back reapplies
 *     recompute the same settings. Collapsing only *consecutive* duplicates is what makes this safe:
 *     an alternating `Pause → Resume → Pause` keeps all three, so no state transition is lost.
 *     (Same-type-anywhere coalescing would drop that third Pause and leave the pipeline resumed
 *     against the user's last intent — `ControlFloodBoundTest` pins exactly this.)
 *  2. **A hard cap.** Alternating verbs coalesce to nothing, so duplicates alone bound nothing
 *     against a hostile sender. Past [maxPending] queued control events the newest is dropped: a
 *     caller flooding faster than the pipeline drains has already lost ordering meaning, and every
 *     control verb is re-sendable.
 *
 * Not handled here, deliberately: `PANIC` and `DISABLE` never enter this queue — they are service
 * actions that run teardown directly — so terminal commands cannot be starved by a flood and no
 * priority lane is needed. Sensor ticks have their own drop-not-queue mutex (D-027).
 */
internal class ControlEventGate(private val maxPending: Int = MAX_PENDING_CONTROL) {

    /**
     * The pipeline mailbox. UNLIMITED because a producer must never be suspended holding a binder
     * thread; the bound is this class's job, not the channel's.
     */
    private val events = Channel<PipelineEvent>(Channel.UNLIMITED)

    /** Type of the newest QUEUED control event; cleared as the consumer takes it. */
    private val lastType = AtomicReference<Class<*>?>(null)
    private val pending = AtomicInteger(0)
    private val dropped = AtomicInteger(0)

    val pendingCount: Int get() = pending.get()
    val droppedCount: Int get() = dropped.get()

    /**
     * Offer [event] to [send], honouring both layers. Returns true when it was queued.
     *
     * [coalescible] is false for value-carrying events (`OverrideDetected` holds the observed
     * brightness, so folding two would act on the older slider position — cap it, never fold it).
     */
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

    /**
     * Drain the mailbox, releasing each coalescing slot as the event LEAVES the queue rather than
     * after it is handled: a duplicate arriving mid-handling is a genuinely new request, because the
     * state it observed is the state this handler is leaving behind.
     */
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
        /**
         * The default ceiling on queued control events. Chosen well above any legitimate burst
         * (screen on/off, a settings Apply, a context swap and a few automation verbs arriving
         * together is a handful of events, not dozens) and far below a level at which the backlog is
         * itself the problem.
         */
        const val MAX_PENDING_CONTROL = 64
    }
}
