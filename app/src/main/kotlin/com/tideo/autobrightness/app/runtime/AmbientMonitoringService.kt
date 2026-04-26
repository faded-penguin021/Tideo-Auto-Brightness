package com.tideo.autobrightness.app.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.tideo.autobrightness.app.AppModule
import com.tideo.autobrightness.app.R
import com.tideo.autobrightness.app.settings.SettingsStore
import com.tideo.autobrightness.app.storage.serviceHealthDataStore
import com.tideo.autobrightness.app.storage.settingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AmbientMonitoringService : Service() {
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private lateinit var settingsStore: SettingsStore
    private lateinit var healthStore: ServiceHealthStore
    private val appModule = AppModule()
    private var monitoringJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(applicationContext.settingsDataStore)
        healthStore = ServiceHealthStore(applicationContext.serviceHealthDataStore)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (monitoringJob?.isActive != true) {
            monitoringJob = scope.launch {
                monitorLoop()
            }
        }
        return START_STICKY
    }

    private suspend fun monitorLoop() {
        while (true) {
            val settings = settingsStore.readSettings()
            if (!settings.enabled) {
                stopSelf()
                return
            }

            val now = System.currentTimeMillis()
            val result = appModule.evaluateAndApplyBrightnessUseCase.run()
            if (result.sampledLux != null) {
                healthStore.markSensorSampled(now)
            }
            if (result.applied) {
                healthStore.markApplied(now)
            } else {
                healthStore.markDegraded("Continuous sensing paused: permissions or OS restrictions")
            }

            delay(SAMPLE_INTERVAL_MS)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ambient monitoring",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Auto Brightness active")
            .setContentText("Monitoring ambient changes")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        monitoringJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.tideo.autobrightness.runtime.action.START"
        const val EXTRA_REASON = "reason"
        private const val CHANNEL_ID = "ambient_monitoring"
        private const val NOTIFICATION_ID = 1001
        private const val SAMPLE_INTERVAL_MS = 15_000L
    }
}
