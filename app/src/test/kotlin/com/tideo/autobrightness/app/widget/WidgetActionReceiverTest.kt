package com.tideo.autobrightness.app.widget

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.tideo.autobrightness.app.runtime.AmbientMonitoringService
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// D-147: exported provider block custom actions (toggle/reset) on co-installed apps.
@RunWith(RobolectricTestRunner::class)
class WidgetActionReceiverTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun exportedProvider_ignoresForeignResetAction_D147() {
        DashboardWidgetProvider().onReceive(
            application,
            Intent("com.tideo.autobrightness.widget.action.RESET"),
        )
        assertNull(
            shadowOf(application).nextStartedService,
            "a foreign RESET on the exported provider must not reach the service",
        )
    }

    @Test
    fun reset_dispatchesReapplyToTheMonitoringService() {
        WidgetActionReceiver().onReceive(application, Intent(WidgetActionReceiver.ACTION_RESET))
        val intent = shadowOf(application).nextStartedService
        assertEquals(AmbientMonitoringService::class.java.name, intent.component?.className)
        assertEquals(AmbientMonitoringService.ACTION_REAPPLY, intent.action)
    }

}
