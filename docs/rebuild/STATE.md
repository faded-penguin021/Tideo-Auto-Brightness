# STATE — project state & session memory

> **Length guard (read before editing).** Steady-state target ≤ 12 KB. **If this file exceeds
> 32 KB, aggressively compress before committing:** collapse each completed *Active work* stage
> into one Changelog line, move any durable gotcha into `DEVIATIONS_LEDGER.md` (the permanent,
> append-only registry — never compressed), and delete narrative/punch-list prose. The
> **Project** and **Current state** sections must always survive compression. The full migration
> narrative is already frozen in `../history/` — do not re-accumulate it here.

## Project

Native **Kotlin/Compose** Android app that is a feature-parity rebuild of the Tasker project
`Advanced_Auto_Brightness_V3.3`. Three modules: **`:domain`** (pure-JVM math/decision logic,
golden-tested), **`:platform`** (Android system adapters behind small interfaces), **`:app`**
(Compose M3 UI, DataStore `AabSettings`, foreground-service runtime, QS tile, boot receiver).
Privilege tiers: **BASIC** = user-grantable `WRITE_SETTINGS` (full core pipeline); **ELEVATED**
= `WRITE_SECURE_SETTINGS` via one-time `pm grant` (super dimming + Privileged Display toggles).
minSdk 31, target/compile 36.

## Current state

**Shipped: v1.6.2** (`versionCode 16`). **Pending release: 1.7.0 / `versionCode 17`** — the
Privileged Display Control feature, COMPLETE incl. Segment-5 polish (D-149–D-152;
`changelogs/17.txt` final). Owner squash-merges the session branch, runs
`DEVICE_TEST_SCRIPT.md` §11 on-device, then tags/releases 1.7.0.

`PARITY_CHECKLIST.md` is zero-`pending`; golden parity tests green; TODO/FIXME = 0;
`parity_gaps.md` has 0 open gaps. Full acceptance ladder green 2026-07-05.

How changes are made now: see `RUNBOOK.md` (change-type playbooks; the **glue-review protocol**
is mandatory for `:platform`/runtime diffs; multi-session features follow the playbook-5
persisted-plan pattern). The migration narrative is frozen in `../history/`; the deviations
registry stays live.

> Code/docs elsewhere cite deviations by number (e.g. `STATE.md D-048`, `F50`). All deviations
> — migration and ongoing — live in the permanent registry `DEVIATIONS_LEDGER.md` (200 rows per
> file, then `_A.md`/DA-…, `_B.md`/DB-… — D-153; gate findings are in
> `../history/STATE_rebuild.md`). Look there.

**Owner actions pending:**

- **H4 (D-135):** repo Settings → Code security → enable "Dependabot security updates" +
  "Private vulnerability reporting" (the committed files are inert without them).
- **H5 (D-137):** in the fdroiddata submission set `Binaries:` to the release-APK URL pattern
  and `reproducible: yes` (pin the CI's JDK 21) so F-Droid publishes the signed APK.
- **1.7.0:** on-device pass of `DEVICE_TEST_SCRIPT.md` §11 (Privileged Display), then
  tag/release after squash-merge.

## Decided non-items (don't re-litigate without new evidence)

- **Repo/process (2026-06/07):** root `CHANGELOG.md` (redundant with STATE + fastlane + the
  ledger); speculative dependency-currency bumps (only on a security advisory); a standalone
  doc-drift audit (RUNBOOK self-adaptation covers it); action SHA-pinning / Gradle dependency
  verification (declined 2026-06-29 as wrong cost/benefit for a solo F-Droid app).
- **Privileged Display (decided at Segments 4.5–5, D-150–D-152):**
  - **Per-toggle orthogonal scheduling** — D-150 built it, the D-151 pivot removed it.
    Scheduling IS "a Contexts rule loads a profile carrying display fields", winner-takes-all.
  - **A persisted last-applied seed** for `DisplayTogglesCoordinator` — deferred at 4.5: it
    would shrink the D-151 process-death residual by re-introducing exactly the latch-like
    persistence the pivot removed. Revisit only on real-world reports.
  - **QS tile / notification action for grayscale** (or any display toggle) —
    profiles/Contexts are the switching surface.
  - **Refresh-rate forcing** / OEM-specific alternate keys — OEM-fragmented (D-048/D-149).
  - **A manual Extra-Dim toggle** — Extra Dim is pipeline-owned (D-144/D-149).

## Changelog

One line per shipped change (newest first). Keep terse; details live in the ledger.

- 2026-07-05 — folds into 1.7.0/vc17 (owner-requested): **D-154** circadian Night Light
  temperature — profile toggle `nightLightCircadianEnabled` (full D-151 fan-out); the
  temperature rides the task90 tanh modifier (profile temp/AOSP-default at night → 4082 K by
  day) via a 60 s only-on-change ticker in `DisplayTogglesCoordinator` (own computation —
  pipeline cycles starve in steady light, D-110). Manual temp changes don't stick while
  tracking (consented). Tests +7. Glue-review: one comparator-replay finding fixed pre-commit.
- 2026-07-05 — docs/process only (owner-instructed): **D-153** deviations-ledger file cap —
  200 rows per file, rollover `DA-001`/`DB-001`…; summarizing rejected (rows are cited by
  number, stay verbatim). Pointers updated in the ledger header, CLAUDE.md, RUNBOOK.
- 2026-07-05 — docs-only (Privileged Display **Segment 5 — feature COMPLETE**): README +
  `screen_map.md` finalized (toggles = ELEVATED **profile settings** applied by
  profiles/Contexts — no standalone scheduler); owner checklist = **`DEVICE_TEST_SCRIPT.md`
  §11**; `plans/privileged-display.md` deleted (content in D-149–D-152); RUNBOOK playbook 5
  gains the multi-session persisted-plan pattern; architecture docs synced; non-items
  recorded above; STATE recompressed.
- 2026-07-05 — folds into 1.7.0/vc17 (UI polish, owner finding; refines D-152, no ledger row):
  the AOSP-keys/OEM note moved off an always-on footer card to a top-bar **ⓘ → dialog**
  (`SettingsScaffold` gains an `actions` slot); i18n ratchet 0.
- 2026-07-05 — folds into 1.7.0/vc17 (4.5 follow-up, owner findings): **D-152** profile port
  complete — AOD / stay-awake / HDR join `AabSettings` (7 display fields); ONE draft-edited
  profile surface + grant card; `applyNow` writes the device directly exactly when the service
  is off (Apply is never a silent no-op). Glue-review: clean.
- 2026-07-04 — folds into 1.7.0/vc17 (**Segment 4.5 — owner-instructed pivot**): **D-151**
  display toggles become PROFILE settings applied on profile change by
  `DisplayTogglesCoordinator` (idempotent only-on-change; seed adopts; resting = baseline; NO
  latch/sweep — accepted process-death residual). D-150 schedule system removed wholesale;
  `ContextMatching` + `SecureDisplayController` kept. Glue-review: clean.
- 2026-07-03 — folds into 1.7.0/vc17 (Segment 4): **D-150** display schedules end-to-end (own
  DataStore, edge-triggered coordinator with death-safe latch + restore, rules UI, shared
  `TriggerEditors.kt` extraction; glue-review: +1 s boundary-wake fix) — **removed again by
  4.5**; the ledger row is the record.
- 2026-07-03 — folds into 1.7.0/vc17 (Segment 3, `:domain`-only): display-rule resolver + the
  behavior-preserving **`ContextMatching` extraction** (goldens untouched and green). The
  resolver died with 4.5; `ContextMatching` stays live.
- 2026-07-03 — folds into 1.7.0/vc17 (Segment 2 — the core ask): the **Privileged Display
  screen** (route always registered; Menu row only at ELEVATED off live `tierFlow()`;
  self-guarding 3-channel grant card; read-back VM). Glue-review: one Mutex finding fixed.
- 2026-07-03 — repo-tooling only: `setup-android-sdk.sh` seeds the Gradle wrapper cache from
  `/opt` (the cloud egress proxy 403s the wrapper download).
- 2026-07-03 — 1.7.0 / `versionCode 17` (MINOR, Segment 1): **D-149** `:platform`
  `SecureDisplayController` — Night Light (+temperature), daltonizer, inversion, AOD,
  stay-awake-charging, Android-14+ force-SDR; AOSP-universal keys only; Extra Dim excluded
  (pipeline-owned). Changelog `17.txt`. Glue-review: one accepted finding documented.
- 2026-07-02 — tests-only (F-backlog U6 → **F-backlog CLOSED**): **D-148** the H3 glue-seam
  audit's last four seams covered (+19 tests; Shizuku*/MaintenanceWorker skips argued in-row).
- 2026-07-02 — docs-only (U5): parity transcription spot-check — **clean, zero disagreements**;
  `XML_RECIPES.md` gains R0 (restore the gitignored XML in a fresh clone).
- 2026-07-02 — 1.6.2/vc16 (U4): **D-146** NaN import guard; **D-147** widget actions off the
  exported provider. `/security-review` clean; `SECURITY.md` +3 scope notes.
- 2026-07-02 — 1.6.2/vc16 (U3): **D-144** post-death Extra-Dim residual cleared (tri-state
  latch); **D-145** `ShizukuShell` unbinds on bind timeout.
- 2026-07-02 — 1.6.2/vc16 (U2): **D-141** rule edits bypass the PASS-1 cooldown; **D-142** wifi
  SSID listener `[WIFI]`-gated + snapshot clear; **D-143** stale ssidFlow resolves dropped.
- 2026-07-02 — 1.6.2 / `versionCode 16` (PATCH, U1): **D-139** panic restore cancel-and-joins
  the animation consumer; **D-140** zombie-FGS gates on control intents. Changelog `16.txt`.
- 2026-07-02 — docs-only: **D-138** F-backlog adopted (U1–U6, retroactive adversarial review
  of the shipped glue + security & transcription audits).
- 2026-07-01 — build-config only (H5): **D-137** release APK **proven reproducible**; owner
  fdroiddata steps under "Owner actions pending".
- 2026-07-01 — tests + a test-seam (H3): **D-136** glue-seam audit + 4 gap-closing suites
  (+14 tests).
- 2026-07-01 — repo-policy only (H4): **D-135** `SECURITY.md` + security-only Dependabot
  (needs the owner-side Code-security toggles).
- 2026-07-01 — 1.6.1 / `versionCode 15` (PATCH, H2): **D-134** saved pre-service brightness
  mode persisted across process death.
- 2026-07-01 — docs-only: **D-133** hardening backlog adopted; RUNBOOK gains the mandatory
  **glue-review protocol** (H1); `FABLE_HANDOFF.md` deleted; STATE compressed.
- 2026-06-30 — 1.6.0 / `versionCode 14` (MINOR): **D-130** no-Location SSID path (DUMP grant,
  strategy order Shizuku → root → DUMP → Location); **D-131** full UI i18n (~250 strings,
  ratchet 0, human-only translations policy); **D-132** plug/unplug bypasses the battery
  cooldown. Owner on-device pass confirmed.
- 2026-06-29 — CI-only: per-job `timeout-minutes` + wrapper properties in Gradle cache keys;
  stricter supply-chain measures declined with reasons.
- 2026-06-29 — 1.5.0 / `versionCode 13` (MINOR): **D-125** wizard curve suggestion is
  user-driven (preview seeds the draft); **D-126** resume no longer loops back to paused (F64
  settle window also suppresses in-cycle override detection).
- 2026-06-29 — CI-only: **D-124** `release-preflight.yml` PR gate (versionCode/semver/changelog
  when the PR ships app code; skip-ci token scan on every PR).
- 2026-06-29 — CI-only: **D-123** `release.yml` reuses the F-Droid changelog as the GitHub
  Release "What's new".
- 2026-06-28 — 1.4.0 / `versionCode 12` (MINOR): **D-117**–**D-122** (PWM perceived-brightness
  graph, edge-to-edge modal, release-notes auto-append, fresh location fix, HTTPS geo-IP).
- 2026-06-28 — 1.3.0 / `versionCode 11` (MINOR): **D-116** Panic gesture rework +
  `%AAB_PanicSensitivity`.
- 2026-06-28 — 1.2.1 / `versionCode 10` (PATCH re-cut): **D-115** skip-ci token skipped
  v1.2.0's release; `release.yml` triggers on `release: published`.
- 2026-06-28 — 1.2.0 / `versionCode 9` (MINOR): **D-108**–**D-114** (battery sentinel, IA
  rework, confirmations, priority 1–100).
- 2026-06-28 — 1.1.1 / `versionCode 8` (PATCH): **D-107** explicit PendingIntents.
- 2026-06-26 — 1.1.0 / `versionCode 7` (MINOR): targetSdk/compileSdk 36, Robolectric 4.16.1
  (JDK 21), CodeQL, `.debug` suffix (D-106); folded **D-101**–**D-105**. Owner Pass A/B passed.
- 2026-06-25 — 1.0.4 / `versionCode 6` (PATCH): **D-100** nav-bar padding on bottom controls.
- 2026-06-24 — 1.0.3 / `versionCode 5` (PATCH): **D-098** dialog Save/Cancel clip; **D-099**
  version/tag realignment + RUNBOOK §6 release checklist.
- 2026-06-24 — Wi-Fi context fixes: **D-096** no-Location SSID strategies first; **D-097**
  rule-editor edge-to-edge.
- 2026-06-24 — 1.0.1 / `versionCode 4`: re-tag so the release tag contains `fastlane/`.
- 2026-06-24 — F-Droid prep: fastlane metadata added; submission + tag are owner steps.
- 2026-06-23 — v1.0.0: Tasker→Kotlin rebuild complete; Gate 3 signed off. Full history frozen
  in `../history/`.
