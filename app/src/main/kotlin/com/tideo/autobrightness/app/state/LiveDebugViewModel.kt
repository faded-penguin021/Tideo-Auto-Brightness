package com.tideo.autobrightness.app.state

import android.app.Application
import android.content.ComponentName
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tideo.autobrightness.app.runtime.AabFlash
import com.tideo.autobrightness.app.runtime.AabToastAccessibilityService
import com.tideo.autobrightness.app.runtime.AutoBrightnessRuntime
import com.tideo.autobrightness.app.runtime.LiveRuntimeState
import com.tideo.autobrightness.app.runtime.PipelineState
import com.tideo.autobrightness.app.storage.settingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    /** Whether the opt-in global-flash AccessibilityService is enabled (G2R-F50). */
    val globalToastsEnabled: Boolean = false,
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

    // Re-read on demand (the screen pokes this on resume) since enabling the AccessibilityService
    // happens in system Settings, outside any DataStore/flow we observe (G2R-F50).
    private val globalToasts = MutableStateFlow(isGlobalToastServiceEnabled())

    val state: StateFlow<LiveDebugUiState> = combine(
        LiveRuntimeState.pipeline,
        LiveRuntimeState.activeContext,
        LiveRuntimeState.serviceRunning,
        settingsFlow,
        globalToasts,
    ) { pipeline, context, running, settings, global ->
        LiveDebugUiState(
            pipeline = pipeline,
            serviceRunning = running,
            activeContext = context,
            minBrightness = settings.first,
            maxBrightness = settings.second,
            debugLevel = settings.third,
            globalToastsEnabled = global,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveDebugUiState())

    fun setDebugLevel(level: Int) {
        viewModelScope.launch {
            app.settingsDataStore.updateData { it.copy(debugLevel = level) }
            // Instant debug-off / instant-switch (G2R-F52): clear any flash on screen now, and push
            // the new selection into the running pipeline immediately. The pipeline reads its settings
            // through the ContextEngine's cached effective snapshot, so a bare DataStore write would
            // not take effect until the next reapply — reapply() re-reads the fresh baseline.
            if (level == 0) AabFlash.cancel()
            if (app.settingsDataStore.data.first().serviceEnabled) AutoBrightnessRuntime.reapply(app)
        }
    }

    /** Re-poll whether the global-flash AccessibilityService is enabled (call on screen resume). */
    fun refreshGlobalToastStatus() {
        globalToasts.value = isGlobalToastServiceEnabled()
    }

    private fun isGlobalToastServiceEnabled(): Boolean {
        val flattened = Settings.Secure.getString(
            app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val component = ComponentName(app, AabToastAccessibilityService::class.java).flattenToString()
        return flattened.split(':').any { it.equals(component, ignoreCase = true) }
    }
}
