# Tideo Auto Brightness — maintenance guide

A native **Kotlin/Compose** Android app that is a feature-parity rebuild of the Tasker project
`Advanced_Auto_Brightness_V3.3`. The rebuild is **complete and shipped (v1.0.0)**; work now is
*maintenance* — changing profiles/tasks/scenes, fixing bugs, occasionally adding features.

The original Tasker XML lives in `docs/rebuild/extraction/_source/` (gitignored, 1.6 MB —
**never read it wholesale; use `docs/rebuild/XML_RECIPES.md`**). The migration narrative
(segment briefs, gate findings) is frozen in `docs/history/`. The numbered deviations live in
`docs/rebuild/DEVIATIONS_LEDGER.md` — a **permanent, append-only registry** (code cites bare
`D-NN`; never compress or delete entries; append new ones as D-096+).

> **Ground truth:** code + golden test vectors. The docs under `docs/rebuild/` and
> `docs/history/` describe the app as-built and may drift — when a doc conflicts with the code,
> trust the code and correct the doc.

## Maintenance protocol (every session)

1. Run `scripts/setup-android-sdk.sh` if `local.properties` is missing (~4 min first time).
2. Read `docs/rebuild/STATE.md` — current project state and any active/staged work.
3. Open the matching change-type playbook in `docs/rebuild/RUNBOOK.md`; read the reference docs
   it names before touching code.
4. Do the work. Consult/flip the rows you affect in `docs/rebuild/PARITY_CHECKLIST.md`.
5. Run the acceptance ladder (below) until green. **Never leave the branch red.**
6. Update `docs/rebuild/STATE.md` (honor its length guard) and, if the runbook itself was
   insufficient, fix the runbook in the same change.
7. Commit and push: `git push -u origin <your-session-branch>`.

## Build commands

```bash
./gradlew :domain:test            # pure-JVM engine + golden parity tests
./gradlew :platform:test          # Robolectric adapter tests
./gradlew :app:testDebugUnitTest  # app unit + Robolectric tests
./gradlew :app:assembleDebug      # APK at app/build/outputs/apk/debug/
./gradlew :app:lintDebug          # lint vs frozen baseline
```

No KVM → no emulator. Verification = compile + JVM/Robolectric tests; on-device behavior is
owner-verified.

## Architecture

- `:domain` — pure JVM/Kotlin. ALL math/decision logic. No Android imports, ever. Golden-tested
  against transcribed Tasker reference implementations in `domain/src/test`.
- `:platform` — Android library. Real system adapters behind small interfaces: light sensor,
  brightness writer (OEM range normalization), secure-dimming writer, tiered PrivilegeManager,
  ContentObserver override detector, battery/wifi/location/foreground-app readers.
- `:app` — Compose M3 UI (~9 screens), DataStore settings (`AabSettings`), foreground service
  runtime (`specialUse` type), QS tile, boot receiver, notification with actions.

Privilege tiers: **BASIC** = user-grantable `WRITE_SETTINGS` → full core pipeline. **ELEVATED**
= `WRITE_SECURE_SETTINGS` via one-time `pm grant` (adb / Shizuku / root) → super dimming. After
the grant the dimming path writes `Settings.Secure` directly (no binder). Shizuku is a genuine
**optional runtime** dependency in exactly one place: the no-Location Wi-Fi SSID strategy
(`ShizukuWifiSsidStrategy` → `cmd wifi status` via `ShizukuShell`) — not "grant-only".

## Coding conventions

- **Tasker semantics win over taste.** Port behavior exactly, including odd rounding (3-decimal
  `round3`, `Math.round` tie-toward-+∞, BigDecimal HALF_UP, string-formatted numbers). Modernize
  the *how* (coroutines, flows), never the *what*.
- Provenance comments on ported logic: `// Tasker: task535 "Lux Smoothing (Java)" XML L15204`.
- Golden vectors and the reference implementations are immutable test fixtures: production code
  conforms to THEM. Changing one requires proof the extraction was wrong + a `STATE.md` entry.
- No new dependencies unless the change clearly warrants it.
- minSdk 31, target/compile 35. No legacy API branches below 31.
- Kotlin official code style; match existing file/package layout.

## Invariants that still bind (full catalog: `docs/rebuild/DEVIATIONS_LEDGER.md`)

- **Concurrency model is BINDING:** a single pipeline coroutine; one event runs to completion
  (including animation); events arriving mid-cycle are **DROPPED, not queued** (the Tasker
  re-entry mutex, `%AAB_MainLoop`).
- **Profile gates** are hardcoded Kotlin booleans with provenance + a truth-table test — there
  is no generic ConditionList evaluator. ConditionList semantics: plain And/Or bind tighter
  (And > Or); And2/Or2 join those groups left-to-right; ⚠️ XML children are **alphabetical** —
  re-sort numerically.
- **Curve math source of truth:** `task661` holds NO Java — its math is in Variable Set (code
  547) maths expressions/sub-tasks; `task663`'s Java 3-zone formula is a plot-side copy for
  **cross-validation only**. Disagreements → `parity_gaps.md`, never guess.
- `%AAB_Proximity` (prof759/task545) damps `LuxAlpha ×0.1`, never pauses. `%AAB_Test` =
  curve-wizard diagnostics → clipboard (user-facing, surface it). `%AAB_Debug` = 10 named toast
  categories, not a verbosity level.
- Action codes: 474 = embedded Java, 547 = Variable Set, 590 = Variable Split, 105 = Set
  Clipboard. **Never read Tasker prefs (adbwp) in the app.**

## Git rules

- Develop and push **only** on your session's assigned `claude/<codename>` branch (named in the
  session directive). Push with `git push -u origin <your-session-branch>` (retry up to 4× with
  backoff 2s/4s/8s/16s on network errors only). **Never force-push. Never push to `main`.**
- The owner merges session branches to `main` via PR. Do not open a PR unless asked.
