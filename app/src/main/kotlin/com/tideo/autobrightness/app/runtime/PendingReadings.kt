package com.tideo.autobrightness.app.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/** DC-069: the one light reading a busy cycle or its cooldown kept back; a newer reading replaces it. */
internal class PendingReadings(private val scope: CoroutineScope, private val onCooldownEnd: () -> Unit) {

    data class Reading(val lux: Double, val accuracy: Int, val session: Int, val fence: Long, val continuation: Boolean = false)

    enum class Offer { HELD, REPLACED, STALE }

    private val slot = AtomicReference<Reading?>(null)

    // Control events admitted so far; a reading that arrived after one was queued waits behind it.
    @Volatile private var fence = 0L

    @Volatile private var expiry: Job? = null

    @Volatile var session = 0
        private set

    val current: Reading? get() = slot.get()

    val coolingDown: Boolean get() = expiry?.isActive == true

    fun reading(lux: Double, accuracy: Int, session: Int) = Reading(lux, accuracy, session, fence)

    @Synchronized fun <T> fenced(admit: () -> T): T { fence++; return admit() }

    @Synchronized fun <T> behindFence(post: (fence: Long) -> T): T = post(fence)

    @Synchronized fun offer(reading: Reading): Offer = when {
        reading.session != session -> Offer.STALE
        slot.getAndSet(reading)?.continuation == false -> Offer.REPLACED
        else -> Offer.HELD
    }

    @Synchronized fun offerContinuation(lux: Double, from: Int): Boolean =
        from == session && slot.compareAndSet(null, Reading(lux, accuracy = 0, session = session, fence = fence, continuation = true))

    @Synchronized fun discard(reading: Reading): Boolean = (slot.get() === reading).also { if (it) clear() }

    @Synchronized fun restore(reading: Reading): Boolean = reading.session != session || slot.compareAndSet(null, reading)

    @Synchronized fun take(session: Int, fence: Long): Reading? {
        val r = slot.get()?.takeIf { session == this.session && it.fence <= fence } ?: return null
        slot.set(null)
        return r
    }

    @Synchronized fun armCooldown(delayMs: Long) {
        if (coolingDown || slot.get() == null) return
        expiry = scope.launch {
            delay(delayMs.coerceAtLeast(1L))
            synchronized(this@PendingReadings) { if (expiry === coroutineContext.job) expiry = null }
            onCooldownEnd()
        }
    }

    @Synchronized fun clear(): Boolean {
        expiry?.cancel(); expiry = null
        return slot.getAndSet(null)?.continuation == false
    }

    @Synchronized fun newSession(): Int { clear(); return ++session }
}
