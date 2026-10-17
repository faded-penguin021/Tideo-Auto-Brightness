package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.R
import com.tideo.autobrightness.domain.brightness.DisplayCalibration
import com.tideo.autobrightness.platform.brightness.DisplayBrightnessReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Android's brightness while handed off, never in pipeline state; uncalibrated, the setting (≤ 255) (DD-077). */
class HighBrightnessUpdates(
    private val reader: DisplayBrightnessReader,
    private val calibration: suspend () -> DisplayCalibrationRecord?,
    private val settingBrightness: () -> Int?,
    private val sink: HandoffSink,
    private val periodMs: Long = PERIOD_MS,
) {
    private val _readout = MutableStateFlow<Int?>(null)
    val readout: StateFlow<Int?> = _readout.asStateFlow()

    private var hbm = false

    // Tasker: prof "High Brightness Updates" (AAB hand-off export, DD-077).
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope, state: Flow<PipelineState>): Job = scope.launch {
        state.map { (it.serviceOn && it.handedOff && !it.paused && !it.hibernated) to it.handedOff }
            .distinctUntilChanged()
            .collectLatest { (active, handedOff) ->
                // _HandBackToAAB A4 clears %AAB_HBMFlag; a screen-off does not.
                if (!handedOff) hbm = false
                _readout.value = null
                while (active) {
                    read()
                    delay(periodMs)
                }
            }
    }

    // Tasker: "_ReadBrightness" (AAB hand-off export, DD-077).
    internal suspend fun read() {
        val record = calibration()
        val level = if (record == null) {
            settingBrightness()
        } else {
            reader.read(record.field)?.let { record.calibration.levelFor(it.raw) }
        } ?: return
        if (level == _readout.value) return
        _readout.value = level
        val above = level > DisplayCalibration.HBM_ABOVE
        if (above != hbm) {
            hbm = above
            sink.onHighBrightness(above)
        }
    }

    companion object {
        const val PERIOD_MS = 2_000L
    }
}

class StoredDisplayCalibrationSink(
    private val write: suspend (DisplayCalibrationRecord?) -> Unit,
    private val flash: ToastHandoffSink,
) : DisplayCalibrationSink {
    override suspend fun started() {
        LiveRuntimeState.setCalibrating(true)
        flash.flash(R.string.flash_calibration_start)
    }

    override suspend fun finished(record: DisplayCalibrationRecord?) {
        try {
            write(record)
        } finally {
            LiveRuntimeState.setCalibrating(false)
            flash.flash(if (record != null) R.string.flash_calibration_ok else R.string.flash_calibration_failed)
        }
    }
}
