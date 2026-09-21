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

/** D-151: profile fields → device (only-on-change). Seed no-op, swaps write diffs, stop() returns to baseline. */
@OptIn(ExperimentalCoroutinesApi::class)
class DisplayTogglesCoordinatorTest {

    private class FakeSecureDisplay : SecureDisplayController {
        override var nightLightAvailable = true
        override var alwaysOnDisplayAvailable = true
        val writes = mutableListOf<String>()
        // DB-048: what the coordinator ASKED for, recorded before the capability gate. Without it a
        // gated assertion only re-reads this fake's own `if`, and would still pass if the coordinator
        // stopped calling the controller at all. The production gate is pinned in
        // SecureDisplayControllerTest; this fake only stands in for it.
        val attempts = mutableListOf<String>()
        var stayAwake: Boolean? = false
        var deviceTemp: Int? = null
        var failTemperatureWrites = false
        private fun write(entry: String): Result<Unit> {
            writes += entry
            return Result.success(Unit)
        }

        private fun gated(entry: String, available: Boolean): Result<Unit> {
            attempts += entry
            return if (available) write(entry) else Result.success(Unit)
        }

        override fun readNightLight() = false
        override fun setNightLight(on: Boolean) = gated("nightLight=$on", nightLightAvailable)
        override fun readNightLightTemperature(): Int? = deviceTemp
        override fun setNightLightTemperature(kelvin: Int): Result<Unit> {
            if (failTemperatureWrites) {
                attempts += "temp=$kelvin"
                return Result.failure(SecurityException("refused"))
            }
            return gated("temp=$kelvin", nightLightAvailable)
        }
        override fun readNightLightAutoMode() = NightLightAutoMode.MANUAL
        override fun readDaltonizer() = DaltonizerMode.OFF
        override fun setDaltonizer(mode: DaltonizerMode) = write("daltonizer=$mode")
        override fun readInversion() = false
        override fun setInversion(on: Boolean) = write("inversion=$on")
        override fun readAlwaysOnDisplay() = false
        override fun setAlwaysOnDisplay(on: Boolean) = gated("aod=$on", alwaysOnDisplayAvailable)
        override fun readStayAwakePlugged() = stayAwake
        override fun setStayAwakePlugged(on: Boolean): Result<Unit> {
            stayAwake = on
            return write("stayAwake=$on")
        }
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
        // D-154: ramp Kelvin the fake "sun" yields; null = not computable
        var rampKelvin: Int? = null
        var anchorGiven: Int? = null
        var storedAnchor: Int? = null
        val baselineFlow = MutableStateFlow(baseline)
        val effectiveFlow = MutableStateFlow<AabSettings?>(null)
        val coordinator = DisplayTogglesCoordinator(
            effectiveFlow = effectiveFlow,
            baselineFlow = baselineFlow,
            display = display,
            tierProvider = { this.tier },
            circadianTemperature = { _, nightKelvin ->
                anchorGiven = nightKelvin
                this.rampKelvin
            },
            readAnchor = { this.storedAnchor },
            writeAnchor = { this.storedAnchor = it },
            tickIntervalMs = tickIntervalMs,
        )
    }

    @Test
    fun seed_adoptsBaselineWithoutWriting_evenWhenFirstEffectiveEqualsBaseline() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness()
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = baseline
        runCurrent()
        // Service start is not a profile change; must never touch device
        assertTrue(h.display.writes.isEmpty(), "seed must not write: ${h.display.writes}")
    }

    @Test
    fun profileSwap_writesOnlyTheChangedFields() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness()
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = baseline
        h.effectiveFlow.value = nightProfile
        runCurrent()
        // inversion is false in both; must not write
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
        // Baseline turns Night Light off; null temp means "no opinion" (persistent system pref)
        assertEquals(listOf("nightLight=false", "daltonizer=OFF"), h.display.writes)
    }

    @Test
    fun equalSwap_writesNothing_soManualChangesStick() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness()
        h.coordinator.start(backgroundScope)
        // Two profiles with identical display fields: no write, so manual/system changes stick
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
        // Grant does not retroactively replay; only next change asserts
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
        // D-151: service stop re-applies baseline (no latch)
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
        // User edits baseline profile; reevaluate() republishes as effective
        val edited = baseline.copy(nightLightEnabled = true)
        h.baselineFlow.value = edited
        h.effectiveFlow.value = edited
        runCurrent()
        assertEquals(listOf("nightLight=true"), h.display.writes)
        h.display.writes.clear()
        // Resting state tracks baseline edit; stop() has nothing to undo
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
    fun stayAwakeChange_preservesAnUnrepresentableDeviceMask_DB077() =
        runTest(UnconfinedTestDispatcher()) {
            val h = Harness()
            h.display.stayAwake = null // Android currently holds a mask such as legacy value 7.
            h.coordinator.start(backgroundScope)
            h.effectiveFlow.value = baseline
            h.effectiveFlow.value = baseline.copy(stayAwakeChargingEnabled = true)
            h.effectiveFlow.value = baseline
            runCurrent()

            assertTrue(h.display.writes.isEmpty(), "profile transitions must preserve the custom mask")
        }

    @Test
    fun stop_preservesAnUnrepresentableStayAwakeMask_DB077() =
        runTest(UnconfinedTestDispatcher()) {
            val h = Harness()
            h.coordinator.start(backgroundScope)
            h.effectiveFlow.value = baseline.copy(stayAwakeChargingEnabled = true)
            runCurrent()
            h.display.writes.clear()
            h.display.stayAwake = null

            h.coordinator.stop()

            assertTrue(h.display.writes.isEmpty(), "service stop must preserve the custom mask")
        }

    @Test
    fun stayAwakeChange_diffWritesRepresentableDeviceStates_DB077() =
        runTest(UnconfinedTestDispatcher()) {
            val h = Harness()
            h.coordinator.start(backgroundScope)
            h.effectiveFlow.value = baseline
            h.effectiveFlow.value = baseline.copy(stayAwakeChargingEnabled = true)
            h.effectiveFlow.value = baseline
            runCurrent()

            assertEquals(listOf("stayAwake=true", "stayAwake=false"), h.display.writes)
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
    fun unsupportedNightLightAndAod_cannotBeBypassedByProfilesTicksOrPanic() =
        runTest(UnconfinedTestDispatcher()) {
            val h = Harness(tickIntervalMs = 1_000L)
            h.display.nightLightAvailable = false
            h.display.alwaysOnDisplayAvailable = false
            h.rampKelvin = 3_200
            h.coordinator.start(backgroundScope)
            h.effectiveFlow.value = baseline
            h.effectiveFlow.value = circadianProfile.copy(alwaysOnDisplayEnabled = true)
            runCurrent()
            advanceTimeBy(1_100); runCurrent()
            h.coordinator.panicReset()

            assertTrue(
                h.display.writes.none { it.startsWith("nightLight=") || it.startsWith("temp=") || it.startsWith("aod=") },
                "capability-gated features must never reach a write: ${h.display.writes}",
            )
            // DB-048: and the suppression must be the CONTROLLER's, not a coordinator-local skip —
            // otherwise this passes just as well on a build where the gate has been deleted.
            assertTrue(
                h.display.attempts.any { it.startsWith("nightLight=") } &&
                    h.display.attempts.any { it.startsWith("aod=") },
                "every path must still route through the gated setters: ${h.display.attempts}",
            )
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
        // Sun unchanged; tick must be silent (no churn)
        advanceTimeBy(61_000); runCurrent()
        assertTrue(h.display.writes.isEmpty(), "unchanged ramp must not rewrite: ${h.display.writes}")
        // Sun moved; one write per change
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

        // Tracking on but below ELEVATED; inert (D-151 tier-gate)
        h.effectiveFlow.value = circadianProfile
        runCurrent()
        h.display.writes.clear()
        h.tier = Tier.BASIC
        h.rampKelvin = 3_200
        advanceTimeBy(60_000); runCurrent()
        assertTrue(h.display.writes.isEmpty(), "below ELEVATED the tick must not write: ${h.display.writes}")

        // Ramp not computable (null); skip with no crash
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
        // Static profile's 2700 equals anchor, but device sits at last ramp write (3400);
        // diff vs what was written, not profile history
        h.effectiveFlow.value = circadianProfile.copy(nightLightCircadianEnabled = false)
        runCurrent()
        assertEquals(listOf("temp=2700"), h.display.writes)
    }

    @Test
    fun leavingCircadian_forANoOpinionProfile_putsTheDevicesOwnKelvinBack_DC056() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness()
        h.display.deviceTemp = 1_500 // what the user had set before Tideo touched the key
        h.rampKelvin = 3_400
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = circadianProfile
        runCurrent()
        h.display.writes.clear()
        h.effectiveFlow.value = baseline
        runCurrent()
        assertEquals(listOf("nightLight=false", "temp=1500"), h.display.writes)
        assertEquals(null, h.storedAnchor, "releasing the key must clear the persisted anchor")
    }


    private val deviceDefaultProfile = circadianProfile.copy(nightLightTemperature = null)

    @Test
    fun aNullSetpoint_anchorsTheRampToTheDevicesOwnKelvin_notAConstant_DC056() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness()
        h.display.deviceTemp = 1_500
        h.rampKelvin = 3_400
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = deviceDefaultProfile
        runCurrent()
        assertEquals(1_500, h.anchorGiven)
        assertEquals(1_500, h.storedAnchor, "taking the key over must record what it displaced")
    }

    @Test
    fun anExplicitSetpoint_stillAnchorsTheRamp_DC056() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness()
        h.display.deviceTemp = 1_500
        h.rampKelvin = 3_400
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = circadianProfile // setpoint 2700
        runCurrent()
        assertEquals(2_700, h.anchorGiven, "an explicit setpoint outranks the device")
    }

    @Test
    fun aGenuinelyUnsetKey_fallsBackToTheFrameworkDefault_DC056() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness()
        h.display.deviceTemp = null // the key has never been written on this device
        h.rampKelvin = 3_400
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = deviceDefaultProfile
        runCurrent()
        assertEquals(SecureDisplayController.NIGHT_LIGHT_DEFAULT_K, h.anchorGiven)
    }

    @Test
    fun aSurvivingAnchor_isReusedRatherThanResampledFromTheRamp_DC056() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness()
        h.storedAnchor = 1_500
        h.display.deviceTemp = 3_400
        h.rampKelvin = 3_300
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = deviceDefaultProfile
        runCurrent()
        assertEquals(1_500, h.anchorGiven, "the anchor outlives the process that captured it")
    }

    @Test
    fun repeatedCircadianCycles_doNotRatchetTheAnchor_DC056() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness()
        h.display.deviceTemp = 1_500
        h.rampKelvin = 4_000 // daytime: the ramp sits near the day endpoint
        h.coordinator.start(backgroundScope)
        repeat(3) {
            h.effectiveFlow.value = deviceDefaultProfile
            runCurrent()
            h.effectiveFlow.value = deviceDefaultProfile.copy(nightLightCircadianEnabled = false)
            runCurrent()
        }
        assertEquals(1_500, h.anchorGiven, "the anchor must not drift toward the day endpoint")
    }

    @Test
    fun stop_handsTheKeyBack_evenWhenTheBaselineStillAsksForARamp_DC056() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness(baseline = deviceDefaultProfile)
        h.display.deviceTemp = 1_500
        h.rampKelvin = 3_400
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = deviceDefaultProfile
        runCurrent()
        advanceTimeBy(61_000); runCurrent()
        assertEquals(listOf("temp=3400"), h.display.writes)
        h.display.writes.clear()
        h.coordinator.stop()
        assertEquals(listOf("temp=1500"), h.display.writes)
        assertEquals(null, h.storedAnchor)
    }

    @Test
    fun aSurvivingAnchor_isRestored_evenWhenTheProfileMatchesTheSeededAssumption_DC056() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness(baseline = nightProfile)
        h.storedAnchor = 1_500
        h.display.deviceTemp = 3_400
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = nightProfile
        runCurrent()
        assertEquals(listOf("temp=2700"), h.display.writes)
        assertEquals(null, h.storedAnchor)
    }

    @Test
    fun aFailedRestore_keepsTheAnchorForTheNextAttempt_DC056() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness()
        h.display.deviceTemp = 1_500
        h.rampKelvin = 3_400
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = deviceDefaultProfile
        runCurrent()
        h.display.failTemperatureWrites = true
        h.effectiveFlow.value = baseline
        runCurrent()
        assertEquals(1_500, h.storedAnchor, "a restore that did not land must not surrender the key")

        h.display.failTemperatureWrites = false
        h.display.writes.clear()
        h.effectiveFlow.value = baseline.copy(inversionEnabled = true)
        runCurrent()
        assertTrue(h.display.writes.contains("temp=1500"), "the retry must land: ${h.display.writes}")
        assertEquals(null, h.storedAnchor)
    }

    @Test
    fun panic_putsTheDisplacedKelvinBack_DC056_revisingD155() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness()
        h.display.deviceTemp = 1_500
        h.rampKelvin = 3_400
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = deviceDefaultProfile
        runCurrent()
        h.display.writes.clear()
        h.coordinator.panicReset()
        assertEquals("temp=1500", h.display.writes.getOrNull(1))
        assertEquals("nightLight=false", h.display.writes.getOrNull(0))
        assertEquals(null, h.storedAnchor, "panic must not strand the anchor")
    }

    @Test
    fun aFailedRestore_doesNotLetTheStaticWriteFakeTheHandover_DC056() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness()
        h.display.deviceTemp = 1_500
        h.rampKelvin = 3_400
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = deviceDefaultProfile
        runCurrent()
        h.display.failTemperatureWrites = true
        h.display.writes.clear()
        h.effectiveFlow.value = nightProfile // static 2700: the fall-through would write it
        runCurrent()
        assertTrue(
            h.display.writes.none { it.startsWith("temp=") },
            "a second attempt must not stand in for the restore: ${h.display.writes}",
        )
        assertEquals(1_500, h.storedAnchor, "the key is still ours until the restore lands")
    }

    @Test
    fun leavingCircadian_forANightLightBeingSwitchedOn_restoresBeforeEnabling_DC056() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness()
        h.display.deviceTemp = 1_500
        h.rampKelvin = 3_400
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = deviceDefaultProfile.copy(nightLightEnabled = false)
        runCurrent()
        advanceTimeBy(61_000); runCurrent() // the ticker takes the key over
        h.display.writes.clear()
        h.effectiveFlow.value = baseline.copy(nightLightEnabled = true)
        runCurrent()
        assertEquals(
            listOf("temp=1500", "nightLight=true"),
            h.display.writes,
            "enabling first would show the ramp sample before the restore landed",
        )
    }

    @Test
    fun anUnwritableKey_isNotTreatedAsRestored_DC056() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness()
        h.display.deviceTemp = 1_500
        h.rampKelvin = 3_400
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = deviceDefaultProfile
        runCurrent()
        h.display.nightLightAvailable = false // a capability or ROM change under us
        h.effectiveFlow.value = baseline
        runCurrent()
        assertEquals(1_500, h.storedAnchor, "a successful no-op is not a restore")
    }

    @Test
    fun belowElevated_releasingKeepsTheAnchorPending_DC056() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness()
        h.display.deviceTemp = 1_500
        h.rampKelvin = 3_400
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = deviceDefaultProfile
        runCurrent()
        h.tier = Tier.BASIC
        h.display.writes.clear()
        h.effectiveFlow.value = baseline
        runCurrent()
        assertTrue(h.display.writes.isEmpty(), "nothing is writable below ELEVATED: ${h.display.writes}")
        assertEquals(1_500, h.storedAnchor, "an anchor that could not be restored must not be lost")
    }

    // --- D-155: panic resets the privileged display keys to DEFAULTS ---

    @Test
    fun panicReset_writesAllDefaults_unconditionally_D155() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness()
        h.coordinator.start(backgroundScope)
        h.effectiveFlow.value = baseline
        runCurrent()
        // Panic writes every field unconditionally (post-death residuals invisible)
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
        // stop() after panic must find coordinator stopped; write nothing
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
        // Same-process re-enable: restart's first effective differs; re-asserts configuration
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
        // Drift guard: DALTONIZER_MODES mirrors platform DaltonizerMode
        assertEquals(DaltonizerMode.entries.map { it.name }.toSet(), DALTONIZER_MODES)
    }
}
