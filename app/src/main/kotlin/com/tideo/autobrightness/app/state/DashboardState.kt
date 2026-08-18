package com.tideo.autobrightness.app.state

import com.tideo.autobrightness.app.runtime.CircadianLocationStatus
import com.tideo.autobrightness.platform.privilege.Tier

// Everything the Dashboard renders: settings, pipeline snapshot, privilege tier, health telemetry.
data class DashboardUiState(
    val serviceEnabled: Boolean = false,
    val tier: Tier = Tier.NONE,
    val serviceRunning: Boolean = false,
    val paused: Boolean = false,
    // prof755/task567: Manual override detected; drives Resume card (G2R-F79).
    val pausedByOverride: Boolean = false,
    val rawLux: Double? = null,
    val smoothedLux: Double? = null,
    val currentBrightness: Int? = null,
    val targetBrightness: Int? = null,
    val circadianScale: Double? = null,
    val dimmingStrength: Double = 0.0,
    val throttleMs: Long? = null,
    val activeContext: String? = null,
    val activeProfile: String? = null,
    val lastSampleMs: Long? = null,
    // Stale when >10s since last sample; shows "live data may be stale" banner (S12.9d).
    val stale: Boolean = false,
    val health: ServiceHealthUiState = ServiceHealthUiState(),
    // D-110: location freshness for circadian modifier; shows staleness hint if needed.
    val circadianLocation: CircadianLocationStatus? = null,
)

data class ServiceHealthUiState(
    val lastSensorTimestampMs: Long? = null,
    val lastApplyTimestampMs: Long? = null,
    val degradedMode: Boolean = false,
    val degradedReason: String? = null,
)
