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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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

    companion object {
        fun of(override: ExperimentDateLocation, stored: CachedSunLocation?, today: Long): CircadianLocationStatus =
            when {
                override.latitude != null && override.longitude != null ->
                    CircadianLocationStatus(override.latitude, override.longitude, today, today, fixed = true)
                stored != null -> CircadianLocationStatus(stored.latitude, stored.longitude, stored.day, today)
                else -> CircadianLocationStatus(today = today)
            }
    }
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
    // D-103: the stored day's location; DD-061: followed, so a fix saved by the UI reaches a running service
    private val storedLocation: Flow<CachedSunLocation?> = flowOf(null),
    private val persistLocation: suspend (latitude: Double, longitude: Double, day: Long) -> Unit =
        { _, _, _ -> },
    private val storedAttemptDay: Flow<Long?> = flowOf(null),
    private val persistGeoIpAttemptDay: suspend (day: Long) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
    // F73: offset at the TARGET instant (DST-aware)
    private val tzOffsetForDate: (dateEpochSec: Long) -> Double = { dateEpochSec ->
        TimeZone.getDefault().getOffset(dateEpochSec * 1000L) / 3_600_000.0
    },
) {
    // S12.9e volatile audit: single values; the location/day pair is written only under adopt's lock
    @Volatile private var override: ExperimentDateLocation = ExperimentDateLocation()
    @Volatile private var cacheKey: String? = null
    @Volatile private var cached: CircadianWindows? = null

    // F83: once-a-day acquired location (Android or geo-IP), keyed by day
    @Volatile private var resolvedLoc: LocationSnapshot? = null
    @Volatile private var resolvedDay: Long = Long.MIN_VALUE
    @Volatile private var attemptedDay: Long = Long.MIN_VALUE
    @Volatile private var acquisitionReady = false
    private val acquiring = AtomicBoolean(false)

    // D-110: fired when a location resolves after construction (seed, acquire, stored fix) so the pipeline recomputes.
    // Settable post-construction; fires immediately if location already resolved.
    @Volatile private var _onWindowsRefreshed: () -> Unit = {}
    var onWindowsRefreshed: () -> Unit
        get() = _onWindowsRefreshed
        set(value) {
            _onWindowsRefreshed = value
            if (resolvedLoc != null || acquisitionReady) value()
        }

    init {
        // F39: invalidate cache when override changes
        scope.launch {
            overrideFlow.collect {
                if (it == override) return@collect
                override = it
                cacheKey = null
                synchronized(this@CircadianWindowProvider) { resolvedDay = Long.MIN_VALUE }
            }
        }
        // D-103: seed from the stored location on cold start, then follow it (DD-061)
        scope.launch {
            var seeded = false
            storedLocation.distinctUntilChanged().catch { emit(null) }.collect { stored ->
                val adopted = adopt(stored)
                if (seeded) {
                    if (adopted) onWindowsRefreshed()
                    return@collect
                }
                seeded = true
                cancellableOrNull { storedAttemptDay.first() }?.let { attemptedDay = it }
                acquisitionReady = true
                onWindowsRefreshed()
                scope.launch { followAttemptDay() }
            }
        }
    }

    // DD-061: a stored attempt day cleared (the IP fallback switched on) frees the day for one more try
    private suspend fun followAttemptDay() {
        storedAttemptDay.distinctUntilChanged().catch {}.collect { day ->
            if (day != null || attemptedDay == Long.MIN_VALUE) return@collect
            attemptedDay = Long.MIN_VALUE
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
            LocationSnapshot(ov.latitude!!, ov.longitude!!)
        } else {
            // F83: acquire once a day when needed
            if (acquisitionReady && (resolvedLoc == null || resolvedDay != day)) triggerAcquire(day, todayDay)
            // D-110: fall back to cached location when no fresh fix available
            resolvedLoc ?: return null
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
                // DA-037: validate all sources before accepting; DD-061: a fix stored meanwhile wins
                val fix = snap?.let { CachedSunLocation(it.latitude, it.longitude, locationDay) }
                if (fix != null && adopt(fix, unlessResolvedFor = locationDay)) {
                    // D-103: persist for cold start
                    cancellableOrNull { persistLocation(fix.latitude, fix.longitude, fix.day) }
                    // D-110: signal recompute for async resolution
                    onWindowsRefreshed()
                }
            } finally {
                acquiring.set(false)
            }
        }
    }

    @Synchronized
    private fun adopt(stored: CachedSunLocation?, unlessResolvedFor: Long? = null): Boolean {
        val snap = stored?.let { LocationSnapshot(it.latitude, it.longitude) }?.takeIf { it.isValid } ?: return false
        if (unlessResolvedFor != null && resolvedLoc != null && resolvedDay == unlessResolvedFor) return false
        if (snap == resolvedLoc && stored.day == resolvedDay) return false
        resolvedLoc = snap
        resolvedDay = stored.day
        cacheKey = null
        return true
    }

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
