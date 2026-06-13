package com.tideo.autobrightness.platform.brightness

import android.Manifest
import android.content.Context
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
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class SecureDimmingControllerTest {
    private lateinit var context: Context
    private lateinit var privilegeManager: AndroidPrivilegeManager
    private lateinit var controller: AndroidSecureDimmingController

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        privilegeManager = AndroidPrivilegeManager(context)
        controller = AndroidSecureDimmingController(context, privilegeManager)
    }

    @Test
    fun setLevel_failsWhenNotElevated() {
        // In Robolectric, WRITE_SECURE_SETTINGS is never granted → tier < ELEVATED.
        assertTrue(privilegeManager.currentTier() < Tier.ELEVATED)
        val result = controller.setLevel(500)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun setActivated_failsWhenNotElevated() {
        assertTrue(privilegeManager.currentTier() < Tier.ELEVATED)
        val result = controller.setActivated(true)
        assertTrue(result.isFailure)
    }

    @Test
    fun setLevelAndActivated_writeSecureKeys_whenElevated() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        privilegeManager.refresh()

        assertTrue(controller.setLevel(500).isSuccess)
        assertTrue(controller.setActivated(true).isSuccess)
        assertEquals(
            500,
            Settings.Secure.getInt(context.contentResolver, "reduce_bright_colors_level", -1),
        )
        assertEquals(
            1,
            Settings.Secure.getInt(context.contentResolver, "reduce_bright_colors_activated", -1),
        )
    }

    @Test
    fun setLevel_clampsToValidRange_whenElevated() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        privilegeManager.refresh()

        assertTrue(controller.setLevel(5000).isSuccess)
        assertEquals(
            1000,
            Settings.Secure.getInt(context.contentResolver, "reduce_bright_colors_level", -1),
        )
    }
}
