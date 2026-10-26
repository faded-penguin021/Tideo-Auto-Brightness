# Display calibration on panels with a manual ceiling (Pixel)

## Status

- **Spec only. No code has changed.** On 2026-10-26 the owner asked for the best way forward on
  the Pixel whose display calibration fails with "grant the DUMP permission", and authorised
  **Unit 0 only**: this file, ledger row DE-005 and the STATE entries. Every later unit needs the
  owner's go-ahead.
- **D1 is decided: (a)** (owner, 2026-10-26). D2 and D3 below are open, each with a
  recommendation. Unit 1 waits only on the owner's go-ahead, and Units 2 and 3 wait on D2 and D3.
- Evidence: a reporter's `dumpsys display`, relayed by the owner, read against
  `DisplayCalibrationRun`, `DisplayCalibration` and `DisplayBrightnessDump` at `3512908`
  (DE-005).

## What the dump shows

The dump's "Automatic Brightness Adjustments" event log holds a full calibration attempt
(DE-005). The setting value of each event is `brt × 254 + 1`:

| Setting written | `brt` (what the panel got) | `unclampedBrt` (what was asked) |
|---|---|---|
| 1, 5, 10 … 150 | 0.0 rising to 0.5866142 | the same |
| 175 | **0.63** | 0.685 |
| 200 | **0.63** | 0.783 |
| then 38 (the level before the run) | | |

- **Reading worked.** The sweep accepts a point only on a stable reading, so its 10 points show
  that the DUMP grant, root or Shizuku answered.
- **The panel has a manual ceiling.** Every event says `hbmMax=0.63`, as do `mCurrentMax`,
  `mCachedBrightnessInfo.brightnessMax` and `mHbmData`'s transition point. In manual mode AOSP
  clamps brightness to High Brightness Mode's transition point. Only Adaptive Brightness at
  10 000 lx or more may pass it. Settings from about 161 up all show 0.63.
- **Why it fails.** Level 200 read 0.63, the same as 175. `readStable` needs a reading that differs
  from the previous point (`DisplayCalibrationRun.kt:95`), so the 3 s window ran out (`:92`). The
  restore at +3.3 s matches. `DisplayCalibration.of` would also refuse a reading that does not
  rise (`DisplayCalibration.kt:42`).
- **Why it blames DUMP.** Every failure stores AAB's one `error` (DD-078). Both the flash
  (`flash_calibration_failed`) and the card (`handoff_calibration_failed_banner`) word that as a
  missing DUMP grant.
- **Not a Pixel quirk of Tideo's.** AAB's own `_CalibrateDisplayRange` requires rising readings,
  so it fails the same way here. There is no parity source for the fix.

## The fix

Two defects, in three units:

1. **The sweep refuses a ceiling** (Unit 1). This is the blocker.
2. **The readout above a ceiling** (Unit 2). Once Unit 1 passes, AAB's extrapolation makes the
   High Brightness Mode numbers absurd.
3. **The failure message names one cause for every failure** (Unit 3).

### Unit 1. The sweep accepts a ceiling at the top (D1)

- **Rule.** The sweep runs as today until a level's readings never leave the previous point's
  value. That level marks a ceiling only when:
  - the previous point is at level 128 or above; and
  - the 3 s window ends with two equal readings that match the previous point, and no reading in
    the window differed.
- **Confirm at 255.** The sweep then writes 255, skipping the levels between, and waits the same
  window. 255 must end the same way, on the ceiling. Any other outcome fails, as today.
- **Store.** Drop the last point, which already sat on the ceiling, and append `255:<ceiling>`.
  On this Pixel that is `1:0.0,5:0.015748031,…,150:0.5866142,255:0.63`, still strictly rising,
  so `DisplayCalibration.of`, `decode` and the stored format do not change.
- **Why the ceiling must map to 255.** The readout's contract is "above 255 means High Brightness
  Mode" (`help_handoff_calibration`, `handoff_calibration_note_calibrated`). The ceiling is where
  that mode starts, so it must read exactly 255.
- **The threshold is what matters** (the owner's condition for D1). The stored 255 point's raw is
  the value above which High Brightness Mode is on: 0.63 on this Pixel, 4095 on the owner's
  OnePlus (DD-081). Collapsing the repeated readings onto 255 keeps the stored points strictly
  rising, so the format and `decode` do not change.
- **Why level 128.** It is the middle of the range.
  - The existing flat-panel test (`min(level, 100) × 16`, flat from 128 after 100) still fails.
  - A thermal clamp sits lower. This Pixel's first throttling step caps it at 0.3806513, about
    level 98, so a throttled sweep fails instead of recording a false ceiling.
  - This Pixel's ceiling is at about level 161.
- **Unchanged.** A panel that keeps rising (the owner's OnePlus, DD-081) stores the same 13
  points. The non-ceiling path of `readStable` is untouched, including DD-079's "a point whose
  last read ends past the 3 s window still counts".
- **Cost.** On a ceiling panel the sweep takes about 15 s, not 8 s: one full window at the
  ceiling, one at 255. It writes 12 levels, not 13.
- **Known approximation.** Between the last rising point and the ceiling the readout is
  interpolated toward 255. On this Pixel 0.61 reads 207, where setting 156 produces it.
  The setting cannot express the jump from 161 to 255, so this is accepted (see "Deliberately
  left alone").
- **Tests** (`HandoffPipelineTest`, `DisplayCalibrationTest`):
  - a Pixel-shaped panel, `raw = (min(level, 161) − 1) / 254`, calibrates with the points above,
    and the writes are 1…200, 255, then the restore;
  - the existing flat-panel arm still fails;
  - a panel that holds at 200 for the window but reads higher at 255 fails;
  - the existing 13-point straight-line test is unchanged.
- **Records.**
  - A DE row: the deviation from `_CalibrateDisplayRange` (DD-078).
  - Glue review.
  - Device step 61 (`docs/rebuild/DEVICE_TEST_SCRIPT.md` and its `s18_61` row in
    `e2e/scenarios.toml`) gains the ceiling case.
  - The train's version bump, by playbook 6.

### Unit 2. The readout above a ceiling (D2)

- **The problem after Unit 1.** `levelFor` extrapolates past the last point with the last
  segment's slope (AAB, DD-078). On a ceiling calibration that segment is 150 → 255 over 0.043
  of raw, so the panel's top, 1.0, reads **1150**. A raw step of 0.01 moves the readout by 24.
- **Recommended change (D2 b).** Above the top point of a ceiling calibration, read
  `255 × raw / ceiling`. This Pixel's 1.0 then reads **405**, about 159 % of the normal
  maximum.
  - The owner's OnePlus calibration is a line through about zero (`1:16.0` to `255:4095.0`). On
    it, AAB's slope already gives the same number: 4171 reads 260 either way (DD-081).
- **Where the flag lives.**
  - `DisplayCalibration` gains `ceiling: Boolean`, and `encode()` does not change.
  - `display_prefs` gains `display_calibration_ceiling`, written with each success and removed on
    any other write.
  - A calibration without the key, which covers every one made today, keeps AAB's extrapolation
    exactly.
  - A ceiling calibration made before this unit needs one recalibration. Unit 4's device check
    recalibrates anyway.
- **Tests.**
  - `DisplayCalibrationTest`: ceiling and non-ceiling extrapolation; `encode` unchanged.
  - A store round trip for the key.
  - `HighBrightnessUpdatesTest`: on a ceiling calibration the High Brightness Mode flag turns on
    just above the ceiling and off at it.
- **Records.** A DE row and glue review.

### Unit 3. Name the real cause of a failed calibration (D3)

- **Causes.** `DisplayCalibrationSink.finished` carries the record or a cause:

  | Cause | When | What the card and the flash say |
  |---|---|---|
  | `NO_READING` | no source returned a brightness line in the first level's window | today's wording: DUMP over ADB, root or Shizuku |
  | `ADAPTIVE` | the mode was not MANUAL, or the hand-off could not be released (DD-079) | turn Adaptive Brightness off and retry |
  | `UNSTEADY` | readings came but did not settle, did not rise, or failed the ceiling check | keep the screen on and untouched, then retry |
  | `INTERRUPTED` | a brightness write was refused, the restore failed, or Disable or panic stopped it | retry |

- **Store.**
  - `display_calibration` still holds AAB's `error` (DD-078).
  - A new `display_calibration_error` holds the cause, and `Status.Failed` carries it.
  - A stored `error` without a cause, which covers every failure before this unit, keeps today's
    wording.
- **Strings.** One new string per new cause, shared by the card's banner and the flash. Also one
  cause-neutral card note ("The last calibration failed."), since today's note names
  `dumpsys display`. Each new string joins Owner-queue item 1 (Chinese), and this unit updates
  that item's count.
- **Tests.**
  - `HandoffPipelineTest`: the cause for no reading, a pause under Adaptive Brightness and a flat
    panel.
  - `SettingsScreensTest`: the banner per cause, and the legacy `error`.
  - A store round trip.
- **Records.** A DE row, glue review, and step 61's failure expectations in both device files.

### Unit 4. Device checks, then close out

- **The owner's OnePlus**, on the debug install, which learns its own calibration (DE-002):
  - Recalibrate. Expect "Calibrated" after 13 levels, and
    `adb shell run-as com.tideo.autobrightness.debug cat files/datastore/display_prefs.preferences_pb | grep -a display_calibration`
    showing the same straight line as DD-081, with no ceiling key.
  - Revoke DUMP
    (`adb shell pm revoke com.tideo.autobrightness.debug android.permission.DUMP`) with Shizuku
    stopped and no root, then calibrate. Expect the DUMP message.
  - Pause with Adaptive Brightness on, then calibrate. Expect the Adaptive Brightness message.
- **The reporter's Pixel.** The owner has none (DD-087), so this goes to the Owner queue as a
  request to the reporter:
  - grant DUMP and calibrate. Expect "Calibrated" in about 15 s;
  - hand off in direct sun at 10 000 lx or more. Expect the readout above 255 and "High
    Brightness Mode activated" once, while
    `adb shell dumpsys display | grep -E 'mHbmMode|hbmMax'` shows the mode on.
- **Close out.**
  - Every unit has its DE row.
  - "Deliberately left alone" has a lasting home: DE-005 already holds the curve-ceiling fact.
  - Remove the STATE checklist and delete this file.

## Inputs that may add work

### The owner's sunlight probe

The owner will log, every 2 s in bright light under Adaptive Brightness on the OnePlus,
`cmd display get-brightness 0`, the setting, `mScreenBrightness` and
`mCachedBrightnessInfo.brightness`. Tideo must be off or handed off, or it switches the mode back
to MANUAL. Adding `mode=$(settings get system screen_brightness_mode)` to each line shows that.

- **`get` matches `mScreenBrightness` on every line, above 4095 too.** Then `get-brightness` is a
  cheap source on this phone for root and Shizuku: one binder call, not a full dump. It would be
  a new unit here or join faster-Shizuku Unit 7.
- **`get` stays at or below 4095 while `mScreenBrightness` passes it.** Then it reads the setting,
  not the panel, and is no source for the readout.
- **`mCachedBrightnessInfo.brightness` passes 4095 with the panel.** Then BrightnessInfo
  (faster-Shizuku Unit 7) carries High Brightness Mode on this phone.
- **It cannot be assumed elsewhere.**
  - In AOSP, `getBrightness` returns the display's brightness setting, not what Adaptive
    Brightness applies. OPlus writes its auto value into the setting (DD-083), which is why it
    can track on the OnePlus.
  - On the Pixel at setting 200, `unclampedBrt` was 0.783 while the panel showed 0.63 (DE-005),
    so a calibration read through the setting would likely miss the ceiling.
  - A `get-brightness` source must therefore be adopted per device on evidence. A calibration
    must record its source with its field and never be read through another.
  - DUMP-only users keep dumpsys. `get-brightness` needs `CONTROL_DISPLAY_BRIGHTNESS`, which the
    shell has and an app does not.

### Low brightness on the 0–255 scale (AdvancedAutoBrightness issue 26)

- The same Pixel's dump measures it (DE-006):
  - `screen_brightness` is linear in light, about 6 nits per step;
  - the slider is perceptual, following AOSP's HLG gamma curve up to the ceiling;
  - setting 1 is 0 % of the slider at 2 nits, and setting 2 is already 14 % at about 8 nits;
  - half the slider lies between settings 1 and 14.
- No integer setting reaches between 2 and 8 nits, so neither this plan nor a new Tideo
  conversion can fix it. It needs a float write, which is faster-Shizuku Unit 12 ("Finer
  brightness steps", which reopens DC-026). DE-006 is the measurement that unit's first step asks
  for, on a Pixel.

## Owner decisions (open)

1. **D1. Accept a ceiling at the top of the sweep?** **Decided: (a)** (owner, 2026-10-26:
   repeated 0.63 readings break the rising-readings assumption, "That's fine. As long as we know
   the value above which we can assume hbm to be activated").
   - (a) Unit 1 as written. Pixels, and any panel whose manual range stops below
     its High Brightness Mode range, can calibrate. Nothing changes where the sweep passes today.
   - (b) Keep AAB's rule. These panels never calibrate, and the readout stays the setting, at
     most 255 (DD-080). Record it in Decided non-items, and still do Unit 3 so the message stops
     blaming DUMP.
   - (c) Read the ceiling from the dump's `mCachedBrightnessInfo` lines. That adds fields to the
     Shizuku helper's filter, and it is no general source: on the owner's OnePlus the transition
     point reads `Infinity`, with AOSP's High Brightness Mode controller idle (DD-083).
2. **D2. The readout above a ceiling.**
   - (a) Keep AAB's slope. No new state, but this Pixel reads up to about 1150.
   - (b) **Recommended.** Proportional above a ceiling calibration only. It needs one
     `display_prefs` key, and AAB's rule stays wherever it applies today.
   - (c) Proportional for every calibration. It needs no key and gives the OnePlus the same
     numbers, but it departs from `_ReadBrightness` on every panel.
3. **D3. The failure message.**
   - (a) **Recommended.** One message per cause (Unit 3). This report went down the wrong path
     because of the message.
   - (b) One neutral message for every failure. It needs fewer new strings, but it still leaves
     the user guessing, and three existing strings lose their Chinese.
   - (c) Keep it as it is (AAB parity).

## Deliberately left alone

- **Tideo's own curve on a ceiling panel.** Settings from 162 to 255 all show 0.63 here
  (DE-005), so the top of a Pixel user's curve is flat in manual mode. Fixing that means
  changing Tideo's 0–255 conversion, which stays frozen (DC-011…DC-013, DC-025…DC-028,
  DB-083). It needs its own owner decision, not this plan.
- **The sweep's levels and timings** (AAB's, DD-078). The help and guide texts keep "13 levels",
  which on a ceiling panel is 12.
- **Calibrating under Adaptive Brightness.** No: the sweep never writes the mode (DD-079).
- **The interpolation between the last rising point and the ceiling** (Unit 1, "Known
  approximation").
- **Low-brightness resolution** (DE-006). It belongs to faster-Shizuku Unit 12; see "Inputs that
  may add work".

## Relation to other plans

- **Faster Shizuku paths, Unit 7** (BrightnessInfo through the helper) touches
  `DisplayBrightnessReader` and the sweep's polling.
  - BrightnessInfo's brightness also stops at the transition point in manual mode
    (`mCachedBrightnessInfo.brightnessMax=0.63` here), so Unit 1's rule stays needed there.
  - Whichever plan lands second rebases on the other.
- **Light refactor, Unit 1** changes what `dimming.disengage()` publishes. The sweep calls it but
  is not changed by it.

## Execution rules

- **Units.** One unit at a time, each shippable:
  - a STATE Changelog line;
  - `scripts/ladder.sh` green;
  - the commit;
  - `--guards-only`;
  - a push to the session branch.
- **Reviews.** Units 1–3 touch runtime glue, so each gets the fresh-context glue review before
  its commit.
- **Records.**
  - Each code unit writes a DE row for its deviation from AAB (DD-078).
  - Code cites those rows, never this file.
- **Version.** Unit 1 carries the train's one version bump by playbook 6: a patch, expected to be
  1.15.3 / vc33. Check the live tags first, and never bump twice in one train.
- **Decisions reshape units.**
  - D2 (a) drops Unit 2.
  - D3 (b) or (c) reshapes or drops Unit 3.
- **Comment budget.** A raise is a rule change and is asked for first.

## Units (sequential)

0. Commit this plan, DE-005 and the STATE entries. **Stop.**
1. The sweep accepts a ceiling at the top (D1).
2. The readout above a ceiling (D2).
3. The real cause of a failed calibration (D3).
4. Device checks on the owner's OnePlus, the Pixel request queued for the reporter, then close
   out and delete this file.
