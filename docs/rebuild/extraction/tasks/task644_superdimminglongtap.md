# task644 — _SuperDimmingLongTap

- **XML:** `Advanced_Auto_Brightness_V3.3.prj_9.xml` lines L30661–L30875 (215 lines)
- **Priority:** ?  · **Actions:** 15
- **Action-code histogram:** Flash x5, Else/Else-If x4, If x2, End If x2, code779 x1, Notify x1

> Auto-transcribed verbatim from XML (entity-decoded). Provenance: each row is `actN` at its XML line. Reads/writes inferred from Variable Set targets and `%var` references.

| act | line | code | detail | condition |
|---|---|---|---|---|
| 0 | L30666 | 37 If | **If** | `%AAB_Privilege = Shizuku` |
| 1 | L30676 | 548 Flash | **Flash** `Displays the current privilege. ¶Shizuku privilege will use shell commands (reasonably efficient, albeit slow) to enable super dimming.` |  |
| 2 | L30744 | 43 Else/Else-If | **Else/Else-If** | `%AAB_Privilege = ADB WiFi` |
| 3 | L30754 | 548 Flash | **Flash** `Displays the current privilege.¶ADB WiFi shell commands are reasonably efficient albeit slow. Note: May need re-enabling after rebooting your device.` |  |
| 4 | L30774 | 43 Else/Else-If | **Else/Else-If** | `%AAB_Privilege = Root` **Or** `%AAB_Privilege = Write Secure` |
| 5 | L30790 | 548 Flash | **Flash** `Displays your current privilege. ¶%AAB_Privilege privilege will efficiently use Custom Settings actions to enable super dimming.` |  |
| 6 | L30810 | 43 Else/Else-If | **Else/Else-If** | `%AAB_Privilege = None` |
| 7 | L30820 | 37 If | **If** | `%AAB_NotifyUse = true` |
| 8 | L30830 | 779 code779 | **code779** arg0=`Advanced Auto Brightness` |  |
| 9 | L30835 | 523 Notify | _Learn_ — **Notify** arg0=`Advanced Auto Brightness` arg1=`Please tap learn in order to learn how to grant Write Secure` arg4=1 arg5=5 arg7=4 arg10=1 |  |
| 10 | L30696 | 38 End If | **End If** |  |
| 11 | L30699 | 548 Flash | **Flash** `Displays your current privilege. ¶⚠️ Unprivileged will draw a semi-transparent overlay and eat your battery. Not recommended to enable super dimming!` |  |
| 12 | L30719 | 43 Else/Else-If | **Else/Else-If** |  |
| 13 | L30722 | 548 Flash | **Flash** `⚠️ Privilege not set. Please tap the Check Privilege button.` |  |
| 14 | L30741 | 38 End If | **End If** |  |

**Variables written:** (none)

**Variables read:** (none)
