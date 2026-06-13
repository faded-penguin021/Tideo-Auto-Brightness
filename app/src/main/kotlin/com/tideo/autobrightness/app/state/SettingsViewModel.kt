package com.tideo.autobrightness.app.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tideo.autobrightness.app.AppModule
import com.tideo.autobrightness.app.runtime.AutoBrightnessRuntime
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.DefaultProfiles
import com.tideo.autobrightness.app.settings.FieldError
import com.tideo.autobrightness.app.settings.SettingsValidator
import com.tideo.autobrightness.app.storage.settingsDataStore
import com.tideo.autobrightness.domain.brightness.BrightnessFormulae
import com.tideo.autobrightness.platform.privilege.PrivilegeManager
import com.tideo.autobrightness.platform.privilege.Tier
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Single ViewModel backing every S12 parameter screen (Curve & Brightness, Reactivity, Animation &
 * Dimming, Dynamic Scale). The DataStore is the source of truth (G1-F3 / D-043c pattern): edits
 * persist immediately and flow back, so the notification, QS tile and other screens stay coherent.
 *
 * Validation is advisory and Tasker-faithful: [SettingsValidator] (task583/707) reddens fields but
 * never blocks the write — exactly like the Tasker scenes (the anonymous-handler triage maps the
 * 583/707 `_RedInvalidFormulae` rows here, D-027f bucket (c)).
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val privilegeManager: PrivilegeManager = AppModule(application).privilegeManager

    val settings: StateFlow<AabSettings> = app.settingsDataStore.data
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AabSettings())

    /** task583/707 advisory field errors recomputed on every settings change. */
    val errors: StateFlow<List<FieldError>> = app.settingsDataStore.data
        .map { SettingsValidator.validate(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tier: StateFlow<Tier> = privilegeManager.tierFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Tier.NONE)

    fun refreshTier() = privilegeManager.refresh()

    /** Persist a settings edit. Raw value is stored (Tasker keeps invalid-but-advisory values). */
    fun update(transform: (AabSettings) -> AabSettings) {
        viewModelScope.launch { app.settingsDataStore.updateData(transform) }
    }

    /** Reset every parameter to the task570 baseline defaults (anonymous-handler reset rows). */
    fun resetDefaults() {
        viewModelScope.launch {
            val updated = app.settingsDataStore.updateData { current ->
                // Preserve runtime/identity fields; reset only the tunable parameter set.
                AabSettings(
                    serviceEnabled = current.serviceEnabled,
                    contextOverride = current.contextOverride,
                )
            }
            if (updated.serviceEnabled) AutoBrightnessRuntime.reapply(app)
        }
    }

    /** Apply a built-in named profile (task592 set), preserving the live service-enabled flag. */
    fun applyProfile(name: String) {
        val profile = DefaultProfiles.all[name] ?: return
        viewModelScope.launch {
            val updated = app.settingsDataStore.updateData { current ->
                profile.copy(serviceEnabled = current.serviceEnabled)
            }
            // task592/626 apply re-runs Advanced Auto Brightness so the new curve takes effect
            // immediately, not at the next sensor tick (G2-F16).
            if (updated.serviceEnabled) AutoBrightnessRuntime.reapply(app)
        }
    }

    fun replaceAll(newSettings: AabSettings) {
        viewModelScope.launch {
            val updated = app.settingsDataStore.updateData { current ->
                newSettings.copy(serviceEnabled = current.serviceEnabled)
            }
            if (updated.serviceEnabled) AutoBrightnessRuntime.reapply(app)
        }
    }
}

/** Live-derived continuity coefficients (task659) for the Curve & Brightness readout. */
fun AabSettings.derivedCoefficients(): BrightnessFormulae.ContinuityCoefficients =
    BrightnessFormulae.deriveContinuityCoefficients(
        form1A = form1A.toDouble(),
        form2B = form2B.toDouble(),
        form2C = form2C.toDouble(),
        zone1End = zone1End.toDouble(),
        zone2End = zone2End.toDouble(),
        maxBrightness = maxBrightness.toDouble(),
    )
