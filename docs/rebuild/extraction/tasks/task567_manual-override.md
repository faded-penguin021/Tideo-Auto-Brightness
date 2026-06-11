# task567 — Manual Override

- **XML:** `Advanced_Auto_Brightness_V3.3.prj_9.xml` lines L20525–L20885 (361 lines)
- **Priority:** 6  · **Actions:** 33
- **Action-code histogram:** Stop x7, If x6, End If x6, Variable Set x3, Perform Task x2, Notify x2, code159 x2, code779 x1, Flash x1, Run Shell x1, Else/Else-If x1, Wait x1

> Auto-transcribed verbatim from XML (entity-decoded). Provenance: each row is `actN` at its XML line. Reads/writes inferred from Variable Set targets and `%var` references.

| act | line | code | detail | condition |
|---|---|---|---|---|
| 0 | L20532 | 37 If | **If** | `%caller1 = *Allow Override*` **Or** `%caller1 = *Smooth Brightness Transition*` |
| 1 | L20549 | 137 Stop | **Stop** |  |
| 2 | L20680 | 137 Stop | **Stop** |  |
| 3 | L20769 | 137 Stop | **Stop** |  |
| 4 | L20820 | 137 Stop | **Stop** |  |
| 5 | L20825 | 137 Stop | **Stop** |  |
| 6 | L20830 | 137 Stop | **Stop** |  |
| 7 | L20835 | 30 Wait | **Wait** %AAB_CycleTimes? args: arg0=%AAB_CycleTime |  |
| 8 | L20845 | 137 Stop | **Stop** | `%AAB_Service = Off` **Or** `%AutoBrightRunning = 1` **Or** `%AAB_Manual_Override = true` **Or** `%AAB_Initializing = true` |
| 9 | L20875 | 37 If | **If** | `%AAB_NotifyUse = true` |
| 10 | L20554 | 779 code779 | **code779** arg0=`Advanced Auto Brightness` |  |
| 11 | L20559 | 38 End If | **End If** |  |
| 12 | L20562 | 37 If | **If** | `%AAB_DetectOverrides = On` |
| 13 | L20572 | 130 Perform Task | **Perform Task** "Process Overrides" (pri=%priority) |  |
| 14 | L20588 | 38 End If | **End If** |  |
| 15 | L20591 | 547 Variable Set | **Set** `%AAB_Manual_Override` = `true` |  |
| 16 | L20601 | 547 Variable Set | **Set** `%AAB_ResumeTapped` = `Off` |  |
| 17 | L20611 | 548 Flash | **Flash** `Manual brightness override detected or Android's auto-brightness enabled. The service is now paused. Tap the notification to continue the service. ` |  |
| 18 | L20630 | 37 If | **If** | `%AAB_NotifyUse = true` |
| 19 | L20640 | 523 Notify | _Tap here to resume_ — **Notify** arg0=`Advanced Auto Brightness Paused` arg1=`Brightness manually set or Android auto brightness enabled. ` arg4=1 arg5=5 arg7=2 arg10=1 |  |
| 20 | L20685 | 38 End If | **End If** |  |
| 21 | L20688 | 130 Perform Task | **Perform Task** "_QSToggleAABService V2" (pri=%priority+3) |  |
| 22 | L20704 | 159 code159 | **code159** arg0=`Allow Override` |  |
| 23 | L20709 | 159 code159 | **code159** arg0=`Repost Paused Notification` arg1=1 |  |
| 24 | L20714 | 37 If | **If** | `%AAB_DimmingStatus = 1` |
| 25 | L20724 | 123 Run Shell | **Run Shell** `settings put secure reduce_bright_colors_activated 0` |  |
| 26 | L20736 | 547 Variable Set | **Set** `%AAB_DimmingStatus` = `1` |  |
| 27 | L20746 | 38 End If | **End If** |  |
| 28 | L20749 | 43 Else/Else-If | **Else/Else-If** | `%caller1 = *Repost Paused Notification*` |
| 29 | L20759 | 37 If | **If** | `%AAB_NotifyUse = true` |
| 30 | L20774 | 523 Notify | _Tap here to resume_ — **Notify** arg0=`Advanced Auto Brightness Paused` arg1=`Brightness manually set or Android auto brightness enabled. ` arg4=1 arg5=1 arg7=2 arg10=1 |  |
| 31 | L20814 | 38 End If | **End If** |  |
| 32 | L20817 | 38 End If | **End If** |  |

**Variables written:** `%AAB_DimmingStatus`, `%AAB_Manual_Override`, `%AAB_ResumeTapped`

**Variables read:** (none)
