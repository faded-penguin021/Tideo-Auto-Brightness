# HARNESS_LOCAL — what this repo adds on top of stock AMH

This repository runs the **Agentic Maintenance Harness**
([`faded-penguin021/AMH`](https://github.com/faded-penguin021/AMH)), adopted at **amh-v9.1.0**
and upgraded to **amh-v14.0.0**, under the `full` profile. `AGENTS.md` is the constitution, `docs/STATE.md` the working memory,
`docs/LEDGER*.md` the permanent registry, `docs/RUNBOOK.md` the playbooks, `scripts/ladder.sh`
the one verification entrypoint.

**This file exists so an upgrade stays a file copy.** It is the single place recording every
way this repo differs from a stock install — so whoever upgrades can diff the shipped scripts,
read `harness/CHANGELOG.md` forward, and know exactly what local surface has to be re-checked
without reverse-engineering it from the tree. Keep it current in the same change that alters
any of it.

## The split, in one table

| | Files | Rule |
|---|---|---|
| **Shipped** | `scripts/{ladder,session-start,command-guard,redact,test-ladder-guards}.sh`, `scripts/MANIFEST.sha256` | **Never edit.** Parameter-free, they read `amh.conf` at runtime. The ladder's integrity rung hashes them against the manifest every run, so a local edit is reported rather than discovered a year later by whoever upgrades. |
| **Ours** | `amh.conf`, `scripts/verify.sh`, `scripts/guards/*.sh`, `scripts/bootstrap.sh`, `scripts/session-facts.sh`, `scripts/tests/local-guards.sh` | The extension points. Everything repo-specific goes in one of these — if a change fits none of them, the harness is missing an extension point; raise it upstream rather than carrying a local patch. |
| **Ours (prose)** | `AGENTS.md`, `CLAUDE.md`, `docs/{STATE,RUNBOOK,LEDGER*}.md`, `docs/history/` | Seeded once, ours thereafter. Upgrades arrive as hand-applied notes, never a re-sync. |
| **Ours (config)** | `.github/workflows/*`, `.claude/settings.json`, `.codex/*` | Written only when absent. Diff each against its upstream template on an upgrade and take what applies. |

## Where our old harness went

The AMH was generalized *from* this repo, spun out, and diverged; converging meant deleting our
copy of everything upstream now owns. Our old ladder ran 11 numbered guards:

| Old guard | Now |
|---|---|
| 1 / 1a — STATE size band + compression landing | shipped `guard_state_size` (plus an edit-delta refinement we never had) |
| 1b — required sections | shipped `guard_state_structure` (also catches duplicate headings) |
| 1c — ledger rollover | shipped `guard_ledger_rollover` |
| 2 — skip-ci tokens | shipped `guard_poison_tokens`, token list in `POISON_TOKENS` |
| 3 / 4 / 7 — checkpoint, stale-branch, rule-review tripwires | shipped `advisories` |
| 5 — citation integrity + `[cited]` sync | shipped `guard_citations` |
| 8 / 10 — redact and command-guard self-tests | shipped `guard_rail_selftests` |
| 9 — secret-shape scan, **worktree half** | shipped `guard_secret_shapes` |
| 9 — secret-shape scan, **staged half** | **ours:** `scripts/guards/staged-secrets.sh` |
| 6 — F-Droid changelog cap | **ours:** `scripts/guards/fdroid-changelog.sh` |
| 11 — falsifiable doc-facts | **ours:** `scripts/guards/doc-facts.sh` |
| 5 — citation *prefix→file* mapping | **ours:** `scripts/guards/ledger-prefix.sh` |
| — no upstream rung — format-arg resolution | **ours:** `scripts/guards/format-args.sh` |

We **gained** three rungs with no local predecessor: `guard_author_identity`,
`guard_shipped_integrity` and `guard_repo_local`.

`docs/AGENTIC_HARNESS_PROMPT.md` — the v1.8 generalization of this harness — was deleted. It is
the AMH repository now, and a stale local snapshot would read as authoritative to a future
session while drifting against every upstream release.

## The eight repo-local guards, and why each is not upstream's job

- **`fdroid-changelog.sh`** — F-Droid flags a `whatsNew` over 500 **characters**, and it measures
  codepoints, not bytes. `wc -c` rejects a legal note full of em dashes. Only the current
  `versionCode`'s file is checked; existence is `release-preflight.yml`'s job, which knows
  whether the PR ships app code.
- **`doc-facts.sh`** — one machine anchor per load-bearing prose claim that has *already* drifted
  in a shipped commit. The incident-only bar is binding (DA-015): this is not a doc-testing
  framework, and a fact proposed without a named drift commit was reviewed out once already.
  Three anchors today: the Shizuku runtime-site count (`d66de4c`), the parenthesised sub-item
  citation form (`3949383` — the whole-word matcher makes the bare-suffixed form resolve to
  nothing, so the shipped citation rung stays green while a row loses its pointer, DB-022), and
  the constitution's AMH version against `AMH_VERSION` (DB-019, where the two drifted apart
  because the constitution carried no number to compare).
- **`staged-secrets.sh`** — the shipped scan reads the working tree, which misses a secret that
  was staged and then edited out of the file on disk. The index is what `git commit` records.
- **`ledger-prefix.sh`** — the shipped citation guard pools every volume and asks only whether an
  ID resolves *somewhere*, which is right for it: it cannot know an adopter's volume naming.
  Here the naming is the rule, so a `DB-` row misfiled into `LEDGER_A.md` resolves cleanly and
  passes upstream.
- **`comment-budget.sh`** — the constitution puts durable prose in the `.md` tier and leaves a
  `D-NNN` pointer in the code, and nothing enforced it: the tree reached 7620 comment lines
  against 40651 lines of Kotlin, much of it re-telling a ledger row verbatim. Upstream cannot own
  this — it ships no language-aware scanner and has no view on an adopter's comment conventions.
  Three checks: a 12-line cap on any contiguous comment block (the structural one — narrative does
  not fit in 12 lines, so it has to go to the `.md` and leave a pointer); a per-module comment-line
  budget (which catches density arriving as hundreds of individually-reasonable two-line comments,
  the way the tree actually got here); and a provenance manifest, described below, which is a floor
  rather than a cap because the first two checks are what endanger it. Counting is a real Kotlin
  scan, not a grep for a leading slash: `//` inside a string is code, a raw string of `//` lines is
  code,
  and block comments nest. The fixture suite's `cb-rawstring` case exists to fail the naive
  implementation, and does. It also runs as a `--hook` mode; see the adapter table below.

  **Its one exemption, and what holds the line.** KDoc tag lines — `@param`, `@return`, `@throws`,
  `@see`, `@sample`, and *only* those five — do not count toward the block cap, because a
  seven-parameter KDoc cannot fit in 12 lines and counting them made deleting the parameter docs
  the cheapest way to pass. That is the guard's only hole, and a hole nobody counts grows one
  individually-reasonable entry at a time, which is precisely how the comment bloat happened.
  So the boundary is pinned three ways: five cases assert each exempt tag passes, two behavioural
  cases assert `@note` and `@property` still **fail**, and one case pins the exemption **regex
  literal** so any change to the set turns it red whatever the new tag is. That literal pin exists
  because the behavioural cases alone were not enough — the DA-005 pass widened the set to
  `@property|@constructor|@receiver`, the three a Kotlin repo would actually reach for, and every
  case stayed green. A prose claim of a tripwire that only covers one arbitrary tag is the
  enforcement asymmetry this file warns about elsewhere. **None of this prevents a session from
  widening the set** — it cannot; editing the guard and the fixtures together passes. What it buys
  is that the widening cannot be silent or accidental: it must appear in the diff of two files
  that are both in `RULE_FILES`, where the rule-review tripwire fires.
  Its third check is a **floor**, not a cap, and it is there because this guard is what endangers
  the thing it protects: `// Tasker` provenance markers are comments, so the budget's downward
  pressure falls on them too, and the first consolidation pass deleted four despite an explicit
  instruction not to. `ProvenanceTest` in `:domain` already floors provenance — but only for
  `BrightnessEngine.kt`, one of the 33 files that carry markers. Everywhere else the constitution's
  rule had nothing behind it.

  **That floor started as a tree-wide count and the count was not enough.** `grep -c '// Tasker' >=
  68` protects the *population*, never any individual marker: delete one from an algorithm, add one
  anywhere else, and the total is unchanged, so the guard passes while the ported logic has silently
  lost its audit trail. That is not an adversarial bypass — it is the shape of an ordinary
  maintenance change that splits or retires one ported path. So the unit is now a **record**, keyed
  by file plus the **Tasker source coordinates** the marker cites (`task535`, `prof759`, `act28`,
  `elements26`, `L15204`, `%AAB_*`) — ONE record per coordinate, so a record fails when and only
  when that reference is gone from that file. Enriching a marker, merging two that cite the same
  coordinate, and adding new provenance are all free; the manifest is a floor, not a whitelist. Its
  record count lives in the guard and is deliberately not restated here. Keying on coordinates
  rather than on the marker's text is what makes it livable: most markers were reworded by the
  consolidation that shipped this guard, each keeping its reference while shortening the prose, and
  a text-keyed manifest would have gone red on every one of them —
  a rule that fires on every honest prose edit gets regenerated by reflex until it means nothing.
  The rewrite paid for itself immediately: normalising this way found two markers in
  `BrightnessPolicyInput.kt` whose `act10/14` and `act26/27/28` coordinates the consolidation had
  dropped, while the tree-wide count sat at exactly 68 and passed. Its residue, stated because an
  overstated coverage claim is what stops the next reader checking: coordinates are normalised, so a
  marker **re-pointed** at a different sub-action of the same task is not detected, and records
  for markers citing no recognised coordinate degenerate to a per-file count (that count is a real
  floor, but it cannot say which marker left).
  Regenerate with `scripts/guards/comment-budget.sh --provenance-records`, which is the guard's own
  extractor — so the manifest cannot be normalised differently from the checker that reads it — and
  baseline it from the merge base, never a tree that is mid-change.
- **`format-args.sh`** — a string resource carrying a format specifier, passed to `toast()` as the
  call's only argument, throws `MissingFormatArgumentException` when the toast is shown. Nothing
  else in the tree can see it: the Kotlin compiler does not read `strings.xml`, the toast unit tests
  assert on resource IDs and discard the args, and lint's `StringFormatInvalid` is not on the
  ladder's path. DB-060 is the incident — a string gained `%1$d` for one caller and the other
  caller, on a screen no device round had exercised, crashed the app against a tagged release that
  did not. Upstream cannot own this: it ships no Android resource scanner.
  **It scans the vararg resolvers and nothing else, and that narrowness is the design.**
  `Resources.getString(int)` does not format — it is `getText(id).toString()`, and Compose's
  `stringResource(id)` delegates to it, so resolving a `%1$d` string through either renders the
  template rather than throwing, and `template.format(…)` afterwards is correct code this tree
  already contains. Only the vararg overload formats, and it formats even when the array is empty.
  **Two functions reach it here** — `Toaster.invoke(resId, vararg)` (D-131) and
  `ControlReceiver.flashDrop` (DB-035) — and `RESOLVERS` must name every one; an unnamed wrapper is
  a silent hole. A guard that also flagged `stringResource` would fail the ladder on correct code,
  which is how a rule gets regenerated away.
  **Three DA-005 rounds, each finding the last one wrong**, which is the honest provenance of this
  guard: the first draft asserted the wrong crash mechanism and scanned three functions that cannot
  produce it; the rewrite that fixed those was blind to `emptyArray()` spreads and to `flashDrop`
  entirely, and shipped two fixture cases whose bodies could not fail either way. The corrections to
  *that* have had no review. Its header states what it still does not catch (ids reaching a resolver
  through a variable or a runtime-empty spread, multi-line call sites, plurals/string-arrays, the
  space flag, wrong arg counts and types past zero, translations).

- **`python-edit.sh`** — the only one here that is not a ladder check at heart: a **pre-execution
  advisory** (DB-062), wired as a second `PreToolUse` hook beside the shipped command guard, which
  is integrity-hashed and so cannot host a repo-local rule. It blocks the FIRST inline-Python file
  edit per marker lifetime — once per container in practice, since the marker lives in `/tmp` with no
  session component (DB-063) — and passes every later one, mirroring the shipped guard's `.env` and
  destructive advisories. The objection is opacity, not danger: `Write`/`Edit` render a diff as the
  change happens, while a `python3 - <<EOF` heredoc is only checkable afterwards by reading the file
  back. One block makes that a choice rather than a reflex. Its ladder mode runs the matcher
  fixtures, because nothing inside the repo can verify the hook still *fires* — only a real command
  shows that. Its header states what it does not match (a script file, `sed -i` and friends,
  runtime-constructed commands, and every edit after the first).

- **`action-pins.sh`** — every remote action `uses:` is a 40-hex commit SHA carrying a `# vX.Y.Z`
  marker, every `docker://` image is a 64-hex sha256 digest, quoted block-mapping `uses` keys are
  included, while flow-style and multiline `uses`, explicit mapping keys, and escaped quoted keys
  fail closed as noncanonical workflow syntax (DB-085). One SHA never wears two markers,
  and one action+version never resolves to two commits (DB-076). Upstream
  cannot own this: the AMH ships no CI-supply-chain opinion, and the Scorecard rails these encode
  are this repository's own RUNBOOK playbook 8. **What it deliberately does not check is the thing
  you would want most**: whether a marker names the tag its SHA really belongs to. That needs
  `git ls-remote`, and the ladder is offline and deterministic, so a pin mislabelled *consistently*
  passes here and playbook 8 step 2 — resolve the tag on GitHub by hand — remains the only layer
  that sees it. What it does catch is the failure that actually happened, twice: Dependabot rewrites
  a SHA but its marker rewrite fails silently on lines with trailing prose after the version
  (DB-038), leaving the same commit labelled two ways across the tree. Both incidents were caught by
  eye, on a PR, by a reviewer who happened to look.

`scripts/tests/local-guards.sh` is their fixture suite — 145 cases, run by `scripts/verify.sh`.
Nothing upstream knows these guards exist, so without it their failure paths never execute. Its
negative cases are the point: each was checked by mutating the guard it covers and confirming
exactly one case turns red.

**A repo-local guard's exit code is an interface, not just pass/fail (AMH 4.2.0, DB-023).**
Three verdicts: exit 0 passes, exit 2 whose *merged* output begins `WARN ` warns without turning
the ladder red, and every other non-zero fails. The marker is mandatory because bash exits 2 on
a syntax error — and `grep` and `diff` exit 2 on trouble — so an unmarked exit 2 is read as a
broken guard rather than a mild opinion. The contract is the **ladder's**: a workflow or script
calling a guard directly still reads any non-zero as failure, which is why nothing here invokes
`scripts/guards/*.sh` outside `scripts/ladder.sh`.

**All eight of ours fail closed, deliberately.** A codepoint count over F-Droid's hard cap, a
secret in the index and a misfiled ledger prefix are wrong every time they fire, so the warn tier
— for a rule with legitimate exceptions nobody has enumerated — does not apply to them.
`action-pins.sh` joins them on the same test: a tag ref where a SHA belongs, an unlabelled pin and
two markers on one commit are each wrong every time, and the one judgement call it might have
wanted — is this marker the right *tag* — is the question it deliberately does not ask.
`comment-budget.sh` was the one real candidate for the warn tier and was refused it: a budget that
only warns is a budget the next session spends, and warn fatigue is the documented failure mode
for exactly this shape of rule. The escape hatch is not a warning, it is the constants in the
guard's own header — raising one is visible in the diff and trips the rule-review tripwire, which
is the reviewable version of the same flexibility.
`doc-facts.sh` is the interesting one: its anchors are deliberate approximations and *can* fire on
a true claim (a fifth file naming `ShizukuShell` without being a runtime dependency site, per that
guard's own header). It stays fail-closed anyway, because reconciling the prose against the code
is the work the anchor exists to force. Choose the tier when you add a guard, and say here which
you chose and why.

## Every `amh.conf` value that differs from stock

| Key | Ours | Why |
|---|---|---|
| `AMH_PROSE_VERSION` | `9.1.0` (no stock key) | **Ours entirely.** The version whose binding prose the tree follows, against `AMH_VERSION`'s `14.0.0` for the shipped scripts: `4e22273` landed the file copy and left the seed prose owed. The **Upgrading** section below reads the changelog forward from this key, which is the reason it exists; `doc-facts.sh` warns while the two differ and fails if `AGENTS.md` drops its disclosure paragraph first. Delete the paragraph and set the two equal in the commit that lands the prose (DC-031). |
| `BRANCH_PREFIX` | `claude` | Session branches are `claude/<codename>`, named in each session's directive. |
| `MERGE_MODE` | `branch-train` | DA-002: branches are cut from the newest session branch, superseded ones deleted unmerged, only the final superset squash-merged. |
| `REMOTE_FLAG` | `AAB_REMOTE` | Pre-existing neutral flag (D-176). See the adapter note below. |
| `LEDGER_LINE_CAP` | `1000` (stock 800) | The base volume closed at D-176 and `_A.md` at exactly 1000 lines (D-153, DA-001). Since AMH 5.1.0 the volume headers and the RUNBOOK name the key instead of restating the number, so **this cell is the last prose copy** — it is the one that must move with `amh.conf`. |
| `STATE_REQUIRED_SECTIONS` | `+ ## Decided non-items` | Where declined work is recorded; losing it invites re-litigating settled questions (D-162, DA-021). |
| `POISON_TOKENS` | `+ [no ci] [skip actions] [actions skip]` | D-115: `release-preflight.yml` enforces this wider set at PR time; the two must agree. |
| `CITATION_SCAN_PATHS` | `+ scripts` | The repo-local guards and `verify.sh` depend on ledger rows exactly as the Kotlin does. Safe because the shipped scripts name upstream's rows as `AMH ledger row D004` — a form the guard does not read as a citation. |
| `CITATION_EXCLUDE` | the two test paths | Their fixtures carry synthetic IDs by design. Scanning `scripts/` is safe by construction, not by luck: **upstream never writes a bare `D-NNN` in a shipped script** — it spells its own rows `AMH ledger row D004` precisely so an adopter's citation guard cannot resolve them (confirmed by the AMH maintainer, 2026-08-03; since 4.2.0 a guard in AMH's own repository fails if that ever stops being true, so the guarantee is enforced rather than promised). No upgrade check needed. Dropping the key would pull the shipped fixture suite's synthetic IDs into scope and report them as unresolved in a file we may never edit. |
| `AUTHOR_EMAIL_ALLOW` | three no-reply aliases | The owner's forge alias, GitHub's web-UI committer, and the agent's. States which identities are expected; no regex can tell a personal address from a work one. |
| `VERSION_FILE` | empty | This repo's version is a Kotlin DSL assignment in `app/build.gradle.kts`, not the first line of a plain file, so the release-window banner cannot read it. `release-preflight.yml` (D-124) enforces the version invariants instead. |
| `REQUIRED_TOOLS` | `bash git java` | The ladder and its fixture suites are shell/Git programs, while the Android verification set requires the JVM. The session banner reports availability; nothing consumes the states. |
| `ADAPTER_FILES` | the Claude and Codex adapter paths | Names every adapter this repository ships. `configured` reports file presence, not that an integration or hook actually ran. |
| `RULE_FILES` | `+ CLAUDE.md`, `scripts/{guards,tests,bootstrap.sh,session-facts.sh,verify.sh}` | A repo-local guard is legislation exactly as a shipped one is, and `CLAUDE.md` must never diverge from the constitution it points at. The list must name every extension point `AGENTS.md` **Harness** admits, unshipped repo-local scripts included, or the rule-review tripwire has a hole exactly where local authority is widest. |

## Adapter and CI notes

**`.claude/settings.json` translates `CLAUDE_CODE_REMOTE=true` into `AAB_REMOTE=1`.** Claude
Code on the web sets its own variable, and the shipped bootstrap reads only the neutral flag
`amh.conf` names. The translation belongs in the adapter — a vendor's environment variable must
never reach a shipped script or `amh.conf`.

> **Adoption hazard, learned the hard way.** Our pre-AMH `command-guard.sh` took a
> `--claude-hook` flag; the shipped one reads the hook payload on stdin with no flag. The moment
> `amh-init.sh` overwrote the script, the stale `PreToolUse` command began failing on every Bash
> call and blocked the whole session. **Update the adapter in the same step that installs the
> scripts**, before running anything else.

**`.github/workflows/build.yml`** invokes `scripts/ladder.sh` with no arguments. The shipped
ladder forwards nothing to Gradle, so the `--no-daemon --no-configuration-cache` pair that used
to sit on the CI command line now lives in `scripts/verify.sh`, which adds it when `CI` is set
(DA-017: CI's single invocation only ever pays the config-cache *store* cost). The separate
guard-self-test step is gone because `verify.sh` runs both fixture suites as its first rungs.
The `ci.yml` that `amh-init` installs was deleted: `build.yml` already runs the ladder and
carries this repo's JDK/SDK/cache setup.

**`scripts/bootstrap.sh`** holds the Android SDK setup and the background Gradle warm-up
(D-173), and runs only when `AAB_REMOTE=1` — never implicitly on a developer machine.

**`scripts/session-facts.sh`** is the second repo-local script the harness does not ship, and it
exists because of a rule rather than a toolchain (DC-030). AMH 14.0.0 makes working memory
tree-relative, so `docs/STATE.md` may no longer record which release is newest or whether this
version is tagged. Striking that sentence and stopping there would just hand the question to
whoever resumes cold, so this script answers it live at every session start: the tree's
`versionName`/`versionCode` read straight from `app/build.gradle.kts`, the newest `v*` tag on
`origin`, whether this version is released, and the branch's position against `origin/main`.

The shipped banner already has a release line, driven by `VERSION_FILE` and `RELEASE_TAG_PREFIX`,
and both are deliberately left empty: it reads the version from the **first line** of a file, and
this project's version lives inside `app/build.gradle.kts`. Setting them would have meant creating
a `VERSION` file — a second source of truth, free to drift from the build the release actually
ships. Reading the build file is the narrower answer.

It always exits 0 and degrades to an explicit `release status UNKNOWN`, plus the command that
settles it, in two distinct cases: `origin` unreachable, and no `timeout` binary available. The
second case **skips the probe entirely** rather than running it unbounded — the promise is that the
hook cannot stall a session, and an unbounded network call cannot keep that promise. Reachability is
judged by exit status alone, so a reachable origin with no tags reports "no `vX.Y.Z` tag exists yet",
which is a different fact from "could not reach origin"; conflating those two was a real bug caught
in review.

Two limits stated rather than implied: "newest tag" ranks plain `vMAJOR.MINOR.PATCH` only, so a
pre-release tag is ignored for ranking (the released/unreleased test for the current version is an
exact ref match and handles any string); and the ahead/behind counts read the locally fetched
`origin/main`, so a shallow clone undercounts them.

**Only Claude Code runs it** — the hook is in `.claude/settings.json`, and Codex was not observed to
fire any repository hook (see the adapter table above), so for Codex this paragraph is the whole
of it.

It **is** in `RULE_FILES`. The first draft left it out on the grounds that it only reports and
binds no one; the rule-review pass rejected that, and correctly. What it prints is the release
guidance every session reads before deciding whether a version is safe to work on, so wording that
drifts changes what agents believe while producing no advisory at all. Reporting is not the same as
harmless.

## Adding an agent adapter

`AGENTS.md` is the constitution for every agent; an adapter is wiring only, and lives in a
dot-dir (`.claude/`, `.codex/`). A new one must:

- run `scripts/session-start.sh` at session start;
- translate its own vendor environment variables into the neutral flag `amh.conf` names — **in
  the adapter, never in a shipped script** (that is what the `CLAUDE_CODE_REMOTE` → `AAB_REMOTE`
  line above is);
- mirror the deny rails — environment dumps, force-push, pushing to `main` — if the agent
  supports permission rules;
- wire `scripts/command-guard.sh` as a pre-execution check if the agent supports hooks;
- pipe tool output through `scripts/redact.sh` if the agent has an output-filter hook;
- honour one session, one branch;
- add its config file to `RULE_FILES` in `amh.conf`, so a diff to it trips the rule-review
  tripwire.

**State which of those layers the adapter actually provides, honestly**, in the adapter's own
`$comment` field. A false coverage claim is what stops the next reader checking by hand. The two
that exist today:

| Adapter | Bootstrap | Command rail | Deny rails | Output redaction | Comment rail | Inline-Python rail |
|---|---|---|---|---|---|---|
| `.claude/settings.json` | yes (SessionStart hook, with the remote-flag translation; a second hook runs the repo-local `scripts/session-facts.sh`) | yes (PreToolUse, stdin payload) | yes | **no** — Claude Code has no output-filter hook, so `scripts/redact.sh` is manual-pipe only and is what the ladder's secret scan uses | yes (PostToolUse on `Edit\|Write\|MultiEdit` → `comment-budget.sh --hook`, block cap only) | yes (second PreToolUse hook → `python-edit.sh --hook`, first match only) |
| `.codex/config.toml` + `.codex/rules/amh.rules` | declared; **not observed to fire** on codex 0.152.1 — see below | declared; **not observed to fire** on 0.152.1, which would leave `command-guard.sh` an uncalled script for Codex | yes — the `.rules` prefix policy is loaded (`--ignore-rules` exists to skip it); its path vocabulary is narrower than a hook's | **no** | **no** — neither a shell hook nor a prefix rule can judge a file an edit tool wrote; `AGENTS.md` Conventions is the immediate layer | **no** — `AGENTS.md` Conventions is the only layer standing |

**On the comment rail specifically.** The ladder guard is the coverage; the hook is only
*salience*. A rule that lands solely in a ladder run arrives after the narrative is written and
the session has moved on, which is why the same check also runs on the edit that writes it. The
hook cannot block — `PostToolUse` fires after the tool has already run — and it exits 2 because
that is the one code for which Claude Code feeds a hook's stderr back to the model. It is
deliberately silent when it cannot parse its payload: a hook that fires spuriously on every edit
is one the next session deletes, and the ladder still covers the tree either way. Nothing can
detect that the hook stopped firing — `configured` in the session banner means a file is present,
never that a hook ran.

**On the Codex adapter's hooks — declared, but not observed to fire (DC-030).** `.codex/config.toml`
declares a `SessionStart` and a `PreToolUse` hook. On codex CLI 0.152.1, 2026-09-02, neither was
observed to run: `codex doctor` reports its `config.toml` as `~/.codex/config.toml` and lists no
project-level layer, and `scripts/session-start.sh` prints its banner unconditionally yet that
banner appeared in none of roughly ten `codex exec` runs inside this repository. The table above
claimed `yes` for both rails before anyone measured.

Read the strength of that claim exactly. It is an **observation on one version**, not a proof: no
repository check can tell a hook invocation from a manual one, which is why `AGENTS.md` says an
agent's rails cannot be detected from inside the tree at all. What the tree does prove is only that
the hooks are declared. Project execpolicy `.rules` files *are* loaded — `codex exec --ignore-rules`
exists precisely to skip them — so `.codex/rules/amh.rules` is real either way. Re-measure on a
version bump; upstream may add project-config support at any time.

`AGENTS.md` currently states the opposite in its Conventions section — that "Codex's pre-shell hook
runs the shipped command guard" and that it has a shell hook. Reconciling those sentences is a
change to the constitution, so it is an Owner-queue item rather than something this file settles.

**An agent with no pre-execution hook has no command rail at all.** `scripts/command-guard.sh` is
then a script nobody calls, and the constitution's prose is the only layer standing. No check can
detect this: telling a hook invocation from a manual one needs vendor-specific environment
variables the harness will not assume.

## Upgrading

Follow `docs/UPGRADING.md` in the AMH repository. In short: clone the target tag, read
`harness/CHANGELOG.md` forward from **`AMH_PROSE_VERSION`** in `amh.conf` — not from
`AMH_VERSION` — copy `harness/templates/scripts/*` (the **whole** directory — the manifest lives
beside the scripts it hashes), apply the changelog's Upgrading notes by hand, and drive
`scripts/ladder.sh` green.

Read forward from the **prose** key because that is the one naming rules this tree actually
follows. The two keys are equal whenever nothing is owed, so on an ordinary upgrade this reads
exactly as it always did. They differ only while a split upgrade is outstanding, and then
`AMH_VERSION` has already advanced past notes nobody applied: starting there skips them silently,
and no guard can catch it, because reading a changelog is not something a check can observe. This
is the sole reason the second key exists (DC-031). Set both equal in the commit that lands the
owed prose.
A new guard failing on something that was always there is a finding, not upgrade damage: fix
the finding, never weaken the guard. Then re-check every row of the tables above.
