package com.tideo.autobrightness.app.runtime

import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
    @Test
    fun pauseAndReapply_whilePipelineRunning_keepTheServiceUp() {
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
