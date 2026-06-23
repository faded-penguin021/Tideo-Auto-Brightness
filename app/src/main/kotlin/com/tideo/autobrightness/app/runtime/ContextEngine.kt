package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.ContextRule
import com.tideo.autobrightness.app.settings.ContextSignalTokens
import com.tideo.autobrightness.app.settings.toSpec
import com.tideo.autobrightness.domain.context.ContextOverrideResolver
import com.tideo.autobrightness.domain.context.ContextResolution
import com.tideo.autobrightness.domain.context.ContextSignals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import kotlin.math.abs

/**
 * Runtime context-override engine — the Kotlin rebuild of Tasker's 8 watcher profiles (prof762–768
 * + prof8) + task43 `_EvaluateContexts V2` orchestration (contexts_spec).
 *
 * The pure precedence/merge decision is delegated to the golden domain [ContextOverrideResolver];
 * this class owns the stateful glue the resolver brief leaves out:
 *  - PASS 1 per-caller cooldown debounce (contexts_spec §4 / task43 L31-57).
 *  - PASS 2 signal-change veto gates + `%AAB_ContextState` (task43 L183-248).
 *  - signal acquisition via the S7 readers (battery/wifi/foreground-app/location) + clock/solar.
 *  - applying the resolved override by swapping the **entire active profile** (contexts_spec §4 —
 *    NOT a scale/min/max tweak) and re-running Set Initial Brightness ([onProfileChanged]).
 *
 * Concurrency: every evaluation runs under [evalMutex] so the veto state and active-profile latch
 * stay consistent across the battery/wifi/app/time signal sources.
 */
class ContextEngine(
    private val rulesProvider: suspend () -> List<ContextRule>,
    private val baselineProvider: suspend () -> AabSettings,
    // Live rule set: the engine reacts to rule add/edit/delete so a newly-created app or location rule
    // starts its foreground/location listener immediately, instead of only at start()/screen-on (the
    // "a new app rule does nothing until you toggle the screen / reboot" bug). emptyFlow() = no live
    // updates (tests that pass a static rule list and rely on start()'s seed).
    private val rulesFlow: Flow<List<ContextRule>> = emptyFlow(),
    private val profileCatalog: ProfileCatalog,
    private val signalSource: ContextSignalSource,
    private val onProfileChanged: () -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val userProfileName: String = "Default",
    private val debugSink: DebugSink = NoOpDebugSink,
    private val contextLoadSink: ContextLoadSink = NoOpContextLoadSink,
) {
    private val _activeContext = MutableStateFlow<String?>(null)
    /** `%AAB_ActiveContext` — the winning rule's name, or null when running the user baseline. */
    val activeContext: StateFlow<String?> = _activeContext.asStateFlow()

    private val _nextContextTime = MutableStateFlow<String?>(null)
    /** `%AAB_NextContextTime` (HH.MM) — nearest future time endpoint, drives time re-eval scheduling. */
    val nextContextTime: StateFlow<String?> = _nextContextTime.asStateFlow()

    // Effective settings the pipeline consumes: the active profile merged over the baseline, or the
    // baseline itself when no override is active. Null until the first evaluation seeds it.
    private val _effective = MutableStateFlow<AabSettings?>(null)

    private val evalMutex = Mutex()

    // PASS 1: global last-eval clock (task43 %AAB_LastEvalTime). null until the first eval runs, so a
    // freshly started engine always evaluates once regardless of the cooldown window.
    private var lastEvalTime: Long? = null
    // PASS 2: previous signal snapshot (%AAB_ContextState: app#lat#lon#batt#plug#day#wifi).
    private var lastApp = ""
    private var lastBatt = -1
    private var lastPlug = -1
    private var lastDay = -1
    private var lastWifi = ""
    /** The profile currently applied (`%AAB_CurrentActiveProfile`). */
    private var currentProfileName: String? = null

    // Latest live signal pieces fed by the reader flows; assembled into a full snapshot per eval.
    // S12.9e: a single MutableStateFlow<SignalSnapshot> replaces six independent @Volatile fields so a
    // single battery/wifi/app/location update is published atomically and an evaluation reads one
    // untearable snapshot (battery percent + plug flag, or lat + lon, can no longer be read half-updated).
    private val signalSnapshot = MutableStateFlow(SignalSnapshot())
    // Last location that actually triggered a LOCATION evaluation — the ≥100 m debounce anchor (F45).
    private var lastLocEvalLat: Double? = null
    private var lastLocEvalLon: Double? = null

    private var scope: CoroutineScope? = null
    private var batteryJob: Job? = null
    private var wifiJob: Job? = null
    private var appJob: Job? = null
    private var locationJob: Job? = null
    private var rulesJob: Job? = null
    private var timeJob: Job? = null
    // The lone surviving @Volatile (S12.9e audit): written from the lifecycle callbacks onScreenOn/Off
    // and read from the listener-start helpers on the engine scope — a single boolean, single-writer
    // per transition, so a plain volatile read is sufficient (no compound invariant to protect).
    @Volatile private var screenOn = true

    /** Effective settings for the pipeline's settingsProvider: active profile override or baseline. */
    suspend fun effectiveSettings(): AabSettings = _effective.value ?: baselineProvider()

    /**
     * Re-derive the effective settings from the FRESH baseline now (settings Apply / profile load,
     * G2R-F11/F12). [effectiveSettings] otherwise returns the cached `_effective` snapshot from the
     * last context evaluation, so a manual DataStore edit (e.g. raising %AAB_MinBright) would not take
     * effect until the next watcher eval — leaving the runtime on stale settings ("stuck at 10").
     *
     * This keeps the currently-resolved active context/profile (it does NOT re-run the watcher
     * resolution, so it can't spuriously switch contexts) but re-reads the baseline and re-merges, so
     * a global/baseline edit flows through immediately while any active override stays in effect.
     * The service calls this before [BrightnessPipelineController.reapply].
     */
    suspend fun reevaluate() = evalMutex.withLock {
        val baseline = baselineProvider()
        // Manual context lock (%AAB_ContextOverride): a manual profile load latches it (G2R-F30,
        // D-014/D-038a). When set, the user's choice IS the baseline and all watcher overrides are
        // suppressed (the resolver already skips switching) — so drop any still-active context and run
        // the bare baseline. A "Resume" clears the latch and lets the next eval re-resolve contexts.
        if (baseline.contextOverride) {
            _activeContext.value = null
            currentProfileName = userProfileName
            _effective.value = baseline
            return@withLock
        }
        val active = currentProfileName
        _effective.value = if (_activeContext.value != null && active != null) {
            profileCatalog.profile(active)?.let { mergeProfile(baseline, it) } ?: baseline
        } else {
            baseline
        }
    }

    fun start(scope: CoroutineScope) {
        if (this.scope != null) return
        this.scope = scope
        batteryJob = scope.launch {
            signalSource.batteryFlow().collect { snap ->
                signalSnapshot.update { it.copy(batteryPercent = snap.percent, plugged = snap.plugged) }
                evaluate(ContextCaller.BATTERY)
            }
        }
        wifiJob = scope.launch {
            signalSource.wifiFlow().collect { ssid ->
                signalSnapshot.update { it.copy(wifi = ssid ?: "") }
                evaluate(ContextCaller.WIFI)
            }
        }
        scope.launch {
            // Seed the effective settings + active profile from a first general evaluation.
            evaluate(ContextCaller.GENERAL)
            startAppPollIfNeeded()
            startLocationListenerIfNeeded()
        }
        // React to rule add/edit/delete at runtime: (re)start or stop the foreground-app / location
        // listeners as the rule set gains or loses app/location triggers, then re-resolve so a rule
        // that already matches the current state applies immediately. Without this, a rule created
        // while the service is running never starts its poller until the next screen-on / reboot.
        rulesJob = scope.launch {
            rulesFlow.collect {
                refreshSignalListeners()
                evaluate(ContextCaller.GENERAL)
            }
        }
        // prof764 self-scheduling Time context (contexts_spec): wake EXACTLY at the next time boundary
        // and re-evaluate, instead of waiting for the next light sample. TYPE_LIGHT is an on-change
        // sensor, so in constant light (phone on a desk, screen off) no sample arrives and a 20:00 /
        // Sunset rule would otherwise fire late — only when the user next disturbs the sensor or wakes
        // the screen. `collectLatest` re-arms whenever the nearest boundary changes; the inner loop
        // re-arms a same-time daily recurrence. NB: a coroutine delay() can be deferred during deep Doze,
        // so onScreenOn()'s TIME eval and the 15-min MaintenanceWorker remain backstops for that case.
        timeJob = scope.launch {
            nextContextTime.collectLatest {
                while (true) {
                    val token = nextContextTime.value ?: break
                    val waitMs = millisUntilNextContextWake(token, clock())
                    if (waitMs < 0) break
                    delay(waitMs)
                    evaluate(ContextCaller.TIME)
                }
            }
        }
    }

    fun stop() {
        batteryJob?.cancel(); batteryJob = null
        wifiJob?.cancel(); wifiJob = null
        appJob?.cancel(); appJob = null
        locationJob?.cancel(); locationJob = null
        rulesJob?.cancel(); rulesJob = null
        timeJob?.cancel(); timeJob = null
        scope = null
    }

    /**
     * Align the foreground-app and location listeners with the current rule set: start a listener when
     * a rule now uses that signal, cancel it when no rule does (cost gate). Idempotent — the start
     * helpers no-op when already running, so a no-op rule edit costs nothing.
     */
    private suspend fun refreshSignalListeners() {
        val tokens = ContextSignalTokens.from(rulesProvider())
        if (tokens.usesApps) startAppPollIfNeeded() else { appJob?.cancel(); appJob = null }
        if (tokens.usesLocation) startLocationListenerIfNeeded() else { locationJob?.cancel(); locationJob = null }
    }

    /** Screen ON (prof761 reinit): resume foreground-app polling + re-evaluate time windows. */
    fun onScreenOn() {
        screenOn = true
        scope?.launch {
            startAppPollIfNeeded()
            startLocationListenerIfNeeded()
            evaluate(ContextCaller.TIME)
        }
    }

    /** Screen OFF (prof753 hibernate): app polling is pointless with the display off. */
    fun onScreenOff() {
        screenOn = false
        appJob?.cancel(); appJob = null
    }

    /** Called from the pipeline cycle: re-evaluate time-window rules (contexts_spec — prof764). */
    fun onPipelineTick() {
        scope?.launch { evaluate(ContextCaller.TIME) }
    }

    private suspend fun startAppPollIfNeeded() {
        if (appJob?.isActive == true) return
        if (!screenOn) return
        val tokens = ContextSignalTokens.from(rulesProvider())
        if (!tokens.usesApps) return // poll only when ≥1 app rule is configured (cost gate)
        appJob = scope?.launch {
            signalSource.foregroundAppFlow(APP_POLL_INTERVAL_MS).collect { pkg ->
                pkg?.let { app -> signalSnapshot.update { it.copy(app = app) } }
                evaluate(ContextCaller.APP_CHANGED)
            }
        }
    }

    /**
     * Start the "super smart location listener" (G2R-F45) only when a rule actually uses location —
     * Tasker gates the listener profile on `%AAB_ContextCache` containing `[LOC]` so the GPS/network
     * listener never runs without a location rule (battery cost gate). Hosted in the service scope so
     * it survives backgrounding; updates feed [latestLat]/[latestLon] and fire a LOCATION evaluation
     * only after a ≥100 m move (the input-blocking near-constant toast fix). Kept alive across
     * screen-off (a "near home" context should hold while the display is off).
     */
    private suspend fun startLocationListenerIfNeeded() {
        if (locationJob?.isActive == true) return
        val tokens = ContextSignalTokens.from(rulesProvider())
        if (!tokens.usesLocation) return // [LOC] gate — no GPS listener without a location rule
        locationJob = scope?.launch {
            signalSource.locationFlow().collect { loc ->
                signalSnapshot.update { it.copy(lat = loc.lat, lon = loc.lon) }
                val prevLat = lastLocEvalLat
                val prevLon = lastLocEvalLon
                val moved = prevLat == null || prevLon == null ||
                    haversineMeters(prevLat, prevLon, loc.lat, loc.lon) >= LOCATION_DEBOUNCE_M
                if (moved) {
                    lastLocEvalLat = loc.lat
                    lastLocEvalLon = loc.lon
                    evaluate(ContextCaller.LOCATION)
                }
            }
        }
    }

    private suspend fun evaluate(caller: ContextCaller) = evalMutex.withLock {
        val rules = rulesProvider()
        val tokens = ContextSignalTokens.from(rules)

        // PASS 1 — per-caller cooldown debounce.
        val cooldown = caller.cooldownMs
        val now = clock()
        val last = lastEvalTime
        if (cooldown > 0 && last != null && now - last < cooldown) return@withLock

        val snap = signalSnapshot.value
        val signals = signalSource.assemble(snap.app, snap.batteryPercent, snap.plugged, snap.wifi, snap.lat, snap.lon)

        // PASS 2 — veto gates.
        if (!shouldProceed(caller, signals, tokens)) return@withLock
        lastEvalTime = now
        recordState(signals)

        // PASS 3/4 — pure decision.
        val baseline = baselineProvider()
        // %AAB_Debug 9 "Context Location" (D-023, G2-F15): Flash the signals feeding this evaluation.
        debugSink.emit(DebugCategory.CONTEXT_LOCATION, baseline.debugLevel) {
            "app ${signals.app.ifEmpty { "—" }} · loc ${signals.lat},${signals.lon} · wifi ${signals.wifi.ifEmpty { "—" }}"
        }
        val knownProfiles = profileCatalog.names()
        val resolution = ContextOverrideResolver.resolve(
            rules = rules.map { it.toSpec() },
            signals = signals,
            overrideActive = baseline.contextOverride,
            userProfile = userProfileName,
            profileExists = { knownProfiles.contains(it) },
        )
        apply(resolution, baseline, rules, caller)
    }

    private fun shouldProceed(caller: ContextCaller, signals: ContextSignals, tokens: ContextSignalTokens): Boolean {
        val midnightRollover = signals.dayOfWeek != lastDay && lastDay != -1
        if (caller == ContextCaller.RESUME || midnightRollover) return true
        return when (caller) {
            ContextCaller.APP_CHANGED -> {
                val isRuleActive = _activeContext.value != null
                val isNonDefault = currentProfileName != null && currentProfileName != userProfileName
                val appInCache = tokens.appPackages.contains(signals.app)
                (isRuleActive || isNonDefault || appInCache) && signals.app != lastApp
            }
            ContextCaller.LOCATION -> tokens.usesLocation
            ContextCaller.BATTERY ->
                tokens.usesBattery && (signals.plugged != (lastPlug == 1) || abs(signals.batteryPercent - lastBatt) >= BATTERY_DELTA_THRESHOLD)
            ContextCaller.WIFI -> tokens.usesWifi && signals.wifi != lastWifi
            ContextCaller.TIME, ContextCaller.GENERAL, ContextCaller.RESUME -> true
        }
    }

    private fun recordState(signals: ContextSignals) {
        lastApp = signals.app
        lastBatt = signals.batteryPercent
        lastPlug = if (signals.plugged) 1 else 0
        lastDay = signals.dayOfWeek
        lastWifi = signals.wifi
    }

    private suspend fun apply(
        resolution: ContextResolution,
        baseline: AabSettings,
        rules: List<ContextRule>,
        caller: ContextCaller,
    ) {
        _nextContextTime.value = resolution.nextContextTime

        // Manual context lock (%AAB_ContextOverride): wake times refresh, profile switch skipped.
        val target = resolution.targetProfile ?: return

        val changed = target != currentProfileName
        currentProfileName = target
        _activeContext.value = resolution.activeContextName
        // Surface the context-selected profile on the Dashboard (manual loads set it via SettingsViewModel;
        // under the manual lock this code returned early above, so it never clobbers a manual choice).
        LiveRuntimeState.setActiveProfile(target)

        _effective.value = if (resolution.activeContextName != null) {
            // A rule won — swap the entire active profile (load-current-file, D-038(ii) simplification).
            profileCatalog.profile(target)?.let { mergeProfile(baseline, it) } ?: baseline
        } else {
            // No match → revert to the user baseline profile.
            baseline
        }

        // task43 act21: re-run Set Initial Brightness when the profile actually changed.
        if (changed) {
            // %AAB_Debug 8 "Context Automation" (D-023, G2-F15, enriched for G2R-F47): Flash the
            // trigger, context, profile, and winning rule (with its priority) on every auto-load.
            val winner = rules.firstOrNull { it.id == resolution.matchedRuleId }
            debugSink.emit(DebugCategory.CONTEXT_AUTOMATION, baseline.debugLevel) {
                buildString {
                    append("trigger ${caller.name.lowercase()}")
                    append(" · context ${resolution.activeContextName ?: "(none)"}")
                    append(" · profile $target")
                    winner?.let { append(" · rule ${it.name} (priority ${it.priority})") }
                }
            }
            // G2R-F25: confirm the load to the user (Tasker flashes when a context loads its profile).
            // Fires only when a named rule actually wins (not when reverting to the user baseline).
            resolution.activeContextName?.let { contextLoadSink.onContextLoaded(it, target) }
            onProfileChanged()
        }
    }

    /** Great-circle distance in metres for the ≥100 m location debounce (F45). */
    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        return EARTH_RADIUS_M * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    }

    private companion object {
        const val APP_POLL_INTERVAL_MS = 2500L
        const val BATTERY_DELTA_THRESHOLD = 5
        const val LOCATION_DEBOUNCE_M = 100.0
        const val EARTH_RADIUS_M = 6_371_000.0
    }
}

/**
 * Milliseconds from [now] until the next local-wall-clock occurrence of an `"HH.MM"` token (the
 * resolver's `%AAB_NextContextTime` format, "%02d.%02d"). If that instant has already passed today it
 * wraps to tomorrow (task43 L459-475 — a non-positive diff wraps a day). Returns -1 for an unparseable
 * token so the scheduler can break instead of spinning. Pure (only `Calendar` + the default timezone),
 * so it is unit-testable without coroutines.
 */
internal fun millisUntilNextContextWake(token: String, now: Long): Long {
    val dot = token.indexOf('.')
    if (dot <= 0) return -1
    val hour = token.substring(0, dot).toIntOrNull() ?: return -1
    val minute = token.substring(dot + 1).toIntOrNull() ?: return -1
    if (hour !in 0..23 || minute !in 0..59) return -1
    val cal = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_MONTH, 1)
    return cal.timeInMillis - now
}

/** Caller identity drives the PASS 1 cooldown + PASS 2 veto branch (task43 L31-57, L204-240). */
enum class ContextCaller(val cooldownMs: Long) {
    RESUME(0L),
    APP_CHANGED(500L),
    BATTERY(30_000L),
    LOCATION(8_000L),
    WIFI(8_000L),
    TIME(1_000L),
    GENERAL(500L),
}

/**
 * The latest live signal pieces fed by the reader flows (S12.9e): one atomically-published value so a
 * context evaluation reads an untearable snapshot instead of six independently-volatile fields.
 */
data class SignalSnapshot(
    val app: String = "",
    val batteryPercent: Int = 0,
    val plugged: Boolean = false,
    val wifi: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
)

/** A point-in-time battery reading (decoupled from the platform `BatteryState` so the engine is JVM-testable). */
data class BatterySignal(val percent: Int, val plugged: Boolean)

/** A point-in-time location fix (decoupled from the platform `LocationSnapshot`). */
data class LocationSignal(val lat: Double, val lon: Double)

/**
 * Platform signal acquisition for the context engine. The Android impl wires the S7 readers
 * (battery/wifi/foreground-app/location) and computes day-of-week + local seconds + solar times.
 */
interface ContextSignalSource {
    fun batteryFlow(): Flow<BatterySignal>
    fun wifiFlow(): Flow<String?>
    fun foregroundAppFlow(intervalMs: Long): Flow<String?>

    /** Continuous location fixes (G2R-F45); collected only when a rule uses location (the [LOC] gate). */
    fun locationFlow(): Flow<LocationSignal>

    /** Assemble a full snapshot from the latest cached signal pieces + current clock/location/solar. */
    suspend fun assemble(
        app: String,
        batteryPercent: Int,
        plugged: Boolean,
        wifi: String,
        lat: Double,
        lon: Double,
    ): ContextSignals
}

/**
 * Sink for the "context rule loaded its profile" notification (G2R-F25). The Android impl shows a
 * user-visible toast; kept as a fun interface so the engine stays JVM-testable. Distinct from
 * [DebugSink], which is gated on the debug-category selector — this fires unconditionally on a load.
 */
fun interface ContextLoadSink {
    fun onContextLoaded(contextName: String, profileName: String)
}

/** No-op load sink for tests / when no toast surface is wired. */
object NoOpContextLoadSink : ContextLoadSink {
    override fun onContextLoaded(contextName: String, profileName: String) = Unit
}

/** Resolves a profile NAME to its full [AabSettings]. S10 backs this with the built-in profiles. */
interface ProfileCatalog {
    suspend fun profile(name: String): AabSettings?
    suspend fun names(): Set<String>
}

/**
 * Overlay a context profile's parameter set onto the baseline. The fields swapped are exactly Tasker
 * task626 `_ContextResume`'s 39-key snapshot (the LOAD_FILE parameter set); fields outside it
 * (service enable, manual context lock, debug level, setup title, schema version, and
 * `detectOverrides`) are preserved from the baseline.
 *
 * `detectOverrides` (%AAB_DetectOverrides) is a GLOBAL reactivity preference, NOT one of task626's
 * curve/min-max/threshold/dimming snapshot keys (contexts_spec §4 enumerates the snapshot), so a
 * context profile swap must not silently turn manual-override detection off (G2-F8).
 *
 * S12.9c #1: these are exactly five of the seven [com.tideo.autobrightness.app.settings.GlobalPrefs]
 * fields. The full `copy(global = baseline.global)` is intentionally NOT used: GlobalPrefs also holds
 * `quickSettingsEnabled`/`notificationsEnabled`, which ARE in task626's per-profile snapshot and so
 * must come from the loaded profile, not the baseline.
 */
internal fun mergeProfile(baseline: AabSettings, profile: AabSettings): AabSettings = profile.copy(
    serviceEnabled = baseline.serviceEnabled,
    contextOverride = baseline.contextOverride,
    detectOverrides = baseline.detectOverrides,
    debugLevel = baseline.debugLevel,
    setupTitle = baseline.setupTitle,
    schemaVersion = baseline.schemaVersion,
)
