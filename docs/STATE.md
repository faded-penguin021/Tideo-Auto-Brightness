# STATE — project state & session memory

> **Length guard (DA-004).** Thresholds are in `amh.conf`; the compression rules, and the rule for
> what may sit in `Current state` at all, are `docs/RUNBOOK.md` → **Working-memory compression**,
> and they bind whether or not you follow this pointer. Prose-only — no guard judges it.

## Project

Native Kotlin/Compose rebuild of Tasker `Advanced_Auto_Brightness_V3.3`: `:domain` pure JVM,
`:platform` Android adapters, `:app` Compose/DataStore/FGS. BASIC runs core brightness; ELEVATED
adds super dimming and Privileged Display.

## Current state

Harness AMH 14.1.0 with its one hand step applied (DC-029…DC-036, DC-051); the live ledger is
`LEDGER_D.md`. **Release standing is NOT recorded here:** the session banner computes it
(`scripts/session-facts.sh`, DC-030), settled by hand with
`git ls-remote --tags --refs origin 'refs/tags/v*'`.

The tree declares 1.14.0 / vc28 (the train paragraph under the Owner queue). The light-stall
findings H1 and H2 remain recorded in DD-003 and DD-002. Device rounds on 1.10.0-debug vc24 are
closed, with the 0–4095 conversion path frozen as built, and a later build owes its own run
(DC-011…DC-013, DC-025…DC-028, DB-083; `DEVICE_TEST_SCRIPT.md` §2); no round script is alive
(RUNBOOK §6, DB-010), the force-stop investigation stays closed (DB-051…DB-060), and
Scorecard.dev is a run-once local input.

## Active work

- **Real-device E2E suite** — `docs/plans/DEVICE_E2E_PLAN.md` (owner-approved 2026-09-13): S0–S4
  done and the blocking Sol review of S2–S4 fixed (DD-021); S5's read-only preflight is built and
  passed on 1.14.0-debug vc28 (DD-029); no Tasker/MacroDroid profile on the owner's phone acts on
  `STATE_CHANGED` (owner, 2026-10-04), so device runs set `TIDEO_E2E_CONFIRM=automation`; S6's
  smoke set passed there (DD-032); S7 is under way (DD-033, DD-034): 15 of 21 auto scenarios
  pass (13) or skip correctly (2) on the phone; s06_19a and s11_39 await a build carrying DD-034's
  two app fixes, s06_19b is a Tideo defect awaiting the owner (Open questions), s02_10b is
  unclassified (Live Debug's brightness never matched the stored value in 15 s), and s02_10a/10e
  need the phone to wake unlocked. S8–S9 open. Owed when the
  partial rows get tests, from Sol on S4a: a dialog-root flag for SaveProfileDialog before s14_50, and the
  native flash overlay has no resource-id (s13_44a).
- **Night Light fix** — `docs/plans/NIGHT_LIGHT_CIRCADIAN_FIX.md`, for
  `faded-penguin021/AdvancedAutoBrightness#15` (not a Tideo issue): [x] N1 · [ ] N2 Kelvin bounds
  · [ ] N3 daytime activation, BLOCKED on device evidence · [ ] N4 close-out.
- **Solar offsets** — `docs/plans/CONTEXT_SOLAR_OFFSETS.md` (owner go-ahead 2026-10-06): [x] U1
  evaluator parity (DD-035) · [ ] U2 rule editor.

## Owner queue

> **Protected section (D-167).** Never delete it, and never silently drop items during compression
> — a ladder guard warns if the header vanishes. Items leave only when done, answered or triaged;
> then delete the item and record the outcome as a Changelog line or a ledger row. How to test an
> item before restating it, and why every session's final message must:
> `docs/RUNBOOK.md` → **Session discipline** 7.
>
> **Plain language here — exempt from the tree's terse, ledger-ID-first register (DB-079, owner,
> 2026-08-23)** because a person decides from it: say what to do, on what, what result means it
> worked, and the command that settles it, with ledger IDs last. Keep the Open questions format —
> fork, options, recommendation (D-167), dated (DA-006); credential leaks and external-content
> escalations land here too.

1. **[2026-09-23] On the next false "manual override" pause, read brightness before pressing
   Resume:** `adb shell settings get system screen_brightness` while Live Debug still shows the
   pause. About 193 means the 12 stuck, so something outside Tideo changed brightness; 241 means a
   dip that reverted by itself, which Tideo paused on because its "settled" value is a re-read
   3 ms later, and fixing that is a settle-window change for you to rule on. Extra Dim is ruled
   out (DC-059…DC-062).

2. **[2026-10-04] Check the proximity damp on the phone next time you test a build.** Tideo now
   follows your new Lux Smoothing and Detect Proximity: while the top of the phone is covered, the
   ×0.1 damps smoothing itself, and uncovering re-evaluates the last reading (DD-024). Settles it:
   run `DEVICE_TEST_SCRIPT.md` step 13. While covered, brightness should creep only part of the way
   toward a light change, with Live Debug's "Smoothing α" at a tenth. After you uncover the sensor it
   should catch up within a cycle or two, with no change in light.

3. **[2026-10-04] Check #139 and the global panic toggle on a 1.14.0 build.** Run
   `DEVICE_TEST_SCRIPT.md` step 23's charge-limit bullet and step 15a's global-toggle bullet. Worked
   if an "Only while plugged in" rule stays active through `adb shell dumpsys battery set status 4`
   (then `dumpsys battery reset`), and a rule loading a profile saved with "Only when plugged in" on
   leaves Live Debug's switch off (DD-025).

4. **[2026-10-04] Get two Chinese strings translated; they show in English until then.**
   `help_pwm_exponent` (DD-014) and `contexts_only_plugged_in` (DD-025) changed meaning, so their
   old Chinese was removed (DD-026). Ask a fluent speaker, such as #141's translator. Settles it:
   `grep -c -e 'name="help_pwm_exponent"' -e 'name="contexts_only_plugged_in"'
   app/src/main/res/values-b+zh+Hans/strings.xml` prints 2.

5. **[2026-10-04] Once this train is on `main`, check the translation badge.** The `Translation
   badges` workflow first runs there. Worked if its Actions run is green and the README's 简体中文
   badge shows a percentage. If its push was refused, let Actions create the `badges` branch
   (Settings → Actions → Workflow permissions, or the blocking ruleset) and rerun it. Settles it:
   `git ls-remote --heads origin badges` prints one line (DD-028).

6. **[2026-10-05] Check the new defaults on the phone when you next install a 1.14.0 build over your
   current one, and fix two things in Tasker.** Before installing, note the Misc screen's animation
   steps, min wait and max wait. Worked if they are unchanged when you had ever changed any of them,
   and otherwise read 50 / 5 / 30; loading an unedited Default shows a Reactivity midpoint of 4.0
   and an unedited Outdoors 4.255; and with "Trust unreliable sensor" on, loading an unedited
   built-in leaves it on. Settles the first part: `adb shell run-as com.tideo.autobrightness.debug
   cat files/datastore/aab_settings.json` lists `animSteps`, `minWaitMs`, `maxWaitMs` and
   `throttleDefaultMs` once the app has saved anything. In Tasker: task592's Outdoors declares
   `JSONObject genOutdoor` twice, so name the reactivity one `reactOutdoor`, and its second
   `min_wait` line stands where `genOutdoor.put("delta_factor", 4.0);` was, so put that back (Tideo
   keeps Outdoors at 4.0); and task637's
   self-healing emergency Default (`performLoad`) still writes `thresh_midpoint` 3.0 in the
   extracted source, so make it 4.0 if yours does too (DD-030, DD-031).

7. **[2026-10-05] Install the next 1.14.0 debug build, then let the E2E suite rerun two
   scenarios.** It carries two fixes the phone run found (DD-034): flashes stay Tideo's teal pill
   after the app is relaunched, and re-enabling the service after a panic brings your profile's
   inversion/grayscale/stay-awake back. Install with `TIDEO_E2E_SERIAL=<phone>:5555
   ADB_SERVER_HOST=127.0.0.1 e2e/run.sh --install <apk>` (you type `INSTALL`). Worked if
   `e2e/run.sh -k 's06_19a or s11_39'` passes; it needs WRITE_SECURE_SETTINGS granted, so `adb
   shell pm grant com.tideo.autobrightness.debug android.permission.WRITE_SECURE_SETTINGS` first
   and revoke it after if you want it off.

Open questions:

- **[2026-10-05] Should an open Dashboard notice a grant by itself?** DEVICE_TEST_SCRIPT 19b
  promises the badge reaches ELEVATED within ~10 s of an adb grant with no restart, but no screen
  calls `refreshTier()`, so it changes only when the app is reopened; e2e s06_19b fails on that.
  Options: (a) poll the tier every few seconds while the Dashboard is visible; (b) refresh on
  resume and change the script to "after returning to the app". Recommendation: (b), since a grant
  is a once-per-install event and polling costs a Binder call every few seconds forever.
- **[2026-10-05] Do you want the wake scenarios (s02_10a, s02_10e) automated?** They sleep and
  wake the screen and need it to come back unlocked. Options: (a) set the phone to lock a few
  minutes after screen-off while testing; (b) keep them manual. Recommendation: (a) for test
  sessions only. s02_10a's own check (no false pause on wake) passed before the lock stopped it.

**This train is `1.14.0` on vc28, its ONE bump** (the owner's minor bump of 2026-10-04, moved off
1.13.0 / vc27 when `main` shipped #141). It carries #139 and is shared with the #136 and
solar-offset plans. Land further user-facing fixes in `changelogs/28.txt` (500-character cap),
never by bumping or creating `29.txt`; re-open only for something major, and say so.

## Decided non-items

- **No migration resets an already-snowballed `nightLightTemperature` (owner, 2026-09-21;
  DC-055).** A stored Kelvin cannot be told apart from a setpoint the user genuinely chose, so a
  blanket reset to null would discard real choices while missing contaminated profiles that
  currently have circadian off. DC-055 stops the capture; an affected user clears it with the
  screen's existing "device default" button. Do not propose a one-shot reset.
- **Both backup-fix questions are closed (owner, 2026-09-18; DC-052)** — the pre-fix blast radius
  will not be measured, needing `bmgr` work the owner declines, and no regression test guards
  `android:backupAgent`, one having been dropped as YAGNI; propose neither. The owner later ran
  one package-scoped restore check (2026-09-27): it found and fixed the missing
  `fullBackupOnly` and verified the restore path end to end (DD-008).
- **The `stop()`/`emergencyStop()` join asymmetry stays** (owner, 2026-09-07; DC-047).
- **Issues #123, #126 and #127 get no reply, and no issue gets one unasked** (owner, 2026-08-24,
  re-confirmed 2026-09-07; DB-082) — a standing rule, and nothing was posted.
- **Three device checks will never be executed (owner, 2026-09-07)** — the Android 12/12L Wi-Fi fix
  (DB-074), the unrecognised-colour-mode button (DB-071, DB-078) and Night Light / always-on failing
  safely (DB-041…DB-043) all need hardware the owner lacks.
- **Graph Metrics is owner-tested; its automated wiring tests are declined** (owner, 2026-09-07;
  DC-001).
- **The x86_64-JDK-on-ARM rule is a local host concern** (owner, 2026-09-07; DC-033,
  `.orch/LOCAL_LADDER.md`).
- Still declined: root changelog, speculative dependency bumps, standalone drift audit, Gradle
  dependency verification, wider session-branch CI, the D-162/DA-021 triage sets (DB-038), the
  superseded Privileged Display schedule and a persisted seed without real reports (D-150–152), a
  grayscale quick action, refresh-rate/OEM keys, manual Extra Dim, panic re-firing after teardown,
  §11.39a C1/C2 as wontfix, a scripted `bmgr restore` step (DB-013; the owner ran it once by
  hand, DD-008), and migrating
  the test-only `ContextsContent` wrapper; the rest is in `docs/plans/REVIEW_TRIAGE_1.9.0.md`.
- **Never synthesise unsupported display values on a device** (DB-071) — use a real settings UI;
  DB-077 is exempt, mask 7 having been written by Tideo v1.9.0.
- Rejected by the #126/#127 plan and not to be reintroduced: keying wake behaviour on
  `ACTION_USER_PRESENT`/unlock (owner, 2026-08-30), a larger fixed or blanket settle window, wake
  baseline adoption, a recent-write token set (D-034/D-051(d)), and auto-learning the device
  maximum.

## Changelog

Newest first; ledger rows are the durable detail.

- 2026-10-06 — **Solar offsets U1: the evaluator follows AAB's task43 revision of 2026-10-01 —
  `SUNRISE±N`/`SUNSET±N` endpoints, a rule held for all of its end minute, an exit wake at end +
  1 min — and an imported offset token no longer aborts evaluation (DD-035).**

- 2026-10-05 — **E2E S7 checkpoint: the effect-ordered suites ran on the phone; the harness now
  reads focus, the notification shade and greyed Apply the way OxygenOS 16 shows them (DD-033),
  and two Tideo defects it found are fixed in the app, unverified on device (DD-034).**

- 2026-10-05 — **E2E S6: smoke passes on the owner's phone on 1.14.0-debug vc28, after fixing the
  harness's OxygenOS service-record and chip-label parsing (DD-032).**

- 2026-10-05 — **First launch now takes the owner's task570 animation defaults (50 / 5 / 30 /
  1510) and the built-ins task592's midpoints (4.0, Outdoors 4.255), so Default is the
  first-launch settings; any animation value a user changed is kept, in settings, baseline snapshot
  and exports, which now store every key with no schema bump, and an untouched built-in follows
  (DD-030); loading an untouched built-in keeps the user's trust-unreliable and Quick Settings
  choices (DD-031).**

- 2026-10-04 — **E2E S5: read-only preflight built and passed on the phone; 1.14.0-debug vc28 is
  its first guarded install (DD-029). Owner: no automation receiver, so all 21 scenarios clear
  the SKIP rules.**

- 2026-10-04 — **`main` merged in after #141 shipped as v1.13.0 / vc27 (Simplified Chinese, the
  language picker, the User Guide theme; device appearance unverified), so this train moved to
  1.14.0 / vc28 with `changelogs/28.txt`.**
- 2026-10-01..04 — **This train so far: E2E S1–S4 (DD-015…DD-021), the PWM help (DD-014), the
  proximity damp (DD-022, DD-024), #139 (DD-025), translation policy, English fallback and CI
  coverage badges (DD-023, DD-026…DD-028), #138's actions bump.**
- 2026-06-23..09-28 — **v1.0.0 → v1.12.0 (D-096…DD-012); #133 and #134 owner-passed on
  1.12.0-debug vc26.**
