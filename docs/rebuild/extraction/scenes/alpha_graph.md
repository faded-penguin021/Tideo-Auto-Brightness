# Scene: AAB Alpha Graph

- XML line range: **L1038–1201** (`<Scene sr="sceneAAB Alpha Graph">`)
- Scene geom: 1440 x 2944 (portrait)
- Chart generator: **task557 `_GenerateAlphaGraph (Java)`** (feeds `%AAB_HTML_Graph3`)
- Target M3 screen: **Reactivity** (alpha-response overlay)

## Element count by type (scene-level elements)

| Type | Count |
|------|-------|
| WebElement | 2 |
| TextElement | 4 |
| PropertiesElement | 1 |
| **Total scene elements** | **7** |

(The raw `RectElement` grep returns 5 — those are intrinsic per-element `background` rects, not scene elements.)

## Elements (in document order)

| name (sr) | type | bound var (arg2) | label (arg1) | tap handler | purpose |
|-----------|------|------------------|--------------|-------------|---------|
| elements0 | WebElement | `%AAB_Scenebg` | — | — | Banner/title background (scene chrome) |
| elements1 | WebElement | `%AAB_HTML_Graph3` | — | — | **The alpha graph** (Chart.js canvas, full panel L0,309,1440,1959) |
| elements2 | TextElement | — | "Save" | — | Drop-shadow layer behind Apply button |
| elements3 | TextElement | — | "Apply" | click=746 | Button: apply/save (recompute) |
| elements4 | TextElement | — | "Back" | — | Drop-shadow layer behind Back button |
| elements5 | TextElement | — | "Back" | click=748 | Button: close scene |
| props | PropertiesElement | — | — | — | Scene properties (bg #FF000000) |

## Chart

Source: task557 `_GenerateAlphaGraph (Java)`, consumes `%aab_deltafactor`.

- **X-axis**: lux delta proxy — 39 hardcoded `labelValues` from 1 → 2000 (geometric ramp); each value is divided by 100 → `lux_delta`. Effectively a log-ish lux-change axis.
- **Y-axis**: alpha (smoothing response factor) in 0..1.
- **Series (2)**:
  - `new_data` = `1 - exp(-aab_deltafactor * lux_delta)` — the user's current alpha curve.
  - `ref_data` = `1 - exp(-1.8 * lux_delta)` — hardcoded reference curve (deltafactor=1.8).
- No threshold/marker lines. Both series rounded to 6 decimals (HALF_UP).

Target Compose chart: **ReactivityChart (alpha overlay)** — render `new_data` vs `ref_data` as the alpha-response overlay inside the Reactivity screen. (If kept standalone, its own AlphaChart.)

## Disposition

| element | disposition |
|---------|-------------|
| elements0 (bg_banner_title) | dropped(scene chrome) |
| elements1 (graph_view / AAB_HTML_Graph3) | kept-as Reactivity as ReactivityChart(alpha overlay) |
| elements2 (save drop_shadow) | dropped(scene chrome) |
| elements3 (Apply) | merged-into Reactivity |
| elements4 (back drop_shadow) | dropped(scene chrome) |
| elements5 (Back) | dropped(scene chrome) |
| props | dropped(scene chrome) |
