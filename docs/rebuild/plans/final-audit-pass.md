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

**Agent A (`:app` runtime glue) — delivered.** One ranked finding + one declined observation;
everything else audited clean (list in its report; includes ControlReceiver/D-160, ProfileApplier,
STATE_CHANGED, panic D-139 ordering, D-140 gates, D-144/D-108 latches — all verified sound).

- **A1 (MED): ContextEngine — stale location/app signal snapshots on rule removal (D-142
  asymmetric sibling).** `refreshSignalListeners` (ContextEngine.kt ~217-222) cancels the
  location/app listeners WITHOUT clearing `signalSnapshot.lat/lon` / `.app` (and never resets
  `lastLocEvalLat/Lon`), while the wifi sibling `stopWifiListener` (~284-288) DOES clear its
  snapshot (D-142, pinned by `wifiListenerStop_clearsStaleSsid_D142`). Scenario: delete a "near
  Home" location rule → drive to Work (no listener → snapshot stays Home) → re-add the rule →
  `evaluate(RESUME)` matches the STALE Home coords and applies the Home profile until a fresh fix
  lands. Same class for the foreground-app poll. Fix: mirror the wifi clear (lat/lon → 0.0 "no
  fix" — `haveFix` already treats (0,0) as none; app → ""), null `lastLocEvalLat/Lon`; add the
  missing sibling tests.
- **A-obs (LOW, declined by agent as OS-constraint):** `AutoBrightnessRuntime.sendServiceAction`
  swallows background-start `IllegalStateException` (incl. `ForegroundServiceStartNotAllowedException`)
  for PANIC/PAUSE/RESUME/REAPPLY without `markDegraded`, unlike `startMonitoring` — an external
  PANIC from a background-restricted state is dropped silently.
- Agent A did NOT fully reach: ContextEngine PASS-1/PASS-2 veto math, CircadianWindowProvider,
  PowerDrawCalibrator, AppProfileCatalog, debug emitters, ContextSchedule time-wake edges.

**Agent B (`:platform`) — delivered.** One ranked finding + two known/theoretical; all other
adapters audited clean (incl. D-034a/b echo + round-trip, D-143 guards, D-145 ShizukuShell,
SecureDisplayController multi-key writes, D-148 PowerMeter).

- **B1 (MED): PanicGate re-arm latch clears on shake-induced orientation flicker.**
  `PanicSensorSource.kt` ~:240 feeds `gate.canArm(armed, detector.isUpsideDown)` with the
  INSTANTANEOUS filtered orientation, which the window logic itself documents as flickering
  during a vigorous same-axis shake (~224-230) — so after a timed-out window, shaking while
  still inverted can clear `consumed`, re-open a fresh window and fire a REAL panic without the
  contract's flip-straight re-arm (class doc ~104-112; `PanicGateTest` only sees clean
  transitions). Fix: debounce the "sustained straight" signal (N consecutive non-inverted
  frames / hysteresis) to match the window's flicker-immunity.
- **B2 (known):** `ShizukuGrantGateway.bindAndGrant` bind-timeout residual — explicitly accepted
  in the D-145 row; no action.
- **B3 (LOW/theoretical):** `BatteryStateReader` `EXTRA_TEMPERATURE` defaults to 0 (a real-looking
  0.0 °C) instead of a D-108-style absent sentinel; extra is present in practice on real devices.

**Agent C (settings/persistence/UI/domain boundaries) — FAILED mid-audit** (account session
limit), no report delivered. Partial signal from its last visible step: it believed it had found
a **"draft-apply bug"** naturally triggered via the Misc min/max wait sliders (minWait 1..99,
maxWait 2..100 — cross-field constraint?) and was about to check backup rules
(`dataExtractionRules`) vs DataStore. **Its whole scope must be re-audited**; chase the
draft-apply + minWait/maxWait hint first.
