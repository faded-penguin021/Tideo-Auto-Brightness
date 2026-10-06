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
    suspend fun today(epochMs: Long, liveFix: Pair<Double, Double>? = null): Pair<Long, Long>? {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
        val offsetSecs = cal.timeZone.getOffset(epochMs) / 1000L
        val (lat, lon) = firstValid(
            { experimentPrefs.dateLocation.first().let { p -> p.latitude?.let { la -> p.longitude?.let { la to it } } } },
            { liveFix },
            { location.lastKnownLocation()?.let { it.latitude to it.longitude } },
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
    }
}
