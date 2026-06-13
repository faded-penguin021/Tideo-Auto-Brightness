package com.tideo.autobrightness.app.runtime

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Gate truth-table test (S9a deliverable 5, D-027): asserts every branch of the prof760 and
 * prof758 ConditionList transcriptions. A mis-parenthesized gate silently suppresses sensor
 * events with no other test catching it, so each clause is exercised true AND false.
 */
class ProfileGatesTest {

    // Convenience: a sample that passes all three prof760 sub-gates.
    private fun monitor(
        trustUnreliable: Boolean = false,
        accuracy: Int = 3,
        lux: Double = 5.0,
        low: Double = 10.0,
        high: Double = 100.0,
        mainLoopOn: Boolean = false,
        seeded: Boolean = true,
    ) = ProfileGates.monitorAmbientLightGate(
        trustUnreliable, accuracy, lux, low, high, mainLoopOn, seeded,
    )

    // --- prof760: accuracy-trust gate ---

    @Test fun monitor_trustUnreliableOn_lowAccuracy_passesTrust() {
        // ((TrustUnreliable=On) OR ...) — On short-circuits the accuracy requirement.
        assertTrue(monitor(trustUnreliable = true, accuracy = 1))
    }

    @Test fun monitor_trustUnreliableOff_lowAccuracy_failsTrust() {
        // (TrustUnreliable=Off AND accuracy>1) — accuracy 1 (Low) is rejected.
        assertFalse(monitor(trustUnreliable = false, accuracy = 1))
    }

    @Test fun monitor_trustUnreliableOff_mediumAccuracy_passesTrust() {
        assertTrue(monitor(trustUnreliable = false, accuracy = 2))
    }

    // --- prof760: absolute dead-band gate ---

    @Test fun monitor_luxInsideBand_failsDeadBand() {
        // lux is between low and high → inside the dead-band → suppressed.
        assertFalse(monitor(lux = 50.0, low = 10.0, high = 100.0))
    }

    @Test fun monitor_luxBelowLow_passesDeadBand() {
        assertTrue(monitor(lux = 5.0, low = 10.0, high = 100.0))
    }

    @Test fun monitor_luxAboveHigh_passesDeadBand() {
        assertTrue(monitor(lux = 200.0, low = 10.0, high = 100.0))
    }

    @Test fun monitor_notSeeded_bypassesDeadBand() {
        // First reading: thresholds not yet seeded → always enter the first-run init path.
        assertTrue(monitor(lux = 50.0, low = 10.0, high = 100.0, seeded = false))
    }

    // --- prof760: main-loop mutex ---

    @Test fun monitor_mainLoopOn_failsMutex() {
        assertFalse(monitor(mainLoopOn = true))
    }

    @Test fun monitor_allGatesPass() {
        assertTrue(monitor(trustUnreliable = false, accuracy = 3, lux = 5.0, mainLoopOn = false))
    }

    // --- prof758: dynamic scale ---

    private fun scale(
        nowMod: Double,
        morningStart: Double = 6 * 3600.0,
        morningEnd: Double = 8 * 3600.0,
        eveningStart: Double = 18 * 3600.0,
        eveningEnd: Double = 20 * 3600.0,
        stale: Boolean = false,
        scalingUse: Boolean = true,
    ) = ProfileGates.dynamicScaleGate(
        nowMod, morningStart, morningEnd, eveningStart, eveningEnd, stale, scalingUse,
    )

    @Test fun scale_scalingDisabled_alwaysFalse() {
        // The final And2: (... ) AND (ScalingUse=true).
        assertFalse(scale(nowMod = 7 * 3600.0, scalingUse = false))
        assertFalse(scale(nowMod = 7 * 3600.0, stale = true, scalingUse = false))
    }

    @Test fun scale_insideMorningWindow_fires() {
        assertTrue(scale(nowMod = 7 * 3600.0))
    }

    @Test fun scale_insideEveningWindow_fires() {
        assertTrue(scale(nowMod = 19 * 3600.0))
    }

    @Test fun scale_outsideWindows_notStale_doesNotFire() {
        assertFalse(scale(nowMod = 12 * 3600.0))
    }

    @Test fun scale_sunDataStale_firesEvenOutsideWindows() {
        // c12 (sun data stale today) is an OR member of the inner group.
        assertTrue(scale(nowMod = 12 * 3600.0, stale = true))
    }

    @Test fun scale_windowWrapsMidnight_matchesViaDayOffset() {
        // Evening window 23:30→00:30 wraps midnight; now=00:10 must match via the ±86400 wrap.
        val start = 23 * 3600.0 + 1800.0 // 23:30
        val end = 24 * 3600.0 + 1800.0   // 00:30 next day (> 86400)
        assertTrue(scale(nowMod = 600.0, eveningStart = start, eveningEnd = end))
    }

    // --- prof755: allow override ---

    @Test fun override_allConditionsMet() {
        assertTrue(
            ProfileGates.allowOverrideGate(
                serviceOn = true, autoBrightRunning = false, manualOverride = false,
                initializing = false, detectOverrides = true,
            ),
        )
    }

    @Test fun override_eachBlockingConditionVetoes() {
        assertFalse(ProfileGates.allowOverrideGate(false, false, false, false, true))   // service off
        assertFalse(ProfileGates.allowOverrideGate(true, true, false, false, true))     // mid-write
        assertFalse(ProfileGates.allowOverrideGate(true, false, true, false, true))     // already paused
        assertFalse(ProfileGates.allowOverrideGate(true, false, false, true, true))     // initializing
        assertFalse(ProfileGates.allowOverrideGate(true, false, false, false, false))   // detect off
    }
}
