# task645 — Disable Super Dimming (Privileged)

- **XML:** `Advanced_Auto_Brightness_V3.3.prj_9.xml` lines L30876–L31025 (150 lines)
- **Priority:** ?  · **Actions:** 14
- **Action-code histogram:** Custom Setting x4, Else/Else-If x3, code375 x2, Run Shell x2, If x1, End If x1, Variable Set x1

> Auto-transcribed verbatim from XML (entity-decoded). Provenance: each row is `actN` at its XML line. Reads/writes inferred from Variable Set targets and `%var` references.

| act | line | code | detail | condition |
|---|---|---|---|---|
| 0 | L30881 | 37 If | **If** | `%AAB_Privilege = Root` |
| 1 | L30892 | 235 Custom Setting | **Custom Setting** arg0=1 arg1=`reduce_bright_colors_level` arg2=`0` arg3=1 |  |
| 2 | L30947 | 235 Custom Setting | **Custom Setting** arg0=1 arg1=`reduce_bright_colors_activated` arg2=`0` arg3=1 |  |
| 3 | L30955 | 43 Else/Else-If | **Else/Else-If** | `%AAB_Privilege = Write Secure` |
| 4 | L30965 | 235 Custom Setting | **Custom Setting** arg0=1 arg1=`reduce_bright_colors_level` arg2=`0` |  |
| 5 | L30973 | 235 Custom Setting | **Custom Setting** arg0=1 arg1=`reduce_bright_colors_activated` arg2=`0` |  |
| 6 | L30981 | 43 Else/Else-If | **Else/Else-If** | `%AAB_Privilege = Shizuku` |
| 7 | L30991 | 123 Run Shell | **Run Shell** `settings put secure reduce_bright_colors_level 0` |  |
| 8 | L31003 | 123 Run Shell | **Run Shell** `settings put secure reduce_bright_colors_activated 0` |  |
| 9 | L31015 | 43 Else/Else-If | **Else/Else-If** | `%AAB_Privilege = ADB WiFi` |
| 10 | L30900 | 375 code375 | **code375** arg1=`settings put secure reduce_bright_colors_level 0` arg4=10 |  |
| 11 | L30917 | 375 code375 | **code375** arg1=`settings put secure reduce_bright_colors_activated 0` arg4=10 |  |
| 12 | L30934 | 38 End If | **End If** |  |
| 13 | L30937 | 547 Variable Set | **Set** `%AAB_DimmingStatus` = `0` |  |

**Variables written:** `%AAB_DimmingStatus`

**Variables read:** (none)
