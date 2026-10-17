package com.tideo.autobrightness.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
        data object Failed : Status
        /** Calibrated on another build, as AAB's fingerprint check finds after an OS update. */
        data object Needed : Status
        data class Calibrated(val record: DisplayCalibrationRecord) : Status
    }

    val status: Flow<Status> = dataStore.data.map { prefs ->
        val encoded = prefs[CALIBRATION] ?: return@map Status.Uncalibrated
        if (encoded == FAILED) return@map Status.Failed
        if (prefs[BUILD] != build) return@map Status.Needed
        val calibration = DisplayCalibration.decode(encoded)
        val field = prefs[FIELD]
        if (calibration == null || field == null) Status.Uncalibrated else Status.Calibrated(DisplayCalibrationRecord(calibration, field))
    }

    suspend fun current(): DisplayCalibrationRecord? = (status.first() as? Status.Calibrated)?.record

    suspend fun write(record: DisplayCalibrationRecord?) {
        dataStore.edit {
            if (record == null) {
                it[CALIBRATION] = FAILED
                it.remove(FIELD)
            } else {
                it[CALIBRATION] = record.calibration.encode()
                it[FIELD] = record.field
                it[BUILD] = build
            }
        }
    }

    private companion object {
        const val FAILED = "error"
        val CALIBRATION = stringPreferencesKey("display_calibration")
        val FIELD = stringPreferencesKey("display_calibration_field")
        val BUILD = stringPreferencesKey("display_calibration_build")
    }
}
