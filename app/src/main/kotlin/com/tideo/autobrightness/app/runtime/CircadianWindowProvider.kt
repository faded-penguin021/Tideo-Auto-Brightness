package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.CachedSunLocation
import com.tideo.autobrightness.app.settings.ExperimentDateLocation
import com.tideo.autobrightness.domain.circadian.SolarCalculator
import com.tideo.autobrightness.platform.context.LocationReader
import com.tideo.autobrightness.platform.context.LocationResult
import com.tideo.autobrightness.platform.context.LocationSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.SimpleTimeZone
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean

/** Real circadian windows (seconds-of-day) for the dynamic-scale computation. UTC frame, task90 Block #2. */
data class CircadianWindows(
    val morningStart: Double,
    val morningEnd: Double,
    val eveningStart: Double,
    val eveningEnd: Double,
    val sunlightDurationMinutes: Double,
    val isPolar: Boolean,
)

/** D-110: freshness of the location backing the live circadian modifier, for UI staleness hint. */
data class CircadianLocationStatus(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val resolvedForDay: Long? = null,
    val today: Long = 0L,
    val fixed: Boolean = false,
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null
    val ageDays: Long? get() = resolvedForDay?.let { (today - it).coerceAtLeast(0L) }
    val isStale: Boolean get() = !fixed && hasLocation && (ageDays ?: 0L) > 0L
}

/** Supplies live [CircadianWindows] to the pipeline (G2R-F73). Features F73, F39, F83, D-121: DST-aware tz,
 * independent date/location overrides, once-a-day location acquisition with geo-IP fallback (D-103 cache).
 * domain/ stays fenced: only calls `SolarCalculator.compute`/`buildScheduleWindows`. */
class CircadianWindowProvider(
    private val scope: CoroutineScope,
    overrideFlow: Flow<ExperimentDateLocation>,
    private val location: LocationReader,
    // F83: ipwho.is geo-IP fallback (HTTPS D-121)
    private val geoIpFallback: suspend () -> LocationSnapshot?,
    // D-103: load/save once-a-day location across process restarts
    private val loadCachedLocation: suspend () -> CachedSunLocation? = { null },
    private val persistLocation: suspend (latitude: Double, longitude: Double, day: Long) -> Unit =
        { _, _, _ -> },
    private val loadGeoIpAttemptDay: suspend () -> Long? = { null },
    private val persistGeoIpAttemptDay: suspend (day: Long) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
    // F73: offset at the TARGET instant (DST-aware)
    private val tzOffsetForDate: (dateEpochSec: Long) -> Double = { dateEpochSec ->
        TimeZone.getDefault().getOffset(dateEpochSec * 1000L) / 3_600_000.0
    },
) {
    // S12.9e volatile audit: cross three coroutines, single values with no compound invariant
    @Volatile private var override: ExperimentDateLocation = ExperimentDateLocation()
    @Volatile private var cacheKey: String? = null
    @Volatile private var cached: CircadianWindows? = null

    // F83: once-a-day acquired location (Android or geo-IP), keyed by day
    @Volatile private var resolvedLoc: LocationSnapshot? = null
    @Volatile private var resolvedDay: Long = Long.MIN_VALUE
    @Volatile private var attemptedDay: Long = Long.MIN_VALUE
    @Volatile private var acquisitionReady = false
    private val acquiring = AtomicBoolean(false)

    // D-110: fired when a location resolves after construction (cache seed or fresh acquire) so pipeline recomputes.
    // Settable post-construction; fires immediately if location already resolved.
    @Volatile private var _onWindowsRefreshed: () -> Unit = {}
    var onWindowsRefreshed: () -> Unit
        get() = _onWindowsRefreshed
        set(value) {
            _onWindowsRefreshed = value
            if (resolvedLoc != null || acquisitionReady) value()
        }

    /** D-110: location backing the live circadian modifier + freshness for UI staleness hint. Updated on each [current] call. */
    @Volatile var status: CircadianLocationStatus = CircadianLocationStatus()
        private set

    init {
        // F39: invalidate cache when override changes
        scope.launch {
            overrideFlow.collect {
                if (it == override) return@collect
                override = it
                cacheKey = null
                resolvedDay = Long.MIN_VALUE
            }
        }
        // D-103: seed from persisted location on cold start
        scope.launch {
            val cached = cancellableOrNull { loadCachedLocation() }
            val cachedSnapshot = cached?.let { LocationSnapshot(it.latitude, it.longitude) }
            if (resolvedLoc == null && cachedSnapshot?.hasValidCoordinates() == true) {
                resolvedLoc = cachedSnapshot
                resolvedDay = cached.day
                cacheKey = null
            }
            cancellableOrNull { loadGeoIpAttemptDay() }?.let { attemptedDay = it }
            acquisitionReady = true
            onWindowsRefreshed()
        }
    }

    /** Windows for active location/date at [transitionFactor], or null when no location is known. */
    fun current(transitionFactor: Double): CircadianWindows? {
        val ov = override
        val nowSec = clock() / 1000L

        // F39: fixed Date independent of fixed Location
        val dateEpochSec = ov.date?.let { parseDateEpochSec(it, tzOffsetForDate(nowSec)) } ?: nowSec
        val tz = tzOffsetForDate(dateEpochSec)
        val day = dateEpochSec / 86_400L

        val todayDay = nowSec / 86_400L
        val loc: LocationSnapshot = if (ov.latitude != null && ov.longitude != null) {
            // F83: fixed lat/lon, skip acquisition
            status = CircadianLocationStatus(ov.latitude, ov.longitude, resolvedForDay = day, today = todayDay, fixed = true)
            LocationSnapshot(ov.latitude!!, ov.longitude!!)
        } else {
            // F83: acquire once a day when needed
            if (acquisitionReady && (resolvedLoc == null || resolvedDay != day)) triggerAcquire(day, todayDay)
            // D-110: fall back to cached location when no fresh fix available
            val cachedLoc = resolvedLoc
            if (cachedLoc == null) {
                status = CircadianLocationStatus(today = todayDay)
                return null
            }
            status = CircadianLocationStatus(cachedLoc.latitude, cachedLoc.longitude, resolvedForDay = resolvedDay, today = todayDay, fixed = false)
            cachedLoc
        }

        val key = "$day|${round4(loc.latitude)}|${round4(loc.longitude)}|$transitionFactor|$tz"
        if (key == cacheKey) return cached
        val windows = compute(loc.latitude, loc.longitude, dateEpochSec, tz, transitionFactor)
        cacheKey = key
        cached = windows
        return windows
    }

    // F83: task90 act5–41 acquisition order (last-known → fresh fix → geo-IP)
    private fun triggerAcquire(locationDay: Long, attemptDay: Long) {
        if (attemptedDay == attemptDay) return
        if (!acquiring.compareAndSet(false, true)) return
        // DA-037: bound to once per calendar day
        attemptedDay = attemptDay
        scope.launch {
            try {
                cancellableOrNull { persistGeoIpAttemptDay(attemptDay) }
                val snap = location.lastKnownLocation()
                    ?: (cancellableOrNull { location.currentLocation() } as? LocationResult.Available)?.snapshot
                    ?: geoIpFallback()
                // DA-037: validate all sources before accepting
                if (snap != null && snap.hasValidCoordinates()) {
                    resolvedLoc = snap
                    resolvedDay = locationDay
                    cacheKey = null // recompute windows
                    // D-103: persist for cold start
                    cancellableOrNull { persistLocation(snap.latitude, snap.longitude, locationDay) }
                    // D-110: signal recompute for async resolution
                    onWindowsRefreshed()
                }
            } finally {
                acquiring.set(false)
            }
        }
    }

    private fun LocationSnapshot.hasValidCoordinates(): Boolean =
        latitude.isFinite() && latitude in -90.0..90.0 &&
            longitude.isFinite() && longitude in -180.0..180.0 &&
            (latitude != 0.0 || longitude != 0.0)

    private suspend fun <T> cancellableOrNull(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    companion object {
        /** Real solar windows via fenced domain math (JVM-testable). */
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
