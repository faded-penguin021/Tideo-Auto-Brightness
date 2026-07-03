package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.BatteryTrigger
import com.tideo.autobrightness.app.settings.ContextTriggers
import com.tideo.autobrightness.app.settings.DisplayRule
import com.tideo.autobrightness.domain.context.ContextSignals
import com.tideo.autobrightness.domain.display.DisplayAction
import com.tideo.autobrightness.platform.display.DaltonizerMode
import com.tideo.autobrightness.platform.display.DisplayRestoreLatch
import com.tideo.autobrightness.platform.display.NightLightAutoMode
import com.tideo.autobrightness.platform.display.SecureDisplayController
import com.tideo.autobrightness.platform.privilege.Tier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/**
 * DisplayRulesCoordinator (D-150): edge-triggered apply/restore with the death-safe latch —
 * engage/release transitions, pre-state restore, startup residual sweep, tier gate, manual-change
 * survival, overnight boundary semantics through the shared matcher, service-stop restore, and the
 * ContextEngine-style cost gates (rule-gated app poll incl. the D-142 stale-snapshot clear).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DisplayRulesCoordinatorTest {

    // --- fakes ---------------------------------------------------------------------------------

    /** Signal source honoring the PASSED app/battery values (the coordinator's own tracking is
     *  under test) with settable clock/day fields; counts live foreground-poll collectors. */
    private class FakeSignalSource(
        var dayOfWeek: Int = 4, // Wednesday
        var nowSecondsOfDay: Int = 12 * 3600,
    ) : ContextSignalSource {
        val appFlow = MutableSharedFlow<String?>(extraBufferCapacity = 16)
        var appCollectors = 0
        override fun batteryFlow(): Flow<BatterySignal> = MutableSharedFlow()
        override fun wifiFlow(): Flow<String?> = MutableSharedFlow()
        override fun foregroundAppFlow(intervalMs: Long): Flow<String?> =
            appFlow.onStart { appCollectors++ }.onCompletion { appCollectors-- }
        override fun locationFlow(): Flow<LocationSignal> = MutableSharedFlow()
        override suspend fun assemble(
            app: String, batteryPercent: Int, plugged: Boolean, wifi: String, lat: Double, lon: Double,
        ): ContextSignals = ContextSignals(
            app = app, lat = lat, lon = lon, batteryPercent = batteryPercent, plugged = plugged,
            wifi = wifi, dayOfWeek = dayOfWeek, nowSecondsOfDay = nowSecondsOfDay,
        )
    }

    /** In-memory device with a write log (shared [events] with the latch proves ordering). */
    private class FakeSecureDisplay(private val events: MutableList<String>) : SecureDisplayController {
        var failWrites = false
        var nightLight = false
        var daltonizer: DaltonizerMode = DaltonizerMode.OFF
        var inversion = false

        private fun write(name: String, apply: () -> Unit): Result<Unit> {
            if (failWrites) return Result.failure(SecurityException("stale grant"))
            events += "write:$name"
            apply()
            return Result.success(Unit)
        }

        override fun readNightLight() = nightLight
        override fun setNightLight(on: Boolean) = write("nightLight=$on") { nightLight = on }
        override fun readNightLightTemperature(): Int? = null
        override fun setNightLightTemperature(kelvin: Int) = write("temp=$kelvin") {}
        override fun readNightLightAutoMode() = NightLightAutoMode.MANUAL
        override fun readDaltonizer() = daltonizer
        override fun setDaltonizer(mode: DaltonizerMode) = write("daltonizer=$mode") { daltonizer = mode }
        override fun readInversion() = inversion
        override fun setInversion(on: Boolean) = write("inversion=$on") { inversion = on }
        override fun readAlwaysOnDisplay() = false
        override fun setAlwaysOnDisplay(on: Boolean) = write("aod=$on") {}
        override fun readStayAwakePlugged() = false
        override fun setStayAwakePlugged(on: Boolean) = write("stayAwake=$on") {}
        override val hdrForceSdrAvailable = false
        override fun readHdrForceSdr() = false
        override fun setHdrForceSdr(on: Boolean) = write("hdr=$on") {}
    }

    private class FakeLatch(private val events: MutableList<String>) : DisplayRestoreLatch {
        val map = mutableMapOf<String, String>()
        override fun preState(actionKey: String): String? = map[actionKey]
        override fun save(actionKey: String, preState: String) {
            events += "latch:$actionKey=$preState"
            map[actionKey] = preState
        }
        override fun clear(actionKey: String) {
            events += "unlatch:$actionKey"
            map.remove(actionKey)
        }
    }

    // --- fixtures ------------------------------------------------------------------------------

    private fun alwaysRule(action: DisplayAction = DisplayAction.GRAYSCALE) =
        DisplayRule(id = "always", name = "Always", action = action.name)

    /** Mon–Fri 22:00–06:00 (Calendar days 2..6) — the plan's overnight truth-table rule. */
    private fun overnightRule() = DisplayRule(
        id = "night", name = "Weeknights", action = DisplayAction.GRAYSCALE.name,
        triggers = ContextTriggers(timeRange = listOf("22:00", "06:00"), days = listOf(2, 3, 4, 5, 6)),
    )

    private fun appRule() = DisplayRule(
        id = "app", name = "Social", action = DisplayAction.GRAYSCALE.name,
        triggers = ContextTriggers(apps = listOf("com.social")),
    )

    private class Harness(testScope: TestScope) {
        val events = mutableListOf<String>()
        val src = FakeSignalSource()
        val display = FakeSecureDisplay(events)
        val latch = FakeLatch(events)
        var rules: List<DisplayRule> = emptyList()
        val rulesFlow = MutableSharedFlow<List<DisplayRule>>(extraBufferCapacity = 4)
        var tier: Tier = Tier.ELEVATED
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScope.testScheduler))
        val coordinator = DisplayRulesCoordinator(
            rulesProvider = { rules },
            rulesFlow = rulesFlow,
            signalSource = src,
            display = display,
            restoreLatch = latch,
            tierProvider = { tier },
            clock = { 0L },
        )

        /** Update the rule set the way the store does: new provider snapshot + a flow emission. */
        suspend fun editRules(newRules: List<DisplayRule>) {
            rules = newRules
            rulesFlow.emit(newRules)
        }
    }

    private fun TestScope.startedHarness(
        rules: List<DisplayRule> = emptyList(),
        configure: Harness.() -> Unit = {},
    ): Harness {
        val h = Harness(this)
        h.rules = rules
        h.configure()
        h.coordinator.start(h.scope)
        runCurrent()
        return h
    }

    // --- transitions ---------------------------------------------------------------------------

    @Test
    fun engageEdge_latchesPreStateBeforeWriting() = runTest {
        val h = startedHarness(listOf(alwaysRule()))
        assertEquals(DaltonizerMode.GRAYSCALE, h.display.daltonizer)
        assertEquals("OFF", h.latch.map["GRAYSCALE"])
        // Death-safety ordering: the latch commit must precede the device write.
        assertEquals(
            listOf("latch:GRAYSCALE=OFF", "write:daltonizer=GRAYSCALE"),
            h.events.filter { "GRAYSCALE" in it.uppercase() },
        )
        h.scope.cancel()
    }

    @Test
    fun holdWhileMatching_neverReasserts_soManualChangesStick() = runTest {
        val h = startedHarness(listOf(alwaysRule()))
        // The user flips the toggle off manually mid-window (writes directly, not via the rules).
        h.display.daltonizer = DaltonizerMode.OFF
        val writesBefore = h.events.count { it.startsWith("write:") }
        h.coordinator.onScreenOn() // any re-evaluation while still matching
        runCurrent()
        assertEquals(DaltonizerMode.OFF, h.display.daltonizer, "manual change must stick until the edge")
        assertEquals(writesBefore, h.events.count { it.startsWith("write:") }, "hold must not re-write")
        assertEquals("OFF", h.latch.map["GRAYSCALE"], "still engaged — latch stays")
        h.scope.cancel()
    }

    @Test
    fun releaseEdge_restoresPreState_andClearsLatch() = runTest {
        val h = startedHarness(listOf(overnightRule())) {
            src.dayOfWeek = 4; src.nowSecondsOfDay = 23 * 3600 // Wed 23:00 — matching
            display.daltonizer = DaltonizerMode.PROTANOMALY // a real pre-state, not just OFF
        }
        assertEquals(DaltonizerMode.GRAYSCALE, h.display.daltonizer)
        assertEquals("PROTANOMALY", h.latch.map["GRAYSCALE"])

        h.src.nowSecondsOfDay = 7 * 3600 // Wed 07:00 — window over
        h.coordinator.onScreenOn()
        runCurrent()
        assertEquals(DaltonizerMode.PROTANOMALY, h.display.daltonizer, "release must restore the pre-engage mode")
        assertNull(h.latch.map["GRAYSCALE"])
        h.scope.cancel()
    }

    @Test
    fun engageWriteFailure_clearsLatch_thenALaterEvaluationRetries() = runTest {
        val h = startedHarness(listOf(alwaysRule())) { display.failWrites = true }
        assertNull(h.latch.map["GRAYSCALE"], "failed engage must not claim engagement")
        assertEquals(DaltonizerMode.OFF, h.display.daltonizer)

        h.display.failWrites = false
        h.coordinator.onScreenOn()
        runCurrent()
        assertEquals(DaltonizerMode.GRAYSCALE, h.display.daltonizer, "next evaluation retries the engage")
        h.scope.cancel()
    }

    // --- startup residual sweep (process death) --------------------------------------------------

    @Test
    fun startupResidualSweep_restoresLatchedActionWithNoMatchingRule() = runTest {
        val h = Harness(this)
        // A previous process died mid-engagement: latch persisted, toggle still on, rule gone.
        h.latch.map["NIGHT_LIGHT"] = "0"
        h.display.nightLight = true
        h.coordinator.start(h.scope)
        runCurrent()
        assertFalse(h.display.nightLight, "residual sweep must restore the pre-death state")
        assertNull(h.latch.map["NIGHT_LIGHT"])
        h.scope.cancel()
    }

    @Test
    fun startupWithLatchAndStillMatchingRule_adoptsEngagement_keepsOriginalPreState() = runTest {
        val h = Harness(this)
        h.latch.map["GRAYSCALE"] = "OFF"
        h.display.daltonizer = DaltonizerMode.GRAYSCALE // the dead process had engaged it
        h.rules = listOf(alwaysRule())
        h.coordinator.start(h.scope)
        runCurrent()
        assertEquals(0, h.events.count { it.startsWith("write:") }, "adoption must not re-write")
        assertEquals("OFF", h.latch.map["GRAYSCALE"], "original pre-state survives for the eventual release")
        h.scope.cancel()
    }

    // --- tier gate -------------------------------------------------------------------------------

    @Test
    fun belowElevated_isInert_noWritesNoLatchChurn() = runTest {
        val h = startedHarness(listOf(alwaysRule())) {
            tier = Tier.BASIC
            latch.map["INVERSION"] = "0" // an old obligation must be preserved, not touched
        }
        assertEquals(0, h.events.size, "below ELEVATED: no writes, no latch churn")
        assertEquals("0", h.latch.map["INVERSION"])
        assertEquals(DaltonizerMode.OFF, h.display.daltonizer)
        h.scope.cancel()
    }

    // --- overnight boundary (shared matcher through the coordinator) ----------------------------

    @Test
    fun overnightRule_matchesSaturday0100_viaFridayTail() = runTest {
        val h = startedHarness(listOf(overnightRule())) {
            src.dayOfWeek = 7; src.nowSecondsOfDay = 3600 // Sat 01:00 — Friday's overnight tail
        }
        assertEquals(DaltonizerMode.GRAYSCALE, h.display.daltonizer)
        h.scope.cancel()
    }

    @Test
    fun overnightRule_doesNotMatchSunday2300() = runTest {
        val h = startedHarness(listOf(overnightRule())) {
            src.dayOfWeek = 1; src.nowSecondsOfDay = 23 * 3600 // Sun 23:00 — Sunday not in Mon–Fri
        }
        assertEquals(DaltonizerMode.OFF, h.display.daltonizer)
        assertNull(h.latch.map["GRAYSCALE"])
        h.scope.cancel()
    }

    // --- service stop -----------------------------------------------------------------------------

    @Test
    fun stop_restoresEngagedActions() = runTest {
        val h = startedHarness(listOf(alwaysRule(DisplayAction.NIGHT_LIGHT)))
        assertTrue(h.display.nightLight)
        h.coordinator.stop()
        assertFalse(h.display.nightLight, "service stop must not leave a schedule's toggle stuck on")
        assertNull(h.latch.map["NIGHT_LIGHT"])
        h.scope.cancel()
    }

    @Test
    fun stop_failedRestore_keepsTheLatchObligation() = runTest {
        val h = startedHarness(listOf(alwaysRule(DisplayAction.INVERSION)))
        assertTrue(h.display.inversion)
        h.display.failWrites = true // grant revoked mid-run
        h.coordinator.stop()
        assertTrue(h.display.inversion, "restore failed — device untouched")
        assertEquals("0", h.latch.map["INVERSION"], "obligation survives for the next start's sweep")
        h.scope.cancel()
    }

    // --- rule edits + cost gates ------------------------------------------------------------------

    @Test
    fun ruleEdit_reevaluatesImmediately() = runTest {
        val h = startedHarness()
        assertEquals(DaltonizerMode.OFF, h.display.daltonizer)
        h.editRules(listOf(alwaysRule()))
        runCurrent()
        assertEquals(DaltonizerMode.GRAYSCALE, h.display.daltonizer, "a saved rule must apply now, not later")
        h.scope.cancel()
    }

    @Test
    fun appPoll_onlyWhileAnEnabledAppRuleExists_andStaleAppClearsOnGateOff() = runTest {
        val h = startedHarness(listOf(appRule()))
        assertEquals(1, h.src.appCollectors, "app rule present → poll runs")

        h.src.appFlow.emit("com.social")
        runCurrent()
        assertEquals(DaltonizerMode.GRAYSCALE, h.display.daltonizer)

        // Deleting the app rule stops the poll and releases; the snapshot must clear too (D-142):
        h.editRules(emptyList())
        runCurrent()
        assertEquals(0, h.src.appCollectors, "no app rule → poll cancelled")
        assertEquals(DaltonizerMode.OFF, h.display.daltonizer)

        // Re-adding the rule must NOT match on the stale pre-delete package…
        h.editRules(listOf(appRule()))
        runCurrent()
        assertEquals(DaltonizerMode.OFF, h.display.daltonizer, "stale app snapshot must not match")
        // …but a fresh emission engages again.
        h.src.appFlow.emit("com.social")
        runCurrent()
        assertEquals(DaltonizerMode.GRAYSCALE, h.display.daltonizer)
        h.scope.cancel()
    }

    @Test
    fun disabledRule_isFullyInert() = runTest {
        val h = startedHarness(listOf(appRule().copy(enabled = false)))
        assertEquals(0, h.src.appCollectors, "a disabled app rule must not run the poll (cost gate)")
        assertEquals(0, h.events.size)
        h.scope.cancel()
    }

    @Test
    fun screenOff_stopsPoll_screenOnReevaluates() = runTest {
        val h = startedHarness(listOf(overnightRule())) {
            src.dayOfWeek = 4; src.nowSecondsOfDay = 23 * 3600 // engaged
        }
        assertEquals(DaltonizerMode.GRAYSCALE, h.display.daltonizer)
        h.coordinator.onScreenOff()
        h.src.nowSecondsOfDay = 7 * 3600 // window ends while the screen is off
        h.coordinator.onScreenOn()
        runCurrent()
        assertEquals(DaltonizerMode.OFF, h.display.daltonizer, "screen-on must re-evaluate the window")
        h.scope.cancel()
    }

    @Test
    fun batteryScopedRule_neverMatchesInV1_unknownSentinel() = runTest {
        // v1 runs no battery listener: the assembled percent stays the -1 unknown sentinel, so a
        // battery-scoped rule (model supported, editor not yet) must never match on garbage (D-108).
        val batteryRule = DisplayRule(
            id = "batt", name = "Low battery", action = DisplayAction.GRAYSCALE.name,
            triggers = ContextTriggers(battery = BatteryTrigger(max = 100)),
        )
        val h = startedHarness(listOf(batteryRule))
        assertEquals(0, h.events.size)
        assertEquals(DaltonizerMode.OFF, h.display.daltonizer)
        h.scope.cancel()
    }
}
