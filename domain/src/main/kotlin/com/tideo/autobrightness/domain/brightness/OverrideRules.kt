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
     * Tasker: task561 "Process Overrides" — maintains %AAB_Overrides array capped at 50 entries.
     * New entries are inserted at position 1 (code355 Array Push, act6/9 — newest-first ordering).
     * Oldest entries are deleted from the tail when the array exceeds 50 (acts 12-15 top_of_loop).
     * When BOTH ScalingUse=true AND ScaleDynamicCompress!=0 (act0 gate), stores the de-compressed
     * base brightness (BRIGHT / ScaleDynamicCompress, capped at 255 per act3-5).
     *
     * @param history           Existing override points (newest at index 0).
     * @param lux               Current smoothed lux (%SmoothedLux).
     * @param brightness        Observed brightness (%BRIGHT).
     * @param dynamicCompress   %AAB_ScaleDynamicCompress.
     * @param scalingUse        %AAB_ScalingUse — must be true alongside non-zero compress to de-compress.
     * @param maxEntries        Maximum history size (50 in Tasker).
     * @return                  Updated override history with the new entry at index 0; oldest dropped if full.
     */
    fun recordOverridePoint(
        history: List<Pair<Double, Double>>,
        lux: Double,
        brightness: Double,
        dynamicCompress: Double,
        scalingUse: Boolean,
        maxEntries: Int = 50,
    ): List<Pair<Double, Double>> {
        // act0: ScalingUse=true AND ScaleDynamicCompress!=0 → de-compress
        var idealBase = if (scalingUse && dynamicCompress != 0.0) brightness / dynamicCompress else brightness
        // act3-5: cap at 255
        if (idealBase > 255.0) idealBase = 255.0
        // act6/9: Array Push at index 1 → newest-first; acts 12-15: drop oldest from tail
        val updated = listOf(lux to idealBase) + history
        return if (updated.size > maxEntries) updated.take(maxEntries) else updated
    }
}
