package com.tideo.autobrightness.app.navigation

sealed interface AppRoute {
    data object Dashboard : AppRoute
    data object BrightnessSettings : AppRoute
    data object Dimming : AppRoute
    data object Reactivity : AppRoute
    data object Experiment : AppRoute
    data object Misc : AppRoute
    data object GraphBrightness : AppRoute
    data object GraphAlpha : AppRoute
    data object GraphDimming : AppRoute
    data object GraphCircadian : AppRoute
    data object GraphTaper : AppRoute
    data object GraphPowerDraw : AppRoute
}
