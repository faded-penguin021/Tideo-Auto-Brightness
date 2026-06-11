# task659 — _UpdateBrightnessFormulae

- **XML:** `Advanced_Auto_Brightness_V3.3.prj_9.xml` lines L33331–L33365 (35 lines)
- **Priority:** 1000  · **Actions:** 3
- **Action-code histogram:** Variable Set x2, Return x1

> Auto-transcribed verbatim from XML (entity-decoded). Provenance: each row is `actN` at its XML line. Reads/writes inferred from Variable Set targets and `%var` references.

| act | line | code | detail | condition |
|---|---|---|---|---|
| 0 | L33337 | 547 Variable Set | **Set** `%aab_form2a` = `%aab_form1a*(sqrt(%aab_zone1end))` [DoMaths] |  |
| 1 | L33347 | 547 Variable Set | **Set** `%aab_form3a` = `(%aab_zone2end*(%AAB_MaxBright-(%aab_form2a+%aab_form2b*((%aab_zone2end-%aab_form2c)^0.33-(%aab_zone1end-%aab_form2c)^0.33)))/%AAB_MaxBright)` [DoMaths] |  |
| 2 | L33357 | 126 Return | **Return** `` |  |

**Variables written:** `%aab_form2a`, `%aab_form3a`

**Variables read:** `%AAB_MaxBright`, `%aab_form1a`, `%aab_form2a`, `%aab_form2b`, `%aab_form2c`, `%aab_zone1end`, `%aab_zone2end`
