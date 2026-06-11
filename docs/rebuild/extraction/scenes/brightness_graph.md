# Scene: AAB Brightness Graph

- XML line range: **L1202–1414** (`<Scene sr="sceneAAB Brightness Graph">`)
- Scene geom: 1440 x 2944 (portrait)
- Chart generator: **task663 `_GenerateGraph (Java)`** (feeds `%AAB_HTML_Graph`)
- Target M3 screen: **Curve & Brightness**

## Element count by type (scene-level elements)

| Type | Count |
|------|-------|
| WebElement | 2 |
| TextElement | 6 |
| PropertiesElement | 1 |
| **Total scene elements** | **9** |

(Raw `RectElement` grep returns 7 — intrinsic per-element backgrounds.)

## Elements (in document order)

| name (sr) | type | bound var (arg2) | label (arg1) | tap handler | purpose |
|-----------|------|------------------|--------------|-------------|---------|
| elements0 | WebElement | `%AAB_Scenebg` | — | — | Banner/title background (scene chrome) |
| elements1 | WebElement | `%AAB_HTML_Graph` | — | — | **The brightness curve graph** (Chart.js canvas) |
| elements2 | TextElement | — | (empty) | — | Drop-shadow behind Apply |
| elements3 | TextElement | — | "Apply" | click=384 | Button: apply/save settings |
| elements4 | TextElement | — | "Suggest graph" | — | Drop-shadow behind Suggest button |
| elements5 | TextElement | — | "Back" | — | Drop-shadow behind Back button |
| elements6 | TextElement | — | "Back" | click=412 | Button: close scene |
| elements7 | TextElement | — | "Suggest values" | click=651 | Button: auto-suggest curve params (task38 _SuggestCurveParameters) |
| props | PropertiesElement | — | — | — | Scene properties (bg #FF000000) |

## Chart

Source: task663 `_GenerateGraph (Java)` (block #2 is the curve math; block #1 builds `scatter_data` from `%AAB_Overrides`).

Consumes: `%aab_zone1end`, `%aab_zone2end`, `%aab_form1a`, `%aab_form2a`, `%aab_form2b`, `%aab_form2c`, `%aab_form3a`, `%AAB_MinBright`, `%AAB_MaxBright`, `%AAB_Overrides`.

- **X-axis**: lux — 41 hardcoded log-spaced values 0.1 → 100000 (logarithmic lux).
- **Y-axis**: brightness level (0..255, clamped to MinBright/MaxBright).
- **Series (3)**:
  - `new_data` = user's 3-zone curve: Zone1 `form1a*sqrt(lux)`; Zone2 `form2a + form2b*(pow(lux-form2c,0.33) - pow(zone1end-form2c,0.33))`; Zone3 `MaxBright - (form3a/lux)*MaxBright`. Clamped Min/Max.
  - `ref_data` = hardcoded reference 3-zone curve (5*sqrt(lux); 29.58+8.8*(...); 255-(2513/lux)*255). Clamped.
  - `scatter_data` = user override points `{x:lux,y:brightness}` from `%AAB_Overrides1..N` (block #1).
- No marker lines; the override scatter doubles as point markers. Curves rounded to 3 decimals (HALF_UP).

Target Compose chart: **BrightnessCurveChart**.

## Disposition

| element | disposition |
|---------|-------------|
| elements0 (bg_banner_title) | dropped(scene chrome) |
| elements1 (graph_view / AAB_HTML_Graph) | kept-as Curve & Brightness as BrightnessCurveChart |
| elements2 (save drop_shadow) | dropped(scene chrome) |
| elements3 (Apply) | merged-into Curve & Brightness |
| elements4 (suggest drop_shadow) | dropped(scene chrome) |
| elements5 (back drop_shadow) | dropped(scene chrome) |
| elements6 (Back) | dropped(scene chrome) |
| elements7 (Suggest values) | merged-into Curve & Brightness |
| props | dropped(scene chrome) |
