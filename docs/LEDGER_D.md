# DEVIATIONS & DISCOVERIES LEDGER D — permanent registry (DD-001…)

> **Append-only registry — NEVER archived, compressed, or truncated.** The continuation of
> `LEDGER_C.md`, which closed at its 1000-line cap (D-153 mechanism, DA-001 line-based
> cap). Code comments and docs cite entries as bare `DD-0NN` and must always resolve here, so no
> entry may ever be deleted or summarized away. **Append new maintenance deviations as DD-001,
> DD-002, … at the bottom** — one continuous sequence, never restart numbering. Code + golden
> vectors are ground truth; an entry that conflicts with current code is historical, and the code
> settles present behaviour. **Search before appending (DA-006):** grep the ledger files for the topic
> first — extend or cite an existing row rather than append a near-duplicate.
>
> **Rows are immutable (AMH 10.0.0).** The ` [cited]` marker is metadata and may be synchronized in
> place — the citation rung requires it to track the citation set in BOTH directions, so any
> append-only guard must permit adding AND dropping it (AMH 10.3.0). Otherwise correct a detail with
> a new row and append `Corrected by <ID>.` to the old row, or replace its whole conclusion with a
> new row and append `Superseded by <ID>.` The first pointer is final; which verb is honest is the
> reviewer's call, not a guard's.
>
> **Paths in rows (AMH 14.0.0).** A row's immutability covers its text, not the lifetime or location
> of a file it names. A new path reference must resolve in the tree where the row is authored; a
> committed row's target may later move or disappear, and that drift leaves the historical text
> alone. A new path that does not resolve, and any citation of a plan's path (RUNBOOK **Session
> discipline** 5), are both forbidden — and both are **prose-only here**: this repository ships no
> path-reference guard and nothing scans rows for plan paths, so the rule-review pass is the whole
> enforcement.
>
> **Boundaries determine when machinery intervenes, not how much content an author should produce
> (AMH 12.0.0/13.0.0).** `LEDGER_ROW_SENTENCE_CAP` and `LEDGER_ROW_CHAR_CAP` are rejection
> boundaries for new rows; `LEDGER_LINE_CAP` is a rollover boundary; this volume's byte size is
> measurement only, reported and never judged. Crossing a rejection boundary rejects the row;
> passing one proves no more than the absence of obvious oversizing, and is not a verdict on
> concision, scope or quality. Never merge sentences, repunctuate, or drop useful qualifiers solely
> to move a counter; nearing a boundary is a classification signal, so split the material or reduce
> it to its durable conclusion. The keys are
> named here and deliberately not restated as a number, because nothing checks this preamble
> against the config and a copied number goes stale the first time a cap moves. Read them from
> `amh.conf`; a green ladder deliberately does not print the limits. Bytes are counted with
> `LC_ALL=C` over the whole row, line breaks included, so ASCII is one
> byte per character and non-ASCII UTF-8 is charged by encoded bytes. Capture the durable lesson,
> not the whole debugging narrative — the narrative stays in the commit and its PR body (which
> survive the squash as the merged commit's message) and `docs/history/` is frozen (DB-010). But
> the SEQUENCE of work does not survive: intermediate states inside a train are destroyed, so
> anything a later session must be able to look up belongs in the row, not in the history around
> it. Rows already present at HEAD are historical and exempt.
>
> **File cap & rollover.** THIS FILE holds at most `LEDGER_LINE_CAP` lines from `amh.conf`,
> named rather than restated for the same reason as the row cap above; the ladder prints the
> live count against it on every run. The FINAL row may finish past the cap, but no row
> may ever START past it: when this file stands at more than that many lines, create
> **`LEDGER_E.md`** with this same header discipline and start numbering at **DE-001**.
> The suffix advances as an odometer over A–Z without limit (`_Z` → `_AA`, `_AZ` → `_BA`,
> `_ZZ` → `_AAA`). The volumes form a chain walked from `LEDGER.md`; a volume after a missing
> link is unreachable and is not a volume, however well its name is shaped. The ladder computes
> and prints the next reachable volume name when rollover is due.
> Existing rows are never moved, renumbered, or rewritten by a rollover.

- DD-001: **The light-stall train for Tideo #130 and #132 is closed (2026-09-25): it fixed the
  notification and the defects a test reproduced, and added the diagnostics #132 needs, but it is
  not shown to be the fix for #132's freeze.** Shipped: the notification no longer falls back to
  "Monitoring" on a start command (DC-065), Live Debug's Light Sensor card (DC-066), sensor
  registration after settings load (DC-067), live state cleared by service ownership (DC-068),
  Tasker's dead band restored (DC-063), the proximity damp confined to the reported α (DC-064), the
  pending-latest slot (DC-069, owner Q1 (b)) and the settling path (DC-070, owner Q2 with the
  endpoint revised to "inside the stored band"), each with the entry test that failed first.
  DC-069 and DC-070 are the rule-review rows for Q1 and Q2, and DC-071 is the Tasker check behind
  Q2. The low-accuracy re-admission policy was dropped rather than deferred (DD-004), and the
  dashboard STALE banner redesign waits until the Light Sensor card's signals have been seen in the
  field. Open: H1 (DD-003) and H2 (DD-002); the proximity parity change still owes its device run
  (STATE Owner queue).

- DD-002: **H2, open: the first light event after a wake is sometimes not delivered, and Tideo
  then writes nothing (2026-09-25, from the owner's OnePlus 13 observations of 2026-09-23).**
  Screen-off hibernate nulls smoothed lux, the last reading, the band and the cooldown; on wake
  `reinit()` loads settings before re-registering, and `setInitialBrightness` returns without a
  write when it has no lux, so if no first event arrives an on-change sensor in steady light sends
  nothing and brightness stays where the framework restored it. Tasker is not exposed: task618 on
  prof761's wake reads the sensor itself (act8) and retries up to seven times while accuracy is
  under 2 and trust is off (act9–12), then seeds smoothed lux and the last reading from it. The
  evidence is one intermittent wake that ended on "Monitoring" before DC-065, which a start command
  landing after the wake's cycle explains equally well, while the one instrumented dark wake
  (2026-09-24, screen off 3 min) was healthy: first event 0.0 lx at accuracy 3 within 2 s, then
  about 4 events a second. On a sensor that repeats like that H2 clears itself within seconds, so
  a separating reproduction needs the Light Sensor card's "First event after registration" read
  at once after a wake that stays wrong; no fix is planned until one exists.

- DD-003: **H1, open: the sensor path may go quiet while still enabled (2026-09-25, Tideo #132).**
  In both of #132's logs, which are attachments on the issue and not stored here, the ambient light
  sensor stays enabled at the HAL until the service toggle's `Enable = 0`, which is consistent with
  Tideo's registration surviving but does not name the client and says nothing about delivery to
  Tideo's callback. Neither log contains a stall's onset: their last samples fall 2–4 min before
  each log starts. The evidence that would separate H1 from a dropped reading is, from a build with
  the Light Sensor card and before toggling: a Live Debug screenshot, `adb shell dumpsys
  sensorservice`, and `adb logcat -d` with `adb logcat -G 16M` set beforehand; missing recent
  light events in `dumpsys` alone does not name a firmware fault. The card's callback counters are
  what separate the two, and the OnePlus 13 baseline for sensor-to-callback lag is 172–230 ms
  (daylight, 2026-09-24). Exposure probably depends on event cadence, a strictly on-change sensor
  such as the Pixel's being the likeliest, but no event rate has been measured on #132's device.

- DD-004: **Rejecting a low-accuracy light reading cannot happen on an AOSP framework, so the
  re-admission policy for it was dropped (owner, 2026-09-24).** The framework's JNI receiver copies
  the HAL status into `event.accuracy` only for the motion, magnetic and heart-rate types, and every
  other type, `TYPE_LIGHT` included, gets `SENSOR_STATUS_ACCURACY_HIGH` (3) from the `default:`
  arm in `android_hardware_SensorManager.cpp`, present at `android-12.0.0_r1` and
  `android-16.0.0_r1`; `SystemSensorManager.dispatchSensorEvent` calls `onAccuracyChanged` only
  while delivering an event whose accuracy changed. So "Trust low-accuracy sensor" cannot change
  which light readings pass on an AOSP-derived framework (crDroid included), and accuracy cannot
  recover without a new event; OxygenOS admitted 0 lx readings with the setting off. Withdrawn with
  it: that the OnePlus reports accuracy ≤ 1 at 0 lx, and that a wake which stays on "Monitoring"
  points to accuracy. Reopen only as a new analysis if the Light Sensor card, which records every
  callback's accuracy and the trust setting in effect, shows an OEM framework reporting light
  accuracy ≤ 1. Still unchecked: whether Tasker re-evaluates prof760 when `%AAB_TrustUnreliable`
  changes.

- DD-005: **Claims the light-stall analysis withdrew, so they are not reintroduced (2026-09-25).**
  A stale "Last sample" does not show that nothing reached the app, since one rejected final
  reading goes stale in step with the last completed cycle and both screens truncate ages to whole
  minutes. Live Debug's raw lux is the last processed reading, not the latest received one, and
  acknowledged writes with normal cycle times describe completed work only, not the absence of a
  block. Re-feeding the last reading cannot finish a transition, because α reaches 0 above a
  drop's band (DC-070), and "α ≥ 0 on every smoothed row" is not a parity test, because task535
  subtracts the previous cycle's stored threshold. "No more cycles than before plus one" is not a
  no-ghosts test either, since a correct pending slot can exceed it over a long burst (DC-069).

- DD-006: **The settling path passed its device check (owner's OnePlus 13, debug build of
  `4cace33`, dark room, 2026-09-25; DC-070).** A flashlight flickered at the sensor for about
  10 s and ended dark; brightness, sampled every ~250 ms, then kept falling for about 5 s
  (980 → 691 → 434 → 273 → 177 → 161 on the device's raw scale) and held 161, the same value as
  the dark baseline before the run, for the remaining 24 s, where R6's run on `938058a` had held
  42 lx's brightness. The Light Sensor card read a last cycle of `SETTLED`, with later 0 lx
  readings refused as `DEAD_BAND`. The silent-sensor continuation was not exercised: this sensor
  keeps reporting about 4 times a second, so real readings finished the settling and the
  "settling" count did not move. Proximity's device check (DC-064) was offered in the same session
  and skipped by the owner, so it stays open.


- DD-007: **The OnePlus 13's light sensor keeps reporting in a dark room, although it declares
  itself on-change (2026-09-25, owner's phone).** `dumpsys sensorservice` lists "OPLUS Fusion
  Light Sensor Next Gen", Tideo's light sensor, with `flags: 0x00000002` (on-change), yet its last
  50 events were all 0.00 lx over 13 s, about 3.8 a second, and Tideo's Light Sensor card counted
  151 callbacks in 41 s at 0 lx. Each event carries further fields that keep changing, one of them
  counting up, which probably explains the steady stream, but that is inferred. On this phone a
  missed or dropped reading is therefore replaced within about 250 ms, which hides the dropped
  reading, the unfinished transition and H2 alike; a sensor that falls silent in steady light, as
  #132's Pixel may, would expose them (DD-003).

- DD-008: **Backup saved nothing until `fullBackupOnly` (2026-09-27, OnePlus 13).** Without it a
  `backupAgent` app is key/value-only, and `SettingsBackupAgent` has no helpers, so no build since
  1.8.2 backed up settings or profiles (DC-052). With it, `backupnow` streamed exactly the two
  allowlisted files, and a Tideo-only restore brought them back with the sanitizer's fields
  written, closing DB-013's residual.

- DD-009: **A failed package-scoped restore still wipes that app's data and permissions
  (2026-09-27).** A backup signed with another debug key was rejected after Android had cleared
  Tideo; a successful restore did not wipe. Snapshot with `run-as` before any restore (DB-013).

- DD-010: **`bmgr` preconditions (2026-09-27).** A freshly installed app is `stopped=true` and
  refuses full backup until launched once; the Google transport restores a debug build signed
  with the same key, so no `bmgr transport` switch is needed.

- DD-011 [cited]: **An override pause can be discarded from its notification (#134, 2026-09-28).**
  Discard forgets the point that pause recorded, then resumes, unlike AAB's `_DiscardLastOverride`,
  which pops the newest; with none remembered it only resumes. It matches by value, so if that point
  was already tapped off the graph an identical older one goes instead, removing only a duplicate;
  per-point ids were rejected as unjustified (owner). Android shows three actions at most,
  so Discard · Resume · Disable replace Reset while a point is discardable.

- DD-012 [cited]: **Curve inputs persist unclamped where Apply accepts them (#133, 2026-09-28).**
  The rebuild-invented ranges (Form1A 1..20, Form2B 0.1..30, Form2C 1..50, zone ends ≤20k/100k)
  cut valid wizard and hand-made curves on the next cold read or profile load; Tasker bounds none.
  `validate()` now repairs only non-finite values, Form1A<0, Form2C>Zone1End, Zone1End<1 and
  Zone2End<Zone1End; Form3A≥0 stays Apply's job (D-169). The engine clamps before narrowing to
  Int, and the graph's fixed 100k-lux axis stays, as in Tasker (owner).

- DD-013: **PWM mode without WRITE_SECURE_SETTINGS pins the hardware at the threshold, uncompensated
  (2026-09-28).** A fresh debug install restored a PWM-on profile (threshold 150) but no adb grant, so
  the screen sat at 2409/4095 while the notification showed the perceived 28: the D-050 floor ignores
  the tier, and the unprivileged overlay that compensates in Tasker is deferred (D-040). The restore
  worked as intended (DD-008); owner: leave as is, grant the permission after a restore.

- DD-014 [cited]: **The PWM software-exponent help is corrected away from task702's flash (2026-10-01,
  owner; the owner ports the new text back to AAB, as in D-168).** Tasker's text said higher values
  keep the screen brighter for longer and change only the transition, not how much it dims.
  `finalDimLevel` does the opposite: its level is 99·(1 − r^exp) with r < 1, so a higher exponent
  dims more at every target below the threshold, and because the bias is floored at 10 the darkest
  level depends on it too (threshold 15: about 51 % at 0.8, 93 % at 3.0). `help_pwm_exponent` now
  says so, deliberately without the ~94 % cap (owner); no maths changed.

- DD-015: **The real-device E2E suite drives the phone with uiautomator2 + adbutils directly, behind
  a deny-by-default command and UI boundary and a conflict-aware recovery journal (2026-10-01,
  owner-approved 2026-09-13).** Artemis was rejected because it rewrites
  `enabled_accessibility_services`, which holds Tideo's own service; mobile-use stays only as an
  offline triage tool over sanitised evidence, since live it uninstalls packages and swaps IMEs.
  `e2e/scenarios.toml` classifies every `DEVICE_TEST_SCRIPT.md` step and must change with it;
  profile-changing steps stay manual, because the settings, baseline and profile-name tuple cannot
  be restored.
  Corrected by DD-043.

- DD-016: **The E2E boundary gates adb where adbutils opens services, not at the harness's own
  calls (2026-10-01).** That is the one chokepoint library internals share; it also shells out to
  an `adb` binary (`adb_output`, server auto-start), now refused, and a device is bound by serial
  because a transport id cannot be tied to one. It confines libraries and honest harness code, not
  code written to evade it: a source tripwire covers that, and occlusion is left to S6's smoke run.

- DD-017: **E2E recovery attributes values, not keys: it restores a key only from a value the run
  caused, and keeps the service off while any conflict stands (2026-10-01).** A running service
  would write over a conflicted key. Android has no conditional write, so the residual windows
  (read→restore, action→observe, the stop's own teardown) are named in `recovery.py`. The exception
  is brightness and the Extra Dim level under a run that touched the service: the pipeline moves
  them faster than any observation, so any value they hold counts as the run's.

- DD-018 [cited]: **The app root exports Compose test tags as uiautomator resource-ids
  (2026-10-02), the E2E suite's one production hook.** `TideoRootSurface` sets
  `testTagsAsResourceId` once for the whole nav graph; a Robolectric test pins it. A `Dialog` is
  its own window and inherits nothing, so one gets the flag only when a scenario needs a tag inside
  it.

- DD-019: **The E2E recovery port reads paused as the Resume action on Tideo's ongoing
  notification, and taps `service_switch` only once it shows the stored `serviceEnabled`
  (2026-10-02).** PAUSE never posts on `manual_override`, and the Dashboard renders the switch
  off until settings load, so either shortcut flips or misreads the service. Identity is checked
  over a read-only session before uiautomator2 connects; only Android user 0 is bound.

- DD-020: **E2E scenarios act only through a `Run` that refuses an undeclared effect, and every
  Privileged Display preference is journaled before the screen is touched (2026-10-02).** Apply
  stores the whole draft, including fields read back from the device, so recovery restores prefs
  after settings and the screen must open clean. An injected override's curve point is removed
  with the notification's Discard (owner, 2026-10-02); the install guard streams the bytes it
  inspected, after the owner's typed approval.

- DD-021 [cited]: **Sol's blocking review of E2E S2–S4 found six BLOCKERs and six HIGHs, all
  fixed before any device contact (2026-10-02).** Under a service that was running, recovery
  leaves the brightness mode to Tideo's restart and verifies the owner's saved mode, since writing
  manual back first made Tideo save manual as theirs; curve points are journaled, and an
  attributable value is only the last observed or the one about to be written. A Draft Apply
  before the DataStore seed committed defaults over the profile, for anyone tapping fast, so the
  app now refuses it. Accepted: a window appearing between the hierarchy dump and the tap, and
  `bmgr` denied outright although the owner allowed one form.

- DD-022: **While near, the proximity ×0.1 now also sizes the brightness animation,
  because the owner changed Tasker's task544 to do so (2026-10-03).** The owner's V2 sets
  `%lux_results2 = round3(%lux_results2 × 0.1)` while `%AAB_Proximity ~ near` (pasted A30), copies
  it into `%LuxAlpha` and passes it as A35's par2, so task661 sizes the animation from it; A26–A28
  have already stored `%SmoothedLux` from the undamped α, so smoothing, the band and the target are
  unchanged. `evaluate` now feeds the damped, 3-dp-rounded α to `calculateAnimation` on a smoothed
  or settled cycle, and the oracle and `LightCycleParityTest` model the new A30. task543 gives a
  smaller α fewer steps and the throttle is the animation's length, so at the defaults α 0.3 goes
  from 7 × 53 ms (throttle 381 ms) to 2 × 64 ms (138 ms): covered, the same target arrives in a
  coarser jump and the next cycle may run sooner — no slowing, and not D-087's in-EMA damp, which
  stays withdrawn by DC-064. On-device behaviour is unverified.
  Superseded by DD-024.

- DD-023: **Translations may be AI-assisted when a fluent speaker reviews every string; unreviewed
  machine translation stays out (owner, #140, 2026-10-04).** D-131's human-only rule turned away a
  translator who drafts with an AI model, then checks every string against the English original and
  in the running app. `CONTRIBUTING.md` and the README now name three kinds: human and AI-assisted
  translations are accepted, the PR saying which and naming the drafting tool, while tool output
  submitted without that review is not. The in-app `misc_language_note` still says only that human
  translations are welcome, which stays true, and is left alone because #141 rewrites that line.

- DD-024 [cited]: **While near, the proximity ×0.1 damps smoothing itself, and uncovering re-runs
  Evaluate, because the owner moved the damp into task535 and extended task545 (2026-10-04).**
  Answering DD-022's open question with its option (b), the owner's task535 adds A3b,
  `lux_alpha = round3(lux_alpha × 0.1)` while `%AAB_Proximity` equals `near`, before A4's blend,
  and task544's near branch is gone, so smoothed lux, the band, the target, the animation and the
  readout all follow the damped α (D-087's in-EMA damp, withdrawn by DC-064, now as Tasker's);
  `smoothLux` and the oracle damp there, and `evaluate` no longer damps afterwards. task545's exit
  branch now performs task544 on `%AAB_LastRawLux`, which Tideo queues on a near→far change as a
  recheck in DC-069's pending slot: it skips prof760's band and accuracy gate as Tasker's direct
  call does, keeps the service, pause, screen and task544 dead-band gates, is deferred by the
  cooldown where A6 would drop it, yields to any real reading, and counts as settling in Live
  Debug. One departure remains: DC-070's settling still runs while near, in damped steps, landing
  smoothed lux on the band edge by its twentieth step, where Tasker would leave it lagging until a
  reading leaves the band or the sensor is uncovered. On-device behaviour is unverified.
- DD-025 [cited]: **Context rules read "plugged in" from `EXTRA_PLUGGED`, not the charge status,
  ported from the owner's AAB task43 (#139); and the panic "Only when plugged in" toggle is now
  global everywhere, as DB-009 ruled (2026-10-04).** At a charge limit Android reports
  `NOT_CHARGING` (sometimes `DISCHARGING`) while the charger stays connected, so task43 A12's
  `isPlugged` (`EXTRA_STATUS` ∈ {`CHARGING`, `FULL`}), which Tideo ported faithfully, flapped
  "Only while charging" rules at the limit, matched on-battery rules wrongly, and D-132's cooldown
  bypass re-evaluated on every flip. The owner's AAB V3.4 task43 (AdvancedAutoBrightness#21) now
  reads `getIntExtra(EXTRA_PLUGGED, 0) > 0` and renames the editor label "Only While Plugged In";
  `AndroidBatteryStateReader` ports it as `BatteryState.isPlugged` (a missing extra is unplugged;
  AC, USB, wireless and dock count, as in the panic source), and the label and rule summary follow,
  so this is a port, not a deviation. Separately, six sites that keep `panicSensitivity` across a
  settings swap omitted `panicRequiresPlugged` — `mergeProfile` (rule load and revert),
  `ProfileApplier.applyProfile`, `SettingsViewModel.resetDefaults` and `replaceAll`, and
  `DraftSettingsViewModel`'s store refresh and Apply — so a rule loading a profile saved with the
  toggle on disabled the gesture on battery, the case DB-009 ruled out, and a Live Debug change
  under an active rule was lost on revert; all six now take the same side as `panicSensitivity`.
  The unit opened 1.13.0 / vc27, shared with the #136 and solar-offset plans; on-device behaviour
  is unverified (`DEVICE_TEST_SCRIPT.md` steps 15a and 23), and `AndroidPowerMeter.isCharging`
  (task524's calibration abort) still reads the charge status, left for its own decision.

- DD-026 [cited]: **An untranslated string falls back to English instead of blocking the build
  (owner, 2026-10-04).** With `values-b+zh+Hans/` present, lint's `MissingTranslation` error
  meant every new or rewritten English string waited on a fluent translator, which the policy of
  DD-023 cannot hurry, so `app/lint.xml` lowers it to a warning and Android shows the English
  default. A string whose English meaning changes loses its translations in the same change,
  because a stale one is worse than English. `help_pwm_exponent` (DD-014) and
  `contexts_only_plugged_in` (DD-025) were the first, and show in English in the Chinese UI until
  retranslated.

- DD-027: **The README shows a coverage badge per translated language, checked by a unit
  test rather than published by CI (owner request, 2026-10-04).** `TranslationCoverageBadgeTest`
  counts each `values-*/strings.xml` key that a translatable default string also has, rounds the
  share down to a whole percent, and fails unless the README's shields.io badge for that language
  tag shows that percent and its colour, printing the line to paste. Publishing the numbers from
  CI would need a write-permission workflow and a badge branch, so the cost accepted instead is a
  one-number README edit whenever coverage crosses a whole percent. Simplified Chinese started at
  99% (662 of 664), the two strings DD-026 removed being the gap.
  Superseded by DD-028.

- DD-028 [cited]: **Translation coverage badges are published by CI, replacing DD-027's unit test
  (owner, 2026-10-04).** The test failed a change whenever coverage crossed a whole percent, which
  contradicts DD-026's rule that a missing translation must not block anyone. The `Translation
  badges` workflow runs `.github/scripts/translation_coverage.py` on pushes to `main` that touch a
  `strings.xml`, writes one shields.io endpoint JSON per language tag to the orphan `badges`
  branch with a job-scoped write token (the `clean-dist.yml` pattern), and lists missing keys in
  the job summary. A broken run shows as a stale or failing badge rather than a red PR, and the
  workflow's first run on `main` is its only real test.
- DD-029: **E2E S5: `run.sh --preflight` is the suite's first device contact, and it is read-only
  (2026-10-04).** On the owner's phone it ran 126 adb requests, all READ templates. It refuses
  unless Tideo's language list is certainly English: the app's own locales walk into the system's,
  and a Chinese entry anywhere after a non-English first one is refused (Sol). The install guard
  gained a first install, reached only on `dumpsys package`'s own "Unable to find package" line.
  The owner approved it, and its signer now binds every later install.
- DD-030 [cited]: **First-launch animation defaults are 50 steps, 5–30 ms waits and a 1510 ms
  throttle, and the built-ins' midpoint is 4.0 (Outdoors 4.255), as the owner's adapted task570
  and task592 set them (2026-10-05).** The owner's task570 sets `%AAB_AnimSteps` 50,
  `%AAB_MinWait` 5 and `%AAB_MaxWait` 30, where the extracted XML has 20/25/65, and still derives
  `%AAB_Throttle` and `%AAB_ThreshMidpoint = log10(%AAB_Zone2End)`, so `AabSettings()` now equals
  `DefaultProfiles.Default`; the owner's task592 moves `getBaseProfile()` from 3.0 to 4.0 and gives
  Outdoors log10(18000) = 4.255, and Tideo keeps storing both derived values rather than computing
  them (owner). The settings, context-baseline and export writers used to omit every key equal to
  its default, so each now encodes every key and its reader pins an object's absent animation keys
  to 20/25/65/1310 unless all four are absent, when it takes the new values (owner: a user who
  changed any of them keeps exactly what they had, and the group never mixes old and new). The
  schema stays v3 on purpose, key presence being the marker, because a bump made an older build
  reject the file and reset every setting on a downgrade or an equal-versionCode reinstall (review
  finding). Saved built-ins are stored whole, so `factoryRevision` 1 gives a built-in still holding
  its revision-0 values the current ones, once, and keeps any the user edited. Accepted: a v3 user
  who had set all four back to exactly 20/25/65/1310 cannot be told from one who never touched them
  and moves too; on-device behaviour is unverified.
- DD-031 [cited]: **Loading an untouched built-in profile keeps the user's "Trust unreliable
  sensor" and Quick Settings choices, closing a parity gap open since the port (2026-10-05).**
  task592's `getBaseProfile()` never wrote `reactivity.detect_overrides`,
  `reactivity.trust_unreliable` or `circadian.qs_use` ("Booleans removed to respect user choices"
  is in the extracted source too), and task637's `performLoad` writes only the keys a file holds, so
  a Tasker load of a built-in keeps those globals; D-073(2) and `features_spec.md`'s schema list
  read performSave's full key set as task592's, and only `detectOverrides` was kept on every load.
  `DefaultProfiles.keepUserChoices` treats a profile as task592's when its name is a built-in's and
  its values equal that built-in's (schema version aside), and the manual load, the context
  engine's rule load, a Tasker configs-folder load and the load preview all route through it.
  A built-in the user edited and saved, or a Tasker file whose other values differ, carries its own
  values, as `performSave` writes every key. On-device behaviour is unverified.
- DD-032: **E2E S6: the smoke set passes on the owner's phone (OxygenOS, Android 16) on
  1.14.0-debug vc28, after two harness parser fixes (2026-10-05).** OxygenOS appends
  ` c:<package>` inside a ServiceRecord's braces, so preflight read the running service as
  stopped; `fgs_in_dump` now admits exactly that suffix and still reads anything else as not
  running. A tagged AssistChip dumps empty with its label in a child, so a read falls back to the
  node's one text-bearing descendant and refuses two. The owner installed the build out of band,
  same signer. s01_4 failed once in 8 runs with its message not kept; S7 classifies it if it
  recurs.

- DD-033: **E2E S7 on OxygenOS / Android 16: what the phone told the harness (2026-10-05).**
  Focus is `dumpsys window displays`' `mCurrentFocus` (absent under `windows`); adbutils'
  `app_current()` read an open shade as the app under it, so every action now needs its own
  package, or the `NotificationShade` window, to hold focus, and the shade closes by `cmd
  statusbar collapse`. A collapsed notification row has no app name and no actions: it is
  expanded once per opening, only when `--noredact` shows the debug package alone posted its
  title (Sol, Astra). UiScrollable gave up after one Compose swipe, the draft Apply bar is always
  shown (Apply is enabled when dirty and free of errors), `pm revoke` does not kill the app, and
  an unrecognised stay-awake mask keeps the profile's value (owner, over the script's "reads
  ON"). s01_4 did not recur.

- DD-034 [cited]: **Two Tideo defects the S7 device run found, fixed (2026-10-05).** A
  NEW_TASK|CLEAR_TASK relaunch disposed the old `AabFlashHost` after the new one registered, and
  its `registerForeground(null)` sent every later flash to a plain Toast; a host now clears only
  itself. D-155's "a same-process restart re-asserts the baseline" never held, as
  `createRuntime` builds a new `DisplayTogglesCoordinator` per service start: a process-level
  mark, set after panic's last write and consumed by the next start, seeds from the defaults.
  Both await a device rerun on a build carrying them (s06_19a, s11_39).

- DD-035 [cited]: **AAB's task43 revision of 2026-10-01 — solar offsets, an inclusive end minute
  and an exit wake at end + 1 min — ported (2026-10-06).** The owner pasted the revised
  `_EvaluateContexts` act12, whose changed regions are transcribed (the only copy) in
  `_source/java/task43_1_evaluatecontexts-v2.rev-2026-10-01.hunks.txt`. A `time_range` endpoint
  starting with `SUNRISE` or `SUNSET` takes an offset in minutes after the first `+`, else the first
  `-` (a parse failure is 0), wrapped into the day and floored to the minute, plain tokens included;
  `SolarTimeTokens` is the grammar's single home, for evaluator and editor, and before it Tideo threw
  on an imported `SUNRISE+30`. The window is tested on the current minute, so a rule holds for all
  of its end minute, and the wake list holds `start` and `end + 60 s`, where the old wake at `end`
  matched again and left the rule on until a later evaluation. Deviations kept: a solar token can be picked with no
  location fix (no preview; the engine uses 06:00/18:00 as task43 does), where the AAB editor
  refuses; an invalid offset is an inline error, not a modal; re-tapping the selected token does
  not return to clock time ("Clear time" or a picked time does); an offset overflowing a Long is
  rejected by the editor where AAB saves it and evaluates 0; `HH:MM` with inner whitespace is still
  accepted (each part trimmed) where Tasker aborts. On-device behaviour is unverified.

- DD-036: **The owner's device round on 1.14.0-debug vc28 (built from `55262a5`) passed (2026-10-06).**
  All nine branch checks passed on the phone: DD-035's exit (a rule ending 07:03 stayed on through
  07:03:59 and switched off at the 07:04 boundary, screen on and untouched) and the rule editor's
  offsets, including the no-location and Simplified Chinese options but not the AAB import; #139's
  charge-limit status 4 and the global panic toggle (DD-025); the first-launch defaults, read as
  50 / 5 / 30, the 4.0 and 4.255 midpoints, and trust-unreliable kept on a built-in load (DD-030,
  DD-031); the proximity damp (DD-022, DD-024); both DD-034 fixes, by a
  method the report does not name; and the PWM help text (DD-014). The owner could not read the
  Chinese screen, but `values-b+zh+Hans` holds neither `help_pwm_exponent` nor
  `contexts_only_plugged_in`, so both fall back to English as DD-026 intends. The solar-offsets plan
  closed, and its webview paste moved verbatim to
  `_source/java/task637_profilemanager-webview.rev-2026-10-01.hunks.txt`.

- DD-037: **Decided non-items moved verbatim out of `docs/STATE.md` to
  `docs/rebuild/DECIDED_NON_ITEMS.md` (owner's grant, 2026-10-06).** STATE was over its compression
  trigger at the previous commit, so the next commit had to land under the post-action ceilings.
  Even with every completed stage folded, it could not get there while keeping the live E2E
  narrative verbatim, which the owner asked for. STATE keeps the required `## Decided non-items`
  header with a pointer to the file. The declines stay legislation under the rule-review protocol,
  and RUNBOOK's two references now name the file.

- DD-038 [cited]: **SUNRISE/SUNSET rule tokens resolve from the Circadian screen's pinned location
  (owner report, 2026-10-06).** The owner's `SUNSET-30`–`SUNRISE+30` rule switched on at 17:30 local
  while the pinned Circadian graph showed sunset at ~19:06, because the engine read only the live fix
  or Android's last-known location, found neither, and used task43's 18:00 placeholder; the rule
  editor's labels held a second copy of that math. Both now call `ContextSolarTimes` with the
  owner's order: pinned → live fix → Android last-known → geo-IP only while that fallback is enabled
  → 06:00/18:00, matching task43, which reads the circadian task's `%AAB_Sunrise/Sunset`. Geo-IP is
  the circadian's once-a-day cached lookup (D-103), never fetched during evaluation, and since that
  cache does not record its source an older cached Android fix is also skipped while geo-IP is off
  (owner accepted). A pinned date does not move rule times, which use today. JVM-tested
  (`ContextSolarTimesTest`); on-device behaviour is unverified.

- DD-039: **E2E S7 reruns on a build carrying DD-034 (2026-10-06).** s06_19a passes. s02_10b was
  a harness defect: a drift dismissal writes nothing, so re-settling before its second step
  waited for a pipeline write steady light never makes; it now settles once and passed twice.
  s11_39 still fails, but its restart loaded the owner's sunset-to-sunrise context rule's
  profile (both toggles off), so it says nothing of DD-034 until rerun outside that window.

- DD-040: **Owner answers on two E2E scenarios (2026-10-06).** An open Dashboard need not notice
  a grant, so s06_19b is manual: the badge on reopening is s06_16, and what DEVICE_TEST_SCRIPT 19b
  still adds, super dimming starting without a restart, needs a dark room. The owner keeps
  fingerprint unlock, so a `screen_wake` scenario SKIPs unless `unlock` is confirmed, and each
  wake waits 60 s for the keyguard (`isKeyguardShowing`) to clear.

- DD-041: **E2E S7's last three rows pass on the phone (2026-10-06).** With the owner's
  `SUNSET-30` rule deleted s11_39 passes, and s02_10e passes with `unlock`. s02_10a's control
  wrote after a locked phone dozes again (~8-10 s after wake, so Tideo hibernated); the owner now
  unlocks first, and since the quiet half's write stands until the light moves, Tideo's last
  applied value is put back before the control. Passed twice.

- DD-042 [cited]: **TIME rules wake only at `%AAB_NextContextTime`, as prof764 does; the evaluation
  on every pipeline update is gone (owner, 2026-10-06).** That tick, D-042(e)'s stand-in kept after
  D-093 built the scheduler, assembled signals about once a second, reading Android's last-known
  location each time without a pinned or live fix. It also re-ran refused evaluations, so a PASS-1
  refusal (shared cooldown) of any caller but BATTERY is now retried as that caller once the
  cooldown has passed, a departure from task43, which drops it (Astra's choice over strict parity).
  `ContextSolarTimes` reuses the last-known answer for 10 minutes, as the app poll still assembles
  every 2.5 s. As in Tasker, a day-only rule waits for the next evaluation after midnight. JVM-tested;
  on-device unverified.

- DD-043: **mobile-use is dropped from the E2E suite; there is no triage extra (owner, 2026-10-07).**
  At both the pinned `12a1dbd` and `62913c9` its agent raises `DeviceNotFoundError` without a local
  or cloud device, so an offline triage would use only its LangChain wrappers. Its
  `adbutils==2.9.3` pin also makes a `triage` extra unsatisfiable beside the suite's 2.12.0
  (`uv lock`).

- DD-044: **The E2E plan is closed (S9, 2026-10-07): on 1.14.0-debug vc28, installed before DD-042,
  17 of 20 auto rows pass and 3 skip on their own preconditions; the journal ends empty and
  Tideo's private stores are byte-identical before and after.** The skips: grant already held
  (s06_16), the owner's brightness mode manual (s02_10c), daltonizer at AOSP 0 (s11_32c). From
  Sol's final review, a held value the restore template refuses now SKIPs like an absent row; the
  rest is `e2e/README.md` "Known limits", and comparing private state stays a manual step
  (RUNBOOK playbook 9).
  Commit `e9f1b22` cites this ID for the N2 Kelvin-range row, renumbered DD-045 (owner, 2026-10-07).

- DD-045 [cited]: **The Night Light slider, ramp day endpoint and unset-key anchor follow the
  device's `config_nightDisplayColorTemperature{Min,Max,Default}`, not AOSP's 2596/4082/2850
  (N2, #142).** Each absent value falls back to AOSP's; both bounds stay inside the 1000–10000
  write rails until N2b moves them, so a 686 floor shows as 1000, and a min ≥ max pair keeps
  AOSP's range. The hint loses its Chinese (DD-026). JVM-tested; on-device unverified.
  Renumbered from DD-044, which the parallel S9 branch had taken first (owner, 2026-10-07).
  Corrected by DD-046.

- DD-046 [cited]: **Every stored Night Light setpoint and every Kelvin write is railed to 686–7308,
  AAB's extended range, not 1000–10000 (N2b step 1, #142).** One band is shared by
  `SecureDisplayController`, `AabSettingsMapper` and `SettingsValidator`; 686 is where the
  #142 matrix fit's blue multiplier reaches zero, and 7308 the highest Kelvin at which every
  multiplier stays within 0–1. A device floor of 686 now shows as 686, but a device config above
  7308 is cut to 7308 (slider top and circadian day endpoint), and a config wholly above it falls
  back to AOSP's range; a stored or read-back value of 7309–10000 (import, a high-max slider, or a
  device key set elsewhere) clamps to 7308 on save, and the e2e restore floor drops to 686.
  JVM-tested; on-device unverified.
- DD-047: **Night Light's extended range is a per-profile field, `extendedNightLightEnabled`,
  default off (N2b step 2, #142); nothing reads it yet.** It persists and exports with the profile,
  imports from key=value as `%AAB_ExtendedNightLight`, context-merges from the loaded profile, is
  kept at its current value by a legacy import like the other display fields, is never taken from a
  device read-back, and a draft edit of it blocks a re-merge. **Decided under the owner's N2b grant,
  departing from AAB:** a saved profile or export written before the field reads it as off, where
  AAB's loader keeps the current mode when the key is absent. Tideo profiles are complete
  snapshots — every display field added later took its default the same way — and a tri-state
  "keep current" would reach every consumer of the flag, for profiles that predate a feature nobody
  could have switched on. The zh-Hans label is owed with the Owner queue's translation item.
  JVM-tested; on-device unverified.
- DD-048 [cited]: **Night Light Kelvin is clamped to the active range at write time: 686–7308 through
  DC-057's display-service bridge while a profile's extended flag is on, else the device's own range
  through the key (N2b step 3, #142).** `NightLightTemperatureRoute.writeClamped` is the one entry:
  extended tries the bridge first and only then mirrors the key, skipping the honour probe so a
  clamping service getter never latches NOT_HONOURED, and with no route (Shizuku stopped, no root)
  only the device clamp reaches the key, skipped when the device already holds it; because AOSP's
  service keeps a binder Kelvin raw and compares clamped values, a key write clamping to the same
  edge would be a no-op, so after an out-of-range binder write the next non-extended write also goes
  through the bridge, unprobed (a process-wide flag). The coordinator's seed, below-ELEVATED
  tracking, static, swap, tick and release writes all clamp alike and `deviceTempK` tracks the
  Kelvin that landed; the screen's direct Apply uses the same entry, always writing while extended,
  and the ramp's day endpoint is 7308 while the flag is on. The forks and known limits are DD-049;
  the AOSP behaviour above is recalled, neither re-read nor measured — JVM-tested, on-device
  unverified.
- DD-049: **DD-048's forks, decided under the owner's N2b grant, and its accepted limits.** (a) Only
  writes are clamped, as AAB's `_NightLightAPI` A3/A4 do; the ramp's night endpoint and DC-056's anchor stay
  raw, so the ramp shape is unchanged; (b) restoring a displaced anchor for a null setpoint is railed
  to 686–7308, never device-clamped, because it puts back the device's own value rather than
  applying a setpoint; (c) the route is not cached: every extended write tries the bridge, so the
  ticker recovers a returning Shizuku within a minute, while a static setpoint written with no route
  waits for the next profile change or Apply, as AAB's apply-time write does; (d) a read-back
  equal to the setpoint's device clamp keeps the stored setpoint (the snapshot carries the device
  range), done here because this step introduced the clamp the read-back would otherwise copy
  into the draft. Known and accepted: `stop()`'s main-thread quick write skips Shizuku (DC-057's
  design), so an extended resting profile lands device-clamped there, and a reboot reloads the
  clamped key, so a static extended setpoint waits for its next apply while a ramp recovers on its
  next tick.
  Corrected by DD-059.
- DD-050 [cited]: **The Privileged Display screen's Night Light card has a "Go beyond temperature
  limits" row under "Follow circadian scaling", shown while Shizuku is usable by Tideo (running and
  permitted, as the bridge needs) or root answers, and kept visible while a draft has it on (N2b
  step 4, #142).** The slider's range, its hint and the circadian help's day endpoint follow the
  draft flag at once (686–7308, device default kept); a stored setpoint the slider cannot show keeps
  its number in the label, reaching the draft only when the thumb moves; and with the flag on and no
  route, DC-057's "needs Shizuku" note shows, as the owner decided. **Fork decided under the owner's
  N2b grant:** root is probed with `su -c id` once per screen open, only at ELEVATED and only when
  Shizuku is not usable, so a Shizuku user never sees a root-manager toast for it, and a successful
  in-app root grant counts as root. JVM-tested (Compose under Robolectric); on-device unverified, and
  the zh-Hans strings are owed with the Owner queue's translation item.
- DD-051: **DC-057's "only on a build observed to ignore the key" no longer holds: the Night Light
  Kelvin also goes through the display-service binder while a profile's extended range is on and
  after an out-of-range write until one lands through it (DD-048), so the constitution's third Shizuku place
  is reworded under the rule-review protocol and still counts as one (N2b step 5, #142).** The
  owner's direction kept that place a single file (`NightDisplayServiceBridge`), so `doc-facts.sh`
  stays at three; README's paragraph became one sentence pointing at `SHIZUKU_USAGE.md`, which now
  holds the paragraph updated for extended mode, and the guard's failure message names that file
  and `privilege_tiers.md` as the restatements. Nothing checks that README's pointer resolves or
  that `SHIZUKU_USAGE.md` agrees with the constitution: like every doc-facts claim, the guard
  counts code and never parses prose, so both are reviewer-held.
- DD-052 [cited]: **Debug builds are dark blue, not AAB teal (owner, 2026-10-08), so a debug install
  is told from the release at a glance.** `Color.kt`'s three teal constants switch on
  `BuildConfig.DEBUG` (#1565C0, #42A5F5, #64B5F6), the hard-coded teal sites read them, and the
  debug source set overrides the launcher background and widget drawables; release keeps every
  teal value. Unit tests run on the debug variant, so they check palette wiring, not the release
  literals; the User Guide's faint green-grey tints stay in debug (Sol review, triaged).
  Corrected by DD-054.
- DD-053: **DD-048's range-off return is device-verified: on the owner's OnePlus 13, 1.14.0-debug
  vc28 at 57561ec (owner, 2026-10-08), step 38a passed.** An extended 1018 K setpoint applied
  through the bridge reddened the panel; turning the range off and applying moved the key to 2596
  in about 3 s and the panel back with it; extended circadian in daylight held the key at 7308.
  With Shizuku stopped, the tick and a static apply clamped the key (4082, then 2596) while the
  panel kept the service's 4082, as DC-057 says for this key-ignoring build. The step's old "Apply
  lands the device's minimum" missed both that and the greyed Apply under circadian, so it now says so.
- DD-054 [cited]: **The User Guide's quote text, quote background and tip background turn blue in
  debug builds too (owner, 2026-10-08: they still read as teal on the device).** Release keeps its
  green-grey values; `UserGuideThemeTest` expects the variant's tint. Corrects DD-052's "tints stay".
- DD-055 [cited]: **Only the current owner publishes live runtime state (owner, 2026-10-08: option
  (a)).** `LiveRuntimeState.publish` takes the publishing service and runs under the claim/release
  lock; a publish from a released or superseded instance is dropped, so a destroyed service's late
  publish can no longer re-mark it running after the 5 s reset or overwrite a successor (completes
  DC-068). A stopped snapshot landing after `release` is dropped too, so the grace shows the last
  accepted one; no test forces a publish to straddle a reset (Sol review, triaged).
- DD-056 [cited]: **A scheduled TIME wake runs its evaluation as its own job, and the scheduler also wakes
  at local midnight (PR-time Astra review, 2026-10-08).** Run inside `collectLatest`, the
  evaluation was cancelled by its own `apply()` publishing the next boundary, so a suspending
  settings write left the rule active but its profile unwritten (pre-existing, made the only path
  by DD-042). A SUNRISE/SUNSET token armed before midnight kept the previous day's sun time; task43
  reads sun times fresh on each run, so a midnight wake now re-resolves them (owner, 2026-10-08).
  Glue review: each evaluation is a child of `timeJob`, so `stop()` cancels one that outlived a re-arm.
- DD-057: **The PR-time Astra review's remaining findings were triaged by user-visible impact
  (owner, 2026-10-08).** Fixed: the badge workflow publishes only from `main`, `pipeline_spec.md`
  notes DD-024, steps 38/38a follow the device's range, and a migration test's name claims less.
  Accepted limits: solar times use the evaluation day's UTC offset, so on a DST-change day they're an
  hour off; a clock rule inside the spring DST gap waits for the next wake; three AOSP-cache edges in
  the extended Night Light route (key-only after a bridge write, the null-anchor release, recovery
  suppressed while Shizuku is down); and e2e harness gaps (recovery order, preflight accepting a
  failed backup, install provenance, a teardown read failure skipping recovery, s11's rollback race).
  Corrected by DD-059.
- DD-058 [cited]: **Release preflight classifies `*/src/debug/*` and `e2e/*` as non-shipping (PR #144
  preflight, 2026-10-08).** Its fail-closed `classify_path` stopped on the first path in neither
  class, `app/src/debug/res/drawable/ic_launcher_background.xml` (DD-052); the `e2e/` harness
  (DD-015) was unclassified as well. Debug-variant sources never reach the release or F-Droid APK,
  and Gradle reads nothing under `e2e/`, so neither demands a version bump. The ladder never runs
  this workflow, so a new top-level tree stays green locally until the PR's preflight classifies it
  (accepted; prose-only).
- DD-059 [cited]: **Service stop puts Night Light back as Tideo found it, on/off and Kelvin, once Tideo
  has written it; other toggles still rest at the baseline (owner, 2026-10-09, revising D-151 for
  Night Light).** Owner's device: extended + circadian at noon left the service at a raw 7308, and
  disabling Tideo showed a 2596 K tint where Night Light had been off. A persisted `NightLightPrior`
  is taken just before the first write and dropped by panic; anchor and prior restores go through
  the bridge whenever the key alone cannot show them, and since `stop()` blocks the main thread
  that Shizuku binds on, the owed service write follows off it unless the key moved on. Sol's review
  (2026-10-09) fixed four; accepted: sub-second races between stop and a successor or startup's
  record load, an owed write lost to process death right after stop, Night Light switched back on
  before an owed service Kelvin lands, and DD-057's flag-reset edge.
  Corrected by DD-060.
- DD-060 [cited]: **With no `NightLightPrior` stored, a service start asserts a baseline's Night Light
  instead of adopting it (owner's device, 2026-10-09).** DD-059's stop hands Night Light back, so the
  next start's baseline seed claimed an "on" the device no longer showed and left it off until a
  manual Apply. The seed now takes Night Light as off with an unknown Kelvin, and the first
  context-evaluated profile writes it; a baseline with Night Light off still adopts, so a system
  Night Light the user turned on is not switched off at each start. Sol (2026-10-09): no defects.
- DD-061 [cited]: **A running service follows the stored circadian location and the IP fallback
  switch; it read the store once, at start (owner's device, 2026-10-09).** On a fresh install the
  service spent the day's DA-037 attempt before the owner enabled ipwho.is, and the fix "Use current
  location" then stored (DB-054) stayed unread, so the pipeline and Night Light ran on
  `TimeContext`'s 06–08/18–20 UTC placeholder windows (scale 1.15 where ~0.87 was due) until the
  service restarted.
  `CircadianWindowProvider` now follows the store (DataStore subscriptions, woken only by writes):
  it adopts a valid stored fix that differs from the one it holds, an acquisition that finishes
  after a fix was stored for its day is dropped, and switching the IP fallback on clears the
  persisted attempt day in the same write, so the freed day survives a restart — a user action,
  like DA-037's per-tap lookup, not an automatic retry. The store is the one way a location
  reaches the provider; the two view-models' copies of the status rule and the provider's test-only `status` became one
  `CircadianLocationStatus.of`, and four copies of the coordinate check became
  `LocationSnapshot.isValid`. Kept apart on purpose: background acquisition never powers GPS while
  the button does (D-122, DB-059), and DD-038's rule-time order is the owner's. JVM-tested;
  on-device behaviour is unverified.
