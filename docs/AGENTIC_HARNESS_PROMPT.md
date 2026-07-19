# The Agentic Maintenance Harness — a generalized, reusable prompt

This document generalizes the maintenance harness used in this repository (the constitution
file + STATE.md + RUNBOOK.md + DEVIATIONS_LEDGER + `scripts/ladder.sh` + session bootstrap)
into a prompt you can apply to **any** repository maintained by agentic AI sessions — any
agent, any model vendor — with strong human-in-the-loop feedback. It has three parts:

- **Part 1 — Design principles.** The extracted logic: *why* each mechanism exists. Read this
  to adapt the harness intelligently rather than cargo-culting it.
- **Part 2 — The prompt.** A drop-in, placeholder-parameterized operating prompt (the target
  repo's always-loaded agent-instructions file — `AGENTS.md`, `CLAUDE.md`, or your agent's
  equivalent). This is what the agent reads every session.
- **Part 3 — Scaffold templates.** The supporting artifacts the prompt refers to: STATE file,
  RUNBOOK, append-only ledger, guard script spec, session hook, and permission rails.

Placeholders use `{{DOUBLE_BRACES}}`. Instantiate them for the target repo; delete anything
that genuinely doesn't apply, but read Part 1 first — most pieces earn their keep only in
combination.

---

## Part 1 — Design principles (the extracted logic)

**P1. Declare a ground-truth hierarchy.** Code + immutable test fixtures outrank every
document. Docs describe the system as-built and *will* drift; the standing order is "when a doc
conflicts with the code, trust the code and correct the doc." Without this rule, agents
oscillate between conflicting sources or "fix" correct code to match a stale doc.

**P2. Tier memory by mutability and size, and bound every tier that agents must read.**
Long-context repos die by unbounded accumulation. Split memory into four tiers:

| Tier | Artifact | Mutability | Size discipline |
|---|---|---|---|
| Constitution | The agent-instructions file (the Part-2 prompt) | Stable; edited rarely, deliberately | Small by construction |
| Working memory | `STATE.md` | Rewritten freely | **Machine-enforced cap** (warn/fail), compression protocol, protected sections |
| Permanent memory | Numbered append-only ledger | Append-only, never compressed | Per-file line cap + rollover to sibling files |
| Archive | `docs/history/` | Frozen; consult, never extend | Unbounded but never on the hot path |

The key move: history that must never be lost goes to the *ledger* (append-only, citable);
narrative that has served its purpose goes to the *archive*; only the small working set stays
in the file every session reads first.

**P3. Machine-check everything checkable — but only over artifacts the work produces anyway.**
Guards verify diffs, file sizes, commit messages, citation cross-references — things that exist
as a side effect of doing the work. Never invent self-reported attestations (checkboxes,
"I reviewed this" YAML, per-checklist-item line quotes): an agent can emit those without doing
the work (Goodhart) — external reviewers re-propose these regularly; keep declining. If a rule
can't be derived from a real artifact, it stays a prose rule plus reviewer attention.

**P4. One verification entrypoint, shared by CI and local *by construction*.** A single
`scripts/ladder.sh` (guards, then the full test/build/lint set) that CI invokes directly. No
hand-maintained lockstep between "what the agent runs" and "what CI runs" — divergence there is
where "green locally, red in CI" mysteries breed. Provide a `--guards-only` fast path for
docs-only work, and test the guards themselves with a fixture suite (guards are code).

**P5. Checkpoint invariant: assume the session dies at any moment.** Every unit of work ends
*acceptance green → state-file changelog line → commit → push* before the next unit starts. An
interrupted session (rate limit, context window, crash) loses at most the unit in flight.
Corollaries: work strictly sequentially (no parallel subagents on one repo — they have burned
whole usage windows), keep units small (≈ one focused hour) and independently shippable, and
give each a **binary** acceptance check — tests or a scripted comparison, never "looks right".

**P6. Assume the weakest agent.** Write every rule so it works if the session runs on a lesser
model and is cut off mid-task. Prefer mechanical steps with hard gates over judgment calls.
Tell the agent explicitly: *you are the last reviewer; there is no stronger pass behind you.*

**P7. Recovery is a protocol, not improvisation.** When a unit goes wrong, don't flail
forward: reset to the last green checkpoint (`git reset --hard HEAD` + careful clean), re-run
the ladder to confirm green, re-attempt smaller. If the dead end taught a durable lesson,
record it *before* retrying. Pushed checkpoints are immutable — recovery never rewrites pushed
history (and force-push is denied at the permission layer, not just in prose — see P13).

**P8. "Ask, don't assume" — route owner-judgment forks to a queue, don't guess.** Some forks
are the human's to resolve: irreversible/expensive-to-unwind changes, user-visible behavior
with no specification to appeal to, version-semantics ambiguity, process/policy reshaping. The
agent stops at the last green checkpoint, records the fork under the state file's **Owner
queue → Open questions** (options + a recommendation with reasons), then moves to independent
work. Routine engineering judgment inside a unit's stated scope is *not* a fork — the rule
exists for decisions the owner would want to make, not as cover for avoiding decisions the
agent should make.

**P9. The Owner queue is the bidirectional human-in-the-loop channel.** One protected section
in the state file holds: **Pending owner actions** (things only the human can do — merging,
tagging, on-device/production verification), **Open questions** (P8), and **Incoming
findings** (the intake where the human drops manual-test results — guaranteed to be seen,
because reading the state file is protocol step 1 of the next session). Items leave the queue
only when done/answered/triaged, with the outcome recorded as a changelog line or ledger row.
Every session's **final chat message restates the queue** so the human never has to open the
file to know what's pending. A guard warns if a compression pass deletes the section.

**P10. Keep negative memory: "Decided non-items."** A standing list of things considered and
rejected, with dates and reasons ("don't re-litigate without new evidence"). Agents — and
external AI reviewers — endlessly re-propose plausible-sounding ideas the owner already
declined; this section is the vaccine, and it's cheaper than re-arguing each time.

**P11. Citations bind code to permanent memory — and a machine enforces both directions.**
Code comments cite ledger entries by bare ID (`D-042`); a guard verifies every ID cited from
source resolves to a ledger row, that row numbers are unique, and that rows cited from code
carry a machine-synced `[cited]` marker (so anyone reading the ledger knows which rows are
load-bearing before rewording them). Where code ports behavior from a reference system, add
**provenance comments** naming the exact source artifact and location. Never cite ephemeral
artifacts (plan files, chat) from code — cite only artifacts guaranteed to outlive the change.

**P12. Adversarial self-review, seeded by your own bug history.** Test suites can't see all
code (in this repo: platform/runtime "glue" invisible to golden vectors). For diffs touching
those areas, mandate a second, *hostile* read of the full diff after tests pass — hunting a
concrete checklist of bug classes, each one a real shipped bug from the ledger (gate polarity,
insertion order, observer echo races, non-idempotent lifecycle, stale async completions, …).
The ledger feeds the checklist: every new shipped bug class gets appended; when a class turns
out to be mechanically testable, encode it as a regression test and retire it from the
checklist — the pass holds only what tests *cannot* see. Verdict goes in the commit body
("glue-review pass: clean"); findings get fixed pre-commit and ledgered if durable.

**P13. Hard rails in the permission layer; discipline in prose.** Denials that must never be
crossed (force-push, pushing to the default branch) live in tool-permission deny rules —
enforced even if the prose is forgotten. Pre-allow the verification commands so the agent
never stalls on a permission prompt for the ladder. One session = one dedicated branch; the
human merges via squash (one commit per branch on the default branch keeps intra-branch churn
out of history — and makes mid-feature pivots cheap, because abandoning a checkpointed segment
costs nothing on `main`).

**P14. Initialization is one agent-neutral script, idempotent, with background warm-up.** A
single `scripts/session-start.sh` that any agent's hook mechanism invokes (and that an agent
with no hook support runs manually — say so in the instructions file): (a) bootstraps the
toolchain idempotently (instant when cached), gated on an explicit remote-environment flag —
never a heuristic that could surprise a developer machine; (b) launches build-system warm-up
in the background so the first ladder run doesn't pay the cold cost serially while the agent
reads docs; (c) verifies the checked-out branch and warns on `main`/detached HEAD (the first
misplaced commit is the expensive one); (d) prints the protocol pointer ("read STATE first,
then the RUNBOOK playbook"). The script self-locates its repo root — it must not depend on
any one agent's environment variables.

**P15. Process docs are code — self-adapting, in the same change.** If the RUNBOOK is wrong,
stale, or missing the case just handled, fixing it is part of the change, not a follow-up. The
runbook must always describe how changes are *actually* made now.

**P16. Multi-session features use provisional persisted plans.** An owner-approved plan file
plus a checklist mirrored in the state file; segments run sequentially, each ending shippable
(P5). Treat the plan as provisional — the owner may pivot mid-feature; per-segment checkpoints
are what make removal of an entire segment cheap. At the final segment **delete the plan
file**: by then its durable content must live in changelog lines + ledger rows (P11: code
never cites the plan — it dies; the ledger doesn't).

**P17. Secrets are write-only to the agent.** Session environments carry credentials (VCS
tokens, proxy auth, deploy keys) even when the codebase ships none. Never dump environments
(`env`, `printenv`, `.env` files, container/service inspect output, unredacted config dumps);
never print a credential's value, prefix, suffix, length, or hash — report only fixed-key
presence ("`DATABASE_URL` is set") and bounded counts, and redact subprocess/exception/API
output before reasoning over it. If a diagnostic can't be done through a redacted path, stop
and request a narrower evidence contract via the Owner queue (P8 applied to secrets) — never
default to raw output. Credential rotation and auth-config changes are always Owner-queue
items with explicit approval and a rollback plan. Split per P13: the dump commands go in the
permission deny rails; the redaction discipline stays prose. (Adapted from an external
agent-safety protocol — `odysseus-fuzzy`'s AGENTS.md.)

---

## Part 2 — The prompt (drop-in constitution template)

> Instantiate the placeholders, then place this at the repo root as the always-loaded agent
> instructions file. **One file is canonical; every other agent's expected filename is a
> pointer to it, and pointers only point — they never diverge.** `AGENTS.md` is the emerging
> cross-agent default; agents that read a different filename (Claude Code reads `CLAUDE.md`)
> get a short stub referring to the canonical file ("read it in full; if your harness has no
> session-start hook, run `scripts/session-start.sh` yourself"). Which file is canonical
> matters less than the single-source rule — an established repo whose citations all name one
> file keeps that file canonical and points the others at it.

```markdown
# {{PROJECT_NAME}} — maintenance guide

{{ONE_PARAGRAPH_PROJECT_DESCRIPTION: what it is, what it's built with, and its lifecycle
stage — e.g. "shipped v1.0; work is now maintenance: bug fixes, small features".}}

{{IF_THERE_IS_A_REFERENCE_SYSTEM: where the reference/spec artifacts live, and the safe way
to read them (an index/recipes doc), if reading them wholesale is a context hazard.}}

> **Ground truth:** code + {{IMMUTABLE_TEST_FIXTURES, e.g. "golden test vectors"}}. Docs
> describe the system as-built and may drift — when a doc conflicts with the code, trust the
> code and correct the doc.

Long-term memory: numbered deviations/discoveries live in `docs/LEDGER.md` — a **permanent,
append-only registry** (code cites bare `D-NN`; code-cited rows carry a machine-synced
`[cited]` marker; never compress or delete entries; append the next number in the LIVE ledger
file — each file caps at {{LINE_CAP}} lines: the final row may overflow the cap, the next row
opens the next file, `D-… → DA-…` (`_A.md`) `→ DB-…`).

## Maintenance protocol (every session)

1. {{BOOTSTRAP_STEP, e.g. "Run scripts/setup-env.sh if <marker file> is missing."}}
2. Read `docs/STATE.md` — current project state, active/staged work, and the Owner queue.
3. Open the matching change-type playbook in `docs/RUNBOOK.md`; read the reference docs it
   names before touching code.
4. Do the work under RUNBOOK **Session discipline**: sequential, small checkpointed units,
   binary acceptance.
5. Run the acceptance ladder until green. **Never leave the branch red.**
6. Update `docs/STATE.md` (honor its length guard) and, if the runbook itself was
   insufficient, fix the runbook in the same change.
7. Commit and push: `git push -u origin <your-session-branch>`.

## Build & verify commands

```bash
scripts/ladder.sh                # ALL verification in one command, after fast local guards
                                 # (--guards-only for docs-only work)
{{INDIVIDUAL_TEST_BUILD_LINT_COMMANDS, one per line with a one-phrase comment each}}
```

{{VERIFICATION_LIMITS, e.g. "No emulator here — verification = compile + unit tests;
on-device behavior is owner-verified via the Owner queue."}} Every commit body states what
was actually verified and names what could NOT be verified locally — disclosure of real
actions, never implied coverage.

## Architecture

{{SHORT_MODULE_MAP: one bullet per module/layer — what lives there and the invariant that
protects it, e.g. "`:domain` — pure logic, no framework imports, ever; golden-tested."}}

## Coding conventions

- {{SEMANTIC_FIDELITY_RULE, if porting/parity work: "reference semantics win over taste —
  port behavior exactly, including its oddities; modernize the *how*, never the *what*."}}
- Provenance comments on ported/spec-derived logic: `// {{SOURCE}}: <artifact> <locator>`.
- {{FIXTURE_IMMUTABILITY_RULE: "golden fixtures are immutable; production code conforms to
  THEM. Changing one requires proof the fixture was wrong + a STATE.md entry."}}
- No new dependencies unless the change clearly warrants it.
- {{TOOLCHAIN_FLOOR: min supported versions; no legacy branches below them.}}
- Match existing code style and file/package layout.

## Invariants that still bind (full catalog: `docs/LEDGER.md`)

{{THE_SHORTLIST: 3–8 bullets of the invariants agents are most likely to violate —
concurrency model, protected data flows, forbidden shortcuts. Each cites its ledger row.}}

## Secret hygiene

- The session environment carries credentials even though the codebase may ship none. Never
  dump environments (`env`, `printenv`, `.env` files, inspect output); never print a
  credential's value, prefix, suffix, length, or hash — report key presence only. The
  permission rails deny the dump commands.
- A diagnostic that seems to need raw secret material becomes an Owner-queue open question
  (ask for a narrower evidence contract) — never raw output.

## Git rules

- Develop and push **only** on your session's assigned `{{BRANCH_PREFIX}}/<codename>` branch.
  Push with `git push -u origin <branch>` (retry with backoff on network errors only).
  **Never force-push. Never push to `{{DEFAULT_BRANCH}}`.**
- The owner merges session branches via **squash-merge** PRs (one commit per branch on
  `{{DEFAULT_BRANCH}}`). Do not open a PR unless asked. {{TAGGING_RULE: "Tagging/releasing
  stays an owner step."}}

## Agent harness

- This file is the constitution for **any** coding agent; the other agent-instruction
  filenames in this repo are pointers here and must only point, never diverge.
- Session bootstrap is agent-neutral: `scripts/session-start.sh` (remote toolchain setup
  gated on `{{REMOTE_FLAG}}=1`; branch check; protocol pointer). If your harness has no
  session-start hook, run it yourself first.
- Per-agent adapters live in dot-dirs and contain wiring only. A new agent's adapter must:
  run the bootstrap at session start, mirror the permission deny rails (env dumps,
  force-push, push to `{{DEFAULT_BRANCH}}`) if the agent supports permission rules, and
  honor the one-session-one-branch rule above.
```

---

## Part 3 — Scaffold templates

### 3.1 `docs/STATE.md` — working memory (bounded, compressible)

```markdown
# STATE — project state & session memory

> **Length guard (read before editing).** Steady-state target ≤ {{SOFT_KB}} KB. **If this
> file exceeds {{HARD_KB}} KB, aggressively compress before committing:** collapse each
> completed work stage into one Changelog line, move any durable gotcha into the append-only
> ledger, delete narrative prose. The **Project**, **Current state**, and **Owner queue**
> sections must always survive compression (Owner queue items are the owner's to close —
> compress their prose, never drop an open item). (`scripts/ladder.sh` machine-checks this:
> warn > {{SOFT_KB}} KB, fail > {{HARD_KB}} KB.)

## Project
{{FIVE_LINE_SUMMARY — enough that a fresh session needs no other orientation doc.}}

## Current state
{{WHAT_IS_SHIPPED / what is code-complete awaiting owner action / active multi-unit work
with its checklist / "no active work".}}

## Owner queue
> **Protected section.** Never delete this section or silently drop items during compression
> (a ladder guard warns if the header vanishes). Items leave only when done/answered/triaged
> (then: delete, and record the outcome as a Changelog line or ledger row). A session's final
> chat message restates this queue.

**Pending owner actions:** {{numbered list, or "(none)"}}
**Open questions:** {{fork + options + the session's recommendation, or "(none)"}}
**Incoming findings:** {{owner's manual-test results land here, or "(none)"}}

## Decided non-items (don't re-litigate without new evidence)
- {{DECISION — date, what was declined, one-line reason.}}

## Changelog
One line per shipped change or completed unit (newest first). Keep terse; details live in the
cited ledger rows and git history.
- {{DATE}} — **D-NNN** {{one-line summary}}. Detail in the D-NNN row.
```

### 3.2 `docs/RUNBOOK.md` — change-type playbooks

```markdown
# RUNBOOK — maintenance playbook

Entry point for changing the system. Pick the playbook matching your task, read the reference
docs it names, then do the work. **Code + {{FIXTURES}} are ground truth**; where any doc
disagrees with the code, trust the code (and fix the doc).

## Where logic lives
{{MODULE_MAP — same shape as the constitution's, one level more detail.}}

## Reference-doc index
| Question | Doc |
|---|---|
| {{QUESTION}} | {{DOC_PATH}} |

## Change-type playbooks
Each: *when · read first · code to touch · obligations · acceptance · record it.*

### N. {{CHANGE_TYPE, e.g. "Bug fix"}}
- **Read first:** {{reference docs + related ledger rows}}
- **Steps/Code:** {{where the change goes; e.g. reproduce → failing test first → fix so it
  conforms to the fixtures (never edit a fixture to pass) → ladder → adversarial review if
  the diff touches {{UNTESTED_GLUE_AREAS}}}}
- **Acceptance:** {{the binary gate — which ladder rungs}}
- **Record:** {{STATE line; ledger row if durable; which reference doc to update}}

{{Repeat for each recurring change type: feature, config/schema change, dependency/platform
bump (two reviewable commits: forward-compat first, behavior flip second), release cut
(version invariants; owner does the tagging), etc.}}

## Session discipline (BINDING for every session)
1. **Strictly sequential.** No parallel subagents; one unit of work at a time.
2. **Small, shippable units.** ≤ ~1 focused hour, independently shippable, with a hard
   **binary** acceptance check — never "looks right".
3. **Checkpoint invariant.** Every unit ends: acceptance green → STATE Changelog line →
   commit → push. Never start a second unit on top of an uncommitted first.
4. **You are the last reviewer.** The adversarial-review protocol is mandatory; there is no
   stronger pass behind you.
5. **Multi-unit work** persists an owner-approved plan file + STATE checklist; segments run
   sequentially and each ends shippable; delete the plan at the end (durable content must by
   then live in Changelog lines + ledger rows; code cites ledger rows, never the plan file).
6. **Recovery.** If the unit in flight has gone wrong, reset to the last green checkpoint,
   re-run the ladder to confirm green, re-attempt smaller. Record durable lessons first.
   Pushed checkpoints are immutable — never rewrite pushed history.
7. **Ask, don't assume.** Forks that are (a) irreversible/expensive to unwind (schema
   migration, deleting a feature, renaming a public surface), (b) user-visible behavior with
   no spec to appeal to, (c) version-semantics ambiguous (readable as minor vs major), or
   (d) process-reshaping (changes how the owner works, not just the code) are the OWNER's:
   stop at the last green checkpoint, record the fork + options + your recommendation under
   STATE → Owner queue → Open questions, move to independent work. Genuinely unsure whether
   something is a fork? Treat it as one — the queue entry already carries your recommended
   resolution, so escalation costs the owner one read, while a wrong guess can cost a
   segment. Routine engineering judgment inside a unit's stated scope is NOT a fork. Final
   chat message restates the Owner queue.
8. **Verification disclosure.** Every commit body states what was actually verified (which
   ladder rungs/tests ran) and names what could NOT be verified locally — disclosure of
   real actions, not an attestation gate.

## Adversarial review protocol (MANDATORY for {{UNTESTED_GLUE_AREAS}} diffs)
After the ladder is green, re-read the FULL diff fresh — as a hostile reviewer, not the
author — hunting specifically these proven bug classes (each from a real shipped bug; append
new classes as the ledger grows):
- {{BUG_CLASS + its ledger citation}} …
If the pass finds nothing, say so in the commit body ("adversarial pass: clean"); if it finds
something, fix before commit and ledger anything durable.

## Acceptance ladder
**One command: `scripts/ladder.sh`** — fast pre-flight guards, then the full task set in one
invocation. CI's verification step invokes THIS script (shared by construction — no
hand-maintained lockstep). `--guards-only` covers docs-only changes in seconds.

## When CI fails (workflow vs code)
Local ladder and CI run the same script, so CI-red/local-green = environment, not code.
Triage: (1) read the failing log — real failure (fix the code) vs toolchain mismatch (fix the
workflow, in the same PR) vs flake (re-run once; never "fix" code for a flake). (2) Never
weaken a gate to get green. (3) Real-but-out-of-scope: say so with the log excerpt.

## Self-adaptation — keep this runbook useful
If this runbook lacks what you need: consult the ledger + archive; record durable facts as
ledger rows; and if a playbook is wrong, stale, or missing the case you just handled, **fix
this RUNBOOK in the same change.** Treat it as code.
```

### 3.3 `docs/LEDGER.md` — permanent memory (append-only, rolling files)

```markdown
# DEVIATIONS & DISCOVERIES LEDGER — permanent registry (D-001…)

> **Append-only registry — NEVER archived, compressed, or truncated.** Canonical, permanent
> home for every numbered deviation/discovery. Code and docs cite entries as bare `D-NNN`
> and must always resolve here — no entry may ever be deleted or summarized away. Append new
> entries at the bottom, one continuous sequence. Code + fixtures are ground truth; if an
> entry conflicts with current code, trust the code and correct the entry (don't delete it).
>
> **File cap & rollover.** THIS FILE holds at most **{{LINE_CAP}}** lines (cap the LINES,
> not the row count — rows vary in length, and it's the read/context cost you're bounding;
> keep the number in lockstep with the ladder guard's constant). The final row may FINISH
> past the cap, but no row may ever START past it: when the file stands over the cap, create
> `LEDGER_A.md` with this same header discipline, numbering from **DA-001** (then
> `_B.md`/DB-001, …). Existing rows are never moved or renumbered — the cap bounds *file
> size* (an unbounded file is a context hazard for agents), not history. A citation's prefix
> names its file.
>
> **`[cited]` marker (machine-managed).** A row cited from the guard's scan scope carries
> ` [cited]` after its number. The ladder guard syncs it BOTH directions (cited-but-unmarked
> and marked-but-uncited both fail) — verified derived state, never hand-tracked. The marker
> warns that code resolves here before you lean on or reword a row.

- D-001: {{terse entry: what was discovered/decided/broken, what to do about it, what it
  affects. One entry per durable fact. Solved mistakes AND standing invariants both live
  here.}}
```

### 3.4 `scripts/ladder.sh` — guard + verification spec

One bash script, `set -euo pipefail`, run by both the agent and CI. Structure:

1. **Guards first (seconds, no build):**
   - *State length:* warn over soft cap, **fail** over hard cap.
   - *State structure:* fail if a required section header is missing (over-compression
     tripwire); warn if the Owner-queue header vanished (data loss for the human).
   - *Ledger rollover:* warn approaching the line cap; fail when the live file's LAST row
     *starts* past the cap (the final row may overflow; the next belongs in the next file).
   - *Citation integrity:* grep source trees (code + workflows, NOT docs or the guard's own
     test fixtures) for `D[AB]?-\d+`; every citation must resolve to a row in the file its
     prefix names; no duplicate row numbers; `[cited]` markers must match the citation set
     both ways.
   - *Poison-token scan:* fixed strings that must never reach a commit message (e.g. CI-skip
     tokens that a squash-merge would fold onto the default branch), scanned over
     `origin/{{DEFAULT_BRANCH}}..HEAD` **before push** — because force-push is forbidden, a
     pushed mistake is permanent until merge.
   - *Local-only advisories (WARN, skipped in CI):* checkpoint tripwire (code changed vs
     default branch but STATE.md not in the diff → the changelog line is probably missing);
     stale-branch tripwire (behind the default branch → merge, never rebase pushed history);
     plan-orphan tripwire (a file under `docs/plans/` not referenced from STATE.md's active
     work → a finished or pivoted plan missed its deletion step; plans must die — code cites
     ledger rows, never plans).
   - {{DOMAIN_GUARDS: any repo-specific machine-checkable release rule — e.g. a changelog
     length cap, a version-monotonicity check.}}
2. **`--guards-only` exits here** (docs-only work).
3. **The full verification set** in one invocation: `{{TEST + LINT + BUILD tasks}}`.

Plus `scripts/test-ladder-guards.sh`: a fixture-based regression suite for the guards
themselves (synthesizes tiny repos/ledgers, asserts each guard's pass/warn/fail), run in CI
and whenever a guard changes.

### 3.5 Session bootstrap + permission rails (the per-agent adapter layer)

**Session bootstrap — one agent-neutral `scripts/session-start.sh`** (idempotent; P14). It
self-locates the repo root from its own path and keys remote-only steps off an explicit
neutral flag (e.g. `{{REMOTE_FLAG}}=1`) — never any one agent's environment variables:
```bash
# 1. Bootstrap toolchain if needed (instant when cached); on remote containers, launch
#    build-system warm-up in the BACKGROUND so the first ladder run is cheap.
# 2. Branch check: warn loudly on the default branch or detached HEAD.
# 3. Print the protocol pointer: "read docs/STATE.md first, then the RUNBOOK playbook."
# 4. Print STATE.md's size vs its soft cap — if it's already near the cap, the session
#    knows to compress BEFORE writing, not after a failed commit-time guard.
```
Each agent's adapter lives in its own dot-dir and stays THIN — wiring only, no logic. A new
agent's adapter must: invoke the bootstrap at session start (via its hook mechanism, or the
instructions file tells hook-less agents to run it manually), mirror the deny rails below if
the agent supports permission rules, and honor the one-session-one-branch rule. Everything
behavioral stays in the shared constitution + scripts, so switching agents rewrites nothing.

**Tool permissions** (the adapter's config — `.claude/settings.json` for Claude Code, or
your agent's equivalent):
- **Allow:** the ladder, the setup/warm-up/bootstrap scripts, the build tool — verification
  must never stall on a permission prompt.
- **Deny (hard rails):** `git push --force` in all spellings; any push targeting
  `{{DEFAULT_BRANCH}}` directly; environment/secret dumps (`env`, `printenv`, reads of
  `.env`-style files — P17). Prose forbids it; the permission layer *enforces* it. An agent
  without permission-rule support still inherits the prose rule — the rails are
  defense-in-depth, not the only copy of the policy.

---

## Adaptation notes

- **Smallest useful subset** (a repo with light AI maintenance): the Part-2 prompt + STATE.md
  with the Owner queue + a single verification command. For small repos, fold the RUNBOOK into
  the constitution — fewer files to keep straight; split only when the playbooks multiply. Add the
  ledger the first time you catch yourself re-explaining a past mistake; add guards the first
  time a rule is violated.
- **Bootstrap `ladder.sh` as nothing but the verification commands.** Guards accrete one at a
  time, each earning its place after a real violation, and each landing with a fixture test in
  the guard test suite (a botched guard that false-passes is worse than no guard). Treat the
  first few sessions as a shakedown: watch adherence, and when a rule proves ambiguous, the
  fix is a clarified RUNBOOK/prompt in the same change — not more rules.
- **The ledger earns its cost** once ≥2 distinct sessions (or agents) touch the repo — it's
  the only channel through which session N's shipped bug teaches session N+9's review pass.
- **Human effort budget:** the owner's recurring touchpoints are exactly three — merge squash
  PRs, action the Owner queue, and drop manual-test findings into Incoming findings. Every
  other mechanism runs agent-side. If a proposed addition to the harness increases the
  owner's per-change workload, it's probably wrong (see P3).
