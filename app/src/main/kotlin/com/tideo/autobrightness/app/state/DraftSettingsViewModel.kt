package com.tideo.autobrightness.app.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tideo.autobrightness.app.AppModule
import com.tideo.autobrightness.app.runtime.AutoBrightnessRuntime
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.FieldError
import com.tideo.autobrightness.app.settings.Severity
import com.tideo.autobrightness.app.settings.SettingsValidator
import com.tideo.autobrightness.app.settings.validate
import com.tideo.autobrightness.app.storage.settingsDataStore
import com.tideo.autobrightness.domain.wizard.OverridePoint
import com.tideo.autobrightness.platform.privilege.PrivilegeManager
import com.tideo.autobrightness.platform.privilege.Tier
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Per-screen draft editor: temp-preview mode with Apply/Discard (G2-F1). Epoch counter rebinds fields (G2-F7).
class DraftSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val appModule = AppModule(application)
    private val privilegeManager: PrivilegeManager = appModule.privilegeManager

    val overridePoints: StateFlow<List<OverridePoint>> = appModule.overridePointStore.points()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val committed: StateFlow<AabSettings> = app.settingsDataStore.data
        .stateIn(viewModelScope, SharingStarted.Eagerly, AabSettings())

    private val _draft = MutableStateFlow(AabSettings())
    val draft: StateFlow<AabSettings> = _draft.asStateFlow()

    private val _epoch = MutableStateFlow(0)
    val epoch: StateFlow<Int> = _epoch.asStateFlow()

    private var seeded = false

    init {
        viewModelScope.launch {
            app.settingsDataStore.data.collect { c ->
                if (!seeded) {
                    // D-125: curve-suggestion preview applies to seed so values ride epoch 0→1.
                    val preview = CurveSuggestionPreview.consume()
                    _draft.value = preview?.invoke(c) ?: c
                    seeded = true
                    _epoch.update { it + 1 }
                } else {
                    _draft.update {
                        it.copy(
                            serviceEnabled = c.serviceEnabled,
                            contextOverride = c.contextOverride,
                            schemaVersion = c.schemaVersion,
                            setupTitle = c.setupTitle,
                            // GLOBAL fields: debugLevel (Live Debug scene, G2R-F9), panicSensitivity (D-116).
                            debugLevel = c.debugLevel,
                            panicSensitivity = c.panicSensitivity,
                        )
                    }
                }
            }
        }
    }

    val dirty: StateFlow<Boolean> = combine(_draft, committed) { d, c -> d != c }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val errors: StateFlow<List<FieldError>> = _draft
        .map { SettingsValidator.validate(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // True if CRITICAL error; Apply disabled (D-052). Advisory warnings never block.
    val hasCriticalError: StateFlow<Boolean> = errors
        .map { list -> list.any { it.severity == Severity.CRITICAL } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // D-169: emit when Apply auto-raises MaxBright to fit curve; one-shot buffered SharedFlow.
    private val _maxBrightnessRaised = MutableSharedFlow<Int>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val maxBrightnessRaised: SharedFlow<Int> = _maxBrightnessRaised.asSharedFlow()

    // DB-008: announce when Apply clamps dimming strength; one-shot buffered SharedFlow.
    private val _dimmingStrengthClamped = MutableSharedFlow<Int>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val dimmingStrengthClamped: SharedFlow<Int> = _dimmingStrengthClamped.asSharedFlow()

    val tier: StateFlow<Tier> = privilegeManager.tierFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Tier.NONE)

    fun refreshTier() = privilegeManager.refresh()

    fun edit(transform: (AabSettings) -> AabSettings) = _draft.update(transform)

    // DB-040: the draft this read-back last produced, so a user edit of a display field is
    // distinguishable from our own write. Main-thread only (the screen's LaunchedEffect).
    private var lastReadBack: AabSettings? = null

    /**
     * DB-039/DB-040: merge a Privileged Display device read-back into the draft, atomically.
     * Refused before the seed — until epoch 1 the draft is `AabSettings()` defaults, and merging
     * into those then having the seed overwrite them loses the read-back, while the reverse order
     * would replace the user's whole profile with defaults. The gate and the write are one
     * `update` so a concurrent user edit cannot be read-then-clobbered.
     */
    fun mergeDeviceReadBack(snapshot: DeviceDisplaySnapshot) {
        if (!seeded) return
        val committedNow = committed.value
        _draft.update { current ->
            readBackDraft(current, committedNow, lastReadBack, snapshot)
                ?.also { lastReadBack = it }
                ?: current
        }
    }

    fun deleteOverridePoint(point: OverridePoint) {
        viewModelScope.launch { appModule.overridePointStore.delete(point) }
    }

    // Commit draft → DataStore; service/identity fields preserved.
    fun apply(raiseMaxBrightForCurve: Boolean = false) {
        // D-085: clamp fields on commit (same as SettingsStore/import/export).
        // D-169: raise MaxBright if curve needs it (D-052 blocks on form errors).
        // Tasker force-fixes and flashes "adjusted to N" rather than blocking the save.
        val fix = if (raiseMaxBrightForCurve) _draft.value.raiseMaxBrightnessForCurve() else MaxBrightnessFix(_draft.value, null)
        val requestedStrength = fix.settings.dimmingStrength
        val toCommit = fix.settings.validate()
        // D-164: snap draft to validated copy so Apply is a fixed point; epoch rebinds fields.
        _draft.value = toCommit
        _epoch.update { it + 1 }
        if (fix.raisedTo != null) _maxBrightnessRaised.tryEmit(toCommit.maxBrightness)
        // DB-008: announce clamp only if value moved.
        if (toCommit.dimmingStrength < requestedStrength) {
            _dimmingStrengthClamped.tryEmit(toCommit.dimmingStrength)
        }
        viewModelScope.launch {
            val committedNow = app.settingsDataStore.updateData { current ->
                toCommit.copy(
                    serviceEnabled = current.serviceEnabled,
                    contextOverride = current.contextOverride,
                    debugLevel = current.debugLevel,
                    panicSensitivity = current.panicSensitivity,
                )
            }
            if (committedNow.serviceEnabled) AutoBrightnessRuntime.reapply(app)
        }
    }

    fun discard() {
        _draft.value = committed.value
        _epoch.update { it + 1 }
    }
}
