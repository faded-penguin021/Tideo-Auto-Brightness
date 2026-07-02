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

/**
 * D-147: the widget provider must stay exported for the system's APPWIDGET_UPDATE broadcasts, so a
 * co-installed app can always send it explicit intents — the custom state-changing button actions
 * (toggle/reset) must therefore not be actionable on it.
 */
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

    // NOT covered here: WidgetActionReceiver.toggle. Its enable path schedules the maintenance
    // worker, and WorkManager cannot run under Robolectric without the androidx.work-testing
    // artifact (declined as a new dependency, see the H3 MaintenanceWorker rationale in STATE.md).
    // Writing the shared DataStore singleton from this test also polluted persisted serviceEnabled
    // for later suites. The toggle body is the QS tile's shipped pattern, moved verbatim (D-147).
}
