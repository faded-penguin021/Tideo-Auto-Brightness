package com.tideo.autobrightness.app.settings

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AabSettingsMigrationTest {

    // A minimal v1 JSON: schemaVersion=1, a few explicit fields, NO new v2 fields.
    private val v1Json = """
        {
          "schemaVersion": 1,
          "serviceEnabled": true,
          "minBrightness": 12,
          "maxBrightness": 240,
          "scale": 1,
          "throttleDefaultMs": 1000,
          "debugLevel": 3
        }
    """.trimIndent()

    @Test
    fun `v1 json deserializes with new v2 fields at their defaults`() = runTest {
        val settings = AabSettingsSerializer.readFrom(v1Json.byteInputStream())

        // Pre-existing fields are read from JSON
        assertEquals(12, settings.minBrightness)
        assertEquals(240, settings.maxBrightness)
        assertEquals(3, settings.debugLevel)

        // New v2 fields absent from v1 JSON take Kotlin default values
        assertEquals(20, settings.animSteps, "animSteps should default to 20 (task570)")
        assertEquals(4.0, settings.thresholdMidpoint, "thresholdMidpoint should default to 4.0 (log10(10000))")
        assertFalse(settings.contextOverride, "contextOverride must default to false — the runtime context-lock latch starts unlatched (D-038)")
        assertEquals("Advanced Auto Brightness Setup", settings.setupTitle)
    }

    @Test
    fun `v1 json schema version is bumped to current after migration`() = runTest {
        val settings = AabSettingsSerializer.readFrom(v1Json.byteInputStream())
        assertEquals(CURRENT_SCHEMA_VERSION, settings.schemaVersion, "schemaVersion should be bumped to the current schema")
    }

    @Test
    fun `v2 json round-trips without loss`() = runTest {
        val original = AabSettings(
            minBrightness = 15,
            animSteps = 30,
            thresholdMidpoint = 3.5,
            contextOverride = false,
            setupTitle = "Custom Title",
        )
        val encoded = buildString {
            val output = java.io.ByteArrayOutputStream()
            AabSettingsSerializer.writeTo(original, output)
            append(output.toString())
        }
        val decoded = AabSettingsSerializer.readFrom(encoded.byteInputStream())
        assertEquals(original.minBrightness, decoded.minBrightness)
        assertEquals(original.animSteps, decoded.animSteps)
        assertEquals(original.thresholdMidpoint, decoded.thresholdMidpoint)
        assertEquals(original.contextOverride, decoded.contextOverride)
        assertEquals(original.setupTitle, decoded.setupTitle)
        assertEquals(CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
    }

    @Test
    fun `scale field survives int-encoded v1 json as float`() = runTest {
        // v1 stored scale as Int (e.g. 1); v2 is Float. JSON integer decodes safely to Float.
        val settings = AabSettingsSerializer.readFrom(v1Json.byteInputStream())
        assertEquals(1.0f, settings.scale, 0.001f)
    }

    // G2R-F85: v2 stored a bogus editable `thresholdDynamic`. v3 removed it. A v2 JSON still carrying
    // the key must decode cleanly (ignoreUnknownKeys drops it) and migrate to v3 — no data loss, no error.
    private val v2JsonWithThreshDynamic = """
        {
          "schemaVersion": 2,
          "serviceEnabled": true,
          "minBrightness": 14,
          "thresholdDynamic": 12,
          "animSteps": 20,
          "thresholdMidpoint": 4.0
        }
    """.trimIndent()

    @Test
    fun `v2 json with dropped thresholdDynamic key decodes and migrates to v3`() = runTest {
        val settings = AabSettingsSerializer.readFrom(v2JsonWithThreshDynamic.byteInputStream())
        assertEquals(14, settings.minBrightness, "known keys still read")
        assertEquals(3, settings.schemaVersion, "schemaVersion bumped to v3")
        assertEquals(3, CURRENT_SCHEMA_VERSION, "v3 is the current schema after F85")
    }

    @Test
    fun `migrate is idempotent on current version`() {
        val current = AabSettings()
        val migrated = AabSettingsSerializer.migrate(current)
        assertEquals(current, migrated)
    }

    @Test
    fun `throttle default corrected to 1310 for new installs`() {
        val defaults = AabSettings()
        assertEquals(1310L, defaults.throttleDefaultMs, "task570 canonical default is AnimSteps*MaxWait+10=20*65+10=1310")
    }
}
