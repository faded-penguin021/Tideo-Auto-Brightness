package com.tideo.autobrightness.app.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tideo.autobrightness.app.storage.powerDrawDataStore
import com.tideo.autobrightness.domain.power.PowerDrawSample
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** S14: the persisted task524 power-draw dataset round-trips and `hasData` tracks presence. */
@RunWith(RobolectricTestRunner::class)
class PowerDrawStoreTest {
    private val store = PowerDrawStore(
        ApplicationProvider.getApplicationContext<Context>().powerDrawDataStore,
    )

    @Test
    fun saveThenLoad_roundTripsAndTracksPresence() = runBlocking {
        assertTrue(store.samples.first().isEmpty(), "starts empty")
        assertFalse(store.hasData.first(), "no data initially")

        store.save(listOf(PowerDrawSample(0, 0.0, 0.0), PowerDrawSample(255, 220.0, 0.88)))

        val loaded = store.samples.first()
        assertEquals(2, loaded.size)
        assertEquals(255, loaded[1].brightness)
        assertEquals(220.0, loaded[1].currentMa, 1e-9)
        assertEquals(0.88, loaded[1].powerW, 1e-9)
        assertTrue(store.hasData.first(), "hasData true after save")

        store.clear()
        assertTrue(store.samples.first().isEmpty(), "cleared")
        assertFalse(store.hasData.first())
    }
}
