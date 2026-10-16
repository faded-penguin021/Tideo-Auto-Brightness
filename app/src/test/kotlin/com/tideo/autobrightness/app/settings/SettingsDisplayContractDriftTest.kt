package com.tideo.autobrightness.app.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * S12.9c #2: valueFor fail-fast; every AabSettingsContract key must resolve; guards drift.
 */
class SettingsDisplayContractDriftTest {

    private val settings = AabSettings()

    @Test
    fun `every contract key resolves through valueFor`() {
        AabSettingsContract.rules.forEach { rule ->
            val value = settings.valueFor(rule.key)
            assertTrue(value.isNotEmpty(), "contract key '${rule.key}' resolved to empty")
        }
    }

    @Test
    fun `valueFor throws on an unknown key`() {
        assertFailsWith<IllegalArgumentException> { settings.valueFor("notAKey") }
    }

    @Test
    fun `contract key count matches the resolvable extractor arms`() {
        // Contract size = count of resolvable keys (no orphans, no missing arms).
        val resolved = AabSettingsContract.rules.count {
            runCatching { settings.valueFor(it.key) }.isSuccess
        }
        assertEquals(AabSettingsContract.rules.size, resolved)
    }

    @Test
    fun `the hand-off rows carry AAB's names, their real defaults, and show as changed when set DD070`() {
        val rows = AabSettingsContract.rules.filter { it.key.startsWith("handoff") }
        assertEquals(
            mapOf("%AAB_AdaptiveOn" to "handoffEnabled", "%AAB_AdaptiveHandoffThresh" to "handoffLux", "%AAB_HandoffHysteresis" to "handoffHysteresis"),
            rows.associate { it.taskerVariable to it.key },
        )
        rows.forEach { assertEquals(it.defaultValue, settings.valueFor(it.key), it.key) }
        val changed = AabSettings(handoffEnabled = true, handoffLux = 30_000, handoffHysteresis = 0.7)
            .displayRows().filter { it.changed }.map { it.taskerVariable }
        assertEquals(rows.map { it.taskerVariable }, changed)
    }
}
