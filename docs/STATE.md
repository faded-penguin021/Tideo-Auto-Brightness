# STATE — project state & session memory

> **Length guard (DA-004 hysteresis — read before editing).** Grow freely to **14 KB**; no
> trimming below that line. When the guard warns, run ONE deep compression pass to **≤ 9 KB** —
> never trim to just under the threshold, because a micro-trim re-arms the warning a session
> later and the wide band IS the debounce. That floor is a **ceiling, not a target**: if the
> pass lands short, fold MORE completed stages rather than micro-trimming toward it. Fail above
> **16 KB**. Compression means: collapse each completed stage into one Changelog line, fold
> changelog clusters, move durable gotchas to the append-only ledger, delete narrative prose.
> **Project**, **Current state**, **Decided non-items** and **Changelog** must always survive —
> those four are `STATE_REQUIRED_SECTIONS` and the guard FAILS on a missing or empty one.
> **Owner queue** is protected separately at WARN level: never delete it, and never silently
> drop items during compression — they are the owner's to close, so compress their prose
> rather than dropping an open item.
>
> `scripts/ladder.sh` machine-checks the band, the required sections, and that a pass actually lands on the floor
> rather than just clearing the warning; above the cap it tells a compression pass from an
> ordinary edit by how much the file shrank (`STATE_EDIT_DELTA_BYTES` in `amh.conf`), so fixing
> a typo up here does not oblige you to compress the whole file. All four numbers live in
> `amh.conf` — keep this paragraph in lockstep with them.

## Project

Native Kotlin/Compose rebuild of Tasker `Advanced_Auto_Brightness_V3.3`: `:domain` pure-JVM
math/decisions, `:platform` Android adapters, `:app` Compose/DataStore/FGS UI/runtime. BASIC
`WRITE_SETTINGS` provides the core pipeline; ELEVATED `WRITE_SECURE_SETTINGS` adds super dimming
and Privileged Display. Maintenance runs on the **Agentic Maintenance Harness (AMH 3.0.0)** —
`AGENTS.md` + this file + `docs/RUNBOOK.md` + `docs/LEDGER*.md` + `scripts/ladder.sh`.

## Current state

**Harness: AMH 3.0.0, converged and green (DB-014…DB-016).** The maintenance harness this repo
originated was spun out as [AMH](https://github.com/faded-penguin021/AMH), diverged, and has now
been replaced by upstream's. **The five scripts under `scripts/` named in
`scripts/MANIFEST.sha256` are upstream's byte-for-byte and hash-checked every run — never edit
one;** changes go to `amh.conf`, `scripts/guards/*.sh` or `scripts/verify.sh`. The constitution
is `AGENTS.md` (`CLAUDE.md` points at it; rows written before 2026-08-03 cite the old name).
`docs/HARNESS_LOCAL.md` records every local deviation from a stock install and is the document
the next upgrade reads first.

**Shipped: v1.8.2 (vc20).** Verified 2026-08-03: `v1.8.2` is tagged on origin and `main` carries
vc20. The train (AGP 8.13.2, F-Droid compatibility CI, bounded profile import, sticky-restart
gating, the DA-024 store icon) is merged. **Awaiting F-Droid**: the reproducible-build verification
of the first AGP 8.13.2 release is the owner's to confirm — no session can observe it.

`domain/` and `platform/` are byte-identical to 1.8.1. No plan files; parity checklist
zero-pending; tests green; TODO/FIXME and parity gaps zero. The ledger rolled over twice — the
live volume is `LEDGER_B.md`.

## Owner queue

> **Protected (D-167).** Owner actions/questions/findings survive compression; items leave only
> when done, answered or triaged. Test each item before restating it. Final chat restates it.

**Pending owner actions:**

1. Confirm F-Droid reports 1.8.2 successfully verified — the first AGP 8.13.2 release. Follow
   RUNBOOK §6/DA-026 if it differs. **Not observable from a session**; the owner settles it.
   When it lands, the DB-010 fold above becomes due and this item closes.

**Open questions:** (none)

**Incoming findings:**

- 2026-08-02 — **Owner device pass on 1.8.2-debug: 49 PASS / 5 FAIL / 2 BLOCKED / 3 SKIPPED.**
  Two real defects, both fixed: **C4** plugged-only panic fired on battery (DB-011); **F3/F4** a
  re-granted WRITE_SECURE_SETTINGS stayed invisible to the running service until an app restart
  (DB-012). The other three FAILs were script defects, not app defects (H used `am force-stop`,
  which cancels the sticky restart it was testing; G4's slow-provider condition never occurred;
  J6 followed a failed reinstall). K2/K3 were BLOCKED by Shizuku UI behaviour the steps assumed
  away. All corrected in `rebuild/DEVICE_TEST_SCRIPT_1.8.2.md`, which carries a "Round 2" list.
- 2026-08-02 — **Harm caused by our own script (DB-013).** Section J's `bmgr restore <token>`
  omitted the package argument — a whole-device restore that irreversibly overwrote stored
  settings across many unrelated apps on the owner's phone. Section J is retired outright at the
  owner's instruction (see Decided non-items) and the script states its blast radius up front.
- 2026-08-02 — **Owner-queue candidate (from DB-012):** `PrivilegeManager` is per-`AppModule` and
  `AppModule` is built at ~10 call sites, so "the shared tier cache" is shared only within one
  instance. A process-wide singleton is the real fix; DB-012 self-heals the visible symptom
  instead, the lifetime/threading change being too broad for a train at its tag.
- 2026-07-30 — Owner confirmed no open Dependabot alerts, closing DA-040's local-evidence gap
  (DA-041). 2026-07-24 — Owner confirmed `main` protection and secret-scanning push protection
  are enabled (DA-006).

## Decided non-items

- **Repo/process:** root changelog; speculative dependency bumps; standalone drift audit; action
  SHA-pinning; Gradle dependency verification; widening build CI to session branches (D-161).
  DA-040's wrapper-distribution digest is a narrower executable-integrity finding and reopens
  neither declined program.
- **Triage #1–#6 (D-162, DA-006/010/011/013/015):** glue checkbox output; ledger
  index/symlink/status retrofit/ID allocator; session delta/header/manifest; per-playbook
  matrices or RUNBOOK split; dashboard/KPIs/aging guard; dependency-pin playbook; scaffold
  CLI/profiles; full train verifier, warm-up sentinel, PR-body guard; auto-memory/prompt-order
  rewrite; orphan-provenance expansion.
- **Triage #7 (DA-021):** no harness rewrite from the external context-engineering blog; the
  rails are intentional and agent-neutral. Companion recovery stop became DA-012.
- **Privileged Display (D-150–152):** per-toggle scheduling; persisted last-applied seed absent
  real reports; QS/notification grayscale action; refresh-rate/OEM keys; manual Extra-Dim toggle.
- **On-device backup/restore verification (owner, 2026-08-02, DB-013):** not to be tested again.
  The script's whole-device `bmgr restore` damaged unrelated apps; the owner declined a re-run
  and that decline is binding. `SettingsBackupSanitizer` stays unit-tested and its allowlist
  inspectable; that `onRestoreFinished` is actually invoked is an **accepted unverified
  residual** for 1.8.2. Do not re-add the steps without new evidence (e.g. a spare device).

## Changelog

One line per shipped change or completed unit (newest first). Detail lives in the cited ledger
rows and in git history.

- 2026-08-03 — **DB-010 fold done at the 1.8.2 tag (owner-approved).** The ephemeral
  `rebuild/DEVICE_TEST_SCRIPT_1.8.2.md` is deleted; its durable checks became steps 15a/15b
  (plugged-only panic, demand-driven accelerometer) and 19a–19c (setpoint clamp where it is
  stored, live grant pickup, failed Extra Dim level write) in the permanent
  `rebuild/DEVICE_TEST_SCRIPT.md`. The train's other sections tested hardening of features the
  standing script already covers and whose bounds are unit-tested, so they did not carry over.
  Section J stays retired (DB-013) with its unverified residual recorded above.

- 2026-08-03 — **AMH convergence complete (DB-014…DB-016).** Adopted AMH 3.0.0 at the `full`
  profile, replacing the harness this repo originated. Docs moved to the AMH layout; all 124
  bold-form ledger row headers normalized to the only shape AMH's parser reads; the five shipped
  scripts replaced ours (8 of our 11 guards were already theirs, and we gain author-identity,
  shipped-integrity and repo-local-guard rungs). What stayed ours moved to the extension points:
  `scripts/bootstrap.sh`, `scripts/verify.sh`, four `scripts/guards/` and a 19-case
  `scripts/tests/local-guards.sh`. `AGENTS.md` became the constitution and `CLAUDE.md` its
  pointer; `docs/HARNESS_LOCAL.md` records every local delta; `docs/AGENTIC_HARNESS_PROMPT.md`
  was deleted as superseded by the AMH repository.
- 2026-08-02 — **DB-008/DB-009 (issue #110, upstream Tasker parity).** Dimming strength is now
  clamped in the shared `validate()` on every write path, so the field can no longer show 100
  while the screen dims to 65. New `panicRequiresPlugged` pref restricts the panic gesture to
  external power; implementing it exposed the accelerometer being held at ~50 Hz for the life of
  the service, including screen-off where the gesture cannot fire — registration is now
  demand-driven.
- 2026-07-31 — **Adversarial security round (DA-043/DA-044 + DB-001…DB-007).** Five findings
  against the hardening branch, all real, all fixed; three more the review did not find; one
  rebuttal that became a correction. Six audit documents merged into
  `rebuild/SECURITY_REVIEW.md`.
- 2026-07-23..30 — **DA-016–042:** wizard top-K fix, 1.8.1 RESUME-context fix, API-free release
  preflight, triage #7; store icon; AGP 8.13.2; F-Droid buildserver compatibility and
  cross-environment APK reproducibility CI; 256 KiB strict streamed profile imports;
  supersession-safe sticky restart; privileged commands became fixed typed Shizuku operations
  with bounded Binder/process time; permission/privacy allowlists; hardened opt-in geo-IP;
  bounded profile structures; every display write and callback/worker lifetime audited;
  dependency/build/release audit; Gradle wrapper pinned to its official SHA-256.
- 2026-06-23..07-24 — **D-096–176 + DA-001–015:** v1.0.0 rebuild through 1.8.0 — SDK36/JDK21,
  release/CodeQL/glue gates, Privileged Display, intent control, accessibility/crash log,
  IME/RESUME; then the ladder/ledger/state/harness/secret/git rails, branch-train, F-Droid
  inclusion, force dark, triages #1–#6 and the final adversarial audit.
