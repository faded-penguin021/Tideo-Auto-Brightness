# Privileged Display Control — execution plan (active)

> Adopted 2026-07-03 (owner-approved). **Delete this file at Segment 5** — by then its content
> is collapsed into `STATE.md` Changelog lines + ledger rows. Progress checklist lives in
> `STATE.md` Active work; flip segments there as they land.

## What & why

A new ELEVATED-only settings page (invisible at BASIC/NONE) exposing display toggles that
`WRITE_SECURE_SETTINGS` unlocks — owner-named minimum: **Night Light, HDR force-SDR
(dithering-sensitive users), daltonizer grayscale** — plus other safely-doable toggles, and
**scheduling** (e.g. "social apps → black & white on weekdays after 22:00").

**"Privileged" = WRITE_SECURE_SETTINGS via adb, Shizuku and/or root** — exactly the existing
`Tier.ELEVATED` (D-016: those are GRANT channels only; elevated truth = `checkPermission`;
after the grant, writes go direct via `Settings.Secure`, no binder). No Shizuku/root write
fallback — channels grant, permission writes.

**Owner constraints:** vanilla-Android/AOSP-universal only (no OEM-specific key branches —
document variance the D-048 way, don't code it); no parallel subagents (D-133); each segment
ends shippable: ladder green → STATE.md Changelog line → commit → push. RUNBOOK playbook 5
(Tasker-independent feature) + playbook 3 design-coherence callout + §6 release bump +
glue-review protocol on `:platform`/runtime diffs.

## Execution rules

- **Sequential only** (D-133). Checkpoint each segment fully before starting the next; never
  leave the branch red. git log + STATE.md must always explain where things stand.
- **Branch:** this session `claude/privileged-settings-page-pw9ne8`. A later session bases its
  own `claude/<codename>` branch on the unmerged predecessor (`git checkout -B <new>
  origin/<old>`), else latest main. Never force-push; owner squash-merges.
- **Parity guardrails:** `ContextOverrideResolver` is Tasker-ported and golden-tested — any
  refactor must be behavior-preserving with golden tests untouched and green. The brightness
  pipeline's single-coroutine drop-on-reentry model is BINDING — the new coordinator runs its
  own coroutines in the service scope, never inside the pipeline cycle.
- **i18n ratchet** (`HardcodedStringCheckTest`); design per `design/m3_audit.md`; never write
  the literal skip-ci token (D-115); verify `DEVIATIONS_LEDGER.md` tail before numbering new
  rows (last known: D-148).
- **Watch-items before adding files:** read `PipelineFileLayoutTest` + `DeadApiCheckTest`
  for enforced layout/dead-API rules; `UiShellTest`/`PlaceholderScreenAuditTest`/
  `SettingsScreensTest` iterate `AppRoute.entries`/`menuNavDestinations` and need the new
  route handled.

## Research map (done — do not redo)

- **Privilege:** `platform/.../privilege/PrivilegeManager.kt` — `Tier {NONE, BASIC, ELEVATED}`,
  `tierFlow()`. UI ELEVATED-gating precedent: `SuperDimmingScreen.kt:112,148`.
- **Secure-write pattern to copy:** `platform/.../brightness/SecureDimmingController.kt` (tier
  check → `runCatching { Settings.Secure.putInt(...) }` → `Result<Unit>`). **Extra Dim
  (`reduce_bright_colors_*`) is OWNED by `SuperDimmingCoordinator` (D-144 latch) — the new
  page must NOT expose it.**
- **Context rules (reuse):** model `app/.../settings/ContextOverrideRules.kt` (triggers: apps /
  wifi / battery / location / timeRange "HH:MM"|SUNRISE|SUNSET incl. overnight / days
  1=Sun..7=Sat); pure resolver `domain/.../context/ContextOverrideResolver.kt` (winner-takes-
  all + `nextWakeTime` "%02d.%02d"); engine `app/.../runtime/ContextEngine.kt` (cost-gated
  listeners, `millisUntilNextContextWake` self-scheduling, screen on/off hooks). Display
  scheduling needs **all-matching** semantics → separate rule list + separate small resolver;
  do NOT extend ContextRule (keeps Tasker-interop contexts.json + golden resolver untouched).
- **Rule-editor UI to reuse:** `ContextsScreen.kt` private composables `TriggerSection`
  (~L625), `TimeField` (~L658), `TimeTokenRow` (~L701), `DayPicker` (~L730), apps picker +
  `hasUsageAccess()` prompt (~L150, ~L359). Manifest already has `PACKAGE_USAGE_STATS` +
  launcher `<queries>`.
- **Wiring:** `AppModule.createRuntime()` → `RuntimeGraph`; `AmbientMonitoringService` drives
  lifecycles + screen hooks; DataStore registry `app/.../storage/AppDataStores.kt` (pattern:
  `ContextRulesSerializer`/`ContextRuleStore`). Death-safe latches: `:platform`
  SharedPreferences `commit()` (D-134 precedent).
- **Nav:** `AppRoute.kt` enum + grouped lists; `NavGraph.kt:51`; `MenuScreen.kt:124-134`.

## Settings keys (AOSP-universal, single code path)

| Toggle | Table / key | Values | Confidence |
|---|---|---|---|
| Night Light | Secure `night_display_activated` | 0/1 | high (AOSP ≥7.1) |
| Night Light temp | Secure `night_display_color_temperature` | Kelvin int (slider ~2596–4082; verify AOSP defaults at execution) | high |
| Night Light auto mode | Secure `night_display_auto_mode` | 0 manual / 1 custom / 2 twilight — read-only caveat v1 | high |
| Color correction | Secure `accessibility_display_daltonizer_enabled` + `accessibility_display_daltonizer` | enabled 0/1; mode 0=grayscale, 11/12/13 protan/deutan/tritan | high |
| Color inversion | Secure `accessibility_display_inversion_enabled` | 0/1 | high |
| Always-on display | Secure `doze_always_on` | 0/1 | high |
| Stay awake while charging | Global `STAY_ON_WHILE_PLUGGED_IN` (public API) | 0 off / 7 on; read on = value≠0 | high |
| HDR force-SDR | Global `hdr_conversion_mode` (hidden) | AOSP `HdrConversionMode` — verify exact int at execution | **experimental** |

`WRITE_SECURE_SETTINGS` covers Secure and Global writes. `putInt` on a wrong key silently
*creates* it — hence read-back display + owner on-device verification. HDR: verify the value
mapping against AOSP source at execution (WebFetch/WebSearch); if unverifiable, ship API-gated
(`sdkInt >= 34`, injectable), labeled Experimental, default off, owner-verify note in STATE.
**Rejected as OEM-fragmented:** refresh-rate forcing, any Samsung/OEM alternate keys.

## Segments

### Segment 0 — persist this plan (docs-only) ✔ this commit

### Segment 1 — `:platform` SecureDisplayController (+ version bump)
1. New `platform/.../display/SecureDisplayController.kt` mirroring `SecureDimmingController`:
   interface + `AndroidSecureDisplayController(context, privilegeManager, sdkInt =
   Build.VERSION.SDK_INT)`. Per feature: `read…()` (incl. unset via `getString` null) and
   `set…(value): Result<Unit>` (tier < ELEVATED → failure; `runCatching` write). Features per
   the key table; daltonizer as mode enum Off/Grayscale/Protan/Deutan/Tritan.
2. Robolectric tests: tier-gate (BASIC → failure, no write), write→read round-trips,
   daltonizer truth-table, unset detection, HDR gate via injected `sdkInt`.
3. **Version bump now** (first app-code segment): `versionName "1.7.0"`, `versionCode 17`
   (MINOR; verify latest tag + pending 1.6.2/vc16 per RUNBOOK §6 first) +
   `changelogs/17.txt` (refine wording later).
4. Glue-review pass; full ladder; STATE Changelog line + ledger row (D-149+: feature adopted —
   AOSP-universal keys, OEM variance documented not branched; Extra Dim excluded). Commit, push.

### Segment 2 — Privileged Display screen (manual toggles) ← core ask ships here
1. `AppRoute.PrivilegedDisplay("privileged_display", …)`; register in NavGraph. Menu row only
   at `Tier.ELEVATED` (own "Privileged" group card, lock icon); route always registered; the
   screen self-guards: non-ELEVATED → AabCard offering ALL THREE grant channels via existing
   PrivilegeManager affordances (copyable `adbGrantInstruction()`, Shizuku one-tap
   `requestShizukuGrant` + `shizukuAvailability()` tri-state, root `tryGrantViaRoot`) —
   mirror Onboarding's grant UI. Menu row + screen react to `tierFlow()` (not one-shot).
2. `DisplayTogglesViewModel`: read-back state (IO dispatcher) + tierFlow; refresh on resume +
   after writes; surface write failures.
3. UI sections (AabCard, m3_audit-conformant, strings.xml): Night Light (switch + Kelvin
   slider + auto-mode caveat), Color (daltonizer mode selector, inversion), Screen (AOD,
   stay-awake-charging), Experimental (HDR, hidden when unavailable), Info card (AOSP keys,
   OEM variance caveat).
4. Tests: screen at ELEVATED (toggles) + BASIC (guard card); Menu tier visibility; update
   route-audit tests. Update `screen_map.md`; refine `17.txt`. Ladder + glue-review. STATE
   line. Commit, push.

### Segment 3 — `:domain` display-rule resolver
1. Extract shared trigger matching from `ContextOverrideResolver` into internal
   `ContextMatching.kt` (time/day window incl. overnight prev-day rule, `resolveTimeToken`,
   `nextWakeTime`). Behavior-preserving: golden tests untouched and green = proof.
2. New `DisplayRules.kt`: `DisplayRuleSpec(id, name, enabled, action, triggers…)` (model all
   six trigger dims; UI exposes apps/time/days first). `DisplayAction`: GRAYSCALE,
   NIGHT_LIGHT, INVERSION ("turn X on while matching"). `DisplayRulesResolver.resolve` —
   all matching enabled rules apply, per-action OR; output per action Boolean? (null = no
   opinion → restore); `nextBoundary` via shared `nextWakeTime`.
3. Truth-table tests incl. days=[Mon..Fri] 22:00–06:00 app-scoped → matches Sat 01:00
   (Friday's overnight tail), not Sun 23:00; multi-rule OR; disabled inert. Ladder. STATE
   line. Commit, push.

### Segment 4 — scheduling runtime + storage + rules UI
1. Storage: `DisplayRuleSet` + `DisplayRulesSerializer` + `Context.displayRulesDataStore`
   ("aab_display_rules.json") + `DisplayRulesStore` (clone ContextRuleStore pattern).
   Deliberately outside `AabSettings` (no migration/import-export coupling — future work).
2. `DisplayRulesCoordinator` (app/runtime) in `AmbientMonitoringService` scope (wired in
   `AppModule.createRuntime` → `RuntimeGraph`), never inside the pipeline coroutine:
   - Own listeners, ContextEngine cost-gate pattern: app poll ONLY while ≥1 enabled rule uses
     apps and screen on (reuse `AndroidContextSignalSource.foregroundAppFlow`; a second 2.5 s
     poll may coexist with the context engine's — accepted v1 cost); time boundaries via
     shared `millisUntilNextContextWake`; re-evaluate on screen-on + rule edits.
   - **Edge-triggered apply/restore:** on per-action desired-state TRANSITION only: engage →
     persist pre-state (SharedPreferences `commit()` latch, D-134/D-144 pattern) + write;
     release → restore pre-state + clear latch. Manual changes between edges stick (document
     in UI). Startup residual sweep (latch with no active rule → restore). Service stop →
     restore engaged. Inert below ELEVATED.
3. UI: "Schedules" section on the Privileged Display screen: rule list + editor modal.
   Refactor ContextsScreen's private trigger composables into shared `ui/components/`
   (ContextsScreen behavior identical); editor = action picker + days + start/end + optional
   apps (+ usage-access prompt reuse).
4. Tests: coordinator (transitions, restore, residual sweep, tier gate, edge-triggered manual
   survival, overnight boundary), store round-trip, screens, ContextsScreen still green.
5. Glue-review (mandatory); finalize `17.txt`; ledger row for apply/restore semantics; full
   ladder; STATE line. Commit, push.

### Segment 5 — polish + handoff
README blurb; `screen_map.md` final; STATE collapse + **owner on-device verification
checklist** (each toggle incl. HDR read-back verify; one schedule rule end-to-end incl.
overnight + app-scoped; restore on rule end + service stop); delete this plan file; record
not-planned (QS tile/notification action for grayscale, display rules in import/export,
Extra-Dim manual toggle, refresh-rate forcing); full ladder; RUNBOOK self-adaptation check.
Commit, push.

## Verification

Full ladder at Segments 1, 2, 4, 5 (`:domain:test`, `:platform:test`, `:app:testDebugUnitTest`,
`:app:assembleDebug`, `:app:lintDebug`); glue-review on 1, 2 (glue part), 4 — note the pass
result in each commit body. No emulator: on-device behavior owner-verified via the Segment 5
checklist; a debug APK may be built into `dist/` (gitignored, D-112 — never commit) and sent
via the file tool.
