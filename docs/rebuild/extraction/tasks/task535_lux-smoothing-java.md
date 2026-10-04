# task535 — Lux Smoothing (Java)

- **XML:** `Advanced_Auto_Brightness_V3.3.prj_9.xml` lines L15197–L15260 (64 lines)
- **Priority:** 100  · **Actions:** 2
- **Action-code histogram:** Java Code x1, Return x1

> Auto-transcribed verbatim from XML (entity-decoded). Provenance: each row is `actN` at its XML line. Reads/writes inferred from Variable Set targets and `%var` references.

| act | line | code | detail | condition |
|---|---|---|---|---|
| 0 | L15203 | 474 Java Code | **Java Code** → `%output` (see `_source/java/task535_*.java.txt`) |  |
| 1 | L15252 | 126 Return | **Return** `%output` |  |

**Variables written:** `%output`

**Variables read:** (none)

> **Owner's task535 (2026-10-04, DD-024), not in this XML:** the Java reads `"%AAB_Proximity"` too,
> and between A3 (`lux_alpha`) and A4 (the blend) adds A3b: `if (AAB_Proximity.equals("near"))
> lux_alpha = Math.round(lux_alpha * 0.1 * 1000.0) / 1000.0;`. The new smoothed lux and the
> returned α both come from the damped value.
