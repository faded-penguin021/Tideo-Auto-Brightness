package com.tideo.autobrightness.app.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tideo.autobrightness.app.AppModule
import com.tideo.autobrightness.app.runtime.AutoBrightnessRuntime
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.FieldError
import com.tideo.autobrightness.app.settings.SettingsValidator
import com.tideo.autobrightness.app.storage.settingsDataStore
import com.tideo.autobrightness.platform.privilege.PrivilegeManager
import com.tideo.autobrightness.platform.privilege.Tier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Per-screen draft editor backing the S12.5b parameter screens (Curve & Brightness, Misc, Reactivity,
 * Animation & Dimming, Dynamic Scale). This ports Tasker AAB's **temporary-preview → Apply** model
 * (G2-F1): edits mutate a local [draft] only, the screen's graph previews the draft live, and the
 * committed/active value stays available for the `[brackets]` indicator until **Apply** commits
 * draft → DataStore and forces an immediate pipeline re-evaluate (G2-F16). Back/Discard throws the
 * draft away (the VM is NavBackStackEntry-scoped, so leaving the screen discards automatically).
 *
 * Drafts are per-screen: each destination resolves its own instance via `viewModel()`, so screens do
 * not share a draft. The [epoch] counter is bumped on every (re)seed so seed-once text fields rebind
 * to the fresh draft value rather than re-seeding mid-keystroke (G2-F7).
 */
class DraftSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val privilegeManager: PrivilegeManager = AppModule(application).privilegeManager

    /** The committed/active settings (DataStore source of truth) shown in `[brackets]`. */
    val committed: StateFlow<AabSettings> = app.settingsDataStore.data
        .stateIn(viewModelScope, SharingStarted.Eagerly, AabSettings())

    private val _draft = MutableStateFlow(AabSettings())
    /** The live, editable draft. Only this screen's edits mutate it (preview source). */
    val draft: StateFlow<AabSettings> = _draft.asStateFlow()

    private val _epoch = MutableStateFlow(0)
    /** Draft-epoch counter — bumped on seed/discard so seed-once fields rebind (G2-F7). */
    val epoch: StateFlow<Int> = _epoch.asStateFlow()

    private var seeded = false

    init {
        viewModelScope.launch {
            // Seed the draft once from the first committed snapshot; thereafter only re-sync the
            // runtime/identity fields the parameter screens never edit, so [dirty] reflects only
            // this screen's edits even if the service is toggled elsewhere while editing.
            app.settingsDataStore.data.collect { c ->
                if (!seeded) {
                    _draft.value = c
                    seeded = true
                    _epoch.update { it + 1 }
                } else {
                    _draft.update {
                        it.copy(
                            serviceEnabled = c.serviceEnabled,
                            contextOverride = c.contextOverride,
                            schemaVersion = c.schemaVersion,
                            setupTitle = c.setupTitle,
                        )
                    }
                }
            }
        }
    }

    /** True when the draft differs from the committed settings (enables Apply/Discard + brackets). */
    val dirty: StateFlow<Boolean> = combine(_draft, committed) { d, c -> d != c }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** task583/707 advisory errors recomputed on the DRAFT so the preview reddens live. */
    val errors: StateFlow<List<FieldError>> = _draft
        .map { SettingsValidator.validate(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tier: StateFlow<Tier> = privilegeManager.tierFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Tier.NONE)

    fun refreshTier() = privilegeManager.refresh()

    /** Edit the draft only — nothing persists until [apply] (G2-F1 temporary preview). */
    fun edit(transform: (AabSettings) -> AabSettings) = _draft.update(transform)

    /**
     * Commit draft → DataStore and force an immediate pipeline re-evaluate (G2-F1/F16). The
     * service/identity fields are preserved from the live committed value (never edited here), so an
     * Apply cannot flip the master switch or the context lock.
     */
    fun apply() {
        val toCommit = _draft.value
        viewModelScope.launch {
            val committedNow = app.settingsDataStore.updateData { current ->
                toCommit.copy(
                    serviceEnabled = current.serviceEnabled,
                    contextOverride = current.contextOverride,
                )
            }
            // UNLIMITED control event — takes effect even with no new sensor reading (G2-F16). Only
            // when the master switch is on, so an Apply while disabled does not spin up the service.
            if (committedNow.serviceEnabled) AutoBrightnessRuntime.reapply(app)
        }
    }

    /** Revert the draft to the committed values (Discard, or dirty-back confirmation). */
    fun discard() {
        _draft.value = committed.value
        _epoch.update { it + 1 }
    }
}
