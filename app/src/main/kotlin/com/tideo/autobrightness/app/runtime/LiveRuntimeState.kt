package com.tideo.autobrightness.app.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide bridge so the in-app UI can observe the live pipeline running inside
 * [AmbientMonitoringService] (a separate component in the same process). The service's
 * [BrightnessPipelineController] owns the authoritative [PipelineState] StateFlow; it republishes
 * each snapshot here (plus the active context label) so the Dashboard can render live lux / target
 * / paused without binding the service.
 *
 * Concurrency: publishes happen only from the service's pipeline-collector coroutine (single
 * writer); the UI reads immutable snapshots. When the service stops it [reset]s so the UI does not
 * show stale "live" data for a dead loop.
 */
object LiveRuntimeState {
    private val _pipeline = MutableStateFlow(PipelineState())
    val pipeline: StateFlow<PipelineState> = _pipeline.asStateFlow()

    private val _activeContext = MutableStateFlow<String?>(null)
    val activeContext: StateFlow<String?> = _activeContext.asStateFlow()

    /** True while a foreground service instance is publishing (i.e. the loop is alive). */
    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    fun publish(state: PipelineState, activeContext: String?) {
        _pipeline.value = state
        _activeContext.value = activeContext
        _serviceRunning.value = true
    }

    fun reset() {
        _pipeline.value = PipelineState()
        _activeContext.value = null
        _serviceRunning.value = false
    }
}
