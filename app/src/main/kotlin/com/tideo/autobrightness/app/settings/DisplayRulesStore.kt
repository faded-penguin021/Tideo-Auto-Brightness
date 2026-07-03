package com.tideo.autobrightness.app.settings

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Persistence + CRUD for display schedule rules (D-150 — the [ContextRuleStore] pattern: upsert
 * by [DisplayRule.id] on save, rebuild the list excluding the id on delete). DataStore is the
 * single source of truth; the runtime [DisplayRulesCoordinator] reacts to [rulesFlow] so an
 * add/edit/delete re-evaluates immediately (the D-141 lesson).
 */
class DisplayRulesStore(private val dataStore: DataStore<DisplayRuleSet>) {

    fun rulesFlow(): Flow<List<DisplayRule>> = dataStore.data.map { it.rules }

    suspend fun rules(): List<DisplayRule> = dataStore.data.first().rules

    /** Upsert by id (replace if present, else append). */
    suspend fun save(rule: DisplayRule) {
        dataStore.updateData { current ->
            val without = current.rules.filterNot { it.id == rule.id }
            current.copy(rules = without + rule)
        }
    }

    /** Rebuild the list excluding [id]. */
    suspend fun delete(id: String) {
        dataStore.updateData { current ->
            current.copy(rules = current.rules.filterNot { it.id == id })
        }
    }
}
