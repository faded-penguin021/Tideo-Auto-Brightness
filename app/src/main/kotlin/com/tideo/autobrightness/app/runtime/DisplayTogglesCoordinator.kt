package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.platform.display.DaltonizerMode
import com.tideo.autobrightness.platform.display.SecureDisplayController
import com.tideo.autobrightness.platform.privilege.Tier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Applies display-toggle PROFILE fields (D-151/D-152: Night Light, temperature, daltonizer,
 * inversion, always-on display, stay-awake-charging, HDR force-SDR) to device via
 * ELEVATED-gated [SecureDisplayController], idempotent and only-on-change (D-151 replaces D-150).
 *
 * Seed to baseline values without writing; service stop re-applies baseline; process death skips
 * reapply. D-154 circadian: ticker owns temperature when enabled; deviceTempK tracks actual
 * writes (ramp or static). D-139 class concurrency: own collector; all applies serialize under
 * [applyMutex]; stop cancels collector then applies baseline.
 */
class DisplayTogglesCoordinator(
    private val effectiveFlow: Flow<AabSettings?>,
    private val baselineFlow: Flow<AabSettings>,
    private val display: SecureDisplayController,
    private val tierProvider: () -> Tier,
    // D-154: the current circadian-ramp Kelvin for the given settings (night anchor + steepness +
    // transition factor come from them), or null when no ramp is computable. Pure and
    // non-blocking; called under [applyMutex].
    private val circadianTemperature: (AabSettings) -> Int? = { null },
    private val tickIntervalMs: Long = 60_000L,
) {
    private val applyMutex = Mutex()

    // Last asserted or seeded state. Guarded by [applyMutex].
    private var lastApplied: DisplayToggleState? = null

    // Last WRITTEN Kelvin (D-154); diff compares against this not lastApplied.temperatureK. Guarded by [applyMutex].
    private var deviceTempK: Int? = null

    // Latest effective settings. Guarded by [applyMutex].
    private var latestEffective: AabSettings? = null

    // Baseline settings; resting state for [stop]. Volatile (collector-writer).
    @Volatile private var resting: AabSettings? = null

    private var scope: CoroutineScope? = null
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (this.scope != null) return
        this.scope = scope
        job = scope.launch {
            // Seed: baseline values for first only-on-change comparison.
            applyMutex.withLock {
                val seedSettings = baselineFlow.first()
                resting = seedSettings
                if (lastApplied == null) {
                    val seed = DisplayToggleState.of(seedSettings)
                    lastApplied = seed
                    deviceTempK = seed.temperatureK
                }
            }
            launch { baselineFlow.collect { resting = it } }
            // D-154 ticker: circadian temperature ramp; delay-first (swap path covers now).
            launch {
                while (true) {
                    delay(tickIntervalMs)
                    applyMutex.withLock { tickLocked() }
                }
            }
            effectiveFlow.filterNotNull().collect { effective ->
                applyMutex.withLock {
                    latestEffective = effective
                    applyLocked(DisplayToggleState.of(effective), effective)
                }
            }
        }
    }

    /** Service stop: return toggles to baseline (only-on-change). D-134/D-150 precedent. */
    fun stop() {
        if (scope == null) return
        scope = null
        job?.cancel(); job = null
        runBlocking {
            applyMutex.withLock {
                resting?.let { applyLocked(DisplayToggleState.of(it), it) }
            }
        }
    }

    /**
     * task528 panic (D-155): reset ALL toggles to defaults (not baseline; may carry impairing values).
     * Writes unconditional; clears D-151 post-death residuals. Temperature not written. Tears down
     * coordinator so baseline cannot resurrect.
     */
    suspend fun panicReset() {
        scope = null
        job?.cancel(); job = null
        applyMutex.withLock {
            lastApplied = DisplayToggleState.of(AabSettings())
            deviceTempK = null
            latestEffective = null
            if (tierProvider() < Tier.ELEVATED) return // nothing we could write (or clear)
            display.setNightLight(false)
            display.setDaltonizer(DaltonizerMode.OFF)
            display.setInversion(false)
            display.setAlwaysOnDisplay(false)
            display.setStayAwakePlugged(false)
            if (display.hdrForceSdrAvailable) display.setHdrForceSdr(false)
        }
    }

    /** Diff-write [desired] against [lastApplied]. Caller holds [applyMutex]. */
    private fun applyLocked(desired: DisplayToggleState, settings: AabSettings) {
        val last = lastApplied
        lastApplied = desired
        if (last == null || desired == last) return
        // No-op below ELEVATED but keep tracking. Static temperature opinion must track (incl. null);
        // circadian mode does NOT (ramp was never written; first post-grant tick is the feature working).
        if (tierProvider() < Tier.ELEVATED) {
            if (!desired.circadianTemp) deviceTempK = desired.temperatureK
            return
        }
        if (desired.nightLight != last.nightLight) display.setNightLight(desired.nightLight)
        // D-154: circadian writes current ramp on swap; static writes non-null anchor.
        // Both diff against deviceTempK to avoid stale ramp sticking.
        if (desired.circadianTemp) {
            circadianTemperature(settings)?.let { kelvin ->
                if (kelvin != deviceTempK) display.setNightLightTemperature(kelvin)
                deviceTempK = kelvin
            }
        } else {
            val temperature = desired.temperatureK
            if (temperature != null && temperature != deviceTempK) {
                display.setNightLightTemperature(temperature)
            }
            deviceTempK = temperature
        }
        if (desired.daltonizer != last.daltonizer) display.setDaltonizer(desired.daltonizer)
        if (desired.inversion != last.inversion) display.setInversion(desired.inversion)
        if (desired.alwaysOn != last.alwaysOn) display.setAlwaysOnDisplay(desired.alwaysOn)
        if (desired.stayAwake != last.stayAwake) display.setStayAwakePlugged(desired.stayAwake)
        if (desired.hdrForceSdr != last.hdrForceSdr && display.hdrForceSdrAvailable) {
            display.setHdrForceSdr(desired.hdrForceSdr)
        }
    }

    /** D-154: one circadian temperature tick. Caller holds [applyMutex]. */
    private fun tickLocked() {
        val settings = latestEffective ?: return
        if (!settings.nightLightCircadianEnabled) return
        if (tierProvider() < Tier.ELEVATED) return
        val kelvin = circadianTemperature(settings) ?: return
        if (kelvin != deviceTempK) {
            display.setNightLightTemperature(kelvin)
            deviceTempK = kelvin
        }
    }

    private data class DisplayToggleState(
        val nightLight: Boolean,
        val temperatureK: Int?,
        val circadianTemp: Boolean,
        val daltonizer: DaltonizerMode,
        val inversion: Boolean,
        val alwaysOn: Boolean,
        val stayAwake: Boolean,
        val hdrForceSdr: Boolean,
    ) {
        companion object {
            fun of(settings: AabSettings) = DisplayToggleState(
                nightLight = settings.nightLightEnabled,
                temperatureK = settings.nightLightTemperature,
                circadianTemp = settings.nightLightCircadianEnabled,
                // Fallback for un-validated input.
                daltonizer = DaltonizerMode.entries.firstOrNull { it.name == settings.daltonizerMode }
                    ?: DaltonizerMode.OFF,
                inversion = settings.inversionEnabled,
                alwaysOn = settings.alwaysOnDisplayEnabled,
                stayAwake = settings.stayAwakeChargingEnabled,
                hdrForceSdr = settings.hdrForceSdrEnabled,
            )
        }
    }
}
