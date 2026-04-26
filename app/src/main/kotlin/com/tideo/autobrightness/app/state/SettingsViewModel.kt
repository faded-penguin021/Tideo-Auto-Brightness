package com.tideo.autobrightness.app.state

import android.app.Application
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tideo.autobrightness.app.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val Application.dataStore by preferencesDataStore(name = "auto_brightness_settings")

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsStore = SettingsStore(application.dataStore)

    private val mutableState = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = mutableState.asStateFlow()

    private val mutableGraphState = MutableStateFlow(GraphState())
    val graphState: StateFlow<GraphState> = mutableGraphState.asStateFlow()

    init {
        viewModelScope.launch {
            mutableState.value = settingsStore.readSettings()
        }
    }

    fun setEnabled(enabled: Boolean) = mutate { copy(enabled = enabled) }
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
