package com.tideo.autobrightness.platform.context

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * Battery instrumentation for the Tasker task524 `_CalibratePowerDraw` port: the raw current sensor,
 * battery voltage, and charging status. The pure normalization + post-processing live in the domain
 * (`PowerDrawCalibration`); this is the thin hardware adapter.
 *
 * Provenance: task524 reads `BatteryManager.getLongProperty(2)` (CURRENT_NOW, falling back to (3)
 * CURRENT_AVERAGE), `getIntProperty(6)` (STATUS), and `ACTION_BATTERY_CHANGED EXTRA_VOLTAGE`.
 */
interface PowerMeter {
    /** Raw `BATTERY_PROPERTY_CURRENT_NOW` (µA or mA per device), falling back to `CURRENT_AVERAGE`; the
     *  domain `PowerDrawCalibration.normalizeCurrentMa` converts it. `Long.MIN_VALUE`/0 ⇒ unsupported. */
    fun readCurrentRaw(): Long

    /** Battery voltage in volts (`EXTRA_VOLTAGE / 1000`), or 0.0 if unknown. */
    fun readVoltageVolts(): Double

    /** task524 abort condition: STATUS == Charging (2) or Full (5). */
    fun isCharging(): Boolean

    /** task524 pre-check: the current sensor responds (raw != 0 and != Long.MIN_VALUE). */
    fun hasCurrentSensor(): Boolean
}

class AndroidPowerMeter(private val context: Context) : PowerMeter {
    private val batteryManager: BatteryManager =
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    override fun readCurrentRaw(): Long {
        val now = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        return if (now == 0L) batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) else now
    }

    override fun readVoltageVolts(): Double {
        val status: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val mv = status?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        return if (mv > 0) mv / 1000.0 else 0.0
    }

    override fun isCharging(): Boolean {
        val status = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    override fun hasCurrentSensor(): Boolean {
        val raw = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        return raw != 0L && raw != Long.MIN_VALUE
    }
}
