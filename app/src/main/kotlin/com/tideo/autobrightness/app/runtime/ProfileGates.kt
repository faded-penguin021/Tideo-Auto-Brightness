package com.tideo.autobrightness.app.runtime

/** Hardcoded ConditionList gates (D-027: fixed profile set via hand-written boolean expressions).
 * D-021: plain And/Or bind tighter; And2/Or2 are outer joins (left-to-right).
 * ProfileGatesTest asserts every branch (mis-parenthesization silently breaks). */
object ProfileGates {

    /** prof760 gate: ((TrustUnreliable=On) OR (TrustUnreliable=Off AND accuracy>1)) AND
     * ((lux < ThreshAbsLow) OR (lux > ThreshAbsHigh)) AND (MainLoop != On). D-021.
     * thresholdsSeeded=false on first run (task544 act10-17); dead-band bypass then. */
    fun monitorAmbientLightGate(
        trustUnreliable: Boolean,
        accuracy: Int,
        lux: Double,
        threshAbsLow: Double,
        threshAbsHigh: Double,
        mainLoopOn: Boolean,
        thresholdsSeeded: Boolean,
    ): Boolean {
        val accuracyTrust = trustUnreliable || (!trustUnreliable && accuracy > 1)
        val deadBand = !thresholdsSeeded || lux < threshAbsLow || lux > threshAbsHigh
        val mutex = !mainLoopOn
        return accuracyTrust && deadBand && mutex
    }

    /** prof758 gate: (inMorning OR inEvening OR sunDataStale) AND scalingUse. D-021.
     * Each ramp window checked at now±86400 to cover midnight-wrap. */
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

    /** prof755 gate: Service=On AND !AutoBrightRunning AND !ManualOverride AND !Initializing AND DetectOverrides.
     * Mirror of OverrideRules.isManualOverride (domain version adds suppress-echo set). */
    fun allowOverrideGate(
        serviceOn: Boolean,
        autoBrightRunning: Boolean,
        manualOverride: Boolean,
        initializing: Boolean,
        detectOverrides: Boolean,
    ): Boolean = serviceOn && !autoBrightRunning && !manualOverride && !initializing && detectOverrides
}
