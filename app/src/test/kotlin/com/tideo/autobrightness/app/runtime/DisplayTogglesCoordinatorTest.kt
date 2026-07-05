package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.DALTONIZER_MODES
import com.tideo.autobrightness.platform.display.DaltonizerMode
import com.tideo.autobrightness.platform.display.NightLightAutoMode
import com.tideo.autobrightness.platform.display.SecureDisplayController
import com.tideo.autobrightness.platform.privilege.Tier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * DisplayTogglesCoordinator (D-151): profile fields → device via the only-on-change apply path —
 * seed adopts the baseline without writing, profile swaps write per-field diffs, equal swaps write
 * nothing (manual/system changes stick), tier gate no-ops below ELEVATED, a null temperature is
 * never written, and stop() returns the toggles to the baseline's values (the resting state).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DisplayTogglesCoordinatorTest {

    /** In-memory device with a write log — the assertions are on WHICH writes happen. */
    private class FakeSecureDisplay : SecureDisplayController {
        val writes = mutableListOf<String>()
        private fun write(entry: String): Result<Unit> {
            writes += entry
            return Result.success(Unit)
        }

        override fun readNightLight() = false
        override fun setNightLight(on: Boolean) = write("nightLight=$on")
        override fun readNightLightTemperature(): Int? = null
        override fun setNightLightTemperature(kelvin: Int) = write("temp=$kelvin")
        override fun readNightLightAutoMode() = NightLightAutoMode.MANUAL
        override fun readDaltonizer() = DaltonizerMode.OFF
        override fun setDaltonizer(mode: DaltonizerMode) = write("daltonizer=$mode")
        override fun readInversion() = false
        override fun setInversion(on: Boolean) = write("inversion=$on")
        override fun readAlwaysOnDisplay() = false
        override fun setAlwaysOnDisplay(on: Boolean) = write("aod=$on")
        override fun readStayAwakePlugged() = false
        override fun setStayAwakePlugged(on: Boolean) = write("stayAwake=$on")
        override var hdrForceSdrAvailable = true
        override fun readHdrForceSdr() = false
        override fun setHdrForceSdr(on: Boolean) = write("hdr=$on")
    }

    private val baseline = AabSettings()
    private val nightProfile = AabSettings(
        nightLightEnabled = true,
        nightLightTemperature = 2_700,
        daltonizerMode = "GRAYSCALE",
        inversionEnabled = false,
    )

    private class Harness(
        tier: Tier = Tier.ELEVATED,
        baseline: AabSettings = AabSettings(),
        tickIntervalMs: Long = 60_000L,
    ) {
        val display = FakeSecureDisplay()
        var tier = tier
        /** The D-154 ramp Kelvin the fake "sun" currently yields; null = ramp not computable. */
        var rampKelvin: Int? = null
        val baselineFlow = MutableStateFlow(baseline)
        val effectiveFlow = MutableStateFlow<AabSettings?>(null)
        val coordinator = DisplayTogglesCoordinator(
            effectiveFlow = effectiveFlow,
            baselineFlow = baselineFlow,
            display = display,
            tierProvider = { this.tier },
            circadianTemperature = { this.rampKelvin },
            tickIntervalMs = tickIntervalMs,
        )
    }

    @Test
    fun seed_adoptsBaselineWithoutWriting_evenWhenFirstEffectiveEqualsBaseline() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness()
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = baseline
        runCurrent()
        // Service start is not a profile change: a default chain must never touch the device
        // (the system Night Light schedule keeps working for non-users of the feature).
        assertTrue(h.display.writes.isEmpty(), "seed must not write: ${h.display.writes}")
    }

    @Test
    fun profileSwap_writesOnlyTheChangedFields() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness()
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = baseline
        h.effectiveFlow.value = nightProfile
        runCurrent()
        // inversion is false in BOTH — it must not be written.
        assertEquals(
            listOf("nightLight=true", "temp=2700", "daltonizer=GRAYSCALE"),
            h.display.writes,
        )
    }

    @Test
    fun swapBackToBaseline_restoresBaselineValues_butNeverWritesANullTemperature() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness()
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = nightProfile
        h.display.writes.clear()
        h.effectiveFlow.value = baseline
        runCurrent()
        // Baseline turns Night Light + grayscale back off; its null temperature means "no opinion"
        // (the system treats the temperature as a persistent preference), so no temp write.
        assertEquals(listOf("nightLight=false", "daltonizer=OFF"), h.display.writes)
    }

    @Test
    fun equalSwap_writesNothing_soManualChangesStick() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness()
        h.coordinator.start(backgroundScope)
        // Two different profiles with IDENTICAL display fields (e.g. Battery Saver → Outdoors,
        // both defaults): no display write, so a manual/system toggle in between is not stomped.
        h.effectiveFlow.value = baseline
        h.effectiveFlow.value = baseline.copy(minBrightness = 42)
        runCurrent()
        assertTrue(h.display.writes.isEmpty(), "equal display fields must not write: ${h.display.writes}")
    }

    @Test
    fun belowElevated_isNoOp_butKeepsTracking() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness(tier = Tier.BASIC)
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = baseline
        h.effectiveFlow.value = nightProfile
        runCurrent()
        assertTrue(h.display.writes.isEmpty(), "below ELEVATED nothing is written: ${h.display.writes}")
        // A later grant does NOT retroactively replay the skipped swap — only the next CHANGE
        // asserts (the dimming coordinator's tier-gate semantics).
        h.tier = Tier.ELEVATED
        h.effectiveFlow.value = nightProfile.copy(inversionEnabled = true)
        runCurrent()
        assertEquals(listOf("inversion=true"), h.display.writes)
    }

    @Test
    fun stop_returnsToTheBaselineValues() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness()
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = nightProfile
        runCurrent()
        h.display.writes.clear()
        h.coordinator.stop()
        // D-151 resting state: a service stop mid-override re-applies the baseline (no latch).
        assertEquals(listOf("nightLight=false", "daltonizer=OFF"), h.display.writes)
    }

    @Test
    fun stop_withoutLeavingBaseline_writesNothing() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness()
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = baseline
        runCurrent()
        h.coordinator.stop()
        assertTrue(h.display.writes.isEmpty(), "a session that stayed at baseline must not write on stop")
    }

    @Test
    fun baselineEditWhileRunning_appliesViaTheEffectiveFlow_andBecomesTheNewRestingState() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness()
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = baseline
        // User edits the baseline profile (Apply on the Privileged Display profile section):
        // reevaluate() republishes the new baseline as the effective settings.
        val edited = baseline.copy(nightLightEnabled = true)
        h.baselineFlow.value = edited
        h.effectiveFlow.value = edited
        runCurrent()
        assertEquals(listOf("nightLight=true"), h.display.writes)
        h.display.writes.clear()
        // The resting state followed the baseline edit: stop() has nothing to undo.
        h.coordinator.stop()
        assertTrue(h.display.writes.isEmpty(), "resting state must track the live baseline")
    }

    @Test
    fun screenFields_aodStayAwakeHdr_writeOnDiff_D152() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness()
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = baseline
        h.effectiveFlow.value = baseline.copy(
            alwaysOnDisplayEnabled = true,
            stayAwakeChargingEnabled = true,
            hdrForceSdrEnabled = true,
        )
        runCurrent()
        assertEquals(listOf("aod=true", "stayAwake=true", "hdr=true"), h.display.writes)
    }

    @Test
    fun hdrField_isInert_whenTheDeviceLacksHdrControl_D152() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness()
        h.display.hdrForceSdrAvailable = false // pre-Android-14 device
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = baseline
        h.effectiveFlow.value = baseline.copy(hdrForceSdrEnabled = true)
        runCurrent()
        assertTrue(h.display.writes.isEmpty(), "hdr must not be written when unavailable: ${h.display.writes}")
    }

    @Test
    fun unknownDaltonizerString_fallsBackToOff() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness()
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = nightProfile
        h.display.writes.clear()
        // Un-validated input (validate() would have reset it): the coordinator must not crash and
        // must treat the unknown mode as OFF.
        h.effectiveFlow.value = nightProfile.copy(daltonizerMode = "SEPIA_FROM_THE_FUTURE")
        runCurrent()
        assertEquals(listOf("daltonizer=OFF"), h.display.writes)
    }

    // --- D-154: circadian Night Light temperature ---

    private val circadianProfile = AabSettings(
        nightLightEnabled = true,
        nightLightTemperature = 2_700, // the night anchor while tracking
        nightLightCircadianEnabled = true,
    )

    @Test
    fun circadianSwapIn_writesTheCurrentRampValue_notTheStaticAnchor_D154() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness()
        h.rampKelvin = 3_400
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = baseline
        h.effectiveFlow.value = circadianProfile
        runCurrent()
        assertEquals(listOf("nightLight=true", "temp=3400"), h.display.writes)
    }

    @Test
    fun ticker_movesTheTemperature_onlyOnChange_D154() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness()
        h.rampKelvin = 3_400
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = circadianProfile
        runCurrent()
        h.display.writes.clear()
        // Sun unchanged → the tick must be silent (no per-minute settings churn).
        advanceTimeBy(61_000); runCurrent()
        assertTrue(h.display.writes.isEmpty(), "unchanged ramp must not rewrite: ${h.display.writes}")
        // Sun moved → exactly one write per changed value.
        h.rampKelvin = 3_300
        advanceTimeBy(60_000); runCurrent()
        advanceTimeBy(60_000); runCurrent()
        assertEquals(listOf("temp=3300"), h.display.writes)
    }

    @Test
    fun ticker_isInert_whenTrackingIsOff_orBelowElevated_orRampUnavailable_D154() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness()
        h.rampKelvin = 3_400
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = nightProfile // static temperature profile — ticker not in play
        runCurrent()
        h.display.writes.clear()
        advanceTimeBy(61_000); runCurrent()
        assertTrue(h.display.writes.isEmpty(), "static profile must not tick: ${h.display.writes}")

        // Tracking on but below ELEVATED → inert (the D-151 tier-gate semantics).
        h.effectiveFlow.value = circadianProfile
        runCurrent()
        h.display.writes.clear()
        h.tier = Tier.BASIC
        h.rampKelvin = 3_200
        advanceTimeBy(60_000); runCurrent()
        assertTrue(h.display.writes.isEmpty(), "below ELEVATED the tick must not write: ${h.display.writes}")

        // Ramp not computable (null) → skip, no crash, no write.
        h.tier = Tier.ELEVATED
        h.rampKelvin = null
        advanceTimeBy(60_000); runCurrent()
        assertTrue(h.display.writes.isEmpty(), "a null ramp must be skipped: ${h.display.writes}")
    }

    @Test
    fun leavingCircadian_forAStaticProfile_reassertsTheStaticAnchor_D154() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness()
        h.rampKelvin = 3_400
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = circadianProfile
        runCurrent()
        h.display.writes.clear()
        // The static profile's 2700 equals the circadian profile's recorded anchor, but the DEVICE
        // sits at the last ramp write (3400) — the diff must compare against what was written, not
        // the profile-field history, or the temperature would stick at the ramp value forever.
        h.effectiveFlow.value = circadianProfile.copy(nightLightCircadianEnabled = false)
        runCurrent()
        assertEquals(listOf("temp=2700"), h.display.writes)
    }

    @Test
    fun leavingCircadian_forANoOpinionProfile_leavesTheTemperatureAlone_D154() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness()
        h.rampKelvin = 3_400
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = circadianProfile
        runCurrent()
        h.display.writes.clear()
        // Baseline has a null temperature = "no opinion": per D-151 it never writes, so the last
        // ramp value stays (a persistent system preference, like any other null-temp hand-off).
        h.effectiveFlow.value = baseline
        runCurrent()
        assertEquals(listOf("nightLight=false"), h.display.writes)
    }

    // --- D-155: panic resets the privileged display keys to DEFAULTS ---

    @Test
    fun panicReset_writesAllDefaults_unconditionally_D155() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness()
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = baseline
        runCurrent()
        // Even though tracking believes everything already IS default (post-death residuals are
        // invisible to this process), panic writes every field — no diff, no temperature.
        h.coordinator.panicReset()
        assertEquals(
            listOf(
                "nightLight=false", "daltonizer=OFF", "inversion=false",
                "aod=false", "stayAwake=false", "hdr=false",
            ),
            h.display.writes,
        )
    }

    @Test
    fun panicReset_thenServiceStop_doesNotResurrectTheBaseline_D155() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness(baseline = nightProfile)
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = nightProfile
        runCurrent()
        h.display.writes.clear()
        h.coordinator.panicReset()
        h.display.writes.clear()
        // onDestroy's stop() follows the panic teardown — it must find the coordinator stopped
        // and write NOTHING (the baseline carries the values panic just cleared).
        h.coordinator.stop()
        assertTrue(h.display.writes.isEmpty(), "stop after panic must not re-apply the baseline: ${h.display.writes}")
    }

    @Test
    fun restartAfterPanic_reassertsTheBaseline_onTheFirstEffectiveEmission_D155() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness(baseline = nightProfile)
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = nightProfile
        runCurrent()
        h.coordinator.panicReset()
        h.display.writes.clear()
        // Same-process re-enable: lastApplied stayed at DEFAULTS, so the restart's first
        // effective emission (the baseline) DIFFERS and re-asserts the user's configuration —
        // the panic is an escape hatch, not a permanent opt-out.
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = nightProfile
        runCurrent()
        assertEquals(listOf("nightLight=true", "temp=2700", "daltonizer=GRAYSCALE"), h.display.writes)
    }

    @Test
    fun panicReset_belowElevated_writesNothing_D155() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness(tier = Tier.BASIC)
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = baseline
        runCurrent()
        h.coordinator.panicReset()
        assertTrue(h.display.writes.isEmpty(), "below ELEVATED panic has nothing it could write: ${h.display.writes}")
    }

    @Test
    fun panicReset_skipsHdr_whenUnavailable_D155() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness()
        h.display.hdrForceSdrAvailable = false // pre-Android-14 device
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = baseline
        runCurrent()
        h.coordinator.panicReset()
        assertTrue(h.display.writes.none { it.startsWith("hdr") }, "hdr must be skipped: ${h.display.writes}")
    }

    @Test
    fun daltonizerModeStrings_mirrorThePlatformEnum() {
        // Drift guard: AabSettings.DALTONIZER_MODES is the platform-import-free mirror of
        // DaltonizerMode — a rename/addition on either side must fail here.
        assertEquals(DaltonizerMode.entries.map { it.name }.toSet(), DALTONIZER_MODES)
    }
}
