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
    private lateinit var displayToggles: DisplayTogglesCoordinator
    private lateinit var panicSensor: com.tideo.autobrightness.platform.sensor.PanicSensorSource
    // Shared tier cache. Refreshed only at resume points (start + screen-on) so the dimming
    // coordinator can read the cached tier per cycle instead of re-checking the permission (G1-F5).
    private lateinit var privilegeManager: com.tideo.autobrightness.platform.privilege.PrivilegeManager
    private var notificationJob: Job? = null
    private var panicJob: Job? = null
    // DB-009: watches %AAB_PanicPlugged so a toggle change re-evaluates the sensor gate at once.
    private var panicGateJob: Job? = null
    // D-157 (U5): outbound STATE_CHANGED publisher + a cache of the opt-in flag. The cache lets
    // onDestroy() decide synchronously whether to emit the final off-state event (its DataStore read
    // would be async, and the scope is being torn down). Written by the publisher on Dispatchers.Default,
    // read on the main thread in onDestroy → @Volatile for visibility (S12.9e volatile convention).
    private var stateEventJob: Job? = null
    // internal (not private) so the onDestroy final-off-event test can set the cache deterministically
    // without driving the Dispatchers.Default publisher.
    @Volatile internal var externalControlEnabled = false
    // D-172: one-shot per service instance (kept non-null after completion so ensureRunning's
    // re-entries — resume/reapply — never re-bind Shizuku).
    private var forceDarkJob: Job? = null
    // DA-030 (extends D-140): state for the null-intent START_STICKY gate. The generation counter is
    // bumped by every command AND by onDestroy, and re-checked on the main thread after the read
    // returns — cancel() alone cannot stop a coroutine that has already passed its last suspension,
    // so cancellation is necessary but not sufficient to keep a stale read from starting the runtime.
    private var stickyRestartGateJob: Job? = null
    private var stickyRestartGeneration = 0L
    private var destroyed = false
    // DA-030: test seam for holding the sticky-restart DataStore read in flight (same precedent as
    // externalControlEnabled below). Production always reads the persisted setting; a real read
    // completes before a test can interleave the superseding command the race is about.
    internal var stickyRestartEnabledReader: suspend () -> Boolean = {
        applicationContext.settingsDataStore.data.first().serviceEnabled
    }
    internal var runtimeStartCount = 0
        private set
    private var runtimeStarted = false
    private val mainHandler = Handler(Looper.getMainLooper())

    // Rising-edge latch so the high-priority override alert + toast fire ONCE per override, not on
    // every notification refresh while it stays paused (G2R-F35).
    private var alertedOverride = false

    // SCREEN_ON/OFF are runtime-only broadcasts (not deliverable to manifest receivers).
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
     * Display ON (prof761 reinit). In Tasker, waking the screen resumes context automation: the reinit
     * clears the manual context lock (`%AAB_ContextOverride`) so the context rules take over again
     * instead of staying pinned to a manually-loaded profile (owner: screen off→on resumes context
     * automation). Clear the latch first (when set), then run the normal reinit + context re-evaluation.
     */
    private fun onScreenOn() {
        // Re-detect the privilege tier on wake so a WRITE_SECURE_SETTINGS grant made while the service
        // was running (ADB/Shizuku) is picked up here, rather than on every dimming cycle (G1-F5).
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
        // F75: clear any stale separate override notification left by an older build (pre-fold) so it
        // cannot linger beside the single foreground notification this build now uses.
        getSystemService(NotificationManager::class.java).cancel(OVERRIDE_NOTIFICATION_ID)

        // AppModule composes the real graph (S7 adapters + S9a pipeline + S9b super dimming +
        // S10 context engine); writer and observer share one instance for the suppress-echo
        // marker (D-034), and the pipeline reads its settings through the context engine.
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

        // A real command supersedes a pending OS-restart decision. In particular ACTION_START is
        // trusted because every explicit starter persists serviceEnabled before sending it, so it
        // retains the existing synchronous startup latency and semantics.
        if (intent != null) {
            stickyRestartGeneration++
            stickyRestartGateJob?.cancel()
            stickyRestartGateJob = null
        }

        when (intent?.action) {
            ACTION_PAUSE -> {
                // D-140: startForegroundService CREATES the service, so a PAUSE aimed at "the running
                // service" can land on a fresh instance whose pipeline was never start()ed. Nothing to
                // pause → stop instead of idling forever as a foregrounded zombie.
                if (!controller.state.value.serviceOn) return stopNotRunning(startId)
                controller.pause()
            }
            ACTION_RESUME -> {
                // F74: ensureRunning() FIRST. The override Resume action can be delivered to a freshly
                // (re)created service whose pipeline consumer was never start()ed (the paused-override
                // notification persists across a service kill, prof756). Without the running consumer the
                // Resume event sits unconsumed in the channel → the button looks inert. ensureRunning()
                // starts the consumer + sensor + notification job before the Resume event is posted.
                // (Deliberately NOT D-140-gated: Resume is the one action whose contract is resurrect.)
                ensureRunning()
                controller.resume()
            }
            ACTION_REAPPLY -> {
                // D-140: a REAPPLY landing on a not-running instance (widget Reset while the service is
                // off — startForegroundService creates the service) must not birth the pipeline; the
                // documented contract is "no-op when the service is not running; the next start picks up
                // the committed settings" (AutoBrightnessRuntime.reapply). Previously this branch
                // ensureRunning()'d unconditionally, starting the light-sensor collector + FGS zombie
                // against the persisted disable.
                if (!controller.state.value.serviceOn) return stopNotRunning(startId)
                // Settings Apply / profile load: re-run the pipeline now (G2-F16). ensureRunning()
                // so an Apply made while the service is up (but this start re-delivers) is safe.
                ensureRunning()
                // Refresh the effective settings from the FRESH baseline BEFORE re-applying, so a
                // manual DataStore edit (e.g. min-brightness) takes effect immediately rather than
                // using the stale context snapshot (G2R-F11/F12).
                scope.launch {
                    contextEngine.reevaluate()
                    controller.reapply()
                }
            }
            ACTION_RESUME_CONTEXT -> {
                // DA-018: "Resume context automation" (Profiles banner / CONTEXTS_RESUME). Same D-140
                // not-running gate as REAPPLY — a Resume aimed at a dead pipeline must not birth a zombie
                // (the manual lock is already cleared in the store; the next start evaluates fresh).
                if (!controller.state.value.serviceOn) return stopNotRunning(startId)
                ensureRunning()
                // Tasker _ContextResume → evaluate contexts → Set Initial Brightness: a GENUINE
                // evaluate(RESUME) so a currently-matching rule applies NOW / a no-match reverts to
                // %AAB_ProfileUser, THEN reapply (Set Initial Brightness). Unlike REAPPLY (reevaluate
                // only republishes), this actually re-runs the resolver so the store + active-profile
                // label stop diverging (the owner-reported staleness after Resume).
                scope.launch {
                    contextEngine.resumeContextAutomation()
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
            else -> {
                if (intent != null) {
                    // DB-005: an unrecognised action must not be a start command. This branch used to
                    // treat "any non-null intent" as ACTION_START, so a typo'd or future action name
                    // silently started the whole runtime — fail-open dispatch on a service that the
                    // component audit already flags as export-risky if it is ever exported.
                    // (No log line: this file deliberately carries none, and the action string is
                    // caller-supplied text.)
                    if (intent.action != null && intent.action != ACTION_START) {
                        return stopNotRunning(startId)
                    }
                    // DA-030: an explicit start keeps the synchronous path and skips the read. Every
                    // explicit starter already establishes serviceEnabled first — BootCompletedReceiver
                    // and MaintenanceWorker read it before sending, ControlReceiver/tile/widget
                    // updateData it before sending. A NEW ACTION_START sender must do the same or it
                    // does not belong on this branch.
                    ensureRunning()
                } else {
                    // DA-030, extending the D-140 defense for the START_STICKY hole (D-140 called
                    // ensureRunning() first and undid it after, so a disabled service still briefly
                    // ran sensors, the brightness writer and the display-toggle writes):
                    // an OS restart supplies a null intent,
                    // and the user may have disabled the service while its old process was dead. The
                    // foreground notification above satisfies the FGS deadline, but NOTHING in the
                    // runtime graph starts until DataStore confirms the persisted opt-in. A later null
                    // delivery replaces this decision; the startId paired with the winning read is the
                    // one stopped on the disabled path.
                    stickyRestartGateJob?.cancel()
                    val generation = ++stickyRestartGeneration
                    stickyRestartGateJob = scope.launch {
                        val enabled = try {
                            stickyRestartEnabledReader()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // A corrupt/unreadable store must fail closed rather than strand an empty
                            // START_STICKY foreground service indefinitely.
                            false
                        }
                        // Lifecycle commands are delivered on main. Serialize the decision there too:
                        // cancel() alone cannot stop a coroutine that has returned from its last
                        // suspension, whereas this generation check makes supersession/destruction
                        // authoritative even in that completion race.
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
     * D-140: this instance was created BY a control intent (pause/reapply) while no pipeline was
     * running — there is nothing to control. Drop the foreground notification and stop; NOT_STICKY so
     * the OS does not resurrect the zombie. startForeground was already called above (mandatory after
     * a startForegroundService), so the notification only blips.
     */
    private fun stopNotRunning(startId: Int): Int {
        // A fresh instance may be recovering after process death while the old process left MANUAL
        // mode or Extra Dim behind. The graph did not start, but controller.stop() is deliberately
        // recovery-safe and clears those persisted/unknown residues without starting a sensor.
        controller.stop()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun ensureRunning() {
        // DA-030: runtimeStarted LATCHES (onDestroy only needs "was the graph ever up"), but the
        // counter must NOT — a latched counter cannot tell one activation from two, which is exactly
        // what the supersession tests claim to prove.
        runtimeStarted = true
        runtimeStartCount++
        // Refresh the cached tier at the service-resume points (start / resume / reapply) so an
        // out-of-band grant is reflected without the per-cycle permission check the dimming path used
        // to do (G1-F5). currentTier() reads this cache thereafter.
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
     * D-172: re-assert the force-dark prop (`debug.hwui.force_dark`) once per service instance —
     * the prop resets on every reboot, so the boot-started service restores the user's saved choice.
     * Gated on the persisted opt-in FIRST (while off this is a cheap DataStore read — no Shizuku
     * bind, no su spawn, the app never touches the property). Only TRUE is ever asserted: an off
     * opt-in means "leave the property alone", not "force it off" — the user may drive it via
     * developer options. Fire-and-forget: with no privileged shell yet (Shizuku typically isn't up
     * right after boot; the root fallback usually is) apply() returns null and the next service
     * start retries; the Tools card shows the live truth meanwhile.
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
        startPanicGateWatcher()
    }

    /**
     * DB-009: restart the panic collector when `%AAB_PanicPlugged` changes.
     *
     * The source decides whether to hold the accelerometer registered, and it re-decides on screen and
     * power transitions — neither of which happens when the user simply flips the toggle. Without this,
     * turning the restriction OFF while unplugged with the screen on would leave the gesture inert
     * until the next screen-off, i.e. exactly when someone is standing there testing it. Restarting the
     * flow re-evaluates the gate immediately; it is a cheap cancel + re-collect, not a runtime restart.
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
     * D-157 (U5): outbound `event.STATE_CHANGED` broadcasts so automation frameworks can *react* to
     * Tideo (pause/resume, profile change, service on/off), not just command it. Opt-in — gated by the
     * SAME `ControlPrefsStore.externalControlEnabled` flag as the inbound `ControlReceiver`; while off,
     * nothing is emitted (and the [externalControlEnabled] cache stays false, so onDestroy also stays
     * silent). Combining the flag flow in means flipping the toggle ON re-emits the current snapshot.
     *
     * Only *running* snapshots are published here (`serviceOn` true), distinct-until-changed; the single
     * authoritative OFF event is emitted by [onDestroy] (which covers every stop path — SERVICE_OFF
     * toggle, Disable, Panic — uniformly, unlike the collector, which the scope teardown races).
     */
    private fun startStateEventPublisher() {
        if (stateEventJob?.isActive == true) return
        val controlEnabledFlow = ControlPrefsStore(applicationContext.controlPrefsDataStore).externalControlEnabled
        stateEventJob = scope.launch {
            // LiveRuntimeState.activeProfile is the name of the profile in force whether loaded manually
            // (LOAD_PROFILE / Profiles UI) or by a context rule (F46) — exactly what automation wants for
            // the `profile` extra, and already the single source the Dashboard uses.
            combine(
                controlEnabledFlow,
                controller.state,
                LiveRuntimeState.activeProfile,
            ) { enabled, state, profile ->
                // Cache the opt-in flag for onDestroy's final off-state decision (see the field doc).
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
     * Send the public [ACTION_STATE_CHANGED] broadcast (D-157 U5). Global (no `setPackage`) so a
     * third-party automation app's receiver can pick it up — the whole point of the event. It carries
     * only low-sensitivity liveness flags and is emitted solely while the user has opted in, so no
     * permission gate (plan: no new manifest permission).
     */
    private fun broadcastStateChanged(enabled: Boolean, running: Boolean, paused: Boolean, profile: String?) {
        sendBroadcast(buildStateChangedIntent(enabled, running, paused, profile))
    }

    /**
     * The distinct-until-changed running snapshot, or null to publish nothing: while the opt-in is off,
     * or while the pipeline is not up (the OFF transition is [onDestroy]'s single authoritative event,
     * not the collector's). Pure — extracted so it is deterministically unit-tested without driving the
     * Dispatchers.Default collector.
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

    /** Build the public [ACTION_STATE_CHANGED] intent. Extracted for a deterministic contract test. */
    internal fun buildStateChangedIntent(enabled: Boolean, running: Boolean, paused: Boolean, profile: String?): Intent =
        Intent(ACTION_STATE_CHANGED).apply {
            putExtra(EXTRA_ENABLED, enabled)
            putExtra(EXTRA_RUNNING, running)
            putExtra(EXTRA_PAUSED, paused)
            putExtra(EXTRA_PROFILE, profile)
        }

    /** The distinct-until-changed outbound snapshot (D-157 U5). */
    internal data class OutboundState(
        val enabled: Boolean,
        val running: Boolean,
        val paused: Boolean,
        val profile: String?,
    )

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
        // D-155: panic also returns the privileged display toggles to their DEFAULTS (not the
        // baseline — it may itself carry grayscale/inversion/Night Light). Must run before the
        // teardown reaches onDestroy's displayToggles.stop(), which would re-apply the baseline;
        // panicReset() tears the coordinator down so that stop() finds it already stopped.
        displayToggles.panicReset()
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
        // Keep the wrapped Intent unambiguously EXPLICIT (CWE-927 / java/android/implicit-pendingintents):
        // target our own service by component AND package, set on their own statements. The previous
        // one-liner `Intent(this, X).setAction(action)` is explicit at runtime, but CodeQL's Kotlin
        // dataflow does not carry the constructor component through the chained `.setAction()` and
        // flagged it as an implicit PendingIntent. FLAG_IMMUTABLE already prevents tampering.
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
     * Swiped from recents (S12.9d). The system may keep a START_STICKY FGS alive and recreate it; arm
     * the staleness watchdog rather than wiping the live state immediately so the UI does not flicker
     * to "no data" if a fresh instance republishes within the grace window.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        armStalenessWatchdog()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // D-157 (U5): the single authoritative outbound OFF event, emitted BEFORE scope.cancel() tears
        // down the publisher (D-139-class ordering). onDestroy is the one exit common to EVERY stop path
        // — SERVICE_OFF toggle (stopService), Disable, Panic — so automation always sees a final off,
        // where the collector alone would be raced by the teardown. Gated by the cached opt-in flag so
        // nothing leaks when external control was never enabled. (A system-driven kill+restart shows a
        // brief off→on; that is honest and self-corrects, as the LiveRuntimeState watchdog already
        // assumes for onDestroy.)
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
            // Return the display toggles to the baseline profile's values (D-151 resting state): a
            // context-profile override must not outlive the runtime that applies it. Before
            // scope.cancel() so the coordinator can serialize against an in-flight apply.
            displayToggles.stop()
        }
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
        // DA-018: resume context automation — a genuine context re-evaluation (evaluate(RESUME)) then
        // Set Initial Brightness, distinct from REAPPLY's republish-only path.
        const val ACTION_RESUME_CONTEXT = "com.tideo.autobrightness.runtime.action.RESUME_CONTEXT"
        const val EXTRA_REASON = "reason"

        // D-157 (U5): the PUBLIC outbound event contract for automation frameworks (Tasker / MacroDroid).
        // Distinct `event.*` namespace from the inbound `control.*` (ControlReceiver) and the internal
        // `runtime.action.*` above. Emitted only while ControlPrefsStore.externalControlEnabled is on.
        const val ACTION_STATE_CHANGED = "com.tideo.autobrightness.event.STATE_CHANGED"
        const val EXTRA_ENABLED = "enabled" // Boolean: the service/pipeline is on
        const val EXTRA_RUNNING = "running" // Boolean: actively adjusting (on AND not paused)
        const val EXTRA_PAUSED = "paused" // Boolean: paused
        const val EXTRA_PROFILE = "profile" // String?: the profile name in force, or absent/null
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
