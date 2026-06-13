package com.tideo.autobrightness.app.state

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tideo.autobrightness.app.AppModule
import com.tideo.autobrightness.app.settings.ContextRule
import com.tideo.autobrightness.app.settings.ContextRuleStore
import com.tideo.autobrightness.app.settings.DefaultProfiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One installed, launchable app for the context-rule app picker. */
data class AppEntry(val packageName: String, val label: String)

/** Drives the Contexts screen: rule CRUD over [ContextRuleStore] + the installed-app picker. */
class ContextsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val store: ContextRuleStore = AppModule(application).contextRuleStore

    val rules: StateFlow<List<ContextRule>> = store.rulesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Built-in profile names a rule can switch to (extended with user profiles in a later pass). */
    val profileNames: List<String> = DefaultProfiles.all.keys.toList()

    fun save(rule: ContextRule) = viewModelScope.launch { store.save(rule) }
    fun delete(id: String) = viewModelScope.launch { store.delete(id) }

    /** Launchable apps, sorted by label — for the per-app context-rule picker. */
    suspend fun installedApps(): List<AppEntry> = withContext(Dispatchers.Default) {
        val pm = app.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(launcher, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .distinct()
            .map { pkg ->
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg)
                AppEntry(pkg, label)
            }
            .sortedBy { it.label.lowercase() }
    }
}
