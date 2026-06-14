package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.BatteryTrigger
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

    private fun TestScope.engine(
        rules: List<ContextRule>,
        signalSource: FakeSignalSource,
        baseline: AabSettings = this@ContextEngineTest.baseline,
        clock: () -> Long = { 0L },
        onChanged: () -> Unit = {},
    ): Pair<ContextEngine, CoroutineScope> {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val engine = ContextEngine(
            rulesProvider = { rules },
            baselineProvider = { baseline },
            profileCatalog = catalog,
            signalSource = signalSource,
            onProfileChanged = onChanged,
            clock = clock,
        )
        return engine to scope
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
        var live = baseline
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val engine = ContextEngine(
            rulesProvider = { listOf(videoStreamingRule) },
            baselineProvider = { live },
            profileCatalog = catalog,
            signalSource = FakeSignalSource(app = "com.netflix.mediaclient"),
            onProfileChanged = {},
            clock = { 0L },
        )
        engine.start(scope)
        advanceUntilIdle()
        assertEquals("Cinema", engine.activeContext.value, "a rule is active before the lock")

        // Manual profile load: baseline becomes a chosen profile WITH the context lock latched.
        live = baseline.copy(contextOverride = true, minBrightness = 42)
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
        var base = baseline.copy(minBrightness = 10)
        val src = FakeSignalSource(app = "com.other.app") // no rule matches → baseline path
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val engine = ContextEngine(
            rulesProvider = { listOf(videoStreamingRule) },
            baselineProvider = { base },
            profileCatalog = catalog,
            signalSource = src,
            onProfileChanged = {},
            clock = { 0L },
        )
        engine.start(scope)
        advanceUntilIdle()
        assertEquals(10, engine.effectiveSettings().minBrightness)

        base = base.copy(minBrightness = 90)
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
            baselineProvider = { baseline },
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
        val engine = ContextEngine(
            rulesProvider = { listOf(videoStreamingRule) },
            baselineProvider = { baseline.copy(debugLevel = DebugCategory.CONTEXT_AUTOMATION.level) },
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
}
