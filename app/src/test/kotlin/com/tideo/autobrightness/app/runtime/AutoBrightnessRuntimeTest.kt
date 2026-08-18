package com.tideo.autobrightness.app.runtime

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.Test
import kotlin.test.assertEquals

/** H3 glue-seam audit: service-action dispatch for Dashboard buttons, QS tile, notification actions. */
@RunWith(RobolectricTestRunner::class)
class AutoBrightnessRuntimeTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    private fun nextService(): Intent = shadowOf(application).nextStartedService

    @Test
    fun pause_dispatchesPauseActionToTheMonitoringService() {
        AutoBrightnessRuntime.pause(application)
        val intent = nextService()
        assertEquals(AmbientMonitoringService::class.java.name, intent.component?.className)
        assertEquals(AmbientMonitoringService.ACTION_PAUSE, intent.action)
    }

    @Test
    fun resume_dispatchesResumeAction() {
        AutoBrightnessRuntime.resume(application)
        assertEquals(AmbientMonitoringService.ACTION_RESUME, nextService().action)
    }

    @Test
    fun reapply_dispatchesReapplyAction() {
        AutoBrightnessRuntime.reapply(application)
        assertEquals(AmbientMonitoringService.ACTION_REAPPLY, nextService().action)
    }

    @Test
    fun startMonitoring_dispatchesStartAction_withReasonExtra() {
        AutoBrightnessRuntime.startMonitoring(application, "boot")
        val intent = nextService()
        assertEquals(AmbientMonitoringService.ACTION_START, intent.action)
        assertEquals("boot", intent.getStringExtra(AmbientMonitoringService.EXTRA_REASON))
    }

    @Test
    fun stopMonitoring_stopsTheMonitoringService() {
        AutoBrightnessRuntime.stopMonitoring(application)
        val stopped = shadowOf(application).nextStoppedService
        assertEquals(AmbientMonitoringService::class.java.name, stopped.component?.className)
    }
}
