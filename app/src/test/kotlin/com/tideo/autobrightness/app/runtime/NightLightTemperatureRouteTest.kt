package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.runtime.NightLightTemperatureRoute.Verdict
import com.tideo.autobrightness.platform.display.DaltonizerMode
import com.tideo.autobrightness.platform.display.NightDisplayServiceBridge
import com.tideo.autobrightness.platform.display.NightLightAutoMode
import com.tideo.autobrightness.platform.display.SecureDisplayController
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NightLightTemperatureRouteTest {

    private class KeyDisplay : SecureDisplayController {
        var key: Int? = null
        val history = mutableListOf<Int>()
        var failWrites = false
        var competing: Int? = null
        override val nightLightAvailable = true
        override fun readNightLight() = false
        override fun setNightLight(on: Boolean) = Result.success(Unit)
        override fun readNightLightTemperature() = competing ?: key
        override fun setNightLightTemperature(kelvin: Int): Result<Unit> {
            if (failWrites) return Result.failure(SecurityException("refused"))
            key = kelvin
            history += kelvin
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
            bridgeOutOfRange = AtomicBoolean(false),
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
    fun `an extended write goes to the service unprobed, so a clamping getter never latches a verdict`() = runTest {
        val h = Harness().apply { service.observesKey = false }
        repeat(3) { assertEquals(700 + it, h.route.writeClamped(700 + it, extended = true).getOrThrow()) }
        assertEquals(listOf(700, 701, 702), h.service.sets)
        assertEquals(702, h.display.key, "the settings row follows the service")
        assertEquals(0, h.service.reads)
        assertEquals(Verdict.UNKNOWN, h.route.verdict)
        assertEquals(false, h.stored)
    }

    @Test
    fun `the extended band is 686 to 7308 and the normal one the device's`() = runTest {
        val h = Harness()
        assertEquals(686, h.route.writeClamped(500, extended = true).getOrThrow())
        assertEquals(7_308, h.route.writeClamped(9_000, extended = true).getOrThrow())
        assertEquals(4_082, h.route.writeClamped(5_000, extended = false).getOrThrow())
        assertEquals(2_596, h.route.writeClamped(700, extended = false).getOrThrow())
        assertEquals(
            listOf(686, 7_308, 4_082), h.service.sets,
            "extended writes reach the service, and so does the one write that leaves an out-of-range Kelvin",
        )
    }

    @Test
    fun `leaving an out-of-range binder Kelvin goes through the service once, unprobed`() = runTest {
        val h = Harness()
        h.route.writeClamped(700, extended = true)
        assertEquals(2_596, h.route.writeClamped(1_000, extended = false).getOrThrow())
        assertEquals(listOf(700, 2_596), h.service.sets, "a key write of 2596 is a no-op on a service holding raw 700")
        assertEquals(0, h.service.reads, "no probe: its clamped getter would read 2596 and latch HONOURED")
        h.route.writeClamped(3_000, extended = false)
        assertEquals(listOf(700, 2_596), h.service.sets, "back to the key path once the service is in range")
    }

    @Test
    fun `leaving an out-of-range binder Kelvin with the service gone writes the key but never probes`() = runTest {
        val h = Harness()
        h.route.writeClamped(700, extended = true)
        h.service.reachable = false
        assertEquals(2_596, h.route.writeClamped(1_000, extended = false).getOrThrow())
        assertEquals(0, h.service.reads, "a clamped getter would read 2596 and latch HONOURED")
        assertEquals(Verdict.UNKNOWN, h.route.verdict)
    }

    @Test
    fun `an in-range extended Kelvin leaves the key path alone afterwards`() = runTest {
        val h = Harness()
        h.route.writeClamped(3_000, extended = true)
        h.route.writeClamped(2_596, extended = false)
        assertEquals(listOf(3_000), h.service.sets)
    }

    @Test
    fun `extended with no route lands the device clamp on the key, never the extended value`() = runTest {
        val h = Harness().apply { service.reachable = false }
        assertEquals(2_596, h.route.writeClamped(700, extended = true).getOrThrow())
        assertEquals(listOf(2_596), h.display.history)
        assertEquals(2_596, h.route.writeClamped(700, extended = true, probe = false, current = 2_596).getOrThrow())
        assertEquals(listOf(2_596), h.display.history, "a tick that cannot reach the service does not churn the key")
    }

    @Test
    fun `the anchor read after an extended write is the key, not a clamped service getter`() = runTest {
        val h = Harness().apply { service.observesKey = false }
        h.route.writeClamped(700, extended = true)
        h.service.service = 2_596
        assertEquals(700, h.route.readDeviceKelvin().getOrThrow())
    }

    @Test
    fun `a refused settings write stops before the service`() = runTest {
        val h = Harness(persisted = true).apply { display.failWrites = true }
        assertTrue(h.route.write(3_000).isFailure)
        assertTrue(h.service.sets.isEmpty())
        assertTrue(
            h.route.writeClamped(3_000, extended = true).isFailure,
            "extended reaches the service first, but a refused key is still a failed write",
        )
    }
}
