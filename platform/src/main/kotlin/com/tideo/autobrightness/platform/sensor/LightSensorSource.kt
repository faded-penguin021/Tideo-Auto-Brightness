package com.tideo.autobrightness.platform.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

// Tasker: prof760 "Monitor Ambient Light" → SensorManager.TYPE_LIGHT at SENSOR_DELAY_NORMAL
data class LightSample(
    val lux: Float,
    val accuracy: Int,
    val timestampNanos: Long,
    val seq: Int = 0,
    val callbackElapsedNanos: Long = 0L,
)

interface LightSensorSource {
    /** TYPE_LIGHT at SENSOR_DELAY_NORMAL; [onCallback] runs in the listener, before the flow sees it. */
    fun samples(onRegistered: (Boolean) -> Unit = {}, onCallback: (LightSample) -> Unit = {}): Flow<LightSample>
}

class AndroidLightSensorSource(private val context: Context) : LightSensorSource {
    override fun samples(
        onRegistered: (Boolean) -> Unit,
        onCallback: (LightSample) -> Unit,
    ): Flow<LightSample> = callbackFlow {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        if (lightSensor == null) {
            onRegistered(false)
            close()
            return@callbackFlow
        }

        var seq = 0
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val sample = LightSample(
                    event.values[0], event.accuracy, event.timestamp, ++seq, SystemClock.elapsedRealtimeNanos(),
                )
                onCallback(sample)
                trySend(sample)
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }

        onRegistered(sensorManager.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL))
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
