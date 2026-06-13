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

/**
 * Parity tests for [CurveSuggestionEngine] (task38 + task655) against committed golden vectors.
 *
 * Each test case is defined in [GoldenVectorGenerator.wizardTestCases]; the golden CSV captures
 * the deterministic output of the optimization engine for each fixed input. Any change to the
 * engine that shifts a zone boundary or curve parameter will fail these tests.
 *
 * Segment: S6.
 */
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

    /**
     * Independent abort-path check (S8.5/D-037): task38 returns the "error" path (null) when the
     * override set has fewer than 9 points after ghost injection. No golden case exercises this;
     * 2 real points + ≤5 ghosts < 9 → null. This assertion does not depend on a production-derived
     * golden, so it is genuine ground truth for the abort contract.
     */
    @Test
    fun wizard_abortsBelowMinimumDataPoints() {
        val input = CurveSuggestionInput(
            overrides = listOf(OverridePoint(10.0, 20.0), OverridePoint(1000.0, 150.0)),
            currentCurve = BrightnessCurveConfig(),
        )
        assertNull(CurveSuggestionEngine.suggest(input), "fewer than 9 points must abort to null")
    }
}
