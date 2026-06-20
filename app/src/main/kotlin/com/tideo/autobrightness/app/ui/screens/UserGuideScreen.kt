package com.tideo.autobrightness.app.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.ui.components.AabCard
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.SettingsColumn
import com.tideo.autobrightness.app.ui.components.SettingsScaffold

/**
 * AAB User Guide scene (Tasker: sceneAAB User Guide, extraction/scenes/user_guide.md). The static
 * 9-section manual ported verbatim into M3 cards. Reached from the Menu Info &amp; Help group, and
 * shown once as the **post-onboarding first-run destination** (G2R-F80) with the Menu beneath it so the
 * "Got it" button / back arrow both return to the hub. The in-scene close button is replaced by the
 * M3 back arrow.
 */
@Composable
fun UserGuideScreen(navController: NavHostController) {
    UserGuideContent(onBack = { navController.popBackStack() })
}

// (titleRes, bodyRes) per user_guide.md section — bodies hold newline-separated bullets.
private val GUIDE_SECTIONS = listOf(
    R.string.guide_welcome_title to R.string.guide_welcome_body,
    R.string.guide_s1_title to R.string.guide_s1_body,
    R.string.guide_s2_title to R.string.guide_s2_body,
    R.string.guide_s3_title to R.string.guide_s3_body,
    R.string.guide_s4_title to R.string.guide_s4_body,
    R.string.guide_s5_title to R.string.guide_s5_body,
    R.string.guide_s6_title to R.string.guide_s6_body,
    R.string.guide_s7_title to R.string.guide_s7_body,
    R.string.guide_s8_title to R.string.guide_s8_body,
    R.string.guide_s9_title to R.string.guide_s9_body,
)

@Composable
fun UserGuideContent(onBack: () -> Unit) {
    SettingsScaffold(stringResource(R.string.title_user_guide), onBack) { padding ->
        SettingsColumn(padding) {
            Text(
                stringResource(R.string.guide_banner),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.testTag("guide_banner"),
            )

            GUIDE_SECTIONS.forEach { (titleRes, bodyRes) ->
                AabCard {
                    SectionHeader(stringResource(titleRes), divider = true)
                    Text(stringResource(bodyRes), style = MaterialTheme.typography.bodyMedium)
                }
            }

            Text(
                stringResource(R.string.guide_outro),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().testTag("guide_done"),
            ) { Text(stringResource(R.string.guide_done)) }
        }
    }
}
