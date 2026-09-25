package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.domain.brightness.BrightnessEngine
import com.tideo.autobrightness.platform.brightness.ScreenBrightnessController
import com.tideo.autobrightness.platform.observe.BrightnessObserver
import com.tideo.autobrightness.platform.sensor.LightSensorSource
import com.tideo.autobrightness.platform.sensor.ProximitySensorSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * Runtime auto-brightness pipeline orchestrator (BINDING, D-027): serialized through a single
 * consumer coroutine. Light readings are admitted, or held in one slot, by [LightAdmission] (DC-069).
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
    // prof759/task545 proximity. Optional: null (unit tests / no proximity sensor) is never near.
    private val proximitySource: ProximitySensorSource? = null,
    private val callbackLog: SensorCallbackLog = SensorCallbackLog(),
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
    private val admission = LightAdmission(this, { cachedSettings }, throttle, controlGate, clock, scope)

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

    /** Start the pipeline; the light sensor registers only once settings have loaded (DC-067). */
    fun start() {
        if (consumerJob != null) return
        _state.update { it.copy(serviceOn = true) }
        consumerJob = scope.launch {
            cachedSettings = settingsProvider().also { throttle.seed(it.throttleDefaultMs) }
            startSensor(RegistrationCause.START, coroutineContext.job)
            controlGate.consumeEach { handle(it); admission.drain() }
        }
        startOverrideDetection()
    }

    /** Stop the pipeline entirely (service teardown). */
    fun stop() {
        _state.update { it.copy(serviceOn = false) }
        // DC-067: consumer first, so a registration still pending on it sees it cancelled.
        consumerJob?.cancel(); consumerJob = null
        admission.invalidate(SampleRejection.SERVICE_DISABLED)
        stopSensor()
        overrideJob?.cancel(); overrideJob = null
        proximityTracker.stop()
        admission.release()
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
    private fun postControl(event: PipelineEvent) =
        admission.fenced { controlGate.admit(event, event !is PipelineEvent.OverrideDetected) }
    internal val controlBacklog: ControlEventGate get() = controlGate // DA-043 counters (test seam)

    /** prof769/task528 panic: restore brightness, drop dimming, stop everything (D-139). */
    suspend fun emergencyStop() {
        val consumer = consumerJob?.also { it.cancel() }
        stopSensor()
        overrideJob?.cancel(); overrideJob = null
        consumer?.join(); consumerJob = null
        proximityTracker.stop()
        admission.release()
        panicHandler.execute() // task528 act6-8: restore 255 + drop dimming
        _state.value = PipelineState(serviceOn = false)
    }

    @Synchronized
    private fun startOverrideDetection() {
        if (overrideJob?.isActive == true) return
        overrideJob = scope.launch {
            overrideMonitor.overrides().collect { observed ->
                postControl(PipelineEvent.OverrideDetected(observed, OverrideSource.OBSERVER))
            }
        }
    }

    @Synchronized
    private fun startSensor(cause: RegistrationCause, owner: Job) {
        if (sensorJob?.isActive == true || !owner.isActive) return
        val generation = callbackLog.registered(cause, clock())
        val current = admission.newSession()
        _state.update { it.copy(sensor = it.sensor.registered()) }
        sensorJob = scope.launch {
            lightSensor.samples(
                onRegistered = { callbackLog.listenerRegistered(generation, it) },
                onCallback = { callbackLog.callback(generation, it, clock()) },
            ).collect { sample -> admission.onSample(sample.lux.toDouble(), sample.accuracy, current) }
        }
        proximityTracker.start()
    }

    @Synchronized
    private fun stopSensor() {
        sensorJob?.cancel(); sensorJob = null; admission.newSession(); callbackLog.unregistered()
    }

    private suspend fun handle(event: PipelineEvent) {
        when (event) {
            is PipelineEvent.SensorTick -> admission.run(event) { lux, claim, continuation -> cycleRunner.runCycle(lux, claim, continuation) }
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
        _state.update { it.copy(hibernated = false) }
        startSensor(RegistrationCause.WAKE, currentCoroutineContext().job)
        startOverrideDetection()
        if (!_state.value.paused) cycleRunner.setInitialBrightness(settings)
    }

    /** prof753/task585 hibernate: stop sensing and Allow Override, clear runtime state (DC-042). */
    private fun hibernate() {
        admission.invalidate(SampleRejection.SCREEN_OFF)
        stopSensor()
        overrideJob?.cancel(); overrideJob = null
        proximityTracker.stop()
        admission.release()
        dimming.disengage() // task585: drop super dimming when the display goes off
        _state.update {
            it.copy(
                hibernated = true,
                smoothedLux = null,
                lastRawLux = null,
                lastAcceptedMs = null,
                threshAbsLow = null,
                threshAbsHigh = null,
                threshDynamicPercent = null,
                threshDynamic = null,
                cycleTimeMs = null,
                // DC-008: UNKNOWN, not stale, across a sleep (lastBrightnessWrite survives — it is
                // the continuous diagnostic).
                lastAppliedBrightness = null,
                proximityNear = false,
                settlingSteps = 0,
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
