package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.platform.sensor.ProximitySensorSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** prof759/task545 proximity lifecycle, kept OUT of pipeline orchestrator. Collects [source] and reports near/far via [onNear] (maps to %AAB_Proximity damp). */
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
