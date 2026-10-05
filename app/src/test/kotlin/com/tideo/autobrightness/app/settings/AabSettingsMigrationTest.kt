package com.tideo.autobrightness.app.settings

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AabSettingsMigrationTest {

    // A minimal v1 JSON: schemaVersion=1, a few explicit fields, NO new v2 fields.
    private val v1Json = """
        {
          "schemaVersion": 1,
          "serviceEnabled": true,
          "minBrightness": 12,
          "maxBrightness": 240,
          "scale": 1,
          "throttleDefaultMs": 1000,
          "debugLevel": 3
        }
    """.trimIndent()

    @Test
    fun `v1 json deserializes with new v2 fields at their defaults`() = runTest {
        val settings = AabSettingsSerializer.readFrom(v1Json.byteInputStream())

        // Pre-existing fields are read from JSON
        assertEquals(12, settings.minBrightness)
        assertEquals(240, settings.maxBrightness)
        assertEquals(3, settings.debugLevel)

        // Absent v2 fields take defaults, except animation: throttleDefaultMs pins it to v3's (DD-030).
        assertEquals(20, settings.animSteps, "a pre-v4 file with one animation key keeps v3's animSteps")
        assertEquals(4.0, settings.thresholdMidpoint, "thresholdMidpoint should default to 4.0 (log10(10000))")
        assertFalse(settings.contextOverride, "contextOverride must default to false — the runtime context-lock latch starts unlatched (D-038)")
        assertEquals("Advanced Auto Brightness Setup", settings.setupTitle)
    }

    @Test
    fun `v1 json schema version is bumped to current after migration`() = runTest {
        val settings = AabSettingsSerializer.readFrom(v1Json.byteInputStream())
        assertEquals(CURRENT_SCHEMA_VERSION, settings.schemaVersion, "schemaVersion should be bumped to the current schema")
    }

    @Test
    fun `v2 json round-trips without loss`() = runTest {
        val original = AabSettings(
            minBrightness = 15,
            animSteps = 30,
            thresholdMidpoint = 3.5,
            contextOverride = false,
            setupTitle = "Custom Title",
        )
        val encoded = buildString {
            val output = java.io.ByteArrayOutputStream()
            AabSettingsSerializer.writeTo(original, output)
            append(output.toString())
        }
        val decoded = AabSettingsSerializer.readFrom(encoded.byteInputStream())
        assertEquals(original.minBrightness, decoded.minBrightness)
        assertEquals(original.animSteps, decoded.animSteps)
        assertEquals(original.thresholdMidpoint, decoded.thresholdMidpoint)
        assertEquals(original.contextOverride, decoded.contextOverride)
        assertEquals(original.setupTitle, decoded.setupTitle)
        assertEquals(CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
    }

    @Test
    fun `scale field survives int-encoded v1 json as float`() = runTest {
        // v1 stored scale as Int (e.g. 1); v2 is Float. JSON integer decodes safely to Float.
        val settings = AabSettingsSerializer.readFrom(v1Json.byteInputStream())
        assertEquals(1.0f, settings.scale, 0.001f)
    }

    // G2R-F85: v2 stored a bogus editable `thresholdDynamic`. v3 removed it. A v2 JSON still carrying
    // the key must decode cleanly (ignoreUnknownKeys drops it) and migrate to v3 — no data loss, no error.
    private val v2JsonWithThreshDynamic = """
        {
          "schemaVersion": 2,
          "serviceEnabled": true,
          "minBrightness": 14,
          "thresholdDynamic": 12,
          "animSteps": 20,
          "thresholdMidpoint": 4.0
        }
    """.trimIndent()

    @Test
    fun `v2 json with dropped thresholdDynamic key decodes and migrates to v3`() = runTest {
        val settings = AabSettingsSerializer.readFrom(v2JsonWithThreshDynamic.byteInputStream())
        assertEquals(14, settings.minBrightness, "known keys still read")
        assertEquals(3, settings.schemaVersion, "schemaVersion bumped to v3")
        assertEquals(3, CURRENT_SCHEMA_VERSION, "v3 is the current schema after F85; DD-030 deliberately did not bump it")
    }

    @Test
    fun `migrate is idempotent on current version`() {
        val current = AabSettings()
        val migrated = AabSettingsSerializer.migrate(current)
        assertEquals(current, migrated)
    }

    @Test
    fun `new installs take the owner's task570 animation defaults`() {
        val d = AabSettings()
        assertEquals(listOf(50, 5, 30), listOf(d.animSteps, d.minWaitMs, d.maxWaitMs))
        assertEquals(1510L, d.throttleDefaultMs, "AnimSteps*MaxWait+10 = 50*30+10")
        assertEquals(4.0, d.thresholdMidpoint, "log10(Zone2End)")
        assertEquals(d, DefaultProfiles.Default, "the Default profile is the first-launch settings")
    }

    private val frozenDefaults = """
        {"schemaVersion":3,"serviceEnabled":true,"detectOverrides":false,"minBrightness":10,"maxBrightness":255,
        "offset":0,"scale":1.0,"zone1End":35,"zone2End":10000,"form1A":5.0,"form2B":8.8,"form2C":18,
        "dimmingEnabled":false,"dimmingStrength":25,"dimmingExponent":2.5,"dimmingThreshold":15,"dimSpread":100,
        "pwmSensitive":false,"pwmExponent":0.8,"throttleDefaultMs":1510,"minWaitMs":5,"maxWaitMs":30,
        "animSteps":50,"deltaFactor":1.8,"thresholdBright":0.08,"thresholdDark":0.3,"thresholdDim":0.25,
        "thresholdSteepness":2.1,"thresholdMidpoint":4.0,"scalingEnabled":false,"scaleSpread":15,
        "scaleSteepness":6,"scaleTaperMidpoint":190,"scaleTaperSteepness":0.075,"scaleTransitionFactor":0.1,
        "trustUnreliableSensor":false,"quickSettingsEnabled":false,"notificationsEnabled":true,"debugLevel":0,
        "panicSensitivity":8,"contextOverride":false,"panicRequiresPlugged":false,
        "setupTitle":"Advanced Auto Brightness Setup","nightLightEnabled":false,"nightLightTemperature":null,
        "nightLightCircadianEnabled":false,"daltonizerMode":"OFF","inversionEnabled":false,
        "alwaysOnDisplayEnabled":false,"stayAwakeChargingEnabled":false,"hdrForceSdrEnabled":false}
    """.trimIndent()

    @Test
    fun `every default is frozen, because a file written before DD-030 omits keys at their default`() {
        val now = Json { encodeDefaults = true }.encodeToJsonElement(AabSettings.serializer(), AabSettings())
        assertEquals(
            Json.parseToJsonElement(frozenDefaults), now,
            "A default changed, so an old file that omitted that key would silently take the new value: pin the " +
                "old value in AabSettingsSerializer.upgradeJson (datastore_map.md, DD-030), then update this literal.",
        )
    }

    // DD-030: a v3 writer omitted default-valued keys, schemaVersion among them.
    private suspend fun readV3(json: String) = AabSettingsSerializer.readFrom(json.byteInputStream())

    private fun AabSettings.animation() = listOf(animSteps.toLong(), minWaitMs.toLong(), maxWaitMs.toLong(), throttleDefaultMs)

    @Test
    fun `a v3 file with no animation key takes the new defaults`() = runTest {
        val s = readV3("""{ "minBrightness": 12 }""")
        assertEquals(listOf(50L, 5L, 30L, 1510L), s.animation())
        assertEquals(12, s.minBrightness)
        assertEquals(CURRENT_SCHEMA_VERSION, s.schemaVersion)
    }

    @Test
    fun `a v3 file with one animation key keeps the v3 defaults for the others`() = runTest {
        assertEquals(listOf(40L, 25L, 65L, 1310L), readV3("""{ "animSteps": 40 }""").animation())
        assertEquals(listOf(20L, 25L, 100L, 1310L), readV3("""{ "schemaVersion": 3, "maxWaitMs": 100 }""").animation())
    }

    @Test
    fun `a v3 file with every animation key keeps them`() = runTest {
        val json = """{ "animSteps": 1, "minWaitMs": 5, "maxWaitMs": 30, "throttleDefaultMs": 1510 }"""
        assertEquals(listOf(1L, 5L, 30L, 1510L), readV3(json).animation())
    }

    @Test
    fun `a file written now holds every key, so it round-trips and a v3 build still reads it`() = runTest {
        val out = java.io.ByteArrayOutputStream()
        AabSettingsSerializer.writeTo(AabSettings(animSteps = 40), out)
        val written = Json.parseToJsonElement(out.toString()).jsonObject
        val descriptor = AabSettings.serializer().descriptor
        val allKeys = (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }.toSet()
        assertEquals(allKeys, written.keys, "every key, defaults included")
        assertEquals(listOf(40L, 5L, 30L, 1510L), AabSettingsSerializer.readFrom(out.toByteArray().inputStream()).animation())
        val v3Reader = Json { ignoreUnknownKeys = true }.decodeFromString(AabSettings.serializer(), out.toString())
        assertTrue(v3Reader.schemaVersion in 1..3, "a downgrade or an equal-versionCode reinstall keeps the file")
        assertEquals(listOf(40L, 5L, 30L, 1510L), v3Reader.animation())
    }

    @Test
    fun `a v3 context baseline snapshot is pinned like the live file`() = runTest {
        val stored = """{ "snapshot": { "animSteps": 40 }, "userProfileName": "Outdoors" }"""
        val baseline = ContextBaselineSerializer.readFrom(stored.byteInputStream())
        assertEquals(listOf(40L, 25L, 65L, 1310L), baseline.snapshot?.animation())
        assertEquals("Outdoors", baseline.userProfileName)
        assertEquals(null, ContextBaselineSerializer.readFrom("""{ "userProfileName": "x" }""".byteInputStream()).snapshot)
    }

    @Test
    fun `a context baseline written now round-trips a partly-default animation group`() = runTest {
        val out = java.io.ByteArrayOutputStream()
        ContextBaselineSerializer.writeTo(ContextBaseline(snapshot = AabSettings(animSteps = 40)), out)
        val decoded = ContextBaselineSerializer.readFrom(out.toByteArray().inputStream())
        assertEquals(listOf(40L, 5L, 30L, 1510L), decoded.snapshot?.animation())
    }
}
