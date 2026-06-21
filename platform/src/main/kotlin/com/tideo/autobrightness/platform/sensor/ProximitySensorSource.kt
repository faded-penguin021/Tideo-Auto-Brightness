package com.tideo.autobrightness.platform.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Tasker prof759 "Proximity Detection" (State Proximity, code 125, arg0=1) → task545 "Detect Proximity".
 *
 * Emits `true` when the proximity sensor reads **near** (phone at the ear / covered) and `false` on
 * **far**. The runtime maps "near" to the task544 act28/29 smoothing-alpha damp
 * (`BrightnessEngine.PROXIMITY_ALPHA_DAMP` = ×0.1) so a hand/ear briefly over the light sensor does not
 * jerk the brightness. Like Tasker, it NEVER pauses the pipeline — it only softens reactivity.
 */
interface ProximitySensorSource {
    /** Emits near (true) / far (false). Unregisters on cancellation; completes with no sensor. */
    fun near(): Flow<Boolean>
}

class AndroidProximitySensorSource(private val context: Context) : ProximitySensorSource {
    override fun near(): Flow<Boolean> = callbackFlow {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (sensor == null) {
            // No proximity sensor on this device → never near (no damp); complete cleanly.
            close()
            return@callbackFlow
        }
        // Proximity sensors are effectively binary: ~0 cm = near, maximumRange = far. Anything below
        // the max counts as near (the conventional check).
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
