package com.tideo.autobrightness.app.settings

import android.content.Context
import com.tideo.autobrightness.app.runtime.AutoBrightnessRuntime
import com.tideo.autobrightness.app.runtime.LiveRuntimeState
import com.tideo.autobrightness.app.storage.settingsDataStore

/**
 * VM-free profile-load / context-resume logic (D-157 U3): the bodies of
 * `SettingsViewModel.applyProfile` / `resumeContextAutomation` moved here verbatim so BOTH the
 * Profiles UI (via the ViewModel, which delegates) and the external `ControlReceiver`
 * (`LOAD_PROFILE` / `CONTEXTS_RESUME`) drive the exact same, already-hardened path — no logic is
 * duplicated at the receiver. Pure suspend functions with no Android-lifecycle coupling; the caller
 * owns the coroutine scope (the ViewModel its `viewModelScope`, the receiver its `goAsync`).
 *
 * `SettingsViewModelTest` passes UNMODIFIED against the delegating ViewModel — the equivalence check.
 */
class ProfileApplier(
    context: Context,
    private val userProfiles: UserProfileStore,
) {
    private val appContext = context.applicationContext

    /**
     * Apply a saved named profile (the [UserProfileStore] set; built-ins are seeded into it). The live
     * service-enabled flag plus the GLOBAL preferences `detectOverrides` and `debugLevel` are preserved:
     * neither is part of the task626 profile snapshot, so loading a profile must not turn manual-override
     * detection off (G2-F8) nor change the selected debug category (G2R-F9).
     *
     * A manual profile load also latches the **manual context lock** `%AAB_ContextOverride=true`
     * (G2R-F30, D-014/D-038a) so the context watchers stop overriding the user's deliberate choice; the
     * Profiles screen surfaces a "Resume" affordance ([resumeContextAutomation]) to clear it. An
     * unknown name is a no-op (external callers may send an arbitrary string).
     */
    suspend fun applyProfile(name: String) {
        val profile = userProfiles.get(name) ?: DefaultProfiles.all[name] ?: return
        val updated = appContext.settingsDataStore.updateData { current ->
            profile.copy(
                serviceEnabled = current.serviceEnabled,
                detectOverrides = current.detectOverrides,
                debugLevel = current.debugLevel,
                panicSensitivity = current.panicSensitivity,
                contextOverride = true, // latch the manual context lock (G2R-F30)
            )
        }
        // Surface the loaded profile on the Dashboard (LiveRuntimeState, in-memory bridge).
        LiveRuntimeState.setActiveProfile(name)
        // task592/626 apply re-runs Advanced Auto Brightness so the new curve takes effect
        // immediately, not at the next sensor tick (G2-F16).
        if (updated.serviceEnabled) AutoBrightnessRuntime.reapply(appContext)
    }

    /**
     * Clear the manual context lock latched by [applyProfile] and re-evaluate so the context watchers
     * resume overriding (G2R-F30). Mirrors Tasker clearing %AAB_ContextOverride.
     */
    suspend fun resumeContextAutomation() {
        val updated = appContext.settingsDataStore.updateData { it.copy(contextOverride = false) }
        if (updated.serviceEnabled) AutoBrightnessRuntime.reapply(appContext)
    }
}
