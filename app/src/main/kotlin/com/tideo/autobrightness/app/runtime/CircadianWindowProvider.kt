package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.CachedSunLocation
import com.tideo.autobrightness.app.settings.ExperimentDateLocation
import com.tideo.autobrightness.domain.circadian.SolarCalculator
import com.tideo.autobrightness.platform.context.LocationReader
import com.tideo.autobrightness.platform.context.LocationResult
import com.tideo.autobrightness.platform.context.LocationSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.SimpleTimeZone
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The real circadian ramp windows (seconds-of-day) for the dynamic-scale computation — morning/evening
 * transition bounds + sunlight duration + polar flag, the fields task90 Block #2 reads.
 *
 * **Frame:** UTC seconds-of-day — `SolarCalculator.buildScheduleWindows` derives them as
 * `riseEpochSec % 86400` etc., and the pipeline's `now` is `(currentTimeMillis/1000) % 86400`, also
 * UTC. This matches Tasker exactly: task90 act0 sets `%AAB_NowSS = %TIMES % 86400` and act59 sets the
 * windows from `%ss_* % 86400` — both UTC-seconds-of-day. `riseEpochSec` is tz-independent (the
 * `zoneOffset` cancels between `startOfDay` and `localHour`), so the ramp tracks the real sun.
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
 * D-110: freshness of the location backing the live circadian modifier, for the UI staleness hint.
 *
 * @param latitude/longitude the active location (null = none resolved yet → modifier uses defaults).
 * @param resolvedForDay epoch-day the active location was acquired for (null when none).
 * @param today epoch-day "now".
 * @param fixed true when a manual fixed lat/lon override is pinned (never "stale").
 */
data class CircadianLocationStatus(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val resolvedForDay: Long? = null,
    val today: Long = 0L,
    val fixed: Boolean = false,
) {
    /** A location (fresh or cached) is available — the modifier tracks the real sun, not the defaults. */
    val hasLocation: Boolean get() = latitude != null && longitude != null
    /** Whole days between the active location's acquisition day and today (0 = acquired today). */
    val ageDays: Long? get() = resolvedForDay?.let { (today - it).coerceAtLeast(0L) }
    /** The cached location is from a previous day and isn't a pinned fixed location → show "stale". */
    val isStale: Boolean get() = !fixed && hasLocation && (ageDays ?: 0L) > 0L
}

/**
 * Supplies live [CircadianWindows] to the pipeline (G2R-F73). Before this, the pipeline fed
 * `TimeContext`'s **defaults** (a fixed 6–8am / 18–20pm UTC morning/evening) whenever no location was
 * known — and `lastKnownLocation()` is frequently null on a device that has not actively used GPS, so
 * the default eveningStart (18:00 UTC = 20:00 local @UTC+2) made the evening ramp fire ~1 h early
 * (the owner's "scale 1.025 at 20:58 local" — Gate 2 4th re-test). This now:
 *
 *  - **F73** computes the tz offset at the **target date instant** (DST-aware), so the live ramp uses
 *    today's current offset and a fixed winter date uses the winter offset; the UTC frame already
 *    matched Tasker (above), so the residual error was the missing location, not the math.
 *  - **F39** resolves the fixed Date and Location **independently** — either, both, or neither — so a
 *    date-only override (e.g. 21 Dec, live location) or a location-only override actually applies.
 *  - **F83** ports task90's once-a-day location acquisition (act5–41): **skip** when a fixed lat/lon
 *    is pinned; otherwise acquire from Android (last-known → fresh fix) and, failing that, fall back
 *    to **ipwho.is** geo-IP (act28, HTTPS D-121). The result is cached per day and re-acquired when the day rolls
 *    over (`%AAB_SunLastDate != %DATE`). Acquisition is async (it can hit the network); `current()`
 *    stays non-blocking and returns the old windows until the first fix lands.
 *
 * domain/ stays fenced: this only *calls* `SolarCalculator.compute`/`buildScheduleWindows`.
 */
class CircadianWindowProvider(
    private val scope: CoroutineScope,
    overrideFlow: Flow<ExperimentDateLocation>,
    private val location: LocationReader,
    // F83: ipwho.is geo-IP fallback (task90 act28, HTTPS D-121), injected as a suspend fn for testability.
    private val geoIpFallback: suspend () -> LocationSnapshot?,
    // D-103: load/save the once-a-day resolved location across process restarts (default no-op so
    // existing tests that construct the provider directly keep their in-memory-only behavior).
    private val loadCachedLocation: suspend () -> CachedSunLocation? = { null },
    private val persistLocation: suspend (latitude: Double, longitude: Double, day: Long) -> Unit =
        { _, _, _ -> },
    private val clock: () -> Long = System::currentTimeMillis,
    // F73: offset at the TARGET instant, not "now" — covers DST and fixed dates in another season.
    private val tzOffsetForDate: (dateEpochSec: Long) -> Double = { dateEpochSec ->
        TimeZone.getDefault().getOffset(dateEpochSec * 1000L) / 3_600_000.0
    },
) {
    // S12.9e volatile audit — these cross three coroutines (the overrideFlow collector, the async
    // triggerAcquire launch, and current() on the pipeline consumer). Each holds a single independent
    // value with no compound invariant between them (a stale read at worst recomputes one window or
    // skips one cache hit, self-correcting next call), so @Volatile (visibility-only) is the right tool;
    // the only multi-step action — the once-per-day acquire — is separately guarded by [acquiring].
    @Volatile private var override: ExperimentDateLocation = ExperimentDateLocation()
    @Volatile private var cacheKey: String? = null
    @Volatile private var cached: CircadianWindows? = null

    // F83: the once-a-day acquired location (Android or geo-IP), keyed by the day it was acquired for.
    @Volatile private var resolvedLoc: LocationSnapshot? = null
    @Volatile private var resolvedDay: Long = Long.MIN_VALUE
    private val acquiring = AtomicBoolean(false)

    // D-110: fired when a location resolves AFTER construction (cache seed or a fresh acquire) so the
    // pipeline recomputes immediately. Without this, a cold start in STABLE light set the initial
    // brightness with the TimeContext defaults (scaleDynamic ≈ 0.85) BEFORE the async cache/geo-IP fix
    // landed, and prof760 then dropped every steady-light cycle — so the circadian modifier stayed stuck
    // at the default all morning while the chart (which reads last-known/representative location directly)
    // looked correct. AppModule wires this to controller.reapply().
    //
    // Settable because the controller is built after this provider (the provider feeds it `current`), so
    // the wiring is post-construction. The cache-seed launch is async and may resolve BEFORE the callback
    // is wired; the setter therefore fires once immediately when a location already resolved, so the
    // recompute is never lost to construction-vs-wiring ordering (either path triggers exactly one
    // reapply).
    @Volatile private var _onWindowsRefreshed: () -> Unit = {}
    var onWindowsRefreshed: () -> Unit
        get() = _onWindowsRefreshed
        set(value) {
            _onWindowsRefreshed = value
            if (resolvedLoc != null) value()
        }

    /** D-110: the location backing the live circadian modifier + its freshness, for the UI staleness
     *  hint. `resolvedForDay` is the epoch-day the active location was acquired for (null = no fix yet);
     *  compare to today to show "cached N days ago". Updated on every [current] call. */
    @Volatile var status: CircadianLocationStatus = CircadianLocationStatus()
        private set

    init {
        // F39 override drives the windows; invalidate the cache (and force a re-acquire) when it changes.
        scope.launch {
            overrideFlow.collect {
                override = it
                cacheKey = null
                resolvedDay = Long.MIN_VALUE
            }
        }
        // D-103: seed the in-memory location from the persisted once-a-day fix so a cold start
        // (process death / service restart after screen-on) returns the cached location immediately
        // instead of falling back to TimeContext defaults. Don't clobber a fresher in-memory fix that
        // a concurrent acquire may already have set. current() still re-acquires when the day rolls.
        scope.launch {
            val cached = runCatching { loadCachedLocation() }.getOrNull() ?: return@launch
            if (resolvedLoc == null) {
                resolvedLoc = LocationSnapshot(cached.latitude, cached.longitude)
                resolvedDay = cached.day
                cacheKey = null
                // D-110: recompute now that a (possibly day-old) cached location is available — the
                // initial brightness may already have been set with the default windows in stable light.
                onWindowsRefreshed()
            }
        }
    }

    /** Windows for the active location/date at [transitionFactor], or null when no location is known. */
    fun current(transitionFactor: Double): CircadianWindows? {
        val ov = override
        val nowSec = clock() / 1000L

        // F39: fixed Date is independent of fixed Location. No date override → today.
        val dateEpochSec = ov.date?.let { parseDateEpochSec(it, tzOffsetForDate(nowSec)) } ?: nowSec
        val tz = tzOffsetForDate(dateEpochSec)
        val day = dateEpochSec / 86_400L

        val todayDay = nowSec / 86_400L
        val loc: LocationSnapshot = if (ov.latitude != null && ov.longitude != null) {
            // F83: fixed lat/lon → use it directly, skip all acquisition.
            status = CircadianLocationStatus(ov.latitude, ov.longitude, resolvedForDay = day, today = todayDay, fixed = true)
            LocationSnapshot(ov.latitude!!, ov.longitude!!)
        } else {
            // F83: acquire (once a day, async) when we have no fix or the day rolled over.
            if (resolvedLoc == null || resolvedDay != day) triggerAcquire(day)
            // D-110: fall back to the last resolved location even when the day has rolled over and no
            // fresh fix is available (location + geo-IP off) — recompute TODAY's windows with the cached
            // coordinates instead of returning null → the default-window 0.85. status records the age so
            // the UI can show "cached N days ago" until the user refreshes the fix.
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

    // F83: task90 act5–41 acquisition order, async — Android last-known → fresh fix → ipwho.is.
    private fun triggerAcquire(day: Long) {
        if (!acquiring.compareAndSet(false, true)) return
        scope.launch {
            try {
                val snap = location.lastKnownLocation()
                    ?: (runCatching { location.currentLocation() }.getOrNull() as? LocationResult.Available)?.snapshot
                    ?: geoIpFallback()
                if (snap != null) {
                    resolvedLoc = snap
                    resolvedDay = day
                    cacheKey = null // recompute windows with the freshly acquired location
                    // D-103: persist so the next cold start reuses it before re-acquiring.
                    runCatching { persistLocation(snap.latitude, snap.longitude, day) }
                    // D-110: a fresh fix landed asynchronously — recompute so the modifier updates even
                    // if the light is steady (no sensor cycle would otherwise call current() again).
                    onWindowsRefreshed()
                }
            } finally {
                acquiring.set(false)
            }
        }
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
