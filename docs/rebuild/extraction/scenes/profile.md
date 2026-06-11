# Scene: AAB Profile

- XML line range: L5724–L6562
- Source: `Advanced_Auto_Brightness_V3.3.prj_9.xml`, `<Scene sr="sceneAAB Profile">`
- Canvas: 1440 × 2944 (portrait)
- Element count by type: WebElement ×1, RectElement ×1, PropertiesElement ×1 (total 3)
- This scene is a single full-screen HTML WebElement (the "Profile Manager / Dashboard"
  + Context-rule editor). All interaction happens via the Tasker JS interface
  (`performTask(...)` / `handleTaskerCall(...)`) inside the HTML, not via element tap handlers.
  There is **no close ButtonElement** (unlike the other Web scenes).

## Elements (in document order)

| Name | Type | Bound variable | Value range / options | Tap handler | Purpose |
|------|------|----------------|-----------------------|-------------|---------|
| elements0 | WebElement | — (renders HTML; JS reads `%AAB_*` via `performTask`) | arg0 title = "Profile Dashboard"; arg1=2 (local HTML source) | JS-driven (see JS calls below) | Profile Manager dashboard: lists saved profile files, compares active settings vs factory defaults (tuned values shown yellow), Save/Load/Delete profiles, Create Default Profiles, and full Context-rule editor (App/Time+Day/Battery/Location/Wi-Fi triggers, priority 1–100). |
| background | RectElement | — | arg0="" (no fill); decorative | none | Scene background fill. |
| props | PropertiesElement | — | keyTask=632 (`AAB About` key task); LinkClickFilter urlMatch="stop", stopEvent=true | keyTask 632 on back-key; "stop" link → close | Scene-level properties: hardware back/menu key → task 632; intercept `stop` URL navigation to dismiss. |

### JS → Tasker calls embedded in the WebElement HTML
- `handleTaskerCall('_CreateDefaultProfiles', 10, 'FROM_PROFILE_SCENE')` — button "Create Default Profiles"
- `performTask('_ProfileManager', 10, 'SAVE_FILE', name)` — save profile
- `performTask('_ProfileManager', 10, 'LOAD_FILE', '${name}')` — load profile ("Load Anyway")
- `performTask('_ProfileManager', 10, 'DELETE_FILE', '${name}')` — delete profile
- `performTask('_ContextManager', 10, 'SAVE_CONTEXT', JSON.stringify(newCtx))` — save context rule
- `performTask('_ContextManager', 10, 'DELETE_CONTEXT', '${id}')` — delete context rule
- `performTask('_ContextResume', 10)` — resume context engine
- Context-rule UI controls: `toggleDay()` (day-of-week M/T/W/T/F/S/S, data-val 1–7),
  `setSolarTime('Start'|'End', 'SUNRISE'|'SUNSET')`, `_GetWifiForContext`, `_GetLocationForContext`.

## Disposition

| Element | Disposition |
|---------|-------------|
| elements0 (WebElement: profile dashboard + context editor) | profile — kept-as Profiles & Import/Export |
| background (RectElement) | profile — dropped(decorative scene background; M3 provides surface) |
| props (PropertiesElement / back task 632) | profile — dropped(handled by Compose navigation) |
