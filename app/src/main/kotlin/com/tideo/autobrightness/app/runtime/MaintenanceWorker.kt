package com.tideo.autobrightness.app.runtime

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tideo.autobrightness.app.settings.SettingsStore
import com.tideo.autobrightness.app.storage.serviceHealthDataStore
import com.tideo.autobrightness.app.storage.settingsDataStore

/** Periodic safety net: re-ensure service is running, record heartbeat (S9b: poll-loop removed). */
class MaintenanceWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    private val settingsStore = SettingsStore(appContext.settingsDataStore)
    private val healthStore = ServiceHealthStore(appContext.serviceHealthDataStore)

    override suspend fun doWork(): Result {
        val settings = settingsStore.readRawSettings()
        if (!settings.serviceEnabled) return Result.success()

        // Re-ensure the service is up; a running one still gets onStartCommand, never a no-op (DC-065).
        AutoBrightnessRuntime.startMonitoring(applicationContext, "maintenance_reinit")
        healthStore.markApplied(System.currentTimeMillis())
        return Result.success()
    }
}
