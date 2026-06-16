package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.toAnimationConfig
import com.tideo.autobrightness.app.settings.toBrightnessCurveConfig
import com.tideo.autobrightness.app.settings.toDynamicScalingConfig
import com.tideo.autobrightness.app.settings.toThresholdConfig
import com.tideo.autobrightness.domain.brightness.BrightnessContext
import com.tideo.autobrightness.domain.brightness.BrightnessEngine
import com.tideo.autobrightness.domain.brightness.BrightnessPolicyInput
import com.tideo.autobrightness.domain.brightness.OverrideRules
import com.tideo.autobrightness.domain.brightness.PreviousState
import com.tideo.autobrightness.domain.brightness.SoftwareDimming
import com.tideo.autobrightness.domain.brightness.TimeContext
import com.tideo.autobrightness.platform.brightness.ScreenBrightnessController
import com.tideo.autobrightness.platform.observe.BrightnessObserver
import com.tideo.autobrightness.platform.sensor.LightSensorSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The runtime auto-brightness pipeline: the Kotlin rebuild of the Tasker sensor → brightness loop.
 *
 * Concurrency model (BINDING, D-027): the pipeline is serialized through a single consumer
 * coroutine. One [PipelineEvent] runs to completion — including its animation frames — before the
 * next is processed. Sensor ticks arriving while a cycle is in flight are **DROPPED, not queued**,
 * exactly as prof760's `%AAB_MainLoop != On` clause drops them in Tasker (a re-entry mutex, D-021),
 * implemented here as the [inCycle] busy flag. All durable runtime state lives in [state] and is
 * written ONLY from the consumer coroutine; the sensor/observer collectors signal it via [events].
 *
 * Pipeline sources (pipeline_spec.md):
 *   - prof760/task554 main loop      → [LightSensorSource] → gated → [PipelineEvent.SensorTick]
 *   - prof755/task567 override detect → [OverrideMonitor]  → [PipelineEvent.OverrideDetected]
 *   - lifecycle (screen on/off, pause/resume/panic) → events posted by the service
 *
 * The decision math is the golden-tested domain [BrightnessEngine]; this controller owns only the
 * Tasker runtime state machine and the animated writes (via [AnimationRunner]).
 */
class BrightnessPipelineController(
    private val lightSensor: LightSensorSource,
    private val brightness: ScreenBrightnessController,
    brightnessObserver: BrightnessObserver,
    private val settingsProvider: suspend () -> AabSettings,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val animationRunner: AnimationRunner = AnimationRunner(brightness),
    private val dimming: DimmingCoordinator = NoOpDimmingCoordinator,
    private val debugSink: DebugSink = NoOpDebugSink,
    private val overrideSink: OverridePointSink = NoOpOverridePointSink,
    // F73: real solar ramp windows for the dynamic-scale engine. Default `{ null }` keeps the old
    // fixed-window behaviour (and existing tests) intact; AppModule supplies the live provider.
    private val circadianWindowsProvider: (transitionFactor: Double) -> CircadianWindows? = { null },
) {
    private val engine = BrightnessEngine()

    // %AAB_Throttle + Throttle Reinitialization watchdog (task566 / prof754, G2R-F78).
    private val throttle = ThrottleController()

    private val _state = MutableStateFlow(PipelineState())
    val state: StateFlow<PipelineState> = _state.asStateFlow()

    // Fast-changing intra-cycle flags read by the observer coroutine; kept outside the immutable
    // snapshot because they flip many times within a single cycle (mid-write / initial write).
    @Volatile private var autoRunning = false
    @Volatile private var initializing = false

    // Post-init override-suppression deadline (S12.7a, F64): override detection is suppressed until
    // `clock() >= this`. Armed after every Set Initial Brightness self-write (service start / screen-on
    // reinit / resume / context swap) so the ContentObserver echo of our own write — delivered after
    // `initializing` resets — cannot spuriously pause/override on start. 0 = no window open.
    @Volatile private var suppressOverrideUntilMs = 0L

    // %AAB_MainLoop re-entry mutex: true while a sensor cycle is claimed or running.
    private val inCycle = AtomicBoolean(false)

    // Cached so the per-sample prof760 gate does not hit DataStore on every reading.
    @Volatile private var cachedSettings: AabSettings? = null

    // F48: the Dynamic Scale debug Flash fires only while the circadian scale is actively ramping and
    // is throttled to ~once per 2 min — not on every light change. `lastScaleDynamicSeen` detects an
    // in-progress transition (the scale value is time-driven, so a change means a dawn/dusk ramp).
    private val dynamicScaleDebugGate = DynamicScaleDebugGate()
    @Volatile private var lastScaleDynamicSeen: Double? = null

    private val events = Channel<PipelineEvent>(Channel.UNLIMITED)

    private val overrideMonitor = OverrideMonitor(brightnessObserver) {
        val s = _state.value
        OverrideMonitor.GateState(
            serviceOn = s.serviceOn,
            autoRunning = autoRunning,
            paused = s.paused,
            initializing = initializing,
            detectOverrides = cachedSettings?.detectOverrides ?: false,
            suppressed = clock() < suppressOverrideUntilMs,
        )
    }

    private var consumerJob: Job? = null
    private var sensorJob: Job? = null
    private var overrideJob: Job? = null

    /** Begin the pipeline: claim foreground state, start the consumer + sensor + observer flows. */
    fun start() {
        if (consumerJob != null) return
        _state.update { it.copy(serviceOn = true) }
        consumerJob = scope.launch {
            cachedSettings = settingsProvider().also { throttle.seed(it.throttleDefaultMs) }
            for (event in events) {
                handle(event)
            }
        }
        overrideJob = scope.launch {
            overrideMonitor.overrides().collect { observed ->
                events.trySend(PipelineEvent.OverrideDetected(observed))
            }
        }
        startSensor()
    }

    /** Stop the pipeline entirely (service teardown). */
    fun stop() {
        _state.update { it.copy(serviceOn = false) }
        sensorJob?.cancel(); sensorJob = null
        overrideJob?.cancel(); overrideJob = null
        consumerJob?.cancel(); consumerJob = null
        inCycle.set(false)
    }

    // Lifecycle entry points — the service posts these; they run in consumer order.
    fun onScreenOff() { events.trySend(PipelineEvent.ScreenOff) }
    fun onScreenOn() { events.trySend(PipelineEvent.ScreenOn) }
    fun pause() { events.trySend(PipelineEvent.Pause) }
    fun resume() { events.trySend(PipelineEvent.Resume) }

    /** A context override swapped the active profile: re-apply the initial brightness (task43 act21). */
    fun onContextChanged() { events.trySend(PipelineEvent.ContextChanged) }

    /**
     * A settings Apply / profile load committed new parameters: re-run the pipeline immediately so the
     * change takes effect without waiting for a new sensor reading (G2-F16). This is an UNLIMITED
     * control event — it is NOT subject to the drop-not-queue sensor mutex — and reuses the same
     * re-evaluate path as a context swap (re-read effective settings → Set Initial Brightness).
     */
    fun reapply() { events.trySend(PipelineEvent.ContextChanged) }

    /**
     * prof769/task528 panic: restore a sane brightness, drop super dimming, and FULL STOP
     * (%AAB_Service=Off — task528 act1-2 toggles the service off). This is terminal, not a
     * pausable state (Gate 1 G1-F4): it is invoked synchronously and tears all jobs down, so the
     * service can persist serviceEnabled=false and stop right after.
     */
    fun emergencyStop() {
        sensorJob?.cancel(); sensorJob = null
        overrideJob?.cancel(); overrideJob = null
        consumerJob?.cancel(); consumerJob = null
        inCycle.set(false)
        brightness.forceManualMode()
        brightness.write(PANIC_BRIGHTNESS) // task528 act6: Set Display Brightness 255
        brightness.restoreMode()
        dimming.disengage() // task528 act7/8: Disable Super Dimming
        _state.value = PipelineState(serviceOn = false)
    }

    private fun startSensor() {
        if (sensorJob?.isActive == true) return
        sensorJob = scope.launch {
            lightSensor.samples().collect { sample -> onSensorSample(sample.lux.toDouble(), sample.accuracy) }
        }
    }

    /**
     * prof760 gate evaluation on the collector coroutine. Passing samples claim the [inCycle] mutex
     * and enqueue a [PipelineEvent.SensorTick]; everything else is dropped here (never queued).
     */
    private fun onSensorSample(lux: Double, accuracy: Int) {
        // Sensor-collector path. The two PipelineState fields written here (lastSampleMs, throttleMs)
        // come from the collector rather than the consumer; both are monotonic and MutableStateFlow
        // .update is atomic (CAS retry), so they cannot corrupt or be lost against the consumer's
        // snapshot writes.
        val now = clock()
        val settings = cachedSettings
        val s = _state.value
        // Throttle Reinitialization watchdog (task566/prof754, G2R-F78 follow-up): run it on EVERY
        // delivered sample, because in stable light prof760's dead-band gate drops every reading and no
        // cycle ever runs — so the throttle would otherwise stay stuck at its last small value. A
        // reading outside [ThreshAbsLow, ThreshAbsHigh] is a significant change; 10 s of only-in-band
        // readings raises the throttle to the AnimSteps×MaxWait+10 ceiling (stops polling).
        if (settings != null && s.threshAbsLow != null) {
            val significant = lux < (s.threshAbsLow ?: 0.0) || lux > (s.threshAbsHigh ?: 0.0)
            throttle.onSample(now, significant, throttle.ceiling(settings.animSteps, settings.maxWaitMs))
        }
        // Record every delivered sample (live "last sample" age, G2R-F5) + the current throttle so the
        // idle climb is visible in Live Debug without a cycle. MutableStateFlow.update is atomic.
        _state.update { it.copy(lastSampleMs = now, throttleMs = throttle.throttleMs) }
        if (settings == null || !settings.serviceEnabled) return
        val passes = ProfileGates.monitorAmbientLightGate(
            trustUnreliable = settings.trustUnreliableSensor,
            accuracy = accuracy,
            lux = lux,
            threshAbsLow = s.threshAbsLow ?: 0.0,
            threshAbsHigh = s.threshAbsHigh ?: 0.0,
            mainLoopOn = inCycle.get(),
            thresholdsSeeded = s.threshAbsLow != null,
        )
        if (!passes) return
        // Re-entry mutex: claim the cycle slot, or drop. Cleared when the cycle completes.
        if (!inCycle.compareAndSet(false, true)) return
        if (events.trySend(PipelineEvent.SensorTick(lux, accuracy)).isFailure) {
            inCycle.set(false)
        }
    }

    private suspend fun handle(event: PipelineEvent) {
        when (event) {
            is PipelineEvent.SensorTick -> {
                try {
                    runCycle(event.lux)
                } finally {
                    inCycle.set(false)
                }
            }
            PipelineEvent.ScreenOff -> hibernate()
            PipelineEvent.ScreenOn -> reinit()
            PipelineEvent.Pause -> pauseInternal()
            PipelineEvent.Resume -> resumeInternal()
            is PipelineEvent.OverrideDetected -> handleOverride(event.observedBrightness)
            PipelineEvent.ContextChanged -> reapplyProfile()
        }
    }

    /** task43 act21: a context profile swap re-runs Set Initial Brightness with the new (effective) settings. */
    private suspend fun reapplyProfile() {
        if (_state.value.paused || !_state.value.serviceOn) return
        setInitialBrightness(settingsProvider().also { cachedSettings = it })
    }

    /** task554 → task544 → task535 → task661: ingest a reading and animate to the new brightness. */
    private suspend fun runCycle(rawLux: Double) {
        val settings = settingsProvider().also { cachedSettings = it }
        if (!settings.serviceEnabled || _state.value.paused) return

        val now = clock()
        val s = _state.value
        // Throttle gate (task544 act2-9): drop ticks inside the THROTTLE window. The active window is
        // the runtime %AAB_Throttle (actual steps×wait, or the idle ceiling — G2R-F78), not the raw
        // setting; ThrottleController.seed() initialised it to the setting at start.
        s.lastAcceptedMs?.let { last ->
            if (now - last < throttle.throttleMs) return
        }

        val cycleStart = now
        autoRunning = true
        try {
            val output = engine.evaluate(buildInput(rawLux, settings, s))
            val from = brightness.read()
            // task661 act22-26 / task698 step 3: hold the hardware floor in PWM-sensitive mode (D-050).
            val target = applyPwmFloor(output.targetBrightness, settings)

            // %AAB_Debug 3/4: Flash the light-evaluation + dynamic-scale figures (D-023, G2-F15).
            emitDebug(DebugCategory.LIGHT_EVAL, settings) {
                "lux ${round3(rawLux)}→${output.smoothedLux.toInt()} · thr ${output.thresholdLow.toInt()}–${output.thresholdHigh.toInt()} · →$target"
            }
            // %AAB_Debug 4 "Dynamic Scale Calcs": fire only ~2 min into a dawn/dusk transition, not on
            // every light change (G2R-F48). A transition is the time-driven circadian scale changing.
            val prevScale = lastScaleDynamicSeen
            val transitionActive = prevScale != null && kotlin.math.abs(output.scaleDynamic - prevScale) > 1e-4
            lastScaleDynamicSeen = output.scaleDynamic
            if (dynamicScaleDebugGate.shouldEmit(now, transitionActive)) {
                emitDebug(DebugCategory.DYNAMIC_SCALE, settings) {
                    "scale ${round3(output.scaleDynamic)} · compress ${output.scaleDynamicCompress}"
                }
            }

            val brightnessChanged = target != from
            if (target != from) {
                brightness.forceManualMode()
                // %AAB_Debug 1 "Skip Animations": jump straight to the target (Tasker debug mode).
                if (settings.debugLevel == DebugCategory.SKIP_ANIMATIONS.level) {
                    brightness.write(target)
                    emitDebug(DebugCategory.SKIP_ANIMATIONS, settings) { "skip → $target" }
                } else {
                    emitDebug(DebugCategory.ANIMATION_DETAILS, settings) {
                        "animate $from→$target in ${output.animationSteps}×${output.animationWaitMs}ms"
                    }
                    val result = animationRunner.animate(
                        from = from,
                        to = target,
                        steps = output.animationSteps,
                        waitMs = output.animationWaitMs,
                        detectOverrides = settings.detectOverrides,
                    )
                    if (result == AnimationRunner.Result.OVERRIDDEN) {
                        events.trySend(PipelineEvent.OverrideDetected(brightness.read()))
                        return
                    }
                }
            }

            // Super-dimming layer (task646→650/645): engage below DimmingThreshold, else disengage.
            // Runs from the pipeline coroutine so the secure write is serialized with the cycle.
            // F65: feed the UN-FLOORED engine target (%AAB_CurrentBright in task646 act1/act2), NOT the
            // PWM-floored hardware value — when pwmSensitive raises `target` UP to dimmingThreshold the
            // floored value is never < threshold, so the secure reduce_bright_colors layer would never
            // engage and "Extra Dim" never applied. The two layers cooperate: the hardware sits at the
            // PWM floor while the secure layer darkens visually below it (task661/698 floor ⟂ task650).
            dimming.apply(output.targetBrightness, settings)
            // F58 live readout: %AAB_DimmingDS (abs reduce_bright_colors level) + %AAB_DimmingCurrent
            // (relative strength) for the Super Dimming screen, computed from the golden SoftwareDimming.
            val (dimCurrent, dimDS) = dimmingReadout(output.targetBrightness, settings)

            val cycleTotal = (clock() - cycleStart).toDouble()
            // %AAB_Debug 7 "Graph Metrics": Flash the measured cycle duration (feeds throttle).
            emitDebug(DebugCategory.GRAPH_METRICS, settings) { "cycle ${cycleTotal.toInt()}ms" }
            // task566 / prof754: %AAB_Throttle is the engine's ACTUAL animation duration this cycle
            // (transitionDurationMs = loops×wait+10+cycleTime, golden task543), NOT MaxSteps×MaxWait+10
            // (G2R-F78). After ~10 s of no brightness change the watchdog raises it to that ceiling.
            throttle.onCycleComplete(
                now = now,
                brightnessChanged = brightnessChanged,
                actualThrottleMs = output.transitionDurationMs,
                ceilingMs = throttle.ceiling(settings.animSteps, settings.maxWaitMs),
            )
            _state.update {
                it.copy(
                    smoothedLux = output.smoothedLux,
                    lastRawLux = round3(rawLux),
                    lastAcceptedMs = now,
                    threshAbsLow = output.thresholdLow,
                    threshAbsHigh = output.thresholdHigh,
                    threshDynamic = output.dynamicThreshold,
                    cycleTimeMs = cycleTotal,
                    scaleDynamic = output.scaleDynamic,
                    scaleDynamicCompress = output.scaleDynamicCompress,
                    scalingUse = settings.scalingEnabled,
                    lastAppliedBrightness = target,
                    targetBrightness = target,
                    dimmingCurrent = dimCurrent,
                    dimmingDS = dimDS,
                    // Live Debug "Performance & Timings" parity (G2R-F29).
                    luxAlpha = output.luxAlpha,
                    animationSteps = output.animationSteps,
                    animationWaitMs = output.animationWaitMs,
                    throttleMs = throttle.throttleMs,
                    lastUpdateMs = clock(),
                )
            }
        } finally {
            autoRunning = false
        }
    }

    /**
     * task650 act28/act30: the Super Dimming live-readout pair `%AAB_DimmingDS` (abs reduce_bright_colors
     * level) and `%AAB_DimmingCurrent` (relative strength = dim_shell × dim_progress), for the
     * un-floored engine target (G2R-F58). Computed from the golden-tested [SoftwareDimming] (call only).
     * Zero when no dim path is active (target ≥ threshold or both dim toggles off), mirroring
     * task661 act33/34 which clears both when disengaging.
     *
     * @return (dimmingCurrent, dimmingDS)
     */
    private fun dimmingReadout(target: Int, settings: AabSettings): Pair<Double, Double> {
        if (target >= settings.dimmingThreshold) return 0.0 to 0.0
        return when {
            // PWM-sensitive: the level is task700 finalDimLevel; it has no separate progress term, so
            // the relative + absolute readouts coincide.
            settings.pwmSensitive -> {
                val ds = SoftwareDimming.finalDimLevel(
                    targetBrightness = target.toDouble(),
                    isElevated = true,
                    dimmingThreshold = settings.dimmingThreshold.toDouble(),
                    pwmExp = settings.pwmExponent.toDouble(),
                )
                ds to ds
            }
            settings.dimmingEnabled -> {
                val ds = SoftwareDimming.dimShell(
                    brightness = target.toDouble(),
                    minBrightness = settings.minBrightness.toDouble(),
                    dimmingThreshold = settings.dimmingThreshold.toDouble(),
                    dimmingExponent = settings.dimmingExponent.toDouble(),
                    dimmingStrength = settings.dimmingStrength.toDouble(),
                    dimDynamic = null,
                )
                val progress = SoftwareDimming.dimProgress(
                    brightness = target.toDouble(),
                    minBrightness = settings.minBrightness.toDouble(),
                    dimmingThreshold = settings.dimmingThreshold.toDouble(),
                    dimmingExponent = settings.dimmingExponent.toDouble(),
                )
                (ds * progress) to ds
            }
            else -> 0.0 to 0.0
        }
    }

    private fun buildInput(rawLux: Double, settings: AabSettings, s: PipelineState): BrightnessPolicyInput {
        // UTC seconds-of-day — the same frame as the solar ramp windows below (buildScheduleWindows
        // derives them as riseEpochSec % 86400). Both UTC ⇒ the ramp tracks the real sun (F73).
        val secondsOfDay = ((clock() / 1000L) % 86_400L).toDouble()
        val previous = if (s.smoothedLux != null && s.lastRawLux != null) {
            PreviousState(smoothedLux = s.smoothedLux, lastRawLux = s.lastRawLux, cycleTimeMs = s.cycleTimeMs)
        } else {
            null
        }
        // F73: feed the REAL sunrise/sunset windows (not the fixed 6–8am-UTC TimeContext defaults) so
        // %AAB_ScaleDynamic ramps with the actual day. Null (no location yet) → keep the old defaults.
        val windows = circadianWindowsProvider(settings.scaleTransitionFactor.toDouble())
        val time = if (windows != null) {
            TimeContext(
                secondsOfDay = secondsOfDay,
                morningStart = windows.morningStart,
                morningEnd = windows.morningEnd,
                eveningStart = windows.eveningStart,
                eveningEnd = windows.eveningEnd,
                sunlightDurationMinutes = windows.sunlightDurationMinutes,
            )
        } else {
            TimeContext(secondsOfDay = secondsOfDay)
        }
        return BrightnessPolicyInput(
            lux = rawLux,
            time = time,
            context = BrightnessContext(isPolarDayNight = windows?.isPolar ?: false),
            thresholds = settings.toThresholdConfig(),
            curve = settings.toBrightnessCurveConfig(),
            animation = settings.toAnimationConfig(),
            dynamicScaling = settings.toDynamicScalingConfig(),
            previous = previous,
        )
    }

    /**
     * task567 Manual Override: record the point and latch paused; the service posts the notification.
     *
     * task567 act7/act8 settle: wait `%AAB_CycleTime` for the new brightness to "take hold", then
     * RE-READ and re-check the gate before committing the pause. A rapid light swing makes the pipeline
     * write its own multi-frame transition whose ContentObserver callbacks can lag/coalesce; only a
     * value that, after settling, is still NOT what we last applied is a genuine manual override — so a
     * fast lux swing no longer false-pauses the loop (G2R-F26/D-049 #1). This runs in the single
     * pipeline consumer (D-027), so the settle delay simply defers the next event.
     *
     * G2R-F71: the settle is `%AAB_CycleTime` ONLY — NOT the `%AAB_Throttle` reactivity cooldown. The
     * throttle gates the task544 main loop alone (prof760, see [runCycle]); prof755→task567 override
     * detection is a SEPARATE Tasker profile and must not borrow the cooldown window, or a genuine
     * override goes unacknowledged for the entire throttle (which on a long throttle is the override
     * being "swallowed"). When no cycle has measured a CycleTime yet (`cycleTimeMs == null`), settle
     * immediately — Tasker's "Wait %AAB_CycleTime" on an unset variable is a 0 ms wait, not the cooldown.
     */
    private suspend fun handleOverride(observed: Int) {
        val s = _state.value
        if (!OverrideRules.shouldCommitPause(s.serviceOn, autoRunning, s.paused, initializing)) return

        val settleMs = (s.cycleTimeMs?.toLong() ?: 0L).coerceAtLeast(0L)
        if (settleMs > 0) delay(settleMs)

        val s2 = _state.value
        // Re-check the gate: a cycle/pause/panic may have started during the settle wait.
        if (!OverrideRules.shouldCommitPause(s2.serviceOn, autoRunning, s2.paused, initializing)) return
        val settled = brightness.read()
        // Settled back to OUR last applied brightness → it was our in-flight write / a transient during
        // a rapid swing, not a manual override (D-049 #1). Don't pause.
        if (s2.lastAppliedBrightness != null && settled == s2.lastAppliedBrightness) return

        val history = OverrideRules.recordOverridePoint(
            history = s2.overrideHistory,
            lux = s2.smoothedLux ?: 0.0,
            brightness = settled.toDouble(),
            dynamicCompress = s2.scaleDynamicCompress,
            scalingUse = s2.scalingUse,
        )
        brightness.clearSelfWriteMarker()
        // task567: a manual override disengages any active super dimming.
        dimming.disengage()
        // pausedByOverride flags this as a DETECTED override (not a user Pause) so the service raises
        // the high-priority notification + toast (G2R-F35).
        _state.update { it.copy(paused = true, pausedByOverride = true, overrideHistory = history) }
        // Persist the captured training point (newest first) so the wizard + curve overlay have real
        // input across restarts (G2R-F13; closes D-044c).
        history.firstOrNull()?.let { (lux, bright) -> overrideSink.record(lux, bright) }
    }

    private fun pauseInternal() {
        brightness.clearSelfWriteMarker()
        dimming.disengage()
        // A user-initiated Pause is NOT an override (pausedByOverride stays false → no alert, G2R-F35).
        _state.update { it.copy(paused = true, pausedByOverride = false) }
    }

    /** task569 Resume After Override: re-establish the initial brightness and clear the pause latch. */
    private suspend fun resumeInternal() {
        _state.update { it.copy(paused = false, pausedByOverride = false) }
        setInitialBrightness(settingsProvider().also { cachedSettings = it })
    }

    /** prof761/task618 wake reinit: throttle resets to default (it is the setting), set initial brightness. */
    private suspend fun reinit() {
        val settings = settingsProvider().also { cachedSettings = it }
        startSensor()
        if (!_state.value.paused) setInitialBrightness(settings)
    }

    /** prof753/task585 hibernate: stop sensing and clear the runtime loop state. */
    private fun hibernate() {
        sensorJob?.cancel(); sensorJob = null
        inCycle.set(false)
        dimming.disengage() // task585: drop super dimming when the display goes off
        _state.update {
            it.copy(
                smoothedLux = null,
                lastRawLux = null,
                lastAcceptedMs = null,
                threshAbsLow = null,
                threshAbsHigh = null,
                cycleTimeMs = null,
            )
        }
    }

    /** task618 block#1: set a starting brightness immediately (no smoothing loop) on wake/resume. */
    private fun setInitialBrightness(settings: AabSettings) {
        val s = _state.value
        val lux = s.smoothedLux ?: s.lastRawLux ?: return
        initializing = true
        try {
            // previous = null → engine's first-run path maps the reading straight to a target.
            val output = engine.evaluate(buildInput(lux, settings, PipelineState()))
            val target = applyPwmFloor(output.targetBrightness, settings)
            brightness.forceManualMode()
            brightness.write(target)
            // F65: dimming decides off the un-floored engine target, not the PWM-floored write (above).
            dimming.apply(output.targetBrightness, settings)
            // F64: arm the post-init settle window so the ContentObserver echo of THIS self-write
            // (and any AUTO→MANUAL mode-flip recompute) — delivered after `initializing` clears below
            // — is not flagged as a manual override on start/reinit/resume/context swap.
            suppressOverrideUntilMs = clock() + INITIAL_SETTLE_MS
            _state.update {
                it.copy(
                    lastAppliedBrightness = target,
                    targetBrightness = target,
                    lastAcceptedMs = clock(),
                )
            }
        } finally {
            initializing = false
        }
    }

    /**
     * task661 act22-26 / task698 step 3 hardware floor (D-050): when "Use software dimming
     * (PWM-sensitive)" is on and the target falls below the dimming threshold, hold the HARDWARE
     * brightness at the threshold (the panel's PWM-flicker floor). Further darkening is the
     * secure/overlay layer's job (deferred, D-040) — not by writing a lower hardware value. The floor
     * is in domain space; [ScreenBrightnessController] maps it onto the device range (cf. D-049 #4).
     */
    private fun applyPwmFloor(target: Int, settings: AabSettings): Int =
        if (settings.pwmSensitive && target < settings.dimmingThreshold) settings.dimmingThreshold else target

    /** Emit a runtime debug Flash for [category] gated on the live debugLevel (D-023, G2-F15). */
    private fun emitDebug(category: DebugCategory, settings: AabSettings, message: () -> String) =
        debugSink.emit(category, settings.debugLevel, message)

    // Tasker round3 idiom: Math.round(x*1000)/1000 (ties toward +∞).
    private fun round3(value: Double): Double = Math.round(value * 1000.0) / 1000.0

    private companion object {
        const val PANIC_BRIGHTNESS = 255

        // F64 settle window: long enough to outlast the async ContentObserver delivery of our own
        // initial write + the mode-flip recompute, short enough that a real user override moments
        // after a reinit is still caught by the next observer event.
        const val INITIAL_SETTLE_MS = 1500L
    }
}

/**
 * Sink for captured manual-override training points (task561 %AAB_Overrides). The runtime persists
 * each genuine override so the curve wizard + curve overlay have real input (G2R-F13). Kept as a
 * small interface so the controller stays unit-testable without a DataStore.
 */
fun interface OverridePointSink {
    suspend fun record(lux: Double, brightness: Double)
}

/** No-op sink for controller unit tests / when no persistence is wired. */
object NoOpOverridePointSink : OverridePointSink {
    override suspend fun record(lux: Double, brightness: Double) = Unit
}
