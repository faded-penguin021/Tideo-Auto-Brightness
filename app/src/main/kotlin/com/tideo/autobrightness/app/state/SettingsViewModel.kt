package com.tideo.autobrightness.app.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tideo.autobrightness.app.AppModule
import com.tideo.autobrightness.app.runtime.AutoBrightnessRuntime
import com.tideo.autobrightness.app.runtime.LiveRuntimeState
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.DefaultProfiles
import com.tideo.autobrightness.app.settings.FieldError
import com.tideo.autobrightness.app.settings.SavedProfile
import com.tideo.autobrightness.app.settings.SettingsValidator
import com.tideo.autobrightness.app.settings.UserProfileStore
import com.tideo.autobrightness.app.storage.settingsDataStore
import com.tideo.autobrightness.domain.brightness.BrightnessFormulae
import com.tideo.autobrightness.domain.wizard.OverridePoint
import com.tideo.autobrightness.platform.privilege.PrivilegeManager
import com.tideo.autobrightness.platform.privilege.Tier
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
    private val appModule = AppModule(application)
    private val privilegeManager: PrivilegeManager = appModule.privilegeManager
    private val userProfiles: UserProfileStore = appModule.userProfileStore

    init {
        // Seed the five built-in profiles once so the Profiles screen + context catalog see them.
        viewModelScope.launch { userProfiles.ensureSeeded() }
    }

    /** Recorded manual-override training points (newest first) feeding the curve wizard (G2R-F13). */
    val overridePoints: StateFlow<List<OverridePoint>> = appModule.overridePointStore.points()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** User-editable saved profiles (built-ins seeded first), for the Profiles screen (G2R-F15). */
    val profiles: StateFlow<List<SavedProfile>> = userProfiles.profilesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
                // Preserve runtime/identity + the global preferences; reset only the tunable set.
                // debugLevel + detectOverrides are GLOBAL (not per-profile), so a reset must keep
                // them — debugLevel belongs to the Live Debug scene now (G2R-F9), detectOverrides
                // is a global reactivity preference (G2-F8).
                AabSettings(
                    serviceEnabled = current.serviceEnabled,
                    contextOverride = current.contextOverride,
                    detectOverrides = current.detectOverrides,
                    debugLevel = current.debugLevel,
                )
            }
            if (updated.serviceEnabled) AutoBrightnessRuntime.reapply(app)
        }
    }

    /**
     * Apply a saved named profile (the [UserProfileStore] set; built-ins are seeded into it). The live
     * service-enabled flag plus the GLOBAL preferences `detectOverrides` and `debugLevel` are preserved:
     * neither is part of the task626 profile snapshot, so loading a profile must not turn manual-override
     * detection off (G2-F8) nor change the selected debug category (G2R-F9).
     *
     * A manual profile load also latches the **manual context lock** `%AAB_ContextOverride=true`
     * (G2R-F30, D-014/D-038a) so the context watchers stop overriding the user's deliberate choice; the
     * Profiles screen surfaces a "Resume" affordance ([resumeContextAutomation]) to clear it.
     */
    fun applyProfile(name: String) {
        viewModelScope.launch {
            val profile = userProfiles.get(name) ?: DefaultProfiles.all[name] ?: return@launch
            val updated = app.settingsDataStore.updateData { current ->
                profile.copy(
                    serviceEnabled = current.serviceEnabled,
                    detectOverrides = current.detectOverrides,
                    debugLevel = current.debugLevel,
                    contextOverride = true, // latch the manual context lock (G2R-F30)
                )
            }
            // Surface the loaded profile on the Dashboard (LiveRuntimeState, in-memory bridge).
            LiveRuntimeState.setActiveProfile(name)
            // task592/626 apply re-runs Advanced Auto Brightness so the new curve takes effect
            // immediately, not at the next sensor tick (G2-F16).
            if (updated.serviceEnabled) AutoBrightnessRuntime.reapply(app)
        }
    }

    fun replaceAll(newSettings: AabSettings) {
        viewModelScope.launch {
            val updated = app.settingsDataStore.updateData { current ->
                // Preserve the live service flag + the global DetectOverrides (G2-F8) and debugLevel
                // (G2R-F9) preferences — neither belongs to an imported profile's parameter set. An
                // import is also a manual load → latch the context lock (G2R-F30).
                newSettings.copy(
                    serviceEnabled = current.serviceEnabled,
                    detectOverrides = current.detectOverrides,
                    debugLevel = current.debugLevel,
                    contextOverride = true,
                )
            }
            if (updated.serviceEnabled) AutoBrightnessRuntime.reapply(app)
        }
    }

    /** Save the current settings as a named profile (create or overwrite, G2R-F15). */
    fun saveCurrentAs(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            userProfiles.save(trimmed, app.settingsDataStore.data.first())
        }
    }

    /**
     * Register an imported/legacy profile into the saved-profile catalog (G2R-F44). The
     * [AppProfileCatalog] reads [UserProfileStore], so registering here makes the profile selectable
     * as a context-rule target without the user having to manually "Save current as…" first. Distinct
     * from [replaceAll], which applies a profile to the live settings; the Profiles screen does both
     * when loading a legacy config.
     */
    fun saveImportedProfile(name: String, settings: AabSettings) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { userProfiles.save(trimmed, settings) }
    }

    /** Delete a saved profile (built-ins can be removed; [restoreFactoryProfiles] re-seeds them). */
    fun deleteProfile(name: String) {
        viewModelScope.launch { userProfiles.delete(name) }
    }

    /** Re-seed the five built-in profiles from [DefaultProfiles] (G2R-F15, owner-decision 3). */
    fun restoreFactoryProfiles() {
        viewModelScope.launch { userProfiles.restoreFactory() }
    }

    /**
     * Clear the manual context lock latched by [applyProfile] and re-evaluate so the context watchers
     * resume overriding (G2R-F30). Mirrors Tasker clearing %AAB_ContextOverride.
     */
    fun resumeContextAutomation() {
        viewModelScope.launch {
            val updated = app.settingsDataStore.updateData { it.copy(contextOverride = false) }
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
