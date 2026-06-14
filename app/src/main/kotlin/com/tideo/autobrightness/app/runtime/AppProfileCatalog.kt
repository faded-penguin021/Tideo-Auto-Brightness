package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.DefaultProfiles
import com.tideo.autobrightness.app.settings.UserProfileStore

/**
 * Resolves a context rule's target profile NAME to its [AabSettings] parameter set. S12.6d backs this
 * with the user-editable [UserProfileStore] (the five built-in [DefaultProfiles] seeded once, plus any
 * user "Save current as…" entries), so context rules can target user profiles too (closes the D-042c
 * "unknown rule.profile → null" gap). The built-in map is a fallback for the never-seeded edge.
 */
class AppProfileCatalog(private val store: UserProfileStore) : ProfileCatalog {
    override suspend fun profile(name: String): AabSettings? =
        store.get(name) ?: DefaultProfiles.all[name]

    override suspend fun names(): Set<String> =
        (store.names() + DefaultProfiles.all.keys).toSet()
}
