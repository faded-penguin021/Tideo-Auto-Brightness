package com.tideo.autobrightness.domain.parity

import com.tideo.autobrightness.domain.brightness.BrightnessCurveConfig
import com.tideo.autobrightness.domain.reference.GoldenVectorGenerator
import com.tideo.autobrightness.domain.wizard.CurveSuggestionEngine
import com.tideo.autobrightness.domain.wizard.CurveSuggestionInput
import com.tideo.autobrightness.domain.wizard.OverridePoint
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Parity tests for CurveSuggestionEngine (task38 + task655) against golden vectors (S6). */
class WizardParityTest {

    private fun golden(name: String): Map<String, Map<String, String>> {
        val file = File("src/test/resources/golden/$name")
        assertTrue(file.exists(), "missing golden vector $name — run with -DregenGolden=1")
        val lines = file.readLines().filter { it.isNotBlank() }
        val header = lines.first().split(",")
        // Index by testCase+tau key
        return lines.drop(1).associate { line ->
            val row = header.zip(line.split(",")).toMap()
            "${row["testCase"]}_${row["tau"]}" to row
        }
    }

    @Test
    fun wizard_matchesGolden() {
        val goldenRows = golden("wizard.csv")
        val mismatches = mutableListOf<String>()

        for (tc in GoldenVectorGenerator.wizardTestCases) {
            val v = tc.curveVariant
            val cfg = BrightnessCurveConfig(
                form1A = v.form1a, form2A = v.form2a, form2B = v.form2b, form2C = v.form2c,
                zone1End = v.zone1End, zone2End = v.zone2End, form3A = v.form3a,
                minBrightness = v.minBright.toInt(), maxBrightness = v.maxBright.toInt(),
            )
            val input = CurveSuggestionInput(overrides = tc.overrides, currentCurve = cfg, tau = tc.tau)
            val result = CurveSuggestionEngine.suggest(input)

            val key = "${tc.name}_${tc.tau.toBigDecimal().toPlainString()}"
            val row = goldenRows[key]
            if (row == null) { mismatches += "missing golden row for $key"; continue }

            val goldenIsNull = row["isNull"] == "true"
            if (goldenIsNull) {
                if (result != null) mismatches += "$key golden=null but engine returned a result"
                continue
            }
            if (result == null) {
                mismatches += "$key golden=non-null but engine returned null"; continue
            }

            val tag = "testCase=${tc.name} tau=${tc.tau}"
            if (result.zone1End != row["zone1End"]!!.toLong()) mismatches += "$tag zone1End engine=${result.zone1End} ref=${row["zone1End"]}"
            if (result.zone2End != row["zone2End"]!!.toLong()) mismatches += "$tag zone2End engine=${result.zone2End} ref=${row["zone2End"]}"
            if (result.form1a != row["form1a"]) mismatches += "$tag form1a engine=${result.form1a} ref=${row["form1a"]}"
            if (result.form2a != row["form2a"]) mismatches += "$tag form2a engine=${result.form2a} ref=${row["form2a"]}"
            if (result.form2b != row["form2b"]) mismatches += "$tag form2b engine=${result.form2b} ref=${row["form2b"]}"
            if (result.form2c != row["form2c"]) mismatches += "$tag form2c engine=${result.form2c} ref=${row["form2c"]}"
            if (result.form2d != row["form2d"]!!.toLong()) mismatches += "$tag form2d engine=${result.form2d} ref=${row["form2d"]}"
            if (result.form3a != row["form3a"]) mismatches += "$tag form3a engine=${result.form3a} ref=${row["form3a"]}"
        }
        if (mismatches.isNotEmpty()) fail("wizard diverges in ${mismatches.size} cases:\n${mismatches.joinToString("\n")}")
    }

    /** S8.5/D-037: abort when override set < 9 points after ghost injection. */
    @Test
    fun wizard_abortsBelowMinimumDataPoints() {
        val input = CurveSuggestionInput(
            overrides = listOf(OverridePoint(10.0, 20.0), OverridePoint(1000.0, 150.0)),
            currentCurve = BrightnessCurveConfig(),
        )
        assertNull(CurveSuggestionEngine.suggest(input), "fewer than 9 points must abort to null")
    }

    /** G3-F17: default τ=0.001 (task38 act2), not 4.0 fallback. */
    @Test
    fun wizard_defaultTauIsTheFaithfulAct2Value() {
        val input = CurveSuggestionInput(
            overrides = listOf(OverridePoint(10.0, 20.0)),
            currentCurve = BrightnessCurveConfig(),
        )
        assertEquals(0.001, input.tau, "default τ must be the act2 0.001, not the 4.0 fallback")
    }

    /** DA-016: Top-K Zone1End shortlist sorted descending by score (catch single-swap bubble bug). */
    @Test
    fun wizard_topKCandidatesAreSortedDescendingByScore_DA016() {
        val overrides = listOf(
            OverridePoint(1.0, 4.0), OverridePoint(3.0, 7.0), OverridePoint(6.0, 10.0),
            OverridePoint(12.0, 16.0), OverridePoint(20.0, 24.0), OverridePoint(45.0, 38.0),
            OverridePoint(90.0, 52.0), OverridePoint(180.0, 66.0), OverridePoint(400.0, 84.0),
            OverridePoint(900.0, 108.0), OverridePoint(2000.0, 140.0), OverridePoint(5000.0, 180.0),
            OverridePoint(12000.0, 220.0), OverridePoint(30000.0, 248.0),
        )
        val result = CurveSuggestionEngine.suggest(
            CurveSuggestionInput(overrides = overrides, currentCurve = BrightnessCurveConfig(), tau = 4.0),
        )
        assertTrue(result != null, "expected a suggestion for a well-populated dataset")

        val scoreRegex = Regex("""Top Cand #\d+: Z1End=[-\d.]+ \(Score: (-?[\d.]+)\)""")
        val scores = scoreRegex.findAll(result.diagnosticsLog).map { it.groupValues[1].toDouble() }.toList()
        assertTrue(scores.size >= 2, "expected at least two Top Cand rows, got ${scores.size}")

        for (i in 1 until scores.size) {
            assertTrue(
                scores[i] <= scores[i - 1],
                "Top-K shortlist must be sorted descending by score, but #${i + 1} (${scores[i]}) " +
                    "> #$i (${scores[i - 1]}) — a real candidate is stranded behind a placeholder " +
                    "(single-swap bubble bug). Full log:\n${result.diagnosticsLog}",
            )
        }
    }
}
