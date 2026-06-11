# Scene: AAB Color Filter

- XML line range: **L2552–2582** (`<Scene sr="sceneAAB Color Filter">`)
- Scene geom: 1440 x 3168 (portrait, taller than display to fully cover incl. status/nav bars)
- Type: a single full-screen colored **RectElement** used as a screen dimming/tint overlay
- Scene bg is transparent (#00FFFFFF) so only the rect tints the screen
- Target M3 screen: **Animation & Dimming** (overlay is the software super-dimming / PWM-sensitive fallback)

## Element count by type (top-level Scene children = 2)

| Type | Count |
|------|-------|
| RectElement | 1 |
| PropertiesElement | 1 |
| **Total** | **2** |

## Elements (in document order)

| # | name (sr) | type | bound variable | value range / options | handler task(s) | purpose |
|---|-----------|------|----------------|-----------------------|-----------------|---------|
| elements0 | Rectangle1 | RectElement | fill color = `%AAB_HexOverlay` (arg2) | ARGB hex string (e.g. #80000000) | — | Full-screen tint/dim overlay rectangle (0,0,1439,3168). Alpha+color drive super-dimming and screen tint. |
| props | props | PropertiesElement | — | title "AAB Color Filter", scene bg #00FFFFFF (transparent) | — (no keyTask) | Scene properties; transparent so only the overlay rect renders |

## Notes
- This scene IS the software-dimming surface. In the Kotlin rebuild it becomes an overlay window
  (TYPE_APPLICATION_OVERLAY / accessibility overlay) whose color = the computed `%AAB_HexOverlay`
  produced by the dimming pipeline (alpha = dimming strength, used as PWM-sensitive software floor fallback).
- No tap handlers: it is a pure visual overlay, ideally non-interactive (pass-through touch).

## Disposition

| Element | Disposition |
|---------|-------------|
| elements0 (Rectangle1 overlay) | color_filter — kept-as Animation & Dimming |
| props (scene properties) | color_filter — dropped(replaced by overlay window config in Compose/service) |
