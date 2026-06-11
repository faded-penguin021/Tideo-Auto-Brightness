# task585 — Reset Brightness and State

- **XML:** `Advanced_Auto_Brightness_V3.3.prj_9.xml` lines L23272–L23434 (163 lines)
- **Priority:** 6  · **Actions:** 18
- **Action-code histogram:** Variable Clear x7, code159 x3, Stop x3, Variable Set x2, Perform Task x2, code49 x1

> Auto-transcribed verbatim from XML (entity-decoded). Provenance: each row is `actN` at its XML line. Reads/writes inferred from Variable Set targets and `%var` references.

| act | line | code | detail | condition |
|---|---|---|---|---|
| 0 | L23278 | 159 code159 | **code159** arg0=`Monitor Ambient Light` |  |
| 1 | L23283 | 159 code159 | **code159** arg0=`Proximity Detection` |  |
| 2 | L23386 | 159 code159 | **code159** arg0=`Allow Override` |  |
| 3 | L23391 | 137 Stop | **Stop** |  |
| 4 | L23396 | 137 Stop | **Stop** |  |
| 5 | L23401 | 137 Stop | **Stop** |  |
| 6 | L23406 | 549 Variable Clear | **Clear** `%AAB_MainLoop` |  |
| 7 | L23413 | 549 Variable Clear | **Clear** `%AutoBrightRunning` |  |
| 8 | L23420 | 549 Variable Clear | **Clear** `%LuxAlpha` |  |
| 9 | L23427 | 549 Variable Clear | **Clear** `%SmoothedLux` |  |
| 10 | L23288 | 549 Variable Clear | **Clear** `%LastAAB` |  |
| 11 | L23295 | 549 Variable Clear | **Clear** `%AAB_PrevBright` |  |
| 12 | L23302 | 549 Variable Clear | **Clear** `%AAB_NetLocation` |  |
| 13 | L23309 | 547 Variable Set | **Set** `%AAB_Throttle` = `%AAB_AnimSteps*%AAB_MinWait` [DoMaths] |  |
| 14 | L23319 | 49 code49 | **code49** arg0=`AAB Color Filter` |  |
| 15 | L23324 | 130 Perform Task | **Perform Task** "Disable Super Dimming (Privileged)" (pri=%priority) | `%AAB_Privilege != None` |
| 16 | L23347 | 547 Variable Set | **Set** `%AAB_DimmingStatus` = `0` |  |
| 17 | L23357 | 130 Perform Task | **Perform Task** "_ContextResume" (pri=%priority) | `%caller2 != *Manual Override*` **And** `%SCREEN = Off` |

**Variables written:** `%AAB_DimmingStatus`, `%AAB_MainLoop`, `%AAB_NetLocation`, `%AAB_PrevBright`, `%AAB_Throttle`, `%AutoBrightRunning`, `%LastAAB`, `%LuxAlpha`, `%SmoothedLux`

**Variables read:** `%AAB_AnimSteps`, `%AAB_MinWait`
