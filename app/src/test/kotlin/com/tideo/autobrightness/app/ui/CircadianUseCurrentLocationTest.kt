package com.tideo.autobrightness.app.ui

import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.ui.screens.acquireCurrentLocation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CircadianUseCurrentLocationTest {

    private val toasts = mutableListOf<Int>()
    private val filled = mutableListOf<Pair<Double, Double>>()

    private suspend fun acquire(servicesOn: Boolean = true, fresh: suspend () -> Pair<Double, Double>?) =
        acquireCurrentLocation(
            servicesOn = { servicesOn },
            freshLatLon = fresh,
            fill = { la, lo -> filled += la to lo },
            toast = { id, _ -> toasts += id },
        )

    @Test
    fun `a cancelled acquisition stays silent instead of reporting no location`() = runTest {
        val job = launch { acquire { awaitCancellation() } }
        runCurrent()
        assertTrue(R.string.toast_acquiring_location in toasts, "the wait must still be announced")

        job.cancelAndJoin()

        assertFalse(
            R.string.toast_acquire_location_failed in toasts,
            "leaving the screen mid-fix is not a failed fix; runCatching swallowing the " +
                "CancellationException made the two indistinguishable to the owner",
        )
        assertTrue(filled.isEmpty())
    }

    @Test
    fun `an acquired location fills the fields and says nothing more`() = runTest {
        acquire { 52.37021 to 4.89516 }

        assertEquals(listOf(52.37021 to 4.89516), filled)
        assertEquals(listOf(R.string.toast_acquiring_location), toasts)
    }

    @Test
    fun `a genuine miss still reports the failure`() = runTest {
        acquire { null }

        assertTrue(filled.isEmpty())
        assertEquals(listOf(R.string.toast_acquiring_location, R.string.toast_acquire_location_failed), toasts)
    }

    @Test
    fun `an ordinary failure is caught, not propagated`() = runTest {
        val reached = CompletableDeferred<Unit>()
        acquire { throw IllegalStateException("provider blew up") }
        reached.complete(Unit)

        assertTrue(reached.isCompleted)
        assertEquals(listOf(R.string.toast_acquiring_location, R.string.toast_acquire_location_failed), toasts)
    }

    @Test
    fun `location services off names the switch instead of promising a wait`() = runTest {
        acquire(servicesOn = false) { null }

        assertEquals(
            listOf(R.string.toast_location_services_off, R.string.toast_acquire_location_failed),
            toasts,
        )
    }
}
