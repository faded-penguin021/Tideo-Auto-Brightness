package com.tideo.autobrightness.app.state

import android.Manifest
import android.app.Application
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.tideo.autobrightness.platform.display.DaltonizerMode
import com.tideo.autobrightness.platform.privilege.Tier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * DisplayTogglesViewModel glue (D-149, Segment 2) through the REAL AndroidPrivilegeManager +
 * AndroidSecureDisplayController under Robolectric: the tier gate surfaces write failures without
 * writing, an elevated write round-trips through the read-back, and refresh() picks up changes made
 * outside the app (system Settings / adb).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DisplayTogglesViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var app: Application

    @Before
    fun setUp() {
        // Unconfined Main + IO so viewModelScope work runs eagerly and assertions are synchronous.
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() = DisplayTogglesViewModel(app, io = dispatcher)

    private fun grantElevated() {
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
    }

    @Test
    fun writeBelowElevated_surfacesFailure_andWritesNothing() {
        val vm = vm()
        assertTrue(vm.state.value.tier < Tier.ELEVATED)

        vm.setNightLight(true)

        assertTrue(vm.state.value.writeFailed, "a tier-gated write must surface as writeFailed")
        // Read-back kept the truth: nothing was written.
        assertFalse(vm.state.value.nightLight)
        assertEquals(
            -999,
            Settings.Secure.getInt(app.contentResolver, "night_display_activated", -999),
        )
    }

    @Test
    fun writeAtElevated_appliesAndReadsBack() {
        grantElevated()
        val vm = vm()
        assertEquals(Tier.ELEVATED, vm.state.value.tier)

        vm.setNightLight(true)
        assertTrue(vm.state.value.nightLight)
        assertFalse(vm.state.value.writeFailed)

        vm.setDaltonizer(DaltonizerMode.GRAYSCALE)
        assertEquals(DaltonizerMode.GRAYSCALE, vm.state.value.daltonizer)

        // A subsequent successful write clears a stale failure flag by construction (readBack copy).
        vm.setInversion(true)
        assertTrue(vm.state.value.inversion)
        assertFalse(vm.state.value.writeFailed)
    }

    @Test
    fun refresh_clearsStaleWriteFailureBanner() {
        val vm = vm()
        vm.setNightLight(true) // below ELEVATED → fails and raises the banner
        assertTrue(vm.state.value.writeFailed)

        vm.refresh() // leaving + returning re-reads the truth; "the last change failed" is stale news
        assertFalse(vm.state.value.writeFailed)
    }

    @Test
    fun refresh_picksUpExternalChangesAndAGrant() {
        val vm = vm()
        assertTrue(vm.state.value.tier < Tier.ELEVATED)
        assertFalse(vm.state.value.nightLight)

        // Simulate an adb grant + a change made in the system Settings app while backgrounded.
        grantElevated()
        Settings.Secure.putInt(app.contentResolver, "night_display_activated", 1)

        vm.refresh()

        assertEquals(Tier.ELEVATED, vm.state.value.tier, "refresh must re-probe the tier")
        assertTrue(vm.state.value.nightLight, "refresh must re-read the device state")
    }
}
