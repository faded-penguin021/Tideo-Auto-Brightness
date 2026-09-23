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
> were corrected, and its withdrawn claims joined §3.

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
   - the **inner gate**, `BrightnessEngine.evaluate` (`:56-58`), recomputes the band around the
     *previous processed* raw value and skips smoothing (`α = 0`, smoothed retained) inside it.

   Re-feeding the same reading therefore does nothing. After a drop to 10 lx the recomputed band is
   7–13 and the retry is inside it. **This inner gate is Tideo's divergence, not Tasker's; see F-E.** **Zero has its own trap:** after a 0 lx cycle the stored band is
   0–0.1 (`absoluteThresholds`, `:147`), and later 0 readings fail the outer gate.
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
   *previous* reading. **Seen in the field:** 09-23 capture 2 shows 8–15 lx, which is the previous
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

### F-F — A low-accuracy reading is rejected, and recovered accuracy never re-admits it. Confidence: medium as a mechanism; attribution open

- prof760's first stage drops any reading with `accuracy ≤ 1` unless "Trust low-accuracy sensor"
  is on (`ProfileGates.kt:20`, `BrightnessPipelineController.kt:207-208`).
- `LightSensorSource`'s `onAccuracyChanged` is a no-op (`LightSensorSource.kt:39`). On an on-change
  sensor, accuracy can recover without the value changing, so no new event arrives and the rejected
  reading is never evaluated. A toggle re-registers the sensor and gets a fresh initial event,
  which matches #132's "toggle fixes it".
- It fits the Pixel's AOC reporting "Device appears to be covered" at each re-activation in both
  logs, **if** the sensor marks those readings low-accuracy. Neither log records accuracy.
- **Asked on #132 (owner, 2026-09-23): does enabling the setting stop the freezes?** This answer
  decides the shape of the fix (§5, gate before R6).

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
- **"α ≥ 0 on every smoothed row" as a parity test.** Tasker's task535 subtracts the previous
  cycle's stored threshold, so faithful parity can yield a small negative α (F-E caveat).

## 4. Owner decisions — both answered 2026-09-23

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

**Gate before R6 — the #132 accuracy answer (F-F).** R1, R2, R3 and R5 stand whatever the reporter
says: they fix confirmed defects or restore parity. R3 must count accuracy rejections as their
own reason either way.

- **If the setting stops the freezes:** F-F is #132's cause. The fix is a small one: re-admit the
  last rejected reading when `onAccuracyChanged` reports accuracy > 1. That is a new policy with its
  own row and test. Re-scope R6 and R7 before starting them: they would fix real mechanisms that
  #132 no longer demonstrates, and their cost (the D-027 departure, a new settling policy) needs a
  fresh case. R4 stays.
- **If it does not:** F-F is ruled out for #132, and R6 and R7 proceed as written.
- **No answer:** R6 and R7 wait for R3's diagnostics from the reporter instead.

- [x] **R0 — this plan.**
- [ ] **R1 — F-A, the notification overwrite.** No fork.
  - **Change:** when the pipeline is already running, build the `startForeground` notification
    from current controller state and active context, through the same model mapping the live
    updater uses. Preserve the paused state and its actions. Correct the `MaintenanceWorker`
    comment.
  - **Tests:** a repeated start with an unchanged model and no later sensor event leaves the posted
    text at `Lux … → brightness …` and keeps the active-context subtext (`:528`); the paused
    variant keeps its title and actions; a first start (not running) still posts the monitoring
    text.
- [ ] **R2 — F-C's startup race only.** No fork: it reorders startup and queues nothing. Register
  the sensor only after `cachedSettings` has loaded. Holding an early reading until settings load
  would be deferred work, which belongs to R6 and its D-027 rule review, so R2 does not do it (Sol).
  - **Tests:** registration happens after settings resolve, and the first event after
    registration is evaluated.
- [ ] **R3 — Diagnostics (Astra), shipped before R5–R7** so a reporter can separate lost delivery,
  a dropped final reading and an unfinished cycle from one screenshot. In Live Debug show:
  - the actual sensor callback: value, accuracy, sensor timestamp and arrival time;
  - collector receipt, plus admitted, deferred and rejected counts with the last rejection reason
    (accuracy, dead band, mutex, cooldown, settings not loaded), and the last `onAccuracyChanged`
    value and time (F-F);
  - the current cycle stage and start time, and the last completed cycle.

  Labels must be string resources: the hardcoded-string ceiling in `HardcodedStringCheckTest` may
  only fall. **R5 and R6 each revise this taxonomy (Sol):** R5 adds a distinct act19 stop, and R6
  turns busy and cooldown drops into deferred or replaced work. Each of those segments states which
  counter a reading now lands in, so R4's banner never reads an ambiguous signal.
- [ ] **R4 — F-D, the health surfaces.** No parity source; the owner approves the wording.
  - Drive the banner from R3's signals, not a heartbeat.
  - Decide whether to clear runtime state by service-instance ownership and lifecycle instead of
    publish recency.
  - **Tests:** a running service in steady light past 5 s after `onTaskRemoved` keeps its state; the
    banner distinguishes steady delivery, absent callbacks, rejected callbacks and an unfinished
    cycle, with one test each.
- [ ] **R5 — F-E, restore Tasker's dead-band.** This restores parity, so it needs no fork (playbook
  2/4). It does change behaviour users know, so it gets a `changelogs/25.txt` line.
  - **Change:** in `BrightnessEngine.evaluate`, add task544 act19 (`relative_change` against
    smoothed lux, stopping below `dynamic_threshold`) before smoothing, and centre
    `absoluteThresholds` on the **current** reading as task554 act1 rounds it (three decimals,
    HALF_UP), not on an unrounded `input.lux`. Keep act20's and act35's differing `par1` for the
    `< 0.2` special case and the rounding scale.
  - **The act19 stop is its own result, not a zero-α one (Sol).** Tasker's act19 path runs act20
    (the band from `%par1`), clears the cycle and stops (acts 20–23): no smoothing, no mapping, no
    brightness or dimming work. `evaluate` must return a result that `PipelineCycleRunner` can tell
    apart. For it, the runner stores act20's band and skips mapping, animation, throttle updates
    and accepted-state publication (`PipelineCycleRunner.kt:80`, `:139`, `:148`).
  - **Tests:** a reference oracle for the act18–act35 orchestration in `TaskerReference`, with
    contract rows for:
    - a return to the previous level after A → B is processed;
    - a reading below act19's threshold making no smoothing call **and no mapping, write or
      accepted-state publish**;
    - every smoothed row's α matching the oracle, sign included (a negative α from a threshold that
      moved between cycles is parity; F-E caveat);
    - the stored band centred on the rounded processed reading.
  - **Record:** a `parity_gaps.md` entry; the `PARITY_CHECKLIST.md` rows for task544, task546 and
    task554, which read `ported` today (playbook 2); and a row that amends D-039(a)'s "engine is
    the oracle" premise.
  - **Order:** land before R6 and R7. It changes both of their contracts: R6's tests must run
    against the corrected gates, and R5 does not supply R7's continuation (F-E item 4).
- [ ] **R6 — F-C, the pending-latest slot.** Q1 is answered. It still needs the rule-review pass
  that amends the `AGENTS.md` invariant and records the D-027 departure in a new row.
  - **The owner's no-ghosts condition, as acceptance tests:**
    - there is exactly one slot, and a newer reading replaces it; nothing queues;
    - a pending reading is evaluated at most one cycle plus one cooldown after it arrived (the
      response is never later than that);
    - a flicker run (alternating bright and dim faster than the cooldown for 30 s, then steady)
      runs no more cycles than today's drop policy plus one, and its last cycle evaluates the final
      reading. Ending on the final light's *target* needs R7's settling, so R7 asserts that (Sol).
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
    - a final out-of-band reading during an animation, then silence, is evaluated;
    - the same during a cooldown;
    - after each of a queued screen-off, pause, override, stop and sensor-session replacement, the
      pending reading is not evaluated.
- [ ] **R7 — F-B, the settling path.** Q2 is answered; the Tasker check comes first. Re-derive the
  continuation on top of R5. act19 gives a defined endpoint only for readings that reach task544,
  and prof760 keeps an unchanged reading out (F-E item 4). Any continuation is therefore a new
  policy with its own row, whatever the Tasker check finds. The acceptance criteria below stand
  either way, and once R7 lands R6's flicker run must also end on the final light's target.
  - **Behaviour:** separate "has a sufficiently different reading arrived?" from "has the accepted
    transition finished?". Continuation bypasses both input gates and keeps pause, override,
    lifecycle and proximity behaviour. It completes when the brightness target for the accepted
    stable lux is reached. Repeated calls to the adaptive-α formula are not a completion policy.
  - **Tests:** a single drop followed by silence finishes the brightness transition, for a zero and
    a non-zero destination. The same with proximity near. No timer is left re-evaluating an
    unchanged result. "Smoothed reaches raw within N cooldowns" is **not** a valid expectation.
- [ ] **R8 — close-out.** Write ledger rows for what shipped, answering Q1 and Q2 in rows (never
  citing this path), then delete this file.

## 6. Evidence still wanted from #132 (ask only if the owner chooses to)

- Before any R5–R7 release, a build with R3. Then, during a stall and **before toggling**: a Live
  Debug screenshot, `adb shell dumpsys sensorservice`, and `adb logcat -d` with `adb logcat -G 16M`
  set beforehand so the onset is captured.
- Missing recent ALS events in `dumpsys` is **not** enough on its own to name a firmware fault;
  R3's callback-arrival counters are what separate H1 from F-C.
