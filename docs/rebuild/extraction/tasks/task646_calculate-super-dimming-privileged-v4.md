# task646 — Calculate Super Dimming (Privileged) V4

- **XML:** `Advanced_Auto_Brightness_V3.3.prj_9.xml` lines L31026–L31289 (264 lines)
- **Priority:** 100  · **Actions:** 24
- **Action-code histogram:** Variable Set x9, If x4, End If x4, Else/Else-If x3, Flash x2, Stop x1, Perform Task x1

> Auto-transcribed verbatim from XML (entity-decoded). Provenance: each row is `actN` at its XML line. Reads/writes inferred from Variable Set targets and `%var` references.

| act | line | code | detail | condition |
|---|---|---|---|---|
| 0 | L31032 | 137 Stop | **Stop** | `%AAB_DimmingEnabled != true` |
| 1 | L31044 | 37 If | **If** | `%AAB_CurrentBright < %AAB_DimmingThreshold` |
| 2 | L31140 | 547 Variable Set | **Set** `%target_brightness` = `%AAB_CurrentBright` |  |
| 3 | L31211 | 547 Variable Set | **Set** `%dim_progress` = `(1 - (%target_brightness - %AAB_MinBright) / (%AAB_DimmingThreshold - %AAB_MinBright)) ^ %AAB_DimmingExponent` [DoMaths] |  |
| 4 | L31221 | 547 Variable Set | **Set** `%dim_progress` = `0` | `%dim_progress < 0` |
| 5 | L31238 | 547 Variable Set | **Set** `%dim_progress` = `1` | `%dim_progress > 1` |
| 6 | L31255 | 37 If | **If** | `%AAB_ScalingUse = true` |
| 7 | L31266 | 547 Variable Set | **Set** `%clamped_strength` = `%AAB_DimmingStrength * %AAB_DimDynamic` |  |
| 8 | L31276 | 43 Else/Else-If | **Else/Else-If** |  |
| 9 | L31279 | 547 Variable Set | **Set** `%clamped_strength` = `%AAB_DimmingStrength` |  |
| 10 | L31054 | 38 End If | **End If** |  |
| 11 | L31057 | 37 If | _I'm clamping to 65 in order to prevent screens that are too dark._ — **If** | `%clamped_strength > 65` |
| 12 | L31068 | 547 Variable Set | **Set** `%clamped_strength` = `65` |  |
| 13 | L31078 | 43 Else/Else-If | **Else/Else-If** | `%clamped_strength < 0` |
| 14 | L31088 | 547 Variable Set | **Set** `%clamped_strength` = `0` |  |
| 15 | L31098 | 38 End If | **End If** |  |
| 16 | L31101 | 547 Variable Set | **Set** `%dim_shell` = `%clamped_strength*%dim_progress` [DoMaths] |  |
| 17 | L31111 | 130 Perform Task | **Perform Task** "Apply Dimming (Privileged)" (pri=%priority) |  |
| 18 | L31127 | 38 End If | **End If** |  |
| 19 | L31130 | 37 If | **If** | `%AAB_ScalingUse = true` |
| 20 | L31150 | 548 Flash | **Flash** `Super dimming effect (rel/abs): %AAB_DimmingCurrent% / %AAB_DimmingDS¶Strength setpoint (max): %AAB_DimmingStrength¶Circadian strength (max): %AAB_DimmingStrengthCurr` | `%AAB_Debug = 5` |
| 21 | L31178 | 43 Else/Else-If | **Else/Else-If** |  |
| 22 | L31181 | 548 Flash | **Flash** `Super dimming effect: %AAB_DimmingCurrent%¶Strength setpoint: %AAB_DimmingStrength` | `%AAB_Debug = 5` |
| 23 | L31208 | 38 End If | **End If** |  |

**Variables written:** `%clamped_strength`, `%dim_progress`, `%dim_shell`, `%target_brightness`

**Variables read:** `%AAB_CurrentBright`, `%AAB_DimDynamic`, `%AAB_DimmingExponent`, `%AAB_DimmingStrength`, `%AAB_DimmingThreshold`, `%AAB_MinBright`, `%clamped_strength`, `%dim_progress`, `%target_brightness`
