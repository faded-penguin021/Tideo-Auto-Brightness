# task618 — Set Initial Brightness (Java) V3

- **XML:** `Advanced_Auto_Brightness_V3.3.prj_9.xml` lines L25754–L26280 (527 lines)
- **Priority:** 8  · **Actions:** 43
- **Action-code histogram:** Variable Set x10, Perform Task x6, End If x5, code159 x5, If x5, Variable Clear x3, Java Code x2, code779 x2, code888 x1, Stop(?) x1, Variable Search Replace x1, code300 x1, code373 x1

> Auto-transcribed verbatim from XML (entity-decoded). Provenance: each row is `actN` at its XML line. Reads/writes inferred from Variable Set targets and `%var` references.

| act | line | code | detail | condition |
|---|---|---|---|---|
| 0 | L25760 | 547 Variable Set | _This flag prevents this task from being flagged as a manual brightness change._ — **Set** `%AAB_Initializing` = `true` |  |
| 1 | L25771 | 549 Variable Clear | **Clear** `%AAB_Manual_Override` | `%AAB_Manual_Override is set ` |
| 2 | L25893 | 130 Perform Task | **Perform Task** "Initialize AAB Defaults" (pri=%priority) | `%AAB_SetupComplete !~ (no match) 1` |
| 3 | L26012 | 547 Variable Set | **Set** `%counter` = `0` |  |
| 4 | L26175 | 37 If | **If** | `%caller1 != *_EvaluateContexts*` **And** `%caller1 != *_ProfileManager*` |
| 5 | L26210 | 130 Perform Task | **Perform Task** "_EvaluateContexts V2" (pri=%priority+1) |  |
| 6 | L26226 | 38 End If | **End If** |  |
| 7 | L26229 | 300 code300 | _top_of_loop_ — **code300**  |  |
| 8 | L26233 | 373 code373 | **code373** arg1=`5` arg2=%AAB_DefaultThrottle |  |
| 9 | L26252 | 37 If | **If** | `%as_accuracy < 2` **And** `%AAB_TrustUnreliable = Off` **And** `%counter < 7` **And** `%err is NOT set ` |
| 10 | L25785 | 888 code888 | **code888** arg0=`%counter` arg1=1 |  |
| 11 | L25791 | 549 Variable Clear | **Clear** `%as_values1` |  |
| 12 | L25798 | 135 Stop(?) | **Stop(?)** arg0=1 arg1=1 arg2=`top_of_loop` |  |
| 13 | L25804 | 38 End If | **End If** |  |
| 14 | L25807 | 598 Variable Search Replace | **Var Search/Replace** in `%as_values1` arg0=`%as_values1` arg1=`^\[(.+)\]$` arg6=1 arg7=`$1` | `%as_values1 is set ` |
| 15 | L25825 | 474 Java Code | **Java Code** → `` (see `_source/java/task618_*.java.txt`) |  |
| 16 | L25849 | 547 Variable Set | _This skips animations during Map Lux to Brightness_ — **Set** `%orig_debug` = `1` | `%AAB_PWMSensitive != true` |
| 17 | L25867 | 547 Variable Set | _This skips smoothing and ensures the new value is set with a maximum alpha. Passed as %par2 into Map Lux To Brightness task._ — **Set** `%LuxAlpha` = `1` |  |
| 18 | L25878 | 159 code159 | **code159** arg0=`Monitor Ambient Light` |  |
| 19 | L25883 | 547 Variable Set | **Set** `%AAB_CycleStart` = `%TIMEMS` |  |
| 20 | L25916 | 130 Perform Task | **Perform Task** "Map Lux to Brightness (Java) V2" (pri=%priority) par1=`%smoothed_lux` par2=`%LuxAlpha` |  |
| 21 | L25932 | 547 Variable Set | **Set** `%LastAAB` = `%TIMEMS` |  |
| 22 | L25942 | 37 If | **If** | `%AAB_NotifyUse = true` |
| 23 | L25952 | 779 code779 | **code779** arg0=`Advanced Auto Brightness Paused` |  |
| 24 | L25957 | 779 code779 | **code779** arg0=`%AAB_SetupTitle` |  |
| 25 | L25962 | 38 End If | **End If** |  |
| 26 | L25965 | 37 If | **If** | `%AAB_ScalingUse = true` |
| 27 | L25976 | 130 Perform Task | **Perform Task** "Dynamic Scale V13 (Java) App Version" (pri=%priority+2) |  |
| 28 | L25992 | 38 End If | **End If** |  |
| 29 | L25995 | 37 If | **If** | `%AAB_DimmingEnabled = true` **And** `%AAB_PWMSensitive != true` |
| 30 | L26022 | 130 Perform Task | **Perform Task** "Dimming Decider" (pri=%priority+1) |  |
| 31 | L26038 | 38 End If | **End If** |  |
| 32 | L26041 | 547 Variable Set | **Set** `%AutoBrightRunning` = `0` |  |
| 33 | L26051 | 547 Variable Set | **Set** `%AAB_Throttle` = `%AAB_AnimSteps * %AAB_MaxWait + 10` [DoMaths] |  |
| 34 | L26061 | 547 Variable Set | **Set** `%AAB_LastSensorAccuracy` = `%as_accuracy` | `%as_accuracy is set ` |
| 35 | L26078 | 547 Variable Set | **Set** `%AAB_LastRawLux` = `%as_values1` | `%as_values1 is set ` |
| 36 | L26095 | 474 Java Code | _This prevents Monitor Ambient Light profile from interfering from the brightness set in this task._ — **Java Code** → `` (see `_source/java/task618_*.java.txt`) | `%AAB_DefaultThrottle is set ` |
| 37 | L26124 | 130 Perform Task | **Perform Task** "_ContextF5NetLoc V8" (pri=%priority) | `%AAB_Manual_Override != true` **And** `%AAB_Service = On` **And** `%AAB_ContextCache = *[LOC]*` **And** `%AAB_ContextOverride != true` |
| 38 | L26165 | 159 code159 | **code159** arg0=`Monitor Ambient Light` arg1=1 |  |
| 39 | L26170 | 159 code159 | **code159** arg0=`Proximity Detection` arg1=1 |  |
| 40 | L26191 | 159 code159 | **code159** arg0=`Dynamic Scale Engine` arg1=1 |  |
| 41 | L26197 | 159 code159 | **code159** arg0=`Allow Override` arg1=1 |  |
| 42 | L26203 | 549 Variable Clear | **Clear** `%AAB_Initializing` |  |

**Variables written:** `%AAB_CycleStart`, `%AAB_Initializing`, `%AAB_LastRawLux`, `%AAB_LastSensorAccuracy`, `%AAB_Manual_Override`, `%AAB_Throttle`, `%AutoBrightRunning`, `%LastAAB`, `%LuxAlpha`, `%as_values1`, `%counter`, `%orig_debug`

**Variables read:** `%AAB_AnimSteps`, `%AAB_MaxWait`, `%TIMEMS`, `%as_accuracy`, `%as_values1`, `%smoothed_lux`
