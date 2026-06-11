# task566 — Reset Throttle

- **XML:** `Advanced_Auto_Brightness_V3.3.prj_9.xml` lines L20427–L20524 (98 lines)
- **Priority:** 6  · **Actions:** 11
- **Action-code histogram:** Variable Set x4, If x3, End If x3, code159 x1

> Auto-transcribed verbatim from XML (entity-decoded). Provenance: each row is `actN` at its XML line. Reads/writes inferred from Variable Set targets and `%var` references.

| act | line | code | detail | condition |
|---|---|---|---|---|
| 0 | L20433 | 547 Variable Set | **Set** `%AAB_DefaultThrottle` = `%AAB_AnimSteps * %AAB_MaxWait + 10` [DoMaths] |  |
| 1 | L20443 | 37 If | **If** | `%LastAAB is set ` |
| 2 | L20456 | 547 Variable Set | **Set** `%elapsed_time` = `%TIMEMS-%LastAAB` [DoMaths] |  |
| 3 | L20466 | 38 End If | **End If** |  |
| 4 | L20469 | 37 If | **If** | `%AAB_DefaultThrottle < 1000` |
| 5 | L20479 | 547 Variable Set | **Set** `%AAB_DefaultThrottle` = `1000` [DoMaths] |  |
| 6 | L20489 | 38 End If | **End If** |  |
| 7 | L20492 | 37 If | **If** | `%elapsed_time > 9999` **And** `%AAB_Throttle >= %default_throttle` |
| 8 | L20508 | 547 Variable Set | _After a brightness animation ends, the throttle is stuck at a low value (in unchanging light). This "unsticks" the throttle and prevents additional runs of the Evaluate Light Change task. However, that profile shouldn't run anyway due to the boolean logic in the profile triggers. Perhaps this profile and task are redundant._ — **Set** `%AAB_Throttle` = `%AAB_DefaultThrottle` |  |
| 9 | L20519 | 159 code159 | **code159** arg0=`Throttle Reinitialization` |  |
| 10 | L20453 | 38 End If | **End If** |  |

**Variables written:** `%AAB_DefaultThrottle`, `%AAB_Throttle`, `%elapsed_time`

**Variables read:** `%AAB_AnimSteps`, `%AAB_DefaultThrottle`, `%AAB_MaxWait`, `%LastAAB`, `%TIMEMS`
