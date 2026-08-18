package com.tideo.autobrightness.app.runtime

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.tideo.autobrightness.app.storage.settingsDataStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AmbientMonitoringServiceTest {

    @Test
    fun disabledStickyRestart_stopsWithoutStartingRuntimeOrWriters() {
        val gate = CompletableDeferred<Unit>()
        val controller = Robolectric.buildService(AmbientMonitoringService::class.java).create()
        try {
            val service = controller.get()
            service.stickyRestartEnabledReader = { gate.await(); false }

            service.onStartCommand(null, 0, 41)
            assertNotNull(shadowOf(service).lastForegroundNotification, "FGS deadline is met while the gate waits")
            assertEquals(0, service.runtimeStartCount, "no runtime component or writer may start before confirmation")

            gate.complete(Unit)
            waitUntil { shadowOf(service).isStoppedBySelf }
            assertEquals(0, service.runtimeStartCount, "disabled restart must never reach runtime/writer startup")
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun enabledStickyRestart_startsRuntimeExactlyOnce() {
        val gate = CompletableDeferred<Unit>()
        val controller = Robolectric.buildService(AmbientMonitoringService::class.java).create()
        try {
            val service = controller.get()
            service.stickyRestartEnabledReader = { gate.await(); true }
            service.onStartCommand(null, 0, 42)

            gate.complete(Unit)
            waitUntil { service.runtimeStartCount == 1 }
            assertEquals(1, service.runtimeStartCount)
            assertFalse(shadowOf(service).isStoppedBySelf)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun failedStickyRestartRead_failsClosedWithoutRuntimeStartup() {
        val controller = Robolectric.buildService(AmbientMonitoringService::class.java).create()
        try {
            val service = controller.get()
            service.stickyRestartEnabledReader = { error("unreadable settings") }

            service.onStartCommand(null, 0, 46)
            waitUntil { shadowOf(service).isStoppedBySelf }

            assertEquals(0, service.runtimeStartCount)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun destructionWhileStickyRestartGatePending_preventsLateRuntimeStartup() {
        val readerReturned = CompletableDeferred<Unit>()
        val controller = Robolectric.buildService(AmbientMonitoringService::class.java).create()
        val service = controller.get()
        service.stickyRestartEnabledReader = {
            readerReturned.complete(Unit)
            true
        }
        service.onStartCommand(null, 0, 43)

        runBlocking { readerReturned.await() }
        // Destroy in cancel-after-last-suspension window to test late startup prevention.
        controller.destroy()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, service.runtimeStartCount, "destroy must cancel the gate before any runtime stop/write path")
    }

    @Test
    fun explicitStartWhileStickyRestartGatePending_supersedesGateAndStartsOnce() {
        val readerReturned = CompletableDeferred<Unit>()
        val controller = Robolectric.buildService(AmbientMonitoringService::class.java).create()
        try {
            val service = controller.get()
            service.stickyRestartEnabledReader = {
                readerReturned.complete(Unit)
                false
            }
            service.onStartCommand(null, 0, 44)
            runBlocking { readerReturned.await() }

            service.onStartCommand(Intent().setAction(AmbientMonitoringService.ACTION_START), 0, 45)
            assertEquals(1, service.runtimeStartCount, "trusted explicit starts retain synchronous semantics")
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(1, service.runtimeStartCount, "the cancelled sticky decision cannot start or stop again")
            assertFalse(shadowOf(service).isStoppedBySelf)
        } finally {
            controller.destroy()
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 2_000
        while (!condition() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue(condition(), "asynchronous service transition timed out")
    }

    @Test
    fun onStartCommand_postsForegroundNotification() {
        // ACTION_START: D-140 stops service on PAUSE, so assertions ride the real start action.
        val controller = Robolectric.buildService(AmbientMonitoringService::class.java).create()
        try {
            val intent = Intent().setAction(AmbientMonitoringService.ACTION_START)
            val service = controller.withIntent(intent).startCommand(0, 0).get()

            val notification = shadowOf(service).lastForegroundNotification
            assertNotNull(notification, "service should post a foreground notification")
        } finally {
            controller.destroy()
        }
    }

    // S12.7b/G2R-F35/F40: high-priority notification with Resume action + toast.
    @Test
    fun manualOverride_postsHighPriorityNotificationWithResumeAndToast() {
        val service = Robolectric.buildService(AmbientMonitoringService::class.java).create().get()

        service.notifyManualOverride()
        shadowOf(Looper.getMainLooper()).idle() // run the posted Toast

        val nm = service.getSystemService(NotificationManager::class.java)
        val notif = shadowOf(nm).allNotifications.last()
        assertEquals(android.app.Notification.PRIORITY_HIGH, notif.priority, "override alert is high-priority")
        val actions = notif.actions?.map { it.title.toString() } ?: emptyList()
        assertTrue(actions.contains("Resume"), "override alert offers a Resume action (F40)")
        assertTrue(
            ShadowToast.getTextOfLatestToast().contains("Manual override"),
            "a toast should flash on a manual override",
        )
    }

    // S12.9b/G2R-F91: override flash routed through AabFlash presenter, not bare Toast.
    @Test
    fun manualOverride_flashesThroughAabFlashSurface() {
        val shown = mutableListOf<String>()
        val presenter = object : AabFlash.Presenter {
            override fun show(text: String) { shown += text }
            override fun hide() {}
        }
        AabFlash.register(presenter)
        try {
            val service = Robolectric.buildService(AmbientMonitoringService::class.java).create().get()
            service.notifyManualOverride()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(
                shown.any { it.contains("Manual override") },
                "override flash should be delivered to the AabFlash presenter, not a plain Toast",
            )
        } finally {
            AabFlash.register(null)
        }
    }

    // S12.8a/G2R-F76: ongoing notification has NO Pause action (confused users).
    @Test
    fun ongoingNotification_hasNoPauseAction() {
        // ACTION_START for D-140.
        val controller = Robolectric.buildService(AmbientMonitoringService::class.java).create()
        try {
            val intent = Intent().setAction(AmbientMonitoringService.ACTION_START)
            val service = controller.withIntent(intent).startCommand(0, 0).get()

            val notification = shadowOf(service).lastForegroundNotification
            assertNotNull(notification)
            val actions = notification.actions?.map { it.title.toString() } ?: emptyList()
            assertTrue(!actions.contains("Pause"), "ongoing notification must not offer Pause (F76)")
            assertTrue(actions.contains("Reset"), "Reset (panic) is kept")
            assertTrue(actions.contains("Disable"), "Disable is kept")
        } finally {
            controller.destroy()
        }
    }

    // D-140: PAUSE/REAPPLY on fresh instance (created by startForegroundService) must stop, not zombie.
    @Test
    fun pause_whenPipelineNotRunning_stopsSelfInsteadOfZombieing() {
        val service = Robolectric.buildService(AmbientMonitoringService::class.java).create().get()

        val result = service.onStartCommand(Intent().setAction(AmbientMonitoringService.ACTION_PAUSE), 0, 1)

        assertEquals(android.app.Service.START_NOT_STICKY, result, "a not-running PAUSE must not be sticky")
        assertTrue(shadowOf(service).isStoppedBySelf, "the service must stop itself (D-140)")
    }

    @Test
    fun reapply_whenPipelineNotRunning_stopsSelfInsteadOfStartingThePipeline() {
        val service = Robolectric.buildService(AmbientMonitoringService::class.java).create().get()

        val result = service.onStartCommand(Intent().setAction(AmbientMonitoringService.ACTION_REAPPLY), 0, 1)

        assertEquals(android.app.Service.START_NOT_STICKY, result)
        assertTrue(shadowOf(service).isStoppedBySelf, "REAPPLY on a not-running service must not start the pipeline (D-140)")
    }

    // DB-037: panic confirms itself however it was triggered. D-155: PANIC is also exempt from
    // the D-140 not-running gate that stops PAUSE/REAPPLY/RESUME_CONTEXT, so it runs here.
    @Test
    fun panicByIntent_vibratesSosOnce_andIsNotRefusedByTheNotRunningGate() {
        val controller = Robolectric.buildService(AmbientMonitoringService::class.java).create()
        try {
            val service = controller.get()
            assertEquals(0, service.sosCount)

            val result = service.onStartCommand(Intent().setAction(AmbientMonitoringService.ACTION_PANIC), 0, 1)

            assertEquals(android.app.Service.START_NOT_STICKY, result, "panic is a full stop")
            // panicAndStop runs on Dispatchers.Default — idling the main looper proves nothing, and
            // stopSelf() lands later still (after the DataStore write), so it needs its own wait.
            waitUntil { service.sosCount == 1 }
            waitUntil { shadowOf(service).isStoppedBySelf }
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun panic_twice_buzzesOnce() {
        // Double-tapping the notification's Reset used to run the whole recovery twice; silent
        // before, an audible double buzz once every path confirms.
        val controller = Robolectric.buildService(AmbientMonitoringService::class.java).create()
        try {
            val service = controller.get()
            val panic = Intent().setAction(AmbientMonitoringService.ACTION_PANIC)

            service.onStartCommand(panic, 0, 1)
            service.onStartCommand(panic, 0, 2)
            waitUntil { service.sosCount >= 1 }
            repeat(20) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10) }

            assertEquals(1, service.sosCount, "a second panic must be swallowed, not buzz again")
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun resumeContext_whenPipelineNotRunning_stopsSelfInsteadOfStartingThePipeline() {
        // DA-018: RESUME_CONTEXT shares D-140 not-running gate; must not birth zombie FGS.
        val service = Robolectric.buildService(AmbientMonitoringService::class.java).create().get()

        val result = service.onStartCommand(Intent().setAction(AmbientMonitoringService.ACTION_RESUME_CONTEXT), 0, 1)

        assertEquals(android.app.Service.START_NOT_STICKY, result)
        assertTrue(shadowOf(service).isStoppedBySelf, "RESUME_CONTEXT on a not-running service must not start the pipeline (D-140)")
    }

    // D-140: once START runs pipeline (serviceOn=true), PAUSE/REAPPLY act on it and keep service up.
    // ACTION_START is trusted; caller persisted serviceEnabled, bypasses null-intent gate.
    @Test
    fun pauseAndReapply_whilePipelineRunning_keepTheServiceUp() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        runBlocking { app.settingsDataStore.updateData { it.copy(serviceEnabled = true) } }
        val controller = Robolectric.buildService(AmbientMonitoringService::class.java).create()
        try {
            val service = controller.get()
            service.onStartCommand(Intent().setAction(AmbientMonitoringService.ACTION_START), 0, 1)
            service.onStartCommand(Intent().setAction(AmbientMonitoringService.ACTION_PAUSE), 0, 2)
            service.onStartCommand(Intent().setAction(AmbientMonitoringService.ACTION_REAPPLY), 0, 3)
            assertTrue(!shadowOf(service).isStoppedBySelf, "a running service must not stop on PAUSE/REAPPLY")
        } finally {
            controller.destroy()
        }
    }

    // ---- D-157 (U5): outbound event.STATE_CHANGED contract + teardown ordering ----

    // Publisher emits only while opted-in AND running; OFF is onDestroy's job.
    @Test
    fun outboundSnapshot_isNullWhenOptedOutOrNotRunning() {
        val service = Robolectric.buildService(AmbientMonitoringService::class.java).create().get()
        assertNull(
            service.outboundSnapshot(enabled = false, state = PipelineState(serviceOn = true), profile = "Night"),
            "opted out → publish nothing",
        )
        assertNull(
            service.outboundSnapshot(enabled = true, state = PipelineState(serviceOn = false), profile = null),
            "not running → the OFF event is onDestroy's, not the collector's",
        )
    }

    @Test
    fun outboundSnapshot_reflectsRunningPausedAndProfile() {
        val service = Robolectric.buildService(AmbientMonitoringService::class.java).create().get()

        val active = service.outboundSnapshot(true, PipelineState(serviceOn = true, paused = false), "Night")!!
        assertEquals(true, active.enabled)
        assertEquals(true, active.running, "on and not paused = actively adjusting")
        assertEquals(false, active.paused)
        assertEquals("Night", active.profile)

        val paused = service.outboundSnapshot(true, PipelineState(serviceOn = true, paused = true), null)!!
        assertEquals(true, paused.enabled)
        assertFalse(paused.running, "paused = not actively adjusting")
        assertEquals(true, paused.paused)
        assertNull(paused.profile, "no profile in force is surfaced as a null extra")
    }

    @Test
    fun buildStateChangedIntent_carriesTheFullPublicContract() {
        val service = Robolectric.buildService(AmbientMonitoringService::class.java).create().get()

        val intent = service.buildStateChangedIntent(enabled = true, running = false, paused = true, profile = "Night")

        assertEquals(AmbientMonitoringService.ACTION_STATE_CHANGED, intent.action)
        assertEquals(true, intent.getBooleanExtra(AmbientMonitoringService.EXTRA_ENABLED, false))
        assertEquals(false, intent.getBooleanExtra(AmbientMonitoringService.EXTRA_RUNNING, true))
        assertEquals(true, intent.getBooleanExtra(AmbientMonitoringService.EXTRA_PAUSED, false))
        assertEquals("Night", intent.getStringExtra(AmbientMonitoringService.EXTRA_PROFILE))
        // Global (no package restriction) for third-party automation receivers.
        assertNull(intent.`package`, "the outbound event must not be package-restricted")
    }

    // onDestroy: single authoritative OFF emitter; emits when opted in, silent when never opted in.
    @Test
    fun onDestroy_broadcastsFinalOffEvent_whenOptedIn() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val controller = Robolectric.buildService(AmbientMonitoringService::class.java).create()
        controller.get().externalControlEnabled = true

        controller.destroy()

        val off = shadowOf(app).broadcastIntents
            .last { it.action == AmbientMonitoringService.ACTION_STATE_CHANGED }
        assertEquals(false, off.getBooleanExtra(AmbientMonitoringService.EXTRA_ENABLED, true))
        assertEquals(false, off.getBooleanExtra(AmbientMonitoringService.EXTRA_RUNNING, true))
        assertEquals(false, off.getBooleanExtra(AmbientMonitoringService.EXTRA_PAUSED, true))
        assertNull(off.getStringExtra(AmbientMonitoringService.EXTRA_PROFILE))
    }

    @Test
    fun onDestroy_emitsNothing_whenExternalControlNeverEnabled() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val controller = Robolectric.buildService(AmbientMonitoringService::class.java).create()
        controller.destroy()

        assertTrue(
            shadowOf(app).broadcastIntents.none { it.action == AmbientMonitoringService.ACTION_STATE_CHANGED },
            "no outbound event may leak when external control was never opted into",
        )
    }

    @Test
    fun lifecycle_createStartDestroy_doesNotThrow() {
        val controller = Robolectric.buildService(AmbientMonitoringService::class.java).create()
        controller
            .withIntent(Intent().setAction(AmbientMonitoringService.ACTION_PAUSE))
            .startCommand(0, 0)
            .get()
        controller.destroy()
    }
}
