package com.tideo.autobrightness.app.runtime

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tideo.autobrightness.app.settings.SettingsStore
import com.tideo.autobrightness.app.storage.serviceHealthDataStore
import com.tideo.autobrightness.app.storage.settingsDataStore

/**
 * Periodic safety net. Evaluation now lives entirely in the live foreground service
 * ([AmbientMonitoringService] + [BrightnessPipelineController]); this worker only re-ensures the
 * service is running (it can be killed under memory pressure) and records a health heartbeat.
 * The legacy poll-loop use case was removed in S9b.
 */
class MaintenanceWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    private val settingsStore = SettingsStore(appContext.settingsDataStore)
    private val healthStore = ServiceHealthStore(appContext.serviceHealthDataStore)

    override suspend fun doWork(): Result {
        val settings = settingsStore.readRawSettings()
        if (!settings.serviceEnabled) return Result.success()

        // Re-ensure the foreground service is up; startForegroundService is a no-op if already running.
        AutoBrightnessRuntime.startMonitoring(applicationContext, "maintenance_reinit")
        healthStore.markApplied(System.currentTimeMillis())
        return Result.success()
    }
}
