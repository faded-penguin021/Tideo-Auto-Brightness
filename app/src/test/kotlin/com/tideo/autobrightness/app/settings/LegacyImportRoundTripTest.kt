package com.tideo.autobrightness.app.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Round-trip test: audit-default values → key=value legacy string → AabSettings.
 *
 * Fixture is built from defaults_audit.md canonical values (task570 _Initialize AAB Defaults).
 * Verifies TaskerLegacyProfileSerializer.deserialize() correctly maps every %AAB_* variable
 * that it handles to the corresponding AabSettings field.
 */
class LegacyImportRoundTripTest {

    // Fixture built from defaults_audit.md / task570 canonical defaults
    private val auditDefaultsLegacy = """
        %AAB_Service = On
        %AAB_DetectOverrides = Off
        %AAB_MinBright = 10
        %AAB_MaxBright = 255
        %AAB_Offset = 0
        %AAB_Scale = 1
        %AAB_Zone1End = 35
        %AAB_Zone2End = 10000
        %AAB_Form1A = 5
        %AAB_Form2B = 8.8
        %AAB_Form2C = 18
        %AAB_DimmingEnabled = false
        %AAB_DimmingStrength = 25
        %AAB_DimmingExponent = 2.5
        %AAB_DimmingThreshold = 15
        %AAB_DimSpread = 100
        %AAB_PWMSensitive = false
        %AAB_PWMExp = 0.8
        %AAB_Throttle = 1310
        %AAB_MinWait = 25
        %AAB_MaxWait = 65
        %AAB_AnimSteps = 20
        %AAB_DeltaFactor = 1.8
        %AAB_ThreshBright = 0.08
        %AAB_ThreshDark = 0.3
        %AAB_ThreshDim = 0.25
        %AAB_ThreshSteepness = 2.1
        %AAB_ThreshMidpoint = 4.0
        %AAB_ScalingUse = false
        %AAB_ScaleSpread = 15
        %AAB_ScaleSteepness = 6
        %AAB_ScaleTaperMidpoint = 190
        %AAB_ScaleTaperSteepness = 0.075
        %AAB_ScaleTransitionFactor = 0.1
        %AAB_TrustUnreliable = Off
        %AAB_QSUse = false
        %AAB_NotifyUse = true
        %AAB_Debug = 0
        %AAB_ContextOverride = true
    """.trimIndent()

    @Test
    fun `audit defaults round-trip through legacy deserializer`() {
        val settings = TaskerLegacyProfileSerializer.deserialize(auditDefaultsLegacy)

        assertTrue(settings.serviceEnabled, "%AAB_Service=On → serviceEnabled=true")
        assertFalse(settings.detectOverrides)
        assertEquals(10, settings.minBrightness)
        assertEquals(255, settings.maxBrightness)
        assertEquals(0, settings.offset)
        assertEquals(1.0f, settings.scale, 0.001f)
        assertEquals(35, settings.zone1End)
        assertEquals(10000, settings.zone2End)
        assertEquals(5.0, settings.form1A, 0.0001)
        assertEquals(8.8f, settings.form2B, 0.001f)
        assertEquals(18, settings.form2C)
        assertFalse(settings.dimmingEnabled)
        assertEquals(25, settings.dimmingStrength)
        assertEquals(2.5f, settings.dimmingExponent, 0.001f)
        assertEquals(15, settings.dimmingThreshold)
        assertEquals(100, settings.dimSpread)
        assertFalse(settings.pwmSensitive)
        assertEquals(0.8f, settings.pwmExponent, 0.001f)
        assertEquals(1310L, settings.throttleDefaultMs)
        assertEquals(25, settings.minWaitMs)
        assertEquals(65, settings.maxWaitMs)
        assertEquals(20, settings.animSteps)
        assertEquals(1.8f, settings.deltaFactor, 0.001f)
        assertEquals(0.08f, settings.thresholdBright, 0.001f)
        assertEquals(0.3f, settings.thresholdDark, 0.001f)
        assertEquals(0.25f, settings.thresholdDim, 0.001f)
        assertEquals(2.1f, settings.thresholdSteepness, 0.001f)
        assertEquals(4.0, settings.thresholdMidpoint, 0.001)
        assertFalse(settings.scalingEnabled)
        assertEquals(15, settings.scaleSpread)
        assertEquals(6, settings.scaleSteepness)
        assertEquals(190, settings.scaleTaperMidpoint)
        assertEquals(0.075f, settings.scaleTaperSteepness, 0.0001f)
        assertEquals(0.1f, settings.scaleTransitionFactor, 0.001f)
        assertFalse(settings.trustUnreliableSensor)
        assertFalse(settings.quickSettingsEnabled)
        assertTrue(settings.notificationsEnabled)
        assertEquals(0, settings.debugLevel)
        assertTrue(settings.contextOverride)
    }

    @Test
    fun `legacy boolean variants are accepted`() {
        val raw = """
            %AAB_Service = On
            %AAB_ScalingUse = true
            %AAB_TrustUnreliable = 1
            %AAB_DimmingEnabled = Off
        """.trimIndent()
        val s = TaskerLegacyProfileSerializer.deserialize(raw)
        assertTrue(s.serviceEnabled, "On → true")
        assertTrue(s.scalingEnabled, "true → true")
        assertTrue(s.trustUnreliableSensor, "1 → true")
        assertFalse(s.dimmingEnabled, "Off → false")
    }

    @Test
    fun `legacy scale as integer string round-trips`() {
        val raw = "%AAB_Scale = 1"
        val s = TaskerLegacyProfileSerializer.deserialize(raw)
        assertEquals(1.0f, s.scale, 0.001f)
    }

    @Test
    fun `legacy scale as float string round-trips`() {
        val raw = "%AAB_Scale = 0.8"
        val s = TaskerLegacyProfileSerializer.deserialize(raw)
        assertEquals(0.8f, s.scale, 0.001f)
    }

    // G2R-F70: the REAL Tasker AAB config format is nested JSON (task637 _ProfileManager.performSave,
    // XML L29365+), NOT %AAB_Key=value. Before the fix this parsed to all-defaults, so a legacy "Load"
    // changed nothing on screen. Keys/defaults mirror performSave; values here are deliberately
    // non-default so a defaults-only parse would fail every assertion.
    private val nestedJsonConfig = """
        {
          "meta": { "name": "Night", "version": "3.3", "timestamp": 1700000000000 },
          "general": { "z1_end": 50.0, "z2_end": 8000.0, "form1a": 7.0, "form2a": 49.5,
                       "form2b": 9.9, "form2c": 20.0, "form2d": 50.0, "form3a": 2000.0 },
          "misc": { "min_bright": 3.0, "max_bright": 200.0, "scale": 0.8, "offset": 5.0,
                    "anim_steps": 40, "min_wait": 10, "max_wait": 50, "delta_factor": 2.2,
                    "throttle": 1500 },
          "reactivity": { "detect_overrides": true, "thresh_dark": 0.4, "thresh_dim": 0.2,
                          "thresh_bright": 0.06, "thresh_steepness": 2.5, "thresh_midpoint": 3.5,
                          "trust_unreliable": true },
          "circadian": { "spread": 20.0, "transition": 0.2, "steepness": 8.0, "enabled": true,
                         "taper_mid": 200.0, "taper_steep": 0.05, "qs_use": true },
          "superdimming": { "enabled": true, "threshold": 25.0, "strength": 30.0, "exponent": 3.0,
                            "spread": 80.0, "pwm_exp": 0.9, "pwm_sensitive": true }
        }
    """.trimIndent()

    @Test
    fun `nested Tasker JSON config parses every section`() {
        val s = TaskerLegacyProfileSerializer.deserialize(nestedJsonConfig)

        // general
        assertEquals(50, s.zone1End)
        assertEquals(8000, s.zone2End)
        assertEquals(7.0, s.form1A, 0.0001)
        assertEquals(9.9f, s.form2B, 0.001f)
        assertEquals(20, s.form2C)
        // misc
        assertEquals(3, s.minBrightness)
        assertEquals(200, s.maxBrightness)
        assertEquals(0.8f, s.scale, 0.001f)
        assertEquals(5, s.offset)
        assertEquals(40, s.animSteps)
        assertEquals(10, s.minWaitMs)
        assertEquals(50, s.maxWaitMs)
        assertEquals(2.2f, s.deltaFactor, 0.001f)
        assertEquals(1500L, s.throttleDefaultMs)
        // reactivity
        assertTrue(s.detectOverrides)
        assertEquals(0.4f, s.thresholdDark, 0.001f)
        assertEquals(0.2f, s.thresholdDim, 0.001f)
        assertEquals(0.06f, s.thresholdBright, 0.001f)
        assertEquals(2.5f, s.thresholdSteepness, 0.001f)
        assertEquals(3.5, s.thresholdMidpoint, 0.001)
        assertTrue(s.trustUnreliableSensor)
        // circadian
        assertEquals(20, s.scaleSpread)
        assertEquals(0.2f, s.scaleTransitionFactor, 0.001f)
        assertEquals(8, s.scaleSteepness)
        assertEquals(200, s.scaleTaperMidpoint)
        assertEquals(0.05f, s.scaleTaperSteepness, 0.0001f)
        assertTrue(s.scalingEnabled)
        assertTrue(s.quickSettingsEnabled)
        // superdimming
        assertTrue(s.dimmingEnabled)
        assertEquals(25, s.dimmingThreshold)
        assertEquals(30, s.dimmingStrength)
        assertEquals(3.0f, s.dimmingExponent, 0.001f)
        assertEquals(80, s.dimSpread)
        assertEquals(0.9f, s.pwmExponent, 0.001f)
        assertTrue(s.pwmSensitive)
    }

    @Test
    fun `nested JSON with a missing section keeps defaults for that section`() {
        // Only `misc` present (cf. performLoad's emergency Default which omits circadian/superdimming
        // detail): the parsed fields apply, everything else stays at the baseline default.
        val partial = """{ "meta": { "name": "x" }, "misc": { "min_bright": 30.0 } }"""
        val s = TaskerLegacyProfileSerializer.deserialize(partial)
        assertEquals(30, s.minBrightness)
        assertEquals(255, s.maxBrightness, "absent key keeps the default")
        assertFalse(s.scalingEnabled, "absent circadian section keeps the default")
    }

    // G2R-F70: Tasker stores curve params as continuous doubles, so a legacy export may carry a
    // decimal for fields the rebuild keeps as Int (Form2C/MinBright/Throttle round); but Form1A is a
    // continuous Double (G2R-F70) and must keep its decimal — the old `toIntOrNull()` returned null on
    // "6.8" (dropping to the default), and the 8c round (6.8 → 7) lost the precision the owner reported.
    @Test
    fun `key=value Form1A keeps its decimal while Int fields round`() {
        val raw = """
            %AAB_Form1A = 6.8
            %AAB_Form2C = 17.4
            %AAB_MinBright = 12.0
            %AAB_Throttle = 1500.6
        """.trimIndent()
        val s = TaskerLegacyProfileSerializer.deserialize(raw)
        assertEquals(6.8, s.form1A, 1e-9, "6.8 must be preserved exactly, not rounded to 7 or dropped to 5")
        assertEquals(17, s.form2C, "17.4 rounds to 17 (still Int)")
        assertEquals(12, s.minBrightness)
        assertEquals(1501L, s.throttleDefaultMs, "1500.6 rounds to 1501")
    }

    @Test
    fun `nested JSON fractional Form1A is preserved`() {
        val s = TaskerLegacyProfileSerializer.deserialize(
            """{ "general": { "form1a": 6.834 } }""",
        )
        assertEquals(6.834, s.form1A, 1e-9)
    }

    // G2R-F70: a legacy load = hard-coded task570 defaults THEN the file's diffs. A config that omits a
    // field must reset it to its task570 default, NOT inherit whatever was loaded before. The serializer
    // starts from AabSettings() (= task570 baseline), so a partial config drops back to defaults.
    @Test
    fun `partial config resets unspecified fields to task570 defaults not previous values`() {
        // Only zone1End differs; everything else must equal the hard-coded baseline.
        val s = TaskerLegacyProfileSerializer.deserialize("""{ "general": { "z1_end": 50.0 } }""")
        assertEquals(50, s.zone1End, "the file's diff applies")
        val baseline = AabSettings()
        assertEquals(baseline.form1A, s.form1A, "absent form1A resets to the default, never inherits")
        assertEquals(baseline.minBrightness, s.minBrightness)
        assertEquals(baseline.scale, s.scale, 0.001f)
        assertEquals(baseline.deltaFactor, s.deltaFactor, 0.001f)
    }

    @Test
    fun `unknown variables are ignored`() {
        val raw = """
            %AAB_MinBright = 20
            %UNRELATED = ignored
            %NotAnAAB = also_ignored
        """.trimIndent()
        val s = TaskerLegacyProfileSerializer.deserialize(raw)
        assertEquals(20, s.minBrightness)
    }

    @Test
    fun `NaN values in an imported profile cannot poison the settings D146`() {
        // "NaN".toDoubleOrNull() parses (Java Double.parseDouble accepts NaN/Infinity), and NaN slips
        // through every coerceIn — so a malformed or hostile profile file could persist NaN into the
        // brightness math. The parse must come out finite (the field default).
        val raw = """
            %AAB_Scale = NaN
            %AAB_Form1A = NaN
            %AAB_DeltaFactor = Infinity
            %AAB_ThreshMidpoint = -Infinity
        """.trimIndent()
        val s = TaskerLegacyProfileSerializer.deserialize(raw)
        assertTrue(s.scale.isFinite(), "NaN scale must not survive the import")
        assertEquals(AabSettings().scale, s.scale, 0.001f)
        assertTrue(s.form1A.isFinite(), "NaN form1A must not survive the import")
        assertEquals(10.0f, s.deltaFactor, 0.001f, "+Infinity clamps to the deltaFactor max")
        assertEquals(0.0, s.thresholdMidpoint, "-Infinity clamps to the midpoint min")
    }
}
