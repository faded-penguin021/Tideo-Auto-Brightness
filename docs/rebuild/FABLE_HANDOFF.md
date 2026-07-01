# Fable handoff — a plan for writing the real plan

> **What this file is.** Not a task list to execute. It's a brief for the next **Fable** session,
> written under two binding constraints: (1) Anthropic export-control restrictions cut the prior
> Fable stint short, and (2) account usage is capped at 50%/week through **2026-07-07**, after
> which this may run API-tier only — tokens are scarce for an indefinite stretch. The ask that
> produced this file: review project state + the deviations made so far (primary), then plan
> something durable that does **not** depend on Fable (or any one model) being present to finish
> or stay correct (secondary). Delete this file once its two jobs below are done and folded into
> `STATE.md`/`RUNBOOK.md` — it isn't meant to become a third permanent doc.

## Constraints the *real* plan must satisfy

1. **No parallel subagents.** The owner is on a Pro plan. The last time this repo's work used
   concurrent `Agent`/Task fan-out, every subagent hit the rate limit before any of them returned
   — an entire 5-hour window burned for zero output. Whatever Fable plans must run **one agent (or
   just direct tool calls) at a time**, foreground, sequentially. Prefer no subagent at all for
   small well-scoped edits; a subagent is for something genuinely too large for the main loop, not
   a parallelism trick.
2. **Assume the executor is a lesser model, possibly mid-context-compaction, possibly not Fable at
   all.** This project's own history is the evidence for why that's dangerous unmitigated: every
   Sonnet-authored code segment (S5, S7) **passed its own acceptance gate** and still shipped a
   real bug that only a dedicated review pass caught — S5 → **D-030** (gate-polarity +
   insertion-order bugs), S7 → **D-034** (suppress-echo race + OEM rounding drift). The project's
   fix at the time was a model-tier policy (**D-035**: Opus from S9a on). That fix isn't reliably
   available to you now (tokens/tier are the whole problem). So the mitigation has to be
   **structural**, not "use a smarter model": keep each unit of work small and self-contained,
   give each one a concrete automated acceptance check that would actually have caught the failure
   mode in question (a truth table for gate polarity, an ordering assertion, a race-condition
   regression test) — never "looks right on read-through" as the bar.
3. **Stay out of `:domain/`, the golden vectors, and the runtime concurrency model** unless a unit
   explicitly targets them. As of this writing the project is at zero parity gaps
   (`parity_gaps.md`, all 7 closed at S5), zero pending rows in `PARITY_CHECKLIST.md`, zero
   TODO/FIXME, and the acceptance ladder is green (v1.6.0, `versionCode 14`). That invariant is the
   single most valuable thing in the repo and the most expensive to re-verify with weak execution
   — don't spend the scarce budget re-touching it defensively; spend it on lower-blast-radius work
   (candidates below) precisely so a weaker model executing alone is safe.
4. **Checkpoint every unit.** Ladder green → `STATE.md` Changelog line → commit → push to the
   session's assigned branch, per unit — never batch several risky changes into one
   uncheckpointed session. A session that gets cut (export control, rate limit, compaction) mid-
   unit should lose at most that one unit, never the whole backlog, and the next session (any
   model) should be able to pick up from git + `STATE.md` alone with no other memory.

## Step 1 (primary) — review pass, budget-consciously

- Read `docs/rebuild/STATE.md` in full (~200 lines) — it's the live truth of what shipped and
  when. `docs/history/STATE_rebuild.md` is the **frozen** migration narrative (~109K tokens) —
  do not read it wholesale; it's cited from the ledger only for archaeology, not for forward
  planning.
- Skim the **last ~10–15 entries** of `docs/rebuild/DEVIATIONS_LEDGER.md` (it's permanent and
  append-only; most of it is settled migration history you don't need). Grep for a specific `D-NN`
  only when a doc cites it.
- Re-confirm, don't assume, the numbers in constraint 3 above are still true (`grep -c pending
  docs/rebuild/PARITY_CHECKLIST.md`, tail of `parity_gaps.md`, the acceptance ladder). They were
  true at 2026-07-01 / v1.6.0; they may have moved by the time you read this.
- Write the finding as a short note (a few lines, not a new doc) — if everything above is still
  green, say so plainly and move to Step 2; if something's drifted (a red ladder, a re-opened gap,
  a contradiction between two ledger entries), that becomes the first, highest-priority unit of
  the real plan, ahead of anything below.

## Step 2 (secondary) — durable hardening backlog

A menu, not a mandate. Pick what's still relevant, drop what's stale, order by value ÷
risk-of-weak-execution. Each bullet is meant to become one or more RUNBOOK-style units: a
one-paragraph spec, the files it touches, and its acceptance command — small enough to checkpoint
per constraint 4.

1. **`SECURITY.md` + root `CHANGELOG.md`.** Neither exists today. `SECURITY.md` (who to contact,
   no bug bounty, response expectations) is standard open-source hygiene and pure docs — zero
   code risk, good first unit to prove the checkpoint discipline. A root `CHANGELOG.md` that
   indexes `STATE.md`'s per-version Changelog lines (or just links to it) helps external
   contributors/F-Droid reviewers without duplicating the source of truth.
2. **`.github/dependabot.yml`, scoped narrow.** The 2026-06-29 CI-hygiene pass explicitly
   *declined* strict dependency verification, action SHA-pinning, and a Gradle versionCode task as
   wrong cost/benefit for a solo F-Droid app — don't re-litigate that call. What wasn't proposed:
   a plain Dependabot config for the `gradle` and `github-actions` ecosystems, PRs-only (no
   auto-merge) — visibility with zero automatic behavior change, since `release-preflight.yml`
   still gates every PR regardless. Cheap, reversible, low ambiguity.
3. **F-Droid fit-and-finish, repo side only.** The actual `fdroiddata` submission is an owner step
   in a repo this session can't touch — but this repo can prepare for it: check the assembled APK
   for reproducible-build friendliness (no embedded absolute build paths/non-deterministic
   timestamps), and check whether the opt-in `ipwho.is` geo-IP fallback (default off, D-105/D-121)
   needs any F-Droid-facing disclosure. Investigation-first; likely lands as a small docs or build
   config change.
4. **Test-coverage backfill on known-thin spots.** E.g. `BrightnessTileService` only has an
   "instantiation smoke" test (Robolectric can't bind a QS tile) — the pure `tileSubtitle()`
   extracted in S12.7b is a good target for exhaustive branch-case tests. Additive, test-only —
   a model can't regress behavior by adding assertions to a pure function it isn't editing.
5. **Accessibility pass.** D-131 got hardcoded strings to zero (i18n-ready) but didn't audit
   TalkBack semantics or touch-target sizing. Doable per-screen (one screen = one unit = one
   commit), each with a Compose semantics-tree test as the acceptance check.
6. **Explicitly not recommended right now:** anything in `:domain`, the runtime concurrency model,
   or the release/versioning process — all stable, all high-blast-radius, none of it is what
   "hardens for the long term via a weaker model" means. Leave them alone unless Step 1 found a
   live regression there.

## What "done" looks like for this handoff

- Step 1's finding is written down (a few lines — extend `STATE.md` "Active work", don't create
  a fourth doc).
- Step 2's chosen items are turned into a small ordered backlog, RUNBOOK-playbook style.
- If tokens allow *in this same session*, execute the first (cheapest) unit fully — commit, push —
  as proof the checkpoint discipline holds, then stop. Leave the rest as the plan for whoever
  (whatever model) picks this up next.
- Delete this file when the backlog has a permanent home (fold the still-relevant parts into
  `RUNBOOK.md`/`STATE.md`; this file's job is done once a real plan exists).
