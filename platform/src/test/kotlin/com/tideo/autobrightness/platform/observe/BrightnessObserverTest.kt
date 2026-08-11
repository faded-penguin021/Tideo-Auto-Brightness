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
        // Pre-set brightness before observer registration; tests use notifyChange() only
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

        // Trigger observers via public Android API (Robolectric intercepts); ContentObserver called synchronously
        context.contentResolver.notifyChange(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), null
        )

        job.cancel()
        assertTrue(received.isNotEmpty(), "Expected emission after notifyChange (raw=150, not self-write)")
    }

    @Test
    fun selfWrite_isFiltered_externalChangeStillEmitted() = runTest(UnconfinedTestDispatcher()) {
        val received = mutableListOf<Int>()
        val job = launch(UnconfinedTestDispatcher()) {
            observer.externalChanges().collect { received.add(it) }
        }
        val uri = Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS)

        // Self-write: controller records marker; notify must be filtered
        controller.write(150)
        context.contentResolver.notifyChange(uri, null)

        // External write to different value; must be emitted (guards against vacuous pass)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 42)
        context.contentResolver.notifyChange(uri, null)

        job.cancel()
        assertTrue(received.none { it == 150 }, "Self-write (150) should have been filtered out")
        assertTrue(received.contains(42), "External change (42) should have been emitted")
    }
}
