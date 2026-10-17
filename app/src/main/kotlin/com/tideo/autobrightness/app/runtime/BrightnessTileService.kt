package com.tideo.autobrightness.app.runtime

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.tideo.autobrightness.app.storage.settingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Quick Settings tile mirroring Tasker's _QSToggleAABService V2 (task551): tapping the tile toggles
 * the auto-brightness service on/off, persisting `serviceEnabled` and starting/stopping the
 * foreground service (S9a [AmbientMonitoringService]). The tile label/state reflects the persisted
 * enable flag and the live pipeline state.
 */
class BrightnessTileService : TileService() {
    // S12.9e scope audit: legitimately-owned, NOT a leak — this tile owns its lifecycle and cancels
    // the scope in onDestroy(). It is therefore kept as a dedicated SupervisorJob scope rather than
    // routed through AppProcessScope (which is for owner-less process-scoped work).
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Active only between onStartListening/onStopListening: pushes live state changes to the tile so
    // Off→Starting→Active/Paused renders while the panel is open (G2R-F63). The service also pings us
    // via TileService.requestListeningState so the OS re-runs onStartListening on each state change.
    private var listeningJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listeningJob?.cancel()
        listeningJob = scope.launch {
            val enabledFlow = applicationContext.settingsDataStore.data
                .map { it.serviceEnabled }
                .distinctUntilChanged()
            combine(
                enabledFlow,
                LiveRuntimeState.serviceRunning,
                LiveRuntimeState.pipeline.map { it.paused to it.handedOff }.distinctUntilChanged(),
            ) { enabled, running, (paused, handedOff) -> TileState(enabled, running, paused, handedOff) }
                .distinctUntilChanged()
                .collect(::renderTile)
        }
    }

    override fun onStopListening() {
        listeningJob?.cancel()
        listeningJob = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            // task551 act1/act27: flip %AAB_Service, then start/stop the runtime accordingly.
            val newEnabled = applicationContext.settingsDataStore.updateData {
                it.copy(serviceEnabled = !it.serviceEnabled)
            }.serviceEnabled
            AutoBrightnessRuntime.onSettingChanged(applicationContext, newEnabled)
            val p = LiveRuntimeState.pipeline.value
            renderTile(TileState(newEnabled, LiveRuntimeState.serviceRunning.value, p.paused, p.handedOff))
        }
    }

    private data class TileState(val enabled: Boolean, val running: Boolean, val paused: Boolean, val handedOff: Boolean)

    private fun renderTile(s: TileState) {
        qsTile?.apply {
            state = if (s.enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = TILE_LABEL
            subtitle = tileSubtitle(s.enabled, s.running, s.paused, s.handedOff)
            updateTile()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val TILE_LABEL = "Auto Brightness"

        /**
         * Pure live-state → subtitle mapping (G2-F17/G2R-F63, DD-075). Extracted so it is
         * unit-testable without binding a real QS tile (Robolectric cannot).
         */
        fun tileSubtitle(enabled: Boolean, running: Boolean, paused: Boolean, handedOff: Boolean = false): String = when {
            !enabled -> "Off"
            running && paused -> "Paused"
            running && handedOff -> "Android"
            running -> "Active"
            else -> "Starting…"
        }
    }
}
