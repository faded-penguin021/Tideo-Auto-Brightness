package com.tideo.autobrightness.app.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.AppModule
import com.tideo.autobrightness.app.storage.settingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Foreground service hosting the real [BrightnessPipelineController] (S9a runtime core).
 *
 * Replaces the toy poll loop: composes the live platform adapters, runs as a `specialUse`
 * foreground service (continuous ambient-light monitoring), surfaces a live lux/target
 * notification with Pause/Resume/Disable/Panic actions, and routes display ON/OFF to the
 * pipeline's reinit/hibernate paths (prof761/task618 and prof753/task585).
 */
class AmbientMonitoringService : Service() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var controller: BrightnessPipelineController
    private lateinit var contextEngine: ContextEngine
    private var notificationJob: Job? = null

    // SCREEN_ON/OFF are runtime-only broadcasts (not deliverable to manifest receivers).
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> { controller.onScreenOff(); contextEngine.onScreenOff() }
                Intent.ACTION_SCREEN_ON -> { controller.onScreenOn(); contextEngine.onScreenOn() }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // AppModule composes the real graph (S7 adapters + S9a pipeline + S9b super dimming +
        // S10 context engine); writer and observer share one instance for the suppress-echo
        // marker (D-034), and the pipeline reads its settings through the context engine.
        val runtime = AppModule(applicationContext).createRuntime(scope)
        controller = runtime.controller
        contextEngine = runtime.contextEngine

        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(NotificationModel()),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )

        when (intent?.action) {
            ACTION_PAUSE -> controller.pause()
            ACTION_RESUME -> controller.resume()
            ACTION_REAPPLY -> {
                // Settings Apply / profile load: re-run the pipeline now (G2-F16). ensureRunning()
                // first so an Apply made while the service is up (but this start re-delivers) is safe.
                ensureRunning()
                // Refresh the effective settings from the FRESH baseline BEFORE re-applying, so a
                // manual DataStore edit (e.g. min-brightness) takes effect immediately rather than
                // using the stale context snapshot (G2R-F11/F12).
                scope.launch {
                    contextEngine.reevaluate()
                    controller.reapply()
                }
            }
            ACTION_PANIC -> {
                // task528 panic = full stop (not a pausable state, G1-F4): restore brightness +
                // drop dimming, then tear the service down like Disable.
                scope.launch { panicAndStop() }
                return START_NOT_STICKY
            }
            ACTION_DISABLE -> {
                scope.launch { disableAndStop() }
                return START_NOT_STICKY
            }
            else -> ensureRunning()
        }
        return START_STICKY
    }

    private fun ensureRunning() {
        controller.start()
        contextEngine.start(scope)
        if (notificationJob?.isActive == true) return
        val manualOverrideFlow = applicationContext.settingsDataStore.data
            .map { it.contextOverride }
            .distinctUntilChanged()
        notificationJob = scope.launch {
            combine(controller.state, contextEngine.activeContext, manualOverrideFlow) { state, ctx, manualOverride ->
                // Each accepted cycle drives time-window re-evaluation (contexts_spec — prof764).
                contextEngine.onPipelineTick()
                // Republish the live snapshot so the in-app Dashboard / Menu can render it (S11). The
                // manual-load override lock (%AAB_ContextOverride) is surfaced separately from the
                // active context rule (F46) so the Menu can distinguish them.
                LiveRuntimeState.publish(state, ctx, manualOverride)
                NotificationModel(state.smoothedLux, state.targetBrightness, state.paused, state.serviceOn, ctx)
            }
                .distinctUntilChanged()
                .collect { model ->
                    if (!model.serviceOn) return@collect
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, buildNotification(model))
                }
        }
    }

    private suspend fun disableAndStop() {
        controller.stop()
        tearDownDisabled()
    }

    private suspend fun panicAndStop() {
        controller.emergencyStop() // restore 255 + drop dimming + cancel jobs (task528)
        tearDownDisabled()
    }

    private suspend fun tearDownDisabled() {
        // Persist the disable so boot/screen receivers do not restart the loop.
        applicationContext.settingsDataStore.updateData { it.copy(serviceEnabled = false) }
        LiveRuntimeState.reset()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ambient monitoring",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private data class NotificationModel(
        val smoothedLux: Double? = null,
        val targetBrightness: Int? = null,
        val paused: Boolean = false,
        val serviceOn: Boolean = true,
        val activeContext: String? = null,
    )

    private fun buildNotification(model: NotificationModel): Notification {
        // Without WRITE_SETTINGS the loop runs but every brightness write is a no-op (G1-F1);
        // surface why nothing is changing instead of looking silently broken.
        val canWrite = android.provider.Settings.System.canWrite(this)
        val title = when {
            !canWrite -> "Auto Brightness — permission needed"
            model.paused -> "Auto Brightness paused"
            else -> "Auto Brightness active"
        }
        val text = when {
            !canWrite -> "Grant 'Modify system settings' to control brightness"
            model.paused -> "Manual override active — tap Resume to continue"
            model.smoothedLux != null && model.targetBrightness != null ->
                "Lux ${model.smoothedLux.toInt()} → brightness ${model.targetBrightness}"
            else -> "Monitoring ambient light"
        }
        // Surface the active context override (%AAB_ActiveContext) as a second line when one is on.
        val contextLine = model.activeContext?.let { "Context: $it" }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_brightness)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        contextLine?.let { builder.setSubText(it) }

        if (model.paused) {
            builder.addAction(0, "Resume", actionIntent(ACTION_RESUME))
        } else {
            builder.addAction(0, "Pause", actionIntent(ACTION_PAUSE))
        }
        builder.addAction(0, "Reset", actionIntent(ACTION_PANIC))
        builder.addAction(0, "Disable", actionIntent(ACTION_DISABLE))
        return builder.build()
    }

    private fun actionIntent(action: String): PendingIntent {
        val intent = Intent(this, AmbientMonitoringService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        contextEngine.stop()
        controller.stop()
        LiveRuntimeState.reset()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.tideo.autobrightness.runtime.action.START"
        const val ACTION_PAUSE = "com.tideo.autobrightness.runtime.action.PAUSE"
        const val ACTION_RESUME = "com.tideo.autobrightness.runtime.action.RESUME"
        const val ACTION_DISABLE = "com.tideo.autobrightness.runtime.action.DISABLE"
        const val ACTION_PANIC = "com.tideo.autobrightness.runtime.action.PANIC"
        const val ACTION_REAPPLY = "com.tideo.autobrightness.runtime.action.REAPPLY"
        const val EXTRA_REASON = "reason"
        private const val CHANNEL_ID = "ambient_monitoring"
        private const val NOTIFICATION_ID = 1001
    }
}
