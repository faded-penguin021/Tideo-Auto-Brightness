package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.platform.brightness.SecureDimmingController
import com.tideo.autobrightness.platform.privilege.Tier

/** Super-dimming layer for the pipeline (S9b); applies `reduce_bright_colors` below threshold (task646, task650, task645). D-027: no synchronization needed (pipeline-only). */
interface DimmingCoordinator {
    /** Engage/disengage based on [targetBrightness]; [dimDynamic] is the circadian dim-strength multiplier (task646 act6/act7). */
    fun apply(targetBrightness: Int, settings: AabSettings, dimDynamic: Double? = null)

    /** Force-disengage (pause / override / panic / hibernate); publishes a zero readout first (DE-044). */
    fun disengage()
}

object NoOpDimmingCoordinator : DimmingCoordinator {
    override fun apply(targetBrightness: Int, settings: AabSettings, dimDynamic: Double?) = Unit
    override fun disengage() = Unit
}

class SuperDimmingCoordinator(
    private val secureDimming: SecureDimmingController,
    private val debugSink: DebugSink = NoOpDebugSink,
    /** Re-detect privilege tier (DB-012): service tier cache only refreshes at resume; UI refresh is a different instance. */
    private val refreshTier: () -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
    private val readout: (DimmingReadout) -> Unit = {},
    private val tierProvider: () -> Tier,
) : DimmingCoordinator {

    // Nullable to avoid zero-sentinel ambiguity at boot; rate-limits tier re-detects on the cycle path.
    private var lastTierRefresh: Long? = null

    // D-144: null=UNKNOWN (fresh-process), false=known-off, true=engaged. Prevents stale Extra Dim after restart.
    private var engaged: Boolean? = null

    /** Engage/disengage Extra Dim below threshold (task646 act0/act1) and publish what is engaged (DE-044). */
    override fun apply(targetBrightness: Int, settings: AabSettings, dimDynamic: Double?) {
        val plan = DimmingPlan.of(targetBrightness, settings, dimDynamic)
        val wantsDim = plan != null

        // DB-012: re-detect tier cache if dimming wanted but tier says no (self-heal adb/Shizuku grants).
        var elevated = tierProvider() >= Tier.ELEVATED
        if (wantsDim && !elevated) {
            val now = clock()
            val last = lastTierRefresh
            if (last == null || now - last >= TIER_REFRESH_MIN_INTERVAL_MS) {
                lastTierRefresh = now
                refreshTier()
                elevated = tierProvider() >= Tier.ELEVATED
            }
        }
        // %AAB_Debug 6 (G2R-F49): show overlay preview when dimming wanted but tier can't write.
        if (wantsDim && !elevated) emitOverlayPreview(targetBrightness, settings, dimDynamic)

        if (plan == null || !elevated) {
            // %AAB_Debug 5 (G2-F9): log reason dimming is off.
            emitDebug(settings) {
                when {
                    !settings.dimmingEnabled && !settings.pwmSensitive -> "off: dimming disabled"
                    !elevated -> "off: needs WRITE_SECURE_SETTINGS"
                    else -> "off: $targetBrightness ≥ threshold ${settings.dimmingThreshold}"
                }
            }
            disengage()
            return
        }

        // G3-F6: level ≤ 0 means no dimming (disengage to avoid stale Android Extra Dim state).
        val level = plan.level
        if (level <= 0) {
            emitDebug(settings) { "off: computed dim level 0 (target $targetBrightness, daylight/zero-strength)" }
            disengage()
            return
        }

        // Write level before activation; fails retry via next cycle (DA-038). OEM key variance: G2-F9, D-048.
        val mode = plan.mode
        val levelWritten = secureDimming.setLevel(level).isSuccess
        if (!levelWritten) {
            // DB-001: failed write while engaged is dangerous (screen stays pinned at old level); fail safe to unknown.
            if (engaged == true) {
                val cleared = secureDimming.setActivated(false).isSuccess
                engaged = if (cleared) false else null
            }
            readout(DimmingReadout.NONE)
            emitDebug(settings) {
                "FAILED ($mode) level $level not written (target $targetBrightness) — Extra Dim cleared"
            }
            return
        }
        if (engaged != true) {
            if (secureDimming.setActivated(true).isSuccess) engaged = true
        }
        readout(if (engaged == true) plan.readout else DimmingReadout.NONE)
        emitDebug(settings) { "ON ($mode) level $level (target $targetBrightness < ${settings.dimmingThreshold})" }
    }

    /** %AAB_Debug 5 "Super Dimming Info" (D-023, G2-F15). */
    private fun emitDebug(settings: AabSettings, message: () -> String) =
        debugSink.emit(DebugCategory.SUPER_DIMMING, settings.debugLevel, message)

    /** %AAB_Debug 6 (G2R-F49): unprivileged fallback overlay, always from the ordinary dim_shell (→ overlay alpha). */
    private fun emitOverlayPreview(targetBrightness: Int, settings: AabSettings, dimDynamic: Double?) {
        debugSink.emit(DebugCategory.OVERLAY_PREVIEW, settings.debugLevel) {
            val alpha = Math.round(2.55 * DimmingPlan.shell(targetBrightness, settings, dimDynamic)).toInt().coerceIn(0, 255)
            "overlay ${"#%02X000000".format(alpha)}"
        }
    }

    /** task645: clears level and activation. D-144: runs from unknown to clear pre-death residual. */
    override fun disengage() {
        readout(DimmingReadout.NONE)
        if (engaged == false) return
        // Clear activation even if level fails (safety-critical); keep latch unknown if either fails to retry.
        val levelCleared = secureDimming.setLevel(0).isSuccess
        val deactivated = secureDimming.setActivated(false).isSuccess
        if (levelCleared && deactivated) engaged = false
    }

    private companion object {
        // DB-012: rate-limit tier re-detects; 10s imperceptible to user running `pm grant`.
        const val TIER_REFRESH_MIN_INTERVAL_MS = 10_000L
    }
}
