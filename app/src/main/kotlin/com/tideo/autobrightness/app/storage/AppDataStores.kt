package com.tideo.autobrightness.app.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import androidx.datastore.preferences.preferencesDataStore
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.AabSettingsSerializer
import com.tideo.autobrightness.app.settings.ContextOverrideConfig
import com.tideo.autobrightness.app.settings.ContextRulesSerializer
import com.tideo.autobrightness.app.settings.OverridePoints
import com.tideo.autobrightness.app.settings.OverridePointsSerializer
import com.tideo.autobrightness.app.settings.SavedProfiles
import com.tideo.autobrightness.app.settings.SavedProfilesSerializer

val Context.settingsDataStore: DataStore<AabSettings> by dataStore(
    fileName = "aab_settings.json",
    serializer = AabSettingsSerializer,
)
val Context.serviceHealthDataStore by preferencesDataStore(name = "service_health")

// Context-override rules (S10): the rebuild's store for the rule set (Tasker contexts.json + caches).
val Context.contextRulesDataStore: DataStore<ContextOverrideConfig> by dataStore(
    fileName = "aab_context_rules.json",
    serializer = ContextRulesSerializer,
)

// Recorded manual-override training points (%AAB_Overrides): captured at runtime by the pipeline so
// the curve wizard + curve overlay have real input (G2R-F13/F14; closes the D-044c capture gap).
val Context.overridePointsDataStore: DataStore<OverridePoints> by dataStore(
    fileName = "aab_override_points.json",
    serializer = OverridePointsSerializer,
)

// User-editable named profiles (S12.6d, G2R-F15): the five DefaultProfiles seeded once, then
// overwritable, plus any "Save current as…" entries. AppProfileCatalog reads this so context rules
// can target user profiles too (closes the D-042c unknown-profile gap).
val Context.userProfilesDataStore: DataStore<SavedProfiles> by dataStore(
    fileName = "aab_user_profiles.json",
    serializer = SavedProfilesSerializer,
)
