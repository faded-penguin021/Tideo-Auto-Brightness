package com.tideo.autobrightness.app.ui.screens

import androidx.compose.ui.res.stringResource
import com.tideo.autobrightness.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tideo.autobrightness.app.runtime.PipelineState
import com.tideo.autobrightness.app.state.LiveDebugUiState
import com.tideo.autobrightness.app.state.LiveDebugViewModel
import com.tideo.autobrightness.app.ui.components.AabTopBar
import com.tideo.autobrightness.app.ui.components.DiagnosticCard
import com.tideo.autobrightness.app.ui.components.DiagnosticLine
import com.tideo.autobrightness.app.ui.components.fmt
import com.tideo.autobrightness.app.ui.components.fmtAlpha
import com.tideo.autobrightness.app.ui.components.fmtInt
import com.tideo.autobrightness.app.ui.components.goldValue

/** %AAB_Debug 10 named categories, verbatim (D-023). Index == debugLevel. Lives on the Live Debug
 *  scene now (G2R-F9), the global home for the debug-category selector. */
val DEBUG_LABELS = listOf(
    "Off", "Skip Animations", "Animation Details", "Light Eval Thresholds", "Dynamic Scale Calcs",
    "Super Dimming Info", "Overlay Preview", "Graph Metrics", "Context Automation", "Context Location",
)

/**
 * The **Live Debug Info** scene (S12.6b, G2R-F6): the Compose rebuild of the Tasker AAB Debug scene
 * (extraction/scenes/debug.md, XML L2583) — a glass-box readout of the live `%AAB_*` runtime vars
 * (gold-highlighted, the Tasker debug "strong" colour) grouped as in the original HTML dashboard, plus
 * the now-GLOBAL debug-category selector (moved off Misc, G2R-F9). Reached from the Menu hub.
 */
@Composable
fun LiveDebugScreen(navController: NavHostController, vm: LiveDebugViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Re-poll the global-flash AccessibilityService enablement on resume (it is toggled in system
    // Settings, outside any flow we observe — G2R-F50).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshGlobalToastStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LiveDebugContent(
        state = state,
        onSelectDebug = vm::setDebugLevel,
        onEnableGlobalToasts = {
            // Deep-link to the system Accessibility settings so the user can enable the overlay.
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        },
        onBack = { navController.popBackStack() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveDebugContent(
    state: LiveDebugUiState,
    onSelectDebug: (Int) -> Unit,
    onBack: () -> Unit,
    onEnableGlobalToasts: () -> Unit = {},
) {
    val p = state.pipeline
    Scaffold(topBar = { AabTopBar(title = stringResource(R.string.title_live_debug), onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .testTag("live_debug_screen"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Core Metrics (debug.md HTML group 1): the smoothing + threshold + brightness figures.
            DiagnosticCard("Core Metrics", "debug_core_metrics") {
                Metric("Smoothed lux", fmt(p.smoothedLux), "debug_smoothed_lux")
                Metric("Raw lux", fmt(p.lastRawLux), "debug_raw_lux")
                Metric("Dynamic threshold", fmt(p.threshDynamic), "debug_dynamic_threshold")
                Metric("Dead zone (lx)", "${fmt(p.threshAbsLow)} – ${fmt(p.threshAbsHigh)}", "debug_dead_zone")
                Metric("Current brightness", fmtInt(p.lastAppliedBrightness), "debug_current_bright")
                Metric("Target brightness", fmtInt(p.targetBrightness), "debug_target_bright")
            }

            // Circadian & dimming scale (debug.md "Dimming Engine"): uncompressed vs taper-true scale.
            DiagnosticCard("Circadian & Scale", "debug_scale") {
                Metric("Uncompressed scale", fmt(p.scaleDynamic, 3), "debug_scale_dynamic")
                Metric("True (compressed) scale", fmt(p.scaleDynamicCompress, 3), "debug_scale_compress")
            }

            // System Status (debug.md group 2): service / override / active rule.
            DiagnosticCard("System Status", "debug_system_status") {
                Metric("Service", if (state.serviceRunning) "Running" else "Stopped", "debug_service")
                Metric("Manual override", if (p.paused) "Paused" else "No", "debug_override")
                Metric("Active rule", state.activeContext ?: "None", "debug_active_rule")
            }

            // Performance & Timings (debug.md L19-23): luxAlpha, cycle total, reactivity cooldown
            // (throttle), last animation (steps×wait) and last update — full Tasker parity (G2R-F29).
            DiagnosticCard("Performance & Timings", "debug_performance") {
                Metric("Smoothing α (LuxAlpha)", fmtAlpha(p.luxAlpha), "debug_lux_alpha")
                Metric("Cycle time (ms)", fmt(p.cycleTimeMs, 0), "debug_cycle_time")
                Metric("Reactivity cooldown (ms)", p.throttleMs?.toString() ?: "—", "debug_throttle")
                Metric("Last animation", animationLabel(p.animationSteps, p.animationWaitMs), "debug_last_animation")
                Metric("Last update", lastSampleLabel(p.lastUpdateMs), "debug_last_update")
                Metric("Last sample", lastSampleLabel(p.lastSampleMs), "debug_last_sample")
            }

            DebugLevelSelector(state.debugLevel, onSelectDebug)

            GlobalFlashCard(state.globalToastsEnabled, onEnableGlobalToasts)
        }
    }
}

/**
 * Opt-in card for the system-wide flash overlay (G2R-F50). The debug/context flashes are
 * foreground-only by default; enabling the [com.tideo.autobrightness.app.runtime.AabToastAccessibilityService]
 * shows them over other apps. Presentation-only AccessibilityService (no content reading); degrades to
 * a foreground toast when off, so this is purely optional.
 */
@Composable
private fun GlobalFlashCard(enabled: Boolean, onEnable: () -> Unit) {
    DiagnosticCard(
        title = stringResource(R.string.title_global_flash),
        testTag = "global_flash_card",
    ) {
        DiagnosticLine("global_flash_status") {
            append("Status: ")
            goldValue(if (enabled) "Enabled" else "Off (foreground only)")
        }
        Text(
            "Show debug/context flashes over other apps via an optional Accessibility overlay. " +
                "It only draws the messages — it never reads screen content.",
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        OutlinedButton(
            onClick = onEnable,
            modifier = Modifier.fillMaxWidth().testTag("global_flash_enable"),
        ) {
            Text(if (enabled) "Open Accessibility settings" else "Enable in Accessibility settings")
        }
    }
}

/** A `label: value` debug line with the live value highlighted gold (the Tasker debug strong colour). */
@Composable
private fun Metric(label: String, value: String, testTag: String) {
    DiagnosticLine(testTag) {
        append("$label: ")
        goldValue(value)
    }
}

/** "steps×waitms" for the Live Debug "Last Animation" row, or "—" when no animation has run. */
private fun animationLabel(steps: Int?, waitMs: Long?): String =
    if (steps != null && waitMs != null) "${steps}×${waitMs}ms" else "—"

private fun lastSampleLabel(ms: Long?, now: Long = System.currentTimeMillis()): String {
    if (ms == null) return "never"
    val secs = ((now - ms) / 1000L).coerceAtLeast(0L)
    return when {
        secs < 1L -> "just now"
        secs < 60L -> "${secs}s ago"
        secs < 3600L -> "${secs / 60L}m ago"
        else -> "${secs / 3600L}h ago"
    }
}

/**
 * The %AAB_Debug 10-category selector (D-023), now a GLOBAL control on the Live Debug scene (G2R-F9):
 * it writes `debugLevel` straight to the DataStore (never via a profile/parameter draft), so loading a
 * profile or applying a parameter screen leaves the selected category untouched.
 */
@Composable
fun DebugLevelSelector(current: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    // Anchor the menu to the button (Box wrapper) — a bare DropdownMenu sibling has no anchor and
    // floats away from its trigger (D-114b, same fix as the rule-editor profile selector).
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().testTag("debug_selector"),
        ) {
            Text("Debug: ${DEBUG_LABELS.getOrElse(current) { "Off" }}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DEBUG_LABELS.forEachIndexed { level, label ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onSelect(level); expanded = false },
                )
            }
        }
    }
}
