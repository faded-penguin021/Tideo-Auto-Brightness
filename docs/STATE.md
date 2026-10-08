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

2. **[2026-10-04] Once this train is on `main`, check the translation badge.** The `Translation
   badges` workflow first runs there. Worked if its Actions run is green and the README's 简体中文
   badge shows a percentage. If its push was refused, let Actions create the `badges` branch
   (Settings → Actions → Workflow permissions, or the blocking ruleset) and rerun it. Settles it:
   `git ls-remote --heads origin badges` prints one line (DD-028).

Open questions: none.

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

- 2026-10-08 — **Release preflight classifies debug-variant sources and the `e2e/` harness as
  non-shipping; its fail-closed step had stopped on DD-052's debug launcher drawable (DD-058).**

- 2026-10-08 — **PR-time Astra review: a scheduled TIME wake no longer cancels its own profile
  write, and a Sunrise/Sunset rule re-arms from the new day's sun times at midnight (DD-056); the
  badge workflow publishes only from `main`; the rest triaged, fixed or accepted (DD-057).**

- 2026-10-08 — **Open question answered (a): only the current owner publishes live runtime state,
  so a destroyed service's late publish cannot show it running again; behind the once-flaky
  `destroy_withoutASuccessor_…` test (DD-055).**

- 2026-10-08 — **Owner ran step 38a on 1.14.0-debug vc28 (57561ec): the extended Night Light range
  passes, including the range-off return to the device minimum (DD-053); the step's no-Shizuku
  expectation is corrected. The User Guide's quote and tip tints turn blue in debug too (DD-054).**

- 2026-10-08 — **Debug builds are dark blue: UI palette, launcher icon and widget; release stays
  teal (DD-052).**

- 2026-10-08 — **Owner approved the low-lux jitter rule as specified; it goes in the train after
  1.14.0, each step on the owner's go-ahead.**

- 2026-10-08 — **Night Light plan closed (N4): archived whole, now frozen, as
  `docs/history/NIGHT_LIGHT_CIRCADIAN_FIX.md` for its AAB `_NightLightAPI` transcript (owner);
  device step 38a stays on the Owner queue.**

- 2026-10-07 — **Owner queue: try step 38a on a device; Open question: archive or delete the Night
  Light plan (its N4).**

- 2026-10-07 — **Night Light N2b done (step 5b): device step 38a and its `s11_38a` row, and
  `changelogs/28.txt` gains the range line, the other lines tightened to fit 500 characters.**

- 2026-10-07 — **Night Light N2b step 5a (rule change): AGENTS.md's third Shizuku place now covers
  the extended range; README points at the new `SHIZUKU_USAGE.md`; DC-057's "only" is corrected
  (DD-051).**

- 2026-10-07 — **Night Light N2b step 4: the "Go beyond temperature limits" row on the Privileged
  Display screen, shown with Shizuku running or root, kept while on; the slider, its hint and the
  circadian help follow the draft flag at once (DD-050).**

- 2026-10-07 — **Night Light N2b step 3: Kelvin writes clamp to the active range at write time —
  686–7308 through the display-service bridge while a profile's extended flag is on, else the
  device range; the ramp's day endpoint is 7308 while it is on, and a stored out-of-range setpoint
  survives the read-back (DD-048, DD-049).**

- 2026-10-07 — **Night Light N2b step 2: the per-profile `extendedNightLightEnabled` field (off by
  default, `%AAB_ExtendedNightLight`), persisted, exported and context-merged; nothing reads it yet.
  A profile saved before it reads it as off, a grant-decided departure from AAB (DD-047).**

- 2026-10-07 — **Owner queue: `claude/device-e2e` and `claude/e2e-s9-closeout` are deleted on
  origin (owner; `git ls-remote --heads` printed nothing), so that item is dropped.**

- 2026-10-07 — **Night Light N2b step 1: every stored setpoint and Kelvin write is railed to
  686–7308, one band for controller, mapper and validator (DD-046). The plan now holds AAB's
  reference, distilled, and the owner's answers.**

- 2026-10-07 — **The parallel `claude/e2e-s9-closeout` branch merged into this train; its row kept
  DD-044, so N2's row is renumbered DD-045 (owner).**

- 2026-10-07 — **Night Light N2: the slider range, the circadian day endpoint and the unset-key
  default come from the device's framework config, AOSP's values only as fallback (DD-045).**

- 2026-10-07 — **E2E suite complete (S9; plan deleted): 17 of 20 auto rows pass on the phone and 3
  skip on their preconditions, private state unchanged; how to run it is RUNBOOK playbook 9, what
  a PASS does not prove is `e2e/README.md` "Known limits", and the script names each section's
  auto rows (DD-044).**

- 2026-10-07 — **E2E S8 dropped by the owner: mobile-use has no device-free mode and its adbutils
  pin cannot be locked beside the suite's, so there is no triage extra (DD-043).**

- 2026-10-06 — **Night Light plan: #142's owner direction added as N2b (686–7308 K behind an
  opt-in toggle); N3 closed as the owner's DD-038 solar-fallback diagnosis. Owner, 2026-10-07:
  N2b waits for AAB's reference (`_NightLightAPI` transcribed into the plan), ramps from 7308 K
  (the top at which every RGB multiplier stays in 0–1),
  is per profile, is visible only with Shizuku or root, and keeps out-of-range setpoints.**

- 2026-10-06 — **Time rules wake only at the next context time, as Tasker's prof764 does; the
  per-pipeline-update evaluation that read Android's last-known location about once a second is
  gone (DD-042).**

- 2026-10-06 — **E2E S7 device runs done: s11_39 passes now the owner's `SUNSET-30` rule is
  deleted, s02_10e passes, and s02_10a passes once its control waits for the owner's unlock
  (DD-041).**

- 2026-10-06 — **Owner queue answered: no false override pause has recurred since 2026-09-23, so
  that item is dropped; the task592/task637 Tasker fixes are done; the owner re-checked the
  `SUNSET-30` rule and it is correct (DD-038). Both E2E open questions are closed
  (DD-040): an open Dashboard need not notice a grant, and wake scenarios stay owner-unlocked.**

- 2026-10-06 — **E2E S7 reruns on vc28 with DD-034: s06_19a passes; s02_10b's failure was the
  harness re-settling after a drift dismissal, fixed and passing; s11_39 awaits a run outside the
  owner's nightly context rule (DD-039).**

- 2026-10-06 — **SUNRISE/SUNSET rule times follow the pinned Circadian location; one shared
  resolver for engine and editor (DD-038).**
- 2026-10-06 — **The owner's device round on 1.14.0-debug vc28 passed all nine checks; the
  solar-offsets plan closed, its webview paste now a source transcript (DD-036). Compression
  pass: Decided non-items moved verbatim to `docs/rebuild/DECIDED_NON_ITEMS.md` (DD-037).**
- 2026-10-05 — **E2E S7 checkpoint: the effect-ordered suites ran on the phone; the harness now
  reads focus, the notification shade and greyed Apply the way OxygenOS 16 shows them (DD-033),
  and two Tideo defects it found are fixed in the app, unverified on device (DD-034).**
- 2026-10-05 — **E2E S6: smoke passes on the owner's phone on 1.14.0-debug vc28, after fixing the
  harness's OxygenOS service-record and chip-label parsing (DD-032).**
- 2026-10-04 — **E2E S5: read-only preflight built and passed on the phone; 1.14.0-debug vc28 is
  its first guarded install (DD-029). Owner: no automation receiver, so all 21 scenarios clear
  the SKIP rules.**
- 2026-10-01..06 — **This train (1.14.0 / vc28 after `main` shipped #141 as v1.13.0): E2E S1–S4
  (DD-015…DD-021), the PWM help (DD-014), the proximity damp (DD-022,
  DD-024), #139 (DD-025), translation policy and coverage badges (DD-023, DD-026…DD-028), the
  owner's first-launch defaults (DD-030, DD-031), AAB's solar offsets (DD-035), #138's actions
  bump.**
- 2026-06-23..09-28 — **v1.0.0 → v1.12.0 (D-096…DD-012); #133 and #134 owner-passed on
  1.12.0-debug vc26.**
