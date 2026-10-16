package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.toBrightnessCurveConfig
import com.tideo.autobrightness.domain.brightness.BrightnessCurveConfig
import com.tideo.autobrightness.domain.brightness.HandoffRules
import com.tideo.autobrightness.domain.brightness.HandoffRules.Decision
import com.tideo.autobrightness.platform.brightness.ScreenBrightnessController

internal class HandoffCoordinator(
    private val ctx: PipelineRuntimeContext,
    private val brightness: ScreenBrightnessController,
    private val dimming: DimmingCoordinator,
    private val clock: () -> Long,
) {
    private var minimumByCurve: Pair<BrightnessCurveConfig, Int?>? = null

    // DD-074: an entry and stop()'s serviceOn flip serialize here.
    private val teardown = Any()

    fun fence(stopping: () -> Unit) = synchronized(teardown, stopping)

    /** True while Android owns the screen, so the caller writes nothing. */
    fun ownsScreen(settings: AabSettings, lux: Double): Boolean {
        val decision = HandoffRules.next(
            active = ctx.stateValue.handedOff,
            enabled = settings.handoffEnabled,
            smoothedLux = lux,
            effectiveThreshold = effectiveThreshold(settings),
            hysteresis = settings.handoffHysteresis,
        )
        return when (decision) {
            Decision.ENTER -> enter()
            Decision.STAY -> true
            Decision.EXIT -> !exit()
            Decision.NONE -> false
        }.also { owned -> if (owned) dimming.disengage() }
    }

    fun releaseWithoutLux(settings: AabSettings) {
        if (!ctx.stateValue.handedOff) return
        if (!settings.handoffEnabled || effectiveThreshold(settings) == null) exit()
    }

    fun release() {
        if (ctx.stateValue.handedOff) exit()
    }

    // Tasker: "_Hand-offToAdaptive" (AAB hand-off export, DD-070).
    private fun enter(): Boolean = synchronized(teardown) {
        if (!ctx.stateValue.serviceOn) return false
        ctx.update { it.copy(handedOff = true) }
        armSettle()
        dimming.disengage()
        val landed = brightness.handOffToSystem()
        armSettle()
        ctx.update { if (landed) it.copy(lastAppliedBrightness = null) else it.copy(handedOff = false) }
        landed
    }

    // Tasker: "_HandBackToAAB" (AAB hand-off export, DD-070).
    private fun exit(): Boolean {
        armSettle()
        if (!brightness.reclaimFromSystem()) return false
        armSettle()
        ctx.update { it.copy(handedOff = false) }
        return true
    }

    // DC-009, re-armed after the flip as Set Initial Brightness does.
    private fun armSettle() = ctx.armInitialSettle(clock() + PipelineCycleRunner.INITIAL_SETTLE_MS)

    private fun effectiveThreshold(settings: AabSettings): Int? {
        if (!settings.handoffEnabled) return null
        val cfg = settings.toBrightnessCurveConfig()
        val cached = minimumByCurve
        val lowest = if (cached?.first == cfg) cached.second else HandoffRules.minimumHandoffLux(cfg).also { minimumByCurve = cfg to it }
        return lowest?.let { maxOf(settings.handoffLux, it) }
    }
}
