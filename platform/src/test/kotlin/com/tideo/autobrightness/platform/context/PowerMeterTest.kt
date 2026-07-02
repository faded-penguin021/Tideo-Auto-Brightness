package com.tideo.autobrightness.platform.context

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** H3 glue-seam audit: the task524 power-meter property mapping had no test. */
@RunWith(RobolectricTestRunner::class)
class PowerMeterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val meter = AndroidPowerMeter(context)

    @Test
    fun readCurrentRaw_usesCurrentNow_whenNonZero() {
        shadowOf(batteryManager).setLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW, -350_000L)
        assertEquals(-350_000L, meter.readCurrentRaw())
    }

    @Test
    fun readCurrentRaw_zeroFallsBackToCurrentAverage() {
        // task524: getLongProperty(2) CURRENT_NOW, falling back to (3) CURRENT_AVERAGE on 0.
        shadowOf(batteryManager).setLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW, 0L)
        shadowOf(batteryManager).setLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE, 120_000L)
        assertEquals(120_000L, meter.readCurrentRaw())
    }

    @Test
    fun hasCurrentSensor_zeroAndMinValueMeanUnsupported() {
        shadowOf(batteryManager).setLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW, 0L)
        assertFalse(meter.hasCurrentSensor(), "0 = sensor not responding")
        shadowOf(batteryManager).setLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW, Long.MIN_VALUE)
        assertFalse(meter.hasCurrentSensor(), "Long.MIN_VALUE = property unsupported")
        shadowOf(batteryManager).setLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW, -5_000L)
        assertTrue(meter.hasCurrentSensor())
    }

    @Test
    fun isCharging_chargingOrFull_abortsCalibration() {
        // task524 abort condition: STATUS == Charging (2) or Full (5).
        shadowOf(batteryManager).setIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
        assertTrue(meter.isCharging())
        shadowOf(batteryManager).setIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS, BatteryManager.BATTERY_STATUS_FULL)
        assertTrue(meter.isCharging())
        shadowOf(batteryManager).setIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING)
        assertFalse(meter.isCharging())
    }

    @Test
    fun readVoltageVolts_convertsMillivoltsFromStickyBroadcast() {
        context.sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_VOLTAGE, 3850),
        )
        assertEquals(3.85, meter.readVoltageVolts(), 1e-9)
    }

    @Test
    fun readVoltageVolts_missingExtraReadsZero() {
        context.sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED))
        assertEquals(0.0, meter.readVoltageVolts(), 1e-9)
    }
}
