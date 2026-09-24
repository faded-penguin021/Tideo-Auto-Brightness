# Plan — light-tracking stalls and the misleading monitoring surfaces (Tideo #130, #132)

> Execution plan (RUNBOOK playbook 5, multi-session). The owner approved **persisting** it
> (2026-09-23) and answered both of its parity questions the same day (§4). The segment checklist
> is mirrored in `docs/STATE.md` → `## Active work`. The plan is provisional: delete it at R8, once
> its durable content lives in STATE Changelog lines and ledger rows. No ledger row and no code
> comment may cite this path.
>
> Analysis by Opus. Two gpt-6-astra review passes (2026-09-23) are folded in, with Astra's
> corrections applied in place and §3 listing the claims they withdrew. F-E comes from an owner
> lead. A gpt-5.6-sol review (2026-09-23) is folded in the same way: F-E item 4, R2, R5, R6 and R7
> were corrected, and its withdrawn claims joined §3. The owner's OnePlus 13 observations
> (2026-09-23, §1) extended F-A's start-command sources and added the intermittent wake case, H2.
> A third Astra pass (2026-09-23) added R5's first-run branch, R6's structural no-ghosts tests,
> R7's supersession contract, and a §5 gate that bases attribution on R3's records.
> **Rescoped by the owner (2026-09-23, §5 Scope).** This train ships the notification fix,
> diagnostics and defects that a test reproduces. The new input and settling policies (RF, R6, R7)
> are deferred until a diagnostic trace names the failing path.
> **Re-scoped again by the owner (2026-09-24, §5 Scope)** after four findings were re-verified
> against `24a02f0`: **R6 joins the train**, R7 stays deferred, and **RF is dropped**, because
> AOSP reports every light reading as high accuracy, which rules out F-F's mechanism (F-F).

## 1. Reports and scope

| Report | Device | Symptom |
|---|---|---|
| Tideo #132 (LordSithek) | Pixel 9 Pro (caiman), crDroid, Android 16, 1.10.0, ELEVATED via root; battery optimisation off, locked in memory | Brightness occasionally stops following light (dark↔bright) until the service is toggled off/on. Two Live Debug captures with 48 s and 82 s logcats. |
| Tideo #130 (secondary symptom) | OPPO Find X9 Ultra, ColorOS, 1.10.0 | Notification "repeatedly falls back to *Monitoring ambient light*". Toggling the tile restores it; tile and notification disagree. |
| Owner | OnePlus 13, OxygenOS | Saw the same notification text at night. Has not seen Tideo go stale on this phone. |
| Owner, 2026-09-23 | same phone, 0 lx, "Trust low-accuracy sensor" off | (1) After a screen off/on, the notification reads "Monitoring ambient light". (2) With the setting on, a screen off/on gives `0 lx → 0`. (3) Turning it off, with no screen cycle, **instantly** restores "Monitoring"; turning it on again, still with no screen cycle, does not clear it. (4) Later, several screen cycles with the setting **off** gave `0 lx → 0`. (5) A wake after at least 2 min with the screen off, setting off, showed `0 lx → 0` at once. (6) Screen left on in the dark with no interaction: after a while the notification fell back to "Monitoring". Live Debug right after showed smoothed and raw 0.0, band 0.0–0.1, target 0, hardware 15 (requested → acknowledged 15 → 15), active rule "Phone in bed", last update (last completed cycle) 7 min ago, last sample "just now". |

**Reading of the 2026-09-23 observations (revised 2026-09-24).** The trust setting cannot change
what passes on an AOSP framework (F-F), so it plays no part in any of them.
- (3) is F-A, triggered by the settings save. It is the only deterministic one.
- (2), (4) and (5) are ordinary successful wakes. (2) tells us nothing about trust.
- (6) is F-A through the worker (F-A below). The 0 lx samples arriving while the notification was
  wrong were refused by F-B's zero trap, since accuracy cannot refuse them.
- (1) is **intermittent**. After a wake nothing gates the first event except accuracy, which
  always passes, so a wake that ends on "Monitoring" is H2 (no first event arrived) or a start
  command landing after the wake's cycle (F-A). Nothing observed so far separates the two.

The observations do not say whether the brightness itself moved. After an H2 wake the code writes
nothing, which is hard to notice in the dark.

**Not every device is affected.** No mechanism in §2 needs a faulty device, but each one's
exposure depends on the sensor's event cadence. A sensor that emits frequent small changes (jitter)
keeps re-triggering cycles and hides both mechanisms. A strictly on-change sensor, such as the
Pixel's under-display TMD3733 on its always-on-compute chip, exposes them. **This is a hypothesis:
no event rates have been measured on either phone.**

**Code version.** All paths and lines are at `64be441`. The cited runtime files are byte-identical to
**v1.10.0** (`411e26a`), checked with `git diff --stat` over `app/.../runtime/`, `MainActivity.kt`
and `platform/.../sensor/`, so they describe the reporter's build. On 2026-09-24 the four findings'
line references were re-checked at `24a02f0`: `git diff --stat 64be441 24a02f0` over the three
modules' `src/main` is empty.

## 2. Findings

### F-A — The foreground notification is overwritten with an empty model. Confidence: high

- `AmbientMonitoringService.onStartCommand` always calls `startForeground(…,
  buildNotification(NotificationModel()), …)` (`:143`). An empty model renders "Monitoring ambient
  light" (`:526`).
- The live updater is `.distinctUntilChanged()` (`:281`). It keeps its previous model and does not
  re-post that same model after `startForeground` has replaced the visible notification. **In steady
  light the visible notification stays wrong indefinitely**, and even a fresh sample fixes it only if
  it changes a model field.
- Start commands reach a service that is already running from many places, since every
  `AutoBrightnessRuntime.sendServiceAction` is a `startForegroundService`:
  - `MainActivity.onCreate` → `AutoBrightnessRuntime.bootstrap()` → `startMonitoring()`;
  - `MaintenanceWorker`, every 15 min;
  - **every settings save**, through `AutoBrightnessRuntime.reapply()` → `ACTION_REAPPLY`:
    `DraftSettingsViewModel.kt:155`, `SettingsViewModel.kt:83,116`, `ProfileApplier.kt:35`,
    `CircadianExtrasViewModel.kt:101`, `LiveDebugViewModel.kt:81`;
  - the widget (`WidgetActionReceiver.kt:17`) and `ControlReceiver` (`:93`);
  - `ACTION_RESUME` and `ACTION_RESUME_CONTEXT` from the UI.

  (`AppModule.kt:130` calls `controller.reapply()` directly, sends no start command, and so is
  not a source.) The worker's comment, "startForegroundService is a no-op if already running"
  (`MaintenanceWorker.kt:22`), is false: `onStartCommand` runs on every call.
- **Checked against both #132 logs.**
  - The 09-23 log *creates* the activity at 18:23:06.104 (`result code=0`, `Displayed +143ms`), and
    the notification reads "Monitoring ambient light" 76 ms later. Live Debug, captured 10 s after
    that at 18:23:16, shows smoothed 30.1 lx and target 19.
  - The 09-22 log only brings the task to front (`result code=2`, no post), and the notification
    keeps `Lux 1 → brightness 5`.
- **Seen unattended on the owner's phone (2026-09-23, observation 6): the worker path.** The
  pipeline state was non-null (smoothed 0.0, target 0), so the live model reads `Lux 0 →
  brightness 0`, and the screen was on, so `hibernate()` had not nulled it. Only a start command
  posts "Monitoring" over that model. Every start-command source above needs a user action except
  boot and `MaintenanceWorker` (`MaintenanceWorker.kt:23`, every 15 min), so the worker is the only
  source that fits. Optional confirmation: the time of the fallback matches the worker's schedule
  (`adb shell dumpsys jobscheduler`, the Tideo WorkManager job).
- **Reproduced on the owner's phone (2026-09-23, observation 3).** Saving the trust setting sends
  `ACTION_REAPPLY`, so `startForeground` posts "Monitoring". `reapplyProfile` → `setInitialBrightness`
  recomputes the same 0 → 0 model, so the `distinctUntilChanged` updater never re-posts it. Saving
  again repeats the same thing. No sensor or trust logic is involved. **Separating check (optional):**
  in steady light with the setting on, save an unrelated setting. If "Monitoring" appears, that
  confirms the cause.
- **Explains:** #130's fallback text and its tile/notification disagreement (the tile reads live
  state), and the owner's night sighting (or the intermittent wake case, F-F or H2). **Does not** show that brightness adjustment stopped.
- **Supersedes** the OEM battery-optimisation explanation posted on #130.

### F-B — A drop is not fully absorbed, and nothing continues it. Confidence: high as a mechanism; attribution provisional

`BrightnessEngine.smoothLux` (`:127-131`, default `deltaFactor` 1.8):
`luxDelta = |raw − S|/(S + 1)`, `α = 1 − exp(−1.8·(luxDelta − dynamicThreshold))`,
`smoothed = raw·α + S·(1 − α)`.

1. **A drop never finishes in one step.** For a drop, `luxDelta = (S − raw)/(S + 1) < 1`, so α stays
   well below 1. With a 0.3 threshold, a drop from 160 lx to 0 goes 160 → ≈46. Rises can snap: the
   contract test has 20 → 800 giving α = 1.0.
2. **No continuation path exists (Astra).** There are two dead-band gates:
   - the **outer gate**, `ProfileGates.monitorAmbientLightGate` (`:21`), checks raw lux against the
     *stored* thresholds using strict `<` and `>`;
   - the **inner gate**, `BrightnessEngine.evaluate` (`:56-58`), recomputes the band around the
     *previous processed* raw value and skips smoothing (`α = 0`, smoothed retained) inside it.

   Re-feeding the same reading therefore does nothing. After a drop to 10 lx the recomputed band is
   7–13 and the retry is inside it. **This inner gate is Tideo's divergence, not Tasker's; see F-E.** **Zero has its own trap:** after a 0 lx cycle the stored band is
   0–0.1 (`absoluteThresholds`, `:147`), and later 0 readings fail the outer gate, which needs
   `lux < 0.0` or `lux > 0.1` (`ProfileGates.kt:21`). Smoothed lux, left partway down by
   reason 1, then stays where it is for as long as the room stays dark. Re-checked 2026-09-24.
3. **The formula doesn't converge to raw either.** α crosses zero where
   `|raw − S|/(S + 1) = dynamicThreshold`. For raw 0 and a threshold of 0.3 that is S ≈ 0.43 lx.
   Rounding can halt progress earlier, and below that point the unclamped α (D-010(a)) goes
   **negative**.
4. **The proximity damp** (`α × 0.1` while "near", `%AAB_Proximity`, applied at `BrightnessEngine.kt:67`) makes
   every leftover larger. The AOC logged "Device appears to be covered" at each re-activation in
   both #132 logs.

**Effect:** a healthy sensor and a completed brightness cycle can still leave the screen too
bright after a drop, until the light changes again or the service restarts. A restart has no
previous smoothed value and snaps to raw.

**Evidence:** *consistent with* 09-22 capture 1 (α 0.624, ambient 2, raw 0; toggling gave
`Lux 1 → brightness 5` then `Lux 0 → brightness 2`) and the Reddit screenshot (ambient 47, raw 0,
brightness 24/255). Back-solving from rounded dashboard values, with a threshold taken from a
different capture, shows **consistency, not a reconstruction** of the transition. F-B is a strong
candidate for capture 1 only.

### F-C — The newest reading is discarded while busy, with no reconsideration. Confidence: high as a mechanism; attribution unproven

- `onSensorSample` (`BrightnessPipelineController.kt:194`) drops a reading while a cycle is
  running, in two places: prof760's `mainLoopOn` operand (`:212`, evaluated in
  `ProfileGates.kt:22`) and the `inCycle` compare-and-set (`:217`). `runCycle` drops one inside
  the cooldown (`PipelineCycleRunner.kt:73-74`). None of them keeps the reading or schedules
  another look. Re-checked 2026-09-24.
- If the **final** reading of a change lands in the ≈1.3 s window (cycle 544–557 ms + cooldown
  460–776 ms in the #132 captures) and the light then holds steady, that reading is never
  evaluated.
- **Startup race (Astra):** `start()` registers the sensor before the consumer has loaded
  `cachedSettings`, and `onSensorSample` returns while settings are null (`:205`). The sensor's
  initial event can therefore be lost on every service start.
- **Candidate for 09-23 capture 2.** The last *processed* raw reading was 31.8 lx, and the first
  reading after re-activation was 0 lx. F-B alone cannot produce that. **A hand over the sensor at
  the toggle, or a sensor path that went quiet (H1), explain it equally well.**
- **The capture 2 sequence (owner, 2026-09-24).** The capture's readings run 11.5 → 31.8 → 0 lx. If
  the 0 lx reading arrived while the 31.8 lx cycle was animating or cooling down, F-C drops it, and
  an on-change sensor in a steady dark room sends nothing further, so the screen holds 31.8 lx's
  brightness indefinitely. That fits the capture without assuming a sensor fault. It is still a
  fit, not a trace: only R3 records the drop reason. This is the evidence that moved R6 into the
  train (§5).

### F-D — The health surfaces cannot tell "steady" from "stuck". Confidence: high

- **The dashboard banner** goes STALE after 10 s without a publish (`LiveRuntimeState.kt:17-25`), and
  publishing happens only when controller state, the active context or the manual override changes
  (`AmbientMonitoringService.kt:264-271`), so steady light raises it. **An independent heartbeat would prove only that the heartbeat runs; it must not
  certify sensor delivery or cycle progress (Astra).**
- **The `onTaskRemoved` watchdog** (`armStalenessWatchdog`, `:593`) resets `LiveRuntimeState` after
  5 s without a publish, which steady light satisfies. A running service can then show as stopped on
  the dashboard, tile and widget. The logs don't show this happening; it is a code-level route to
  misleading state.

### F-E — Tideo's dead-band is not Tasker's. Confidence: high (code read against Tasker source)

Owner lead, 2026-09-23: "luxAlpha can go negative in Tideo; I never saw that in Tasker". Tasker's
flow, from `pipeline_spec.md` §1–§6 and the extracted Java:

- task554 act1 sets `%AAB_LastRawLux` to the **current** reading, BigDecimal-rounded to three
  decimals (`task554_1…txt:27-30`). Only after
  that does act2 run task544.
- task544 act19 **stops** if `relative_change < dynamic_threshold`, where
  `relative_change = round3(|par1 − SmoothedLux| / (SmoothedLux + 1))`. The comparison is against
  **smoothed** lux, and task535 smoothing (act25) never runs when the change is too small.
- task546 builds the stored band around `%AAB_LastRawLux`, i.e. the **current** reading. It does
  this at both of its call sites: act20 (the dead-band path) and act35 (after smoothing).

Tideo's `BrightnessEngine.evaluate` has neither piece:

1. **No act19.** `shouldUpdate` (`:57`) tests the reading against an absolute band instead of
   `relative_change` against smoothed lux, and nothing in the engine computes `relative_change`.
   Smoothing therefore runs where Tasker stops, which makes negative α routine. That moves the
   smoothed value *away* from the reading, a ghost of its own.
2. **The band is one reading late.** `absoluteThresholds(input.lux, prev?.lastRawLux …)` (`:56`)
   centres both the inner band and the stored outer band (`PipelineCycleRunner.kt:159-160`) on the
   *previous* reading (re-checked 2026-09-24: `BrightnessEngine.kt:149-150`, fed by
   `prev?.lastRawLux` at `:56`). **Seen in the field:** 09-23 capture 2 shows 8–15 lx, which is the previous
   raw ≈ 11.5 ± 30% rounded to whole lux, while the reading just processed was 31.8. Tasker's band
   there would have been ≈ 22–41.
3. **Consequence — a return to the previous level is ignored.** After A → B is processed, the
   stored band sits around A, so a later reading back near A fails the outer gate. The screen then
   stays at B's brightness until the light leaves A's band. Example: sun at 100 lx, shade at 30,
   back in the sun: the band is 70–130 and the return is dropped, so the screen stays dim in
   sunlight. No sensor fault or timing race is involved. A drop to under 0.2 lx escapes the trap,
   because the `< 0.2` special case uses the current reading.
4. **It does not cure F-B's retry trap (Sol).** Once task546 centres the stored band on the current
   reading, an identical repeat reading lies inside it, so prof760's outer gate
   (`profiles.md:40-48`) rejects it before task554/task544 run. On an on-change sensor the repeat
   does not even arrive. **As far as prof760 goes, Tasker shows F-B's stall too.** The Q2 check
   still has to establish whether anything else re-ran the main loop. Where act19 *does* run, it
   removes the routine negative α: it stops wherever `effective_delta` would be negative under the
   same threshold, and Tideo's gate and smoothing share one (`dynamicThreshold * 100`, D-036
   Finding 7).

**How it slipped through.** The golden vectors cover smoothing, thresholds and mapping in isolation.
Which gate runs, and where the band is centred, sits in `evaluate`, which only
`BrightnessEngineContractTest` covers. D-039(a) took the engine's `shouldUpdate` as the oracle
without comparing it to act19. The ledger records no decision to diverge.

**Caveat.** Tasker is not entirely free of negative α (`parity_gaps.md:35`). Its task535 subtracts `%AAB_ThreshDynamic`,
which in the smoothing path was last written by the *previous* cycle's task546 (act35 runs after
act25), so a threshold that fell between cycles can still give a small negative α. That is rare,
consistent with the owner never observing it.

### F-G — The proximity damp slows smoothing, which Tasker's does not. Confidence: high (code read against Tasker source, 2026-09-24)

Found while transcribing act10–act35 for R5. In task544, act27 stores `%SmoothedLux` from
task535's own result, computed with the **undamped** α. act29 then sets only the global `%LuxAlpha`
to `%lux_results2 * 0.1`, and act33 hands Map Lux `par2 = %lux_results2`, the undamped α again (XML
L16196–L16275). The damped value therefore feeds nothing but the readouts that display
`%LuxAlpha`. Tideo applies the ×0.1 **inside** the EMA (`BrightnessEngine.kt:67`, `:130-131`), so
while proximity reads near every smoothed step moves a tenth as far, and the animation is sized
from the damped α. That is the mechanism F-B's reason 4 describes, and it is Tideo's own. The
AGENTS.md sentence "multiplies `LuxAlpha` by 0.1; it never pauses" holds for Tasker's global, and
the contract test `proximityNear_dampsLuxAlphaByTenth` pins Tideo's divergent behaviour.
**Deferred by the owner (2026-09-24) to a fresh-context session** and routed to the STATE Owner
queue; R5 shipped without it. **Resolved 2026-09-24 as a parity restore (DC-064):** the engine
smooths, maps and animates undamped and damps only the reported α; that pinning test is replaced.

### F-F — A low-accuracy reading is rejected, and nothing re-admits it. Ruled out on AOSP frameworks (2026-09-24); RF dropped

- prof760's first stage drops any reading with `accuracy ≤ 1` unless "Trust low-accuracy sensor"
  is on (`ProfileGates.kt:20`, `BrightnessPipelineController.kt:207-208`).
- **On AOSP that gate can never fire for light.** `LightSensorSource` passes `event.accuracy`
  (`LightSensorSource.kt:36`). The framework's JNI receiver fills that value from the HAL's status
  only for orientation, magnetic field, accelerometer, gyroscope, gravity and linear acceleration
  (`vector.status`) and heart rate. **Every other type, `TYPE_LIGHT` included, gets
  `SENSOR_STATUS_ACCURACY_HIGH` (3)** (`frameworks/base/core/jni/android_hardware_SensorManager.cpp`,
  the `default:` arm of the status switch). The same arm is present at `android-12.0.0_r1` (minSdk 31)
  and `android-16.0.0_r1`. So on an AOSP-derived framework, including crDroid (#132), the
  trust setting cannot change which readings pass.
- **`onAccuracyChanged` cannot re-admit either.** `SystemSensorManager.dispatchSensorEvent` calls it
  only while delivering an event, when that event's accuracy differs from the last one
  (`core/java/android/hardware/SystemSensorManager.java`, "call onAccuracyChanged() only if the
  value changes"). "Accuracy recovers without a new event" cannot happen, and ignoring the callback
  (`LightSensorSource.kt:39`) loses nothing that `event.accuracy` does not already carry.
- **What remains open:** an OEM framework that rewrote that switch. Nothing shows one, and
  OxygenOS admitted 0 lx with the setting off (observations 4, 5). R3 records every callback's
  accuracy, so a divergent OEM would show there. Only then would this finding reopen, as a new
  analysis rather than as RF.

The analysis below is superseded by the bullets above. It is kept for R8's row, because it records
how the wake path behaves.

- **The wake path leaves no second chance.** Screen-off `hibernate()` nulls `smoothedLux`,
  `lastRawLux`, the thresholds and `lastAcceptedMs` (`BrightnessPipelineController.kt:265-279`). On
  wake, `reinit()` caches settings *before* `startSensor()` (`:251-253`), and
  `setInitialBrightness` returns early with no lux (`PipelineCycleRunner.kt:359`), so **no
  brightness is written**. The re-registration's first event then meets unseeded thresholds, a
  cleared cooldown and a free mutex, so the accuracy gate is the only one that can reject it. If
  it is rejected, or never arrives (H2), nothing runs until the light changes.
- **Not shown on the owner's OnePlus 13.** A wake there sometimes ends on "Monitoring" with the
  setting off (§1, observation 1), and at other times gives `0 lx → 0` with it off (observations 4
  and 5). This sensor therefore *can* report accuracy > 1 at 0 lx. Whether the failing wakes were
  a low-accuracy first event (F-F) or no event at all (H2) is open. Observation 2 does not separate
  them either, since the setting may have been on during a wake that would have succeeded anyway.
  Whether `onAccuracyChanged` ever fires on this sensor is unknown.
- **Code fact, independent of any device: turning the setting on never re-admits.** Turning it on
  saves settings → `ACTION_REAPPLY` → `setInitialBrightness`, which reads only
  `smoothedLux ?: lastRawLux`, both null after a wake. A rejected reading is never kept, and an
  on-change sensor in steady light sends no other. Observation 3's lasting "Monitoring" is F-A
  either way. **Whether Tasker re-evaluates prof760 when `%AAB_TrustUnreliable` changes is unchecked**:
  check it with `docs/rebuild/XML_RECIPES.md` alongside Q2, and do not assume parity either way.
- ~~On an on-change sensor, accuracy can recover without the value changing~~ — withdrawn
  2026-09-24 (§3): the callback fires only with an event.
- The Pixel's AOC "Device appears to be covered" at each re-activation cannot reach the gate as
  low accuracy, because the framework reports light as high accuracy (above).
- **Asked on #132 (owner, 2026-09-23): does enabling the setting stop the freezes?** On an AOSP
  framework the answer cannot depend on the setting, so any difference the reporter sees is
  coincidence or a different cause.
- **R2 cannot explain the wake case.** `reinit()` loads settings before registering the sensor, so
  F-C's startup race applies only to the first service start.

### H2 — The first event after a wake is sometimes not delivered. Open

With F-F ruled out (2026-09-24), this is the leading reading of the owner's intermittent wake
"Monitoring" (§1, observation 1). The other is a start command landing after the wake's cycle
(F-A). If the ALS sends no initial event on re-registration, an on-change sensor in steady light
sends nothing. The wake path writes nothing without an accepted reading (F-F's wake-path bullet),
so brightness stays where the framework restored it. **Separating check, no new build needed:**
Live Debug's "Last sample" is stamped for *every* delivered reading, before any gate
(`BrightnessPipelineController.kt:204`), and shows seconds under a minute. After a wake that ends
on "Monitoring", with the screen off for at least 2 min beforehand, open Live Debug at once. If it
reads 2 min or more, no reading arrived (H2). If it reads seconds, a reading arrived. After a wake
no gate can refuse that reading (unseeded band, cleared cooldown, free mutex, accuracy always
high), so a start command then overwrote the notification (F-A). Opening the app reposts
"Monitoring" (F-A) but does not touch "Last sample". The owner's one run (2026-09-23) did not
reproduce the failure, so it separated nothing. In observation 6 the sensor kept delivering at a
steady ~0 lx (last sample "just now", last completed cycle 7 min earlier). A sensor that repeats
like that recovers within seconds from a missing first event. So on this phone H2 predicts
"Monitoring" that clears by itself, and "Monitoring" that *stays* points to F-A. That rests on one
screenshot, and the delivery rate has not been measured.

**Tasker does not wait for the first event (2026-09-24).** task618 Set Initial Brightness, which
runs on wake (prof761), reads the light sensor itself (act8, code373 with sensor type 5 and a
`%AAB_DefaultThrottle` timeout) and retries up to seven times while accuracy is under 2 and trust is
off (act9–12). It then sets `%SmoothedLux` and `%AAB_LastRawLux` from that reading and maps it.
Tideo's `setInitialBrightness` has no reading of its own after a wake (F-F's wake-path bullet), so
H2 is a Tideo-only exposure. Recorded here as evidence for a future H2 fix, which is still not
planned.

**Observation 6, other readings.**
- The samples arriving at 0 lx were refused by the stored 0.0–0.1 band (F-B's zero trap: 0 is
  neither below 0.0 nor above 0.1). Accuracy cannot refuse them (F-F). The screen was already at
  target, so this is harmless here.
- Hardware 15 against target 0 is the PWM floor or dimming threshold at work (`applyPwmFloor`,
  D-050), with the perceived target reported (D-109). It is a live instance of R7's rule that
  completion is judged on the perceived target, not the hardware value. A fix for H2 is not
planned: it waits for a separated reproduction, and would change what a wake does with no reading.

### H1 — The sensor path goes quiet while still enabled. Open

In both #132 logs the ALS stays enabled at the HAL until the toggle's `Enable = 0`. HAL enablement
does not name the client, so it is **consistent with** Tideo's registration surviving without
proving it (Sol), and it says nothing about delivery through Tideo's callback and flow. The logs
are the attachments on Tideo #132; they are not stored in this repository. **Neither log contains the stall's onset:** the last samples fall 2–4 min
before each log starts.

## 3. Claims withdrawn during review (do not reintroduce)

- **"Last sample stale ⇒ nothing reached the app."** That holds only while readings keep arriving.
  One rejected *final* reading goes stale in step with the last completed cycle, and both screens
  truncate ages to whole minutes (`LiveDebugScreen.kt:307`, `DashboardScreen.kt:372`).
- **"Raw lux" is the latest received reading.** It is the last *processed* one: `lastRawLux` is
  written when a cycle completes (`PipelineCycleRunner.kt:157`).
- **"Acknowledged writes and normal cycle times rule out a block."** They describe completed past
  work only.
- **"The 8–15 lx band passes every sample."** It passes only readings outside it. "The band is
  centred on the previous reading by construction" is true of Tideo's code
  (`BrightnessEngine.kt:149-150`), but Tasker centres it on the *current* reading, so it **is** a
  bug: F-E.
- **"Re-feed the last reading until it converges."** Rejected for F-B's reasons 2 and 3.
- **"Capture 1 is fully explained."** It is consistent with F-B, nothing more.
- **"Runtime-only means no parity decision."** A continuation or completion policy changes
  behaviour, wherever the code lives.
- **"The failure is inherited from Tasker."** Unverified; see Q2. prof760 alone would not continue
  it, though (F-E item 4).
- **"Under act19, an unchanged repeat reading keeps smoothing until act19 stops it."** prof760
  rejects it first (F-E item 4). A continuation that re-enters task544 is a new policy, not parity.
- **"The OnePlus 13 reports accuracy ≤ 1 at 0 lx, so F-F is confirmed there."** Inferred from
  observations 1–2 on the assumption that a first event always arrives. Observations 4–5 admitted
  0 lx readings with the setting off, and H2 explains observation 1 equally well.
- **"These segments together are the fix for #132."** Not established. The train fixes F-A and
  defects shown on their own, and adds diagnostics. #132's freeze is still under investigation
  (owner, 2026-09-23).
- **"If the setting stops the freezes, F-F is #132's cause."** Supporting evidence only, since the
  stalls are intermittent. Attribution uses R3's records (§5, Scope).
- **"No more cycles than today's policy plus one" as the no-ghosts test.** A correct slot can
  exceed that over a long burst (R6).
- **"α ≥ 0 on every smoothed row" as a parity test.** Tasker's task535 subtracts the previous
  cycle's stored threshold, so faithful parity can yield a small negative α (F-E caveat).
- **"A light reading can arrive with accuracy ≤ 1", and the claims resting on it** (withdrawn
  2026-09-24, F-F): that observation 2 reflects the trust setting; that observation 6's 0 lx
  samples may have been refused for accuracy; that a wake which *stays* on "Monitoring" points to
  F-F; and that accuracy can recover without a new event. AOSP reports light as
  `SENSOR_STATUS_ACCURACY_HIGH` and calls `onAccuracyChanged` only with an event.

## 4. Owner decisions — both answered 2026-09-23; Q1 active again since 2026-09-24

> **Q1 is active again (owner, 2026-09-24)** because R6 is back in the train (§5, Scope). Its
> D-027 rule review has not started, so nothing binding has changed yet. **Q2 stays inactive**:
> R7 is still deferred, and its answer stays on record for a reopening.

- **Q1 → (b), with a condition.** Keep the newest reading, **as long as Tideo does not chase
  ghosts**, e.g. driving under trees in the sun, where it would respond to bright/dim/bright/dim
  cycles long after the light has changed. R6 turns that condition into acceptance tests.
- **Q2 → (b), after the Tasker check.** Settle to the target once the check is done.

The options as they were put:

- **Q1 — Keep the newest reading instead of dropping it (F-C).** This reverses D-027(d) for one
  slot. D-027(d) holds that a queueing or conflating implementation "would process events Tasker
  never would". It also changes the concurrency invariant in `AGENTS.md`, so the **rule-review
  protocol** applies.
  - (a) keep drop-not-queue, and accept F-C;
  - (b) a single pending-latest slot, reconsidered once without a new callback;
  - (c) (b) only for the startup race.

  **Recommendation: (b).** One slot replaces rather than queues, so there is no backlog. The
  reading Tasker would have dropped is the one that leaves the screen wrong indefinitely on an
  on-change sensor.
- **Q2 — A settling path with a defined endpoint (F-B).** This is user-visible behaviour with no
  parity source (discipline 7(b)). **Answer first whether Tasker shows the same stall:** check how
  prof760's light trigger fires on an unchanged reading, and whether anything re-ran the main loop,
  using `docs/rebuild/XML_RECIPES.md` and never reading the whole XML.
  - (a) keep parity, and accept F-B;
  - (b) Astra's contract: once a transition is accepted, move by a bounded transition to the
    brightness target for the accepted stable lux, and finish when that target is reached;
  - (c) (b) only when the Tasker check shows Tasker settled in practice.

  **Recommendation: (b), after the Tasker check.** Curve maths and golden vectors stay untouched.

## 5. Segments

Each segment ends: ladder green → STATE Changelog line → commit → push (discipline 3). The
**glue-review protocol** is mandatory for every runtime segment. User-facing lines go into
`changelogs/25.txt` (500-character cap). There is no version bump; this train is 1.11.0 / vc25.
Nothing is posted on either issue unless the owner asks (DB-082). The `:app` and `:platform`
comment budgets were exactly full at `64be441` (2538/2538 and 330/330), so a runtime segment must
offset any comment it adds. Raising a budget is a rule change (`comment-budget.sh`, rule-review
protocol).

**Scope (owner, 2026-09-23): this train investigates one unresolved freeze and fixes defects that
can each be shown on their own.** It is not presented as the fix for #132. The work falls into
three groups:

1. **The notification fix and diagnostics: in the train.** R1 (F-A, reproduced on demand and seen
   happening unattended) and R3 (the evidence #132 needs). Nothing so far shows that brightness
   tracking stopped on the owner's phone.
2. **Code defects: in the train only if a deterministic test reproduces the failure first.** R2
   (startup race), R4 (the `onTaskRemoved` watchdog), R5 (the misplaced dead-band) and, **since
   2026-09-24 (owner), R6** (keeping the newest dropped reading; F-C's capture 2 sequence). Each
   segment's first commit step is a test that fails on the current code, and the fix must turn it
   green. R6 is also new behaviour, so it additionally needs the D-027 rule review and lands after
   R5 so its tests run against the corrected gates.
3. **New behaviour: deferred.** R7 (finishing partly smoothed transitions). It addresses a
   plausible mechanism, but it is not shown to be necessary for #132, and it adds timing,
   cancellation and state responsibilities. **RF is dropped, not deferred (owner, 2026-09-24):**
   its mechanism cannot occur on an AOSP framework (F-F). Tests can show that R7 meets a chosen
   contract, not that the contract is the right answer to the reporter's problem, and neither an
   adb session nor the E2E suite closes that gap. **Reopen it only when a diagnostic trace (R3,
   from #132 or the owner's phone) names the failing path it addresses.** Its contract below is
   kept for that day.

An episode is attributed to a path only by R3's record of that episode: the rejection reason and
the trust setting in effect. R3 must record both, even though on AOSP accuracy always passes: the
record is what would expose an OEM framework that differs (F-F).

- [x] **R0 — this plan.**
- [x] **R1 — F-A, the notification overwrite.** **Done 2026-09-24 (DC-065).** The three new
  `AmbientMonitoringServiceTest` cases failed on the old code as predicted (`a settings save must
  not post Monitoring expected:<[Lux 100 → brightness 120]> but was:<[Monitoring ambient
  light]>`; the paused case read "active"). A lock was added beyond the plan: without it, a start
  command could leave an older model visible over a newer one that the updater had just posted.
  **Device check passed 2026-09-24** (OnePlus 13, debug build of `a50e935`, partly cloudy
  daylight). Each REAPPLY was read 0.5 s later from the same device shell, and the text stayed
  `Lux … → brightness …` with the lux unchanged, so no new reading re-posted it. The
  notification's `when` moved, which shows that REAPPLY re-posted it. The release build 1.10.0
  had switched to "Monitoring" on the same REAPPLY earlier that day.
  - **Change:** when the pipeline is already running, build the `startForeground` notification
    from current controller state and active context, through the same model mapping the live
    updater uses. Preserve the paused state and its actions. Correct the `MaintenanceWorker`
    comment.
  - **Tests:** a repeated start with an unchanged model and no later sensor event leaves the posted
    text at `Lux … → brightness …` and keeps the active-context subtext (`:528`); the paused
    variant keeps its title and actions; a first start (not running) still posts the monitoring
    text. **Drive the repeat through `ACTION_REAPPLY`** (the owner's reproduction: a settings save
    whose reapply recomputes the same model), as well as through a plain start. After a wake with
    no accepted reading, "Monitoring" is still posted; that text is true there (F-F or H2).
  - **Device check (owner):** in steady light, saving any setting leaves `Lux … → brightness …`.
- [ ] **R2 — F-C's startup race only.** Group 2. No fork: it reorders startup and queues nothing.
  Register the sensor only after `cachedSettings` has loaded. Holding an early reading until
  settings load would be deferred work, which belongs to the deferred R6 and its D-027 rule
  review, so R2 does not do it (Sol).
  - **Entry test (fails today):** a fake sensor that emits on registration, before the settings
    provider resolves; the emitted reading must be evaluated.
  - **Tests:** registration happens after settings resolve, and the first event after
    registration is evaluated.
- [x] **R3 — Diagnostics. Done 2026-09-24 (DC-066).** Live Debug has a "Light Sensor" card. The
  callback record is written on the listener thread and is generation-scoped. Registration shows its
  cause and whether `registerListener` succeeded. It shows received, admitted and rejected, with
  the last rejection's reason and the trust setting in effect, then the cycle stage since its claim
  and the last cycle's result, with R5's act19 stop as `DEAD_BAND_STOP`. **Taxonomy as shipped:**
  admitted means it reached `engine.evaluate`. Rejection reasons are SETTINGS_NOT_LOADED,
  SERVICE_DISABLED, ACCURACY, DEAD_BAND and MUTEX at the collector, QUEUE_CLOSED, and PAUSED and
  COOLDOWN at the cycle. There is no "deferred" count until R6 creates one. R6 must move MUTEX and
  COOLDOWN into it and add "replaced". A Sol review added the generation and claim guards and the
  trust record. The glue review scoped every counter move to the owning claim, so a tick queued
  across a wake or a restart moves nothing, and cleared the registration on hibernate and stop.
  **Daylight device check passed 2026-09-24** (OnePlus 13, debug build): START registration and its
  first event (331 lx, accuracy 3), 321 callbacks against 321 received, and counters that sum. The
  sensor-to-callback lag was 172–230 ms, a baseline for H1. The dark-wake test (§7 step 4) was
  deferred by the owner because it was daytime.
- **R3 as specified** (Astra), shipped before R4 and R5 so a reporter can separate lost delivery,
  a dropped final reading and an unfinished cycle from one screenshot. In Live Debug show:
  - the actual sensor callback: value, accuracy, sensor timestamp and arrival time;
  - collector receipt, plus admitted, deferred and rejected counts with the last rejection reason
    (accuracy, dead band, mutex, cooldown, settings not loaded). Accuracy stays a reason even
    though AOSP always passes it, so an OEM framework that differs would show (F-F). The
    `onAccuracyChanged` value adds nothing, because the framework calls it only with an event
    whose accuracy is already recorded, so it is dropped (2026-09-24);
  - the current cycle stage and start time, and the last completed cycle;
  - whether a first event arrived after each registration (wake or start), with its value and
    accuracy, so one screenshot separates H2 from F-A on the OnePlus.

  Labels must be string resources: the hardcoded-string ceiling in `HardcodedStringCheckTest` may
  only fall. **Later segments revise this taxonomy (Sol):** R5 adds a distinct act19 stop, and R6
  turns busy and cooldown drops into deferred or replaced work. Each such segment
  states which counter a reading now lands in, so no reader of these signals sees an ambiguous one.
- [ ] **R4 — F-D, the `onTaskRemoved` watchdog only.** Group 2. Clear runtime state by
  service-instance ownership and lifecycle, not by publish recency, so that steady light no longer
  shows a running service as stopped.
  - **Entry test (fails today):** a running service in steady light, 5 s or more after
    `onTaskRemoved`, keeps its state.
  - The dashboard banner redesign (driving STALE from R3's signals, with wording the owner
    approves) is **not** in this train. It waits until R3's signals have been seen in the field.
- [x] **R5 — F-E, restore Tasker's dead-band.** **Done 2026-09-24 (DC-063, parity_gaps gap-08).**
  The entry test failed on the old code as predicted (`the 100.0 lx reading must be evaluated
  expected:<100.0> but was:<30.0>`). Two points below were corrected in the doing: act14's first
  run seeds **0 % and a zero-width band** (Tasker maths reads the unset `%dynamic_threshold` as 0),
  not a band from the reading's own threshold; and `setInitialBrightness` is task618, not act10,
  so it is **not** checked against the first-run row (gap-08 records task618's differences). The
  acts 28–33 proximity mismatch found here is F-G. Group 2. This restores parity, so it needs no fork
  (playbook 2/4). It does change behaviour users know, so it gets a changelog line. Only R6 lands
  after it, and the owner decides whether it ships in 1.11.0 or a later release. **Started
  2026-09-24, ahead of R1 and R3 (owner):** it is the same work under every answer to the R6/R7
  question.
  - **Entry test (fails today):** A → B is processed, then a return to A is rejected by the stored
    band.
  - **Change:** in `BrightnessEngine.evaluate`, add task544 act19 (`relative_change` against
    smoothed lux, stopping below `dynamic_threshold`) before smoothing, and centre
    `absoluteThresholds` on the **current** reading as task554 act1 rounds it (three decimals,
    HALF_UP), not on an unrounded `input.lux`. Keep act20's and act35's differing `par1` for the
    `< 0.2` special case and the rounding scale.
  - **The first reading bypasses act19 (Astra).** Tasker's acts 10–17 run before act18: if
    `%SmoothedLux` is unset (and `%AutoBrightRunning ≠ 1`), they set `SmoothedLux = par1`,
    `LuxAlpha = 1`, `LastAAB = now`, seed the thresholds, map the reading and stop
    (`pipeline_spec.md` §3 step 3). Today the engine reaches that case only through
    `prev == null` in `shouldUpdate` (`BrightnessEngine.kt:58`), with `prevSmoothedLux` defaulting
    to `input.lux` (`:52`). Adding act19 without an explicit first-run branch would compute
    `relative_change = 0` and reject the first reading after every start and wake, **indefinitely**
    on an on-change sensor in steady light. So `evaluate` needs a first-run result: the reading
    initialises smoothing, gets its target and seeds the band, and does not pass act18/act19.
    `setInitialBrightness` calls the engine with an empty previous state
    (`PipelineCycleRunner.kt:362`), so it takes the same branch.
  - **The act19 stop is its own result, not a zero-α one (Sol).** Tasker's act19 path runs act20
    (the band from `%par1`), clears the cycle and stops (acts 20–23): no smoothing, no mapping, no
    brightness or dimming work. `evaluate` must return a result that `PipelineCycleRunner` can tell
    apart. For it, the runner stores act20's band and skips mapping, animation, throttle updates
    and accepted-state publication (`PipelineCycleRunner.kt:80`, `:139`, `:148`).
  - **Tests:** a reference oracle for the act10–act35 orchestration in `TaskerReference`, starting
    at act10 so that the first-run branch is covered, with contract rows for:
    - **first-run regressions (Astra):** a cold start, then one reading and silence; a screen
      off/on, then one reading and silence. Each runs once at non-zero lux and once at 0 lx, and
      each must produce and write the reading's target. `setInitialBrightness` with an empty
      previous state must match the first-run row;
    - a return to the previous level after A → B is processed;
    - a reading below act19's threshold making no smoothing call **and no mapping, write or
      accepted-state publish**;
    - every smoothed row's α matching the oracle, sign included (a negative α from a threshold that
      moved between cycles is parity; F-E caveat);
    - the stored band centred on the rounded processed reading.
  - **Record:** a `parity_gaps.md` entry; the `PARITY_CHECKLIST.md` rows for task544, task546 and
    task554, which read `ported` today (playbook 2); and a row that amends D-039(a)'s "engine is
    the oracle" premise.
  - **R6 builds on R5's gates.** R5 changes R6's contract, and it does not supply R7's
    continuation (F-E item 4).
- [ ] **R6 — F-C, the pending-latest slot.** Group 2, **in the train since 2026-09-24 (owner)** on
  F-C's capture 2 sequence. Q1's answer (b) is active (§4). Lands after R5.
  - **Rule review first.** It amends the concurrency invariant in `AGENTS.md` ("events that arrive
    during a cycle are dropped rather than queued") and records the D-027(d) departure in a new
    row, both through the rule-review protocol, in the same commit as the code.
  - **Entry test (fails today):** a final out-of-band reading during an animation, then silence,
    is evaluated.
  - **The owner's no-ghosts condition, as acceptance tests.** These are structural properties, not
    a cycle count (Astra). A slot legitimately uses its kept reading when the cooldown expires,
    where today's code waits for another callback, so over a long burst it can run more cycles
    than today's policy without any backlog or stale replay. The tests:
    - there is exactly one slot, and a newer reading replaces it; nothing queues;
    - the current cooldown is respected: no cycle starts inside it;
    - when a cycle starts, it takes the newest eligible reading;
    - a reading that was superseded while pending is never processed afterwards;
    - once input stops, the final eligible reading is reconsidered with no further callback;
    - **latency, qualified:** a reading that stays newest and eligible, with no control event
      delaying or invalidating it, is evaluated at most one cycle plus one cooldown after it
      arrived. No unconditional deadline is possible, because queued control events run first
      (Ordering, below).

    The flicker run (bright and dim alternating faster than the cooldown for 30 s, then steady)
    asserts these properties throughout, and that its last cycle evaluates the final reading.
    Ending on the final light's *target* needs R7's settling, which stays deferred, so R6 does not
    assert it (Sol).
  - **Behaviour:** new readings replace the pending one during initialisation, an active cycle and
    the cooldown. After completion, the newest pending reading is reconsidered against current
    settings and thresholds. During a cooldown, one reconsideration is scheduled for when it
    expires. Pause, override and proximity behaviour are preserved. Pending work is invalidated on
    screen-off, stop and a new sensor session. An in-progress animation is never cancelled.
  - `.conflate()` upstream is insufficient: the rejections happen downstream of the collector.
  - **Ordering (Sol):** screen-off, pause, override, context and stop events share the consumer path
    and queue behind an active cycle (`BrightnessPipelineController.kt:223`). A pending reading is
    never drained ahead of a control event that is already queued; that event runs first and may
    invalidate it.
  - **Tests:**
    - the entry test above;
    - the same during a cooldown;
    - after each of a queued screen-off, pause, override, stop and sensor-session replacement, the
      pending reading is not evaluated.
  - **R3's taxonomy:** a busy or cooldown drop becomes "deferred" when it fills the slot and
    "replaced" when a newer reading supersedes it; R6 states this in its commit.
- [ ] **R8 — close-out.** Deleting this plan must not lose the deferred analysis. Write ledger
  rows, none citing this path, for:
  - what shipped;
  - **open findings, one row each:** F-B, H1 and H2, and F-C too if R6 has not shipped. Each row
    carries its mechanism, its evidence, and the contract from the Deferred subsection, so that a
    reopening starts from the analysis rather than from nothing;
  - **F-F, as ruled out:** the AOSP source facts in its section, why RF was dropped (owner,
    2026-09-24), and the one condition that would reopen it (an OEM framework that R3 shows
    reporting light accuracy ≤ 1);
  - Q1 as executed by R6, through its own rule-review row. Q2 as answered but inactive, since R7
    never started, so nothing binding changed.

  Then delete this file.

### Deferred — not in this train (group 3)

Not a checklist item. Reopen it only as the Scope block above says; its contract then becomes a
segment, re-reviewed against the evidence that reopened it. RF, formerly listed here, was dropped
on 2026-09-24 (F-F). R8's F-F row keeps what it was and why it went.

- **R7 — F-B, the settling path.** Reopens on a trace showing a completed cycle that left the screen
  short of its target, with no later reading admitted. Q2's answer stands but is inactive (§4), and
  the Tasker check comes first. Re-derive the
  continuation on top of R5. act19 gives a defined endpoint only for readings that reach task544,
  and prof760 keeps an unchanged reading out (F-E item 4). Any continuation is therefore a new
  policy with its own row, whatever the Tasker check finds. The acceptance criteria below stand
  either way, and once R7 lands R6's flicker run must also end on the final light's target.
  - **Behaviour:** separate "has a sufficiently different reading arrived?" from "has the accepted
    transition finished?". Continuation bypasses both input gates and keeps pause, override,
    lifecycle and proximity behaviour. It completes when the brightness target for the accepted
    stable lux is reached. Repeated calls to the adaptive-α formula are not a completion policy.
  - **Supersession contract (Astra), for the under-trees case:**
    - a newly accepted reading supersedes whatever remains of the continuation toward the old
      target. An animation already running may finish under the chosen policy, but all later
      settling uses the new target, and the old continuation never resumes;
    - **duration bound:** settling toward one target finishes within a stated bound, which R7 must
      name and justify before coding;
    - **terminal state:** on completion, the engine state the next evaluation reads (smoothed lux,
      stored band, last raw) is consistent with the target reached. A display target reached while
      smoothing is left behind could make the next cycle move backwards;
    - **completion is judged on the perceived target, not hardware brightness alone:** under super
      dimming or the PWM floor (`applyPwmFloor`, D-050), the hardware value does not identify the
      perceived target (D-109).
  - **Tests:**
    - a single drop followed by silence finishes the brightness transition, for a zero and a
      non-zero destination, and the same with proximity near;
    - **superseded destination:** darkness starts a transition, bright light arrives before it
      finishes, then the sensor goes silent. The screen ends on the bright target, and the dark
      continuation never resumes;
    - after completion, one more identical evaluation moves nothing (terminal-state consistency);
    - completion with super dimming engaged and with the PWM floor active;
    - no timer is left re-evaluating an unchanged result.

    "Smoothed reaches raw within N cooldowns" is **not** a valid expectation.

## 6. Evidence still wanted from #132 (ask only if the owner chooses to)

- A build with R3 first; nothing in group 3 reopens without it. Then, during a stall and **before toggling**: a Live
  Debug screenshot, `adb shell dumpsys sensorservice`, and `adb logcat -d` with `adb logcat -G 16M`
  set beforehand so the onset is captured.
- Missing recent ALS events in `dumpsys` is **not** enough on its own to name a firmware fault;
  R3's callback-arrival counters are what separate H1 from F-C.

## 7. Device session workflow (owner-approved 2026-09-23)

The owner's OnePlus 13 is connected over adb to the owner's laptop, with Tideo's intent control
enabled. The agent drives it within the scope below.

- **Where.** Run the session on the laptop: the Claude Desktop app, or `claude remote-control` in
  the repo folder. A cloud container cannot reach the laptop's adb. Pull `claude/device-e2e-plan`
  first.
- **Scope. It does not relax `DEVICE_E2E_PLAN.md` §3,** which still binds the automated suite. This
  is supervised, hand-driven work for this plan only:
  - **read-only, by default:** `dumpsys` (including `sensorservice` and `jobscheduler`), `logcat`,
    screenshots;
  - **the only commands that change device state:**
    - Tideo's own intents: `com.tideo.autobrightness.control.REAPPLY`, `SERVICE_ON` and
      `SERVICE_OFF`;
    - screen off/on (`input keyevent 26`);
    - installing the debug build;
  - **never:** `pm clear`, uninstall, `settings delete|reset`, profile loading or changes, or
    anything on the E2E §3 denylist. Anything outside this list is asked first.
- **Two installs, one owner of brightness.** The debug build installs as
  `com.tideo.autobrightness.debug`, alongside the release app. Turn off the release app's service
  (`SERVICE_OFF` to `-p com.tideo.autobrightness`) before the debug service runs, and turn it back
  on at the end.
- **Order.**
  1. **Baseline on the release build.**
     - Reproduce F-A on demand:
       `adb shell am broadcast -a com.tideo.autobrightness.control.REAPPLY -p com.tideo.autobrightness`.
       While the model is non-null, the notification falls back to "Monitoring". The receiver
       ignores every action while intent control is off.
     - Check `dumpsys jobscheduler` for the Tideo WorkManager job's schedule, to confirm observation
       6's worker attribution.
  2. **Implement R5, then R1, then R3, then R2** (owner, 2026-09-24: R5 went first as the work that
     does not depend on the R6/R7 answer). R4 and R6 follow. Each ends on a green `scripts/ladder.sh`, a commit and
     a push (§5).
  3. **Install the debug build and repeat step 1** against `-p com.tideo.autobrightness.debug`.
     The notification must keep `Lux … → brightness …` (R1's device check).
  4. **With R3 installed, repeat the dark-wake test** (H2's check). R3 shows each reading's arrival
     and accuracy directly, so F-F and H2 can be told apart. A result is evidence for the deferred items; it reopens
     one only as §5's Scope block says.
- **Record.** Put each device result in the commit body, and put its effect on a finding in this
  plan. Device results are observations, scoped to when they were seen (AGENTS.md session
  protocol, step 6).
