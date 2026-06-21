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
 *  - **Gravity** (a low-pass of the raw accelerometer) gives the device orientation. Android's
 *    accelerometer reads **+9.8 on whichever axis points up** at rest, so held upright (top edge up)
 *    `gravity.y ≈ +9.8`; held **upside down** (top edge down) it points the other way, `gravity.y ≈
 *    −9.8`. So upside-down ≡ `gravity.y < −[upsideDownGravityY]` and y is the dominant axis.
 *  - **Shake** is the residual linear acceleration magnitude (raw − gravity); a spike past
 *    [shakeThreshold] m/s² is a shake (the Tasker "shake" event).
 *
 * Pure + frame-by-frame so the timing is unit-testable without a [SensorManager]; the Android source
 * feeds it raw accelerometer triples and gates the result on the display being on (State 123/1).
 */
class PanicGestureDetector(
    // S14 (owner: panic too sensitive): require a more committed inversion. gy < −8.0 ≈ within ~35° of
    // fully upside down (was −7.0 ≈ 44°), so a phone held at a casual downward angle no longer counts.
    private val upsideDownGravityY: Float = 8.0f,
    private val shakeThreshold: Float = 12.0f,
    // Heavy low-pass so a vigorous shake does NOT drag the gravity estimate across the upside-down
    // threshold (G2R-F77: panic must fire ONLY when genuinely inverted, never the right way up).
    private val gravityAlpha: Float = 0.9f,
    // The inversion must be held for this many readings before a shake can fire — a transient flip
    // during a shake the right way up cannot satisfy it.
    private val sustainedFrames: Int = 5,
) {
    private val gravity = FloatArray(3)
    private var seeded = false
    private var upsideDownStreak = 0

    /**
     * Feed one raw accelerometer reading (m/s², device frame). Returns true on the reading that
     * completes the panic gesture: the device has been **stably upside down** AND a shake spike is
     * present. "Upside down" means the gravity vector points toward the **bottom** of the screen
     * (gravity.y ≈ −9.8) AND y is the dominant axis (|gy| > |gx|, |gz|) — so a phone lying flat / held
     * upright / in landscape does not qualify even while shaking (G2R-F77).
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

        val gy = gravity[1]
        val upsideDown = gy < -upsideDownGravityY &&
            kotlin.math.abs(gy) >= kotlin.math.abs(gravity[0]) &&
            kotlin.math.abs(gy) >= kotlin.math.abs(gravity[2])
        upsideDownStreak = if (upsideDown) upsideDownStreak + 1 else 0

        val lx = x - gravity[0]
        val ly = y - gravity[1]
        val lz = z - gravity[2]
        val shakeMag = sqrt(lx * lx + ly * ly + lz * lz)
        return upsideDownStreak >= sustainedFrames && shakeMag > shakeThreshold
    }

    fun reset() {
        seeded = false
        upsideDownStreak = 0
        gravity.fill(0f)
    }
}

/**
 * Lifecycle gate around [PanicGestureDetector] for the Android source (S14, owner: the panic fired
 * when grabbing the phone out of a pocket and turning the screen on). Two protections beyond the
 * detector's orientation/shake test:
 *
 *  - **Screen-on grace:** waking the phone (grab + press) IS a shake, usually while the device is still
 *    being rotated upright. For [screenOnGraceMs] after the display becomes interactive no panic can
 *    fire, and the caller resets the detector on the wake edge so an inversion+shake streak built up in
 *    the pocket cannot complete the gesture the instant the screen turns on.
 *  - **Cooldown:** a detected gesture fires the panic at most once per [cooldownMs] (debounce).
 *
 * Pure + clock-injected so the timing is unit-testable without a SensorManager/PowerManager.
 */
class PanicGate(
    private val screenOnGraceMs: Long = 3_000L,
    private val cooldownMs: Long = 3_000L,
) {
    private var wasInteractive = false
    private var graceUntilMs = 0L
    // null = never fired. (A sentinel like Long.MIN_VALUE would overflow `now - lastFiredMs`.)
    private var lastFiredMs: Long? = null

    /**
     * Feed the display-interactive state for this reading (call BEFORE the detector). Returns true on
     * the rising edge (the screen just turned on) so the caller resets the gesture detector and the
     * [screenOnGraceMs] window opens.
     */
    fun onScreenState(now: Long, interactive: Boolean): Boolean {
        val justWoke = interactive && !wasInteractive
        if (justWoke) graceUntilMs = now + screenOnGraceMs
        wasInteractive = interactive
        return justWoke
    }

    /**
     * Whether a detector-reported gesture should actually fire now: display on (State 123/1), past the
     * wake grace, and past the cooldown. Records the fire time when it returns true.
     */
    fun shouldFire(now: Long, interactive: Boolean): Boolean {
        if (!interactive) return false
        if (now < graceUntilMs) return false
        val last = lastFiredMs
        if (last != null && now - last < cooldownMs) return false
        lastFiredMs = now
        return true
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
    private val gate: PanicGate = PanicGate(),
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
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val now = clock()
                val interactive = power.isInteractive
                // On the screen-on edge, reset the gesture streak so an inversion+shake built up in the
                // pocket can't complete the instant the display wakes (owner: false-fired on grab-to-wake).
                if (gate.onScreenState(now, interactive)) detector.reset()
                val fired = detector.onAccelerometer(event.values[0], event.values[1], event.values[2])
                // Gate on display-on (State 123/1) + the post-wake grace + the one-shot cooldown.
                if (fired && gate.shouldFire(now, interactive)) trySend(Unit)
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
