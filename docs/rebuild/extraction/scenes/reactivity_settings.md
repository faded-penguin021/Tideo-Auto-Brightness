# Scene: AAB Reactivity Settings

- XML line range: **L6739–7532** (`<Scene sr="sceneAAB Reactivity Settings">`)
- Scene geom: 1440 x 2944 (portrait), gridSize 5
- Title banner var: `%AAB_SettingsBGone'Reactivity'%AAB_SettingsBGtwo` (WebElement `bg_banner_title`)
- Target M3 screen: **Reactivity**

## Element count by type (top-level Scene children = 36)

| Type | Count |
|------|-------|
| WebElement | 1 |
| RectElement | 5 |
| TextElement | 18 |
| EditTextElement | 5 |
| SwitchElement | 4 |
| PropertiesElement | 1 |
| **Total** | **36** |

(Note: each Text/Edit/Switch/Web element also carries one nested `background` RectElement — intrinsic styling, not counted. The raw `<RectElement sr=` grep returns 34 because it includes those nested backgrounds.)

## Elements (in document order)

| # | name (sr) | type | bound variable | value range / options | handler task(s) | purpose |
|---|-----------|------|----------------|-----------------------|-----------------|---------|
| elements0 | bg_banner_title | WebElement | `%AAB_SettingsBGone`/`%AAB_SettingsBGtwo` (title="Reactivity") | — | — | HTML banner/title background ("Reactivity" tab header) |
| elements1 | rectangle_10 | RectElement | — | — | — | Decorative card bg (#FF404040, override/trust toggle band, y=409) |
| elements31 | Override | TextElement | displays `%AAB_DetectOverrides` | — | longclick=519 | Label "Use override detection" + value; longtap=help (`_CalibratePowerDraw`/help popup) |
| elements32 | Switch3 | SwitchElement | `%AAB_DetectOverrides` (on-overlay) | bool | checkchange=526 | Override-detection toggle ON half (geom y=409) |
| elements33 | Switch4 | SwitchElement | `%AAB_DetectOverrides` (off-overlay) | bool | checkchange=525 | Override-detection toggle OFF half (`_PanicButton`) |
| elements34 | Override_on_green | RectElement | — | — | — | Green "override on" indicator dot (#FF007C63) |
| elements5 | rectangle_4 | RectElement | — | — | — | Decorative card bg for threshold-percentages panel (y=584) |
| elements6 | thresh_dark_label | TextElement | displays `%aab_threshdarkpc` | — | longclick=719 | Label "Dark Threshold" + value; longtap help |
| elements13 | dark_threshold | EditTextElement | `%aab_threshdarkpc` | numeric (percent) | valueselected=701 | Edit dark-zone reactivity threshold % |
| elements11 | zone_1_end_label | TextElement | displays `%AAB_Zone1End` | — | longclick=493 | Label "Zone 1 End" + value; longtap help |
| elements12 | zone_1_end_calculated | TextElement | displays `%aab_zone1end` ("(auto)") | — | — | Read-only derived Zone 1 end (mirrors Brightness scene) |
| elements7 | thresh_dim_label | TextElement | displays `%aab_threshdimpc` | — | longclick=720 | Label "Dim Threshold" + value; longtap help |
| elements14 | dim_threshold | EditTextElement | `%aab_threshdimpc` | numeric (percent) | valueselected=704 | Edit dim-zone reactivity threshold % |
| elements8 | thresh_bright_label | TextElement | displays `%aab_threshbrightpc` | — | longclick=721 | Label "Bright Threshold" + value; longtap help |
| elements15 | bright_threshold | EditTextElement | `%aab_threshbrightpc` | numeric (percent) | valueselected=708 | Edit bright-zone reactivity threshold % |
| elements9 | thresh_steepness_label | TextElement | displays `%AAB_ThreshSteepness` | — | longclick=722 | Label "Curve Slope" + value; longtap help |
| elements16 | steepness_threshold | EditTextElement | `%aab_threshsteepness` | numeric | valueselected=709 | Edit threshold-curve steepness/slope |
| elements10 | thresh_midpoint_label | TextElement | displays `%AAB_ThreshMidpoint` | — | longclick=712 | Label "Curve Mid" + value; longtap help |
| elements23 | midpoint_reactivity | EditTextElement | `%aab_threshmidpoint` | numeric | valueselected=713 | Edit threshold-curve midpoint |
| elements26 | rectangle_8 | RectElement | — | — | — | Decorative card bg for trust-sensor band (y=1384) |
| elements27 | Trust | TextElement | displays `%AAB_TrustUnreliable` | — | longclick=729 | Label "Trust Low-Accuracy Sensor" + value; longtap help |
| elements28 | Switch1 | SwitchElement | `%AAB_TrustUnreliable` (on-overlay) | bool | checkchange=733 | Trust-low-accuracy toggle ON half (geom y=1384) |
| elements29 | Switch2 | SwitchElement | `%AAB_TrustUnreliable` (off-overlay) | bool | checkchange=731 | Trust-low-accuracy toggle OFF half |
| elements30 | Unreliable_on_green | RectElement | — | — | — | Green "trust on" indicator dot (#FF007C63) |
| elements17 | rectangle_5 | RectElement | — | — | — | Decorative card bg for live-status panel (y=1559) |
| elements21 | current_threshold | TextElement | displays `%AAB_ThreshDynamic`, `%SmoothedLux`, `%AAB_ThreshAbsLow`, `%AAB_ThreshAbsHigh` | — | longclick=711 | Live readout: current dynamic threshold + smoothed lux + abs lux gate bounds; longtap action |
| elements2 | rectangle_9 | RectElement | — | — | — | Decorative card bg for bottom button bar (y=2034) |
| elements3 | draw_graph_drop_shadow2 | TextElement | static "Draw Graph" | — | — | Drop-shadow layer behind "Draw Reactivity Graph" |
| elements4 | draw_graph2 | TextElement | static "Draw Reactivity Graph" | — | click=473 | Button: open reactivity graph (`_ShowUserGuide`/graph) |
| elements19 | save_button_drop_shadow | TextElement | static (empty) | — | — | Drop-shadow layer behind Apply button |
| elements20 | save_button1 | TextElement | static "Apply" | — | click=710 | Button: apply/save reactivity settings |
| elements18 | exit_scenes_drop_shadows | TextElement | static "Exit" | — | — | Drop-shadow layer behind Exit button |
| elements22 | exit_scenes | TextElement | static "Exit" | — | click=537 | Button: close scene (`_ExperimentScene`) |
| elements24 | undo_button_drop_shadow | TextElement | static "Reset tab defaults" | — | — | Drop-shadow layer behind Reset button |
| elements25 | undo_button | TextElement | static "Reset tab defaults" | — | click=735 | Button: reset this tab's settings to defaults |
| props | props | PropertiesElement | — | scene props (overlay=2) | — | Scene properties (background #FF000000) |

### Notes
- Two switch pairs render bound ON/OFF toggles: `Switch3`/`Switch4` (geom y=409) = **Override detection** (`%AAB_DetectOverrides`), `Switch1`/`Switch2` (geom y=1384) = **Trust low-accuracy sensor** (`%AAB_TrustUnreliable`). Each pair collapses to one M3 toggle; `*_on_green` rects are the lit indicators.
- All EditText threshold fields commit via a `valueselectedTask` (701/704/708/709/713). No `focuschangeTask` present on this scene's edits (unlike Brightness scene).
- `current_threshold` exposes the absolute lux gate bounds (`%AAB_ThreshAbsLow`/`%AAB_ThreshAbsHigh`) that live in prof760's ConditionList (CLAUDE.md) — read-only here.
- `%AAB_ThreshMidpoint` is a known schema gap vs `AabSettings.kt` (CLAUDE.md) — surfaced as an editable field here.

## Disposition

| element | disposition |
|---------|-------------|
| elements0 (bg_banner_title) | dropped(scene chrome) |
| elements1 (rectangle_10) | dropped(scene chrome) |
| elements31 (Override) | merged-into Reactivity |
| elements32 (Switch3) | kept-as Reactivity |
| elements33 (Switch4) | merged-into Reactivity |
| elements34 (Override_on_green) | dropped(scene chrome) |
| elements5 (rectangle_4) | dropped(scene chrome) |
| elements6 (thresh_dark_label) | merged-into Reactivity |
| elements13 (dark_threshold) | kept-as Reactivity |
| elements11 (zone_1_end_label) | merged-into Reactivity |
| elements12 (zone_1_end_calculated) | merged-into Reactivity |
| elements7 (thresh_dim_label) | merged-into Reactivity |
| elements14 (dim_threshold) | kept-as Reactivity |
| elements8 (thresh_bright_label) | merged-into Reactivity |
| elements15 (bright_threshold) | kept-as Reactivity |
| elements9 (thresh_steepness_label) | merged-into Reactivity |
| elements16 (steepness_threshold) | kept-as Reactivity |
| elements10 (thresh_midpoint_label) | merged-into Reactivity |
| elements23 (midpoint_reactivity) | kept-as Reactivity |
| elements26 (rectangle_8) | dropped(scene chrome) |
| elements27 (Trust) | merged-into Reactivity |
| elements28 (Switch1) | kept-as Reactivity |
| elements29 (Switch2) | merged-into Reactivity |
| elements30 (Unreliable_on_green) | dropped(scene chrome) |
| elements17 (rectangle_5) | dropped(scene chrome) |
| elements21 (current_threshold) | merged-into Reactivity |
| elements2 (rectangle_9) | dropped(scene chrome) |
| elements3 (draw_graph_drop_shadow2) | dropped(scene chrome) |
| elements4 (draw_graph2) | merged-into Reactivity |
| elements19 (save_button_drop_shadow) | dropped(scene chrome) |
| elements20 (save_button1) | merged-into Reactivity |
| elements18 (exit_scenes_drop_shadows) | dropped(scene chrome) |
| elements22 (exit_scenes) | dropped(scene chrome) |
| elements24 (undo_button_drop_shadow) | dropped(scene chrome) |
| elements25 (undo_button) | merged-into Reactivity |
| props | dropped(scene chrome) |
