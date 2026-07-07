package com.tideo.autobrightness.app.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tideo.autobrightness.app.control.ControlPrefsStore
import com.tideo.autobrightness.app.storage.controlPrefsDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * D-157 (U4): backs the Tools → "Automation control" toggle. Reads/writes the opt-in
 * [ControlPrefsStore.externalControlEnabled] gate the exported `ControlReceiver` checks first.
 * Mirrors [CircadianExtrasViewModel]'s geo-IP opt-in wiring (StateFlow + a `viewModelScope`
 * setter) — the same default-off privacy/security pattern (D-105). Its OWN store, never an
 * `AabSettings` field, so profile apply/import can never flip it.
 */
class ControlPrefsViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ControlPrefsStore(application.controlPrefsDataStore)

    val externalControlEnabled: StateFlow<Boolean> = store.externalControlEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setExternalControlEnabled(enabled: Boolean) {
        viewModelScope.launch { store.setExternalControlEnabled(enabled) }
    }
}
