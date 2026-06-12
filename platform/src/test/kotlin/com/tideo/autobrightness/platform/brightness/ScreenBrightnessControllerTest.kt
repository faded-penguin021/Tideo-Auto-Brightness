package com.tideo.autobrightness.platform.brightness

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ScreenBrightnessControllerTest {
    private lateinit var context: Context
    private lateinit var controller: AndroidScreenBrightnessController

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        controller = AndroidScreenBrightnessController(context)
    }

    @Test
    fun write_and_read_roundtrip() {
        controller.write(128)
        assertEquals(128, controller.read())
    }

    @Test
    fun write_boundary_min() {
        controller.write(0)
        assertEquals(0, controller.read())
    }

    @Test
    fun write_boundary_max() {
        controller.write(255)
        assertEquals(255, controller.read())
    }

    @Test
    fun forceManualMode_setsManualBrightnessMode() {
        controller.forceManualMode()
        val mode = Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            -1,
        )
        assertEquals(Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL, mode)
    }

    @Test
    fun restoreMode_restoresPreviousMode() {
        // Set automatic mode first, then force manual, then restore.
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
        )
        controller.forceManualMode()
        controller.restoreMode()
        val mode = Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            -1,
        )
        assertEquals(Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC, mode)
    }

    @Test
    fun registerExpectedWrite_isSelfWrite_trueOnce() {
        controller.registerExpectedWrite(100)
        assertTrue(controller.isSelfWrite(100))
        // Consumed after first check.
        assertFalse(controller.isSelfWrite(100))
    }

    @Test
    fun isSelfWrite_unknownValue_returnsFalse() {
        assertFalse(controller.isSelfWrite(42))
    }

    @Test
    fun clearExpectedWrites_removesAll() {
        controller.registerExpectedWrite(50)
        controller.registerExpectedWrite(60)
        controller.clearExpectedWrites()
        assertFalse(controller.isSelfWrite(50))
        assertFalse(controller.isSelfWrite(60))
    }
}
