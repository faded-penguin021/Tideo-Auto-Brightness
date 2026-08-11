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
        LiveRuntimeState.setActiveProfile(name)
        if (updated.serviceEnabled) AutoBrightnessRuntime.reapply(appContext)
    }

    // DA-018: clear context lock and re-evaluate (G2R-F30). Route through resumeContext(), not reapply().
    suspend fun resumeContextAutomation() {
        baselineStore.clear()
        val updated = appContext.settingsDataStore.updateData { it.copy(contextOverride = false) }
        if (updated.serviceEnabled) AutoBrightnessRuntime.resumeContext(appContext)
    }
}
