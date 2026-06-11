# task653 — Apply Dimming (Unprivileged)

- **XML:** `Advanced_Auto_Brightness_V3.3.prj_9.xml` lines L32371–L32526 (156 lines)
- **Priority:** 1000  · **Actions:** 18
- **Action-code histogram:** Variable Set x6, If x3, End If x3, Else/Else-If x2, Flash x1, code49 x1, Stop x1, Return x1

> Auto-transcribed verbatim from XML (entity-decoded). Provenance: each row is `actN` at its XML line. Reads/writes inferred from Variable Set targets and `%var` references.

| act | line | code | detail | condition |
|---|---|---|---|---|
| 0 | L32378 | 37 If | **If** | `%par1 is set ` |
| 1 | L32388 | 547 Variable Set | **Set** `%dim_shell` = `%par1` |  |
| 2 | L32453 | 38 End If | **End If** |  |
| 3 | L32456 | 37 If | **If** | `%dim_shell is set ` |
| 4 | L32466 | 547 Variable Set | **Set** `%dim_alpha_dec` = `2.55*%dim_shell` [DoMaths] |  |
| 5 | L32476 | 547 Variable Set | **Set** `%AAB_DimmingDS` = `%dim_shell` | `%dim_shell is set ` |
| 6 | L32493 | 37 If | **If** | `%dim_progress is set ` |
| 7 | L32503 | 547 Variable Set | **Set** `%AAB_DimmingCurrent` = `%dim_shell*%dim_progress` [DoMaths] |  |
| 8 | L32513 | 43 Else/Else-If | **Else/Else-If** |  |
| 9 | L32516 | 547 Variable Set | **Set** `%AAB_DimmingCurrent` = `%dim_shell` [DoMaths] |  |
| 10 | L32398 | 38 End If | **End If** |  |
| 11 | L32401 | 43 Else/Else-If | **Else/Else-If** |  |
| 12 | L32404 | 548 Flash | **Flash** `Error: super dimming failed. Local variable dim_shell not set (%par1)` |  |
| 13 | L32423 | 547 Variable Set | **Set** `%AAB_DimmingStatus` = `0` |  |
| 14 | L32433 | 49 code49 | **code49** arg0=`AAB Color Filter` |  |
| 15 | L32437 | 137 Stop | **Stop** |  |
| 16 | L32442 | 38 End If | **End If** |  |
| 17 | L32445 | 126 Return | **Return** `%dim_alpha_dec` |  |

**Variables written:** `%AAB_DimmingCurrent`, `%AAB_DimmingDS`, `%AAB_DimmingStatus`, `%dim_alpha_dec`, `%dim_shell`

**Variables read:** `%dim_progress`, `%dim_shell`, `%par1`
