package com.tideo.autobrightness.domain.parity

import com.tideo.autobrightness.domain.circadian.DynamicScaleEngine
import com.tideo.autobrightness.domain.circadian.DynamicScaleInput
import com.tideo.autobrightness.domain.circadian.SolarCalculator
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Parity tests for [SolarCalculator] and [DynamicScaleEngine] against committed golden vectors.
 *
 * Golden CSV generated from [TaskerReference.solarTimes] / [TaskerReference.dynamicScale]
 * (faithful ports of task90 Java blocks #1 and #2, XML L40429+L41085). Polar assertions
 * exercise the ≷1380-minute sunlight-duration threshold separately.
 *
 * Segment: S6.
 */
class CircadianParityTest {

    private val tol = 1e-9

    private fun golden(name: String): List<Map<String, String>> {
        val file = File("src/test/resources/golden/$name")
        assertTrue(file.exists(), "missing golden vector $name — run with -DregenGolden=1")
        val lines = file.readLines().filter { it.isNotBlank() }
        val header = lines.first().split(",")
        return lines.drop(1).map { line -> header.zip(line.split(",")).toMap() }
    }

    private fun Map<String, String>.d(k: String): Double = getValue(k).toDouble()
    private fun Map<String, String>.l(k: String): Long = getValue(k).toLong()

    // ---- solar-time parity ---------------------------------------------------------------

    @Test
    fun circadian_solarTimes_matchesGolden() {
        val mismatches = mutableListOf<String>()
        // Check each unique (lat,lng,dateEpoch,tzOffset) combo — deduplicate to avoid n*16 assertions
        val seen = mutableSetOf<String>()
        for (r in golden("circadian.csv")) {
            val key = "${r["loc"]}_${r["date"]}"
            if (!seen.add(key)) continue
            val lat = r.d("lat"); val lng = r.d("lng")
            val epoch = r.l("dateEpoch"); val tz = r.d("tzOffset")
            val solar = SolarCalculator.compute(lat, lng, epoch, tz)
            val tag = "loc=${r["loc"]} date=${r["date"]}"
            if (solar.sunStatus != r["sunStatus"])
                mismatches += "$tag sunStatus engine=${solar.sunStatus} ref=${r["sunStatus"]}"
            if (abs(solar.riseEpochSec - r.l("riseEpochSec")) > 0)
                mismatches += "$tag riseEpoch engine=${solar.riseEpochSec} ref=${r["riseEpochSec"]}"
            if (abs(solar.setEpochSec - r.l("setEpochSec")) > 0)
                mismatches += "$tag setEpoch engine=${solar.setEpochSec} ref=${r["setEpochSec"]}"
            if (abs(solar.noonEpochSec - r.l("noonEpochSec")) > 0)
                mismatches += "$tag noonEpoch engine=${solar.noonEpochSec} ref=${r["noonEpochSec"]}"
            if (abs(solar.sunlightDurationMinutes - r.l("sunlightMins")) > 0)
                mismatches += "$tag sunlightMins engine=${solar.sunlightDurationMinutes} ref=${r["sunlightMins"]}"
        }
        if (mismatches.isNotEmpty()) fail("solarTimes diverges in ${mismatches.size} cases:\n${mismatches.take(10).joinToString("\n")}")
    }

    // ---- schedule-window parity ----------------------------------------------------------

    @Test
    fun circadian_scheduleWindows_matchesGolden() {
        val mismatches = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (r in golden("circadian.csv")) {
            val key = "${r["loc"]}_${r["date"]}_${r["transitionFactor"]}"
            if (!seen.add(key)) continue
            val lat = r.d("lat"); val lng = r.d("lng")
            val epoch = r.l("dateEpoch"); val tz = r.d("tzOffset")
            val solar = SolarCalculator.compute(lat, lng, epoch, tz)
            val win = SolarCalculator.buildScheduleWindows(solar, r.d("transitionFactor"))
            val tag = "loc=${r["loc"]} date=${r["date"]} tf=${r["transitionFactor"]}"
            listOf(
                "dawnSecOfDay" to win.dawnSecOfDay,
                "sunriseSecOfDay" to win.sunriseSecOfDay,
                "noonSecOfDay" to win.noonSecOfDay,
                "sunsetSecOfDay" to win.sunsetSecOfDay,
                "duskSecOfDay" to win.duskSecOfDay,
                "morningStart" to win.morningStart,
                "morningEnd" to win.morningEnd,
                "morningDuration" to win.morningDuration,
                "eveningStart" to win.eveningStart,
                "eveningEnd" to win.eveningEnd,
                "eveningDuration" to win.eveningDuration,
            ).forEach { (col, v) ->
                if (abs(v - r.d(col)) > tol) mismatches += "$tag $col engine=$v ref=${r[col]}"
            }
        }
        if (mismatches.isNotEmpty()) fail("scheduleWindows diverges in ${mismatches.size} cases; e.g. ${mismatches.first()}")
    }

    // ---- dynamic-scale parity ------------------------------------------------------------

    @Test
    fun circadian_dynamicScale_matchesGolden() {
        val mismatches = mutableListOf<String>()
        for (r in golden("circadian.csv")) {
            val input = DynamicScaleInput(
                nowSecOfDay = r.d("nowSecOfDay"),
                morningStart = r.d("morningStart"),
                morningEnd = r.d("morningEnd"),
                eveningStart = r.d("eveningStart"),
                eveningEnd = r.d("eveningEnd"),
                sunlightDurationMinutes = r.d("sunlightMins"),
                isPolar = r["isPolar"] == "true",
                steepness = r.d("steepness"),
                scaleSpreadPercent = r.d("scaleSpreadPercent"),
                dimSpreadPercent = r.d("dimSpreadPercent"),
            )
            val ds = DynamicScaleEngine.compute(input)
            val tag = "loc=${r["loc"]} date=${r["date"]} tf=${r["transitionFactor"]} now=${r["nowSecOfDay"]}"
            if (abs(ds.progress - r.d("progress")) > tol) mismatches += "$tag progress engine=${ds.progress} ref=${r["progress"]}"
            if (abs(ds.modifier - r.d("modifier")) > tol) mismatches += "$tag modifier engine=${ds.modifier} ref=${r["modifier"]}"
            if (abs(ds.dimDynamic - r.d("dimDynamic")) > tol) mismatches += "$tag dimDynamic engine=${ds.dimDynamic} ref=${r["dimDynamic"]}"
            if (abs(ds.scaleDynamic - r.d("scaleDynamic")) > tol) mismatches += "$tag scaleDynamic engine=${ds.scaleDynamic} ref=${r["scaleDynamic"]}"
        }
        if (mismatches.isNotEmpty()) fail("dynamicScale diverges in ${mismatches.size} rows; e.g. ${mismatches.first()}")
    }

    // ---- polar assertions (Tasker: task90 Java Block #2, L51-55 — isPolar branch) ---------

    @Test
    fun polar_midnightSun_progressIs1() {
        val result = DynamicScaleEngine.compute(
            DynamicScaleInput(
                nowSecOfDay = 43200.0,
                morningStart = 21600.0, morningEnd = 28800.0,
                eveningStart = 64800.0, eveningEnd = 72000.0,
                sunlightDurationMinutes = 1440.0,
                isPolar = true,
                steepness = 4.0,
            )
        )
        assertEquals(1.0, result.progress, tol, "midnight-sun (1440 min) must have progress=1")
    }

    @Test
    fun polar_justAbove1380_progressIs1() {
        val result = DynamicScaleEngine.compute(
            DynamicScaleInput(
                nowSecOfDay = 43200.0,
                morningStart = 21600.0, morningEnd = 28800.0,
                eveningStart = 64800.0, eveningEnd = 72000.0,
                sunlightDurationMinutes = 1381.0,
                isPolar = true,
                steepness = 4.0,
            )
        )
        assertEquals(1.0, result.progress, tol, "sunlightMins > 1380 must have progress=1")
    }

    @Test
    fun polar_exactlyAt1380_progressIs0() {
        val result = DynamicScaleEngine.compute(
            DynamicScaleInput(
                nowSecOfDay = 43200.0,
                morningStart = 21600.0, morningEnd = 28800.0,
                eveningStart = 64800.0, eveningEnd = 72000.0,
                sunlightDurationMinutes = 1380.0,
                isPolar = true,
                steepness = 4.0,
            )
        )
        assertEquals(0.0, result.progress, tol, "sunlightMins == 1380 (not > 1380) must have progress=0")
    }

    @Test
    fun polar_night_progressIs0() {
        val result = DynamicScaleEngine.compute(
            DynamicScaleInput(
                nowSecOfDay = 43200.0,
                morningStart = 21600.0, morningEnd = 28800.0,
                eveningStart = 64800.0, eveningEnd = 72000.0,
                sunlightDurationMinutes = 0.0,
                isPolar = true,
                steepness = 4.0,
            )
        )
        assertEquals(0.0, result.progress, tol, "polar night (0 min) must have progress=0")
    }
}
