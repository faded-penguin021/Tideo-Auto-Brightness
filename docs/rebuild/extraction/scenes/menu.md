# Scene: AAB Menu

- XML line range: **L4462–4717** (`<Scene sr="sceneAAB Menu">`)
- Scene geom: 1440 x 2944 (portrait)
- Title: "AAB Menu" (PropertiesElement `props`, keyTask 606)
- Role: **Navigation hub** — a single full-screen WebElement renders an HTML menu; taps call `performTask(taskName, 10)` to launch the named tasks/scenes.

## Element count by type (top-level Scene children = 2)

| Type | Count |
|------|-------|
| WebElement | 1 |
| PropertiesElement | 1 |
| **Total** | **2** |

(The raw `<RectElement sr=` grep returns 1 — that is the nested `background` Rect inside the WebElement, intrinsic styling, not a scene element.)

## Elements (in document order)

| # | name (sr) | type | bound variable | value range / options | handler task(s) | purpose |
|---|-----------|------|----------------|-----------------------|-----------------|---------|
| elements0 | menu | WebElement | — (static HTML `menu`) | navigation targets (see below) | per-button `handleNavigation()` → `performTask(name,10)` | Full-screen HTML menu UI ("Advanced Auto Brightness" banner + cards). Nav targets: hero card → `_ShowProfileScene` (Profiles & Contexts); Settings card → `Advanced Auto Brightness` (General), `_ReactivityScene` (Reactivity), `_MiscScene` (Misc), `_ExperimentScene` (Circadian), `_SuperDimmingScene` (Super Dimming); Info & Help card → `_ShowDebugScene` (Live Debug Info), `_ShowUserGuide` (User Guide), `_ShowAboutScene` (About), `_AskPermissionsV7` (Recheck Permissions), `_ShowLicenseScene` (Chart.js License) |
| props | props | PropertiesElement | — | — | keyTask=606; LinkClickFilter `back` (stopEvent) | Scene properties: title "AAB Menu", back-link filter, key task 606 |

## Disposition

- elements0 — merged-into Dashboard (the menu's nav function is replaced by the M3 navigation; hero/Settings/Info entries become the M3 destination set, not an HTML page)
- props — dropped(nav replaced by M3 nav)
