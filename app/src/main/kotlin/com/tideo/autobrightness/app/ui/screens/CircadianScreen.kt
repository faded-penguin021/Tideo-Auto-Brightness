package com.tideo.autobrightness.app.ui.screens

import androidx.compose.ui.res.stringResource
import com.tideo.autobrightness.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

/** Circadian (Tasker AAB Experiment Settings + Experiment/Taper graphs); renamed for clarity (G2R-F4). */
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
    val geoIpEnabled by extras.geoIpEnabled.collectAsStateWithLifecycle() // G3-F12 / D-105 privacy opt-in
    val locationStatus by extras.circadianLocationStatus.collectAsStateWithLifecycle() // D-110 staleness hint
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
        locationStatus = locationStatus,
        dateLocation = dateLocation,
        todayDate = extras.today(),
        defaultLatLon = defaultLatLon,
        onSetDateLocation = { date, lat, lon -> extras.set(date, lat, lon); toast(R.string.toast_fixed_datelocation) },
        onUseLiveData = { extras.useLiveData(); toast(R.string.toast_using_live_data) },
        onUseCurrentLocation = { fill ->
            scope.launch {
                // D-122: this now actively acquires a fresh fix (the OS location indicator appears) and can
                // take a few seconds — tell the user it's working rather than appearing to hang.
                toast(R.string.toast_acquiring_location)
                val latLon = runCatching { extras.freshLatLon() }.getOrNull()
                if (latLon != null) fill(latLon.first, latLon.second)
                else toast(R.string.toast_acquire_location_failed)
            }
        },
        geoIpEnabled = geoIpEnabled,
        onSetGeoIpEnabled = extras::setGeoIpEnabled,
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
            toast(R.string.toast_reset_defaults)
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
    locationStatus: com.tideo.autobrightness.app.runtime.CircadianLocationStatus =
        com.tideo.autobrightness.app.runtime.CircadianLocationStatus(),
    dateLocation: ExperimentDateLocation = ExperimentDateLocation(),
    todayDate: String = "",
    defaultLatLon: Pair<Double, Double>? = null,
    onSetDateLocation: (String, Double?, Double?) -> Unit = { _, _, _ -> },
    onUseLiveData: () -> Unit = {},
    onUseCurrentLocation: ((Double, Double) -> Unit) -> Unit = {},
    geoIpEnabled: Boolean = false,
    onSetGeoIpEnabled: (Boolean) -> Unit = {},
) {
    DraftSettingsScaffold(stringResource(R.string.title_circadian), dirty, onApply, onDiscard, onBack, criticalError, onReset) { padding ->
        SettingsColumn(padding) {
            // G2R-F81: Circadian (scaling) and Taper (compression) graphs above settings; use F39 fixed lat/lon if pinned.
            val chartLat = dateLocation.latitude ?: defaultLatLon?.first
            val chartLon = dateLocation.longitude ?: defaultLatLon?.second
            // F39 fixed date drives chart; falls back to today.
            val chartDateSec = chartDateEpochSec(dateLocation.date)
            ChartPager(
                listOf(
                    ChartSlot(stringResource(R.string.graph_circadian), "dynamic_scale_chart") {
                        CircadianScaleChart(
                            draft.toDynamicScalingConfig(),
                            Modifier.testTag("dynamic_scale_chart"),
                            latitude = chartLat, longitude = chartLon,
                            dateEpochSec = chartDateSec,
                            transitionFactor = draft.scaleTransitionFactor.toDouble(),
                        )
                    },
                    ChartSlot(stringResource(R.string.graph_taper), "taper_chart") {
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

            // D-110: show staleness hint when location is stale or missing while scaling is on.
            if (draft.scalingEnabled && (locationStatus.isStale || !locationStatus.hasLocation)) {
                CircadianStaleBanner(locationStatus)
            }

            // Live glass-box readout: uncompressed vs true (taper-compressed) circadian scale (G2R-F8).
            CircadianDiagnosticCard(
                minBrightness = committed.minBrightness,
                maxBrightness = committed.maxBrightness,
            )

            // G2R-F82: scaling fields feed the Circadian graph; taper fields feed the Taper graph.
            GraphSettingsGroup(stringResource(R.string.graph_circadian)) {
                SectionHeader(stringResource(R.string.circadian_scaling_header), divider = true)
                // S13d: help text moved from always-visible to "ⓘ" reveal for consistency.
                SwitchSettingRow(
                    stringResource(R.string.circadian_enable_scaling), draft.scalingEnabled,
                    { onEdit { s -> s.copy(scalingEnabled = it) } },
                    help = R.string.help_circadian_scaling,
                    testTag = "switch_scalingEnabled",
                )
                NumberSettingField(
                    // SAFETY: scale spread clamped 1..100 (negative would invert curve, push multiplier ≤0).
                    stringResource(R.string.circadian_scale_spread), draft.scaleSpread, { onEdit { s -> s.copy(scaleSpread = it.toInt().coerceIn(1, 100)) } },
                    epoch = epoch, committed = committed.scaleSpread,
                    help = R.string.help_scale_spread, testTag = "field_scaleSpread",
                )
                NumberSettingField(
                    stringResource(R.string.circadian_scale_steepness), draft.scaleSteepness, { onEdit { s -> s.copy(scaleSteepness = it.toInt()) } },
                    epoch = epoch, committed = committed.scaleSteepness,
                    help = R.string.help_scale_steepness, testTag = "field_scaleSteepness",
                )
                NumberSettingField(
                    stringResource(R.string.circadian_transition_factor), draft.scaleTransitionFactor, { onEdit { s -> s.copy(scaleTransitionFactor = it.toFloat()) } },
                    epoch = epoch, committed = committed.scaleTransitionFactor, isInt = false,
                    help = R.string.help_scale_transition, testTag = "field_scaleTransitionFactor",
                )
                if (draft.scaleTransitionFactor > 0.5f) {
                    ErrorBanner(stringResource(R.string.circadian_err_transition), "error_scaleTransitionFactor")
                }
            }

            GraphSettingsGroup(stringResource(R.string.graph_taper)) {
                SectionHeader(stringResource(R.string.circadian_taper_header), divider = true)
                // Tasker Experiment slider: taper midpoint 130–240 (experiment_settings.md elements26, G2-F13).
                IntSliderSettingField(
                    stringResource(R.string.circadian_taper_midpoint), draft.scaleTaperMidpoint, 130..240,
                    { onEdit { s -> s.copy(scaleTaperMidpoint = it) } },
                    committed = committed.scaleTaperMidpoint,
                    help = R.string.help_taper_midpoint, testTag = "slider_scaleTaperMidpoint",
                )
                if (draft.scaleTaperMidpoint > draft.maxBrightness) {
                    ErrorBanner(stringResource(R.string.circadian_err_taper_midpoint), "error_scaleTaperMidpoint")
                }
                NumberSettingField(
                    stringResource(R.string.circadian_taper_steepness), draft.scaleTaperSteepness, { onEdit { s -> s.copy(scaleTaperSteepness = it.toFloat()) } },
                    epoch = epoch, committed = committed.scaleTaperSteepness, isInt = false,
                    help = R.string.help_taper_steepness, testTag = "field_scaleTaperSteepness",
                )
            }

            CircadianDateLocationCard(
                value = dateLocation,
                todayDate = todayDate,
                currentLatLon = defaultLatLon,
                onSet = onSetDateLocation,
                onUseLiveData = onUseLiveData,
                onUseCurrentLocation = onUseCurrentLocation,
                geoIpEnabled = geoIpEnabled,
                onSetGeoIpEnabled = onSetGeoIpEnabled,
            )
        }
    }
}

/** Date & location element (G2R-F39): pin fixed date/lat/lon to preview circadian for any day/place, or revert to live data. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircadianDateLocationCard(
    value: ExperimentDateLocation,
    todayDate: String,
    currentLatLon: Pair<Double, Double>?,
    onSet: (String, Double?, Double?) -> Unit,
    onUseLiveData: () -> Unit,
    onUseCurrentLocation: ((Double, Double) -> Unit) -> Unit = {},
    geoIpEnabled: Boolean = false,
    onSetGeoIpEnabled: (Boolean) -> Unit = {},
) {
    val effDate = value.date ?: todayDate
    val effLat = value.latitude ?: currentLatLon?.first
    val effLon = value.longitude ?: currentLatLon?.second

    var dateText by remember(effDate) { mutableStateOf(effDate) }
    var latText by remember(effLat) { mutableStateOf(effLat?.let { formatCoord(it) } ?: "") }
    var lonText by remember(effLon) { mutableStateOf(effLon?.let { formatCoord(it) } ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }

    AabCard {
    SectionHeader(stringResource(R.string.circadian_date_location_header), divider = true)
    Text(
        when {
            value.isUnset -> stringResource(R.string.circadian_status_live)
            value.latitude == null || value.longitude == null ->
                stringResource(R.string.circadian_status_fixed_date, value.date ?: stringResource(R.string.circadian_live_word))
            else -> stringResource(
                R.string.circadian_status_fixed_full,
                value.date ?: stringResource(R.string.circadian_today_word),
                fmtCoord(value.latitude), fmtCoord(value.longitude),
            )
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("exp_status"),
    )
    OutlinedButton(
        onClick = { showDatePicker = true },
        modifier = Modifier.fillMaxWidth().testTag("exp_date_value"),
    ) { Text(stringResource(R.string.circadian_date, dateText)) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = latText, onValueChange = { latText = it.filter { c -> c.isDigit() || c == '.' || c == ',' || c == '-' } },
            label = { Text(stringResource(R.string.field_latitude)) }, singleLine = true,
            modifier = Modifier.weight(1f).testTag("exp_lat"),
        )
        OutlinedTextField(
            value = lonText, onValueChange = { lonText = it.filter { c -> c.isDigit() || c == '.' || c == ',' || c == '-' } },
            label = { Text(stringResource(R.string.field_longitude)) }, singleLine = true,
            modifier = Modifier.weight(1f).testTag("exp_lon"),
        )
    }
    OutlinedButton(
        onClick = { onUseCurrentLocation { la, lo -> latText = formatCoord(la); lonText = formatCoord(lo) } },
        modifier = Modifier.testTag("exp_use_location"),
    ) { Text(stringResource(R.string.action_use_current_location)) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                // F39: date and location independent. Blank coords = date-only, valid coords = pin both.
                val lat = parseCoord(latText)
                val lon = parseCoord(lonText)
                val coordsBlank = latText.isBlank() && lonText.isBlank()
                if (dateText.isNotBlank() && (coordsBlank || (lat != null && lon != null))) {
                    onSet(dateText.trim(), lat, lon)
                }
            },
            modifier = Modifier.testTag("exp_set"),
        ) { Text(stringResource(R.string.circadian_set_fixed)) }
        TextButton(onClick = onUseLiveData, modifier = Modifier.testTag("exp_use_live")) {
            Text(stringResource(R.string.circadian_use_live_data))
        }
    }

    // G3-F12 / D-105: IP-geolocation fallback is explicit opt-in (D-121 privacy); default OFF.
    SwitchSettingRow(
        label = stringResource(R.string.circadian_ip_fallback_label),
        checked = geoIpEnabled,
        onCheckedChange = onSetGeoIpEnabled,
        help = R.string.help_circadian_ip_fallback,
        testTag = "exp_geoip_toggle",
    )

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
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.confirm_cancel)) } },
        ) { DatePicker(state = state) }
    }
    }
}

/** D-110: amber staleness hint when location is stale/missing while scaling active. Uses gold card per M3 audit. */
@Composable
private fun CircadianStaleBanner(status: com.tideo.autobrightness.app.runtime.CircadianLocationStatus) {
    val text = if (status.isStale) {
        stringResource(R.string.circadian_stale_banner, status.ageDays ?: 0L)
    } else {
        stringResource(R.string.circadian_no_location_banner)
    }
    Card(
        modifier = Modifier.fillMaxWidth().testTag("circadian_stale_banner"),
        colors = CardDefaults.cardColors(
            containerColor = com.tideo.autobrightness.app.ui.theme.AabGold,
            contentColor = com.tideo.autobrightness.app.ui.theme.AabOnGold,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
    }
}

private fun fmtCoord(v: Double?): String = v?.let { formatCoord(it) } ?: "—"

// DB-051: these coordinates are parsed back, so they are dot-decimal — never the default locale's.
internal fun formatCoord(value: Double): String = String.format(Locale.US, "%.5f", value)

internal fun parseCoord(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

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
