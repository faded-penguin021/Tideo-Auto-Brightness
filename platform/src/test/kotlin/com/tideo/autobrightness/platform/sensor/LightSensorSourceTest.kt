package com.tideo.autobrightness.platform.sensor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
        val job = launch { source.samples().collect { } }
        advanceTimeBy(50)
        job.cancel()
        job.join()
    }
}
