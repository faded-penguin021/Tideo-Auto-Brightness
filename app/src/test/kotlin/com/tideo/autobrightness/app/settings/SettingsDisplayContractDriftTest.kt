package com.tideo.autobrightness.app.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * S12.9c #2: [valueFor] is fail-fast — every [AabSettingsContract] key must resolve in its `when`, and
 * an unknown key throws instead of returning "". This guards against the contract and the extractor
 * drifting apart (a new contract row with no `valueFor` arm, or vice versa).
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
        // Resolving all contract keys must not throw; the count is the contract size (no orphan arms
        // can be exercised from the contract, and no contract key can be missing an arm).
        val resolved = AabSettingsContract.rules.count {
            runCatching { settings.valueFor(it.key) }.isSuccess
        }
        assertEquals(AabSettingsContract.rules.size, resolved)
    }
}
