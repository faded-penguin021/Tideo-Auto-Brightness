# task584 — _ForegroundNotification

- **XML:** `Advanced_Auto_Brightness_V3.3.prj_9.xml` lines L23191–L23271 (81 lines)
- **Priority:** 6  · **Actions:** 7
- **Action-code histogram:** If x2, Notify x2, End If x2, Else/Else-If x1

> Auto-transcribed verbatim from XML (entity-decoded). Provenance: each row is `actN` at its XML line. Reads/writes inferred from Variable Set targets and `%var` references.

| act | line | code | detail | condition |
|---|---|---|---|---|
| 0 | L23197 | 37 If | **If** | `%AAB_NotifyUse = true` |
| 1 | L23207 | 37 If | _High priority notification when the service starts._ — **If** | `%caller1 != *Repost Foreground Notification*` |
| 2 | L23219 | 523 Notify | **Notify** arg0=`Advanced Auto Brightness` arg1=`Foreground notification. Please don't dismiss this notificat` arg4=1 arg5=5 arg7=3 |  |
| 3 | L23240 | 43 Else/Else-If | _Low priority notification to keep the service alive._ — **Else/Else-If** |  |
| 4 | L23244 | 523 Notify | **Notify** arg0=`Advanced Auto Brightness` arg1=`Foreground notification. Please don't dismiss this notificat` arg4=1 arg5=1 arg7=3 |  |
| 5 | L23265 | 38 End If | **End If** |  |
| 6 | L23268 | 38 End If | **End If** |  |

**Variables written:** (none)

**Variables read:** (none)
