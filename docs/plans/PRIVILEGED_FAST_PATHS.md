# Faster Shizuku paths and Android 17 display APIs (OnePlus 13 on ColorOS 17)

## Status

- **Spec only. No code has changed.** The owner approved this plan on 2026-10-19. That approval
  authorised **Unit 0 only**: committing this file on the session branch
  `claude/e2e-1.15.0-record`. Every later unit needs the owner's separate go-ahead.
- No unit opens a PR, replies on an issue, tags or releases.
- **Unit 5 gained Shizuku authorisation for an adb-elevated user** (2026-10-31, from issue #157,
  DE-017). It stays under the device gate below unless the owner moves it.
- **Device gate (owner, 2026-10-19).** Units 2–13 start only after the owner's OnePlus 13
  (CPH2653) has updated to **ColorOS 17 (Android 17, API 37)**. OxygenOS has reached its end of
  life, so the plan targets ColorOS 17.
  - Units 0 and 1 need no phone.
  - Unit 3 (docs only) needs no phone either. It may move earlier if the owner says so.
- **Testability rule (owner, 2026-10-19).** The plan covers only what works on that phone and can
  be checked there.
  - A unit whose device check fails is dropped and recorded, never shipped untested.
  - Root-only paths stay as they are, because the owner has no root.

## Context

**Why.** A brainstorm on 2026-10-19 found the main inefficiency. Every Shizuku operation starts
a fresh helper process and kills it afterwards. `ShizukuShell.kt:86` unbinds with
`remove = true`, which runs `ShizukuUserService.destroy()` and then `System.exit(0)`. So each
operation pays for a new `app_process` VM. That happens in three places:
- **Night Light writes.** On a build that ignores the Kelvin key (DC-057), every 60 s ramp tick
  writes through Shizuku.
- **Hand-off readout.** The 2 s `dumpsys display` loop does the same whenever Shizuku is the
  route that answers (DD-077, `HighBrightnessUpdates.kt:66`).
- **Wi-Fi SSID.** Each SSID resolution starts a helper too.

**Verified in AOSP `android17-release` (`android-17.0.0_r1`), 2026-10-19.**

| Permission | Who can hold it | Unlocks |
|---|---|---|
| `CONTROL_DISPLAY_BRIGHTNESS` | signature only; **the shell has it** | `getBrightnessInfo` (real level, min/max, HBM mode, max reason, window override) |
| `MANAGE_ACTIVITY_TASKS` | signature/recents; the shell has it | `registerTaskStackListener` (push foreground app) |
| `WRITE_SECURE_SETTINGS` | Tideo has it at ELEVATED; the shell has it | `setUserDisabledHdrTypes` / `setAreUserDisabledHdrTypesAllowed` (live HDR) |
| `DUMP`, `PACKAGE_USAGE_STATS` | `development` flag, so `pm grant`-able; the shell has them | one-tap grants |
| `DEVICE_POWER`, `MANAGE_APP_OPS_MODES` | the shell has them | `cmd deviceidle whitelist`, `appops set` |

Other source facts:
- The display service reads the two HDR settings only in `systemReady()`. That is why DB-044's
  toggle may need a reboot.
- Display callbacks are keyed per calling process (`mCallbacks.get(callingPid)`). So a listener
  must live in Tideo's own helper, never behind `ShizukuBinderWrapper`.
- Subscribing to brightness events needs no permission.

**The DisplayManager change was Android 16 QPR2, not QPR1.** QPR2 is the minor SDK release,
API 36.1 (`BAKLAVA_1`), and Android 17 carries the same set. It made public:
- `DisplayManager.getBrightness(displayId, unit)`. It needs no permission, but it returns the
  **stored setting**, not the level Android chose.
- `setBrightness(displayId, value, unit)`. It needs only `WRITE_SETTINGS`.
  `BRIGHTNESS_UNIT_PERCENTAGE` is the slider's perceptual 0–100 scale over the *current* min–max.
- `EVENT_TYPE_DISPLAY_BRIGHTNESS`, delivered through API 36's
  `registerDisplayListener(Executor, eventFilter, listener)`.

API 36 also added `EVENT_TYPE_DISPLAY_STATE`, but nothing in Tideo needs it. Compiling against
36.1 needs AGP ≥ 8.13.0; the repo has 8.13.2.

**OxygenOS 16 priors, re-measured in Unit 2.**
- DD-083: `BrightnessInfo` floats are in panel units (755 of 4095). OPlus's own auto controller
  is in charge, and AOSP's HBM controller is idle. The brightness setting tracks auto mode.
- DE-007: in sunlight, handed off (DE-008), BrightnessInfo's brightness, the setting and
  `cmd display get-brightness` all stop at 4095 while `mScreenBrightness` goes on to 4174. So
  on OxygenOS 16, BrightnessInfo does not carry High Brightness Mode, and the setting tracks
  auto only up to 4095.
- DC-025: apps see 0–255 while the shell sees 0–4095.
- DC-054: the shell holds `CONTROL_DISPLAY_COLOR_TRANSFORMS`.
- DC-057 and DC-058: Night Light goes through `color_display`; Shizuku posts bind callbacks to
  the main looper.

**Decisions this plan reopens.** The owner says flagged decisions can be reopened. Each reopen
becomes a new DD row in the unit that acts on it:
- DC-057's per-operation binding (Unit 6);
- DD-077/DD-078's readout source and AAB's 2 s tick (Units 7 and 9);
- DB-044's "migrate only to a non-hidden API" (Unit 11);
- DC-026's conversion freeze (Unit 12 only);
- `privilege_tiers.md`'s "never holds a runtime binder open".

**Not reopened:**
- the Decided non-items: refresh-rate/OEM keys, manual Extra Dim, the grayscale quick action;
- D-094 (exact alarms);
- targetSdk, which stays 36. Moving to 37 is playbook 7, separate.

## Execution rules

- **Units.** One unit at a time, following playbook 5 and session discipline. Each unit runs, in
  order:
  - a STATE Changelog line;
  - `scripts/ladder.sh` green (`--guards-only` for docs-only units);
  - the commit;
  - `--guards-only` again;
  - a push to the session branch.
- **Reviews.**
  - Every `:platform` or runtime unit gets a blocking fresh-context glue review.
  - Any change to AGENTS.md or `scripts/guards/doc-facts.sh` gets a rule review: fresh context,
    strongest tier, no self-review fallback. If no fresh context can be spawned, the change waits
    in the Owner queue.
- **Phone (owner, 2026-10-19).** Units run from the owner's Docker container, which reaches the
  phone over wireless adb. The device serial and address stay in the environment, never in a
  file (playbook 9).
  - **Reads.** The agent may run any read-only command: `getprop`, `dumpsys`, `settings get`,
    `pm dump`, `aflags list`, `cmd appops get`, `logcat -d`.
  - **Settings writes.** The agent may run `settings put` when a unit needs it and it can't harm
    the phone.
    - Before each write it records the prior value in the unit's notes, and it restores that
      value before the unit ends.
    - It never touches adb, developer-option, lock-screen, network, display-size or
      provisioning keys.
    - Anything unexpected stops the unit and is restored first.
  - **Harness.** Builds go on only through `e2e/run.sh --install`, and scenario writes go through
    `e2e/run.sh`, so they are journaled. Never `pm clear`, uninstall, reboot or `bmgr`
    (playbook 9).
  - **Owner only.** Every other change stays with the owner: other `pm`/`am`/`cmd`/`appops`
    changes, taps inside Tideo that no E2E scenario performs, and physical actions such as
    sunlight or opening apps.
- **Code.**
  - Hidden-interface calls happen only inside the privileged helper process
    (`privilege_tiers.md`).
  - Every new path probes for capability and keeps its verdict keyed by `Build.FINGERPRINT`, as
    `NightLightVerdictStore` and `DisplayCalibrationStore` do. It falls back to today's path on
    any failure.
  - BASIC behaviour is unchanged except for the public Android 17 APIs (Units 9 and 12).
  - No new dependencies.
- **Version.** One bump per train. The first code unit takes the next minor (1.16.0) and the next free versionCode, and adds `changelogs/<vc>.txt` (≤ 500 characters).
- **Shizuku lockstep.** Any change to the set of Shizuku runtime uses updates all of these in the
  same commit (DD-051: one consumer file per use):
  - AGENTS.md's claim;
  - `SHIZUKU_USAGE.md`;
  - `privilege_tiers.md`;
  - `doc-facts.sh`'s `shizuku_expected`.
- **Device steps and records.**
  - Each unit adds its DEVICE_TEST_SCRIPT.md §19 steps (numbering from 62) and the matching
    `e2e/scenarios.toml` rows in the same commit.
  - New strings go through resources, and each one adds to Owner-queue item 1's Chinese backlog.
  - Code comments cite DD rows, never this plan, and ledger rows never cite its path.

## Units (sequential)

**0. Commit the plan (no device).**
- Write this text, minus the approval note at the top, to `docs/plans/PRIVILEGED_FAST_PATHS.md`.
- In STATE `## Active work`, add the item with a one-line unit checklist and the ColorOS 17 gate.
- Add a STATE Changelog line.
- Run `--guards-only`, commit, run `--guards-only` again, push. **Stop.**

**1. Astra review of the plan (the owner runs it).**
- The owner gives Astra this file, pointing it at the "Review brief for Astra" section.
- Agent, next session: triage every finding by user-visible impact, as DD-057 did. An accepted
  finding amends the plan; a rejected one gets its reason.
- Commit the revised plan, one DD row (the findings and each disposition, naming the plan in
  prose) and a STATE line.
- Acceptance: every finding has a recorded disposition.

**2. ColorOS 17 baseline and device facts (the gate opens here; no code).**
- **Baseline.**
  - The agent runs the playbook-9 E2E suite on the installed build. The owner provides its
    preconditions: phone unlocked, hands off the display settings.
  - The owner recalibrates the display range, because the fingerprint changed (DD-077), and
    lets Night Light re-probe (DC-057).
  - Any failure is fixed under playbook 4 first.
- **Facts (the agent runs them over adb; all read-only except F5's restored mode switch):**
  - F1 `getprop ro.build.version.sdk`, `ro.build.version.sdk_full` and `ro.build.display.id`.
    Expect 37.
  - F2 `aflags list | grep display_listener_performance_improvements`. This gates Unit 9.
  - F3 `pm dump com.android.shell | grep -E 'CONTROL_DISPLAY_BRIGHTNESS|MANAGE_ACTIVITY_TASKS|DEVICE_POWER|MANAGE_APP_OPS_MODES|WRITE_SECURE_SETTINGS'`.
    Each must read `granted=true`. This gates Units 5, 7, 10 and 11.
  - F4 `dumpsys display | grep -E 'mCachedBrightnessInfo|mUserDisabledHdrTypes|mAreUserDisabledHdrTypesAllowed|HdrCapabilities|AutomaticBrightness'`.
    It shows whether BrightnessInfo tracks the panel, whether the HDR fields exist, and which
    auto controller runs. This gates Units 7 and 11 and re-checks the exclusions. For Unit 7,
    also run it handed off in sunlight, with `mScreenBrightness` above 4095 (DE-007).
  - F5 The agent switches `screen_brightness_mode` with `settings put`, recording and restoring
    the prior value, and reads `screen_brightness` in each mode. Does the setting track auto
    (the DD-083 prior)? Then, with the owner's slider at the top, it reads the stored maximum
    (`e2e/devices/CPH2653.toml`, DC-025).
  - F6 `dumpsys package com.tideo.autobrightness | grep -E 'permission\.(DUMP|WRITE_SECURE_SETTINGS)'`,
    `cmd appops get com.tideo.autobrightness GET_USAGE_STATS` and
    `dumpsys deviceidle whitelist | grep -c tideo`. These are Unit 5's baseline.
  - F7 The Night Light verdict on ColorOS 17. It decides how much Unit 6 helps Night Light.
  - F8 The owner's eye test for Unit 12: at night, does a low-brightness transition (domain
    about 2–10) show visible steps?
- **Records.** One DD row holding the facts and each unit's gate outcome. Update `CPH2653.toml`
  if the stored maximum changed. Drop failed units from this plan and from the STATE checklist.

**3. Split `SHIZUKU_USAGE.md` (docs only; no device).**
- Replace the single 188-word sentence with:
  - a short intro saying Shizuku is optional;
  - one short item per runtime use. Today there are four: no-Location Wi-Fi SSID, force dark,
    Night Light temperature through the display service, and the display brightness readout;
  - one item for the one-time grants.
- Keep the plain language and the same facts. Leave README's pointer unchanged. The text must
  agree with AGENTS.md "Architecture" and with `privilege_tiers.md`.
- From here on, each unit that changes a use edits its own item.
- Acceptance: `--guards-only` green, and the commit body states that the four items match
  AGENTS.md.

**4. Open the code train and measure the helper (no behaviour change).**
- Do the version bump.
- `ShizukuShell` counts binds, helper starts (`onServiceConnected`), each call's round-trip time
  in ms, and failures and timeouts, and exposes them as a StateFlow.
- Live Debug gets a "Shizuku helper" card (`LiveDebugViewModel`, `LiveDebugScreen`).
- Tests: the stats reducer, and `HardcodedStringCheckTest`. Glue review.
- Device: during a Night Light ramp or a display-range calibration with Shizuku answering,
  record helper starts against calls, and the latency. That is the baseline, recorded in a DD
  row.

**5. One-tap Shizuku grants and battery-optimization exemption.**
- **Authorisation first, for an adb-elevated user too** (added 2026-10-31 from issue #157,
  DE-017; owner: "the Shizuku stuff belongs to the privileged paths plan").
  - The gap. Tideo asks Shizuku for authorisation only from onboarding's elevated step and
    Privileged Display's grant card, and both show their "Use Shizuku" button only below ELEVATED
    (`OnboardingScreen.kt:368`, `PrivilegedDisplayScreen.kt:154`). A user elevated over adb is
    never asked, so every runtime use stays off for them with Shizuku running: the dump fallback
    (DD-077), Night Light's binder route (DC-057), the SSID strategy and force-dark. #157's
    POCO F4 is that case: Shizuku 13.5 running, DUMP granted, Tideo not authorised. This unit's
    own buttons would meet the same wall.
  - The change. While Shizuku runs and Tideo is not authorised, Privileged Display also shows
    "Use Shizuku" at ELEVATED, and each one-tap button below asks for authorisation first.
    Reusing `requestGrant` keeps its single flight and prompt timeout, and the existing string;
    its WRITE_SECURE_SETTINGS grant is then a repeat, which the glue review confirms is harmless,
    or the request is split out.
  - The advice that sends users to Shizuku cannot be followed in the app today:
    `flash_calibration_failed`, `handoff_calibration_failed_banner` and
    `help_handoff_calibration` ("allow root or Shizuku"), and `pd_night_light_needs_shizuku`
    ("Start Shizuku"). The review decides whether they change. The calibration banner is also
    the display-calibration plan's Unit 4; whichever lands second rebases.
  - Device: the owner removes Tideo's authorisation in the Shizuku app, then Privileged Display
    offers "Use Shizuku" at ELEVATED; after the tap, a Shizuku use answers again (Unit 4's helper
    card counts the call).
- `ShizukuUserService`, in the grant process, gets three fixed operations. Each takes the package
  from its own Context (DA-031), uses argv only and bounds its output:
  - `pm grant <own> android.permission.DUMP`;
  - `appops set <own> GET_USAGE_STATS allow`;
  - `cmd deviceidle whitelist +<own>` / `-<own>`.
- `ShizukuGrantGateway` / `PrivilegeManager` get request methods modelled on `requestGrant`:
  single flight, with prompt and bind timeouts.
- State is read through public calls: `checkSelfPermission(DUMP)`, `hasUsageStatsAccess()`
  (`ForegroundAppMonitor.kt:60`) and `PowerManager.isIgnoringBatteryOptimizations`.
- UI:
  - "Grant with Shizuku" beside the DUMP command (`ContextsViewModel.dumpGrantCommand`);
  - "Allow with Shizuku" in onboarding's usage-access step;
  - "Exempt from battery optimization", with an undo.
  - The adb instructions stay ("ADB always offered").
- Docs: AUTOMATION.md's `SERVICE_ON` note, `privilege_tiers.md`'s grant channels and
  `SHIZUKU_USAGE.md`'s grants item. Grants are not runtime uses, so the constant stays at 4.
- Tests: `PrivilegeManagerTest`, gateway fakes and the UI tests. Glue review.
- Device:
  - the owner taps each button, and the agent's F6 commands confirm each change;
  - with Tideo stopped and exempt, `SERVICE_ON` starts the service. It's sent through the E2E
    automation scenario, or by the owner.

**6. Keep the helper resident (reopens DC-057's per-operation binding).**
- DE-001 already holds the helper bound, `daemon(false)`; this unit adds only the idle and
  teardown rules that still earn their place, on that same binding, never a second one.
- `ShizukuShell` keeps one binding while a feature needs it:
  - `daemon(false)`, so the helper dies with Tideo;
  - bind on first use;
  - the existing `serial` Mutex keeps calls from overlapping (DD-077);
  - after an idle window longer than the 60 s Night Light tick (Astra Q1), unbind with
    `remove = true`. Never while a subscription from Unit 10 is active;
  - rebind after binder death or a Shizuku restart;
  - release on `AmbientMonitoringService` teardown;
  - never wait for a bind on the main thread (DC-058);
  - keep the bind-timeout unbind (D-145) and bump the user-service version.
- Tests, with a fake-binder connector seam: the idle timeout, death followed by rebind, serial
  calls, timeout unbind and teardown. Glue review.
- Docs: helper lifetime in `privilege_tiers.md` and the `SHIZUKU_USAGE.md` intro. A DD row
  reopens DC-057.
- Device:
  - Unit 4's card shows about one helper start per active period;
  - `dumpsys meminfo` records the helper's memory, which matters under Android 17's memory
    limits;
  - Night Light, SSID and the readout still work;
  - with Shizuku stopped the fallbacks work;
  - with Tideo disabled the helper process is gone.

**7. Read the brightness from `BrightnessInfo` through the helper.**
- **Gate (DE-007).** Run this unit only if F4's sunlight run shows
  `mCachedBrightnessInfo.brightness` passing 4095 together with `mScreenBrightness`. On
  OxygenOS 16 it stopped at 4095, so this source would cap the readout at 255 and the HBM flash
  would never fire. If it still stops, drop the unit; dumpsys stays the readout's source.
- A new helper operation returns BrightnessInfo as primitives: brightness, min, max, HBM mode,
  transition point, max reason and window override. The helper reads it by reflection, following
  the `ColorDisplayBinder` pattern. F3 shows the shell holds `CONTROL_DISPLAY_BRIGHTNESS`.
- `DisplayBrightnessReader` tries the BrightnessInfo source first whenever the helper is usable.
  - The dumpsys sources stay as the fallback.
  - Calibration records name their field, so one recalibration switches the source.
  - `DisplayCalibrationRun`'s 150 ms polls become cheap.
- Live Debug shows the max reason (thermal capping) and the window override, as diagnostics only.
- This is the same fourth use in the same file, so the count stays at 4. The wording in
  AGENTS.md, `SHIZUKU_USAGE.md` and `privilege_tiers.md` changes, so the rule review runs.
- A DD row records the deviation: AAB's `_ReadBrightness` reads dumpsys (DD-078); this has the
  same meaning through a different mechanism.
- Tests: reader order and fallback with fakes, calibration on the new field, and
  `HighBrightnessUpdatesTest` unchanged. Glue review.
- Device:
  - handed off outdoors, Live Debug's value equals
    `dumpsys display | grep mCachedBrightnessInfo`;
  - recalibration passes;
  - the HBM flash still fires above 255 in sunlight.

**8. Compile against API 36.1.**
- `app/` and `platform/` use `compileSdk { version = release(36) { minorApiLevel = 1 } }`.
- Install SDK platform 36.1 wherever the build needs it (`scripts/setup-android-sdk.sh`, CI).
- Runtime gate: `SDK_INT >= 36 && SDK_INT_FULL >= VERSION_CODES_FULL.BAKLAVA_1`.
- All 36.1 calls sit behind seams, so Robolectric never touches them. targetSdk stays 36.
- Acceptance:
  - the ladder is green locally;
  - Build and all four F-Droid compatibility stages are green at PR time (CI runs only on PRs
    and main).
- If F-Droid can't provide 36.1, call the two public methods reflectively behind the same gate
  instead (Astra Q2).

**9. Event-driven readout (BASIC tier; deviates from AAB's 2 s tick, DD-078).**
- While handed off, `HighBrightnessUpdates` reads on `EVENT_TYPE_DISPLAY_BRIGHTNESS` (no
  permission). A slow safety poll (Astra Q3) and coalescing back it up, and the fixed 2 s loop
  goes.
- If the device step shows the event doesn't fire under OPlus's controller, trigger on the
  `SCREEN_BRIGHTNESS` observer instead, but only if F5 showed the setting tracks auto. Build
  only what fires on the phone. The setting holds at 4095 while the panel is above it (DE-007),
  so under that observer only the safety poll moves the readout above 4095.
- Tests, with a fake trigger and virtual time:
  - one read per event;
  - the safety poll;
  - nothing while not handed off;
  - cancellation on hand-back.
- Glue review. A DD row and a `parity_gaps.md` entry record the deviation.
- Device: in a sunlight hand-off, the readout follows within about 1 s, above 4095 too, the HBM
  flash still turns on and off, and reads per minute fall against Unit 4's baseline.

**10. Push-based foreground app (fifth runtime use).**
- The helper registers `ITaskStackListener` and pushes the front package through a oneway
  callback AIDL.
- `ForegroundAppMonitor` gets a push implementation that it uses while the helper is usable.
  - The 2.5 s UsageStats poll (`ContextEngine.kt:543`) stays as the fallback.
  - The gates are unchanged: app rules exist, the screen is on, plus D-142 and D-163.
- It's a new consumer file, so the constant goes from 4 to 5 and the three docs change. Rule
  review.
- Tests: `ContextEngineTest` with the push source, a fall back to polling when the helper dies,
  and stale completion (D-143). Glue review.
- Device:
  - an app rule switches within about 1 s of opening the app;
  - with Shizuku stopped, polling still works.

**11. "Disable HDR" applies live (reopens DB-044; sixth runtime use).**
- A helper operation calls `setUserDisabledHdrTypes` and `setAreUserDisabledHdrTypesAllowed`.
  Both are hidden (`@hide`; the first is also `@TestApi`) and need `WRITE_SECURE_SETTINGS`. They
  update the displays at once and write the settings themselves.
- `SecureDisplayController` routes through the helper when it's usable. Otherwise it keeps
  today's settings write, which may need a reboot.
- The UI text says which path applies (DB-044's naming rule).
- DB-045's representability rule and the panic reset are unchanged.
- The constant goes from 5 to 6 and the docs change. Rule review. A DD row reopens DB-044.
- Tests: `SecureDisplayControllerTest` and `DisplayTogglesCoordinatorTest` with a fake helper.
  Glue review.
- Device: when the profile toggle applies, an HDR video stops rendering in HDR immediately; when
  it's cleared, HDR returns, with no reboot.

**12. Finer brightness steps (optional; reopens DC-026).**
- This runs only if F8 is yes and the owner reopens DC-026. Otherwise it's dropped.
- First, measure on the phone how the percentage maps to the shell's `screen_brightness`. If the
  mapping isn't monotonic and precise, drop the unit.
  - A Pixel's mapping is already measured (DE-006): AOSP's HLG gamma curve up to the manual
    ceiling, with setting 2 already at 14 % of the slider. AdvancedAutoBrightness issue 26
    reports the same gap.
- Animation frames *between* Tasker-domain endpoints use the public
  `setBrightness(…, BRIGHTNESS_UNIT_PERCENTAGE)`, which BASIC can call. Endpoints keep today's
  exact domain write.
- The echo handling (DC-002…DC-008, DB-082) must treat sub-step echoes as self-writes.
- The range comes from the API and is never learned (a Decided non-item).
- Glue review, and a rule review if any AGENTS.md invariant is touched.
- Device: a night-time A/B of the same transition, and DEVICE_TEST_SCRIPT §2 step 10b re-run.

**13. Close-out.**
- The final changelog (≤ 500 characters).
- §19 and its `e2e` rows complete.
- `parity_gaps.md` deviations.
- `datastore_map.md` for the new verdict keys.
- `permission_audit.md`: confirm the manifest is unchanged.
- A STATE Owner-queue item: "run §19 on `<version>`-debug".
- Delete this file. Its durable content lives in DD rows and changelog lines.

## Review brief for Astra (Unit 1)

**Scope.**
- In scope:
  - this plan's design;
  - the order of its units, its gates and its risks;
  - its test and evidence strategy;
  - its fit with AGENTS.md and the RUNBOOK (playbooks 5, 6 and 9; glue and rule review);
  - the facts it cites, using the sources at the end.
- Out of scope: code (none is written), the excluded items (check only their reasons), the Tasker
  XML, the release and PR process, and targetSdk 37.

**Considerations.**
1. **Testability and device safety.**
   - There is one phone: a OnePlus 13 on ColorOS 17, with Shizuku at the shell UID and no root.
     The agent reaches it over wireless adb from the owner's container.
   - The agent may read freely. It may write settings only when each write is recorded and
     restored, and never adb, developer-option, lock-screen or network keys.
   - There's no emulator, and local checks are JVM/Robolectric only.
   - A unit that can't be observed on that phone must be dropped, not shipped.
2. **OPlus divergence.** Panel-unit floats (DD-083). The split app/shell scale (DC-025). OPlus's
   own auto controller. HBM running outside AOSP's HBM controller. All OxygenOS facts are priors
   until Unit 2.
3. **Tasker parity.** Every deviation gets a DD row and a `parity_gaps.md` entry: the 2 s tick
   becoming events, dumpsys becoming BrightnessInfo, polling becoming push, live HDR, and
   sub-steps beyond 0–255. Golden-tested domain math stays untouched.
4. **Reopened decisions.** DC-057, DD-077/DD-078, DB-044, DC-026 and `privilege_tiers.md`. Check
   that no Decided non-item comes back.
5. **Security.**
   - A resident shell-UID process.
   - The AIDL stays a fixed allowlist: package from the Context (DA-031), argv only, bounded
     output.
   - Callback binders.
   - Grants and the deviceidle exemption are explicit, reversible user actions.
6. **Lifecycle and concurrency (the glue-review classes).**
   - Shizuku restarts, `daemon(false)` and the idle unbind.
   - DC-058's main-looper callbacks and D-145's timeout unbind.
   - "Calls never overlap" (DD-077).
   - Stale async results (D-143), sibling gates (D-142) and cancel-before-compensate (D-139).
   - Process death (D-144).
7. **Hidden-API stability.** Reflection happens only in the helper. Verdicts are keyed by
   fingerprint, failures fall back, and unsupported values are never synthesised (DB-071).
8. **Build.**
   - compileSdk 36.1 against a reflective call.
   - The SDK platform in CI and in the F-Droid build, which is verified only at PR time.
   - Robolectric and 36.1.
   - Lint and NewApi with `SDK_INT_FULL`.
   - One bump per train, shared with the jitter fix.
9. **Evidence over theory (the owner's stance in DC-026).** Each efficiency claim is measured on
   the phone: Unit 4 against Unit 6, reads per minute in Unit 9, helper memory. A unit with no
   measurable win is dropped.
10. **Guard lockstep.** The Shizuku claim in three documents plus `doc-facts.sh` moves per unit,
    under rule review.
11. **UX, i18n and a11y.** Strings in resources, the Chinese backlog, the a11y tests, and "ADB
    always offered".
12. **Android 17 platform changes.** Per-app memory limits (the resident helper). Reflection
    limits apply only to apps targeting 37, which we don't. No new activity starts are planned
    (BAL hardening).
13. **Unit sizing.** About one focused hour each, with binary acceptance. Should anything be
    split, merged or reordered?

**Questions.**
- **Q1** The helper's idle window. Should rare operations (force dark) keep per-operation
  binding?
- **Q2** compileSdk 36.1, or a reflective call to the public 36.1 methods?
- **Q3** The safety-poll interval once readout is event-driven. Should calibration keep fast
  polling?
- **Q4** Should Unit 12 exist at all?
- **Q5** Are the one-tap grants correctly kept out of the runtime-use count?
- **Q6** TaskStackListener against UsageStats `ACTIVITY_RESUMED`: split screen, picture-in-
  picture, the lock screen.

**Deliverable.** A list of findings, each tagged blocker, should-fix or nit, naming its unit and
consideration. Answers to Q1–Q6. A list of units to drop and of anything missing.

## Excluded (not compatible or not testable on the phone)

- **Even Dimmer** (`even_dimmer_*` keys). It needs a Pixel-style display config. It could come
  back only if `dumpsys display` shows even-dimmer support.
- **`BrightnessConfiguration`** (handing Android the curve). OPlus's auto controller is in
  charge, and Tideo's smoothing and animation would be replaced. It could come back only if F4
  shows AOSP's controller driving auto mode.
- **`setTemporaryBrightness`.** OPlus's float units risk a flash to full brightness or black.
  Unit 12's public setter covers the same need.
- **`ShizukuBinderWrapper` or `HiddenApiBypass` in the app process.** Hidden calls stay in the
  helper, and callbacks are keyed per process.
- **Root launchers (libsu, Sui) and new `su` paths.** They can't be tested; the existing root
  fallbacks stay.
- **BrightnessTracker slider events and ambient-light stats.** It's unknown whether OPlus records
  them, and they would change the wizard's inputs. Revisit after Unit 2.
- **Others:**
  - `EVENT_TYPE_DISPLAY_STATE`: no Tideo problem it solves.
  - SSID through `IWifiManager`: Unit 6 already removes the per-call VM.
  - The public `getBrightness`: it returns the setting, not the actual level.
  - Android 17 targetSdk work: that's playbook 7.

## Verification

- **Every unit.**
  - `scripts/ladder.sh`; `--guards-only` for the docs-only units 0, 1 and 3, and for Unit 2
    unless it changes code.
  - Key suites: `:platform:testDebugUnitTest`, `:app:testDebugUnitTest` and `:domain:test`.
  - Glue and rule review verdicts in the commit body.
  - The commit body says what ran and what couldn't be checked on the device.
- **Device.**
  - Each unit's §19 steps run on the owner's phone on ColorOS 17.
  - The agent confirms them over adb: it reads freely, and writes settings only under the Phone
    rule.
  - The playbook-9 E2E suite runs after Units 6 and 9 and at close-out.
- **Evidence.** Compare Unit 4's baseline with Unit 6 (helper starts, latency, memory) and with
  Unit 9 (reads per minute). Any unit with no measurable win on the phone is reverted before the
  train closes.

**Sources.**
- AOSP `android17-release` and `android16-qpr2-release`:
  - `core/res/AndroidManifest.xml` and `packages/Shell/AndroidManifest.xml`;
  - `DisplayManagerService.java`, `DisplayManager.java`, `DisplayManagerGlobal.java` and
    `BrightnessInfo.java`;
  - `ActivityTaskManagerService.java`.
- The Android API diffs for 36, 36.1 and 37 (`developer.android.com/sdk/api_diff/…`).
- The Android 16 QPR2 SDK setup page and the Shizuku-API `Shizuku.java`.
