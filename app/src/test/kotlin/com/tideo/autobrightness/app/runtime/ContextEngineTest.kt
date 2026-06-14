package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.BatteryTrigger
import com.tideo.autobrightness.app.settings.ContextRule
import com.tideo.autobrightness.app.settings.ContextTriggers
import com.tideo.autobrightness.app.settings.DefaultProfiles
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
        override fun batteryFlow(): Flow<BatterySignal> = battery
        override fun wifiFlow(): Flow<String?> = wifi_
        override fun foregroundAppFlow(intervalMs: Long): Flow<String?> = appFlow
        override suspend fun assemble(app: String, batteryPercent: Int, plugged: Boolean, wifi: String) =
            ContextSignals(
                app = this.app, batteryPercent = this.batteryPercent, plugged = this.plugged,
                wifi = this.wifi, dayOfWeek = dayOfWeek, nowSecondsOfDay = nowSecondsOfDay,
            )
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
    fun mergeProfile_preservesDetectOverrides_G2F8() {
        // detectOverrides is a global reactivity preference, NOT a task626 snapshot key: a context
        // profile swap must keep the user's manual-override detection setting (G2-F8).
        val base = AabSettings(detectOverrides = true, minBrightness = 7)
        val profile = AabSettings(detectOverrides = false, minBrightness = 99)
        val merged = mergeProfile(base, profile)
        assertEquals(true, merged.detectOverrides, "detectOverrides comes from the baseline")
        assertEquals(99, merged.minBrightness, "curve/brightness params still come from the profile")
    }
}
