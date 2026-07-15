package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.BatteryTrigger
import com.tideo.autobrightness.app.settings.ContextBaselineStore
import com.tideo.autobrightness.app.settings.ContextRule
import com.tideo.autobrightness.app.settings.ContextTriggers
import com.tideo.autobrightness.app.settings.DefaultProfiles
import com.tideo.autobrightness.app.settings.LocationTrigger
import com.tideo.autobrightness.domain.context.ContextSignals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContextEngineTest {

    private val baseline = AabSettings(serviceEnabled = true, maxBrightness = 255)

    private val videoStreamingRule = ContextRule(
        id = "vid",
        name = "Cinema",
        profile = "Video Streaming",
        priority = 10,
        triggers = ContextTriggers(apps = listOf("com.netflix.mediaclient")),
    )

    private val batterySaverRule = ContextRule(
        id = "bat",
        name = "Low Battery",
        profile = "Battery Saver",
        priority = 5,
        triggers = ContextTriggers(battery = BatteryTrigger(max = 20)),
    )

    private val catalog = object : ProfileCatalog {
        override suspend fun profile(name: String): AabSettings? = DefaultProfiles.all[name]
        override suspend fun names(): Set<String> = DefaultProfiles.all.keys
    }

    /** Fake source whose [assemble] returns its own mutable fields, so a test drives signals directly. */
    private class FakeSignalSource(
        var app: String = "",
        var batteryPercent: Int = 50,
        var plugged: Boolean = false,
        var wifi: String = "",
        var dayOfWeek: Int = 4,
        var nowSecondsOfDay: Int = 12 * 3600,
    ) : ContextSignalSource {
        val battery = MutableSharedFlow<BatterySignal>(extraBufferCapacity = 16)
        val wifi_ = MutableSharedFlow<String?>(extraBufferCapacity = 16)
        val appFlow = MutableSharedFlow<String?>(extraBufferCapacity = 16)
        val locations = MutableSharedFlow<LocationSignal>(extraBufferCapacity = 16)
        /** lat/lon the engine passed into [assemble] on the last evaluation (location-listener wiring). */
        var lastAssembledLat = 0.0
        var lastAssembledLon = 0.0
        override fun batteryFlow(): Flow<BatterySignal> = battery
        override fun wifiFlow(): Flow<String?> = wifi_
        override fun foregroundAppFlow(intervalMs: Long): Flow<String?> = appFlow
        override fun locationFlow(): Flow<LocationSignal> = locations
        override suspend fun assemble(
            app: String, batteryPercent: Int, plugged: Boolean, wifi: String, lat: Double, lon: Double,
        ): ContextSignals {
            lastAssembledLat = lat
            lastAssembledLon = lon
            return ContextSignals(
                app = this.app, lat = lat, lon = lon,
                batteryPercent = this.batteryPercent, plugged = this.plugged,
                wifi = this.wifi, dayOfWeek = dayOfWeek, nowSecondsOfDay = nowSecondsOfDay,
            )
        }
    }

    /** In-memory live settings store (D-170 write-through): the DataStore stand-in the engine
     *  reads AND writes; tests inspect/mutate [value] like the UI edits the real store. */
    private class FakeSettingsStore(var value: AabSettings) {
        val provider: suspend () -> AabSettings = { value }
        val writer: suspend (transform: (AabSettings) -> AabSettings) -> AabSettings = { transform ->
            value = transform(value)
            value
        }
    }

    /** In-memory pre-override baseline snapshot (task626 `_ContextResume`, D-170). */
    private class FakeBaselineStore(var stored: AabSettings? = null) : ContextBaselineStore {
        override suspend fun snapshot(): AabSettings? = stored
        override suspend fun save(baseline: AabSettings) { stored = baseline }
        override suspend fun clear() { stored = null }
    }

    private data class EngineHarness(
        val engine: ContextEngine,
        val scope: CoroutineScope,
        val settings: FakeSettingsStore,
        val baselineStore: FakeBaselineStore,
    )

    private fun TestScope.engine(
        rules: List<ContextRule>,
        signalSource: ContextSignalSource,
        baseline: AabSettings = this@ContextEngineTest.baseline,
        clock: () -> Long = { 0L },
        onChanged: () -> Unit = {},
        baselineStore: FakeBaselineStore = FakeBaselineStore(),
    ): EngineHarness {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val settings = FakeSettingsStore(baseline)
        val engine = ContextEngine(
            rulesProvider = { rules },
            settingsProvider = settings.provider,
            settingsWriter = settings.writer,
            baselineStore = baselineStore,
            profileCatalog = catalog,
            signalSource = signalSource,
            onProfileChanged = onChanged,
            clock = clock,
        )
        return EngineHarness(engine, scope, settings, baselineStore)
    }

    @Test
    fun appMatch_swapsEntireProfileAndFiresOnChanged() = runTest {
        var changes = 0
        val src = FakeSignalSource(app = "com.netflix.mediaclient")
        val (engine, scope) = engine(listOf(videoStreamingRule), src, onChanged = { changes++ })
        engine.start(scope)
        advanceUntilIdle()

        val eff = engine.effectiveSettings()
        assertEquals("Cinema", engine.activeContext.value)
        // The whole profile swaps: Video Streaming carries dimmingEnabled=true / threshold 20.
        assertEquals(true, eff.dimmingEnabled)
        assertEquals(20, eff.dimmingThreshold)
        // Service-level flags stay from the baseline (outside task626's 39-key snapshot).
        assertEquals(true, eff.serviceEnabled)
        assertTrue(changes >= 1, "profile change must trigger onProfileChanged")
        scope.cancel()
    }

    @Test
    fun reevaluate_withContextLock_dropsActiveContextAndRunsBaseline() = runTest {
        // G2R-F30: a manual profile load latches %AAB_ContextOverride=true. reevaluate() must drop any
        // active context and run the (now manually-chosen) baseline, so watchers stop overriding.
        val live = FakeSettingsStore(baseline)
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val engine = ContextEngine(
            rulesProvider = { listOf(videoStreamingRule) },
            settingsProvider = live.provider,
            settingsWriter = live.writer,
            baselineStore = FakeBaselineStore(),
            profileCatalog = catalog,
            signalSource = FakeSignalSource(app = "com.netflix.mediaclient"),
            onProfileChanged = {},
            clock = { 0L },
        )
        engine.start(scope)
        advanceUntilIdle()
        assertEquals("Cinema", engine.activeContext.value, "a rule is active before the lock")

        // Manual profile load: the live store becomes a chosen profile WITH the context lock latched.
        live.value = baseline.copy(contextOverride = true, minBrightness = 42)
        engine.reevaluate()
        advanceUntilIdle()

        assertNull(engine.activeContext.value, "the lock drops the active context")
        assertEquals(42, engine.effectiveSettings().minBrightness, "the manual baseline now runs")
        scope.cancel()
    }

    @Test
    fun noMatch_revertsToBaseline() = runTest {
        val src = FakeSignalSource(app = "com.other.app")
        val (engine, scope) = engine(listOf(videoStreamingRule), src)
        engine.start(scope)
        advanceUntilIdle()
        assertNull(engine.activeContext.value)
        assertEquals(baseline, engine.effectiveSettings())
        scope.cancel()
    }

    @Test
    fun batteryVeto_subFivePercentChangeDoesNotReEvaluate() = runTest {
        var now = 0L
        val src = FakeSignalSource(batteryPercent = 50)
        val (engine, scope) = engine(listOf(batterySaverRule), src, clock = { now })
        engine.start(scope)
        advanceUntilIdle()
        assertNull(engine.activeContext.value) // 50 > 20 → no match; lastBatt recorded = 50.

        // Past the 30s battery cooldown, drop to 48% (Δ2 < 5) → vetoed, no re-eval.
        now = 40_000L
        src.batteryPercent = 48
        src.battery.emit(BatterySignal(48, plugged = false))
        advanceUntilIdle()
        assertNull(engine.activeContext.value, "sub-5% battery change must be vetoed")

        // Drop to 15% (Δ35 ≥ 5) → re-evaluate; the battery rule wins.
        now = 80_000L
        src.batteryPercent = 15
        src.battery.emit(BatterySignal(15, plugged = false))
        advanceUntilIdle()
        assertEquals("Low Battery", engine.activeContext.value)
        scope.cancel()
    }

    @Test
    fun plugChange_bypassesBatteryCooldown_switchesToHigherPriorityChargingRule_D132() = runTest {
        // Owner report 2026-06-30: plugging in while "Low Battery" (P80) is active should IMMEDIATELY
        // switch to the higher-priority "Charging" (P81) rule. The plug event was being swallowed by the
        // 30s battery cooldown, so the switch lagged until the next un-vetoed battery tick (D-132). Both
        // rules are battery-only (equal specificity), so this also confirms priority — not specificity —
        // decides among matching rules.
        val charging = ContextRule(
            id = "chg", name = "Charging", profile = "Outdoors", priority = 81,
            triggers = ContextTriggers(battery = BatteryTrigger(onPower = true)),
        )
        val lowBattery = ContextRule(
            id = "low", name = "Low Battery", profile = "Battery Saver", priority = 80,
            triggers = ContextTriggers(battery = BatteryTrigger(max = 30)),
        )
        var now = 0L
        val src = FakeSignalSource(batteryPercent = 25, plugged = false)
        val (engine, scope) = engine(listOf(charging, lowBattery), src, clock = { now })
        engine.start(scope)
        advanceUntilIdle()
        assertEquals("Low Battery", engine.activeContext.value) // 25% unplugged → only Low Battery matches.

        // Plug in WITHIN the 30s cooldown (5s after the seed eval). The plug change must bypass the
        // cooldown and re-evaluate now — Charging (P81) matches and outranks Low Battery (P80).
        now = 5_000L
        src.plugged = true
        src.battery.emit(BatterySignal(25, plugged = true))
        advanceUntilIdle()
        assertEquals("Charging", engine.activeContext.value)
        scope.cancel()
    }

    /** Source that honours the battery percent the engine passes into [assemble] (the live snapshot),
     *  so the seed-evaluation "no reading yet" path can be exercised. */
    private class PassThroughBatterySource(
        var dayOfWeek: Int = 4,
        var nowSecondsOfDay: Int = 12 * 3600,
    ) : ContextSignalSource {
        val battery = MutableSharedFlow<BatterySignal>(extraBufferCapacity = 16)
        override fun batteryFlow(): Flow<BatterySignal> = battery
        override fun wifiFlow(): Flow<String?> = MutableSharedFlow()
        override fun foregroundAppFlow(intervalMs: Long): Flow<String?> = MutableSharedFlow()
        override fun locationFlow(): Flow<LocationSignal> = MutableSharedFlow()
        override suspend fun assemble(
            app: String, batteryPercent: Int, plugged: Boolean, wifi: String, lat: Double, lon: Double,
        ): ContextSignals = ContextSignals(
            app = app, lat = lat, lon = lon, batteryPercent = batteryPercent, plugged = plugged,
            wifi = wifi, dayOfWeek = dayOfWeek, nowSecondsOfDay = nowSecondsOfDay,
        )
    }

    @Test
    fun serviceStart_noBatteryReadingYet_doesNotFlashSaver_D108() = runTest {
        // D-108: at start() the seed GENERAL eval runs before the battery callbackFlow delivers its
        // first value. The snapshot reports -1 ("unknown"), so the max=20 saver rule must NOT match —
        // no Battery Saver flash. Once a real (high) reading arrives the engine stays on the baseline;
        // a real LOW reading then correctly applies the saver.
        var now = 0L
        val src = PassThroughBatterySource()
        val (engine, scope) = engine(listOf(batterySaverRule), src, clock = { now })
        engine.start(scope)
        advanceUntilIdle()
        assertNull(engine.activeContext.value, "seed eval with unknown battery must not match the saver")

        // First real reading: 80% (unplugged) → still no saver. (Past the 30s battery cooldown so the
        // BATTERY caller isn't debounced against the seed eval's lastEvalTime.)
        now = 35_000L
        src.battery.emit(BatterySignal(80, plugged = false))
        advanceUntilIdle()
        assertNull(engine.activeContext.value, "80% must not match a max=20 saver rule")

        // Battery later drains to 10% → the saver correctly engages.
        now = 70_000L
        src.battery.emit(BatterySignal(10, plugged = false))
        advanceUntilIdle()
        assertEquals("Low Battery", engine.activeContext.value, "a real low reading applies the saver")
        scope.cancel()
    }

    @Test
    fun serviceStart_realLowBatteryFirstReading_appliesSaverWithoutVeto_D108() = runTest {
        // The first real reading transitions lastBatt from the -1 sentinel; even a low value within
        // BATTERY_DELTA_THRESHOLD of -1 must not be vetoed (D-108).
        var now = 0L
        val src = PassThroughBatterySource()
        val (engine, scope) = engine(listOf(batterySaverRule), src, clock = { now })
        engine.start(scope)
        advanceUntilIdle()
        assertNull(engine.activeContext.value)

        now = 35_000L // past the 30s battery cooldown vs the seed eval
        src.battery.emit(BatterySignal(3, plugged = false)) // |3 - (-1)| = 4 < 5, must still evaluate
        advanceUntilIdle()
        assertEquals("Low Battery", engine.activeContext.value)
        scope.cancel()
    }

    @Test
    fun overrideActive_skipsProfileSwitch() = runTest {
        val lockedBaseline = baseline.copy(contextOverride = true)
        val src = FakeSignalSource(app = "com.netflix.mediaclient")
        val (engine, scope) = engine(listOf(videoStreamingRule), src, baseline = lockedBaseline)
        engine.start(scope)
        advanceUntilIdle()
        // %AAB_ContextOverride latched → no switch even though the app rule matches.
        assertNull(engine.activeContext.value)
        assertEquals(lockedBaseline, engine.effectiveSettings())
        scope.cancel()
    }

    @Test
    fun priorityWins_amongMultipleMatches() = runTest {
        // Both rules match (app + low battery); Video Streaming has the higher priority.
        val src = FakeSignalSource(app = "com.netflix.mediaclient", batteryPercent = 10)
        val (engine, scope) = engine(listOf(batterySaverRule, videoStreamingRule), src)
        engine.start(scope)
        advanceUntilIdle()
        assertEquals("Cinema", engine.activeContext.value)
        scope.cancel()
    }

    @Test
    fun reevaluate_picksUpFreshBaseline_withoutWatcherEval_G2RF11() = runTest {
        // G2R-F11/F12: a manual settings Apply edits the DataStore baseline but fires no context
        // signal. effectiveSettings() otherwise serves the cached snapshot (stale "stuck at 10");
        // reevaluate() must re-read the fresh baseline so the change takes effect immediately.
        val store = FakeSettingsStore(baseline.copy(minBrightness = 10))
        val src = FakeSignalSource(app = "com.other.app") // no rule matches → baseline path
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val engine = ContextEngine(
            rulesProvider = { listOf(videoStreamingRule) },
            settingsProvider = store.provider,
            settingsWriter = store.writer,
            baselineStore = FakeBaselineStore(),
            profileCatalog = catalog,
            signalSource = src,
            onProfileChanged = {},
            clock = { 0L },
        )
        engine.start(scope)
        advanceUntilIdle()
        assertEquals(10, engine.effectiveSettings().minBrightness)

        store.value = store.value.copy(minBrightness = 90)
        engine.reevaluate()
        assertEquals(90, engine.effectiveSettings().minBrightness, "reevaluate re-reads the fresh baseline")
        scope.cancel()
    }

    @Test
    fun contextLoad_firesLoadSink_G2RF25() = runTest {
        // G2R-F25: a runtime context-rule profile load notifies the load sink (→ user toast),
        // unconditionally (not gated on the debug selector). Fires only for a named rule win.
        val loads = mutableListOf<Pair<String, String>>()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val engine = ContextEngine(
            rulesProvider = { listOf(videoStreamingRule) },
            settingsProvider = { baseline },
            settingsWriter = { it(baseline) },
            baselineStore = FakeBaselineStore(),
            profileCatalog = catalog,
            signalSource = FakeSignalSource(app = "com.netflix.mediaclient"),
            onProfileChanged = {},
            clock = { 0L },
            contextLoadSink = { ctx, prof -> loads += ctx to prof },
        )
        engine.start(scope)
        advanceUntilIdle()
        assertEquals(listOf("Cinema" to "Video Streaming"), loads)
        scope.cancel()
    }

    @Test
    fun locationListener_debouncesSub100mMovesAndFiresOn100mMove_G2RF45() = runTest {
        // G2R-F45: the smart location listener feeds fixes; a ≥100 m move re-evaluates, a sub-100 m
        // nudge is debounced (so the context-location debug toasts aren't near-constant). The [LOC]
        // gate keeps the listener off when no rule uses location — here one does.
        var now = 0L
        val locRule = ContextRule(
            id = "loc", name = "AtHome", profile = "Battery Saver", priority = 10,
            triggers = ContextTriggers(location = LocationTrigger(lat = 10.0, lon = 10.0, radius = 200.0)),
        )
        val src = FakeSignalSource()
        val (engine, scope) = engine(listOf(locRule), src, clock = { now })
        engine.start(scope)
        advanceUntilIdle()
        assertNull(engine.activeContext.value)

        // First fix (far away) always fires; no match.
        now = 10_000L
        src.locations.emit(LocationSignal(20.0, 20.0))
        advanceUntilIdle()
        assertNull(engine.activeContext.value)
        assertEquals(20.0, src.lastAssembledLat, "first fix is evaluated")

        // A ~44 m nudge → debounced: no re-evaluation (assemble not re-run with the new coords).
        now = 30_000L
        src.locations.emit(LocationSignal(20.0004, 20.0))
        advanceUntilIdle()
        assertEquals(20.0, src.lastAssembledLat, "sub-100 m move must be debounced (no re-eval)")

        // Move onto the rule's location (>100 m) → re-evaluate → rule matches.
        now = 50_000L
        src.locations.emit(LocationSignal(10.0, 10.0))
        advanceUntilIdle()
        assertEquals("AtHome", engine.activeContext.value)
        scope.cancel()
    }

    @Test
    fun contextAutomationDebug_includesTriggerRuleAndPriority_G2RF47() = runTest {
        // G2R-F47: the Context Automation debug toast must name the trigger, context, profile and the
        // winning rule with its priority (not just "context → profile").
        val messages = mutableListOf<String>()
        val sink = DebugSink { category, activeLevel, message ->
            if (category == DebugCategory.CONTEXT_AUTOMATION && activeLevel == category.level) messages += message()
        }
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val debugBaseline = baseline.copy(debugLevel = DebugCategory.CONTEXT_AUTOMATION.level)
        val engine = ContextEngine(
            rulesProvider = { listOf(videoStreamingRule) },
            settingsProvider = { debugBaseline },
            settingsWriter = { it(debugBaseline) },
            baselineStore = FakeBaselineStore(),
            profileCatalog = catalog,
            signalSource = FakeSignalSource(app = "com.netflix.mediaclient"),
            onProfileChanged = {},
            clock = { 0L },
            debugSink = sink,
        )
        engine.start(scope)
        advanceUntilIdle()
        assertTrue(
            messages.any { it.contains("trigger") && it.contains("rule Cinema (priority 10)") },
            "expected enriched context-automation toast, got $messages",
        )
        scope.cancel()
    }

    @Test
    fun newAppRuleAtRuntime_startsForegroundPollAndApplies() = runTest {
        // Owner finding: creating an app context rule while the service is running did nothing — the
        // foreground-app poll only started at start()/screen-on, so a rule added later never triggered
        // (and its Context Automation debug flash never fired). The engine now reacts to the rule set.
        var now = 0L
        val rulesFlow = MutableStateFlow<List<ContextRule>>(emptyList())
        val src = FakeSignalSource()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val engine = ContextEngine(
            rulesProvider = { rulesFlow.value },
            rulesFlow = rulesFlow,
            // Static store: these tests exercise listener gating/vetoes, not profile writes (D-170).
            settingsProvider = { baseline },
            settingsWriter = { it(baseline) },
            baselineStore = FakeBaselineStore(),
            profileCatalog = catalog,
            signalSource = src,
            onProfileChanged = {},
            clock = { now },
        )
        engine.start(scope)
        advanceUntilIdle()

        // No app rule yet: the foreground poll isn't running, so opening the app does nothing.
        src.app = "com.netflix.mediaclient"
        src.appFlow.emit("com.netflix.mediaclient")
        advanceUntilIdle()
        assertNull(engine.activeContext.value, "no rule yet → no poll, no match")

        // Create the app rule at runtime (as the Contexts screen save would). A DIFFERENT app is in the
        // foreground, so the rule exists but does not yet match.
        now = 1_000L
        src.app = "com.other.app"
        rulesFlow.value = listOf(videoStreamingRule)
        advanceUntilIdle()
        assertNull(engine.activeContext.value, "rule created but its app isn't foreground")

        // Switch to the rule's app: the now-running poll detects it and applies the profile (without the
        // fix the emit has no collector → activeContext stays null).
        now = 2_000L
        src.app = "com.netflix.mediaclient"
        src.appFlow.emit("com.netflix.mediaclient")
        advanceUntilIdle()
        assertEquals("Cinema", engine.activeContext.value, "the newly-started poll resolves the app rule")
        scope.cancel()
    }

    private val wifiHomeRule = ContextRule(
        id = "wif",
        name = "Home Wifi",
        profile = "Battery Saver",
        priority = 7,
        triggers = ContextTriggers(wifi = listOf("HomeNet")),
    )

    @Test
    fun wifiListener_gatedOnWifiRules_D142() = runTest {
        // F-U2-2 / D-142: Tasker gates the wifi watcher on the `[WIFI]` cache token (prof768,
        // contexts_spec §1.1) — the SSID acquisition (Shizuku/root/dumpsys shell strategies) must not
        // run when no rule uses wifi. The engine previously collected ssidFlow() unconditionally,
        // unlike the [LOC]-gated location listener and the app-rule-gated foreground poll.
        val rulesFlow = MutableStateFlow<List<ContextRule>>(emptyList())
        val src = FakeSignalSource()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val engine = ContextEngine(
            rulesProvider = { rulesFlow.value },
            rulesFlow = rulesFlow,
            // Static store: these tests exercise listener gating/vetoes, not profile writes (D-170).
            settingsProvider = { baseline },
            settingsWriter = { it(baseline) },
            baselineStore = FakeBaselineStore(),
            profileCatalog = catalog,
            signalSource = src,
            onProfileChanged = {},
            clock = { 0L },
        )
        engine.start(scope)
        advanceUntilIdle()
        assertEquals(0, src.wifi_.subscriptionCount.value, "no wifi rule → ssidFlow must not be collected")

        rulesFlow.value = listOf(wifiHomeRule)
        advanceUntilIdle()
        assertEquals(1, src.wifi_.subscriptionCount.value, "a wifi rule starts the listener")

        rulesFlow.value = emptyList()
        advanceUntilIdle()
        assertEquals(0, src.wifi_.subscriptionCount.value, "deleting the last wifi rule stops the listener")
        scope.cancel()
    }

    /** Source whose [assemble] honours the app/wifi the engine passes in (the live snapshot), so
     *  the stale-snapshot clearing on listener stop can be exercised (D-142 / D-163). */
    private class PassThroughSource : ContextSignalSource {
        val wifi_ = MutableSharedFlow<String?>(extraBufferCapacity = 16)
        val appFlow = MutableSharedFlow<String?>(extraBufferCapacity = 16)
        override fun batteryFlow(): Flow<BatterySignal> = MutableSharedFlow()
        override fun wifiFlow(): Flow<String?> = wifi_
        override fun foregroundAppFlow(intervalMs: Long): Flow<String?> = appFlow
        override fun locationFlow(): Flow<LocationSignal> = MutableSharedFlow()
        override suspend fun assemble(
            app: String, batteryPercent: Int, plugged: Boolean, wifi: String, lat: Double, lon: Double,
        ): ContextSignals = ContextSignals(
            app = app, lat = lat, lon = lon, batteryPercent = batteryPercent, plugged = plugged,
            wifi = wifi, dayOfWeek = 4, nowSecondsOfDay = 12 * 3600,
        )
    }

    @Test
    fun wifiListenerStop_clearsStaleSsid_D142() = runTest {
        // F-U2-2 / D-142 companion: stopping the gated listener must clear the wifi snapshot —
        // otherwise deleting the last wifi rule and re-adding one later would match the STALE SSID
        // captured before the stop (the device may have left that network unobserved, since onLost
        // callbacks stopped with the listener).
        var now = 0L
        val rulesFlow = MutableStateFlow<List<ContextRule>>(listOf(wifiHomeRule))
        val src = PassThroughSource()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val engine = ContextEngine(
            rulesProvider = { rulesFlow.value },
            rulesFlow = rulesFlow,
            // Static store: these tests exercise listener gating/vetoes, not profile writes (D-170).
            settingsProvider = { baseline },
            settingsWriter = { it(baseline) },
            baselineStore = FakeBaselineStore(),
            profileCatalog = catalog,
            signalSource = src,
            onProfileChanged = {},
            clock = { now },
        )
        engine.start(scope)
        advanceUntilIdle()

        now = 10_000L
        src.wifi_.emit("HomeNet")
        advanceUntilIdle()
        assertEquals("Home Wifi", engine.activeContext.value, "connected SSID matches the rule")

        // Delete the only wifi rule: listener stops, baseline restored.
        now = 20_000L
        rulesFlow.value = emptyList()
        advanceUntilIdle()
        assertNull(engine.activeContext.value, "no rules → baseline")

        // Re-add the rule. The snapshot must NOT still hold the pre-stop "HomeNet" — the device may
        // have left that network while unobserved. Only a fresh emission may match.
        now = 30_000L
        rulesFlow.value = listOf(wifiHomeRule)
        advanceUntilIdle()
        assertNull(engine.activeContext.value, "stale pre-stop SSID must not match the re-added rule")

        now = 40_000L
        src.wifi_.emit("HomeNet")
        advanceUntilIdle()
        assertEquals("Home Wifi", engine.activeContext.value, "a fresh emission matches again")
        scope.cancel()
    }

    @Test
    fun locationListenerStop_clearsStaleFixAndDebounceAnchor_D163() = runTest {
        // D-163 (the D-142 asymmetric sibling): stopping the [LOC]-gated listener must clear the
        // location snapshot — otherwise deleting the last location rule and re-adding one later
        // (rulesFlow → evaluate(RESUME), which bypasses every PASS-2 veto) matches the STALE fix
        // captured before the stop, applying e.g. the Home profile at Work until a fresh fix lands.
        var now = 0L
        val locRule = ContextRule(
            id = "loc", name = "AtHome", profile = "Battery Saver", priority = 10,
            triggers = ContextTriggers(location = LocationTrigger(lat = 10.0, lon = 10.0, radius = 200.0)),
        )
        val rulesFlow = MutableStateFlow<List<ContextRule>>(listOf(locRule))
        val src = FakeSignalSource()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val engine = ContextEngine(
            rulesProvider = { rulesFlow.value },
            rulesFlow = rulesFlow,
            // Static store: these tests exercise listener gating/vetoes, not profile writes (D-170).
            settingsProvider = { baseline },
            settingsWriter = { it(baseline) },
            baselineStore = FakeBaselineStore(),
            profileCatalog = catalog,
            signalSource = src,
            onProfileChanged = {},
            clock = { now },
        )
        engine.start(scope)
        advanceUntilIdle()

        now = 10_000L
        src.locations.emit(LocationSignal(10.0, 10.0))
        advanceUntilIdle()
        assertEquals("AtHome", engine.activeContext.value, "a fix inside the radius matches the rule")

        // Delete the only location rule: listener stops, baseline restored.
        now = 20_000L
        rulesFlow.value = emptyList()
        advanceUntilIdle()
        assertNull(engine.activeContext.value, "no rules → baseline")
        assertEquals(0, src.locations.subscriptionCount.value, "deleting the last location rule stops the listener")

        // Re-add the rule. The snapshot must NOT still hold the pre-stop fix — the device may have
        // moved while unobserved (no listener → no updates). Only a fresh fix may match.
        now = 30_000L
        rulesFlow.value = listOf(locRule)
        advanceUntilIdle()
        assertNull(engine.activeContext.value, "stale pre-stop fix must not match the re-added rule")

        // A fresh fix inside the radius matches again. This also pins the debounce-anchor reset:
        // the fresh fix is <100 m from the stale anchor, so a kept lastLocEval* would swallow the
        // LOCATION evaluation entirely and leave the re-added rule unresolved.
        now = 40_000L
        src.locations.emit(LocationSignal(10.0, 10.0))
        advanceUntilIdle()
        assertEquals("AtHome", engine.activeContext.value, "a fresh fix matches again")
        scope.cancel()
    }

    @Test
    fun appPollStop_clearsStaleForegroundApp_D163() = runTest {
        // D-163 (the D-142 asymmetric sibling, app path): stopping the rule-gated foreground poll
        // must clear the app snapshot — otherwise deleting the last app rule and re-adding one later
        // matches the STALE package captured before the stop (the user may have left that app while
        // unobserved). NB the screen-off pause deliberately KEEPS the snapshot (context holds).
        var now = 0L
        val rulesFlow = MutableStateFlow<List<ContextRule>>(listOf(videoStreamingRule))
        val src = PassThroughSource()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val engine = ContextEngine(
            rulesProvider = { rulesFlow.value },
            rulesFlow = rulesFlow,
            // Static store: these tests exercise listener gating/vetoes, not profile writes (D-170).
            settingsProvider = { baseline },
            settingsWriter = { it(baseline) },
            baselineStore = FakeBaselineStore(),
            profileCatalog = catalog,
            signalSource = src,
            onProfileChanged = {},
            clock = { now },
        )
        engine.start(scope)
        advanceUntilIdle()

        now = 10_000L
        src.appFlow.emit("com.netflix.mediaclient")
        advanceUntilIdle()
        assertEquals("Cinema", engine.activeContext.value, "foreground app matches the rule")

        // Delete the only app rule: poll stops, baseline restored.
        now = 20_000L
        rulesFlow.value = emptyList()
        advanceUntilIdle()
        assertNull(engine.activeContext.value, "no rules → baseline")
        assertEquals(0, src.appFlow.subscriptionCount.value, "deleting the last app rule stops the poll")

        // Re-add the rule. The snapshot must NOT still hold the pre-stop package.
        now = 30_000L
        rulesFlow.value = listOf(videoStreamingRule)
        advanceUntilIdle()
        assertNull(engine.activeContext.value, "stale pre-stop foreground app must not match the re-added rule")

        // A fresh emission matches again.
        now = 40_000L
        src.appFlow.emit("com.netflix.mediaclient")
        advanceUntilIdle()
        assertEquals("Cinema", engine.activeContext.value, "a fresh emission matches again")
        scope.cancel()
    }

    @Test
    fun ruleEditWithinGeneralCooldown_appliesImmediately_D141() = runTest {
        // F-U2-1 / D-141: the rules-changed eval ran as GENERAL (500 ms PASS-1 cooldown on the shared
        // global lastEvalTime), so a rule add/edit/delete ≤500 ms after ANY evaluation was silently
        // vetoed — the new rule then didn't apply until the next signal change, defeating the
        // "applies immediately" intent. The rules-changed eval must run as RESUME (cooldown 0, no veto).
        var now = 0L
        val rulesFlow = MutableStateFlow<List<ContextRule>>(emptyList())
        val src = FakeSignalSource(batteryPercent = 10)
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val engine = ContextEngine(
            rulesProvider = { rulesFlow.value },
            rulesFlow = rulesFlow,
            // Static store: these tests exercise listener gating/vetoes, not profile writes (D-170).
            settingsProvider = { baseline },
            settingsWriter = { it(baseline) },
            baselineStore = FakeBaselineStore(),
            profileCatalog = catalog,
            signalSource = src,
            onProfileChanged = {},
            clock = { now },
        )
        engine.start(scope)
        advanceUntilIdle()
        assertNull(engine.activeContext.value, "no rules yet → baseline")

        // Create a rule that ALREADY matches (battery 10% ≤ max 20) only 300 ms after the seed eval —
        // inside the GENERAL cooldown window. It must apply immediately, not wait for a signal change.
        now = 300L
        rulesFlow.value = listOf(batterySaverRule)
        advanceUntilIdle()
        assertEquals("Low Battery", engine.activeContext.value, "rule created mid-cooldown applies immediately")
        scope.cancel()
    }

    // --- D-170: write-through profile application + persisted baseline snapshot (task626) ---

    @Test
    fun contextLoad_writesThroughAndSnapshotsBaseline_thenRevertRestores_D170() = runTest {
        var now = 0L
        val src = FakeSignalSource(batteryPercent = 10)
        val h = engine(listOf(batterySaverRule), src, clock = { now })
        h.engine.start(h.scope)
        advanceUntilIdle()
        assertEquals("Low Battery", h.engine.activeContext.value)
        // Write-through (Tasker LOAD_FILE parity): the LIVE store holds the loaded profile — the
        // settings screens read this store, so they show the loaded values, not the old baseline.
        val saver = DefaultProfiles.all.getValue("Battery Saver")
        assertEquals(mergeProfile(baseline, saver), h.settings.value, "the live store holds the loaded profile")
        // The pre-override settings were snapshotted (task626 _ContextResume).
        assertEquals(baseline, h.baselineStore.stored, "the baseline is snapshotted before the first override")

        // Battery recovers → no rule matches → the snapshot is restored into the live store and cleared.
        now = 40_000L
        src.batteryPercent = 90
        src.battery.emit(BatterySignal(90, plugged = false))
        advanceUntilIdle()
        assertNull(h.engine.activeContext.value)
        assertEquals(baseline, h.settings.value, "the revert restores the snapshotted baseline")
        assertNull(h.baselineStore.stored, "the snapshot is cleared after the revert")
        h.scope.cancel()
    }

    @Test
    fun midOverrideEdits_sameProfileEvalKeepsThem_revertDiscardsProfileKeysKeepsGlobals_D170() = runTest {
        var now = 0L
        val src = FakeSignalSource(batteryPercent = 10)
        val h = engine(listOf(batterySaverRule), src, clock = { now })
        h.engine.start(h.scope)
        advanceUntilIdle()
        assertEquals("Low Battery", h.engine.activeContext.value)

        // The user edits the LIVE (override) settings: a profile key and a global pref.
        h.settings.value = h.settings.value.copy(minBrightness = 77, debugLevel = 6)

        // The same rule re-resolves (act17: target equals the current active profile → nothing is
        // rewritten, so the edits survive the re-evaluation).
        now = 40_000L
        src.batteryPercent = 4
        src.battery.emit(BatterySignal(4, plugged = false))
        advanceUntilIdle()
        assertEquals(77, h.settings.value.minBrightness, "a same-profile re-evaluation must not stomp edits (act17)")

        // Battery recovers → revert: profile keys restore from the snapshot (Tasker parity —
        // mid-override edits to the task626 39-key set are discarded), while GLOBAL prefs keep
        // their current values (the G2-F8/G2R-F9 class; not part of the snapshot key set).
        now = 80_000L
        src.batteryPercent = 90
        src.battery.emit(BatterySignal(90, plugged = false))
        advanceUntilIdle()
        assertEquals(baseline.minBrightness, h.settings.value.minBrightness, "profile keys revert to the snapshot")
        assertEquals(6, h.settings.value.debugLevel, "global prefs keep their live values across the revert")
        h.scope.cancel()
    }

    @Test
    fun ruleToRuleSwitch_keepsOriginalBaselineSnapshot_D170() = runTest {
        var now = 0L
        val src = FakeSignalSource(batteryPercent = 10)
        val h = engine(listOf(batterySaverRule, videoStreamingRule), src, clock = { now })
        h.engine.start(h.scope)
        advanceUntilIdle()
        assertEquals("Low Battery", h.engine.activeContext.value)
        assertEquals(baseline, h.baselineStore.stored)

        // A higher-priority app rule takes over: the ORIGINAL snapshot must survive the switch.
        now = 40_000L
        src.app = "com.netflix.mediaclient"
        src.appFlow.emit("com.netflix.mediaclient")
        advanceUntilIdle()
        assertEquals("Cinema", h.engine.activeContext.value)
        assertEquals(baseline, h.baselineStore.stored, "a rule→rule switch keeps the pre-override snapshot")

        // Everything stops matching → revert to the ORIGINAL baseline, not to Battery Saver.
        now = 80_000L
        src.app = "com.other.app"
        src.batteryPercent = 90
        src.appFlow.emit("com.other.app")
        advanceUntilIdle()
        assertNull(h.engine.activeContext.value)
        assertEquals(baseline, h.settings.value, "the revert restores the original baseline")
        assertNull(h.baselineStore.stored)
        h.scope.cancel()
    }

    @Test
    fun staleSnapshotAtStart_isRestoredBySeedEval_D170() = runTest {
        // A process death mid-override (or between the revert's restore write and the snapshot
        // clear) leaves a lingering snapshot with the live store still holding the override. When
        // the rule no longer matches, the seed evaluation must restore + clear it — otherwise a
        // later override would skip its snapshot and a still-later revert would resurrect stale
        // settings over the user's.
        val src = FakeSignalSource(batteryPercent = 90) // no rule matches at start
        val stale = FakeBaselineStore(stored = baseline.copy(minBrightness = 3))
        val h = engine(
            listOf(batterySaverRule),
            src,
            baseline = baseline.copy(minBrightness = 200), // the dead session's override residue
            baselineStore = stale,
        )
        h.engine.start(h.scope)
        advanceUntilIdle()
        assertNull(h.engine.activeContext.value)
        assertEquals(3, h.settings.value.minBrightness, "the lingering snapshot is restored at the seed eval")
        assertNull(stale.stored, "and the snapshot is cleared")
        h.scope.cancel()
    }

    @Test
    fun staleSnapshotAfterLockResume_isHealedByUnchangedNoMatchEval_D170() = runTest {
        // The glue-review hole: after reevaluate()'s lock branch sets currentProfileName to the
        // user profile, the first no-match eval arrives with target == currentProfileName
        // (changed = false). A snapshot lingering from a death between a manual load's paired
        // writes must STILL be restored + cleared there — the restore is deliberately not gated
        // on `changed` (snapshot exists ⟺ override active).
        var now = 0L
        val src = FakeSignalSource(batteryPercent = 90) // never matches the saver rule
        val stale = FakeBaselineStore(stored = baseline.copy(minBrightness = 3))
        val h = engine(
            listOf(batterySaverRule),
            src,
            baseline = baseline.copy(contextOverride = true, minBrightness = 200),
            clock = { now },
            baselineStore = stale,
        )
        h.engine.start(h.scope)
        advanceUntilIdle()
        assertEquals(3, stale.stored?.minBrightness, "lock latched → seed eval skips the switch, snapshot survives")

        // The manual-load reapply path: reevaluate() under the latched lock sets currentProfileName
        // to the user profile. The screen-on resume then clears the lock, and the next no-match
        // eval resolves target == currentProfileName (changed = false).
        h.engine.reevaluate()
        h.settings.value = h.settings.value.copy(contextOverride = false)

        now = 40_000L
        src.batteryPercent = 80 // Δ ≥ 5 vs the seed reading so the BATTERY veto lets the eval run
        src.battery.emit(BatterySignal(80, plugged = false))
        advanceUntilIdle()
        assertEquals(3, h.settings.value.minBrightness, "the unchanged no-match eval restores the stale snapshot")
        assertNull(stale.stored, "and clears it")
        h.scope.cancel()
    }

    @Test
    fun mergeProfile_preservesDetectOverrides_G2F8() {
        // detectOverrides is a global reactivity preference, NOT a task626 snapshot key: a context
        // profile swap must keep the user's manual-override detection setting (G2-F8).
        val base = AabSettings(detectOverrides = true, minBrightness = 7)
        val profile = AabSettings(detectOverrides = false, minBrightness = 99)
        val merged = mergeProfile(base, profile)
        assertEquals(true, merged.detectOverrides, "detectOverrides comes from the baseline")
        assertEquals(99, merged.minBrightness, "curve/brightness params still come from the profile")
    }

    @Test
    fun mergeProfile_preservesDebugLevel_G2RF9() {
        // debugLevel is a GLOBAL preference (Live Debug scene), NOT a task626 snapshot key: a context
        // profile swap must keep the selected debug category (G2R-F9).
        val base = AabSettings(debugLevel = 4, minBrightness = 7)
        val profile = AabSettings(debugLevel = 0, minBrightness = 99)
        val merged = mergeProfile(base, profile)
        assertEquals(4, merged.debugLevel, "debugLevel comes from the baseline")
        assertEquals(99, merged.minBrightness, "curve/brightness params still come from the profile")
    }

    @Test
    fun mergeProfile_swapsDisplayToggleFields_D151() {
        // The D-151/D-152 display-toggle fields are PER-PROFILE screen state (the dimming-fields
        // precedent) — a context profile swap takes ALL of them from the loaded profile, not the
        // baseline.
        val base = AabSettings()
        val profile = AabSettings(
            nightLightEnabled = true,
            nightLightTemperature = 2_700,
            nightLightCircadianEnabled = true,
            daltonizerMode = "GRAYSCALE",
            inversionEnabled = true,
            alwaysOnDisplayEnabled = true,
            stayAwakeChargingEnabled = true,
            hdrForceSdrEnabled = true,
        )
        val merged = mergeProfile(base, profile)
        assertEquals(true, merged.nightLightEnabled, "nightLightEnabled comes from the profile")
        assertEquals(2_700, merged.nightLightTemperature, "nightLightTemperature comes from the profile")
        assertEquals(true, merged.nightLightCircadianEnabled, "nightLightCircadianEnabled comes from the profile (D-154)")
        assertEquals("GRAYSCALE", merged.daltonizerMode, "daltonizerMode comes from the profile")
        assertEquals(true, merged.inversionEnabled, "inversionEnabled comes from the profile")
        assertEquals(true, merged.alwaysOnDisplayEnabled, "alwaysOnDisplayEnabled comes from the profile")
        assertEquals(true, merged.stayAwakeChargingEnabled, "stayAwakeChargingEnabled comes from the profile")
        assertEquals(true, merged.hdrForceSdrEnabled, "hdrForceSdrEnabled comes from the profile")
    }
}
