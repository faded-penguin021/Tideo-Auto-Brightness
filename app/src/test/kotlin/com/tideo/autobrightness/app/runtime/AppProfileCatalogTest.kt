package com.tideo.autobrightness.app.runtime

import androidx.datastore.core.DataStore
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.SavedProfiles
import com.tideo.autobrightness.app.settings.UserProfileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * G2R-F44: a legacy/imported profile registered via [UserProfileStore] (what the Profiles screen now
 * does on load) must be visible through [AppProfileCatalog] — the catalog the context-rule editor
 * reads — so it is selectable as a rule target without a manual re-save.
 */
class AppProfileCatalogTest {

    private class FakeDataStore<T>(initial: T) : DataStore<T> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<T> = state
        override suspend fun updateData(transform: suspend (t: T) -> T): T {
            val updated = transform(state.value)
            state.update { updated }
            return updated
        }
    }

    @Test
    fun importedProfile_isSelectableAsRuleTarget() = runTest {
        val store = UserProfileStore(FakeDataStore(SavedProfiles()))
        val catalog = AppProfileCatalog(store)

        // The Profiles screen registers the imported profile under its file name.
        store.save("My Legacy Profile", AabSettings(minBrightness = 33))

        assertTrue("My Legacy Profile" in catalog.names(), "imported profile must appear in the catalog")
        assertEquals(33, catalog.profile("My Legacy Profile")?.minBrightness)
    }
}
