package com.tideo.autobrightness.platform.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * H3 glue-seam audit: sustained-inversion, pass-through fire, 10 s window veto, re-entry latch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PanicSensorSourceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private var now = 0L
    private var sensitivity = 0
    private var near = false
    /** The nullable truth the source consumes; `requiresPlugged` is the boolean shorthand for it. */
    private var requiresPluggedOrNull: Boolean? = false
    private var requiresPlugged: Boolean
        get() = requiresPluggedOrNull == true
        set(value) { requiresPluggedOrNull = value }

    private fun source(windowMs: Long = 10_000L) = AndroidPanicSensorSource(
        context = context,
        sensitivity = { sensitivity },
        isNear = { near },
        requiresPlugged = { requiresPluggedOrNull },
        windowMs = windowMs,
        clock = { now },
    )

    /** How many listeners the source currently holds — the battery question, made observable. */
    private fun registeredListenerCount(): Int = shadowOf(sensorManager).listeners.size

    /**
     * Send broadcast and let it be delivered; idle() ensures receiver runs before assertions.
     */
    private fun broadcast(action: String) {
        context.sendBroadcast(android.content.Intent(action))
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    private fun accelSensor(): Sensor {
        val sensor = ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER)
        shadowOf(sensorManager).addSensor(sensor)
        return sensor
    }

    /** Feed one raw accelerometer sample (device-frame m/s²) to the registered listener. */
    private fun sample(x: Float, y: Float, z: Float) {
        val event = ShadowSensorManager.createSensorEvent(3, Sensor.TYPE_ACCELEROMETER)
        event.values[0] = x
        event.values[1] = y
        event.values[2] = z
        shadowOf(sensorManager).sendSensorEventToListeners(event)
    }

    private fun upsideDownFrames(count: Int) = repeat(count) { sample(0f, -9.81f, 0f) }
    private fun uprightFrames(count: Int) = repeat(count) { sample(0f, 9.81f, 0f) }

    @Test
    fun sustainedInversion_passThroughSensitivity_firesOnce_thenNeedsReEntry() = runTest {
        accelSensor()
        sensitivity = 0 // pass-through: no shake required (slider 0)
        val events = mutableListOf<Unit>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { source().events().collect { events += it } }

        // Frame 1 seeds the gravity filter; frames 2..6 build the 5-frame sustained streak.
        upsideDownFrames(6)
        assertEquals(1, events.size, "sustained inversion at sensitivity 0 fires immediately")

        // D-021: consumed gesture; no re-fire until re-entry.
        upsideDownFrames(10)
        assertEquals(1, events.size, "no re-fire while the phone stays inverted")

        // Flip straight, then invert again → a fresh gesture fires.
        uprightFrames(30)
        upsideDownFrames(40)
        assertEquals(2, events.size, "re-entry re-arms the gesture")
        job.cancel()
    }

    @Test
    fun transientFlip_doesNotFire() = runTest {
        accelSensor()
        sensitivity = 0
        val events = mutableListOf<Unit>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { source().events().collect { events += it } }

        // Only 3 inverted frames (< sustainedFrames 5) between upright phases: never armed.
        uprightFrames(3)
        upsideDownFrames(3)
        uprightFrames(3)
        assertEquals(0, events.size, "a transient flip must not arm the panic gesture")
        job.cancel()
    }

    @Test
    fun armedWindow_noQualifyingShake_vetoesOnTimeout() = runTest {
        accelSensor()
        sensitivity = 5 // real shake required; 10 s window opens on arming
        val events = mutableListOf<Unit>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { source().events().collect { events += it } }

        upsideDownFrames(6) // arm → window opens (steady hold, no shake)
        assertEquals(0, events.size, "no fire while the window waits for a shake")

        now += 10_001L // the 10 s window elapses with no qualifying shake
        upsideDownFrames(5) // next samples close the window (veto) and must not fire
        assertEquals(0, events.size, "window timeout vetoes the gesture")

        // Still inverted after the veto: consumed — no new window, no fire.
        upsideDownFrames(10)
        assertEquals(0, events.size, "vetoed gesture stays consumed until re-entry")
        job.cancel()
    }

    @Test
    fun proximityNear_blocksArming() = runTest {
        accelSensor()
        sensitivity = 0
        near = true // Covered/in-pocket must never panic
        val events = mutableListOf<Unit>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { source().events().collect { events += it } }

        upsideDownFrames(20)
        assertEquals(0, events.size, "proximity-near must block the panic gesture")
        job.cancel()
    }

    // ---- DB-009: %AAB_PanicPlugged + registration gate (issue #110) ----

    @Test
    fun requiresPlugged_whileOnBattery_doesNotEvenRegisterTheAccelerometer() = runTest {
        accelSensor()
        sensitivity = 0
        requiresPlugged = true // and the Robolectric default battery state is "not plugged"
        val events = mutableListOf<Unit>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { source().events().collect { events += it } }

        assertEquals(
            0,
            registeredListenerCount(),
            "an unsatisfiable gesture must not hold a 50 Hz sensor listener open",
        )

        // Nothing to feed the detector, so nothing can fire.
        upsideDownFrames(40)
        assertEquals(0, events.size, "the gesture must not fire while the plugged requirement is unmet")
        job.cancel()
    }

    @Test
    fun requiresPlugged_registersOnPowerConnected_andReleasesOnDisconnect() = runTest {
        accelSensor()
        sensitivity = 0
        requiresPlugged = true
        val events = mutableListOf<Unit>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { source().events().collect { events += it } }
        assertEquals(0, registeredListenerCount())

        broadcast(android.content.Intent.ACTION_POWER_CONNECTED)
        assertEquals(1, registeredListenerCount(), "plugging in must arm the sensor")
        upsideDownFrames(6)
        assertEquals(1, events.size, "the gesture works normally once plugged in")

        broadcast(android.content.Intent.ACTION_POWER_DISCONNECTED)
        assertEquals(
            0,
            registeredListenerCount(),
            "unplugging must release the sensor again, not leave it running",
        )
        job.cancel()
    }

    @Test
    fun screenOff_releasesTheSensor_evenWithoutThePluggedRestriction() = runTest {
        accelSensor()
        sensitivity = 0
        requiresPlugged = false
        val events = mutableListOf<Unit>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { source().events().collect { events += it } }
        assertEquals(1, registeredListenerCount(), "screen on + no restriction → the gesture is live")

        // DB-009: screen off must release sensor; holding it open costs ~50 Hz all night.
        broadcast(android.content.Intent.ACTION_SCREEN_OFF)
        assertEquals(0, registeredListenerCount(), "screen off must release the accelerometer")

        broadcast(android.content.Intent.ACTION_SCREEN_ON)
        assertEquals(1, registeredListenerCount(), "screen on must re-arm it")
        upsideDownFrames(6)
        assertEquals(1, events.size, "the gesture still works after a screen-off/on cycle")
        job.cancel()
    }

    // ---- DB-011: requirement authoritative at ARM time, not just registration ----

    @Test
    fun requirementTurningOnAfterRegistration_stopsTheGesture_withNoPowerBroadcast() = runTest {
        accelSensor()
        sensitivity = 0
        requiresPlugged = false // registers: screen on, no restriction
        val events = mutableListOf<Unit>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { source().events().collect { events += it } }
        assertEquals(1, registeredListenerCount())

        // DB-011: no broadcast arrives to re-run registration; requirement must gate FIRING too.
        requiresPlugged = true
        upsideDownFrames(40)
        assertEquals(0, events.size, "the plugged requirement must gate FIRING, not just registration")
        job.cancel()
    }

    @Test
    fun unknownRequirement_neitherRegistersNorFires() = runTest {
        accelSensor()
        sensitivity = 0
        requiresPluggedOrNull = null // no effective-settings snapshot yet (service just started)
        val events = mutableListOf<Unit>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { source().events().collect { events += it } }

        assertEquals(0, registeredListenerCount(), "an unknown requirement must not arm the sensor")
        upsideDownFrames(40)
        assertEquals(
            0,
            events.size,
            "an unknown restriction must not be read as 'no restriction' — that is how a plugged-only " +
                "gesture fired on battery",
        )
        job.cancel()
    }

    @Test
    fun unknownRequirement_isTransient_gestureWorksOnceTheSnapshotArrives() = runTest {
        accelSensor()
        sensitivity = 0
        requiresPluggedOrNull = null
        val events = mutableListOf<Unit>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { source().events().collect { events += it } }
        assertEquals(0, registeredListenerCount())

        // Fail-closed is not one-way; resolved snapshot re-runs decision on next screen-on.
        requiresPluggedOrNull = false
        broadcast(android.content.Intent.ACTION_SCREEN_ON)
        assertEquals(1, registeredListenerCount(), "a resolved snapshot must re-arm the sensor")
        upsideDownFrames(6)
        assertEquals(1, events.size, "the gesture must work normally once the requirement is known")
        job.cancel()
    }

    @Test
    fun aWindowInterruptedByScreenOff_doesNotSurviveIntoTheNextRegistration() = runTest {
        accelSensor()
        sensitivity = 5 // a real shake window, not pass-through
        val events = mutableListOf<Unit>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { source(windowMs = 10_000L).events().collect { events += it } }

        upsideDownFrames(6) // arms a shake window
        broadcast(android.content.Intent.ACTION_SCREEN_OFF)
        broadcast(android.content.Intent.ACTION_SCREEN_ON)

        // Gravity filter reset; half-finished window must not resume across the gap.
        sample(0f, -9.81f, 0f)
        assertEquals(0, events.size, "a stale window must not fire after the sensor was released")
        job.cancel()
    }
}
