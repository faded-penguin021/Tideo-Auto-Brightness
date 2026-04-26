package com.tideo.autobrightness.app.state

data class SettingsState(
    val enabled: Boolean = true,
    val minBrightness: Float = 0.05f,
    val maxBrightness: Float = 1f,
    val dimming: SettingGroup = SettingGroup(),
    val reactivity: SettingGroup = SettingGroup(),
    val experiment: SettingGroup = SettingGroup(),
    val misc: SettingGroup = SettingGroup(),
)

data class SettingGroup(
    val sliderA: Float = 0.5f,
    val sliderB: Float = 0.5f,
    val toggleA: Boolean = false,
)

enum class GraphType {
    Brightness,
    Alpha,
    Dimming,
    Circadian,
    Taper,
    PowerDraw,
}

data class ServiceHealthUiState(
    val lastSensorTimestampMs: Long? = null,
    val lastApplyTimestampMs: Long? = null,
    val degradedMode: Boolean = false,
    val degradedReason: String? = null,
)

data class GraphState(
    val points: Map<GraphType, List<Float>> = GraphType.entries.associateWith {
        List(24) { i -> ((i % 10) + 1) / 10f }
    }
)
