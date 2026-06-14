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
import com.tideo.autobrightness.app.settings.byPriority
import com.tideo.autobrightness.domain.circadian.SolarCalculator
import com.tideo.autobrightness.platform.context.AndroidForegroundAppMonitor
import com.tideo.autobrightness.platform.context.AndroidLocationReader
import com.tideo.autobrightness.platform.context.AndroidWifiInfoReader
import com.tideo.autobrightness.platform.context.LocationResult
import com.tideo.autobrightness.platform.context.SsidResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/** One installed, launchable app for the context-rule app picker (icon + label, G2-F14). */
data class AppEntry(val packageName: String, val label: String, val icon: ImageBitmap? = null)

/** Drives the Contexts screen: rule CRUD over [ContextRuleStore] + the installed-app picker. */
class ContextsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val appModule = AppModule(application)
    private val store: ContextRuleStore = appModule.contextRuleStore
    private val userProfiles: UserProfileStore = appModule.userProfileStore
    private val wifi = AndroidWifiInfoReader(application)
    private val location = AndroidLocationReader(application)
    private val foregroundApp = AndroidForegroundAppMonitor(application)

    // Ordered by priority (highest first) to match the resolution model (G2R-F43, D-014), not by
    // creation time; ties keep a stable name order so the list doesn't jump around.
    val rules: StateFlow<List<ContextRule>> = store.rulesFlow()
        .map { it.byPriority() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Saved profile names a rule can switch to (built-ins + user profiles, G2R-F15/D-042c). */
    val profileNames: StateFlow<List<String>> = userProfiles.profilesFlow()
        .map { profiles -> profiles.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(rule: ContextRule) = viewModelScope.launch { store.save(rule) }
    fun delete(id: String) = viewModelScope.launch { store.delete(id) }

    /**
     * The currently-connected Wi-Fi SSID for the "use current SSID" helper (G2R-F22). Returns a typed
     * [SsidResult] so the screen can show a targeted message (not-on-Wi-Fi vs needs-location vs
     * location-services-off) instead of a blanket "Not connected".
     */
    suspend fun currentSsid(): SsidResult = wifi.currentSsid()

    /**
     * The current device location for the "use current location" helper (G2R-F42). Rechecks the
     * permission grant at call time and requests a fresh fix, so it no longer wrongly reports
     * "not granted" right after the user grants Location (the propagation-delay bug).
     */
    suspend fun currentLocation(): LocationResult = location.currentLocation()

    /**
     * Today's resolved sunrise / sunset as "HH:MM" for the SUNRISE/SUNSET token labels (G2R-F68),
     * computed for the last-known location. Null when no location is available (tokens still work;
     * they just show without the resolved time). Mirrors AndroidContextSignalSource's solar math.
     */
    suspend fun solarTimes(): Pair<String, String>? = withContext(Dispatchers.Default) {
        val loc = runCatching { location.lastKnownLocation() }.getOrNull() ?: return@withContext null
        val cal = Calendar.getInstance()
        val offsetHours = cal.timeZone.getOffset(cal.timeInMillis) / 3_600_000.0
        val offsetSecs = (offsetHours * 3600.0).toLong()
        runCatching {
            val solar = SolarCalculator.compute(loc.latitude, loc.longitude, cal.timeInMillis / 1000L, offsetHours)
            val rise = Math.floorMod(solar.riseEpochSec + offsetSecs, 86_400L)
            val set = Math.floorMod(solar.setEpochSec + offsetSecs, 86_400L)
            formatSeconds(rise) to formatSeconds(set)
        }.getOrNull()
    }

    private fun formatSeconds(s: Long): String = "%02d:%02d".format(s / 3600, (s % 3600) / 60)

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
