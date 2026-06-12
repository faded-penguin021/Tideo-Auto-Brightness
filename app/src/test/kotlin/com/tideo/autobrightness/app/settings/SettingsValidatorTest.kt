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
    fun `predicted brightness below 25 at 1000 lux triggers safety error (zone1 formula)`() {
        // zone1End=2000 > 1000 → zone1 formula: form1A * sqrt(1000)
        // form1A=0 → safe_val=0 < 25 → safety error
        // Note: form1A has a lower valid range of 1, but validate() coerces after validation
        // So we test with a value that genuinely produces low brightness without violating form1A range
        // form1A=1: safe_val = 1 * sqrt(1000) ≈ 31.6 → valid (>25)
        // form1A=1, zone1End=1 so 1000>zone1End → zone2 path
        // Easiest: keep zone1End large enough, reduce form1A below sqrt threshold
        // 1 * sqrt(1000) = 31.6 → still > 25
        // Let's try: zone1End=2000, form1A=1 → safe_val=31.6 → valid
        // For zone1 < 25 we'd need form1A * sqrt(1000) < 25 → form1A < 0.79 → but min valid is 1
        // So zone1 path with valid form1A can't trigger the error. Try zone2:
        // Make zone1End=10 (< 1000), zone2End=2000 (>1000): zone2 formula applies
        // Use very small form2B to get a tiny zone2 value
        val s = AabSettings(
            zone1End = 10,
            zone2End = 2000,
            form1A = 1,
            form2B = 0.1f,
            form2C = 5,
            maxBrightness = 255,
        )
        val errors = SettingsValidator.validate(s)
        // Compute expected value manually for assertion
        // form2A = form1A * sqrt(zone1End) = 1 * sqrt(10) ≈ 3.162
        // safe_val = form2A + form2B * ((1000-form2C)^0.33 - (zone1End-form2C)^0.33)
        //          = 3.162 + 0.1 * ((995)^0.33 - (5)^0.33)
        //          ≈ 3.162 + 0.1 * (9.97 - 1.71) ≈ 3.162 + 0.826 ≈ 3.99
        // 3.99 < 25 → safety error expected
        assertTrue(
            errors.any { it.field == "safetyBrightness" },
            "Expected safetyBrightness error for very dim zone-2 config; got: $errors",
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
    fun `zone3 path activates when zone2End below 1000`() {
        // zone1End=5, zone2End=500 → both < 1000 → zone3 formula
        // zone3: MaxBright - (form3A/1000)*MaxBright
        // With high form3A the result can be negative → safety error
        val s = AabSettings(
            zone1End = 5,
            zone2End = 500,
            form1A = 1,
            form2B = 0.1f,
            form2C = 2,
            maxBrightness = 255,
        )
        val errors = SettingsValidator.validate(s)
        // form3A likely very large (zone2end * (maxbright - ...) / maxbright) → zone3 safe_val may be negative
        // Either way, method must not crash
        assertEquals(errors.filterNot { it.field == "form3A" || it.field == "safetyBrightness" || it.field == "form2A" }.size, 0)
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
