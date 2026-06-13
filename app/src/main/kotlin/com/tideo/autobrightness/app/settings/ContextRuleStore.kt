package com.tideo.autobrightness.app.settings

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Persistence + CRUD for context-override rules — the rebuild of Tasker task623 `_ContextManager`
 * (contexts_spec §3). The single mutation entry point: upsert by [ContextRule.id] on save, rebuild
 * the list excluding the id on delete.
 *
 * The dual RAM/disk cache dance of the original (contexts_spec §2) is unnecessary here: DataStore
 * is the single, always-fresh source of truth, so there is no stale-cache failsafe to port (the
 * daily reset's only real effect was forcing a disk reload, which a repository makes moot — D-014).
 */
class ContextRuleStore(private val dataStore: DataStore<ContextOverrideConfig>) {

    fun rulesFlow(): Flow<List<ContextRule>> = dataStore.data.map { it.rules }

    suspend fun rules(): List<ContextRule> = dataStore.data.first().rules

    /** SAVE_CONTEXT: upsert by id (replace if present, else append). */
    suspend fun save(rule: ContextRule) {
        dataStore.updateData { current ->
            val without = current.rules.filterNot { it.id == rule.id }
            current.copy(rules = without + rule)
        }
    }

    /** DELETE_CONTEXT: rebuild the list excluding [id]. */
    suspend fun delete(id: String) {
        dataStore.updateData { current ->
            current.copy(rules = current.rules.filterNot { it.id == id })
        }
    }

    /** Replace the whole set (import / bulk edit). */
    suspend fun replaceAll(rules: List<ContextRule>) {
        dataStore.updateData { it.copy(rules = rules) }
    }
}
