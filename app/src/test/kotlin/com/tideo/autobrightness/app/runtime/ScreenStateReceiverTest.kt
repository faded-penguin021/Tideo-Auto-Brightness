package com.tideo.autobrightness.app.runtime

import android.content.Intent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertNull

/**
 * S12.9e: [ScreenStateReceiver] now dispatches its DataStore read through the [goAsync] helper
 * (`PendingResult` + supervised AppProcessScope) instead of a detached `CoroutineScope.launch`. A
 * null/unknown action must short-circuit before any async work, and a directly-invoked receiver (no
 * system dispatch → null PendingResult) must not crash.
 */
@RunWith(RobolectricTestRunner::class)
class ScreenStateReceiverTest {

    @Test
    fun nullAction_startsNoServiceAndDoesNotThrow() {
        val context = RuntimeEnvironment.getApplication()
        while (shadowOf(context).nextStartedService != null) { /* drain */ }

        ScreenStateReceiver().onReceive(context, Intent())

        assertNull(
            shadowOf(context).nextStartedService,
            "an intent with no action must not start the service",
        )
    }

    @Test
    fun screenAction_doesNotThrowWhenInvokedDirectly() {
        val context = RuntimeEnvironment.getApplication()
        // Service is disabled by default, so the async branch reads serviceEnabled=false and returns;
        // the point of this test is that the goAsync() path (null PendingResult under a direct call)
        // does not throw on the calling thread.
        ScreenStateReceiver().onReceive(context, Intent(Intent.ACTION_SCREEN_ON))
        ScreenStateReceiver().onReceive(context, Intent(Intent.ACTION_SCREEN_OFF))
    }
}
