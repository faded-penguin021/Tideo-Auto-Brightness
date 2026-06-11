# Scene: AAB Experiment Graph

- XML line range: **L3170–3333** (`<Scene sr="sceneAAB Experiment Graph">`)
- Scene geom: 1440 x 2944 (portrait)
- Chart generator: **task549 `_GenerateCircadianGraph V8 (Java)`** (feeds `%AAB_HTML_Graph4`)
  - NOTE: the Experiment Graph reuses the **circadian/scaling generator** (same task that drives the day-scaling preview). It is a live "what-if" sandbox fed by `sim_*` simulation variables rather than the live `%AAB_*` sun events.
- Target M3 screen: **Dynamic Scale**

## Element count by type (scene-level elements)

| Type | Count |
|------|-------|
| WebElement | 2 |
| TextElement | 4 |
| PropertiesElement | 1 |
| **Total scene elements** | **7** |

(Raw `RectElement` grep returns 5 — intrinsic per-element `background` rects, not scene elements.)

## Elements (in document order)

| name (sr) | type | bound var (arg2) | label (arg1) | tap handler | purpose |
|-----------|------|------------------|--------------|-------------|---------|
| elements0 (bg_banner_title) | WebElement | `%AAB_Scenebg` | — | — | Banner/title background (scene chrome) |
| elements1 (graph_view4) | WebElement | `%AAB_HTML_Graph4` | — | — | **The experiment / day-scaling sandbox graph** (Chart.js canvas) |
| elements2 (save_button_drop_shadow) | TextElement | — | (empty) | — | Drop-shadow behind Apply |
| elements3 (save_button) | TextElement | — | "Apply" | click=669 | Button: apply/save experiment params |
| elements4 (back_scene_drop_shadow) | TextElement | — | "Back" | — | Drop-shadow behind Back |
| elements5 (back_scene) | TextElement | — | "Back" | click=671 | Button: close scene |
| props | PropertiesElement | — | — | — | Scene properties (bg #FF000000) |

## Chart

Source: task549 `_GenerateCircadianGraph V8 (Java)`. Consumes simulation vars `sim_spread`, `sim_transition`, `sim_steepness`, plus `%AAB_PolarState`, `%AAB_Sunlightduration`, and the five normalized sun events `%AAB_Sundawn/Sunrise/Sunnoon/Sunset/Sundusk`.

- **X-axis**: time of day 00:00 → 24:00 (`'HH:MM'` labels, 600s/10-min steps → 145 points). Linear time, not lux.
- **Y-axis**: day-scaling multiplier `scaled_value = 1.0 + (sim_spread/100) * modifier`, where `modifier` is a tanh-normalized day/night progress in [-1..1] (1 at solar noon, -1 deep night). So Y is the brightness-scaling factor across the day (highest by day, lowest at night — inverse of the dimming graph).
- **Series (1)**: `data_points` (the scaling curve over the day).
- **Markers**: emits `sim_calc_dawn / sim_calc_noon / sim_calc_sunset / sim_calc_dusk` (normalized seconds) for vertical reference lines in the HTML template.
- Values rounded to 3 decimals (BigDecimal HALF_UP).

Target Compose chart: **ExperimentChart** (shares its series model with CircadianChart; render in the Dynamic Scale screen as the live what-if preview).

## Disposition

| element | disposition |
|---------|-------------|
| elements0 (bg_banner_title) | dropped(scene chrome) |
| elements1 (graph_view4 / AAB_HTML_Graph4) | kept-as Dynamic Scale as ExperimentChart |
| elements2 (save drop_shadow) | dropped(scene chrome) |
| elements3 (Apply) | merged-into Dynamic Scale |
| elements4 (back drop_shadow) | dropped(scene chrome) |
| elements5 (Back) | dropped(scene chrome) |
| props | dropped(scene chrome) |
