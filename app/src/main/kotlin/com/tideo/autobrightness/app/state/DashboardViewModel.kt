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
import com.tideo.autobrightness.app.runtime.CircadianLocationStatus
import com.tideo.autobrightness.app.runtime.ServiceHealthStore
import com.tideo.autobrightness.app.runtime.Staleness
import com.tideo.autobrightness.app.settings.ExperimentPrefsStore
import com.tideo.autobrightness.app.settings.validate
import com.tideo.autobrightness.app.storage.experimentPrefsDataStore
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
 * Drives [DashboardScreen] from DataStore persisted state and [LiveRuntimeState] pipeline.
 * No local cache: notification actions and privilege grants propagate to UI (G1-F3 pattern).
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val privilegeManager: PrivilegeManager = AppModule(application).privilegeManager
    private val healthStore = ServiceHealthStore(application.serviceHealthDataStore)

    private val serviceEnabledFlow = app.settingsDataStore.data
        .map { it.validate().serviceEnabled }
        .distinctUntilChanged()

    // D-110: circadian location freshness for dashboard hint. Shown only when scaling is on.
    // Mirrors CircadianWindowProvider.current() fallback for UI consistency.
    private val experimentPrefs = ExperimentPrefsStore(application.experimentPrefsDataStore)
    private val circadianStatusFlow = combine(
        app.settingsDataStore.data.map { it.validate().scalingEnabled }.distinctUntilChanged(),
        experimentPrefs.dateLocation,
        experimentPrefs.cachedSunLocation,
    ) { scalingOn, ov, cache ->
        if (!scalingOn) return@combine null
        val today = System.currentTimeMillis() / 1000L / 86_400L
        when {
            ov.latitude != null && ov.longitude != null ->
                CircadianLocationStatus(ov.latitude, ov.longitude, resolvedForDay = today, today = today, fixed = true)
            cache != null ->
                CircadianLocationStatus(cache.latitude, cache.longitude, resolvedForDay = cache.day, today = today, fixed = false)
            else -> CircadianLocationStatus(today = today)
        }
    }

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
        circadianStatusFlow,
    ) { enabled, live, tier, health, circadian ->
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
            circadianLocation = circadian,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    /** Re-probe the privilege tier (call on resume — a grant may have happened in Settings/Shizuku). */
    fun refreshTier() = privilegeManager.refresh()

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            // Persist first; boot/screen receivers + maintenance read serviceEnabled.
            app.settingsDataStore.updateData { it.copy(serviceEnabled = enabled) }
            AutoBrightnessRuntime.onSettingChanged(app, enabled)
        }
    }

    // G2R-F79: only Resume remains (to clear pausedByOverride); Pause control removed.
    fun resume() = AutoBrightnessRuntime.resume(app)

    /**
     * Reset to automatic brightness and clear any manual-override pause.
     * Resume clears pause flags and runs Set Initial Brightness (G3-F11). No-op when service off.
     */
    fun resetToAuto() = AutoBrightnessRuntime.resume(app)

    /** Whether the "Add Quick Settings tile" prompt is available (StatusBarManager API, Android 13+). */
    fun canAddTile(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** Whether to offer "Add widget" (launcher supports pinning AND none placed yet). */
    fun canAddWidget(): Boolean = runCatching {
        AppWidgetManager.getInstance(app).isRequestPinAppWidgetSupported &&
            !DashboardWidgetProvider.hasInstances(app)
    }.getOrDefault(false)

    /**
     * Prompt OS to add QS tile (Android 13+). System de-dupes; [onResult] surfaces the status.
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

    fun addWidget() {
        runCatching {
            val mgr = AppWidgetManager.getInstance(app)
            if (mgr.isRequestPinAppWidgetSupported) {
                mgr.requestPinAppWidget(ComponentName(app, DashboardWidgetProvider::class.java), null, null)
            }
        }
    }

    companion object {
        const val RESULT_REQUEST_FAILED = -1
    }
}
