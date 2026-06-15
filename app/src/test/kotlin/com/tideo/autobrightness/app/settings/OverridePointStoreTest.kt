package com.tideo.autobrightness.app.settings

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import org.junit.Test

class OverridePointStoreTest {

    private class FakeDataStore<T>(initial: T) : DataStore<T> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<T> = state
        override suspend fun updateData(transform: suspend (t: T) -> T): T {
            val updated = transform(state.value)
            state.update { updated }
            return updated
        }
    }

    private fun store() = OverridePointStore(FakeDataStore(OverridePoints()))

    @Test
    fun record_prependsNewestFirst() = runTest {
        val s = store()
        s.record(10.0, 20.0)
        s.record(30.0, 40.0)
        val points = s.points().first()
        assertEquals(2, points.size)
        assertEquals(30.0, points.first().lux, "newest point is first (task561 push at index 1)")
        assertEquals(40.0, points.first().brightness)
    }

    @Test
    fun record_capsAtMaxPoints() = runTest {
        val s = store()
        repeat(OverridePoints.MAX_POINTS + 10) { i -> s.record(i.toDouble(), i.toDouble()) }
        val points = s.points().first()
        assertEquals(OverridePoints.MAX_POINTS, points.size, "history is capped at the Tasker limit")
        // Newest first: the last recorded (largest i) is at the head.
        assertEquals((OverridePoints.MAX_POINTS + 9).toDouble(), points.first().lux)
    }

    @Test
    fun delete_removesMatchingPoint_keepingDuplicates() = runTest {
        // F36 tap-to-delete: only the first record with the matching (lux, brightness) pair is removed.
        val s = store()
        s.record(10.0, 20.0)
        s.record(30.0, 40.0)
        s.record(10.0, 20.0)
        s.delete(com.tideo.autobrightness.domain.wizard.OverridePoint(10.0, 20.0))
        val points = s.points().first()
        assertEquals(2, points.size, "exactly one matching point is deleted")
        assertEquals(1, points.count { it.lux == 10.0 && it.brightness == 20.0 }, "a duplicate survives")
    }

    @Test
    fun clear_emptiesHistory() = runTest {
        val s = store()
        s.record(1.0, 2.0)
        s.clear()
        assertEquals(emptyList(), s.points().first())
    }
}
