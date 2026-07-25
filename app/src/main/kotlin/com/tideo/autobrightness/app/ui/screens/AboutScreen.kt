package com.tideo.autobrightness.app.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.navigation.NavHostController
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.ui.components.AabCard
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.SettingsColumn
import com.tideo.autobrightness.app.ui.components.SettingsScaffold

/**
 * AAB About scene (Tasker: sceneAAB About, extraction/scenes/about.md). The static "About & License"
 * page — banner, intro, acknowledgments, and the MIT license box. The Chart.js acknowledgment is
 * dropped (Chart.js is removed in the Kotlin rebuild; the charts are native Compose now). The in-scene
 * close button is replaced by the M3 back arrow (anonymous_handlers triage bucket (a)).
 */
@Composable
fun AboutScreen(navController: NavHostController) {
    val context = LocalContext.current
    val version = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
    }
    AboutContent(version = version, onBack = { navController.popBackStack() })
}

@Composable
fun AboutContent(version: String, onBack: () -> Unit) {
    SettingsScaffold(stringResource(R.string.title_about), onBack) { padding ->
        SettingsColumn(padding) {
            AabCard {
                Text(stringResource(R.string.about_app_name), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.about_version, version),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.testTag("about_version"),
                )
            }

            AabCard {
                SectionHeader(stringResource(R.string.about_heading), divider = true)
                Text(stringResource(R.string.about_intro), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.about_author), style = MaterialTheme.typography.bodyMedium)
            }

            AabCard {
                SectionHeader(stringResource(R.string.about_ack_header), divider = true)
                Text(stringResource(R.string.about_ack_tasker), style = MaterialTheme.typography.bodyMedium)
            }

            AabCard {
                SectionHeader(stringResource(R.string.about_license_header), divider = true)
                Text(
                    stringResource(R.string.about_license_body),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.testTag("about_license"),
                )
            }
        }
    }
}
