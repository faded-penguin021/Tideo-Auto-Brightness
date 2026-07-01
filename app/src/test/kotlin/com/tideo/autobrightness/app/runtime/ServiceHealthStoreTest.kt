package com.tideo.autobrightness.app.runtime

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tideo.autobrightness.app.storage.serviceHealthDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** H3 glue-seam audit: the degraded-mode latch (set on FGS start denial) had no test. */
@RunWith(RobolectricTestRunner::class)
class ServiceHealthStoreTest {
    private val store = ServiceHealthStore(
        ApplicationProvider.getApplicationContext<Context>().serviceHealthDataStore,
    )

    @Test
    fun degradedLatch_setsWithReason_andClearsOnNextApply() = runBlocking {
        assertFalse(store.telemetry.first().degradedMode, "starts healthy")

        store.markDegraded("Foreground start blocked: ForegroundServiceStartNotAllowedException")
        val degraded = store.telemetry.first()
        assertTrue(degraded.degradedMode)
        assertEquals(
            "Foreground start blocked: ForegroundServiceStartNotAllowedException",
            degraded.degradedReason,
        )

        // A successful apply is proof of life: it must CLEAR the latch, not just timestamp.
        store.markApplied(42_000L)
        val healthy = store.telemetry.first()
        assertFalse(healthy.degradedMode)
        assertNull(healthy.degradedReason)
        assertEquals(42_000L, healthy.lastApplyTimestampMs)
    }

    @Test
    fun sensorHeartbeat_recordsTimestamp_withoutTouchingDegradedState() = runBlocking {
        store.markDegraded("still degraded")
        store.markSensorSampled(7_000L)
        val telemetry = store.telemetry.first()
        assertEquals(7_000L, telemetry.lastSensorTimestampMs)
        assertTrue(telemetry.degradedMode, "a sensor sample alone is not proof of applying")
    }
}
