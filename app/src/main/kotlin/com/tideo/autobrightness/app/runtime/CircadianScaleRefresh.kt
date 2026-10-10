package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.toDynamicScalingConfig
import com.tideo.autobrightness.domain.brightness.BrightnessContext
import com.tideo.autobrightness.domain.brightness.BrightnessEngine
import com.tideo.autobrightness.domain.brightness.TimeContext
import com.tideo.autobrightness.domain.circadian.DynamicScaleResult

internal fun circadianTimeContext(nowMs: Long, windows: CircadianWindows?): TimeContext {
    val secondsOfDay = ((nowMs / 1000L) % 86_400L).toDouble()
    return windows?.let {
        TimeContext(secondsOfDay, it.morningStart, it.morningEnd, it.eveningStart, it.eveningEnd, it.sunlightDurationMinutes)
    } ?: TimeContext(secondsOfDay = secondsOfDay)
}

internal fun liveDynamicScale(nowMs: Long, windows: CircadianWindows?, settings: AabSettings): DynamicScaleResult =
    BrightnessEngine().dynamicScale(
        circadianTimeContext(nowMs, windows),
        settings.toDynamicScalingConfig(),
        BrightnessContext(isPolarDayNight = windows?.isPolar ?: false),
    )

/** Tasker: prof758 (2-min Time context) → task90 act80, Java #2; act82's task544 call is not ported (DD-063). */
internal class CircadianScaleRefresh(
    private val ctx: PipelineRuntimeContext,
    private val settingsProvider: suspend () -> AabSettings,
    private val circadianWindowsProvider: (transitionFactor: Double) -> CircadianWindows?,
    private val clock: () -> Long,
) {
    suspend fun refresh() {
        val s = ctx.stateValue
        if (!s.serviceOn || s.hibernated) return
        val settings = settingsProvider().also { ctx.cacheSettings(it) }
        if (!settings.serviceEnabled) return
        val windows = circadianWindowsProvider(settings.scaleTransitionFactor.toDouble())
        val now = clock()
        val time = circadianTimeContext(now, windows)
        val due = ProfileGates.dynamicScaleGate(
            nowMod = time.secondsOfDay,
            morningStart = time.morningStart,
            morningEnd = time.morningEnd,
            eveningStart = time.eveningStart,
            eveningEnd = time.eveningEnd,
            sunDataStale = windows == null,
            scalingUse = settings.scalingEnabled,
        )
        if (!due) return
        val scale = liveDynamicScale(now, windows, settings).scaleDynamic
        ctx.update { it.copy(scaleDynamic = scale) }
    }

    companion object {
        const val PERIOD_MS = 120_000L
    }
}
