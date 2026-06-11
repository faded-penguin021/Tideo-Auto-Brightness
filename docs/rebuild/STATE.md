# STATE — cross-session memory for the rebuild program

Every session: read this first; append/update before your final commit. Keep entries terse and
factual. This file is the ONLY shared memory between sessions — if it isn't written here, the
next session does not know it.

## Segment log

| Segment | Date | Model | Status | Commit | Notes |
|---|---|---|---|---|---|
| S0 scaffolding | 2026-06-11 | Fable (planning session) | DONE | (this commit) | CLAUDE.md, RUNBOOK, recipes, checklist, SDK script, hook authored. No source/gradle changes. |

Status values: DONE · PARTIAL · BLOCKED (see failure protocol in CLAUDE.md).

## Current state

Repo contains the Tasker XML (ground truth), the audited Codex conversion (salvage/discard per
CLAUDE.md ledger), Codex-era reference docs in docs/migration/, and the rebuild program
scaffolding in docs/rebuild/. No segment work has started. The build does not even CONFIGURE
yet (D-007: missing pluginManagement; plus missing res/, no wrapper) — first green gradle
output of any kind is S3's deliverable. Android SDK bootstrap script verified working (15 s
on this container; idempotent re-run verified).

## Next up

- S1 (Opus, high) — extraction A: pipeline + 40 Java blocks. No preconditions besides S0.
- S2 (Opus, medium) — extraction B: features/contexts/scenes. No preconditions besides S0.
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

Append new entries as D-007, D-008, … with which segments they affect.

## Blockers

(none)

## Gate findings

### Gate 1 (after S9) — pending
### Gate 2 (after S12) — pending
### Gate 3 (after S14) — pending

The human records on-device findings here; the next session triages them.
