# S13a — Material 3 design-system audit & redesign plan

**Segment:** S13a (design-system foundation). **Scope:** establish the reusable token primitives
(`ui/theme/Dimens.kt`, `Shape.kt`, `Type.kt`) and a written per-screen redesign plan that S13b
(component library) and S13c (restyle) execute against. **No logic, no recolor, no new deps.**

This document is the *plan*; the tokens are the *foundation*. S13b builds the components the plan
names; S13c applies them screen-by-screen and checks off the rows here.

---

## 1. Binding constraints (from the S13 brief — carried into every later sub-segment)

- **Color is fixed.** The teal+gold `AabLightColorScheme`/`AabDarkColorScheme` (S12.5a, `Color.kt`)
  stay. The restyle is **layout / spacing / elevation / typography / motion**, never recoloring.
- **No new dependencies** — Compose M3 + foundation only.
- **Hard fence (do not modify):** `ChartCanvas.kt`, `domain/`, golden vectors, `runtime/`, gradle,
  manifest, `SettingsValidator`. Charts (S13d) build *on top of* `ChartCanvas`.
- **i18n:** new/affected UI strings go through `stringResource` (`HardcodedStringCheckTest` ratchet,
  ceiling 92). S13a adds **no** UI strings (tokens are `dp`/`sp`/shape values + a markdown doc).
- **Behaviour-preserving:** all S13a token values equal the literals they replace, so wiring them in
  is visually a no-op and existing screen tests stay green. Emphasis/spacing *changes* are S13c.

---

## 2. Token foundation delivered by S13a

### 2.1 Spacing & dimensions — `ui/theme/Dimens.kt`

Replaces the scattered `dp` literals (census: `2,4,6,8,10,12,16,20,24,28,400`). Two layers:

| Layer | Tokens | Notes |
|---|---|---|
| Raw 4 dp grid (+2 dp sub-step) | `space1`=2 · `space2`=4 · `space3`=8 · `space4`=12 · `space5`=16 · `space6`=24 · `space7`=32 | use when no semantic token fits |
| Semantic | `screenPaddingHorizontal`=16 · `screenPaddingVertical`=12 · `fieldSpacing`=10 · `sectionSpacing`=8 · `fieldRowPaddingTight`=2 · `rowGap`=12 · `rowGapWide`=16 | named for intent |
| Cards | `cardPadding`=16 · `heroCardPadding`=20 · `cardElevation`=1 · `cardElevationRaised`=3 | feeds S13b `AabCard` |
| Components | `iconSize`=24 · `touchTarget`=48 · `dividerThickness`=1 · `chartHeight`=200 | `chartHeight` feeds S13d slots |

**Off-grid flag for S13c:** `fieldSpacing` = 10 dp (carried from S12.5b draft-edit screens) and
`heroCardPadding` = 20 dp are the two non-4 dp-grid semantic tokens. Kept as-is here for behaviour
parity; S13c should decide whether to normalise 10→8/12 and 20→24 (one-line change in `Dimens.kt`).

Referenced today (S13a acceptance — "≥1 screen"): `components/SettingsScaffold.kt::SettingsColumn`
(every settings screen inherits it) and `screens/MenuScreen.kt`.

### 2.2 Shape — `ui/theme/Shape.kt`

`AabShapes` = the M3 default shape scale, formalised + wired into `TideoTheme`:
extraSmall 4 / small 8 / medium 12 / large 16 / extraLarge 28 dp.

| Token | Intended use |
|---|---|
| `extraSmall` (4) | chips, small inline affordances |
| `small` (8) | text fields, buttons |
| `medium` (12) | standard `AabCard` / section containers (S13b) |
| `large` (16) | hero cards, dialogs, bottom sheets |
| `extraLarge` (28) | full-bleed banners / prominent surfaces |

### 2.3 Typography — `ui/theme/Type.kt`

`AabTypography` = explicit M3 type scale (default font), wired into `TideoTheme`. The six roles the
UI renders today are restated with standard metrics; the rest inherit M3 defaults. Role map:

| Role | Use | S13c emphasis note |
|---|---|---|
| `titleLarge` (22) | hero card titles, prominent headings | candidate for SemiBold |
| `titleMedium` (16, Medium) | `SectionHeader` (primary-tinted) | already weighted |
| `bodyLarge` (16) | field labels, switch/slider text | — |
| `bodyMedium` (14) | derived readouts, subtitles, secondary | — |
| `bodySmall` (12) | helper / validation / long-press help | — |
| `labelLarge` (14, Medium) | button text | M3 button default |

### 2.4 ColorScheme role-mapping (S13a deliverable — documents the *fixed* S12.5a scheme)

The palette is frozen; this is the contract for *which semantic role* carries *which brand colour*,
so S13b/S13c reach for `MaterialTheme.colorScheme.*` and never a raw `AabTeal`/`AabGold` literal
(two sanctioned raw uses remain: the hero-card icon tint and chart series — see notes).

| M3 role | Dark value | Light value | Semantic use in AAB |
|---|---|---|---|
| `primary` | `AabTeal` #007C63 | `AabTeal` | brand teal: app bars, primary buttons, section headers, "on" indicators, switches |
| `onPrimary` | `AabOnTeal` #FFFFFF | #FFFFFF | text/icons on teal |
| `primaryContainer` | `AabTeal` | `AabTealAccent` #00A986 | hero/prominent cards (Menu) |
| `secondary` | `AabGold` #FFC107 | `AabGold` | gold "strong" accent, warnings, emphasis |
| `onSecondary` | `AabOnGold` #2A2000 | `AabOnGold` | dark text on gold |
| `tertiary` | `AabTealAccent` #00A986 | `AabTealAccent` | lighter teal accent headings |
| `background` | `AabBackgroundDark` #333333 | `AabBackgroundLight` #F6F8F7 | scene background |
| `surface` | `AabSurfaceDark` #383838 | `AabSurfaceLight` #FFFFFF | cards, sheets, fields |
| `surfaceVariant` | `AabPanelDark` #404040 | `AabSurfaceVariantLight` #DCE5E1 | decorative panels, dividers, disabled text |
| `onSurface` / `onSurfaceVariant` | `AabOnDark` #ECECEC | `AabOnLight` #1A1C1B | body / secondary text |
| `error` | `AabError` #D32F2F | `AabError` | invalid-field red (`_RedInvalidFormulae` family) |

Sanctioned raw-colour uses (NOT recolored by S13c): `AabGold` for the Menu hero-card icon tint
(Tasker gold-sun), and the `AabChartBlue`/`AabGold`/`AabTeal` **chart series** colours (Chart.js
dataset parity, S13d) — these are data-encoding colours, not theme roles.

### 2.5 Component blueprints for S13b (owner UI-overhaul direction, 2026-06-20)

The owner forwarded a "Pro-Tool aesthetic" UI spec (high-contrast, structured, grouped, gold data
accents). It validates this stage and sharpens the S13b component list. It is adopted **with one
correction**: the spec's `#1A1C1E` surface / `Tertiary`-amber proposal is mapped onto the **frozen**
AAB palette (S12.5a, Tasker-provenanced), since "color scheme is fixed — NOT recoloring" is a binding
S13 guardrail. The structure, contrast intent, and gold-for-data emphasis are all honoured; only the
exact hex values defer to the existing scheme (dark surface `#383838`, data accent = `secondary`
`AabGold`, not a new `tertiary`). S13b builds these as the reusable "Lego blocks"; S13c applies them.

| # | Component (S13b) | Spec | Palette/token mapping |
|---|---|---|---|
| B1 | **`SectionHeader`** (exists — enhance) | group label above settings clusters | `titleSmall`/`titleMedium`, color `primary` (teal) + a thin `outlineVariant` `Divider` immediately below |
| B2 | **`HeroNavCard`** (exists in MenuScreen — promote to shared) | large `ElevatedCard`, **teal left-edge accent** bar, title + optional subtitle + right-aligned chevron | `large` shape, `cardElevationRaised`; left accent = `primary`; icon tint `AabGold` (Tasker gold-sun, sanctioned); trailing `Icons.AutoMirrored.Filled.KeyboardArrowRight` |
| B3 | **`NavRow`** | clickable row grouped *inside* an `AabCard` (no full-width dividers — card groups), text left + grey chevron right | `onSurface` text, `onSurfaceVariant` chevron; rows share one `AabCard` container |
| B4 | **`KeyValueRow`** (NEW — the critical data readout) | `SpaceBetween` row: **Key** left (`bodyLarge`, `onSurface`) · **Value** right (`bodyLarge` **Bold**, **gold**); subtle bottom border | value color = `secondary` (`AabGold`); bottom border = `outlineVariant`. **This is the high-contrast data-pop the spec calls CRITICAL** — use for every derived/live numeric (Dashboard lux/target, derived form2A/form3A, throttle, dimming readouts) |
| B5 | **`ActionButtonBar`** (exists as `DraftApplyBar` pattern — generalise) | horizontal weight-even tonal/outlined buttons, each with a leading icon | `rowGap` between; `FilledTonalButton`/`OutlinedButton`; equal `weight(1f)` |

Structural rules folded into §3/§4 (apply to ALL screens): strict 16 dp horizontal page padding
(`screenPaddingHorizontal`, no edge-bleed); standardized teal `TopAppBar` with single-line ellipsised
`titleLarge` title; never a flat endless settings list — always grouped in an `AabCard` (`16dp`/`large`
or `medium` shape); collapse a row's secondary actions (Overwrite/Delete vs Load) behind a 3-dot
overflow menu (Profiles/Contexts rule + profile rows — see §3 row 10).

---

## 3. Per-screen redesign plan

Each row: current wireframe gaps (what the S12 functional build left flat) → the target M3 pattern
S13c applies using §2 tokens + S13b components. **S13c checks off the "Done" box.**

Legend for target components (built in S13b — see §2.5 for the owner-spec blueprints):
**`SettingField`** = unified NumberSettingField/IntSliderSettingField/SwitchSettingRow + validation +
help · **`AabCard`** = elevated section container (medium shape, `cardElevation`, `cardPadding`) ·
**`KeyValueRow`** (B4) = key/gold-value data readout · **`SectionHeader`** (B1) = teal label + divider
· **`HeroNavCard`** (B2) / **`NavRow`** (B3) = navigation · **`ActionButtonBar`** (B5) · **`EmptyState`**
= icon+text empty placeholder · **motion** = enter/exit + list-item animation helpers.

| # | Screen (file) | Current wireframe gaps | Target M3 pattern (S13c) | Done |
|---|---|---|---|:--:|
| 1 | **Menu** (`MenuScreen.kt`) | flat `ListItem` rows, no grouping containers, ad-hoc `8/12/16/20` dp (now tokenised), hero card uses raw padding | group Settings/Info rows into `AabCard` sections; hero card → `large` shape + elevation + motion on press; consistent `sectionSpacing` | ✅ |
| 2 | **Dashboard** (`DashboardScreen.kt`) | **14 ad-hoc cards**, mixed `2/8/12/16` dp, no shared card style, dense status blocks, no motion on live values | consolidate to `AabCard` instances (uniform elevation/padding/shape); live lux/target with animated value transitions; status chips for tier/health | ✅ |
| 3 | **Curve & Brightness** (`CurveBrightnessScreen.kt`) | single card + raw fields, `12.dp` literal, derived-readout row visually flat | `SettingField` rows in `AabCard` groups; derived form2A/form3A in a distinct readout card; chart host gets `chartHeight` | ✅ |
| 4 | **Reactivity** (`ReactivityScreen.kt`) | **no cards at all** — bare field stack, no section grouping | wrap threshold/delta/trust groups in `AabCard`; `SettingField` for every row; section headers | ✅ |
| 5 | **Super Dimming** (`SuperDimmingScreen.kt`) | bare stack, lone `2.dp`, ELEVATED-gated rows not visually distinguished | `AabCard` groups; disabled/tier-gated rows get the tier-hint treatment; PWM toggle grouped | ✅ |
| 6 | **Circadian** (`CircadianScreen.kt`) | 3 inconsistent cards, `8.dp` only, sun-source toggle not emphasised, has an empty-state hint inline | unify cards; `EmptyState` for "no location yet"; sun-source as a clear segmented choice | ✅ |
| 7 | **Misc** (`MiscScreen.kt`) | **no cards**, bare slider stack | group experiment sliders into a labelled `AabCard`; consistent `fieldSpacing` | ✅ |
| 8 | **Tools** (`ToolsScreen.kt`) | 4 cards, mixed `8/12/16` dp, wizard/calibration/debug visually equal-weight | `AabCard` per tool with clear titles; wizard CTA emphasised; in-app log view styled | ✅ |
| 9 | **Live Debug** (`LiveDebugScreen.kt`) | 7 cards, mixed `4/8/16` dp, monospace log dense, no empty-state | uniform `AabCard`; scrollable log surface with `EmptyState` when empty; consistent spacing | ✅ |
| 10 | **Profiles & Contexts** (merged, `ProfilesContextsScreen.kt` + `ProfilesScreen.kt`/`ContextsScreen.kt` content) | **no cards on the merged host**, rule list mixes `6/8/10/12/16/28/400` dp, `400.dp` fixed list height, 5 inline empty hints, rule-editor modal minimally styled | rule/profile rows as `AabCard`; replace inline empty hints with `EmptyState`; rule-editor modal styled (`large` shape); drop the fixed `400.dp` for `weight`/intrinsic; motion on add/remove | ✅ |

**S13c completion notes (per row, against the targets above):**
- **1 Menu** — hero → shared `HeroNavCard` (teal edge + press-scale motion); Dashboard/Settings/Info rows → `NavRow` grouped in `AabCard` sections; `SectionHeader(divider=true)`.
- **2 Dashboard** — the plain status/light/brightness/context/health blocks → `AabCard` (uniform elevation/padding); the colour-semantic banners (stale/override) intentionally kept as tinted `Card` (they encode state, not section grouping). Status chips already present (`TierBadge`/`AssistChip`).
- **3 Curve & Brightness** — curve-zone fields grouped in an `AabCard`; derived continuity coefficients now a distinct `AabCard` of gold `KeyValueRow` data-pops (B4).
- **4 Reactivity** — threshold/α groups keep the `GraphSettingsGroup` (carries the `group_<graph>` test contract, G2R-F82); the trailing override/trust switches grouped into an `AabCard`.
- **5 Super Dimming** — already grouped by `GraphSettingsGroup`; `SectionHeader(divider=true)` added; tier-gated rows keep the existing `enabled=` hinting + grant link.
- **6 Circadian** — scaling/taper keep `GraphSettingsGroup`; the date/location block grouped into an `AabCard`. (No literal "no location" empty-state: the `exp_status` line already reports live-vs-fixed and is test-pinned — converting it would break G2R-F39 tests.)
- **7 Misc** — Brightness-range / Animation / Notifications each in an `AabCard`; derived throttle + auto-scale readouts → gold `KeyValueRow` (B4).
- **8 Tools** — `WizardCard`, the diagnostics report, and the power-draw section → `AabCard`.
- **9 Live Debug** — already fully component-consistent via the glass-box `DiagnosticCard` (surfaceVariant + teal title + gold values); left as the right semantic component rather than flattening to a generic `AabCard`. No log list exists yet, so no `EmptyState` was needed.
- **10 Profiles & Contexts** — profile/rule/legacy rows → `AabCard`; "no profiles"/"no rules" inline hints → `EmptyState`; rule-editor modal gains `tonalElevation`. **Deferred:** the 3-dot overflow for a row's secondary actions and dropping the app-picker `heightIn(max=400.dp)` — both kept as-is because the existing screen tests find `apply/overwrite/delete_*` directly (an overflow menu hides them until expanded) and the picker lives inside a `verticalScroll` parent (a `weight` child is illegal there). Recorded for S14.
- **Cross-cutting motion (§4):** app-wide screen enter/exit wired via `AabMotion.screenEnter/Exit` on the `AppNavGraph` `NavHost`; hero press-scale via `HeroNavCard`.

**Not in S13c (handled in S13d):** About + User-Guide static screens, the six remaining charts
(`ReactivityChart`/`DimmingChart`/`CircadianChart`/`TaperChart`/`PowerDrawChart`/`ExperimentChart`)
filling the `ChartSlot.content` swap points, and removing `PlaceholderScreen.kt`/`ChartPlaceholder.kt`.

---

## 4. Cross-cutting gaps (apply via S13b components, all screens)

- **No shared card style** → `AabCard` (medium shape, `cardElevation`, `cardPadding`) everywhere a
  raw `Card(...)`/bare `Column` group exists today; group settings into cards, never a flat list (§2.5).
- **Low-contrast data** → `KeyValueRow` (B4) renders every derived/live numeric with a **bold gold**
  value against the key, giving the "Pro-Tool" data-pop the owner spec requires (Dashboard, derived
  curve coefficients, throttle, dimming readouts).
- **Edge-bleed / inconsistent app bars** → strict `screenPaddingHorizontal`; teal single-line
  ellipsised `TopAppBar` everywhere (§2.5 structural rules).
- **Stacked secondary actions** → 3-dot overflow menu for a row's secondary actions (Profiles/Contexts).
- **No empty-states** → `EmptyState` for: Circadian (no location), Live Debug (empty log),
  Profiles/Contexts (no profiles / no rules), Tools (no recorded wizard samples — D-044c note).
- **No motion** → S13b motion helpers: screen enter/exit, list add/remove, press feedback on hero/CTA.
- **Inconsistent field rendering** → fold `NumberSettingField`/`IntSliderSettingField`/`SwitchSettingRow`
  into one `SettingField` surface so validation/help/`[committed]`-bracket behaviour is uniform
  (behaviour-preserving — S12.5b semantics kept).
- **Ad-hoc `dp`** → §2.1 tokens (Menu + SettingsColumn already migrated in S13a as the reference).

---

## 5. S13a acceptance checklist

- [x] `Dimens.kt` / `Shape.kt` / `Type.kt` created with decided values.
- [x] Tokens referenced by ≥1 screen (`SettingsColumn` shared by all settings screens; `MenuScreen`)
      and wired into `TideoTheme` (typography + shapes).
- [x] Audit enumerates all 9 screens + the merged Profiles/Contexts screen (§3) with gap→target rows.
- [x] ColorScheme role-mapping documented (§2.4); color scheme unchanged (behaviour-preserving).
- [x] Build green (`:app:assembleDebug :app:testDebugUnitTest :app:lintDebug`).
