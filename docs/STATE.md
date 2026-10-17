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

- **Bright-light hand-off (#136)** — `docs/plans/BRIGHT_LIGHT_HANDOFF.md`, spec only (owner,
  2026-10-01). It was rewritten on 2026-10-15 against the owner's AAB build and the owner's
  fixes after it, with four deviations and Open question 1 (HBM readout). It goes in the train after 1.14.0 (owner,
  2026-10-07), each unit on the owner's go-ahead. The owner's AAB build of it is force-added under `dist/aab-bright-light-handoff/` as
  the port's reference; `git rm -r` it before this train's PR merges (`clean-dist.yml` is only
  the backstop). A `BUDGET_app` comment-budget raise, if unit 5 needs one, is pre-approved and
  folds into that unit (owner, 2026-10-16); it still takes the DA-005 rule review. Units, in the
  plan's order:
  - [x] 0 open the train (1.15.0 / vc30, adb facts)
  - [x] 1 domain `HandoffRules` + DD rows (DD-070…DD-072)
  - [x] 2 settings
  - [x] 3 platform (glue review)
  - [x] 4 runtime (glue review)
  - [x] 5 UI (glue review)
  - [ ] 6 close-out
  - [x] 7 HBM readout and display calibration (owner's answer to Open question 1)
- **Low-lux settling jitter** — `docs/plans/LOW_LUX_SETTLING_JITTER.md`, spec only; its
  **Proposed fix** is approved as written and goes in the train after 1.14.0 (owner, 2026-10-08),
  each step on the owner's go-ahead.

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
The hand-off units and the low-lux jitter fix each add a sentence to `changelogs/30.txt`
(500-character cap) instead. Never bump twice in one train.

## Decided non-items

Binding owner declines live in `docs/rebuild/DECIDED_NON_ITEMS.md` (moved there by the owner's
grant, DD-037), including the `docs/plans/REVIEW_TRIAGE_1.9.0.md` declines. Read it before
proposing work; changing it is legislation under RUNBOOK's rule-review protocol.

## Changelog

Newest first; ledger rows are the durable detail.

- 2026-10-17 — **Owner's sunlight test of unit 7 passed on 1.15.0-debug vc30: calibrated 255 →
  4095, handed off at 92 048 lx, 4171 raw shown as 260 and 258, the HBM flash seen; Play Protect
  blocks a file-manager install, so debug builds go on over adb (DD-081).**

- 2026-10-17 — **Hand-off unit 7, the owner's answer to Open question 1: while handed off, the
  dashboard, widget and Live Debug show Android's brightness from `dumpsys display` (DUMP, root,
  then Shizuku, now a fourth Shizuku use), above 255 under High Brightness Mode, after a display
  calibration on Hand-off Behavior; AAB's 2 s loop, HBM flashes and calibration card (DD-077…DD-080).**

- 2026-10-17 — **Owner's device test of unit 5: the hand-off settings move to their own Menu
  screen, Hand-off Behavior, as AAB's scene, and each hand-off and hand-back flashes AAB's message
  (Pause and Disable stay silent) (DD-076).**

- 2026-10-17 — **Hand-off unit 5: a hand-off card on Curve & Brightness (switch, lux with the
  curve's minimum, 50–100 % hand-back slider) and a graph marker at the effective threshold; the
  notification, pill, widget, tile and Live Debug say "Android", and brightness readouts blank
  while handed off; help in tooltips and guide section 10 (DD-075).**

- 2026-10-16 — **Hand-off unit 4: a runtime `HandoffCoordinator` hands Android AUTOMATIC above the
  effective threshold and takes it back below the exit, with no write, dimming or override while
  handed off; Apply, a context swap, Resume and Pause decide at once, start reconciles the marker,
  and an entry and `stop()` serialize (DD-074). Nothing shows it until unit 5.**
- 2026-10-16 — **Hand-off unit 3: the brightness controller hands Android AUTOMATIC under a
  persisted `handed_off` marker committed with the saved mode first, reclaims MANUAL, and
  reconciles the marker at start, so Tideo's own AUTOMATIC is never saved as the user's mode
  (DD-073). Nothing calls it until unit 4.**
- 2026-10-16 — **Hand-off unit 2: per-profile `handoffEnabled` / `handoffLux` (25 132) /
  `handoffHysteresis` (0.8) under AAB's `%AAB_` names, imported from AAB's key=value and
  `adaptive_handoff` JSON with lux rounded up; a hysteresis outside 0.5–1.0 becomes 0.8, and no
  validator rule blocks Apply (DD-071).**
- 2026-10-16 — **Hand-off unit 1: domain `HandoffRules` decides enter/stay/exit with AAB's strict
  edges and finds the curve's minimum hand-off lux (25 132 on the default curve) by a downward
  scan that holds on non-monotone curves. DD-070…DD-072 record the AAB reference before `dist/`
  goes.**
- 2026-10-16 — **`DraftSettingsViewModelTest`'s intermittent CI hang fixed, test-only: an earlier
  test's in-flight `apply()` wedged the DataStore writer across Robolectric's looper reset
  (DD-069).**
- 2026-10-16 — **Hand-off unit 0: the train opens as 1.15.0 / vc30 with `changelogs/30.txt`. AOSP
  HBM is unconfigured on the owner's phone (OEM boost 4095 → 4674), so `mHbmMode` is no HBM fact.
  AAB's rule, dumpsys brightness above 4095, is. A sensor's declared `maxRange` (65 535 here) is no
  saturation point: the owner has seen readings over 100 000 lx. Details in the plan's Open
  question 1.**
- 2026-10-16 — **The `DraftSettingsViewModelTest` CI-hang fix went out as PR #147 off `main`;
  Owner queue item 2 now asks for that merge.**
- 2026-10-16 — **Every workflow pinned to `ubuntu-24.04` ahead of GitHub moving `ubuntu-latest`
  to 26.04 (DD-068); the move itself is Owner queue item 3.**
- 2026-10-16 — **CI speed: `build.yml` runs the guard fixture suites only after a failed ladder,
  since `verify.sh` already runs them (~80 s a run, DD-067).**
- 2026-10-15 — **Hand-off plan (#136) rewritten against the owner's AAB build (export in
  `dist/`). It uses AAB's names and decision rule, an effective threshold instead of a blocking
  validator, a 90 % hysteresis cap, ownership persistence and recovery, and a mode observer, and
  it targets 1.15.0 / vc30. Reviewed by Sol (analysis) and Astra (plan).**
- 2026-10-15 — **The owner fixed five of the review's AAB findings and declined two as user error:
  switching Adaptive Brightness off by hand, and a 100 % hysteresis. The plan now matches AAB on
  those: no mode observer, and hysteresis 50–100 %. Four deviations remain.**

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
