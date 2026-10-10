package com.tideo.autobrightness.app.runtime

import android.content.Context
import com.tideo.autobrightness.app.settings.ExperimentPrefsStore
import com.tideo.autobrightness.app.storage.experimentPrefsDataStore
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

class AndroidContextSignalSource(
    context: Context,
    private val battery: BatteryStateReader = AndroidBatteryStateReader(context.applicationContext),
    private val wifi: WifiInfoReader = AndroidWifiInfoReader(context.applicationContext),
    private val foregroundApp: ForegroundAppMonitor = AndroidForegroundAppMonitor(context.applicationContext),
    private val location: LocationReader = AndroidLocationReader(context.applicationContext),
    private val clock: () -> Long = System::currentTimeMillis,
    private val solarTimes: ContextSolarTimes = ContextSolarTimes(
        ExperimentPrefsStore(context.applicationContext.experimentPrefsDataStore),
        location,
    ),
) : ContextSignalSource {

    override fun batteryFlow(): Flow<BatterySignal> =
        battery.batteryState().map { BatterySignal(percent = it.levelPercent, plugged = it.isPlugged) }

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

        val (sunrise, sunset) = solarTimes.today(cal.timeInMillis, liveFix = lat to lon)
            ?: (ContextSolarTimes.DEFAULT_SUNRISE to ContextSolarTimes.DEFAULT_SUNSET)

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
}
