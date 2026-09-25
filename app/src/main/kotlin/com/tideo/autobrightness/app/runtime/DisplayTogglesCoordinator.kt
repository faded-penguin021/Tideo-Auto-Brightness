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
 * inversion, always-on display, stay-awake-charging, experimental HDR disabling) to device via
 * ELEVATED-gated [SecureDisplayController], idempotent and only-on-change (D-151 replaces D-150).
 *
 * Seed to baseline values without writing; service stop re-applies baseline; process death skips
 * reapply. D-154 circadian: ticker owns temperature when enabled; DC-056 makes that ownership
 * explicit, process-outliving and handed back. deviceTempK tracks actual writes. D-139 class
 * concurrency: own collector; applies serialize under [applyMutex]; stop then applies baseline.
 */
class DisplayTogglesCoordinator(
    private val effectiveFlow: Flow<AabSettings?>,
    private val baselineFlow: Flow<AabSettings>,
    private val display: SecureDisplayController,
    private val tierProvider: () -> Tier,
    // D-154: ramp Kelvin for these settings and the resolved night anchor, or null when not
    // computable. Pure and non-blocking; called under [applyMutex].
    private val circadianTemperature: (AabSettings, Int) -> Int? = { _, _ -> null },
    private val readAnchor: suspend () -> Int? = { null },
    private val writeAnchor: suspend (Int?) -> Unit = {},
    private val tickIntervalMs: Long = 60_000L,
    private val temperatureRoute: NightLightTemperatureRoute = NightLightTemperatureRoute(display),
) {
    private val applyMutex = Mutex()

    // Last asserted or seeded state. Guarded by [applyMutex].
    private var lastApplied: DisplayToggleState? = null

    // Last WRITTEN Kelvin (D-154); diff compares against this not lastApplied.temperatureK. Guarded by [applyMutex].
    private var deviceTempK: Int? = null

    // DC-056: displaced device Kelvin; non-null IS ramp ownership of the key. Guarded by [applyMutex].
    private var anchorK: Int? = null

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
                anchorK = readAnchor()
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
                resting?.let { applyLocked(DisplayToggleState.of(it), it, probe = false) }
                releaseAnchorLocked(resting)
            }
        }
    }

    /**
     * task528 panic (D-155): reset ALL toggles to defaults (not baseline; may carry impairing values).
     * Writes unconditional; clears D-151 post-death residuals. Tears down coordinator so baseline
     * cannot resurrect. DC-056 revises its temperature clause: a displaced value is put back.
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
            releaseAnchorLocked(settings = null)
            display.setDaltonizer(DaltonizerMode.OFF)
            display.setInversion(false)
            display.setAlwaysOnDisplay(false)
            display.setStayAwakePlugged(false)
            if (display.hdrForceSdrAvailable) display.setHdrForceSdr(false)
        }
    }

    private suspend fun acquireAnchorLocked(): Int? {
        anchorK?.let { return it }
        val kelvin = temperatureRoute.readDeviceKelvin().getOrElse { return null }
            ?: SecureDisplayController.NIGHT_LIGHT_DEFAULT_K
        writeAnchor(kelvin)
        anchorK = kelvin
        return kelvin
    }

    private suspend fun releaseAnchorLocked(settings: AabSettings?): Boolean {
        val anchor = anchorK ?: return true
        if (tierProvider() < Tier.ELEVATED || !display.nightLightAvailable) return false
        val target = settings?.nightLightTemperature ?: anchor
        if (temperatureRoute.write(target, probe = false).isFailure) return false
        deviceTempK = target
        writeAnchor(null)
        anchorK = null
        return true
    }

    /** Diff-write [desired] against [lastApplied]. Caller holds [applyMutex]. */
    private suspend fun applyLocked(desired: DisplayToggleState, settings: AabSettings, probe: Boolean = true) {
        val last = lastApplied
        lastApplied = desired
        if (last == null || desired == last) {
            if (!desired.circadianTemp) releaseAnchorLocked(settings)
            return
        }
        // No-op below ELEVATED but keep tracking. Static temperature opinion must track (incl. null);
        // circadian mode does NOT (ramp was never written; first post-grant tick is the feature working).
        if (tierProvider() < Tier.ELEVATED) {
            if (!desired.circadianTemp) deviceTempK = desired.temperatureK
            return
        }
        val switching = desired.nightLight != last.nightLight
        if (switching && !desired.nightLight) display.setNightLight(false)
        val released = desired.circadianTemp || releaseAnchorLocked(settings)
        if (switching && desired.nightLight) display.setNightLight(true)
        // D-154: both paths diff against deviceTempK, which advances only on a write that landed.
        if (desired.circadianTemp) {
            val kelvin = acquireAnchorLocked()?.let { anchor ->
                circadianTemperature(settings, settings.nightLightTemperature ?: anchor)
            }
            if (kelvin != null && (kelvin == deviceTempK || temperatureRoute.write(kelvin, probe).isSuccess)) {
                deviceTempK = kelvin
            }
        } else if (released) {
            val temperature = desired.temperatureK
            if (temperature == null || temperature == deviceTempK ||
                temperatureRoute.write(temperature, probe).isSuccess
            ) {
                deviceTempK = temperature
            }
        }
        if (desired.daltonizer != last.daltonizer) display.setDaltonizer(desired.daltonizer)
        if (desired.inversion != last.inversion) display.setInversion(desired.inversion)
        if (desired.alwaysOn != last.alwaysOn) display.setAlwaysOnDisplay(desired.alwaysOn)
        if (desired.stayAwake != last.stayAwake) {
            // DB-077: normal transitions preserve masks only DB-078 or panic may replace.
            val deviceStayAwake = display.readStayAwakePlugged()
            if (deviceStayAwake != null && deviceStayAwake != desired.stayAwake) {
                display.setStayAwakePlugged(desired.stayAwake)
            }
        }
        if (desired.hdrForceSdr != last.hdrForceSdr && display.hdrForceSdrAvailable) {
            display.setHdrForceSdr(desired.hdrForceSdr)
        }
    }

    /** D-154: one circadian temperature tick. Caller holds [applyMutex]. */
    private suspend fun tickLocked() {
        val settings = latestEffective ?: return
        if (!settings.nightLightCircadianEnabled) return
        if (tierProvider() < Tier.ELEVATED) return
        val anchor = acquireAnchorLocked() ?: return // the tick acquires too: a grant can arrive after the swap
        val kelvin = circadianTemperature(settings, settings.nightLightTemperature ?: anchor) ?: return
        if (kelvin != deviceTempK && temperatureRoute.write(kelvin).isSuccess) deviceTempK = kelvin
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
