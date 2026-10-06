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

- **Real-device E2E suite** — `docs/plans/DEVICE_E2E_PLAN.md` (owner-approved 2026-09-13): S0–S4
  done and the blocking Sol review of S2–S4 fixed (DD-021); S5's read-only preflight is built and
  passed on 1.14.0-debug vc28 (DD-029); no Tasker/MacroDroid profile on the owner's phone acts on
  `STATE_CHANGED` (owner, 2026-10-04), so device runs set `TIDEO_E2E_CONFIRM=automation`; S6's
  smoke set passed there (DD-032); S7 is under way (DD-033, DD-034): 15 of 21 auto scenarios
  pass (13) or skip correctly (2) on the phone; the owner passed DD-034's two app fixes on
  1.14.0-debug vc28 (DD-036), not saying whether e2e ran s06_19a and s11_39; s06_19b is a Tideo
  defect awaiting the owner (Open questions), s02_10b is unclassified (Live Debug's brightness
  never matched the stored value in 15 s), and s02_10a/10e need the phone to wake unlocked. S8–S9
  open. Owed when the partial rows get tests, from Sol on S4a: a dialog-root flag for
  SaveProfileDialog before s14_50, and the native flash overlay has no resource-id (s13_44a).
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

2. **[2026-10-04] Get six Chinese strings translated; they show in English until then.**
   `help_pwm_exponent` (DD-014) and `contexts_only_plugged_in` (DD-025) changed meaning, so their
   old Chinese was removed (DD-026), and the rule editor's four `contexts_offset_*` strings are new
   (DD-035). Ask a fluent speaker, such as #141's translator. Settles it:
   `grep -c -e 'name="help_pwm_exponent"' -e 'name="contexts_only_plugged_in"' -e
   'name="contexts_offset_' app/src/main/res/values-b+zh+Hans/strings.xml` prints 6.

3. **[2026-10-04] Once this train is on `main`, check the translation badge.** The `Translation
   badges` workflow first runs there. Worked if its Actions run is green and the README's 简体中文
   badge shows a percentage. If its push was refused, let Actions create the `badges` branch
   (Settings → Actions → Workflow permissions, or the blocking ruleset) and rerun it. Settles it:
   `git ls-remote --heads origin badges` prints one line (DD-028).

4. **[2026-10-05] Fix two things in Tasker's default profiles.** task592's Outdoors declares
   `JSONObject genOutdoor` twice, so name the reactivity one `reactOutdoor`, and its second
   `min_wait` line stands where `genOutdoor.put("delta_factor", 4.0);` was, so put that back (Tideo
   keeps Outdoors at 4.0). task637's self-healing emergency Default (`performLoad`) still writes
   `thresh_midpoint` 3.0 in the extracted source, so make it 4.0 if yours does too (DD-030, DD-031).
   Nothing in this repository settles it; tell a session when it is done.

5. **[2026-10-06] Re-check your "sunrise test" rule on the next build.** With the location pinned on
   the Circadian screen, the rule editor's Sunset button should show about 19:0x (not blank), and
   the `SUNSET-30` rule should switch on around 18:3x, not at 17:30. Settles it: on the Context
   rules screen the card's gold "Active" tag first appears at that time (DD-038).

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
1.13.0 / vc27 when `main` shipped #141). Land further user-facing fixes in `changelogs/28.txt`
(500-character cap), never by bumping or creating `29.txt`; re-open only for something major, and
say so.

## Decided non-items

Binding owner declines live in `docs/rebuild/DECIDED_NON_ITEMS.md` (moved there by the owner's
grant, DD-037), including the `docs/plans/REVIEW_TRIAGE_1.9.0.md` declines. Read it before
proposing work; changing it is legislation under RUNBOOK's rule-review protocol.

## Changelog

Newest first; ledger rows are the durable detail.

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
