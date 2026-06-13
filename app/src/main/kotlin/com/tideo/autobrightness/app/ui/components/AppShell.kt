package com.tideo.autobrightness.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.navigation.AppRoute
import com.tideo.autobrightness.app.ui.theme.AabGold
import com.tideo.autobrightness.app.ui.theme.AabOnTeal
import com.tideo.autobrightness.app.ui.theme.AabTeal

/**
 * The app shell chrome — a branded top bar + the navigation drawer. This is the Compose rebuild of
 * the Tasker **AAB Menu scene** (extraction/scenes/menu.md, XML L4462): a project banner up top and
 * grouped navigation "cards" (Profiles & Contexts hero / Settings / Info & Help). The flat
 * `OutlinedButton` nav list S11/S12 put on the Dashboard is replaced by this drawer (Gate-2 G2-F18).
 *
 * S12.5a scope: identity + navigation only. Field grouping (the re-added Misc/General screen) and the
 * preview→Apply model are S12.5b; this keeps the existing AppRoute set and routes to it unchanged.
 */

/** Branded center-aligned top bar with the hamburger that opens the drawer (the "name up top"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AabTopBar(title: String, onOpenMenu: () -> Unit) {
    CenterAlignedTopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            androidx.compose.material3.IconButton(
                onClick = onOpenMenu,
                modifier = Modifier.testTag("menu_button"),
            ) {
                Icon(Icons.Filled.Menu, contentDescription = "Open menu")
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = AabTeal,
            titleContentColor = AabOnTeal,
            navigationIconContentColor = AabGold,
        ),
    )
}

/**
 * The drawer content — the AAB Menu scene's grouped destination set. Sections mirror the menu's
 * three HTML cards (menu.md L22): a Profiles & Contexts group (the hero), a Settings group, and an
 * Info & Help group (Recheck Permissions → Onboarding; the Chart.js License entry is dropped per the
 * screen_map). `current` highlights the active route.
 */
@Composable
fun AabNavDrawer(
    current: AppRoute?,
    onNavigate: (AppRoute) -> Unit,
    onRecheckPermissions: () -> Unit,
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("nav_drawer"),
    ) {
        DrawerBanner()
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            drawerItem(AppRoute.Dashboard, Icons.Filled.Home, current, onNavigate)

            DrawerSectionLabel("Profiles & Contexts")
            drawerItem(AppRoute.Profiles, Icons.Filled.Person, current, onNavigate)
            drawerItem(AppRoute.Contexts, Icons.Filled.Place, current, onNavigate)

            DrawerSectionLabel("Settings")
            drawerItem(AppRoute.CurveBrightness, Icons.Filled.Settings, current, onNavigate)
            drawerItem(AppRoute.Reactivity, Icons.Filled.Refresh, current, onNavigate)
            drawerItem(AppRoute.AnimationDimming, Icons.Filled.PlayArrow, current, onNavigate)
            drawerItem(AppRoute.DynamicScale, Icons.Filled.DateRange, current, onNavigate)

            DrawerSectionLabel("Info & Help")
            drawerItem(AppRoute.Tools, Icons.Filled.Build, current, onNavigate)
            drawerItem(AppRoute.About, Icons.Filled.Info, current, onNavigate)
            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                label = { Text("Recheck Permissions") },
                selected = false,
                onClick = onRecheckPermissions,
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .testTag("nav_recheck_permissions"),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Teal banner header with the gold sun mark — the menu scene's "Advanced Auto Brightness" header. */
@Composable
private fun DrawerBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(AabTeal)
            .padding(16.dp)
            .testTag("drawer_banner"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_stat_brightness),
            contentDescription = null,
            tint = AabGold,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                "Advanced",
                color = AabOnTeal,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Auto Brightness",
                color = AabGold,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DrawerSectionLabel(text: String) {
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun drawerItem(
    route: AppRoute,
    icon: ImageVector,
    current: AppRoute?,
    onNavigate: (AppRoute) -> Unit,
) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(route.label) },
        selected = route == current,
        onClick = { onNavigate(route) },
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = AabTeal.copy(alpha = 0.20f),
            selectedIconColor = AabTeal,
            selectedTextColor = AabTeal,
        ),
        modifier = Modifier
            .padding(NavigationDrawerItemDefaults.ItemPadding)
            .testTag("nav_${route.route}"),
    )
}
