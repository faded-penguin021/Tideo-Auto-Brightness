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

Consumed in task544: in the owner's V2 (2026-10-03, DD-022), `if %AAB_Proximity ~ near →
%lux_results2 = round3(%lux_results2 × 0.1)`, copied into `%LuxAlpha` and passed as Map Lux's par2,
so task661 sizes the animation from the damped α. It does **NOT** pause the pipeline and does not
damp smoothing: `%SmoothedLux` is already stored from the undamped α, so the target brightness is
unchanged and only the animation (fewer steps, shorter throttle) and the `%LuxAlpha` readouts move.
The XML transcribed here predates V2: there act29 damped only `%LuxAlpha` (DC-064). The only other
reader is the Debug scene.
