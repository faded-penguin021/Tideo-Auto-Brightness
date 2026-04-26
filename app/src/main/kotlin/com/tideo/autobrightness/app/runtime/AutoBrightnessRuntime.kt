package com.tideo.autobrightness.app.runtime

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.tideo.autobrightness.app.settings.SettingsStore
import com.tideo.autobrightness.app.storage.serviceHealthDataStore
import com.tideo.autobrightness.app.storage.settingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

object AutoBrightnessRuntime {
    fun bootstrap(context: Context) {
        scheduleMaintenance(context)
        CoroutineScope(Dispatchers.Default).launch {
            val settings = SettingsStore(context.settingsDataStore).readSettings()
            if (settings.enabled) {
                startMonitoring(context, "bootstrap")
            }
        }
    }

    fun onSettingChanged(context: Context, enabled: Boolean) {
        scheduleMaintenance(context)
        if (enabled) {
            startMonitoring(context, "settings_enabled")
        } else {
            stopMonitoring(context)
        }
    }

    fun startMonitoring(context: Context, reason: String) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, AmbientMonitoringService::class.java).apply {
            action = AmbientMonitoringService.ACTION_START
            putExtra(AmbientMonitoringService.EXTRA_REASON, reason)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        } catch (error: ForegroundServiceStartNotAllowedException) {
            CoroutineScope(Dispatchers.IO).launch {
                ServiceHealthStore(appContext.serviceHealthDataStore).markDegraded(
                    "Foreground start blocked: ${error.javaClass.simpleName}",
                )
            }
        } catch (error: IllegalStateException) {
            CoroutineScope(Dispatchers.IO).launch {
                ServiceHealthStore(appContext.serviceHealthDataStore).markDegraded(
                    "Background restricted: ${error.javaClass.simpleName}",
                )
            }
        }
    }

    fun stopMonitoring(context: Context) {
        context.applicationContext.stopService(Intent(context, AmbientMonitoringService::class.java))
    }

    fun scheduleMaintenance(context: Context) {
        val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build(),
            )
            .addTag(MAINTENANCE_TAG)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_MAINTENANCE_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private const val UNIQUE_MAINTENANCE_WORK = "auto_brightness_maintenance"
    const val MAINTENANCE_TAG = "maintenance"
}
