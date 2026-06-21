package com.tideo.autobrightness.app.ui.screens

import androidx.compose.ui.res.stringResource
import com.tideo.autobrightness.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tideo.autobrightness.app.runtime.LiveRuntimeState
import com.tideo.autobrightness.app.runtime.PipelineState
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.ExperimentDateLocation
import com.tideo.autobrightness.app.settings.toBrightnessCurveConfig
import com.tideo.autobrightness.app.settings.toDynamicScalingConfig
import com.tideo.autobrightness.app.state.CircadianExtrasViewModel
import com.tideo.autobrightness.app.state.DraftSettingsViewModel
import com.tideo.autobrightness.app.ui.components.AabCard
import com.tideo.autobrightness.app.ui.components.ChartPager
import com.tideo.autobrightness.app.ui.components.ChartSlot
import com.tideo.autobrightness.app.ui.components.CircadianDiagnosticCard
import com.tideo.autobrightness.app.ui.components.DraftSettingsScaffold
import com.tideo.autobrightness.app.ui.components.GraphSettingsGroup
import com.tideo.autobrightness.app.ui.components.IntSliderSettingField
import com.tideo.autobrightness.app.ui.components.NumberSettingField
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.SettingsColumn
import com.tideo.autobrightness.app.ui.components.SwitchSettingRow
import com.tideo.autobrightness.app.ui.components.rememberToaster
import com.tideo.autobrightness.app.ui.graph.CircadianScaleChart
import com.tideo.autobrightness.app.ui.graph.TaperChart
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Circadian (Tasker AAB Experiment Settings + Experiment/Taper graphs). Renamed from "Dynamic Scale"
 * in S12.6a (G2R-F4) to match the Tasker name for the day/night curve scaling. Draft → Apply (S12.5b).
 */
@Composable
fun CircadianScreen(
    navController: NavHostController,
    vm: DraftSettingsViewModel = viewModel(),
    extras: CircadianExtrasViewModel = viewModel(),
) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val committed by vm.committed.collectAsStateWithLifecycle()
    val dirty by vm.dirty.collectAsStateWithLifecycle()
    val epoch by vm.epoch.collectAsStateWithLifecycle()
    val criticalError by vm.hasCriticalError.collectAsStateWithLifecycle()
    val live by LiveRuntimeState.pipeline.collectAsStateWithLifecycle()
    val toast = rememberToaster()
    val scope = rememberCoroutineScope()

    // F39: the Circadian fixed date/location override + its live-data defaults (today + location).
    val dateLocation by extras.dateLocation.collectAsStateWithLifecycle()
    var defaultLatLon by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        defaultLatLon = runCatching { extras.defaultLatLon() }.getOrNull()
    }

    CircadianContent(
        draft, committed, epoch, dirty,
        onEdit = vm::edit, onApply = vm::apply, onDiscard = vm::discard,
        onBack = { navController.popBackStack() },
        criticalError = criticalError,
        live = live,
        dateLocation = dateLocation,
        todayDate = extras.today(),
        defaultLatLon = defaultLatLon,
        onSetDateLocation = { date, lat, lon -> extras.set(date, lat, lon); toast("Fixed date/location set") },
        onUseLiveData = { extras.useLiveData(); toast("Using live data") },
        onUseCurrentLocation = { fill ->
            scope.launch {
                val latLon = runCatching { extras.freshLatLon() }.getOrNull()
                if (latLon != null) fill(latLon.first, latLon.second)
                else toast("No location fix yet — grant Location and try again")
            }
        },
        // G2R-F17: reset only the circadian scaling + taper fields to the task570 baseline.
        onReset = {
            vm.edit { s ->
                val d = AabSettings()
                s.copy(
                    scalingEnabled = d.scalingEnabled, scaleSpread = d.scaleSpread,
                    scaleSteepness = d.scaleSteepness, scaleTransitionFactor = d.scaleTransitionFactor,
                    scaleTaperMidpoint = d.scaleTaperMidpoint, scaleTaperSteepness = d.scaleTaperSteepness,
                )
            }
            toast("Reset to defaults")
        },
    )
}

@Composable
fun CircadianContent(
    draft: AabSettings,
    committed: AabSettings,
    epoch: Int,
    dirty: Boolean,
    onEdit: ((AabSettings) -> AabSettings) -> Unit,
    onApply: () -> Unit,
    onDiscard: () -> Unit,
    onBack: () -> Unit,
    criticalError: Boolean = false,
    onReset: (() -> Unit)? = null,
    live: PipelineState = PipelineState(),
    dateLocation: ExperimentDateLocation = ExperimentDateLocation(),
    todayDate: String = "",
    defaultLatLon: Pair<Double, Double>? = null,
    onSetDateLocation: (String, Double?, Double?) -> Unit = { _, _, _ -> },
    onUseLiveData: () -> Unit = {},
    onUseCurrentLocation: ((Double, Double) -> Unit) -> Unit = {},
) {
    DraftSettingsScaffold(stringResource(R.string.title_circadian), dirty, onApply, onDiscard, onBack, criticalError, onReset) { padding ->
        SettingsColumn(padding) {
            // G2R-F81: the two relevant graphs sit ABOVE the settings and are swiped between — the
            // Circadian (scaling) curve and the Taper (compression) curve. S13 fills the chart slots.
            // Gate-2(5th) obs: the scaling graph is just "Circadian" (Tasker AAB Experiment Graph), not
            // "Experiment"; the separate "Circadian Dimming" graph lives on the Super Dimming screen.
            // Resolve the chart's location: the F39 fixed lat/lon if pinned, else the live default. The
            // day-scaling curve is drawn from the real solar windows for that place (representative
            // fallback inside the chart when no fix exists yet).
            val chartLat = dateLocation.latitude ?: defaultLatLon?.first
            val chartLon = dateLocation.longitude ?: defaultLatLon?.second
            // The F39 fixed date must drive the chart too (sunrise/sunset shift with the date) — without
            // this the curve ignored the date picker and always plotted today. Falls back to today.
            val chartDateSec = chartDateEpochSec(dateLocation.date)
            ChartPager(
                listOf(
                    ChartSlot("Circadian", "dynamic_scale_chart") {
                        CircadianScaleChart(
                            draft.toDynamicScalingConfig(),
                            Modifier.testTag("dynamic_scale_chart"),
                            latitude = chartLat, longitude = chartLon,
                            dateEpochSec = chartDateSec,
                            transitionFactor = draft.scaleTransitionFactor.toDouble(),
                        )
                    },
                    ChartSlot("Taper", "taper_chart") {
                        TaperChart(
                            draft.toBrightnessCurveConfig(), draft.scaleSpread,
                            Modifier.testTag("taper_chart"),
                            // Live "Now" line at the current brightness, only while circadian scaling
                            // is actually shifting the curve (scaleDynamic ≠ 1).
                            currentBrightness = live.targetBrightness?.takeIf {
                                live.serviceOn && live.scalingUse &&
                                    kotlin.math.abs((live.scaleDynamic ?: 1.0) - 1.0) > 0.001
                            },
                        )
                    },
                ),
            )

            // Live glass-box readout: uncompressed vs true (taper-compressed) circadian scale (G2R-F8).
            CircadianDiagnosticCard(
                minBrightness = committed.minBrightness,
                maxBrightness = committed.maxBrightness,
            )

            // G2R-F82: scaling fields feed the Circadian graph; taper fields feed the Taper graph.
            GraphSettingsGroup("Circadian") {
                SectionHeader("Circadian scaling", divider = true)
                // S13d owner fix: these used always-visible `helper=` text while every sibling settings
                // screen surfaces its explanation behind the "ⓘ" reveal (`help=`). Made consistent —
                // tap ⓘ to view the explanation here too.
                SwitchSettingRow(
                    "Enable dynamic scaling", draft.scalingEnabled,
                    { onEdit { s -> s.copy(scalingEnabled = it) } },
                    help = "Shift the whole curve across the day using sun position.",
                    testTag = "switch_scalingEnabled",
                )
                NumberSettingField(
                    // SAFETY: scale spread stays positive (1..100) — negative inverts the curve and can
                    // push the scale multiplier to ≤0 (black screen). Only the super-dimming circadian
                    // spread may go negative. Clamped on edit so the unsafe value never enters the draft.
                    "Scale spread", draft.scaleSpread, { onEdit { s -> s.copy(scaleSpread = it.toInt().coerceIn(1, 100)) } },
                    epoch = epoch, committed = committed.scaleSpread,
                    help = "How wide the scale shifts over the day (%, 1–100).", testTag = "field_scaleSpread",
                )
                NumberSettingField(
                    "Scale steepness", draft.scaleSteepness, { onEdit { s -> s.copy(scaleSteepness = it.toInt()) } },
                    epoch = epoch, committed = committed.scaleSteepness,
                    help = "Sharpness of the day/night transition.", testTag = "field_scaleSteepness",
                )
                NumberSettingField(
                    "Transition factor", draft.scaleTransitionFactor, { onEdit { s -> s.copy(scaleTransitionFactor = it.toFloat()) } },
                    epoch = epoch, committed = committed.scaleTransitionFactor, isInt = false,
                    help = "Duration of the dawn/dusk transition.", testTag = "field_scaleTransitionFactor",
                )
                // task517/674: large transition factors make the graph non-sensical.
                if (draft.scaleTransitionFactor > 0.5f) {
                    ErrorBanner("Transition factor > 0.5 may produce a non-sensical curve.", "error_scaleTransitionFactor")
                }
            }

            GraphSettingsGroup("Taper") {
                SectionHeader("Compression taper", divider = true)
                // Tasker Experiment slider: taper midpoint 130–240 (experiment_settings.md elements26, G2-F13).
                IntSliderSettingField(
                    "Taper midpoint", draft.scaleTaperMidpoint, 130..240,
                    { onEdit { s -> s.copy(scaleTaperMidpoint = it) } },
                    committed = committed.scaleTaperMidpoint,
                    help = "Brightness level where compression centres.", testTag = "slider_scaleTaperMidpoint",
                )
                // task689: taper midpoint cannot exceed current maximum brightness.
                if (draft.scaleTaperMidpoint > draft.maxBrightness) {
                    ErrorBanner("Taper midpoint cannot exceed maximum brightness.", "error_scaleTaperMidpoint")
                }
                NumberSettingField(
                    "Taper steepness", draft.scaleTaperSteepness, { onEdit { s -> s.copy(scaleTaperSteepness = it.toFloat()) } },
                    epoch = epoch, committed = committed.scaleTaperSteepness, isInt = false,
                    help = "Slope of the dynamic-range compression.", testTag = "field_scaleTaperSteepness",
                )
            }

            // F39: fixed Date and/or Lat/Lon override — drives the live circadian scaling, not just a
            // preview (experiment_settings.md elements35–37; _ExperimentSetDate/_ExperimentClearDate).
            CircadianDateLocationCard(
                value = dateLocation,
                todayDate = todayDate,
                currentLatLon = defaultLatLon,
                onSet = onSetDateLocation,
                onUseLiveData = onUseLiveData,
                onUseCurrentLocation = onUseCurrentLocation,
            )
        }
    }
}

/**
 * The Circadian "Date & location" element (experiment_settings.md elements35–37; G2R-F39). Lets the
 * user pin a fixed date + latitude/longitude to preview the circadian curve for any day/place
 * (`_ExperimentSetDate`), or revert to **live data** — today + the current location
 * (`_ExperimentClearDate`). When nothing is pinned the fields are pre-filled with those live defaults
 * so the screen always shows a concrete date/location.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircadianDateLocationCard(
    value: ExperimentDateLocation,
    todayDate: String,
    currentLatLon: Pair<Double, Double>?,
    onSet: (String, Double?, Double?) -> Unit,
    onUseLiveData: () -> Unit,
    onUseCurrentLocation: ((Double, Double) -> Unit) -> Unit = {},
) {
    // Effective defaults shown when nothing is pinned: today + current location (G2R-F39).
    val effDate = value.date ?: todayDate
    val effLat = value.latitude ?: currentLatLon?.first
    val effLon = value.longitude ?: currentLatLon?.second

    var dateText by remember(effDate) { mutableStateOf(effDate) }
    var latText by remember(effLat) { mutableStateOf(effLat?.let { "%.5f".format(it) } ?: "") }
    var lonText by remember(effLon) { mutableStateOf(effLon?.let { "%.5f".format(it) } ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }

    // S13c restyle (m3_audit §3 row 6): the inline date/location controls are grouped into an `AabCard`.
    AabCard {
    SectionHeader("Date & location", divider = true)
    Text(
        when {
            value.isUnset -> "Live data — today + current location."
            // F39: date and location pin independently — show whichever is fixed.
            value.latitude == null || value.longitude == null ->
                "Fixed date: ${value.date ?: "live"} (live location)"
            else -> "Fixed: ${value.date ?: "today"} @ ${fmtCoord(value.latitude)}, ${fmtCoord(value.longitude)}"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("exp_status"),
    )
    OutlinedButton(
        onClick = { showDatePicker = true },
        modifier = Modifier.fillMaxWidth().testTag("exp_date_value"),
    ) { Text("Date: $dateText") }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = latText, onValueChange = { latText = it.filter { c -> c.isDigit() || c == '.' || c == '-' } },
            label = { Text("Latitude") }, singleLine = true,
            modifier = Modifier.weight(1f).testTag("exp_lat"),
        )
        OutlinedTextField(
            value = lonText, onValueChange = { lonText = it.filter { c -> c.isDigit() || c == '.' || c == '-' } },
            label = { Text("Longitude") }, singleLine = true,
            modifier = Modifier.weight(1f).testTag("exp_lon"),
        )
    }
    OutlinedButton(
        onClick = { onUseCurrentLocation { la, lo -> latText = "%.5f".format(la); lonText = "%.5f".format(lo) } },
        modifier = Modifier.testTag("exp_use_location"),
    ) { Text("Use current location") }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                // F39: date and location are independent. Blank coords → date-only override (live
                // location); valid coords → pin both. Date defaults to today, so it is always present.
                val lat = latText.trim().toDoubleOrNull()
                val lon = lonText.trim().toDoubleOrNull()
                val coordsBlank = latText.isBlank() && lonText.isBlank()
                if (dateText.isNotBlank() && (coordsBlank || (lat != null && lon != null))) {
                    onSet(dateText.trim(), lat, lon)
                }
            },
            modifier = Modifier.testTag("exp_set"),
        ) { Text("Set fixed") }
        TextButton(onClick = onUseLiveData, modifier = Modifier.testTag("exp_use_live")) {
            Text("Use live data")
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = parseDateMillis(dateText))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { dateText = formatDateMillis(it) }
                        showDatePicker = false
                    },
                    modifier = Modifier.testTag("exp_date_ok"),
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
    }
}

private fun fmtCoord(v: Double?): String = v?.let { "%.5f".format(it) } ?: "—"

private val EXP_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

/** Parse a `YYYY-MM-DD` string to UTC millis for the DatePicker; null (→ today) on any failure. */
private fun parseDateMillis(date: String): Long? =
    runCatching { EXP_DATE_FORMAT.parse(date)?.time }.getOrNull()

private fun formatDateMillis(millis: Long): String = EXP_DATE_FORMAT.format(java.util.Date(millis))

/** Epoch seconds for the circadian charts: the fixed [date] (UTC midnight) if pinned, else now. */
internal fun chartDateEpochSec(date: String?): Long =
    date?.let { parseDateMillis(it)?.div(1000L) } ?: (System.currentTimeMillis() / 1000L)
