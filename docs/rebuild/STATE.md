# STATE — project state & session memory

> **Length guard (DA-004 hysteresis).** Grow freely to **14 KB**. Once warned, compress once to
> **≤ 9 KB**; fail >16 KB. Preserve Project, Current state, Owner queue, decided non-items and
> Changelog. Compress completed work into Changelog lines; move durable rules to the append-only
> ledger. Guard 1/1a enforces the 9→14 KB debounce landing; never micro-trim.

## Project

Native Kotlin/Compose rebuild of Tasker `Advanced_Auto_Brightness_V3.3`: `:domain` pure-JVM
math/decisions, `:platform` Android adapters, and `:app` Compose/DataStore/FGS UI/runtime. BASIC
`WRITE_SETTINGS` provides the core pipeline; ELEVATED `WRITE_SECURE_SETTINGS` adds super dimming and
Privileged Display.

## Current state

**Shipped: v1.8.1 (vc19). Release pending: 1.8.2 / vc20.** The train branch
`claude/gradle-deprecation-fdroid-870gh3` carries AGP 8.13.2, F-Droid compatibility CI, and the
1.8.2 bump; PR #99 folded bounded profile import + sticky-restart gating into it, and PR #96's
squash title/body describe the net train. At the tag, run DA-026's one-shot F-Droid reproducibility
check; the DA-024 store icon lands with it. `domain/` and `platform/` remain byte-identical to 1.8.1.
The 2026-07-31 adversarial round (DA-043/DA-044 + DB-001…DB-007) then fixed five confirmed findings
in that hardening plus three the review did not name, and merged six audit documents into
`SECURITY_REVIEW.md`. Device verification is now two files with a lifecycle (DB-010): the permanent
`DEVICE_TEST_SCRIPT.md` and the ephemeral `DEVICE_TEST_SCRIPT_1.8.2.md`, which is **deleted at the
1.8.2 tag** after folding anything durable into the permanent one (RUNBOOK §6). Three retired round
scripts are gone; `RESUME_CONTEXT_TEST.md`'s DA-018 checks live on as step 25's sub-bullets.
No plan files; parity checklist has zero pending, tests are green, TODO/FIXME
and parity gaps are zero. Changes follow RUNBOOK; the ledger rolled over — the live file is now
`DEVIATIONS_LEDGER_B.md` (`_A.md` closed at its 1000-line cap).

**2026-07-30 dependency/release audit (DA-040):** direct dependencies and privileged surfaces are
inventoried in `SECURITY_REVIEW.md` (dependency row); no versions changed. The
normal Gradle 8.14.3 wrapper originally lacked `distributionSha256Sum`; DA-042 now pins Gradle's
official binary ZIP digest, closing that executable-integrity gap. The owner confirmed there are no
open Dependabot alerts (DA-041), so no dependency version change is warranted by the approved process.

## Owner queue

> **Protected (D-167).** Owner actions/questions/findings survive compression. Final chat restates it.

**Pending owner actions:**

1. Close #97/#98 unmerged (superseded by merged #99), squash-merge PR #96 to `main`, and cut
   **1.8.2/vc20** from GitHub's release UI.
2. At that tag, after `release.yml`, confirm F-Droid reports the version successfully verified. This
   is the first AGP 8.13.2 release; follow RUNBOOK §6/DA-026 if it differs, then remove both items.

**Open questions:** (none)

**Incoming findings:**

- 2026-07-30 — Owner confirmed the approved Dependabot view has no open alerts, closing DA-040's
  local-evidence gap (DA-041); no dependency bump is indicated.
- 2026-07-24 — Owner confirmed GitHub `main` protection and secret-scanning push protection enabled
  (DA-006).

## Decided non-items (don't re-litigate without new evidence)

- **Repo/process:** root changelog; speculative dependency bumps; standalone drift audit; action
  SHA-pinning; Gradle dependency verification; widening build CI to session branches (D-161).
  DA-040's wrapper-distribution digest is a narrower executable-integrity finding, not dependency
  verification; it does not reopen either declined program.
- **Triage #1–#6 (D-162, DA-006/010/011/013/015):** glue checkbox output; ledger index/symlink/status
  retrofit/ID allocator; session delta/header/manifest; per-playbook matrices or RUNBOOK split;
  dashboard/KPIs/aging guard; dependency-pin playbook; scaffold CLI/profiles; full train verifier,
  warm-up sentinel, PR-body guard; auto-memory/prompt-order rewrite; orphan-provenance expansion.
- **Triage #7 (DA-021):** no harness/constitution rewrite from the external context-engineering blog;
  existing rails are intentional and agent-neutral. Companion recovery stop became DA-012.
- **Privileged Display (D-150–152):** per-toggle scheduling; persisted last-applied seed absent real
  reports; QS/notification grayscale action; refresh-rate/OEM keys; manual Extra-Dim toggle.

## Changelog

- 2026-08-02 — **DB-009 (issue #110, upstream Tasker parity + a battery bug it exposed).** New global
  pref `panicRequiresPlugged` (`%AAB_PanicPlugged`, default OFF) restricts the panic gesture to
  external power, surfaced on Live Debug beside the sensitivity slider. Implementing it surfaced the
  real problem: Tideo's orientation watch IS the trigger (Tasker gets it free from a profile STATE),
  so the accelerometer was held at ~50 Hz for the life of the service — **including screen-off, where
  the gesture cannot fire**. Registration is now demand-driven on `interactive && (!requiresPlugged ||
  plugged)`, re-evaluated on screen and power broadcasts. A test caught the first version consuming
  the gesture on release, which would have required a flip-straight-and-back after every screen-off.

- 2026-08-02 — **DB-008 (issue #110, upstream Tasker parity).** Dimming strength was clamped to 65 in
  the math but not in the stored setpoint, so the field showed 100 while the screen dimmed to 65 — the
  reporter confirmed it with `adb shell settings get secure`. Ported the owner's upstream
  `_SaveButtonDimming` A9–A12 fix: the setpoint is clamped in the shared `validate()` (every write
  path, not just the save button), Apply announces the correction with the value that persisted, and
  the draft snaps so the field shows what is in effect. Announce only when the value actually moved —
  the A9 test as shipped (`> 64.999999999`) also fired at exactly 65, flashing for a value it did not
  change. Upstream has since moved A9 to `> 65.0000000001`, so the two now agree with no Tideo change.

- 2026-07-31 — **Adversarial security round (DA-043/DA-044 + DB-001…DB-007).** Five findings against
  the hardening branch, all real, all fixed: external-control admission bounded the receiver but not
  the pipeline queue behind it (now coalescing + capped, `ControlFloodBoundTest`); SAF provider I/O
  ran on the UI dispatcher unbounded (now `Dispatchers.IO` + 20 s + cancellation); a failed Extra Dim
  level write left the previous, **stronger** level on screen while reporting `ON`; backup carried
  `serviceEnabled`/`contextOverride` (now sanitized at restore); the F-Droid comparator trusted
  declared CRC32 metadata and its docs overclaimed (now SHA-256 over decompressed bytes, EOCD-anchored
  signing-block reader, `selftest`). **Three the review did not find:** a `stdoutLimit = 0` that made
  any output from `pm grant` read as failure; the sanitizer's first design no-opping on the common
  case (`serviceEnabled` defaults **true** and kotlinx omits defaults, so the risky backup is the one
  where the key is absent); and unbounded post-kill reaps. **One rebuttal that became a correction:**
  a test written to disprove the geo-IP cancellation finding confirmed it instead — `invokeOnCompletion`
  on a job parked in `read()` cannot fire until that read returns. **Declined with reasons:** splitting
  the settings DataStore, a PANIC/DISABLE priority lane (they never use that queue), `apksigcopier`
  here, reordering profile apply (the proposed order silently reverts a user's load), and folding
  `FDROID_VALIDATION.md` into the RUNBOOK.

One line per shipped change (newest first); detail lives in the deviation rows and git history.

- 2026-07-30 — **DA-042:** pinned the Gradle 8.14.3 binary wrapper distribution to Gradle's official
  SHA-256, verified through both official checksum surfaces; wrapper downloads now fail on mismatch.
- 2026-07-30 — **DA-041:** owner confirmed no open Dependabot alerts; corrected DA-040's unavailable
  local status to the approved point-in-time result and removed the completed Owner-queue action.
- 2026-07-30 — **DA-040 dependency/build/release security audit:** inventoried direct and privileged
  dependencies, repositories, wrapper/signing/minification/lint/manifest policy, CI artifacts and
  provenance assumptions without version changes. Recorded the missing wrapper digest and initially
  unavailable local advisory evidence, while keeping action pinning/dependency verification declined
  absent new evidence; DA-041 records the owner's no-open-alert confirmation.
- 2026-07-29 — **DA-039:** traced callback/poller/worker/coroutine/Binder/process lifetimes; bounded
  external automation with process-wide single-flight admission.
- 2026-07-29 — **DA-038:** audited every display write; teardown restores mode/Extra Dim safely,
  panic recovery is independent, and Extra Dim ordering/latches are failure-aware.
- 2026-07-29 — **DA-037:** hardened opt-in geo-IP with redirect refusal, 16 KiB/strict JSON and
  coordinate bounds, cancellation, fail-closed consumption, daily retry bound and explicit privacy copy.
- 2026-07-29 — **DA-036:** bounded native/legacy profile structures, schemas, catalogs and names;
  validation covers every persistence/apply boundary and direct imports preserve secure toggles.
- 2026-07-29 — **DA-034/035:** completed permission/privacy allowlists and debug-only throwable logs;
  corrected F-Droid download-artifact to Node-24 v7.
- 2026-07-29 — **DA-031:** privileged commands became fixed typed Shizuku operations with bounded
  Binder/process time/output, cleanup and value-free failures. DA-032 bootstrap was reverted by DA-033.
- 2026-07-29 — completed Android component/action/export/permission/replay/FGS audit and pre-work
  security model; enabled external automation remains an explicit ambient local-authority decision.
- 2026-07-28 — **DA-029/030:** 256 KiB strict streamed profile imports with typed UI failures; sticky
  restart waits for persisted opt-in and is supersession-safe. Cut pending 1.8.2/vc20.
- 2026-07-28 — **DA-024–028:** store icon/badges; AGP 8.13.2; F-Droid buildserver compatibility and
  cross-environment APK-content reproducibility CI, including artifact-root and tag-trigger fixes.
- 2026-07-23..25 — **DA-016/018/020–023:** wizard top-K fix, 1.8.1 RESUME-context fix, API-free release
  preflight, triage #7, and Ko-fi repo-only owner correction.
- 2026-07-10..24 — **D-161–176 + DA-001–017:** ladder/ledger/state/harness/secret/git rails,
  branch-train, F-Droid inclusion, force dark, triages #1–#6 and final adversarial audit.
- 2026-06-23..07-09 — **v1.0.0 rebuild → 1.7.0/vc17 → 1.8.0:** D-096–160 shipped SDK36/JDK21,
  release/CodeQL/glue gates, Privileged Display, intent control, accessibility/crash log and IME/RESUME.
