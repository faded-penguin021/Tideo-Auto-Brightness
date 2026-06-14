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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.NavHostController
import com.tideo.autobrightness.app.AppModule
import com.tideo.autobrightness.app.navigation.AppRoute
import com.tideo.autobrightness.platform.privilege.ShizukuGrantGateway
import com.tideo.autobrightness.platform.privilege.Tier
import kotlinx.coroutines.flow.first

/** Stateless onboarding state — everything the stepper renders. */
data class OnboardingUiState(
    val notificationsGranted: Boolean = true,
    val canWrite: Boolean = false,
    val tier: Tier = Tier.NONE,
    val shizukuAvailable: Boolean = false,
    val needsUsageAccess: Boolean = false,
    val usageAccessGranted: Boolean = false,
    val elevatedMessage: String? = null,
    val adbCommand: String = "",
)

/**
 * Privilege onboarding (task563's 8 gates + order; the polling-dialog flow itself is a sanctioned
 * deviation, D-024). Steps: POST_NOTIFICATIONS → WRITE_SETTINGS (the BASIC gate that makes the core
 * pipeline work) → optional ELEVATED (super dimming, three grant channels, skippable) → usage access
 * (only when app context-rules exist). The Activity-result launchers + privilege probing live in this
 * wrapper; [OnboardingContent] is stateless so it renders under a Robolectric compose test.
 */
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
                shizukuAvailable = privilegeManager.isShizukuAvailable(),
            ),
        )
    }

    fun reprobe() {
        privilegeManager.refresh()
        ui = ui.copy(
            notificationsGranted = notificationsGranted(context),
            canWrite = Settings.System.canWrite(context),
            tier = privilegeManager.currentTier(),
            shizukuAvailable = privilegeManager.isShizukuAvailable(),
            usageAccessGranted = hasUsageAccess(context),
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
    ) { reprobe() } // ACTION_MANAGE_WRITE_SETTINGS returns no result; re-check canWrite.

    val usageLauncher = rememberLauncherForActivityResult(
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
        onCopyAdb = { clipboard.setText(AnnotatedString(ui.adbCommand)) },
        onRequestShizuku = {
            ui = ui.copy(elevatedMessage = "Requesting Shizuku grant…")
            privilegeManager.requestShizukuGrant { result ->
                // Callback may arrive off the main thread; mutating Compose state is fine here as it
                // schedules a recomposition. reprobe() reads the refreshed tier set on success.
                ui = ui.copy(elevatedMessage = result.toMessage())
                reprobe()
            }
        },
        onTryRoot = {
            val ok = privilegeManager.tryGrantViaRoot()
            ui = ui.copy(elevatedMessage = if (ok) "Granted via root." else "Root grant failed or unavailable.")
            reprobe()
        },
        onRequestUsageAccess = { usageLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
        onDone = {
            navController.navigate(AppRoute.Dashboard.route) {
                popUpTo(AppRoute.Onboarding.route) { inclusive = true }
                launchSingleTop = true
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingContent(
    state: OnboardingUiState,
    onRequestNotifications: () -> Unit,
    onRequestWriteSettings: () -> Unit,
    onCopyAdb: () -> Unit,
    onRequestShizuku: () -> Unit,
    onTryRoot: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onDone: () -> Unit,
) {
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Setup & Permissions") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StepCard(
                title = "1. Notifications",
                body = "Allow notifications so the ongoing brightness controls and status are visible.",
                done = state.notificationsGranted,
                actionLabel = "Allow notifications",
                onAction = onRequestNotifications,
                testTag = "step_notifications",
            )
            StepCard(
                title = "2. Modify system settings (required)",
                body = "Grants brightness control. Without this the service runs but cannot change brightness.",
                done = state.canWrite,
                actionLabel = "Open setting",
                onAction = onRequestWriteSettings,
                testTag = "step_write_settings",
            )
            ElevatedStepCard(state, onCopyAdb, onRequestShizuku, onTryRoot)
            // G2R-F24: usage access is OPTIONAL by default (per D-024/task563) — always shown so it is
            // discoverable, but only flagged as needed once a per-app context rule exists.
            StepCard(
                title = if (state.needsUsageAccess) "4. Usage access (needed for per-app rules)"
                else "4. Usage access (optional)",
                body = if (state.needsUsageAccess) {
                    "One of your context rules targets specific apps. Grant usage access so the " +
                        "service can detect the foreground app."
                } else {
                    "Only needed if you later add a context rule that switches profiles per app. " +
                        "You can skip this for now and grant it from the Contexts screen later."
                },
                done = state.usageAccessGranted,
                actionLabel = "Open usage access",
                onAction = onRequestUsageAccess,
                testTag = "step_usage_access",
            )
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().testTag("onboarding_done"),
            ) { Text(if (state.canWrite) "Done" else "Skip for now") }
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
                Text("Granted ✓", color = MaterialTheme.colorScheme.tertiary)
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
            Text("3. Elevated access (optional)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Enables super dimming below the dimming threshold. Choose any one channel — it is a " +
                    "one-time grant of WRITE_SECURE_SETTINGS.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.tier == Tier.ELEVATED) {
                Text("Granted ✓", color = MaterialTheme.colorScheme.tertiary)
            } else {
                Text("ADB (from a computer):", style = MaterialTheme.typography.labelMedium)
                Text(state.adbCommand, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCopyAdb, modifier = Modifier.testTag("copy_adb")) {
                        Text("Copy command")
                    }
                    if (state.shizukuAvailable) {
                        OutlinedButton(onClick = onRequestShizuku, modifier = Modifier.testTag("grant_shizuku")) {
                            Text("Use Shizuku")
                        }
                    }
                    TextButton(onClick = onTryRoot, modifier = Modifier.testTag("grant_root")) {
                        Text("Try root")
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
