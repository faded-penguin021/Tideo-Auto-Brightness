package com.tideo.autobrightness.app.settings

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** S12.6d: user-editable profile store (built-ins seed once; factory restore re-seeds). */
class UserProfileStoreTest {

    private class FakeDataStore<T>(initial: T) : DataStore<T> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<T> = state
        override suspend fun updateData(transform: suspend (t: T) -> T): T {
            val updated = transform(state.value)
            state.update { updated }
            return updated
        }
    }

    private fun store() = UserProfileStore(FakeDataStore(SavedProfiles()))

    // DD-030: a revision-0 file as the previous app wrote it — built-ins at the 3.0 midpoint.
    private fun revision0(profiles: List<SavedProfile>): String {
        val json = Json { encodeDefaults = true }
        val encoded = json.encodeToJsonElement(SavedProfiles.serializer(), SavedProfiles(profiles, seeded = true))
        return JsonObject(encoded.jsonObject - "factoryRevision").toString()
    }

    private val legacyBuiltIns = DefaultProfiles.all.map { (name, s) ->
        SavedProfile(name, s.copy(thresholdMidpoint = 3.0, schemaVersion = 3), builtIn = true)
    }

    @Test
    fun readFrom_movesOnlyUntouchedBuiltInsToTheNewMidpoint() = runTest {
        val edited = legacyBuiltIns.map {
            if (it.name == "Night Reading") it.copy(settings = it.settings.copy(minBrightness = 30)) else it
        }
        val mine = SavedProfile("Mine", DefaultProfiles.Default.copy(thresholdMidpoint = 3.0))
        val read = SavedProfilesSerializer.readFrom(revision0(edited + mine).byteInputStream())

        val byName = read.profiles.associate { it.name to it.settings }
        for (name in listOf("Default", "Battery Saver", "Video Streaming", "Outdoors")) {
            assertEquals(DefaultProfiles.all.getValue(name), byName.getValue(name), name)
        }
        assertEquals(4.255, byName.getValue("Outdoors").thresholdMidpoint)
        assertEquals(3.0, byName.getValue("Night Reading").thresholdMidpoint, "an edited built-in is kept")
        assertEquals(30, byName.getValue("Night Reading").minBrightness)
        assertEquals(3.0, byName.getValue("Mine").thresholdMidpoint, "a user profile is kept")
        assertEquals(SavedProfiles.FACTORY_REVISION, read.factoryRevision)
    }

    private val v113Default = """
        {"schemaVersion":3,"serviceEnabled":true,"detectOverrides":false,"minBrightness":10,"maxBrightness":255,
        "offset":0,"scale":1.0,"zone1End":35,"zone2End":10000,"form1A":5.0,"form2B":8.8,"form2C":18,
        "dimmingEnabled":false,"dimmingStrength":25,"dimmingExponent":2.5,"dimmingThreshold":15,"dimSpread":100,
        "pwmSensitive":false,"pwmExponent":0.8,"throttleDefaultMs":1510,"minWaitMs":5,"maxWaitMs":30,
        "animSteps":50,"deltaFactor":1.8,"thresholdBright":0.08,"thresholdDark":0.3,"thresholdDim":0.25,
        "thresholdSteepness":2.1,"thresholdMidpoint":3.0,"scalingEnabled":false,"scaleSpread":15,
        "scaleSteepness":6,"scaleTaperMidpoint":190,"scaleTaperSteepness":0.075,"scaleTransitionFactor":0.1,
        "trustUnreliableSensor":false,"quickSettingsEnabled":false,"notificationsEnabled":true,"debugLevel":0,
        "panicSensitivity":8,"contextOverride":false,"panicRequiresPlugged":false,
        "setupTitle":"Advanced Auto Brightness Setup","nightLightEnabled":false,"nightLightTemperature":null,
        "nightLightCircadianEnabled":false,"daltonizerMode":"OFF","inversionEnabled":false,
        "alwaysOnDisplayEnabled":false,"stayAwakeChargingEnabled":false,"hdrForceSdrEnabled":false}
    """.trimIndent()

    private val v113Outdoors = """
        {"schemaVersion":3,"serviceEnabled":true,"detectOverrides":false,"minBrightness":25,"maxBrightness":255,
        "offset":15,"scale":1.15,"zone1End":55,"zone2End":18000,"form1A":8.0,"form2B":8.8,"form2C":18,
        "dimmingEnabled":false,"dimmingStrength":25,"dimmingExponent":2.5,"dimmingThreshold":15,"dimSpread":100,
        "pwmSensitive":false,"pwmExponent":0.8,"throttleDefaultMs":1510,"minWaitMs":10,"maxWaitMs":30,
        "animSteps":10,"deltaFactor":4.0,"thresholdBright":0.08,"thresholdDark":0.3,"thresholdDim":0.25,
        "thresholdSteepness":2.1,"thresholdMidpoint":3.0,"scalingEnabled":false,"scaleSpread":15,
        "scaleSteepness":6,"scaleTaperMidpoint":190,"scaleTaperSteepness":0.075,"scaleTransitionFactor":0.1,
        "trustUnreliableSensor":false,"quickSettingsEnabled":false,"notificationsEnabled":true,"debugLevel":0,
        "panicSensitivity":8,"contextOverride":false,"panicRequiresPlugged":false,
        "setupTitle":"Advanced Auto Brightness Setup","nightLightEnabled":false,"nightLightTemperature":null,
        "nightLightCircadianEnabled":false,"daltonizerMode":"OFF","inversionEnabled":false,
        "alwaysOnDisplayEnabled":false,"stayAwakeChargingEnabled":false,"hdrForceSdrEnabled":false}
    """.trimIndent()

    @Test
    fun readFrom_upgradesTheBuiltInsAsV113SeededThem() = runTest {
        val file = """{"profiles":[{"name":"Default","settings":$v113Default,"builtIn":true},""" +
            """{"name":"Outdoors","settings":$v113Outdoors,"builtIn":true}],"seeded":true}"""
        val read = SavedProfilesSerializer.readFrom(file.byteInputStream())
        assertEquals(listOf(DefaultProfiles.Default, DefaultProfiles.Outdoors), read.profiles.map { it.settings })
    }

    @Test
    fun readFrom_leavesAnUpgradedFileAlone() = runTest {
        val upgraded = SavedProfiles(legacyBuiltIns, seeded = true, factoryRevision = SavedProfiles.FACTORY_REVISION)
        val json = Json { encodeDefaults = true }.encodeToString(SavedProfiles.serializer(), upgraded)
        val read = SavedProfilesSerializer.readFrom(json.byteInputStream())
        assertEquals(3.0, read.profiles.first { it.name == "Default" }.settings.thresholdMidpoint, "a chosen 3.0 is kept")
    }

    @Test
    fun seedingAndFactoryRestore_recordTheCurrentRevision() = runTest {
        val ds = FakeDataStore(SavedProfiles())
        val s = UserProfileStore(ds)
        s.ensureSeeded()
        assertEquals(SavedProfiles.FACTORY_REVISION, ds.data.first().factoryRevision)
        s.restoreFactory()
        assertEquals(SavedProfiles.FACTORY_REVISION, ds.data.first().factoryRevision)
    }

    @Test
    fun ensureSeeded_seedsTheFiveBuiltIns() = runTest {
        val s = store()
        val names = s.names()
        assertEquals(DefaultProfiles.all.keys.toList(), names, "built-ins seed in order")
        assertTrue(s.profiles().all { it.builtIn }, "all seeded entries are flagged built-in")
    }

    @Test
    fun save_createsUserProfile() = runTest {
        val s = store()
        s.save("My Profile", AabSettings(minBrightness = 42))
        assertEquals(42, s.get("My Profile")?.minBrightness)
        assertTrue("My Profile" in s.names())
    }

    @Test
    fun save_overwritesExistingBuiltInInPlace() = runTest {
        val s = store()
        val before = s.names()
        s.save("Default", AabSettings(minBrightness = 7))
        assertEquals(7, s.get("Default")?.minBrightness, "overwrite replaces the built-in's settings")
        assertEquals(before, s.names(), "overwrite keeps the entry in place (no duplicate/reorder)")
        // Overwritten built-in stays 'factory' so restore can re-seed it.
        assertTrue(s.profiles().first { it.name == "Default" }.builtIn)
    }

    @Test
    fun delete_removesProfile() = runTest {
        val s = store()
        s.save("Temp", AabSettings())
        s.delete("Temp")
        assertNull(s.get("Temp"))
    }

    @Test
    fun restoreFactory_reSeedsBuiltInsButKeepsUserProfiles() = runTest {
        val s = store()
        s.save("Default", AabSettings(minBrightness = 99))   // corrupt a built-in
        s.save("Mine", AabSettings(maxBrightness = 200))      // user profile
        s.restoreFactory()

        assertEquals(
            DefaultProfiles.Default.minBrightness,
            s.get("Default")?.minBrightness,
            "factory restore reverts the built-in to DefaultProfiles",
        )
        assertNotNull(s.get("Mine"), "factory restore keeps user-created profiles")
        assertEquals(200, s.get("Mine")?.maxBrightness)
    }
}
