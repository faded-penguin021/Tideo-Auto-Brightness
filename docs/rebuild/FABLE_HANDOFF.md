# Fable handoff — orientation, not a plan

> Not a task list. Written by a Sonnet session under two facts: export-control restrictions cut
> the prior Fable stint short, and account usage is capped at 50%/week through **2026-07-07** (API
> tier after) — tokens are scarce and this session may get cut too. The ask: review project state
> and the deviations made so far, then decide what durable hardening work is worth doing and plan
> it. The plan is yours to reason out; this file exists so you don't have to re-derive orientation
> from scratch on a tight budget. Delete it once you've written the real plan somewhere permanent.

## Two operational facts, not design constraints

- **No parallel subagents.** The last time this repo's work fanned out concurrent `Agent`/Task
  calls, every one hit the rate limit before returning — a full 5-hour window burned for nothing.
  Whatever you plan, execute it one agent (or direct tool call) at a time.
- **This session may end mid-work.** Whatever unit you're on, get it to a state where `git log` +
  `STATE.md` fully explain it before moving to the next — ladder green, Changelog line, commit,
  push. Don't let "the plan" only exist in your head or a half-finished working tree.

Everything else — what's worth hardening, how big a unit should be, whether a given change needs
`:domain`-level care or is safe for a quick pass — is your call. You have more room to reason about
this codebase than a cold read of a prescriptive backlog would leave you; use it.

## Where to look, so the reasoning is cheap

- `docs/rebuild/STATE.md` (~200 lines) — full read, it's the live truth of what shipped and when.
- `docs/history/STATE_rebuild.md` is the **frozen** migration narrative (~109K tokens) — don't
  read it wholesale; it's for archaeology on a specific question, not forward planning.
- `docs/rebuild/DEVIATIONS_LEDGER.md` is permanent/append-only and mostly settled migration
  history — tail the last ~15 entries for recent decisions, grep a specific `D-NN` when cited.
- Sanity-check rather than assume: `grep -c pending docs/rebuild/PARITY_CHECKLIST.md`,
  `parity_gaps.md`, the acceptance ladder. As of 2026-07-01 / v1.6.0 all three were clean (0
  pending, 0 open gaps, green ladder) — worth a fast re-check since it may have moved.
- Worth knowing going in: the ledger records two cases (S5 → **D-030**, S7 → **D-034**) where a
  Sonnet-authored segment passed its own acceptance gate but still shipped a real bug that only a
  dedicated review pass caught. Read those two entries if you want the specifics; draw your own
  conclusion about what that implies for how you size and gate the work you plan, given you can't
  assume a review pass or a stronger model will be looking over anyone's shoulder this time.
- No `SECURITY.md`, root `CHANGELOG.md`, or Dependabot config exist in the repo as of this
  writing, if that's useful signal. The 2026-06-29 CI-hygiene pass declined stricter supply-chain
  measures (SHA-pinning, dependency verification) as wrong cost/benefit for a solo F-Droid app —
  worth reading that entry before proposing something similar, not to avoid it necessarily but so
  you're disagreeing with a reasoned prior decision on purpose rather than by accident.

## What to leave behind

Write down (in `STATE.md` or wherever fits) what you reviewed and what you concluded, and the plan
itself in whatever shape makes it executable by the next session — which may not be you and may
not be Fable. If you get through a first unit of work in this same session, checkpoint it properly
and stop rather than pushing further uncheckpointed; leave the rest as a plan, not a memory.
