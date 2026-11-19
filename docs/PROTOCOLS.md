# PROTOCOLS — what binds every session

<!--
SEED TEMPLATE (AMH). Yours from the moment it is copied. The procedures every session in this
repository works under, whatever its task: the constitution sends every session here, and the
change-type playbooks in docs/RUNBOOK.md route into these sections. Editing this file is a rule
change, so it goes through the Rule-review protocol below; the runbook's self-adaptation does
not reach it.
-->

The task-specific half — the module map, the change-type playbooks, CI triage — is in
`docs/RUNBOOK.md`. This file is the half no task is exempt from.

## Session discipline (BINDING for every maintenance session — D-161)

Structural rules replacing the retired model-tier policy: D-035 moved code segments to Opus after
D-030/D-034 (segments passed their own gates; review still found shipped bugs), but a top-tier
model is no longer a given — assume this session may run on a lesser model and may be cut off at
any moment (rate limit, window end, compaction).

1. **Strictly sequential.** No parallel subagents; one unit of work at a time (the D-133
   sequential-segments rule, generalized to all work). Parallel agent fan-out has burned an
   entire usage window before; it is never the cheap path here. (A single BLOCKING
   glue-review subagent inside a unit is sequential work, not fan-out — DA-003.)
2. **Small, shippable units.** ≤ ~1 focused hour each, independently shippable, with a hard
   **binary** acceptance check (tests / `scripts/ladder.sh` / a scripted comparison — never
   "looks right"). Prefer mechanical steps with hard gates over judgment calls.
3. **Checkpoint invariant (AMH 15.0.0).** Every unit ends: STATE.md Changelog line →
   acceptance green → commit → commit-metadata check → push. The changelog line comes FIRST so
   the ladder verifies the tree that is committed. The check after the commit is
   `scripts/ladder.sh --guards-only`: its poison-token and author-identity rungs read commits, so
   the run before the commit cannot see the one being made, and the git-native pre-push rail
   judges refs, never messages or identities. A failure there is fixed by amending the unpushed
   commit and running the check again; a WARN that either rung checked NOTHING means the check
   did not happen — fetch the reference it names and re-run, or report that it did not run. The
   commit body cannot disclose that check (discipline 8); report it where the session reports its
   outcome. Never start a second unit on top of an uncommitted first — an interrupted session
   must lose at most the unit in flight.
4. **You are the last reviewer.** The glue-review protocol (below) is mandatory at every model
   tier — and the rule-review protocol for harness legislation (DA-005); there is no stronger
   pass behind you. Delegate the pass itself to a fresh-context subagent where the harness
   supports one (DA-003) — accountability for triaging its findings stays with the session.
5. **Multi-unit work** uses the `docs/RUNBOOK.md` playbook-5 persisted-plan pattern (plan file +
   STATE checklist) so any session can resume the backlog mid-stream. At the end, move a completed plan worth
   retaining whole to `docs/history/` — this repository has that archive tier, and it is frozen,
   so an archived plan is a historical record and never permanent memory — otherwise delete it.
   Its durable outcomes live in changelog lines and ledger rows either way. **No ledger row may
   cite a plan's PATH** (AMH 10.4.0) — not in backticks, not as a link, not as a bare filename. A
   plan is provisional context, not permanent evidence, so the citation dies the moment the plan
   retires, inside a row that can never be repaired. Record what the plan DELIVERED in the row and
   name the plan in prose if you must, in a form no path guard resolves. **A legacy citation does
   NOT pin its plan** (AMH 14.0.0, withdrawing the earlier retained-in-place exception): a row's
   immutability covers its text, not the lifetime or location of a file it names, so the row keeps
   its historical wording and the plan completes archive-or-delete like any other, leaving no
   redirect, tombstone or symlink. `docs/LEDGER_B.md` carries one such legacy citation; the plan it
   names is retained for its live triage content, not for that citation, which is why it stays.
   **All of this is prose-only.** This repository ships no path-reference guard and nothing scans
   ledger rows for plan paths — the ladder's `PLAN_DIR` rung only WARNs when a plan file is not
   referenced from `docs/STATE.md`, which is a different question — so the rule-review pass is the
   whole enforcement. Upstream's reference repository checks this in its own append-only guard; we
   do not have one, and this sentence exists so nobody reads the rule as gated.
6. **Recovery — bounded, with a stop condition (DA-012).** If the unit in flight has gone
   wrong, do not flail forward: reset the working tree to the last green checkpoint (`git
   status` first, then `git reset --hard HEAD` and a careful `git clean` of files you created),
   re-run `scripts/ladder.sh` to confirm green, then re-attempt smaller. If the dead end taught
   a durable lesson, record it (STATE line or D-row) before retrying. **But recovery is not
   infinite: if the SAME blocker survives a second reset-and-retry cycle with no real progress,
   stop — do not keep thrashing it.** Reset once more to the last green checkpoint (never end a
   unit red — discipline 3/5), record the blocker (what failed, what you tried) under `STATE.md`
   Owner queue, then close it like any unit — ladder, commit, commit-metadata check, push
   (discipline 3) — so the record survives session death, and end the unit.
   A guard/gate that won't go green is either a real fix you're missing (a code or doc problem —
   diagnose it, don't just re-run it) or an owner fork (discipline 7) — neither is solved by
   burning the usage window re-running a script (the P6 weakest-agent failure mode). The stop is
   for a genuinely stuck blocker, not cover for abandoning a failure you could diagnose — a gate
   fails for a reason; find it before you invoke the stop. Pushed checkpoints are immutable —
   recovery never rewrites pushed history.
7. **Ask, don't assume (owner-judgment forks — D-167).** Some decisions are the owner's, and a
   plausible guess is worse than a pause: the owner has pivoted mid-feature before (D-151
   removed an entire already-on-branch segment). When a unit hits a fork that is (a)
   **irreversible or expensive to unwind** (schema migration, deleting a feature, renaming a
   public surface), (b) **user-visible behavior with no parity source** (no Tasker artifact or
   golden vector says which way), (c) **semver-ambiguous** (could be read as minor vs major), or
   (d) **process/policy reshaping** (changing how the owner works, not just the code): do NOT
   resolve it by picking. Stop at the last green checkpoint, record the question under
   `STATE.md ## Owner queue` → **Open questions** — date-stamped `[YYYY-MM-DD]` (age is the
   owner's triage cue, DA-006), the fork, the options, and your
   recommendation with reasons — then end the unit and move to independent work (or end the
   session). Genuinely unsure whether something is a fork? Treat it as one: the entry already
   carries your recommendation, so escalation costs the owner one read while a wrong guess can
   cost a segment. Routine engineering judgment inside a unit's stated
   scope is NOT a fork — this rule is for decisions the owner would want to make, not cover for
   avoiding decisions the agent should make.

   **Testing a queue item, and the final message (AMH 9.2.0, folded here from the STATE
   preamble).** A session's **final chat message restates the Owner queue** so the owner sees
   pending items without opening STATE — **each item tested first, never copied forward.** Run the
   command an item carries and read its OUTPUT against the resolution the item states, never its
   exit status: a check written to detect the unresolved condition exits 0 exactly when the item
   is still open. An item the output shows resolved is done in this session — delete it and record
   the outcome as a changelog line or a ledger row, rather than restating it with a caveat. An item
   with no check is restated as **unverified**, naming who settles it; carrying a check is
   deliberately NOT required, because an item that needs one will get one, "the owner says so" is a
   check the way a checkbox is evidence, and the absence is information rather than an omission.
   Nothing enforces any of this and nothing may — a gate that consumes "I checked" is a self-report
   (the D-162 line again).
8. **Verification disclosure (D-175).** Every commit body states what was actually verified
   (which ladder rungs/tests ran — for docs-only units, `--guards-only`) and names what could
   NOT be verified locally. On-device behavior stays owner-verified (`DEVICE_TEST_SCRIPT.md`);
   never imply a green ladder covers it. This is disclosure of real actions, not an
   attestation gate (the D-162 line holds).
9. **Establish coverage before reporting an absence (AMH 5.1.0).** Before you report that
   something does not exist or never happened, establish that the command you ran *could have
   seen it*, and name an artifact that could have held the answer — naming the command that
   already failed to see it discharges nothing. The shape to watch for is a local artifact read
   and its answer reported as a property of the repository. The standing trap here is git under
   this repo's `MERGE_MODE` (`amh.conf`): a squash merge lands a whole train of sessions as ONE
   commit, so every INTERMEDIATE state and superseded branch is destroyed by design. `git log`
   and `blame` still show each merged snapshot and what changed between them, but not what
   happened inside a train, in what order or why, so they cannot answer "was this ever tried",
   "when did this change" or "what did that session do", and a search of them cannot establish
   that something never happened — the ledger, the `docs/STATE.md` changelog and `docs/history/`
   are what survive. This does NOT extend to released states: tags and `git show <tag>:<path>` remain
   valid evidence and `docs/RUNBOOK.md` §6 depends on them; likewise `git show HEAD:<path>`, which
   several ladder rungs use. Prose-only, and the defect is the generalization drawn from a command's output —
   no pre-execution rail can see a belief formed after the command returned. If a later session
   finds a checkable slice of this, that check is welcome; what cannot be gated is the belief.
10. **A unit owes the review of what it was set to change, never only that of the diff it
   ends with (AMH 16.0.0).** Decide which pass the unit owes — the glue-review pass, the
   rule-review pass, or a review its plan names — from the change it was planned or asked to
   make, before the work starts. A unit framed as evaluating whether to make a change owes that
   change's pass, because its outcome is that decision, and an asked unit's change is the ask as
   the owner wrote it, not the session's paraphrase of it. Keep the pass when the unit ends
   smaller, and above all when it concludes the change is not needed: the reviewer then gets
   what the unit was set to change, the conclusion and the evidence it rests on — the attempt,
   the measurement — but, as with a diff, not the author's argument for it; it works at the tier
   the planned change would have earned and replays that evidence first (item 11). "No code
   changed, so no review" is the shape to watch for. "Not needed" is the cheapest outcome a
   unit can reach, and if it also removed the review that would test it, doing less would be a
   route past every gate — the laundering of re-reviewing until a pass comes back clean,
   reached by shrinking instead. Concluding "not needed" is an outcome the pass tests;
   redefining what a unit of an owner-approved plan was set to change is the owner's call
   (item 5). A unit that stops at a blocker (item 6) or a fork (item 7) has concluded nothing:
   it parks, and its pass waits with it. The commit body says what the unit was set to change
   and which pass that owed (item 8) — a disclosure nothing consumes. "A unit is what one
   reviewer saw" counts passes; this item says which pass is owed. The acceptance ladder still
   follows the tree — a docs-only result runs the docs-only gate — because the ladder checks
   what was committed, while the pass checks a judgement. Prose-only: which pass a unit owes is
   decided from intent, which no guard can read.
11. **A decision says whether its evidence was measured, estimated or reported (AMH 16.0.0).**
   A ledger row, commit message or report that rests a decision on a number or an outcome — a
   diff size, a line count, "it came out longer", "it does not reproduce" — says how it got it:
   measured, naming the command, with its input kept where the reviewer can reach it until the
   pass reports; estimated by reading; or reported by someone else, naming who. A past tense is
   a measurement claim: "it came out longer" asserts that an attempt was built. A measurement
   nobody can replay reads as an estimate. A ledger row outlives every session branch, so it
   carries the number and the command themselves and names no branch, as item 5 rules for a
   plan's path. Where a pass is owed its reviewer replays what it can (item 10); elsewhere the
   reader is the only check. Prose-only, and it must stay so — nothing in the artifacts tells
   an estimate from a measurement, and a required "measured:" field would be filled either
   way; the sentence stays a disclosure because nothing consumes it.
12. **A fix's test is written before the fix, and existing tests are frozen for the fix (AMH
   17.0.0, P21).**
   (a) For a defect, write the test that reproduces it and run it against the unfixed tree
   before any fix is written. It must fail for the reason the report names: a failure on a
   typo, a missing import or an unrelated assertion has reproduced nothing. Then write the fix.
   The test committed is the test that was seen failing: one edited after the fix was written
   is run against the unfixed tree again. The failing test is never committed on its own; the
   unit ends green, with the test and the fix in one commit (item 3). Where a defect cannot be
   reproduced in a test, the commit body says so and says what was done instead.
   (b) A test that existed before the fix is not the fix's to change. Changing,
   skipping, loosening or deleting one is a second decision, and "a test" includes what decides
   whether it runs and what it accepts: fixtures and golden data, shared helpers, the runner's
   exclusions and tolerances. A test changed to make way for a fix is covered whichever unit
   changes it. That unit owes a fresh-context pass, run as the glue-review pass below with two
   differences: the strongest available tier whatever the diff's size, and no self-review
   fallback. Where no fresh context can be spawned, the test stays as it was and the question
   goes to the Owner queue. The reviewer gets the old test, the new one, and the defect report
   as it was received (or the reproduction, where the session found the defect itself), and
   judges whether the old test was wrong by the specification, never by the fix. Where no
   specification settles what the behaviour should be, that is the owner's fork (item 7), not
   the reviewer's. Where the unit already owes a pass, it is the same pass. Adding a test is
   not covered. The reason for both: a test written after the fix is written from the fix, and
   showing that it fails without the fix does not tell the two apart, so both demonstrations
   are owed. The same session may write both. Order keeps the test from asserting whatever the
   fix's code happens to do; it does not keep the test from sharing the session's diagnosis.
   Prose-only: nothing records the order, and no guard reads the diff for a changed test. The
   commit body says the test was seen failing first and names any pre-existing test the unit
   changed, a disclosure nothing consumes (item 8).

## Glue-review protocol (MANDATORY for `:platform` / `:app` runtime changes)

Any change touching a `:platform` adapter or `:app` runtime glue (service, pipeline controller,
observers, receivers, tile, notification actions) gets a **second, adversarial diff pass before
commit**, after the ladder is green. A unit set to make such a change owes this pass even when it
ends without one; the reviewer then reviews its conclusion (Session discipline 10). A fix that
changes, skips, loosens or deletes a test that existed before it owes this pass too, whatever
areas its diff touches, at the strongest available tier and with no self-review fallback
(Session discipline 12).

**Who performs the pass (DA-003): a fresh-context reviewer, not the authoring context.** The
context that wrote a diff is anchored on its own reasoning and predisposed to accept it. Run
the pass in a review subagent (or your harness's equivalent clean invocation) that receives:
the full diff, the bug-class checklist below, and read access to the tree — and does NOT
receive the author's rationale or chat history. Scale the reviewer to the diff: a small
mechanical diff may use a light model tier; a large or glue-heavy diff gets the strongest
tier available. This is the one sanctioned subagent (a single, BLOCKING review inside the
unit — Session discipline 1's fan-out ban is about parallel *work*). The session stays
accountable: triage every finding (fix it, or record why not) — a clean report is read, not
rubber-stamped. Only if the harness cannot spawn any fresh context, fall back to the
in-context pass: re-read the FULL diff fresh — as a hostile reviewer, not the author.

Either way, the pass hunts specifically the ledger's proven bug classes:

- condition/gate **polarity and missing operands** (D-030 b: `scalingUse` dropped from an AND gate);
- **list/insertion order** — newest-first vs newest-last (D-030 b: Array Push at index 1);
- **observer/echo races** — token-consumption vs latest-value matching, delayed callbacks,
  no-op writes that never notify (D-034 a);
- **int truncation vs `Math.round`** round-trip drift across range normalization (D-034 b);
- **non-idempotent lifecycle calls** and per-process state that should survive process death
  (D-034 c — `savedMode` is the standing example; D-144 — the dimming `engaged` latch: a
  Tasker-persisted global rebuilt as an in-process field must treat process start as UNKNOWN,
  not "off");
- **null/absent sentinel handling** at startup — a reader that hasn't produced a real value yet
  must not match rules (D-108 battery `-1`);
- **fire-and-forget cancellation ordered before a compensating write** — `cancel()` without
  `join()` lets the dying coroutine's last write serialize AFTER the "restore" write (D-139:
  panic 255 vs an in-flight animation frame);
- **"send-to-running-service" assumptions** — `startForegroundService` CREATES the service, never
  a no-op; control actions (pause/reapply) must be validated in `onStartCommand` or they birth a
  zombie FGS (D-140: widget Reset while disabled started the sensor collector).
- **asymmetric sibling gates** — a cost/enable gate applied to one of several parallel signal
  paths but not its siblings (D-142: the location listener had the `[LOC]` gate and the app poll
  its rule gate, but the wifi listener ran ungated — shell-spawning SSID strategies with zero
  wifi rules). When reviewing a gate, enumerate the sibling paths and check each.
- **stale async completion published over newer state** — an async resolve/fetch launched by an
  event can complete AFTER the state it described changed; without a still-current check its
  result overwrites the newer truth, sometimes latching (D-143: an in-flight SSID resolve landing
  after `onLost` resurrected a "connected" state; a late failed resolve wiped a confirmed SSID
  and the resolved-network skip then blocked recovery until reconnect).
- **persisting a TRANSFORMED copy of visible edit state without writing the transform back** —
  any commit path that validates/clamps/coerces on the way to disk must snap the visible state
  (draft, field, slider) to the exact persisted copy, or the UI diverges permanently from the
  store (D-164: draft Apply committed `validate()`'s cross-field-coerced copy while the draft
  kept the raw pair — perpetually dirty screen, a slider showing a value that never persisted;
  the G3-F3 class, which range-alignment alone cannot fix for cross-field rules).

Rationale (D-030/D-034/D-035): every Sonnet migration segment passed its own acceptance gate,
yet dedicated review found real shipped bugs in exactly this glue — golden vectors cannot see
platform/runtime code, so reviewer attention is the only net there. There is no separate
review pass or stronger model behind you now; this second pass replaces it. If the pass finds
nothing, say so in the commit/PR body ("glue-review pass: clean"); if it finds something, fix
it before commit and record anything durable as a `D-NN`.

## Rule-review protocol (MANDATORY for binding-rule / guard changes — DA-005)

Any diff that changes the harness's LEGISLATION — CLAUDE.md, this file (its protocols and
Session discipline), `scripts/ladder.sh` guard semantics **and their fixture suite
`scripts/test-ladder-guards.sh`** (a weakened fixture softens the net every later guard
change is tested against), `scripts/session-start.sh`, `scripts/redact.sh` and `scripts/redact-tool-output.sh` (DA-007 — a
silently weakened redaction filter is a weakened rail), `scripts/command-guard.sh` (DA-009 —
same weakened-rail rationale for the pre-execution command rail), ledger preambles, adapter permission
rules (e.g. `.claude/settings.json` allow/deny), `AGENTS.md` — gets the same fresh-context
review as glue (DA-003 mechanics: the reviewer receives the diff, this checklist, and tree
access — never the author's rationale), with three differences: the reviewer defaults to the
**strongest available tier regardless of diff size** (a three-line rule edit can carry a
semantic bomb; size is a bad proxy for rule diffs); there is **NO self-review fallback** for
rule diffs — a session that cannot spawn a fresh context parks the rule change in the Owner
queue (discipline 7(d)) for a session that can, instead of reviewing its own legislation;
and it hunts RULE bug classes, each from this repo's history:

- **rule contradiction** — the new rule vs an existing binding rule (the DA-003 carve-out
  from the D-161 fan-out ban exists precisely because the two rules collide);
- **prose/guard lockstep drift** — a number or behavior stated in prose diverging from the
  guard constant/logic enforcing it (the DA-001/DA-004 lockstep obligations);
- **Goodhart-ability** — the rule can be satisfied without its intent (trimming to just
  under 12 KB satisfied the old length guard while defeating it — DA-004);
- **enforcement asymmetry** — prose implies a protection no guard or permission rail
  actually checks (either say it's prose-only, or add the check);
- **citation validity** — the citation guard deliberately does not scan docs (`CITATION_SCAN_PATHS` in `amh.conf`, which today covers `app domain platform .github scripts`), so D-row cites in rule
  prose are checked HERE: the row exists and actually says what the rule claims;
- **agent-agnosticism regression** — the rule silently assumes one agent's machinery,
  filenames, or env vars (D-176).

A unit set to change legislation owes the pass even when it ends without a diff (Session
discipline 10).

Out of scope: routine STATE.md edits (changelog lines, queue items, Current state) —
working memory, not legislation. Two STATE-anchored texts ARE legislation and stay in scope: the
**length-guard preamble** (guard-lockstep thresholds) and **Decided non-items** (binding
declines, whose body is `docs/rebuild/DECIDED_NON_ITEMS.md` under STATE's pointer, DD-037). Verdict goes in the commit body ("rule-review pass: clean", or the findings and
their triage). The ladder's rule-file advisory (DA-006) WARNs when the *uncommitted* diff touches a file
named in `RULE_FILES` (`amh.conf`) — that tripwire only *surfaces* the obligation, it never certifies the
pass; the review itself stays prose-enforced (the D-162 no-attestation-gates line holds).
STATE.md and the ledger files are deliberately outside the tripwire (they change in nearly
every unit — warn fatigue kills tripwires), so their legislative sections stay wholly
prose-covered; so does `docs/rebuild/DECIDED_NON_ITEMS.md`, which is not in `RULE_FILES` either. **One level of meta only:** the reviewer reports, the session triages, the
owner arbitrates via the Owner queue — nobody reviews the reviewer.

## Incident: leaked credential (DA-006)

If a secret (token, key, password) has reached a commit, a pushed branch, a log, or any other
output: stop normal work — **containment outranks the checkpoint invariant.**

1. **Never repeat the value again** — not in STATE, the ledger, chat, an issue, or a
   "look what leaked" diff. Refer to it by key name only (D-175 discipline).
2. **Owner queue, immediately** (Pending owner actions): the key name, where it landed
   (commit SHA / file / log), and the exposure window. Push nothing new containing it.
3. **The owner rotates the credential FIRST** — rotation, not history cleanup, is what ends
   the exposure; treat the value as burned even after cleanup.
4. **History rewrite is the ONE sanctioned exception** to "never rewrite pushed history" and
   the force-push rail: owner-decided AND owner-executed — an agent never runs the rewrite
   (the deny rail stays for agents; the owner lifts it for themselves). Scoped to removing
   the secret (e.g. `git filter-repo`), coordinated with the remote. Never as part of normal
   engineering.
5. Afterward: ledger row for the incident; if a guard or deny rule could have caught it, add
   it with a fixture test. Demonstrate the fixture fails without the guard by removing the
   behaviour and re-running the suite — never by performing the unguarded operation for real
   (`AGENTS.md` **Destructive work**).

## Acceptance ladder

**One command: `scripts/ladder.sh`** — the shipped AMH guards, then this repo's guards under
`scripts/guards/`, then `scripts/verify.sh`, which holds the five Gradle rungs in a single
invocation. The ladder is a **shipped script: never edit it** (`docs/HARNESS_LOCAL.md`); the
integrity rung hashes it against `scripts/MANIFEST.sha256` every run. It takes **no arguments
other than `--guards-only`** and forwards nothing to Gradle — a flag you want under CI belongs
in `scripts/verify.sh`, which is where `--no-daemon --no-configuration-cache` now lives.

The guards are not enumerated in prose anywhere, deliberately (re-enumerations kept drifting as
guards were added, DA-008 F8). To see what runs, read `scripts/ladder.sh`'s `run_guards`
function and `ls scripts/guards/`; the ladder also names every rung as it executes. Both
fixture suites — `scripts/test-ladder-guards.sh` for the shipped guards and
`scripts/tests/local-guards.sh` for ours — run inside `scripts/verify.sh`, and again as a
standalone `build.yml` step after a failed ladder only, so that a red guard still tells you
whether the guards themselves regressed (DD-067). Run them whenever you change a guard (D-173).

On remote containers the session bootstrap runs `scripts/bootstrap.sh`, which launches
`scripts/warm-gradle.sh` in the background (D-173), so by the time you first run the ladder the
cold dependency/compile cost has usually already been paid; `~/.gradle-warmup.log` shows its
progress, `AAB_SKIP_WARMUP=1` disables it. **`build.yml`'s "Acceptance ladder" step invokes
this same script (D-166)**, so CI and local share one definition of verified — there is no
hand-maintained lockstep. `scripts/ladder.sh --guards-only` covers docs-only changes in
seconds.

**Working-memory output.** The size rung reads the byte caps and both compression-floor keys
from `amh.conf`, using the shipped default only for an omitted key. It names a threshold only
when a verdict turns on it: a plain green size line names no cap, and a successful compression
reports headroom below both the byte and sentence floors rather than printing either target.
The two units are conjunctive: shaving words cannot clear the sentence floor, while
repunctuating without freeing space cannot clear the byte floor. The boot banner still prints
size against the soft cap, and a small edit above it still names `STATE_EDIT_DELTA_BYTES`, because
those verdicts turn on those values. Read configured thresholds from `amh.conf`, and treat the
guard functions as authority if this description drifts.

The rungs individually (run the relevant subset until green; on-device behavior is
owner-verified — no emulator, no KVM):

```bash
./gradlew :domain:test            # pure-JVM engine + golden parity tests
./gradlew :platform:test          # Robolectric adapter tests
./gradlew :app:testDebugUnitTest  # app unit + Robolectric tests
./gradlew :app:assembleDebug      # APK
./gradlew :platform:lintDebug :app:lintDebug  # lint, BOTH Android modules (hard gate — no baseline;
                                              # targeted suppressions in app/lint.xml). One module's
                                              # gate is not a gate: unless checkDependencies is on,
                                              # AGP reports a library's findings only in that
                                              # library's own report (DB-074).
```

## Working-memory compression

The rules for compressing `docs/STATE.md` (AMH 9.2.0, rewritten by 11.0.0/12.0.0/13.0.0/14.0.0).
They live **here** rather than in the file they govern, and the placement is the point: these rules
change only under the rule-review protocol, while the byte cap on `docs/STATE.md` exists to force
*volatile* content out. A block of permanent rules sitting inside that cap spends the budget every
pass is fighting for, and it cannot itself be compressed — folding a live rule is repeal. Measured
here rather than assumed: the preamble this section replaced was ~1,050 bytes of a 9,216-byte
ceiling.

**Relocating a live rule is legislation, not tidying — and the destination decides whether it is
repeal.** This file is read by every session and every playbook routes into it, so a rule here still
reaches the session that needs it. The ledger and `docs/history/` do not: they are retrieval
storage nobody reads whole, and a live rule there binds nothing. This repository has **no**
documentation-navigation guard, so the pointer left behind in `docs/STATE.md` is prose only —
said plainly rather than implying a check we do not have; `doc-facts.sh` is an anchor guard and
does not read this heading. Moving any further passage out of working memory stays the owner's
call, one grant at a time.

**Threshold vocabulary (AMH 13.0.0).** The words are load-bearing and each names a different
behaviour: `STATE_WARN_KB` and `STATE_EDIT_DELTA_BYTES` are **triggers**; `STATE_HARD_KB`,
`LEDGER_ROW_SENTENCE_CAP` and `LEDGER_ROW_CHAR_CAP` are **rejection boundaries**;
`STATE_COMPRESS_TO_KB` and `STATE_COMPRESS_TO_SENTENCES` are **post-action ceilings**;
`LEDGER_LINE_CAP` is a **rollover boundary**; and the live ledger volume's byte size is
**measurement only** — reported every run, never judged. There is no ledger warning band; 13.0.0
removed it. Every one of these lives in `amh.conf` and is deliberately **not** restated here as a
number: nothing checks this prose against the config, so a copied number drifts silently the first
time a threshold moves. `docs/HARNESS_LOCAL.md` carries the one sanctioned prose copy of
`LEDGER_LINE_CAP`, and that row is a lockstep obligation.

**When to compress.** Compress by lifecycle, not by file size: once a stage is complete, fold its
narrative and route its durable lessons **even below the compression trigger** (AMH 11.0.0). The
trigger only makes a pass mandatory before further substantive work; the rejection boundary remains
a byte-only failure boundary. A typo fix above the trigger is allowed and still owes the pass.
Boundaries determine when machinery intervenes, not how much content an author should produce.

**How far.** After compression, keep only current state, unresolved Owner-queue items, immediate
operational gotchas, and concise Changelog pointers. The configured byte and sentence values are
post-compression acceptance ceilings, satisfied **together**, not retention goals. There is no
reward for keeping text because space remains, no preferred landing size, and no need to add,
preserve or reshape content to approach either ceiling. A substantially smaller file is equally
successful — and often better — when it retains everything live.

**What a passing counter does and does not establish (AMH 12.0.0).** Passing the paired byte and
sentence checks establishes only that the file or row is not obviously oversized. It is **not** an
authoring-quality verdict: it says nothing about concision, correct scope, information quality, or
whether a compression pass actually succeeded. A successful guard verdict reports the measured
property and nothing more.

**Counter-only rewrites are forbidden (AMH 12.0.0).** Do not merge sentences, repunctuate, remove
useful qualifiers, or otherwise rewrite text *solely* to move a counter. The sentence ceiling exists
precisely so that word-by-word shaving cannot satisfy the guard — shaving words cannot move a
sentence count, and a pass that repunctuated `. T` into `; t` to halve one would be gaming the gate
rather than compressing the file. Approaching either boundary is a **classification signal**, not an
editing target: material near one probably contains narrative or several lessons, and should be
split, reduced to its durable conclusion, or routed out with a concise pointer.

**How.** Decide each fold by whether the stage is complete, never by proximity to a number.
Collapse completed stages into concise Changelog pointers, fold changelog clusters, move durable
lessons into the append-only ledger before deleting their narrative, and retain only immediate
operational gotchas. Never shave clauses until the guard goes quiet, and never cut text into
another file — that is not compression, it is the relocation the second paragraph makes the
owner's call. **Project**, **Current state** and **Owner queue** must always survive a pass:
compress an Owner-queue item's prose, never drop an open one — closing them is the owner's call.

**What may be in `Current state` at all — the content rule beside the size rule (AMH 14.0.0).**
Compression decides how much survives; this decides what is eligible. Three categories, and only
the first is unqualified truth here:

1. **Repository-controlled** — true of the checked-out tree until another repository change alters
   it: the version the tree declares, implemented behaviour, tracked active work, and the live
   ledger **volume** — the volume, never its latest row id, which every append moves and no
   sentence follows. This is what `Current state` is for.
2. **World-controlled** — can change with no change to the tree: whether a branch merged, whether a
   remote tag or release exists, PR and CI status, deployments, remote branches, forge protection
   settings. Never cached here as current truth.
3. **Historical observation** — a past external fact kept because it is still useful, scoped in the
   sentence to when or where it was observed so it cannot read as present status. This repository's
   device-round readings are the standing example.

Apply the test before writing a sentence: *would it still be accurate if this same commit were
cloned tomorrow, under another branch name, after forge state had moved?* If not, route it. Where a
live probe already computes the fact, point at the probe rather than copying its output —
`scripts/session-facts.sh` is ours, and it is why no release number is cached here (DC-030). Where a
session cannot inspect the setting (branch protection, a remote's configuration), state the
expectation or the unresolved owner action without claiming to have observed it. Where the
resolution is an external action, it belongs in the Owner queue with its check, not here.

Two limits, so this is not read wider than it is. The scope is `Current state`; it does not reach
the Changelog or the ledger pointers, which are historical storage by construction — a dated line
calling a train unreleased records when it was written, and no session owes a re-validation of what
a past row said. And it is **prose-only**: no guard reads a State sentence for temporal validity,
none is proposed, and none should be — the discriminator is meaning, which is what this harness
refuses to build gates on. Do not add one, and do not make startup grep this file for forbidden
phrases.

