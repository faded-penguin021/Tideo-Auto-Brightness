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
            "a non-boot action must not start the service (short-circuits before goAsync)",
        )
    }

    // S12.9e: the boot read now runs via goAsync(); a directly-invoked receiver gets a null
    // PendingResult, which the helper finishes defensively — the dispatch must not throw on the
    // calling thread (the async branch reads serviceEnabled=false by default and starts nothing).
    @Test
    fun receiver_bootAction_dispatchesWithoutThrowing() {
        val context = RuntimeEnvironment.getApplication()
        BootCompletedReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
    }
}
