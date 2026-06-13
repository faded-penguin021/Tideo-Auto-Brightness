package com.tideo.autobrightness.app.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import androidx.datastore.preferences.preferencesDataStore
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.AabSettingsSerializer
import com.tideo.autobrightness.app.settings.ContextOverrideConfig
import com.tideo.autobrightness.app.settings.ContextRulesSerializer

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
