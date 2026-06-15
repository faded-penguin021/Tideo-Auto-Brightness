package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.ExperimentDateLocation
import com.tideo.autobrightness.app.settings.ExperimentPrefsStore
import com.tideo.autobrightness.domain.circadian.SolarCalculator
import com.tideo.autobrightness.platform.context.LocationReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.SimpleTimeZone
import java.util.TimeZone

/**
 * The real circadian ramp windows (seconds-of-day) for the dynamic-scale computation — morning/evening
 * transition bounds + sunlight duration + polar flag, the fields task90 Block #2 reads.
 *
 * **Frame:** UTC seconds-of-day — `SolarCalculator.buildScheduleWindows` derives them as
 * `riseEpochSec % 86400` etc., and the pipeline's `now` is `(currentTimeMillis/1000) % 86400`, also
 * UTC. The two share a frame, so the ramp tracks the real sun regardless of the device's clock label.
 */
data class CircadianWindows(
    val morningStart: Double,
    val morningEnd: Double,
    val eveningStart: Double,
    val eveningEnd: Double,
    val sunlightDurationMinutes: Double,
    val isPolar: Boolean,
)

/**
 * Supplies live [CircadianWindows] to the pipeline (G2R-F73). Until now the pipeline fed
 * `TimeContext`'s **defaults** (a fixed 6–8am / 18–20pm *UTC* morning/evening) to the dynamic-scale
 * engine — never the real sunrise — so for any non-UTC device the morning ramp was hours off (e.g. a
 * UTC+1 user saw `%AAB_ScaleDynamic` < 1 at 07:13 local, because "morning" was pinned to 06–08 UTC =
 * 07–09 local). This wires the already-fenced, golden-tested [SolarCalculator] into the runtime.
 *
 * Source of truth: the F39 fixed date/location override when set, else today + last-known location.
 * Returns `null` when no location is known → the pipeline keeps the old default windows (degrade, no
 * crash). Solar is recomputed only when the day/location/transition-factor actually change.
 *
 * domain/ stays fenced: this only *calls* `SolarCalculator.compute`/`buildScheduleWindows`.
 */
class CircadianWindowProvider(
    scope: CoroutineScope,
    experimentStore: ExperimentPrefsStore,
    private val location: LocationReader,
    private val clock: () -> Long = System::currentTimeMillis,
    private val tzOffsetHours: () -> Double = {
        TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3_600_000.0
    },
) {
    @Volatile private var override: ExperimentDateLocation = ExperimentDateLocation()
    @Volatile private var cacheKey: String? = null
    @Volatile private var cached: CircadianWindows? = null

    init {
        // F39 override drives the preview; invalidate the cache when it changes.
        scope.launch { experimentStore.dateLocation.collect { override = it; cacheKey = null } }
    }

    /** Windows for the active location/date at [transitionFactor], or null when no location is known. */
    fun current(transitionFactor: Double): CircadianWindows? {
        val ov = override
        val tz = tzOffsetHours()
        val lat: Double
        val lon: Double
        val dateEpochSec: Long
        if (!ov.isUnset && ov.latitude != null && ov.longitude != null) {
            lat = ov.latitude!!
            lon = ov.longitude!!
            dateEpochSec = parseDateEpochSec(ov.date, tz) ?: (clock() / 1000L)
        } else {
            val loc = runCatching { location.lastKnownLocation() }.getOrNull() ?: return null
            lat = loc.latitude
            lon = loc.longitude
            dateEpochSec = clock() / 1000L
        }

        val key = "${dateEpochSec / 86_400L}|${round4(lat)}|${round4(lon)}|$transitionFactor|$tz"
        if (key == cacheKey) return cached
        val windows = compute(lat, lon, dateEpochSec, tz, transitionFactor)
        cacheKey = key
        cached = windows
        return windows
    }

    companion object {
        /** Pure: real solar windows for a location/date via the fenced domain math (JVM-testable). */
        fun compute(
            lat: Double,
            lon: Double,
            dateEpochSec: Long,
            tzOffsetHours: Double,
            transitionFactor: Double,
        ): CircadianWindows {
            val solar = SolarCalculator.compute(lat, lon, dateEpochSec, tzOffsetHours)
            val w = SolarCalculator.buildScheduleWindows(solar, transitionFactor)
            return CircadianWindows(
                morningStart = w.morningStart,
                morningEnd = w.morningEnd,
                eveningStart = w.eveningStart,
                eveningEnd = w.eveningEnd,
                sunlightDurationMinutes = solar.sunlightDurationMinutes.toDouble(),
                isPolar = solar.sunStatus == "polar",
            )
        }

        private fun round4(v: Double): Long = Math.round(v * 10_000.0)

        /** Parse a `YYYY-MM-DD` fixed-date override to noon-of-that-local-day epoch seconds. */
        private fun parseDateEpochSec(date: String?, tzOffsetHours: Double): Long? {
            if (date == null) return null
            val tzMs = Math.round(tzOffsetHours * 3_600_000.0).toInt()
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = SimpleTimeZone(tzMs, "AAB")
            }
            return runCatching { fmt.parse(date)?.time?.let { it / 1000L + 12 * 3600L } }.getOrNull()
        }
    }
}
