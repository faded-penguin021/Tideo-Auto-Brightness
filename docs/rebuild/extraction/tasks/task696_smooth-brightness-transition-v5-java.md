# task696 — Smooth Brightness Transition V5 (Java)

- **XML:** `Advanced_Auto_Brightness_V3.3.prj_9.xml` lines L35693–L35926 (234 lines)
- **Priority:** 100  · **Actions:** 6
- **Action-code histogram:** code159 x2, code389 x1, Variable Set x1, Java Code x1, Flash x1

> Auto-transcribed verbatim from XML (entity-decoded). Provenance: each row is `actN` at its XML line. Reads/writes inferred from Variable Set targets and `%var` references.

| act | line | code | detail | condition |
|---|---|---|---|---|
| 0 | L35699 | 389 code389 | **code389** arg1=`%AAB_LastAnimation=%TIMEMS¶%AAB_CycleTime=%TIMEMS - %AAB_Cyc` arg4=`=` arg5=1 arg6=3 arg8=1 |  |
| 1 | L35717 | 547 Variable Set | **Set** `%AutoBrightRunning` = `1` |  |
| 2 | L35727 | 159 code159 | **code159** arg0=`Allow Override` |  |
| 3 | L35732 | 474 Java Code | **Java Code** → `` (see `_source/java/task696_*.java.txt`) |  |
| 4 | L35890 | 159 code159 | **code159** arg0=`Allow Override` arg1=1 |  |
| 5 | L35895 | 548 Flash | **Flash** `Hardware mode¶Loops: %loops, wait step: %wait ms¶Java loop duration: %java_loop_duration ms¶Target brightness: %AAB_CurrentBright¶Maximum target: %max_target¶Minimum target: %min_target` | `%AAB_Debug = 2` |

**Variables written:** `%AutoBrightRunning`

**Variables read:** (none)
