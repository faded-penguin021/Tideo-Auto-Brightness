package com.tideo.autobrightness.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.tideo.autobrightness.app.navigation.AppRoute
import com.tideo.autobrightness.app.navigation.navigateTopLevel
import com.tideo.autobrightness.app.runtime.LiveRuntimeState
import com.tideo.autobrightness.app.ui.components.AabCard
import com.tideo.autobrightness.app.ui.components.AabMenuBanner
import com.tideo.autobrightness.app.ui.components.HeroNavCard
import com.tideo.autobrightness.app.ui.components.NavRow
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.theme.Dimens

/**
 * The AAB **Menu** home screen (S12.6a, G2R-F1/F2): the Compose rebuild of the Tasker AAB Menu scene
 * (menu.md, XML L4462) promoted from the S12.5a nav drawer into the app's canonical hub. It is the
 * start destination after onboarding and the back-target from every settings/tool screen.
 *
 * Layout mirrors the menu scene's three HTML cards: the gold-sun teal banner, the **Profiles &
 * Contexts hero card** (moved off the Dashboard), a Settings group, and an Info & Help group
 * (Recheck Permissions → Onboarding). The Dashboard is just another destination here (live status).
 *
 * S13c restyle (m3_audit §3 row 1): the flat `ListItem` rows are replaced by the shared S13b
 * navigation blocks — the hero promoted to [HeroNavCard] (teal edge + press motion) and the grouped
 * destinations rendered as [NavRow]s inside elevated [AabCard] sections (no more endless flat list).
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
            // Edge-to-edge (targetSdk 35, enforced on Android 15+): this hub has no Scaffold to apply
            // the system-bar inset, so its scrolling content drew under the nav bar — the last row,
            // "Recheck Permissions", was buried (worst with 3-button navigation). navigationBarsPadding()
            // sits inside the scroll, giving the final row clearance to scroll fully into view; it reads
            // 0 on pre-15 non-edge-to-edge windows, so no extra gap there.
            modifier = Modifier
                .navigationBarsPadding()
                .padding(
                    horizontal = Dimens.screenPaddingHorizontal,
                    vertical = Dimens.screenPaddingVertical,
                ),
            verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
        ) {
            // Live status — the former start destination, now reached from the hub.
            AabCard {
                NavRow(
                    AppRoute.Dashboard.label, { onNavigate(AppRoute.Dashboard) },
                    icon = Icons.Filled.Home, testTag = "menu_${AppRoute.Dashboard.route}",
                )
            }

            SectionHeader("Profiles & Contexts", divider = true)
            // S12.9f (D-070): Profiles and Contexts are one destination now — a single hero card. It
            // still surfaces the live context status. F46 semantics: a manual profile load IS the
            // override (latched %AAB_ContextOverride, cleared by Resume on the merged screen); a
            // context *rule* being active is automation working as intended, NOT an override.
            HeroNavCard(
                icon = Icons.Filled.Person,
                title = "Profiles & Contexts",
                subtitle = when {
                    manualOverride -> "Manual override active — open to resume"
                    activeContext != null -> "Context active: $activeContext"
                    else -> "Save & load profiles · automatic context rules"
                },
                testTag = "hero_profiles_contexts",
                onClick = { onNavigate(AppRoute.Profiles) },
                // G3-F14: the owner found the full-prominence hero too dominant — use the quiet variant.
                prominent = false,
            )

            SectionHeader("Settings", divider = true)
            AabCard {
                // G3-F10: Curve & Brightness had the same gear as Misc; "Create" (edit) reads as
                // shaping the curve and removes the duplicate. A fuller Material Symbols pass for the
                // remaining rows needs material-icons-extended (a dependency decision → STATE.md).
                MenuNavRow(AppRoute.CurveBrightness, Icons.Filled.Create, onNavigate)
                MenuNavRow(AppRoute.Reactivity, Icons.Filled.Refresh, onNavigate)
                MenuNavRow(AppRoute.SuperDimming, Icons.Filled.PlayArrow, onNavigate)
                MenuNavRow(AppRoute.Circadian, Icons.Filled.DateRange, onNavigate)
                MenuNavRow(AppRoute.Misc, Icons.Filled.Settings, onNavigate)
            }

            SectionHeader("Info & Help", divider = true)
            AabCard {
                MenuNavRow(AppRoute.Tools, Icons.Filled.Build, onNavigate)
                MenuNavRow(AppRoute.LiveDebug, Icons.Filled.Search, onNavigate)
                MenuNavRow(AppRoute.UserGuide, Icons.AutoMirrored.Filled.List, onNavigate)
                MenuNavRow(AppRoute.About, Icons.Filled.Info, onNavigate)
                NavRow(
                    "Recheck Permissions", onRecheckPermissions,
                    icon = Icons.Filled.Lock, testTag = "menu_recheck_permissions",
                )
            }
        }
    }
}

/** A shared [NavRow] (tagged `menu_<route>`) used for the Dashboard + Settings + Info groups. */
@Composable
private fun MenuNavRow(
    route: AppRoute,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onNavigate: (AppRoute) -> Unit,
) {
    NavRow(route.label, { onNavigate(route) }, icon = icon, testTag = "menu_${route.route}")
}
