package com.tideo.autobrightness.platform.context

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

/** H3 glue-seam audit: the ACTION_BATTERY_CHANGED → BatteryState mapping had no test. */
@RunWith(RobolectricTestRunner::class)
class BatteryStateReaderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val reader = AndroidBatteryStateReader(context)

    private fun batteryIntent(status: Int, level: Int, scale: Int, tempTenths: Int) =
        Intent(Intent.ACTION_BATTERY_CHANGED)
            .putExtra(BatteryManager.EXTRA_STATUS, status)
            .putExtra(BatteryManager.EXTRA_LEVEL, level)
            .putExtra(BatteryManager.EXTRA_SCALE, scale)
            .putExtra(BatteryManager.EXTRA_TEMPERATURE, tempTenths)

    @Test
    fun stickyBroadcast_emitsImmediately_withScaledPercent() = runTest {
        context.sendStickyBroadcast(
            batteryIntent(BatteryManager.BATTERY_STATUS_CHARGING, 50, 200, 321),
        )
        // Non-100 EXTRA_SCALE devices must not report the raw level as a percent.
        assertEquals(
            BatteryState(isCharging = true, levelPercent = 25, temperatureTenths = 321),
            reader.batteryState().first(),
        )
    }

    @Test
    fun fullStatus_countsAsCharging() = runTest {
        context.sendStickyBroadcast(
            batteryIntent(BatteryManager.BATTERY_STATUS_FULL, 100, 100, 250),
        )
        assertEquals(true, reader.batteryState().first().isCharging)
    }

    @Test
    fun dischargingStatus_isNotCharging() = runTest {
        context.sendStickyBroadcast(
            batteryIntent(BatteryManager.BATTERY_STATUS_DISCHARGING, 80, 100, 250),
        )
        val state = reader.batteryState().first()
        assertEquals(false, state.isCharging)
        assertEquals(80, state.levelPercent)
    }

    @Test
    fun zeroScale_guardsTheDivision() = runTest {
        context.sendStickyBroadcast(
            batteryIntent(BatteryManager.BATTERY_STATUS_DISCHARGING, 80, 0, 250),
        )
        assertEquals(0, reader.batteryState().first().levelPercent)
    }
}
