package com.tideo.autobrightness.app.settings

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * S12.9c #1: AabSettings is decomposable into seven nested records, but the on-disk schema stays the
 * flat v3 JSON. This asserts that (a) a flat v3 JSON file loads into the nested group views, and
 * (b) re-encoding keeps the flat wire format (no nested keys) — i.e. wire compatibility is preserved.
 */
class NestedSchemaRoundTripTest {

    private val json = Json { ignoreUnknownKeys = true }

    // A flat v3 JSON document with one non-default value per group (the others fall to defaults).
    private val flatV3Json = """
        {
          "schemaVersion": 3,
          "minBrightness": 12, "maxBrightness": 240, "offset": 3, "scale": 0.9,
          "zone1End": 40, "zone2End": 9000, "form1A": 6.5, "form2B": 9.1, "form2C": 19,
          "dimmingEnabled": true, "dimmingStrength": 30, "dimmingExponent": 3.0,
          "dimmingThreshold": 20, "dimSpread": 80, "pwmSensitive": true, "pwmExponent": 0.9,
          "animSteps": 30, "minWaitMs": 20, "maxWaitMs": 70, "throttleDefaultMs": 1400,
          "deltaFactor": 2.0, "thresholdBright": 0.07, "thresholdDark": 0.35, "thresholdDim": 0.22,
          "thresholdSteepness": 2.2, "thresholdMidpoint": 3.8, "trustUnreliableSensor": true,
          "scalingEnabled": true, "scaleSpread": 20, "scaleSteepness": 7,
          "scaleTaperMidpoint": 200, "scaleTaperSteepness": 0.06, "scaleTransitionFactor": 0.2,
          "serviceEnabled": false, "detectOverrides": true, "debugLevel": 4,
          "contextOverride": true, "setupTitle": "X",
          "quickSettingsEnabled": true, "notificationsEnabled": false
        }
    """.trimIndent()

    @Test
    fun `flat v3 JSON loads into the nested group views`() {
        val s = json.decodeFromString(AabSettings.serializer(), flatV3Json)

        assertEquals(BrightnessBounds(12, 240, 3, 0.9f), s.bounds)
        assertEquals(CurveParams(40, 9000, 6.5, 9.1f, 19), s.curve)
        assertEquals(DimmingConfig(true, 30, 3.0f, 20, 80, true, 0.9f), s.dimming)
        assertEquals(AnimationConfig(30, 20, 70, 1400L), s.animation)
        assertEquals(ThresholdConfig(2.0f, 0.07f, 0.35f, 0.22f, 2.2f, 3.8, true), s.thresholds)
        assertEquals(ScalingConfig(true, 20, 7, 200, 0.06f, 0.2f), s.scaling)
        // panicSensitivity omitted from the JSON → falls to its default 8 (D-116).
        assertEquals(GlobalPrefs(false, true, 4, 8, true, "X", true, false), s.global)
    }

    @Test
    fun `re-encoding keeps the flat wire format`() {
        val s = json.decodeFromString(AabSettings.serializer(), flatV3Json)
        val encoded = json.encodeToString(AabSettings.serializer(), s)

        // Flat keys present; no nested group object keys leaked into the wire format.
        assertTrue(encoded.contains("\"minBrightness\""), "flat key must persist: $encoded")
        assertTrue(encoded.contains("\"dimSpread\""))
        assertFalse(encoded.contains("\"bounds\""), "nested group must NOT be serialized: $encoded")
        assertFalse(encoded.contains("\"global\""))

        // Full round-trip identity.
        val again = json.decodeFromString(AabSettings.serializer(), encoded)
        assertEquals(s, again)
    }

    @Test
    fun `group views reflect the default settings`() {
        val d = AabSettings()
        assertEquals(d.minBrightness, d.bounds.minBrightness)
        assertEquals(d.dimSpread, d.dimming.dimSpread)
        assertEquals(d.serviceEnabled, d.global.serviceEnabled)
        assertEquals(d.scalingEnabled, d.scaling.scalingEnabled)
    }
}
