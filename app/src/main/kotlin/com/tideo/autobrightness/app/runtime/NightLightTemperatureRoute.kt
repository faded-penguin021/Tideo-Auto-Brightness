package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.platform.display.NightDisplayServiceBridge
import com.tideo.autobrightness.platform.display.SecureDisplayController
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay

/** DC-057: every Night Light Kelvin write; the service too once the key is confirmed ignored. */
class NightLightTemperatureRoute(
    private val display: SecureDisplayController,
    private val bridge: NightDisplayServiceBridge? = null,
    private val isNotHonoured: suspend () -> Boolean = { false },
    private val markNotHonoured: suspend () -> Unit = {},
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val settleMs: Long = SETTLE_MS,
    private val bridgeOutOfRange: AtomicBoolean = BRIDGE_OUT_OF_RANGE,
) {
    enum class Verdict { UNKNOWN, HONOURED, NOT_HONOURED }

    var verdict: Verdict = Verdict.UNKNOWN
        private set

    private var loaded = false
    private var honouredAtMs = 0L
    private var mismatchedKelvin: Int? = null
    private var mismatches = 0

    suspend fun readDeviceKelvin(): Result<Int?> {
        load()
        if (verdict != Verdict.NOT_HONOURED) return Result.success(display.readNightLightTemperature())
        return bridge?.readKelvin()?.let { Result.success(it) } ?: Result.failure(unreachable())
    }

    suspend fun writeClamped(kelvin: Int, extended: Boolean, probe: Boolean = true, current: Int? = null): Result<Int> {
        val device = display.nightLightRange
        if (extended && bridge != null) {
            val target = display.nightLightRange(extended = true).clamp(kelvin)
            if (bridge.setKelvin(target, quick = !probe) != null) {
                bridgeOutOfRange.set(target != device.clamp(target))
                return display.setNightLightTemperature(target).map { target }
            }
        }
        val target = device.clamp(kelvin)
        if (target == current) return Result.success(target)
        // DD-048: the service keeps a binder Kelvin raw, so a key write clamping to the same edge is a no-op.
        if (bridge != null && bridgeOutOfRange.get()) {
            display.setNightLightTemperature(target).onFailure { return Result.failure(it) }
            if (bridge.setKelvin(target, quick = !probe) != null) {
                bridgeOutOfRange.set(false)
                return Result.success(target)
            }
        }
        return write(target, probe && !bridgeOutOfRange.get()).map { target }
    }

    /** DD-059: DC-056's raw hand-back, via the service if the key can't show it; false = service write owed. */
    suspend fun restore(kelvin: Int, quick: Boolean = true): Result<Boolean> {
        load()
        val inRange = kelvin == display.nightLightRange.clamp(kelvin)
        val keyShowsIt = inRange && !bridgeOutOfRange.get() && verdict != Verdict.NOT_HONOURED
        if (bridge == null || keyShowsIt) return display.setNightLightTemperature(kelvin).map { true }
        if (bridge.setKelvin(kelvin, quick) != null) {
            bridgeOutOfRange.set(!inRange)
            return display.setNightLightTemperature(kelvin).map { true }
        }
        return display.setNightLightTemperature(kelvin).map { false }
    }

    suspend fun write(kelvin: Int, probe: Boolean = true): Result<Unit> {
        val result = display.setNightLightTemperature(kelvin)
        val bridge = bridge ?: return result
        if (result.isFailure) return result
        load()
        return when {
            verdict == Verdict.NOT_HONOURED ->
                if (bridge.setKelvin(kelvin, quick = !probe) != null) result else Result.failure(unreachable())
            !probe -> result
            verdict == Verdict.UNKNOWN || nowMs() - honouredAtMs >= RECHECK_MS -> observe(bridge, kelvin)
            else -> result
        }
    }

    private suspend fun observe(bridge: NightDisplayServiceBridge, kelvin: Int): Result<Unit> {
        delay(settleMs)
        if (display.readNightLightTemperature() != kelvin) return Result.success(Unit)
        val service = bridge.readKelvin() ?: return Result.success(Unit)
        if (service == kelvin) {
            verdict = Verdict.HONOURED
            honouredAtMs = nowMs()
            mismatches = 0
            mismatchedKelvin = null
            return Result.success(Unit)
        }
        verdict = Verdict.UNKNOWN
        if (kelvin == mismatchedKelvin) return Result.success(Unit)
        mismatchedKelvin = kelvin
        if (++mismatches < CONFIRMATIONS) return Result.success(Unit)
        markNotHonoured()
        verdict = Verdict.NOT_HONOURED
        return if (bridge.setKelvin(kelvin) != null) Result.success(Unit) else Result.failure(unreachable())
    }

    private suspend fun load() {
        if (loaded) return
        loaded = true
        if (bridge != null && isNotHonoured()) verdict = Verdict.NOT_HONOURED
    }

    private fun unreachable() = IllegalStateException("DC-057: display service unreachable")

    companion object {
        const val SETTLE_MS = 750L
        const val RECHECK_MS = 30 * 60_000L
        const val CONFIRMATIONS = 2

        /** Process-wide: the coordinator and the screen each hold a route. */
        val BRIDGE_OUT_OF_RANGE = AtomicBoolean(false)
    }
}
