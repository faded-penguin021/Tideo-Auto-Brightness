# task554 — Process Sensor Event (Java)

- **XML:** `Advanced_Auto_Brightness_V3.3.prj_9.xml` lines L18115–L18193 (79 lines)
- **Priority:** 8  · **Actions:** 3
- **Action-code histogram:** Variable Set x1, Java Code x1, Perform Task x1

> Auto-transcribed verbatim from XML (entity-decoded). Provenance: each row is `actN` at its XML line. Reads/writes inferred from Variable Set targets and `%var` references.

| act | line | code | detail | condition |
|---|---|---|---|---|
| 0 | L18121 | 547 Variable Set | **Set** `%AAB_MainLoop` = `On` |  |
| 1 | L18131 | 474 Java Code | **Java Code** → `` (see `java/task554_*.java`) |  |
| 2 | L18177 | 130 Perform Task | **Perform Task** "Evaluate Light Change (Java) V2" (pri=%priority) par1=`%as_values1` |  |

**Variables written:** `%AAB_MainLoop`

**Variables read:** `%as_values1`
