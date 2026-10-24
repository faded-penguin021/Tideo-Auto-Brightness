# Night Light switch-on order: no stale Kelvin flash on a profile switch

## Status

- **Spec only. No code has changed.** The owner asked on 2026-10-20 for this plan, docs only, on
  the session branch `math-explained`. That authorised **Unit 0 only**: committing this file.
  Every later unit needs the owner's separate go-ahead.
- No unit opens a PR, replies on an issue, tags or releases.
- The screen's Apply gets the same fix, as its own Unit 2 (owner, 2026-10-20).
- The owner pre-approved an `app` comment-budget raise for Units 1 and 2 (2026-10-20), up to +5
  lines between them. The rule review still runs.

## The bug (owner report, 2026-10-20)

With **Follow circadian scaling** on, a profile switch in daylight turns the panel deep red, at
the profile's night temperature, and a few seconds later it jumps to the correct day temperature
(full blue). The owner confirmed when it happens:

- **It flashes** when the profile switched FROM has Night Light off. Example: a context switch
  from the owner's social media profile (Night Light off) back to the baseline (Night Light on,
  circadian).
- **It does not flash** when both profiles have Night Light on.

## Cause (validated 2026-10-20)

`DisplayTogglesCoordinator.applyNightLightLocked` switches Night Light on **before** it writes
the Kelvin:

1. `display.setNightLight(true)` runs first. The panel turns on at whatever Kelvin the display
   service still holds.
2. Only then does the circadian branch read the anchor (`acquireAnchorLocked`) and write the ramp
   Kelvin (`writeKelvinLocked`). The static branch writes its setpoint at the same late point.

**Why the stale Kelvin is the night value.** The social media profile has Night Light off and
circadian off. Leaving the circadian profile for it, the coordinator turns Night Light off and
releases the ramp's key (DC-056): it writes that profile's setpoint, or puts the device's own
Kelvin back. With Night Light off nothing shows, but the service now holds that warm Kelvin.
The next switch-on shows it.

**Why it takes seconds.** With the extended range on the owner's OnePlus 13, the anchor read and
the Kelvin write each go through the display-service bridge, and each Shizuku operation binds a
fresh helper (DC-057, DC-058). If the bridge times out, DD-064's re-check retries 5 s later.

**Evidence.**
- A throwaway JVM test (not committed) ran the owner's sequence against the coordinator. The
  circadian profile used a 686 K night setpoint, the extended range and a daytime ramp of 7308 K.
  The switch away wrote `nightLight=false, temp=686`. The switch back wrote
  `nightLight=true, temp=7308`. So the panel showed `ON@686`, then `ON@7308`.
- Nine assertions in `DisplayTogglesCoordinatorTest` pin the same enable-first order (listed in
  Unit 1).

**This explains DD-065.** That row recorded a brief warmer tint before the expected one, with the
cause unknown. Its size depends on the Kelvin the service held: mild after a ramp sample, deep
red after a night setpoint.

**The rule this breaks already exists twice:**
- D-149: the daltonizer writes its value before enabling, "no one-frame flash of the previous
  matrix".
- DC-056: the release writes before the enable ("bracketing the enable flag on the side that
  cannot show the stale sample"), pinned by
  `leavingCircadian_forANightLightBeingSwitchedOn_restoresBeforeEnabling_DC056`.

The circadian and static swap-in writes are the only Night Light writes that still come after
the switch-on.

## The fix

Move the switch-on to the end of `applyNightLightLocked`, after both Kelvin branches:

```kotlin
if (switching && !desired.nightLight) display.setNightLight(false)
val released = desired.circadianTemp || releaseAnchorLocked(settings)
if (desired.circadianTemp) { /* unchanged: anchor, ramp write, recheck */ }
else if (released) { /* unchanged: static write */ }
if (switching && desired.nightLight) display.setNightLight(true) // DD-090: the Kelvin lands first
```

That is the whole production change: one line moved and one citation added.

**What it changes:**
- Every switch-on now shows the new profile's Kelvin from its first frame. That covers circadian
  and static profiles, context switches, manual loads, and the first profile after a service
  start (DD-060).
- Night Light comes on a little later: after the write instead of before it.
  - On the bridge, that is the Shizuku bind time.
  - Off the bridge, a write whose honour probe is due waits its 750 ms settle (DC-057).
  - The owner trades on-time-but-wrong for slightly-late-but-right.

**What it does not change:**
- A failed write still lets the switch-on happen, as today, and DD-064's 5 s re-check still
  retries it.
- Switch-off, release, stop, the restore to the prior state (DD-059), panic and the ticker are
  untouched.
- The per-operation Shizuku latency is not addressed here. The faster-Shizuku plan's Unit 6
  covers that.

**Assumption, and its evidence.** The display service keeps a Kelvin written while Night Light is
off and shows it on the next switch-on.
- The owner's report is evidence for that on the OnePlus 13: the deep red is exactly such a
  Kelvin.
- AOSP's behaviour is recalled, not measured: a Kelvin change while off updates the service's
  matrix, and switching on animates to it.

## The screen's Apply (Unit 2; owner, 2026-10-20)

The Privileged Display screen's own Apply has the same order. It applies while the service is off.
In `DisplayTogglesViewModel`'s device-write block, the `setNightLight` write comes before the
static temperature write. So turning Night Light on there with a new setpoint briefly shows the
old one.
- It never runs on a context switch, which is why it is a separate unit.
- The fix swaps those two blocks.
- The owner chose to fix it, as Unit 2, after the coordinator fix.

## Execution rules

- **Units.** One unit at a time, following playbook 4 and session discipline. Each unit runs, in
  order:
  - a STATE Changelog line;
  - `scripts/ladder.sh` green (`--guards-only` for docs-only units);
  - the commit;
  - `--guards-only` again;
  - a push to the session branch.
- **Reviews.**
  - Units 1 and 2 write device settings, so each gets a blocking fresh-context glue review.
  - Each unit's comment-budget raise gets a rule review: fresh context, strongest tier, no
    self-review fallback. If no fresh context can be spawned, the raise waits in the Owner queue,
    and that unit's fix waits with it.
- **Version.** 1.15.0 / vc30 is tagged, so Unit 1 is the train's one bump: a patch, 1.15.1 /
  vc31, with `fastlane/metadata/android/en-US/changelogs/31.txt` (under 500 characters).
  - The faster-Shizuku plan's version line names vc31 for its own first code unit. Unit 1
    rewords that line to "the next free versionCode", since vc31 is then spent.
  - If that plan's bump has already happened in this train, this fix rides it instead.
  - Unit 2 rides Unit 1's bump. It never bumps again.
- **Records.** Code cites DD rows, never this plan, and ledger rows never cite its path.

## Units (sequential)

**0. Commit this plan (docs only).**
- Write this file.
- Add the STATE `## Active work` item with its unit checklist, and a STATE Changelog line.
- Run `--guards-only`, commit, run `--guards-only` again, push. **Stop.**

**1. The fix, its tests and its records (one commit).**
- **Test first.** Add a regression test with the owner's sequence:
  - a circadian profile with Night Light on;
  - then a profile with Night Light off and circadian off, holding a warm setpoint;
  - then back to the circadian profile in daylight.

  It asserts that the Kelvin write comes before `nightLight=true`, and that the extended route's
  service set comes before it too. Add a static sibling: a switch-on into a static setpoint.
  Run both and see them **fail** on today's code.
- **Fix.** Move the line as above and cite DD-090 in a one-line pointer.
- **Comment budget (owner pre-approval, 2026-10-20).** The `app` budget was full on 2026-10-20:
  `comment-budget.sh` read 2564/2564.
  - The owner pre-approves raising `RAISED_app` in `scripts/guards/comment-budget.sh` for Units 1
    and 2, as DD-076 did for the hand-off's unit 5.
  - First cut any comment the change makes restating. Then raise the budget by exactly the
    comment lines the unit still adds, in main and test sources, as the guard counts them. That is
    expected to be +1 to +3 here, with **+5** the cap for Units 1 and 2 together.
  - Extend the `RAISED_*` note with a line naming DD-090.
  - It is still a rule change. `scripts/guards` is in `RULE_FILES`, so the rule review below
    applies, and DD-090 records the raise.
  - General headroom is out of scope. That would be a re-baseline (DB-081), on its own owner
    decision.
- **Re-order nine assertions** from `nightLight=true, temp=…` to `temp=…, nightLight=true`:
  - `profileSwap_writesOnlyTheChangedFields`
  - `circadianSwapIn_writesTheCurrentRampValue_notTheStaticAnchor_D154`
  - `anExtendedSetpoint_reachesTheServiceUnclamped_DD048`
  - `withoutTheFlag_theSameSetpointIsClampedToTheDeviceRange_DD048`
  - `anExtendedSetpointWithNoRoute_landsOnTheDeviceRange_DD048`
  - `anExtendedRamp_writesThroughTheServiceOnSwapAndTick_DD048`
  - `aSwitchOnTheOsOverrode_isRewrittenWithinTheRecheck_DD064`
  - `restartAfterPanic_reassertsTheBaseline_onTheFirstEffectiveEmission_D155`
  - `theNextStart_switchesNightLightBackOn_afterStopHandedItBack_DD060`

  No other assertion should move. If one does, stop and report it.
- **Version.** 1.15.1 / vc31, `31.txt`, and the faster-Shizuku plan's version line (above).
- **Ledger.**
  - New row **DD-090 [cited]**: the cause, the move, the owner's confirmation, the trade-off and
    the recalled AOSP assumption. JVM-tested; on-device unverified.
  - Pointer line on DD-065: "Explained and corrected by DD-090."
- **Device steps.** In `docs/rebuild/DEVICE_TEST_SCRIPT.md`:
  - Step 38a: drop "a very short dip to a warmer tint … is known (DD-065)". It should now expect
    no warmer dip.
  - New step **38b**, a switch-on from a Night-Light-off profile. Steps:
    - Hold a circadian, extended profile and a second profile with Night Light off, circadian off
      and a warm setpoint.
    - Load the second profile, then the first, in daylight. Use a manual load, then the owner's
      context rule.
    - Expected: Night Light comes on at the day temperature, with no red frame, possibly a
      moment after the load.

  Add the matching manual row `s11_38b` in `e2e/scenarios.toml`, in the same commit.
- **Gates.** Run `scripts/ladder.sh` green, then the glue review and the rule review.
- **Commit body.** Says what ran and that the panel was not checked on a device.

**2. The screen's Apply (one commit; owner, 2026-10-20).**
- **Test first.** In `DisplayTogglesViewModelTest`, use the existing `beforeNightLightWrite` hook.
  - Set up: service off, Night Light off, the key holding a different Kelvin.
  - Action: `applyNow` with Night Light on and a setpoint.
  - Assert: when `setNightLight(true)` is called, the Kelvin key already holds that setpoint.
  - See it **fail** on today's code.
  - No existing screen test pins the order today (2026-10-20).
- **Fix.** In `DisplayTogglesViewModel`'s device-write block, move the Night Light block after
  the temperature block, citing DD-091 in a one-line pointer. The order of the other writes is
  unchanged.
- **Comment budget.** Any raise comes from the shared +5 cap above, rule-reviewed again.
- **Ledger.** New row **DD-091 [cited]**: the same rule applied to the service-off Apply.
- **Device step.** Add the service-off Apply case to step 38b, and refresh the `s11_38b` row in
  the same commit.
  - Service off; Night Light off at one Kelvin.
  - On the screen, turn Night Light on with a visibly different setpoint, then Apply.
  - Expected: no frame at the old Kelvin.
- **Records.** If the user-facing note in `31.txt` needs it, extend it, still under 500
  characters.
- **Gates.** Run `scripts/ladder.sh` green, then the glue review and any rule review.

**3. Owner device check, and close-out.**
- The owner runs steps 38a and 38b on 1.15.1-debug vc31, built from Unit 2's commit.
- Agent: record the result in a new DD row (verified, or the finding).
- Delete this plan file, and remove its Active work item. Its content then lives in DD-090, the
  new row and the STATE Changelog.
