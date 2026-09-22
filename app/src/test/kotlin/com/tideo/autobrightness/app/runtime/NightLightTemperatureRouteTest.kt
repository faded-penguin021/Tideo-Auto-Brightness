package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.runtime.NightLightTemperatureRoute.Verdict
import com.tideo.autobrightness.platform.display.DaltonizerMode
import com.tideo.autobrightness.platform.display.NightDisplayServiceBridge
import com.tideo.autobrightness.platform.display.NightLightAutoMode
import com.tideo.autobrightness.platform.display.SecureDisplayController
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NightLightTemperatureRouteTest {

    private class KeyDisplay : SecureDisplayController {
        var key: Int? = null
        var failWrites = false
        var competing: Int? = null
        override val nightLightAvailable = true
        override fun readNightLight() = false
        override fun setNightLight(on: Boolean) = Result.success(Unit)
        override fun readNightLightTemperature() = competing ?: key
        override fun setNightLightTemperature(kelvin: Int): Result<Unit> {
            if (failWrites) return Result.failure(SecurityException("refused"))
            key = kelvin
            return Result.success(Unit)
        }
        override fun readNightLightAutoMode() = NightLightAutoMode.MANUAL
        override fun readDaltonizer() = DaltonizerMode.OFF
        override fun setDaltonizer(mode: DaltonizerMode) = Result.success(Unit)
        override fun readInversion() = false
        override fun setInversion(on: Boolean) = Result.success(Unit)
        override val alwaysOnDisplayAvailable = false
        override fun readAlwaysOnDisplay() = false
        override fun setAlwaysOnDisplay(on: Boolean) = Result.success(Unit)
        override fun readStayAwakePlugged() = false
        override fun setStayAwakePlugged(on: Boolean) = Result.success(Unit)
        override val hdrForceSdrAvailable = false
        override fun readHdrForceSdr() = false
        override fun setHdrForceSdr(on: Boolean) = Result.success(Unit)
    }

    private class FakeService(private val display: KeyDisplay) : NightDisplayServiceBridge {
        var observesKey = true
        var reachable = true
        var service = 3_730
        val sets = mutableListOf<Int>()
        var reads = 0

        override suspend fun readKelvin(): Int? {
            reads++
            if (!reachable) return null
            if (observesKey) display.key?.let { service = it }
            return service
        }

        override suspend fun setKelvin(kelvin: Int, quick: Boolean): Int? {
            if (!reachable) return null
            sets += kelvin
            service = kelvin
            return kelvin
        }
    }

    private class Harness(persisted: Boolean = false) {
        val display = KeyDisplay()
        val service = FakeService(display)
        var stored = persisted
        var now = 0L
        val route = NightLightTemperatureRoute(
            display = display,
            bridge = service,
            isNotHonoured = { stored },
            markNotHonoured = { stored = true },
            nowMs = { now },
        )
    }

    @Test
    fun `no bridge is exactly the settings write`() = runTest {
        val display = KeyDisplay()
        val route = NightLightTemperatureRoute(display)
        route.write(3_000)
        assertEquals(3_000, display.key)
        assertEquals(Verdict.UNKNOWN, route.verdict)
    }

    @Test
    fun `an honouring service is observed once and never written`() = runTest {
        val h = Harness()
        h.route.write(3_000)
        assertEquals(Verdict.HONOURED, h.route.verdict)
        h.route.write(3_100)
        assertEquals(1, h.service.reads, "no re-probe inside the recheck window")
        assertTrue(h.service.sets.isEmpty())
        assertEquals(false, h.stored)
    }

    @Test
    fun `an ignoring service needs two distinct Kelvins before it is written`() = runTest {
        val h = Harness().apply { service.observesKey = false }
        h.route.write(3_000)
        h.route.write(3_000)
        assertEquals(Verdict.UNKNOWN, h.route.verdict, "a repeat of the same Kelvin is not new evidence")
        h.route.write(3_100)
        assertEquals(Verdict.NOT_HONOURED, h.route.verdict)
        assertTrue(h.stored)
        assertEquals(listOf(3_100), h.service.sets)
        h.route.write(3_200)
        assertEquals(listOf(3_100, 3_200), h.service.sets)
        assertEquals(3_200, h.display.key, "the settings row is still written")
    }

    @Test
    fun `an unreachable service is unknown, never a verdict`() = runTest {
        val h = Harness().apply { service.reachable = false }
        repeat(3) { h.route.write(3_000 + it * 100) }
        assertEquals(Verdict.UNKNOWN, h.route.verdict)
        assertEquals(false, h.stored)
    }

    @Test
    fun `a mismatch after a match drops back to probing every write`() = runTest {
        val h = Harness().apply { service.observesKey = false }
        h.route.write(3_000)
        h.service.observesKey = true
        h.route.write(3_100)
        h.service.observesKey = false
        h.now += NightLightTemperatureRoute.RECHECK_MS
        h.route.write(3_200)
        assertEquals(Verdict.UNKNOWN, h.route.verdict)
        h.route.write(3_300)
        assertEquals(Verdict.NOT_HONOURED, h.route.verdict)
    }

    @Test
    fun `a persisted verdict routes without probing and reads the service`() = runTest {
        val h = Harness(persisted = true).apply { service.observesKey = false }
        h.display.key = 3_000
        assertEquals(3_730, h.route.readDeviceKelvin().getOrNull())
        h.route.write(2_800, probe = false)
        assertEquals(listOf(2_800), h.service.sets)
    }

    @Test
    fun `teardown writes never probe`() = runTest {
        val h = Harness().apply { service.observesKey = false }
        h.route.write(3_000, probe = false)
        h.route.write(3_100, probe = false)
        assertEquals(0, h.service.reads)
        assertEquals(Verdict.UNKNOWN, h.route.verdict)
    }

    @Test
    fun `an ignored key with no route is a failed write and an unreadable device`() = runTest {
        val h = Harness(persisted = true).apply { service.reachable = false }
        h.display.key = 3_000
        assertTrue(h.route.write(2_800).isFailure, "the service never changed")
        assertTrue(h.route.readDeviceKelvin().isFailure, "the stale key is not the device's Kelvin")
    }

    @Test
    fun `a key that no longer holds our write is not evidence`() = runTest {
        val h = Harness().apply { service.observesKey = false }
        h.display.competing = 9_999
        h.route.write(3_000)
        h.route.write(3_100)
        assertEquals(Verdict.UNKNOWN, h.route.verdict)
        assertEquals(false, h.stored)
        assertEquals(0, h.service.reads)
    }

    @Test
    fun `a refused settings write stops before the service`() = runTest {
        val h = Harness(persisted = true).apply { display.failWrites = true }
        assertTrue(h.route.write(3_000).isFailure)
        assertTrue(h.service.sets.isEmpty())
    }
}
