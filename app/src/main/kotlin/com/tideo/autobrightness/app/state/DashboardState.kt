package com.tideo.autobrightness.app.state

import com.tideo.autobrightness.platform.privilege.Tier

/**
 * Everything the Dashboard renders, composed from: the persisted settings (serviceEnabled — the
 * DataStore is the source of truth so the notification's Disable propagates, G1-F3), the live
 * pipeline snapshot (lux / brightness / paused / active context, published by the service via
 * LiveRuntimeState), the privilege tier (badge → onboarding), and the service-health telemetry.
 */
data class DashboardUiState(
    val serviceEnabled: Boolean = false,
    val tier: Tier = Tier.NONE,
    val serviceRunning: Boolean = false,
    val paused: Boolean = false,
    val rawLux: Double? = null,
    val smoothedLux: Double? = null,
    val currentBrightness: Int? = null,
    val targetBrightness: Int? = null,
    val activeContext: String? = null,
    val health: ServiceHealthUiState = ServiceHealthUiState(),
)

data class ServiceHealthUiState(
    val lastSensorTimestampMs: Long? = null,
    val lastApplyTimestampMs: Long? = null,
    val degradedMode: Boolean = false,
    val degradedReason: String? = null,
)
