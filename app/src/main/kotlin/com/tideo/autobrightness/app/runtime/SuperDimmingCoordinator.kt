package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.domain.brightness.SoftwareDimming
import com.tideo.autobrightness.platform.brightness.SecureDimmingController
import com.tideo.autobrightness.platform.privilege.Tier

/**
 * The super-dimming layer wired into the runtime pipeline (S9b).
 *
 * In Tasker, super dimming drives the secure `reduce_bright_colors` setting below a brightness
 * threshold (task646 _CalculateSuperDimming → task650 _ApplyDimming) and clears it above the
 * threshold (task645 _DisableSuperDimming). The dimming MATH is the golden-tested domain
 * [SoftwareDimming]; the SECURE write is the platform [SecureDimmingController] (ELEVATED-gated).
 *
 * Concurrency (D-027): [apply]/[disengage] are invoked ONLY from the pipeline coroutine
 * ([BrightnessPipelineController]), so the engaged latch needs no synchronization.
 */
interface DimmingCoordinator {
    /** Engage or disengage super dimming for a freshly-computed [targetBrightness]. */
    fun apply(targetBrightness: Int, settings: AabSettings)

    /** Force-disengage (pause / override / panic / hibernate). */
    fun disengage()
}

/** Default no-op used when no dimming is wired (existing controller unit tests). */
object NoOpDimmingCoordinator : DimmingCoordinator {
    override fun apply(targetBrightness: Int, settings: AabSettings) = Unit
    override fun disengage() = Unit
}

class SuperDimmingCoordinator(
    private val secureDimming: SecureDimmingController,
    private val debugSink: DebugSink = NoOpDebugSink,
    private val tierProvider: () -> Tier,
) : DimmingCoordinator {

    // %AAB_DimmingStatus — 1 while reduce_bright_colors is engaged, else 0.
    private var engaged = false

    /**
     * task646 act0/act1: engage the Android "Extra Dim" (reduce_bright_colors) secure layer when the
     * target sits below the threshold and the tier can write secure settings; otherwise disengage.
     *
     * Two mutually-exclusive engage paths (the UI enforces the exclusivity, G2-F10):
     *  - **Super dimming** (`dimmingEnabled`): the level is the task646 `dim_shell`
     *    (clamped_strength × dim_progress).
     *  - **PWM-sensitive** (`pwmSensitive`, G2R-F65 REOPENED): the hardware brightness is held at the
     *    threshold by the pipeline ([BrightnessPipelineController.applyPwmFloor], task661 act22/698) AND
     *    the screen is darkened *below* that floor via Extra Dim using the task700 `finalDimLevel`
     *    (Map Lux to Brightness V2 act23 → "Software Dimming V2"). The previous rebuild only floored
     *    and never dimmed — this restores the dim-below-floor half.
     */
    override fun apply(targetBrightness: Int, settings: AabSettings) {
        val elevated = tierProvider() >= Tier.ELEVATED
        val belowThreshold = targetBrightness < settings.dimmingThreshold
        val pwmPath = settings.pwmSensitive && belowThreshold
        val superPath = settings.dimmingEnabled && belowThreshold
        val wantsDim = pwmPath || superPath
        val shouldEngage = wantsDim && elevated

        // %AAB_Debug 6 "Overlay Preview" (G2R-F49): when dimming WOULD engage but the tier can't write
        // secure settings, Tasker fell back to a semi-transparent screen overlay (the AAB Color Filter
        // scene, task653/654). The overlay window is deferred (D-040); surface its computed colour so
        // the unprivileged user can still see what the fallback would do.
        if (wantsDim && !elevated) emitOverlayPreview(targetBrightness, settings)

        if (!shouldEngage) {
            // %AAB_Debug 5: surface WHY dimming is not on (G2-F9 device diagnosis) — most often the
            // tier or the threshold gate, not the secure write itself.
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

        val level: Int = if (pwmPath) {
            // task700 "Software Dimming V2" (task661 act23): the reduce_bright_colors level for the
            // PWM-floored hardware. finalDimLevel is in the privileged 0–99 secure range when elevated.
            Math.round(
                SoftwareDimming.finalDimLevel(
                    targetBrightness = targetBrightness.toDouble(),
                    isElevated = true,
                    dimmingThreshold = settings.dimmingThreshold.toDouble(),
                    pwmExp = settings.pwmExponent.toDouble(),
                ),
            ).toInt()
        } else {
            // task646 act3-16: dim_shell = clamped_strength × dim_progress.
            // DimDynamic (the circadian strength multiplier, task646 act6 ScalingUse branch) is wired
            // with the real solar windows in S12 (D-040); the baseline/default config has scaling off,
            // so the plain-strength branch (dimDynamic = null) is the correct path here.
            Math.round(
                SoftwareDimming.dimShell(
                    brightness = targetBrightness.toDouble(),
                    minBrightness = settings.minBrightness.toDouble(),
                    dimmingThreshold = settings.dimmingThreshold.toDouble(),
                    dimmingExponent = settings.dimmingExponent.toDouble(),
                    dimmingStrength = settings.dimmingStrength.toDouble(),
                    dimDynamic = null,
                ),
            ).toInt()
        }

        // task650 act10-14: write reduce_bright_colors_activated=1 once, then the level each cycle.
        // NOTE (G2-F9, device gate): these are the AOSP "Extra dim" secure keys
        // (reduce_bright_colors_activated / reduce_bright_colors_level). Some OEM skins ship a
        // renamed/relocated key (or require the accessibility feature pre-enabled); if engagement
        // logs "ON" here (debug 5) but the screen does not visibly dim on a given device, that is OEM
        // secure-key variance, not a logic bug — see SecureDimmingController + STATE.md D-048.
        if (!engaged) {
            secureDimming.setActivated(true)
            engaged = true
        }
        secureDimming.setLevel(level)
        val mode = if (pwmPath) "PWM" else "SD"
        emitDebug(settings) { "ON ($mode) level $level (target $targetBrightness < ${settings.dimmingThreshold})" }
    }

    /** %AAB_Debug 5 "Super Dimming Info" (D-023, G2-F15). */
    private fun emitDebug(settings: AabSettings, message: () -> String) =
        debugSink.emit(DebugCategory.SUPER_DIMMING, settings.debugLevel, message)

    /**
     * %AAB_Debug 6 "Overlay Preview" (G2R-F49): Flash the computed overlay colour for the unprivileged
     * software-dimming fallback. Mirrors the Tasker math: dim_shell → dim_alpha_dec = 2.55 × dim_shell
     * (task653 act4) → "ARGB To Hex" black overlay `%AAB_HexOverlay` (task654, AAB Color Filter scene
     * = `#AA000000`). Computed via the golden-tested [SoftwareDimming.dimShell]; the overlay alpha is
     * the dim strength mapped onto 0–255.
     */
    private fun emitOverlayPreview(targetBrightness: Int, settings: AabSettings) {
        debugSink.emit(DebugCategory.OVERLAY_PREVIEW, settings.debugLevel) {
            val dimShell = SoftwareDimming.dimShell(
                brightness = targetBrightness.toDouble(),
                minBrightness = settings.minBrightness.toDouble(),
                dimmingThreshold = settings.dimmingThreshold.toDouble(),
                dimmingExponent = settings.dimmingExponent.toDouble(),
                dimmingStrength = settings.dimmingStrength.toDouble(),
                dimDynamic = null,
            )
            val alpha = Math.round(2.55 * dimShell).toInt().coerceIn(0, 255)
            "overlay ${"#%02X000000".format(alpha)}"
        }
    }

    /** task645: level→0 then activated→0; %AAB_DimmingStatus=0. */
    override fun disengage() {
        if (!engaged) return
        secureDimming.setLevel(0)
        secureDimming.setActivated(false)
        engaged = false
    }
}
