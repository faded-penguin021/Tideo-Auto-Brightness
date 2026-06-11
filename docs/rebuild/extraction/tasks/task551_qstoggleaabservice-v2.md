# task551 — _QSToggleAABService V2

- **XML:** `Advanced_Auto_Brightness_V3.3.prj_9.xml` lines L17325–L17718 (394 lines)
- **Priority:** 100  · **Actions:** 49
- **Action-code histogram:** code159 x31, Perform Task x7, If x2, Variable Set x2, code779 x2, End If x2, Stop x1, Else/Else-If x1, code808 x1

> Auto-transcribed verbatim from XML (entity-decoded). Provenance: each row is `actN` at its XML line. Reads/writes inferred from Variable Set targets and `%var` references.

| act | line | code | detail | condition |
|---|---|---|---|---|
| 0 | L17332 | 37 If | **If** | `%AAB_Service = On` **And** `%caller1 != *Resume After Override*` **Or** `%caller1 = *_ContextResume*` |
| 1 | L17355 | 547 Variable Set | **Set** `%AAB_Service` = `Off` |  |
| 2 | L17429 | 130 Perform Task | **Perform Task** "_MainSwitchUI V2" (pri=%priority+2) |  |
| 3 | L17535 | 159 code159 | **code159** arg0=`Initialize (Display On)` |  |
| 4 | L17611 | 159 code159 | **code159** arg0=`Monitor Ambient Light` |  |
| 5 | L17688 | 159 code159 | **code159** arg0=`Hibernate (Display Off)` |  |
| 6 | L17694 | 159 code159 | **code159** arg0=`Allow Override` |  |
| 7 | L17700 | 159 code159 | **code159** arg0=`Throttle Reinitialization` |  |
| 8 | L17706 | 159 code159 | **code159** arg0=`Dynamic Scale Engine` |  |
| 9 | L17712 | 159 code159 | **code159** arg0=`Allow Override` |  |
| 10 | L17365 | 159 code159 | **code159** arg0=`Proximity Detection` |  |
| 11 | L17371 | 159 code159 | **code159** arg0=`Repost Foreground Notification` |  |
| 12 | L17377 | 159 code159 | **code159** arg0=`Context: App Changed` |  |
| 13 | L17383 | 159 code159 | **code159** arg0=`Context: Battery Changed` |  |
| 14 | L17389 | 159 code159 | **code159** arg0=`Context: Location Changed` |  |
| 15 | L17395 | 159 code159 | **code159** arg0=`Context: Time Changed` |  |
| 16 | L17401 | 159 code159 | **code159** arg0=`Context: Location Listener` |  |
| 17 | L17407 | 159 code159 | **code159** arg0=`Context: Location Refresher` |  |
| 18 | L17413 | 159 code159 | **code159** arg0=`Context: WiFi (Dis)connected` |  |
| 19 | L17419 | 37 If | **If** | `%AAB_NotifyUse = true` |
| 20 | L17445 | 779 code779 | **code779** arg0=`Advanced Auto Brightness` |  |
| 21 | L17450 | 779 code779 | **code779** arg0=`Advanced Auto Brightness Paused` | `%AAB_Manual_Override != true` |
| 22 | L17462 | 38 End If | **End If** |  |
| 23 | L17465 | 130 Perform Task | **Perform Task** "Dimming Decider" (pri=%priority) |  |
| 24 | L17481 | 130 Perform Task | **Perform Task** "Reset Brightness and State" (pri=%priority) |  |
| 25 | L17497 | 137 Stop | **Stop** |  |
| 26 | L17502 | 43 Else/Else-If | **Else/Else-If** |  |
| 27 | L17505 | 547 Variable Set | **Set** `%AAB_Service` = `On` |  |
| 28 | L17515 | 130 Perform Task | **Perform Task** "_MainSwitchUI V2" (pri=%priority+2) |  |
| 29 | L17531 | 808 code808 | **code808**  |  |
| 30 | L17541 | 130 Perform Task | **Perform Task** "Set Initial Brightness (Java) V3" (pri=%priority+1) |  |
| 31 | L17557 | 159 code159 | **code159** arg0=`Initialize (Display On)` arg1=1 |  |
| 32 | L17563 | 159 code159 | **code159** arg0=`Monitor Ambient Light` arg1=1 |  |
| 33 | L17569 | 159 code159 | **code159** arg0=`Hibernate (Display Off)` arg1=1 |  |
| 34 | L17575 | 159 code159 | **code159** arg0=`Allow Override` arg1=1 |  |
| 35 | L17581 | 159 code159 | **code159** arg0=`Throttle Reinitialization` arg1=1 |  |
| 36 | L17587 | 159 code159 | **code159** arg0=`Dynamic Scale Engine` arg1=1 |  |
| 37 | L17593 | 159 code159 | **code159** arg0=`Proximity Detection` arg1=1 |  |
| 38 | L17599 | 159 code159 | **code159** arg0=`Repost Foreground Notification` arg1=1 |  |
| 39 | L17605 | 159 code159 | **code159** arg0=`Context: App Changed` arg1=1 |  |
| 40 | L17617 | 159 code159 | **code159** arg0=`Context: Battery Changed` arg1=1 |  |
| 41 | L17623 | 159 code159 | **code159** arg0=`Context: Location Changed` arg1=1 |  |
| 42 | L17629 | 159 code159 | **code159** arg0=`Context: Time Changed` arg1=1 |  |
| 43 | L17635 | 159 code159 | **code159** arg0=`Context: Location Listener` arg1=1 |  |
| 44 | L17641 | 159 code159 | **code159** arg0=`Context: Location Refresher` arg1=1 |  |
| 45 | L17647 | 159 code159 | **code159** arg0=`Context: WiFi (Dis)connected` arg1=1 |  |
| 46 | L17653 | 130 Perform Task | **Perform Task** "_ForegroundNotification" (pri=%priority-1) |  |
| 47 | L17669 | 130 Perform Task | **Perform Task** "Dimming Decider" (pri=%priority) |  |
| 48 | L17685 | 38 End If | **End If** |  |

**Variables written:** `%AAB_Service`

**Variables read:** (none)
