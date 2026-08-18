package com.tideo.autobrightness.app.settings

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Persistence + CRUD for context-override rules (task623 rebuild, contexts_spec §3).
 * Single mutation entry point: upsert by id or delete. DataStore is the single source of truth.
 */
class ContextRuleStore(private val dataStore: DataStore<ContextOverrideConfig>) {

    fun rulesFlow(): Flow<List<ContextRule>> = dataStore.data.map { it.rules }

    suspend fun rules(): List<ContextRule> = dataStore.data.first().rules

    suspend fun save(rule: ContextRule) {
        dataStore.updateData { current ->
            val without = current.rules.filterNot { it.id == rule.id }
            current.copy(rules = without + rule)
        }
    }

    suspend fun delete(id: String) {
        dataStore.updateData { current ->
            current.copy(rules = current.rules.filterNot { it.id == id })
        }
    }

    suspend fun replaceAll(rules: List<ContextRule>) {
        dataStore.updateData { it.copy(rules = rules) }
    }
}
