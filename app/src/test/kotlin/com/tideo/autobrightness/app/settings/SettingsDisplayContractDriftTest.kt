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
}
