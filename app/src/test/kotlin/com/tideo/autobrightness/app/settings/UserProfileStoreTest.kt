package com.tideo.autobrightness.app.settings

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * S12.6d (G2R-F15): the user-editable profile store. Built-ins seed once, are overwritable, and a
 * factory restore re-seeds them while keeping user-created profiles.
 */
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
        // The overwritten built-in is still 'factory' so restore can re-seed it.
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
