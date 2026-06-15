package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.domain.circadian.DynamicScaleEngine
import com.tideo.autobrightness.domain.circadian.DynamicScaleInput
import org.junit.Test
import java.util.Calendar
import java.util.SimpleTimeZone
import kotlin.test.assertTrue

/**
 * G2R-F73 regression: the pipeline must feed the REAL sunrise/sunset ramp windows to the dynamic-scale
 * engine, not `TimeContext`'s fixed 6–8am-UTC defaults. Pure JVM — exercises the app-layer
 * [CircadianWindowProvider.compute] + the fenced domain [DynamicScaleEngine] only (domain untouched).
 *
 * Scenario: Edinburgh (55.95, −3.19), mid-June, device clock UTC+1 (BST). At **07:13 local = 06:13
 * UTC** the sun has been up for ~3h, so `%AAB_ScaleDynamic` should be in full daytime (> 1). The old
 * fixed windows treated 06–08 UTC (= 07–09 local) as "morning", so the ramp had barely started there
 * → scaleDynamic < 1 (the owner's bug).
 */
class CircadianWindowProviderTest {

    // Utrecht, NL — the owner's location (UTC+2 / CEST in June).
    private val lat = 52.0907
    private val lon = 5.1214
    private val tzOffsetHours = 2.0
    private val transitionFactor = 0.1
    private val scaleSpread = 15.0
    private val steepness = 6.0

    // 08:13 local (CEST) == 06:13 UTC, as UTC seconds-of-day (the pipeline's `now` frame). This is the
    // instant the owner saw scaleDynamic = 0.852.
    private val now0613Utc = (6 * 3600 + 13 * 60).toDouble()

    private fun midJuneEpochSec(): Long {
        val cal = Calendar.getInstance(SimpleTimeZone(2 * 3_600_000, "CEST"))
        cal.set(2026, Calendar.JUNE, 15, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis / 1000L
    }

    @Test
    fun utrecht_sunriseAndDaytimeScale_matchReality() {
        // Sanity-anchor against a real-world almanac value: Utrecht sunrise on 2026-06-15 ≈ 05:18 local.
        val solar = com.tideo.autobrightness.domain.circadian.SolarCalculator
            .compute(lat, lon, midJuneEpochSec(), tzOffsetHours)
        fun local(epochSec: Long): String {
            val s = Math.floorMod(epochSec + (tzOffsetHours * 3600).toLong(), 86_400L)
            return "%02d:%02d".format(s / 3600, (s % 3600) / 60)
        }
        assertTrue(local(solar.riseEpochSec) == "05:18", "Utrecht sunrise was ${local(solar.riseEpochSec)}")

        val w = CircadianWindowProvider.compute(lat, lon, midJuneEpochSec(), tzOffsetHours, transitionFactor)
        val scale = scaleDynamicAt(
            now0613Utc, w.morningStart, w.morningEnd, w.eveningStart, w.eveningEnd, w.sunlightDurationMinutes,
        )
        // Full daytime at 08:13 local → progress 1 → 1 + 15% = 1.15 (was 0.852 with the fixed windows).
        assertTrue(kotlin.math.abs(scale - 1.15) < 1e-6, "expected 1.15 daytime scale, was $scale")
    }

    private fun scaleDynamicAt(
        now: Double,
        morningStart: Double, morningEnd: Double,
        eveningStart: Double, eveningEnd: Double,
        sunlightMinutes: Double,
    ): Double = DynamicScaleEngine.compute(
        DynamicScaleInput(
            nowSecOfDay = now,
            morningStart = morningStart, morningEnd = morningEnd,
            eveningStart = eveningStart, eveningEnd = eveningEnd,
            sunlightDurationMinutes = sunlightMinutes,
            isPolar = false, steepness = steepness, scaleSpreadPercent = scaleSpread,
        ),
    ).scaleDynamic

    @Test
    fun realWindows_giveDaytimeScale_at0813Local() {
        val w = CircadianWindowProvider.compute(lat, lon, midJuneEpochSec(), tzOffsetHours, transitionFactor)
        // Morning ramp completes well before 06:13 UTC; evening is far later → full daytime.
        assertTrue(w.morningEnd < now0613Utc, "morningEnd ${w.morningEnd} should be before $now0613Utc")
        assertTrue(w.eveningStart > now0613Utc, "eveningStart ${w.eveningStart} should be after $now0613Utc")

        val scale = scaleDynamicAt(
            now0613Utc, w.morningStart, w.morningEnd, w.eveningStart, w.eveningEnd, w.sunlightDurationMinutes,
        )
        assertTrue(scale > 1.0, "real sunrise → daytime scale > 1, was $scale")
    }

    @Test
    fun defaultWindows_reproduceTheBug_subUnityScale() {
        // The pre-F73 behaviour: TimeContext defaults (6–8am / 18–20pm UTC, 720 min sunlight).
        val scale = scaleDynamicAt(
            now0613Utc, 6 * 3600.0, 8 * 3600.0, 18 * 3600.0, 20 * 3600.0, 720.0,
        )
        assertTrue(scale < 1.0, "fixed 6–8am-UTC morning makes 06:13 UTC sub-unity (the bug), was $scale")
    }

    @Test
    fun realWindows_areOrderedAndNonPolarAtMidLatitude() {
        val w = CircadianWindowProvider.compute(lat, lon, midJuneEpochSec(), tzOffsetHours, transitionFactor)
        assertTrue(!w.isPolar, "55.95°N in June is not polar")
        assertTrue(w.morningStart < w.morningEnd, "morning window ordered")
        assertTrue(w.eveningStart < w.eveningEnd, "evening window ordered")
        assertTrue(w.morningEnd <= w.eveningStart, "day spans morningEnd..eveningStart")
    }
}
