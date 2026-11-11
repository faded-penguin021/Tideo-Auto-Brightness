# Display calibration: manual ceilings, the HBM threshold and failure diagnostics

## Status

- **Units 1–4 are code (DE-029…DE-032); Unit 5 is spec.** On 2026-10-26 the owner asked for the best way forward on
  the Pixel whose display calibration fails with "grant the DUMP permission", and authorised
  **Unit 0 only**: this file, ledger row DE-005 and the STATE entries. Every later unit needs the
  owner's go-ahead.
- **Revised on 2026-10-31 after a review of this plan and the HyperOS plan, relayed by the owner,
  whose six findings are all taken (DE-015, DE-016).** This is now the shared calibration plan: it
  holds ceiling validation, persistence, the readout above the boundary, HBM detection and failure
  diagnostics for every panel. The HyperOS plan (`HYPEROS_SETTING_SCALE.md`) keeps the setting's
  scale and the override-point migration.
- **Sequencing (owner, 2026-10-31).** This plan and the HyperOS plan stay open, and executing
  them is the next stage, after the codemap plan is complete and merged to `main`. No unit here
  starts before then.
- **D1–D4 are decided** (owner; DE-027): D1 (a), amended by the review so a plateau becomes the
  HBM threshold only when the dump corroborates it; D2 (a), against the recommendation, on
  purpose; D3 (a); D4 (a). Unit 1 landed on 2026-11-04 (DE-029): logcat tag `TideoCalibration`.
  Unit 2 landed on 2026-11-04 (DE-030), and Unit 3 the same day in the same train (DE-031), then Unit 4 (DE-032).
- Evidence: a reporter's `dumpsys display`, relayed by the owner, read against
  `DisplayCalibrationRun`, `DisplayCalibration` and `DisplayBrightnessDump` at `3512908`
  (DE-005); the review checked against `HighBrightnessUpdates` and `DumpsysDisplayBrightnessReader`
  at `c687a84`.

## Cases (the test matrix)

| Case | What fails | Evidence | Standing |
|---|---|---|---|
| Pixel (DE-005) | the sweep stalls at the 0.63 manual ceiling, then blames DUMP | the reporter's recorded dump | **confirmed** |
| POCO F4 on HyperOS (DE-014), once the scale is fixed | stalls at its transition point, 0.49976 | the reporter's manual sweep holds 0.49975574 from setting 128 up, the dump's transition point, with `brightnessMaxReason =0` (#157, DE-027) | **confirmed** by the sweep; the app's read path is still unshown |
| The owner's OnePlus (DD-081) | nothing: it calibrates today | the owner's device | regression guard |

- **The HyperOS scale fix does not fix the Pixel.** The Pixel's sweep already writes 1 to 255 on
  the right scale; its failure is the ceiling.
- **This plan's ceiling work should fix both.** The F4's sweep shows the ceiling; Unit 5's trace
  still has to show the app reading it.

## What the Pixel dump shows

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

Four gaps, in four code units:

1. **Nothing records what a failing run saw** (Unit 1). The HyperOS hypotheses and the cause
   table both need it.
2. **The sweep refuses a ceiling**, and a bare plateau cannot say whether it is one (Unit 2).
3. **The readout and the HBM flag above a ceiling** (Unit 3). AAB's extrapolation makes the
   numbers absurd, and the flag reads the rounded readout, which misses the crossing.
4. **The failure message blames permissions for every failure** (Unit 4).

### Unit 1. The calibration trace (no decision)

- **Why.** One read is not one shell call. `DumpsysDisplayBrightnessReader` tries its preferred
  source, then falls through DUMP, root and Shizuku in turn. Each dump may take 5 s
  (`DisplayBrightnessDump.TIMEOUT_SECONDS`) plus a 2 s reap, and Shizuku adds up to 4 s to bind.
  So `time dumpsys display` in a shell does not measure what the sweep waited for (DE-016).
- **Sources report a status**, not just null:
  - *unavailable*: DUMP not granted, `su` did not start, or Shizuku not usable;
  - *failed*: timed out, cut off, or a non-zero exit;
  - *no field*: output came, but none of AAB's three fields parsed;
  - or a sample, with the field.

  Each attempt carries its elapsed time. The reader still returns the first sample, as today.
- **The filter keeps the corroboration lines** that Unit 2 needs: AOSP's
  `mCachedBrightnessInfo.hbmTransitionPoint`, `.brightnessMax` and `.brightnessMaxReason`, and
  `mHbmData`, plus `mAppliedLowPower`, since battery saver scales every reading (DE-027). The F4
  prints `brightnessMaxReason =0`, with a space before `=`. `DisplayBrightnessDump.filter` also
  runs inside the Shizuku user service, so the helper's version moved from 4 to 5 (DE-029).
- **The sweep logs one line per level**: the level written; each read's source, status, field, raw
  and elapsed ms; and how the window ended. It also logs the corroboration lines once per run, and
  a summary line.
- **Surface: logcat, under one tag.** No strings, no stored state and no new `%AAB_Debug`
  category, since those are AAB's ten (AGENTS.md). Both reporters have adb, so
  `adb logcat -s <tag>` during a run is enough. A copyable trace in the app would be a
  user-visible change, so it waits for a report that needs it.
- **Behaviour is unchanged**: the same reads, sources, windows and outcomes.
- **Tests.**
  - `DisplayBrightnessReaderTest`: a status per source, and elapsed time from a fake clock.
  - A filter test that keeps the corroboration lines and still caps the line count.
  - `HandoffPipelineTest`: the trace's lines for a passing and a failing sweep.
- **Records.** A DE row and glue review. It carries the train's one version bump if it is the
  train's first code unit (see "Execution rules").

### Unit 2. The sweep accepts a corroborated ceiling, and stores it (D1, D4)

- **Plateau.** The sweep runs as today until a level's readings never leave the previous point's
  value. That level is a plateau candidate only when:
  - the previous point is at level 128 or above; and
  - the 3 s window ends with two equal readings that match the previous point, and no reading in
    the window differed.
- **Confirm at 255.** The sweep then writes 255, skipping the levels between, and waits the same
  window. 255 must end the same way, on the plateau. Any other outcome fails, as today.
- **What those two steps establish: saturation, nothing more** (DE-015). A plateau cannot tell an
  HBM boundary from a thermal cap or a stuck reading. Thermal caps are configured per device:
  this Pixel's first throttling step caps it at 0.3806513, about level 98, but that is evidence
  about this Pixel, not a bound on others. Level 128 stays as a floor, no longer argued as a
  thermal guard; corroboration is the guard.
- **Corroborate.** The plateau is a **verified HBM boundary** only when a dump read on the
  plateau shows:
  - a transition point equal to the plateau as printed (`hbmTransitionPoint`, or `mHbmData`'s
    transition); and
  - no other cap in force: `brightnessMaxReason` none, and `brightnessMax` equal to that
    transition point.

  The dumps so far: the Pixel's transition and `brightnessMax` are both 0.63, its plateau
  (DE-005). The F4 shows a transition of 0.49975574 and, at 0 lx, `brightnessMax` equal to it,
  and the reporter's manual sweep plateaus there (DE-014, DE-017, DE-027). The OnePlus reads
  `Infinity` and never plateaus (DD-083).
- **Otherwise it is an observed ceiling** and never becomes the HBM threshold. D4 decides whether
  the run fails or stores it.
- **Precondition, settled by the rule (DE-030).** The F4's full dump carries
  `brightnessMaxReason =0` (#157, DE-027); the Pixel's is unchecked. The rule therefore requires
  only a printed transition point, and checks `brightnessMax` and `brightnessMaxReason` where the
  dump prints them, so it never relies on a field the Pixel may lack. Unit 5's Pixel trace shows
  which lines that panel prints.
- **Battery saver: refused, decided (owner, 2026-11-04; DE-028).** The F4's plateau under battery
  saver is 0.44978, 0.9 times the printed transition point, so the corroboration above would fail
  there (DE-027). Unit 4 refuses to start a calibration while battery saver is on.
- **Store a verified boundary.**
  - Drop the last point, which already sat on the plateau, and append `255:<plateau>`. On this
    Pixel that is `1:0.0,5:0.015748031,…,150:0.5866142,255:0.63`, still strictly rising, so
    `DisplayCalibration.of`, `decode` and `encode()` do not change.
  - The record gains the boundary's raw. `display_prefs` gains `display_calibration_hbm`, written
    in the same edit as the points and removed by every other write: a failure, or a rising
    panel's success.
  - **No ceiling record can exist without the key** (DE-015). Earlier builds never store a
    ceiling, and this unit writes the two together. A record without the key is a rising panel's,
    and keeps AAB's rules exactly.
- **Why the boundary maps to 255.** The readout's contract is "above 255 means High Brightness
  Mode" (`help_handoff_calibration`, `handoff_calibration_note_calibrated`). The verified boundary
  is where that mode starts, so it reads exactly 255. On the owner's OnePlus that raw is 4095
  (DD-081), the owner's condition for D1.
- **Unchanged.** A panel that keeps rising (the owner's OnePlus, DD-081) stores the same 13
  points and no key. The non-plateau path of `readStable` is untouched, including DD-079's "a
  point whose last read ends past the 3 s window still counts".
- **Cost.** On a ceiling panel the sweep takes about 15 s, not 8 s: one full window on the
  plateau, one at 255. It writes 12 levels, not 13, or 14 when the plateau first shows at 255,
  which is written again for its own window.
- **Known approximation.** Between the last rising point and the boundary the readout is
  interpolated toward 255. On this Pixel 0.61 reads 207, where setting 156 produces it. The
  setting cannot express the jump from 161 to 255, so this is accepted (see "Deliberately left
  alone").
- **Tests** (`HandoffPipelineTest`, `DisplayCalibrationTest`, a store round trip):
  - a Pixel-shaped panel, `raw = min((level − 1) / 254, 0.63)`, whose dump shows a transition
    point of 0.63, calibrates with the points above and stores the boundary 0.63. The writes are
    1…200, 255, then the restore;
  - **a temporary cap above level 128**: flat at 0.7 from about level 179, while the dump shows a
    transition point of `Infinity` or a thermal `brightnessMaxReason`. No HBM threshold is stored:
    under D4 (a) the run fails with its cause, under D4 (b) it is stored as observed, without
    `display_calibration_hbm`;
  - a stuck reading, flat at level 150's value with no transition point: the same outcome;
  - the existing flat-panel arm still fails, and so does a panel that holds at 200 for the window
    but reads higher at 255;
  - the existing 13-point straight-line test is unchanged and stores no key;
  - the store writes the key with a verified boundary and removes it on a failure and on a rising
    calibration.
- **Records.**
  - A DE row: the deviation from `_CalibrateDisplayRange` (DD-078).
  - Glue review.
  - Device step 61 (`docs/rebuild/DEVICE_TEST_SCRIPT.md` and its `s18_61` row in
    `e2e/scenarios.toml`) gains the ceiling case.

### Unit 3. The readout and the HBM flag above the boundary (D2)

- **The readout after Unit 2.** `levelFor` extrapolates past the last point with the last
  segment's slope (AAB, DD-078). On a ceiling calibration that segment is 150 → 255 over 0.043
  of raw, so the panel's top, 1.0, reads **1150**. A raw step of 0.01 moves the readout by 24.
- **Decided: no formula change (D2 a, DE-027).** The readout keeps AAB's slope, so this Pixel's
  1.0 reads about 1150. The proportional `255 × raw / boundary` (about 405) was declined.
- **The HBM flag reads raw, not the rounded readout** (DE-015). Today `read()` sets the flag from
  `level > 255` and returns early while the rounded readout is unchanged
  (`HighBrightnessUpdates.kt:56-58`). Just above the boundary AAB's slope rounds to 255: 0.6301
  is about 255.2. So the flag would never turn on there.
  - With a stored boundary, the flag follows `raw > boundary`, evaluated on every read, apart from
    the readout and before its early return.
  - A record without the key keeps AAB's `level > 255` (DD-077). The uncalibrated setting path
    never passes 255, so it never sets the flag, as today.
- **Ships with Unit 2** (DE-015). The train is not merged between them, so no release accepts a
  ceiling and still reads it with AAB's slope or the rounded flag. Unit 2 already writes the key,
  so no recalibration is owed when this unit lands.
- **Tests.**
  - `DisplayCalibrationTest`: extrapolation past a boundary keeps AAB's slope; `encode` unchanged.
  - `HighBrightnessUpdatesTest`: on a boundary of 0.63, raw 0.6301 and then 0.6302 both display
    255, the flag turns on at the first and is reported once, and 0.63 turns it off, still
    displaying 255. A record without the key keeps `level > 255`.
- **Records.** A DE row and glue review.

### Unit 4. Name the real cause of a failed calibration (D3)

- **Causes**, classified from Unit 1's source statuses. `DisplayCalibrationSink.finished` carries
  the record or a cause:

  | Cause | When | What the card and the flash say |
  |---|---|---|
  | `NO_ACCESS` | at the first level every source was unavailable: DUMP not granted, `su` did not start, Shizuku not usable | today's advice: DUMP over ADB, root or Shizuku. The only cause where a missing permission is established |
  | `NO_READING` | some source was available, but the first level's window had no reading: timed out, failed or no field | neutral: Tideo could not read the display's brightness; retry. No permission advice |
  | `READING_LOST` | an earlier level was read, then a level's whole window had no reading | neutral: readings stopped partway through; retry |
  | `ADAPTIVE` | the mode was not MANUAL, or the hand-off could not be released (DD-079) | turn Adaptive Brightness off and retry |
  | `UNSTEADY` | readings came but did not settle, did not rise, or failed the 255 confirmation | keep the screen on and untouched, then retry |
  | `CEILING_UNCONFIRMED` | D4 (a) only: a plateau confirmed at 255 but not corroborated | brightness stopped rising early and Tideo could not confirm that High Brightness Mode starts there; retry later, with the phone cool |
  | `INTERRUPTED` | a brightness write was refused, the restore failed, or Disable or panic stopped it | retry |
  | `BATTERY_SAVER` | `PowerManager.isPowerSaveMode` was true when the run was asked for; checked before the first write, so nothing is written (DE-028) | an explicit refusal that says why: battery saver dims every brightness reading, so a calibration taken now would be wrong; turn battery saver off and retry |

  - A superuser manager that denies `su` makes it exit non-zero. That reads as *failed*, not as
    *unavailable*, so it lands in the neutral wording rather than claiming a missing grant.
  - A window that saw both *no reading* and readings that did not settle is `UNSTEADY`: readings
    did come.
- **Store.**
  - `display_calibration` still holds AAB's `error` (DD-078).
  - A new `display_calibration_error` holds the cause, and `Status.Failed` carries it.
  - A stored `error` without a cause, which covers every failure before this unit, gets the
    neutral `NO_READING` wording, since its cause was never established.
- **Strings.** One new string per cause, shared by the card's banner and the flash, and one
  cause-neutral card note ("The last calibration failed."), since today's note names
  `dumpsys display`. Each new string joins Owner-queue item 1 (Chinese), and this unit updates
  that item's count.
- **Tests.**
  - `HandoffPipelineTest`: every cause above, including a source that times out with DUMP
    granted (`NO_READING`, not `NO_ACCESS`), readings that stop after level 50, and battery
    saver on at the start (`BATTERY_SAVER`, no write).
  - `SettingsScreensTest`: the banner per cause, and the legacy `error`.
  - A store round trip.
- **Records.** A DE row, glue review, and step 61's failure expectations in both device files.

### Unit 5. Device checks, then close out

- **The owner's OnePlus** (regression guard), on the debug install, which learns its own
  calibration (DE-002):
  - Recalibrate. Expect "Calibrated" after 13 levels, and
    `adb shell run-as com.tideo.autobrightness.debug cat files/datastore/display_prefs.preferences_pb | grep -a display_calibration`
    showing the same straight line as DD-081, with no `display_calibration_hbm`.
  - Revoke DUMP
    (`adb shell pm revoke com.tideo.autobrightness.debug android.permission.DUMP`) with Shizuku
    stopped and no root, then calibrate. Expect the `NO_ACCESS` message.
  - Pause with Adaptive Brightness on, then calibrate. Expect the Adaptive Brightness message.
- **The reporter's Pixel** (confirmed case). The owner has none (DD-087), so this goes to the
  Owner queue as a request to the reporter:
  - grant DUMP and calibrate. Expect "Calibrated" in about 15 s, and the trace's corroboration
    lines showing the transition point 0.63. Ask also for
    `adb shell getprop ro.build.version.sdk_full`, so a run on Android 17 QPR2 (37.2, which
    promises no behaviour change) can be told apart from an earlier build;
  - hand off in direct sun at 10 000 lx or more. Expect the readout above 255 and "High
    Brightness Mode activated" once, while
    `adb shell dumpsys display | grep -E 'mHbmMode|hbmMax'` shows the mode on.
- **The POCO F4** (confirmed by the sweep, DE-027), asked of its reporter on #157 through the
  Owner queue, with battery saver off; the HyperOS plan's reporter checks no longer carry it
  (DE-024):
  - first, from the corrected build (that plan's Unit 1 with this plan's Unit 1), the trace and
    the dump of a failed run: start `adb logcat -s TideoCalibration > trace.txt`,
    calibrate, then at once run `adb shell dumpsys display > display.txt`, and attach both. The
    trace gives the failing level, each read's source, field, raw samples and elapsed time; the
    dump's event log gives each level's `brt` and `unclampedBrt`. A shell
    `time dumpsys display` is not asked for: it times one call, not the app's read path. Only if
    they show H1's shape (see that plan) does this plan's ceiling fix apply to the F4; any other
    cause gets its own DE row, and a unit here if needed;
  - then, with Units 2 and 3, calibrate and expect "Calibrated" with the boundary 0.49975574;
  - the reporter saw the screen brighter than the dashboard after strong sunlight (#157). Manual
    mode stops at about 500 nits there, so ask for the lux, the readout and the brightness mode
    when it recurs, and compare with the hand-off readout once Units 2 and 3 are in.
- **Close out.**
  - Every unit has its DE row.
  - "Deliberately left alone" has a lasting home: DE-005 already holds the curve-ceiling fact.
  - Remove the STATE checklist and delete this file.

## Inputs that may add work

### The owner's sunlight probe: settled, no unit (DE-007)

- **What it showed.** The owner logged ten 2 s samples on the OnePlus in sunlight (2026-10-28).
  - All four values rose together from 1734 to 3019.
  - Then `get`, the setting and `mCachedBrightnessInfo.brightness` held 4095.0 while
    `mScreenBrightness` rose from 4111.0 to 4174.0.
- **`get-brightness` reads the setting, not the panel.** It is no source for the readout, so
  `dumpsys display` stays the source and this plan gains no unit.
- **BrightnessInfo does not carry High Brightness Mode on this phone either.** That gates
  faster-Shizuku Unit 7, not this plan (see "Relation to other plans").
- Tideo was running, handed off to Adaptive Brightness above 65 000 lx (DE-008). That is the
  state in which the readout runs.

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

## Owner decisions (decided; D2–D4 on 2026-11-04, DE-027)

1. **D1. Accept a ceiling at the top of the sweep?** **Decided: (a)** (owner, 2026-10-26:
   repeated 0.63 readings break the rising-readings assumption, "That's fine. As long as we know
   the value above which we can assume hbm to be activated"). **Amended** by the owner-relayed
   review (2026-10-31, DE-015): a plateau is that value only when the dump corroborates it
   (Unit 2), and D4 decides the rest.
   - (a) Unit 2 as written. Pixels, and any panel whose manual range stops below its High
     Brightness Mode range, can calibrate. Nothing changes where the sweep passes today.
   - (b) Keep AAB's rule. These panels never calibrate, and the readout stays the setting, at
     most 255 (DD-080). Record it in Decided non-items, and still do Unit 4 so the message stops
     blaming DUMP.
   - (c) Read the ceiling from the dump's `mCachedBrightnessInfo` lines. It is no general source:
     on the owner's OnePlus the transition point reads `Infinity`, with AOSP's High Brightness
     Mode controller idle (DD-083). The amendment uses those lines only to corroborate a measured
     plateau, never as its source, so the OnePlus's `Infinity` is no obstacle: it never plateaus.
2. **D2. The readout above a boundary. Decided: (a)**, against the recommendation, on purpose
   (owner: "Deliberately chose D2a instead of b, not a typo"). Unit 3's raw-based HBM flag
   applies under every option.
   - (a) Keep AAB's slope. No formula change, but this Pixel reads up to about 1150.
   - (b) Recommended before the decision. Proportional above a stored boundary only. It uses Unit 2's key, and
     AAB's rule stays wherever it applies today.
   - (c) Proportional for every calibration. It gives the OnePlus the same numbers, but it departs
     from `_ReadBrightness` on every panel.
3. **D3. The failure message. Decided: (a).**
   - (a) Recommended. One message per cause (Unit 4), with permission advice only where a
     missing permission is established. This report went down the wrong path because of the
     message.
   - (b) One neutral message for every failure. It needs fewer new strings, but it still leaves
     the user guessing, and three existing strings lose their Chinese.
   - (c) Keep it as it is (AAB parity). Every failure keeps blaming DUMP, the defect the review's
     sixth finding names.
4. **D4. A plateau the dump does not corroborate** (new, 2026-10-31). **Decided: (a).**
   - (a) Recommended. Fail the run with its own cause (`CEILING_UNCONFIRMED`). Nothing is
     stored, so a temporary cap cannot become permanent, and a retry once it lifts can succeed.
     It needs one string.
   - (b) Store it as an observed ceiling, with its own marker: the readout holds 255 above it, the
     HBM flag never fires from it, and the card says the top is unconfirmed (one string). A cap
     that lifts later leaves the readout at 255 above it until a recalibration.

## Deliberately left alone

- **Tideo's own curve on a ceiling panel.** Settings from 162 to 255 all show 0.63 here
  (DE-005), so the top of a Pixel user's curve is flat in manual mode. Fixing that means
  changing Tideo's 0–255 conversion, which stays frozen (DC-011…DC-013, DC-025…DC-028,
  DB-083). It needs its own owner decision, not this plan.
- **The sweep's levels and timings** (AAB's, DD-078). The help and guide texts keep "13 levels",
  which on a ceiling panel is 12. Longer windows or dump limits wait for Unit 1's measured times;
  the HyperOS fork raised them without measurements and still failed (DE-016).
- **Calibrating under Adaptive Brightness.** No: the sweep never writes the mode (DD-079).
- **Battery saver switched on during a sweep, or during a later hand-off readout.** Only the
  start is checked (DE-028); a readout under battery saver reads 10 % low until it is turned off.
- **The interpolation between the last rising point and the boundary** (Unit 2, "Known
  approximation").
- **Low-brightness resolution** (DE-006). It belongs to faster-Shizuku Unit 12; see "Inputs that
  may add work".

## Relation to other plans

- **HyperOS brightness scale** (`HYPEROS_SETTING_SCALE.md`). It owns the setting's scale and the
  override points; this plan owns every calibration change.
  - The F4's calibration evidence is asked here (Unit 5), from a build with this plan's Unit 1
    and that plan's Unit 1; that plan's reporter checks no longer carry it (DE-024). The HyperOS
    cause is settled from that build before this plan's fix is declared to apply to the F4.
  - Under H1 the F4's plateau starts at level 128, Unit 2's floor. Do not raise that floor without
    the F4's evidence (DE-014).
  - Whichever plan lands second rebases on the other.
- **Faster Shizuku paths, Unit 7** (BrightnessInfo through the helper) touches
  `DisplayBrightnessReader` and the sweep's polling.
  - BrightnessInfo's brightness also stops at the transition point in manual mode
    (`mCachedBrightnessInfo.brightnessMax=0.63` here), so Unit 2's rule stays needed there.
  - On the owner's OnePlus in sunlight it stops at 4095 while the panel passes it (DE-007), so
    that unit is gated on a sunlight re-measurement under ColorOS 17.
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
- **Release.** Units 2 and 3 ship in one release: the train is not merged between them.
- **Reviews.** Units 1–4 touch runtime glue, so each gets the fresh-context glue review before
  its commit.
- **Records.**
  - Each code unit writes a DE row for its deviation from AAB (DD-078).
  - Code cites those rows, never this file.
- **Version.** The train's first code unit, here or in the HyperOS plan, carries its one version
  bump by playbook 6: a patch, expected to be 1.15.3 / vc33. Check the live tags first, and never
  bump twice in one train.
- **Decisions shape units** (DE-027). D2 (a): Unit 3 changes no formula and adds only the
  raw-based HBM flag. D3 (a): Unit 4 as written. D4 (a): Unit 2 fails an uncorroborated plateau
  with `CEILING_UNCONFIRMED`.
- **Comment budget.** A raise is a rule change and is asked for first.

## Units (sequential)

0. Commit this plan, DE-005 and the STATE entries; revised after the review (DE-015, DE-016).
   **Stop.**
1. The calibration trace.
2. The sweep accepts a corroborated ceiling, and stores it (D1, D4).
3. The readout and the HBM flag above the boundary (D2), in the same release as Unit 2.
4. The real cause of a failed calibration (D3).
5. Device checks on the owner's OnePlus, the Pixel request queued for the reporter, the F4's
   trace and dump asked of its reporter and its calibration once they settle the cause, then
   close out and delete this file.
