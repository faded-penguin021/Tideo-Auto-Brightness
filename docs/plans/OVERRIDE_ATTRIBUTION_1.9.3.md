# Override-attribution hardening (issues #126 / #127)

## Context

Two reports from the same user (Oppo A6 Pro / ColorOS, Android 16, Tideo 1.9.2, ELEVATED):

- **#126** — repeated bright → covered → bright light swings near the top of the curve eventually pause
  the pipeline with "Manual Override Detected". Disabling override detection makes it stop entirely.
  Reporter's device max raw `screen_brightness` is **3083**; anim steps 50, max wait 30.
- **#127** — after screen-off, screen-on and unlock, the pipeline is paused. Closed as a duplicate of
  #126; the *class* is shared but the trigger differs, and the wake path has its own defects.

This is the third visit to the same system: D-049 (#1 and #4 fixed, **#2 and #3 knowingly left open**),
D-126, and DB-082 (issue #123). DB-082's own row already names the remaining weakness — *"the
single-latest self-write marker is still the weak part, and a grace window around our own writes
remains the deeper fix."*

**The load-bearing observation.** Every pause commits in `PipelineCycleRunner.handleOverride`
(`app/…/runtime/PipelineCycleRunner.kt:241`), which pauses only when the settled read differs from
`lastAppliedBrightness`. So an internal echo race alone cannot pause the pipeline while that reference
is fresh — a **real foreign write** to `Settings.System.SCREEN_BRIGHTNESS` must exist. Tideo cannot
observe *who* wrote it, and no user report can supply that. The fix must therefore be
attribution-independent: make the app's record of its own writes reflect what Android actually stored,
and stop treating the small set of changes we can explain as user input.

Root cause confidence: MODERATE-HIGH (a foreign writer is proven by the code path; its identity —
OEM clamp, adaptive-mode re-assert, or Android 12+ int↔float `BrightnessSynchronizer` drift — is not).
Fix confidence: HIGH. Each change below is a **no-op on a device that behaves**: acknowledged == requested,
mode stays MANUAL, drift is zero.

Intended outcome: 1.9.3 (vc24, unreleased on this branch) stops false-pausing on this class of device,
and records enough diagnostics that the next such report identifies the writer without an adb session.

## Out of scope / rejected

- **`ACTION_USER_PRESENT` + keyguard hold.** Rejected by the owner (2026-08-30): keying wake behaviour
  on unlock changes behaviour for every user, and a lock-screen glance in the dark must be handled
  correctly without requiring a touch. Do not reintroduce. Everything below is value- and state-based,
  never keyed to unlock.
- **Raising the settle window to a larger fixed number** (1500 → 3000/5000), or a blanket time-based
  grace period after every write: creates a period where a genuine slider move is undetectable, with no
  principle deciding its length. Change 6 derives the window instead, and floors it at today's value.
- **Wake baseline adoption** ("nothing of ours is on screen after a wake, so adopt the first observed
  value"). Tempting, but it inverts the existing, deliberate
  `genuineOverrideAfterTheWakeWindow_stillPauses_DB082` test — a slider move on the lock screen is a
  real thing to detect. Leave that behaviour alone.
- **A recent-write token set** (D-034/D-051(d) rejected it for four enumerated defects). Single-latest
  stays; only its *meaning* changes, from intent to acknowledgement.
- **Auto-learning the effective device max** when writes come back clamped. Diagnose it (below), don't
  self-tune — a feedback loop against an OEM clamp is worse than the bug.
- No golden-vector change, and no change to the curve or threshold mathematics. `:domain` is touched in
  exactly one place, change 5's `OverrideRules.shouldCommitPause` signature — decision logic belongs
  there (RUNBOOK "Where logic lives"), and no golden vector covers that function.

## Changes

### 1. Transactional write with an acknowledged marker — `platform/…/brightness/ScreenBrightnessController.kt`

Today `write()` assigns `lastSelfWriteDevice` **after** `putInt` returns (`:71`), and `isSelfWrite`
demands exact equality with the requested value. Two defects: the observer callback can be dispatched
before the marker exists, and an OEM that clamps/quantizes/normalizes the stored value makes every one
of our own writes look external.

- Arm `lastSelfWriteDevice = requested` **before** `putInt`; restore the previous value if the write is
  refused (`putInt` returns `false`) or throws `SecurityException` (keep the existing rethrow for other
  throwables).
- After a successful write, re-read the key and store the **acknowledged** raw value as the marker.
- Add `@Volatile private var selfWriteInProgress` (it is read from the observer thread — the existing
  `lastSelfWriteDevice` carries `@Volatile` for the same reason) and make
  `isSelfWrite(raw) = selfWriteInProgress || raw == lastSelfWriteDevice`.
- Add `fun lastAcknowledgedWrite(): Int?` to the interface, returning the acknowledged value **converted
  to domain space** via the existing `toDomain`.
- Delete `isOnScreenSelfWrite()` from the interface and the implementation: it has **no production
  caller** (only test doubles implement it) — it is the exact-match detector the D-055 band check
  replaced. D-049 stays `[cited]` via `AnimationRunner` and `PipelineCycleRunner`.

Known limits to record in the ledger row, not to fix here: the read-back only catches **synchronous**
provider-side normalization; an OEM (or `BrightnessSynchronizer`) that re-writes the key milliseconds
later is caught by change 4, not this. Cost is one extra binder read per frame (50 per sweep); this repo
has a standing precedent against per-sample IPC (`AndroidPanicSensorSource` avoids `power.isInteractive`
per sample). Take the simple version first; if the ladder or a device shows animation cost, restrict the
read-back to the final frame and `setInitialBrightness` — intermediate frames are already gated out of
the observer path by `autoRunning`.

### 2. Store what Android accepted — `app/…/runtime/PipelineCycleRunner.kt`

`lastAppliedBrightness` is set from `target` (intent) at `:146` and `:288`. Set it from
`brightness.lastAcknowledgedWrite()`, falling back to `target` when null (write refused / no write).
This is what makes the settle comparison at `:251` refer to reality. It also feeds Live Debug's
"Current brightness" and the dashboard/widget readouts — acknowledged is the more honest value there
too, and stays consistent with D-109 (`targetBrightness` remains the perceived, un-floored value).

### 3. Settle-delay fallback — `PipelineCycleRunner.kt:244`

`settleMs` is `cycleTimeMs ?: 0`, and `hibernate()` nulls `cycleTimeMs` — so on the wake path, the one
with the weakest reference, the pause commits with **no re-read delay at all**. D-049 #1 specified
"≈ `cycleTimeMs`, **fallback to throttle**"; the fallback was never implemented. Use
`throttle.throttleMs` (already injected into the runner) when `cycleTimeMs` is null, floored to a small
non-zero minimum so the re-read always happens.

### 4. Tolerance at the commit guard — `PipelineCycleRunner.kt:251`

`settled == s2.lastAppliedBrightness` is exact in domain space. At the reporter's device max, one domain
step ≈ 12 raw units, so any asynchronous round-trip that crosses a domain boundary pauses the pipeline.
Compare with **±1 domain unit**. A genuine slider move is far larger than 1/255; this is a value
tolerance, not a time window, so it cannot swallow a real override.

### 5. Mode-aware attribution — controller + `handleOverride`

Add `fun isManualMode(): Boolean` to `ScreenBrightnessController` (reads
`Settings.System.SCREEN_BRIGHTNESS_MODE`; no permission needed). In `handleOverride`, before committing:
if the mode is **not** MANUAL, the framework's adaptive controller wrote the value, not the user — call
`brightness.forceManualMode()`, record the diagnostic (change 7), and return without pausing. The next
cycle re-establishes our brightness normally.

**The decision belongs in `:domain`, the settings read in `:platform`.** Its sibling conditions all live
in `OverrideRules.shouldCommitPause` (`domain/…/brightness/OverrideRules.kt:26-37`, pure booleans), and
RUNBOOK "Where logic lives" puts decision logic there. Extend that function with an `isManualMode`
parameter and give it a `:domain` unit test; only the `Settings.System.SCREEN_BRIGHTNESS_MODE` read is
platform work. No golden vector covers `OverrideRules`, so the fence holds.

This is the owner's own #127 hypothesis made actionable, and it is behaviour-neutral wherever the mode
stays MANUAL. It is a **deviation from Tasker** (task567/prof755 do not consult the mode). This is a bug
fix, so playbook **4** governs the record: `STATE.md` **and `docs/rebuild/parity_gaps.md`**, whose home
for deviations is the doc index. `STATE.md` "Current state" currently asserts *"parity checklist and
parity gaps empty"* — that sentence becomes false with this change and must be updated in the same unit.
Note also that the app already force-flips to MANUAL every cycle (`PipelineCycleRunner.kt:93`), so this
does not create a new claim over the mode.

### 6. Variable settle window — `PipelineCycleRunner.INITIAL_SETTLE_MS`, `BrightnessPipelineController.onScreenOn`

`INITIAL_SETTLE_MS = 1500` is **port-invented, not Tasker parity**: D-054 cites `task696` L49-56/L126-134
for the band and `task567` act7's `Wait %AAB_CycleTime` for the settle, and no Tasker constant of 1500
exists anywhere in the ledgers or the rebuild docs. Tasker's settle quantities here are *variables*
(`%AAB_CycleTime`, `%AAB_Throttle`), so a derived window is closer to the source than the constant is.

Derive it from the configured transition length, **reusing the existing
`ThrottleController.ceiling(animSteps, maxWaitMs)`** (`= animSteps × maxWaitMs + 10`, already injected
into the runner) rather than adding a second near-identical formula — two copies of one constant is
exactly the drift AGENTS.md warns about. Bound it:

```
settleMs = throttle.ceiling(animSteps, maxWaitMs).coerceIn(1500, 10_000)
```

Bounds are not optional. `animSteps` is `0..100` and `maxWaitMs` is `1..5000` (`AabSettings.kt:132-133`),
so the raw ceiling ranges from ~21 ms to **8.3 minutes** — unbounded, it would either collapse the
window below what the wake path needs or silently disable override detection for minutes. The floor of
1500 keeps today's device-verified DB-082 behaviour as the minimum, so this can only *lengthen* the
window, never regress it. Sanity: the reporter's 50 × 30 = 1500 and the defaults' 20 × 65 = 1300 both
land at the floor, so realistic configs are unchanged; a slow 100 × 65 profile gets 6.5 s instead of 1.5 s.

Two implementation notes:

- `onScreenOn()` runs on the receiver thread and arms *before* DataStore is read (that ordering is
  DB-082's fix — do not disturb it), so it only has `cachedSettings`, which is null until the first
  settings load. Fall back to the floor when null.
- Add one helper so the two arming sites cannot drift, and update the "one number, one place" comment at
  `PipelineCycleRunner.kt:309` — the floor constant now holds that role.

Keep this quantity distinct from change 3: the settle **window** is a transition-length ceiling
(`throttle.ceiling`), while `handleOverride`'s settle **delay** is the measured cycle duration
(`cycleTimeMs`, Tasker `%AAB_CycleTime`, falling back to `throttle.throttleMs`). Do not merge them.

### 7. Diagnostics — `PipelineState.kt`, `LiveDebugScreen.kt`

The app currently pauses without recording anything about why. Add nullable fields to `PipelineState`
(observed value, expected/acknowledged value, brightness mode at detection, and which path fired —
observer vs in-animation band), set them where an override is detected and where one is dismissed by
changes 4/5, and surface them in Live Debug as a new `DiagnosticCard` beside "System Status". Also
surface **requested vs acknowledged** for the last write: if they diverge at the top of the range, the
reporter's device max disagrees with `config_screenBrightnessSettingMaximum` and the top of their curve
is silently flat — that is worth seeing even though we are not fixing it here.

**Do not add a `DebugCategory`.** `%AAB_Debug` is a fixed Tasker-parity set: `AabSettings.kt:150` pins
`range 0..9` and the label array in `strings.xml` matches. A tenth level would be a parity change for a
diagnostic that belongs on-screen.

## Tests

Adding two interface members breaks every double; update all six:
`platform/…/ScreenBrightnessControllerTest.kt`, `platform/…/observe/BrightnessObserverTest.kt`,
`app/…/runtime/{BrightnessPipelineControllerTest, AnimationRunnerTest, PanicHandlerTest,
ControlFloodBoundTest}.kt`.

**A test seam is required.** Robolectric's settings provider stores values verbatim, so "the OEM
normalizes our write" is unreachable without one. Add a narrow injectable read/write seam to
`AndroidScreenBrightnessController` in the style of the existing `deviceMaxOverride` constructor
parameter; without it the headline regression test cannot exist.

New coverage:
1. A normalized write (requested 3212 → stored 3083) leaves a marker matching the **stored** value, so
   the observer callback is a self-write, not an override.
2. An observer callback arriving while `putInt` is in flight is a self-write (`selfWriteInProgress`).
3. `lastAppliedBrightness` equals the acknowledged value, not the requested one.
4. A 50-frame bright → dark → bright sequence with normalized writes does not pause. (#126)
5. A settled value within ±1 domain of the applied value does not pause; a genuine external write after
   an animation still does.
6. `SCREEN_BRIGHTNESS_MODE != MANUAL` at commit → no pause, and `forceManualMode()` is called; mode
   MANUAL with the same write → still pauses. (#127)
7. The wake path re-reads before committing (settle fallback) instead of committing instantly.
8. The settle window derives from settings: a slow profile (100 × 65) gets ~6.5 s, a fast one is floored
   at 1500, the extreme (100 × 5000) is capped at 10 s, and `onScreenOn` with no cached settings uses
   the floor.

Must keep passing unchanged: `frameworkWriteOnWake_isNotAnOverride_DB082`,
`genuineOverrideAfterTheWakeWindow_stillPauses_DB082`, `rapidLightChange_doesNotFalsePause_butRealOverrideDoes`,
`cycleDuringSettleWindow_suppressesInAnimationOverrideDetection_D126`, and
`isSelfWrite_{matchesLastWrite_repeatable, tracksLatestWriteOnly, unknownValue_returnsFalse}`.

## Verification

- `scripts/ladder.sh` green (build + JVM/Robolectric + guards). No emulator here: **nothing about the
  OEM's actual normalization is verifiable locally** — say so in the commit body.
- **Glue review is mandatory and pre-authorized by the owner** (RUNBOOK "Glue-review protocol",
  DA-003): a fresh-context adversarial reviewer over the full diff, blocking, after the ladder is green,
  before commit. Triage every finding. This diff is squarely in its target classes — observer/echo
  races, round-trip drift across range normalization, non-idempotent lifecycle state.
- **Device checks for the Owner queue** — the two pass/fail ones use *injected* triggers so they can fail on a device that
  never shows the bug (DB-083), and mirrored into `docs/rebuild/DEVICE_TEST_SCRIPT.md`:
  1. `adb shell settings put system screen_brightness <applied ±1 domain>` → must NOT pause;
     the same write far from the applied value → MUST pause.
  2. `adb shell settings put system screen_brightness_mode 1`, then a brightness write → must NOT
     pause and the app must flip the mode back to 0; with mode 0, the same write → MUST pause.
  3. Read requested vs acknowledged in Live Debug at the top of the curve (diagnostic, not pass/fail).

## Recording

- **Branch:** work on the branch your session directive assigns, cut from this one —
  `git checkout -B <your-branch> origin/claude/override-detection-race-condition-mli9ia` (branch-train,
  DA-002). Confirm that branch still exists first (`git ls-remote --heads origin`); superseded branches
  get deleted. Never push to `main`.
- **STATE segment checklist first.** This plan is multi-unit, so Session discipline 5 applies: before
  starting, mirror a segment checklist in `STATE.md` `## Active work` (create the section) so a session
  that dies mid-execution leaves something resumable. `STATE.md` has ~49 bytes of headroom under the
  DA-004 soft cap, so adding it trips the WARN and owes ONE deep compression pass to **both** floors
  (`STATE_COMPRESS_TO_KB` and `STATE_COMPRESS_TO_SENTENCES`) — budget for that pass, don't shave words.
- **Version:** re-run RUNBOOK §6's semver decision once the diff exists rather than assuming the
  2026-08-26 patch call carries over — it covered the Graph Metrics fix, and this adds a user-facing
  Live Debug card plus a behaviour change on the pause path. §6 says pick the highest category that
  applies, and minor-vs-patch is an owner fork (discipline 7(c)): if it reads as minor, record it in
  `STATE.md` **Owner queue → Open questions**, dated, with options and a recommendation. Either way
  extend `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
- **Ledger:** append to `docs/LEDGER_C.md` (live volume) and cite the row ID you actually appended —
  take the next free `DC-NNN`; do not pre-assume one, since a sibling branch in the train may have taken
  it. The material below is more than one row's worth: `LEDGER_ROW_SENTENCE_CAP` and
  `LEDGER_ROW_CHAR_CAP` in `amh.conf` are hard ladder FAILs, so split it across rows rather than
  overrunning one. Cover: the load-bearing observation; why single-latest was kept but re-defined from
  intent to acknowledgement; the synchronous-only limit of the read-back; the Tasker deviation in
  change 5; that `INITIAL_SETTLE_MS` was port-invented rather than parity, with the bounds and why each
  is needed; and the USER_PRESENT rejection with the owner's reasoning. Cite the row(s) from the touched
  source with one-line pointers and add `[cited]`.
- `docs/STATE.md`: one Changelog line, the corrected "parity gaps" sentence (change 5), and the two
  device-check queue items in plain language with their commands.
- **Do not post to the tracker.** STATE's Owner queue item 3 stands: issues get no reply without the
  owner saying so.
