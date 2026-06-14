package com.tideo.autobrightness.app.ui.screens

/**
 * Verbatim Tasker long-press help text (S12.6e, G2R-F19/F20/F21).
 *
 * Every AAB settings-scene label carries a `longclick` help task that Flashes (action code 548) an
 * explanatory string. These constants are the decoded, **verbatim** Flash text from those tasks — the
 * single source of truth for the parameter tooltips, surfaced via [com.tideo.autobrightness.app.ui
 * .components.NumberSettingField] `help=`. Do NOT paraphrase; changing a string requires re-deriving
 * it from the XML (extraction/scenes + the help task id noted on each line).
 *
 * Provenance: extraction/scenes/{reactivity,brightness,misc,superdimming}_settings.md → longclick task.
 */
object TaskerHelp {
    // --- Reactivity scene (extraction/scenes/reactivity_settings.md) -----------------------------
    /** task719 — %aab_threshdarkpc. */
    const val THRESH_DARK =
        "The reactivity level in complete darkness. A higher value means the brightness is more " +
            "stable and less 'jumpy' in very dark rooms."

    /** task720 — %aab_threshdimpc. */
    const val THRESH_DIM =
        "The baseline reactivity for most situations. A lower value makes the system more " +
            "responsive to small decreases in light."

    /** task721 — %aab_threshbrightpc. */
    const val THRESH_BRIGHT =
        "The baseline reactivity for bright, outdoor light. A higher value prevents tiny shadows " +
            "(like your hand) from causing annoying brightness dips."

    /** task722 — %AAB_ThreshSteepness. */
    const val CURVE_SLOPE =
        "Controls how quickly the reactivity changes as you move from dim to bright light. " +
            "Higher value = more abrupt transition."

    /** task712 — %AAB_ThreshMidpoint. */
    const val CURVE_MID =
        "The log-transformed lux level where the system is most sensitive to change. Think of it " +
            "as the 'center point' of the reactivity curve."

    /** task729 — %AAB_TrustUnreliable. */
    const val TRUST_UNRELIABLE =
        "Enable this ONLY if auto-brightness seems unresponsive on your device. This may cause " +
            "brightness to become unstable or flicker."

    /** task519 — %AAB_DetectOverrides. */
    const val DETECT_OVERRIDES =
        "Disable this if you get frequent false positives for the override detection."

    // --- Misc scene (extraction/scenes/misc_settings.md) -----------------------------------------
    /** task740 — %AAB_DeltaFactor ("Smoothing Δ"). The label/help the owner flagged (G2R-F19). */
    const val DELTA_FACTOR =
        "Controls how much to smooth out sensor readings. Higher values react faster to small " +
            "light changes, but may increase jitter. Lower values are more stable, but might feel " +
            "sluggish."

    /** task723 — %AAB_MinBright. */
    const val MIN_BRIGHT =
        "Sets the lowest brightness level the screen will ever use. Recommended to be > 0 on OLED " +
            "screens to avoid 'black crush'."

    /** task536 — %AAB_MaxBright. */
    const val MAX_BRIGHT =
        "Sets the highest brightness level the screen will ever use. Can be used to cap the max " +
            "brightness to save battery."

    /** task732 — %AAB_Offset. */
    const val OFFSET =
        "A simple brightness boost or cut. This value is added to (or subtracted from) the final " +
            "brightness across all light levels."

    /** task730 — %AAB_Scale. */
    const val SCALE =
        "A global multiplier for the entire brightness curve. Use it to adjust the overall " +
            "contrast. >1 stretches the curve brighter, <1 compresses it dimmer."

    /** task726 — %AAB_AnimSteps. */
    const val ANIM_STEPS =
        "The number of steps in a brightness change animation. More steps create a smoother, but " +
            "slightly slower, transition."

    /** task728 — %AAB_MaxWait. */
    const val MAX_WAIT =
        "The longest time (in milliseconds) between animation steps. Controls how slowly small, " +
            "subtle brightness changes occur."

    /** task727 — %AAB_MinWait. */
    const val MIN_WAIT =
        "The shortest time (in milliseconds) between animation steps. Controls how fast large " +
            "brightness changes feel."

    /** task682 — %AAB_NotifyUse. */
    const val NOTIFY_USE =
        "Use notifications to keep the service alive and show 'paused' notifications."

    // --- Brightness scene (extraction/scenes/brightness_settings.md) ------------------------------
    /** task725 — %AAB_Form1A ("Zone 1 Scaling"). */
    const val FORM_1A =
        "Controls how quickly brightness rises in dim light. Recommended: 1 to 5"

    /** task747 — %AAB_Zone1End. */
    const val ZONE_1_END =
        "Sets the lux level where 'dim light' ends and 'indoor light' begins. Typically for moving " +
            "from a dark room to a lit one."

    /** task737 — %AAB_Form2B ("Zone 2 Scaling"). */
    const val FORM_2B =
        "Higher values give a major boost in medium light. Recommended: 3 to 10"

    /** task741 — %AAB_Form2C ("Zone 2 Offset"). */
    const val FORM_2C =
        "Subtle but powerful \"offset\" for the midrange curve. Lower values make the transition " +
            "from dark to dim light more gradual and smooth. Recommended: -300 to zone 1 end."

    /** task750 — %AAB_Zone2End. */
    const val ZONE_2_END =
        "Sets the lux level where 'indoor light' ends and 'bright outdoor light' begins. Affects " +
            "the transition when going outside."

    // --- Superdimming scene (extraction/scenes/superdimming_settings.md) --------------------------
    /** task465 — %AAB_DimmingStrength. */
    const val DIMMING_STRENGTH =
        "Controls the maximum super dimming strength. Value shows what happens without circadian " +
            "effects."

    /** task505 — %AAB_DimSpread. */
    const val DIM_SPREAD =
        "Controls how wide the scale shifts over the day. Only active when circadian scaling is " +
            "enabled!"

    /** task506 — %AAB_DimmingExponent. */
    const val DIMMING_EXPONENT =
        "Controls how gradual the super dimming kicks in and how it behaves as brightness " +
            "approaches min brightness. <1: hard transition, not recommended. 1: linear transition. " +
            ">1 soft(er) transition."

    /** task421 — %AAB_DimmingThreshold. */
    const val DIMMING_THRESHOLD =
        "This is the screen brightness below which super dimming kicks in."

    /** task510 — %AAB_DimmingEnabled. */
    const val DIMMING_ENABLED =
        "Enables or disables the entire experimental circadian scaling feature. When disabled the " +
            "strength setpoint is used as a maximum value throughout the day."

    /** task702 — %AAB_PWMExp ("Software exp."). */
    const val PWM_EXPONENT =
        "Gamma-like exponent. Controls the shape of the dimming curve. Higher values keep the " +
            "screen brighter for longer and fade more steeply near the threshold; lower values dim " +
            "more quickly. Adjusts how brightness transitions, not how much(!)"

    /** task529 — %AAB_PWMSensitive. */
    const val PWM_SENSITIVE =
        "Enables or disables the software dimming feature. When enabled the max hardware brightness " +
            "is fixated to the PWM threshold."
}
