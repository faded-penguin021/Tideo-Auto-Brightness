# Runtime resource and callback lifetime audit

This static audit traces callback sources, pollers, jobs, coroutines, Binder calls, process spawns,
notifications and widgets. “Owner” is the object whose cancellation ends the work. Platform callback
rates are upper requests: Android and hardware may deliver less often. Code is the ground truth.

## Result

No permanently leaked sensor, receiver, network, location, content-observer, Shizuku-service, or
child-process registration was found. Every long-lived registration is inside a `callbackFlow` and
has an `awaitClose` teardown, or belongs to the foreground-service scope and is stopped in service
teardown. Shell output and lifetime are bounded. The one actionable availability gap was the exported
automation receiver: every broadcast created an independent `goAsync` coroutine and `PendingResult`.
DataStore serialized each transaction but not the whole command, so a co-installed app could build an
unbounded backlog and race subsequent service/widget effects. DA-039 now admits one whole external
command process-wide and drops overlap. A wall-clock quota is not needed for memory safety and would
break legitimate rapid automation; serialized admission is the narrow required bound.

## Callback and loop matrix

| Source | Enable gate | Frequency / bound | Cancellation owner | Exception policy | Cleanup path |
|---|---|---|---|---|---|
| Light sensor | running opted-in FGS | `SENSOR_DELAY_NORMAL`; pipeline throttle and drop-on-reentry bound expensive cycles | `BrightnessPipelineController` service job | missing sensor closes; `trySend` is non-blocking | flow cancellation unregisters listener |
| Proximity sensor | running FGS | `SENSOR_DELAY_NORMAL`; state update only | controller service scope | missing sensor closes; send failure is ignored | `awaitClose` unregisters |
| Panic accelerometer + linear acceleration | running FGS | `SENSOR_DELAY_GAME` (~50 Hz); one 10 s shake window per inversion | panic collector in service scope | missing accelerometer closes; callbacks do bounded arithmetic | unregister both sensors and screen receiver in `awaitClose` |
| Brightness `ContentObserver` | running controller | OS changes only; self-write echo suppression; non-blocking channel | controller service job | invalid reads ignored; provider exception cancels supervised child | `awaitClose` unregisters observer |
| Battery receiver | running context engine | sticky seed plus `BATTERY_CHANGED` broadcasts | `ContextEngine.batteryJob` | malformed values become sentinels; collector failure is service-supervised | `awaitClose` unregisters receiver |
| Wi-Fi `NetworkCallback` | at least one `[WIFI]` rule | network capability/loss callbacks; confirmed network skips repeat resolution; each resolution has Shizuku 4 s and shell 15 s bounds | `ContextEngine.wifiJob` / callbackFlow scope | strategies fail to `null`; stale completions are discarded | callback cancellation unregisters; Shizuku calls unbind; processes timeout/destroy |
| Foreground-app polling | app rule exists **and** screen is on | 2 s; each UsageEvents scan is a finite 3 s window | `ContextEngine.appJob` | platform exception cancels only the supervised service child | cancel on screen-off/rule removal/service stop |
| Continuous location | location rule exists | OS request: ≥30 s and ≥50 m; context evaluation additionally requires ≥100 m | `ContextEngine.locationJob` | permission/provider failures close or are ignored | `awaitClose` removes listener; cancellation clears cached fix/anchor on rule removal |
| One-shot location | explicit UI request | 5 s current-fix / 20 s active-fix timeout | calling UI coroutine | permission and provider failures become typed unavailable results | cancellation signal or listener removal; successful active fix removes listener |
| Context time scheduler | running FGS with a next boundary | sleeps until nearest boundary; daily loop, plus screen-on/15 min backstops | `ContextEngine.timeJob` | cancellation propagates; evaluation serialized by `evalMutex` | service/rule lifecycle cancels job |
| Display-temperature ticker | running FGS | delay-first; configured 15 min; only-on-change Settings write | `DisplayTogglesCoordinator.job` | child failure is isolated by service supervisor | stop cancels job, serializes behind mutex, restores baseline |
| Staleness ticker | only while a lifecycle-aware UI collector exists | 1 s, `distinctUntilChanged` | Compose/lifecycle collector | cancellation propagates | cold flow ends with collector |
| Maintenance WorkManager job | unique periodic work; worker rechecks `serviceEnabled` | platform minimum 15 min, unique `UPDATE`; no network requirement | WorkManager | an unexpected read/write exception fails the attempt; no internal retry loop | coroutine worker cancellation; finite read/start/heartbeat |
| FGS start/restart | explicit persisted opt-in; sticky null start rereads opt-in and fails closed | commands only plus 15 min maintenance; `ensureRunning` idempotent | Android service + its `SupervisorJob` | background-start denial is caught and health-marked; child failures supervised | `onDestroy` cancels scope, collectors, receivers, sensors and restores display state |
| Shizuku shell bind | only Wi-Fi rule resolution or opted-in force-dark | 4 s bind timeout; allowlisted Binder operation; service command 10 s | calling coroutine | Binder/permission errors return null | cancellation/finally unbind with `remove=true`; user service `destroy()` exits process |
| Shizuku grant bind | explicit user grant action | 4 s timer; one Binder transaction | gateway completion latch | failure returned exactly once | permission listener removed; every completion/timeout disconnect unbinds |
| Root/DUMP/Shizuku process | gated privileged Wi-Fi/force-dark/grant operation | stdout/stderr caps; 10–15 s timeout | synchronous operation thread/coroutine | nonzero/overflow/error returns null | streams close; timeout forcibly destroys; reader threads join |
| Broadcast `goAsync` | boot, widget toggle, opted-in external control | finite DataStore/service work; external control is now single-flight | process-wide supervised scope + platform `PendingResult` | release logging only omits throwable; `finally` always finishes | pending result `finish()` in helper; DA-039 releases admission in nested `finally` |
| DataStore mutations | UI/internal command gates; external control opt-in | DataStore serializes transactions; no retry/poll loop | caller coroutine / process | failures propagate to owner; cancellation is not converted to success | transaction completes or caller cancels; no held registration |
| External automation broadcasts | separate opt-in defaults off | one command in flight process-wide; overlap dropped; no queued backlog | `ControlReceiver` atomic admission + `goAsync` completion | gate/read/route failure releases admission and finishes broadcast | `finally` releases atomic and helper finishes pending result |
| Ongoing notification | running FGS | initial foreground post, then changed `NotificationModel` only; manual override alerts once per rising edge | service notification collector | notification failure cancels supervised child, not pipeline | service stop removes foreground notification |
| Widget refresh | widget exists and system/update/state-changing event occurs | `updatePeriodMillis=0`; changed pipeline model only; no-widget fast path avoids DataStore | process scope for finite repaint | read failure renders disabled; manager failures are bounded/no-op | coroutine ends; no alarm/listener registration |

## Adversarial exported-control case

**Threat:** after the user opts in, a co-installed unprivileged app explicitly broadcasts control
verbs as fast as Binder permits. Opt-in is a feature gate, not caller authentication. The attacker
cannot inject settings keys, profile bodies, shell arguments, URIs, or privileges, but can request
DataStore reads/writes, FGS commands, notification/widget work, profile application, and panic.

Before DA-039, each delivery acquired a `PendingResult` and launched a process-scope coroutine.
DataStore's own mutex serialized only store updates, allowing arbitrary waiting coroutines and
out-of-order post-transaction side effects. Therefore **serialized command admission is needed**.
The implementation uses a process-wide atomic single-flight gate and deliberately drops overlap
rather than creating another queue. This bounds memory/PendingResults and matches the runtime's
existing drop-on-reentry philosophy. Explicit `SERVICE_ON`/`SERVICE_OFF` provide retry-safe
convergence; `TOGGLE` remains intentionally non-idempotent.

A time-based per-UID or global quota is **not required now**: Android broadcasts do not provide a
stable authenticated automation identity here, sequential calls are finite, and a quota would make
valid multi-step automations timing-dependent. If field evidence shows sequential battery/UX abuse,
the next product decision is a documented global token bucket or selected-caller capability—not an
unbounded serialized queue and not reliance on DataStore contention.

## Residual limits

- Android may kill the process without lifecycle callbacks; OS process death releases registrations,
  while display-setting restoration has the separate limits recorded in the display-safety audit.
- Cancellation after a blocking Binder transaction starts cannot interrupt that transaction, but its
  allowlisted user-service command has its own 10 s process bound and the app unbinds afterwards.
- Wi-Fi capability churn can start overlapping resolution coroutines before older ones finish. Each is
  independently lifetime/output bounded and stale-safe, but cancellation/coalescing would further save
  energy if device traces show callback storms; it is not currently an unbounded lifetime leak.
- WorkManager's 15-minute cadence is an OS minimum, not an exact clock; Doze may delay it.
