package com.tideo.autobrightness.platform.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.SensorEventBuilder
import org.robolectric.shadows.ShadowSensor
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LightSensorSourceTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun source_instantiates_without_throwing() {
        val source = AndroidLightSensorSource(context)
        assertNotNull(source)
    }

    @Test
    fun samples_flow_cancels_cleanly() = runTest {
        val source = AndroidLightSensorSource(context)
        // Robolectric has no real TYPE_LIGHT sensor; flow either closes immediately
        // (sensor == null path) or suspends at awaitClose. Cancellation must complete cleanly.
        val registered = mutableListOf<Boolean>()
        val job = launch { source.samples(onRegistered = { registered += it }).collect { } }
        advanceTimeBy(50)
        job.cancel()
        job.join()
    }

    @Test
    fun each_registration_numbers_its_callbacks_and_reports_them_before_the_flow() = runTest {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val shadow = shadowOf(manager)
        val light = ShadowSensor.newInstance(Sensor.TYPE_LIGHT)
        shadow.addSensor(light)
        val source = AndroidLightSensorSource(context)
        fun send(lux: Float) = shadow.sendSensorEventToListeners(
            SensorEventBuilder.newBuilder().setSensor(light).setValues(floatArrayOf(lux))
                .setAccuracy(SensorManager.SENSOR_STATUS_ACCURACY_HIGH).setTimestamp(42L).build(),
        )

        repeat(2) {
            val registered = mutableListOf<Boolean>()
            val callbacks = mutableListOf<LightSample>()
            val collected = mutableListOf<LightSample>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) {
                source.samples(onRegistered = { registered += it }, onCallback = { callbacks += it })
                    .collect { collected += it }
            }
            assertEquals(listOf(true), registered)
            send(10f)
            send(20f)
            assertEquals(listOf(1, 2), callbacks.map { it.seq })
            assertEquals(callbacks, collected)
            assertEquals(42L, callbacks[0].timestampNanos)
            assertEquals(SensorManager.SENSOR_STATUS_ACCURACY_HIGH, callbacks[0].accuracy)
            job.cancel()
            job.join()
        }
    }
}
