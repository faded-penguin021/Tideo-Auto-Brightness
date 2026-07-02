# RUNBOOK — maintenance playbook

The Tasker→Kotlin rebuild is **done and shipped** (v1.0.0). This is the entry point for
*changing* the app afterward while preserving Tasker feature parity. Pick the change-type
playbook that matches your task, read the reference docs it names, then do the work.

The migration narrative (segment briefs, gate findings) is frozen in `../history/` — consult it,
don't extend it. The numbered deviations live in `DEVIATIONS_LEDGER.md`, a permanent append-only
registry (record new ones as D-096+; never compress it). **Code + golden vectors are ground
truth**; where any doc disagrees with the code, trust the code (and fix the doc).

## Where logic lives

- **`:domain`** — pure JVM/Kotlin. ALL math/decision logic (smoothing, threshold, curve
  mapping, scaling, animation planning). No Android imports. Golden-tested.
- **`:platform`** — Android library. Real system adapters behind small interfaces (light
  sensor, brightness/secure-dimming writers, PrivilegeManager, ContentObserver override
  detector, battery/wifi/location/foreground-app readers).
- **`:app`** — Compose M3 UI (~9 screens), DataStore settings (`AabSettings`), foreground
  service runtime, QS tile, boot receiver, notification.

## Reference-doc index (live, in `docs/rebuild/` unless noted)

| Question | Doc |
|---|---|
| What is every Tasker artifact and how was it dispositioned? | `PARITY_CHECKLIST.md` |
| End-to-end pipeline behavior (sensor→dimming) | `extraction/pipeline_spec.md` |
| A specific profile / gate's semantics | `extraction/profiles.md`, `extraction/contexts_spec.md` |
| A specific task's actions / curve math | `extraction/tasks/task<id>_*.md` |
| Default values & variable classification | `extraction/defaults_audit.md` |
| Ancillary features (tile, notification, debug, import/export) | `extraction/features_spec.md` |
| Anonymous scene-handler tasks | `extraction/tasks/anonymous_handlers.md` |
| Scene → M3 screen mapping | `screen_map.md`, `extraction/scenes/*` |
| How to safely re-read the source XML | `XML_RECIPES.md` |
| Known parity deviations / open gaps | `parity_gaps.md` |
| Privilege tiers / permissions / DataStore schema | `architecture/*` |
| Material 3 audit | `design/m3_audit.md` |
| Numbered deviations — solved mistakes + ongoing (⭐, append D-096+) | `DEVIATIONS_LEDGER.md` |

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
  relevant `DEVIATIONS_LEDGER` rows (profile gating, ConditionList semantics). Re-read the XML
  only via `XML_RECIPES.md`.
- **Code:** the hardcoded-boolean profile gates in `:domain`/`:platform` (no generic
  ConditionList evaluator exists — gates are explicit Kotlin booleans with a truth-table test).
- **Parity:** ConditionList semantics — plain And/Or bind tighter than And2/Or2; XML children
  are alphabetical (re-sort numerically). Preserve the single-coroutine, drop-on-reentry model.
- **Acceptance:** update the gate truth-table test; run the ladder below; **glue-review
  protocol** (below) on any `:platform`/runtime part of the diff.
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
- **Acceptance:** `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:lintDebug`.
- **Record:** update `screen_map.md` + `PARITY_CHECKLIST.md` + `STATE.md`.

### 4. Bug fix
- **Read first:** the reference doc for the affected area (above) + any related
  `DEVIATIONS_LEDGER` row.
- **Steps:** reproduce → add/adjust a failing test first → fix so it conforms to the golden
  vectors (never edit a golden vector to pass; changing one needs proof the extraction was
  wrong + a `STATE.md` entry) → run the ladder → **glue-review protocol** (below) if the fix
  touches `:platform`/runtime glue.
- **Record:** `STATE.md` (and `parity_gaps.md` if it was a parity gap).

### 5. Tasker-independent feature (rare — no parity source)
- No golden reference exists; still obey the coding conventions in `CLAUDE.md` and add tests.
  The **glue-review protocol** (below) applies to any `:platform`/runtime glue it adds.
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
  filename is the **versionCode**, not the name) with a short user-facing note. **`release.yml`
  auto-reuses this file as the GitHub Release's "What's new" section (D-123)** — it reads the tagged
  build's `versionCode`, looks up the matching changelog, and slots it between the owner's UI summary
  and GitHub's auto "What's Changed". So the owner no longer hand-copies the changelog into the release
  body; just keep this file accurate. (If it's missing, the release still publishes with only the auto
  "What's Changed".)
- **Record:** a `STATE.md` Changelog line; if the version drifted or you changed the release
  process, a `DEVIATIONS_LEDGER.md` row. **Do NOT keep a per-version changelog in `build.gradle.kts`**
  (D-127): the history lives in `STATE.md`, the ledger, the fastlane changelogs, and git — the gradle
  file keeps only the bump *invariant* comment, not a running log (it had grown to ~50 lines of
  redundant narrative).
- **Tagging stays an owner step** — do not create tags or open releases yourself.
- **CI guardrail (`release-preflight.yml`, D-124).** A secret-free PR check enforces this checklist so a
  miss is caught before merge, not after a bad tag. It runs the version/changelog checks **only when the
  PR ships app code** (any changed file outside `docs/`, `.github/`, `scripts/`, `fastlane/`, `*.md`, or a
  `src/test`/`androidTest` tree) — a docs/workflow/test-only PR skips them. When it fires it requires:
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
  Active work. Every "code change"/"blocker" row gets a sub-task before flipping targetSdk.
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

## Glue-review protocol (MANDATORY for `:platform` / `:app` runtime changes)

Any change touching a `:platform` adapter or `:app` runtime glue (service, pipeline controller,
observers, receivers, tile, notification actions) gets a **second, adversarial diff pass before
commit**: after the ladder is green, re-read the FULL diff fresh — as a hostile reviewer, not the
author — hunting specifically the ledger's proven bug classes:

- condition/gate **polarity and missing operands** (D-030 b: `scalingUse` dropped from an AND gate);
- **list/insertion order** — newest-first vs newest-last (D-030 b: Array Push at index 1);
- **observer/echo races** — token-consumption vs latest-value matching, delayed callbacks,
  no-op writes that never notify (D-034 a);
- **int truncation vs `Math.round`** round-trip drift across range normalization (D-034 b);
- **non-idempotent lifecycle calls** and per-process state that should survive process death
  (D-034 c — `savedMode` is the standing example);
- **null/absent sentinel handling** at startup — a reader that hasn't produced a real value yet
  must not match rules (D-108 battery `-1`);
- **fire-and-forget cancellation ordered before a compensating write** — `cancel()` without
  `join()` lets the dying coroutine's last write serialize AFTER the "restore" write (D-139:
  panic 255 vs an in-flight animation frame);
- **"send-to-running-service" assumptions** — `startForegroundService` CREATES the service, never
  a no-op; control actions (pause/reapply) must be validated in `onStartCommand` or they birth a
  zombie FGS (D-140: widget Reset while disabled started the sensor collector).

Rationale (D-030/D-034/D-035): every Sonnet migration segment passed its own acceptance gate,
yet dedicated review found real shipped bugs in exactly this glue — golden vectors cannot see
platform/runtime code, so reviewer attention is the only net there. There is no separate
review pass or stronger model behind you now; this second pass replaces it. If the pass finds
nothing, say so in the commit/PR body ("glue-review pass: clean"); if it finds something, fix
it before commit and record anything durable as a `D-NN`.

## Acceptance ladder

Run the relevant subset until green (on-device behavior is owner-verified — no emulator, no KVM):

```bash
./gradlew :domain:test            # pure-JVM engine + golden parity tests
./gradlew :platform:test          # Robolectric adapter tests
./gradlew :app:testDebugUnitTest  # app unit + Robolectric tests
./gradlew :app:assembleDebug      # APK
./gradlew :app:lintDebug          # lint vs frozen baseline
```

## When CI fails on a PR (workflow vs code)

The local ladder (above) and CI (`.github/workflows/build.yml`) run the *same* Gradle tasks, so a
green local tree usually means green CI. When CI is red but local is green, the failure is in the
**environment/workflow**, not your code — diagnose before "fixing tests". Triage in this order:

1. **Read the failing step's log** — distinguish the three kinds:
   - **Real failure** (a test assertion, a lint finding, a compile error): reproduce locally with the
     exact failing task and fix the *code* (playbooks 1–5). This is the common case; trust it.
   - **Toolchain/environment mismatch** (CI runner differs from local): the symptom is a failure that
     does **not** reproduce locally. Fix the *workflow*, not the code. Known examples:
     - **JDK version** — Robolectric 4.16+ needs **JDK 21** to run SDK-36 tests; all workflows pin
       `java-version: '21'`. A `setup-java@v5` with 17 throws `initializationError` /
       `targetSdkVersion > maxSdkVersion` only in CI. Bump the workflow JDK in lockstep with any
       Robolectric/targetSdk change (RUNBOOK §7).
     - **SDK/build-tools** — CI relies on AGP fetching the compile SDK on demand; a new `compileSdk`
       just works, but a new build-tools requirement may need a `setup-android` tweak.
   - **Flake** (network blip, cache corruption, runner OOM, `actions/*` outage): non-deterministic,
     unrelated to the diff. Re-run the job once. If it passes, it was a flake — note it and move on;
     do **not** "fix" code for a flake. If it recurs, treat it as an environment issue and harden the
     workflow (pin the action version, add a retry, drop a poisoned cache key).
2. **Changing a workflow is in scope** when the failure is environmental — workflows are code; fix
   `.github/workflows/*` in the same PR as the change that needs it (don't disable a check to get
   green). **Never** weaken a gate (skip tests, drop `abortOnError`, `continue-on-error`) to pass CI.
3. **If a failure is real but out of scope** (a pre-existing flake in an unrelated suite, an upstream
   action breakage you can't fix), say so in the PR with the log excerpt and where you're stuck —
   don't silently retry forever.

## Self-adaptation — keep this runbook useful

If this runbook lacks what you need for the task in front of you:
1. Consult the live reference docs above (esp. `DEVIATIONS_LEDGER.md`) and the frozen
   `../history/` narrative.
2. If you learn a durable fact future sessions need, record it as a new numbered deviation
   (D-096+) in `DEVIATIONS_LEDGER.md` and/or correct the relevant reference doc —
   provenance-stamped, terse.
3. If a playbook here is wrong, stale, or missing a case you just handled, **fix this RUNBOOK in
   the same change.** Treat the runbook as code: it should always reflect how changes are
   actually made now.
