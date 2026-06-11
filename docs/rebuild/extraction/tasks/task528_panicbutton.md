# task528 — _PanicButton

- **XML:** `Advanced_Auto_Brightness_V3.3.prj_9.xml` lines L14806–L14938 (133 lines)
- **Priority:** 100  · **Actions:** 13
- **Action-code histogram:** code65 x3, Perform Task x3, Variable Set x2, Stop x2, code62 x1, Set Display Brightness x1, code49 x1

> Auto-transcribed verbatim from XML (entity-decoded). Provenance: each row is `actN` at its XML line. Reads/writes inferred from Variable Set targets and `%var` references.

| act | line | code | detail | condition |
|---|---|---|---|---|
| 0 | L14812 | 62 code62 | _This pattern is S.O.S. in morse code_ — **code62** arg0=`0,100,100,100,100,100,300,300,100,300,100,300,300,100,100,10` |  |
| 1 | L14818 | 547 Variable Set | **Set** `%AAB_Service` = `on` |  |
| 2 | L14855 | 130 Perform Task | **Perform Task** "_QSToggleAABService V2" (pri=%priority+3) |  |
| 3 | L14871 | 547 Variable Set | **Set** `%AAB_Manual_Override` = `true` |  |
| 4 | L14881 | 137 Stop | **Stop** |  |
| 5 | L14886 | 137 Stop | **Stop** |  |
| 6 | L14891 | 810 Set Display Brightness | **Set Display Brightness** arg0=`255` |  |
| 7 | L14900 | 130 Perform Task | **Perform Task** "Disable Super Dimming (Unprivileged)" (pri=%priority) |  |
| 8 | L14916 | 130 Perform Task | **Perform Task** "Disable Super Dimming (Privileged)" (pri=%priority) |  |
| 9 | L14932 | 49 code49 | _This is redundant, but better safe than sorry!_ — **code49** arg0=`AAB Color Filter` |  |
| 10 | L14828 | 65 code65 | **code65** arg0=`AAB Brightness Settings` arg1=`Switch` arg2=1 |  |
| 11 | L14837 | 65 code65 | **code65** arg0=`AAB Brightness Settings` arg1=`Switch2` |  |
| 12 | L14846 | 65 code65 | **code65** arg0=`AAB Brightness Settings` arg1=`Service_on_green` |  |

**Variables written:** `%AAB_Manual_Override`, `%AAB_Service`

**Variables read:** (none)
