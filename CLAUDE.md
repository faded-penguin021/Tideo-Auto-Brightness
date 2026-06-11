# Tideo Auto Brightness — Tasker → Kotlin rebuild

You are one session in a multi-session rebuild program. The program converts the Tasker project
`Advanced_Auto_Brightness_V3.3.prj_9.xml` (repo root, 1.6 MB — **NEVER read it wholesale; use
`docs/rebuild/XML_RECIPES.md`**) into a modern Kotlin/Compose Android app with feature parity.
A previous AI conversion attempt was audited: parts are salvaged (see ledger below), the rest is
rebuilt segment by segment. Work happens on **your session's assigned `claude/*` branch** (see Git rules).

## Session protocol (follow in order, every session)

1. Run `scripts/setup-android-sdk.sh` if `local.properties` is missing (~4 min first time).
   Skip for docs-only segments (S1, S2).
2. Read `docs/rebuild/STATE.md` — segment log, deviations ledger, blockers left for you.
3. Read YOUR segment brief in `docs/rebuild/RUNBOOK.md`. Your prompt tells you which segment
   you are. Stay inside its scope and non-goals. Check its preconditions are DONE in STATE.md.
4. Do the work. Consult `docs/rebuild/PARITY_CHECKLIST.md` rows you affect.
5. Run your segment's acceptance commands until green.
6. Update `docs/rebuild/STATE.md` (append your segment-log row, update "Current state" and
   "Next up", add any deviations/discoveries) and flip your PARITY_CHECKLIST.md rows.
7. Commit with message prefix `[Sx] ` and push: `git push -u origin <your-session-branch>`.

**Never leave the branch red.** If acceptance cannot go green: revert source changes (or move
them to side branch `claude/blocked-Sx`), still commit+push the STATE.md update with
status=BLOCKED, the exact failing command, last ~30 lines of error, suspected cause, and a
recommended next action (including whether a stronger model should retry).

## Build commands

```bash
./gradlew :domain:test                  # pure-JVM engine + golden parity tests
./gradlew :platform:test               # Robolectric adapter tests (after S3)
./gradlew :app:testDebugUnitTest       # app unit + Robolectric tests
./gradlew :app:assembleDebug           # APK at app/build/outputs/apk/debug/
./gradlew :app:lintDebug               # lint vs frozen baseline (after S3)
```

Until S3 lands, NO gradle target even configures (missing pluginManagement — STATE.md D-007)
and there is no wrapper; S3 bootstraps via `/opt/gradle/bin/gradle` (8.14.3, Java 21).

## Architecture (target)

- `:domain` — pure JVM/Kotlin. ALL math/decision logic. No Android imports, ever.
  Golden-tested against a transcribed Tasker reference implementation in `domain/src/test`.
- `:platform` — Android library (converted from pure-JVM in S3). Real system adapters behind
  small interfaces: light sensor, brightness writer (OEM range normalization), secure-dimming
  writer, tiered PrivilegeManager, ContentObserver override detector, battery/wifi/location/
  foreground-app readers.
- `:app` — Compose M3 UI (~9 screens), DataStore settings (`AabSettings`), foreground service
  runtime (`specialUse` type), QS tile, boot receiver, notification with actions.
- `:data` — orphaned legacy module, retired in S3. Do not add code to it.

Privilege tiers: BASIC = user-grantable WRITE_SETTINGS → full core pipeline works.
ELEVATED = WRITE_SECURE_SETTINGS via one-time `pm grant` (adb / Shizuku / root) → super
dimming. Shizuku is used only in the grant flow, never as a runtime binder dependency.

## Facts & corrections ledger (hard-won; trust these over older docs/audits)

- Embedded Java = action **code 474** (40 blocks, census table in XML_RECIPES.md §R7).
  Code 598 is Variable Search/Replace — an earlier audit confused them.
- Java source in `arg0` is XML-entity-encoded; decode before use. Tasker substitutes `%var`
  VALUES textually into the source pre-compile → reference transcriptions parameterize them.
- **task661 "Map Lux to Brightness (Java) V2" contains NO Java** — runtime curve math is in
  its Variable Set (547) maths expressions and sub-tasks. The Java 3-zone formula in task663
  (_GenerateGraph, ~L34405) is a plot-side copy: cross-validate against it, don't port from it.
- Profile-level `<ConditionList>` blocks are pipeline logic (prof760 holds the absolute lux
  threshold gate + sensor-accuracy trust gate). Extract profiles, not just their enter tasks.
- Profiles have a `ve="2"` attribute: awk pattern `/<Profile sr="prof760"/` — no closing `>`.
- 125 distinct `%AAB_*` variables; ~37 are user-facing settings. Known schema gaps vs
  `AabSettings.kt`: `%AAB_AnimSteps`, `%AAB_MaxSteps`, `%AAB_ThreshMidpoint` missing;
  `AnimationConfig` defaults (50/5/30) conflict with AabSettings (25/65) — resolve via S1's
  defaults_audit.md, not by guessing.
- form2A / form3A are DERIVED continuity coefficients (task659 _UpdateBrightnessFormulae),
  not stored settings.
- prj_9 ≈ prj_4 (only _SuggestCurveParameters V23→V24), so docs/migration/ (Codex-era) is
  not stale — but it has zero action-level or scene detail; treat as reference only.
- Salvage KEEP list: `domain/.../brightness/BrightnessEngine.kt` (+tests),
  `app/.../settings/AabSettings.kt`, `TaskerLegacyProfileSerializer.kt`, SettingsStore/
  AppDataStores, module split, runtime scaffolding shapes. DISCARD list (deleted by S3/S9):
  toy `BrightnessPolicyEngine` + `EvaluateAndApplyBrightnessUseCase` + `Ports.kt`, everything
  in `data/`, `platform/SystemAdapters.kt` fakes, `WebViewGraphFallback`,
  `PermissionOnboardingStateMachine`.
- Known compile blocker until S3: `AmbientMonitoringService.kt:85` references
  `R.mipmap.ic_launcher` but no `res/` exists.
- Environment: no KVM → no emulator. Verification = compile + JVM/Robolectric tests; on-device
  behavior is checked by the human at Gates 1–3 (see RUNBOOK).
- S3.5 owner-review corrections (trust these; details in extraction docs + STATE.md D-020…D-026):
  ConditionList semantics = plain And/Or bind tighter (And > Or), And2/Or2 join those groups
  left-to-right; ⚠️ ConditionList children are ALPHABETICAL in the XML — re-sort numerically
  (this scrambled S1's prof758 transcription). Action 590 = Variable Split, 105 = Set Clipboard.
  %AAB_Debug = 10 named toast categories (Debug-scene selector labels), not verbosity.
  %AAB_Test = curve-wizard diagnostics report → clipboard (user-facing — surface it). prof759/
  task545 sets %AAB_Proximity near/far → damps LuxAlpha ×0.1, never pauses. prof769 panic =
  upside-down + shake. 168/276 tasks are anonymous scene handlers → see
  extraction/tasks/anonymous_handlers.md. Never read Tasker prefs (adbwp) in the rebuild.

## Coding conventions

- **Tasker semantics win over taste.** Port behavior exactly, including odd rounding
  (3-decimal `round3`, `Math.round` tie-toward-+∞, BigDecimal HALF_UP, string-formatted
  numbers). Modernize the *how* (coroutines, flows), never the *what*.
- Provenance comments on ported logic: `// Tasker: task535 "Lux Smoothing (Java)" XML L15204`.
- Golden vectors and the reference implementation are immutable test fixtures: production code
  conforms to THEM. Changing them requires proof the extraction was wrong + a STATE.md entry.
- No new dependencies unless your segment brief sanctions them.
- minSdk 31, target/compile 35. No legacy API branches below 31.
- Kotlin official code style; match existing file/package layout.

## Git rules

- Each cloud session gets its OWN `claude/<codename>` branch, named in the session directive.
  This is **by design** (D-020): the owner merges each segment to `main` via PR after stage
  completion, and new sessions start from fresh main. Develop and push ONLY on your session's
  assigned branch. Do NOT log, explain, or try to reconcile branch-name differences between
  sessions — that question is settled. Never force-push. Never push to main.
- Commit prefix `[Sx] `. Push with `git push -u origin <your-session-branch>`
  (retry up to 4× with backoff 2s/4s/8s/16s on network errors only).
- Parallel segments (S6∥S7∥S8) land on separate session branches; the owner's stage merges
  absorb STATE.md append-both-sides conflicts.
