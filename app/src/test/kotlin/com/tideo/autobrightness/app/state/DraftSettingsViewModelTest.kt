package com.tideo.autobrightness.app.state

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.storage.settingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
}
