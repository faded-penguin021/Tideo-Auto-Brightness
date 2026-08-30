package com.tideo.autobrightness.platform.brightness

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
        // %LastAAB semantics: marker NOT consumed; delayed callbacks must still match.
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
        controller.forceManualMode() // Must not overwrite saved AUTOMATIC.
        controller.restoreMode()
        val mode = Settings.System.getInt(
            context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, -1,
        )
        assertEquals(Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC, mode)
    }

    @Test
    fun restoreMode_survivesProcessDeath_restoresAutomatic_D134() {
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
        )
        controller.forceManualMode()
        // Process death: new instance must know pre-service mode (fresh state, same prefs).
        val afterRestart = AndroidScreenBrightnessController(context)
        afterRestart.restoreMode()
        val mode = Settings.System.getInt(
            context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, -1,
        )
        assertEquals(Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC, mode)
    }

    @Test
    fun forceManualMode_afterProcessDeathMidManual_keepsPersistedAutomatic_D134() {
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
        )
        controller.forceManualMode() // saves AUTOMATIC, device now MANUAL
        // Process death: restarted instance must keep persisted AUTOMATIC, not re-save MANUAL.
        val afterRestart = AndroidScreenBrightnessController(context)
        afterRestart.forceManualMode()
        afterRestart.restoreMode()
        val mode = Settings.System.getInt(
            context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, -1,
        )
        assertEquals(Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC, mode)
    }

    @Test
    fun forceManualMode_freshStartWithNonManualMode_overwritesStalePersistedValue_D134() {
        // Stale persisted MANUAL from crash; user now in AUTOMATIC. Non-MANUAL current mode wins.
        context.getSharedPreferences(
            AndroidScreenBrightnessController.PREFS_NAME, Context.MODE_PRIVATE,
        ).edit().putInt(
            AndroidScreenBrightnessController.KEY_SAVED_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        ).commit()
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
        )
        controller.forceManualMode()
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

    /** DC-002: an OEM that stores [store] of what we asked for; reads stay real. */
    private fun normalizing(store: (Int) -> Int, deviceMax: Int = 1023) =
        AndroidScreenBrightnessController(
            context,
            deviceMaxOverride = deviceMax,
            rawWrite = { raw ->
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, store(raw))
            },
        )

    @Test
    fun normalizedWrite_marksTheSTOREDValue_soTheEchoIsASelfWrite() {
        val oem = normalizing(store = { 3083 })
        val result = oem.write(255)
        assertEquals(WriteStatus.ACKNOWLEDGED, result.status)
        assertEquals(1023, result.requestedRaw)
        assertEquals(3083, result.acknowledgedRaw)
        assertEquals(1023, result.deviceMax)
        assertTrue(oem.isSelfWrite(3083))
        assertFalse(oem.isSelfWrite(1023), "the requested raw was never on screen")
    }

    @Test
    fun callbackDuringPutInt_isASelfWrite_selfWriteInProgress() {
        var seenMidWrite: Boolean? = null
        lateinit var oem: AndroidScreenBrightnessController
        oem = AndroidScreenBrightnessController(
            context,
            deviceMaxOverride = 255,
            rawWrite = { raw ->
                seenMidWrite = oem.isSelfWrite(9999)
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, raw)
            },
        )
        oem.write(100)
        assertEquals(true, seenMidWrite)
        assertFalse(oem.isSelfWrite(9999), "the in-progress flag must not survive the write")
    }

    @Test
    fun refusedWrite_restoresThePreviousMarker_andReportsREFUSED() {
        var refuse = false
        val oem = AndroidScreenBrightnessController(
            context,
            deviceMaxOverride = 255,
            rawWrite = { raw ->
                if (refuse) false
                else Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, raw)
            },
        )
        oem.write(70)
        refuse = true
        val result = oem.write(200)
        assertEquals(WriteStatus.REFUSED, result.status)
        assertNull(result.acknowledgedRaw)
        assertNull(result.acknowledgedDomain)
        assertTrue(oem.isSelfWrite(70), "a refused write must leave the previous marker in place")
        assertFalse(oem.isSelfWrite(200))
    }

    @Test
    fun deniedWrite_reportsDENIED_andRestoresThePreviousMarker() {
        var deny = false
        val oem = AndroidScreenBrightnessController(
            context,
            deviceMaxOverride = 255,
            rawWrite = { raw ->
                if (deny) throw SecurityException("WRITE_SETTINGS revoked")
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, raw)
            },
        )
        oem.write(70)
        deny = true
        val result = oem.write(200)
        assertEquals(WriteStatus.DENIED, result.status)
        assertNull(result.acknowledgedRaw)
        assertTrue(oem.isSelfWrite(70))
    }

    @Test
    fun writtenButUnacknowledged_keepsTheREQUESTEDMarker_soItsOwnEchoIsStillFiltered() {
        val oem = AndroidScreenBrightnessController(
            context,
            deviceMaxOverride = 255,
            rawWrite = { raw ->
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, raw)
            },
            rawRead = { null },
        )
        val result = oem.write(140)
        assertEquals(WriteStatus.WRITTEN_UNACKNOWLEDGED, result.status)
        assertEquals(140, result.requestedDomain)
        assertNull(result.acknowledgedRaw)
        assertNull(result.acknowledgedDomain)
        assertTrue(oem.isSelfWrite(140), "the requested raw stays the marker when read-back fails")
    }

    @Test
    fun unexpectedThrowable_isRethrown_andLeavesNoPoisonedMarker() {
        var explode = false
        val oem = AndroidScreenBrightnessController(
            context,
            deviceMaxOverride = 255,
            rawWrite = { raw ->
                if (explode) throw IllegalStateException("provider")
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, raw)
            },
        )
        oem.write(70)
        explode = true
        assertFailsWith<IllegalStateException> { oem.write(200) }
        assertFalse(oem.isSelfWrite(4242), "selfWriteInProgress must not survive the rethrow")
        assertTrue(oem.isSelfWrite(70), "the previous marker must be restored")
    }

    @Test
    fun isManualMode_readsTheMode_andForceManualModeReportsSuccess() {
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
        )
        assertFalse(controller.isManualMode())
        assertTrue(controller.forceManualMode())
        assertTrue(controller.isManualMode())
    }
}
