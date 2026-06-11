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

Consumed in task544 act28–29: `if %AAB_Proximity = near → LuxAlpha = lux_results2 × 0.1`
(reaction damping while the phone is at the ear / in a pocket). It does **NOT** pause the
pipeline. Port target: S9 proximity sensor listener → pipeline state flag feeding the
engine's alpha damping.
