package com.tideo.autobrightness.platform.display

import android.Manifest
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.tideo.autobrightness.platform.privilege.AndroidPrivilegeManager
import com.tideo.autobrightness.platform.privilege.Tier
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class SecureDisplayControllerTest {
    private lateinit var context: Context
    private lateinit var privilegeManager: AndroidPrivilegeManager
    private lateinit var controller: AndroidSecureDisplayController

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        privilegeManager = AndroidPrivilegeManager(context)
        controller = AndroidSecureDisplayController(
            context, privilegeManager,
            nightLightAvailable = true,
            alwaysOnDisplayAvailable = true,
        )
    }

    private fun grantElevated() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        privilegeManager.refresh()
    }

    private fun secureInt(key: String) = Settings.Secure.getInt(context.contentResolver, key, -999)
    private fun globalInt(key: String) = Settings.Global.getInt(context.contentResolver, key, -999)


    @Test
    fun writes_failWhenNotElevated_andWriteNothing() {
        assertTrue(privilegeManager.currentTier() < Tier.ELEVATED)
        val results = listOf(
            controller.setNightLight(true),
            controller.setNightLightTemperature(3000),
            controller.setDaltonizer(DaltonizerMode.GRAYSCALE),
            controller.setInversion(true),
            controller.setAlwaysOnDisplay(true),
            controller.setStayAwakePlugged(true),
        )
        results.forEach { result ->
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is SecurityException)
        }
        assertEquals(-999, secureInt("night_display_activated"))
        assertEquals(-999, secureInt("accessibility_display_daltonizer_enabled"))
        assertEquals(-999, globalInt(Settings.Global.STAY_ON_WHILE_PLUGGED_IN))
    }

    @Test
    fun unsupportedWrites_stillRejectCallersBelowElevated() {
        val unavailable = AndroidSecureDisplayController(
            context, privilegeManager,
            nightLightAvailable = false,
            alwaysOnDisplayAvailable = false,
        )

        listOf(
            unavailable.setNightLight(true),
            unavailable.setNightLightTemperature(2_700),
            unavailable.setAlwaysOnDisplay(true),
        ).forEach { result ->
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is SecurityException)
        }
    }


    @Test
    fun reads_workWithoutPrivilege_defaultingToOff() {
        assertFalse(controller.readNightLight())
        assertNull(controller.readNightLightTemperature())
        assertEquals(NightLightAutoMode.MANUAL, controller.readNightLightAutoMode())
        assertEquals(DaltonizerMode.OFF, controller.readDaltonizer())
        assertFalse(controller.readInversion())
        assertFalse(controller.readAlwaysOnDisplay())
        assertFalse(controller.readStayAwakePlugged())
    }


    @Test
    fun nightLight_roundTrips() {
        grantElevated()
        assertTrue(controller.setNightLight(true).isSuccess)
        assertEquals(1, secureInt("night_display_activated"))
        assertTrue(controller.readNightLight())

        assertTrue(controller.setNightLight(false).isSuccess)
        assertEquals(0, secureInt("night_display_activated"))
        assertFalse(controller.readNightLight())
    }

    @Test
    fun nightLightTemperature_roundTrips_andClampsToSanityBand() {
        grantElevated()
        assertTrue(controller.setNightLightTemperature(3400).isSuccess)
        assertEquals(3400, controller.readNightLightTemperature())

        assertTrue(controller.setNightLightTemperature(50).isSuccess)
        assertEquals(1_000, controller.readNightLightTemperature())

        assertTrue(controller.setNightLightTemperature(99_999).isSuccess)
        assertEquals(10_000, controller.readNightLightTemperature())
    }

    @Test
    fun nightLight_unavailable_isSuccessfulNoOp_forActivationAndTemperature() {
        grantElevated()
        val unavailable = AndroidSecureDisplayController(
            context, privilegeManager,
            nightLightAvailable = false,
            alwaysOnDisplayAvailable = true,
        )

        assertTrue(unavailable.setNightLight(true).isSuccess)
        assertTrue(unavailable.setNightLightTemperature(2_700).isSuccess)
        assertFalse(unavailable.readNightLight())
        assertNull(unavailable.readNightLightTemperature())
        assertEquals(-999, secureInt("night_display_activated"))
        assertEquals(-999, secureInt("night_display_color_temperature"))
    }

    @Test
    fun nightLightAutoMode_mapsKnownValues() {
        grantElevated()
        Settings.Secure.putInt(context.contentResolver, "night_display_auto_mode", 1)
        assertEquals(NightLightAutoMode.CUSTOM_SCHEDULE, controller.readNightLightAutoMode())
        Settings.Secure.putInt(context.contentResolver, "night_display_auto_mode", 2)
        assertEquals(NightLightAutoMode.TWILIGHT, controller.readNightLightAutoMode())
        Settings.Secure.putInt(context.contentResolver, "night_display_auto_mode", 77)
        assertEquals(NightLightAutoMode.MANUAL, controller.readNightLightAutoMode())
    }


    @Test
    fun daltonizer_grayscale_writesValueAndEnabled() {
        grantElevated()
        assertTrue(controller.setDaltonizer(DaltonizerMode.GRAYSCALE).isSuccess)
        assertEquals(0, secureInt("accessibility_display_daltonizer"))
        assertEquals(1, secureInt("accessibility_display_daltonizer_enabled"))
        assertEquals(DaltonizerMode.GRAYSCALE, controller.readDaltonizer())
    }

    @Test
    fun daltonizer_correctionModes_roundTrip() {
        grantElevated()
        listOf(
            DaltonizerMode.PROTANOMALY to 11,
            DaltonizerMode.DEUTERANOMALY to 12,
            DaltonizerMode.TRITANOMALY to 13,
        ).forEach { (mode, raw) ->
            assertTrue(controller.setDaltonizer(mode).isSuccess)
            assertEquals(raw, secureInt("accessibility_display_daltonizer"))
            assertEquals(mode, controller.readDaltonizer())
        }
    }

    @Test
    fun daltonizer_off_disablesButPreservesModeValue() {
        grantElevated()
        controller.setDaltonizer(DaltonizerMode.DEUTERANOMALY)
        assertTrue(controller.setDaltonizer(DaltonizerMode.OFF).isSuccess)
        assertEquals(0, secureInt("accessibility_display_daltonizer_enabled"))
        // Mode value survives the disable (system Settings behavior).
        assertEquals(12, secureInt("accessibility_display_daltonizer"))
        assertEquals(DaltonizerMode.OFF, controller.readDaltonizer())
    }

    @Test
    fun daltonizer_enabledWithUnrecognizedValue_readsAsOff() {
        grantElevated()
        Settings.Secure.putInt(context.contentResolver, "accessibility_display_daltonizer", 42)
        Settings.Secure.putInt(context.contentResolver, "accessibility_display_daltonizer_enabled", 1)
        assertEquals(DaltonizerMode.OFF, controller.readDaltonizer())
    }


    @Test
    fun inversion_alwaysOn_stayAwake_roundTrip() {
        grantElevated()
        assertTrue(controller.setInversion(true).isSuccess)
        assertTrue(controller.readInversion())
        assertEquals(1, secureInt("accessibility_display_inversion_enabled"))

        assertTrue(controller.setAlwaysOnDisplay(true).isSuccess)
        assertTrue(controller.readAlwaysOnDisplay())
        assertEquals(1, secureInt("doze_always_on"))

        assertTrue(controller.setStayAwakePlugged(true).isSuccess)
        assertTrue(controller.readStayAwakePlugged())
        // AC | USB | WIRELESS
        assertEquals(7, globalInt(Settings.Global.STAY_ON_WHILE_PLUGGED_IN))

        assertTrue(controller.setStayAwakePlugged(false).isSuccess)
        assertFalse(controller.readStayAwakePlugged())
        assertEquals(0, globalInt(Settings.Global.STAY_ON_WHILE_PLUGGED_IN))
    }

    @Test
    fun alwaysOnDisplay_unavailable_isSuccessfulNoOp() {
        grantElevated()
        val unavailable = AndroidSecureDisplayController(
            context, privilegeManager,
            nightLightAvailable = true,
            alwaysOnDisplayAvailable = false,
        )

        assertTrue(unavailable.setAlwaysOnDisplay(true).isSuccess)
        assertFalse(unavailable.readAlwaysOnDisplay())
        assertEquals(-999, secureInt("doze_always_on"))
    }


    @Test
    fun hdr_unavailableBelowApi34_failsWithoutWriting() {
        grantElevated()
        val old = AndroidSecureDisplayController(context, privilegeManager, sdkInt = 33)
        assertFalse(old.hdrForceSdrAvailable)
        assertNull(old.readHdrForceSdr())
        val result = old.setHdrForceSdr(true)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is UnsupportedOperationException)
        assertEquals(-999, globalInt("are_user_disabled_hdr_formats_allowed"))
    }

    @Test
    fun hdr_disableFormats_roundTripsOnApi34() {
        grantElevated()
        val modern = AndroidSecureDisplayController(
            context, privilegeManager, sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
        )
        assertTrue(modern.hdrForceSdrAvailable)
        // DB-046: an absent row is unrepresentable, not canonical OFF; seed the state under test.
        Settings.Global.putInt(context.contentResolver, "are_user_disabled_hdr_formats_allowed", 1)
        Settings.Global.putString(context.contentResolver, "user_disabled_hdr_formats", "")
        assertEquals(false, modern.readHdrForceSdr())

        assertTrue(modern.setHdrForceSdr(true).isSuccess)
        assertEquals(0, globalInt("are_user_disabled_hdr_formats_allowed"))
        assertEquals(
            "1,2,3,4",
            Settings.Global.getString(context.contentResolver, "user_disabled_hdr_formats"),
        )
        assertEquals(true, modern.readHdrForceSdr())

        assertTrue(modern.setHdrForceSdr(false).isSuccess)
        assertEquals(1, globalInt("are_user_disabled_hdr_formats_allowed"))
        assertEquals("", Settings.Global.getString(context.contentResolver, "user_disabled_hdr_formats"))
        assertEquals(false, modern.readHdrForceSdr())
    }

    @Test
    fun hdr_read_preservesUnrepresentableRows_andAcceptsPermutedCompleteSet() {
        val modern = AndroidSecureDisplayController(
            context, privilegeManager, sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
        )
        Settings.Global.putInt(context.contentResolver, "are_user_disabled_hdr_formats_allowed", 0)

        Settings.Global.putString(context.contentResolver, "user_disabled_hdr_formats", "1,2")
        assertNull(modern.readHdrForceSdr())
        Settings.Global.putString(context.contentResolver, "user_disabled_hdr_formats", "garbage")
        assertNull(modern.readHdrForceSdr())
        Settings.Global.putString(context.contentResolver, "user_disabled_hdr_formats", "4, 2,1,3,3")
        assertEquals(true, modern.readHdrForceSdr())

        listOf(Int.MIN_VALUE, -1, 2).forEach { malformedFlag ->
            if (malformedFlag == Int.MIN_VALUE) {
                Settings.Global.putString(
                    context.contentResolver,
                    "are_user_disabled_hdr_formats_allowed",
                    null,
                )
            } else {
                Settings.Global.putInt(
                    context.contentResolver,
                    "are_user_disabled_hdr_formats_allowed",
                    malformedFlag,
                )
            }
            Settings.Global.putString(context.contentResolver, "user_disabled_hdr_formats", "")
            assertNull(modern.readHdrForceSdr())
            Settings.Global.putString(context.contentResolver, "user_disabled_hdr_formats", "1,2,3,4")
            assertNull(modern.readHdrForceSdr())
        }
    }

    @Test
    fun hdr_writeFailsWhenNotElevated_evenOnApi34() {
        val modern = AndroidSecureDisplayController(
            context, privilegeManager, sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
        )
        val result = modern.setHdrForceSdr(true)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }
}
