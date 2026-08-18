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

/** S12.6a (G2R-F1/F2): app shell chrome. Branded top bar + Menu-screen building blocks. */

/** Branded center-aligned top bar. [onBack] null on home/Menu for no nav icon. */
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
        // Teal banner only for Menu hub; others use default M3 surface app bar
    )
}

/** Teal brand banner (S13c' §08). Gold sun/aperture, Plex Sans wordmark, Plex Mono tagline. */
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
            // Wordmark on one line: "Tideo" white + "Auto Brightness" gold
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
