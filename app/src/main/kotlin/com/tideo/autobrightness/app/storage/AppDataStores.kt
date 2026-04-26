package com.tideo.autobrightness.app.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import androidx.datastore.preferences.preferencesDataStore
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.AabSettingsSerializer

val Context.settingsDataStore: DataStore<AabSettings> by dataStore(
    fileName = "aab_settings.json",
    serializer = AabSettingsSerializer,
)
val Context.serviceHealthDataStore by preferencesDataStore(name = "service_health")
