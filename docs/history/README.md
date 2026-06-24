# docs/history — frozen record of the Tasker → Kotlin rebuild

This folder is a **frozen archive** of the one-time migration that produced this app. The
migration is **complete and shipped: v1.0.0, Gate 3 signed off 2026-06-23.** Nothing here is
maintained going forward — it is kept so future AI-assisted changes can learn from the
hard-won decisions and **not repeat already-solved mistakes**.

## How the Tasker project became this app

The source was the Tasker project `Advanced_Auto_Brightness_V3.3` (a 1.6 MB XML export of
profiles, tasks with embedded Java, and scenes). Over segments **S0–S14** it was extracted to
ground-truth specs, then rebuilt as a native **Kotlin/Compose** app with feature parity:
pure-JVM `:domain` math/decision logic golden-tested against transcribed Tasker references,
an Android `:platform` adapter layer, and a Compose `:app` with a foreground-service runtime,
QS tile, and boot receiver. Three on-device human gates (core loop, surfaces & tiers,
acceptance soak) all passed.

## Contents (frozen)

- **`RUNBOOK_rebuild.md`** — the original segment briefs (S0–S14 + sub-segments), the execution
  DAG, and the global failure protocol used to drive the migration.
- **`STATE_rebuild.md`** — the migration's cross-session memory: segment log, the layered
  "Current state" punch-list history, gate findings, and check results.

> **The deviations ledger is NOT here — it lives at `docs/rebuild/DEVIATIONS_LEDGER.md`.** ⭐ It
> is a **permanent, append-only registry** (not frozen): ~96 numbered corrections to earlier
> wrong assumptions (e.g. embedded Java is action code 474 not 598; `task661` holds no Java;
> ConditionList children are alphabetical in the XML; the single-coroutine drop-on-reentry
> concurrency model), and the home for all future deviations (D-096+). Code cites bare `D-NN`,
> so it is never compressed or deleted. **Read it before touching ported pipeline logic.**

## Caveat — frozen, may drift

These files describe the app **as it was built**. Code and golden vectors are ground truth; if
anything here conflicts with the current code, **trust the code**. For live, forward-looking
docs see `docs/rebuild/RUNBOOK.md` (maintenance playbook), `docs/rebuild/STATE.md` (current
project state), and the live reference docs (`extraction/`, `XML_RECIPES.md`,
`PARITY_CHECKLIST.md`, `screen_map.md`) that remain in `docs/rebuild/`.
