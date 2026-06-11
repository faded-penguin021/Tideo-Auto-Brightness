# Scene: AAB Circadian Dimming Graph

- XML line range: **L2388–2551** (`<Scene sr="sceneAAB Circadian Dimming Graph">`)
- Scene geom: 1440 x 2944 (portrait)
- Chart generator: **task705 `_GenerateCircadianDimmingGraph (Java)`** (feeds `%AAB_HTML_Graph7`)
- Target M3 screen: **Animation & Dimming**

## Element count by type (scene-level elements)

| Type | Count |
|------|-------|
| WebElement | 2 |
| TextElement | 4 |
| PropertiesElement | 1 |
| **Total scene elements** | **7** |

(Raw `RectElement` grep returns 5 — intrinsic per-element backgrounds.)

## Elements (in document order)

| name (sr) | type | bound var (arg2) | label (arg1) | tap handler | purpose |
|-----------|------|------------------|--------------|-------------|---------|
| elements0 | WebElement | `%AAB_Scenebg` | — | — | Banner/title background (scene chrome) |
| elements1 | WebElement (graph_view4) | `%AAB_HTML_Graph7` | — | — | **The circadian dimming graph** (Chart.js canvas) |
| elements2 | TextElement | — | (empty) | — | Drop-shadow behind Apply |
| elements3 | TextElement | — | "Apply" | click=520 | Button: apply/save settings |
| elements4 | TextElement | — | "Back" | — | Drop-shadow behind Back button |
| elements5 | TextElement | — | "Back" | click=521 | Button: close scene |
| props | PropertiesElement | — | — | — | Scene properties (bg #FF000000) |

## Chart

Source: task705 `_GenerateCircadianDimmingGraph (Java)`. Consumes `%aab_dimspread`, `%AAB_ScaleTransitionFactor`, `%AAB_ScaleSteepness`, `%AAB_Sunlightduration`, `%AAB_PolarState`, and the five sun events `%AAB_Sundawn/Sunrise/Sunnoon/Sunset/Sundusk`.

- **X-axis**: time of day 00:00 → 24:00 (`'HH:MM'` labels, 600s/10-min steps → 145 points). Linear time, not lux.
- **Y-axis**: dim modifier multiplier. `dim_val = 2.0 - (1.0 + (dimspread/100)*modifier)` where `modifier` is a tanh-smoothed day/night progress (0=night → 1=day). So Y is the dimming multiplier that is highest at night and lowest at day (inverse of the scaling graph).
- **Series (1)**: `data_points` (the dim curve over the day).
- **Markers**: emits `sim_calc_dawn / sim_calc_noon / sim_calc_sunset / sim_calc_dusk` and `now_utc` for vertical reference lines in the HTML template. Rounded to 3 decimals (HALF_UP).

Target Compose chart: **CircadianChart**.

## Disposition

| element | disposition |
|---------|-------------|
| elements0 (bg_banner_title) | dropped(scene chrome) |
| elements1 (graph_view4 / AAB_HTML_Graph7) | kept-as Animation & Dimming as CircadianChart |
| elements2 (save drop_shadow) | dropped(scene chrome) |
| elements3 (Apply) | merged-into Animation & Dimming |
| elements4 (back drop_shadow) | dropped(scene chrome) |
| elements5 (Back) | dropped(scene chrome) |
| props | dropped(scene chrome) |
