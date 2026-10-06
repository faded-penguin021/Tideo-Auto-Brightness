package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.ExperimentPrefsStore
import com.tideo.autobrightness.domain.circadian.SolarCalculator
import com.tideo.autobrightness.platform.context.LocationReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.Calendar

/** SUNRISE/SUNSET local seconds-of-day for the engine and the rule editor; location order is DD-038's. */
class ContextSolarTimes(
    private val experimentPrefs: ExperimentPrefsStore,
    private val location: LocationReader,
) {
    private class LastKnown(val latLon: Pair<Double, Double>?, val atMs: Long)

    @Volatile private var lastKnown: LastKnown? = null

    suspend fun today(epochMs: Long, liveFix: Pair<Double, Double>? = null): Pair<Long, Long>? {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
        val offsetSecs = cal.timeZone.getOffset(epochMs) / 1000L
        val (lat, lon) = firstValid(
            { experimentPrefs.dateLocation.first().let { p -> p.latitude?.let { la -> p.longitude?.let { la to it } } } },
            { liveFix },
            { lastKnownAt(epochMs) },
            {
                if (!experimentPrefs.geoIpEnabled.first()) null
                else experimentPrefs.readCachedSunLocation()?.let { it.latitude to it.longitude }
            },
        ) ?: return null
        return runCatching {
            val solar = SolarCalculator.compute(lat, lon, epochMs / 1000L, offsetSecs / 3600.0)
            Math.floorMod(solar.riseEpochSec + offsetSecs, 86_400L) to
                Math.floorMod(solar.setEpochSec + offsetSecs, 86_400L)
        }.getOrNull()
    }

    private fun lastKnownAt(nowMs: Long): Pair<Double, Double>? {
        lastKnown?.takeIf { nowMs - it.atMs in 0 until LAST_KNOWN_REUSE_MS }?.let { return it.latLon }
        val latLon = location.lastKnownLocation()?.let { it.latitude to it.longitude }
        lastKnown = LastKnown(latLon, nowMs)
        return latLon
    }

    private suspend fun firstValid(vararg candidates: suspend () -> Pair<Double, Double>?): Pair<Double, Double>? {
        for (candidate in candidates) {
            val loc = try {
                candidate()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (loc != null && isValid(loc.first, loc.second)) return loc
        }
        return null
    }

    private fun isValid(lat: Double, lon: Double): Boolean =
        lat.isFinite() && lat in -90.0..90.0 && lon.isFinite() && lon in -180.0..180.0 &&
            (lat != 0.0 || lon != 0.0)

    companion object {
        const val DEFAULT_SUNRISE = 21_600L // 06:00
        const val DEFAULT_SUNSET = 64_800L  // 18:00
        const val LAST_KNOWN_REUSE_MS = 10 * 60_000L
    }
}
