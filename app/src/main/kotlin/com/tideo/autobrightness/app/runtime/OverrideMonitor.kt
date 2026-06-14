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
        /**
         * True while the post-init settle window is open (S12.7a, F64). Set Initial Brightness writes
         * synchronously under `initializing`, but the ContentObserver callback for that write — and for
         * any system brightness recompute the AUTO→MANUAL mode flip triggers — can be delivered *after*
         * `initializing` has reset, racing the observer into flagging our own start/reinit/resume/QS-on
         * write as a manual override. The controller holds this true for a short settle after each
         * initial write so that trailing self-write echo is ignored.
         */
        val suppressed: Boolean = false,
    )

    /** Emits observed brightness values that qualify as manual overrides. */
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
            // The observer already suppressed our own writes; no further echo set needed here.
            expectedValues = emptySet(),
        )
        if (isOverride) observed else null
    }
}
