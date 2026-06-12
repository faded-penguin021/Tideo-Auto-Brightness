package com.tideo.autobrightness.platform.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

// Tasker: prof760 "Monitor Ambient Light" → SensorManager.TYPE_LIGHT at SENSOR_DELAY_NORMAL
data class LightSample(
    val lux: Float,
    val accuracy: Int,
    val timestampNanos: Long,
)

interface LightSensorSource {
    /** Emits samples from TYPE_LIGHT at SENSOR_DELAY_NORMAL. Unregisters on cancellation. */
    fun samples(): Flow<LightSample>
}

class AndroidLightSensorSource(private val context: Context) : LightSensorSource {
    override fun samples(): Flow<LightSample> = callbackFlow {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        if (lightSensor == null) {
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(LightSample(event.values[0], event.accuracy, event.timestamp))
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }

        sensorManager.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
