package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.NightLightPrior
import com.tideo.autobrightness.platform.display.DaltonizerMode
import com.tideo.autobrightness.platform.display.SecureDisplayController
import com.tideo.autobrightness.platform.privilege.Tier
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
 * Applies display-toggle profile fields (D-151/D-152) through ELEVATED-gated [SecureDisplayController],
 * only on change: the seed adopts the baseline unwritten, stop returns to it (Night Light as found,
 * DD-059), D-154's ticker owns a circadian Kelvin (DC-056). Applies serialize under [applyMutex] (D-139).
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
    private val readPrior: suspend () -> NightLightPrior? = { null },
    private val writePrior: suspend (NightLightPrior?) -> Unit = {},
    private val tickIntervalMs: Long = 60_000L,
    private val temperatureRoute: NightLightTemperatureRoute = NightLightTemperatureRoute(display),
    private val handOffScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) {
    private val applyMutex = Mutex()

    // Last asserted or seeded state. Guarded by [applyMutex].
    private var lastApplied: DisplayToggleState? = null

    // Last WRITTEN Kelvin (D-154); diff compares against this not lastApplied.temperatureK. Guarded by [applyMutex].
    private var deviceTempK: Int? = null

    // DC-056: displaced device Kelvin; non-null IS ramp ownership of the key. Guarded by [applyMutex].
    private var anchorK: Int? = null

    // DD-059: Night Light before Tideo's first write; stop() puts it back. Guarded by [applyMutex].
    private var prior: NightLightPrior? = null

    // DD-059: an anchor restore only the key took; the service write is owed. Guarded by [applyMutex].
    private var restoreOwedK: Int? = null

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
                prior = readPrior()
                val afterPanic = panicked.getAndSet(false)
                if (lastApplied == null) {
                    val seed = DisplayToggleState.of(if (afterPanic) AabSettings() else seedSettings)
                    lastApplied = seed
                    deviceTempK = seed.temperatureK?.let { display.nightLightRange(seed.extended).clamp(it) }
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

    /** Service stop: toggles to baseline (D-151), Night Light as found (DD-059). */
    fun stop() {
        if (scope == null) return
        scope = null
        job?.cancel(); job = null
        val owed = runBlocking {
            applyMutex.withLock {
                resting?.let { applyLocked(DisplayToggleState.of(it), it, probe = false, nightLight = false) }
                val held = prior
                if (held != null) restorePriorLocked(held) else releaseAnchorLocked(settings = null)
                restoreOwedK.also { restoreOwedK = null }
            }
        }
        if (owed != null) handOffScope.launch {
            if (display.readNightLightTemperature() == owed) temperatureRoute.restore(owed, quick = false)
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
            writePrior(null) // D-155's defaults are the reset; no later stop puts the pre-Tideo state back
            prior = null
            if (tierProvider() < Tier.ELEVATED) return // nothing we could write (or clear)
            display.setNightLight(false)
            releaseAnchorLocked(settings = null)
            display.setDaltonizer(DaltonizerMode.OFF)
            display.setInversion(false)
            display.setAlwaysOnDisplay(false)
            display.setStayAwakePlugged(false)
            if (display.hdrForceSdrAvailable) display.setHdrForceSdr(false)
            panicked.set(true)
        }
    }

    private suspend fun capturePriorLocked() {
        if (prior != null) return
        val kelvin = anchorK
            ?: temperatureRoute.readDeviceKelvin().fold({ it ?: display.nightLightRange.default }, { null })
        val found = NightLightPrior(display.readNightLight(), kelvin)
        writePrior(found)
        prior = found
    }

    /** DD-059: off, Kelvin, on, so no edge shows a stale Kelvin; the record goes once it landed. */
    private suspend fun restorePriorLocked(held: NightLightPrior) {
        if (tierProvider() < Tier.ELEVATED || !display.nightLightAvailable) return
        val activated = display.readNightLight()
        if (!held.activated && activated) display.setNightLight(false).onFailure { return }
        held.kelvin?.let { kelvin ->
            val railed = kelvin.coerceIn(SecureDisplayController.NIGHT_LIGHT_RAIL_K)
            val shown = temperatureRoute.restore(railed).getOrElse { return }
            restoreOwedK = railed.takeUnless { shown }
        }
        if (held.activated && !activated) display.setNightLight(true).onFailure { return }
        writeAnchor(null)
        anchorK = null
        writePrior(null)
        prior = null
    }

    private suspend fun acquireAnchorLocked(): Int? {
        anchorK?.let { return it }
        val kelvin = temperatureRoute.readDeviceKelvin().getOrElse { return null }
            ?: display.nightLightRange.default
        writeAnchor(kelvin)
        anchorK = kelvin
        return kelvin
    }

    private suspend fun releaseAnchorLocked(settings: AabSettings?): Boolean {
        val anchor = anchorK ?: return true
        if (tierProvider() < Tier.ELEVATED || !display.nightLightAvailable) return false
        val setpoint = settings?.nightLightTemperature
        val restored = if (setpoint != null) {
            writeKelvinLocked(setpoint, settings, probe = false, skipIfCurrent = false)
        } else {
            val railed = anchor.coerceIn(SecureDisplayController.NIGHT_LIGHT_RAIL_K)
            temperatureRoute.restore(railed).onSuccess { shown ->
                deviceTempK = railed
                restoreOwedK = railed.takeUnless { shown }
            }.isSuccess
        }
        if (!restored) return false
        writeAnchor(null)
        anchorK = null
        return true
    }

    /** Diff-write [desired] against [lastApplied]. Caller holds [applyMutex]. */
    private suspend fun applyLocked(
        desired: DisplayToggleState,
        settings: AabSettings,
        probe: Boolean = true,
        nightLight: Boolean = true,
    ) {
        val last = lastApplied
        lastApplied = desired
        if (last == null || desired == last) {
            if (nightLight && !desired.circadianTemp) releaseAnchorLocked(settings)
            return
        }
        // No-op below ELEVATED but keep tracking. Static temperature opinion must track (incl. null);
        // circadian mode does NOT (ramp was never written; first post-grant tick is the feature working).
        if (tierProvider() < Tier.ELEVATED) {
            if (!desired.circadianTemp) {
                deviceTempK = desired.temperatureK?.let { display.nightLightRange(desired.extended).clamp(it) }
            }
            return
        }
        if (nightLight) applyNightLightLocked(desired, last, settings, probe)
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

    private suspend fun applyNightLightLocked(
        desired: DisplayToggleState,
        last: DisplayToggleState,
        settings: AabSettings,
        probe: Boolean,
    ) {
        val switching = desired.nightLight != last.nightLight
        if (switching) capturePriorLocked()
        if (switching && !desired.nightLight) display.setNightLight(false)
        val released = desired.circadianTemp || releaseAnchorLocked(settings)
        if (switching && desired.nightLight) display.setNightLight(true)
        // D-154: both paths diff against deviceTempK, which advances only on a write that landed.
        if (desired.circadianTemp) {
            val kelvin = acquireAnchorLocked()?.let { anchor ->
                circadianTemperature(settings, settings.nightLightTemperature ?: anchor)
            }
            if (kelvin != null) writeKelvinLocked(kelvin, settings, probe)
        } else if (released) {
            val temperature = desired.temperatureK
            if (temperature == null) deviceTempK = null else writeKelvinLocked(temperature, settings, probe)
        }
    }

    /** D-154: one circadian temperature tick. Caller holds [applyMutex]. */
    private suspend fun tickLocked() {
        val settings = latestEffective ?: return
        if (!settings.nightLightCircadianEnabled) return
        if (tierProvider() < Tier.ELEVATED) return
        val anchor = acquireAnchorLocked() ?: return // the tick acquires too: a grant can arrive after the swap
        val kelvin = circadianTemperature(settings, settings.nightLightTemperature ?: anchor) ?: return
        writeKelvinLocked(kelvin, settings)
    }

    /** DD-048: [kelvin] clamped to [settings]' active range; [deviceTempK] tracks what landed. */
    private suspend fun writeKelvinLocked(
        kelvin: Int,
        settings: AabSettings?,
        probe: Boolean = true,
        skipIfCurrent: Boolean = true,
    ): Boolean {
        val extended = settings?.extendedNightLightEnabled == true
        if (skipIfCurrent && display.nightLightRange(extended).clamp(kelvin) == deviceTempK) return true
        capturePriorLocked()
        val current = deviceTempK.takeIf { skipIfCurrent }
        val landed = temperatureRoute.writeClamped(kelvin, extended, probe, current).getOrElse { return false }
        deviceTempK = landed
        return true
    }

    private data class DisplayToggleState(
        val nightLight: Boolean,
        val temperatureK: Int?,
        val circadianTemp: Boolean,
        val extended: Boolean,
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
                extended = settings.extendedNightLightEnabled,
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

    internal companion object {
        val panicked = AtomicBoolean(false) // DD-034: outlives the per-start instance (D-155)
    }
}
