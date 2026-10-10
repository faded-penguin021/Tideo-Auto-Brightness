# Bright-light hand-off to Android Adaptive Brightness (#136)

## Status

Spec only. No code has changed. The approved action is committing this plan to `docs/plans/`
(owner, 2026-10-01). Executing any unit below needs the owner's separate go-ahead. Accepting the
plan authorises commits on the session branch only: no PR, no reply on #136, no tag or release.

## Context

Issue #136 asks Tideo to give control back to Android's Adaptive Brightness above a lux
threshold. That lets the OEM's sunlight/HBM boost work, which no `SCREEN_BRIGHTNESS` value can
reach. A second user confirmed the need on a OnePlus 15. Tideo takes control back with
hysteresis once it gets darker.

The owner's replies in the thread fix the design:

- **"Relinquish control", not "turn on HBM at x lux".** Tideo does not need the OEM's HBM lux
  point; an earlier `dumpsys display` search for it came up empty.
- **A new runtime state.** The service stays on and keeps measuring lux but writes nothing, and
  manual-override detection is off.
- **Hysteresis.** Hand off at x lux and take back below x − y.
- **The threshold is set in lux.** The reporter's curve plateaus at 255 from 800 lux, but they
  want to keep control until 2000.
- **The value is checked against the profile's curve**: brightness at x must be ≥ 0.9 × max
  brightness.

Owner decisions (2026-10-01):

- **Exit point:** a **slider of 10–50 % below** the hand-off lux, default 20 %.
- **An invalid value blocks Apply.** Red text shows the minimum lux this profile's max brightness
  and curve allow.
- **The settings are per profile.**

There is no Tasker source, so this is a playbook-5 Tasker-independent feature and a recorded
deviation; the owner may build it in AAB later. v1.12.0 is tagged, so this is a new train:
**1.13.0 / versionCode 27** (minor: a new feature and new settings).

## Execution rules

- **Phone over adb: read-only.** Allowed: `settings get`, `dumpsys`, `getprop`, `logcat -d`,
  `run-as … cat`. Not allowed: `settings put`, install, or any `pm`/`am`/`cmd` that changes
  state. The E2E plan's no-mutation rule (before its S4 recovery contract) applies. When a check
  needs the phone changed, the owner does it and the agent reads the result.
- **RUNBOOK session discipline:**
  - One unit at a time, no parallel agents.
  - Each unit ends with `scripts/ladder.sh` green → a STATE Changelog line → commit → push.
  - Each runtime or `:platform` unit gets one blocking fresh-context glue-review pass.
  - Commit bodies say what ran and what could not be checked on the device.

## Design

### Validation (domain, pure)

New `domain/.../brightness/HandoffRules.kt`, next to `OverrideRules.kt`:

- `minimumHandoffLux(cfg): Int?` is the lowest whole lux L in 1..100 000 such that the **graph
  curve** stays ≥ 0.9 × `maxBrightness` from L up to the graph's 100k axis.
  - The graph curve is `BrightnessEngine.mapLuxToBrightness` clamped to [min, max], exactly what
    `BrightnessCurveChart` plots. It ignores scale, offset and the circadian taper, as the graph
    does.
  - It uses a dense log-spaced scan from the top down, then bisection to an integer. A scan,
    because DD-012 allows non-monotone curves (negative Form2B, Form3A ≤ 0).
  - It returns null when the curve never gets there.
  - On the default curve the minimum is about 25 100 lx (zone 3: `10 × form3A`).
- `exitLux(enter, exitPercent)` = `enter × (1 − p/100)`.
- `next(active, smoothedLux, …)` → `ENTER / STAY / EXIT / NONE`. It works on **smoothed** lux, so
  one sensor spike can't flip the mode. It **never enters with an invalid threshold**, so a
  context swap, import or later max-brightness change can't cause a pointless hand-off.

### Settings (per profile, in `AabSettings`)

Fields: `handoffEnabled = false`, `handoffLux: Int? = null`, `handoffExitPercent = 20`. They use
invented `%AAB_HandoffUse/Lux/Exit` names, following the D-116 precedent. Touched:

- the `AabSettingsContract` rules;
- `validate()` (clamp lux 1..100 000, exit 10..50, NaN guards);
- `SettingsDisplay` `valueFor` and labels;
- the `TaskerLegacyProfileSerializer` key=value lines;
- a **CRITICAL** `SettingsValidator` `FieldError("handoffLux")` when enabled and the value is null
  or below the minimum. Its message names the minimum and the 90 % brightness. The KDoc's
  "exactly 5 Tasker rules" is updated to name this rebuild-only rule.

They are additive with defaults, so no schema bump is needed (`ignoreUnknownKeys`). They are in no
preserve list, because they are per profile. Known consequence: an older app rejects an exported
profile that carries these keys at non-default values (strict import).

### Platform: never corrupt the saved brightness mode

`AndroidScreenBrightnessController.forceManualMode()` saves any non-MANUAL mode as the user's
pre-service mode (D-134). After a hand-off it would save **Tideo's own AUTOMATIC**. Disable or
panic would then leave the phone in Adaptive Brightness for a user who never had it on.

The fix:

- New interface methods `handOffToSystem()` and `reclaimFromSystem()`.
- A **persisted `handed_off` marker** in the same SharedPreferences, written with `commit()`, so
  it survives process death (the D-034(c) class).
- `forceManualMode()` never saves the mode while the marker is set.
- `restoreMode()` clears the marker.
- Every caller is covered: cycle, `setInitialBrightness`, `reclaimManualMode`, `PanicHandler`.

### Runtime state "handed off"

- `PipelineState.handedOff` survives hibernate, because the mode stays AUTOMATIC while the screen
  is off; the first cycle after wake decides.
- `PipelineCycleRunner.kt` is at 432 of its 434-line cap (`PipelineFileLayoutTest`). So the logic
  lives in a new `runtime/HandoffCoordinator.kt`, and `dimmingReadout` moves to its own file as a
  pure refactor to free budget.

In `runCycle`, after `engine.evaluate`:

- **ENTER:**
  - Set `handedOff` in state **first**, so both override gates see it before the mode flip's
    echo.
  - Arm the settle window (DC-009) and disengage dimming.
  - Then call `handOffToSystem()`.
- **While handed off:** keep publishing lux, thresholds and α as usual, but do no write,
  animation or dimming, and pass `brightnessChanged = false` to the throttle.
- **EXIT:**
  - Call `reclaimFromSystem()` and arm the settle window.
  - Clear the flag.
  - Fall through to the normal write/animation path.

**Override detection is off on both sibling paths:**

- `OverrideMonitor.GateState.handedOff`
- `PipelineCycleRunner.canPause`. Without this, `handleOverride` would reclaim MANUAL (DC-009) and
  undo the hand-off.

**Other events:**

- **Context swap, Apply, Resume** (`reapplyProfile`, `resume`, `setInitialBrightness`): skip the
  write when the new settings still keep the hand-off; otherwise exit first.
- **Pause** (automation intent): exits first, then pauses.
- **Disable / panic:** unchanged; `restoreMode()` returns the true pre-service mode.

### What the user sees

- **Curve & Brightness** gets a hand-off card:
  - a switch;
  - a lux field. When the switch turns on it is prefilled with the minimum rounded up to 100 lx.
    Red error text gives the minimum, or says the curve never reaches 90 % of max;
  - a 10–50 % slider with a live "takes back control below N lx";
  - a vertical `ChartMarker` at the hand-off lux on the graph.
- **Misc screen:** the same red banner appears when a max-brightness change there invalidates the
  hand-off. Draft errors are global, so otherwise Apply would be blocked with no visible reason.
- **Notification:** "Android is controlling brightness · N lx".
- **Dashboard pill, widget and tile:** "Android".
- **Live Debug:** a "Hand-off" metric. All strings go through `stringResource`, so
  `WRAPPER_CEILING` is not raised. Help text goes in `TaskerHelp`/`UserGuideScreen`.
- The public `STATE_CHANGED` broadcast is unchanged.

## Units (sequential)

Each unit: ladder green → STATE line → commit → push.

0. **Open the train.**
   - Confirm the tag with `git ls-remote --tags --refs origin 'refs/tags/v*'`.
   - The bump to 1.13.0 / vc27 and `changelogs/27.txt` already landed with the plugged-in fix
     (DD-025); add this plan's sentence to `27.txt` within the cap instead.
   - Add a checklist for this plan under STATE `## Active work` and fix the stale Owner-queue
     "train is 1.12.0" paragraph.
   - **Read-only adb facts:** model and SDK, `screen_brightness_mode`/`screen_brightness`, the
     settings maximum, `dumpsys display` HBM/auto-brightness lines, and the light sensor's
     `maxRange` from `dumpsys sensorservice`.
   - **If the sensor saturates below the default curve's ~25 100 lx minimum**, stop and raise it
     as an Owner-queue question first, because the feature could never trigger on default curves.
1. **Domain:** `HandoffRules` plus JVM tests. Cases: default curve, the reporter's 255 plateau,
   Battery Saver, negative Form2B, Form3A ≤ 0 → null, hysteresis with no flapping. Append ledger
   row DD-014 (feature, deviation, validation basis) with `[cited]`.
2. **Settings:** fields, contract, validate, display, legacy import and the CRITICAL rule. Extend
   these tests: `AabSettingsClampTest`, `AabSettingsMigrationTest`, `CurveParamFreedomTest`,
   `SettingsValidatorTest`, `LegacyImportRoundTripTest`, `ContextEngineTest` merge,
   `DraftSettingsViewModelTest` Apply blocked.
3. **Platform:** controller methods, the persisted marker, all test fakes updated. Robolectric
   tests: the saved MANUAL survives hand-off → forceManual → restore, a new instance with the
   marker set, and panic during a hand-off. Glue review.
4. **Runtime:** the `dimmingReadout` extraction (no behaviour change), `HandoffCoordinator`,
   state, both override gates and the event handling. Pipeline tests:
   - enter/exit at the band edges, with no write while handed off;
   - observer and commit overrides ignored;
   - a context swap to a profile with the feature off forces exit;
   - Pause exits;
   - `stop()` restores the real mode;
   - screen off/on keeps the flag and re-decides.

   Glue review.
5. **UI:** the card, the graph marker, the Misc banner, notification, pill, widget, tile, Live
   Debug and help text. `HardcodedStringCheckTest` ceilings unchanged; `:app:assembleDebug` and
   lint. Glue review, because the notification is glue.
6. **Close-out:**
   - Finalise `changelogs/27.txt` (≤ 500 codepoints).
   - Add `parity_gaps.md` dev-03 and `DEVICE_TEST_SCRIPT.md` §18, steps 58–60: entry and exit at
     the band edges, no override pause in sunlight, and disable restoring the original mode.
   - Update `datastore_map.md` and `aab_settings_schema.md`.
   - STATE changelog, plus an Owner-queue item "run §18 on 1.13.0-debug".
   - Delete this file; its content will by then live in DD-014 and changelog lines.

## Verification

- **Every unit:** `scripts/ladder.sh`. Key suites: `:domain:test`,
  `:platform:testDebugUnitTest`, `:app:testDebugUnitTest` (including `PipelineFileLayoutTest`
  and `HardcodedStringCheckTest`).
- **On the phone after unit 5:** the owner installs the debug APK, sets a hand-off lux, and goes
  into sunlight or uses a strong light. Read over adb, read-only:
  - `screen_brightness_mode` reads `1` above the threshold and `0` below the exit;
  - `screen_brightness` doesn't move while handed off, apart from the OEM's own changes;
  - `logcat -d` shows no override pause;
  - after Disable, the mode equals what it was before the service started.

  Anything not observed stays on the Owner queue as §18 steps.
