# STATE — project state & session memory

> **Length guard (DA-004).** Thresholds are in `amh.conf`; the compression rules, and the rule for
> what may sit in `Current state` at all, are `docs/PROTOCOLS.md` → **Working-memory compression**,
> and they bind whether or not you follow this pointer. Prose-only — no guard judges it.

## Project

Native Kotlin/Compose rebuild of Tasker `Advanced_Auto_Brightness_V3.3`: `:domain` pure JVM,
`:platform` Android adapters, `:app` Compose/DataStore/FGS. BASIC runs core brightness; ELEVATED
adds super dimming and Privileged Display.

## Current state

Harness AMH 17.0.0, scripts and hand-applied seed prose together (DE-042); the live ledger is
`LEDGER_E.md`. **Release standing is NOT recorded here:** the session banner computes it
(`scripts/session-facts.sh`, DC-030), settled by hand with
`git ls-remote --tags --refs origin 'refs/tags/v*'`.

The tree declares 1.15.3 / vc33; whether that is already tagged, so that the next code unit owes
a bump, is the session banner's call. Never bump twice in one train. Tideo reads and
writes the brightness setting as 0–255 on every device, with no config lookup (DE-023, overruling
DC-026); DE-019 holds the fix if a device is ever shown to read 0–4095. The light-stall findings
H1/H2 are DD-003/DD-002, no round script is alive (RUNBOOK §6, DB-010), the force-stop
investigation stays closed (DB-051…DB-060), and Scorecard.dev is a run-once local input.

## Active work

- **Light-refactor follow-ups (owner, 2026-11-21)** — Tideo defects the refactor's units noted
  and left, one unit each, before the flash plan's Unit 1, with no plan file. Out of scope: the
  runner machine's QEMU test flakes and missing pytest, and D4's base-curve label, which the owner
  dropped as a nit (DE-049 keeps it unlanded).
  [x] 1 dimming chart crash below the minimum · [x] 2 secure dimming write result (DE-052) ·
  [ ] 4 legacy `setupTitle` and the built-in match (DE-047, DD-031).
- **Faster Shizuku paths and Android 17 display APIs** — `docs/plans/PRIVILEGED_FAST_PATHS.md`,
  spec only (owner, 2026-10-19). Unit 1 is the owner's Astra review; Units 2–13 wait until the
  owner's OnePlus 13 runs ColorOS 17, each on the owner's go-ahead. Unit 5 also lets an
  adb-elevated user authorise Shizuku (#157, DE-017).
  [x] 0 plan · [ ] 1 Astra review · [ ] 2 ColorOS 17 baseline · [ ] 3 split `SHIZUKU_USAGE.md` ·
  [ ] 4 helper metrics and bump · [ ] 5 one-tap Shizuku grants · [ ] 6 resident helper ·
  [ ] 7 BrightnessInfo readout · [ ] 8 compileSdk 36.1 · [ ] 9 event-driven readout · [ ] 10 push
  foreground app · [ ] 11 live HDR · [ ] 12 finer steps (optional) · [ ] 13 close-out.
- **Display calibration** — `docs/plans/DISPLAY_CALIBRATION_CEILING.md`, Units 1–4 shipped
  (DE-005, DE-015, DE-027…DE-032); each unit runs on the owner's go-ahead.
  [ ] 5 device checks and close-out: the Pixel calibrates (DE-037); its HBM check and the F4 trace
  stay open and do not block release.
- **HyperOS brightness scale** — `docs/plans/HYPEROS_SETTING_SCALE.md`, issue #157 (DE-014,
  DE-016…DE-018); each unit runs on the owner's go-ahead.
  [x] 0 plan · [x] 1 one scale (DE-023) · [ ] 2 reporter's device checks · [ ] 3 close-out.
- **Minor redesign: remove the AI design tells, keep the good design** —
  `docs/plans/DESIGN_TELLS_REDESIGN.md`, spec only (owner, 2026-11-16). Unit 1 is the owner's
  Astra `-e high` review of every decision; each later unit runs on the owner's go-ahead.
  [x] 0 plan · [ ] 1 Astra review · [ ] 2 Claude Design system and canvas · [ ] 3 design rules ·
  [ ] 4 Dashboard card and bump · [ ] 5 labels and mono · [ ] 6 status · [ ] 7 headers ·
  [ ] 8 copy · [ ] 9 Claude Design catch-up · [ ] 10 device check and close-out.
- **Flash visibility: On, Off (outside Tideo), Off (everywhere), and two bugs (#161)** —
  `docs/plans/FLASH_VISIBILITY_TOGGLE.md`, spec only; D1–D5 decided (DE-040, DE-041); each unit
  runs on the owner's go-ahead, from the Docker host's plan-runner with device work only via
  `e2e/run.sh`. It runs after the follow-ups, its Unit 1 makes the train's one bump (DE-043), and
  the train's one PR waits until this plan is done (owner, 2026-11-21).
  [x] 0 plan · [ ] 1 predictable background flashes and a truthful status · [ ] 2 the three-state
  setting and bump · [ ] 3 §19 steps and e2e scenarios · [ ] 4 phone run · [ ] 5 close-out.

## Owner queue

> **Protected section (D-167).** Never delete it, and never silently drop items during compression
> — a ladder guard warns if the header vanishes. Items leave only when done, answered or triaged;
> then delete the item and record the outcome as a Changelog line or a ledger row. How to test an
> item before restating it, and why every session's final message must:
> `docs/PROTOCOLS.md` → **Session discipline** 7.
>
> **Plain language here — exempt from the tree's terse, ledger-ID-first register (DB-079, owner,
> 2026-08-23)** because a person decides from it: say what to do, on what, what result means it
> worked, and the command that settles it, with ledger IDs last. Keep the Open questions format —
> fork, options, recommendation (D-167), dated (DA-006); credential leaks and external-content
> escalations land here too.

1. **[2026-10-04] Get 89 Chinese strings translated; they show in English until then.** Ask on
   #143 for every key the command below lists: #143's 65 (some since replaced by eight
   `calibration_failed_*` messages, and `handoff_calibration_note_failed` reworded, DE-032), ten
   `debug_night_light_*` labels (DE-038), six relabelled brightness readouts (DE-045) and two PWM
   chart strings (DE-046). Missing keys fall back to English, so it does not block a release
   (owner, 2026-10-08). Paste the replies into the file. Settles it: `comm -23 <(grep -v
   'translatable="false"' app/src/main/res/values/strings.xml | grep -o 'name="[^"]*"' | sort)
   <(grep -o 'name="[^"]*"' app/src/main/res/values-b+zh+Hans/strings.xml | sort) | wc -l` prints
   0 (89 on 2026-11-21).

2. **[2026-10-13] Decide whether to add a check that the output-redaction hook is still wired.**
   Both adapters run `scripts/redact-tool-output.sh` after every tool call (AMH 15.0.0), but
   nothing notices if that `PostToolUse` group is deleted, rewired or loses its bash pin, and a
   missing hook looks exactly like output with no credential (DD-066). Options: (a) a fact check in
   `scripts/guards/doc-facts.sh` with a fixture in `scripts/tests/local-guards.sh`, under rule
   review; (b) leave it prose-only, as the adapter comments disclose. Recommendation (a), as
   upstream checks this in its own repository. Settles it: `grep -c redact-tool-output
   scripts/guards/*.sh` prints a nonzero count, or a Changelog line records (b).

3. **[2026-10-28] Check that F-Droid's rebuild of 1.15.3 matches ours.** It is the first release
   built on `ubuntu-26.04` (DE-009) and with #159's new pins in `release.yml`, which succeeded on
   2026-11-15 with a signed APK and one changelog block. Settles it: 1.15.3 appears on F-Droid
   marked reproducible.

## Decided non-items

Binding owner declines live in `docs/rebuild/DECIDED_NON_ITEMS.md` (moved there by the owner's
grant, DD-037), including the `docs/plans/REVIEW_TRIAGE_1.9.0.md` declines. Read it before
proposing work; changing it is legislation under PROTOCOLS' rule-review protocol.

## Changelog

Newest first; ledger rows are the durable detail.

- 2026-11-21 — **Follow-up 2: a refused Extra Dim write now reads as failed (DE-052).** The
  coordinator's existing failure path (DB-001) then clears Extra Dim and retries next cycle.
- 2026-11-21 — **Follow-up 1: the Super Dimming chart no longer crashes when the draft threshold
  sits below the minimum brightness while Extra Dim is engaged.** The "Now" marker's clamp got an
  empty range there (minimum 25, threshold 4: range 25..15).
- 2026-11-20..21 — **Light refactor done, its plan deleted (DE-043…DE-051): one dimming plan
  publishes the readout, zeroed on every disengage; readouts say "Perceived" or "Hardware
  (written)"; the PWM chart plots the runtime's level; every swap path keeps one global set; small
  formulas have one copy; Extra Dim takes the engine's circadian multiplier; AGENTS.md gains the
  one-source rule.** The owner's OnePlus 13 passed `DEVICE_TEST_SCRIPT.md` §20 on 2026-11-21, and
  step 67's shell check now names the stored 0–4095 scale (DC-025).
- 2026-11-15..19 — **AMH 15.1.0 -> 17.0.0 (DE-042); #161 planned and decided (DE-040, DE-041); the
  design-tells redesign planned; `MATH_EXPLAINED.md` re-checked for v1.15.3, and at every bump from
  now on (DE-039).**
- 2026-10-19..11-14 — **The 1.15.1, 1.15.2 and 1.15.3 trains (DD-088…DD-093, DE-001…DE-038):
  dark-room jitter, calibration Units 1–4, the HyperOS scale's Unit 1, codemap, CI on
  `ubuntu-26.04`, Android 17 QPR2 read, Live Debug's Night Light card.** 1.15.2's screen
  service-off Apply is still unverified on device.
- 2026-06-23..10-18 — **v1.0.0 → 1.15.0 / vc30 (D-096…DD-087); the 1.14.x Night Light plan is
  archived as `docs/history/NIGHT_LIGHT_CIRCADIAN_FIX.md`.**
