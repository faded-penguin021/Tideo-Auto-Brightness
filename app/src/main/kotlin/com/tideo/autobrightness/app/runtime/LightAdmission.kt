package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.AabSettings
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** prof760 on the collector: a passing reading claims the cycle, or waits in [pending] (D-027, DC-069). */
internal class LightAdmission(
    private val ctx: PipelineRuntimeContext,
    private val settings: () -> AabSettings?,
    private val throttle: ThrottleController,
    private val controlGate: ControlEventGate,
    private val clock: () -> Long,
    scope: CoroutineScope,
) {
    // %AAB_MainLoop re-entry mutex: true while a sensor cycle is claimed or running.
    private val inCycle = AtomicBoolean(false)
    private val claims = AtomicInteger(0)
    @Volatile private var admittedSession = -1
    private val pending = PendingReadings(scope) { drain() }

    fun newSession(): Int = pending.newSession()

    fun <T> fenced(admit: () -> T): T = pending.fenced(admit)

    fun release() = inCycle.set(false)

    fun onSample(lux: Double, accuracy: Int, from: Int) {
        if (from != pending.session) return
        val now = clock()
        val settings = settings()
        val s = ctx.stateValue
        // Throttle Reinitialization watchdog (task566/prof754, G2R-F78).
        if (settings != null && s.threshAbsLow != null) {
            val significant = lux < (s.threshAbsLow ?: 0.0) || lux > (s.threshAbsHigh ?: 0.0)
            throttle.onSample(now, significant, throttle.ceiling(settings.animSteps, settings.maxWaitMs))
        }
        val reading = pending.reading(lux, accuracy, from)
        // A held reading replaces the pending one unbanded; the drain applies the band then current.
        val held = inCycle.get() || pending.coolingDown || pending.current != null
        val gate = gateRejection(reading, settings, s, mainLoopOn = held, banded = !held)
            ?: SampleRejection.MUTEX.takeUnless { inCycle.compareAndSet(false, true) }
        val rejection = gate.takeUnless { it == SampleRejection.MUTEX }
        val offer = if (rejection == null) pending.offer(reading) else null
        if (offer == PendingReadings.Offer.STALE) return run { if (gate == null) inCycle.set(false) }
        val replacing = offer == PendingReadings.Offer.REPLACED
        val claim = if (gate == null) claims.incrementAndGet() else 0
        ctx.update {
            it.copy(
                lastSampleMs = now,
                throttleMs = throttle.throttleMs,
                sensor = it.sensor.received(rejection, claim, now, settings?.trustUnreliableSensor, replacing),
            )
        }
        if (gate == null) postTick(claim, now, from) else if (rejection == null) drain()
    }

    private fun gateRejection(
        r: PendingReadings.Reading,
        settings: AabSettings?,
        s: PipelineState,
        mainLoopOn: Boolean,
        banded: Boolean = true,
    ) = when {
        settings == null -> SampleRejection.SETTINGS_NOT_LOADED
        !settings.serviceEnabled -> SampleRejection.SERVICE_DISABLED
        else -> ProfileGates.monitorAmbientLightRejection(
            trustUnreliable = settings.trustUnreliableSensor,
            accuracy = r.accuracy,
            lux = r.lux,
            threshAbsLow = s.threshAbsLow ?: 0.0,
            threshAbsHigh = s.threshAbsHigh ?: 0.0,
            mainLoopOn = mainLoopOn,
            thresholdsSeeded = banded && s.threshAbsLow != null && !s.unsettled,
        )
    }

    private fun postTick(claim: Int, now: Long, session: Int) {
        val posted = pending.behindFence { controlGate.offerSensorTick(PipelineEvent.SensorTick(claim, session, it)) }
        if (posted) return
        inCycle.set(false)
        pending.clear()
        ctx.update {
            it.copy(sensor = it.sensor.cycleRejected(SampleRejection.QUEUE_CLOSED, now, settings()?.trustUnreliableSensor, claim))
        }
    }

    /** Claims a cycle for the pending reading once no cycle or cooldown holds it; safe from any thread. */
    fun drain() {
        if (pending.current == null && !continueSettling()) return
        val session = pending.current?.session ?: return
        if (settings() == null) return
        val s = ctx.stateValue
        val now = clock()
        if (s.paused) return invalidate(SampleRejection.PAUSED)
        if (!inCycle.get() && discardIfSettled(s)) return
        val cooldownLeftMs = cooldownLeftMs(s, now)
        if (cooldownLeftMs > 0) return pending.armCooldown(cooldownLeftMs)
        if (!inCycle.compareAndSet(false, true)) return
        val claim = claims.incrementAndGet()
        ctx.update { it.copy(sensor = it.sensor.claimed(claim, now)) }
        postTick(claim, now, session)
    }

    // DC-070: a silent sensor still finishes settling, from the latest reading this sensor session admitted.
    private fun continueSettling(): Boolean {
        val s = ctx.stateValue
        val lux = s.lastRawLux ?: return false
        return continuationRejection(settings(), s) == null && pending.offerContinuation(lux, admittedSession)
    }

    private fun discardIfSettled(s: PipelineState): Boolean {
        val held = pending.current ?: return false
        val settings = settings()
        val rejection = if (held.continuation) continuationRejection(settings, s) else gateRejection(held, settings, s, false)
        if (rejection != SampleRejection.DEAD_BAND || !pending.discard(held)) return false
        if (!held.continuation) ctx.update { it.copy(sensor = it.sensor.rejected(rejection, clock(), settings?.trustUnreliableSensor)) }
        return true
    }

    private fun continuationRejection(settings: AabSettings?, s: PipelineState) = when {
        settings == null -> SampleRejection.SETTINGS_NOT_LOADED
        !settings.serviceEnabled || !s.serviceOn -> SampleRejection.SERVICE_DISABLED
        s.paused -> SampleRejection.PAUSED
        s.hibernated -> SampleRejection.SCREEN_OFF
        !s.unsettled -> SampleRejection.DEAD_BAND
        else -> null
    }

    // task544 act2-9: the throttle gate (G2R-F78).
    private fun cooldownLeftMs(s: PipelineState, now: Long): Long =
        s.lastAcceptedMs?.let { now - it }?.takeIf { it >= 0 }?.let { throttle.throttleMs - it } ?: 0L

    fun invalidate(reason: SampleRejection) {
        if (!pending.clear()) return
        ctx.update { it.copy(sensor = it.sensor.rejected(reason, clock(), settings()?.trustUnreliableSensor)) }
    }

    suspend fun run(tick: PipelineEvent.SensorTick, cycle: suspend (lux: Double, claim: Int, continuation: Boolean) -> Unit) {
        try {
            val reading = pending.take(tick.session, tick.fence)
            val s = ctx.stateValue
            val now = clock()
            val settings = settings()
            val continuation = reading?.continuation == true
            val rejection = reading?.let {
                if (continuation) continuationRejection(settings, s) else gateRejection(it, settings, s, mainLoopOn = false)
            }
            when {
                reading == null -> ctx.update { it.copy(sensor = it.sensor.abandoned(tick.claim)) }
                cooldownLeftMs(s, now) > 0 -> {
                    val replaced = !pending.restore(reading)
                    ctx.update {
                        it.copy(sensor = if (continuation) it.sensor.abandoned(tick.claim) else it.sensor.cycleDeferred(tick.claim, replaced))
                    }
                }
                continuation && rejection != null -> ctx.update { it.copy(sensor = it.sensor.abandoned(tick.claim)) }
                rejection != null -> ctx.update {
                    it.copy(sensor = it.sensor.cycleRejected(rejection, now, settings?.trustUnreliableSensor, tick.claim))
                }
                else -> {
                    admittedSession = tick.session
                    cycle(reading.lux, tick.claim, continuation)
                }
            }
        } finally {
            inCycle.set(false)
            ctx.update { it.copy(sensor = it.sensor.completed(CycleResult.ABORTED, clock(), tick.claim)) }
        }
    }
}
