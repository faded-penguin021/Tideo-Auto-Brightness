package com.tideo.autobrightness.app.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tideo.autobrightness.app.runtime.LiveRuntimeState
import com.tideo.autobrightness.app.runtime.PipelineState
import com.tideo.autobrightness.app.storage.settingsDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the Live Debug scene renders: the live pipeline snapshot + a few persisted figures. */
data class LiveDebugUiState(
    val pipeline: PipelineState = PipelineState(),
    val serviceRunning: Boolean = false,
    val activeContext: String? = null,
    val minBrightness: Int = 0,
    val maxBrightness: Int = 255,
    /** The GLOBAL %AAB_Debug category (G2R-F9) — the selector lives here, not on Misc. */
    val debugLevel: Int = 0,
)

/**
 * Drives [com.tideo.autobrightness.app.ui.screens.LiveDebugScreen] (S12.6b, G2R-F6/F9). The live
 * `%AAB_*` runtime vars come from [LiveRuntimeState] (the service republishes the pipeline state
 * there); min/max + the global debug category come from the DataStore. [setDebugLevel] writes the
 * category straight to the DataStore — the selector is global, so it never goes through a per-profile
 * or per-screen draft (G2R-F9).
 */
class LiveDebugViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application

    private val settingsFlow = app.settingsDataStore.data
        .map { Triple(it.minBrightness, it.maxBrightness, it.debugLevel) }

    val state: StateFlow<LiveDebugUiState> = combine(
        LiveRuntimeState.pipeline,
        LiveRuntimeState.activeContext,
        LiveRuntimeState.serviceRunning,
        settingsFlow,
    ) { pipeline, context, running, settings ->
        LiveDebugUiState(
            pipeline = pipeline,
            serviceRunning = running,
            activeContext = context,
            minBrightness = settings.first,
            maxBrightness = settings.second,
            debugLevel = settings.third,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveDebugUiState())

    fun setDebugLevel(level: Int) {
        viewModelScope.launch { app.settingsDataStore.updateData { it.copy(debugLevel = level) } }
    }
}
