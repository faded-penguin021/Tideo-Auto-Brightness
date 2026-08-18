package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.DefaultProfiles
import com.tideo.autobrightness.app.settings.UserProfileStore

// S12.6d: resolve profile NAME to AabSettings (D-042(c)). UserProfileStore + DefaultProfiles fallback.
class AppProfileCatalog(private val store: UserProfileStore) : ProfileCatalog {
    override suspend fun profile(name: String): AabSettings? =
        store.get(name) ?: DefaultProfiles.all[name]

    override suspend fun names(): Set<String> =
        (store.names() + DefaultProfiles.all.keys).toSet()
}
