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

/**
 * How fresh the published live pipeline snapshot is, by age of [PipelineState.lastPublishMs]
 * (S12.9d): FRESH < 3 s, AGING 3–10 s, STALE > 10 s. The Dashboard surfaces a banner when the
 * data is STALE while the service still claims to be running (a sign the loop has wedged).
 */
enum class Staleness { FRESH, AGING, STALE }

/** Pure classifier shared by the Flow and its tests. null timestamp (never published / reset) = STALE. */
internal fun classifyStaleness(lastPublishMs: Long?, now: Long): Staleness {
    if (lastPublishMs == null) return Staleness.STALE
    val age = now - lastPublishMs
    return when {
        age < 3_000L -> Staleness.FRESH
        age <= 10_000L -> Staleness.AGING
        else -> Staleness.STALE
    }
}

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

    /**
     * `%AAB_ContextOverride` — true while a MANUAL profile load has latched the override lock
     * (S12.7a, F46). This is distinct from [activeContext]: a context *rule* being active is NOT an
     * override (it is automation working as configured); only a manual load is. Resume clears it.
     */
    private val _manualOverride = MutableStateFlow(false)
    val manualOverride: StateFlow<Boolean> = _manualOverride.asStateFlow()

    /**
     * `%AAB_CurrentActiveProfile` — the name of the profile currently in force, set when the user
     * loads one manually (SettingsViewModel) or a context rule loads one (ContextEngine). Distinct from
     * [activeContext] (the matching rule's name) so the Dashboard can show both "Profile: X" and the
     * context that selected it. In-memory like [activeContext]; cleared on [reset].
     */
    private val _activeProfile = MutableStateFlow<String?>(null)
    val activeProfile: StateFlow<String?> = _activeProfile.asStateFlow()

    /** Publish the active profile name (manual or context-driven load). */
    fun setActiveProfile(name: String?) {
        _activeProfile.value = name
    }

    /** True while a foreground service instance is publishing (i.e. the loop is alive). */
    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    fun publish(
        state: PipelineState,
        activeContext: String?,
        manualOverride: Boolean = false,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        // Stamp the publish time so the staleness gate can age the snapshot (S12.9d).
        _pipeline.value = state.copy(lastPublishMs = nowMs)
        _activeContext.value = activeContext
        _manualOverride.value = manualOverride
        _serviceRunning.value = true
    }

    /**
     * Emits the freshness of the published snapshot, re-evaluated every [intervalMs] so a wedged loop
     * ages into [Staleness.STALE] without a new publish (S12.9d). [clock] is injectable for tests.
     */
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
