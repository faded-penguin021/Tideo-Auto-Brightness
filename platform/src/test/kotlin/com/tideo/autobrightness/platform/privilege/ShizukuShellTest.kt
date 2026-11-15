package com.tideo.autobrightness.platform.privilege

import android.content.Context
import android.content.ServiceConnection
import androidx.test.core.app.ApplicationProvider
import com.tideo.autobrightness.platform.brightness.DumpOutput
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ShizukuShellTest {
    private lateinit var context: Context
    private lateinit var original: ShizukuShell.UserServiceBinding
    private val fake = FakeBinding()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        original = ShizukuShell.binding
        ShizukuShell.binding = fake
    }

    @After
    fun tearDown() {
        fake.service.alive = false // the next test's first call releases this one's binding
        ShizukuShell.binding = original
    }

    @Test
    fun backToBackCalls_bothAnswerOnOneBind() = runBlocking { // real time: the worker thread must not race a virtual clock
        assertEquals(4082, ShizukuShell.readNightDisplayTemperature(context))
        assertEquals(7308, ShizukuShell.setNightDisplayTemperature(context, 7308))
        assertTrue(fake.bound.single() !in fake.unbound)
    }

    @Test
    fun deadBinder_nextCallBindsAgain() = runBlocking {
        assertEquals(4082, ShizukuShell.readNightDisplayTemperature(context))
        val first = fake.bound.last()
        fake.service.alive = false
        fake.service = FakeService()
        assertEquals(4082, ShizukuShell.readNightDisplayTemperature(context))
        assertEquals(2, fake.bound.size)
        assertTrue(first in fake.unbound)
    }

    @Test
    fun bindTimeout_unbindsAndReturnsNull() = runBlocking {
        fake.connects = false
        assertNull(ShizukuShell.readNightDisplayTemperature(context))
        assertTrue(fake.bound.last() in fake.unbound)
        fake.connects = true
        assertEquals(4082, ShizukuShell.readNightDisplayTemperature(context))
    }

    @Test
    fun displayRead_reportsLinesFailureAndUnavailable_andReleasesWhenUnusable() = runBlocking {
        fake.service.lines = "mScreenBrightness=0.5"
        assertEquals(DumpOutput.Lines("mScreenBrightness=0.5"), ShizukuShell.readDisplayBrightness(context))
        fake.service.lines = null
        assertEquals(DumpOutput.Failed, ShizukuShell.readDisplayBrightness(context))
        val held = fake.bound.last()
        assertTrue(held !in fake.unbound, "a null answer keeps the helper (DE-001)")
        fake.usable = false
        assertEquals(DumpOutput.Unavailable, ShizukuShell.readDisplayBrightness(context))
        assertTrue(held in fake.unbound, "an unusable Shizuku releases the held helper (DE-001)")
        fake.usable = true
    }

    /** DE-001, as in Shizuku 13.1.5: a bind right after a `remove = true` unbind of a live helper never connects. */
    private class FakeBinding : ShizukuShell.UserServiceBinding {
        var service = FakeService()
        var connects = true
        val bound = mutableListOf<ServiceConnection>()
        val unbound = mutableListOf<ServiceConnection>()
        private val connected = mutableMapOf<ServiceConnection, FakeService>()
        private var loseNextBind = false

        var usable = true

        override fun isUsable() = usable

        override fun bind(context: Context, connection: ServiceConnection) {
            bound += connection
            if (loseNextBind) {
                loseNextBind = false
                return
            }
            if (!connects) return
            connected[connection] = service
            connection.onServiceConnected(null, service)
        }

        override fun unbind(context: Context, connection: ServiceConnection) {
            unbound += connection
            if (connected[connection]?.alive == true) loseNextBind = true
        }
    }

    private class FakeService : IShizukuUserService.Stub() {
        var alive = true

        override fun isBinderAlive() = alive
        override fun pingBinder(): Boolean = error("DE-004: a blocking IPC outside the cancellable worker")
        override fun destroy() = Unit
        override fun grantWriteSecureSettings() = false
        override fun wifiStatus(): String? = null
        override fun readForceDark(): String? = null
        override fun setForceDark(enabled: Boolean): String? = null
        override fun readNightDisplayTemperature() = 4082
        override fun setNightDisplayTemperature(kelvin: Int) = kelvin
        var lines: String? = null
        override fun displayBrightnessLines(): String? = lines
    }
}
