package com.tideo.autobrightness.app.control

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.tideo.autobrightness.app.runtime.AabFlash
import com.tideo.autobrightness.app.runtime.AmbientMonitoringService
import com.tideo.autobrightness.app.runtime.DebugCategory
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.storage.settingsDataStore
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

/** D-157: ControlReceiver security (opt-in gate defaults OFF); routing verification. */
@RunWith(RobolectricTestRunner::class)
class ControlReceiverTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val receiver = ControlReceiver()

    @Test
    fun controlDisabled_ignoresAllActions_D157() {
        val actions = listOf(
            ControlReceiver.ACTION_SERVICE_ON,
            ControlReceiver.ACTION_SERVICE_OFF,
            ControlReceiver.ACTION_SERVICE_TOGGLE,
            ControlReceiver.ACTION_PAUSE,
            ControlReceiver.ACTION_RESUME,
            ControlReceiver.ACTION_REAPPLY,
            ControlReceiver.ACTION_PANIC,
            ControlReceiver.ACTION_LOAD_PROFILE,
            ControlReceiver.ACTION_CONTEXTS_RESUME,
        )
        for (action in actions) {
            runBlocking { receiver.handle(application, action) }
            assertNull(
                shadowOf(application).nextStartedService,
                "gate OFF must ignore $action — nothing may reach the service",
            )
        }
    }

    @Test
    fun pause_routesToPauseAction() = assertRoutes(ControlReceiver.ACTION_PAUSE, AmbientMonitoringService.ACTION_PAUSE)

    @Test
    fun resume_routesToResumeAction() {
        seed(AabSettings(serviceEnabled = true))
        assertRoutes(ControlReceiver.ACTION_RESUME, AmbientMonitoringService.ACTION_RESUME)
    }

    @Test
    fun resume_whileServiceDisabled_isDropped_D160() {
        seed(AabSettings(serviceEnabled = false))
        runBlocking { receiver.route(application, ControlReceiver.ACTION_RESUME) }
        assertNull(
            shadowOf(application).nextStartedService,
            "external RESUME while the service is disabled must not reach the service",
        )
    }

    @Test
    fun reapply_routesToReapplyAction() = assertRoutes(ControlReceiver.ACTION_REAPPLY, AmbientMonitoringService.ACTION_REAPPLY)

    @Test
    fun panic_routesToPanicAction() = assertRoutes(ControlReceiver.ACTION_PANIC, AmbientMonitoringService.ACTION_PANIC)

    @Test
    fun unknownAction_ignored() {
        runBlocking { receiver.route(application, "com.tideo.autobrightness.control.BOGUS") }
        assertNull(shadowOf(application).nextStartedService, "an unrecognised action must be a no-op")
    }

    @Test
    fun loadProfile_appliesNamedProfileViaProfileApplier() {
        seed(AabSettings(serviceEnabled = false, minBrightness = 3, contextOverride = false))
        runBlocking { receiver.route(application, ControlReceiver.ACTION_LOAD_PROFILE, "Battery Saver") }
        val r = committed()
        assertNotEquals(3, r.minBrightness, "the loaded profile's curve params applied")
        assertTrue(r.contextOverride, "an external LOAD_PROFILE latches the manual context lock (G2R-F30)")
    }

    @Test
    fun loadProfile_withoutName_isNoOp() {
        val before = AabSettings(serviceEnabled = false, minBrightness = 42, contextOverride = false)
        seed(before)
        runBlocking { receiver.route(application, ControlReceiver.ACTION_LOAD_PROFILE, null) }
        assertEquals(before, committed(), "LOAD_PROFILE without a name extra must change nothing")
    }

    @Test
    fun contextsResume_clearsContextLockViaProfileApplier() {
        seed(AabSettings(serviceEnabled = false, contextOverride = true))
        runBlocking { receiver.route(application, ControlReceiver.ACTION_CONTEXTS_RESUME) }
        assertFalse(committed().contextOverride, "CONTEXTS_RESUME clears the manual context lock")
    }

    // --- DB-035: why a dropped command did nothing (CONTEXT_AUTOMATION level only) ---

    @Test
    fun gateOff_flashesTheReason_atTheContextAutomationLevel() {
        seed(AabSettings(debugLevel = DebugCategory.CONTEXT_AUTOMATION.level))
        val flashes = captureFlashes {
            runBlocking { receiver.handle(application, ControlReceiver.ACTION_SERVICE_ON) }
        }
        assertEquals(1, flashes.size, "the gate-off drop must say so")
        assertTrue(flashes.single().contains("external control is off"))
        assertNull(shadowOf(application).nextStartedService, "the flash must not weaken the gate")
    }

    @Test
    fun gateOff_isSilentAtEveryOtherDebugLevel() {
        // D-157: the DEFAULT configuration keeps "no side effect before the opt-in gate".
        for (level in listOf(0, 1, 7, 9)) {
            seed(AabSettings(debugLevel = level))
            val flashes = captureFlashes {
                runBlocking { receiver.handle(application, ControlReceiver.ACTION_SERVICE_ON) }
            }
            assertTrue(flashes.isEmpty(), "debug level $level must stay silent")
        }
    }

    @Test
    fun loadProfile_unknownName_flashesTheName() {
        seed(AabSettings(debugLevel = DebugCategory.CONTEXT_AUTOMATION.level, minBrightness = 42))
        val flashes = captureFlashes {
            runBlocking { receiver.route(application, ControlReceiver.ACTION_LOAD_PROFILE, "Nope") }
        }
        assertTrue(flashes.single().contains("Nope"), "the unresolved name is the useful part")
        assertEquals(42, committed().minBrightness, "an unknown name must still change nothing")
    }

    @Test
    fun loadProfile_withoutName_flashesTheMissingExtra() {
        seed(AabSettings(debugLevel = DebugCategory.CONTEXT_AUTOMATION.level))
        val flashes = captureFlashes {
            runBlocking { receiver.route(application, ControlReceiver.ACTION_LOAD_PROFILE, null) }
        }
        // Not just "name" — the unknown-profile string contains "named" too, so that would pass
        // with the two branches swapped.
        assertTrue(flashes.single().contains("no \"name\" extra"), "must be the MISSING-extra message")
    }

    @Test
    fun loadProfile_unknownName_isClampedAndStrippedBeforeDisplay() {
        // The caller picks this text and AabFlash may render it in the system-wide overlay.
        seed(AabSettings(debugLevel = DebugCategory.CONTEXT_AUTOMATION.level))
        val hostile = "‮Enter\n\u0000your PIN‬" + "A".repeat(8_000)
        val flashes = captureFlashes {
            runBlocking { receiver.route(application, ControlReceiver.ACTION_LOAD_PROFILE, hostile) }
        }
        val flash = flashes.single()
        assertTrue(flash.length < 200, "an 8 KB name must not reach the overlay verbatim")
        assertFalse(flash.contains('‮'), "bidi overrides must be stripped")
        assertFalse(flash.contains('\n') || flash.contains('\u0000'), "ISO controls must be stripped")
        assertFalse(flash.contains('�'), "stripped controls must not become replacement glyphs")
    }

    @Test
    fun resume_whileServiceDisabled_flashesTheReason_D160() {
        seed(AabSettings(serviceEnabled = false, debugLevel = DebugCategory.CONTEXT_AUTOMATION.level))
        val flashes = captureFlashes {
            runBlocking { receiver.route(application, ControlReceiver.ACTION_RESUME) }
        }
        assertTrue(flashes.single().contains("service is switched off"))
        assertNull(shadowOf(application).nextStartedService, "still dropped — the flash is the only change")
    }

    @Test
    fun anAppliedCommandFlashesNothing() {
        seed(AabSettings(serviceEnabled = true, debugLevel = DebugCategory.CONTEXT_AUTOMATION.level))
        val flashes = captureFlashes {
            runBlocking { receiver.route(application, ControlReceiver.ACTION_RESUME) }
        }
        assertTrue(flashes.isEmpty(), "only DROPS explain themselves")
    }

    /** Collects AabFlash output; the sink posts to the main looper, so idle it before returning. */
    private fun captureFlashes(block: () -> Unit): List<String> {
        val seen = mutableListOf<String>()
        AabFlash.register(object : AabFlash.Presenter {
            override fun show(text: String) { seen += text }
            override fun hide() = Unit
        })
        try {
            block()
            shadowOf(Looper.getMainLooper()).idle()
        } finally {
            AabFlash.register(null)
        }
        return seen
    }

    private fun seed(settings: AabSettings) = runBlocking { application.settingsDataStore.updateData { settings } }
    private fun committed(): AabSettings = runBlocking { application.settingsDataStore.data.first() }

    private fun assertRoutes(controlAction: String, expectedServiceAction: String) {
        runBlocking { receiver.route(application, controlAction) }
        val intent = shadowOf(application).nextStartedService
        assertEquals(AmbientMonitoringService::class.java.name, intent.component?.className)
        assertEquals(expectedServiceAction, intent.action)
    }
}
