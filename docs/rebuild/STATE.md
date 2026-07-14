# STATE — project state & session memory

> **Length guard (read before editing).** Steady-state target ≤ 12 KB. **If this file exceeds
> 32 KB, aggressively compress before committing:** collapse each completed *Active work* stage
> into one Changelog line, move any durable gotcha into `DEVIATIONS_LEDGER.md` (the permanent,
> append-only registry — never compressed), and delete narrative/punch-list prose. The
> **Project**, **Current state**, and **Owner queue** sections must always survive compression
> (Owner queue items are the owner's to close — compress their prose, never drop an open item).
> The full migration narrative is already frozen in `../history/` — do not re-accumulate it here.
> (`scripts/ladder.sh` machine-checks this rule: warn > 12 KB, fail > 32 KB.)

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

**Code-complete, awaiting owner release cut: 1.8.0 / `versionCode 18`** — (a) **intent control
for automation frameworks** (Tasker / MacroDroid, **D-157**; user reference `docs/AUTOMATION.md`)
and (b) the **A11y (TalkBack) backlog + crash-log capture** (**D-156**, **D-158**), plus the
**D-159** IME fix and the **D-160** audit fix. `changelogs/18.txt` final (455 chars). Pre-release
audit passed 2026-07-09; the 2026-07-12 final adversarial audit is fully closed (all scopes
clean, D-163–D-165 fixed — see the Changelog + D-rows). No active multi-unit work; all persisted
plan files deleted.

`PARITY_CHECKLIST.md` is zero-`pending`; golden parity tests green; TODO/FIXME = 0;
`parity_gaps.md` has 0 open gaps.

Owner-pending actions live in **`## Owner queue`** below (guarded section — see its preamble).

How changes are made now: see `RUNBOOK.md` (change-type playbooks; the **glue-review protocol**
is mandatory for `:platform`/runtime diffs; multi-session features follow the playbook-5
persisted-plan pattern). The migration narrative is frozen in `../history/`; the deviations
registry stays live.

> Code/docs elsewhere cite deviations by number (e.g. `STATE.md D-048`, `F50`). All deviations
> — migration and ongoing — live in the permanent registry `DEVIATIONS_LEDGER.md` (200 rows per
> file, then `_A.md`/DA-…, `_B.md`/DB-… — D-153; gate findings are in
> `../history/STATE_rebuild.md`). Look there.

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
   `claude/fable-model-improvements-3r07qs` → `main` — it supersets the 1.8.0 branch, so
   `user-guide-accuracy-check-i5fxex` and `intent-control-u4-i2i7e1` are **deleted unmerged**
   (both fully contained, 0 unique commits). Ready-made draft (no `[skip ci]`-class tokens, D-115):
   - *Title:* `1.8.0: intent control (D-157), a11y (D-156/D-158), IME fix (D-159), RESUME gate (D-160) + repo hardening (D-161)`
   - *Body bullets:* opt-in automation surface (verbs + LOAD_PROFILE/CONTEXTS_RESUME + outbound
     STATE_CHANGED; docs/AUTOMATION.md) · TalkBack backlog A0–A7 + crash-log capture C1 · D-159
     three-part IME fix · D-160 audit fix (glue-review verdicts in the U2/U5/38c66cd commit
     bodies) · repo hardening (ladder.sh guards, STATE compression, D-161 session discipline,
     Gradle parallel/config-cache) · owner on-device checklist = DEVICE_TEST §§12–14 ·
     vc18/1.8.0, changelog 455 chars.
2. After CI green: on-device pass of `DEVICE_TEST_SCRIPT.md` **§12 (TalkBack), §13 (automation),
   §14 (insets)**; findings → **Incoming findings** below.
3. Cut **v1.8.0 / vc18** from `main` via the Release UI.
4. When merging session branches, note `claude/ladder-checkpoint-improvements-dm4ewz` (D-166/167
   repo hardening) is independent of the 1.8.0 PR branch — separate squash-merge.

**Open questions:** (none)

**Incoming findings:** (none — on-device results from DEVICE_TEST passes land here)

## Decided non-items (don't re-litigate without new evidence)

- **Repo/process (2026-06/07):** root `CHANGELOG.md` (redundant with STATE + fastlane + the
  ledger); speculative dependency-currency bumps (only on a security advisory); a standalone
  doc-drift audit (RUNBOOK self-adaptation covers it); action SHA-pinning / Gradle dependency
  verification (declined 2026-06-29 as wrong cost/benefit for a solo F-Droid app); widening
  build.yml to `claude/**` push events (declined 2026-07-10 — PR-time CI + the local ladder
  suffice, D-161).
- **External AI-review suggestions (declined 2026-07-10, reasons in D-162):** glue-review
  checkbox output; ledger active-file symlink/marker; session-start delta generator; platform
  contract tests (already exist — D-136/D-148); tracking-id branch names.
- **YAML codification of RUNBOOK/state (declined 2026-07-13):** checkpoint manifest
  (`checkpoint.yaml`), glue-review YAML pass/fail block, per-playbook test matrices. Re-litigates
  the D-162 checkbox decline; self-reported flags/attestations can't enforce reviewer attention
  (Goodhart — the paperwork becomes the target) and duplicate state that git/STATE/the ledger
  already own; scoped test matrices save nothing when the full ladder re-verifies warm in ~1 s.
  Enforcement here derives from artifacts the agent produces anyway (diffs, commit messages,
  file sizes — the D-115/guard-1b/guard-3 pattern), never from artifacts produced to pass the
  check.
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

- 2026-07-14 — **D-169** (owner-reported parity gap): MaxBright auto-raise on the **Misc** save. When a
  curve leaves no zone-3 headroom (form3A < 0) and MaxBright < 255, the Misc-screen Apply now RAISES
  MaxBright to the curve's Zone 2 End value + flashes "adjusted to N" (Tasker `_SaveButtonMisc` A5–A18),
  instead of the D-052 CRITICAL block with an opaque form3A message. The Curve screen only reddens
  form3A (advisory), never touching MaxBright (matches Tasker's curve scene). **Narrows D-052** —
  form2A<0 / form2C>zone1End still block Apply. Curve math/goldens untouched.
- 2026-07-14 — **D-168** (owner-reported parity gap; owner syncs Tasker to match): fixed the
  "Use super dimming" toggle help (was task510's verbatim text describing *circadian scaling* —
  wrong control) and made the shared dimming-threshold field relabel "SD threshold"→"PWM
  threshold" + swap its flash when software dimming (PWM) is on. UI strings only; no domain/math.
- 2026-07-13 — **D-167** (owner-approved review item 5): guarded `## Owner queue` STATE section
  (pending actions / open questions / incoming-findings intake; guard 1b WARN; the 1.8.0 release
  path moved there from unprotected prose); RUNBOOK Session discipline 7 "ask, don't assume"
  (owner-judgment forks are queued with options + recommendation, never resolved by picking);
  sessions restate the queue in their final message.
- 2026-07-13 — **D-166 (+ addendum)** repo-hardening (external-review triage, no app code): four
  prose invariants → mechanism. `build.yml` invokes `scripts/ladder.sh` with `fetch-depth: 0`
  (structural CI/local lockstep; the D-115 token scan genuinely runs in CI); ladder guards 3
  (checkpoint STATE-line tripwire; per-branch residual documented) + 4 (behind-main → merge, not
  rebase), WARN-only, CI-skipped; `settings.json` deny rules for force-push / push-main /
  `+refspec` / `HEAD:main`; session-start hook prints branch check + STATE pointer unconditionally.
- 2026-07-12 — final adversarial audit CLOSED (all scopes; fixes fold into 1.8.0/vc18):
  **D-163** rule-removal clears signal snapshots; **D-164** draft Apply snaps to the validated
  commit; **D-165** panic re-arm needs a sustained straight spell. Deferred settings/UI/domain
  scope re-ran clean; `WizardDegenerateInputTest` pins the wizard invariant. +6 tests, no goldens.
- 2026-07-10 — **D-161** repo-hardening U1–U4 (`scripts/ladder.sh`, STATE compressed 27.6→12 KB,
  RUNBOOK **Session discipline**, Gradle parallel/config-cache — warm re-verify ~1 s) +
  **D-162** external-review triage (guards 1b/1c, recovery rule, release-preflight
  golden-fixture gate; declines in the row + non-items).
- 2026-07-08/09 — 1.8.0 fixes + close-out: **D-159** draft-screen IME dead gap (3-part
  edge-to-edge recipe); **D-160** external `RESUME` gated at `ControlReceiver.route`
  (DEVICE_TEST §13.43); pre-release audit green, DEVICE_TEST §14 added; user-guide S5 corrected.
- 2026-07-06/07 — 1.8.0/vc18 features: **D-157 intent control** (opt-in exported broadcast
  surface, default OFF — 7 verbs + LOAD_PROFILE/CONTEXTS_RESUME, outbound STATE_CHANGED,
  `docs/AUTOMATION.md`, DEVICE_TEST §13, +20 tests) + **D-156 a11y A0–A7** / **D-158 crash-log
  capture C1** (SemanticsAudit gate, DEVICE_TEST §12, +72 tests). Owner completed H4/H5
  (D-135/D-137 fdroiddata).
- 2026-07-05 — docs/process: **D-153** ledger 200-row cap + rollover; RUNBOOK <500-char
  F-Droid `whatsNew` rule.
- 2026-07-03..05 — **1.7.0 / vc17 (MINOR): Privileged Display Control** (**D-149**–**D-155** +
  D-048 OEM quirk): `SecureDisplayController`, Privileged Display screen, `ContextMatching`
  extraction (goldens untouched), D-150 schedules removed by the D-151 owner pivot (toggles =
  PROFILE settings, `DisplayTogglesCoordinator`), D-152 profile port, D-154 circadian Night
  Light (task90 tanh), D-155 panic resets toggles. DEVICE_TEST §11; playbook-5 pattern added.
  Also repo-tooling: `setup-android-sdk.sh` seeds the Gradle wrapper cache from `/opt`.
- 2026-07-01/02 — 1.6.1 / vc15 + 1.6.2 / vc16 (PATCHes): hardening H1–H5 (**D-133**–**D-137**,
  RUNBOOK gains the mandatory **glue-review protocol**; reproducible release APK) and F-backlog
  U1–U6 closed (**D-138**–**D-148**, +19 tests; parity spot-check + `/security-review` clean).
- 2026-06-26..30 — 1.1.0→1.6.0 (vc7→vc14, MINORs + PATCHes): targetSdk/compileSdk 36 +
  Robolectric 4.16.1/JDK 21 + CodeQL + `.debug` suffix (**D-101**–**D-106**, owner Pass A/B
  passed); **D-107**–**D-122** (incl. D-115 skip-ci token, D-116 panic rework); CI
  release-preflight/release.yml (**D-123**–**D-124**); wizard/resume (**D-125**–**D-126**);
  no-Location SSID, full i18n, plug/unplug cooldown bypass (**D-130**–**D-132**).
- 2026-06-23..25 — **v1.0.0: Tasker→Kotlin rebuild complete** (Gate 3 signed off; history frozen
  in `../history/`); early PATCHes 1.0.1–1.0.4 (**D-096**–**D-100**, F-Droid metadata, RUNBOOK
  §6 release checklist).
