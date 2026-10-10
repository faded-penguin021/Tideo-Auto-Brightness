# task545 "Detect Proximity" (XML L16424–16473) — S3.5

Enter & exit task of prof759 (State **Proximity**, code 125, arg0=1). Task pri 6.
Owner-verified S3.5 (D-022).

| Act | Code | Action |
|---|---|---|
| act0 | 37 If | `%caller1 ~ *enter*` |
| act1 | 547 Variable Set | `%AAB_Proximity = near` |
| act2 | 43 Else-If | `%caller1 ~ *exit*` |
| act3 | 547 Variable Set | `%AAB_Proximity = far` |
| act4 | 38 End If | — |

**Owner's version (2026-10-04, DD-024), not in this XML:** the exit branch, after setting `far`,
adds A5: Perform Task "Evaluate Light Change (Java) V2" with par1 = `%AAB_LastRawLux`. Tideo runs it
as a recheck through the pending slot (`LightAdmission.proximityExit`).

Consumed in task535 A3b (DD-024): while near, `lux_alpha = round3(lux_alpha × 0.1)` before the
blend, so smoothing, the target, the animation and the `%LuxAlpha` readout all move a tenth as far.
It does **NOT** pause the pipeline. History: the XML here damped only `%LuxAlpha` in task544 act29
(DC-064), and the owner's V2 of 2026-10-03 damped the animation but not smoothing (DD-022). The only
other reader is the Debug scene.
