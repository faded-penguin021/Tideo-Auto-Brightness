package com.tideo.autobrightness.app.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tideo.autobrightness.app.AppModule
import com.tideo.autobrightness.app.runtime.AutoBrightnessRuntime
import com.tideo.autobrightness.app.runtime.LiveRuntimeState
import com.tideo.autobrightness.app.runtime.ServiceHealthStore
import com.tideo.autobrightness.app.settings.validate
import com.tideo.autobrightness.app.storage.serviceHealthDataStore
import com.tideo.autobrightness.app.storage.settingsDataStore
import com.tideo.autobrightness.platform.privilege.PrivilegeManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives [DashboardScreen]. Source of truth is the DataStore for persisted state and
 * [LiveRuntimeState] for the running pipeline; nothing is cached locally, so the notification's
 * Disable/Pause actions and a post-launch privilege grant all propagate to the UI (G1-F3 pattern,
 * carried forward per the S9b hand-off note).
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val privilegeManager: PrivilegeManager = AppModule(application).privilegeManager
    private val healthStore = ServiceHealthStore(application.serviceHealthDataStore)

    private val serviceEnabledFlow = app.settingsDataStore.data
        .map { it.validate().serviceEnabled }
        .distinctUntilChanged()

    private data class Live(
        val running: Boolean,
        val paused: Boolean,
        val pausedByOverride: Boolean,
        val rawLux: Double?,
        val smoothedLux: Double?,
        val current: Int?,
        val target: Int?,
        val circadianScale: Double?,
        val dimmingStrength: Double,
        val throttleMs: Long?,
        val context: String?,
        val lastSampleMs: Long?,
    )

    private val liveFlow = combine(
        LiveRuntimeState.pipeline,
        LiveRuntimeState.activeContext,
        LiveRuntimeState.serviceRunning,
    ) { p, ctx, running ->
        Live(
            running, p.paused, p.pausedByOverride, p.lastRawLux, p.smoothedLux,
            p.lastAppliedBrightness, p.targetBrightness, p.scaleDynamicCompress, p.dimmingCurrent,
            p.throttleMs, ctx, p.lastSampleMs,
        )
    }

    private val healthFlow = healthStore.telemetry.map {
        ServiceHealthUiState(
            lastSensorTimestampMs = it.lastSensorTimestampMs,
            lastApplyTimestampMs = it.lastApplyTimestampMs,
            degradedMode = it.degradedMode,
            degradedReason = it.degradedReason,
        )
    }

    val state: StateFlow<DashboardUiState> = combine(
        serviceEnabledFlow,
        liveFlow,
        privilegeManager.tierFlow(),
        healthFlow,
    ) { enabled, live, tier, health ->
        DashboardUiState(
            serviceEnabled = enabled,
            tier = tier,
            serviceRunning = live.running,
            paused = live.paused,
            pausedByOverride = live.pausedByOverride,
            rawLux = live.rawLux,
            smoothedLux = live.smoothedLux,
            currentBrightness = live.current,
            targetBrightness = live.target,
            circadianScale = live.circadianScale,
            dimmingStrength = live.dimmingStrength,
            throttleMs = live.throttleMs,
            activeContext = live.context,
            lastSampleMs = live.lastSampleMs,
            health = health,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    /** Re-probe the privilege tier (call on resume — a grant may have happened in Settings/Shizuku). */
    fun refreshTier() = privilegeManager.refresh()

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            // Persist first (boot/screen receivers + maintenance read serviceEnabled), then start/stop.
            app.settingsDataStore.updateData { it.copy(serviceEnabled = enabled) }
            AutoBrightnessRuntime.onSettingChanged(app, enabled)
        }
    }

    // G2R-F79: the Dashboard no longer exposes a Pause control (stopping = disable the service). Only
    // Resume remains, to clear a DETECTED manual override (pausedByOverride). The runtime Pause/Resume
    // events still exist for the notification/override path; the dashboard just doesn't offer Pause.
    fun resume() = AutoBrightnessRuntime.resume(app)
}
