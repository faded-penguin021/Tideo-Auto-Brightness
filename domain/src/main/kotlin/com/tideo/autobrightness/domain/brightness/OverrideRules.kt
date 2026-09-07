package com.tideo.autobrightness.domain.brightness

// Pure decision logic for manual-override detect/pause/resume. Sources: task567, task569, task561, prof755.
object OverrideRules {

    // Decide if an observed brightness change is external (task567/prof755). Check gates + suppress-echo (task696/698).
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
        if (observedValue in expectedValues) return false
        return true
    }

    // Decide if override (pause) condition still valid after re-check delay (task567 act8).
    // DC-006: [isManualMode] is a deliberate deviation — task567/prof755 never consult the mode.
    // A non-MANUAL mode means Tideo no longer owns the mode it writes against, so the event is
    // ambiguous rather than user input. Defaults to true: the pre-settle gate is state-only, and a
    // failed mode read must fail toward pausing.
    fun shouldCommitPause(
        isServiceOn: Boolean,
        isAutoRunning: Boolean,
        isAlreadyPaused: Boolean,
        isInitializing: Boolean,
        isManualMode: Boolean = true,
    ): Boolean {
        if (!isServiceOn) return false
        if (isAutoRunning) return false
        if (isAlreadyPaused) return false
        if (isInitializing) return false
        if (!isManualMode) return false
        return true
    }

    // DC-005: one domain step is representational drift, not a user adjustment. Deliberately blind to
    // a persistent ≤1-step deviation; bounded in MAGNITUDE, unlike a time-based grace period.
    fun isRepresentationalDrift(settled: Int, lastApplied: Int?): Boolean =
        lastApplied != null && kotlin.math.abs(settled - lastApplied) <= DOMAIN_DRIFT_TOLERANCE

    const val DOMAIN_DRIFT_TOLERANCE = 1

    // Record override point (lux, brightness) capped at maxEntries (task561, newest-first).
    fun recordOverridePoint(
        history: List<Pair<Double, Double>>,
        lux: Double,
        brightness: Double,
        dynamicCompress: Double,
        scalingUse: Boolean,
        maxEntries: Int = 50,
    ): List<Pair<Double, Double>> {
        var idealBase = if (scalingUse && dynamicCompress != 0.0) brightness / dynamicCompress else brightness
        if (idealBase > 255.0) idealBase = 255.0
        val updated = listOf(lux to idealBase) + history
        return if (updated.size > maxEntries) updated.take(maxEntries) else updated
    }
}
