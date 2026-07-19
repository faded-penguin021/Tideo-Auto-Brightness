# STATE — project state & session memory

> **Length guard (read before editing).** Steady-state target ≤ 12 KB. **If this file exceeds
> 16 KB, aggressively compress before committing:** collapse each completed *Active work* stage
> into one Changelog line, move any durable gotcha into `DEVIATIONS_LEDGER.md` (the permanent,
> append-only registry — never compressed), and delete narrative/punch-list prose. The
> **Project**, **Current state**, and **Owner queue** sections must always survive compression
> (Owner queue items are the owner's to close — compress their prose, never drop an open item).
> The full migration narrative is already frozen in `../history/` — do not re-accumulate it here.
> (`scripts/ladder.sh` machine-checks this rule: warn > 12 KB, fail > 16 KB.)

## Project

Native **Kotlin/Compose** Android app that is a feature-parity rebuild of the Tasker project
`Advanced_Auto_Brightness_V3.3`. Three modules: **`:domain`** (pure-JVM math/decision logic,
golden-tested), **`:platform`** (Android system adapters behind small interfaces), **`:app`**
(Compose M3 UI, DataStore `AabSettings`, foreground-service runtime, QS tile, boot receiver).
Privilege tiers: **BASIC** = user-grantable `WRITE_SETTINGS` (full core pipeline); **ELEVATED**
= `WRITE_SECURE_SETTINGS` via one-time `pm grant` (super dimming + Privileged Display toggles).
minSdk 31, target/compile 36.

## Current state

**Shipped: v1.7.0** (`versionCode 17`, tagged on `main`) — Privileged Display Control
(D-149–D-155).

**Code-complete, awaiting owner release cut: 1.8.0 / `versionCode 18`** — intent control for
automation frameworks (**D-157**, user reference `docs/AUTOMATION.md`), the A11y backlog +
crash-log capture (**D-156**/**D-158**), the **D-159** IME fix, the **D-160** audit fix, and
force dark via Shizuku/root (**D-172**, owner-folded into vc18 2026-07-17;
`changelogs/18.txt` 488 B). The whole train now lives on ONE branch —
`claude/agent-agnostic-harness-fmb35b` (all other session branches deleted, commits
contained; 2026-07-19 audit). Pre-release
audit passed 2026-07-09; the 2026-07-12 final adversarial audit fully closed (D-163–D-165
fixed). No active multi-unit work; no persisted plan files.

`PARITY_CHECKLIST.md` is zero-`pending`; golden parity tests green; TODO/FIXME = 0;
`parity_gaps.md` has 0 open gaps.

Owner-pending actions live in **`## Owner queue`** below (guarded section — see its preamble).

How changes are made now: see `RUNBOOK.md` (change-type playbooks; the **glue-review protocol**
is mandatory for `:platform`/runtime diffs; multi-session features follow the playbook-5
persisted-plan pattern). The migration narrative is frozen in `../history/`; all numbered
deviations live in the permanent registry `DEVIATIONS_LEDGER.md` (D-153 rollover; gate findings
in `../history/STATE_rebuild.md`).

## Owner queue

> **Protected section (D-167).** Never delete this section or silently drop its items during a
> compression pass — `scripts/ladder.sh` guard 1b warns if the header vanishes. Two kinds of
> items live here, both owner-facing: **Pending owner actions** (things only the owner can do)
> and **Open questions** (owner-judgment forks a session stopped at instead of guessing —
> RUNBOOK Session discipline 7, each with options + the session's recommendation). **Incoming
> findings** is the intake for the owner's on-device test results — the next session's protocol
> step 2 reads this file, so findings recorded here are guaranteed to be seen. Items leave the
> queue only when done/answered/triaged (then: delete, and record the outcome as a Changelog
> line or D-row). A session's final chat message should restate this queue.

**Pending owner actions (the 1.8.0 release path):**

1. **PR is deliberately deferred until the F-Droid review completes** (owner decision
   2026-07-09) — CI (build/CodeQL/release-preflight) first runs there; the local ladder is the
   equivalent build.yml task set. **ONE PR** (owner decision 2026-07-10): open it from
   **`claude/agent-agnostic-harness-fmb35b`** → `main` — the only session branch left on the
   remote (2026-07-19 audit); it supersets the whole train: the former fable/user-guide/
   intent-control/shizuku/ladder-checkpoint branches are deleted, their commits contained
   here. The squash commit takes the PR title+body, so use this draft describing the FULL
   55-commit / 97-file / +6344/−427 payload vs `main` (no `[skip ci]`-class tokens, D-115):
   - *Title:* `1.8.0 (vc18): intent control, a11y + crash-log capture, IME/RESUME/audit
     fixes, force dark — plus repo hardening and the agent-agnostic harness (D-156…D-176,
     DA-001)`
   - *Body:*
     **Features (1.8.0/vc18):** opt-in automation intent surface — verbs,
     LOAD_PROFILE/CONTEXTS_RESUME, outbound STATE_CHANGED; `docs/AUTOMATION.md` (D-157) ·
     TalkBack backlog A0–A7 + crash-log capture (D-156/D-158) · force dark via Shizuku/root,
     new `ForceDarkController` + Tools toggle (D-172) · context-rule profile loads write
     through to live settings with baseline revert (D-170).
     **Fixes:** three-part IME dead-gap fix (D-159) · external RESUME gated on user-disabled
     service (D-160) · adversarial-audit closes: stale signal snapshots, draft-Apply snap,
     panic re-arm flicker (D-163–D-165) · super-dimming help/PWM relabel + Misc MaxBright
     auto-raise parity (D-168/D-169) · stale User Guide copy.
     **Repo hardening:** `scripts/ladder.sh` one-command acceptance + guards (STATE caps,
     ledger rollover, citation integrity + `[cited]` sync, skip-ci scan, changelog cap) with
     its own 21-case regression suite, run by CI (D-161/D-166/D-173/D-174) · Owner queue +
     ask-don't-assume (D-167) · secret hygiene (D-175) · Gradle parallel/config-cache.
     **Agent-agnostic harness (D-176/DA-001):** adds `AGENTS.md` pointer stub + neutral
     `scripts/session-start.sh`; removes `.claude/hooks/session-start.sh` (`.claude/` = thin
     adapter); ledger rollover now 1000 lines/file — base ledger closed at D-176,
     `DEVIATIONS_LEDGER_A.md` live.
     **Release:** vc18 / 1.8.0, changelog 488 B · on-device checklist = DEVICE_TEST §§12–15.
2. After CI green: on-device pass of `DEVICE_TEST_SCRIPT.md` **§12 (TalkBack), §13 (automation),
   §14 (insets), §15 (force dark, D-172)**; findings → **Incoming findings** below.
3. Cut **v1.8.0 / vc18** from `main` via the Release UI.

**Open questions:** (none)

**Incoming findings:** (none — on-device results from DEVICE_TEST passes land here)

## Decided non-items (don't re-litigate without new evidence)

- **Repo/process (2026-06/07):** root `CHANGELOG.md` (redundant with STATE + fastlane + the
  ledger); speculative dependency-currency bumps (only on a security advisory); a standalone
  doc-drift audit (RUNBOOK self-adaptation covers it); action SHA-pinning / Gradle dependency
  verification (declined 2026-06-29, wrong cost/benefit solo); widening
  build.yml to `claude/**` push events (declined 2026-07-10 — PR-time CI + the local ladder
  suffice, D-161).
- **External AI-review suggestions (declined 2026-07-10, reasons in D-162):** glue-review
  checkbox output; ledger active-file symlink/marker; session-start delta generator; platform
  contract tests (already exist — D-136/D-148); tracking-id branch names.
- **YAML codification of RUNBOOK/state (declined 2026-07-13):** checkpoint manifest, glue-review
  YAML pass/fail block, per-playbook test matrices. Re-litigates the D-162 checkbox decline
  (Goodhart; duplicates state git/STATE/the ledger own). Enforcement derives from artifacts
  produced anyway, never from artifacts produced to pass the check.
- **Privileged Display (decided at Segments 4.5–5, D-150–D-152):**
  - **Per-toggle orthogonal scheduling** — D-150 built it, the D-151 pivot removed it.
    Scheduling IS "a Contexts rule loads a profile carrying display fields", winner-takes-all.
  - **A persisted last-applied seed** for `DisplayTogglesCoordinator` — deferred at 4.5: it
    would shrink the D-151 process-death residual by re-introducing exactly the latch-like
    persistence the pivot removed. Revisit only on real-world reports.
  - **QS tile / notification action for grayscale** (or any display toggle) —
    profiles/Contexts are the switching surface.
  - **Refresh-rate forcing** / OEM-specific alternate keys — OEM-fragmented (D-048/D-149).
  - **A manual Extra-Dim toggle** — Extra Dim is pipeline-owned (D-144/D-149).

## Changelog

One line per shipped change or completed backlog (newest first). Keep terse; details live in
the cited D-rows and git history.

- 2026-07-19 — **DA-002** (owner-requested): branch-train workflow codified — sessions stack,
  superseded branches deleted, ONE final-branch squash PR describing `origin/main..HEAD`;
  CLAUDE.md Git rules + guard-4 wording + harness prompt P13/3.4.
- 2026-07-19 — docs (owner-requested, no D-row): `AGENTIC_HARNESS_PROMPT.md` made
  agent-agnostic (pointer-file pattern, neutral bootstrap/adapters per D-176) + synced to the
  DA-001 line-based ledger cap.
- 2026-07-19 — **DA-001** (owner-instructed): ledger rollover unit rows → **1000 lines** (final
  row may overflow; the next opens the next file); base ledger CLOSED at D-176 (D-177…D-184
  never assigned), `DEVIATIONS_LEDGER_A.md` is live; guard 1c + test fixtures rewritten.
- 2026-07-19 — **D-176** (owner-requested): agent-agnostic harness — AGENTS.md pointer stub,
  neutral `scripts/session-start.sh` bootstrap (`AAB_REMOTE=1`), `.claude/` reduced to a thin
  settings.json adapter; CLAUDE.md stays canonical.
- 2026-07-18 — **D-175** (owner-requested): secret hygiene (env-dump denies in settings.json,
  presence-only reporting) + commit-body verification disclosure (RUNBOOK discipline 8).
- 2026-07-18 — docs (owner-requested, no D-row): `docs/AGENTIC_HARNESS_PROMPT.md` — the
  harness generalized into a reusable prompt.
- 2026-07-18 — **D-174** (owner-requested): machine-synced `[cited]` ledger marker — guard 5
  enforces both directions; 93 rows retrofitted. Detail in the D-174 row.
- 2026-07-18 — **D-173** harness hardening: ladder guards 5 (D-citation
  integrity) + 6 (F-Droid changelog cap); `test-ladder-guards.sh` regression suite (18 cases,
  + build.yml step); release-preflight classifies `.claude/*` non-shipping; session-start
  background Gradle warm-up; STATE recompressed 15.3 → 12 KB. Detail in the D-173 row.
- 2026-07-18 — copy trim (wording only, no D-row): shortened the two force-dark strings in
  `strings.xml` per owner screenshot markup.
- 2026-07-17 — **D-172** (owner-requested; folded into unreleased 1.8.0/vc18): force dark via
  Shizuku/root — Tools card toggling `debug.hwui.force_dark` (`ForceDarkController`), opt-in,
  service re-asserts at start; per-app flipping declined. DEVICE_TEST §15; +11 tests.
- 2026-07-16 — docs: retired 8 stale `deferred-S13` tags in `anonymous_handlers.md` (shipped in
  S13d); ledger preamble separates structural vs base-file header content (D-153 rollover).
- 2026-07-15 — **D-171** (owner-instructed): ledger file cap 200 → **184 rows**; guard 1c +
  docs updated in lockstep.
- 2026-07-15 — **D-170** (owner-reported): context-rule profile loads write through to the live
  settings DataStore (`LOAD_FILE` semantics), pre-override baseline in
  `aab_context_baseline.json`, restored on no-match revert; supersedes D-038(ii). +8 tests.
- 2026-07-14 — STATE length-guard hard cap 32 KB → **16 KB** (owner); guard 1 + preamble updated.
- 2026-07-14 — **D-169** (owner-reported parity gap): Misc save raises MaxBright to Zone 2 End +
  flash (Tasker `_SaveButtonMisc`) instead of the D-052 block; narrows D-052.
- 2026-07-14 — **D-168** (owner-reported parity gap): super-dimming toggle help fix + "PWM
  threshold" relabel. UI strings only.
- 2026-07-13 — **D-167**: guarded `## Owner queue` section + RUNBOOK discipline 7 "ask,
  don't assume".
- 2026-07-13 — **D-166 (+ addendum)** repo-hardening: `build.yml` runs `scripts/ladder.sh`,
  ladder guards 3/4, `settings.json` push deny rules, session-start branch/STATE print.
- 2026-07-12 — final adversarial audit CLOSED: **D-163**–**D-165** fixes +
  `WizardDegenerateInputTest`; +6 tests.
- 2026-07-10 — **D-161** repo-hardening U1–U4 (ladder.sh, STATE 27.6→12 KB, Session discipline,
  Gradle parallel/config-cache) + **D-162** external-review triage (guards 1b/1c, recovery rule,
  golden-fixture gate; declines in the row + non-items).
- 2026-07-08/09 — 1.8.0 close-out: **D-159** IME dead gap; **D-160** external `RESUME` gate;
  pre-release audit green; DEVICE_TEST §14.
- 2026-07-06/07 — 1.8.0/vc18 features: **D-157** intent control (`docs/AUTOMATION.md`,
  +20 tests) + **D-156** a11y A0–A7 / **D-158** crash-log capture (+72 tests). Owner
  completed H4/H5.
- 2026-07-05 — docs/process: **D-153** ledger 200-row cap + rollover; RUNBOOK <500-char
  F-Droid `whatsNew` rule.
- 2026-07-03..05 — **1.7.0 / vc17 (MINOR): Privileged Display Control** (**D-149**–**D-155** + D-048
  OEM quirk): `SecureDisplayController`, Privileged Display screen, `ContextMatching` extraction, D-150
  schedules removed by the D-151 owner pivot (toggles = PROFILE settings), D-152 profile port, D-154
  circadian Night Light (task90 tanh), D-155 panic resets toggles. playbook-5 added.
- 2026-07-01/02 — 1.6.1 / vc15 + 1.6.2 / vc16 (PATCHes): hardening H1–H5 (**D-133**–**D-137**,
  RUNBOOK gains the mandatory **glue-review protocol**; reproducible release APK) and F-backlog
  U1–U6 closed (**D-138**–**D-148**, +19 tests; parity spot-check + `/security-review` clean).
- 2026-06-26..30 — 1.1.0→1.6.0 (vc7→vc14): SDK 36 + Robolectric 4.16.1/JDK 21 + CodeQL +
  `.debug` suffix (**D-101**–**D-106**); **D-107**–**D-122** (D-115 skip-ci, D-116 panic);
  release CI (**D-123**–**D-124**); wizard/resume (**D-125**–**D-126**); SSID/i18n/cooldown
  (**D-130**–**D-132**).
- 2026-06-23..25 — **v1.0.0: Tasker→Kotlin rebuild complete** (Gate 3 signed off; history frozen
  in `../history/`); early PATCHes 1.0.1–1.0.4 (**D-096**–**D-100**, F-Droid metadata, RUNBOOK
  §6 release checklist).
