# Scene: AAB Dimming Graph

- XML line range: **L3006–3169** (`<Scene sr="sceneAAB Dimming Graph">`)
- Scene geom: 1440 x 2944 (portrait)
- Chart generator: **task556 `_GenerateDimmingCurveGraph (Java)`** (feeds `%AAB_HTML_Graph6`)
- Target M3 screen: **Animation & Dimming**

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
| elements1 (graph_view) | WebElement | `%AAB_HTML_Graph6` | — | — | **The dimming curve graph** (Chart.js canvas) |
| elements2 (save_button_drop_shadow) | TextElement | — | (empty) | — | Drop-shadow behind Apply |
| elements3 (save_button) | TextElement | — | "Apply" | click=515 | Button: apply/save settings |
| elements4 (back_scene_drop_shadow) | TextElement | — | "Back" | — | Drop-shadow behind Back |
| elements5 (back_scene) | TextElement | — | "Back" | click=516 | Button: close scene |
| props | PropertiesElement | — | — | — | Scene properties (bg #FF000000) |

## Chart

Source: task556 `_GenerateDimmingCurveGraph (Java)`. Consumes `%aab_minbright`, `%aab_dimmingthreshold`, `%aab_dimmingexponent`, `%aab_dimmingstrength`.

- **X-axis**: target brightness level (`brightness_labels`), integer loop from `minbright` to `max(dimmingthreshold, 15)`, step 1. Linear brightness level (0..255 domain), NOT lux.
- **Y-axis**: dim progress percentage (0..100) plus a secondary dim-shell value (`dim_ds_points`).
- **Series (3)**:
  - `dim_data_points` = user curve `pow(1 - (b - minbright)/(dimmingthreshold - minbright), dimmingexponent) * 100` for `b < dimmingthreshold`, else 0.
  - `ref_dim_data_points` = reference curve `pow(1 - b/15, 2.5) * 100` for `b < 15`, else 0.
  - `dim_ds_points` = dim shell `dimmingstrength * dim_progress` (the applied dimming magnitude).
  - Also emits scalar `max_dim_strength` (dim shell at first step) as a marker/annotation value.
- Percentages rounded to 3 decimals; labels to 0 decimals (BigDecimal HALF_UP).

Target Compose chart: **DimmingChart**.

## Disposition

| element | disposition |
|---------|-------------|
| elements0 (bg_banner_title) | dropped(scene chrome) |
| elements1 (graph_view / AAB_HTML_Graph6) | kept-as Animation & Dimming as DimmingChart |
| elements2 (save drop_shadow) | dropped(scene chrome) |
| elements3 (Apply) | merged-into Animation & Dimming |
| elements4 (back drop_shadow) | dropped(scene chrome) |
| elements5 (Back) | dropped(scene chrome) |
| props | dropped(scene chrome) |
