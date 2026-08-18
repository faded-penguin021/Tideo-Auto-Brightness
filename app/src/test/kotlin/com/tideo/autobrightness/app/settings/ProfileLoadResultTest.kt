package com.tideo.autobrightness.app.settings

import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** S12.9c #3: ProfileLoadResult typing across three branches (Success/LegacyFallback/TotalFailure). */
@RunWith(RobolectricTestRunner::class)
class ProfileLoadResultTest {

    private val manager = ProfileImportExportManager(ApplicationProvider.getApplicationContext())

    @Test
    fun `normal app export imports through bounded reader`() = runBlocking {
        val name = "bounded-reader-round-trip"
        manager.exportToAppPrivate(name, AabSettings(minBrightness = 17))

        val result = manager.importFromAppPrivate(name)

        assertTrue(result is ProfileLoadResult.Success)
        assertEquals(17, result.settings.minBrightness)
    }

    @Test
    fun `our export format is a Success`() {
        // The app's own export wraps settings in an AabProfilePayload { schemaVersion, settings }.
        val payload = """{ "schemaVersion": 3, "settings": { "minBrightness": 7 } }"""
        val result = manager.decodePayload(payload)
        assertTrue(result is ProfileLoadResult.Success, "expected Success, got $result")
        assertEquals(7, (result as ProfileLoadResult.Success).settings.minBrightness)
    }

    @Test
    fun `a Tasker nested config is a LegacyFallback`() {
        val taskerConfig = """{ "general": { "z1_end": 50.0 } }"""
        val result = manager.readAndDecode(ByteArrayInputStream(taskerConfig.encodeToByteArray()))
        assertTrue(result is ProfileLoadResult.LegacyFallback, "expected LegacyFallback, got $result")
        assertEquals(50, (result as ProfileLoadResult.LegacyFallback).settings.zone1End)
        assertTrue(result.jsonError.isNotEmpty(), "the JSON error should be recorded")
    }

    @Test
    fun `input exactly at encoded limit is accepted by the reader`() {
        val prefix = """{ "schemaVersion": 3, "settings": { "minBrightness": 19 } }"""
        val bytes = prefix.padEnd(ProfileImportExportManager.MAX_ENCODED_PROFILE_BYTES).encodeToByteArray()

        val result = manager.readAndDecode(ByteArrayInputStream(bytes))

        assertTrue(result is ProfileLoadResult.Success)
        assertEquals(19, result.settings.minBrightness)
    }

    @Test
    fun `input one byte over encoded limit is rejected`() {
        val bytes = ByteArray(ProfileImportExportManager.MAX_ENCODED_PROFILE_BYTES + 1) { ' '.code.toByte() }

        assertEquals(ProfileLoadResult.TooLarge, manager.readAndDecode(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `absent and inaccurate declared sizes cannot bypass streaming bound`() {
        val normal = """{ "schemaVersion": 3, "settings": { "minBrightness": 23 } }""".encodeToByteArray()
        assertTrue(manager.readAndDecode(ByteArrayInputStream(normal), declaredSize = null) is ProfileLoadResult.Success)

        val oversized = ByteArray(ProfileImportExportManager.MAX_ENCODED_PROFILE_BYTES + 1)
        assertEquals(
            ProfileLoadResult.TooLarge,
            manager.readAndDecode(ByteArrayInputStream(oversized), declaredSize = 1),
        )
    }

    @Test
    fun `malformed UTF-8 and stream failures are typed read failures`() {
        assertEquals(
            ProfileLoadResult.ReadFailure,
            manager.readAndDecode(ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28))),
        )
        val failingStream = object : InputStream() {
            override fun read(): Int = throw IOException("private provider detail")
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = read()
        }
        assertEquals(ProfileLoadResult.ReadFailure, manager.readAndDecode(failingStream))
    }

    @Test
    fun `zero byte bulk reads cannot stall or bypass the cap`() {
        var remaining = ProfileImportExportManager.MAX_ENCODED_PROFILE_BYTES + 1
        val hostile = object : InputStream() {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = 0
            override fun read(): Int = if (remaining-- > 0) ' '.code else -1
        }
        assertEquals(ProfileLoadResult.TooLarge, manager.readAndDecode(hostile))
    }

    @Test
    fun `native payload rejects unsupported schemas unknown fields and excessive structure`() {
        listOf(
            """{ "schemaVersion": 999, "settings": {} }""",
            """{ "schemaVersion": 3, "unexpected": true, "settings": {} }""",
            """{ "schemaVersion": 3, "settings": { "unexpected": true } }""",
            """{ "schemaVersion": 3, "schemaVersion": 3, "settings": {} }""",
            "[".repeat(ImportStructureGuard.MAX_DEPTH + 1) + "0" +
                "]".repeat(ImportStructureGuard.MAX_DEPTH + 1),
        ).forEach { input ->
            assertTrue(manager.decodePayload(input) is ProfileLoadResult.TotalFailure, input.take(80))
        }
    }

    @Test
    fun `private filename normalization rejects dot aliases and avoids collisions`() {
        val dot = manager.sanitizeFileName(".")
        val dotDot = manager.sanitizeFileName("..")
        val blank = manager.sanitizeFileName("   ")
        assertTrue(dot.startsWith("profile-") && dot.endsWith(".json"))
        assertTrue(dotDot.startsWith("profile-") && dotDot.endsWith(".json"))
        assertTrue(blank.startsWith("profile-") && blank.endsWith(".json"))
        assertNotEquals(dot, dotDot)
        assertNotEquals(manager.sanitizeFileName("a?"), manager.sanitizeFileName("a!"))
    }

    @Test
    fun `a flat AAB key=value dump is a LegacyFallback`() {
        val result = manager.decodePayload("%AAB_MinBright = 22")
        assertTrue(result is ProfileLoadResult.LegacyFallback, "expected LegacyFallback, got $result")
        assertEquals(22, (result as ProfileLoadResult.LegacyFallback).settings.minBrightness)
    }

    @Test
    fun `garbage is a TotalFailure with both errors`() {
        val result = manager.decodePayload("this is not a profile at all")
        assertTrue(result is ProfileLoadResult.TotalFailure, "expected TotalFailure, got $result")
        result as ProfileLoadResult.TotalFailure
        assertTrue(result.jsonError.isNotEmpty())
        assertTrue(result.legacyError.isNotEmpty())
    }

    @Test
    fun `display toggle fields round-trip through the export format D151`() {
        // The D-151 profile fields ride the same AabSettings serializer as everything else —
        // including the nullable temperature (null = "device default" must survive a round-trip
        // as null, and a set value as itself).
        val withOpinion = """
            { "schemaVersion": 3, "settings": {
                "nightLightEnabled": true, "nightLightTemperature": 2700,
                "nightLightCircadianEnabled": true,
                "daltonizerMode": "GRAYSCALE", "inversionEnabled": true,
                "alwaysOnDisplayEnabled": true, "stayAwakeChargingEnabled": true,
                "hdrForceSdrEnabled": true } }
        """.trimIndent()
        val loaded = (manager.decodePayload(withOpinion) as ProfileLoadResult.Success).settings
        assertEquals(true, loaded.nightLightEnabled)
        assertEquals(2_700, loaded.nightLightTemperature)
        assertEquals(true, loaded.nightLightCircadianEnabled)
        assertEquals("GRAYSCALE", loaded.daltonizerMode)
        assertEquals(true, loaded.inversionEnabled)
        assertEquals(true, loaded.alwaysOnDisplayEnabled)
        assertEquals(true, loaded.stayAwakeChargingEnabled)
        assertEquals(true, loaded.hdrForceSdrEnabled)

        val withoutOpinion = """{ "schemaVersion": 3, "settings": { "minBrightness": 7 } }"""
        val defaults = (manager.decodePayload(withoutOpinion) as ProfileLoadResult.Success).settings
        assertEquals(null, defaults.nightLightTemperature, "absent temperature stays 'device default'")
        assertEquals(DALTONIZER_OFF, defaults.daltonizerMode)
        assertEquals(false, defaults.alwaysOnDisplayEnabled)
    }

    @Test
    fun `imported daltonizer garbage validates back to OFF D151`() {
        // decodePayload runs validate() — an unknown mode string from a newer schema or a
        // hand-edited file must not poison the profile (the D-146 spirit for strings).
        val payload = """{ "schemaVersion": 3, "settings": { "daltonizerMode": "SEPIA" } }"""
        val loaded = (manager.decodePayload(payload) as ProfileLoadResult.Success).settings
        assertEquals(DALTONIZER_OFF, loaded.daltonizerMode)
    }
}
