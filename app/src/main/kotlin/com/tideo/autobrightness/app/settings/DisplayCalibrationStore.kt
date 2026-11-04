package com.tideo.autobrightness.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tideo.autobrightness.app.runtime.CalibrationFailure
import com.tideo.autobrightness.app.runtime.DisplayCalibrationOutcome
import com.tideo.autobrightness.app.runtime.DisplayCalibrationRecord
import com.tideo.autobrightness.domain.brightness.DisplayCalibration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** `%AAB_BrightCal` / `%AAB_BrightCalFP` / `%AAB_NeedsCal`: a device fact, never part of a profile (DD-077). */
class DisplayCalibrationStore(
    private val dataStore: DataStore<Preferences>,
    private val build: String,
) {
    sealed interface Status {
        data object Uncalibrated : Status
        data class Failed(val cause: CalibrationFailure) : Status
        /** Calibrated on another build, as AAB's fingerprint check finds after an OS update. */
        data object Needed : Status
        data class Calibrated(val record: DisplayCalibrationRecord) : Status
    }

    val status: Flow<Status> = dataStore.data.map { prefs ->
        val encoded = prefs[CALIBRATION] ?: return@map Status.Uncalibrated
        if (encoded == FAILED) {
            return@map Status.Failed(CalibrationFailure.entries.firstOrNull { it.name == prefs[ERROR] } ?: CalibrationFailure.NO_READING)
        }
        if (prefs[BUILD] != build) return@map Status.Needed
        val calibration = DisplayCalibration.decode(encoded)
        val field = prefs[FIELD]
        if (calibration == null || field == null) Status.Uncalibrated else Status.Calibrated(DisplayCalibrationRecord(calibration, field, prefs[HBM]))
    }

    suspend fun current(): DisplayCalibrationRecord? = (status.first() as? Status.Calibrated)?.record

    suspend fun write(outcome: DisplayCalibrationOutcome) {
        dataStore.edit {
            when (outcome) {
                is DisplayCalibrationOutcome.Failed -> {
                    it[CALIBRATION] = FAILED
                    it[ERROR] = outcome.cause.name
                    it.remove(FIELD)
                    it.remove(HBM)
                }
                is DisplayCalibrationOutcome.Calibrated -> {
                    val record = outcome.record
                    it[CALIBRATION] = record.calibration.encode()
                    it[FIELD] = record.field
                    it[BUILD] = build
                    it.remove(ERROR)
                    val boundary = record.hbmBoundary
                    if (boundary != null) it[HBM] = boundary else it.remove(HBM)
                }
            }
        }
    }

    private companion object {
        const val FAILED = "error"
        val CALIBRATION = stringPreferencesKey("display_calibration")
        val ERROR = stringPreferencesKey("display_calibration_error")
        val FIELD = stringPreferencesKey("display_calibration_field")
        val HBM = doublePreferencesKey("display_calibration_hbm")
        val BUILD = stringPreferencesKey("display_calibration_build")
    }
}
