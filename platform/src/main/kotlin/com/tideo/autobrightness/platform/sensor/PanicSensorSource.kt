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

/**
 * Orientation detector for the prof769 "Panic (Reset)" STATE (D-021, D-116).
 *
 * The reworked panic (`tmp/Panic_profile_task.md`) gates on `Orientation [Is:Upside Down] ∧ Display
 * State [Is:On] ∧ %AAB_Proximity !~ Near` — the old significant-motion EVENT is gone; the shake is now
 * validated separately by [PanicShakeGate] inside a 10 s window. This class supplies the **upside-down**
 * half of that state from the accelerometer:
 *  - **Gravity** (a low-pass of the raw accelerometer) gives the device orientation. Android reads
 *    **+9.8 on whichever axis points up** at rest, so held upright `gravity.y ≈ +9.8`; held **upside
 *    down** (top edge down) `gravity.y ≈ −9.8`. Upside-down ≡ `gravity.y < −[upsideDownGravityY]` AND y
 *    is the dominant axis (|gy| ≥ |gx|, |gz|) — a phone lying flat or in landscape never qualifies.
 *  - [isUpsideDown] is the instantaneous (filtered) orientation; [onAccelerometer] returns the
 *    **sustained** orientation (held for [sustainedFrames] readings) so a transient flip during a shake
 *    the right way up cannot arm the gesture (G2R-F77 / S14: panic must fire ONLY when genuinely inverted).
 *  - [linearMagnitude] exposes the gravity-stripped acceleration magnitude so the Android source can run
 *    the shake gate off the bare accelerometer when the device has no `TYPE_LINEAR_ACCELERATION` sensor
 *    (mirrors the A2 Java high-pass fallback).
 *
 * Pure + frame-by-frame so the orientation timing is unit-testable without a [SensorManager].
 */
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

/**
 * Re-arm latch for the panic shake window (task528 `_PanicButton`, D-021 / D-116).
 *
 * The prof769 trigger is a STATE (Upside-Down ∧ Display-On ∧ Proximity-not-Near). When it becomes true a
 * single 10 s [PanicShakeGate] window runs; whether that window fires the panic (a qualifying shake) or
 * **times out** (no shake), the gesture must NOT start another window until the phone leaves the
 * upside-down state and returns — exactly as a Tasker STATE only re-fires on re-entry ("the profile
 * won't trigger again until the phone is flipped straight and then upside-down again", `tmp/Tmp.md`).
 *
 * D-165: "leaves the upside-down state" must be a **sustained** straight spell ([rearmFrames]
 * consecutive non-inverted readings), not a single reading. The instantaneous orientation flickers
 * false during a vigorous same-axis shake — the exact artifact the 10 s window is already immune to
 * (see the window comment in [AndroidPanicSensorSource]) — and the α=0.9 gravity low-pass keeps it
 * false for ~5-6 readings after ONE strong spike even with the phone held stably inverted. A
 * single-reading clear therefore let "timed-out window → keep holding inverted → shake hard" re-open
 * a fresh window and fire a real panic without any flip straight, breaking the STATE re-entry contract.
 *
 * Pure state machine, clock-free: the Android source owns the orientation/display/proximity sensing and
 * the 10 s window; this only decides *whether a new window may start*.
 */
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

/**
 * Accelerometer-backed [PanicSensorSource] for the reworked prof769 (D-116).
 *
 * Registers `TYPE_ACCELEROMETER` for orientation (via [PanicGestureDetector]) and, when present,
 * `TYPE_LINEAR_ACCELERATION` for the shake magnitude (else it high-passes the accelerometer like the A2
 * Java fallback). When the armed state (sustained upside-down ∧ display-on ∧ proximity-not-near) becomes
 * true it opens a [windowMs] window driven by a fresh [PanicShakeGate]`(sensitivity())`:
 *  - sensitivity 0 ⇒ pass-through: fire immediately, no shake required;
 *  - shake reaches target within the window ⇒ fire;
 *  - window elapses with no qualifying shake ⇒ veto (no fire).
 * Either outcome consumes the gesture ([PanicGate]) so it cannot re-fire until the phone is flipped
 * straight and inverted again. All sensor/screen callbacks are delivered on a single looper, so the
 * window state is mutated single-threaded (no locks needed).
 */
class AndroidPanicSensorSource(
    private val context: Context,
    /** Current `%AAB_PanicSensitivity` (0..10). Read per arming so a slider change takes effect at once. */
    private val sensitivity: () -> Int,
    /** Current `%AAB_Proximity ~ Near` — the gesture only arms while NOT near (covered/in-pocket = no panic). */
    private val isNear: () -> Boolean,
    /**
     * Current `%AAB_PanicPlugged` (DB-009, issue #110): when true the gesture only works on external
     * power. Read at every registration decision, not cached, so flipping it takes effect at the next
     * screen or power transition.
     */
    private val requiresPlugged: () -> Boolean = { false },
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

        // DB-009: plugged state, seeded from the STICKY battery broadcast (registerReceiver(null, …)
        // reads the last one without registering anything) and then maintained on the two explicit
        // power-transition broadcasts. ACTION_BATTERY_CHANGED itself is deliberately NOT registered:
        // it fires on every level/temperature tick, which is exactly the kind of always-on cost this
        // gate exists to remove.
        val plugged = AtomicBoolean(
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                ?.let { it > 0 } ?: false,
        )

        // --- Window state (mutated only from sensor callbacks, all on one looper → single-threaded) ---
        var windowActive = false
        var windowDeadline = 0L
        var shakeGate: PanicShakeGate? = null
        // Latest shake magnitude: from the linear-accel sensor when present, else the detector's
        // gravity-stripped accelerometer residual.
        var shakeMagnitude = 0.0

        // Clear the in-flight window WITHOUT consuming the gesture. Used when the sensor is released
        // (DB-009): releasing is not an outcome of the gesture, so it must not latch the
        // consume-until-re-entry gate — doing so left the user needing a flip-straight-and-back after
        // every screen-off, which a test caught immediately.
        fun resetWindow() {
            windowActive = false
            shakeGate = null
        }

        // Both window OUTCOMES — a qualifying shake (fire) and the 10 s timeout (veto) — consume the
        // gesture: it will not re-arm until the phone is flipped straight and inverted again (D-021).
        fun endWindow() {
            resetWindow()
            gate.consume()
        }

        val accelListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val now = clock()
                val sustainedUpsideDown = detector.onAccelerometer(event.values[0], event.values[1], event.values[2])
                if (linear == null) shakeMagnitude = detector.linearMagnitude
                val armed = sustainedUpsideDown && interactive.get() && !isNear()

                if (windowActive) {
                    // Faithful to the A2 Java: once armed, the 10 s window runs to completion and is NOT
                    // re-gated on orientation. A vigorous up-and-down shake while inverted is along the
                    // SAME axis as gravity, so it transiently disturbs the gravity-based `isUpsideDown`
                    // estimate; abandoning the window on that flicker made up-down shakes self-cancel while
                    // orthogonal left-right shakes survived (owner: "shake direction wrong"). The shake
                    // magnitude is omnidirectional, so the window must not depend on shake direction.
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

        // --- DB-009: register the accelerometer ONLY while the gesture could actually fire --------
        //
        // This is the structural difference from Tasker, and the reason it mattered. There, the
        // profile's Orientation STATE does the watching (the platform's job, effectively free) and the
        // A3 Java registers the accelerometer for at most the 10 s shake window. Here the orientation
        // watch IS the trigger, so the listener was registered for the entire life of the service —
        // SENSOR_DELAY_GAME, ~50 Hz, all day, including with the screen off, where the gesture cannot
        // fire at all because arming requires `interactive`.
        //
        // The gate below is exactly the set of preconditions that are cheap to observe via broadcast
        // and that make firing impossible while false:
        //   - screen off        → arming already required `interactive`; nothing is lost.
        //   - unplugged, when the user asked for plugged-only (A3's early veto, before it registers
        //     its own listener).
        // Everything else (orientation, proximity, shake) still needs the sensor to evaluate.
        var registered = false
        fun canFire(): Boolean = interactive.get() && (!requiresPlugged() || plugged.get())
        fun syncSensors() {
            val want = canFire()
            if (want == registered) return
            if (want) {
                // SENSOR_DELAY_GAME (~50 Hz) matches the A2 Java's registration — fast enough to track
                // a shake.
                sensorManager.registerListener(accelListener, accel, SensorManager.SENSOR_DELAY_GAME)
                if (linear != null && linearListener != null) {
                    sensorManager.registerListener(linearListener, linear, SensorManager.SENSOR_DELAY_GAME)
                }
            } else {
                sensorManager.unregisterListener(accelListener)
                if (linearListener != null) sensorManager.unregisterListener(linearListener)
                // A half-finished gesture must not survive the gap: drop the window and the filter
                // state so the next registration starts from a clean, unseeded gravity estimate.
                // resetWindow(), NOT endWindow() — see above.
                resetWindow()
                detector.reset()
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
