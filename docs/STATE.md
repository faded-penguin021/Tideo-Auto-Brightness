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

The tree declares 1.15.2 / vc32 (the train paragraph under the Owner queue); 1.14.0's device
round passed on 1.14.0-debug vc28 (DD-036), and step 38a's #145 check passed on 1.14.1-debug vc29
with a brief warmer tint before the expected one (DD-065); DE-001's held Shizuku helper passed on
1.15.2-debug vc32 (DE-003). The 0–4095 conversion
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
- **Light refactor of duplicated logic** — `docs/plans/LIGHT_REFACTOR_CONSOLIDATION.md`, spec only
  (owner, 2026-10-21; D1–D5 decided). Each unit runs on the owner's go-ahead.
  [x] 0 plan · [ ] 1 dimming readout · [ ] 2 brightness labels · [ ] 3 PWM dimming chart ·
  [ ] 4 `keepGlobalsFrom` · [ ] 5 formula dedups · [ ] 6 engine `dimDynamic` · [ ] 7 AGENTS.md rule · [ ] 8 close-out.
- **Pixel display calibration** — `docs/plans/DISPLAY_CALIBRATION_CEILING.md`, spec only (owner,
  2026-10-26; DE-005, DE-006). D1 is decided (a); D2–D3 are open (Owner queue 3), and each unit
  runs on the owner's go-ahead.
  [x] 0 plan · [ ] 1 sweep accepts a ceiling · [ ] 2 readout above a ceiling · [ ] 3 failure
  causes · [ ] 4 device checks and close-out.
- **Codemap, a generated feature map for agents** — `docs/plans/CODEMAP.md`, spec only (owner,
  2026-10-29; D1–D4 open, Owner queue 5). Each unit runs on the owner's go-ahead.
  [x] 0 plan · [x] 1 Astra review · [ ] 2 placement, tokenizer, declarations · [ ] 3 references ·
  [ ] 4 features, queries, write and check · [ ] 5 seed and measure · [ ] 6 guard (per D2) ·
  [ ] 7 agent entry points · [ ] 8 probe and close-out.

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

3. **[2026-10-26] Decide the Pixel calibration plan's two remaining questions before its Units 2
   and 3.** A Pixel's calibration fails because manual brightness stops at 0.63, so the top
   levels read the same, and the failure then wrongly blames DUMP (DE-005). You decided D1 (a):
   accept that ceiling, since the value above which High Brightness Mode (HBM) is on is what
   matters. D2: the readout in HBM on such a panel, as (a) AAB's slope, up to ~1150,
   (b) proportional to the ceiling, up to ~405, or (c) proportional on every panel. D3: (a) a
   message per cause, (b) one neutral message, or (c) unchanged. Recommendation: D2 (b), D3 (a);
   reasons in the plan's Owner decisions. Unverified until you answer; settles it:
   `grep -c '^## Owner decisions (open)' docs/plans/DISPLAY_CALIBRATION_CEILING.md` prints 0
   (retitled once D2 and D3 are decided), or the plan is gone.

4. **[2026-10-28] Watch the first release on Ubuntu 26.04; no PR can run the release workflows.**
   CI moved to `ubuntu-26.04` (DE-009), but `release.yml` runs only on a tag, so its first run
   there is the next release. Check that it attaches a signed APK, that its changelog block
   appears once, and that F-Droid's rebuild of that release matches ours. Settles it: `gh run list
   --workflow release.yml --limit 1 --json conclusion,createdAt` shows `success` dated after the
   merge, and the version appears on F-Droid as reproducible.

5. **[2026-10-29] Decide the codemap plan's four questions; D2 and D3 can wait for its Unit 5
   numbers.** D1: who regenerates the map, (a) the author with CI only checking, or (b) a CI job
   that commits. D2: (a) commit the generated map, or (b) only the anchors, checked live. D3: the
   keyword heuristic as (a) a map section, (b) a query only, or (c) dropped. D4: (a) the plan adds
   its own AGENTS.md sentence now, or (b) folds it into the light-refactor plan's Unit 7.
   Recommendation: D1 (a), D2 (a) unless Unit 5's replay shows the map changing in most
   snapshots, D3 (b), D4 (a); Astra recommended the same, and reasons are in the plan's Owner
   decisions (DE-010). Unverified until you answer; settles it: `grep -c '^## Owner decisions
   (open)' docs/plans/CODEMAP.md` prints 0 (retitled once all four are decided), or the plan is
   gone.

**The tree declares `1.15.2` / vc32, this train's one bump (DE-001)**; 1.15.1 was seen tagged on
2026-10-26 (`git ls-remote --tags --refs origin 'refs/tags/v*'`). Never bump twice in one train.

## Decided non-items

Binding owner declines live in `docs/rebuild/DECIDED_NON_ITEMS.md` (moved there by the owner's
grant, DD-037), including the `docs/plans/REVIEW_TRIAGE_1.9.0.md` declines. Read it before
proposing work; changing it is legislation under RUNBOOK's rule-review protocol.

## Changelog

Newest first; ledger rows are the durable detail.

- 2026-10-29 — **Codemap plan revised after Astra High's review: all nine findings accepted. The
  scanner reports lexical candidates, finds declarations by nesting across all brackets, and
  lands its `RULE_FILES` and split-table placement with its first code; D1–D4 to the Owner queue
  (DE-010, owner). A ChatGPT second pass found no blockers; Unit 5 now measures shared-settings
  coupling and Unit 3's real-tree corpus covers every reference construct (DE-011).**
- 2026-10-29 — **Planned a generated feature map so an agent finds a feature's existing owners
  before writing new logic: hand-kept anchors, a stdlib scanner that expands them from the code,
  live queries and a fail-closed freshness guard. DD-038's second copy of the solar math is the
  motivating incident. Spec only; Unit 1 is the owner's Astra High review (owner).**
- 2026-10-28 — **CI moves to `ubuntu-26.04`: every workflow's runner label, still an exact image,
  never `ubuntu-latest`; accepted on full CI green on its PR, and the tag, dispatch and
  `main`-only workflows first run there after the merge (DE-009, owner's queue answer (a)).**
- 2026-10-28 — **The owner's sunlight probe settles the calibration plan's `get-brightness`
  input: on the OnePlus, handed off above 65 000 lx, `get-brightness`, the setting and
  BrightnessInfo stop at 4095 while the panel passes it, so `dumpsys display` stays the readout's
  source and that plan gains no unit; faster-Shizuku Unit 7 now waits on a sunlight re-check
  under ColorOS 17 (DE-007, DE-008, owner).**
- 2026-10-26 — **Pixel calibration plan: D1 decided (a), the HBM threshold being what matters;
  AdvancedAutoBrightness issue 26's dark-end gap measured on the same Pixel, setting 2 already at
  14 % of the slider, and routed to the faster-Shizuku plan's float-write unit (DE-006); the
  owner's `cmd display get-brightness` sunlight probe and how to read it added (owner).**
- 2026-10-26 — **Planned the Pixel display calibration fix: a reporter's dump shows the reads
  worked and the sweep stalled at the 0.63 manual ceiling, then blamed DUMP (DE-005); the plan
  accepts a ceiling at the top, reads HBM above it in proportion and names each failure's cause.
  Spec only; D1–D3 open (owner).**
- 2026-10-26 — **Shizuku helper reuse, planned, revised after Sol's review and closed: the helper
  stays bound, so a return from a Night-Light-off profile no longer shows 4082 K for ~5 s
  (1.15.2 / vc32, DE-001); its liveness check is a local read (Astra, DE-004); the owner passed it
  on 1.15.2-debug vc32 (DE-003). 1.15.1's fix had missed because only the debug install was
  checked, so device checks now give it the release install's learned facts first (DE-002,
  owner); plan deleted.**
- 2026-10-25 — **The session banner now fetches and warns when HEAD can fast-forward to any branch,
  bot-only tips excluded, so a checkout behind a worktree's pushed work shows at start (owner).**
- 2026-10-20..25 — **Night Light switch-on order, planned and closed: Night Light switches on
  after the profile's Kelvin lands, so a switch from a Night-Light-off profile no longer flashes
  the held (red) Kelvin, both on a context switch (1.15.1 / vc31, DD-090) and in the Privileged
  Display screen's service-off Apply (DD-091); no shared write-order helper, since one came out
  longer (DD-092). The owner passed the context-switch case on 1.15.1-debug vc31; the screen's
  Apply stays on-device unverified (DD-093).**

- 2026-10-21 — **Duplicated logic audited: Live Debug and the Dashboard show hardware vs perceived
  brightness in PWM mode, both kept (D-109); six consolidations planned in
  `docs/plans/LIGHT_REFACTOR_CONSOLIDATION.md`, spec only.**
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
