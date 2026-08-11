package com.tideo.autobrightness.app.runtime

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/** Freshness of published snapshot (S12.9d): FRESH<3s, AGING 3-10s, STALE>10s (wedge detection). */
enum class Staleness { FRESH, AGING, STALE }

// null timestamp = STALE.
internal fun classifyStaleness(lastPublishMs: Long?, now: Long): Staleness {
    if (lastPublishMs == null) return Staleness.STALE
    val age = now - lastPublishMs
    return when {
        age < 3_000L -> Staleness.FRESH
        age <= 10_000L -> Staleness.AGING
        else -> Staleness.STALE
    }
}

/** Process-wide bridge: UI observes live pipeline from AmbientMonitoringService without service binding. Concurrency: single writer (pipeline-collector). */
object LiveRuntimeState {
    private val _pipeline = MutableStateFlow(PipelineState())
    val pipeline: StateFlow<PipelineState> = _pipeline.asStateFlow()

    private val _activeContext = MutableStateFlow<String?>(null)
    val activeContext: StateFlow<String?> = _activeContext.asStateFlow()

    /** %AAB_ContextOverride: true while manual profile load latches override (S12.7a, F46); resume clears. */
    private val _manualOverride = MutableStateFlow(false)
    val manualOverride: StateFlow<Boolean> = _manualOverride.asStateFlow()

    /** %AAB_CurrentActiveProfile: name of active profile (manual or context-driven); distinct from activeContext. */
    private val _activeProfile = MutableStateFlow<String?>(null)
    val activeProfile: StateFlow<String?> = _activeProfile.asStateFlow()

    fun setActiveProfile(name: String?) {
        _activeProfile.value = name
    }

    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    fun publish(
        state: PipelineState,
        activeContext: String?,
        manualOverride: Boolean = false,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        _pipeline.value = state.copy(lastPublishMs = nowMs)
        _activeContext.value = activeContext
        _manualOverride.value = manualOverride
        _serviceRunning.value = true
    }

    /** Emit snapshot freshness re-evaluated every [intervalMs] to detect wedged loops (S12.9d). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun staleness(
        clock: () -> Long = System::currentTimeMillis,
        intervalMs: Long = 1_000L,
    ): Flow<Staleness> = pipeline.flatMapLatest { state ->
        flow {
            while (true) {
                emit(classifyStaleness(state.lastPublishMs, clock()))
                delay(intervalMs)
            }
        }
    }.distinctUntilChanged()

    fun reset() {
        _pipeline.value = PipelineState()
        _activeContext.value = null
        _manualOverride.value = false
        _serviceRunning.value = false
        _activeProfile.value = null
    }
}
