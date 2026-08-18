package com.tideo.autobrightness.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import com.tideo.autobrightness.domain.wizard.OverridePoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/** One manual-override training point: (lux, brightness) pair user teaches via slider. Brightness is
 * de-compressed ideal base (task561), matching value Tasker stores in %AAB_Overrides<N>. */
@Serializable
data class OverridePointRecord(val lux: Double, val brightness: Double)

/** The persisted override-point set (`%AAB_Overrides`, capped at [MAX_POINTS], newest first). */
@Serializable
data class OverridePoints(val points: List<OverridePointRecord> = emptyList()) {
    companion object {
        const val MAX_POINTS = 50  // task561 cap
        const val SCHEMA_VERSION = 1  // v1=initial; bump on breaking change (S12.9c #5)
    }
}

/** DataStore serializer for override points. Survives process restarts so wizard/overlay have real input (D-044(c) gap). */
object OverridePointsSerializer : Serializer<OverridePoints> {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val defaultValue: OverridePoints = OverridePoints()

    override suspend fun readFrom(input: InputStream): OverridePoints =
        runCatching {
            json.decodeFromString(OverridePoints.serializer(), input.readBytes().decodeToString())
        }.getOrDefault(defaultValue)

    override suspend fun writeTo(t: OverridePoints, output: OutputStream) {
        output.write(json.encodeToString(OverridePoints.serializer(), t).encodeToByteArray())
    }
}

/** Persistence for manual-override training points: records on pause, newest-first, capped at MAX_POINTS. */
class OverridePointStore(private val dataStore: DataStore<OverridePoints>) {

    /** Recorded points as domain OverridePoints (newest first), for wizard + chart overlay. */
    fun points(): Flow<List<OverridePoint>> = dataStore.data.map { stored ->
        stored.points.map { OverridePoint(lux = it.lux, brightness = it.brightness) }
    }

    suspend fun record(lux: Double, brightness: Double) {
        dataStore.updateData { current ->
            val updated = listOf(OverridePointRecord(lux, brightness)) + current.points
            OverridePoints(updated.take(OverridePoints.MAX_POINTS))
        }
    }

    /** Delete matching point (tap-to-delete on chart, F36). Matches (lux, brightness) pair; removes first only. */
    suspend fun delete(point: OverridePoint) {
        dataStore.updateData { current ->
            val idx = current.points.indexOfFirst {
                it.lux == point.lux && it.brightness == point.brightness
            }
            if (idx < 0) current else OverridePoints(current.points.filterIndexed { i, _ -> i != idx })
        }
    }

    suspend fun clear() {
        dataStore.updateData { OverridePoints() }
    }
}
