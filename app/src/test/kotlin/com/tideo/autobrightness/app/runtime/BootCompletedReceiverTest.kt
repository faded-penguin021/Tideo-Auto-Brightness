package com.tideo.autobrightness.app.runtime

import android.content.Intent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class BootCompletedReceiverTest {

    @Test
    fun startMonitoring_targetsAmbientMonitoringService() {
        val context = RuntimeEnvironment.getApplication()

        AutoBrightnessRuntime.startMonitoring(context, "test")

        val started = shadowOf(context).nextStartedService
        assertNotNull(started, "startMonitoring should start a service")
        assertEquals(
            AmbientMonitoringService::class.java.name,
            started.component?.className,
            "boot start must target the ambient monitoring foreground service",
        )
        assertEquals(AmbientMonitoringService.ACTION_START, started.action)
    }

    @Test
    fun receiver_ignoresNonBootAction() {
        val context = RuntimeEnvironment.getApplication()
        // Drain any pre-existing started-service intents.
        while (shadowOf(context).nextStartedService != null) { /* drain */ }

        BootCompletedReceiver().onReceive(context, Intent("com.example.OTHER"))

        assertNull(
            shadowOf(context).nextStartedService,
            "a non-boot action must not start the service",
        )
    }
}
