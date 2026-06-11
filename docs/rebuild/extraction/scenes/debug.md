# Scene: AAB Debug Scene

- XML line range: L2583–L3005
- Source: `Advanced_Auto_Brightness_V3.3.prj_9.xml`, `<Scene sr="sceneAAB Debug Scene">`
- Canvas: 1440 × 2944 (portrait)
- Element count by type: WebElement ×1, ButtonElement ×1, RectElement ×1, PropertiesElement ×1 (total 4)
- Full-screen HTML WebElement (live "Debug Status" readout) + top-right close button.

## Elements (in document order)

| Name | Type | Bound variable | Value range / options | Tap handler | Purpose |
|------|------|----------------|-----------------------|-------------|---------|
| elements0 | WebElement | reads many `%AAB_*` / `%SmoothedLux` / `%LuxAlpha` live vars (see list) | arg0 title="bg_banner_title"; arg1=2 (local HTML) | JS-driven | Live debug dashboard: Core Metrics, System Status, Dimming Engine, Automation, Performance & Timings. Also hosts "Screen Power Measurement" calibration and a debug-level selector. |
| elements1 | ButtonElement | — | icon `mw_navigation_close`; geom 1282,0,157,157 (top-right) | clickTask **462** | Close/back button → runs task 462. |
| background | RectElement | — | arg0="" decorative | none | Scene background fill. |
| props | PropertiesElement | — | keyTask **600** ("AAB Settings"); LinkClickFilter urlMatch="back", stopEvent=true | keyTask 600 on back-key; "back" link → close | Scene properties: hardware key → task 600; intercept `back` URL to dismiss. |

### Live variables surfaced by the WebElement
`%SmoothedLux`, `%AAB_LastRawLux`, `%AAB_ThreshAbsLow`, `%AAB_ThreshAbsHigh`,
`%AAB_CurrentBright`, `%AAB_MaxBright`, `%AAB_ScaleDynamicCompress`, `%AAB_ScaleDynamic`,
`%AAB_Proximity`, `%LuxAlpha`, `%AAB_CycleTotal`, `%AAB_SunLastDate` — plus textual status
fields: Service, Manual Override, Animation, Reactivity Cooldown, Dimming Mode/Status,
Active Rule, Loaded Profile, Last Update, Last Animation.

### JS → Tasker calls embedded in the WebElement HTML
- `performTask('_CalibratePowerDraw', 10)` — "Screen Power Measurement" calibration tool
  (steps brightness, measures mA, generates Power Curve). Experimental diagnostic.
- `performTask('_SetDebugLevel', 10, val)` — set the debug *category* (10 named levels from the
  selector options at XML L2773–2782, e.g. "7 - Graph Metrics" — see features_spec §4, D-023).

## Disposition

| Element | Disposition |
|---------|-------------|
| elements0 (WebElement: live debug status + power calibration) | debug — kept-as Tools |
| elements1 (ButtonElement: close, clickTask 462) | debug — dropped(M3 nav back replaces in-scene close button) |
| background (RectElement) | debug — dropped(decorative scene background) |
| props (PropertiesElement / back task 600) | debug — dropped(handled by Compose navigation) |
