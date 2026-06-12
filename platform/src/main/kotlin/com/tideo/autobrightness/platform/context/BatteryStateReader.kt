package com.tideo.autobrightness.platform.context

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

// Tasker: prof763 Context: Battery Changed → task43 evaluates %BATT/%CHARGING for context rules.
data class BatteryState(
    val isCharging: Boolean,
    val levelPercent: Int,
    val temperatureTenths: Int,
)

interface BatteryStateReader {
    fun batteryState(): Flow<BatteryState>
}

class AndroidBatteryStateReader(private val context: Context) : BatteryStateReader {
    override fun batteryState(): Flow<BatteryState> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                trySend(intent.toBatteryState())
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        // Sticky broadcast — registerReceiver returns the last broadcast immediately.
        val sticky = context.registerReceiver(receiver, filter)
        sticky?.let { trySend(it.toBatteryState()) }
        awaitClose { context.unregisterReceiver(receiver) }
    }

    private fun Intent.toBatteryState(): BatteryState {
        val status = getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val pct = if (scale > 0) (level * 100 / scale) else 0
        val temp = getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        return BatteryState(isCharging, pct, temp)
    }
}
