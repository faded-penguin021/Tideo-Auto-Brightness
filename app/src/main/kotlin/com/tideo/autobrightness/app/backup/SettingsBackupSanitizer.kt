package com.tideo.autobrightness.app.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * DB-002: strip **runtime state** out of a restored `aab_settings.json`.
 *
 * `aab_settings.json` is backup-eligible because it carries the user's brightness configuration —
 * curve, thresholds, dimming, circadian — which is genuinely worth restoring onto a new device. It
 * also carries a couple of fields that describe *what this installation was doing at backup time*,
 * and those must not travel:
 *
 *  - `serviceEnabled` — restoring `true` asserts a running state the new installation has not
 *    established (no `WRITE_SETTINGS` grant yet, no user opt-in on this device).
 *  - `contextOverride` — the manual context lock. Restoring `true` silently pins the fresh install
 *    to a manually-loaded profile, so context rules appear broken with nothing on screen to explain
 *    why. Its matching `%AAB_ProfileUser` identity is *not* backed up, which is precisely what makes
 *    a restored `true` incoherent rather than merely surprising.
 *
 * Why this shape rather than splitting the store: the alternative — moving runtime fields into a
 * second, non-backed-up DataStore — is a schema migration of the app's central settings object, and
 * it buys nothing here. Backup eligibility is a property of the *file*, so the only question is what
 * that file may say after a restore. Answering it at the restore boundary keeps one settings schema,
 * one serializer, and one migration path, and puts the rule where it is enforceable and testable.
 *
 * Unknown keys are preserved verbatim: this runs against files written by other app versions, and a
 * sanitizer that silently dropped fields it did not recognise would be a data-loss bug in the guise
 * of a privacy control.
 */
object SettingsBackupSanitizer {

    /** Fields reset on restore, with the value they are reset to. */
    internal val RUNTIME_FIELD_RESETS: Map<String, JsonPrimitive> = mapOf(
        "serviceEnabled" to JsonPrimitive(false),
        "contextOverride" to JsonPrimitive(false),
    )

    private val json = Json { prettyPrint = true }

    /**
     * Return [rawSettingsJson] with every runtime field forced to its safe value, or `null` when the
     * input is not a JSON object. A `null` return means "do not rewrite": a file that cannot be parsed is a file whose
     * contents this function does not understand, and clobbering it would destroy a restore that the
     * app's own tolerant serializer might still have made sense of.
     */
    fun sanitize(rawSettingsJson: String): String? {
        val root = runCatching { json.parseToJsonElement(rawSettingsJson).jsonObject }.getOrNull() ?: return null
        val sanitized = buildMap {
            putAll(root)
            // Write the reset ALWAYS — never "only if the key is present". The settings serializer
            // uses kotlinx defaults (no `encodeDefaults`), so a field equal to its default is simply
            // absent from the file, and `serviceEnabled` defaults to **true**. The dangerous backup
            // is therefore the one where the key is missing: reading it back yields `true` from the
            // data class default. A present-keys-only sanitizer would no-op on precisely the common
            // case it exists to fix. (Found by test, not by inspection — see the sanitizer test.)
            putAll(RUNTIME_FIELD_RESETS)
        }
        return json.encodeToString(JsonObject.serializer(), JsonObject(sanitized))
    }
}
