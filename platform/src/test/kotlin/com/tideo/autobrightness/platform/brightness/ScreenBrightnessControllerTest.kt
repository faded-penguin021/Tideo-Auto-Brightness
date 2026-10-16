package com.tideo.autobrightness.platform.brightness

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ScreenBrightnessControllerTest {
    private lateinit var context: Context
    private lateinit var controller: AndroidScreenBrightnessController

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        controller = AndroidScreenBrightnessController(context)
    }

    @Test
    fun write_and_read_roundtrip() {
        controller.write(128)
        assertEquals(128, controller.read())
    }

    @Test
    fun write_boundary_min() {
        controller.write(0)
        assertEquals(0, controller.read())
    }

    @Test
    fun write_boundary_max() {
        controller.write(255)
        assertEquals(255, controller.read())
    }

    @Test
    fun forceManualMode_setsManualBrightnessMode() {
        controller.forceManualMode()
        val mode = Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            -1,
        )
        assertEquals(Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL, mode)
    }

    @Test
    fun restoreMode_restoresPreviousMode() {
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
        )
        controller.forceManualMode()
        controller.restoreMode()
        val mode = Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            -1,
        )
        assertEquals(Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC, mode)
    }

    @Test
    fun isSelfWrite_matchesLastWrite_repeatable() {
        controller.write(100)
        assertTrue(controller.isSelfWrite(100))
        // %LastAAB semantics: marker NOT consumed; delayed callbacks must still match.
        assertTrue(controller.isSelfWrite(100))
    }

    @Test
    fun isSelfWrite_tracksLatestWriteOnly() {
        controller.write(50)
        controller.write(60)
        assertFalse(controller.isSelfWrite(50))
        assertTrue(controller.isSelfWrite(60))
    }

    @Test
    fun isSelfWrite_unknownValue_returnsFalse() {
        assertFalse(controller.isSelfWrite(42))
    }

    @Test
    fun clearSelfWriteMarker_forgetsLastWrite() {
        controller.write(50)
        controller.clearSelfWriteMarker()
        assertFalse(controller.isSelfWrite(50))
    }

    @Test
    fun forceManualMode_isIdempotent_restoresOriginalMode() {
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
        )
        controller.forceManualMode()
        controller.forceManualMode() // Must not overwrite saved AUTOMATIC.
        controller.restoreMode()
        val mode = Settings.System.getInt(
            context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, -1,
        )
        assertEquals(Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC, mode)
    }

    @Test
    fun restoreMode_survivesProcessDeath_restoresAutomatic_D134() {
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
        )
        controller.forceManualMode()
        // Process death: new instance must know pre-service mode (fresh state, same prefs).
        val afterRestart = AndroidScreenBrightnessController(context)
        afterRestart.restoreMode()
        val mode = Settings.System.getInt(
            context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, -1,
        )
        assertEquals(Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC, mode)
    }

    @Test
    fun forceManualMode_afterProcessDeathMidManual_keepsPersistedAutomatic_D134() {
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
        )
        controller.forceManualMode() // saves AUTOMATIC, device now MANUAL
        // Process death: restarted instance must keep persisted AUTOMATIC, not re-save MANUAL.
        val afterRestart = AndroidScreenBrightnessController(context)
        afterRestart.forceManualMode()
        afterRestart.restoreMode()
        val mode = Settings.System.getInt(
            context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, -1,
        )
        assertEquals(Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC, mode)
    }

    @Test
    fun forceManualMode_freshStartWithNonManualMode_overwritesStalePersistedValue_D134() {
        // Stale persisted MANUAL from crash; user now in AUTOMATIC. Non-MANUAL current mode wins.
        context.getSharedPreferences(
            AndroidScreenBrightnessController.PREFS_NAME, Context.MODE_PRIVATE,
        ).edit().putInt(
            AndroidScreenBrightnessController.KEY_SAVED_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        ).commit()
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
        )
        controller.forceManualMode()
        controller.restoreMode()
        val mode = Settings.System.getInt(
            context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, -1,
        )
        assertEquals(Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC, mode)
    }

    @Test
    fun oemNormalization_roundTripIsIdentity_for1023Max() {
        val oem = AndroidScreenBrightnessController(context, settingsApiMaxOverride = 1023)
        for (domain in intArrayOf(0, 1, 99, 100, 128, 254, 255)) {
            oem.write(domain)
            assertEquals(domain, oem.read(), "round-trip failed for domain=$domain")
        }
    }

    @Test
    fun oemNormalization_writeScalesToDeviceRange() {
        val oem = AndroidScreenBrightnessController(context, settingsApiMaxOverride = 1023)
        oem.write(255)
        val raw = Settings.System.getInt(
            context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1,
        )
        assertEquals(1023, raw)
    }

    @Test
    fun write_clampsOutOfRangeDomainInput() {
        controller.write(300)
        assertEquals(255, controller.read())
        controller.write(-5)
        assertEquals(0, controller.read())
    }

    /** DC-002: an OEM that stores [store] of what we asked for; reads stay real. */
    private fun normalizing(store: (Int) -> Int, settingsApiMax: Int = 1023) =
        AndroidScreenBrightnessController(
            context,
            settingsApiMaxOverride = settingsApiMax,
            rawWrite = { raw ->
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, store(raw))
            },
        )

    @Test
    fun normalizedWrite_marksTheSTOREDValue_soTheEchoIsASelfWrite() {
        val oem = normalizing(store = { 3083 })
        val result = oem.write(255)
        assertEquals(WriteStatus.ACKNOWLEDGED, result.status)
        assertEquals(255, result.requestedDomain)
        assertEquals(1023, result.requestedSettingValue)
        assertEquals(3083, result.readBackSettingValue)
        assertEquals(255, result.acknowledgedDomain)
        assertEquals(1023, result.settingsApiMax)
        assertTrue(oem.isSelfWrite(3083))
        // The requested raw stays matchable: a provider that applies asynchronously echoes it later.
        assertTrue(oem.isSelfWrite(1023))
        assertFalse(oem.isSelfWrite(77))
    }

    @Test
    fun acknowledgedDomain_isTheStoredRawConverted_notTheRequestedDomain() {
        val oem = normalizing(store = { 512 })
        val result = oem.write(255)
        assertEquals(255, result.requestedDomain)
        assertEquals(128, result.acknowledgedDomain, "512/1023 → 128 in domain space")
    }

    @Test
    fun callbackDuringPutInt_isASelfWrite_selfWriteInProgress() {
        var seenMidWrite: Boolean? = null
        lateinit var oem: AndroidScreenBrightnessController
        oem = AndroidScreenBrightnessController(
            context,
            settingsApiMaxOverride = 255,
            rawWrite = { raw ->
                seenMidWrite = oem.isSelfWrite(9999)
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, raw)
            },
        )
        oem.write(100)
        assertEquals(true, seenMidWrite)
        assertFalse(oem.isSelfWrite(9999), "the in-progress flag must not survive the write")
    }

    @Test
    fun refusedWrite_restoresThePreviousMarker_andReportsREFUSED() {
        var refuse = false
        val oem = AndroidScreenBrightnessController(
            context,
            settingsApiMaxOverride = 255,
            rawWrite = { raw ->
                if (refuse) false
                else Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, raw)
            },
        )
        oem.write(70)
        refuse = true
        val result = oem.write(200)
        assertEquals(WriteStatus.REFUSED, result.status)
        assertNull(result.readBackSettingValue)
        assertNull(result.acknowledgedDomain)
        assertTrue(oem.isSelfWrite(70), "a refused write must leave the previous marker in place")
        assertFalse(oem.isSelfWrite(200))
    }

    @Test
    fun deniedWrite_reportsDENIED_andRestoresThePreviousMarker() {
        var deny = false
        val oem = AndroidScreenBrightnessController(
            context,
            settingsApiMaxOverride = 255,
            rawWrite = { raw ->
                if (deny) throw SecurityException("WRITE_SETTINGS revoked")
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, raw)
            },
        )
        oem.write(70)
        deny = true
        val result = oem.write(200)
        assertEquals(WriteStatus.DENIED, result.status)
        assertNull(result.readBackSettingValue)
        assertTrue(oem.isSelfWrite(70))
    }

    @Test
    fun writtenButUnacknowledged_keepsTheREQUESTEDMarker_soItsOwnEchoIsStillFiltered() {
        val oem = AndroidScreenBrightnessController(
            context,
            settingsApiMaxOverride = 255,
            rawWrite = { raw ->
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, raw)
            },
            rawRead = { null },
        )
        oem.write(70) // A prior landed write, so "keeps the requested raw" is pinned against it.
        val result = oem.write(140)
        assertEquals(WriteStatus.WRITTEN_UNACKNOWLEDGED, result.status)
        assertEquals(140, result.requestedDomain)
        assertNull(result.readBackSettingValue)
        assertNull(result.acknowledgedDomain)
        assertTrue(oem.isSelfWrite(140), "the requested raw stays the marker when read-back fails")
        assertFalse(oem.isSelfWrite(70), "the previous marker must not survive a landed write")
    }

    @Test
    fun unexpectedThrowable_isRethrown_andLeavesNoPoisonedMarker() {
        var explode = false
        val oem = AndroidScreenBrightnessController(
            context,
            settingsApiMaxOverride = 255,
            rawWrite = { raw ->
                if (explode) throw IllegalStateException("provider")
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, raw)
            },
        )
        oem.write(70)
        explode = true
        assertFailsWith<IllegalStateException> { oem.write(200) }
        assertFalse(oem.isSelfWrite(4242), "selfWriteInProgress must not survive the rethrow")
        assertTrue(oem.isSelfWrite(70), "the previous marker must be restored")
    }

    @Test
    fun isManualMode_readsTheMode_andForceManualModeLandsIt() {
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
        )
        assertFalse(controller.isManualMode())
        assertTrue(controller.forceManualMode())
        assertTrue(controller.isManualMode())
    }

    private val manual = Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
    private val automatic = Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC

    private fun mode() = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, -1)

    private fun setMode(mode: Int) {
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, mode)
    }

    private fun prefs() = context.getSharedPreferences(
        AndroidScreenBrightnessController.PREFS_NAME, Context.MODE_PRIVATE,
    )

    private fun marker() = prefs().getBoolean(AndroidScreenBrightnessController.KEY_HANDED_OFF, false)

    private fun saved(): Int? = prefs().getInt(AndroidScreenBrightnessController.KEY_SAVED_MODE, -1).takeIf { it >= 0 }

    private fun modeSeam(refuse: (Int) -> Boolean) = AndroidScreenBrightnessController(
        context,
        rawModeWrite = { mode ->
            if (refuse(mode)) false
            else Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, mode)
        },
    )

    @Test
    fun savedManual_survivesHandoff_thenForceManual_thenRestore() {
        setMode(manual)
        controller.forceManualMode()
        assertTrue(controller.handOffToSystem())
        assertEquals(automatic, mode())
        assertTrue(marker())
        controller.forceManualMode()
        assertEquals(manual, saved(), "Tideo's own AUTOMATIC must not be saved as the user's")
        assertFalse(marker(), "a landed MANUAL ends the hand-off")
        controller.restoreMode()
        assertEquals(manual, mode())
        assertFalse(marker())
        assertNull(saved())
    }

    @Test
    fun afterForceManualEndsAHandoff_theUsersLaterAutomaticIsSavedAgain() {
        setMode(manual)
        controller.forceManualMode()
        controller.handOffToSystem()
        controller.forceManualMode()
        setMode(automatic)
        controller.forceManualMode()
        assertEquals(automatic, saved(), "the user's own AUTOMATIC")
        controller.restoreMode()
        assertEquals(automatic, mode())
    }

    @Test
    fun refusedRestoreDuringHandoff_keepsMarkerAndSavedMode_forTheNextStart() {
        setMode(manual)
        var refuse = false
        val oem = modeSeam { refuse }
        oem.forceManualMode()
        oem.handOffToSystem()
        refuse = true
        oem.restoreMode()
        assertEquals(automatic, mode())
        assertTrue(marker())
        assertEquals(manual, saved())
        val afterRestart = AndroidScreenBrightnessController(context)
        assertTrue(afterRestart.reconcileHandoff())
        afterRestart.restoreMode()
        assertEquals(manual, mode())
        assertFalse(marker())
    }

    @Test
    fun refusedRestoreOutsideHandoff_stillForgetsTheSavedMode_D134() {
        setMode(automatic)
        val oem = modeSeam { it == automatic }
        oem.forceManualMode()
        oem.restoreMode()
        assertNull(saved())
    }

    @Test
    fun reclaim_landsManual_clearsMarker_andKeepsTheSavedMode() {
        setMode(manual)
        controller.forceManualMode()
        controller.handOffToSystem()
        assertTrue(controller.reclaimFromSystem())
        assertEquals(manual, mode())
        assertFalse(marker())
        assertEquals(manual, saved())
    }

    @Test
    fun firstEntry_withNoSavedModeYet_savesTheOriginalManual() {
        setMode(manual)
        assertNull(saved())
        assertTrue(controller.handOffToSystem())
        assertEquals(manual, saved())
        controller.restoreMode()
        assertEquals(manual, mode())
    }

    @Test
    fun firstEntry_fromTheUsersAutomatic_savesAutomatic() {
        setMode(automatic)
        assertTrue(controller.handOffToSystem())
        assertEquals(automatic, saved())
        controller.reclaimFromSystem()
        controller.restoreMode()
        assertEquals(automatic, mode())
    }

    @Test
    fun secondEntry_whileHandedOff_neverSavesTideosAutomatic() {
        setMode(manual)
        controller.forceManualMode()
        controller.handOffToSystem()
        assertTrue(controller.handOffToSystem())
        assertEquals(manual, saved())
        assertTrue(marker())
    }

    @Test
    fun failedAutomaticWrite_leavesNoMarker_andTheModeAlone() {
        setMode(manual)
        val refusing = modeSeam { it == automatic }
        refusing.forceManualMode()
        assertFalse(refusing.handOffToSystem())
        assertFalse(marker())
        assertEquals(manual, mode())
        assertEquals(manual, saved())
    }

    @Test
    fun deniedAutomaticWrite_leavesNoMarker() {
        setMode(manual)
        val denying = AndroidScreenBrightnessController(
            context,
            rawModeWrite = { throw SecurityException("WRITE_SETTINGS revoked") },
        )
        assertFalse(denying.handOffToSystem())
        assertFalse(marker())
    }

    @Test
    fun unexpectedThrowableOnEntry_isRethrown_andLeavesNoMarker() {
        setMode(manual)
        val exploding = AndroidScreenBrightnessController(
            context,
            rawModeWrite = { throw IllegalStateException("provider") },
        )
        assertFailsWith<IllegalStateException> { exploding.handOffToSystem() }
        assertFalse(marker())
    }

    @Test
    fun failedReclaim_keepsTheMarker_soTheNextAttemptAndARestartStillKnowItIsOurs() {
        setMode(manual)
        var refuseManual = false
        val oem = modeSeam { it == manual && refuseManual }
        oem.forceManualMode()
        assertTrue(oem.handOffToSystem())
        refuseManual = true
        assertFalse(oem.reclaimFromSystem())
        assertTrue(marker())
        assertEquals(automatic, mode())
        oem.forceManualMode()
        assertEquals(manual, saved(), "a refused MANUAL leaves our AUTOMATIC unsaved")
        assertTrue(AndroidScreenBrightnessController(context).reconcileHandoff())
    }

    @Test
    fun deniedReclaim_keepsTheMarker() {
        setMode(manual)
        controller.handOffToSystem()
        val denying = AndroidScreenBrightnessController(
            context,
            rawModeWrite = { throw SecurityException("WRITE_SETTINGS revoked") },
        )
        assertFalse(denying.reclaimFromSystem())
        assertTrue(marker())
    }

    @Test
    fun newInstance_markerSet_modeAutomatic_staysHandedOff() {
        setMode(manual)
        controller.forceManualMode()
        controller.handOffToSystem()
        val afterRestart = AndroidScreenBrightnessController(context)
        assertTrue(afterRestart.reconcileHandoff())
        assertTrue(marker())
        afterRestart.restoreMode()
        assertEquals(manual, mode(), "the true pre-service mode survives process death mid-hand-off")
    }

    @Test
    fun newInstance_markerSet_modeManual_clearsTheMarker_andKeepsTheSavedMode() {
        setMode(automatic)
        controller.forceManualMode()
        prefs().edit().putBoolean(AndroidScreenBrightnessController.KEY_HANDED_OFF, true).commit()
        val afterRestart = AndroidScreenBrightnessController(context)
        assertFalse(afterRestart.reconcileHandoff())
        assertFalse(marker())
        afterRestart.restoreMode()
        assertEquals(automatic, mode())
    }

    @Test
    fun newInstance_markerSet_modeUnreadable_startsNormally_butKeepsTheMarker_untilManualLands() {
        prefs().edit()
            .putInt(AndroidScreenBrightnessController.KEY_SAVED_MODE, manual)
            .putBoolean(AndroidScreenBrightnessController.KEY_HANDED_OFF, true)
            .commit()
        val afterRestart = AndroidScreenBrightnessController(context)
        assertFalse(afterRestart.reconcileHandoff())
        assertTrue(marker(), "an unknown mode may still be our AUTOMATIC")
        setMode(automatic)
        afterRestart.forceManualMode()
        assertEquals(manual, saved(), "the AUTOMATIC read after an unreadable start is not saved")
        assertFalse(marker())
        afterRestart.restoreMode()
        assertEquals(manual, mode())
    }

    @Test
    fun failedRepeatEntry_keepsTheExistingMarker() {
        setMode(manual)
        var refuse = false
        val oem = modeSeam { refuse }
        oem.forceManualMode()
        assertTrue(oem.handOffToSystem())
        refuse = true
        assertFalse(oem.handOffToSystem())
        assertTrue(marker(), "only a marker this call created is rolled back")
        assertEquals(manual, saved())
    }

    @Test
    fun reconcile_withoutMarker_isFalse_andTouchesNothing() {
        setMode(automatic)
        assertFalse(controller.reconcileHandoff())
        assertEquals(automatic, mode())
        assertNull(saved())
    }

    @Test
    fun panicDuringHandoff_restoresTheTrueMode_andClearsTheMarker() {
        setMode(manual)
        controller.forceManualMode()
        controller.handOffToSystem()
        controller.forceManualMode()
        controller.write(255)
        controller.restoreMode()
        assertEquals(manual, mode())
        assertFalse(marker())
        assertNull(saved())
    }
}
