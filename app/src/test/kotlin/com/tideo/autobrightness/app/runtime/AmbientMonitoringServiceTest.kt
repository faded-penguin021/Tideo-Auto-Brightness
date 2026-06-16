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
        val controller = Robolectric.buildService(AmbientMonitoringService::class.java).create()
        // ACTION_PAUSE keeps the heavy sensor/observer flows from starting while still exercising
        // the foreground-notification path (posted unconditionally in onStartCommand).
        val intent = Intent().setAction(AmbientMonitoringService.ACTION_PAUSE)
        val service = controller.withIntent(intent).startCommand(0, 0).get()

        val notification = shadowOf(service).lastForegroundNotification
        assertNotNull(notification, "service should post a foreground notification")
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

    // S12.8a/G2R-F76: the ongoing service notification must NOT carry a Pause action (it behaved like
    // an override and confused users); Reset + Disable remain.
    @Test
    fun ongoingNotification_hasNoPauseAction() {
        val controller = Robolectric.buildService(AmbientMonitoringService::class.java).create()
        val intent = Intent().setAction(AmbientMonitoringService.ACTION_PAUSE)
        val service = controller.withIntent(intent).startCommand(0, 0).get()

        val notification = shadowOf(service).lastForegroundNotification
        assertNotNull(notification)
        val actions = notification.actions?.map { it.title.toString() } ?: emptyList()
        assertTrue(!actions.contains("Pause"), "ongoing notification must not offer Pause (F76)")
        assertTrue(actions.contains("Reset"), "Reset (panic) is kept")
        assertTrue(actions.contains("Disable"), "Disable is kept")
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
