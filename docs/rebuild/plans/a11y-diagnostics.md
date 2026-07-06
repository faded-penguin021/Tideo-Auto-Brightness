# Plan — Accessibility (TalkBack) backlog + local crash-log capture

**Owner-approved 2026-07-06.** RUNBOOK playbook-5 persisted plan (pattern proven by Privileged
Display, D-149–D-152). Units run **strictly sequentially** — no parallel subagents/tool fan-out —
and each unit ends SHIPPABLE: full acceptance ladder green → `STATE.md` Changelog line + Active-work
tick → commit → push. A follow-up session bases its branch on the unmerged predecessor
(`git checkout -B <new> origin/<old>`), else latest `main`. This plan is provisional (the owner may
pivot it); **the last executed unit deletes this file** — by then its durable content must live in
`STATE.md` Changelog lines + ledger rows (code cites `D-NN`, never this file).

**Why:** pre-A0 the app had ~27 semantics annotations across 16 screens of sliders, switches,
icon-only buttons, and canvas graphs — TalkBack users get unlabeled controls. On-brand: a
brightness/dimming app's users skew light-sensitive/low-vision. Designed for lesser-model
execution: small units, hard behavioral acceptance gates, per-unit file allowlists
(the D-030/D-034 lesson made structural instead of model-tier policy D-035).

## Standing guardrails (READ FIRST, apply to EVERY unit)

1. **Blast radius:** never touch `:domain`, `:platform`, golden vectors, or `:app` runtime glue
   (service/pipeline/receivers/tile/widget). Each unit lists its allowed files; stay inside.
   **C1 is the sole exception** and is flagged glue-review-MANDATORY.
2. **Semantics/strings-only:** no visual or layout change, no color/dp/shape edits
   (`design/m3_audit.md` is frozen). Exception: A7 may adjust touch-target dp via `Dimens.*`
   tokens, each change called out in the commit body.
3. **Label association must not change interaction:** put
   `Modifier.semantics { contentDescription = label }` on the control node (Slider/Switch/etc.).
   Do NOT convert rows to `toggleable`/`clickable` — that changes tap behavior AND breaks the
   existing tests that `performClick()` the control's `testTag`. **Existing testTags stay stable.**
4. **Strings:** every new label goes through `strings.xml` `a11y_*` keys + `stringResource`.
   `HardcodedStringCheckTest` (ratchet 0) counts a literal `contentDescription = "…"` as a
   violation — this is deliberate; keep it at 0.
5. **Acceptance gate (the hard check):** render the unit's surface in a Robolectric compose test
   and call `SemanticsAudit.assertAllInteractiveNodesAreLabeled()` — it walks the merged
   semantics tree (what TalkBack traverses) and names every unlabeled interactive node. Fix
   every violation it reports using the A0 patterns, then add one targeted assertion per fix
   (contentDescription/heading/merge). **Template to copy: `SettingsControlsA11yTest` (A0).**
   Screens with ViewModels: follow how `SettingsScreensTest` renders screens.
6. **Checkpoint (every unit, no exceptions):** full ladder
   (`./gradlew :domain:test :platform:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`)
   → `STATE.md` Changelog line + tick the Active-work box → commit → `git push -u origin
   <session-branch>` (retry ≤4× backoff 2s/4s/8s/16s on network errors only). A cut-short
   session loses at most one unit.
7. **Session start:** read `STATE.md`; `git fetch --tags origin` and re-check
   `app/build.gradle.kts` on `main` — A0 claimed **1.8.0 / versionCode 18**
   (`changelogs/18.txt`). If another feature (e.g. intent control) ships first and takes it,
   rebase, bump to the next free minor + code per RUNBOOK §6, and rename the changelog file.
   Later units only amend `18.txt` when they add something user-visible (keep < 500 chars).
8. **Recording:** STATE Changelog line per unit; ledger row only for durable
   conventions/decisions (append in the LIVE ledger file, D-153 rollover). The a11y
   conventions row is **D-156** — cite it from code comments.
9. If anything in this plan turns out wrong/stale, fix this file in the same commit
   (RUNBOOK self-adaptation).

## Units (each ~1–3 prod files + tests, one commit)

### A0 — settings primitives + the audit gate ✅ DONE (D-156, this plan's commit)
`SemanticsAudit.kt` (test helper: merged-tree walk, letter-or-digit label rule, heading assert),
`SettingsControls.kt` (HelpInfoButton named "Help: <label>" + glyph cleared from semantics;
Slider/Switch labeled; `SectionHeader` → `heading()`; `DerivedReadout` merged),
`a11y_help_for` string, version 1.8.0/vc18 + `changelogs/18.txt`, `SettingsControlsA11yTest`
(the worked template — written failing against the pre-fix components, green after).

### A1 — remaining shared components
- **Files:** `app/src/main/kotlin/com/tideo/autobrightness/app/ui/components/{AabCard,AabNav,
  AppShell,SettingField,TriggerEditors,DiagnosticCard,BrightnessInstrument,Toaster,AabFlashHost}.kt`,
  `strings.xml`, tests in `app/src/test/kotlin/com/tideo/autobrightness/app/ui/`.
- **Work:** render each reusable component (extend `ComponentLibraryTest` scenarios or a new
  `ComponentsA11yTest`) under the audit gate; fix what it flags (icon-only buttons, unlabeled
  toggleables). Add merge semantics to `KeyValueRow` (label+value = one announcement, mirror
  `DerivedReadout`). Check `AabFlashHost`: if the flash overlay is a composable, give it
  `liveRegion = LiveRegionMode.Polite` so confirmations are announced; if it's a window overlay
  outside the composition, document that as a residual instead of forcing it.
- **Acceptance:** audit green over every rendered component + targeted assertions; ladder.

### A2 — canvas graphs get text alternatives
- **Files:** `ui/components/GraphScaffold.kt`, the graph screens' chart composables (touch only
  the chart node's modifier), `strings.xml`, tests.
- **Work:** canvases are invisible to TalkBack (and NOT interactive, so the audit won't flag
  them — this unit's gate is targeted assertions instead). Give each graph a localized
  `contentDescription` from an `a11y_graph_*` template naming the graph and its key parameters
  (e.g. "Brightness curve graph: zone 1 to %1$d lux…"). Keep summaries one sentence; the
  numbers shown in the screen's fields need not be repeated.
- **Acceptance:** per-graph assertion that the chart node exposes the CD; ladder.

### A3 — Dashboard, Menu, Onboarding
- **Files:** `ui/screens/{DashboardScreen,MenuScreen}.kt`, `ui/onboarding/OnboardingScreen.kt`,
  `strings.xml`, tests.
- **Work:** render each screen (fake/default VM state, per `SettingsScreensTest`) under the
  audit; fix flags. Dashboard banners (`StaleBanner`/`OverrideCard`/resume) appear dynamically →
  `liveRegion = LiveRegionMode.Polite` on the banner container so state changes are announced.
- **Acceptance:** audit green per screen + banner liveRegion assertions; ladder.

### A4 — settings screens, group 1
- **Files:** `ui/screens/{CurveBrightnessScreen,ReactivityScreen,CircadianScreen,
  SuperDimmingScreen}.kt`, `strings.xml`, tests.
- **Work/acceptance:** audit-render each screen; most controls are the A0 primitives (already
  labeled) — fix only screen-local violations; heading assertions for the screen's sections.

### A5 — settings screens, group 2
- **Files:** `ui/screens/{MiscScreen,ToolsScreen,PrivilegedDisplayScreen,LiveDebugScreen}.kt`,
  `strings.xml`, tests. Same recipe as A4.

### A6 — profiles/contexts + info screens
- **Files:** `ui/screens/{ProfilesScreen,ContextsScreen,ProfilesContextsScreen,AboutScreen,
  UserGuideScreen,TaskerHelp}.kt` (+ rule-editor dialogs via `TriggerEditors` call sites),
  `strings.xml`, tests. Same recipe; rule-editor dialogs rendered and audited too.

### A7 — touch targets + owner TalkBack checklist (final a11y unit)
- **Files:** component/screen files only where a target is under-size, `Dimens.kt` if a token is
  needed, `docs/rebuild/DEVICE_TEST_SCRIPT.md`, tests.
- **Work:** compose-ui-test `assertTouchWidthIsAtLeast(48.dp)`/`assertTouchHeightIsAtLeast(48.dp)`
  over the interactive nodes of the A0–A6 test surfaces (M3 components mostly guarantee this via
  minimum-interactive-size — expect few fixes; guardrail 2's dp exception applies). Add a short
  owner TalkBack section to `DEVICE_TEST_SCRIPT.md` (§12): semantics tests approximate TalkBack;
  on-device is owner-verified (no emulator/KVM). Ledger row for anything durable found.
- If the owner opts out of C1, this unit deletes this plan file.

### C1 — local crash-log capture (OPTIONAL, last; **the only glue unit — glue-review MANDATORY**)
- **Files:** new `app/src/main/kotlin/com/tideo/autobrightness/app/AabApplication.kt`,
  `AndroidManifest.xml` (`android:name` only), `ui/screens/ToolsScreen.kt`, `strings.xml`,
  `changelogs/18.txt` (+1 line), tests.
- **Work:** `AabApplication.onCreate` installs a `Thread.setDefaultUncaughtExceptionHandler`
  that writes a timestamped stack trace to `filesDir/crash/` (keep the 5 newest, delete older),
  then **always delegates to the previous handler** (never swallow — the process must still
  die). No telemetry, no network, app-private files only. ToolsScreen gains a Diagnostics row
  "Copy latest crash log" using the existing clipboard pattern (ToolsScreen.kt:317, `%AAB_Test`
  copy) — clipboard, NOT a share intent/FileProvider; row shows "none recorded" state.
- **Acceptance:** Robolectric tests — handler writes + rotates + delegates (install a fake
  previous handler and assert it's called); ToolsScreen row copies/handles-empty. Full ladder.
  **RUNBOOK glue-review protocol on the diff**, hunting specifically: handler installed
  idempotently (process restart), delegation ordering (write THEN delegate, delegate even if
  the write throws), no per-process state assumed to survive death. Ledger row.
- This unit deletes this plan file (durable content → its ledger row + D-156).
