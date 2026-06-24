# STATE — project state & session memory

> **Length guard (read before editing).** Steady-state target ≤ 12 KB. **If this file exceeds
> 32 KB, aggressively compress before committing:** collapse each completed *Active work* stage
> into one Changelog line, move any durable gotcha to `../history/DEVIATIONS_LEDGER.md` (or the
> relevant live reference doc), and delete narrative/punch-list prose. The **Project** and
> **Current state** sections must always survive compression. The full migration history is
> already frozen in `../history/` — do not re-accumulate it here.

## Project

Native **Kotlin/Compose** Android app that is a feature-parity rebuild of the Tasker project
`Advanced_Auto_Brightness_V3.3`. Three modules: **`:domain`** (pure-JVM math/decision logic,
golden-tested), **`:platform`** (Android system adapters behind small interfaces), **`:app`**
(Compose M3 UI, DataStore `AabSettings`, foreground-service runtime, QS tile, boot receiver).
Privilege tiers: **BASIC** = user-grantable `WRITE_SETTINGS` (full core pipeline); **ELEVATED**
= `WRITE_SECURE_SETTINGS` via one-time `pm grant` (super dimming). minSdk 31, target/compile 35.

## Current state

**Shipped: v1.0.0** (`versionCode 3`), migration complete — all three on-device human gates
passed (Gate 1 core loop, Gate 2 surfaces & tiers, Gate 3 acceptance soak, signed off
2026-06-23). `PARITY_CHECKLIST.md` is zero-`pending`; golden parity tests green; TODO/FIXME = 0.
No active work in flight.

How changes are made now: see `RUNBOOK.md` (change-type playbooks). Full rebuild record + the
~96-entry deviations ledger are frozen in `../history/`.

> Code/docs elsewhere cite migration deviations by number (e.g. `STATE.md D-048`, `F50`). Those
> entries now live in `../history/DEVIATIONS_LEDGER.md` (and `../history/STATE_rebuild.md` for
> gate findings) — look there.

## Active work

*(none — base state. Populate this section only while a change needs multiple stages; clear it
to a Changelog line on completion.)*

When in use, track stages here:

| Stage | Date | Status | Notes |
|---|---|---|---|

**Blockers:** none.

**New deviations (this work):** none. *(Record durable facts future sessions need; promote
lasting ones to `../history/DEVIATIONS_LEDGER.md` when curating.)*

## Changelog

One line per shipped change (newest first). Keep terse.

- 2026-06-23 — v1.0.0: Tasker→Kotlin rebuild complete; Gate 3 signed off. Full history frozen
  in `../history/`.
