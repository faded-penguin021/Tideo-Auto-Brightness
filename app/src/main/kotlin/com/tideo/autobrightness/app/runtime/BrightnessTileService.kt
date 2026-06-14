package com.tideo.autobrightness.app.runtime

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.tideo.autobrightness.app.storage.settingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Quick Settings tile mirroring Tasker's _QSToggleAABService V2 (task551): tapping the tile toggles
 * the auto-brightness service on/off, persisting `serviceEnabled` and starting/stopping the
 * foreground service (S9a [AmbientMonitoringService]). The tile label/state reflects the persisted
 * enable flag.
 */
class BrightnessTileService : TileService() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            val enabled = applicationContext.settingsDataStore.data.first().serviceEnabled
            renderTile(enabled)
        }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            // task551 act1/act27: flip %AAB_Service, then start/stop the runtime accordingly.
            val newEnabled = applicationContext.settingsDataStore.updateData {
                it.copy(serviceEnabled = !it.serviceEnabled)
            }.serviceEnabled
            AutoBrightnessRuntime.onSettingChanged(applicationContext, newEnabled)
            renderTile(newEnabled)
        }
    }

    private fun renderTile(enabled: Boolean) {
        // Reflect the live paused/running state, not just the persisted enable flag (G2-F17). The
        // service republishes its pipeline snapshot into LiveRuntimeState (D-043b); read it here so
        // the tile shows "Paused" when a manual override is latched.
        val running = LiveRuntimeState.serviceRunning.value
        val paused = LiveRuntimeState.pipeline.value.paused
        qsTile?.apply {
            state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "Auto Brightness"
            subtitle = when {
                !enabled -> "Off"
                running && paused -> "Paused"
                running -> "Active"
                else -> "Starting…"
            }
            updateTile()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
