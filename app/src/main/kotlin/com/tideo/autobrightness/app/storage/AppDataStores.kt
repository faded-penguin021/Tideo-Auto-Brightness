package com.tideo.autobrightness.app.storage

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

val Context.settingsDataStore by preferencesDataStore(name = "auto_brightness_settings")
val Context.serviceHealthDataStore by preferencesDataStore(name = "service_health")
