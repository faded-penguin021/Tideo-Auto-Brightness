package com.tideo.autobrightness.domain.brightness

/**
 * Pure decision logic for the manual-override detect / pause / resume state machine.
 *
 * Tasker sources: task567 "Manual Override" (XML L20525-L20885), task569 "Resume After Override"
 * (L20896-L20978), task561 "Process Overrides" (L19385-L19521), prof755 "Allow Override" gate.
 *
 * Platform responsibilities (S7/S9a): posting notifications, shell commands, QS-tile state, and
 * the ContentObserver that delivers observed brightness values. This object holds only pure
 * boolean/data decisions that require no Android APIs.
 *
 * See also: pipeline_spec.md §2 (Override detect / pause / resume state machine).
 */
object OverrideRules {

    /**
     * Decide whether an observed brightness change is a manual (external) override.
     *
     * Tasker: prof755 gate fires when `Service=On ∧ AutoBrightRunning=0 ∧ Manual_Override!~true
     * ∧ Initializing!~true ∧ DetectOverrides!~Off`. task567 act8 then re-checks the same guard.
     *
     * The suppress-echo contract (task696/698 read-back): the pipeline registers the values it
     * intends to write. An observed value outside the registered set — while not in a known
     * pipeline write — is an external override.
     *
     * @param isServiceOn       Current %AAB_Service = On.
     * @param isAutoRunning     Current %AutoBrightRunning = 1 (mid-pipeline write).
     * @param isAlreadyPaused   Current %AAB_Manual_Override = true.
     * @param isInitializing    Current %AAB_Initializing = true.
     * @param detectOverrides   Current %AAB_DetectOverrides = On.
     * @param observedValue     The brightness value observed from the system setting.
     * @param expectedValues    The set of values the pipeline registered as self-writes; empty = no
     *                          suppression (treat any change as external when other gates pass).
     * @return                  True if this change should be treated as a manual override.
     */
    fun isManualOverride(
        isServiceOn: Boolean,
        isAutoRunning: Boolean,
        isAlreadyPaused: Boolean,
        isInitializing: Boolean,
        detectOverrides: Boolean,
        observedValue: Int,
        expectedValues: Set<Int>,
    ): Boolean {
        if (!isServiceOn) return false
        if (isAutoRunning) return false
        if (isAlreadyPaused) return false
        if (isInitializing) return false
        if (!detectOverrides) return false
        // Observed value is one of our own self-writes — suppress
        if (observedValue in expectedValues) return false
        return true
    }

    /**
     * Decide whether the override (pause) condition is still valid after a re-check delay.
     *
     * Tasker: task567 act8 Stop guard — drop the event if service is off, pipeline is mid-run,
     * already paused, or initializing. Mirrors the profile gate but evaluated after the CycleTime
     * wait (to let an in-progress animation finish before deciding).
     */
    fun shouldCommitPause(
        isServiceOn: Boolean,
        isAutoRunning: Boolean,
        isAlreadyPaused: Boolean,
        isInitializing: Boolean,
    ): Boolean {
        if (!isServiceOn) return false
        if (isAutoRunning) return false
        if (isAlreadyPaused) return false
        if (isInitializing) return false
        return true
    }

    /**
     * Record a manual override point (lux, brightness pair) into the capped override history.
     *
     * Tasker: task561 "Process Overrides" — maintains %AAB_Overrides array capped at 50 entries;
     * oldest entry deleted when limit exceeded (acts 12-15 top_of_loop). When dynamic compression
     * is active, stores the de-compressed base brightness (BRIGHT / ScaleDynamicCompress).
     *
     * @param history           Existing override points (lux → brightness).
     * @param lux               Current smoothed lux.
     * @param brightness        Observed brightness (raw BRIGHT, or de-compressed when [dynamicCompress] != 0).
     * @param dynamicCompress   %AAB_ScaleDynamicCompress — non-zero means dynamic scaling is active.
     * @param maxEntries        Maximum history size (50 in Tasker).
     * @return                  Updated override history with the new entry appended; oldest dropped if full.
     */
    fun recordOverridePoint(
        history: List<Pair<Double, Double>>,
        lux: Double,
        brightness: Double,
        dynamicCompress: Double,
        maxEntries: Int = 50,
    ): List<Pair<Double, Double>> {
        val idealBase = if (dynamicCompress != 0.0) {
            (brightness / dynamicCompress).coerceAtMost(255.0)
        } else {
            brightness
        }
        val updated = history + (lux to idealBase)
        return if (updated.size > maxEntries) updated.drop(updated.size - maxEntries) else updated
    }
}
