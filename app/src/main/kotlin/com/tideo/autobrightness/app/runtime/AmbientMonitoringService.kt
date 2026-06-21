package com.tideo.autobrightness.app.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.AppModule
import com.tideo.autobrightness.app.storage.settingsDataStore
import com.tideo.autobrightness.app.widget.DashboardWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
    // S12.9e scope audit: legitimately-owned, NOT a leak — the foreground service owns its lifecycle
    // and cancels this scope in onDestroy(). It backs the whole runtime graph for the service lifetime,
    // so it is a dedicated SupervisorJob scope rather than the owner-less AppProcessScope.
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var controller: BrightnessPipelineController
    private lateinit var contextEngine: ContextEngine
    private lateinit var panicSensor: com.tideo.autobrightness.platform.sensor.PanicSensorSource
    private var notificationJob: Job? = null
    private var panicJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Rising-edge latch so the high-priority override alert + toast fire ONCE per override, not on
    // every notification refresh while it stays paused (G2R-F35).
    private var alertedOverride = false

    // SCREEN_ON/OFF are runtime-only broadcasts (not deliverable to manifest receivers).
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> { controller.onScreenOff(); contextEngine.onScreenOff() }
                Intent.ACTION_SCREEN_ON -> onScreenOn()
            }
        }
    }

    /**
     * Display ON (prof761 reinit). In Tasker, waking the screen resumes context automation: the reinit
     * clears the manual context lock (`%AAB_ContextOverride`) so the context rules take over again
     * instead of staying pinned to a manually-loaded profile (owner: screen off→on resumes context
     * automation). Clear the latch first (when set), then run the normal reinit + context re-evaluation.
     */
    private fun onScreenOn() {
        controller.onScreenOn()
        scope.launch {
            if (applicationContext.settingsDataStore.data.first().contextOverride) {
                applicationContext.settingsDataStore.updateData { it.copy(contextOverride = false) }
                contextEngine.reevaluate()
                controller.reapply()
            }
            contextEngine.onScreenOn()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // F75: clear any stale separate override notification left by an older build (pre-fold) so it
        // cannot linger beside the single foreground notification this build now uses.
        getSystemService(NotificationManager::class.java).cancel(OVERRIDE_NOTIFICATION_ID)

        // AppModule composes the real graph (S7 adapters + S9a pipeline + S9b super dimming +
        // S10 context engine); writer and observer share one instance for the suppress-echo
        // marker (D-034), and the pipeline reads its settings through the context engine.
        val runtime = AppModule(applicationContext).createRuntime(scope)
        controller = runtime.controller
        contextEngine = runtime.contextEngine
        panicSensor = runtime.panicSensor

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
            ACTION_RESUME -> {
                // F74: ensureRunning() FIRST. The override Resume action can be delivered to a freshly
                // (re)created service whose pipeline consumer was never start()ed (the paused-override
                // notification persists across a service kill, prof756). Without the running consumer the
                // Resume event sits unconsumed in the channel → the button looks inert. ensureRunning()
                // starts the consumer + sensor + notification job before the Resume event is posted.
                ensureRunning()
                controller.resume()
            }
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
        startPanicDetector()
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
                NotificationModel(
                    state.smoothedLux,
                    state.targetBrightness,
                    state.paused,
                    state.serviceOn,
                    ctx,
                    state.pausedByOverride,
                )
            }
                .distinctUntilChanged()
                .collect { model ->
                    if (!model.serviceOn) return@collect
                    // F75: the override alert is the SAME notification (NOTIFICATION_ID), raised to the
                    // high-priority channel on the rising edge — so it never stacks with a second one.
                    // It pops + buzzes once, then settles back into the ongoing paused/active form on the
                    // next emission. No separate notification ID is ever posted.
                    val rising = model.pausedByOverride && !alertedOverride
                    if (rising) {
                        notifyManualOverride()
                    } else {
                        getSystemService(NotificationManager::class.java)
                            .notify(NOTIFICATION_ID, buildNotification(model))
                    }
                    // QS tile live refresh (G2R-F63): ping the tile so Off→Starting→Active/Paused
                    // renders without the panel being closed+reopened. The tile re-reads the live
                    // LiveRuntimeState/DataStore in onStartListening.
                    requestTileRefresh()
                    // Home-screen widget live refresh: same event-driven path (only on a changed,
                    // accepted cycle — no polling), so the widget tracks Brightness/lux/profile/context.
                    DashboardWidgetProvider.refresh(applicationContext)
                    alertedOverride = model.pausedByOverride
                }
        }
    }

    /**
     * prof769/task528 panic detector (G2R-F77): collect the upside-down + shake gesture and fire the
     * task528 panic — SOS vibration + restore brightness 255 + disable super dimming + service Off.
     */
    private fun startPanicDetector() {
        if (panicJob?.isActive == true) return
        panicJob = scope.launch {
            panicSensor.events().collect {
                vibrateSos()
                panicAndStop()
            }
        }
    }

    /**
     * task528 act0 (code62 Vibrate Pattern): the S.O.S. morse pattern. `setView`-less vibration so the
     * "flash" the owner expects is the SOS buzz + the brightness jump to 255 (task528 act6).
     */
    private fun vibrateSos() {
        val vibrator = getSystemService(android.os.Vibrator::class.java) ?: return
        runCatching {
            vibrator.vibrate(android.os.VibrationEffect.createWaveform(SOS_MORSE_PATTERN, -1))
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
        // Repaint the home-screen widget to its "Off" form now that the loop is gone.
        DashboardWidgetProvider.refresh(applicationContext)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_ambient),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        // Separate HIGH-importance, vibrating channel for the manual-override alert (G2R-F35). The
        // ongoing FGS notification stays on the silent LOW channel; an override heads-up + buzz is a
        // distinct one-shot, mirroring Tasker's Notify+vibrate on a detected manual override.
        manager.createNotificationChannel(
            NotificationChannel(
                OVERRIDE_CHANNEL_ID,
                getString(R.string.notif_channel_override),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200)
            },
        )
    }

    /**
     * Raise the **ongoing** foreground notification (NOTIFICATION_ID) to the high-priority override
     * channel so it pops as a heads-up + buzzes once, and flash a toast (G2R-F35/F75). Because it
     * reuses the foreground notification ID — never a second ID — it can never stack with the ongoing
     * notification; the next emission settles it back to the low-importance paused form (G2R-F40). It
     * keeps the ongoing FGS contract (setOngoing) + the Reset/Disable actions.
     */
    internal fun notifyManualOverride() {
        val alert = NotificationCompat.Builder(this, OVERRIDE_CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_override_title))
            .setContentText(getString(R.string.notif_override_text))
            .setSmallIcon(R.drawable.ic_stat_brightness)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVibrate(longArrayOf(0, 200, 100, 200))
            .addAction(0, getString(R.string.action_resume), actionIntent(ACTION_RESUME))
            .addAction(0, getString(R.string.action_reset), actionIntent(ACTION_PANIC))
            .addAction(0, getString(R.string.action_disable), actionIntent(ACTION_DISABLE))
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, alert)
        // G2R-F91: route the override flash through the shared teal [AabFlash] operational surface
        // (global a11y overlay → in-app pill → Toast fallback), consistent with the profile/context-load
        // flashes — not a bare, non-tappable Toast that can stack independently.
        mainHandler.post {
            AabFlash.show(this, getString(R.string.flash_manual_override))
        }
    }

    /** Ask the OS to refresh our QS tile (G2R-F63); no-op if the tile isn't added. */
    private fun requestTileRefresh() {
        runCatching {
            TileService.requestListeningState(
                applicationContext,
                ComponentName(applicationContext, BrightnessTileService::class.java),
            )
        }
    }

    private data class NotificationModel(
        val smoothedLux: Double? = null,
        val targetBrightness: Int? = null,
        val paused: Boolean = false,
        val serviceOn: Boolean = true,
        val activeContext: String? = null,
        val pausedByOverride: Boolean = false,
    )

    private fun buildNotification(model: NotificationModel): Notification {
        // Without WRITE_SETTINGS the loop runs but every brightness write is a no-op (G1-F1);
        // surface why nothing is changing instead of looking silently broken.
        val canWrite = android.provider.Settings.System.canWrite(this)
        val title = when {
            !canWrite -> getString(R.string.notif_title_permission_needed)
            model.paused -> getString(R.string.notif_title_paused)
            else -> getString(R.string.notif_title_active)
        }
        val text = when {
            !canWrite -> getString(R.string.notif_text_grant_write)
            model.paused -> getString(R.string.notif_text_paused)
            model.smoothedLux != null && model.targetBrightness != null ->
                getString(R.string.notif_text_lux_brightness, model.smoothedLux.toInt(), model.targetBrightness)
            else -> getString(R.string.notif_text_monitoring)
        }
        // Surface the active context override (%AAB_ActiveContext) as a second line when one is on.
        val contextLine = model.activeContext?.let { getString(R.string.notif_subtext_context, it) }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_brightness)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        contextLine?.let { builder.setSubText(it) }

        // F76: NO Pause action on the ongoing notification — pausing from here behaved like a manual
        // override and confused users; to stop, disable the service. Resume is still offered while
        // paused (after a real override); Reset (panic) + Disable remain.
        if (model.paused) {
            builder.addAction(0, getString(R.string.action_resume), actionIntent(ACTION_RESUME))
        }
        builder.addAction(0, getString(R.string.action_reset), actionIntent(ACTION_PANIC))
        builder.addAction(0, getString(R.string.action_disable), actionIntent(ACTION_DISABLE))
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

    /**
     * Swiped from recents (S12.9d). The system may keep a START_STICKY FGS alive and recreate it; arm
     * the staleness watchdog rather than wiping the live state immediately so the UI does not flicker
     * to "no data" if a fresh instance republishes within the grace window.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        armStalenessWatchdog()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        panicJob?.cancel(); panicJob = null
        contextEngine.stop()
        controller.stop()
        // Watchdog instead of an immediate reset (S12.9d): if the OS restarts the FGS and it
        // republishes within the grace window, the live data survives; otherwise it is cleared so the
        // Dashboard does not show a stale "live" snapshot for a dead loop. A genuine user-driven stop
        // (tearDownDisabled) already reset immediately, so this only softens system-driven teardowns.
        armStalenessWatchdog()
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Reset [LiveRuntimeState] after [WATCHDOG_GRACE_MS] unless a newer publish (e.g. from a restarted
     * service instance) arrived in the meantime. Posted on the main handler so it outlives [scope]'s
     * cancellation during teardown.
     */
    private fun armStalenessWatchdog() {
        val armedAt = System.currentTimeMillis()
        mainHandler.postDelayed({
            val lastPublish = LiveRuntimeState.pipeline.value.lastPublishMs
            if (lastPublish == null || lastPublish < armedAt) {
                LiveRuntimeState.reset()
                DashboardWidgetProvider.refresh(applicationContext)
            }
        }, WATCHDOG_GRACE_MS)
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
        private const val OVERRIDE_CHANNEL_ID = "manual_override"
        private const val NOTIFICATION_ID = 1001
        private const val OVERRIDE_NOTIFICATION_ID = 1002
        // S12.9d: grace before the staleness watchdog wipes LiveRuntimeState on a system teardown.
        private const val WATCHDOG_GRACE_MS = 5_000L

        // task528 act0 (code62): S.O.S. in morse code. Tasker arg0 was
        // "0,100,100,100,100,100,300,300,100,300,100,300,300,100,100,10" — the same on/off durations
        // (ms) as a VibrationEffect waveform (index 0 = initial off delay).
        private val SOS_MORSE_PATTERN = longArrayOf(
            0, 100, 100, 100, 100, 100, // S: dot dot dot
            300, 300, 100, 300, 100, 300, // O: dash dash dash
            300, 100, 100, 10, // S: dot dot dot (trailing)
        )
    }
}
