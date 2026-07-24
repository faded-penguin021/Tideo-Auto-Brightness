package com.tideo.autobrightness.app.runtime

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.tideo.autobrightness.app.storage.settingsDataStore
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
    fun onStartCommand_postsForegroundNotification() {
        // ACTION_START: since D-140 a PAUSE on a not-running instance stops the service (removing the
        // foreground notification), so the notification assertions must ride the real start action.
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

    // S12.7b/G2R-F35/F40: a detected manual override posts a high-priority notification carrying a
    // Resume action, and flashes a toast.
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

    // S12.9b/G2R-F91: the override flash must route through the shared AabFlash operational surface
    // (so a registered presenter — the a11y overlay or the in-app pill — renders it), not a bare Toast.
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

    // S12.8a/G2R-F76: the ongoing service notification must NOT carry a Pause action (it behaved like
    // an override and confused users); Reset + Disable remain.
    @Test
    fun ongoingNotification_hasNoPauseAction() {
        // ACTION_START for the same D-140 reason as above.
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

    // D-140 (F-backlog U1): startForegroundService CREATES the service, so a PAUSE/REAPPLY aimed at
    // "the running service" can land on a fresh instance whose pipeline was never start()ed (e.g.
    // the widget's Reset while the service is off — its "no-op when not running" comment assumed
    // the intent would be dropped). There is nothing to pause or re-apply on such an instance; it
    // must stop itself instead of idling forever as a foregrounded zombie (REAPPLY previously even
    // started the light-sensor collector against the persisted disable).
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

    // The positive path must survive the D-140 gate: once START has run the pipeline (serviceOn=true,
    // set synchronously by controller.start()), PAUSE and REAPPLY act on it and keep the service up.
    //
    // ACTION_START falls into the service's `else` branch, whose D-140 START_STICKY defense fires a
    // `scope.launch { if (!serviceEnabled) disableAndStop() }` on Dispatchers.Default (a background
    // thread). With the DataStore left at its default `serviceEnabled=false` that guard could tear the
    // service down mid-test, nondeterministically — a real race, not a test artifact. Seed
    // `serviceEnabled=true` first (exactly what every explicit starter does in production, per the
    // branch's own comment) so the guard is a no-op regardless of thread timing.
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

    // The publisher only emits while opted-in AND running; the OFF transition is onDestroy's job, so a
    // not-running or opted-out snapshot maps to null (nothing broadcast).
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
        // Global (no package restriction) so a third-party automation receiver can pick it up.
        assertNull(intent.`package`, "the outbound event must not be package-restricted")
    }

    // onDestroy is the single authoritative OFF emitter, covering EVERY stop path uniformly. When the
    // user has opted in (cache = true), tearing the service down broadcasts a final off-state; when they
    // never opted in, nothing is emitted.
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
        // externalControlEnabled stays at its default false.
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
        // Tear down cleanly (unregisters the screen receiver, cancels the scope).
        controller.destroy()
    }
}
