package com.tideo.autobrightness.platform.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.sqrt

/**
 * Tasker prof769 "Panic (Reset)" gesture detector (D-021 ledger: "upside-down + shake").
 *
 * prof769's `<ConditionList>` is `Event 2083 (shake/significant-motion)` ∧ `State 120/3
 * (orientation = upside down)` ∧ `State 123/1 (display on)` → task528 `_PanicButton`. The orientation
 * + shake are both derivable from the accelerometer:
 *  - **Gravity** (a low-pass of the raw accelerometer) gives the device orientation. Held upright in
 *    portrait the gravity vector points toward the bottom of the screen (`y ≈ −9.8`); held **upside
 *    down** it points toward the top (`y ≈ +9.8`). So upside-down ≡ `gravityY > [upsideDownGravityY]`.
 *  - **Shake** is the residual linear acceleration magnitude (raw − gravity); a spike past
 *    [shakeThreshold] m/s² is a shake (the Tasker "shake" event).
 *
 * Pure + frame-by-frame so the timing is unit-testable without a [SensorManager]; the Android source
 * feeds it raw accelerometer triples and gates the result on the display being on (State 123/1).
 */
class PanicGestureDetector(
    private val upsideDownGravityY: Float = 7.0f,
    private val shakeThreshold: Float = 12.0f,
    // Low-pass coefficient isolating gravity from the raw accelerometer (standard Android idiom).
    private val gravityAlpha: Float = 0.8f,
) {
    private val gravity = FloatArray(3)
    private var seeded = false

    /**
     * Feed one raw accelerometer reading (m/s², device frame). Returns true on the reading that
     * completes the panic gesture: the device is upside down AND a shake spike is present. Gravity is
     * tracked across calls via the low-pass filter, so a few readings are needed before it is stable.
     */
    fun onAccelerometer(x: Float, y: Float, z: Float): Boolean {
        if (!seeded) {
            gravity[0] = x; gravity[1] = y; gravity[2] = z
            seeded = true
            return false
        }
        gravity[0] = gravityAlpha * gravity[0] + (1 - gravityAlpha) * x
        gravity[1] = gravityAlpha * gravity[1] + (1 - gravityAlpha) * y
        gravity[2] = gravityAlpha * gravity[2] + (1 - gravityAlpha) * z

        val upsideDown = gravity[1] > upsideDownGravityY
        val lx = x - gravity[0]
        val ly = y - gravity[1]
        val lz = z - gravity[2]
        val shakeMag = sqrt(lx * lx + ly * ly + lz * lz)
        return upsideDown && shakeMag > shakeThreshold
    }

    fun reset() {
        seeded = false
        gravity.fill(0f)
    }
}

/**
 * Emits a [Unit] each time the prof769 panic gesture (upside-down + shake, display on) is detected.
 * The runtime maps each emission to the task528 panic (SOS + brightness 255 + full stop).
 */
interface PanicSensorSource {
    fun events(): Flow<Unit>
}

/**
 * Accelerometer-backed [PanicSensorSource] for prof769. Registers TYPE_ACCELEROMETER at the UI delay
 * (fast enough to catch a shake), runs the [PanicGestureDetector], and gates each detection on the
 * display being interactive (State 123/1) plus a short cooldown so one shake fires the panic once.
 */
class AndroidPanicSensorSource(
    private val context: Context,
    private val detector: PanicGestureDetector = PanicGestureDetector(),
    private val cooldownMs: Long = 3_000L,
    private val clock: () -> Long = System::currentTimeMillis,
) : PanicSensorSource {
    override fun events(): Flow<Unit> = callbackFlow {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (accel == null) {
            close()
            return@callbackFlow
        }
        var lastFiredMs = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val fired = detector.onAccelerometer(event.values[0], event.values[1], event.values[2])
                if (!fired) return
                // State 123/1: only while the display is on (a face-down phone in a pocket must not fire).
                if (!power.isInteractive) return
                val now = clock()
                if (now - lastFiredMs < cooldownMs) return
                lastFiredMs = now
                trySend(Unit)
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
