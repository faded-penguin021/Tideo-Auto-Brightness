package com.tideo.autobrightness.app.state

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.storage.settingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * S12.5b acceptance: draft/preview → Apply model (G2-F1).
 * Edits mutate draft only; Apply commits to DataStore; Discard reverts to committed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DraftSettingsViewModelTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    // D-125: clear process-global preview holder to prevent leaks between tests.
    @Before fun clearPreview() = CurveSuggestionPreview.clear()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun setBaseline(settings: AabSettings) {
        runBlocking { app.settingsDataStore.updateData { settings } }
        idle()
    }

    private fun committed(): AabSettings = runBlocking { app.settingsDataStore.data.first() }

    /** Idle + poll until [predicate] holds on the live committed value (bounded ~1s). */
    private fun awaitCommitted(predicate: (AabSettings) -> Boolean): AabSettings {
        repeat(100) {
            idle()
            val v = committed()
            if (predicate(v)) return v
            Thread.sleep(10)
        }
        return committed()
    }

    /**
     * Idle + poll until [predicate] holds on the VM's own state (bounded ~1s). The store reaching a
     * value does NOT mean the VM's `committed` collector has seen it, so anything derived from it —
     * `dirty` above all — needs its own wait or the assertion races the collector.
     */
    private fun awaitVm(vm: DraftSettingsViewModel, predicate: (DraftSettingsViewModel) -> Boolean) {
        repeat(100) {
            idle()
            if (predicate(vm)) return
            Thread.sleep(10)
        }
    }

    private fun awaitVmOn(
        dispatcher: TestDispatcher,
        vm: DraftSettingsViewModel,
        predicate: (DraftSettingsViewModel) -> Boolean,
    ) {
        repeat(100) {
            dispatcher.scheduler.advanceUntilIdle()
            idle()
            if (predicate(vm)) return
            Thread.sleep(10)
        }
    }

    private fun seededVm(): DraftSettingsViewModel {
        val vm = DraftSettingsViewModel(app)
        // Wait for init collector to seed; gate on epoch 0→1 (not draft == committed which may be true early).
        repeat(100) {
            idle()
            if (vm.epoch.value >= 1) return vm
            Thread.sleep(10)
        }
        return vm
    }

    // --- DB-040: the Privileged Display device read-back, at the layer where the bugs lived ---

    private fun deviceSnapshot(nightLight: Boolean = false, inversion: Boolean = false) =
        DeviceDisplaySnapshot(
            nightLight = nightLight,
            temperatureK = null,
            daltonizer = com.tideo.autobrightness.platform.display.DaltonizerMode.OFF,
            inversion = inversion,
            alwaysOn = false,
            stayAwake = false,
            hdrForceSdr = false,
        )

    @Test
    fun readBack_isRefusedBeforeTheSeed_soDefaultsNeverReplaceTheProfile() {
        // DB-040: a snapshot routinely beats the DataStore seed; merging into defaults loses either
        // the read-back or the profile.
        setBaseline(AabSettings(minBrightness = 42, nightLightEnabled = false))
        // DB-052: viewModelScope is Main.immediate, so on the test thread the init collector could
        // run inline and seed before the merge — the state under test, held open deterministically.
        val main = StandardTestDispatcher()
        Dispatchers.setMain(main)
        try {
            val vm = DraftSettingsViewModel(app)
            assertEquals(0, vm.epoch.value, "the seed must still be pending; nothing else pins that")

            vm.mergeDeviceReadBack(deviceSnapshot(nightLight = true))
            assertFalse(
                vm.draft.value.nightLightEnabled,
                "merging into pre-seed defaults is what makes the seed race destructive",
            )

            awaitVmOn(main, vm) { it.epoch.value >= 1 }
            assertEquals(42, vm.draft.value.minBrightness, "the profile must survive the race")
            vm.mergeDeviceReadBack(deviceSnapshot(nightLight = true))
            assertTrue(vm.draft.value.nightLightEnabled, "the read-back resumes after the seed")
            assertEquals(42, vm.draft.value.minBrightness, "without discarding the profile")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun readBack_tracksRepeatedDeviceChanges() {
        setBaseline(AabSettings(nightLightEnabled = false))
        val vm = seededVm()

        vm.mergeDeviceReadBack(deviceSnapshot(nightLight = true))
        assertTrue(vm.draft.value.nightLightEnabled, "first read-back shows the device")

        vm.mergeDeviceReadBack(deviceSnapshot(nightLight = false))
        assertFalse(vm.draft.value.nightLightEnabled, "and so does the second (DB-039)")
    }

    @Test
    fun readBack_survivesABackgroundGlobalFieldWrite() {
        // The service/QS tile/context engine rewrite serviceEnabled, contextOverride, debugLevel and
        // panicSensitivity into the draft. Those are not user edits and must not stop tracking.
        setBaseline(AabSettings(nightLightEnabled = false, debugLevel = 0))
        val vm = seededVm()
        vm.mergeDeviceReadBack(deviceSnapshot(nightLight = true))

        setBaseline(committed().copy(debugLevel = 8)) // Live Debug moves a GLOBAL field
        idle()

        vm.mergeDeviceReadBack(deviceSnapshot(nightLight = false))
        assertFalse(vm.draft.value.nightLightEnabled, "a background write is not a user edit")
    }

    @Test
    fun readBack_refusesToClobberAUserEdit_thenResumesAfterDiscard() {
        setBaseline(AabSettings(nightLightEnabled = false, inversionEnabled = false))
        val vm = seededVm()
        vm.mergeDeviceReadBack(deviceSnapshot(nightLight = true))

        vm.edit { it.copy(inversionEnabled = true) } // the user touches a display field
        vm.mergeDeviceReadBack(deviceSnapshot(nightLight = false))
        assertTrue(vm.draft.value.nightLightEnabled, "an uncommitted edit must not be clobbered")

        vm.discard()
        vm.mergeDeviceReadBack(deviceSnapshot(nightLight = false))
        assertFalse(vm.draft.value.nightLightEnabled, "after Discard the read-back resumes")
    }

    @Test
    fun edit_marksDirty_thenDiscardReverts() {
        setBaseline(AabSettings(minBrightness = 10))
        val vm = seededVm()
        assertEquals(10, vm.draft.value.minBrightness)
        assertFalse(vm.dirty.value)

        vm.edit { it.copy(minBrightness = 42) }
        idle()
        assertEquals(42, vm.draft.value.minBrightness)
        assertTrue(vm.dirty.value, "editing the draft should mark it dirty")
        assertEquals(10, committed().minBrightness)

        vm.discard()
        idle()
        assertEquals(10, vm.draft.value.minBrightness, "discard reverts the draft to committed")
        assertFalse(vm.dirty.value)
    }

    @Test
    fun apply_commitsDraftToDataStore() {
        setBaseline(AabSettings(maxBrightness = 200))
        val vm = seededVm()
        vm.edit { it.copy(maxBrightness = 222) }
        idle()
        vm.apply()

        val result = awaitCommitted { it.maxBrightness == 222 }
        assertEquals(222, result.maxBrightness, "Apply commits the draft to the DataStore")
    }

    @Test
    fun apply_raisesMaxBrightToFitCurve_D169() {
        // D-169: raise MaxBright to curve minimum (253) instead of blocking save.
        val steep = AabSettings(
            form1A = 5.0, zone1End = 35, form2B = 20f, form2C = 10, zone2End = 3000, maxBrightness = 200,
        )
        setBaseline(steep)
        val vm = seededVm()
        vm.edit { it.copy(maxBrightness = 150) }
        idle()
        vm.apply(raiseMaxBrightForCurve = true)

        val result = awaitCommitted { it.maxBrightness == 253 }
        assertEquals(253, result.maxBrightness, "Apply raises MaxBright to the curve's Zone 2 End value")
        // D-164: draft snaps to committed so screen is not left dirty.
        assertEquals(253, vm.draft.value.maxBrightness, "the draft reflects the auto-raised value")
    }

    @Test
    fun apply_curveScreen_doesNotRaiseMaxBright_D169() {
        // D-169: plain apply() (Curve screen) must NOT touch MaxBright; Tasker only raises on Misc save.
        val steep = AabSettings(
            form1A = 5.0, zone1End = 35, form2B = 20f, form2C = 10, zone2End = 3000, maxBrightness = 200,
        )
        setBaseline(steep)
        val vm = seededVm()
        vm.edit { it.copy(maxBrightness = 150) }
        idle()
        vm.apply()

        val result = awaitCommitted { it.maxBrightness == 150 }
        assertEquals(150, result.maxBrightness, "the Curve/other-screen Apply leaves MaxBright untouched")
    }

    @Test
    fun apply_clampsOutOfRangeValues() {
        // D-085: clamp out-of-range values on Apply instead of persisting unsafe values.
        setBaseline(AabSettings(maxBrightness = 200))
        val vm = seededVm()
        vm.edit { it.copy(maxBrightness = 999) }
        idle()
        vm.apply()

        val result = awaitCommitted { it.maxBrightness == 255 }
        assertEquals(255, result.maxBrightness, "Apply clamps out-of-range values (validate)")
    }

    @Test
    fun apply_snapsDraftToValidatedCommit_soDirtyClears_D164() {
        // D-164: apply() is a fixed point — draft snaps to validated copy, dirty converges to false.
        setBaseline(AabSettings(minWaitMs = 10, maxWaitMs = 50))
        val vm = seededVm()
        val epochAtSeed = vm.epoch.value
        vm.edit { it.copy(minWaitMs = 99, maxWaitMs = 2) }
        idle()
        vm.apply()

        val result = awaitCommitted { it.minWaitMs == 99 }
        assertEquals(99, result.maxWaitMs, "validate() coerces maxWaitMs up to minWaitMs on commit")
        assertEquals(99, vm.draft.value.maxWaitMs, "the draft snaps to the validated copy Apply committed")
        assertTrue(vm.epoch.value > epochAtSeed, "Apply bumps the epoch so seed-once fields rebind")
        idle()
        assertFalse(vm.dirty.value, "Apply must converge: draft == committed after the snap")
    }

    @Test
    fun apply_preservesServiceEnabledFromCommitted() {
        // serviceEnabled is runtime/identity; Apply must not flip the master switch.
        setBaseline(AabSettings(serviceEnabled = false, offset = 0))
        val vm = seededVm()
        vm.edit { it.copy(offset = 7) }
        idle()
        vm.apply()

        val result = awaitCommitted { it.offset == 7 }
        assertEquals(7, result.offset)
        assertFalse(result.serviceEnabled, "Apply preserves the committed serviceEnabled flag")
    }

    @Test
    fun initialSeed_appliesPendingCurveSuggestionPreview_D125() {
        // D-125: preview applied to draft during initial seed; persists only on Apply.
        CurveSuggestionPreview.clear()
        setBaseline(AabSettings(maxBrightness = 200, zone1End = 35, form2C = 18))
        CurveSuggestionPreview.request { it.copy(zone1End = 77, form2C = 5) }

        val vm = seededVm()

        assertEquals(77, vm.draft.value.zone1End, "the preview transform seeds the draft")
        assertEquals(5, vm.draft.value.form2C)
        assertTrue(vm.dirty.value, "a previewed suggestion makes the draft dirty (Apply/Discard + brackets)")
        assertEquals(35, committed().zone1End, "preview does not persist (preview, not commit)")
    }

    @Test
    fun initialSeed_consumesPreviewOnce_soOtherScreensAreUnaffected_D125() {
        // consume() is one-shot; second screen's VM gets plain committed values (no leak).
        CurveSuggestionPreview.clear()
        setBaseline(AabSettings(zone1End = 35))
        CurveSuggestionPreview.request { it.copy(zone1End = 77) }

        val first = seededVm()
        assertEquals(77, first.draft.value.zone1End, "first VM to seed consumes the preview")

        val second = seededVm()
        assertEquals(35, second.draft.value.zone1End, "preview is one-shot — the next VM seeds plainly")
        assertFalse(second.dirty.value)
    }

    // DB-008: _SaveButtonDimming A9-A12 (issue #110) — clamp setpoint and reflect in field.

    @Test
    fun apply_clampsDimmingStrengthSetpoint_andSnapsTheDraftToIt() {
        setBaseline(AabSettings(dimmingStrength = 25))
        val vm = seededVm()

        vm.edit { it.copy(dimmingStrength = 100) }
        idle()
        assertEquals(100, vm.draft.value.dimmingStrength, "the draft holds what was typed until Apply")

        vm.apply()
        val committed = awaitCommitted { it.dimmingStrength == 65 }

        assertEquals(65, committed.dimmingStrength, "the persisted setpoint must be the clamped value")
        assertEquals(
            65,
            vm.draft.value.dimmingStrength,
            "the field must show 65 after Apply — showing 100 while 65 applies is issue #110",
        )
        awaitVm(vm) { !it.dirty.value }
        assertFalse(vm.dirty.value, "Apply must be a fixed point: draft == committed afterwards")
    }

    @Test
    fun apply_announcesTheClamp_onlyWhenTheValueActuallyMoved() {
        setBaseline(AabSettings(dimmingStrength = 25))
        val vm = seededVm()
        val announced = mutableListOf<Int>()
        val collector = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
            .launch { vm.dimmingStrengthClamped.collect { announced += it } }

        // Exactly at cap: nothing corrected, so nothing announced (avoid false positives).
        vm.edit { it.copy(dimmingStrength = 65) }
        idle()
        vm.apply()
        awaitCommitted { it.dimmingStrength == 65 }
        assertTrue(announced.isEmpty(), "a setpoint of exactly 65 is not a clamp: $announced")

        // Above cap: corrected and reported.
        vm.edit { it.copy(dimmingStrength = 90) }
        idle()
        vm.apply()
        awaitCommitted { it.dimmingStrength == 65 }
        idle()
        assertEquals(listOf(65), announced, "the clamp must be announced with the persisted value")
        collector.cancel()
    }

    @Test
    fun apply_leavesAStrengthBelowTheCapAlone() {
        setBaseline(AabSettings(dimmingStrength = 25))
        val vm = seededVm()

        vm.edit { it.copy(dimmingStrength = 64) }
        idle()
        vm.apply()

        assertEquals(64, awaitCommitted { it.dimmingStrength == 64 }.dimmingStrength)
        assertEquals(64, vm.draft.value.dimmingStrength)
    }
}
