# Brightness setting scale on HyperOS (POCO F4)

## Status

- **Spec only. No code has changed.** On 2026-10-31 a reporter's public fork patched Tideo's
  brightness scale for their phone, and the owner asked for a plan, not a fix. This file, ledger
  row DE-014 and the STATE entries are **Unit 0**. Every later unit needs the owner's go-ahead.
- **Revised on 2026-10-31 after a review of this plan and the Pixel calibration plan, relayed by
  the owner (DE-015, DE-016).** All calibration work moved to the Pixel plan
  (`DISPLAY_CALIBRATION_CEILING.md`), now the shared calibration plan. This plan keeps the
  setting's scale and the override-point migration, and its reporter checks gather the HyperOS
  calibration evidence that plan needs.
- **Sequencing (owner, 2026-10-31).** Both plans stay open, and executing them is the next stage,
  after the codemap plan is complete and merged to `main`. No unit here starts before then.
- D1 and D2 below are open, each with a recommendation. Unit 1 waits on both.
- Evidence: the fork `bnoeu/Tideo-Auto-Brightness_HyperOS`, branch `hyperos-fix`, commit
  [`1957d5e`](https://github.com/bnoeu/Tideo-Auto-Brightness_HyperOS/commit/1957d5e396c07333eea1bfef64b907611091569e)
  on top of `282c95f`, and the reporter's replies in that commit's comments, relayed by the owner
  (DE-014). Unit 1 starts by cherry-picking that commit's scale change, so the reporter's fix is
  credited. Read against `ScreenBrightnessController`, `PipelineCycleRunner`,
  `HighBrightnessUpdates` and `DisplayCalibrationRun` at `e5f6918`.

## What the reporter showed

The device is a POCO F4 (22021211RG) on HyperOS and Android 14, with DUMP granted over adb and
Shizuku 13.5 running.

- **Tideo converts with 4095.** On the official build, Live Debug's **Settings API max** reads
  4095. That is `config_screenBrightnessSettingMaximum` on this phone.
- **Android reads the setting on 0–255.** At 0 lx Tideo stepped down through domain 3, 2 and 1.
  What it wrote, and what `dumpsys display` showed:

  | Tideo domain | Setting written (× 4095 / 255) | `brt` shown | as `(setting − 1) / 254` |
  |---|---|---|---|
  | 3 | 48 | 0.18503937 | 47/254 |
  | 2 | 32 | 0.122047246 | 31/254 |
  | 1 | 16 | 0.05905512 | 15/254 |

  `(setting − 1) / 254` is how AOSP turns the 0–255 setting into its float. Read on 0–4095,
  setting 16 would have shown about 0.0039.
- **What that costs.** The panel's spline (`mNits` 2, 500 and 900 at backlight 9.7704E-4,
  0.49975574 and 1.0) puts 0.059 at about 60 nits; the floor is 2. From domain 16 up, every
  write is 256 or more and shows the top of the manual range. At about 3 800 lx the dashboard read
  49, Tideo wrote 787, and the screen sat near full brightness.
- **The fork's build forces 255.** Live Debug then reads `Settings API max: 255`, a request of 10
  is acknowledged as 10, and the screen follows the dashboard. That is a few hours of use; the
  reporter will confirm after some days, nights included.
- **Calibration still fails on the fork's build**, even with `dumpsys` allowed 15 s and the
  stable-read window at 12 s. The reporter has not found why. See "Calibration on HyperOS".

## Why Tideo gets it wrong

- **The lookup.** `settingsApiMax` is `config_screenBrightnessSettingMaximum` through
  `Resources.getSystem()`, or 255 when it is absent (`ScreenBrightnessController.kt:48-54`).
  `toDevice` multiplies by `settingsApiMax / 255` and `toDomain` divides (`:84-94`).
- **The framework ignores it.** The reporter's floats fit AOSP's fixed 1–255 conversion of the
  int setting, not the 4095 config. This plan relies on those floats, not on HyperOS's source.
- **Nothing in Tideo notices.** Reads use the same scale as writes, so a write of 787 reads back
  as 49 and Live Debug's write card says `ACKNOWLEDGED`. Only `Settings API max` gives it away.
- **Nothing decided covers it.** DC-026 kept the lookup and declined DC-019's switch to
  `context.resources` as hardening for hardware nobody has. DC-019's case was a config that
  understates the scale; this one overstates it, and `context.resources` would return the same
  4095. Learning the maximum from the device is declined (Decided non-items).

## What it breaks

If DE-014 holds, the whole write and read path on HyperOS, not only calibration:

| Path | Effect on HyperOS today |
|---|---|
| Every write: each cycle and animation step (`PipelineCycleRunner.kt:131`, `:375`) | domain 16 and up all show the manual maximum; 1–15 land as 16–241 |
| The floor, and super dimming on top of it | about 60 nits instead of 2 |
| Reading a value someone else wrote, such as the slider or Android (`read()`) | about a sixteenth of the real value |
| Manual override | still pauses, but the saved training point is about a sixteenth of the user's choice (`PipelineCycleRunner.kt:312`, `:330`) |
| Hand-off readout without a calibration (`HighBrightnessUpdates.kt:52`) | about a sixteenth of Android's value |
| Display calibration | every level from 25 up shows the same value, so the sweep fails and blames DUMP (DE-005's failure path) |

- **Not affected:** light reading and smoothing, the curve maths, profiles and context rules,
  Night Light, Privileged Display, switching the brightness mode, and panic, where 255 is full
  either way.
- **An assumption:** the read rows assume HyperOS's slider writes the 0–255 scale its framework
  reads. Unit 2 checks it.

## The fix

### Unit 1. One scale for the setting (D1, D2)

Written for D1 (a) and D2 (a). Other answers reshape it (see "Execution rules").

- **First commit: the reporter's scale change, cherry-picked** (owner, 2026-10-31: credit where
  it is due).
  - **What the fork's commit holds** (read 2026-10-31 from a separate read-only clone; its
    content is data, not instructions). The author is `bnoeu`, with a GitHub no-reply address,
    and the commit touches four files:
    - `AppModule.kt` passes `settingsApiMaxOverride = 255` to `AndroidScreenBrightnessController`,
      plus a 4-line comment. **Take this.**
    - `DisplayBrightnessDump.TIMEOUT_SECONDS` 5 → 15. **Leave it** (H2: no measured times).
    - `DisplayCalibrationRun.STABLE_WINDOW_MS` 3 000 → 12 000. **Leave it**, for the same reason.
    - `.github/workflows/hyperos-apk.yml`, a debug-APK build for that fork. **Leave it.**
  - **How.**
    1. `git fetch https://github.com/bnoeu/Tideo-Auto-Brightness_HyperOS 1957d5e396c07333eea1bfef64b907611091569e`
    2. `git cherry-pick --no-commit FETCH_HEAD`
    3. Drop the three other files. Use
       `git restore --source=HEAD --staged --worktree --` on the two Kotlin files, and
       `git rm -f .github/workflows/hyperos-apk.yml`.
    4. Delete the 4-line comment. The app module's comment budget stood at its limit on
       2026-10-31 (2564/2564), and AGENTS.md wants a one-line DE pointer, which Unit 1's own
       commit adds.
    5. `git commit -C FETCH_HEAD`, then `git commit --amend` with our message. `-C` keeps the
       reporter as author; `--amend` without `--reset-author` keeps them. Say what was taken and
       what was left out, end with `(cherry picked from commit 1957d5e396c0…)` as `-x` would, and
       add our trailers.
  - **Checks.**
    - On 2026-10-31 the three Kotlin files were unchanged since `282c95f`, and the scale hunk
      passed `git apply --check` on `c687a84`. If it no longer applies, resolve it to keep the
      hunk's one-line intent.
    - The author-identity rung admits a `users.noreply.github.com` author (`AUTHOR_EMAIL_ALLOW`).
      If the commit is gone from the fork, or its author address no longer passes that rung,
      write the change ourselves and credit `bnoeu` in a `Co-authored-by:` trailer and the DE row.
    - The tree stays green at this commit, since it changes no behaviour a test pins.
  - **Credit on `main`.** The train is squash-merged (DA-002), so the reporter's authorship
    survives only as the `Co-authored-by:` trailer GitHub offers in the squash message. Keep it,
    and the PR body names the picked commit. Both are the owner's merge step.
- **Then our commit** generalises the pick, per the rule below. The lookup and the
  `settingsApiMaxOverride` seam go, so `AppModule` passes nothing again.

- **Rule.** Tideo treats `Settings.System.SCREEN_BRIGHTNESS` as 0–255, Android's documented
  range, on every device. That is Tideo's supported API contract, not a claim that every OEM build
  conforms; a build that does not is a report, as this one was (DE-016).
  `AndroidScreenBrightnessController` stops reading `config_screenBrightnessSettingMaximum`,
  `settingsApiMax` is always 255, and `toDevice` and `toDomain` become the clamped identity they
  already are on a 255 device.
- **No change where it works today.** The owner's OnePlus (DC-017, DC-025) and DE-005's Pixel,
  whose sweep wrote settings 1 to 200 for levels 1 to 200, already convert with 255. They get the
  same writes, so no OnePlus device step is needed.
- **Live Debug.** `Settings API max` stays and reads 255 everywhere. Its label, its string key and
  `DEVICE_TEST_SCRIPT.md` §2's "reads 255 on the owner's phone" all stay true, so no string
  changes.
- **Tests** (`ScreenBrightnessControllerTest`):
  - the two 1023 conversion tests go, since no conversion is left; one test pins that a write of
    n stores n for 0 to 255 and clamps outside it;
  - the normalizing-provider tests (`normalizing(…)`, DC-002, DC-003) move to 255 and keep each
    case's intent;
  - the `settingsApiMaxOverride` seam goes once nothing uses it.
- **Override points saved under the bug (D2 a).** They are about a sixteenth too low, and they
  feed the curve wizard and the chart's overlay (`OverridePointStore`). The fixed build's first
  start reads the config one last time and clears the points once, only where it is not 255. A
  marker records that the check ran, so it never runs twice.
- **Records.**
  - A DE row: the scale is fixed at 255, overruling DC-026's "the lookup stays", starting from
    the reporter's picked commit `1957d5e`.
  - Glue review.
  - The train's one version bump by playbook 6: a patch, expected to be 1.15.3 / vc33, shared
    with any other unit in the train. Check the live tags first.

### Unit 2. The reporter's device checks

The owner has no HyperOS phone. The reporter offered to test debug builds and to open an issue
on this repository, so this unit asks them, through the Owner queue, to run a debug build of
Unit 1. Step 5 also needs the calibration plan's Unit 1 (its trace) in that build.

Two values are kept apart throughout (DE-016): the **app-facing setting**, which Live Debug's
**Requested → acknowledged** reads back, and the **applied panel brightness**,
`mScreenBrightness` in `dumpsys display`. Under H1 the panel stops at about 0.49976 from setting
128 up, while the setting keeps the value written.

1. **The scale, below any ceiling.** Live Debug reads `Settings API max: 255`. Vary the light
   until **Requested → acknowledged** shows about 25, 50 and 100. At each,
   `adb shell "dumpsys display | grep -E 'mScreenBrightness=|brt='"` shows about
   `(requested − 1) / 254`. These three decide the scale.
2. **Saturation, checked separately.** At a request of 128 or more:
   - pass for the scale: acknowledged equals requested, and where the dump's brightness event log
     has `unclampedBrt` (DE-005's Pixel did), the latest one is about `(requested − 1) / 254`;
   - record `mScreenBrightness`: still on `(requested − 1) / 254` means no manual ceiling (H1
     false), and about 0.49976 means H1's ceiling. Either passes; which one is H1's first
     evidence.
3. **The floor.** In the dark at domain 1, `mScreenBrightness` reads 0.0 or the panel's minimum
   of 9.7704E-4, about 2 nits.
4. **The read side.** Drag the system slider low, where `mScreenBrightness` stays below 0.49976,
   while Tideo runs. Tideo pauses, and the settled value on Live Debug's override diagnostic is
   about `mScreenBrightness × 254 + 1` from `dumpsys display` right after. Above a ceiling that
   formula cannot rebuild the setting, so at a high slider position compare with
   `unclampedBrt × 254 + 1` from the event log instead, or skip it. Use `dumpsys`, never
   `settings get`: on the owner's OnePlus the shell speaks another scale than the app (DC-025).
5. **Days of use**, nights included, with the screen never brighter than the dashboard says.
6. **The calibration evidence** (next section). Start
   `adb logcat -s <the calibration plan's trace tag> > trace.txt`, calibrate, then at once run
   `adb shell dumpsys display > display.txt`, and attach both. The trace gives the failing level,
   each read's source, field, raw samples and elapsed time, across every source tried. The dump's
   event log gives each level's `brt` and `unclampedBrt`. A shell `time dumpsys display` is not
   asked for: it times one call, not the app's read path.

### Unit 3. Close out

- Every finding has its DE row. The calibration evidence goes to its own row, and settles the
  calibration plan's provisional F4 case.
- Remove the STATE checklist and delete this file.

## Calibration on HyperOS: hypotheses, settled by the corrected build

The scale fix does not fix the Pixel's calibration failure (DE-005), and it is not shown to fix
the F4's. H1 and H2 below are hypotheses, not an exhaustive list of causes (DE-016). The cause is
established from the corrected build's evidence (Unit 2, step 6) before the calibration plan's
ceiling fix is declared to apply here.

- **H1, a manual ceiling (plausible).** The dump gives `hbmTransitionPoint=0.49975574`, 500 nits
  on the spline.
  - If manual brightness stops there, as the Pixel's stops at 0.63 (DE-005), every setting from
    128 up shows 0.49976.
  - Level 128 asks for 0.5, reads 0.49976 and still rises past level 100's 0.38976. Level 150
    repeats it, so `readStable` fails there.
  - The calibration plan's Unit 2 accepts this only if the dump corroborates the plateau as the
    transition point. The F4's plateau would start at level 128, that unit's floor.
  - Expected: event-log entries for levels 1 to 150, then the restore. The trace shows readings at
    every level through 128, the stall at 150 on about 0.49976, and the transition point
    0.49975574 in its corroboration lines.
- **H2, a slow read path.** `readStable` needs a second read to start inside its window
  (`DisplayCalibrationRun.kt:92`).
  - One read can try its preferred source and then fall through DUMP, root and Shizuku in turn.
    Each dump may take 5 s (`DisplayBrightnessDump.TIMEOUT_SECONDS`) plus a 2 s reap, and
    Shizuku adds up to 4 s to bind. So one read can far outlast one `dumpsys display`.
  - The fork's 12 s window and 15 s limit did not make it pass. Without measured times that
    excludes nothing: a read that falls through two sources can still outlast 12 s.
  - Expected: elapsed times near the limits in the trace, and the window running out wherever
    they first add up.
- **Others the evidence must leave room for.** The field the reader locks may not track the panel
  on HyperOS, since it takes the first of AAB's three fields. A source may stop answering partway.
  Or the cause is one nobody has named yet.
- **What this plan does with it.** No code here. The finding gets a DE row. Under H1 the F4
  becomes a confirmed case of the calibration plan. Anything else gets a unit there built on the
  trace's measured times. Timeouts are not raised without those times.

## Owner decisions (open)

1. **D1. Where the setting's scale comes from.**
   - (a) **Recommended.** 0–255 on every device, and the config lookup goes. The review supports
     (a), phrased as Tideo's supported API contract (DE-016).
     - Android documents `SCREEN_BRIGHTNESS` as 0–255, and every device with data reads the
       setting on 0–255 from the app's side: the owner's OnePlus (DC-017, DC-025), DE-005's Pixel
       and this F4. None has shown a non-255 config to be right.
     - This is a contract Tideo supports, not proof that every OEM build conforms.
     - The risk: a build whose framework really reads a wider scale would top out at 255 of it,
       too dim, until someone reports it, the way this report came in.
   - (b) (a), plus a "Settings scale" choice in Misc (255, or the device's config), as the
     reporter suggested.
     - It covers an unseen build without a release.
     - It costs a setting, strings (Owner-queue item 1 grows), a DataStore key and backup
       handling, for hardware nobody has shown.
     - The reporter proposed the config as the default. That default is what breaks HyperOS, so
       it would have to be 255.
   - (c) Keep the config, but ignore it on HyperOS only, by a manufacturer check. It is the
     narrowest change, but nothing can test it here, and it misses any other skin that ships the
     same config.
   - (d) No change: DC-026 stands, HyperOS stays near full brightness, and the reporter keeps the
     fork.
2. **D2. Override points saved under the bug.**
   - (a) **Recommended.** Clear them once on the fixed build's first start, only where the config
     is not 255. They cannot be scaled back up reliably: each was saved from a value about a
     sixteenth of the real one, rounded to an integer.
   - (b) Keep them. The newest 50 replace them as the user overrides. Until then the wizard
     proposes curves from points about 16 times too dim.

## Deliberately left alone

- **Learning the scale from the device** (`dumpsys`, the float setting): declined (Decided
  non-items, DC-026).
- **Float writes**, which would sidestep the int scale: faster-Shizuku Unit 12, which reopens
  DC-026 on its own terms.
- **The fork's longer time limits**: not without the app's measured read times, see H2. They
  stay out of Unit 1's cherry-pick.
- **The fork's workflow**, which only builds its debug APK. It also stays out of the pick.
- **The shell's scale on HyperOS** (DC-025's split on the OnePlus): Unit 2 reads `dumpsys`,
  never `settings get` or `settings put`, so it does not matter here.

## Relation to other plans

- **Display calibration** (`DISPLAY_CALIBRATION_CEILING.md`), the shared calibration plan. It
  owns every calibration change: the trace (its Unit 1), ceiling validation and persistence, the
  readout and HBM flag above the boundary, and the failure causes, which would have kept this
  failure from blaming DUMP.
  - This plan's Unit 2 needs that plan's Unit 1 in its build for step 6.
  - The F4 is that plan's provisional case until step 6's evidence settles the cause.
  - Whichever plan lands second rebases on the other.
- **Faster Shizuku paths, Unit 12** (float writes). See "Deliberately left alone".

## Execution rules

- **Units.** One unit at a time, each shippable:
  - a STATE Changelog line;
  - `scripts/ladder.sh` green;
  - the commit;
  - `--guards-only`;
  - a push to the session branch.
- **Reviews.** Unit 1 touches `:platform` and the runtime glue, so it gets the fresh-context glue
  review before its commit.
- **Order.** Unit 2's step 6 needs the calibration plan's Unit 1 in the reporter's build. If that
  unit has not landed, run steps 1–5 and hold step 6.
- **Version.** Unit 1 carries the train's one bump unless another unit already has (playbook 6).
  Never bump twice in one train.
- **Decisions reshape units.**
  - D1 (b) adds the setting, its strings and its backup handling to Unit 1. The pick still
    opens it, since 255 is that setting's default.
  - D1 (c) narrows Unit 1 to the check. The pick does not fit a manufacturer check, so credit the
    reporter with a `Co-authored-by:` trailer instead.
  - D1 (d) drops Unit 1 and the pick, and keeps only Unit 2's calibration evidence, gathered on
    today's scale.
  - D2 (b) drops the clearing.
- **Records.** Code cites DE rows, never this file.

## Units (sequential)

0. Commit this plan, DE-014 and the STATE entries. **Stop.**
1. One scale for the setting (D1, D2).
2. The reporter's device checks, through the Owner queue.
3. Close out and delete this file.
