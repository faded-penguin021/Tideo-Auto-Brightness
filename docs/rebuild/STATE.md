# STATE — cross-session memory for the rebuild program

Every session: read this first; append/update before your final commit. Keep entries terse and
factual. This file is the ONLY shared memory between sessions — if it isn't written here, the
next session does not know it.

## Segment log

| Segment | Date | Model | Status | Commit | Notes |
|---|---|---|---|---|---|
| S0 scaffolding | 2026-06-11 | Fable (planning session) | DONE | (this commit) | CLAUDE.md, RUNBOOK, recipes, checklist, SDK script, hook authored. No source/gradle changes. |
| S1 extraction A | 2026-06-11 | Opus/high | DONE | (this commit) | 40 Java blocks decoded; 28 task docs; profiles.md (753–761,769); pipeline_spec.md; defaults_audit.md (125 vars); INDEX.md. Docs-only. Java "Extracted" checklist cells flipped. |

Status values: DONE · PARTIAL · BLOCKED (see failure protocol in CLAUDE.md).

## Current state

S1 DONE. `docs/rebuild/extraction/` now holds the verbatim ground truth for the core pipeline:
40 decoded Java blocks (`java/`), 28 action-by-action task transcriptions (`tasks/`), `profiles.md`
(pipeline profiles + gates), `pipeline_spec.md` (end-to-end flow + state-var table), `defaults_audit.md`
(all 125 vars classified), `INDEX.md` (inventory + action-code legend + unresolved + 3 spot-checks).
Later pipeline segments should read these, not the XML. Build still does not CONFIGURE (D-007; S3).
Repo also has the Tasker XML, audited Codex conversion (salvage/discard per ledger), and docs/migration/.

## Next up

- S2 (Opus, medium) — extraction B: features/contexts/scenes. No preconditions besides S0.
  S1 deliberately left S2's surface untouched (contexts 762–768/8, task90/38/43, scenes, screen_map).
- S3 (Sonnet, medium) — toolchain modernization + first green build. No preconditions besides S0.
- S1 ∥ S2 ∥ S3 are parallel-safe (disjoint files; rebase before push if raced).

## Deviations & discoveries ledger

Seeded by the S0 audit (details in CLAUDE.md "Facts & corrections ledger"):

- D-001: Java blocks are action code 474 (40), not 598. (Affects S1.)
- D-002: task661 has no Java; curve math is in Variable Set expressions. task663's Java is a
  plot-side copy for cross-validation only. (Affects S1, S4.)
- D-003: Profile-level ConditionLists carry pipeline gating (prof760: absolute thresholds,
  sensor-accuracy trust, %AAB_MainLoop). (Affects S1, S9.)
- D-004: AabSettings schema gaps: %AAB_AnimSteps, %AAB_MaxSteps, %AAB_ThreshMidpoint;
  AnimationConfig defaults conflict with AabSettings. (Affects S1 defaults_audit, S8.)
- D-005: :platform is currently kotlin("jvm"); becomes com.android.library in S3.
- D-006: 125 distinct %AAB_* variables (not 122 as older docs say).
- D-007: settings.gradle.kts has NO pluginManagement block → AGP unresolvable → gradle
  CONFIGURATION fails for every target, even pure-JVM `:domain:test` (verified 2026-06-11 with
  Gradle 8.14.3: "Plugin com.android.application 8.5.2 was not found"). The Codex build was
  never runnable. S3 must add `pluginManagement { repositories { google(); mavenCentral();
  gradlePluginPortal() } }` (or migrate plugin decls accordingly). Until S3: no gradle target
  works at all. (Affects S3; S4 is safe — it depends on S3.)

- D-008: D-004 RESOLVED (S1 `defaults_audit.md`). Canonical from task570: `AAB_AnimSteps=20`,
  `MinWait=25`, `MaxWait=65`, `Throttle=AnimSteps*MaxWait+10=1310`, `ThreshMidpoint=log10(Zone2End)=4`.
  `AAB_MaxSteps` is in the 125-var census but NEVER assigned a default → legacy/unused, do not invent
  one. The salvaged `AnimationConfig` defaults (50/5/30) are WRONG; use 20/25/65. Settings missing from
  `AabSettings.kt`: `AnimSteps`, `ContextOverride`, `SetupTitle` (+ derived `ThreshMidpoint`). Split:
  38 SETTING / 4 DERIVED (form2A/2D/3A, ThreshMidpoint) / 83 RUNTIME. (Affects S5, S8.)
- D-009: prof760 + prof758 multi-clause gates use Tasker `And2`/`Or2` sub-grouping; exact
  parenthesization (and polarity of `%AAB_MainLoop != On`) is UNRESOLVED — literal sequences captured
  in `extraction/profiles.md`, best-effort reading flagged. Validate against runtime in S9. `ThreshAbsLow/High`
  (prof760 abs gate) are written by task546 _Set Thresholds_, NOT task570. (Affects S9; informs S4.)
- D-010: Engine vs Tasker micro-divergences found in S1 spot-check (code left untouched, for S4/S5):
  (a) `BrightnessEngine.luxSmoothing` clamps `luxAlpha.coerceIn(0,1)` — task535 does NOT clamp;
  (b) `mapLuxToBrightness` wraps `^0.33` bases in `.coerceAtLeast(0.0)` — task661 does NOT. Dynamic-threshold
  sigmoid+dark branch is an EXACT match. (Affects S4 golden boundary rows, S5.)
- D-011: BRANCH DISCREPANCY. CLAUDE.md/RUNBOOK say work on `claude/modest-bohr-5ybl2i`, but this
  session's checked-out branch and push target is `claude/practical-mayer-kaopcf` (per session directive).
  S1 committed/pushed to `claude/practical-mayer-kaopcf`. Future sessions: confirm the intended branch;
  if the canonical branch is modest-bohr, these S1 docs may need to be merged/cherry-picked over.

Append new entries as D-008, D-009, … with which segments they affect.

## Blockers

(none)

## Gate findings

### Gate 1 (after S9) — pending
### Gate 2 (after S12) — pending
### Gate 3 (after S14) — pending

The human records on-device findings here; the next session triages them.
