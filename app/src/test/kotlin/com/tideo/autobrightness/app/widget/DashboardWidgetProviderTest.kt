package com.tideo.autobrightness.app.widget

import com.tideo.autobrightness.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure mapping test for the home-screen widget status label (no Android widget binding needed — the
 * mapping is extracted so it is unit-testable, like [com.tideo.autobrightness.app.runtime.BrightnessTileService.tileSubtitle]).
 */
class DashboardWidgetProviderTest {

    private fun model(enabled: Boolean, running: Boolean, paused: Boolean) =
        WidgetModel(enabled, running, paused, brightness = null, lux = null, profile = null, context = null)

    @Test fun off_whenDisabled() {
        assertEquals(R.string.widget_status_off, DashboardWidgetProvider.statusLabelRes(model(false, true, false)))
    }

    @Test fun starting_whenEnabledButNotRunning() {
        assertEquals(R.string.widget_status_starting, DashboardWidgetProvider.statusLabelRes(model(true, false, false)))
    }

    @Test fun active_whenRunning() {
        assertEquals(R.string.widget_status_active, DashboardWidgetProvider.statusLabelRes(model(true, true, false)))
    }

    @Test fun paused_whenRunningAndPaused() {
        assertEquals(R.string.widget_status_paused, DashboardWidgetProvider.statusLabelRes(model(true, true, true)))
    }
}
