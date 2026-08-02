package com.tideo.autobrightness.app.state

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.storage.settingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * S12.5b acceptance: the draft/preview → Apply model (G2-F1). Edits mutate a draft only; Apply commits
 * draft → DataStore; Discard reverts the draft to the committed value. Drives the real DataStore-backed
 * VM under Robolectric, idling the main looper to let the seed/commit coroutines settle.
 */
@RunWith(RobolectricTestRunner::class)
class DraftSettingsViewModelTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    // CurveSuggestionPreview is a process-global holder (D-125); clear it so a pending preview from one
    // test can never leak into another's initial seed.
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

    private fun seededVm(): DraftSettingsViewModel {
        val vm = DraftSettingsViewModel(app)
        // Wait for the init collector to ACTUALLY seed the draft. The VM bumps `epoch` 0→1 on its
        // first committed emission, so gate on that — NOT on `draft == committed()`, which is already
        // true from construction when the committed baseline equals the AabSettings() defaults (e.g.
        // minBrightness = 10). Returning early there left `seeded` false, so the first emission fired
        // during a later idle() and clobbered the test's edit back to the committed value (flaky
        // edit_marksDirty_thenDiscardReverts).
        repeat(100) {
            idle()
            if (vm.epoch.value >= 1) return vm
            Thread.sleep(10)
        }
        return vm
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
        // The committed value is untouched until Apply (temporary preview).
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
        // D-169 (_SaveButtonMisc A5–A11): a steep curve whose value at Zone 2 End (~252) exceeds a low
        // MaxBright (150) leaves form3A < 0. Apply must RAISE MaxBright to the curve minimum (253) and
        // announce it, instead of blocking the save.
        val steep = AabSettings(
            form1A = 5.0, zone1End = 35, form2B = 20f, form2C = 10, zone2End = 3000, maxBrightness = 200,
        )
        setBaseline(steep)
        val vm = seededVm()
        vm.edit { it.copy(maxBrightness = 150) }
        idle()
        // The Misc screen opts in (Tasker _SaveButtonMisc); the Curve screen does not.
        vm.apply(raiseMaxBrightForCurve = true)

        val result = awaitCommitted { it.maxBrightness == 253 }
        assertEquals(253, result.maxBrightness, "Apply raises MaxBright to the curve's Zone 2 End value")
        // The draft snaps to the committed value (D-164 fixed-point) so the screen is not left dirty.
        assertEquals(253, vm.draft.value.maxBrightness, "the draft reflects the auto-raised value")
    }

    @Test
    fun apply_curveScreen_doesNotRaiseMaxBright_D169() {
        // D-169: a plain apply() (the Curve/other screens) must NOT touch MaxBright — Tasker only
        // auto-raises on the Misc scene save. The too-low value commits as-is; form3A just warns.
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
        // D-085 (S14): a parameter screen must never persist an unsafe value. An out-of-range draft
        // (maxBrightness 999) is clamped on Apply (→ 255) rather than written raw to the engine.
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
        // D-164 (audit finding C1): validate() rewrites cross-field pairs on commit — maxWaitMs is
        // coerced up to minWaitMs — and the Misc wait sliders (1..99 / 2..100) can produce
        // min=99/max=2 with only an ADVISORY banner. Apply used to commit (99,99) while the draft
        // kept (99,2): draft ≠ committed forever (perpetually dirty, a slider showing a value that
        // silently didn't persist — the G3-F3 class, cross-field edition, unfixable by aligning
        // ranges). apply() must be a FIXED POINT: the draft snaps to the exact validated copy it
        // commits, dirty converges to false, and the epoch bump rebinds seed-once fields.
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
        // serviceEnabled is a runtime/identity field — Apply must not flip the master switch.
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
        // D-125: a wizard "Preview graph" request is applied to the draft DURING the initial seed (the
        // same atomic epoch 0→1 that populates the seed-once fields), so the suggested values show in
        // the fields with the current values in [brackets], and nothing persists until Apply.
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
        // consume() is one-shot: a second screen's VM seeding after the preview was taken gets the
        // plain committed values (no leak of the curve preview to e.g. the Misc/Reactivity drafts).
        CurveSuggestionPreview.clear()
        setBaseline(AabSettings(zone1End = 35))
        CurveSuggestionPreview.request { it.copy(zone1End = 77) }

        val first = seededVm()
        assertEquals(77, first.draft.value.zone1End, "first VM to seed consumes the preview")

        val second = seededVm()
        assertEquals(35, second.draft.value.zone1End, "preview is one-shot — the next VM seeds plainly")
        assertFalse(second.dirty.value)
    }

    // ---- DB-008: _SaveButtonDimming A9-A12 (issue #110) ---------------------------------------
    // The screen used to show the strength the user typed while the runtime clamped the effect to 65.
    // Apply now corrects the SETPOINT and says so, so the number on screen is the number in effect.

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
        assertFalse(vm.dirty.value, "Apply must be a fixed point: draft == committed afterwards")
    }

    @Test
    fun apply_announcesTheClamp_onlyWhenTheValueActuallyMoved() {
        setBaseline(AabSettings(dimmingStrength = 25))
        val vm = seededVm()
        val announced = mutableListOf<Int>()
        val collector = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
            .launch { vm.dimmingStrengthClamped.collect { announced += it } }

        // Exactly at the cap: nothing is corrected, so nothing may be announced — announcing a
        // correction that did not happen is the same misinformation issue #110 is about. Upstream
        // agrees since A9 became `> 65.0000000001` (it previously fired at 65 as well).
        vm.edit { it.copy(dimmingStrength = 65) }
        idle()
        vm.apply()
        awaitCommitted { it.dimmingStrength == 65 }
        assertTrue(announced.isEmpty(), "a setpoint of exactly 65 is not a clamp: $announced")

        // Above the cap: corrected, and reported with the value that actually persisted.
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
