package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.domain.brightness.BrightnessEngine
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
 * The runtime auto-brightness pipeline ORCHESTRATOR: the Kotlin rebuild of the Tasker sensor →
 * brightness loop's state machine. S12.9e decomposed the original 596-LOC class — the per-event math
 * glue moved to [PipelineCycleRunner], the debug-flash surface to [PipelineDebugEmitter], and the
 * panic effect to [PanicHandler]; this file owns construction, start/stop, the event loop, the prof760
 * sensor gate, the [ControllerHook], and state exposure.
 *
 * Concurrency model (BINDING, D-027): the pipeline is serialized through a single consumer
 * coroutine. One [PipelineEvent] runs to completion — including its animation frames — before the
 * next is processed. Sensor ticks arriving while a cycle is in flight are **DROPPED, not queued**,
 * exactly as prof760's `%AAB_MainLoop != On` clause drops them in Tasker (a re-entry mutex, D-021),
 * implemented here as the [inCycle] busy flag. All durable runtime state lives in [state] and is
 * written ONLY from the consumer coroutine (via [PipelineRuntimeContext]); the sensor/observer
 * collectors signal it via [events].
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
) : ControllerHook, PipelineRuntimeContext {

    private val engine = BrightnessEngine()

    // %AAB_Throttle + Throttle Reinitialization watchdog (task566 / prof754, G2R-F78).
    private val throttle = ThrottleController()

    private val _state = MutableStateFlow(PipelineState())
    val state: StateFlow<PipelineState> = _state.asStateFlow()

    // Cached so the per-sample prof760 gate does not hit DataStore on every reading. @Volatile (S12.9e
    // audit, survivor #1): written by the consumer (start/runCycle/setInitial) and read on the SENSOR
    // collector ([onSensorSample]) + the OBSERVER gate — cross-coroutine single-reference handoff, each
    // read independently atomic; no compound invariant rides on it.
    @Volatile private var cachedSettings: AabSettings? = null

    // Post-init override-suppression deadline (S12.7a, F64): override detection is suppressed until
    // `clock() >= this`. @Volatile (S12.9e audit, survivor #2): written by the consumer (setInitial via
    // [armInitialSettle]) and read on the OBSERVER gate coroutine — a single monotonic Long, atomic.
    // 0 = no window open.
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

    private val events = Channel<PipelineEvent>(Channel.UNLIMITED)

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

    // --- PipelineRuntimeContext: the single-writer accessors the cycle runner reaches state through ---

    override val stateValue: PipelineState get() = _state.value
    override fun update(transform: (PipelineState) -> PipelineState) = _state.update(transform)
    override fun cacheSettings(settings: AabSettings) { cachedSettings = settings }
    override fun armInitialSettle(untilMs: Long) { suppressOverrideUntilMs = untilMs }
    override fun postOverrideDetected(observed: Int) { events.trySend(PipelineEvent.OverrideDetected(observed)) }

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
    override fun onContextChanged() { events.trySend(PipelineEvent.ContextChanged) }

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
        panicHandler.execute() // task528 act6-8: restore 255 + drop dimming
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
                    cycleRunner.runCycle(event.lux)
                } finally {
                    inCycle.set(false)
                }
            }
            PipelineEvent.ScreenOff -> hibernate()
            PipelineEvent.ScreenOn -> reinit()
            PipelineEvent.Pause -> pauseInternal()
            PipelineEvent.Resume -> cycleRunner.resume()
            is PipelineEvent.OverrideDetected -> cycleRunner.handleOverride(event.observedBrightness)
            PipelineEvent.ContextChanged -> cycleRunner.reapplyProfile()
        }
    }

    private fun pauseInternal() {
        brightness.clearSelfWriteMarker()
        dimming.disengage()
        // A user-initiated Pause is NOT an override (pausedByOverride stays false → no alert, G2R-F35).
        _state.update { it.copy(paused = true, pausedByOverride = false) }
    }

    /** prof761/task618 wake reinit: throttle resets to default (it is the setting), set initial brightness. */
    private suspend fun reinit() {
        val settings = settingsProvider().also { cachedSettings = it }
        startSensor()
        if (!_state.value.paused) cycleRunner.setInitialBrightness(settings)
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
