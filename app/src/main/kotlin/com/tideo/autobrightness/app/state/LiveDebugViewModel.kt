package com.tideo.autobrightness.app.state

import android.app.Application
import android.content.ComponentName
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tideo.autobrightness.app.AppModule
import com.tideo.autobrightness.app.runtime.AabFlash
import com.tideo.autobrightness.app.runtime.AabToastAccessibilityService
import com.tideo.autobrightness.app.runtime.AutoBrightnessRuntime
import com.tideo.autobrightness.app.runtime.LiveRuntimeState
import com.tideo.autobrightness.app.runtime.PipelineState
import com.tideo.autobrightness.app.runtime.SensorCallbacks
import com.tideo.autobrightness.app.storage.settingsDataStore
import com.tideo.autobrightness.platform.privilege.PrivilegeManager
import com.tideo.autobrightness.platform.privilege.Tier
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
    val sensorCallbacks: SensorCallbacks = SensorCallbacks(),
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
    val nightLight: NightLightDebug = NightLightDebug(),
)

data class NightLightDebug(
    val enabled: Boolean = false,
    val circadian: Boolean = false,
    val temperatureK: Int? = null,
    val rampKelvin: Int? = null,
    val elevated: Boolean = true,
)

private data class LiveDebugSettings(
    val minBrightness: Int,
    val maxBrightness: Int,
    val debugLevel: Int,
    val panicSensitivity: Int,
    val panicRequiresPlugged: Boolean,
    val nightLightEnabled: Boolean,
    val nightLightCircadian: Boolean,
    val nightLightTemperatureK: Int?,
)

/** S12.6b, G2R-F6/F9: drives LiveDebugScreen. Live %AAB_* from LiveRuntimeState; settings from DataStore. */
class LiveDebugViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application

    private val settingsFlow = app.settingsDataStore.data
        .map {
            LiveDebugSettings(
                it.minBrightness, it.maxBrightness, it.debugLevel, it.panicSensitivity, it.panicRequiresPlugged,
                it.nightLightEnabled, it.nightLightCircadianEnabled, it.nightLightTemperature,
            )
        }

    private val privilegeManager: PrivilegeManager = AppModule(application).privilegeManager

    // G2R-F50: re-read on demand (system settings outside DataStore)
    private val globalToasts = MutableStateFlow(isGlobalToastServiceEnabled())

    val state: StateFlow<LiveDebugUiState> = combine(
        combine(LiveRuntimeState.pipeline, LiveRuntimeState.sensorCallbacks.state, ::Pair),
        combine(LiveRuntimeState.activeContext, LiveRuntimeState.serviceRunning, ::Pair),
        combine(LiveRuntimeState.nightLightRampKelvin, privilegeManager.tierFlow(), ::Pair),
        settingsFlow,
        globalToasts,
    ) { (pipeline, callbacks), (context, running), (rampKelvin, tier), settings, global ->
        LiveDebugUiState(
            pipeline = pipeline,
            sensorCallbacks = callbacks,
            serviceRunning = running,
            activeContext = context,
            minBrightness = settings.minBrightness,
            maxBrightness = settings.maxBrightness,
            debugLevel = settings.debugLevel,
            panicSensitivity = settings.panicSensitivity,
            panicRequiresPlugged = settings.panicRequiresPlugged,
            globalToastsEnabled = global,
            nightLight = NightLightDebug(
                enabled = settings.nightLightEnabled,
                circadian = settings.nightLightCircadian,
                temperatureK = settings.nightLightTemperatureK,
                rampKelvin = rampKelvin,
                elevated = tier >= Tier.ELEVATED,
            ),
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

    fun refreshTier() = privilegeManager.refresh()

    private fun isGlobalToastServiceEnabled(): Boolean {
        val flattened = Settings.Secure.getString(
            app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val component = ComponentName(app, AabToastAccessibilityService::class.java).flattenToString()
        return flattened.split(':').any { it.equals(component, ignoreCase = true) }
    }
}
