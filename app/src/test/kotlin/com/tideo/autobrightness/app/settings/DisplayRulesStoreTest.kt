package com.tideo.autobrightness.app.settings

import androidx.datastore.core.DataStore
import com.tideo.autobrightness.domain.display.DisplayAction
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** D-150: display-rule storage CRUD + serializer round-trip (the ContextRuleStore pattern). */
class DisplayRulesStoreTest {

    private class FakeDataStore<T>(initial: T) : DataStore<T> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<T> = state
        override suspend fun updateData(transform: suspend (t: T) -> T): T {
            val updated = transform(state.value)
            state.update { updated }
            return updated
        }
    }

    private fun store() = DisplayRulesStore(FakeDataStore(DisplayRuleSet()))

    private fun rule(id: String, action: String = DisplayAction.GRAYSCALE.name) = DisplayRule(
        id = id,
        name = "Rule $id",
        action = action,
        triggers = ContextTriggers(
            apps = listOf("com.example.social"),
            timeRange = listOf("22:00", "06:00"),
            days = listOf(2, 3, 4, 5, 6),
        ),
    )

    @Test
    fun save_appendsNewRule() = runTest {
        val s = store()
        s.save(rule("a"))
        s.save(rule("b"))
        assertEquals(listOf("a", "b"), s.rules().map { it.id })
    }

    @Test
    fun save_upsertsById() = runTest {
        val s = store()
        s.save(rule("a", action = DisplayAction.GRAYSCALE.name))
        s.save(rule("a", action = DisplayAction.INVERSION.name))
        val rules = s.rules()
        assertEquals(1, rules.size, "same id must upsert, not duplicate")
        assertEquals(DisplayAction.INVERSION.name, rules.single().action)
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
    fun serializer_roundTripsAllFields() = runTest {
        val original = DisplayRuleSet(rules = listOf(rule("a"), rule("b", DisplayAction.NIGHT_LIGHT.name).copy(enabled = false)))
        val out = ByteArrayOutputStream()
        DisplayRulesSerializer.writeTo(original, out)
        val decoded = DisplayRulesSerializer.readFrom(ByteArrayInputStream(out.toByteArray()))
        assertEquals(original, decoded)
    }

    @Test
    fun serializer_malformedInput_fallsBackToEmptyDefault() = runTest {
        val decoded = DisplayRulesSerializer.readFrom(ByteArrayInputStream("not json".toByteArray()))
        assertEquals(DisplayRuleSet(), decoded)
    }

    @Test
    fun toSpec_mapsAllTriggerDimensions() {
        val spec = rule("a").toSpec()
        assertEquals(DisplayAction.GRAYSCALE, spec?.action)
        assertEquals(listOf("com.example.social"), spec?.apps)
        assertEquals("22:00", spec?.timeRange?.start)
        assertEquals("06:00", spec?.timeRange?.end)
        assertEquals(listOf(2, 3, 4, 5, 6), spec?.days)
        assertTrue(spec?.enabled == true)
    }

    @Test
    fun toSpec_unknownAction_isNullSoTheRuleStaysInert() {
        // A rule written by a newer schema (unknown action) must not fail the set — just go inert.
        assertNull(rule("a", action = "SEPIA_FUTURE").toSpec())
    }
}
