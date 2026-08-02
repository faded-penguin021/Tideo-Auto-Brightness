package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.domain.brightness.SoftwareDimming
import com.tideo.autobrightness.platform.brightness.SecureDimmingController
import com.tideo.autobrightness.platform.privilege.Tier
import java.math.BigDecimal
import java.math.RoundingMode

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
    /**
     * Engage or disengage super dimming for a freshly-computed [targetBrightness].
     *
     * [scaleDynamic] is the cycle's `%AAB_ScaleDynamic` (the engine output; 1.0 when scaling is off).
     * It drives the circadian dim-strength multiplier `%AAB_DimDynamic` (task646 act6/act7); the
     * neutral default 1.0 ⇒ no circadian effect, matching the scaling-off path.
     */
    fun apply(targetBrightness: Int, settings: AabSettings, scaleDynamic: Double = 1.0)

    /** Force-disengage (pause / override / panic / hibernate). */
    fun disengage()
}

/** Default no-op used when no dimming is wired (existing controller unit tests). */
object NoOpDimmingCoordinator : DimmingCoordinator {
    override fun apply(targetBrightness: Int, settings: AabSettings, scaleDynamic: Double) = Unit
    override fun disengage() = Unit
}

/**
 * task646 act6/act7 + task90 Block #2: the `%AAB_DimDynamic` circadian strength multiplier applied to
 * `%AAB_DimmingStrength` before the [SoftwareDimming.dimShell] clamp, but ONLY when `%AAB_ScalingUse`
 * (= [AabSettings.scalingEnabled]) is on (act9 otherwise uses plain strength → `null`).
 *
 * `%AAB_DimDynamic` and `%AAB_ScaleDynamic` are computed together in task90 from the SAME tanh
 * day/night `modifier` (0 = night → 1 = day):
 * ```
 *   ScaleDynamic = 1 + (ScaleSpread/100)·modifier      // published in PipelineState / engine output
 *   DimDynamic   = 1 − (DimSpread/100)·modifier         // %AAB_DimDynamic (task90 emits 2 − (1 + …))
 * ```
 * Eliminating the shared `modifier` (ScaleSpread ∈ 1..100 by contract, so never zero) gives an exact,
 * scale-spread-independent expression in terms of the published scale:
 * ```
 *   DimDynamic = 1 − (DimSpread/ScaleSpread)·(ScaleDynamic − 1)
 * ```
 * Rounded HALF_UP to 3 decimals to match Tasker's stored `%AAB_DimDynamic` (task90 BigDecimal scale 3).
 *
 * Semantics (G2R-F90): DimSpread 100 → suppress dimming proportionally to daylight (day → 0);
 * DimSpread 0 → engage normally (always 1.0); DimSpread −100 → boost during daylight (day → 2.0).
 * Previously [SuperDimmingCoordinator] hardcoded `dimDynamic = null` (D-040), leaving circadian
 * dimming inert: Spread had no effect and dimming engaged at full strength even in daylight.
 */
internal fun circadianDimMultiplier(scaleDynamic: Double, settings: AabSettings): Double? {
    if (!settings.scalingEnabled || settings.scaleSpread == 0) return null
    val raw = 1.0 - (settings.dimSpread.toDouble() / settings.scaleSpread) * (scaleDynamic - 1.0)
    return BigDecimal(raw).setScale(3, RoundingMode.HALF_UP).toDouble()
}

class SuperDimmingCoordinator(
    private val secureDimming: SecureDimmingController,
    private val debugSink: DebugSink = NoOpDebugSink,
    /**
     * Re-detect the privilege tier (DB-012). The service's cached tier (G1-F5) is refreshed only at
     * its own resume points — service start and screen-on — and `AppModule` is constructed per call
     * site, so the *UI's* `privilegeManager.refresh()` updates a DIFFERENT instance. A grant made
     * over adb/Shizuku with the screen on therefore stayed invisible to the running service until a
     * screen-off/on or an app restart; the device pass hit exactly that ("kept complaining about
     * write secure settings even though i regranted; restarting the app resolved that").
     */
    private val refreshTier: () -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
    // Last so the `SuperDimmingCoordinator(secure) { Tier.ELEVATED }` trailing-lambda form keeps working.
    private val tierProvider: () -> Tier,
) : DimmingCoordinator {

    // Last time a cache-miss re-detect ran. Bounds the Binder cost: the refresh below sits on the
    // pipeline's cycle path, and an un-granted user who leaves dimming enabled hits it every cycle.
    // Nullable, not 0L: a test clock (and a device clock read moments after boot) starts AT 0, so a
    // zero sentinel would swallow the very first re-detect. Nullable also avoids the Long.MIN_VALUE
    // sentinel's overflow on `now - last`.
    private var lastTierRefresh: Long? = null

    // %AAB_DimmingStatus — true while reduce_bright_colors is engaged, false when known-off.
    // D-144: null = UNKNOWN, the fresh-process state. Tasker's %AAB_DimmingStatus is a PERSISTED
    // global, so after a Tasker restart the disable path still wrote the secure keys; an in-process
    // `false` start would instead early-return the first disengage and leave a pre-death engagement
    // (Extra Dim) stuck on after the sticky restart. From UNKNOWN, the first disengage writes the
    // clears (idempotent if already off) and the first engage writes activated=1.
    private var engaged: Boolean? = null

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
    override fun apply(targetBrightness: Int, settings: AabSettings, scaleDynamic: Double) {
        val belowThreshold = targetBrightness < settings.dimmingThreshold
        val pwmPath = settings.pwmSensitive && belowThreshold
        val superPath = settings.dimmingEnabled && belowThreshold
        val wantsDim = pwmPath || superPath

        // DB-012: the ONE moment a stale tier cache is visibly wrong — the user asked for dimming and
        // we believe we may not write. Re-detect (rate-limited) instead of waiting for the next
        // screen-on, so an adb/Shizuku grant made while the screen is on self-heals within a cycle.
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
        val shouldEngage = wantsDim && elevated

        // task646 act6/act7: the circadian DimDynamic multiplier (G2R-F90 — was hardcoded null, D-040).
        val dimDynamic = circadianDimMultiplier(scaleDynamic, settings)

        // %AAB_Debug 6 "Overlay Preview" (G2R-F49): when dimming WOULD engage but the tier can't write
        // secure settings, Tasker fell back to a semi-transparent screen overlay (the AAB Color Filter
        // scene, task653/654). The overlay window is deferred (D-040); surface its computed colour so
        // the unprivileged user can still see what the fallback would do.
        if (wantsDim && !elevated) emitOverlayPreview(targetBrightness, settings, dimDynamic)

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
            // task646 act3-16: dim_shell = clamped_strength × dim_progress, where clamped_strength is
            // DimmingStrength × DimDynamic when %AAB_ScalingUse is on (act7) — the circadian scale
            // (G2R-F90); else plain DimmingStrength (act9, dimDynamic = null).
            Math.round(
                SoftwareDimming.dimShell(
                    brightness = targetBrightness.toDouble(),
                    minBrightness = settings.minBrightness.toDouble(),
                    dimmingThreshold = settings.dimmingThreshold.toDouble(),
                    dimmingExponent = settings.dimmingExponent.toDouble(),
                    dimmingStrength = settings.dimmingStrength.toDouble(),
                    dimDynamic = dimDynamic,
                ),
            ).toInt()
        }

        // G3-F6 (Gate 3): a computed level of 0 means "no dimming". At circadian Spread 100 in full
        // daylight the DimDynamic multiplier drives clamped_strength→0, so dim_shell rounds to 0; writing
        // reduce_bright_colors_activated=1 with level 0 still leaves Android's Extra Dim engaged and the
        // owner saw a residual daytime dim (reduce_bright_colors only takes integer levels). Treat level
        // ≤ 0 as disengage so daytime/zero-strength is truly undimmed.
        if (level <= 0) {
            emitDebug(settings) { "off: computed dim level 0 (target $targetBrightness, daylight/zero-strength)" }
            disengage()
            return
        }

        // Write the bounded level BEFORE activation. If the process dies between the two writes the
        // feature remains off; the reverse order can briefly apply an OEM/default stale level and
        // produce an extreme unintended dim. Do not advance the latch on a failed write: a revoked
        // permission or SettingsProvider failure must be retried by the next cycle/cleanup rather
        // than being mistaken for successfully engaged state (DA-038).
        // NOTE (G2-F9, device gate): these are the AOSP "Extra dim" secure keys
        // (reduce_bright_colors_activated / reduce_bright_colors_level). Some OEM skins ship a
        // renamed/relocated key (or require the accessibility feature pre-enabled); if engagement
        // logs "ON" here (debug 5) but the screen does not visibly dim on a given device, that is OEM
        // secure-key variance, not a logic bug — see SecureDimmingController + STATE.md D-048.
        val mode = if (pwmPath) "PWM" else "SD"
        val levelWritten = secureDimming.setLevel(level).isSuccess
        if (!levelWritten) {
            // DB-001: a failed level write while ALREADY engaged is the dangerous case, and the
            // previous code was silent about it — the latch was already `true`, so it skipped
            // activation and returned, leaving the screen pinned at the PREVIOUS level. Since levels
            // fall as the target rises, that stale level is typically STRONGER than the one just
            // requested: the user brightens the room, the write fails, and the screen stays dark.
            // Fail safe instead: drop to a known-off state (best effort) and mark the latch UNKNOWN
            // so the next cycle re-engages from scratch rather than trusting a level it never wrote.
            // ONLY when we know we are engaged. From UNKNOWN (fresh process) or known-off, a failed
            // level write means nothing of ours is on screen, and writing activation here would be a
            // secure write we have no reason to make — the pre-existing "a missing level must never
            // activate an unknown OEM level" invariant covers that case and still holds.
            if (engaged == true) {
                val cleared = secureDimming.setActivated(false).isSuccess
                engaged = if (cleared) false else null
            }
            emitDebug(settings) {
                "FAILED ($mode) level $level not written (target $targetBrightness) — Extra Dim cleared"
            }
            return
        }
        if (engaged != true) {
            if (secureDimming.setActivated(true).isSuccess) engaged = true
        }
        // Report ON only for a level that actually reached the secure setting. The old message was
        // emitted unconditionally, so a failed write still logged "ON <new level>" and sent device
        // diagnosis after the wrong suspect (DB-001).
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
    private fun emitOverlayPreview(targetBrightness: Int, settings: AabSettings, dimDynamic: Double?) {
        debugSink.emit(DebugCategory.OVERLAY_PREVIEW, settings.debugLevel) {
            val dimShell = SoftwareDimming.dimShell(
                brightness = targetBrightness.toDouble(),
                minBrightness = settings.minBrightness.toDouble(),
                dimmingThreshold = settings.dimmingThreshold.toDouble(),
                dimmingExponent = settings.dimmingExponent.toDouble(),
                dimmingStrength = settings.dimmingStrength.toDouble(),
                dimDynamic = dimDynamic,
            )
            val alpha = Math.round(2.55 * dimShell).toInt().coerceIn(0, 255)
            "overlay ${"#%02X000000".format(alpha)}"
        }
    }

    /** task645: level→0 then activated→0; %AAB_DimmingStatus=0. Runs from engaged OR unknown (D-144):
     *  only a known-off latch skips the writes, so a fresh process clears any pre-death residual. */
    override fun disengage() {
        if (engaged == false) return
        // Clear activation even when the level clear fails: OFF is the safety-critical write. Keep
        // the latch unknown unless BOTH writes succeeded, so a later stop/cycle retries cleanup.
        val levelCleared = secureDimming.setLevel(0).isSuccess
        val deactivated = secureDimming.setActivated(false).isSuccess
        if (levelCleared && deactivated) engaged = false
    }

    private companion object {
        /**
         * Floor between cache-miss tier re-detects (DB-012). `detectTier()` is a Binder permission
         * check; 10 s is imperceptible to someone who just ran `pm grant` and keeps the cost off a
         * per-cycle path for an unprivileged user who leaves dimming enabled.
         */
        const val TIER_REFRESH_MIN_INTERVAL_MS = 10_000L
    }
}
