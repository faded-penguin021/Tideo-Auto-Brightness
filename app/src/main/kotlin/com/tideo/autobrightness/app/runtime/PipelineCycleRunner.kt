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
import kotlinx.coroutines.delay

/** Single-writer accessor for [PipelineState] (D-027). */
internal interface PipelineRuntimeContext {
    /** The current snapshot (atomic, untearable read). */
    val stateValue: PipelineState
    /** Apply [transform] to the snapshot (CAS-atomic). */
    fun update(transform: (PipelineState) -> PipelineState)
    /** Cache the effective settings for the per-sample prof760 gate (orchestrator-owned @Volatile). */
    fun cacheSettings(settings: AabSettings)
    /** Arm the post-init override-suppression window until [untilMs] (orchestrator-owned @Volatile). */
    fun armInitialSettle(untilMs: Long)
    /** True while the post-init/resume settle window is open (Set Initial Brightness just established
     *  our own brightness; its transition must not be mis-seen as a manual override — D-126). */
    fun overrideSuppressed(): Boolean
    /** Post a detected-override event back onto the consumer channel (mid-animation override). */
    fun postOverrideDetected(observed: Int)
}

/** Pipeline per-event math: Set Initial Brightness, runCycle, override settle, super-dimming readout (D-027). */
internal class PipelineCycleRunner(
    private val ctx: PipelineRuntimeContext,
    private val engine: BrightnessEngine,
    private val brightness: ScreenBrightnessController,
    private val animationRunner: AnimationRunner,
    private val dimming: DimmingCoordinator,
    private val throttle: ThrottleController,
    private val debug: PipelineDebugEmitter,
    private val settingsProvider: suspend () -> AabSettings,
    private val circadianWindowsProvider: (transitionFactor: Double) -> CircadianWindows?,
    private val overrideSink: OverridePointSink,
    private val clock: () -> Long,
) {

    /** task43 act21: a context profile swap re-runs Set Initial Brightness with the new (effective) settings. */
    suspend fun reapplyProfile() {
        if (ctx.stateValue.paused || !ctx.stateValue.serviceOn) return
        setInitialBrightness(settingsProvider().also { ctx.cacheSettings(it) })
    }

    /** task569 Resume After Override: re-establish the initial brightness and clear the pause latch. */
    suspend fun resume() {
        ctx.update { it.copy(paused = false, pausedByOverride = false) }
        setInitialBrightness(settingsProvider().also { ctx.cacheSettings(it) })
    }

    /** task554 → task544 → task535 → task661: ingest a reading and animate to the new brightness. */
    suspend fun runCycle(rawLux: Double) {
        val settings = settingsProvider().also { ctx.cacheSettings(it) }
        if (!settings.serviceEnabled || ctx.stateValue.paused) return

        val now = clock()
        val s = ctx.stateValue
        // task544 act2-9: throttle gate (G2R-F78).
        s.lastAcceptedMs?.let { last ->
            if (now - last < throttle.throttleMs) return
        }

        val cycleStart = now
        ctx.update { it.copy(autoRunning = true) }
        try {
            val output = engine.evaluate(buildInput(rawLux, settings, s))
            val from = brightness.read()
            // task661 act22-26 / task698 step 3: hardware floor in PWM-sensitive mode (D-050); readout tracks perceived (D-109).
            val target = applyPwmFloor(output.targetBrightness, settings)
            val perceived = output.targetBrightness

            // D-023, G2-F15, G2R-F48: debug metrics.
            debug.emit(DebugCategory.LIGHT_EVAL, settings.debugLevel) {
                "lux ${round3(rawLux)}→${output.smoothedLux.toInt()} · thr ${output.thresholdLow.toInt()}–${output.thresholdHigh.toInt()} · →$target"
            }
            debug.maybeDynamicScale(now, output.scaleDynamic, settings.debugLevel) {
                "scale ${round3(output.scaleDynamic)} · compress ${output.scaleDynamicCompress}"
            }

            val brightnessChanged = target != from
            if (target != from) {
                brightness.forceManualMode()
                // G3-F5: publish target early so dashboard animates during sweep (D-109: perceived, not floored).
                ctx.update { it.copy(targetBrightness = perceived) }
                if (settings.debugLevel == DebugCategory.SKIP_ANIMATIONS.level) {
                    brightness.write(target)
                    debug.emit(DebugCategory.SKIP_ANIMATIONS, settings.debugLevel) { "skip → $target" }
                } else {
                    debug.emit(DebugCategory.ANIMATION_DETAILS, settings.debugLevel) {
                        "animate $from→$target in ${output.animationSteps}×${output.animationWaitMs}ms"
                    }
                    // D-126: suppress override detection during post-init/resume settle window (F64).
                    val result = animationRunner.animate(
                        from = from,
                        to = target,
                        steps = output.animationSteps,
                        waitMs = output.animationWaitMs,
                        detectOverrides = settings.detectOverrides && !ctx.overrideSuppressed(),
                    )
                    if (result == AnimationRunner.Result.OVERRIDDEN) {
                        ctx.postOverrideDetected(brightness.read())
                        return
                    }
                }
            }

            // task646→650/645: F65 uses un-floored target, not PWM-floored hardware (task661/698 floor ⟂ task650).
            dimming.apply(output.targetBrightness, settings, output.scaleDynamic)
            // F58: dimming live readout.
            val (dimCurrent, dimDS) = dimmingReadout(output.targetBrightness, settings, output.scaleDynamic)

            // DC-001: cycle time is state (cycleTimeMs, Live Debug), not a Graph Metrics Flash —
            // %AAB_Debug 7 times chart (re)draws (features_spec §4), not this pipeline timer.
            val cycleTotal = (clock() - cycleStart).toDouble()
            // task566 / prof754: actual animation duration, not max steps×wait (G2R-F78).
            throttle.onCycleComplete(
                now = now,
                brightnessChanged = brightnessChanged,
                actualThrottleMs = output.transitionDurationMs,
                ceilingMs = throttle.ceiling(settings.animSteps, settings.maxWaitMs),
            )
            ctx.update {
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
                    // lastAppliedBrightness = hardware (floored); targetBrightness = perceived (D-109).
                    lastAppliedBrightness = target,
                    targetBrightness = perceived,
                    dimmingCurrent = dimCurrent,
                    dimmingDS = dimDS,
                    luxAlpha = output.luxAlpha,
                    animationSteps = output.animationSteps,
                    animationWaitMs = output.animationWaitMs,
                    throttleMs = throttle.throttleMs,
                    lastUpdateMs = clock(),
                )
            }
        } finally {
            ctx.update { it.copy(autoRunning = false) }
        }
    }

    /** task650 act28/act30: Super Dimming live readout (G2R-F58). @return (dimmingCurrent, dimmingDS) */
    private fun dimmingReadout(target: Int, settings: AabSettings, scaleDynamic: Double): Pair<Double, Double> {
        if (target >= settings.dimmingThreshold) return 0.0 to 0.0
        // G2R-F90: reflect the circadian-scaled dim_shell applied (task646 act7).
        val dimDynamic = circadianDimMultiplier(scaleDynamic, settings)
        return when {
            // PWM-sensitive: task700 finalDimLevel.
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
                    dimDynamic = dimDynamic,
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
        // UTC seconds-of-day (F73).
        val secondsOfDay = ((clock() / 1000L) % 86_400L).toDouble()
        val previous = if (s.smoothedLux != null && s.lastRawLux != null) {
            PreviousState(smoothedLux = s.smoothedLux, lastRawLux = s.lastRawLux, cycleTimeMs = s.cycleTimeMs)
        } else {
            null
        }
        // F73: real sunrise/sunset windows, not fixed defaults.
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
            // prof759/task545: damp the smoothing alpha ×0.1 while the proximity sensor reads near.
            proximityNear = s.proximityNear,
        )
    }

    /**
     * task567: Manual Override settle (D-049 #1, G2R-F26/F71). Don't borrow throttle window.
     * DB-082: the settle window is re-checked HERE too, not only where the change is observed —
     * observe → post → consume is asynchronous, so a change seen just before the window opened can
     * still arrive at the commit inside it.
     */
    suspend fun handleOverride(observed: Int) {
        if (!canPause(ctx.stateValue)) return

        val settleMs = (ctx.stateValue.cycleTimeMs?.toLong() ?: 0L).coerceAtLeast(0L)
        if (settleMs > 0) delay(settleMs)

        val s2 = ctx.stateValue
        if (!canPause(s2)) return
        val settled = brightness.read()
        // Settled to our last write → transient, not override (D-049 #1).
        if (s2.lastAppliedBrightness != null && settled == s2.lastAppliedBrightness) return

        val history = OverrideRules.recordOverridePoint(
            history = s2.overrideHistory,
            lux = s2.smoothedLux ?: 0.0,
            brightness = settled.toDouble(),
            dynamicCompress = s2.scaleDynamicCompress,
            scalingUse = s2.scalingUse,
        )
        brightness.clearSelfWriteMarker()
        dimming.disengage()
        // pausedByOverride: detected override (G2R-F35, D-044(c)).
        ctx.update { it.copy(paused = true, pausedByOverride = true, overrideHistory = history) }
        history.firstOrNull()?.let { (lux, bright) -> overrideSink.record(lux, bright) }
    }

    /** task618 block#1: Set Initial Brightness. */
    fun setInitialBrightness(settings: AabSettings) {
        val s = ctx.stateValue
        // DB-082: arm BEFORE the lux guard and before the write. Below the guard it never armed on
        // the one transition that needs it most — wake, where hibernate has just nulled both lux
        // fields — and below the write it left the write's own echo unsuppressed.
        ctx.armInitialSettle(clock() + INITIAL_SETTLE_MS)
        val lux = s.smoothedLux ?: s.lastRawLux ?: return
        ctx.update { it.copy(initializing = true) }
        try {
            val output = engine.evaluate(buildInput(lux, settings, PipelineState()))
            val target = applyPwmFloor(output.targetBrightness, settings)
            val perceived = output.targetBrightness
            brightness.forceManualMode()
            brightness.write(target)
            // F65: use un-floored target; F64: settle window.
            dimming.apply(output.targetBrightness, settings, output.scaleDynamic)
            // Re-arm from the END of the write so the full window covers the transition too.
            ctx.armInitialSettle(clock() + INITIAL_SETTLE_MS)
            ctx.update {
                it.copy(
                    lastAppliedBrightness = target,    // actual hardware write (floored)
                    targetBrightness = perceived,      // perceived read-out (D-109)
                    lastAcceptedMs = clock(),
                )
            }
        } finally {
            ctx.update { it.copy(initializing = false) }
        }
    }

    private fun canPause(s: PipelineState): Boolean = !ctx.overrideSuppressed() &&
        OverrideRules.shouldCommitPause(s.serviceOn, s.autoRunning, s.paused, s.initializing)

    /** task661 act22-26 / task698 step 3: hardware floor in PWM-sensitive mode (D-050, D-049 #4). */
    private fun applyPwmFloor(target: Int, settings: AabSettings): Int =
        if (settings.pwmSensitive && target < settings.dimmingThreshold) settings.dimmingThreshold else target

    // Tasker round3 idiom: Math.round(x*1000)/1000 (ties toward +∞).
    private fun round3(value: Double): Double = Math.round(value * 1000.0) / 1000.0

    internal companion object {
        // F64: settle window (1.5s). Also armed on wake (DB-082) — one number, one place.
        const val INITIAL_SETTLE_MS = 1500L
    }
}
