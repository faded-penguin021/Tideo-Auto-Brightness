# Scene: AAB Reactivity Graph

- XML line range: **L6563–6738** (`<Scene sr="sceneAAB Reactivity Graph">`)
- Scene geom: 1440 x 2944 (portrait)
- Chart generator: **task703 `_GenerateReactivityGraph (Java)`** (feeds `%AAB_HTML_Graph2`)
- Target M3 screen: **Reactivity**

## Element count by type (scene-level elements)

| Type | Count |
|------|-------|
| WebElement | 2 |
| TextElement | 4 |
| PropertiesElement | 1 |
| **Total scene elements** | **7** |

(Raw `RectElement` grep returns 6 — intrinsic per-element `background` rects, not scene elements.)

## Elements (in document order)

| name (sr) | type | bound var (arg2) | label (arg1) | tap handler | purpose |
|-----------|------|------------------|--------------|-------------|---------|
| elements0 (bg_banner_title) | WebElement | `%AAB_Scenebg` | — | — | Banner/title background (scene chrome) |
| elements1 (graph_view) | WebElement | `%AAB_HTML_Graph2` | — | — | **The reactivity / threshold graph** (Chart.js canvas) |
| elements2 (save_button_drop_shadow) | TextElement | — | (empty) | — | Drop-shadow behind Apply |
| elements3 (save_button) | TextElement | — | "Apply" | click=406 | Button: apply/save threshold settings |
| elements4 (back_scene_drop_shadow) | TextElement | — | "Back" | — | Drop-shadow behind Back |
| elements5 (back_scene) | TextElement | — | "Back" | click=411 | Button: close scene |
| props | PropertiesElement | — | — | — | Scene properties (bg #FF000000) |

## Chart

Source: task703 `_GenerateReactivityGraph (Java)`. Consumes `%aab_threshdark`, `%aab_threshdim`, `%aab_threshbright`, `%aab_threshmidpoint`, `%aab_threshsteepness`, `%aab_zone1end`.

- **X-axis**: lux — 39 hardcoded log-spaced `luxValues` 1 → 100000 (logarithmic lux).
- **Y-axis**: reactivity threshold percentage (0..100) — the change-magnitude threshold required to trigger a brightness update at a given lux.
- **Series (2)**:
  - `new_data` = user curve. For `lux < zone1end`: linear `(threshdark - ((threshdark - threshdim)/zone1end) * lux) * 100`. Else sigmoid `(threshdim + (threshbright - threshdim)/(1 + e^(-threshsteepness*(log10(lux+1) - threshmidpoint)))) * 100`.
  - `ref_data` = hardcoded reference. For `lux < 35`: `(0.30 - ((0.30 - 0.25)/35) * lux) * 100`. Else `(0.25 + (0.08 - 0.25)/(1 + e^(-2.1*(log10(lux+1) - 4)))) * 100`.
- No marker lines (zone1end is the implicit linear→sigmoid knee). Values rounded to 3 decimals (BigDecimal HALF_UP).

Target Compose chart: **ReactivityChart**.

## Disposition

| element | disposition |
|---------|-------------|
| elements0 (bg_banner_title) | dropped(scene chrome) |
| elements1 (graph_view / AAB_HTML_Graph2) | kept-as Reactivity as ReactivityChart |
| elements2 (save drop_shadow) | dropped(scene chrome) |
| elements3 (Apply) | merged-into Reactivity |
| elements4 (back drop_shadow) | dropped(scene chrome) |
| elements5 (Back) | dropped(scene chrome) |
| props | dropped(scene chrome) |
