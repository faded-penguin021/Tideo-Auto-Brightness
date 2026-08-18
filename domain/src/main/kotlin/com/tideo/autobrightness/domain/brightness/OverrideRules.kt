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
