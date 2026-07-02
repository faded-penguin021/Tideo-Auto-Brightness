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
= `WRITE_SECURE_SETTINGS` via one-time `pm grant` (super dimming). minSdk 31, target/compile 35.

## Current state

**Shipped: v1.6.0** (`versionCode 14`). `PARITY_CHECKLIST.md` is zero-`pending`; golden parity
tests green; TODO/FIXME = 0; `parity_gaps.md` has 0 open gaps (all 7 closed at S5). Full
acceptance ladder re-verified green 2026-07-01.

How changes are made now: see `RUNBOOK.md` (change-type playbooks + the **glue-review
protocol**, mandatory for `:platform`/runtime diffs). The migration narrative (segment briefs,
gate findings) is frozen in `../history/`; the deviations registry stays live.

> Code/docs elsewhere cite deviations by number (e.g. `STATE.md D-048`, `F50`). All deviations
> — migration and ongoing — live in the permanent registry `DEVIATIONS_LEDGER.md` (gate
> findings are in `../history/STATE_rebuild.md`). Look there.

## Active work — short-term Fable-dependent hardening (F-backlog, adopted D-138)

Complement of the D-133 backlog: the audits that **require** a capable model, executed now
while Fable access lasts (capped 50 %/week, ends ~2026-07-07); every finding converted into
durable artifacts (failing-test-first fix + D-NN row) so nothing depends on Fable afterward.
Execution: strictly sequential (no parallel subagents, D-133); each unit fully checkpointed
(ladder green + Changelog line + commit + push) before the next. v1.6.1 is tagged → the first
unit shipping an app-code fix bumps **1.6.2 / versionCode 16** (+ `changelogs/16.txt`); later
units fold in. Per-unit protocol: read targets in full + that area's ledger/extraction docs;
adversarial pass per the RUNBOOK glue-review bug classes **plus** two lenses (single-pipeline
drop-not-queue concurrency at every entry point; cross-component contract drift —
units/ranges/sentinels/ordering); finding → failing test → minimal fix; a too-big finding →
a precise backlog row here (file:line + suggested fix) for a weaker executor. New proven bug
class → append to the RUNBOOK glue-review list. Mark each unit DONE + date + "clean" or its
D-NN list as it checkpoints.

- **U1 — pipeline core + brightness writes** (DONE 2026-07-02 → **D-139, D-140**, ships as 1.6.2):
  full adversarial pass over `BrightnessPipelineController`, `PipelineCycleRunner`, `PanicHandler`,
  `PipelineState`, `AnimationRunner`, `OverrideMonitor`, `ThrottleController`, `ProfileGates`,
  `PipelineDebugEmitter`, `LiveRuntimeState`, `AmbientMonitoringService`, `AutoBrightnessRuntime`,
  `AppModule`/`AppProcessScope`; `:platform` `ScreenBrightnessController`, `SecureDimmingController`,
  `BrightnessObserver`, `LightSensorSource`. Findings fixed: D-139 (panic write raced by an
  in-flight animation frame — cancel-and-JOIN), D-140 (control intents to a not-running service
  birthed a zombie FGS — serviceOn gate + sticky-restart enablement verify). Cleared as
  NON-issues after checking the Tasker extraction: hibernate clearing `lastRawLux` (task618 polls
  a FRESH sample on wake — the event-driven equivalent is the first gated tick, so no stale-lux
  initial write is wanted); the un-mirrored task585 act13 throttle reset (unobservable —
  `lastAcceptedMs` is cleared, cycle 1 recomputes; `reinit()` doc corrected). RUNBOOK glue-review
  list gains both new proven bug classes.
- **U2 — context engine + readers** (DONE 2026-07-02 → **D-141, D-142, D-143**, fold into 1.6.2):
  full pass over `ContextEngine`, `AndroidContextSignalSource`, `CircadianWindowProvider`,
  `BatteryStateReader`, `ForegroundAppMonitor`, `LocationReader`, `WifiInfoReader` +
  `WifiSsidStrategies`, `PowerMeter`, `GeoIpLocationClient`. Findings fixed: D-141 (rule
  add/edit/delete ≤500 ms after any eval was vetoed by the GENERAL PASS-1 cooldown — rules-changed
  eval now RESUME), D-142 (wifi listener gains the missing `[WIFI]` cost gate + stale-snapshot
  clear on stop; the flagged "does ssidFlow poll?" question answered: event-driven but ungated,
  shell strategies re-ran per capabilities callback), D-143 (in-flight SSID resolve racing
  onLost/a faster resolve could publish stale state and stick — live-network guards; new
  `WifiInfoReaderTest` covers the ssidFlow callback seam, closing part of that H3 row). Cleared
  as NON-issues vs the extraction: `plugged` derived from STATUS charging/full matches task43's
  own Java (L146-147); battery acquisition stays ungated deliberately (sticky receiver ≈ free,
  feeds D-132); the onLost-during-roam transient null is parity-consistent (prof768 evaluates on
  both edges). RUNBOOK glue-review list +2 proven bug classes (asymmetric sibling gates; stale
  async completion).
- **U3 — entry points + privilege** (DONE 2026-07-02 → **D-144, D-145**, fold into 1.6.2): full
  pass over `BrightnessTileService`, `BootCompletedReceiver` (+ the `goAsync` ext),
  `DashboardWidgetProvider`, `SuperDimmingCoordinator`, `PrivilegeManager`,
  `ShizukuGrantGateway`/`ShizukuShell`/`ShizukuUserService`, `AndroidPanicSensorSource` +
  `PanicGestureDetector`/`PanicGate`. Findings fixed: D-144 (per-process dimming `engaged` latch
  vs Tasker's persisted `%AAB_DimmingStatus` — post-death restart left Extra Dim stuck on; latch
  now tri-state with UNKNOWN at process start), D-145 (ShizukuShell bind-timeout leak —
  `invokeOnCancellation` unbind; binder-untestable, argued + owner-verifiable). Accepted
  residual recorded in D-145: `ShizukuGrantGateway.requestGrant` has no bind timeout (hung
  Shizuku → no `onResult`; rare, user-retriable). Notification-action senders were already
  covered by U1 (D-140 service-side gates). RUNBOOK D-034 c bug class gains the D-144 example.
- **U4 — security review of parsing + privileged surfaces** (pending): `/security-review` +
  manual pass on `ProfileImportExportManager`/`TaskerLegacyProfileSerializer`/
  `LegacyConfigImporter` (adds the H3 import-export round-trip tests), dumpsys-wifi regex,
  `ShizukuShell` command construction, GeoIp response parsing, exported-component intents,
  secure-settings write path. By-design findings → `SECURITY.md` scope note, not a "fix".
- **U5 — parity transcription spot-check** (pending): re-derive from the XML (via
  `XML_RECIPES.md` ONLY) task661-vs-663 curve math, task535 rounding chain, profile-gate truth
  tables vs ConditionList (alphabetical-children trap). Disagreement → `parity_gaps.md` row,
  never a silent fixture edit.
- **U6 — stretch: remaining H3 seams** (pending): least Fable-dependent, deliberately last; if
  unreached they simply stay on the H3 row below.

## Active work — post-v1.6.0 hardening backlog (adopted D-133)

From the 2026-07-01 Fable review (which replaced and deleted `FABLE_HANDOFF.md`). Framing:
prefer hardening that is **machine-enforced or once-done-done** over anything that relies on a
capable model executing later — tests and CI gates keep working when the executor is a weaker
model, the owner alone, or nobody. Execution rules: one unit ≈ one session; checkpoint each
unit fully (ladder green + Changelog line + commit + push) before starting the next; **no
parallel subagents** (rate-limit burn, see D-133). Priority order:

- **H3 — glue-seam test audit** (audit + first slice DONE 2026-07-01, D-136; remaining seams
  below). Audit result: coverage is broader than filenames suggest — `BrightnessPipelineControllerTest`
  (17 integration tests) exercises `PipelineCycleRunner`/`PanicHandler`/`PipelineState`/
  `PipelineDebugEmitter` through the real controller; tile/boot/widget/observer/dimming all have
  suites. Real gaps found and closed: `ForegroundAppMonitor` (D-034 f retention — regression
  test via a new clock ctor seam), `BatteryStateReader` (intent→state mapping, scale guard),
  `AutoBrightnessRuntime` (service-action intent dispatch the notification/tile/UI funnel
  through), `ServiceHealthStore` (degraded latch clears on apply). **Remaining seams, in value
  order** (each a small follow-up): `LocationReader.activeFix` (D-120/122, shadow
  LocationManager), `AndroidPanicSensorSource` arming (sensor shadows), `PowerMeter` property
  mapping, `ExperimentPrefsStore` + `ProfileImportExportManager` round-trips. (`WifiInfoReader`
  callback path: covered 2026-07-02 by `WifiInfoReaderTest`, D-143.) **Skipped with reason:** `MaintenanceWorker`
  (6 lines; testing needs the androidx.work-testing artifact — new dep not warranted),
  `Shizuku*` (binder-dependent, not Robolectric-testable; owner device-verified),
  `ControllerHookHolder`/`ProximityTracker`/`AppProcessScope` (trivial; behavior asserted via
  controller suite).
- **H5 — F-Droid reproducible-build investigation** (DONE 2026-07-01, D-137). **Findings: the
  build is reproducible.** Two clean-room `assembleRelease` builds are **byte-identical
  (same SHA-256)** after the one standard fix (now applied): `dependenciesInfo { includeInApk =
  false; includeInBundle = false }` — AGP otherwise embeds a Play-encrypted metadata blob in the
  APK signing block. Everything else was already right: all 47 dependency versions pinned (no
  dynamic/SNAPSHOT), AGP/Kotlin pinned, no minification (deterministic plain dex), no NDK,
  version literals not git-derived, and signing is env-driven so a keystore-less build is
  unsigned — exactly what F-Droid's verify-then-copy-signature flow (apksigcopier) consumes.
  **Owner recommendation:** in the fdroiddata metadata set `Binaries:` to the GitHub release APK
  URL pattern and add `reproducible: yes` to the build recipe (pin the same JDK 21 the CI uses);
  F-Droid will then build, verify byte-equality, and publish YOUR signed APK — one artifact
  everywhere, F-Droid-verified. Same-machine determinism is proven here; the cross-machine check
  happens on F-Droid's verification server on first submission.
- **Non-items (decided — don't re-litigate without new evidence):** root `CHANGELOG.md`
  (redundant with STATE + fastlane + ledger), speculative dependency-currency bumps (only on a
  security advisory), a standalone doc-drift audit (RUNBOOK self-adaptation covers it
  opportunistically), action SHA-pinning / Gradle dependency verification (declined 2026-06-29
  with reasons).

Done 2026-07-01: **H1 — RUNBOOK glue-review protocol** — a mandatory adversarial second diff
pass for any `:platform`/runtime change, hunting the proven D-030/D-034 bug classes (gate
polarity/operands, insertion order, observer-echo races, truncation drift, non-idempotent
lifecycle / per-process state, startup sentinels). See RUNBOOK; adoption recorded as D-133.
Done 2026-07-01: **H2** — shipped as 1.6.1 (D-134, see Changelog).
Done 2026-07-01: **H4** — SECURITY.md + security-only Dependabot (D-135, see Changelog).
⚠️ **Owner action for H4:** repo Settings → Code security → enable "Dependabot security
updates" + "Private vulnerability reporting" (the committed files are inert without them).

## Changelog

One line per shipped change (newest first). Keep terse; details live in the ledger.

- 2026-07-02 — folds into pending 1.6.2 (F-backlog U3 complete): **D-144** a process death while
  Extra Dim was engaged no longer leaves it stuck on after the sticky restart (dimming latch
  tri-state, UNKNOWN at process start → first disengage clears the residual); **D-145**
  `ShizukuShell` unbinds on bind timeout (leak; binder-untestable, argued). Tests +2, 4
  assertions modernized. Glue-review pass: clean.
- 2026-07-02 — folds into pending 1.6.2 (F-backlog U2 complete): **D-142** the wifi SSID
  listener is now `[WIFI]`-gated like Tasker's prof768 (no shell-strategy SSID probing — or su
  prompts — without a wifi rule) and clears its snapshot on stop so a re-added rule can't match
  a stale SSID; **D-143** `ssidFlow()` drops in-flight resolves that outlived their network
  state (stale SSID after disconnect / stale null over a confirmed SSID could stick). Tests +5
  (incl. new `WifiInfoReaderTest`, first ssidFlow-callback coverage). RUNBOOK glue-review list
  +2 proven bug classes. Glue-review pass: clean.
- 2026-07-02 — folds into pending 1.6.2 (F-backlog U2, first slice): **D-141** a context rule
  add/edit/delete within 500 ms of any evaluation now applies immediately (rules-changed eval
  runs as RESUME, bypassing the GENERAL PASS-1 cooldown veto). Test +1. Glue-review pass: clean.
- 2026-07-02 — 1.6.2 / `versionCode 16` (PATCH — bug fixes, F-backlog U1): **D-139** panic
  restore can no longer be trampled by an in-flight animation frame (`emergencyStop` now
  cancel-and-joins the consumer before writing 255); **D-140** pause/reapply intents landing on a
  not-running service stop it instead of birthing a zombie FGS (widget Reset while disabled
  started the light-sensor collector), + sticky-restart enablement verify. Tests +4; RUNBOOK
  glue-review list +2 proven bug classes. Changelog `16.txt`. Glue-review pass: clean.
- 2026-07-02 — docs-only: **D-138** short-term Fable-dependent hardening adopted (F-backlog
  U1–U6 above) — retroactive adversarial review of the shipped runtime/platform glue + security
  and transcription audits, unit-checkpointed while Fable access lasts.
- 2026-07-01 — build-config only (folds into pending 1.6.1, backlog H5): **D-137** release APK
  proven reproducible (two clean builds byte-identical) after disabling AGP's Play-encrypted
  `dependenciesInfo` blob; owner: submit fdroiddata with `reproducible: yes` + `Binaries:` (see
  H5 row above).
- 2026-07-01 — tests + a test-seam (folds into pending 1.6.1, backlog H3): **D-136** glue-seam
  audit — 4 gap-closing suites (`ForegroundAppMonitorTest` incl. the D-034 (f) retention
  regression via a new `clock` ctor seam, `BatteryStateReaderTest`, `AutoBrightnessRuntimeTest`,
  `ServiceHealthStoreTest`, +14 tests); audit table + remaining seams in the H3 row above.
- 2026-07-01 — repo-policy only (no app change, backlog H4): **D-135** root `SECURITY.md`
  (latest-release support, private vulnerability reporting, by-design scope notes) +
  `.github/dependabot.yml` security-only (`open-pull-requests-limit: 0` for gradle +
  github-actions). Consistent with the 2026-06-29 decline (alerting ≠ bump ceremony). Needs the
  owner-side Code-security toggles to take effect (see Active work ⚠️).
- 2026-07-01 — 1.6.1 / `versionCode 15` (PATCH — bug fix, backlog H2): **D-134** the saved
  pre-service brightness mode is persisted (`:platform` SharedPreferences, `commit()`), closing
  the D-034(c) residual — after a process death mid-manual, a restarted service no longer
  re-saves its own MANUAL residue as "the user's mode", so panic/restore hands back the user's
  real (e.g. AUTOMATIC) mode. Disambiguation: current mode MANUAL → an already-persisted value
  wins; non-MANUAL → overwrites stale. Tests: `ScreenBrightnessControllerTest` +3 (`*_D134`).
  Changelog `15.txt`. First application of the RUNBOOK glue-review pass: clean.
- 2026-07-01 — docs-only: **D-133** post-v1.6.0 hardening adopted — RUNBOOK gains the mandatory
  glue-review protocol (H1); hardening backlog H2–H5 recorded above; `FABLE_HANDOFF.md` deleted
  (its ask fulfilled); STATE compressed to the length-guard target.
- 2026-06-30 — 1.6.0 / `versionCode 14` (MINOR): **D-130** no-Location SSID path wired up —
  `android.permission.DUMP` now declared (user-grantable over ADB, reverses F89), strategy order
  Shizuku → root `cmd wifi status` → DUMP `dumpsys wifi` (Tasker two-step regex) → Location;
  SSID-help dialog with copyable ADB grant. **D-131** full UI i18n (~250 strings →
  `strings.xml`; `HardcodedStringCheckTest` ratchet now 0; English-only Language selector;
  human-only translations policy in CONTRIBUTING/README). **D-132** plug/unplug bypasses the
  PASS-1 battery cooldown so a charging context switches immediately. Owner on-device pass
  confirmed; owner squash-merges + publishes v1.6.0.
- 2026-06-29 — CI-only: per-job `timeout-minutes` + wrapper properties in Gradle cache keys;
  stricter supply-chain measures (SHA-pinning, dependency verification, versionCode Gradle task)
  deliberately declined as wrong cost/benefit for a solo F-Droid app.
- 2026-06-29 — 1.5.0 / `versionCode 13` (MINOR): **D-125** curve suggestion is now user-driven
  (wizard "Preview graph" seeds the editable draft via `CurveSuggestionPreview` +
  `DraftSettingsViewModel.seedDraft`; suggested line disappears on close). **D-126** resume no
  longer loops back to paused — the F64 settle window also suppresses in-cycle override
  detection. Changelog `13.txt`; engine/goldens untouched.
- 2026-06-29 — CI-only: **D-124** `release-preflight.yml` PR gate (versionCode > latest tag,
  semver versionName, non-empty changelog — only when the PR ships app code; `[skip ci]`-token
  scan on every PR).
- 2026-06-29 — CI-only: **D-123** `release.yml` auto-reuses the F-Droid changelog as the GitHub
  Release "What's new" (idempotent marker; missing changelog → warn + skip).
- 2026-06-28 — 1.4.0 / `versionCode 12` (MINOR): **D-117** graph "Now"/"Live brightness" show
  PERCEIVED brightness in PWM mode; **D-118** Contexts rules modal edge-to-edge (no nav-bar
  clip); **D-119** release notes auto-append; **D-120/D-122** "Use current location" actively
  acquires a fresh fix; **D-121** geo-IP fallback moved to HTTPS ipwho.is + cleartext pinned
  OFF. Owner squash-merges + publishes v1.4.0.
- 2026-06-28 — 1.3.0 / `versionCode 11` (MINOR): **D-116** Panic (Reset) gesture rework —
  upside-down ∧ display-on ∧ proximity-not-near + 10 s leaky-bucket shake gate (`PanicShakeGate`,
  contract-tested vs task528 Java); new `%AAB_PanicSensitivity` (0–10) slider on Live Debug.
  Retires the S14 grab-to-wake false-fire.
- 2026-06-28 — 1.2.1 / `versionCode 10` (PATCH re-cut, no app change): **D-115** a stray
  `[skip ci]` in the squash body skipped v1.2.0's release workflow; `release.yml` now triggers
  on `release: published` + `workflow_dispatch` fallback.
- 2026-06-28 — 1.2.0 / `versionCode 9` (MINOR): **D-108**–**D-114** — battery `-1` sentinel (no
  saver flash at start), perceived-brightness read-out in PWM mode, circadian stale-location
  fallback + staleness hints, resume banner + Tasker-style Profiles & Contexts IA, Actions
  node24, rule/profile delete-overwrite confirmations, priority 1–100. RUNBOOK gains the
  m3_audit design-coherence callout.
- 2026-06-28 — 1.1.1 / `versionCode 8` (PATCH): **D-107** notification/widget PendingIntents
  made un-missably explicit (clears CodeQL `implicit-pendingintents` High).
- 2026-06-26 — 1.1.0 / `versionCode 7` (MINOR): targetSdk 35→36 + compileSdk 36 (zero required
  code changes), Robolectric 4.16.1 (JDK 21, CI JDK 17→21), CodeQL workflow added, debug variant
  gets `.debug` suffix so it coexists with the release (D-106). Folded fixes **D-101**–**D-105**
  (dimming threshold 0..255, wizard auto-copy + button wrap, circadian location persisted, chart
  declutter, geo-IP opt-in). Owner Pass A/B on-device: passed.
- 2026-06-25 — 1.0.4 / `versionCode 6` (PATCH): **D-100** bottom controls (DraftApplyBar, Menu
  last row) get `navigationBarsPadding()` under 3-key nav.
- 2026-06-24 — 1.0.3 / `versionCode 5` (PATCH): **D-098** rule-editor Save/Cancel moved into the
  editor scroll (Compose `Dialog` never delivers a bottom inset); **D-099** in-app version
  realigned with the tag drift + RUNBOOK §6 release checklist added.
- 2026-06-24 — Wi-Fi context fixes: **D-096** `ssidFlow()` runs the no-Location strategies first
  (rules match with Location OFF); **D-097** rule-editor `Dialog` made edge-to-edge.
- 2026-06-24 — 1.0.1 / `versionCode 4`: packaging-only re-tag so a release tag contains
  `fastlane/` (F-Droid reads metadata from the built commit).
- 2026-06-24 — F-Droid prep: `fastlane/metadata/android/en-US/` added (title, descriptions,
  changelog, 4 screenshots). Submission to fdroiddata + release tag are owner steps.
- 2026-06-23 — v1.0.0: Tasker→Kotlin rebuild complete; Gate 3 signed off. Full history frozen
  in `../history/`.
