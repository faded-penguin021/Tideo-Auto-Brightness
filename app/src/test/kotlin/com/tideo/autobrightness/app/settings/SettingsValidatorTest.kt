package com.tideo.autobrightness.app.settings

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Validates SettingsValidator against Tasker task583 + task707 rules (features_spec.md §5).
 *
 * 5 rules total:
 *   task583 advisory (3): form2A<0, form3A<0, form2C>zone1End
 *   task707 safety (2): predicted brightness@1000lux<25/255 → unsafe; MaxBright defaults 255
 */
class SettingsValidatorTest {

    @Test
    fun `valid default settings produce no errors`() {
        val errors = SettingsValidator.validate(AabSettings())
        assertTrue(errors.isEmpty(), "Default settings should be valid; got: $errors")
    }

    @Test
    fun `form2C greater than zone1End triggers advisory error`() {
        // form2C=40 > zone1End=35 → task583 rule 3
        val settings = AabSettings(form2C = 40, zone1End = 35)
        val errors = SettingsValidator.validate(settings)
        assertTrue(errors.any { it.field == "form2C" }, "Expected form2C error; got: $errors")
    }

    @Test
    fun `form2C equal to zone1End is valid`() {
        val settings = AabSettings(form2C = 35, zone1End = 35)
        val errors = SettingsValidator.validate(settings)
        assertTrue(errors.none { it.field == "form2C" }, "form2C == zone1End is not invalid; got: $errors")
    }

    @Test
    fun `negative form2A triggers advisory error`() {
        // form2A = form1A * sqrt(zone1End); make form1A negative to force form2A < 0
        val settings = AabSettings(form1A = -1, zone1End = 35)
        val errors = SettingsValidator.validate(settings)
        assertTrue(errors.any { it.field == "form2A" }, "Expected form2A error; got: $errors")
    }

    @Test
    fun `predicted brightness below 25 at 1000 lux triggers safety error (zone2 formula)`() {
        // zone1End=10 (<1000), zone2End=2000 (>1000) → 1000 falls in ZONE 2.
        // form2A = 1*sqrt(10) ≈ 3.162; safe_val = 3.162 + 0.1*((995)^0.33 - (5)^0.33) ≈ 3.99 < 25.
        val s = AabSettings(
            zone1End = 10,
            zone2End = 2000,
            form1A = 1,
            form2B = 0.1f,
            form2C = 5,
            maxBrightness = 255,
        )
        val errors = SettingsValidator.validate(s)
        assertTrue(
            errors.any { it.field == "safetyBrightness" },
            "Expected safetyBrightness error for very dim zone-2 config; got: $errors",
        )
    }

    @Test
    fun `zone1 formula is selected when zone1End above 1000 and is safe with valid form1A`() {
        // zone1End=2000 > 1000 → 1000 falls in ZONE 1: form1A*sqrt(1000). Exercises the
        // zone-1 selection branch (otherwise never hit by any test). With the minimum valid
        // form1A=1, safe_val = sqrt(1000) ≈ 31.6 > 25, so zone-1 configs are always safe at
        // 1000 lux — a regression that mis-selected zone 2/3 here (form2B tiny → ~0) would
        // wrongly raise a safety error, which this asserts does NOT happen.
        val s = AabSettings(
            zone1End = 2000,
            zone2End = 10_000,
            form1A = 1,
            form2B = 0.1f,
            form2C = 5,
            maxBrightness = 255,
        )
        val errors = SettingsValidator.validate(s)
        assertTrue(
            errors.none { it.field == "safetyBrightness" },
            "Zone-1 selection (zone1End>1000) with valid form1A must be safe at 1000 lux; got: $errors",
        )
    }

    @Test
    fun `predicted brightness above 25 at 1000 lux produces no safety error`() {
        val errors = SettingsValidator.validate(AabSettings())
        assertTrue(
            errors.none { it.field == "safetyBrightness" },
            "Default settings should be safe at 1000 lux; got: $errors",
        )
    }

    @Test
    fun `zone3 formula is selected when zone2End below 1000 and fires safety error when too dim`() {
        // zone1End=5, zone2End=999 → both ≤ 1000 → 1000 falls in ZONE 3:
        //   safe_val = MaxBright - (form3A/1000)*MaxBright.
        // form3A ≈ 999*(255-(2.236+0.1*((997)^0.33-(3)^0.33)))/255 ≈ 987 → safe_val ≈ 3.3 < 25.
        // Previously this test filtered out the very fields it should check, so it passed even
        // with zero errors (vacuous). Now assert the safety error is actually present.
        val s = AabSettings(
            zone1End = 5,
            zone2End = 999,
            form1A = 1,
            form2B = 0.1f,
            form2C = 2,
            maxBrightness = 255,
        )
        val errors = SettingsValidator.validate(s)
        assertTrue(
            errors.any { it.field == "safetyBrightness" },
            "Zone-3 selection with a very dim curve must raise a safety error; got: $errors",
        )
    }

    @Test
    fun `multiple advisory errors accumulate`() {
        // form2C > zone1End AND form2A < 0 (form1A = -1) should both appear
        val s = AabSettings(form1A = -1, form2C = 50, zone1End = 35)
        val errors = SettingsValidator.validate(s)
        val fields = errors.map { it.field }.toSet()
        assertTrue("form2A" in fields, "Expected form2A error; got $fields")
        assertTrue("form2C" in fields, "Expected form2C error; got $fields")
    }

    @Test
    fun `error messages are non-empty`() {
        val s = AabSettings(form2C = 50, zone1End = 35)
        val errors = SettingsValidator.validate(s)
        assertTrue(errors.all { it.message.isNotEmpty() }, "All errors should have non-empty messages")
    }
}
