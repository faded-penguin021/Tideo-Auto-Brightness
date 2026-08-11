package com.tideo.autobrightness.app.settings

import android.content.Context
import com.tideo.autobrightness.app.runtime.AutoBrightnessRuntime
import com.tideo.autobrightness.app.runtime.LiveRuntimeState
import com.tideo.autobrightness.app.storage.contextBaselineDataStore
import com.tideo.autobrightness.app.storage.settingsDataStore

// D-157: VM-free profile/context logic shared by UI and ControlReceiver.
class ProfileApplier(
    context: Context,
    private val userProfiles: UserProfileStore,
) {
    private val appContext = context.applicationContext
    // D-170: manual loads clear pre-override baseline snapshot (task626).
    private val baselineStore = DataStoreContextBaselineStore(appContext.contextBaselineDataStore)

    // Apply a saved profile; preserve service/detectOverrides/debugLevel. Latch manual context lock (G2R-F30, D-014).
    suspend fun applyProfile(name: String) {
        val profile = (userProfiles.get(name) ?: DefaultProfiles.all[name] ?: return).validate()
        baselineStore.clear()
        // DA-018: persist loaded profile as %AAB_ProfileUser (fallback for Resume/no-match).
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
