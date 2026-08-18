package com.tideo.autobrightness.app.state

import android.Manifest
import android.app.Application
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.platform.display.NightLightAutoMode
import com.tideo.autobrightness.platform.display.AndroidSecureDisplayController
import com.tideo.autobrightness.platform.display.SecureDisplayController
import com.tideo.autobrightness.platform.privilege.AndroidPrivilegeManager
import com.tideo.autobrightness.platform.privilege.Tier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * DisplayTogglesViewModel glue (D-149; reworked by D-152) through the REAL
 * AndroidPrivilegeManager + AndroidSecureDisplayController under Robolectric: [applyNow] (the
 * service-off direct write) is tier-gated and writes the profile fields to the device; refresh()
 * re-probes the tier and the device facts the screen still reads (Night Light auto-mode caveat).
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

    private fun vm(
        nightLightAvailable: Boolean = true,
        alwaysOnDisplayAvailable: Boolean = true,
        io: CoroutineDispatcher = dispatcher,
    ): DisplayTogglesViewModel {
        val privileges = AndroidPrivilegeManager(app)
        return DisplayTogglesViewModel(
            app,
            privilegeManager = privileges,
            display = AndroidSecureDisplayController(
                app, privileges,
                nightLightAvailable = nightLightAvailable,
                alwaysOnDisplayAvailable = alwaysOnDisplayAvailable,
            ),
            io = io,
        )
    }

    private fun grantElevated() {
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
    }

    @Test
    fun applyNowBelowElevated_surfacesFailure_andWritesNothing() {
        val vm = vm()
        assertTrue(vm.state.value.tier < Tier.ELEVATED)

        vm.applyNow(AabSettings(nightLightEnabled = true))

        assertTrue(vm.state.value.writeFailed, "a tier-gated write must surface as writeFailed")
        assertEquals(
            -999,
            Settings.Secure.getInt(app.contentResolver, "night_display_activated", -999),
        )
    }

    @Test
    fun applyNowAtElevated_writesTheProfileFields_butNeverANullTemperature() {
        grantElevated()
        val vm = vm()
        assertEquals(Tier.ELEVATED, vm.state.value.tier)

        vm.applyNow(
            AabSettings(
                nightLightEnabled = true,
                daltonizerMode = "GRAYSCALE",
                inversionEnabled = true,
                alwaysOnDisplayEnabled = true,
            ),
        )

        assertFalse(vm.state.value.writeFailed)
        val resolver = app.contentResolver
        assertEquals(1, Settings.Secure.getInt(resolver, "night_display_activated", -999))
        assertEquals(1, Settings.Secure.getInt(resolver, "accessibility_display_daltonizer_enabled", -999))
        assertEquals(0, Settings.Secure.getInt(resolver, "accessibility_display_daltonizer", -999))
        assertEquals(1, Settings.Secure.getInt(resolver, "accessibility_display_inversion_enabled", -999))
        assertEquals(1, Settings.Secure.getInt(resolver, "doze_always_on", -999))
        assertEquals(true, assertNotNull(vm.deviceSnapshot.value).nightLight)
        // null temperature = "device default": the key must stay unset.
        assertEquals(-999, Settings.Secure.getInt(resolver, "night_display_color_temperature", -999))
    }

    @Test
    fun applyNow_writesOnlyTheFieldsTheDeviceDoesNotAlreadyAgreeWith() {
        grantElevated()
        val resolver = app.contentResolver
        Settings.Secure.putInt(resolver, "accessibility_display_inversion_enabled", 1)
        val vm = vm()

        vm.applyNow(AabSettings(nightLightEnabled = true, inversionEnabled = true))

        assertEquals(1, Settings.Secure.getInt(resolver, "night_display_activated", -999))
        assertEquals(
            -999,
            Settings.Secure.getInt(resolver, "accessibility_display_daltonizer_enabled", -999),
            "DB-068: a field the device already agrees with must not be rewritten",
        )
        assertFalse(vm.state.value.writeFailed)
    }

    @Test
    fun applyNow_withAnUnrepresentableDeviceMode_preservesItWhenTheUserPickedNothing() {
        grantElevated()
        val resolver = app.contentResolver
        Settings.Secure.putInt(resolver, "accessibility_display_daltonizer", 42)
        Settings.Secure.putInt(resolver, "accessibility_display_daltonizer_enabled", 1)
        val vm = vm()
        assertTrue(vm.state.value.daltonizerPreferenceCustom)
        assertNull(assertNotNull(vm.deviceSnapshot.value).daltonizer)

        vm.applyNow(AabSettings(nightLightEnabled = true), committed = AabSettings())

        assertEquals(42, Settings.Secure.getInt(resolver, "accessibility_display_daltonizer", -999))
        assertEquals(1, Settings.Secure.getInt(resolver, "accessibility_display_daltonizer_enabled", -999))
        assertEquals(1, Settings.Secure.getInt(resolver, "night_display_activated", -999))
    }

    @Test
    fun applyNow_withAnUnrepresentableDeviceMode_stillWritesTheUsersOwnPick() {
        grantElevated()
        val resolver = app.contentResolver
        Settings.Secure.putInt(resolver, "accessibility_display_daltonizer", 42)
        Settings.Secure.putInt(resolver, "accessibility_display_daltonizer_enabled", 1)
        val vm = vm()

        vm.applyNow(AabSettings(daltonizerMode = "GRAYSCALE"), committed = AabSettings())

        assertEquals(0, Settings.Secure.getInt(resolver, "accessibility_display_daltonizer", -999))
        assertEquals(1, Settings.Secure.getInt(resolver, "accessibility_display_daltonizer_enabled", -999))
        assertFalse(vm.state.value.daltonizerPreferenceCustom)
    }

    // DB-048: the read-back rollback DB-047 fixed had a second half. With the service RUNNING the
    // screen skips applyNow (the coordinator writes instead), so nothing invalidated the pre-Apply
    // snapshot and the merge gate — reopened by Apply making draft == committed again — replayed it
    // over the just-applied draft. Both halves of the D-152 split go through applyDraft now.
    @Test
    fun applyDraft_withTheServiceRunning_invalidatesTheStaleSnapshotAndLeavesTheWriteToTheCoordinator() {
        grantElevated()
        val vm = vm()
        assertEquals(false, assertNotNull(vm.deviceSnapshot.value).nightLight)

        vm.applyDraft(AabSettings(nightLightEnabled = true), AabSettings(serviceEnabled = true))

        assertNull(
            vm.deviceSnapshot.value,
            "the pre-Apply OFF snapshot must stop being mergeable before the draft epoch advances",
        )
        assertEquals(
            -999,
            Settings.Secure.getInt(app.contentResolver, "night_display_activated", -999),
            "with the service running the coordinator owns the write, not this VM",
        )
    }

    @Test
    fun applyDraft_withTheServiceStopped_stillWritesTheDeviceDirectly() {
        // The other half of the same branch: D-152's direct path must not be lost to the fix above.
        grantElevated()
        val vm = vm()

        vm.applyDraft(AabSettings(nightLightEnabled = true), AabSettings(serviceEnabled = false))

        assertEquals(1, Settings.Secure.getInt(app.contentResolver, "night_display_activated", -999))
        assertEquals(true, assertNotNull(vm.deviceSnapshot.value).nightLight)
    }

    @Test
    fun applyDraft_withTheServiceRunning_suppressesAnOlderRefreshStillInFlight() {
        grantElevated()
        val controlledIo = StandardTestDispatcher(dispatcher.scheduler)
        val vm = vm(io = controlledIo)
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(vm.deviceSnapshot.value)

        vm.refresh() // reads the pre-Apply device, still pending on the controlled dispatcher
        vm.applyDraft(AabSettings(nightLightEnabled = true), AabSettings(serviceEnabled = true))
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(
            vm.deviceSnapshot.value,
            "a refresh scheduled before Apply must not republish the state Apply invalidated",
        )
    }

    @Test
    fun applyNow_invalidatesTheOldSnapshotBeforeItsAsyncWriteCanRun() {
        grantElevated()
        val controlledIo = StandardTestDispatcher(dispatcher.scheduler)
        val vm = vm(io = controlledIo)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, assertNotNull(vm.deviceSnapshot.value).nightLight)

        vm.applyNow(AabSettings(nightLightEnabled = true))

        assertNull(vm.deviceSnapshot.value, "the old OFF snapshot must be invalid before Apply advances the draft epoch")
        assertEquals(
            -999,
            Settings.Secure.getInt(app.contentResolver, "night_display_activated", -999),
            "the controlled dispatcher must keep the device write pending",
        )

        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(true, assertNotNull(vm.deviceSnapshot.value).nightLight)
    }

    @Test
    fun applyNow_suppressesAnOlderRefreshCompletionBeforeThePendingWrite() {
        grantElevated()
        val controlledIo = StandardTestDispatcher(dispatcher.scheduler)
        val privileges = AndroidPrivilegeManager(app)
        val realDisplay = AndroidSecureDisplayController(
            app,
            privileges,
            nightLightAvailable = true,
            alwaysOnDisplayAvailable = true,
        )
        var onAutoModeRead: (() -> Unit)? = null
        var beforeNightLightWrite: (() -> Unit)? = null
        val display = object : SecureDisplayController by realDisplay {
            override fun readNightLightAutoMode(): NightLightAutoMode = realDisplay.readNightLightAutoMode().also {
                onAutoModeRead?.invoke()
            }

            override fun setNightLight(on: Boolean): Result<Unit> {
                beforeNightLightWrite?.invoke()
                return realDisplay.setNightLight(on)
            }
        }
        val vm = DisplayTogglesViewModel(app, privileges, display, controlledIo)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, assertNotNull(vm.deviceSnapshot.value).nightLight)

        onAutoModeRead = {
            onAutoModeRead = null
            vm.applyNow(AabSettings(nightLightEnabled = true))
        }
        beforeNightLightWrite = {
            assertNull(
                vm.deviceSnapshot.value,
                "the superseded refresh must not republish OFF after Apply invalidates it",
            )
        }

        vm.refresh()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, assertNotNull(vm.deviceSnapshot.value).nightLight)
    }

    @Test
    fun directApply_cannotBypassNightLightOrAodCapabilities() {
        grantElevated()
        val vm = vm(nightLightAvailable = false, alwaysOnDisplayAvailable = false)

        vm.applyNow(
            AabSettings(
                nightLightEnabled = true,
                nightLightTemperature = 2_700,
                alwaysOnDisplayEnabled = true,
            ),
        )

        assertFalse(vm.state.value.writeFailed)
        assertFalse(vm.state.value.nightLightAvailable)
        assertFalse(vm.state.value.alwaysOnDisplayAvailable)
        val snapshot = assertNotNull(vm.deviceSnapshot.value)
        assertNull(snapshot.nightLight)
        assertNull(snapshot.alwaysOn)
        assertEquals(-999, Settings.Secure.getInt(app.contentResolver, "night_display_activated", -999))
        assertEquals(-999, Settings.Secure.getInt(app.contentResolver, "night_display_color_temperature", -999))
        assertEquals(-999, Settings.Secure.getInt(app.contentResolver, "doze_always_on", -999))
    }

    @Test
    fun directApply_preservesAnUnrepresentablePartialHdrPreference() {
        grantElevated()
        Settings.Global.putInt(app.contentResolver, "are_user_disabled_hdr_formats_allowed", 0)
        Settings.Global.putString(app.contentResolver, "user_disabled_hdr_formats", "1,2")
        val vm = vm()
        assertNull(assertNotNull(vm.deviceSnapshot.value).hdrForceSdr)
        assertFalse(vm.state.value.hdrAvailable)
        assertTrue(vm.state.value.hdrPreferenceCustom)

        vm.applyNow(AabSettings(inversionEnabled = true, hdrForceSdrEnabled = false))

        assertEquals("1,2", Settings.Global.getString(app.contentResolver, "user_disabled_hdr_formats"))
        assertEquals(0, Settings.Global.getInt(app.contentResolver, "are_user_disabled_hdr_formats_allowed"))
        assertEquals(1, Settings.Secure.getInt(app.contentResolver, "accessibility_display_inversion_enabled"))
    }

    @Test
    fun directApply_rechecksHdrAfterAnExternalChange_andPreservesTheNewCustomRow() {
        grantElevated()
        Settings.Global.putInt(app.contentResolver, "are_user_disabled_hdr_formats_allowed", 1)
        Settings.Global.putString(app.contentResolver, "user_disabled_hdr_formats", "")
        val vm = vm()
        assertEquals(false, assertNotNull(vm.deviceSnapshot.value).hdrForceSdr)

        Settings.Global.putInt(app.contentResolver, "are_user_disabled_hdr_formats_allowed", 0)
        Settings.Global.putString(app.contentResolver, "user_disabled_hdr_formats", "1,2")
        vm.applyNow(AabSettings(inversionEnabled = true, hdrForceSdrEnabled = false))

        assertEquals("1,2", Settings.Global.getString(app.contentResolver, "user_disabled_hdr_formats"))
        assertEquals(0, Settings.Global.getInt(app.contentResolver, "are_user_disabled_hdr_formats_allowed"))
        assertNull(assertNotNull(vm.deviceSnapshot.value).hdrForceSdr)
        assertTrue(vm.state.value.hdrPreferenceCustom)
        assertFalse(vm.state.value.hdrAvailable)
    }

    @Test
    fun refresh_clearsStaleWriteFailureBanner() {
        val vm = vm()
        vm.applyNow(AabSettings(nightLightEnabled = true)) // below ELEVATED → fails, banner up
        assertTrue(vm.state.value.writeFailed)

        vm.refresh() // leaving + returning; "the last change failed" is stale news
        assertFalse(vm.state.value.writeFailed)
    }

    @Test
    fun deviceSnapshot_isNullBelowElevated_andReadsTheDeviceOnce() {
        // DB-034: reads need no grant, but below ELEVATED the toggles never compose.
        Settings.Secure.putInt(app.contentResolver, "accessibility_display_inversion_enabled", 1)
        val vm = vm()
        assertTrue(vm.state.value.tier < Tier.ELEVATED)
        assertNull(vm.deviceSnapshot.value)

        grantElevated()
        vm.refresh()

        val snapshot = assertNotNull(vm.deviceSnapshot.value)
        assertTrue(snapshot.inversion, "the snapshot must report what the device actually reads")
        assertEquals(false, snapshot.nightLight)
    }

    @Test
    fun deviceSnapshot_tracksAnExternalChange() {
        // The system quick-settings tile flipping Night Light while we were backgrounded.
        grantElevated()
        val vm = vm()
        assertEquals(false, assertNotNull(vm.deviceSnapshot.value).nightLight)

        Settings.Secure.putInt(app.contentResolver, "night_display_activated", 1)
        Settings.Secure.putInt(app.contentResolver, "night_display_color_temperature", 2700)
        vm.refresh()

        val snapshot = assertNotNull(vm.deviceSnapshot.value)
        assertEquals(true, snapshot.nightLight)
        assertEquals(2700, snapshot.temperatureK)
    }

    @Test
    fun refresh_picksUpAGrantAndTheNightLightAutoMode() {
        val vm = vm()
        assertTrue(vm.state.value.tier < Tier.ELEVATED)
        assertEquals(NightLightAutoMode.MANUAL, vm.state.value.nightLightAutoMode)

        // Simulate an adb grant + a schedule set in the system Settings app while backgrounded.
        grantElevated()
        Settings.Secure.putInt(app.contentResolver, "night_display_auto_mode", 2)

        vm.refresh()

        assertEquals(Tier.ELEVATED, vm.state.value.tier, "refresh must re-probe the tier")
        assertEquals(
            NightLightAutoMode.TWILIGHT,
            vm.state.value.nightLightAutoMode,
            "refresh must re-read the auto-mode caveat input",
        )
    }
}
