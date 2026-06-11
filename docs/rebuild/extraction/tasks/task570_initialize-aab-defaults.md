# task570 — Initialize AAB Defaults

- **XML:** `Advanced_Auto_Brightness_V3.3.prj_9.xml` lines L20979–L21633 (655 lines)
- **Priority:** 100  · **Actions:** 68
- **Action-code histogram:** Variable Set x41, If x9, End If x9, code159 x7, Notify x1, Return x1

> Auto-transcribed verbatim from XML (entity-decoded). Provenance: each row is `actN` at its XML line. Reads/writes inferred from Variable Set targets and `%var` references.

| act | line | code | detail | condition |
|---|---|---|---|---|
| 0 | L20985 | 37 If | **If** | `%AAB_SetupComplete !~ (no match) 1` |
| 1 | L20995 | 37 If | **If** | `%reset is NOT set ` |
| 2 | L21112 | 159 code159 | **code159** arg0=`Hibernate (Display Off)` |  |
| 3 | L21221 | 159 code159 | **code159** arg0=`Initialize (Display On)` |  |
| 4 | L21338 | 159 code159 | **code159** arg0=`Monitor Ambient Light` |  |
| 5 | L21442 | 159 code159 | **code159** arg0=`Throttle Reinitialization` |  |
| 6 | L21540 | 159 code159 | **code159** arg0=`Repost Paused Notification` |  |
| 7 | L21613 | 159 code159 | **code159** arg0=`Allow Override` |  |
| 8 | L21618 | 159 code159 | **code159** arg0=`Dynamic Scale Engine` |  |
| 9 | L21623 | 547 Variable Set | **Set** `%AAB_Service` = `Off` |  |
| 10 | L21005 | 38 End If | **End If** |  |
| 11 | L21008 | 37 If | **If** | `%reset ~ (matches) 1` **Or** `%reset is NOT set ` |
| 12 | L21024 | 547 Variable Set | _End of the Zone where Formula 1 applies._ — **Set** `%AAB_Zone1End` = `35` |  |
| 13 | L21035 | 547 Variable Set | _End of the Zone where Formula 2 applies._ — **Set** `%AAB_Zone2End` = `10000` |  |
| 14 | L21046 | 547 Variable Set | _This is the 'Scaling' factor for the Zone 1 formula._ — **Set** `%AAB_Form1A` = `5` |  |
| 15 | L21057 | 547 Variable Set | _This is the 'Start Brightness' constant for Zone 2._ — **Set** `%AAB_Form2A` = `%AAB_Form1A*(sqrt(%AAB_Zone1End))` [DoMaths] |  |
| 16 | L21068 | 547 Variable Set | _This is the 'Scaling' factor for Zone 2._ — **Set** `%AAB_Form2B` = `8.8` |  |
| 17 | L21079 | 547 Variable Set | _This is the 'Steepness/Offset' factor for Zone 2._ — **Set** `%AAB_Form2C` = `18` |  |
| 18 | L21090 | 547 Variable Set | _This is the Zone 1 Transition Lux, used in the Zone 2 formula._ — **Set** `%AAB_Form2D` = `%AAB_Zone1End` |  |
| 19 | L21101 | 547 Variable Set | _This is the 'High-Lux Alignment Factor' for Zone 3._ — **Set** `%AAB_Form3A` = `(%AAB_Zone2End*(255-(%AAB_Form2A+%AAB_Form2B*((%AAB_Zone2End-%AAB_Form2C)^0.33-(%AAB_Form2D-%AAB_Form2C)^0.33)))/255)` [DoMaths] |  |
| 20 | L21117 | 38 End If | **End If** |  |
| 21 | L21120 | 37 If | **If** | `%reset ~ (matches) 2` **Or** `%reset is NOT set ` |
| 22 | L21136 | 547 Variable Set | _Caution: Brightness of 0 may be unreadable on some devices. Test in a dark room._ — **Set** `%AAB_MinBright` = `10` |  |
| 23 | L21147 | 547 Variable Set | **Set** `%AAB_MaxBright` = `255` |  |
| 24 | L21157 | 547 Variable Set | _This acts as a multiplier for the entire curve._ — **Set** `%AAB_Scale` = `1` |  |
| 25 | L21168 | 547 Variable Set | _This adds a constant to the curve and thereby 'lifts' the curve upwards._ — **Set** `%AAB_Offset` = `0` |  |
| 26 | L21179 | 547 Variable Set | _The maximum number of animation steps to perform for the Smooth Brightness Transition task._ — **Set** `%AAB_AnimSteps` = `20` |  |
| 27 | L21190 | 547 Variable Set | **Set** `%AAB_MinWait` = `25` |  |
| 28 | L21200 | 547 Variable Set | **Set** `%AAB_MaxWait` = `65` |  |
| 29 | L21210 | 547 Variable Set | _Default set to allow %AAB_MaxSteps × AAB_MaxWait to finish._ — **Set** `%AAB_Throttle` = `((%AAB_AnimSteps)*(%AAB_MaxWait)+10)` [DoMaths] |  |
| 30 | L21226 | 547 Variable Set | **Set** `%AAB_DeltaFactor` = `1.8` |  |
| 31 | L21236 | 547 Variable Set | **Set** `%AAB_ThreshDynamic` = `5` | `%AAB_ThreshDynamic is NOT set ` |
| 32 | L21253 | 38 End If | **End If** |  |
| 33 | L21256 | 37 If | **If** | `%reset ~ (matches) 3` **Or** `%reset is NOT set ` |
| 34 | L21272 | 547 Variable Set | _This setting disables override detection._ — **Set** `%AAB_DetectOverrides` = `Off` |  |
| 35 | L21283 | 547 Variable Set | _Stability in darkness (at 0 lux)_ — **Set** `%AAB_ThreshDark` = `0.3` |  |
| 36 | L21294 | 547 Variable Set | _Sensitivity in dim light (lower bound asymptote)_ — **Set** `%AAB_ThreshDim` = `0.25` |  |
| 37 | L21305 | 547 Variable Set | _Sensitivity in bright light (upper bound asymptote)_ — **Set** `%AAB_ThreshBright` = `0.08` |  |
| 38 | L21316 | 547 Variable Set | _Reactivity Aggressiveness_ — **Set** `%AAB_ThreshSteepness` = `2.1` |  |
| 39 | L21327 | 547 Variable Set | _Midpoint of change (log lux)_ — **Set** `%AAB_ThreshMidpoint` = `log10(%AAB_Zone2End)` [DoMaths] |  |
| 40 | L21343 | 547 Variable Set | **Set** `%AAB_TrustUnreliable` = `Off` |  |
| 41 | L21353 | 38 End If | **End If** |  |
| 42 | L21356 | 37 If | **If** | `%reset ~ (matches) 4` **Or** `%reset is NOT set ` |
| 43 | L21372 | 547 Variable Set | **Set** `%AAB_ScaleSpread` = `15` |  |
| 44 | L21382 | 547 Variable Set | **Set** `%AAB_ScaleTransitionFactor` = `0.1` |  |
| 45 | L21392 | 547 Variable Set | **Set** `%AAB_ScaleSteepness` = `6` |  |
| 46 | L21402 | 547 Variable Set | **Set** `%AAB_ScalingUse` = `false` |  |
| 47 | L21412 | 547 Variable Set | **Set** `%AAB_ScaleTaperMidpoint` = `190` |  |
| 48 | L21422 | 547 Variable Set | **Set** `%AAB_ScaleTaperSteepness` = `0.075` |  |
| 49 | L21432 | 547 Variable Set | **Set** `%AAB_QSUse` = `false` |  |
| 50 | L21447 | 38 End If | **End If** |  |
| 51 | L21450 | 37 If | **If** | `%reset ~ (matches) 5` **Or** `%reset is NOT set ` |
| 52 | L21466 | 547 Variable Set | **Set** `%AAB_DimmingEnabled` = `false` |  |
| 53 | L21476 | 547 Variable Set | **Set** `%AAB_DimmingThreshold` = `15` [DoMaths] |  |
| 54 | L21486 | 547 Variable Set | **Set** `%AAB_DimmingStrength` = `25` |  |
| 55 | L21496 | 547 Variable Set | **Set** `%AAB_DimmingExponent` = `2.5` |  |
| 56 | L21506 | 547 Variable Set | _If circadian scaling is disabled, this won't be visible in the scene._ — **Set** `%AAB_DimSpread` = `100` |  |
| 57 | L21517 | 547 Variable Set | **Set** `%AAB_PWMExp` = `0.8` |  |
| 58 | L21527 | 38 End If | **End If** |  |
| 59 | L21530 | 37 If | **If** | `%reset is NOT set ` |
| 60 | L21545 | 547 Variable Set | **Set** `%AAB_Debug` = `0` |  |
| 61 | L21555 | 547 Variable Set | **Set** `%AAB_SetupTitle` = `Advanced Auto Brightness Setup` |  |
| 62 | L21565 | 37 If | **If** | `%AAB_NotifyUse = true` |
| 63 | L21575 | 523 Notify | **Notify** arg0=`%AAB_SetupTitle` arg1=`Defaults initialized. To finish setup, please turn your scre` arg5=3 |  |
| 64 | L21596 | 38 End If | **End If** |  |
| 65 | L21599 | 38 End If | **End If** |  |
| 66 | L21602 | 38 End If | **End If** |  |
| 67 | L21605 | 126 Return | **Return** `` |  |

**Variables written:** `%AAB_AnimSteps`, `%AAB_Debug`, `%AAB_DeltaFactor`, `%AAB_DetectOverrides`, `%AAB_DimSpread`, `%AAB_DimmingEnabled`, `%AAB_DimmingExponent`, `%AAB_DimmingStrength`, `%AAB_DimmingThreshold`, `%AAB_Form1A`, `%AAB_Form2A`, `%AAB_Form2B`, `%AAB_Form2C`, `%AAB_Form2D`, `%AAB_Form3A`, `%AAB_MaxBright`, `%AAB_MaxWait`, `%AAB_MinBright`, `%AAB_MinWait`, `%AAB_Offset`, `%AAB_PWMExp`, `%AAB_QSUse`, `%AAB_Scale`, `%AAB_ScaleSpread`, `%AAB_ScaleSteepness`, `%AAB_ScaleTaperMidpoint`, `%AAB_ScaleTaperSteepness`, `%AAB_ScaleTransitionFactor`, `%AAB_ScalingUse`, `%AAB_Service`, `%AAB_SetupTitle`, `%AAB_ThreshBright`, `%AAB_ThreshDark`, `%AAB_ThreshDim`, `%AAB_ThreshDynamic`, `%AAB_ThreshMidpoint`, `%AAB_ThreshSteepness`, `%AAB_Throttle`, `%AAB_TrustUnreliable`, `%AAB_Zone1End`, `%AAB_Zone2End`

**Variables read:** `%AAB_AnimSteps`, `%AAB_Form1A`, `%AAB_Form2A`, `%AAB_Form2B`, `%AAB_Form2C`, `%AAB_Form2D`, `%AAB_MaxWait`, `%AAB_Zone1End`, `%AAB_Zone2End`
