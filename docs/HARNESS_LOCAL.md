# HARNESS_LOCAL — what this repo adds on top of stock AMH

This repository runs the **Agentic Maintenance Harness**
([`faded-penguin021/AMH`](https://github.com/faded-penguin021/AMH)), adopted at **amh-v3.0.0**
under the `full` profile. `AGENTS.md` is the constitution, `docs/STATE.md` the working memory,
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
| **Ours** | `amh.conf`, `scripts/verify.sh`, `scripts/guards/*.sh`, `scripts/bootstrap.sh`, `scripts/tests/local-guards.sh` | The extension points. Everything repo-specific goes in one of these — if a change fits none of them, the harness is missing an extension point; raise it upstream rather than carrying a local patch. |
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

We **gained** three rungs with no local predecessor: `guard_author_identity`,
`guard_shipped_integrity` and `guard_repo_local`.

`docs/AGENTIC_HARNESS_PROMPT.md` — the v1.8 generalization of this harness — was deleted. It is
the AMH repository now, and a stale local snapshot would read as authoritative to a future
session while drifting against every upstream release.

## The four repo-local guards, and why each is not upstream's job

- **`fdroid-changelog.sh`** — F-Droid flags a `whatsNew` over 500 **characters**, and it measures
  codepoints, not bytes. `wc -c` rejects a legal note full of em dashes. Only the current
  `versionCode`'s file is checked; existence is `release-preflight.yml`'s job, which knows
  whether the PR ships app code.
- **`doc-facts.sh`** — one machine anchor per load-bearing prose claim that has *already* drifted
  in a shipped commit. The incident-only bar is binding (DA-015): this is not a doc-testing
  framework, and a fact proposed without a named drift commit was reviewed out once already.
- **`staged-secrets.sh`** — the shipped scan reads the working tree, which misses a secret that
  was staged and then edited out of the file on disk. The index is what `git commit` records.
- **`ledger-prefix.sh`** — the shipped citation guard pools every volume and asks only whether an
  ID resolves *somewhere*, which is right for it: it cannot know an adopter's volume naming.
  Here the naming is the rule, so a `DB-` row misfiled into `LEDGER_A.md` resolves cleanly and
  passes upstream.

`scripts/tests/local-guards.sh` is their fixture suite — 17 cases, run by `scripts/verify.sh`.
Nothing upstream knows these guards exist, so without it their failure paths never execute. Its
negative cases are the point: each was checked by mutating the guard it covers and confirming
exactly one case turns red.

## Every `amh.conf` value that differs from stock

| Key | Ours | Why |
|---|---|---|
| `BRANCH_PREFIX` | `claude` | Session branches are `claude/<codename>`, named in each session's directive. |
| `MERGE_MODE` | `branch-train` | DA-002: branches are cut from the newest session branch, superseded ones deleted unmerged, only the final superset squash-merged. |
| `REMOTE_FLAG` | `AAB_REMOTE` | Pre-existing neutral flag (D-176). See the adapter note below. |
| `LEDGER_LINE_CAP` | `1000` (stock 800) | The base volume closed at D-176 and `_A.md` at exactly 1000 lines (D-153, DA-001). Keep in lockstep with each volume's own header. |
| `STATE_REQUIRED_SECTIONS` | `+ ## Decided non-items` | Where declined work is recorded; losing it invites re-litigating settled questions (D-162, DA-021). |
| `POISON_TOKENS` | `+ [no ci] [skip actions] [actions skip]` | D-115: `release-preflight.yml` enforces this wider set at PR time; the two must agree. |
| `CITATION_SCAN_PATHS` | `+ scripts` | The repo-local guards and `verify.sh` depend on ledger rows exactly as the Kotlin does. Safe because the shipped scripts name upstream's rows as `AMH ledger row D004` — a form the guard does not read as a citation. |
| `CITATION_EXCLUDE` | the two test paths | Their fixtures carry synthetic IDs by design. Scanning `scripts/` is safe by construction, not by luck: upstream never writes a bare `D-NNN` in a shipped script — it names its own rows `AMH ledger row D004` precisely so an adopter's citation guard cannot resolve them (confirmed by the AMH maintainer, 2026-08-03). Nothing to re-check on upgrade. |
| `AUTHOR_EMAIL_ALLOW` | three no-reply aliases | The owner's forge alias, GitHub's web-UI committer, and the agent's. States which identities are expected; no regex can tell a personal address from a work one. |
| `VERSION_FILE` | empty | This repo's version is a Kotlin DSL assignment in `app/build.gradle.kts`, not the first line of a plain file, so the release-window banner cannot read it. `release-preflight.yml` (D-124) enforces the version invariants instead. |
| `RULE_FILES` | `+ CLAUDE.md`, `scripts/{guards,tests,bootstrap.sh,verify.sh}` | A repo-local guard is legislation exactly as a shipped one is, and `CLAUDE.md` must never diverge from the constitution it points at. |

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

## Upgrading

Follow `docs/UPGRADING.md` in the AMH repository. In short: clone the target tag, read
`harness/CHANGELOG.md` forward from `AMH_VERSION` in `amh.conf`, copy
`harness/templates/scripts/*` (the **whole** directory — the manifest lives beside the scripts
it hashes), apply the changelog's Upgrading notes by hand, and drive `scripts/ladder.sh` green.
A new guard failing on something that was always there is a finding, not upgrade damage: fix
the finding, never weaken the guard. Then re-check every row of the tables above.
