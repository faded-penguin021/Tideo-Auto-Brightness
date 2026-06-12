package com.tideo.autobrightness.platform.privilege

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
}
