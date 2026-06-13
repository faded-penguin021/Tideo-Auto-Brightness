package com.tideo.autobrightness.app.runtime

/**
 * Hardcoded Kotlin transcriptions of the pipeline-profile `<ConditionList>` gates.
 *
 * Per D-027: we port a FIXED set of profiles, so each gate is a hand-written boolean expression
 * with a provenance comment showing the D-021 parenthesization — NOT a generic ConditionList
 * evaluator. A mis-parenthesized gate silently suppresses sensor events with no other test
 * catching it, so [ProfileGatesTest] asserts every branch of each clause.
 *
 * ConditionList boolean semantics (owner-verified, D-021): plain `And`/`Or` bind tighter and form
 * inner sub-expressions (`And` > `Or`); `And2`/`Or2` are the outer joins between those groups,
 * evaluated left-to-right. Source: docs/rebuild/extraction/profiles.md.
 */
object ProfileGates {

    /**
     * prof760 "Monitor Ambient Light" gate (XML L318; profiles.md §prof760).
     *
     * Verbatim conditions c0..c5 with bool sequence `[Or2, And, And2, Or, And2]`:
     *   c0 `%AAB_TrustUnreliable = On`     Or2
     *   c1 `%AAB_TrustUnreliable = Off`    And
     *   c2 `%as_accuracy > 1`              And2
     *   c3 `%as_values1 < %AAB_ThreshAbsLow`   Or
     *   c4 `%as_values1 > %AAB_ThreshAbsHigh`  And2
     *   c5 `%AAB_MainLoop != On`           —
     *
     * Confirmed reading (D-021):
     *   ((TrustUnreliable=On) OR (TrustUnreliable=Off AND as_accuracy>1))
     *   AND ((as_values1 < ThreshAbsLow) OR (as_values1 > ThreshAbsHigh))
     *   AND (MainLoop != On)
     *
     * Three staged gates: accuracy-trust, absolute-threshold dead-band, main-loop mutex.
     *
     * @param trustUnreliable %AAB_TrustUnreliable (true == "On").
     * @param accuracy        %as_accuracy (1=Low, 2=Med, 3=High).
     * @param lux             %as_values1 (raw sensor lux).
     * @param threshAbsLow    %AAB_ThreshAbsLow (set by task546 _Set Thresholds_).
     * @param threshAbsHigh   %AAB_ThreshAbsHigh.
     * @param mainLoopOn      %AAB_MainLoop == "On" (a cycle is already running).
     * @param thresholdsSeeded false on the very first reading, before task546 has ever run; the
     *                          absolute dead-band sub-gate is bypassed so the first reading always
     *                          enters the first-run init path (task544 act10-17).
     */
    fun monitorAmbientLightGate(
        trustUnreliable: Boolean,
        accuracy: Int,
        lux: Double,
        threshAbsLow: Double,
        threshAbsHigh: Double,
        mainLoopOn: Boolean,
        thresholdsSeeded: Boolean,
    ): Boolean {
        // Gate 1 — accuracy trust: ((TrustUnreliable=On) OR (TrustUnreliable=Off AND accuracy>1))
        val accuracyTrust = trustUnreliable || (!trustUnreliable && accuracy > 1)
        // Gate 2 — absolute dead-band: ((lux < ThreshAbsLow) OR (lux > ThreshAbsHigh))
        // Bypassed before the thresholds have ever been seeded (first run).
        val deadBand = !thresholdsSeeded || lux < threshAbsLow || lux > threshAbsHigh
        // Gate 3 — main-loop mutex: (MainLoop != On)
        val mutex = !mainLoopOn
        return accuracyTrust && deadBand && mutex
    }

    /**
     * prof758 "Dynamic Scale Engine" gate (XML L195; profiles.md §prof758).
     *
     * Numerically re-sorted bool sequence (S3.5; alphabetical XML ordering corrected, D-021):
     * twelve plain joins then one final `And2`:
     *   [And, Or, And, Or, And, Or, And, Or, And, Or, And, Or, And2]
     *
     * Reading (D-021):
     *   ((c0∧c1) ∨ (c2∧c3) ∨ (c4∧c5) ∨ (c6∧c7) ∨ (c8∧c9) ∨ (c10∧c11) ∨ c12) AND (ScalingUse=true)
     * where each (c2k∧c2k+1) is a "now is inside a dawn/dusk ramp window" test, evaluated at the
     * current day and at ±86400 s to cover windows that wrap midnight; c12 = sun data stale today.
     *
     * @param nowMod        %TIMES%86400 — seconds since local midnight.
     * @param morningStart  %AAB_MorningStart (sec-of-day).
     * @param morningEnd    %AAB_MorningEnd.
     * @param eveningStart  %AAB_EveningStart.
     * @param eveningEnd    %AAB_EveningEnd.
     * @param sunDataStale  %AAB_SunLastDate != %DATE (today's sun times not yet computed).
     * @param scalingUse    %AAB_ScalingUse.
     */
    fun dynamicScaleGate(
        nowMod: Double,
        morningStart: Double,
        morningEnd: Double,
        eveningStart: Double,
        eveningEnd: Double,
        sunDataStale: Boolean,
        scalingUse: Boolean,
    ): Boolean {
        val inMorning = inWindowWithWrap(nowMod, morningStart, morningEnd)
        val inEvening = inWindowWithWrap(nowMod, eveningStart, eveningEnd)
        val inWindowOrStale = inMorning || inEvening || sunDataStale
        return inWindowOrStale && scalingUse
    }

    /** start < now < end, also checking now±86400 so a window straddling midnight still matches. */
    private fun inWindowWithWrap(now: Double, start: Double, end: Double): Boolean {
        return inWindow(now, start, end) ||
            inWindow(now + 86_400.0, start, end) ||
            inWindow(now - 86_400.0, start, end)
    }

    private fun inWindow(now: Double, start: Double, end: Double): Boolean = now > start && now < end

    /**
     * prof755 "Allow Override" gate (XML L56; profiles.md §prof755).
     *
     * `Service=On AND AutoBrightRunning=0 AND Manual_Override!~true AND Initializing!~true
     *  AND DetectOverrides!~Off` — treat an external brightness write as a manual override only
     * when the service is on, our own pipeline is not mid-write, we are not already paused, not
     * initializing, and override detection is enabled.
     *
     * The pure decision also lives in domain OverrideRules.isManualOverride (which adds the
     * suppress-echo set); this mirror exists so the gate truth-table test covers prof755 too.
     */
    fun allowOverrideGate(
        serviceOn: Boolean,
        autoBrightRunning: Boolean,
        manualOverride: Boolean,
        initializing: Boolean,
        detectOverrides: Boolean,
    ): Boolean = serviceOn && !autoBrightRunning && !manualOverride && !initializing && detectOverrides
}
