package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.domain.brightness.OverrideRules
import com.tideo.autobrightness.platform.observe.BrightnessObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

/** Bridges BrightnessObserver (prof755) to override detect in OverrideRules (task567). D-034/D-027. */
class OverrideMonitor(
    private val observer: BrightnessObserver,
    private val gateProvider: () -> GateState,
) {
    data class GateState(
        val serviceOn: Boolean,
        val autoRunning: Boolean,
        val paused: Boolean,
        val initializing: Boolean,
        val detectOverrides: Boolean,
        // S12.7a, F64: post-init settle window (ignores trailing self-write echo)
        val suppressed: Boolean = false,
    )

    fun overrides(): Flow<Int> = observer.externalChanges().mapNotNull { observed ->
        val g = gateProvider()
        if (g.suppressed) return@mapNotNull null
        val isOverride = OverrideRules.isManualOverride(
            isServiceOn = g.serviceOn,
            isAutoRunning = g.autoRunning,
            isAlreadyPaused = g.paused,
            isInitializing = g.initializing,
            detectOverrides = g.detectOverrides,
            observedValue = observed,
            expectedValues = emptySet(),
        )
        if (isOverride) observed else null
    }
}
