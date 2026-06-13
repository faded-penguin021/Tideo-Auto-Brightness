package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.domain.brightness.OverrideRules
import com.tideo.autobrightness.platform.observe.BrightnessObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

/**
 * Bridges the platform [BrightnessObserver] (prof755 ContentObserver) to the override
 * detect/pause/resume decision in domain [OverrideRules] (task567 gate).
 *
 * The observer already filters self-writes via the ScreenBrightnessController suppress-echo hook
 * (D-034), so this monitor only needs to apply the remaining prof755 gate clauses. Emitted values
 * are observed brightness levels that should pause the pipeline; the controller turns each into a
 * [PipelineEvent.OverrideDetected] (no state is written from the observer coroutine — D-027).
 */
class OverrideMonitor(
    private val observer: BrightnessObserver,
    private val gateProvider: () -> GateState,
) {
    /** Live snapshot of the prof755/task567 gate inputs at the moment a change is observed. */
    data class GateState(
        val serviceOn: Boolean,
        val autoRunning: Boolean,
        val paused: Boolean,
        val initializing: Boolean,
        val detectOverrides: Boolean,
    )

    /** Emits observed brightness values that qualify as manual overrides. */
    fun overrides(): Flow<Int> = observer.externalChanges().mapNotNull { observed ->
        val g = gateProvider()
        val isOverride = OverrideRules.isManualOverride(
            isServiceOn = g.serviceOn,
            isAutoRunning = g.autoRunning,
            isAlreadyPaused = g.paused,
            isInitializing = g.initializing,
            detectOverrides = g.detectOverrides,
            observedValue = observed,
            // The observer already suppressed our own writes; no further echo set needed here.
            expectedValues = emptySet(),
        )
        if (isOverride) observed else null
    }
}
