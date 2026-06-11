# task661 — Map Lux to Brightness (Java) V2

- **XML:** `Advanced_Auto_Brightness_V3.3.prj_9.xml` lines L33382–L33891 (510 lines)
- **Priority:** 100  · **Actions:** 52
- **Action-code histogram:** Variable Set x16, Perform Task x9, If x8, End If x8, Else/Else-If x6, Stop x2, Variable Clear x1, Set Display Brightness x1, code159 x1

> Auto-transcribed verbatim from XML (entity-decoded). Provenance: each row is `actN` at its XML line. Reads/writes inferred from Variable Set targets and `%var` references.

| act | line | code | detail | condition |
|---|---|---|---|---|
| 0 | L33388 | 547 Variable Set | **Set** `%AutoBrightRunning` = `1` |  |
| 1 | L33398 | 547 Variable Set | **Set** `%smoothed_lux` = `%par1` |  |
| 2 | L33493 | 547 Variable Set | **Set** `%lux_alpha` = `%par2` |  |
| 3 | L33633 | 37 If | **If** | `%smoothed_lux < %AAB_Zone1End` |
| 4 | L33732 | 547 Variable Set | **Set** `%mapped_brightness` = `%AAB_Form1A*(sqrt(%par1))` [DoMaths] |  |
| 5 | L33834 | 43 Else/Else-If | **Else/Else-If** | `%smoothed_lux < %AAB_Zone2End` |
| 6 | L33865 | 547 Variable Set | **Set** `%mapped_brightness` = `%AAB_Form2A + %AAB_Form2B*((%par1-%AAB_Form2C)^0.33 - (%AAB_Form2D-%AAB_Form2C)^0.33)` [DoMaths] |  |
| 7 | L33875 | 43 Else/Else-If | **Else/Else-If** |  |
| 8 | L33878 | 547 Variable Set | **Set** `%mapped_brightness` = `%AAB_MaxBright - (%AAB_Form3A/%par1)*%AAB_MaxBright` [DoMaths] |  |
| 9 | L33888 | 38 End If | **End If** |  |
| 10 | L33408 | 37 If | **If** | `%AAB_ScalingUse = true` |
| 11 | L33418 | 130 Perform Task | **Perform Task** "Dynamic Range Compressed Scale (Java) V2" (pri=%priority) par1=`%mapped_brightness` →return `%dr_results` |  |
| 12 | L33434 | 547 Variable Set | **Set** `%calculated_brightness` = `%dr_results` |  |
| 13 | L33444 | 43 Else/Else-If | **Else/Else-If** |  |
| 14 | L33447 | 547 Variable Set | **Set** `%calculated_brightness` = `%mapped_brightness * %AAB_Scale + %AAB_Offset` [DoMaths] |  |
| 15 | L33457 | 38 End If | **End If** |  |
| 16 | L33460 | 37 If | **If** | `%calculated_brightness < %AAB_MinBright` |
| 17 | L33470 | 547 Variable Set | **Set** `%calculated_brightness` = `%AAB_MinBright` |  |
| 18 | L33480 | 38 End If | **End If** |  |
| 19 | L33483 | 37 If | **If** | `%calculated_brightness > %AAB_MaxBright` |
| 20 | L33503 | 547 Variable Set | **Set** `%calculated_brightness` = `%AAB_MaxBright` |  |
| 21 | L33513 | 38 End If | **End If** |  |
| 22 | L33516 | 37 If | **If** | `%AAB_PWMSensitive = true` **And** `%calculated_brightness < %AAB_DimmingThreshold` |
| 23 | L33532 | 130 Perform Task | **Perform Task** "Software Dimming V2" (pri=%priority) par1=`%calculated_brightness` →return `%final_dim` |  |
| 24 | L33548 | 130 Perform Task | **Perform Task** "Calculate Animation (Java) V2" (pri=%priority) par1=`%lux_alpha` →return `%calculate_results` |  |
| 25 | L33564 | 130 Perform Task | **Perform Task** "Smooth DC-Like Brightness Transition V5 (Java)" (pri=%priority) par1=`%calculated_brightness,%final_dim` par2=`%calculate_results` |  |
| 26 | L33580 | 137 Stop | **Stop** |  |
| 27 | L33585 | 43 Else/Else-If | **Else/Else-If** | `%AAB_PWMSensitive = true` **And** `%AAB_DimmingStatus = 1` **And** `%calculated_brightness > %AAB_DimmingThreshold` |
| 28 | L33607 | 37 If | **If** | `%AAB_Privilege != None` |
| 29 | L33617 | 130 Perform Task | **Perform Task** "Disable Super Dimming (Privileged)" (pri=%priority) |  |
| 30 | L33643 | 43 Else/Else-If | **Else/Else-If** |  |
| 31 | L33646 | 130 Perform Task | **Perform Task** "Disable Super Dimming (Unprivileged)" (pri=%priority) |  |
| 32 | L33662 | 38 End If | **End If** |  |
| 33 | L33665 | 547 Variable Set | **Set** `%AAB_DimmingCurrent` = `0` |  |
| 34 | L33675 | 547 Variable Set | **Set** `%AAB_DimmingDS` = `0` |  |
| 35 | L33685 | 549 Variable Clear | **Clear** `%AAB_PrevBright` |  |
| 36 | L33692 | 547 Variable Set | **Set** `%AAB_DimmingStatus` = `0` |  |
| 37 | L33702 | 38 End If | **End If** |  |
| 38 | L33705 | 37 If | **If** | `%AAB_Debug = 1` **Or** `%orig_debug = 1` |
| 39 | L33721 | 37 If | _Shows 1 decimal point in the scene below %AAB_DimmingThreshold lux and is used for higher accuracy in super dimming._ — **If** | `%calculated_brightness < %AAB_DimmingThreshold` |
| 40 | L33742 | 547 Variable Set | **Set** `%AAB_CurrentBright` = `%calculated_brightness` [DoMaths] |  |
| 41 | L33752 | 43 Else/Else-If | **Else/Else-If** |  |
| 42 | L33755 | 547 Variable Set | **Set** `%AAB_CurrentBright` = `%calculated_brightness` [DoMaths] |  |
| 43 | L33765 | 38 End If | **End If** |  |
| 44 | L33768 | 130 Perform Task | **Perform Task** "Dimming Decider" (pri=%priority) |  |
| 45 | L33784 | 810 Set Display Brightness | **Set Display Brightness** arg0=`%calculated_brightness` |  |
| 46 | L33793 | 547 Variable Set | **Set** `%AutoBrightRunning` = `0` | `%caller1 != *Set Initial Brightness*` |
| 47 | L33810 | 137 Stop | **Stop** |  |
| 48 | L33815 | 38 End If | **End If** |  |
| 49 | L33818 | 130 Perform Task | **Perform Task** "Calculate Animation (Java) V2" (pri=%priority) par1=`%lux_alpha` →return `%calculate_results` |  |
| 50 | L33844 | 159 code159 | **code159** arg0=`Throttle Reinitialization` arg1=1 |  |
| 51 | L33849 | 130 Perform Task | **Perform Task** "Smooth Brightness Transition V5 (Java)" (pri=%priority) par1=`%calculated_brightness` par2=`%calculate_results` |  |

**Variables written:** `%AAB_CurrentBright`, `%AAB_DimmingCurrent`, `%AAB_DimmingDS`, `%AAB_DimmingStatus`, `%AAB_PrevBright`, `%AutoBrightRunning`, `%calculate_results`, `%calculated_brightness`, `%dr_results`, `%final_dim`, `%lux_alpha`, `%mapped_brightness`, `%smoothed_lux`

**Variables read:** `%AAB_Form1A`, `%AAB_Form2A`, `%AAB_Form2B`, `%AAB_Form2C`, `%AAB_Form2D`, `%AAB_Form3A`, `%AAB_MaxBright`, `%AAB_MinBright`, `%AAB_Offset`, `%AAB_Scale`, `%calculated_brightness`, `%dr_results`, `%final_dim`, `%lux_alpha`, `%mapped_brightness`, `%par1`, `%par2`
