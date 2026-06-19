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

/**
 * The shared single-writer accessor surface for [PipelineState] (S12.9e). The orchestrator
 * [BrightnessPipelineController] implements it; [PipelineCycleRunner] reaches durable runtime state
 * through it so there is exactly one owner of the underlying `MutableStateFlow` — preserving the D-027
 * model that all state is written from the consumer coroutine.
 */
internal interface PipelineRuntimeContext {
    /** The current snapshot (atomic, untearable read). */
    val stateValue: PipelineState
    /** Apply [transform] to the snapshot (CAS-atomic). */
    fun update(transform: (PipelineState) -> PipelineState)
    /** Cache the effective settings for the per-sample prof760 gate (orchestrator-owned @Volatile). */
    fun cacheSettings(settings: AabSettings)
    /** Arm the post-init override-suppression window until [untilMs] (orchestrator-owned @Volatile). */
    fun armInitialSettle(untilMs: Long)
    /** Post a detected-override event back onto the consumer channel (mid-animation override). */
    fun postOverrideDetected(observed: Int)
}

/**
 * The pipeline's per-event math glue (S12.9e: extracted from BrightnessPipelineController so the
 * orchestrator keeps only the state machine). Owns the Tasker cycle work — Set Initial Brightness,
 * `runCycle` (sense → smooth → map → animate → super-dim), the manual-override settle, and the
 * super-dimming live readout — calling the golden-tested domain [BrightnessEngine] for all decisions.
 *
 * Runs entirely on the single pipeline consumer coroutine (D-027). Shared mutable runtime state is
 * reached through [PipelineRuntimeContext] (owned by the orchestrator) so there is one writer of
 * [PipelineState]; the collaborators (engine, brightness, animation, dimming, throttle, debug) are
 * injected so this stays unit-testable through the controller's existing tests.
 */
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
        // Throttle gate (task544 act2-9): drop ticks inside the THROTTLE window. The active window is
        // the runtime %AAB_Throttle (actual steps×wait, or the idle ceiling — G2R-F78), not the raw
        // setting; ThrottleController.seed() initialised it to the setting at start.
        s.lastAcceptedMs?.let { last ->
            if (now - last < throttle.throttleMs) return
        }

        val cycleStart = now
        ctx.update { it.copy(autoRunning = true) }
        try {
            val output = engine.evaluate(buildInput(rawLux, settings, s))
            val from = brightness.read()
            // task661 act22-26 / task698 step 3: hold the hardware floor in PWM-sensitive mode (D-050).
            val target = applyPwmFloor(output.targetBrightness, settings)

            // %AAB_Debug 3/4: Flash the light-evaluation + dynamic-scale figures (D-023, G2-F15).
            debug.emit(DebugCategory.LIGHT_EVAL, settings.debugLevel) {
                "lux ${round3(rawLux)}→${output.smoothedLux.toInt()} · thr ${output.thresholdLow.toInt()}–${output.thresholdHigh.toInt()} · →$target"
            }
            // %AAB_Debug 4 "Dynamic Scale Calcs": fire only ~2 min into a dawn/dusk transition (G2R-F48).
            debug.maybeDynamicScale(now, output.scaleDynamic, settings.debugLevel) {
                "scale ${round3(output.scaleDynamic)} · compress ${output.scaleDynamicCompress}"
            }

            val brightnessChanged = target != from
            if (target != from) {
                brightness.forceManualMode()
                // %AAB_Debug 1 "Skip Animations": jump straight to the target (Tasker debug mode).
                if (settings.debugLevel == DebugCategory.SKIP_ANIMATIONS.level) {
                    brightness.write(target)
                    debug.emit(DebugCategory.SKIP_ANIMATIONS, settings.debugLevel) { "skip → $target" }
                } else {
                    debug.emit(DebugCategory.ANIMATION_DETAILS, settings.debugLevel) {
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
                        ctx.postOverrideDetected(brightness.read())
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
            dimming.apply(output.targetBrightness, settings, output.scaleDynamic)
            // F58 live readout: %AAB_DimmingDS (abs reduce_bright_colors level) + %AAB_DimmingCurrent
            // (relative strength) for the Super Dimming screen, computed from the golden SoftwareDimming.
            val (dimCurrent, dimDS) = dimmingReadout(output.targetBrightness, settings, output.scaleDynamic)

            val cycleTotal = (clock() - cycleStart).toDouble()
            // %AAB_Debug 7 "Graph Metrics": Flash the measured cycle duration (feeds throttle).
            debug.emit(DebugCategory.GRAPH_METRICS, settings.debugLevel) { "cycle ${cycleTotal.toInt()}ms" }
            // task566 / prof754: %AAB_Throttle is the engine's ACTUAL animation duration this cycle
            // (transitionDurationMs = loops×wait+10+cycleTime, golden task543), NOT MaxSteps×MaxWait+10
            // (G2R-F78). After ~10 s of no brightness change the watchdog raises it to that ceiling.
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
            ctx.update { it.copy(autoRunning = false) }
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
    private fun dimmingReadout(target: Int, settings: AabSettings, scaleDynamic: Double): Pair<Double, Double> {
        if (target >= settings.dimmingThreshold) return 0.0 to 0.0
        // G2R-F90: the readout must reflect the SAME circadian-scaled dim_shell that is applied
        // (task646 act7), or the Super Dimming live values disagree with the actual Extra Dim level.
        val dimDynamic = circadianDimMultiplier(scaleDynamic, settings)
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
    suspend fun handleOverride(observed: Int) {
        val s = ctx.stateValue
        if (!OverrideRules.shouldCommitPause(s.serviceOn, s.autoRunning, s.paused, s.initializing)) return

        val settleMs = (s.cycleTimeMs?.toLong() ?: 0L).coerceAtLeast(0L)
        if (settleMs > 0) delay(settleMs)

        val s2 = ctx.stateValue
        // Re-check the gate: a cycle/pause/panic may have started during the settle wait.
        if (!OverrideRules.shouldCommitPause(s2.serviceOn, s2.autoRunning, s2.paused, s2.initializing)) return
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
        ctx.update { it.copy(paused = true, pausedByOverride = true, overrideHistory = history) }
        // Persist the captured training point (newest first) so the wizard + curve overlay have real
        // input across restarts (G2R-F13; closes D-044c).
        history.firstOrNull()?.let { (lux, bright) -> overrideSink.record(lux, bright) }
    }

    /** task618 block#1: set a starting brightness immediately (no smoothing loop) on wake/resume. */
    fun setInitialBrightness(settings: AabSettings) {
        val s = ctx.stateValue
        val lux = s.smoothedLux ?: s.lastRawLux ?: return
        ctx.update { it.copy(initializing = true) }
        try {
            // previous = null → engine's first-run path maps the reading straight to a target.
            val output = engine.evaluate(buildInput(lux, settings, PipelineState()))
            val target = applyPwmFloor(output.targetBrightness, settings)
            brightness.forceManualMode()
            brightness.write(target)
            // F65: dimming decides off the un-floored engine target, not the PWM-floored write (above).
            dimming.apply(output.targetBrightness, settings, output.scaleDynamic)
            // F64: arm the post-init settle window so the ContentObserver echo of THIS self-write
            // (and any AUTO→MANUAL mode-flip recompute) — delivered after `initializing` clears below
            // — is not flagged as a manual override on start/reinit/resume/context swap.
            ctx.armInitialSettle(clock() + INITIAL_SETTLE_MS)
            ctx.update {
                it.copy(
                    lastAppliedBrightness = target,
                    targetBrightness = target,
                    lastAcceptedMs = clock(),
                )
            }
        } finally {
            ctx.update { it.copy(initializing = false) }
        }
    }

    /**
     * task661 act22-26 / task698 step 3 hardware floor (D-050): when "Use software dimming
     * (PWM-sensitive)" is on and the target falls below the dimming threshold, hold the HARDWARE
     * brightness at the threshold (the panel's PWM-flicker floor). Further darkening is the
     * secure/overlay layer's job — not by writing a lower hardware value. The floor is in domain
     * space; [ScreenBrightnessController] maps it onto the device range (cf. D-049 #4).
     */
    private fun applyPwmFloor(target: Int, settings: AabSettings): Int =
        if (settings.pwmSensitive && target < settings.dimmingThreshold) settings.dimmingThreshold else target

    // Tasker round3 idiom: Math.round(x*1000)/1000 (ties toward +∞).
    private fun round3(value: Double): Double = Math.round(value * 1000.0) / 1000.0

    private companion object {
        // F64 settle window: long enough to outlast the async ContentObserver delivery of our own
        // initial write + the mode-flip recompute, short enough that a real user override moments
        // after a reinit is still caught by the next observer event.
        const val INITIAL_SETTLE_MS = 1500L
    }
}
