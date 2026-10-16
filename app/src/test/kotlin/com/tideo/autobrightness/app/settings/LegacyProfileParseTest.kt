package com.tideo.autobrightness.app.settings

import com.tideo.autobrightness.app.settings.TaskerLegacyProfileSerializer.LegacyProfileParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** S12.9c #4: legacy parser throws on structurally-invalid input so ProfileImportExportManager can build TotalFailure. Valid-but-empty parses to task570 defaults. */
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

    @Test
    fun `the extended Night Light flag parses from key=value and is off when absent`() {
        assertEquals(true, TaskerLegacyProfileSerializer.deserialize("%AAB_ExtendedNightLight = On").extendedNightLightEnabled)
        assertEquals(false, TaskerLegacyProfileSerializer.deserialize("%AAB_ExtendedNightLight = false").extendedNightLightEnabled)
        assertEquals(false, TaskerLegacyProfileSerializer.deserialize("%AAB_MinBright = 42").extendedNightLightEnabled)
    }

    @Test
    fun `AAB's hand-off globals parse from key=value DD070`() {
        val s = TaskerLegacyProfileSerializer.deserialize(
            "%AAB_AdaptiveOn = true\n%AAB_AdaptiveHandoffThresh = 25130.4\n%AAB_HandoffHysteresis = 0.9",
        )
        assertEquals(true, s.handoffEnabled)
        assertEquals(25_131, s.handoffLux, "DD-071: a decimal rounds up")
        assertEquals(0.9, s.handoffHysteresis)
    }

    @Test
    fun `malformed hand-off globals fall back to the defaults DD071`() {
        val s = TaskerLegacyProfileSerializer.deserialize(
            "%AAB_AdaptiveOn = maybe\n%AAB_AdaptiveHandoffThresh = Infinity\n%AAB_HandoffHysteresis = NaN",
        )
        assertEquals(AabSettings(), s)
        assertEquals(0.8, TaskerLegacyProfileSerializer.deserialize("%AAB_HandoffHysteresis = 1.5").handoffHysteresis)
    }
}
