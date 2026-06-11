# Scene: AAB Taper Graph

- XML line range: **L8387–8550** (`<Scene sr="sceneAAB Taper Graph">`)
- Scene geom: 1440 x 2944 (portrait)
- Chart generator: **task657 `_GenerateCompressionGraph (Java)`** (feeds `%AAB_HTML_Graph5`)
  - NOTE: the Taper Graph reuses the **compression generator** (task657). The "taper" is the sigmoid that tapers the day/night scale spread toward the brightness extremes.
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
| elements1 (graph_view) | WebElement | `%AAB_HTML_Graph5` | — | — | **The taper / compression graph** (Chart.js canvas) |
| elements2 (save_button_drop_shadow) | TextElement | — | (empty) | — | Drop-shadow behind Apply |
| elements3 (save_button) | TextElement | — | "Apply" | click=679 | Button: apply/save taper settings |
| elements4 (back_scene_drop_shadow) | TextElement | — | "Back" | — | Drop-shadow behind Back |
| elements5 (back_scene) | TextElement | — | "Back" | click=680 | Button: close scene |
| props | PropertiesElement | — | — | — | Scene properties (bg #FF000000) |

## Chart

Source: task657 `_GenerateCompressionGraph (Java)`. Consumes `%aab_scalespread`, `%aab_scaletapermidpoint`, `%aab_scaletapersteepness`, `%AAB_MinBright`, `%AAB_MaxBright`.

- **X-axis**: mapped brightness level (`brightness_labels`), integer loop `minbright` → `maxbright`, step 1. Linear brightness level, NOT lux.
- **Y-axis**: effective scaling multiplier (around 1.0; `>1` = day boost, `<1` = night reduction). Shows how the spread is tapered by a sigmoid as brightness approaches the extremes.
- **Series (2)**:
  - `day_scale_points` = `1 + (sim_day_scale - 1) * taper_effect`, capped by a dynamic ceiling. `sim_day_scale = 1 + scalespread/100`.
  - `night_scale_points` = `1 + (sim_night_scale - 1) * taper_effect`, floored by a dynamic floor. `sim_night_scale = 1 - scalespread/100`.
  - `taper_effect = 1 - 1/(1 + exp(exponent))` is the sigmoid driven by `scaletapermidpoint` / `scaletapersteepness`.
- No explicit marker lines (the 1.0 baseline and the day/night caps are implicit). Values rounded to 3 decimals; labels to 0 decimals (BigDecimal HALF_UP).

Target Compose chart: **TaperChart**.

## Disposition

| element | disposition |
|---------|-------------|
| elements0 (bg_banner_title) | dropped(scene chrome) |
| elements1 (graph_view / AAB_HTML_Graph5) | kept-as Dynamic Scale as TaperChart |
| elements2 (save drop_shadow) | dropped(scene chrome) |
| elements3 (Apply) | merged-into Dynamic Scale |
| elements4 (back drop_shadow) | dropped(scene chrome) |
| elements5 (Back) | dropped(scene chrome) |
| props | dropped(scene chrome) |
