# task647 — Calculate Super Dimming (Unprivileged) V3

- **XML:** `Advanced_Auto_Brightness_V3.3.prj_9.xml` lines L31290–L31586 (297 lines)
- **Priority:** 100  · **Actions:** 27
- **Action-code histogram:** Variable Set x12, If x4, End If x4, Else/Else-If x3, Perform Task x2, Flash x2

> Auto-transcribed verbatim from XML (entity-decoded). Provenance: each row is `actN` at its XML line. Reads/writes inferred from Variable Set targets and `%var` references.

| act | line | code | detail | condition |
|---|---|---|---|---|
| 0 | L31296 | 37 If | **If** | `%target_brightness < %AAB_DimmingThreshold` |
| 1 | L31307 | 547 Variable Set | **Set** `%target_brightness` = `%AAB_CurrentBright` |  |
| 2 | L31417 | 547 Variable Set | **Set** `%dim_progress` = `(1 - (%target_brightness - %AAB_MinBright) / (%AAB_DimmingThreshold - %AAB_MinBright)) ^ %AAB_DimmingExponent` [DoMaths] |  |
| 3 | L31515 | 547 Variable Set | **Set** `%dim_progress` = `0` | `%dim_progress < 0` |
| 4 | L31532 | 547 Variable Set | **Set** `%dim_progress` = `1` | `%dim_progress > 1` |
| 5 | L31549 | 37 If | **If** | `%AAB_ScalingUse = true` |
| 6 | L31560 | 547 Variable Set | **Set** `%clamped_strength` = `%AAB_DimmingStrength * %AAB_DimDynamic` |  |
| 7 | L31570 | 43 Else/Else-If | **Else/Else-If** |  |
| 8 | L31573 | 547 Variable Set | **Set** `%clamped_strength` = `%AAB_DimmingStrength` |  |
| 9 | L31583 | 38 End If | **End If** |  |
| 10 | L31317 | 37 If | _I'm clamping to 65 in order to prevent screens that are too dark._ — **If** | `%clamped_strength > 65` |
| 11 | L31328 | 547 Variable Set | **Set** `%clamped_strength` = `65` |  |
| 12 | L31338 | 43 Else/Else-If | **Else/Else-If** | `%clamped_strength < 0` |
| 13 | L31348 | 547 Variable Set | **Set** `%clamped_strength` = `0` |  |
| 14 | L31358 | 38 End If | **End If** |  |
| 15 | L31361 | 547 Variable Set | **Set** `%dim_shell` = `%clamped_strength*%dim_progress` [DoMaths] |  |
| 16 | L31371 | 130 Perform Task | **Perform Task** "Apply Dimming (Unprivileged)" (pri=%priority) par1=`%dim_shell` →return `%dim_alpha_dec` |  |
| 17 | L31387 | 547 Variable Set | **Set** `%AAB_DimmingDS` = `%dim_shell` [DoMaths] |  |
| 18 | L31397 | 547 Variable Set | **Set** `%AAB_DimmingCurrent` = `%dim_progress*100` [DoMaths] |  |
| 19 | L31407 | 547 Variable Set | **Set** `%AAB_DimmingStrengthCurr` = `%clamped_strength` [DoMaths] |  |
| 20 | L31427 | 37 If | **If** | `%AAB_ScalingUse = true` |
| 21 | L31437 | 548 Flash | **Flash** `Super dimming effect (rel/abs): %AAB_DimmingCurrent% / %AAB_DimmingDS¶Strength setpoint (max): %AAB_DimmingStrength¶Circadian strength (max): %AAB_DimmingStrengthCurr` | `%AAB_Debug = 5` |
| 22 | L31465 | 43 Else/Else-If | **Else/Else-If** |  |
| 23 | L31468 | 548 Flash | **Flash** `Super dimming effect: %AAB_DimmingCurrent%¶Strength setpoint: %AAB_DimmingStrength` | `%AAB_Debug = 5` |
| 24 | L31495 | 38 End If | **End If** |  |
| 25 | L31498 | 38 End If | **End If** |  |
| 26 | L31501 | 130 Perform Task | **Perform Task** "ARGB To Hex" (pri=0) par1=`%dim_alpha_dec` |  |

**Variables written:** `%AAB_DimmingCurrent`, `%AAB_DimmingDS`, `%AAB_DimmingStrengthCurr`, `%clamped_strength`, `%dim_alpha_dec`, `%dim_progress`, `%dim_shell`, `%target_brightness`

**Variables read:** `%AAB_CurrentBright`, `%AAB_DimDynamic`, `%AAB_DimmingExponent`, `%AAB_DimmingStrength`, `%AAB_DimmingThreshold`, `%AAB_MinBright`, `%clamped_strength`, `%dim_alpha_dec`, `%dim_progress`, `%dim_shell`, `%target_brightness`
