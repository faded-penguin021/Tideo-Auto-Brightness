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

The tree declares 1.14.0 / vc28 (the train paragraph under the Owner queue); its device round
passed on 1.14.0-debug vc28 (DD-036), and a later build owes its own run. The 0–4095 conversion
path stays frozen as built (DC-011…DC-013, DC-025…DC-028, DB-083), the light-stall findings H1/H2
are DD-003/DD-002, no round script is alive (RUNBOOK §6, DB-010), the force-stop investigation
stays closed (DB-051…DB-060), and Scorecard.dev is a run-once local input.

## Active work

- **Bright-light hand-off (#136)** — `docs/plans/BRIGHT_LIGHT_HANDOFF.md`, spec only (owner,
  2026-10-01); it goes in the train after 1.14.0 (owner, 2026-10-07), each unit on the owner's
  go-ahead.
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

1. **[2026-10-04] Get eleven Chinese strings translated; they show in English until then.**
   `help_pwm_exponent` (DD-014), `contexts_only_plugged_in` (DD-025) and
   `pd_night_light_temp_hint` (DD-045) changed meaning, so their old Chinese was removed (DD-026),
   the rule editor's four `contexts_offset_*` strings are new (DD-035), and so are the extended
   Night Light range's four: `settings_night_light_extended` (DD-047),
   `pd_night_light_temp_hint_extended` and the two `pd_night_light_extended*` (DD-050). Asked in
   #143 (2026-10-08); it does not block the release, since missing keys fall back to English
   (owner, 2026-10-08). Paste translations replied there into the file. Settles it:
   `grep -c -e 'name="help_pwm_exponent"' -e 'name="contexts_only_plugged_in"' -e
   'name="pd_night_light_temp_hint' -e 'name="contexts_offset_' -e
   'name="settings_night_light_extended"' -e 'name="pd_night_light_extended'
   app/src/main/res/values-b+zh+Hans/strings.xml` prints 11.

2. **[2026-10-10] Fix the intermittent CI hang in `DraftSettingsViewModelTest`; until then, rerun
   on a hang.** The `Build` run on `main` for #144 (run 38039291255, attempt 1) sat in the ladder
   step until cancelled; its thread dump shows Robolectric's main thread parked in
   `setBaseline`'s `runBlocking { settingsDataStore.updateData { … } }`
   (`DraftSettingsViewModelTest.kt:42`, from `edit_marksDirty_thenDiscardReverts`) with every
   coroutine worker idle. The same tree passed on the PR, and attempt 2 got past it. The test is
   old and flaked before (D-071, D-080). `release.yml` and `release-signing.yml` run the same
   tests, so a hang there costs their 30-minute cap and a rerun, never a bad release; F-Droid
   builds `assembleRelease` only and never runs tests. Needs a follow-up fix PR off `main`, on
   your go-ahead. Worked when that PR is merged and a `Build` run on `main` is green; settles it:
   `git log --oneline origin/main -- app/src/test/kotlin/com/tideo/autobrightness/app/state/DraftSettingsViewModelTest.kt`
   shows a commit after 2026-10-10.

**This train is `1.14.0` on vc28, its ONE bump** (the owner's minor bump of 2026-10-04, moved off
1.13.0 / vc27 when `main` shipped #141). Land further user-facing fixes in `changelogs/28.txt`
(500-character cap), never by bumping or creating `29.txt`; re-open only for something major, and
say so.

## Decided non-items

Binding owner declines live in `docs/rebuild/DECIDED_NON_ITEMS.md` (moved there by the owner's
grant, DD-037), including the `docs/plans/REVIEW_TRIAGE_1.9.0.md` declines. Read it before
proposing work; changing it is legislation under RUNBOOK's rule-review protocol.

## Changelog

Newest first; ledger rows are the durable detail.

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
