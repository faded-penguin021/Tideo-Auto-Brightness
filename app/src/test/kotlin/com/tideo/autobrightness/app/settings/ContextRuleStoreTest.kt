package com.tideo.autobrightness.app.settings

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class ContextRuleStoreTest {

    /** In-memory DataStore for pure-JVM CRUD testing (no Android/file plumbing). */
    private class FakeDataStore<T>(initial: T) : DataStore<T> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<T> = state
        override suspend fun updateData(transform: suspend (t: T) -> T): T {
            val updated = transform(state.value)
            state.update { updated }
            return updated
        }
    }

    private fun store() = ContextRuleStore(FakeDataStore(ContextOverrideConfig()))

    private fun rule(id: String, profile: String = "P_$id") =
        ContextRule(id = id, name = "Rule $id", profile = profile)

    @Test
    fun save_appendsNewRule() = runTest {
        val s = store()
        s.save(rule("a"))
        s.save(rule("b"))
        assertEquals(listOf("a", "b"), s.rules().map { it.id })
    }

    @Test
    fun save_upsertsExistingIdInPlaceableSet() = runTest {
        val s = store()
        s.save(rule("a", profile = "Old"))
        s.save(rule("a", profile = "New"))
        val rules = s.rules()
        assertEquals(1, rules.size, "same id must upsert, not duplicate")
        assertEquals("New", rules.single().profile)
    }

    @Test
    fun delete_removesById() = runTest {
        val s = store()
        s.save(rule("a"))
        s.save(rule("b"))
        s.delete("a")
        assertEquals(listOf("b"), s.rules().map { it.id })
    }

    @Test
    fun replaceAll_overwritesWholeSet() = runTest {
        val s = store()
        s.save(rule("a"))
        s.replaceAll(listOf(rule("x"), rule("y")))
        assertEquals(listOf("x", "y"), s.rulesFlow().first().map { it.id })
    }

    @Test
    fun taskerJsonExport_isBareArray() {
        val config = ContextOverrideConfig(rules = listOf(rule("a")))
        val json = config.toTaskerJson()
        assertTrue(json.trimStart().startsWith("["), "Tasker interop export must be a bare JSON array")
    }

    @Test
    fun byPriority_ordersHighestFirst_thenName_G2RF43() {
        // G2R-F43: the rule list must display by priority (highest first), not creation order; ties
        // break on a stable, case-insensitive name order.
        val rules = listOf(
            ContextRule(id = "1", name = "zeta", profile = "P", priority = 1),
            ContextRule(id = "2", name = "alpha", profile = "P", priority = 5),
            ContextRule(id = "3", name = "Beta", profile = "P", priority = 5),
            ContextRule(id = "4", name = "gamma", profile = "P", priority = 9),
        )
        assertEquals(listOf("gamma", "alpha", "Beta", "zeta"), rules.byPriority().map { it.name })
    }
}
