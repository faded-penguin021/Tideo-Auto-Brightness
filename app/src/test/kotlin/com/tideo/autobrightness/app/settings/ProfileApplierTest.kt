package com.tideo.autobrightness.app.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tideo.autobrightness.app.runtime.AmbientMonitoringService
import com.tideo.autobrightness.app.storage.contextBaselineDataStore
import com.tideo.autobrightness.app.storage.settingsDataStore
import com.tideo.autobrightness.app.storage.userProfilesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** D-157 U3: VM-free ProfileApplier holds `applyProfile`/`resumeContextAutomation` bodies from SettingsViewModel. */
@RunWith(RobolectricTestRunner::class)
class ProfileApplierTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val applier = ProfileApplier(app, UserProfileStore(app.userProfilesDataStore))

    private fun seed(settings: AabSettings) = runBlocking { app.settingsDataStore.updateData { settings } }
    private fun committed(): AabSettings = runBlocking { app.settingsDataStore.data.first() }

    @Test
    fun applyProfile_appliesCurveAndLatchesLock_preservingGlobals() = runBlocking {
        // No reapply intent without serviceEnabled; assert DataStore write
        seed(AabSettings(serviceEnabled = false, debugLevel = 5, detectOverrides = true, minBrightness = 3))

        applier.applyProfile("Battery Saver")

        val r = committed()
        assertNotEquals(3, r.minBrightness, "the profile's curve params applied")
        assertTrue(r.contextOverride, "manual load latches context lock (G2R-F30)")
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
        // Manual load makes current settings authoritative; drop pre-override baseline snapshot
        seed(AabSettings(serviceEnabled = false))
        val store = DataStoreContextBaselineStore(app.contextBaselineDataStore)
        store.save(AabSettings(minBrightness = 3))

        applier.applyProfile("Battery Saver")

        assertNull(store.snapshot(), "a manual profile load drops the pre-override snapshot (D-170)")
    }

    @Test
    fun applyProfile_recordsUserProfileName_DA018() = runBlocking {
        // DA-018: manual load sets %AAB_ProfileUser (target for later Resume/no-match)
        seed(AabSettings(serviceEnabled = false))
        val store = DataStoreContextBaselineStore(app.contextBaselineDataStore)

        applier.applyProfile("Battery Saver")

        assertEquals("Battery Saver", store.userProfileName(), "a manual load records %AAB_ProfileUser (DA-018)")
    }

    @Test
    fun resumeContextAutomation_routesToResumeContextAction_DA018() = runBlocking {
        // DA-018: Resume runs genuine re-evaluation (RESUME → Set Initial Brightness), not REAPPLY
        seed(AabSettings(serviceEnabled = true, contextOverride = true))

        applier.resumeContextAutomation()

        val intent = shadowOf(app).nextStartedService
        assertEquals(
            AmbientMonitoringService.ACTION_RESUME_CONTEXT,
            intent.action,
            "Resume must run a real context re-evaluation, not a republish-only REAPPLY",
        )
    }

    @Test
    fun resumeContextAutomation_leavesUserProfileNameIntact_DA018() = runBlocking {
        // Resume reverts to %AAB_ProfileUser; must not overwrite it
        seed(AabSettings(serviceEnabled = false, contextOverride = true))
        val store = DataStoreContextBaselineStore(app.contextBaselineDataStore)
        store.setUserProfileName("Outdoors")

        applier.resumeContextAutomation()

        assertEquals("Outdoors", store.userProfileName(), "Resume leaves %AAB_ProfileUser unchanged (DA-018)")
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
