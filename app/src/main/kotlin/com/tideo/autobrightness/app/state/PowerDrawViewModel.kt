package com.tideo.autobrightness.app.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tideo.autobrightness.app.runtime.PowerDrawCalibrator
import com.tideo.autobrightness.app.runtime.PowerDrawProgress
import com.tideo.autobrightness.app.settings.PowerDrawStore
import com.tideo.autobrightness.app.storage.powerDrawDataStore
import com.tideo.autobrightness.domain.power.PowerDrawSample
import com.tideo.autobrightness.platform.context.AndroidPowerMeter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the Tools screen's power-draw calibration (task524 `_CalibratePowerDraw`). Exposes the persisted
 * dataset for the `PowerDrawChart` and runs the [PowerDrawCalibrator] sweep. The host supplies
 * [calibrate]'s `setScreenBrightness` by driving its Activity window (no WRITE_SETTINGS needed).
 */
class PowerDrawViewModel(app: Application) : AndroidViewModel(app) {
    private val store = PowerDrawStore(app.powerDrawDataStore)
    private val meter = AndroidPowerMeter(app)

    val samples: StateFlow<List<PowerDrawSample>> = store.samples
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val hasData: StateFlow<Boolean> = store.hasData
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()
    private val _progress = MutableStateFlow<PowerDrawProgress?>(null)
    val progress: StateFlow<PowerDrawProgress?> = _progress.asStateFlow()

    fun calibrate(
        setScreenBrightness: suspend (Int) -> Unit,
        isCancelled: () -> Boolean = { false },
        onResult: (PowerDrawCalibrator.Result) -> Unit = {},
    ) {
        if (_running.value) return
        _running.value = true
        viewModelScope.launch {
            try {
                val result = PowerDrawCalibrator(
                    meter = meter,
                    setScreenBrightness = setScreenBrightness,
                    onProgress = { _progress.value = it },
                ).calibrate(isCancelled = isCancelled)
                if (result is PowerDrawCalibrator.Result.Success) store.save(result.samples)
                onResult(result)
            } finally {
                _running.value = false
                _progress.value = null
            }
        }
    }

    fun clear() {
        viewModelScope.launch { store.clear() }
    }
}
