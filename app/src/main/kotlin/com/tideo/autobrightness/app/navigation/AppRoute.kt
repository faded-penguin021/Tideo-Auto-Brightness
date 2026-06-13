package com.tideo.autobrightness.app.navigation

/**
 * The target Material 3 screen set from `docs/rebuild/screen_map.md`. S11 builds Dashboard and
 * Onboarding for real; the parameter/tool/profile screens are owned by S12 and the About/Guide
 * content by S13, so they resolve to a labelled placeholder until then ([owner] tags which segment
 * fills each in). Keeping the full route table here means navigation resolves end-to-end now.
 */
enum class AppRoute(val route: String, val label: String, val owner: String) {
    Dashboard("dashboard", "Dashboard", "S11"),
    Onboarding("onboarding", "Setup & Permissions", "S11"),
    CurveBrightness("curve_brightness", "Curve & Brightness", "S12"),
    Reactivity("reactivity", "Reactivity", "S12"),
    AnimationDimming("animation_dimming", "Animation & Dimming", "S12"),
    DynamicScale("dynamic_scale", "Dynamic Scale", "S12"),
    Contexts("contexts", "Contexts", "S12"),
    Tools("tools", "Tools", "S12"),
    Profiles("profiles", "Profiles & Import/Export", "S12"),
    About("about", "About & Guide", "S13");

    companion object {
        /** Destinations surfaced as navigation entries on the Dashboard (everything the user tunes). */
        val dashboardDestinations: List<AppRoute> = listOf(
            CurveBrightness, Reactivity, AnimationDimming, DynamicScale,
            Contexts, Tools, Profiles, About,
        )
    }
}
