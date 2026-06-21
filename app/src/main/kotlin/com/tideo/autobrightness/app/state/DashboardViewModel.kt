package com.tideo.autobrightness.app.state

import android.app.Application
import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.AppModule
import com.tideo.autobrightness.app.runtime.AutoBrightnessRuntime
import com.tideo.autobrightness.app.runtime.BrightnessTileService
import com.tideo.autobrightness.app.runtime.LiveRuntimeState
import com.tideo.autobrightness.app.widget.DashboardWidgetProvider
import com.tideo.autobrightness.app.runtime.ServiceHealthStore
import com.tideo.autobrightness.app.runtime.Staleness
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
        val profile: String?,
        val lastSampleMs: Long?,
        val stale: Boolean,
    )

    private val liveFlow = combine(
        LiveRuntimeState.pipeline,
        LiveRuntimeState.activeContext,
        LiveRuntimeState.serviceRunning,
        LiveRuntimeState.staleness(),
        LiveRuntimeState.activeProfile,
    ) { p, ctx, running, staleness, profile ->
        Live(
            running, p.paused, p.pausedByOverride, p.lastRawLux, p.smoothedLux,
            p.lastAppliedBrightness, p.targetBrightness, p.scaleDynamicCompress, p.dimmingCurrent,
            p.throttleMs, ctx, profile, p.lastSampleMs, staleness == Staleness.STALE,
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
            activeProfile = live.profile,
            lastSampleMs = live.lastSampleMs,
            stale = live.stale,
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

    /** Owner: Reset = re-apply / snap to auto — force the pipeline to recompute now (clears a manual
     *  override pause). No-op when the service is not running; the next start applies the settings. */
    fun resetToAuto() = AutoBrightnessRuntime.reapply(app)

    /** Whether the "Add Quick Settings tile" prompt is available (StatusBarManager API, Android 13+). */
    fun canAddTile(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** Whether to offer "Add widget": the launcher supports pinning AND none is placed yet (owner: only
     *  offer it when one isn't already present). */
    fun canAddWidget(): Boolean = runCatching {
        AppWidgetManager.getInstance(app).isRequestPinAppWidgetSupported &&
            !DashboardWidgetProvider.hasInstances(app)
    }.getOrDefault(false)

    /**
     * Prompt the OS to add the QS tile (Android 13+). The system de-dupes: if the tile is already
     * present it returns TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED — surfaced via [onResult] so the
     * screen can toast "already added" rather than failing silently (owner: only when not present).
     */
    fun addTile(onResult: (Int) -> Unit) {
        if (!canAddTile()) return
        val sbm = app.getSystemService(StatusBarManager::class.java) ?: return
        runCatching {
            sbm.requestAddTileService(
                ComponentName(app, BrightnessTileService::class.java),
                app.getString(R.string.widget_title),
                Icon.createWithResource(app, R.drawable.ic_stat_brightness),
                app.mainExecutor,
                { result -> onResult(result) },
            )
        }.onFailure { onResult(RESULT_REQUEST_FAILED) }
    }

    /** Prompt the launcher to pin the home-screen widget (no-op if unsupported). */
    fun addWidget() {
        runCatching {
            val mgr = AppWidgetManager.getInstance(app)
            if (mgr.isRequestPinAppWidgetSupported) {
                mgr.requestPinAppWidget(ComponentName(app, DashboardWidgetProvider::class.java), null, null)
            }
        }
    }

    companion object {
        /** Sentinel result for [addTile] when the request could not be dispatched at all. */
        const val RESULT_REQUEST_FAILED = -1
    }
}
