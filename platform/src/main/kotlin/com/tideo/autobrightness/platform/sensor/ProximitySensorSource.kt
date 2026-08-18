package com.tideo.autobrightness.platform.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Tasker prof759/task545: proximity detection. Emits near (true) / far (false). */
interface ProximitySensorSource {
    fun near(): Flow<Boolean>
}

class AndroidProximitySensorSource(private val context: Context) : ProximitySensorSource {
    override fun near(): Flow<Boolean> = callbackFlow {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (sensor == null) {
            close()
            return@callbackFlow
        }
        // Proximity binary: ~0 cm = near, maximumRange = far.
        val maxRange = sensor.maximumRange
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(event.values[0] < maxRange)
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
