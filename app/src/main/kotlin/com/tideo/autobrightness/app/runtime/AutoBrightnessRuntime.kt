package com.tideo.autobrightness.app.runtime

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.tideo.autobrightness.app.settings.SettingsStore
import com.tideo.autobrightness.app.storage.serviceHealthDataStore
import com.tideo.autobrightness.app.storage.settingsDataStore
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

object AutoBrightnessRuntime {
    fun bootstrap(context: Context) {
        scheduleMaintenance(context)
        // S12.9e: process-scoped + supervised.
        AppProcessScope.launch {
            val settings = SettingsStore(context.settingsDataStore).readRawSettings()
            if (settings.serviceEnabled) {
                startMonitoring(context, "bootstrap")
            }
        }
    }

    /** Pause the live pipeline from the UI (mirrors the notification's Pause action). */
    fun pause(context: Context) = sendServiceAction(context, AmbientMonitoringService.ACTION_PAUSE)

    /** Resume the live pipeline from the UI (mirrors the notification's Resume action). */
    fun resume(context: Context) = sendServiceAction(context, AmbientMonitoringService.ACTION_RESUME)

    // G2-F16: force re-evaluate now; no-op when service not running (D-140).
    fun reapply(context: Context) = sendServiceAction(context, AmbientMonitoringService.ACTION_REAPPLY)

    // DA-018: resume context automation (run genuine evaluation, not just reapply).
    fun resumeContext(context: Context) = sendServiceAction(context, AmbientMonitoringService.ACTION_RESUME_CONTEXT)

    /**
     * D-157: full-stop panic from an external command (mirrors the notification's Reset button, which
     * sends the same [AmbientMonitoringService.ACTION_PANIC]): restore brightness, drop dimming, tear
     * the service down. Safe when not running — startForegroundService creates the instance, it panics
     * and self-terminates (NOT_STICKY), exactly as the notification Reset does.
     */
    fun panic(context: Context) = sendServiceAction(context, AmbientMonitoringService.ACTION_PANIC)

    private fun sendServiceAction(context: Context, action: String) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, AmbientMonitoringService::class.java).setAction(action)
        try {
            // minSdk 31 ≥ O, so startForegroundService is always available (S12.9a dead-branch removal).
            // NOTE (D-140): this CREATES the service when it is not running — it is never a no-op —
            // so control actions (pause/reapply) are validated in the service's onStartCommand.
            appContext.startForegroundService(intent)
        } catch (_: IllegalStateException) {
            // Background-start restriction (the app is neither foreground nor exempt) — the action
            // was user-initiated from UI that no longer exists; dropping it is the right outcome.
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
            // minSdk 31 ≥ O, so startForegroundService is always available (S12.9a dead-branch removal).
            appContext.startForegroundService(intent)
        } catch (error: ForegroundServiceStartNotAllowedException) {
            AppProcessScope.launch {
                ServiceHealthStore(appContext.serviceHealthDataStore).markDegraded(
                    "Foreground start blocked: ${error.javaClass.simpleName}",
                )
            }
        } catch (error: IllegalStateException) {
            AppProcessScope.launch {
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
