package com.tideo.autobrightness.app.control

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tideo.autobrightness.app.runtime.AmbientMonitoringService
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

/**
 * D-157: the exported [ControlReceiver] is safe only because its opt-in gate defaults OFF and is the
 * FIRST check. These tests pin (a) the OFF-ignores-everything security property and (b) that each core
 * verb, once past the gate, routes onto its existing service action.
 */
@RunWith(RobolectricTestRunner::class)
class ControlReceiverTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val receiver = ControlReceiver()

    /**
     * The security property (D-147 class re-opened, but gated): with the opt-in flag at its default OFF,
     * NO action — not even one that would otherwise start the service — reaches the runtime. Goes through
     * [ControlReceiver.handle] so the gate is exercised exactly as `onReceive` runs it.
     */
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
    fun resume_routesToResumeAction() = assertRoutes(ControlReceiver.ACTION_RESUME, AmbientMonitoringService.ACTION_RESUME)

    @Test
    fun reapply_routesToReapplyAction() = assertRoutes(ControlReceiver.ACTION_REAPPLY, AmbientMonitoringService.ACTION_REAPPLY)

    @Test
    fun panic_routesToPanicAction() = assertRoutes(ControlReceiver.ACTION_PANIC, AmbientMonitoringService.ACTION_PANIC)

    @Test
    fun unknownAction_ignored() {
        runBlocking { receiver.route(application, "com.tideo.autobrightness.control.BOGUS") }
        assertNull(shadowOf(application).nextStartedService, "an unrecognised action must be a no-op")
    }

    /** LOAD_PROFILE routes onto the shared [ProfileApplier]: the named built-in applies and latches the
     *  manual context lock (serviceEnabled=false so no reapply intent is emitted). */
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

    private fun seed(settings: AabSettings) = runBlocking { application.settingsDataStore.updateData { settings } }
    private fun committed(): AabSettings = runBlocking { application.settingsDataStore.data.first() }

    /** Past-the-gate routing: [route] maps the control verb onto the service's own action constant. */
    private fun assertRoutes(controlAction: String, expectedServiceAction: String) {
        runBlocking { receiver.route(application, controlAction) }
        val intent = shadowOf(application).nextStartedService
        assertEquals(AmbientMonitoringService::class.java.name, intent.component?.className)
        assertEquals(expectedServiceAction, intent.action)
    }

    // NOT covered: SERVICE_ON/OFF/TOGGLE routing. Their enable path schedules the maintenance worker
    // (WorkManager unavailable under Robolectric without work-testing — a declined dependency, H3) and
    // writes the shared settings DataStore singleton (pollutes later suites). This is the same carve-out
    // as WidgetActionReceiverTest.toggle; the setServiceEnabled body is that shipped dance, parameterized.
}
