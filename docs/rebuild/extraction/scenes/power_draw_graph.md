# Scene: AAB Power Draw Graph

- XML line range: **L5611–5723** (`<Scene sr="sceneAAB Power Draw Graph">`)
- Scene geom: 1440 x 2944 (portrait)
- Chart generator: **task524 `_CalibratePowerDraw`** (feeds `%AAB_HTML_Graph8`)
  - NOTE: task524 is a live measurement/calibration routine. It steps the screen through 16 brightness levels (`TARGET_POINTS=16`, distribution exponent 0.45), samples real battery current/voltage via `BatteryManager` (median of polled samples), then builds the Chart.js HTML inline (`powerChart`) rather than via a separate string-replace template.
- Target M3 screen: **Tools**

## Element count by type (scene-level elements)

| Type | Count |
|------|-------|
| WebElement | 2 |
| TextElement | 1 |
| PropertiesElement | 1 |
| **Total scene elements** | **4** |

(Raw `RectElement` grep returns 3 — intrinsic per-element `background` rects. This scene has no Apply button — measurement-only, just Back.)

## Elements (in document order)

| name (sr) | type | bound var (arg2) | label (arg1) | tap handler | purpose |
|-----------|------|------------------|--------------|-------------|---------|
| elements0 (bg_banner_title) | WebElement | `%AAB_Scenebg` | — | — | Banner/title background (scene chrome) |
| elements1 (graph_view) | WebElement | `%AAB_HTML_Graph8` | — | — | **The measured power-draw graph** (Chart.js canvas) |
| elements2 (back_scene_drop_shadow) | TextElement | — | "Back" | — | Drop-shadow behind Back |
| elements3 (back_scene) | TextElement | — | "Back" | click=532 | Button: close scene |
| props | PropertiesElement | — | — | — | Scene properties (bg #FF000000) |

## Chart

Source: task524 `_CalibratePowerDraw`. Inline-built Chart.js `line` chart (no parametric formula — data is measured at runtime). Safety gates: aborts if battery current sensor unresponsive or device is charging/full.

- **X-axis**: `Brightness Level (0-255)`, linear, fixed `min:0 max:255`.
- **Y-axis (left, `y`)**: `Screen Power (W or mW)` — net measured screen power `power_w` (color #007C63, filled).
- **Y-axis (right, `y1`)**: `Current (mA)` — measured current draw (color #FFC107, dashed, no fill).
- **Series (2)**: `dataW` (power) on `y`, `dataY` (current) on `y1`. (Legend filters out hidden 'Upper' / 'Stability (SD)' helper datasets.)
- Output vars: `part_one`, `part_two` (HTML halves), `data` (JSON of measured `{brightness, power_w, current}` list).

Target Compose chart: **PowerDrawChart**.

## Disposition

| element | disposition |
|---------|-------------|
| elements0 (bg_banner_title) | dropped(scene chrome) |
| elements1 (graph_view / AAB_HTML_Graph8) | kept-as Tools as PowerDrawChart |
| elements2 (back drop_shadow) | dropped(scene chrome) |
| elements3 (Back) | dropped(scene chrome) |
| props | dropped(scene chrome) |
