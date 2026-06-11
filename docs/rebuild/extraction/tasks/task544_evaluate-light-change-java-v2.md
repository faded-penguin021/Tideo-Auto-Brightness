# task544 — Evaluate Light Change (Java) V2

- **XML:** `Advanced_Auto_Brightness_V3.3.prj_9.xml` lines L15929–L16423 (495 lines)
- **Priority:** 100  · **Actions:** 41
- **Action-code histogram:** Variable Set x12, Stop x5, If x5, Perform Task x5, End If x5, code389 x3, Java Code x1, Variable Clear x1, Array Push x1, Else/Else-If x1, Wait Until x1, Flash x1

> Auto-transcribed verbatim from XML (entity-decoded). Provenance: each row is `actN` at its XML line. Reads/writes inferred from Variable Set targets and `%var` references.

| act | line | code | detail | condition |
|---|---|---|---|---|
| 0 | L15936 | 137 Stop | **Stop** | `%par1 is NOT set ` |
| 1 | L15948 | 137 Stop | **Stop** | `%TRUN = *Map Lux To Brightness*` **Or** `%TRUN = *Smooth Brightness Transition*` **Or** `%TRUN = *Process Sensor Event*` |
| 2 | L16114 | 389 code389 | **code389** arg1=`%now=%TIMEMS¶%min_interval=%AAB_Throttle` arg4=`=` arg5=1 arg6=3 |  |
| 3 | L16234 | 37 If | **If** | `%LastAAB is set ` |
| 4 | L16365 | 547 Variable Set | **Set** `%elapsed_time` = `%now - %LastAAB` [DoMaths] |  |
| 5 | L16392 | 37 If | **If** | `%elapsed_time < %min_interval` |
| 6 | L16402 | 547 Variable Set | **Set** `%AAB_MainLoop` = `0` |  |
| 7 | L16412 | 137 Stop | **Stop** |  |
| 8 | L16417 | 38 End If | **End If** |  |
| 9 | L16420 | 38 End If | **End If** |  |
| 10 | L15972 | 37 If | **If** | `%SmoothedLux is NOT set ` **And** `%AutoBrightRunning != 1` |
| 11 | L15988 | 547 Variable Set | **Set** `%SmoothedLux` = `%par1` [DoMaths] |  |
| 12 | L15998 | 547 Variable Set | **Set** `%LuxAlpha` = `1` |  |
| 13 | L16008 | 547 Variable Set | **Set** `%LastAAB` = `%now` |  |
| 14 | L16018 | 389 code389 | **code389** arg1=`%AAB_ThreshDynamic=(%dynamic_threshold)*100¶%AAB_ThreshAbsLo` arg4=`=` arg5=1 |  |
| 15 | L16037 | 130 Perform Task | **Perform Task** "Map Lux to Brightness (Java) V2" (pri=%priority+1) par1=`%SmoothedLux` par2=`%LuxAlpha` |  |
| 16 | L16053 | 137 Stop | **Stop** |  |
| 17 | L16058 | 38 End If | **End If** |  |
| 18 | L16061 | 474 Java Code | **Java Code** → `` (see `java/task544_*.java`) |  |
| 19 | L16104 | 37 If | **If** | `%relative_change < %dynamic_threshold` |
| 20 | L16132 | 130 Perform Task | **Perform Task** "Set Thresholds (Java)" (pri=%priority) par1=`%par1` par2=`%dynamic_threshold` |  |
| 21 | L16148 | 549 Variable Clear | **Clear** `%AAB_CycleStart` |  |
| 22 | L16155 | 547 Variable Set | **Set** `%AAB_MainLoop` = `0` |  |
| 23 | L16165 | 137 Stop | **Stop** |  |
| 24 | L16170 | 38 End If | **End If** |  |
| 25 | L16173 | 130 Perform Task | **Perform Task** "Lux Smoothing (Java)" (pri=%priority) par1=`%par1` par2=`%SmoothedLux` →return `%lux_results` |  |
| 26 | L16189 | 590 Array Push | **Array Push** arg0=`%lux_results` arg1=`,` arg3=1 |  |
| 27 | L16196 | 389 code389 | **code389** arg1=`%new_smoothed_lux=%lux_results1¶%SmoothedLux=%lux_results1` arg4=`=` arg6=3 |  |
| 28 | L16214 | 37 If | **If** | `%AAB_Proximity = near` |
| 29 | L16224 | 547 Variable Set | **Set** `%LuxAlpha` = `%lux_results2*0.1` [DoMaths] |  |
| 30 | L16244 | 43 Else/Else-If | **Else/Else-If** |  |
| 31 | L16247 | 547 Variable Set | **Set** `%LuxAlpha` = `%lux_results2` |  |
| 32 | L16257 | 38 End If | **End If** |  |
| 33 | L16260 | 130 Perform Task | **Perform Task** "Map Lux to Brightness (Java) V2" (pri=%priority) par1=`%new_smoothed_lux` par2=`%lux_results2` |  |
| 34 | L16276 | 547 Variable Set | **Set** `%LastAAB` = `%now` |  |
| 35 | L16286 | 130 Perform Task | **Perform Task** "Set Thresholds (Java)" (pri=%priority) par1=`%new_smoothed_lux` par2=`%dynamic_threshold` |  |
| 36 | L16302 | 35 Wait Until | **Wait Until** arg0=100 | `%TRUN != *Smooth Brightness Transition*` |
| 37 | L16319 | 547 Variable Set | **Set** `%AutoBrightRunning` = `0` |  |
| 38 | L16329 | 547 Variable Set | **Set** `%AAB_MainLoop` = `Off` |  |
| 39 | L16339 | 548 Flash | **Flash** `Relative change %relative_change > Dynamic threshold %dynamic_threshold. Updating.` | `%AAB_Debug = 3` |
| 40 | L16375 | 547 Variable Set | **Set** `%AAB_CycleTotal` = `%TIMEMS-%AAB_CycleStart` [DoMaths] | `%AAB_Debug != 1` |

**Variables written:** `%AAB_CycleStart`, `%AAB_CycleTotal`, `%AAB_MainLoop`, `%AutoBrightRunning`, `%LastAAB`, `%LuxAlpha`, `%SmoothedLux`, `%elapsed_time`, `%lux_results`

**Variables read:** `%AAB_CycleStart`, `%LastAAB`, `%SmoothedLux`, `%TIMEMS`, `%lux_results2`, `%new_smoothed_lux`, `%now`, `%par1`
