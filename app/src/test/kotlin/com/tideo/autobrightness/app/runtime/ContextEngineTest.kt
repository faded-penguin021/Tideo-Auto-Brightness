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

    /** Mutable test signal source. */
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

    /** D-170: in-memory settings store. */
    private class FakeSettingsStore(var value: AabSettings) {
        val provider: suspend () -> AabSettings = { value }
        val writer: suspend (transform: (AabSettings) -> AabSettings) -> AabSettings = { transform ->
            value = transform(value)
            value
        }
    }

    /** task626 _ContextResume snapshot (D-170) + %AAB_ProfileUser (DA-018). */
    private class FakeBaselineStore(
        var stored: AabSettings? = null,
        var name: String = "Default",
    ) : ContextBaselineStore {
        override suspend fun snapshot(): AabSettings? = stored
        override suspend fun save(baseline: AabSettings) { stored = baseline }
        override suspend fun clear() { stored = null }
        override suspend fun userProfileName(): String = name
        override suspend fun setUserProfileName(name: String) { this.name = name }
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
        assertEquals(true, eff.dimmingEnabled)
        assertEquals(20, eff.dimmingThreshold)
        assertEquals(true, eff.serviceEnabled)
        assertTrue(changes >= 1, "profile change must trigger onProfileChanged")
        scope.cancel()
    }

    @Test
    fun reevaluate_withContextLock_dropsActiveContextAndRunsBaseline() = runTest {
        // G2R-F30: manual load drops active context to run baseline.
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
        assertNull(engine.activeContext.value)

        now = 40_000L
        src.batteryPercent = 48
        src.battery.emit(BatterySignal(48, plugged = false))
        advanceUntilIdle()
        assertNull(engine.activeContext.value, "sub-5% battery change must be vetoed")

        now = 80_000L
        src.batteryPercent = 15
        src.battery.emit(BatterySignal(15, plugged = false))
        advanceUntilIdle()
        assertEquals("Low Battery", engine.activeContext.value)
        scope.cancel()
    }

    @Test
    fun plugChange_bypassesBatteryCooldown_switchesToHigherPriorityChargingRule_D132() = runTest {
        // D-132: plug transition bypasses battery cooldown, switches to higher priority.
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
        assertEquals("Low Battery", engine.activeContext.value)

        now = 5_000L
        src.plugged = true
        src.battery.emit(BatterySignal(25, plugged = true))
        advanceUntilIdle()
        assertEquals("Charging", engine.activeContext.value)
        scope.cancel()
    }

    /** Source that passes through battery percent from engine. */
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
        // D-108: unknown battery must not match a battery rule.
        var now = 0L
        val src = PassThroughBatterySource()
        val (engine, scope) = engine(listOf(batterySaverRule), src, clock = { now })
        engine.start(scope)
        advanceUntilIdle()
        assertNull(engine.activeContext.value, "seed eval with unknown battery must not match the saver")

        now = 35_000L
        src.battery.emit(BatterySignal(80, plugged = false))
        advanceUntilIdle()
        assertNull(engine.activeContext.value, "80% must not match a max=20 saver rule")

        now = 70_000L
        src.battery.emit(BatterySignal(10, plugged = false))
        advanceUntilIdle()
        assertEquals("Low Battery", engine.activeContext.value, "a real low reading applies the saver")
        scope.cancel()
    }

    @Test
    fun serviceStart_realLowBatteryFirstReading_appliesSaverWithoutVeto_D108() = runTest {
        // D-108: first real reading is not vetoed against sentinel.
        var now = 0L
        val src = PassThroughBatterySource()
        val (engine, scope) = engine(listOf(batterySaverRule), src, clock = { now })
        engine.start(scope)
        advanceUntilIdle()
        assertNull(engine.activeContext.value)

        now = 35_000L
        src.battery.emit(BatterySignal(3, plugged = false))
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
        assertNull(engine.activeContext.value)
        assertEquals(lockedBaseline, engine.effectiveSettings())
        scope.cancel()
    }

    @Test
    fun priorityWins_amongMultipleMatches() = runTest {
        val src = FakeSignalSource(app = "com.netflix.mediaclient", batteryPercent = 10)
        val (engine, scope) = engine(listOf(batterySaverRule, videoStreamingRule), src)
        engine.start(scope)
        advanceUntilIdle()
        assertEquals("Cinema", engine.activeContext.value)
        scope.cancel()
    }

    @Test
    fun reevaluate_picksUpFreshBaseline_withoutWatcherEval_G2RF11() = runTest {
        // G2R-F11: manual settings Apply must re-read baseline without signal.
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
        // G2R-F25: context-rule profile load fires load sink unconditionally.
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
        // G2R-F45: location debounces sub-100m moves, [LOC] gate prevents unnecessary polling.
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

        now = 10_000L
        src.locations.emit(LocationSignal(20.0, 20.0))
        advanceUntilIdle()
        assertNull(engine.activeContext.value)
        assertEquals(20.0, src.lastAssembledLat, "first fix is evaluated")

        now = 30_000L
        src.locations.emit(LocationSignal(20.0004, 20.0))
        advanceUntilIdle()
        assertEquals(20.0, src.lastAssembledLat, "sub-100 m move must be debounced (no re-eval)")

        now = 50_000L
        src.locations.emit(LocationSignal(10.0, 10.0))
        advanceUntilIdle()
        assertEquals("AtHome", engine.activeContext.value)
        scope.cancel()
    }

    @Test
    fun contextAutomationDebug_includesTriggerRuleAndPriority_G2RF47() = runTest {
        // G2R-F47: debug toast names trigger, context, profile, and priority.
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
        // Owner finding: new app rules must start foreground poll and apply immediately.
        var now = 0L
        val rulesFlow = MutableStateFlow<List<ContextRule>>(emptyList())
        val src = FakeSignalSource()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val engine = ContextEngine(
            rulesProvider = { rulesFlow.value },
            rulesFlow = rulesFlow,
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

        src.app = "com.netflix.mediaclient"
        src.appFlow.emit("com.netflix.mediaclient")
        advanceUntilIdle()
        assertNull(engine.activeContext.value, "no rule yet → no poll, no match")

        now = 1_000L
        src.app = "com.other.app"
        rulesFlow.value = listOf(videoStreamingRule)
        advanceUntilIdle()
        assertNull(engine.activeContext.value, "rule created but its app isn't foreground")

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
        // D-142: wifi SSID listener gated on [WIFI] cache token.
        val rulesFlow = MutableStateFlow<List<ContextRule>>(emptyList())
        val src = FakeSignalSource()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val engine = ContextEngine(
            rulesProvider = { rulesFlow.value },
            rulesFlow = rulesFlow,
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

    /** Source that passes through app/wifi from engine. */
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
        // D-142: listener stop clears stale wifi snapshot to prevent false matches.
        var now = 0L
        val rulesFlow = MutableStateFlow<List<ContextRule>>(listOf(wifiHomeRule))
        val src = PassThroughSource()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val engine = ContextEngine(
            rulesProvider = { rulesFlow.value },
            rulesFlow = rulesFlow,
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
        // D-163: listener stop clears stale location snapshot and debounce anchor.
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

        now = 30_000L
        rulesFlow.value = listOf(locRule)
        advanceUntilIdle()
        assertNull(engine.activeContext.value, "stale pre-stop fix must not match the re-added rule")

        now = 40_000L
        src.locations.emit(LocationSignal(10.0, 10.0))
        advanceUntilIdle()
        assertEquals("AtHome", engine.activeContext.value, "a fresh fix matches again")
        scope.cancel()
    }

    @Test
    fun appPollStop_clearsStaleForegroundApp_D163() = runTest {
        // D-163: poll stop clears stale app snapshot to prevent false matches.
        var now = 0L
        val rulesFlow = MutableStateFlow<List<ContextRule>>(listOf(videoStreamingRule))
        val src = PassThroughSource()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val engine = ContextEngine(
            rulesProvider = { rulesFlow.value },
            rulesFlow = rulesFlow,
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

        now = 30_000L
        rulesFlow.value = listOf(videoStreamingRule)
        advanceUntilIdle()
        assertNull(engine.activeContext.value, "stale pre-stop foreground app must not match the re-added rule")

        now = 40_000L
        src.appFlow.emit("com.netflix.mediaclient")
        advanceUntilIdle()
        assertEquals("Cinema", engine.activeContext.value, "a fresh emission matches again")
        scope.cancel()
    }

    @Test
    fun ruleEditWithinGeneralCooldown_appliesImmediately_D141() = runTest {
        // D-141: rule changes apply immediately, run as RESUME (cooldown 0).
        var now = 0L
        val rulesFlow = MutableStateFlow<List<ContextRule>>(emptyList())
        val src = FakeSignalSource(batteryPercent = 10)
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val engine = ContextEngine(
            rulesProvider = { rulesFlow.value },
            rulesFlow = rulesFlow,
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

        now = 300L
        rulesFlow.value = listOf(batterySaverRule)
        advanceUntilIdle()
        assertEquals("Low Battery", engine.activeContext.value, "rule created mid-cooldown applies immediately")
        scope.cancel()
    }

    // D-170: write-through profile + persisted baseline snapshot (task626)

    @Test
    fun contextLoad_writesThroughAndSnapshotsBaseline_thenRevertRestores_D170() = runTest {
        var now = 0L
        val src = FakeSignalSource(batteryPercent = 10)
        val h = engine(listOf(batterySaverRule), src, clock = { now })
        h.engine.start(h.scope)
        advanceUntilIdle()
        assertEquals("Low Battery", h.engine.activeContext.value)
        val saver = DefaultProfiles.all.getValue("Battery Saver")
        assertEquals(mergeProfile(baseline, saver), h.settings.value, "the live store holds the loaded profile")
        assertEquals(baseline, h.baselineStore.stored, "the baseline is snapshotted before the first override")

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

        now = 40_000L
        src.batteryPercent = 4
        src.battery.emit(BatterySignal(4, plugged = false))
        advanceUntilIdle()
        assertEquals(77, h.settings.value.minBrightness, "a same-profile re-evaluation must not stomp edits (act17)")

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

        now = 40_000L
        src.app = "com.netflix.mediaclient"
        src.appFlow.emit("com.netflix.mediaclient")
        advanceUntilIdle()
        assertEquals("Cinema", h.engine.activeContext.value)
        assertEquals(baseline, h.baselineStore.stored, "a rule→rule switch keeps the pre-override snapshot")

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
        // Process death mid-override leaves lingering snapshot; seed eval must restore + clear it.
        val src = FakeSignalSource(batteryPercent = 90)
        val stale = FakeBaselineStore(stored = baseline.copy(minBrightness = 3))
        val h = engine(
            listOf(batterySaverRule),
            src,
            baseline = baseline.copy(minBrightness = 200),
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
        // Stale snapshot from death between manual load writes must restore even when changed=false.
        var now = 0L
        val src = FakeSignalSource(batteryPercent = 90)
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

        h.engine.reevaluate()
        h.settings.value = h.settings.value.copy(contextOverride = false)

        now = 40_000L
        src.batteryPercent = 80
        src.battery.emit(BatterySignal(80, plugged = false))
        advanceUntilIdle()
        assertEquals(3, h.settings.value.minBrightness, "the unchanged no-match eval restores the stale snapshot")
        assertNull(stale.stored, "and clears it")
        h.scope.cancel()
    }

    // DA-018: Resume runs genuine evaluation + reverts to %AAB_ProfileUser

    @Test
    fun resumeContextAutomation_appliesCurrentlyMatchingRuleImmediately_DA018() = runTest {
        // DA-018: Resume must run genuine evaluation (task626 _ContextResume).
        val src = FakeSignalSource(app = "com.netflix.mediaclient")
        val h = engine(listOf(videoStreamingRule), src, baseline = baseline.copy(contextOverride = true))
        h.engine.start(h.scope)
        advanceUntilIdle()
        assertNull(h.engine.activeContext.value, "the manual lock suppresses the rule before Resume")

        h.settings.value = h.settings.value.copy(contextOverride = false)
        h.engine.resumeContextAutomation()
        advanceUntilIdle()

        assertEquals("Cinema", h.engine.activeContext.value, "Resume applies the matching rule immediately")
        assertEquals(true, h.engine.effectiveSettings().dimmingEnabled, "the rule's profile is written through (D-170)")
        h.scope.cancel()
    }

    @Test
    fun resumeContextAutomation_noMatch_revertsToUserProfileNotDefault_DA018() = runTest {
        // DA-018: Resume must revert to %AAB_ProfileUser, not hardcoded "Default".
        LiveRuntimeState.reset()
        val src = FakeSignalSource(app = "com.other.app")
        val h = engine(
            listOf(videoStreamingRule), src,
            baselineStore = FakeBaselineStore(name = "Outdoors"),
        )
        h.engine.start(h.scope)
        advanceUntilIdle()
        LiveRuntimeState.setActiveProfile("Movie Night")

        h.engine.resumeContextAutomation()
        advanceUntilIdle()

        assertNull(h.engine.activeContext.value, "no rule matches → no active context")
        assertEquals("Outdoors", LiveRuntimeState.activeProfile.value, "reverts to %AAB_ProfileUser, not Default")
        assertEquals(baseline, h.engine.effectiveSettings(), "no snapshot → the live store is unchanged")
        h.scope.cancel()
    }

    @Test
    fun mergeProfile_preservesDetectOverrides_G2F8() {
        // G2-F8: detectOverrides is global, not task626 snapshot key.
        val base = AabSettings(detectOverrides = true, minBrightness = 7)
        val profile = AabSettings(detectOverrides = false, minBrightness = 99)
        val merged = mergeProfile(base, profile)
        assertEquals(true, merged.detectOverrides, "detectOverrides comes from the baseline")
        assertEquals(99, merged.minBrightness, "curve/brightness params still come from the profile")
    }

    @Test
    fun mergeProfile_preservesDebugLevel_G2RF9() {
        // G2R-F9: debugLevel is global, not task626 snapshot key.
        val base = AabSettings(debugLevel = 4, minBrightness = 7)
        val profile = AabSettings(debugLevel = 0, minBrightness = 99)
        val merged = mergeProfile(base, profile)
        assertEquals(4, merged.debugLevel, "debugLevel comes from the baseline")
        assertEquals(99, merged.minBrightness, "curve/brightness params still come from the profile")
    }

    @Test
    fun mergeProfile_swapsDisplayToggleFields_D151() {
        // D-151/D-152: display-toggle fields are per-profile; swap takes all from loaded profile.
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
