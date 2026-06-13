package com.tideo.autobrightness.app.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tideo.autobrightness.app.runtime.AutoBrightnessRuntime
import com.tideo.autobrightness.app.runtime.ServiceHealthStore
import com.tideo.autobrightness.app.settings.AabSettingsMapper
import com.tideo.autobrightness.app.settings.SettingsStore
import com.tideo.autobrightness.app.settings.validate
import com.tideo.autobrightness.app.storage.serviceHealthDataStore
import com.tideo.autobrightness.app.storage.settingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsStore = SettingsStore(application.settingsDataStore)
    private val healthStore = ServiceHealthStore(application.serviceHealthDataStore)

    private val mutableState = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = mutableState.asStateFlow()

    private val mutableGraphState = MutableStateFlow(GraphState())
    val graphState: StateFlow<GraphState> = mutableGraphState.asStateFlow()

    private val mutableHealthState = MutableStateFlow(ServiceHealthUiState())
    val healthState: StateFlow<ServiceHealthUiState> = mutableHealthState.asStateFlow()

    init {
        // Observe the DataStore as the source of truth so external writes (e.g. the notification
        // "Disable" action flipping serviceEnabled) propagate to the UI toggle (G1-F3). Only the
        // persisted fields are reconciled; local-only group state (sliders) is preserved.
        viewModelScope.launch {
            getApplication<Application>().settingsDataStore.data
                .map { AabSettingsMapper.toUiState(it.validate()) }
                .distinctUntilChanged()
                .collect { persisted ->
                    mutableState.update {
                        it.copy(
                            enabled = persisted.enabled,
                            minBrightness = persisted.minBrightness,
                            maxBrightness = persisted.maxBrightness,
                        )
                    }
                }
        }
        viewModelScope.launch {
            healthStore.telemetry.collect { telemetry ->
                mutableHealthState.value = ServiceHealthUiState(
                    lastSensorTimestampMs = telemetry.lastSensorTimestampMs,
                    lastApplyTimestampMs = telemetry.lastApplyTimestampMs,
                    degradedMode = telemetry.degradedMode,
                    degradedReason = telemetry.degradedReason,
                )
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        mutate { copy(enabled = enabled) }
        AutoBrightnessRuntime.onSettingChanged(getApplication(), enabled)
    }

    fun updateBrightness(min: Float, max: Float) = mutate { copy(minBrightness = min, maxBrightness = max) }
    fun updateDimming(group: SettingGroup) = mutate { copy(dimming = group) }
    fun updateReactivity(group: SettingGroup) = mutate { copy(reactivity = group) }
    fun updateExperiment(group: SettingGroup) = mutate { copy(experiment = group) }
    fun updateMisc(group: SettingGroup) = mutate { copy(misc = group) }

    private fun mutate(transform: SettingsState.() -> SettingsState) {
        mutableState.update(transform)
        viewModelScope.launch { settingsStore.writeSettings(mutableState.value) }
    }
}
