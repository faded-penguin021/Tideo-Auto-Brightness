package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.platform.display.NightDisplayServiceBridge
import com.tideo.autobrightness.platform.display.SecureDisplayController
import kotlinx.coroutines.delay

/** DC-057: every Night Light Kelvin write; the service too once the key is confirmed ignored. */
class NightLightTemperatureRoute(
    private val display: SecureDisplayController,
    private val bridge: NightDisplayServiceBridge? = null,
    private val isNotHonoured: suspend () -> Boolean = { false },
    private val markNotHonoured: suspend () -> Unit = {},
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val settleMs: Long = SETTLE_MS,
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
    }
}
