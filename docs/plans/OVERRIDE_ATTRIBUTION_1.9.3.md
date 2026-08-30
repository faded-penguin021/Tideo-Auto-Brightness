# Override-attribution hardening (issues #126 / #127)

> **Revision 2 (2026-08-30)** — external review of revision 1, triaged against the source. Accepted:
> the animation-band path was unhardened (new **change 1b**, and the #126 test rewritten around it), the
> acknowledgement must be a per-write result rather than a global getter, **change 6 is withdrawn**, and
> several certainty claims were overstated (the "a foreign write must exist" observation was simply
> false — a second self-inflicted path, the observer route, is documented alongside it). Accepted with
> a corrected rationale: the failed-write case (recording the *previous* value is truthful, not a
> defect; the real fault is the getter's three-way ambiguity) and the mode-recovery failure handling.
> Not accepted as stated: nothing — every finding held up, one only after re-deriving why.

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

**The load-bearing observation (corrected 2026-08-30 by review).** Every pause commits in
`PipelineCycleRunner.handleOverride` (`app/…/runtime/PipelineCycleRunner.kt:241`), which pauses only
when the settled read differs from `lastAppliedBrightness`. What that proves is narrower than this
plan's first draft claimed: **at commit time the settled brightness differed from Tideo's own recorded
baseline.** It does *not* prove a foreign writer. `lastAppliedBrightness` is recorded from *intent*
(`target`, `:146`/`:288`), so it can be stale, or can name a value that never landed. Two
foreign-writer-free paths reach a pause on a device that normalizes our writes:

- **Observer route.** A write of domain 250 is stored lower (OEM clamp/quantization). The echo carries
  the *stored* raw value, which does not equal the *requested* marker, so `AndroidBrightnessObserver`
  (`:31`) forwards it. Mid-animation `autoRunning` drops it, but the final frame's echo can be
  dispatched after `runCycle`'s `finally` clears `autoRunning`. `handleOverride` then compares the
  stored value against `lastAppliedBrightness = 250`, which never landed, and pauses. **Changes 1+2
  close this one.**
- **Animation-band route.** `AnimationRunner` reads brightness itself; it consults neither the observer
  nor the self-write marker. If normalization moves the stored value more than the ±2 band tolerance
  out of the sweep band on two consecutive frames (`AnimationRunner.kt:37-48`) it returns `OVERRIDDEN`;
  `runCycle` posts `OverrideDetected` and **returns before the `ctx.update` that would refresh
  `lastAppliedBrightness`** (`:111-114`), so `handleOverride` compares the settled value against the
  *previous* cycle's baseline and pauses. Changes 1, 2, 4 and 5 do not touch this path — hence **change
  1b**, without which the #126 regression test (test 4) asserts behaviour nothing here guarantees.

The fix stays attribution-independent: make the app's record of its own writes mean what Android
actually stored, make **every** detector consult that record, and stop treating the small set of changes
we can explain as user input. What the app can never establish is *who* wrote a value it cannot explain;
no change below tries to.

Root-cause class confidence: MODERATE-HIGH (a self-inflicted pause is reachable from normalization
alone, on both paths above). Exact mechanism: MODERATE — which normalization or re-assertion the Oppo
performs (OEM clamp, adaptive-mode re-assert, or Android 12+ int↔float `BrightnessSynchronizer` drift)
is not determinable from here; the reporter's hardware is the only way to settle it. Fix-design
confidence: HIGH. Each change below is a **no-op on a device that behaves**: acknowledged == requested,
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
  principle deciding its length. **Deriving** the window from the transition length was proposed as
  change 6 and is now withdrawn for the same reason plus two others — see change 6 for the record and
  for what evidence would reopen it. `INITIAL_SETTLE_MS` stays 1500.
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
- **Return the acknowledgement from the write that produced it — not from a global getter.** Change
  `write(level: Int)` to return a small result (`requestedDomain`, `requestedRaw`, `acknowledgedDomain`,
  `acknowledgedRaw`, `ok`); `acknowledged*` are null when `ok` is false. A getter such as
  `lastAcknowledgedWrite()` cannot distinguish *"the write I just made was refused"* from *"no write has
  happened"* from *"`clearSelfWriteMarker()` ran"* — three states that must not collapse, because
  `handleOverride` and `pauseInternal` both clear the marker. The result also hands change 7 its
  requested-vs-acknowledged pair with no second mutable side channel.
- Delete `isOnScreenSelfWrite()` from the interface and the implementation: it has **no production
  caller** (only test doubles implement it) — it is the exact-match detector D-054's band check replaced
  because it false-fired on OEM round-trip drift. Change 1b needs the acknowledged *value*, which the
  write result carries, so do **not** resurrect this member for it. D-049 stays `[cited]` via
  `AnimationRunner` and `PipelineCycleRunner`.

`selfWriteInProgress` is a real, bounded blind spot and must be described as one, not sold as free:
any brightness change landing between `putInt` and its read-back is classified as ours, a genuine
user or framework write included. That window is microseconds-to-milliseconds rather than the 1.5 s of
a blanket grace period, which is why it is judged acceptable — but the plan must not also claim it
leaves no interval in which a genuine adjustment is undetectable. It does. A boolean is only sound
while **at most one brightness transaction is in flight**: today that holds (all four writers —
`AnimationRunner:42`, `PipelineCycleRunner:97`/`:281`, and `PanicHandler:13` after
`consumerJob.cancelAndJoin()` — run on the serialized consumer, D-027). State that invariant in the
ledger row; if a later caller could break it, serialize `write()` itself rather than widening the flag.

Known limits to record in the ledger row, not to fix here: the read-back only catches **synchronous**
provider-side normalization. An OEM (or `BrightnessSynchronizer`) that re-writes the key milliseconds
later is absorbed by change 4 **only while the drift stays within ±1 domain unit**; a larger
asynchronous clamp still reads as external, on both detector paths, and will still pause. That is the
correct outcome for a genuinely ambiguous event — but say so rather than implying change 4 covers
asynchronous normalization in general. Cost is one extra binder read per frame (50 per sweep, on top of
the band read `AnimationRunner` already does); this repo has a standing precedent against per-sample IPC
(`AndroidPanicSensorSource` avoids `power.isInteractive` per sample). Take the simple version first. If
a device shows animation cost, do **not** fall back to "read back only the final frame" — change 1b
needs a per-frame acknowledgement; fold the two reads instead, since frame *i+1*'s band read is already
a settled read of frame *i*'s write.

### 1b. Acknowledged-write awareness in the animation detector — `app/…/runtime/AnimationRunner.kt`

Change 1's marker is consulted only by `BrightnessObserver`. `AnimationRunner` is a second, independent
detector that reads brightness directly and can abort a sweep — and, per the corrected observation
above, that abort alone pauses the pipeline with no foreign writer involved. Harden it the same way:

- `write()` now returns the acknowledgement; keep the latest one in a local `var` across frames (no new
  interface member, no global getter).
- `isOutOfBand` becomes: out of band **and** the read does not equal the latest acknowledged domain
  value. A value Android told us it stored for our own frame is not an override even when it falls
  outside the nominal sweep band — which is exactly the normalized-write case.
- This does **not** reinstate the D-054 defect. The band+2-read debounce stays authoritative for
  wrong-direction and overshoot; the acknowledgement is an additional *in-band* condition (OR), not a
  replacement for the band as the old exact-match detector was.
- Independently, `runCycle`'s `OVERRIDDEN` early return (`:111-114`) must record the last acknowledged
  value into `lastAppliedBrightness` before posting, so `handleOverride` compares against what is
  actually on screen instead of the previous cycle's stale baseline. A genuine mid-animation override
  still pauses (settled = the foreign value ≠ acknowledged); a normalization no longer does. Keep both
  halves: the first stops the false abort, the second stops a false abort from becoming a false pause.

### 2. Store what Android accepted — `app/…/runtime/PipelineCycleRunner.kt`

`lastAppliedBrightness` is set from `target` (intent) at `:146` and `:288`. Set it from the
acknowledgement carried by change 1's write result. **On a failed write, leave `lastAppliedBrightness`
unchanged** — do not fall back to `target`, which asserts a value that never landed, and do not adopt
a stale acknowledgement as if it were this write's. The previous value is the truthful one when the
write was refused, because nothing moved. This is what makes the settle comparison at `:251` refer to
reality. It also feeds Live Debug's
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
Compare with **±1 domain unit**.

Define the semantics rather than calling it a no-op: **one domain step is treated as representational
drift; a persistent two-step deviation stays detectable.** This is a deliberate deadband, not a
formality — it is false that a genuine slider move is always larger than 1/255. A user *can* make a
one-step adjustment, and on a 3083-raw device the system slider's own granularity is far finer than one
domain unit, so the smallest real moves become invisible. The trade is accepted because a 1/255 change
is imperceptible, and because it is a *value* tolerance rather than a time window: it cannot swallow a
real override the way a grace period can. There is precedent — the animation detector already tolerates
a wider ±2 band plus two consecutive failures. Pin the boundary in tests: ±1 dismissed, ±2 pauses.

### 5. Mode-aware attribution — controller + `handleOverride`

Add `fun isManualMode(): Boolean` to `ScreenBrightnessController` (reads
`Settings.System.SCREEN_BRIGHTNESS_MODE`; no permission needed). In `handleOverride`, before committing:
if the mode is **not** MANUAL, call `brightness.forceManualMode()`, record the diagnostic (change 7),
and return without pausing. The next cycle re-establishes our brightness normally.

**State the claim at its real strength.** A non-MANUAL mode proves only that *Tideo's expected mode
invariant is broken* — not that "the framework's adaptive controller wrote the value, not the user."
The user may have deliberately enabled adaptive brightness, ColorOS may have restored it, or something
else may have changed it. So the rule is: *if the mode is no longer MANUAL at commit time, the event is
ambiguous, because Tideo no longer owns the display mode it writes against — restore MANUAL and dismiss
this override attempt rather than label it user input.* Same code, honest justification.

`forceManualMode()` currently swallows `SecurityException` and reports nothing. Give it a `Boolean`
result: dismissing an override on the strength of a mode recovery that silently failed would leave the
app knowingly running in the conflicting state. **When the recovery fails, still do not pause** — record
the disposition as an unrecovered mode conflict (change 7) and surface it. Pausing would label the event
"Manual Override Detected", which is precisely the misattribution this change exists to stop, and a
failed mode write means `WRITE_SETTINGS` is gone, so the brightness writes are failing too and the
pipeline is already inert. Flag this as the owner-visible call it is.

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
Correction to this plan's first draft: the app does **not** force-flip to MANUAL every cycle.
`forceManualMode()` at `PipelineCycleRunner.kt:93` sits inside `if (target != from)`, so a cycle that
changes nothing never reclaims the mode; only `setInitialBrightness` (`:280`) does so unconditionally.
Change 5 therefore **does** add a new mode-recovery site — a narrow extension of an existing claim
rather than a fresh one, but record it as an addition, not as behaviour that was already there.

### 6. Variable settle window — WITHDRAWN (2026-08-30, review)

**Do not implement. `INITIAL_SETTLE_MS` stays 1500 in this fix.** The withdrawn proposal was
`settleMs = throttle.ceiling(animSteps, maxWaitMs).coerceIn(1500, 10_000)`, on the argument that 1500 is
port-invented rather than Tasker parity (which it is — D-054 cites `task696` L49-56/L126-134 for the
band and `task567` act7's `Wait %AAB_CycleTime` for the settle; no Tasker constant of 1500 exists in the
ledgers or the rebuild docs). Three reasons it does not belong in *this* change:

- **It fixes nothing here.** `ceiling(50, 30) = 1510`, so the reporter's own configuration gets 10 ms
  more than today. If 1500 ms did not solve #127, 1510 ms is not the fix. The plan itself conceded that
  realistic configurations land on the floor — which means the change is inert exactly where the bug is.
- **The two quantities are unrelated.** `ThrottleController.ceiling` bounds sensor/animation throttling.
  The F64/DB-082 window protects an instantaneous Set Initial Brightness write and wake-time framework
  settling. Deriving one from the other ties a suppression window to a setting that has no bearing on
  how long the framework takes to settle.
- **It regresses paths it was never aimed at.** `setInitialBrightness` is also reached from `resume()`
  and `reapplyProfile()` (`PipelineCycleRunner.kt:51-60`), so a slow profile would turn Resume and every
  context/profile re-apply into 6.5–10 s of override suppression, during which a genuine manual change
  is ignored. That is the blanket grace period this plan explicitly rejects, wearing a duration borrowed
  from an unrelated setting.

Changes 1–5 plus the change-7 diagnostics settle whether anything survives the existing 1500 ms. **What
would reopen this:** #127 still reproducing after those land, *with* a diagnostic record showing the
dismissal arriving after the window closed — that is the evidence a wake-specific adjustment would need,
and it would then be sized to what the record shows rather than to `animSteps × maxWaitMs`. The parity
observation is worth a ledger sentence either way, so the next visit does not re-derive it.

Change 3's quantity is unaffected and stays distinct: `handleOverride`'s settle **delay** is the measured
cycle duration (`cycleTimeMs`, Tasker `%AAB_CycleTime`, falling back to `throttle.throttleMs`).

### 7. Diagnostics — `PipelineState.kt`, `LiveDebugScreen.kt`

The app currently pauses without recording anything about why. Add **one nullable structured record**,
`overrideDiagnostic: OverrideDiagnostic?`, to `PipelineState` — not four or five independent nullable
fields. `PipelineState` is a coherent single-consumer snapshot (D-027); five loose nullables admit
combinations that cannot occur (a mode with no source, a settled value with no disposition) and make the
Live Debug rendering a pile of null checks. One record — `source` (observer / animation-band),
`disposition` (paused / dismissed-tolerance / dismissed-mode / mode-recovery-failed), `observed`,
`settled`, `expected`, `requestedRaw`, `acknowledgedRaw`, `mode`, `timestamp` — cannot hold an
impossible state and renders as a table.

Set it where an override is detected and where one is dismissed by changes 4/5, and surface it in Live
Debug as a new `DiagnosticCard` beside "System Status". `observed` finally gives
`handleOverride(observed: Int)` a use: **that parameter is currently dead** — the function takes it and
never reads it, deciding entirely on the post-settle re-read. Recording both is what makes an
observed↔settled divergence visible instead of invisible. Also surface **requested vs acknowledged** for
the last write: if they diverge at the top of the range, the reporter's device max disagrees with
`config_screenBrightnessSettingMaximum` and the top of their curve is silently flat — worth seeing even
though we are not fixing it here.

Claim only what this delivers. The diagnostics **cannot identify the writer** — nothing in the app can.
They identify the *mechanism class*: requested ≠ acknowledged, mode conflict, which detector fired,
small settled drift, or none of those (a genuine unexplained external write). That is still enough to
route the next report without an adb session, which is the actual goal.

**Do not add a `DebugCategory`.** `%AAB_Debug` is a fixed Tasker-parity set: `AabSettings.kt:150` pins
`range 0..9` and the label array in `strings.xml` matches. A tenth level would be a parity change for a
diagnostic that belongs on-screen.

## Tests

Changing `write()`'s return type, adding `isManualMode()`, and giving `forceManualMode()` a result break
every double; update all six: `platform/…/ScreenBrightnessControllerTest.kt`,
`platform/…/observe/BrightnessObserverTest.kt`, `app/…/runtime/{BrightnessPipelineControllerTest,
AnimationRunnerTest, PanicHandlerTest, ControlFloodBoundTest}.kt`. Deleting `isOnScreenSelfWrite()`
removes an override from four of them.

**A test seam is required.** Robolectric's settings provider stores values verbatim, so "the OEM
normalizes our write" is unreachable without one. Add a narrow injectable read/write seam to
`AndroidScreenBrightnessController` in the style of the existing `deviceMaxOverride` constructor
parameter; without it the headline regression test cannot exist.

New coverage:
1. A normalized write (requested 3212 → stored 3083) leaves a marker matching the **stored** value, so
   the observer callback is a self-write, not an override.
2. An observer callback arriving while `putInt` is in flight is a self-write (`selfWriteInProgress`).
3. `lastAppliedBrightness` equals the acknowledged value, not the requested one.
4. **The #126 headline.** A 50-frame bright → dark → bright sequence with normalized writes does not
   pause — and the normalization must be **deliberately large enough to fall outside the sweep band**,
   not merely a domain unit or two. Without change 1b this test fails, which is the point of writing it
   this way: it is the only test that distinguishes the two detector paths. Assert both halves — no
   `OVERRIDDEN` from `AnimationRunner`, and no pause from `handleOverride`.
5. A settled value within ±1 domain of the applied value does not pause; **±2 does** (change 4's
   deadband boundary, pinned in both directions); a genuine external write after an animation still
   pauses.
6. `SCREEN_BRIGHTNESS_MODE != MANUAL` at commit → no pause, and `forceManualMode()` is called; mode
   MANUAL with the same write → still pauses. (#127) Plus: `forceManualMode()` **failing** → still no
   pause, and the diagnostic records the unrecovered mode conflict.
7. The wake path re-reads before committing (settle fallback) instead of committing instantly.
8. **Asynchronous normalization**, both sides of the line: a late re-write within ±1 domain is absorbed
   (change 4); a late re-write large enough to leave the animation band is *not* absorbed and still
   pauses — assert that explicitly rather than leaving it undefined, since it is the documented limit of
   the synchronous read-back.
9. A refused write (`SecurityException`) leaves `lastAppliedBrightness` at its previous value — neither
   the un-landed `target` nor a stale acknowledgement presented as this write's.

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
  1. `adb shell settings put system screen_brightness <raw>` → must NOT pause at one domain step away;
     the same write far from the applied value → MUST pause. **Mind the coordinate systems:**
     `settings put system screen_brightness` takes **raw device** values, while `lastAppliedBrightness`
     is **domain 0–255**. On the reporter's 3083-scale device one domain step ≈ 12 raw, so writing
     "applied + 1" literally tests nothing. Derive the raw target from `settings get system
     screen_brightness` and add `round(deviceMax / 255)` for one step, `2 ×` that for the must-pause
     side — do not hand the owner a domain number to type into a raw-valued command.
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
  overrunning one. Cover: the corrected load-bearing observation — that a pause proves only a
  divergence from Tideo's own recorded baseline, and that **two** foreign-writer-free paths reach one;
  why single-latest was kept but re-defined from intent to acknowledgement, and why the acknowledgement
  is a per-write result rather than a global getter; the synchronous-only limit of the read-back and the
  `selfWriteInProgress` blind spot with its one-transaction-in-flight invariant; that the ±1 comparison
  is a deliberate deadband with a stated boundary; the Tasker deviation in change 5 and the new
  mode-recovery site; that `INITIAL_SETTLE_MS` is port-invented rather than parity **but was left at
  1500** in this fix, with change 6's withdrawal reasoning and the evidence that would reopen it; and
  the USER_PRESENT rejection with the owner's reasoning. Cite the row(s) from the touched source with
  one-line pointers and add `[cited]`.
- `docs/STATE.md`: one Changelog line, the corrected "parity gaps" sentence (change 5), and the two
  device-check queue items in plain language with their commands.
- **Do not post to the tracker.** STATE's Owner queue item 3 stands: issues get no reply without the
  owner saying so.
