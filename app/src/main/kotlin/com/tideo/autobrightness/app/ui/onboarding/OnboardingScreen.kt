package com.tideo.autobrightness.app.ui.onboarding

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.tideo.autobrightness.R
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.NavHostController
import com.tideo.autobrightness.app.AppModule
import com.tideo.autobrightness.app.navigation.completeOnboarding
import com.tideo.autobrightness.platform.privilege.ShizukuAvailability
import com.tideo.autobrightness.platform.privilege.ShizukuGrantGateway
import com.tideo.autobrightness.platform.privilege.Tier
import kotlinx.coroutines.flow.first

/** Stateless onboarding state — everything the stepper renders. */
data class OnboardingUiState(
    val notificationsGranted: Boolean = true,
    val canWrite: Boolean = false,
    val tier: Tier = Tier.NONE,
    val shizukuAvailability: ShizukuAvailability = ShizukuAvailability.NOT_INSTALLED,
    val needsUsageAccess: Boolean = false,
    val usageAccessGranted: Boolean = false,
    val locationGranted: Boolean = false,
    // Sideloaded installs may hit Android's "Restricted setting" block (G2R-F33).
    val sideloaded: Boolean = false,
    val elevatedMessage: String? = null,
    val adbCommand: String = "",
)

/** Privilege onboarding (task563 gates, D-024): POST_NOTIFICATIONS → WRITE_SETTINGS → ELEVATED → usage access. */
@Composable
fun OnboardingScreen(navController: NavHostController) {
    val context = LocalContext.current
    val appModule = remember { AppModule(context.applicationContext) }
    val privilegeManager = remember { appModule.privilegeManager }
    val contextRuleStore = remember { appModule.contextRuleStore }
    val clipboard = LocalClipboardManager.current

    var ui by remember {
        mutableStateOf(
            OnboardingUiState(
                adbCommand = privilegeManager.adbGrantInstruction(),
                shizukuAvailability = privilegeManager.shizukuAvailability(),
                sideloaded = isLikelySideloaded(context),
            ),
        )
    }

    fun reprobe() {
        privilegeManager.refresh()
        ui = ui.copy(
            notificationsGranted = notificationsGranted(context),
            canWrite = Settings.System.canWrite(context),
            tier = privilegeManager.currentTier(),
            shizukuAvailability = privilegeManager.shizukuAvailability(),
            usageAccessGranted = hasUsageAccess(context),
            locationGranted = hasLocationPermission(context),
        )
    }

    // Determine whether the usage-access step is relevant (any rule targets specific apps).
    LaunchedEffect(Unit) {
        val needsUsage = runCatching { contextRuleStore.rulesFlow().first() }
            .getOrDefault(emptyList())
            .any { !it.triggers.apps.isNullOrEmpty() }
        ui = ui.copy(needsUsageAccess = needsUsage)
        reprobe()
    }

    // Re-check grants whenever we come back from a system settings screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) reprobe()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> ui = ui.copy(notificationsGranted = granted) }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { reprobe() }

    val usageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { reprobe() }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { reprobe() }

    val appInfoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { reprobe() }

    OnboardingContent(
        state = ui,
        onRequestNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onRequestWriteSettings = { settingsLauncher.launch(privilegeManager.writeSettingsIntent()) },
        onRequestLocation = {
            locationLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        },
        onOpenAppInfo = {
            appInfoLauncher.launch(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        },
        onCopyAdb = { clipboard.setText(AnnotatedString(ui.adbCommand)) },
        onRequestShizuku = {
            ui = ui.copy(elevatedMessage = "Requesting Shizuku grant…")
            privilegeManager.requestShizukuGrant { result ->
                ui = ui.copy(elevatedMessage = result.toMessage())
                reprobe() // Reads refreshed tier on success
            }
        },
        onTryRoot = {
            val ok = privilegeManager.tryGrantViaRoot()
            ui = ui.copy(elevatedMessage = if (ok) "Granted via root." else "Root grant failed or unavailable.")
            reprobe()
        },
        onRequestUsageAccess = { usageLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
        onDone = { navController.completeOnboarding() }, // G2R-F57: land on Menu hub
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingContent(
    state: OnboardingUiState,
    onRequestNotifications: () -> Unit,
    onRequestWriteSettings: () -> Unit,
    onRequestLocation: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onCopyAdb: () -> Unit,
    onRequestShizuku: () -> Unit,
    onTryRoot: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onDone: () -> Unit,
) {
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.onboarding_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // D-131: app language picker (English only); see OnboardingLanguageCard.
            OnboardingLanguageCard()
            // G2R-F33: show restricted settings hint if needed (sideloaded app).
            if (state.sideloaded) {
                RestrictedSettingsCard(onOpenAppInfo)
            }
            StepCard(
                title = stringResource(R.string.onboarding_step1_title),
                body = stringResource(R.string.onboarding_step1_body),
                done = state.notificationsGranted,
                actionLabel = stringResource(R.string.onboarding_step1_action),
                onAction = onRequestNotifications,
                testTag = "step_notifications",
            )
            StepCard(
                title = stringResource(R.string.onboarding_step2_title),
                body = stringResource(R.string.onboarding_step2_body),
                done = state.canWrite,
                actionLabel = stringResource(R.string.onboarding_step2_action),
                onAction = onRequestWriteSettings,
                testTag = "step_write_settings",
            )
            // Location for SSID fallback + context rules; optional (G2R-F41).
            StepCard(
                title = stringResource(R.string.onboarding_step3_title),
                body = stringResource(R.string.onboarding_step3_body),
                done = state.locationGranted,
                actionLabel = stringResource(R.string.onboarding_step3_action),
                onAction = onRequestLocation,
                testTag = "step_location",
            )
            ElevatedStepCard(state, onCopyAdb, onRequestShizuku, onTryRoot)
            // Usage access is OPTIONAL by default (D-024/task563).
            StepCard(
                title = if (state.needsUsageAccess) stringResource(R.string.onboarding_usage_title_needed)
                else stringResource(R.string.onboarding_usage_title_optional),
                body = if (state.needsUsageAccess) {
                    stringResource(R.string.onboarding_usage_body_needed)
                } else {
                    stringResource(R.string.onboarding_usage_body_optional)
                },
                done = state.usageAccessGranted,
                actionLabel = stringResource(R.string.onboarding_usage_action),
                onAction = onRequestUsageAccess,
                testTag = "step_usage_access",
            )
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().testTag("onboarding_done"),
            ) { Text(if (state.canWrite) stringResource(R.string.onboarding_done) else stringResource(R.string.onboarding_skip)) }
        }
    }
}

/** App-language picker (D-131), not yet functional (English only). Wired when translated resources land. */
@Composable
private fun OnboardingLanguageCard() {
    var expanded by remember { mutableStateOf(false) }
    val english = stringResource(R.string.language_english)
    Card(modifier = Modifier.testTag("language_card")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.misc_language_header), style = MaterialTheme.typography.titleMedium)
            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth().testTag("language_selector"),
                ) {
                    Text(stringResource(R.string.misc_language_label) + ": " + english)
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text(english) }, onClick = { expanded = false })
                }
            }
            Text(
                stringResource(R.string.misc_language_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RestrictedSettingsCard(onOpenAppInfo: () -> Unit) {
    Card(modifier = Modifier.testTag("restricted_settings_hint")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.onboarding_restricted_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.onboarding_restricted_body), // G3-F13: tap greyed toggle
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = onOpenAppInfo, modifier = Modifier.testTag("open_app_info")) {
                Text(stringResource(R.string.onboarding_open_app_info))
            }
        }
    }
}

@Composable
private fun StepCard(
    title: String,
    body: String,
    done: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    testTag: String,
) {
    Card(modifier = Modifier.testTag(testTag)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            if (done) {
                Text(stringResource(R.string.onboarding_granted), color = MaterialTheme.colorScheme.tertiary)
            } else {
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun ElevatedStepCard(
    state: OnboardingUiState,
    onCopyAdb: () -> Unit,
    onRequestShizuku: () -> Unit,
    onTryRoot: () -> Unit,
) {
    Card(modifier = Modifier.testTag("step_elevated")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.onboarding_elevated_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.onboarding_elevated_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.tier == Tier.ELEVATED) {
                Text(stringResource(R.string.onboarding_granted), color = MaterialTheme.colorScheme.tertiary)
            } else {
                Text(stringResource(R.string.onboarding_adb_label), style = MaterialTheme.typography.labelMedium)
                Text(state.adbCommand, style = MaterialTheme.typography.bodySmall)
                // Shizuku not running: prompt to start it before one-tap grant.
                if (state.shizukuAvailability == ShizukuAvailability.INSTALLED_NOT_RUNNING) {
                    Text(
                        stringResource(R.string.onboarding_shizuku_prompt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.testTag("shizuku_start_prompt"),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCopyAdb, modifier = Modifier.testTag("copy_adb")) {
                        Text(stringResource(R.string.onboarding_copy_command))
                    }
                    if (state.shizukuAvailability == ShizukuAvailability.RUNNING) {
                        OutlinedButton(onClick = onRequestShizuku, modifier = Modifier.testTag("grant_shizuku")) {
                            Text(stringResource(R.string.onboarding_use_shizuku))
                        }
                    }
                    TextButton(onClick = onTryRoot, modifier = Modifier.testTag("grant_root")) {
                        Text(stringResource(R.string.onboarding_try_root))
                    }
                }
            }
            state.elevatedMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

private fun ShizukuGrantGateway.Result.toMessage(): String = when (this) {
    ShizukuGrantGateway.Result.Success -> "Granted via Shizuku ✓"
    ShizukuGrantGateway.Result.Unavailable -> "Shizuku is not running."
    ShizukuGrantGateway.Result.PermissionDenied -> "Shizuku permission denied."
    is ShizukuGrantGateway.Result.Failed -> "Shizuku grant failed: $reason"
}

private fun notificationsGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
}

private fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.unsafeCheckOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName,
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun hasLocationPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** Best-effort sideload detection (G2R-F33): errs toward showing hint, purely advisory. */
private fun isLikelySideloaded(context: Context): Boolean = try {
    val installer = context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
    installer == null || installer !in PLAY_STORE_INSTALLERS
} catch (_: Throwable) {
    true
}

private val PLAY_STORE_INSTALLERS = setOf("com.android.vending", "com.google.android.feedback")
