package com.tideo.autobrightness.app.runtime

import android.util.Log
import com.tideo.autobrightness.platform.brightness.DisplayBrightnessRead

internal object DisplayCalibrationTrace {
    const val TAG = "TideoCalibration"

    fun logcat(line: String) {
        Log.i(TAG, line)
    }

    fun poll(read: DisplayBrightnessRead): String =
        if (read.attempts.isEmpty()) {
            read.sample?.let { "${it.field}=${it.raw}" } ?: "none"
        } else {
            read.attempts.joinToString(",") { a ->
                val sample = a.sample?.let { ":${it.field}=${it.raw}" }.orEmpty()
                "${a.source}:${a.status.name.lowercase()}$sample:${a.elapsedMs}ms"
            }
        }
}
