package com.tideo.autobrightness.app.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tideo.autobrightness.app.AppModule
import com.tideo.autobrightness.app.runtime.AutoBrightnessRuntime
import com.tideo.autobrightness.app.runtime.LiveRuntimeState
import com.tideo.autobrightness.app.settings.DisplayCalibrationStore.Status
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** The scene's calibration badge states (`getCalibrationState`, DD-077). */
enum class CalibrationBadge { RUNNING, CALIBRATED, FAILED, NEEDED, UNCALIBRATED }

/** [badge] is null until the store has been read ("Checking…"). */
data class DisplayCalibrationUi(val badge: CalibrationBadge?, val serviceRunning: Boolean)

class DisplayCalibrationViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AppModule(application).displayCalibrationStore

    val state: StateFlow<DisplayCalibrationUi> = combine(
        store.status,
        LiveRuntimeState.calibrating,
        LiveRuntimeState.serviceRunning,
    ) { status, calibrating, running ->
        val badge = when {
            calibrating -> CalibrationBadge.RUNNING
            status is Status.Calibrated -> CalibrationBadge.CALIBRATED
            status == Status.Failed -> CalibrationBadge.FAILED
            status == Status.Needed -> CalibrationBadge.NEEDED
            else -> CalibrationBadge.UNCALIBRATED
        }
        DisplayCalibrationUi(badge, running)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DisplayCalibrationUi(null, false))

    fun calibrate() = AutoBrightnessRuntime.calibrateDisplay(getApplication())
}
