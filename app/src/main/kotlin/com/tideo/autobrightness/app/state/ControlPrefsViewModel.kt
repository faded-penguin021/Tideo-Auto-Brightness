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

/** D-157 (U4): Automation control toggle; default-off privacy pattern (D-105); separate store, isolated from profiles. */
class ControlPrefsViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ControlPrefsStore(application.controlPrefsDataStore)

    val externalControlEnabled: StateFlow<Boolean> = store.externalControlEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setExternalControlEnabled(enabled: Boolean) {
        viewModelScope.launch { store.setExternalControlEnabled(enabled) }
    }

    // D-172: Force dark opt-in (same wiring pattern).
    val forceDarkEnabled: StateFlow<Boolean> = store.forceDarkEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setForceDarkEnabled(enabled: Boolean) {
        viewModelScope.launch { store.setForceDarkEnabled(enabled) }
    }
}
