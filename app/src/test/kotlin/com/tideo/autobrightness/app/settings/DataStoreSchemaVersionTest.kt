package com.tideo.autobrightness.app.settings

import kotlin.test.Test
import kotlin.test.assertEquals

// S12.9c #5: schema version matching (see datastore_map.md).
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
    fun `context-baseline store is at schema v2 with an identity default`() {
        assertEquals(2, ContextBaseline.SCHEMA_VERSION)
        assertEquals(ContextBaseline(), ContextBaselineSerializer.defaultValue)
    }

    @Test
    fun `override-points store is at schema v1 with an identity default`() {
        assertEquals(1, OverridePoints.SCHEMA_VERSION)
        assertEquals(OverridePoints(), OverridePointsSerializer.defaultValue)
    }
}
