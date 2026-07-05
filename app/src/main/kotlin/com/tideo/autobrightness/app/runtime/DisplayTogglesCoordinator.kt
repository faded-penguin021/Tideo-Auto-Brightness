package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.platform.display.DaltonizerMode
import com.tideo.autobrightness.platform.display.SecureDisplayController
import com.tideo.autobrightness.platform.privilege.Tier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
) {
    private val applyMutex = Mutex()

    // What the coordinator last asserted (or adopted at seed). Guarded by [applyMutex].
    private var lastApplied: DisplayToggleState? = null

    // The baseline's display fields — the resting state [stop] returns to. Kept fresh by its own
    // collector so stop() never blocks on a DataStore read. Single-writer volatile (the collector).
    @Volatile private var resting: DisplayToggleState? = null

    private var scope: CoroutineScope? = null
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (this.scope != null) return
        this.scope = scope
        job = scope.launch {
            // Seed BEFORE collecting: the resting/last-applied state is the baseline's values, so
            // the first effective emission is a real only-on-change comparison, not a blind write.
            applyMutex.withLock {
                val seed = DisplayToggleState.of(baselineFlow.first())
                resting = seed
                if (lastApplied == null) lastApplied = seed
            }
            launch { baselineFlow.collect { resting = DisplayToggleState.of(it) } }
            effectiveFlow.filterNotNull().collect { effective ->
                applyMutex.withLock { applyLocked(DisplayToggleState.of(effective)) }
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
            applyMutex.withLock { resting?.let { applyLocked(it) } }
        }
    }

    /** Diff-write [desired] against [lastApplied]. Caller holds [applyMutex]. */
    private fun applyLocked(desired: DisplayToggleState) {
        val last = lastApplied
        lastApplied = desired
        if (last == null || desired == last) return
        // No-op below ELEVATED — but keep tracking, so a post-grant session behaves like any other
        // (asserts on the next CHANGE; it never retroactively replays what it skipped).
        if (tierProvider() < Tier.ELEVATED) return
        // Write failures (revoked/stale grant race) are intentionally not retried: the tier gate
        // above is the real guard, and the next differing swap re-writes the field anyway.
        if (desired.nightLight != last.nightLight) display.setNightLight(desired.nightLight)
        val temperature = desired.temperatureK
        if (temperature != null && temperature != last.temperatureK) {
            display.setNightLightTemperature(temperature)
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

    /** The profile fields, normalized (string mode → enum) so comparisons are value-typed. */
    private data class DisplayToggleState(
        val nightLight: Boolean,
        val temperatureK: Int?,
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
