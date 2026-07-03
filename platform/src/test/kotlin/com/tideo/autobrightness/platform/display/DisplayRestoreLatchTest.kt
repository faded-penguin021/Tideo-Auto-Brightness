package com.tideo.autobrightness.platform.display

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * D-150: the death-safe restore latch must be durable across instances (the whole point — a
 * fresh process after a death must read the pre-engage state the dead process persisted).
 */
@RunWith(RobolectricTestRunner::class)
class DisplayRestoreLatchTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun save_readBack_clear_roundTrip() {
        val latch = SharedPrefsDisplayRestoreLatch(context)
        assertNull(latch.preState("GRAYSCALE"))
        latch.save("GRAYSCALE", "OFF")
        assertEquals("OFF", latch.preState("GRAYSCALE"))
        latch.clear("GRAYSCALE")
        assertNull(latch.preState("GRAYSCALE"))
    }

    @Test
    fun preState_survivesANewInstance_processDeathSemantics() {
        SharedPrefsDisplayRestoreLatch(context).save("NIGHT_LIGHT", "1")
        // A brand-new instance (the post-death process) reads the persisted value.
        assertEquals("1", SharedPrefsDisplayRestoreLatch(context).preState("NIGHT_LIGHT"))
    }

    @Test
    fun actionsAreIndependent() {
        val latch = SharedPrefsDisplayRestoreLatch(context)
        latch.save("GRAYSCALE", "PROTANOMALY")
        latch.save("INVERSION", "0")
        latch.clear("GRAYSCALE")
        assertNull(latch.preState("GRAYSCALE"))
        assertEquals("0", latch.preState("INVERSION"))
    }
}
