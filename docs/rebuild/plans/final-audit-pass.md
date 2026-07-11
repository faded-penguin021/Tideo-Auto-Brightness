# Plan: final adversarial audit pass (pre-1.8.0-merge bug hunt)

> Playbook-5 persisted plan (RUNBOOK). Owner directive 2026-07-11: one last deep review pass —
> "identify real bugs/breakage/parity issues" and fix what is verified. Session constraint set by
> the owner: **max 3 parallel subagents while auditing, max 2 while executing fixes** (a one-off
> override of the D-161 sequential-only rule, for this audit only). Fix units themselves still
> land sequentially per D-161 (test-first → ladder → STATE line → commit → push).

## Status (checkpointed 2026-07-11, session `claude/fable5-api-tier-migration-cmqg5d`)

- Branch = `53a37e6` (identical to `claude/fable-model-improvements-3r07qs` tip — the 1.8.0
  superset that becomes the single release PR). Working tree clean.
- **Baseline: full 5-rung ladder GREEN in-session** (7m24s, `147 actionable tasks`) at `53a37e6`.
- **Coordinator's independent review — CLEAN, no defects found** in: `ControlReceiver` (gate-first
  + D-160 RESUME gate verified), `ControlPrefsStore`, `ProfileApplier` (verbatim extraction),
  `AppProcessScope.goAsync` (finally + supervised scope), `AabApplication`/`CrashLogStore` (D-158
  ring: always-delegates + idempotent install verified), `AppDataStores` (all singletons — repeated
  `AppModule()` construction safe), `AndroidManifest` exported surface (D-147/D-157 split correct),
  `AmbientMonitoringService` (D-140 gates, F74/F75/F76 notification semantics, U5 STATE_CHANGED
  publisher incl. onDestroy cache), `DisplayTogglesCoordinator` (cancel-then-mutex ordering sound;
  documented D-151 trades intact), `AutoBrightnessRuntime`, D-159 three-part IME fix (all three
  parts present and coherent).
- **3 Explore audit agents** were launched (scopes: A = `:app` runtime glue; B = `:platform`
  adapters; C = settings/persistence + UI wiring + domain boundaries + manifest/res), each armed
  with the RUNBOOK glue-review bug-class list. Session-limit wrap-up was requested; their findings
  (possibly partial) are appended below when received.

## Next session: resume protocol

1. Read this file + the findings section below. Triage each finding per RUNBOOK: verify against
   code + the cited D-row + existing tests before believing it (agents were told odd behavior may
   be intentional Tasker parity — re-check anyway; Explore reports are leads, not verdicts).
2. Fix only VERIFIED bugs, as playbook-4 units (failing test first → fix → ladder green →
   STATE.md line → commit → push; glue-review pass on any `:platform`/runtime diff). Max two
   parallel subagents if any are used; units land sequentially.
3. Anything triaged as not-a-bug or intentional: record the verdict below (or a D-row if durable)
   so it is not re-investigated.
4. At audit close: delete this plan file (its durable content moves to STATE.md Changelog +
   D-rows), remove the STATE.md Active-work pointer, final ladder, push.

## Audit-agent findings (raw, UNTRIAGED — leads, not verdicts)

_Pending at checkpoint time; appended verbatim-condensed when the agents reply._
