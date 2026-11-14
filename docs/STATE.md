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

The tree declares 1.15.3 / vc33 (the train paragraph under the Owner queue). Tideo reads and
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

3. **[2026-10-28] Watch the first release on Ubuntu 26.04; no PR can run the release workflows.**
   CI moved to `ubuntu-26.04` (DE-009), but `release.yml` runs only on a tag, so its first run
   there is the next release. If Dependabot's #159 merges first, that run is also the first of its
   new pins in `release.yml`, and `release-signing.yml` runs them only on a dispatch, which you
   can fire on #159's branch (playbook 8 step 5); the same SHAs passed `build` and `fdroid-compat`
   on #159 (2026-11-01). Check that it attaches a signed APK,
   that its changelog block appears once, and that F-Droid's rebuild of that release matches ours. Settles it: `gh run list
   --workflow release.yml --limit 1 --json conclusion,createdAt` shows `success` dated after the
   merge, and the version appears on F-Droid as reproducible.

4. **[2026-11-12] Merge this train, close #157 and release 1.15.3 around Sat 2026-11-14 or Sun
   2026-11-15, unless the F4 reporter answers first.** You said so on #157. The Pixel reporter
   has already confirmed on AdvancedAutoBrightness issue 26 that calibration completes on the vc33
   debug build (2026-11-05). If the F4 reporter sends a calibration log before you release, read it
   first. If calibration failed there for some reason other than the screen simply stopping at its
   normal maximum (about half the panel's range on that phone), have that new cause written up in
   the ledger before you release. This release is also the one item 3 watches. Two reporter checks
   stay open and do not block it. One is the Pixel's High Brightness Mode in direct sun: the
   readout goes above 255, the toast shows once, and `adb shell dumpsys display | grep -E
   'mHbmMode|hbmMax'` shows the mode on. The other is the F4's calibration log. When this item
   leaves, move those two checks into the calibration plan's Unit 5, where they already sit.
   The train also carries Live Debug's Night Light card (DE-038), which `33.txt` names.
   Settles it: `gh pr list --head ccr-b1d50b4f-nyhwmv --state merged` lists this train's PR,
   `gh issue view 157 --json state` prints `CLOSED`, and `gh release view v1.15.3` succeeds (DE-037).

**The tree declares `1.15.3` / vc33, this train's one bump (DE-023)**; 1.15.2 was seen tagged on
2026-11-01 (`git ls-remote --tags --refs origin 'refs/tags/v*'`). Never bump twice in one train.

## Decided non-items

Binding owner declines live in `docs/rebuild/DECIDED_NON_ITEMS.md` (moved there by the owner's
grant, DD-037), including the `docs/plans/REVIEW_TRIAGE_1.9.0.md` declines. Read it before
proposing work; changing it is legislation under RUNBOOK's rule-review protocol.

## Changelog

Newest first; ledger rows are the durable detail.

- 2026-11-14 — **Live Debug gains AAB's Night Light card: status, circadian adjustment, and the
  setpoint or the Kelvin the ramp last landed; the coordinator publishes the landed Kelvin, and
  the card says when Tideo is not applying it (DE-038, owner). Owner queue item 1 is now
  81 strings.**

- 2026-11-13 — **The Pixel reporter confirmed calibration completes on the vc33 debug build
  (AdvancedAutoBrightness issue 26; HBM still unseen). The owner will merge, close #157 and
  release around 2026-11-14/15 if the F4 reporter has not replied by then. Owner queue item 4 (DE-037).**

- 2026-11-11 — **Light-refactor plan re-read against the tree and corrected, spec only: its
  conversion freeze is gone, Unit 5 reuses `HandoffRules.effectiveThreshold`, Unit 4 is a
  behaviour change with its own row, and Unit 1's coordinator publishes the dimming readout
  (DE-036, owner).**

- 2026-11-11 — **Android 17 QPR2 (API 37.2) read against the tree: no breakage, nothing on
  ColorOS 17. The faster-Shizuku plan gains its 37.2 facts (Units 2, 7, 8, 12), and the
  calibration plan's Pixel check asks for `sdk_full`. The Quick Settings slider during a window
  override is DE-033, colour correction and edge effects DE-034, and DE-020 is corrected by DE-035.**

- 2026-11-04 — **Calibration Unit 4: a failed calibration stores `display_calibration_error` and
  shows one message per cause on the card and in the flash, with DUMP advice only for
  `NO_ACCESS`; battery saver refuses the run before any write (DE-032, DE-028). Owner queue item 1
  is now 71 strings.**

- 2026-11-04 — **Calibration Unit 3: with a stored boundary the hand-off's HBM flag follows raw
  above it on every read, so a crossing that still rounds to 255 turns it on; no readout formula
  change (D2 (a), DE-031). Units 2 and 3 are now in one train for one release.**
- 2026-11-04 — **Calibration Unit 2: a plateau at level 128 or above is confirmed at 255 and stored
  as `255:<plateau>` with `display_calibration_hbm` only when the dump's transition point matches;
  an uncorroborated one fails (DE-030). Owner queue item 3, the Pixel-dump grep, is closed: the rule
  checks `brightnessMaxReason` only where printed. Unit 3 must ship in the same release.**
- 2026-11-04 — **Calibration Unit 1: the sweep logs each level's reads, sources, statuses and
  times, the dump's ceiling lines and a summary under logcat tag `TideoCalibration`; behaviour
  unchanged, Shizuku helper version 5 (DE-029).**
- 2026-11-04 — **Calibration refuses to start under battery saver, saying why; Unit 4 carries it
  as the `BATTERY_SAVER` cause, and Owner queue item 3 now holds only the Pixel-dump grep (DE-028,
  owner).**
- 2026-11-04 — **Calibration D2 (a), D3 (a), D4 (a) decided; the F4 is a confirmed ceiling case
  from the reporter's #157 sweep, and battery saver's 0.9 factor is a new Owner queue item 3
  (DE-027, owner).**
- 2026-11-03 — **Playbook 8 gains step 5, rule-reviewed: name the bumped lines no PR runs
  automatically, and route a pin no PR job carries to the Owner queue with its check (DE-026,
  owner); on #159 its three release-workflow SHAs ran in `build`'s steps, and it bumps neither pin
  step 5 lists.**
- 2026-11-03 — **Playbook 8 on Dependabot #159, six github-actions bumps in one grouped PR: each
  pin is its tag's commit, resolved by hand; no old SHA is left and `action-pins.sh` passes on its
  tree; every new pin still declares `node24`, so `build.yml`'s policy block needs no edit; its CI
  was green on 2026-11-01, `fdroid-compat` included (the Owner queue's Ubuntu 26.04 release item).**
- 2026-11-03 — **STATE compressed by stage; `Current state` corrected to DE-023's 0–255 scale,
  and DC-026 gains its pointer to DE-023.**
- 2026-10-31..11-02 — **HyperOS scale planned and decided from a POCO F4 reporter's fork and #157
  (DE-014…DE-020); Unit 1, 1.15.3 / vc33, the setting 0–255 everywhere with the reporter's commit
  first under their name (DE-023); Unit 2 trimmed to the reporter's checks (DE-024); E2E on vc33
  found no Tideo defect, s02_10b open on moving light, the wake pair not run (DE-025).**
- 2026-10-29..11-01 — **Codemap planned, built and closed (DE-010…DE-013, DE-021, DE-022).**
- 2026-10-26..28 — **Calibration planned (DE-005…DE-008); CI on `ubuntu-26.04` (DE-009).**
- 2026-10-19..26 — **Dark-room jitter closed with curve settings (DD-088, DD-089); 1.15.1 / vc31
  and 1.15.2 / vc32 owner-passed, except the screen's service-off Apply, still unverified on
  device (DD-090…DD-093, DE-001…DE-004); faster-Shizuku and light-refactor plans committed.**
- 2026-06-23..10-18 — **v1.0.0 → 1.15.0 / vc30 (D-096…DD-087); the 1.14.x Night Light plan is
  archived as `docs/history/NIGHT_LIGHT_CIRCADIAN_FIX.md`.**
