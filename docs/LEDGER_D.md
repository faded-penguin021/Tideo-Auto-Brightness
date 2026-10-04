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
