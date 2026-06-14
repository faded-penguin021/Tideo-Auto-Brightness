package com.tideo.autobrightness.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.ui.theme.AabGold
import com.tideo.autobrightness.app.ui.theme.AabOnTeal
import com.tideo.autobrightness.app.ui.theme.AabTeal

/**
 * The app shell chrome — the branded top bar and the Menu-screen building blocks. This is the
 * Compose rebuild of the Tasker **AAB Menu scene** (extraction/scenes/menu.md, XML L4462): a gold-sun
 * teal banner over grouped navigation "cards" (Profiles & Contexts hero / Settings / Info & Help).
 *
 * S12.6a (G2R-F1/F2): the S12.5a slide-over `AabNavDrawer` is promoted into a real
 * [com.tideo.autobrightness.app.ui.screens.MenuScreen] home destination — the canonical hub and the
 * back-target from every settings/tool screen. These shared pieces ([AabMenuBanner],
 * [AabSectionLabel]) render that screen; the top bar is reused by Menu/Dashboard.
 */

/**
 * Branded center-aligned top bar. When [onBack] is non-null a back arrow is shown (the standard
 * up-navigation that returns to the Menu); pass null on the home/Menu screen for no nav icon.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AabTopBar(title: String, onBack: (() -> Unit)? = null) {
    CenterAlignedTopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = AabTeal,
            titleContentColor = AabOnTeal,
            navigationIconContentColor = AabGold,
        ),
    )
}

/** Teal banner header with the gold sun mark — the rebuild's "Tideo Auto Brightness" brand header. */
@Composable
fun AabMenuBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(AabTeal)
            .padding(16.dp)
            .testTag("menu_banner"),
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
                "Tideo",
                color = AabOnTeal,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Auto Brightness",
                color = AabGold,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** A grouped-section divider + label, mirroring the menu scene's three HTML cards. */
@Composable
fun AabSectionLabel(text: String) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
    )
}
