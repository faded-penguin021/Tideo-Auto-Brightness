package com.tideo.autobrightness.app.navigation

import androidx.annotation.StringRes
import com.tideo.autobrightness.R

/**
 * The target Material 3 screen set from `docs/rebuild/screen_map.md`.
 *
 * S12.6a (G2R-F1/F2/F3/F4): the **AAB Menu is now a real home screen** ([Menu]) — the app hub that
 * the user returns to from every settings/tool screen (not the Dashboard). The Dashboard is a
 * separate live-status destination reached from the Menu. Two screens are renamed to match Tasker:
 * `Animation & Dimming` → **Super Dimming** and `Dynamic Scale` → **Circadian**.
 */
// [label] is the English name (used by tests + internal logging); [titleRes] is the i18n string shown
// in the UI (D-131). Keep them in sync if a screen is renamed.
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
    // S12.6a rename (G2R-F4): "Dynamic Scale" → "Circadian" (the Tasker name for the day/night curve).
    Circadian("circadian", "Circadian", R.string.title_circadian, "S12.6a"),
    // S12.5b re-adds the Misc/General screen (G2-F2): the Tasker "Misc" scene's brightness range
    // (min/max/offset/scale), animation (steps + min/max wait + throttle), notifications and debug
    // fields live here — they were wrongly scattered onto other screens in S12.
    Misc("misc", "Misc", R.string.title_misc, "S12.5b"),
    Tools("tools", "Tools", R.string.title_tools, "S12"),
    // S12.6b (G2R-F6): the AAB Debug scene rebuilt as a glass-box Live Debug Info destination — the
    // live %AAB_* runtime readout + the (now global) debug-category selector, reached from the Menu.
    LiveDebug("live_debug", "Live Debug Info", R.string.title_live_debug, "S12.6b"),
    // S12.9f (D-070): Profiles + Contexts folded into one destination — saved profiles + their
    // context rules (rule editing in a modal). Replaces the separate Profiles and Contexts screens.
    Profiles("profiles", "Profiles & Contexts", R.string.title_profiles_contexts, "S12.9f"),
    // S13d: the static reference screens (extraction/scenes/about.md + user_guide.md). The User Guide
    // is also the post-onboarding first-run destination (G2R-F80).
    UserGuide("user_guide", "User Guide", R.string.title_user_guide, "S13d"),
    About("about", "About", R.string.title_about, "S13d");

    companion object {
        /** Profiles & Contexts — the Menu's prominent hero card (one merged destination, S12.9f). */
        val heroDestinations: List<AppRoute> = listOf(Profiles)

        /** The tunable parameter screens — the Menu "Settings" group. */
        val settingsDestinations: List<AppRoute> = listOf(
            CurveBrightness, Reactivity, SuperDimming, Circadian, Misc,
        )

        /** Tools + Live Debug + reference content — the Menu "Info & Help" group. */
        val infoDestinations: List<AppRoute> = listOf(Tools, LiveDebug, UserGuide, About)

        /**
         * Every destination surfaced as a plain navigation ROW in the Menu (the hero cards render
         * separately). Drives the menu list + the navigation smoke tests.
         */
        val menuNavDestinations: List<AppRoute> =
            listOf(Dashboard) + settingsDestinations + infoDestinations
    }
}
