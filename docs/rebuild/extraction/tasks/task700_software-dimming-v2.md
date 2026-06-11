# task700 — Software Dimming V2

- **XML:** `Advanced_Auto_Brightness_V3.3.prj_9.xml` lines L36474–L36712 (239 lines)
- **Priority:** 100  · **Actions:** 26
- **Action-code histogram:** Variable Set x13, If x5, End If x5, Else/Else-If x2, Return x1

> Auto-transcribed verbatim from XML (entity-decoded). Provenance: each row is `actN` at its XML line. Reads/writes inferred from Variable Set targets and `%var` references.

| act | line | code | detail | condition |
|---|---|---|---|---|
| 0 | L36480 | 37 If | **If** | `%AAB_Privilege != None` |
| 1 | L36490 | 547 Variable Set | **Set** `%max_dim` = `99` |  |
| 2 | L36587 | 43 Else/Else-If | **Else/Else-If** |  |
| 3 | L36654 | 547 Variable Set | _This is 99% on a 0-255 scale_ — **Set** `%max_dim` = `252.45` |  |
| 4 | L36665 | 38 End If | **End If** |  |
| 5 | L36668 | 547 Variable Set | **Set** `%safe_thresh` = `%AAB_DimmingThreshold` [DoMaths] |  |
| 6 | L36678 | 37 If | **If** | `%safe_thresh < 1` |
| 7 | L36688 | 547 Variable Set | **Set** `%safe_thresh` = `1` |  |
| 8 | L36698 | 38 End If | **End If** |  |
| 9 | L36701 | 547 Variable Set | _This constant determines how dark the screen gets at 0 brightness. Default = 95% opaque as a maximum value._ — **Set** `%dark_floor` = `0.95` |  |
| 10 | L36500 | 547 Variable Set | **Set** `%k_factor` = `(1 - %dark_floor) ^ (1 / %AAB_PWMExp)` [DoMaths] |  |
| 11 | L36510 | 547 Variable Set | **Set** `%bias` = `(%k_factor * %safe_thresh) / (1 - %k_factor)` [DoMaths] |  |
| 12 | L36520 | 37 If | **If** | `%bias < 10` |
| 13 | L36530 | 547 Variable Set | _Bias is necessary because a screen at 0 brightness should not equal a black screen_ — **Set** `%bias` = `10` |  |
| 14 | L36541 | 38 End If | **End If** |  |
| 15 | L36544 | 547 Variable Set | **Set** `%ratio` = `(%par1 + %bias) / (%safe_thresh + %bias)` [DoMaths] |  |
| 16 | L36554 | 37 If | **If** | `%ratio > 1` |
| 17 | L36564 | 547 Variable Set | **Set** `%ratio` = `1` |  |
| 18 | L36574 | 38 End If | **End If** |  |
| 19 | L36577 | 547 Variable Set | **Set** `%final_dim` = `%max_dim * (1 - %ratio ^ %AAB_PWMExp)` [DoMaths] |  |
| 20 | L36590 | 37 If | _This and the next block should mathematically never trigger. But I really <b>don't</b> want black screens._ — **If** | `%final_dim > %max_dim` **And** `%AAB_Privilege != None` |
| 21 | L36607 | 547 Variable Set | **Set** `%final_dim` = `99` |  |
| 22 | L36617 | 43 Else/Else-If | **Else/Else-If** | `%AAB_Privilege = None` **And** `%final_dim > %max_dim` |
| 23 | L36633 | 547 Variable Set | **Set** `%final_dim` = `253` |  |
| 24 | L36643 | 38 End If | **End If** |  |
| 25 | L36646 | 126 Return | **Return** `%final_dim` |  |

**Variables written:** `%bias`, `%dark_floor`, `%final_dim`, `%k_factor`, `%max_dim`, `%ratio`, `%safe_thresh`

**Variables read:** `%AAB_DimmingThreshold`, `%AAB_PWMExp`, `%bias`, `%dark_floor`, `%k_factor`, `%max_dim`, `%par1`, `%ratio`, `%safe_thresh`
