# Scene: AAB Chart.Js License

- XML line range: **L2194–2387** (`<Scene sr="sceneAAB Chart.Js License">`)
- Scene geom: 1440 x 2944 (portrait)
- Type: single full-screen **WebElement** rendering the static Chart.js MIT license text + close button
- Target M3 screen: **none — dropped (Chart.js removed from the Kotlin rebuild)**

## Element count by type (top-level Scene children = 3)

| Type | Count |
|------|-------|
| WebElement | 1 |
| ButtonElement | 1 |
| PropertiesElement | 1 |
| **Total** | **3** |

(WebElement carries one nested `background` RectElement — intrinsic styling, not counted.)

## Elements (in document order)

| # | name (sr) | type | bound variable | value range / options | handler task(s) | purpose |
|---|-----------|------|----------------|-----------------------|-----------------|---------|
| elements0 | (web, arg0="Chart.Js License") | WebElement | — (static) | — | LinkClickFilter urlMatch="back" (stopEvent) | Full-screen static HTML showing the Chart.js MIT license text |
| elements1 | Button1 | ButtonElement | — | icon `mw_navigation_close` (geom 1282,0,157,157) | clickTask=**718** | Top-right close (X) button |
| props | props | PropertiesElement | — | title "AAB Chart.Js License" | keyTask=**604** | Scene properties; back/key handler task 604 |

## Static TEXT content
- Chart.js MIT license attribution page. Not ported: Chart.js is removed in the Kotlin rebuild
  (native Compose charts replace it), so this third-party license page has no target screen.
- If a third-party license aggregation screen is later added, the Chart.js entry would no longer
  apply; any new charting library's license belongs there instead.

## Disposition

| Element | Disposition |
|---------|-------------|
| elements0 (Chart.js license web page) | chartjs_license — dropped(Chart.js removed) |
| elements1 (close button) | chartjs_license — dropped(Chart.js removed) |
| props (scene properties / back task 604) | chartjs_license — dropped(Chart.js removed) |
