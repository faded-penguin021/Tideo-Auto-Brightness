package com.tideo.autobrightness.app.runtime

import android.content.Context
import com.tideo.autobrightness.domain.circadian.SolarCalculator
import com.tideo.autobrightness.domain.context.ContextSignals
import com.tideo.autobrightness.platform.context.AndroidBatteryStateReader
import com.tideo.autobrightness.platform.context.AndroidForegroundAppMonitor
import com.tideo.autobrightness.platform.context.AndroidLocationReader
import com.tideo.autobrightness.platform.context.AndroidWifiInfoReader
import com.tideo.autobrightness.platform.context.BatteryStateReader
import com.tideo.autobrightness.platform.context.ForegroundAppMonitor
import com.tideo.autobrightness.platform.context.LocationReader
import com.tideo.autobrightness.platform.context.WifiInfoReader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

/**
 * Android-backed [ContextSignalSource]: bridges the S7 platform readers into the engine and computes
 * the clock/calendar/solar fields the pure resolver needs (day-of-week, local seconds-of-day, and
 * SUNRISE/SUNSET as local seconds — task43 L62-80, L150-152).
 */
class AndroidContextSignalSource(
    context: Context,
    private val battery: BatteryStateReader = AndroidBatteryStateReader(context.applicationContext),
    private val wifi: WifiInfoReader = AndroidWifiInfoReader(context.applicationContext),
    private val foregroundApp: ForegroundAppMonitor = AndroidForegroundAppMonitor(context.applicationContext),
    private val location: LocationReader = AndroidLocationReader(context.applicationContext),
    private val clock: () -> Long = System::currentTimeMillis,
) : ContextSignalSource {

    override fun batteryFlow(): Flow<BatterySignal> =
        battery.batteryState().map { BatterySignal(percent = it.levelPercent, plugged = it.isCharging) }

    override fun wifiFlow(): Flow<String?> = wifi.ssidFlow()

    override fun foregroundAppFlow(intervalMs: Long): Flow<String?> =
        foregroundApp.foregroundPackage(intervalMs)

    override fun locationFlow(): Flow<LocationSignal> =
        location.locationUpdates().map { LocationSignal(it.latitude, it.longitude) }

    override suspend fun assemble(
        app: String,
        batteryPercent: Int,
        plugged: Boolean,
        wifi: String,
        lat: Double,
        lon: Double,
    ): ContextSignals {
        val cal = Calendar.getInstance()
        cal.timeInMillis = clock()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val nowSecs = cal.get(Calendar.HOUR_OF_DAY) * 3600 +
            cal.get(Calendar.MINUTE) * 60 + cal.get(Calendar.SECOND)

        // lat/lon now come from the engine's live location listener (G2R-F45). 0.0,0.0 = no fix yet:
        // fall back to last-known for the solar computation but report the (0,0) so the gate stays off.
        val haveFix = lat != 0.0 || lon != 0.0
        val solarLoc = if (haveFix) lat to lon else {
            runCatching { location.lastKnownLocation() }.getOrNull()?.let { it.latitude to it.longitude }
        }
        val offsetSecs = cal.timeZone.getOffset(cal.timeInMillis) / 1000L
        val (sunrise, sunset) = solarLocalSeconds(solarLoc?.first, solarLoc?.second, cal.timeInMillis / 1000L, offsetSecs)

        return ContextSignals(
            app = app,
            lat = lat,
            lon = lon,
            batteryPercent = batteryPercent,
            plugged = plugged,
            dayOfWeek = dayOfWeek,
            nowSecondsOfDay = nowSecs,
            wifi = wifi,
            sunriseLocalSecs = sunrise,
            sunsetLocalSecs = sunset,
        )
    }

    // SUNRISE/SUNSET as local seconds-of-day. Falls back to 06:00/18:00 (task43 L67/72) when no
    // location is known or the solar computation fails (e.g. polar day/night sentinels).
    private fun solarLocalSeconds(lat: Double?, lon: Double?, epochSec: Long, offsetSecs: Long): Pair<Long, Long> {
        if (lat == null || lon == null) return DEFAULT_SUNRISE to DEFAULT_SUNSET
        return runCatching {
            val solar = SolarCalculator.compute(lat, lon, epochSec, offsetSecs / 3600.0)
            val rise = Math.floorMod(solar.riseEpochSec + offsetSecs, 86_400L)
            val set = Math.floorMod(solar.setEpochSec + offsetSecs, 86_400L)
            rise to set
        }.getOrDefault(DEFAULT_SUNRISE to DEFAULT_SUNSET)
    }

    private companion object {
        const val DEFAULT_SUNRISE = 21_600L // 06:00
        const val DEFAULT_SUNSET = 64_800L  // 18:00
    }
}
