package com.tideo.autobrightness.app.runtime

import android.content.Intent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertNotNull

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
