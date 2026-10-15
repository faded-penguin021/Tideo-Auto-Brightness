# Bright-light hand-off to Android Adaptive Brightness (#136)

## Status

Spec only. No Tideo code has changed. The owner has built the feature in AAB (handed over
2026-10-15). That build is now the reference for this port, and this plan was rewritten against it.
The export is force-added under `dist/aab-bright-light-handoff/`. It is temporary: unit 6 removes
it, and unit 1 copies every fact the port depends on into the ledger first.

Executing any unit needs the owner's separate go-ahead. Accepting the plan authorises commits on
the session branch only: no PR, no reply on #136, no tag or release.

**Train: 1.15.0 / versionCode 30**, a minor bump for a new feature and new settings. v1.14.1 /
vc29 is tagged, so the tree's 1.14.1 is spent. If `LOW_LUX_SETTLING_JITTER.md` lands first in this
train, it makes that one bump, and this plan adds its changelog sentence instead.

## Context

Issue #136 asks Tideo to hand brightness control to Android's Adaptive Brightness above a lux
threshold. That lets the OEM's sunlight/HBM boost work, which no `SCREEN_BRIGHTNESS` value can
reach. Tideo takes control back, with hysteresis, once it gets darker. A second user confirmed the
need on a OnePlus 15.

The owner's design in the thread, which still holds:

- **"Relinquish control", not "turn on HBM at x lux".** Tideo never needs the OEM's HBM lux point.
- **A new runtime state.** The service stays on and keeps measuring lux, but writes nothing, and
  override detection is off.
- **Hysteresis, a threshold set in lux, and a validity floor:** at the threshold, the profile's
  curve must give at least 0.9 × max brightness.
- **The settings are per profile.**

## The AAB reference (export of 2026-10-15)

This section records the AAB build as it was handed over, so the plan stands once `dist/` is gone.
`T` and `S` refer to `handoff-tasks.txt` and `handoff-scene.txt`.

- **Globals and profile JSON:**

  | Global | Meaning | Profile JSON | Default |
  |---|---|---|---|
  | `%AAB_AdaptiveOn` | feature switch, `true`/`false` | `adaptive_handoff.enabled` | false |
  | `%AAB_AdaptiveHandoffThresh` | entry lux, decimal | `adaptive_handoff.threshold` | 25130.0 |
  | `%AAB_HandoffHysteresis` | exit lux = threshold × this | `adaptive_handoff.hysteresis` | 0.8 |
  | `%AAB_AdaptiveHandoff` | runtime flag, `1` while handed off | (not saved) | (unset) |

  The default threshold is `10 × Form3A` on the default curve (2513), which is the default
  curve's own minimum. The scene's slider runs from 50 to 100 %.

  Tideo's default minimum is **25 132 lx**, not 25 130. It derives Form3A (≈ 2513.153) with
  `form2B` held as the Float `8.8f`, where AAB uses its stored 2513.0.
- **The decision, `_Hand-offDecider`:**
  - **Enter** when `AdaptiveOn` and `SmoothedLux > threshold`, if not already handed off.
  - **Exit** when `!AdaptiveOn` or `SmoothedLux < threshold × hysteresis`, if handed off.
  - Both comparisons are strict.
- **When AAB decides:**
  - in Evaluate Light Change, after smoothing and before Map Lux, so a hand-back is animated in
    the same cycle;
  - at Set Initial Brightness (screen-on, resume, profile Apply), on the fresh raw reading;
  - after every profile load, when the service is on and not overridden.

  It does **not** decide when the dead band refuses a reading.
- **Enter, `_Hand-offToAdaptive`:** set the flag first, then turn Auto Brightness on, then
  disable super dimming, then show a toast.
- **Exit, `_HandBackToAAB`:** turn Auto Brightness off, read brightness, clear the flag and the
  HBM flag, then show a toast.
- **Gates while handed off:**
  - The `Allow Override` profile requires `%AAB_AdaptiveHandoff !~ 1`.
  - Smooth Brightness Transition V5 returns at once, and breaks out mid-animation if the flag
    flips.
  - The DC-like transition skips its hardware write and its override check, but still applies
    dimming.
- **Service toggle:** disable hands back, then clears the flag. Enable forces Auto Brightness off
  and the flag to 0, then runs Set Initial Brightness, which may hand off again at once.
- **Validation:**
  - Done on the **unmodified base curve**: no clamp, scale, offset or circadian taper.
  - The scene checks one point: `base(threshold) ≥ 0.9 × MaxBright`.
  - The minimum is `10 × Form3A` when that falls at or past Zone2End. Otherwise it is a 50-step
    bisection over [0, max(Zone2End, 10000)], which assumes a monotone curve.
  - There is no upper bound.
  - On Apply, a too-low value is **replaced** by the minimum with a warning, and the user taps
    Apply again to save.
  - `_DetermineMinHand-offThresh` silently raises the global to the minimum after a curve save
    and after every profile load.
- **HBM readout and display calibration:**
  - `_CalibrateDisplayRange` writes 13 `screen_brightness` points in MANUAL mode. At each it reads
    `dumpsys display` (the first of `mScreenBrightness`, `mDisplayBrightness`,
    `mCurrentScreenBrightness`). It stores `"value:raw,…"` in `%AAB_BrightCal`, keyed to
    `Build.FINGERPRINT`.
  - `_ReadBrightness` reads dumpsys through `%AAB_SecondaryPrivilege` (Root, ADB WiFi, Shizuku,
    else DUMP). It maps raw to a value, extrapolating past the 255 point (above 255 means HBM),
    and sets `%AAB_CurrentBright`.
  - It runs every 2 s while the display is on, the service is on, the flag is set and there is
    no override (profile `High Brightness Updates`).
  - Both transition tasks also call it while handed off, when a calibration is present and is
    not `error`.

## Port design

The port follows AAB's names, decision rule and order of actions. Where it deviates, the reason is
given.

### Deviations from AAB (owner may veto any)

1. **Effective threshold instead of a stored raise or a blocked Apply.** The runtime enters at
   `max(stored threshold, minimum for the live curve)`.
   - AAB's silent raise mutates a global, and changes to max brightness in the Misc screen never
     trigger it.
   - The old plan's CRITICAL validator would block Apply on screens that never show the
     hand-off field.
   - This way nothing blocks Apply, nothing rewrites a profile, and a curve edit that is undone
     restores the user's own value.
   - The card shows the effective value, and says when it was raised.
   - Supersedes the 2026-10-01 decision "an invalid value blocks Apply".
2. **Hysteresis is capped at 90 %.** At AAB's 100 % the exit equals the entry, and every admitted
   reading that crosses it flips the mode. Stored as AAB's fraction, 0.50–0.90, default 0.80.
   This equals the owner's 2026-10-01 "10–50 % below". On import, 0.91–1.0 becomes 0.90, and
   anything outside [0.5, 1.0] or non-finite becomes the default.
3. **A missing setting means its default (disabled).** In AAB, loading a profile with no
   `adaptive_handoff` section, such as one saved before the feature, keeps the previous
   profile's hand-off live. Tideo's additive fields already default per profile.
4. **Settings changes are decided when they are handled, not on the next reading.** Apply, a
   context swap, import and Resume run the decision on the lux they already hold, even when the
   dead band would refuse the next reading.
   - They are ordinary queued control events. Each runs after the current cycle and its
     animation, and never ahead of a control event queued before it, so DC-069 and DC-070 are
     untouched.
   - In AAB, switching the feature off in steady sunlight can leave Android in control until lux
     moves past the dead band or the screen cycles.
5. **Resume and service start keep a hand-off that is still valid.** They do not force MANUAL and
   then re-enter, as AAB's enable does.
6. **Losing AUTOMATIC while handed off ends the hand-off.** In AAB, switching Adaptive Brightness
   off by hand leaves brightness frozen: both override paths are gated, and nothing reclaims
   control.
   - **The trigger:** a new `SCREEN_BRIGHTNESS_MODE` observer, registered only while handed off,
     sees MANUAL outside the settle window. It is independent of lux admission, because steady
     sunlight can admit no reading for a long time.
   - **Detect Overrides on:** the observer's event goes through the consumer as a control event,
     into the existing override commit path, and pauses.
   - **Detect Overrides off:** the hand-off ends and Tideo resumes writing.
   - **What it does not claim:** under dev-01/DC-006, MANUAL proves only that Tideo lost
     ownership, not who wrote it. The pause is the same policy that path already applies to an
     ambiguous MANUAL.
   - **A failed or unknown mode read does nothing.** It must not reuse `isManualMode()`, which
     reports a failed read as MANUAL.
7. **No dimming while handed off,** including PWM mode. AAB's DC-like path can switch the filter
   back on while handed off when scale or circadian take the target below the dimming threshold.
8. **The minimum is the lowest whole lux from which the base curve stays ≥ 0.9 × max up to
   100 000 lx** (unclamped, as AAB). AAB's analytic zone-3 shortcut is used when it applies.
   Otherwise a log-spaced scan then bisection, because DD-012 allows non-monotone curves. If
   none exists, the result is null and the feature never enters. AAB stores a decimal; Tideo
   stores whole lux and imports AAB's value rounded up.
9. **HBM readout is out of the core units** (Open question 1).

### Validation (domain, pure)

New `domain/.../brightness/HandoffRules.kt`, next to `OverrideRules.kt`:

- `minimumHandoffLux(cfg): Int?`, as in deviation 8.
- `effectiveThreshold(stored, cfg): Int?` returns `max(stored, minimum)`, or null.
- `exitLux(threshold, hysteresis)` returns `threshold × hysteresis`.
- `next(active, smoothedLux, …)` returns `ENTER / STAY / EXIT / NONE`, with AAB's strict
  comparisons, on smoothed lux. At initial brightness that is the seeded raw reading, as in AAB.
  It never enters with a null effective threshold, and while active a null effective threshold
  is **EXIT**. Otherwise a curve applied mid-hand-off whose minimum passes 100 k would strand
  AUTOMATIC.

### Settings (per profile, in `AabSettings`)

- **Fields and their contract rows:**
  - `handoffEnabled = false` → `%AAB_AdaptiveOn`
  - `handoffLux: Int = 25132` → `%AAB_AdaptiveHandoffThresh` (the default curve's Tideo minimum)
  - `handoffHysteresis = 0.8` → `%AAB_HandoffHysteresis`

  These replace the invented `%AAB_HandoffUse/Lux/Exit` names.
- **Touched:**
  - the `AabSettingsContract` rules;
  - `validate()`: lux to 1..100 000, hysteresis as in deviation 2, NaN guards;
  - `SettingsDisplay` (`valueFor` and labels);
  - `TaskerLegacyProfileSerializer`: the `%AAB_` key=value parser, **and** an `adaptive_handoff`
    section in its JSON path. Today that path reads only `general`, `misc`, `reactivity`,
    `circadian` and `superdimming`, so an AAB profile would lose the section.
- **No CRITICAL `SettingsValidator` rule** (deviation 1). The validator's KDoc ("exactly 5
  rules") is corrected while there.
- **No schema bump:** the fields are additive with defaults (`ignoreUnknownKeys`). They are in no
  preserve list, because they are per profile.
- **Known consequence:** an older app rejects an exported profile that carries these keys at
  non-default values (strict import).

### Platform: never corrupt the saved brightness mode

`AndroidScreenBrightnessController.forceManualMode()` saves any non-MANUAL mode as the user's
pre-service mode (D-134). After a hand-off it would save **Tideo's own AUTOMATIC**, and Disable or
panic would leave Adaptive Brightness on for a user who never had it. AAB has no saved mode, so
this part is Tideo's own. The fix:

- New interface methods `handOffToSystem(): Boolean` and `reclaimFromSystem(): Boolean`. Both
  report whether the mode write landed.
- `handOffToSystem()`:
  1. **Saves the original mode first.** If no saved mode exists yet, it saves the current one.
     A bright first reading can enter before any `forceManualMode()` has saved it.
  2. Writes the saved mode and a **persisted `handed_off` marker** in one `commit()` to the same
     SharedPreferences, so both survive process death together (the D-034(c) class).
  3. Only then writes AUTOMATIC.
  4. If that write fails, it clears the marker and returns false.
- `reclaimFromSystem()` writes MANUAL. It clears the marker only after the write succeeds.
- `forceManualMode()` never saves the mode while the marker is set.
- `restoreMode()` clears the marker.
- Every caller is covered: cycle, `setInitialBrightness`, `reclaimManualMode`, `PanicHandler`.
- **Startup reconciliation.** `BrightnessPipelineController` start reads the marker **before** it
  registers observers or runs the first cycle:
  - **Marker set, mode AUTOMATIC:** start with `handedOff = true`. The first decision keeps or
    exits the hand-off; deviation 5 relies on this.
  - **Marker set, mode MANUAL or unreadable:** death came between the persist and the mode
    write, or the user took control. Clear the marker and start normally. The saved mode is
    intact.

### Runtime state "handed off"

- `PipelineState.handedOff` survives hibernate: the mode stays AUTOMATIC with the screen off, and
  the first cycle after wake decides.
- The logic goes in a new `runtime/HandoffCoordinator.kt`. `PipelineCycleRunner.kt` is at 419 of
  its 434-line cap (`PipelineFileLayoutTest`), too little for the feature, and moving
  `dimmingReadout` out is not needed.

In `runCycle`, after smoothing and before the write, as AAB orders it:

- **ENTER:**
  1. Set `handedOff` in state, so both override gates see it before the mode flip's echo.
  2. Arm the settle window (DC-009).
  3. Disengage dimming.
  4. Call `handOffToSystem()`. **If it returns false**, roll the flag back and write normally
     this cycle. That counts neither as an override nor as a hand-off, and the next decision may
     retry.
- **While handed off:**
  - Keep publishing lux, thresholds and α.
  - Do no write, animation or dimming, and pass `brightnessChanged = false` to the throttle.
  - The mode observer of deviation 6 is registered.
- **EXIT:**
  1. Call `reclaimFromSystem()` and arm the settle window. **If it returns false**, stay handed
     off, without writes, and retry at the next decision. Writing against AUTOMATIC would be
     overwritten, or read as an override.
  2. On success, clear the flag and unregister the mode observer.
  3. Fall through to the normal write and animation path in the same cycle, as AAB does.

**Override detection is off on both sibling paths** (AAB's `Allow Override` gate):

- `OverrideMonitor.GateState.handedOff`
- `PipelineCycleRunner.canPause`. Without this, `handleOverride` would reclaim MANUAL (DC-009)
  and undo the hand-off.

**Other events:**

- **Apply, context swap, import, Resume** (`reapplyProfile`, `resume`, `setInitialBrightness`):
  decide when the event is handled (deviation 4). Skip the write when the hand-off stays;
  otherwise exit first.
  - **With no lux held** (hibernated: both lux fields are cleared, and `setInitialBrightness`
    returns early), the ownership exit runs **before** that early return: reclaim MANUAL
    without a brightness write. The next wake writes.
- **Pause** (automation intent): exits first, then pauses. AAB reaches the same state through its
  service toggle.
- **Disable / panic:** unchanged. `restoreMode()` returns the true pre-service mode.

### Provenance

- The DD row from unit 1 carries this plan's AAB reference section: names, decision, order,
  validation and defaults. That keeps the port auditable once `dist/` is gone.
- Ported logic gets `// Tasker: "_Hand-offDecider" (AAB hand-off export, DD-067)`-style markers,
  as AGENTS.md asks for ported logic. The `comment-budget.sh` manifest is a floor, so new markers
  need no manifest edit.
- These tasks are not in the V3.3 XML, so the markers name the task and carry no `L` coordinate.

### What the user sees

- **Curve & Brightness** gets a hand-off card:
  - a switch;
  - a lux field (default 25 132), with a live note of the curve's minimum. It says when the
    effective threshold is raised to that minimum, and says when the curve never reaches 90 % of
    max;
  - a 50–90 % slider, "takes back control below N lx";
  - a vertical `ChartMarker` at the effective threshold.
- **Notification:** "Android is controlling brightness · N lx".
- **Dashboard pill, widget and tile:** "Android".
- **Live Debug:** a "Hand-off" metric. All strings go through `stringResource`, so
  `WRAPPER_CEILING` is not raised. Help text goes in `TaskerHelp`/`UserGuideScreen`.
- The public `STATE_CHANGED` broadcast is unchanged.
- There is no Misc-screen banner (deviation 1).

## Units (sequential)

Each unit ends (RUNBOOK checkpoint invariant): STATE Changelog line → `scripts/ladder.sh` green →
commit → `scripts/ladder.sh --guards-only` → push.

0. **Open the train.**
   - `git ls-remote --tags --refs origin 'refs/tags/v*'` shows v1.14.1. If `app/build.gradle.kts`
     still says 1.14.1 / vc29, bump it to 1.15.0 / vc30 and create
     `fastlane/metadata/android/en-US/changelogs/30.txt` (≤ 500 codepoints). Otherwise add one
     sentence to that file.
   - Fix the STATE Owner-queue train paragraph, which still names 1.14.1, and add this plan's
     checklist under `## Active work`.
   - **Read-only adb facts:**
     - model and SDK;
     - `screen_brightness_mode` and `screen_brightness`;
     - the light sensor's `maxRange` (`dumpsys sensorservice`);
     - the `dumpsys display` lines for the three brightness fields, `HighBrightnessModeController`
       and `mHbmMode`, once in each mode. The owner switches modes. Open question 1 needs these.
   - **If the sensor saturates below 25 132 lx**, stop and raise it as an Owner-queue question
     first: on default curves the feature could never trigger.
1. **Domain.** `HandoffRules` plus JVM tests:
   - the default curve (minimum 25 132);
   - the reporter's 255 plateau;
   - Battery Saver;
   - negative Form2B;
   - Form3A ≤ 0;
   - a minimum above 100 k → null;
   - effective-threshold raise;
   - null while active → EXIT;
   - strict edges and no flapping at 90 %.

   Append ledger row **DD-067** (or the next free DD row) `[cited]`: the feature, the AAB reference
   section above, and deviations 1–9.
2. **Settings.** Fields, contract, validate, display, the key=value and `adaptive_handoff` JSON
   import, and the hysteresis import mapping. Extend `AabSettingsClampTest`,
   `AabSettingsMigrationTest`, `CurveParamFreedomTest`, `SettingsValidatorTest`,
   `LegacyImportRoundTripTest`, `LegacyProfileParseTest`, `SettingsDisplayContractDriftTest`
   and the `ContextEngineTest` merge.
3. **Platform.** Controller methods, the persisted marker, all test fakes (`AnimationRunnerTest`,
   `BrightnessPipelineControllerTest`, `PanicHandlerTest`, `ControlFloodBoundTest`).
   Robolectric tests:
   - the saved MANUAL survives hand-off → forceManual → restore;
   - the first entry with no saved mode yet saves the original;
   - a failed AUTOMATIC write leaves no marker;
   - a failed reclaim keeps the marker;
   - a new instance with the marker set;
   - panic during a hand-off.

   Glue review.
4. **Runtime.** `HandoffCoordinator`, state, startup reconciliation, both override gates, the
   mode observer (`platform/observe/`, beside `BrightnessObserver`, which watches
   `SCREEN_BRIGHTNESS` only), and the event handling. Pipeline tests:
   - enter and exit at the band edges, with no write and no dimming while handed off;
   - a failed entry or failed reclaim follows the rules above;
   - observer and commit overrides ignored;
   - a foreign MANUAL pauses with Detect Overrides on, and resumes writing with it off, without
     any lux reading;
   - a process restart with the marker set (AUTOMATIC → handed off; MANUAL → cleared);
   - Apply that switches the feature off while hibernated reclaims with no write;
   - a context swap to a profile with the feature off exits at once, under a held dead band;
   - Apply that switches the feature off exits at once;
   - Pause exits;
   - Resume keeps a valid hand-off without a mode flip;
   - `stop()` restores the real mode;
   - screen off/on keeps the flag and re-decides.

   Glue review.
5. **UI.** The card, the graph marker, notification, pill, widget, tile, Live Debug and help
   text. `HardcodedStringCheckTest` ceilings stay unchanged. Run `:app:assembleDebug` and lint.
   Glue review, because the notification is glue.
6. **Close-out.**
   - Finalise `30.txt`.
   - Add `parity_gaps.md` **dev-03** (deviations 1–8).
   - Add `DEVICE_TEST_SCRIPT.md` **§18, steps 58–61**, each with an `e2e/scenarios.toml` row
     (`test_manifest.py` keeps them in lockstep):
     - entry and exit at the band edges;
     - no override pause in sunlight;
     - switching Adaptive Brightness off by hand pauses;
     - Disable restores the original mode.
   - Update `datastore_map.md` and `aab_settings_schema.md`.
   - Add a STATE changelog line, plus an Owner-queue item "run §18 on 1.15.0-debug".
   - **`git rm -r dist/aab-bright-light-handoff`**, then remove its STATE pointer.
   - Delete this file. By then its content lives in DD-067 and the changelog lines.
7. **HBM readout (optional, only if Open question 1 says so).**
   - Prefer a direct HBM fact from `dumpsys display` (`mHbmMode`, the brightness float against the
     normal maximum) over AAB's 13-point calibration. That needs no screen stepping and no
     fingerprint invalidation.
   - Read in-process with the DUMP grant Tideo already declares (D-130), never through Shizuku.
     Shizuku stays at the three uses AGENTS.md anchors.
   - Read once per admitted reading while handed off and only while a surface shows it. There is
     no 2 s ticker.
   - Put a timeout on the dumpsys process.
   - If calibration is still needed: run it with the pipeline paused and through the controller,
     persist the dumpsys field it locked, and parse exponent floats.

## Open questions

1. **[2026-10-15] HBM readout: port it, and how?** AAB shows the real brightness, above 255 in HBM,
   while handed off. Options:
   - (a) No readout. The surfaces say "Android" and show lux.
   - (b) Unit 7 from a direct `dumpsys display` HBM field, DUMP grant only.
   - (c) Unit 7 with AAB's calibration.

   **Recommendation:** (a) for this train, then (b) if unit 0's adb facts show an HBM field on
   the owner's phone. (c) costs a 13-step screen sweep and re-calibration after every OS update.

## Execution rules

- **Phone over adb: read-only.** Allowed: `settings get`, `dumpsys`, `getprop`, `logcat -d`,
  `run-as … cat`. Not allowed: `settings put`, install, or any `pm`/`am`/`cmd` that changes state.
  When a check needs the phone changed, the owner does it and the agent reads the result.
- **RUNBOOK session discipline:**
  - One unit at a time, no parallel agents.
  - Each unit ends with the checkpoint above: STATE line, then ladder, commit,
    `--guards-only`, push.
  - Each runtime or `:platform` unit gets one blocking fresh-context glue-review pass.
  - Commit bodies say what ran and what could not be checked on the device.

## Verification

- **Every unit:** `scripts/ladder.sh`. Key suites: `:domain:test`, `:platform:testDebugUnitTest`,
  `:app:testDebugUnitTest` (including `PipelineFileLayoutTest` and `HardcodedStringCheckTest`).
- **On the phone after unit 5:** the owner installs the debug APK, sets a hand-off lux, and goes
  into sunlight or uses a strong light. Read over adb, read-only:
  - `screen_brightness_mode` reads `1` above the threshold and `0` below the exit;
  - `screen_brightness` doesn't move while handed off, apart from the OEM's own changes;
  - `logcat -d` shows no override pause, until the owner switches Adaptive Brightness off by
    hand;
  - after Disable, the mode equals what it was before the service started.

  Anything not observed stays on the Owner queue as §18 steps.
