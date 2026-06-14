package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.toAnimationConfig
import com.tideo.autobrightness.app.settings.toBrightnessCurveConfig
import com.tideo.autobrightness.app.settings.toDynamicScalingConfig
import com.tideo.autobrightness.app.settings.toThresholdConfig
import com.tideo.autobrightness.domain.brightness.BrightnessEngine
import com.tideo.autobrightness.domain.brightness.BrightnessPolicyInput
import com.tideo.autobrightness.domain.brightness.OverrideRules
import com.tideo.autobrightness.domain.brightness.PreviousState
import com.tideo.autobrightness.domain.brightness.TimeContext
import com.tideo.autobrightness.platform.brightness.ScreenBrightnessController
import com.tideo.autobrightness.platform.observe.BrightnessObserver
import com.tideo.autobrightness.platform.sensor.LightSensorSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
) {
    private val engine = BrightnessEngine()

    private val _state = MutableStateFlow(PipelineState())
    val state: StateFlow<PipelineState> = _state.asStateFlow()

    // Fast-changing intra-cycle flags read by the observer coroutine; kept outside the immutable
    // snapshot because they flip many times within a single cycle (mid-write / initial write).
    @Volatile private var autoRunning = false
    @Volatile private var initializing = false

    // %AAB_MainLoop re-entry mutex: true while a sensor cycle is claimed or running.
    private val inCycle = AtomicBoolean(false)

    // Cached so the per-sample prof760 gate does not hit DataStore on every reading.
    @Volatile private var cachedSettings: AabSettings? = null

    private val events = Channel<PipelineEvent>(Channel.UNLIMITED)

    private val overrideMonitor = OverrideMonitor(brightnessObserver) {
        val s = _state.value
        OverrideMonitor.GateState(
            serviceOn = s.serviceOn,
            autoRunning = autoRunning,
            paused = s.paused,
            initializing = initializing,
            detectOverrides = cachedSettings?.detectOverrides ?: false,
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
            cachedSettings = settingsProvider()
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
        // Record every delivered sample so the UI can show a live "last sample" age (G2R-F5), even
        // for readings the prof760 dead-band/throttle gates later drop. This is the one PipelineState
        // field written from the sensor collector rather than the consumer; it is a monotonic
        // timestamp and MutableStateFlow.update is atomic (CAS retry), so it cannot corrupt or be
        // lost against the consumer's snapshot writes.
        _state.update { it.copy(lastSampleMs = clock()) }
        val settings = cachedSettings ?: return
        if (!settings.serviceEnabled) return
        val s = _state.value
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
        // Throttle gate (task544 act2-9): drop ticks inside the throttle window.
        s.lastAcceptedMs?.let { last ->
            if (now - last < settings.throttleDefaultMs) return
        }

        val cycleStart = now
        autoRunning = true
        try {
            val output = engine.evaluate(buildInput(rawLux, settings, s))
            val from = brightness.read()
            val target = output.targetBrightness

            // %AAB_Debug 3/4: Flash the light-evaluation + dynamic-scale figures (D-023, G2-F15).
            emitDebug(DebugCategory.LIGHT_EVAL, settings) {
                "lux ${round3(rawLux)}→${output.smoothedLux.toInt()} · thr ${output.thresholdLow.toInt()}–${output.thresholdHigh.toInt()} · →$target"
            }
            emitDebug(DebugCategory.DYNAMIC_SCALE, settings) { "scaleCompress ${output.scaleDynamicCompress}" }

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

            val cycleTotal = (clock() - cycleStart).toDouble()
            // %AAB_Debug 7 "Graph Metrics": Flash the measured cycle duration (feeds throttle).
            emitDebug(DebugCategory.GRAPH_METRICS, settings) { "cycle ${cycleTotal.toInt()}ms" }
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
                )
            }

            // Super-dimming layer (task646→650/645): engage below DimmingThreshold, else disengage.
            // Runs from the pipeline coroutine so the secure write is serialized with the cycle.
            dimming.apply(target, settings)
        } finally {
            autoRunning = false
        }
    }

    private fun buildInput(rawLux: Double, settings: AabSettings, s: PipelineState): BrightnessPolicyInput {
        // UTC seconds-of-day; real local time + solar ramp windows are wired with circadian in S9b/S12.
        val secondsOfDay = ((clock() / 1000L) % 86_400L).toDouble()
        val previous = if (s.smoothedLux != null && s.lastRawLux != null) {
            PreviousState(smoothedLux = s.smoothedLux, lastRawLux = s.lastRawLux, cycleTimeMs = s.cycleTimeMs)
        } else {
            null
        }
        return BrightnessPolicyInput(
            lux = rawLux,
            time = TimeContext(secondsOfDay = secondsOfDay),
            thresholds = settings.toThresholdConfig(),
            curve = settings.toBrightnessCurveConfig(),
            animation = settings.toAnimationConfig(),
            dynamicScaling = settings.toDynamicScalingConfig(),
            previous = previous,
        )
    }

    /** task567 Manual Override: record the point and latch paused; the service posts the notification. */
    private fun handleOverride(observed: Int) {
        val s = _state.value
        // task567 act8 re-check guard (mirrors the prof755 gate after the cycle-time wait).
        if (!OverrideRules.shouldCommitPause(s.serviceOn, autoRunning, s.paused, initializing)) return
        val history = OverrideRules.recordOverridePoint(
            history = s.overrideHistory,
            lux = s.smoothedLux ?: 0.0,
            brightness = observed.toDouble(),
            dynamicCompress = s.scaleDynamicCompress,
            scalingUse = s.scalingUse,
        )
        brightness.clearSelfWriteMarker()
        // task567: a manual override disengages any active super dimming.
        dimming.disengage()
        _state.update { it.copy(paused = true, overrideHistory = history) }
    }

    private fun pauseInternal() {
        brightness.clearSelfWriteMarker()
        dimming.disengage()
        _state.update { it.copy(paused = true) }
    }

    /** task569 Resume After Override: re-establish the initial brightness and clear the pause latch. */
    private suspend fun resumeInternal() {
        _state.update { it.copy(paused = false) }
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
            brightness.forceManualMode()
            brightness.write(output.targetBrightness)
            dimming.apply(output.targetBrightness, settings)
            _state.update {
                it.copy(
                    lastAppliedBrightness = output.targetBrightness,
                    targetBrightness = output.targetBrightness,
                    lastAcceptedMs = clock(),
                )
            }
        } finally {
            initializing = false
        }
    }

    /** Emit a runtime debug Flash for [category] gated on the live debugLevel (D-023, G2-F15). */
    private fun emitDebug(category: DebugCategory, settings: AabSettings, message: () -> String) =
        debugSink.emit(category, settings.debugLevel, message)

    // Tasker round3 idiom: Math.round(x*1000)/1000 (ties toward +∞).
    private fun round3(value: Double): Double = Math.round(value * 1000.0) / 1000.0

    private companion object {
        const val PANIC_BRIGHTNESS = 255
    }
}
