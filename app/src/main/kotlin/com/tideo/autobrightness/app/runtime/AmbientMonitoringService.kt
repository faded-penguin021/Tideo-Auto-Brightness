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
import com.tideo.autobrightness.app.control.ControlPrefsStore
import com.tideo.autobrightness.app.storage.controlPrefsDataStore
import com.tideo.autobrightness.app.storage.settingsDataStore
import com.tideo.autobrightness.app.widget.DashboardWidgetProvider
import com.tideo.autobrightness.platform.display.ForceDarkController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.filterNotNull
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
    // Legitimately-owned scope for the service lifetime, cancelled in onDestroy().
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var controller: BrightnessPipelineController
    private lateinit var contextEngine: ContextEngine
    private lateinit var displayToggles: DisplayTogglesCoordinator
    private lateinit var panicSensor: com.tideo.autobrightness.platform.sensor.PanicSensorSource
    // Tier cache, refreshed at resume points to avoid per-cycle permission checks (G1-F5).
    private lateinit var privilegeManager: com.tideo.autobrightness.platform.privilege.PrivilegeManager
    private var notificationJob: Job? = null
    private var panicJob: Job? = null
    // DB-009: watches %AAB_PanicPlugged so a toggle change re-evaluates the sensor gate at once.
    private var panicGateJob: Job? = null
    // D-157: outbound STATE_CHANGED publisher + cached opt-in flag for onDestroy's final event.
    private var stateEventJob: Job? = null
    // D-157: internal for testing; visible to onDestroy for the final off-state decision.
    @Volatile internal var externalControlEnabled = false
    // D-172: one-shot per instance; kept non-null to prevent re-bind on re-entries.
    private var forceDarkJob: Job? = null
    // DA-030: null-intent START_STICKY gate; generation counter prevents stale reads from starting.
    private var stickyRestartGateJob: Job? = null
    private var stickyRestartGeneration = 0L
    private var destroyed = false
    // DA-030: test seam for sticky-restart read. Production reads the persisted setting.
    internal var stickyRestartEnabledReader: suspend () -> Boolean = {
        applicationContext.settingsDataStore.data.first().serviceEnabled
    }
    internal var runtimeStartCount = 0
        private set
    private var runtimeStarted = false
    private val mainHandler = Handler(Looper.getMainLooper())

    // Rising-edge latch for override alert (G2R-F35).
    private var alertedOverride = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    controller.onScreenOff(); contextEngine.onScreenOff()
                }
                Intent.ACTION_SCREEN_ON -> onScreenOn()
            }
        }
    }

    /**
     * Display ON (prof761 reinit): clear context lock, rescan privileges, reinit pipeline.
     */
    private fun onScreenOn() {
        // Re-detect privilege tier on wake so out-of-band grants (ADB/Shizuku) are picked up (G1-F5).
        privilegeManager.refresh()
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
        // F75: clear stale override notification from older builds.
        getSystemService(NotificationManager::class.java).cancel(OVERRIDE_NOTIFICATION_ID)

        // AppModule composes the runtime graph; shared instance for suppress-echo (D-034).
        val runtime = AppModule(applicationContext).createRuntime(scope)
        controller = runtime.controller
        contextEngine = runtime.contextEngine
        displayToggles = runtime.displayToggles
        panicSensor = runtime.panicSensor
        privilegeManager = runtime.privilegeManager

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

        // A real command supersedes a pending OS restart; explicit starters pre-persist serviceEnabled.
        if (intent != null) {
            stickyRestartGeneration++
            stickyRestartGateJob?.cancel()
            stickyRestartGateJob = null
        }

        when (intent?.action) {
            ACTION_PAUSE -> {
                // D-140: PAUSE on fresh instance with no running pipeline → stop instead.
                if (!controller.state.value.serviceOn) return stopNotRunning(startId)
                controller.pause()
            }
            ACTION_RESUME -> {
                // F74: ensureRunning() first so fresh instance can consume the Resume event.
                // Deliberately NOT D-140-gated: Resume's contract is to resurrect.
                ensureRunning()
                controller.resume()
            }
            ACTION_REAPPLY -> {
                // D-140: REAPPLY on not-running instance must not birth the pipeline.
                if (!controller.state.value.serviceOn) return stopNotRunning(startId)
                // G2-F16: re-run the pipeline; ensure safe delivery if service is up.
                ensureRunning()
                // Refresh effective settings before re-applying (G2R-F11/F12).
                scope.launch {
                    contextEngine.reevaluate()
                    controller.reapply()
                }
            }
            ACTION_RESUME_CONTEXT -> {
                // DA-018: resume context automation; same D-140 not-running gate as REAPPLY.
                if (!controller.state.value.serviceOn) return stopNotRunning(startId)
                ensureRunning()
                // Tasker _ContextResume → evaluate contexts → Set Initial Brightness: a GENUINE
                // evaluate(RESUME), not REAPPLY's republish-only path, so the resolver re-runs and
                // the store stops diverging from the active-profile label.
                scope.launch {
                    contextEngine.resumeContextAutomation()
                    controller.reapply()
                }
            }
            ACTION_PANIC -> {
                // task528: full stop (not pausable), restore brightness, tear down service.
                scope.launch { panicAndStop() }
                return START_NOT_STICKY
            }
            ACTION_DISABLE -> {
                scope.launch { disableAndStop() }
                return START_NOT_STICKY
            }
            else -> {
                if (intent != null) {
                    // DB-005: unrecognized action must not start the runtime.
                    if (intent.action != null && intent.action != ACTION_START) {
                        return stopNotRunning(startId)
                    }
                    // DA-030: explicit start keeps synchronous path; every starter pre-persists serviceEnabled.
                    ensureRunning()
                } else {
                    // DA-030: null intent gate; OS restart defers ensureRunning() behind a DataStore read.
                    stickyRestartGateJob?.cancel()
                    val generation = ++stickyRestartGeneration
                    stickyRestartGateJob = scope.launch {
                        val enabled = try {
                            stickyRestartEnabledReader()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // Corrupt store fails closed to avoid stranding an FGS indefinitely.
                            false
                        }
                        // Serialize decision on main thread; generation check authorizes supersession/destruction.
                        mainHandler.post {
                            if (destroyed || stickyRestartGeneration != generation) return@post
                            stickyRestartGateJob = null
                            if (enabled) {
                                ensureRunning()
                            } else {
                                // NOT D-140's disableAndStop(): that persists serviceEnabled = false,
                                // which would turn a transient read failure into a write, and its
                                // LiveRuntimeState.reset() + widget repaint are moot when the runtime
                                // never started.
                                stopNotRunning(startId)
                            }
                        }
                    }
                }
            }
        }
        return START_STICKY
    }

    /**
     * D-140: control intent on not-running service; drop notification and stop NOT_STICKY.
     */
    private fun stopNotRunning(startId: Int): Int {
        // controller.stop() is recovery-safe; clears residues without starting a sensor.
        controller.stop()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun ensureRunning() {
        // DA-030: runtimeStarted latches; runtimeStartCount must NOT (tests distinguish activations).
        runtimeStarted = true
        runtimeStartCount++
        // Refresh tier cache at resume points so out-of-band grants are reflected (G1-F5).
        privilegeManager.refresh()
        controller.start()
        contextEngine.start(scope)
        displayToggles.start(scope)
        startPanicDetector()
        startStateEventPublisher()
        startForceDarkReapply()
        if (notificationJob?.isActive == true) return
        val manualOverrideFlow = applicationContext.settingsDataStore.data
            .map { it.contextOverride }
            .distinctUntilChanged()
        notificationJob = scope.launch {
            combine(controller.state, contextEngine.activeContext, manualOverrideFlow) { state, ctx, manualOverride ->
                contextEngine.onPipelineTick()
                // Republish for Dashboard/Menu; separate override lock from active context rule (F46).
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
                    // F75: override alert reuses NOTIFICATION_ID; pops once then settles back.
                    val rising = model.pausedByOverride && !alertedOverride
                    if (rising) {
                        notifyManualOverride()
                    } else {
                        getSystemService(NotificationManager::class.java)
                            .notify(NOTIFICATION_ID, buildNotification(model))
                    }
                    // G2R-F63: QS tile live refresh; renders state changes without panel close+reopen.
                    requestTileRefresh()
                    // Home-screen widget live refresh: event-driven (no polling).
                    DashboardWidgetProvider.refresh(applicationContext)
                    alertedOverride = model.pausedByOverride
                }
        }
    }

    /**
     * D-172: assert debug.hwui.force_dark once per instance; fire-and-forget, retries on next start.
     */
    private fun startForceDarkReapply() {
        if (forceDarkJob != null) return
        forceDarkJob = scope.launch {
            if (ControlPrefsStore(applicationContext.controlPrefsDataStore).forceDarkEnabled.first()) {
                ForceDarkController.apply(applicationContext, enabled = true)
            }
        }
    }

    /**
     * prof769/task528: collect upside-down + shake gesture and fire panic (G2R-F77).
     */
    private fun startPanicDetector() {
        if (panicJob?.isActive == true) return
        panicJob = scope.launch {
            // DB-011: wait for effective settings; %AAB_PanicPlugged is null until first context eval.
            contextEngine.effectiveFlow.filterNotNull().first()
            panicSensor.events().collect {
                vibrateSos()
                panicAndStop()
            }
        }
        startPanicGateWatcher()
    }

    /**
     * DB-009: restart panic collector when %AAB_PanicPlugged changes; re-evaluates gate immediately.
     */
    private fun startPanicGateWatcher() {
        if (panicGateJob?.isActive == true) return
        panicGateJob = scope.launch {
            contextEngine.effectiveFlow
                .map { it?.panicRequiresPlugged ?: false }
                .distinctUntilChanged()
                .drop(1) // the first emission is the state the collector already started under
                .collect {
                    panicJob?.cancelAndJoin()
                    panicJob = scope.launch {
                        panicSensor.events().collect {
                            vibrateSos()
                            panicAndStop()
                        }
                    }
                }
        }
    }

    /**
     * D-157 (U5): outbound event.STATE_CHANGED broadcasts; opt-in gated by externalControlEnabled.
     * Running snapshots only (distinct-until-changed); onDestroy emits the authoritative OFF event.
     */
    private fun startStateEventPublisher() {
        if (stateEventJob?.isActive == true) return
        val controlEnabledFlow = ControlPrefsStore(applicationContext.controlPrefsDataStore).externalControlEnabled
        stateEventJob = scope.launch {
            // LiveRuntimeState.activeProfile: profile name (manual or by context rule); shared with Dashboard.
            combine(
                controlEnabledFlow,
                controller.state,
                LiveRuntimeState.activeProfile,
            ) { enabled, state, profile ->
                // Cache opt-in for onDestroy's final off-state.
                externalControlEnabled = enabled
                outboundSnapshot(enabled, state, profile)
            }
                .distinctUntilChanged()
                .collect { snap ->
                    snap?.let { broadcastStateChanged(it.enabled, it.running, it.paused, it.profile) }
                }
        }
    }

    /**
     * D-157 U5: send public ACTION_STATE_CHANGED broadcast; global so third-party receivers pick it up.
     */
    private fun broadcastStateChanged(enabled: Boolean, running: Boolean, paused: Boolean, profile: String?) {
        sendBroadcast(buildStateChangedIntent(enabled, running, paused, profile))
    }

    /**
     * D-157: running snapshot or null if opt-in off or pipeline down; extracted for deterministic testing.
     */
    internal fun outboundSnapshot(enabled: Boolean, state: PipelineState, profile: String?): OutboundState? =
        if (!enabled || !state.serviceOn) {
            null
        } else {
            OutboundState(
                enabled = true,
                running = !state.paused, // actively adjusting = on AND not paused
                paused = state.paused,
                profile = profile,
            )
        }

    /** Extracted for deterministic contract testing. */
    internal fun buildStateChangedIntent(enabled: Boolean, running: Boolean, paused: Boolean, profile: String?): Intent =
        Intent(ACTION_STATE_CHANGED).apply {
            putExtra(EXTRA_ENABLED, enabled)
            putExtra(EXTRA_RUNNING, running)
            putExtra(EXTRA_PAUSED, paused)
            putExtra(EXTRA_PROFILE, profile)
        }

    /** D-157 U5: distinct-until-changed outbound snapshot. */
    internal data class OutboundState(
        val enabled: Boolean,
        val running: Boolean,
        val paused: Boolean,
        val profile: String?,
    )

    /**
     * task528 act0: S.O.S. morse pattern (code62 Vibrate Pattern).
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
        controller.emergencyStop() // restore 255 + drop dimming (task528)
        // D-155: return display toggles to DEFAULTS; run before displayToggles.stop().
        displayToggles.panicReset()
        tearDownDisabled()
    }

    private suspend fun tearDownDisabled() {
        applicationContext.settingsDataStore.updateData { it.copy(serviceEnabled = false) }
        LiveRuntimeState.reset()
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
        // G2R-F35: separate HIGH-importance vibrating channel for manual-override alert.
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
     * G2R-F35/F75: raise ongoing notification to override channel; pops as heads-up and buzzes once.
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
        // G2R-F91: route through shared AabFlash surface (overlay → pill → Toast fallback).
        mainHandler.post {
            AabFlash.show(this, getString(R.string.flash_manual_override))
        }
    }

    /** G2R-F63: request QS tile refresh; no-op if not added. */
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
        // G1-F1: surface permission issue instead of looking silently broken.
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
        val contextLine = model.activeContext?.let { getString(R.string.notif_subtext_context, it) }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_brightness)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        contextLine?.let { builder.setSubText(it) }

        // F76: NO Pause action (confused users); Resume/Reset/Disable only.
        if (model.paused) {
            builder.addAction(0, getString(R.string.action_resume), actionIntent(ACTION_RESUME))
        }
        builder.addAction(0, getString(R.string.action_reset), actionIntent(ACTION_PANIC))
        builder.addAction(0, getString(R.string.action_disable), actionIntent(ACTION_DISABLE))
        return builder.build()
    }

    private fun actionIntent(action: String): PendingIntent {
        // CWE-927: explicit Intent; component + package on separate statements for CodeQL.
        val intent = Intent(this, AmbientMonitoringService::class.java)
        intent.setPackage(packageName)
        intent.action = action
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * S12.9d: arm staleness watchdog to prevent UI flicker on FGS recreation.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        armStalenessWatchdog()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // D-157 (U5): single authoritative OFF event emitted before scope.cancel() (covers all stop paths).
        if (externalControlEnabled) broadcastStateChanged(enabled = false, running = false, paused = false, profile = null)
        runCatching { unregisterReceiver(screenReceiver) }
        destroyed = true
        stickyRestartGeneration++
        stickyRestartGateJob?.cancel(); stickyRestartGateJob = null
        panicJob?.cancel(); panicJob = null
        panicGateJob?.cancel(); panicGateJob = null
        stateEventJob?.cancel(); stateEventJob = null
        if (runtimeStarted) {
            contextEngine.stop()
            controller.stop()
            // D-151: return toggles to baseline (override must not outlive the runtime).
            displayToggles.stop()
        }
        // S12.9d: watchdog instead of immediate reset (survives FGS recreation within grace window).
        armStalenessWatchdog()
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Reset LiveRuntimeState after WATCHDOG_GRACE_MS unless a newer publish arrived.
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
        // DA-018: resume context automation (genuine eval, not republish-only).
        const val ACTION_RESUME_CONTEXT = "com.tideo.autobrightness.runtime.action.RESUME_CONTEXT"
        const val EXTRA_REASON = "reason"

        // D-157 (U5): public event.STATE_CHANGED for automation frameworks; event.* namespace.
        const val ACTION_STATE_CHANGED = "com.tideo.autobrightness.event.STATE_CHANGED"
        const val EXTRA_ENABLED = "enabled" // Boolean: the service/pipeline is on
        const val EXTRA_RUNNING = "running" // Boolean: actively adjusting (on AND not paused)
        const val EXTRA_PAUSED = "paused" // Boolean: paused
        const val EXTRA_PROFILE = "profile" // String?: the profile name in force, or absent/null
        private const val CHANNEL_ID = "ambient_monitoring"
        private const val OVERRIDE_CHANNEL_ID = "manual_override"
        private const val NOTIFICATION_ID = 1001
        private const val OVERRIDE_NOTIFICATION_ID = 1002
        // S12.9d: grace for staleness watchdog on system teardown.
        private const val WATCHDOG_GRACE_MS = 5_000L

        // task528 act0 (code62): S.O.S. morse (Tasker arg0 on/off durations in ms, index 0 = delay).
        private val SOS_MORSE_PATTERN = longArrayOf(
            0, 100, 100, 100, 100, 100, // S: dot dot dot
            300, 300, 100, 300, 100, 300, // O: dash dash dash
            300, 100, 100, 10, // S: dot dot dot (trailing)
        )
    }
}
