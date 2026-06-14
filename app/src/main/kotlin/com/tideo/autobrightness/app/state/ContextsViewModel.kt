package com.tideo.autobrightness.app.state

import android.app.Application
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tideo.autobrightness.app.AppModule
import com.tideo.autobrightness.app.settings.ContextRule
import com.tideo.autobrightness.app.settings.ContextRuleStore
import com.tideo.autobrightness.app.settings.UserProfileStore
import com.tideo.autobrightness.platform.context.AndroidForegroundAppMonitor
import com.tideo.autobrightness.platform.context.AndroidWifiInfoReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One installed, launchable app for the context-rule app picker (icon + label, G2-F14). */
data class AppEntry(val packageName: String, val label: String, val icon: ImageBitmap? = null)

/** Drives the Contexts screen: rule CRUD over [ContextRuleStore] + the installed-app picker. */
class ContextsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val appModule = AppModule(application)
    private val store: ContextRuleStore = appModule.contextRuleStore
    private val userProfiles: UserProfileStore = appModule.userProfileStore
    private val wifi = AndroidWifiInfoReader(application)
    private val foregroundApp = AndroidForegroundAppMonitor(application)

    val rules: StateFlow<List<ContextRule>> = store.rulesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Saved profile names a rule can switch to (built-ins + user profiles, G2R-F15/D-042c). */
    val profileNames: StateFlow<List<String>> = userProfiles.profilesFlow()
        .map { profiles -> profiles.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(rule: ContextRule) = viewModelScope.launch { store.save(rule) }
    fun delete(id: String) = viewModelScope.launch { store.delete(id) }

    /** The currently-connected Wi-Fi SSID for the "use current SSID" helper (null if not on Wi-Fi). */
    suspend fun currentSsid(): String? = withContext(Dispatchers.Default) { wifi.currentSsid() }

    /** task43 reads %APP_FOREGROUND via usage stats — app rules are dead without this grant. */
    fun hasUsageAccess(): Boolean = foregroundApp.hasUsageAccessPermission()
    fun usageAccessIntent(): Intent =
        foregroundApp.usageAccessSettingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Launchable apps with icon + label, sorted by label — for the per-app context-rule picker. The
     * manifest `<queries>` LAUNCHER block makes these visible on Android 11+ (G2-F14).
     */
    suspend fun installedApps(): List<AppEntry> = withContext(Dispatchers.Default) {
        val pm = app.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(launcher, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .distinct()
            .mapNotNull { pkg ->
                val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return@mapNotNull null
                val label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(pkg)
                val icon = runCatching {
                    pm.getApplicationIcon(info).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
                }.getOrNull()
                AppEntry(pkg, label, icon)
            }
            .sortedBy { it.label.lowercase() }
    }

    private companion object {
        const val ICON_PX = 96
    }
}
