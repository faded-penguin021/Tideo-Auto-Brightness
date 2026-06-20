package com.tideo.autobrightness.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.tideo.autobrightness.app.navigation.AppRoute
import com.tideo.autobrightness.app.navigation.navigateTopLevel
import com.tideo.autobrightness.app.runtime.LiveRuntimeState
import com.tideo.autobrightness.app.ui.components.AabMenuBanner
import com.tideo.autobrightness.app.ui.components.AabSectionLabel
import com.tideo.autobrightness.app.ui.theme.AabGold
import com.tideo.autobrightness.app.ui.theme.Dimens

/**
 * The AAB **Menu** home screen (S12.6a, G2R-F1/F2): the Compose rebuild of the Tasker AAB Menu scene
 * (menu.md, XML L4462) promoted from the S12.5a nav drawer into the app's canonical hub. It is the
 * start destination after onboarding and the back-target from every settings/tool screen.
 *
 * Layout mirrors the menu scene's three HTML cards: the gold-sun teal banner, the **Profiles &
 * Contexts hero cards** (moved off the Dashboard), a Settings group, and an Info & Help group
 * (Recheck Permissions → Onboarding). The Dashboard is just another destination here (live status).
 */
@Composable
fun MenuScreen(navController: NavHostController) {
    val activeContext by LiveRuntimeState.activeContext.collectAsStateWithLifecycle()
    val manualOverride by LiveRuntimeState.manualOverride.collectAsStateWithLifecycle()
    MenuContent(
        activeContext = activeContext,
        manualOverride = manualOverride,
        onNavigate = { route -> navController.navigateTopLevel(route) },
        onRecheckPermissions = { navController.navigateTopLevel(AppRoute.Onboarding) },
    )
}

@Composable
fun MenuContent(
    activeContext: String?,
    manualOverride: Boolean = false,
    onNavigate: (AppRoute) -> Unit,
    onRecheckPermissions: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        AabMenuBanner()
        Column(
            modifier = Modifier.padding(
                horizontal = Dimens.screenPaddingHorizontal,
                vertical = Dimens.screenPaddingVertical,
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
        ) {
            // Live status — the former start destination, now reached from the hub.
            MenuNavRow(AppRoute.Dashboard, Icons.Filled.Home, onNavigate)

            AabSectionLabel("Profiles & Contexts")
            // S12.9f (D-070): Profiles and Contexts are one destination now — a single hero card. It
            // still surfaces the live context status. F46 semantics: a manual profile load IS the
            // override (latched %AAB_ContextOverride, cleared by Resume on the merged screen); a
            // context *rule* being active is automation working as intended, NOT an override.
            MenuHeroCard(
                icon = Icons.Filled.Person,
                title = "Profiles & Contexts",
                subtitle = when {
                    manualOverride -> "Manual override active — open to resume"
                    activeContext != null -> "Context active: $activeContext"
                    else -> "Save & load profiles · automatic context rules"
                },
                testTag = "hero_profiles_contexts",
                onClick = { onNavigate(AppRoute.Profiles) },
            )

            AabSectionLabel("Settings")
            MenuNavRow(AppRoute.CurveBrightness, Icons.Filled.Settings, onNavigate)
            MenuNavRow(AppRoute.Reactivity, Icons.Filled.Refresh, onNavigate)
            MenuNavRow(AppRoute.SuperDimming, Icons.Filled.PlayArrow, onNavigate)
            MenuNavRow(AppRoute.Circadian, Icons.Filled.DateRange, onNavigate)
            MenuNavRow(AppRoute.Misc, Icons.Filled.Settings, onNavigate)

            AabSectionLabel("Info & Help")
            MenuNavRow(AppRoute.Tools, Icons.Filled.Build, onNavigate)
            MenuNavRow(AppRoute.LiveDebug, Icons.Filled.Search, onNavigate)
            MenuNavRow(AppRoute.About, Icons.Filled.Info, onNavigate)
            ListItem(
                headlineContent = { Text("Recheck Permissions") },
                leadingContent = { Icon(Icons.Filled.Lock, contentDescription = null) },
                modifier = Modifier
                    .clickable(onClick = onRecheckPermissions)
                    .testTag("menu_recheck_permissions"),
            )
        }
    }
}

/** A plain navigation row (tagged `menu_<route>`) used for the Dashboard + Settings + Info groups. */
@Composable
private fun MenuNavRow(route: AppRoute, icon: ImageVector, onNavigate: (AppRoute) -> Unit) {
    ListItem(
        headlineContent = { Text(route.label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier
            .clickable { onNavigate(route) }
            .testTag("menu_${route.route}"),
    )
}

/** Prominent summary card — the AAB Menu "hero" cards for Profiles / Contexts (G2R-F2). */
@Composable
private fun MenuHeroCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(testTag),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Dimens.heroCardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.rowGapWide),
        ) {
            Icon(icon, contentDescription = null, tint = AabGold)
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
