package com.tideo.autobrightness.app.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import androidx.datastore.preferences.preferencesDataStore
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.AabSettingsSerializer
import com.tideo.autobrightness.app.settings.ContextBaseline
import com.tideo.autobrightness.app.settings.ContextBaselineSerializer
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

// Circadian Experiment override (S12.7h / G2R-F39). Unset = live data.
val Context.experimentPrefsDataStore by preferencesDataStore(name = "experiment_prefs")

// D-157: opt-in gate for external intent-control surface; own store (not AabSettings field).
val Context.controlPrefsDataStore by preferencesDataStore(name = "control_prefs")

// Power-draw dataset (S14); persisted across restarts, overwritten on recalibration.
val Context.powerDrawDataStore by preferencesDataStore(name = "power_draw")

// D-170: pre-override baseline snapshot; context-rule loads write through to settingsDataStore.
val Context.contextBaselineDataStore: DataStore<ContextBaseline> by dataStore(
    fileName = "aab_context_baseline.json",
    serializer = ContextBaselineSerializer,
)

// Context-override rules (S10): rule set store (Tasker contexts.json + caches).
val Context.contextRulesDataStore: DataStore<ContextOverrideConfig> by dataStore(
    fileName = "aab_context_rules.json",
    serializer = ContextRulesSerializer,
)

// Manual-override training points (D-044(c)); wizard/overlay input. Captured at runtime.
val Context.overridePointsDataStore: DataStore<OverridePoints> by dataStore(
    fileName = "aab_override_points.json",
    serializer = OverridePointsSerializer,
)

// User-editable named profiles (S12.6d, D-042(c)); DefaultProfiles seeded once, then overwritable.
val Context.userProfilesDataStore: DataStore<SavedProfiles> by dataStore(
    fileName = "aab_user_profiles.json",
    serializer = SavedProfilesSerializer,
)
