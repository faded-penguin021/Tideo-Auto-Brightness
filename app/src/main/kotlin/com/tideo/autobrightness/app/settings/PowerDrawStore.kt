package com.tideo.autobrightness.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tideo.autobrightness.domain.power.PowerDrawSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** On-disk row for the persisted dataset (the domain [PowerDrawSample] stays serialization-free). */
@Serializable
private data class PowerSampleDto(val brightness: Int, val currentMa: Double, val powerW: Double)

/**
 * Persists the measured task524 `_CalibratePowerDraw` dataset (Tasker `%data` / `%AAB_HTML_Graph8`).
 * The Tools `PowerDrawChart` renders [samples]; a calibration run [save]s a fresh set (overwrite).
 */
class PowerDrawStore(private val dataStore: DataStore<Preferences>) {
    val samples: Flow<List<PowerDrawSample>> = dataStore.data.map { prefs ->
        prefs[DATA]?.let(::decode) ?: emptyList()
    }

    /** True once a calibration has been recorded (the Tasker "Data Found" entry condition). */
    val hasData: Flow<Boolean> = dataStore.data.map { (it[DATA]?.length ?: 0) > 2 }

    suspend fun save(samples: List<PowerDrawSample>) {
        dataStore.edit { it[DATA] = encode(samples) }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(DATA) }
    }

    private fun encode(samples: List<PowerDrawSample>): String =
        JSON.encodeToString(SERIALIZER, samples.map { PowerSampleDto(it.brightness, it.currentMa, it.powerW) })

    private fun decode(raw: String): List<PowerDrawSample> = runCatching {
        JSON.decodeFromString(SERIALIZER, raw).map { PowerDrawSample(it.brightness, it.currentMa, it.powerW) }
    }.getOrDefault(emptyList())

    private companion object {
        val DATA = stringPreferencesKey("power_draw_data")
        val JSON = Json { ignoreUnknownKeys = true }
        val SERIALIZER = ListSerializer(PowerSampleDto.serializer())
    }
}
