# task561 — Process Overrides

- **XML:** `Advanced_Auto_Brightness_V3.3.prj_9.xml` lines L19385–L19521 (137 lines)
- **Priority:** 100  · **Actions:** 17
- **Action-code histogram:** Variable Set x5, If x3, End If x3, code355 x2, code300 x1, code356 x1, Stop(?) x1, Else/Else-If x1

> Auto-transcribed verbatim from XML (entity-decoded). Provenance: each row is `actN` at its XML line. Reads/writes inferred from Variable Set targets and `%var` references.

| act | line | code | detail | condition |
|---|---|---|---|---|
| 0 | L19391 | 37 If | **If** | `%AAB_ScalingUse = true` **And** `%AAB_ScaleDynamicCompress != 0` |
| 1 | L19407 | 547 Variable Set | **Set** `%ideal_base_brightness` = `%BRIGHT / %AAB_ScaleDynamicCompress` [DoMaths] |  |
| 2 | L19461 | 547 Variable Set | **Set** `%rounded_lux` = ` %SmoothedLux` [DoMaths] |  |
| 3 | L19471 | 37 If | **If** | `%ideal_base_brightness > 255` |
| 4 | L19481 | 547 Variable Set | **Set** `%ideal_base_brightness` = `255` [DoMaths] |  |
| 5 | L19491 | 38 End If | **End If** |  |
| 6 | L19494 | 355 code355 | **code355** arg0=`%AAB_Overrides` arg1=1 arg2=`%rounded_lux,%ideal_base_brightness` |  |
| 7 | L19501 | 43 Else/Else-If | **Else/Else-If** |  |
| 8 | L19504 | 547 Variable Set | **Set** `%rounded_lux` = ` %SmoothedLux` [DoMaths] |  |
| 9 | L19514 | 355 code355 | **code355** arg0=`%AAB_Overrides` arg1=1 arg2=`%rounded_lux,%BRIGHT` |  |
| 10 | L19417 | 38 End If | **End If** |  |
| 11 | L19420 | 300 code300 | _top_of_loop_ — **code300**  |  |
| 12 | L19424 | 37 If | **If** | `%AAB_Overrides(#) > 50` |
| 13 | L19434 | 547 Variable Set | **Set** `%last_index` = `%AAB_Overrides(#)` |  |
| 14 | L19444 | 356 code356 | **code356** arg0=`%AAB_Overrides` arg1=%last_index |  |
| 15 | L19452 | 135 Stop(?) | **Stop(?)** arg0=1 arg1=1 arg2=`top_of_loop` |  |
| 16 | L19458 | 38 End If | **End If** |  |

**Variables written:** `%ideal_base_brightness`, `%last_index`, `%rounded_lux`

**Variables read:** `%AAB_Overrides`, `%AAB_ScaleDynamicCompress`, `%BRIGHT`, `%SmoothedLux`
