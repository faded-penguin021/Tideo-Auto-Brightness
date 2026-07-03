package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.DisplayRule
import com.tideo.autobrightness.app.settings.toSpec
import com.tideo.autobrightness.domain.display.DisplayAction
import com.tideo.autobrightness.domain.display.DisplayRulesResolver
import com.tideo.autobrightness.platform.display.DaltonizerMode
import com.tideo.autobrightness.platform.display.DisplayRestoreLatch
import com.tideo.autobrightness.platform.display.SecureDisplayController
import com.tideo.autobrightness.platform.privilege.Tier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runtime coordinator for the Privileged Display schedule rules (D-150, rebuild-only feature —
 * `plans/privileged-display.md` Segment 4): evaluates the enabled [DisplayRule]s against the
 * current signals via the pure [DisplayRulesResolver] and holds each matched action's secure
 * toggle ON for the duration of the match.
 *
 * Concurrency: runs its OWN coroutines in the service scope — NEVER inside the pipeline cycle.
 * The brightness pipeline's single-coroutine drop-on-reentry model is BINDING and this class
 * must not touch it; every evaluation serializes under [evalMutex] instead.
 *
 * Apply/restore contract (edge-triggered, D-150):
 *  - **Engage edge** (action newly desired): persist the pre-engage device state in the
 *    death-safe [DisplayRestoreLatch] (SharedPreferences `commit()`, D-134/D-144 pattern) FIRST,
 *    then write the toggle ON. Latch-before-write means a death between the two restores a
 *    no-op (the latched pre-state still equals the device).
 *  - **Hold**: while the action stays desired, nothing is re-asserted — a manual change made
 *    during a window sticks until the window ends (documented in the UI).
 *  - **Release edge** (action no longer desired): restore the latched pre-state, then clear the
 *    latch. A failed restore (revoked grant) KEEPS the latch — the obligation survives until a
 *    later evaluation, service stop, or startup sweep can write again.
 *  - **Startup residual sweep**: the first evaluation compares desired state against the latch,
 *    so a latch left by a dead process with no matching rule restores immediately; with the rule
 *    still matching it is adopted as engaged (original pre-state preserved).
 *  - **Service stop** ([stop]): every latched action is restored — a schedule must not outlive
 *    the runtime that maintains it.
 *  - **Inert below ELEVATED**: no writes and no latch churn (existing latches are kept as
 *    restore obligations for when the grant returns).
 *
 * Cost gates (ContextEngine pattern): the foreground-app poll runs ONLY while ≥1 enabled rule
 * uses apps AND the screen is on (a second 2.5 s poll may coexist with the context engine's —
 * accepted v1 cost, see the plan); time boundaries self-schedule via the shared
 * [millisUntilNextContextWake]; rule edits and screen-on re-evaluate immediately.
 */
class DisplayRulesCoordinator(
    private val rulesProvider: suspend () -> List<DisplayRule>,
    private val rulesFlow: Flow<List<DisplayRule>> = emptyFlow(),
    private val signalSource: ContextSignalSource,
    private val display: SecureDisplayController,
    private val restoreLatch: DisplayRestoreLatch,
    private val tierProvider: () -> Tier,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val evalMutex = Mutex()

    // Nearest future rule endpoint as "HH.MM" (DisplayResolution.nextBoundary — the same wake
    // format as %AAB_NextContextTime, so the shared scheduler helper applies).
    private val nextBoundary = MutableStateFlow<String?>(null)

    private var scope: CoroutineScope? = null
    private var appJob: Job? = null
    private var rulesJob: Job? = null
    private var timeJob: Job? = null

    // Same single-writer volatile pattern as ContextEngine.screenOn: written from the service's
    // lifecycle callbacks, read from the poll-start helper on the coordinator scope.
    @Volatile private var screenOn = true

    // Latest foreground package from the poll; "" until a rule-gated poll delivers one, so an
    // app-scoped rule cannot match before a real reading exists (the D-108 sentinel spirit).
    @Volatile private var latestApp = ""

    fun start(scope: CoroutineScope) {
        if (this.scope != null) return
        this.scope = scope
        scope.launch {
            // Seed evaluation doubles as the startup residual sweep: a latch persisted by a dead
            // process with no matching rule restores here; a still-matching one is adopted.
            evaluate()
            startAppPollIfNeeded()
        }
        // React to rule add/edit/delete at runtime: re-align the poll gate and re-resolve NOW
        // (the D-141 lesson — a user edit must apply immediately, not on the next signal change).
        rulesJob = scope.launch {
            rulesFlow.collect {
                refreshAppPoll()
                evaluate()
            }
        }
        // Self-scheduling time boundary (the ContextEngine prof764 pattern): wake exactly at the
        // nearest rule endpoint. A coroutine delay() can be deferred in deep Doze; screen-on
        // re-evaluation is the backstop, same as the context engine.
        timeJob = scope.launch {
            nextBoundary.collectLatest {
                while (true) {
                    val token = nextBoundary.value ?: break
                    val waitMs = millisUntilNextContextWake(token, clock())
                    if (waitMs < 0) break
                    // +1 s past the boundary (glue-review finding): the shared window match is
                    // end-INCLUSIVE (now <= end) and delay() typically lands within the boundary's
                    // second — an on-time wake at a window END would still match, hold, and re-arm
                    // for TOMORROW. The context engine survives the same geometry only because
                    // pipeline ticks re-evaluate seconds later; this coordinator has no tick, so
                    // it wakes just past the boundary instead. A 1 s-late edge is invisible here.
                    delay(waitMs + BOUNDARY_MARGIN_MS)
                    evaluate()
                }
            }
        }
    }

    /** Screen ON: resume the app poll (if rule-gated on) and re-evaluate time windows. */
    fun onScreenOn() {
        screenOn = true
        scope?.launch {
            startAppPollIfNeeded()
            evaluate()
        }
    }

    /** Screen OFF: app polling is pointless with the display off (ContextEngine parity — the
     *  last-seen app is kept, like the context engine's snapshot, until the poll resumes). */
    fun onScreenOff() {
        screenOn = false
        appJob?.cancel(); appJob = null
    }

    /**
     * Service stop: cancel the listeners, then restore every latched action so a schedule never
     * outlives the runtime. `scope = null` BEFORE the restore keeps a mutex-queued evaluation
     * from re-engaging after us, and taking [evalMutex] serializes an in-flight evaluation's
     * writes BEFORE the restore (the D-139 cancel-without-join bug class). Writes are a handful
     * of fast settings puts, so blocking the caller briefly is acceptable (D-134 precedent does
     * synchronous settings writes on the caller thread too).
     */
    fun stop() {
        if (scope == null) return
        appJob?.cancel(); appJob = null
        rulesJob?.cancel(); rulesJob = null
        timeJob?.cancel(); timeJob = null
        scope = null
        runBlocking {
            evalMutex.withLock {
                // release() no-ops when un-latched and keeps the latch when the write fails.
                DisplayAction.entries.forEach { release(it) }
            }
        }
    }

    /** Align the foreground-app poll with the current rule set (cost gate — apps poll only while
     *  ≥1 ENABLED rule uses apps). On gate-off, clear the snapshot too: with no poll running a
     *  kept package would go stale unobserved, and a later re-added app rule must only match a
     *  fresh reading (the D-142 stale-snapshot lesson). */
    private suspend fun refreshAppPoll() {
        if (rulesUseApps()) {
            startAppPollIfNeeded()
        } else {
            appJob?.cancel(); appJob = null
            latestApp = ""
        }
    }

    private suspend fun rulesUseApps(): Boolean =
        rulesProvider().any { it.enabled && !it.triggers.apps.isNullOrEmpty() }

    private suspend fun startAppPollIfNeeded() {
        if (appJob?.isActive == true) return
        if (!screenOn) return
        if (!rulesUseApps()) return
        appJob = scope?.launch {
            signalSource.foregroundAppFlow(APP_POLL_INTERVAL_MS).collect { pkg ->
                if (pkg != null && pkg != latestApp) {
                    latestApp = pkg
                    evaluate()
                }
            }
        }
    }

    private suspend fun evaluate() = evalMutex.withLock {
        if (scope == null) return@withLock // stopped: a queued evaluation must not re-engage
        val specs = rulesProvider().mapNotNull { it.toSpec() }
        // v1 listens to app + time only (the editor exposes apps/time/days): battery stays the
        // -1 unknown sentinel and wifi/location their defaults, so rules carrying those model
        // dimensions simply never match (D-108 semantics) instead of matching on garbage.
        val signals = signalSource.assemble(latestApp, -1, false, "", 0.0, 0.0)
        val resolution = DisplayRulesResolver.resolve(specs, signals)
        nextBoundary.value = resolution.nextBoundary
        if (tierProvider() < Tier.ELEVATED) return@withLock // inert: no writes, latches kept
        for (action in DisplayAction.entries) {
            val engaged = restoreLatch.preState(action.name) != null
            when {
                resolution.desired(action) == true && !engaged -> engage(action)
                resolution.desired(action) == null && engaged -> release(action)
                // desired && engaged → hold: no re-assert, manual changes stick until the edge.
            }
        }
    }

    private fun engage(action: DisplayAction) {
        val preState = when (action) {
            DisplayAction.GRAYSCALE -> display.readDaltonizer().name
            DisplayAction.NIGHT_LIGHT -> encode(display.readNightLight())
            DisplayAction.INVERSION -> encode(display.readInversion())
        }
        // Latch BEFORE the write (death-safety): dying between the two leaves a latch whose
        // pre-state still equals the device, so the startup sweep restores a harmless no-op.
        restoreLatch.save(action.name, preState)
        val result = when (action) {
            DisplayAction.GRAYSCALE -> display.setDaltonizer(DaltonizerMode.GRAYSCALE)
            DisplayAction.NIGHT_LIGHT -> display.setNightLight(true)
            DisplayAction.INVERSION -> display.setInversion(true)
        }
        // Failed write = not engaged: clear so a later evaluation retries instead of "holding"
        // an engagement that never happened.
        if (result.isFailure) restoreLatch.clear(action.name)
    }

    private fun release(action: DisplayAction) {
        val preState = restoreLatch.preState(action.name) ?: return
        val result = when (action) {
            DisplayAction.GRAYSCALE -> display.setDaltonizer(
                DaltonizerMode.entries.firstOrNull { it.name == preState } ?: DaltonizerMode.OFF,
            )
            DisplayAction.NIGHT_LIGHT -> display.setNightLight(decode(preState))
            DisplayAction.INVERSION -> display.setInversion(decode(preState))
        }
        // A failed restore keeps the latch: the restore obligation must survive a revoked grant.
        if (result.isSuccess) restoreLatch.clear(action.name)
    }

    private fun encode(on: Boolean): String = if (on) "1" else "0"
    private fun decode(preState: String): Boolean = preState == "1"

    private companion object {
        // Mirrors ContextEngine.APP_POLL_INTERVAL_MS; a second concurrent poll is the accepted
        // v1 cost (plan §Segment 4) — the two engines gate independently on their own rule sets.
        const val APP_POLL_INTERVAL_MS = 2500L

        // Wake this far past a time boundary so the end-inclusive window has actually ended by
        // the time the evaluation reads the clock (see the timeJob comment).
        const val BOUNDARY_MARGIN_MS = 1000L
    }
}
