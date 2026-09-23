# Plan — light-tracking stalls and the misleading monitoring surfaces (Tideo #130, #132)

> Execution plan (RUNBOOK playbook 5, multi-session). The owner approved **persisting** it
> (2026-09-23). Two of its segments are blocked on owner decisions that are still open (§4); approving
> the file does not decide them. The segment checklist is mirrored in `docs/STATE.md` →
> `## Active work`. The plan is provisional: delete it at R7, once its durable content lives in
> STATE Changelog lines and ledger rows. No ledger row and no code comment may cite this path.
>
> Analysis by Opus. Two gpt-6-astra review passes (2026-09-23) are folded in. Astra's corrections
> are applied in place, and §3 lists the claims they withdrew.

## 1. Reports and scope

| Report | Device | Symptom |
|---|---|---|
| Tideo #132 (LordSithek) | Pixel 9 Pro (caiman), crDroid, Android 16, 1.10.0, ELEVATED via root; battery optimisation off, locked in memory | Brightness occasionally stops following light (dark↔bright) until the service is toggled off/on. Two Live Debug captures with 48 s and 82 s logcats. |
| Tideo #130 (secondary symptom) | OPPO Find X9 Ultra, ColorOS, 1.10.0 | Notification "repeatedly falls back to *Monitoring ambient light*". Toggling the tile restores it; tile and notification disagree. |
| Owner | OnePlus 13, OxygenOS | Saw the same notification text at night. Tideo **never** goes stale on this phone. |

**Not every device is affected.** No mechanism in §2 needs a faulty device, but each one's
exposure depends on the sensor's event cadence. A sensor that emits frequent small changes (jitter)
keeps re-triggering cycles and hides both mechanisms. A strictly on-change sensor, such as the
Pixel's under-display TMD3733 on its always-on-compute chip, exposes them. **This is a hypothesis:
no event rates have been measured on either phone.**

**Code version.** All paths and lines are at `64be441`. The cited runtime files are byte-identical to
**v1.10.0** (`411e26a`), checked with `git diff --stat` over `app/.../runtime/`, `MainActivity.kt`
and `platform/.../sensor/`, so they describe the reporter's build.

## 2. Findings

### F-A — The foreground notification is overwritten with an empty model. Confidence: high

- `AmbientMonitoringService.onStartCommand` always calls `startForeground(…,
  buildNotification(NotificationModel()), …)` (`:143`). An empty model renders "Monitoring ambient
  light" (`:526`).
- The live updater is `.distinctUntilChanged()` (`:281`). It keeps its previous model and does not
  re-post that same model after `startForeground` has replaced the visible notification. **In steady
  light the visible notification stays wrong indefinitely**, and even a fresh sample fixes it only if
  it changes a model field.
- Start commands reach a service that is already running from two places:
  - `MainActivity.onCreate` → `AutoBrightnessRuntime.bootstrap()` → `startMonitoring()`;
  - `MaintenanceWorker`, every 15 min.

  The worker's comment, "startForegroundService is a no-op if already running"
  (`MaintenanceWorker.kt:22`), is false: `onStartCommand` runs on every call.
- **Checked against both #132 logs.**
  - The 09-23 log *creates* the activity at 18:23:06.104 (`result code=0`, `Displayed +143ms`), and
    the notification reads "Monitoring ambient light" 76 ms later. Live Debug, captured 10 s after
    that at 18:23:16, shows smoothed 30.1 lx and target 19.
  - The 09-22 log only brings the task to front (`result code=2`, no post), and the notification
    keeps `Lux 1 → brightness 5`.
- **Explains:** #130's fallback text and its tile/notification disagreement (the tile reads live
  state), and the owner's night sighting. **Does not** show that brightness adjustment stopped.
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
   - the **inner gate**, `BrightnessEngine.evaluate` (`:56-57`), recomputes the band around the
     *previous processed* raw value and skips smoothing (`α = 0`, smoothed retained) inside it.

   Re-feeding the same reading therefore does nothing. After a drop to 10 lx the recomputed band is
   7–13 and the retry is inside it. **Zero has its own trap:** after a 0 lx cycle the stored band is
   0–0.1 (`absoluteThresholds`, `:147`), and later 0 readings fail the outer gate.
3. **The formula doesn't converge to raw either.** α crosses zero where
   `|raw − S|/(S + 1) = dynamicThreshold`. For raw 0 and a threshold of 0.3 that is S ≈ 0.43 lx.
   Rounding can halt progress earlier, and below that point the unclamped α (D-010(a)) goes
   **negative**.
4. **The proximity damp** (`α × 0.1` while "near", `%AAB_Proximity`, `BrightnessEngine.kt:30`) makes
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

- `onSensorSample` drops a reading while `inCycle` is set (`BrightnessPipelineController.kt:217`).
  `runCycle` drops one inside the cooldown (`PipelineCycleRunner.kt:72-74`). Neither keeps it or
  schedules another look.
- If the **final** reading of a change lands in the ≈1.3 s window (cycle 544–557 ms + cooldown
  460–776 ms in the #132 captures) and the light then holds steady, that reading is never
  evaluated.
- **Startup race (Astra):** `start()` registers the sensor before the consumer has loaded
  `cachedSettings`, and `onSensorSample` returns while settings are null (`:205`). The sensor's
  initial event can therefore be lost on every service start.
- **Candidate for 09-23 capture 2.** The last *processed* raw reading was 31.8 lx, and the first
  reading after re-activation was 0 lx. F-B alone cannot produce that. **A hand over the sensor at
  the toggle, or a sensor path that went quiet (H1), explain it equally well.**

### F-D — The health surfaces cannot tell "steady" from "stuck". Confidence: high

- **The dashboard banner** goes STALE after 10 s without a publish (`LiveRuntimeState.kt:17-25`), and
  publishing happens only when pipeline state changes (`AmbientMonitoringService.kt:271`), so steady
  light raises it. **An independent heartbeat would prove only that the heartbeat runs; it must not
  certify sensor delivery or cycle progress (Astra).**
- **The `onTaskRemoved` watchdog** (`armStalenessWatchdog`, `:593`) resets `LiveRuntimeState` after
  5 s without a publish, which steady light satisfies. A running service can then show as stopped on
  the dashboard, tile and widget. The logs don't show this happening; it is a code-level route to
  misleading state.

### H1 — The sensor path goes quiet while still enabled. Open

In both #132 logs the ALS stays enabled at the HAL until the toggle's `Enable = 0`. That means
Tideo's registration was never dropped; it does **not** prove events were delivered through
Tideo's callback and flow. **Neither log contains the stall's onset:** the last samples fall 2–4 min
before each log starts.

## 3. Claims withdrawn during review (do not reintroduce)

- **"Last sample stale ⇒ nothing reached the app."** That holds only while readings keep arriving.
  One rejected *final* reading goes stale in step with the last completed cycle, and both screens
  truncate ages to whole minutes (`LiveDebugScreen.kt:307`, `DashboardScreen.kt:372`).
- **"Raw lux" is the latest received reading.** It is the last *processed* one: `lastRawLux` is
  written when a cycle completes (`PipelineCycleRunner.kt:157`).
- **"Acknowledged writes and normal cycle times rule out a block."** They describe completed past
  work only.
- **"The 8–15 lx band is a threshold bug and passes every sample."** The band is built around the
  previous raw reading by construction (`BrightnessEngine.kt:149-150`), and it passes only readings
  outside it.
- **"Re-feed the last reading until it converges."** Rejected for F-B's reasons 2 and 3.
- **"Capture 1 is fully explained."** It is consistent with F-B, nothing more.
- **"Runtime-only means no parity decision."** A continuation or completion policy changes
  behaviour, wherever the code lives.
- **"The failure is inherited from Tasker."** Unverified; see Q2.

## 4. Owner decisions (mirrored in STATE → Owner queue → Open questions)

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

- [x] **R0 — this plan.**
- [ ] **R1 — F-A, the notification overwrite.** No fork.
  - **Change:** when the pipeline is already running, build the `startForeground` notification
    from current controller state and active context, through the same model mapping the live
    updater uses. Preserve the paused state and its actions. Correct the `MaintenanceWorker`
    comment.
  - **Tests:** a repeated start with an unchanged model and no later sensor event leaves the posted
    text at `Lux … → brightness …`; the paused variant keeps its title and actions; a first start
    (not running) still posts the monitoring text.
- [ ] **R2 — F-C's startup race only.** No fork: it reorders startup and queues nothing. Load
  settings before registering the sensor, or keep the initial reading until they load.
  - **Test:** an initial event delivered before settings resolve is evaluated.
- [ ] **R3 — Diagnostics (Astra), shipped before R5/R6** so a reporter can separate lost delivery,
  a dropped final reading and an unfinished cycle from one screenshot. In Live Debug show:
  - the actual sensor callback: value, accuracy, sensor timestamp and arrival time;
  - collector receipt, plus admitted, deferred and rejected counts with the last rejection reason
    (accuracy, dead band, mutex, cooldown, settings not loaded);
  - the current cycle stage and start time, and the last completed cycle.

  Labels must be string resources: the hardcoded-string ceiling in `HardcodedStringCheckTest` may
  only fall.
- [ ] **R4 — F-D, the health surfaces.** No parity source; the owner approves the wording.
  - Drive the banner from R3's signals, not a heartbeat.
  - Decide whether to clear runtime state by service-instance ownership and lifecycle instead of
    publish recency.
  - **Test:** a running service in steady light past 5 s after `onTaskRemoved` keeps its state.
- [ ] **R5 — F-C, the pending-latest slot.** **BLOCKED on Q1**, and on the rule-review pass that
  amends the `AGENTS.md` invariant and records the D-027 departure in a new row.
  - **Behaviour:** new readings replace the pending one during initialisation, an active cycle and
    the cooldown. After completion, the newest pending reading is reconsidered against current
    settings and thresholds. During a cooldown, one reconsideration is scheduled for when it
    expires. Pause, override and proximity behaviour are preserved. Pending work is invalidated on
    screen-off, stop and a new sensor session. An in-progress animation is never cancelled.
  - `.conflate()` upstream is insufficient: the rejections happen downstream of the collector.
  - **Tests:** a final out-of-band reading during an animation, then silence, is evaluated; the
    same during a cooldown.
- [ ] **R6 — F-B, the settling path.** **BLOCKED on Q2.**
  - **Behaviour:** separate "has a sufficiently different reading arrived?" from "has the accepted
    transition finished?". Continuation bypasses both input gates and keeps pause, override,
    lifecycle and proximity behaviour. It completes when the brightness target for the accepted
    stable lux is reached. Repeated calls to the adaptive-α formula are not a completion policy.
  - **Tests:** a single drop followed by silence finishes the brightness transition, for a zero and
    a non-zero destination. The same with proximity near. No timer is left re-evaluating an
    unchanged result. "Smoothed reaches raw within N cooldowns" is **not** a valid expectation.
- [ ] **R7 — close-out.** Write ledger rows for what shipped, answering Q1 and Q2 in rows (never
  citing this path), then delete this file.

## 6. Evidence still wanted from #132 (ask only if the owner chooses to)

- Before any R5/R6 release, a build with R3. Then, during a stall and **before toggling**: a Live
  Debug screenshot, `adb shell dumpsys sensorservice`, and `adb logcat -d` with `adb logcat -G 16M`
  set beforehand so the onset is captured.
- Missing recent ALS events in `dumpsys` is **not** enough on its own to name a firmware fault;
  R3's callback-arrival counters are what separate H1 from F-C.
