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
 * Applies the display-toggle PROFILE fields (D-151/D-152, rebuild-only: Night Light +
 * temperature, daltonizer mode, inversion, always-on display, stay-awake-charging, HDR
 * force-SDR) to the device through the ELEVATED-gated [SecureDisplayController] — the
 * super-dimming precedent: profile fields drive a secure feature, applied via the existing
 * context/profile-load path, no-op below ELEVATED.
 *
 * Apply contract (idempotent, only-on-change — D-151 replaces the D-150 schedule system):
 *  - The coordinator collects [ContextEngine.effectiveFlow]; a write happens only for a field whose
 *    desired value CHANGED versus what was last applied. Equal profile swaps write nothing, so a
 *    user who never edits these fields never has a toggle touched (the system Night Light schedule
 *    keeps working), and manual/system changes between profile swaps stick.
 *  - **Seed = the baseline profile's values, adopted without writing.** Service start is not a
 *    profile change: the device is authoritative until the first swap. (Consequence, accepted with
 *    D-151's no-latch trade: a context already active at service start re-asserts its display
 *    fields only if they differ from the baseline's — which is exactly when they matter.)
 *  - `nightLightTemperature == null` means "this profile has no temperature opinion": it is never
 *    written, and leaving a profile that set one does NOT unset it (the system treats the
 *    temperature as a persistent preference; it only shows while Night Light is on).
 *  - **Resting state = the baseline profile's values**: [stop] re-applies the latest baseline
 *    (only-on-change), so a service stop mid-override returns the toggles to the baseline without
 *    any death-safe latch or residual sweep (that was D-150 machinery). A process DEATH skips this
 *    — the next session adopts the baseline and self-heals at its next differing swap.
 *  - **Inert below ELEVATED:** desired state is tracked but nothing is written; after a mid-session
 *    grant the toggles assert on the next change (like the dimming coordinator's tier gate).
 *
 * Circadian temperature (D-154): while the EFFECTIVE profile has `nightLightCircadianEnabled`,
 * the temperature field is owned by a slow ticker instead of the swap diff — every
 * [tickIntervalMs] it asks [circadianTemperature] for the current ramp Kelvin (the same tanh
 * modifier that drives `%AAB_ScaleDynamic`; an independent computation because the pipeline's
 * value goes stale in steady light, the D-110 lesson) and writes only on change. Consequences,
 * by design: manual temperature changes do NOT stick while tracking is on (the toggle is the
 * consent — every OTHER field keeps the manual-changes-stick rule); [deviceTempK] remembers what
 * was last actually written (ramp or static) so leaving a tracking profile for a static one
 * re-asserts the static anchor even when the anchors are numerically equal. The first assert
 * after service start lands on the first tick (≤ one interval) or the first differing swap.
 *
 * Concurrency: its OWN collector coroutine in the service scope — never inside the pipeline cycle
 * (the single-coroutine drop-on-reentry model is BINDING). Every apply serializes under
 * [applyMutex]; [stop] cancels the collector first, then takes the mutex, so an in-flight apply
 * completes before the baseline re-apply and a queued one dies with the cancellation (D-139 class).
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

    // What the coordinator last asserted (or adopted at seed). Guarded by [applyMutex].
    private var lastApplied: DisplayToggleState? = null

    // The Kelvin value last actually WRITTEN (static anchor or circadian tick), or adopted at
    // seed; the temperature diff compares against this, not lastApplied.temperatureK, so the
    // ticker and the swap path can't fight (D-154). Guarded by [applyMutex].
    private var deviceTempK: Int? = null

    // The latest effective settings the collector saw — the tick's input. Guarded by [applyMutex].
    private var latestEffective: AabSettings? = null

    // The baseline's settings — the resting state [stop] returns to. Kept fresh by its own
    // collector so stop() never blocks on a DataStore read. Single-writer volatile (the collector).
    @Volatile private var resting: AabSettings? = null

    private var scope: CoroutineScope? = null
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (this.scope != null) return
        this.scope = scope
        job = scope.launch {
            // Seed BEFORE collecting: the resting/last-applied state is the baseline's values, so
            // the first effective emission is a real only-on-change comparison, not a blind write.
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
            // D-154 ticker: circadian temperature follows the sun even when no profile swap and
            // no pipeline cycle happens (steady light). Delay-first: the swap path already covers
            // "now"; the tick is pure math + at most one settings put per interval.
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

    /**
     * Service stop: return the toggles to the baseline's values (only-on-change — a session that
     * never left the baseline writes nothing). Blocking is a handful of fast settings puts at most
     * (the D-134/D-150 stop precedent); the mutex serializes any in-flight apply first.
     */
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
     * task528 panic (D-155): the Reset gesture is the "give me a usable screen back" escape
     * hatch, so it returns ALL display toggles to their defaults — not to the baseline, which
     * may itself carry the impairing values (grayscale/inversion/Night Light). Writes are
     * UNCONDITIONAL (no diff): panic must also clear residuals this process can't know about
     * (e.g. a post-death D-151 leftover). The temperature is deliberately not written (default
     * = null opinion; Night Light is off after this anyway). Tears the coordinator down like
     * [stop] — the service teardown that follows finds it stopped and re-applies nothing, so
     * the baseline cannot resurrect what panic just cleared. [lastApplied] stays at the
     * defaults: a same-process service restart then re-asserts the baseline on the first
     * effective emission (a cross-death restart is the standard D-151 seed/residual trade).
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
        // No-op below ELEVATED — but keep tracking, so a post-grant session behaves like any other
        // (asserts on the next CHANGE; it never retroactively replays what it skipped). The static
        // temperature opinion must track here too — incl. null — exactly like every other field,
        // or a post-grant swap that doesn't touch the temperature would replay it. Circadian mode
        // deliberately does NOT track: the ramp value was never written, and the first post-grant
        // tick asserting it is the feature working, not a replay.
        if (tierProvider() < Tier.ELEVATED) {
            if (!desired.circadianTemp) deviceTempK = desired.temperatureK
            return
        }
        // Write failures (revoked/stale grant race) are intentionally not retried: the tier gate
        // above is the real guard, and the next differing swap re-writes the field anyway.
        if (desired.nightLight != last.nightLight) display.setNightLight(desired.nightLight)
        // Temperature (D-154): a circadian-tracking profile writes the current ramp value on the
        // swap edge (the ticker keeps it moving after); a static profile writes its non-null
        // anchor. Both diff against deviceTempK, which then tracks the profile's OPINION in static
        // mode (incl. null — the D-151 comparator semantics) and the last RAMP value in circadian
        // mode — so ownership hand-offs between ticker and swaps stay only-on-change without
        // sticking at a stale ramp temperature.
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
        // The HDR field is inert below Android 14 (the controller's availability gate).
        if (desired.hdrForceSdr != last.hdrForceSdr && display.hdrForceSdrAvailable) {
            display.setHdrForceSdr(desired.hdrForceSdr)
        }
    }

    /** One circadian temperature tick (D-154). Caller holds [applyMutex]. */
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

    /** The profile fields, normalized (string mode → enum) so comparisons are value-typed. */
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
                // Validation resets unknown strings to OFF; this fallback covers un-validated input.
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
