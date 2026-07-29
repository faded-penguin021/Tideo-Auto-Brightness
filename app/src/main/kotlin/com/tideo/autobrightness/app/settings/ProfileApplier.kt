package com.tideo.autobrightness.app.settings

import android.content.Context
import com.tideo.autobrightness.app.runtime.AutoBrightnessRuntime
import com.tideo.autobrightness.app.runtime.LiveRuntimeState
import com.tideo.autobrightness.app.storage.contextBaselineDataStore
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
    // D-170: manual loads make the CURRENT settings authoritative — the pre-override baseline
    // snapshot (task626 _ContextResume) is stale the moment the user picks a profile by hand, so
    // both entry points below clear it (Tasker re-snapshots the live var set at Resume).
    private val baselineStore = DataStoreContextBaselineStore(appContext.contextBaselineDataStore)

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
        val profile = (userProfiles.get(name) ?: DefaultProfiles.all[name] ?: return).validate()
        // The manual choice becomes the new baseline — drop any pre-override snapshot (D-170).
        // Clear BEFORE the settings write: a death in between means the load simply didn't take
        // (benign), whereas write-then-clear could leave a stale snapshot that a later revert
        // would restore over the user's deliberate choice.
        baselineStore.clear()
        // DA-018: this profile is now `%AAB_ProfileUser` — the last manually-loaded profile, i.e. the
        // target a later "Resume context automation" (or any no-match) reverts to. Persist the NAME so
        // the resolver's fallback + the active-profile label match the settings the pipeline runs,
        // instead of collapsing to the hardcoded "Default".
        baselineStore.setUserProfileName(name)
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
     *
     * DA-018: this drives the Tasker `_ContextResume` flow (→ evaluate contexts → Set Initial
     * Brightness). It routes through [AutoBrightnessRuntime.resumeContext], NOT plain `reapply`: reapply
     * only republishes the unlocked settings (`ContextEngine.reevaluate`), so a currently-matching rule
     * never applied and a no-match never re-labelled — the active-profile indicator flipped to "Default"
     * while the settings screens stayed on the loaded profile (owner report). The dedicated verb runs a
     * genuine `evaluate(RESUME)` on the engine so the rule applies now, or the store reverts to
     * `%AAB_ProfileUser`, before Set Initial Brightness runs. `%AAB_ProfileUser` is left as the last
     * manually-loaded profile — Resume reverts TO it, so it must not be overwritten here.
     */
    suspend fun resumeContextAutomation() {
        // Resume = the current settings become the baseline the next override snapshots (task626
        // re-snapshot semantics, D-170); clear any residual pre-lock snapshot first.
        baselineStore.clear()
        val updated = appContext.settingsDataStore.updateData { it.copy(contextOverride = false) }
        if (updated.serviceEnabled) AutoBrightnessRuntime.resumeContext(appContext)
    }
}
