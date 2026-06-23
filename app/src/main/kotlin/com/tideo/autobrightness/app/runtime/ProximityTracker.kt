package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.platform.sensor.ProximitySensorSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * prof759/task545 proximity lifecycle, kept OUT of the pipeline orchestrator so it stays an orchestrator
 * (PipelineFileLayoutTest). While the pipeline is sensing it collects the optional [source] and reports
 * near/far via [onNear]; the controller maps that to `%AAB_Proximity` (the task544 act28/29 ×0.1
 * smoothing-alpha damp). [source] null (controller unit tests / no proximity sensor) → a no-op.
 */
class ProximityTracker(
    private val source: ProximitySensorSource?,
    private val scope: CoroutineScope,
    private val onNear: (Boolean) -> Unit,
) {
    private var job: Job? = null

    /** Begin collecting (idempotent); no-op when there is no source. */
    fun start() {
        val src = source ?: return
        if (job?.isActive == true) return
        job = scope.launch { src.near().collect(onNear) }
    }

    /** Stop collecting (hibernate / teardown). */
    fun stop() {
        job?.cancel()
        job = null
    }
}
