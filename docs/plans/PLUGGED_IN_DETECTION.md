# Plugged-in detection: charge-limit flapping (#139) and the panic toggle following profiles

## Status

Spec only. No code has changed. The approved action is committing this plan to `docs/plans/`
(owner, 2026-10-02). Executing U1 needs the owner's separate go-ahead, which should also settle the
decisions under **Owner decisions**. Even then, only session-branch commits are authorised: no PR,
no reply on #139, no tag or release.

## Context

Two defects share one subject: whether the phone is on external power. Both fixes are small and
are planned as **one unit** (owner, 2026-10-02). Line numbers are as of `a3e1158`.

### Fix A: a charging rule drops at a charge limit (#139)

Reporter: Pixel 8a on GrapheneOS (Android 17), Tideo 1.11.0, with an 80 % charge limit. A rule with
the Battery trigger and "Only while charging" turns on and off once the battery reaches 80 %. At
100 % it does not.

**Mechanism.** `AndroidBatteryStateReader.toBatteryState` (`BatteryStateReader.kt:39`) derives
`isCharging` from `EXTRA_STATUS` ∈ {`CHARGING`, `FULL`}. At a charge limit, Android reports
`NOT_CHARGING` (sometimes briefly `DISCHARGING`) while the charger stays connected. As the level
dips and tops back up, the status returns to `CHARGING`. That value reaches the rule as
`BatterySignal.plugged` (`AndroidContextSignalSource.kt:28`) and is compared with `onPower`
(`ContextMatching.kt:50`). D-132 (`ContextEngine.kt:365`) lets a plug change bypass the 30 s
battery cooldown, so every status flip re-evaluates the rules at once. At 100 % the status is
`FULL`, which counts, so that case works.

Both rule polarities are affected: an "on battery" rule (`onPower == false`) wrongly turns on at
the limit.

**Tasker parity.** This is a faithful port. task43 A12's Java does the same thing
(`task43_1_evaluatecontexts-v2.java.txt` L146–147):

```java
status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
isPlugged = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL);
```

So the fix is either a recorded deviation or a port of an AAB revision (decision 1).

**1.12.0 does not fix it.** The 1.12.0 squash (`b0d029c`) touches none of the files above.

### Fix B: the panic "Only when plugged in" toggle follows profiles

DB-009 made `panicRequiresPlugged` (`%AAB_PanicPlugged`) a **global** preference: "a context rule
swapping profiles must not change whether the safety escape hatch works". The code does not do
that. Every site that keeps the global `panicSensitivity` across a settings swap omits
`panicRequiresPlugged`:

| Site | Path | Effect today |
|---|---|---|
| `ContextEngine.kt:648` `mergeProfile` | a rule loads a profile; the baseline is restored when it drops | the toggle becomes the profile's saved value while the rule is active |
| `ProfileApplier.kt:30` `applyProfile` | manual profile load (UI and `LOAD_PROFILE`) | the toggle becomes the profile's saved value |
| `SettingsViewModel.kt:80` `resetDefaults` | Reset to defaults | the toggle resets to off |
| `SettingsViewModel.kt:102` `replaceAll` | profile import | the toggle takes the imported value |
| `DraftSettingsViewModel.kt:70` | the draft refreshes from the store | a Live Debug change does not reach an open draft, so the draft reads dirty |
| `DraftSettingsViewModel.kt:152` | the draft commits (Apply) | Apply writes the draft's stale value over a Live Debug change |

Saving a profile stores the whole live settings, the toggle included (`SettingsViewModel.kt:124`),
so any profile saved with the toggle in a different position carries it.

**Consequences:**

- A rule that loads a profile saved with the toggle **on** disables the gesture on battery while
  that rule is active. That is the exact case DB-009 ruled out.
- A change made in Live Debug while a rule is active is lost when the rule drops, because the
  restore takes the snapshot's value.
- With #139's flapping, the toggle flips with the rule whenever the profile and the baseline
  differ. Each change restarts the panic collector (`AmbientMonitoringService.kt:333`
  `startPanicGateWatcher`), which drops a shake window in progress.

**What is not affected.** The panic source's own plugged state is correct. `PanicSensorSource.kt:169`
seeds it from `EXTRA_PLUGGED > 0`, and lines 274–275 update it only on `ACTION_POWER_CONNECTED` and
`ACTION_POWER_DISCONNECTED`. A charge limit changes neither. The only exception is a device whose
charge limit reports the charger as disconnected; Fix A's device check reveals that.

There is no Tasker question here. The upstream panic-plugged feature is not in the extracted source,
and DB-009's "global" ruling is Tideo's own.

## Execution rules

- One unit. It ends with `scripts/ladder.sh` green, then a STATE Changelog line, then a commit and a
  push to the session branch. The commit body says what ran and what could not be checked on a
  device.
- **Glue review is mandatory.** The unit touches a `:platform` adapter (`BatteryStateReader`) and
  `:app` runtime glue (`ContextEngine.mergeProfile`), so it gets one blocking fresh-context review
  pass after the ladder is green (RUNBOOK **Glue-review protocol**).
- **The phone over adb is read-only.** The owner runs every device check, including the
  `dumpsys battery set` simulation below, which changes device state.
- **Write the failing test first** (RUNBOOK playbook 4). Each fix's first new test must fail on the
  current tree.
- **Train.** v1.12.0 is tagged, so this cannot ride vc26. See decision 3.

## U1 — Plugged-in detection (both fixes)

### Fix A: `:platform` reader

- **`BatteryState`:** rename `isCharging` to `isPlugged`. It means a charger is connected, not
  that the battery is charging.
- **`toBatteryState`:** compute `isPlugged = getIntExtra(EXTRA_PLUGGED, 0) > 0` and drop the
  `EXTRA_STATUS` read. A missing extra means not plugged. AC, USB, wireless and dock all count, the
  same set as the panic source (DB-009 accepted wireless).
- **Comments:** keep the `// Tasker: prof763 …` marker. Rewording it is allowed, but dropping its
  `prof763`/`task43` references fails `comment-budget.sh`. Add a one-line citation of the new
  ledger row.
- **`AndroidContextSignalSource.batteryFlow`:** map `plugged = it.isPlugged`.
- **Leave alone:** `ContextEngine`, `ContextMatching` and D-132. With the signal fixed, a plug
  change again means a real plug or unplug.

### Fix B: `:app` copy sites

At each of the six sites in the table above, add `panicRequiresPlugged = <same source>.panicRequiresPlugged`
next to the existing `panicSensitivity` line. Use the same source (`baseline`, `current` or `c`) so
the two panic preferences can never disagree about which side wins. Then:

- Extend the `mergeProfile` KDoc's list of preserved global preferences.
- Extend the `DraftSettingsViewModel` "GLOBAL fields" comment.
- Fix the `ContextEngine.effectiveSnapshot` KDoc so it states both panic preferences are identical
  in baseline and effective.

Do not touch `GlobalPrefs` (`AabSettingsGroups.kt`). It is a computed view used only by
`NestedSchemaRoundTripTest`, and it already lists fields that are not preserved (the G2-F8 note).

### Tests (written first)

**`BatteryStateReaderTest`.** Add `EXTRA_PLUGGED` to `seedBattery`, then cover:

- `NOT_CHARGING` with AC plugged → plugged (the #139 case);
- `DISCHARGING` with USB plugged → plugged;
- `FULL` with nothing plugged → not plugged;
- no `EXTRA_PLUGGED` extra → not plugged.

Rename `fullStatus_countsAsCharging` and `dischargingStatus_isNotCharging` to say what they now
assert.

**`AndroidContextSignalSourceTest`.** `batteryFlow` maps `isPlugged`.

**`ContextEngineTest`:**

- `mergeProfile` keeps the baseline's `panicRequiresPlugged`, in both directions (on over off and
  off over on), next to `mergeProfile_preservesDetectOverrides_G2F8`.
- Engine level: a rule loads a profile saved with the toggle on while the baseline is off, and the
  effective value stays off.
- Engine level: changing the toggle in the live store while a rule is active survives the revert
  to the baseline.

**`ProfileApplierTest`, `SettingsViewModelTest`, `DraftSettingsViewModelTest`.** One case per site:

- applying a profile, Reset and import each keep the current value;
- the draft follows a store-side change and does not read dirty because of it;
- Apply does not write a stale value back.

### Records

- **Ledger.** Add one new `LEDGER_D.md` row for both fixes. It records:
  - the #139 mechanism, and the Tasker deviation or AAB port per decision 1;
  - the six omitted sites and the DB-009 ruling they broke.

  Mark it `[cited]` if the code cites it. Append a pointer line to DB-009 that names the new row.
  Do not edit DB-009's text.
- **Parity.** If decision 1 is a deviation: add `dev-03` to `docs/rebuild/parity_gaps.md`
  ("Maintenance-era deviations"), and update the prof763 row in `PARITY_CHECKLIST.md`. If it is a
  port: update the task43 provenance and the checklist row as playbook 2 requires.
- **STATE.** Add a Changelog line, and note the deviation if there is one (playbook 5 Record).
- **Changelog.** Add one sentence to the user-facing changelog of the train chosen in decision 3,
  within the 500-character cap.

### Device checks (the owner runs them)

Add sub-bullets under `DEVICE_TEST_SCRIPT.md` steps 23 and 15a. Their numbering does not change, so
`e2e/scenarios.toml` needs no new row, and the `s08_23` (manual) and `s05_15a` (partial) reasons
still hold. If the implementer adds a numbered step instead, add its manifest row in the same
commit (RUNBOOK playbook 5, "Device steps change in two places").

**Step 23 (charge limit):**

1. With the charger in and an "Only while charging" rule active, run
   `adb shell dumpsys battery set status 4`. Expected: the rule stays active and Live Debug shows
   no context change.
2. Run `adb shell dumpsys battery reset`.
3. On a phone with a real charge limit, at the limit, run `adb shell dumpsys battery`. Expected:
   `status: 4` (or 3) while `AC powered` or `USB powered` is `true`, and the rule stays active.
4. If `AC powered` and `USB powered` both read `false` at the limit, that device's charge limit
   reports the charger as disconnected. Then neither fix can help on that device: record it,
   don't work around it.

**Step 15a (panic toggle is global):**

1. Turn "Only when plugged in" **off** and save profile P.
2. Turn it **on** and save profile Q.
3. Turn it **off** again, then add a rule that loads Q.
4. While the rule is active, Live Debug's switch still reads **off**, and the gesture still fires
   on battery.
5. Toggle the switch while the rule is active. Expected: the new value survives the rule dropping.

### Acceptance

- `scripts/ladder.sh` is green.
- The glue-review pass ran, and every finding was triaged.
- The new tests failed before the fix and pass after it.
- The commit body names the device checks it could not run.

## Owner decisions

1. **Fix A's Tasker standing.**
   - (a) A recorded deviation, dev-03.
   - (b) First land the same patch in AAB task43 (Appendix A). Tideo then ports it, with no
     deviation recorded.

   Either way the code change is the same. Recommendation: (b) if AAB still gets updates, since it
   has the same bug.
2. **The rule editor's label.** `contexts_only_charging` reads "Only while charging", and the rule
   summary reads "charging".
   - (a) Keep both. AOSP's own plug-based "Stay awake while charging" uses the same wording, and
     the reporter already read it as "while the charger is connected".
   - (b) Rename both to "Only while plugged in" and "plugged in", matching the panic toggle.

   Recommendation: (a).
3. **Train.** The open #136 and solar-offset plans share 1.13.0 / vc27: whichever unit lands first
   does the bump and opens `changelogs/27.txt`.
   - (a) Join that train under the same rule.
   - (b) Ship this alone as 1.12.1 / vc27 first, and move those plans to vc28.

   Recommendation: (a), unless #136 is far enough off that the reporter would wait long for a
   one-unit fix.

## Out of scope (observed, not planned)

- **`AndroidPowerMeter.isCharging`** (task524 calibration abort) also reads the charge status. At a
  charge limit, calibration would run while the phone is on the charger, and the current readings
  would mean little. It is a different decision with its own Tasker source.
- **`panic_plugged_help`** says the gesture works "only when plugged in via USB cable". The code
  counts AC, wireless and dock too (DB-009). It is a one-string fix if the owner wants it in this
  unit.

## Appendix A — the AAB-side patch (task43 A12 Java)

The same fix in the Tasker project replaces the two lines quoted under Fix A with:

```java
isPlugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) > 0;
```

`curPlug`, the Battery veto (`curPlug != lastPlug`, L225) and the `on_power` test (L379) all read
`isPlugged`, so nothing else changes. The first evaluation after the update may count one plug
change against the stored `%AAB_ContextState`, and then settles. Tasker keeps the 30 s cooldown on
plug changes (no D-132 there), so its flapping is slower, but the rule still drops.
