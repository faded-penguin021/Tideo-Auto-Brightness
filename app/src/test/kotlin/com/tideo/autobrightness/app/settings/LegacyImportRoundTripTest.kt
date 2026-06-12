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
        %AAB_ThreshDynamic = 5
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
        assertEquals(5, settings.form1A)
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
        assertEquals(5, settings.thresholdDynamic)
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
}
