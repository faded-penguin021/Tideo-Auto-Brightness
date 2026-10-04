# STATE — project state & session memory

> **Length guard (DA-004).** Thresholds are in `amh.conf`; the compression rules, and the rule for
> what may sit in `Current state` at all, are `docs/RUNBOOK.md` → **Working-memory compression**,
> and they bind whether or not you follow this pointer. Prose-only — no guard judges it.

## Project

Native Kotlin/Compose rebuild of Tasker `Advanced_Auto_Brightness_V3.3`: `:domain` pure JVM,
`:platform` Android adapters, `:app` Compose/DataStore/FGS. BASIC runs core brightness; ELEVATED
adds super dimming and Privileged Display.

## Current state

Harness AMH 14.1.0 with its one hand step applied (DC-029…DC-036, DC-051); upstream manifest
scripts are immutable; the live ledger is `LEDGER_D.md`. **Release standing is NOT recorded
here:** the session banner computes it (`scripts/session-facts.sh`, DC-030), settled by hand with
`git ls-remote --tags --refs origin 'refs/tags/v*'`.

The tree declares 1.14.0 / vc28 and includes Simplified Chinese and app-language selection
(#141), #134's notification Discard (DD-011) and #133's unclamped curve inputs (DD-012). This
train adds the proximity damp in smoothing (DD-022, DD-024) and #139's plugged-in fix with the
global panic toggle (DD-025). The light-stall findings H1 and H2 remain recorded in DD-003 and
DD-002. Device rounds on 1.10.0-debug vc24 are closed, with the 0–4095 conversion path frozen as
built, and a later build owes its own run (DC-011…DC-013, DC-025…DC-028, DB-083;
`DEVICE_TEST_SCRIPT.md` §2); no round script is alive (RUNBOOK §6, DB-010), the force-stop
investigation stays closed (DB-051…DB-060), and Scorecard.dev is a run-once local input.

## Active work

- **Real-device E2E suite** — `docs/plans/DEVICE_E2E_PLAN.md` (owner-approved 2026-09-13): S0–S4
  done and the blocking Sol review of S2–S4 fixed (DD-021); next is S5, read-only preflight, which
  needs the owner to start the host adb server; S5–S9 open. Owed when the partial rows
  get tests, from Sol on S4a: a dialog-root flag for SaveProfileDialog before s14_50, and the
  native flash overlay has no resource-id (s13_44a).
- **Night Light fix** — `docs/plans/NIGHT_LIGHT_CIRCADIAN_FIX.md`, for
  `faded-penguin021/AdvancedAutoBrightness#15` (not a Tideo issue): [x] N1 · [ ] N2 Kelvin bounds
  · [ ] N3 daytime activation, BLOCKED on device evidence · [ ] N4 close-out.

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

4. **[2026-10-04] Get two Chinese strings translated; until then they show in English.** Both
   English strings changed meaning on this train, so their outdated Chinese was removed (DD-026):
   `help_pwm_exponent` (the PWM exponent help, DD-014) and `contexts_only_plugged_in` (the
   "Only while plugged in" rule label, DD-025). Ask a fluent speaker, such as #141's translator,
   per `CONTRIBUTING.md`. Settles it: `grep -c -e 'name="help_pwm_exponent"' -e
   'name="contexts_only_plugged_in"' app/src/main/res/values-b+zh+Hans/strings.xml` prints 2.

Open questions:

- None.

**This train is `1.14.0` on vc28, its ONE bump** — the owner chose a minor bump on 2026-10-04,
and it moved off 1.13.0 / vc27 when `main` took that for #141's Simplified Chinese. It was opened
by #139 and is shared with the #136 and solar-offset plans. Land further user-facing fixes by
editing `changelogs/28.txt` (500-character cap), never by bumping or by creating `29.txt`; re-open
only for something major, and say so.

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

- 2026-10-04 — **Untranslated strings fall back to English (owner): lint's `MissingTranslation`
  is a warning, and the two Chinese strings whose English changed meaning were removed rather
  than left stale (DD-026).**
- 2026-10-04 — **`main` merged in after #141 shipped as v1.13.0 / vc27, so this train moves to
  1.14.0 / vc28 with its notes in `changelogs/28.txt`; `26.txt` and `27.txt` are main's, as
  published. The merge brings Simplified Chinese, app-language selection and the User Guide theme
  fix.**
- 2026-10-04 — **#139: context rules read "plugged in" from `EXTRA_PLUGGED`, ported from the
  owner's AAB task43 (AdvancedAutoBrightness#21), so a charge limit no longer flaps them; the rule
  label is now "Only while plugged in"; the panic "Only when plugged in" toggle survives profile
  loads, rule reverts, Reset, import and Apply, as DB-009 ruled. Opens 1.13.0 / vc27 (owner)
  (DD-025).**
- 2026-10-04 — **Proximity damp moved into smoothing, per the owner's task535 A3b. That answers
  the open question with (b): while covered, smoothed lux moves a tenth as far. Uncovering now
  re-evaluates the last raw reading (task545 A5); the oracle changed with the source (DD-024,
  superseding DD-022).**
- 2026-10-04 — **Translation policy relaxed (owner, #140): human and AI-assisted translations
  that a fluent speaker reviewed string by string are accepted, unreviewed machine translation is
  not; `CONTRIBUTING.md` and the README say so (DD-023).**
- 2026-10-04 — User Guide HTML and its WebView background follow the app theme, including changes
  while the page is open; dark-mode accents and tinted callouts retain the original palette, with
  corresponding readable gold/green/coral colors in light mode. Robolectric covers both palettes,
  light-mode contrast and both switch directions; actual device rendering remains unverified.
- 2026-10-03..04 — Added Simplified Chinese and persistent app-language selection (1.13.0 / vc27),
  including System default; UI, grant feedback, diagnostics (DC-040), profile lists, notifications
  and widgets follow the language. Review fixes use one language lookup per notification,
  profile labels matching all 40 original English entries, with independent Chinese labels where
  screen wording differs, and refreshed channel names with Android 12/12L storage-race
  coverage; changelog 26 is unchanged. Translation/picker guidance is documented;
  compiled launch resources retain AppCompat, with device appearance unverified.
- 2026-10-03 — **Proximity damp follows the owner's Tasker V2: the ×0.1 α (3 dp) now sizes the
  animation as well as the readout, smoothing still undamped; the oracle changed with it, the
  source having changed (DD-022, superseding DC-064's readout-only damp).**

- 2026-10-01..02 — **E2E S1–S4 and the blocking Sol review of S2–S4: `e2e/` scaffolding, the
  command/UI boundary, effect journal and recovery, 21 device scenarios behind an effect-gated
  `Run`, root test tags as resource-ids; all review findings fixed; the deferred full ladder ran
  green on `b761165`; no device contacted (DD-015…DD-021).**
- 2026-10-01 — **#138's grouped github-actions bump (wrapper-validation 6.4.0, setup-java 6.0.1,
  setup-android 4.0.4, codeql-action 4.38.2) cherry-picked onto this train after full CI went
  green on the PR; every pin resolved to its tag by hand; #138 closed as included.**
- 2026-10-01 — **The PWM software-exponent help now matches `finalDimLevel` (higher dims more),
  departing from task702's flash; the owner ports it back to AAB (DD-014).**
- 2026-09-28 — **#133: curve inputs persist unclamped wherever Apply accepts them (DD-012); owner
  confirmed on 1.12.0-debug vc26 that a Form1A-40 curve and #133's exact curve (Form1A 28.7353),
  loaded as profiles, survive, the latter after a force-stop.**
- 2026-09-28 — **1.12.0/vc26 opened from `main` (v1.11.0 tagged, Owner-queue item closed); #134:
  the override notification can Discard the adjustment it just recorded (DD-011); owner passed
  `DEVICE_TEST_SCRIPT.md` step 57 on 1.12.0-debug vc26.**

- 2026-09-27 — **Backup actually saves data now (DD-008):** `android:fullBackupOnly="true"` moves
  the helper-less agent off the empty key/value path; the backup and a Tideo-only restore,
  including the sanitizer, were verified on the OnePlus 13.
- 2026-09-25 — **Light-stall train closed (R8):** its plan is deleted and its record is DC-063…DC-071
  and DD-001…DD-007, with H1 and H2 open. The ledger rolled over to `LEDGER_D.md` (DC-071 ended
  past the cap), and `AGENTS.md` names the new live volume. R7's settling passed on the OnePlus 13
  (DD-006) and became `DEVICE_TEST_SCRIPT.md` step 56. Live Debug and the diagnostic cards show lux
  at its stored precision and the dynamic threshold as a percentage (owner request).
- 2026-09-23..25 — **Light stalls R0–R7 and F-G (DC-059…DC-071):** notification, diagnostics,
  startup race, watchdog, Tasker's dead band, proximity damp, pending slot, settling; a false
  override pause on unlock diagnosed, not fixed.
- 2026-09-18..22 — **Night Light: snowball closed, anchor on the device's own Kelvin (DC-055,
  DC-056), Kelvin via `color_display` where the key is ignored, making vc25 `1.11.0` (DC-057,
  DC-058); backup-agent fix (DC-052).**
- 2026-09-02..13 — **AMH 9.1.0 → 14.1.0 (DC-029…DC-036, DC-050, DC-051), the runtime rot audit
  (DC-037…DC-049), E2E suite planned.**
- 2026-06-23..08-31 — **v1.0.0 → v1.9.2, then the #126/#127 train (D-096…DC-028).**
