package com.tideo.autobrightness.app.backup

import com.tideo.autobrightness.app.settings.AabSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DB-002: Semantic test for sanitizer (runtime state, not file paths). */
class SettingsBackupSanitizerTest {

    private val json = Json { prettyPrint = true }
    private val lenientJson = Json { ignoreUnknownKeys = true }
    private val allFieldsJson = Json { encodeDefaults = true }

    private fun encodeDefaults(overrides: AabSettings.() -> AabSettings = { this }) =
        json.encodeToString(AabSettings.serializer(), AabSettings().overrides())

    private fun decode(restored: String) =
        lenientJson.decodeFromString(AabSettings.serializer(), restored)

    @Test
    fun restoredSettings_neverClaimTheServiceWasRunning() {
        val backedUp = encodeDefaults { copy(serviceEnabled = true, contextOverride = true) }

        val restored = SettingsBackupSanitizer.sanitize(backedUp)!!

        val settings = decode(restored)
        assertEquals(false, settings.serviceEnabled)
        assertEquals(false, settings.contextOverride)
    }

    @Test
    fun restoredSettings_keepTheConfigurationThatMakesBackupWorthDoing() {
        val backedUp = encodeDefaults {
            copy(serviceEnabled = true, minBrightness = 7, maxBrightness = 201, dimmingThreshold = 33)
        }

        val restored = SettingsBackupSanitizer.sanitize(backedUp)!!

        val settings = decode(restored)
        assertEquals(7, settings.minBrightness)
        assertEquals(201, settings.maxBrightness)
        assertEquals(33, settings.dimmingThreshold)
        assertEquals(false, settings.serviceEnabled)
    }

    @Test
    fun unknownFieldsSurviveSanitizing() {
        // Newer version file restored onto older: unknown fields must survive (not data loss).
        val fromNewerVersion = """{"schemaVersion":3,"serviceEnabled":true,"futureField":"keep-me"}"""

        val restored = SettingsBackupSanitizer.sanitize(fromNewerVersion)!!

        val fields = json.parseToJsonElement(restored).jsonObject
        assertEquals("keep-me", fields.getValue("futureField").jsonPrimitive.content)
        assertEquals(false, fields.getValue("serviceEnabled").jsonPrimitive.content.toBoolean())
    }

    @Test
    fun absentRuntimeFieldsAreForced_becauseAbsentMeansTheDefaultAndTheDefaultIsEnabled() {
        // serviceEnabled defaults to TRUE; store omits defaults; must force absent fields.
        val serviceRunningAtBackupTime = """{"schemaVersion":3,"minBrightness":10}"""

        val restored = SettingsBackupSanitizer.sanitize(serviceRunningAtBackupTime)!!

        assertEquals(
            false,
            decode(restored).serviceEnabled,
            "an absent serviceEnabled restored as the default `true`",
        )
        assertEquals(10, decode(restored).minBrightness)
    }

    @Test
    fun unparseableInputIsLeftAlone() {
        // Return null (don't rewrite) on unparseable input; don't destroy data.
        assertNull(SettingsBackupSanitizer.sanitize("not json at all"))
        assertNull(SettingsBackupSanitizer.sanitize("[1,2,3]"))
    }

    @Test
    fun everyResetTargetIsARealSettingsField() {
        // Guard against rename silently turning reset into no-op.
        val allFields = allFieldsJson.encodeToString(AabSettings.serializer(), AabSettings())
        val fields = json.parseToJsonElement(allFields).jsonObject
        for (field in SettingsBackupSanitizer.RUNTIME_FIELD_RESETS.keys) {
            assertTrue(field in fields, "$field is not a field of AabSettings any more")
        }
    }
}
