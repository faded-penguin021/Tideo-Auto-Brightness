package com.tideo.autobrightness.app.runtime

import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class AabFlashTest {

    private class Recorder : AabFlash.Presenter {
        val shown = mutableListOf<String>()
        var hidden = 0
        override fun show(text: String) { shown += text }
        override fun hide() { hidden++ }
    }

    @After fun tearDown() = AabFlash.registerForeground(null)

    @Test fun `the old host's disposal leaves the new host registered`() {
        val old = Recorder()
        val new = Recorder()
        AabFlash.registerForeground(old)
        AabFlash.registerForeground(new)
        AabFlash.unregisterForeground(old)
        AabFlash.show(RuntimeEnvironment.getApplication(), "Applied")
        assertEquals(listOf("Applied"), new.shown)
        assertEquals(emptyList(), old.shown)
    }

    @Test fun `the current host's disposal clears and hides it`() {
        val host = Recorder()
        AabFlash.registerForeground(host)
        AabFlash.unregisterForeground(host)
        assertEquals(1, host.hidden)
        AabFlash.show(RuntimeEnvironment.getApplication(), "Applied")
        assertEquals(emptyList(), host.shown)
    }
}
