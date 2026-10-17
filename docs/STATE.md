# STATE — project state & session memory

> **Length guard (DA-004).** Thresholds are in `amh.conf`; the compression rules, and the rule for
> what may sit in `Current state` at all, are `docs/RUNBOOK.md` → **Working-memory compression**,
> and they bind whether or not you follow this pointer. Prose-only — no guard judges it.

## Project

Native Kotlin/Compose rebuild of Tasker `Advanced_Auto_Brightness_V3.3`: `:domain` pure JVM,
`:platform` Android adapters, `:app` Compose/DataStore/FGS. BASIC runs core brightness; ELEVATED
adds super dimming and Privileged Display.

## Current state

Harness AMH 15.1.0, scripts and hand-applied seed prose together (DD-066); the live ledger is
`LEDGER_D.md`. **Release standing is NOT recorded here:** the session banner computes it
(`scripts/session-facts.sh`, DC-030), settled by hand with
`git ls-remote --tags --refs origin 'refs/tags/v*'`.

The tree declares 1.15.0 / vc30 (the train paragraph under the Owner queue); 1.14.0's device
round passed on 1.14.0-debug vc28 (DD-036), and step 38a's #145 check passed on 1.14.1-debug vc29
with a brief warmer tint before the expected one (DD-065). The 0–4095 conversion
path stays frozen as built (DC-011…DC-013, DC-025…DC-028, DB-083), the light-stall findings H1/H2
are DD-003/DD-002, no round script is alive (RUNBOOK §6, DB-010), the force-stop investigation
stays closed (DB-051…DB-060), and Scorecard.dev is a run-once local input.

## Active work

- **Low-lux settling jitter** — `docs/plans/LOW_LUX_SETTLING_JITTER.md`, spec only; its
  **Proposed fix** is approved as written (owner, 2026-10-08) and deferred to the train after
  1.15.0 (owner, 2026-10-17), each step on the owner's go-ahead.

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

1. **[2026-10-04] Get twelve Chinese strings translated; they show in English until then.**
   `help_pwm_exponent` (DD-014), `contexts_only_plugged_in` (DD-025),
   `pd_night_light_temp_hint` (DD-045) and `ssid_help_dump_security` (DD-077, added 2026-10-17)
   changed meaning, so their old Chinese was removed (DD-026),
   the rule editor's four `contexts_offset_*` strings are new (DD-035), and so are the extended
   Night Light range's four: `settings_night_light_extended` (DD-047),
   `pd_night_light_temp_hint_extended` and the two `pd_night_light_extended*` (DD-050). Asked in
   #143 (2026-10-08); it does not block the release, since missing keys fall back to English
   (owner, 2026-10-08). Paste translations replied there into the file. Settles it:
   `grep -c -e 'name="help_pwm_exponent"' -e 'name="contexts_only_plugged_in"' -e
   'name="pd_night_light_temp_hint' -e 'name="contexts_offset_' -e
   'name="settings_night_light_extended"' -e 'name="pd_night_light_extended' -e
   'name="ssid_help_dump_security"' app/src/main/res/values-b+zh+Hans/strings.xml` prints 12.

2. **[2026-10-13] Decide whether to add a check that the output-redaction hook is still wired.**
   Since AMH 15.0.0 both adapters run `scripts/redact-tool-output.sh` after every tool call, but
   nothing in this tree notices if that `PostToolUse` group is deleted, rewired or loses its bash
   pin — a missing redaction hook looks exactly like output that held no credential (DD-066).
   Options: (a) a fact check in `scripts/guards/doc-facts.sh` with a fixture in
   `scripts/tests/local-guards.sh`, a rule change under rule review; (b) leave it prose-only, as
   disclosed in the adapter comments. Recommendation (a): upstream ships the same check in its own
   repository for this reason. Unverified until you answer; settles it: `grep -c
   redact-tool-output scripts/guards/*.sh` prints a nonzero count, or a STATE changelog line records
   (b).

3. **[2026-10-16] Move CI to Ubuntu 26.04 when you choose; nothing breaks if you wait.** Every
   workflow is pinned to `ubuntu-24.04` (DD-068), so GitHub's switch of `ubuntu-latest` to 26.04
   (2026-10-19 to 11-19) changes nothing here. Options: (a) move after the rollout settles, in a
   PR that changes only the runner labels; (b) stay on 24.04 until GitHub announces its
   retirement. Recommendation (a): accept it only when that PR's `Build` and all four
   `F-Droid compatibility` stages are green, the reproducibility stage above all. Settles it:
   `grep -rc 'ubuntu-24.04' .github/workflows/` prints 0 for every file, or a STATE changelog line
   records (b).

**The tree declares `1.15.0` / vc30, this train's ONE bump**, made by the hand-off plan's unit 0
after v1.14.1 was seen tagged on 2026-10-16 (`git ls-remote --tags --refs origin 'refs/tags/v*'`).
`changelogs/30.txt` (500-character cap) carries only the hand-off, since the low-lux jitter fix
moved to the next train. Never bump twice in one train.

## Decided non-items

Binding owner declines live in `docs/rebuild/DECIDED_NON_ITEMS.md` (moved there by the owner's
grant, DD-037), including the `docs/plans/REVIEW_TRIAGE_1.9.0.md` declines. Read it before
proposing work; changing it is legislation under RUNBOOK's rule-review protocol.

## Changelog

Newest first; ledger rows are the durable detail.

- 2026-10-17 — **E2E suite on 1.15.0-debug vc30: 17 of 20 auto rows pass, 3 skip on their own
  preconditions, journal empty, after one harness-timing teardown miss that `--recover` fixed
  (DD-086). #136's OxygenOS 16 calibration needed "Disable system optimization" on; it stays a
  workaround (DD-087).**

- 2026-10-17 — **Owner's cursory pass of DEVICE_TEST_SCRIPT §18 on the phone found it all fine;
  Owner queue item 4 is closed and the train goes to its PR (DD-084). Astra's ship check said
  HOLD on an interrupted calibration, left to the owner's merge call, and a data-keeping
  downgrade, which the owner dismissed (DD-085).**

- 2026-10-17 — **Hand-off unit 6 closes the #136 plan: `30.txt` final, parity gap dev-03,
  DEVICE_TEST_SCRIPT §18 steps 58–61 with their e2e rows, the settings schema and the DataStore
  map (now listing `display_prefs` and the controller's `screen_brightness_controller` prefs);
  the AAB export in `dist/` and the plan file are deleted, the plan's last unrecorded facts kept
  as DD-082 and DD-083 (Sol review). Owner queue item 4 asks for §18. The
  low-lux jitter fix is deferred to the train after 1.15.0 (owner).**

- 2026-10-15..17 — **Bright-light hand-off (#136) on 1.15.0 / vc30, from the owner's AAB build:
  AAB's reference (DD-070, DD-072), domain `HandoffRules` and per-profile settings with four
  owner-approved deviations (DD-071), the controller's `handed_off` marker (DD-073), the runtime coordinator (DD-074),
  surfaces that say "Android", the Hand-off Behavior screen and AAB's flashes (DD-075, DD-076),
  and the HBM readout with display calibration (DD-077…DD-080), which passed the owner's sunlight
  test on the phone (DD-081; debug builds go on over adb, as Play Protect blocks a file-manager
  install).**
- 2026-10-16 — **`DraftSettingsViewModelTest`'s intermittent CI hang fixed, test-only: an earlier
  test's in-flight `apply()` wedged the DataStore writer across Robolectric's looper reset
  (DD-069).**
- 2026-10-16 — **The `DraftSettingsViewModelTest` CI-hang fix went out as PR #147 off `main`;
  Owner queue item 2 now asks for that merge.**
- 2026-10-16 — **Every workflow pinned to `ubuntu-24.04` ahead of GitHub moving `ubuntu-latest`
  to 26.04 (DD-068); the move itself is Owner queue item 3.**
- 2026-10-16 — **CI speed: `build.yml` runs the guard fixture suites only after a failed ladder,
  since `verify.sh` already runs them (~80 s a run, DD-067).**
- 2026-10-13 — **Harness AMH 14.1.0 -> 15.1.0: root/home deletions permanently denied, checkpoint
  reordered (STATE line → ladder → commit → `--guards-only` → push), a Destructive work section in
  AGENTS.md, output redaction wired as a `PostToolUse` hook in both adapters (DD-066).**

- 2026-10-10 — **1.14.1 / vc29 for #145: a circadian Night Light key that the device moved off
  what Tideo wrote is rewritten by the next tick, and an apply gets an early tick 5 s later
  (DD-064); owner-verified on device, with a brief warmer tint before the expected one (DD-065).**
- 2026-10-10 — **Translation badges work on `main` (owner-confirmed; `badges` branch present,
  DD-028).**
- 2026-10-10 — **Owner-confirmed on device: Circadian follows a new location without a restart,
  the live card shows the applied scale, and the dusk scale refresh steps (DD-061…DD-063).**
- 2026-10-09 — **Open question answered (a): AAB's 2-minute prof758 re-run refreshes the scale
  readout during the dawn/dusk ramps and moves no brightness; the owner declined (b) as jitter in
  steady light. One shared helper now serves the pipeline, that tick and Night Light (DD-063).**
- 2026-10-09 — **Night Light goes back as found on disable and on again on enable, both
  owner-confirmed (DD-059, DD-060). A running service follows the stored circadian location and
  the IP fallback switch (DD-061); a reapply now publishes the scale it applied (DD-062).**
- 2026-10-08 — **Release preflight treats debug sources and `e2e/` as non-shipping (DD-058); Astra
  review fixes for TIME wakes and the badge workflow (DD-056, DD-057); only the owning service
  publishes live state (DD-055); debug builds are dark blue (DD-052, DD-054); step 38a passed on
  device (DD-053); the low-lux jitter rule approved for after 1.14.0.**
- 2026-10-06..08 — **Night Light: the device's Kelvin range (N2, DD-045) and the opt-in extended
  686–7308 K range per profile, Shizuku or root only (N2b, DD-046…DD-051); the plan is archived as
  `docs/history/NIGHT_LIGHT_CIRCADIAN_FIX.md`.**
- 2026-10-04..07 — **E2E suite S5–S9 done on the owner's phone, S8 dropped (DD-029,
  DD-032…DD-034, DD-039…DD-044); RUNBOOK playbook 9 runs it.**
- 2026-10-06 — **Device round on 1.14.0-debug vc28 passed nine checks (DD-036); Decided non-items
  moved to `docs/rebuild/DECIDED_NON_ITEMS.md` (DD-037); SUNRISE/SUNSET rules follow the pinned
  location (DD-038); TIME rules wake only at the next context time (DD-042).**
- 2026-10-01..06 — **This train (1.14.0 / vc28 after `main` shipped #141 as v1.13.0): E2E S1–S4
  (DD-015…DD-021), the PWM help (DD-014), the proximity damp (DD-022,
  DD-024), #139 (DD-025), translation policy and coverage badges (DD-023, DD-026…DD-028), the
  owner's first-launch defaults (DD-030, DD-031), AAB's solar offsets (DD-035), #138's actions
  bump.**
- 2026-06-23..09-28 — **v1.0.0 → v1.12.0 (D-096…DD-012); #133 and #134 owner-passed on
  1.12.0-debug vc26.**
