package com.tideo.autobrightness.app.backup

import com.tideo.autobrightness.app.settings.AabSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * DB-002. The point of these is SEMANTIC: the old backup test pinned the file paths in the
 * allowlist, which locks in whatever the allowlist happens to say — including a mistake. These
 * assert what a restored settings file may claim about runtime state.
 */
class SettingsBackupSanitizerTest {

    private val json = Json { prettyPrint = true }

    private fun encodeDefaults(overrides: AabSettings.() -> AabSettings = { this }) =
        json.encodeToString(AabSettings.serializer(), AabSettings().overrides())

    @Test
    fun restoredSettings_neverClaimTheServiceWasRunning() {
        val backedUp = encodeDefaults { copy(serviceEnabled = true, contextOverride = true) }

        val restored = SettingsBackupSanitizer.sanitize(backedUp)!!

        val settings = Json { ignoreUnknownKeys = true }
            .decodeFromString(AabSettings.serializer(), restored)
        assertEquals(false, settings.serviceEnabled)
        assertEquals(false, settings.contextOverride)
    }

    @Test
    fun restoredSettings_keepTheConfigurationThatMakesBackupWorthDoing() {
        val backedUp = encodeDefaults {
            copy(serviceEnabled = true, minBrightness = 7, maxBrightness = 201, dimmingThreshold = 33)
        }

        val restored = SettingsBackupSanitizer.sanitize(backedUp)!!

        // Round-trip through the real serializer: the restored file must still parse as settings.
        val settings = Json { ignoreUnknownKeys = true }
            .decodeFromString(AabSettings.serializer(), restored)
        assertEquals(7, settings.minBrightness)
        assertEquals(201, settings.maxBrightness)
        assertEquals(33, settings.dimmingThreshold)
        assertEquals(false, settings.serviceEnabled)
    }

    @Test
    fun unknownFieldsSurviveSanitizing() {
        // A file written by a newer version, restored onto an older one. Dropping fields we do not
        // recognise would be data loss dressed up as a privacy control.
        val fromNewerVersion = """{"schemaVersion":3,"serviceEnabled":true,"futureField":"keep-me"}"""

        val restored = SettingsBackupSanitizer.sanitize(fromNewerVersion)!!

        val fields = json.parseToJsonElement(restored).jsonObject
        assertEquals("keep-me", fields.getValue("futureField").jsonPrimitive.content)
        assertEquals(false, fields.getValue("serviceEnabled").jsonPrimitive.content.toBoolean())
    }

    @Test
    fun absentRuntimeFieldsAreForced_becauseAbsentMeansTheDefaultAndTheDefaultIsEnabled() {
        // The whole reason this is not "reset the keys that are present": AabSettings.serviceEnabled
        // defaults to TRUE and the store omits default-valued fields, so the backup of a device with
        // the service running contains no serviceEnabled key at all. Restoring that file without
        // forcing the field reads back `true` from the data class default — the exact stale-runtime
        // state this sanitizer exists to stop.
        val serviceRunningAtBackupTime = """{"schemaVersion":3,"minBrightness":10}"""

        val restored = SettingsBackupSanitizer.sanitize(serviceRunningAtBackupTime)!!

        assertEquals(
            false,
            Json { ignoreUnknownKeys = true }
                .decodeFromString(AabSettings.serializer(), restored).serviceEnabled,
            "an absent serviceEnabled restored as the default `true`",
        )
        assertEquals(10, Json { ignoreUnknownKeys = true }
            .decodeFromString(AabSettings.serializer(), restored).minBrightness)
    }

    @Test
    fun unparseableInputIsLeftAlone() {
        // Returning null means "do not rewrite the file" — clobbering an unparseable restore would
        // destroy data the app's own tolerant serializer might still recover.
        assertNull(SettingsBackupSanitizer.sanitize("not json at all"))
        assertNull(SettingsBackupSanitizer.sanitize("[1,2,3]"))
    }

    @Test
    fun everyResetTargetIsARealSettingsField() {
        // Guards against a rename silently turning a reset into a no-op: the sanitizer would keep
        // "working" while the field it was meant to clear travelled untouched.
        // Encode with defaults ON so every field is present: the production store omits defaults,
        // which is exactly why this check cannot use the production encoder.
        val allFields = Json { encodeDefaults = true }
            .encodeToString(AabSettings.serializer(), AabSettings())
        val fields = json.parseToJsonElement(allFields).jsonObject
        for (field in SettingsBackupSanitizer.RUNTIME_FIELD_RESETS.keys) {
            assertTrue(field in fields, "$field is not a field of AabSettings any more")
        }
    }
}
