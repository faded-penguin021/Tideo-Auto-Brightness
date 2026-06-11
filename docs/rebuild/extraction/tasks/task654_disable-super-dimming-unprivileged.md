# task654 — Disable Super Dimming (Unprivileged)

- **XML:** `Advanced_Auto_Brightness_V3.3.prj_9.xml` lines L32527–L32573 (47 lines)
- **Priority:** ?  · **Actions:** 5
- **Action-code histogram:** Variable Set x3, Variable Clear x1, code49 x1

> Auto-transcribed verbatim from XML (entity-decoded). Provenance: each row is `actN` at its XML line. Reads/writes inferred from Variable Set targets and `%var` references.

| act | line | code | detail | condition |
|---|---|---|---|---|
| 0 | L32532 | 549 Variable Clear | **Clear** `%AAB_HexOverlay` |  |
| 1 | L32539 | 49 code49 | **code49** arg0=`AAB Color Filter` |  |
| 2 | L32543 | 547 Variable Set | **Set** `%AAB_DimmingStatus` = `0` |  |
| 3 | L32553 | 547 Variable Set | **Set** `%AAB_DimmingDS` = `0` |  |
| 4 | L32563 | 547 Variable Set | **Set** `%AAB_DimmingCurrent` = `0` [DoMaths] |  |

**Variables written:** `%AAB_DimmingCurrent`, `%AAB_DimmingDS`, `%AAB_DimmingStatus`, `%AAB_HexOverlay`

**Variables read:** (none)
