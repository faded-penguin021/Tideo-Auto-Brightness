# Codemap: a generated feature map, so agents find what already exists

## Status

- **Spec only. No code has changed.** The owner asked on 2026-10-29 for this plan, which
  authorised **Unit 0 only**: committing this file on the session branch
  `session/dreamy-bardeen-c4jps1`. Every later unit needs the owner's separate go-ahead.
- **Unit 1 is done.** Astra reviewed the plan at high effort ("Astra High"), and the owner relayed
  the findings on 2026-10-29. All nine findings were accepted and none rejected; this revision
  carries them. The dispositions are under **Unit 1 triage**, and the durable record is a DE row.
  A second pass by ChatGPT, also relayed by the owner, found no blockers; its three residual gaps
  are folded into Units 3 and 5 and the residuals (DE-011).
- Two design passes fed it: this session's and a ChatGPT answer the owner supplied. They agreed
  on the core. Where they differed, the choice and its reason are under Design and Excluded.
- **No unit changes the app.** Nothing under `app/`, `domain/` or `platform/` changes. So there
  is no version bump, no F-Droid changelog, no device step and no glue review.
- No unit opens a PR, tags or releases.
- **No new Markdown.** The deliverable is one TOML file, one Python script and its tests, one
  guard, possibly one generated JSON file (D2), and edits to existing docs. This plan is deleted at
  close-out.
- Owner decisions D1–D4 are open, with recommendations, and routed to the Owner queue. Astra's
  recommendations match them; the review is not the owner's acceptance.
- **Probe baseline:** not yet run. Unit 5 records it here, not in the ledger, so the Unit 8 probe
  cannot read it (see Unit 8).

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
- It has three production callers (the list was checked by reading, not shown exhaustive; Unit 3
  re-derives it):
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

**What it is not.** Every answer is a set of **lexical reference candidates**, not Kotlin semantic
resolution. The tool does not enforce reuse, find every caller, or prove that no duplicate logic
exists. An empty answer means "no references found within the scanned scope", never "unused".
It relates files only through anchors and references, so two files that share state without
referencing each other, such as two readers of one `AabSettings` field, look unrelated (Unit 5
measures how often).
Anchors describe navigation scope: they grant no architectural permission and repeal no invariant.
Shared membership is not a requirement to unify behaviour; DD-061 kept two location orders apart
on purpose, and the ledger rows the queries list are what carry such a difference.

**Three pieces.**
1. **`codemap/features.toml`, kept by hand.** Each feature lists:
   - `anchors`: the top-level Kotlin symbols that own its logic, each a bare name while that name
     is unique in the tree, or package-qualified
     (`com.tideo.autobrightness.domain.circadian.SolarCalculator`) when it is not;
   - optionally `keywords`, which feed the reinvention heuristic (D3).

   Nothing else goes in it: no file lists, no prose, no relation types. An unknown key, a value
   of the wrong type, an empty or duplicated anchor, or a malformed feature name is an error.
   Example, with names verified on 2026-10-29:
   ```toml
   [circadian]
   anchors = ["SolarCalculator", "DynamicScaleEngine", "NightLightTemperatureRamp",
              "ContextSolarTimes", "CircadianWindowProvider"]
   keywords = ["sunrise", "sunset", "solar", "circadian"]
   ```
2. **`scripts/codemap.py`.** Stdlib-only Python, 3.11 or later for `tomllib` (`python3` is
   already in `REQUIRED_TOOLS`). It scans tracked Kotlin, resolves reference candidates, expands
   features, answers queries, and writes and checks the map.
3. **`codemap/map.json`, generated and committed under D2 (a) only.** Feature-level only.

**The scanner's contract.**
- **Input.** The repository root comes from the script's own location (`git -C <script dir>
  rev-parse --show-toplevel`), or from `--root` for the fixtures, never from the working
  directory. Files are tracked `*.kt` under `{app,domain,platform}/src/{main,debug,test}/kotlin`,
  read from `git ls-files -z`. That is the same tracked-set discovery as the other guards, with
  the same DB-056 caveat: a new file is invisible until it is staged. All 316 tracked Kotlin files
  sat under those roots at `282c95f`, and the tree held no Java.
- **Decoding.** Strict UTF-8. CRLF and CR read as one line break, so positions do not depend on
  a checkout's line endings.
- **Failure is loud.** A failing `git` command, an unreadable tracked file, an invalid UTF-8 byte,
  lexical input the tokenizer cannot close (an unterminated string, comment or template, or an
  unbalanced bracket), and depth-0 syntax the declaration reader does not support are each an
  error naming the file and position. The tool never emits a smaller result that looks complete.
- **Roles and source sets.** Files under `src/test` get the role `test` and can never be anchors.
  A `main` file sees `main`; a `test` or `debug` file sees `main` and its own source set.
- **Tokens.** Every token keeps its line and column.
  - A comment becomes one space, so `a/**/b` stays two identifiers. Nested block comments are
    handled.
  - String text is dropped, but both template forms are code: `$name` and `${…}`, nested
    templates included. `\$` and `${'$'}` are text.
  - Raw strings and character literals (`'{'`, `'"'`) are handled, so neither moves a bracket
    depth. These are the traps `comment-budget.sh`'s fixtures already name, such as
    `cb-rawstring`.
- **Declarations.** A top-level declaration is a declaration keyword at nesting depth 0 across
  `()`, `[]` and `{}` together. Brace depth alone is wrong: a constructor property sits at brace
  depth 0 inside its class header's parentheses, as in `DynamicScaleInput`, and a bodyless data
  class has no braces at all.
  - Keywords: `class`, `interface` and `object` with any modifiers (`data`, `enum`, `sealed`,
    `value`, `annotation`, `fun interface`), `typealias`, `fun`, `val` and `var`.
  - A `fun` header's type parameters and receiver are skipped, so `fun <T> Foo<T>.bar()`
    declares `bar`. An expression body, a lambda, an anonymous object and a class body are
    nested, so nothing in them is top-level.
  - Each declaration records its package, name, kind, visibility (`private`, `internal` or
    public) and position. Nested declarations are not indexed.
- **Symbol identity.** A symbol is its package plus its name.
  - Overloads, the same name in one package, are one symbol with several declarations, possibly
    in several files.
  - The same name in two packages is two symbols. The tree has two such pairs today:
    `AnimationConfig` and `ThresholdConfig`, in `app/.../settings` and
    `domain/.../brightness` (verified 2026-10-29).
- **Reference candidates.** An identifier token in file F is a candidate reference to symbol S,
  provided F's source set can see S's, when:
  - S is in F's package and is not `private`, since a private top-level declaration is
    file-scoped;
  - F imports S explicitly, or under an `as` alias, and the imported name occurs in F's code
    after the imports. An import whose name never occurs is reported as `import-only`, never as a
    consumer;
  - F imports a member or nested type of S, such as `import …DisplayCalibration.Point` (10 such
    imports on 2026-10-29). That records a candidate on S of kind `member-import`;
  - F star-imports S's package;
  - or F names S fully qualified.

  An identifier after `.` or `::` still counts: a top-level extension function is called as
  `x.bar()`, and a top-level function is passed as `::bar` (`::hourToHhmm` on 2026-10-29). A
  same-named member is the false positive listed below. `Type::member` and `Type::class` are
  candidates on `Type`.

  A file's references to its own declarations are ignored. `internal` needs no rule while no
  package spans two modules; none did on 2026-10-29, and the scanner fails if one does. On that
  date the tree held 2 star imports, both `kotlin.math.*`, and 0 alias imports.
- **Approximations,** stated in the script's header and in a footer on every text answer:
  - a local name, parameter or member that shadows a top-level one gives a false positive;
  - so does a same-named member of another type, such as `x.compute()` beside a top-level
    `compute`;
  - references from XML, the manifest, Gradle, reflection or generated code are invisible.
- **Ambiguity.** A bare name that matches more than one symbol visible from F records a candidate
  on each, marked ambiguous and counted. An anchor or a `who-uses` argument that matches more
  than one symbol is an error that lists each one with its package; a qualified name resolves it.
  Nothing ever picks the first match.

**Expanding a feature.** Always one hop:
- **owners** are the files that declare an anchor;
- **consumers** are the production files that reference one, each listed with the anchors it
  references;
- **tests** are the test files that reference one.

A feature grows only when someone adds an anchor, a one-line change a reviewer can see.
Multi-hop expansion is excluded: through a hub like `AppModule` it would pull in the whole app.
DI hubs stay consumers, and Unit 5 measures how much noise they add; no separate role is planned
until that shows a need. An anchor may belong to more than one feature. Night Light and circadian
share `NightLightTemperatureRamp`, for example. Features that share members are reported as
**overlapping**, which is derived, not declared, and says nothing about whether their behaviour
should agree.

**The committed map (D2 (a) only).**
- **Contents.** `schema`, then per feature:
  - `declared`: the anchors, each with its qualified symbol and its file;
  - `derived`: owners, consumers (each with the anchors it uses), tests and, under D3 (a),
    `unanchored`.

  The two key names carry the provenance, so a reader can tell a declared claim from a derived
  one.
- **Left out on purpose:** line numbers, and the ledger and Tasker joins. Both would make the
  file change on edits that leave feature membership unchanged. Queries compute them live.

**Determinism contract.** Identical supported inputs produce identical bytes. A different tracked
set or different file contents are different inputs, and may produce a different map.
- Keys are sorted. Lists are deduplicated and sorted by repo-relative POSIX path, then by symbol.
- Two-space indent, UTF-8 and a final newline. The writer writes bytes with LF endings, so
  Python never translates newlines.
- No timestamps, absolute paths or environment data.
- LF is also pinned in `.gitattributes`, because Git for Windows converts line endings otherwise
  (that file's header explains why).
- **Tests compare the JSON bytes,** not only query text: two `PYTHONHASHSEED` values, two working
  directories, and LF and CRLF copies of one fixture tree must all give the same bytes, and a
  second `write` must change nothing.

**Queries.** They run on the working tree, so they include uncommitted edits. Output is text by
default, `--json` on request. Every answer states its scope and approximations. Distinct exit
statuses separate a search that succeeded (with or without results) from an unknown symbol, an
ambiguous symbol, an unknown feature, a path outside the scanned scope, and malformed input; Unit 3
fixes the numbers. A successful empty answer reads "no references found within scanned scope".
- `features` lists every feature and its anchors.
- `feature <name>` lists the owners, consumers, tests and overlapping features, plus the ledger
  rows and Tasker coordinates cited in member files.
  - Each row is listed with the files that cite it, with owner-file citations apart from
    consumer-file citations, so rows from a large consumer do not bury the relevant ones.
  - This is file-level context. It does not show that every row governs every anchor.
  - For `circadian`, the list includes DD-038 and DD-061.
- `impact <path>` is the question an agent has just before editing a file. It answers which
  features the file belongs to, which symbols it declares, which files and tests use those
  symbols, and which rows it cites.
- `who-uses <Symbol>` lists the candidates with line numbers and their kind (same-package,
  import, member-import, star, qualified), with `import-only` lines apart.
- `suspects [<feature>]` runs the keyword heuristic (D3).
- `write` regenerates the map, and `write --stdout` prints it. `check` is the guard's mode, and
  `check --anchors-only` is its D2 (b) form.

**The check** (`codemap.py check`, run by `scripts/guards/codemap.sh`).
- It fails when:
  - `features.toml` is malformed;
  - an anchor resolves to no symbol, or to more than one;
  - the scan fails (above);
  - under D2 (a) only, the map is missing, or it differs byte for byte from a fresh generation.
- `check --anchors-only` runs the first three.
- **Fail-closed.** A stale generated file is wrong every time it fires, which is
  `docs/HARNESS_LOCAL.md`'s test for that tier.
- **Its message** says three things: run `python3 scripts/codemap.py write`, `git add` any new
  Kotlin first, and review the map's diff.
- **The guard is not a duplication detector.** It only checks that declared anchors exist and,
  under D2 (a), that a generated file is current. That matters because the light-refactor plan's
  Unit 7 judged a duplication detector either noisy or easy to satisfy without meeting its intent.
- **Residual.** A session can satisfy the anchor check by deleting an anchor instead of
  updating it. That is a reviewable escape hatch, not something the gate prevents: the edit shows
  in the `features.toml` diff, and under D2 (a) the members it drops show in the map diff.
- **Residual: coverage.** The check proves that declared anchors exist, never that the registry
  is complete. A new feature that nobody registers is invisible to every query. The `AGENTS.md`
  sentence (Unit 7) and `suspects` are the only prompts, and no guard is planned: completeness is
  a judgment, and the minimal registry is the chosen design.

**The reinvention heuristic (D3).** For each feature with keywords, it lists the production files
that meet three conditions:
- some identifier contains a keyword as a whole camelCase or snake_case part;
- the file references none of the feature's anchors;
- the file is not an owner.

It reads identifiers only, never comments or strings. Its matches are leads, not evidence of a
second implementation: a file can duplicate logic while referencing an anchor, and a legitimate
file can match a keyword without referencing one. It is never a gate. Under D3 (a) the list is
serialised in the map; a listed file never fails the check, but a stale serialisation of the list
does, like any other stale map content.

**CI.** Nothing new. `build.yml` already runs the ladder, and the guard joins it. CI never writes
to a branch (D1).

**Placement.**
- `scripts/codemap.py` is an unshipped repo-local script. `AGENTS.md` → Harness makes adding one
  a rule change and requires it to be named in `RULE_FILES` and in `docs/HARNESS_LOCAL.md`'s
  split table **in the commit that adds it**. So Unit 2 does both, adds
  `scripts/tests/codemap/` to that table's "Ours" row, and updates `AGENTS.md`'s count of such
  scripts ("the two that exist"), all under the rule review.
- `release-preflight.yml` fails closed on any path it cannot classify, and a top-level
  `codemap/` is such a path (DB-021). So Unit 4, the first unit that adds a file there, also adds
  `codemap/*` to `classify_path` as non-shipping, plus a pin line. The script and its tests sit
  under `scripts/`, which is already classified.

## Owner decisions (open)

All four are routed to the Owner queue. Astra recommended the same four options.

1. **D1. Who updates the map.**
   - (a) The author regenerates and CI only checks.
   - (b) A CI job regenerates and commits to the branch.

   **Recommendation: (a).** Option (b) needs `contents: write` in a PR workflow, where every
   workflow that runs on a pull request here holds `contents: read`. It cannot push to a fork's
   PR. It moves the head after review, and it lands the map's diff outside the change that
   caused it. Option (a) is how a lockfile works.
2. **D2. Commit the map, or only the anchors.**
   - (a) Commit the feature-level `codemap/map.json`; the guard runs `check`.
   - (b) Commit only `features.toml`. Queries compute everything live, the guard runs
     `check --anchors-only`, and nothing checks a map's freshness because there is no map.

   **Recommendation: (a), provisionally, subject to Unit 5's replay.** It is what the owner asked
   for. A PR's diff then shows a membership change ("circadian gained `FooScreen.kt`"), and
   someone who only reads files (a web reviewer, Astra) still sees the map. Take (b) if the replay
   shows the map would have changed in most merged snapshots.
3. **D3. The reinvention heuristic.**
   - (a) An `unanchored` section in the committed map, never a gate.
   - (b) Only the `codemap.py suspects` query.
   - (c) Drop it.

   **Recommendation: (b) initially.** Keyword matches are leads, not findings. Promote to (a)
   only if Unit 5's numbers show the lists are short and mostly worth reading.
4. **D4. The AGENTS.md wording and the light-refactor plan's "one source" paragraph.**
   - (a) Unit 7 adds its own sentence, and amends that plan's Unit 7 draft to name the queries,
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
  - **Rule review** (DA-005: fresh context, strongest tier, no self-review fallback) for any
    change to `AGENTS.md`, `docs/RUNBOOK.md`, `docs/HARNESS_LOCAL.md`, `amh.conf`,
    `scripts/verify.sh`, the guard, its fixtures or an adapter. That is Units 2, 6 and 7. A
    session that cannot spawn one parks the change in the Owner queue.
  - From Unit 6 the codemap tests define guard acceptance, so Unit 6's rule review also covers
    `codemap.py`'s `check` path and those tests.
  - Units 3–5 change only the tool or its data, and get a fresh-context code review before
    commit (DA-003 mechanics). `scripts/codemap.py` and `scripts/tests` are in `RULE_FILES` from
    Unit 2, so the rule-file advisory fires on them. Each commit body says why the rule review
    does not apply yet (Q4).
- **Verification.** From Unit 2, `scripts/verify.sh` runs the codemap tests, so every unit that
  changes code runs the full `scripts/ladder.sh`. `--guards-only` is kept for the units that
  change only documentation.
- **Subagents.** At most one at a time, and blocking: a review, or the probe (Units 5 and 8). That
  is sequential work under Session discipline 1, and this plan allows the probe explicitly.
- **Citations.** `scripts/` is in `CITATION_SCAN_PATHS` and `scripts/tests` is excluded. So
  synthetic ledger IDs may appear only under `scripts/tests/codemap/`, never in
  `scripts/codemap.py`.
- **Records.**
  - Units 1, 5, 6 and 8 each write one DE row.
  - Code cites DE rows, never this plan, and no ledger row cites this plan's path.
  - No row states the probe's prompt or pass criteria before Unit 8.
- **Dependencies.** None new: stdlib only, offline and deterministic like the rest of the
  ladder.

## Units (sequential)

**0. Commit the plan (no code).** Done.

**1. Astra High review of the plan.** Done: every finding has a disposition (**Unit 1 triage**),
D1–D4 are in the Owner queue, and the DE row is written.

**2. Placement, tokenizer and declaration index.**
- **Placement, in the same commit** (`AGENTS.md` → Harness):
  - `amh.conf`: `RULE_FILES` gains `scripts/codemap.py`. The guard's meaning will live there
    (Q3). The two data files stay out (Q5).
  - `docs/HARNESS_LOCAL.md`: the split table's "Ours" row gains `scripts/codemap.py` and
    `scripts/tests/codemap/`.
  - `AGENTS.md` → Harness: the count of unshipped repo-local scripts names three.
  - `scripts/verify.sh` runs the codemap tests; this unit fixes the exact command.
- `scripts/codemap.py`: root discovery, `git` input, strict decoding, the tokenizer, the
  declaration index, error reporting, and a `declarations [<path>]` query that prints the index.
- Tests in `scripts/tests/codemap/` on a synthetic Kotlin tree, one case each:
  - **tokens:** line, block and nested comments; `a/**/b`; a raw string; `$name`; `${…}`; a
    nested template; `\$`; `${'$'}`; the character literals `'{'` and `'"'`;
  - **declarations:** constructor properties; a bodyless class; a generic extension function;
    an expression-bodied function; an anonymous object; a top-level property with a getter;
    each class-like modifier; `private` and `internal`; overloads;
  - **malformed input:** an unterminated string, comment and template; an unbalanced bracket;
    invalid UTF-8; unsupported depth-0 syntax; each fails and names its position.
- Determinism: `declarations --json` gives the same bytes under two `PYTHONHASHSEED` values,
  from two working directories, and for LF and CRLF copies of the fixture tree.
- Real tree: every tracked Kotlin file scans without error, and `DynamicScaleInput`'s
  constructor properties are not top-level declarations.
- Rule review.
- Acceptance: the full ladder is green with the new tests, `cmp` finds no difference, the real
  tree scans cleanly, and the rule-review verdict is in the commit body.

**3. Reference candidates.**
- The resolver as **The scanner's contract** states it: same-package use (visibility and source
  set), explicit and alias imports with the use check, `import-only`, `member-import`, star
  imports, fully qualified names, ambiguity, and the one-module-per-package check.
- The `who-uses` and `impact` queries (`impact` without its feature lines for now), their exit
  statuses and the empty-answer wording.
- Tests, one case per rule, plus:
  - a callable reference `::bar`, `Type::member`, `Type::class`, an extension call `x.bar()`
    and a qualified `Obj.member`;
  - a same-named member of another type, which is the documented false positive;
  - an unused import;
  - a `private` declaration beside a same-named identifier in another file of its package;
  - an imported nested type;
  - a test-source declaration that a `main` file cannot see;
  - an ambiguous name, then a qualified name that resolves it;
  - an unknown symbol, a path outside the scanned scope, and each exit status.
- Determinism as in Unit 2, on `who-uses --json` and `impact --json`.
- **Real-tree corpus.** Two solar symbols are too easy a check, so the corpus covers each
  construct. Before running the scanner, derive each expected list at the unit's base with
  `grep -rlw` plus a reading, as matches and non-matches (files that name the symbol only in a
  comment or string, or mean another symbol of that name). Then the scanner's lists must equal
  them:
  - `SolarCalculator` (imports and qualified calls) and `ContextSolarTimes`, whose list must
    include `AndroidContextSignalSource`, the no-import case;
  - `hourToHhmm`, a top-level `internal` function reached through `::hourToHhmm`;
  - one top-level extension function, chosen at the unit's base, called as `x.f()`;
  - `DisplayCalibration`, reached through `import …DisplayCalibration.Point`;
  - both `ThresholdConfig` symbols, each list excluding the other package's users.

  The code reviewer re-derives the corpus lists independently.
- Fresh-context code review.
- Acceptance: the full ladder is green, `cmp` finds no difference, and every corpus list
  matches, non-matches included.

**4. Features, queries, write and check.**
- Add `codemap/features.toml` with `circadian` only, plus the `classify_path` line and its pin
  line in `release-preflight.yml`.
- Add the TOML validation, anchor resolution (bare and qualified), feature expansion, overlaps,
  the `features`, `feature` and `suspects` queries, the query-time ledger and Tasker joins with
  their file provenance, `write`, `write --stdout`, `check` and `check --anchors-only`. Nothing
  is committed under `codemap/` except the TOML.
- Tests:
  - each TOML error (unknown key, wrong type, empty anchor list, duplicate anchor), and an
    unknown feature;
  - an unresolved anchor and an ambiguous anchor, each an error, and a qualified anchor that
    resolves;
  - expansion, overlap, and `suspects`;
  - the joins with synthetic IDs, owner-file and consumer-file citations apart;
  - `check` on a fresh, a stale and a missing map, and `check --anchors-only`;
  - the map's JSON bytes under the determinism contract, and a second `write` that changes
    nothing.
- Fresh-context code review.
- Acceptance:
  - the full ladder is green;
  - `feature circadian` on the real tree lists the five anchor files as owners;
  - its ledger join includes DD-038 and DD-061, each with the files that cite it.

**5. Seed features and measure (still not wired).**
- Add the candidate features below. Every anchor was checked as declared on 2026-10-29, and
  the final lists are this unit's call:
  - `night-light`: `NightLightTemperatureRamp`, `NightLightVerdictStore`, `ColorDisplayBinder`;
  - `handoff`: `HandoffCoordinator`, `HighBrightnessUpdates`, `DisplayBrightnessReader`,
    `DisplayCalibrationStore`;
  - `super-dimming`: `SuperDimmingCoordinator`, `SoftwareDimming`;
  - `contexts`: `ContextEngine`, `ContextMatching`, `ContextSolarTimes`;
  - `privilege`: `PrivilegeManager`, `ShizukuShell`.
- Measure, and record the numbers in one DE row:
  - **Members** per feature, how many files are members of every feature, and for each hub
    (such as `AppModule`) the anchors it references.
  - **The heuristic.** The `suspects` list per feature, each entry triaged as a real or
    plausible second implementation, a missing anchor, or noise.
  - **Churn.**
    - If `git rev-parse --is-shallow-repository` prints `true`, run `git fetch --unshallow`;
      otherwise do not. It printed `true` on 2026-10-29, with 49 first-parent commits of
      `origin/main` visible.
    - Record the `origin/main` tip SHA. Take up to 30 consecutive first-parent snapshots back
      from it; if history holds fewer, use what there is and report the smaller number.
    - Run this unit's generator and `features.toml`, fixed, over each snapshot's own tracked
      files, so a tool change cannot pass for membership churn.
    - Count the consecutive pairs whose map differs, and why. Report, per feature, the eligible
      pairs and the pairs excluded because an anchor did not resolve on one side.
  - **Shared state.** The map relates files only through anchors, so two readers of one setting
    look unrelated. For each seeded feature, list the `AabSettings` fields (its groups' fields
    included) that its owner files read by name, then the production files outside the feature
    that read any of them. Triage each file as a coupling the `feature` answer should have
    shown, or noise, and record whether any would have changed a `feature` or `impact` answer.
    The measuring script stays in scratch; nothing is committed. Stores and other state holders
    are top-level classes, so they are already visible as references once anchored.
  - **Cost:** the runtime of `check` and of `check --anchors-only`, the number of ambiguous
    names, and the number of anchors that needed qualifying.
- **The probe baseline (Unit 8),** run now, before anything advertises the map: one blocking
  fresh-context subagent on a worktree at `282c95f`, which predates this plan, with Unit 8's
  prompt and nothing else (no map, no plan, no review transcript). Record the model tier, the
  verdict on each criterion and whether it already passes in this plan's Status, not in the DE
  row.
- The owner decides D2 and D3 with these numbers if they are still open.
- Fresh-context code review.
- Acceptance: the full ladder is green, every anchor resolves to exactly one symbol, the DE row
  holds every number above, and the baseline is recorded in Status.

**6. The guard, per D2.**
- **D2 (a):** commit `codemap/map.json` and its `.gitattributes` line; the guard runs
  `python3 scripts/codemap.py check`.
- **D2 (b):** no map and no `.gitattributes` line; the guard runs `check --anchors-only`.
- **D3:** under (a) the map gains `unanchored`; under (b) nothing changes; under (c) the
  `suspects` query and the `keywords` key are removed with their tests.
- Add `scripts/guards/codemap.sh`, fail-closed.
- `scripts/tests/local-guards.sh` gets these cases:
  - a fresh tree passes;
  - a stale map and a missing map (D2 (a) only), an unresolved anchor, an ambiguous anchor and a
    malformed `features.toml` each fail;
  - `python3` missing fails and names it.

  `run_guard` copies only the guard into its sandbox, so these cases must copy
  `scripts/codemap.py` too. Check each negative case by mutating the guard and confirming
  exactly one case turns red.
- `docs/HARNESS_LOCAL.md`:
  - the guard count, eight to nine;
  - "Seven of the eight fail closed" becomes "Eight of the nine";
  - a guard entry: why it is not upstream's job, which tier and why, and the residuals above;
  - the fixture case count.
- Rule review, including `codemap.py`'s `check` path.
- Acceptance: the full `scripts/ladder.sh` is green with the new rung, the rule-review verdict is
  in the commit body, and a DE row records the D2 and D3 outcome and the guard's tier.

**7. Entry points for agents.**
- **AGENTS.md → Conventions.** Add a sentence; the rule review may trim it. Draft:

  > **Find a feature's existing code before writing new logic.** Run
  > `python3 scripts/codemap.py impact <file>` before editing a file, and
  > `python3 scripts/codemap.py feature <name>` (`features` lists them) before adding behaviour
  > in a feature's area; reuse its owners and read the ledger rows it lists. When you add a
  > class that owns a feature's logic, or a new feature, add it to `codemap/features.toml`. The
  > answers are lexical candidates from Kotlin sources: an empty one means none were found in
  > the scanned scope, not that nothing exists (Session discipline 9), and two files sharing a
  > feature does not mean their behaviour must be unified.
- **RUNBOOK:**
  - a Reference-doc index row: "Which files implement a feature, and what does a file touch?";
  - one "read first" line in playbooks 4 and 5.
- Per D4, the light-refactor plan's Unit 7 draft names the queries.
- No session-start feature list (Q6). Add one only if the probe shows that agents fail to
  discover the queries.
- Rule review.
- Acceptance: `--guards-only` is green, and the verdict is in the commit body.

**8. Probe and close-out.**
- **The probe.** One blocking fresh-context subagent, at the baseline's model tier, on a worktree
  of the close-out candidate with this plan file removed and without any review transcript.
  Prompt, the same as the baseline's: "Plan, without implementing, a change so Night Light's
  evening ramp starts at civil dusk instead of sunset. List, separately, the files you read, the
  files you would change with the reason for each, and every existing behaviour that must stay
  consistent."
  - It passes if the answer meets all five:
    - the files it read include all three `SolarCalculator` callers;
    - it proposes no change to a caller that the requested behaviour does not reach, or it says
      why the change is needed;
    - it decides explicitly about the rule tokens in `ContextSolarTimes` (DD-038);
    - it keeps the two location orders distinct (DD-061);
    - it adds no second solar computation.
  - Compare it with the Unit 5 baseline, criterion by criterion. It is one run each, reported as
    an anecdote, not an effectiveness estimate.
- **Close-out.**
  - A DE row holds the probe result and the baseline.
  - The STATE item is removed, with a Changelog line.
  - Delete this file. Its durable content lives in DE rows, `docs/HARNESS_LOCAL.md` and
    `AGENTS.md`.

## Unit 1 triage (Astra High, relayed by the owner, 2026-10-29)

Astra checked the plan's facts at `282c95f`: the 316 Kotlin files and their scan roots, no Java,
the three named `SolarCalculator` callers and the no-import `ContextSolarTimes` use. It did not
establish that the caller list is exhaustive, which Unit 3 now does. Every finding was accepted.

| # | Finding | Disposition |
|---|---|---|
| 1 | **Blocker.** Brace depth 0 does not find top-level declarations; Unit 2 is too big | Accepted. Verified on `DynamicScaleInput`. Declarations now use nesting depth across all three bracket kinds, with Astra's fixtures. Unit 2 is split into Units 2 and 3. |
| 2 | **Blocker.** Results are lexical candidates, not semantic usage; blind spots incomplete | Accepted. **What it is not**, the reference rules (`$name`, member imports, visibility, source sets, `import-only`), the approximations footer, and the empty-answer wording and exit statuses. |
| 3 | Determinism needs a full input and output contract | Accepted. **Determinism contract**: root discovery, `ls-files -z`, strict UTF-8, LF written as bytes, comment-as-space, and byte tests across seeds, directories and line endings, plus an idempotent second write. |
| 4 | The extension-point amendment comes too late; `--guards-only` misused for code | Accepted and widened. `AGENTS.md` → Harness already requires `RULE_FILES` and the split table in the commit that adds the script, so Unit 2 does both, with the count in `AGENTS.md`, under rule review. `verify.sh` runs the tests from Unit 2, so code units run the full ladder. |
| 5 | `check` measured before any unit builds it; D2 (b) undefined | Accepted. Unit 4 builds `write`, `check`, `check --anchors-only` and `suspects`. Unit 6 carries both D2 branches. D1–D4 are routed to the Owner queue. |
| 6 | Symbol identity and malformed input undefined | Accepted. A symbol is package plus name, overloads are one symbol, anchors may be qualified, ambiguity fails closed, and malformed TOML, `git` failures, unreadable files and unclosed or unsupported syntax are errors. |
| 7 | The replay is not reproducible and hides its exclusions | Accepted. Unit 5's churn steps: a recorded tip, first-parent snapshots, each snapshot's own files, a fixed generator, reported exclusions, and unshallow only when shallow. |
| 8 | Ledger joins can bury rows; the probe conflates reading and changing | Accepted. Joins list citing files, owner and consumer apart. The probe separates files read from files changed, adds the location-order criterion, and isolates the baseline from the map, the plan and this review. |
| 9 | Keyword matches are leads; the edit-time hint is premature | Accepted. D3 now recommends (b). The hint (old Unit 7) moves to Excluded. |

**Questions.** Astra's answers are adopted, with one correction:
- **Q1.** A stdlib tool is right for a bounded lexical navigation aid, never described as
  semantic resolution. Reconsider a parser dependency only if supporting the tree's syntax grows
  into a parser project.
- **Q2.** One hop, widened through anchors; DI hubs stay consumers, shown with their anchors, and
  Unit 5 measures their noise.
- **Q3.** `scripts/codemap.py` belongs in `RULE_FILES`, with the logic in Python and the guard
  thin. Astra placed it there "when it becomes part of enforcement"; `AGENTS.md` → Harness binds
  earlier, at the commit that adds the script, so it lands in Unit 2.
- **Q4.** Tool tests are not legislation because of their directory. The rule review applies to
  any unit that changes placement or verification policy, and to the tests once they define guard
  acceptance (Unit 6).
- **Q5.** `features.toml` and the map stay out of `RULE_FILES`; anchor edits get normal review
  of the TOML and membership diffs. The boundary is stated under **What it is not**.
- **Q6.** No session-start feature list for now.
- **Q7.** The probe runs at n = 1 as an anecdotal acceptance exercise, as one blocking
  fresh-context subagent, with the same model tier, prompt and access as its baseline.
- **Q8.** No relation types for now. Overlap cannot say "share this calculation and keep these
  policies apart"; the ledger rows carry that, and the probe tests that they are recovered.

**Second pass (ChatGPT, relayed by the owner, 2026-10-29).** No blockers; it would retain the
one-hop map, qualified anchors, the lexical-only claims, the byte checks, the owner/consumer split
and the absence of relation types. Its three residual gaps:

| # | Gap | Disposition |
|---|---|---|
| 1 | Features coupled through shared state (one `AabSettings` field) look unrelated | Accepted. The Excluded settings item named Unit 5 as its trigger, but no unit measured it. Unit 5 now does, and **What it is not** discloses the gap. |
| 2 | Two solar symbols are too easy a real-tree check | Accepted. Unit 3's corpus adds a callable reference, an extension call, a nested-type import and `ThresholdConfig`'s two packages as non-matches, re-derived by the reviewer; the reference rules now say that identifiers after `.` or `::` count, and Unit 3 adds synthetic cases for them. |
| 3 | Nothing notices an unregistered feature | Accepted as a residual, with no guard, as it advised. **Residual: coverage** states it, and Unit 7's draft asks for new features as well as new anchors. |

## Excluded

- **Declared relation types beyond anchors.** Overlaps are derived. The rest would be hand-kept
  prose that drifts (Q8).
- **Invariants written as prose strings.** An invariant must point to the test or guard that
  enforces it, and those already live in tests and ledger rows.
- **A separate workflow, or CI commits.** See D1. The ladder is the single entry point.
- **SCIP, tree-sitter, Kotlin PSI and `jdeps`.** They bring a dependency or tie the map to a
  build (Q1). Even a parser would not give semantic resolution.
- **A committed symbol-level graph.** It would change on nearly every PR and conflict across
  session branches. Queries compute that level live.
- **LLM audits in CI.** They are not deterministic. One may propose features for review but is
  never part of the gate.
- **Graph rendering.** Seeing the graph is not the bottleneck; querying it is.
- **Member-level settings fields** (who reads which `AabSettings` field). Deferred: revisit only
  if Unit 5's shared-state measurement shows a real coupling that a `feature` or `impact` answer
  misses. It is the strongest candidate for a later extension.
- **A registry-completeness guard.** See **Residual: coverage**.
- **An edit-time hint** (a Claude-only `PostToolUse` hook on a new `.kt` file). Deferred until the
  probe shows the queries help: it adds adapter-specific behaviour and a review surface before
  the main intervention is evaluated, and Codex has no post-edit hook.
- **A session-start feature list** (Q6).
- **Deriving `doc-facts.sh`'s Shizuku count from the map.** Possible later, but not planned.
- **Scanning XML resources, the manifest, Gradle scripts and `e2e/`.**

## Verification

- **By unit.**
  - Units 0, 1 and 7: `scripts/ladder.sh --guards-only`; they change only documentation.
  - Units 2–6: the full `scripts/ladder.sh`, whose `scripts/verify.sh` runs the codemap tests from
    Unit 2; plus each unit's determinism `cmp`.
  - Unit 8: `--guards-only`, after the probe.
- Every commit body says what ran and what did not.
- No device verification applies.
