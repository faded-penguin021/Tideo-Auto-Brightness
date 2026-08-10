# STATE — project state & session memory

> **Length guard (DA-004 hysteresis — read before editing).** The thresholds are
> `STATE_WARN_KB`, `STATE_COMPRESS_TO_KB` and `STATE_HARD_KB` in `amh.conf`, named here and
> deliberately **not** restated as numbers: nothing checks this prose against the config, so a
> copied number drifts silently the first time a threshold moves. `scripts/ladder.sh` prints the
> soft and hard caps when it passes and the floor when it warns. Grow freely to the soft cap; no
> trimming below it. When the guard warns, run ONE deep pass to the floor — never trim to just
> under the soft cap, because a micro-trim re-arms the warning a session later and the wide band
> IS the debounce. The floor is a **ceiling, not a target**: if the pass lands short, fold MORE
> completed stages rather than micro-trimming toward it. Fail above the hard cap. Compression
> means: collapse each completed stage into one Changelog line, fold changelog clusters, move
> durable gotchas to the append-only ledger, delete narrative prose.
>
> **Project**, **Current state**, **Decided non-items** and **Changelog** must always survive —
> those four are `STATE_REQUIRED_SECTIONS` and the guard FAILS on a missing or empty one.
> **Owner queue** is protected separately at WARN level: never delete it and never silently drop
> items during compression — they are the owner's to close, so compress their prose rather than
> dropping an open item.
>
> The ladder also checks WHERE a pass lands, and the rule is sharper than it reads. **Any** edit
> that takes the file from above the soft cap to at or below it must reach the floor, however
> small the edit was: a five-byte typo fix at 14340 bytes crosses the cap and FAILS unless it
> also lands under the floor. The `STATE_EDIT_DELTA_BYTES` distinction between a compression
> pass and an ordinary edit applies only while the file is **still above** the cap. Two traps
> follow: never pad the file back up to escape that failure, and never trim a file that is
> already under the cap — a pass that starts below it is invisible to the landing check, so
> nothing will tell you it stopped short.

## Project

Native Kotlin/Compose rebuild of Tasker `Advanced_Auto_Brightness_V3.3`: `:domain` pure-JVM
math/decisions, `:platform` Android adapters, `:app` Compose/DataStore/FGS UI/runtime. BASIC
`WRITE_SETTINGS` runs the core pipeline; ELEVATED `WRITE_SECURE_SETTINGS` adds super dimming and
Privileged Display. Maintenance runs on the **AMH** (`amh.conf` records the version).

## Current state

**Harness: AMH 5.1.0 (DB-024; converged at 3.0.0, DB-014…DB-016).** The harness this repo
originated was spun out as [AMH](https://github.com/faded-penguin021/AMH) and is now replaced by
upstream's. **The five scripts named in `scripts/MANIFEST.sha256` are upstream's byte-for-byte
and hash-checked every run — never edit one;** changes go to `amh.conf`, `scripts/guards/*.sh` or
`scripts/verify.sh`. The constitution is `AGENTS.md` (`CLAUDE.md` points at it; rows before
2026-08-03 cite the old name). `docs/HARNESS_LOCAL.md` records every local deviation and holds
the last prose copy of the cap values; it is what the next upgrade reads first.

**Shipped: v1.8.2 (vc20).** Verified 2026-08-03: `v1.8.2` is tagged on origin and `main` carries
vc20. The train (AGP 8.13.2, F-Droid compatibility CI, bounded profile import, sticky-restart
gating, the DA-024 store icon) is merged. **Awaiting F-Droid**: reproducible-build verification of
the first AGP 8.13.2 release is the owner's to confirm — no session can observe it.

`domain/` and `platform/` are byte-identical to 1.8.1. No plan files; parity checklist
zero-pending; tests green; TODO/FIXME and parity gaps zero. Live ledger: `LEDGER_B.md`.

## Owner queue

> **Protected (D-167).** Owner actions/questions/findings survive compression; items leave only
> when done, answered or triaged. Test each item before restating it. Final chat restates it.

**Pending owner actions:**

1. Confirm F-Droid reports 1.8.2 successfully verified — the first AGP 8.13.2 release. Follow
   RUNBOOK §6/DA-026 if it differs. **Not observable from a session**; the owner settles it.

**Open questions:** (none)

**Incoming findings:**

- 2026-08-02 — **Owner device pass on 1.8.2-debug: 49 PASS / 5 FAIL / 2 BLOCKED / 3 SKIPPED.**
  Two real defects, both fixed (DB-011 plugged-only panic firing on battery; DB-012 a re-granted
  WRITE_SECURE_SETTINGS invisible to the running service until app restart). The other three FAILs
  were script defects; K2/K3 BLOCKED by Shizuku UI behaviour the steps assumed away.
- 2026-08-02 — **Harm caused by our own script (DB-013).** Section J's `bmgr restore <token>`
  omitted the package argument — a whole-device restore that irreversibly overwrote settings
  across unrelated apps on the owner's phone. Section J is retired at the owner's instruction (see
  Decided non-items) and the script now states its blast radius up front.
- 2026-08-02 — **Owner-queue candidate (from DB-012):** `PrivilegeManager` is per-`AppModule`,
  built at ~10 call sites, so the "shared" tier cache is shared within one instance only. A
  process-wide singleton is the real fix; DB-012 self-heals the symptom instead.
- 2026-07-30 — Owner confirmed no open Dependabot alerts, closing DA-040's local-evidence gap
  (DA-041). 2026-07-24 — Owner confirmed `main` protection and secret-scanning push protection
  are enabled (DA-006).

## Decided non-items

- **Repo/process:** root changelog; speculative dependency bumps; standalone drift audit; action
  SHA-pinning; Gradle dependency verification; widening build CI to session branches (D-161).
  DA-040's wrapper digest is a narrower executable-integrity finding and reopens neither
  declined program.
- **Triage #1–#6 (D-162, DA-006/010/011/013/015):** glue checkbox output; ledger
  index/symlink/status retrofit/ID allocator; session delta/header/manifest; per-playbook matrices
  or RUNBOOK split; dashboard/KPIs/aging guard; dependency-pin playbook; scaffold CLI/profiles;
  train verifier, warm-up sentinel, PR-body guard; auto-memory rewrite; orphan-provenance.
- **Triage #7 (DA-021):** no harness rewrite from the external context-engineering blog; the rails
  are intentional and agent-neutral. Companion recovery stop became DA-012.
- **Privileged Display (D-150–152):** per-toggle scheduling; persisted last-applied seed absent
  real reports; QS/notification grayscale action; refresh-rate/OEM keys; manual Extra-Dim toggle.
- **On-device backup/restore verification (owner, 2026-08-02, DB-013):** not to be tested again.
  The script's whole-device `bmgr restore` damaged unrelated apps; the owner declined a re-run and
  that decline is binding. `SettingsBackupSanitizer` stays unit-tested and its allowlist
  inspectable; that `onRestoreFinished` is actually invoked is an **accepted unverified residual**
  for 1.8.2. Do not re-add the steps without new evidence (e.g. a spare device).

## Changelog

Newest first; older clusters fold to one line. The cited ledger rows are the record.

- 2026-08-10 — **AMH upgraded 4.2.0 → 5.1.0 (DB-024…DB-026).** The one MAJOR (5.0.0,
  `LEDGER_ROW_CHAR_CAP` default 2000 → 800) was a no-op: our 750 is set explicitly. Only
  `ladder.sh`, `test-ladder-guards.sh` and the manifest moved, and the release declares no
  `amh.conf` key we do not already set. Both 5.1.0 seed-prose notes applied by hand. The
  rule-review pass returned 8 findings, all triaged and fixed — the load-bearing two are DB-025/DB-026.
- 2026-08-03..09 — **AMH convergence, then three upgrades to 4.2.0 (DB-014…DB-023).** Adopted AMH
  3.0.0 at the `full` profile, replacing the harness this repo originated; then the runtime
  inventory, unlimited volume carry, row cap 750, fail-closed preflight classes, a guard warn
  tier. Two lessons cost work: a 42% constitution cut landed unreviewed and the review restored 12
  defects, 3 binding (DB-018); and the whole-word matcher silently stopped resolving `D-042c`
  citations, which an upgrade answered by deleting three `[cited]` markers (DB-022).
- 2026-07-31..08-03 — **Tasker parity fixes, the adversarial security round, and the DB-010 fold
  (DB-001…DB-013, DA-043/DA-044).** Dimming strength clamped in the shared `validate()` on every
  write path; `panicRequiresPlugged` added, which exposed the accelerometer held at ~50 Hz for the
  service's life (now demand-driven). Security round: five findings, all real, all fixed, three
  more the review missed. At the 1.8.2 tag the ephemeral device script was folded into the
  permanent `rebuild/DEVICE_TEST_SCRIPT.md`; Section J stays retired (DB-013).
- 2026-06-23..07-30 — **D-096–176 + DA-001–042:** v1.0.0 through 1.8.1. The rebuild
  (SDK36/JDK21, Privileged Display, intent control, accessibility/crash log, IME/RESUME), the
  release/CodeQL/glue gates, the ladder/ledger/state/harness/secret/git rails, branch-train,
  F-Droid inclusion and triages #1–#7; then the hardening train — AGP 8.13.2, reproducibility CI,
  bounded profile imports, typed Shizuku operations, allowlists, wrapper pinned to its SHA-256.
