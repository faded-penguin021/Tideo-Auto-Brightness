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

/**
 * One persisted manual-override training point: the (lux, brightness) pair the user implicitly taught
 * by grabbing the system slider. The brightness is the de-compressed "ideal base" brightness produced
 * by [com.tideo.autobrightness.domain.brightness.OverrideRules.recordOverridePoint] (task561), so the
 * curve wizard fits against the same value Tasker stores in `%AAB_Overrides<N>`.
 */
@Serializable
data class OverridePointRecord(val lux: Double, val brightness: Double)

/** The persisted override-point set (`%AAB_Overrides`, capped at [MAX_POINTS], newest first). */
@Serializable
data class OverridePoints(val points: List<OverridePointRecord> = emptyList()) {
    companion object {
        /** Tasker task561 caps %AAB_Overrides at 50 entries. */
        const val MAX_POINTS = 50
    }
}

/**
 * DataStore serializer for the recorded override points. Survives service/process restarts so the
 * Tools curve-suggestion wizard and the brightness-curve overlay (G2R-F13/F14) have real input,
 * instead of always starting from an empty set (the D-044c gap).
 */
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

/**
 * Persistence + capture for the manual-override training points. The pipeline records a point each
 * time it pauses on a genuine manual override (newest-first, capped at [OverridePoints.MAX_POINTS],
 * mirroring task561); the wizard / curve overlay read [points].
 */
class OverridePointStore(private val dataStore: DataStore<OverridePoints>) {

    /** The recorded points as domain [OverridePoint]s (newest first), for the wizard + chart overlay. */
    fun points(): Flow<List<OverridePoint>> = dataStore.data.map { stored ->
        stored.points.map { OverridePoint(lux = it.lux, brightness = it.brightness) }
    }

    /** Append a captured override point (newest first), dropping the oldest beyond the cap. */
    suspend fun record(lux: Double, brightness: Double) {
        dataStore.updateData { current ->
            val updated = listOf(OverridePointRecord(lux, brightness)) + current.points
            OverridePoints(updated.take(OverridePoints.MAX_POINTS))
        }
    }

    /**
     * Delete the recorded point matching [point] (tap-to-delete on the curve chart, F36). Matches on
     * the (lux, brightness) pair and removes only the first such record so duplicates are not all wiped.
     */
    suspend fun delete(point: OverridePoint) {
        dataStore.updateData { current ->
            val idx = current.points.indexOfFirst {
                it.lux == point.lux && it.brightness == point.brightness
            }
            if (idx < 0) current else OverridePoints(current.points.filterIndexed { i, _ -> i != idx })
        }
    }

    /** Clear all recorded points (Tools "reset overrides"). */
    suspend fun clear() {
        dataStore.updateData { OverridePoints() }
    }
}
