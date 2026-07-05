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
 * (TotalFailure). Only TotalFailure surfaces a user-visible error (the Profiles & Contexts error card).
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

    @Test
    fun `display toggle fields round-trip through the export format D151`() {
        // The D-151 profile fields ride the same AabSettings serializer as everything else —
        // including the nullable temperature (null = "device default" must survive a round-trip
        // as null, and a set value as itself).
        val withOpinion = """
            { "schemaVersion": 3, "settings": {
                "nightLightEnabled": true, "nightLightTemperature": 2700,
                "daltonizerMode": "GRAYSCALE", "inversionEnabled": true,
                "alwaysOnDisplayEnabled": true, "stayAwakeChargingEnabled": true,
                "hdrForceSdrEnabled": true } }
        """.trimIndent()
        val loaded = (manager.decodePayload(withOpinion) as ProfileLoadResult.Success).settings
        assertEquals(true, loaded.nightLightEnabled)
        assertEquals(2_700, loaded.nightLightTemperature)
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
