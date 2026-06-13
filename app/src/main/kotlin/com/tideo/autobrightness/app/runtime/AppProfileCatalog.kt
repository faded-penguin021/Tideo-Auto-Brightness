package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.DefaultProfiles

/**
 * Resolves a context rule's target profile NAME to its [AabSettings] parameter set. S10 backs this
 * with the five built-in profiles ([DefaultProfiles], task592); S12 (profile save/load) extends it
 * with user-named profiles. An unknown name resolves to null → the engine keeps the user baseline.
 */
object AppProfileCatalog : ProfileCatalog {
    override suspend fun profile(name: String): AabSettings? = DefaultProfiles.all[name]
    override suspend fun names(): Set<String> = DefaultProfiles.all.keys
}
