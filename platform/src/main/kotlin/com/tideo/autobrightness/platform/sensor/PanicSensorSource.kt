package com.tideo.autobrightness.platform.sensor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.PowerManager
import com.tideo.autobrightness.domain.panic.PanicShakeGate
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt

/** Orientation detector for prof769 panic gesture (D-021, D-116): computes sustained upside-down state
 * and gravity-stripped acceleration magnitude for shake detection. Pure state machine. */
class PanicGestureDetector(
    // S14 (owner: panic too sensitive): require a committed inversion. gy < −8.0 ≈ within ~35° of fully
    // upside down (was −7.0 ≈ 44°), so a phone held at a casual downward angle does not count.
    private val upsideDownGravityY: Float = 8.0f,
    // Heavy low-pass so a vigorous shake does NOT drag the gravity estimate across the upside-down
    // threshold (G2R-F77: orientation must read inverted ONLY when genuinely inverted).
    private val gravityAlpha: Float = 0.9f,
    // The inversion must be held this many readings before the gesture arms — a transient flip cannot.
    private val sustainedFrames: Int = 5,
) {
    private val gravity = FloatArray(3)
    private var seeded = false
    private var upsideDownStreak = 0

    /** Instantaneous (filtered) orientation for this reading: true iff the device is upside down now. */
    var isUpsideDown: Boolean = false
        private set

    /** Gravity-stripped acceleration magnitude (m/s²) of the last reading — the shake source for the
     *  accelerometer fallback path (no `TYPE_LINEAR_ACCELERATION`). */
    var linearMagnitude: Double = 0.0
        private set

    /**
     * Feed one raw accelerometer reading (m/s², device frame). Returns true once the device has been
     * **stably upside down** for [sustainedFrames] readings. Also updates [isUpsideDown] and
     * [linearMagnitude]. The very first reading only seeds the gravity filter (returns false).
     */
    fun onAccelerometer(x: Float, y: Float, z: Float): Boolean {
        if (!seeded) {
            gravity[0] = x; gravity[1] = y; gravity[2] = z
            seeded = true
            isUpsideDown = false
            linearMagnitude = 0.0
            return false
        }
        gravity[0] = gravityAlpha * gravity[0] + (1 - gravityAlpha) * x
        gravity[1] = gravityAlpha * gravity[1] + (1 - gravityAlpha) * y
        gravity[2] = gravityAlpha * gravity[2] + (1 - gravityAlpha) * z

        val gy = gravity[1]
        isUpsideDown = gy < -upsideDownGravityY &&
            abs(gy) >= abs(gravity[0]) &&
            abs(gy) >= abs(gravity[2])
        upsideDownStreak = if (isUpsideDown) upsideDownStreak + 1 else 0

        val lx = x - gravity[0]
        val ly = y - gravity[1]
        val lz = z - gravity[2]
        linearMagnitude = sqrt((lx * lx + ly * ly + lz * lz).toDouble())
        return upsideDownStreak >= sustainedFrames
    }

    fun reset() {
        seeded = false
        upsideDownStreak = 0
        gravity.fill(0f)
        isUpsideDown = false
        linearMagnitude = 0.0
    }
}

/** Re-arm latch for panic gesture window (task528 `_PanicButton`, D-021, D-116, D-165).
 * Clears only after [rearmFrames] consecutive non-inverted readings to avoid shake-induced flicker. */
class PanicGate(
    // 25 readings ≈ 0.5 s at SENSOR_DELAY_GAME (~50 Hz): unreachable by shake/filter artifacts
    // (single-spike recovery is ~5-6 readings; oscillating shakes keep interrupting the streak),
    // trivially reached by a genuine flip straight (a real re-entry takes ≥1 s of handling).
    private val rearmFrames: Int = REARM_FRAMES,
) {
    // True once a window has run for the current inversion; cleared after a SUSTAINED straight spell.
    private var consumed = false
    private var straightStreak = 0

    /**
     * Whether a fresh shake window may start now. [armed] = sustained-upside-down ∧ display-on ∧
     * proximity-not-near. [upsideDown] is the instantaneous orientation: [rearmFrames] consecutive
     * non-inverted readings clear the latch so the next inversion can re-arm (D-165).
     */
    fun canArm(armed: Boolean, upsideDown: Boolean): Boolean {
        if (upsideDown) {
            straightStreak = 0
        } else if (consumed && ++straightStreak >= rearmFrames) {
            consumed = false
        }
        return armed && !consumed
    }

    /** Record that a shake window ran (fired OR timed out). Latches until a sustained straight spell. */
    fun consume() {
        consumed = true
        straightStreak = 0
    }

    companion object {
        /** Default re-arm spell length, in consecutive non-inverted readings (D-165). */
        const val REARM_FRAMES = 25
    }
}

/**
 * Emits a [Unit] each time the prof769 panic gesture completes: the device is held upside-down with the
 * display on and the proximity sensor NOT near, and a qualifying shake (per [PanicShakeGate], scaled by
 * the user's `%AAB_PanicSensitivity`) occurs within 10 s. The runtime maps each emission to the task528
 * panic (SOS + brightness 255 + full stop).
 */
interface PanicSensorSource {
    fun events(): Flow<Unit>
}

/** Accelerometer-backed source for prof769 panic gesture (D-116, DB-009, DB-011).
 * Opens 10 s shake window when armed; closes and consumes on fire or timeout. Single-threaded via looper. */
class AndroidPanicSensorSource(
    private val context: Context,
    /** Current `%AAB_PanicSensitivity` (0..10). Read per arming so a slider change takes effect at once. */
    private val sensitivity: () -> Int,
    /** Current `%AAB_Proximity ~ Near` — the gesture only arms while NOT near (covered/in-pocket = no panic). */
    private val isNear: () -> Boolean,
    /** Requires plugged state (DB-009, DB-011, issue #110). `null` = unknown, handled fail-closed. */
    private val requiresPlugged: () -> Boolean? = { false },
    private val detector: PanicGestureDetector = PanicGestureDetector(),
    private val gate: PanicGate = PanicGate(),
    private val windowMs: Long = 10_000L,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newShakeGate: (Int) -> PanicShakeGate = { PanicShakeGate(it) },
) : PanicSensorSource {
    override fun events(): Flow<Unit> = callbackFlow {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val linear = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) // may be null
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (accel == null) {
            // No accelerometer → cannot detect orientation; complete cleanly (no panic source).
            close()
            return@callbackFlow
        }

        // Screen-interactive state, kept current WITHOUT a per-event Binder call (reading
        // power.isInteractive on every sample would be a synchronous IPC to system_server). Seed once,
        // then flip it on the cheap SCREEN_ON/OFF protected broadcasts.
        val interactive = AtomicBoolean(power.isInteractive)

        // DB-009: plugged state, seeded from sticky battery broadcast, maintained on power-transition
        // broadcasts. ACTION_BATTERY_CHANGED not registered (fires on every level tick; gate removes that cost).
        val plugged = AtomicBoolean(
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                ?.let { it > 0 } ?: false,
        )

        // DB-011: resolve plugged requirement once for both registration and arming gates. Unknown=fail-closed.
        fun pluggedRequirementMet(): Boolean = when (requiresPlugged()) {
            null -> false
            false -> true
            true -> plugged.get()
        }

        // Window state (mutated single-threaded from sensor callbacks on one looper).
        var windowActive = false
        var windowDeadline = 0L
        var shakeGate: PanicShakeGate? = null
        var shakeMagnitude = 0.0  // From linear-accel sensor, else detector's gravity-stripped residual.

        // Clear window without consuming gesture (DB-009): releasing must not latch the re-entry gate.
        fun resetWindow() {
            windowActive = false
            shakeGate = null
        }

        // Both outcomes (fire or timeout) consume gesture; re-arm requires flip-straight-and-back (D-021, D-165).
        fun endWindow() {
            resetWindow()
            gate.consume()
        }

        val accelListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val now = clock()
                val sustainedUpsideDown = detector.onAccelerometer(event.values[0], event.values[1], event.values[2])
                if (linear == null) shakeMagnitude = detector.linearMagnitude
                // DB-011: check plugged requirement at ARM time, not just registration (battery gate re-evals on broadcasts).
                val armed = sustainedUpsideDown && interactive.get() && !isNear() && pluggedRequirementMet()

                if (windowActive) {
                    // A2 Java: window runs to completion, not re-gated on orientation (prevents shake-direction bias).
                    when {
                        // 10 s elapsed with no qualifying shake → veto (consume; needs a re-entry to re-arm).
                        now >= windowDeadline -> endWindow()
                        // Feed the shake; a completed gate fires the panic and consumes the gesture.
                        shakeGate?.onSample(shakeMagnitude) == true -> {
                            trySend(Unit)
                            endWindow()
                        }
                    }
                } else if (gate.canArm(armed, detector.isUpsideDown)) {
                    val g = newShakeGate(sensitivity())
                    if (g.isPassThrough) {
                        // Sensitivity 0: no shake required — fire at once, then require a re-entry.
                        trySend(Unit)
                        gate.consume()
                    } else {
                        shakeGate = g
                        windowActive = true
                        windowDeadline = now + windowMs
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }

        // Linear-accel listener (only if the device has the sensor): supplies the gravity-free shake
        // magnitude directly. Delivered on the same looper as the accel listener → no races on shakeMagnitude.
        val linearListener = linear?.let {
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
                    shakeMagnitude = sqrt((x * x + y * y + z * z).toDouble())
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
            }
        }

        // DB-009: register accelerometer only while gesture could fire (structural difference from Tasker).
        var registered = false
        fun canFire(): Boolean = interactive.get() && pluggedRequirementMet()
        fun syncSensors() {
            val want = canFire()
            if (want == registered) return
            if (want) {
                // SENSOR_DELAY_GAME (~50 Hz): matches A2 Java, fast enough for shake tracking.
                sensorManager.registerListener(accelListener, accel, SensorManager.SENSOR_DELAY_GAME)
                if (linear != null && linearListener != null) {
                    sensorManager.registerListener(linearListener, linear, SensorManager.SENSOR_DELAY_GAME)
                }
            } else {
                sensorManager.unregisterListener(accelListener)
                if (linearListener != null) sensorManager.unregisterListener(linearListener)
                // resetWindow() not endWindow(): releasing is not a gesture outcome, so don't latch re-entry gate.
                resetWindow()
                detector.reset()  // Clean gravity estimate for next registration.
            }
            registered = want
        }

        val stateReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> interactive.set(true)
                    Intent.ACTION_SCREEN_OFF -> interactive.set(false)
                    Intent.ACTION_POWER_CONNECTED -> plugged.set(true)
                    Intent.ACTION_POWER_DISCONNECTED -> plugged.set(false)
                }
                syncSensors()
            }
        }
        context.registerReceiver(
            stateReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            },
        )
        syncSensors()

        awaitClose {
            sensorManager.unregisterListener(accelListener)
            if (linearListener != null) sensorManager.unregisterListener(linearListener)
            runCatching { context.unregisterReceiver(stateReceiver) }
        }
    }
}
