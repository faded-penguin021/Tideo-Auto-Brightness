package com.tideo.autobrightness.app.settings

import com.tideo.autobrightness.app.settings.TaskerLegacyProfileSerializer.LegacyProfileParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * S12.9c #4: the legacy parser throws on structurally-invalid input so [ProfileImportExportManager]
 * can build a `ProfileLoadResult.TotalFailure`. A valid-but-empty profile is NOT an error — it parses
 * to the task570 defaults.
 */
class LegacyProfileParseTest {

    @Test
    fun `empty input throws`() {
        assertFailsWith<LegacyProfileParseException> { TaskerLegacyProfileSerializer.deserialize("") }
        assertFailsWith<LegacyProfileParseException> { TaskerLegacyProfileSerializer.deserialize("   \n  ") }
    }

    @Test
    fun `random non-profile text throws`() {
        assertFailsWith<LegacyProfileParseException> {
            TaskerLegacyProfileSerializer.deserialize("the quick brown fox")
        }
        assertFailsWith<LegacyProfileParseException> {
            TaskerLegacyProfileSerializer.deserialize("name=value\nother=thing")
        }
    }

    @Test
    fun `empty JSON object parses to defaults (no inheritance, not an error)`() {
        val s = TaskerLegacyProfileSerializer.deserialize("{}")
        assertEquals(AabSettings(), s)
    }

    @Test
    fun `a JSON config with only meta parses to defaults`() {
        val s = TaskerLegacyProfileSerializer.deserialize("""{ "meta": { "name": "x" } }""")
        assertEquals(AabSettings().minBrightness, s.minBrightness)
    }

    @Test
    fun `a single valid AAB key=value line parses`() {
        val s = TaskerLegacyProfileSerializer.deserialize("%AAB_MinBright = 42")
        assertEquals(42, s.minBrightness)
    }
}
