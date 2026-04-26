package com.tideo.autobrightness.app.runtime

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tideo.autobrightness.app.AppModule
import com.tideo.autobrightness.app.settings.SettingsStore
import com.tideo.autobrightness.app.storage.serviceHealthDataStore
import com.tideo.autobrightness.app.storage.settingsDataStore

class MaintenanceWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    private val settingsStore = SettingsStore(appContext.settingsDataStore)
    private val healthStore = ServiceHealthStore(appContext.serviceHealthDataStore)
    private val appModule = AppModule()

    override suspend fun doWork(): Result {
        val settings = settingsStore.readSettings()
        if (!settings.enabled) return Result.success()

        val now = System.currentTimeMillis()
        val result = appModule.evaluateAndApplyBrightnessUseCase.run()
        if (result.sampledLux != null) {
            healthStore.markSensorSampled(now)
        }
        if (result.applied) {
            healthStore.markApplied(now)
        } else {
            healthStore.markDegraded("Maintenance-only mode active")
        }

        AutoBrightnessRuntime.startMonitoring(applicationContext, "maintenance_reinit")
        return Result.success()
    }
}
