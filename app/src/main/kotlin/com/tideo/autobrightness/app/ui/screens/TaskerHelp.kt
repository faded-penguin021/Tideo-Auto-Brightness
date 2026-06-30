package com.tideo.autobrightness.app.ui.screens

import androidx.annotation.StringRes
import com.tideo.autobrightness.R

/**
 * Verbatim Tasker long-press help text (S12.6e, G2R-F19/F20/F21).
 *
 * Every AAB settings-scene label carries a `longclick` help task that Flashes (action code 548) an
 * explanatory string. These map each parameter to the **verbatim** Flash text — the single source of
 * truth for the parameter tooltips — surfaced via the `help=` arg of the settings field primitives.
 *
 * D-131 (i18n): the text moved to `strings.xml` (`help_*`) so it is translatable; these constants are
 * now the matching `@StringRes` ids. Do NOT paraphrase; changing a string requires re-deriving it from
 * the XML (extraction/scenes + the help task id noted on each line).
 *
 * Provenance: extraction/scenes/{reactivity,brightness,misc,superdimming}_settings.md → longclick task.
 */
object TaskerHelp {
    // --- Reactivity scene (extraction/scenes/reactivity_settings.md) -----------------------------
    /** task719 — %aab_threshdarkpc. */
    @StringRes val THRESH_DARK = R.string.help_thresh_dark

    /** task720 — %aab_threshdimpc. */
    @StringRes val THRESH_DIM = R.string.help_thresh_dim

    /** task721 — %aab_threshbrightpc. */
    @StringRes val THRESH_BRIGHT = R.string.help_thresh_bright

    /** task722 — %AAB_ThreshSteepness. */
    @StringRes val CURVE_SLOPE = R.string.help_curve_slope

    /** task712 — %AAB_ThreshMidpoint. */
    @StringRes val CURVE_MID = R.string.help_curve_mid

    /** task729 — %AAB_TrustUnreliable. */
    @StringRes val TRUST_UNRELIABLE = R.string.help_trust_unreliable

    /** task519 — %AAB_DetectOverrides. */
    @StringRes val DETECT_OVERRIDES = R.string.help_detect_overrides

    // --- Misc scene (extraction/scenes/misc_settings.md) -----------------------------------------
    /** task740 — %AAB_DeltaFactor ("Smoothing Δ"). The label/help the owner flagged (G2R-F19). */
    @StringRes val DELTA_FACTOR = R.string.help_delta_factor

    /** task723 — %AAB_MinBright. */
    @StringRes val MIN_BRIGHT = R.string.help_min_bright

    /** task536 — %AAB_MaxBright. */
    @StringRes val MAX_BRIGHT = R.string.help_max_bright

    /** task732 — %AAB_Offset. */
    @StringRes val OFFSET = R.string.help_offset

    /** task730 — %AAB_Scale. */
    @StringRes val SCALE = R.string.help_scale

    /** task726 — %AAB_AnimSteps. */
    @StringRes val ANIM_STEPS = R.string.help_anim_steps

    /** task728 — %AAB_MaxWait. */
    @StringRes val MAX_WAIT = R.string.help_max_wait

    /** task727 — %AAB_MinWait. */
    @StringRes val MIN_WAIT = R.string.help_min_wait

    /** task682 — %AAB_NotifyUse. */
    @StringRes val NOTIFY_USE = R.string.help_notify_use

    // --- Brightness scene (extraction/scenes/brightness_settings.md) ------------------------------
    /** task725 — %AAB_Form1A ("Zone 1 Scaling"). */
    @StringRes val FORM_1A = R.string.help_form_1a

    /** task747 — %AAB_Zone1End. */
    @StringRes val ZONE_1_END = R.string.help_zone_1_end

    /** task737 — %AAB_Form2B ("Zone 2 Scaling"). */
    @StringRes val FORM_2B = R.string.help_form_2b

    /** task741 — %AAB_Form2C ("Zone 2 Offset"). */
    @StringRes val FORM_2C = R.string.help_form_2c

    /** task750 — %AAB_Zone2End. */
    @StringRes val ZONE_2_END = R.string.help_zone_2_end

    // --- Superdimming scene (extraction/scenes/superdimming_settings.md) --------------------------
    /** task465 — %AAB_DimmingStrength. */
    @StringRes val DIMMING_STRENGTH = R.string.help_dimming_strength

    /** task505 — %AAB_DimSpread. */
    @StringRes val DIM_SPREAD = R.string.help_dim_spread

    /** task506 — %AAB_DimmingExponent. */
    @StringRes val DIMMING_EXPONENT = R.string.help_dimming_exponent

    /** task421 — %AAB_DimmingThreshold. */
    @StringRes val DIMMING_THRESHOLD = R.string.help_dimming_threshold

    /** task510 — %AAB_DimmingEnabled. */
    @StringRes val DIMMING_ENABLED = R.string.help_dimming_enabled

    /** task702 — %AAB_PWMExp ("Software exp."). */
    @StringRes val PWM_EXPONENT = R.string.help_pwm_exponent

    /** task529 — %AAB_PWMSensitive. */
    @StringRes val PWM_SENSITIVE = R.string.help_pwm_sensitive
}
