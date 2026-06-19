# task698 — Smooth DC-Like Brightness Transition V5 (Java)

- **XML:** `Advanced_Auto_Brightness_V3.3.prj_9.xml` lines L35949–L36451 (503 lines)
- **Priority:** 100  · **Actions:** 24
- **Action-code histogram:** Variable Set x10, Array Push x2, Else/Else-If x2, End If x2, code159 x2, If x2, code389 x1, Set Display Brightness x1, Java Code x1, Flash x1

> Auto-transcribed verbatim from XML (entity-decoded). Provenance: each row is `actN` at its XML line. Reads/writes inferred from Variable Set targets and `%var` references.

| act | line | code | detail | condition |
|---|---|---|---|---|
| 0 | L35956 | 547 Variable Set | **Set** `%AutoBrightRunning` = `1` | `%AutoBrightRunning = 0` **Or** `%AutoBrightRunning is NOT set ` |
| 1 | L35979 | 590 Array Push | **Array Push** arg0=`%par1` arg1=`,` |  |
| 2 | L36326 | 547 Variable Set | **Set** `%calculated_brightness` = `%par11` |  |
| 3 | L36384 | 547 Variable Set | **Set** `%final_dim` = `%par12` |  |
| 4 | L36394 | 590 Array Push | **Array Push** arg0=`%par2` arg1=`,` |  |
| 5 | L36401 | 547 Variable Set | **Set** `%loops` = `%par21` |  |
| 6 | L36411 | 547 Variable Set | **Set** `%wait` = `%par22` |  |
| 7 | L36421 | 547 Variable Set | **Set** `%dim_start` = `%AAB_DimmingDS` |  |
| 8 | L36431 | 37 If | **If** | `%AAB_PrevBright is NOT set ` |
| 9 | L36441 | 547 Variable Set | **Set** `%currentbrightness` = `%AAB_DimmingThreshold` [DoMaths] |  |
| 10 | L35986 | 43 Else/Else-If | **Else/Else-If** |  |
| 11 | L35989 | 547 Variable Set | **Set** `%currentbrightness` = `%AAB_PrevBright` [DoMaths] |  |
| 12 | L35999 | 38 End If | **End If** |  |
| 13 | L36002 | 389 code389 | **code389** arg1=`%AAB_LastAnimation=%TIMEMS¶%AAB_CycleTime=%TIMEMS - %AAB_Cyc` arg4=`=` arg5=1 arg6=3 arg8=1 |  |
| 14 | L36020 | 159 code159 | **code159** arg0=`Allow Override` |  |
| 15 | L36026 | 810 Set Display Brightness | **Set Display Brightness** arg0=`%AAB_DimmingThreshold` | `%BRIGHT != %AAB_DimmingThreshold` |
| 16 | L36042 | 474 Java Code | **Java Code** → `` (see `_source/java/task698_*.java.txt`) |  |
| 17 | L36302 | 37 If | _Shows 1 decimal point in the scene below %AAB_DimmingThreshold lux and is used for higher accuracy in super dimming._ — **If** | `%calculated_brightness_smooth < %AAB_DimmingThreshold` |
| 18 | L36313 | 547 Variable Set | **Set** `%AAB_PrevBright` = `%calculated_brightness_smooth` [DoMaths] |  |
| 19 | L36323 | 43 Else/Else-If | **Else/Else-If** |  |
| 20 | L36336 | 547 Variable Set | **Set** `%AAB_PrevBright` = `%calculated_brightness_smooth` [DoMaths] |  |
| 21 | L36346 | 38 End If | **End If** |  |
| 22 | L36349 | 159 code159 | **code159** arg0=`Allow Override` arg1=1 |  |
| 23 | L36355 | 548 Flash | **Flash** `Software dimming¶Loops: %loops, wait step: %wait ms¶Java loop duration: %java_step_duration ms¶Target brightness: %AAB_CurrentBright` | `%AAB_Debug = 2` |

**Variables written:** `%AAB_PrevBright`, `%AutoBrightRunning`, `%calculated_brightness`, `%currentbrightness`, `%dim_start`, `%final_dim`, `%loops`, `%wait`

**Variables read:** `%AAB_DimmingDS`, `%AAB_DimmingThreshold`, `%AAB_PrevBright`, `%calculated_brightness_smooth`, `%par11`, `%par12`, `%par21`, `%par22`
