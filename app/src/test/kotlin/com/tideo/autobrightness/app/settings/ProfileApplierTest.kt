package com.tideo.autobrightness.app.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tideo.autobrightness.app.storage.contextBaselineDataStore
import com.tideo.autobrightness.app.storage.settingsDataStore
import com.tideo.autobrightness.app.storage.userProfilesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-157 U3: the VM-free [ProfileApplier] holds the `applyProfile` / `resumeContextAutomation` bodies
 * moved verbatim out of `SettingsViewModel` (which still passes UNMODIFIED — the equivalence check).
 * These pin the same profile-load semantics directly on the applier, the path the external receiver
 * shares.
 */
@RunWith(RobolectricTestRunner::class)
class ProfileApplierTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val applier = ProfileApplier(app, UserProfileStore(app.userProfilesDataStore))

    private fun seed(settings: AabSettings) = runBlocking { app.settingsDataStore.updateData { settings } }
    private fun committed(): AabSettings = runBlocking { app.settingsDataStore.data.first() }

    @Test
    fun applyProfile_appliesCurveAndLatchesLock_preservingGlobals() = runBlocking {
        // serviceEnabled=false so no reapply intent is emitted; the DataStore write is what we assert.
        seed(AabSettings(serviceEnabled = false, debugLevel = 5, detectOverrides = true, minBrightness = 3))

        applier.applyProfile("Battery Saver") // a built-in — falls back to DefaultProfiles.all when unseeded

        val r = committed()
        assertNotEquals(3, r.minBrightness, "the profile's curve params applied")
        assertTrue(r.contextOverride, "a manual load latches the context lock (G2R-F30)")
        assertEquals(5, r.debugLevel, "debugLevel is global — a profile load must not change it")
        assertTrue(r.detectOverrides, "detectOverrides is global — preserved across a profile load")
    }

    @Test
    fun applyProfile_unknownName_isNoOp() = runBlocking {
        val before = AabSettings(serviceEnabled = false, minBrightness = 42, contextOverride = false)
        seed(before)

        applier.applyProfile("no such profile")

        assertEquals(before, committed(), "an unknown profile name must leave settings untouched")
    }

    @Test
    fun resumeContextAutomation_clearsContextLock() = runBlocking {
        seed(AabSettings(serviceEnabled = false, contextOverride = true))

        applier.resumeContextAutomation()

        assertFalse(committed().contextOverride, "resume clears the manual context lock (G2R-F30)")
    }

    @Test
    fun applyProfile_clearsBaselineSnapshot_D170() = runBlocking {
        // A manual load makes the current settings authoritative: any pre-override baseline snapshot
        // (task626 _ContextResume) is stale and must not be resurrected by a later context revert.
        seed(AabSettings(serviceEnabled = false))
        val store = DataStoreContextBaselineStore(app.contextBaselineDataStore)
        store.save(AabSettings(minBrightness = 3))

        applier.applyProfile("Battery Saver")

        assertNull(store.snapshot(), "a manual profile load drops the pre-override snapshot (D-170)")
    }

    @Test
    fun resumeContextAutomation_clearsBaselineSnapshot_D170() = runBlocking {
        // Resume = the current settings become the baseline the NEXT override snapshots (task626
        // re-snapshot semantics); a residual snapshot from before the lock must be dropped.
        seed(AabSettings(serviceEnabled = false, contextOverride = true))
        val store = DataStoreContextBaselineStore(app.contextBaselineDataStore)
        store.save(AabSettings(minBrightness = 3))

        applier.resumeContextAutomation()

        assertNull(store.snapshot(), "resume drops the residual pre-lock snapshot (D-170)")
    }
}
