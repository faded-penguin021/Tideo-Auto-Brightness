package com.tideo.autobrightness.app.settings

import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S12.9c #3: profile loading reports a typed [ProfileLoadResult] across all three branches —
 * our export format (Success), the legacy Tasker parser (LegacyFallback), and unparseable input
 * (TotalFailure). Only TotalFailure surfaces a user-visible error (the ProfilesScreen error card).
 */
@RunWith(RobolectricTestRunner::class)
class ProfileLoadResultTest {

    private val manager = ProfileImportExportManager(ApplicationProvider.getApplicationContext())

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
        val result = manager.decodePayload(taskerConfig)
        assertTrue(result is ProfileLoadResult.LegacyFallback, "expected LegacyFallback, got $result")
        assertEquals(50, (result as ProfileLoadResult.LegacyFallback).settings.zone1End)
        assertTrue(result.jsonError.isNotEmpty(), "the JSON error should be recorded")
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
}
