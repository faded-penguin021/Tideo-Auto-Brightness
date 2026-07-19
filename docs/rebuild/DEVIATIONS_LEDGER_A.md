# DEVIATIONS & DISCOVERIES LEDGER A — permanent registry (DA-001…)

> **Append-only registry — NEVER archived, compressed, or truncated.** This is the canonical,
> permanent home for every numbered deviation/discovery from DA-001 on (the base block D-001…
> D-176 lives in `DEVIATIONS_LEDGER.md`, closed by DA-001). Code comments and docs cite
> entries as bare `DA-0NN` and must always resolve here, so no entry may ever be deleted or
> summarized away. **Append new maintenance deviations as DA-001, DA-002, … at the bottom** —
> one continuous sequence, never restart numbering. The highest-value "don't repeat these
> mistakes" reference. Code + golden vectors are ground truth; if an entry conflicts with
> current code, trust the code and correct the entry (don't delete it).
>
> **File cap & rollover (D-153 mechanism; cap unit changed rows → LINES by DA-001 —
> owner-instructed).** THIS FILE holds at most **1000 lines** (`scripts/ladder.sh`
> `LEDGER_CAP_LINES` — keep the two in lockstep). The FINAL row may finish past the cap, but
> no row may ever START past it: when the file stands at more than 1000 lines, do NOT append
> here — create **`DEVIATIONS_LEDGER_B.md`** with this same header discipline and start
> numbering at **DB-001**, and so on (`_C.md` → DC-…). Existing rows are never moved,
> renumbered, or summarized — the cap bounds *file size* (an unbounded single file is a
> read/context hazard for agentic maintenance flows), not history.
> A citation's prefix names its file: `D-…` → `DEVIATIONS_LEDGER.md`, `DA-…` → this file,
> `DB-…` → ledger B. Cites stay bare (`DA-017`) everywhere, exactly like `D-017`.
> "Same header discipline" means: carry over the *structural* header content (the append-only
> warning, this cap-&-rollover paragraph with the prefixes/filenames advanced, the
> citation-routing sentence, the ground-truth rule, the `[cited]`-marker paragraph below) but
> NOT file-specific lines — the base-block sentence above stays unique to each file's own
> lineage note.
>
> **`[cited]` marker (D-174 — machine-managed, owner-requested).** A row whose number is cited
> from the guard-5 scan scope (`app/ domain/ platform/ .github/` sources — see
> `scripts/ladder.sh`) carries ` [cited]` directly after its number (`- **DA-002 [cited]: …`).
> Ladder guard 5 syncs it in BOTH directions — a cited row missing the marker and a marked row
> no longer cited both fail — so the marker is verified derived state, never hand-tracked:
> `grep '\[cited\]'` on a ledger file lists every code-anchored row without cross-referencing
> the tree, and the marker on a row warns that a code/workflow comment resolves here before
> you lean on or reword it. Adding/removing this marker is mechanical metadata maintenance,
> NOT a content edit.

## Deviations & discoveries ledger (continued from D-176)

- **DA-001: ledger rollover cap changed rows → lines; base ledger closed at D-176
  (owner-instructed, 2026-07-19).** The D-153/D-171 cap (184 rows/file) bounded row COUNT but
  not size — rows average ~19 lines, and the base file reached ~3.3k lines at only 171 rows,
  defeating the cap's purpose (bounded read/context cost). New rule, effective from this file
  on: a ledger file caps at **1000 lines** (`LEDGER_CAP_LINES` in `scripts/ladder.sh` guard
  1c — the constant and this paragraph move in lockstep). The final row may FINISH past the
  cap; the next row must open the next file — machine-checked as "no row may START past the
  cap line". Warn lead: 900 lines, mirroring the old 10-row lead. Consequences: the base
  `DEVIATIONS_LEDGER.md` is **closed** with D-176 as its final row — numbers D-177…D-184 are
  never assigned (a citation `D-177+` is always a typo); its preamble carries a closure note
  (structural edit, D-171 precedent — no rows touched). Guard 1c rewritten (line-based,
  last-row-start check); `test-ladder-guards.sh` guard-1c fixtures rewritten to match;
  CLAUDE.md/RUNBOOK cap wording updated. Numbering within a file is open-ended (no fixed
  DA-184 ceiling — the line cap decides when a file ends).

- **DA-002: branch-train workflow codified (owner-requested, 2026-07-19).** The docs assumed
  "one branch per session, each squash-merged separately"; the owner's real workflow is a
  branch TRAIN: each new session branch is cut from the newest session branch (not `main`),
  superseded branches are deleted unmerged once contained, and only the final superset
  branch lands on `main` — ONE squash PR whose title/body (which the squash commit inherits)
  must describe the net `origin/main..HEAD` diff, not the last session's commits.
  Discovered the hard way: a 2026-07-19 remote audit found five queue-cited branches deleted
  and this session's branch carrying the whole 55-commit 1.8.0 train, while the staged PR
  draft covered only 4 commits. Codified in CLAUDE.md Git rules; ladder guard 4's
  behind-main WARN reworded (structural under this model — reconcile only when `main`
  carries work the train lacks); harness prompt P13/3.4 generalized. Standing agent duties:
  compute PR drafts from `origin/main..HEAD`; `git ls-remote --heads origin` before citing
  session branches in docs.

- **DA-003: glue review moves to a fresh-context subagent (owner-requested, 2026-07-19).**
  The RUNBOOK glue-review pass had the authoring context review its own diff — anchored on
  its own reasoning, predisposed to accept it (the exact failure D-030/D-034 recorded:
  segments passed their own gates, independent review still found shipped bugs). Now the
  pass runs in a fresh-context reviewer — a subagent or the harness's equivalent clean
  invocation — given the diff, the bug-class checklist, and tree access, but NOT the
  author's rationale/chat. Reviewer model tier scales with the diff (small mechanical →
  light tier; large/glue-heavy → strongest available). Carve-out: this single BLOCKING
  review subagent is sequential work inside the unit, not the D-161 parallel fan-out the
  discipline bans. The session remains accountable — every finding is triaged (fixed or
  declined with a reason); a clean report is read, not rubber-stamped. In-context
  self-review survives only as the fallback where the harness cannot spawn a fresh context.
  RUNBOOK protocol + discipline rules 1/4 and harness prompt P12/3.2 updated.

- **DA-004: STATE length guard gains hysteresis (owner-requested, 2026-07-19).** The old
  scheme (warn > 12 KB, fail > 16 KB, "steady-state target 12 KB") produced exactly the
  byte-trim anti-pattern an external review flagged: sessions trimmed to just under 12 KB,
  the next session's lines re-armed the warn, and the log filled with micro-commits ("three
  fewer bytes") — two of which even mis-reported their own result. New scheme, stateless
  hysteresis instead of pass-counting (no persistent debounce state to maintain or corrupt):
  **grow freely to 14 KB; when the warn fires, ONE deep compression pass to ≤ 9 KB; fail
  > 16 KB (unchanged).** The 9→14 KB band (~5 KB ≈ 15–25 changelog lines) is the debounce;
  the 14→16 KB gap keeps 2 KB of emergency margin for a long session. Trimming to just under
  a threshold is now named as the anti-pattern in the guard message and the STATE preamble.
  Guard 1 + its test fixtures (22 cases) + STATE preamble + harness prompt 3.1/3.4 updated
  in lockstep.

- **DA-005: rule-review — harness legislation gets the fresh-context pass too
  (owner-requested, 2026-07-19).** Rule changes had no review at all: guard LOGIC has a test
  suite, but the prose rules (CLAUDE.md, RUNBOOK protocols, ledger preambles, permission
  rails) had none, and a bad rule compounds — it manufactures defects in every future
  session that obeys it, while the authoring context is maximally anchored on a rule it just
  designed (evidence in git: pushed commits 6ae163a/55522d6 mis-claimed a cleared warn; the
  DA-003/D-161 collision needed an explicit carve-out). New RUNBOOK "Rule-review protocol":
  binding-rule/guard diffs — including the guard FIXTURE suite and the session bootstrap,
  and STATE's two legislative sections (length-guard preamble, Decided non-items) — get the
  DA-003 fresh-context reviewer with a RULE checklist: contradiction, prose/guard lockstep
  drift, Goodhart-ability, enforcement asymmetry, citation validity (guard 5 skips docs),
  agent-agnosticism regression. **Strongest tier regardless of diff size** (size is a bad
  proxy for rule diffs; refines DA-003's scaling), and NO self-review fallback — a session
  that can't spawn a fresh context parks the rule change in the Owner queue instead. Routine
  STATE edits exempt (working memory). Prose-only by design (D-162: no attestation gates);
  verdict disclosed in the commit body. One level of meta only: nobody reviews the reviewer;
  the owner arbitrates via the queue.
