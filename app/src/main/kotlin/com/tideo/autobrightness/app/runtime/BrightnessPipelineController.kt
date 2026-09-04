package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.domain.brightness.BrightnessEngine
import com.tideo.autobrightness.platform.brightness.ScreenBrightnessController
import com.tideo.autobrightness.platform.observe.BrightnessObserver
import com.tideo.autobrightness.platform.sensor.LightSensorSource
import com.tideo.autobrightness.platform.sensor.ProximitySensorSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runtime auto-brightness pipeline orchestrator (BINDING, D-027): serialized through a single
 * consumer coroutine. Sensor ticks arriving during a cycle are DROPPED (re-entry mutex [inCycle]).
 * State is written ONLY from the consumer coroutine via [PipelineRuntimeContext].
 * Pipeline sources: prof760 main loop, prof755 override detection, and lifecycle events.
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
    // prof759/task545 proximity damp. Optional: null (controller unit tests / no proximity sensor) →
    // never near → no damp, so existing behaviour and golden parity are unchanged.
    private val proximitySource: ProximitySensorSource? = null,
) : ControllerHook, PipelineRuntimeContext {

    private val engine = BrightnessEngine()

    // %AAB_Throttle + Throttle Reinitialization watchdog (task566 / prof754, G2R-F78).
    private val throttle = ThrottleController()

    private val _state = MutableStateFlow(PipelineState())
    val state: StateFlow<PipelineState> = _state.asStateFlow()

    // @Volatile: written by consumer, read on SENSOR/OBSERVER collectors (cross-coroutine handoff).
    @Volatile private var cachedSettings: AabSettings? = null

    // @Volatile: override suppression deadline, read on the OBSERVER gate. Written by the consumer
    // and, since DB-082, by the screen-on receiver thread — a single volatile long, no ordering
    // between the two beyond "latest deadline wins", which is what the window wants anyway.
    @Volatile private var suppressOverrideUntilMs = 0L

    // %AAB_MainLoop re-entry mutex: true while a sensor cycle is claimed or running.
    private val inCycle = AtomicBoolean(false)

    private val debugEmitter = PipelineDebugEmitter(debugSink)
    private val panicHandler = PanicHandler(brightness, dimming)
    private val cycleRunner = PipelineCycleRunner(
        ctx = this,
        engine = engine,
        brightness = brightness,
        animationRunner = animationRunner,
        dimming = dimming,
        throttle = throttle,
        debug = debugEmitter,
        settingsProvider = settingsProvider,
        circadianWindowsProvider = circadianWindowsProvider,
        overrideSink = overrideSink,
        clock = clock,
    )

    private val controlGate = ControlEventGate() // DA-043 backlog bound

    private val overrideMonitor = OverrideMonitor(brightnessObserver) {
        val s = _state.value
        OverrideMonitor.GateState(
            serviceOn = s.serviceOn,
            autoRunning = s.autoRunning,
            paused = s.paused,
            initializing = s.initializing,
            detectOverrides = cachedSettings?.detectOverrides ?: false,
            suppressed = clock() < suppressOverrideUntilMs,
        )
    }

    private var consumerJob: Job? = null
    private var sensorJob: Job? = null
    private var overrideJob: Job? = null

    // prof759/task545 proximity damp. Orchestrator only; lifecycle lives in ProximityTracker.
    private val proximityTracker = ProximityTracker(proximitySource, scope) { near ->
        _state.update { it.copy(proximityNear = near) }
    }

    // --- PipelineRuntimeContext: the single-writer accessors the cycle runner reaches state through ---

    override val stateValue: PipelineState get() = _state.value
    override fun update(transform: (PipelineState) -> PipelineState) = _state.update(transform)
    override fun cacheSettings(settings: AabSettings) { cachedSettings = settings }
    override fun armInitialSettle(untilMs: Long) { suppressOverrideUntilMs = untilMs }
    override fun overrideSuppressed(): Boolean = clock() < suppressOverrideUntilMs
    override fun postOverrideDetected(observed: Int, source: OverrideSource) {
        postControl(PipelineEvent.OverrideDetected(observed, source))
    }

    /** Start the pipeline and consumer/sensor/observer flows. */
    fun start() {
        if (consumerJob != null) return
        _state.update { it.copy(serviceOn = true) }
        consumerJob = scope.launch {
            cachedSettings = settingsProvider().also { throttle.seed(it.throttleDefaultMs) }
            controlGate.consumeEach { handle(it) }
        }
        overrideJob = scope.launch {
            overrideMonitor.overrides().collect { observed ->
                postControl(PipelineEvent.OverrideDetected(observed, OverrideSource.OBSERVER))
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
        proximityTracker.stop()
        inCycle.set(false)
        // DA-038: independently clear pre-death Extra Dim residue and return brightness-mode ownership.
        runCatching { dimming.disengage() }
        runCatching { brightness.restoreMode() }
    }

    // Lifecycle entry points — the service posts these; they run in consumer order.
    fun onScreenOff() { postControl(PipelineEvent.ScreenOff) }
    /**
     * DB-082 (issue #123): arm the settle window on the RECEIVER thread, before the event is even
     * queued. `reinit()` reads settings from DataStore first, and the framework re-asserts
     * SCREEN_BRIGHTNESS as the display comes back — that write lands in the gap, against a
     * self-write marker left over from before the sleep, and reads as a manual override.
     */
    fun onScreenOn() {
        armInitialSettle(clock() + PipelineCycleRunner.INITIAL_SETTLE_MS)
        postControl(PipelineEvent.ScreenOn)
    }
    fun pause() { postControl(PipelineEvent.Pause) }
    fun resume() { postControl(PipelineEvent.Resume) }

    /** A context override swapped the active profile: re-apply the initial brightness (task43 act21). */
    override fun onContextChanged() { postControl(PipelineEvent.ContextChanged) }

    /** Re-run the pipeline after settings apply (G2-F16). */
    fun reapply() { postControl(PipelineEvent.ContextChanged) }

    // DA-043 bound; OverrideDetected carries a value, so it is capped but never folded.
    private fun postControl(event: PipelineEvent) = controlGate.admit(event, event !is PipelineEvent.OverrideDetected)
    internal val controlBacklog: ControlEventGate get() = controlGate // DA-043 counters (test seam)

    /** prof769/task528 panic: restore brightness, drop dimming, stop everything (D-139). */
    suspend fun emergencyStop() {
        sensorJob?.cancel(); sensorJob = null
        overrideJob?.cancel(); overrideJob = null
        consumerJob?.cancelAndJoin(); consumerJob = null
        proximityTracker.stop()
        inCycle.set(false)
        panicHandler.execute() // task528 act6-8: restore 255 + drop dimming
        _state.value = PipelineState(serviceOn = false)
    }

    private fun startSensor() {
        if (sensorJob?.isActive == true) return
        sensorJob = scope.launch {
            lightSensor.samples().collect { sample -> onSensorSample(sample.lux.toDouble(), sample.accuracy) }
        }
        proximityTracker.start()
    }

    /** prof760 gate on collector: passing samples claim [inCycle] mutex, others are dropped. */
    private fun onSensorSample(lux: Double, accuracy: Int) {
        val now = clock()
        val settings = cachedSettings
        val s = _state.value
        // Throttle Reinitialization watchdog (task566/prof754, G2R-F78).
        if (settings != null && s.threshAbsLow != null) {
            val significant = lux < (s.threshAbsLow ?: 0.0) || lux > (s.threshAbsHigh ?: 0.0)
            throttle.onSample(now, significant, throttle.ceiling(settings.animSteps, settings.maxWaitMs))
        }
        // Record every delivered sample + current throttle (Live Debug visibility, G2R-F5).
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
        if (!controlGate.offerSensorTick(PipelineEvent.SensorTick(lux, accuracy))) {
            inCycle.set(false)
        }
    }

    private suspend fun handle(event: PipelineEvent) {
        when (event) {
            is PipelineEvent.SensorTick -> {
                try {
                    cycleRunner.runCycle(event.lux)
                } finally {
                    inCycle.set(false)
                }
            }
            PipelineEvent.ScreenOff -> hibernate()
            PipelineEvent.ScreenOn -> reinit()
            PipelineEvent.Pause -> pauseInternal()
            PipelineEvent.Resume -> cycleRunner.resume()
            is PipelineEvent.OverrideDetected ->
                cycleRunner.handleOverride(event.observedBrightness, event.source)
            PipelineEvent.ContextChanged -> cycleRunner.reapplyProfile()
        }
    }

    private fun pauseInternal() {
        brightness.clearSelfWriteMarker()
        dimming.disengage()
        // A user-initiated Pause is NOT an override (pausedByOverride stays false → no alert, G2R-F35).
        _state.update { it.copy(paused = true, pausedByOverride = false) }
    }

    /** prof761/task618 wake reinit: clear smoothing state, start sensing, set initial brightness. */
    private suspend fun reinit() {
        val settings = settingsProvider().also { cachedSettings = it }
        startSensor()
        if (!_state.value.paused) cycleRunner.setInitialBrightness(settings)
    }

    /** prof753/task585 hibernate: stop sensing, clear runtime state. */
    private fun hibernate() {
        sensorJob?.cancel(); sensorJob = null
        proximityTracker.stop()
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
                // DC-008: UNKNOWN, not stale, across a sleep (lastBrightnessWrite survives — it is
                // the continuous diagnostic).
                lastAppliedBrightness = null,
                proximityNear = false,
            )
        }
    }
}

/** Sink for manual-override training points (task561 %AAB_Overrides, G2R-F13). */
fun interface OverridePointSink {
    suspend fun record(lux: Double, brightness: Double)
}

/** No-op sink for controller unit tests / when no persistence is wired. */
object NoOpOverridePointSink : OverridePointSink {
    override suspend fun record(lux: Double, brightness: Double) = Unit
}
