package com.tideo.autobrightness.app.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tideo.autobrightness.app.AppModule
import com.tideo.autobrightness.app.runtime.AutoBrightnessRuntime
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.DataStoreContextBaselineStore
import com.tideo.autobrightness.app.settings.DefaultProfiles
import com.tideo.autobrightness.app.settings.FieldError
import com.tideo.autobrightness.app.settings.ProfileApplier
import com.tideo.autobrightness.app.settings.SavedProfile
import com.tideo.autobrightness.app.settings.SettingsValidator
import com.tideo.autobrightness.app.settings.UserProfileStore
import com.tideo.autobrightness.app.settings.validate
import com.tideo.autobrightness.app.storage.contextBaselineDataStore
import com.tideo.autobrightness.app.storage.settingsDataStore
import com.tideo.autobrightness.domain.brightness.BrightnessFormulae
import com.tideo.autobrightness.domain.wizard.OverridePoint
import com.tideo.autobrightness.platform.privilege.PrivilegeManager
import com.tideo.autobrightness.platform.privilege.Tier
import kotlin.math.ceil
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** SettingsViewModel for S12 parameter screens (D-043(c), D-027(f)). DataStore is source of truth
 * (immediate persistence + flow-back). Validation advisory, never blocks. */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val appModule = AppModule(application)
    private val privilegeManager: PrivilegeManager = appModule.privilegeManager
    private val userProfiles: UserProfileStore = appModule.userProfileStore

    /** VM-free profile-load/context-resume logic shared with external control receiver (D-157 U3). */
    private val profileApplier = ProfileApplier(application, userProfiles)

    init {
        viewModelScope.launch { userProfiles.ensureSeeded() }
    }

    /** Manual-override training points for curve wizard (G2R-F13, newest first). */
    val overridePoints: StateFlow<List<OverridePoint>> = appModule.overridePointStore.points()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Saved profiles for Profiles screen (G2R-F15, built-ins seeded first). */
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

    fun update(transform: (AabSettings) -> AabSettings) {
        viewModelScope.launch { app.settingsDataStore.updateData(transform) }
    }

    /** Reset parameters to task570 defaults, preserving runtime/identity + global prefs (G2R-F9, G2-F8). */
    fun resetDefaults() {
        viewModelScope.launch {
            val updated = app.settingsDataStore.updateData { current ->
                AabSettings(
                    serviceEnabled = current.serviceEnabled,
                    contextOverride = current.contextOverride,
                    detectOverrides = current.detectOverrides,
                    debugLevel = current.debugLevel,
                    panicSensitivity = current.panicSensitivity,
                )
            }
            if (updated.serviceEnabled) AutoBrightnessRuntime.reapply(app)
        }
    }

    /** Apply a saved profile (D-157 U3), same path as external receiver's LOAD_PROFILE. */
    fun applyProfile(name: String) {
        viewModelScope.launch { profileApplier.applyProfile(name) }
    }

    fun replaceAll(newSettings: AabSettings) {
        viewModelScope.launch {
            // Import is manual load: drop baseline snapshot (D-170), latch context lock (G2R-F30).
            DataStoreContextBaselineStore(app.contextBaselineDataStore).clear()
            val updated = app.settingsDataStore.updateData { current ->
                // Preserve service flag, DetectOverrides (G2-F8), debugLevel (G2R-F9). Neither belongs to imported profile.
                newSettings.validate().copy(
                    serviceEnabled = current.serviceEnabled,
                    detectOverrides = current.detectOverrides,
                    debugLevel = current.debugLevel,
                    panicSensitivity = current.panicSensitivity,
                    contextOverride = true,
                    // No per-capability preview on import: keep secure fields unchanged (visible preview on profile apply).
                    dimmingEnabled = current.dimmingEnabled,
                    nightLightEnabled = current.nightLightEnabled,
                    nightLightTemperature = current.nightLightTemperature,
                    nightLightCircadianEnabled = current.nightLightCircadianEnabled,
                    daltonizerMode = current.daltonizerMode,
                    inversionEnabled = current.inversionEnabled,
                    alwaysOnDisplayEnabled = current.alwaysOnDisplayEnabled,
                    stayAwakeChargingEnabled = current.stayAwakeChargingEnabled,
                    hdrForceSdrEnabled = current.hdrForceSdrEnabled,
                )
            }
            if (updated.serviceEnabled) AutoBrightnessRuntime.reapply(app)
        }
    }

    fun saveCurrentAs(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            userProfiles.save(trimmed, app.settingsDataStore.data.first())
        }
    }

    /** Register imported profile to catalog (G2R-F44). Distinct from [replaceAll] (applies to live settings). */
    fun saveImportedProfile(name: String, settings: AabSettings) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { userProfiles.save(trimmed, settings) }
    }

    fun deleteProfile(name: String) {
        viewModelScope.launch { userProfiles.delete(name) }
    }

    fun restoreFactoryProfiles() {
        viewModelScope.launch { userProfiles.restoreFactory() }
    }

    /** Clear manual context lock (D-157 U3), shared with receiver's CONTEXTS_RESUME. */
    fun resumeContextAutomation() {
        viewModelScope.launch { profileApplier.resumeContextAutomation() }
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

/** Min MaxBright needed by current curve (ceil of brightness at Zone 2 End); below it form3A < 0 (D-169). */
fun AabSettings.minRequiredMaxBrightness(): Int =
    ceil(
        BrightnessFormulae.zone2EndBrightness(
            form1A = form1A.toDouble(),
            form2B = form2B.toDouble(),
            form2C = form2C.toDouble(),
            zone1End = zone1End.toDouble(),
            zone2End = zone2End.toDouble(),
        ),
    ).toInt()

/** Result of [raiseMaxBrightnessForCurve]: the (possibly) adjusted settings + the new value if raised. */
data class MaxBrightnessFix(val settings: AabSettings, val raisedTo: Int?)

/** Tasker `_SaveButtonMisc` A5–A11 (D-169): raise MaxBright if curve needs headroom, never lower.
 * If MaxBright=255 or form3A≥0, no change. */
fun AabSettings.raiseMaxBrightnessForCurve(): MaxBrightnessFix {
    if (maxBrightness >= 255) return MaxBrightnessFix(this, null) // A5 gate
    if (derivedCoefficients().form3A >= 0.0) return MaxBrightnessFix(this, null) // A7
    val minReq = minRequiredMaxBrightness() // A8
    if (minReq <= maxBrightness) return MaxBrightnessFix(this, null) // never lower
    return MaxBrightnessFix(copy(maxBrightness = minReq), minReq) // A9
}
