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
    /** true when [paused] was latched by a DETECTED manual brightness override (prof755/task567), as
     *  opposed to nothing. Drives the redesigned dashboard's Resume-after-override card (G2R-F79). */
    val pausedByOverride: Boolean = false,
    val rawLux: Double? = null,
    val smoothedLux: Double? = null,
    val currentBrightness: Int? = null,
    val targetBrightness: Int? = null,
    /** %AAB_ScaleDynamicCompress — the effective circadian scale applied now (1.0 = no shift). */
    val circadianScale: Double? = null,
    /** %AAB_DimmingCurrent — relative super-dimming strength now (0 = not dimming). */
    val dimmingStrength: Double = 0.0,
    /** %AAB_Throttle — the reactivity cooldown (ms) currently in force. */
    val throttleMs: Long? = null,
    val activeContext: String? = null,
    /** %AAB_CurrentActiveProfile — the profile currently in force (manual or context load), or null. */
    val activeProfile: String? = null,
    /** TIMEMS of the last sensor sample the pipeline saw (live), for the "Xs ago" readout (G2R-F5). */
    val lastSampleMs: Long? = null,
    /** True when the published live snapshot has aged past STALE (>10 s) — the loop may be wedged.
     *  Drives the amber "live data may be stale" banner, shown only while [serviceRunning] (S12.9d). */
    val stale: Boolean = false,
    val health: ServiceHealthUiState = ServiceHealthUiState(),
)

data class ServiceHealthUiState(
    val lastSensorTimestampMs: Long? = null,
    val lastApplyTimestampMs: Long? = null,
    val degradedMode: Boolean = false,
    val degradedReason: String? = null,
)
