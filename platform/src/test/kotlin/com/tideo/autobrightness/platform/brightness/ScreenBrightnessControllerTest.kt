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
    fun isSelfWrite_matchesLastWrite_repeatable() {
        controller.write(100)
        assertTrue(controller.isSelfWrite(100))
        // %LastAAB semantics: marker is NOT consumed — delayed observer callbacks for
        // earlier animation frames re-read the latest value and must still match.
        assertTrue(controller.isSelfWrite(100))
    }

    @Test
    fun isSelfWrite_tracksLatestWriteOnly() {
        controller.write(50)
        controller.write(60)
        assertFalse(controller.isSelfWrite(50))
        assertTrue(controller.isSelfWrite(60))
    }

    @Test
    fun isSelfWrite_unknownValue_returnsFalse() {
        assertFalse(controller.isSelfWrite(42))
    }

    @Test
    fun clearSelfWriteMarker_forgetsLastWrite() {
        controller.write(50)
        controller.clearSelfWriteMarker()
        assertFalse(controller.isSelfWrite(50))
    }

    @Test
    fun forceManualMode_isIdempotent_restoresOriginalMode() {
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
        )
        controller.forceManualMode()
        controller.forceManualMode() // second call must not overwrite saved AUTOMATIC
        controller.restoreMode()
        val mode = Settings.System.getInt(
            context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, -1,
        )
        assertEquals(Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC, mode)
    }

    @Test
    fun oemNormalization_roundTripIsIdentity_for1023Max() {
        val oem = AndroidScreenBrightnessController(context, deviceMaxOverride = 1023)
        for (domain in intArrayOf(0, 1, 99, 100, 128, 254, 255)) {
            oem.write(domain)
            assertEquals(domain, oem.read(), "round-trip failed for domain=$domain")
        }
    }

    @Test
    fun oemNormalization_writeScalesToDeviceRange() {
        val oem = AndroidScreenBrightnessController(context, deviceMaxOverride = 1023)
        oem.write(255)
        val raw = Settings.System.getInt(
            context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1,
        )
        assertEquals(1023, raw)
    }

    @Test
    fun write_clampsOutOfRangeDomainInput() {
        controller.write(300)
        assertEquals(255, controller.read())
        controller.write(-5)
        assertEquals(0, controller.read())
    }
}
