# task650 — Apply Dimming (Privileged)

- **XML:** `Advanced_Auto_Brightness_V3.3.prj_9.xml` lines L31851–L32229 (379 lines)
- **Priority:** 1000  · **Actions:** 40
- **Action-code histogram:** Variable Set x9, If x8, End If x8, Custom Setting x4, Else/Else-If x4, Run Shell x2, code375 x2, Perform Task x1, Flash x1, Stop x1

> Auto-transcribed verbatim from XML (entity-decoded). Provenance: each row is `actN` at its XML line. Reads/writes inferred from Variable Set targets and `%var` references.

| act | line | code | detail | condition |
|---|---|---|---|---|
| 0 | L31858 | 37 If | **If** | `%par1 is set ` |
| 1 | L31868 | 547 Variable Set | **Set** `%dim_shell` = `%par1` |  |
| 2 | L31962 | 38 End If | **End If** |  |
| 3 | L32074 | 37 If | **If** | `%AAB_Privilege = Root` |
| 4 | L32180 | 37 If | **If** | `%AAB_DimmingStatus != 1` |
| 5 | L32190 | 235 Custom Setting | **Custom Setting** arg0=1 arg1=`reduce_bright_colors_activated` arg2=`1` arg3=1 |  |
| 6 | L32198 | 547 Variable Set | **Set** `%AAB_DimmingStatus` = `1` |  |
| 7 | L32208 | 38 End If | **End If** |  |
| 8 | L32211 | 235 Custom Setting | **Custom Setting** arg0=1 arg1=`reduce_bright_colors_level` arg2=`%dim_shell` arg3=1 |  |
| 9 | L32219 | 43 Else/Else-If | **Else/Else-If** | `%AAB_Privilege = Write Secure` |
| 10 | L31878 | 37 If | **If** | `%AAB_DimmingStatus != 1` |
| 11 | L31888 | 235 Custom Setting | **Custom Setting** arg0=1 arg1=`reduce_bright_colors_activated` arg2=`1` |  |
| 12 | L31896 | 547 Variable Set | **Set** `%AAB_DimmingStatus` = `1` |  |
| 13 | L31906 | 38 End If | **End If** |  |
| 14 | L31909 | 235 Custom Setting | **Custom Setting** arg0=1 arg1=`reduce_bright_colors_level` arg2=`%dim_shell` |  |
| 15 | L31917 | 43 Else/Else-If | **Else/Else-If** | `%AAB_Privilege = Shizuku` |
| 16 | L31927 | 37 If | **If** | `%AAB_DimmingStatus != 1` |
| 17 | L31937 | 123 Run Shell | **Run Shell** `settings put secure reduce_bright_colors_activated 1` |  |
| 18 | L31949 | 547 Variable Set | **Set** `%AAB_DimmingStatus` = `1` |  |
| 19 | L31959 | 38 End If | **End If** |  |
| 20 | L31965 | 123 Run Shell | **Run Shell** `settings put secure reduce_bright_colors_level %dim_shell` |  |
| 21 | L31977 | 43 Else/Else-If | **Else/Else-If** | `%AAB_Privilege = ADB WiFi` |
| 22 | L31987 | 37 If | **If** | `%AAB_DimmingStatus != 1` |
| 23 | L31997 | 375 code375 | **code375** arg1=`settings put secure reduce_bright_colors_activated 1` arg4=10 |  |
| 24 | L32014 | 547 Variable Set | **Set** `%AAB_DimmingStatus` = `1` |  |
| 25 | L32024 | 38 End If | **End If** |  |
| 26 | L32027 | 375 code375 | **code375** arg1=`settings put secure reduce_bright_colors_level %dim_shell` arg4=10 |  |
| 27 | L32044 | 38 End If | **End If** |  |
| 28 | L32047 | 547 Variable Set | **Set** `%AAB_DimmingDS` = `%dim_shell` | `%dim_shell is set ` |
| 29 | L32064 | 37 If | **If** | `%dim_progress is set ` |
| 30 | L32084 | 547 Variable Set | **Set** `%AAB_DimmingCurrent` = `%dim_shell*%dim_progress` [DoMaths] |  |
| 31 | L32094 | 43 Else/Else-If | **Else/Else-If** |  |
| 32 | L32097 | 547 Variable Set | **Set** `%AAB_DimmingCurrent` = `%dim_shell` [DoMaths] |  |
| 33 | L32107 | 38 End If | **End If** |  |
| 34 | L32110 | 547 Variable Set | **Set** `%AAB_DimmingStrengthCurr` = `%clamped_strength` [DoMaths] | `%clamped_strength is set ` |
| 35 | L32127 | 37 If | **If** | `%dim_shell is NOT set ` |
| 36 | L32137 | 130 Perform Task | **Perform Task** "Disable Super Dimming (Privileged)" (pri=%priority) |  |
| 37 | L32153 | 548 Flash | **Flash** `Error: super dimming failed. Local variable dim_shell not set (%dim_shell)` |  |
| 38 | L32172 | 137 Stop | **Stop** |  |
| 39 | L32177 | 38 End If | **End If** |  |

**Variables written:** `%AAB_DimmingCurrent`, `%AAB_DimmingDS`, `%AAB_DimmingStatus`, `%AAB_DimmingStrengthCurr`, `%dim_shell`

**Variables read:** `%clamped_strength`, `%dim_progress`, `%dim_shell`, `%par1`
