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

The tree declares 1.15.1 / vc31 (the train paragraph under the Owner queue); 1.14.0's device
round passed on 1.14.0-debug vc28 (DD-036), and step 38a's #145 check passed on 1.14.1-debug vc29
with a brief warmer tint before the expected one (DD-065). The 0–4095 conversion
path stays frozen as built (DC-011…DC-013, DC-025…DC-028, DB-083), the light-stall findings H1/H2
are DD-003/DD-002, no round script is alive (RUNBOOK §6, DB-010), the force-stop investigation
stays closed (DB-051…DB-060), and Scorecard.dev is a run-once local input.

## Active work

- **Faster Shizuku paths and Android 17 display APIs** — `docs/plans/PRIVILEGED_FAST_PATHS.md`,
  spec only (owner, 2026-10-19). Unit 1 is the owner's Astra review. Units 2–13 wait until the
  owner's OnePlus 13 runs ColorOS 17, and each unit runs on the owner's go-ahead.
  [x] 0 plan committed · [ ] 1 Astra review · [ ] 2 ColorOS 17 baseline and facts · [ ] 3 split
  `SHIZUKU_USAGE.md` · [ ] 4 helper metrics and version bump · [ ] 5 one-tap Shizuku grants ·
  [ ] 6 resident helper · [ ] 7 BrightnessInfo readout · [ ] 8 compileSdk 36.1 · [ ] 9
  event-driven readout · [ ] 10 push foreground app · [ ] 11 live HDR · [ ] 12 finer steps
  (optional) · [ ] 13 close-out.
- **Night Light switch-on order** — `docs/plans/NIGHT_LIGHT_SWITCH_ON_ORDER.md`, spec only (owner,
  2026-10-20): a switch-on from a Night-Light-off profile flashes the held Kelvin (red) before the
  circadian one lands. Each unit runs on the owner's go-ahead; an `app` comment-budget raise is
  pre-approved for Units 1–2, up to +5 between them, rule review still owed.
  [x] 0 plan committed · [x] 1 coordinator fix, tests, DD-090, 1.15.1 / vc31 · [x] 2 the screen's
  Apply, DD-091 · [x] 3 shared write-order helper, not landed, DD-092 · [ ] 4 owner device check and
  close-out.
- **Light refactor of duplicated logic** — `docs/plans/LIGHT_REFACTOR_CONSOLIDATION.md`, spec only
  (owner, 2026-10-21; D1–D5 decided). Each unit runs on the owner's go-ahead.
  [x] 0 plan · [ ] 1 dimming readout · [ ] 2 brightness labels · [ ] 3 PWM dimming chart ·
  [ ] 4 `keepGlobalsFrom` · [ ] 5 formula dedups · [ ] 6 engine `dimDynamic` · [ ] 7 AGENTS.md rule · [ ] 8 close-out.

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

1. **[2026-10-04] Get 65 Chinese strings translated; they show in English until then.** Four
   changed meaning and lost their old Chinese (DD-026); the rest are new: the rule editor's
   offsets, the extended Night Light range and the bright-light hand-off (DD-035, DD-047, DD-050,
   DD-071…DD-077). #143 asks for all 65; missing keys fall back to English, so it does not block a
   release (owner, 2026-10-08). Paste translations replied there into the file. Settles it: `comm -23 <(grep -v 'translatable="false"' app/src/main/res/values/strings.xml |
   grep -o 'name="[^"]*"' | sort) <(grep -o 'name="[^"]*"'
   app/src/main/res/values-b+zh+Hans/strings.xml | sort) | wc -l` prints 0 (65 on 2026-10-18).

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

**The tree declares `1.15.1` / vc31, this train's ONE bump**, made by the Night Light switch-on
plan's unit 1 after v1.15.0 was seen tagged on 2026-10-25 (`git ls-remote --tags --refs origin
'refs/tags/v*'`). `changelogs/31.txt` (500-character cap) carries only that fix. Never bump twice
in one train.

## Decided non-items

Binding owner declines live in `docs/rebuild/DECIDED_NON_ITEMS.md` (moved there by the owner's
grant, DD-037), including the `docs/plans/REVIEW_TRIAGE_1.9.0.md` declines. Read it before
proposing work; changing it is legislation under RUNBOOK's rule-review protocol.

## Changelog

Newest first; ledger rows are the durable detail.

- 2026-10-25 — **The session banner now fetches and warns when HEAD can fast-forward to any branch,
  bot-only tips excluded, so a checkout behind a worktree's pushed work shows at start (owner).**
- 2026-10-25 — **No shared Night Light write-order helper: each site's order stays two inline
  edges, since a helper came out longer (DD-092).**
- 2026-10-25 — **The Privileged Display screen's service-off Apply now switches Night Light on
  after the setpoint lands and off before it; step 38b gains the case, JVM-tested only (DD-091).**
- 2026-10-25 — **The Night Light switch-on plan gains Unit 3, the shared write-order helper the
  light-refactor plan defers to it, landed only if smaller (owner).**
- 2026-10-25 — **Night Light now switches on after the profile's Kelvin lands, so a switch from a
  Night-Light-off profile no longer flashes the held (red) Kelvin; 1.15.1 / vc31, step 38b, JVM-tested
  only (DD-090).**

- 2026-10-21 — **Duplicated logic audited: Live Debug and the Dashboard show hardware vs perceived
  brightness in PWM mode, both kept (D-109); six consolidations planned in
  `docs/plans/LIGHT_REFACTOR_CONSOLIDATION.md`, spec only.**
- 2026-10-20 — **The red flash on a switch to a circadian profile is write order (DD-065's warmer
  tint); its fix is planned in `docs/plans/NIGHT_LIGHT_SWITCH_ON_ORDER.md`, the screen's
  service-off Apply as its Unit 2 (owner).**
- 2026-10-19 — **Dark-room jitter closed with curve settings, no code (DD-088, DD-089); the
  faster-Shizuku and Android 17 plan committed as `docs/plans/PRIVILEGED_FAST_PATHS.md`, spec
  only.**
- 2026-10-13..18 — **1.15.0 / vc30: the bright-light hand-off (#136) with the HBM readout and display
  calibration, owner-passed in sunlight (DD-070…DD-085); E2E 17/20 on vc30 (DD-086, DD-087); #143
  asks for all 65 strings. CI: a test-only DataStore hang fix (DD-069), runners pinned to
  `ubuntu-24.04` (DD-068), fixture suites only after a failed ladder (DD-067). Harness AMH 14.1.0 →
  15.1.0 (DD-066).**
- 2026-10-01..10 — **1.14.0 / vc28 and 1.14.1 / vc29: E2E S1–S9 (DD-015…DD-044), Night Light's
  device range, extended range and circadian fixes, owner-verified (DD-045…DD-065; plan archived as
  `docs/history/NIGHT_LIGHT_CIRCADIAN_FIX.md`), translations and badges (DD-023…DD-028), the
  owner's first-launch defaults (DD-030, DD-031), AAB's solar offsets (DD-035).**
- 2026-06-23..09-28 — **v1.0.0 → v1.12.0 (D-096…DD-012); #133 and #134 owner-passed on
  1.12.0-debug vc26.**
