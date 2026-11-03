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
  (owner, 2026-10-21; D1–D5 decided). Each unit runs on the owner's go-ahead.
  [x] 0 plan · [ ] 1 dimming readout · [ ] 2 brightness labels · [ ] 3 PWM dimming chart ·
  [ ] 4 `keepGlobalsFrom` · [ ] 5 formula dedups · [ ] 6 engine `dimDynamic` · [ ] 7 AGENTS.md rule · [ ] 8 close-out.
- **Display calibration (Pixel confirmed, HyperOS provisional)** —
  `docs/plans/DISPLAY_CALIBRATION_CEILING.md`, spec only (owner, 2026-10-26; DE-005, revised
  DE-015). D1 is decided (a), amended; D2–D4 are open (Owner queue 3). It and the HyperOS plan
  are the current stage; each unit runs on the owner's go-ahead.
  [x] 0 plan · [ ] 1 calibration trace · [ ] 2 corroborated ceiling, stored · [ ] 3 readout and
  HBM flag (one release with 2) · [ ] 4 failure causes · [ ] 5 device checks and close-out.
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

1. **[2026-10-04] Get 65 Chinese strings translated; they show in English until then.** #143 asks
   for all 65 — four changed meaning and lost their old Chinese, the rest are new (DD-026, DD-035,
   DD-047, DD-050, DD-071…DD-077); missing keys fall back to English, so it does not block a
   release (owner, 2026-10-08). Paste translations replied there into the file. Settles it: `comm -23 <(grep -v 'translatable="false"' app/src/main/res/values/strings.xml |
   grep -o 'name="[^"]*"' | sort) <(grep -o 'name="[^"]*"'
   app/src/main/res/values-b+zh+Hans/strings.xml | sort) | wc -l` prints 0 (65 on 2026-11-03).

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

3. **[2026-10-26] Decide the calibration plan's three open questions before its Units 2–4.** A
   Pixel's calibration fails because manual brightness stops at 0.63, so the top levels read the
   same, and the failure then wrongly blames DUMP (DE-005); D1 is decided (DE-015). D2: the
   readout in High Brightness Mode (HBM) on such a panel, as (a) AAB's slope, up to ~1150, (b)
   proportional to the ceiling, up to ~405, or (c) proportional on every panel. D3: (a) a message
   per cause, blaming permissions only when they are shown missing, (b) one neutral message, or
   (c) unchanged. D4: a plateau the dump does not confirm (a) fails the run with its own message,
   or (b) is stored without ever setting the HBM flag. Recommendation: D2 (b), D3 (a), D4 (a);
   reasons in the plan. Before Unit 2, also check whether the Pixel and F4 dumps carry
   `brightnessMaxReason` (`grep -c brightnessMaxReason <dump>`); the F4 reporter offers the full
   dump on #157. Unverified until you answer; settles it: `grep -c '^## Owner decisions (open)'
   docs/plans/DISPLAY_CALIBRATION_CEILING.md` prints 0 (retitled once D2–D4 are decided), or the
   plan is gone.

4. **[2026-10-28] Watch the first release on Ubuntu 26.04; no PR can run the release workflows.**
   CI moved to `ubuntu-26.04` (DE-009), but `release.yml` runs only on a tag, so its first run
   there is the next release. Check that it attaches a signed APK, that its changelog block
   appears once, and that F-Droid's rebuild of that release matches ours. Settles it: `gh run list
   --workflow release.yml --limit 1 --json conclusion,createdAt` shows `success` dated after the
   merge, and the version appears on F-Droid as reproducible.

**The tree declares `1.15.3` / vc33, this train's one bump (DE-023)**; 1.15.2 was seen tagged on
2026-11-01 (`git ls-remote --tags --refs origin 'refs/tags/v*'`). Never bump twice in one train.

## Decided non-items

Binding owner declines live in `docs/rebuild/DECIDED_NON_ITEMS.md` (moved there by the owner's
grant, DD-037), including the `docs/plans/REVIEW_TRIAGE_1.9.0.md` declines. Read it before
proposing work; changing it is legislation under RUNBOOK's rule-review protocol.

## Changelog

Newest first; ledger rows are the durable detail.

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
