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

data class LiveDebugUiState(
    val pipeline: PipelineState = PipelineState(),
    val serviceRunning: Boolean = false,
    val activeContext: String? = null,
    val minBrightness: Int = 0,
    val maxBrightness: Int = 255,
    // G2R-F9: global %AAB_Debug category
    val debugLevel: Int = 0,
    // D-116: global %AAB_PanicSensitivity (0..10)
    val panicSensitivity: Int = 8,
    val panicRequiresPlugged: Boolean = false,
    // G2R-F50: global-flash AccessibilityService enabled
    val globalToastsEnabled: Boolean = false,
)

private data class LiveDebugSettings(
    val minBrightness: Int,
    val maxBrightness: Int,
    val debugLevel: Int,
    val panicSensitivity: Int,
    val panicRequiresPlugged: Boolean,
)

/** S12.6b, G2R-F6/F9: drives LiveDebugScreen. Live %AAB_* from LiveRuntimeState; settings from DataStore. */
class LiveDebugViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application

    private val settingsFlow = app.settingsDataStore.data
        .map { LiveDebugSettings(it.minBrightness, it.maxBrightness, it.debugLevel, it.panicSensitivity, it.panicRequiresPlugged) }

    // G2R-F50: re-read on demand (system settings outside DataStore)
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
            minBrightness = settings.minBrightness,
            maxBrightness = settings.maxBrightness,
            debugLevel = settings.debugLevel,
            panicSensitivity = settings.panicSensitivity,
            panicRequiresPlugged = settings.panicRequiresPlugged,
            globalToastsEnabled = global,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveDebugUiState())

    fun setDebugLevel(level: Int) {
        viewModelScope.launch {
            app.settingsDataStore.updateData { it.copy(debugLevel = level) }
            // G2R-F52: instant debug-off/switch; reapply for pipeline pickup
            if (level == 0) AabFlash.cancel()
            if (app.settingsDataStore.data.first().serviceEnabled) AutoBrightnessRuntime.reapply(app)
        }
    }

    /** D-116: persist global %AAB_PanicSensitivity (0..10). Global, never per-profile. */
    fun setPanicSensitivity(value: Int) {
        viewModelScope.launch {
            app.settingsDataStore.updateData { it.copy(panicSensitivity = value.coerceIn(0, 10)) }
        }
    }

    /** DB-009: %AAB_PanicPlugged (#110). Global pref (safety hatch must not vary by context). */
    fun setPanicRequiresPlugged(value: Boolean) {
        viewModelScope.launch {
            app.settingsDataStore.updateData { it.copy(panicRequiresPlugged = value) }
        }
    }

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
