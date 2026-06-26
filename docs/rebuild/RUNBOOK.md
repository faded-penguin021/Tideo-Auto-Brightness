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

### 1. Tasker profile added / changed / removed (a trigger context or pipeline gate)
- **Read first:** `extraction/profiles.md` (+ `contexts_spec.md` for context watchers); the
  relevant `DEVIATIONS_LEDGER` rows (profile gating, ConditionList semantics). Re-read the XML
  only via `XML_RECIPES.md`.
- **Code:** the hardcoded-boolean profile gates in `:domain`/`:platform` (no generic
  ConditionList evaluator exists — gates are explicit Kotlin booleans with a truth-table test).
- **Parity:** ConditionList semantics — plain And/Or bind tighter than And2/Or2; XML children
  are alphabetical (re-sort numerically). Preserve the single-coroutine, drop-on-reentry model.
- **Acceptance:** update the gate truth-table test; run the ladder below.
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
  wrong + a `STATE.md` entry) → run the ladder.
- **Record:** `STATE.md` (and `parity_gaps.md` if it was a parity gap).

### 5. Tasker-independent feature (rare — no parity source)
- No golden reference exists; still obey the coding conventions in `CLAUDE.md` and add tests.
- **Record:** note the deviation-from-Tasker explicitly in `STATE.md`.

### 6. Cutting a release / version bump
The owner publishes releases by pushing a `vX.Y.Z` git tag on `main`; F-Droid then builds that
tagged commit and reads its metadata. The **in-app version is decoupled from the tag** and has
drifted before (the `v1.0.2` tag was cut while `build.gradle.kts` still said `1.0.1` / `versionCode
4` — see D-099), so check it explicitly.

- **Check the current release state first** (tags are not always present in a fresh clone):
  ```bash
  git fetch --tags origin
  git tag --sort=-v:refname | head        # latest v* tag = the released version
  git show "$(git describe --tags --abbrev=0)":app/build.gradle.kts | grep -E 'versionCode|versionName'
  grep -E 'versionCode|versionName' app/build.gradle.kts   # what the working tree ships
  ```
- **Invariant — the build must never ship behind a tag.** Any change destined for a new release
  must set, in `app/build.gradle.kts`:
  - `versionName` ≥ the latest `v*` tag (use the next patch, e.g. latest tag `v1.0.2` → `1.0.3`);
  - `versionCode` **strictly greater than every released code** (monotonic; F-Droid rejects a
    re-used code). Bump by 1 from the highest code ever shipped, not from the last tag's code if
    that tag forgot to bump.
- **F-Droid changelog:** add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (the
  filename is the **versionCode**, not the name) with a short user-facing note.
- **Record:** a `STATE.md` Changelog line; if the version drifted or you changed the release
  process, a `DEVIATIONS_LEDGER.md` row.
- **Tagging stays an owner step** — do not create tags or open releases yourself.

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
  delivering us X". Drop a debug APK in `dist/` (temporary, deleted before merge) for sideload.
- **Record:** `STATE.md` Changelog line; if Android <N> forced a workaround, a `D-NN` row.
  If anything here was wrong/stale, fix this section in the same change.

## Acceptance ladder

Run the relevant subset until green (on-device behavior is owner-verified — no emulator, no KVM):

```bash
./gradlew :domain:test            # pure-JVM engine + golden parity tests
./gradlew :platform:test          # Robolectric adapter tests
./gradlew :app:testDebugUnitTest  # app unit + Robolectric tests
./gradlew :app:assembleDebug      # APK
./gradlew :app:lintDebug          # lint vs frozen baseline
```

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
