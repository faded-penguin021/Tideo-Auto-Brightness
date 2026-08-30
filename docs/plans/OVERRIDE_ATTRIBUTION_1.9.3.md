# Override-attribution hardening (issues #126 / #127)

> **Revision 3 (2026-08-30)** — second external review, triaged against the source. All six findings
> accepted; none needed an architectural change, all needed specification the executing session would
> otherwise have had to invent. The two blocking ones: `animate()` could not return the acknowledgement
> that change 1b requires `runCycle` to use (fixed — **`AnimationOutcome`**, carrying the acknowledgement
> and the read that actually tripped the detector), and the write transaction left the pre-armed marker
> poisoned on an unexpected throwable (fixed — **every exit is enumerated**, restoration and flag
> clearing happen in `finally`, and *write landed but was never acknowledged* is modelled rather than
> collapsed into "failed"). Also fixed: the detector `source` now travels on `OverrideDetected` instead
> of being guessed at the diagnostic; diagnostics split into a **continuous** `lastBrightnessWrite` and
> an **event-scoped** `overrideDiagnostic`, so requested-vs-acknowledged is readable on a device that
> never fires an override; change 3's floor is an exact constant with a stated meaning; and three claims
> that were still false or overstated are replaced (the blanket "no-op on a device that behaves", change
> 4's "cannot swallow a real override", and the device test's use of the reporter's *observed* maximum
> in place of the `deviceMax` Tideo actually converts with). Revision 1→2 history is in this file's
> commit bodies; it is not repeated here.

## Context

Two reports from the same user (Oppo A6 Pro / ColorOS, Android 16, Tideo 1.9.2, ELEVATED):

- **#126** — repeated bright → covered → bright light swings near the top of the curve eventually pause
  the pipeline with "Manual Override Detected". Disabling override detection makes it stop entirely.
  The largest raw `screen_brightness` the reporter has observed is **3083**; anim steps 50, max wait 30.
  **3083 is a floor on what the provider stores, not Tideo's `deviceMax`.** The controller derives
  `deviceMax` from `config_screenBrightnessSettingMaximum` (`ScreenBrightnessController.kt:29-36`) and
  converts with it in both directions (`:48-58`). A device that advertises a larger maximum than its
  provider will store is exactly the suspected mechanism: if Tideo believes 4095, one domain unit is
  ≈ 16 raw rather than ≈ 12, and the top of the curve is silently flat. Every raw quantity below is
  derived from the advertised `deviceMax`, never from 3083.
- **#127** — after screen-off, screen-on and unlock, the pipeline is paused. Closed as a duplicate of
  #126; the *class* is shared but the trigger differs, and the wake path has its own defects.

This is the third visit to the same system: D-049 (#1 and #4 fixed, **#2 and #3 knowingly left open**),
D-126, and DB-082 (issue #123). DB-082's own row already names the remaining weakness — *"the
single-latest self-write marker is still the weak part, and a grace window around our own writes
remains the deeper fix."*

**The load-bearing observation.** Every pause commits in `PipelineCycleRunner.handleOverride`
(`app/…/runtime/PipelineCycleRunner.kt:241`), which pauses only when the settled read differs from
`lastAppliedBrightness`. What that proves is narrower than this plan's first draft claimed: **at commit
time the settled brightness differed from Tideo's own recorded baseline.** It does *not* prove a foreign
writer. `lastAppliedBrightness` is recorded from *intent* (`target`, `:146`/`:288`), so it can be stale,
or can name a value that never landed. Two foreign-writer-free paths reach a pause on a device that
normalizes our writes:

- **Observer route.** A write of domain 250 is stored lower (OEM clamp/quantization). The echo carries
  the *stored* raw value, which does not equal the *requested* marker, so `AndroidBrightnessObserver`
  (`:31`) forwards it. Mid-animation `autoRunning` drops it, but the final frame's echo can be
  dispatched after `runCycle`'s `finally` clears `autoRunning`. `handleOverride` then compares the
  stored value against `lastAppliedBrightness = 250`, which never landed, and pauses. **Changes 1+2
  close this one.**
- **Animation-band route.** `AnimationRunner` reads brightness itself; it consults neither the observer
  nor the self-write marker. If normalization moves the stored value more than the ±2 band tolerance
  out of the sweep band on two consecutive frames (`AnimationRunner.kt:37-48`) it aborts; `runCycle`
  posts `OverrideDetected` and **returns before the `ctx.update` that would refresh
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
confidence: HIGH.

**What is behaviour-neutral on a well-behaved device, and what is not.** Revision 2 claimed *every*
change was a no-op there; that is false, and the three exceptions are the ones a reviewer would most
want flagged. Neutral when acknowledged == requested and the mode stays MANUAL: **changes 1, 1b, 2 and
5** — same writes, same pauses, at the cost of one extra provider read per write and one settings read
per commit attempt. Not neutral: **change 3** adds a settle delay on the wake path where today there is
none; **change 4** deliberately makes a persistent one-domain-unit deviation undetectable; **change 7**
adds state fields, an event field and a Live Debug card. Each is argued on its own merits below rather
than smuggled in as a no-op.

Intended outcome: 1.9.3 (vc24, unreleased on this branch) stops false-pausing on this class of device,
and records enough diagnostics that the next such report identifies the **mechanism class** without an
adb session. It will not identify the writer — nothing in the app can (change 7).

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
- **Auto-learning the effective device max** when writes come back clamped. Diagnose it — change 7's
  write record carries requested, acknowledged and `deviceMax` together, which is what makes a clamp
  visible — but do not self-tune: a feedback loop against an OEM clamp is worse than the bug.
- No golden-vector change, and no change to the curve or threshold mathematics. `:domain` is touched in
  exactly one place, change 5's `OverrideRules.shouldCommitPause` signature — decision logic belongs
  there (RUNBOOK "Where logic lives"), and no golden vector covers that function.

## Changes

### 1. Transactional write with an acknowledged marker — `platform/…/brightness/ScreenBrightnessController.kt`

Today `write()` assigns `lastSelfWriteDevice` **after** `putInt` returns (`:71`), ignores `putInt`'s
Boolean, and `isSelfWrite` demands exact equality with the requested value. Three defects: the observer
callback can be dispatched before the marker exists, a refused write is indistinguishable from a
successful one, and an OEM that clamps/quantizes/normalizes the stored value makes every one of our own
writes look external.

**The result type** (`:platform`, beside the interface — `:app` already depends on `:platform`, so
`PipelineState` can hold one without a duplicate app-side model):

```kotlin
enum class WriteStatus { ACKNOWLEDGED, WRITTEN_UNACKNOWLEDGED, REFUSED, DENIED }

data class BrightnessWriteResult(
    val requestedDomain: Int,
    val requestedRaw: Int,
    val acknowledgedRaw: Int?,      // non-null only for ACKNOWLEDGED
    val acknowledgedDomain: Int?,   // toDomain(acknowledgedRaw)
    val deviceMax: Int,             // the value THIS write converted with
    val status: WriteStatus,
)
```

`write(level: Int)` returns it. A plain `ok: Boolean` is not enough, for two separate reasons:

- **`WRITTEN_UNACKNOWLEDGED` is not "failed".** `putInt` returned true but the read-back could not be
  performed. The write probably landed; we cannot say at what value. Reporting that as `ok = false`
  asserts *nothing moved*, which is not what we know, and change 2 would then leave a baseline that is
  certainly wrong.
- **`DENIED` (SecurityException) is not `REFUSED` (`putInt` returned false).** Change 5's
  mode-recovery-failure reasoning turns on exactly this distinction — *"a failed mode write means
  `WRITE_SETTINGS` is gone, so the brightness writes are failing too"* — and a status that says which
  one occurred is what makes that claim checkable from a diagnostic instead of assumed.

**Every exit defined.** Capture `previous = lastSelfWriteDevice` first; arming is
`selfWriteInProgress = true` then `lastSelfWriteDevice = requestedRaw`, both **before** `putInt`; the
`finally` clears `selfWriteInProgress` on every path including the rethrow.

| exit | marker afterwards | `selfWriteInProgress` | result |
| --- | --- | --- | --- |
| `putInt` true, read-back succeeds | acknowledged raw | cleared in `finally` | `ACKNOWLEDGED`, `acknowledged*` set |
| `putInt` true, read-back fails | **stays the requested raw** (the armed value) | cleared in `finally` | `WRITTEN_UNACKNOWLEDGED`, `acknowledged*` null |
| `putInt` returns false | restored to `previous` | cleared in `finally` | `REFUSED`, `acknowledged*` null |
| `SecurityException` | restored to `previous` | cleared in `finally` | `DENIED`, `acknowledged*` null |
| any other throwable | restored to `previous`, **in `finally`** | cleared in `finally` | rethrown (unchanged from today) |

Two details the table compresses. The marker is left at the *requested* raw in the unacknowledged case
because that is both today's behaviour and the value most likely on screen — restoring `previous` there
would guarantee the echo of a write that did land reads as external. And the read-back must be
`Settings.System.getInt(resolver, SCREEN_BRIGHTNESS, -1)` inside `runCatching`, treating `-1` or a throw
as a read-back failure: do **not** reuse `read()` (`:60-63`), whose default of 128 would be
"acknowledged" as a write value, which is worse than admitting the read-back failed.

The rest of change 1:

- Add `@Volatile private var selfWriteInProgress` (it is read from the observer thread — the existing
  `lastSelfWriteDevice` carries `@Volatile` for the same reason) and make
  `isSelfWrite(raw) = selfWriteInProgress || raw == lastSelfWriteDevice`.
- **The acknowledgement is returned by the write that produced it — never by a global getter.** A getter
  such as `lastAcknowledgedWrite()` cannot distinguish *"the write I just made was refused"* from *"no
  write has happened"* from *"`clearSelfWriteMarker()` ran"* — three states that must not collapse,
  because `handleOverride` (`:260`) and `pauseInternal` (`BrightnessPipelineController.kt:233`) both
  clear the marker. The result also hands change 7 its requested/acknowledged/`deviceMax` triple with no
  second mutable side channel.
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
detector that reads brightness directly and can abort a sweep — and, per the load-bearing observation
above, that abort alone pauses the pipeline with no foreign writer involved. Harden it the same way.

**`animate()` must return more than today's enum.** The acknowledgement stays local to the runner, but
`runCycle` needs both it and the read that tripped the detector, so the outcome carries them out:

```kotlin
sealed interface AnimationOutcome {
    /** The latest ACKNOWLEDGED frame write; null only if no frame was acknowledged. */
    val lastAcknowledged: BrightnessWriteResult?

    data class Completed(override val lastAcknowledged: BrightnessWriteResult?) : AnimationOutcome

    data class Overridden(
        override val lastAcknowledged: BrightnessWriteResult?,
        /** The read that tripped the two-read detector (domain), not a later re-read. */
        val triggerObserved: Int,
    ) : AnimationOutcome
}
```

A sealed pair rather than one `(result, ack?, trigger?)` record, for the same reason change 7 refuses
five loose nullables: `Overridden` with no triggering read is not a state that can occur, so do not
create it and then null-check it in `runCycle`. `AnimationRunner.Result` is deleted; `AnimationRunnerTest`'s
six `assertEquals(Result.X, result)` sites become type assertions.

- Keep the latest **ACKNOWLEDGED** write in a local `var` across frames. A `WRITTEN_UNACKNOWLEDGED`,
  `REFUSED` or `DENIED` frame does **not** replace it — those tell us nothing about what is on screen.
- Split `isOutOfBand` so the read is visible to the caller: `bandRead(): Int` returns the value; the
  frame loop decides. A read counts toward the two-read threshold only if it is out of band **and** it
  does not equal `lastAcknowledged?.acknowledgedDomain`. Exact equality is safe here by construction:
  frame *i+1*'s band read is `toDomain(raw)` of the same stored raw the acknowledgement recorded, so a
  device that stored our frame and left it alone compares equal with no tolerance at all.
- On the trip, return `Overridden(lastAcknowledged, triggerObserved = that read)` — the read the
  detector actually saw, not a fresh `controller.read()` afterwards, which by then may be a different
  value and would misreport the event in change 7's diagnostic. The post-loop final check
  (`AnimationRunner.kt:46-49`) returns the same way.
- This does **not** reinstate the D-054 defect. The band + two-read debounce stays authoritative for
  wrong-direction and overshoot; the acknowledgement is an additional *in-band* condition ANDed into the
  out-of-band test, not a replacement for the band as the old exact-match detector was.

**`runCycle`'s `OVERRIDDEN` early return (`:111-114`) becomes, in order:**

1. If `outcome.lastAcknowledged?.acknowledgedDomain` is non-null, `ctx.update` it into
   `lastAppliedBrightness` — so `handleOverride` compares against what is actually on screen instead of
   the previous cycle's stale baseline. When there is no acknowledgement, leave the baseline alone
   (change 2's rule: never assert a value that was not confirmed).
2. In the same `ctx.update`, record `lastBrightnessWrite = outcome.lastAcknowledged` (change 7).
3. `ctx.postOverrideDetected(outcome.triggerObserved, OverrideSource.ANIMATION_BAND)` (change 7a).
4. Return, as today.

Keep both halves: the first bullet list stops the false abort, this step stops a false abort from
becoming a false pause. A genuine mid-animation override still pauses — the settled read is the foreign
value, which equals neither the acknowledgement nor the refreshed baseline.

### 2. Store what Android accepted — `app/…/runtime/PipelineCycleRunner.kt`

`lastAppliedBrightness` is set from `target` (intent) at `:146` and `:288`. Set it from the
acknowledgement instead. Every site, because the rule differs per path and an implementer should not
have to derive it:

| site | `lastAppliedBrightness` becomes |
| --- | --- |
| `target == from`, no write at all (`:91-92`) | `target` — nothing was written, and `from` came from `brightness.read()`, so this already names the on-screen value |
| SKIP_ANIMATIONS direct write (`:97`) | `ACKNOWLEDGED` → `acknowledgedDomain`; `WRITTEN_UNACKNOWLEDGED` → `requestedDomain`; `REFUSED`/`DENIED` → **unchanged** |
| animated, `Completed` (`:146`) | from `outcome.lastAcknowledged` by the same rule; no acknowledgement at all → **unchanged** |
| animated, `Overridden` | the same, applied before the post (change 1b) |
| `setInitialBrightness` (`:288`) | from that write's result, same rule |

**On `REFUSED`/`DENIED`, leave `lastAppliedBrightness` unchanged** — do not fall back to `target`, which
asserts a value that never landed, and do not adopt a stale acknowledgement as if it were this write's.
The previous value is the truthful one when nothing moved.

**On `WRITTEN_UNACKNOWLEDGED`, record `requestedDomain`** — an explicit, diagnosable assumption, not a
lapse: the write reported success, so something almost certainly landed, and what we asked for is the
only estimate available. Leaving the baseline stale would be worse rather than more honest — the screen
has moved and the commit guard would then compare against a value that is certainly not on it. The
assumption is visible: `lastBrightnessWrite.status` says the value was never confirmed, and change 7
renders it.

This is what makes the settle comparison at `:251` refer to reality. It also feeds Live Debug's
"Current brightness" and the dashboard/widget readouts — acknowledged is the more honest value there
too, and stays consistent with D-109 (`targetBrightness` remains the perceived, un-floored value).

### 3. Settle-delay fallback — `PipelineCycleRunner.kt:244`

`settleMs` is `cycleTimeMs ?: 0`, and `hibernate()` nulls `cycleTimeMs` — so on the wake path, the one
with the weakest reference, the pause commits with **no re-read delay at all**. D-049 #1 specified
"≈ `cycleTimeMs`, **fallback to throttle**"; the fallback was never implemented:

```kotlin
val settleMs = (ctx.stateValue.cycleTimeMs?.toLong() ?: throttle.throttleMs).coerceAtLeast(MIN_SETTLE_MS)
delay(settleMs)
```

**`MIN_SETTLE_MS = 1L`, in the companion beside `INITIAL_SETTLE_MS`, and it means exactly one thing:**
the re-read must never happen in the same dispatch as the event that asked for it. `delay(0)` returns
without suspending, so today's `if (settleMs > 0) delay(settleMs)` can commit against a read taken with
no opportunity for an in-flight provider write to land. One millisecond is the smallest value that
guarantees the suspension; it is a **yield floor, not a settling estimate**. The settling estimate is
`cycleTimeMs`, falling back to `throttleMs`. No larger floor is defensible from anything on hand — a
bigger one would be precisely the port-invented constant change 6 was withdrawn for — so if a diagnostic
later shows dismissals arriving after the re-read, size it from that record, the same discipline change
6 imposes on itself.

The floor is not theoretical. `throttleMs` **can** be zero: `onCycleComplete` floors it at 0 on purpose
(`ThrottleController.kt:47`, *"No setting floor (F78)"*) and `BrightnessEngine` emits
`transitionDurationMs = 0` on the manual-override path (`BrightnessEngine.kt:37`). The 100 ms lower
bound belongs to the *setting* (`AabSettingsMapper.kt:93`) and is only applied at `seed()`, so the
fallback cannot borrow it.

Observation for the executing session, **not** a change to make here: `throttle.seed()` is called once,
in `start()` (`BrightnessPipelineController.kt:116`), and `reinit()` (`:240-244`) does not reseed it, so
on the wake path the fallback is the pre-sleep window — the right order of magnitude and non-zero in
practice. `PipelineEvent.ScreenOn`'s doc comment (`PipelineState.kt:53`) says wake resets the throttle;
it does not. Correct the comment if you touch it, but do not change the code to match the comment inside
this fix.

### 4. Tolerance at the commit guard — `PipelineCycleRunner.kt:251`

`settled == s2.lastAppliedBrightness` is exact in domain space. At the reporter's scale one domain step
is ≈ 12 raw units (≈ 16 if the advertised maximum is 4095 — see Context), so any asynchronous round-trip
that crosses a domain boundary pauses the pipeline. Compare with **±1 domain unit**.

Define the semantics rather than calling it a no-op: **one domain step is treated as representational
drift; a persistent two-step deviation stays detectable.** This is a deliberate deadband, not a
formality — it is false that a genuine slider move is always larger than 1/255. A user *can* make a
one-step adjustment, and on a 3000-plus-raw device the system slider's own granularity is far finer than
one domain unit, so the smallest real moves become invisible. State the cost plainly: **a persistent
deviation of ≤1 domain unit is made indistinguishable from representational drift, deliberately.** The
trade is accepted because a 1/255 change is imperceptible, and because the blindness is bounded in
*magnitude*: a deadband cannot swallow an arbitrarily large override, whereas a grace period is blind to
a change of any size for its whole duration. There is precedent — the animation detector already
tolerates a wider ±2 band plus two consecutive failures. Pin the boundary in tests: ±1 dismissed, ±2
pauses.

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

`forceManualMode()` currently swallows `SecurityException` and reports nothing (`:91`). Give it a
`Boolean` result: dismissing an override on the strength of a mode recovery that silently failed would
leave the app knowingly running in the conflicting state. **When the recovery fails, still do not
pause** — record the disposition as an unrecovered mode conflict (change 7) and surface it. Pausing
would label the event "Manual Override Detected", which is precisely the misattribution this change
exists to stop, and a failed mode write means `WRITE_SETTINGS` is gone, so the brightness writes are
failing too and the pipeline is already inert. That last inference is now *checkable* rather than
assumed: change 1's `DENIED` status says the same thing about the brightness writes, and change 7 shows
both. Flag this as the owner-visible call it is.

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
The app does **not** force-flip to MANUAL every cycle: `forceManualMode()` at `PipelineCycleRunner.kt:93`
sits inside `if (target != from)`, so a cycle that changes nothing never reclaims the mode; only
`setInitialBrightness` (`:280`) does so unconditionally. Change 5 therefore **does** add a new
mode-recovery site — a narrow extension of an existing claim rather than a fresh one, but record it as
an addition, not as behaviour that was already there.

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

Change 3's quantities are unaffected and stay distinct: `handleOverride`'s settle **delay** is the
measured cycle duration (`cycleTimeMs`, Tasker `%AAB_CycleTime`), falling back to `throttle.throttleMs`,
floored at `MIN_SETTLE_MS = 1` — a yield, not a window.

### 7. Diagnostics — `PipelineState.kt`, `BrightnessPipelineController.kt`, `LiveDebugScreen.kt`

The app currently pauses without recording anything about why.

#### 7a. The event must say which detector fired

Both detector paths converge on `PipelineEvent.OverrideDetected(observedBrightness)`
(`PipelineState.kt:63`): the observer collector (`BrightnessPipelineController.kt:121`) and the animation
abort (`PipelineCycleRunner.kt:112` via `postOverrideDetected`, `:109`). Nothing downstream can tell them
apart, so the diagnostic's `source` would be a guess. Propagate it instead:

- `OverrideDetected(observedBrightness: Int, source: OverrideSource)`, with
  `enum class OverrideSource { OBSERVER, ANIMATION_BAND }` beside it.
- `PipelineRuntimeContext.postOverrideDetected(observed: Int, source: OverrideSource)` (`:32`), and the
  dispatch at `BrightnessPipelineController.kt:227` passes it to `handleOverride(observed, source)`.
- The observer collector supplies `OBSERVER`; change 1b's early return supplies `ANIMATION_BAND` with
  the read that actually tripped the detector.
- `ControlEventGate` coalesces on `event::class.java` and this event is admitted with
  `coalescible = false` (`ControlEventGate.kt:26-27`, `BrightnessPipelineController.kt:162`), so a second
  field changes nothing about admission or the DA-043 bound.

#### 7b. Two records, not one and not five loose fields

- **`lastBrightnessWrite: BrightnessWriteResult?`** — the **continuous** one. Updated once per cycle
  from the animation outcome or the direct write (**not** per frame: 50 state updates per sweep would
  churn every Compose consumer for nothing), and in `setInitialBrightness`. This is what makes requested
  vs acknowledged vs `deviceMax` readable **on a device that never fires an override at all** — which
  device check 3 asks for, and which a single override-scoped record cannot deliver.
- **`overrideDiagnostic: OverrideDiagnostic?`** — the **event-scoped** one, written where an override is
  detected and where one is dismissed by changes 4/5: `source` (from the event), `disposition` (paused /
  dismissed-tolerance / dismissed-mode / mode-recovery-failed), `observed` (the event's value — for the
  animation path the read that tripped the detector), `settled`, `expected` (`lastAppliedBrightness` at
  commit time), `mode`, `write: BrightnessWriteResult?` (the `lastBrightnessWrite` in force at the
  event — referenced, not re-derived), `timestamp`.

Two records rather than a handful of independent nullables: `PipelineState` is a coherent
single-consumer snapshot (D-027), and loose fields admit combinations that cannot occur (a mode with no
source, a settled value with no disposition) while making the Live Debug rendering a pile of null
checks. Two records rather than one: they have different lifetimes, and collapsing them would leave the
normalization readout unavailable in exactly the case where the device behaves well enough not to pause.

`observed` finally gives `handleOverride(observed: Int)` a use: **that parameter is currently dead** —
the function takes it and never reads it, deciding entirely on the post-settle re-read. Recording both
it and `settled` is what makes an observed↔settled divergence visible instead of invisible.

#### 7c. Surface, and claim only what it delivers

One new `DiagnosticCard` in Live Debug beside "System Status": `lastBrightnessWrite` rendered whenever
it exists (requested → acknowledged, status, `deviceMax`), and `overrideDiagnostic` below it when
present. `deviceMax` on the card is what lets a device tester derive one domain step from the number
Tideo actually converts with instead of from an observed maximum (Verification, check 1).

The diagnostics **cannot identify the writer** — nothing in the app can. They identify the *mechanism
class*: requested ≠ acknowledged, a write refused or denied, a mode conflict, which detector fired,
small settled drift, or none of those (a genuine unexplained external write). That is still enough to
route the next report without an adb session, which is the actual goal.

**Do not add a `DebugCategory`.** `%AAB_Debug` is a fixed Tasker-parity set: `AabSettings.kt:150` pins
`range 0..9` and the label array in `strings.xml` matches. A tenth level would be a parity change for a
diagnostic that belongs on-screen.

## Tests

Signature changes break every double; update all six: `platform/…/ScreenBrightnessControllerTest.kt`,
`platform/…/observe/BrightnessObserverTest.kt`, `app/…/runtime/{BrightnessPipelineControllerTest,
AnimationRunnerTest, PanicHandlerTest, ControlFloodBoundTest}.kt`. The breaking set is: `write()` returns
`BrightnessWriteResult`, `forceManualMode()` returns `Boolean`, `isManualMode()` is new,
`isOnScreenSelfWrite()` is deleted (an override disappears from four doubles), `animate()` returns
`AnimationOutcome` (six `assertEquals(Result.X, …)` sites in `AnimationRunnerTest` become type
assertions), and `OverrideDetected` takes a `source`.

**A test seam is required.** Robolectric's settings provider stores values verbatim, so "the OEM
normalizes our write" is unreachable without one. Add a narrow injectable read/write seam to
`AndroidScreenBrightnessController` in the style of the existing `deviceMaxOverride` constructor
parameter; without it the headline regression test cannot exist. The seam must also be able to fail a
read-back (for `WRITTEN_UNACKNOWLEDGED`) and to make `putInt` return false (for `REFUSED`).

New coverage:

1. A normalized write (requested 3212 → stored 3083) leaves a marker matching the **stored** value, so
   the observer callback is a self-write, not an override.
2. An observer callback arriving while `putInt` is in flight is a self-write (`selfWriteInProgress`).
3. `lastAppliedBrightness` equals the acknowledged value, not the requested one.
4. **The #126 headline.** A 50-frame bright → dark → bright sequence with normalized writes does not
   pause — and the normalization must be **deliberately large enough to fall outside the sweep band**,
   not merely a domain unit or two. Without change 1b this test fails, which is the point of writing it
   this way: it is the only test that distinguishes the two detector paths. Assert all three: no
   `Overridden` from `AnimationRunner`, no pause from `handleOverride`, and — when the same fixture is
   pushed past the band with a *foreign* value — an `Overridden` whose `triggerObserved` is the read that
   tripped it.
5. A settled value within ±1 domain of the applied value does not pause; **±2 does** (change 4's
   deadband boundary, pinned in both directions); a genuine external write after an animation still
   pauses.
6. `SCREEN_BRIGHTNESS_MODE != MANUAL` at commit → no pause, and `forceManualMode()` is called; mode
   MANUAL with the same write → still pauses. (#127) Plus: `forceManualMode()` **failing** → still no
   pause, and the diagnostic records the unrecovered mode conflict.
7. The wake path re-reads after a suspension instead of committing instantly: `cycleTimeMs = null`
   **and** `throttleMs = 0` still yields one delay and one re-read. Assert the behaviour (a suspension
   occurred, the re-read happened), not the number of milliseconds — `MIN_SETTLE_MS` is a floor, not a
   promise about duration.
8. **Asynchronous normalization**, both sides of the line: a late re-write within ±1 domain is absorbed
   (change 4); a late re-write large enough to leave the animation band is *not* absorbed and still
   pauses — assert that explicitly rather than leaving it undefined, since it is the documented limit of
   the synchronous read-back.
9. A refused write (`REFUSED` and `DENIED` separately) leaves `lastAppliedBrightness` at its previous
   value — neither the un-landed `target` nor a stale acknowledgement presented as this write's — and
   restores the **previous** self-write marker.
10. `WRITTEN_UNACKNOWLEDGED` (write succeeds, read-back fails): status recorded, `acknowledged*` null,
    the marker holds the **requested** raw so the write's own echo is still filtered, and
    `lastAppliedBrightness` becomes `requestedDomain`.
11. An unexpected throwable from `putInt` is rethrown, and afterwards the marker is the **previous**
    value and `selfWriteInProgress` is false (assert via `isSelfWrite` of an unrelated value returning
    false — the poisoned-state regression this exit exists to prevent).
12. `lastBrightnessWrite` is populated by an ordinary cycle **with no override anywhere in it** — the
    continuous-diagnostic guarantee device check 3 rests on.
13. Source propagation: the observer route posts `OBSERVER`, the animation abort posts `ANIMATION_BAND`,
    and `overrideDiagnostic.source` is what the event carried rather than a re-derivation.

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
- **Device checks for the Owner queue** — the two pass/fail ones use *injected* triggers so they can
  fail on a device that never shows the bug (DB-083), and mirrored into
  `docs/rebuild/DEVICE_TEST_SCRIPT.md`:
  1. **One domain step, derived from Tideo's own mapping.** Read `deviceMax` from the new Live Debug
     write card (change 7c) — that is `config_screenBrightnessSettingMaximum`, the value Tideo converts
     with, and it is **not** necessarily the largest raw value the device will store (3083 was an
     observation; a provider that clamps below the advertised maximum is the suspected mechanism, so
     using the observed maximum here can silently test the wrong step size). One step =
     `round(deviceMax / 255)` raw. Then: `settings get system screen_brightness` for the current raw,
     `adb shell settings put system screen_brightness <raw ± one step>` → must NOT pause; the same
     command with `2 ×` that offset → MUST pause. **Mind the coordinate systems:** `settings put system
     screen_brightness` takes **raw device** values, while `lastAppliedBrightness` is **domain 0–255** —
     never hand the owner a domain number to type into a raw-valued command.
  2. `adb shell settings put system screen_brightness_mode 1`, then a brightness write → must NOT
     pause and the app must flip the mode back to 0; with mode 0, the same write → MUST pause.
  3. Read requested vs acknowledged vs `deviceMax` in the Live Debug write card at the top of the curve
     (diagnostic, not pass/fail). Available on any run that writes — let one cycle complete first; it
     does not require an override to have fired. A requested value above the acknowledged one at the top
     of the range means the advertised maximum disagrees with what the provider stores, and the top of
     that user's curve is silently flat.

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
  overrunning one. Cover: the load-bearing observation — that a pause proves only a divergence from
  Tideo's own recorded baseline, and that **two** foreign-writer-free paths reach one; why single-latest
  was kept but re-defined from intent to acknowledgement, and why the acknowledgement is a per-write
  result (and, for the animation, a returned outcome) rather than a global getter; the write-status
  taxonomy, in particular why *written but unacknowledged* is not "failed" and what baseline each status
  writes; the synchronous-only limit of the read-back and the `selfWriteInProgress` blind spot with its
  one-transaction-in-flight invariant; that the ±1 comparison is a deliberate deadband whose blindness is
  bounded in magnitude, with its stated boundary; that `MIN_SETTLE_MS` is a yield floor and not a settling
  estimate; that `OverrideDetected` now carries its detector source, and why the diagnostics are two
  records with different lifetimes; the Tasker deviation in change 5 and the new mode-recovery site; that
  `INITIAL_SETTLE_MS` is port-invented rather than parity **but was left at 1500** in this fix, with
  change 6's withdrawal reasoning and the evidence that would reopen it; and the USER_PRESENT rejection
  with the owner's reasoning. Cite the row(s) from the touched source with one-line pointers and add
  `[cited]`.
- `docs/STATE.md`: one Changelog line, the corrected "parity gaps" sentence (change 5), and the two
  device-check queue items in plain language with their commands.
- **Do not post to the tracker.** STATE's Owner queue item 3 stands: issues get no reply without the
  owner saying so.
