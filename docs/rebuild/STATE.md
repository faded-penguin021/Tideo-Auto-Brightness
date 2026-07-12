# STATE — project state & session memory

> **Length guard (read before editing).** Steady-state target ≤ 12 KB. **If this file exceeds
> 32 KB, aggressively compress before committing:** collapse each completed *Active work* stage
> into one Changelog line, move any durable gotcha into `DEVIATIONS_LEDGER.md` (the permanent,
> append-only registry — never compressed), and delete narrative/punch-list prose. The
> **Project** and **Current state** sections must always survive compression. The full migration
> narrative is already frozen in `../history/` — do not re-accumulate it here.
> (`scripts/ladder.sh` machine-checks this rule: warn > 12 KB, fail > 32 KB.)

## Project

Native **Kotlin/Compose** Android app that is a feature-parity rebuild of the Tasker project
`Advanced_Auto_Brightness_V3.3`. Three modules: **`:domain`** (pure-JVM math/decision logic,
golden-tested), **`:platform`** (Android system adapters behind small interfaces), **`:app`**
(Compose M3 UI, DataStore `AabSettings`, foreground-service runtime, QS tile, boot receiver).
Privilege tiers: **BASIC** = user-grantable `WRITE_SETTINGS` (full core pipeline); **ELEVATED**
= `WRITE_SECURE_SETTINGS` via one-time `pm grant` (super dimming + Privileged Display toggles).
minSdk 31, target/compile 36.

## Current state

**Shipped: v1.7.0** (`versionCode 17`, tagged on `main`) — Privileged Display Control
(D-149–D-155).

**Code-complete, awaiting owner release cut: 1.8.0 / `versionCode 18`** — (a) **intent control
for automation frameworks** (Tasker / MacroDroid, **D-157**; user reference `docs/AUTOMATION.md`)
and (b) the **A11y (TalkBack) backlog + crash-log capture** (**D-156**, **D-158**), plus the
**D-159** IME fix and the **D-160** audit fix. `changelogs/18.txt` final (455 chars). Pre-release
audit passed 2026-07-09 (Fable): full 5-rung ladder green in-session; branch topology verified.
No active multi-unit work; all persisted plan files deleted. The 2026-07-12 final-audit pass is
fully closed: all three agent scopes audited (D-163–D-165 fixed; the deferred
settings/persistence/UI-wiring scope re-ran clean in two scoped passes — locale/backup/export
mechanics, field parsing, wizard boundaries, display-VM applyNow, time-wake edges — with the
wizard's never-non-finite reliance on the raw `SettingsViewModel.update` path now pinned by
`WizardDegenerateInputTest`).

`PARITY_CHECKLIST.md` is zero-`pending`; golden parity tests green; TODO/FIXME = 0;
`parity_gaps.md` has 0 open gaps.

**Owner actions pending (the 1.8.0 release path):**

1. **PR is deliberately deferred until the F-Droid review completes** (owner decision
   2026-07-09) — CI (build/CodeQL/release-preflight) first runs there; the local ladder is the
   equivalent build.yml task set. **ONE PR** (owner decision 2026-07-10): open it from
   `claude/fable-model-improvements-3r07qs` → `main` — it supersets the 1.8.0 branch, so
   `user-guide-accuracy-check-i5fxex` and `intent-control-u4-i2i7e1` are **deleted unmerged**
   (both fully contained, 0 unique commits). Ready-made draft (no `[skip ci]`-class tokens, D-115):
   - *Title:* `1.8.0: intent control (D-157), a11y (D-156/D-158), IME fix (D-159), RESUME gate (D-160) + repo hardening (D-161)`
   - *Body bullets:* opt-in automation surface (verbs + LOAD_PROFILE/CONTEXTS_RESUME + outbound
     STATE_CHANGED; docs/AUTOMATION.md) · TalkBack backlog A0–A7 + crash-log capture C1 · D-159
     three-part IME fix · D-160 audit fix (glue-review verdicts in the U2/U5/38c66cd commit
     bodies) · repo hardening (ladder.sh guards, STATE compression, D-161 session discipline,
     Gradle parallel/config-cache) · owner on-device checklist = DEVICE_TEST §§12–14 ·
     vc18/1.8.0, changelog 455 chars.
2. After CI green: on-device pass of `DEVICE_TEST_SCRIPT.md` **§12 (TalkBack), §13 (automation),
   §14 (insets)**; findings → "Gate findings" here.
3. Cut **v1.8.0 / vc18** from `main` via the Release UI.

How changes are made now: see `RUNBOOK.md` (change-type playbooks; the **glue-review protocol**
is mandatory for `:platform`/runtime diffs; multi-session features follow the playbook-5
persisted-plan pattern). The migration narrative is frozen in `../history/`; the deviations
registry stays live.

> Code/docs elsewhere cite deviations by number (e.g. `STATE.md D-048`, `F50`). All deviations
> — migration and ongoing — live in the permanent registry `DEVIATIONS_LEDGER.md` (200 rows per
> file, then `_A.md`/DA-…, `_B.md`/DB-… — D-153; gate findings are in
> `../history/STATE_rebuild.md`). Look there.

## Decided non-items (don't re-litigate without new evidence)

- **Repo/process (2026-06/07):** root `CHANGELOG.md` (redundant with STATE + fastlane + the
  ledger); speculative dependency-currency bumps (only on a security advisory); a standalone
  doc-drift audit (RUNBOOK self-adaptation covers it); action SHA-pinning / Gradle dependency
  verification (declined 2026-06-29 as wrong cost/benefit for a solo F-Droid app); widening
  build.yml to `claude/**` push events (declined 2026-07-10 — PR-time CI + the local ladder
  suffice, D-161).
- **External AI-review suggestions (declined 2026-07-10, reasons in D-162):** glue-review
  checkbox output; ledger active-file symlink/marker; session-start delta generator; platform
  contract tests (already exist — D-136/D-148); tracking-id branch names.
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

One line per shipped change or completed backlog (newest first). Keep terse; details live in
the cited D-rows and git history.

- 2026-07-12 — final adversarial audit pass, CLOSED (all 3 agent scopes + coordinator review;
  everything else clean), fixes fold into 1.8.0/vc18: **D-163** rule-removal clears the
  location/app signal snapshots + debounce anchor (D-142 siblings); **D-164** draft Apply snaps
  the draft to the validated copy it commits (ends the perpetual-dirty screen); **D-165** panic
  re-arm needs a sustained straight spell (shake flicker can't re-open a consumed window). The
  deferred settings/UI/domain scope re-ran clean (two scoped passes + probe); the wizard
  abort-or-finite invariant guarding the validate()-free apply path is pinned by
  `WizardDegenerateInputTest`. +6 tests, 2 updated; no goldens touched.

- 2026-07-10 — repo-tooling/CI/docs (**D-162** external-review triage): ladder guards 1b
  (STATE structure tripwire) + 1c (ledger rollover counter, 157/200); RUNBOOK recovery rule
  (Session discipline 6); release-preflight golden-fixture gate (goldens/reference change ⇒
  STATE.md entry). Declined suggestions recorded in the row + non-items.
- 2026-07-10 — **D-161 repo-hardening backlog U1–U4** (ships in the single 1.8.0 squash per
  owner): `scripts/ladder.sh` one-command ladder + machine guards; STATE.md compressed
  27.6→12 KB; RUNBOOK **Session discipline** (structure replaces the D-035 tier policy);
  Gradle parallel + config cache (session-first build 376 s; warm re-verify 5 s / 1 s).
- 2026-07-09 — no-code: 1.8.0 pre-release audit close-out — ladder green in-session, owner
  release path rewritten, branch topology verified; DEVICE_TEST §14 added (the D-159 insets
  sweep CI can't see).
- 2026-07-09 — folds into 1.8.0/vc18 (audit finding): **D-160** external `RESUME` could
  resurrect a user-disabled service — gated at `ControlReceiver.route`; AUTOMATION.md/KDoc
  corrected to per-verb truth; DEVICE_TEST §13.43.
- 2026-07-08 — folds into 1.8.0/vc18 (owner on-device bug): **D-159** draft-screen IME dead gap
  — the 3-part edge-to-edge recipe (all three parts required; the row has it).
- 2026-07-08 — docs/copy-only: user-guide S5 corrected to shipped profile behavior (all five
  built-ins editable AND deletable + factory restore); other 8 sections audited accurate.
- 2026-07-06/07 — 1.8.0/vc18 feature (a): **D-157 intent control** for automation frameworks
  (`:app`-only) — opt-in exported broadcast surface, default OFF: 7 verbs +
  `LOAD_PROFILE`/`CONTEXTS_RESUME`, outbound `event.STATE_CHANGED`, Tools card, VM-free
  `ProfileApplier`, `docs/AUTOMATION.md`, DEVICE_TEST §13. Tests +20; a pre-existing
  `AmbientMonitoringServiceTest` flake fixed.
- 2026-07-06/07 — 1.8.0/vc18 feature (b): **D-156 a11y backlog A0–A7** (per-unit
  `SemanticsAudit` gate; labeled primitives/toggleables/sliders, graph text alternatives,
  banner liveRegions, 48 dp floor, DEVICE_TEST §12) + **D-158 crash-log capture C1**
  (`filesDir/crash` 5-ring, always delegates; Tools copy row). Tests +72.
- 2026-07-07 — owner-completed (no code): H4 (D-135) Dependabot security updates + private
  vulnerability reporting; H5 (D-137) fdroiddata `Binaries:`/`reproducible: yes`.
- 2026-07-05 — docs/process: **D-153** ledger 200-row file cap + rollover (summarizing
  rejected); RUNBOOK <500-char F-Droid `whatsNew` rule (`17.txt` left as-is — vc17 already
  tagged).
- 2026-07-03..05 — **1.7.0 / vc17 (MINOR): Privileged Display Control, Segments 1–5** —
  **D-149** `:platform` `SecureDisplayController` (AOSP-universal keys; Extra Dim excluded);
  the Privileged Display screen (3-channel grant card, read-back VM); `:domain`
  `ContextMatching` extraction (goldens untouched); **D-150** display schedules built, then
  removed by the **D-151** owner pivot (toggles = PROFILE settings via the idempotent
  `DisplayTogglesCoordinator`); **D-152** profile port (7 `AabSettings` fields, one draft
  surface, `applyNow` direct-write when the service is off); **D-154** circadian Night Light
  temperature (task90 tanh, 60 s only-on-change ticker); **D-155** panic resets display toggles
  to DEFAULTS; OEM ⓘ-dialog polish; OxygenOS Kelvin quirk noted (D-048). Owner checklist
  DEVICE_TEST §11; plan file deleted at Segment 5 (playbook-5 pattern added to RUNBOOK).
- 2026-07-03 — repo-tooling only: `setup-android-sdk.sh` seeds the Gradle wrapper cache from
  `/opt` (the cloud egress proxy 403s the wrapper download).
- 2026-07-02 — **1.6.2 / vc16 (PATCH): F-backlog U1–U6 CLOSED (D-138–D-148)** — **D-139** panic
  cancel-and-joins the animation consumer; **D-140** zombie-FGS gates; **D-141** rule-edit
  cooldown bypass; **D-142** wifi `[WIFI]` gate; **D-143** stale ssidFlow resolves dropped;
  **D-144** post-death Extra-Dim residual; **D-145** ShizukuShell bind-timeout unbind;
  **D-146** NaN import guard; **D-147** widget actions off the exported provider; **D-148**
  last four glue seams (+19 tests); parity transcription spot-check clean (`XML_RECIPES.md`
  R0); `/security-review` clean.
- 2026-07-01 — **hardening backlog H1–H5 (D-133–D-137)** — RUNBOOK gains the mandatory
  **glue-review protocol** (H1); 1.6.1 / vc15 (PATCH) **D-134** saved pre-service brightness
  mode persisted across process death; **D-136** glue-seam audit + 4 suites (+14 tests);
  **D-135** `SECURITY.md` + security-only Dependabot; **D-137** release APK proven
  reproducible. `FABLE_HANDOFF.md` deleted.
- 2026-06-30 — 1.6.0 / vc14 (MINOR): **D-130** no-Location SSID path (DUMP); **D-131** full UI
  i18n (ratchet 0); **D-132** plug/unplug bypasses the battery cooldown.
- 2026-06-29 — CI-only: **D-124** `release-preflight.yml` PR gate; **D-123** `release.yml`
  reuses the F-Droid changelog as the Release "What's new"; per-job timeouts + wrapper
  properties in cache keys; stricter supply-chain measures declined with reasons.
- 2026-06-29 — 1.5.0 / vc13 (MINOR): **D-125** wizard curve suggestion is user-driven;
  **D-126** resume no longer loops back to paused (F64 settle window).
- 2026-06-28 — 1.4.0 / vc12 (MINOR): **D-117**–**D-122** (PWM graph, edge-to-edge modal,
  release-notes auto-append, fresh location, HTTPS geo-IP); 1.3.0 / vc11 (MINOR): **D-116**
  panic gesture rework; 1.2.1 / vc10 (PATCH re-cut): **D-115** skip-ci token; 1.2.0 / vc9
  (MINOR): **D-108**–**D-114**; 1.1.1 / vc8 (PATCH): **D-107** explicit PendingIntents.
- 2026-06-26 — 1.1.0 / vc7 (MINOR): targetSdk/compileSdk 36, Robolectric 4.16.1 (JDK 21),
  CodeQL, `.debug` suffix (D-106); folded **D-101**–**D-105**. Owner Pass A/B passed.
- 2026-06-24/25 — early PATCHes: 1.0.4/vc6 **D-100** nav-bar padding; 1.0.3/vc5 **D-098**
  dialog clip + **D-099** version/tag realignment (RUNBOOK §6 release checklist);
  **D-096**/**D-097** Wi-Fi context fixes; 1.0.1/vc4 re-tag with `fastlane/`; F-Droid
  metadata prep.
- 2026-06-23 — v1.0.0: Tasker→Kotlin rebuild complete; Gate 3 signed off. Full history frozen
  in `../history/`.
