package com.tideo.autobrightness.app.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * S12.9c #5: every typed-JSON DataStore declares a schema version constant, and each serializer's
 * `defaultValue` reflects the current schema. Documented in `docs/rebuild/architecture/datastore_map.md`.
 * (The `service_health` and `experiment_prefs` Preferences DataStores are schema-less key/value stores
 * and have no serializer to check — see the doc.)
 */
class DataStoreSchemaVersionTest {

    @Test
    fun `settings store version matches its serializer default`() {
        assertEquals(3, CURRENT_SCHEMA_VERSION)
        assertEquals(CURRENT_SCHEMA_VERSION, AabSettings().schemaVersion)
        assertEquals(CURRENT_SCHEMA_VERSION, AabSettingsSerializer.defaultValue.schemaVersion)
    }

    @Test
    fun `context-rules store is at schema v1 with an identity default`() {
        assertEquals(1, ContextOverrideConfig.SCHEMA_VERSION)
        assertEquals(ContextOverrideConfig(), ContextRulesSerializer.defaultValue)
    }

    @Test
    fun `user-profiles store is at schema v1 with an identity default`() {
        assertEquals(1, SavedProfiles.SCHEMA_VERSION)
        assertEquals(SavedProfiles(), SavedProfilesSerializer.defaultValue)
    }

    @Test
    fun `override-points store is at schema v1 with an identity default`() {
        assertEquals(1, OverridePoints.SCHEMA_VERSION)
        assertEquals(OverridePoints(), OverridePointsSerializer.defaultValue)
    }
}
