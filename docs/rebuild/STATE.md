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

Native **Kotlin/Compose** Android app that is a feature-parity rebuild of the Tasker project
`Advanced_Auto_Brightness_V3.3`. Three modules: **`:domain`** (pure-JVM math/decision logic,
golden-tested), **`:platform`** (Android system adapters behind small interfaces), **`:app`**
(Compose M3 UI, DataStore `AabSettings`, foreground-service runtime, QS tile, boot receiver).
Privilege tiers: **BASIC** = user-grantable `WRITE_SETTINGS` (full core pipeline); **ELEVATED**
= `WRITE_SECURE_SETTINGS` via one-time `pm grant` (super dimming + Privileged Display toggles).

## Current state

**Shipped: v1.7.0** (vc17, on `main`). **Code-complete, awaiting owner release cut:
1.8.0 / vc18** — intent control (D-157, `docs/AUTOMATION.md`), a11y backlog + crash-log
capture (D-156/D-158), IME fix (D-159), audit fix (D-160), force dark (D-172). The whole train lives on ONE
branch — `claude/agent-agnostic-harness-fmb35b` (all other session branches deleted;
2026-07-19 audit). No active multi-unit work; no plan files.

`PARITY_CHECKLIST.md` zero-`pending`; parity tests green; TODO/FIXME = 0; `parity_gaps.md`
0 open gaps. Changes are made per `RUNBOOK.md` (glue review mandatory for
`:platform`/runtime diffs; playbook-5 plans for multi-session features).
Deviations live in `DEVIATIONS_LEDGER.md` (live file `_A.md`).

## Owner queue

> **Protected section (D-167).** Never delete this section or silently drop its items during
> a compression pass — ladder guard 1b warns if the header vanishes. **Pending owner
> actions** = things only the owner can do; **Open questions** = owner-judgment forks a
> session stopped at instead of guessing (RUNBOOK discipline 7; options + recommendation
> each); **Incoming findings** = intake for the owner's on-device test results (protocol
> step 2 reads this file, so findings here are guaranteed seen).
> Items leave only when done/answered/triaged (then delete + record the outcome as a
> Changelog line or D-row). A session's final chat message restates this queue.

**Pending owner actions (the 1.8.0 release path):**

1. **PR deferred until the F-Droid review completes** (owner 2026-07-09); the local
   ladder is the equivalent build.yml task set. **ONE PR** (owner
   2026-07-10) from **`claude/agent-agnostic-harness-fmb35b`** → `main` (the only session
   branch; supersets the whole train). The squash commit takes the PR title+body — use this
   draft for the FULL `origin/main..HEAD` payload (no `[skip ci]`-class tokens, D-115):
   - *Title:* `1.8.0 (vc18): intent control, a11y + crash-log capture, IME/RESUME/audit
     fixes, force dark — plus repo hardening and the agent-agnostic harness (D-156…D-176,
     DA-001…DA-009)`
   - *Body:*
     **Features (1.8.0/vc18):** opt-in automation intent surface — verbs, LOAD_PROFILE/
     CONTEXTS_RESUME, outbound STATE_CHANGED; `docs/AUTOMATION.md` (D-157) · TalkBack
     backlog A0–A7 + crash-log capture (D-156/D-158) · force dark via Shizuku/root (D-172) ·
     context-rule loads write through to live settings with baseline revert (D-170).
     **Fixes:** three-part IME dead-gap fix (D-159) · external RESUME gated on
     user-disabled service (D-160) · adversarial-audit closes (D-163–D-165) · super-dimming
     relabel + MaxBright auto-raise parity (D-168/D-169) · stale User Guide copy.
     **Repo hardening:** `scripts/ladder.sh` one-command acceptance + guards (STATE caps,
     ledger rollover, citation integrity + `[cited]` sync, skip-ci scan, changelog cap,
     rule-review tripwire, redaction self-test, secret-shape commit scan, command-rail
     self-test) + the instructive pre-execution command guard (PreToolUse hook, DA-009),
     with its own regression suite, run by CI (D-161/D-166/D-173/D-174/DA-006–DA-009) · Owner queue +
     ask-don't-assume (D-167) · secret hygiene: prose + `redact.sh` filter + leak-response
     protocol + instruction hierarchy (D-175/DA-006/DA-007) · Gradle parallel/config-cache.
     **Agent-agnostic harness (D-176/DA-001…DA-008):** `AGENTS.md` pointer stub + neutral
     `scripts/session-start.sh`; `.claude/` = thin adapter; ledger 1000-line rollover (base
     closed at D-176, `_A.md` live); branch-train codified; fresh-context glue/rule review.
     **Release:** vc18 / 1.8.0; changelog 488 B.
2. After CI green: on-device pass of `DEVICE_TEST_SCRIPT.md` **§§12–15** (TalkBack,
   automation, insets, force dark); findings → **Incoming findings** below.
3. Cut **v1.8.0 / vc18** from `main` via the Release UI.
4. **Server-side rails (DA-006):** verify/enable on GitHub — `main` branch protection (PRs
   required; force-push + deletion blocked) and secret-scanning push protection. Adapter
   deny rules bind only agents that load them.

**Open questions:** (none)

**Incoming findings:** (none)

## Decided non-items (don't re-litigate without new evidence)

- **Repo/process (2026-06/07):** root `CHANGELOG.md` (redundant with STATE + fastlane + the
  ledger); speculative dependency bumps (only on a security advisory); standalone doc-drift
  audit (RUNBOOK self-adaptation covers it); action SHA-pinning / Gradle dependency
  verification (2026-06-29, wrong cost/benefit solo); widening build.yml to `claude/**`
  push events (2026-07-10 — PR-time CI + the local ladder suffice, D-161).
- **External AI-review triage #1 (declined 2026-07-10, reasons in D-162):** glue-review
  checkbox output; ledger active-file symlink/marker; session-start delta generator;
  platform contract tests (exist — D-136/D-148); tracking-id branch names.
- **YAML codification of RUNBOOK/state (declined 2026-07-13):** checkpoint manifest,
  glue-review YAML block, per-playbook test matrices — re-litigates the D-162 checkbox
  decline (Goodhart). Enforcement derives from artifacts produced anyway, never from
  artifacts produced to pass the check.
- **External AI-review triage #2 (declined 2026-07-20, accepts + reasons in DA-006):**
  verification manifests / `.ladder` summaries + machine session header in STATE
  (re-litigates 2026-07-13); generated ledger index (grep IS the index);
  metrics dashboard (owner-effort budget); dependency SHA-pinning playbook (re-litigates
  2026-06-29); harness scaffold CLI / profiles / example repo (owner call if
  ever published standalone); ledger `Status:` retrofit (row-prose supersession notes,
  now codified); Owner-queue aging guard (date-stamps suffice). (The local secret-pattern
  guard decline was owner-reopened same day → ladder guard 9, DA-008.)
- **Privileged Display (decided at Segments 4.5–5, D-150–D-152):** per-toggle orthogonal
  scheduling (removed by the D-151 pivot — scheduling IS "a Contexts rule loads a profile
  carrying display fields", winner-takes-all); persisted last-applied seed for
  `DisplayTogglesCoordinator` (re-introduces the latch persistence the pivot removed;
  revisit on real-world reports); QS tile / notification action for grayscale
  (profiles/Contexts are the switching surface); refresh-rate forcing / OEM alternate keys
  (OEM-fragmented, D-048/D-149); manual Extra-Dim toggle (pipeline-owned, D-144/D-149).

## Changelog

One line per shipped change (newest first); detail in the D-rows and git history.

- 2026-07-21 — **DA-009** (owner-relayed external insight): instructive pre-execution
  command guard — agent-neutral `scripts/command-guard.sh` (force-push / push-to-main /
  env-dump rails, deny reasons the agent self-corrects from), wired as the Claude Code
  Bash PreToolUse hook; self-test = ladder guard 10; joins guard-7 legislation list +
  D-176 adapter checklist. Static deny rules + prose stay as layers beneath it.

- 2026-07-20 — **DA-008** (owner-reopened): secret-shape commit guard — ladder guard 9
  scans worktree (NUL-safe list) + staged blobs via `redact.sh --scan`/`--scan-staged`
  (scan = filter, drift-free; value-free output, now test-asserted); found + fixed two
  format-valid fixture literals in DA-007's own self-test; supersedes the triage-#2
  decline. Rule-review: 1 blocker (word-split file skip) + 7 more, all adopted.
- 2026-07-20 — **DA-007** (owner-requested): mechanical secret redaction —
  `scripts/redact.sh` (known token shapes → `[REDACTED:<class>]`; 16-case self-test run by
  new **ladder guard 8**; joins guard 7 + rule-review scope); adapters pipe output through
  it where an output-filter hook exists (Claude Code today: manual-pipe only).
- 2026-07-20 — **DA-006** (owner-approved) external-review triage #2: instruction
  hierarchy, leaked-credential incident protocol (the ONE history-rewrite exception),
  server-side-rails owner item, ladder guard 7 rule-review tripwire, self-adaptation
  boundary, harness v1.1 polish. Declines above; detail in the row.
- 2026-07-10..19 — **D-161**–**D-176 + DA-001…DA-005**: repo hardening (`ladder.sh` guards +
  their test suite, CI runs the ladder, deny rules, guarded Owner queue, secret hygiene);
  external-review triage #1; final adversarial audit closed; parity/relabel fixes;
  context-load write-through; force dark; agent-agnostic harness (AGENTS.md stub, neutral
  session-start, ledger line-cap rollover — base closed at D-176); branch-train codified;
  fresh-context glue + rule review; STATE-guard hysteresis.
- 2026-06-23..07-09 — **v1.0.0 Tasker→Kotlin rebuild complete** (Gate 3; history frozen in
  `../history/`) → **1.7.0/vc17** → 1.8.0 features/close-out: D-096–D-160 — SDK 36/JDK 21/
  CodeQL, release CI, hardening + mandatory glue review, F-backlog, **Privileged Display
  Control** (D-151 pivot), intent control, a11y + crash-log capture, IME/RESUME fixes.
