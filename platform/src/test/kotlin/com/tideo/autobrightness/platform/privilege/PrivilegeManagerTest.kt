package com.tideo.autobrightness.platform.privilege

import android.Manifest
import android.content.Context
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.robolectric.Shadows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class PrivilegeManagerTest {
    private lateinit var context: Context
    private lateinit var manager: AndroidPrivilegeManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = AndroidPrivilegeManager(context)
    }

    @Test
    fun initialTier_isNotElevatedInRobolectric() {
        // WRITE_SECURE_SETTINGS is never granted in the Robolectric sandbox.
        assertTrue(manager.currentTier() < Tier.ELEVATED)
    }

    @Test
    fun tierFlow_isNotNull() {
        assertNotNull(manager.tierFlow())
    }

    @Test
    fun tierFlow_initialValueMatchesCurrentTier() {
        assertTrue(manager.tierFlow().value == manager.currentTier())
    }

    @Test
    fun refresh_doesNotThrow() {
        manager.refresh()
    }

    @Test
    fun adbGrantInstruction_containsPackageNameAndPermission() {
        val instruction = manager.adbGrantInstruction()
        assertTrue(instruction.contains(context.packageName))
        assertTrue(instruction.contains("WRITE_SECURE_SETTINGS"))
    }

    @Test
    fun dumpGrantInstruction_containsPackageNameAndDumpPermission() {
        // D-130: the no-Location SSID `dumpsys wifi` path is enabled by an ADB `pm grant` of DUMP.
        val instruction = manager.dumpGrantInstruction()
        assertTrue(instruction.contains(context.packageName))
        assertTrue(instruction.contains("android.permission.DUMP"))
        assertTrue(instruction.contains("pm grant"))
    }

    @Test
    fun grantedWriteSecureSettings_detectsElevated() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        manager.refresh()
        assertTrue(manager.currentTier() == Tier.ELEVATED)
        assertTrue(manager.tierFlow().value == Tier.ELEVATED)
    }

    @Test
    fun shizukuAvailability_notInstalled_whenPackageAbsent() {
        // No Shizuku manager app installed in the sandbox, and no live binder → NOT_INSTALLED.
        assertEquals(ShizukuAvailability.NOT_INSTALLED, manager.shizukuAvailability())
    }

    @Test
    fun shizukuAvailability_installedNotRunning_whenPackagePresentButBinderDead() {
        // S12.9b G2R-F91: pingBinder() can't tell "not installed" from "installed but not running".
        // With the manager app installed but no live binder (Robolectric), the state is the latter, so
        // the UI can prompt "start Shizuku" instead of hiding the path.
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        Shadows.shadowOf(app.packageManager).installPackage(
            PackageInfo().apply { packageName = ShizukuGrantGateway.SHIZUKU_PACKAGE },
        )
        assertEquals(ShizukuAvailability.INSTALLED_NOT_RUNNING, manager.shizukuAvailability())
    }

    @Test
    fun adbGrantInstruction_isAlwaysOffered_regardlessOfShizuku() {
        // The ADB channel is the no-companion-app path and must always be available.
        assertTrue(manager.adbGrantInstruction().isNotBlank())
        assertEquals(ShizukuAvailability.NOT_INSTALLED, manager.shizukuAvailability())
        assertTrue(manager.adbGrantInstruction().contains("WRITE_SECURE_SETTINGS"))
    }

    @Test
    fun writeSettingsIntent_targetsManageWriteSettingsForThisPackage() {
        val intent = manager.writeSettingsIntent()
        assertTrue(intent.action == android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS)
        assertTrue(intent.data.toString().contains(context.packageName))
    }
}
