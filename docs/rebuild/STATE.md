# STATE — project state & session memory

> **Length guard (read before editing — DA-004 hysteresis).** Grow freely to **14 KB**; no
> trimming below that line. When the ladder warns (> 14 KB), run ONE deep compression pass
> to **≤ 9 KB** — never trim to just under a threshold (micro-trims re-arm the warn a session
> later; the 9→14 KB band is the debounce). Fail > 16 KB. Compression means: collapse each
> completed *Active work* stage into one Changelog line, fold changelog clusters, move any
> durable gotcha into the ledger (permanent, append-only — never compressed), delete
> narrative/punch-list prose. The **Project**, **Current state**, and **Owner queue** sections
> must always survive compression (Owner queue items are the owner's to close — compress their
> prose, never drop an open item). The migration narrative is frozen in `../history/` — do not
> re-accumulate it here. (`scripts/ladder.sh` guard 1 machine-checks: warn > 14 KB, fail
> > 16 KB.)

## Project

Native **Kotlin/Compose** Android rebuild of Tasker `Advanced_Auto_Brightness_V3.3`. Modules:
**`:domain`** (pure-JVM math/decision logic, golden-tested), **`:platform`** (Android adapters
behind small interfaces), **`:app`** (Compose M3 UI, DataStore `AabSettings`, FGS runtime, QS
tile, boot receiver). Privileges: **BASIC** `WRITE_SETTINGS` = full core pipeline; **ELEVATED**
`WRITE_SECURE_SETTINGS` (one-time `pm grant`) = super dimming + Privileged Display toggles.

## Current state

**Shipped: v1.7.0** (vc17, on `main`). **Code-complete, awaiting owner release cut: 1.8.0 /
vc18** — intent control (D-157), a11y + crash-log capture (D-156/D-158), IME (D-159), audit
(D-160), force dark (D-172). Whole train on ONE branch `claude/agent-agnostic-harness-fmb35b`
(other session branches deleted, 2026-07-19). No active work; no plan files.
`PARITY_CHECKLIST.md` zero-`pending`; parity tests green; TODO/FIXME 0; `parity_gaps.md` 0 open.
Changes per `RUNBOOK.md`; deviations in `DEVIATIONS_LEDGER.md` (live `_A.md`).

## Owner queue

> **Protected section (D-167).** Never delete this section or drop items during compression
> (guard 1b warns if the header vanishes). **Pending owner actions** = only-owner tasks; **Open
> questions** = owner-judgment forks (options + recommendation each, discipline 7); **Incoming
> findings** = owner on-device results. Items leave when done/answered/triaged (delete + record
> as a Changelog line or D-row). Final chat message restates this queue.

**Pending owner actions (the 1.8.0 release path):**

1. **PR deferred until F-Droid review completes** (owner 2026-07-09); local ladder = build.yml
   task set. **ONE PR** (owner 2026-07-10) from **`claude/agent-agnostic-harness-fmb35b`** →
   `main` (only session branch; supersets the train). Squash commit takes the PR title+body —
   draft = the FULL `origin/main..HEAD` payload (no `[skip ci]`-class tokens, D-115):
   - *Title:* `1.8.0 (vc18): intent control, a11y + crash-log capture, IME/RESUME/audit fixes,
     force dark — plus repo hardening and the agent-agnostic harness (D-156…D-176, DA-001…DA-012)`
   - *Body:* **Features (1.8.0/vc18):** automation intent surface (D-157, `docs/AUTOMATION.md`) ·
     TalkBack A0–A7 + crash-log capture (D-156/D-158) · force dark via Shizuku/root (D-172) ·
     context write-through + baseline revert (D-170). **Fixes:** IME dead-gap (D-159) · RESUME
     gate (D-160) · audit closes (D-163–D-165) · super-dimming relabel + MaxBright auto-raise
     (D-168/D-169) · stale User Guide. **Repo hardening:** `ladder.sh` acceptance + 10 guards +
     command guard, CI-run, own regression suite (D-161/D-166/D-173/D-174/DA-006–DA-012) · guarded
     Owner queue + ask-don't-assume (D-167) · secret hygiene (`redact.sh` + leak protocol +
     instruction
     hierarchy, D-175/DA-006/DA-007) · Gradle parallel/config-cache. **Harness (D-176/DA-001…
     DA-012, prompt v1.4):** `AGENTS.md` stub + neutral `session-start.sh`; thin `.claude/`
     adapter; ledger 1000-line rollover; branch-train; fresh-context glue/rule review;
     bounded-recovery stop condition (DA-012). **Release:** vc18 / 1.8.0.
2. After CI green: on-device `DEVICE_TEST_SCRIPT.md` **§§12–15**; findings → **Incoming
   findings**.
3. Cut **v1.8.0 / vc18** from `main` via the Release UI.
4. **Server-side rails (DA-006):** enable on GitHub — `main` branch protection (PRs required;
   force-push + deletion blocked) + secret-scanning push protection.

**Open questions:** (none)

**Incoming findings:** (none)

## Decided non-items (don't re-litigate without new evidence)

- **Repo/process (2026-06/07):** root `CHANGELOG.md`; speculative dep bumps (only on a security
  advisory); standalone doc-drift audit (self-adaptation covers it); action SHA-pinning / Gradle
  dep verification (2026-06-29, wrong cost/benefit); widening build.yml to `claude/**` (PR-time CI
  + local ladder suffice, D-161).
- **Triage #1 (2026-07-10, D-162):** glue-review checkbox output; ledger active-file
  symlink/marker; session-start delta generator; platform contract tests (exist, D-136/D-148);
  tracking-id branch names.
- **YAML codification of RUNBOOK/state (2026-07-13):** checkpoint manifest, glue-review YAML,
  per-playbook test matrices — re-litigates D-162 (Goodhart); enforcement derives from artifacts
  produced anyway.
- **Triage #2 (2026-07-20, accepts in DA-006):** verification manifests / `.ladder` summaries +
  machine session header (re-litigates 2026-07-13); generated ledger index (grep IS the index);
  metrics dashboard; dep SHA-pinning playbook; scaffold CLI/profiles/example repo (owner call if
  published); ledger `Status:` retrofit; Owner-queue aging guard. (Secret-pattern-guard decline
  owner-reopened → guard 9, DA-008.)
- **Triage #3 (2026-07-21, adopts in DA-010):** full verify-train script (invariants owner-side);
  PR-draft "whole train" guard (not machine-derivable, attestation Goodhart); warm-up sentinel
  (Gradle's lock IS the sync).
- **Triage #4 (2026-07-21, DA-011):** prompt-cache doc-ordering (load constitution/RUNBOOK/ledger
  first, STATE + diffs last for cache hits) — non-actionable in an agent-agnostic prompt (agent
  doesn't control host context assembly; vendor/time-specific, P6/D-176); contradicts the
  grep-on-demand ledger design (a cached prefix = reading 3.5k+ lines every session, the hazard P2
  bounds). Companion "unstick" idea NOT declined — ADOPTED (owner-approved) as the discipline-6
  bounded-recovery stop condition, DA-012.
- **Privileged Display (D-150–D-152):** per-toggle orthogonal scheduling (removed by D-151 pivot);
  persisted last-applied seed for `DisplayTogglesCoordinator` (revisit on real reports); QS tile /
  notification grayscale action; refresh-rate forcing / OEM alternate keys (D-048/D-149); manual
  Extra-Dim toggle (D-144/D-149).

## Changelog

One line per shipped change (newest first); detail in the D-rows and git history.

- 2026-07-21 — **DA-012** (owner-approved): anti-thrash stop condition in Session discipline 6
  (Recovery) — if the SAME blocker survives a second reset-and-retry cycle with no progress, stop,
  Owner-queue the blocker, end the unit (was: no termination condition → thrash the window, P6).
  Anchors the stop to a green checkpoint (branch never ends red) + persists the Owner-queue note.
  Adopts the DA-011 rec (the minimal stop-and-escalate kernel), not the literal 3-strike counter.
  Mirrored to the harness prompt (P7 + template 3.2) — **v1.3→v1.4**. DA-005 rule-review:
  fresh-context subagent, SAFE-WITH-FIXES (2 should-fixes + 3 nits adopted). DA-011 Open question
  retired.
- 2026-07-21 — **DA-011** (external-review triage #4): DECLINED prompt-cache doc-ordering
  (non-actionable in an agent-agnostic prompt; contradicts the grep-on-demand ledger design —
  P2/P6/D-176) → Decided non-items. PARKED the "unstick"/anti-thrash idea as an Owner-queue Open
  question (a binding discipline-6 change: DA-005 + owner fork). No code/rule/harness-prompt
  change — harness stays v1.3.
- 2026-07-10..21 — **D-161…D-176 + DA-001…DA-010**: repo hardening (`ladder.sh` guards + test
  suite, CI-run, deny rules + command guard, guarded Owner queue, secret hygiene: `redact.sh` +
  secret-shape scan + leak protocol + instruction hierarchy); triages #1–#3; final adversarial
  audit; parity/relabel fixes; context-load write-through; force dark; agent-agnostic harness
  (AGENTS.md stub, neutral session-start, ledger rollover, base closed at D-176); branch-train;
  fresh-context glue/rule review; STATE hysteresis; harness prompt v1.0→v1.3. Detail in each row.
- 2026-06-23..07-09 — **v1.0.0 Tasker→Kotlin rebuild** (Gate 3; frozen in `../history/`) →
  **1.7.0/vc17** → 1.8.0 close-out: D-096–D-160 — SDK 36/JDK 21/CodeQL, release CI, glue review,
  F-backlog, **Privileged Display** (D-151 pivot), intent control, a11y + crash-log, IME/RESUME.
