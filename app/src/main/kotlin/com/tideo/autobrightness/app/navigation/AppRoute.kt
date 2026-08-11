package com.tideo.autobrightness.app.navigation

import androidx.annotation.StringRes
import com.tideo.autobrightness.R

/** S12.6a (G2R-F1/F2/F3/F4): AAB Menu is now a real home screen (app hub). Dashboard is separate. Screens renamed: Animation & Dimming → Super Dimming; Dynamic Scale → Circadian. */
enum class AppRoute(
    val route: String,
    val label: String,
    @StringRes val titleRes: Int,
    val owner: String,
) {
    Menu("menu", "Menu", R.string.menu_title, "S12.6a"),
    Dashboard("dashboard", "Dashboard", R.string.title_dashboard, "S11"),
    Onboarding("onboarding", "Setup & Permissions", R.string.onboarding_title, "S11"),
    CurveBrightness("curve_brightness", "Curve & Brightness", R.string.title_curve_brightness, "S12"),
    Reactivity("reactivity", "Reactivity", R.string.title_reactivity, "S12"),
    // S12.6a rename (G2R-F3): the screen owns super dimming + PWM after S12.5b, so its name follows.
    SuperDimming("super_dimming", "Super Dimming", R.string.title_super_dimming, "S12.6a"),
    Circadian("circadian", "Circadian", R.string.title_circadian, "S12.6a"),
    // S12.5b: Misc screen hosts Tasker scene's brightness range, animation, notifications, debug fields.
    Misc("misc", "Misc", R.string.title_misc, "S12.5b"),
    Tools("tools", "Tools", R.string.title_tools, "S12"),
    // S12.6b (G2R-F6): AAB Debug scene rebuilt as glass-box Live Debug Info (runtime readout + debug-category selector).
    LiveDebug("live_debug", "Live Debug Info", R.string.title_live_debug, "S12.6b"),
    // S12.9f (D-070): Profiles + Contexts folded into one destination (rule editing in modal).
    Profiles("profiles", "Profiles & Contexts", R.string.title_profiles_contexts, "S12.9f"),
    // D-149: ELEVATED-only display toggles behind WRITE_SECURE_SETTINGS grant.
    PrivilegedDisplay("privileged_display", "Privileged Display", R.string.title_privileged_display, "D-149"),
    // S13d: static reference screens (extraction/scenes/about.md + user_guide.md); User Guide is post-onboarding destination (G2R-F80).
    UserGuide("user_guide", "User Guide", R.string.title_user_guide, "S13d"),
    About("about", "About", R.string.title_about, "S13d");

    companion object {
        /** Profiles & Contexts (one merged destination, S12.9f). */
        val heroDestinations: List<AppRoute> = listOf(Profiles)

        /** Tunable parameter screens (Menu "Settings" group). */
        val settingsDestinations: List<AppRoute> = listOf(
            CurveBrightness, Reactivity, SuperDimming, Circadian, Misc,
        )

        /** Tools + Live Debug + reference content (Menu "Info & Help" group). */
        val infoDestinations: List<AppRoute> = listOf(Tools, LiveDebug, UserGuide, About)

        /** ELEVATED-gated destinations (Menu "Privileged" group, D-149). */
        val privilegedDestinations: List<AppRoute> = listOf(PrivilegedDisplay)

        /** Unconditional navigation rows in Menu. Drives menu list + smoke tests. */
        val menuNavDestinations: List<AppRoute> =
            listOf(Dashboard) + settingsDestinations + infoDestinations
    }
}
