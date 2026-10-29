# Codemap: a generated feature map, so agents find what already exists

## Status

- **Spec only. No code has changed.** The owner asked on 2026-10-29 for this plan. That request
  authorised **Unit 0 only**: committing this file on the session branch
  `session/dreamy-bardeen-c4jps1`. Every later unit needs the owner's separate go-ahead.
- **Unit 1 is a review by Astra at high effort ("Astra High"), run by the owner.** The brief is
  at the end of this file.
- Two design passes fed it: this session's and a ChatGPT answer the owner supplied. They agreed
  on the core. Where they differed, the choice and its reason are under Design and Excluded.
- **No unit changes the app.** Nothing under `app/`, `domain/` or `platform/` changes. So there
  is no version bump, no F-Droid changelog, no device step and no glue review.
- No unit opens a PR, tags or releases.
- **No new Markdown.** The deliverable is one TOML file, one generated JSON file, one Python
  script, one guard and edits to existing docs. This plan is deleted at close-out.
- Owner decisions D1–D4 are open, with recommendations.

## Why

**The problem.** An agent that reads files one at a time can understand each one correctly and
still miss that a concept spans several of them, and then write a second copy. Grep does not
solve this: 62 of the 316 tracked Kotlin files mention "circadian" (36 in `src/main`), but the
solar computation that matters is called directly from three. A hand-written architecture page
drifts, and the tree already has plenty of prose.

**Incidents.** New machinery here needs a named incident.
- **DD-038 (owner report, 2026-10-06).** A `SUNSET-30` rule switched on at 17:30 while the
  Circadian graph showed sunset at about 19:06. The context engine had its own location lookup
  and fell back to a placeholder, and the rule editor's labels held a second copy of the solar
  math. Both now go through `ContextSolarTimes`.
- **DD-061.** The same area later merged two copies of a status rule into
  `CircadianLocationStatus.of`, and four copies of a coordinate check into
  `LocationSnapshot.isValid`. It also kept two location orders apart on purpose: background
  acquisition never powers GPS, and DD-038's rule-time order is the owner's.
- **The light-refactor audit (2026-10-21)** found six more calculations written twice, three of
  them visible to users.

**The solar math as an agent should see it** (verified 2026-10-29 at `282c95f`):
- `SolarCalculator` is declared in `domain/.../circadian/SolarTimes.kt`. The file name does not
  match the symbol, so a search by file name misses it.
- It has three callers:
  - `ContextSolarTimes`: context rules and the rule editor, in DD-038's order;
  - `CircadianWindowProvider`: the pipeline's circadian windows, in task90's acquisition order;
  - `CircadianChart`: the Circadian screen's graph.
- `AndroidContextSignalSource` uses `ContextSolarTimes` without importing it, because the two
  share a package. A graph built from imports misses that edge.
- An agent that finds the two location orders may try to unify them, which DD-061 rejected. The
  map should hand it DD-038 and DD-061 along with the files.

**What it builds on.** Two hand-kept links already exist: `D-NNN` citations link code to its
reasons, and `// Tasker:` markers link code to its source. The map joins both instead of
copying them.

## Design

**Principle.** Derive what the code shows, declare only what it cannot, and check the boundary
on every ladder run.

**Three pieces.**
1. **`codemap/features.toml`, kept by hand.** Each feature lists:
   - `anchors`: the top-level Kotlin symbols that own its logic;
   - optionally `keywords`, which feed the reinvention tripwire (D3).

   Nothing else goes in it: no file lists, no prose, no relation types. Example, with names
   verified on 2026-10-29:
   ```toml
   [circadian]
   anchors = ["SolarCalculator", "DynamicScaleEngine", "NightLightTemperatureRamp",
              "ContextSolarTimes", "CircadianWindowProvider"]
   keywords = ["sunrise", "sunset", "solar", "circadian"]
   ```
2. **`scripts/codemap.py`.** Stdlib-only Python, 3.11 or later for `tomllib` (`python3` is
   already in `REQUIRED_TOOLS`). It scans tracked Kotlin, resolves references, expands features,
   answers queries, and writes and checks the map.
3. **`codemap/map.json`, generated and committed (D2).** Feature-level only.

**The scanner.**
- **Input.** Tracked `*.kt` under `{app,domain,platform}/src/{main,debug,test}/kotlin`, read
  from `git ls-files`. That is the same tracked-set discovery as the other guards, with the same
  DB-056 caveat: a new file is invisible until it is staged. Files under `src/test` get the role
  `test` and can never be anchors.
- **Tokens.** Comments and string text are stripped, but `${…}` template expressions are kept,
  because they are code. Raw strings and nested block comments are handled. These are the traps
  `comment-budget.sh`'s fixtures already name, such as `cb-rawstring`.
- **Declarations.** Top-level only, meaning brace depth 0: `class`, `interface` and `object`
  with any modifiers, `typealias`, `fun` (an extension `fun Foo.bar()` declares `bar`), and
  top-level `val` and `var`.
- **References.** An identifier in file F refers to symbol S when:
  - S is in F's own package;
  - F imports S explicitly, or under an `as` alias;
  - F star-imports S's package;
  - or F names S fully qualified.

  A file's references to its own declarations are ignored. Today there are 2 star imports and 0
  alias imports.
- **Ambiguity.** Two visible declarations with the same simple name are recorded as an edge to
  both and counted. For an anchor, ambiguity is an error.
- **Blind spots,** stated in the script's header:
  - references from XML, the manifest or Gradle;
  - reflection;
  - a local name shadowing a top-level one, which gives a false positive.

  The tree holds no Java today.

**Expanding a feature.** Always one hop:
- **owners** are the files that declare an anchor;
- **consumers** are the production files that reference one;
- **tests** are the test files that reference one.

A feature grows only when someone adds an anchor, a one-line change a reviewer can see.
Multi-hop expansion is excluded: through a hub like `AppModule` it would pull in the whole app.
An anchor may belong to more than one feature. Night Light and circadian share
`NightLightTemperatureRamp`, for example. Features that share members are reported as
**overlapping**, which is derived, not declared.

**The committed map.**
- **Contents.** `schema`, then per feature:
  - `declared`: the anchors, each with its file;
  - `derived`: owners, consumers (each with the anchors it uses), tests and, under D3,
    `unanchored`.

  The two key names carry the provenance, so a reader can tell a declared claim from a derived
  one.
- **Left out on purpose:** line numbers, and the ledger and Tasker joins. Both would make the
  file change on edits that leave feature membership unchanged. Queries compute them live.
- **Byte-stable output:**
  - sorted keys and lists;
  - two-space indent and a final newline;
  - repo-relative POSIX paths;
  - no timestamps;
  - LF endings, pinned in `.gitattributes`, because Git for Windows converts line endings
    otherwise (that file's header explains why).

**Queries.** They run on the working tree, so they include uncommitted edits. Output is text by
default, `--json` on request.
- `features` lists every feature and its anchors.
- `feature <name>` lists the owners, consumers, tests and overlapping features, plus the ledger
  rows cited in member files and their Tasker coordinates. For `circadian`, that list includes
  DD-038 and DD-061.
- `impact <path>` is the question an agent has just before editing a file. It answers which
  features the file belongs to, which symbols it declares, which files and tests use those
  symbols, and which rows it cites.
- `who-uses <Symbol>` lists references with line numbers.
- `write` regenerates the map; `check` is the guard's mode.

**The check** (`codemap.py check`, run by `scripts/guards/codemap.sh`).
- It fails when:
  - an anchor resolves to no declaration, or to more than one;
  - the map is missing;
  - the map differs, byte for byte, from a fresh generation.
- **Fail-closed.** A stale generated file is wrong every time it fires, which is
  `docs/HARNESS_LOCAL.md`'s test for that tier.
- **Its message** says three things: run `python3 scripts/codemap.py write`, `git add` any new
  Kotlin first, and review the map's diff.
- **The guard is not a duplication detector.** It only checks that a generated file is current
  and that declared anchors exist. That matters because the light-refactor plan's Unit 7
  judged a duplication detector either noisy or easy to satisfy without meeting its intent.
- **Residual.** A session can satisfy the anchor check by deleting an anchor instead of
  updating it. That edit shows in the `features.toml` diff, and the members it drops show in
  the map diff.

**The reinvention tripwire (D3).** For each feature with keywords, it lists the production files
that meet three conditions:
- some identifier contains a keyword as a whole camelCase or snake_case part;
- the file references none of the feature's anchors;
- the file is not an owner.

It reads identifiers only, never comments or strings. It is never a gate. It is the one piece
that can notice a second implementation after it is written; the queries are what prevent one
beforehand. A new class that *should* become an anchor shows up here too, which prompts the
addition.

**CI.** Nothing new. `build.yml` already runs the ladder, and the guard joins it. CI never writes
to a branch (D1).

**Placement.** `release-preflight.yml` fails closed on any path it cannot classify, and a
top-level `codemap/` is such a path (DB-021). So the first unit that adds a file there also adds
`codemap/*` to `classify_path` as non-shipping, plus a pin line. The script and its tests sit under
`scripts/`, which is already classified.

## Owner decisions (open)

1. **D1. Who updates the map.**
   - (a) The author regenerates and CI only checks.
   - (b) A CI job regenerates and commits to the branch.

   **Recommendation: (a).** Option (b) needs `contents: write` in a PR workflow, where every
   workflow that runs on a pull request here holds `contents: read`. It cannot push to a fork's
   PR. It moves the head after review, and it lands the map's diff outside the change that
   caused it. Option (a) is how a lockfile works.
2. **D2. Commit the map, or only the anchors.**
   - (a) Commit the feature-level `codemap/map.json`.
   - (b) Commit only `features.toml`, have queries compute everything live, and have the guard
     check only that anchors resolve.

   **Recommendation: (a).** It is what the owner asked for. A PR's diff then shows a membership
   change ("circadian gained `FooScreen.kt`"), and someone who only reads files (a web reviewer,
   Astra) still sees the map. Revisit if Unit 4's replay shows the map would have changed in
   most merged snapshots.
3. **D3. The reinvention tripwire.**
   - (a) An `unanchored` section in the committed map, never a gate.
   - (b) Only a `codemap.py suspects` query.
   - (c) Drop it.

   **Recommendation: decide on Unit 4's numbers.** Take (a) if the lists are short enough to
   read in full and mostly worth reading; otherwise (b). Under (a), an agent that regenerates
   the map (the guard forces that) sees its own new file listed, at the moment that matters.
4. **D4. The AGENTS.md wording and the light-refactor plan's "one source" paragraph.**
   - (a) Unit 6 adds its own sentence, and amends that plan's Unit 7 draft to name the queries,
     so the two texts agree.
   - (b) Wait, and fold the pointer into light-refactor Unit 7.

   **Recommendation: (a).** Option (b) needs only one rule review, but it leaves the map
   unadvertised until light-refactor Units 1–6 have landed.

## Execution rules

- **Units.** One unit at a time, under playbook 5 and session discipline. Each ends, in order:
  - a STATE Changelog line;
  - acceptance green;
  - the commit;
  - `--guards-only`;
  - a push to the session branch.
- **Reviews.**
  - Any change to the guard, its fixtures, `amh.conf`, `docs/HARNESS_LOCAL.md`, `AGENTS.md`,
    `docs/RUNBOOK.md` or an adapter gets the rule review (DA-005): fresh context, strongest
    tier, no self-review fallback. A session that cannot spawn one parks the change in the
    Owner queue.
  - Units 2–4 change only the tool, and get a fresh-context code review before commit (DA-003
    mechanics).
- **`scripts/tests` is in `RULE_FILES`,** so from Unit 2 the rule-file advisory fires on the
  codemap tests. They are not guard fixtures until Unit 5, and each commit body says so (Q4).
- **Citations.** `scripts/` is in `CITATION_SCAN_PATHS` and `scripts/tests` is excluded. So
  synthetic ledger IDs may appear only under `scripts/tests/codemap/`, never in
  `scripts/codemap.py`.
- **Records.**
  - Units 1, 4, 5 and 8 each write one DE row.
  - Code cites DE rows, never this plan, and no ledger row cites this plan's path.
- **Dependencies.** None new: stdlib only, offline and deterministic like the rest of the
  ladder.

## Units (sequential)

**0. Commit the plan (no code).**
- Write this file.
- In STATE `## Active work`, add the item with a one-line unit checklist.
- Add a STATE Changelog line.
- Run `--guards-only`, commit, run `--guards-only` again, push. **Stop.**

**1. Astra High review of the plan (the owner runs it).**
- The owner gives Astra this file and points it at "Review brief for Astra".
- Agent, next session:
  - Triage every finding by impact, as DD-057 did for an earlier Astra review; here, the impact
    is on what agents will be told. An accepted finding amends the plan; a rejected one gets
    its reason.
  - Route D1–D4, if still open, to the Owner queue.
  - Commit the revised plan, one DE row (the findings and each disposition, naming the plan in
    prose) and a STATE line.
- Acceptance: every finding has a recorded disposition.

**2. The scanner, at symbol level (not wired into anything).**
- `scripts/codemap.py`: the tokenizer, the declaration index, the resolver, and the
  `who-uses` and `impact` queries (`impact` without its feature lines for now).
- Tests in `scripts/tests/codemap/` run against a synthetic Kotlin tree. One case per rule:
  - same-package use;
  - explicit, alias and star imports;
  - a fully qualified name;
  - a `${…}` template;
  - a mention only in a comment or only in a string, which must not count;
  - a raw string and a nested block comment;
  - an extension function;
  - an ambiguous name.
- Determinism: run `who-uses` twice with different `PYTHONHASHSEED` values and from another
  directory, and compare the outputs with `cmp`.
- Real tree: before running the scanner, re-derive the expected caller lists for
  `SolarCalculator` and `ContextSolarTimes` at the unit's base with `grep -rlw` plus a reading.
  Then the scanner's lists must equal them. The `ContextSolarTimes` list must include
  `AndroidContextSignalSource`, the no-import case.
- Acceptance: the tests are green, `cmp` finds no difference, and both real-tree lists match.

**3. Features and queries.**
- Add `codemap/features.toml` with `circadian` only, plus the `classify_path` line in
  `release-preflight.yml`.
- Add feature expansion, the `features` and `feature` queries, overlaps, the query-time ledger
  and Tasker joins, and `write --stdout`. Nothing is committed under `codemap/` except the TOML.
- Tests: expansion; an unresolved anchor and an ambiguous anchor, each an error; overlap; the
  joins, using synthetic IDs.
- Acceptance:
  - the tests are green;
  - `feature circadian` on the real tree lists the five anchor files as owners;
  - its ledger join includes DD-038 and DD-061.

**4. Seed features and measure (still not wired).**
- Add the candidate features below. Every anchor was checked as declared on 2026-10-29, and
  the final lists are this unit's call:
  - `night-light`: `NightLightTemperatureRamp`, `NightLightVerdictStore`, `ColorDisplayBinder`;
  - `handoff`: `HandoffCoordinator`, `HighBrightnessUpdates`, `DisplayBrightnessReader`,
    `DisplayCalibrationStore`;
  - `super-dimming`: `SuperDimmingCoordinator`, `SoftwareDimming`;
  - `contexts`: `ContextEngine`, `ContextMatching`, `ContextSolarTimes`;
  - `privilege`: `PrivilegeManager`, `ShizukuShell`.
- Measure, and record the numbers in one DE row:
  - **Members** per feature, and how many files are members of every feature (hubs such as
    `AppModule`).
  - **The tripwire.** Its list per feature, each entry triaged as a real or plausible second
    implementation, a missing anchor, or noise.
  - **Churn.** Run `git fetch --unshallow` first. Replay the generator over the last 30 merged
    snapshots on `main` and count those whose map would have changed, and why. Count only
    features whose anchors resolve on both sides.
  - **Cost:** the runtime of `check`, and the number of ambiguous names.
  - **The probe baseline (Unit 8)**, run now, before anything advertises the map.
- The owner decides D2 and D3 with these numbers if they are still open.
- Acceptance: every anchor resolves to exactly one declaration, and the DE row holds every
  number above.

**5. The guard and the committed map.**
- Commit `codemap/map.json` and its `.gitattributes` line.
- Add `scripts/guards/codemap.sh`, fail-closed, which runs `python3 scripts/codemap.py check`.
- `scripts/tests/local-guards.sh` gets these cases:
  - a fresh map passes;
  - a stale map, a missing map, an unresolved anchor and an ambiguous anchor each fail;
  - `python3` missing fails and names it.

  `run_guard` copies only the guard into its sandbox, so these cases must copy
  `scripts/codemap.py` too. Check each negative case by mutating the guard and confirming
  exactly one case turns red.
- `scripts/verify.sh` runs the codemap unit tests.
- `amh.conf`: `RULE_FILES` gains `scripts/codemap.py`. The guard's meaning lives there (Q3). The
  two data files stay out (Q5).
- `docs/HARNESS_LOCAL.md`:
  - the split table's "Ours" row;
  - the guard count, eight to nine;
  - "Seven of the eight fail closed" becomes "Eight of the nine";
  - a guard entry: why it is not upstream's job, which tier and why, and the residuals above;
  - the fixture case count.
- Rule review.
- Acceptance: the full `scripts/ladder.sh` is green with the new rung, and the rule-review
  verdict is in the commit body.

**6. Entry points for agents.**
- Run the Unit 8 probe's baseline first if Unit 4 did not.
- **AGENTS.md → Conventions.** Add a sentence; the rule review may trim it. Draft:

  > **Find a feature's existing code before writing new logic.** Run
  > `python3 scripts/codemap.py impact <file>` before editing a file, and
  > `python3 scripts/codemap.py feature <name>` (`features` lists them) before adding behaviour
  > in a feature's area; reuse its owners and read the ledger rows it lists. When you add a
  > class that owns a feature's logic, add it to `codemap/features.toml` as an anchor. The map
  > covers declared features and Kotlin references only, so an empty answer does not show that
  > nothing exists (Session discipline 9).
- **RUNBOOK:**
  - a Reference-doc index row: "Which files implement a feature, and what does a file touch?";
  - one "read first" line in playbooks 4 and 5.
- Per D4, the light-refactor plan's Unit 7 draft names the queries.
- Optionally, per Q6, `scripts/session-facts.sh` prints the feature names at session start.
- Rule review.
- Acceptance: `--guards-only` is green, and the verdict is in the commit body.

**7. Edit-time hint (optional; Claude adapter only).**
- Run this only if D3 kept the tripwire and Unit 4 found it quiet.
- A `PostToolUse` hook on `Write`, for an untracked `.kt` file, runs
  `python3 scripts/codemap.py hint <path>`. It prints the features whose keywords the file
  matches and whose anchors it does not reference.
- It exits 2, so Claude Code feeds the output back, like `comment-budget.sh --hook`. It stays
  silent on no match or on a payload it cannot parse.
- Codex has no post-edit hook. The adapter table in `docs/HARNESS_LOCAL.md` says so, and for
  Codex `AGENTS.md` is the only layer.
- Fixture cases cover the hint mode. Rule review.
- Acceptance: the full ladder is green, and the adapter table row is updated.

**8. Probe and close-out.**
- **The probe.** Give a fresh context this fixed prompt: "Plan, without implementing, a change
  so Night Light's evening ramp starts at civil dusk instead of sunset. List every file you
  would change and every existing behaviour that must stay consistent."
  - It passes if the answer meets all three:
    - it names all three `SolarCalculator` callers;
    - it decides explicitly about the rule tokens in `ContextSolarTimes` (DD-038);
    - it adds no second solar computation.
  - Compare the result with the baseline from Unit 4 or 6, using the same prompt and the same
    model tier. It is one run each, reported as an anecdote, not a statistic.
- **Close-out.**
  - A DE row holds the probe result.
  - The STATE item is removed, with a Changelog line.
  - Delete this file. Its durable content lives in DE rows, `docs/HARNESS_LOCAL.md` and
    `AGENTS.md`.

## Review brief for Astra (Unit 1)

**Scope.**
- In scope:
  - the design, and the order, sizing and gates of the units;
  - the determinism claims;
  - the plan's fit with `AGENTS.md`, `docs/RUNBOOK.md` (playbook 5, session discipline, the
    rule review) and `docs/HARNESS_LOCAL.md` (verdict tiers, fixture conventions, the
    shipped/ours split);
  - the facts it cites, checked against the sources below.
- Out of scope: app code (none changes), the Tasker XML, and the release and PR process.

**Considerations.**
1. **Determinism.** Can two machines, or two Python hash seeds, ever produce different bytes?
   Consider the tracked-set input, sort orders, path forms and line endings.
2. **Goodhart and fatigue.**
   - Will a fail-closed freshness check be regenerated by reflex until nobody reads the diff?
   - Does keeping line numbers and joins out of the map hold its churn down enough?
   - Can the anchor check be satisfied without meeting its intent?
3. **Harness fit.**
   - The ladder is the single entry point, and CI does not write back.
   - Guards discover files from the tracked set (DB-056).
   - Verdict tiers.
   - `run_guard`'s sandbox.
   - Citation scan paths.
   - `RULE_FILES` coverage.
   - `release-preflight.yml`'s fail-closed classification.
4. **Accuracy.** Are the scanner's blind spots acceptable for a navigation aid? Which ones would
   mislead an agent into a wrong "nothing uses this"?
5. **Scope.**
   - Is this a "standalone doc-drift audit", which Decided non-items declines? The plan holds
     that it is not: it audits no prose.
   - Does it contradict the light-refactor plan's "why prose and no guard"?
   - Does it reach past its incidents (DD-038, DD-061 and the light-refactor audit)?
6. **Agent-agnosticism (D-176).** Every layer except the optional hook works for any agent with
   a shell.
7. **Unit sizing.** About one focused hour each, with binary acceptance. Should anything be
   split, merged, reordered or dropped?

**Questions.**
- **Q1.** Is a stdlib tokenizer the right extractor, against tree-sitter (a pip dependency),
  Kotlin PSI or `jdeps` (both tie the map to a build), or SCIP (immature Kotlin and Android
  support)?
- **Q2.** Is a fixed single hop, widened only by adding anchors, right? Should DI hubs such as
  `AppModule` get their own role instead of appearing as consumers?
- **Q3.** Is `scripts/codemap.py` in `RULE_FILES` an acceptable home? The alternative is the
  logic inside the guard, as `comment-budget.sh` is its own extractor.
- **Q4.** Are the codemap tests in `scripts/tests` legislation before Unit 5 wires the guard?
- **Q5.** Should `codemap/features.toml` be in `RULE_FILES`? It changes what agents are told, but
  it would make the advisory fire on every anchor edit.
- **Q6.** Is a session-start line listing the features worth a rule review of
  `scripts/session-facts.sh`?
- **Q7.** Is the probe worth running at n = 1? May it run as a single blocking fresh-context
  subagent instead of an owner session?
- **Q8.** The plan derives overlaps and declares no relation types (`depends_on`,
  `shares_state_with`, `must_remain_consistent_with`). Is anything lost that matters?

**Deliverable.**
- A list of findings, each tagged blocker, should-fix or nit, naming its unit and consideration.
- Answers to Q1–Q8.
- A list of units to drop, and of anything missing.

## Excluded

- **Declared relation types beyond anchors.** Overlaps are derived. The rest would be hand-kept
  prose that drifts.
- **Invariants written as prose strings.** An invariant must point to the test or guard that
  enforces it, and those already live in tests and ledger rows.
- **A separate workflow, or CI commits.** See D1. The ladder is the single entry point.
- **SCIP, tree-sitter, Kotlin PSI and `jdeps`.** They bring a dependency or tie the map to a
  build (Q1).
- **A committed symbol-level graph.** It would change on nearly every PR and conflict across
  session branches. Queries compute that level live.
- **LLM audits in CI.** They are not deterministic. One may propose features for review but is
  never part of the gate.
- **Graph rendering.** Seeing the graph is not the bottleneck; querying it is.
- **Member-level settings fields** (who reads which `AabSettings` field). Deferred: revisit only
  if Unit 4 shows overlaps missing a real coupling.
- **Deriving `doc-facts.sh`'s Shizuku count from the map.** Possible later, but not planned.
- **Scanning XML resources, the manifest, Gradle scripts and `e2e/`.**

## Verification

- **By unit.**
  - Units 0, 1 and 6: `scripts/ladder.sh --guards-only`.
  - Units 2–4: `--guards-only`, plus the codemap unit tests (Unit 2 fixes the exact command)
    and the determinism `cmp`.
  - Units 5 and 7: the full `scripts/ladder.sh`, because `scripts/verify.sh` and the fixture
    suite change.
- Every commit body says what ran and what did not.
- No device verification applies.

**Sources** (in this repository at `282c95f`):
- `AGENTS.md` and `docs/RUNBOOK.md`: playbook 5, Session discipline, the rule-review protocol and
  the acceptance ladder.
- `docs/HARNESS_LOCAL.md`: the split table, the verdict tiers and the fixture conventions.
- `amh.conf`: `RULE_FILES`, `CITATION_SCAN_PATHS`, `CITATION_EXCLUDE` and `REQUIRED_TOOLS`.
- `scripts/ladder.sh` (`guard_repo_local`) and `scripts/tests/local-guards.sh` (`run_guard`).
- `.github/workflows/release-preflight.yml` (`classify_path`) and `.gitattributes`.
- The light-refactor plan's Unit 7 and `docs/rebuild/DECIDED_NON_ITEMS.md`.
- DD-038 and DD-061 in `docs/LEDGER_D.md`.
- `SolarTimes.kt`, `ContextSolarTimes.kt`, `CircadianWindowProvider.kt`, `CircadianChart.kt`
  and `AndroidContextSignalSource.kt`.
