package com.tideo.autobrightness.platform.observe

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.tideo.autobrightness.platform.brightness.AndroidScreenBrightnessController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BrightnessObserverTest {
    private lateinit var context: Context
    private lateinit var controller: AndroidScreenBrightnessController
    private lateinit var observer: AndroidBrightnessObserver

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Pre-set brightness BEFORE the ContentObserver is registered so putInt doesn't fire
        // the observer during setup. Tests call notifyChange() only (no putInt mid-test).
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 150)
        controller = AndroidScreenBrightnessController(context)
        observer = AndroidBrightnessObserver(context, controller)
    }

    @Test
    fun externalChange_isEmitted() = runTest(UnconfinedTestDispatcher()) {
        val received = mutableListOf<Int>()
        val job = launch(UnconfinedTestDispatcher()) {
            observer.externalChanges().collect { received.add(it) }
        }

        // Trigger registered observers via the public Android API (Robolectric intercepts).
        // The null-handler ContentObserver is called synchronously by the shadow.
        context.contentResolver.notifyChange(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), null
        )

        job.cancel()
        assertTrue(received.isNotEmpty(), "Expected emission after notifyChange (raw=150, not self-write)")
    }

    @Test
    fun selfWrite_isFiltered() = runTest(UnconfinedTestDispatcher()) {
        val received = mutableListOf<Int>()
        val job = launch(UnconfinedTestDispatcher()) {
            observer.externalChanges().collect { received.add(it) }
        }

        // Mark the current value (150) as a self-write before notifying.
        controller.registerExpectedWrite(150)
        context.contentResolver.notifyChange(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), null
        )

        job.cancel()
        assertTrue(received.none { it == 150 }, "Self-write (150) should have been filtered out")
    }
}
