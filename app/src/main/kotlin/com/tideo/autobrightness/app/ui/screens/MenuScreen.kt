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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.AppModule
import com.tideo.autobrightness.app.navigation.AppRoute
import com.tideo.autobrightness.app.navigation.navigateTopLevel
import com.tideo.autobrightness.app.runtime.LiveRuntimeState
import com.tideo.autobrightness.platform.privilege.Tier
import com.tideo.autobrightness.app.ui.components.AabCard
import com.tideo.autobrightness.app.ui.components.AabMenuBanner
import com.tideo.autobrightness.app.ui.components.HeroNavCard
import com.tideo.autobrightness.app.ui.components.NavRow
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.theme.Dimens

/** AAB Menu home screen (S12.6a, G2R-F1/F2); Compose rebuild of Tasker AAB Menu scene promoted from
 *  nav drawer. S13c restyle: shared navigation blocks in [AabCard] sections. */
@Composable
fun MenuScreen(navController: NavHostController) {
    val activeContext by LiveRuntimeState.activeContext.collectAsStateWithLifecycle()
    val manualOverride by LiveRuntimeState.manualOverride.collectAsStateWithLifecycle()
    // D-149: "Privileged" group tier-gated; tierFlow() re-probed on resume for background grants.
    val context = LocalContext.current
    val privilegeManager = remember { AppModule(context.applicationContext).privilegeManager }
    val tier by privilegeManager.tierFlow().collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) privilegeManager.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    MenuContent(
        activeContext = activeContext,
        manualOverride = manualOverride,
        tier = tier,
        onNavigate = { route -> navController.navigateTopLevel(route) },
        onRecheckPermissions = { navController.navigateTopLevel(AppRoute.Onboarding) },
    )
}

@Composable
fun MenuContent(
    activeContext: String?,
    manualOverride: Boolean = false,
    tier: Tier = Tier.NONE,
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
            // Edge-to-edge (Android 15+): navigationBarsPadding() lets final row scroll into view.
            modifier = Modifier
                .navigationBarsPadding()
                .padding(
                    horizontal = Dimens.screenPaddingHorizontal,
                    vertical = Dimens.screenPaddingVertical,
                ),
            verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
        ) {
            AabCard {
                NavRow(
                    stringResource(AppRoute.Dashboard.titleRes), { onNavigate(AppRoute.Dashboard) },
                    icon = Icons.Filled.Home, testTag = "menu_${AppRoute.Dashboard.route}",
                )
            }

            SectionHeader(stringResource(R.string.title_profiles_contexts), divider = true)
            // S12.9f (D-070): Profiles and Contexts are one destination (hero card).
            HeroNavCard(
                icon = Icons.Filled.Person,
                title = stringResource(R.string.title_profiles_contexts),
                subtitle = when {
                    manualOverride -> stringResource(R.string.menu_subtitle_override)
                    activeContext != null -> stringResource(R.string.menu_subtitle_context, activeContext)
                    else -> stringResource(R.string.menu_subtitle_default)
                },
                testTag = "hero_profiles_contexts",
                onClick = { onNavigate(AppRoute.Profiles) },
                prominent = false,
            )

            SectionHeader(stringResource(R.string.menu_section_settings), divider = true)
            AabCard {
                // G3-F10: "Create" icon for Curve & Brightness (edit action).
                MenuNavRow(AppRoute.CurveBrightness, Icons.Filled.Create, onNavigate)
                MenuNavRow(AppRoute.Reactivity, Icons.Filled.Refresh, onNavigate)
                MenuNavRow(AppRoute.SuperDimming, Icons.Filled.PlayArrow, onNavigate)
                MenuNavRow(AppRoute.Circadian, Icons.Filled.DateRange, onNavigate)
                MenuNavRow(AppRoute.Misc, Icons.Filled.Settings, onNavigate)
            }

            // D-149: ELEVATED-only group.
            if (tier == Tier.ELEVATED) {
                SectionHeader(stringResource(R.string.menu_section_privileged), divider = true)
                AabCard {
                    AppRoute.privilegedDestinations.forEach { route ->
                        MenuNavRow(route, Icons.Filled.Lock, onNavigate)
                    }
                }
            }

            SectionHeader(stringResource(R.string.menu_section_info), divider = true)
            AabCard {
                MenuNavRow(AppRoute.Tools, Icons.Filled.Build, onNavigate)
                MenuNavRow(AppRoute.LiveDebug, Icons.Filled.Search, onNavigate)
                MenuNavRow(AppRoute.UserGuide, Icons.AutoMirrored.Filled.List, onNavigate)
                MenuNavRow(AppRoute.About, Icons.Filled.Info, onNavigate)
                NavRow(
                    stringResource(R.string.menu_recheck_permissions), onRecheckPermissions,
                    icon = Icons.Filled.Lock, testTag = "menu_recheck_permissions",
                )
            }
        }
    }
}

@Composable
private fun MenuNavRow(
    route: AppRoute,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onNavigate: (AppRoute) -> Unit,
) {
    NavRow(stringResource(route.titleRes), { onNavigate(route) }, icon = icon, testTag = "menu_${route.route}")
}
