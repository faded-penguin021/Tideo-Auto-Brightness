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

Consumed in task544 act28–29: `if %AAB_Proximity = near → LuxAlpha = lux_results2 × 0.1`.
It does **NOT** pause the pipeline, and it does not damp reactivity either: act27 has already
stored `%SmoothedLux` from the undamped α, and act33 hands Map Lux the undamped `%lux_results2`,
so only the readouts that display `%LuxAlpha` change (corrected 2026-09-24, DC-064; the S14 port
had damped the EMA). The only other reader is the Debug scene.
