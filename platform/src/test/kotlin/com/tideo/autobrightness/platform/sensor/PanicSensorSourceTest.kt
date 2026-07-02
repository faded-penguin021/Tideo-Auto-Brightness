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
 * H3 glue-seam audit: `AndroidPanicSensorSource` arming had no test — the sustained-inversion
 * requirement, the pass-through fire, the 10 s window veto, and the consume-until-re-entry latch
 * (prof769 rework, D-116) are all glue in this source.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PanicSensorSourceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private var now = 0L
    private var sensitivity = 0
    private var near = false

    private fun source(windowMs: Long = 10_000L) = AndroidPanicSensorSource(
        context = context,
        sensitivity = { sensitivity },
        isNear = { near },
        windowMs = windowMs,
        clock = { now },
    )

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

        // Still inverted: the gesture is consumed — more frames must NOT re-fire (Tasker STATE
        // semantics: no re-trigger until the phone leaves the state and re-enters, D-021).
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
        sensitivity = 5 // real shake required → a 10 s window opens on arming
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
        near = true // %AAB_Proximity ~ Near: covered/in-pocket must never panic
        val events = mutableListOf<Unit>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { source().events().collect { events += it } }

        upsideDownFrames(20)
        assertEquals(0, events.size, "proximity-near must block the panic gesture")
        job.cancel()
    }
}
