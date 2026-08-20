package com.tideo.autobrightness.platform.context

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

// Tasker: prof762 Context: App Changed → task43 reads %APP_FOREGROUND for per-app context rules.
interface ForegroundAppMonitor {
    /** True if OPSTR_GET_USAGE_STATS is allowed for this app. */
    fun hasUsageAccessPermission(): Boolean
    fun usageAccessSettingsIntent(): Intent
    /** Polls UsageStatsManager every [intervalMs] ms and emits the current foreground package. */
    fun foregroundPackage(intervalMs: Long = 2000L): Flow<String?>
}

class AndroidForegroundAppMonitor(
    private val context: Context,
    // D-034 (b): clock seam for test time control (shadow clock can't move wall-clock).
    private val clock: () -> Long = System::currentTimeMillis,
) : ForegroundAppMonitor {
    override fun hasUsageAccessPermission(): Boolean = hasUsageStatsAccess(context)

    override fun usageAccessSettingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    override fun foregroundPackage(intervalMs: Long): Flow<String?> = flow {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        // Retain lastKnown to cover apps sitting in foreground longer than poll window (D-034 f).
        var lastKnown: String? = null
        while (true) {
            queryForeground(usm)?.let { lastKnown = it }
            emit(lastKnown)
            delay(intervalMs)
        }
    }

    private fun queryForeground(usm: UsageStatsManager): String? {
        val now = clock()
        val events = usm.queryEvents(now - 3000L, now)
        val event = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                last = event.packageName
            }
        }
        return last
    }
}

@Suppress("DEPRECATION")
fun hasUsageStatsAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
    } else {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}
