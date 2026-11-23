# RUNBOOK — maintenance playbook

The Tasker→Kotlin rebuild is **done and shipped** (v1.0.0). This is the entry point for
*changing* the app afterward while preserving Tasker feature parity. Pick the change-type
playbook that matches your task, read the reference docs it names, then do the work under
`docs/PROTOCOLS.md`. The procedures every session is bound by, whatever its task — Session
discipline, the glue-review and rule-review protocols, the leaked-credential incident, the
acceptance ladder and working-memory compression — are there, not here (AMH 17.0.0).

The migration narrative (segment briefs, gate findings) is frozen in `docs/history/` — consult it,
don't extend it. The numbered deviations live in `docs/LEDGER.md`, a permanent append-only
registry (never compress it; append the next number in the LIVE ledger file — base file closed
at D-176; from `LEDGER_A.md`/DA-001 on, `LEDGER_LINE_CAP` lines per file, final row may overflow,
next row opens `_B.md`/DB-001, …; D-153/DA-001). **Code + golden
vectors are ground truth** for what the app does; where descriptive prose disagrees with the code,
trust the code (and fix the prose) — never a binding rule, which the code does not settle (see the
constitution's ground-truth note) — and never the append-only ledger, whose rows are never edited
in place: a correction is a new row plus one appended pointer on the old one.

## Where logic lives

- **`:domain`** — pure JVM/Kotlin. ALL math/decision logic (smoothing, threshold, curve
  mapping, scaling, animation planning). No Android imports. Golden-tested.
- **`:platform`** — Android library. Real system adapters behind small interfaces (light
  sensor, brightness/secure-dimming writers, PrivilegeManager, ContentObserver override
  detector, battery/wifi/location/foreground-app readers).
- **`:app`** — Compose M3 UI (~9 screens), DataStore settings (`AabSettings`), foreground
  service runtime, QS tile, boot receiver, notification.

## Invariants (BINDING — `AGENTS.md` rule 18)

Binding under `AGENTS.md` rule 18, which moved them here from the constitution (DE-058). The
complete catalogue is in `docs/LEDGER*.md`; search it as needed. These are the ones most often
violated during maintenance:

- The concurrency model is binding. There is one pipeline coroutine, and each event runs to
  completion, including its animation. Light readings are never queued: while a cycle or its
  cooldown holds Tasker's `%AAB_MainLoop` re-entry mutex, the newest reading that passes the
  accuracy gate waits in a single pending slot, replacing any older one, and is reconsidered
  against the gates then current, dead band included, once the cycle and cooldown end. A pending
  reading never runs ahead of a control event queued before it arrived, and screen-off, pause and
  stop discard it. Tasker drops such a reading; this departure is DC-069, amending D-027(d).
  While smoothed lux lies outside the stored band (stretched to hold the reading it is centred
  on), the dead band does not refuse an unchanged reading, and after an event that leaves the slot
  empty while the pipeline runs awake and unpaused, the latest reading that sensor session
  admitted is put back in it as a marked continuation, so settling finishes on a sensor gone
  silent. A real reading replaces the
  continuation, it is never counted as a callback, and nothing refills it once smoothed lux is
  inside the band. A settling step that stalls, or the twentieth, places smoothed lux on the
  nearest edge. Tasker has no settling path; this departure is DC-070 (DC-071).
- Profile gates are hardcoded Kotlin booleans with provenance and a truth-table test. There is no
  generic `ConditionList` evaluator. For `ConditionList`, And binds more tightly than Or, then And2
  and Or2 join from left to right. XML children are alphabetical, so sort them numerically before
  reading their order.
- Curve mathematics comes from the Variable Set expressions in `task661`, not from Java. The Java
  three-zone formula in `task663` exists only as a plotting copy for cross-validation. If the two
  disagree, record the difference in `docs/rebuild/parity_gaps.md` instead of guessing.
- `%AAB_Proximity` multiplies `LuxAlpha` by 0.1; it never pauses the pipeline. `%AAB_Test` contains
  user-facing curve-wizard diagnostics intended for the clipboard and must be surfaced.
  `%AAB_Debug` consists of ten named categories rather than a verbosity level.

## Reference-doc index (live; bare names are in `docs/rebuild/`, paths are repo-relative)

| Question | Doc |
|---|---|
| What is every Tasker artifact and how was it dispositioned? | `PARITY_CHECKLIST.md` |
| Which files implement a feature, and what does a file touch? | `python3 scripts/codemap.py features`, `feature <name>`, `impact <file>`; registry `codemap/features.toml` |
| End-to-end pipeline behavior (sensor→dimming) | `extraction/pipeline_spec.md` |
| A specific profile / gate's semantics | `extraction/profiles.md`, `extraction/contexts_spec.md` |
| A specific task's actions / curve math | `extraction/tasks/task<id>_*.md` |
| Default values & variable classification | `extraction/defaults_audit.md` |
| Ancillary features (tile, notification, debug, import/export) | `extraction/features_spec.md` |
| App languages and translations | `CONTRIBUTING.md`; picker: `app/src/main/kotlin/com/tideo/autobrightness/app/ui/onboarding/OnboardingScreen.kt`; supported locales: `app/src/main/res/xml/locales_config.xml`. `MainActivity` uses AppCompat's app-language API with automatic locale storage on Android 12/12L. |
| Anonymous scene-handler tasks | `extraction/tasks/anonymous_handlers.md` |
| Scene → M3 screen mapping | `screen_map.md`, `extraction/scenes/*` |
| How to safely re-read the source XML | `XML_RECIPES.md` |
| Known parity deviations / open gaps | `parity_gaps.md` |
| Privilege tiers / permissions / DataStore schema | `architecture/*` |
| Material 3 audit | `design/m3_audit.md` |
| What the F-Droid compatibility CI proves (and does not) | `FDROID_VALIDATION.md` |
| Threat model — assets, attackers, invariants | `SECURITY_AUDIT_MODEL.md` |
| The standing on-device acceptance pass (permanent, cited by §number) | `DEVICE_TEST_SCRIPT.md` |
| What the *unreleased* train changed, for the owner to check (ephemeral) | `DEVICE_TEST_SCRIPT_<version>.md` |
| The executable twin of that pass: one row per step, auto / partial / manual | `e2e/scenarios.toml` |
| Which control enforces an invariant, and what proves it | `SECURITY_REVIEW.md` |
| Numbered deviations — solved mistakes + ongoing (⭐, append in the live file, D-153 rollover; `[cited]` = code-anchored, D-174) | `docs/LEDGER.md` (later `_A.md`/DA-…, `_B.md`/DB-…) |

## Change-type playbooks

Each: *when · read first · code to touch · parity obligations · acceptance · record it.*

> **Design coherence — READ FIRST for ANY UI/visual change** (a new screen, but also a banner, card,
> chip, color, icon, or spacing tweak — playbooks 3 *and* 4/5 when they touch Compose). Read
> `design/m3_audit.md` before styling and conform to it: the teal+gold palette is **frozen** (never
> recolor — reach for `MaterialTheme.colorScheme.*`; gold = `secondary` = warnings/emphasis); group
> content in `AabCard` (medium/large shape, `cardElevation`, `cardPadding`), never a flat list;
> **colour-semantic banners (stale/override/resume) are tinted `Card`s** (`AabGold`/`AabOnGold` or a
> `*Container` role), NOT `AabCard`; derived/live numerics use the gold `KeyValueRow` (B4); new strings
> go through `stringResource` (`HardcodedStringCheckTest` ratchet); use `Dimens.*` tokens, not raw `dp`.
> When in doubt, mirror the nearest existing component (e.g. the dashboard `StaleBanner`/`OverrideCard`).

### 1. Tasker profile added / changed / removed (a trigger context or pipeline gate)
- **Read first:** `extraction/profiles.md` (+ `contexts_spec.md` for context watchers); the
  relevant `docs/LEDGER.md` rows (profile gating, ConditionList semantics). Re-read the XML
  only via `XML_RECIPES.md`.
- **Code:** the hardcoded-boolean profile gates in `:domain`/`:platform` (no generic
  ConditionList evaluator exists — gates are explicit Kotlin booleans with a truth-table test).
- **Parity:** ConditionList semantics — plain And/Or bind tighter than And2/Or2; XML children
  are alphabetical (re-sort numerically). Preserve the single-coroutine, drop-on-reentry model.
- **Acceptance:** update the gate truth-table test; run the ladder; **glue-review
  protocol** (`docs/PROTOCOLS.md`) on any `:platform`/runtime part of the diff.
- **Record:** flip the row in `PARITY_CHECKLIST.md`; note the change in `STATE.md`.

### 2. Tasker task added / changed / removed (pipeline / curve math)
- **Read first:** `extraction/tasks/task<id>_*.md` + `extraction/pipeline_spec.md`; cross-check
  `defaults_audit.md` for any variables involved.
- **Code:** the corresponding `:domain` engine logic + its golden tests.
- **Parity:** port behavior EXACTLY incl. odd rounding (`round3`, `Math.round` tie-toward-+∞,
  BigDecimal HALF_UP, string-formatted numbers). For Variable Set (code 547) maths, follow the
  transcription protocol: cross-validate `task661` vs `task663`; **disagreements → `parity_gaps.md`,
  never guess.** Golden vectors are immutable — production conforms to them.
- **Acceptance:** golden/JVM tests green.
- **Record:** add a provenance comment (`// Tasker: task<id> "<name>" XML L<line>`); update
  `PARITY_CHECKLIST.md` + `STATE.md`.

### 3. Tasker scene added / changed / removed (a screen)
- **Read first:** `screen_map.md` + the matching `extraction/scenes/*.md` + `design/m3_audit.md`.
- **Code:** the Compose screen in `:app`; settings via `AabSettings`/DataStore.
- **Parity:** keep user-facing behavior/labels faithful; honor the scene→screen consolidation
  matrix rather than reintroducing one-scene-per-screen.
- **Acceptance:** `:app:testDebugUnitTest`, `:app:assembleDebug`, `:platform:lintDebug :app:lintDebug`.
- **Record:** update `screen_map.md` + `PARITY_CHECKLIST.md` + `STATE.md`.

### 4. Bug fix
- **Read first:** the reference doc for the affected area (above) + any related
  `docs/LEDGER.md` row, and `python3 scripts/codemap.py impact <file>` for each Kotlin source you will edit.
- **Steps:** reproduce → add a failing test and see it fail on the unfixed tree, for the
  reason the report names, before writing the fix (where no test can reproduce it, the commit
  body says so and what was done instead); an existing test is not the fix's to adjust
  (`docs/PROTOCOLS.md` → **Session discipline** 12) → fix so it conforms to the golden
  vectors (never edit a golden vector to pass; changing one needs proof the extraction was
  wrong + a `STATE.md` entry) → run the ladder → **glue-review protocol** (`docs/PROTOCOLS.md`) if
  the fix touches `:platform`/runtime glue, or if it changes, skips, loosens or deletes an
  existing test in any module (strongest tier, no self-review fallback).
- **Plan handoff:** when the owner approves a plan that a DIFFERENT session will execute, persist it
  under playbook 5's Multi-session plan rules below — all of them, including the STATE segment
  checklist and the deletion precondition. A fix small enough to do in the approving session needs no
  file. No guard enforces any of this: the ladder's `PLAN_DIR` advisory only WARNs when a plan file is
  not referenced from `STATE.md`, and it does not run in CI.
- **Record:** `STATE.md` (and `parity_gaps.md` if it was a parity gap).

### 5. Tasker-independent feature (rare — no parity source)
- **Read first:** `python3 scripts/codemap.py feature <name>` for every registered feature the
  change reaches (`features` lists them), so it reuses that feature's owners; a new feature is
  added to `codemap/features.toml` (AGENTS.md → Conventions).
- No golden reference exists; still obey the coding conventions in `CLAUDE.md` and add tests.
  The **glue-review protocol** (`docs/PROTOCOLS.md`) applies to any `:platform`/runtime glue it adds.
- **Multi-session features** (pattern proven by Privileged Display, D-149–D-152): persist an
  owner-approved execution plan in the directory named by `PLAN_DIR` in `amh.conf` (today
  `docs/plans/`), which also holds retained triage docs such as `REVIEW_TRIAGE_1.9.0.md`. The two are
  not governed alike: an execution plan is deleted at its final segment (below), while a triage doc
  that the Decided non-items (`docs/rebuild/DECIDED_NON_ITEMS.md`, DD-037) or a ledger row still
  points at is retained until nothing
  points at it. Mirror a segment checklist in `STATE.md` `## Active work`, creating that section if it
  is absent. Segments run **sequentially** (D-133) and each ends SHIPPABLE:
  STATE Changelog line → ladder green → commit → `--guards-only` → push (`docs/PROTOCOLS.md` discipline 3). A follow-up session bases its branch on
  the unmerged predecessor (`git checkout -B <new> origin/<old>`), else latest main. Treat the
  plan as provisional — the owner may pivot it mid-feature (D-151 replaced an entire
  already-on-branch segment; per-segment checkpoints are what made that removal cheap). At the
  final segment **delete the plan file**: its durable content must by then live in `STATE.md`
  Changelog lines + ledger rows — that precondition is the rule, so a tree carrying no plan file means
  every plan landed, not that one went missing. Code comments cite `D-NN`, never the plan file (it
  dies; the ledger doesn't).
- **Device steps change in two places.** `DEVICE_TEST_SCRIPT.md` is the human-readable spec and
  `e2e/scenarios.toml` its machine/agent-executable twin. Any change that adds, removes or rewrites
  a numbered step updates both in the same commit, whatever playbook it falls under: the step in
  the script, and its row (status, effects, reason, and the test once one exists) in the manifest.
  A plan that will add device steps names both files in the segment that adds them. **Layer:** the
  `e2e` manifest test fails on a step without a row, but only when `e2e/run.sh tests/unit` runs;
  neither `scripts/ladder.sh` nor CI runs it, so at the ladder this is prose-only.
- **Record:** note the deviation-from-Tasker explicitly in `STATE.md`.

### 6. Cutting a release / version bump
The owner publishes releases from the GitHub "Draft a new release" UI on `main` (which creates the
`vX.Y.Z` tag); `release.yml` then builds + signs + attaches the APK, and F-Droid builds that tagged
commit and reads its metadata. The **in-app version is decoupled from the tag** and has drifted before
(the `v1.0.2` tag was cut while `build.gradle.kts` still said `1.0.1` / `versionCode 4` — see D-099),
so check it explicitly.

> **⚠️ NEVER write the literal `[skip ci]` (or `[ci skip]` / `[no ci]` / `***NO_CI***`) in a commit
> message or the PR title (D-115).** GitHub honors that token on `push`/`pull_request` events, and a
> **squash-merge folds the commit messages + PR title into the squash commit** — so a stray `[skip ci]`
> (even as descriptive prose, e.g. documenting `clean-dist.yml`) lands on `main` and **silently skips
> ALL workflows for that commit**, including `build`/`codeql` on the push AND `release.yml` on the tag
> that points at it (this is exactly why v1.2.0's release "didn't run"). `release.yml` now primarily
> triggers on `release: published` (immune to skip-ci) + a `workflow_dispatch` fallback, but the token
> still skips the main-push `build`/`codeql`. `release-preflight.yml` machine-enforces this on commit
> messages + PR title (D-124). **The PR body may freely *document* the token** (the scan exempts it, so
> a PR can explain release CI without false-failing) — but if your squash setting uses the PR
> description, prefer the hyphenated `skip-ci` there too. The only legitimate use of the literal is
> inside a workflow's OWN auto-commit heredoc (e.g. `clean-dist.yml`). If a release's workflow was
> skipped, run **Actions → Release → "Run workflow"** with the tag, or re-publish the release.

- **Check the current release state first** (tags are not always present in a fresh clone):
  ```bash
  git fetch --tags origin
  git tag --sort=-v:refname | head        # latest v* tag = the released version
  git show "$(git describe --tags --abbrev=0)":app/build.gradle.kts | grep -E 'versionCode|versionName'
  grep -E 'versionCode|versionName' app/build.gradle.kts   # what the working tree ships
  ```
- **Invariant — the build must never ship behind a tag.** Any change destined for a new release
  must set, in `app/build.gradle.kts`:
  - `versionName` ≥ the latest `v*` tag, bumped by **semantic versioning** — decide the field by the
    *nature* of the change, do not reflexively bump patch:
    - **patch** (`x.y.Z`) — bug fixes, doc/packaging, internal refactors; no user-visible behavior
      or capability change (e.g. D-098 Save/Cancel clip fix → `1.0.3`).
    - **minor** (`x.Y.0`) — new user-facing feature, a new setting/range, or **observable platform
      behavior** such as a `targetSdk` bump (e.g. targetSdk 35→36 → `1.1.0`). Reset patch to 0.
    - **major** (`X.0.0`) — a breaking change: a settings-schema migration that can't round-trip, a
      dropped feature, a minSdk raise that strands devices, or any change that invalidates a user's
      existing profiles/config. Reset minor and patch to 0.
    - When a change spans categories, pick the **highest** that applies.
  - `versionCode` **strictly greater than every released code** (monotonic; F-Droid rejects a
    re-used code). Bump by 1 from the highest code ever shipped, not from the last tag's code if
    that tag forgot to bump. (versionCode is always +1 regardless of which semver field moved.)
- **F-Droid changelog:** add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (the
  filename is the **versionCode**, not the name) with a short user-facing note. **Keep it under 500
  characters** (whole file incl. the trailing newline — aim ≤ 480 for margin): F-Droid's code-quality
  scan flags a longer `whatsNew` as a Minor finding. The cap is on **characters (codepoints), not
  bytes** (DA-019) — a note with em dashes / accents / emoji can exceed 500 bytes while under 500
  chars, so `wc -c` **overcounts**; measure with `LC_ALL=C tr -d '\200-\277' < file | wc -c`
  (strips UTF-8 continuation bytes → one byte per codepoint), which is exactly what
  `scripts/guards/fdroid-changelog.sh` machine-fails the current versionCode's file over (D-173). **`release.yml`
  auto-reuses this file as the GitHub Release's "What's new" section (D-123)** — it reads the tagged
  build's `versionCode`, looks up the matching changelog, and slots it between the owner's UI summary
  and GitHub's auto "What's Changed". So the owner no longer hand-copies the changelog into the release
  body; just keep this file accurate. (If it's missing, the release still publishes with only the auto
  "What's Changed".)
- **`MATH_EXPLAINED.md` follows every version bump (DE-039).** The user guide names the version it
  was checked against in its intro and pins its §15 source links to that release tag. In the unit
  that bumps the version, re-pin both to the new version and fix every section whose behaviour,
  default, range, message or screen changed since the pinned release. Find them with
  `git diff v<the guide's pinned version> -- domain/src/main app/src/main platform/src/main`,
  starting from the guide's own pin, not the latest tag: if a bump skipped the guide, the latest
  tag would hide that release's changes. `domain/` changes touch the math sections, `app/` and
  `platform/` changes the runtime ones (hand-off, calibration, overrides). If nothing user-visible
  changed, re-pin anyway. A unit that lands after the bump and before the tag re-runs the diff
  from the same pin. Before hand-off, check that every §15 path exists in the tree
  (`git cat-file -e HEAD:<path>`). The PR template's Release impact asks for the outcome, re-pin
  included. The re-pinned links do not resolve until the owner publishes the tag. **No guard
  enforces this bullet**; this bullet is the check. That is a choice: the guide shipped stale
  through 1.15.1–1.15.3 (DE-039), and a later unit may add a guard comparing its pinned version
  with `versionName`.
- **F-Droid store icon (DA-024):** the listing icon comes from
  `fastlane/metadata/android/en-US/images/icon.png` (512×512), **not** from the APK — F-Droid cannot
  rasterize our adaptive-icon XML (`mipmap-anydpi/ic_launcher.xml`), so with no PNG the client shows
  its generic placeholder. That PNG is a rendered copy of the launcher vectors, so **any launcher-icon
  change must re-render it in the same unit** — source + one-line recipe in
  `docs/rebuild/design/store_icon.svg`. No guard enforces the lockstep (the incident-only bar, DA-015:
  the drift itself hasn't happened yet); this bullet is the check. The icon only reaches the store
  when F-Droid builds a **new tagged release** — an existing release's listing never backfills.
- **Check the F-Droid compatibility run before tagging (DA-027).** `fdroid-compat.yml` rebuilds the
  commit in F-Droid's own `fdroidserver:buildserver` image via `gradlew-fdroid`, runs `fdroid
  scanner`, checks the APK signing blocks (D-137) and the live fdroiddata recipe, and compares the
  F-Droid build against a normal release build. It runs on **PRs** touching `**/*.gradle.kts`,
  `gradle/**`, `gradle.properties`, `fastlane/metadata/**`, `scripts/fdroid-check.py`,
  `fdroid-compat.yml` or `release.yml`; on **every `main` push and every `v*` tag, unfiltered**
  (path filters and ref filters are ANDed, so a filtered tag trigger would never fire — DA-028);
  and on **`workflow_dispatch`**. Three consequences for a release, in the order they bite:
  - The **tag run fires after the tag exists** — backstop, not gate. The pre-tag evidence is the run
    on the release commit itself: every `main` push now runs it, so a commit merged to `main` has a
    verdict. Tagging something that never landed on `main` (or re-checking after a red run) means
    firing one by hand: **Actions → F-Droid compatibility → Run workflow**.
  - **If it is red, do not tag** — and if a *tag* run goes red, fix and re-release before F-Droid
    picks the tag up; red there means the release would silently never appear in F-Droid.
  - Green does not prove the **metadata** check ran: it warns-and-passes when gitlab.com is
    unreachable (deliberate — see `FDROID_VALIDATION.md`, which also lists what green never covers:
    the real signing key, the release job as a third environment, store-listing metadata).
  **No guard enforces this bullet** — `release-preflight.yml` does not look at F-Droid; this bullet
  is the check (same standing as the DA-024 icon lockstep above).
- **⚠️ One-time, next release only — confirm F-Droid still reproduces (DA-026).** The AGP 8.7.3 →
  8.13.2 bump changes `classes*.dex` and the baseline profile, so the **first tagged release after
  it** is the first time F-Droid rebuilds our GitHub-signed APK under the new toolchain. It was
  verified in F-Droid's own buildserver image before shipping (byte-identical APK across two
  environments), but the GitHub Actions runner was never the environment under test. After that
  release builds, check the F-Droid build log reports `...successfully verified` /
  `compared built binary to supplied reference binary successfully` for the new versionCode. If it
  instead reports a mismatch, the app is **not** delisted — F-Droid simply doesn't publish that
  version — and the fix is a repo-side determinism fix, not a rollback of the release. **Delete this
  bullet once one release has verified green**; it is a one-shot check, not standing process.
- **Retire the round script (DB-010).** A train under development may carry **one**
  `DEVICE_TEST_SCRIPT_<version>.md` — what *this* unreleased train changed, so the owner isn't
  re-running the whole app to check one fix. It is **ephemeral**: when the version ships, fold
  anything with standing value into the numbered sections of `DEVICE_TEST_SCRIPT.md` (append or
  extend an existing step — sections are cited by number, so never renumber), add or update the
  matching `e2e/scenarios.toml` rows (playbook 5, "Device steps change in two places"), and
  **delete** the round file. Git history is its archive; `docs/history/` is not (that is the frozen migration
  record and takes no maintenance-era files). Two round scripts alive at once means the previous
  one should already have been retired.
- **Record:** a `STATE.md` Changelog line; if the version drifted or you changed the release
  process, a `docs/LEDGER.md` row. **Do NOT keep a per-version changelog in `build.gradle.kts`**
  (D-127): the history lives in `STATE.md`, the ledger, the fastlane changelogs, and git — the gradle
  file keeps only the bump *invariant* comment, not a running log (it had grown to ~50 lines of
  redundant narrative).
- **Tagging stays an owner step** — do not create tags or open releases yourself.
- **CI guardrail (`release-preflight.yml`, D-124).** A secret-free PR check enforces this checklist so a
  miss is caught before merge, not after a bad tag. It runs the version/changelog checks **only when the
  PR ships app code**: shipped `src/main`/`src/release` trees, Gradle build graph/toolchain files,
  wrapper files, or ProGuard/consumer rules. Harness/config/docs/workflow/test/metadata changes,
  debug-variant `src/debug` trees and the owner-run `e2e/` device harness do not manufacture a
  release bump (DD-058). Any path in neither explicit class fails closed and must be
  classified in the same PR. **Prose-only invariant:** if the build begins consuming a file under
  a non-shipping tree, that input is reclassified as shipping in the introducing PR; CI cannot infer
  a future Gradle input graph. When the release gate fires it requires:
  `versionCode` **strictly greater** than the latest `v*` tag's code, `versionName` not regressed below
  that tag and semver-shaped, and a non-empty `changelogs/<versionCode>.txt`. The **skip-ci token scan**
  (D-115) runs on *every* PR — it greps the PR's commit messages + title + body (not file contents) for
  `[skip ci]` / `[ci skip]` / `[no ci]` / `[skip actions]` / `[actions skip]` / `***NO_CI***`. This is a
  backstop, not a substitute for doing the checklist; if it ever blocks a legitimate change, fix the gate
  in the same PR (RUNBOOK "When CI fails") rather than working around it.

### 7. Bumping `targetSdk` (new Android platform)
First done 35→36 (Android 16) — see `STATE.md` Changelog and the impact matrix it carried.
Do it in two reviewable commits; on-device verification is owner-only (no emulator).

- **Read first:** Google's "Behavior changes: Apps targeting Android <N>" + "Behavior
  changes: all apps" pages. Build an **impact matrix** scoped to *this app's* surfaces only
  (don't audit the whole OS): specialUse FGS + FGS background-job quotas, ongoing
  notification + actions, `WRITE_SECURE_SETTINGS`/Settings writes, background location,
  `PACKAGE_USAGE_STATS`, exported boot receiver, app widget, QS tile, accessibility overlay,
  edge-to-edge enforcement, predictive back, package visibility `<queries>`, 16 KB page size.
  Mark each row **no-op / config-only / code change / blocker**; put the matrix in `STATE.md`
  `## Active work`, creating that section if it is absent. Every "code change"/"blocker" row gets a sub-task before flipping targetSdk.
- **Install the SDK platform** (fresh containers ship only the current one):
  `sdkmanager "platforms;android-<N>" "build-tools;<N>.0.0"`.
- **Commit 1 — `compileSdk` only** (keep `targetSdk` one behind). Bump `compileSdk` in
  `app/build.gradle.kts` **and** `platform/build.gradle.kts`. Run the full ladder. This is
  forward-compat with no behavior change and is independently shippable if the targetSdk flip
  later stalls.
- **Commit 2 — `targetSdk = <N>` + version bump.** Bump `targetSdk` (app only) and the
  version per §6 (targetSdk is observable behavior → minor bump, e.g. 1.0.x → 1.1.0). Add the
  `changelogs/<versionCode>.txt`. Address the matrix's code-change rows.
- **Robolectric tracks the SDK.** Tests have no `@Config(sdk=…)` pins, so they auto-run under
  the manifest `targetSdk`. Each new platform needs a Robolectric release that supports it
  (36 → **4.16.1**, which **requires JDK 21** at test runtime — the Gradle JVM, not
  `sourceCompatibility`). Symptom if too old: `targetSdkVersion=<N> > maxSdkVersion=<N-1>`
  `IllegalArgumentException` (`initializationError` on every Robolectric class). Bump
  `robolectric` in `gradle/libs.versions.toml`; the matching `android-all` jar is fetched from
  Maven on first run.
- **Native libs / 16 KB page size.** Even with no NDK code, transitive AndroidX libs ship
  `.so`s (DataStore, graphics-path). Verify 16 KB alignment on the assembled APK:
  `readelf -lW <lib>.so | grep LOAD` (want `0x4000`) and
  `build-tools/<N>.0.0/zipalign -c -P 16 -v 4 <apk>`.
- **Acceptance:** full ladder green, then **owner runs the on-device Pass A (regression) +
  Pass B (feature-availability) matrices** — the ladder cannot catch "the OS silently stopped
  delivering us X". Build a debug APK for the owner to sideload. **`dist/` is `.gitignore`d (D-112)** —
  do NOT commit the APK: build it into `dist/` and **send it to the owner via the file tool** (or point
  them at the `build.yml` `app-debug` CI artifact). `clean-dist.yml` stays as a backstop that removes a
  force-added `dist/` from `main`.
  Debug builds carry a separate package (`applicationIdSuffix=".debug"`, label "Tideo AB (Debug)",
  Shizuku authority `${applicationId}.shizuku`) so a test build coexists with the owner's signed
  release without sharing data — keep this (D-106); the debug app has its own storage, so ELEVATED
  needs a separate `pm grant … com.tideo.autobrightness.debug …`.
  > **⚠️ Run only ONE variant's service at a time (D-128).** Coexistence is for *swapping* (install both,
  > enable one). If BOTH the debug and release services run at once they fight over the single global
  > `Settings.System.SCREEN_BRIGHTNESS`: the self-write marker is per-process, so each instance sees the
  > OTHER's writes as a manual override and pauses — a mutual resume→light-change→pause loop that looks
  > like a runtime bug but is just two controllers on one setting. Disable (or uninstall) one; a truly
  > disabled instance is inert (no observer, no writes). This only bites the developer, never an end user.
- **Record:** `STATE.md` Changelog line; if Android <N> forced a workaround, a `D-NN` row.
  If anything here was wrong/stale, fix this section in the same change.

### 8. Dependabot upgrade / CI action bump
- **When:** a Dependabot PR lands. **github-actions** is version-updates ON, **grouped into one PR**
  (`groups: "*"`), monthly. **gradle is security-only** (`open-pull-requests-limit: 0`) — a gradle PR
  appears only on an advisory; no speculative bumps (D-135). Read `.github/dependabot.yml` +
  DB-038 first.
- **Scorecard.dev is run-once/local (v5.5.0, ephemeral — not a CI gate).** Its recommendations
  survive as the two rails below. **Pinned-Dependencies** is now half-mechanised —
  `scripts/guards/action-pins.sh` (DB-076) holds the shape checks named in step 2, and the tag↔SHA
  claim itself stays yours. **Token-Permissions** is prose only: nothing re-checks it.
  - **Pinned-Dependencies:** every remote action `uses:` is a 40-hex **commit SHA** + trailing
    `# vX.Y.Z` marker, and every `docker://` image is pinned to a 64-hex `sha256` digest.
    **Never revert a pin to a tag ref** — a moved tag changes what runs and drops the score to 0.
  - **Token-Permissions:** top-level `permissions:` stays minimal/read-only; elevate per-**job**,
    never a top-level `contents: write`.
- **Steps:**
  1. Keep the grouped PR as **one** — grouped review is deliberate; don't split it.
  2. **Resolve each bumped line's tag to its commit on GitHub, by hand.** The pin must equal it and
     the marker must name it. Dependabot rewrites both, but its marker rewrite silently fails when
     **trailing prose follows the version** on the `uses:` line, leaving a stale `# vX.Y.Z` on a
     moved SHA (DB-038 decay, hit twice). Which layer holds this: `scripts/guards/action-pins.sh`
     (DB-076) fails the ladder on a tag ref, an unlabelled pin, one SHA wearing two markers, and one
     action+version resolving to two commits — the last two being the shape a failed marker rewrite
     produces. It cannot check the tag↔SHA claim itself, because that needs the network and the
     ladder is offline, so a pin mislabelled **the same way everywhere** is still yours to catch
     here and nowhere else. Both cross-checks compare call sites against each other, so **an action
     pinned at only one call site has no second opinion**, and step 2 is the only layer that sees a
     stale marker on it at all. Which those are:
     `grep -rhoE 'uses: *[^@ ]+@[0-9a-f]{40}' .github/workflows/ | sed 's/@.*//' | sort | uniq -c | sort -n`
     (four of eleven, 2026-08-23).
  3. **Fix prose Dependabot can't touch** — it never edits comments. E.g. `build.yml`'s Node-24
     policy block names the pinned majors ("Do NOT downgrade…") and goes factually wrong on a bump.
     Update any such block in the **same** PR.
  4. **Let CI run against the PR** — the bump is only validated by the workflows executing, above all
     `fdroid-compat.yml`'s reproducibility job round-tripping `upload-`/`download-artifact`. Do **not**
     cherry-pick action bumps onto an unrelated branch where those jobs never run.
  5. **Name the bumped lines no PR runs automatically (DE-026).** A workflow triggered only by a
     tag, a release, a dispatch, a `main`-only push or `pull_request_target` (which never runs the
     PR's copy of its file) does not run on the PR. Run both commands below on the PR's HEAD tree,
     never the session branch, which by step 4 does not carry the bumps: a detached worktree
     (`git worktree add --detach <scratch dir> <head SHA>`, removed with
     `git worktree remove --force <scratch dir>`). This lists those workflows:
     `grep -LE '^  pull_request:' .github/workflows/*.y*ml` (`release.yml`, `release-signing.yml`,
     `clean-dist.yml`, `translation-badges.yml` and `redirect-external-prs.yml`, 2026-11-03). A
     bumped line in one is covered at PR time when a step using the same action at the same SHA
     ran, not skipped, in a job the PR's checks list; the record names that job, and it shows the
     action runs, not that this file's own inputs work with it. A pin no PR-run workflow carries
     has no automatic PR-time run:
     `pins() { tr '\n' '\0' | xargs -0 grep -hoE "uses[\"']? *: *[\"']?[^@ \"']+@[0-9a-f]{40}" /dev/null | sed -E "s/.*[: \"']//" | sort -u; }; comm -23 <(grep -LE '^  pull_request:' .github/workflows/*.y*ml | pins) <(grep -lE '^  pull_request:' .github/workflows/*.y*ml | pins)`
     (`actions/github-script` and `softprops/action-gh-release`, 2026-11-03). Route each such bump
     to the Owner queue with the check that settles it, never into the record as green:
     - `release-signing.yml` only signs and uploads an artifact, so the owner may dispatch it on
       the PR branch (Actions → Release Signing → Run workflow), which runs its own copy with its
       own inputs; it runs with the signing secrets, which the same SHAs receive after the merge.
     - `release.yml` is never dispatched for this, because a dispatch attaches an APK to a real
       release; its check is the next tag's run:
       `gh run list --workflow release.yml --limit 1 --json conclusion,createdAt`.
     - `redirect-external-prs.yml`'s `triage` job skips the owner's and bots' PRs, so its first run
       is the next outside contributor's PR, which nobody can schedule; its check is the same
       command with `--workflow redirect-external-prs.yml`, or the owner accepts it as watch-only,
       a broken run costing one missing triage comment.
     Both commands match a two-space-indented `pull_request:` line, nothing more. Any other
     spelling of the trigger lists as never-PR, the safe direction, while a PR workflow whose
     `branches:`, `paths:` or `types:` filter, or a job or step `if:`, skipped this PR still lists
     as PR-run, so match against the PR's checks and their step conclusions, not the list. A
     newline in a file name breaks them. **Prose only:** no guard runs these commands or reads the
     record; the PR's checks and job logs are the evidence.
- **Avoid:** SHA→tag reversion; splitting the grouped PR; trusting the marker on any line with
  trailing prose; merging before `build` **and** `fdroid-compat` are green; bumping gradle on a
  cadence; calling a line step 5 names validated because the PR's CI is green.
- **Acceptance:** full CI green on the PR, and every bumped line step 5 names either matched to a
  step that ran its SHA in the PR's checks or routed to the Owner queue with its settling check.
  **Record:** STATE changelog line, with step 5's outcome; a `D-NN` only if the process itself
  changed.

### 9. Running the device E2E suite on the owner's phone
- **When:** a build needs device evidence beyond JVM tests — a fix on a scenario's path, a train
  nearing release, or the owner asks. It covers the `auto` rows only; `DEVICE_TEST_SCRIPT.md`'s
  `E2E auto:` line under each section names them, and every other step stays the owner's.
- **Read first:** `e2e/README.md` (boundary, journal, run commands), DD-015 (decision and safety
  model), DD-040 (wake scenarios).
- **Preconditions, from the owner:** the phone unlocked and reachable over adb (the serial lives in
  the environment, never in a file); the debug build installed; Tideo resolving to English; and
  hands off the phone's display settings until the run ends (`e2e/README.md` "Known limits").
  Confirmations go in `TIDEO_E2E_CONFIRM`: `automation` stands (no Tasker/MacroDroid profile acts
  on `STATE_CHANGED`, owner 2026-10-04); `contexts` only when the owner confirms it for that run;
  `unlock` only after asking, since each wake then waits 60 s for their fingerprint (DD-040).
- **Steps:**
  1. `e2e/run.sh tests/unit` — device-free.
  2. `e2e/run.sh --preflight` — read-only; anything but exit 0 stops the run. Its "would run" list
     is the expected set. Note the installed build's commit: a build older than the tree's last
     `app/` change is evidence about that build, not the tree.
  3. A new build goes on only through `e2e/run.sh --install <apk>` or by the owner's own hand.
  4. `e2e/run.sh tests --ignore=tests/unit --junitxml=<private store>/junit-<date>.xml` — one
     invocation; collection orders it by effect stage. JUnit XML carries skip reasons that may name
     the device, so it goes to the private store, never under `e2e/`.
  5. The wake pair, once the owner is at the phone: add `unlock` and run `-k "s02_10a or s02_10e"`.
  6. `e2e/run.sh --recover` — exits 0 at once on an empty journal. A pending journal blocks every
     later run; its conflicts are the owner's to resolve. Recovery never writes a conflicted key
     itself, but a preference restore can rewrite one through Tideo (`e2e/README.md` "Known
     limits").
  7. `e2e/run.sh --preflight` again, and compare its record with step 2's: settings, grant,
     runtime state and each of Tideo's private stores, field by field. Only churn the run
     explains may differ; nothing compares them automatically.
- **Failures:** classify as Tideo defect / harness / framework / timing / OEM / invalid assumption.
  Fix in scope, rerun the node, then the group. A Tideo defect is playbook 4.
- **Avoid:** any device mutation outside `run.sh`: the boundary covers only the harness, so a
  hand-typed `adb shell` write is unjournaled. Never `pm clear`, uninstall, reboot or `bmgr`.
- **Layer:** the boundary, journal and SKIP rules are code with unit tests; the preconditions,
  asking before `unlock` and step 7's comparison are prose only.
- **Acceptance:** every collected scenario passes, or skips with a SKIP rule's reason, and the
  skips match preflight's list or a scenario's own stated precondition; the journal is empty; step
  7 finds no unexplained difference. **Record:** a ledger row naming the build (versionName/versionCode and its commit) and
  the pass/skip/fail counts; a STATE changelog line.

## When CI fails on a PR (workflow vs code)

The local ladder and CI run the same script, but the commit, index and worktree are inputs too.
A guard built on `git ls-files` may omit an untracked file; staging it after the local run changes
the input CI receives and can turn local-green into CI-red without any environment difference.
Read the failing log, reproduce the exact tree state CI checked, and stage new files before the
local ladder when discovery is index-dependent. Then triage in this order:

1. **Read the failing step's log** — distinguish the three kinds:
   - **Real failure** (a test assertion, a lint finding, a compile error): reproduce locally with the
     exact failing task and fix the *code* (playbooks 1–5). This is the common case; trust it.
   - **Toolchain/environment mismatch** (CI runner differs from local): the symptom is a failure that
     does **not** reproduce locally. Fix the *workflow*, not the code. Known examples:
     - **JDK version** — Robolectric 4.16+ needs **JDK 21** to run SDK-36 tests; all workflows pin
       `java-version: '21'`. A `setup-java` pinned to 17 throws `initializationError` /
       `targetSdkVersion > maxSdkVersion` only in CI. Bump the workflow JDK in lockstep with any
       Robolectric/targetSdk change (RUNBOOK §7).
     - **SDK/build-tools** — CI relies on AGP fetching the compile SDK on demand; a new `compileSdk`
       just works, but a new build-tools requirement may need a `setup-android` tweak.
   - **Flake** (network blip, cache corruption, runner OOM, `actions/*` outage): non-deterministic,
     unrelated to the diff. Re-run the job once. If it passes, it was a flake — note it and move on;
     do **not** "fix" code for a flake. If it recurs, treat it as an environment issue and harden the
     workflow (pin the action version, add a retry, drop a poisoned cache key).
   - **F-Droid-only failure** (`fdroid-compat.yml` red while `build.yml` is green): that is the
     workflow doing its job — the repo builds for us but not for F-Droid, or the two builds no
     longer match. It is a *real* failure with a real consequence (the next release silently not
     appearing in F-Droid), not an environment quirk to re-run away. Read the annotation's title
     (`Normal release build failed`, `F-Droid compatibility validation failed`, `F-Droid scanner
     check failed`, `Signing assumption check failed`, `Metadata validation failed`,
     `Reproducibility validation failed`), then `FDROID_VALIDATION.md` → "Reading a failure", which
     gives the local reproduction command for each. The exception: upstream moves under us (their
     image or the unpinned `fdroid scanner` changed) — treat that as an environment change and say
     so in the PR. **The generic remedy below does not apply: do NOT pin the buildserver image or
     the scanner to get green.** Tracking upstream is the design (DA-027); a frozen copy of
     upstream's opinion stops predicting what upstream will reject. Adapt the repo — or if upstream
     itself broke, say so and wait.
2. **Changing a workflow is in scope** when the failure is environmental — workflows are code; fix
   `.github/workflows/*` in the same PR as the change that needs it (don't disable a check to get
   green). **Never** weaken a gate (skip tests, drop `abortOnError`, `continue-on-error`) to pass CI.
3. **If a failure is real but out of scope** (a pre-existing flake in an unrelated suite, an upstream
   action breakage you can't fix), say so in the PR with the log excerpt and where you're stuck —
   don't silently retry forever.

## Self-adaptation — keep this runbook useful

If this runbook lacks what you need for the task in front of you:
1. Consult the live reference docs above (esp. `docs/LEDGER.md`) and the frozen
   `docs/history/` narrative.
2. If you learn a durable fact future sessions need, record it as a new numbered deviation
   in the live ledger file (`LEDGER_LINE_CAP` cap + rollover, D-153/DA-001) and/or correct the relevant
   reference doc — provenance-stamped, terse.
3. If a playbook here is wrong, stale, or missing a case you just handled, **fix this RUNBOOK in
   the same change.** Treat the runbook as code: it should always reflect how changes are
   actually made now.

Boundary (DA-006): self-adaptation covers *operational* content — playbooks, the doc index,
module maps, commands, stale paths. *Binding* rules (everything in `docs/PROTOCOLS.md`, this
file's Invariants (`AGENTS.md` rule 18), guard semantics, git/permission policy) are never
self-adaptation: they go through the rule-review protocol (DA-005), and process-reshaping changes
through the Owner queue (`docs/PROTOCOLS.md` → **Session discipline** 7(d)).
