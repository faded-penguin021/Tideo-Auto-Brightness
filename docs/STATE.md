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

- **Faster Shizuku paths and Android 17 display APIs** — `docs/plans/PRIVILEGED_FAST_PATHS.md`,
  spec only (owner, 2026-10-19). Unit 1 is the owner's Astra review. Units 2–13 wait until the
  owner's OnePlus 13 runs ColorOS 17, and each unit runs on the owner's go-ahead. Unit 5 also
  lets an adb-elevated user authorise Shizuku (#157, DE-017).
  [x] 0 plan committed · [ ] 1 Astra review · [ ] 2 ColorOS 17 baseline and facts · [ ] 3 split
  `SHIZUKU_USAGE.md` · [ ] 4 helper metrics and version bump · [ ] 5 one-tap Shizuku grants ·
  [ ] 6 resident helper · [ ] 7 BrightnessInfo readout · [ ] 8 compileSdk 36.1 · [ ] 9
  event-driven readout · [ ] 10 push foreground app · [ ] 11 live HDR · [ ] 12 finer steps
  (optional) · [ ] 13 close-out.
- **Light refactor of duplicated logic** — `docs/plans/LIGHT_REFACTOR_CONSOLIDATION.md`, spec only
  (owner, 2026-10-21; D1–D5 decided; re-read and corrected 2026-11-11, DE-036). Each unit runs on
  the owner's go-ahead.
  [x] 0 plan · [ ] 1 dimming readout · [ ] 2 brightness labels · [ ] 3 PWM dimming chart ·
  [ ] 4 `keepGlobalsFrom` · [ ] 5 formula dedups · [ ] 6 engine `dimDynamic` · [ ] 7 AGENTS.md rule · [ ] 8 close-out.
- **Display calibration (Pixel and HyperOS confirmed)** —
  `docs/plans/DISPLAY_CALIBRATION_CEILING.md`, Units 1–4 in code (owner, 2026-10-26; DE-005, revised
  DE-015). D1 (a), amended; D2 (a), D3 (a), D4 (a) (DE-027); battery saver refuses a run
  (DE-028). It and the HyperOS plan are the current stage; each unit runs on the owner's go-ahead.
  [x] 0 plan · [x] 1 calibration trace (DE-029) · [x] 2 corroborated ceiling, stored (DE-030) · [x] 3 readout and
  HBM flag (DE-031) · [x] 4 failure causes (DE-032) · [ ] 5 device checks and close-out (the Pixel
  calibrates, DE-037; the Pixel's HBM check and the F4 trace stay open and do not block release).
- **HyperOS brightness scale** — `docs/plans/HYPEROS_SETTING_SCALE.md` (owner, 2026-10-31;
  DE-014, revised DE-016); issue #157 is the report (DE-017). D1 (a) and D2 (b) decided (DE-018);
  each unit runs on the owner's go-ahead.
  [x] 0 plan · [x] 1 one scale (DE-023) · [ ] 2 reporter's device checks · [ ] 3 close-out.
- **Minor redesign: remove the AI design tells, keep the good design** —
  `docs/plans/DESIGN_TELLS_REDESIGN.md`, spec only (owner, 2026-11-16; README out of scope; labels
  keep i18n and a11y; Claude Design for review). Unit 1 is the owner's Astra `-e high` review of
  every decision; each later unit runs on the owner's go-ahead.
  [x] 0 plan · [ ] 1 Astra review · [ ] 2 Claude Design system and canvas · [ ] 3 design rules ·
  [ ] 4 Dashboard card and bump · [ ] 5 labels and mono · [ ] 6 status · [ ] 7 headers ·
  [ ] 8 copy · [ ] 9 Claude Design catch-up · [ ] 10 device check and close-out.

- **Flash visibility: On, Off (outside Tideo), Off (everywhere), and two bugs (#161)** —
  `docs/plans/FLASH_VISIBILITY_TOGGLE.md`, spec only (owner, 2026-11-17; AAB-first waived on #161,
  DE-040). D1–D5 decided (DE-041); each unit runs on the owner's go-ahead.
  Runs from the Docker host's plan-runner, phone on wireless adb, device work only via `e2e/run.sh`.
  [x] 0 plan · [ ] 1 predictable background flashes and a truthful status · [ ] 2 the three-state
  setting and bump · [ ] 3 §19 steps and e2e scenarios · [ ] 4 phone run · [ ] 5 close-out.

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

1. **[2026-10-04] Get 81 Chinese strings translated; they show in English until then.** #143 asks
   for 65 — four changed meaning and lost their old Chinese, the rest are new (DD-026, DD-035,
   DD-047, DD-050, DD-071…DD-077); since then two of those were replaced by eight
   `calibration_failed_*` messages, and `handoff_calibration_note_failed` was reworded (DE-032), and
   Live Debug's Night Light card added ten `debug_night_light_*` labels (DE-038), so #143 needs
   those added. Missing keys fall back to English, so it does not block a
   release (owner, 2026-10-08). Paste translations replied there into the file. Settles it: `comm -23 <(grep -v 'translatable="false"' app/src/main/res/values/strings.xml |
   grep -o 'name="[^"]*"' | sort) <(grep -o 'name="[^"]*"'
   app/src/main/res/values-b+zh+Hans/strings.xml | sort) | wc -l` prints 0 (81 on 2026-11-14).

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

3. **[2026-10-28] Check that F-Droid's rebuild of 1.15.3 matches ours.** It is the first release
   built on `ubuntu-26.04` (DE-009) and with #159's new pins in `release.yml`; the GitHub side
   already passed (the 2026-11-16 Changelog line). Settles it: 1.15.3 appears on F-Droid marked
   reproducible.

## Decided non-items

Binding owner declines live in `docs/rebuild/DECIDED_NON_ITEMS.md` (moved there by the owner's
grant, DD-037), including the `docs/plans/REVIEW_TRIAGE_1.9.0.md` declines. Read it before
proposing work; changing it is legislation under RUNBOOK's rule-review protocol.

## Changelog

Newest first; ledger rows are the durable detail.

- 2026-11-17 — **#161's D1–D5 decided (owner, DE-041): flashes stay on over other apps by default,
  and Live Debug adds Off (outside Tideo) and Off (everywhere), each governing every flash; the
  context-load flash becomes a translatable string. The plan's Units 2 and 3 are rewritten for it.
  The plan runs from the Docker host's plan-runner with the phone on wireless adb (owner), so
  device work moves to e2e scenarios s19_62–66 (Unit 3) and a playbook-9 run (Unit 4).**
- 2026-11-17 — **Issue #161 planned, spec only (owner): a toggle for flashes over other apps, and
  two bugs found on the way — a stopped activity swallows background flashes, and Live Debug's
  Accessibility status can read Off while the service is on; D1–D5 open (DE-040).**

- 2026-11-16 — **`MATH_EXPLAINED.md` re-checked against v1.15.3 (no `domain/` change since
  v1.15.0): §1's setting scale (DE-023), §10's calibration ceiling, HBM flag, requirements and
  per-cause failures (DE-028, DE-030…DE-032), §15 links re-pinned. Every version bump now re-checks
  the guide: RUNBOOK playbook 6 and the PR template's Release impact (DE-039; prose-only, no guard;
  rule review by a fresh-context Fable 5.2, four findings, all applied).**
- 2026-11-16 — **Design-tells redesign planned, spec only (owner): each catalogue match judged
  remove, fix or keep, good design kept; D1–D10 open for the Astra review. STATE compressed by stage:
  the 1.15.3 train is folded below. Owner queue: the release item closed (PR #160 merged on
  2026-11-15, #157 closed, v1.15.3 published; its two reporter checks sit in the calibration
  plan's Unit 5), and the Ubuntu 26.04 item narrowed to F-Droid (`release.yml` succeeded on
  2026-11-15 with a signed APK and one changelog block).**
- 2026-10-31..11-14 — **The 1.15.3 / vc33 train: HyperOS scale planned and Unit 1 shipped, the
  setting 0–255 everywhere (DE-014…DE-025); calibration D2–D4 and battery-saver refusal decided,
  Units 1–4 shipped (DE-027…DE-032), the Pixel confirmed (DE-037); playbook 8 on #159 and its
  step 5 (DE-026); Android 17 QPR2 read (DE-033…DE-035); light-refactor plan corrected (DE-036);
  Live Debug's Night Light card (DE-038).**
- 2026-10-29..11-01 — **Codemap planned, built and closed (DE-010…DE-013, DE-021, DE-022).**
- 2026-10-26..28 — **Calibration planned (DE-005…DE-008); CI on `ubuntu-26.04` (DE-009).**
- 2026-10-19..26 — **Dark-room jitter closed with curve settings (DD-088, DD-089); 1.15.1 / vc31
  and 1.15.2 / vc32 owner-passed, except the screen's service-off Apply, still unverified on
  device (DD-090…DD-093, DE-001…DE-004); faster-Shizuku and light-refactor plans committed.**
- 2026-06-23..10-18 — **v1.0.0 → 1.15.0 / vc30 (D-096…DD-087); the 1.14.x Night Light plan is
  archived as `docs/history/NIGHT_LIGHT_CIRCADIAN_FIX.md`.**
