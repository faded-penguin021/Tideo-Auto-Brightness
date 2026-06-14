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
     * task646 act0/act1: engage only when dimming is enabled, the target sits below the threshold,
     * and the tier can write secure settings; otherwise disengage. Mirrors the
     * "below threshold engage / above threshold disengage" decider (pipeline_spec §1d step 5).
     */
    override fun apply(targetBrightness: Int, settings: AabSettings) {
        val elevated = tierProvider() >= Tier.ELEVATED
        val shouldEngage = settings.dimmingEnabled &&
            elevated &&
            targetBrightness < settings.dimmingThreshold

        if (!shouldEngage) {
            // %AAB_Debug 5: surface WHY dimming is not on (G2-F9 device diagnosis) — most often the
            // tier or the threshold gate, not the secure write itself.
            emitDebug(settings) {
                when {
                    !settings.dimmingEnabled -> "off: dimming disabled"
                    !elevated -> "off: needs WRITE_SECURE_SETTINGS"
                    else -> "off: $targetBrightness ≥ threshold ${settings.dimmingThreshold}"
                }
            }
            disengage()
            return
        }

        // task646 act3-16: dim_shell = clamped_strength × dim_progress.
        // DimDynamic (the circadian strength multiplier, task646 act6 ScalingUse branch) is wired
        // with the real solar windows in S12 (D-040); the baseline/default config has scaling off,
        // so the plain-strength branch (dimDynamic = null) is the correct path here.
        val dimShell = SoftwareDimming.dimShell(
            brightness = targetBrightness.toDouble(),
            minBrightness = settings.minBrightness.toDouble(),
            dimmingThreshold = settings.dimmingThreshold.toDouble(),
            dimmingExponent = settings.dimmingExponent.toDouble(),
            dimmingStrength = settings.dimmingStrength.toDouble(),
            dimDynamic = null,
        )

        // task650 act10-14: write reduce_bright_colors_activated=1 once, then the level each cycle.
        // NOTE (G2-F9, device gate): these are the AOSP "Extra dim" secure keys
        // (reduce_bright_colors_activated / reduce_bright_colors_level). Some OEM skins ship a
        // renamed/relocated key (or require the accessibility feature pre-enabled); if engagement
        // logs "ON" here (debug 5) but the screen does not visibly dim on a given device, that is OEM
        // secure-key variance, not a logic bug — see SecureDimmingController + STATE.md D-048.
        val level = Math.round(dimShell).toInt()
        if (!engaged) {
            secureDimming.setActivated(true)
            engaged = true
        }
        secureDimming.setLevel(level)
        emitDebug(settings) { "ON level $level (target $targetBrightness < ${settings.dimmingThreshold})" }
    }

    /** %AAB_Debug 5 "Super Dimming Info" (D-023, G2-F15). */
    private fun emitDebug(settings: AabSettings, message: () -> String) =
        debugSink.emit(DebugCategory.SUPER_DIMMING, settings.debugLevel, message)

    /** task645: level→0 then activated→0; %AAB_DimmingStatus=0. */
    override fun disengage() {
        if (!engaged) return
        secureDimming.setLevel(0)
        secureDimming.setActivated(false)
        engaged = false
    }
}
