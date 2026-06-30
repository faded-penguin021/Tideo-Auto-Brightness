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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.ui.theme.AabDataCaption
import com.tideo.autobrightness.app.ui.theme.AabGold
import com.tideo.autobrightness.app.ui.theme.AabOnTeal
import com.tideo.autobrightness.app.ui.theme.AabTeal
import com.tideo.autobrightness.app.ui.theme.Dimens

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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.a11y_back))
                }
            }
        },
        // Owner feedback: the teal banner belongs to the Menu hub only (its gold-sun [AabMenuBanner]).
        // Every other screen uses the default M3 surface app bar so the app reads coherently — Dashboard
        // and Live Debug no longer carry a second teal header.
    )
}

/**
 * The teal brand banner — the one place identity lives (S13c' §08). A clean gold sun/aperture mark, the
 * wordmark in Plex Sans (SemiBold "Tideo" + Medium gold "Auto Brightness"), and a tracked Plex Mono
 * tagline — intentional, not a coloured bar.
 */
@Composable
fun AabMenuBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .background(AabTeal)
            .padding(horizontal = Dimens.cardPadding, vertical = Dimens.cardPadding)
            .testTag("menu_banner"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_stat_brightness),
            contentDescription = null,
            tint = AabGold,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.width(Dimens.rowGapWide))
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space1)) {
            // The full wordmark reads on ONE line — "Tideo" white + "Auto Brightness" gold (owner
            // feedback: the stacked two-line wordmark looked broken).
            Row {
                Text(
                    stringResource(R.string.app_wordmark_primary) + " ",
                    color = AabOnTeal,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.app_wordmark_secondary),
                    color = AabGold,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                stringResource(R.string.app_tagline).uppercase(),
                color = AabOnTeal.copy(alpha = 0.66f),
                style = AabDataCaption,
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
